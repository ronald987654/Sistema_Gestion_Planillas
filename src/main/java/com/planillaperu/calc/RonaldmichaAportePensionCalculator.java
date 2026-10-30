package com.planillaperu.calc;

import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.domain.RonaldmichaAfpComision;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaSistemaPension;
import com.planillaperu.domain.RonaldmichaTipoComision;

import java.math.BigDecimal;

public final class RonaldmichaAportePensionCalculator {

    public static final String CODIGO_ONP = "0607";
    public static final String CODIGO_AFP_APORTE = "0608";
    public static final String CODIGO_AFP_PRIMA = "0606";
    public static final String CODIGO_AFP_COMISION = "0601";

    public record AportePension(
            RonaldmichaSistemaPension sistema,
            RonaldmichaTipoComision tipoComision,
            BigDecimal asignacionFamiliar,
            BigDecimal remuneracion,
            BigDecimal tasaAporte,
            BigDecimal aporte,
            BigDecimal tasaPrima,
            BigDecimal basePrima,
            BigDecimal topePrima,
            BigDecimal prima,
            BigDecimal tasaComision,
            BigDecimal comision,
            BigDecimal total) {

        public boolean primaTopada() {
            return topePrima != null && remuneracion.compareTo(topePrima) > 0;
        }
    }

    private RonaldmichaAportePensionCalculator() {
    }

    public static BigDecimal asignacionFamiliar(RonaldmichaParametrosVersion p) {
        return RonaldmichaDinero.porcentaje(p.rmv(), p.asigFamiliarPct());
    }

    public static AportePension calcular(RonaldmichaParametrosVersion p, RonaldmichaSistemaPension sistema, RonaldmichaTipoComision tipoComision,
                                         BigDecimal sueldo, boolean conAsignacionFamiliar) {
        BigDecimal asignacion = conAsignacionFamiliar ? asignacionFamiliar(p) : RonaldmichaDinero.CERO;
        BigDecimal remuneracion = RonaldmichaDinero.sumar(sueldo, asignacion);
        return aportes(p, sistema, tipoComision, asignacion, remuneracion);
    }

    public static AportePension sobreBase(RonaldmichaParametrosVersion p, RonaldmichaSistemaPension sistema, RonaldmichaTipoComision tipoComision,
                                          BigDecimal base) {
        return aportes(p, sistema, tipoComision, RonaldmichaDinero.CERO, RonaldmichaDinero.redondear(base));
    }

    private static AportePension aportes(RonaldmichaParametrosVersion p, RonaldmichaSistemaPension sistema, RonaldmichaTipoComision tipoComision,
                                         BigDecimal asignacion, BigDecimal remuneracion) {
        BigDecimal cero = RonaldmichaDinero.CERO;

        if (sistema == RonaldmichaSistemaPension.ONP) {
            BigDecimal aporte = RonaldmichaDinero.porcentaje(remuneracion, p.onpPct());
            return new AportePension(sistema, null, asignacion, remuneracion, p.onpPct(), aporte,
                    null, cero, null, cero, null, cero, aporte);
        }

        BigDecimal aporte = RonaldmichaDinero.porcentaje(remuneracion, p.afpAportePct());
        BigDecimal basePrima = remuneracion.min(p.rmaPrima());
        BigDecimal prima = RonaldmichaDinero.porcentaje(basePrima, p.afpPrimaPct());
        RonaldmichaAfpComision comisiones = p.comision(sistema.nombre())
                .orElseThrow(() -> new IllegalArgumentException("Sin comisiones registradas para " + sistema.nombre()));
        BigDecimal tasaComision;
        BigDecimal comision;
        if (tipoComision == RonaldmichaTipoComision.MIXTA) {
            tasaComision = comisiones.comisionMixtaPct();
            comision = cero;
        } else {
            tasaComision = comisiones.comisionFlujoPct();
            comision = RonaldmichaDinero.porcentaje(remuneracion, tasaComision);
        }
        return new AportePension(sistema, tipoComision, asignacion, remuneracion, p.afpAportePct(), aporte,
                p.afpPrimaPct(), RonaldmichaDinero.redondear(basePrima), RonaldmichaDinero.redondear(p.rmaPrima()), prima,
                tasaComision, comision, RonaldmichaDinero.sumar(aporte, prima, comision));
    }
}
