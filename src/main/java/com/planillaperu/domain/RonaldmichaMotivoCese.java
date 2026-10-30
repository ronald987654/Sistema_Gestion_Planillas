package com.planillaperu.domain;

public enum RonaldmichaMotivoCese {
    RENUNCIA("Renuncia voluntaria"),
    DESPIDO_ARBITRARIO("Despido arbitrario"),
    VENCIMIENTO("Vencimiento de contrato"),
    MUTUO_DISENSO("Mutuo disenso"),
    FALTA_GRAVE("Despido por falta grave"),
    JUBILACION("Jubilación"),
    FALLECIMIENTO("Fallecimiento");

    private final String etiqueta;

    RonaldmichaMotivoCese(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public boolean indemnizable() {
        return this == DESPIDO_ARBITRARIO;
    }

    @Override
    public String toString() {
        return etiqueta;
    }

    public static RonaldmichaMotivoCese desdeEtiqueta(String valor) {
        for (RonaldmichaMotivoCese m : values()) {
            if (m.etiqueta.equals(valor)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Motivo de cese desconocido: " + valor);
    }
}
