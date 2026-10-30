package com.planillaperu.ui.components;

import javafx.animation.PauseTransition;
import javafx.scene.control.Button;
import javafx.util.Duration;

public class RonaldmichaConfirmButton extends Button {

    private static final String TEXTO_CONFIRMAR = "¿Seguro? Pulse otra vez";

    private final PauseTransition desarmar = new PauseTransition(Duration.seconds(4));
    private Runnable accion = () -> { };
    private String textoOriginal;
    private boolean armado;

    public RonaldmichaConfirmButton() {
        this("");
    }

    public RonaldmichaConfirmButton(String texto) {
        super(texto);
        getStyleClass().add("btn");
        desarmar.setOnFinished(e -> desarmar());
        focusedProperty().addListener((o, antes, ahora) -> {
            if (!ahora) {
                desarmar();
            }
        });
        setOnAction(e -> {
            if (armado) {
                desarmar();
                accion.run();
            } else {
                armar();
            }
        });
    }

    public RonaldmichaConfirmButton(String texto, Runnable accion) {
        this(texto);
        setAccion(accion);
    }

    public void setAccion(Runnable accion) {
        this.accion = accion;
    }

    public boolean isArmado() {
        return armado;
    }

    private void armar() {
        armado = true;
        textoOriginal = getText();
        setText(TEXTO_CONFIRMAR);
        getStyleClass().add("btn-danger");
        desarmar.playFromStart();
    }

    private void desarmar() {
        if (!armado) {
            return;
        }
        armado = false;
        desarmar.stop();
        setText(textoOriginal);
        getStyleClass().remove("btn-danger");
    }
}
