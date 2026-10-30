package com.planillaperu.domain;

public enum RonaldmichaEstadoVacacion {
    PROGRAMADO("Programado", "info"),
    GOZADO("Gozado", "success"),
    PENDIENTE("Pendiente de aprobación", "warning"),
    RECHAZADO("Rechazado", "error"),
    ANULADO("Anulado", "neutral");

    private final String etiqueta;
    private final String estilo;

    RonaldmichaEstadoVacacion(String etiqueta, String estilo) {
        this.etiqueta = etiqueta;
        this.estilo = estilo;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public String estilo() {
        return estilo;
    }

    public boolean cuenta() {
        return this == PROGRAMADO || this == GOZADO;
    }

    public boolean cerrado() {
        return this == RECHAZADO || this == ANULADO;
    }

    @Override
    public String toString() {
        return etiqueta;
    }

    public static RonaldmichaEstadoVacacion desdeEtiqueta(String valor) {
        for (RonaldmichaEstadoVacacion e : values()) {
            if (e.etiqueta.equals(valor)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Estado de vacación desconocido: " + valor);
    }
}
