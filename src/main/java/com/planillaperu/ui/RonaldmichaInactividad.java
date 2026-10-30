package com.planillaperu.ui;

import javafx.animation.PauseTransition;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.InputEvent;
import javafx.util.Duration;

final class RonaldmichaInactividad {

    private final Scene escena;
    private final PauseTransition pausa;
    private final EventHandler<InputEvent> reiniciar;

    RonaldmichaInactividad(Scene escena, int minutos, Runnable alVencer) {
        this.escena = escena;
        this.pausa = new PauseTransition(Duration.minutes(minutos));
        this.pausa.setOnFinished(e -> alVencer.run());
        this.reiniciar = e -> pausa.playFromStart();
        escena.addEventFilter(InputEvent.ANY, reiniciar);
        pausa.play();
    }

    void detener() {
        pausa.stop();
        escena.removeEventFilter(InputEvent.ANY, reiniciar);
    }
}
