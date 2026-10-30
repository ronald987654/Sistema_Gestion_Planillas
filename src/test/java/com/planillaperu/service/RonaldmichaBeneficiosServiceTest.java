package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaSemestreBeneficio;
import com.planillaperu.domain.RonaldmichaTipoBeneficio;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaBeneficioRepository;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaEmpresaRepository;
import com.planillaperu.repository.RonaldmichaParametrosRepository;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;
import com.planillaperu.repository.RonaldmichaPlanillaRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.service.RonaldmichaBeneficiosService.Ajuste;
import com.planillaperu.service.RonaldmichaBeneficiosService.Beneficio;
import com.planillaperu.service.RonaldmichaBeneficiosService.FilaBeneficio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaBeneficiosServiceTest {

    private static final RonaldmichaSemestreBeneficio GRATI_JULIO = new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, 2026, 1);
    private static final RonaldmichaSemestreBeneficio CTS_NOVIEMBRE = new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.CTS, 2026, 2);

    @TempDir
    Path carpeta;

    private RonaldmichaAuditoriaService auditoria;
    private RonaldmichaBeneficiosService servicio;
    private RonaldmichaUsuario admin;
    private RonaldmichaUsuario aprobador;

    @BeforeEach
    void preparar() {
        RonaldmichaDatabase db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
        auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        RonaldmichaTrabajadorRepository trabajadores = new RonaldmichaTrabajadorRepository(db);
        RonaldmichaPeriodoRepository periodoRepositorio = new RonaldmichaPeriodoRepository(db);
        RonaldmichaParametrosService parametros = new RonaldmichaParametrosService(new RonaldmichaParametrosRepository(db), periodoRepositorio,
                trabajadores, auditoria, reloj);
        RonaldmichaPeriodoService periodos = new RonaldmichaPeriodoService(periodoRepositorio, reloj);
        RonaldmichaAsistenciaRepository asistenciaRepositorio = new RonaldmichaAsistenciaRepository(db);
        RonaldmichaAsistenciaService asistencia = new RonaldmichaAsistenciaService(asistenciaRepositorio, trabajadores, periodos, parametros,
                auditoria, reloj);
        RonaldmichaPlanillaService planilla = new RonaldmichaPlanillaService(trabajadores, asistenciaRepositorio, new RonaldmichaPlanillaRepository(db),
                periodoRepositorio, periodos, parametros, asistencia, auditoria, reloj);
        servicio = new RonaldmichaBeneficiosService(trabajadores, new RonaldmichaBeneficioRepository(db), planilla, parametros,
                new RonaldmichaEmpresaService(new RonaldmichaEmpresaRepository(db), auditoria), auditoria);
        admin = new RonaldmichaUsuario(1L, "admin", "", RonaldmichaRol.ADMIN, null, true);
        aprobador = new RonaldmichaUsuario(3L, "aprobador", "", RonaldmichaRol.APROBADOR, null, true);
    }

    @Test
    void semestres() {
        assertThat(GRATI_JULIO.meses()).first().isEqualTo(YearMonth.of(2026, 1));
        assertThat(GRATI_JULIO.fechaPago()).isEqualTo(LocalDate.of(2026, 7, 15));
        RonaldmichaSemestreBeneficio ctsMayo = new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.CTS, 2026, 1);
        assertThat(ctsMayo.desde()).isEqualTo(YearMonth.of(2025, 11));
        assertThat(ctsMayo.hasta()).isEqualTo(YearMonth.of(2026, 4));
        assertThat(ctsMayo.gratificacionDelSexto()).isEqualTo(new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, 2025, 2));
        assertThat(CTS_NOVIEMBRE.gratificacionDelSexto()).isEqualTo(GRATI_JULIO);
        assertThat(RonaldmichaSemestreBeneficio.vigenteEn(RonaldmichaTipoBeneficio.CTS, YearMonth.of(2026, 12)))
                .isEqualTo(new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.CTS, 2027, 1));
        assertThat(RonaldmichaBeneficiosService.periodoTexto(ctsMayo)).isEqualTo("noviembre 2025–abril 2026");
    }

    @Test
    void gratificacionDeJulio() {
        Beneficio b = servicio.consultar(aprobador, GRATI_JULIO);

        assertThat(b.error()).isEmpty();
        assertThat(b.filas()).hasSize(7);
        FilaBeneficio rosa = fila(b, "40000001");
        assertThat(rosa.meses()).isEqualTo(6);
        assertThat(rosa.asignacionFamiliar()).isEqualByComparingTo("113.00");
        assertThat(rosa.gratificacion()).isEqualByComparingTo("3613.00");
        assertThat(rosa.bonificacion()).isEqualByComparingTo("325.17");
        assertThat(rosa.total()).isEqualByComparingTo("3938.17");
        assertThat(rosa.variables()).hasSize(6);
        assertThat(fila(b, "40000004").gratificacion()).isEqualByComparingTo("956.50");
        assertThat(fila(b, "40000006").total()).isEqualByComparingTo("0.00");
        assertThat(fila(b, "40000003").bonificacion()).isEqualByComparingTo("358.63");
        assertThatThrownBy(() -> servicio.consultar(new RonaldmichaUsuario(5L, "40000002", "", RonaldmichaRol.TRABAJADOR, 2L, true),
                GRATI_JULIO)).isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    @Test
    void ctsDeNoviembreUsaElSextoDeJulio() {
        FilaBeneficio rosa = fila(servicio.consultar(admin, CTS_NOVIEMBRE), "40000001");

        assertThat(rosa.meses()).isEqualTo(6);
        assertThat(rosa.dias()).isZero();
        assertThat(rosa.asignacionFamiliar()).isEqualByComparingTo("123.00");
        assertThat(rosa.gratificacion()).isEqualByComparingTo("3613.00");
        assertThat(rosa.sextoGratificacion()).isEqualByComparingTo("602.17");
        assertThat(rosa.computable()).isEqualByComparingTo("4225.17");
        assertThat(rosa.importe()).isEqualByComparingTo("2112.59");
    }

    @Test
    void ajustesManualesYRestablecer() {
        long rosa = fila(servicio.consultar(admin, GRATI_JULIO), "40000001").trabajador().id();
        assertThatThrownBy(() -> servicio.guardarAjustes(aprobador, GRATI_JULIO,
                Map.of(rosa, new Ajuste(new BigDecimal("4000"), 3, null))))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.guardarAjustes(admin, GRATI_JULIO,
                Map.of(rosa, new Ajuste(null, -1, null)))).isInstanceOf(RonaldmichaValidacionException.class);

        servicio.guardarAjustes(admin, GRATI_JULIO, Map.of(rosa, new Ajuste(new BigDecimal("4000"), 3, null)));

        Beneficio b = servicio.consultar(admin, GRATI_JULIO);
        FilaBeneficio ajustada = fila(b, "40000001");
        assertThat(b.ajustados()).isEqualTo(1);
        assertThat(ajustada.basicoAjustado()).isTrue();
        assertThat(ajustada.faltasAjustadas()).isTrue();
        assertThat(ajustada.basicoAuto()).isEqualByComparingTo("3500.00");
        assertThat(ajustada.gratificacion()).isEqualByComparingTo("4044.45");
        assertThat(auditoria.recientes(5)).anyMatch(r -> r.accion().equals("AJUSTE_GRATIFICACION")
                && r.detalle().contains("básico S/ 3,500.00 → S/ 4,000.00") && r.detalle().contains("faltas 0 → 3"));

        assertThat(fila(servicio.consultar(admin, CTS_NOVIEMBRE), "40000001").gratificacion())
                .isEqualByComparingTo("4044.45");
        servicio.guardarAjustes(admin, CTS_NOVIEMBRE, Map.of(rosa, new Ajuste(null, null, new BigDecimal("3000"))));
        FilaBeneficio cts = fila(servicio.consultar(admin, CTS_NOVIEMBRE), "40000001");
        assertThat(cts.gratificacionAjustada()).isTrue();
        assertThat(cts.sextoGratificacion()).isEqualByComparingTo("500.00");
        assertThat(cts.importe()).isEqualByComparingTo("2061.50");

        assertThat(servicio.restablecer(admin, GRATI_JULIO)).isEqualTo(1);
        assertThat(servicio.consultar(admin, GRATI_JULIO).ajustados()).isZero();
        assertThat(acciones()).contains("AJUSTE_CTS", "AJUSTES_BENEFICIO_RESTABLECIDOS");

        servicio.guardarAjustes(admin, CTS_NOVIEMBRE, Map.of(rosa, new Ajuste(null, null, new BigDecimal("3613.00"))));
        assertThat(servicio.consultar(admin, CTS_NOVIEMBRE).ajustados()).isZero();
    }

    @Test
    void exportaLosTresReportes() {
        long rosa = fila(servicio.consultar(admin, CTS_NOVIEMBRE), "40000001").trabajador().id();

        assertThat(servicio.planillaExcel(admin, GRATI_JULIO, null)).isNotEmpty();
        assertThat(new String(servicio.boletasPdf(aprobador, CTS_NOVIEMBRE, rosa), 0, 4, StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");
        assertThat(servicio.boletasWord(admin, GRATI_JULIO, null)).isNotEmpty();
        assertThat(servicio.pagosExcel(admin, CTS_NOVIEMBRE, null)).isNotEmpty();
        String txt = new String(servicio.pagosTxt(admin, CTS_NOVIEMBRE, null), StandardCharsets.UTF_8);
        assertThat(txt).startsWith("TIPO_DOC|DOCUMENTO|APELLIDOS Y NOMBRES|BANCO|CUENTA|IMPORTE")
                .contains("DNI|40000001|Quispe Mamani, Rosa Elena|Caja Ejemplo|100-0000001|2112.59")
                .doesNotContain("40000006");
        assertThat(acciones()).filteredOn("BENEFICIO_EXPORTADO"::equals).hasSize(5);
    }

    private static FilaBeneficio fila(Beneficio b, String dni) {
        return b.filas().stream().filter(f -> f.trabajador().numDoc().equals(dni)).findFirst().orElseThrow();
    }

    private List<String> acciones() {
        return auditoria.recientes(50).stream().map(r -> r.accion()).toList();
    }
}
