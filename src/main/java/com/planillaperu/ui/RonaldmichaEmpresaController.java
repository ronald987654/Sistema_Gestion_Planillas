package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaRuc;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaEmpresaService;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaValidacion;
import com.planillaperu.ui.components.RonaldmichaToast;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RonaldmichaEmpresaController {

    private static final Logger log = LoggerFactory.getLogger(RonaldmichaEmpresaController.class);

    private final RonaldmichaAppContext contexto;
    private Long id;
    private byte[] logo;
    private byte[] firma;

    @FXML private Label chipCompletitud;
    @FXML private Label textoFaltantes;
    @FXML private Button botonGuardar;
    private final RonaldmichaValidacion validacion = new RonaldmichaValidacion();
    @FXML private Label erroresGuardado;

    @FXML private TextField ruc;
    @FXML private Label rucMensaje;
    @FXML private TextField razonSocial;
    @FXML private TextField nombreComercial;
    @FXML private ComboBox<String> regimenTributario;
    @FXML private TextField ciiu;
    @FXML private DatePicker inicioActividades;

    @FXML private TextField direccion;
    @FXML private TextField departamento;
    @FXML private TextField provincia;
    @FXML private TextField distrito;
    @FXML private TextField ubigeo;
    @FXML private TextField ciudad;
    @FXML private TextField rubro;

    @FXML private ComboBox<RonaldmichaRegimenLaboral> regimenLaboral;
    @FXML private Label etiquetaRemype;
    @FXML private TextField registroRemype;
    @FXML private TextField eps;
    @FXML private ComboBox<String> periodicidad;
    @FXML private TextField diaPago;
    @FXML private CheckBox sctr;

    @FXML private TextField repNombre;
    @FXML private ComboBox<String> repTipoDoc;
    @FXML private TextField repDoc;
    @FXML private TextField repCargo;
    @FXML private TextField partidaRegistral;

    @FXML private TextField telefono;
    @FXML private TextField correo;
    @FXML private TextField correoBoletas;

    @FXML private ImageView logoVista;
    @FXML private Label logoVacio;
    @FXML private Button quitarLogo;
    @FXML private ImageView firmaVista;
    @FXML private Label firmaVacia;
    @FXML private Button quitarFirma;
    @FXML private CheckBox firmaDigitalActiva;
    @FXML private CheckBox huellaEnBoleta;

    public RonaldmichaEmpresaController(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        regimenTributario.getItems().setAll("Régimen General", "Régimen MYPE Tributario",
                "Régimen Especial de Renta", "Nuevo RUS");
        regimenLaboral.getItems().setAll(RonaldmichaRegimenLaboral.values());
        regimenLaboral.setConverter(new StringConverter<>() {
            @Override
            public String toString(RonaldmichaRegimenLaboral r) {
                return r == null ? "" : r.descripcion();
            }

            @Override
            public RonaldmichaRegimenLaboral fromString(String s) {
                return null;
            }
        });
        periodicidad.getItems().setAll("Mensual", "Quincenal", "Semanal");
        repTipoDoc.getItems().setAll("DNI", "CE", "PAS");
        inicioActividades.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate fecha) {
                return RonaldmichaFormatos.fecha(fecha);
            }

            @Override
            public LocalDate fromString(String texto) {
                try {
                    return texto == null || texto.isBlank() ? null : RonaldmichaFormatos.leerFecha(texto);
                } catch (DateTimeParseException e) {
                    return inicioActividades.getValue();
                }
            }
        });

        cargar(contexto.empresa().obtener());

        ChangeListener<Object> cambio = (o, a, b) -> actualizarEstado();
        for (TextField campo : List.of(ruc, razonSocial, nombreComercial, ciiu, direccion, departamento, provincia,
                distrito, ubigeo, ciudad, registroRemype, eps, diaPago, repNombre, repDoc, repCargo,
                partidaRegistral, telefono, correo, correoBoletas, rubro)) {
            campo.textProperty().addListener(cambio);
        }
        regimenLaboral.valueProperty().addListener(cambio);
        ocultarErrores();
        actualizarEstado();
        configurarValidacion();
    }

    private void configurarValidacion() {
        validacion
                .regla(ruc, () -> RonaldmichaRuc.error(ruc.getText()).orElse(null), ruc.textProperty())
                .regla(razonSocial, () -> vacio(razonSocial.getText()) ? "Ingrese la razón social." : null,
                        razonSocial.textProperty())
                .regla(registroRemype, () -> regimenLaboral.getValue() != null
                        && formulario().requiereRemype() && vacio(registroRemype.getText())
                        ? "Obligatorio para " + regimenLaboral.getValue().descripcion().toLowerCase(RonaldmichaFormatos.LOCALE_PE)
                        + "." : null, registroRemype.textProperty(), regimenLaboral.valueProperty())
                .regla(ubigeo, () -> vacio(ubigeo.getText()) || ubigeo.getText().trim().matches("\\d{6}") ? null
                        : "El ubigeo tiene 6 dígitos.", ubigeo.textProperty())
                .regla(diaPago, () -> {
                    if (vacio(diaPago.getText())) {
                        return null;
                    }
                    Integer d = entero(diaPago.getText());
                    return d != null && d >= 1 && d <= 31 ? null : "Entre 1 y 31.";
                }, diaPago.textProperty())
                .regla(correo, () -> correoValido(correo.getText()) ? null : "El correo no es válido.",
                        correo.textProperty())
                .regla(correoBoletas, () -> correoValido(correoBoletas.getText()) ? null : "El correo no es válido.",
                        correoBoletas.textProperty());
        botonGuardar.disableProperty().bind(validacion.validoProperty().not());
    }

    private static boolean correoValido(String s) {
        return vacio(s) || s.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private void cargar(RonaldmichaEmpresa e) {
        id = e.id();
        ruc.setText(e.ruc());
        razonSocial.setText(e.razonSocial());
        nombreComercial.setText(e.nombreComercial());
        regimenTributario.setValue(e.regimenTributario());
        ciiu.setText(e.ciiu());
        inicioActividades.setValue(e.inicioActividades());
        direccion.setText(e.direccion());
        departamento.setText(e.departamento());
        provincia.setText(e.provincia());
        distrito.setText(e.distrito());
        ubigeo.setText(e.ubigeo());
        ciudad.setText(e.ciudad());
        regimenLaboral.setValue(e.regimenLaboral());
        registroRemype.setText(e.registroRemype());
        eps.setText(e.eps());
        periodicidad.setValue(e.periodicidad());
        diaPago.setText(e.diaPago() == null ? "" : e.diaPago().toString());
        sctr.setSelected(e.sctr());
        repNombre.setText(e.repNombre());
        repTipoDoc.setValue(e.repTipoDoc() == null ? "DNI" : e.repTipoDoc());
        repDoc.setText(e.repDoc());
        repCargo.setText(e.repCargo());
        partidaRegistral.setText(e.partidaRegistral());
        telefono.setText(e.telefono());
        correo.setText(e.correo());
        correoBoletas.setText(e.correoBoletas());
        rubro.setText(e.rubro());
        firmaDigitalActiva.setSelected(e.firmaDigitalActiva());
        huellaEnBoleta.setSelected(e.huellaEnBoleta());
        logo = e.logo();
        firma = e.firma();
        mostrarImagen(logo, logoVista, logoVacio, quitarLogo);
        mostrarImagen(firma, firmaVista, firmaVacia, quitarFirma);
    }

    private RonaldmichaEmpresa formulario() {
        return new RonaldmichaEmpresa(id, ruc.getText(), razonSocial.getText(), nombreComercial.getText(),
                texto(regimenTributario), ciiu.getText(), inicioActividades.getValue(), direccion.getText(),
                departamento.getText(), provincia.getText(), distrito.getText(), ubigeo.getText(), ciudad.getText(),
                regimenLaboral.getValue(), registroRemype.getText(), eps.getText(), periodicidad.getValue(),
                entero(diaPago.getText()), sctr.isSelected(), repNombre.getText(), repTipoDoc.getValue(),
                repDoc.getText(), repCargo.getText(), partidaRegistral.getText(), telefono.getText(),
                correo.getText(), correoBoletas.getText(), logo, firma, rubro.getText(), firmaDigitalActiva.isSelected(),
                huellaEnBoleta.isSelected());
    }

    private void actualizarEstado() {
        RonaldmichaEmpresa e = formulario();

        String texto = ruc.getText();
        if (texto == null || texto.isBlank()) {
            rucMensaje.setText("");
        } else {
            rucMensaje.setText(RonaldmichaRuc.esValido(texto) ? "RUC válido" : "");
            rucMensaje.getStyleClass().removeAll("mensaje-ok", "mensaje-error");
            rucMensaje.getStyleClass().add("mensaje-ok");
        }

        etiquetaRemype.setText(e.regimenLaboral() != null && e.requiereRemype() ? "N° REMYPE *" : "N° REMYPE");

        List<String> faltan = contexto.empresa().faltantes(e);
        chipCompletitud.getStyleClass().removeAll("success", "warning");
        if (faltan.isEmpty()) {
            chipCompletitud.setText("Datos completos");
            chipCompletitud.getStyleClass().add("success");
            textoFaltantes.setText("");
        } else {
            chipCompletitud.setText("Incompleto");
            chipCompletitud.getStyleClass().add("warning");
            textoFaltantes.setText("Faltan: " + String.join(", ", faltan));
        }
    }

    @FXML
    private void guardar() {
        try {
            RonaldmichaEmpresa guardada = contexto.empresa().guardar(formulario(), contexto.sesion());
            id = guardada.id();
            ocultarErrores();
            RonaldmichaToast.exito(botonGuardar, "Datos de la empresa guardados.");
        } catch (RonaldmichaValidacionException e) {
            erroresGuardado.setText(e.errores().stream().map(m -> "• " + m).collect(Collectors.joining("\n")));
            erroresGuardado.setVisible(true);
            erroresGuardado.setManaged(true);
            RonaldmichaToast.error(botonGuardar, "Revise los datos: no se guardaron los cambios.");
        } catch (RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(botonGuardar, e.getMessage());
        }
    }

    @FXML
    private void vistaPreviaPdf() {
        RonaldmichaEmpresa datos = formulario();
        RonaldmichaArchivos.generarYGuardar(botonGuardar, "membrete-empresa.pdf", "PDF (*.pdf)", "*.pdf",
                () -> contexto.empresa().vistaPreviaPdf(datos));
    }

    @FXML
    private void cargarLogo() {
        elegirImagen("Elegir logo", bytes -> {
            logo = bytes;
            mostrarImagen(logo, logoVista, logoVacio, quitarLogo);
        });
    }

    @FXML
    private void quitarLogo() {
        logo = null;
        mostrarImagen(null, logoVista, logoVacio, quitarLogo);
        actualizarEstado();
    }

    @FXML
    private void cargarFirma() {
        elegirImagen("Elegir firma", bytes -> {
            firma = bytes;
            mostrarImagen(firma, firmaVista, firmaVacia, quitarFirma);
        });
    }

    @FXML
    private void quitarFirma() {
        firma = null;
        mostrarImagen(null, firmaVista, firmaVacia, quitarFirma);
        actualizarEstado();
    }

    private void elegirImagen(String titulo, Consumer<byte[]> alElegir) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(titulo);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes PNG o JPG", "*.png", "*.jpg", "*.jpeg"));
        File archivo = chooser.showOpenDialog(botonGuardar.getScene().getWindow());
        if (archivo == null) {
            return;
        }
        try {
            if (archivo.length() > RonaldmichaEmpresaService.MAX_IMAGEN_BYTES) {
                RonaldmichaToast.error(botonGuardar, "La imagen pesa más de 2 MB.");
                return;
            }
            byte[] bytes = Files.readAllBytes(archivo.toPath());
            if (new Image(new ByteArrayInputStream(bytes)).isError()) {
                RonaldmichaToast.error(botonGuardar, "El archivo no es una imagen PNG o JPG válida.");
                return;
            }
            alElegir.accept(bytes);
            actualizarEstado();
        } catch (IOException e) {
            RonaldmichaToast.error(botonGuardar, "No se pudo leer la imagen: " + e.getMessage());
        }
    }

    private static void mostrarImagen(byte[] bytes, ImageView vista, Label vacio, Button quitar) {
        boolean hay = bytes != null && bytes.length > 0;
        vista.setImage(hay ? new Image(new ByteArrayInputStream(bytes)) : null);
        vacio.setVisible(!hay);
        quitar.setDisable(!hay);
    }

    private void ocultarErrores() {
        erroresGuardado.setVisible(false);
        erroresGuardado.setManaged(false);
    }

    private static String texto(ComboBox<String> combo) {
        return combo.isEditable() ? combo.getEditor().getText() : combo.getValue();
    }

    private static Integer entero(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(texto.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
