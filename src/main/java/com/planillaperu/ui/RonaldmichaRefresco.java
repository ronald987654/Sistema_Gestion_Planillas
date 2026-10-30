package com.planillaperu.ui;

import javafx.application.Platform;

final class RonaldmichaRefresco {

    private final Runnable accion;
    private boolean pendiente;

    RonaldmichaRefresco(Runnable accion) {
        this.accion = accion;
    }

    void pedir() {
        if (pendiente) {
            return;
        }
        pendiente = true;
        Platform.runLater(() -> {
            pendiente = false;
            accion.run();
        });
    }
}
