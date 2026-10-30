package com.planillaperu.service;

import com.planillaperu.calc.RonaldmichaResultadoPlanilla;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla.Concepto;
import com.planillaperu.domain.RonaldmichaConceptoExtra;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaSituacionPeriodo;
import com.planillaperu.domain.RonaldmichaTipoConceptoExtra;
import com.planillaperu.domain.RonaldmichaTratamientoHe;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository.HoraExtraManual;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaParametrosRepository;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;
import com.planillaperu.repository.RonaldmichaPlanillaRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.service.RonaldmichaPlanillaService.Borrador;
import com.planillaperu.service.RonaldmichaPlanillaService.Detalle;
import com.planillaperu.service.RonaldmichaPlanillaService.FilaPlanilla;
import com.planillaperu.service.RonaldmichaPlanillaService.PlanillaMes;
import com.planillaperu.service.RonaldmichaPlanillaService.Totales;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaPlanillaServiceTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);

    @TempDir
    Path carpeta;

    private RonaldmichaDatabase db;
    private RonaldmichaAuditoriaService auditoria;
    private RonaldmichaPlanillaService servicio;
    private RonaldmichaPeriodoService periodos;
    private RonaldmichaUsuario admin;
    private RonaldmichaUsuario aprobador;
    private long carlos;

    @BeforeEach
    void preparar() {
        db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
        auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        RonaldmichaTrabajadorRepository trabajadores = new RonaldmichaTrabajadorRepository(db);
        RonaldmichaPeriodoRepository periodoRepositorio = new RonaldmichaPeriodoRepository(db);
        RonaldmichaParametrosService parametros = new RonaldmichaParametrosService(new RonaldmichaParametrosRepository(db), periodoRepositorio,
                trabajadores, auditoria, reloj);
        periodos = new RonaldmichaPeriodoService(periodoRepositorio, reloj);
        RonaldmichaAsistenciaRepository asistenciaRepositorio = new RonaldmichaAsistenciaRepository(db);
        RonaldmichaAsistenciaService asistencia = new RonaldmichaAsistenciaService(asistenciaRepositorio, trabajadores, periodos, parametros,
                auditoria, reloj);
        servicio = new RonaldmichaPlanillaService(trabajadores, asistenciaRepositorio, new RonaldmichaPlanillaRepository(db),
                periodoRepositorio, periodos, parametros, asistencia, auditoria, reloj);
        admin = new RonaldmichaUsuario(1L, "admin", "", RonaldmichaRol.ADMIN, null, true);
        aprobador = new RonaldmichaUsuario(3L, "aprobador", "", RonaldmichaRol.APROBADOR, null, true);
        carlos = trabajadores.listarTodos().stream().filter(t -> t.numDoc().equals("40000002")).findFirst()
                .orElseThrow().id();
    }

    @Test
    void consultaLaPlanillaDelMes() {
        PlanillaMes p = servicio.consultar(aprobador, OCTUBRE);

        assertThat(p.situacion()).isEqualTo(RonaldmichaSituacionPeriodo.ABIERTO);
        assertThat(p.editable()).isTrue();
        assertThat(p.filas()).hasSize(7);
        RonaldmichaResultadoPlanilla r = fila(p, carlos).resultado();
        assertThat(r.diasLaborados()).isEqualTo(30);
        assertThat(r.basico()).isEqualByComparingTo("2800.00");
        assertThat(r.totalIngresos()).isEqualByComparingTo("2950.00");
        assertThat(r.pension().aporte()).isEqualByComparingTo("280.00");
        assertThat(r.essalud()).isEqualByComparingTo("252.00");

        Totales t = Totales.de(p.filas());
        assertThat(t.trabajadores()).isEqualTo(7);
        assertThat(t.neto()).isEqualByComparingTo(t.ingresos().subtract(t.descuentos()));
        assertThatThrownBy(() -> servicio.consultar(new RonaldmichaUsuario(5L, "40000002", "", RonaldmichaRol.TRABAJADOR, carlos, true),
                OCTUBRE)).isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    @Test
    void sinParametrosNoHayFilas() {
        PlanillaMes p = servicio.consultar(admin, YearMonth.of(2024, 6));

        assertThat(p.filas()).isEmpty();
        assertThat(p.error()).isPresent();
        assertThat(p.editable()).isFalse();
    }

    @Test
    void simulaYGuardaNovedadesSobretiempoYConceptos() {
        Borrador b = new Borrador(2, 0, 0, 0, 0, 0, new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("50"),
                BigDecimal.ZERO,
                List.of(new HoraExtraManual(LocalDate.of(2026, 10, 5), new BigDecimal("2.5"), RonaldmichaTratamientoHe.PAGAR, true),
                        new HoraExtraManual(LocalDate.of(2026, 10, 6), new BigDecimal("3"), RonaldmichaTratamientoHe.COMPENSAR, true)),
                List.of(new RonaldmichaConceptoExtra(RonaldmichaTipoConceptoExtra.ING, "1000", "Bono por meta", new BigDecimal("200"))));
        Detalle d = servicio.detalle(admin, carlos, OCTUBRE);
        assertThat(d.desdeAsistencia()).isFalse();
        assertThat(d.horasDiarias()).isEqualByComparingTo("8");

        RonaldmichaResultadoPlanilla simulado = servicio.simular(d, b).resultado();
        RonaldmichaResultadoPlanilla guardado = servicio.guardar(admin, carlos, OCTUBRE, b);

        assertThat(guardado).isEqualTo(simulado);
        assertThat(guardado.basico()).isEqualByComparingTo("2613.33");
        assertThat(guardado.horasExtra25()).isEqualByComparingTo("29.17");
        assertThat(guardado.horasExtra35()).isEqualByComparingTo("7.88");
        assertThat(guardado.adelanto()).isEqualByComparingTo("50.00");
        assertThat(guardado.conceptos()).extracting(Concepto::descripcion).contains("Bono por meta", "Comisiones");

        assertThat(fila(servicio.consultar(admin, OCTUBRE), carlos).resultado()).isEqualTo(guardado);
        Borrador leido = servicio.detalle(admin, carlos, OCTUBRE).guardado();
        assertThat(leido.inasistencias()).isEqualTo(2);
        assertThat(leido.horasExtra()).hasSize(2);
        assertThat(leido.conceptos()).singleElement().satisfies(c -> {
            assertThat(c.codigoPlame()).isEqualTo("1000");
            assertThat(c.monto()).isEqualByComparingTo("200.00");
        });
        assertThat(acciones()).contains("PLANILLA_NOVEDADES_GUARDADAS");
        assertThatThrownBy(() -> servicio.guardar(aprobador, carlos, OCTUBRE, b))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    @Test
    void validaElBorrador() {
        Detalle d = servicio.detalle(admin, carlos, OCTUBRE);
        Borrador b = new Borrador(20, 8, 0, 0, 0, 5, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("-1"),
                List.of(new HoraExtraManual(LocalDate.of(2026, 11, 2), BigDecimal.ONE, RonaldmichaTratamientoHe.PAGAR, true),
                        new HoraExtraManual(LocalDate.of(2026, 10, 7), new BigDecimal("0.3"), RonaldmichaTratamientoHe.PAGAR, true),
                        new HoraExtraManual(LocalDate.of(2026, 10, 8), new BigDecimal("5"), RonaldmichaTratamientoHe.PAGAR, true),
                        new HoraExtraManual(LocalDate.of(2026, 10, 8), new BigDecimal("4"), RonaldmichaTratamientoHe.PAGAR, true)),
                List.of(new RonaldmichaConceptoExtra(RonaldmichaTipoConceptoExtra.DES, "12", "", BigDecimal.ZERO)));

        List<String> errores = servicio.validar(d, b);

        assertThat(errores).anyMatch(e -> e.contains("suman 33: no pueden pasar de los 30 días"))
                .anyMatch(e -> e.contains("no pueden ser negativos"))
                .anyMatch(e -> e.contains("02/11/2026 no es del periodo 10/2026"))
                .anyMatch(e -> e.contains("múltiplos de 0.5 (revise el 07/10/2026)"))
                .anyMatch(e -> e.contains("08/10/2026 suma 17 h (jornada 8 h + sobretiempo 9 h)"))
                .anyMatch(e -> e.contains("debe tener 4 dígitos"))
                .anyMatch(e -> e.contains("Escriba la descripción"))
                .anyMatch(e -> e.contains("mayor que cero"));
        assertThatThrownBy(() -> servicio.guardar(admin, carlos, OCTUBRE, b)).isInstanceOf(RonaldmichaValidacionException.class);
        assertThat(servicio.simular(d, b).errores()).isEqualTo(errores);
    }

    @Test
    void conMarcacionesLosDiasVienenDeAsistencia() throws SQLException {
        ejecutar("INSERT INTO asistencia (trabajador_id, fecha, hora_ingreso, hora_salida, he_autorizada, he_tratamiento)"
                + " VALUES (" + carlos + ", '2026-10-02', '08:00', '20:30', 1, 'pago')");
        Borrador b = new Borrador(5, 0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, List.of(), List.of());

        Detalle d = servicio.detalle(admin, carlos, OCTUBRE);
        RonaldmichaResultadoPlanilla r = servicio.guardar(admin, carlos, OCTUBRE, b);

        assertThat(d.desdeAsistencia()).isTrue();
        assertThat(d.asistencia().he25Min()).isEqualTo(120);
        assertThat(r.inasistencias()).isEqualTo(d.asistencia().faltas());
        assertThat(servicio.detalle(admin, carlos, OCTUBRE).guardado().inasistencias()).isZero();
        assertThat(fila(servicio.consultar(admin, OCTUBRE), carlos).desdeAsistencia()).isTrue();
    }

    @Test
    void cierraYReabreElPeriodo() {
        assertThatThrownBy(() -> servicio.cerrar(aprobador, OCTUBRE)).isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        assertThat(periodos.situacion(YearMonth.of(2026, 11))).isEqualTo(RonaldmichaSituacionPeriodo.FUTURO);

        servicio.cerrar(admin, OCTUBRE);

        assertThat(periodos.situacion(OCTUBRE)).isEqualTo(RonaldmichaSituacionPeriodo.CERRADO);
        assertThat(periodos.situacion(YearMonth.of(2026, 11))).isEqualTo(RonaldmichaSituacionPeriodo.ABIERTO);
        assertThat(periodos.situacion(YearMonth.of(2026, 12))).isEqualTo(RonaldmichaSituacionPeriodo.FUTURO);
        assertThat(servicio.situacionesDelAnio(2026)).containsEntry(OCTUBRE, RonaldmichaSituacionPeriodo.CERRADO)
                .containsEntry(YearMonth.of(2026, 12), RonaldmichaSituacionPeriodo.FUTURO).hasSize(12);
        assertThat(periodos.buscar(OCTUBRE)).hasValueSatisfying(p -> assertThat(p.cerradoPor()).isEqualTo("admin"));
        assertThat(servicio.consultar(admin, OCTUBRE).editable()).isFalse();
        assertThatThrownBy(() -> servicio.guardar(admin, carlos, OCTUBRE, Borrador.VACIO))
                .hasMessageContaining("solo lectura");
        assertThatThrownBy(() -> servicio.guardar(admin, carlos, YearMonth.of(2026, 12), Borrador.VACIO))
                .hasMessageContaining("aún no está abierto");
        assertThatThrownBy(() -> servicio.cerrar(admin, OCTUBRE)).hasMessageContaining("Solo se puede cerrar");

        assertThatThrownBy(() -> servicio.reabrir(admin, OCTUBRE, "  ")).hasMessageContaining("motivo");
        servicio.reabrir(admin, OCTUBRE, "Faltó registrar una comisión");

        assertThat(periodos.situacion(OCTUBRE)).isEqualTo(RonaldmichaSituacionPeriodo.ABIERTO);
        assertThat(periodos.buscar(OCTUBRE)).hasValueSatisfying(p ->
                assertThat(p.motivoReapertura()).isEqualTo("Faltó registrar una comisión"));
        assertThat(acciones()).contains("PLANILLA_CERRADA", "PLANILLA_REABIERTA");
        assertThat(auditoria.recientes(10)).anyMatch(r -> r.accion().equals("PLANILLA_REABIERTA")
                && r.detalle().contains("Faltó registrar una comisión"));
    }

    private static FilaPlanilla fila(PlanillaMes p, long id) {
        return p.filas().stream().filter(f -> f.trabajador().id() == id).findFirst().orElseThrow();
    }

    private List<String> acciones() {
        return auditoria.recientes(50).stream().map(r -> r.accion()).toList();
    }

    private void ejecutar(String sql) throws SQLException {
        try (Connection c = db.conexion(); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
