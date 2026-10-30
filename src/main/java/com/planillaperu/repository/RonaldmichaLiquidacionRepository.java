package com.planillaperu.repository;

import com.planillaperu.domain.RonaldmichaEstadoLiquidacion;
import com.planillaperu.domain.RonaldmichaLiquidacion;
import com.planillaperu.domain.RonaldmichaMotivoCese;

import java.math.BigDecimal;
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

public class RonaldmichaLiquidacionRepository {

    private final RonaldmichaDatabase db;

    public RonaldmichaLiquidacionRepository(RonaldmichaDatabase db) {
        this.db = db;
    }

    public List<RonaldmichaLiquidacion> listar() {
        return consultar("SELECT * FROM liquidacion ORDER BY fecha_cese DESC, id DESC", null);
    }

    public Optional<RonaldmichaLiquidacion> buscar(long id) {
        List<RonaldmichaLiquidacion> lista = consultar("SELECT * FROM liquidacion WHERE id = ?", id);
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.getFirst());
    }

    public List<RonaldmichaLiquidacion> delTrabajador(long trabajadorId) {
        return consultar("SELECT * FROM liquidacion WHERE trabajador_id = ? ORDER BY id DESC", trabajadorId);
    }

    public long guardar(RonaldmichaLiquidacion l, boolean registrarCese) {
        try (Connection c = db.conexion()) {
            c.setAutoCommit(false);
            try {
                long id = l.id() == null ? insertar(c, l) : actualizar(c, l);
                if (registrarCese) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE trabajador SET fecha_cese = ? WHERE id = ?")) {
                        ps.setString(1, l.fechaCese().toString());
                        ps.setLong(2, l.trabajadorId());
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo guardar la liquidación", e);
        }
    }

    public boolean cambiarEstado(long id, RonaldmichaEstadoLiquidacion desde, RonaldmichaEstadoLiquidacion hacia) {
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("UPDATE liquidacion SET estado = ? WHERE id = ? AND estado = ?")) {
            ps.setString(1, hacia.etiqueta());
            ps.setLong(2, id);
            ps.setString(3, desde.etiqueta());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo cambiar el estado de la liquidación", e);
        }
    }

    public boolean eliminarBorrador(long id) {
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("DELETE FROM liquidacion WHERE id = ? AND estado = 'Borrador'")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo eliminar la liquidación", e);
        }
    }

    private static long insertar(Connection c, RonaldmichaLiquidacion l) throws SQLException {
        String sql = "INSERT INTO liquidacion (trabajador_id, fecha_cese, motivo, otros_ingresos, otros_descuentos,"
                + " faltas_cts, faltas_grati, ultima_grati, estado, fecha_registro, usuario, bruto, descuento, neto)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, l.trabajadorId());
            int i = parametros(ps, 2, l);
            ps.setString(i++, l.fechaRegistro().toString());
            ps.setString(i++, l.usuario());
            importes(ps, i, l);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static long actualizar(Connection c, RonaldmichaLiquidacion l) throws SQLException {
        String sql = "UPDATE liquidacion SET fecha_cese = ?, motivo = ?, otros_ingresos = ?, otros_descuentos = ?,"
                + " faltas_cts = ?, faltas_grati = ?, ultima_grati = ?, estado = ?, bruto = ?, descuento = ?, neto = ?"
                + " WHERE id = ? AND estado <> 'Pagado'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = parametros(ps, 1, l);
            i = importes(ps, i, l);
            ps.setLong(i, l.id());
            if (ps.executeUpdate() == 0) {
                throw new RonaldmichaDatosException("La liquidación ya está pagada o no existe", null);
            }
            return l.id();
        }
    }

    private static int parametros(PreparedStatement ps, int i, RonaldmichaLiquidacion l) throws SQLException {
        ps.setString(i++, l.fechaCese().toString());
        ps.setString(i++, l.motivo().etiqueta());
        ps.setString(i++, l.otrosIngresos().toPlainString());
        ps.setString(i++, l.otrosDescuentos().toPlainString());
        ps.setInt(i++, l.faltasCts());
        ps.setInt(i++, l.faltasGratificacion());
        ps.setString(i++, l.ultimaGratificacion() == null ? null : l.ultimaGratificacion().toPlainString());
        ps.setString(i++, l.estado().etiqueta());
        return i;
    }

    private static int importes(PreparedStatement ps, int i, RonaldmichaLiquidacion l) throws SQLException {
        ps.setString(i++, l.bruto().toPlainString());
        ps.setString(i++, l.descuento().toPlainString());
        ps.setString(i++, l.neto().toPlainString());
        return i;
    }

    private List<RonaldmichaLiquidacion> consultar(String sql, Long parametro) {
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (parametro != null) {
                ps.setLong(1, parametro);
            }
            List<RonaldmichaLiquidacion> lista = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(leer(rs));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer las liquidaciones", e);
        }
    }

    private static RonaldmichaLiquidacion leer(ResultSet rs) throws SQLException {
        String ultima = rs.getString("ultima_grati");
        return new RonaldmichaLiquidacion(
                rs.getLong("id"),
                rs.getLong("trabajador_id"),
                LocalDate.parse(rs.getString("fecha_cese")),
                RonaldmichaMotivoCese.desdeEtiqueta(rs.getString("motivo")),
                new BigDecimal(rs.getString("otros_ingresos")),
                new BigDecimal(rs.getString("otros_descuentos")),
                rs.getInt("faltas_cts"),
                rs.getInt("faltas_grati"),
                ultima == null || ultima.isBlank() ? null : new BigDecimal(ultima),
                RonaldmichaEstadoLiquidacion.desdeEtiqueta(rs.getString("estado")),
                LocalDateTime.parse(rs.getString("fecha_registro")),
                rs.getString("usuario"),
                new BigDecimal(rs.getString("bruto")),
                new BigDecimal(rs.getString("descuento")),
                new BigDecimal(rs.getString("neto")));
    }
}
