package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.calc.RonaldmichaImpactoParametrosCalculator.Impacto;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaAfpComision;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaTrabajadorRemuneracion;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaConfirmButton;
import com.planillaperu.ui.components.RonaldmichaEmptyState;
import com.planillaperu.ui.components.RonaldmichaIcono;
import com.planillaperu.ui.components.RonaldmichaModal;
import com.planillaperu.ui.components.RonaldmichaMoneyCell;
import com.planillaperu.ui.components.RonaldmichaToast;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class RonaldmichaParametrosController {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy");

    private final RonaldmichaAppContext contexto;

    @FXML private Button botonNueva;
    @FXML private Button botonAnular;
    @FXML private TableView<RonaldmichaParametrosVersion> tabla;
    @FXML private TableColumn<RonaldmichaParametrosVersion, String> colDesde;
    @FXML private TableColumn<RonaldmichaParametrosVersion, String> colNorma;
    @FXML private TableColumn<RonaldmichaParametrosVersion, BigDecimal> colRmv;
    @FXML private TableColumn<RonaldmichaParametrosVersion, BigDecimal> colUit;
    @FXML private TableColumn<RonaldmichaParametrosVersion, String> colTasas;
    @FXML private TableColumn<RonaldmichaParametrosVersion, RonaldmichaParametrosVersion> colEstado;
    @FXML private TableColumn<RonaldmichaParametrosVersion, String> colRegistro;

    @FXML private VBox detalle;
    @FXML private Label tituloDetalle;
    @FXML private Label textoDetalle;
    @FXML private Label textoAnulacion;
    @FXML private TableView<RonaldmichaAfpComision> tablaComisiones;
    @FXML private TableColumn<RonaldmichaAfpComision, String> colAfp;
    @FXML private TableColumn<RonaldmichaAfpComision, String> colFlujo;
    @FXML private TableColumn<RonaldmichaAfpComision, String> colMixta;

    public RonaldmichaParametrosController(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        tabla.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.AJUSTES, "Sin versiones de parámetros",
                "Registre la RMV, la UIT y las tasas vigentes para calcular la planilla.")
                .conAccion("Nueva versión", this::nuevaVersion));
        tablaComisiones.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.AJUSTES, "Sin comisiones AFP",
                "Elija una versión para ver sus comisiones de flujo y mixta."));
        colDesde.setCellValueFactory(c -> new ReadOnlyStringWrapper(MES.format(c.getValue().vigenteDesde())));
        colDesde.getStyleClass().add("num");
        colNorma.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().norma()));
        colRmv.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().rmv()));
        colRmv.setCellFactory(RonaldmichaMoneyCell.fabrica());
        colUit.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().uit()));
        colUit.setCellFactory(RonaldmichaMoneyCell.fabrica());
        colTasas.setCellValueFactory(c -> new ReadOnlyStringWrapper(resumenTasas(c.getValue())));
        colEstado.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colEstado.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(RonaldmichaParametrosVersion v, boolean vacio) {
                super.updateItem(v, vacio);
                if (vacio || v == null) {
                    setGraphic(null);
                    return;
                }
                Label chip = new Label(v.estado().etiqueta());
                chip.getStyleClass().addAll("chip", v.vigente() ? "success" : "error");
                setGraphic(chip);
            }
        });
        colRegistro.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().registradoPor() + " · " + RonaldmichaFormatos.fecha(c.getValue().fechaRegistro().toLocalDate())));

        colAfp.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().afp()));
        colFlujo.setCellValueFactory(c -> new ReadOnlyStringWrapper(tasa(c.getValue().comisionFlujoPct())));
        colFlujo.getStyleClass().add("num");
        colMixta.setCellValueFactory(c -> new ReadOnlyStringWrapper(tasa(c.getValue().comisionMixtaPct())));
        colMixta.getStyleClass().add("num");

        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, v) -> mostrarDetalle(v));
        recargar(null);
    }

    private void recargar(Long seleccionarId) {
        List<RonaldmichaParametrosVersion> versiones = contexto.parametros().listar();
        tabla.setItems(FXCollections.observableList(versiones));
        RonaldmichaParametrosVersion elegido = versiones.stream()
                .filter(v -> seleccionarId != null && v.id().equals(seleccionarId))
                .findFirst()
                .orElse(versiones.isEmpty() ? null : versiones.getFirst());
        tabla.getSelectionModel().select(elegido);
        mostrarDetalle(elegido);
    }

    private void mostrarDetalle(RonaldmichaParametrosVersion v) {
        detalle.setVisible(v != null);
        detalle.setManaged(v != null);
        botonAnular.setDisable(v == null || !v.vigente());
        if (v == null) {
            return;
        }
        tituloDetalle.setText("Versión vigente desde " + MES.format(v.vigenteDesde()));
        textoDetalle.setText("RMV " + RonaldmichaFormatos.moneda(v.rmv()) + " · UIT " + RonaldmichaFormatos.moneda(v.uit())
                + " · Rem. máx. asegurable " + RonaldmichaFormatos.moneda(v.rmaPrima()) + " · SIS " + RonaldmichaFormatos.moneda(v.sisMonto())
                + "\nAsignación familiar " + pct(v.asigFamiliarPct()) + " · EsSalud " + pct(v.essaludPct())
                + " · EPS " + pct(v.epsPct()) + " · ONP " + pct(v.onpPct())
                + "\nAporte AFP " + pct(v.afpAportePct()) + " · Prima AFP " + pct(v.afpPrimaPct())
                + " · Bonif. extraordinaria " + pct(v.bonifExtPct()) + " (con EPS " + pct(v.bonifExtEpsPct()) + ")"
                + " · Vida Ley " + pct(v.vidaLeyPct()));
        boolean anulada = !v.vigente();
        textoAnulacion.setVisible(anulada);
        textoAnulacion.setManaged(anulada);
        if (anulada) {
            textoAnulacion.setText("Anulada por " + v.anuladoPor() + " el "
                    + RonaldmichaFormatos.fechaHora(v.fechaAnulacion()) + ". Motivo: " + v.motivoAnulacion());
        }
        tablaComisiones.setItems(FXCollections.observableList(v.comisiones()));
    }

    @FXML
    private void nuevaVersion() {
        YearMonth siguiente = contexto.periodos().mesActual().plusMonths(1);
        RonaldmichaParametrosVersion base = contexto.parametros().listar().stream()
                .filter(RonaldmichaParametrosVersion::vigente)
                .findFirst()
                .orElse(null);
        RonaldmichaParametrosFormulario formulario = new RonaldmichaParametrosFormulario(base, siguiente);
        VBox impacto = new VBox(8);

        ScrollPane desplazable = new ScrollPane(new VBox(16, formulario, impacto));
        desplazable.setFitToWidth(true);
        desplazable.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        desplazable.setPrefViewportHeight(520);

        Button revisar = new Button("Revisar impacto");
        revisar.getStyleClass().add("btn");
        Button guardar = new Button("Guardar versión");
        guardar.getStyleClass().addAll("btn", "btn-primary");
        guardar.setDisable(true);

        RonaldmichaModal modal = new RonaldmichaModal("Nueva versión de parámetros", desplazable).ancho(820);
        formulario.alCambiar(() -> guardar.setDisable(true));

        revisar.setOnAction(e -> {
            RonaldmichaParametrosVersion borrador = formulario.leer();
            List<String> errores = contexto.parametros().validar(borrador);
            impacto.getChildren().clear();
            if (!errores.isEmpty()) {
                Label error = new Label(errores.stream().map(m -> "• " + m).collect(Collectors.joining("\n")));
                error.getStyleClass().addAll("alert", "error");
                error.setWrapText(true);
                error.setMaxWidth(Double.MAX_VALUE);
                impacto.getChildren().add(error);
                guardar.setDisable(true);
            } else {
                impacto.getChildren().add(vistaImpacto(borrador, contexto.parametros().impacto(borrador)));
                guardar.setDisable(false);
            }
            desplazable.layout();
            desplazable.setVvalue(1);
        });

        guardar.setOnAction(e -> {
            try {
                RonaldmichaParametrosVersion nueva = contexto.parametros().registrar(formulario.leer(), contexto.sesion());
                modal.cerrar();
                recargar(nueva.id());
                RonaldmichaToast.exito(tabla, "Versión vigente desde " + MES.format(nueva.vigenteDesde()) + " registrada.");
            } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException ex) {
                RonaldmichaToast.error(tabla, ex.getMessage());
            }
        });

        modal.agregarAccion(revisar).agregarAccion(guardar).mostrar(tabla);
    }

    private VBox vistaImpacto(RonaldmichaParametrosVersion nueva, Impacto impacto) {
        VBox caja = new VBox(8);
        caja.getStyleClass().addAll("alert", "info");

        Label titulo = new Label("Vista previa del impacto desde " + MES.format(nueva.vigenteDesde()));
        titulo.getStyleClass().add("title-md");
        caja.getChildren().add(titulo);

        RonaldmichaParametrosVersion anterior = impacto.anterior();
        if (anterior != null) {
            caja.getChildren().add(linea("RMV: " + RonaldmichaFormatos.moneda(anterior.rmv()) + " → " + RonaldmichaFormatos.moneda(nueva.rmv())
                    + "   ·   UIT: " + RonaldmichaFormatos.moneda(anterior.uit()) + " → " + RonaldmichaFormatos.moneda(nueva.uit())));
        }

        List<RonaldmichaTrabajadorRemuneracion> bajo = impacto.bajoNuevaRmv();
        if (bajo.isEmpty()) {
            caja.getChildren().add(linea("Ningún trabajador activo gana menos que la nueva RMV."));
        } else {
            caja.getChildren().add(linea(bajo.size() + (bajo.size() == 1 ? " trabajador gana" : " trabajadores ganan")
                    + " menos que la nueva RMV de " + RonaldmichaFormatos.moneda(nueva.rmv()) + ":"));
            for (RonaldmichaTrabajadorRemuneracion t : bajo) {
                caja.getChildren().add(linea("   • " + t.trabajador().apellidos() + ", " + t.trabajador().nombres()
                        + " — " + RonaldmichaFormatos.moneda(t.sueldo()) + " · " + t.trabajador().regimen().descripcion()));
            }
        }

        caja.getChildren().add(linea("Asignación familiar: " + RonaldmichaFormatos.moneda(impacto.asignacionAnterior()) + " → "
                + RonaldmichaFormatos.moneda(impacto.asignacionNueva()) + " por trabajador. " + impacto.conAsignacion()
                + " trabajadores la reciben: " + (impacto.diferenciaMensual().signum() >= 0 ? "+" : "")
                + RonaldmichaFormatos.moneda(impacto.diferenciaMensual()) + " al mes en total."));
        caja.getChildren().add(linea("Los periodos anteriores a " + MES.format(nueva.vigenteDesde())
                + " seguirán calculándose con sus parámetros."));
        return caja;
    }

    @FXML
    private void anular() {
        RonaldmichaParametrosVersion v = tabla.getSelectionModel().getSelectedItem();
        if (v == null || !v.vigente()) {
            return;
        }
        TextArea motivo = new TextArea();
        motivo.setPromptText("Motivo de la anulación (obligatorio)");
        motivo.setPrefRowCount(3);
        motivo.setWrapText(true);
        Label explicacion = new Label("La versión no se borra: queda en el historial como Anulada y los periodos "
                + "desde " + MES.format(v.vigenteDesde()) + " pasarán a usar la versión vigente anterior.");
        explicacion.setWrapText(true);
        explicacion.getStyleClass().add("text-muted");

        RonaldmichaConfirmButton confirmar = new RonaldmichaConfirmButton("Anular versión");
        RonaldmichaModal modal = new RonaldmichaModal("Anular versión desde " + MES.format(v.vigenteDesde()),
                new VBox(12, explicacion, motivo)).ancho(480);
        confirmar.setAccion(() -> {
            try {
                contexto.parametros().anular(v.id(), motivo.getText(), contexto.sesion());
                modal.cerrar();
                recargar(v.id());
                RonaldmichaToast.exito(tabla, "Versión desde " + MES.format(v.vigenteDesde()) + " anulada.");
            } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException ex) {
                RonaldmichaToast.error(tabla, ex.getMessage());
            }
        });
        modal.agregarAccion(confirmar).mostrar(tabla);
    }

    private static String resumenTasas(RonaldmichaParametrosVersion v) {
        return "EsSalud " + pct(v.essaludPct()) + " · ONP " + pct(v.onpPct())
                + " · AFP " + pct(v.afpAportePct()) + " + " + pct(v.afpPrimaPct())
                + " · Asig. fam. " + pct(v.asigFamiliarPct());
    }

    private static String pct(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString() + " %";
    }

    private static String tasa(BigDecimal valor) {
        return RonaldmichaFormatos.numero(valor) + " %";
    }

    private static Label linea(String texto) {
        Label l = new Label(texto);
        l.setWrapText(true);
        l.getStyleClass().add("texto-info");
        return l;
    }
}
