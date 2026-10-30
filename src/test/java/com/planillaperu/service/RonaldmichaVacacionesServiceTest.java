package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaAprobacionPendiente;
import com.planillaperu.domain.RonaldmichaEstadoVacacion;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaTipoVacacion;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.domain.RonaldmichaVacacion;
import com.planillaperu.export.RonaldmichaSolicitudVacaciones;
import com.planillaperu.repository.RonaldmichaAprobacionRepository;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaEmpresaRepository;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.repository.RonaldmichaVacacionRepository;
import com.planillaperu.service.RonaldmichaVacacionesService.Detalle;
import com.planillaperu.service.RonaldmichaVacacionesService.FilaVacaciones;
import com.planillaperu.service.RonaldmichaVacacionesService.Registro;
import com.planillaperu.service.RonaldmichaVacacionesService.Solicitud;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaVacacionesServiceTest {

    private RonaldmichaAuditoriaService auditoria;
    private RonaldmichaVacacionesService servicio;
    private RonaldmichaAprobacionService aprobaciones;
    private RonaldmichaPeriodoRepository periodos;
    private RonaldmichaTrabajadorRepository trabajadores;
    private RonaldmichaEmpresaService empresa;
    private RonaldmichaUsuario admin;
    private RonaldmichaUsuario aprobador;
    private RonaldmichaUsuario carlosUsuario;

    @TempDir
    Path carpeta;

    @BeforeEach
    void preparar() {
        RonaldmichaDatabase db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
        auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        trabajadores = new RonaldmichaTrabajadorRepository(db);
        periodos = new RonaldmichaPeriodoRepository(db);
        empresa = new RonaldmichaEmpresaService(new RonaldmichaEmpresaRepository(db), auditoria);
        servicio = new RonaldmichaVacacionesService(trabajadores, new RonaldmichaVacacionRepository(db), new RonaldmichaPeriodoService(periodos, reloj),
                empresa, auditoria, reloj);
        aprobaciones = new RonaldmichaAprobacionService(new RonaldmichaAprobacionRepository(db), auditoria, reloj);
        admin = new RonaldmichaUsuario(1L, "admin", "", RonaldmichaRol.ADMIN, id("40000001"), true);
        aprobador = new RonaldmichaUsuario(3L, "aprobador", "", RonaldmichaRol.APROBADOR, id("40000003"), true);
        carlosUsuario = new RonaldmichaUsuario(2L, "40000002", "", RonaldmichaRol.TRABAJADOR, id("40000002"), true);
    }

    @Test
    void recordDeLosDatosDeDemo() {
        List<FilaVacaciones> filas = servicio.listar(aprobador);

        assertThat(filas).hasSize(7);
        FilaVacaciones rosa = fila(filas, "40000001");
        assertThat(rosa.resumen().mesesCumplidos()).isEqualTo(91);
        assertThat(rosa.resumen().derecho()).isEqualTo(210);
        assertThat(rosa.resumen().gozadas()).isEqualTo(180);
        assertThat(rosa.resumen().vendidas()).isEqualTo(15);
        assertThat(rosa.resumen().pendiente()).isEqualTo(15);
        assertThat(rosa.resumen().planificado()).isEqualTo(15);
        assertThat(rosa.disponible()).isZero();
        FilaVacaciones carlos = fila(filas, "40000002");
        assertThat(carlos.resumen().pendiente()).isEqualTo(30);
        assertThat(carlos.enAprobacion()).isEqualTo(10);
        assertThat(carlos.disponible()).isEqualTo(20);
        assertThat(fila(filas, "40000004").resumen().derecho()).isEqualTo(60);

        assertThat(servicio.alertasIndemnizacion(admin)).extracting(f -> f.trabajador().numDoc())
                .containsExactlyInAnyOrder("40000003", "40000005");
        assertThat(fila(filas, "40000003").resumen().periodosSinGozar()).isEqualTo(3);
        assertThatThrownBy(() -> servicio.listar(carlosUsuario)).isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    @Test
    void solicitudDelPortalQuedaPendienteYLaApruebaElAdministrador() {
        RonaldmichaVacacion v = servicio.registrar(carlosUsuario, id("40000002"), goce("2026-11-02", "2026-11-11"));

        assertThat(v.estado()).isEqualTo(RonaldmichaEstadoVacacion.PENDIENTE);
        RonaldmichaAprobacionPendiente pendiente = aprobaciones.pendientes(admin).stream()
                .filter(a -> a.detalle().contains("02/11/2026")).findFirst().orElseThrow();
        assertThat(pendiente.tipo()).isEqualTo("Vacaciones");
        assertThat(aprobaciones.puedeResolver(pendiente, admin)).isTrue();
        assertThat(aprobaciones.puedeResolver(pendiente, carlosUsuario)).isFalse();
        assertThat(servicio.miDetalle(carlosUsuario).fila().enAprobacion()).isEqualTo(20);

        aprobaciones.aprobar(pendiente.id(), admin);

        Detalle d = servicio.miDetalle(carlosUsuario);
        assertThat(registro(d, v.id()).estado()).isEqualTo(RonaldmichaEstadoVacacion.PROGRAMADO);
        assertThat(d.fila().resumen().planificado()).isEqualTo(10);
        assertThat(d.fila().enAprobacion()).isEqualTo(10);

        RonaldmichaAprobacionPendiente demo = aprobaciones.pendientes(admin).stream()
                .filter(a -> a.tipo().equals("Vacaciones")).findFirst().orElseThrow();
        aprobaciones.rechazar(demo.id(), "Cierre de inventario en enero", aprobador);
        Detalle despues = servicio.miDetalle(carlosUsuario);
        assertThat(despues.fila().enAprobacion()).isZero();
        assertThat(despues.registros()).anyMatch(r -> r.estado() == RonaldmichaEstadoVacacion.RECHAZADO);
        assertThat(acciones()).contains("VACACION_SOLICITADA", "APROBACION_APROBADA", "APROBACION_RECHAZADA");

        assertThatThrownBy(() -> servicio.detalle(carlosUsuario, id("40000001")))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.registrar(aprobador, id("40000002"), goce("2026-11-16", "2026-11-20")))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    @Test
    void validaciones() {
        long carlos = id("40000002");
        long lucia = id("40000003");
        assertThat(servicio.validar(carlosUsuario, carlos, null, venta("2026-11-02", "2026-11-06", false)))
                .anyMatch(e -> e.contains("acuerdo escrito"));
        assertThat(servicio.validar(carlosUsuario, carlos, null, goce("2027-01-10", "2027-01-15")))
                .anyMatch(e -> e.contains("Se cruza"));
        assertThat(servicio.validar(carlosUsuario, carlos, null, goce("2026-10-01", "2026-10-05")))
                .anyMatch(e -> e.contains("posterior a hoy"));
        assertThat(servicio.validar(admin, carlos, null, goce("2026-12-10", "2026-12-01")))
                .anyMatch(e -> e.contains("anterior a la fecha desde"));
        assertThat(servicio.validar(admin, id("40000004"), null, goce("2026-11-02", "2026-11-21")))
                .anyMatch(e -> e.contains("solo hay 15 disponibles"));
        assertThat(servicio.validar(admin, id("40000001"), null, goce("2027-02-01", "2027-02-05")))
                .anyMatch(e -> e.contains("no tiene días pendientes"));

        servicio.registrar(admin, lucia, venta("2027-02-01", "2027-02-10", true));
        assertThat(servicio.validar(admin, lucia, null, venta("2027-03-01", "2027-03-06", true)))
                .anyMatch(e -> e.contains("15 días por año"));
        assertThat(servicio.validar(admin, lucia, null, venta("2027-03-01", "2027-03-05", true))).isEmpty();

        periodos.cerrar(YearMonth.of(2026, 11), "admin", LocalDateTime.of(2026, 12, 1, 9, 0));
        assertThat(servicio.validar(admin, lucia, null, goce("2026-11-02", "2026-11-11")))
                .anyMatch(e -> e.contains("noviembre 2026 está cerrada"));
        assertThatThrownBy(() -> servicio.registrar(admin, lucia, goce("2026-11-02", "2026-11-11")))
                .isInstanceOf(RonaldmichaValidacionException.class);
    }

    @Test
    void editarSoloProgramadosYAnularSinBorrar() {
        Detalle rosa = servicio.detalle(admin, id("40000001"));
        int total = rosa.registros().size();
        Registro programado = rosa.registros().stream().filter(r -> r.estado() == RonaldmichaEstadoVacacion.PROGRAMADO)
                .findFirst().orElseThrow();
        Registro gozado = rosa.registros().stream().filter(r -> r.estado() == RonaldmichaEstadoVacacion.GOZADO)
                .findFirst().orElseThrow();
        assertThat(programado.editable()).isTrue();
        assertThat(gozado.editable()).isFalse();

        servicio.editar(admin, programado.vacacion().id(), goce("2026-12-21", "2026-12-30"));
        assertThat(registro(servicio.detalle(admin, id("40000001")), programado.vacacion().id()).vacacion().dias())
                .isEqualTo(10);
        assertThatThrownBy(() -> servicio.editar(admin, gozado.vacacion().id(), goce("2027-01-04", "2027-01-08")))
                .isInstanceOf(RonaldmichaValidacionException.class);

        servicio.anular(admin, programado.vacacion().id());
        Detalle despues = servicio.detalle(admin, id("40000001"));
        assertThat(despues.registros()).hasSize(total);
        assertThat(registro(despues, programado.vacacion().id()).estado()).isEqualTo(RonaldmichaEstadoVacacion.ANULADO);
        assertThat(despues.fila().resumen().planificado()).isZero();
        assertThatThrownBy(() -> servicio.anular(admin, programado.vacacion().id()))
                .isInstanceOf(RonaldmichaValidacionException.class);

        Registro solicitud = servicio.miDetalle(carlosUsuario).registros().stream()
                .filter(r -> r.estado() == RonaldmichaEstadoVacacion.PENDIENTE).findFirst().orElseThrow();
        assertThat(solicitud.anulable()).isTrue();
        servicio.anular(carlosUsuario, solicitud.vacacion().id());
        assertThat(aprobaciones.pendientes(admin)).noneMatch(a -> a.tipo().equals("Vacaciones"));
        assertThat(acciones()).contains("VACACION_EDITADA", "VACACION_ANULADA");
    }

    @Test
    void adjuntosYSolicitudEnWordYPdf() {
        Registro r = servicio.miDetalle(carlosUsuario).registros().getFirst();
        long id = r.vacacion().id();
        assertThatThrownBy(() -> servicio.adjuntar(carlosUsuario, id, "nota.txt", new byte[]{1}))
                .isInstanceOf(RonaldmichaValidacionException.class);
        byte[] pdf = "%PDF-1.4 prueba".getBytes(StandardCharsets.US_ASCII);
        servicio.adjuntar(carlosUsuario, id, "certificado.pdf", pdf);
        assertThat(registro(servicio.miDetalle(carlosUsuario), id).vacacion().adjuntoNombre()).isEqualTo("certificado.pdf");
        assertThat(servicio.adjunto(admin, id)).isEqualTo(pdf);
        servicio.quitarAdjunto(carlosUsuario, id);
        assertThat(registro(servicio.miDetalle(carlosUsuario), id).vacacion().tieneAdjunto()).isFalse();

        assertThat(new String(servicio.solicitudPdf(carlosUsuario, id), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(new String(servicio.solicitudWord(admin, id), 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
        long rosa = servicio.detalle(admin, id("40000001")).registros().getFirst().vacacion().id();
        assertThatThrownBy(() -> servicio.solicitudPdf(carlosUsuario, rosa)).isInstanceOf(RonaldmichaAccesoDenegadoException.class);

        RonaldmichaSolicitudVacaciones s = servicio.solicitud(empresa.obtener(), r.vacacion());
        assertThat(s.asunto()).isEqualTo("Solicitud de vacaciones");
        assertThat(s.parrafos().getFirst()).contains("Carlos Alberto Flores Huamán", "DNI N.° 40000002",
                "10 días calendario", "04/01/2027", "13/01/2027", "D.Leg. 713");
        assertThat(s.resumen()).anyMatch(d -> d.etiqueta().equals("Saldo después de la solicitud")
                && d.valor().equals("20 días"));
        assertThat(acciones()).contains("VACACION_ADJUNTO", "VACACION_ADJUNTO_QUITADO", "VACACION_DOCUMENTO");
    }

    private static Solicitud goce(String desde, String hasta) {
        return new Solicitud(RonaldmichaTipoVacacion.GOCE, LocalDate.parse(desde), LocalDate.parse(hasta), null, false);
    }

    private static Solicitud venta(String desde, String hasta, boolean acuerdo) {
        return new Solicitud(RonaldmichaTipoVacacion.VENTA, LocalDate.parse(desde), LocalDate.parse(hasta), null, acuerdo);
    }

    private long id(String dni) {
        return trabajadores.listarTodos().stream().filter(t -> t.numDoc().equals(dni)).findFirst().orElseThrow().id();
    }

    private static FilaVacaciones fila(List<FilaVacaciones> filas, String dni) {
        return filas.stream().filter(f -> f.trabajador().numDoc().equals(dni)).findFirst().orElseThrow();
    }

    private static Registro registro(Detalle d, long id) {
        return d.registros().stream().filter(r -> r.vacacion().id() == id).findFirst().orElseThrow();
    }

    private List<String> acciones() {
        return auditoria.recientes(50).stream().map(r -> r.accion()).toList();
    }
}
