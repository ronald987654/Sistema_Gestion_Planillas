package com.planillaperu.calc;

import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaTrabajadorRemuneracion;

import java.math.BigDecimal;
import java.util.List;

public final class RonaldmichaImpactoParametrosCalculator {

    public record Impacto(
            RonaldmichaParametrosVersion anterior,
            List<RonaldmichaTrabajadorRemuneracion> bajoNuevaRmv,
            BigDecimal asignacionAnterior,
            BigDecimal asignacionNueva,
            int conAsignacion,
            BigDecimal diferenciaMensual) {
    }

    private RonaldmichaImpactoParametrosCalculator() {
    }

    public static Impacto calcular(RonaldmichaParametrosVersion anterior, RonaldmichaParametrosVersion nueva,
                                   List<RonaldmichaTrabajadorRemuneracion> trabajadores) {
        List<RonaldmichaTrabajadorRemuneracion> bajoRmv = trabajadores.stream()
                .filter(t -> t.sueldo().compareTo(nueva.rmv()) < 0)
                .toList();
        BigDecimal asigAnterior = anterior == null
                ? RonaldmichaDinero.CERO : RonaldmichaDinero.porcentaje(anterior.rmv(), anterior.asigFamiliarPct());
        BigDecimal asigNueva = RonaldmichaDinero.porcentaje(nueva.rmv(), nueva.asigFamiliarPct());
        int conAsignacion = (int) trabajadores.stream().filter(RonaldmichaTrabajadorRemuneracion::asigFamiliar).count();
        BigDecimal diferencia = RonaldmichaDinero.redondear(
                asigNueva.subtract(asigAnterior).multiply(BigDecimal.valueOf(conAsignacion)));
        return new Impacto(anterior, bajoRmv, asigAnterior, asigNueva, conAsignacion, diferencia);
    }
}
