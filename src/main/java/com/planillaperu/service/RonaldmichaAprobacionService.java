package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaAprobacion;
import com.planillaperu.domain.RonaldmichaAprobacionPendiente;
import com.planillaperu.domain.RonaldmichaEstadoAprobacion;
import com.planillaperu.domain.RonaldmichaPermiso;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaAprobacionRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RonaldmichaAprobacionService {

    private final RonaldmichaAprobacionRepository repositorio;
    private final RonaldmichaAuditoriaService auditoria;
    private final Clock reloj;

    public RonaldmichaAprobacionService(RonaldmichaAprobacionRepository repositorio, RonaldmichaAuditoriaService auditoria, Clock reloj) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public List<RonaldmichaAprobacionPendiente> pendientes(RonaldmichaUsuario usuario) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "ver las aprobaciones pendientes");
        return repositorio.listarPendientes();
    }

    public Optional<RonaldmichaAprobacion> buscar(long id) {
        return repositorio.buscarPorId(id);
    }

    public void aprobar(long id, RonaldmichaUsuario usuario) {
        RonaldmichaAprobacion a = verificar(id, usuario, "aprobar cambios");
        if (!repositorio.aprobar(a, usuario.usuario(), ahora())) {
            throw new RonaldmichaValidacionException("La solicitud ya fue resuelta por otro usuario.");
        }
        auditoria.registrar(usuario.usuario(), "APROBACION_APROBADA",
                "Solicitud #" + a.id() + " · " + a.tipo() + " · " + a.detalle() + " · registrada por " + a.registradoPor());
    }

    public void rechazar(long id, String motivo, RonaldmichaUsuario usuario) {
        RonaldmichaAprobacion a = verificar(id, usuario, "rechazar cambios");
        if (motivo == null || motivo.isBlank()) {
            throw new RonaldmichaValidacionException("Indique el motivo del rechazo.");
        }
        if (!repositorio.rechazar(a.id(), usuario.usuario(), ahora(), motivo.trim())) {
            throw new RonaldmichaValidacionException("La solicitud ya fue resuelta por otro usuario.");
        }
        auditoria.registrar(usuario.usuario(), "APROBACION_RECHAZADA",
                "Solicitud #" + a.id() + " · " + a.tipo() + " · " + a.detalle() + " · Motivo: " + motivo.trim());
    }

    private RonaldmichaAprobacion verificar(long id, RonaldmichaUsuario usuario, String accion) {
        RonaldmichaAprobacion a = repositorio.buscarPorId(id)
                .orElseThrow(() -> new RonaldmichaValidacionException("La solicitud no existe."));
        RonaldmichaPermisos.exigir(usuario, permiso(a.tipo()), esVacaciones(a.tipo()) ? "resolver solicitudes de vacaciones" : accion);
        if (a.estado() != RonaldmichaEstadoAprobacion.PENDIENTE) {
            throw new RonaldmichaValidacionException("La solicitud ya fue resuelta (" + a.estado().etiqueta().toLowerCase() + ").");
        }
        if (a.registradoPor().equals(usuario.usuario())) {
            throw new RonaldmichaAccesoDenegadoException("No puede resolver un cambio que usted mismo registró.");
        }
        if (a.trabajadorId() != null && Objects.equals(a.trabajadorId(), usuario.trabajadorId())) {
            throw new RonaldmichaAccesoDenegadoException(esVacaciones(a.tipo()) ? "No puede resolver su propia solicitud de vacaciones."
                    : "No puede resolver cambios de su propio sueldo o cuentas.");
        }
        return a;
    }

    public boolean puedeResolver(RonaldmichaAprobacionPendiente a, RonaldmichaUsuario usuario) {
        if (usuario == null || !RonaldmichaMatrizPermisos.tiene(usuario.rol(), permiso(a.tipo()))
                || a.registradoPor().equals(usuario.usuario())) {
            return false;
        }
        return a.trabajadorId() == null || !Objects.equals(a.trabajadorId(), usuario.trabajadorId());
    }

    private static RonaldmichaPermiso permiso(String tipo) {
        return esVacaciones(tipo) ? RonaldmichaPermiso.RESOLVER_VACACIONES : RonaldmichaPermiso.APROBAR_CAMBIOS;
    }

    public static boolean esVacaciones(String tipo) {
        return RonaldmichaAprobacionRepository.TIPO_VACACIONES.equals(tipo);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
    }
}
