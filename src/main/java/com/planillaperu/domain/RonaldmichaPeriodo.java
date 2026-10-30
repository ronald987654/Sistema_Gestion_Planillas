package com.planillaperu.domain;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public record RonaldmichaPeriodo(
        YearMonth periodo,
        RonaldmichaEstadoPeriodo estado,
        String cerradoPor,
        LocalDateTime fechaCierre,
        String motivoReapertura) {

    public static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyyMM");

    public boolean esSoloLectura() {
        return estado == RonaldmichaEstadoPeriodo.CERRADO;
    }

    public String codigo() {
        return FORMATO.format(periodo);
    }
}
