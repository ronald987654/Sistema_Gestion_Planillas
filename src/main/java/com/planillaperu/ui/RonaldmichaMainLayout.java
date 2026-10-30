package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaAprobacionPendiente;
import com.planillaperu.domain.RonaldmichaPermiso;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaSituacionPeriodo;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaAprobacionService;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaChip;
import com.planillaperu.ui.components.RonaldmichaConfirmButton;
import com.planillaperu.ui.components.RonaldmichaEmptyState;
import com.planillaperu.ui.components.RonaldmichaModal;
import com.planillaperu.ui.components.RonaldmichaIcono;
import com.planillaperu.ui.components.RonaldmichaToast;
import com.planillaperu.ui.components.RonaldmichaWorkerComboBox;
import com.planillaperu.ui.components.RonaldmichaWorkerPicker;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class RonaldmichaMainLayout {

    private static final double ANCHO_COLAPSO = 1100;
    private static final double SIDEBAR_ANCHO = 248;
    private static final double SIDEBAR_COLAPSADA = 68;

    private final RonaldmichaAppContext contexto;
    private final ToggleGroup grupoMenu = new ToggleGroup();
    private final List<ToggleButton> items = new ArrayList<>();
    private final List<Node> encabezadosGrupo = new ArrayList<>();
    private final Label badge = new Label();
    private boolean actualizandoPeriodo;
    private RonaldmichaVista vistaActual;
    private final Map<RonaldmichaVista, Node> vistasGuardadas = new EnumMap<>(RonaldmichaVista.class);
    private final ChangeListener<Number> oyenteBadge = (o, a, n) -> actualizarBadge(n.intValue());

    @FXML private StackPane raiz;
    @FXML private VBox sidebar;
    @FXML private Label subtituloMarca;
    @FXML private VBox menu;
    @FXML private Label tituloVista;
    @FXML private Label subtituloVista;
    @FXML private RonaldmichaWorkerComboBox buscador;
    private boolean buscadorPermitido = true;
    @FXML private Button campana;
    @FXML private ComboBox<Month> selectorMes;
    @FXML private ComboBox<Integer> selectorAnio;
    @FXML private Label chipPeriodo;
    private final ChangeListener<RonaldmichaEvento> alCambiarDatos = (o, a, e) -> alEvento(e);
    @FXML private MenuButton menuUsuario;
    @FXML private StackPane contenido;

    public RonaldmichaMainLayout(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        RonaldmichaUsuario usuario = contexto.sesion();
        boolean esTrabajador = usuario.rol() == RonaldmichaRol.TRABAJADOR;

        construirMenu(usuario.rol());
        configurarBuscador(!esTrabajador);
        configurarCampana(!esTrabajador);
        configurarPeriodo();
        configurarMenuUsuario(usuario);

        raiz.widthProperty().addListener((o, a, ancho) -> colapsar(ancho.doubleValue() < ANCHO_COLAPSO));
        colapsar(false);

        RonaldmichaNavegacion.registrar(this::navegar);
        RonaldmichaNavegacion.registrarAprobaciones(this::mostrarAprobaciones);
        raiz.sceneProperty().addListener((o, anterior, escena) -> {
            if (escena != null && anterior == null) {
                configurarAtajos(escena);
            }
        });
        grupoMenu.selectToggle(items.getFirst());
    }

    public void navegar(RonaldmichaVista vista) {
        for (ToggleButton item : items) {
            if (item.getUserData() == vista) {
                if (item.isSelected()) {
                    mostrar(vista);
                } else {
                    grupoMenu.selectToggle(item);
                }
                return;
            }
        }
    }

    private void mostrar(RonaldmichaVista vista) {
        if (!vista.permitidaPara(contexto.sesion().rol())) {
            return;
        }
        vistaActual = vista;
        tituloVista.setText(vista.titulo());
        subtituloVista.setText(subtitulo(vista, contexto.periodoProperty().get()));
        RonaldmichaAtajos.limpiar();
        Node nodo;
        try {
            contexto.seguridad().exigirAlguno(contexto.sesion(), vista.permisos(), vista.titulo());
            nodo = vistasGuardadas.get(vista);
            if (nodo == null) {
                nodo = vista.fxml() == null ? vistaVacia(vista) : RonaldmichaRecursos.cargarVista(vista.fxml(), contexto);
                nodo = RonaldmichaPagina.envolver(nodo);
                if (vista == RonaldmichaVista.REPORTES) {
                    vistasGuardadas.put(vista, nodo);
                }
            }
        } catch (RonaldmichaAccesoDenegadoException e) {
            nodo = RonaldmichaPagina.envolver(sinPermiso(e.getMessage()));
        }
        contenido.getChildren().setAll(nodo);
        nodo.applyCss();
        RonaldmichaEstilos.aplicar(nodo);
        RonaldmichaAnimaciones.aparecer(nodo);
    }

    private Node vistaVacia(RonaldmichaVista vista) {
        RonaldmichaEmptyState vacio = new RonaldmichaEmptyState(vista.icono() == null ? RonaldmichaIcono.VACIO : vista.icono(),
                vista.titulo(), "Este módulo aún no tiene contenido.");
        VBox caja = new VBox(vacio);
        caja.getStyleClass().add("view");
        caja.setAlignment(Pos.CENTER);
        return caja;
    }

    private Node sinPermiso(String mensaje) {
        VBox caja = new VBox(new RonaldmichaEmptyState(RonaldmichaIcono.ESCUDO, "Sin permiso", mensaje));
        caja.getStyleClass().add("view");
        caja.setAlignment(Pos.CENTER);
        return caja;
    }

    private void construirMenu(RonaldmichaRol rol) {
        for (RonaldmichaVista.Grupo grupo : RonaldmichaVista.menu(rol)) {
            if (grupo.titulo() != null) {
                Label encabezado = new Label(grupo.titulo().toUpperCase(RonaldmichaFormatos.LOCALE_PE));
                encabezado.getStyleClass().add("sidebar-group");
                Region separador = new Region();
                separador.getStyleClass().add("sidebar-separator");
                VBox.setMargin(separador, new Insets(10, 8, 0, 8));
                menu.getChildren().addAll(encabezado, separador);
                encabezadosGrupo.add(encabezado);
                encabezadosGrupo.add(separador);
            }
            for (RonaldmichaVista vista : grupo.vistas()) {
                ToggleButton item = new ToggleButton(vista.titulo(), vista.icono().nodo());
                item.getStyleClass().add("sidebar-item");
                item.setMaxWidth(Double.MAX_VALUE);
                item.setUserData(vista);
                item.setToggleGroup(grupoMenu);
                item.setTooltip(new Tooltip(vista.titulo()));
                menu.getChildren().add(item);
                items.add(item);
            }
        }
        grupoMenu.selectedToggleProperty().addListener((o, anterior, actual) -> {
            if (actual == null) {
                grupoMenu.selectToggle(anterior);
            } else {
                mostrar((RonaldmichaVista) actual.getUserData());
            }
        });
    }

    private void colapsar(boolean colapsada) {
        sidebar.setPrefWidth(colapsada ? SIDEBAR_COLAPSADA : SIDEBAR_ANCHO);
        sidebar.setMinWidth(colapsada ? SIDEBAR_COLAPSADA : SIDEBAR_ANCHO);
        if (colapsada) {
            if (!sidebar.getStyleClass().contains("collapsed")) {
                sidebar.getStyleClass().add("collapsed");
            }
        } else {
            sidebar.getStyleClass().remove("collapsed");
        }
        subtituloMarca.setVisible(!colapsada);
        subtituloMarca.setManaged(!colapsada);
        buscador.setVisible(buscadorPermitido && !colapsada);
        buscador.setManaged(buscadorPermitido && !colapsada);
        for (Node n : encabezadosGrupo) {
            n.setVisible(!colapsada || !(n instanceof Label));
            n.setManaged(n.isVisible());
        }
        for (ToggleButton item : items) {
            item.setContentDisplay(colapsada ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
        }
    }

    private void configurarBuscador(boolean visible) {
        buscadorPermitido = visible;
        buscador.setVisible(visible);
        buscador.setManaged(visible);
        if (!visible) {
            return;
        }
        buscador.setTrabajadores(contexto.trabajadores().activos());
        buscador.valueProperty().addListener((o, a, trabajador) -> {
            if (trabajador == null) {
                return;
            }
            contexto.trabajadorSeleccionadoProperty().set(trabajador.id());
            RonaldmichaWorkerPicker picker = pickerVisible();
            if (picker != null) {
                picker.seleccionarPorId(trabajador.id());
            }
            if (picker == null || picker.getSelected() == null || picker.getSelected().id() != trabajador.id()) {
                navegar(RonaldmichaVista.TRABAJADORES);
            }
            Platform.runLater(() -> buscador.setValue(null));
        });
    }

    private RonaldmichaWorkerPicker pickerVisible() {
        for (Node n : contenido.lookupAll(".worker-picker")) {
            if (n instanceof RonaldmichaWorkerPicker picker && visibleEnArbol(picker)) {
                return picker;
            }
        }
        return null;
    }

    private static boolean visibleEnArbol(Node nodo) {
        for (Node n = nodo; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private void configurarCampana(boolean visible) {
        campana.setVisible(visible);
        campana.setManaged(visible);
        badge.getStyleClass().add("badge");
        badge.setMouseTransparent(true);
        StackPane grafico = new StackPane(RonaldmichaIcono.CAMPANA.nodo(), badge);
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        grafico.setPrefSize(26, 26);
        campana.setGraphic(grafico);
        campana.setTooltip(new Tooltip("Aprobaciones pendientes"));
        if (visible) {
            contexto.aprobacionesPendientesProperty().addListener(new WeakChangeListener<>(oyenteBadge));
            contexto.refrescarAprobaciones();
            actualizarBadge(contexto.aprobacionesPendientesProperty().get());
        }
    }

    private void actualizarBadge(int pendientes) {
        badge.setText(pendientes > 99 ? "99+" : String.valueOf(pendientes));
        badge.setVisible(pendientes > 0);
    }

    @FXML
    private void mostrarAprobaciones() {
        List<RonaldmichaAprobacionPendiente> pendientes = contexto.aprobaciones().pendientes(contexto.sesion());
        contexto.refrescarAprobaciones();
        ContextMenu lista = new ContextMenu();
        if (pendientes.isEmpty()) {
            MenuItem vacio = new MenuItem("No hay aprobaciones pendientes");
            vacio.setDisable(true);
            lista.getItems().add(vacio);
        } else {
            for (RonaldmichaAprobacionPendiente a : pendientes) {
                String trabajador = a.trabajador() == null ? "" : " · " + a.trabajador();
                MenuItem item = new MenuItem(a.tipo() + trabajador + " · " + RonaldmichaFormatos.fecha(a.fecha().toLocalDate()));
                item.setOnAction(e -> mostrarSolicitud(a));
                lista.getItems().add(item);
            }
        }
        lista.show(campana, Side.BOTTOM, 0, 4);
    }

    private void mostrarSolicitud(RonaldmichaAprobacionPendiente a) {
        Label detalle = new Label(a.detalle());
        detalle.setWrapText(true);
        detalle.getStyleClass().add("title-md");
        Label origen = new Label((a.trabajador() == null ? "" : "Trabajador: " + a.trabajador() + "\n")
                + "Registrado por " + a.registradoPor() + " el " + RonaldmichaFormatos.fechaHora(a.fecha()));
        origen.setWrapText(true);
        origen.getStyleClass().add("text-muted");
        VBox contenido = new VBox(12, detalle, origen);
        RonaldmichaModal modal = new RonaldmichaModal(a.tipo() + " · solicitud #" + a.id(), contenido).ancho(560);

        boolean vacaciones = RonaldmichaAprobacionService.esVacaciones(a.tipo());
        if (!contexto.aprobaciones().puedeResolver(a, contexto.sesion())) {
            Label aviso = new Label(vacaciones ? "Esta solicitud debe resolverla otro usuario (administrador o aprobador)."
                    : "Este cambio debe aprobarlo otro usuario con rol APROBADOR.");
            aviso.getStyleClass().addAll("alert", "info");
            aviso.setWrapText(true);
            aviso.setMaxWidth(Double.MAX_VALUE);
            contenido.getChildren().add(aviso);
            modal.botonCancelar().setText("Cerrar");
            modal.mostrar(raiz);
            return;
        }

        TextArea motivo = new TextArea();
        motivo.setPromptText("Motivo del rechazo (obligatorio para rechazar)");
        motivo.setPrefRowCount(2);
        motivo.setWrapText(true);
        contenido.getChildren().add(motivo);

        RonaldmichaConfirmButton rechazar = new RonaldmichaConfirmButton("Rechazar");
        Button aprobar = new Button(vacaciones ? "Aprobar" : "Aprobar y aplicar");
        aprobar.getStyleClass().addAll("btn", "btn-primary");
        rechazar.setAccion(() -> resolver(modal, () -> contexto.aprobaciones().rechazar(a.id(), motivo.getText(),
                contexto.sesion()), "Solicitud #" + a.id() + " rechazada."));
        aprobar.setOnAction(e -> resolver(modal, () -> contexto.aprobaciones().aprobar(a.id(), contexto.sesion()),
                vacaciones ? "Solicitud #" + a.id() + " aprobada: las vacaciones quedan programadas."
                        : "Solicitud #" + a.id() + " aprobada: el cambio ya se aplicó."));
        modal.agregarAccion(rechazar).agregarAccion(aprobar).mostrar(raiz);
    }

    private void resolver(RonaldmichaModal modal, Runnable accion, String exito) {
        try {
            accion.run();
            modal.cerrar();
            contexto.refrescarAprobaciones();
            if (vistaActual != null) {
                mostrar(vistaActual);
            }
            RonaldmichaToast.exito(raiz, exito);
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException ex) {
            RonaldmichaToast.error(raiz, ex.getMessage());
        }
    }

    private void configurarPeriodo() {
        selectorMes.getItems().setAll(Month.values());
        selectorMes.setConverter(new StringConverter<>() {
            @Override
            public String toString(Month m) {
                if (m == null) {
                    return "";
                }
                String nombre = m.getDisplayName(TextStyle.FULL_STANDALONE, RonaldmichaFormatos.LOCALE_PE);
                return nombre.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + nombre.substring(1);
            }

            @Override
            public Month fromString(String s) {
                return null;
            }
        });
        actualizarAnios(contexto.periodoProperty().get());

        selectorMes.valueProperty().addListener((o, a, b) -> periodoElegido());
        selectorAnio.valueProperty().addListener((o, a, b) -> periodoElegido());
        contexto.periodoProperty().addListener((o, a, periodo) -> mostrarPeriodo(periodo));
        contexto.eventoProperty().addListener(new WeakChangeListener<>(alCambiarDatos));
        mostrarPeriodo(contexto.periodoProperty().get());
    }

    private void periodoElegido() {
        if (actualizandoPeriodo || selectorMes.getValue() == null || selectorAnio.getValue() == null) {
            return;
        }
        contexto.periodoProperty().set(YearMonth.of(selectorAnio.getValue(), selectorMes.getValue()));
    }

    private void actualizarAnios(YearMonth periodo) {
        int actual = contexto.periodos().mesActual().getYear();
        int desde = contexto.parametros().listar().stream().mapToInt(v -> v.vigenteDesde().getYear()).min()
                .orElse(actual - 1);
        int hasta = Math.max(actual, periodo == null ? actual : periodo.getYear()) + 1;
        desde = Math.min(desde, periodo == null ? desde : periodo.getYear());
        List<Integer> anios = IntStream.rangeClosed(desde, hasta).boxed().toList();
        if (!anios.equals(selectorAnio.getItems())) {
            actualizandoPeriodo = true;
            selectorAnio.getItems().setAll(anios);
            actualizandoPeriodo = false;
        }
    }

    private static String subtitulo(RonaldmichaVista vista, YearMonth periodo) {
        String mes = periodo == null ? "" : RonaldmichaPlanillaDetalle.mesTexto(periodo);
        return switch (vista) {
            case INICIO -> "Resumen de " + mes;
            case TRABAJADORES -> "Fichas, contratos, remuneración y cuentas";
            case ASISTENCIA, MI_ASISTENCIA -> "Marcaciones de " + mes;
            case VACACIONES, MIS_VACACIONES -> "Récord vacacional a la fecha";
            case PLANILLA_MENSUAL -> "Periodo " + mes;
            case RESUMEN_ANUAL -> periodo == null ? "Año" : "Año " + periodo.getYear();
            case REPORTES -> "Documentos y declaraciones de " + mes;
            case GRATIFICACIONES, CTS -> "Semestre vigente en " + mes;
            case LIQUIDACIONES -> "Beneficios sociales al cese";
            case EMPRESA -> "Identificación, domicilio, representante y boleta";
            case PARAMETROS -> "RMV, UIT y tasas vigentes en " + mes;
            case USUARIOS -> "Usuarios, permisos, aprobaciones y auditoría";
            case MIS_DOCUMENTOS -> "Boletas, constancias y cargos de recepción";
            case MIS_DATOS -> "Datos personales y de contacto";
        };
    }

    private void alEvento(RonaldmichaEvento e) {
        if (e == null || contexto.sesion() == null) {
            return;
        }
        if (e.es(RonaldmichaEvento.Tema.PERIODO)) {
            mostrarPeriodo(contexto.periodoProperty().get());
        }
        if (e.es(RonaldmichaEvento.Tema.SEGURIDAD, RonaldmichaEvento.Tema.TRABAJADORES, RonaldmichaEvento.Tema.VACACIONES)) {
            contexto.refrescarAprobaciones();
        }
    }

    private void mostrarPeriodo(YearMonth periodo) {
        actualizarAnios(periodo);
        if (vistaActual != null) {
            subtituloVista.setText(subtitulo(vistaActual, periodo));
        }
        actualizandoPeriodo = true;
        selectorMes.setValue(periodo.getMonth());
        selectorAnio.setValue(periodo.getYear());
        actualizandoPeriodo = false;

        RonaldmichaSituacionPeriodo situacion = contexto.periodos().situacion(periodo);
        RonaldmichaChip.actualizar(chipPeriodo, situacion.etiqueta());
    }

    private void configurarMenuUsuario(RonaldmichaUsuario usuario) {
        menuUsuario.setText(usuario.usuario());
        menuUsuario.setGraphic(RonaldmichaIcono.USUARIO.nodo(18));

        MenuItem rol = new MenuItem("Rol: " + usuario.rol());
        rol.setDisable(true);
        CheckMenuItem reducir = new CheckMenuItem("Reducir movimiento");
        reducir.setSelected(RonaldmichaPreferencias.reducirMovimiento());
        reducir.selectedProperty().addListener((o, a, valor) -> {
            RonaldmichaPreferencias.setReducirMovimiento(valor);
            RonaldmichaVentanas.marcarMovimiento(raiz);
        });
        MenuItem clave = new MenuItem("Cambiar contraseña", RonaldmichaIcono.ESCUDO.nodo(16));
        clave.setOnAction(e -> RonaldmichaCambioClave.mostrar(raiz, contexto, null,
                () -> RonaldmichaToast.exito(raiz, "Contraseña actualizada."), () -> { }));
        MenuItem salir = new MenuItem("Cerrar sesión", RonaldmichaIcono.SALIR.nodo(16));
        salir.setOnAction(e -> cerrarSesion());

        menuUsuario.getItems().setAll(rol, reducir, clave);
        if (usuario.rol() == RonaldmichaRol.ADMIN) {
            MenuItem respaldar = new MenuItem("Respaldar base de datos…", RonaldmichaIcono.CARPETA.nodo(16));
            respaldar.setOnAction(e -> respaldar());
            MenuItem restaurar = new MenuItem("Restaurar base de datos…");
            restaurar.setOnAction(e -> confirmarRestauracion());
            menuUsuario.getItems().addAll(new SeparatorMenuItem(), respaldar, restaurar);
        }
        menuUsuario.getItems().addAll(new SeparatorMenuItem(), salir);
    }

    private void cerrarSesion() {
        contexto.cerrarSesion();
        Stage stage = (Stage) raiz.getScene().getWindow();
        RonaldmichaVentanas.mostrarLogin(stage, contexto);
        RonaldmichaToast.exito(stage.getScene().getRoot(), "Sesión cerrada.");
    }

    private void configurarAtajos(Scene escena) {
        KeyCombination buscar = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
        KeyCombination nuevo = new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN);
        KeyCombination exportar = new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN);
        escena.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (raiz.lookup(".modal-overlay") != null) {
                return;
            }
            if (buscar.match(e) && buscador.isVisible()) {
                e.consume();
                buscador.requestFocus();
                buscador.getEditor().selectAll();
            } else if (nuevo.match(e)) {
                e.consume();
                if (contexto.seguridad().tiene(contexto.sesion(), RonaldmichaPermiso.EDITAR_TRABAJADORES)) {
                    contexto.nuevoTrabajadorSolicitadoProperty().set(true);
                    navegar(RonaldmichaVista.TRABAJADORES);
                } else {
                    RonaldmichaToast.error(raiz, "Su usuario no puede registrar trabajadores.");
                }
            } else if (exportar.match(e)) {
                e.consume();
                if (!RonaldmichaAtajos.exportar()) {
                    RonaldmichaToast.error(raiz, "Esta vista no tiene exportación.");
                }
            }
        });
    }

    private void respaldar() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Respaldar base de datos");
        selector.setInitialFileName(contexto.respaldo().nombreSugerido());
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base de datos SQLite (*.db)", "*.db"));
        File destino = selector.showSaveDialog(raiz.getScene().getWindow());
        if (destino == null) {
            return;
        }
        try {
            contexto.respaldo().respaldar(contexto.sesion(), destino.toPath());
            RonaldmichaToast.exito(raiz, "Copia de seguridad guardada: " + destino.getName());
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException ex) {
            RonaldmichaToast.error(raiz, ex.getMessage());
        }
    }

    private void confirmarRestauracion() {
        Label texto = new Label("La base actual se reemplazará por la copia que elija (trabajadores, planillas, "
                + "usuarios y auditoría). Antes se guarda automáticamente una copia de la base actual junto a ella. "
                + "Al terminar deberá iniciar sesión de nuevo.");
        texto.setWrapText(true);
        RonaldmichaModal modal = new RonaldmichaModal("Restaurar base de datos", texto).ancho(480);
        RonaldmichaConfirmButton elegir = new RonaldmichaConfirmButton("Elegir copia y restaurar");
        elegir.setAccion(() -> {
            modal.cerrar();
            restaurar();
        });
        modal.agregarAccion(elegir).mostrar(raiz);
    }

    private void restaurar() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Restaurar base de datos");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base de datos SQLite (*.db)", "*.db"));
        File origen = selector.showOpenDialog(raiz.getScene().getWindow());
        if (origen == null) {
            return;
        }
        try {
            java.nio.file.Path previo = contexto.respaldo().restaurar(contexto.sesion(), origen.toPath());
            contexto.cerrarSesion();
            Stage stage = (Stage) raiz.getScene().getWindow();
            RonaldmichaVentanas.mostrarLogin(stage, contexto);
            RonaldmichaToast.exito(stage.getScene().getRoot(), "Base restaurada desde " + origen.getName()
                    + ". La anterior quedó en " + previo.getFileName() + ". Inicie sesión.");
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException ex) {
            RonaldmichaToast.error(raiz, ex.getMessage());
        }
    }
}
