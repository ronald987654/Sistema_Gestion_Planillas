package com.planillaperu.domain;

import java.math.BigDecimal;

public enum RonaldmichaRegimenLaboral {
    GENERAL("general", "Régimen general", 2, 30, 30),
    PEQUENA("pequena", "Pequeña empresa", 1, 15, 15),
    MICRO("micro", "Microempresa", 0, 0, 15);

    private final String codigo;
    private final String descripcion;
    private final int gratificacionesAnuales;
    private final int diasCtsAnuales;
    private final int diasVacacionesAnuales;

    RonaldmichaRegimenLaboral(String codigo, String descripcion, int gratificacionesAnuales, int diasCtsAnuales,
                   int diasVacacionesAnuales) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.gratificacionesAnuales = gratificacionesAnuales;
        this.diasCtsAnuales = diasCtsAnuales;
        this.diasVacacionesAnuales = diasVacacionesAnuales;
    }

    public int gratificacionesAnuales() {
        return gratificacionesAnuales;
    }

    public int diasCtsAnuales() {
        return diasCtsAnuales;
    }

    public int diasVacacionesAnuales() {
        return diasVacacionesAnuales;
    }

    public BigDecimal factorGratificacion() {
        return BigDecimal.valueOf(gratificacionesAnuales).divide(BigDecimal.valueOf(2));
    }

    public BigDecimal factorCts() {
        return BigDecimal.valueOf(diasCtsAnuales).divide(BigDecimal.valueOf(30));
    }

    public String codigo() {
        return codigo;
    }

    public String descripcion() {
        return descripcion;
    }

    public static RonaldmichaRegimenLaboral desdeCodigo(String valor) {
        for (RonaldmichaRegimenLaboral r : values()) {
            if (r.codigo.equals(valor)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Régimen laboral desconocido: " + valor);
    }
}
