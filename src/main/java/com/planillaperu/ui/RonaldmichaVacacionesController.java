package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.calc.RonaldmichaVacacionesCalculator;
import com.planillaperu.calc.RonaldmichaVacacionesCalculator.Resumen;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaEstadoVacacion;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaTipoVacacion;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaVacacion;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.service.RonaldmichaVacacionesService;
import com.planillaperu.service.RonaldmichaVacacionesService.Detalle;
import com.planillaperu.service.RonaldmichaVacacionesService.FilaVacaciones;
import com.planillaperu.service.RonaldmichaVacacionesService.Registro;
import com.planillaperu.service.RonaldmichaVacacionesService.Solicitud;
import com.planillaperu.ui.components.RonaldmichaChip;
import com.planillaperu.ui.components.RonaldmichaConfirmButton;
import com.planillaperu.ui.components.RonaldmichaEmptyState;
import com.planillaperu.ui.components.RonaldmichaExportButton;
import com.planillaperu.ui.components.RonaldmichaIcono;
import com.planillaperu.ui.components.RonaldmichaKpiCard;
import com.planillaperu.ui.components.RonaldmichaModal;
import com.planillaperu.ui.components.RonaldmichaToast;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class RonaldmichaVacacionesController {

    private final RonaldmichaAppContext contexto;
    private final ObservableList<FilaVacaciones> filas = FXCollections.observableArrayList();
    private final TableView<FilaVacaciones> tabla = new TableView<>(filas);
    private final ObservableList<Registro> registros = FXCollections.observableArrayList();
    private final TableView<Registro> tablaRegistros = new TableView<>(registros);
    private final VBox alertas = new VBox(6);
    private final VBox panel = new VBox(14);
    private Long trabajadorAbierto;

    @FXML private VBox raiz;

    public RonaldmichaVacacionesController(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        configurarRegistros();
        panel.getStyleClass().addAll("card", "panel-vacaciones");
        Label nota = new Label("D.Leg. 713 y D.S. 012-92-TR: 30 días calendario por año completo de servicio "
                + "(15 en micro y pequeña empresa). D.Leg. 1405: la venta no puede pasar de 15 días por año y "
                + "requiere acuerdo escrito. Los registros anulados o rechazados no cuentan.");
        nota.setWrapText(true);
        nota.getStyleClass().addAll("text-muted", "nota-legal");
        if (esTrabajador()) {
            trabajadorAbierto = contexto.sesion().trabajadorId();
            raiz.getChildren().setAll(panel, nota);
            mostrarDetalle();
            return;
        }
        configurarTabla();
        Label titulo = new Label("Récord vacacional");
        titulo.getStyleClass().add("title-md");
        Label subtitulo = new Label("Al " + RonaldmichaFormatos.fecha(contexto.vacaciones().hoy())
                + " · trabajadores activos · Planificado = goces programados con fecha futura");
        subtitulo.getStyleClass().add("text-muted");
        VBox cabecera = new VBox(2, titulo, subtitulo);
        VBox tarjetaTabla = new VBox(tabla);
        tarjetaTabla.getStyleClass().addAll("card", "table-card");
        panel.setVisible(false);
        panel.setManaged(false);
        raiz.getChildren().setAll(cabecera, alertas, tarjetaTabla, panel, nota);
        cargarTabla();
    }

    private void configurarTabla() {
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.VACIO, "Sin trabajadores", "No hay trabajadores activos.")
                .conAccion("Ir a Trabajadores", () -> RonaldmichaNavegacion.ir(RonaldmichaVista.TRABAJADORES)));
        tabla.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(FilaVacaciones f, boolean vacio) {
                super.updateItem(f, vacio);
                getStyleClass().remove("fila-activa");
                if (!vacio && f != null && Objects.equals(f.trabajador().id(), trabajadorAbierto)) {
                    getStyleClass().add("fila-activa");
                }
            }
        });

        TableColumn<FilaVacaciones, String> documento = texto("Documento", 100,
                f -> f.trabajador().tipoDoc().codigo() + " " + f.trabajador().numDoc());
        documento.getStyleClass().add("mono");
        TableColumn<FilaVacaciones, FilaVacaciones> trabajador = new TableColumn<>("Trabajador");
        trabajador.setPrefWidth(240);
        trabajador.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        trabajador.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(FilaVacaciones f, boolean vacio) {
                super.updateItem(f, vacio);
                setText(null);
                if (vacio || f == null) {
                    setGraphic(null);
                    return;
                }
                Label n = new Label(f.trabajador().nombreCompleto());
                n.getStyleClass().add("celda-nombre");
                Label d = new Label(f.trabajador().regimen().descripcion() + " · "
                        + f.resumen().diasAnuales() + " días por año");
                d.getStyleClass().add("celda-detalle");
                VBox caja = new VBox(2, n, d);
                if (f.resumen().alertaIndemnizacion()) {
                    Label alerta = chip(f.resumen().periodosSinGozar() + " periodos sin gozar", "warning");
                    caja.getChildren().add(alerta);
                }
                setGraphic(caja);
            }
        });
        TableColumn<FilaVacaciones, String> ingreso = texto("Ingreso", 96, f -> RonaldmichaFormatos.fecha(f.trabajador().fechaIngreso()));
        TableColumn<FilaVacaciones, FilaVacaciones> pendiente = new TableColumn<>("Pendiente");
        pendiente.setPrefWidth(84);
        pendiente.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        pendiente.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(FilaVacaciones f, boolean vacio) {
                super.updateItem(f, vacio);
                getStyleClass().removeAll("num", "pendiente-positivo");
                setText(vacio || f == null ? null : String.valueOf(f.resumen().pendiente()));
                if (!vacio && f != null) {
                    getStyleClass().add("num");
                    if (f.resumen().pendiente() > 0) {
                        getStyleClass().add("pendiente-positivo");
                    }
                }
            }
        });
        TableColumn<FilaVacaciones, FilaVacaciones> accion = new TableColumn<>("");
        accion.setPrefWidth(96);
        accion.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        accion.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(FilaVacaciones f, boolean vacio) {
                super.updateItem(f, vacio);
                setText(null);
                if (vacio || f == null) {
                    setGraphic(null);
                    return;
                }
                Button detalle = boton("Detalle", "btn-sm", () -> abrir(f));
                detalle.setAccessibleText("Detalle de " + f.trabajador().nombreCompleto());
                setGraphic(detalle);
            }
        });

        List<TableColumn<FilaVacaciones, ?>> columnas = List.of(documento, trabajador, ingreso,
                entero("Meses", 64, f -> f.resumen().mesesCumplidos()),
                entero("Derecho", 76, f -> f.resumen().derecho()),
                entero("Gozadas", 76, f -> f.resumen().gozadas()),
                entero("Vendidas", 76, f -> f.resumen().vendidas()),
                pendiente,
                entero("Planificado", 88, f -> f.resumen().planificado()),
                texto("Récord trunco", 104, f -> trunco(f.resumen())),
                accion);
        columnas.forEach(c -> c.setSortable(false));
        tabla.getColumns().setAll(columnas);
    }

    private void cargarTabla() {
        List<FilaVacaciones> lista;
        try {
            lista = contexto.vacaciones().listar(contexto.sesion());
        } catch (RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
            return;
        }
        filas.setAll(lista);
        alertas.getChildren().clear();
        List<FilaVacaciones> conAlerta = lista.stream().filter(f -> f.resumen().alertaIndemnizacion()).toList();
        if (!conAlerta.isEmpty()) {
            alertas.getChildren().add(alerta(textoAlerta(conAlerta), "warning"));
        }
        tabla.refresh();
    }

    static String textoAlerta(List<FilaVacaciones> conAlerta) {
        String nombres = String.join("; ", conAlerta.stream().map(f -> f.trabajador().nombreCompleto() + " ("
                + f.resumen().periodosSinGozar() + " periodos, " + f.resumen().pendiente() + " días)").toList());
        return (conAlerta.size() == 1 ? "1 trabajador acumula" : conAlerta.size() + " trabajadores acumulan")
                + " 2 periodos o más sin gozar: " + nombres + ". Corresponde la indemnización vacacional "
                + "(D.Leg. 713); programe sus vacaciones.";
    }

    private void abrir(FilaVacaciones f) {
        trabajadorAbierto = f.trabajador().id();
        tabla.getSelectionModel().select(f);
        tabla.refresh();
        panel.setVisible(true);
        panel.setManaged(true);
        mostrarDetalle();
    }

    private void cerrarDetalle() {
        trabajadorAbierto = null;
        panel.setVisible(false);
        panel.setManaged(false);
        tabla.getSelectionModel().clearSelection();
        tabla.refresh();
    }

    private void mostrarDetalle() {
        Detalle d;
        try {
            d = contexto.vacaciones().detalle(contexto.sesion(), trabajadorAbierto);
        } catch (RonaldmichaAccesoDenegadoException | RonaldmichaValidacionException e) {
            panel.getChildren().setAll(alerta(e.getMessage(), "error"));
            return;
        }
        RonaldmichaTrabajador t = d.fila().trabajador();
        Resumen r = d.fila().resumen();

        Label nombre = new Label(esTrabajador() ? "Mi récord vacacional" : t.nombreCompleto());
        nombre.getStyleClass().add("title-md");
        Label datos = new Label((esTrabajador() ? t.nombreCompleto() + " · " : "") + t.regimen().descripcion()
                + " · ingreso " + RonaldmichaFormatos.fecha(t.fechaIngreso()) + " · " + RonaldmichaVacacionesService.tiempo(r) + " de servicio");
        datos.getStyleClass().add("text-muted");
        datos.setWrapText(true);
        VBox textos = new VBox(2, nombre, datos);
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox acciones = new HBox(8);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        if (!esTrabajador()) {
            acciones.getChildren().add(boton("Cerrar", null, this::cerrarDetalle));
        }
        if (esAdmin() || esTrabajador()) {
            Button planificar = boton(esTrabajador() ? "+ Solicitar vacaciones" : "+ Planificar vacaciones",
                    "btn-primary", () -> planificar(d, null));
            planificar.setMinWidth(Region.USE_PREF_SIZE);
            acciones.getChildren().add(planificar);
        }
        HBox cabecera = new HBox(12, textos, acciones);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        FlowPane kpis = new FlowPane(10, 10,
                kpi("Meses cumplidos", String.valueOf(r.mesesCumplidos()), "Trunco: " + trunco(r)),
                kpi("Derecho", r.derecho() + " d", r.aniosCompletos() + " × " + r.diasAnuales() + " días"),
                kpi("Gozadas", r.gozadas() + " d", null),
                kpi("Vendidas", r.vendidas() + " d", "Máx. 15 por año"),
                kpi("Pendientes", r.pendiente() + " d", r.periodosSinGozar() + " periodos sin gozar"),
                kpi("Planificadas", r.planificado() + " d", d.fila().enAprobacion() > 0
                        ? d.fila().enAprobacion() + " d en aprobación" : "Disponibles: " + d.fila().disponible() + " d"));

        panel.getChildren().setAll(cabecera, kpis);
        if (r.alertaIndemnizacion()) {
            panel.getChildren().add(alerta("Acumula " + r.periodosSinGozar() + " periodos sin gozar ("
                    + r.pendiente() + " días): corresponde la indemnización vacacional (D.Leg. 713).", "warning"));
        }
        registros.setAll(d.registros());
        Label tituloRegistros = new Label("Registros");
        tituloRegistros.getStyleClass().add("title-md");
        VBox tarjeta = new VBox(tablaRegistros);
        tarjeta.getStyleClass().add("table-card");
        panel.getChildren().addAll(tituloRegistros, tarjeta);
    }

    private void configurarRegistros() {
        tablaRegistros.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tablaRegistros.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.SOL, "Sin registros",
                "Aún no hay vacaciones planificadas, gozadas ni vendidas."));
        TableColumn<Registro, String> fecha = texto("Fecha de registro", 130,
                g -> RonaldmichaFormatos.fechaHora(g.vacacion().fechaRegistro()));
        TableColumn<Registro, String> tipo = texto("Tipo", 70, g -> g.vacacion().tipo().etiqueta());
        TableColumn<Registro, Integer> dias = entero("Días", 56, g -> g.vacacion().dias());
        TableColumn<Registro, String> desde = texto("Desde", 92, g -> RonaldmichaFormatos.fecha(g.vacacion().desde()));
        TableColumn<Registro, String> hasta = texto("Hasta", 92, g -> RonaldmichaFormatos.fecha(g.vacacion().hasta()));
        TableColumn<Registro, String> observacion = texto("Observación", 180, g -> {
            RonaldmichaVacacion v = g.vacacion();
            String obs = v.observacion() == null ? "" : v.observacion();
            return v.tipo() == RonaldmichaTipoVacacion.VENTA && v.acuerdoEscrito() ? ("Con acuerdo escrito. " + obs).trim() : obs;
        });
        TableColumn<Registro, Registro> estado = celdas("Estado", 170, g -> chip(g.estado().etiqueta(), g.estado().estilo()));
        TableColumn<Registro, String> usuario = texto("Usuario", 90, g -> g.vacacion().usuario());
        TableColumn<Registro, Registro> adjunto = celdas("Archivo adjunto", 200, this::celdaAdjunto);
        TableColumn<Registro, Registro> acciones = celdas("Acciones", 270, this::celdaAcciones);
        List<TableColumn<Registro, ?>> columnas = List.of(fecha, tipo, dias, desde, hasta, observacion, estado, usuario,
                adjunto, acciones);
        columnas.forEach(c -> c.setSortable(false));
        tablaRegistros.getColumns().setAll(columnas);
    }

    private Node celdaAdjunto(Registro g) {
        RonaldmichaVacacion v = g.vacacion();
        if (v.tieneAdjunto()) {
            Hyperlink nombre = new Hyperlink("📎 " + v.adjuntoNombre());
            nombre.getStyleClass().add("adjunto-link");
            nombre.setTooltip(new Tooltip("Descargar " + v.adjuntoNombre()));
            nombre.setOnAction(e -> guardarArchivo(v.adjuntoNombre(), "Archivo", "*.*",
                    () -> contexto.vacaciones().adjunto(contexto.sesion(), v.id())));
            HBox caja = new HBox(2, nombre);
            caja.setAlignment(Pos.CENTER_LEFT);
            if (g.adjuntable()) {
                RonaldmichaConfirmButton quitar = new RonaldmichaConfirmButton("✕", () -> ejecutar(
                        () -> contexto.vacaciones().quitarAdjunto(contexto.sesion(), v.id()), "Archivo quitado."));
                quitar.getStyleClass().addAll("btn-sm", "btn-ghost");
                quitar.setTooltip(new Tooltip("Quitar el archivo adjunto"));
                quitar.setAccessibleText("Quitar archivo adjunto");
                caja.getChildren().add(quitar);
            }
            return caja;
        }
        if (g.adjuntable()) {
            return boton("Adjuntar", "btn-sm", () -> adjuntar(v));
        }
        Label vacio = new Label("—");
        vacio.getStyleClass().add("text-muted");
        return vacio;
    }

    private Node celdaAcciones(Registro g) {
        RonaldmichaVacacion v = g.vacacion();
        HBox caja = new HBox(6);
        caja.setAlignment(Pos.CENTER_LEFT);
        if (g.editable()) {
            caja.getChildren().add(boton("Editar", "btn-sm", () -> planificarEdicion(g)));
        }
        String base = "solicitud_vacaciones_" + v.id();
        caja.getChildren().add(archivo("Word", base + ".docx",
                () -> contexto.vacaciones().solicitudWord(contexto.sesion(), v.id())));
        caja.getChildren().add(archivo("PDF", base + ".pdf",
                () -> contexto.vacaciones().solicitudPdf(contexto.sesion(), v.id())));
        if (g.anulable()) {
            RonaldmichaConfirmButton anular = new RonaldmichaConfirmButton("Anular", () -> ejecutar(
                    () -> contexto.vacaciones().anular(contexto.sesion(), v.id()), "Registro anulado."));
            anular.getStyleClass().add("btn-sm");
            caja.getChildren().add(anular);
        }
        return caja;
    }

    private void planificarEdicion(Registro g) {
        try {
            planificar(contexto.vacaciones().detalle(contexto.sesion(), g.vacacion().trabajadorId()), g);
        } catch (RonaldmichaAccesoDenegadoException | RonaldmichaValidacionException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
        }
    }

    private void planificar(Detalle d, Registro editando) {
        RonaldmichaVacacion original = editando == null ? null : editando.vacacion();
        long trabajadorId = d.fila().trabajador().id();
        ToggleGroup grupo = new ToggleGroup();
        ToggleButton goce = new ToggleButton("Planificar (goce)");
        ToggleButton venta = new ToggleButton("Comprar (venta)");
        goce.setToggleGroup(grupo);
        venta.setToggleGroup(grupo);
        grupo.selectToggle(original != null && original.tipo() == RonaldmichaTipoVacacion.VENTA ? venta : goce);
        grupo.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) {
                grupo.selectToggle(antes);
            }
        });
        HBox conmutador = new HBox(4, goce, venta);
        conmutador.getStyleClass().add("segmented");

        Resumen r = d.fila().resumen();
        int disponibles = d.fila().disponible() + (original == null ? 0 : original.dias());
        Label saldo = new Label("Días pendientes: " + r.pendiente() + " · disponibles para planificar: " + disponibles
                + " · venta disponible: " + r.ventaDisponible());
        saldo.getStyleClass().addAll("alert", "info");
        saldo.setWrapText(true);
        saldo.setMaxWidth(Double.MAX_VALUE);

        DatePicker desde = selectorFecha();
        DatePicker hasta = selectorFecha();
        Label dias = new Label("0 días");
        dias.getStyleClass().addAll("num", "dias-calculados");
        Runnable recalcular = () -> {
            int n = RonaldmichaVacacionesCalculator.diasCalendario(desde.getValue(), hasta.getValue());
            dias.setText(n + (n == 1 ? " día" : " días"));
        };
        desde.valueProperty().addListener((o, a, b) -> {
            if (b != null && hasta.getValue() == null) {
                hasta.setValue(b);
            }
            recalcular.run();
        });
        hasta.valueProperty().addListener((o, a, b) -> recalcular.run());
        hasta.setOnShowing(e -> {
            if (hasta.getValue() == null && desde.getValue() != null) {
                hasta.setValue(desde.getValue());
            }
        });

        CheckBox acuerdo = new CheckBox("Cuenta con acuerdo escrito firmado (obligatorio para la venta)");
        acuerdo.setWrapText(true);
        Runnable mostrarAcuerdo = () -> {
            boolean esVenta = grupo.getSelectedToggle() == venta;
            acuerdo.setVisible(esVenta);
            acuerdo.setManaged(esVenta);
        };
        grupo.selectedToggleProperty().addListener((o, a, b) -> mostrarAcuerdo.run());

        TextArea observacion = new TextArea();
        observacion.setPromptText("Observación (opcional)");
        observacion.setPrefRowCount(2);
        observacion.setWrapText(true);

        if (original != null) {
            desde.setValue(original.desde());
            hasta.setValue(original.hasta());
            acuerdo.setSelected(original.acuerdoEscrito());
            observacion.setText(original.observacion());
        }
        mostrarAcuerdo.run();
        recalcular.run();

        Label errores = alerta("", "error");
        errores.setVisible(false);
        errores.setManaged(false);

        HBox fechas = new HBox(12, campo("Desde", desde), campo("Hasta", hasta), campo("Días calculados", dias));
        fechas.setAlignment(Pos.BOTTOM_LEFT);
        VBox contenido = new VBox(12, conmutador, saldo, fechas, acuerdo, campo("Observación", observacion), errores);

        String titulo = original != null ? "Editar vacaciones"
                : esTrabajador() ? "Solicitar vacaciones" : "Planificar vacaciones";
        RonaldmichaModal modal = new RonaldmichaModal(titulo + " · " + d.fila().trabajador().nombreCompleto(), contenido).ancho(600);
        Button guardar = boton(original != null ? "Guardar cambios" : esTrabajador() ? "Enviar solicitud" : "Guardar",
                "btn-primary", () -> {
                    Solicitud s = new Solicitud(grupo.getSelectedToggle() == venta ? RonaldmichaTipoVacacion.VENTA : RonaldmichaTipoVacacion.GOCE,
                            desde.getValue(), hasta.getValue(), observacion.getText(), acuerdo.isSelected());
                    RonaldmichaVacacionesService servicio = contexto.vacaciones();
                    List<String> lista = servicio.validar(contexto.sesion(), trabajadorId,
                            original == null ? null : original.id(), s);
                    if (!lista.isEmpty()) {
                        mostrarErrores(errores, lista);
                        return;
                    }
                    try {
                        String mensaje;
                        if (original == null) {
                            RonaldmichaVacacion v = servicio.registrar(contexto.sesion(), trabajadorId, s);
                            mensaje = v.estado() == RonaldmichaEstadoVacacion.PENDIENTE
                                    ? "Solicitud enviada: queda pendiente de aprobación."
                                    : RonaldmichaVacacionesService.descripcion(v) + " registrado.";
                        } else {
                            servicio.editar(contexto.sesion(), original.id(), s);
                            mensaje = "Registro actualizado.";
                        }
                        modal.cerrar();
                        recargar();
                        RonaldmichaToast.exito(raiz, mensaje);
                    } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
                        mostrarErrores(errores, List.of(e.getMessage()));
                    }
                });
        modal.agregarAccion(guardar).mostrar(raiz);
    }

    private static void mostrarErrores(Label errores, List<String> lista) {
        errores.setText(String.join("\n", lista.stream().map(e -> "• " + e).toList()));
        errores.setVisible(true);
        errores.setManaged(true);
    }

    private void adjuntar(RonaldmichaVacacion v) {
        FileChooser selector = new FileChooser();
        selector.setTitle("Adjuntar archivo");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF o imagen (*.pdf, *.png, *.jpg)",
                "*.pdf", "*.png", "*.jpg", "*.jpeg"));
        File archivo = selector.showOpenDialog(raiz.getScene().getWindow());
        if (archivo == null) {
            return;
        }
        try {
            byte[] contenido = Files.readAllBytes(archivo.toPath());
            ejecutar(() -> contexto.vacaciones().adjuntar(contexto.sesion(), v.id(), archivo.getName(), contenido),
                    "Archivo adjuntado: " + archivo.getName());
        } catch (IOException e) {
            RonaldmichaToast.error(raiz, "No se pudo leer el archivo: " + e.getMessage());
        }
    }

    private void ejecutar(Runnable accion, String exito) {
        try {
            accion.run();
            recargar();
            RonaldmichaToast.exito(raiz, exito);
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
        }
    }

    private void recargar() {
        if (!esTrabajador()) {
            cargarTabla();
        }
        if (trabajadorAbierto != null) {
            mostrarDetalle();
        }
        contexto.refrescarAprobaciones();
    }

    private void guardarArchivo(String nombre, String descripcion, String extension, Supplier<byte[]> generar) {
        RonaldmichaArchivos.generarYGuardar(raiz, nombre, descripcion, extension, generar::get);
    }

    private static RonaldmichaExportButton archivo(String texto, String nombre, Supplier<byte[]> generar) {
        RonaldmichaExportButton b = new RonaldmichaExportButton(texto, RonaldmichaExportButton.Formato.de(nombre),
                () -> new RonaldmichaExportButton.Archivo(nombre, generar.get()));
        b.getStyleClass().add("btn-sm");
        return b;
    }

    private boolean esAdmin() {
        return contexto.sesion() != null && contexto.sesion().rol() == RonaldmichaRol.ADMIN;
    }

    private boolean esTrabajador() {
        return contexto.sesion() != null && contexto.sesion().rol() == RonaldmichaRol.TRABAJADOR;
    }

    private static String trunco(Resumen r) {
        return r.truncoMeses() + " m " + r.truncoDias() + " d";
    }

    private static RonaldmichaKpiCard kpi(String titulo, String valor, String detalle) {
        RonaldmichaKpiCard k = new RonaldmichaKpiCard(titulo, valor, detalle);
        k.getStyleClass().add("kpi-sm");
        return k;
    }

    private static <T> TableColumn<T, String> texto(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(valor.apply(c.getValue())));
        return col;
    }

    private static <T> TableColumn<T, Integer> entero(String titulo, double ancho, Function<T, Integer> valor) {
        TableColumn<T, Integer> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.getStyleClass().add("num");
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(valor.apply(c.getValue())));
        return col;
    }

    private static <T> TableColumn<T, T> celdas(String titulo, double ancho, Function<T, Node> nodo) {
        TableColumn<T, T> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(T fila, boolean vacio) {
                super.updateItem(fila, vacio);
                setText(null);
                setGraphic(vacio || fila == null ? null : nodo.apply(fila));
            }
        });
        return col;
    }

    private static Label chip(String texto, String estilo) {
        return RonaldmichaChip.de(texto, estilo);
    }

    private static Button boton(String texto, String estilo, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().add("btn");
        if (estilo != null) {
            b.getStyleClass().add(estilo);
        }
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> accion.run());
        return b;
    }

    private static VBox campo(String etiqueta, Node control) {
        Label l = new Label(etiqueta);
        l.getStyleClass().add("field-label");
        return new VBox(4, l, control);
    }

    private static Label alerta(String texto, String estilo) {
        Label l = new Label(texto);
        l.getStyleClass().addAll("alert", estilo);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private DatePicker selectorFecha() {
        DatePicker d = new DatePicker();
        d.setPromptText("dd/mm/aaaa");
        d.setPrefWidth(160);
        d.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate f) {
                return RonaldmichaFormatos.fecha(f);
            }

            @Override
            public LocalDate fromString(String s) {
                try {
                    return s == null || s.isBlank() ? null : RonaldmichaFormatos.leerFecha(s);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });
        return d;
    }
}
