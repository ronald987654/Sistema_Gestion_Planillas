package com.planillaperu.domain;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public record RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio tipo, int anio, int semestre) {

    private static final int DIA_PAGO = 15;

    public RonaldmichaSemestreBeneficio {
        if (semestre != 1 && semestre != 2) {
            throw new IllegalArgumentException("El semestre debe ser 1 o 2: " + semestre);
        }
    }

    public static RonaldmichaSemestreBeneficio vigenteEn(RonaldmichaTipoBeneficio tipo, YearMonth mes) {
        int m = mes.getMonthValue();
        if (tipo == RonaldmichaTipoBeneficio.GRATIFICACION) {
            return new RonaldmichaSemestreBeneficio(tipo, mes.getYear(), m <= 7 ? 1 : 2);
        }
        if (m <= 5) {
            return new RonaldmichaSemestreBeneficio(tipo, mes.getYear(), 1);
        }
        return m <= 11 ? new RonaldmichaSemestreBeneficio(tipo, mes.getYear(), 2)
                : new RonaldmichaSemestreBeneficio(tipo, mes.getYear() + 1, 1);
    }

    public YearMonth desde() {
        if (tipo == RonaldmichaTipoBeneficio.GRATIFICACION) {
            return YearMonth.of(anio, semestre == 1 ? 1 : 7);
        }
        return semestre == 1 ? YearMonth.of(anio - 1, 11) : YearMonth.of(anio, 5);
    }

    public YearMonth hasta() {
        return desde().plusMonths(5);
    }

    public List<YearMonth> meses() {
        List<YearMonth> lista = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            lista.add(desde().plusMonths(i));
        }
        return lista;
    }

    public Month mesPago() {
        if (tipo == RonaldmichaTipoBeneficio.GRATIFICACION) {
            return semestre == 1 ? Month.JULY : Month.DECEMBER;
        }
        return semestre == 1 ? Month.MAY : Month.NOVEMBER;
    }

    public LocalDate fechaPago() {
        return LocalDate.of(anio, mesPago(), DIA_PAGO);
    }

    public RonaldmichaSemestreBeneficio gratificacionDelSexto() {
        return semestre == 1 ? new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, anio - 1, 2)
                : new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, anio, 1);
    }

    public RonaldmichaSemestreBeneficio anterior() {
        return semestre == 2 ? new RonaldmichaSemestreBeneficio(tipo, anio, 1) : new RonaldmichaSemestreBeneficio(tipo, anio - 1, 2);
    }

    public RonaldmichaSemestreBeneficio siguiente() {
        return semestre == 1 ? new RonaldmichaSemestreBeneficio(tipo, anio, 2) : new RonaldmichaSemestreBeneficio(tipo, anio + 1, 1);
    }
}
