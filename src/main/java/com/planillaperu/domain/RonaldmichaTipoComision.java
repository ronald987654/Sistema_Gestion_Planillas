package com.planillaperu.domain;

public enum RonaldmichaTipoComision {
    FLUJO("flujo", "Flujo (sobre la remuneración)"),
    MIXTA("mixta", "Mixta (sobre el saldo)");

    private final String codigo;
    private final String descripcion;

    RonaldmichaTipoComision(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    public String codigo() {
        return codigo;
    }

    public String descripcion() {
        return descripcion;
    }

    public static RonaldmichaTipoComision desdeCodigo(String valor) {
        for (RonaldmichaTipoComision t : values()) {
            if (t.codigo.equalsIgnoreCase(valor)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tipo de comisión desconocido: " + valor);
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
