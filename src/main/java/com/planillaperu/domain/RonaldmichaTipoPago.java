package com.planillaperu.domain;

public enum RonaldmichaTipoPago {
    DEPOSITO("Depósito en cuenta"),
    EFECTIVO("Efectivo"),
    CHEQUE("Cheque");

    private final String nombre;

    RonaldmichaTipoPago(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }

    public static RonaldmichaTipoPago desdeNombre(String valor) {
        for (RonaldmichaTipoPago t : values()) {
            if (t.nombre.equalsIgnoreCase(valor)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tipo de pago desconocido: " + valor);
    }

    @Override
    public String toString() {
        return nombre;
    }
}
