package com.planillaperu.calc;

import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public final class RonaldmichaCtsCalculator {

    private static final int DIAS_COMERCIALES = 30;
    private static final BigDecimal SEIS = BigDecimal.valueOf(6);
    private static final BigDecimal DOCE = BigDecimal.valueOf(12);
    private static final BigDecimal TRESCIENTOS_SESENTA = BigDecimal.valueOf(360);
    private static final int ESCALA_INTERMEDIA = 6;

    public record Tiempo(int meses, int dias) {
    }

    public record Resultado(
            int meses,
            int dias,
            int faltas,
            BigDecimal basico,
            BigDecimal asignacionFamiliar,
            BigDecimal promedioVariable,
            BigDecimal gratificacion,
            BigDecimal sextoGratificacion,
            BigDecimal computable,
            BigDecimal cts) {
    }

    private RonaldmichaCtsCalculator() {
    }

    public static Tiempo tiempo(LocalDate ingreso, LocalDate cese, List<YearMonth> meses) {
        int completos = 0;
        int dias = 0;
        for (YearMonth mes : meses) {
            int d = RonaldmichaPlanillaCalculator.diasDelMes(ingreso, cese, mes);
            if (d == DIAS_COMERCIALES) {
                completos++;
            } else {
                dias += d;
            }
        }
        return new Tiempo(completos + dias / DIAS_COMERCIALES, dias % DIAS_COMERCIALES);
    }

    public static Resultado calcular(BigDecimal basico, BigDecimal asignacionFamiliar, List<BigDecimal> variables,
                                     Tiempo tiempo, int faltas, BigDecimal gratificacion, RonaldmichaRegimenLaboral regimen) {
        BigDecimal promedio = RonaldmichaGratificacionCalculator.promedioVariable(variables);
        BigDecimal sexto = RonaldmichaDinero.redondear(gratificacion.divide(SEIS, ESCALA_INTERMEDIA, RoundingMode.HALF_UP));
        BigDecimal computable = RonaldmichaDinero.sumar(basico, asignacionFamiliar, promedio, sexto);
        BigDecimal porMeses = RonaldmichaDinero.redondear(computable.multiply(BigDecimal.valueOf(tiempo.meses()))
                .divide(DOCE, ESCALA_INTERMEDIA, RoundingMode.HALF_UP));
        BigDecimal porDias = RonaldmichaDinero.redondear(computable.multiply(BigDecimal.valueOf(tiempo.dias()))
                .divide(TRESCIENTOS_SESENTA, ESCALA_INTERMEDIA, RoundingMode.HALF_UP));
        BigDecimal porFaltas = RonaldmichaDinero.redondear(computable.multiply(BigDecimal.valueOf(Math.max(0, faltas)))
                .divide(TRESCIENTOS_SESENTA, ESCALA_INTERMEDIA, RoundingMode.HALF_UP));
        BigDecimal bruto = porMeses.add(porDias).subtract(porFaltas).max(BigDecimal.ZERO);
        BigDecimal cts = RonaldmichaDinero.redondear(bruto.multiply(regimen.factorCts()));
        return new Resultado(tiempo.meses(), tiempo.dias(), faltas, RonaldmichaDinero.redondear(basico),
                RonaldmichaDinero.redondear(asignacionFamiliar), promedio, RonaldmichaDinero.redondear(gratificacion), sexto, computable, cts);
    }
}
