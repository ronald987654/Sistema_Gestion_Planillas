package com.planillaperu.repository;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class RonaldmichaDatabase {

    private static final Logger log = LoggerFactory.getLogger(RonaldmichaDatabase.class);

    private final Path archivo;
    private final SQLiteDataSource dataSource;

    public RonaldmichaDatabase(Path archivo) {
        this.archivo = archivo;
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        this.dataSource = new SQLiteDataSource(config);
        this.dataSource.setUrl("jdbc:sqlite:" + archivo.toAbsolutePath());
    }

    public static Path archivoPorDefecto() {
        return Path.of(System.getProperty("user.home"), "planilla.db");
    }

    public void migrar() {
        log.info("Migrando base de datos {}", archivo.toAbsolutePath());
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.repair();
        flyway.migrate();
    }

    public void restablecer() {
        log.warn("Restableciendo base de datos {}", archivo.toAbsolutePath());
        try (Connection c = conexion(); Statement st = c.createStatement()) {
            List<String> tablas = new ArrayList<>();
            try (ResultSet rs = st.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")) {
                while (rs.next()) {
                    tablas.add(rs.getString(1));
                }
            }
            st.execute("PRAGMA foreign_keys = OFF");
            for (String tabla : tablas) {
                st.execute("DROP TABLE IF EXISTS \"" + tabla.replace("\"", "\"\"") + "\"");
            }
            st.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo vaciar la base de datos", e);
        }
        migrar();
    }

    public void respaldar(Path destino) {
        try {
            Files.deleteIfExists(destino);
        } catch (IOException e) {
            throw new RonaldmichaDatosException("No se pudo reemplazar " + destino.getFileName(), e);
        }
        try (Connection c = conexion(); PreparedStatement ps = c.prepareStatement("VACUUM INTO ?")) {
            ps.setString(1, destino.toAbsolutePath().toString());
            ps.execute();
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo crear la copia de seguridad", e);
        }
    }

    public static String validarRespaldo(Path origen) {
        if (!Files.isRegularFile(origen)) {
            throw new RonaldmichaDatosException("El archivo no existe: " + origen.getFileName(), null);
        }
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        SQLiteDataSource ds = new SQLiteDataSource(config);
        ds.setUrl("jdbc:sqlite:" + origen.toAbsolutePath());
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            List<String> tablas = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
                while (rs.next()) {
                    tablas.add(rs.getString(1));
                }
            }
            if (!tablas.containsAll(List.of("flyway_schema_history", "trabajador", "usuario", "parametros_version"))) {
                throw new RonaldmichaDatosException(origen.getFileName() + " no es una copia de seguridad de Planilla Perú.", null);
            }
            try (ResultSet rs = st.executeQuery("SELECT version FROM flyway_schema_history WHERE success = 1"
                    + " AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")) {
                return rs.next() ? rs.getString(1) : "?";
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException(origen.getFileName() + " no es una base de datos SQLite válida.", e);
        }
    }

    public void restaurar(Path origen, Path respaldoPrevio) {
        respaldar(respaldoPrevio);
        log.warn("Restaurando {} desde {}", archivo.toAbsolutePath(), origen.toAbsolutePath());
        try {
            Path temporal = archivo.resolveSibling(archivo.getFileName() + ".restaurando");
            Files.copy(origen, temporal, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RonaldmichaDatosException("No se pudo restaurar la base de datos (la anterior quedó en "
                    + respaldoPrevio.getFileName() + ")", e);
        }
        migrar();
    }

    public String versionEsquema() {
        try (Connection c = conexion(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM flyway_schema_history WHERE success = 1"
                     + " AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")) {
            return rs.next() ? rs.getString(1) : "0";
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer la versión de la base de datos", e);
        }
    }

    public Connection conexion() throws SQLException {
        return dataSource.getConnection();
    }

    public Path archivo() {
        return archivo;
    }
}
