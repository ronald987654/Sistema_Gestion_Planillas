package com.planillaperu.common;

import java.math.BigDecimal;

public final class RonaldmichaNumeroALetras {

    private static final String[] UNIDADES = {"", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho",
            "nueve", "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho",
            "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco",
            "veintiséis", "veintisiete", "veintiocho", "veintinueve"};
    private static final String[] DECENAS = {"", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta",
            "ochenta", "noventa"};
    private static final String[] CENTENAS = {"", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
            "seiscientos", "setecientos", "ochocientos", "novecientos"};

    private RonaldmichaNumeroALetras() {
    }

    public static String soles(BigDecimal monto) {
        BigDecimal valor = RonaldmichaDinero.redondear(monto);
        BigDecimal absoluto = valor.abs();
        long entero = absoluto.longValue();
        int centimos = absoluto.subtract(BigDecimal.valueOf(entero)).movePointRight(2).intValue();
        String texto = (valor.signum() < 0 ? "menos " : "") + entero(entero) + " con "
                + String.format("%02d", centimos) + "/100 soles";
        return texto.toUpperCase(RonaldmichaFormatos.LOCALE_PE);
    }

    public static String texto(BigDecimal monto) {
        String s = soles(monto).toLowerCase(RonaldmichaFormatos.LOCALE_PE);
        return s.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + s.substring(1);
    }

    public static String entero(long n) {
        if (n == 0) {
            return "cero";
        }
        if (n < 0) {
            return "menos " + entero(-n);
        }
        StringBuilder sb = new StringBuilder();
        long millones = n / 1_000_000;
        long resto = n % 1_000_000;
        if (millones > 0) {
            sb.append(millones == 1 ? "un millón" : apocopar(miles(millones)) + " millones");
        }
        if (resto > 0) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(miles(resto));
        }
        return sb.toString();
    }

    private static String miles(long n) {
        long miles = n / 1000;
        int resto = (int) (n % 1000);
        StringBuilder sb = new StringBuilder();
        if (miles > 0) {
            sb.append(miles == 1 ? "mil" : apocopar(centenas((int) miles)) + " mil");
        }
        if (resto > 0) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(centenas(resto));
        }
        return sb.toString();
    }

    private static String centenas(int n) {
        if (n == 100) {
            return "cien";
        }
        int c = n / 100;
        int resto = n % 100;
        String decenas = decenas(resto);
        if (c == 0) {
            return decenas;
        }
        return CENTENAS[c] + (decenas.isEmpty() ? "" : " " + decenas);
    }

    private static String decenas(int n) {
        if (n < 30) {
            return UNIDADES[n];
        }
        int u = n % 10;
        return DECENAS[n / 10] + (u == 0 ? "" : " y " + UNIDADES[u]);
    }

    private static String apocopar(String texto) {
        if (texto.endsWith("veintiuno")) {
            return texto.substring(0, texto.length() - "veintiuno".length()) + "veintiún";
        }
        if (texto.endsWith("uno")) {
            return texto.substring(0, texto.length() - 1);
        }
        return texto;
    }
}
