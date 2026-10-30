package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaUsuarioService;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaModal;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

import java.util.stream.Collectors;

final class RonaldmichaCambioClave {

    private RonaldmichaCambioClave() {
    }

    static void mostrar(Node nodo, RonaldmichaAppContext contexto, String claveTemporal, Runnable listo, Runnable cancelado) {
        boolean obligatorio = claveTemporal != null;
        PasswordField actual = new PasswordField();
        PasswordField nueva = new PasswordField();
        PasswordField confirmar = new PasswordField();
        Label errores = new Label();
        errores.getStyleClass().addAll("alert", "error");
        errores.setWrapText(true);
        errores.setMaxWidth(Double.MAX_VALUE);
        errores.setVisible(false);
        errores.setManaged(false);

        VBox contenido = new VBox(10);
        if (obligatorio) {
            Label aviso = new Label("Su contraseña es temporal. Elija una nueva para continuar.");
            aviso.getStyleClass().addAll("alert", "info");
            aviso.setWrapText(true);
            aviso.setMaxWidth(Double.MAX_VALUE);
            contenido.getChildren().add(aviso);
        } else {
            contenido.getChildren().addAll(etiqueta("Contraseña actual"), actual);
        }
        Label reglas = new Label("Al menos " + RonaldmichaUsuarioService.CLAVE_MINIMA + " caracteres, con letras y números.");
        reglas.getStyleClass().add("text-muted");
        contenido.getChildren().addAll(etiqueta("Nueva contraseña"), nueva, etiqueta("Confirmar contraseña"), confirmar,
                reglas, errores);

        RonaldmichaModal modal = new RonaldmichaModal("Cambiar contraseña", contenido).ancho(420);
        Button guardar = new Button("Guardar contraseña");
        guardar.getStyleClass().addAll("btn", "btn-primary");
        guardar.setDefaultButton(true);
        boolean[] guardado = {false};
        guardar.setOnAction(e -> {
            try {
                contexto.usuarios().cambiarClave(contexto.sesion(), obligatorio ? claveTemporal : actual.getText(),
                        nueva.getText(), confirmar.getText());
                guardado[0] = true;
                modal.cerrar();
            } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException ex) {
                errores.setText(ex instanceof RonaldmichaValidacionException v
                        ? v.errores().stream().map(m -> "• " + m).collect(Collectors.joining("\n")) : ex.getMessage());
                errores.setVisible(true);
                errores.setManaged(true);
            }
        });
        modal.alCerrar(() -> (guardado[0] ? listo : cancelado).run());
        modal.agregarAccion(guardar).mostrar(nodo);
        (obligatorio ? nueva : actual).requestFocus();
    }

    private static Label etiqueta(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("field-label");
        return l;
    }
}
