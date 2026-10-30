package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaAprobacionRepository;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaBeneficioRepository;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaDocumentoRepository;
import com.planillaperu.repository.RonaldmichaEmpresaRepository;
import com.planillaperu.repository.RonaldmichaLiquidacionRepository;
import com.planillaperu.repository.RonaldmichaParametrosRepository;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;
import com.planillaperu.repository.RonaldmichaPlanillaRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.repository.RonaldmichaVacacionRepository;
import com.planillaperu.service.RonaldmichaInicioService.Alerta;
import com.planillaperu.service.RonaldmichaInicioService.GrupoPension;
import com.planillaperu.service.RonaldmichaInicioService.Kpi;
import com.planillaperu.service.RonaldmichaInicioService.Nivel;
import com.planillaperu.service.RonaldmichaInicioService.Paso;
import com.planillaperu.service.RonaldmichaInicioService.Resumen;
import com.planillaperu.service.RonaldmichaReporte.Formato;
import com.planillaperu.service.RonaldmichaReportesService.Parametros;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaInicioServiceTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);

    @TempDir
    Path carpeta;

    private RonaldmichaInicioService servicio;
    private RonaldmichaReportesService reportes;
    private RonaldmichaPeriodoRepository periodoRepositorio;
    private RonaldmichaUsuario admin;

    @BeforeEach
    void preparar() {
        RonaldmichaDatabase db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
        RonaldmichaAuditoriaService auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        RonaldmichaTrabajadorRepository trabajadores = new RonaldmichaTrabajadorRepository(db);
        periodoRepositorio = new RonaldmichaPeriodoRepository(db);
        RonaldmichaParametrosService parametros = new RonaldmichaParametrosService(new RonaldmichaParametrosRepository(db), periodoRepositorio,
                trabajadores, auditoria, reloj);
        RonaldmichaPeriodoService periodos = new RonaldmichaPeriodoService(periodoRepositorio, reloj);
        RonaldmichaAsistenciaRepository asistenciaRepositorio = new RonaldmichaAsistenciaRepository(db);
        RonaldmichaAsistenciaService asistencia = new RonaldmichaAsistenciaService(asistenciaRepositorio, trabajadores, periodos, parametros,
                auditoria, reloj);
        RonaldmichaPlanillaService planilla = new RonaldmichaPlanillaService(trabajadores, asistenciaRepositorio, new RonaldmichaPlanillaRepository(db),
                periodoRepositorio, periodos, parametros, asistencia, auditoria, reloj);
        RonaldmichaEmpresaService empresa = new RonaldmichaEmpresaService(new RonaldmichaEmpresaRepository(db), auditoria);
        RonaldmichaBeneficiosService beneficios = new RonaldmichaBeneficiosService(trabajadores, new RonaldmichaBeneficioRepository(db), planilla,
                parametros, empresa, auditoria);
        RonaldmichaVacacionRepository vacacionRepositorio = new RonaldmichaVacacionRepository(db);
        RonaldmichaVacacionesService vacaciones = new RonaldmichaVacacionesService(trabajadores, vacacionRepositorio, periodos, empresa,
                auditoria, reloj);
        RonaldmichaLiquidacionService liquidaciones = new RonaldmichaLiquidacionService(trabajadores, new RonaldmichaLiquidacionRepository(db),
                vacacionRepositorio, beneficios, parametros, periodos, empresa, auditoria, reloj);
        RonaldmichaDocumentoRepository documentos = new RonaldmichaDocumentoRepository(db);
        reportes = new RonaldmichaReportesService(trabajadores, planilla, beneficios, liquidaciones, vacaciones, parametros, empresa,
                documentos, RonaldmichaMailService.NO_CONFIGURADO, auditoria, reloj);
        servicio = new RonaldmichaInicioService(planilla, parametros, empresa, vacaciones, periodos, asistenciaRepositorio,
                documentos, new RonaldmichaAprobacionService(new RonaldmichaAprobacionRepository(db), auditoria, reloj), reloj);
        admin = new RonaldmichaUsuario(1L, "admin", "", RonaldmichaRol.ADMIN, null, true);
    }

    @Test
    void cicloDeLaPlanillaDelMes() {
        Resumen r = servicio.resumen(admin, OCTUBRE);

        assertThat(r.ciclo().pasos()).extracting(Paso::titulo).containsExactly("Empresa y parámetros", "Trabajadores",
                "Asistencia", "Novedades", "Revisión", "Cierre", "Boletas emitidas");
        assertThat(paso(r, "Empresa y parámetros").hecho()).isFalse();
        assertThat(paso(r, "Trabajadores").hecho()).isTrue();
        assertThat(paso(r, "Revisión").hecho()).isTrue();
        assertThat(paso(r, "Cierre").hecho()).isFalse();
        assertThat(paso(r, "Boletas emitidas").hecho()).isFalse();
        assertThat(r.ciclo().pasos()).extracting(Paso::destino).containsExactly(RonaldmichaInicioService.Destino.EMPRESA,
                RonaldmichaInicioService.Destino.TRABAJADORES, RonaldmichaInicioService.Destino.ASISTENCIA, RonaldmichaInicioService.Destino.PLANILLA,
                RonaldmichaInicioService.Destino.PLANILLA_DETALLE, RonaldmichaInicioService.Destino.PLANILLA_CIERRE,
                RonaldmichaInicioService.Destino.BOLETAS);

        reportes.exportar(admin, RonaldmichaReporte.BOLETA_PAGO, Formato.PDF, new Parametros(OCTUBRE, null, true));
        periodoRepositorio.cerrar(OCTUBRE, "admin", LocalDateTime.of(2026, 10, 31, 18, 0));
        Resumen despues = servicio.resumen(admin, OCTUBRE);
        assertThat(paso(despues, "Boletas emitidas").hecho()).isTrue();
        assertThat(paso(despues, "Cierre").hecho()).isTrue();
        assertThat(despues.ciclo().hechos()).isGreaterThan(r.ciclo().hechos());
        assertThat(despues.ciclo().progreso()).isBetween(0.0, 1.0);
    }

    @Test
    void kpisSeriesYDistribuciones() {
        Resumen r = servicio.resumen(admin, OCTUBRE);

        assertThat(r.kpis()).extracting(Kpi::titulo)
                .containsExactly("Trabajadores en planilla", "Neto a pagar", "Aportes del empleador", "Costo laboral del mes");
        Kpi trabajadores = r.kpis().getFirst();
        assertThat(trabajadores.actual()).isEqualByComparingTo("7");
        assertThat(trabajadores.variacion()).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.0"));
        assertThat(r.kpis().get(3).variacion()).hasValueSatisfying(v -> assertThat(v.signum()).isPositive());

        assertThat(r.serie()).hasSize(6);
        assertThat(r.serie().getFirst().mes()).isEqualTo(YearMonth.of(2026, 5));
        assertThat(r.serie().getLast().mes()).isEqualTo(OCTUBRE);
        assertThat(r.serie()).allMatch(RonaldmichaInicioService.PuntoMes::conDatos);
        assertThat(r.serie().getLast().costoLaboral()).isGreaterThan(r.serie().getLast().neto());

        assertThat(r.areas()).isNotEmpty();
        assertThat(r.areas()).extracting(RonaldmichaInicioService.CostoArea::costo).isSortedAccordingTo((a, b) -> b.compareTo(a));
        assertThat(r.pensiones()).extracting(GrupoPension::grupo, GrupoPension::trabajadores)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("ONP", 2),
                        org.assertj.core.groups.Tuple.tuple("AFP flujo", 2),
                        org.assertj.core.groups.Tuple.tuple("AFP mixta", 3));
        assertThat(r.pensiones()).allSatisfy(g -> assertThat(g.monto().signum()).isPositive());
        assertThat(r.serie().getLast().trabajadores()).isEqualTo(7);
        assertThat(r.trabajadores()).isEqualTo(7);
        assertThat(YearMonth.from(r.fechaPago())).isEqualTo(OCTUBRE);

        Resumen setiembre = servicio.resumen(admin, YearMonth.of(2026, 9));
        assertThat(setiembre.serie().getLast().mes()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(setiembre.kpis().get(3).actual()).isNotEqualByComparingTo(r.kpis().get(3).actual());
    }

    @Test
    void alertasYCalendario() {
        Resumen r = servicio.resumen(admin, OCTUBRE);

        assertThat(r.alertas()).anyMatch(a -> a.nivel() == Nivel.CRITICO && a.titulo().contains("empresa incompletos"));
        assertThat(r.alertas()).anyMatch(a -> a.nivel() == Nivel.ATENCION && a.titulo().equals("Vacaciones acumuladas")
                && a.detalle().contains("Rojas Castillo, Lucía Fernanda"));
        assertThat(r.alertas()).anyMatch(a -> a.nivel() == Nivel.ATENCION
                && a.titulo().equals("Nuevos parámetros vigentes desde octubre 2026"));
        assertThat(r.alertas()).extracting(Alerta::titulo).noneMatch(t -> t.startsWith("Básico menor") || t.contains("CUSPP"));
        assertThat(r.obligaciones()).hasSize(RonaldmichaInicioService.OBLIGACIONES_MAXIMAS)
                .noneMatch(o -> o.titulo().equals("PLAME"))
                .anyMatch(o -> o.titulo().equals("Depósito de CTS"));

        Resumen sinParametros = servicio.resumen(admin, YearMonth.of(2024, 6));
        assertThat(sinParametros.alertas()).anyMatch(a -> a.titulo().startsWith("Sin parámetros para junio 2024"));
        assertThatThrownBy(() -> servicio.resumen(new RonaldmichaUsuario(5L, "40000002", "", RonaldmichaRol.TRABAJADOR, 2L, true), OCTUBRE))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    private static Paso paso(Resumen r, String titulo) {
        return r.ciclo().pasos().stream().filter(p -> p.titulo().equals(titulo)).findFirst().orElseThrow();
    }
}
