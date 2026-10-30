package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaPermiso;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaConfiguracionRepository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RonaldmichaSeguridadService {

    public static final int INACTIVIDAD_MINIMA = 5;
    public static final int INACTIVIDAD_MAXIMA = 240;
    private static final int INACTIVIDAD_POR_DEFECTO = 30;

    public record Regla(String titulo, String descripcion) {
    }

    private final RonaldmichaConfiguracionRepository configuracion;
    private final RonaldmichaUsuarioService usuarios;
    private final RonaldmichaAuditoriaService auditoria;

    public RonaldmichaSeguridadService(RonaldmichaConfiguracionRepository configuracion, RonaldmichaUsuarioService usuarios, RonaldmichaAuditoriaService auditoria) {
        this.configuracion = configuracion;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    public void exigirAlguno(RonaldmichaUsuario usuario, Collection<RonaldmichaPermiso> permisos, String vista) {
        if (usuario == null) {
            throw new RonaldmichaAccesoDenegadoException("Inicie sesión.");
        }
        if (permisos.stream().noneMatch(p -> RonaldmichaMatrizPermisos.tiene(usuario.rol(), p))) {
            throw new RonaldmichaAccesoDenegadoException("Su usuario (" + usuario.rol() + ") no tiene acceso a «" + vista + "».");
        }
    }

    public boolean tiene(RonaldmichaUsuario usuario, RonaldmichaPermiso permiso) {
        return usuario != null && RonaldmichaMatrizPermisos.tiene(usuario.rol(), permiso);
    }

    public Map<RonaldmichaRol, Set<RonaldmichaPermiso>> matriz() {
        Map<RonaldmichaRol, Set<RonaldmichaPermiso>> m = new LinkedHashMap<>();
        for (RonaldmichaRol r : RonaldmichaRol.values()) {
            m.put(r, RonaldmichaMatrizPermisos.de(r));
        }
        return m;
    }

    public List<Regla> reglas() {
        return List.of(
                new Regla("Doble control",
                        "Los cambios de sueldo y de cuentas bancarias (también los que pide el trabajador desde "
                                + "«Mis datos») quedan pendientes y solo se aplican cuando otro usuario los aprueba."),
                new Regla("Separación de funciones",
                        "Nadie resuelve lo que registró ni lo que lo afecta: el administrador no aprueba sus propios "
                                + "cambios sensibles ni cambia su propio sueldo; el aprobador no resuelve su propio aumento."),
                new Regla("Cierre de periodo",
                        "Una planilla cerrada es de solo lectura (asistencia, novedades y montos); reabrirla exige un "
                                + "motivo y queda en la auditoría. El trabajador solo ve las boletas de periodos cerrados."),
                new Regla("Contraseñas cifradas",
                        "Se guardan con BCrypt. Las nuevas tienen al menos " + RonaldmichaUsuarioService.CLAVE_MINIMA
                                + " caracteres con letras y números; las temporales se cambian en el primer ingreso."),
                new Regla("Bloqueo por intentos fallidos",
                        "Tras " + usuarios.intentosMaximos() + " intentos fallidos seguidos la cuenta se bloquea "
                                + usuarios.minutosBloqueo() + " minutos, o hasta que el administrador la desbloquee."),
                new Regla("Cierre por inactividad",
                        "La sesión se cierra tras " + minutosInactividad() + " minutos sin usar el teclado ni el ratón."));
    }

    public int minutosInactividad() {
        int m = configuracion.entero(RonaldmichaConfiguracionRepository.INACTIVIDAD_MINUTOS, INACTIVIDAD_POR_DEFECTO);
        return Math.clamp(m, INACTIVIDAD_MINIMA, INACTIVIDAD_MAXIMA);
    }

    public void cambiarInactividad(RonaldmichaUsuario admin, int minutos) {
        RonaldmichaPermisos.exigir(admin, RonaldmichaPermiso.GESTIONAR_USUARIOS, "cambiar la configuración de la sesión");
        if (minutos < INACTIVIDAD_MINIMA || minutos > INACTIVIDAD_MAXIMA) {
            throw new RonaldmichaValidacionException("El cierre por inactividad debe estar entre " + INACTIVIDAD_MINIMA + " y "
                    + INACTIVIDAD_MAXIMA + " minutos.");
        }
        int antes = minutosInactividad();
        configuracion.guardar(RonaldmichaConfiguracionRepository.INACTIVIDAD_MINUTOS, String.valueOf(minutos));
        auditoria.registrar(admin.usuario(), "CONFIGURACION_ACTUALIZADA", "Cierre de sesión por inactividad: "
                + antes + " → " + minutos + " minutos");
    }
}
