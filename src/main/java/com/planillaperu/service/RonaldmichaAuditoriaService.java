package com.planillaperu.service;

import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaPermiso;
import com.planillaperu.domain.RonaldmichaRegistroAuditoria;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.export.RonaldmichaTablaExcel;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class RonaldmichaAuditoriaService {

    public static final int MAXIMO_CONSULTA = 2000;

    public record Filtro(LocalDate desde, LocalDate hasta, String usuario, String accion) {

        public Filtro {
            usuario = usuario == null || usuario.isBlank() ? null : usuario;
            accion = accion == null || accion.isBlank() ? null : accion;
        }

        String texto() {
            List<String> partes = new ArrayList<>();
            partes.add(desde == null && hasta == null ? "Todas las fechas"
                    : "Del " + (desde == null ? "inicio" : RonaldmichaFormatos.fecha(desde)) + " al "
                    + (hasta == null ? "día de hoy" : RonaldmichaFormatos.fecha(hasta)));
            if (usuario != null) {
                partes.add("Usuario " + usuario);
            }
            if (accion != null) {
                partes.add("Acción " + accion);
            }
            return String.join(" · ", partes);
        }
    }

    private final RonaldmichaAuditoriaRepository repositorio;
    private final Clock reloj;
    private final List<Consumer<RonaldmichaRegistroAuditoria>> oyentes = new CopyOnWriteArrayList<>();

    public RonaldmichaAuditoriaService(RonaldmichaAuditoriaRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    public void registrar(String usuario, String accion, String detalle) {
        LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
        RonaldmichaRegistroAuditoria r = new RonaldmichaRegistroAuditoria(null, usuario, ahora, accion, detalle);
        repositorio.insertar(r);
        for (Consumer<RonaldmichaRegistroAuditoria> o : oyentes) {
            o.accept(r);
        }
    }

    public void suscribir(Consumer<RonaldmichaRegistroAuditoria> oyente) {
        oyentes.add(oyente);
    }

    public List<RonaldmichaRegistroAuditoria> recientes(int limite) {
        return repositorio.listarRecientes(limite);
    }

    public List<RonaldmichaRegistroAuditoria> buscar(RonaldmichaUsuario usuario, Filtro filtro) {
        RonaldmichaPermisos.exigir(usuario, RonaldmichaPermiso.VER_AUDITORIA, "ver la auditoría");
        if (filtro.desde() != null && filtro.hasta() != null && filtro.hasta().isBefore(filtro.desde())) {
            throw new RonaldmichaValidacionException("La fecha «hasta» no puede ser anterior a «desde».");
        }
        return repositorio.buscar(filtro.desde(), filtro.hasta(), filtro.usuario(), filtro.accion(), MAXIMO_CONSULTA);
    }

    public List<String> usuariosRegistrados(RonaldmichaUsuario usuario) {
        RonaldmichaPermisos.exigir(usuario, RonaldmichaPermiso.VER_AUDITORIA, "ver la auditoría");
        return repositorio.distintos(true);
    }

    public List<String> acciones(RonaldmichaUsuario usuario) {
        RonaldmichaPermisos.exigir(usuario, RonaldmichaPermiso.VER_AUDITORIA, "ver la auditoría");
        return repositorio.distintos(false);
    }

    public byte[] exportarExcel(RonaldmichaUsuario usuario, Filtro filtro) {
        List<RonaldmichaRegistroAuditoria> registros = buscar(usuario, filtro);
        List<List<Object>> filas = new ArrayList<>();
        for (RonaldmichaRegistroAuditoria r : registros) {
            filas.add(List.of(RonaldmichaFormatos.fechaHora(r.fechaHora()), r.usuario(), r.accion(),
                    r.detalle() == null ? "" : r.detalle()));
        }
        byte[] excel = RonaldmichaTablaExcel.crear(List.of(new RonaldmichaTablaExcel.Hoja("Auditoría", "Auditoría · " + filtro.texto(),
                List.of("Fecha y hora", "Usuario", "Acción", "Detalle"), filas, false)));
        registrar(usuario.usuario(), "AUDITORIA_EXPORTADA", filtro.texto() + " · " + registros.size()
                + " registros · Excel");
        return excel;
    }
}
