package com.planillaperu.domain;

public enum RonaldmichaSituacionPeriodo {
    ABIERTO("Abierto"),
    CERRADO("Cerrado"),
    FUTURO("Futuro");

    private final String etiqueta;

    RonaldmichaSituacionPeriodo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
