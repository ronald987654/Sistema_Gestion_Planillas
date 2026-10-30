package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaAprobacionPendiente;
import com.planillaperu.domain.RonaldmichaCuentaUsuario;
import com.planillaperu.domain.RonaldmichaPermiso;
import com.planillaperu.domain.RonaldmichaRegistroAuditoria;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaAuditoriaService;
import com.planillaperu.service.RonaldmichaAuditoriaService.Filtro;
import com.planillaperu.service.RonaldmichaSeguridadService;
import com.planillaperu.service.RonaldmichaSeguridadService.Regla;
import com.planillaperu.service.RonaldmichaUsuarioService.ClaveTemporal;
import com.planillaperu.service.RonaldmichaUsuarioService.NuevoUsuario;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaChip;
import com.planillaperu.ui.components.RonaldmichaConfirmButton;
import com.planillaperu.ui.components.RonaldmichaEmptyState;
import com.planillaperu.ui.components.RonaldmichaIcono;
import com.planillaperu.ui.components.RonaldmichaModal;
import com.planillaperu.ui.components.RonaldmichaToast;
import com.planillaperu.ui.components.RonaldmichaWorkerComboBox;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RonaldmichaUsuariosController {

    private static final int DIAS_AUDITORIA = 30;
    private static final String TODOS_USUARIOS = "Todos los usuarios";
    private static final String TODAS_ACCIONES = "Todas las acciones";

    private final RonaldmichaAppContext contexto;
    private final ToggleGroup secciones = new ToggleGroup();
    private final VBox contenido = new VBox(16);
    private final ObservableList<RonaldmichaCuentaUsuario> cuentas = FXCollections.observableArrayList();
    private final TableView<RonaldmichaCuentaUsuario> tablaUsuarios = new TableView<>(cuentas);
    private final ObservableList<RonaldmichaAprobacionPendiente> pendientes = FXCollections.observableArrayList();
    private final TableView<RonaldmichaAprobacionPendiente> tablaAprobaciones = new TableView<>(pendientes);
    private final ObservableList<RonaldmichaRegistroAuditoria> registros = FXCollections.observableArrayList();
    private final TableView<RonaldmichaRegistroAuditoria> tablaAuditoria = new TableView<>(registros);
    private final DatePicker desde = fecha();
    private final DatePicker hasta = fecha();
    private final ComboBox<String> filtroUsuario = new ComboBox<>();
    private final ComboBox<String> filtroAccion = new ComboBox<>();
    private final Label totalAuditoria = new Label();
    private ToggleButton botonAprobaciones;

    @FXML private VBox raiz;

    public RonaldmichaUsuariosController(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        RonaldmichaAtajos.registrarExportar(this::exportarAuditoria);
        HBox barra = new HBox(4);
        barra.getStyleClass().addAll("segmented", "secciones-seguridad");
        barra.setMaxWidth(Region.USE_PREF_SIZE);
        Map<String, Node> vistas = new java.util.LinkedHashMap<>();
        vistas.put("Usuarios", seccionUsuarios());
        vistas.put("Permisos por rol", seccionPermisos());
        vistas.put("Reglas activas", seccionReglas());
        vistas.put("Aprobaciones", seccionAprobaciones());
        vistas.put("Auditoría", seccionAuditoria());
        vistas.forEach((titulo, nodo) -> {
            ToggleButton b = new ToggleButton(titulo);
            b.getStyleClass().add("seccion-seguridad");
            b.setUserData(nodo);
            b.setToggleGroup(secciones);
            barra.getChildren().add(b);
            if (titulo.equals("Aprobaciones")) {
                botonAprobaciones = b;
            }
        });
        secciones.selectedToggleProperty().addListener((o, anterior, actual) -> {
            if (actual == null) {
                secciones.selectToggle(anterior);
                return;
            }
            contenido.getChildren().setAll((Node) actual.getUserData());
            RonaldmichaAnimaciones.aparecer(contenido);
        });
        raiz.getChildren().setAll(barra, contenido);
        cargarUsuarios();
        cargarAprobaciones();
        cargarFiltros();
        buscarAuditoria();
        secciones.selectToggle(secciones.getToggles().getFirst());
    }

    private Node seccionUsuarios() {
        Label titulo = titulo("Usuarios");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        Button nuevo = boton("+ Nuevo usuario", "btn-primary", this::nuevoUsuario);
        mostrar(nuevo, esAdmin());
        HBox cabecera = new HBox(12, titulo, espacio, nuevo);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        tablaUsuarios.getStyleClass().add("tabla-usuarios");
        tablaUsuarios.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaUsuarios.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.USUARIO, "Sin usuarios",
                "Cree el primer usuario con «+ Nuevo usuario»."));
        TableColumn<RonaldmichaCuentaUsuario, String> usuario = texto("Usuario", 140, RonaldmichaCuentaUsuario::usuario);
        usuario.getStyleClass().add("mono");
        List<TableColumn<RonaldmichaCuentaUsuario, ?>> columnas = new ArrayList<>(List.of(usuario,
                celda("Rol", 170, c -> chip(nombreRol(c.rol()), c.rol() == RonaldmichaRol.TRABAJADOR ? "neutral" : "info")),
                texto("Trabajador", 230, c -> c.trabajador() == null ? "—" : c.trabajador()),
                celda("Estado", 170, this::estado),
                texto("Último acceso", 130, c -> c.ultimoAcceso() == null ? "Nunca" : RonaldmichaFormatos.fechaHora(c.ultimoAcceso()))));
        if (esAdmin()) {
            columnas.add(celda("", 380, this::accionesUsuario));
        }
        columnas.forEach(c -> c.setSortable(false));
        tablaUsuarios.getColumns().setAll(columnas);

        Label nota = new Label(esAdmin()
                ? "Las contraseñas nuevas o restablecidas son temporales: el usuario las cambia en su primer ingreso."
                : "Solo un administrador crea, desactiva o restablece usuarios.");
        nota.getStyleClass().add("text-muted");
        return tarjeta(cabecera, tablaUsuarios, nota);
    }

    private Node estado(RonaldmichaCuentaUsuario c) {
        if (!c.activo()) {
            return chip("Inactivo", "neutral");
        }
        if (contexto.usuarios().bloqueada(c)) {
            return chip("Bloqueado hasta " + RonaldmichaFormatos.fechaHora(c.bloqueadoHasta()).substring(11), "error");
        }
        return c.debeCambiarClave() ? chip("Contraseña temporal", "warning") : chip("Activo", "success");
    }

    private Node accionesUsuario(RonaldmichaCuentaUsuario c) {
        HBox caja = new HBox(6);
        boolean propio = c.id() == contexto.sesion().id();
        if (!propio) {
            caja.getChildren().add(boton("Restablecer contraseña", "btn-sm", () -> restablecer(c)));
        }
        if (contexto.usuarios().bloqueada(c)) {
            caja.getChildren().add(boton("Desbloquear", "btn-sm", () -> accionUsuario(
                    () -> contexto.usuarios().desbloquear(contexto.sesion(), c.id()), c.usuario() + " desbloqueado.")));
        }
        if (c.activo() && !propio) {
            RonaldmichaConfirmButton desactivar = new RonaldmichaConfirmButton("Desactivar", () -> accionUsuario(
                    () -> contexto.usuarios().cambiarActivo(contexto.sesion(), c.id(), false), c.usuario() + " desactivado."));
            desactivar.getStyleClass().add("btn-sm");
            desactivar.setMinWidth(Region.USE_PREF_SIZE);
            caja.getChildren().add(desactivar);
        } else if (!c.activo()) {
            caja.getChildren().add(boton("Activar", "btn-sm", () -> accionUsuario(
                    () -> contexto.usuarios().cambiarActivo(contexto.sesion(), c.id(), true), c.usuario() + " activado.")));
        }
        return caja;
    }

    private void accionUsuario(Runnable accion, String exito) {
        try {
            accion.run();
            cargarUsuarios();
            buscarAuditoria();
            RonaldmichaToast.exito(raiz, exito);
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
        }
    }

    private void restablecer(RonaldmichaCuentaUsuario c) {
        try {
            ClaveTemporal temporal = contexto.usuarios().restablecerClave(contexto.sesion(), c.id());
            cargarUsuarios();
            buscarAuditoria();
            mostrarClave("Contraseña restablecida", temporal);
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
        }
    }

    private void nuevoUsuario() {
        ComboBox<RonaldmichaRol> rol = new ComboBox<>(FXCollections.observableArrayList(RonaldmichaRol.values()));
        rol.setConverter(new StringConverter<>() {
            @Override
            public String toString(RonaldmichaRol r) {
                return r == null ? "" : nombreRol(r);
            }

            @Override
            public RonaldmichaRol fromString(String s) {
                return null;
            }
        });
        rol.setMaxWidth(Double.MAX_VALUE);
        RonaldmichaWorkerComboBox trabajador = new RonaldmichaWorkerComboBox();
        trabajador.setTrabajadores(contexto.trabajadores().activos());
        trabajador.setMaxWidth(Double.MAX_VALUE);
        TextField usuario = new TextField();
        usuario.setPromptText("p. ej. jperez");
        Label ayuda = new Label();
        ayuda.getStyleClass().add("text-muted");
        ayuda.setWrapText(true);
        rol.valueProperty().addListener((o, a, r) -> {
            boolean esTrabajador = r == RonaldmichaRol.TRABAJADOR;
            usuario.setDisable(esTrabajador);
            if (esTrabajador) {
                usuario.clear();
            }
            ayuda.setText(esTrabajador
                    ? "El trabajador entra al portal con su número de documento: ese será su usuario."
                    : "Vincule la cuenta a su ficha de trabajador para aplicar la separación de funciones "
                    + "(no podrá aprobar cambios propios).");
        });
        rol.setValue(RonaldmichaRol.TRABAJADOR);
        Label error = new Label();
        error.getStyleClass().addAll("alert", "error");
        error.setWrapText(true);
        error.setMaxWidth(Double.MAX_VALUE);
        mostrar(error, false);

        VBox cuerpo = new VBox(10, etiqueta("Rol"), rol, etiqueta("Trabajador"), trabajador, etiqueta("Usuario"),
                usuario, ayuda, error);
        RonaldmichaModal modal = new RonaldmichaModal("Nuevo usuario", cuerpo).ancho(480);
        Button crear = boton("Crear usuario", "btn-primary", () -> { });
        crear.setOnAction(e -> {
            try {
                ClaveTemporal temporal = contexto.usuarios().crear(contexto.sesion(), new NuevoUsuario(usuario.getText(),
                        rol.getValue(), trabajador.getValue() == null ? null : trabajador.getValue().id()));
                modal.cerrar();
                cargarUsuarios();
                cargarFiltros();
                buscarAuditoria();
                mostrarClave("Usuario creado", temporal);
            } catch (RonaldmichaValidacionException ex) {
                error.setText(ex.errores().stream().map(m -> "• " + m).collect(Collectors.joining("\n")));
                mostrar(error, true);
            } catch (RonaldmichaAccesoDenegadoException ex) {
                RonaldmichaToast.error(raiz, ex.getMessage());
            }
        });
        modal.agregarAccion(crear).mostrar(raiz);
    }

    private void mostrarClave(String titulo, ClaveTemporal temporal) {
        Label usuario = new Label("Usuario: " + temporal.cuenta().usuario());
        usuario.getStyleClass().add("title-md");
        Label clave = new Label(temporal.clave());
        clave.getStyleClass().addAll("mono", "clave-temporal");
        Button copiar = boton("Copiar", "btn-sm", () -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(temporal.clave());
            Clipboard.getSystemClipboard().setContent(c);
            RonaldmichaToast.exito(raiz, "Contraseña copiada.");
        });
        HBox fila = new HBox(10, clave, copiar);
        fila.setAlignment(Pos.CENTER_LEFT);
        Label nota = new Label("Entréguela al usuario por un medio seguro. Se muestra solo esta vez y deberá "
                + "cambiarla al ingresar.");
        nota.getStyleClass().addAll("alert", "warning");
        nota.setWrapText(true);
        nota.setMaxWidth(Double.MAX_VALUE);
        RonaldmichaModal modal = new RonaldmichaModal(titulo, new VBox(12, usuario, etiqueta("Contraseña temporal"), fila, nota)).ancho(440);
        modal.botonCancelar().setText("Cerrar");
        modal.mostrar(raiz);
    }

    private void cargarUsuarios() {
        try {
            cuentas.setAll(contexto.usuarios().listar(contexto.sesion()));
        } catch (RonaldmichaAccesoDenegadoException e) {
            cuentas.clear();
        }
    }

    private Node seccionPermisos() {
        GridPane matriz = new GridPane();
        matriz.getStyleClass().add("matriz-permisos");
        matriz.add(encabezado("Permiso"), 0, 0);
        RonaldmichaRol[] roles = RonaldmichaRol.values();
        for (int c = 0; c < roles.length; c++) {
            Label r = encabezado(nombreRol(roles[c]));
            r.setMaxWidth(Double.MAX_VALUE);
            r.setAlignment(Pos.CENTER);
            matriz.add(r, c + 1, 0);
        }
        Map<RonaldmichaRol, Set<RonaldmichaPermiso>> permisos = contexto.seguridad().matriz();
        int fila = 1;
        for (RonaldmichaPermiso p : RonaldmichaPermiso.values()) {
            Label grupo = new Label(p.grupo().toUpperCase(RonaldmichaFormatos.LOCALE_PE));
            grupo.getStyleClass().add("permiso-grupo");
            Label descripcion = new Label(p.descripcion());
            descripcion.setWrapText(true);
            VBox nombre = new VBox(2, grupo, descripcion);
            nombre.getStyleClass().add("celda-permiso");
            matriz.add(nombre, 0, fila);
            for (int c = 0; c < roles.length; c++) {
                boolean tiene = permisos.get(roles[c]).contains(p);
                Label marca = new Label(tiene ? "✓" : "—");
                marca.getStyleClass().addAll("celda-permiso", tiene ? "permiso-si" : "permiso-no");
                marca.setMaxWidth(Double.MAX_VALUE);
                marca.setMaxHeight(Double.MAX_VALUE);
                marca.setAlignment(Pos.CENTER);
                matriz.add(marca, c + 1, fila);
            }
            fila++;
        }
        javafx.scene.layout.ColumnConstraints primera = new javafx.scene.layout.ColumnConstraints();
        primera.setHgrow(Priority.ALWAYS);
        matriz.getColumnConstraints().add(primera);
        for (RonaldmichaRol ignored : roles) {
            matriz.getColumnConstraints().add(new javafx.scene.layout.ColumnConstraints(150));
        }
        Label nota = new Label("Solo lectura. Cada servicio verifica estos permisos antes de mostrar datos o ejecutar "
                + "una acción; ocultar botones en pantalla es solo una ayuda visual.");
        nota.getStyleClass().add("text-muted");
        nota.setWrapText(true);
        return tarjeta(titulo("Matriz de permisos por rol"), matriz, nota);
    }

    private Node seccionReglas() {
        VBox lista = new VBox(10);
        for (Regla r : contexto.seguridad().reglas()) {
            Label t = new Label(r.titulo());
            t.getStyleClass().add("title-sm");
            Label activa = chip("Activa", "success");
            Region espacio = new Region();
            HBox.setHgrow(espacio, Priority.ALWAYS);
            HBox cabecera = new HBox(8, t, espacio, activa);
            cabecera.setAlignment(Pos.CENTER_LEFT);
            Label d = new Label(r.descripcion());
            d.setWrapText(true);
            d.getStyleClass().add("text-muted");
            VBox regla = new VBox(4, cabecera, d);
            regla.getStyleClass().add("regla-seguridad");
            lista.getChildren().add(regla);
        }
        VBox seccion = tarjeta(titulo("Reglas activas"), lista);
        if (esAdmin()) {
            Spinner<Integer> minutos = new Spinner<>(RonaldmichaSeguridadService.INACTIVIDAD_MINIMA, RonaldmichaSeguridadService.INACTIVIDAD_MAXIMA,
                    contexto.seguridad().minutosInactividad(), 5);
            minutos.setEditable(true);
            minutos.setPrefWidth(110);
            Button guardar = boton("Guardar", "btn-primary", () -> {
                try {
                    minutos.commitValue();
                    contexto.seguridad().cambiarInactividad(contexto.sesion(), minutos.getValue());
                    buscarAuditoria();
                    RonaldmichaToast.exito(raiz, "Cierre por inactividad: " + minutos.getValue()
                            + " minutos (se aplica desde el próximo inicio de sesión).");
                } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException | NumberFormatException e) {
                    RonaldmichaToast.error(raiz, e instanceof NumberFormatException ? "Ingrese un número de minutos." : e.getMessage());
                }
            });
            HBox fila = new HBox(10, etiqueta("Cerrar la sesión tras"), minutos, etiqueta("minutos sin actividad"), guardar);
            fila.setAlignment(Pos.CENTER_LEFT);
            seccion.getChildren().add(fila);
        }
        return seccion;
    }

    private Node seccionAprobaciones() {
        tablaAprobaciones.getStyleClass().add("tabla-aprobaciones");
        tablaAprobaciones.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaAprobaciones.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.CAMPANA, "Sin aprobaciones pendientes",
                "Los cambios de sueldo, de cuentas y las solicitudes de vacaciones aparecen aquí."));
        TableColumn<RonaldmichaAprobacionPendiente, String> numero = texto("#", 50, a -> String.valueOf(a.id()));
        numero.getStyleClass().add("mono");
        TableColumn<RonaldmichaAprobacionPendiente, RonaldmichaAprobacionPendiente> detalle = celda("Detalle", 320, a -> {
            Label l = new Label(a.detalle());
            l.setWrapText(true);
            return l;
        });
        List<TableColumn<RonaldmichaAprobacionPendiente, ?>> columnas = List.of(numero,
                texto("Tipo", 170, RonaldmichaAprobacionPendiente::tipo),
                texto("Trabajador", 200, a -> a.trabajador() == null ? "—" : a.trabajador()),
                detalle,
                texto("Registrado por", 120, RonaldmichaAprobacionPendiente::registradoPor),
                texto("Fecha", 120, a -> RonaldmichaFormatos.fechaHora(a.fecha())),
                celda("", 200, this::accionesAprobacion));
        columnas.forEach(c -> c.setSortable(false));
        tablaAprobaciones.getColumns().setAll(columnas);
        Label nota = new Label("Separación de funciones: no se puede resolver lo que uno mismo registró ni lo que lo "
                + "afecta (su sueldo, sus cuentas o sus vacaciones).");
        nota.getStyleClass().add("text-muted");
        nota.setWrapText(true);
        return tarjeta(titulo("Cola de aprobaciones"), tablaAprobaciones, nota);
    }

    private Node accionesAprobacion(RonaldmichaAprobacionPendiente a) {
        if (!contexto.aprobaciones().puedeResolver(a, contexto.sesion())) {
            Label l = new Label("La resuelve otro usuario");
            l.getStyleClass().add("text-muted");
            return l;
        }
        return new HBox(6,
                boton("Aprobar", "btn-primary", () -> resolver(() -> contexto.aprobaciones().aprobar(a.id(),
                        contexto.sesion()), "Solicitud #" + a.id() + " aprobada.")),
                boton("Rechazar", null, () -> rechazar(a)));
    }

    private void rechazar(RonaldmichaAprobacionPendiente a) {
        TextArea mensaje = new TextArea();
        mensaje.setPromptText("Motivo del rechazo (lo verá quien registró el cambio)");
        mensaje.setPrefRowCount(3);
        mensaje.setWrapText(true);
        Label detalle = new Label(a.tipo() + (a.trabajador() == null ? "" : " · " + a.trabajador()) + "\n" + a.detalle());
        detalle.setWrapText(true);
        RonaldmichaModal modal = new RonaldmichaModal("Rechazar solicitud #" + a.id(), new VBox(10, detalle, etiqueta("Mensaje"), mensaje))
                .ancho(500);
        RonaldmichaConfirmButton confirmar = new RonaldmichaConfirmButton("Rechazar");
        confirmar.setAccion(() -> {
            try {
                contexto.aprobaciones().rechazar(a.id(), mensaje.getText(), contexto.sesion());
                modal.cerrar();
                despuesDeResolver("Solicitud #" + a.id() + " rechazada.");
            } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
                RonaldmichaToast.error(raiz, e.getMessage());
            }
        });
        modal.agregarAccion(confirmar).mostrar(raiz);
    }

    private void resolver(Runnable accion, String exito) {
        try {
            accion.run();
            despuesDeResolver(exito);
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
        }
    }

    private void despuesDeResolver(String exito) {
        cargarAprobaciones();
        contexto.refrescarAprobaciones();
        buscarAuditoria();
        RonaldmichaToast.exito(raiz, exito);
    }

    private void cargarAprobaciones() {
        try {
            pendientes.setAll(contexto.aprobaciones().pendientes(contexto.sesion()));
        } catch (RonaldmichaAccesoDenegadoException e) {
            pendientes.clear();
        }
        if (botonAprobaciones != null) {
            botonAprobaciones.setText(pendientes.isEmpty() ? "Aprobaciones" : "Aprobaciones (" + pendientes.size() + ")");
        }
    }

    private Node seccionAuditoria() {
        LocalDate hoy = contexto.vacaciones().hoy();
        desde.setValue(hoy.minusDays(DIAS_AUDITORIA));
        hasta.setValue(hoy);
        filtroUsuario.setPrefWidth(180);
        filtroAccion.setPrefWidth(240);
        Button filtrar = boton("Filtrar", "btn-primary", this::buscarAuditoria);
        Button exportar = boton("Exportar Excel", "btn-excel", this::exportarAuditoria);
        HBox filtros = new HBox(10, campo("Desde", desde), campo("Hasta", hasta), campo("Usuario", filtroUsuario),
                campo("Acción", filtroAccion), filtrar, exportar);
        filtros.setAlignment(Pos.BOTTOM_LEFT);
        filtros.getStyleClass().add("filtros-auditoria");

        tablaAuditoria.getStyleClass().add("tabla-auditoria");
        tablaAuditoria.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaAuditoria.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.ESCUDO, "Sin registros",
                "No hay registros con ese filtro.").conAccion("Quitar filtros", () -> {
                    desde.setValue(null);
                    hasta.setValue(null);
                    filtroUsuario.setValue(TODOS_USUARIOS);
                    filtroAccion.setValue(TODAS_ACCIONES);
                    buscarAuditoria();
                }));
        TableColumn<RonaldmichaRegistroAuditoria, String> accion = texto("Acción", 210, RonaldmichaRegistroAuditoria::accion);
        accion.getStyleClass().add("mono");
        List<TableColumn<RonaldmichaRegistroAuditoria, ?>> columnas = List.of(
                texto("Fecha y hora", 130, r -> RonaldmichaFormatos.fechaHora(r.fechaHora())),
                texto("Usuario", 110, RonaldmichaRegistroAuditoria::usuario),
                accion,
                texto("Detalle", 480, r -> r.detalle() == null ? "" : r.detalle()));
        tablaAuditoria.getColumns().setAll(columnas);
        totalAuditoria.getStyleClass().add("text-muted");
        return tarjeta(titulo("Auditoría"), filtros, totalAuditoria, tablaAuditoria);
    }

    private void cargarFiltros() {
        try {
            String u = filtroUsuario.getValue();
            String a = filtroAccion.getValue();
            List<String> usuarios = new ArrayList<>(List.of(TODOS_USUARIOS));
            usuarios.addAll(contexto.auditoria().usuariosRegistrados(contexto.sesion()));
            List<String> acciones = new ArrayList<>(List.of(TODAS_ACCIONES));
            acciones.addAll(contexto.auditoria().acciones(contexto.sesion()));
            filtroUsuario.getItems().setAll(usuarios);
            filtroAccion.getItems().setAll(acciones);
            filtroUsuario.setValue(u == null || !usuarios.contains(u) ? TODOS_USUARIOS : u);
            filtroAccion.setValue(a == null || !acciones.contains(a) ? TODAS_ACCIONES : a);
        } catch (RonaldmichaAccesoDenegadoException e) {
            filtroUsuario.getItems().clear();
            filtroAccion.getItems().clear();
        }
    }

    private Filtro filtro() {
        String u = filtroUsuario.getValue();
        String a = filtroAccion.getValue();
        return new Filtro(desde.getValue(), hasta.getValue(), TODOS_USUARIOS.equals(u) ? null : u,
                TODAS_ACCIONES.equals(a) ? null : a);
    }

    private void buscarAuditoria() {
        try {
            registros.setAll(contexto.auditoria().buscar(contexto.sesion(), filtro()));
            int n = registros.size();
            totalAuditoria.setText(n == RonaldmichaAuditoriaService.MAXIMO_CONSULTA
                    ? "Mostrando los " + n + " registros más recientes: acote el filtro para ver los anteriores."
                    : n + (n == 1 ? " registro" : " registros"));
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
        }
    }

    private void exportarAuditoria() {
        Filtro f;
        try {
            f = filtro();
        } catch (RonaldmichaValidacionException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
            return;
        }
        RonaldmichaArchivos.generarYGuardar(raiz, "auditoria_" + contexto.vacaciones().hoy().toString().replace("-", "")
                + ".xlsx", "Excel (*.xlsx)", "*.xlsx", () -> contexto.auditoria().exportarExcel(contexto.sesion(), f),
                destino -> buscarAuditoria());
    }

    private boolean esAdmin() {
        return contexto.seguridad().tiene(contexto.sesion(), RonaldmichaPermiso.GESTIONAR_USUARIOS);
    }

    static String nombreRol(RonaldmichaRol r) {
        return switch (r) {
            case ADMIN -> "Administrador";
            case APROBADOR -> "Aprobador (gerencia)";
            case TRABAJADOR -> "Trabajador";
        };
    }

    private static VBox tarjeta(Node... nodos) {
        VBox caja = new VBox(12, nodos);
        caja.getStyleClass().add("card");
        return caja;
    }

    private static Label titulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("title-md");
        return l;
    }

    private static Label encabezado(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().addAll("celda-permiso", "encabezado-permiso");
        return l;
    }

    private static Label etiqueta(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("field-label");
        return l;
    }

    private static VBox campo(String texto, Node control) {
        return new VBox(4, etiqueta(texto), control);
    }

    private static Label chip(String texto, String estilo) {
        return RonaldmichaChip.de(texto, estilo);
    }

    private static void mostrar(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    private static <T> TableColumn<T, String> texto(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(valor.apply(c.getValue())));
        return col;
    }

    private static <T> TableColumn<T, T> celda(String titulo, double ancho, Function<T, Node> grafico) {
        TableColumn<T, T> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean vacio) {
                super.updateItem(item, vacio);
                setText(null);
                setGraphic(vacio || item == null ? null : grafico.apply(item));
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
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> accion.run());
        return b;
    }

    private static DatePicker fecha() {
        DatePicker d = new DatePicker();
        d.setPromptText("dd/mm/aaaa");
        d.setPrefWidth(140);
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
