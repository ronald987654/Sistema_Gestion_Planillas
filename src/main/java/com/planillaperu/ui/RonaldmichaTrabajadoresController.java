package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.common.RonaldmichaBusqueda;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaAprobacionPendiente;
import com.planillaperu.domain.RonaldmichaHorario;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.export.RonaldmichaLectorTabla;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaAprobacionService;
import com.planillaperu.service.RonaldmichaTrabajadorService;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaChip;
import com.planillaperu.ui.components.RonaldmichaEmptyState;
import com.planillaperu.ui.components.RonaldmichaIcono;
import com.planillaperu.ui.components.RonaldmichaModal;
import com.planillaperu.ui.components.RonaldmichaMoneyCell;
import com.planillaperu.ui.components.RonaldmichaToast;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RonaldmichaTrabajadoresController {

    private static final String TODAS_LAS_AREAS = "Todas las áreas";
    private static final String ACTIVOS = "Activos";
    private static final String CESADOS = "Cesados";
    private static final String TODOS = "Todos";

    private final RonaldmichaAppContext contexto;
    private final ObservableList<RonaldmichaTrabajador> trabajadores = FXCollections.observableArrayList();
    private final FilteredList<RonaldmichaTrabajador> filtrados = new FilteredList<>(trabajadores);
    private Set<Long> conCambioPendiente = Set.of();

    @FXML private TextField buscar;
    @FXML private ComboBox<String> filtroArea;
    @FXML private ComboBox<String> filtroEstado;
    @FXML private Button botonImportar;
    @FXML private Button botonPlantilla;
    @FXML private Button botonNuevo;
    @FXML private Label resumen;
    @FXML private TableView<RonaldmichaTrabajador> tabla;
    @FXML private TableColumn<RonaldmichaTrabajador, String> colCodigo;
    @FXML private TableColumn<RonaldmichaTrabajador, String> colDocumento;
    @FXML private TableColumn<RonaldmichaTrabajador, RonaldmichaTrabajador> colNombre;
    @FXML private TableColumn<RonaldmichaTrabajador, String> colArea;
    @FXML private TableColumn<RonaldmichaTrabajador, RonaldmichaRegimenLaboral> colRegimen;
    @FXML private TableColumn<RonaldmichaTrabajador, LocalDate> colIngreso;
    @FXML private TableColumn<RonaldmichaTrabajador, BigDecimal> colBasico;
    @FXML private TableColumn<RonaldmichaTrabajador, String> colPension;
    @FXML private TableColumn<RonaldmichaTrabajador, RonaldmichaTrabajador> colEstado;
    @FXML private TableColumn<RonaldmichaTrabajador, RonaldmichaTrabajador> colAcciones;

    public RonaldmichaTrabajadoresController(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        RonaldmichaAtajos.registrarExportar(this::exportar);
        boolean admin = esAdmin();
        for (Button b : List.of(botonImportar, botonPlantilla, botonNuevo)) {
            b.setVisible(admin);
            b.setManaged(admin);
        }
        if (contexto.nuevoTrabajadorSolicitadoProperty().get()) {
            contexto.nuevoTrabajadorSolicitadoProperty().set(false);
            if (admin) {
                tabla.sceneProperty().addListener((o, antes, escena) -> {
                    if (escena != null && antes == null) {
                        nuevo();
                    }
                });
            }
        }

        filtroEstado.getItems().setAll(ACTIVOS, CESADOS, TODOS);
        filtroEstado.setValue(ACTIVOS);
        buscar.textProperty().addListener((o, a, b) -> filtrar());
        filtroArea.valueProperty().addListener((o, a, b) -> filtrar());
        filtroEstado.valueProperty().addListener((o, a, b) -> filtrar());

        configurarColumnas();
        SortedList<RonaldmichaTrabajador> ordenados = new SortedList<>(filtrados);
        ordenados.comparatorProperty().bind(tabla.comparatorProperty());
        tabla.setItems(ordenados);
        tabla.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.TRABAJADORES, "Sin resultados",
                "Ningún trabajador coincide con la búsqueda o los filtros.").conAccion("Limpiar filtros", () -> {
                    buscar.clear();
                    filtroArea.setValue(TODAS_LAS_AREAS);
                    filtroEstado.setValue(ACTIVOS);
                }));
        tabla.setRowFactory(t -> {
            javafx.scene.control.TableRow<RonaldmichaTrabajador> fila = new javafx.scene.control.TableRow<>();
            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !fila.isEmpty() && esAdmin()) {
                    editar(fila.getItem());
                }
            });
            return fila;
        });

        recargar();
        seleccionar(contexto.trabajadorSeleccionadoProperty().get());
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, t) -> {
            if (t != null) {
                contexto.trabajadorSeleccionadoProperty().set(t.id());
            }
        });
    }

    private void configurarColumnas() {
        colCodigo.setVisible(false);
        colIngreso.setVisible(false);
        colArea.setVisible(false);
        colCodigo.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().codigo()));
        colDocumento.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().tipoDoc().codigo() + " " + c.getValue().numDoc()));
        colDocumento.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String documento, boolean vacio) {
                super.updateItem(documento, vacio);
                RonaldmichaTrabajador t = vacio || getTableRow() == null ? null : getTableRow().getItem();
                if (t == null) {
                    setGraphic(null);
                    return;
                }
                Label doc = new Label(documento);
                doc.getStyleClass().add("mono");
                Label codigo = new Label(t.codigo());
                codigo.getStyleClass().addAll("celda-detalle", "mono");
                setGraphic(new VBox(2, doc, codigo));
            }
        });

        colNombre.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colNombre.setComparator(Comparator.comparing(t -> RonaldmichaBusqueda.normalizar(t.nombreCompleto())));
        colNombre.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(RonaldmichaTrabajador t, boolean vacio) {
                super.updateItem(t, vacio);
                if (vacio || t == null) {
                    setGraphic(null);
                    return;
                }
                Label nombre = new Label(t.nombreCompleto());
                nombre.getStyleClass().add("celda-nombre");
                Label cargo = new Label(Stream.of(t.cargo(), t.area(), "ingreso " + RonaldmichaFormatos.fecha(t.fechaIngreso()))
                        .filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(" · ")));
                cargo.getStyleClass().add("celda-detalle");
                setGraphic(new VBox(2, nombre, cargo));
            }
        });

        colArea.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().area()));

        colRegimen.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().regimen()));
        colRegimen.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(RonaldmichaRegimenLaboral r, boolean vacio) {
                super.updateItem(r, vacio);
                if (vacio || r == null) {
                    setGraphic(null);
                    return;
                }
                Label chip = chip(switch (r) {
                    case GENERAL -> "General";
                    case PEQUENA -> "Pequeña";
                    case MICRO -> "Micro";
                }, switch (r) {
                    case GENERAL -> "info";
                    case PEQUENA -> "warning";
                    case MICRO -> "holiday";
                });
                chip.setTooltip(new Tooltip(r.descripcion()));
                setGraphic(chip);
            }
        });

        colIngreso.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().fechaIngreso()));
        colIngreso.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate f, boolean vacio) {
                super.updateItem(f, vacio);
                setText(vacio || f == null ? null : RonaldmichaFormatos.fecha(f));
            }
        });
        colIngreso.getStyleClass().add("mono");

        colBasico.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().sueldo()));
        colBasico.setCellFactory(RonaldmichaMoneyCell.fabrica());

        colPension.setCellValueFactory(c -> new ReadOnlyStringWrapper(pension(c.getValue())));

        colEstado.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colEstado.setComparator(Comparator.comparing((RonaldmichaTrabajador t) -> !contexto.trabajadores().activo(t))
                .thenComparing(t -> !conCambioPendiente.contains(t.id())));
        colEstado.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(RonaldmichaTrabajador t, boolean vacio) {
                super.updateItem(t, vacio);
                if (vacio || t == null) {
                    setGraphic(null);
                    return;
                }
                boolean activo = contexto.trabajadores().activo(t);
                VBox caja = new VBox(4, RonaldmichaChip.estado(activo ? "Activo" : "Cesado"));
                if (conCambioPendiente.contains(t.id())) {
                    caja.getChildren().add(chip("Cambio pendiente", "warning"));
                }
                setGraphic(caja);
            }
        });

        colAcciones.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colAcciones.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(RonaldmichaTrabajador t, boolean vacio) {
                super.updateItem(t, vacio);
                if (vacio || t == null) {
                    setGraphic(null);
                    return;
                }
                Button editar = accion(RonaldmichaIcono.EDITAR, "Editar ficha de " + t.nombreCompleto(), () -> editar(t));
                Button liquidar = accion(RonaldmichaIcono.LIQUIDACION, "Liquidar a " + t.nombreCompleto(), () -> liquidar(t));
                boolean admin = esAdmin();
                editar.setDisable(!admin);
                liquidar.setDisable(!admin);
                HBox botones = new HBox(6, editar, liquidar);
                botones.setAlignment(Pos.CENTER_LEFT);
                setGraphic(botones);
            }
        });
    }

    private void recargar() {
        RonaldmichaTrabajador seleccionado = tabla.getSelectionModel().getSelectedItem();
        conCambioPendiente = contexto.aprobaciones().pendientes(contexto.sesion()).stream()
                .filter(a -> !RonaldmichaAprobacionService.esVacaciones(a.tipo()))
                .map(RonaldmichaAprobacionPendiente::trabajadorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        trabajadores.setAll(contexto.trabajadores().todos());

        String area = filtroArea.getValue();
        List<String> areas = trabajadores.stream().map(RonaldmichaTrabajador::area).filter(Objects::nonNull)
                .distinct().sorted(Comparator.comparing(RonaldmichaBusqueda::normalizar)).toList();
        filtroArea.getItems().setAll(TODAS_LAS_AREAS);
        filtroArea.getItems().addAll(areas);
        filtroArea.setValue(area != null && filtroArea.getItems().contains(area) ? area : TODAS_LAS_AREAS);
        filtrar();
        tabla.refresh();
        if (seleccionado != null) {
            seleccionar(seleccionado.id());
        }
    }

    private void filtrar() {
        String consulta = buscar.getText();
        String area = filtroArea.getValue();
        String estado = filtroEstado.getValue();
        filtrados.setPredicate(t -> {
            boolean activo = contexto.trabajadores().activo(t);
            if (ACTIVOS.equals(estado) && !activo || CESADOS.equals(estado) && activo) {
                return false;
            }
            if (area != null && !TODAS_LAS_AREAS.equals(area) && !area.equals(t.area())) {
                return false;
            }
            return RonaldmichaBusqueda.coincide(t.nombreCompleto() + " " + t.nombres() + " " + t.apellidos() + " "
                    + t.numDoc() + " " + t.codigo(), consulta);
        });
        long activos = trabajadores.stream().filter(contexto.trabajadores()::activo).count();
        resumen.setText("Mostrando " + filtrados.size() + " de " + trabajadores.size() + " trabajadores · "
                + activos + " activos · " + (trabajadores.size() - activos) + " cesados");
    }

    private void seleccionar(Long id) {
        if (id == null) {
            return;
        }
        tabla.getItems().stream().filter(t -> id.equals(t.id())).findFirst().ifPresent(t -> {
            tabla.getSelectionModel().select(t);
            tabla.scrollTo(t);
        });
    }

    @FXML
    private void nuevo() {
        abrirFormulario(null, null);
    }

    private void editar(RonaldmichaTrabajador t) {
        RonaldmichaTrabajador actual = contexto.trabajadores().buscar(t.id()).orElse(t);
        abrirFormulario(actual, contexto.trabajadores().horario(t.id()).orElse(null));
    }

    private void abrirFormulario(RonaldmichaTrabajador t, RonaldmichaHorario h) {
        YearMonth periodo = contexto.periodoProperty().get();
        List<String> areas = filtroArea.getItems().stream().filter(a -> !TODAS_LAS_AREAS.equals(a)).toList();
        RonaldmichaTrabajadorFormulario formulario = new RonaldmichaTrabajadorFormulario(contexto, t, h, areas, periodo);

        ScrollPane desplazable = new ScrollPane(formulario);
        desplazable.setFitToWidth(true);
        desplazable.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        desplazable.setPrefViewportHeight(560);
        desplazable.getStyleClass().add("form-scroll");

        Button guardar = new Button(t == null ? "Registrar trabajador" : "Guardar cambios");
        guardar.getStyleClass().addAll("btn", "btn-primary");
        guardar.disableProperty().bind(formulario.validoProperty().not());
        guardar.setDefaultButton(true);
        String titulo = t == null ? "Nuevo trabajador" : "Editar · " + t.codigo() + " · " + t.nombreCompleto();
        RonaldmichaModal modal = new RonaldmichaModal(titulo, desplazable).ancho(940);

        guardar.setOnAction(e -> {
            try {
                RonaldmichaTrabajadorService.Guardado g = contexto.trabajadores()
                        .guardar(formulario.leer(), formulario.leerHorario(), contexto.sesion(), periodo);
                modal.cerrar();
                contexto.trabajadorSeleccionadoProperty().set(g.trabajador().id());
                recargar();
                seleccionar(g.trabajador().id());
                if (g.pendiente().isPresent()) {
                    contexto.refrescarAprobaciones();
                    RonaldmichaToast.exito(tabla, "Datos guardados. " + g.pendiente().get().tipo()
                            + " queda pendiente de aprobación.");
                } else {
                    RonaldmichaToast.exito(tabla, t == null
                            ? "Trabajador " + g.trabajador().codigo() + " registrado."
                            : "Datos de " + g.trabajador().nombreCompleto() + " actualizados.");
                }
            } catch (RonaldmichaValidacionException ex) {
                formulario.mostrarErrores(ex.errores());
                desplazable.setVvalue(0);
                RonaldmichaToast.error(tabla, "Revise los datos marcados.");
            } catch (RonaldmichaAccesoDenegadoException ex) {
                formulario.mostrarErrores(List.of(ex.getMessage()));
                desplazable.setVvalue(0);
                RonaldmichaToast.error(tabla, ex.getMessage());
            }
        });
        modal.agregarAccion(guardar).mostrar(tabla);
    }

    private void liquidar(RonaldmichaTrabajador t) {
        contexto.trabajadorALiquidarProperty().set(t.id());
        RonaldmichaNavegacion.ir(RonaldmichaVista.LIQUIDACIONES);
    }

    @FXML
    private void importar() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Importar trabajadores");
        selector.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV o Excel (*.csv, *.xlsx)", "*.csv", "*.xlsx"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        File archivo = selector.showOpenDialog(ventana());
        if (archivo == null) {
            return;
        }
        YearMonth periodo = contexto.periodoProperty().get();
        botonImportar.setDisable(true);
        String texto = botonImportar.getText();
        botonImportar.setText("Importando…");
        RonaldmichaTareas.ejecutar(() -> contexto.trabajadores().importar(RonaldmichaLectorTabla.leer(archivo.toPath()), archivo.getName(),
                contexto.sesion(), periodo), r -> {
            botonImportar.setText(texto);
            botonImportar.setDisable(false);
            recargar();
            mostrarResultadoImportacion(archivo.getName(), r);
        }, ex -> {
            botonImportar.setText(texto);
            botonImportar.setDisable(false);
            RonaldmichaToast.error(tabla, ex instanceof IOException ? "No se pudo leer el archivo: " + ex.getMessage()
                    : RonaldmichaTareas.mensaje(ex));
        });
    }

    private void mostrarResultadoImportacion(String archivo, RonaldmichaTrabajadorService.ResultadoImportacion r) {
        VBox contenido = new VBox(10);
        Label total = new Label(r.importados() + (r.importados() == 1 ? " importado" : " importados")
                + (r.observados().isEmpty() ? " · sin observaciones" : " · " + r.observados().size() + " observados"));
        total.getStyleClass().add("title-md");
        contenido.getChildren().add(total);
        if (!r.observados().isEmpty()) {
            Label lista = new Label("Observados:\n" + r.observados().stream().map(o -> "• " + o)
                    .collect(Collectors.joining("\n")));
            lista.getStyleClass().addAll("alert", "warning");
            lista.setWrapText(true);
            lista.setMaxWidth(Double.MAX_VALUE);
            ScrollPane desplazable = new ScrollPane(lista);
            desplazable.setFitToWidth(true);
            desplazable.setPrefViewportHeight(Math.min(320, 40 + 22.0 * r.observados().size()));
            contenido.getChildren().add(desplazable);
        }
        RonaldmichaModal modal = new RonaldmichaModal("Importación de " + archivo, contenido).ancho(640);
        modal.botonCancelar().setText("Cerrar");
        modal.mostrar(tabla);
        if (r.observados().isEmpty()) {
            RonaldmichaToast.exito(tabla, r.mensaje());
        } else {
            RonaldmichaToast.error(tabla, r.importados() + " importados · " + r.observados().size() + " observados.");
        }
    }

    @FXML
    private void descargarPlantilla() {
        RonaldmichaArchivos.generarYGuardar(tabla, "plantilla_trabajadores.csv", "CSV (*.csv)", "*.csv",
                () -> contexto.trabajadores().plantillaCsv());
    }

    @FXML
    private void exportar() {
        String nombre = "trabajadores_" + DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now()) + ".xlsx";
        RonaldmichaArchivos.generarYGuardar(tabla, nombre, "Excel (*.xlsx)", "*.xlsx",
                () -> contexto.trabajadores().exportarExcel(contexto.sesion()),
                f -> contexto.empresa().avisoDatosIncompletos().ifPresent(aviso -> RonaldmichaToast.error(tabla, aviso)));
    }

    private boolean esAdmin() {
        return contexto.sesion() != null && contexto.sesion().rol() == RonaldmichaRol.ADMIN;
    }

    private Window ventana() {
        return tabla.getScene().getWindow();
    }

    private static String pension(RonaldmichaTrabajador t) {
        if (!t.sistemaPension().esAfp()) {
            return "ONP";
        }
        return t.sistemaPension().nombre().replaceFirst("^AFP ", "")
                + (t.tipoComision() == null ? "" : " · " + t.tipoComision().codigo());
    }

    private static Button accion(RonaldmichaIcono icono, String texto, Runnable accion) {
        Button b = new Button();
        b.setGraphic(icono.nodo(16));
        b.getStyleClass().addAll("btn", "btn-sm", "btn-icon");
        b.setTooltip(new Tooltip(texto));
        b.setAccessibleText(texto);
        b.setOnAction(e -> accion.run());
        return b;
    }

    private static Label chip(String texto, String estilo) {
        return RonaldmichaChip.de(texto, estilo);
    }
}
