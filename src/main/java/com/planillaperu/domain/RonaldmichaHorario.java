package com.planillaperu.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public record RonaldmichaHorario(
        LocalTime horaIngreso,
        LocalTime horaSalida,
        int refrigerioMin,
        int toleranciaMin,
        Set<DayOfWeek> diasLaborables,
        boolean noFiscalizado) {

    public RonaldmichaHorario {
        diasLaborables = Set.copyOf(diasLaborables);
    }

    public static Set<DayOfWeek> leerDias(String texto) {
        Set<DayOfWeek> dias = new TreeSet<>();
        if (texto == null || texto.isBlank()) {
            return dias;
        }
        for (String parte : texto.split("[,;\\s]+")) {
            if (!parte.isBlank()) {
                dias.add(DayOfWeek.of(Integer.parseInt(parte.trim())));
            }
        }
        return dias;
    }

    public String diasComoTexto() {
        return new TreeSet<>(diasLaborables).stream()
                .map(d -> String.valueOf(d.getValue()))
                .collect(Collectors.joining(","));
    }
}
