package com.planillaperu.service;

import com.planillaperu.calc.RonaldmichaAportePensionCalculator.AportePension;
import com.planillaperu.calc.RonaldmichaRentaQuintaCalculator;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla.Concepto;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla.TipoConcepto;
import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.common.RonaldmichaNumeroALetras;
import com.planillaperu.domain.RonaldmichaDocumentoEmitido;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.domain.RonaldmichaEstadoDocumento;
import com.planillaperu.domain.RonaldmichaLiquidacion;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaSemestreBeneficio;
import com.planillaperu.domain.RonaldmichaSituacionPeriodo;
import com.planillaperu.domain.RonaldmichaTipoBeneficio;
import com.planillaperu.domain.RonaldmichaTipoDocumento;
import com.planillaperu.domain.RonaldmichaTipoPago;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaTrabajadorResumen;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.export.RonaldmichaTablaExcel;
import com.planillaperu.export.doc.RonaldmichaDocumentModel;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Bloque;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Columna;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Firma;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Firmas;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Heading;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Item;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.KeyValue;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Orientacion;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Par;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Paragraph;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Table;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Total;
import com.planillaperu.export.doc.RonaldmichaBoletaPlantilla;
import com.planillaperu.export.doc.RonaldmichaPdfRenderer;
import com.planillaperu.export.doc.RonaldmichaPlantillas;
import com.planillaperu.export.doc.RonaldmichaWordRenderer;
import com.planillaperu.repository.RonaldmichaDocumentoRepository;
import com.planillaperu.repository.RonaldmichaPlanillaRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.service.RonaldmichaBeneficiosService.FilaBeneficio;
import com.planillaperu.service.RonaldmichaPlanillaService.FilaPlanilla;
import com.planillaperu.service.RonaldmichaPlanillaService.PlanillaMes;
import com.planillaperu.service.RonaldmichaReporte.Formato;
import com.planillaperu.service.RonaldmichaVacacionesService.Registro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class RonaldmichaReportesService {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM yyyy", RonaldmichaFormatos.LOCALE_PE);
    private static final DateTimeFormatter AAAAMM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int EMITIDOS_MAXIMO = 300;
    private static final String SIN_CUENTA = "Sin cuenta";
    private static final int LONGITUD_CUSPP = 12;

    public record Parametros(YearMonth mes, Long trabajadorId, boolean todos, boolean dosCopias) {

        public Parametros(YearMonth mes, Long trabajadorId, boolean todos) {
            this(mes, trabajadorId, todos, true);
        }
    }

    private static final List<RonaldmichaReporte> MIS_DOCUMENTOS = List.of(RonaldmichaReporte.BOLETA_PAGO, RonaldmichaReporte.GRATIFICACION,
            RonaldmichaReporte.CTS, RonaldmichaReporte.RENTA_QUINTA, RonaldmichaReporte.CONSTANCIA_TRABAJO, RonaldmichaReporte.SOLICITUD_VACACIONES);

    public record Archivo(String nombre, byte[] contenido, List<String> numeros) {
    }

    public record Envio(int registrados, int enviados, List<String> sinCorreo, String mensaje) {
    }

    private record Pieza(RonaldmichaTrabajador trabajador, Function<String, RonaldmichaDocumentModel> modelo) {
    }

    private final RonaldmichaTrabajadorRepository trabajadores;
    private final RonaldmichaPlanillaService planilla;
    private final RonaldmichaBeneficiosService beneficios;
    private final RonaldmichaLiquidacionService liquidaciones;
    private final RonaldmichaVacacionesService vacaciones;
    private final RonaldmichaParametrosService parametros;
    private final RonaldmichaEmpresaService empresa;
    private final RonaldmichaDocumentoRepository documentos;
    private final RonaldmichaMailService correo;
    private final RonaldmichaAuditoriaService auditoria;
    private final Clock reloj;

    public RonaldmichaReportesService(RonaldmichaTrabajadorRepository trabajadores, RonaldmichaPlanillaService planilla, RonaldmichaBeneficiosService beneficios,
                           RonaldmichaLiquidacionService liquidaciones, RonaldmichaVacacionesService vacaciones, RonaldmichaParametrosService parametros,
                           RonaldmichaEmpresaService empresa, RonaldmichaDocumentoRepository documentos, RonaldmichaMailService correo,
                           RonaldmichaAuditoriaService auditoria, Clock reloj) {
        this.trabajadores = trabajadores;
        this.planilla = planilla;
        this.beneficios = beneficios;
        this.liquidaciones = liquidaciones;
        this.vacaciones = vacaciones;
        this.parametros = parametros;
        this.empresa = empresa;
        this.documentos = documentos;
        this.correo = correo;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public List<RonaldmichaReporte> catalogo(RonaldmichaUsuario usuario) {
        if (esTrabajador(usuario)) {
            return MIS_DOCUMENTOS.stream().filter(RonaldmichaReporte::paraTrabajador).toList();
        }
        return List.of(RonaldmichaReporte.values());
    }

    public List<Formato> formatos(RonaldmichaUsuario usuario, RonaldmichaReporte r) {
        return esTrabajador(usuario) ? r.formatos().stream().filter(f -> f == Formato.PDF).toList() : r.formatos();
    }

    public RonaldmichaEmpresa empresa() {
        return empresa.obtener();
    }

    public Optional<String> avisoEmpresa(RonaldmichaEmpresa e) {
        return empresa.avisoDatosIncompletos(e);
    }

    public boolean correoConfigurado() {
        return correo.configurado();
    }

    public String periodoTexto(RonaldmichaReporte r, YearMonth mes) {
        return switch (r) {
            case GRATIFICACION -> RonaldmichaBeneficiosService.etiqueta(RonaldmichaSemestreBeneficio.vigenteEn(RonaldmichaTipoBeneficio.GRATIFICACION, mes));
            case CTS -> RonaldmichaBeneficiosService.etiqueta(RonaldmichaSemestreBeneficio.vigenteEn(RonaldmichaTipoBeneficio.CTS, mes));
            case RENTA_QUINTA -> "Ejercicio " + mes.getYear() + " (enero a " + mesNombre(mes) + ")";
            case LIQUIDACION, LIBERACION_CTS, SOLICITUD_VACACIONES, CERTIFICADO_TRABAJO, CONSTANCIA_TRABAJO -> "A la fecha";
            default -> mesTexto(mes);
        };
    }

    public List<RonaldmichaTrabajadorResumen> trabajadores(RonaldmichaUsuario usuario, RonaldmichaReporte r, YearMonth mes) {
        exigirCatalogo(usuario, r);
        if (esTrabajador(usuario)) {
            return trabajadores.buscarPorId(usuario.trabajadorId()).map(t -> List.of(t.resumen())).orElse(List.of());
        }
        LocalDate hoy = hoy();
        List<RonaldmichaTrabajador> lista = switch (r) {
            case BOLETA_PAGO -> planilla.delMes(mes).filas().stream().map(FilaPlanilla::trabajador).toList();
            case GRATIFICACION, CTS -> filasBeneficio(r, mes).stream().map(FilaBeneficio::trabajador).toList();
            case LIQUIDACION -> trabajadores.listarTodos().stream()
                    .filter(t -> liquidaciones.delTrabajador(t.id()).isPresent()).toList();
            case LIBERACION_CTS, CERTIFICADO_TRABAJO -> {
                List<RonaldmichaTrabajador> todos = trabajadores.listarTodos();
                List<RonaldmichaTrabajador> cesados = todos.stream().filter(t -> cese(t).isPresent()).toList();
                List<RonaldmichaTrabajador> unidos = new ArrayList<>(cesados);
                todos.stream().filter(t -> !cesados.contains(t) && t.activoEn(hoy)).forEach(unidos::add);
                yield unidos;
            }
            case RENTA_QUINTA -> trabajadores.listarTodos().stream()
                    .filter(t -> !t.fechaIngreso().isAfter(mes.atEndOfMonth())
                            && (t.fechaCese() == null || t.fechaCese().getYear() >= mes.getYear())).toList();
            default -> trabajadores.listarTodos().stream().filter(t -> t.activoEn(hoy)).toList();
        };
        return lista.stream().map(RonaldmichaTrabajador::resumen).toList();
    }

    public RonaldmichaDocumentModel vistaPrevia(RonaldmichaUsuario usuario, RonaldmichaReporte r, Parametros p) {
        exigirAcceso(usuario, r, p);
        if (r.porTrabajador()) {
            List<Pieza> piezas = piezas(usuario, r, p);
            if (piezas.isEmpty()) {
                throw new RonaldmichaValidacionException("No hay documentos para el filtro elegido.");
            }
            RonaldmichaDocumentModel d = copias(piezas.getFirst().modelo().apply(RonaldmichaPlantillas.NUMERO_PREVIO), p);
            return piezas.size() == 1 || d.esBoleta() ? d
                    : d.conSubtitulo(d.subtitulo() + " · vista previa del 1.° de " + piezas.size() + " documentos");
        }
        return switch (r) {
            case PLANILLA -> planillaPdf(p.mes());
            case PAGOS -> pagosVista(p.mes());
            case AFPNET -> afpnetVista(p.mes());
            case PLAME -> resumenPlame(p.mes());
            default -> throw new IllegalStateException("Reporte sin vista previa: " + r);
        };
    }

    public List<String> observaciones(RonaldmichaUsuario usuario, RonaldmichaReporte r, Parametros p) {
        exigirAcceso(usuario, r, p);
        List<String> obs = new ArrayList<>();
        switch (r) {
            case PLANILLA, AFPNET -> filas(p.mes()).stream().map(FilaPlanilla::trabajador)
                    .filter(t -> t.sistemaPension().esAfp() && !cusppValido(t.cuspp()))
                    .forEach(t -> obs.add(t.nombreCompleto() + ": el CUSPP está vacío o no tiene 12 caracteres."));
            case PAGOS -> filas(p.mes()).stream()
                    .filter(f -> f.resultado().netoAPagar().signum() > 0)
                    .map(FilaPlanilla::trabajador)
                    .forEach(t -> {
                        if (t.tipoPago() != RonaldmichaTipoPago.DEPOSITO) {
                            obs.add(t.nombreCompleto() + ": se paga con " + t.tipoPago().nombre().toLowerCase(RonaldmichaFormatos.LOCALE_PE)
                                    + "; no va en el TXT.");
                        } else if (vacio(t.bancoSueldo()) || vacio(t.ctaSueldo())) {
                            obs.add(t.nombreCompleto() + ": falta el banco o la cuenta de sueldo; no va en el TXT.");
                        }
                    });
            case BOLETA_PAGO -> {
                List<String> faltan = RonaldmichaBoletaPlantilla.faltantes(empresa.obtener());
                if (!faltan.isEmpty()) {
                    obs.add("Faltan datos de la empresa para la boleta: " + String.join(", ", faltan)
                            + ". Saldrán como [COMPLETAR]; complételos en Configuración › Datos de la empresa.");
                }
            }
            case PLAME -> {
                if (vacio(empresa.obtener().ruc())) {
                    obs.add("La empresa no tiene RUC: los archivos PLAME lo llevan en el nombre.");
                }
                filas(p.mes()).stream().map(FilaPlanilla::trabajador)
                        .filter(t -> t.tipoDoc() == RonaldmichaTipoDocumento.DNI && (t.numDoc() == null || !t.numDoc().matches("\\d{8}")))
                        .forEach(t -> obs.add(t.nombreCompleto() + ": el DNI debe tener 8 dígitos."));
            }
            default -> {
            }
        }
        return obs;
    }

    public Archivo exportar(RonaldmichaUsuario usuario, RonaldmichaReporte r, Formato formato, Parametros p) {
        exigirAcceso(usuario, r, p);
        if (!r.formatos().contains(formato)) {
            throw new RonaldmichaValidacionException(r.titulo() + " no se exporta en " + formato.etiqueta() + ".");
        }
        if (!formatos(usuario, r).contains(formato)) {
            throw new RonaldmichaAccesoDenegadoException("Sus documentos se descargan solo en PDF.");
        }
        RonaldmichaEmpresa e = empresa.obtener();
        if (r.porTrabajador()) {
            List<Pieza> piezas = piezas(usuario, r, p);
            if (piezas.isEmpty()) {
                throw new RonaldmichaValidacionException("No hay documentos para el filtro elegido.");
            }
            String nombre = piezas.size() == 1
                    ? base(r, p.mes()) + "_" + piezas.getFirst().trabajador().codigo() + "." + formato.extension()
                    : base(r, p.mes()) + "_todos." + formato.extension();
            List<RonaldmichaDocumentoEmitido> registrados = registrar(r, p.mes(), formato, nombre, piezas, RonaldmichaEstadoDocumento.GENERADO);
            List<RonaldmichaDocumentModel> modelos = IntStream.range(0, piezas.size())
                    .mapToObj(i -> paraUsuario(usuario, copias(piezas.get(i).modelo().apply(registrados.get(i).numero()), p))
                            .conArchivo(nombre))
                    .toList();
            byte[] contenido = formato == Formato.WORD
                    ? RonaldmichaWordRenderer.renderVarios(modelos, nombre, e, empresa.avisoDatosIncompletos(e), ahora())
                    : RonaldmichaPdfRenderer.renderVarios(modelos, nombre, e, empresa.avisoDatosIncompletos(e), ahora());
            List<String> numeros = registrados.stream().map(RonaldmichaDocumentoEmitido::numero).toList();
            auditar(usuario, r, formato, numeros, piezas.size() == 1 ? piezas.getFirst().trabajador().nombreCompleto()
                    : piezas.size() + " trabajadores");
            return new Archivo(nombre, contenido, numeros);
        }
        String nombre = r == RonaldmichaReporte.PLAME ? nombrePlame(p.mes(), e) + "." + formato.extension()
                : base(r, p.mes()) + "." + formato.extension();
        byte[] contenido = switch (r) {
            case PLANILLA -> formato == Formato.EXCEL ? planillaExcel(p.mes()) : pdf(planillaPdf(p.mes()), nombre, e);
            case PAGOS -> formato == Formato.EXCEL ? pagosExcel(p.mes()) : pagosTxt(p.mes());
            case AFPNET -> formato == Formato.EXCEL ? afpnetExcel(p.mes()) : pdf(afpnetVista(p.mes()), nombre, e);
            case PLAME -> formato == Formato.ZIP ? plameZip(p.mes(), e) : pdf(resumenPlame(p.mes()), nombre, e);
            default -> throw new IllegalStateException("Reporte sin exportación: " + r);
        };
        RonaldmichaDocumentoEmitido d = documentos.registrar(List.of(new RonaldmichaDocumentoEmitido(null, null, r.tipo(), null, null,
                p.mes(), formato.etiqueta(), ahora(), RonaldmichaEstadoDocumento.GENERADO, false, nombre))).getFirst();
        auditar(usuario, r, formato, List.of(d.numero()), mesTexto(p.mes()));
        return new Archivo(nombre, contenido, List.of(d.numero()));
    }

    public Envio enviarPorCorreo(RonaldmichaUsuario usuario, RonaldmichaReporte r, Parametros p) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "enviar documentos por correo");
        exigirAcceso(usuario, r, p);
        if (!r.porTrabajador()) {
            throw new RonaldmichaValidacionException("Solo los documentos por trabajador se envían por correo.");
        }
        List<Pieza> piezas = piezas(usuario, r, p);
        List<Pieza> conCorreo = piezas.stream().filter(x -> !vacio(x.trabajador().correo())).toList();
        List<String> sinCorreo = piezas.stream().filter(x -> vacio(x.trabajador().correo()))
                .map(x -> x.trabajador().nombreCompleto()).toList();
        RonaldmichaEmpresa e = empresa.obtener();
        int enviados = 0;
        List<String> numeros = new ArrayList<>();
        for (Pieza pieza : conCorreo) {
            String nombre = base(r, p.mes()) + "_" + pieza.trabajador().codigo() + ".pdf";
            RonaldmichaDocumentoEmitido d = registrar(r, p.mes(), Formato.PDF, nombre, List.of(pieza), RonaldmichaEstadoDocumento.GENERADO)
                    .getFirst();
            numeros.add(d.numero());
            RonaldmichaDocumentModel modelo = copias(pieza.modelo().apply(d.numero()), p);
            if (modelo.esBoleta() && !modelo.copias().isEmpty()) {
                modelo = modelo.conCopias(List.of(RonaldmichaDocumentModel.Copia.TRABAJADOR));
            }
            byte[] pdf = RonaldmichaPdfRenderer.render(modelo.conArchivo(nombre), e,
                    empresa.avisoDatosIncompletos(e), ahora());
            RonaldmichaMailService.Resultado resultado = correo.enviar(new RonaldmichaMailService.Correo(pieza.trabajador().correo(),
                    r.titulo() + " · " + periodoTexto(r, p.mes()), "Adjuntamos su documento " + d.numero() + ".",
                    nombre, pdf));
            if (resultado.enviado()) {
                documentos.cambiarEstado(List.of(d.id()), RonaldmichaEstadoDocumento.ENVIADO);
                enviados++;
            }
        }
        String mensaje = correo.configurado()
                ? enviados + " de " + conCorreo.size() + " documentos enviados."
                : "El correo no está configurado: se registraron " + conCorreo.size()
                + " documentos para enviar y no se envió ninguno.";
        auditoria.registrar(usuario.usuario(), "ENVIO_CORREO", r.titulo() + " · " + periodoTexto(r, p.mes()) + " · "
                + conCorreo.size() + " con correo, " + enviados + " enviados, " + sinCorreo.size() + " sin correo"
                + (numeros.isEmpty() ? "" : " · " + String.join(", ", numeros)));
        return new Envio(conCorreo.size(), enviados, sinCorreo, mensaje);
    }

    public List<RonaldmichaDocumentoEmitido> emitidos(RonaldmichaUsuario usuario) {
        if (esTrabajador(usuario)) {
            return documentos.recientes(EMITIDOS_MAXIMO, usuario.trabajadorId());
        }
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "ver los documentos emitidos");
        return documentos.recientes(EMITIDOS_MAXIMO, null);
    }

    public void marcarCargoRecibido(RonaldmichaUsuario usuario, RonaldmichaDocumentoEmitido d) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "registrar cargos");
        if (!d.requiereCargo()) {
            throw new RonaldmichaValidacionException(d.numero() + " no requiere cargo de recepción.");
        }
        if (!documentos.marcarCargoRecibido(d.id(), ahora(), "Cargo físico registrado por " + usuario.usuario())) {
            throw new RonaldmichaValidacionException("El cargo de " + d.numero() + " ya estaba recibido.");
        }
        auditoria.registrar(usuario.usuario(), "CARGO_RECIBIDO", d.numero()
                + (d.trabajador() == null ? "" : " · " + d.trabajador()));
    }

    public static final int MESES_PORTAL = 12;

    public record BoletaPortal(YearMonth mes, RonaldmichaSituacionPeriodo situacion, Optional<RonaldmichaDocumentoEmitido> documento) {

        public boolean disponible() {
            return situacion == RonaldmichaSituacionPeriodo.CERRADO;
        }
    }

    public List<BoletaPortal> misBoletas(RonaldmichaUsuario usuario) {
        long id = RonaldmichaPermisos.exigirPortal(usuario, "ver sus documentos");
        RonaldmichaTrabajador t = trabajadores.buscarPorId(id).orElseThrow(() -> new RonaldmichaValidacionException("El trabajador no existe."));
        YearMonth actual = YearMonth.now(reloj);
        List<BoletaPortal> lista = new ArrayList<>();
        Map<Integer, Map<YearMonth, RonaldmichaSituacionPeriodo>> situaciones = new TreeMap<>();
        for (int i = 0; i < MESES_PORTAL; i++) {
            YearMonth mes = actual.minusMonths(i);
            boolean conVinculo = !t.fechaIngreso().isAfter(mes.atEndOfMonth())
                    && (t.fechaCese() == null || !t.fechaCese().isBefore(mes.atDay(1)));
            if (!conVinculo) {
                continue;
            }
            RonaldmichaSituacionPeriodo s = situaciones.computeIfAbsent(mes.getYear(), planilla::situacionesDelAnio).get(mes);
            lista.add(new BoletaPortal(mes, s, documentos.ultimoDe(RonaldmichaReporte.BOLETA_PAGO.tipo(), id, mes)));
        }
        return lista;
    }

    public Archivo descargarMiBoleta(RonaldmichaUsuario usuario, YearMonth mes) {
        long id = RonaldmichaPermisos.exigirPortal(usuario, "descargar su boleta");
        Parametros p = new Parametros(mes, id, false);
        exigirAcceso(usuario, RonaldmichaReporte.BOLETA_PAGO, p);
        Optional<RonaldmichaDocumentoEmitido> emitido = documentos.ultimoDe(RonaldmichaReporte.BOLETA_PAGO.tipo(), id, mes);
        if (emitido.isEmpty()) {
            return exportar(usuario, RonaldmichaReporte.BOLETA_PAGO, Formato.PDF, p);
        }
        List<Pieza> piezas = piezas(usuario, RonaldmichaReporte.BOLETA_PAGO, p);
        if (piezas.isEmpty()) {
            throw new RonaldmichaValidacionException("No hay boleta de " + mesTexto(mes).toLowerCase(RonaldmichaFormatos.LOCALE_PE) + ".");
        }
        RonaldmichaDocumentoEmitido d = emitido.get();
        String nombre = base(RonaldmichaReporte.BOLETA_PAGO, mes) + "_" + piezas.getFirst().trabajador().codigo() + ".pdf";
        RonaldmichaEmpresa e = empresa.obtener();
        byte[] pdf = RonaldmichaPdfRenderer.render(paraUsuario(usuario, piezas.getFirst().modelo().apply(d.numero())).conArchivo(nombre), e,
                empresa.avisoDatosIncompletos(e), ahora());
        auditoria.registrar(usuario.usuario(), "BOLETA_DESCARGADA", d.numero() + " · " + mesTexto(mes)
                + " · portal del trabajador");
        return new Archivo(nombre, pdf, List.of(d.numero()));
    }

    public RonaldmichaDocumentoEmitido confirmarRecepcion(RonaldmichaUsuario usuario, long documentoId) {
        long id = RonaldmichaPermisos.exigirPortal(usuario, "confirmar la recepción de documentos");
        RonaldmichaDocumentoEmitido d = documentos.buscar(documentoId)
                .orElseThrow(() -> new RonaldmichaValidacionException("El documento no existe."));
        if (!Objects.equals(d.trabajadorId(), id)) {
            throw new RonaldmichaAccesoDenegadoException("Solo puede confirmar la recepción de sus propios documentos.");
        }
        if (!d.requiereCargo()) {
            throw new RonaldmichaValidacionException(d.numero() + " no requiere cargo de recepción.");
        }
        if (RonaldmichaReporte.BOLETA_PAGO.tipo().equals(d.tipo()) && d.periodo() != null
                && planilla.situacionesDelAnio(d.periodo().getYear()).get(d.periodo()) != RonaldmichaSituacionPeriodo.CERRADO) {
            throw new RonaldmichaValidacionException("La boleta de " + mesTexto(d.periodo()).toLowerCase(RonaldmichaFormatos.LOCALE_PE)
                    + " aún no está disponible: el periodo no está cerrado.");
        }
        LocalDateTime ahora = ahora();
        if (!documentos.marcarCargoRecibido(d.id(), ahora, "Cargo electrónico confirmado por " + usuario.usuario())) {
            throw new RonaldmichaValidacionException("Ya confirmó la recepción de " + d.numero() + ".");
        }
        auditoria.registrar(usuario.usuario(), "CARGO_ELECTRONICO", d.numero() + " · "
                + (RonaldmichaReporte.desdeTipo(d.tipo()) == null ? d.tipo() : RonaldmichaReporte.desdeTipo(d.tipo()).titulo())
                + (d.periodo() == null ? "" : " · " + mesTexto(d.periodo()))
                + " · recepción confirmada el " + RonaldmichaFormatos.fechaHora(ahora) + " (D.S. 009-2011-TR)");
        return documentos.buscar(d.id()).orElseThrow();
    }

    private List<Pieza> piezas(RonaldmichaUsuario usuario, RonaldmichaReporte r, Parametros p) {
        RonaldmichaEmpresa e = empresa.obtener();
        Long id = p.todos() ? null : p.trabajadorId();
        if (!p.todos() && id == null) {
            throw new RonaldmichaValidacionException("Elija un trabajador.");
        }
        return switch (r) {
            case BOLETA_PAGO -> filas(p.mes()).stream().filter(f -> id == null || f.trabajador().id().equals(id))
                    .map(f -> new Pieza(f.trabajador(), n -> boleta(f, p.mes(), e))).toList();
            case GRATIFICACION, CTS -> {
                RonaldmichaSemestreBeneficio s = semestre(r, p.mes());
                yield filasBeneficio(r, p.mes()).stream().filter(f -> id == null || f.trabajador().id().equals(id))
                        .map(f -> new Pieza(f.trabajador(), n -> RonaldmichaPlantillas.constancia(
                                beneficios.constancias(s, List.of(f)).getFirst(), n, e))).toList();
            }
            case LIQUIDACION -> {
                RonaldmichaTrabajador t = trabajador(id);
                RonaldmichaLiquidacion l = liquidaciones.delTrabajador(t.id()).orElseThrow(() ->
                        new RonaldmichaValidacionException(t.nombreCompleto() + " no tiene liquidación guardada."));
                RonaldmichaLiquidacionService.Calculo c = liquidaciones.calcular(usuario, RonaldmichaLiquidacionService.borrador(l));
                yield List.of(new Pieza(t, n -> RonaldmichaPlantillas.liquidacion(c.reporte(), n, e)));
            }
            case LIBERACION_CTS, CERTIFICADO_TRABAJO -> {
                RonaldmichaTrabajador t = trabajador(id);
                LocalDate cese = cese(t).orElseThrow(() -> new RonaldmichaValidacionException(t.nombreCompleto()
                        + " no tiene cese registrado" + (r == RonaldmichaReporte.CERTIFICADO_TRABAJO
                        ? ": emita la constancia de trabajo." : ".")));
                boolean carta = r == RonaldmichaReporte.LIBERACION_CTS;
                List<String> parrafos = carta ? RonaldmichaTextosDocumentos.liberacionCts(e, t, cese)
                        : RonaldmichaTextosDocumentos.certificadoTrabajo(e, t, cese);
                yield List.of(new Pieza(t, n -> RonaldmichaPlantillas.carta(carta ? "CARTA DE LIBERACIÓN DE CTS"
                        : "CERTIFICADO DE TRABAJO", n, parrafos, RonaldmichaTextosDocumentos.ciudadFecha(e, hoy()), e,
                        recibi(t))));
            }
            case CONSTANCIA_TRABAJO -> {
                RonaldmichaTrabajador t = trabajador(id);
                if (!t.activoEn(hoy())) {
                    throw new RonaldmichaValidacionException(t.nombreCompleto() + " ya cesó: emita el certificado de trabajo.");
                }
                yield List.of(new Pieza(t, n -> RonaldmichaPlantillas.carta("CONSTANCIA DE TRABAJO", n,
                        RonaldmichaTextosDocumentos.constanciaTrabajo(e, t), RonaldmichaTextosDocumentos.ciudadFecha(e, hoy()), e,
                        recibi(t))));
            }
            case SOLICITUD_VACACIONES -> {
                RonaldmichaTrabajador t = trabajador(id);
                Registro ultimo = vacaciones.detalle(usuario, t.id()).registros().stream()
                        .filter(g -> !g.estado().cerrado())
                        .findFirst().orElseThrow(() -> new RonaldmichaValidacionException(t.nombreCompleto()
                                + " no tiene registros de vacaciones vigentes."));
                yield List.of(new Pieza(t, n -> RonaldmichaPlantillas.solicitudVacaciones(
                        vacaciones.solicitud(e, ultimo.vacacion()), n)));
            }
            case RENTA_QUINTA -> {
                RonaldmichaTrabajador t = trabajador(id);
                yield List.of(new Pieza(t, n -> rentaQuinta(t, p.mes(), n, e)));
            }
            default -> throw new IllegalStateException("No es un documento por trabajador: " + r);
        };
    }

    private RonaldmichaDocumentModel boleta(FilaPlanilla f, YearMonth mes, RonaldmichaEmpresa e) {
        RonaldmichaTrabajador t = f.trabajador();
        Optional<RonaldmichaPlanillaRepository.OtroEmpleador> otro = planilla.otroEmpleadorSinPermiso(t.id(), mes.getYear());
        Optional<RonaldmichaVacacionesService.Rango> goce = vacaciones.goceEnMes(t.id(), mes);
        boolean cesaEnElMes = t.fechaCese() != null && YearMonth.from(t.fechaCese()).equals(mes);
        String liquidacion = cesaEnElMes ? documentos.ultimoDe(RonaldmichaReporte.LIQUIDACION.tipo(), t.id())
                .map(RonaldmichaDocumentoEmitido::numero).orElse(null) : null;
        return RonaldmichaBoletaPlantilla.modelo(new RonaldmichaBoletaPlantilla.Entrada(t, f.resultado(), f.horasOrdinarias(),
                f.minutosExtra(), e, parametros.vigentes(mes), mes,
                otro.map(o -> vacio(o.razonSocial()) ? valor(o.ruc()) : o.razonSocial()).orElse(null),
                otro.map(RonaldmichaPlanillaRepository.OtroEmpleador::renta).orElse(null),
                goce.map(RonaldmichaVacacionesService.Rango::desde).orElse(null), goce.map(RonaldmichaVacacionesService.Rango::hasta).orElse(null),
                liquidacion));
    }

    private static RonaldmichaDocumentModel paraUsuario(RonaldmichaUsuario usuario, RonaldmichaDocumentModel d) {
        return esTrabajador(usuario) && d.esBoleta() && !d.copias().isEmpty()
                ? d.conCopias(List.of(RonaldmichaDocumentModel.Copia.TRABAJADOR)) : d;
    }

    private RonaldmichaDocumentModel rentaQuinta(RonaldmichaTrabajador t, YearMonth hasta, String numero, RonaldmichaEmpresa e) {
        List<List<String>> filas = new ArrayList<>();
        BigDecimal rentas = RonaldmichaDinero.CERO;
        BigDecimal retenido = RonaldmichaDinero.CERO;
        BigDecimal impuestoProyectado = RonaldmichaDinero.CERO;
        for (PlanillaMes pm : planilla.delAnio(hasta)) {
            Optional<FilaPlanilla> fila = pm.filas().stream()
                    .filter(f -> f.trabajador().id().equals(t.id())).findFirst();
            if (fila.isEmpty()) {
                continue;
            }
            RonaldmichaResultadoPlanilla r = fila.get().resultado();
            rentas = RonaldmichaDinero.sumar(rentas, r.baseRenta());
            retenido = RonaldmichaDinero.sumar(retenido, r.rentaQuinta());
            impuestoProyectado = r.renta().impuestoAnual();
            filas.add(List.of(mesNombre(pm.mes()), RonaldmichaFormatos.numero(r.baseRenta()), RonaldmichaFormatos.numero(r.rentaQuinta())));
        }
        for (int semestre = 1; semestre <= 2; semestre++) {
            RonaldmichaSemestreBeneficio s = new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, hasta.getYear(), semestre);
            if (YearMonth.of(s.anio(), s.mesPago()).isAfter(hasta)) {
                continue;
            }
            BigDecimal grati = beneficios.calcular(s).filas().stream().filter(f -> f.trabajador().id().equals(t.id()))
                    .map(FilaBeneficio::total).findFirst().orElse(RonaldmichaDinero.CERO);
            if (grati.signum() > 0) {
                rentas = RonaldmichaDinero.sumar(rentas, grati);
                filas.add(List.of(RonaldmichaBeneficiosService.etiqueta(s) + " (con bonificación)", RonaldmichaFormatos.numero(grati), "0.00"));
            }
        }
        Optional<RonaldmichaPlanillaRepository.OtroEmpleador> otro = planilla.otroEmpleadorSinPermiso(t.id(), hasta.getYear());
        BigDecimal retenidoOtros = RonaldmichaDinero.CERO;
        if (otro.isPresent()) {
            rentas = RonaldmichaDinero.sumar(rentas, otro.get().renta());
            retenidoOtros = otro.get().retencion();
            filas.add(List.of("Otros empleadores" + (otro.get().razonSocial() == null ? ""
                    : " (" + otro.get().razonSocial() + ")"), RonaldmichaFormatos.numero(otro.get().renta()),
                    RonaldmichaFormatos.numero(retenidoOtros)));
        }
        RonaldmichaParametrosVersion p = parametros.vigentes(hasta);
        BigDecimal deduccion = RonaldmichaRentaQuintaCalculator.deduccion(p.uit());
        BigDecimal neta = RonaldmichaDinero.redondear(rentas.subtract(deduccion).max(BigDecimal.ZERO));
        BigDecimal impuesto = RonaldmichaRentaQuintaCalculator.impuestoAnual(neta, p.uit());
        BigDecimal retenidoTotal = RonaldmichaDinero.sumar(retenido, retenidoOtros);
        List<Bloque> bloques = List.of(
                new KeyValue(List.of(
                        new Par("Empleador", valor(e.razonSocial())), new Par("RUC", valor(e.ruc())),
                        new Par("Trabajador", t.nombreCompleto()),
                        new Par("Documento", t.tipoDoc().codigo() + " " + t.numDoc()),
                        new Par("Ejercicio", hasta.getYear() + " (enero a " + mesNombre(hasta) + ")"),
                        new Par("UIT del ejercicio", RonaldmichaFormatos.moneda(p.uit())))),
                new Heading("Rentas de quinta categoría y retenciones"),
                new Table(List.of("Concepto", "Renta bruta (S/)", "Retención (S/)"), filas,
                        List.of("Total", RonaldmichaFormatos.numero(rentas), RonaldmichaFormatos.numero(retenidoTotal)),
                        List.of(false, true, true)),
                new KeyValue(List.of(
                        new Par("Renta bruta", RonaldmichaFormatos.moneda(rentas)),
                        new Par("Deducción de 7 UIT", RonaldmichaFormatos.moneda(deduccion)),
                        new Par("Renta neta", RonaldmichaFormatos.moneda(neta)),
                        new Par("Impuesto calculado", RonaldmichaFormatos.moneda(impuesto)),
                        new Par("Impuesto anual proyectado en " + mesNombre(hasta) + " (art. 40)",
                                RonaldmichaFormatos.moneda(impuestoProyectado)),
                        new Par("Retenido por el empleador", RonaldmichaFormatos.moneda(retenido)),
                        new Par("Retenido por otros empleadores", RonaldmichaFormatos.moneda(retenidoOtros)),
                        new Par("Diferencia", RonaldmichaFormatos.moneda(RonaldmichaDinero.redondear(impuesto.subtract(retenidoTotal)))))),
                new Total("TOTAL RETENIDO", retenido, RonaldmichaNumeroALetras.texto(retenido)),
                new Paragraph("Se certifica que las rentas y retenciones indicadas corresponden a lo pagado y retenido "
                        + "por el empleador en el ejercicio (art. 40 del Reglamento de la LIR: la retención de "
                        + "diciembre ajusta el impuesto del año con lo realmente percibido). La deducción adicional de "
                        + "hasta 3 UIT no la aplica el empleador: el trabajador la aplica en su declaración anual."),
                new Firmas(List.of(RonaldmichaPlantillas.firmaEmpleador(e), recibi(t))));
        return new RonaldmichaDocumentModel("CERTIFICADO DE RENTAS Y RETENCIONES DE QUINTA CATEGORÍA",
                RonaldmichaPlantillas.subtitulo("Art. 45 del Reglamento de la Ley del Impuesto a la Renta", numero), List.of(),
                Orientacion.VERTICAL, bloques, null);
    }

    private static final List<String> COLUMNAS_PLANILLA = List.of(
            "N.°", "Código", "Documento", "Apellidos y nombres", "Cargo", "Fecha de ingreso", "Sistema de pensión",
            "CUSPP", "Días laborados", "Faltas", "Días de vacaciones", "Días subsidiados", "Básico",
            "Remuneración vacacional", "Asignación familiar", "Horas extra 25 %", "Horas extra 35 %",
            "Feriado trabajado", "Comisiones", "Bonificación", "Otros ingresos afectos", "Tardanza (−)", "Subsidio",
            "Ingresos no afectos", "Total ingresos", "Aporte ONP/AFP", "Prima de seguros AFP", "Comisión AFP",
            "Renta de 5.ª", "Otros descuentos", "Total descuentos", "Neto a pagar", "Aportes del empleador");

    private byte[] planillaExcel(YearMonth mes) {
        List<List<Object>> filas = new ArrayList<>();
        List<FilaPlanilla> lista = filas(mes);
        for (int i = 0; i < lista.size(); i++) {
            RonaldmichaTrabajador t = lista.get(i).trabajador();
            RonaldmichaResultadoPlanilla r = lista.get(i).resultado();
            filas.add(List.of(i + 1, t.codigo(), t.tipoDoc().codigo() + " " + t.numDoc(), t.nombreCompleto(),
                    valor(t.cargo()), RonaldmichaFormatos.fecha(t.fechaIngreso()), pension(t), valor(t.cuspp()),
                    r.diasLaborados(), r.inasistencias(), r.diasVacaciones(), r.diasSubsidiados(),
                    r.basico(), r.remuneracionVacacional(), r.asignacionFamiliar(), r.horasExtra25(), r.horasExtra35(),
                    r.feriadoTrabajado(), r.comisiones(), r.bonificacion(), r.ingresosExtraAfectos(), r.tardanza(),
                    r.subsidio(), RonaldmichaDinero.sumar(r.movilidad(), r.ingresosExtraNoAfectos()), r.totalIngresos(),
                    r.pension().aporte(), r.pension().prima(), r.pension().comision(), r.rentaQuinta(),
                    RonaldmichaDinero.sumar(r.adelanto(), r.otrosDescuentos(), r.descuentosExtra()), r.totalDescuentos(),
                    r.netoAPagar(), r.totalAportes()));
        }
        List<Integer> sumas = IntStream.range(8, COLUMNAS_PLANILLA.size()).boxed().toList();
        return RonaldmichaTablaExcel.crear(List.of(new RonaldmichaTablaExcel.Hoja("Planilla " + AAAAMM.format(mes),
                "Planilla de remuneraciones · " + mesTexto(mes) + " · " + valor(empresa.obtener().razonSocial()),
                COLUMNAS_PLANILLA, filas, false, sumas)));
    }

    private RonaldmichaDocumentModel planillaPdf(YearMonth mes) {
        List<FilaPlanilla> lista = filas(mes);
        List<List<String>> filas = new ArrayList<>();
        BigDecimal[] totales = new BigDecimal[11];
        Arrays.fill(totales, RonaldmichaDinero.CERO);
        for (FilaPlanilla f : lista) {
            RonaldmichaTrabajador t = f.trabajador();
            RonaldmichaResultadoPlanilla r = f.resultado();
            BigDecimal he = RonaldmichaDinero.sumar(r.horasExtra25(), r.horasExtra35());
            BigDecimal otros = RonaldmichaDinero.redondear(r.totalIngresos().subtract(r.basico()).subtract(r.asignacionFamiliar())
                    .subtract(he));
            BigDecimal otrosDesc = RonaldmichaDinero.redondear(r.totalDescuentos().subtract(r.pension().total())
                    .subtract(r.rentaQuinta()));
            BigDecimal[] valores = {r.basico(), r.asignacionFamiliar(), he, otros, r.totalIngresos(), r.pension().total(),
                    r.rentaQuinta(), otrosDesc, r.totalDescuentos(), r.netoAPagar(), r.totalAportes()};
            List<String> fila = new ArrayList<>(List.of(t.tipoDoc().codigo() + " " + t.numDoc(), t.nombreCompleto(),
                    String.valueOf(r.diasLaborados())));
            for (int i = 0; i < valores.length; i++) {
                fila.add(RonaldmichaFormatos.numero(valores[i]));
                totales[i] = RonaldmichaDinero.sumar(totales[i], valores[i]);
            }
            filas.add(fila);
        }
        List<String> pie = new ArrayList<>(List.of("TOTAL", lista.size() + " trabajadores", ""));
        for (BigDecimal total : totales) {
            pie.add(RonaldmichaFormatos.numero(total));
        }
        List<Boolean> numericas = new ArrayList<>(List.of(false, false, true));
        numericas.addAll(Collections.nCopies(totales.length, true));
        BigDecimal neto = totales[9];
        RonaldmichaEmpresa e = empresa.obtener();
        return new RonaldmichaDocumentModel("PLANILLA DE REMUNERACIONES", mesTexto(mes) + " · D.S. 001-98-TR", List.of(),
                Orientacion.HORIZONTAL, List.of(new Table(List.of("Documento", "Apellidos y nombres", "Días", "Básico",
                "Asig. fam.", "Horas extra", "Otros ingresos", "Total ingresos", "ONP/AFP", "Renta 5.ª",
                "Otros desc.", "Total desc.", "Neto", "Aportes empl."), filas, pie, numericas),
                new Total("TOTAL NETO A PAGAR", neto, RonaldmichaNumeroALetras.texto(neto)), RonaldmichaPlantillas.firmasReporte(e)), null);
    }

    private Map<String, List<FilaPlanilla>> pagosPorBanco(YearMonth mes) {
        Map<String, List<FilaPlanilla>> porBanco = new TreeMap<>();
        for (FilaPlanilla f : filas(mes)) {
            if (f.resultado().netoAPagar().signum() <= 0) {
                continue;
            }
            porBanco.computeIfAbsent(conCuenta(f.trabajador()) ? f.trabajador().bancoSueldo().trim() : SIN_CUENTA,
                    k -> new ArrayList<>()).add(f);
        }
        List<FilaPlanilla> sin = porBanco.remove(SIN_CUENTA);
        Map<String, List<FilaPlanilla>> ordenado = new LinkedHashMap<>(porBanco);
        if (sin != null) {
            ordenado.put(SIN_CUENTA, sin);
        }
        return ordenado;
    }

    private byte[] pagosExcel(YearMonth mes) {
        Map<String, List<FilaPlanilla>> porBanco = pagosPorBanco(mes);
        List<RonaldmichaTablaExcel.Hoja> hojas = new ArrayList<>();
        List<List<Object>> resumen = new ArrayList<>();
        porBanco.forEach((banco, lista) -> resumen.add(List.of(banco, lista.size(), RonaldmichaDinero.sumar(lista.stream()
                .map(f -> f.resultado().netoAPagar()).toArray(BigDecimal[]::new)))));
        hojas.add(new RonaldmichaTablaExcel.Hoja("Resumen", "Pagos de remuneraciones · " + mesTexto(mes),
                List.of("Banco", "Trabajadores", "Importe (S/)"), resumen, false, List.of(1, 2)));
        porBanco.forEach((banco, lista) -> {
            List<List<Object>> filas = new ArrayList<>();
            for (int i = 0; i < lista.size(); i++) {
                RonaldmichaTrabajador t = lista.get(i).trabajador();
                filas.add(List.of(i + 1, t.tipoDoc().codigo(), t.numDoc(), t.nombreCompleto(),
                        banco.equals(SIN_CUENTA) ? t.tipoPago().nombre() : t.ctaSueldo().trim(), "PEN",
                        lista.get(i).resultado().netoAPagar()));
            }
            hojas.add(new RonaldmichaTablaExcel.Hoja(banco, banco + " · " + mesTexto(mes), List.of("N.°", "Tipo doc.", "Documento",
                    "Apellidos y nombres", banco.equals(SIN_CUENTA) ? "Forma de pago" : "Cuenta", "Moneda",
                    "Importe (S/)"), filas, false, List.of(6)));
        });
        return RonaldmichaTablaExcel.crear(hojas);
    }

    private byte[] pagosTxt(YearMonth mes) {
        StringBuilder sb = new StringBuilder();
        pagosPorBanco(mes).forEach((banco, lista) -> {
            if (banco.equals(SIN_CUENTA)) {
                return;
            }
            for (FilaPlanilla f : lista) {
                RonaldmichaTrabajador t = f.trabajador();
                sb.append(t.tipoDoc().codigo()).append('|').append(t.numDoc()).append('|')
                        .append(nombreMayusculas(t)).append('|').append(banco).append('|').append(t.ctaSueldo().trim())
                        .append("|PEN|").append(f.resultado().netoAPagar().toPlainString()).append("\r\n");
            }
        });
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private RonaldmichaDocumentModel pagosVista(YearMonth mes) {
        List<Bloque> bloques = new ArrayList<>();
        Map<String, List<FilaPlanilla>> porBanco = pagosPorBanco(mes);
        porBanco.forEach((banco, lista) -> {
            bloques.add(new Heading(banco + " · " + lista.size() + " trabajadores"));
            List<List<String>> filas = lista.stream().map(f -> List.of(
                    f.trabajador().tipoDoc().codigo() + " " + f.trabajador().numDoc(), f.trabajador().nombreCompleto(),
                    banco.equals(SIN_CUENTA) ? f.trabajador().tipoPago().nombre() : f.trabajador().ctaSueldo(),
                    RonaldmichaFormatos.numero(f.resultado().netoAPagar()))).toList();
            BigDecimal total = RonaldmichaDinero.sumar(lista.stream().map(f -> f.resultado().netoAPagar()).toArray(BigDecimal[]::new));
            bloques.add(new Table(List.of("Documento", "Apellidos y nombres", "Cuenta", "Importe (S/)"), filas,
                    List.of("Total", "", "", RonaldmichaFormatos.numero(total)), List.of(false, false, false, true)));
        });
        BigDecimal total = RonaldmichaDinero.sumar(porBanco.values().stream().flatMap(List::stream)
                .map(f -> f.resultado().netoAPagar()).toArray(BigDecimal[]::new));
        bloques.add(new Total("TOTAL A ABONAR", total, RonaldmichaNumeroALetras.texto(total)));
        bloques.add(RonaldmichaPlantillas.firmasReporte(empresa.obtener()));
        return new RonaldmichaDocumentModel("PAGOS MASIVOS", mesTexto(mes) + " · neto a pagar por banco", List.of(),
                Orientacion.VERTICAL, bloques, null);
    }

    private static final List<String> COLUMNAS_AFPNET = List.of(
            "Número de secuencia", "CUSPP", "Tipo de documento de identidad", "Número de documento de identidad",
            "Apellido paterno", "Apellido materno", "Nombres", "Relación laboral", "Inicio de relación laboral",
            "Cese de relación laboral", "Excepción de aportar", "Remuneración asegurable",
            "Aporte voluntario del afiliado con fin previsional", "Aporte voluntario del afiliado sin fin previsional",
            "Aporte voluntario del empleador", "Tipo de trabajo o rubro");

    private Map<String, List<List<Object>>> afpnet(YearMonth mes) {
        Map<String, List<List<Object>>> porAfp = new TreeMap<>();
        for (FilaPlanilla f : filas(mes)) {
            RonaldmichaTrabajador t = f.trabajador();
            if (!t.sistemaPension().esAfp()) {
                continue;
            }
            List<List<Object>> lista = porAfp.computeIfAbsent(t.sistemaPension().nombre(), k -> new ArrayList<>());
            boolean inicio = YearMonth.from(t.fechaIngreso()).equals(mes);
            boolean cese = t.fechaCese() != null && YearMonth.from(t.fechaCese()).equals(mes);
            lista.add(List.of(lista.size() + 1, valor(t.cuspp()), tipoDocAfpnet(t.tipoDoc()), t.numDoc(), t.apPaterno(),
                    t.apMaterno() == null ? "" : t.apMaterno(), t.nombres(), "S", inicio ? "S" : "N", cese ? "S" : "N",
                    "", f.resultado().basePension(), RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, "N"));
        }
        return porAfp;
    }

    private byte[] afpnetExcel(YearMonth mes) {
        List<RonaldmichaTablaExcel.Hoja> hojas = new ArrayList<>();
        afpnet(mes).forEach((afp, filas) -> hojas.add(new RonaldmichaTablaExcel.Hoja(afp, null, COLUMNAS_AFPNET, filas, false)));
        if (hojas.isEmpty()) {
            hojas.add(new RonaldmichaTablaExcel.Hoja("AFPnet", null, COLUMNAS_AFPNET, List.of(), false));
        }
        return RonaldmichaTablaExcel.crear(hojas);
    }

    private RonaldmichaDocumentModel afpnetVista(YearMonth mes) {
        List<Bloque> bloques = new ArrayList<>();
        Map<String, List<List<Object>>> porAfp = afpnet(mes);
        Map<String, List<AportePension>> aportes = new TreeMap<>();
        for (FilaPlanilla f : filas(mes)) {
            if (f.trabajador().sistemaPension().esAfp()) {
                aportes.computeIfAbsent(f.trabajador().sistemaPension().nombre(), k -> new ArrayList<>())
                        .add(f.resultado().pension());
            }
        }
        porAfp.forEach((afp, filas) -> {
            bloques.add(new Heading(afp + " · " + filas.size() + " afiliados"));
            List<AportePension> deLaAfp = aportes.get(afp);
            List<List<String>> lineas = new ArrayList<>();
            for (int i = 0; i < filas.size(); i++) {
                List<Object> f = filas.get(i);
                AportePension a = deLaAfp.get(i);
                lineas.add(List.of(f.get(0).toString(), f.get(1).toString(), f.get(3).toString(),
                        f.get(4) + " " + f.get(5) + ", " + f.get(6), f.get(8).toString(), f.get(9).toString(),
                        RonaldmichaFormatos.numero((BigDecimal) f.get(11)), RonaldmichaFormatos.numero(a.aporte()), RonaldmichaFormatos.numero(a.prima()),
                        RonaldmichaFormatos.numero(a.comision())));
            }
            bloques.add(new Table(List.of("N.°", "CUSPP", "Documento", "Apellidos y nombres", "Inicio", "Cese",
                    "Rem. asegurable", "Aporte", "Prima", "Comisión"), lineas, List.of(),
                    List.of(false, false, false, false, false, false, true, true, true, true)));
        });
        if (bloques.isEmpty()) {
            bloques.add(new Paragraph("No hay afiliados a AFP en la planilla del mes."));
        }
        BigDecimal asegurable = RonaldmichaDinero.sumar(porAfp.values().stream().flatMap(List::stream)
                .map(f -> (BigDecimal) f.get(11)).toArray(BigDecimal[]::new));
        bloques.add(new Total("TOTAL REMUNERACIÓN ASEGURABLE", asegurable, RonaldmichaNumeroALetras.texto(asegurable)));
        bloques.add(RonaldmichaPlantillas.firmasReporte(empresa.obtener()));
        return new RonaldmichaDocumentModel("AFPnet · planilla de aportes", mesTexto(mes) + " · una hoja por AFP", List.of(),
                Orientacion.VERTICAL, bloques, null);
    }

    private static String tipoDocAfpnet(RonaldmichaTipoDocumento t) {
        return switch (t) {
            case DNI -> "0";
            case CE -> "1";
            case PAS -> "4";
        };
    }

    private RonaldmichaDocumentModel resumenPlame(YearMonth mes) {
        List<FilaPlanilla> lista = filas(mes);
        Map<String, Object[]> porCodigo = new TreeMap<>();
        for (FilaPlanilla f : lista) {
            for (Concepto c : f.resultado().conceptos()) {
                if (c.codigoPlame() == null) {
                    continue;
                }
                Object[] acumulado = porCodigo.computeIfAbsent(c.codigoPlame(),
                        k -> new Object[]{c.descripcion(), c.tipo(), 0, RonaldmichaDinero.CERO});
                acumulado[2] = (Integer) acumulado[2] + 1;
                acumulado[3] = RonaldmichaDinero.sumar((BigDecimal) acumulado[3], c.monto());
            }
        }
        List<List<String>> conceptos = porCodigo.entrySet().stream().map(en -> List.of(en.getKey(),
                (String) en.getValue()[0], tipoTexto((TipoConcepto) en.getValue()[1]), en.getValue()[2].toString(),
                RonaldmichaFormatos.numero((BigDecimal) en.getValue()[3]))).toList();
        List<List<String>> trabajadoresFilas = lista.stream().map(f -> List.of(
                f.trabajador().tipoDoc().codigo() + " " + f.trabajador().numDoc(), f.trabajador().nombreCompleto(),
                RonaldmichaFormatos.numero(f.resultado().remuneracionAfecta()), RonaldmichaFormatos.numero(f.resultado().pension().total()),
                RonaldmichaFormatos.numero(f.resultado().rentaQuinta()), RonaldmichaFormatos.numero(f.resultado().essalud()),
                RonaldmichaFormatos.numero(f.resultado().netoAPagar()))).toList();
        RonaldmichaEmpresa e = empresa.obtener();
        BigDecimal afectas = suma(lista, RonaldmichaResultadoPlanilla::remuneracionAfecta);
        BigDecimal neto = suma(lista, RonaldmichaResultadoPlanilla::netoAPagar);
        List<List<String>> archivos = plame(mes, e).entrySet().stream().map(a -> List.of(a.getKey(),
                descripcionPlame(a.getKey()), String.valueOf(a.getValue().lines().count()))).toList();
        List<Bloque> bloques = List.of(
                new KeyValue(List.of(new Par("Periodo", mesTexto(mes)), new Par("RUC", valor(e.ruc())),
                        new Par("Trabajadores", String.valueOf(lista.size())),
                        new Par("Remuneraciones afectas", RonaldmichaFormatos.moneda(afectas)),
                        new Par("Pensiones (ONP/AFP)", RonaldmichaFormatos.moneda(suma(lista, r -> r.pension().total()))),
                        new Par("Renta de 5.ª", RonaldmichaFormatos.moneda(suma(lista, RonaldmichaResultadoPlanilla::rentaQuinta))),
                        new Par("EsSalud", RonaldmichaFormatos.moneda(suma(lista, RonaldmichaResultadoPlanilla::essalud))),
                        new Par("Neto pagado", RonaldmichaFormatos.moneda(suma(lista, RonaldmichaResultadoPlanilla::netoAPagar))))),
                new Heading("Conceptos por código PLAME"),
                new Table(List.of("Código", "Concepto", "Tipo", "Trabajadores", "Monto (S/)"), conceptos, List.of(),
                        List.of(false, false, false, true, true)),
                new Heading("Por trabajador"),
                new Table(List.of("Documento", "Apellidos y nombres", "Rem. afecta", "ONP/AFP", "Renta 5.ª", "EsSalud",
                        "Neto"), trabajadoresFilas, List.of(), List.of(false, false, true, true, true, true, true)),
                new Heading("Archivos del ZIP"),
                new Table(List.of("Archivo", "Contenido", "Líneas"), archivos, List.of(), List.of(false, false, true)),
                new Paragraph("Los códigos de suspensión (07 falta, 21 incapacidad subsidiada, 23 vacaciones) "
                        + "deben validarse con la tabla 21 de SUNAT antes de declarar."),
                new Total("NETO PAGADO", neto, RonaldmichaNumeroALetras.texto(neto)),
                RonaldmichaPlantillas.firmasReporte(e));
        return new RonaldmichaDocumentModel("RESUMEN PLAME", mesTexto(mes) + " · " + nombrePlame(mes, e), List.of(),
                Orientacion.HORIZONTAL, bloques, null);
    }

    private static String descripcionPlame(String archivo) {
        return archivo.endsWith(".rem") ? "Ingresos, descuentos y aportes"
                : archivo.endsWith(".jor") ? "Jornada laboral" : "Suspensiones (días no laborados)";
    }

    private Map<String, String> plame(YearMonth mes, RonaldmichaEmpresa e) {
        String base = nombrePlame(mes, e);
        StringBuilder rem = new StringBuilder();
        StringBuilder jor = new StringBuilder();
        StringBuilder snl = new StringBuilder();
        for (FilaPlanilla f : filas(mes)) {
            RonaldmichaTrabajador t = f.trabajador();
            RonaldmichaResultadoPlanilla r = f.resultado();
            String id = tipoDocSunat(t.tipoDoc()) + "|" + t.numDoc() + "|";
            Map<String, BigDecimal> porCodigo = new TreeMap<>();
            r.conceptos().stream().filter(c -> c.codigoPlame() != null)
                    .forEach(c -> porCodigo.merge(c.codigoPlame(), c.monto().abs(), BigDecimal::add));
            porCodigo.forEach((codigo, monto) -> rem.append(id).append(codigo).append('|')
                    .append(RonaldmichaDinero.redondear(monto).toPlainString()).append('|')
                    .append(RonaldmichaDinero.redondear(monto).toPlainString()).append("|\r\n"));
            int minutosOrdinarios = f.horasOrdinarias().multiply(BigDecimal.valueOf(60))
                    .setScale(0, RoundingMode.HALF_UP).intValue();
            jor.append(id).append(minutosOrdinarios / 60).append('|').append(minutosOrdinarios % 60).append('|')
                    .append(f.minutosExtra() / 60).append('|').append(f.minutosExtra() % 60).append("|\r\n");
            agregarSuspension(snl, id, "07", r.inasistencias());
            agregarSuspension(snl, id, "21", r.diasSubsidiados());
            agregarSuspension(snl, id, "23", r.diasVacaciones());
        }
        Map<String, String> archivos = new LinkedHashMap<>();
        archivos.put(base + ".rem", rem.toString());
        archivos.put(base + ".jor", jor.toString());
        archivos.put(base + ".snl", snl.toString());
        return archivos;
    }

    private static void agregarSuspension(StringBuilder sb, String id, String codigo, int dias) {
        if (dias > 0) {
            sb.append(id).append(codigo).append('|').append(dias).append("|\r\n");
        }
    }

    private byte[] plameZip(YearMonth mes, RonaldmichaEmpresa e) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (Map.Entry<String, String> archivo : plame(mes, e).entrySet()) {
                zip.putNextEntry(new ZipEntry(archivo.getKey()));
                zip.write(archivo.getValue().getBytes(StandardCharsets.ISO_8859_1));
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el ZIP", ex);
        }
        return salida.toByteArray();
    }

    private static String nombrePlame(YearMonth mes, RonaldmichaEmpresa e) {
        return "0601" + AAAAMM.format(mes) + (vacio(e.ruc()) ? "00000000000" : e.ruc().trim());
    }

    private static String tipoDocSunat(RonaldmichaTipoDocumento t) {
        return switch (t) {
            case DNI -> "01";
            case CE -> "04";
            case PAS -> "07";
        };
    }

    private List<RonaldmichaDocumentoEmitido> registrar(RonaldmichaReporte r, YearMonth mes, Formato formato, String archivo,
                                             List<Pieza> piezas, RonaldmichaEstadoDocumento estado) {
        LocalDateTime ahora = ahora();
        return documentos.registrar(piezas.stream().map(x -> new RonaldmichaDocumentoEmitido(null, null, r.tipo(),
                x.trabajador().id(), x.trabajador().nombreCompleto(), mes, formato.etiqueta(), ahora, estado,
                r.requiereCargo(), archivo)).toList());
    }

    private void auditar(RonaldmichaUsuario usuario, RonaldmichaReporte r, Formato formato, List<String> numeros, String detalle) {
        String rango = numeros.size() == 1 ? numeros.getFirst() : numeros.getFirst() + " a " + numeros.getLast();
        auditoria.registrar(usuario.usuario(), "DOCUMENTO_EMITIDO", rango + " · " + r.titulo() + " · "
                + formato.etiqueta() + " · " + detalle);
    }

    private void exigirCatalogo(RonaldmichaUsuario usuario, RonaldmichaReporte r) {
        if (usuario == null) {
            throw new RonaldmichaAccesoDenegadoException("Inicie sesión.");
        }
        if (esTrabajador(usuario) && !r.paraTrabajador()) {
            throw new RonaldmichaAccesoDenegadoException("Su usuario no tiene acceso a " + r.titulo().toLowerCase(RonaldmichaFormatos.LOCALE_PE) + ".");
        }
    }

    private void exigirAcceso(RonaldmichaUsuario usuario, RonaldmichaReporte r, Parametros p) {
        exigirCatalogo(usuario, r);
        if (esTrabajador(usuario) && (p.todos() || !Objects.equals(p.trabajadorId(), usuario.trabajadorId()))) {
            throw new RonaldmichaAccesoDenegadoException("Solo puede generar sus propios documentos.");
        }
        YearMonth periodo = esTrabajador(usuario) ? periodoDelDocumento(r, p.mes()) : null;
        if (periodo != null && planilla.situacionesDelAnio(periodo.getYear()).get(periodo) != RonaldmichaSituacionPeriodo.CERRADO) {
            String documento = r == RonaldmichaReporte.BOLETA_PAGO ? "La boleta" : r.titulo();
            throw new RonaldmichaValidacionException(documento + " de " + mesTexto(periodo).toLowerCase(RonaldmichaFormatos.LOCALE_PE)
                    + " aún no está disponible: el periodo no está cerrado.");
        }
        if (p.todos() && !r.todosEnUnPdf()) {
            throw new RonaldmichaValidacionException(r.titulo() + " se genera por trabajador.");
        }
    }

    private static YearMonth periodoDelDocumento(RonaldmichaReporte r, YearMonth mes) {
        return switch (r) {
            case BOLETA_PAGO, RENTA_QUINTA -> mes;
            case GRATIFICACION, CTS -> {
                RonaldmichaSemestreBeneficio s = semestre(r, mes);
                yield YearMonth.of(s.anio(), s.mesPago());
            }
            default -> null;
        };
    }

    private static RonaldmichaDocumentModel copias(RonaldmichaDocumentModel d, Parametros p) {
        return p.dosCopias() ? d : d.sinCopias();
    }

    private byte[] pdf(RonaldmichaDocumentModel d, String nombre, RonaldmichaEmpresa e) {
        return RonaldmichaPdfRenderer.render(d.conArchivo(nombre), e, empresa.avisoDatosIncompletos(e), ahora());
    }

    private static RonaldmichaDocumentModel.Firma recibi(RonaldmichaTrabajador t) {
        return RonaldmichaPlantillas.recibiConforme(t.nombreCompleto(), t.tipoDoc().codigo() + " " + t.numDoc());
    }

    private List<FilaPlanilla> filas(YearMonth mes) {
        PlanillaMes pm = planilla.delMes(mes);
        pm.error().ifPresent(e -> {
            throw new RonaldmichaValidacionException(e);
        });
        return pm.filas();
    }

    private List<FilaBeneficio> filasBeneficio(RonaldmichaReporte r, YearMonth mes) {
        RonaldmichaBeneficiosService.Beneficio b = beneficios.calcular(semestre(r, mes));
        b.error().ifPresent(e -> {
            throw new RonaldmichaValidacionException(e);
        });
        return b.filas();
    }

    private static RonaldmichaSemestreBeneficio semestre(RonaldmichaReporte r, YearMonth mes) {
        return RonaldmichaSemestreBeneficio.vigenteEn(r == RonaldmichaReporte.CTS ? RonaldmichaTipoBeneficio.CTS : RonaldmichaTipoBeneficio.GRATIFICACION, mes);
    }

    private Optional<LocalDate> cese(RonaldmichaTrabajador t) {
        if (t.fechaCese() != null) {
            return Optional.of(t.fechaCese());
        }
        return liquidaciones.delTrabajador(t.id()).map(RonaldmichaLiquidacion::fechaCese);
    }

    private RonaldmichaTrabajador trabajador(Long id) {
        if (id == null) {
            throw new RonaldmichaValidacionException("Elija un trabajador.");
        }
        return trabajadores.buscarPorId(id).orElseThrow(() -> new RonaldmichaValidacionException("El trabajador no existe."));
    }

    private static BigDecimal suma(List<FilaPlanilla> filas, Function<RonaldmichaResultadoPlanilla, BigDecimal> valor) {
        return RonaldmichaDinero.sumar(filas.stream().map(f -> valor.apply(f.resultado())).toArray(BigDecimal[]::new));
    }

    private static String base(RonaldmichaReporte r, YearMonth mes) {
        return r.name().toLowerCase(RonaldmichaFormatos.LOCALE_PE) + "_" + AAAAMM.format(mes);
    }

    private static String pension(RonaldmichaTrabajador t) {
        return t.sistemaPension().nombre() + (t.sistemaPension().esAfp() && t.tipoComision() != null
                ? " · " + t.tipoComision().codigo() : "");
    }

    private static String horas(BigDecimal horas) {
        int minutos = horas.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValue();
        return minutos / 60 + ":" + String.format("%02d", minutos % 60);
    }

    private static String tipoTexto(TipoConcepto t) {
        return switch (t) {
            case INGRESO -> "Ingreso";
            case REBAJA -> "Rebaja";
            case DESCUENTO -> "Descuento";
            case APORTE -> "Aporte";
        };
    }

    private static String nombreMayusculas(RonaldmichaTrabajador t) {
        return (t.apellidos() + " " + t.nombres()).toUpperCase(RonaldmichaFormatos.LOCALE_PE);
    }

    private static boolean conCuenta(RonaldmichaTrabajador t) {
        return t.tipoPago() == RonaldmichaTipoPago.DEPOSITO && !vacio(t.bancoSueldo()) && !vacio(t.ctaSueldo());
    }

    private static boolean cusppValido(String cuspp) {
        return cuspp != null && cuspp.trim().length() == LONGITUD_CUSPP;
    }

    static String mesTexto(YearMonth m) {
        String s = MES.format(m);
        return s.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + s.substring(1);
    }

    private static String mesNombre(YearMonth m) {
        return m.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, RonaldmichaFormatos.LOCALE_PE)
                .toLowerCase(RonaldmichaFormatos.LOCALE_PE);
    }

    private static boolean esTrabajador(RonaldmichaUsuario u) {
        return u != null && u.rol() == RonaldmichaRol.TRABAJADOR;
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static String valor(String s) {
        return vacio(s) ? "—" : s;
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
    }
}
