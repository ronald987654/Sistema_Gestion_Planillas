package com.planillaperu.ui.components;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class RonaldmichaKpiCard extends VBox {

    private final Label titulo = new Label();
    private final Label valor = new Label("—");
    private final Label detalle = new Label();

    public RonaldmichaKpiCard() {
        getStyleClass().add("kpi");
        titulo.getStyleClass().add("kpi-title");
        valor.getStyleClass().add("kpi-value");
        detalle.getStyleClass().add("kpi-detail");
        detalle.managedProperty().bind(detalle.textProperty().isNotEmpty());
        detalle.visibleProperty().bind(detalle.managedProperty());
        getChildren().addAll(titulo, valor, detalle);
    }

    public RonaldmichaKpiCard(String titulo, String valor, String detalle) {
        this();
        setTitulo(titulo);
        setValor(valor);
        setDetalle(detalle);
    }

    public String getTitulo() {
        return titulo.getText();
    }

    public void setTitulo(String texto) {
        titulo.setText(texto);
    }

    public String getValor() {
        return valor.getText();
    }

    public void setValor(String texto) {
        valor.setText(texto);
    }

    public String getDetalle() {
        return detalle.getText();
    }

    public void setDetalle(String texto) {
        detalle.setText(texto == null ? "" : texto);
    }
}
