package com.planillaperu.domain;

import java.time.LocalDateTime;

public record RonaldmichaCuentaUsuario(
        long id,
        String usuario,
        RonaldmichaRol rol,
        Long trabajadorId,
        String trabajador,
        boolean activo,
        int intentosFallidos,
        LocalDateTime bloqueadoHasta,
        LocalDateTime ultimoAcceso,
        boolean debeCambiarClave) {

    public boolean bloqueadaEn(LocalDateTime ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }
}
