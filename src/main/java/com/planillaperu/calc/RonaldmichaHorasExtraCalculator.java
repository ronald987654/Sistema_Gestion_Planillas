package com.planillaperu.calc;

import com.planillaperu.common.RonaldmichaDinero;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class RonaldmichaHorasExtraCalculator {

    public static final int MINUTOS_AL_25 = 120;
    private static final BigDecimal FACTOR_25 = new BigDecimal("1.25");
    private static final BigDecimal FACTOR_35 = new BigDecimal("1.35");
    private static final BigDecimal FACTOR_DESCANSO = BigDecimal.valueOf(2);
    private static final BigDecimal DIAS_MES = BigDecimal.valueOf(30);
    private static final BigDecimal SESENTA = BigDecimal.valueOf(60);
    private static final int ESCALA_INTERMEDIA = 6;

    public record Tramos(int al25, int al35) {
    }

    public record Importes(
            BigDecimal remuneracionDiaria,
            BigDecimal valorHora,
            BigDecimal he25,
            BigDecimal he35,
            BigDecimal descansos,
            BigDecimal total) {
    }

    private RonaldmichaHorasExtraCalculator() {
    }

    public static Tramos dividir(int minutos) {
        int positivos = Math.max(0, minutos);
        int al25 = Math.min(MINUTOS_AL_25, positivos);
        return new Tramos(al25, positivos - al25);
    }

    public static Importes calcular(BigDecimal remuneracionMensual, BigDecimal horasDiarias,
                                    int minutosAl25, int minutosAl35, int diasConSobretasa) {
        BigDecimal diaria = remuneracionMensual.divide(DIAS_MES, ESCALA_INTERMEDIA, RoundingMode.HALF_UP);
        BigDecimal valorHora = valorHoraExacto(remuneracionMensual, horasDiarias);
        BigDecimal he25 = RonaldmichaDinero.redondear(importe(valorHora, FACTOR_25, minutosAl25));
        BigDecimal he35 = RonaldmichaDinero.redondear(importe(valorHora, FACTOR_35, minutosAl35));
        BigDecimal descansos = RonaldmichaDinero.redondear(diaria.multiply(FACTOR_DESCANSO).multiply(BigDecimal.valueOf(diasConSobretasa)));
        return new Importes(RonaldmichaDinero.redondear(diaria), valorHora(remuneracionMensual, horasDiarias), he25, he35,
                descansos, RonaldmichaDinero.sumar(he25, he35, descansos));
    }

    public static BigDecimal valorHora(BigDecimal remuneracionMensual, BigDecimal horasDiarias) {
        return valorHoraExacto(remuneracionMensual, horasDiarias).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal valorHoraExacto(BigDecimal remuneracionMensual, BigDecimal horasDiarias) {
        if (horasDiarias == null || horasDiarias.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return remuneracionMensual.divide(DIAS_MES, ESCALA_INTERMEDIA, RoundingMode.HALF_UP)
                .divide(horasDiarias, ESCALA_INTERMEDIA, RoundingMode.HALF_UP);
    }

    private static BigDecimal importe(BigDecimal valorHora, BigDecimal factor, int minutos) {
        return valorHora.multiply(factor).multiply(BigDecimal.valueOf(minutos))
                .divide(SESENTA, ESCALA_INTERMEDIA, RoundingMode.HALF_UP);
    }
}
