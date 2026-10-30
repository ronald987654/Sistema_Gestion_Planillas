package com.planillaperu.domain;

import java.util.Collection;

public enum RonaldmichaOrigenAsistencia {
    SISTEMA("Sistema"),
    EXCEL("Excel"),
    EXCEL_AJUSTES("Excel + ajustes"),
    SIN_REGISTRO("Sin registro");

    private final String etiqueta;

    RonaldmichaOrigenAsistencia(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public static RonaldmichaOrigenAsistencia desdeEtiqueta(String valor) {
        for (RonaldmichaOrigenAsistencia o : values()) {
            if (o.etiqueta.equalsIgnoreCase(valor)) {
                return o;
            }
        }
        throw new IllegalArgumentException("Origen de asistencia desconocido: " + valor);
    }

    public RonaldmichaOrigenAsistencia alEditar() {
        return this == EXCEL || this == EXCEL_AJUSTES ? EXCEL_AJUSTES : SISTEMA;
    }

    public static RonaldmichaOrigenAsistencia delMes(Collection<RonaldmichaOrigenAsistencia> origenes) {
        if (origenes.isEmpty()) {
            return SIN_REGISTRO;
        }
        if (origenes.stream().allMatch(o -> o == SISTEMA)) {
            return SISTEMA;
        }
        if (origenes.stream().allMatch(o -> o == EXCEL)) {
            return EXCEL;
        }
        return EXCEL_AJUSTES;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
