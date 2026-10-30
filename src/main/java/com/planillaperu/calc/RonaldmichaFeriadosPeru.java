package com.planillaperu.calc;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class RonaldmichaFeriadosPeru {

    private static final Map<MonthDay, String> FIJOS = new LinkedHashMap<>();

    static {
        FIJOS.put(MonthDay.of(1, 1), "Año Nuevo");
        FIJOS.put(MonthDay.of(5, 1), "Día del Trabajo");
        FIJOS.put(MonthDay.of(6, 7), "Batalla de Arica y Día de la Bandera");
        FIJOS.put(MonthDay.of(6, 29), "San Pedro y San Pablo");
        FIJOS.put(MonthDay.of(7, 23), "Día de la Fuerza Aérea del Perú");
        FIJOS.put(MonthDay.of(7, 28), "Fiestas Patrias");
        FIJOS.put(MonthDay.of(7, 29), "Fiestas Patrias");
        FIJOS.put(MonthDay.of(8, 6), "Batalla de Junín");
        FIJOS.put(MonthDay.of(8, 30), "Santa Rosa de Lima");
        FIJOS.put(MonthDay.of(10, 8), "Combate de Angamos");
        FIJOS.put(MonthDay.of(11, 1), "Día de Todos los Santos");
        FIJOS.put(MonthDay.of(12, 8), "Inmaculada Concepción");
        FIJOS.put(MonthDay.of(12, 9), "Batalla de Ayacucho");
        FIJOS.put(MonthDay.of(12, 25), "Navidad");
    }

    private RonaldmichaFeriadosPeru() {
    }

    public static LocalDate pascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(anio, mes, dia);
    }

    public static Map<LocalDate, String> nacionales(int anio) {
        Map<LocalDate, String> feriados = new TreeMap<>();
        FIJOS.forEach((md, nombre) -> feriados.put(md.atYear(anio), nombre));
        LocalDate pascua = pascua(anio);
        feriados.put(pascua.minusDays(3), "Jueves Santo");
        feriados.put(pascua.minusDays(2), "Viernes Santo");
        return Collections.unmodifiableMap(feriados);
    }

    public static Map<LocalDate, String> combinar(int anio, Map<LocalDate, String> agregados) {
        Map<LocalDate, String> todos = new TreeMap<>(nacionales(anio));
        agregados.forEach((fecha, nombre) -> {
            if (fecha.getYear() == anio) {
                todos.putIfAbsent(fecha, nombre);
            }
        });
        return Collections.unmodifiableMap(todos);
    }

    public static Map<LocalDate, String> delMes(YearMonth mes, Map<LocalDate, String> agregados) {
        Map<LocalDate, String> delMes = new TreeMap<>();
        combinar(mes.getYear(), agregados).forEach((fecha, nombre) -> {
            if (YearMonth.from(fecha).equals(mes)) {
                delMes.put(fecha, nombre);
            }
        });
        return Collections.unmodifiableMap(delMes);
    }
}
