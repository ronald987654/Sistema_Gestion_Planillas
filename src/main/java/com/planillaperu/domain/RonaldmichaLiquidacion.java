package com.planillaperu.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record RonaldmichaLiquidacion(
        Long id,
        long trabajadorId,
        LocalDate fechaCese,
        RonaldmichaMotivoCese motivo,
        BigDecimal otrosIngresos,
        BigDecimal otrosDescuentos,
        int faltasCts,
        int faltasGratificacion,
        BigDecimal ultimaGratificacion,
        RonaldmichaEstadoLiquidacion estado,
        LocalDateTime fechaRegistro,
        String usuario,
        BigDecimal bruto,
        BigDecimal descuento,
        BigDecimal neto) {
}
