package com.planillaperu.repository;

import com.planillaperu.domain.RonaldmichaSemestreBeneficio;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class RonaldmichaBeneficioRepository {

    public record Ajuste(long trabajadorId, BigDecimal basico, int faltas, BigDecimal gratificacion) {
    }

    private final RonaldmichaDatabase db;

    public RonaldmichaBeneficioRepository(RonaldmichaDatabase db) {
        this.db = db;
    }

    public Map<Long, Ajuste> ajustes(RonaldmichaSemestreBeneficio s) {
        String sql = "SELECT trabajador_id, basico, faltas, gratificacion FROM ajuste_beneficio"
                + " WHERE tipo = ? AND anio = ? AND semestre = ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            parametros(ps, s);
            Map<Long, Ajuste> mapa = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Ajuste a = new Ajuste(rs.getLong(1), decimal(rs.getString(2)), rs.getInt(3),
                            decimal(rs.getString(4)));
                    mapa.put(a.trabajadorId(), a);
                }
            }
            return mapa;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer los ajustes de " + s.tipo().descripcion(), e);
        }
    }

    public void guardar(RonaldmichaSemestreBeneficio s, Ajuste a) {
        String sql = "INSERT INTO ajuste_beneficio (tipo, anio, semestre, trabajador_id, basico, faltas, gratificacion)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (tipo, anio, semestre, trabajador_id) DO UPDATE SET"
                + " basico = excluded.basico, faltas = excluded.faltas, gratificacion = excluded.gratificacion";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            parametros(ps, s);
            ps.setLong(4, a.trabajadorId());
            ps.setString(5, a.basico() == null ? null : a.basico().toPlainString());
            ps.setInt(6, a.faltas());
            ps.setString(7, a.gratificacion() == null ? null : a.gratificacion().toPlainString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo guardar el ajuste", e);
        }
    }

    public void borrar(RonaldmichaSemestreBeneficio s, long trabajadorId) {
        String sql = "DELETE FROM ajuste_beneficio WHERE tipo = ? AND anio = ? AND semestre = ? AND trabajador_id = ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            parametros(ps, s);
            ps.setLong(4, trabajadorId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo quitar el ajuste", e);
        }
    }

    public int borrarTodos(RonaldmichaSemestreBeneficio s) {
        String sql = "DELETE FROM ajuste_beneficio WHERE tipo = ? AND anio = ? AND semestre = ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            parametros(ps, s);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo restablecer los ajustes", e);
        }
    }

    private static void parametros(PreparedStatement ps, RonaldmichaSemestreBeneficio s) throws SQLException {
        ps.setString(1, s.tipo().codigo());
        ps.setInt(2, s.anio());
        ps.setInt(3, s.semestre());
    }

    private static BigDecimal decimal(String valor) {
        return valor == null || valor.isBlank() ? null : new BigDecimal(valor);
    }
}
