package com.planillaperu.calc;

import com.planillaperu.calc.RonaldmichaJornadaCalculator.Jornada;
import com.planillaperu.domain.RonaldmichaHorario;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaJornadaCalculatorTest {

    @Test
    void jornadaCompletaDeLunesAViernes() {
        Jornada j = RonaldmichaJornadaCalculator.calcular(horario("08:00", "17:00", 60, "1,2,3,4,5"));

        assertThat(j.horasDiarias()).isEqualByComparingTo("8");
        assertThat(j.horasSemanales()).isEqualByComparingTo("40");
        assertThat(j.dias()).isEqualTo(5);
        assertThat(j.tiempoParcial()).isFalse();
    }

    @Test
    void turnoQueCruzaLaMedianoche() {
        assertThat(RonaldmichaJornadaCalculator.calcular(horario("22:00", "06:00", 0, "1,2,3,4,5,6")).horasDiarias())
                .isEqualByComparingTo("8");
    }

    @Test
    void tiempoParcialEsMenosDeCuatroHorasDiarias() {
        assertThat(RonaldmichaJornadaCalculator.calcular(horario("08:00", "11:30", 0, "1,2,3")).tiempoParcial()).isTrue();
        assertThat(RonaldmichaJornadaCalculator.calcular(horario("08:00", "12:00", 0, "1,2,3")).tiempoParcial()).isFalse();
    }

    private static RonaldmichaHorario horario(String ingreso, String salida, int refrigerio, String dias) {
        return new RonaldmichaHorario(LocalTime.parse(ingreso), LocalTime.parse(salida), refrigerio, 0, RonaldmichaHorario.leerDias(dias), false);
    }
}
