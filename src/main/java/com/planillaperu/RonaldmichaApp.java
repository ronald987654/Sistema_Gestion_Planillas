package com.planillaperu;

import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.ui.RonaldmichaRecursos;
import com.planillaperu.ui.RonaldmichaVentanas;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RonaldmichaApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(RonaldmichaApp.class);

    private RonaldmichaAppContext contexto;
    private Exception errorInicio;

    @Override
    public void init() {
        try {
            contexto = RonaldmichaAppContext.iniciar(RonaldmichaDatabase.archivoPorDefecto());
            contexto.auditoria().registrar(contexto.usuarioActual(), "INICIO_APLICACION",
                    "Base de datos: " + contexto.database().archivo().toAbsolutePath());
        } catch (Exception e) {
            log.error("No se pudo iniciar la base de datos", e);
            errorInicio = e;
        }
    }

    @Override
    public void start(Stage stage) {
        if (errorInicio != null) {
            Alert alerta = new Alert(Alert.AlertType.ERROR,
                    "No se pudo abrir la base de datos:\n" + errorInicio.getMessage());
            alerta.setHeaderText("Error al iniciar Planilla Perú");
            alerta.showAndWait();
            Platform.exit();
            return;
        }

        Thread.currentThread().setUncaughtExceptionHandler((hilo, error) -> {
            log.error("Error no controlado en la interfaz", error);
            String detalle = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            if (stage.getScene() != null && stage.getScene().getRoot() instanceof javafx.scene.layout.StackPane) {
                com.planillaperu.ui.components.RonaldmichaToast.error(stage.getScene().getRoot(), "Ocurrió un error inesperado: "
                        + detalle + ". El detalle quedó en el registro (~/.planillaperu/logs/planilla.log).");
            }
        });
        RonaldmichaRecursos.cargarFuentes();
        stage.setTitle("Planilla Perú");
        stage.getIcons().add(new javafx.scene.image.Image(
                RonaldmichaApp.class.getResource("/com/planillaperu/icono.png").toExternalForm()));
        RonaldmichaVentanas.mostrarLogin(stage, contexto);
        stage.show();
    }

    @Override
    public void stop() {
        if (contexto != null) {
            contexto.auditoria().registrar(contexto.usuarioActual(), "CIERRE_APLICACION", null);
        }
    }
}
