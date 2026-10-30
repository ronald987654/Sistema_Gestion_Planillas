package com.planillaperu.domain;

import java.time.LocalDateTime;
import java.time.YearMonth;

public record RonaldmichaDocumentoEmitido(
        Long id,
        String numero,
        String tipo,
        Long trabajadorId,
        String trabajador,
        YearMonth periodo,
        String formato,
        LocalDateTime fecha,
        RonaldmichaEstadoDocumento estado,
        boolean requiereCargo,
        String archivo,
        LocalDateTime fechaRecepcion,
        String recepcion) {

    public RonaldmichaDocumentoEmitido(Long id, String numero, String tipo, Long trabajadorId, String trabajador,
                            YearMonth periodo, String formato, LocalDateTime fecha, RonaldmichaEstadoDocumento estado,
                            boolean requiereCargo, String archivo) {
        this(id, numero, tipo, trabajadorId, trabajador, periodo, formato, fecha, estado, requiereCargo, archivo,
                null, null);
    }

    public boolean cargoPendiente() {
        return requiereCargo && estado != RonaldmichaEstadoDocumento.RECIBIDO;
    }
}
