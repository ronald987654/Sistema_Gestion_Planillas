package com.planillaperu.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaMigracionTest {

    @TempDir
    Path carpeta;

    private RonaldmichaDatabase db;

    @BeforeEach
    void preparar() {
        db = RonaldmichaBaseDatosPrueba.crear(carpeta);
    }

    @Test
    void creaTodasLasTablas() throws SQLException {
        List<String> tablas = new ArrayList<>();
        try (Connection c = db.conexion(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (rs.next()) {
                tablas.add(rs.getString(1));
            }
        }
        assertThat(tablas).contains("empresa", "parametros_version", "afp_comision", "trabajador", "horario",
                "asistencia", "feriado_extra", "novedad_mes", "horas_extra_manual", "concepto_extra", "periodo",
                "ajuste_beneficio", "vacacion", "liquidacion", "usuario", "aprobacion", "auditoria",
                "documento_emitido", "configuracion");
    }

    @Test
    void cargaDatosDeEjemplo() throws SQLException {
        assertThat(contar("SELECT COUNT(*) FROM parametros_version")).isEqualTo(3);
        assertThat(contar("SELECT COUNT(*) FROM afp_comision")).isEqualTo(12);
        assertThat(contar("SELECT COUNT(*) FROM trabajador WHERE num_doc BETWEEN '40000001' AND '40000007'"))
                .isEqualTo(7);
        assertThat(contar("SELECT COUNT(*) FROM horario")).isEqualTo(7);
        assertThat(contar("SELECT COUNT(DISTINCT regimen_laboral) FROM trabajador")).isEqualTo(3);
        assertThat(contar("SELECT COUNT(*) FROM trabajador WHERE salud = 'EPS'")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM trabajador WHERE salud = 'SIS'")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM usuario")).isEqualTo(3);
    }

    @Test
    void aplicaClavesForaneas() {
        assertThatThrownBy(() -> ejecutar(
                "INSERT INTO horario (trabajador_id, hora_ingreso, hora_salida) VALUES (9999, '08:00', '17:00')"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("FOREIGN KEY");
    }

    @Test
    void rechazaValoresFueraDelCatalogo() {
        assertThatThrownBy(() -> ejecutar("UPDATE trabajador SET salud = 'Otro' WHERE num_doc = '40000001'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("CHECK");
    }

    private int contar(String sql) throws SQLException {
        try (Connection c = db.conexion(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void ejecutar(String sql) throws SQLException {
        try (Connection c = db.conexion(); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
