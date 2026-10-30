package com.planillaperu.export;

import java.math.BigDecimal;
import java.util.List;

public record RonaldmichaConstanciaBeneficio(
        String titulo,
        String periodo,
        List<Dato> datos,
        List<Linea> lineas,
        String nota) {

    public record Dato(String etiqueta, String valor) {
    }

    public record Linea(String concepto, BigDecimal monto, boolean destacada) {
    }

    public RonaldmichaConstanciaBeneficio {
        datos = List.copyOf(datos);
        lineas = List.copyOf(lineas);
    }
}
