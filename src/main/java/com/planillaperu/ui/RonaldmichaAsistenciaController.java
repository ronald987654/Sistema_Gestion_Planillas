package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.Dia;
import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.EstadoDia;
import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.Resumen;
import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.TipoDia;
import com.planillaperu.calc.RonaldmichaHorasExtraCalculator.Importes;
import com.planillaperu.calc.RonaldmichaJornadaCalculator.Jornada;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.common.RonaldmichaHoras;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.domain.RonaldmichaFeriado;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.domain.RonaldmichaHorario;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.domain.RonaldmichaMarcacion;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.domain.RonaldmichaNovedad;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.domain.RonaldmichaOrigenAsistencia;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.domain.RonaldmichaTrabajadorResumen;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.domain.RonaldmichaTratamientoHe;
import com.planillaperu.export.RonaldmichaAsistenciaExcel;
import com.planillaperu.export.RonaldmichaLectorTabla;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaAsistenciaService;
import com.planillaperu.service.RonaldmichaAsistenciaService.MesAsistencia;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaExportButton;
import com.planillaperu.ui.components.RonaldmichaChip;
import com.planillaperu.ui.components.RonaldmichaConfirmButton;
import com.planillaperu.ui.components.RonaldmichaEmptyState;
import com.planillaperu.ui.components.RonaldmichaIcono;
import com.planillaperu.ui.components.RonaldmichaKpiCard;
import com.planillaperu.ui.components.RonaldmichaModal;
import com.planillaperu.ui.components.RonaldmichaToast;
import com.planillaperu.ui.components.RonaldmichaWorkerPicker;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class RonaldmichaAsistenciaController {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final RonaldmichaAppContext contexto;
    private final ObservableList<Dia> dias = FXCollections.observableArrayList();
    private final ChangeListener<YearMonth> alCambiarPeriodo = (o, a, b) -> cargar();
    private MesAsistencia actual;
    private boolean editable;

    private final Label tituloHorario = new Label();
    private final TextField horaIngreso = hora();
    private final TextField horaSalida = hora();
    private final TextField refrigerio = numero();
    private final TextField tolerancia = numero();
    private final Map<DayOfWeek, ToggleButton> diasLaborables = new EnumMap<>(DayOfWeek.class);
    private final CheckBox noFiscalizado =
            new CheckBox("Personal de dirección o no fiscalizado (sin sobretiempo ni tardanzas)");
    private final Label jornada = new Label();
    private final Label erroresHorario = new Label();
    private final Button guardarHorario = new Button("Guardar horario");

    @FXML private RonaldmichaWorkerPicker picker;
    @FXML private Label titulo;
    @FXML private VBox tarjetaHorario;
    @FXML private Label tituloResumen;
    @FXML private Label chipOrigen;
    @FXML private Label chipSoloLectura;
    @FXML private FlowPane kpis;
    @FXML private FlowPane chipsFeriados;
    @FXML private HBox agregarFeriado;
    @FXML private VBox alertas;
    @FXML private FlowPane acciones;
    @FXML private TableView<Dia> tabla;
    @FXML private TableColumn<Dia, String> colFecha;
    @FXML private TableColumn<Dia, String> colDia;
    @FXML private TableColumn<Dia, Dia> colTipo;
    @FXML private TableColumn<Dia, String> colIngreso;
    @FXML private TableColumn<Dia, String> colSalida;
    @FXML private TableColumn<Dia, String> colHoras;
    @FXML private TableColumn<Dia, String> colTardanza;
    @FXML private TableColumn<Dia, String> colSobretiempo;
    @FXML private TableColumn<Dia, Dia> colEstado;
    @FXML private TableColumn<Dia, RonaldmichaNovedad> colNovedad;
    @FXML private TableColumn<Dia, Dia> colHorasExtra;

    public RonaldmichaAsistenciaController(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        RonaldmichaAtajos.registrarExportar(() -> acciones.getChildren().stream()
                .filter(RonaldmichaExportButton.class::isInstance).map(RonaldmichaExportButton.class::cast)
                .filter(b -> b.getText().startsWith("Exportar") || b.getText().startsWith("Descargar"))
                .findFirst().ifPresent(RonaldmichaExportButton::exportar));
        construirHorario();
        configurarTabla();
        if (esTrabajador()) {
            mostrar(picker, false);
        } else {
            mostrar(titulo, false);
            picker.setPool(contexto.trabajadores().activos());
            picker.seleccionarPorId(contexto.trabajadorSeleccionadoProperty().get());
            picker.selectedProperty().addListener((o, a, t) -> {
                if (t != null) {
                    contexto.trabajadorSeleccionadoProperty().set(t.id());
                }
                cargar();
            });
        }
        contexto.periodoProperty().addListener(new WeakChangeListener<>(alCambiarPeriodo));
        cargar();
    }

    private Long trabajadorId() {
        if (esTrabajador()) {
            return contexto.sesion().trabajadorId();
        }
        RonaldmichaTrabajadorResumen t = picker.getSelected();
        return t == null ? null : t.id();
    }

    private void cargar() {
        Long id = trabajadorId();
        YearMonth mes = contexto.periodoProperty().get();
        tituloResumen.setText("Resumen de " + mesTexto(mes));
        actual = null;
        if (id == null) {
            limpiar("Elija un trabajador para ver su asistencia.");
            return;
        }
        try {
            actual = contexto.asistencia().consultar(contexto.sesion(), id, mes);
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            limpiar(e.getMessage());
            return;
        }
        editable = esAdmin() && !actual.soloLectura();
        RonaldmichaTrabajador t = actual.trabajador();
        titulo.setText("Mi asistencia · " + t.nombreCompleto());
        mostrarHorario(t, actual.horario());
        mostrarResumen(actual);
        tabla.setEditable(editable);
        dias.setAll(actual.dias());
    }

    private void limpiar(String mensaje) {
        tarjetaHorario.setDisable(true);
        tituloHorario.setText("Horario");
        chipOrigen.setText(RonaldmichaOrigenAsistencia.SIN_REGISTRO.etiqueta());
        mostrar(chipSoloLectura, false);
        kpis.getChildren().clear();
        chipsFeriados.getChildren().clear();
        agregarFeriado.getChildren().clear();
        acciones.getChildren().clear();
        alertas.getChildren().setAll(alerta(mensaje, "info"));
        dias.clear();
    }

    private void construirHorario() {
        tituloHorario.getStyleClass().add("title-md");
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        g.add(campo("Ingreso", horaIngreso), 0, 0);
        g.add(campo("Salida", horaSalida), 1, 0);
        g.add(campo("Refrigerio (min)", refrigerio), 0, 1);
        g.add(campo("Tolerancia (min)", tolerancia), 1, 1);

        FlowPane botones = new FlowPane(6, 6);
        for (DayOfWeek d : DayOfWeek.values()) {
            ToggleButton b = new ToggleButton(diaCorto(d));
            b.getStyleClass().add("dia-toggle");
            diasLaborables.put(d, b);
            botones.getChildren().add(b);
            b.selectedProperty().addListener((o, a, s) -> actualizarJornada());
        }
        noFiscalizado.setWrapText(true);
        jornada.getStyleClass().add("chip");
        jornada.setMinWidth(Label.USE_PREF_SIZE);
        erroresHorario.getStyleClass().addAll("alert", "error");
        erroresHorario.setWrapText(true);
        erroresHorario.setMaxWidth(Double.MAX_VALUE);
        mostrar(erroresHorario, false);
        guardarHorario.getStyleClass().addAll("btn", "btn-primary");
        guardarHorario.setOnAction(e -> guardarHorario());

        for (TextField campo : List.of(horaIngreso, horaSalida, refrigerio, tolerancia)) {
            campo.textProperty().addListener((o, a, b) -> actualizarJornada());
        }
        noFiscalizado.selectedProperty().addListener((o, a, b) -> actualizarJornada());

        tarjetaHorario.getChildren().setAll(tituloHorario, g, campo("Días laborables", botones), noFiscalizado,
                jornada, erroresHorario, guardarHorario);
    }

    private void mostrarHorario(RonaldmichaTrabajador t, RonaldmichaHorario h) {
        tarjetaHorario.setDisable(false);
        tituloHorario.setText("Horario de " + t.nombres() + " " + t.apPaterno());
        horaIngreso.setText(RonaldmichaHoras.texto(h.horaIngreso()));
        horaSalida.setText(RonaldmichaHoras.texto(h.horaSalida()));
        refrigerio.setText(String.valueOf(h.refrigerioMin()));
        tolerancia.setText(String.valueOf(h.toleranciaMin()));
        diasLaborables.forEach((d, b) -> b.setSelected(h.diasLaborables().contains(d)));
        noFiscalizado.setSelected(h.noFiscalizado());
        mostrar(erroresHorario, false);

        boolean admin = esAdmin();
        for (Node n : List.of(horaIngreso, horaSalida, refrigerio, tolerancia, noFiscalizado)) {
            n.setDisable(!admin);
        }
        diasLaborables.values().forEach(b -> b.setDisable(!admin));
        mostrar(guardarHorario, admin);
        actualizarJornada();
    }

    private RonaldmichaHorario leerHorario() {
        try {
            LocalTime ingreso = RonaldmichaHoras.leer(horaIngreso.getText());
            LocalTime salida = RonaldmichaHoras.leer(horaSalida.getText());
            if (ingreso == null || salida == null) {
                return null;
            }
            Set<DayOfWeek> elegidos = new TreeSet<>();
            diasLaborables.forEach((d, b) -> {
                if (b.isSelected()) {
                    elegidos.add(d);
                }
            });
            return new RonaldmichaHorario(ingreso, salida, entero(refrigerio), entero(tolerancia), elegidos,
                    noFiscalizado.isSelected());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return null;
        }
    }

    private void actualizarJornada() {
        RonaldmichaHorario h = leerHorario();
        jornada.getStyleClass().removeAll("success", "warning");
        if (h == null) {
            jornada.setText("Jornada: complete el horario (HH:MM)");
            jornada.getStyleClass().add("warning");
            return;
        }
        Jornada j = contexto.asistencia().jornada(h);
        boolean valido = contexto.asistencia().validarHorario(h).isEmpty();
        jornada.setText("Jornada: " + horas(j.horasDiarias()) + " h diarias · " + horas(j.horasSemanales())
                + " h semanales");
        jornada.getStyleClass().add(valido ? "success" : "warning");
    }

    private void guardarHorario() {
        RonaldmichaHorario h = leerHorario();
        if (h == null) {
            mostrarErroresHorario(List.of("Complete el ingreso y la salida con el formato HH:MM, y el refrigerio y la "
                    + "tolerancia en minutos."));
            return;
        }
        try {
            contexto.asistencia().guardarHorario(contexto.sesion(), actual.trabajador().id(), h);
            RonaldmichaToast.exito(tabla, "Horario guardado.");
            cargar();
        } catch (RonaldmichaValidacionException e) {
            mostrarErroresHorario(e.errores());
        } catch (RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(tabla, e.getMessage());
        }
    }

    private void mostrarErroresHorario(List<String> errores) {
        erroresHorario.setText(errores.stream().map(m -> "• " + m).collect(Collectors.joining("\n")));
        mostrar(erroresHorario, true);
    }

    private void mostrarResumen(MesAsistencia m) {
        chipOrigen.setText(m.origen().etiqueta());
        chipOrigen.getStyleClass().removeAll("success", "info", "warning");
        switch (m.origen()) {
            case SISTEMA -> chipOrigen.getStyleClass().add("success");
            case EXCEL -> chipOrigen.getStyleClass().add("info");
            case EXCEL_AJUSTES -> chipOrigen.getStyleClass().add("warning");
            case SIN_REGISTRO -> { }
        }
        mostrar(chipSoloLectura, m.soloLectura());

        Resumen r = m.resumen();
        Importes imp = m.importes().orElse(null);
        boolean sinRegistro = m.origen() == RonaldmichaOrigenAsistencia.SIN_REGISTRO;
        kpis.getChildren().setAll(
                kpi("Días laborables", String.valueOf(r.diasLaborables()),
                        r.porRegistrar() > 0 ? r.porRegistrar() + " por registrar" : null),
                kpi("Asistencias", sinRegistro ? "—" : String.valueOf(r.asistencias()), null),
                kpi("Faltas", sinRegistro ? "—" : String.valueOf(r.faltas()), sinRegistro
                        ? "Sin marcaciones: la planilla usa las novedades"
                        : r.licenciasSinGoce() > 0 ? "incluye " + r.licenciasSinGoce() + " lic. sin goce" : null),
                kpi("Tardanza", r.tardanzaMin() + " min", null),
                kpi("HE al 25%", RonaldmichaHoras.duracion(r.he25Min()) + " h", imp == null ? null : RonaldmichaFormatos.moneda(imp.he25())),
                kpi("HE al 35%", RonaldmichaHoras.duracion(r.he35Min()) + " h", imp == null ? null : RonaldmichaFormatos.moneda(imp.he35())),
                kpi("Horas a compensar", RonaldmichaHoras.duracion(r.compensarMin()) + " h", null),
                kpi("Descansos o feriados trabajados", String.valueOf(r.descansosTrabajados()),
                        imp == null || r.descansosConSobretasa() == 0 ? null
                                : RonaldmichaFormatos.moneda(imp.descansos()) + " (día + 100%)"),
                kpi("Horas trabajadas", RonaldmichaHoras.duracion(r.minutosTrabajados()) + " h", null));

        chipsFeriados.getChildren().clear();
        for (RonaldmichaFeriado f : m.feriados()) {
            chipsFeriados.getChildren().add(chipFeriado(f));
        }
        if (m.feriados().isEmpty()) {
            Label ninguno = new Label("Sin feriados este mes");
            ninguno.getStyleClass().add("text-muted");
            chipsFeriados.getChildren().add(ninguno);
        }
        agregarFeriado.getChildren().clear();
        if (editable) {
            DatePicker fecha = selectorFecha(m.mes());
            Button agregar = new Button("+ Día no laborable");
            agregar.getStyleClass().addAll("btn", "btn-sm");
            agregar.setOnAction(e -> agregarDiaNoLaborable(fecha.getValue()));
            agregarFeriado.getChildren().setAll(fecha, agregar);
        }

        alertas.getChildren().clear();
        for (String texto : m.alertas()) {
            alertas.getChildren().add(alerta(texto, texto.startsWith("Personal de dirección") ? "info" : "warning"));
        }
        construirAcciones(m);
    }

    private Node chipFeriado(RonaldmichaFeriado f) {
        Label texto = new Label(DIA_MES.format(f.fecha()) + " · " + f.nombre());
        HBox chip = new HBox(4, texto);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().addAll("chip", "holiday", "chip-feriado");
        if (f.agregado() && editable) {
            Button quitar = new Button("✕");
            quitar.getStyleClass().addAll("btn-ghost", "chip-quitar");
            quitar.setAccessibleText("Quitar " + RonaldmichaFormatos.fecha(f.fecha()));
            quitar.setOnAction(e -> quitarDiaNoLaborable(f.fecha()));
            chip.getChildren().add(quitar);
        }
        return chip;
    }

    private void construirAcciones(MesAsistencia m) {
        acciones.getChildren().clear();
        if (esTrabajador()) {
            acciones.getChildren().add(botonExcel("Descargar mi asistencia (Excel)", "mi_asistencia_",
                    (u, mes) -> contexto.asistencia().exportarMiAsistencia(u, mes)));
            return;
        }
        if (esAdmin()) {
            Button llenar = boton("Llenar días con el horario", "btn-primary", this::llenarConHorario);
            Button importar = boton("Importar Excel", "btn-excel", this::importar);
            RonaldmichaConfirmButton borrar = new RonaldmichaConfirmButton("Borrar marcaciones del mes", this::borrarMes);
            borrar.setDisable(m.soloLectura());
            llenar.setDisable(m.soloLectura());
            importar.setDisable(m.soloLectura());
            acciones.getChildren().addAll(llenar, importar);
            acciones.getChildren().addAll(plantillaExcel(), exportarExcel(), borrar);
        } else {
            acciones.getChildren().addAll(plantillaExcel(), exportarExcel());
        }
        acciones.getChildren().add(boton("Ver en la planilla", null, this::verEnPlanilla));
    }

    private void configurarTabla() {
        tabla.setItems(dias);
        tabla.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.RELOJ, "Sin datos",
                "Elija un trabajador en el selector y el mes en el encabezado."));
        tabla.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Dia d, boolean vacio) {
                super.updateItem(d, vacio);
                getStyleClass().remove("dia-no-laborable");
                if (!vacio && d != null && d.tipo() != TipoDia.LABORABLE) {
                    getStyleClass().add("dia-no-laborable");
                }
            }
        });

        colFecha.setCellValueFactory(c -> new ReadOnlyStringWrapper(DIA_MES.format(c.getValue().fecha())));
        colFecha.getStyleClass().add("mono");
        colDia.setCellValueFactory(c -> new ReadOnlyStringWrapper(diaCorto(c.getValue().fecha().getDayOfWeek())));

        colTipo.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colTipo.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Dia d, boolean vacio) {
                super.updateItem(d, vacio);
                setText(null);
                setGraphic(null);
                if (vacio || d == null) {
                    return;
                }
                if (d.tipo() == TipoDia.FERIADO) {
                    setGraphic(chip("Feriado · " + d.feriado(), "holiday"));
                } else {
                    setText(d.tipo().etiqueta());
                }
            }
        });

        colIngreso.setCellValueFactory(c -> new ReadOnlyStringWrapper(RonaldmichaHoras.texto(c.getValue().marcacion().ingreso())));
        colSalida.setCellValueFactory(c -> new ReadOnlyStringWrapper(RonaldmichaHoras.texto(c.getValue().marcacion().salida())));
        for (TableColumn<Dia, String> col : List.of(colIngreso, colSalida)) {
            col.getStyleClass().add("mono");
            col.setCellFactory(c -> new TextFieldTableCell<Dia, String>(new StringConverter<String>() {
                @Override
                public String toString(String s) {
                    return s;
                }

                @Override
                public String fromString(String s) {
                    return s;
                }
            }) {
                @Override
                public void startEdit() {
                    Dia d = getTableRow() == null ? null : getTableRow().getItem();
                    if (d != null && d.estado() != EstadoDia.SIN_VINCULO) {
                        super.startEdit();
                    }
                }
            });
        }
        colIngreso.setOnEditCommit(e -> guardarHora(e.getRowValue(), e.getNewValue(), true));
        colSalida.setOnEditCommit(e -> guardarHora(e.getRowValue(), e.getNewValue(), false));

        colHoras.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().marcacion().completa() ? RonaldmichaHoras.duracion(c.getValue().minutosTrabajados()) : ""));
        colTardanza.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().tardanzaMin() > 0 ? c.getValue().tardanzaMin() + " min" : ""));
        colSobretiempo.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().sobretiempoMin() > 0 ? RonaldmichaHoras.duracion(c.getValue().sobretiempoMin()) : ""));
        for (TableColumn<Dia, String> col : List.of(colHoras, colTardanza, colSobretiempo)) {
            col.getStyleClass().add("num");
        }

        colEstado.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colEstado.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Dia d, boolean vacio) {
                super.updateItem(d, vacio);
                setGraphic(vacio || d == null ? null : chip(estado(d), estiloEstado(d.estado())));
            }
        });

        colNovedad.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().marcacion().novedad()));
        colNovedad.setCellFactory(c -> new ComboBoxTableCell<Dia, RonaldmichaNovedad>(new StringConverter<RonaldmichaNovedad>() {
            @Override
            public String toString(RonaldmichaNovedad n) {
                return n == null ? "" : n.descripcion();
            }

            @Override
            public RonaldmichaNovedad fromString(String s) {
                return RonaldmichaNovedad.desdeTexto(s);
            }
        }, RonaldmichaNovedad.values()) {
            @Override
            public void startEdit() {
                Dia d = getTableRow() == null ? null : getTableRow().getItem();
                if (d != null && d.estado() != EstadoDia.SIN_VINCULO) {
                    super.startEdit();
                }
            }
        });
        colNovedad.setOnEditCommit(e -> guardar(base(e.getRowValue()).conNovedad(e.getNewValue())));

        colHorasExtra.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colHorasExtra.setCellFactory(c -> new CeldaHorasExtra());
        colHorasExtra.setEditable(false);
    }

    private final class CeldaHorasExtra extends TableCell<Dia, Dia> {
        private final CheckBox autorizada = new CheckBox("Autorizada");
        private final ComboBox<RonaldmichaTratamientoHe> tratamiento = new ComboBox<>(
                FXCollections.observableArrayList(RonaldmichaTratamientoHe.values()));
        private final HBox caja = new HBox(8, autorizada, tratamiento);
        private boolean cargando;

        CeldaHorasExtra() {
            caja.setAlignment(Pos.CENTER_LEFT);
            tratamiento.getStyleClass().add("input-sm");
            autorizada.setOnAction(e -> cambiar());
            tratamiento.setOnAction(e -> cambiar());
        }

        private void cambiar() {
            Dia d = getItem();
            if (cargando || d == null) {
                return;
            }
            guardar(base(d).conHorasExtra(autorizada.isSelected(), tratamiento.getValue()));
        }

        @Override
        protected void updateItem(Dia d, boolean vacio) {
            super.updateItem(d, vacio);
            if (vacio || d == null || d.sobretiempoMin() == 0) {
                setGraphic(null);
                return;
            }
            cargando = true;
            autorizada.setSelected(d.marcacion().heAutorizada());
            tratamiento.setValue(d.marcacion().heTratamiento());
            cargando = false;
            autorizada.setDisable(!editable);
            tratamiento.setDisable(!editable);
            setGraphic(caja);
        }
    }

    private void guardarHora(Dia d, String texto, boolean esIngreso) {
        LocalTime hora;
        try {
            hora = RonaldmichaHoras.leer(texto);
        } catch (IllegalArgumentException e) {
            RonaldmichaToast.error(tabla, "Escriba la hora como HH:MM (por ejemplo 08:00): " + e.getMessage() + ".");
            tabla.refresh();
            return;
        }
        RonaldmichaMarcacion m = base(d);
        guardar(esIngreso ? m.conHoras(hora, m.salida()) : m.conHoras(m.ingreso(), hora));
    }

    private void guardar(RonaldmichaMarcacion m) {
        try {
            contexto.asistencia().guardarDia(contexto.sesion(), m);
            cargar();
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(tabla, e.getMessage());
            tabla.refresh();
        }
    }

    private RonaldmichaMarcacion base(Dia d) {
        RonaldmichaMarcacion m = d.marcacion();
        return new RonaldmichaMarcacion(actual.trabajador().id(), d.fecha(), m.ingreso(), m.salida(), m.novedad(),
                m.heAutorizada(), m.heTratamiento(), m.origen());
    }

    private void llenarConHorario() {
        ejecutar(() -> {
            int n = contexto.asistencia().llenarConHorario(contexto.sesion(), actual.trabajador().id(), actual.mes());
            cargar();
            RonaldmichaToast.exito(tabla, n == 0 ? "No hay días laborables por llenar hasta hoy."
                    : n + (n == 1 ? " día completado" : " días completados") + " con el horario.");
        });
    }

    private void borrarMes() {
        ejecutar(() -> {
            int n = contexto.asistencia().borrarMes(contexto.sesion(), actual.trabajador().id(), actual.mes());
            cargar();
            RonaldmichaToast.exito(tabla, n + (n == 1 ? " día borrado" : " días borrados") + " de " + mesTexto(actual.mes()) + ".");
        });
    }

    private void agregarDiaNoLaborable(LocalDate fecha) {
        ejecutar(() -> {
            contexto.asistencia().agregarDiaNoLaborable(contexto.sesion(), fecha, null);
            cargar();
            RonaldmichaToast.exito(tabla, "Día no laborable agregado: " + RonaldmichaFormatos.fecha(fecha) + ".");
        });
    }

    private void quitarDiaNoLaborable(LocalDate fecha) {
        ejecutar(() -> {
            contexto.asistencia().quitarDiaNoLaborable(contexto.sesion(), fecha);
            cargar();
            RonaldmichaToast.exito(tabla, "Se quitó el día no laborable del " + RonaldmichaFormatos.fecha(fecha) + ".");
        });
    }

    private void verEnPlanilla() {
        if (actual != null) {
            contexto.trabajadorSeleccionadoProperty().set(actual.trabajador().id());
        }
        RonaldmichaNavegacion.ir(RonaldmichaVista.PLANILLA_MENSUAL);
    }

    private void importar() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Importar asistencia");
        selector.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel o CSV (*.xlsx, *.csv)", "*.xlsx", "*.csv", "*.xls"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        File archivo = selector.showOpenDialog(tabla.getScene().getWindow());
        if (archivo == null) {
            return;
        }
        YearMonth mes = contexto.periodoProperty().get();
        acciones.setDisable(true);
        RonaldmichaToast.exito(tabla, "Importando " + archivo.getName() + "…");
        RonaldmichaTareas.ejecutar(() -> contexto.asistencia().importar(
                RonaldmichaLectorTabla.leer(archivo.toPath(), RonaldmichaAsistenciaExcel.HOJA_ASISTENCIA), archivo.getName(),
                contexto.sesion(), mes), r -> {
            acciones.setDisable(false);
            cargar();
            mostrarResultadoImportacion(archivo.getName(), r);
        }, e -> {
            acciones.setDisable(false);
            RonaldmichaToast.error(tabla, RonaldmichaTareas.mensaje(e));
        });
    }

    private void mostrarResultadoImportacion(String archivo, RonaldmichaAsistenciaService.ResultadoImportacion r) {
        VBox contenido = new VBox(10);
        Label total = new Label(r.dias() + (r.dias() == 1 ? " día importado" : " días importados") + " · "
                + r.trabajadores() + (r.trabajadores() == 1 ? " trabajador" : " trabajadores")
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
            RonaldmichaToast.error(tabla, r.dias() + " días importados · " + r.observados().size() + " observados.");
        }
    }

    private RonaldmichaExportButton plantillaExcel() {
        return botonExcel("Plantilla Excel", "plantilla_asistencia_", (u, mes) -> contexto.asistencia().plantillaExcel(u, mes));
    }

    private RonaldmichaExportButton exportarExcel() {
        return botonExcel("Exportar Excel", "asistencia_", (u, mes) -> contexto.asistencia().exportarExcel(u, mes));
    }

    private RonaldmichaExportButton botonExcel(String texto, String prefijo, BiFunction<RonaldmichaUsuario, YearMonth, byte[]> datos) {
        YearMonth[] mes = new YearMonth[1];
        RonaldmichaExportButton b = new RonaldmichaExportButton(texto, RonaldmichaExportButton.Formato.EXCEL,
                () -> new RonaldmichaExportButton.Archivo(prefijo + codigo(mes[0]) + ".xlsx", datos.apply(contexto.sesion(), mes[0])));
        b.conPrevio(continuar -> {
            mes[0] = contexto.periodoProperty().get();
            continuar.run();
        });
        return b;
    }

    private void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(tabla, e.getMessage());
        }
    }

    private boolean esAdmin() {
        return contexto.sesion() != null && contexto.sesion().rol() == RonaldmichaRol.ADMIN;
    }

    private boolean esTrabajador() {
        return contexto.sesion() != null && contexto.sesion().rol() == RonaldmichaRol.TRABAJADOR;
    }

    private static String estado(Dia d) {
        if (d.trabajoEnDescanso() && !d.conSobretasa()) {
            return d.estado().etiqueta() + " (sustitutorio)";
        }
        return d.estado().etiqueta();
    }

    private static String estiloEstado(EstadoDia e) {
        return switch (e) {
            case ASISTIO -> "success";
            case TARDANZA, INCOMPLETA -> "warning";
            case FALTA, LICENCIA_SIN_GOCE -> "error";
            case FERIADO_PAGADO, TRABAJO_FERIADO, TRABAJO_DESCANSO -> "holiday";
            case VACACIONES, LICENCIA_CON_GOCE, DESCANSO_MEDICO, DESCANSO_SUSTITUTORIO -> "info";
            case POR_REGISTRAR, DESCANSO, SIN_VINCULO -> "neutral";
        };
    }

    private static RonaldmichaKpiCard kpi(String titulo, String valor, String detalle) {
        RonaldmichaKpiCard k = new RonaldmichaKpiCard(titulo, valor, detalle);
        k.getStyleClass().add("kpi-sm");
        return k;
    }

    private static Label alerta(String texto, String estilo) {
        Label l = new Label(texto);
        l.getStyleClass().addAll("alert", estilo);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
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
        b.setOnAction(e -> accion.run());
        return b;
    }

    private static VBox campo(String etiqueta, Node control) {
        Label l = new Label(etiqueta);
        l.getStyleClass().add("field-label");
        VBox caja = new VBox(4, l, control);
        HBox.setHgrow(caja, Priority.ALWAYS);
        return caja;
    }

    private static DatePicker selectorFecha(YearMonth mes) {
        DatePicker d = new DatePicker();
        d.setPromptText("dd/mm/aaaa");
        d.setPrefWidth(150);
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
        d.setOnShowing(e -> {
            if (d.getValue() == null) {
                d.setValue(mes.atDay(1));
            }
        });
        return d;
    }

    private static TextField hora() {
        TextField t = new TextField();
        t.setPromptText("HH:MM");
        t.getStyleClass().add("mono");
        return t;
    }

    private static TextField numero() {
        TextField t = new TextField();
        t.getStyleClass().add("num");
        return t;
    }

    private static int entero(TextField campo) {
        String v = campo.getText() == null ? "" : campo.getText().trim();
        return v.isEmpty() ? 0 : Integer.parseInt(v);
    }

    private static String horas(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }

    private static String diaCorto(DayOfWeek d) {
        String n = d.getDisplayName(TextStyle.SHORT_STANDALONE, RonaldmichaFormatos.LOCALE_PE).replace(".", "");
        return n.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + n.substring(1);
    }

    private static String mesTexto(YearMonth mes) {
        return mes.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, RonaldmichaFormatos.LOCALE_PE) + " " + mes.getYear();
    }

    private static String codigo(YearMonth mes) {
        return String.format("%04d%02d", mes.getYear(), mes.getMonthValue());
    }

    private static void mostrar(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }
}
