package com.planillaperu.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RonaldmichaHoras {

    private static final Pattern HORA = Pattern.compile("(\\d{1,2})[:.h](\\d{2})(?::(\\d{2}))?(am|pm)?");
    private static final Pattern FRACCION = Pattern.compile("0?[.,]\\d+|0|1");
    private static final List<DateTimeFormatter> FECHAS = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
            estricto("d/M/uuuu"), estricto("d-M-uuuu"), estricto("uuuu/M/d"));
    private static final LocalDate BASE_EXCEL = LocalDate.of(1899, 12, 30);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private RonaldmichaHoras() {
    }

    private static DateTimeFormatter estricto(String patron) {
        return DateTimeFormatter.ofPattern(patron).withResolverStyle(ResolverStyle.STRICT);
    }

    public static LocalTime leer(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String v = texto.trim().toLowerCase(Locale.ROOT);
        if (FRACCION.matcher(v).matches()) {
            long minutos = new BigDecimal(v.replace(',', '.')).multiply(BigDecimal.valueOf(24 * 60))
                    .setScale(0, RoundingMode.HALF_UP).longValue();
            if (minutos >= 24 * 60) {
                throw new IllegalArgumentException("hora inválida «" + texto.trim() + "»");
            }
            return LocalTime.MIDNIGHT.plusMinutes(minutos);
        }
        String compacto = v.replace(" ", "").replace(" ", "").replace("a.m.", "am").replace("p.m.", "pm")
                .replace("a.m", "am").replace("p.m", "pm");
        Matcher m = HORA.matcher(compacto);
        if (!m.matches()) {
            throw new IllegalArgumentException("hora inválida «" + texto.trim() + "»");
        }
        int hora = Integer.parseInt(m.group(1));
        int minuto = Integer.parseInt(m.group(2));
        String sufijo = m.group(4);
        if (sufijo != null) {
            if (hora < 1 || hora > 12) {
                throw new IllegalArgumentException("hora inválida «" + texto.trim() + "»");
            }
            hora = hora % 12 + ("pm".equals(sufijo) ? 12 : 0);
        }
        if (hora > 23 || minuto > 59) {
            throw new IllegalArgumentException("hora inválida «" + texto.trim() + "»");
        }
        return LocalTime.of(hora, minuto);
    }

    public static String texto(LocalTime hora) {
        return hora == null ? "" : HH_MM.format(hora);
    }

    public static LocalDate leerFecha(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("falta la fecha");
        }
        String v = texto.trim();
        if (v.matches("\\d{5}(\\.0+)?")) {
            return BASE_EXCEL.plusDays(Long.parseLong(v.replaceAll("\\..*", "")));
        }
        if (v.length() > 10 && v.charAt(10) == 'T' || v.length() > 10 && v.charAt(10) == ' ') {
            v = v.substring(0, 10);
        }
        for (DateTimeFormatter f : FECHAS) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignorada) {
            }
        }
        throw new IllegalArgumentException("fecha inválida «" + texto.trim() + "» (use AAAA-MM-DD)");
    }

    public static String duracion(int minutos) {
        return (minutos < 0 ? "-" : "") + Math.abs(minutos) / 60 + ":" + String.format("%02d", Math.abs(minutos) % 60);
    }

    public static BigDecimal enHoras(int minutos) {
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
