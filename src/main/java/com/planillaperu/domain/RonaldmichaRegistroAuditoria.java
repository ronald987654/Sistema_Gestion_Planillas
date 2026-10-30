package com.planillaperu.domain;

import java.time.LocalDateTime;

public record RonaldmichaRegistroAuditoria(
        Long id,
        String usuario,
        LocalDateTime fechaHora,
        String accion,
        String detalle) {
}
