package com.planillaperu.rendimiento;

import com.planillaperu.repository.RonaldmichaAsistenciaRepository;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBeneficioRepository;
import com.planillaperu.repository.RonaldmichaEmpresaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaParametrosRepository;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;
import com.planillaperu.repository.RonaldmichaPlanillaRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.service.RonaldmichaAsistenciaService;
import com.planillaperu.service.RonaldmichaAuditoriaService;
import com.planillaperu.service.RonaldmichaBeneficiosService;
import com.planillaperu.service.RonaldmichaEmpresaService;
import com.planillaperu.service.RonaldmichaParametrosService;
import com.planillaperu.service.RonaldmichaPeriodoService;
import com.planillaperu.service.RonaldmichaPlanillaService;
import com.planillaperu.service.RonaldmichaPlanillaService.PlanillaMes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaPlanillaRendimientoTest {

    private static final int TRABAJADORES = 500;
    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);

    @TempDir
    Path carpeta;

    private RonaldmichaPlanillaService planilla;

    @BeforeEach
    void preparar() throws SQLException {
        RonaldmichaDatabase db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        copiarTrabajadores(db, TRABAJADORES - 7);
        Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
        RonaldmichaAuditoriaService auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        RonaldmichaTrabajadorRepository trabajadores = new RonaldmichaTrabajadorRepository(db);
        RonaldmichaPeriodoRepository periodoRepositorio = new RonaldmichaPeriodoRepository(db);
        RonaldmichaParametrosService parametros = new RonaldmichaParametrosService(new RonaldmichaParametrosRepository(db), periodoRepositorio,
                trabajadores, auditoria, reloj);
        RonaldmichaPeriodoService periodos = new RonaldmichaPeriodoService(periodoRepositorio, reloj);
        RonaldmichaAsistenciaRepository asistenciaRepositorio = new RonaldmichaAsistenciaRepository(db);
        RonaldmichaAsistenciaService asistencia = new RonaldmichaAsistenciaService(asistenciaRepositorio, trabajadores, periodos, parametros,
                auditoria, reloj);
        planilla = new RonaldmichaPlanillaService(trabajadores, asistenciaRepositorio, new RonaldmichaPlanillaRepository(db),
                periodoRepositorio, periodos, parametros, asistencia, auditoria, reloj);
        new RonaldmichaBeneficiosService(trabajadores, new RonaldmichaBeneficioRepository(db), planilla, parametros,
                new RonaldmichaEmpresaService(new RonaldmichaEmpresaRepository(db), auditoria), auditoria);
    }

    @Test
    void planillaDe500TrabajadoresEnMenosDeUnSegundo() {
        planilla.delMes(OCTUBRE.minusMonths(1));

        long inicio = System.nanoTime();
        PlanillaMes pm = planilla.delMes(OCTUBRE);
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        System.out.println("Planilla de " + pm.filas().size() + " trabajadores: " + ms + " ms");

        assertThat(pm.error()).isEmpty();
        assertThat(pm.filas()).hasSize(TRABAJADORES);
        assertThat(ms).as("milisegundos").isLessThan(1000);
    }

    static void copiarTrabajadores(RonaldmichaDatabase db, int cantidad) throws SQLException {
        String columnas = "tipo_doc, ap_paterno, ap_materno, nombres, celular, correo, fecha_ingreso, fecha_cese,"
                + " area, cargo, tipo_contrato, regimen_laboral, centro_costo, sucursal, sueldo, movilidad, salud,"
                + " sistema_pension, cuspp, tipo_comision, asig_familiar, vida_ley, tipo_pago, banco_sueldo, cta_sueldo,"
                + " banco_cts, cta_cts";
        try (Connection c = db.conexion(); Statement st = c.createStatement()) {
            st.execute("WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < " + cantidad + ")"
                    + " INSERT INTO trabajador (codigo, num_doc, " + columnas + ")"
                    + " SELECT printf('F%04d', n.i), printf('7%07d', n.i), " + columnas
                    + " FROM n JOIN trabajador t ON t.num_doc = printf('4000000%d', 1 + (n.i % 7))");
            st.execute("INSERT INTO horario (trabajador_id, hora_ingreso, hora_salida, refrigerio_min, tolerancia_min,"
                    + " dias_laborables, no_fiscalizado)"
                    + " SELECT t.id, h.hora_ingreso, h.hora_salida, h.refrigerio_min, h.tolerancia_min, h.dias_laborables,"
                    + " h.no_fiscalizado FROM trabajador t"
                    + " JOIN trabajador o ON o.num_doc = printf('4000000%d', 1 + (CAST(substr(t.num_doc, 2) AS INTEGER) % 7))"
                    + " JOIN horario h ON h.trabajador_id = o.id WHERE t.codigo LIKE 'F%'");
        }
    }
}
