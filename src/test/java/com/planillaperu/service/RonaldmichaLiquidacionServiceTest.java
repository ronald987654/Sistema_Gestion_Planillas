package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaEstadoLiquidacion;
import com.planillaperu.domain.RonaldmichaLiquidacion;
import com.planillaperu.domain.RonaldmichaMotivoCese;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaBeneficioRepository;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaEmpresaRepository;
import com.planillaperu.repository.RonaldmichaLiquidacionRepository;
import com.planillaperu.repository.RonaldmichaParametrosRepository;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;
import com.planillaperu.repository.RonaldmichaPlanillaRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.repository.RonaldmichaVacacionRepository;
import com.planillaperu.service.RonaldmichaLiquidacionService.Borrador;
import com.planillaperu.service.RonaldmichaLiquidacionService.Calculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaLiquidacionServiceTest {

    private static final LocalDate CESE = LocalDate.of(2026, 10, 31);

    @TempDir
    Path carpeta;

    private RonaldmichaAuditoriaService auditoria;
    private RonaldmichaLiquidacionService servicio;
    private RonaldmichaTrabajadorRepository trabajadores;
    private RonaldmichaPeriodoRepository periodoRepositorio;
    private RonaldmichaUsuario admin;
    private RonaldmichaUsuario aprobador;

    @BeforeEach
    void preparar() {
        RonaldmichaDatabase db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
        auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        trabajadores = new RonaldmichaTrabajadorRepository(db);
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
        servicio = new RonaldmichaLiquidacionService(trabajadores, new RonaldmichaLiquidacionRepository(db), new RonaldmichaVacacionRepository(db),
                beneficios, parametros, periodos, empresa, auditoria, reloj);
        admin = new RonaldmichaUsuario(1L, "admin", "", RonaldmichaRol.ADMIN, id("40000001"), true);
        aprobador = new RonaldmichaUsuario(3L, "aprobador", "", RonaldmichaRol.APROBADOR, id("40000003"), true);
    }

    @Test
    void calculaConLosDatosDeDemo() {
        Calculo c = servicio.calcular(aprobador, rosa(RonaldmichaMotivoCese.DESPIDO_ARBITRARIO, null));

        assertThat(c.ultimaGratificacionCalculada()).isEqualByComparingTo("3613.00");
        assertThat(c.resultado().diasPendientes()).isEqualTo(15);
        assertThat(c.resultado().asignacionFamiliar()).isEqualByComparingTo("123.00");
        assertThat(c.resultado().neto()).isEqualByComparingTo("50137.14");
        assertThat(c.reporte().bloques()).extracting(b -> b.titulo())
                .containsExactly("Vacaciones", "Gratificación trunca", "CTS trunca", "Indemnización y otros");
        assertThat(c.reporte().netoEnLetras()).startsWith("Cincuenta mil ciento treinta y siete");

        Calculo editada = servicio.calcular(aprobador, rosa(RonaldmichaMotivoCese.RENUNCIA, new BigDecimal("3000")));
        assertThat(editada.resultado().sextoGratificacion()).isEqualByComparingTo("500.00");
        assertThat(editada.resultado().indemnizacion()).isEqualByComparingTo("0.00");
        assertThat(RonaldmichaLiquidacionService.ultimaGratificacionPagada(LocalDate.of(2026, 3, 10)).anio()).isEqualTo(2025);
    }

    @Test
    void borradorCeseRegistradoYPagado() {
        RonaldmichaLiquidacion borrador = servicio.guardar(admin, null, rosa(RonaldmichaMotivoCese.DESPIDO_ARBITRARIO, null), false);

        assertThat(borrador.estado()).isEqualTo(RonaldmichaEstadoLiquidacion.BORRADOR);
        assertThat(borrador.neto()).isEqualByComparingTo("50137.14");
        assertThat(servicio.listar(aprobador)).hasSize(1);
        assertThat(trabajadores.buscarPorId(id("40000001")).orElseThrow().fechaCese()).isNull();
        assertThatThrownBy(() -> servicio.guardar(admin, null, rosa(RonaldmichaMotivoCese.RENUNCIA, null), false))
                .isInstanceOf(RonaldmichaValidacionException.class).hasMessageContaining("ya tiene una liquidación");
        assertThatThrownBy(() -> servicio.marcarPagado(admin, borrador.id()))
                .isInstanceOf(RonaldmichaValidacionException.class).hasMessageContaining("Primero registre el cese");

        RonaldmichaLiquidacion cese = servicio.guardar(admin, borrador.id(), rosa(RonaldmichaMotivoCese.DESPIDO_ARBITRARIO, null), true);
        assertThat(cese.estado()).isEqualTo(RonaldmichaEstadoLiquidacion.CESE_REGISTRADO);
        assertThat(trabajadores.buscarPorId(id("40000001")).orElseThrow().fechaCese()).isEqualTo(CESE);
        assertThat(servicio.liquidables()).noneMatch(t -> t.numDoc().equals("40000001"));
        assertThatThrownBy(() -> servicio.eliminar(admin, cese.id())).isInstanceOf(RonaldmichaValidacionException.class);

        servicio.marcarPagado(admin, cese.id());
        assertThat(servicio.buscar(cese.id()).orElseThrow().estado()).isEqualTo(RonaldmichaEstadoLiquidacion.PAGADO);
        assertThatThrownBy(() -> servicio.guardar(admin, cese.id(), rosa(RonaldmichaMotivoCese.RENUNCIA, null), false))
                .isInstanceOf(RonaldmichaValidacionException.class).hasMessageContaining("solo lectura");
        assertThat(acciones()).contains("LIQUIDACION_GUARDADA", "CESE_REGISTRADO", "LIQUIDACION_PAGADA");
        assertThat(auditoria.recientes(10)).anyMatch(r -> r.accion().equals("CESE_REGISTRADO")
                && r.detalle().contains("T-Registro"));
    }

    @Test
    void soloElAdministradorGuardaYSoloSeEliminanBorradores() {
        Borrador carlos = new Borrador(id("40000002"), LocalDate.of(2026, 11, 15), RonaldmichaMotivoCese.VENCIMIENTO, null, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO);
        assertThatThrownBy(() -> servicio.guardar(aprobador, null, carlos, false))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);

        RonaldmichaLiquidacion l = servicio.guardar(admin, null, carlos, false);
        servicio.eliminar(admin, l.id());

        assertThat(servicio.listar(admin)).isEmpty();
        assertThat(acciones()).contains("LIQUIDACION_ELIMINADA");
    }

    @Test
    void validaciones() {
        long rosa = id("40000001");
        assertThat(servicio.validar(new Borrador(rosa, LocalDate.of(2018, 1, 1), null, null, 0, 200, null, null), null))
                .anyMatch(e -> e.contains("anterior al ingreso"))
                .anyMatch(e -> e.contains("motivo"))
                .anyMatch(e -> e.contains("entre 0 y 180"));
        assertThat(servicio.validar(new Borrador(null, CESE, RonaldmichaMotivoCese.RENUNCIA, null, 0, 0, null, null), null))
                .containsExactly("Elija al trabajador.");
        assertThat(servicio.validar(new Borrador(rosa, CESE, RonaldmichaMotivoCese.RENUNCIA, new BigDecimal("-1"), 0, 0, null, null),
                null)).anyMatch(e -> e.contains("negativos"));

        periodoRepositorio.cerrar(YearMonth.of(2026, 10), "admin", LocalDateTime.of(2026, 11, 2, 9, 0));
        assertThatThrownBy(() -> servicio.guardar(admin, null, rosa(RonaldmichaMotivoCese.RENUNCIA, null), true))
                .isInstanceOf(RonaldmichaValidacionException.class).hasMessageContaining("cerrada");
        assertThat(servicio.guardar(admin, null, rosa(RonaldmichaMotivoCese.RENUNCIA, null), false).estado())
                .isEqualTo(RonaldmichaEstadoLiquidacion.BORRADOR);
    }

    @Test
    void reportesYCertificado() {
        Borrador b = rosa(RonaldmichaMotivoCese.DESPIDO_ARBITRARIO, null);

        assertThat(new String(servicio.reportePdf(aprobador, b), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(new String(servicio.reporteWord(admin, b), 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
        assertThat(new String(servicio.certificadoTrabajo(admin, b), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(acciones()).filteredOn("LIQUIDACION_EXPORTADA"::equals).hasSize(2);
        assertThat(acciones()).contains("CERTIFICADO_TRABAJO");
    }

    private Borrador rosa(RonaldmichaMotivoCese motivo, BigDecimal ultimaGrati) {
        return new Borrador(id("40000001"), CESE, motivo, ultimaGrati, 0, 0, new BigDecimal("100"), new BigDecimal("50"));
    }

    private long id(String dni) {
        return trabajadores.listarTodos().stream().filter(t -> t.numDoc().equals(dni)).findFirst().orElseThrow().id();
    }

    private List<String> acciones() {
        return auditoria.recientes(50).stream().map(r -> r.accion()).toList();
    }
}
