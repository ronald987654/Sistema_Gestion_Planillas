package com.planillaperu.domain;

public enum RonaldmichaSalud {
    ESSALUD("EsSalud"),
    EPS("EPS"),
    SIS("SIS");

    private final String nombre;

    RonaldmichaSalud(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }

    public static RonaldmichaSalud desdeNombre(String valor) {
        for (RonaldmichaSalud s : values()) {
            if (s.nombre.equalsIgnoreCase(valor)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Cobertura de salud desconocida: " + valor);
    }

    @Override
    public String toString() {
        return nombre;
    }
}
