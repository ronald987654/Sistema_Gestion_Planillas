package com.planillaperu.domain;

public enum RonaldmichaEstadoLiquidacion {
    BORRADOR("Borrador", "warning"),
    CESE_REGISTRADO("Cese registrado", "info"),
    PAGADO("Pagado", "success");

    private final String etiqueta;
    private final String estilo;

    RonaldmichaEstadoLiquidacion(String etiqueta, String estilo) {
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

    public static RonaldmichaEstadoLiquidacion desdeEtiqueta(String valor) {
        for (RonaldmichaEstadoLiquidacion e : values()) {
            if (e.etiqueta.equals(valor)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Estado de liquidación desconocido: " + valor);
    }
}
