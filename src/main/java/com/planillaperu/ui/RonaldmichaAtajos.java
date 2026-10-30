package com.planillaperu.ui;

public final class RonaldmichaAtajos {

    private static Runnable exportar;

    private RonaldmichaAtajos() {
    }

    static void registrarExportar(Runnable accion) {
        exportar = accion;
    }

    static void limpiar() {
        exportar = null;
    }

    static boolean exportar() {
        if (exportar == null) {
            return false;
        }
        exportar.run();
        return true;
    }
}
