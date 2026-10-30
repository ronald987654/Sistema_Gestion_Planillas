package com.planillaperu.repository;

import com.planillaperu.domain.RonaldmichaEstadoVacacion;
import com.planillaperu.domain.RonaldmichaTipoVacacion;
import com.planillaperu.domain.RonaldmichaVacacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RonaldmichaVacacionRepository {

    private static final String COLUMNAS = "id, trabajador_id, tipo, desde, hasta, dias, observacion, estado,"
            + " fecha_registro, usuario, adjunto_nombre, acuerdo_escrito";

    private final RonaldmichaDatabase db;

    public RonaldmichaVacacionRepository(RonaldmichaDatabase db) {
        this.db = db;
    }

    public List<RonaldmichaVacacion> listarTodos() {
        return consultar("SELECT " + COLUMNAS + " FROM vacacion ORDER BY desde, id", null);
    }

    public List<RonaldmichaVacacion> listar(long trabajadorId) {
        return consultar("SELECT " + COLUMNAS + " FROM vacacion WHERE trabajador_id = ?"
                + " ORDER BY fecha_registro DESC, id DESC", trabajadorId);
    }

    public Optional<RonaldmichaVacacion> buscar(long id) {
        List<RonaldmichaVacacion> lista = consultar("SELECT " + COLUMNAS + " FROM vacacion WHERE id = ?", id);
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.getFirst());
    }

    public RonaldmichaVacacion insertar(RonaldmichaVacacion v, String detalleAprobacion) {
        String sql = "INSERT INTO vacacion (trabajador_id, tipo, desde, hasta, dias, observacion, estado,"
                + " fecha_registro, usuario, acuerdo_escrito) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = db.conexion()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, v.trabajadorId());
                    ps.setString(2, v.tipo().etiqueta());
                    ps.setString(3, texto(v.desde()));
                    ps.setString(4, texto(v.hasta()));
                    ps.setInt(5, v.dias());
                    ps.setString(6, v.observacion());
                    ps.setString(7, v.estado().etiqueta());
                    ps.setString(8, v.fechaRegistro().toString());
                    ps.setString(9, v.usuario());
                    ps.setInt(10, v.acuerdoEscrito() ? 1 : 0);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        id = rs.getLong(1);
                    }
                }
                if (detalleAprobacion != null) {
                    String aprobacion = "INSERT INTO aprobacion (tipo, trabajador_id, detalle, cambios_json,"
                            + " registrado_por, fecha, estado, vacacion_id) VALUES (?, ?, ?, '{}', ?, ?, 'Pendiente', ?)";
                    try (PreparedStatement ps = c.prepareStatement(aprobacion)) {
                        ps.setString(1, RonaldmichaAprobacionRepository.TIPO_VACACIONES);
                        ps.setLong(2, v.trabajadorId());
                        ps.setString(3, detalleAprobacion);
                        ps.setString(4, v.usuario());
                        ps.setString(5, v.fechaRegistro().toString());
                        ps.setLong(6, id);
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return new RonaldmichaVacacion(id, v.trabajadorId(), v.tipo(), v.desde(), v.hasta(), v.dias(), v.observacion(),
                        v.estado(), v.fechaRegistro(), v.usuario(), null, v.acuerdoEscrito());
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo registrar las vacaciones", e);
        }
    }

    public void actualizar(RonaldmichaVacacion v, String detalleAprobacion) {
        String sql = "UPDATE vacacion SET tipo = ?, desde = ?, hasta = ?, dias = ?, observacion = ?, acuerdo_escrito = ?"
                + " WHERE id = ?";
        try (Connection c = db.conexion()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, v.tipo().etiqueta());
                    ps.setString(2, texto(v.desde()));
                    ps.setString(3, texto(v.hasta()));
                    ps.setInt(4, v.dias());
                    ps.setString(5, v.observacion());
                    ps.setInt(6, v.acuerdoEscrito() ? 1 : 0);
                    ps.setLong(7, v.id());
                    ps.executeUpdate();
                }
                if (detalleAprobacion != null) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE aprobacion SET detalle = ?"
                            + " WHERE vacacion_id = ? AND estado = 'Pendiente'")) {
                        ps.setString(1, detalleAprobacion);
                        ps.setLong(2, v.id());
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo actualizar las vacaciones", e);
        }
    }

    public boolean anular(long id, String usuario, LocalDateTime fecha, String motivo) {
        try (Connection c = db.conexion()) {
            c.setAutoCommit(false);
            try {
                int filas;
                try (PreparedStatement ps = c.prepareStatement("UPDATE vacacion SET estado = 'Anulado'"
                        + " WHERE id = ? AND estado NOT IN ('Anulado', 'Rechazado')")) {
                    ps.setLong(1, id);
                    filas = ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE aprobacion SET estado = 'Rechazado',"
                        + " resuelto_por = ?, fecha_resolucion = ?, motivo_rechazo = ?"
                        + " WHERE vacacion_id = ? AND estado = 'Pendiente'")) {
                    ps.setString(1, usuario);
                    ps.setString(2, fecha.toString());
                    ps.setString(3, motivo);
                    ps.setLong(4, id);
                    ps.executeUpdate();
                }
                c.commit();
                return filas > 0;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo anular las vacaciones", e);
        }
    }

    static int resolverSolicitud(Connection c, long id, RonaldmichaEstadoVacacion nuevo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE vacacion SET estado = ?"
                + " WHERE id = ? AND estado = 'Pendiente de aprobación'")) {
            ps.setString(1, nuevo.etiqueta());
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }

    public void adjuntar(long id, String nombre, byte[] contenido) {
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("UPDATE vacacion SET adjunto_nombre = ?, adjunto = ? WHERE id = ?")) {
            ps.setString(1, nombre);
            ps.setBytes(2, contenido);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo guardar el archivo adjunto", e);
        }
    }

    public Optional<byte[]> adjunto(long id) {
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("SELECT adjunto FROM vacacion WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getBytes(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer el archivo adjunto", e);
        }
    }

    private List<RonaldmichaVacacion> consultar(String sql, Long parametro) {
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (parametro != null) {
                ps.setLong(1, parametro);
            }
            List<RonaldmichaVacacion> lista = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(leer(rs));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer las vacaciones", e);
        }
    }

    private static RonaldmichaVacacion leer(ResultSet rs) throws SQLException {
        return new RonaldmichaVacacion(
                rs.getLong("id"),
                rs.getLong("trabajador_id"),
                RonaldmichaTipoVacacion.desdeEtiqueta(rs.getString("tipo")),
                fecha(rs.getString("desde")),
                fecha(rs.getString("hasta")),
                rs.getInt("dias"),
                rs.getString("observacion"),
                RonaldmichaEstadoVacacion.desdeEtiqueta(rs.getString("estado")),
                LocalDateTime.parse(rs.getString("fecha_registro")),
                rs.getString("usuario"),
                rs.getString("adjunto_nombre"),
                rs.getInt("acuerdo_escrito") == 1);
    }

    private static LocalDate fecha(String s) {
        return s == null ? null : LocalDate.parse(s);
    }

    private static String texto(LocalDate d) {
        return d == null ? null : d.toString();
    }
}
