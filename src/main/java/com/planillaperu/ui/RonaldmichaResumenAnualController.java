package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaResumenAnualService;
import com.planillaperu.service.RonaldmichaResumenAnualService.Concepto;
import com.planillaperu.service.RonaldmichaResumenAnualService.FilaAnual;
import com.planillaperu.service.RonaldmichaResumenAnualService.Modo;
import com.planillaperu.service.RonaldmichaResumenAnualService.Resumen;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaExportButton;
import com.planillaperu.ui.components.RonaldmichaEmptyState;
import com.planillaperu.ui.components.RonaldmichaIcono;
import com.planillaperu.ui.components.RonaldmichaToast;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class RonaldmichaResumenAnualController {

    private record Linea(String codigo, String nombre, Function<YearMonth, BigDecimal> valor, BigDecimal total,
                         boolean esTotal) {
    }

    private final RonaldmichaAppContext contexto;
    private final ComboBox<Integer> anio = new ComboBox<>();
    private final ChangeListener<YearMonth> alCambiarPeriodo = (o, a, periodo) -> {
        if (periodo != null && anio.getItems().contains(periodo.getYear())) {
            anio.setValue(periodo.getYear());
        }
    };
    private final ToggleGroup modo = new ToggleGroup();
    private RonaldmichaExportButton botonExportar;
    private final ObservableList<Linea> lineas = FXCollections.observableArrayList();
    private final TableView<Linea> tabla = new TableView<>(lineas);
    private final ObservableList<Concepto> conceptos = FXCollections.observableArrayList();
    private final TableView<Concepto> tablaConceptos = new TableView<>(conceptos);
    private final Label observaciones = new Label();
    private final Label tituloTabla = new Label();
    private Resumen resumen;

    @FXML private VBox raiz;

    public RonaldmichaResumenAnualController(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        RonaldmichaAtajos.registrarExportar(this::exportar);
        anio.getItems().setAll(contexto.resumenAnual().anios());
        int elegido = contexto.periodoProperty().get().getYear();
        anio.setValue(anio.getItems().contains(elegido) ? elegido : anio.getItems().getFirst());
        anio.valueProperty().addListener((o, a, b) -> cargar());
        contexto.periodoProperty().addListener(new WeakChangeListener<>(alCambiarPeriodo));

        HBox selector = new HBox();
        selector.getStyleClass().add("segmented");
        for (Modo m : Modo.values()) {
            ToggleButton b = new ToggleButton(m.etiqueta());
            b.setUserData(m);
            b.setToggleGroup(modo);
            selector.getChildren().add(b);
        }
        modo.selectToggle(modo.getToggles().getFirst());
        modo.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                modo.selectToggle(a);
            } else {
                mostrarTabla();
            }
        });

        Label leyendaGrati = new Label("Julio y diciembre: gratificación");
        leyendaGrati.getStyleClass().addAll("chip", "leyenda-grati");
        Label leyendaFuturo = new Label("Mes futuro");
        leyendaFuturo.getStyleClass().addAll("chip", "leyenda-futuro");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        Integer[] anioExportado = new Integer[1];
        botonExportar = new RonaldmichaExportButton("Exportar Excel", RonaldmichaExportButton.Formato.EXCEL, () -> new RonaldmichaExportButton.Archivo(
                "resumen_anual_" + anioExportado[0] + ".xlsx",
                contexto.resumenAnual().exportarExcel(contexto.sesion(), anioExportado[0])));
        botonExportar.conPrevio(continuar -> {
            anioExportado[0] = anio.getValue();
            continuar.run();
        });
        botonExportar.setGraphic(RonaldmichaIcono.DESCARGA.nodo(16));
        Button exportar = botonExportar;
        HBox barra = new HBox(12, etiqueta("Año"), anio, selector, leyendaGrati, leyendaFuturo, espacio, exportar);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.getStyleClass().addAll("card", "barra-resumen-anual");

        observaciones.getStyleClass().addAll("alert", "warning");
        observaciones.setWrapText(true);
        observaciones.setMaxWidth(Double.MAX_VALUE);

        tabla.getStyleClass().add("tabla-resumen-anual");
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tabla.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.CALENDARIO, "Sin planillas", "No hay trabajadores en planilla este año.")
                .conAccion("Ir a Planilla mensual", () -> RonaldmichaNavegacion.ir(RonaldmichaVista.PLANILLA_MENSUAL)));
        tabla.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Linea l, boolean vacio) {
                super.updateItem(l, vacio);
                getStyleClass().remove("fila-total");
                if (!vacio && l != null && l.esTotal()) {
                    getStyleClass().add("fila-total");
                }
            }
        });
        tituloTabla.getStyleClass().add("title-md");
        Label notaNeto = new Label("Neto pagado: neto de la planilla más la gratificación de julio y diciembre. Costo "
                + "laboral: el de la planilla, que ya incluye las provisiones de gratificación, CTS y vacaciones.");
        notaNeto.getStyleClass().add("text-muted");
        notaNeto.setWrapText(true);
        VBox tarjetaTabla = new VBox(10, tituloTabla, tabla, notaNeto);
        tarjetaTabla.getStyleClass().add("card");

        tablaConceptos.getStyleClass().add("tabla-conceptos-anual");
        tablaConceptos.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tablaConceptos.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.CALENDARIO, "Sin conceptos",
                "Los conceptos aparecen cuando hay planillas en el año."));
        Label tituloConceptos = new Label("Resumen por concepto");
        tituloConceptos.getStyleClass().add("title-md");
        VBox tarjetaConceptos = new VBox(10, tituloConceptos, tablaConceptos);
        tarjetaConceptos.getStyleClass().add("card");

        raiz.getChildren().setAll(barra, observaciones, tarjetaTabla, tarjetaConceptos);
        cargar();
    }

    private void cargar() {
        try {
            resumen = contexto.resumenAnual().consultar(contexto.sesion(), anio.getValue());
        } catch (RonaldmichaAccesoDenegadoException | RonaldmichaValidacionException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
            return;
        }
        observaciones.setText(String.join("\n", resumen.observaciones()));
        observaciones.setVisible(!resumen.observaciones().isEmpty());
        observaciones.setManaged(observaciones.isVisible());
        mostrarTabla();
        mostrarConceptos();
    }

    private Modo modo() {
        return (Modo) modo.getSelectedToggle().getUserData();
    }

    private void mostrarTabla() {
        if (resumen == null) {
            return;
        }
        Modo m = modo();
        tituloTabla.setText(m.etiqueta() + " por trabajador · " + resumen.anio());
        List<TableColumn<Linea, ?>> columnas = new ArrayList<>();
        TableColumn<Linea, String> codigo = texto("Código", 70, Linea::codigo);
        codigo.getStyleClass().add("mono");
        columnas.add(codigo);
        columnas.add(texto("Trabajador", 230, Linea::nombre));
        for (YearMonth mes : resumen.meses()) {
            columnas.add(columnaMes(mes, l -> l.valor().apply(mes)));
        }
        TableColumn<Linea, BigDecimal> total = monto("Total", 120, Linea::total);
        total.getStyleClass().add("col-total");
        columnas.add(total);
        columnas.forEach(c -> c.setSortable(false));
        tabla.getColumns().setAll(columnas);

        List<Linea> filas = new ArrayList<>();
        for (FilaAnual f : resumen.filas()) {
            filas.add(new Linea(f.trabajador().codigo(), f.trabajador().nombreCompleto(), mes -> f.valor(mes, m),
                    f.total(m), false));
        }
        if (!filas.isEmpty()) {
            filas.add(new Linea("", "Total", mes -> resumen.totalMes(mes, m), resumen.total(m), true));
        }
        lineas.setAll(filas);
    }

    private void mostrarConceptos() {
        List<TableColumn<Concepto, ?>> columnas = new ArrayList<>();
        TableColumn<Concepto, Concepto> nombre = new TableColumn<>("Concepto");
        nombre.setPrefWidth(300);
        nombre.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        nombre.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Concepto x, boolean vacio) {
                super.updateItem(x, vacio);
                setText(null);
                if (vacio || x == null) {
                    setGraphic(null);
                    return;
                }
                Label t = new Label(x.nombre());
                t.getStyleClass().add("concepto-nombre");
                Label d = new Label(x.detalle());
                d.getStyleClass().add("text-muted");
                setGraphic(new VBox(1, t, d));
            }
        });
        columnas.add(nombre);
        for (YearMonth mes : resumen.meses()) {
            columnas.add(columnaMes(mes, c -> c.porMes().get(mes)));
        }
        TableColumn<Concepto, BigDecimal> total = monto("Total", 120, Concepto::total);
        total.getStyleClass().add("col-total");
        columnas.add(total);
        columnas.forEach(c -> c.setSortable(false));
        tablaConceptos.getColumns().setAll(columnas);
        conceptos.setAll(resumen.conceptos());
    }

    private <T> TableColumn<T, BigDecimal> columnaMes(YearMonth mes, Function<T, BigDecimal> valor) {
        boolean futuro = resumen.futuro(mes);
        TableColumn<T, BigDecimal> col = new TableColumn<>(RonaldmichaResumenAnualService.mesCorto(mes));
        col.setPrefWidth(104);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(futuro ? null : valor.apply(c.getValue())));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal monto, boolean vacio) {
                super.updateItem(monto, vacio);
                setText(vacio ? null : futuro || monto == null ? "—" : RonaldmichaFormatos.moneda(monto));
            }
        });
        col.getStyleClass().addAll("num", "col-mes-anual");
        if (Resumen.mesDeGratificacion(mes)) {
            col.getStyleClass().add("col-grati");
        }
        if (futuro) {
            col.getStyleClass().add("col-futuro");
        }
        return col;
    }

    private void exportar() {
        botonExportar.exportar();
    }

    private static Label etiqueta(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("field-label");
        return l;
    }

    private static <T> TableColumn<T, String> texto(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(valor.apply(c.getValue())));
        return col;
    }

    private static <T> TableColumn<T, BigDecimal> monto(String titulo, double ancho, Function<T, BigDecimal> valor) {
        TableColumn<T, BigDecimal> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(valor.apply(c.getValue())));
        col.setCellFactory(com.planillaperu.ui.components.RonaldmichaMoneyCell.fabrica());
        return col;
    }
}
