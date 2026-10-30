package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaDocumentoEmitido;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.domain.RonaldmichaEstadoDocumento;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaTrabajadorResumen;
import com.planillaperu.export.doc.RonaldmichaDocumentModel;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaReporte;
import com.planillaperu.service.RonaldmichaReporte.Formato;
import com.planillaperu.service.RonaldmichaReporte.Grupo;
import com.planillaperu.service.RonaldmichaReportesService;
import com.planillaperu.service.RonaldmichaReportesService.Archivo;
import com.planillaperu.service.RonaldmichaReportesService.Envio;
import com.planillaperu.service.RonaldmichaReportesService.Parametros;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaEmptyState;
import com.planillaperu.ui.components.RonaldmichaExportButton;
import com.planillaperu.ui.components.RonaldmichaIcono;
import com.planillaperu.ui.components.RonaldmichaModal;
import com.planillaperu.ui.components.RonaldmichaToast;
import com.planillaperu.ui.components.RonaldmichaVistaDocumento;
import com.planillaperu.ui.components.RonaldmichaWorkerPicker;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RonaldmichaReportesController {

    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final double ANCHO_TARJETA = 196;
    private static final double ALTO_TARJETA = 64;

    private static final Logger log = LoggerFactory.getLogger(RonaldmichaReportesController.class);

    private final RonaldmichaAppContext contexto;
    private final ChangeListener<YearMonth> alCambiarPeriodo = (o, a, b) -> recargarPool();
    private final RonaldmichaRefresco refrescoEmitidos = new RonaldmichaRefresco(this::cargarEmitidos);
    private final RonaldmichaRefresco refrescoVista = new RonaldmichaRefresco(this::recargarPool);
    private final ChangeListener<RonaldmichaEvento> alCambiarDatos = (o, a, e) -> {
        if (e == null) {
            return;
        }
        if (e.es(RonaldmichaEvento.Tema.DOCUMENTOS)) {
            refrescoEmitidos.pedir();
        } else if (e.tema() != RonaldmichaEvento.Tema.OTRO && e.tema() != RonaldmichaEvento.Tema.SEGURIDAD && this.reporte != null) {
            refrescoVista.pedir();
        }
    };
    private final ToggleGroup tarjetas = new ToggleGroup();
    private final Label titulo = new Label();
    private final Label periodo = new Label();
    private final Label baseLegal = new Label();
    private final FlowPane botones = new FlowPane(8, 8);
    private final VBox opciones = new VBox(8);
    private final CheckBox dosCopias = new CheckBox("Dos copias por hoja: empleador y trabajador");
    private final CheckBox validar = new CheckBox("Validar antes de exportar");
    private final HBox guardado = new HBox(8);
    private final RonaldmichaVistaDocumento vista = new RonaldmichaVistaDocumento();
    private final Label resumenEmitidos = new Label();
    private final ObservableList<RonaldmichaDocumentoEmitido> emitidos = FXCollections.observableArrayList();
    private final TableView<RonaldmichaDocumentoEmitido> tablaEmitidos = new TableView<>(emitidos);
    private RonaldmichaReporte reporte;
    private RonaldmichaExportButton principal;
    private int cantidadTrabajadores;
    private int solicitud;

    @FXML private VBox raiz;
    @FXML private VBox selector;
    @FXML private RonaldmichaWorkerPicker picker;

    public RonaldmichaReportesController(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        List<RonaldmichaReporte> catalogo = servicio().catalogo(contexto.sesion());

        picker.selectedProperty().addListener((o, a, b) -> refrescar());
        dosCopias.setSelected(true);
        dosCopias.selectedProperty().addListener((o, a, b) -> refrescar());
        validar.setSelected(true);

        StackPane contenedorHoja = new StackPane(vista);
        contenedorHoja.setAlignment(Pos.TOP_CENTER);
        contenedorHoja.getStyleClass().add("contenedor-hoja");

        configurarEmitidos();
        raiz.getChildren().setAll(catalogo(catalogo), selector, barraExportacion(), contenedorHoja, registro());
        raiz.getStyleClass().add("vista-reportes");
        contexto.periodoProperty().addListener(new WeakChangeListener<>(alCambiarPeriodo));
        contexto.eventoProperty().addListener(new WeakChangeListener<>(alCambiarDatos));
        raiz.sceneProperty().addListener((o, antes, escena) -> {
            if (escena != null) {
                alEntrar();
            }
        });
        if (!catalogo.isEmpty()) {
            elegirTarjetaInicial();
        }
        cargarEmitidos();
        Platform.runLater(this::recargarPool);
    }

    private void alEntrar() {
        RonaldmichaAtajos.registrarExportar(() -> {
            if (principal != null) {
                principal.exportar();
            }
        });
        if (contexto.seccionSolicitadaProperty().get() == RonaldmichaAppContext.Seccion.BOLETA_PAGO) {
            elegirTarjetaInicial();
        }
    }

    private void elegirTarjetaInicial() {
        boolean boleta = contexto.seccionSolicitadaProperty().get() == RonaldmichaAppContext.Seccion.BOLETA_PAGO;
        contexto.seccionSolicitadaProperty().set(null);
        tarjetas.getToggles().stream()
                .filter(t -> boleta ? t.getUserData() == RonaldmichaReporte.BOLETA_PAGO : t.isSelected())
                .findFirst().orElse(tarjetas.getToggles().getFirst())
                .setSelected(true);
    }

    private VBox catalogo(List<RonaldmichaReporte> catalogo) {
        Map<String, List<RonaldmichaReporte>> grupos = new LinkedHashMap<>();
        if (esTrabajador()) {
            grupos.put("Mis documentos", catalogo);
        } else {
            Map<Grupo, List<RonaldmichaReporte>> porGrupo = catalogo.stream().collect(Collectors.groupingBy(RonaldmichaReporte::grupo,
                    () -> new EnumMap<>(Grupo.class), Collectors.toList()));
            porGrupo.forEach((g, lista) -> grupos.put(g.titulo(), lista));
        }
        VBox caja = new VBox(16);
        caja.getStyleClass().add("catalogo-reportes");
        grupos.forEach((nombre, lista) -> {
            Label g = new Label(nombre.toUpperCase(RonaldmichaFormatos.LOCALE_PE));
            g.getStyleClass().add("grupo-reportes");
            FlowPane fila = new FlowPane(10, 10);
            fila.getStyleClass().add("fila-tarjetas");
            lista.forEach(r -> fila.getChildren().add(tarjeta(r)));
            caja.getChildren().add(new VBox(8, g, fila));
        });
        return caja;
    }

    private ToggleButton tarjeta(RonaldmichaReporte r) {
        Label t = new Label(r.titulo());
        t.getStyleClass().add("tarjeta-titulo");
        Label d = new Label(r.descripcion());
        d.getStyleClass().add("tarjeta-descripcion");
        VBox textos = new VBox(2, t, d);
        textos.setAlignment(Pos.CENTER_LEFT);
        ToggleButton b = new ToggleButton();
        b.setGraphic(textos);
        b.getStyleClass().add("tarjeta-reporte");
        b.setMinSize(ANCHO_TARJETA, ALTO_TARJETA);
        b.setPrefSize(ANCHO_TARJETA, ALTO_TARJETA);
        b.setMaxSize(ANCHO_TARJETA, ALTO_TARJETA);
        b.setTooltip(new Tooltip(r.titulo() + " · " + r.descripcion()));
        b.setAccessibleText(r.titulo() + ". " + r.descripcion());
        b.setUserData(r);
        b.setToggleGroup(tarjetas);
        b.selectedProperty().addListener((o, antes, ahora) -> {
            if (ahora) {
                seleccionar(r);
            } else if (tarjetas.getSelectedToggle() == null) {
                b.setSelected(true);
            }
        });
        return b;
    }

    private VBox barraExportacion() {
        titulo.getStyleClass().add("barra-titulo");
        periodo.getStyleClass().addAll("chip", "neutral");
        HBox lineaTitulo = new HBox(8, titulo, periodo);
        lineaTitulo.setAlignment(Pos.CENTER_LEFT);
        baseLegal.getStyleClass().add("base-legal");
        baseLegal.setWrapText(true);
        VBox textos = new VBox(4, lineaTitulo, baseLegal);
        HBox.setHgrow(textos, Priority.ALWAYS);
        textos.setMaxWidth(Double.MAX_VALUE);
        textos.setMinWidth(240);
        botones.setAlignment(Pos.CENTER_RIGHT);
        botones.setPrefWrapLength(620);
        HBox fila = new HBox(16, textos, botones);
        fila.setAlignment(Pos.CENTER_LEFT);

        guardado.setAlignment(Pos.CENTER_LEFT);
        guardado.getStyleClass().addAll("alert", "info");
        mostrar(guardado, false);

        VBox barra = new VBox(12, fila, opciones, guardado);
        barra.getStyleClass().addAll("card", "barra-exportacion");
        return barra;
    }

    private static VBox opcion(CheckBox casilla, String explicacion) {
        Label ayuda = new Label(explicacion);
        ayuda.getStyleClass().add("opcion-ayuda");
        ayuda.setWrapText(true);
        VBox caja = new VBox(2, casilla, ayuda);
        caja.getStyleClass().add("opcion-exportacion");
        return caja;
    }

    private static String etiqueta(RonaldmichaReporte r, Formato f) {
        return switch (f) {
            case TXT -> "TXT banco";
            case ZIP -> "Archivos PLAME (ZIP)";
            case EXCEL -> r == RonaldmichaReporte.AFPNET ? "Excel AFPnet" : "Excel";
            default -> f.etiqueta();
        };
    }

    private void armarBotones() {
        botones.getChildren().clear();
        principal = null;
        List<Formato> lista = formatos();
        for (int i = 0; i < lista.size(); i++) {
            Formato f = lista.get(i);
            RonaldmichaExportButton b = botonExportar(etiqueta(reporte, f), f, false);
            if (i == 0) {
                b.setGraphic(RonaldmichaIcono.DESCARGA.nodo(16));
                principal = b;
            }
            botones.getChildren().add(b);
        }
        if (reporte.todosEnUnPdf() && !esTrabajador()) {
            RonaldmichaExportButton todos = botonExportar("Todos (" + cantidadTrabajadores + ") en un PDF", Formato.PDF, true);
            todos.setDisable(cantidadTrabajadores == 0);
            Button correo = boton("Enviar por correo", "btn-correo", this::enviar);
            correo.setGraphic(RonaldmichaIcono.CORREO.nodo(16));
            correo.setTooltip(new Tooltip("Envía el PDF al correo del trabajador elegido"));
            botones.getChildren().addAll(todos, correo);
        }
    }

    private void armarOpciones() {
        opciones.getChildren().clear();
        if (reporte == RonaldmichaReporte.BOLETA_PAGO) {
            dosCopias.setText("Dos copias");
            opciones.getChildren().add(opcion(dosCopias,
                    "Dos páginas por trabajador: la copia del empleador y la del trabajador."));
        } else if (reporte.todosEnUnPdf()) {
            dosCopias.setText("Dos copias por hoja: empleador y trabajador");
            opciones.getChildren().add(opcion(dosCopias,
                    "El PDF lleva la copia del empleador y la del trabajador lado a lado en una hoja horizontal."));
        }
        if (!esTrabajador() && formatos().stream().anyMatch(Formato::validaAntes)) {
            opciones.getChildren().add(opcion(validar,
                    "En Excel, TXT y PLAME revisa CUSPP, cuentas y documentos, y avisa antes de generar el archivo."));
        }
        mostrar(opciones, !opciones.getChildren().isEmpty());
    }

    private void seleccionar(RonaldmichaReporte r) {
        reporte = r;
        titulo.setText(r.titulo());
        baseLegal.setText(r.baseLegal());
        mostrar(guardado, false);
        recargarPool();
    }

    private void recargarPool() {
        if (reporte == null) {
            return;
        }
        YearMonth mes = contexto.periodoProperty().get();
        periodo.setText(servicio().periodoTexto(reporte, mes));
        mostrar(selector, reporte.porTrabajador() && !esTrabajador());
        List<RonaldmichaTrabajadorResumen> pool = poolPara(reporte, mes);
        cantidadTrabajadores = pool.size();
        picker.setPool(pool);
        armarBotones();
        armarOpciones();
        refrescar();
    }

    private List<RonaldmichaTrabajadorResumen> poolPara(RonaldmichaReporte r, YearMonth mes) {
        if (!r.porTrabajador() || esTrabajador()) {
            return List.of();
        }
        try {
            return servicio().trabajadores(contexto.sesion(), r, mes);
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            return List.of();
        }
    }

    private void refrescar() {
        if (reporte == null) {
            return;
        }
        int numero = ++solicitud;
        RonaldmichaReporte r = reporte;
        Parametros p = parametros(false);
        RonaldmichaTareas.ejecutar(() -> {
            RonaldmichaEmpresa e = servicio().empresa();
            return new Previa(servicio().vistaPrevia(contexto.sesion(), r, p), e, servicio().avisoEmpresa(e));
        }, previa -> {
            if (numero == solicitud) {
                vista.mostrar(previa.modelo(), previa.empresa(), previa.aviso());
                RonaldmichaAnimaciones.aparecer(vista);
            }
        }, error -> {
            if (numero != solicitud) {
                return;
            }
            if (error instanceof RonaldmichaValidacionException || error instanceof RonaldmichaAccesoDenegadoException) {
                vista.mostrarError(error.getMessage());
            } else {
                log.error("No se pudo generar la vista previa de {}", r.titulo(), error);
                vista.mostrarError("No se pudo generar la vista previa.");
                RonaldmichaToast.error(raiz, "Vista previa de " + r.titulo() + ": " + RonaldmichaTareas.mensaje(error));
            }
            RonaldmichaAnimaciones.aparecer(vista);
        });
    }

    private record Previa(RonaldmichaDocumentModel modelo, RonaldmichaEmpresa empresa, Optional<String> aviso) {
    }

    private Parametros parametros(boolean todos) {
        YearMonth mes = contexto.periodoProperty().get();
        boolean copias = !reporte.todosEnUnPdf() || dosCopias.isSelected();
        if (!reporte.porTrabajador()) {
            return new Parametros(mes, null, false, copias);
        }
        if (esTrabajador()) {
            return new Parametros(mes, contexto.sesion().trabajadorId(), false, copias);
        }
        RonaldmichaTrabajadorResumen t = picker.getSelected();
        return new Parametros(mes, t == null ? null : t.id(), todos, copias);
    }

    private List<Formato> formatos() {
        return servicio().formatos(contexto.sesion(), reporte);
    }

    private RonaldmichaExportButton botonExportar(String texto, Formato formato, boolean todos) {
        RonaldmichaExportButton b = new RonaldmichaExportButton(texto, RonaldmichaExportButton.Formato.valueOf(formato.name()), null);
        RonaldmichaReporte r = reporte;
        Parametros[] p = new Parametros[1];
        Archivo[] generado = new Archivo[1];
        b.conPrevio(continuar -> {
            p[0] = parametros(todos);
            revisarObservaciones(formato, p[0], continuar);
        });
        b.setGenerador(() -> {
            generado[0] = servicio().exportar(contexto.sesion(), r, formato, p[0]);
            return new RonaldmichaExportButton.Archivo(generado[0].nombre(), generado[0].contenido());
        });
        b.alGuardar(destino -> {
            cargarEmitidos();
            mostrarGuardado(destino, generado[0].numeros());
        });
        b.alCancelar(a -> {
            cargarEmitidos();
            RonaldmichaToast.exito(raiz, "Documento registrado (" + rango(generado[0].numeros()) + "); no se guardó el archivo.");
        });
        b.setAccessibleText("Exportar " + r.titulo() + " en " + texto);
        return b;
    }

    private void revisarObservaciones(Formato formato, Parametros p, Runnable continuar) {
        boolean revisar = formato.validaAntes() && validar.isSelected() || reporte == RonaldmichaReporte.BOLETA_PAGO;
        if (revisar && !esTrabajador()) {
            List<String> observaciones;
            try {
                observaciones = servicio().observaciones(contexto.sesion(), reporte, p);
            } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
                RonaldmichaToast.error(raiz, e.getMessage());
                return;
            }
            if (!observaciones.isEmpty()) {
                confirmarObservaciones(observaciones, continuar);
                return;
            }
        }
        continuar.run();
    }

    private void confirmarObservaciones(List<String> observaciones, Runnable continuar) {
        VBox contenido = new VBox(8);
        Label intro = new Label("Revise estas observaciones antes de exportar:");
        intro.setWrapText(true);
        contenido.getChildren().add(intro);
        observaciones.forEach(o -> {
            Label l = new Label("• " + o);
            l.setWrapText(true);
            contenido.getChildren().add(l);
        });
        contenido.getStyleClass().add("observaciones");
        RonaldmichaModal modal = new RonaldmichaModal("Observaciones · " + reporte.titulo(), contenido).ancho(620);
        Button seguir = boton("Exportar de todos modos", "btn-primary", () -> {
            modal.cerrar();
            continuar.run();
        });
        modal.agregarAccion(seguir).mostrar(raiz);
    }

    private static String rango(List<String> numeros) {
        return numeros.size() == 1 ? numeros.getFirst() : numeros.getFirst() + " a " + numeros.getLast();
    }

    private void mostrarGuardado(File destino, List<String> numeros) {
        Label texto = new Label("Archivo guardado: " + destino.getName() + " · "
                + (numeros.size() == 1 ? numeros.getFirst() : numeros.size() + " documentos (" + rango(numeros) + ")"));
        texto.setWrapText(true);
        HBox.setHgrow(texto, Priority.ALWAYS);
        texto.setMaxWidth(Double.MAX_VALUE);
        Hyperlink abrir = new Hyperlink("Abrir carpeta");
        abrir.setOnAction(e -> RonaldmichaArchivos.abrirCarpeta(raiz, destino.toPath().getParent()));
        guardado.getChildren().setAll(texto, abrir);
        mostrar(guardado, true);
    }

    private void enviar() {
        Envio envio;
        try {
            envio = servicio().enviarPorCorreo(contexto.sesion(), reporte, parametros(false));
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
            return;
        }
        cargarEmitidos();
        VBox contenido = new VBox(8);
        Label mensaje = new Label(envio.mensaje());
        mensaje.setWrapText(true);
        mensaje.getStyleClass().addAll("alert", servicio().correoConfigurado() ? "info" : "warning");
        mensaje.setMaxWidth(Double.MAX_VALUE);
        contenido.getChildren().add(mensaje);
        if (envio.sinCorreo().isEmpty()) {
            contenido.getChildren().add(new Label("El trabajador tiene correo registrado."));
        } else {
            Label t = new Label("Sin correo registrado (" + envio.sinCorreo().size() + "):");
            t.getStyleClass().add("celda-nombre");
            contenido.getChildren().add(t);
            envio.sinCorreo().forEach(n -> contenido.getChildren().add(new Label("• " + n)));
        }
        RonaldmichaModal modal = new RonaldmichaModal("Enviar por correo · " + reporte.titulo(), contenido).ancho(600);
        modal.botonCancelar().setText("Cerrar");
        modal.mostrar(raiz);
    }

    private VBox registro() {
        Label tituloEmitidos = new Label("Registro de documentos emitidos");
        tituloEmitidos.getStyleClass().add("title-md");
        resumenEmitidos.getStyleClass().add("text-muted");
        VBox tarjeta = new VBox(10, new VBox(2, tituloEmitidos, resumenEmitidos), tablaEmitidos);
        tarjeta.getStyleClass().addAll("card", "registro-emitidos");
        return tarjeta;
    }

    private void configurarEmitidos() {
        tablaEmitidos.getStyleClass().add("tabla-emitidos");
        tablaEmitidos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaEmitidos.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.CARPETA, "Sin documentos emitidos",
                "Cada exportación queda registrada aquí con su número correlativo."));
        TableColumn<RonaldmichaDocumentoEmitido, String> numero = texto("N°", 150, RonaldmichaDocumentoEmitido::numero);
        numero.getStyleClass().add("mono");
        TableColumn<RonaldmichaDocumentoEmitido, RonaldmichaDocumentoEmitido> estado = new TableColumn<>("Estado");
        estado.setPrefWidth(110);
        estado.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        estado.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(RonaldmichaDocumentoEmitido d, boolean vacio) {
                super.updateItem(d, vacio);
                setText(null);
                if (vacio || d == null) {
                    setGraphic(null);
                    return;
                }
                Label chip = new Label(d.estado().etiqueta());
                chip.getStyleClass().addAll("chip", d.estado().estilo());
                setGraphic(chip);
            }
        });
        TableColumn<RonaldmichaDocumentoEmitido, RonaldmichaDocumentoEmitido> cargo = new TableColumn<>("Cargo");
        cargo.setPrefWidth(170);
        cargo.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        cargo.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(RonaldmichaDocumentoEmitido d, boolean vacio) {
                super.updateItem(d, vacio);
                setText(null);
                setGraphic(null);
                if (vacio || d == null) {
                    return;
                }
                if (!d.requiereCargo()) {
                    setText("No requiere");
                } else if (d.estado() == RonaldmichaEstadoDocumento.RECIBIDO) {
                    setText(d.fechaRecepcion() == null ? "Recibido" : "Recibido " + RonaldmichaFormatos.fecha(d.fechaRecepcion().toLocalDate()));
                } else if (esTrabajador()) {
                    setText("Pendiente");
                } else {
                    setGraphic(boton("Marcar recibido", "btn-sm", () -> marcarCargo(d)));
                }
            }
        });
        List<TableColumn<RonaldmichaDocumentoEmitido, ?>> columnas = List.of(numero,
                texto("Documento", 200, d -> {
                    RonaldmichaReporte r = RonaldmichaReporte.desdeTipo(d.tipo());
                    return r == null ? d.tipo() : r.titulo();
                }),
                texto("Trabajador", 220, d -> d.trabajador() == null ? "Toda la empresa" : d.trabajador()),
                texto("Periodo", 90, d -> d.periodo() == null ? "" : PERIODO.format(d.periodo())),
                texto("Formato", 80, RonaldmichaDocumentoEmitido::formato),
                texto("Fecha", 140, d -> RonaldmichaFormatos.fechaHora(d.fecha())),
                estado, cargo);
        columnas.forEach(c -> c.setSortable(false));
        tablaEmitidos.getColumns().setAll(columnas);
    }

    private void cargarEmitidos() {
        try {
            emitidos.setAll(servicio().emitidos(contexto.sesion()));
        } catch (RonaldmichaAccesoDenegadoException e) {
            emitidos.clear();
        }
        long pendientes = emitidos.stream().filter(RonaldmichaDocumentoEmitido::cargoPendiente).count();
        resumenEmitidos.setText(emitidos.size() + " documento(s) · " + pendientes
                + " pendiente(s) de cargo de recepción");
    }

    private void marcarCargo(RonaldmichaDocumentoEmitido d) {
        try {
            servicio().marcarCargoRecibido(contexto.sesion(), d);
            cargarEmitidos();
            RonaldmichaToast.exito(raiz, "Cargo de " + d.numero() + " recibido.");
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
        }
    }

    private RonaldmichaReportesService servicio() {
        return contexto.reportes();
    }

    private boolean esTrabajador() {
        return contexto.sesion() != null && contexto.sesion().rol() == RonaldmichaRol.TRABAJADOR;
    }

    private static void mostrar(Region nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    private static <T> TableColumn<T, String> texto(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(valor.apply(c.getValue())));
        return col;
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
}
