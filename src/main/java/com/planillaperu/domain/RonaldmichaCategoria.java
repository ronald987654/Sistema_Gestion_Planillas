package com.planillaperu.domain;

public enum RonaldmichaCategoria {
    EMPLEADO("Empleado"),
    OBRERO("Obrero");

    private final String nombre;

    RonaldmichaCategoria(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }

    public static RonaldmichaCategoria desdeNombre(String valor) {
        for (RonaldmichaCategoria c : values()) {
            if (c.nombre.equalsIgnoreCase(valor)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Categoría desconocida: " + valor);
    }

    @Override
    public String toString() {
        return nombre;
    }
}
