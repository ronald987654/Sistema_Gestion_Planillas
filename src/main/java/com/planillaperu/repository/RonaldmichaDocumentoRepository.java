package com.planillaperu.repository;

import com.planillaperu.domain.RonaldmichaDocumentoEmitido;
import com.planillaperu.domain.RonaldmichaEstadoDocumento;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RonaldmichaDocumentoRepository {

    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyyMM");

    private final RonaldmichaDatabase db;

    public RonaldmichaDocumentoRepository(RonaldmichaDatabase db) {
        this.db = db;
    }

    public List<RonaldmichaDocumentoEmitido> registrar(List<RonaldmichaDocumentoEmitido> documentos) {
        try (Connection c = db.conexion()) {
            c.setAutoCommit(false);
            try {
                List<RonaldmichaDocumentoEmitido> guardados = new ArrayList<>();
                String sql = "INSERT INTO documento_emitido (numero, tipo, trabajador_id, periodo, formato, fecha, estado,"
                        + " requiere_cargo, archivo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                for (RonaldmichaDocumentoEmitido d : documentos) {
                    String numero = siguiente(c, d.tipo(), d.fecha().getYear());
                    try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, numero);
                        ps.setString(2, d.tipo());
                        ps.setObject(3, d.trabajadorId());
                        ps.setString(4, d.periodo() == null ? null : PERIODO.format(d.periodo()));
                        ps.setString(5, d.formato());
                        ps.setString(6, d.fecha().toString());
                        ps.setString(7, d.estado().etiqueta());
                        ps.setInt(8, d.requiereCargo() ? 1 : 0);
                        ps.setString(9, d.archivo());
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            rs.next();
                            guardados.add(new RonaldmichaDocumentoEmitido(rs.getLong(1), numero, d.tipo(), d.trabajadorId(),
                                    d.trabajador(), d.periodo(), d.formato(), d.fecha(), d.estado(), d.requiereCargo(),
                                    d.archivo()));
                        }
                    }
                }
                c.commit();
                return guardados;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo registrar los documentos emitidos", e);
        }
    }

    private static String siguiente(Connection c, String tipo, int anio) throws SQLException {
        String prefijo = tipo + "-" + anio + "-";
        try (PreparedStatement ps = c.prepareStatement("SELECT MAX(CAST(SUBSTR(numero, ?) AS INTEGER))"
                + " FROM documento_emitido WHERE tipo = ? AND numero LIKE ?")) {
            ps.setInt(1, prefijo.length() + 1);
            ps.setString(2, tipo);
            ps.setString(3, prefijo + "%");
            try (ResultSet rs = ps.executeQuery()) {
                int ultimo = rs.next() ? rs.getInt(1) : 0;
                return prefijo + String.format("%06d", ultimo + 1);
            }
        }
    }

    public Set<Long> trabajadoresConDocumento(String tipo, YearMonth periodo) {
        String sql = "SELECT DISTINCT trabajador_id FROM documento_emitido WHERE tipo = ? AND periodo = ?"
                + " AND trabajador_id IS NOT NULL";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tipo);
            ps.setString(2, PERIODO.format(periodo));
            Set<Long> ids = new HashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
            return ids;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer los documentos emitidos", e);
        }
    }

    private static final String SELECT = "SELECT d.*,"
            + " t.ap_paterno || ' ' || COALESCE(t.ap_materno, '') || ', ' || t.nombres AS nombre"
            + " FROM documento_emitido d LEFT JOIN trabajador t ON t.id = d.trabajador_id";

    public List<RonaldmichaDocumentoEmitido> recientes(int limite, Long trabajadorId) {
        String sql = SELECT + (trabajadorId == null ? "" : " WHERE d.trabajador_id = ?")
                + " ORDER BY d.fecha DESC, d.id DESC LIMIT ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (trabajadorId != null) {
                ps.setLong(i++, trabajadorId);
            }
            ps.setInt(i, limite);
            List<RonaldmichaDocumentoEmitido> lista = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(leer(rs));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer los documentos emitidos", e);
        }
    }

    public Optional<RonaldmichaDocumentoEmitido> buscar(long id) {
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(SELECT + " WHERE d.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(leer(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer el documento emitido", e);
        }
    }

    public Optional<RonaldmichaDocumentoEmitido> ultimoDe(String tipo, long trabajadorId) {
        String sql = SELECT + " WHERE d.tipo = ? AND d.trabajador_id = ? ORDER BY d.id DESC LIMIT 1";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tipo);
            ps.setLong(2, trabajadorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(leer(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer los documentos emitidos", e);
        }
    }

    public Optional<RonaldmichaDocumentoEmitido> ultimoDe(String tipo, long trabajadorId, YearMonth periodo) {
        String sql = SELECT + " WHERE d.tipo = ? AND d.trabajador_id = ? AND d.periodo = ? ORDER BY d.id DESC LIMIT 1";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tipo);
            ps.setLong(2, trabajadorId);
            ps.setString(3, PERIODO.format(periodo));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(leer(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer los documentos emitidos", e);
        }
    }

    private static RonaldmichaDocumentoEmitido leer(ResultSet rs) throws SQLException {
        long trabajador = rs.getLong("trabajador_id");
        Long id = rs.wasNull() ? null : trabajador;
        String periodo = rs.getString("periodo");
        String recepcion = rs.getString("fecha_recepcion");
        return new RonaldmichaDocumentoEmitido(rs.getLong("id"), rs.getString("numero"), rs.getString("tipo"), id,
                rs.getString("nombre"), periodo == null ? null : YearMonth.parse(periodo, PERIODO),
                rs.getString("formato"), LocalDateTime.parse(rs.getString("fecha")),
                RonaldmichaEstadoDocumento.desdeEtiqueta(rs.getString("estado")), rs.getInt("requiere_cargo") == 1,
                rs.getString("archivo"), recepcion == null ? null : LocalDateTime.parse(recepcion),
                rs.getString("recepcion"));
    }

    public int cambiarEstado(Collection<Long> ids, RonaldmichaEstadoDocumento estado) {
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("UPDATE documento_emitido SET estado = ? WHERE id = ?")) {
            int total = 0;
            for (long id : ids) {
                ps.setString(1, estado.etiqueta());
                ps.setLong(2, id);
                total += ps.executeUpdate();
            }
            return total;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo actualizar los documentos emitidos", e);
        }
    }

    public boolean marcarCargoRecibido(long id, LocalDateTime fecha, String recepcion) {
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("UPDATE documento_emitido SET estado = 'Recibido',"
                     + " fecha_recepcion = ?, recepcion = ? WHERE id = ? AND requiere_cargo = 1 AND estado <> 'Recibido'")) {
            ps.setString(1, fecha.toString());
            ps.setString(2, recepcion);
            ps.setLong(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo marcar el cargo", e);
        }
    }
}
