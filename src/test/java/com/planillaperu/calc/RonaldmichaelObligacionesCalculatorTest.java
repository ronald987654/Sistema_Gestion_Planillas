package com.planillaperu.calc;

import com.planillaperu.calc.RonaldmichaelObligacionesCalculator.Obligacion;
import com.planillaperu.calc.RonaldmichaelObligacionesCalculator.Tipo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaelObligacionesCalculatorTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 15);
    private static final Map<Integer, Map<LocalDate, String>> FERIADOS = Map.of(
            2026, RonaldmichaelFeriadosPeru.nacionales(2026), 2027, RonaldmichaelFeriadosPeru.nacionales(2027));

    @Test
    void plameSegunElUltimoDigitoDelRuc() {
        assertThat(RonaldmichaelObligacionesCalculator.vencimientoPlame(YearMonth.of(2026, 10), 0, FERIADOS))
                .isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(RonaldmichaelObligacionesCalculator.vencimientoPlame(YearMonth.of(2026, 10), 3, FERIADOS))
                .isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(RonaldmichaelObligacionesCalculator.vencimientoPlame(YearMonth.of(2026, 10), 5, FERIADOS))
                .isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(RonaldmichaelObligacionesCalculator.vencimientoPlame(YearMonth.of(2026, 10), 9, FERIADOS))
                .isEqualTo(LocalDate.of(2026, 10, 21));
    }

    @Test
    void proximasObligacionesOrdenadas() {
        List<Obligacion> lista = RonaldmichaelObligacionesCalculator.proximas(HOY, "20600000005", FERIADOS, 8);

        assertThat(lista).hasSize(8);
        assertThat(lista.getFirst().tipo()).isEqualTo(Tipo.PLAME);
        assertThat(lista.getFirst().fecha()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(lista).extracting(Obligacion::fecha).isSorted();
        assertThat(lista).anyMatch(o -> o.tipo() == Tipo.AFPNET && o.desde().equals(LocalDate.of(2026, 11, 1))
                && o.fecha().equals(LocalDate.of(2026, 11, 5)) && o.detalle().contains("octubre 2026"));
        assertThat(lista).anyMatch(o -> o.tipo() == Tipo.CTS && o.fecha().equals(LocalDate.of(2026, 11, 15)));
        assertThat(lista).anyMatch(o -> o.tipo() == Tipo.GRATIFICACION && o.fecha().equals(LocalDate.of(2026, 12, 15)));
        assertThat(lista).noneMatch(o -> o.fecha().isBefore(HOY));
        assertThat(lista.getFirst().diasRestantes(HOY)).isEqualTo(4);
    }

    @Test
    void sinRucNoHayPlame() {
        assertThat(RonaldmichaelObligacionesCalculator.proximas(HOY, null, FERIADOS, 20)).noneMatch(o -> o.tipo() == Tipo.PLAME)
                .anyMatch(o -> o.tipo() == Tipo.AFPNET);
    }
}
