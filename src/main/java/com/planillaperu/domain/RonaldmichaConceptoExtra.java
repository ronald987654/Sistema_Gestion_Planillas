package com.planillaperu.domain;

import java.math.BigDecimal;

public record RonaldmichaConceptoExtra(
        RonaldmichaTipoConceptoExtra tipo,
        String codigoPlame,
        String descripcion,
        BigDecimal monto) {
}
