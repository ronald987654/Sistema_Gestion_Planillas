package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla.Concepto;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla.TipoConcepto;
import com.planillaperu.common.RonaldmichaBusqueda;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaPeriodo;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaSituacionPeriodo;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaReporte;
import com.planillaperu.service.RonaldmichaReporte.Formato;
import com.planillaperu.service.RonaldmichaReportesService;
import com.planillaperu.service.RonaldmichaPlanillaService.FilaPlanilla;
import com.planillaperu.service.RonaldmichaPlanillaService.PlanillaMes;
import com.planillaperu.service.RonaldmichaPlanillaService.Totales;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaExportButton;
import com.planillaperu.ui.components.RonaldmichaConceptosPlanilla;
import com.planillaperu.ui.components.RonaldmichaConfirmButton;
import com.planillaperu.ui.components.RonaldmichaEmptyState;
import com.planillaperu.ui.components.RonaldmichaIcono;
import com.planillaperu.ui.components.RonaldmichaKpiCard;
import com.planillaperu.ui.components.RonaldmichaModal;
import com.planillaperu.ui.components.RonaldmichaMoneyCell;
import com.planillaperu.ui.components.RonaldmichaToast;
import javafx.application.Platform;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class RonaldmichaPlanillaController {

    private static final String TODAS = "Todas las sucursales";
    private static final String SIN_SUCURSAL = "Sin sucursal";

    record Fila(FilaPlanilla fila, Totales totales) {

        boolean esTotal() {
            return fila == null;
        }
    }

    private final RonaldmichaAppContext contexto;
    private final ChangeListener<YearMonth> alCambiarPeriodo = (o, a, b) -> cargar();
    private final RonaldmichaRefresco refresco = new RonaldmichaRefresco(this::cargar);
    private final ChangeListener<RonaldmichaEvento> alCambiarDatos = (o, a, e) -> {
        if (e != null && e.es(RonaldmichaEvento.Tema.TRABAJADORES, RonaldmichaEvento.Tema.ASISTENCIA, RonaldmichaEvento.Tema.PARAMETROS,
                RonaldmichaEvento.Tema.SEGURIDAD, RonaldmichaEvento.Tema.PERIODO, RonaldmichaEvento.Tema.VACACIONES)) {
            refresco.pedir();
        }
    };
    private final ObservableList<Fila> filas = FXCollections.observableArrayList();
    private final TableView<Fila> tablaResumen = new TableView<>(filas);
    private final TableView<Fila> tablaDetallada = new TableView<>(filas);
    private final ToggleGroup grupoVista = new ToggleGroup();
    private PlanillaMes planilla;
    private int anioTira;

    @FXML private Button anioAnterior;
    @FXML private Button anioSiguiente;
    @FXML private Label tituloAnio;
    @FXML private Label infoCierre;
    @FXML private HBox accionesCierre;
    @FXML private HBox tiraMeses;
    @FXML private VBox banner;
    @FXML private FlowPane kpis;
    @FXML private ComboBox<String> filtroSucursal;
    @FXML private TextField buscar;
    @FXML private ToggleButton vistaResumen;
    @FXML private ToggleButton vistaDetallada;
    @FXML private Label resumen;
    @FXML private StackPane contenedorTabla;
    @FXML private HBox exportaciones;
    private RonaldmichaExportButton exportarExcel;

    public RonaldmichaPlanillaController(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        exportarExcel = exportacion("Excel", Formato.EXCEL);
        exportaciones.getChildren().setAll(exportarExcel, exportacion("PDF", Formato.PDF));
        RonaldmichaAtajos.registrarExportar(this::exportar);
        anioAnterior.setGraphic(RonaldmichaIcono.ANTERIOR.nodo(16));
        anioAnterior.setTooltip(new Tooltip("Año anterior"));
        anioSiguiente.setGraphic(RonaldmichaIcono.SIGUIENTE.nodo(16));
        anioSiguiente.setTooltip(new Tooltip("Año siguiente"));

        vistaResumen.setToggleGroup(grupoVista);
        vistaDetallada.setToggleGroup(grupoVista);
        grupoVista.selectedToggleProperty().addListener((o, anterior, actual) -> {
            if (actual == null) {
                grupoVista.selectToggle(anterior);
            } else {
                mostrarTabla();
            }
        });
        filtroSucursal.valueProperty().addListener((o, a, b) -> filtrar());
        buscar.textProperty().addListener((o, a, b) -> filtrar());

        configurarTablaResumen();
        tablaDetallada.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        for (TableView<Fila> t : List.of(tablaResumen, tablaDetallada)) {
            t.setRowFactory(tv -> new TableRow<>() {
                @Override
                protected void updateItem(Fila f, boolean vacio) {
                    super.updateItem(f, vacio);
                    getStyleClass().remove("fila-total");
                    if (!vacio && f != null && f.esTotal()) {
                        getStyleClass().add("fila-total");
                    }
                }
            });
            t.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.PLANILLA, "Sin trabajadores",
                    "No hay trabajadores que coincidan con el filtro en este periodo.").conAccion("Limpiar filtro", () -> {
                        buscar.clear();
                        filtroSucursal.setValue(TODAS);
                    }));
        }
        mostrarTabla();

        contexto.periodoProperty().addListener(new WeakChangeListener<>(alCambiarPeriodo));
        contexto.eventoProperty().addListener(new WeakChangeListener<>(alCambiarDatos));
        cargar();
        abrirSeccionSolicitada();
    }

    private void cargar() {
        YearMonth mes = contexto.periodoProperty().get();
        anioTira = mes.getYear();
        try {
            planilla = contexto.planilla().consultar(contexto.sesion(), mes);
        } catch (RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(contenedorTabla, e.getMessage());
            return;
        }
        construirTira();
        mostrarBanner();
        mostrarCierre();
        actualizarSucursales();
        filtrar();
        seleccionarTrabajadorElegido();
    }

    private void construirTira() {
        YearMonth elegido = contexto.periodoProperty().get();
        tituloAnio.setText("Planilla " + anioTira);
        tiraMeses.getChildren().clear();
        contexto.planilla().situacionesDelAnio(anioTira).forEach((mes, situacion) -> {
            Label nombre = new Label(mesCorto(mes));
            nombre.getStyleClass().add("mes-nombre");
            Label estado = new Label(switch (situacion) {
                case CERRADO -> "Cerrado ✓";
                case ABIERTO -> "Abierto";
                case FUTURO -> "Futuro";
            });
            estado.getStyleClass().add("mes-estado");
            VBox grafico = new VBox(2, nombre, estado);
            grafico.setAlignment(Pos.CENTER);
            Button b = new Button();
            b.setGraphic(grafico);
            b.getStyleClass().addAll("mes-boton", situacion.name().toLowerCase());
            if (mes.equals(elegido)) {
                b.getStyleClass().add("elegido");
            }
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
            b.setAccessibleText(RonaldmichaPlanillaDetalle.mesTexto(mes) + " · " + situacion.etiqueta());
            b.setOnAction(e -> contexto.periodoProperty().set(mes));
            tiraMeses.getChildren().add(b);
        });
    }

    @FXML
    private void anioAnterior() {
        anioTira--;
        construirTira();
    }

    @FXML
    private void anioSiguiente() {
        anioTira++;
        construirTira();
    }

    private void mostrarBanner() {
        banner.getChildren().clear();
        String mes = RonaldmichaPlanillaDetalle.mesTexto(planilla.mes()).toLowerCase(RonaldmichaFormatos.LOCALE_PE);
        planilla.error().ifPresent(e -> banner.getChildren().add(alerta(e, "error")));
        switch (planilla.situacion()) {
            case FUTURO -> banner.getChildren().add(alerta("La planilla de " + mes + " aún no está abierta: los "
                    + "montos son referenciales y no se pueden editar. Se abre al cerrar el mes anterior.", "info"));
            case CERRADO -> banner.getChildren().add(alerta("Planilla de " + mes + " cerrada: solo lectura. Para "
                    + "modificarla, reábrala indicando el motivo.", "warning"));
            case ABIERTO -> planilla.registro().map(RonaldmichaPeriodo::motivoReapertura).filter(m -> !m.isBlank())
                    .ifPresent(m -> banner.getChildren().add(alerta("Planilla reabierta · motivo: " + m, "info")));
        }
    }

    private void mostrarCierre() {
        accionesCierre.getChildren().clear();
        infoCierre.setText("");
        YearMonth mes = planilla.mes();
        String nombreMes = mesLargo(mes);
        if (planilla.situacion() == RonaldmichaSituacionPeriodo.CERRADO) {
            planilla.registro().filter(p -> p.cerradoPor() != null).ifPresent(p -> infoCierre.setText(
                    "Cerrada por " + p.cerradoPor() + " el " + RonaldmichaFormatos.fechaHora(p.fechaCierre())));
            if (esAdmin()) {
                Button reabrir = new Button("Reabrir");
                reabrir.getStyleClass().add("btn");
                reabrir.setOnAction(e -> pedirMotivoReapertura());
                accionesCierre.getChildren().add(reabrir);
            }
        } else if (planilla.situacion() == RonaldmichaSituacionPeriodo.ABIERTO && esAdmin() && planilla.error().isEmpty()) {
            RonaldmichaConfirmButton cerrar = new RonaldmichaConfirmButton("Cerrar planilla de " + nombreMes, this::cerrarPlanilla);
            cerrar.getStyleClass().add("btn-primary");
            cerrar.setTooltip(new Tooltip("Bloquea las ediciones de " + nombreMes + " y abre el mes siguiente."));
            accionesCierre.getChildren().add(cerrar);
        }
    }

    private void abrirSeccionSolicitada() {
        RonaldmichaAppContext.Seccion seccion = contexto.seccionSolicitadaProperty().get();
        contexto.seccionSolicitadaProperty().set(null);
        if (seccion == RonaldmichaAppContext.Seccion.PLANILLA_DETALLADA) {
            vistaDetallada.setSelected(true);
        } else if (seccion == RonaldmichaAppContext.Seccion.PLANILLA_CIERRE) {
            accionesCierre.getChildren().stream().findFirst().ifPresent(boton -> {
                boton.getStyleClass().add("accion-resaltada");
                Platform.runLater(boton::requestFocus);
            });
        }
    }

    private void actualizarSucursales() {
        String anterior = filtroSucursal.getValue();
        List<String> sucursales = planilla.filas().stream().map(f -> sucursal(f.trabajador()))
                .distinct().sorted().toList();
        filtroSucursal.getItems().setAll(TODAS);
        filtroSucursal.getItems().addAll(sucursales);
        filtroSucursal.setValue(anterior != null && filtroSucursal.getItems().contains(anterior) ? anterior : TODAS);
    }

    private void filtrar() {
        if (planilla == null) {
            return;
        }
        String sucursal = filtroSucursal.getValue();
        String consulta = buscar.getText();
        List<FilaPlanilla> visibles = planilla.filas().stream()
                .filter(f -> sucursal == null || TODAS.equals(sucursal) || sucursal.equals(sucursal(f.trabajador())))
                .filter(f -> RonaldmichaBusqueda.coincide(textoBusqueda(f.trabajador()), consulta))
                .toList();
        Totales totales = Totales.de(visibles);
        List<Fila> nuevas = new ArrayList<>(visibles.stream().map(f -> new Fila(f, null)).toList());
        if (!visibles.isEmpty()) {
            nuevas.add(new Fila(null, totales));
        }
        filas.setAll(nuevas);
        mostrarKpis(totales);
        long conAsistencia = visibles.stream().filter(FilaPlanilla::desdeAsistencia).count();
        resumen.setText(visibles.size() + " de " + planilla.filas().size() + " trabajadores · " + conAsistencia
                + " con datos de Asistencia · " + planilla.situacion().etiqueta());
        if (vistaDetallada.isSelected()) {
            construirColumnasDetalladas();
        }
    }

    private void mostrarKpis(Totales t) {
        String detalle = t.trabajadores() + (t.trabajadores() == 1 ? " trabajador" : " trabajadores");
        RonaldmichaKpiCard neto = new RonaldmichaKpiCard("Neto a pagar", RonaldmichaFormatos.moneda(t.neto()), detalle);
        neto.getStyleClass().add("kpi-neto");
        kpis.getChildren().setAll(
                new RonaldmichaKpiCard("Ingresos", RonaldmichaFormatos.moneda(t.ingresos()), detalle),
                new RonaldmichaKpiCard("Descuentos", RonaldmichaFormatos.moneda(t.descuentos()), "Pensión, renta y otros"),
                new RonaldmichaKpiCard("Aportes del empleador", RonaldmichaFormatos.moneda(t.aportes()), "EsSalud, SIS, Vida Ley y otros"),
                neto,
                new RonaldmichaKpiCard("Renta de 5ta retenida", RonaldmichaFormatos.moneda(t.rentaQuinta()), "Código PLAME 0605"));
    }

    private void seleccionarTrabajadorElegido() {
        Long id = contexto.trabajadorSeleccionadoProperty().get();
        if (id == null) {
            return;
        }
        filas.stream().filter(f -> !f.esTotal() && Objects.equals(f.fila().trabajador().id(), id)).findFirst()
                .ifPresent(f -> {
                    tablaResumen.getSelectionModel().select(f);
                    tablaResumen.scrollTo(f);
                });
    }

    private void mostrarTabla() {
        if (vistaDetallada.isSelected()) {
            construirColumnasDetalladas();
            contenedorTabla.getChildren().setAll(tablaDetallada);
        } else {
            contenedorTabla.getChildren().setAll(tablaResumen);
        }
    }

    private void configurarTablaResumen() {
        tablaResumen.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<Fila, String> ingreso = texto("Ingreso", 96,
                f -> f.esTotal() ? "TOTAL" : RonaldmichaFormatos.fecha(f.fila().trabajador().fechaIngreso()));
        ingreso.getStyleClass().add("mono");
        TableColumn<Fila, String> documento = texto("Documento", 118, f -> f.esTotal()
                ? f.totales().trabajadores() + " trab."
                : f.fila().trabajador().tipoDoc().codigo() + " " + f.fila().trabajador().numDoc());

        TableColumn<Fila, Fila> nombre = new TableColumn<>("Apellidos y nombres");
        nombre.setPrefWidth(270);
        nombre.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        nombre.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Fila f, boolean vacio) {
                super.updateItem(f, vacio);
                if (vacio || f == null || f.esTotal()) {
                    setGraphic(null);
                    return;
                }
                RonaldmichaTrabajador t = f.fila().trabajador();
                Label n = new Label(t.nombreCompleto());
                n.getStyleClass().add("celda-nombre");
                Label d = new Label(t.regimen().descripcion() + " · " + pension(t)
                        + (f.fila().desdeAsistencia() ? " · Asistencia" : ""));
                d.getStyleClass().add("celda-detalle");
                setGraphic(new VBox(2, n, d));
            }
        });

        TableColumn<Fila, String> dias = texto("Días", 70, f -> f.esTotal() ? ""
                : f.fila().resultado().diasLaborados() + " / " + f.fila().resultado().diasMes());
        dias.getStyleClass().add("num");
        TableColumn<Fila, BigDecimal> ingresos = monto("Ingresos", 120, RonaldmichaResultadoPlanilla::totalIngresos,
                Totales::ingresos, null);
        TableColumn<Fila, BigDecimal> descuentos = monto("Descuentos", 120, RonaldmichaResultadoPlanilla::totalDescuentos,
                Totales::descuentos, "monto-descuento");
        TableColumn<Fila, BigDecimal> aportes = monto("Aportes", 120, RonaldmichaResultadoPlanilla::totalAportes,
                Totales::aportes, null);
        TableColumn<Fila, BigDecimal> neto = monto("Neto", 130, RonaldmichaResultadoPlanilla::netoAPagar, Totales::neto,
                "monto-neto");

        TableColumn<Fila, Fila> acciones = new TableColumn<>("Acciones");
        acciones.setPrefWidth(180);
        acciones.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        acciones.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Fila f, boolean vacio) {
                super.updateItem(f, vacio);
                if (vacio || f == null || f.esTotal()) {
                    setGraphic(null);
                    return;
                }
                Button editar = new Button(planilla.editable() && esAdmin() ? "Editar" : "Ver");
                editar.getStyleClass().addAll("btn", "btn-sm");
                editar.setOnAction(e -> editar(f.fila()));
                Button boleta = new Button("Boleta");
                boleta.getStyleClass().addAll("btn", "btn-sm");
                boleta.setOnAction(e -> verBoleta(f.fila()));
                HBox caja = new HBox(6, editar, boleta);
                caja.setAlignment(Pos.CENTER_LEFT);
                setGraphic(caja);
            }
        });
        List<TableColumn<Fila, ?>> columnas = List.of(ingreso, documento, nombre, dias, ingresos, descuentos,
                aportes, neto, acciones);
        columnas.forEach(c -> c.setSortable(false));
        tablaResumen.getColumns().setAll(columnas);
    }

    private void construirColumnasDetalladas() {
        Map<String, Concepto> claves = new LinkedHashMap<>();
        for (TipoConcepto tipo : TipoConcepto.values()) {
            for (FilaPlanilla f : planilla.filas()) {
                f.resultado().conceptos().stream().filter(c -> c.tipo() == tipo)
                        .forEach(c -> claves.putIfAbsent(clave(c), c));
            }
        }
        List<TableColumn<Fila, ?>> columnas = new ArrayList<>();
        columnas.add(texto("Documento", 110, f -> f.esTotal() ? "TOTAL"
                : f.fila().trabajador().tipoDoc().codigo() + " " + f.fila().trabajador().numDoc()));
        columnas.add(texto("Apellidos y nombres", 230, f -> f.esTotal()
                ? f.totales().trabajadores() + " trabajadores" : f.fila().trabajador().nombreCompleto()));
        TableColumn<Fila, String> dias = texto("Días lab.", 76, f -> f.esTotal() ? ""
                : String.valueOf(f.fila().resultado().diasLaborados()));
        dias.getStyleClass().add("num");
        columnas.add(dias);
        claves.forEach((clave, ejemplo) -> {
            String titulo = (ejemplo.codigoPlame() == null ? "" : ejemplo.codigoPlame() + "\n") + ejemplo.descripcion();
            TableColumn<Fila, BigDecimal> col = new TableColumn<>();
            Label encabezado = new Label(titulo);
            encabezado.setWrapText(true);
            encabezado.setTooltip(new Tooltip(ejemplo.tipo().name().toLowerCase() + " · " + ejemplo.descripcion()));
            col.setGraphic(encabezado);
            col.setPrefWidth(128);
            col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().esTotal()
                    ? sumaVisible(clave) : importe(c.getValue().fila().resultado(), clave)));
            col.setCellFactory(c -> celdaNumero(ejemplo.tipo() == TipoConcepto.DESCUENTO
                    || ejemplo.tipo() == TipoConcepto.REBAJA ? "monto-descuento" : null));
            col.getStyleClass().add("col-" + ejemplo.tipo().name().toLowerCase());
            columnas.add(col);
        });
        columnas.add(monto("Total ingresos", 130, RonaldmichaResultadoPlanilla::totalIngresos, Totales::ingresos, null));
        columnas.add(monto("Total descuentos", 130, RonaldmichaResultadoPlanilla::totalDescuentos, Totales::descuentos,
                "monto-descuento"));
        columnas.add(monto("Neto a pagar", 130, RonaldmichaResultadoPlanilla::netoAPagar, Totales::neto, "monto-neto"));
        columnas.add(monto("Total aportes", 130, RonaldmichaResultadoPlanilla::totalAportes, Totales::aportes, null));
        columnas.forEach(c -> c.setSortable(false));
        tablaDetallada.getColumns().setAll(columnas);
    }

    private BigDecimal sumaVisible(String clave) {
        return filas.stream().filter(f -> !f.esTotal()).map(f -> importe(f.fila().resultado(), clave))
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal importe(RonaldmichaResultadoPlanilla r, String clave) {
        return r.conceptos().stream().filter(c -> clave(c).equals(clave))
                .map(c -> c.tipo() == TipoConcepto.REBAJA ? c.monto().negate() : c.monto())
                .reduce(BigDecimal::add).orElse(null);
    }

    private static String clave(Concepto c) {
        return c.tipo() + "|" + c.codigoPlame() + "|" + c.descripcion();
    }

    private static TableColumn<Fila, String> texto(String titulo, double ancho, Function<Fila, String> valor) {
        TableColumn<Fila, String> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(valor.apply(c.getValue())));
        return col;
    }

    private static TableColumn<Fila, BigDecimal> monto(String titulo, double ancho,
                                                       Function<RonaldmichaResultadoPlanilla, BigDecimal> deFila,
                                                       Function<Totales, BigDecimal> deTotal, String estilo) {
        TableColumn<Fila, BigDecimal> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().esTotal()
                ? deTotal.apply(c.getValue().totales()) : deFila.apply(c.getValue().fila().resultado())));
        col.setCellFactory(c -> celdaNumero(estilo));
        return col;
    }

    private static TableCell<Fila, BigDecimal> celdaNumero(String estilo) {
        RonaldmichaMoneyCell<Fila> celda = new RonaldmichaMoneyCell<>();
        if (estilo != null) {
            celda.getStyleClass().add(estilo);
        }
        return celda;
    }

    private void editar(FilaPlanilla f) {
        contexto.trabajadorSeleccionadoProperty().set(f.trabajador().id());
        RonaldmichaPlanillaDetalle.abrir(contexto, f.trabajador().id(), planilla.mes(), contenedorTabla, this::cargar);
    }

    private void verBoleta(FilaPlanilla f) {
        RonaldmichaTrabajador t = f.trabajador();
        RonaldmichaResultadoPlanilla r = f.resultado();
        Label nombre = new Label(t.nombreCompleto());
        nombre.getStyleClass().add("title-md");
        Label datos = new Label(t.tipoDoc().codigo() + " " + t.numDoc() + " · " + (t.cargo() == null ? "" : t.cargo())
                + " · " + t.regimen().descripcion() + " · " + pension(t) + "\nIngreso " + RonaldmichaFormatos.fecha(t.fechaIngreso())
                + " · días laborados " + r.diasLaborados() + " de " + r.diasMes()
                + (r.inasistencias() > 0 ? " · inasistencias " + r.inasistencias() : "")
                + (r.diasVacaciones() > 0 ? " · vacaciones " + r.diasVacaciones() : "")
                + (r.diasSubsidiados() > 0 ? " · subsidiados " + r.diasSubsidiados() : ""));
        datos.getStyleClass().add("text-muted");
        datos.setWrapText(true);
        RonaldmichaConceptosPlanilla columnas = new RonaldmichaConceptosPlanilla();
        columnas.mostrar(r);
        RonaldmichaModal modal = new RonaldmichaModal("Boleta de pago · " + RonaldmichaPlanillaDetalle.mesTexto(planilla.mes()),
                new VBox(14, new VBox(4, nombre, datos), columnas)).ancho(1000);
        modal.botonCancelar().setText("Cerrar");
        modal.mostrar(contenedorTabla);
    }

    @FXML
    private void enviarBoletas() {
        List<RonaldmichaTrabajador> visibles = filas.stream().filter(f -> !f.esTotal()).map(f -> f.fila().trabajador()).toList();
        long conCorreo = visibles.stream().filter(t -> t.correo() != null && !t.correo().isBlank()).count();
        Label texto = new Label("El envío de boletas por correo aún no está conectado a un servidor de correo, así "
                + "que no se envió nada.\n\n" + conCorreo + " de " + visibles.size()
                + " trabajadores del filtro tienen correo registrado"
                + (conCorreo < visibles.size() ? "; complete el correo de los demás en su ficha." : "."));
        texto.setWrapText(true);
        RonaldmichaModal modal = new RonaldmichaModal("Enviar boletas · " + RonaldmichaPlanillaDetalle.mesTexto(planilla.mes()), texto).ancho(560);
        modal.botonCancelar().setText("Cerrar");
        modal.mostrar(contenedorTabla);
    }

    private void exportar() {
        exportarExcel.exportar();
    }

    private RonaldmichaExportButton exportacion(String texto, Formato formato) {
        YearMonth[] mes = new YearMonth[1];
        RonaldmichaExportButton b = new RonaldmichaExportButton(texto, RonaldmichaExportButton.Formato.valueOf(formato.name()), () -> {
            RonaldmichaReportesService.Archivo a = contexto.reportes().exportar(contexto.sesion(), RonaldmichaReporte.PLANILLA, formato,
                    new RonaldmichaReportesService.Parametros(mes[0], null, false));
            return new RonaldmichaExportButton.Archivo(a.nombre(), a.contenido());
        });
        b.conPrevio(continuar -> {
            mes[0] = planilla == null ? contexto.periodoProperty().get() : planilla.mes();
            continuar.run();
        });
        b.setGraphic(RonaldmichaIcono.DESCARGA.nodo(16));
        return b;
    }

    private void cerrarPlanilla() {
        YearMonth mes = planilla.mes();
        try {
            contexto.planilla().cerrar(contexto.sesion(), mes);
            cargar();
            RonaldmichaToast.exito(contenedorTabla, "Planilla de " + mesLargo(mes) + " cerrada. Se abrió "
                    + mesLargo(mes.plusMonths(1)) + ".");
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(contenedorTabla, e.getMessage());
        }
    }

    private void pedirMotivoReapertura() {
        YearMonth mes = planilla.mes();
        TextArea motivo = new TextArea();
        motivo.setPromptText("Motivo de la reapertura (obligatorio)");
        motivo.setPrefRowCount(3);
        motivo.setWrapText(true);
        Label aviso = new Label("La planilla de " + mesLargo(mes) + " volverá a ser editable. El motivo queda "
                + "registrado en la auditoría.");
        aviso.setWrapText(true);
        aviso.getStyleClass().add("text-muted");
        RonaldmichaModal modal = new RonaldmichaModal("Reabrir planilla de " + mesLargo(mes), new VBox(12, aviso, motivo)).ancho(560);
        Button reabrir = new Button("Reabrir");
        reabrir.getStyleClass().addAll("btn", "btn-primary");
        reabrir.disableProperty().bind(motivo.textProperty().isEmpty());
        reabrir.setOnAction(e -> {
            try {
                contexto.planilla().reabrir(contexto.sesion(), mes, motivo.getText());
                modal.cerrar();
                cargar();
                RonaldmichaToast.exito(contenedorTabla, "Planilla de " + mesLargo(mes) + " reabierta.");
            } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException ex) {
                RonaldmichaToast.error(contenedorTabla, ex.getMessage());
            }
        });
        modal.agregarAccion(reabrir).mostrar(contenedorTabla);
    }

    static String pension(RonaldmichaTrabajador t) {
        return t.sistemaPension().nombre() + (t.sistemaPension().esAfp() && t.tipoComision() != null
                ? " · " + t.tipoComision().codigo() : "");
    }

    private boolean esAdmin() {
        return contexto.sesion() != null && contexto.sesion().rol() == RonaldmichaRol.ADMIN;
    }

    private static String sucursal(RonaldmichaTrabajador t) {
        return t.sucursal() == null || t.sucursal().isBlank() ? SIN_SUCURSAL : t.sucursal();
    }

    private static String textoBusqueda(RonaldmichaTrabajador t) {
        return t.nombreCompleto() + " " + t.numDoc() + " " + t.codigo();
    }

    private static String mesCorto(YearMonth mes) {
        String n = mes.getMonth().getDisplayName(TextStyle.SHORT_STANDALONE, RonaldmichaFormatos.LOCALE_PE).replace(".", "");
        return n.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + n.substring(1);
    }

    private static String mesLargo(YearMonth mes) {
        return mes.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, RonaldmichaFormatos.LOCALE_PE);
    }

    private static Label alerta(String texto, String estilo) {
        Label l = new Label(texto);
        l.getStyleClass().addAll("alert", estilo);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }
}
