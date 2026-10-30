package com.planillaperu.calc;

import com.planillaperu.domain.RonaldmichaEstadoParametros;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaTrabajadorRemuneracion;
import com.planillaperu.domain.RonaldmichaTrabajadorResumen;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaImpactoParametrosCalculatorTest {

    @Test
    void cuentaSueldosBajoLaNuevaRmvYDiferenciaDeAsignacion() {
        RonaldmichaParametrosVersion anterior = version("1000", "10");
        RonaldmichaParametrosVersion nueva = version("1100", "10");
        List<RonaldmichaTrabajadorRemuneracion> trabajadores = List.of(
                trabajador(1, "1050.00", true),
                trabajador(2, "1100.00", true),
                trabajador(3, "2500.00", false));

        RonaldmichaImpactoParametrosCalculator.Impacto impacto =
                RonaldmichaImpactoParametrosCalculator.calcular(anterior, nueva, trabajadores);

        assertThat(impacto.bajoNuevaRmv()).extracting(t -> t.trabajador().id()).containsExactly(1L);
        assertThat(impacto.asignacionAnterior()).isEqualTo(new BigDecimal("100.00"));
        assertThat(impacto.asignacionNueva()).isEqualTo(new BigDecimal("110.00"));
        assertThat(impacto.conAsignacion()).isEqualTo(2);
        assertThat(impacto.diferenciaMensual()).isEqualTo(new BigDecimal("20.00"));
    }

    @Test
    void sinVersionAnteriorLaAsignacionAnteriorEsCero() {
        RonaldmichaImpactoParametrosCalculator.Impacto impacto = RonaldmichaImpactoParametrosCalculator.calcular(
                null, version("1025", "10"), List.of(trabajador(1, "3000.00", true)));

        assertThat(impacto.anterior()).isNull();
        assertThat(impacto.asignacionAnterior()).isEqualTo(new BigDecimal("0.00"));
        assertThat(impacto.asignacionNueva()).isEqualTo(new BigDecimal("102.50"));
        assertThat(impacto.diferenciaMensual()).isEqualTo(new BigDecimal("102.50"));
    }

    @Test
    void redondeaHalfUpADosDecimales() {
        RonaldmichaImpactoParametrosCalculator.Impacto impacto = RonaldmichaImpactoParametrosCalculator.calcular(
                version("1234.5", "10"), version("1234.55", "10"), List.of(trabajador(1, "5000.00", true)));

        assertThat(impacto.asignacionNueva()).isEqualTo(new BigDecimal("123.46"));
        assertThat(impacto.diferenciaMensual()).isEqualTo(new BigDecimal("0.01"));
    }

    private static RonaldmichaParametrosVersion version(String rmv, String asigPct) {
        BigDecimal cero = BigDecimal.ZERO;
        return new RonaldmichaParametrosVersion(null, YearMonth.of(2030, 1), null, new BigDecimal(rmv), BigDecimal.ONE,
                new BigDecimal(asigPct), cero, cero, cero, cero, cero, BigDecimal.ONE, cero, cero, cero, cero,
                RonaldmichaEstadoParametros.VIGENTE, "prueba", null, List.of(), null, null, null);
    }

    private static RonaldmichaTrabajadorRemuneracion trabajador(long id, String sueldo, boolean asignacion) {
        RonaldmichaTrabajadorResumen t = new RonaldmichaTrabajadorResumen(id, "T" + id, "DNI", "4000000" + id, "Apellido", null,
                "Nombre", null, null, RonaldmichaRegimenLaboral.GENERAL);
        return new RonaldmichaTrabajadorRemuneracion(t, new BigDecimal(sueldo), asignacion);
    }
}
