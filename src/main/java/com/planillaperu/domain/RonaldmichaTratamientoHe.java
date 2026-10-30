package com.planillaperu.domain;

import com.planillaperu.common.RonaldmichaBusqueda;

public enum RonaldmichaTratamientoHe {
    PAGAR("pago", "Pagar"),
    COMPENSAR("comp", "Compensar");

    private final String codigo;
    private final String descripcion;

    RonaldmichaTratamientoHe(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    public String codigo() {
        return codigo;
    }

    public String descripcion() {
        return descripcion;
    }

    public static RonaldmichaTratamientoHe desdeCodigo(String valor) {
        for (RonaldmichaTratamientoHe t : values()) {
            if (t.codigo.equalsIgnoreCase(valor)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tratamiento de horas extra desconocido: " + valor);
    }

    public static RonaldmichaTratamientoHe desdeTexto(String valor) {
        String v = RonaldmichaBusqueda.normalizar(valor);
        if (v.isEmpty()) {
            return PAGAR;
        }
        for (RonaldmichaTratamientoHe t : values()) {
            if (v.equals(t.codigo) || v.equals(RonaldmichaBusqueda.normalizar(t.descripcion))) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tratamiento de horas extra desconocido: " + valor);
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
