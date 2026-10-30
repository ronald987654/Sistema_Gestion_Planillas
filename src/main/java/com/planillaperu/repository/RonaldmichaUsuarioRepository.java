package com.planillaperu.repository;

import com.planillaperu.domain.RonaldmichaCuentaUsuario;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RonaldmichaUsuarioRepository {

    private static final String SELECT_CUENTA = "SELECT u.*,"
            + " t.ap_paterno || ' ' || COALESCE(t.ap_materno, '') || ', ' || t.nombres AS nombre"
            + " FROM usuario u LEFT JOIN trabajador t ON t.id = u.trabajador_id";

    private final RonaldmichaDatabase db;

    public RonaldmichaUsuarioRepository(RonaldmichaDatabase db) {
        this.db = db;
    }

    public Optional<RonaldmichaUsuario> buscarPorUsuario(String usuario) {
        String sql = "SELECT id, usuario, hash_password, rol, trabajador_id, activo FROM usuario WHERE usuario = ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long trabajadorId = rs.getLong("trabajador_id");
                Long trabajador = rs.wasNull() ? null : trabajadorId;
                return Optional.of(new RonaldmichaUsuario(
                        rs.getLong("id"),
                        rs.getString("usuario"),
                        rs.getString("hash_password"),
                        RonaldmichaRol.valueOf(rs.getString("rol")),
                        trabajador,
                        rs.getInt("activo") == 1));
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer el usuario " + usuario, e);
        }
    }

    public boolean existe(String usuario) {
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM usuario WHERE lower(usuario) = lower(?)")) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer el usuario " + usuario, e);
        }
    }

    public Optional<RonaldmichaCuentaUsuario> cuenta(String usuario) {
        return unaCuenta(SELECT_CUENTA + " WHERE u.usuario = ?", usuario);
    }

    public Optional<RonaldmichaCuentaUsuario> cuenta(long id) {
        return unaCuenta(SELECT_CUENTA + " WHERE u.id = ?", id);
    }

    public List<RonaldmichaCuentaUsuario> listar() {
        String sql = SELECT_CUENTA + " ORDER BY CASE u.rol WHEN 'ADMIN' THEN 0 WHEN 'APROBADOR' THEN 1 ELSE 2 END,"
                + " u.usuario";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<RonaldmichaCuentaUsuario> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(leer(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer los usuarios", e);
        }
    }

    public Optional<RonaldmichaCuentaUsuario> deTrabajador(long trabajadorId, RonaldmichaRol rol) {
        String sql = SELECT_CUENTA + " WHERE u.trabajador_id = ? AND u.rol = ? LIMIT 1";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, trabajadorId);
            ps.setString(2, rol.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(leer(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer el usuario del trabajador", e);
        }
    }

    public int contarActivos(RonaldmichaRol rol) {
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM usuario WHERE rol = ? AND activo = 1")) {
            ps.setString(1, rol.name());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo contar los usuarios", e);
        }
    }

    public long insertar(String usuario, String hash, RonaldmichaRol rol, Long trabajadorId) {
        String sql = "INSERT INTO usuario (usuario, hash_password, rol, trabajador_id, activo, debe_cambiar_clave)"
                + " VALUES (?, ?, ?, ?, 1, 1)";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, usuario);
            ps.setString(2, hash);
            ps.setString(3, rol.name());
            ps.setObject(4, trabajadorId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo crear el usuario " + usuario, e);
        }
    }

    public void cambiarActivo(long id, boolean activo) {
        ejecutar("UPDATE usuario SET activo = ? WHERE id = ?", activo ? 1 : 0, id);
    }

    public void cambiarClave(long id, String hash, boolean temporal) {
        ejecutar("UPDATE usuario SET hash_password = ?, debe_cambiar_clave = ?, intentos_fallidos = 0,"
                + " bloqueado_hasta = NULL WHERE id = ?", hash, temporal ? 1 : 0, id);
    }

    public void registrarFallo(long id, int intentos, LocalDateTime bloqueadoHasta) {
        ejecutar("UPDATE usuario SET intentos_fallidos = ?, bloqueado_hasta = ? WHERE id = ?", intentos,
                bloqueadoHasta == null ? null : bloqueadoHasta.toString(), id);
    }

    public void desbloquear(long id) {
        ejecutar("UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = ?", id);
    }

    public void registrarAcceso(long id, LocalDateTime fecha) {
        ejecutar("UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL, ultimo_acceso = ? WHERE id = ?",
                fecha.toString(), id);
    }

    private void ejecutar(String sql, Object... valores) {
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                ps.setObject(i + 1, valores[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo actualizar el usuario", e);
        }
    }

    private Optional<RonaldmichaCuentaUsuario> unaCuenta(String sql, Object clave) {
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, clave);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(leer(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer el usuario", e);
        }
    }

    private static RonaldmichaCuentaUsuario leer(ResultSet rs) throws SQLException {
        long trabajador = rs.getLong("trabajador_id");
        Long trabajadorId = rs.wasNull() ? null : trabajador;
        return new RonaldmichaCuentaUsuario(
                rs.getLong("id"),
                rs.getString("usuario"),
                RonaldmichaRol.valueOf(rs.getString("rol")),
                trabajadorId,
                trabajadorId == null ? null : rs.getString("nombre"),
                rs.getInt("activo") == 1,
                rs.getInt("intentos_fallidos"),
                fecha(rs.getString("bloqueado_hasta")),
                fecha(rs.getString("ultimo_acceso")),
                rs.getInt("debe_cambiar_clave") == 1);
    }

    private static LocalDateTime fecha(String texto) {
        return texto == null ? null : LocalDateTime.parse(texto);
    }
}
