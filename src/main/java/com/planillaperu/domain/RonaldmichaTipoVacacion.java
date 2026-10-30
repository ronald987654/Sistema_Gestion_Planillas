package com.planillaperu.domain;

public enum RonaldmichaTipoVacacion {
    GOCE("Goce"),
    VENTA("Venta");

    private final String etiqueta;

    RonaldmichaTipoVacacion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }

    public static RonaldmichaTipoVacacion desdeEtiqueta(String valor) {
        for (RonaldmichaTipoVacacion t : values()) {
            if (t.etiqueta.equals(valor)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tipo de vacación desconocido: " + valor);
    }
}
