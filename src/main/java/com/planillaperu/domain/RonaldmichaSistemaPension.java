package com.planillaperu.domain;

public enum RonaldmichaSistemaPension {
    ONP("ONP"),
    HABITAT("AFP Habitat"),
    INTEGRA("AFP Integra"),
    PRIMA("AFP Prima"),
    PROFUTURO("AFP Profuturo");

    private final String nombre;

    RonaldmichaSistemaPension(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }

    public boolean esAfp() {
        return this != ONP;
    }

    public static RonaldmichaSistemaPension desdeNombre(String valor) {
        for (RonaldmichaSistemaPension s : values()) {
            if (s.nombre.equalsIgnoreCase(valor)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Sistema de pensiones desconocido: " + valor);
    }

    @Override
    public String toString() {
        return nombre;
    }
}
