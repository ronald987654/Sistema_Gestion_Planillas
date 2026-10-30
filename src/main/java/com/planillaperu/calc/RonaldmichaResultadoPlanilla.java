package com.planillaperu.calc;

import com.planillaperu.calc.RonaldmichaAportePensionCalculator.AportePension;
import com.planillaperu.calc.RonaldmichaRentaQuintaCalculator.Renta;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record RonaldmichaResultadoPlanilla(
        long trabajadorId,
        YearMonth periodo,
        int diasMes,
        int diasLaborados,
        int inasistencias,
        int diasVacaciones,
        int diasSubsidiados,
        BigDecimal remuneracionDiaria,
        BigDecimal valorHora,
        BigDecimal basico,
        BigDecimal remuneracionVacacional,
        BigDecimal asignacionFamiliar,
        BigDecimal horasExtra25,
        BigDecimal horasExtra35,
        BigDecimal feriadoTrabajado,
        BigDecimal comisiones,
        BigDecimal bonificacion,
        BigDecimal ingresosExtraAfectos,
        BigDecimal tardanza,
        BigDecimal remuneracionAfecta,
        BigDecimal subsidio,
        BigDecimal basePension,
        BigDecimal movilidad,
        BigDecimal ingresosExtraNoAfectos,
        AportePension pension,
        BigDecimal baseRenta,
        BigDecimal pagoExtraordinario,
        Renta renta,
        BigDecimal rentaAnualProyectada,
        BigDecimal rentaQuinta,
        BigDecimal adelanto,
        BigDecimal otrosDescuentos,
        BigDecimal descuentosExtra,
        BigDecimal essalud,
        BigDecimal sis,
        BigDecimal vidaLey,
        BigDecimal aportesExtra,
        BigDecimal provisionGratificacion,
        BigDecimal provisionCts,
        BigDecimal provisionVacaciones,
        BigDecimal totalIngresos,
        BigDecimal totalDescuentos,
        BigDecimal netoAPagar,
        BigDecimal totalAportes,
        BigDecimal costoLaboral,
        List<Concepto> conceptos) {

    public enum TipoConcepto {
        INGRESO,
        REBAJA,
        DESCUENTO,
        APORTE
    }

    public record Concepto(String codigoPlame, String descripcion, TipoConcepto tipo, BigDecimal monto) {
    }

    public RonaldmichaResultadoPlanilla {
        conceptos = List.copyOf(conceptos);
    }

    public BigDecimal totalDe(TipoConcepto tipo) {
        return conceptos.stream().filter(c -> c.tipo() == tipo).map(Concepto::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
