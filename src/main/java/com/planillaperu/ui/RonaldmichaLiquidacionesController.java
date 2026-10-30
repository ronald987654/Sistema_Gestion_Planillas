package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaEstadoLiquidacion;
import com.planillaperu.domain.RonaldmichaLiquidacion;
import com.planillaperu.domain.RonaldmichaMotivoCese;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.export.RonaldmichaConstanciaBeneficio.Dato;
import com.planillaperu.export.RonaldmichaReporteLiquidacion;
import com.planillaperu.export.RonaldmichaReporteLiquidacion.Bloque;
import com.planillaperu.export.RonaldmichaReporteLiquidacion.Linea;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaBeneficiosService;
import com.planillaperu.service.RonaldmichaLiquidacionService;
import com.planillaperu.service.RonaldmichaLiquidacionService.Borrador;
import com.planillaperu.service.RonaldmichaLiquidacionService.Calculo;
import com.planillaperu.service.RonaldmichaLiquidacionService.Fila;
import com.planillaperu.service.RonaldmichaLiquidacionService.Totales;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaConfirmButton;
import com.planillaperu.ui.components.RonaldmichaEmptyState;
import com.planillaperu.ui.components.RonaldmichaExportButton;
import com.planillaperu.ui.components.RonaldmichaIcono;
import com.planillaperu.ui.components.RonaldmichaModal;
import com.planillaperu.ui.components.RonaldmichaMoneyCell;
import com.planillaperu.ui.components.RonaldmichaToast;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class RonaldmichaLiquidacionesController {

    record FilaTabla(Fila fila, Totales totales) {

        boolean esTotal() {
            return fila == null;
        }
    }

    private final RonaldmichaAppContext contexto;
    private final ObservableList<FilaTabla> filas = FXCollections.observableArrayList();
    private final TableView<FilaTabla> tabla = new TableView<>(filas);

    @FXML private VBox raiz;

    public RonaldmichaLiquidacionesController(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        Label titulo = new Label("Liquidaciones de beneficios sociales");
        titulo.getStyleClass().add("title-md");
        Label subtitulo = new Label("Vacaciones, gratificación y CTS truncas, indemnización por despido arbitrario "
                + "y descuentos de pensión");
        subtitulo.getStyleClass().add("text-muted");
        VBox textos = new VBox(2, titulo, subtitulo);
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox cabecera = new HBox(12, textos);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        if (esAdmin()) {
            cabecera.getChildren().add(boton("+ Nueva liquidación", "btn-primary", () -> abrir(null, null)));
        }

        configurarTabla();
        VBox tarjeta = new VBox(tabla);
        tarjeta.getStyleClass().addAll("card", "table-card");
        Label nota = new Label(RonaldmichaLiquidacionService.NOTA);
        nota.setWrapText(true);
        nota.getStyleClass().addAll("text-muted", "nota-legal");
        raiz.getChildren().setAll(cabecera, tarjeta, nota);
        cargar();

        Long aLiquidar = contexto.trabajadorALiquidarProperty().get();
        if (aLiquidar != null) {
            contexto.trabajadorALiquidarProperty().set(null);
            raiz.sceneProperty().addListener((o, antes, escena) -> {
                if (escena != null && antes == null) {
                    liquidar(aLiquidar);
                }
            });
        }
    }

    private void liquidar(long trabajadorId) {
        RonaldmichaLiquidacion existente = contexto.liquidaciones().delTrabajador(trabajadorId).orElse(null);
        abrir(existente, existente == null ? trabajadorId : null);
    }

    private void configurarTabla() {
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        RonaldmichaEmptyState sinLiquidaciones = new RonaldmichaEmptyState(RonaldmichaIcono.LIQUIDACION, "Sin liquidaciones",
                "Use «+ Nueva liquidación» o el botón «Liquidar» en Trabajadores.");
        tabla.setPlaceholder(esAdmin() ? sinLiquidaciones.conAccion("+ Nueva liquidación", () -> abrir(null, null))
                : sinLiquidaciones);
        tabla.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(FilaTabla f, boolean vacio) {
                super.updateItem(f, vacio);
                getStyleClass().remove("fila-total");
                if (!vacio && f != null && f.esTotal()) {
                    getStyleClass().add("fila-total");
                }
            }
        });
        TableColumn<FilaTabla, String> documento = texto("Documento", 100, f -> f.esTotal() ? ""
                : f.fila().trabajador().tipoDoc().codigo() + " " + f.fila().trabajador().numDoc());
        documento.getStyleClass().add("mono");
        List<TableColumn<FilaTabla, ?>> columnas = List.of(
                documento,
                texto("Nombres", 220, f -> f.esTotal() ? "TOTAL · " + f.totales().cantidad() + " liquidaciones"
                        : f.fila().trabajador().nombreCompleto()),
                texto("Ingreso", 90, f -> f.esTotal() ? "" : RonaldmichaFormatos.fecha(f.fila().trabajador().fechaIngreso())),
                texto("Cese", 90, f -> f.esTotal() ? "" : RonaldmichaFormatos.fecha(f.fila().liquidacion().fechaCese())),
                texto("Motivo", 150, f -> f.esTotal() ? "" : f.fila().liquidacion().motivo().etiqueta()),
                monto("Liquidación", 120, RonaldmichaLiquidacion::bruto, Totales::bruto, null),
                monto("Descuento", 110, RonaldmichaLiquidacion::descuento, Totales::descuento, "monto-descuento"),
                monto("Neto", 120, RonaldmichaLiquidacion::neto, Totales::neto, "monto-neto"),
                celdas("Estado", 130, f -> f.esTotal() ? null : chip(f.fila().liquidacion().estado())),
                celdas("Acciones", 360, f -> f.esTotal() ? null : acciones(f.fila())));
        columnas.forEach(c -> c.setSortable(false));
        tabla.getColumns().setAll(columnas);
    }

    private void cargar() {
        List<Fila> lista;
        try {
            lista = contexto.liquidaciones().listar(contexto.sesion());
        } catch (RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
            return;
        }
        List<FilaTabla> nuevas = new ArrayList<>(lista.stream().map(f -> new FilaTabla(f, null)).toList());
        if (!lista.isEmpty()) {
            nuevas.add(new FilaTabla(null, Totales.de(lista)));
        }
        filas.setAll(nuevas);
    }

    private Node acciones(Fila f) {
        RonaldmichaLiquidacion l = f.liquidacion();
        HBox caja = new HBox(6);
        caja.setAlignment(Pos.CENTER_LEFT);
        boolean editable = esAdmin() && l.estado() != RonaldmichaEstadoLiquidacion.PAGADO;
        caja.getChildren().add(boton(editable ? "Editar" : "Ver", "btn-sm", () -> abrir(l, null)));
        Borrador b = RonaldmichaLiquidacionService.borrador(l);
        String base = "liquidacion_" + f.trabajador().codigo();
        caja.getChildren().add(archivo("PDF", base + ".pdf", () -> contexto.liquidaciones().reportePdf(contexto.sesion(), b)));
        caja.getChildren().add(archivo("Word", base + ".docx",
                () -> contexto.liquidaciones().reporteWord(contexto.sesion(), b)));
        if (esAdmin() && l.estado() == RonaldmichaEstadoLiquidacion.CESE_REGISTRADO) {
            RonaldmichaConfirmButton pagar = new RonaldmichaConfirmButton("Marcar pagado", () -> ejecutar(
                    () -> contexto.liquidaciones().marcarPagado(contexto.sesion(), l.id()), "Liquidación marcada como pagada."));
            pagar.getStyleClass().add("btn-sm");
            caja.getChildren().add(pagar);
        }
        if (esAdmin() && l.estado() == RonaldmichaEstadoLiquidacion.BORRADOR) {
            RonaldmichaConfirmButton eliminar = new RonaldmichaConfirmButton("Eliminar", () -> ejecutar(
                    () -> contexto.liquidaciones().eliminar(contexto.sesion(), l.id()), "Borrador eliminado."));
            eliminar.getStyleClass().add("btn-sm");
            caja.getChildren().add(eliminar);
        }
        return caja;
    }

    private void ejecutar(Runnable accion, String exito) {
        try {
            accion.run();
            cargar();
            RonaldmichaToast.exito(raiz, exito);
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
        }
    }

    private void abrir(RonaldmichaLiquidacion existente, Long trabajadorId) {
        RonaldmichaLiquidacionService servicio = contexto.liquidaciones();
        servicio.refrescar();
        boolean editable = esAdmin() && (existente == null || existente.estado() != RonaldmichaEstadoLiquidacion.PAGADO);
        Borrador inicial = existente != null ? RonaldmichaLiquidacionService.borrador(existente) : null;

        ComboBox<RonaldmichaTrabajador> trabajador = new ComboBox<>();
        trabajador.setMaxWidth(Double.MAX_VALUE);
        trabajador.setConverter(new StringConverter<>() {
            @Override
            public String toString(RonaldmichaTrabajador t) {
                return t == null ? "" : t.nombreCompleto() + " · " + t.numDoc();
            }

            @Override
            public RonaldmichaTrabajador fromString(String s) {
                return null;
            }
        });
        if (existente != null) {
            contexto.trabajadores().buscar(existente.trabajadorId()).ifPresent(t -> trabajador.getItems().setAll(t));
            trabajador.getSelectionModel().selectFirst();
            trabajador.setDisable(true);
        } else {
            trabajador.getItems().setAll(servicio.liquidables());
            trabajador.getItems().stream().filter(t -> t.id().equals(trabajadorId)).findFirst()
                    .ifPresent(t -> trabajador.getSelectionModel().select(t));
        }
        trabajador.setPromptText("Elija al trabajador");

        DatePicker cese = selectorFecha();
        cese.setValue(inicial != null ? inicial.fechaCese() : LocalDate.now());
        ComboBox<RonaldmichaMotivoCese> motivo = new ComboBox<>(FXCollections.observableArrayList(RonaldmichaMotivoCese.values()));
        motivo.setMaxWidth(Double.MAX_VALUE);
        motivo.setValue(inicial != null ? inicial.motivo() : RonaldmichaMotivoCese.RENUNCIA);
        TextField ultimaGrati = campoNumero(inicial == null || inicial.ultimaGratificacion() == null ? ""
                : inicial.ultimaGratificacion().toPlainString());
        ultimaGrati.setPromptText("En blanco = calculada");
        Label gratiCalculada = new Label();
        gratiCalculada.getStyleClass().add("celda-detalle");
        gratiCalculada.setWrapText(true);
        TextField faltasCts = campoNumero(inicial == null ? "0" : String.valueOf(inicial.faltasCts()));
        TextField faltasGrati = campoNumero(inicial == null ? "0" : String.valueOf(inicial.faltasGratificacion()));
        TextField otrosIngresos = campoNumero(inicial == null ? "0.00" : inicial.otrosIngresos().toPlainString());
        TextField otrosDescuentos = campoNumero(inicial == null ? "0.00" : inicial.otrosDescuentos().toPlainString());

        GridPane formulario = new GridPane();
        formulario.setHgap(12);
        formulario.setVgap(10);
        formulario.add(campo("Trabajador", trabajador), 0, 0, 2, 1);
        formulario.add(campo("Fecha de cese", cese), 2, 0);
        formulario.add(campo("Motivo", motivo), 3, 0);
        formulario.add(campo("Última gratificación", new VBox(4, ultimaGrati, gratiCalculada)), 0, 1);
        formulario.add(campo("Días de falta CTS", faltasCts), 1, 1);
        formulario.add(campo("Días de falta gratificación", faltasGrati), 2, 1);
        formulario.add(new HBox(12, campo("Otros ingresos", otrosIngresos), campo("Otros descuentos", otrosDescuentos)),
                3, 1);
        for (Node n : List.of(trabajador, cese, motivo, ultimaGrati, faltasCts, faltasGrati, otrosIngresos, otrosDescuentos)) {
            n.setDisable(n.isDisabled() || !editable);
        }

        VBox resultado = new VBox(12);
        resultado.getStyleClass().add("resultado-liquidacion");
        VBox contenido = new VBox(16, formulario, resultado);
        ScrollPane desplazable = new ScrollPane(contenido);
        desplazable.setFitToWidth(true);
        desplazable.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        desplazable.setPrefViewportHeight(620);
        desplazable.getStyleClass().add("modal-scroll");

        RonaldmichaModal modal = new RonaldmichaModal("Detalle de liquidación", desplazable).ancho(1100);
        List<String> erroresFormulario = new ArrayList<>();
        Supplier<Borrador> leer = () -> {
            erroresFormulario.clear();
            RonaldmichaTrabajador t = trabajador.getValue();
            return new Borrador(t == null ? null : t.id(), cese.getValue(), motivo.getValue(),
                    monto(ultimaGrati, "La última gratificación", true, erroresFormulario),
                    entero(faltasCts, "Los días de falta CTS", erroresFormulario),
                    entero(faltasGrati, "Los días de falta de gratificación", erroresFormulario),
                    monto(otrosIngresos, "Otros ingresos", false, erroresFormulario),
                    monto(otrosDescuentos, "Otros descuentos", false, erroresFormulario));
        };
        Runnable recalcular = () -> {
            Borrador b = leer.get();
            if (!erroresFormulario.isEmpty()) {
                resultado.getChildren().setAll(alerta(String.join("\n", erroresFormulario), "error"));
                return;
            }
            try {
                Calculo c = servicio.calcular(contexto.sesion(), b);
                gratiCalculada.setText("Calculada: " + RonaldmichaFormatos.moneda(c.ultimaGratificacionCalculada()) + " ("
                        + RonaldmichaBeneficiosService.etiqueta(c.semestreUltimaGratificacion()) + ")");
                mostrarResultado(resultado, c);
            } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
                gratiCalculada.setText("");
                resultado.getChildren().setAll(alerta(e.getMessage(), "error"));
            }
        };
        trabajador.valueProperty().addListener((o, a, b) -> recalcular.run());
        cese.valueProperty().addListener((o, a, b) -> recalcular.run());
        motivo.valueProperty().addListener((o, a, b) -> recalcular.run());
        for (TextField campo : List.of(ultimaGrati, faltasCts, faltasGrati, otrosIngresos, otrosDescuentos)) {
            campo.textProperty().addListener((o, a, b) -> recalcular.run());
        }
        recalcular.run();

        String base = "liquidacion";
        Button pdf = boton("Reporte PDF", "btn-pdf", () -> conBorrador(leer, erroresFormulario, b -> guardarArchivo(
                base + ".pdf", "PDF (*.pdf)", "*.pdf", () -> servicio.reportePdf(contexto.sesion(), b))));
        Button word = boton("Reporte Word", "btn-word", () -> conBorrador(leer, erroresFormulario, b -> guardarArchivo(
                base + ".docx", "Word (*.docx)", "*.docx", () -> servicio.reporteWord(contexto.sesion(), b))));
        Button certificado = boton("Certificado de trabajo", "btn-pdf", () -> conBorrador(leer, erroresFormulario,
                b -> guardarArchivo("certificado_trabajo.pdf", "PDF (*.pdf)", "*.pdf",
                        () -> servicio.certificadoTrabajo(contexto.sesion(), b))));
        modal.agregarAccion(pdf).agregarAccion(word).agregarAccion(certificado);
        if (editable) {
            Long id = existente == null ? null : existente.id();
            Button guardar = boton("Guardar", null, () -> guardar(modal, leer, erroresFormulario, id, false));
            Button registrar = boton(existente != null && existente.estado() == RonaldmichaEstadoLiquidacion.CESE_REGISTRADO
                    ? "Guardar (cese registrado)" : "Guardar y registrar cese", "btn-primary",
                    () -> guardar(modal, leer, erroresFormulario, id, true));
            registrar.setTooltip(new Tooltip("Pone la fecha de cese al trabajador. Luego dé de baja al trabajador en "
                    + "el T-Registro."));
            modal.agregarAccion(guardar).agregarAccion(registrar);
        } else {
            modal.botonCancelar().setText("Cerrar");
        }
        modal.mostrar(raiz);
    }

    private void guardar(RonaldmichaModal modal, Supplier<Borrador> leer, List<String> errores, Long id, boolean registrarCese) {
        Borrador b = leer.get();
        if (!errores.isEmpty()) {
            RonaldmichaToast.error(raiz, String.join(" ", errores));
            return;
        }
        try {
            RonaldmichaLiquidacion l = contexto.liquidaciones().guardar(contexto.sesion(), id, b, registrarCese);
            modal.cerrar();
            cargar();
            RonaldmichaToast.exito(raiz, l.estado() == RonaldmichaEstadoLiquidacion.CESE_REGISTRADO
                    ? "Liquidación guardada y cese registrado. Recuerde dar de baja al trabajador en el T-Registro."
                    : "Liquidación guardada como borrador.");
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
        }
    }

    private void conBorrador(Supplier<Borrador> leer, List<String> errores, Consumer<Borrador> accion) {
        Borrador b = leer.get();
        if (!errores.isEmpty()) {
            RonaldmichaToast.error(raiz, String.join(" ", errores));
            return;
        }
        accion.accept(b);
    }

    private static void mostrarResultado(VBox resultado, Calculo c) {
        RonaldmichaReporteLiquidacion r = c.reporte();
        FlowPane datos = new FlowPane(18, 8);
        datos.getStyleClass().add("datos-liquidacion");
        for (Dato d : r.datos()) {
            Label etiqueta = new Label(d.etiqueta());
            etiqueta.getStyleClass().add("celda-detalle");
            Label valor = new Label(d.valor());
            valor.getStyleClass().add("celda-nombre");
            datos.getChildren().add(new VBox(2, etiqueta, valor));
        }

        GridPane bloques = new GridPane();
        bloques.setHgap(12);
        bloques.setVgap(12);
        for (int i = 0; i < r.bloques().size(); i++) {
            VBox caja = bloque(r.bloques().get(i));
            GridPane.setHgrow(caja, Priority.ALWAYS);
            caja.setMaxWidth(Double.MAX_VALUE);
            bloques.add(caja, i % 2, i / 2);
        }
        ColumnConstraints mitad = new ColumnConstraints();
        mitad.setPercentWidth(50);
        bloques.getColumnConstraints().setAll(mitad, mitad);

        Label tituloNeto = new Label("NETO A PAGAR");
        tituloNeto.getStyleClass().add("neto-titulo");
        Label neto = new Label(RonaldmichaFormatos.moneda(r.neto()));
        neto.getStyleClass().add("neto-valor");
        Label totales = new Label("Bruto " + RonaldmichaFormatos.moneda(r.bruto()) + " · descuentos " + RonaldmichaFormatos.moneda(r.descuentos()));
        totales.getStyleClass().add("celda-detalle");
        Label letras = new Label("Son: " + r.netoEnLetras());
        letras.getStyleClass().add("neto-letras");
        letras.setWrapText(true);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox filaNeto = new HBox(12, new VBox(2, tituloNeto, totales), espacio, neto);
        filaNeto.setAlignment(Pos.CENTER_LEFT);
        VBox cajaNeto = new VBox(6, filaNeto, letras);
        cajaNeto.getStyleClass().add("neto-caja");
        resultado.getChildren().setAll(datos, bloques, cajaNeto);
    }

    private static VBox bloque(Bloque b) {
        Label titulo = new Label(b.titulo());
        titulo.getStyleClass().add("title-md");
        VBox caja = new VBox(6, titulo);
        caja.getStyleClass().addAll("columna-conceptos", "bloque-liquidacion");
        for (Linea l : b.lineas()) {
            caja.getChildren().add(linea(l.concepto(), l.detalle(), l.monto(), "concepto-linea"));
        }
        caja.getChildren().add(linea("Subtotal", null, b.subtotal(), "concepto-total"));
        return caja;
    }

    private static HBox linea(String concepto, String detalle, BigDecimal monto, String estilo) {
        Label c = new Label(concepto);
        c.setWrapText(true);
        VBox textos = new VBox(1, c);
        if (detalle != null) {
            Label d = new Label(detalle);
            d.getStyleClass().add("celda-detalle");
            d.setWrapText(true);
            textos.getChildren().add(d);
        }
        HBox.setHgrow(textos, Priority.ALWAYS);
        textos.setMaxWidth(Double.MAX_VALUE);
        Label valor = new Label(RonaldmichaFormatos.numero(monto));
        valor.getStyleClass().add("num");
        if (monto.signum() < 0) {
            valor.getStyleClass().add("monto-negativo");
        }
        valor.setMinWidth(Region.USE_PREF_SIZE);
        HBox fila = new HBox(8, textos, valor);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add(estilo);
        return fila;
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

    private static int entero(TextField campo, String nombre, List<String> errores) {
        String t = campo.getText() == null ? "" : campo.getText().trim();
        if (t.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            errores.add(nombre + " deben ser un número entero.");
            return 0;
        }
    }

    private static BigDecimal monto(TextField campo, String nombre, boolean vacioNulo, List<String> errores) {
        String t = campo.getText() == null ? "" : campo.getText().trim().replace(",", "");
        if (t.isEmpty()) {
            return vacioNulo ? null : BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            errores.add(nombre + " debe ser un monto (por ejemplo 1500.00).");
            return vacioNulo ? null : BigDecimal.ZERO;
        }
    }

    private static TextField campoNumero(String valor) {
        TextField t = new TextField(valor);
        t.getStyleClass().add("num");
        t.setPrefWidth(140);
        return t;
    }

    private static Label chip(RonaldmichaEstadoLiquidacion e) {
        Label l = new Label(e.etiqueta());
        l.getStyleClass().addAll("chip", e.estilo());
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    private static <T> TableColumn<T, String> texto(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(valor.apply(c.getValue())));
        return col;
    }

    private static TableColumn<FilaTabla, BigDecimal> monto(String titulo, double ancho,
                                                            Function<RonaldmichaLiquidacion, BigDecimal> deFila,
                                                            Function<Totales, BigDecimal> deTotal, String estilo) {
        TableColumn<FilaTabla, BigDecimal> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().esTotal()
                ? deTotal.apply(c.getValue().totales()) : deFila.apply(c.getValue().fila().liquidacion())));
        col.setCellFactory(c -> {
            RonaldmichaMoneyCell<FilaTabla> celda = new RonaldmichaMoneyCell<>();
            if (estilo != null) {
                celda.getStyleClass().add(estilo);
            }
            return celda;
        });
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
        VBox caja = new VBox(4, l, control);
        caja.setMaxWidth(Double.MAX_VALUE);
        return caja;
    }

    private static Label alerta(String texto, String estilo) {
        Label l = new Label(texto);
        l.getStyleClass().addAll("alert", estilo);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private static DatePicker selectorFecha() {
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
        return d;
    }
}
