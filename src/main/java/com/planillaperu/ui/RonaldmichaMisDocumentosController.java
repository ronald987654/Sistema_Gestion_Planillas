package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaDocumentoEmitido;
import com.planillaperu.domain.RonaldmichaEstadoDocumento;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaReporte;
import com.planillaperu.service.RonaldmichaReportesService.BoletaPortal;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaChip;
import com.planillaperu.ui.components.RonaldmichaEmptyState;
import com.planillaperu.ui.components.RonaldmichaIcono;
import com.planillaperu.ui.components.RonaldmichaToast;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.File;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RonaldmichaMisDocumentosController {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM yyyy", RonaldmichaFormatos.LOCALE_PE);
    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("MM/yyyy");

    private final RonaldmichaAppContext contexto;
    private final ObservableList<BoletaPortal> boletas = FXCollections.observableArrayList();
    private final ObservableList<RonaldmichaDocumentoEmitido> historial = FXCollections.observableArrayList();
    private final TableView<BoletaPortal> tablaBoletas = new TableView<>(boletas);
    private final TableView<RonaldmichaDocumentoEmitido> tablaHistorial = new TableView<>(historial);
    private Set<YearMonth> disponibles = Set.of();

    @FXML private VBox raiz;

    public RonaldmichaMisDocumentosController(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        Label aviso = new Label("Las boletas se publican cuando se cierra la planilla del mes. «Confirmar recepción» "
                + "es su cargo electrónico de entrega (D.S. 009-2011-TR) y queda registrado con fecha y hora.");
        aviso.getStyleClass().addAll("alert", "info");
        aviso.setWrapText(true);
        aviso.setMaxWidth(Double.MAX_VALUE);

        configurarBoletas();
        configurarHistorial();
        raiz.getChildren().setAll(aviso, tarjeta("Boletas de pago", tablaBoletas),
                tarjeta("Historial de documentos", tablaHistorial));
        cargar();
    }

    private void cargar() {
        try {
            boletas.setAll(contexto.reportes().misBoletas(contexto.sesion()));
            disponibles = boletas.stream().filter(BoletaPortal::disponible).map(BoletaPortal::mes)
                    .collect(Collectors.toSet());
            historial.setAll(contexto.reportes().emitidos(contexto.sesion()));
        } catch (RonaldmichaAccesoDenegadoException | RonaldmichaValidacionException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
        }
    }

    private void configurarBoletas() {
        tablaBoletas.getStyleClass().add("tabla-boletas");
        tablaBoletas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaBoletas.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.CARPETA, "Sin boletas", "Aún no tiene meses en planilla."));
        TableColumn<BoletaPortal, String> mes = texto("Periodo", 150, b -> capital(MES.format(b.mes())));
        TableColumn<BoletaPortal, BoletaPortal> estado = celda("Estado", 170, (b, cel) -> chip(b.disponible()
                ? "Disponible" : "Aún no disponible", b.disponible() ? "success" : "neutral"));
        TableColumn<BoletaPortal, String> numero = texto("Número", 150,
                b -> b.documento().map(RonaldmichaDocumentoEmitido::numero).orElse("—"));
        numero.getStyleClass().add("mono");
        TableColumn<BoletaPortal, BoletaPortal> recepcion = celda("Recepción", 190,
                (b, cel) -> b.documento().filter(d -> b.disponible()).map(RonaldmichaMisDocumentosController::recepcion).orElse(null));
        TableColumn<BoletaPortal, BoletaPortal> acciones = celda("", 300, (b, cel) -> accionesBoleta(b));
        List<TableColumn<BoletaPortal, ?>> columnas = List.of(mes, estado, numero, recepcion, acciones);
        columnas.forEach(c -> c.setSortable(false));
        tablaBoletas.getColumns().setAll(columnas);
    }

    private Node accionesBoleta(BoletaPortal b) {
        if (!b.disponible()) {
            Label l = new Label(b.situacion().etiqueta() + ": se publica al cerrar la planilla");
            l.getStyleClass().add("text-muted");
            return l;
        }
        HBox caja = new HBox(8, boton("Descargar PDF", "btn-pdf", () -> descargar(b.mes())));
        b.documento().filter(RonaldmichaDocumentoEmitido::cargoPendiente)
                .ifPresent(d -> caja.getChildren().add(boton("Confirmar recepción", null, () -> confirmar(d))));
        return caja;
    }

    private void descargar(YearMonth mes) {
        RonaldmichaTareas.ejecutar(() -> contexto.reportes().descargarMiBoleta(contexto.sesion(), mes), archivo -> {
            cargar();
            File destino = RonaldmichaArchivos.guardar(raiz, archivo.nombre(), "PDF (*.pdf)", "*.pdf", archivo.contenido());
            if (destino != null) {
                RonaldmichaToast.archivo(raiz, destino.toPath());
            }
        }, e -> RonaldmichaToast.error(raiz, e instanceof RonaldmichaValidacionException || e instanceof RonaldmichaAccesoDenegadoException
                ? e.getMessage() : "No se pudo generar la boleta: " + RonaldmichaTareas.mensaje(e)));
    }

    private void confirmar(RonaldmichaDocumentoEmitido d) {
        try {
            RonaldmichaDocumentoEmitido recibido = contexto.reportes().confirmarRecepcion(contexto.sesion(), d.id());
            cargar();
            RonaldmichaToast.exito(raiz, "Recepción de " + recibido.numero() + " confirmada el "
                    + RonaldmichaFormatos.fechaHora(recibido.fechaRecepcion()) + ".");
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
        }
    }

    private void configurarHistorial() {
        tablaHistorial.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaHistorial.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.CARPETA, "Sin documentos",
                "Aquí aparecen las boletas, constancias y certificados que se le emitan."));
        TableColumn<RonaldmichaDocumentoEmitido, String> numero = texto("Número", 150, RonaldmichaDocumentoEmitido::numero);
        numero.getStyleClass().add("mono");
        List<TableColumn<RonaldmichaDocumentoEmitido, ?>> columnas = List.of(numero,
                texto("Documento", 220, d -> {
                    RonaldmichaReporte r = RonaldmichaReporte.desdeTipo(d.tipo());
                    return r == null ? d.tipo() : r.titulo();
                }),
                texto("Periodo", 90, d -> d.periodo() == null ? "" : PERIODO.format(d.periodo())),
                texto("Emitido", 130, d -> RonaldmichaFormatos.fechaHora(d.fecha())),
                celda("Estado", 110, (d, cel) -> chip(d.estado().etiqueta(), d.estado().estilo())),
                celda("Recepción", 200, (d, cel) -> d.requiereCargo() ? recepcion(d) : null),
                celda("", 170, (d, cel) -> puedeConfirmar(d) ? boton("Confirmar recepción", "btn-sm", () -> confirmar(d))
                        : null));
        columnas.forEach(c -> c.setSortable(false));
        tablaHistorial.getColumns().setAll(columnas);
    }

    private boolean puedeConfirmar(RonaldmichaDocumentoEmitido d) {
        boolean boleta = RonaldmichaReporte.BOLETA_PAGO.tipo().equals(d.tipo());
        return d.cargoPendiente() && (!boleta || disponibles.contains(d.periodo()));
    }

    private static Node recepcion(RonaldmichaDocumentoEmitido d) {
        if (d.estado() == RonaldmichaEstadoDocumento.RECIBIDO) {
            Label l = chip("Recibido" + (d.fechaRecepcion() == null ? "" : " " + RonaldmichaFormatos.fechaHora(d.fechaRecepcion())),
                    "success");
            if (d.recepcion() != null) {
                l.setTooltip(new javafx.scene.control.Tooltip(d.recepcion()));
            }
            return l;
        }
        return chip("Por confirmar", "warning");
    }

    private static VBox tarjeta(String titulo, Node contenido) {
        Label t = new Label(titulo);
        t.getStyleClass().add("title-md");
        VBox caja = new VBox(10, t, contenido);
        caja.getStyleClass().add("card");
        return caja;
    }

    private static Label chip(String texto, String estilo) {
        return RonaldmichaChip.de(texto, estilo);
    }

    private static <T> TableColumn<T, String> texto(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(valor.apply(c.getValue())));
        return col;
    }

    private static <T> TableColumn<T, T> celda(String titulo, double ancho,
                                               java.util.function.BiFunction<T, TableCell<T, T>, Node> grafico) {
        TableColumn<T, T> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean vacio) {
                super.updateItem(item, vacio);
                setText(null);
                setGraphic(vacio || item == null ? null : grafico.apply(item, this));
            }
        });
        return col;
    }

    private static Button boton(String texto, String estilo, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().add("btn");
        if (estilo != null) {
            b.getStyleClass().add(estilo);
        }
        if (!"btn-sm".equals(estilo)) {
            b.getStyleClass().add("btn-sm");
        }
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> accion.run());
        return b;
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + s.substring(1);
    }
}
