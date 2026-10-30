package com.planillaperu.ui;

import javafx.animation.FadeTransition;
import javafx.scene.Node;
import javafx.util.Duration;

public final class RonaldmichaAnimaciones {

    public static final Duration FADE_VISTA = Duration.millis(200);

    private RonaldmichaAnimaciones() {
    }

    public static void aparecer(Node nodo) {
        if (RonaldmichaPreferencias.reducirMovimiento()) {
            nodo.setOpacity(1);
            return;
        }
        nodo.setOpacity(0);
        FadeTransition fade = new FadeTransition(FADE_VISTA, nodo);
        fade.setToValue(1);
        fade.play();
    }
}
