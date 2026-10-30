package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaCuentaUsuario;
import com.planillaperu.domain.RonaldmichaRegistroAuditoria;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaConfiguracionRepository;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.repository.RonaldmichaUsuarioRepository;
import com.planillaperu.service.RonaldmichaUsuarioService.ClaveTemporal;
import com.planillaperu.service.RonaldmichaUsuarioService.NuevoUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaUsuarioServiceTest {

    private static final Set<RonaldmichaRol> ADMINISTRADOR = EnumSet.of(RonaldmichaRol.ADMIN, RonaldmichaRol.APROBADOR);
    private static final Set<RonaldmichaRol> TRABAJADOR = EnumSet.of(RonaldmichaRol.TRABAJADOR);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @TempDir
    Path carpeta;

    private RonaldmichaUsuarioService servicio;
    private RonaldmichaAuditoriaService auditoria;
    private RonaldmichaTrabajadorRepository trabajadores;
    private RelojAjustable reloj;

    private static final class RelojAjustable extends Clock {
        private Instant ahora = Instant.parse("2026-10-15T15:00:00Z");

        void adelantar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return LIMA;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    @BeforeEach
    void preparar() {
        RonaldmichaDatabase db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        reloj = new RelojAjustable();
        auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        trabajadores = new RonaldmichaTrabajadorRepository(db);
        servicio = new RonaldmichaUsuarioService(new RonaldmichaUsuarioRepository(db), trabajadores, new RonaldmichaConfiguracionRepository(db),
                auditoria, reloj);
    }

    @Test
    void autenticaAlAdministrador() {
        RonaldmichaUsuario u = servicio.autenticar("admin", "admin123", ADMINISTRADOR);
        assertThat(u.rol()).isEqualTo(RonaldmichaRol.ADMIN);
        assertThat(u.trabajadorId()).isNotNull();
        assertThat(servicio.debeCambiarClave(u)).isFalse();
    }

    @Test
    void autenticaAlAprobadorPorElAccesoDeAdministracion() {
        RonaldmichaUsuario u = servicio.autenticar("aprobador", "aprob123", ADMINISTRADOR);
        assertThat(u.rol()).isEqualTo(RonaldmichaRol.APROBADOR);
    }

    @Test
    void elTrabajadorEntraConSuDni() {
        RonaldmichaUsuario u = servicio.autenticar(" 40000002 ", "123456", TRABAJADOR);
        assertThat(u.rol()).isEqualTo(RonaldmichaRol.TRABAJADOR);
        assertThat(u.trabajadorId()).isNotNull();
    }

    @Test
    void rechazaCredencialesInvalidas() {
        assertThatThrownBy(() -> servicio.autenticar("admin", "otra", ADMINISTRADOR))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class)
                .hasMessage("Usuario o contraseña incorrectos.");
        assertThatThrownBy(() -> servicio.autenticar("nadie", "admin123", ADMINISTRADOR))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.autenticar("admin", null, ADMINISTRADOR))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    @Test
    void rechazaElTipoDeAccesoQueNoCorrespondeAlRol() {
        assertThatThrownBy(() -> servicio.autenticar("admin", "admin123", TRABAJADOR))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class)
                .hasMessageContaining("Administrador");
        assertThatThrownBy(() -> servicio.autenticar("40000002", "123456", ADMINISTRADOR))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class)
                .hasMessageContaining("Trabajador");
    }

    @Test
    void registraIngresosFallosYCierreDeSesion() {
        RonaldmichaUsuario u = servicio.autenticar("admin", "admin123", ADMINISTRADOR);
        assertThatThrownBy(() -> servicio.autenticar("admin", "mal", ADMINISTRADOR));
        servicio.cerrarSesion(u);
        servicio.cerrarSesionPorInactividad(u, 30);

        assertThat(auditoria.recientes(10))
                .extracting(RonaldmichaRegistroAuditoria::accion)
                .containsExactlyInAnyOrder("INICIO_SESION", "INICIO_SESION_FALLIDO", "CIERRE_SESION",
                        "CIERRE_SESION_INACTIVIDAD");
    }

    @Test
    void bloqueaTrasCincoIntentosFallidosYDesbloqueaAlVencer() {
        for (int i = 1; i < 5; i++) {
            assertThatThrownBy(() -> servicio.autenticar("40000002", "mal", TRABAJADOR))
                    .hasMessage("Usuario o contraseña incorrectos.");
        }
        assertThatThrownBy(() -> servicio.autenticar("40000002", "mal", TRABAJADOR))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class)
                .hasMessageContaining("5 intentos fallidos").hasMessageContaining("bloqueada temporalmente");
        assertThatThrownBy(() -> servicio.autenticar("40000002", "123456", TRABAJADOR))
                .hasMessageContaining("10:15");
        assertThat(auditoria.recientes(3)).extracting(RonaldmichaRegistroAuditoria::accion)
                .contains("USUARIO_BLOQUEADO", "INICIO_SESION_BLOQUEADO");

        reloj.adelantar(Duration.ofMinutes(15));
        assertThat(servicio.autenticar("40000002", "123456", TRABAJADOR).rol()).isEqualTo(RonaldmichaRol.TRABAJADOR);
    }

    @Test
    void unIngresoCorrectoReiniciaLosIntentos() {
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> servicio.autenticar("admin", "mal", ADMINISTRADOR));
        }
        servicio.autenticar("admin", "admin123", ADMINISTRADOR);
        assertThatThrownBy(() -> servicio.autenticar("admin", "mal", ADMINISTRADOR))
                .hasMessage("Usuario o contraseña incorrectos.");
    }

    @Test
    void elAdministradorCreaUnUsuarioConClaveTemporalQueSeCambiaAlIngresar() {
        RonaldmichaUsuario admin = servicio.autenticar("admin", "admin123", ADMINISTRADOR);
        long ana = idDni("40000005");
        ClaveTemporal creado = servicio.crear(admin, new NuevoUsuario("ignorado", RonaldmichaRol.TRABAJADOR, ana));

        assertThat(creado.cuenta().usuario()).isEqualTo("40000005");
        assertThat(creado.cuenta().debeCambiarClave()).isTrue();
        assertThat(servicio.validarClave(null, creado.clave())).isEmpty();
        assertThatThrownBy(() -> servicio.crear(admin, new NuevoUsuario(null, RonaldmichaRol.TRABAJADOR, ana)))
                .isInstanceOf(RonaldmichaValidacionException.class).hasMessageContaining("ya tiene un usuario");
        assertThatThrownBy(() -> servicio.crear(admin, new NuevoUsuario("ADMIN", RonaldmichaRol.ADMIN, null)))
                .hasMessageContaining("Ya existe");
        assertThatThrownBy(() -> servicio.crear(admin, new NuevoUsuario("x y", RonaldmichaRol.APROBADOR, null)))
                .hasMessageContaining("3 a 40 caracteres");

        RonaldmichaUsuario nuevo = servicio.autenticar("40000005", creado.clave(), TRABAJADOR);
        assertThat(servicio.debeCambiarClave(nuevo)).isTrue();
        assertThatThrownBy(() -> servicio.cambiarClave(nuevo, creado.clave(), "corta", "corta"))
                .isInstanceOf(RonaldmichaValidacionException.class).hasMessageContaining("al menos 8");
        assertThatThrownBy(() -> servicio.cambiarClave(nuevo, creado.clave(), "soloLetras", "soloLetras"))
                .hasMessageContaining("letras y números");
        assertThatThrownBy(() -> servicio.cambiarClave(nuevo, "otra", "Planilla2026", "Planilla2026"))
                .hasMessageContaining("actual no es correcta");
        servicio.cambiarClave(nuevo, creado.clave(), "Planilla2026", "Planilla2026");
        assertThat(servicio.debeCambiarClave(nuevo)).isFalse();
        assertThat(servicio.autenticar("40000005", "Planilla2026", TRABAJADOR)).isNotNull();
        assertThat(auditoria.recientes(10)).extracting(RonaldmichaRegistroAuditoria::accion).contains("USUARIO_CREADO", "CLAVE_CAMBIADA");
        assertThat(auditoria.recientes(20)).noneMatch(r -> r.detalle() != null && r.detalle().contains(creado.clave()));
    }

    @Test
    void desactivarRestablecerYDesbloquear() {
        RonaldmichaUsuario admin = servicio.autenticar("admin", "admin123", ADMINISTRADOR);
        RonaldmichaCuentaUsuario carlos = cuenta("40000002");

        assertThatThrownBy(() -> servicio.cambiarActivo(admin, admin.id(), false))
                .hasMessageContaining("propio usuario");
        servicio.cambiarActivo(admin, carlos.id(), false);
        assertThatThrownBy(() -> servicio.autenticar("40000002", "123456", TRABAJADOR))
                .hasMessage("Usuario o contraseña incorrectos.");
        servicio.cambiarActivo(admin, carlos.id(), true);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> servicio.autenticar("40000002", "mal", TRABAJADOR));
        }
        assertThat(servicio.bloqueada(cuenta("40000002"))).isTrue();
        servicio.desbloquear(admin, carlos.id());
        assertThat(servicio.bloqueada(cuenta("40000002"))).isFalse();

        ClaveTemporal temporal = servicio.restablecerClave(admin, carlos.id());
        assertThatThrownBy(() -> servicio.autenticar("40000002", "123456", TRABAJADOR));
        assertThat(servicio.debeCambiarClave(servicio.autenticar("40000002", temporal.clave(), TRABAJADOR))).isTrue();
        assertThatThrownBy(() -> servicio.restablecerClave(admin, admin.id())).hasMessageContaining("Cambiar contraseña");
    }

    @Test
    void soloElAdministradorGestionaUsuarios() {
        RonaldmichaUsuario aprobador = servicio.autenticar("aprobador", "aprob123", ADMINISTRADOR);
        RonaldmichaUsuario trabajador = servicio.autenticar("40000002", "123456", TRABAJADOR);

        assertThat(servicio.listar(aprobador)).extracting(RonaldmichaCuentaUsuario::usuario)
                .containsExactly("admin", "aprobador", "40000002");
        assertThatThrownBy(() -> servicio.listar(trabajador)).isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.crear(aprobador, new NuevoUsuario("jefe", RonaldmichaRol.ADMIN, null)))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.restablecerClave(aprobador, cuenta("40000002").id()))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.cambiarActivo(trabajador, cuenta("admin").id(), false))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    private RonaldmichaCuentaUsuario cuenta(String usuario) {
        RonaldmichaUsuario admin = new RonaldmichaUsuario(1L, "admin", "", RonaldmichaRol.ADMIN, null, true);
        return servicio.listar(admin).stream().filter(c -> c.usuario().equals(usuario)).findFirst().orElseThrow();
    }

    private long idDni(String dni) {
        return trabajadores.listarTodos().stream().filter(t -> t.numDoc().equals(dni)).findFirst().orElseThrow().id();
    }
}
