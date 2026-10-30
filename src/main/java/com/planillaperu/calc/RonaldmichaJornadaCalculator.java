package com.planillaperu.calc;

import com.planillaperu.domain.RonaldmichaHorario;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

public final class RonaldmichaJornadaCalculator {

    public static final BigDecimal HORAS_TIEMPO_PARCIAL = BigDecimal.valueOf(4);

    public record Jornada(BigDecimal horasDiarias, BigDecimal horasSemanales, int dias) {

        public boolean tiempoParcial() {
            return horasDiarias.compareTo(HORAS_TIEMPO_PARCIAL) < 0;
        }
    }

    private RonaldmichaJornadaCalculator() {
    }

    public static Jornada calcular(RonaldmichaHorario h) {
        long minutos = Duration.between(h.horaIngreso(), h.horaSalida()).toMinutes();
        if (minutos <= 0) {
            minutos += 24 * 60;
        }
        minutos = Math.max(0, minutos - h.refrigerioMin());
        BigDecimal diarias = BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        int dias = h.diasLaborables().size();
        return new Jornada(diarias, diarias.multiply(BigDecimal.valueOf(dias)).setScale(2, RoundingMode.HALF_UP), dias);
    }
}
