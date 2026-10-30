package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaSituacionPeriodo;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaPeriodoYDemoServiceTest {

    @TempDir
    Path carpeta;

    private RonaldmichaDatabase db;
    private Clock reloj;

    @BeforeEach
    void preparar() {
        db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
    }

    @Test
    void situacionDelPeriodo() throws SQLException {
        RonaldmichaPeriodoService periodos = new RonaldmichaPeriodoService(new RonaldmichaPeriodoRepository(db), reloj);
        ejecutar("INSERT INTO periodo (periodo, estado) VALUES ('202609', 'Cerrado')");

        assertThat(periodos.situacion(YearMonth.of(2026, 9))).isEqualTo(RonaldmichaSituacionPeriodo.CERRADO);
        assertThat(periodos.situacion(YearMonth.of(2026, 10))).isEqualTo(RonaldmichaSituacionPeriodo.ABIERTO);
        assertThat(periodos.situacion(YearMonth.of(2026, 11))).isEqualTo(RonaldmichaSituacionPeriodo.FUTURO);
    }

    @Test
    void periodoDeTrabajoEsElPrimerMesNoCerrado() throws SQLException {
        RonaldmichaPeriodoService periodos = new RonaldmichaPeriodoService(new RonaldmichaPeriodoRepository(db), reloj);
        assertThat(periodos.periodoDeTrabajo()).as("sin cierres: el mes actual").isEqualTo(YearMonth.of(2026, 10));

        ejecutar("INSERT INTO periodo (periodo, estado) VALUES ('202607', 'Cerrado'), ('202608', 'Cerrado')");
        assertThat(periodos.periodoDeTrabajo()).as("el siguiente al último cerrado").isEqualTo(YearMonth.of(2026, 9));

        ejecutar("INSERT INTO periodo (periodo, estado) VALUES ('202609', 'Abierto')");
        ejecutar("UPDATE periodo SET estado = 'Abierto' WHERE periodo = '202607'");
        assertThat(periodos.periodoDeTrabajo()).as("un mes reabierto va primero").isEqualTo(YearMonth.of(2026, 7));
    }

    @Test
    void restablecerVuelveALosDatosDeDemostracion() throws SQLException {
        RonaldmichaAuditoriaService auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        RonaldmichaTrabajadorRepository trabajadores = new RonaldmichaTrabajadorRepository(db);
        ejecutar("DELETE FROM horario");
        ejecutar("DELETE FROM usuario");
        ejecutar("DELETE FROM trabajador");
        assertThat(trabajadores.listarActivos(LocalDate.now(reloj))).isEmpty();

        new RonaldmichaDatosDemoService(db, auditoria).restablecer("sistema");

        assertThat(trabajadores.listarActivos(LocalDate.now(reloj))).hasSize(7);
        assertThat(auditoria.recientes(10)).singleElement()
                .satisfies(r -> assertThat(r.accion()).isEqualTo("DATOS_DEMO_RESTABLECIDOS"));
    }

    private void ejecutar(String sql) throws SQLException {
        try (Connection c = db.conexion(); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
