package com.planillaperu.export;

import com.planillaperu.export.RonaldmichaConstanciaBeneficio.Dato;

import java.math.BigDecimal;
import java.util.List;

public record RonaldmichaReporteLiquidacion(
        String titulo,
        String subtitulo,
        List<Dato> datos,
        List<Bloque> bloques,
        BigDecimal bruto,
        BigDecimal descuentos,
        BigDecimal neto,
        String netoEnLetras,
        String nota,
        String firmaTrabajador,
        String documentoTrabajador) {

    public record Linea(String concepto, String detalle, BigDecimal monto) {
    }

    public record Bloque(String titulo, List<Linea> lineas, BigDecimal subtotal) {

        public Bloque {
            lineas = List.copyOf(lineas);
        }
    }

    public RonaldmichaReporteLiquidacion {
        datos = List.copyOf(datos);
        bloques = List.copyOf(bloques);
    }
}
