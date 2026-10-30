package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.ui.components.RonaldmichaConfirmButton;
import com.planillaperu.ui.components.RonaldmichaModal;
import com.planillaperu.ui.components.RonaldmichaToast;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Set;

public class RonaldmichaLoginController {

    private static final Logger log = LoggerFactory.getLogger(RonaldmichaLoginController.class);
    private static final Set<RonaldmichaRol> ROLES_ADMIN = EnumSet.of(RonaldmichaRol.ADMIN, RonaldmichaRol.APROBADOR);
    private static final Set<RonaldmichaRol> ROLES_TRABAJADOR = EnumSet.of(RonaldmichaRol.TRABAJADOR);

    private final RonaldmichaAppContext contexto;
    private final ToggleGroup tipoAcceso = new ToggleGroup();

    @FXML private ToggleButton opcionAdmin;
    @FXML private ToggleButton opcionTrabajador;
    @FXML private Label etiquetaUsuario;
    @FXML private TextField campoUsuario;
    @FXML private PasswordField campoClave;
    @FXML private Label mensajeError;
    @FXML private Button botonIngresar;

    public RonaldmichaLoginController(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        opcionAdmin.setToggleGroup(tipoAcceso);
        opcionTrabajador.setToggleGroup(tipoAcceso);
        tipoAcceso.selectedToggleProperty().addListener((o, anterior, actual) -> {
            if (actual == null) {
                tipoAcceso.selectToggle(anterior);
                return;
            }
            boolean trabajador = actual == opcionTrabajador;
            etiquetaUsuario.setText(trabajador ? "DNI" : "Usuario");
            campoUsuario.setPromptText(trabajador ? "Número de DNI" : "Usuario");
            ocultarError();
        });
        tipoAcceso.selectToggle(opcionAdmin);

        ocultarError();
        campoUsuario.textProperty().addListener((o, a, b) -> ocultarError());
        campoClave.textProperty().addListener((o, a, b) -> ocultarError());
        Platform.runLater(campoUsuario::requestFocus);
    }

    @FXML
    private void ingresar() {
        if (campoUsuario.getText().isBlank() || campoClave.getText().isEmpty()) {
            mostrarError(opcionTrabajador.isSelected()
                    ? "Ingrese su DNI y contraseña." : "Ingrese su usuario y contraseña.");
            return;
        }
        Set<RonaldmichaRol> permitidos = opcionTrabajador.isSelected() ? ROLES_TRABAJADOR : ROLES_ADMIN;
        try {
            String clave = campoClave.getText();
            RonaldmichaUsuario usuario = contexto.usuarios().autenticar(campoUsuario.getText(), clave, permitidos);
            contexto.iniciarSesion(usuario);
            Stage stage = (Stage) botonIngresar.getScene().getWindow();
            if (!contexto.usuarios().debeCambiarClave(usuario)) {
                RonaldmichaVentanas.mostrarPrincipal(stage, contexto);
                return;
            }
            RonaldmichaCambioClave.mostrar(botonIngresar, contexto, clave, () -> RonaldmichaVentanas.mostrarPrincipal(stage, contexto), () -> {
                contexto.cerrarSesion();
                campoClave.clear();
                mostrarError("Debe cambiar la contraseña temporal para ingresar.");
            });
        } catch (RonaldmichaAccesoDenegadoException e) {
            campoClave.clear();
            campoClave.requestFocus();
            mostrarError(e.getMessage());
        }
    }

    @FXML
    private void restablecerDemo() {
        Label texto = new Label("Se borrarán todos los datos registrados (trabajadores, asistencia, planillas, "
                + "parámetros, usuarios y auditoría) y se cargarán de nuevo los datos de demostración.");
        texto.setWrapText(true);
        RonaldmichaConfirmButton restablecer = new RonaldmichaConfirmButton("Restablecer");
        RonaldmichaModal modal = new RonaldmichaModal("Restablecer datos de demostración", texto).ancho(440);
        restablecer.setAccion(() -> {
            try {
                contexto.datosDemo().restablecer(contexto.usuarioActual());
                modal.cerrar();
                RonaldmichaToast.exito(botonIngresar, "Datos de demostración restablecidos.");
            } catch (RuntimeException e) {
                log.error("No se pudo restablecer la base de datos", e);
                modal.cerrar();
                RonaldmichaToast.error(botonIngresar, "No se pudo restablecer: " + e.getMessage());
            }
        });
        modal.agregarAccion(restablecer).mostrar(botonIngresar);
    }

    private void mostrarError(String mensaje) {
        mensajeError.setText(mensaje);
        mensajeError.setVisible(true);
        mensajeError.setManaged(true);
    }

    private void ocultarError() {
        mensajeError.setVisible(false);
        mensajeError.setManaged(false);
    }
}
