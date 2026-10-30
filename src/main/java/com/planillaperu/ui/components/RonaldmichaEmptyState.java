package com.planillaperu.ui.components;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class RonaldmichaEmptyState extends VBox {

    private final Label titulo = new Label();
    private final Label mensaje = new Label();

    public RonaldmichaEmptyState() {
        this(RonaldmichaIcono.VACIO, "", "");
    }

    public RonaldmichaEmptyState(RonaldmichaIcono icono, String titulo, String mensaje) {
        getStyleClass().add("empty-state");
        this.titulo.setText(titulo);
        this.titulo.getStyleClass().add("empty-state-title");
        this.mensaje.setText(mensaje);
        this.mensaje.getStyleClass().add("empty-state-message");
        this.mensaje.setWrapText(true);
        this.mensaje.setMaxWidth(420);
        getChildren().addAll(icono.nodo(48), this.titulo, this.mensaje);
    }

    public RonaldmichaEmptyState conAccion(String texto, Runnable accion) {
        Button boton = new Button(texto);
        boton.getStyleClass().addAll("btn", "btn-primary");
        boton.setOnAction(e -> accion.run());
        getChildren().add(boton);
        return this;
    }

    public String getTitulo() {
        return titulo.getText();
    }

    public void setTitulo(String texto) {
        titulo.setText(texto);
    }

    public String getMensaje() {
        return mensaje.getText();
    }

    public void setMensaje(String texto) {
        mensaje.setText(texto);
    }
}
