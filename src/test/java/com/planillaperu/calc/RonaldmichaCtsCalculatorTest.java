package com.planillaperu.calc;

import com.planillaperu.calc.RonaldmichaCtsCalculator.Resultado;
import com.planillaperu.calc.RonaldmichaCtsCalculator.Tiempo;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaCtsCalculatorTest {

    private static final List<YearMonth> PERIODO = List.of(YearMonth.of(2025, 11), YearMonth.of(2025, 12),
            YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3), YearMonth.of(2026, 4));
    private static final List<BigDecimal> SIN_VARIABLE = Collections.nCopies(6, BigDecimal.ZERO);

    @Test
    void periodoCompletoConSextoDeGratificacion() {
        Resultado r = RonaldmichaCtsCalculator.calcular(new BigDecimal("2000"), new BigDecimal("113"), SIN_VARIABLE,
                new Tiempo(6, 0), 0, new BigDecimal("2113.00"), RonaldmichaRegimenLaboral.GENERAL);

        assertThat(r.sextoGratificacion()).isEqualByComparingTo("352.17");
        assertThat(r.computable()).isEqualByComparingTo("2465.17");
        assertThat(r.cts()).isEqualByComparingTo("1232.59");
    }

    @Test
    void mesesDiasYFaltas() {
        Resultado r = RonaldmichaCtsCalculator.calcular(new BigDecimal("3600"), BigDecimal.ZERO, SIN_VARIABLE,
                new Tiempo(3, 15), 2, BigDecimal.ZERO, RonaldmichaRegimenLaboral.GENERAL);

        assertThat(r.cts()).isEqualByComparingTo("1030.00");
    }

    @Test
    void tiempoComputable() {
        assertThat(RonaldmichaCtsCalculator.tiempo(LocalDate.of(2020, 1, 1), null, PERIODO)).isEqualTo(new Tiempo(6, 0));
        assertThat(RonaldmichaCtsCalculator.tiempo(LocalDate.of(2026, 1, 16), null, PERIODO)).isEqualTo(new Tiempo(3, 15));
        assertThat(RonaldmichaCtsCalculator.tiempo(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 3, 10), PERIODO))
                .isEqualTo(new Tiempo(4, 10));
        assertThat(RonaldmichaCtsCalculator.tiempo(LocalDate.of(2026, 3, 11), LocalDate.of(2026, 4, 25),
                List.of(YearMonth.of(2026, 3), YearMonth.of(2026, 4)))).isEqualTo(new Tiempo(1, 15));
    }

    @Test
    void regimenEspecial() {
        Resultado pequena = RonaldmichaCtsCalculator.calcular(new BigDecimal("2400"), BigDecimal.ZERO, SIN_VARIABLE,
                new Tiempo(6, 0), 0, BigDecimal.ZERO, RonaldmichaRegimenLaboral.PEQUENA);
        Resultado micro = RonaldmichaCtsCalculator.calcular(new BigDecimal("2400"), BigDecimal.ZERO, SIN_VARIABLE,
                new Tiempo(6, 0), 0, BigDecimal.ZERO, RonaldmichaRegimenLaboral.MICRO);

        assertThat(pequena.cts()).isEqualByComparingTo("600.00");
        assertThat(micro.cts()).isEqualByComparingTo("0.00");
    }

    @Test
    void variableConLaReglaDeTresMeses() {
        List<BigDecimal> variables = List.of(new BigDecimal("120"), new BigDecimal("120"), new BigDecimal("120"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        Resultado r = RonaldmichaCtsCalculator.calcular(new BigDecimal("2400"), BigDecimal.ZERO, variables, new Tiempo(6, 0), 0,
                BigDecimal.ZERO, RonaldmichaRegimenLaboral.GENERAL);

        assertThat(r.promedioVariable()).isEqualByComparingTo("60.00");
        assertThat(r.cts()).isEqualByComparingTo("1230.00");
    }
}
