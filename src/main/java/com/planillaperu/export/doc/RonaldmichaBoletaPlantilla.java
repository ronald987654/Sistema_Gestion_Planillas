package com.planillaperu.export.doc;

import com.planillaperu.calc.RonaldmichaAportePensionCalculator;
import com.planillaperu.calc.RonaldmichaAportePensionCalculator.AportePension;
import com.planillaperu.calc.RonaldmichaPlanillaCalculator;
import com.planillaperu.calc.RonaldmichaRentaQuintaCalculator;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla;
import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.common.RonaldmichaNumeroALetras;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaSalud;
import com.planillaperu.domain.RonaldmichaSistemaPension;
import com.planillaperu.domain.RonaldmichaTipoComision;
import com.planillaperu.domain.RonaldmichaTipoDocumento;
import com.planillaperu.domain.RonaldmichaTipoPago;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Boleta;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Campo;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Conceptos;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.FilaDatos;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.FirmasBoleta;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.GrupoDatos;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.LineaBoleta;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Orientacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;

public final class RonaldmichaBoletaPlantilla {

    public static final String COD_FALTAS = "0705";
    public static final String COD_JUDICIAL = "0703";

    private static final DateTimeFormatter AAAAMM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final BigDecimal TREINTA = BigDecimal.valueOf(30);
    private static final String SIN_DATO = "—";

    public record Entrada(RonaldmichaTrabajador trabajador, RonaldmichaResultadoPlanilla resultado, BigDecimal horasOrdinarias,
                          int minutosExtra, RonaldmichaEmpresa empresa, RonaldmichaParametrosVersion parametros, YearMonth mes,
                          String otroEmpleador, BigDecimal importeOtro, LocalDate inicioVacaciones,
                          LocalDate finVacaciones, String numeroLiquidacion) {
    }

    private RonaldmichaBoletaPlantilla() {
    }

    public static String numero(YearMonth mes, String codigo) {
        return "BOL-" + AAAAMM.format(mes) + "-" + codigo;
    }

    public static String mes(YearMonth mes) {
        return ("MES DE " + mes.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, RonaldmichaFormatos.LOCALE_PE) + " "
                + mes.getYear()).toUpperCase(RonaldmichaFormatos.LOCALE_PE);
    }

    public static LocalDate fechaPago(RonaldmichaEmpresa e, YearMonth mes) {
        return e.diaPago() == null || e.diaPago() > mes.lengthOfMonth() ? mes.atEndOfMonth() : mes.atDay(e.diaPago());
    }

    public static List<String> faltantes(RonaldmichaEmpresa e) {
        List<String> faltan = new ArrayList<>();
        if (vacio(e.ruc())) faltan.add("RUC");
        if (vacio(e.razonSocial())) faltan.add("razón social");
        if (vacio(e.rubro())) faltan.add("rubro de la empresa");
        if (vacio(e.direccion())) faltan.add("dirección");
        if (vacio(e.distrito())) faltan.add("distrito");
        if (vacio(e.repNombre())) faltan.add("nombre del representante legal");
        if (vacio(e.repDoc())) faltan.add("documento del representante legal");
        return faltan;
    }

    public static RonaldmichaDocumentModel modelo(Entrada in) {
        RonaldmichaTrabajador t = in.trabajador();
        RonaldmichaResultadoPlanilla r = in.resultado();
        RonaldmichaEmpresa e = in.empresa();

        int diasComputables = r.diasLaborados() + r.inasistencias();
        BigDecimal computable = RonaldmichaDinero.redondear(t.sueldo().multiply(BigDecimal.valueOf(diasComputables))
                .divide(TREINTA, 6, RoundingMode.HALF_UP));
        BigDecimal faltas = computable.subtract(r.basico()).max(RonaldmichaDinero.CERO);
        BigDecimal afectosBrutos = RonaldmichaDinero.sumar(r.basico(), r.remuneracionVacacional(), r.asignacionFamiliar(),
                r.horasExtra25(), r.horasExtra35(), r.feriadoTrabajado(), r.comisiones(), r.bonificacion(),
                r.ingresosExtraAfectos());
        BigDecimal tardanza = afectosBrutos.subtract(r.remuneracionAfecta()).max(RonaldmichaDinero.CERO);

        Conceptos remuneraciones = new Conceptos("REMUNERACIONES", false, remuneraciones(r, computable));
        Conceptos descuentos = new Conceptos("RETENCIONES / DESCUENTOS", true, descuentos(t, r, faltas, tardanza));
        Conceptos aportes = new Conceptos("APORTACIONES DEL EMPLEADOR", true, aportes(t, r, in.parametros()));
        BigDecimal totalRem = suma(remuneraciones);
        BigDecimal totalDesc = suma(descuentos);
        BigDecimal neto = totalRem.subtract(totalDesc);
        if (neto.compareTo(r.netoAPagar()) != 0) {
            throw new IllegalStateException("El neto de la boleta (" + neto + ") no coincide con el de la planilla ("
                    + r.netoAPagar() + ") de " + t.codigo());
        }

        Boleta b = new Boleta(numero(in.mes(), t.codigo()), mes(in.mes()), RonaldmichaFormatos.fecha(fechaPago(e, in.mes())),
                datos(in, computable), remuneraciones, descuentos, aportes, totalRem, totalDesc, neto,
                RonaldmichaNumeroALetras.texto(neto), leyendaCese(t, in.mes(), in.numeroLiquidacion()), firmas(e, t));
        return new RonaldmichaDocumentModel(Boleta.TITULO, null, RonaldmichaDocumentModel.DOS_COPIAS, Orientacion.HORIZONTAL,
                List.of(b), null);
    }

    private static List<FilaDatos> datos(Entrada in, BigDecimal computable) {
        RonaldmichaTrabajador t = in.trabajador();
        RonaldmichaResultadoPlanilla r = in.resultado();
        RonaldmichaEmpresa e = in.empresa();
        String direccionEmpresa = vacio(e.direccion()) ? Boleta.COMPLETAR
                : e.direccion().trim() + " - " + (vacio(e.distrito()) ? Boleta.COMPLETAR : e.distrito().trim());
        GrupoDatos empresa = new GrupoDatos("DATOS DE LA EMPRESA", List.of(
                new Campo("RUC", completar(e.ruc()), 1f),
                new Campo("RAZÓN SOCIAL", completar(e.razonSocial()), 2.2f),
                new Campo("RUBRO DE LA EMPRESA", completar(e.rubro()), 1.6f),
                new Campo("DIRECCIÓN", direccionEmpresa, 3.2f)), 1f);
        GrupoDatos trabajador = new GrupoDatos("DATOS DEL TRABAJADOR", List.of(
                new Campo("CÓDIGO", t.codigo(), 0.8f),
                new Campo("NOMBRES", t.nombres(), 1.6f),
                new Campo("APELLIDOS", t.apellidos(), 1.8f),
                new Campo(etiquetaDocumento(t.tipoDoc()), t.numDoc(), 1f),
                new Campo("F. NAC.", fecha(t.fechaNacimiento()), 0.9f),
                new Campo("HIJOS", String.valueOf(t.hijos()), 0.6f),
                new Campo("DIRECCIÓN", dato(t.direccion()), 2.8f)), 1f);
        boolean afp = t.sistemaPension().esAfp();
        GrupoDatos laboral = new GrupoDatos("DATOS DEL TRABAJADOR VINCULADOS A LA RELACIÓN LABORAL", List.of(
                new Campo("CARGO", dato(t.cargo()), 1.8f),
                new Campo("CATEGORÍA", t.categoria().nombre(), 0.9f),
                new Campo("PERIODIC.", vacio(e.periodicidad()) ? "Mensual" : e.periodicidad(), 0.9f),
                new Campo("ONP", afp ? "NO" : "SI", 0.5f),
                new Campo("A.F.P.", afp ? t.sistemaPension().nombre().replaceFirst("^AFP ", "") : SIN_DATO, 0.9f),
                new Campo("C.U.S.P.P.", afp ? dato(t.cuspp()) : SIN_DATO, 1.2f),
                new Campo("F. ING.", fecha(t.fechaIngreso()), 0.9f),
                new Campo("F. CESE", fecha(t.fechaCese()), 0.9f),
                new Campo("INI. VAC.", fecha(in.inicioVacaciones()), 0.9f),
                new Campo("FIN VAC.", fecha(in.finVacaciones()), 0.9f),
                new Campo("DÍAS VAC.", String.valueOf(r.diasVacaciones()), 0.7f)), 1f);
        GrupoDatos dias = new GrupoDatos(null, List.of(
                new Campo("DÍAS LABORADOS", String.valueOf(r.diasLaborados()), 1f),
                new Campo("TOTAL HORAS LABORADAS", horas(in.horasOrdinarias()), 1.2f),
                new Campo("HORAS EXTRAS", horas(BigDecimal.valueOf(in.minutosExtra())
                        .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP)), 0.9f),
                new Campo("DÍAS NO LABORADOS", String.valueOf(r.inasistencias() + r.diasSubsidiados()), 1.1f)), 4.2f);
        GrupoDatos otro = new GrupoDatos(null, List.of(
                new Campo("OTRO EMPLEADOR", in.otroEmpleador() == null ? "NO" : in.otroEmpleador(), 1.4f),
                new Campo("IMPORTE REMUN.", RonaldmichaFormatos.numero(in.importeOtro() == null ? RonaldmichaDinero.CERO : in.importeOtro()),
                        1f)), 2.4f);
        GrupoDatos cuenta = new GrupoDatos(null, List.of(new Campo("CTA. DE DEPÓSITO", cuenta(t), 1f)), 2.4f);
        return List.of(new FilaDatos(List.of(empresa)), new FilaDatos(List.of(trabajador)),
                new FilaDatos(List.of(laboral)), new FilaDatos(List.of(dias, otro, cuenta)));
    }

    private static List<LineaBoleta> remuneraciones(RonaldmichaResultadoPlanilla r, BigDecimal computable) {
        List<LineaBoleta> l = new ArrayList<>();
        l.add(new LineaBoleta(RonaldmichaPlanillaCalculator.COD_BASICO, "Sueldo Computable", computable, null));
        l.add(new LineaBoleta(RonaldmichaPlanillaCalculator.COD_ASIG_FAMILIAR, "Asignación Familiar", r.asignacionFamiliar(), null));
        l.add(new LineaBoleta(RonaldmichaPlanillaCalculator.COD_VACACIONAL, "Remuner. Vacacional", r.remuneracionVacacional(), null));
        l.add(new LineaBoleta(RonaldmichaPlanillaCalculator.COD_MOVILIDAD, "Mov. Sup. Asistencia", r.movilidad(), null));
        siHay(l, RonaldmichaPlanillaCalculator.COD_HE_25, "HE 25%", r.horasExtra25(), null);
        siHay(l, RonaldmichaPlanillaCalculator.COD_HE_35, "HE 35%", r.horasExtra35(), null);
        siHay(l, RonaldmichaPlanillaCalculator.COD_FERIADO, "Trabajo en Feriado/Descanso", r.feriadoTrabajado(), null);
        siHay(l, RonaldmichaPlanillaCalculator.COD_COMISIONES, "Comisiones", r.comisiones(), null);
        siHay(l, RonaldmichaPlanillaCalculator.COD_BONIFICACION, "Bonificación", r.bonificacion(), null);
        siHay(l, RonaldmichaPlanillaCalculator.COD_SUBSIDIO, "Subsidio Incapacidad Temporal", r.subsidio(), null);
        siHay(l, null, "Otros ingresos", RonaldmichaDinero.sumar(r.ingresosExtraAfectos(), r.ingresosExtraNoAfectos()), null);
        return l;
    }

    private static List<LineaBoleta> descuentos(RonaldmichaTrabajador t, RonaldmichaResultadoPlanilla r, BigDecimal faltas,
                                                BigDecimal tardanza) {
        AportePension p = r.pension();
        boolean afp = t.sistemaPension().esAfp();
        boolean flujo = afp && t.tipoComision() == RonaldmichaTipoComision.FLUJO;
        List<LineaBoleta> l = new ArrayList<>();
        l.add(new LineaBoleta(RonaldmichaAportePensionCalculator.CODIGO_AFP_APORTE, "AFP Fondo", afp ? p.aporte() : RonaldmichaDinero.CERO,
                pct(afp ? p.tasaAporte() : null)));
        l.add(new LineaBoleta(RonaldmichaAportePensionCalculator.CODIGO_AFP_COMISION, "AFP Comisión", p.comision(),
                pct(flujo ? p.tasaComision() : null)));
        l.add(new LineaBoleta(RonaldmichaAportePensionCalculator.CODIGO_AFP_PRIMA, "AFP Seguro", p.prima(),
                pct(afp ? p.tasaPrima() : null)));
        l.add(new LineaBoleta(RonaldmichaAportePensionCalculator.CODIGO_ONP, "ONP", afp ? RonaldmichaDinero.CERO : p.aporte(),
                pct(t.sistemaPension() == RonaldmichaSistemaPension.ONP ? p.tasaAporte() : null)));
        l.add(new LineaBoleta(COD_FALTAS, "Faltas", faltas, null));
        l.add(new LineaBoleta(RonaldmichaRentaQuintaCalculator.CODIGO, "Retenciones 5ta", r.rentaQuinta(), null));
        l.add(new LineaBoleta(COD_JUDICIAL, "Retenciones Judiciales", RonaldmichaDinero.CERO, null));
        l.add(new LineaBoleta(RonaldmichaPlanillaCalculator.COD_ADELANTO, "Adelantos", r.adelanto(), null));
        siHay(l, RonaldmichaPlanillaCalculator.COD_TARDANZA, "Tardanzas", tardanza, null);
        siHay(l, RonaldmichaPlanillaCalculator.COD_OTROS_DESCUENTOS, "Otros descuentos",
                RonaldmichaDinero.sumar(r.otrosDescuentos(), r.descuentosExtra()), null);
        return l;
    }

    private static List<LineaBoleta> aportes(RonaldmichaTrabajador t, RonaldmichaResultadoPlanilla r, RonaldmichaParametrosVersion p) {
        List<LineaBoleta> l = new ArrayList<>();
        BigDecimal tasaSalud = switch (t.salud()) {
            case EPS -> p.epsPct();
            case ESSALUD -> p.essaludPct();
            case SIS -> null;
        };
        l.add(new LineaBoleta(RonaldmichaPlanillaCalculator.COD_ESSALUD, t.salud() == RonaldmichaSalud.EPS ? "Essalud (con EPS)" : "Essalud",
                r.essalud(), pct(tasaSalud)));
        l.add(new LineaBoleta(null, "S.C.T.R.", RonaldmichaDinero.CERO, null));
        siHay(l, null, "SIS", r.sis(), null);
        siHay(l, null, "Vida Ley", r.vidaLey(), pct(p.vidaLeyPct()));
        siHay(l, null, "Otros aportes", r.aportesExtra(), null);
        return l;
    }

    private static String leyendaCese(RonaldmichaTrabajador t, YearMonth mes, String numeroLiquidacion) {
        if (t.fechaCese() == null || !YearMonth.from(t.fechaCese()).equals(mes)) {
            return null;
        }
        return "Trabajador cesado el " + RonaldmichaFormatos.fecha(t.fechaCese()) + ". Sus beneficios sociales (CTS, vacaciones y "
                + "gratificación truncas) se pagan en la "
                + (numeroLiquidacion == null ? "liquidación de beneficios sociales." : "Liquidación N° " + numeroLiquidacion + ".");
    }

    private static FirmasBoleta firmas(RonaldmichaEmpresa e, RonaldmichaTrabajador t) {
        String tipoRep = vacio(e.repTipoDoc()) ? "DNI" : e.repTipoDoc().trim();
        boolean firmaDigital = e.firmaDigitalActiva() && e.firma() != null && e.firma().length > 0;
        return new FirmasBoleta(completar(e.repNombre()), tipoRep + " " + completar(e.repDoc()),
                vacio(e.repCargo()) ? "Representante legal" : e.repCargo().trim(),
                t.nombres() + " " + t.apellidos(), t.tipoDoc().codigo() + " " + t.numDoc(), firmaDigital,
                e.huellaEnBoleta());
    }

    private static void siHay(List<LineaBoleta> l, String codigo, String concepto, BigDecimal monto, String pct) {
        if (monto != null && monto.signum() > 0) {
            l.add(new LineaBoleta(codigo, concepto, monto, pct));
        }
    }

    private static BigDecimal suma(Conceptos c) {
        return RonaldmichaDinero.sumar(c.lineas().stream().map(LineaBoleta::monto).toArray(BigDecimal[]::new));
    }

    private static String pct(BigDecimal tasa) {
        return RonaldmichaFormatos.numero(tasa == null ? BigDecimal.ZERO : tasa) + "%";
    }

    private static String cuenta(RonaldmichaTrabajador t) {
        if (t.tipoPago() == RonaldmichaTipoPago.DEPOSITO && !vacio(t.bancoSueldo()) && !vacio(t.ctaSueldo())) {
            return t.bancoSueldo().trim() + " " + t.ctaSueldo().trim();
        }
        return t.tipoPago() == RonaldmichaTipoPago.DEPOSITO ? SIN_DATO : t.tipoPago().nombre();
    }

    private static String etiquetaDocumento(RonaldmichaTipoDocumento d) {
        return switch (d) {
            case DNI -> "D.N.I.";
            case CE -> "C.E.";
            case PAS -> "PASAPORTE";
        };
    }

    private static String horas(BigDecimal horas) {
        int minutos = horas.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValue();
        return minutos / 60 + ":" + String.format("%02d", minutos % 60);
    }

    private static String fecha(LocalDate f) {
        return f == null ? SIN_DATO : RonaldmichaFormatos.fecha(f);
    }

    private static String dato(String s) {
        return vacio(s) ? SIN_DATO : s.trim();
    }

    private static String completar(String s) {
        return vacio(s) ? Boleta.COMPLETAR : s.trim();
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
