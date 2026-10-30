package com.planillaperu.domain;

import java.math.BigDecimal;

public record RonaldmichaAfpComision(
        String afp,
        BigDecimal comisionFlujoPct,
        BigDecimal comisionMixtaPct) {
}
