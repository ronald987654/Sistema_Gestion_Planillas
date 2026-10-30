package com.planillaperu.repository;

import com.planillaperu.domain.RonaldmichaRegistroAuditoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RonaldmichaAuditoriaRepository {

    private final RonaldmichaDatabase db;

    public RonaldmichaAuditoriaRepository(RonaldmichaDatabase db) {
        this.db = db;
    }

    public void insertar(RonaldmichaRegistroAuditoria r) {
        String sql = "INSERT INTO auditoria (usuario, fecha_hora, accion, detalle) VALUES (?, ?, ?, ?)";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.usuario());
            ps.setString(2, r.fechaHora().toString());
            ps.setString(3, r.accion());
            ps.setString(4, r.detalle());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo registrar la auditoría", e);
        }
    }

    public List<RonaldmichaRegistroAuditoria> listarRecientes(int limite) {
        return buscar(null, null, null, null, limite);
    }

    public List<RonaldmichaRegistroAuditoria> buscar(LocalDate desde, LocalDate hasta, String usuario, String accion, int limite) {
        StringBuilder sql = new StringBuilder("SELECT id, usuario, fecha_hora, accion, detalle FROM auditoria WHERE 1 = 1");
        List<Object> valores = new ArrayList<>();
        if (desde != null) {
            sql.append(" AND fecha_hora >= ?");
            valores.add(desde.atStartOfDay().toString());
        }
        if (hasta != null) {
            sql.append(" AND fecha_hora < ?");
            valores.add(hasta.plusDays(1).atStartOfDay().toString());
        }
        if (usuario != null) {
            sql.append(" AND usuario = ?");
            valores.add(usuario);
        }
        if (accion != null) {
            sql.append(" AND accion = ?");
            valores.add(accion);
        }
        sql.append(" ORDER BY fecha_hora DESC, id DESC LIMIT ?");
        valores.add(limite);
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < valores.size(); i++) {
                ps.setObject(i + 1, valores.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<RonaldmichaRegistroAuditoria> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new RonaldmichaRegistroAuditoria(
                            rs.getLong("id"),
                            rs.getString("usuario"),
                            LocalDateTime.parse(rs.getString("fecha_hora")),
                            rs.getString("accion"),
                            rs.getString("detalle")));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer la auditoría", e);
        }
    }

    public List<String> distintos(boolean usuarios) {
        String columna = usuarios ? "usuario" : "accion";
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("SELECT DISTINCT " + columna + " FROM auditoria ORDER BY 1");
             ResultSet rs = ps.executeQuery()) {
            List<String> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(rs.getString(1));
            }
            return lista;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer la auditoría", e);
        }
    }
}
