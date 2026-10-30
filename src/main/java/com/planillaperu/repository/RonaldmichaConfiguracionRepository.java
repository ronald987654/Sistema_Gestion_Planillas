package com.planillaperu.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class RonaldmichaConfiguracionRepository {

    public static final String INACTIVIDAD_MINUTOS = "sesion.inactividad_minutos";
    public static final String INTENTOS_MAXIMOS = "acceso.intentos_maximos";
    public static final String BLOQUEO_MINUTOS = "acceso.bloqueo_minutos";

    private final RonaldmichaDatabase db;

    public RonaldmichaConfiguracionRepository(RonaldmichaDatabase db) {
        this.db = db;
    }

    public Optional<String> valor(String clave) {
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("SELECT valor FROM configuracion WHERE clave = ?")) {
            ps.setString(1, clave);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer la configuración " + clave, e);
        }
    }

    public int entero(String clave, int porDefecto) {
        try {
            return valor(clave).map(String::trim).map(Integer::parseInt).orElse(porDefecto);
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    public void guardar(String clave, String valor) {
        String sql = "INSERT INTO configuracion (clave, valor) VALUES (?, ?)"
                + " ON CONFLICT (clave) DO UPDATE SET valor = excluded.valor";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clave);
            ps.setString(2, valor);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo guardar la configuración " + clave, e);
        }
    }
}
