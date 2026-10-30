package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaPermiso;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;

final class RonaldmichaPermisos {

    private RonaldmichaPermisos() {
    }

    static void exigir(RonaldmichaUsuario usuario, RonaldmichaPermiso permiso, String accion) {
        if (usuario == null) {
            throw new RonaldmichaAccesoDenegadoException("Inicie sesión para " + accion + ".");
        }
        if (!RonaldmichaMatrizPermisos.tiene(usuario.rol(), permiso)) {
            throw new RonaldmichaAccesoDenegadoException("Su usuario (" + usuario.rol() + ") no tiene permiso para " + accion + ".");
        }
    }

    static void exigirAdmin(RonaldmichaUsuario usuario, String accion) {
        if (usuario == null || usuario.rol() != RonaldmichaRol.ADMIN) {
            throw new RonaldmichaAccesoDenegadoException("Solo un administrador puede " + accion + ".");
        }
    }

    static void exigirPersonalDePlanillas(RonaldmichaUsuario usuario, String accion) {
        if (usuario == null || usuario.rol() == RonaldmichaRol.TRABAJADOR) {
            throw new RonaldmichaAccesoDenegadoException("Su usuario no tiene permiso para " + accion + ".");
        }
    }

    static long exigirPortal(RonaldmichaUsuario usuario, String accion) {
        exigir(usuario, RonaldmichaPermiso.PORTAL_PROPIO, accion);
        if (usuario.trabajadorId() == null) {
            throw new RonaldmichaAccesoDenegadoException("Su usuario no está vinculado a un trabajador.");
        }
        return usuario.trabajadorId();
    }
}
