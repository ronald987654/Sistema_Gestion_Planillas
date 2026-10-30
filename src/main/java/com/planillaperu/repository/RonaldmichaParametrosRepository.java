package com.planillaperu.repository;

import com.planillaperu.domain.RonaldmichaAfpComision;
import com.planillaperu.domain.RonaldmichaEstadoParametros;
import com.planillaperu.domain.RonaldmichaParametrosVersion;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RonaldmichaParametrosRepository {

    private final RonaldmichaDatabase db;

    public RonaldmichaParametrosRepository(RonaldmichaDatabase db) {
        this.db = db;
    }

    public Optional<RonaldmichaParametrosVersion> buscarVigente(YearMonth periodo) {
        String sql = "SELECT * FROM parametros_version WHERE estado = ? AND vigente_desde <= ?"
                + " ORDER BY vigente_desde DESC LIMIT 1";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, RonaldmichaEstadoParametros.VIGENTE.etiqueta());
            ps.setString(2, periodo.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(c, rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer los parámetros vigentes a " + periodo, e);
        }
    }

    public Optional<RonaldmichaParametrosVersion> buscarPorId(long id) {
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM parametros_version WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(c, rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer la versión de parámetros " + id, e);
        }
    }

    public List<RonaldmichaParametrosVersion> listar() {
        String sql = "SELECT * FROM parametros_version ORDER BY vigente_desde DESC, id DESC";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<RonaldmichaParametrosVersion> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(c, rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo listar las versiones de parámetros", e);
        }
    }

    public boolean existeVigenteDesde(YearMonth mes) {
        String sql = "SELECT 1 FROM parametros_version WHERE estado = ? AND vigente_desde = ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, RonaldmichaEstadoParametros.VIGENTE.etiqueta());
            ps.setString(2, mes.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo consultar las versiones de " + mes, e);
        }
    }

    public RonaldmichaParametrosVersion insertar(RonaldmichaParametrosVersion v) {
        String sql = "INSERT INTO parametros_version (vigente_desde, norma, rmv, uit, asig_familiar_pct, essalud_pct,"
                + " eps_pct, onp_pct, afp_aporte_pct, afp_prima_pct, rma_prima, bonif_ext_pct, bonif_ext_eps_pct,"
                + " vida_ley_pct, sis_monto, estado, registrado_por, fecha_registro)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        String sqlComision = "INSERT INTO afp_comision (version_id, afp, comision_flujo_pct, comision_mixta_pct)"
                + " VALUES (?,?,?,?)";
        try (Connection c = db.conexion()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement pc = c.prepareStatement(sqlComision)) {
                int i = 1;
                ps.setString(i++, v.vigenteDesde().toString());
                ps.setString(i++, v.norma());
                for (BigDecimal valor : List.of(v.rmv(), v.uit(), v.asigFamiliarPct(), v.essaludPct(), v.epsPct(),
                        v.onpPct(), v.afpAportePct(), v.afpPrimaPct(), v.rmaPrima(), v.bonifExtPct(),
                        v.bonifExtEpsPct(), v.vidaLeyPct(), v.sisMonto())) {
                    ps.setString(i++, valor.toPlainString());
                }
                ps.setString(i++, RonaldmichaEstadoParametros.VIGENTE.etiqueta());
                ps.setString(i++, v.registradoPor());
                ps.setString(i, v.fechaRegistro().toString());
                ps.executeUpdate();
                long id;
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getLong(1);
                }
                for (RonaldmichaAfpComision com : v.comisiones()) {
                    pc.setLong(1, id);
                    pc.setString(2, com.afp());
                    pc.setString(3, com.comisionFlujoPct().toPlainString());
                    pc.setString(4, com.comisionMixtaPct().toPlainString());
                    pc.executeUpdate();
                }
                c.commit();
                return buscarPorId(id).orElseThrow();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo registrar la versión de parámetros", e);
        }
    }

    public void anular(long id, String motivo, String usuario, LocalDateTime fecha) {
        String sql = "UPDATE parametros_version SET estado = ?, motivo_anulacion = ?, anulado_por = ?,"
                + " fecha_anulacion = ? WHERE id = ? AND estado = ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, RonaldmichaEstadoParametros.ANULADA.etiqueta());
            ps.setString(2, motivo);
            ps.setString(3, usuario);
            ps.setString(4, fecha.toString());
            ps.setLong(5, id);
            ps.setString(6, RonaldmichaEstadoParametros.VIGENTE.etiqueta());
            if (ps.executeUpdate() == 0) {
                throw new RonaldmichaDatosException("La versión " + id + " no existe o ya está anulada", null);
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo anular la versión de parámetros " + id, e);
        }
    }

    private static List<RonaldmichaAfpComision> comisiones(Connection c, long versionId) throws SQLException {
        String sql = "SELECT afp, comision_flujo_pct, comision_mixta_pct FROM afp_comision"
                + " WHERE version_id = ? ORDER BY afp";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, versionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RonaldmichaAfpComision> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new RonaldmichaAfpComision(rs.getString("afp"),
                            decimal(rs, "comision_flujo_pct"), decimal(rs, "comision_mixta_pct")));
                }
                return lista;
            }
        }
    }

    private static RonaldmichaParametrosVersion mapear(Connection c, ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String fechaAnulacion = rs.getString("fecha_anulacion");
        return new RonaldmichaParametrosVersion(
                id,
                YearMonth.parse(rs.getString("vigente_desde")),
                rs.getString("norma"),
                decimal(rs, "rmv"),
                decimal(rs, "uit"),
                decimal(rs, "asig_familiar_pct"),
                decimal(rs, "essalud_pct"),
                decimal(rs, "eps_pct"),
                decimal(rs, "onp_pct"),
                decimal(rs, "afp_aporte_pct"),
                decimal(rs, "afp_prima_pct"),
                decimal(rs, "rma_prima"),
                decimal(rs, "bonif_ext_pct"),
                decimal(rs, "bonif_ext_eps_pct"),
                decimal(rs, "vida_ley_pct"),
                decimal(rs, "sis_monto"),
                RonaldmichaEstadoParametros.desdeEtiqueta(rs.getString("estado")),
                rs.getString("registrado_por"),
                LocalDateTime.parse(rs.getString("fecha_registro")),
                comisiones(c, id),
                rs.getString("motivo_anulacion"),
                rs.getString("anulado_por"),
                fechaAnulacion == null ? null : LocalDateTime.parse(fechaAnulacion));
    }

    private static BigDecimal decimal(ResultSet rs, String columna) throws SQLException {
        return new BigDecimal(rs.getString(columna));
    }
}
