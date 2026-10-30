package com.planillaperu.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaFormatosTest {

    @Test
    void monedaEnFormatoPeruano() {
        assertThat(RonaldmichaFormatos.moneda(new BigDecimal("1234.56"))).isEqualTo("S/ 1,234.56");
        assertThat(RonaldmichaFormatos.moneda(new BigDecimal("1234567.5"))).isEqualTo("S/ 1,234,567.50");
        assertThat(RonaldmichaFormatos.moneda(BigDecimal.ZERO)).isEqualTo("S/ 0.00");
        assertThat(RonaldmichaFormatos.moneda(new BigDecimal("-10"))).isEqualTo("-S/ 10.00");
        assertThat(RonaldmichaFormatos.moneda(new BigDecimal("0.005"))).isEqualTo("S/ 0.01");
    }

    @Test
    void fechas() {
        assertThat(RonaldmichaFormatos.fecha(LocalDate.of(2026, 3, 7))).isEqualTo("07/03/2026");
        assertThat(RonaldmichaFormatos.fechaHora(LocalDateTime.of(2026, 3, 7, 9, 5))).isEqualTo("07/03/2026 09:05");
        assertThat(RonaldmichaFormatos.leerFecha("31/12/2026")).isEqualTo(LocalDate.of(2026, 12, 31));
    }
}
