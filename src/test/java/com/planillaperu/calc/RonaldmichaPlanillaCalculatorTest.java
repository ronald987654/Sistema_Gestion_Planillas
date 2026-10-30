package com.planillaperu.calc;

import com.planillaperu.calc.RonaldmichaPlanillaCalculator.Asistencia;
import com.planillaperu.calc.RonaldmichaPlanillaCalculator.Novedades;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla.Concepto;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla.TipoConcepto;
import com.planillaperu.domain.RonaldmichaAfpComision;
import com.planillaperu.domain.RonaldmichaConceptoExtra;
import com.planillaperu.domain.RonaldmichaEstadoParametros;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaSalud;
import com.planillaperu.domain.RonaldmichaSistemaPension;
import com.planillaperu.domain.RonaldmichaTipoComision;
import com.planillaperu.domain.RonaldmichaTipoConceptoExtra;
import com.planillaperu.domain.RonaldmichaTipoDocumento;
import com.planillaperu.domain.RonaldmichaTipoPago;
import com.planillaperu.domain.RonaldmichaTrabajador;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RonaldmichaPlanillaCalculatorTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final LocalDate ANTIGUO = LocalDate.of(2020, 1, 1);
    private static final Asistencia OCHO_HORAS = Asistencia.sinNovedades(new BigDecimal("8"));

    private static final RonaldmichaParametrosVersion P = new RonaldmichaParametrosVersion(1L, OCTUBRE, null,
            new BigDecimal("1230.00"), new BigDecimal("5500.00"), new BigDecimal("10"), new BigDecimal("9"),
            new BigDecimal("6.75"), new BigDecimal("13"), new BigDecimal("10"), new BigDecimal("1.37"),
            new BigDecimal("12732.70"), new BigDecimal("9"), new BigDecimal("6.75"), new BigDecimal("1.53"),
            new BigDecimal("15.00"), RonaldmichaEstadoParametros.VIGENTE, "prueba", null,
            List.of(new RonaldmichaAfpComision("AFP Habitat", new BigDecimal("1.47"), new BigDecimal("1.25")),
                    new RonaldmichaAfpComision("AFP Integra", new BigDecimal("1.55"), new BigDecimal("0.78"))),
            null, null, null);

    @Test
    void onpConAsignacionFamiliarMesCompleto() {
        RonaldmichaResultadoPlanilla r = calcular(trabajador("1230.00", RonaldmichaSistemaPension.ONP, null, true), Novedades.NINGUNA);

        assertThat(r.diasMes()).isEqualTo(30);
        assertThat(r.diasLaborados()).isEqualTo(30);
        assertThat(r.basico()).isEqualByComparingTo("1230.00");
        assertThat(r.asignacionFamiliar()).isEqualByComparingTo("123.00");
        assertThat(r.remuneracionAfecta()).isEqualByComparingTo("1353.00");
        assertThat(r.pension().aporte()).isEqualByComparingTo("175.89");
        assertThat(r.rentaQuinta()).isEqualByComparingTo("0");
        assertThat(r.essalud()).isEqualByComparingTo("121.77");
        assertThat(r.totalIngresos()).isEqualByComparingTo("1353.00");
        assertThat(r.totalDescuentos()).isEqualByComparingTo("175.89");
        assertThat(r.netoAPagar()).isEqualByComparingTo("1177.11");
        assertThat(r.totalAportes()).isEqualByComparingTo("121.77");
        assertThat(r.provisionGratificacion()).isEqualByComparingTo("245.80");
        assertThat(r.provisionCts()).isEqualByComparingTo("131.54");
        assertThat(r.provisionVacaciones()).isEqualByComparingTo("112.75");
        assertThat(r.costoLaboral()).isEqualByComparingTo("1964.86");
        assertThat(r.conceptos()).extracting(Concepto::codigoPlame, Concepto::tipo, Concepto::monto)
                .containsExactly(
                        tuple("0121", TipoConcepto.INGRESO, new BigDecimal("1230.00")),
                        tuple("0201", TipoConcepto.INGRESO, new BigDecimal("123.00")),
                        tuple("0607", TipoConcepto.DESCUENTO, new BigDecimal("175.89")),
                        tuple("0804", TipoConcepto.APORTE, new BigDecimal("121.77")));
    }

    @Test
    void afpIntegraFlujoConElMismoSueldo() {
        RonaldmichaResultadoPlanilla r = calcular(trabajador("1230.00", RonaldmichaSistemaPension.INTEGRA, RonaldmichaTipoComision.FLUJO, true),
                Novedades.NINGUNA);

        assertThat(r.pension().aporte()).isEqualByComparingTo("135.30");
        assertThat(r.pension().prima()).isEqualByComparingTo("18.54");
        assertThat(r.pension().comision()).isEqualByComparingTo("20.97");
        assertThat(r.totalDescuentos()).isEqualByComparingTo("174.81");
        assertThat(r.netoAPagar()).isEqualByComparingTo("1178.19");
        assertThat(r.conceptos()).extracting(Concepto::codigoPlame)
                .containsExactly("0121", "0201", "0608", "0606", "0601", "0804");
    }

    @Test
    void diasComercialesSegunIngresoYCese() {
        assertThat(RonaldmichaPlanillaCalculator.diasDelMes(ANTIGUO, null, OCTUBRE)).isEqualTo(30);
        assertThat(RonaldmichaPlanillaCalculator.diasDelMes(LocalDate.of(2026, 10, 16), null, OCTUBRE)).isEqualTo(15);
        assertThat(RonaldmichaPlanillaCalculator.diasDelMes(LocalDate.of(2026, 10, 31), null, OCTUBRE)).isEqualTo(1);
        assertThat(RonaldmichaPlanillaCalculator.diasDelMes(ANTIGUO, LocalDate.of(2026, 10, 10), OCTUBRE)).isEqualTo(10);
        assertThat(RonaldmichaPlanillaCalculator.diasDelMes(ANTIGUO, LocalDate.of(2026, 10, 31), OCTUBRE)).isEqualTo(30);
        assertThat(RonaldmichaPlanillaCalculator.diasDelMes(ANTIGUO, LocalDate.of(2027, 2, 28), YearMonth.of(2027, 2)))
                .isEqualTo(30);
        assertThat(RonaldmichaPlanillaCalculator.diasDelMes(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 20), OCTUBRE))
                .isEqualTo(16);
        assertThat(RonaldmichaPlanillaCalculator.diasDelMes(ANTIGUO, LocalDate.of(2026, 9, 30), OCTUBRE)).isZero();
        assertThat(RonaldmichaPlanillaCalculator.diasDelMes(LocalDate.of(2026, 11, 1), null, OCTUBRE)).isZero();
    }

    @Test
    void ingresoAMitadDeMesProrrateaBasicoYAsignacion() {
        RonaldmichaTrabajador t = trabajador("3000.00", RonaldmichaSistemaPension.ONP, null, true, LocalDate.of(2026, 10, 16), null);

        RonaldmichaResultadoPlanilla r = calcular(t, Novedades.NINGUNA);

        assertThat(r.diasMes()).isEqualTo(15);
        assertThat(r.basico()).isEqualByComparingTo("1500.00");
        assertThat(r.asignacionFamiliar()).isEqualByComparingTo("61.50");
        assertThat(r.remuneracionAfecta()).isEqualByComparingTo("1561.50");
        assertThat(r.pension().aporte()).isEqualByComparingTo("203.00");
        assertThat(r.essalud()).isEqualByComparingTo("140.54");
        assertThat(r.provisionVacaciones()).isEqualByComparingTo("130.13");
    }

    @Test
    void ceseAMitadDeMes() {
        RonaldmichaTrabajador t = trabajador("3000.00", RonaldmichaSistemaPension.ONP, null, false, ANTIGUO, LocalDate.of(2026, 10, 10));

        RonaldmichaResultadoPlanilla r = calcular(t, Novedades.NINGUNA);

        assertThat(r.diasMes()).isEqualTo(10);
        assertThat(r.basico()).isEqualByComparingTo("1000.00");
        assertThat(r.pension().aporte()).isEqualByComparingTo("130.00");
        assertThat(r.essalud()).isEqualByComparingTo("90.00");
        assertThat(r.netoAPagar()).isEqualByComparingTo("870.00");
    }

    @Test
    void faltasDescuentanBasicoYMovilidad() {
        RonaldmichaTrabajador t = conMovilidad(trabajador("3000.00", RonaldmichaSistemaPension.ONP, null, false), "300.00");

        RonaldmichaResultadoPlanilla r = calcular(t, novedades(2, 0, 0));

        assertThat(r.diasLaborados()).isEqualTo(28);
        assertThat(r.basico()).isEqualByComparingTo("2800.00");
        assertThat(r.movilidad()).isEqualByComparingTo("280.00");
        assertThat(r.remuneracionAfecta()).isEqualByComparingTo("2800.00");
        assertThat(r.pension().aporte()).isEqualByComparingTo("364.00");
        assertThat(r.essalud()).isEqualByComparingTo("252.00");
        assertThat(r.totalIngresos()).isEqualByComparingTo("3080.00");
        assertThat(r.rentaQuinta()).isEqualByComparingTo("0.00");
        assertThat(r.netoAPagar()).isEqualByComparingTo("2716.00");
        assertThat(r.conceptos()).extracting(Concepto::codigoPlame).contains("0909");
    }

    @Test
    void essaludNoBajaDeLaRmvProporcional() {
        RonaldmichaResultadoPlanilla r = calcular(trabajador("1230.00", RonaldmichaSistemaPension.ONP, null, false), novedades(10, 0, 0));

        assertThat(r.basico()).isEqualByComparingTo("820.00");
        assertThat(r.essalud()).isEqualByComparingTo("110.70");
    }

    @Test
    void vacacionesSePaganComoRemuneracionVacacional() {
        RonaldmichaResultadoPlanilla r = calcular(trabajador("3000.00", RonaldmichaSistemaPension.ONP, null, false), novedades(0, 10, 0));

        assertThat(r.diasLaborados()).isEqualTo(20);
        assertThat(r.basico()).isEqualByComparingTo("2000.00");
        assertThat(r.remuneracionVacacional()).isEqualByComparingTo("1000.00");
        assertThat(r.remuneracionAfecta()).isEqualByComparingTo("3000.00");
    }

    @Test
    void subsidioAfectaPensionPeroNoEssalud() {
        RonaldmichaResultadoPlanilla r = calcular(trabajador("3000.00", RonaldmichaSistemaPension.ONP, null, true), novedades(0, 0, 10));

        assertThat(r.diasLaborados()).isEqualTo(20);
        assertThat(r.basico()).isEqualByComparingTo("2000.00");
        assertThat(r.asignacionFamiliar()).isEqualByComparingTo("123.00");
        assertThat(r.remuneracionAfecta()).isEqualByComparingTo("2123.00");
        assertThat(r.subsidio()).isEqualByComparingTo("1041.00");
        assertThat(r.basePension()).isEqualByComparingTo("3164.00");
        assertThat(r.pension().aporte()).isEqualByComparingTo("411.32");
        assertThat(r.essalud()).isEqualByComparingTo("191.07");
        assertThat(r.totalIngresos()).isEqualByComparingTo("3164.00");
        assertThat(r.conceptos()).extracting(Concepto::codigoPlame, Concepto::monto)
                .contains(tuple("0916", new BigDecimal("1041.00")));
    }

    @Test
    void horasExtraYFeriadoTrabajadoConTardanza() {
        RonaldmichaTrabajador t = trabajador("2400.00", RonaldmichaSistemaPension.ONP, null, false);
        Asistencia a = new Asistencia(new BigDecimal("8"), 30, 120, 60, 1);

        RonaldmichaResultadoPlanilla r = RonaldmichaPlanillaCalculator.calcular(t, OCTUBRE, P, Novedades.NINGUNA, a, List.of());

        assertThat(r.valorHora()).isEqualByComparingTo("10.00");
        assertThat(r.horasExtra25()).isEqualByComparingTo("25.00");
        assertThat(r.horasExtra35()).isEqualByComparingTo("13.50");
        assertThat(r.feriadoTrabajado()).isEqualByComparingTo("160.00");
        assertThat(r.tardanza()).isEqualByComparingTo("5.00");
        assertThat(r.remuneracionAfecta()).isEqualByComparingTo("2593.50");
        assertThat(r.totalDe(TipoConcepto.INGRESO).subtract(r.totalDe(TipoConcepto.REBAJA)))
                .isEqualByComparingTo(r.totalIngresos());
        assertThat(r.conceptos()).extracting(Concepto::codigoPlame, Concepto::tipo)
                .contains(tuple("0105", TipoConcepto.INGRESO), tuple("0106", TipoConcepto.INGRESO),
                        tuple("0107", TipoConcepto.INGRESO), tuple("0704", TipoConcepto.REBAJA));
    }

    @Test
    void rentaDeQuintaPorTramos() {
        RonaldmichaResultadoPlanilla r = RonaldmichaPlanillaCalculator.calcular(trabajador("8000.00", RonaldmichaSistemaPension.ONP, null, false),
                YearMonth.of(2026, 1), P, Novedades.NINGUNA, OCHO_HORAS, List.of());

        assertThat(r.rentaAnualProyectada()).isEqualByComparingTo("113440.00");
        assertThat(r.rentaQuinta()).isEqualByComparingTo("736.80");
        assertThat(r.netoAPagar()).isEqualByComparingTo("6223.20");
        assertThat(r.conceptos()).extracting(Concepto::codigoPlame).contains("0605");
    }

    @Test
    void rentaUsaTodosLosTramos() {
        RonaldmichaRentaQuintaCalculator.Renta r = RonaldmichaRentaQuintaCalculator.calcular(new RonaldmichaRentaQuintaCalculator.Entrada(1,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("368500.00"), BigDecimal.ZERO, List.of(), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("5500.00")));

        assertThat(r.rentaNeta()).isEqualByComparingTo("330000.00");
        assertThat(r.impuestoAnual()).isEqualByComparingTo("63525.00");
        assertThat(r.retencionMensual()).isEqualByComparingTo("5293.75");
    }

    @Test
    void conceptosExtraEpsVidaLeyYAfpMixta() {
        RonaldmichaTrabajador t = conSalud(trabajador("2000.00", RonaldmichaSistemaPension.HABITAT, RonaldmichaTipoComision.MIXTA, false), RonaldmichaSalud.EPS,
                true);
        Novedades n = new Novedades(0, 0, 0, new BigDecimal("150.00"), BigDecimal.ZERO, new BigDecimal("200.00"),
                BigDecimal.ZERO);
        List<RonaldmichaConceptoExtra> extras = List.of(
                new RonaldmichaConceptoExtra(RonaldmichaTipoConceptoExtra.ING, "0312", "Bono de productividad", new BigDecimal("100.00")),
                new RonaldmichaConceptoExtra(RonaldmichaTipoConceptoExtra.ING_NR, "0903", "Refrigerio", new BigDecimal("50.00")),
                new RonaldmichaConceptoExtra(RonaldmichaTipoConceptoExtra.DES, "0705", "Préstamo", new BigDecimal("20.00")),
                new RonaldmichaConceptoExtra(RonaldmichaTipoConceptoExtra.APO, null, "Seguro oncológico", new BigDecimal("10.00")));

        RonaldmichaResultadoPlanilla r = RonaldmichaPlanillaCalculator.calcular(t, OCTUBRE, P, n, OCHO_HORAS, extras);

        assertThat(r.remuneracionAfecta()).isEqualByComparingTo("2250.00");
        assertThat(r.pension().aporte()).isEqualByComparingTo("225.00");
        assertThat(r.pension().prima()).isEqualByComparingTo("30.83");
        assertThat(r.pension().comision()).isEqualByComparingTo("0");
        assertThat(r.essalud()).isEqualByComparingTo("151.88");
        assertThat(r.vidaLey()).isEqualByComparingTo("34.43");
        assertThat(r.totalIngresos()).isEqualByComparingTo("2300.00");
        assertThat(r.totalDescuentos()).isEqualByComparingTo("475.83");
        assertThat(r.netoAPagar()).isEqualByComparingTo("1824.17");
        assertThat(r.totalAportes()).isEqualByComparingTo("196.31");
        assertThat(r.conceptos()).extracting(Concepto::codigoPlame, Concepto::tipo)
                .contains(tuple("0103", TipoConcepto.INGRESO), tuple("0312", TipoConcepto.INGRESO),
                        tuple("0903", TipoConcepto.INGRESO), tuple("0701", TipoConcepto.DESCUENTO),
                        tuple("0705", TipoConcepto.DESCUENTO), tuple(null, TipoConcepto.APORTE))
                .doesNotContain(tuple("0601", TipoConcepto.DESCUENTO));
        assertThat(r.totalDe(TipoConcepto.DESCUENTO)).isEqualByComparingTo(r.totalDescuentos());
        assertThat(r.totalDe(TipoConcepto.APORTE)).isEqualByComparingTo(r.totalAportes());
    }

    @Test
    void microempresaConSisSinGratificacionNiCts() {
        RonaldmichaTrabajador t = conSalud(conRegimen(trabajador("1230.00", RonaldmichaSistemaPension.ONP, null, false),
                RonaldmichaRegimenLaboral.MICRO), RonaldmichaSalud.SIS, false);

        RonaldmichaResultadoPlanilla r = calcular(t, Novedades.NINGUNA);

        assertThat(r.essalud()).isEqualByComparingTo("0");
        assertThat(r.sis()).isEqualByComparingTo("15.00");
        assertThat(r.provisionGratificacion()).isEqualByComparingTo("0");
        assertThat(r.provisionCts()).isEqualByComparingTo("0");
        assertThat(r.provisionVacaciones()).isEqualByComparingTo("51.25");
    }

    private static RonaldmichaResultadoPlanilla calcular(RonaldmichaTrabajador t, Novedades n) {
        return RonaldmichaPlanillaCalculator.calcular(t, OCTUBRE, P, n, OCHO_HORAS, List.of());
    }

    private static Novedades novedades(int faltas, int vacaciones, int subsidiados) {
        return new Novedades(faltas, vacaciones, subsidiados, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO);
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

    private static RonaldmichaTrabajador conMovilidad(RonaldmichaTrabajador t, String movilidad) {
        return new RonaldmichaTrabajador(t.id(), t.codigo(), t.tipoDoc(), t.numDoc(), t.apPaterno(), t.apMaterno(), t.nombres(),
                null, null, t.fechaIngreso(), t.fechaCese(), t.area(), t.cargo(), t.tipoContrato(), t.regimen(), null,
                null, t.sueldo(), new BigDecimal(movilidad), t.salud(), t.sistemaPension(), null, t.tipoComision(),
                t.asigFamiliar(), t.vidaLey(), t.tipoPago(), null, null, null, null);
    }

    private static RonaldmichaTrabajador conSalud(RonaldmichaTrabajador t, RonaldmichaSalud salud, boolean vidaLey) {
        return new RonaldmichaTrabajador(t.id(), t.codigo(), t.tipoDoc(), t.numDoc(), t.apPaterno(), t.apMaterno(), t.nombres(),
                null, null, t.fechaIngreso(), t.fechaCese(), t.area(), t.cargo(), t.tipoContrato(), t.regimen(), null,
                null, t.sueldo(), t.movilidad(), salud, t.sistemaPension(), null, t.tipoComision(),
                t.asigFamiliar(), vidaLey, t.tipoPago(), null, null, null, null);
    }

    private static RonaldmichaTrabajador conRegimen(RonaldmichaTrabajador t, RonaldmichaRegimenLaboral regimen) {
        return new RonaldmichaTrabajador(t.id(), t.codigo(), t.tipoDoc(), t.numDoc(), t.apPaterno(), t.apMaterno(), t.nombres(),
                null, null, t.fechaIngreso(), t.fechaCese(), t.area(), t.cargo(), t.tipoContrato(), regimen, null,
                null, t.sueldo(), t.movilidad(), t.salud(), t.sistemaPension(), null, t.tipoComision(),
                t.asigFamiliar(), t.vidaLey(), t.tipoPago(), null, null, null, null);
    }
}
