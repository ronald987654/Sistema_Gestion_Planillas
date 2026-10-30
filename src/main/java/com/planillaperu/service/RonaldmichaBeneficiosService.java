package com.planillaperu.service;

import com.planillaperu.calc.RonaldmichaAportePensionCalculator;
import com.planillaperu.calc.RonaldmichaCtsCalculator;
import com.planillaperu.calc.RonaldmichaGratificacionCalculator;
import com.planillaperu.calc.RonaldmichaPlanillaCalculator;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla;
import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaSalud;
import com.planillaperu.domain.RonaldmichaSemestreBeneficio;
import com.planillaperu.domain.RonaldmichaTipoBeneficio;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.export.RonaldmichaConstanciaBeneficio;
import com.planillaperu.export.RonaldmichaConstanciaBeneficio.Dato;
import com.planillaperu.export.RonaldmichaConstanciaBeneficio.Linea;
import com.planillaperu.export.RonaldmichaTablaExcel;
import com.planillaperu.export.doc.RonaldmichaDocumentModel;
import com.planillaperu.export.doc.RonaldmichaPdfRenderer;
import com.planillaperu.export.doc.RonaldmichaPlantillas;
import com.planillaperu.export.doc.RonaldmichaWordRenderer;
import com.planillaperu.repository.RonaldmichaBeneficioRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

public class RonaldmichaBeneficiosService {

    public static final String NOTA_GRATIFICACION = "Ley 27735 y D.S. 005-2002-TR: la gratificación se calcula por "
            + "meses calendario completos del semestre, menos 1/30 de sexto por cada falta; la remuneración variable "
            + "(horas extra, feriados trabajados y comisiones) se promedia si se percibió en 3 o más meses. "
            + "Bonificación extraordinaria de la Ley 30334: 9 % (6.75 % con EPS), inafecta a aportes y afecta a la "
            + "renta de quinta categoría. Pequeña empresa: 50 %; microempresa: no corresponde.";
    public static final String NOTA_CTS = "D.S. 001-97-TR: remuneración computable = básico + asignación familiar + "
            + "promedio de la variable percibida en 3 o más meses + 1/6 de la gratificación del semestre. "
            + "CTS = computable / 12 por mes y / 360 por día, menos 1/360 por falta. Pequeña empresa: 50 %; "
            + "microempresa: no corresponde.";
    private static final int FALTAS_MAXIMAS = 180;
    private static final String SIN_CUENTA = "Sin cuenta";

    public record FilaBeneficio(
            RonaldmichaTrabajador trabajador,
            int meses,
            int dias,
            int faltas,
            BigDecimal basico,
            BigDecimal asignacionFamiliar,
            Map<YearMonth, BigDecimal> variables,
            BigDecimal promedioVariable,
            BigDecimal gratificacion,
            BigDecimal sextoGratificacion,
            BigDecimal computable,
            BigDecimal importe,
            BigDecimal bonificacion,
            BigDecimal total,
            int faltasAuto,
            BigDecimal basicoAuto,
            BigDecimal gratificacionAuto,
            boolean ajustado) {

        public boolean basicoAjustado() {
            return ajustado && basico.compareTo(basicoAuto) != 0;
        }

        public boolean faltasAjustadas() {
            return ajustado && faltas != faltasAuto;
        }

        public boolean gratificacionAjustada() {
            return ajustado && gratificacion.compareTo(gratificacionAuto) != 0;
        }
    }

    public record Beneficio(RonaldmichaSemestreBeneficio semestre, List<FilaBeneficio> filas, Optional<String> error) {

        public Beneficio {
            filas = List.copyOf(filas);
        }

        public long ajustados() {
            return filas.stream().filter(FilaBeneficio::ajustado).count();
        }
    }

    public record Ajuste(BigDecimal basico, Integer faltas, BigDecimal gratificacion) {
    }

    public record Totales(int trabajadores, int faltas, BigDecimal basico, BigDecimal asignacionFamiliar,
                          Map<YearMonth, BigDecimal> variables, BigDecimal promedioVariable, BigDecimal gratificacion,
                          BigDecimal sextoGratificacion, BigDecimal computable, BigDecimal importe,
                          BigDecimal bonificacion, BigDecimal total) {

        public static Totales de(RonaldmichaSemestreBeneficio s, List<FilaBeneficio> filas) {
            Map<YearMonth, BigDecimal> variables = new TreeMap<>();
            s.meses().forEach(m -> variables.put(m, RonaldmichaDinero.CERO));
            int faltas = 0;
            BigDecimal[] t = new BigDecimal[9];
            Arrays.fill(t, RonaldmichaDinero.CERO);
            for (FilaBeneficio f : filas) {
                faltas += f.faltas();
                f.variables().forEach((m, v) -> variables.merge(m, v, BigDecimal::add));
                BigDecimal[] v = {f.basico(), f.asignacionFamiliar(), f.promedioVariable(), f.gratificacion(),
                        f.sextoGratificacion(), f.computable(), f.importe(), f.bonificacion(), f.total()};
                for (int i = 0; i < v.length; i++) {
                    t[i] = t[i].add(v[i]);
                }
            }
            return new Totales(filas.size(), faltas, t[0], t[1], variables, t[2], t[3], t[4], t[5], t[6], t[7], t[8]);
        }
    }

    private final RonaldmichaTrabajadorRepository trabajadores;
    private final RonaldmichaBeneficioRepository repositorio;
    private final RonaldmichaPlanillaService planilla;
    private final RonaldmichaParametrosService parametros;
    private final RonaldmichaEmpresaService empresa;
    private final RonaldmichaAuditoriaService auditoria;

    public RonaldmichaBeneficiosService(RonaldmichaTrabajadorRepository trabajadores, RonaldmichaBeneficioRepository repositorio,
                             RonaldmichaPlanillaService planilla, RonaldmichaParametrosService parametros, RonaldmichaEmpresaService empresa,
                             RonaldmichaAuditoriaService auditoria) {
        this.trabajadores = trabajadores;
        this.repositorio = repositorio;
        this.planilla = planilla;
        this.parametros = parametros;
        this.empresa = empresa;
        this.auditoria = auditoria;
        planilla.usarGratificaciones(this::totalesPagados);
    }

    private Map<Long, BigDecimal> totalesPagados(RonaldmichaSemestreBeneficio s,
                                                 Function<YearMonth, Map<Long, RonaldmichaResultadoPlanilla>> planillas) {
        Map<Long, BigDecimal> mapa = new HashMap<>();
        calcular(s, planillas).filas().forEach(f -> mapa.put(f.trabajador().id(), f.total()));
        return mapa;
    }

    public Beneficio consultar(RonaldmichaUsuario usuario, RonaldmichaSemestreBeneficio s) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "ver " + nombre(s.tipo()));
        return calcular(s);
    }

    Beneficio calcular(RonaldmichaSemestreBeneficio s) {
        return calcular(s, planilla::resultadosDelMes);
    }

    private Beneficio calcular(RonaldmichaSemestreBeneficio s, Function<YearMonth, Map<Long, RonaldmichaResultadoPlanilla>> planillasDelMes) {
        RonaldmichaParametrosVersion p;
        try {
            p = parametros.vigentes(s.hasta());
        } catch (RonaldmichaParametroNoEncontradoException e) {
            return new Beneficio(s, List.of(), Optional.of(e.getMessage()));
        }
        Map<YearMonth, Map<Long, RonaldmichaResultadoPlanilla>> planillas = new LinkedHashMap<>();
        s.meses().forEach(m -> planillas.put(m, planillasDelMes.apply(m)));
        Map<Long, RonaldmichaBeneficioRepository.Ajuste> ajustes = repositorio.ajustes(s);
        Map<Long, BigDecimal> gratificaciones = s.tipo() == RonaldmichaTipoBeneficio.CTS
                ? gratificacionesPara(s.gratificacionDelSexto()) : Map.of();

        List<FilaBeneficio> filas = new ArrayList<>();
        for (RonaldmichaTrabajador t : trabajadores.listarTodos()) {
            if (s.meses().stream().allMatch(m -> RonaldmichaPlanillaCalculator.diasDelMes(t.fechaIngreso(), t.fechaCese(), m) == 0)) {
                continue;
            }
            Map<YearMonth, BigDecimal> variables = new LinkedHashMap<>();
            int faltasAuto = 0;
            for (YearMonth m : s.meses()) {
                RonaldmichaResultadoPlanilla r = planillas.get(m).get(t.id());
                variables.put(m, r == null ? RonaldmichaDinero.CERO : variable(r));
                faltasAuto += r == null ? 0 : r.inasistencias();
            }
            BigDecimal af = t.asigFamiliar() ? RonaldmichaAportePensionCalculator.asignacionFamiliar(p) : RonaldmichaDinero.CERO;
            RonaldmichaBeneficioRepository.Ajuste a = ajustes.get(t.id());
            int faltas = a == null ? faltasAuto : a.faltas();
            BigDecimal basicoAuto = RonaldmichaDinero.redondear(t.sueldo());
            List<BigDecimal> lista = List.copyOf(variables.values());
            if (s.tipo() == RonaldmichaTipoBeneficio.GRATIFICACION) {
                BigDecimal basico = a == null || a.basico() == null ? basicoAuto : a.basico();
                RonaldmichaGratificacionCalculator.Resultado r = RonaldmichaGratificacionCalculator.calcular(basico, af, lista,
                        RonaldmichaGratificacionCalculator.mesesCompletos(t.fechaIngreso(), t.fechaCese(), s.meses()), faltas,
                        t.regimen(), bonificacionPct(t, p));
                filas.add(new FilaBeneficio(t, r.meses(), 0, faltas, r.basico(), r.asignacionFamiliar(), variables,
                        r.promedioVariable(), r.gratificacion(), RonaldmichaDinero.CERO, r.computable(), r.gratificacion(),
                        r.bonificacion(), r.total(), faltasAuto, basicoAuto, r.gratificacion(), a != null));
            } else {
                BigDecimal gratiAuto = gratificaciones.getOrDefault(t.id(), RonaldmichaDinero.CERO);
                BigDecimal grati = a == null || a.gratificacion() == null ? gratiAuto : a.gratificacion();
                RonaldmichaCtsCalculator.Resultado r = RonaldmichaCtsCalculator.calcular(basicoAuto, af, lista,
                        RonaldmichaCtsCalculator.tiempo(t.fechaIngreso(), t.fechaCese(), s.meses()), faltas, grati, t.regimen());
                filas.add(new FilaBeneficio(t, r.meses(), r.dias(), faltas, r.basico(), r.asignacionFamiliar(),
                        variables, r.promedioVariable(), r.gratificacion(), r.sextoGratificacion(), r.computable(),
                        r.cts(), RonaldmichaDinero.CERO, r.cts(), faltasAuto, basicoAuto, gratiAuto, a != null));
            }
        }
        return new Beneficio(s, filas, Optional.empty());
    }

    private Map<Long, BigDecimal> gratificacionesPara(RonaldmichaSemestreBeneficio grati) {
        Map<Long, BigDecimal> mapa = new HashMap<>();
        calcular(grati).filas().forEach(f -> mapa.put(f.trabajador().id(), f.gratificacion()));
        return mapa;
    }

    public BigDecimal gratificacionDe(long trabajadorId, RonaldmichaSemestreBeneficio grati) {
        return gratificacionesPara(grati).getOrDefault(trabajadorId, RonaldmichaDinero.CERO);
    }

    static BigDecimal variable(RonaldmichaResultadoPlanilla r) {
        return RonaldmichaDinero.sumar(r.horasExtra25(), r.horasExtra35(), r.feriadoTrabajado(), r.comisiones());
    }

    public void guardarAjustes(RonaldmichaUsuario usuario, RonaldmichaSemestreBeneficio s, Map<Long, Ajuste> cambios) {
        RonaldmichaPermisos.exigirAdmin(usuario, "ajustar " + nombre(s.tipo()));
        Beneficio actual = calcular(s);
        Map<Long, FilaBeneficio> porId = new HashMap<>();
        actual.filas().forEach(f -> porId.put(f.trabajador().id(), f));
        List<String> errores = new ArrayList<>();
        cambios.forEach((id, c) -> {
            FilaBeneficio f = porId.get(id);
            String quien = f == null ? "Trabajador " + id : f.trabajador().nombreCompleto();
            if (f == null) {
                errores.add(quien + " no está en " + etiqueta(s) + ".");
                return;
            }
            if (c.faltas() != null && (c.faltas() < 0 || c.faltas() > FALTAS_MAXIMAS)) {
                errores.add(quien + ": las faltas deben estar entre 0 y " + FALTAS_MAXIMAS + ".");
            }
            if (c.basico() != null && c.basico().signum() <= 0) {
                errores.add(quien + ": el básico debe ser mayor que cero.");
            }
            if (c.gratificacion() != null && c.gratificacion().signum() < 0) {
                errores.add(quien + ": la gratificación no puede ser negativa.");
            }
        });
        if (!errores.isEmpty()) {
            throw new RonaldmichaValidacionException(errores);
        }
        String accion = s.tipo() == RonaldmichaTipoBeneficio.GRATIFICACION ? "AJUSTE_GRATIFICACION" : "AJUSTE_CTS";
        cambios.forEach((id, c) -> {
            FilaBeneficio f = porId.get(id);
            int faltas = c.faltas() == null ? f.faltas() : c.faltas();
            BigDecimal basico = s.tipo() == RonaldmichaTipoBeneficio.GRATIFICACION
                    ? RonaldmichaDinero.redondear(c.basico() == null ? f.basico() : c.basico()) : null;
            BigDecimal grati = s.tipo() == RonaldmichaTipoBeneficio.CTS
                    ? RonaldmichaDinero.redondear(c.gratificacion() == null ? f.gratificacion() : c.gratificacion()) : null;
            boolean igualAuto = faltas == f.faltasAuto()
                    && (basico == null || basico.compareTo(f.basicoAuto()) == 0)
                    && (grati == null || grati.compareTo(f.gratificacionAuto()) == 0);
            boolean sinCambio = faltas == f.faltas()
                    && (basico == null || basico.compareTo(f.basico()) == 0)
                    && (grati == null || grati.compareTo(f.gratificacion()) == 0);
            if (sinCambio) {
                return;
            }
            if (igualAuto) {
                repositorio.borrar(s, id);
            } else {
                repositorio.guardar(s, new RonaldmichaBeneficioRepository.Ajuste(id, basico, faltas, grati));
            }
            List<String> detalle = new ArrayList<>();
            if (basico != null && basico.compareTo(f.basico()) != 0) {
                detalle.add("básico " + RonaldmichaFormatos.moneda(f.basico()) + " → " + RonaldmichaFormatos.moneda(basico));
            }
            if (faltas != f.faltas()) {
                detalle.add("faltas " + f.faltas() + " → " + faltas);
            }
            if (grati != null && grati.compareTo(f.gratificacion()) != 0) {
                detalle.add("gratificación " + RonaldmichaFormatos.moneda(f.gratificacion()) + " → " + RonaldmichaFormatos.moneda(grati));
            }
            if (igualAuto) {
                detalle.add("vuelve al cálculo automático");
            }
            auditoria.registrar(usuario.usuario(), accion, describir(f.trabajador()) + " · " + etiqueta(s) + " · "
                    + String.join(" · ", detalle));
        });
    }

    public int restablecer(RonaldmichaUsuario usuario, RonaldmichaSemestreBeneficio s) {
        RonaldmichaPermisos.exigirAdmin(usuario, "restablecer " + nombre(s.tipo()));
        int borrados = repositorio.borrarTodos(s);
        auditoria.registrar(usuario.usuario(), "AJUSTES_BENEFICIO_RESTABLECIDOS", etiqueta(s) + " · " + borrados
                + (borrados == 1 ? " ajuste quitado" : " ajustes quitados") + " · cálculo automático");
        return borrados;
    }

    public byte[] planillaExcel(RonaldmichaUsuario usuario, RonaldmichaSemestreBeneficio s, Long trabajadorId) {
        List<FilaBeneficio> filas = filtrar(usuario, s, trabajadorId);
        List<String> encabezados = new ArrayList<>(List.of("Documento", "Apellidos y nombres", "Régimen", "Ingreso",
                "Meses"));
        if (s.tipo() == RonaldmichaTipoBeneficio.CTS) {
            encabezados.add("Días");
        }
        encabezados.addAll(List.of("Faltas", "Básico", "Asignación familiar"));
        s.meses().forEach(m -> encabezados.add("Variable " + mesCorto(m)));
        encabezados.add("Promedio variable");
        if (s.tipo() == RonaldmichaTipoBeneficio.GRATIFICACION) {
            encabezados.addAll(List.of("Gratificación", "Bonificación extraordinaria", "Total"));
        } else {
            encabezados.addAll(List.of("Gratificación", "1/6 gratificación", "Computable", "CTS", "Banco CTS",
                    "Cuenta CTS"));
        }
        List<List<Object>> datos = new ArrayList<>();
        for (FilaBeneficio f : filas) {
            RonaldmichaTrabajador t = f.trabajador();
            List<Object> fila = new ArrayList<>(List.of(t.tipoDoc().codigo() + " " + t.numDoc(), t.nombreCompleto(),
                    t.regimen().descripcion(), RonaldmichaFormatos.fecha(t.fechaIngreso()), f.meses()));
            if (s.tipo() == RonaldmichaTipoBeneficio.CTS) {
                fila.add(f.dias());
            }
            fila.addAll(List.of(f.faltas(), f.basico(), f.asignacionFamiliar()));
            s.meses().forEach(m -> fila.add(f.variables().get(m)));
            fila.add(f.promedioVariable());
            if (s.tipo() == RonaldmichaTipoBeneficio.GRATIFICACION) {
                fila.addAll(List.of(f.gratificacion(), f.bonificacion(), f.total()));
            } else {
                fila.addAll(List.of(f.gratificacion(), f.sextoGratificacion(), f.computable(), f.importe(),
                        texto(t.bancoCts()), texto(t.ctaCts())));
            }
            datos.add(fila);
        }
        Totales tot = Totales.de(s, filas);
        List<Object> total = new ArrayList<>(List.of("TOTAL", filas.size() + " trabajadores", "", "", ""));
        if (s.tipo() == RonaldmichaTipoBeneficio.CTS) {
            total.add("");
        }
        total.addAll(List.of(tot.faltas(), tot.basico(), tot.asignacionFamiliar()));
        s.meses().forEach(m -> total.add(tot.variables().get(m)));
        total.add(tot.promedioVariable());
        if (s.tipo() == RonaldmichaTipoBeneficio.GRATIFICACION) {
            total.addAll(List.of(tot.gratificacion(), tot.bonificacion(), tot.total()));
        } else {
            total.addAll(List.of(tot.gratificacion(), tot.sextoGratificacion(), tot.computable(), tot.importe(), "", ""));
        }
        datos.add(total);
        byte[] excel = RonaldmichaTablaExcel.crear(List.of(new RonaldmichaTablaExcel.Hoja(nombre(s.tipo()), titulo(s), encabezados, datos,
                true)));
        registrarExportacion(usuario, s, "planilla Excel", filas.size());
        return excel;
    }

    public byte[] boletasPdf(RonaldmichaUsuario usuario, RonaldmichaSemestreBeneficio s, Long trabajadorId) {
        List<FilaBeneficio> filas = filtrar(usuario, s, trabajadorId);
        RonaldmichaEmpresa e = empresa.obtener();
        byte[] pdf = RonaldmichaPdfRenderer.renderVarios(modelos(s, filas, e), etiqueta(s), e,
                empresa.avisoDatosIncompletos(e), LocalDateTime.now());
        registrarExportacion(usuario, s, documento(s) + " PDF", filas.size());
        return pdf;
    }

    public byte[] boletasWord(RonaldmichaUsuario usuario, RonaldmichaSemestreBeneficio s, Long trabajadorId) {
        List<FilaBeneficio> filas = filtrar(usuario, s, trabajadorId);
        RonaldmichaEmpresa e = empresa.obtener();
        byte[] word = RonaldmichaWordRenderer.renderVarios(modelos(s, filas, e), etiqueta(s), e,
                empresa.avisoDatosIncompletos(e), LocalDateTime.now());
        registrarExportacion(usuario, s, documento(s) + " Word", filas.size());
        return word;
    }

    public byte[] pagosExcel(RonaldmichaUsuario usuario, RonaldmichaSemestreBeneficio s, Long trabajadorId) {
        Map<String, List<FilaBeneficio>> porBanco = porBanco(s, filtrar(usuario, s, trabajadorId));
        List<RonaldmichaTablaExcel.Hoja> hojas = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, List<FilaBeneficio>> e : porBanco.entrySet()) {
            List<List<Object>> datos = new ArrayList<>();
            BigDecimal suma = RonaldmichaDinero.CERO;
            for (FilaBeneficio f : e.getValue()) {
                RonaldmichaTrabajador t = f.trabajador();
                datos.add(List.of(t.tipoDoc().codigo(), t.numDoc(), t.nombreCompleto(), texto(banco(s, t)),
                        texto(cuenta(s, t)), f.total()));
                suma = suma.add(f.total());
            }
            datos.add(List.of("TOTAL", "", e.getValue().size() + " pagos", "", "", suma));
            hojas.add(new RonaldmichaTablaExcel.Hoja(e.getKey(), titulo(s) + " · " + e.getKey(),
                    List.of("Tipo doc.", "Documento", "Apellidos y nombres", "Banco", "Cuenta", "Importe"), datos, true));
            total += e.getValue().size();
        }
        if (hojas.isEmpty()) {
            hojas.add(new RonaldmichaTablaExcel.Hoja("Pagos", titulo(s), List.of("Sin pagos"), List.of(), false));
        }
        byte[] excel = RonaldmichaTablaExcel.crear(hojas);
        registrarExportacion(usuario, s, "pagos por banco Excel", total);
        return excel;
    }

    public byte[] pagosTxt(RonaldmichaUsuario usuario, RonaldmichaSemestreBeneficio s, Long trabajadorId) {
        Map<String, List<FilaBeneficio>> porBanco = porBanco(s, filtrar(usuario, s, trabajadorId));
        StringBuilder sb = new StringBuilder("TIPO_DOC|DOCUMENTO|APELLIDOS Y NOMBRES|BANCO|CUENTA|IMPORTE\r\n");
        int total = 0;
        for (List<FilaBeneficio> filas : porBanco.values()) {
            for (FilaBeneficio f : filas) {
                RonaldmichaTrabajador t = f.trabajador();
                sb.append(String.join("|", t.tipoDoc().codigo(), t.numDoc(), t.nombreCompleto(), texto(banco(s, t)),
                        texto(cuenta(s, t)), f.total().toPlainString())).append("\r\n");
                total++;
            }
        }
        registrarExportacion(usuario, s, "pagos por banco TXT", total);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<FilaBeneficio> filtrar(RonaldmichaUsuario usuario, RonaldmichaSemestreBeneficio s, Long trabajadorId) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "exportar " + nombre(s.tipo()));
        Beneficio b = calcular(s);
        if (b.error().isPresent()) {
            throw new RonaldmichaValidacionException(b.error().get());
        }
        return b.filas().stream().filter(f -> trabajadorId == null || Objects.equals(f.trabajador().id(), trabajadorId))
                .toList();
    }

    private static Map<String, List<FilaBeneficio>> porBanco(RonaldmichaSemestreBeneficio s, List<FilaBeneficio> filas) {
        Map<String, List<FilaBeneficio>> mapa = new TreeMap<>((a, b) -> a.equals(SIN_CUENTA) ? (b.equals(SIN_CUENTA) ? 0 : 1)
                : b.equals(SIN_CUENTA) ? -1 : a.compareTo(b));
        for (FilaBeneficio f : filas) {
            if (f.total().signum() <= 0) {
                continue;
            }
            RonaldmichaTrabajador t = f.trabajador();
            String banco = texto(banco(s, t)).isEmpty() || texto(cuenta(s, t)).isEmpty() ? SIN_CUENTA : banco(s, t);
            mapa.computeIfAbsent(banco, k -> new ArrayList<>()).add(f);
        }
        return mapa;
    }

    private List<RonaldmichaDocumentModel> modelos(RonaldmichaSemestreBeneficio s, List<FilaBeneficio> filas, RonaldmichaEmpresa e) {
        return constancias(s, filas).stream().map(c -> RonaldmichaPlantillas.constancia(c, null, e)).toList();
    }

    List<RonaldmichaConstanciaBeneficio> constancias(RonaldmichaSemestreBeneficio s, List<FilaBeneficio> filas) {
        List<RonaldmichaConstanciaBeneficio> lista = new ArrayList<>();
        for (FilaBeneficio f : filas) {
            RonaldmichaTrabajador t = f.trabajador();
            List<Dato> datos = new ArrayList<>(List.of(
                    new Dato("Trabajador", t.nombreCompleto()),
                    new Dato("Documento", t.tipoDoc().codigo() + " " + t.numDoc()),
                    new Dato("Cargo", texto(t.cargo())),
                    new Dato("Fecha de ingreso", RonaldmichaFormatos.fecha(t.fechaIngreso())),
                    new Dato("Régimen", t.regimen().descripcion()),
                    new Dato(s.tipo() == RonaldmichaTipoBeneficio.CTS ? "Tiempo computable" : "Meses computables",
                            s.tipo() == RonaldmichaTipoBeneficio.CTS ? f.meses() + " meses y " + f.dias() + " días"
                                    : f.meses() + " meses"),
                    new Dato("Faltas", String.valueOf(f.faltas())),
                    new Dato(s.tipo() == RonaldmichaTipoBeneficio.CTS ? "Fecha de depósito" : "Fecha de pago",
                            RonaldmichaFormatos.fecha(s.fechaPago()))));
            if (s.tipo() == RonaldmichaTipoBeneficio.CTS) {
                datos.add(new Dato("Banco CTS", texto(t.bancoCts())));
                datos.add(new Dato("Cuenta CTS", texto(t.ctaCts())));
            }
            List<Linea> lineas = new ArrayList<>(List.of(
                    new Linea("Remuneración básica", f.basico(), false),
                    new Linea("Asignación familiar", f.asignacionFamiliar(), false),
                    new Linea("Promedio de remuneración variable", f.promedioVariable(), false)));
            if (s.tipo() == RonaldmichaTipoBeneficio.GRATIFICACION) {
                lineas.add(new Linea("Remuneración computable", f.computable(), true));
                lineas.add(new Linea("Gratificación (" + f.meses() + "/6" + regimenTexto(t.regimen()) + ")",
                        f.gratificacion(), false));
                lineas.add(new Linea("Bonificación extraordinaria Ley 30334 ("
                        + (t.salud() == RonaldmichaSalud.EPS ? "con EPS" : "sin EPS") + ")", f.bonificacion(), false));
                lineas.add(new Linea("Total a pagar", f.total(), true));
            } else {
                lineas.add(new Linea("1/6 de la gratificación (" + RonaldmichaFormatos.moneda(f.gratificacion()) + ")",
                        f.sextoGratificacion(), false));
                lineas.add(new Linea("Remuneración computable", f.computable(), true));
                lineas.add(new Linea("CTS a depositar" + regimenTexto(t.regimen()), f.importe(), true));
            }
            lista.add(new RonaldmichaConstanciaBeneficio(documentoTitulo(s), "Periodo " + periodoTexto(s), datos, lineas,
                    s.tipo() == RonaldmichaTipoBeneficio.GRATIFICACION ? NOTA_GRATIFICACION : NOTA_CTS));
        }
        return lista;
    }

    public static String etiqueta(RonaldmichaSemestreBeneficio s) {
        return s.tipo().descripcion() + " de " + mesLargo(YearMonth.of(s.anio(), s.mesPago())) + " " + s.anio();
    }

    public static String periodoTexto(RonaldmichaSemestreBeneficio s) {
        YearMonth d = s.desde();
        YearMonth h = s.hasta();
        String desde = mesLargo(d) + (d.getYear() == h.getYear() ? "" : " " + d.getYear());
        return desde + "–" + mesLargo(h) + " " + h.getYear();
    }

    public static String mesCorto(YearMonth m) {
        String n = m.getMonth().getDisplayName(TextStyle.SHORT_STANDALONE, RonaldmichaFormatos.LOCALE_PE).replace(".", "");
        return n.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + n.substring(1);
    }

    private static String mesLargo(YearMonth m) {
        String n = m.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, RonaldmichaFormatos.LOCALE_PE);
        return n.toLowerCase(RonaldmichaFormatos.LOCALE_PE);
    }

    private static String titulo(RonaldmichaSemestreBeneficio s) {
        return etiqueta(s) + " · periodo " + periodoTexto(s);
    }

    private static String documento(RonaldmichaSemestreBeneficio s) {
        return s.tipo() == RonaldmichaTipoBeneficio.GRATIFICACION ? "boletas" : "liquidaciones de CTS";
    }

    private static String documentoTitulo(RonaldmichaSemestreBeneficio s) {
        String mes = mesLargo(YearMonth.of(s.anio(), s.mesPago())) + " " + s.anio();
        return s.tipo() == RonaldmichaTipoBeneficio.GRATIFICACION ? "Boleta de pago de gratificación · " + mes
                : "Liquidación del depósito de CTS · " + mes;
    }

    private static String regimenTexto(RonaldmichaRegimenLaboral r) {
        return switch (r) {
            case GENERAL -> "";
            case PEQUENA -> " · pequeña empresa 50 %";
            case MICRO -> " · microempresa: no corresponde";
        };
    }

    private static String nombre(RonaldmichaTipoBeneficio tipo) {
        return tipo == RonaldmichaTipoBeneficio.GRATIFICACION ? "gratificaciones" : "CTS";
    }

    private static BigDecimal bonificacionPct(RonaldmichaTrabajador t, RonaldmichaParametrosVersion p) {
        return t.salud() == RonaldmichaSalud.EPS ? p.bonifExtEpsPct() : p.bonifExtPct();
    }

    private static String banco(RonaldmichaSemestreBeneficio s, RonaldmichaTrabajador t) {
        return s.tipo() == RonaldmichaTipoBeneficio.CTS ? t.bancoCts() : t.bancoSueldo();
    }

    private static String cuenta(RonaldmichaSemestreBeneficio s, RonaldmichaTrabajador t) {
        return s.tipo() == RonaldmichaTipoBeneficio.CTS ? t.ctaCts() : t.ctaSueldo();
    }

    private void registrarExportacion(RonaldmichaUsuario usuario, RonaldmichaSemestreBeneficio s, String reporte, int trabajadores) {
        auditoria.registrar(usuario.usuario(), "BENEFICIO_EXPORTADO", etiqueta(s) + " · " + reporte + " · "
                + trabajadores + (trabajadores == 1 ? " trabajador" : " trabajadores"));
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? "" : valor;
    }

    private static String describir(RonaldmichaTrabajador t) {
        return t.codigo() + " · " + t.tipoDoc().codigo() + " " + t.numDoc() + " · " + t.nombreCompleto();
    }
}
