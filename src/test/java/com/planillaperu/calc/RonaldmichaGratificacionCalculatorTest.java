package com.planillaperu.calc;

import com.planillaperu.calc.RonaldmichaGratificacionCalculator.Resultado;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaGratificacionCalculatorTest {

    private static final List<YearMonth> SEMESTRE = List.of(YearMonth.of(2026, 1), YearMonth.of(2026, 2),
            YearMonth.of(2026, 3), YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6));
    private static final BigDecimal AF = new BigDecimal("113.00");
    private static final BigDecimal NUEVE = BigDecimal.valueOf(9);
    private static final List<BigDecimal> SIN_VARIABLE = Collections.nCopies(6, BigDecimal.ZERO);

    @Test
    void semestreCompletoSinVariableConAsignacionFamiliar() {
        Resultado r = RonaldmichaGratificacionCalculator.calcular(new BigDecimal("2000"), AF, SIN_VARIABLE, 6, 0,
                RonaldmichaRegimenLaboral.GENERAL, NUEVE);

        assertThat(r.computable()).isEqualByComparingTo("2113.00");
        assertThat(r.gratificacion()).isEqualByComparingTo("2113.00");
        assertThat(r.bonificacion()).isEqualByComparingTo("190.17");
        assertThat(r.total()).isEqualByComparingTo("2303.17");
    }

    @Test
    void pequenaEmpresaCobraLaMitadYMicroempresaNada() {
        Resultado pequena = RonaldmichaGratificacionCalculator.calcular(new BigDecimal("2000"), AF, SIN_VARIABLE, 6, 0,
                RonaldmichaRegimenLaboral.PEQUENA, NUEVE);
        Resultado micro = RonaldmichaGratificacionCalculator.calcular(new BigDecimal("2000"), AF, SIN_VARIABLE, 6, 0,
                RonaldmichaRegimenLaboral.MICRO, NUEVE);

        assertThat(pequena.gratificacion()).isEqualByComparingTo("1056.50");
        assertThat(pequena.bonificacion()).isEqualByComparingTo("95.09");
        assertThat(micro.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void bonificacionConEps() {
        Resultado r = RonaldmichaGratificacionCalculator.calcular(new BigDecimal("3000"), BigDecimal.ZERO, SIN_VARIABLE, 6, 0,
                RonaldmichaRegimenLaboral.GENERAL, new BigDecimal("6.75"));

        assertThat(r.bonificacion()).isEqualByComparingTo("202.50");
    }

    @Test
    void variableSoloSiSePercibioEnTresMeses() {
        List<BigDecimal> dosMeses = List.of(new BigDecimal("300"), BigDecimal.ZERO, new BigDecimal("150"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        List<BigDecimal> tresMeses = List.of(new BigDecimal("300"), BigDecimal.ZERO, new BigDecimal("150"),
                BigDecimal.ZERO, new BigDecimal("150"), BigDecimal.ZERO);

        assertThat(RonaldmichaGratificacionCalculator.promedioVariable(dosMeses)).isEqualByComparingTo("0.00");
        assertThat(RonaldmichaGratificacionCalculator.promedioVariable(tresMeses)).isEqualByComparingTo("100.00");

        Resultado r = RonaldmichaGratificacionCalculator.calcular(new BigDecimal("2000"), BigDecimal.ZERO, tresMeses, 6, 0,
                RonaldmichaRegimenLaboral.GENERAL, NUEVE);
        assertThat(r.gratificacion()).isEqualByComparingTo("2100.00");
    }

    @Test
    void mesesCalendarioCompletos() {
        assertThat(RonaldmichaGratificacionCalculator.mesesCompletos(LocalDate.of(2020, 1, 1), null, SEMESTRE)).isEqualTo(6);
        assertThat(RonaldmichaGratificacionCalculator.mesesCompletos(LocalDate.of(2026, 3, 2), null, SEMESTRE)).isEqualTo(3);
        assertThat(RonaldmichaGratificacionCalculator.mesesCompletos(LocalDate.of(2026, 3, 1), null, SEMESTRE)).isEqualTo(4);
        assertThat(RonaldmichaGratificacionCalculator.mesesCompletos(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 5, 20),
                SEMESTRE)).isEqualTo(4);
        assertThat(RonaldmichaGratificacionCalculator.mesesCompletos(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 5, 31),
                SEMESTRE)).isEqualTo(5);
    }

    @Test
    void mesesIncompletosYFaltas() {
        Resultado r = RonaldmichaGratificacionCalculator.calcular(new BigDecimal("3000"), BigDecimal.ZERO, SIN_VARIABLE, 4, 3,
                RonaldmichaRegimenLaboral.GENERAL, NUEVE);

        assertThat(r.gratificacion()).isEqualByComparingTo("1950.00");
        assertThat(r.bonificacion()).isEqualByComparingTo("175.50");
    }
}
