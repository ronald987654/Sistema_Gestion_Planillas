package com.planillaperu.repository;

import com.planillaperu.domain.RonaldmichaEstadoPeriodo;
import com.planillaperu.domain.RonaldmichaPeriodo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

public class RonaldmichaPeriodoRepository {

    private final RonaldmichaDatabase db;

    public RonaldmichaPeriodoRepository(RonaldmichaDatabase db) {
        this.db = db;
    }

    public Optional<YearMonth> primerCerradoDesde(YearMonth mes) {
        String sql = "SELECT periodo FROM periodo WHERE estado = ? AND periodo >= ? ORDER BY periodo LIMIT 1";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, RonaldmichaEstadoPeriodo.CERRADO.etiqueta());
            ps.setString(2, RonaldmichaPeriodo.FORMATO.format(mes));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(YearMonth.parse(rs.getString(1), RonaldmichaPeriodo.FORMATO)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo consultar los periodos cerrados", e);
        }
    }

    public Optional<YearMonth> primerAbierto() {
        return extremo("SELECT MIN(periodo) FROM periodo WHERE estado = ?", RonaldmichaEstadoPeriodo.ABIERTO);
    }

    public Optional<YearMonth> ultimoCerrado() {
        return extremo("SELECT MAX(periodo) FROM periodo WHERE estado = ?", RonaldmichaEstadoPeriodo.CERRADO);
    }

    private Optional<YearMonth> extremo(String sql, RonaldmichaEstadoPeriodo estado) {
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, estado.etiqueta());
            try (ResultSet rs = ps.executeQuery()) {
                String valor = rs.next() ? rs.getString(1) : null;
                return valor == null ? Optional.empty() : Optional.of(YearMonth.parse(valor, RonaldmichaPeriodo.FORMATO));
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo consultar los periodos", e);
        }
    }

    public Optional<YearMonth> ultimoRegistrado() {
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("SELECT MAX(periodo) FROM periodo");
             ResultSet rs = ps.executeQuery()) {
            String valor = rs.next() ? rs.getString(1) : null;
            return valor == null ? Optional.empty() : Optional.of(YearMonth.parse(valor, RonaldmichaPeriodo.FORMATO));
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo consultar los periodos", e);
        }
    }

    public void cerrar(YearMonth mes, String usuario, LocalDateTime fecha) {
        actualizar("INSERT INTO periodo (periodo, estado, cerrado_por, fecha_cierre) VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT (periodo) DO UPDATE SET estado = excluded.estado,"
                        + " cerrado_por = excluded.cerrado_por, fecha_cierre = excluded.fecha_cierre",
                RonaldmichaPeriodo.FORMATO.format(mes), RonaldmichaEstadoPeriodo.CERRADO.etiqueta(), usuario, fecha.toString());
    }

    public void abrir(YearMonth mes) {
        actualizar("INSERT INTO periodo (periodo, estado) VALUES (?, ?) ON CONFLICT (periodo) DO NOTHING",
                RonaldmichaPeriodo.FORMATO.format(mes), RonaldmichaEstadoPeriodo.ABIERTO.etiqueta());
    }

    public void reabrir(YearMonth mes, String motivo) {
        actualizar("UPDATE periodo SET estado = ?, motivo_reapertura = ? WHERE periodo = ?",
                RonaldmichaEstadoPeriodo.ABIERTO.etiqueta(), motivo, RonaldmichaPeriodo.FORMATO.format(mes));
    }

    private void actualizar(String sql, String... valores) {
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                ps.setString(i + 1, valores[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo actualizar el periodo", e);
        }
    }

    public Optional<RonaldmichaPeriodo> buscar(YearMonth mes) {
        String sql = "SELECT periodo, estado, cerrado_por, fecha_cierre, motivo_reapertura FROM periodo WHERE periodo = ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, RonaldmichaPeriodo.FORMATO.format(mes));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String cierre = rs.getString("fecha_cierre");
                return Optional.of(new RonaldmichaPeriodo(
                        YearMonth.parse(rs.getString("periodo"), RonaldmichaPeriodo.FORMATO),
                        RonaldmichaEstadoPeriodo.desdeEtiqueta(rs.getString("estado")),
                        rs.getString("cerrado_por"),
                        cierre == null ? null : LocalDateTime.parse(cierre),
                        rs.getString("motivo_reapertura")));
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer el periodo " + mes, e);
        }
    }
}
