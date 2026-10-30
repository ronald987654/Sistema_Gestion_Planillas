package com.planillaperu.repository;

import com.planillaperu.common.RonaldmichaJsonPlano;
import com.planillaperu.domain.RonaldmichaAprobacion;
import com.planillaperu.domain.RonaldmichaAprobacionPendiente;
import com.planillaperu.domain.RonaldmichaEstadoAprobacion;
import com.planillaperu.domain.RonaldmichaEstadoVacacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RonaldmichaAprobacionRepository {

    public static final String TIPO_VACACIONES = "Vacaciones";

    private final RonaldmichaDatabase db;

    public RonaldmichaAprobacionRepository(RonaldmichaDatabase db) {
        this.db = db;
    }

    public List<RonaldmichaAprobacionPendiente> listarPendientes() {
        String sql = "SELECT a.id, a.tipo, a.trabajador_id, a.detalle, a.registrado_por, a.fecha,"
                + " t.ap_paterno || ' ' || COALESCE(t.ap_materno, '') || ', ' || t.nombres AS trabajador"
                + " FROM aprobacion a LEFT JOIN trabajador t ON t.id = a.trabajador_id"
                + " WHERE a.estado = 'Pendiente' ORDER BY a.fecha DESC, a.id DESC";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<RonaldmichaAprobacionPendiente> lista = new ArrayList<>();
            while (rs.next()) {
                long trabajador = rs.getLong("trabajador_id");
                Long trabajadorId = rs.wasNull() ? null : trabajador;
                lista.add(new RonaldmichaAprobacionPendiente(
                        rs.getLong("id"),
                        rs.getString("tipo"),
                        trabajadorId,
                        rs.getString("trabajador"),
                        rs.getString("detalle"),
                        rs.getString("registrado_por"),
                        LocalDateTime.parse(rs.getString("fecha"))));
            }
            return lista;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer las aprobaciones pendientes", e);
        }
    }

    public Optional<RonaldmichaAprobacion> buscarPorId(long id) {
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM aprobacion WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(leer(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer la aprobación", e);
        }
    }

    public Optional<RonaldmichaAprobacion> pendienteDe(long trabajadorId) {
        String sql = "SELECT * FROM aprobacion WHERE trabajador_id = ? AND estado = 'Pendiente' AND vacacion_id IS NULL ORDER BY id LIMIT 1";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, trabajadorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(leer(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer la aprobación", e);
        }
    }

    public RonaldmichaAprobacion insertar(RonaldmichaAprobacion a) {
        String sql = "INSERT INTO aprobacion (tipo, trabajador_id, detalle, cambios_json, registrado_por, fecha, estado)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, a.tipo());
            ps.setObject(2, a.trabajadorId());
            ps.setString(3, a.detalle());
            ps.setString(4, RonaldmichaJsonPlano.escribir(a.cambios()));
            ps.setString(5, a.registradoPor());
            ps.setString(6, a.fecha().toString());
            ps.setString(7, RonaldmichaEstadoAprobacion.PENDIENTE.etiqueta());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return new RonaldmichaAprobacion(rs.getLong(1), a.tipo(), a.trabajadorId(), a.detalle(), a.cambios(),
                        a.registradoPor(), a.fecha(), RonaldmichaEstadoAprobacion.PENDIENTE, null, null, null);
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo registrar la aprobación", e);
        }
    }

    public boolean aprobar(RonaldmichaAprobacion a, String usuario, LocalDateTime fecha) {
        try (Connection c = db.conexion()) {
            c.setAutoCommit(false);
            try {
                if (resolver(c, a.id(), RonaldmichaEstadoAprobacion.APROBADO, usuario, fecha, null) == 0) {
                    c.rollback();
                    return false;
                }
                Long vacacion = vacacionDe(c, a.id());
                if (vacacion != null) {
                    RonaldmichaVacacionRepository.resolverSolicitud(c, vacacion, RonaldmichaEstadoVacacion.PROGRAMADO);
                } else if (a.trabajadorId() != null) {
                    RonaldmichaTrabajadorRepository.actualizarColumnas(c, a.trabajadorId(), a.cambios());
                }
                c.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo aprobar la solicitud", e);
        }
    }

    public boolean rechazar(long id, String usuario, LocalDateTime fecha, String motivo) {
        try (Connection c = db.conexion()) {
            c.setAutoCommit(false);
            try {
                if (resolver(c, id, RonaldmichaEstadoAprobacion.RECHAZADO, usuario, fecha, motivo) == 0) {
                    c.rollback();
                    return false;
                }
                Long vacacion = vacacionDe(c, id);
                if (vacacion != null) {
                    RonaldmichaVacacionRepository.resolverSolicitud(c, vacacion, RonaldmichaEstadoVacacion.RECHAZADO);
                }
                c.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo rechazar la solicitud", e);
        }
    }

    private static Long vacacionDe(Connection c, long aprobacionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT vacacion_id FROM aprobacion WHERE id = ?")) {
            ps.setLong(1, aprobacionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long id = rs.getLong(1);
                return rs.wasNull() ? null : id;
            }
        }
    }

    private static int resolver(Connection c, long id, RonaldmichaEstadoAprobacion estado, String usuario, LocalDateTime fecha,
                                String motivo) throws SQLException {
        String sql = "UPDATE aprobacion SET estado = ?, resuelto_por = ?, fecha_resolucion = ?, motivo_rechazo = ?"
                + " WHERE id = ? AND estado = 'Pendiente'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, estado.etiqueta());
            ps.setString(2, usuario);
            ps.setString(3, fecha.toString());
            ps.setString(4, motivo);
            ps.setLong(5, id);
            return ps.executeUpdate();
        }
    }

    private static RonaldmichaAprobacion leer(ResultSet rs) throws SQLException {
        long trabajador = rs.getLong("trabajador_id");
        Long trabajadorId = rs.wasNull() ? null : trabajador;
        String resolucion = rs.getString("fecha_resolucion");
        return new RonaldmichaAprobacion(
                rs.getLong("id"),
                rs.getString("tipo"),
                trabajadorId,
                rs.getString("detalle"),
                RonaldmichaJsonPlano.leer(rs.getString("cambios_json")),
                rs.getString("registrado_por"),
                LocalDateTime.parse(rs.getString("fecha")),
                RonaldmichaEstadoAprobacion.desdeEtiqueta(rs.getString("estado")),
                rs.getString("resuelto_por"),
                resolucion == null ? null : LocalDateTime.parse(resolucion),
                rs.getString("motivo_rechazo"));
    }
}
