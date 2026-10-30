package com.planillaperu.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaHorasTest {

    @Test
    void horasEnVariosFormatos() {
        assertThat(RonaldmichaHoras.leer("08:00")).isEqualTo(LocalTime.of(8, 0));
        assertThat(RonaldmichaHoras.leer("8:00")).isEqualTo(LocalTime.of(8, 0));
        assertThat(RonaldmichaHoras.leer("8:00 a. m.")).isEqualTo(LocalTime.of(8, 0));
        assertThat(RonaldmichaHoras.leer("5:30 p. m.")).isEqualTo(LocalTime.of(17, 30));
        assertThat(RonaldmichaHoras.leer("12:15 AM")).isEqualTo(LocalTime.of(0, 15));
        assertThat(RonaldmichaHoras.leer("12:00 pm")).isEqualTo(LocalTime.of(12, 0));
        assertThat(RonaldmichaHoras.leer("17:45:00")).isEqualTo(LocalTime.of(17, 45));
        assertThat(RonaldmichaHoras.leer("0.75")).as("fracción de día de Excel").isEqualTo(LocalTime.of(18, 0));
        assertThat(RonaldmichaHoras.leer("0,354166667")).isEqualTo(LocalTime.of(8, 30));
        assertThat(RonaldmichaHoras.leer("  ")).isNull();
    }

    @Test
    void horasInvalidas() {
        assertThatThrownBy(() -> RonaldmichaHoras.leer("25:00")).hasMessageContaining("hora inválida «25:00»");
        assertThatThrownBy(() -> RonaldmichaHoras.leer("8")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RonaldmichaHoras.leer("13:00 pm")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RonaldmichaHoras.leer("ocho")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fechasComoTextoOSerieDeExcel() {
        LocalDate esperado = LocalDate.of(2026, 10, 5);
        assertThat(RonaldmichaHoras.leerFecha("2026-10-05")).isEqualTo(esperado);
        assertThat(RonaldmichaHoras.leerFecha("05/10/2026")).isEqualTo(esperado);
        assertThat(RonaldmichaHoras.leerFecha("5-10-2026")).isEqualTo(esperado);
        assertThat(RonaldmichaHoras.leerFecha("2026-10-05T00:00")).isEqualTo(esperado);
        assertThat(RonaldmichaHoras.leerFecha("45658")).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThatThrownBy(() -> RonaldmichaHoras.leerFecha("31/02/2026")).hasMessageContaining("fecha inválida");
    }

    @Test
    void duracionYHoras() {
        assertThat(RonaldmichaHoras.duracion(125)).isEqualTo("2:05");
        assertThat(RonaldmichaHoras.duracion(0)).isEqualTo("0:00");
        assertThat(RonaldmichaHoras.enHoras(90)).isEqualByComparingTo("1.50");
        assertThat(RonaldmichaHoras.texto(LocalTime.of(7, 5))).isEqualTo("07:05");
    }
}
