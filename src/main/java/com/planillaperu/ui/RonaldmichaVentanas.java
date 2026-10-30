package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.ui.components.RonaldmichaToast;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class RonaldmichaVentanas {

    private static RonaldmichaInactividad inactividad;

    private RonaldmichaVentanas() {
    }

    public static void mostrarLogin(Stage stage, RonaldmichaAppContext contexto) {
        detenerInactividad();
        stage.setMinWidth(0);
        stage.setMinHeight(0);
        stage.setMaximized(false);
        stage.setResizable(false);
        stage.setScene(escena(RonaldmichaRecursos.cargarVista("login.fxml", contexto), 460, 660));
        stage.centerOnScreen();
    }

    public static void mostrarPrincipal(Stage stage, RonaldmichaAppContext contexto) {
        detenerInactividad();
        stage.setResizable(true);
        Scene escena = escena(RonaldmichaRecursos.cargarVista("main-layout.fxml", contexto), 1280, 800);
        stage.setScene(escena);
        stage.setMinWidth(860);
        stage.setMinHeight(600);
        stage.centerOnScreen();
        int minutos = contexto.seguridad().minutosInactividad();
        inactividad = new RonaldmichaInactividad(escena, minutos, () -> {
            contexto.cerrarSesionPorInactividad(minutos);
            mostrarLogin(stage, contexto);
            RonaldmichaToast.error(stage.getScene().getRoot(), "Su sesión se cerró tras " + minutos + " minutos sin actividad.");
        });
    }

    private static void detenerInactividad() {
        if (inactividad != null) {
            inactividad.detener();
            inactividad = null;
        }
    }

    static void marcarMovimiento(Parent raiz) {
        raiz.getStyleClass().remove("reducir-movimiento");
        if (RonaldmichaPreferencias.reducirMovimiento()) {
            raiz.getStyleClass().add("reducir-movimiento");
        }
    }

    private static Scene escena(Parent raiz, double ancho, double alto) {
        Scene escena = new Scene(raiz, ancho, alto);
        escena.getStylesheets().add(RonaldmichaRecursos.url(RonaldmichaRecursos.TEMA).toExternalForm());
        escena.addPostLayoutPulseListener(() -> RonaldmichaEstilos.aplicar(escena.getRoot()));
        marcarMovimiento(raiz);
        RonaldmichaAnimaciones.aparecer(raiz);
        return escena;
    }
}
