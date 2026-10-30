package com.planillaperu.calc;

import com.planillaperu.calc.RonaldmichaVacacionesCalculator.Resumen;
import com.planillaperu.calc.RonaldmichaVacacionesCalculator.Tiempo;
import com.planillaperu.domain.RonaldmichaEstadoVacacion;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaTipoVacacion;
import com.planillaperu.domain.RonaldmichaVacacion;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaVacacionesCalculatorTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 15);
    private static final LocalDate INGRESO = LocalDate.of(2019, 3, 1);

    @Test
    void tiempoDeServicio() {
        assertThat(RonaldmichaVacacionesCalculator.tiempo(INGRESO, null, HOY)).isEqualTo(new Tiempo(7, 7, 15));
        assertThat(RonaldmichaVacacionesCalculator.tiempo(INGRESO, null, HOY).mesesCumplidos()).isEqualTo(91);
        assertThat(RonaldmichaVacacionesCalculator.tiempo(INGRESO, null, LocalDate.of(2020, 2, 29)).anios()).isEqualTo(1);
        assertThat(RonaldmichaVacacionesCalculator.tiempo(INGRESO, null, LocalDate.of(2020, 2, 28)).anios()).isZero();
        assertThat(RonaldmichaVacacionesCalculator.tiempo(INGRESO, LocalDate.of(2021, 2, 28), HOY)).isEqualTo(new Tiempo(2, 0, 0));
        assertThat(RonaldmichaVacacionesCalculator.tiempo(LocalDate.of(2027, 1, 1), null, HOY)).isEqualTo(new Tiempo(0, 0, 0));
    }

    @Test
    void derechoPorRegimen() {
        Resumen general = RonaldmichaVacacionesCalculator.resumir(INGRESO, null, RonaldmichaRegimenLaboral.GENERAL, List.of(), HOY);
        Resumen pequena = RonaldmichaVacacionesCalculator.resumir(INGRESO, null, RonaldmichaRegimenLaboral.PEQUENA, List.of(), HOY);
        Resumen micro = RonaldmichaVacacionesCalculator.resumir(INGRESO, null, RonaldmichaRegimenLaboral.MICRO, List.of(), HOY);

        assertThat(general.derecho()).isEqualTo(210);
        assertThat(pequena.derecho()).isEqualTo(105);
        assertThat(micro.derecho()).isEqualTo(105);
        assertThat(general.pendiente()).isEqualTo(210);
        assertThat(general.truncoMeses()).isEqualTo(7);
        assertThat(general.truncoDias()).isEqualTo(15);
    }

    @Test
    void pendienteEsDerechoMenosGozadasYVendidas() {
        List<RonaldmichaVacacion> registros = List.of(
                goce("2024-07-01", "2024-07-30", RonaldmichaEstadoVacacion.GOZADO),
                venta(15, RonaldmichaEstadoVacacion.PROGRAMADO),
                goce("2026-12-16", "2026-12-30", RonaldmichaEstadoVacacion.PROGRAMADO),
                goce("2025-01-06", "2025-01-20", RonaldmichaEstadoVacacion.ANULADO),
                goce("2025-02-03", "2025-02-17", RonaldmichaEstadoVacacion.RECHAZADO),
                goce("2027-01-04", "2027-01-13", RonaldmichaEstadoVacacion.PENDIENTE));

        Resumen r = RonaldmichaVacacionesCalculator.resumir(INGRESO, null, RonaldmichaRegimenLaboral.GENERAL, registros, HOY);

        assertThat(r.gozadas()).isEqualTo(30);
        assertThat(r.vendidas()).isEqualTo(15);
        assertThat(r.planificado()).isEqualTo(15);
        assertThat(r.pendiente()).isEqualTo(210 - 30 - 15);
        assertThat(r.ventaDisponible()).isEqualTo(7 * 15 - 15);
    }

    @Test
    void goceEnCursoSeRepartenEntreGozadasYPlanificadas() {
        Resumen r = RonaldmichaVacacionesCalculator.resumir(INGRESO, null, RonaldmichaRegimenLaboral.GENERAL,
                List.of(goce("2026-10-10", "2026-10-24", RonaldmichaEstadoVacacion.PROGRAMADO)), HOY);

        assertThat(r.gozadas()).isEqualTo(6);
        assertThat(r.planificado()).isEqualTo(9);
        assertThat(RonaldmichaVacacionesCalculator.estadoEfectivo(goce("2026-10-10", "2026-10-24", RonaldmichaEstadoVacacion.PROGRAMADO), HOY))
                .isEqualTo(RonaldmichaEstadoVacacion.GOZADO);
        assertThat(RonaldmichaVacacionesCalculator.estadoEfectivo(goce("2026-12-16", "2026-12-30", RonaldmichaEstadoVacacion.PROGRAMADO), HOY))
                .isEqualTo(RonaldmichaEstadoVacacion.PROGRAMADO);
    }

    @Test
    void alertaConDosPeriodosSinGozar() {
        LocalDate ingreso = LocalDate.of(2023, 5, 2);
        Resumen tresPeriodos = RonaldmichaVacacionesCalculator.resumir(ingreso, null, RonaldmichaRegimenLaboral.GENERAL, List.of(), HOY);
        Resumen unPeriodo = RonaldmichaVacacionesCalculator.resumir(ingreso, null, RonaldmichaRegimenLaboral.GENERAL,
                List.of(goce("2024-05-06", "2024-06-04", RonaldmichaEstadoVacacion.GOZADO)), HOY);

        assertThat(tresPeriodos.derecho()).isEqualTo(90);
        assertThat(tresPeriodos.periodosSinGozar()).isEqualTo(3);
        assertThat(tresPeriodos.alertaIndemnizacion()).isTrue();
        assertThat(unPeriodo.periodosSinGozar()).isEqualTo(2);
        assertThat(unPeriodo.alertaIndemnizacion()).isTrue();
        Resumen alDia = RonaldmichaVacacionesCalculator.resumir(ingreso, null, RonaldmichaRegimenLaboral.GENERAL, List.of(
                goce("2024-05-06", "2024-06-04", RonaldmichaEstadoVacacion.GOZADO),
                goce("2025-05-05", "2025-06-03", RonaldmichaEstadoVacacion.GOZADO)), HOY);
        assertThat(alDia.alertaIndemnizacion()).isFalse();
    }

    @Test
    void diasCalendarioInclusive() {
        assertThat(RonaldmichaVacacionesCalculator.diasCalendario(LocalDate.of(2026, 12, 16), LocalDate.of(2026, 12, 30)))
                .isEqualTo(15);
        assertThat(RonaldmichaVacacionesCalculator.diasCalendario(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 1)))
                .isEqualTo(30);
        assertThat(RonaldmichaVacacionesCalculator.diasCalendario(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1))).isZero();
    }

    private static RonaldmichaVacacion goce(String desde, String hasta, RonaldmichaEstadoVacacion estado) {
        LocalDate d = LocalDate.parse(desde);
        LocalDate h = LocalDate.parse(hasta);
        return new RonaldmichaVacacion(null, 1, RonaldmichaTipoVacacion.GOCE, d, h, RonaldmichaVacacionesCalculator.diasCalendario(d, h), null, estado,
                LocalDateTime.of(2024, 1, 1, 10, 0), "admin", null, false);
    }

    private static RonaldmichaVacacion venta(int dias, RonaldmichaEstadoVacacion estado) {
        LocalDate d = LocalDate.of(2025, 4, 2);
        return new RonaldmichaVacacion(null, 1, RonaldmichaTipoVacacion.VENTA, d, d.plusDays(dias - 1L), dias, null, estado,
                LocalDateTime.of(2025, 2, 3, 10, 0), "admin", null, true);
    }
}
