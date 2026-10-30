package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaPermiso;
import com.planillaperu.domain.RonaldmichaRegistroAuditoria;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaConfiguracionRepository;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.repository.RonaldmichaUsuarioRepository;
import com.planillaperu.service.RonaldmichaAuditoriaService.Filtro;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaSeguridadServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 15);

    @TempDir
    Path carpeta;

    private RonaldmichaSeguridadService seguridad;
    private RonaldmichaAuditoriaService auditoria;
    private final RonaldmichaUsuario admin = new RonaldmichaUsuario(1L, "admin", "", RonaldmichaRol.ADMIN, null, true);
    private final RonaldmichaUsuario aprobador = new RonaldmichaUsuario(3L, "aprobador", "", RonaldmichaRol.APROBADOR, null, true);
    private final RonaldmichaUsuario trabajador = new RonaldmichaUsuario(2L, "40000002", "", RonaldmichaRol.TRABAJADOR, 2L, true);

    @BeforeEach
    void preparar() {
        RonaldmichaDatabase db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
        auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        RonaldmichaConfiguracionRepository configuracion = new RonaldmichaConfiguracionRepository(db);
        RonaldmichaUsuarioService usuarios = new RonaldmichaUsuarioService(new RonaldmichaUsuarioRepository(db), new RonaldmichaTrabajadorRepository(db),
                configuracion, auditoria, reloj);
        seguridad = new RonaldmichaSeguridadService(configuracion, usuarios, auditoria);
    }

    @Test
    void matrizDePermisosPorRol() {
        var matriz = seguridad.matriz();
        assertThat(matriz.get(RonaldmichaRol.ADMIN)).contains(RonaldmichaPermiso.GESTIONAR_USUARIOS, RonaldmichaPermiso.APROBAR_CAMBIOS,
                RonaldmichaPermiso.CERRAR_PERIODO).doesNotContain(RonaldmichaPermiso.PORTAL_PROPIO);
        assertThat(matriz.get(RonaldmichaRol.APROBADOR)).containsExactlyInAnyOrder(RonaldmichaPermiso.VER_PLANILLAS, RonaldmichaPermiso.GENERAR_REPORTES,
                RonaldmichaPermiso.APROBAR_CAMBIOS, RonaldmichaPermiso.RESOLVER_VACACIONES, RonaldmichaPermiso.VER_AUDITORIA);
        assertThat(matriz.get(RonaldmichaRol.TRABAJADOR)).containsExactly(RonaldmichaPermiso.PORTAL_PROPIO);
        matriz.get(RonaldmichaRol.TRABAJADOR).add(RonaldmichaPermiso.GESTIONAR_USUARIOS);
        assertThat(seguridad.tiene(trabajador, RonaldmichaPermiso.GESTIONAR_USUARIOS)).isFalse();

        seguridad.exigirAlguno(trabajador, List.of(RonaldmichaPermiso.GENERAR_REPORTES, RonaldmichaPermiso.PORTAL_PROPIO), "Reportes");
        assertThatThrownBy(() -> seguridad.exigirAlguno(trabajador, List.of(RonaldmichaPermiso.VER_AUDITORIA), "Usuarios y seguridad"))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class).hasMessageContaining("Usuarios y seguridad");
        assertThatThrownBy(() -> seguridad.exigirAlguno(null, List.of(RonaldmichaPermiso.VER_PLANILLAS), "Inicio"))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    @Test
    void reglasActivasYCierrePorInactividadConfigurable() {
        assertThat(seguridad.reglas()).extracting(RonaldmichaSeguridadService.Regla::titulo).contains("Doble control",
                "Separación de funciones", "Cierre de periodo", "Bloqueo por intentos fallidos", "Cierre por inactividad");
        assertThat(seguridad.minutosInactividad()).isEqualTo(30);
        assertThat(seguridad.reglas()).anyMatch(r -> r.descripcion().contains("Tras 5 intentos"));

        seguridad.cambiarInactividad(admin, 45);
        assertThat(seguridad.minutosInactividad()).isEqualTo(45);
        assertThatThrownBy(() -> seguridad.cambiarInactividad(admin, 2)).isInstanceOf(RonaldmichaValidacionException.class);
        assertThatThrownBy(() -> seguridad.cambiarInactividad(aprobador, 20)).isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        assertThat(auditoria.recientes(1).getFirst().detalle()).contains("30 → 45 minutos");
    }

    @Test
    void auditoriaFiltrableYExportable() throws IOException {
        auditoria.registrar("admin", "TRABAJADOR_ACTUALIZADO", "T0001");
        auditoria.registrar("aprobador", "APROBACION_APROBADA", "Solicitud #1");
        auditoria.registrar("admin", "PLANILLA_CERRADA", "Octubre 2026");

        assertThat(auditoria.buscar(aprobador, new Filtro(HOY, HOY, "admin", null)))
                .extracting(RonaldmichaRegistroAuditoria::accion).containsExactly("PLANILLA_CERRADA", "TRABAJADOR_ACTUALIZADO");
        assertThat(auditoria.buscar(admin, new Filtro(null, null, "", "APROBACION_APROBADA"))).hasSize(1);
        assertThat(auditoria.buscar(admin, new Filtro(HOY.plusDays(1), null, null, null))).isEmpty();
        assertThat(auditoria.usuariosRegistrados(admin)).contains("admin", "aprobador");
        assertThat(auditoria.acciones(admin)).contains("PLANILLA_CERRADA");
        assertThatThrownBy(() -> auditoria.buscar(admin, new Filtro(HOY, HOY.minusDays(1), null, null)))
                .isInstanceOf(RonaldmichaValidacionException.class);
        assertThatThrownBy(() -> auditoria.buscar(trabajador, new Filtro(null, null, null, null)))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);

        byte[] excel = auditoria.exportarExcel(admin, new Filtro(HOY, HOY, "admin", null));
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            var hoja = libro.getSheet("Auditoría");
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).contains("Usuario admin");
            assertThat(hoja.getPhysicalNumberOfRows()).isGreaterThanOrEqualTo(4);
        }
        assertThat(auditoria.recientes(1).getFirst().accion()).isEqualTo("AUDITORIA_EXPORTADA");
    }
}
