package com.planillaperu.domain;

public enum RonaldmichaTipoBeneficio {
    GRATIFICACION("GRATI", "Gratificación"),
    CTS("CTS", "CTS");

    private final String codigo;
    private final String descripcion;

    RonaldmichaTipoBeneficio(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    public String codigo() {
        return codigo;
    }

    public String descripcion() {
        return descripcion;
    }
}
