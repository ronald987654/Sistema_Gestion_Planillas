package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaAprobacion;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaTrabajadorService.MisDatos;
import com.planillaperu.service.RonaldmichaTrabajadorService.TipoCuenta;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaModal;
import com.planillaperu.ui.components.RonaldmichaToast;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.stream.Collectors;

public class RonaldmichaMisDatosController {

    private final RonaldmichaAppContext contexto;
    private final TextField celular = new TextField();
    private final TextField correo = new TextField();
    private final Label errores = new Label();

    @FXML private VBox raiz;

    public RonaldmichaMisDatosController(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        cargar();
    }

    private void cargar() {
        MisDatos datos;
        try {
            datos = contexto.trabajadores().misDatos(contexto.sesion());
        } catch (RonaldmichaAccesoDenegadoException | RonaldmichaValidacionException e) {
            Label aviso = new Label(e.getMessage());
            aviso.getStyleClass().addAll("alert", "error");
            raiz.getChildren().setAll(aviso);
            return;
        }
        RonaldmichaTrabajador t = datos.trabajador();
        raiz.getChildren().setAll(personales(t), contacto(t), cuentas(t, datos));
    }

    private Node personales(RonaldmichaTrabajador t) {
        GridPane grilla = grilla();
        int fila = 0;
        fila = dato(grilla, fila, "Código", t.codigo());
        fila = dato(grilla, fila, "Documento", t.tipoDoc().codigo() + " " + t.numDoc());
        fila = dato(grilla, fila, "Apellidos y nombres", t.nombreCompleto());
        fila = dato(grilla, fila, "Fecha de nacimiento",
                t.fechaNacimiento() == null ? "—" : RonaldmichaFormatos.fecha(t.fechaNacimiento()));
        fila = dato(grilla, fila, "N° de hijos", String.valueOf(t.hijos()));
        fila = dato(grilla, fila, "Dirección", t.direccion() == null ? "—" : t.direccion());
        fila = dato(grilla, fila, "Cargo", t.cargo());
        fila = dato(grilla, fila, "Categoría", t.categoria().nombre());
        fila = dato(grilla, fila, "Área", t.area());
        fila = dato(grilla, fila, "Fecha de ingreso", RonaldmichaFormatos.fecha(t.fechaIngreso()));
        fila = dato(grilla, fila, "Régimen laboral", t.regimen().descripcion());
        fila = dato(grilla, fila, "Sistema de pensiones", t.sistemaPension().nombre()
                + (t.cuspp() == null ? "" : " · CUSPP " + t.cuspp()));
        dato(grilla, fila, "Forma de pago", t.tipoPago().nombre());
        Label nota = new Label("Si algún dato está mal, pida la corrección al área de planillas.");
        nota.getStyleClass().add("text-muted");
        return tarjeta("Datos personales", grilla, nota);
    }

    private Node contacto(RonaldmichaTrabajador t) {
        celular.setText(t.celular() == null ? "" : t.celular());
        celular.setPromptText("987654321");
        correo.setText(t.correo() == null ? "" : t.correo());
        correo.setPromptText("nombre@correo.com");
        errores.getStyleClass().setAll("label", "alert", "error");
        errores.setWrapText(true);
        errores.setMaxWidth(Double.MAX_VALUE);
        ocultar(errores);

        GridPane grilla = grilla();
        grilla.addRow(0, etiqueta("Celular"), celular);
        grilla.addRow(1, etiqueta("Correo electrónico"), correo);
        Button guardar = new Button("Guardar contacto");
        guardar.getStyleClass().addAll("btn", "btn-primary");
        guardar.setOnAction(e -> guardarContacto());
        HBox acciones = new HBox(guardar);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        return tarjeta("Contacto", grilla, errores, acciones);
    }

    private void guardarContacto() {
        try {
            contexto.trabajadores().actualizarContacto(contexto.sesion(), celular.getText(), correo.getText());
            ocultar(errores);
            RonaldmichaToast.exito(raiz, "Datos de contacto actualizados.");
        } catch (RonaldmichaValidacionException e) {
            errores.setText(e.errores().stream().map(m -> "• " + m).collect(Collectors.joining("\n")));
            errores.setVisible(true);
            errores.setManaged(true);
        } catch (RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
        }
    }

    private Node cuentas(RonaldmichaTrabajador t, MisDatos datos) {
        GridPane grilla = grilla();
        grilla.addRow(0, etiqueta("Cuenta sueldo"), valor(cuenta(t.bancoSueldo(), t.ctaSueldo())));
        grilla.addRow(1, etiqueta("Cuenta CTS"), valor(cuenta(t.bancoCts(), t.ctaCts())));
        VBox caja = new VBox(12, grilla);
        if (datos.pendiente().isPresent()) {
            RonaldmichaAprobacion a = datos.pendiente().get();
            Label pendiente = new Label("Cambio pendiente de aprobación (solicitud #" + a.id() + " del "
                    + RonaldmichaFormatos.fechaHora(a.fecha()) + "): " + a.detalle());
            pendiente.getStyleClass().addAll("alert", "warning", "cambio-pendiente");
            pendiente.setWrapText(true);
            pendiente.setMaxWidth(Double.MAX_VALUE);
            caja.getChildren().add(pendiente);
        }
        Button solicitar = new Button("Solicitar cambio de cuenta");
        solicitar.getStyleClass().add("btn");
        solicitar.setDisable(datos.pendiente().isPresent());
        solicitar.setOnAction(e -> solicitarCambio());
        Label nota = new Label("Por seguridad, el cambio de cuenta no se aplica de inmediato: lo aprueba otro usuario "
                + "(administrador o gerencia).");
        nota.getStyleClass().add("text-muted");
        nota.setWrapText(true);
        HBox acciones = new HBox(solicitar);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        caja.getChildren().addAll(nota, acciones);
        return tarjeta("Cuentas bancarias", caja);
    }

    private void solicitarCambio() {
        ToggleGroup grupo = new ToggleGroup();
        HBox selector = new HBox();
        selector.getStyleClass().add("segmented");
        for (TipoCuenta tipo : TipoCuenta.values()) {
            ToggleButton b = new ToggleButton(tipo.etiqueta());
            b.setUserData(tipo);
            b.setToggleGroup(grupo);
            selector.getChildren().add(b);
        }
        grupo.selectToggle(grupo.getToggles().getFirst());
        grupo.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                grupo.selectToggle(a);
            }
        });
        TextField banco = new TextField();
        banco.setPromptText("Banco");
        TextField cuenta = new TextField();
        cuenta.setPromptText("Número de cuenta");
        Label error = new Label();
        error.getStyleClass().addAll("alert", "error");
        error.setWrapText(true);
        error.setMaxWidth(Double.MAX_VALUE);
        ocultar(error);
        VBox contenido = new VBox(10, selector, etiqueta("Banco"), banco, etiqueta("Número de cuenta"), cuenta, error);

        RonaldmichaModal modal = new RonaldmichaModal("Solicitar cambio de cuenta", contenido).ancho(460);
        Button enviar = new Button("Enviar solicitud");
        enviar.getStyleClass().addAll("btn", "btn-primary");
        enviar.setOnAction(e -> {
            try {
                RonaldmichaAprobacion a = contexto.trabajadores().solicitarCambioCuenta(contexto.sesion(),
                        (TipoCuenta) grupo.getSelectedToggle().getUserData(), banco.getText(), cuenta.getText());
                modal.cerrar();
                contexto.refrescarAprobaciones();
                cargar();
                RonaldmichaToast.exito(raiz, "Solicitud #" + a.id() + " enviada: se aplicará cuando la aprueben.");
            } catch (RonaldmichaValidacionException ex) {
                error.setText(ex.errores().stream().map(m -> "• " + m).collect(Collectors.joining("\n")));
                error.setVisible(true);
                error.setManaged(true);
            } catch (RonaldmichaAccesoDenegadoException ex) {
                RonaldmichaToast.error(raiz, ex.getMessage());
            }
        });
        modal.agregarAccion(enviar).mostrar(raiz);
    }

    private static VBox tarjeta(String titulo, Node... contenido) {
        Label t = new Label(titulo);
        t.getStyleClass().add("title-md");
        VBox caja = new VBox(12, t);
        caja.getChildren().addAll(contenido);
        caja.getStyleClass().addAll("card", "tarjeta-mis-datos");
        caja.setMaxWidth(760);
        return caja;
    }

    private static GridPane grilla() {
        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(10);
        ColumnConstraints etiquetas = new ColumnConstraints(180);
        ColumnConstraints valores = new ColumnConstraints();
        valores.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().setAll(etiquetas, valores);
        return g;
    }

    private static int dato(GridPane g, int fila, String etiqueta, String valor) {
        g.addRow(fila, etiqueta(etiqueta), valor(valor == null || valor.isBlank() ? "—" : valor));
        return fila + 1;
    }

    private static Label etiqueta(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("field-label");
        return l;
    }

    private static Label valor(String texto) {
        Label l = new Label(texto);
        l.setWrapText(true);
        return l;
    }

    private static String cuenta(String banco, String numero) {
        return banco == null && numero == null ? "Sin cuenta registrada"
                : ((banco == null ? "" : banco) + " " + (numero == null ? "" : numero)).trim();
    }

    private static void ocultar(Node n) {
        n.setVisible(false);
        n.setManaged(false);
    }
}
