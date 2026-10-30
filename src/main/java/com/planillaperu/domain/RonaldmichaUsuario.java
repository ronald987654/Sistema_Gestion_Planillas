package com.planillaperu.domain;

public record RonaldmichaUsuario(
        Long id,
        String usuario,
        String hashPassword,
        RonaldmichaRol rol,
        Long trabajadorId,
        boolean activo) {
}
