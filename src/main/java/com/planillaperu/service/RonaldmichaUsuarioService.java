package com.planillaperu.service;

import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaCuentaUsuario;
import com.planillaperu.domain.RonaldmichaPermiso;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaConfiguracionRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.repository.RonaldmichaUsuarioRepository;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RonaldmichaUsuarioService {

    public static final int CLAVE_MINIMA = 8;
    private static final int COSTO_BCRYPT = 10;
    private static final int LARGO_TEMPORAL = 10;
    private static final String CARACTERES_TEMPORAL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos.";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public record NuevoUsuario(String usuario, RonaldmichaRol rol, Long trabajadorId) {
    }

    public record ClaveTemporal(RonaldmichaCuentaUsuario cuenta, String clave) {
    }

    private final RonaldmichaUsuarioRepository repositorio;
    private final RonaldmichaTrabajadorRepository trabajadores;
    private final RonaldmichaConfiguracionRepository configuracion;
    private final RonaldmichaAuditoriaService auditoria;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    public RonaldmichaUsuarioService(RonaldmichaUsuarioRepository repositorio, RonaldmichaTrabajadorRepository trabajadores,
                          RonaldmichaConfiguracionRepository configuracion, RonaldmichaAuditoriaService auditoria, Clock reloj) {
        this.repositorio = repositorio;
        this.trabajadores = trabajadores;
        this.configuracion = configuracion;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public RonaldmichaUsuario autenticar(String nombreUsuario, String clave, Set<RonaldmichaRol> rolesPermitidos) {
        String usuario = nombreUsuario == null ? "" : nombreUsuario.trim();
        LocalDateTime ahora = ahora();
        Optional<RonaldmichaCuentaUsuario> cuenta = repositorio.cuenta(usuario).filter(RonaldmichaCuentaUsuario::activo);
        if (cuenta.isPresent() && cuenta.get().bloqueadaEn(ahora)) {
            auditoria.registrar(usuario, "INICIO_SESION_BLOQUEADO", "Cuenta bloqueada hasta "
                    + RonaldmichaFormatos.fechaHora(cuenta.get().bloqueadoHasta()));
            throw new RonaldmichaAccesoDenegadoException(mensajeBloqueo(cuenta.get().bloqueadoHasta()));
        }
        Optional<RonaldmichaUsuario> encontrado = cuenta.isEmpty() ? Optional.empty() : repositorio.buscarPorUsuario(usuario)
                .filter(u -> clave != null && BCrypt.checkpw(clave, u.hashPassword()));

        if (encontrado.isEmpty()) {
            auditoria.registrar(usuario.isEmpty() ? "(vacío)" : usuario, "INICIO_SESION_FALLIDO",
                    "Credenciales inválidas");
            cuenta.ifPresent(c -> registrarFallo(c, ahora));
            throw new RonaldmichaAccesoDenegadoException(CREDENCIALES_INVALIDAS);
        }
        RonaldmichaUsuario u = encontrado.get();
        if (!rolesPermitidos.contains(u.rol())) {
            auditoria.registrar(usuario, "INICIO_SESION_FALLIDO", "Tipo de acceso no corresponde al rol " + u.rol());
            throw new RonaldmichaAccesoDenegadoException(u.rol() == RonaldmichaRol.TRABAJADOR
                    ? "Esta cuenta es de trabajador. Elija «Trabajador» para ingresar."
                    : "Esta cuenta es de administración. Elija «Administrador» para ingresar.");
        }
        repositorio.registrarAcceso(u.id(), ahora);
        auditoria.registrar(usuario, "INICIO_SESION", "Rol " + u.rol());
        return u;
    }

    private void registrarFallo(RonaldmichaCuentaUsuario c, LocalDateTime ahora) {
        int intentos = c.intentosFallidos() + 1;
        if (intentos < intentosMaximos()) {
            repositorio.registrarFallo(c.id(), intentos, null);
            return;
        }
        LocalDateTime hasta = ahora.plusMinutes(minutosBloqueo());
        repositorio.registrarFallo(c.id(), 0, hasta);
        auditoria.registrar(c.usuario(), "USUARIO_BLOQUEADO", intentos + " intentos fallidos seguidos · bloqueado hasta "
                + RonaldmichaFormatos.fechaHora(hasta));
        throw new RonaldmichaAccesoDenegadoException(intentos + " intentos fallidos. " + mensajeBloqueo(hasta));
    }

    private static String mensajeBloqueo(LocalDateTime hasta) {
        return "La cuenta está bloqueada temporalmente: intente de nuevo a las " + HORA.format(hasta)
                + " o pida al administrador que la desbloquee.";
    }

    public boolean debeCambiarClave(RonaldmichaUsuario usuario) {
        return repositorio.cuenta(usuario.id()).map(RonaldmichaCuentaUsuario::debeCambiarClave).orElse(false);
    }

    public void cambiarClave(RonaldmichaUsuario usuario, String actual, String nueva, String confirmacion) {
        if (usuario == null) {
            throw new RonaldmichaAccesoDenegadoException("Inicie sesión para cambiar su contraseña.");
        }
        RonaldmichaUsuario guardado = repositorio.buscarPorUsuario(usuario.usuario())
                .orElseThrow(() -> new RonaldmichaValidacionException("El usuario no existe."));
        if (actual == null || !BCrypt.checkpw(actual, guardado.hashPassword())) {
            throw new RonaldmichaValidacionException("La contraseña actual no es correcta.");
        }
        List<String> errores = validarClave(usuario.usuario(), nueva);
        if (nueva != null && !nueva.equals(confirmacion)) {
            errores.add("La confirmación no coincide con la nueva contraseña.");
        }
        if (nueva != null && nueva.equals(actual)) {
            errores.add("La nueva contraseña debe ser distinta de la actual.");
        }
        if (!errores.isEmpty()) {
            throw new RonaldmichaValidacionException(errores);
        }
        repositorio.cambiarClave(usuario.id(), hash(nueva), false);
        auditoria.registrar(usuario.usuario(), "CLAVE_CAMBIADA", "Cambio de contraseña por el propio usuario");
    }

    public List<String> validarClave(String usuario, String clave) {
        List<String> errores = new ArrayList<>();
        if (clave == null || clave.length() < CLAVE_MINIMA) {
            errores.add("La contraseña debe tener al menos " + CLAVE_MINIMA + " caracteres.");
        }
        if (clave != null && (!clave.matches(".*\\p{L}.*") || !clave.matches(".*\\d.*"))) {
            errores.add("La contraseña debe combinar letras y números.");
        }
        if (clave != null && usuario != null && clave.equalsIgnoreCase(usuario)) {
            errores.add("La contraseña no puede ser igual al usuario.");
        }
        return errores;
    }

    public void cerrarSesion(RonaldmichaUsuario usuario) {
        auditoria.registrar(usuario.usuario(), "CIERRE_SESION", "Rol " + usuario.rol());
    }

    public void cerrarSesionPorInactividad(RonaldmichaUsuario usuario, int minutos) {
        auditoria.registrar(usuario.usuario(), "CIERRE_SESION_INACTIVIDAD", "Sin actividad durante " + minutos
                + " minutos · rol " + usuario.rol());
    }

    public List<RonaldmichaCuentaUsuario> listar(RonaldmichaUsuario usuario) {
        RonaldmichaPermisos.exigir(usuario, RonaldmichaPermiso.VER_AUDITORIA, "ver los usuarios");
        return repositorio.listar();
    }

    public ClaveTemporal crear(RonaldmichaUsuario admin, NuevoUsuario nuevo) {
        RonaldmichaPermisos.exigir(admin, RonaldmichaPermiso.GESTIONAR_USUARIOS, "crear usuarios");
        if (nuevo.rol() == null) {
            throw new RonaldmichaValidacionException("Elija el rol del usuario.");
        }
        List<String> errores = new ArrayList<>();
        RonaldmichaTrabajador trabajador = null;
        if (nuevo.trabajadorId() != null) {
            trabajador = trabajadores.buscarPorId(nuevo.trabajadorId()).orElse(null);
            if (trabajador == null) {
                errores.add("El trabajador elegido no existe.");
            } else if (!trabajador.activoEn(ahora().toLocalDate())) {
                errores.add(trabajador.nombreCompleto() + " ya no está activo.");
            } else if (repositorio.deTrabajador(trabajador.id(), nuevo.rol()).isPresent()) {
                errores.add(trabajador.nombreCompleto() + " ya tiene un usuario con rol " + nuevo.rol() + ".");
            }
        } else if (nuevo.rol() == RonaldmichaRol.TRABAJADOR) {
            errores.add("Elija el trabajador: el usuario de rol TRABAJADOR entra con su número de documento.");
        }
        String nombre = nuevo.rol() == RonaldmichaRol.TRABAJADOR && trabajador != null ? trabajador.numDoc()
                : nuevo.usuario() == null ? "" : nuevo.usuario().trim();
        if (!nombre.matches("[A-Za-z0-9._-]{3,40}")) {
            errores.add("El usuario debe tener de 3 a 40 caracteres: letras, números, punto, guion o guion bajo.");
        } else if (repositorio.existe(nombre)) {
            errores.add("Ya existe el usuario «" + nombre + "».");
        }
        if (!errores.isEmpty()) {
            throw new RonaldmichaValidacionException(errores);
        }
        String clave = claveTemporal();
        long id = repositorio.insertar(nombre, hash(clave), nuevo.rol(), nuevo.trabajadorId());
        RonaldmichaCuentaUsuario cuenta = cuenta(id);
        auditoria.registrar(admin.usuario(), "USUARIO_CREADO", describir(cuenta) + " · contraseña temporal");
        return new ClaveTemporal(cuenta, clave);
    }

    public RonaldmichaCuentaUsuario cambiarActivo(RonaldmichaUsuario admin, long id, boolean activo) {
        RonaldmichaPermisos.exigir(admin, RonaldmichaPermiso.GESTIONAR_USUARIOS, activo ? "activar usuarios" : "desactivar usuarios");
        RonaldmichaCuentaUsuario c = cuenta(id);
        if (c.activo() == activo) {
            throw new RonaldmichaValidacionException("El usuario " + c.usuario() + " ya está " + (activo ? "activo." : "inactivo."));
        }
        if (!activo && c.id() == admin.id()) {
            throw new RonaldmichaValidacionException("No puede desactivar su propio usuario.");
        }
        if (!activo && c.rol() == RonaldmichaRol.ADMIN && repositorio.contarActivos(RonaldmichaRol.ADMIN) <= 1) {
            throw new RonaldmichaValidacionException("Debe quedar al menos un administrador activo.");
        }
        repositorio.cambiarActivo(id, activo);
        auditoria.registrar(admin.usuario(), activo ? "USUARIO_ACTIVADO" : "USUARIO_DESACTIVADO", describir(c));
        return cuenta(id);
    }

    public ClaveTemporal restablecerClave(RonaldmichaUsuario admin, long id) {
        RonaldmichaPermisos.exigir(admin, RonaldmichaPermiso.GESTIONAR_USUARIOS, "restablecer contraseñas");
        RonaldmichaCuentaUsuario c = cuenta(id);
        if (c.id() == admin.id()) {
            throw new RonaldmichaValidacionException("Para su propia cuenta use «Cambiar contraseña» en el menú de usuario.");
        }
        String clave = claveTemporal();
        repositorio.cambiarClave(id, hash(clave), true);
        auditoria.registrar(admin.usuario(), "CLAVE_RESTABLECIDA", describir(c) + " · contraseña temporal generada");
        return new ClaveTemporal(cuenta(id), clave);
    }

    public RonaldmichaCuentaUsuario desbloquear(RonaldmichaUsuario admin, long id) {
        RonaldmichaPermisos.exigir(admin, RonaldmichaPermiso.GESTIONAR_USUARIOS, "desbloquear usuarios");
        RonaldmichaCuentaUsuario c = cuenta(id);
        if (!bloqueada(c)) {
            throw new RonaldmichaValidacionException("El usuario " + c.usuario() + " no está bloqueado.");
        }
        repositorio.desbloquear(id);
        auditoria.registrar(admin.usuario(), "USUARIO_DESBLOQUEADO", describir(c));
        return cuenta(id);
    }

    public boolean bloqueada(RonaldmichaCuentaUsuario c) {
        return c.bloqueadaEn(ahora());
    }

    public int intentosMaximos() {
        return Math.max(1, configuracion.entero(RonaldmichaConfiguracionRepository.INTENTOS_MAXIMOS, 5));
    }

    public int minutosBloqueo() {
        return Math.max(1, configuracion.entero(RonaldmichaConfiguracionRepository.BLOQUEO_MINUTOS, 15));
    }

    private RonaldmichaCuentaUsuario cuenta(long id) {
        return repositorio.cuenta(id).orElseThrow(() -> new RonaldmichaValidacionException("El usuario no existe."));
    }

    private String claveTemporal() {
        while (true) {
            StringBuilder sb = new StringBuilder(LARGO_TEMPORAL);
            for (int i = 0; i < LARGO_TEMPORAL; i++) {
                sb.append(CARACTERES_TEMPORAL.charAt(azar.nextInt(CARACTERES_TEMPORAL.length())));
            }
            String clave = sb.toString();
            if (validarClave(null, clave).isEmpty()) {
                return clave;
            }
        }
    }

    private static String hash(String clave) {
        return BCrypt.hashpw(clave, BCrypt.gensalt(COSTO_BCRYPT));
    }

    private static String describir(RonaldmichaCuentaUsuario c) {
        return c.usuario() + " · " + c.rol() + (c.trabajador() == null ? "" : " · " + c.trabajador());
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
    }
}
