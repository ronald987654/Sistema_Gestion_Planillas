package com.planillaperu.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaDineroTest {

    @Test
    void redondeaHalfUpADosDecimales() {
        assertThat(RonaldmichaDinero.redondear(new BigDecimal("10.005"))).isEqualByComparingTo("10.01");
        assertThat(RonaldmichaDinero.redondear(new BigDecimal("10.004"))).isEqualByComparingTo("10.00");
        assertThat(RonaldmichaDinero.redondear(new BigDecimal("-10.005"))).isEqualByComparingTo("-10.01");
        assertThat(RonaldmichaDinero.redondear(new BigDecimal("7")).scale()).isEqualTo(2);
    }

    @Test
    void nuloEsCero() {
        assertThat(RonaldmichaDinero.redondear(null)).isEqualTo(RonaldmichaDinero.CERO);
    }

    @Test
    void sumaIgnorandoNulos() {
        assertThat(RonaldmichaDinero.sumar(new BigDecimal("0.10"), null, new BigDecimal("0.20")))
                .isEqualTo(new BigDecimal("0.30"));
    }

    @Test
    void porcentaje() {
        assertThat(RonaldmichaDinero.porcentaje(new BigDecimal("1025.00"), new BigDecimal("13")))
                .isEqualTo(new BigDecimal("133.25"));
        assertThat(RonaldmichaDinero.porcentaje(new BigDecimal("333.33"), new BigDecimal("1.5")))
                .isEqualTo(new BigDecimal("5.00"));
    }
}
