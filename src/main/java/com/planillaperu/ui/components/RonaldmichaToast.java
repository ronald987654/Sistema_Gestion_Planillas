package com.planillaperu.ui.components;

import com.planillaperu.ui.RonaldmichaArchivos;
import com.planillaperu.ui.RonaldmichaPreferencias;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.nio.file.Path;

public final class RonaldmichaToast {

    public enum Tipo { EXITO, ERROR }

    private static final Duration ENTRADA = Duration.millis(220);
    private static final Duration VISIBLE = Duration.millis(4200);
    private static final String ID_CONTENEDOR = "toast-host";

    private RonaldmichaToast() {
    }

    public static void exito(Node cualquierNodo, String mensaje) {
        mostrar(cualquierNodo, mensaje, Tipo.EXITO);
    }

    public static void error(Node cualquierNodo, String mensaje) {
        mostrar(cualquierNodo, mensaje, Tipo.ERROR);
    }

    public static void archivo(Node cualquierNodo, Path archivo) {
        Hyperlink abrir = new Hyperlink("Abrir carpeta");
        abrir.setMinWidth(Region.USE_PREF_SIZE);
        abrir.setOnAction(e -> RonaldmichaArchivos.abrirCarpeta(cualquierNodo, archivo.toAbsolutePath().getParent()));
        mostrar(cualquierNodo, "Archivo guardado: " + archivo.getFileName(), Tipo.EXITO, abrir);
    }

    public static void mostrar(Node cualquierNodo, String mensaje, Tipo tipo) {
        mostrar(cualquierNodo, mensaje, tipo, null);
    }

    public static void mostrar(Node cualquierNodo, String mensaje, Tipo tipo, Node accion) {
        VBox contenedor = contenedor(cualquierNodo);

        Label texto = new Label(mensaje);
        texto.setWrapText(true);
        HBox.setHgrow(texto, Priority.ALWAYS);
        texto.setMaxWidth(Double.MAX_VALUE);

        Button cerrar = new Button();
        cerrar.setGraphic(RonaldmichaIcono.CERRAR.nodo(14));
        cerrar.getStyleClass().addAll("btn-ghost", "btn-icon");
        cerrar.setFocusTraversable(false);
        cerrar.setAccessibleText("Cerrar aviso");

        HBox toast = accion == null ? new HBox(8, texto, cerrar) : new HBox(8, texto, accion, cerrar);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.getStyleClass().addAll("toast", tipo == Tipo.EXITO ? "success" : "error");
        toast.setMaxWidth(420);
        contenedor.getChildren().add(toast);

        PauseTransition espera = new PauseTransition(VISIBLE);
        espera.setOnFinished(e -> ocultar(contenedor, toast));
        cerrar.setOnAction(e -> {
            espera.stop();
            ocultar(contenedor, toast);
        });

        if (!RonaldmichaPreferencias.reducirMovimiento()) {
            toast.setOpacity(0);
            toast.setTranslateY(24);
            TranslateTransition subir = new TranslateTransition(ENTRADA, toast);
            subir.setToY(0);
            FadeTransition aparecer = new FadeTransition(ENTRADA, toast);
            aparecer.setToValue(1);
            new ParallelTransition(subir, aparecer).play();
        }
        espera.play();
    }

    private static void ocultar(VBox contenedor, Node toast) {
        if (RonaldmichaPreferencias.reducirMovimiento()) {
            contenedor.getChildren().remove(toast);
            return;
        }
        FadeTransition fade = new FadeTransition(ENTRADA, toast);
        fade.setToValue(0);
        fade.setOnFinished(e -> contenedor.getChildren().remove(toast));
        fade.play();
    }

    private static VBox contenedor(Node nodo) {
        Parent raiz = nodo.getScene().getRoot();
        if (!(raiz instanceof StackPane stack)) {
            throw new IllegalStateException("La raíz de la escena debe ser un StackPane para mostrar avisos");
        }
        for (Node hijo : stack.getChildren()) {
            if (ID_CONTENEDOR.equals(hijo.getId()) && hijo instanceof VBox existente) {
                existente.toFront();
                return existente;
            }
        }
        VBox nuevo = new VBox();
        nuevo.setId(ID_CONTENEDOR);
        nuevo.getStyleClass().add("toast-host");
        nuevo.setAlignment(Pos.BOTTOM_RIGHT);
        nuevo.setPickOnBounds(false);
        stack.getChildren().add(nuevo);
        return nuevo;
    }
}
