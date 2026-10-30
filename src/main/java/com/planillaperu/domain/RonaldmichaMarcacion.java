package com.planillaperu.domain;

import java.time.LocalDate;
import java.time.LocalTime;

public record RonaldmichaMarcacion(
        long trabajadorId,
        LocalDate fecha,
        LocalTime ingreso,
        LocalTime salida,
        RonaldmichaNovedad novedad,
        boolean heAutorizada,
        RonaldmichaTratamientoHe heTratamiento,
        RonaldmichaOrigenAsistencia origen) {

    public RonaldmichaMarcacion {
        novedad = novedad == null ? RonaldmichaNovedad.NORMAL : novedad;
        heTratamiento = heTratamiento == null ? RonaldmichaTratamientoHe.PAGAR : heTratamiento;
        origen = origen == null ? RonaldmichaOrigenAsistencia.SISTEMA : origen;
    }

    public static RonaldmichaMarcacion vacia(long trabajadorId, LocalDate fecha) {
        return new RonaldmichaMarcacion(trabajadorId, fecha, null, null, RonaldmichaNovedad.NORMAL, false, RonaldmichaTratamientoHe.PAGAR,
                RonaldmichaOrigenAsistencia.SISTEMA);
    }

    public boolean tieneMarcacion() {
        return ingreso != null || salida != null;
    }

    public boolean completa() {
        return ingreso != null && salida != null;
    }

    public boolean vacia() {
        return !tieneMarcacion() && novedad == RonaldmichaNovedad.NORMAL && !heAutorizada && heTratamiento == RonaldmichaTratamientoHe.PAGAR;
    }

    public RonaldmichaMarcacion conHoras(LocalTime nuevoIngreso, LocalTime nuevaSalida) {
        return new RonaldmichaMarcacion(trabajadorId, fecha, nuevoIngreso, nuevaSalida, novedad, heAutorizada, heTratamiento, origen);
    }

    public RonaldmichaMarcacion conNovedad(RonaldmichaNovedad nueva) {
        return new RonaldmichaMarcacion(trabajadorId, fecha, ingreso, salida, nueva, heAutorizada, heTratamiento, origen);
    }

    public RonaldmichaMarcacion conHorasExtra(boolean autorizada, RonaldmichaTratamientoHe tratamiento) {
        return new RonaldmichaMarcacion(trabajadorId, fecha, ingreso, salida, novedad, autorizada, tratamiento, origen);
    }

    public RonaldmichaMarcacion conOrigen(RonaldmichaOrigenAsistencia nuevo) {
        return new RonaldmichaMarcacion(trabajadorId, fecha, ingreso, salida, novedad, heAutorizada, heTratamiento, nuevo);
    }
}
