package com.planillaperu.domain;

import java.time.LocalDateTime;
import java.util.Map;

public record RonaldmichaAprobacion(
        Long id,
        String tipo,
        Long trabajadorId,
        String detalle,
        Map<String, String> cambios,
        String registradoPor,
        LocalDateTime fecha,
        RonaldmichaEstadoAprobacion estado,
        String resueltoPor,
        LocalDateTime fechaResolucion,
        String motivoRechazo) {

    public RonaldmichaAprobacion {
        cambios = Map.copyOf(cambios);
    }
}
