package com.planillaperu.domain;

import java.util.Optional;

public final class RonaldmichaRuc {

    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final String[] PREFIJOS = {"10", "15", "17", "20"};

    private RonaldmichaRuc() {
    }

    public static boolean esValido(String ruc) {
        return error(ruc).isEmpty();
    }

    public static Optional<String> error(String ruc) {
        if (ruc == null || ruc.isBlank()) {
            return Optional.of("Ingrese el RUC.");
        }
        String r = ruc.trim();
        if (!r.matches("\\d{11}")) {
            return Optional.of("El RUC debe tener 11 dígitos.");
        }
        boolean prefijoValido = false;
        for (String p : PREFIJOS) {
            prefijoValido |= r.startsWith(p);
        }
        if (!prefijoValido) {
            return Optional.of("El RUC debe empezar con 10, 15, 17 o 20.");
        }
        if (digitoVerificador(r.substring(0, 10)) != r.charAt(10) - '0') {
            return Optional.of("El dígito verificador del RUC no es correcto.");
        }
        return Optional.empty();
    }

    public static int digitoVerificador(String diezDigitos) {
        int suma = 0;
        for (int i = 0; i < PESOS.length; i++) {
            suma += (diezDigitos.charAt(i) - '0') * PESOS[i];
        }
        int resto = 11 - (suma % 11);
        return switch (resto) {
            case 10 -> 0;
            case 11 -> 1;
            default -> resto;
        };
    }
}
