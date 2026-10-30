package com.planillaperu.service;

import com.planillaperu.calc.RonaldmichaResultadoPlanilla;
import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaSemestreBeneficio;
import com.planillaperu.domain.RonaldmichaSistemaPension;
import com.planillaperu.domain.RonaldmichaTipoBeneficio;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.export.RonaldmichaTablaExcel;
import com.planillaperu.service.RonaldmichaBeneficiosService.Beneficio;
import com.planillaperu.service.RonaldmichaBeneficiosService.FilaBeneficio;
import com.planillaperu.service.RonaldmichaPlanillaService.FilaPlanilla;
import com.planillaperu.service.RonaldmichaPlanillaService.PlanillaMes;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;

public class RonaldmichaResumenAnualService {

    public static final int PRIMER_ANIO = 2025;

    public enum Modo {
        NETO("Neto pagado"),
        COSTO("Costo laboral");

        private final String etiqueta;

        Modo(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }

    public record Celda(BigDecimal neto, BigDecimal costo, BigDecimal gratificacion) {

        public BigDecimal valor(Modo modo) {
            return modo == Modo.NETO ? neto : costo;
        }
    }

    public record FilaAnual(RonaldmichaTrabajador trabajador, Map<YearMonth, Celda> meses) {

        public FilaAnual {
            meses = Collections.unmodifiableMap(new LinkedHashMap<>(meses));
        }

        public BigDecimal valor(YearMonth mes, Modo modo) {
            Celda c = meses.get(mes);
            return c == null ? null : c.valor(modo);
        }

        public BigDecimal total(Modo modo) {
            return meses.values().stream().map(c -> c.valor(modo)).reduce(RonaldmichaDinero.CERO, BigDecimal::add);
        }
    }

    public record Concepto(String nombre, String detalle, Map<YearMonth, BigDecimal> porMes) {

        public Concepto {
            porMes = Collections.unmodifiableMap(new LinkedHashMap<>(porMes));
        }

        public BigDecimal total() {
            return porMes.values().stream().reduce(RonaldmichaDinero.CERO, BigDecimal::add);
        }
    }

    public record Resumen(int anio, YearMonth mesActual, List<YearMonth> meses, List<FilaAnual> filas,
                          List<Concepto> conceptos, List<String> observaciones) {

        public Resumen {
            meses = List.copyOf(meses);
            filas = List.copyOf(filas);
            conceptos = List.copyOf(conceptos);
            observaciones = List.copyOf(observaciones);
        }

        public boolean futuro(YearMonth mes) {
            return mes.isAfter(mesActual);
        }

        public static boolean mesDeGratificacion(YearMonth mes) {
            return mes.getMonth() == Month.JULY || mes.getMonth() == Month.DECEMBER;
        }

        public BigDecimal totalMes(YearMonth mes, Modo modo) {
            if (futuro(mes)) {
                return null;
            }
            return filas.stream().map(f -> f.valor(mes, modo)).filter(Objects::nonNull)
                    .reduce(RonaldmichaDinero.CERO, BigDecimal::add);
        }

        public BigDecimal total(Modo modo) {
            return filas.stream().map(f -> f.total(modo)).reduce(RonaldmichaDinero.CERO, BigDecimal::add);
        }
    }

    private record Datos(Resumen resumen, Map<YearMonth, List<FilaPlanilla>> planillas,
                         Map<YearMonth, Map<Long, BigDecimal>> gratificaciones,
                         Map<YearMonth, Map<Long, BigDecimal>> cts) {
    }

    private final RonaldmichaPlanillaService planilla;
    private final RonaldmichaBeneficiosService beneficios;
    private final RonaldmichaAuditoriaService auditoria;
    private final Clock reloj;

    public RonaldmichaResumenAnualService(RonaldmichaPlanillaService planilla, RonaldmichaBeneficiosService beneficios, RonaldmichaAuditoriaService auditoria,
                               Clock reloj) {
        this.planilla = planilla;
        this.beneficios = beneficios;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public List<Integer> anios() {
        int actual = YearMonth.now(reloj).getYear();
        return IntStream.rangeClosed(PRIMER_ANIO, Math.max(PRIMER_ANIO, actual)).boxed()
                .sorted(Collections.reverseOrder()).toList();
    }

    public Resumen consultar(RonaldmichaUsuario usuario, int anio) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "ver el resumen anual");
        return calcular(anio).resumen();
    }

    private Datos calcular(int anio) {
        YearMonth actual = YearMonth.now(reloj);
        List<YearMonth> meses = IntStream.rangeClosed(1, 12).mapToObj(m -> YearMonth.of(anio, m)).toList();
        List<String> observaciones = new ArrayList<>();

        Map<YearMonth, List<FilaPlanilla>> planillas = new LinkedHashMap<>();
        YearMonth hasta = meses.get(meses.size() - 1).isAfter(actual) ? actual : meses.get(meses.size() - 1);
        List<PlanillaMes> delAnio = hasta.getYear() == anio ? planilla.delAnio(hasta) : List.of();
        for (PlanillaMes pm : delAnio) {
            pm.error().ifPresent(e -> observaciones.add(mesTexto(pm.mes()) + ": " + e));
            planillas.put(pm.mes(), pm.filas());
        }
        Map<YearMonth, Map<Long, BigDecimal>> gratificaciones = new LinkedHashMap<>();
        Map<YearMonth, Map<Long, BigDecimal>> cts = new LinkedHashMap<>();
        for (int s = 1; s <= 2; s++) {
            beneficio(new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.GRATIFICACION, anio, s), actual, gratificaciones, observaciones);
            beneficio(new RonaldmichaSemestreBeneficio(RonaldmichaTipoBeneficio.CTS, anio, s), actual, cts, observaciones);
        }

        Map<Long, RonaldmichaTrabajador> trabajadores = new LinkedHashMap<>();
        Map<Long, Map<YearMonth, Celda>> celdas = new HashMap<>();
        planillas.forEach((m, filas) -> {
            Map<Long, BigDecimal> grati = gratificaciones.getOrDefault(m, Map.of());
            for (FilaPlanilla f : filas) {
                RonaldmichaResultadoPlanilla r = f.resultado();
                BigDecimal g = grati.getOrDefault(f.trabajador().id(), RonaldmichaDinero.CERO);
                trabajadores.putIfAbsent(f.trabajador().id(), f.trabajador());
                celdas.computeIfAbsent(f.trabajador().id(), k -> new LinkedHashMap<>())
                        .put(m, new Celda(r.netoAPagar().add(g), r.costoLaboral(), g));
            }
        });
        List<FilaAnual> filas = trabajadores.values().stream()
                .sorted((a, b) -> a.codigo().compareTo(b.codigo()))
                .map(t -> new FilaAnual(t, celdas.get(t.id())))
                .toList();

        List<Concepto> conceptos = List.of(
                concepto("Remuneraciones", "Total de ingresos de la planilla mensual", meses, planillas,
                        RonaldmichaResultadoPlanilla::totalIngresos),
                porTrabajador("Gratificaciones", "Julio y diciembre, con la bonificación extraordinaria (Ley 30334)",
                        meses, actual, gratificaciones),
                porTrabajador("CTS", "Depósitos de mayo y noviembre (D.S. 001-97-TR)", meses, actual, cts),
                concepto("EsSalud", "Aporte del empleador", meses, planillas, RonaldmichaResultadoPlanilla::essalud),
                concepto("ONP", "Retención al trabajador (sistema nacional)", meses, planillas,
                        r -> r.pension().sistema() == RonaldmichaSistemaPension.ONP ? r.pension().total() : RonaldmichaDinero.CERO),
                concepto("AFP", "Retención al trabajador: aporte, prima de seguro y comisión", meses, planillas,
                        r -> r.pension().sistema().esAfp() ? r.pension().total() : RonaldmichaDinero.CERO),
                concepto("Renta de 5.ª", "Retención del impuesto a la renta de quinta categoría", meses, planillas,
                        RonaldmichaResultadoPlanilla::rentaQuinta));
        Resumen resumen = new Resumen(anio, actual, meses, filas, conceptos, observaciones);
        return new Datos(resumen, planillas, gratificaciones, cts);
    }

    private void beneficio(RonaldmichaSemestreBeneficio s, YearMonth actual, Map<YearMonth, Map<Long, BigDecimal>> destino,
                           List<String> observaciones) {
        YearMonth pago = YearMonth.of(s.anio(), s.mesPago());
        if (pago.isAfter(actual)) {
            return;
        }
        Beneficio b = beneficios.calcular(s);
        b.error().ifPresent(e -> observaciones.add(RonaldmichaBeneficiosService.etiqueta(s) + ": " + e));
        Map<Long, BigDecimal> montos = new HashMap<>();
        for (FilaBeneficio f : b.filas()) {
            montos.put(f.trabajador().id(), f.total());
        }
        destino.put(pago, montos);
    }

    private static Concepto concepto(String nombre, String detalle, List<YearMonth> meses,
                                     Map<YearMonth, List<FilaPlanilla>> planillas,
                                     Function<RonaldmichaResultadoPlanilla, BigDecimal> monto) {
        Map<YearMonth, BigDecimal> porMes = new LinkedHashMap<>();
        for (YearMonth m : meses) {
            List<FilaPlanilla> filas = planillas.get(m);
            if (filas != null) {
                porMes.put(m, filas.stream().map(f -> monto.apply(f.resultado())).reduce(RonaldmichaDinero.CERO, BigDecimal::add));
            }
        }
        return new Concepto(nombre, detalle, porMes);
    }

    private static Concepto porTrabajador(String nombre, String detalle, List<YearMonth> meses, YearMonth actual,
                                          Map<YearMonth, Map<Long, BigDecimal>> montos) {
        Map<YearMonth, BigDecimal> porMes = new LinkedHashMap<>();
        for (YearMonth m : meses) {
            if (!m.isAfter(actual)) {
                porMes.put(m, montos.getOrDefault(m, Map.of()).values().stream().reduce(RonaldmichaDinero.CERO, BigDecimal::add));
            }
        }
        return new Concepto(nombre, detalle, porMes);
    }

    public byte[] exportarExcel(RonaldmichaUsuario usuario, int anio) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "exportar el resumen anual");
        Datos d = calcular(anio);
        Resumen r = d.resumen();
        List<RonaldmichaTablaExcel.Hoja> hojas = new ArrayList<>();
        hojas.add(hojaResumen(r));
        List<String> encabezados = List.of("Código", "Trabajador", "Documento", "Total ingresos", "Gratificación",
                "CTS depositada", "ONP", "AFP", "Renta de 5.ª", "Total descuentos", "Neto planilla", "Neto pagado",
                "EsSalud", "Total aportes", "Costo laboral");
        for (YearMonth m : r.meses()) {
            List<List<Object>> filas = new ArrayList<>();
            List<FilaPlanilla> planillaMes = d.planillas().get(m);
            if (planillaMes != null) {
                Map<Long, BigDecimal> grati = d.gratificaciones().getOrDefault(m, Map.of());
                Map<Long, BigDecimal> cts = d.cts().getOrDefault(m, Map.of());
                for (FilaPlanilla f : planillaMes) {
                    RonaldmichaResultadoPlanilla x = f.resultado();
                    RonaldmichaTrabajador t = f.trabajador();
                    BigDecimal g = grati.getOrDefault(t.id(), RonaldmichaDinero.CERO);
                    BigDecimal onp = x.pension().sistema() == RonaldmichaSistemaPension.ONP ? x.pension().total() : RonaldmichaDinero.CERO;
                    BigDecimal afp = x.pension().sistema().esAfp() ? x.pension().total() : RonaldmichaDinero.CERO;
                    filas.add(List.of(t.codigo(), t.nombreCompleto(), t.numDoc(), x.totalIngresos(), g,
                            cts.getOrDefault(t.id(), RonaldmichaDinero.CERO), onp, afp, x.rentaQuinta(), x.totalDescuentos(),
                            x.netoAPagar(), x.netoAPagar().add(g), x.essalud(), x.totalAportes(), x.costoLaboral()));
                }
            }
            String titulo = "Planilla de " + mesTexto(m).toLowerCase(RonaldmichaFormatos.LOCALE_PE)
                    + (r.futuro(m) ? " · mes futuro: sin montos" : "");
            hojas.add(new RonaldmichaTablaExcel.Hoja(mesCorto(m), titulo, encabezados, filas, false,
                    filas.isEmpty() ? List.of() : IntStream.rangeClosed(3, 14).boxed().toList()));
        }
        byte[] excel = RonaldmichaTablaExcel.crear(hojas);
        auditoria.registrar(usuario.usuario(), "RESUMEN_ANUAL_EXPORTADO", "Año " + anio + " · " + r.filas().size()
                + " trabajadores · Excel (resumen y 12 hojas por mes)");
        return excel;
    }

    private static RonaldmichaTablaExcel.Hoja hojaResumen(Resumen r) {
        List<String> encabezados = new ArrayList<>(List.of("Concepto / trabajador"));
        r.meses().forEach(m -> encabezados.add(mesCorto(m)));
        encabezados.add("Total");
        List<List<Object>> filas = new ArrayList<>();
        for (Modo modo : Modo.values()) {
            filas.add(seccion(modo.etiqueta().toUpperCase(RonaldmichaFormatos.LOCALE_PE), r.meses().size()));
            for (FilaAnual f : r.filas()) {
                filas.add(fila(f.trabajador().codigo() + " · " + f.trabajador().nombreCompleto(), r,
                        m -> f.valor(m, modo), f.total(modo)));
            }
            filas.add(fila("Total " + modo.etiqueta().toLowerCase(RonaldmichaFormatos.LOCALE_PE), r, m -> r.totalMes(m, modo),
                    r.total(modo)));
        }
        filas.add(seccion("RESUMEN POR CONCEPTO", r.meses().size()));
        for (Concepto c : r.conceptos()) {
            filas.add(fila(c.nombre(), r, m -> c.porMes().get(m), c.total()));
        }
        return new RonaldmichaTablaExcel.Hoja("Resumen", "Resumen anual " + r.anio()
                + " · julio y diciembre incluyen la gratificación en el neto", encabezados, filas, false);
    }

    private static List<Object> seccion(String titulo, int meses) {
        List<Object> fila = new ArrayList<>();
        fila.add(titulo);
        for (int i = 0; i <= meses; i++) {
            fila.add("");
        }
        return fila;
    }

    private static List<Object> fila(String nombre, Resumen r, Function<YearMonth, BigDecimal> valor, BigDecimal total) {
        List<Object> fila = new ArrayList<>();
        fila.add(nombre);
        for (YearMonth m : r.meses()) {
            BigDecimal v = r.futuro(m) ? null : valor.apply(m);
            fila.add(v == null ? "" : v);
        }
        fila.add(total);
        return fila;
    }

    public static String mesCorto(YearMonth m) {
        String s = m.getMonth().getDisplayName(TextStyle.SHORT, RonaldmichaFormatos.LOCALE_PE).replace(".", "");
        return s.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + s.substring(1);
    }

    private static String mesTexto(YearMonth m) {
        String s = m.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, RonaldmichaFormatos.LOCALE_PE) + " " + m.getYear();
        return s.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + s.substring(1);
    }
}
