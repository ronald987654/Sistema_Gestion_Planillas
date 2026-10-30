package com.planillaperu.service;

import com.planillaperu.calc.RonaldmichaFeriadosPeru;
import com.planillaperu.calc.RonaldmichaObligacionesCalculator;
import com.planillaperu.calc.RonaldmichaObligacionesCalculator.Obligacion;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla;
import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaSituacionPeriodo;
import com.planillaperu.domain.RonaldmichaTipoComision;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository;
import com.planillaperu.repository.RonaldmichaDocumentoRepository;
import com.planillaperu.service.RonaldmichaPlanillaService.FilaPlanilla;
import com.planillaperu.service.RonaldmichaPlanillaService.PlanillaMes;
import com.planillaperu.service.RonaldmichaVacacionesService.FilaVacaciones;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

public class RonaldmichaInicioService {

    public static final int MESES_SERIE = 6;
    public static final int OBLIGACIONES_MAXIMAS = 8;
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM yyyy", RonaldmichaFormatos.LOCALE_PE);
    private static final int LONGITUD_CUSPP = 12;

    public enum Destino {
        EMPRESA, PARAMETROS, TRABAJADORES, ASISTENCIA, PLANILLA, PLANILLA_DETALLE, PLANILLA_CIERRE, REPORTES, BOLETAS,
        VACACIONES, APROBACIONES
    }

    public record Paso(String titulo, String detalle, boolean hecho, Destino destino) {
    }

    public record Ciclo(YearMonth mes, List<Paso> pasos) {

        public long hechos() {
            return pasos.stream().filter(Paso::hecho).count();
        }

        public double progreso() {
            return pasos.isEmpty() ? 0 : (double) hechos() / pasos.size();
        }
    }

    public record Kpi(String titulo, BigDecimal actual, BigDecimal anterior, boolean moneda) {

        public Optional<BigDecimal> variacion() {
            if (anterior == null || anterior.signum() == 0) {
                return Optional.empty();
            }
            return Optional.of(actual.subtract(anterior).multiply(BigDecimal.valueOf(100))
                    .divide(anterior, 1, RoundingMode.HALF_UP));
        }
    }

    public record PuntoMes(YearMonth mes, BigDecimal costoLaboral, BigDecimal neto, int trabajadores, boolean conDatos) {
    }

    public record CostoArea(String area, BigDecimal costo, int trabajadores) {
    }

    public record GrupoPension(String grupo, int trabajadores, BigDecimal monto) {
    }

    public enum Nivel { CRITICO, ATENCION }

    public record Alerta(Nivel nivel, String titulo, String detalle, Destino destino) {
    }

    public record Resumen(YearMonth mes, LocalDate fechaPago, int trabajadores, Ciclo ciclo, List<Kpi> kpis,
                          List<PuntoMes> serie, List<CostoArea> areas, List<GrupoPension> pensiones,
                          List<Alerta> alertas, List<Obligacion> obligaciones) {
    }

    private final RonaldmichaPlanillaService planilla;
    private final RonaldmichaParametrosService parametros;
    private final RonaldmichaEmpresaService empresa;
    private final RonaldmichaVacacionesService vacaciones;
    private final RonaldmichaPeriodoService periodos;
    private final RonaldmichaAsistenciaRepository asistencia;
    private final RonaldmichaDocumentoRepository documentos;
    private final RonaldmichaAprobacionService aprobaciones;
    private final Clock reloj;

    public RonaldmichaInicioService(RonaldmichaPlanillaService planilla, RonaldmichaParametrosService parametros, RonaldmichaEmpresaService empresa,
                         RonaldmichaVacacionesService vacaciones, RonaldmichaPeriodoService periodos, RonaldmichaAsistenciaRepository asistencia,
                         RonaldmichaDocumentoRepository documentos, RonaldmichaAprobacionService aprobaciones, Clock reloj) {
        this.planilla = planilla;
        this.parametros = parametros;
        this.empresa = empresa;
        this.vacaciones = vacaciones;
        this.periodos = periodos;
        this.asistencia = asistencia;
        this.documentos = documentos;
        this.aprobaciones = aprobaciones;
        this.reloj = reloj;
    }

    public Resumen resumen(RonaldmichaUsuario usuario, YearMonth mes) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "ver el resumen del Inicio");
        Map<YearMonth, PlanillaMes> planillas = new HashMap<>();
        Function<YearMonth, PlanillaMes> delMes = m -> planillas.computeIfAbsent(m, planilla::delMes);
        PlanillaMes actual = delMes.apply(mes);
        PlanillaMes anterior = delMes.apply(mes.minusMonths(1));
        Optional<RonaldmichaParametrosVersion> p = vigentes(mes);

        List<PuntoMes> serie = new ArrayList<>();
        for (int i = MESES_SERIE - 1; i >= 0; i--) {
            YearMonth m = mes.minusMonths(i);
            PlanillaMes pm = delMes.apply(m);
            serie.add(new PuntoMes(m, suma(pm, RonaldmichaResultadoPlanilla::costoLaboral), suma(pm, RonaldmichaResultadoPlanilla::netoAPagar),
                    pm.filas().size(), pm.error().isEmpty()));
        }
        List<Alerta> alertas = alertas(usuario, mes, actual, p);
        return new Resumen(mes, fechaPago(mes), actual.filas().size(), ciclo(mes, actual, p, alertas),
                kpis(actual, anterior), serie, areas(actual), pensiones(actual), alertas, obligaciones());
    }

    private LocalDate fechaPago(YearMonth mes) {
        Integer dia = empresa.obtener().diaPago();
        return dia == null ? mes.atEndOfMonth() : mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }

    private Ciclo ciclo(YearMonth mes, PlanillaMes pm, Optional<RonaldmichaParametrosVersion> p, List<Alerta> alertas) {
        List<String> faltan = empresa.faltantes();
        List<FilaPlanilla> filas = pm.filas();
        long conMarcaciones = filas.stream().filter(FilaPlanilla::desdeAsistencia).count();
        long sinMarcaciones = filas.size() - conMarcaciones;
        long conNovedades = filas.stream().filter(f -> !f.desdeAsistencia())
                .filter(f -> asistencia.novedadMes(f.trabajador().id(), mes).isPresent()).count();
        boolean criticas = alertas.stream().anyMatch(a -> a.nivel() == Nivel.CRITICO && a.destino() != Destino.EMPRESA);
        RonaldmichaSituacionPeriodo situacion = periodos.situacion(mes);
        Set<Long> conBoleta = documentos.trabajadoresConDocumento(RonaldmichaReporte.BOLETA_PAGO.tipo(), mes);
        long boletas = filas.stream().filter(f -> conBoleta.contains(f.trabajador().id())).count();

        List<Paso> pasos = List.of(
                new Paso("Empresa y parámetros", faltan.isEmpty() ? (p.isPresent() ? "Datos completos y parámetros vigentes"
                        : "Faltan los parámetros del mes") : "Faltan " + faltan.size() + " datos de la empresa",
                        faltan.isEmpty() && p.isPresent(), Destino.EMPRESA),
                new Paso("Trabajadores", filas.size() + " con vínculo en el mes", !filas.isEmpty(), Destino.TRABAJADORES),
                new Paso("Asistencia", conMarcaciones + " de " + filas.size() + " con marcaciones",
                        !filas.isEmpty() && sinMarcaciones == 0, Destino.ASISTENCIA),
                new Paso("Novedades", sinMarcaciones == 0 ? "Se toman de la asistencia"
                        : conNovedades + " de " + sinMarcaciones + " sin marcaciones con novedades",
                        !filas.isEmpty() && conNovedades == sinMarcaciones, Destino.PLANILLA),
                new Paso("Revisión", pm.error().orElse(criticas ? "Hay alertas críticas por revisar" : "Sin observaciones"),
                        pm.error().isEmpty() && !filas.isEmpty() && !criticas, Destino.PLANILLA_DETALLE),
                new Paso("Cierre", situacion.etiqueta(), situacion == RonaldmichaSituacionPeriodo.CERRADO, Destino.PLANILLA_CIERRE),
                new Paso("Boletas emitidas", boletas + " de " + filas.size() + " boletas",
                        !filas.isEmpty() && boletas == filas.size(), Destino.BOLETAS));
        return new Ciclo(mes, pasos);
    }

    private static List<Kpi> kpis(PlanillaMes actual, PlanillaMes anterior) {
        boolean hayAnterior = anterior.error().isEmpty() && !anterior.filas().isEmpty();
        return List.of(
                new Kpi("Trabajadores en planilla", BigDecimal.valueOf(actual.filas().size()),
                        hayAnterior ? BigDecimal.valueOf(anterior.filas().size()) : null, false),
                kpi("Neto a pagar", actual, anterior, hayAnterior, RonaldmichaResultadoPlanilla::netoAPagar),
                kpi("Aportes del empleador", actual, anterior, hayAnterior, RonaldmichaResultadoPlanilla::totalAportes),
                kpi("Costo laboral del mes", actual, anterior, hayAnterior, RonaldmichaResultadoPlanilla::costoLaboral));
    }

    private static Kpi kpi(String titulo, PlanillaMes actual, PlanillaMes anterior, boolean hayAnterior,
                           Function<RonaldmichaResultadoPlanilla, BigDecimal> valor) {
        return new Kpi(titulo, suma(actual, valor), hayAnterior ? suma(anterior, valor) : null, true);
    }

    private static List<CostoArea> areas(PlanillaMes pm) {
        Map<String, BigDecimal> costo = new TreeMap<>();
        Map<String, Integer> cantidad = new HashMap<>();
        for (FilaPlanilla f : pm.filas()) {
            String area = f.trabajador().area() == null || f.trabajador().area().isBlank() ? "Sin área" : f.trabajador().area();
            costo.merge(area, f.resultado().costoLaboral(), RonaldmichaDinero::sumar);
            cantidad.merge(area, 1, Integer::sum);
        }
        return costo.entrySet().stream()
                .map(e -> new CostoArea(e.getKey(), RonaldmichaDinero.redondear(e.getValue()), cantidad.get(e.getKey())))
                .sorted(Comparator.comparing(CostoArea::costo).reversed())
                .toList();
    }

    private static List<GrupoPension> pensiones(PlanillaMes pm) {
        Map<String, Integer> grupos = new LinkedHashMap<>();
        Map<String, BigDecimal> montos = new HashMap<>();
        for (String g : List.of("ONP", "AFP flujo", "AFP mixta")) {
            grupos.put(g, 0);
            montos.put(g, BigDecimal.ZERO);
        }
        for (FilaPlanilla f : pm.filas()) {
            RonaldmichaTrabajador t = f.trabajador();
            String grupo = !t.sistemaPension().esAfp() ? "ONP"
                    : t.tipoComision() == RonaldmichaTipoComision.MIXTA ? "AFP mixta" : "AFP flujo";
            grupos.merge(grupo, 1, Integer::sum);
            montos.merge(grupo, f.resultado().pension().total(), RonaldmichaDinero::sumar);
        }
        return grupos.entrySet().stream()
                .map(e -> new GrupoPension(e.getKey(), e.getValue(), RonaldmichaDinero.redondear(montos.get(e.getKey()))))
                .toList();
    }

    private List<Alerta> alertas(RonaldmichaUsuario usuario, YearMonth mes, PlanillaMes pm, Optional<RonaldmichaParametrosVersion> p) {
        List<Alerta> lista = new ArrayList<>();
        List<String> faltan = empresa.faltantes();
        if (!faltan.isEmpty()) {
            lista.add(new Alerta(Nivel.CRITICO, "Datos de la empresa incompletos", "Faltan: " + String.join(", ", faltan)
                    + ". Los documentos llevan un aviso hasta completarlos.", Destino.EMPRESA));
        }
        List<String> sinAsistencia = pm.filas().stream()
                .filter(f -> !f.desdeAsistencia() && asistencia.novedadMes(f.trabajador().id(), mes).isEmpty())
                .map(f -> f.trabajador().nombreCompleto()).toList();
        if (!sinAsistencia.isEmpty()) {
            lista.add(new Alerta(Nivel.ATENCION, "Asistencia sin registrar (" + sinAsistencia.size() + ")",
                    String.join("; ", sinAsistencia) + ". Sin marcaciones ni novedades se paga el mes completo.",
                    Destino.ASISTENCIA));
        }
        int porAprobar = aprobaciones.pendientes(usuario).size();
        if (porAprobar > 0) {
            lista.add(new Alerta(Nivel.ATENCION, porAprobar == 1 ? "1 aprobación pendiente"
                    : porAprobar + " aprobaciones pendientes",
                    "Cambios sensibles a la espera de un segundo usuario.", Destino.APROBACIONES));
        }
        if (p.isEmpty()) {
            lista.add(new Alerta(Nivel.CRITICO, "Sin parámetros para " + MES.format(mes),
                    "Registre la RMV, la UIT y las tasas del periodo para calcular la planilla.", Destino.PARAMETROS));
        } else {
            List<String> bajoRmv = pm.filas().stream().map(FilaPlanilla::trabajador)
                    .filter(t -> t.sueldo().compareTo(p.get().rmv()) < 0).map(RonaldmichaTrabajador::nombreCompleto).toList();
            if (!bajoRmv.isEmpty()) {
                lista.add(new Alerta(Nivel.CRITICO, "Básico menor a la RMV (" + RonaldmichaFormatos.moneda(p.get().rmv()) + ")",
                        String.join("; ", bajoRmv), Destino.TRABAJADORES));
            }
        }
        List<String> sinCuspp = pm.filas().stream().map(FilaPlanilla::trabajador)
                .filter(t -> t.sistemaPension().esAfp() && (t.cuspp() == null || t.cuspp().trim().length() != LONGITUD_CUSPP))
                .map(RonaldmichaTrabajador::nombreCompleto).toList();
        if (!sinCuspp.isEmpty()) {
            lista.add(new Alerta(Nivel.CRITICO, "Falta el CUSPP", String.join("; ", sinCuspp)
                    + ". AFPnet no acepta afiliados sin CUSPP.", Destino.TRABAJADORES));
        }
        List<FilaVacaciones> acumuladas = vacaciones.alertasIndemnizacion(usuario);
        if (!acumuladas.isEmpty()) {
            lista.add(new Alerta(Nivel.ATENCION, "Vacaciones acumuladas", String.join("; ", acumuladas.stream()
                    .map(f -> f.trabajador().nombreCompleto() + " (" + f.resumen().periodosSinGozar() + " periodos)").toList())
                    + ". Con 2 periodos sin gozar corresponde la indemnización vacacional.", Destino.VACACIONES));
        }
        YearMonth hoy = YearMonth.now(reloj);
        for (RonaldmichaParametrosVersion v : parametros.listar()) {
            if (!v.vigente() || v.vigenteDesde().isBefore(hoy)) {
                continue;
            }
            boolean programada = v.vigenteDesde().isAfter(hoy);
            lista.add(new Alerta(Nivel.ATENCION, (programada ? "Parámetros programados desde " : "Nuevos parámetros vigentes desde ")
                    + MES.format(v.vigenteDesde()), "RMV " + RonaldmichaFormatos.moneda(v.rmv()) + " · UIT " + RonaldmichaFormatos.moneda(v.uit())
                    + (v.norma() == null || v.norma().isBlank() ? "" : " · " + v.norma()), Destino.PARAMETROS));
        }
        return lista;
    }

    private List<Obligacion> obligaciones() {
        LocalDate hoy = LocalDate.now(reloj);
        Map<Integer, Map<LocalDate, String>> feriados = new HashMap<>();
        for (int anio = hoy.getYear(); anio <= hoy.getYear() + 1; anio++) {
            feriados.put(anio, RonaldmichaFeriadosPeru.combinar(anio, asistencia.feriadosExtra(anio)));
        }
        return RonaldmichaObligacionesCalculator.proximas(hoy, empresa.obtener().ruc(), feriados, OBLIGACIONES_MAXIMAS);
    }

    private Optional<RonaldmichaParametrosVersion> vigentes(YearMonth mes) {
        try {
            return Optional.of(parametros.vigentes(mes));
        } catch (RonaldmichaParametroNoEncontradoException e) {
            return Optional.empty();
        }
    }

    private static BigDecimal suma(PlanillaMes pm, Function<RonaldmichaResultadoPlanilla, BigDecimal> valor) {
        return RonaldmichaDinero.sumar(pm.filas().stream().map(f -> valor.apply(f.resultado())).toArray(BigDecimal[]::new));
    }
}
