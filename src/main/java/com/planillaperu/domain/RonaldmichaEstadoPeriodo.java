package com.planillaperu.domain;

public enum RonaldmichaEstadoPeriodo {
    ABIERTO("Abierto"),
    CERRADO("Cerrado");

    private final String etiqueta;

    RonaldmichaEstadoPeriodo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public static RonaldmichaEstadoPeriodo desdeEtiqueta(String valor) {
        for (RonaldmichaEstadoPeriodo e : values()) {
            if (e.etiqueta.equals(valor)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Estado de periodo desconocido: " + valor);
    }
}
