package com.planillaperu.domain;

import java.math.BigDecimal;

public record RonaldmichaTrabajadorRemuneracion(
        RonaldmichaTrabajadorResumen trabajador,
        BigDecimal sueldo,
        boolean asigFamiliar) {
}
