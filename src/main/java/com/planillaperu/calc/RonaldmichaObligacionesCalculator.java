package com.planillaperu.calc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RonaldmichaObligacionesCalculator {

    public static final int PRIMER_DIA_HABIL_PLAME = 9;
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es-PE"));
    private static final List<MonthDay> CTS = List.of(MonthDay.of(5, 15), MonthDay.of(11, 15));
    private static final List<MonthDay> GRATIFICACION = List.of(MonthDay.of(7, 15), MonthDay.of(12, 15));

    public enum Tipo { AFPNET, PLAME, CTS, GRATIFICACION }

    public record Obligacion(Tipo tipo, LocalDate desde, LocalDate fecha, String titulo, String detalle) {

        public long diasRestantes(LocalDate hoy) {
            return ChronoUnit.DAYS.between(hoy, fecha);
        }
    }

    private RonaldmichaObligacionesCalculator() {
    }

    public static List<Obligacion> proximas(LocalDate hoy, String ruc, Map<Integer, Map<LocalDate, String>> feriados,
                                            int maximo) {
        List<Obligacion> lista = new ArrayList<>();
        YearMonth actual = YearMonth.from(hoy);
        for (int i = 0; i <= 2; i++) {
            YearMonth mes = actual.plusMonths(i);
            String periodo = MES.format(mes.minusMonths(1));
            lista.add(new Obligacion(Tipo.AFPNET, mes.atDay(1), mes.atDay(5), "AFPnet",
                    "Declaración y pago de aportes del periodo " + periodo));
            Integer digito = ultimoDigito(ruc);
            if (digito != null) {
                lista.add(new Obligacion(Tipo.PLAME, null, vencimientoPlame(mes, digito, feriados), "PLAME",
                        "Periodo " + periodo + " · RUC terminado en " + digito + " (fecha referencial)"));
            }
        }
        for (int anio = hoy.getYear(); anio <= hoy.getYear() + 1; anio++) {
            for (MonthDay d : CTS) {
                lista.add(new Obligacion(Tipo.CTS, null, d.atYear(anio), "Depósito de CTS",
                        d.getMonthValue() == 5 ? "Periodo noviembre–abril" : "Periodo mayo–octubre"));
            }
            for (MonthDay d : GRATIFICACION) {
                lista.add(new Obligacion(Tipo.GRATIFICACION, null, d.atYear(anio), "Pago de gratificación",
                        d.getMonthValue() == 7 ? "Fiestas Patrias" : "Navidad"));
            }
        }
        return lista.stream()
                .filter(o -> !o.fecha().isBefore(hoy))
                .sorted(Comparator.comparing(Obligacion::fecha))
                .limit(maximo)
                .toList();
    }

    public static LocalDate vencimientoPlame(YearMonth mes, int digito, Map<Integer, Map<LocalDate, String>> feriados) {
        int grupo = switch (digito) {
            case 0 -> 0;
            case 1 -> 1;
            case 2, 3 -> 2;
            case 4, 5 -> 3;
            case 6, 7 -> 4;
            default -> 5;
        };
        int buscado = PRIMER_DIA_HABIL_PLAME + grupo;
        Map<LocalDate, String> delAnio = feriados.getOrDefault(mes.getYear(), Map.of());
        int habiles = 0;
        for (LocalDate d = mes.atDay(1); !d.isAfter(mes.atEndOfMonth()); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !delAnio.containsKey(d)) {
                habiles++;
                if (habiles == buscado) {
                    return d;
                }
            }
        }
        return mes.atEndOfMonth();
    }

    private static Integer ultimoDigito(String ruc) {
        if (ruc == null || ruc.isBlank()) {
            return null;
        }
        char c = ruc.trim().charAt(ruc.trim().length() - 1);
        return Character.isDigit(c) ? c - '0' : null;
    }
}
