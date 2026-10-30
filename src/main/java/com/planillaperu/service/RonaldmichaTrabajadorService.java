package com.planillaperu.service;

import com.planillaperu.calc.RonaldmichaAportePensionCalculator;
import com.planillaperu.calc.RonaldmichaAportePensionCalculator.AportePension;
import com.planillaperu.calc.RonaldmichaJornadaCalculator;
import com.planillaperu.calc.RonaldmichaJornadaCalculator.Jornada;
import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaAprobacion;
import com.planillaperu.domain.RonaldmichaHorario;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaSalud;
import com.planillaperu.domain.RonaldmichaSistemaPension;
import com.planillaperu.domain.RonaldmichaTipoComision;
import com.planillaperu.domain.RonaldmichaTipoDocumento;
import com.planillaperu.domain.RonaldmichaTipoPago;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaTrabajadorResumen;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.export.RonaldmichaLectorTabla.Fila;
import com.planillaperu.export.RonaldmichaPlantillaTrabajadores;
import com.planillaperu.repository.RonaldmichaAprobacionRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class RonaldmichaTrabajadorService {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy");

    public record Guardado(RonaldmichaTrabajador trabajador, Optional<RonaldmichaAprobacion> pendiente) {
    }

    public record ResultadoImportacion(int importados, List<String> observados) {

        public ResultadoImportacion {
            observados = List.copyOf(observados);
        }

        public String mensaje() {
            String base = importados + (importados == 1 ? " importado" : " importados");
            return observados.isEmpty() ? base + " · sin observaciones" : base + " · Observados: " + String.join("; ", observados);
        }
    }

    private final RonaldmichaTrabajadorRepository repositorio;
    private final RonaldmichaAprobacionRepository aprobaciones;
    private final RonaldmichaParametrosService parametros;
    private final RonaldmichaEmpresaService empresa;
    private final RonaldmichaAuditoriaService auditoria;
    private final Clock reloj;

    public RonaldmichaTrabajadorService(RonaldmichaTrabajadorRepository repositorio, RonaldmichaAprobacionRepository aprobaciones,
                             RonaldmichaParametrosService parametros, RonaldmichaEmpresaService empresa, RonaldmichaAuditoriaService auditoria,
                             Clock reloj) {
        this.repositorio = repositorio;
        this.aprobaciones = aprobaciones;
        this.parametros = parametros;
        this.empresa = empresa;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public List<RonaldmichaTrabajadorResumen> activos() {
        return repositorio.listarActivos(hoy());
    }

    public List<RonaldmichaTrabajador> todos() {
        return repositorio.listarTodos();
    }

    public Optional<RonaldmichaTrabajador> buscar(long id) {
        return repositorio.buscarPorId(id);
    }

    public Optional<RonaldmichaHorario> horario(long id) {
        return repositorio.horario(id);
    }

    public String siguienteCodigo() {
        return repositorio.siguienteCodigo();
    }

    public boolean activo(RonaldmichaTrabajador t) {
        return t.activoEn(hoy());
    }

    public Optional<RonaldmichaAprobacion> cambioPendiente(long trabajadorId) {
        return aprobaciones.pendienteDe(trabajadorId);
    }

    public Jornada jornada(RonaldmichaHorario h) {
        return RonaldmichaJornadaCalculator.calcular(h);
    }

    public BigDecimal asignacionFamiliar(YearMonth periodo) {
        return RonaldmichaAportePensionCalculator.asignacionFamiliar(parametros.vigentes(periodo));
    }

    public AportePension aportePension(RonaldmichaSistemaPension sistema, RonaldmichaTipoComision tipoComision, BigDecimal sueldo,
                                       boolean asignacionFamiliar, YearMonth periodo) {
        RonaldmichaParametrosVersion p = parametros.vigentes(periodo);
        return RonaldmichaAportePensionCalculator.calcular(p, sistema,
                tipoComision == null ? RonaldmichaTipoComision.FLUJO : tipoComision,
                sueldo == null ? RonaldmichaDinero.CERO : sueldo, asignacionFamiliar);
    }

    public List<String> validar(RonaldmichaTrabajador entrada, RonaldmichaHorario horario, YearMonth periodo) {
        RonaldmichaTrabajador t = normalizar(entrada);
        List<String> errores = new ArrayList<>();

        if (t.tipoDoc() == null) {
            errores.add("Elija el tipo de documento.");
        } else if (t.numDoc() == null) {
            errores.add("Ingrese el número de documento.");
        } else if (t.tipoDoc() == RonaldmichaTipoDocumento.DNI && !t.numDoc().matches("\\d{8}")) {
            errores.add("El DNI debe tener 8 dígitos.");
        } else if (t.tipoDoc() != RonaldmichaTipoDocumento.DNI && !t.numDoc().matches("[A-Z0-9]{1,12}")) {
            errores.add("El número de documento admite hasta 12 letras o números.");
        } else if (repositorio.existeDocumento(t.numDoc(), t.id())) {
            errores.add(t.tipoDoc().codigo() + " duplicado: el " + t.numDoc() + " ya está registrado.");
        }
        if (t.codigo() != null && repositorio.existeCodigo(t.codigo(), t.id())) {
            errores.add("Código duplicado: " + t.codigo() + " ya está registrado.");
        }
        if (t.apPaterno() == null) {
            errores.add("Ingrese el apellido paterno.");
        }
        if (t.nombres() == null) {
            errores.add("Ingrese los nombres.");
        }
        if (t.correo() != null && !t.correo().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            errores.add("El correo no es válido.");
        }
        if (t.celular() != null && !t.celular().matches("\\+?\\d{6,15}")) {
            errores.add("El celular solo admite números (6 a 15 dígitos).");
        }

        if (t.fechaIngreso() == null) {
            errores.add("Ingrese la fecha de ingreso.");
        } else if (t.fechaCese() != null && t.fechaCese().isBefore(t.fechaIngreso())) {
            errores.add("La fecha de cese no puede ser anterior a la de ingreso.");
        }
        if (t.fechaNacimiento() != null && t.fechaIngreso() != null && !t.fechaNacimiento().isBefore(t.fechaIngreso())) {
            errores.add("La fecha de nacimiento debe ser anterior a la de ingreso.");
        }
        if (t.hijos() < 0) {
            errores.add("El número de hijos debe ser un entero de 0 o más.");
        }
        if (t.area() == null) {
            errores.add("Ingrese el área.");
        }
        if (t.cargo() == null) {
            errores.add("Ingrese el cargo.");
        }
        if (t.regimen() == null) {
            errores.add("Elija el régimen laboral.");
        }

        Jornada jornada = null;
        if (horario == null) {
            errores.add("Complete el horario (ingreso, salida y días laborables).");
        } else if (horario.diasLaborables().isEmpty()) {
            errores.add("Marque al menos un día laborable.");
        } else if (horario.refrigerioMin() < 0 || horario.toleranciaMin() < 0) {
            errores.add("El refrigerio y la tolerancia no pueden ser negativos.");
        } else {
            jornada = RonaldmichaJornadaCalculator.calcular(horario);
            if (jornada.horasDiarias().signum() <= 0) {
                errores.add("El horario no deja horas de trabajo: revise ingreso, salida y refrigerio.");
            }
        }

        if (t.sueldo() == null || t.sueldo().signum() <= 0) {
            errores.add("Ingrese el sueldo básico.");
        } else if (jornada != null && !jornada.tiempoParcial()) {
            try {
                BigDecimal rmv = parametros.vigentes(periodo).rmv();
                if (t.sueldo().compareTo(rmv) < 0) {
                    errores.add("El sueldo (" + RonaldmichaFormatos.moneda(t.sueldo()) + ") es menor que la RMV de "
                            + MES.format(periodo) + " (" + RonaldmichaFormatos.moneda(rmv) + "). Solo se permite en tiempo "
                            + "parcial (jornada diaria menor a " + RonaldmichaJornadaCalculator.HORAS_TIEMPO_PARCIAL + " horas).");
                }
            } catch (RonaldmichaParametroNoEncontradoException e) {
                errores.add(e.getMessage());
            }
        }
        if (t.movilidad() == null || t.movilidad().signum() < 0) {
            errores.add("La movilidad debe ser 0 o más.");
        }

        if (t.salud() == null) {
            errores.add("Elija la cobertura de salud.");
        } else if (t.salud() == RonaldmichaSalud.SIS && t.regimen() != null && t.regimen() != RonaldmichaRegimenLaboral.MICRO) {
            errores.add("El SIS solo corresponde a trabajadores de microempresa.");
        }
        if (t.sistemaPension() == null) {
            errores.add("Elija el sistema de pensiones.");
        } else if (t.sistemaPension().esAfp()) {
            if (t.cuspp() == null || !t.cuspp().matches("[A-Z0-9]{12}")) {
                errores.add("El CUSPP es obligatorio para AFP y debe tener 12 caracteres (letras y números).");
            }
            if (t.tipoComision() == null) {
                errores.add("Elija el tipo de comisión AFP (flujo o mixta).");
            }
        }

        if (t.tipoPago() == null) {
            errores.add("Elija la forma de pago.");
        } else if (t.tipoPago() == RonaldmichaTipoPago.DEPOSITO && (t.bancoSueldo() == null || t.ctaSueldo() == null)) {
            errores.add("Para depósito en cuenta indique el banco y la cuenta de sueldo.");
        }
        return errores;
    }

    public Guardado guardar(RonaldmichaTrabajador entrada, RonaldmichaHorario horario, RonaldmichaUsuario usuario, YearMonth periodo) {
        RonaldmichaPermisos.exigirAdmin(usuario, "registrar o modificar trabajadores");
        RonaldmichaTrabajador t = normalizar(entrada);
        List<String> errores = validar(t, horario, periodo);
        if (!errores.isEmpty()) {
            throw new RonaldmichaValidacionException(errores);
        }
        if (t.id() == null) {
            RonaldmichaTrabajador nuevo = registrar(t, horario);
            auditoria.registrar(usuario.usuario(), "TRABAJADOR_REGISTRADO", describir(nuevo)
                    + " · Sueldo " + RonaldmichaFormatos.moneda(nuevo.sueldo()));
            return new Guardado(nuevo, Optional.empty());
        }

        RonaldmichaTrabajador actual = repositorio.buscarPorId(t.id())
                .orElseThrow(() -> new RonaldmichaValidacionException("El trabajador no existe."));
        Map<String, String> cambios = new LinkedHashMap<>();
        List<String> detalle = new ArrayList<>();
        boolean cambiaSueldo = actual.sueldo().compareTo(t.sueldo()) != 0;
        if (cambiaSueldo) {
            cambios.put("sueldo", t.sueldo().toPlainString());
            detalle.add("Sueldo: " + RonaldmichaFormatos.moneda(actual.sueldo()) + " → " + RonaldmichaFormatos.moneda(t.sueldo()));
        }
        boolean cambiaCtaSueldo = !Objects.equals(actual.bancoSueldo(), t.bancoSueldo())
                || !Objects.equals(actual.ctaSueldo(), t.ctaSueldo());
        if (cambiaCtaSueldo) {
            cambios.put("banco_sueldo", n(t.bancoSueldo()));
            cambios.put("cta_sueldo", n(t.ctaSueldo()));
            detalle.add("Cuenta sueldo: " + cuenta(actual.bancoSueldo(), actual.ctaSueldo()) + " → "
                    + cuenta(t.bancoSueldo(), t.ctaSueldo()));
        }
        boolean cambiaCtaCts = !Objects.equals(actual.bancoCts(), t.bancoCts())
                || !Objects.equals(actual.ctaCts(), t.ctaCts());
        if (cambiaCtaCts) {
            cambios.put("banco_cts", n(t.bancoCts()));
            cambios.put("cta_cts", n(t.ctaCts()));
            detalle.add("Cuenta CTS: " + cuenta(actual.bancoCts(), actual.ctaCts()) + " → "
                    + cuenta(t.bancoCts(), t.ctaCts()));
        }

        if (cambiaSueldo && Objects.equals(usuario.trabajadorId(), t.id())) {
            auditoria.registrar(usuario.usuario(), "CAMBIO_SUELDO_PROPIO_RECHAZADO", describir(actual));
            throw new RonaldmichaAccesoDenegadoException("Un administrador no puede cambiar su propio sueldo.");
        }
        if (!cambios.isEmpty()) {
            aprobaciones.pendienteDe(t.id()).ifPresent(p -> {
                throw new RonaldmichaValidacionException("Ya hay un cambio pendiente de aprobación para este trabajador ("
                        + p.tipo().toLowerCase() + ", registrado por " + p.registradoPor() + " el "
                        + RonaldmichaFormatos.fechaHora(p.fecha()) + "). Espere a que se apruebe o rechace.");
            });
        }

        repositorio.actualizar(t.conDatosSensiblesDe(actual), horario);
        auditoria.registrar(usuario.usuario(), "TRABAJADOR_ACTUALIZADO", describir(t));
        if (cambios.isEmpty()) {
            return new Guardado(repositorio.buscarPorId(t.id()).orElseThrow(), Optional.empty());
        }

        String tipo = cambiaSueldo && (cambiaCtaSueldo || cambiaCtaCts) ? "Cambio de sueldo y cuentas"
                : cambiaSueldo ? "Cambio de sueldo" : "Cambio de cuentas bancarias";
        RonaldmichaAprobacion solicitud = aprobaciones.insertar(new RonaldmichaAprobacion(null, tipo, t.id(), String.join(" · ", detalle),
                cambios, usuario.usuario(), ahora(), null, null, null, null));
        auditoria.registrar(usuario.usuario(), "APROBACION_SOLICITADA",
                "Solicitud #" + solicitud.id() + " · " + tipo + " · " + describir(t) + " · " + solicitud.detalle());
        return new Guardado(repositorio.buscarPorId(t.id()).orElseThrow(), Optional.of(solicitud));
    }

    public enum TipoCuenta {
        SUELDO("Cuenta sueldo", "banco_sueldo", "cta_sueldo"),
        CTS("Cuenta CTS", "banco_cts", "cta_cts");

        private final String etiqueta;
        private final String columnaBanco;
        private final String columnaCuenta;

        TipoCuenta(String etiqueta, String columnaBanco, String columnaCuenta) {
            this.etiqueta = etiqueta;
            this.columnaBanco = columnaBanco;
            this.columnaCuenta = columnaCuenta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }

    public record MisDatos(RonaldmichaTrabajador trabajador, Optional<RonaldmichaAprobacion> pendiente) {
    }

    public MisDatos misDatos(RonaldmichaUsuario usuario) {
        long id = RonaldmichaPermisos.exigirPortal(usuario, "ver sus datos");
        RonaldmichaTrabajador t = repositorio.buscarPorId(id).orElseThrow(() -> new RonaldmichaValidacionException("El trabajador no existe."));
        return new MisDatos(t, aprobaciones.pendienteDe(id));
    }

    public RonaldmichaTrabajador actualizarContacto(RonaldmichaUsuario usuario, String celular, String correo) {
        long id = RonaldmichaPermisos.exigirPortal(usuario, "actualizar sus datos");
        RonaldmichaTrabajador actual = repositorio.buscarPorId(id).orElseThrow(() -> new RonaldmichaValidacionException("El trabajador no existe."));
        String nuevoCelular = s(celular);
        String nuevoCorreo = s(correo);
        List<String> errores = new ArrayList<>();
        if (nuevoCelular != null && !nuevoCelular.matches("\\+?\\d{6,15}")) {
            errores.add("El celular solo admite números (6 a 15 dígitos).");
        }
        if (nuevoCorreo != null && !nuevoCorreo.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            errores.add("El correo no es válido.");
        }
        if (!errores.isEmpty()) {
            throw new RonaldmichaValidacionException(errores);
        }
        if (Objects.equals(nuevoCelular, actual.celular()) && Objects.equals(nuevoCorreo, actual.correo())) {
            throw new RonaldmichaValidacionException("No hay cambios en el celular ni en el correo.");
        }
        repositorio.actualizarContacto(id, nuevoCelular, nuevoCorreo);
        auditoria.registrar(usuario.usuario(), "DATOS_CONTACTO_ACTUALIZADOS", describir(actual)
                + " · Celular: " + valor(actual.celular()) + " → " + valor(nuevoCelular)
                + " · Correo: " + valor(actual.correo()) + " → " + valor(nuevoCorreo));
        return repositorio.buscarPorId(id).orElseThrow();
    }

    public RonaldmichaAprobacion solicitarCambioCuenta(RonaldmichaUsuario usuario, TipoCuenta tipo, String banco, String cuenta) {
        long id = RonaldmichaPermisos.exigirPortal(usuario, "solicitar cambios de cuenta");
        RonaldmichaTrabajador t = repositorio.buscarPorId(id).orElseThrow(() -> new RonaldmichaValidacionException("El trabajador no existe."));
        String nuevoBanco = s(banco);
        String nuevaCuenta = s(cuenta);
        List<String> errores = new ArrayList<>();
        if (tipo == null) {
            errores.add("Elija la cuenta que desea cambiar.");
        }
        if (nuevoBanco == null) {
            errores.add("Indique el banco.");
        }
        if (nuevaCuenta == null || !nuevaCuenta.matches("[0-9-]{6,30}")) {
            errores.add("El número de cuenta solo admite dígitos y guiones (6 a 30 caracteres).");
        }
        if (!errores.isEmpty()) {
            throw new RonaldmichaValidacionException(errores);
        }
        String bancoActual = tipo == TipoCuenta.SUELDO ? t.bancoSueldo() : t.bancoCts();
        String cuentaActual = tipo == TipoCuenta.SUELDO ? t.ctaSueldo() : t.ctaCts();
        if (nuevoBanco.equals(bancoActual) && nuevaCuenta.equals(cuentaActual)) {
            throw new RonaldmichaValidacionException("Su " + tipo.etiqueta().toLowerCase(RonaldmichaFormatos.LOCALE_PE) + " ya es esa.");
        }
        aprobaciones.pendienteDe(id).ifPresent(p -> {
            throw new RonaldmichaValidacionException("Ya tiene un cambio pendiente de aprobación (" + p.tipo().toLowerCase()
                    + ", del " + RonaldmichaFormatos.fechaHora(p.fecha()) + "). Espere a que se apruebe o rechace.");
        });
        Map<String, String> cambios = new LinkedHashMap<>();
        cambios.put(tipo.columnaBanco, nuevoBanco);
        cambios.put(tipo.columnaCuenta, nuevaCuenta);
        String detalle = tipo.etiqueta() + ": " + cuenta(bancoActual, cuentaActual) + " → "
                + cuenta(nuevoBanco, nuevaCuenta) + " · pedido por el trabajador desde «Mis datos»";
        RonaldmichaAprobacion solicitud = aprobaciones.insertar(new RonaldmichaAprobacion(null, "Cambio de cuentas bancarias", id, detalle,
                cambios, usuario.usuario(), ahora(), null, null, null, null));
        auditoria.registrar(usuario.usuario(), "APROBACION_SOLICITADA", "Solicitud #" + solicitud.id()
                + " · Cambio de cuentas bancarias · " + describir(t) + " · " + detalle);
        return solicitud;
    }

    private static String valor(String v) {
        return v == null ? "(vacío)" : v;
    }

    private RonaldmichaTrabajador registrar(RonaldmichaTrabajador t, RonaldmichaHorario horario) {
        String codigo = t.codigo() == null ? repositorio.siguienteCodigo() : t.codigo();
        return repositorio.insertar(t.conIdYCodigo(null, codigo), horario);
    }

    public ResultadoImportacion importar(List<Fila> filas, String archivo, RonaldmichaUsuario usuario, YearMonth periodo) {
        RonaldmichaPermisos.exigirAdmin(usuario, "importar trabajadores");
        if (filas.isEmpty()) {
            throw new RonaldmichaValidacionException("El archivo está vacío.");
        }
        Map<String, Integer> indices = RonaldmichaFilaTrabajador.indices(filas.getFirst().valores());
        List<String> faltan = RonaldmichaFilaTrabajador.columnasFaltantes(indices);
        if (!faltan.isEmpty()) {
            throw new RonaldmichaValidacionException("Faltan columnas de la plantilla: " + String.join(", ", faltan)
                    + ". Descargue la plantilla CSV.");
        }

        int importados = 0;
        List<String> observados = new ArrayList<>();
        Set<String> documentos = new HashSet<>();
        Set<String> codigos = new HashSet<>();
        for (Fila fila : filas.subList(1, filas.size())) {
            RonaldmichaFilaTrabajador lectura = RonaldmichaFilaTrabajador.de(indices, fila.valores());
            RonaldmichaTrabajador t = normalizar(lectura.trabajador());
            RonaldmichaHorario horario = lectura.horario();
            List<String> errores = new ArrayList<>(lectura.errores());
            if (t.numDoc() != null && !documentos.add(t.numDoc())) {
                errores.add("documento duplicado en el archivo (" + t.numDoc() + ")");
            }
            if (t.codigo() != null && !codigos.add(t.codigo())) {
                errores.add("código duplicado en el archivo (" + t.codigo() + ")");
            }
            if (errores.isEmpty()) {
                errores.addAll(validar(t, horario, periodo).stream().map(RonaldmichaTrabajadorService::sinPunto).toList());
            }
            if (!errores.isEmpty()) {
                observados.add("Fila " + fila.numero() + ": " + String.join(", ", errores));
                continue;
            }
            registrar(t, horario);
            importados++;
        }
        ResultadoImportacion resultado = new ResultadoImportacion(importados, observados);
        auditoria.registrar(usuario.usuario(), "TRABAJADORES_IMPORTADOS",
                (archivo == null ? "" : archivo + " · ") + resultado.mensaje());
        return resultado;
    }

    public byte[] plantillaCsv() {
        return RonaldmichaPlantillaTrabajadores.csv();
    }

    public byte[] exportarExcel(RonaldmichaUsuario usuario) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "exportar trabajadores");
        List<RonaldmichaTrabajador> lista = repositorio.listarTodos();
        Optional<String> aviso = empresa.avisoDatosIncompletos();
        byte[] excel = RonaldmichaPlantillaTrabajadores.excel(lista, repositorio.horarios(), aviso);
        auditoria.registrar(usuario.usuario(), "TRABAJADORES_EXPORTADOS",
                lista.size() + " trabajadores · Excel" + (aviso.isPresent() ? " · con aviso de datos de empresa incompletos" : ""));
        return excel;
    }

    static RonaldmichaTrabajador normalizar(RonaldmichaTrabajador t) {
        boolean afp = t.sistemaPension() != null && t.sistemaPension().esAfp();
        return new RonaldmichaTrabajador(t.id(), mayus(t.codigo()), t.tipoDoc(), mayus(t.numDoc()), s(t.apPaterno()),
                s(t.apMaterno()), s(t.nombres()), s(t.celular()), s(t.correo()), t.fechaIngreso(), t.fechaCese(),
                s(t.area()), s(t.cargo()), s(t.tipoContrato()), t.regimen(), s(t.centroCosto()), s(t.sucursal()),
                t.sueldo() == null ? null : RonaldmichaDinero.redondear(t.sueldo()),
                t.movilidad() == null ? null : RonaldmichaDinero.redondear(t.movilidad()),
                t.salud(), t.sistemaPension(), afp ? mayus(t.cuspp()) : null, afp ? t.tipoComision() : null,
                t.asigFamiliar(), t.vidaLey(), t.tipoPago(), s(t.bancoSueldo()), s(t.ctaSueldo()), s(t.bancoCts()),
                s(t.ctaCts()), t.fechaNacimiento(), t.hijos(), s(t.direccion()), t.categoria());
    }

    private static String describir(RonaldmichaTrabajador t) {
        return t.codigo() + " · " + t.tipoDoc().codigo() + " " + t.numDoc() + " · " + t.nombreCompleto();
    }

    private static String cuenta(String banco, String numero) {
        return banco == null && numero == null ? "(sin cuenta)" : (n(banco) + " " + n(numero)).trim();
    }

    private static String sinPunto(String mensaje) {
        return mensaje.endsWith(".") ? mensaje.substring(0, mensaje.length() - 1) : mensaje;
    }

    private static String s(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String mayus(String v) {
        String t = s(v);
        return t == null ? null : t.toUpperCase();
    }

    private static String n(String v) {
        return v == null ? "" : v;
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
    }
}
