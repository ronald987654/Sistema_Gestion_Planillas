package com.planillaperu.ui.components;

import com.planillaperu.ui.RonaldmichaPreferencias;
import javafx.animation.FadeTransition;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class RonaldmichaModal {

    private static final Duration FADE = Duration.millis(160);

    private final StackPane overlay = new StackPane();
    private final VBox tarjeta = new VBox();
    private final HBox pie = new HBox(8);
    private final Button cancelar = new Button("Cancelar");
    private Runnable alCerrar = () -> { };
    private Scene escena;
    private final EventHandler<KeyEvent> filtroEsc = e -> {
        if (e.getCode() == KeyCode.ESCAPE) {
            e.consume();
            cerrar();
        }
    };

    public RonaldmichaModal(String titulo, Node contenido) {
        Label etiqueta = new Label(titulo);
        etiqueta.getStyleClass().add("modal-title");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        Button cerrarX = new Button();
        cerrarX.setGraphic(RonaldmichaIcono.CERRAR.nodo(16));
        cerrarX.getStyleClass().addAll("btn-ghost", "btn-icon");
        cerrarX.setAccessibleText("Cerrar");
        cerrarX.setOnAction(e -> cerrar());
        HBox cabecera = new HBox(etiqueta, espacio, cerrarX);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        cancelar.setOnAction(e -> cerrar());
        cancelar.setCancelButton(false);
        pie.setAlignment(Pos.CENTER_RIGHT);
        pie.getChildren().add(cancelar);

        tarjeta.getStyleClass().add("modal-card");
        tarjeta.getChildren().addAll(cabecera, contenido, pie);
        tarjeta.setMaxWidth(560);
        tarjeta.setMaxHeight(Region.USE_PREF_SIZE);
        VBox.setVgrow(contenido, Priority.ALWAYS);

        overlay.getStyleClass().add("modal-overlay");
        overlay.getChildren().add(tarjeta);
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) {
                cerrar();
            }
        });
    }

    public RonaldmichaModal agregarAccion(Button boton) {
        pie.getChildren().add(boton);
        return this;
    }

    public RonaldmichaModal ancho(double maximo) {
        tarjeta.setMaxWidth(maximo);
        return this;
    }

    public RonaldmichaModal alCerrar(Runnable accion) {
        this.alCerrar = accion;
        return this;
    }

    public Button botonCancelar() {
        return cancelar;
    }

    public void mostrar(Node cualquierNodo) {
        escena = cualquierNodo.getScene();
        Parent raiz = escena.getRoot();
        if (!(raiz instanceof StackPane stack)) {
            throw new IllegalStateException("La raíz de la escena debe ser un StackPane para mostrar un modal");
        }
        stack.getChildren().add(overlay);
        escena.addEventFilter(KeyEvent.KEY_PRESSED, filtroEsc);
        if (!RonaldmichaPreferencias.reducirMovimiento()) {
            overlay.setOpacity(0);
            FadeTransition fade = new FadeTransition(FADE, overlay);
            fade.setToValue(1);
            fade.play();
        }
        tarjeta.requestFocus();
    }

    public void cerrar() {
        if (escena == null) {
            return;
        }
        escena.removeEventFilter(KeyEvent.KEY_PRESSED, filtroEsc);
        escena = null;
        Runnable quitar = () -> {
            if (overlay.getParent() instanceof StackPane stack) {
                stack.getChildren().remove(overlay);
            }
            alCerrar.run();
        };
        if (RonaldmichaPreferencias.reducirMovimiento()) {
            quitar.run();
            return;
        }
        FadeTransition fade = new FadeTransition(FADE, overlay);
        fade.setToValue(0);
        fade.setOnFinished(e -> quitar.run());
        fade.play();
    }
}
