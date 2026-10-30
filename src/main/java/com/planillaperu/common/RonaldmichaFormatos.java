package com.planillaperu.common;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class RonaldmichaFormatos {

    public static final Locale LOCALE_PE = Locale.forLanguageTag("es-PE");
    public static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_PE);
    public static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", LOCALE_PE);

    private static final DecimalFormatSymbols SIMBOLOS = new DecimalFormatSymbols(LOCALE_PE);

    static {
        SIMBOLOS.setGroupingSeparator(',');
        SIMBOLOS.setDecimalSeparator('.');
        SIMBOLOS.setMinusSign('-');
    }

    private RonaldmichaFormatos() {
    }

    public static String moneda(BigDecimal monto) {
        BigDecimal valor = RonaldmichaDinero.redondear(monto);
        String numero = numero(valor.abs());
        return (valor.signum() < 0 ? "-" : "") + "S/ " + numero;
    }

    public static String numero(BigDecimal monto) {
        DecimalFormat df = new DecimalFormat("#,##0.00", SIMBOLOS);
        df.setRoundingMode(RonaldmichaDinero.REDONDEO);
        return df.format(RonaldmichaDinero.redondear(monto));
    }

    public static String fecha(LocalDate fecha) {
        return fecha == null ? "" : FECHA.format(fecha);
    }

    public static String fechaHora(LocalDateTime fechaHora) {
        return fechaHora == null ? "" : FECHA_HORA.format(fechaHora);
    }

    public static LocalDate leerFecha(String texto) {
        return LocalDate.parse(texto.trim(), FECHA);
    }
}
