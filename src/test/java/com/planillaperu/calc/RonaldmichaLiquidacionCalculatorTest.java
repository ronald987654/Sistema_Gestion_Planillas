package com.planillaperu.calc;

import com.planillaperu.calc.RonaldmichaLiquidacionCalculator.Entrada;
import com.planillaperu.calc.RonaldmichaLiquidacionCalculator.Resultado;
import com.planillaperu.calc.RonaldmichaVacacionesCalculator.Tiempo;
import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.domain.RonaldmichaAfpComision;
import com.planillaperu.domain.RonaldmichaEstadoParametros;
import com.planillaperu.domain.RonaldmichaMotivoCese;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaSistemaPension;
import com.planillaperu.domain.RonaldmichaTipoComision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaLiquidacionCalculatorTest {

    private static final RonaldmichaParametrosVersion P = new RonaldmichaParametrosVersion(1L, YearMonth.of(2026, 10), null,
            new BigDecimal("1230.00"), new BigDecimal("5500.00"), new BigDecimal("10"), new BigDecimal("9"),
            new BigDecimal("6.75"), new BigDecimal("13"), new BigDecimal("10"), new BigDecimal("1.37"),
            new BigDecimal("12732.70"), new BigDecimal("9"), new BigDecimal("6.75"), new BigDecimal("1.53"),
            new BigDecimal("15.00"), RonaldmichaEstadoParametros.VIGENTE, "prueba", null,
            List.of(new RonaldmichaAfpComision("AFP Integra", new BigDecimal("1.55"), new BigDecimal("0.78"))),
            null, null, null);

    @Test
    void despidoArbitrarioEnElRegimenGeneral() {
        Resultado r = RonaldmichaLiquidacionCalculator.calcular(entrada(RonaldmichaMotivoCese.DESPIDO_ARBITRARIO, RonaldmichaRegimenLaboral.GENERAL,
                RonaldmichaSistemaPension.ONP, "100", "50"));

        assertThat(r.tiempoServicios()).isEqualTo(new Tiempo(7, 8, 0));
        assertThat(r.computable()).isEqualByComparingTo("3623.00");
        assertThat(r.vacacionesTruncas()).isEqualByComparingTo("2415.33");
        assertThat(r.vacacionesPendientes()).isEqualByComparingTo("1811.50");
        assertThat(r.subtotalVacaciones()).isEqualByComparingTo("4226.83");
        assertThat(r.gratificacionDesde()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(r.gratificacionMeses()).isEqualTo(4);
        assertThat(r.gratificacion()).isEqualByComparingTo("2415.33");
        assertThat(r.bonificacion()).isEqualByComparingTo("217.38");
        assertThat(r.ctsDesde()).isEqualTo(YearMonth.of(2026, 5));
        assertThat(r.ctsMeses()).isEqualTo(6);
        assertThat(r.sextoGratificacion()).isEqualByComparingTo("602.17");
        assertThat(r.cts()).isEqualByComparingTo("2112.59");
        assertThat(r.indemnizacion()).isEqualByComparingTo("41664.50");
        assertThat(r.pension().total()).isEqualByComparingTo("549.49");

        assertThat(r.bruto()).isEqualByComparingTo("50736.63");
        assertThat(r.descuentos()).isEqualByComparingTo("599.49");
        assertThat(r.neto()).isEqualByComparingTo("50137.14");
        assertThat(r.netoEnLetras()).isEqualTo("Cincuenta mil ciento treinta y siete con 14/100 soles");
        assertThat(r.subtotalOtros()).isEqualByComparingTo("41165.01");
    }

    @Test
    void renunciaNoTieneIndemnizacion() {
        Resultado r = RonaldmichaLiquidacionCalculator.calcular(entrada(RonaldmichaMotivoCese.RENUNCIA, RonaldmichaRegimenLaboral.GENERAL,
                RonaldmichaSistemaPension.ONP, "0", "0"));

        assertThat(r.indemnizable()).isFalse();
        assertThat(r.indemnizacion()).isEqualByComparingTo("0.00");
        assertThat(r.neto()).isEqualByComparingTo(
                RonaldmichaDinero.sumar(r.subtotalVacaciones(), r.subtotalGratificacion(), r.cts()).subtract(r.pension().total()));
    }

    @Test
    void indemnizacionConTopesPorRegimen() {
        BigDecimal rem = new BigDecimal("3623.00");
        assertThat(RonaldmichaLiquidacionCalculator.indemnizacion(rem, new Tiempo(15, 0, 0), RonaldmichaRegimenLaboral.GENERAL))
                .isEqualByComparingTo("43476.00");
        assertThat(RonaldmichaLiquidacionCalculator.indemnizacion(rem, new Tiempo(7, 8, 0), RonaldmichaRegimenLaboral.PEQUENA))
                .isEqualByComparingTo("14492.00");
        assertThat(RonaldmichaLiquidacionCalculator.indemnizacion(rem, new Tiempo(7, 8, 0), RonaldmichaRegimenLaboral.MICRO))
                .isEqualByComparingTo("9258.78");
        assertThat(RonaldmichaLiquidacionCalculator.indemnizacion(rem, new Tiempo(10, 0, 0), RonaldmichaRegimenLaboral.MICRO))
                .isEqualByComparingTo("10869.00");
    }

    @Test
    void mypeTieneVacacionesTruncasAlCincuentaPorCiento() {
        Resultado r = RonaldmichaLiquidacionCalculator.calcular(entrada(RonaldmichaMotivoCese.RENUNCIA, RonaldmichaRegimenLaboral.PEQUENA,
                RonaldmichaSistemaPension.ONP, "0", "0"));

        assertThat(r.vacacionesTruncas()).isEqualByComparingTo("1207.67");
        assertThat(r.gratificacion()).isEqualByComparingTo("1207.67");
    }

    @Test
    void afpDescuentaAportePrimaYComisionDeFlujo() {
        Resultado r = RonaldmichaLiquidacionCalculator.calcular(entrada(RonaldmichaMotivoCese.RENUNCIA, RonaldmichaRegimenLaboral.GENERAL,
                RonaldmichaSistemaPension.INTEGRA, "0", "0"));

        BigDecimal base = r.subtotalVacaciones();
        assertThat(r.pension().aporte()).isEqualByComparingTo(RonaldmichaDinero.porcentaje(base, new BigDecimal("10")));
        assertThat(r.pension().prima()).isEqualByComparingTo(RonaldmichaDinero.porcentaje(base, new BigDecimal("1.37")));
        assertThat(r.pension().comision()).isEqualByComparingTo(RonaldmichaDinero.porcentaje(base, new BigDecimal("1.55")));
    }

    @Test
    void periodosDeLosBeneficiosTruncos() {
        assertThat(RonaldmichaLiquidacionCalculator.inicioPeriodoCts(YearMonth.of(2026, 3))).isEqualTo(YearMonth.of(2025, 11));
        assertThat(RonaldmichaLiquidacionCalculator.inicioPeriodoCts(YearMonth.of(2026, 12))).isEqualTo(YearMonth.of(2026, 11));
        assertThat(RonaldmichaLiquidacionCalculator.inicioPeriodoCts(YearMonth.of(2026, 5))).isEqualTo(YearMonth.of(2026, 5));

        Entrada e = entrada(RonaldmichaMotivoCese.RENUNCIA, RonaldmichaRegimenLaboral.GENERAL, RonaldmichaSistemaPension.ONP, "0", "0");
        Resultado r = RonaldmichaLiquidacionCalculator.calcular(new Entrada(e.ingreso(), LocalDate.of(2026, 10, 15), e.motivo(),
                e.regimen(), e.basico(), e.asignacionFamiliar(), 0, 0, e.ultimaGratificacion(), 0,
                e.bonificacionPct(), P, RonaldmichaSistemaPension.ONP, null, null, null));
        assertThat(r.gratificacionMeses()).isEqualTo(3);
        assertThat(r.ctsMeses()).isEqualTo(5);
        assertThat(r.ctsDias()).isEqualTo(15);
    }

    private static Entrada entrada(RonaldmichaMotivoCese motivo, RonaldmichaRegimenLaboral regimen, RonaldmichaSistemaPension pension,
                                   String otrosIngresos, String otrosDescuentos) {
        return new Entrada(LocalDate.of(2019, 3, 1), LocalDate.of(2026, 10, 31), motivo, regimen,
                new BigDecimal("3500.00"), new BigDecimal("123.00"), 15, 0, new BigDecimal("3613.00"), 0,
                new BigDecimal("9"), P, pension, pension.esAfp() ? RonaldmichaTipoComision.FLUJO : null,
                new BigDecimal(otrosIngresos), new BigDecimal(otrosDescuentos));
    }
}
