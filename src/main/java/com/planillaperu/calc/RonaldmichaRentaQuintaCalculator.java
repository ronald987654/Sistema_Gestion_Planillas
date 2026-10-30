package com.planillaperu.calc;

import com.planillaperu.common.RonaldmichaDinero;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class RonaldmichaRentaQuintaCalculator {

    public static final String CODIGO = "0605";
    private static final BigDecimal UIT_DEDUCIBLES = BigDecimal.valueOf(7);
    private static final int MESES = 12;
    private static final int ESCALA_INTERMEDIA = 6;

    private record Tramo(BigDecimal hastaUit, BigDecimal tasa) {
    }

    private static final List<Tramo> ESCALA = List.of(
            new Tramo(BigDecimal.valueOf(5), BigDecimal.valueOf(8)),
            new Tramo(BigDecimal.valueOf(20), BigDecimal.valueOf(14)),
            new Tramo(BigDecimal.valueOf(35), BigDecimal.valueOf(17)),
            new Tramo(BigDecimal.valueOf(45), BigDecimal.valueOf(20)),
            new Tramo(null, BigDecimal.valueOf(30)));

    public record Entrada(
            int mes,
            BigDecimal remuneracionMes,
            BigDecimal extraordinarioMes,
            BigDecimal gratificacionesPorPercibir,
            BigDecimal percibidoAnterior,
            List<BigDecimal> retencionesAnteriores,
            BigDecimal otrosEmpleadores,
            BigDecimal retenidoOtrosEmpleadores,
            BigDecimal uit) {

        public Entrada {
            if (mes < 1 || mes > MESES) {
                throw new IllegalArgumentException("Mes fuera de rango: " + mes);
            }
            retencionesAnteriores = List.copyOf(retencionesAnteriores);
        }
    }

    public record Renta(
            BigDecimal rentaAnualProyectada,
            BigDecimal deduccion,
            BigDecimal rentaNeta,
            BigDecimal impuestoAnual,
            BigDecimal retenidoADeducir,
            int divisor,
            BigDecimal retencionOrdinaria,
            BigDecimal impuestoExtraordinario,
            BigDecimal retencionMensual) {

        public static final Renta CERO = new Renta(RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO,
                MESES, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO);
    }

    private RonaldmichaRentaQuintaCalculator() {
    }

    public static Renta calcular(Entrada e) {
        int mesesRestantes = MESES - e.mes() + 1;
        BigDecimal proyectada = RonaldmichaDinero.sumar(
                nulo(e.remuneracionMes()).multiply(BigDecimal.valueOf(mesesRestantes)),
                nulo(e.gratificacionesPorPercibir()), nulo(e.percibidoAnterior()), nulo(e.otrosEmpleadores()));
        BigDecimal deduccion = deduccion(e.uit());
        BigDecimal neta = proyectada.subtract(deduccion).max(RonaldmichaDinero.CERO);
        BigDecimal impuesto = impuestoAnual(neta, e.uit());

        int divisor = divisor(e.mes());
        BigDecimal retenido = e.mes() <= 3 ? RonaldmichaDinero.CERO
                : RonaldmichaDinero.sumar(retenidoHasta(e.retencionesAnteriores(), mesCorte(e.mes())),
                nulo(e.retenidoOtrosEmpleadores()));
        BigDecimal ordinaria = RonaldmichaDinero.redondear(impuesto.subtract(retenido)
                .divide(BigDecimal.valueOf(divisor), ESCALA_INTERMEDIA, RoundingMode.HALF_UP)).max(RonaldmichaDinero.CERO);

        BigDecimal extraordinario = nulo(e.extraordinarioMes());
        BigDecimal impuestoExtra = RonaldmichaDinero.CERO;
        if (extraordinario.signum() > 0) {
            BigDecimal netaConExtra = proyectada.add(extraordinario).subtract(deduccion).max(RonaldmichaDinero.CERO);
            impuestoExtra = impuestoAnual(netaConExtra, e.uit()).subtract(impuesto);
        }
        return new Renta(proyectada, deduccion, RonaldmichaDinero.redondear(neta), impuesto, retenido, divisor, ordinaria,
                impuestoExtra, RonaldmichaDinero.sumar(ordinaria, impuestoExtra));
    }

    public static int divisor(int mes) {
        if (mes <= 3) {
            return 12;
        }
        if (mes == 4) {
            return 9;
        }
        if (mes <= 7) {
            return 8;
        }
        if (mes == 8) {
            return 5;
        }
        return mes <= 11 ? 4 : 1;
    }

    private static int mesCorte(int mes) {
        if (mes == 4) {
            return 3;
        }
        if (mes <= 7) {
            return 4;
        }
        if (mes == 8) {
            return 7;
        }
        return mes <= 11 ? 8 : 11;
    }

    private static BigDecimal retenidoHasta(List<BigDecimal> retenciones, int hastaMes) {
        BigDecimal suma = RonaldmichaDinero.CERO;
        for (int i = 0; i < Math.min(hastaMes, retenciones.size()); i++) {
            suma = suma.add(nulo(retenciones.get(i)));
        }
        return RonaldmichaDinero.redondear(suma);
    }

    public static BigDecimal impuestoAnual(BigDecimal rentaNeta, BigDecimal uit) {
        return RonaldmichaDinero.redondear(impuesto(rentaNeta.max(BigDecimal.ZERO), uit));
    }

    public static BigDecimal deduccion(BigDecimal uit) {
        return RonaldmichaDinero.redondear(uit.multiply(UIT_DEDUCIBLES));
    }

    private static BigDecimal impuesto(BigDecimal neta, BigDecimal uit) {
        BigDecimal impuesto = BigDecimal.ZERO;
        BigDecimal piso = BigDecimal.ZERO;
        for (Tramo tramo : ESCALA) {
            if (neta.compareTo(piso) <= 0) {
                break;
            }
            BigDecimal techo = tramo.hastaUit() == null ? neta : uit.multiply(tramo.hastaUit()).min(neta);
            impuesto = impuesto.add(RonaldmichaDinero.redondear(techo.subtract(piso).multiply(tramo.tasa()).movePointLeft(2)));
            piso = techo;
        }
        return impuesto;
    }

    private static BigDecimal nulo(BigDecimal monto) {
        return monto == null ? RonaldmichaDinero.CERO : monto;
    }
}
