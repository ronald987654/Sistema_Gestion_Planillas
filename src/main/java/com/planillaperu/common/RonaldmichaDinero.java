package com.planillaperu.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class RonaldmichaDinero {

    public static final int ESCALA = 2;
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    public static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA, REDONDEO);

    private RonaldmichaDinero() {
    }

    public static BigDecimal redondear(BigDecimal monto) {
        return monto == null ? CERO : monto.setScale(ESCALA, REDONDEO);
    }

    public static BigDecimal de(String valor) {
        return redondear(new BigDecimal(valor.trim()));
    }

    public static BigDecimal sumar(BigDecimal... montos) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal m : montos) {
            if (m != null) {
                total = total.add(m);
            }
        }
        return redondear(total);
    }

    public static BigDecimal porcentaje(BigDecimal monto, BigDecimal porcentaje) {
        return redondear(monto.multiply(porcentaje).divide(BigDecimal.valueOf(100)));
    }
}
