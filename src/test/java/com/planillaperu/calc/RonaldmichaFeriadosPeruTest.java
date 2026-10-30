package com.planillaperu.calc;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaFeriadosPeruTest {

    @Test
    void pascuaPorComputus() {
        assertThat(RonaldmichaFeriadosPeru.pascua(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
        assertThat(RonaldmichaFeriadosPeru.pascua(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
        assertThat(RonaldmichaFeriadosPeru.pascua(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(RonaldmichaFeriadosPeru.pascua(2019)).isEqualTo(LocalDate.of(2019, 4, 21));
    }

    @Test
    void nacionalesDelAnioConSemanaSanta() {
        Map<LocalDate, String> f = RonaldmichaFeriadosPeru.nacionales(2026);

        assertThat(f).hasSize(16);
        assertThat(f).containsEntry(LocalDate.of(2026, 4, 2), "Jueves Santo")
                .containsEntry(LocalDate.of(2026, 4, 3), "Viernes Santo")
                .containsEntry(LocalDate.of(2026, 1, 1), "Año Nuevo")
                .containsEntry(LocalDate.of(2026, 10, 8), "Combate de Angamos")
                .containsKeys(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 7), LocalDate.of(2026, 6, 29),
                        LocalDate.of(2026, 7, 23), LocalDate.of(2026, 7, 28), LocalDate.of(2026, 7, 29),
                        LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 30), LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 9), LocalDate.of(2026, 12, 25));
    }

    @Test
    void delMesIncluyeLosAgregadosSinPisarLosNacionales() {
        Map<LocalDate, String> extras = Map.of(
                LocalDate.of(2026, 10, 9), "Día no laborable",
                LocalDate.of(2026, 10, 8), "Otro nombre",
                LocalDate.of(2026, 11, 2), "Fuera del mes");

        Map<LocalDate, String> octubre = RonaldmichaFeriadosPeru.delMes(YearMonth.of(2026, 10), extras);

        assertThat(octubre).containsExactly(
                Map.entry(LocalDate.of(2026, 10, 8), "Combate de Angamos"),
                Map.entry(LocalDate.of(2026, 10, 9), "Día no laborable"));
    }
}
