package com.planillaperu.calc;

import com.planillaperu.calc.RonaldmichaAportePensionCalculator.AportePension;
import com.planillaperu.domain.RonaldmichaAfpComision;
import com.planillaperu.domain.RonaldmichaEstadoParametros;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaSistemaPension;
import com.planillaperu.domain.RonaldmichaTipoComision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaAportePensionCalculatorTest {

    private static final RonaldmichaParametrosVersion P = new RonaldmichaParametrosVersion(1L, YearMonth.of(2026, 10), null,
            new BigDecimal("1230.00"), new BigDecimal("5500.00"), new BigDecimal("10"), new BigDecimal("9"),
            new BigDecimal("6.75"), new BigDecimal("13"), new BigDecimal("10"), new BigDecimal("1.37"),
            new BigDecimal("12732.70"), new BigDecimal("9"), new BigDecimal("6.75"), new BigDecimal("1.53"),
            new BigDecimal("15.00"), RonaldmichaEstadoParametros.VIGENTE, "prueba", null,
            List.of(new RonaldmichaAfpComision("AFP Habitat", new BigDecimal("1.47"), new BigDecimal("1.25")),
                    new RonaldmichaAfpComision("AFP Integra", new BigDecimal("1.55"), new BigDecimal("0.78")),
                    new RonaldmichaAfpComision("AFP Prima", new BigDecimal("1.60"), new BigDecimal("1.25")),
                    new RonaldmichaAfpComision("AFP Profuturo", new BigDecimal("1.69"), new BigDecimal("0.68"))),
            null, null, null);

    @Test
    void onpAportaSobreBasicoMasAsignacionFamiliar() {
        AportePension a = RonaldmichaAportePensionCalculator.calcular(P, RonaldmichaSistemaPension.ONP, null, new BigDecimal("3500.00"), true);

        assertThat(a.asignacionFamiliar()).isEqualByComparingTo("123.00");
        assertThat(a.remuneracion()).isEqualByComparingTo("3623.00");
        assertThat(a.aporte()).isEqualByComparingTo("470.99");
        assertThat(a.prima()).isEqualByComparingTo("0");
        assertThat(a.comision()).isEqualByComparingTo("0");
        assertThat(a.total()).isEqualByComparingTo("470.99");
    }

    @Test
    void afpFlujoSumaAportePrimaYComision() {
        AportePension a = RonaldmichaAportePensionCalculator.calcular(P, RonaldmichaSistemaPension.INTEGRA, RonaldmichaTipoComision.FLUJO,
                new BigDecimal("2800.00"), false);

        assertThat(a.aporte()).isEqualByComparingTo("280.00");
        assertThat(a.prima()).isEqualByComparingTo("38.36");
        assertThat(a.comision()).isEqualByComparingTo("43.40");
        assertThat(a.total()).isEqualByComparingTo("361.76");
        assertThat(a.primaTopada()).isFalse();
    }

    @Test
    void afpMixtaNoDescuentaComisionDeLaRemuneracion() {
        AportePension a = RonaldmichaAportePensionCalculator.calcular(P, RonaldmichaSistemaPension.PRIMA, RonaldmichaTipoComision.MIXTA,
                new BigDecimal("5200.00"), true);

        assertThat(a.remuneracion()).isEqualByComparingTo("5323.00");
        assertThat(a.aporte()).isEqualByComparingTo("532.30");
        assertThat(a.prima()).isEqualByComparingTo("72.93");
        assertThat(a.tasaComision()).isEqualByComparingTo("1.25");
        assertThat(a.comision()).isEqualByComparingTo("0");
        assertThat(a.total()).isEqualByComparingTo("605.23");
    }

    @Test
    void laPrimaTieneTopeDeRemuneracionMaximaAsegurable() {
        AportePension a = RonaldmichaAportePensionCalculator.calcular(P, RonaldmichaSistemaPension.INTEGRA, RonaldmichaTipoComision.FLUJO,
                new BigDecimal("15000.00"), false);

        assertThat(a.basePrima()).isEqualByComparingTo("12732.70");
        assertThat(a.prima()).isEqualByComparingTo("174.44");
        assertThat(a.aporte()).isEqualByComparingTo("1500.00");
        assertThat(a.comision()).isEqualByComparingTo("232.50");
        assertThat(a.primaTopada()).isTrue();
    }
}
