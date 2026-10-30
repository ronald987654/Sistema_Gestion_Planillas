package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaPermiso;
import com.planillaperu.domain.RonaldmichaRol;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.planillaperu.domain.RonaldmichaPermiso.APROBAR_CAMBIOS;
import static com.planillaperu.domain.RonaldmichaPermiso.GENERAR_REPORTES;
import static com.planillaperu.domain.RonaldmichaPermiso.PORTAL_PROPIO;
import static com.planillaperu.domain.RonaldmichaPermiso.RESOLVER_VACACIONES;
import static com.planillaperu.domain.RonaldmichaPermiso.VER_AUDITORIA;
import static com.planillaperu.domain.RonaldmichaPermiso.VER_PLANILLAS;

public final class RonaldmichaMatrizPermisos {

    private static final Map<RonaldmichaRol, Set<RonaldmichaPermiso>> MATRIZ = new EnumMap<>(RonaldmichaRol.class);

    static {
        Set<RonaldmichaPermiso> admin = EnumSet.allOf(RonaldmichaPermiso.class);
        admin.remove(PORTAL_PROPIO);
        MATRIZ.put(RonaldmichaRol.ADMIN, admin);
        MATRIZ.put(RonaldmichaRol.APROBADOR, EnumSet.of(VER_PLANILLAS, GENERAR_REPORTES, APROBAR_CAMBIOS, RESOLVER_VACACIONES,
                VER_AUDITORIA));
        MATRIZ.put(RonaldmichaRol.TRABAJADOR, EnumSet.of(PORTAL_PROPIO));
    }

    private RonaldmichaMatrizPermisos() {
    }

    public static boolean tiene(RonaldmichaRol rol, RonaldmichaPermiso permiso) {
        return rol != null && MATRIZ.get(rol).contains(permiso);
    }

    public static Set<RonaldmichaPermiso> de(RonaldmichaRol rol) {
        return EnumSet.copyOf(MATRIZ.get(rol));
    }
}
