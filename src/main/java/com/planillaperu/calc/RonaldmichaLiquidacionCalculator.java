package com.planillaperu.calc;

import com.planillaperu.calc.RonaldmichaAportePensionCalculator.AportePension;
import com.planillaperu.calc.RonaldmichaVacacionesCalculator.Tiempo;
import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.common.RonaldmichaNumeroALetras;
import com.planillaperu.domain.RonaldmichaMotivoCese;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaSistemaPension;
import com.planillaperu.domain.RonaldmichaTipoComision;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class RonaldmichaLiquidacionCalculator {

    private static final int ESCALA_INTERMEDIA = 6;
    private static final BigDecimal TREINTA = BigDecimal.valueOf(30);
    private static final BigDecimal TRESCIENTOS_SESENTA = BigDecimal.valueOf(360);

    public record Entrada(
            LocalDate ingreso,
            LocalDate cese,
            RonaldmichaMotivoCese motivo,
            RonaldmichaRegimenLaboral regimen,
            BigDecimal basico,
            BigDecimal asignacionFamiliar,
            int diasVacacionesPendientes,
            int faltasGratificacion,
            BigDecimal ultimaGratificacion,
            int faltasCts,
            BigDecimal bonificacionPct,
            RonaldmichaParametrosVersion parametros,
            RonaldmichaSistemaPension sistemaPension,
            RonaldmichaTipoComision tipoComision,
            BigDecimal otrosIngresos,
            BigDecimal otrosDescuentos) {
    }

    public record Resultado(
            Tiempo tiempoServicios,
            BigDecimal computable,
            BigDecimal asignacionFamiliar,
            int truncoMeses,
            int truncoDias,
            BigDecimal vacacionesTruncas,
            int diasPendientes,
            BigDecimal vacacionesPendientes,
            int diasIndemnizacionVacacional,
            BigDecimal indemnizacionVacacional,
            BigDecimal subtotalVacaciones,
            YearMonth gratificacionDesde,
            int gratificacionMeses,
            int faltasGratificacion,
            BigDecimal gratificacion,
            BigDecimal bonificacion,
            BigDecimal subtotalGratificacion,
            YearMonth ctsDesde,
            int ctsMeses,
            int ctsDias,
            int faltasCts,
            BigDecimal ultimaGratificacion,
            BigDecimal sextoGratificacion,
            BigDecimal computableCts,
            BigDecimal cts,
            boolean indemnizable,
            BigDecimal indemnizacion,
            BigDecimal otrosIngresos,
            AportePension pension,
            BigDecimal otrosDescuentos,
            BigDecimal subtotalOtros,
            BigDecimal bruto,
            BigDecimal descuentos,
            BigDecimal neto,
            String netoEnLetras) {
    }

    private RonaldmichaLiquidacionCalculator() {
    }

    public static Resultado calcular(Entrada e) {
        Tiempo tiempo = RonaldmichaVacacionesCalculator.tiempo(e.ingreso(), null, e.cese());
        BigDecimal af = RonaldmichaDinero.redondear(e.asignacionFamiliar());
        BigDecimal computable = RonaldmichaDinero.sumar(e.basico(), af);

        BigDecimal truncas = vacacionesTruncas(computable, tiempo.meses(), tiempo.dias(), e.regimen());
        int pendientes = Math.max(0, e.diasVacacionesPendientes());
        BigDecimal vacPendientes = vacacionesNoGozadas(computable, pendientes);
        int diasVencidos = Math.max(0, pendientes - e.regimen().diasVacacionesAnuales());
        BigDecimal indemnizacionVacacional = vacacionesNoGozadas(computable, diasVencidos);
        BigDecimal remuneracionVacacional = RonaldmichaDinero.sumar(truncas, vacPendientes);
        BigDecimal subtotalVacaciones = RonaldmichaDinero.sumar(remuneracionVacacional, indemnizacionVacacional);

        YearMonth mesCese = YearMonth.from(e.cese());
        YearMonth gratiDesde = YearMonth.of(mesCese.getYear(), mesCese.getMonthValue() <= 6 ? 1 : 7);
        List<YearMonth> mesesGrati = meses(gratiDesde, mesCese);
        int mesesCompletos = RonaldmichaGratificacionCalculator.mesesCompletos(e.ingreso(), e.cese(), mesesGrati);
        RonaldmichaGratificacionCalculator.Resultado grati = RonaldmichaGratificacionCalculator.calcular(e.basico(), af, List.of(),
                mesesCompletos, e.faltasGratificacion(), e.regimen(), e.bonificacionPct());

        YearMonth ctsDesde = inicioPeriodoCts(mesCese);
        RonaldmichaCtsCalculator.Tiempo tiempoCts = RonaldmichaCtsCalculator.tiempo(e.ingreso(), e.cese(), meses(ctsDesde, mesCese));
        BigDecimal ultimaGrati = e.ultimaGratificacion() == null ? RonaldmichaDinero.CERO : e.ultimaGratificacion();
        RonaldmichaCtsCalculator.Resultado cts = RonaldmichaCtsCalculator.calcular(e.basico(), af, List.of(), tiempoCts, e.faltasCts(),
                ultimaGrati, e.regimen());

        boolean indemnizable = e.motivo() != null && e.motivo().indemnizable();
        BigDecimal indemnizacion = indemnizable ? indemnizacion(computable, tiempo, e.regimen()) : RonaldmichaDinero.CERO;

        AportePension pension = RonaldmichaAportePensionCalculator.sobreBase(e.parametros(), e.sistemaPension(), e.tipoComision(),
                remuneracionVacacional);
        BigDecimal otrosIngresos = RonaldmichaDinero.redondear(nulo(e.otrosIngresos()));
        BigDecimal otrosDescuentos = RonaldmichaDinero.redondear(nulo(e.otrosDescuentos()));
        BigDecimal descuentos = RonaldmichaDinero.sumar(pension.total(), otrosDescuentos);
        BigDecimal subtotalOtros = RonaldmichaDinero.redondear(RonaldmichaDinero.sumar(indemnizacion, otrosIngresos).subtract(descuentos));

        BigDecimal bruto = RonaldmichaDinero.sumar(subtotalVacaciones, grati.total(), cts.cts(), indemnizacion, otrosIngresos);
        BigDecimal neto = RonaldmichaDinero.redondear(bruto.subtract(descuentos));
        return new Resultado(tiempo, computable, af,
                tiempo.meses(), tiempo.dias(), truncas, pendientes, vacPendientes, diasVencidos,
                indemnizacionVacacional, subtotalVacaciones,
                gratiDesde, mesesCompletos, e.faltasGratificacion(), grati.gratificacion(), grati.bonificacion(),
                grati.total(),
                ctsDesde, tiempoCts.meses(), tiempoCts.dias(), e.faltasCts(), cts.gratificacion(),
                cts.sextoGratificacion(), cts.computable(), cts.cts(),
                indemnizable, indemnizacion, otrosIngresos, pension, otrosDescuentos, subtotalOtros,
                bruto, descuentos, neto, RonaldmichaNumeroALetras.texto(neto));
    }

    public static BigDecimal vacacionesTruncas(BigDecimal computable, int meses, int dias, RonaldmichaRegimenLaboral regimen) {
        BigDecimal porMeses = RonaldmichaDinero.redondear(computable.multiply(BigDecimal.valueOf(meses))
                .divide(BigDecimal.valueOf(12), ESCALA_INTERMEDIA, RoundingMode.HALF_UP));
        BigDecimal porDias = RonaldmichaDinero.redondear(computable.multiply(BigDecimal.valueOf(dias))
                .divide(TRESCIENTOS_SESENTA, ESCALA_INTERMEDIA, RoundingMode.HALF_UP));
        BigDecimal factor = BigDecimal.valueOf(regimen.diasVacacionesAnuales())
                .divide(TREINTA, ESCALA_INTERMEDIA, RoundingMode.HALF_UP);
        return RonaldmichaDinero.redondear(porMeses.add(porDias).multiply(factor));
    }

    public static BigDecimal vacacionesNoGozadas(BigDecimal computable, int dias) {
        return RonaldmichaDinero.redondear(computable.multiply(BigDecimal.valueOf(Math.max(0, dias)))
                .divide(TREINTA, ESCALA_INTERMEDIA, RoundingMode.HALF_UP));
    }

    public static BigDecimal indemnizacion(BigDecimal remuneracion, Tiempo tiempo, RonaldmichaRegimenLaboral regimen) {
        int diasPorAnio = switch (regimen) {
            case GENERAL -> 45;
            case PEQUENA -> 20;
            case MICRO -> 10;
        };
        int tope = switch (regimen) {
            case GENERAL -> 360;
            case PEQUENA -> 120;
            case MICRO -> 90;
        };
        int diasServicio = tiempo.anios() * 360 + tiempo.meses() * 30 + tiempo.dias();
        BigDecimal diasIndemnizacion = BigDecimal.valueOf((long) diasPorAnio * diasServicio)
                .divide(TRESCIENTOS_SESENTA, ESCALA_INTERMEDIA, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(tope));
        return RonaldmichaDinero.redondear(remuneracion.multiply(diasIndemnizacion)
                .divide(TREINTA, ESCALA_INTERMEDIA, RoundingMode.HALF_UP));
    }

    public static YearMonth inicioPeriodoCts(YearMonth mes) {
        int m = mes.getMonthValue();
        if (m >= 5 && m <= 10) {
            return YearMonth.of(mes.getYear(), 5);
        }
        return YearMonth.of(m >= 11 ? mes.getYear() : mes.getYear() - 1, 11);
    }

    private static List<YearMonth> meses(YearMonth desde, YearMonth hasta) {
        List<YearMonth> lista = new ArrayList<>();
        for (YearMonth m = desde; !m.isAfter(hasta); m = m.plusMonths(1)) {
            lista.add(m);
        }
        return lista;
    }

    private static BigDecimal nulo(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }
}
