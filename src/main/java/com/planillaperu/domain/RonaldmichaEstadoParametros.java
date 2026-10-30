package com.planillaperu.domain;

public enum RonaldmichaEstadoParametros {
    VIGENTE("Vigente"),
    ANULADA("Anulada");

    private final String etiqueta;

    RonaldmichaEstadoParametros(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public static RonaldmichaEstadoParametros desdeEtiqueta(String valor) {
        for (RonaldmichaEstadoParametros e : values()) {
            if (e.etiqueta.equals(valor)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Estado de parámetros desconocido: " + valor);
    }
}
