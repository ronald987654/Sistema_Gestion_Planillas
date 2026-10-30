package com.planillaperu.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

final class RonaldmichaPagina {

    static final double ANCHO_MAXIMO = 1400 + 2 * 24;

    private RonaldmichaPagina() {
    }

    static Node envolver(Node vista) {
        if (vista instanceof ScrollPane scroll) {
            centrar(scroll);
            return scroll;
        }
        if (vista instanceof Region r && r.getStyleClass().contains("view") && !r.getStyleClass().contains("vista-fija")) {
            ScrollPane scroll = new ScrollPane(r);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.getStyleClass().add("pagina");
            centrar(scroll);
            return scroll;
        }
        return vista;
    }

    private static void centrar(ScrollPane scroll) {
        if (scroll.getContent() instanceof Region contenido && !(scroll.getContent() instanceof StackPane caja
                && caja.getStyleClass().contains("pagina-centrada"))) {
            contenido.setMaxWidth(ANCHO_MAXIMO);
            StackPane caja = new StackPane(contenido);
            caja.setAlignment(Pos.TOP_CENTER);
            caja.getStyleClass().add("pagina-centrada");
            scroll.setContent(caja);
        }
    }
}
