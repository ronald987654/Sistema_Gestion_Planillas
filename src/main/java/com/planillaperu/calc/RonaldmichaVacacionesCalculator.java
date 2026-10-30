package com.planillaperu.calc;

import com.planillaperu.domain.RonaldmichaEstadoVacacion;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaTipoVacacion;
import com.planillaperu.domain.RonaldmichaVacacion;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class RonaldmichaVacacionesCalculator {

    public static final int VENTA_MAXIMA_ANUAL = 15;
    public static final int PERIODOS_INDEMNIZACION = 2;

    public record Tiempo(int anios, int meses, int dias) {

        public int mesesCumplidos() {
            return anios * 12 + meses;
        }
    }

    public record Resumen(
            Tiempo tiempo,
            int diasAnuales,
            int derecho,
            int gozadas,
            int vendidas,
            int pendiente,
            int planificado) {

        public int mesesCumplidos() {
            return tiempo.mesesCumplidos();
        }

        public int aniosCompletos() {
            return tiempo.anios();
        }

        public int truncoMeses() {
            return tiempo.meses();
        }

        public int truncoDias() {
            return tiempo.dias();
        }

        public int periodosSinGozar() {
            return diasAnuales == 0 ? 0 : Math.max(0, pendiente) / diasAnuales;
        }

        public boolean alertaIndemnizacion() {
            return periodosSinGozar() >= PERIODOS_INDEMNIZACION;
        }

        public int ventaDisponible() {
            return Math.max(0, VENTA_MAXIMA_ANUAL * tiempo.anios() - vendidas);
        }
    }

    private RonaldmichaVacacionesCalculator() {
    }

    public static LocalDate corte(LocalDate cese, LocalDate hoy) {
        return cese != null && cese.isBefore(hoy) ? cese : hoy;
    }

    public static Tiempo tiempo(LocalDate ingreso, LocalDate cese, LocalDate hoy) {
        LocalDate fin = corte(cese, hoy);
        if (ingreso == null || ingreso.isAfter(fin)) {
            return new Tiempo(0, 0, 0);
        }
        Period p = Period.between(ingreso, fin.plusDays(1));
        return new Tiempo(p.getYears(), p.getMonths(), p.getDays());
    }

    public static int diasCalendario(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(desde, hasta) + 1;
    }

    public static RonaldmichaEstadoVacacion estadoEfectivo(RonaldmichaVacacion v, LocalDate hoy) {
        if (v.estado() == RonaldmichaEstadoVacacion.PROGRAMADO && v.desde() != null && !v.desde().isAfter(hoy)) {
            return RonaldmichaEstadoVacacion.GOZADO;
        }
        return v.estado();
    }

    public static Resumen resumir(LocalDate ingreso, LocalDate cese, RonaldmichaRegimenLaboral regimen, List<RonaldmichaVacacion> registros,
                                  LocalDate hoy) {
        Tiempo tiempo = tiempo(ingreso, cese, hoy);
        int diasAnuales = regimen.diasVacacionesAnuales();
        int derecho = tiempo.anios() * diasAnuales;
        int gozadas = 0;
        int vendidas = 0;
        int planificado = 0;
        for (RonaldmichaVacacion v : registros) {
            if (!v.estado().cuenta()) {
                continue;
            }
            if (v.tipo() == RonaldmichaTipoVacacion.VENTA) {
                vendidas += v.dias();
            } else if (v.desde() == null || !v.desde().isAfter(hoy)) {
                int yaGozados = v.hasta() == null || !v.hasta().isAfter(hoy) ? v.dias()
                        : Math.min(v.dias(), diasCalendario(v.desde(), hoy));
                gozadas += yaGozados;
                planificado += v.dias() - yaGozados;
            } else {
                planificado += v.dias();
            }
        }
        return new Resumen(tiempo, diasAnuales, derecho, gozadas, vendidas, derecho - gozadas - vendidas, planificado);
    }
}
