package com.planillaperu.ui.components;

import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class RonaldmichaValidacion {

    private record Regla(Control control, Supplier<String> error, Label mensaje, boolean[] tocado) {
    }

    private final List<Regla> reglas = new ArrayList<>();
    private final ReadOnlyBooleanWrapper valido = new ReadOnlyBooleanWrapper(true);

    public RonaldmichaValidacion regla(Control control, Supplier<String> error, Observable... dependencias) {
        Label mensaje = new Label();
        mensaje.getStyleClass().add("mensaje-campo");
        mensaje.setWrapText(true);
        mostrar(mensaje, false);
        insertarDebajo(control, mensaje);
        Regla r = new Regla(control, error, mensaje, new boolean[]{false});
        reglas.add(r);
        for (Observable o : dependencias) {
            o.addListener(x -> {
                if (control.isFocused() || o != dependencias[0]) {
                    r.tocado()[0] = true;
                }
                evaluar();
            });
        }
        control.focusedProperty().addListener((o, antes, ahora) -> {
            if (!ahora) {
                r.tocado()[0] = true;
                evaluar();
            }
        });
        evaluar();
        return this;
    }

    public ReadOnlyBooleanProperty validoProperty() {
        return valido.getReadOnlyProperty();
    }

    public boolean isValido() {
        return valido.get();
    }

    public void mostrarTodo() {
        reglas.forEach(r -> r.tocado()[0] = true);
        evaluar();
    }

    public void evaluar() {
        boolean todoBien = true;
        for (Regla r : reglas) {
            String error = r.control().isDisabled() || !r.control().isVisible() ? null : r.error().get();
            boolean conError = error != null;
            todoBien &= !conError;
            boolean visible = conError && r.tocado()[0];
            r.control().getStyleClass().remove("campo-error");
            if (visible) {
                r.control().getStyleClass().add("campo-error");
            }
            r.mensaje().setText(visible ? error : "");
            mostrar(r.mensaje(), visible);
        }
        valido.set(todoBien);
    }

    private static void insertarDebajo(Control control, Label mensaje) {
        Node hijo = control;
        Parent padre = control.getParent();
        if (padre instanceof javafx.scene.layout.HBox && padre.getParent() instanceof javafx.scene.layout.VBox) {
            hijo = padre;
            padre = padre.getParent();
        }
        if (padre instanceof javafx.scene.layout.VBox p) {
            int i = p.getChildren().indexOf(hijo);
            p.getChildren().add(i + 1, mensaje);
        }
    }

    private static void mostrar(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }
}
