package com.planillaperu.ui;

import java.util.prefs.Preferences;

public final class RonaldmichaPreferencias {

    private static final Preferences PREFS = Preferences.userRoot().node("com/planillaperu");
    private static final String REDUCIR_MOVIMIENTO = "reducirMovimiento";

    private RonaldmichaPreferencias() {
    }

    public static boolean reducirMovimiento() {
        return PREFS.getBoolean(REDUCIR_MOVIMIENTO, false);
    }

    public static void setReducirMovimiento(boolean valor) {
        PREFS.putBoolean(REDUCIR_MOVIMIENTO, valor);
    }
}
