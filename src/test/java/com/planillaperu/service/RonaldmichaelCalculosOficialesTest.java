package com.planillaperu.service;

import com.planillaperu.calc.RonaldmichaelCtsCalculator;
import com.planillaperu.calc.RonaldmichaelGratificacionCalculator;
import com.planillaperu.calc.RonaldmichaelHorasExtraCalculator;
import com.planillaperu.calc.RonaldmichaelLiquidacionCalculator;
import com.planillaperu.calc.RonaldmichaelPlanillaCalculator;
import com.planillaperu.calc.RonaldmichaelPlanillaCalculator.Asistencia;
import com.planillaperu.calc.RonaldmichaelPlanillaCalculator.HistorialRenta;
import com.planillaperu.calc.RonaldmichaelPlanillaCalculator.Novedades;
import com.planillaperu.calc.RonaldmichaelRentaQuintaCalculator;
import com.planillaperu.calc.RonaldmichaelResultadoPlanilla;
import com.planillaperu.calc.RonaldmichaelResultadoPlanilla.Concepto;
import com.planillaperu.calc.RonaldmichaelResultadoPlanilla.TipoConcepto;
import com.planillaperu.calc.RonaldmichaelVacacionesCalculator;
import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.common.RonaldmichaNumeroALetras;
import com.planillaperu.domain.RonaldmichaAfpComision;
import com.planillaperu.domain.RonaldmichaEstadoParametros;
import com.planillaperu.domain.RonaldmichaHorario;
import com.planillaperu.domain.RonaldmichaMotivoCese;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaSalud;
import com.planillaperu.domain.RonaldmichaSemestreBeneficio;
import com.planillaperu.domain.RonaldmichaSistemaPension;
import com.planillaperu.domain.RonaldmichaTipoBeneficio;
import com.planillaperu.domain.RonaldmichaTipoComision;
import com.planillaperu.domain.RonaldmichaTipoDocumento;
import com.planillaperu.domain.RonaldmichaTipoPago;
import com.planillaperu.domain.RonaldmichaTratamientoHe;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.export.doc.RonaldmichaDocumentModel;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.KeyValue;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Par;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Table;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Total;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository.HoraExtraManual;
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
import com.planillaperu.service.RonaldmichaelBeneficiosService.FilaBeneficio;
import com.planillaperu.service.RonaldmichaelPlanillaService.Borrador;
import com.planillaperu.service.RonaldmichaelPlanillaService.FilaPlanilla;
import com.planillaperu.service.RonaldmichaelPlanillaService.PlanillaMes;
import com.planillaperu.service.RonaldmichaelPlanillaService.Totales;
import com.planillaperu.service.RonaldmichaelReporte.Formato;
import com.planillaperu.service.RonaldmichaelReportesService.Parametros;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaelCalculosOficialesTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final LocalDate ANTIGUO = LocalDate.of(2020, 1, 1);
    private static final BigDecimal OCHO = new BigDecimal("8");
    private static final Asistencia OCHO_HORAS = Asistencia.sinNovedades(OCHO);
    private static final BigDecimal UIT = new BigDecimal("5500.00");

    static final RonaldmichaParametrosVersion P = new RonaldmichaParametrosVersion(1L, OCTUBRE, "Parámetros de prueba",
            new BigDecimal("1230.00"), UIT, new BigDecimal("10"), new BigDecimal("9"), new BigDecimal("6.75"),
            new BigDecimal("13"), new BigDecimal("10"), new BigDecimal("1.37"), new BigDecimal("12732.70"),
            new BigDecimal("9"), new BigDecimal("6.75"), new BigDecimal("1.53"), new BigDecimal("15.00"),
            RonaldmichaEstadoParametros.VIGENTE, "prueba", null,
            List.of(new RonaldmichaAfpComision("AFP Habitat", new BigDecimal("1.47"), new BigDecimal("1.25")),
                    new RonaldmichaAfpComision("AFP Integra", new BigDecimal("1.55"), new BigDecimal("0.78")),
                    new RonaldmichaAfpComision("AFP Prima", new BigDecimal("1.60"), new BigDecimal("1.25")),
                    new RonaldmichaAfpComision("AFP Profuturo", new BigDecimal("1.69"), new BigDecimal("0.68"))),
            null, null, null);

    @Nested
    class PlanillaMensual {

        @Test
        void c01OnpConAsignacionFamiliar() {
            RonaldmichaelResultadoPlanilla r = calcular(trabajador("1230.00", RonaldmichaSistemaPension.ONP, null, true), Novedades.NINGUNA);

            assertThat(r.remuneracionAfecta()).isEqualByComparingTo("1353.00");
            assertThat(r.pension().aporte()).isEqualByComparingTo("175.89");
            assertThat(r.rentaQuinta()).isEqualByComparingTo("0.00");
            assertThat(r.netoAPagar()).isEqualByComparingTo("1177.11");
            assertThat(r.essalud()).isEqualByComparingTo("121.77");
        }

        @Test
        void c02AfpIntegraFlujo() {
            RonaldmichaelResultadoPlanilla r = calcular(trabajador("1230.00", RonaldmichaSistemaPension.INTEGRA, RonaldmichaTipoComision.FLUJO, true),
                    Novedades.NINGUNA);

            assertThat(r.pension().aporte()).isEqualByComparingTo("135.30");
            assertThat(r.pension().prima()).isEqualByComparingTo("18.54");
            assertThat(r.pension().comision()).isEqualByComparingTo("20.97");
            assertThat(r.pension().total()).isEqualByComparingTo("174.81");
            assertThat(r.netoAPagar()).isEqualByComparingTo("1178.19");
            assertThat(r.essalud()).isEqualByComparingTo("121.77");
        }

        @Test
        void c03AfpProfuturoMixtaPequenaEmpresa() {
            RonaldmichaTrabajador t = con(trabajador("2200.00", RonaldmichaSistemaPension.PROFUTURO, RonaldmichaTipoComision.MIXTA, false),
                    RonaldmichaRegimenLaboral.PEQUENA, RonaldmichaSalud.ESSALUD);

            RonaldmichaelResultadoPlanilla r = calcular(t, Novedades.NINGUNA);

            assertThat(r.pension().aporte()).isEqualByComparingTo("220.00");
            assertThat(r.pension().prima()).isEqualByComparingTo("30.14");
            assertThat(r.pension().comision()).isEqualByComparingTo("0.00");
            assertThat(r.netoAPagar()).isEqualByComparingTo("1949.86");
            assertThat(r.essalud()).isEqualByComparingTo("198.00");
        }

        @Test
        void c04TopeDeLaPrima() {
            RonaldmichaelResultadoPlanilla r = calcular(trabajador("15000.00", RonaldmichaSistemaPension.INTEGRA, RonaldmichaTipoComision.FLUJO, false),
                    Novedades.NINGUNA);

            assertThat(r.pension().basePrima()).isEqualByComparingTo("12732.70");
            assertThat(r.pension().prima()).isEqualByComparingTo("174.44").isNotEqualByComparingTo("205.50");
            assertThat(r.pension().primaTopada()).isTrue();
        }

        @Test
        void c05BaseMinimaDeEsSaludEnTiempoParcial() {
            RonaldmichaTrabajador t = trabajador("600.00", RonaldmichaSistemaPension.ONP, null, false);
            t = new RonaldmichaTrabajador(t.id(), t.codigo(), t.tipoDoc(), t.numDoc(), t.apPaterno(), t.apMaterno(), t.nombres(),
                    null, null, t.fechaIngreso(), null, t.area(), t.cargo(), "Tiempo parcial", t.regimen(), null, null,
                    t.sueldo(), BigDecimal.ZERO, t.salud(), t.sistemaPension(), null, null, false, false,
                    RonaldmichaTipoPago.DEPOSITO, null, null, null, null);

            RonaldmichaelResultadoPlanilla r = RonaldmichaelPlanillaCalculator.calcular(t, OCTUBRE, P, Novedades.NINGUNA,
                    Asistencia.sinNovedades(new BigDecimal("3")), List.of());

            assertThat(r.essalud()).isEqualByComparingTo("110.70");
        }

        @Test
        void c06ValorHoraHorasExtraTardanzaYFeriado() {
            assertThat(RonaldmichaelHorasExtraCalculator.valorHora(new BigDecimal("1353.00"), OCHO)).isEqualByComparingTo("5.6375");

            Asistencia a = new Asistencia(OCHO, 25, 120, 60, 1);
            RonaldmichaelResultadoPlanilla r = RonaldmichaelPlanillaCalculator.calcular(trabajador("1230.00", RonaldmichaSistemaPension.ONP, null, true),
                    OCTUBRE, P, Novedades.NINGUNA, a, List.of());

            assertThat(r.valorHora()).isEqualByComparingTo("5.6375");
            assertThat(r.horasExtra25()).isEqualByComparingTo("14.09");
            assertThat(r.horasExtra35()).isEqualByComparingTo("7.61");
            assertThat(r.tardanza()).isEqualByComparingTo("2.35");
            assertThat(r.feriadoTrabajado()).isEqualByComparingTo("90.20");
            assertThat(r.remuneracionAfecta()).isEqualByComparingTo("1462.55");
            assertThat(r.conceptos()).extracting(Concepto::codigoPlame, Concepto::tipo)
                    .contains(org.assertj.core.groups.Tuple.tuple("0704", TipoConcepto.REBAJA));
        }

        @Test
        void c07HorasExtraSeCortanPorDia() {
            LocalDate d1 = LocalDate.of(2026, 10, 5);
            LocalDate d2 = LocalDate.of(2026, 10, 6);

            RonaldmichaelHorasExtraCalculator.Tramos unDia = RonaldmichaelPlanillaService.tramosPagados(
                    List.of(he(d1, "3")), OCTUBRE);
            RonaldmichaelHorasExtraCalculator.Tramos dosDias = RonaldmichaelPlanillaService.tramosPagados(
                    List.of(he(d1, "2"), he(d2, "2")), OCTUBRE);

            assertThat(unDia.al25()).isEqualTo(120);
            assertThat(unDia.al35()).isEqualTo(60);
            assertThat(dosDias.al25()).isEqualTo(240);
            assertThat(dosDias.al35()).isZero();
            assertThat(RonaldmichaelHorasExtraCalculator.dividir(180)).isEqualTo(new RonaldmichaelHorasExtraCalculator.Tramos(120, 60));
        }

        @Test
        void c08Faltas() {
            RonaldmichaelResultadoPlanilla r = calcular(trabajador("1230.00", RonaldmichaSistemaPension.ONP, null, false), novedades(2, 0, 0));

            assertThat(r.basico()).isEqualByComparingTo("1148.00");
        }

        @Test
        void c09IngresoElDia16() {
            RonaldmichaTrabajador t = trabajador("1230.00", RonaldmichaSistemaPension.ONP, null, false, LocalDate.of(2026, 10, 16), null);

            RonaldmichaelResultadoPlanilla r = calcular(t, Novedades.NINGUNA);

            assertThat(r.diasMes()).isEqualTo(15);
            assertThat(r.basico()).isEqualByComparingTo("615.00");
        }

        @Test
        void c10Eps() {
            RonaldmichaTrabajador t = con(trabajador("3000.00", RonaldmichaSistemaPension.ONP, null, false), RonaldmichaRegimenLaboral.GENERAL, RonaldmichaSalud.EPS);

            assertThat(calcular(t, Novedades.NINGUNA).essalud()).isEqualByComparingTo("202.50");
        }

        @Test
        void c11MicroempresaConSis() {
            RonaldmichaTrabajador t = con(trabajador("1230.00", RonaldmichaSistemaPension.ONP, null, false), RonaldmichaRegimenLaboral.MICRO, RonaldmichaSalud.SIS);

            RonaldmichaelResultadoPlanilla r = calcular(t, Novedades.NINGUNA);

            assertThat(r.sis()).isEqualByComparingTo("15.00");
            assertThat(r.essalud()).isEqualByComparingTo("0.00");
            assertThat(r.provisionGratificacion()).isEqualByComparingTo("0.00");
            assertThat(r.provisionCts()).isEqualByComparingTo("0.00");
            assertThat(RonaldmichaRegimenLaboral.MICRO.diasVacacionesAnuales()).isEqualTo(15);
            assertThat(RonaldmichaelGratificacionCalculator.calcular(new BigDecimal("1230.00"), RonaldmichaDinero.CERO, seisCeros(), 6, 0,
                    RonaldmichaRegimenLaboral.MICRO, new BigDecimal("9")).total()).isEqualByComparingTo("0.00");
            assertThat(RonaldmichaelCtsCalculator.calcular(new BigDecimal("1230.00"), RonaldmichaDinero.CERO, seisCeros(),
                    new RonaldmichaelCtsCalculator.Tiempo(6, 0), 0, RonaldmichaDinero.CERO, RonaldmichaRegimenLaboral.MICRO).cts())
                    .isEqualByComparingTo("0.00");
        }

        @Test
        void c12SubsidioDeCincoDias() {
            RonaldmichaelResultadoPlanilla r = calcular(trabajador("1230.00", RonaldmichaSistemaPension.ONP, null, false), novedades(0, 0, 5));

            assertThat(r.basico()).isEqualByComparingTo("1025.00");
            assertThat(r.subsidio()).isEqualByComparingTo("205.00");
            assertThat(r.essalud()).isEqualByComparingTo("92.25");
            assertThat(r.pension().remuneracion()).isEqualByComparingTo("1230.00");
            assertThat(r.pension().aporte()).isEqualByComparingTo("159.90");
        }

        @Test
        void c13MovilidadSupeditadaNoAfecta() {
            RonaldmichaTrabajador sin = trabajador("3000.00", RonaldmichaSistemaPension.ONP, null, false);
            RonaldmichaTrabajador con = new RonaldmichaTrabajador(sin.id(), sin.codigo(), sin.tipoDoc(), sin.numDoc(), sin.apPaterno(),
                    sin.apMaterno(), sin.nombres(), null, null, sin.fechaIngreso(), null, sin.area(), sin.cargo(),
                    sin.tipoContrato(), sin.regimen(), null, null, sin.sueldo(), new BigDecimal("300.00"), sin.salud(),
                    sin.sistemaPension(), null, null, false, false, RonaldmichaTipoPago.DEPOSITO, null, null, null, null);

            RonaldmichaelResultadoPlanilla a = calcular(sin, Novedades.NINGUNA);
            RonaldmichaelResultadoPlanilla b = calcular(con, Novedades.NINGUNA);

            assertThat(b.movilidad()).isEqualByComparingTo("300.00");
            assertThat(b.remuneracionAfecta()).isEqualByComparingTo(a.remuneracionAfecta());
            assertThat(b.pension().total()).isEqualByComparingTo(a.pension().total());
            assertThat(b.essalud()).isEqualByComparingTo(a.essalud());
            assertThat(b.baseRenta()).isEqualByComparingTo(a.baseRenta());
            assertThat(b.renta().rentaAnualProyectada()).isEqualByComparingTo(a.renta().rentaAnualProyectada());
            assertThat(b.netoAPagar()).isEqualByComparingTo(a.netoAPagar().add(new BigDecimal("300.00")));
        }
    }

    @Nested
    class RentaQuinta {

        @Test
        void c14RetencionDeEnero() {
            RonaldmichaelRentaQuintaCalculator.Renta r = RonaldmichaelRentaQuintaCalculator.calcular(new RonaldmichaelRentaQuintaCalculator.Entrada(1,
                    new BigDecimal("7800.00"), RonaldmichaDinero.CERO, new BigDecimal("17004.00"), RonaldmichaDinero.CERO, List.of(),
                    RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, UIT));

            assertThat(r.rentaAnualProyectada()).isEqualByComparingTo("110604.00");
            assertThat(r.deduccion()).isEqualByComparingTo("38500.00");
            assertThat(r.rentaNeta()).isEqualByComparingTo("72104.00");
            assertThat(r.impuestoAnual()).isEqualByComparingTo("8444.56");
            assertThat(r.divisor()).isEqualTo(12);
            assertThat(r.retencionMensual()).isEqualByComparingTo("703.71");

            RonaldmichaelResultadoPlanilla enero = RonaldmichaelPlanillaCalculator.calcular(
                    trabajador("7800.00", RonaldmichaSistemaPension.INTEGRA, RonaldmichaTipoComision.FLUJO, false), YearMonth.of(2026, 1),
                    P, Novedades.NINGUNA, OCHO_HORAS, List.of());
            assertThat(enero.rentaAnualProyectada()).isEqualByComparingTo("110604.00");
            assertThat(enero.rentaQuinta()).isEqualByComparingTo("703.71");
        }

        @Test
        void c15LasDoceRetencionesSumanElImpuestoDelAnio() {
            List<BigDecimal> retenciones = anio(trabajador("7800.00", RonaldmichaSistemaPension.INTEGRA, RonaldmichaTipoComision.FLUJO,
                    false), Map.of());

            assertThat(retenciones).extracting(BigDecimal::toPlainString).containsExactly(
                    "703.71", "703.71", "703.71",
                    "703.71",
                    "703.72", "703.72", "703.72",
                    "703.71",
                    "703.71", "703.71", "703.71",
                    "703.72");
            assertThat(RonaldmichaDinero.sumar(retenciones.toArray(BigDecimal[]::new))).isEqualByComparingTo("8444.56");
        }

        @Test
        void c16SinRentaSiNoSuperaSieteUit() {
            List<BigDecimal> retenciones = anio(trabajador("1230.00", RonaldmichaSistemaPension.ONP, null, false), Map.of());

            assertThat(retenciones).hasSize(12).allSatisfy(r -> assertThat(r).isEqualByComparingTo("0.00"));
        }

        @Test
        void pagoExtraordinarioSumaSuImpuestoAdicionalEnElMes() {
            List<BigDecimal> retenciones = anio(trabajador("7800.00", RonaldmichaSistemaPension.INTEGRA, RonaldmichaTipoComision.FLUJO,
                    false), Map.of(3, new BigDecimal("10000.00")));

            assertThat(retenciones.get(2)).isEqualByComparingTo("2103.71");
            assertThat(RonaldmichaDinero.sumar(retenciones.toArray(BigDecimal[]::new))).isEqualByComparingTo("9844.56");
        }

        @Test
        void laDeduccionAdicionalDeTresUitNoLaAplicaElEmpleador() {
            RonaldmichaelRentaQuintaCalculator.Renta r = RonaldmichaelRentaQuintaCalculator.calcular(new RonaldmichaelRentaQuintaCalculator.Entrada(12,
                    RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, new BigDecimal("110604.00"), List.of(), RonaldmichaDinero.CERO,
                    RonaldmichaDinero.CERO, UIT));

            assertThat(r.rentaNeta()).isEqualByComparingTo("72104.00");
        }

        @Test
        void rentasDeOtrosEmpleadoresDeclaradas() {
            RonaldmichaelRentaQuintaCalculator.Renta r = RonaldmichaelRentaQuintaCalculator.calcular(new RonaldmichaelRentaQuintaCalculator.Entrada(1,
                    new BigDecimal("7800.00"), RonaldmichaDinero.CERO, new BigDecimal("17004.00"), RonaldmichaDinero.CERO, List.of(),
                    new BigDecimal("10000.00"), RonaldmichaDinero.CERO, UIT));

            assertThat(r.rentaAnualProyectada()).isEqualByComparingTo("120604.00");
            assertThat(r.impuestoAnual()).isEqualByComparingTo("9844.56");
        }

        private List<BigDecimal> anio(RonaldmichaTrabajador t, Map<Integer, BigDecimal> extraordinarios) {
            List<BigDecimal> retenciones = new ArrayList<>();
            BigDecimal percibido = RonaldmichaDinero.CERO;
            for (int m = 1; m <= 12; m++) {
                YearMonth mes = YearMonth.of(2026, m);
                BigDecimal bono = extraordinarios.getOrDefault(m, RonaldmichaDinero.CERO);
                Novedades n = new Novedades(0, 0, 0, RonaldmichaDinero.CERO, bono, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO);
                BigDecimal grati = RonaldmichaDinero.redondear(t.sueldo().multiply(new BigDecimal("1.09")));
                BigDecimal porPercibir = RonaldmichaDinero.redondear(grati.multiply(BigDecimal.valueOf(m <= 7 ? 2 : 1)));
                RonaldmichaelResultadoPlanilla r = RonaldmichaelPlanillaCalculator.calcular(t, mes, P, n, OCHO_HORAS, List.of(),
                        new HistorialRenta(percibido, retenciones, porPercibir, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, null));
                retenciones.add(r.rentaQuinta());
                percibido = RonaldmichaDinero.sumar(percibido, r.baseRenta(), m == 7 || m == 12 ? grati : RonaldmichaDinero.CERO);
            }
            return retenciones;
        }
    }

    @Nested
    class Gratificacion {

        private final BigDecimal nueve = new BigDecimal("9");

        @Test
        void c18RegimenGeneral() {
            RonaldmichaelGratificacionCalculator.Resultado r = RonaldmichaelGratificacionCalculator.calcular(new BigDecimal("1230.00"),
                    new BigDecimal("123.00"), seisCeros(), 6, 0, RonaldmichaRegimenLaboral.GENERAL, nueve);

            assertThat(r.gratificacion()).isEqualByComparingTo("1353.00");
            assertThat(r.bonificacion()).isEqualByComparingTo("121.77");
            assertThat(r.total()).isEqualByComparingTo("1474.77");
        }

        @Test
        void c19PequenaEmpresa() {
            RonaldmichaelGratificacionCalculator.Resultado r = RonaldmichaelGratificacionCalculator.calcular(new BigDecimal("2200.00"),
                    RonaldmichaDinero.CERO, seisCeros(), 6, 0, RonaldmichaRegimenLaboral.PEQUENA, nueve);

            assertThat(r.gratificacion()).isEqualByComparingTo("1100.00");
            assertThat(r.bonificacion()).isEqualByComparingTo("99.00");
            assertThat(r.total()).isEqualByComparingTo("1199.00");
        }

        @Test
        void c20ConEps() {
            RonaldmichaelGratificacionCalculator.Resultado r = RonaldmichaelGratificacionCalculator.calcular(new BigDecimal("3000.00"),
                    RonaldmichaDinero.CERO, seisCeros(), 6, 0, RonaldmichaRegimenLaboral.GENERAL, new BigDecimal("6.75"));

            assertThat(r.bonificacion()).isEqualByComparingTo("202.50");
        }

        @Test
        void c21DosFaltasEnElSemestre() {
            RonaldmichaelGratificacionCalculator.Resultado r = RonaldmichaelGratificacionCalculator.calcular(new BigDecimal("1230.00"),
                    new BigDecimal("123.00"), seisCeros(), 6, 2, RonaldmichaRegimenLaboral.GENERAL, nueve);

            assertThat(r.gratificacion()).isEqualByComparingTo("1337.97");
        }

        @Test
        void c22IngresoElDiezDeMarzo() {
            RonaldmichaSemestreBeneficio julio = new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, 2026, 1);
            int meses = RonaldmichaelGratificacionCalculator.mesesCompletos(LocalDate.of(2026, 3, 10), null, julio.meses());

            RonaldmichaelGratificacionCalculator.Resultado r = RonaldmichaelGratificacionCalculator.calcular(new BigDecimal("1230.00"),
                    new BigDecimal("123.00"), seisCeros(), meses, 0, RonaldmichaRegimenLaboral.GENERAL, nueve);

            assertThat(meses).isEqualTo(3);
            assertThat(r.gratificacion()).isEqualByComparingTo("676.50");
        }

        @Test
        void c23VariableSoloSiSePercibioEnTresMesesOMas() {
            List<BigDecimal> dosMeses = montos("100.00", "200.00", "0", "0", "0", "0");
            List<BigDecimal> tresMeses = montos("100.00", "200.00", "300.00", "0", "0", "0");

            RonaldmichaelGratificacionCalculator.Resultado sinVariable = RonaldmichaelGratificacionCalculator.calcular(new BigDecimal("1230.00"),
                    new BigDecimal("123.00"), dosMeses, 6, 0, RonaldmichaRegimenLaboral.GENERAL, nueve);
            RonaldmichaelGratificacionCalculator.Resultado conVariable = RonaldmichaelGratificacionCalculator.calcular(new BigDecimal("1230.00"),
                    new BigDecimal("123.00"), tresMeses, 6, 0, RonaldmichaRegimenLaboral.GENERAL, nueve);

            assertThat(sinVariable.promedioVariable()).isEqualByComparingTo("0.00");
            assertThat(sinVariable.gratificacion()).isEqualByComparingTo("1353.00");
            assertThat(conVariable.promedioVariable()).isEqualByComparingTo("100.00");
            assertThat(conVariable.gratificacion()).isEqualByComparingTo("1453.00");
        }

        @Test
        void c24GratificacionSinAportesPeroConRenta() {
            RonaldmichaelResultadoPlanilla julio = RonaldmichaelPlanillaCalculator.calcular(
                    trabajador("7800.00", RonaldmichaSistemaPension.INTEGRA, RonaldmichaTipoComision.FLUJO, false), YearMonth.of(2026, 7),
                    P, Novedades.NINGUNA, OCHO_HORAS, List.of(),
                    new HistorialRenta(new BigDecimal("46800.00"), Collections.nCopies(6, new BigDecimal("703.71")),
                            new BigDecimal("17004.00"), RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, null));
            assertThat(julio.pension().remuneracion()).isEqualByComparingTo("7800.00");
            assertThat(julio.essalud()).isEqualByComparingTo("702.00");
            assertThat(julio.renta().rentaAnualProyectada()).isEqualByComparingTo("110604.00");
        }
    }

    @Nested
    class Cts {

        private final BigDecimal basico = new BigDecimal("1230.00");
        private final BigDecimal af = new BigDecimal("123.00");
        private final BigDecimal gratiJulio = new BigDecimal("1353.00");

        @Test
        void c25DepositoDeNoviembre() {
            RonaldmichaelCtsCalculator.Resultado r = RonaldmichaelCtsCalculator.calcular(basico, af, seisCeros(), new RonaldmichaelCtsCalculator.Tiempo(6, 0),
                    0, gratiJulio, RonaldmichaRegimenLaboral.GENERAL);

            assertThat(r.sextoGratificacion()).isEqualByComparingTo("225.50");
            assertThat(r.computable()).isEqualByComparingTo("1578.50");
            assertThat(r.cts()).isEqualByComparingTo("789.25");
        }

        @Test
        void c26PequenaYMicroempresa() {
            assertThat(RonaldmichaelCtsCalculator.calcular(basico, af, seisCeros(), new RonaldmichaelCtsCalculator.Tiempo(6, 0), 0, gratiJulio,
                    RonaldmichaRegimenLaboral.PEQUENA).cts()).isEqualByComparingTo("394.63");
            assertThat(RonaldmichaelCtsCalculator.calcular(basico, af, seisCeros(), new RonaldmichaelCtsCalculator.Tiempo(6, 0), 0, gratiJulio,
                    RonaldmichaRegimenLaboral.MICRO).cts()).isEqualByComparingTo("0.00");
        }

        @Test
        void c27CuatroMesesYDiezDias() {
            RonaldmichaelCtsCalculator.Resultado r = RonaldmichaelCtsCalculator.calcular(basico, af, seisCeros(), new RonaldmichaelCtsCalculator.Tiempo(4, 10),
                    0, gratiJulio, RonaldmichaRegimenLaboral.GENERAL);

            assertThat(r.cts()).isEqualByComparingTo("570.02");
        }

        @Test
        void c28CadaFaltaDescuentaUnTrescientosSesentavo() {
            RonaldmichaelCtsCalculator.Resultado r = RonaldmichaelCtsCalculator.calcular(basico, af, seisCeros(), new RonaldmichaelCtsCalculator.Tiempo(6, 0),
                    3, gratiJulio, RonaldmichaRegimenLaboral.GENERAL);

            assertThat(r.cts()).isEqualByComparingTo("776.10");
        }
    }

    @Nested
    class VacacionesYLiquidacion {

        private final BigDecimal computable = new BigDecimal("1353.00");

        @Test
        void c29VacacionesTruncas() {
            assertThat(RonaldmichaelLiquidacionCalculator.vacacionesTruncas(computable, 6, 15, RonaldmichaRegimenLaboral.GENERAL))
                    .isEqualByComparingTo("732.88");
            assertThat(RonaldmichaelLiquidacionCalculator.vacacionesTruncas(computable, 6, 15, RonaldmichaRegimenLaboral.PEQUENA))
                    .isEqualByComparingTo("366.44");
            assertThat(RonaldmichaelLiquidacionCalculator.vacacionesTruncas(computable, 6, 15, RonaldmichaRegimenLaboral.MICRO))
                    .isEqualByComparingTo("366.44");
        }

        @Test
        void c30VacacionesNoGozadasEIndemnizacionVacacionalAparte() {
            assertThat(RonaldmichaelLiquidacionCalculator.vacacionesNoGozadas(computable, 30)).isEqualByComparingTo("1353.00");

            RonaldmichaelLiquidacionCalculator.Resultado r = liquidar(RonaldmichaMotivoCese.RENUNCIA, RonaldmichaSistemaPension.ONP, null, 60);

            assertThat(r.truncoMeses()).isEqualTo(6);
            assertThat(r.truncoDias()).isEqualTo(15);
            assertThat(r.vacacionesTruncas()).isEqualByComparingTo("732.88");
            assertThat(r.vacacionesPendientes()).isEqualByComparingTo("2706.00");
            assertThat(r.diasIndemnizacionVacacional()).isEqualTo(30);
            assertThat(r.indemnizacionVacacional()).isEqualByComparingTo("1353.00");
            assertThat(r.subtotalVacaciones()).isEqualByComparingTo("4791.88");
        }

        @Test
        void c31VacacionesPaganPensionConSuTasaYSinEsSalud() {
            RonaldmichaelLiquidacionCalculator.Resultado onp = liquidar(RonaldmichaMotivoCese.RENUNCIA, RonaldmichaSistemaPension.ONP, null, 60);
            RonaldmichaelLiquidacionCalculator.Resultado afp = liquidar(RonaldmichaMotivoCese.RENUNCIA, RonaldmichaSistemaPension.INTEGRA,
                    RonaldmichaTipoComision.FLUJO, 60);

            assertThat(onp.pension().remuneracion()).isEqualByComparingTo("3438.88");
            assertThat(onp.pension().tasaAporte()).isEqualByComparingTo("13");
            assertThat(onp.pension().aporte()).isEqualByComparingTo("447.05");
            assertThat(afp.pension().tasaAporte()).isEqualByComparingTo("10");
            assertThat(afp.pension().aporte()).isEqualByComparingTo("343.89");
            assertThat(afp.pension().tasaPrima()).isEqualByComparingTo("1.37");
            assertThat(afp.pension().prima()).isEqualByComparingTo("47.11");
            assertThat(afp.pension().tasaComision()).isEqualByComparingTo("1.55");
            assertThat(afp.pension().comision()).isEqualByComparingTo("53.30");
            assertThat(afp.descuentos()).isEqualByComparingTo("444.30");
        }

        @Test
        void c32IndemnizacionPorDespidoArbitrario() {
            RonaldmichaelVacacionesCalculator.Tiempo dosAniosYMedio = new RonaldmichaelVacacionesCalculator.Tiempo(2, 6, 0);
            RonaldmichaelVacacionesCalculator.Tiempo diezAnios = new RonaldmichaelVacacionesCalculator.Tiempo(10, 0, 0);

            assertThat(RonaldmichaelLiquidacionCalculator.indemnizacion(computable, dosAniosYMedio, RonaldmichaRegimenLaboral.GENERAL))
                    .isEqualByComparingTo("5073.75");
            assertThat(RonaldmichaelLiquidacionCalculator.indemnizacion(computable, dosAniosYMedio, RonaldmichaRegimenLaboral.PEQUENA))
                    .isEqualByComparingTo("2255.00");
            assertThat(RonaldmichaelLiquidacionCalculator.indemnizacion(computable, dosAniosYMedio, RonaldmichaRegimenLaboral.MICRO))
                    .isEqualByComparingTo("1127.50");
            assertThat(RonaldmichaelLiquidacionCalculator.indemnizacion(computable, diezAnios, RonaldmichaRegimenLaboral.GENERAL))
                    .isEqualByComparingTo("16236.00");
            assertThat(RonaldmichaelLiquidacionCalculator.indemnizacion(computable, diezAnios, RonaldmichaRegimenLaboral.PEQUENA))
                    .isEqualByComparingTo("5412.00");
            assertThat(RonaldmichaelLiquidacionCalculator.indemnizacion(computable, diezAnios, RonaldmichaRegimenLaboral.MICRO))
                    .isEqualByComparingTo("4059.00");
            assertThat(liquidar(RonaldmichaMotivoCese.DESPIDO_ARBITRARIO, RonaldmichaSistemaPension.ONP, null, 0).indemnizacion())
                    .isPositive();
        }

        @Test
        void c33SinIndemnizacionPorRenunciaTerminoOMutuoDisenso() {
            for (RonaldmichaMotivoCese m : List.of(RonaldmichaMotivoCese.RENUNCIA, RonaldmichaMotivoCese.VENCIMIENTO, RonaldmichaMotivoCese.MUTUO_DISENSO)) {
                assertThat(liquidar(m, RonaldmichaSistemaPension.ONP, null, 0).indemnizacion()).as(m.etiqueta())
                        .isEqualByComparingTo("0.00");
            }
        }

        @Test
        void c34NetoEnLetras() {
            assertThat(RonaldmichaNumeroALetras.texto(new BigDecimal("1364.07")))
                    .isEqualTo("Mil trescientos sesenta y cuatro con 07/100 soles");
            assertThat(RonaldmichaNumeroALetras.texto(new BigDecimal("0.50"))).isEqualTo("Cero con 50/100 soles");
            assertThat(RonaldmichaNumeroALetras.texto(new BigDecimal("21000.00"))).isEqualTo("Veintiún mil con 00/100 soles");

            RonaldmichaelLiquidacionCalculator.Resultado r = liquidar(RonaldmichaMotivoCese.RENUNCIA, RonaldmichaSistemaPension.ONP, null, 60);
            assertThat(r.netoEnLetras()).isEqualTo(RonaldmichaNumeroALetras.texto(r.neto())).endsWith("/100 soles");
        }

        private RonaldmichaelLiquidacionCalculator.Resultado liquidar(RonaldmichaMotivoCese motivo, RonaldmichaSistemaPension sistema, RonaldmichaTipoComision tipo,
                                                         int pendientes) {
            return RonaldmichaelLiquidacionCalculator.calcular(new RonaldmichaelLiquidacionCalculator.Entrada(LocalDate.of(2023, 1, 1),
                    LocalDate.of(2026, 7, 15), motivo, RonaldmichaRegimenLaboral.GENERAL, new BigDecimal("1230.00"),
                    new BigDecimal("123.00"), pendientes, 0, new BigDecimal("1353.00"), 0, new BigDecimal("9"), P,
                    sistema, tipo, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO));
        }
    }

    @Nested
    class CoherenciaEntreModulos {

        @TempDir
        Path carpeta;

        private RonaldmichaelPlanillaService planilla;
        private RonaldmichaelBeneficiosService beneficios;
        private RonaldmichaelReportesService reportes;
        private RonaldmichaelParametrosService parametros;
        private RonaldmichaelAuditoriaService auditoria;
        private RonaldmichaUsuario admin;
        private RonaldmichaTrabajador alto;

        @BeforeEach
        void preparar() {
            RonaldmichaDatabase db = RonaldmichaBaseDatosPrueba.crear(carpeta);
            Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
            auditoria = new RonaldmichaelAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
            RonaldmichaTrabajadorRepository trabajadores = new RonaldmichaTrabajadorRepository(db);
            RonaldmichaPeriodoRepository periodoRepositorio = new RonaldmichaPeriodoRepository(db);
            parametros = new RonaldmichaelParametrosService(new RonaldmichaParametrosRepository(db), periodoRepositorio, trabajadores,
                    auditoria, reloj);
            RonaldmichaelPeriodoService periodos = new RonaldmichaelPeriodoService(periodoRepositorio, reloj);
            RonaldmichaAsistenciaRepository asistenciaRepositorio = new RonaldmichaAsistenciaRepository(db);
            RonaldmichaelAsistenciaService asistencia = new RonaldmichaelAsistenciaService(asistenciaRepositorio, trabajadores, periodos,
                    parametros, auditoria, reloj);
            planilla = new RonaldmichaelPlanillaService(trabajadores, asistenciaRepositorio, new RonaldmichaPlanillaRepository(db),
                    periodoRepositorio, periodos, parametros, asistencia, auditoria, reloj);
            RonaldmichaelEmpresaService empresa = new RonaldmichaelEmpresaService(new RonaldmichaEmpresaRepository(db), auditoria);
            beneficios = new RonaldmichaelBeneficiosService(trabajadores, new RonaldmichaBeneficioRepository(db), planilla, parametros,
                    empresa, auditoria);
            RonaldmichaVacacionRepository vacacionRepositorio = new RonaldmichaVacacionRepository(db);
            RonaldmichaelVacacionesService vacaciones = new RonaldmichaelVacacionesService(trabajadores, vacacionRepositorio, periodos, empresa,
                    auditoria, reloj);
            RonaldmichaelLiquidacionService liquidaciones = new RonaldmichaelLiquidacionService(trabajadores, new RonaldmichaLiquidacionRepository(db),
                    vacacionRepositorio, beneficios, parametros, periodos, empresa, auditoria, reloj);
            reportes = new RonaldmichaelReportesService(trabajadores, planilla, beneficios, liquidaciones, vacaciones, parametros,
                    empresa, new RonaldmichaDocumentoRepository(db), RonaldmichaelMailService.NO_CONFIGURADO, auditoria, reloj);
            admin = new RonaldmichaUsuario(1L, "admin", "", RonaldmichaRol.ADMIN, null, true);
            alto = trabajadores.insertar(new RonaldmichaTrabajador(null, "T0900", RonaldmichaTipoDocumento.DNI, "40000900", "Prueba", "Renta",
                    "Alta", null, null, ANTIGUO, null, "Gerencia", "Director", "Indeterminado", RonaldmichaRegimenLaboral.GENERAL,
                    null, null, new BigDecimal("7800.00"), RonaldmichaDinero.CERO, RonaldmichaSalud.ESSALUD, RonaldmichaSistemaPension.INTEGRA,
                    "000000PRREN0", RonaldmichaTipoComision.FLUJO, false, false, RonaldmichaTipoPago.DEPOSITO, "Banco Ejemplo",
                    "000-0000900", "Caja Ejemplo", "100-0000900"),
                    new RonaldmichaHorario(LocalTime.of(8, 0), LocalTime.of(17, 0), 60, 0,
                            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), false));
        }

        @Test
        void c15ElAnioCompletoEnLaPlanillaRetieneElImpuestoAnual() {
            List<PlanillaMes> anio = planilla.delAnio(YearMonth.of(2026, 12));

            List<BigDecimal> retenciones = anio.stream().map(pm -> fila(pm, alto.id()).resultado().rentaQuinta())
                    .toList();
            assertThat(retenciones.get(0)).isEqualByComparingTo("703.71");
            assertThat(RonaldmichaDinero.sumar(retenciones.toArray(BigDecimal[]::new))).isEqualByComparingTo("8444.56");
            assertThat(fila(planilla.delMes(OCTUBRE), alto.id()).resultado().rentaQuinta())
                    .isEqualByComparingTo(retenciones.get(9));
        }

        @Test
        void c17CertificadoDeRentasYRetencionesCuadra() {
            BigDecimal retenidoPlanillas = RonaldmichaDinero.sumar(planilla.delAnio(YearMonth.of(2026, 12)).stream()
                    .map(pm -> fila(pm, alto.id()).resultado().rentaQuinta()).toArray(BigDecimal[]::new));

            RonaldmichaDocumentModel d = reportes.vistaPrevia(admin, RonaldmichaelReporte.RENTA_QUINTA,
                    new Parametros(YearMonth.of(2026, 12), alto.id(), false));

            Map<String, String> pares = pares(d);
            assertThat(pares.get("Renta bruta")).isEqualTo("S/ 110,604.00");
            assertThat(pares.get("Deducción de 7 UIT")).isEqualTo("S/ 38,500.00");
            assertThat(pares.get("Renta neta")).isEqualTo("S/ 72,104.00");
            assertThat(pares.get("Impuesto calculado")).isEqualTo("S/ 8,444.56");
            assertThat(pares.get("Impuesto anual proyectado en diciembre (art. 40)")).isEqualTo("S/ 8,444.56");
            assertThat(pares.get("Retenido por el empleador")).isEqualTo("S/ 8,444.56");
            assertThat(pares.get("Diferencia")).isEqualTo("S/ 0.00");
            assertThat(retenidoPlanillas).isEqualByComparingTo("8444.56");
            Total total = bloque(d, Total.class);
            assertThat(total.monto()).isEqualByComparingTo(retenidoPlanillas);
        }

        @Test
        void otrosEmpleadoresEntranALaProyeccion() {
            planilla.guardarOtroEmpleador(admin, alto.id(), 2026, null, "Empresa Anterior SAC",
                    new BigDecimal("10000.00"), new BigDecimal("500.00"));

            RonaldmichaelResultadoPlanilla enero = fila(planilla.delMes(YearMonth.of(2026, 1)), alto.id()).resultado();

            assertThat(enero.renta().rentaAnualProyectada()).isEqualByComparingTo("120604.00");
            assertThat(enero.rentaQuinta()).isEqualByComparingTo("820.38");
            assertThat(auditoria.recientes(100)).anyMatch(a -> a.accion().equals("RENTA_OTRO_EMPLEADOR"));
        }

        @Test
        void netoIgualEnBoletaPlanillaPagosYExcel() throws IOException {
            PlanillaMes pm = planilla.delMes(OCTUBRE);
            String txt = new String(reportes.exportar(admin, RonaldmichaelReporte.PAGOS, Formato.TXT,
                    new Parametros(OCTUBRE, null, false)).contenido(), StandardCharsets.UTF_8);
            Map<String, BigDecimal> pagosTxt = new HashMap<>();
            txt.lines().map(l -> l.split("\\|")).forEach(c -> pagosTxt.put(c[1], new BigDecimal(c[6])));
            Map<String, BigDecimal> pagosExcel = columnaExcel(reportes.exportar(admin, RonaldmichaelReporte.PAGOS, Formato.EXCEL,
                    new Parametros(OCTUBRE, null, false)).contenido(), 2, 6);
            Map<String, BigDecimal> planillaExcel = columnaExcel(reportes.exportar(admin, RonaldmichaelReporte.PLANILLA,
                    Formato.EXCEL, new Parametros(OCTUBRE, null, false)).contenido(), 2, 31);

            assertThat(pm.filas()).hasSizeGreaterThan(5);
            for (FilaPlanilla f : pm.filas()) {
                RonaldmichaTrabajador t = f.trabajador();
                BigDecimal neto = f.resultado().netoAPagar();
                RonaldmichaDocumentModel boleta = reportes.vistaPrevia(admin, RonaldmichaelReporte.BOLETA_PAGO,
                        new Parametros(OCTUBRE, t.id(), false));
                assertThat(boleta.boleta().neto()).as("boleta " + t.numDoc()).isEqualByComparingTo(neto);
                assertThat(planillaExcel.get(t.tipoDoc().codigo() + " " + t.numDoc())).as("Excel " + t.numDoc())
                        .isEqualByComparingTo(neto);
                assertThat(pagosExcel.get(t.numDoc())).as("pagos Excel " + t.numDoc()).isEqualByComparingTo(neto);
                if (pagosTxt.containsKey(t.numDoc())) {
                    assertThat(pagosTxt.get(t.numDoc())).as("pagos TXT " + t.numDoc()).isEqualByComparingTo(neto);
                }
            }
            assertThat(pagosTxt).containsKey(alto.numDoc());
        }

        @Test
        void totalesDeLaPlanillaIgualALaSumaDeLasFilas() {
            PlanillaMes pm = planilla.delMes(OCTUBRE);
            Totales t = Totales.de(pm.filas());

            BigDecimal ingresos = RonaldmichaDinero.CERO;
            BigDecimal descuentos = RonaldmichaDinero.CERO;
            BigDecimal aportes = RonaldmichaDinero.CERO;
            BigDecimal neto = RonaldmichaDinero.CERO;
            BigDecimal renta = RonaldmichaDinero.CERO;
            for (FilaPlanilla f : pm.filas()) {
                RonaldmichaelResultadoPlanilla r = f.resultado();
                assertThat(r.totalDe(TipoConcepto.INGRESO).subtract(r.totalDe(TipoConcepto.REBAJA)))
                        .isEqualByComparingTo(r.totalIngresos());
                assertThat(r.totalDe(TipoConcepto.DESCUENTO)).isEqualByComparingTo(r.totalDescuentos());
                assertThat(r.totalDe(TipoConcepto.APORTE)).isEqualByComparingTo(r.totalAportes());
                assertThat(r.totalIngresos().subtract(r.totalDescuentos())).isEqualByComparingTo(r.netoAPagar());
                ingresos = ingresos.add(r.totalIngresos());
                descuentos = descuentos.add(r.totalDescuentos());
                aportes = aportes.add(r.totalAportes());
                neto = neto.add(r.netoAPagar());
                renta = renta.add(r.rentaQuinta());
            }
            assertThat(t.trabajadores()).isEqualTo(pm.filas().size());
            assertThat(t.ingresos()).isEqualByComparingTo(ingresos);
            assertThat(t.descuentos()).isEqualByComparingTo(descuentos);
            assertThat(t.aportes()).isEqualByComparingTo(aportes);
            assertThat(t.neto()).isEqualByComparingTo(neto);
            assertThat(t.rentaQuinta()).isEqualByComparingTo(renta);
        }

        @Test
        void plameRemPorCodigoIgualALosTotalesDeLaPlanilla() throws IOException {
            Map<String, BigDecimal> planillaPorCodigo = new TreeMap<>();
            for (FilaPlanilla f : planilla.delMes(OCTUBRE).filas()) {
                for (Concepto c : f.resultado().conceptos()) {
                    if (c.codigoPlame() != null) {
                        planillaPorCodigo.merge(c.codigoPlame(), c.monto().abs(), BigDecimal::add);
                    }
                }
            }
            Map<String, BigDecimal> remPorCodigo = new TreeMap<>();
            byte[] zip = reportes.exportar(admin, RonaldmichaelReporte.PLAME, Formato.ZIP, new Parametros(OCTUBRE, null, false))
                    .contenido();
            try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
                for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                    if (e.getName().endsWith(".rem")) {
                        new String(in.readAllBytes(), StandardCharsets.ISO_8859_1).lines().map(l -> l.split("\\|"))
                                .forEach(c -> remPorCodigo.merge(c[2], new BigDecimal(c[3]), BigDecimal::add));
                    }
                }
            }

            assertThat(remPorCodigo.keySet()).isEqualTo(planillaPorCodigo.keySet()).contains("0605", "0608", "0804");
            planillaPorCodigo.forEach((codigo, monto) -> assertThat(remPorCodigo.get(codigo)).as(codigo)
                    .isEqualByComparingTo(monto));
        }

        @Test
        void afpnetRemuneracionAsegurableYAportePorAfiliado() throws IOException {
            PlanillaMes pm = planilla.delMes(OCTUBRE);
            Map<String, BigDecimal> asegurable = columnaExcel(reportes.exportar(admin, RonaldmichaelReporte.AFPNET, Formato.EXCEL,
                    new Parametros(OCTUBRE, null, false)).contenido(), 3, 11);
            RonaldmichaDocumentModel vista = reportes.vistaPrevia(admin, RonaldmichaelReporte.AFPNET, new Parametros(OCTUBRE, null, false));
            Map<String, List<String>> porDocumento = new HashMap<>();
            vista.bloques().stream().filter(Table.class::isInstance).map(Table.class::cast)
                    .flatMap(t -> t.filas().stream()).forEach(f -> porDocumento.put(f.get(2), f));

            int afiliados = 0;
            for (FilaPlanilla f : pm.filas()) {
                if (!f.trabajador().sistemaPension().esAfp()) {
                    continue;
                }
                afiliados++;
                RonaldmichaelResultadoPlanilla r = f.resultado();
                String doc = f.trabajador().numDoc();
                assertThat(asegurable.get(doc)).as("asegurable " + doc).isEqualByComparingTo(r.pension().remuneracion());
                List<String> linea = porDocumento.get(doc);
                assertThat(linea.get(7)).as("aporte " + doc).isEqualTo(numero(r.pension().aporte()));
                assertThat(linea.get(8)).as("prima " + doc).isEqualTo(numero(r.pension().prima()));
                assertThat(linea.get(9)).as("comisión " + doc).isEqualTo(numero(r.pension().comision()));
            }
            assertThat(afiliados).isGreaterThan(3);
        }

        @Test
        void unMesCerradoNoCambiaAunqueCambienLosParametros() {
            YearMonth setiembre = YearMonth.of(2026, 9);
            Map<Long, BigDecimal> antes = netos(planilla.delMes(setiembre));
            Map<Long, BigDecimal> rentaAntes = rentas(planilla.delMes(setiembre));
            planilla.cerrar(admin, setiembre);

            assertThatThrownBy(() -> parametros.registrar(version(setiembre, "1300.00"), admin))
                    .isInstanceOf(RonaldmichaelValidacionException.class).hasMessageContaining("cerrado");
            Map<Long, BigDecimal> octubreAntes = netos(planilla.delMes(OCTUBRE));
            parametros.registrar(version(YearMonth.of(2026, 11), "1300.00"), admin);

            assertThat(netos(planilla.delMes(setiembre))).isEqualTo(antes);
            assertThat(rentas(planilla.delMes(setiembre))).isEqualTo(rentaAntes);
            assertThat(netos(planilla.delMes(OCTUBRE))).isEqualTo(octubreAntes);
            assertThat(fila(planilla.delMes(YearMonth.of(2026, 11)), alto.id()).resultado().pension().aporte())
                    .isEqualByComparingTo("780.00");
        }

        @Test
        void reabrirUnMesRecalculaLaRentaEnCascada() {
            YearMonth julio = YearMonth.of(2026, 7);
            YearMonth agosto = YearMonth.of(2026, 8);
            planilla.cerrar(admin, julio);
            planilla.cerrar(admin, agosto);
            BigDecimal agostoAntes = fila(planilla.delMes(agosto), alto.id()).resultado().rentaQuinta();
            assertThat(agostoAntes).isEqualByComparingTo("703.71");

            planilla.reabrir(admin, julio, "Faltas no registradas");
            planilla.guardar(admin, alto.id(), julio, new Borrador(2, 0, 0, 0, 0, 0, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO,
                    RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, List.of(), List.of()));
            assertThat(fila(planilla.delMes(agosto), alto.id()).resultado().rentaQuinta())
                    .isEqualByComparingTo(agostoAntes);
            planilla.cerrar(admin, julio);

            assertThat(fila(planilla.delMes(julio), alto.id()).resultado().rentaQuinta())
                    .isEqualByComparingTo("649.12");
            assertThat(fila(planilla.delMes(agosto), alto.id()).resultado().rentaQuinta())
                    .isEqualByComparingTo("700.07");
            assertThat(auditoria.recientes(100))
                    .anyMatch(a -> a.accion().equals("RENTA_QUINTA_RECALCULADA"));
        }

        @Test
        void cambiarLaAsistenciaCambiaPlanillaBoletaGratificacionYCts() {
            BigDecimal netoAntes = fila(planilla.delMes(OCTUBRE), alto.id()).resultado().netoAPagar();
            BigDecimal gratiAntes = beneficio(new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, 2026, 2)).total();
            BigDecimal ctsAntes = beneficio(new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.CTS, 2026, 2)).total();

            planilla.guardar(admin, alto.id(), OCTUBRE, new Borrador(2, 0, 0, 0, 0, 0, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO,
                    RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, List.of(he(LocalDate.of(2026, 10, 5), "1")), List.of()));

            RonaldmichaelResultadoPlanilla r = fila(planilla.delMes(OCTUBRE), alto.id()).resultado();
            assertThat(r.basico()).isEqualByComparingTo("7280.00");
            assertThat(r.horasExtra25()).isEqualByComparingTo("40.63");
            assertThat(r.netoAPagar()).isNotEqualByComparingTo(netoAntes);
            RonaldmichaDocumentModel boleta = reportes.vistaPrevia(admin, RonaldmichaelReporte.BOLETA_PAGO,
                    new Parametros(OCTUBRE, alto.id(), false));
            assertThat(boleta.boleta().neto()).isEqualByComparingTo(r.netoAPagar());
            FilaBeneficio grati = beneficio(new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, 2026, 2));
            assertThat(grati.gratificacion()).isEqualByComparingTo("7713.33");
            assertThat(grati.total()).isLessThan(gratiAntes);
            assertThat(beneficio(new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.CTS, 2026, 2)).total()).isLessThan(ctsAntes);
        }

        private FilaBeneficio beneficio(RonaldmichaSemestreBeneficio s) {
            return beneficios.consultar(admin, s).filas().stream()
                    .filter(f -> f.trabajador().id().equals(alto.id())).findFirst().orElseThrow();
        }

        private RonaldmichaParametrosVersion version(YearMonth desde, String rmv) {
            return new RonaldmichaParametrosVersion(null, desde, "Prueba", new BigDecimal(rmv), UIT, P.asigFamiliarPct(),
                    P.essaludPct(), P.epsPct(), P.onpPct(), P.afpAportePct(), P.afpPrimaPct(), P.rmaPrima(),
                    P.bonifExtPct(), P.bonifExtEpsPct(), P.vidaLeyPct(), P.sisMonto(), RonaldmichaEstadoParametros.VIGENTE, null,
                    null, P.comisiones(), null, null, null);
        }
    }

    private static RonaldmichaelResultadoPlanilla calcular(RonaldmichaTrabajador t, Novedades n) {
        return RonaldmichaelPlanillaCalculator.calcular(t, OCTUBRE, P, n, OCHO_HORAS, List.of());
    }

    private static Novedades novedades(int faltas, int vacaciones, int subsidiados) {
        return new Novedades(faltas, vacaciones, subsidiados, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO);
    }

    private static HoraExtraManual he(LocalDate fecha, String horas) {
        return new HoraExtraManual(fecha, new BigDecimal(horas), RonaldmichaTratamientoHe.PAGAR, true);
    }

    private static List<BigDecimal> seisCeros() {
        return Collections.nCopies(6, RonaldmichaDinero.CERO);
    }

    private static List<BigDecimal> montos(String... valores) {
        return java.util.Arrays.stream(valores).map(BigDecimal::new).toList();
    }

    private static RonaldmichaTrabajador trabajador(String sueldo, RonaldmichaSistemaPension sistema, RonaldmichaTipoComision comision, boolean af) {
        return trabajador(sueldo, sistema, comision, af, ANTIGUO, null);
    }

    private static RonaldmichaTrabajador trabajador(String sueldo, RonaldmichaSistemaPension sistema, RonaldmichaTipoComision comision, boolean af,
                                         LocalDate ingreso, LocalDate cese) {
        return new RonaldmichaTrabajador(1L, "T0001", RonaldmichaTipoDocumento.DNI, "40000001", "Quispe", "Mamani", "Rosa", null, null,
                ingreso, cese, "Operaciones", "Asistente", "Indeterminado", RonaldmichaRegimenLaboral.GENERAL, null, null,
                new BigDecimal(sueldo), BigDecimal.ZERO, RonaldmichaSalud.ESSALUD, sistema, null, comision, af, false,
                RonaldmichaTipoPago.DEPOSITO, null, null, null, null);
    }

    private static RonaldmichaTrabajador con(RonaldmichaTrabajador t, RonaldmichaRegimenLaboral regimen, RonaldmichaSalud salud) {
        return new RonaldmichaTrabajador(t.id(), t.codigo(), t.tipoDoc(), t.numDoc(), t.apPaterno(), t.apMaterno(), t.nombres(),
                null, null, t.fechaIngreso(), t.fechaCese(), t.area(), t.cargo(), t.tipoContrato(), regimen, null, null,
                t.sueldo(), t.movilidad(), salud, t.sistemaPension(), null, t.tipoComision(), t.asigFamiliar(), false,
                RonaldmichaTipoPago.DEPOSITO, null, null, null, null);
    }

    private static FilaPlanilla fila(PlanillaMes pm, long trabajadorId) {
        return pm.filas().stream().filter(f -> f.trabajador().id() == trabajadorId).findFirst().orElseThrow();
    }

    private static Map<Long, BigDecimal> netos(PlanillaMes pm) {
        Map<Long, BigDecimal> mapa = new TreeMap<>();
        pm.filas().forEach(f -> mapa.put(f.trabajador().id(), f.resultado().netoAPagar()));
        return mapa;
    }

    private static Map<Long, BigDecimal> rentas(PlanillaMes pm) {
        Map<Long, BigDecimal> mapa = new TreeMap<>();
        pm.filas().forEach(f -> mapa.put(f.trabajador().id(), f.resultado().rentaQuinta()));
        return mapa;
    }

    private static <T> T bloque(RonaldmichaDocumentModel d, Class<T> tipo) {
        return d.bloques().stream().filter(tipo::isInstance).map(tipo::cast).findFirst().orElseThrow();
    }

    private static Map<String, String> pares(RonaldmichaDocumentModel d) {
        Map<String, String> mapa = new HashMap<>();
        d.bloques().stream().filter(KeyValue.class::isInstance).map(KeyValue.class::cast)
                .flatMap(kv -> kv.pares().stream()).forEach((Par p) -> mapa.put(p.etiqueta(), p.valor()));
        return mapa;
    }

    private static Map<String, BigDecimal> columnaExcel(byte[] xlsx, int clave, int columna) throws IOException {
        Map<String, BigDecimal> mapa = new HashMap<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            for (Sheet hoja : wb) {
                for (Row fila : hoja) {
                    Cell k = fila.getCell(clave);
                    Cell v = fila.getCell(columna);
                    if (k != null && v != null && k.getCellType() == CellType.STRING
                            && v.getCellType() == CellType.NUMERIC) {
                        mapa.put(k.getStringCellValue(), RonaldmichaDinero.redondear(BigDecimal.valueOf(v.getNumericCellValue())));
                    }
                }
            }
        }
        return mapa;
    }

    private static String numero(BigDecimal monto) {
        return com.planillaperu.common.RonaldmichaFormatos.numero(monto);
    }
}
