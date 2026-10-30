package com.planillaperu.domain;

public enum RonaldmichaTipoConceptoExtra {
    ING("Ingreso remunerativo"),
    ING_NR("Ingreso no remunerativo"),
    DES("Descuento"),
    APO("Aporte del empleador");

    private final String descripcion;

    RonaldmichaTipoConceptoExtra(String descripcion) {
        this.descripcion = descripcion;
    }

    public String descripcion() {
        return descripcion;
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
