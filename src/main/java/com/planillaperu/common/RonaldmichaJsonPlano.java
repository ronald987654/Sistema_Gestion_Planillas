package com.planillaperu.common;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RonaldmichaJsonPlano {

    private RonaldmichaJsonPlano() {
    }

    public static String escribir(Map<String, String> valores) {
        StringBuilder sb = new StringBuilder("{");
        boolean primero = true;
        for (Map.Entry<String, String> e : valores.entrySet()) {
            if (!primero) {
                sb.append(',');
            }
            primero = false;
            texto(sb, e.getKey());
            sb.append(':');
            texto(sb, e.getValue() == null ? "" : e.getValue());
        }
        return sb.append('}').toString();
    }

    public static Map<String, String> leer(String json) {
        Map<String, String> valores = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return valores;
        }
        Lector l = new Lector(json.trim());
        l.esperar('{');
        l.espacios();
        if (l.ver() == '}') {
            return valores;
        }
        while (true) {
            String clave = l.cadena();
            l.espacios();
            l.esperar(':');
            l.espacios();
            valores.put(clave, l.cadena());
            l.espacios();
            char c = l.siguiente();
            if (c == '}') {
                return valores;
            }
            if (c != ',') {
                throw new IllegalArgumentException("JSON inválido en la posición " + l.pos);
            }
            l.espacios();
        }
    }

    private static void texto(StringBuilder sb, String s) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static final class Lector {
        private final String s;
        private int pos;

        Lector(String s) {
            this.s = s;
        }

        char ver() {
            if (pos >= s.length()) {
                throw new IllegalArgumentException("JSON incompleto");
            }
            return s.charAt(pos);
        }

        char siguiente() {
            char c = ver();
            pos++;
            return c;
        }

        void esperar(char c) {
            if (siguiente() != c) {
                throw new IllegalArgumentException("Se esperaba '" + c + "' en la posición " + (pos - 1));
            }
        }

        void espacios() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        String cadena() {
            esperar('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = siguiente();
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = siguiente();
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> sb.append(e);
                }
            }
        }
    }
}
