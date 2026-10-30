package com.planillaperu.common;

import java.text.Normalizer;
import java.util.Locale;

public final class RonaldmichaBusqueda {

    private RonaldmichaBusqueda() {
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim();
    }

    public static boolean coincide(String texto, String consulta) {
        String base = normalizar(texto);
        for (String palabra : normalizar(consulta).split("\\s+")) {
            if (!palabra.isEmpty() && !base.contains(palabra)) {
                return false;
            }
        }
        return true;
    }
}
