package com.planillaperu.service;

import com.planillaperu.common.RonaldmichaDinero;
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
import com.planillaperu.service.RonaldmichaResumenAnualService.Concepto;
import com.planillaperu.service.RonaldmichaResumenAnualService.FilaAnual;
import com.planillaperu.service.RonaldmichaResumenAnualService.Modo;
import com.planillaperu.service.RonaldmichaResumenAnualService.Resumen;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaResumenAnualServiceTest {

    private static final YearMonth JULIO = YearMonth.of(2026, 7);
    private static final YearMonth MAYO = YearMonth.of(2026, 5);

    @TempDir
    Path carpeta;

    private RonaldmichaResumenAnualService servicio;
    private RonaldmichaPlanillaService planilla;
    private RonaldmichaBeneficiosService beneficios;
    private RonaldmichaAuditoriaService auditoria;
    private final RonaldmichaUsuario aprobador = new RonaldmichaUsuario(3L, "aprobador", "", RonaldmichaRol.APROBADOR, null, true);

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
        planilla = new RonaldmichaPlanillaService(trabajadores, asistenciaRepositorio, new RonaldmichaPlanillaRepository(db),
                periodoRepositorio, periodos, parametros, asistencia, auditoria, reloj);
        RonaldmichaEmpresaService empresa = new RonaldmichaEmpresaService(new RonaldmichaEmpresaRepository(db), auditoria);
        beneficios = new RonaldmichaBeneficiosService(trabajadores, new RonaldmichaBeneficioRepository(db), planilla, parametros, empresa,
                auditoria);
        servicio = new RonaldmichaResumenAnualService(planilla, beneficios, auditoria, reloj);
    }

    @Test
    void trabajadoresPorMesConTotalesYMesesFuturos() {
        Resumen r = servicio.consultar(aprobador, 2026);

        assertThat(r.meses()).hasSize(12);
        assertThat(r.filas()).hasSize(7);
        assertThat(r.futuro(YearMonth.of(2026, 11))).isTrue();
        assertThat(r.futuro(YearMonth.of(2026, 10))).isFalse();
        assertThat(r.totalMes(YearMonth.of(2026, 12), Modo.NETO)).isNull();
        assertThat(r.filas()).allMatch(f -> f.valor(YearMonth.of(2026, 12), Modo.COSTO) == null);
        assertThat(Resumen.mesDeGratificacion(JULIO)).isTrue();
        assertThat(Resumen.mesDeGratificacion(MAYO)).isFalse();

        for (Modo modo : Modo.values()) {
            BigDecimal porColumnas = r.meses().stream().map(m -> r.totalMes(m, modo)).filter(Objects::nonNull)
                    .reduce(RonaldmichaDinero.CERO, BigDecimal::add);
            assertThat(porColumnas).isEqualByComparingTo(r.total(modo));
        }

        FilaAnual primera = r.filas().getFirst();
        long id = primera.trabajador().id();
        BigDecimal grati = beneficios.calcular(new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, 2026, 1)).filas()
                .stream().filter(f -> f.trabajador().id() == id).findFirst().orElseThrow().total();
        BigDecimal netoPlanilla = planilla.delMes(JULIO).filas().stream().filter(f -> f.trabajador().id() == id)
                .findFirst().orElseThrow().resultado().netoAPagar();
        assertThat(grati).isPositive();
        assertThat(primera.meses().get(JULIO).gratificacion()).isEqualByComparingTo(grati);
        assertThat(primera.valor(JULIO, Modo.NETO)).isEqualByComparingTo(netoPlanilla.add(grati));
        assertThat(primera.valor(JULIO, Modo.COSTO))
                .isEqualByComparingTo(planilla.resultadosDelMes(JULIO).get(id).costoLaboral());

        assertThatThrownBy(() -> servicio.consultar(new RonaldmichaUsuario(5L, "40000002", "", RonaldmichaRol.TRABAJADOR, 2L, true), 2026))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    @Test
    void resumenPorConcepto() {
        Resumen r = servicio.consultar(aprobador, 2026);

        assertThat(r.conceptos()).extracting(Concepto::nombre).containsExactly("Remuneraciones", "Gratificaciones",
                "CTS", "EsSalud", "ONP", "AFP", "Renta de 5.ª");
        Concepto grati = concepto(r, "Gratificaciones");
        assertThat(grati.porMes().get(JULIO)).isPositive();
        assertThat(grati.porMes().get(MAYO)).isZero();
        assertThat(grati.porMes()).doesNotContainKey(YearMonth.of(2026, 12));
        assertThat(concepto(r, "CTS").porMes().get(MAYO)).isPositive();
        assertThat(concepto(r, "CTS").porMes()).doesNotContainKey(YearMonth.of(2026, 11));
        assertThat(concepto(r, "ONP").total()).isPositive();
        assertThat(concepto(r, "AFP").total()).isPositive();
        assertThat(concepto(r, "Remuneraciones").total()).isGreaterThan(concepto(r, "EsSalud").total());
        BigDecimal deFilas = r.filas().stream().flatMap(f -> f.meses().values().stream())
                .map(RonaldmichaResumenAnualService.Celda::gratificacion).reduce(RonaldmichaDinero.CERO, BigDecimal::add);
        assertThat(grati.total()).isEqualByComparingTo(deFilas);
    }

    @Test
    void excelConUnaHojaPorMesMasElResumen() throws IOException {
        byte[] excel = servicio.exportarExcel(aprobador, 2026);
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(13);
            assertThat(libro.getSheetName(0)).isEqualTo("Resumen");
            assertThat(libro.getSheetAt(7).getRow(0).getCell(0).getStringCellValue()).contains("julio 2026");
            assertThat(libro.getSheetAt(12).getRow(0).getCell(0).getStringCellValue()).contains("mes futuro");
            assertThat(libro.getSheetAt(7).getPhysicalNumberOfRows()).isGreaterThan(7);
        }
        assertThat(auditoria.recientes(1).getFirst().accion()).isEqualTo("RESUMEN_ANUAL_EXPORTADO");
    }

    private static Concepto concepto(Resumen r, String nombre) {
        return r.conceptos().stream().filter(c -> c.nombre().equals(nombre)).findFirst().orElseThrow();
    }
}
