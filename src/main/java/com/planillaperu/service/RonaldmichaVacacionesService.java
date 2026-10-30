package com.planillaperu.service;

import com.planillaperu.calc.RonaldmichaVacacionesCalculator;
import com.planillaperu.calc.RonaldmichaVacacionesCalculator.Resumen;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.domain.RonaldmichaEstadoVacacion;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaSituacionPeriodo;
import com.planillaperu.domain.RonaldmichaTipoVacacion;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.domain.RonaldmichaVacacion;
import com.planillaperu.export.RonaldmichaConstanciaBeneficio.Dato;
import com.planillaperu.export.RonaldmichaSolicitudVacaciones;
import com.planillaperu.export.doc.RonaldmichaDocumentModel;
import com.planillaperu.export.doc.RonaldmichaPdfRenderer;
import com.planillaperu.export.doc.RonaldmichaPlantillas;
import com.planillaperu.export.doc.RonaldmichaWordRenderer;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.repository.RonaldmichaVacacionRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class RonaldmichaVacacionesService {

    public static final int OBSERVACION_MAXIMA = 300;
    public static final int ADJUNTO_MAXIMO_BYTES = 5 * 1024 * 1024;
    public static final Set<String> EXTENSIONES_ADJUNTO = Set.of("pdf", "png", "jpg", "jpeg");
    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy",
            RonaldmichaFormatos.LOCALE_PE);

    public record FilaVacaciones(RonaldmichaTrabajador trabajador, Resumen resumen, int enAprobacion) {

        public int disponible() {
            return Math.max(0, resumen.pendiente() - resumen.planificado() - enAprobacion);
        }
    }

    public record Registro(RonaldmichaVacacion vacacion, RonaldmichaEstadoVacacion estado, boolean editable, boolean anulable,
                           boolean adjuntable) {
    }

    public record Detalle(FilaVacaciones fila, List<Registro> registros) {
    }

    public record Solicitud(RonaldmichaTipoVacacion tipo, LocalDate desde, LocalDate hasta, String observacion,
                            boolean acuerdoEscrito) {

        public int dias() {
            return RonaldmichaVacacionesCalculator.diasCalendario(desde, hasta);
        }
    }

    private final RonaldmichaTrabajadorRepository trabajadores;
    private final RonaldmichaVacacionRepository repositorio;
    private final RonaldmichaPeriodoService periodos;
    private final RonaldmichaEmpresaService empresa;
    private final RonaldmichaAuditoriaService auditoria;
    private final Clock reloj;

    public RonaldmichaVacacionesService(RonaldmichaTrabajadorRepository trabajadores, RonaldmichaVacacionRepository repositorio, RonaldmichaPeriodoService periodos,
                             RonaldmichaEmpresaService empresa, RonaldmichaAuditoriaService auditoria, Clock reloj) {
        this.trabajadores = trabajadores;
        this.repositorio = repositorio;
        this.periodos = periodos;
        this.empresa = empresa;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    public record Rango(LocalDate desde, LocalDate hasta) {
    }

    public Optional<Rango> goceEnMes(long trabajadorId, YearMonth mes) {
        LocalDate inicio = mes.atDay(1);
        LocalDate fin = mes.atEndOfMonth();
        LocalDate desde = null;
        LocalDate hasta = null;
        for (RonaldmichaVacacion v : repositorio.listar(trabajadorId)) {
            if (v.tipo() != RonaldmichaTipoVacacion.GOCE || !v.estado().cuenta() || v.desde() == null || v.hasta() == null
                    || v.hasta().isBefore(inicio) || v.desde().isAfter(fin)) {
                continue;
            }
            LocalDate d = v.desde().isBefore(inicio) ? inicio : v.desde();
            LocalDate h = v.hasta().isAfter(fin) ? fin : v.hasta();
            desde = desde == null || d.isBefore(desde) ? d : desde;
            hasta = hasta == null || h.isAfter(hasta) ? h : hasta;
        }
        return desde == null ? Optional.empty() : Optional.of(new Rango(desde, hasta));
    }

    public List<FilaVacaciones> listar(RonaldmichaUsuario usuario) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "ver las vacaciones del personal");
        Map<Long, List<RonaldmichaVacacion>> porTrabajador = repositorio.listarTodos().stream()
                .collect(Collectors.groupingBy(RonaldmichaVacacion::trabajadorId));
        LocalDate hoy = hoy();
        return trabajadores.listarTodos().stream()
                .filter(t -> t.activoEn(hoy))
                .map(t -> fila(t, porTrabajador.getOrDefault(t.id(), List.of())))
                .toList();
    }

    public Detalle detalle(RonaldmichaUsuario usuario, long trabajadorId) {
        exigirVer(usuario, trabajadorId);
        RonaldmichaTrabajador t = trabajador(trabajadorId);
        List<RonaldmichaVacacion> registros = repositorio.listar(trabajadorId);
        LocalDate hoy = hoy();
        List<Registro> lista = registros.stream().map(v -> registro(usuario, v, hoy)).toList();
        return new Detalle(fila(t, registros), lista);
    }

    public Detalle miDetalle(RonaldmichaUsuario usuario) {
        if (usuario == null || usuario.trabajadorId() == null) {
            throw new RonaldmichaAccesoDenegadoException("Su usuario no está vinculado a un trabajador.");
        }
        return detalle(usuario, usuario.trabajadorId());
    }

    public List<FilaVacaciones> alertasIndemnizacion(RonaldmichaUsuario usuario) {
        return listar(usuario).stream().filter(f -> f.resumen().alertaIndemnizacion()).toList();
    }

    public List<String> validar(RonaldmichaUsuario usuario, long trabajadorId, Long vacacionId, Solicitud s) {
        List<String> errores = new ArrayList<>();
        RonaldmichaTrabajador t = trabajador(trabajadorId);
        if (s.tipo() == null) {
            errores.add("Elija si planifica un goce o compra días (venta).");
        }
        if (s.desde() == null || s.hasta() == null) {
            errores.add("Indique las fechas desde y hasta.");
            return errores;
        }
        if (s.hasta().isBefore(s.desde())) {
            errores.add("La fecha hasta no puede ser anterior a la fecha desde.");
            return errores;
        }
        LocalDate hoy = hoy();
        if (esTrabajador(usuario) && !s.desde().isAfter(hoy)) {
            errores.add("Las vacaciones se solicitan con fecha de inicio posterior a hoy.");
        }
        if (s.desde().isBefore(t.fechaIngreso())) {
            errores.add("La fecha desde es anterior al ingreso del trabajador (" + RonaldmichaFormatos.fecha(t.fechaIngreso()) + ").");
        }
        if (t.fechaCese() != null && s.hasta().isAfter(t.fechaCese())) {
            errores.add("La fecha hasta es posterior al cese del trabajador (" + RonaldmichaFormatos.fecha(t.fechaCese()) + ").");
        }
        if (s.observacion() != null && s.observacion().trim().length() > OBSERVACION_MAXIMA) {
            errores.add("La observación no puede pasar de " + OBSERVACION_MAXIMA + " caracteres.");
        }
        errores.addAll(periodosCerrados(s.desde(), s.hasta()));

        List<RonaldmichaVacacion> otros = repositorio.listar(trabajadorId).stream()
                .filter(v -> !Objects.equals(v.id(), vacacionId)).toList();
        FilaVacaciones fila = fila(t, otros);
        int dias = s.dias();
        if (s.tipo() == RonaldmichaTipoVacacion.GOCE) {
            otros.stream()
                    .filter(v -> v.tipo() == RonaldmichaTipoVacacion.GOCE && (v.estado().cuenta() || v.estado() == RonaldmichaEstadoVacacion.PENDIENTE))
                    .filter(v -> v.desde() != null && !v.desde().isAfter(s.hasta()) && !v.hasta().isBefore(s.desde()))
                    .findFirst()
                    .ifPresent(v -> errores.add("Se cruza con otro goce del " + RonaldmichaFormatos.fecha(v.desde()) + " al "
                            + RonaldmichaFormatos.fecha(v.hasta()) + "."));
        }
        if (fila.disponible() <= 0) {
            errores.add("El trabajador no tiene días pendientes disponibles.");
        } else if (dias > fila.disponible()) {
            errores.add("Son " + dias + " días y solo hay " + fila.disponible()
                    + " disponibles (pendientes menos planificados y en aprobación).");
        }
        if (s.tipo() == RonaldmichaTipoVacacion.VENTA) {
            if (!s.acuerdoEscrito()) {
                errores.add("La venta de vacaciones requiere acuerdo escrito.");
            }
            int ventaPendiente = otros.stream()
                    .filter(v -> v.tipo() == RonaldmichaTipoVacacion.VENTA && v.estado() == RonaldmichaEstadoVacacion.PENDIENTE)
                    .mapToInt(RonaldmichaVacacion::dias).sum();
            int maximo = Math.max(0, fila.resumen().ventaDisponible() - ventaPendiente);
            int anio = s.desde().getYear();
            int vendidasDelAnio = otros.stream()
                    .filter(v -> v.tipo() == RonaldmichaTipoVacacion.VENTA && v.desde() != null && v.desde().getYear() == anio)
                    .filter(v -> v.estado().cuenta() || v.estado() == RonaldmichaEstadoVacacion.PENDIENTE)
                    .mapToInt(RonaldmichaVacacion::dias).sum();
            if (vendidasDelAnio + dias > RonaldmichaVacacionesCalculator.VENTA_MAXIMA_ANUAL) {
                errores.add("La venta no puede pasar de " + RonaldmichaVacacionesCalculator.VENTA_MAXIMA_ANUAL + " días por año: en "
                        + anio + " ya hay " + vendidasDelAnio + " días vendidos o por aprobar.");
            } else if (dias > maximo) {
                errores.add("La venta no puede pasar de " + RonaldmichaVacacionesCalculator.VENTA_MAXIMA_ANUAL
                        + " días por año de servicio: puede vender hasta " + maximo + " días.");
            }
        }
        return errores;
    }

    public RonaldmichaVacacion registrar(RonaldmichaUsuario usuario, long trabajadorId, Solicitud s) {
        boolean solicitud = esTrabajador(usuario);
        if (solicitud) {
            exigirPropio(usuario, trabajadorId, "solicitar vacaciones de otro trabajador");
        } else {
            RonaldmichaPermisos.exigirAdmin(usuario, "registrar vacaciones");
        }
        exigirValida(usuario, trabajadorId, null, s);
        RonaldmichaTrabajador t = trabajador(trabajadorId);
        RonaldmichaVacacion nueva = new RonaldmichaVacacion(null, trabajadorId, s.tipo(), s.desde(), s.hasta(), s.dias(), limpiar(s.observacion()),
                solicitud ? RonaldmichaEstadoVacacion.PENDIENTE : RonaldmichaEstadoVacacion.PROGRAMADO, ahora(), usuario.usuario(), null,
                s.tipo() == RonaldmichaTipoVacacion.VENTA && s.acuerdoEscrito());
        RonaldmichaVacacion guardada = repositorio.insertar(nueva, solicitud ? descripcion(nueva) : null);
        auditoria.registrar(usuario.usuario(), solicitud ? "VACACION_SOLICITADA" : "VACACION_REGISTRADA",
                "#" + guardada.id() + " · " + descripcion(guardada) + " · " + t.nombreCompleto());
        return guardada;
    }

    public void editar(RonaldmichaUsuario usuario, long vacacionId, Solicitud s) {
        RonaldmichaVacacion v = vacacion(vacacionId);
        if (!registro(usuario, v, hoy()).editable()) {
            throw new RonaldmichaValidacionException("Solo se editan registros Programados o Pendientes de aprobación"
                    + (esTrabajador(usuario) ? " que usted solicitó." : "."));
        }
        errorSi(periodosCerrados(v.desde(), v.hasta()));
        exigirValida(usuario, v.trabajadorId(), vacacionId, s);
        RonaldmichaVacacion editada = new RonaldmichaVacacion(v.id(), v.trabajadorId(), s.tipo(), s.desde(), s.hasta(), s.dias(),
                limpiar(s.observacion()), v.estado(), v.fechaRegistro(), v.usuario(), v.adjuntoNombre(),
                s.tipo() == RonaldmichaTipoVacacion.VENTA && s.acuerdoEscrito());
        repositorio.actualizar(editada, v.estado() == RonaldmichaEstadoVacacion.PENDIENTE ? descripcion(editada) : null);
        auditoria.registrar(usuario.usuario(), "VACACION_EDITADA", "#" + v.id() + " · " + descripcion(v) + " → "
                + descripcion(editada) + " · " + trabajador(v.trabajadorId()).nombreCompleto());
    }

    public void anular(RonaldmichaUsuario usuario, long vacacionId) {
        RonaldmichaVacacion v = vacacion(vacacionId);
        if (!registro(usuario, v, hoy()).anulable()) {
            throw new RonaldmichaValidacionException(esTrabajador(usuario)
                    ? "Solo puede anular sus solicitudes pendientes de aprobación."
                    : "El registro ya está " + v.estado().etiqueta().toLowerCase(Locale.ROOT) + ".");
        }
        errorSi(periodosCerrados(v.desde(), v.hasta()));
        if (!repositorio.anular(vacacionId, usuario.usuario(), ahora(), "Anulada por " + usuario.usuario())) {
            throw new RonaldmichaValidacionException("El registro ya fue anulado o rechazado.");
        }
        auditoria.registrar(usuario.usuario(), "VACACION_ANULADA", "#" + v.id() + " · " + descripcion(v) + " · "
                + trabajador(v.trabajadorId()).nombreCompleto());
    }

    public void adjuntar(RonaldmichaUsuario usuario, long vacacionId, String nombre, byte[] contenido) {
        RonaldmichaVacacion v = vacacion(vacacionId);
        if (!registro(usuario, v, hoy()).adjuntable()) {
            throw new RonaldmichaAccesoDenegadoException("No puede adjuntar archivos a este registro.");
        }
        String extension = nombre == null || !nombre.contains(".") ? ""
                : nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONES_ADJUNTO.contains(extension)) {
            throw new RonaldmichaValidacionException("Adjunte un PDF o una imagen (PNG o JPG).");
        }
        if (contenido == null || contenido.length == 0) {
            throw new RonaldmichaValidacionException("El archivo está vacío.");
        }
        if (contenido.length > ADJUNTO_MAXIMO_BYTES) {
            throw new RonaldmichaValidacionException("El archivo no puede pasar de 5 MB.");
        }
        repositorio.adjuntar(vacacionId, nombre, contenido);
        auditoria.registrar(usuario.usuario(), "VACACION_ADJUNTO", "#" + v.id() + " · " + nombre + " · "
                + trabajador(v.trabajadorId()).nombreCompleto());
    }

    public void quitarAdjunto(RonaldmichaUsuario usuario, long vacacionId) {
        RonaldmichaVacacion v = vacacion(vacacionId);
        if (!registro(usuario, v, hoy()).adjuntable()) {
            throw new RonaldmichaAccesoDenegadoException("No puede quitar el archivo de este registro.");
        }
        if (!v.tieneAdjunto()) {
            return;
        }
        repositorio.adjuntar(vacacionId, null, null);
        auditoria.registrar(usuario.usuario(), "VACACION_ADJUNTO_QUITADO", "#" + v.id() + " · " + v.adjuntoNombre()
                + " · " + trabajador(v.trabajadorId()).nombreCompleto());
    }

    public byte[] adjunto(RonaldmichaUsuario usuario, long vacacionId) {
        RonaldmichaVacacion v = vacacion(vacacionId);
        exigirVer(usuario, v.trabajadorId());
        return repositorio.adjunto(vacacionId)
                .orElseThrow(() -> new RonaldmichaValidacionException("El registro no tiene archivo adjunto."));
    }

    public byte[] solicitudPdf(RonaldmichaUsuario usuario, long vacacionId) {
        RonaldmichaVacacion v = vacacion(vacacionId);
        exigirVer(usuario, v.trabajadorId());
        RonaldmichaEmpresa e = empresa.obtener();
        byte[] pdf = RonaldmichaPdfRenderer.render(modelo(e, v), e, empresa.avisoDatosIncompletos(e), ahora());
        registrarDocumento(usuario, v, "PDF");
        return pdf;
    }

    public byte[] solicitudWord(RonaldmichaUsuario usuario, long vacacionId) {
        RonaldmichaVacacion v = vacacion(vacacionId);
        exigirVer(usuario, v.trabajadorId());
        RonaldmichaEmpresa e = empresa.obtener();
        byte[] word = RonaldmichaWordRenderer.render(modelo(e, v), e, empresa.avisoDatosIncompletos(e), ahora());
        registrarDocumento(usuario, v, "Word");
        return word;
    }

    private RonaldmichaDocumentModel modelo(RonaldmichaEmpresa e, RonaldmichaVacacion v) {
        return RonaldmichaPlantillas.solicitudVacaciones(solicitud(e, v), null).conArchivo("Solicitud de vacaciones #" + v.id());
    }

    RonaldmichaSolicitudVacaciones solicitud(RonaldmichaEmpresa e, RonaldmichaVacacion v) {
        RonaldmichaTrabajador t = trabajador(v.trabajadorId());
        List<RonaldmichaVacacion> otros = repositorio.listar(t.id()).stream().filter(o -> !Objects.equals(o.id(), v.id())).toList();
        Resumen r = fila(t, otros).resumen();
        String ciudad = primero(e.ciudad(), e.provincia(), e.distrito(), e.departamento());
        String fecha = FECHA_LARGA.format(v.fechaRegistro().toLocalDate());
        boolean venta = v.tipo() == RonaldmichaTipoVacacion.VENTA;
        String nombre = t.nombres() + " " + t.apellidos();
        String identificacion = "Yo, " + nombre + ", con " + t.tipoDoc().codigo() + " N.° " + t.numDoc()
                + ", código " + t.codigo() + ", " + valor(t.cargo(), "trabajador") + " del área de "
                + valor(t.area(), "—") + ", con fecha de ingreso " + RonaldmichaFormatos.fecha(t.fechaIngreso()) + ", ";
        List<String> parrafos = new ArrayList<>();
        if (venta) {
            parrafos.add(identificacion + "me dirijo a ustedes para solicitar la venta de " + v.dias()
                    + " días de mi descanso vacacional, correspondientes al periodo del " + RonaldmichaFormatos.fecha(v.desde())
                    + " al " + RonaldmichaFormatos.fecha(v.hasta()) + ", conforme al D.Leg. 1405.");
            parrafos.add("La presente constituye el acuerdo escrito que se requiere para la venta de días de "
                    + "vacaciones, que no supera los " + RonaldmichaVacacionesCalculator.VENTA_MAXIMA_ANUAL + " días por año.");
        } else {
            parrafos.add(identificacion + "me dirijo a ustedes para solicitar el goce de " + v.dias()
                    + " días calendario de mi descanso vacacional, del " + RonaldmichaFormatos.fecha(v.desde()) + " al "
                    + RonaldmichaFormatos.fecha(v.hasta()) + ", conforme al D.Leg. 713 y su reglamento, el D.S. 012-92-TR.");
            parrafos.add("Me reincorporaré a mis labores el " + RonaldmichaFormatos.fecha(v.hasta().plusDays(1)) + ".");
        }
        if (v.observacion() != null && !v.observacion().isBlank()) {
            parrafos.add("Observación: " + v.observacion());
        }
        parrafos.add("Agradeceré se sirvan dar su visto bueno a la presente.");

        List<Dato> resumen = List.of(
                new Dato("Fecha de ingreso", RonaldmichaFormatos.fecha(t.fechaIngreso())),
                new Dato("Tiempo de servicio", tiempo(r)),
                new Dato("Días por año (" + t.regimen().descripcion().toLowerCase(Locale.ROOT) + ")",
                        String.valueOf(r.diasAnuales())),
                new Dato("Derecho acumulado", r.derecho() + " días"),
                new Dato("Gozadas", r.gozadas() + " días"),
                new Dato("Vendidas", r.vendidas() + " días"),
                new Dato("Pendientes", r.pendiente() + " días"),
                new Dato(venta ? "Días a vender" : "Días solicitados", v.dias() + " días"),
                new Dato("Desde", RonaldmichaFormatos.fecha(v.desde())),
                new Dato("Hasta", RonaldmichaFormatos.fecha(v.hasta())),
                new Dato("Saldo después de la solicitud", (r.pendiente() - v.dias()) + " días"));
        return new RonaldmichaSolicitudVacaciones(
                (ciudad == null ? "" : ciudad + ", ") + fecha,
                valor(e.razonSocial(), "Empresa sin registrar"),
                venta ? "Solicitud de venta de vacaciones" : "Solicitud de vacaciones",
                parrafos, resumen, nombre, t.tipoDoc().codigo() + " " + t.numDoc(),
                valor(e.repNombre(), "Representante legal"), e.repCargo());
    }

    private void registrarDocumento(RonaldmichaUsuario usuario, RonaldmichaVacacion v, String formato) {
        auditoria.registrar(usuario.usuario(), "VACACION_DOCUMENTO", "Solicitud #" + v.id() + " (" + formato + ") · "
                + descripcion(v) + " · " + trabajador(v.trabajadorId()).nombreCompleto());
    }

    public static String descripcion(RonaldmichaVacacion v) {
        return v.tipo().etiqueta() + " de " + v.dias() + (v.dias() == 1 ? " día" : " días") + " del "
                + RonaldmichaFormatos.fecha(v.desde()) + " al " + RonaldmichaFormatos.fecha(v.hasta());
    }

    public static String tiempo(Resumen r) {
        return plural(r.aniosCompletos(), "año", "años") + ", " + plural(r.truncoMeses(), "mes", "meses") + " y "
                + plural(r.truncoDias(), "día", "días");
    }

    private static String plural(int n, String uno, String varios) {
        return n + " " + (n == 1 ? uno : varios);
    }

    private FilaVacaciones fila(RonaldmichaTrabajador t, List<RonaldmichaVacacion> lista) {
        Resumen r = RonaldmichaVacacionesCalculator.resumir(t.fechaIngreso(), t.fechaCese(), t.regimen(), lista, hoy());
        int enAprobacion = lista.stream().filter(v -> v.estado() == RonaldmichaEstadoVacacion.PENDIENTE)
                .mapToInt(RonaldmichaVacacion::dias).sum();
        return new FilaVacaciones(t, r, enAprobacion);
    }

    private Registro registro(RonaldmichaUsuario usuario, RonaldmichaVacacion v, LocalDate hoy) {
        RonaldmichaEstadoVacacion estado = RonaldmichaVacacionesCalculator.estadoEfectivo(v, hoy);
        boolean admin = usuario != null && usuario.rol() == RonaldmichaRol.ADMIN;
        boolean propio = esTrabajador(usuario) && Objects.equals(usuario.trabajadorId(), v.trabajadorId());
        boolean editable = (admin && (estado == RonaldmichaEstadoVacacion.PROGRAMADO || estado == RonaldmichaEstadoVacacion.PENDIENTE))
                || (propio && estado == RonaldmichaEstadoVacacion.PENDIENTE);
        boolean anulable = (admin && !estado.cerrado()) || (propio && estado == RonaldmichaEstadoVacacion.PENDIENTE);
        boolean adjuntable = (admin || propio) && !estado.cerrado();
        return new Registro(v, estado, editable, anulable, adjuntable);
    }

    private List<String> periodosCerrados(LocalDate desde, LocalDate hasta) {
        List<String> errores = new ArrayList<>();
        if (desde == null || hasta == null) {
            return errores;
        }
        for (YearMonth m = YearMonth.from(desde); !m.isAfter(YearMonth.from(hasta)); m = m.plusMonths(1)) {
            if (periodos.situacion(m) == RonaldmichaSituacionPeriodo.CERRADO) {
                errores.add("La planilla de " + DateTimeFormatter.ofPattern("MMMM yyyy", RonaldmichaFormatos.LOCALE_PE).format(m)
                        + " está cerrada: sus vacaciones son de solo lectura.");
            }
        }
        return errores;
    }

    private void exigirValida(RonaldmichaUsuario usuario, long trabajadorId, Long vacacionId, Solicitud s) {
        errorSi(validar(usuario, trabajadorId, vacacionId, s));
    }

    private static void errorSi(List<String> errores) {
        if (!errores.isEmpty()) {
            throw new RonaldmichaValidacionException(String.join(" ", errores));
        }
    }

    private void exigirVer(RonaldmichaUsuario usuario, long trabajadorId) {
        if (esTrabajador(usuario)) {
            exigirPropio(usuario, trabajadorId, "ver las vacaciones de otro trabajador");
        } else {
            RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "ver las vacaciones del personal");
        }
    }

    private static void exigirPropio(RonaldmichaUsuario usuario, long trabajadorId, String accion) {
        if (!Objects.equals(usuario.trabajadorId(), trabajadorId)) {
            throw new RonaldmichaAccesoDenegadoException("No puede " + accion + ".");
        }
    }

    private static boolean esTrabajador(RonaldmichaUsuario usuario) {
        return usuario != null && usuario.rol() == RonaldmichaRol.TRABAJADOR;
    }

    private RonaldmichaTrabajador trabajador(long id) {
        return trabajadores.buscarPorId(id).orElseThrow(() -> new RonaldmichaValidacionException("El trabajador no existe."));
    }

    private RonaldmichaVacacion vacacion(long id) {
        return repositorio.buscar(id).orElseThrow(() -> new RonaldmichaValidacionException("El registro de vacaciones no existe."));
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
    }

    private static String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String valor(String s, String porDefecto) {
        return s == null || s.isBlank() ? porDefecto : s;
    }

    private static String primero(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
