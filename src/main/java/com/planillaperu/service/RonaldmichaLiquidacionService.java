package com.planillaperu.service;

import com.planillaperu.calc.RonaldmichaAportePensionCalculator;
import com.planillaperu.calc.RonaldmichaAportePensionCalculator.AportePension;
import com.planillaperu.calc.RonaldmichaLiquidacionCalculator;
import com.planillaperu.calc.RonaldmichaLiquidacionCalculator.Entrada;
import com.planillaperu.calc.RonaldmichaLiquidacionCalculator.Resultado;
import com.planillaperu.calc.RonaldmichaVacacionesCalculator;
import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.domain.RonaldmichaEstadoLiquidacion;
import com.planillaperu.domain.RonaldmichaLiquidacion;
import com.planillaperu.domain.RonaldmichaMotivoCese;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaSalud;
import com.planillaperu.domain.RonaldmichaSemestreBeneficio;
import com.planillaperu.domain.RonaldmichaSituacionPeriodo;
import com.planillaperu.domain.RonaldmichaTipoBeneficio;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.export.RonaldmichaConstanciaBeneficio.Dato;
import com.planillaperu.export.RonaldmichaReporteLiquidacion;
import com.planillaperu.export.RonaldmichaReporteLiquidacion.Bloque;
import com.planillaperu.export.RonaldmichaReporteLiquidacion.Linea;
import com.planillaperu.export.doc.RonaldmichaDocumentModel;
import com.planillaperu.export.doc.RonaldmichaPdfRenderer;
import com.planillaperu.export.doc.RonaldmichaPlantillas;
import com.planillaperu.export.doc.RonaldmichaWordRenderer;
import com.planillaperu.repository.RonaldmichaLiquidacionRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.repository.RonaldmichaVacacionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RonaldmichaLiquidacionService {

    public static final int FALTAS_MAXIMAS = 180;
    public static final String NOTA = "D.Leg. 713 (vacaciones), Ley 27735 y Ley 30334 (gratificación y bonificación), "
            + "D.S. 001-97-TR (CTS). Recuerde dar de baja al "
            + "trabajador en el T-Registro.";
    private static final DateTimeFormatter MES_ANIO = DateTimeFormatter.ofPattern("MMM yyyy", RonaldmichaFormatos.LOCALE_PE);
    private static final MonthDay PAGO_JULIO = MonthDay.of(7, 15);
    private static final MonthDay PAGO_DICIEMBRE = MonthDay.of(12, 15);

    public record Borrador(
            Long trabajadorId,
            LocalDate fechaCese,
            RonaldmichaMotivoCese motivo,
            BigDecimal ultimaGratificacion,
            int faltasCts,
            int faltasGratificacion,
            BigDecimal otrosIngresos,
            BigDecimal otrosDescuentos) {
    }

    public record Calculo(
            RonaldmichaTrabajador trabajador,
            Borrador borrador,
            RonaldmichaParametrosVersion parametros,
            RonaldmichaSemestreBeneficio semestreUltimaGratificacion,
            BigDecimal ultimaGratificacionCalculada,
            Resultado resultado,
            RonaldmichaReporteLiquidacion reporte) {
    }

    public record Fila(RonaldmichaLiquidacion liquidacion, RonaldmichaTrabajador trabajador) {
    }

    public record Totales(int cantidad, BigDecimal bruto, BigDecimal descuento, BigDecimal neto) {

        public static Totales de(List<Fila> filas) {
            return new Totales(filas.size(),
                    RonaldmichaDinero.sumar(filas.stream().map(f -> f.liquidacion().bruto()).toArray(BigDecimal[]::new)),
                    RonaldmichaDinero.sumar(filas.stream().map(f -> f.liquidacion().descuento()).toArray(BigDecimal[]::new)),
                    RonaldmichaDinero.sumar(filas.stream().map(f -> f.liquidacion().neto()).toArray(BigDecimal[]::new)));
        }
    }

    private record ClaveGrati(long trabajadorId, RonaldmichaSemestreBeneficio semestre) {
    }

    private final RonaldmichaTrabajadorRepository trabajadores;
    private final RonaldmichaLiquidacionRepository repositorio;
    private final RonaldmichaVacacionRepository vacaciones;
    private final RonaldmichaBeneficiosService beneficios;
    private final RonaldmichaParametrosService parametros;
    private final RonaldmichaPeriodoService periodos;
    private final RonaldmichaEmpresaService empresa;
    private final RonaldmichaAuditoriaService auditoria;
    private final Clock reloj;
    private ClaveGrati claveGrati;
    private BigDecimal valorGrati;

    public RonaldmichaLiquidacionService(RonaldmichaTrabajadorRepository trabajadores, RonaldmichaLiquidacionRepository repositorio,
                              RonaldmichaVacacionRepository vacaciones, RonaldmichaBeneficiosService beneficios, RonaldmichaParametrosService parametros,
                              RonaldmichaPeriodoService periodos, RonaldmichaEmpresaService empresa, RonaldmichaAuditoriaService auditoria, Clock reloj) {
        this.trabajadores = trabajadores;
        this.repositorio = repositorio;
        this.vacaciones = vacaciones;
        this.beneficios = beneficios;
        this.parametros = parametros;
        this.periodos = periodos;
        this.empresa = empresa;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public List<Fila> listar(RonaldmichaUsuario usuario) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "ver las liquidaciones");
        Map<Long, RonaldmichaTrabajador> porId = trabajadores.listarTodos().stream()
                .collect(Collectors.toMap(RonaldmichaTrabajador::id, Function.identity()));
        return repositorio.listar().stream().map(l -> new Fila(l, porId.get(l.trabajadorId()))).toList();
    }

    public Optional<RonaldmichaLiquidacion> buscar(long id) {
        return repositorio.buscar(id);
    }

    public Optional<RonaldmichaLiquidacion> delTrabajador(long trabajadorId) {
        return repositorio.delTrabajador(trabajadorId).stream().findFirst();
    }

    public List<RonaldmichaTrabajador> liquidables() {
        LocalDate hoy = LocalDate.now(reloj);
        return trabajadores.listarTodos().stream()
                .filter(t -> t.activoEn(hoy))
                .filter(t -> repositorio.delTrabajador(t.id()).isEmpty())
                .toList();
    }

    public static Borrador borrador(RonaldmichaLiquidacion l) {
        return new Borrador(l.trabajadorId(), l.fechaCese(), l.motivo(), l.ultimaGratificacion(), l.faltasCts(),
                l.faltasGratificacion(), l.otrosIngresos(), l.otrosDescuentos());
    }

    public void refrescar() {
        claveGrati = null;
        valorGrati = null;
    }

    public List<String> validar(Borrador b, Long id) {
        List<String> errores = validarDatos(b);
        if (b.trabajadorId() != null) {
            repositorio.delTrabajador(b.trabajadorId()).stream()
                    .filter(l -> !Objects.equals(l.id(), id))
                    .findFirst()
                    .ifPresent(l -> errores.add("El trabajador ya tiene una liquidación ("
                            + l.estado().etiqueta().toLowerCase(Locale.ROOT) + ")."));
        }
        return errores;
    }

    private List<String> validarDatos(Borrador b) {
        List<String> errores = new ArrayList<>();
        if (b.trabajadorId() == null) {
            errores.add("Elija al trabajador.");
            return errores;
        }
        RonaldmichaTrabajador t = trabajadores.buscarPorId(b.trabajadorId()).orElse(null);
        if (t == null) {
            errores.add("El trabajador no existe.");
            return errores;
        }
        if (b.fechaCese() == null) {
            errores.add("Indique la fecha de cese.");
        } else if (b.fechaCese().isBefore(t.fechaIngreso())) {
            errores.add("La fecha de cese no puede ser anterior al ingreso (" + RonaldmichaFormatos.fecha(t.fechaIngreso()) + ").");
        }
        if (b.motivo() == null) {
            errores.add("Elija el motivo del cese.");
        }
        if (b.faltasCts() < 0 || b.faltasCts() > FALTAS_MAXIMAS || b.faltasGratificacion() < 0
                || b.faltasGratificacion() > FALTAS_MAXIMAS) {
            errores.add("Los días de falta deben estar entre 0 y " + FALTAS_MAXIMAS + ".");
        }
        if (negativo(b.ultimaGratificacion()) || negativo(b.otrosIngresos()) || negativo(b.otrosDescuentos())) {
            errores.add("Los montos no pueden ser negativos.");
        }
        return errores;
    }

    public Calculo calcular(RonaldmichaUsuario usuario, Borrador b) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "calcular liquidaciones");
        exigirSinErrores(validarDatos(b));
        RonaldmichaTrabajador t = trabajador(b.trabajadorId());
        RonaldmichaParametrosVersion p;
        try {
            p = parametros.vigentes(YearMonth.from(b.fechaCese()));
        } catch (RonaldmichaParametroNoEncontradoException e) {
            throw new RonaldmichaValidacionException(e.getMessage());
        }
        RonaldmichaSemestreBeneficio semestre = ultimaGratificacionPagada(b.fechaCese());
        BigDecimal calculada = gratificacionCalculada(t.id(), semestre);
        BigDecimal ultima = b.ultimaGratificacion() == null ? calculada : b.ultimaGratificacion();
        int pendientes = Math.max(0, RonaldmichaVacacionesCalculator.resumir(t.fechaIngreso(), null, t.regimen(),
                vacaciones.listar(t.id()), b.fechaCese()).pendiente());
        BigDecimal af = t.asigFamiliar() ? RonaldmichaAportePensionCalculator.asignacionFamiliar(p) : RonaldmichaDinero.CERO;
        Resultado r = RonaldmichaLiquidacionCalculator.calcular(new Entrada(t.fechaIngreso(), b.fechaCese(), b.motivo(), t.regimen(),
                t.sueldo(), af, pendientes, b.faltasGratificacion(), ultima, b.faltasCts(),
                t.salud() == RonaldmichaSalud.EPS ? p.bonifExtEpsPct() : p.bonifExtPct(), p, t.sistemaPension(), t.tipoComision(),
                b.otrosIngresos(), b.otrosDescuentos()));
        return new Calculo(t, b, p, semestre, calculada, r, reporte(t, b, p, semestre, r));
    }

    public static RonaldmichaSemestreBeneficio ultimaGratificacionPagada(LocalDate cese) {
        MonthDay dia = MonthDay.from(cese);
        if (!dia.isBefore(PAGO_DICIEMBRE)) {
            return new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, cese.getYear(), 2);
        }
        if (!dia.isBefore(PAGO_JULIO)) {
            return new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, cese.getYear(), 1);
        }
        return new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, cese.getYear() - 1, 2);
    }

    private BigDecimal gratificacionCalculada(long trabajadorId, RonaldmichaSemestreBeneficio s) {
        ClaveGrati clave = new ClaveGrati(trabajadorId, s);
        if (!clave.equals(claveGrati)) {
            valorGrati = beneficios.gratificacionDe(trabajadorId, s);
            claveGrati = clave;
        }
        return valorGrati;
    }

    public RonaldmichaLiquidacion guardar(RonaldmichaUsuario usuario, Long id, Borrador b, boolean registrarCese) {
        RonaldmichaPermisos.exigirAdmin(usuario, "guardar liquidaciones");
        RonaldmichaLiquidacion actual = id == null ? null : repositorio.buscar(id)
                .orElseThrow(() -> new RonaldmichaValidacionException("La liquidación no existe."));
        if (actual != null && actual.estado() == RonaldmichaEstadoLiquidacion.PAGADO) {
            throw new RonaldmichaValidacionException("La liquidación ya está pagada: es de solo lectura.");
        }
        if (actual != null && !Objects.equals(actual.trabajadorId(), b.trabajadorId())) {
            throw new RonaldmichaValidacionException("No se puede cambiar el trabajador de una liquidación guardada.");
        }
        exigirSinErrores(validar(b, id));
        boolean cese = registrarCese || (actual != null && actual.estado() == RonaldmichaEstadoLiquidacion.CESE_REGISTRADO);
        if (cese && periodos.situacion(YearMonth.from(b.fechaCese())) == RonaldmichaSituacionPeriodo.CERRADO) {
            throw new RonaldmichaValidacionException("La planilla de " + MES_ANIO.format(b.fechaCese())
                    + " está cerrada: no se puede registrar un cese en ese periodo.");
        }
        Calculo c = calcular(usuario, b);
        Resultado r = c.resultado();
        RonaldmichaLiquidacion l = new RonaldmichaLiquidacion(id, b.trabajadorId(), b.fechaCese(), b.motivo(), RonaldmichaDinero.redondear(nulo(b.otrosIngresos())),
                RonaldmichaDinero.redondear(nulo(b.otrosDescuentos())), b.faltasCts(), b.faltasGratificacion(),
                b.ultimaGratificacion() == null ? null : RonaldmichaDinero.redondear(b.ultimaGratificacion()),
                cese ? RonaldmichaEstadoLiquidacion.CESE_REGISTRADO : RonaldmichaEstadoLiquidacion.BORRADOR,
                actual == null ? ahora() : actual.fechaRegistro(), actual == null ? usuario.usuario() : actual.usuario(),
                r.bruto(), r.descuentos(), r.neto());
        long nuevoId = repositorio.guardar(l, cese);
        String detalle = "#" + nuevoId + " · " + c.trabajador().nombreCompleto() + " · cese " + RonaldmichaFormatos.fecha(b.fechaCese())
                + " · " + b.motivo().etiqueta() + " · neto " + RonaldmichaFormatos.moneda(r.neto());
        auditoria.registrar(usuario.usuario(), "LIQUIDACION_GUARDADA", detalle);
        if (registrarCese && (actual == null || actual.estado() != RonaldmichaEstadoLiquidacion.CESE_REGISTRADO)) {
            auditoria.registrar(usuario.usuario(), "CESE_REGISTRADO", c.trabajador().nombreCompleto() + " · cese "
                    + RonaldmichaFormatos.fecha(b.fechaCese()) + " · " + b.motivo().etiqueta() + " · pendiente: baja en el T-Registro");
        }
        return repositorio.buscar(nuevoId).orElseThrow();
    }

    public void marcarPagado(RonaldmichaUsuario usuario, long id) {
        RonaldmichaPermisos.exigirAdmin(usuario, "marcar liquidaciones como pagadas");
        RonaldmichaLiquidacion l = liquidacion(id);
        if (l.estado() != RonaldmichaEstadoLiquidacion.CESE_REGISTRADO
                || !repositorio.cambiarEstado(id, RonaldmichaEstadoLiquidacion.CESE_REGISTRADO, RonaldmichaEstadoLiquidacion.PAGADO)) {
            throw new RonaldmichaValidacionException(l.estado() == RonaldmichaEstadoLiquidacion.PAGADO ? "La liquidación ya está pagada."
                    : "Primero registre el cese («Guardar y registrar cese»).");
        }
        auditoria.registrar(usuario.usuario(), "LIQUIDACION_PAGADA", "#" + id + " · "
                + trabajador(l.trabajadorId()).nombreCompleto() + " · neto " + RonaldmichaFormatos.moneda(l.neto()));
    }

    public void eliminar(RonaldmichaUsuario usuario, long id) {
        RonaldmichaPermisos.exigirAdmin(usuario, "eliminar liquidaciones");
        RonaldmichaLiquidacion l = liquidacion(id);
        if (l.estado() != RonaldmichaEstadoLiquidacion.BORRADOR || !repositorio.eliminarBorrador(id)) {
            throw new RonaldmichaValidacionException("Solo se eliminan liquidaciones en borrador.");
        }
        auditoria.registrar(usuario.usuario(), "LIQUIDACION_ELIMINADA", "#" + id + " · "
                + trabajador(l.trabajadorId()).nombreCompleto() + " · cese " + RonaldmichaFormatos.fecha(l.fechaCese()));
    }

    public byte[] reportePdf(RonaldmichaUsuario usuario, Borrador b) {
        Calculo c = calcular(usuario, b);
        RonaldmichaEmpresa e = empresa.obtener();
        byte[] pdf = RonaldmichaPdfRenderer.render(modelo(c, e), e, empresa.avisoDatosIncompletos(e), ahora());
        auditoria.registrar(usuario.usuario(), "LIQUIDACION_EXPORTADA", "PDF · " + c.trabajador().nombreCompleto());
        return pdf;
    }

    public byte[] reporteWord(RonaldmichaUsuario usuario, Borrador b) {
        Calculo c = calcular(usuario, b);
        RonaldmichaEmpresa e = empresa.obtener();
        byte[] word = RonaldmichaWordRenderer.render(modelo(c, e), e, empresa.avisoDatosIncompletos(e), ahora());
        auditoria.registrar(usuario.usuario(), "LIQUIDACION_EXPORTADA", "Word · " + c.trabajador().nombreCompleto());
        return word;
    }

    public byte[] certificadoTrabajo(RonaldmichaUsuario usuario, Borrador b) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "emitir certificados de trabajo");
        if (b.trabajadorId() == null || b.fechaCese() == null) {
            throw new RonaldmichaValidacionException("Elija al trabajador e indique la fecha de cese.");
        }
        RonaldmichaTrabajador t = trabajador(b.trabajadorId());
        RonaldmichaEmpresa e = empresa.obtener();
        RonaldmichaDocumentModel d = RonaldmichaPlantillas.carta("CERTIFICADO DE TRABAJO", null, RonaldmichaTextosDocumentos.certificadoTrabajo(e, t,
                b.fechaCese()), RonaldmichaTextosDocumentos.ciudadFecha(e, LocalDate.now(reloj)), e,
                RonaldmichaPlantillas.recibiConforme(t.nombreCompleto(), t.tipoDoc().codigo() + " " + t.numDoc()))
                .conArchivo("Certificado de trabajo · " + t.nombreCompleto());
        byte[] pdf = RonaldmichaPdfRenderer.render(d, e, empresa.avisoDatosIncompletos(e), ahora());
        auditoria.registrar(usuario.usuario(), "CERTIFICADO_TRABAJO", t.nombreCompleto() + " · "
                + RonaldmichaFormatos.fecha(t.fechaIngreso()) + " al " + RonaldmichaFormatos.fecha(b.fechaCese()));
        return pdf;
    }

    private static RonaldmichaDocumentModel modelo(Calculo c, RonaldmichaEmpresa e) {
        return RonaldmichaPlantillas.liquidacion(c.reporte(), null, e).conArchivo("Liquidación · " + c.trabajador().nombreCompleto());
    }

    private static RonaldmichaReporteLiquidacion reporte(RonaldmichaTrabajador t, Borrador b, RonaldmichaParametrosVersion p, RonaldmichaSemestreBeneficio semestre,
                                              Resultado r) {
        String pension = t.sistemaPension().nombre() + (t.sistemaPension().esAfp() && t.tipoComision() != null
                ? " · comisión " + t.tipoComision().codigo() : "");
        List<Dato> datos = List.of(
                new Dato("Trabajador", t.nombreCompleto()),
                new Dato("Documento", t.tipoDoc().codigo() + " " + t.numDoc()),
                new Dato("Cargo", valor(t.cargo(), "—")),
                new Dato("Régimen", t.regimen().descripcion()),
                new Dato("Salud", t.salud().nombre()),
                new Dato("Pensión", pension),
                new Dato("Fecha de ingreso", RonaldmichaFormatos.fecha(t.fechaIngreso())),
                new Dato("Fecha de cese", RonaldmichaFormatos.fecha(b.fechaCese())),
                new Dato("Motivo", b.motivo().etiqueta()),
                new Dato("Tiempo de servicios", tiempo(r)),
                new Dato("Remuneración computable", RonaldmichaFormatos.moneda(r.computable())),
                new Dato("Asignación familiar", RonaldmichaFormatos.moneda(r.asignacionFamiliar())));

        String factor = t.regimen().diasVacacionesAnuales() == 30 ? "" : " × 50 %";
        List<Linea> lineasVacaciones = new ArrayList<>(List.of(
                new Linea("Vacaciones truncas", r.truncoMeses() + " meses y " + r.truncoDias() + " días de récord trunco"
                        + factor, r.vacacionesTruncas()),
                new Linea("Vacaciones pendientes no gozadas", RonaldmichaFormatos.moneda(r.computable()) + " / 30 × "
                        + r.diasPendientes() + " días", r.vacacionesPendientes())));
        if (r.indemnizacionVacacional().signum() > 0) {
            lineasVacaciones.add(new Linea("Indemnización vacacional (art. 23 D.Leg. 713)",
                    r.diasIndemnizacionVacacional() + " días de periodos vencidos sin gozar · sin aportes",
                    r.indemnizacionVacacional()));
        }
        Bloque vacaciones = new Bloque("Vacaciones", lineasVacaciones, r.subtotalVacaciones());

        Bloque gratificacion = new Bloque("Gratificación trunca", List.of(
                new Linea("Gratificación trunca", mes(r.gratificacionDesde()) + "–" + mes(YearMonth.from(b.fechaCese()))
                        + ": " + r.gratificacionMeses() + " meses completos, " + r.faltasGratificacion() + " faltas",
                        r.gratificacion()),
                new Linea("Bonificación extraordinaria", RonaldmichaFormatos.numero(t.salud() == RonaldmichaSalud.EPS ? p.bonifExtEpsPct()
                        : p.bonifExtPct()) + " % (Ley 30334)", r.bonificacion())), r.subtotalGratificacion());

        Bloque cts = new Bloque("CTS trunca", List.of(
                new Linea("1/6 de la última gratificación", RonaldmichaBeneficiosService.etiqueta(semestre) + ": "
                        + RonaldmichaFormatos.moneda(r.ultimaGratificacion()), r.sextoGratificacion()),
                new Linea("CTS trunca", "Desde " + mes(r.ctsDesde()) + ": " + r.ctsMeses() + " meses y " + r.ctsDias()
                        + " días, " + r.faltasCts() + " faltas · computable " + RonaldmichaFormatos.moneda(r.computableCts()),
                        r.cts())), r.cts());

        List<Linea> otros = new ArrayList<>();
        otros.add(new Linea("Indemnización por despido arbitrario", r.indemnizable()
                ? indemnizacionTexto(t) : "No corresponde (" + b.motivo().etiqueta().toLowerCase(Locale.ROOT) + ")",
                r.indemnizacion()));
        otros.add(new Linea("Otros ingresos", null, r.otrosIngresos()));
        otros.addAll(lineasPension(r.pension()));
        otros.add(new Linea("Otros descuentos", null, r.otrosDescuentos().negate()));
        Bloque indemnizacion = new Bloque("Indemnización y otros", otros, r.subtotalOtros());

        return new RonaldmichaReporteLiquidacion("LIQUIDACIÓN DE BENEFICIOS SOCIALES",
                t.nombreCompleto() + " · cese " + RonaldmichaFormatos.fecha(b.fechaCese()) + " · " + b.motivo().etiqueta(),
                datos, List.of(vacaciones, gratificacion, cts, indemnizacion), r.bruto(), r.descuentos(), r.neto(),
                r.netoEnLetras(), NOTA, t.nombres() + " " + t.apellidos(), t.tipoDoc().codigo() + " " + t.numDoc());
    }

    private static List<Linea> lineasPension(AportePension a) {
        String base = " sobre vacaciones " + RonaldmichaFormatos.moneda(a.remuneracion());
        if (!a.sistema().esAfp()) {
            return List.of(new Linea("Aporte ONP", RonaldmichaFormatos.numero(a.tasaAporte()) + " %" + base, a.aporte().negate()));
        }
        List<Linea> lineas = new ArrayList<>();
        lineas.add(new Linea("Aporte AFP", RonaldmichaFormatos.numero(a.tasaAporte()) + " %" + base, a.aporte().negate()));
        lineas.add(new Linea("Prima de seguros AFP", RonaldmichaFormatos.numero(a.tasaPrima()) + " %" + base, a.prima().negate()));
        if (a.tasaComision() != null) {
            lineas.add(new Linea("Comisión de flujo AFP", RonaldmichaFormatos.numero(a.tasaComision()) + " %" + base,
                    a.comision().negate()));
        }
        return lineas;
    }

    private static String indemnizacionTexto(RonaldmichaTrabajador t) {
        return switch (t.regimen()) {
            case GENERAL -> "1.5 remuneraciones por año (tope 12)";
            case PEQUENA -> "20 remuneraciones diarias por año (tope 120)";
            case MICRO -> "10 remuneraciones diarias por año (tope 90)";
        };
    }

    public static String tiempo(Resultado r) {
        RonaldmichaVacacionesCalculator.Tiempo t = r.tiempoServicios();
        return t.anios() + (t.anios() == 1 ? " año, " : " años, ") + t.meses() + (t.meses() == 1 ? " mes y " : " meses y ")
                + t.dias() + (t.dias() == 1 ? " día" : " días");
    }

    private static String mes(YearMonth m) {
        return MES_ANIO.format(m).replace(".", "");
    }

    private static void exigirSinErrores(List<String> errores) {
        if (!errores.isEmpty()) {
            throw new RonaldmichaValidacionException(String.join(" ", errores));
        }
    }

    private RonaldmichaTrabajador trabajador(long id) {
        return trabajadores.buscarPorId(id).orElseThrow(() -> new RonaldmichaValidacionException("El trabajador no existe."));
    }

    private RonaldmichaLiquidacion liquidacion(long id) {
        return repositorio.buscar(id).orElseThrow(() -> new RonaldmichaValidacionException("La liquidación no existe."));
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
    }

    private static boolean negativo(BigDecimal b) {
        return b != null && b.signum() < 0;
    }

    private static BigDecimal nulo(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }

    private static String valor(String s, String porDefecto) {
        return s == null || s.isBlank() ? porDefecto : s;
    }
}
