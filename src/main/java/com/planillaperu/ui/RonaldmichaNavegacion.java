package com.planillaperu.ui;

import java.util.function.Consumer;

public final class RonaldmichaNavegacion {

    private static Consumer<RonaldmichaVista> destino = vista -> { };
    private static Runnable aprobaciones = () -> { };

    private RonaldmichaNavegacion() {
    }

    static void registrar(Consumer<RonaldmichaVista> navegador) {
        destino = navegador;
    }

    public static void ir(RonaldmichaVista vista) {
        destino.accept(vista);
    }

    static void registrarAprobaciones(Runnable abrir) {
        aprobaciones = abrir;
    }

    public static void abrirAprobaciones() {
        aprobaciones.run();
    }
}
