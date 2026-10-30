package com.planillaperu.calc;

import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public final class RonaldmichaGratificacionCalculator {

    public static final int MESES_MINIMOS_VARIABLE = 3;
    private static final int DIAS_COMERCIALES = 30;
    private static final BigDecimal SEIS = BigDecimal.valueOf(6);
    private static final BigDecimal TREINTA = BigDecimal.valueOf(DIAS_COMERCIALES);
    private static final int ESCALA_INTERMEDIA = 6;

    public record Resultado(
            int meses,
            int faltas,
            BigDecimal basico,
            BigDecimal asignacionFamiliar,
            BigDecimal promedioVariable,
            BigDecimal computable,
            BigDecimal gratificacion,
            BigDecimal bonificacion,
            BigDecimal total) {
    }

    private RonaldmichaGratificacionCalculator() {
    }

    public static Resultado calcular(BigDecimal basico, BigDecimal asignacionFamiliar, List<BigDecimal> variables,
                                     int meses, int faltas, RonaldmichaRegimenLaboral regimen, BigDecimal bonificacionPct) {
        BigDecimal promedio = promedioVariable(variables);
        BigDecimal computable = RonaldmichaDinero.sumar(basico, asignacionFamiliar, promedio);
        BigDecimal porMeses = RonaldmichaDinero.redondear(computable.multiply(BigDecimal.valueOf(meses))
                .divide(SEIS, ESCALA_INTERMEDIA, RoundingMode.HALF_UP));
        BigDecimal porFaltas = RonaldmichaDinero.redondear(computable.multiply(BigDecimal.valueOf(Math.max(0, faltas)))
                .divide(SEIS.multiply(TREINTA), ESCALA_INTERMEDIA, RoundingMode.HALF_UP));
        BigDecimal bruto = porMeses.subtract(porFaltas).max(BigDecimal.ZERO);
        BigDecimal gratificacion = RonaldmichaDinero.redondear(bruto.multiply(regimen.factorGratificacion()));
        BigDecimal bonificacion = RonaldmichaDinero.porcentaje(gratificacion, bonificacionPct);
        return new Resultado(meses, faltas, RonaldmichaDinero.redondear(basico), RonaldmichaDinero.redondear(asignacionFamiliar), promedio,
                computable, gratificacion, bonificacion, RonaldmichaDinero.sumar(gratificacion, bonificacion));
    }

    public static int mesesCompletos(LocalDate ingreso, LocalDate cese, List<YearMonth> meses) {
        int completos = 0;
        for (YearMonth mes : meses) {
            if (RonaldmichaPlanillaCalculator.diasDelMes(ingreso, cese, mes) == DIAS_COMERCIALES) {
                completos++;
            }
        }
        return completos;
    }

    public static BigDecimal promedioVariable(List<BigDecimal> variables) {
        long mesesConVariable = variables.stream().filter(v -> v != null && v.signum() > 0).count();
        if (mesesConVariable < MESES_MINIMOS_VARIABLE) {
            return RonaldmichaDinero.CERO;
        }
        BigDecimal suma = variables.stream().filter(v -> v != null && v.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return RonaldmichaDinero.redondear(suma.divide(SEIS, ESCALA_INTERMEDIA, RoundingMode.HALF_UP));
    }
}
