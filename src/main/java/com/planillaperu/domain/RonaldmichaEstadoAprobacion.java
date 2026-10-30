package com.planillaperu.domain;

public enum RonaldmichaEstadoAprobacion {
    PENDIENTE("Pendiente"),
    APROBADO("Aprobado"),
    RECHAZADO("Rechazado");

    private final String etiqueta;

    RonaldmichaEstadoAprobacion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public static RonaldmichaEstadoAprobacion desdeEtiqueta(String valor) {
        for (RonaldmichaEstadoAprobacion e : values()) {
            if (e.etiqueta.equals(valor)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Estado de aprobación desconocido: " + valor);
    }
}
