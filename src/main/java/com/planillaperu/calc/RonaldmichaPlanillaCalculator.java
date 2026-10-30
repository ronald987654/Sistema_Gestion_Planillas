package com.planillaperu.calc;

import com.planillaperu.calc.RonaldmichaAportePensionCalculator.AportePension;
import com.planillaperu.calc.RonaldmichaHorasExtraCalculator.Importes;
import com.planillaperu.calc.RonaldmichaRentaQuintaCalculator.Renta;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla.Concepto;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla.TipoConcepto;
import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.domain.RonaldmichaConceptoExtra;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaSalud;
import com.planillaperu.domain.RonaldmichaTipoConceptoExtra;
import com.planillaperu.domain.RonaldmichaTrabajador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class RonaldmichaPlanillaCalculator {

    public static final String COD_BASICO = "0121";
    public static final String COD_VACACIONAL = "0118";
    public static final String COD_ASIG_FAMILIAR = "0201";
    public static final String COD_HE_25 = "0105";
    public static final String COD_HE_35 = "0106";
    public static final String COD_FERIADO = "0107";
    public static final String COD_COMISIONES = "0103";
    public static final String COD_BONIFICACION = "0304";
    public static final String COD_SUBSIDIO = "0916";
    public static final String COD_TARDANZA = "0704";
    public static final String COD_MOVILIDAD = "0909";
    public static final String COD_ADELANTO = "0701";
    public static final String COD_OTROS_DESCUENTOS = "0706";
    public static final String COD_ESSALUD = "0804";

    private static final int DIAS_COMERCIALES = 30;
    private static final BigDecimal TREINTA = BigDecimal.valueOf(DIAS_COMERCIALES);
    private static final BigDecimal DOCE = BigDecimal.valueOf(12);
    private static final BigDecimal SESENTA = BigDecimal.valueOf(60);
    private static final int ESCALA_INTERMEDIA = 6;

    public record Novedades(
            int inasistencias,
            int vacaciones,
            int diasSubsidiados,
            BigDecimal comisiones,
            BigDecimal bonificacion,
            BigDecimal adelanto,
            BigDecimal otrosDescuentos) {

        public static final Novedades NINGUNA = new Novedades(0, 0, 0, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO,
                RonaldmichaDinero.CERO);
    }

    public record Asistencia(
            BigDecimal horasDiarias,
            int tardanzaMin,
            int he25Min,
            int he35Min,
            int diasConSobretasa) {

        public static Asistencia sinNovedades(BigDecimal horasDiarias) {
            return new Asistencia(horasDiarias, 0, 0, 0, 0);
        }
    }

    public record HistorialRenta(
            BigDecimal percibidoAnterior,
            List<BigDecimal> retencionesAnteriores,
            BigDecimal gratificacionesPorPercibir,
            BigDecimal otrosEmpleadores,
            BigDecimal retenidoOtrosEmpleadores,
            BigDecimal retencionGuardada) {

        public static final HistorialRenta SIN_HISTORIA = new HistorialRenta(RonaldmichaDinero.CERO, List.of(), null, RonaldmichaDinero.CERO,
                RonaldmichaDinero.CERO, null);

        public HistorialRenta {
            retencionesAnteriores = List.copyOf(retencionesAnteriores);
        }

        public HistorialRenta conRetencionGuardada(BigDecimal retencion) {
            return new HistorialRenta(percibidoAnterior, retencionesAnteriores, gratificacionesPorPercibir,
                    otrosEmpleadores, retenidoOtrosEmpleadores, retencion);
        }
    }

    private RonaldmichaPlanillaCalculator() {
    }

    public static RonaldmichaResultadoPlanilla calcular(RonaldmichaTrabajador t, YearMonth periodo, RonaldmichaParametrosVersion p, Novedades n,
                                             Asistencia a, List<RonaldmichaConceptoExtra> conceptosExtra) {
        return calcular(t, periodo, p, n, a, conceptosExtra, HistorialRenta.SIN_HISTORIA);
    }

    public static RonaldmichaResultadoPlanilla calcular(RonaldmichaTrabajador t, YearMonth periodo, RonaldmichaParametrosVersion p, Novedades n,
                                             Asistencia a, List<RonaldmichaConceptoExtra> conceptosExtra, HistorialRenta historial) {
        int diasMes = diasDelMes(t.fechaIngreso(), t.fechaCese(), periodo);
        int inasistencias = Math.min(n.inasistencias(), diasMes);
        int vacaciones = Math.min(n.vacaciones(), diasMes - inasistencias);
        int subsidiados = Math.min(n.diasSubsidiados(), diasMes - inasistencias - vacaciones);
        int diasLab = diasMes - inasistencias - vacaciones - subsidiados;

        BigDecimal sueldo = t.sueldo();
        BigDecimal afCompleta = t.asigFamiliar() ? RonaldmichaAportePensionCalculator.asignacionFamiliar(p) : RonaldmichaDinero.CERO;
        BigDecimal asignacion = RonaldmichaDinero.redondear(proporcional(afCompleta, diasMes));
        BigDecimal basico = RonaldmichaDinero.redondear(proporcional(sueldo, diasLab));
        BigDecimal vacacional = RonaldmichaDinero.redondear(proporcional(sueldo, vacaciones));
        BigDecimal remComputable = sueldo.add(afCompleta);

        Importes he = RonaldmichaHorasExtraCalculator.calcular(remComputable, a.horasDiarias(), a.he25Min(), a.he35Min(),
                a.diasConSobretasa());
        BigDecimal valorHora = a.horasDiarias().signum() <= 0 ? BigDecimal.ZERO
                : remComputable.divide(TREINTA, ESCALA_INTERMEDIA, RoundingMode.HALF_UP)
                .divide(a.horasDiarias(), ESCALA_INTERMEDIA, RoundingMode.HALF_UP);
        BigDecimal tardanza = RonaldmichaDinero.redondear(valorHora.multiply(BigDecimal.valueOf(a.tardanzaMin()))
                .divide(SESENTA, ESCALA_INTERMEDIA, RoundingMode.HALF_UP));

        BigDecimal subsidio = RonaldmichaDinero.redondear(proporcional(remComputable, subsidiados));
        BigDecimal movilidad = RonaldmichaDinero.redondear(proporcional(nulo(t.movilidad()), diasLab));

        BigDecimal ingExtra = sumaExtra(conceptosExtra, RonaldmichaTipoConceptoExtra.ING);
        BigDecimal ingNrExtra = sumaExtra(conceptosExtra, RonaldmichaTipoConceptoExtra.ING_NR);
        BigDecimal desExtra = sumaExtra(conceptosExtra, RonaldmichaTipoConceptoExtra.DES);
        BigDecimal apoExtra = sumaExtra(conceptosExtra, RonaldmichaTipoConceptoExtra.APO);

        BigDecimal comisiones = RonaldmichaDinero.redondear(n.comisiones());
        BigDecimal bonificacion = RonaldmichaDinero.redondear(n.bonificacion());
        BigDecimal afecta = RonaldmichaDinero.sumar(basico, vacacional, asignacion, he.he25(), he.he35(), he.descansos(),
                comisiones, bonificacion, ingExtra).subtract(tardanza).max(RonaldmichaDinero.CERO);
        BigDecimal basePension = RonaldmichaDinero.sumar(afecta, subsidio);

        AportePension pension = RonaldmichaAportePensionCalculator.sobreBase(p, t.sistemaPension(), t.tipoComision(), basePension);

        BigDecimal bonifPct = t.salud() == RonaldmichaSalud.EPS ? p.bonifExtEpsPct() : p.bonifExtPct();
        RonaldmichaRegimenLaboral regimen = t.regimen();
        BigDecimal baseRenta = basePension;
        BigDecimal extraordinario = bonificacion.min(baseRenta);
        Renta renta = renta(t, periodo, p, historial, baseRenta.subtract(extraordinario), extraordinario,
                remComputable, bonifPct);

        BigDecimal essalud = RonaldmichaDinero.CERO;
        BigDecimal sis = RonaldmichaDinero.CERO;
        if (t.salud() == RonaldmichaSalud.SIS) {
            sis = diasMes > 0 ? RonaldmichaDinero.redondear(p.sisMonto()) : RonaldmichaDinero.CERO;
        } else {
            BigDecimal minimo = proporcional(p.rmv(), diasMes - subsidiados);
            BigDecimal tasa = t.salud() == RonaldmichaSalud.EPS ? p.epsPct() : p.essaludPct();
            essalud = RonaldmichaDinero.porcentaje(afecta.max(minimo), tasa);
        }
        BigDecimal vidaLey = t.vidaLey() ? RonaldmichaDinero.porcentaje(afecta, p.vidaLeyPct()) : RonaldmichaDinero.CERO;

        BigDecimal adelanto = RonaldmichaDinero.redondear(n.adelanto());
        BigDecimal otros = RonaldmichaDinero.redondear(n.otrosDescuentos());
        BigDecimal ingresos = RonaldmichaDinero.sumar(afecta, movilidad, subsidio, ingNrExtra);
        BigDecimal descuentos = RonaldmichaDinero.sumar(pension.total(), renta.retencionMensual(), adelanto, otros, desExtra);
        BigDecimal aportes = RonaldmichaDinero.sumar(essalud, sis, vidaLey, apoExtra);
        Provisiones prov = provisiones(regimen, remComputable, bonifPct, diasMes);
        BigDecimal costo = RonaldmichaDinero.sumar(ingresos, aportes, prov.gratificacion(), prov.cts(), prov.vacaciones());

        List<Concepto> conceptos = new ArrayList<>();
        conceptos.add(new Concepto(COD_BASICO, "Remuneración básica", TipoConcepto.INGRESO, basico));
        agregar(conceptos, COD_VACACIONAL, "Remuneración vacacional", TipoConcepto.INGRESO, vacacional);
        agregar(conceptos, COD_ASIG_FAMILIAR, "Asignación familiar", TipoConcepto.INGRESO, asignacion);
        agregar(conceptos, COD_HE_25, "Horas extra 25 %", TipoConcepto.INGRESO, he.he25());
        agregar(conceptos, COD_HE_35, "Horas extra 35 %", TipoConcepto.INGRESO, he.he35());
        agregar(conceptos, COD_FERIADO, "Trabajo en feriado o día de descanso", TipoConcepto.INGRESO, he.descansos());
        agregar(conceptos, COD_COMISIONES, "Comisiones", TipoConcepto.INGRESO, comisiones);
        agregar(conceptos, COD_BONIFICACION, "Bonificación", TipoConcepto.INGRESO, bonificacion);
        agregarExtras(conceptos, conceptosExtra, RonaldmichaTipoConceptoExtra.ING, TipoConcepto.INGRESO);
        agregar(conceptos, COD_SUBSIDIO, "Subsidio por incapacidad temporal", TipoConcepto.INGRESO, subsidio);
        agregar(conceptos, COD_MOVILIDAD, "Movilidad supeditada a la asistencia", TipoConcepto.INGRESO, movilidad);
        agregarExtras(conceptos, conceptosExtra, RonaldmichaTipoConceptoExtra.ING_NR, TipoConcepto.INGRESO);
        agregar(conceptos, COD_TARDANZA, "Tardanzas", TipoConcepto.REBAJA, tardanza);
        if (t.sistemaPension().esAfp()) {
            agregar(conceptos, RonaldmichaAportePensionCalculator.CODIGO_AFP_APORTE, "AFP aporte obligatorio",
                    TipoConcepto.DESCUENTO, pension.aporte());
            agregar(conceptos, RonaldmichaAportePensionCalculator.CODIGO_AFP_PRIMA, "AFP prima de seguro",
                    TipoConcepto.DESCUENTO, pension.prima());
            agregar(conceptos, RonaldmichaAportePensionCalculator.CODIGO_AFP_COMISION, "AFP comisión sobre la remuneración",
                    TipoConcepto.DESCUENTO, pension.comision());
        } else {
            agregar(conceptos, RonaldmichaAportePensionCalculator.CODIGO_ONP, "Sistema Nacional de Pensiones (ONP)",
                    TipoConcepto.DESCUENTO, pension.aporte());
        }
        agregar(conceptos, RonaldmichaRentaQuintaCalculator.CODIGO, "Renta de quinta categoría", TipoConcepto.DESCUENTO,
                renta.retencionMensual());
        agregar(conceptos, COD_ADELANTO, "Adelanto", TipoConcepto.DESCUENTO, adelanto);
        agregar(conceptos, COD_OTROS_DESCUENTOS, "Otros descuentos", TipoConcepto.DESCUENTO, otros);
        agregarExtras(conceptos, conceptosExtra, RonaldmichaTipoConceptoExtra.DES, TipoConcepto.DESCUENTO);
        agregar(conceptos, COD_ESSALUD, t.salud() == RonaldmichaSalud.EPS ? "EsSalud (con EPS)" : "EsSalud",
                TipoConcepto.APORTE, essalud);
        agregar(conceptos, null, "SIS", TipoConcepto.APORTE, sis);
        agregar(conceptos, null, "Seguro Vida Ley", TipoConcepto.APORTE, vidaLey);
        agregarExtras(conceptos, conceptosExtra, RonaldmichaTipoConceptoExtra.APO, TipoConcepto.APORTE);

        return new RonaldmichaResultadoPlanilla(t.id() == null ? 0 : t.id(), periodo, diasMes, diasLab, inasistencias,
                vacaciones, subsidiados, he.remuneracionDiaria(), he.valorHora(), basico, vacacional,
                asignacion, he.he25(), he.he35(), he.descansos(), comisiones, bonificacion, ingExtra, tardanza,
                afecta, subsidio, basePension, movilidad, ingNrExtra, pension, baseRenta, extraordinario, renta,
                renta.rentaAnualProyectada(), renta.retencionMensual(), adelanto, otros, desExtra, essalud, sis, vidaLey, apoExtra,
                prov.gratificacion(), prov.cts(), prov.vacaciones(), ingresos, descuentos,
                ingresos.subtract(descuentos), aportes, costo, conceptos);
    }

    private static Renta renta(RonaldmichaTrabajador t, YearMonth periodo, RonaldmichaParametrosVersion p, HistorialRenta h,
                               BigDecimal remuneracion, BigDecimal extraordinario, BigDecimal remComputable,
                               BigDecimal bonifPct) {
        BigDecimal gratificaciones = h.gratificacionesPorPercibir();
        if (gratificaciones == null) {
            int pagos = periodo.getMonthValue() <= 7 ? 2 : 1;
            gratificaciones = RonaldmichaDinero.redondear(remComputable.multiply(t.regimen().factorGratificacion())
                    .multiply(BigDecimal.ONE.add(bonifPct.movePointLeft(2))).multiply(BigDecimal.valueOf(pagos)));
        }
        Renta r = RonaldmichaRentaQuintaCalculator.calcular(new RonaldmichaRentaQuintaCalculator.Entrada(periodo.getMonthValue(),
                remuneracion, extraordinario, gratificaciones, h.percibidoAnterior(), h.retencionesAnteriores(),
                h.otrosEmpleadores(), h.retenidoOtrosEmpleadores(), p.uit()));
        if (h.retencionGuardada() == null) {
            return r;
        }
        return new Renta(r.rentaAnualProyectada(), r.deduccion(), r.rentaNeta(), r.impuestoAnual(),
                r.retenidoADeducir(), r.divisor(), r.retencionOrdinaria(), r.impuestoExtraordinario(),
                RonaldmichaDinero.redondear(h.retencionGuardada()));
    }

    public static int diasDelMes(LocalDate ingreso, LocalDate cese, YearMonth periodo) {
        if (ingreso != null && ingreso.isAfter(periodo.atEndOfMonth())
                || cese != null && cese.isBefore(periodo.atDay(1))) {
            return 0;
        }
        int desde = ingreso != null && YearMonth.from(ingreso).equals(periodo)
                ? Math.min(ingreso.getDayOfMonth(), DIAS_COMERCIALES) : 1;
        int hasta = DIAS_COMERCIALES;
        if (cese != null && YearMonth.from(cese).equals(periodo) && !cese.equals(periodo.atEndOfMonth())) {
            hasta = Math.min(cese.getDayOfMonth(), DIAS_COMERCIALES);
        }
        return Math.max(0, hasta - desde + 1);
    }

    private record Provisiones(BigDecimal gratificacion, BigDecimal cts, BigDecimal vacaciones) {
    }

    private static Provisiones provisiones(RonaldmichaRegimenLaboral r, BigDecimal remComputable, BigDecimal bonifPct,
                                           int diasMes) {
        BigDecimal fraccionMes = BigDecimal.valueOf(diasMes).divide(TREINTA, ESCALA_INTERMEDIA, RoundingMode.HALF_UP);
        BigDecimal sexto = remComputable.multiply(BigDecimal.valueOf(r.gratificacionesAnuales()))
                .divide(DOCE, ESCALA_INTERMEDIA, RoundingMode.HALF_UP);
        BigDecimal grat = sexto.multiply(BigDecimal.ONE.add(bonifPct.movePointLeft(2)));
        BigDecimal cts = remComputable.add(sexto).multiply(BigDecimal.valueOf(r.diasCtsAnuales()))
                .divide(TREINTA.multiply(DOCE), ESCALA_INTERMEDIA, RoundingMode.HALF_UP);
        BigDecimal vac = remComputable.multiply(BigDecimal.valueOf(r.diasVacacionesAnuales()))
                .divide(TREINTA.multiply(DOCE), ESCALA_INTERMEDIA, RoundingMode.HALF_UP);
        return new Provisiones(RonaldmichaDinero.redondear(grat.multiply(fraccionMes)),
                RonaldmichaDinero.redondear(cts.multiply(fraccionMes)), RonaldmichaDinero.redondear(vac.multiply(fraccionMes)));
    }

    private static BigDecimal proporcional(BigDecimal monto, int dias) {
        return monto.multiply(BigDecimal.valueOf(Math.max(0, dias))).divide(TREINTA, ESCALA_INTERMEDIA,
                RoundingMode.HALF_UP);
    }

    private static BigDecimal nulo(BigDecimal monto) {
        return monto == null ? RonaldmichaDinero.CERO : monto;
    }

    private static BigDecimal sumaExtra(List<RonaldmichaConceptoExtra> extras, RonaldmichaTipoConceptoExtra tipo) {
        return RonaldmichaDinero.sumar(extras.stream().filter(c -> c.tipo() == tipo).map(RonaldmichaConceptoExtra::monto)
                .toArray(BigDecimal[]::new));
    }

    private static void agregar(List<Concepto> conceptos, String codigo, String descripcion, TipoConcepto tipo,
                                BigDecimal monto) {
        if (monto.signum() != 0) {
            conceptos.add(new Concepto(codigo, descripcion, tipo, monto));
        }
    }

    private static void agregarExtras(List<Concepto> conceptos, List<RonaldmichaConceptoExtra> extras, RonaldmichaTipoConceptoExtra origen,
                                      TipoConcepto tipo) {
        for (RonaldmichaConceptoExtra c : extras) {
            if (c.tipo() == origen) {
                agregar(conceptos, c.codigoPlame(), c.descripcion(), tipo, RonaldmichaDinero.redondear(c.monto()));
            }
        }
    }
}
