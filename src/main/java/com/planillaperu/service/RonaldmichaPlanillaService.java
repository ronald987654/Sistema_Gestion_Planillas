package com.planillaperu.service;

import com.planillaperu.calc.RonaldmichaAportePensionCalculator;
import com.planillaperu.calc.RonaldmichaGratificacionCalculator;
import com.planillaperu.calc.RonaldmichaHorasExtraCalculator;
import com.planillaperu.calc.RonaldmichaHorasExtraCalculator.Tramos;
import com.planillaperu.calc.RonaldmichaJornadaCalculator;
import com.planillaperu.calc.RonaldmichaPlanillaCalculator;
import com.planillaperu.calc.RonaldmichaPlanillaCalculator.HistorialRenta;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla;
import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaConceptoExtra;
import com.planillaperu.domain.RonaldmichaHorario;
import com.planillaperu.domain.RonaldmichaMarcacion;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaPeriodo;
import com.planillaperu.domain.RonaldmichaSalud;
import com.planillaperu.domain.RonaldmichaSemestreBeneficio;
import com.planillaperu.domain.RonaldmichaSituacionPeriodo;
import com.planillaperu.domain.RonaldmichaTipoBeneficio;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaTratamientoHe;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository.HoraExtraManual;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository.NovedadMes;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;
import com.planillaperu.repository.RonaldmichaPlanillaRepository;
import com.planillaperu.repository.RonaldmichaPlanillaRepository.OtroEmpleador;
import com.planillaperu.repository.RonaldmichaPlanillaRepository.RentaMes;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.service.RonaldmichaAsistenciaService.NovedadesPlanilla;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

public class RonaldmichaPlanillaService {

    public static final BigDecimal HORAS_MAXIMAS_DIA = BigDecimal.valueOf(16);
    private static final BigDecimal MEDIA_HORA = new BigDecimal("0.5");
    private static final Pattern CODIGO_PLAME = Pattern.compile("\\d{4}");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy");

    public record Borrador(
            int inasistencias,
            int vacaciones,
            int tardanzaMin,
            int licenciaConGoce,
            int feriadosTrabajados,
            int diasSubsidiados,
            BigDecimal comisiones,
            BigDecimal bonificacion,
            BigDecimal adelanto,
            BigDecimal otrosDescuentos,
            List<HoraExtraManual> horasExtra,
            List<RonaldmichaConceptoExtra> conceptos) {

        public static final Borrador VACIO = new Borrador(0, 0, 0, 0, 0, 0, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO,
                RonaldmichaDinero.CERO, List.of(), List.of());

        public Borrador {
            horasExtra = List.copyOf(horasExtra);
            conceptos = List.copyOf(conceptos);
        }
    }

    public record FilaPlanilla(RonaldmichaTrabajador trabajador, RonaldmichaResultadoPlanilla resultado, boolean desdeAsistencia,
                               BigDecimal horasDiarias, int minutosExtra) {

        public BigDecimal horasOrdinarias() {
            return horasDiarias.multiply(BigDecimal.valueOf(resultado.diasLaborados()));
        }
    }

    public record Totales(int trabajadores, BigDecimal ingresos, BigDecimal descuentos, BigDecimal aportes,
                          BigDecimal neto, BigDecimal rentaQuinta) {

        public static Totales de(Collection<FilaPlanilla> filas) {
            BigDecimal ingresos = RonaldmichaDinero.CERO;
            BigDecimal descuentos = RonaldmichaDinero.CERO;
            BigDecimal aportes = RonaldmichaDinero.CERO;
            BigDecimal neto = RonaldmichaDinero.CERO;
            BigDecimal renta = RonaldmichaDinero.CERO;
            for (FilaPlanilla f : filas) {
                RonaldmichaResultadoPlanilla r = f.resultado();
                ingresos = ingresos.add(r.totalIngresos());
                descuentos = descuentos.add(r.totalDescuentos());
                aportes = aportes.add(r.totalAportes());
                neto = neto.add(r.netoAPagar());
                renta = renta.add(r.rentaQuinta());
            }
            return new Totales(filas.size(), ingresos, descuentos, aportes, neto, renta);
        }
    }

    public record PlanillaMes(YearMonth mes, RonaldmichaSituacionPeriodo situacion, Optional<RonaldmichaPeriodo> registro,
                              List<FilaPlanilla> filas, Optional<String> error) {

        public PlanillaMes {
            filas = List.copyOf(filas);
        }

        public boolean editable() {
            return situacion == RonaldmichaSituacionPeriodo.ABIERTO && error.isEmpty();
        }
    }

    public record Detalle(RonaldmichaTrabajador trabajador, YearMonth mes, RonaldmichaSituacionPeriodo situacion, int diasMes,
                          BigDecimal horasDiarias, NovedadesPlanilla asistencia, Borrador guardado,
                          HistorialRenta historialRenta) {

        public boolean desdeAsistencia() {
            return asistencia != null;
        }

        public boolean editable() {
            return situacion == RonaldmichaSituacionPeriodo.ABIERTO;
        }
    }

    public record Simulacion(RonaldmichaResultadoPlanilla resultado, List<String> errores) {
    }

    @FunctionalInterface
    public interface FuenteGratificaciones {
        Map<Long, BigDecimal> pagadas(RonaldmichaSemestreBeneficio semestre,
                                      Function<YearMonth, Map<Long, RonaldmichaResultadoPlanilla>> planillas);
    }

    private record Plantel(List<RonaldmichaTrabajador> todos, Map<Long, RonaldmichaHorario> horarios) {
    }

    private static final class AcumuladoRenta {
        private BigDecimal percibido = RonaldmichaDinero.CERO;
        private final BigDecimal[] retenciones = new BigDecimal[12];

        void agregar(YearMonth mes, BigDecimal percibidoMes, BigDecimal retencion) {
            percibido = percibido.add(percibidoMes);
            retenciones[mes.getMonthValue() - 1] = retencion;
        }

        List<BigDecimal> retencionesHasta(YearMonth mes) {
            List<BigDecimal> lista = new ArrayList<>();
            for (int i = 0; i < mes.getMonthValue() - 1; i++) {
                lista.add(retenciones[i] == null ? RonaldmichaDinero.CERO : retenciones[i]);
            }
            return lista;
        }
    }

    private record MesCalculado(PlanillaMes planilla, Map<Long, RentaMes> renta) {
    }

    private final RonaldmichaTrabajadorRepository trabajadores;
    private final RonaldmichaAsistenciaRepository asistenciaRepositorio;
    private final RonaldmichaPlanillaRepository repositorio;
    private final RonaldmichaPeriodoRepository periodoRepositorio;
    private final RonaldmichaPeriodoService periodos;
    private final RonaldmichaParametrosService parametros;
    private final RonaldmichaAsistenciaService asistencia;
    private final RonaldmichaAuditoriaService auditoria;
    private final Clock reloj;
    private FuenteGratificaciones gratificaciones;

    public RonaldmichaPlanillaService(RonaldmichaTrabajadorRepository trabajadores, RonaldmichaAsistenciaRepository asistenciaRepositorio,
                           RonaldmichaPlanillaRepository repositorio, RonaldmichaPeriodoRepository periodoRepositorio,
                           RonaldmichaPeriodoService periodos, RonaldmichaParametrosService parametros, RonaldmichaAsistenciaService asistencia,
                           RonaldmichaAuditoriaService auditoria, Clock reloj) {
        this.trabajadores = trabajadores;
        this.asistenciaRepositorio = asistenciaRepositorio;
        this.repositorio = repositorio;
        this.periodoRepositorio = periodoRepositorio;
        this.periodos = periodos;
        this.parametros = parametros;
        this.asistencia = asistencia;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public void usarGratificaciones(FuenteGratificaciones fuente) {
        this.gratificaciones = fuente;
    }

    public PlanillaMes consultar(RonaldmichaUsuario usuario, YearMonth mes) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "ver la planilla");
        return planilla(mes);
    }

    public Map<Long, RonaldmichaResultadoPlanilla> resultadosDelMes(YearMonth mes) {
        Map<Long, RonaldmichaResultadoPlanilla> mapa = new HashMap<>();
        PlanillaMes pm;
        try {
            pm = calcularMes(mes, parametros.vigentes(mes), new Plantel(trabajadores.listarTodos(), trabajadores.horarios()),
                    (t, p) -> HistorialRenta.SIN_HISTORIA, Map.of()).planilla();
        } catch (RonaldmichaParametroNoEncontradoException e) {
            return mapa;
        }
        pm.filas().forEach(f -> mapa.put(f.trabajador().id(), f.resultado()));
        return mapa;
    }

    public PlanillaMes delMes(YearMonth mes) {
        return planilla(mes);
    }

    public List<PlanillaMes> delAnio(YearMonth hasta) {
        return planillasHasta(hasta, true).stream().map(MesCalculado::planilla).toList();
    }

    public Optional<OtroEmpleador> otroEmpleador(RonaldmichaUsuario usuario, long trabajadorId, int anio) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "ver la renta de quinta");
        return Optional.ofNullable(repositorio.otrosEmpleadores(anio).get(trabajadorId));
    }

    public Optional<OtroEmpleador> otroEmpleadorSinPermiso(long trabajadorId, int anio) {
        return Optional.ofNullable(repositorio.otrosEmpleadores(anio).get(trabajadorId));
    }

    public void guardarOtroEmpleador(RonaldmichaUsuario usuario, long trabajadorId, int anio, String ruc, String razonSocial,
                                     BigDecimal renta, BigDecimal retencion) {
        RonaldmichaPermisos.exigirAdmin(usuario, "registrar rentas de otros empleadores");
        RonaldmichaTrabajador t = trabajador(trabajadorId);
        BigDecimal r = RonaldmichaDinero.redondear(nulo(renta));
        BigDecimal ret = RonaldmichaDinero.redondear(nulo(retencion));
        if (r.signum() < 0 || ret.signum() < 0) {
            throw new RonaldmichaValidacionException("La renta y la retención de otros empleadores no pueden ser negativas.");
        }
        if (ret.compareTo(r) > 0) {
            throw new RonaldmichaValidacionException("La retención de otros empleadores no puede pasar de su renta.");
        }
        if (r.signum() == 0) {
            repositorio.borrarOtroEmpleador(trabajadorId, anio);
        } else {
            repositorio.guardarOtroEmpleador(new OtroEmpleador(trabajadorId, anio, limpio(ruc), limpio(razonSocial), r,
                    ret));
        }
        auditoria.registrar(usuario.usuario(), "RENTA_OTRO_EMPLEADOR", describir(t) + " · " + anio + " · renta "
                + RonaldmichaFormatos.moneda(r) + " · retención " + RonaldmichaFormatos.moneda(ret)
                + (limpio(razonSocial) == null ? "" : " · " + razonSocial.trim()));
    }

    public Map<YearMonth, RonaldmichaSituacionPeriodo> situacionesDelAnio(int anio) {
        Map<YearMonth, RonaldmichaSituacionPeriodo> mapa = new TreeMap<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth mes = YearMonth.of(anio, m);
            mapa.put(mes, periodos.situacion(mes));
        }
        return mapa;
    }

    public Detalle detalle(RonaldmichaUsuario usuario, long trabajadorId, YearMonth mes) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "ver la planilla");
        RonaldmichaTrabajador t = trabajador(trabajadorId);
        return new Detalle(t, mes, periodos.situacion(mes),
                RonaldmichaPlanillaCalculator.diasDelMes(t.fechaIngreso(), t.fechaCese(), mes), horasDiarias(t),
                asistenciaDelMes(t, mes), guardado(trabajadorId, mes), historialRenta(t, mes));
    }

    public Simulacion simular(Detalle d, Borrador b) {
        RonaldmichaResultadoPlanilla r = calcular(d.trabajador(), d.mes(), parametros.vigentes(d.mes()), d.horasDiarias(),
                d.asistencia(), b, d.historialRenta());
        return new Simulacion(r, validar(d, b));
    }

    public List<String> validar(Detalle d, Borrador b) {
        List<String> errores = new ArrayList<>();
        if (d.diasMes() == 0) {
            errores.add("El trabajador no tiene vínculo laboral en " + MES.format(d.mes()) + ".");
            return errores;
        }
        if (b.inasistencias() < 0 || b.vacaciones() < 0 || b.tardanzaMin() < 0 || b.licenciaConGoce() < 0
                || b.feriadosTrabajados() < 0 || b.diasSubsidiados() < 0) {
            errores.add("Los días y los minutos no pueden ser negativos.");
        }
        int inasistencias = d.desdeAsistencia() ? d.asistencia().faltas() : b.inasistencias();
        int vacaciones = d.desdeAsistencia() ? d.asistencia().vacaciones() : b.vacaciones();
        int dias = inasistencias + vacaciones + b.diasSubsidiados();
        if (dias > d.diasMes()) {
            errores.add("Inasistencias, vacaciones y días subsidiados suman " + dias + ": no pueden pasar de los "
                    + d.diasMes() + " días del periodo.");
        }
        if (!d.desdeAsistencia() && (b.licenciaConGoce() > d.diasMes() || b.feriadosTrabajados() > d.diasMes())) {
            errores.add("La licencia con goce y los feriados trabajados no pueden pasar de los " + d.diasMes()
                    + " días del periodo.");
        }
        for (BigDecimal monto : List.of(nulo(b.comisiones()), nulo(b.bonificacion()), nulo(b.adelanto()),
                nulo(b.otrosDescuentos()))) {
            if (monto.signum() < 0) {
                errores.add("Comisiones, bonificación, adelanto y otros descuentos no pueden ser negativos.");
                break;
            }
        }
        if (!d.desdeAsistencia()) {
            errores.addAll(validarSobretiempo(d, b.horasExtra()));
        }
        for (RonaldmichaConceptoExtra c : b.conceptos()) {
            String nombre = c.descripcion() == null || c.descripcion().isBlank() ? "un concepto" : "«" + c.descripcion().trim() + "»";
            if (c.codigoPlame() == null || !CODIGO_PLAME.matcher(c.codigoPlame().trim()).matches()) {
                errores.add("El código PLAME de " + nombre + " debe tener 4 dígitos.");
            }
            if (c.descripcion() == null || c.descripcion().isBlank()) {
                errores.add("Escriba la descripción del concepto " + (c.codigoPlame() == null ? "" : c.codigoPlame().trim()) + ".");
            }
            if (c.monto() == null || c.monto().signum() <= 0) {
                errores.add("El monto de " + nombre + " debe ser mayor que cero.");
            }
        }
        return errores;
    }

    private List<String> validarSobretiempo(Detalle d, List<HoraExtraManual> horasExtra) {
        List<String> errores = new ArrayList<>();
        Map<LocalDate, BigDecimal> porDia = new TreeMap<>();
        for (HoraExtraManual he : horasExtra) {
            if (he.fecha() == null) {
                errores.add("Indique la fecha de cada registro de sobretiempo.");
                continue;
            }
            String fecha = RonaldmichaFormatos.fecha(he.fecha());
            if (!YearMonth.from(he.fecha()).equals(d.mes())) {
                errores.add("El sobretiempo del " + fecha + " no es del periodo " + MES.format(d.mes()) + ".");
                continue;
            }
            if (he.horas() == null || he.horas().signum() <= 0
                    || he.horas().remainder(MEDIA_HORA).signum() != 0) {
                errores.add("Las horas de sobretiempo van en múltiplos de 0.5 (revise el " + fecha + ").");
                continue;
            }
            porDia.merge(he.fecha(), he.horas(), BigDecimal::add);
        }
        porDia.forEach((fecha, horas) -> {
            BigDecimal total = d.horasDiarias().add(horas);
            if (total.compareTo(HORAS_MAXIMAS_DIA) > 0) {
                errores.add("El " + RonaldmichaFormatos.fecha(fecha) + " suma " + texto(total) + " h (jornada "
                        + texto(d.horasDiarias()) + " h + sobretiempo " + texto(horas) + " h): un día no puede pasar de "
                        + texto(HORAS_MAXIMAS_DIA) + " h.");
            }
        });
        return errores;
    }

    public RonaldmichaResultadoPlanilla guardar(RonaldmichaUsuario usuario, long trabajadorId, YearMonth mes, Borrador b) {
        RonaldmichaPermisos.exigirAdmin(usuario, "modificar la planilla");
        exigirAbierto(mes);
        Detalle d = detalle(usuario, trabajadorId, mes);
        List<String> errores = validar(d, b);
        if (!errores.isEmpty()) {
            throw new RonaldmichaValidacionException(errores);
        }
        Borrador g = d.guardado();
        Borrador aGuardar = d.desdeAsistencia()
                ? new Borrador(g.inasistencias(), g.vacaciones(), g.tardanzaMin(), g.licenciaConGoce(),
                g.feriadosTrabajados(), b.diasSubsidiados(), b.comisiones(), b.bonificacion(), b.adelanto(),
                b.otrosDescuentos(), g.horasExtra(), b.conceptos())
                : b;
        List<RonaldmichaConceptoExtra> conceptos = aGuardar.conceptos().stream()
                .map(c -> new RonaldmichaConceptoExtra(c.tipo(), c.codigoPlame().trim(), c.descripcion().trim(),
                        RonaldmichaDinero.redondear(c.monto())))
                .toList();
        repositorio.guardarNovedades(trabajadorId, mes, new NovedadMes(aGuardar.inasistencias(),
                        aGuardar.vacaciones(), aGuardar.tardanzaMin(), aGuardar.licenciaConGoce(),
                        aGuardar.feriadosTrabajados(), aGuardar.diasSubsidiados(), RonaldmichaDinero.redondear(aGuardar.comisiones()),
                        RonaldmichaDinero.redondear(aGuardar.bonificacion()), RonaldmichaDinero.redondear(aGuardar.adelanto()),
                        RonaldmichaDinero.redondear(aGuardar.otrosDescuentos())),
                aGuardar.horasExtra(), conceptos);
        RonaldmichaResultadoPlanilla r = calcular(d.trabajador(), mes, parametros.vigentes(mes), d.horasDiarias(), d.asistencia(),
                aGuardar, d.historialRenta());
        auditoria.registrar(usuario.usuario(), "PLANILLA_NOVEDADES_GUARDADAS", describir(d.trabajador()) + " · "
                + MES.format(mes) + " · " + r.diasLaborados() + " días laborados · " + conceptos.size()
                + " conceptos extra · neto " + RonaldmichaFormatos.moneda(r.netoAPagar()));
        return r;
    }

    public void cerrar(RonaldmichaUsuario usuario, YearMonth mes) {
        RonaldmichaPermisos.exigirAdmin(usuario, "cerrar la planilla");
        RonaldmichaSituacionPeriodo situacion = periodos.situacion(mes);
        if (situacion != RonaldmichaSituacionPeriodo.ABIERTO) {
            throw new RonaldmichaValidacionException("Solo se puede cerrar un periodo abierto: " + MES.format(mes) + " está "
                    + situacion.etiqueta().toLowerCase() + ".");
        }
        List<MesCalculado> anio = planillasHasta(mes, false);
        MesCalculado calculado = anio.get(anio.size() - 1);
        PlanillaMes planilla = calculado.planilla();
        if (planilla.error().isPresent()) {
            throw new RonaldmichaValidacionException("No se puede cerrar " + MES.format(mes) + ": " + planilla.error().get());
        }
        Totales t = Totales.de(planilla.filas());
        periodoRepositorio.cerrar(mes, usuario.usuario(), LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS));
        periodoRepositorio.abrir(mes.plusMonths(1));
        repositorio.guardarRentaDelMes(mes, List.copyOf(calculado.renta().values()));
        auditoria.registrar(usuario.usuario(), "PLANILLA_CERRADA", MES.format(mes) + " · " + t.trabajadores()
                + " trabajadores · ingresos " + RonaldmichaFormatos.moneda(t.ingresos()) + " · neto " + RonaldmichaFormatos.moneda(t.neto())
                + " · renta de 5.ª retenida " + RonaldmichaFormatos.moneda(t.rentaQuinta()) + " · se abre "
                + MES.format(mes.plusMonths(1)));
        recalcularRentaEnCascada(usuario, mes);
    }

    private void recalcularRentaEnCascada(RonaldmichaUsuario usuario, YearMonth mes) {
        for (YearMonth m = mes.plusMonths(1); m.getYear() == mes.getYear(); m = m.plusMonths(1)) {
            if (periodos.situacion(m) != RonaldmichaSituacionPeriodo.CERRADO) {
                continue;
            }
            Map<Long, RentaMes> antes = repositorio.rentaDelMes(m);
            repositorio.borrarRentaDelMes(m);
            List<MesCalculado> anio = planillasHasta(m, false);
            Map<Long, RentaMes> despues = anio.get(anio.size() - 1).renta();
            repositorio.guardarRentaDelMes(m, List.copyOf(despues.values()));
            List<String> cambios = new ArrayList<>();
            despues.forEach((id, r) -> {
                RentaMes a = antes.get(id);
                if (a == null || a.retencion().compareTo(r.retencion()) != 0) {
                    cambios.add("trabajador " + id + ": " + RonaldmichaFormatos.moneda(a == null ? RonaldmichaDinero.CERO : a.retencion())
                            + " → " + RonaldmichaFormatos.moneda(r.retencion()));
                }
            });
            if (!cambios.isEmpty()) {
                auditoria.registrar(usuario.usuario(), "RENTA_QUINTA_RECALCULADA", MES.format(m) + " · por el cierre de "
                        + MES.format(mes) + " · " + String.join(" · ", cambios));
            }
        }
    }

    public void reabrir(RonaldmichaUsuario usuario, YearMonth mes, String motivo) {
        RonaldmichaPermisos.exigirAdmin(usuario, "reabrir la planilla");
        if (periodos.situacion(mes) != RonaldmichaSituacionPeriodo.CERRADO) {
            throw new RonaldmichaValidacionException("El periodo " + MES.format(mes) + " no está cerrado.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new RonaldmichaValidacionException("Escriba el motivo para reabrir la planilla de " + MES.format(mes) + ".");
        }
        periodoRepositorio.reabrir(mes, motivo.trim());
        repositorio.borrarRentaDelMes(mes);
        auditoria.registrar(usuario.usuario(), "PLANILLA_REABIERTA", MES.format(mes) + " · motivo: " + motivo.trim());
    }

    private PlanillaMes planilla(YearMonth mes) {
        List<MesCalculado> anio = planillasHasta(mes, false);
        return anio.get(anio.size() - 1).planilla();
    }

    private List<MesCalculado> planillasHasta(YearMonth hasta, boolean todas) {
        return planillasHasta(hasta, todas, new HashMap<>());
    }

    private List<MesCalculado> planillasHasta(YearMonth hasta, boolean todas, Map<Long, AcumuladoRenta> acumulado) {
        Map<Long, OtroEmpleador> otros = repositorio.otrosEmpleadores(hasta.getYear());
        Plantel plantel = new Plantel(trabajadores.listarTodos(), trabajadores.horarios());
        List<MesCalculado> meses = new ArrayList<>();
        Map<YearMonth, Map<Long, RonaldmichaResultadoPlanilla>> calculados = new HashMap<>();
        Function<YearMonth, Map<Long, RonaldmichaResultadoPlanilla>> planillasDelSemestre =
                m -> calculados.containsKey(m) ? calculados.get(m) : resultadosDelMes(m);
        for (YearMonth m = YearMonth.of(hasta.getYear(), 1); !m.isAfter(hasta); m = m.plusMonths(1)) {
            YearMonth mes = m;
            Map<Long, RentaMes> guardada = rentaGuardada(mes);
            if (!guardada.isEmpty() && !todas && mes.isBefore(hasta)) {
                guardada.values().forEach(g -> acumulado.computeIfAbsent(g.trabajadorId(), k -> new AcumuladoRenta())
                        .agregar(mes, g.percibido(), g.retencion()));
                continue;
            }
            RonaldmichaParametrosVersion p;
            try {
                p = parametros.vigentes(mes);
            } catch (RonaldmichaParametroNoEncontradoException e) {
                meses.add(new MesCalculado(new PlanillaMes(mes, periodos.situacion(mes), periodos.buscar(mes), List.of(),
                        Optional.of(e.getMessage())), Map.of()));
                continue;
            }
            Map<Long, BigDecimal> gratisDelMes = gratificacionesPagadasEn(mes, planillasDelSemestre);
            MesCalculado calculado = calcularMes(mes, p, plantel,
                    (t, pv) -> historial(t, mes, pv, acumulado, otros, gratisDelMes, guardada), gratisDelMes);
            calculado.renta().forEach((id, r) -> {
                RentaMes g = guardada.getOrDefault(id, r);
                acumulado.computeIfAbsent(id, k -> new AcumuladoRenta()).agregar(mes, g.percibido(), g.retencion());
            });
            Map<Long, RonaldmichaResultadoPlanilla> porTrabajador = new HashMap<>();
            calculado.planilla().filas().forEach(f -> porTrabajador.put(f.trabajador().id(), f.resultado()));
            calculados.put(mes, porTrabajador);
            meses.add(calculado);
        }
        return meses;
    }

    private Map<Long, RentaMes> rentaGuardada(YearMonth mes) {
        return periodos.situacion(mes) == RonaldmichaSituacionPeriodo.CERRADO ? repositorio.rentaDelMes(mes) : Map.of();
    }

    private static HistorialRenta historial(RonaldmichaTrabajador t, YearMonth mes, RonaldmichaParametrosVersion p,
                                            Map<Long, AcumuladoRenta> acumulado, Map<Long, OtroEmpleador> otros,
                                            Map<Long, BigDecimal> gratisDelMes, Map<Long, RentaMes> guardada) {
        AcumuladoRenta a = acumulado.getOrDefault(t.id(), new AcumuladoRenta());
        OtroEmpleador o = otros.get(t.id());
        HistorialRenta h = new HistorialRenta(RonaldmichaDinero.redondear(a.percibido), a.retencionesHasta(mes),
                gratificacionesPorPercibir(t, mes, p, gratisDelMes), o == null ? RonaldmichaDinero.CERO : o.renta(),
                o == null ? RonaldmichaDinero.CERO : o.retencion(), null);
        RentaMes g = guardada.get(t.id());
        return g == null ? h : h.conRetencionGuardada(g.retencion());
    }

    private HistorialRenta historialRenta(RonaldmichaTrabajador t, YearMonth mes) {
        RonaldmichaParametrosVersion p;
        try {
            p = parametros.vigentes(mes);
        } catch (RonaldmichaParametroNoEncontradoException e) {
            return HistorialRenta.SIN_HISTORIA;
        }
        Map<Long, AcumuladoRenta> acumulado = new HashMap<>();
        if (mes.getMonthValue() > 1) {
            planillasHasta(mes.minusMonths(1), false, acumulado);
        }
        return historial(t, mes, p, acumulado, repositorio.otrosEmpleadores(mes.getYear()),
                gratificacionesPagadasEn(mes, this::resultadosDelMes), rentaGuardada(mes));
    }

    private Map<Long, BigDecimal> gratificacionesPagadasEn(YearMonth mes,
                                                          Function<YearMonth, Map<Long, RonaldmichaResultadoPlanilla>> planillas) {
        int m = mes.getMonthValue();
        if (gratificaciones == null || m != 7 && m != 12) {
            return Map.of();
        }
        return gratificaciones.pagadas(new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, mes.getYear(), m == 7 ? 1 : 2),
                planillas);
    }

    private static BigDecimal gratificacionesPorPercibir(RonaldmichaTrabajador t, YearMonth mes, RonaldmichaParametrosVersion p,
                                                         Map<Long, BigDecimal> delMes) {
        BigDecimal total = RonaldmichaDinero.CERO;
        for (int semestre = 1; semestre <= 2; semestre++) {
            RonaldmichaSemestreBeneficio s = new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, mes.getYear(), semestre);
            YearMonth pago = YearMonth.of(mes.getYear(), s.mesPago());
            if (pago.isBefore(mes)) {
                continue;
            }
            if (pago.equals(mes) && delMes.containsKey(t.id())) {
                total = total.add(delMes.get(t.id()));
            } else if (pago.equals(mes) && !delMes.isEmpty()) {
                continue;
            } else {
                total = total.add(gratificacionProyectada(t, s, p));
            }
        }
        return RonaldmichaDinero.redondear(total);
    }

    private static BigDecimal gratificacionProyectada(RonaldmichaTrabajador t, RonaldmichaSemestreBeneficio s, RonaldmichaParametrosVersion p) {
        BigDecimal af = t.asigFamiliar() ? RonaldmichaAportePensionCalculator.asignacionFamiliar(p) : RonaldmichaDinero.CERO;
        BigDecimal bonif = t.salud() == RonaldmichaSalud.EPS ? p.bonifExtEpsPct() : p.bonifExtPct();
        int meses = RonaldmichaGratificacionCalculator.mesesCompletos(t.fechaIngreso(), t.fechaCese(), s.meses());
        return RonaldmichaGratificacionCalculator.calcular(t.sueldo(), af, List.of(), meses, 0, t.regimen(), bonif).total();
    }

    private MesCalculado calcularMes(YearMonth mes, RonaldmichaParametrosVersion p, Plantel plantel,
                                     BiFunction<RonaldmichaTrabajador, RonaldmichaParametrosVersion, HistorialRenta> historial,
                                     Map<Long, BigDecimal> gratisDelMes) {
        RonaldmichaSituacionPeriodo situacion = periodos.situacion(mes);
        Optional<RonaldmichaPeriodo> registro = periodos.buscar(mes);
        Map<Long, Map<LocalDate, RonaldmichaMarcacion>> marcaciones = asistenciaRepositorio.delMes(mes);
        Map<Long, RonaldmichaHorario> horarios = plantel.horarios();
        Map<Long, NovedadMes> novedades = asistenciaRepositorio.novedadesDelMes(mes);
        Map<Long, List<HoraExtraManual>> horasExtra = asistenciaRepositorio.horasExtraManualesDelMes(mes);
        Map<Long, List<RonaldmichaConceptoExtra>> conceptos = repositorio.conceptosExtraDelMes(mes);
        Map<LocalDate, String> extras = marcaciones.isEmpty() ? Map.of() : asistencia.feriadosExtra(mes.getYear());

        List<FilaPlanilla> filas = new ArrayList<>();
        Map<Long, RentaMes> renta = new HashMap<>();
        for (RonaldmichaTrabajador t : plantel.todos()) {
            if (RonaldmichaPlanillaCalculator.diasDelMes(t.fechaIngreso(), t.fechaCese(), mes) == 0) {
                continue;
            }
            RonaldmichaHorario horario = horarios.get(t.id());
            Map<LocalDate, RonaldmichaMarcacion> delTrabajador = marcaciones.getOrDefault(t.id(), Map.of());
            NovedadesPlanilla asis = delTrabajador.isEmpty() || horario == null ? null
                    : asistencia.desdeMarcaciones(t, horario, mes, delTrabajador, extras);
            BigDecimal horas = horario == null ? BigDecimal.ZERO : RonaldmichaJornadaCalculator.calcular(horario).horasDiarias();
            Borrador b = borrador(novedades.getOrDefault(t.id(), NovedadMes.NINGUNA),
                    horasExtra.getOrDefault(t.id(), List.of()), conceptos.getOrDefault(t.id(), List.of()));
            RonaldmichaResultadoPlanilla r = calcular(t, mes, p, horas, asis, b, historial.apply(t, p));
            Tramos extra = asis != null ? new Tramos(asis.he25Min(), asis.he35Min()) : tramosPagados(b.horasExtra(), mes);
            filas.add(new FilaPlanilla(t, r, asis != null, horas, extra.al25() + extra.al35()));
            renta.put(t.id(), new RentaMes(t.id(), mes, r.baseRenta(), gratisDelMes.getOrDefault(t.id(), RonaldmichaDinero.CERO),
                    r.renta().rentaAnualProyectada(), r.renta().impuestoAnual(), r.rentaQuinta()));
        }
        return new MesCalculado(new PlanillaMes(mes, situacion, registro, filas, Optional.empty()), renta);
    }

    private static RonaldmichaResultadoPlanilla calcular(RonaldmichaTrabajador t, YearMonth mes, RonaldmichaParametrosVersion p, BigDecimal horasDiarias,
                                              NovedadesPlanilla asis, Borrador b, HistorialRenta historial) {
        int inasistencias;
        int vacaciones;
        int tardanza;
        int feriados;
        int he25;
        int he35;
        if (asis != null) {
            inasistencias = asis.faltas();
            vacaciones = asis.vacaciones();
            tardanza = asis.tardanzaMin();
            feriados = asis.feriadosTrabajados();
            he25 = asis.he25Min();
            he35 = asis.he35Min();
        } else {
            inasistencias = b.inasistencias();
            vacaciones = b.vacaciones();
            tardanza = b.tardanzaMin();
            feriados = b.feriadosTrabajados();
            Tramos tramos = tramosPagados(b.horasExtra(), mes);
            he25 = tramos.al25();
            he35 = tramos.al35();
        }
        RonaldmichaPlanillaCalculator.Novedades n = new RonaldmichaPlanillaCalculator.Novedades(Math.max(0, inasistencias),
                Math.max(0, vacaciones), Math.max(0, b.diasSubsidiados()), positivo(b.comisiones()),
                positivo(b.bonificacion()), positivo(b.adelanto()), positivo(b.otrosDescuentos()));
        RonaldmichaPlanillaCalculator.Asistencia a = new RonaldmichaPlanillaCalculator.Asistencia(horasDiarias, Math.max(0, tardanza),
                he25, he35, Math.max(0, feriados));
        List<RonaldmichaConceptoExtra> conceptos = b.conceptos().stream()
                .filter(c -> c.tipo() != null && c.monto() != null && c.monto().signum() > 0)
                .toList();
        return RonaldmichaPlanillaCalculator.calcular(t, mes, p, n, a, conceptos, historial);
    }

    public static Tramos tramosPagados(List<HoraExtraManual> horasExtra, YearMonth mes) {
        Map<LocalDate, Integer> minutosPorDia = new TreeMap<>();
        for (HoraExtraManual he : horasExtra) {
            if (he.fecha() != null && YearMonth.from(he.fecha()).equals(mes) && he.horas() != null
                    && he.horas().signum() > 0 && he.tratamiento() == RonaldmichaTratamientoHe.PAGAR) {
                int minutos = he.horas().multiply(BigDecimal.valueOf(60)).setScale(0, RonaldmichaDinero.REDONDEO).intValue();
                minutosPorDia.merge(he.fecha(), minutos, Integer::sum);
            }
        }
        int al25 = 0;
        int al35 = 0;
        for (int minutos : minutosPorDia.values()) {
            Tramos t = RonaldmichaHorasExtraCalculator.dividir(minutos);
            al25 += t.al25();
            al35 += t.al35();
        }
        return new Tramos(al25, al35);
    }

    private NovedadesPlanilla asistenciaDelMes(RonaldmichaTrabajador t, YearMonth mes) {
        try {
            NovedadesPlanilla n = asistencia.novedadesParaPlanilla(t.id(), mes);
            return n.desdeAsistencia() ? n : null;
        } catch (RonaldmichaValidacionException sinHorario) {
            return null;
        }
    }

    private BigDecimal horasDiarias(RonaldmichaTrabajador t) {
        return trabajadores.horario(t.id()).map(h -> RonaldmichaJornadaCalculator.calcular(h).horasDiarias())
                .orElse(BigDecimal.ZERO);
    }

    private Borrador guardado(long trabajadorId, YearMonth mes) {
        return borrador(asistenciaRepositorio.novedadMes(trabajadorId, mes).orElse(NovedadMes.NINGUNA),
                asistenciaRepositorio.horasExtraManuales(trabajadorId, mes), repositorio.conceptosExtra(trabajadorId, mes));
    }

    private static Borrador borrador(NovedadMes n, List<HoraExtraManual> horasExtra, List<RonaldmichaConceptoExtra> conceptos) {
        return new Borrador(n.inasistencias(), n.vacaciones(), n.tardanzaMin(), n.licencia(), n.feriadosTrabajados(),
                n.diasSubsidiados(), n.comisiones(), n.bonificacion(), n.adelanto(), n.otrosDescuentos(), horasExtra,
                conceptos);
    }

    private void exigirAbierto(YearMonth mes) {
        switch (periodos.situacion(mes)) {
            case CERRADO -> throw new RonaldmichaValidacionException("El periodo " + MES.format(mes)
                    + " está cerrado: es de solo lectura. Reábralo con un motivo para modificarlo.");
            case FUTURO -> throw new RonaldmichaValidacionException("El periodo " + MES.format(mes) + " aún no está abierto.");
            case ABIERTO -> { }
        }
    }

    private RonaldmichaTrabajador trabajador(long id) {
        return trabajadores.buscarPorId(id).orElseThrow(() -> new RonaldmichaValidacionException("El trabajador no existe."));
    }

    private static BigDecimal nulo(BigDecimal monto) {
        return monto == null ? RonaldmichaDinero.CERO : monto;
    }

    private static BigDecimal positivo(BigDecimal monto) {
        return nulo(monto).max(RonaldmichaDinero.CERO);
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String texto(BigDecimal horas) {
        return horas.stripTrailingZeros().toPlainString();
    }

    private static String describir(RonaldmichaTrabajador t) {
        return t.codigo() + " · " + t.tipoDoc().codigo() + " " + t.numDoc() + " · " + t.nombreCompleto();
    }
}
