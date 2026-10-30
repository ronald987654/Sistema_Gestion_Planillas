package com.planillaperu.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RonaldmichaVacacion(
        Long id,
        long trabajadorId,
        RonaldmichaTipoVacacion tipo,
        LocalDate desde,
        LocalDate hasta,
        int dias,
        String observacion,
        RonaldmichaEstadoVacacion estado,
        LocalDateTime fechaRegistro,
        String usuario,
        String adjuntoNombre,
        boolean acuerdoEscrito) {

    public boolean tieneAdjunto() {
        return adjuntoNombre != null && !adjuntoNombre.isBlank();
    }

    public RonaldmichaVacacion conEstado(RonaldmichaEstadoVacacion nuevo) {
        return new RonaldmichaVacacion(id, trabajadorId, tipo, desde, hasta, dias, observacion, nuevo, fechaRegistro, usuario,
                adjuntoNombre, acuerdoEscrito);
    }
}
