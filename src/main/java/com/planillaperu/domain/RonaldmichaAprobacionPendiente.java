package com.planillaperu.domain;

import java.time.LocalDateTime;

public record RonaldmichaAprobacionPendiente(
        long id,
        String tipo,
        Long trabajadorId,
        String trabajador,
        String detalle,
        String registradoPor,
        LocalDateTime fecha) {
}
