package com.planillaperu.domain;

public enum RonaldmichaTipoDocumento {
    DNI("DNI", "DNI"),
    CE("CE", "Carné de extranjería"),
    PAS("PAS", "Pasaporte");

    private final String codigo;
    private final String descripcion;

    RonaldmichaTipoDocumento(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    public String codigo() {
        return codigo;
    }

    public String descripcion() {
        return descripcion;
    }

    public static RonaldmichaTipoDocumento desdeCodigo(String valor) {
        for (RonaldmichaTipoDocumento t : values()) {
            if (t.codigo.equalsIgnoreCase(valor)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tipo de documento desconocido: " + valor);
    }

    @Override
    public String toString() {
        return codigo;
    }
}
