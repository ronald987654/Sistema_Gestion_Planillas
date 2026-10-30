package com.planillaperu.domain;

public enum RonaldmichaEstadoDocumento {
    GENERADO("Generado", "neutral"),
    ENVIADO("Enviado", "info"),
    RECIBIDO("Recibido", "success"),
    DECLARADO("Declarado", "holiday");

    private final String etiqueta;
    private final String estilo;

    RonaldmichaEstadoDocumento(String etiqueta, String estilo) {
        this.etiqueta = etiqueta;
        this.estilo = estilo;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public String estilo() {
        return estilo;
    }

    @Override
    public String toString() {
        return etiqueta;
    }

    public static RonaldmichaEstadoDocumento desdeEtiqueta(String valor) {
        for (RonaldmichaEstadoDocumento e : values()) {
            if (e.etiqueta.equals(valor)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Estado de documento desconocido: " + valor);
    }
}
