package com.planillaperu.calc;

import com.planillaperu.calc.RonaldmichaHorasExtraCalculator.Importes;
import com.planillaperu.calc.RonaldmichaHorasExtraCalculator.Tramos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaHorasExtraCalculatorTest {

    @Test
    void lasDosPrimerasHorasAl25YElRestoAl35() {
        assertThat(RonaldmichaHorasExtraCalculator.dividir(90)).isEqualTo(new Tramos(90, 0));
        assertThat(RonaldmichaHorasExtraCalculator.dividir(120)).isEqualTo(new Tramos(120, 0));
        assertThat(RonaldmichaHorasExtraCalculator.dividir(210)).isEqualTo(new Tramos(120, 90));
        assertThat(RonaldmichaHorasExtraCalculator.dividir(0)).isEqualTo(new Tramos(0, 0));
        assertThat(RonaldmichaHorasExtraCalculator.dividir(-5)).isEqualTo(new Tramos(0, 0));
    }

    @Test
    void importesConValorHoraDeLaJornada() {
        Importes i = RonaldmichaHorasExtraCalculator.calcular(new BigDecimal("3000.00"), new BigDecimal("8"), 120, 30, 1);

        assertThat(i.remuneracionDiaria()).isEqualByComparingTo("100.00");
        assertThat(i.valorHora()).isEqualByComparingTo("12.50");
        assertThat(i.he25()).isEqualByComparingTo("31.25");
        assertThat(i.he35()).isEqualByComparingTo("8.44");
        assertThat(i.descansos()).isEqualByComparingTo("200.00");
        assertThat(i.total()).isEqualByComparingTo("239.69");
        assertThat(i.he35().scale()).isEqualTo(2);
    }

    @Test
    void sinHorasExtraLosImportesSonCero() {
        Importes i = RonaldmichaHorasExtraCalculator.calcular(new BigDecimal("1130.00"), new BigDecimal("7.25"), 0, 0, 0);

        assertThat(i.total()).isEqualByComparingTo("0.00");
        assertThat(i.valorHora()).isEqualByComparingTo("5.1954");
    }
}
