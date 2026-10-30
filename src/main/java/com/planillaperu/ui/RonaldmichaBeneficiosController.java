package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaSemestreBeneficio;
import com.planillaperu.domain.RonaldmichaTipoBeneficio;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaBeneficiosService;
import com.planillaperu.service.RonaldmichaBeneficiosService.Ajuste;
import com.planillaperu.service.RonaldmichaBeneficiosService.Beneficio;
import com.planillaperu.service.RonaldmichaBeneficiosService.FilaBeneficio;
import com.planillaperu.service.RonaldmichaBeneficiosService.Totales;
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
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class RonaldmichaBeneficiosController {

    record Fila(FilaBeneficio fila, Totales totales) {

        boolean esTotal() {
            return fila == null;
        }
    }

    private enum Campo { BASICO, FALTAS, GRATIFICACION }

    private record Destinatario(Long id, String texto) {
    }

    private final RonaldmichaAppContext contexto;
    private final RonaldmichaTipoBeneficio tipo;
    private final ChangeListener<YearMonth> alCambiarPeriodo = (o, a, b) -> irA(semestreDe(b));
    private final ObservableList<Fila> filas = FXCollections.observableArrayList();
    private final TableView<Fila> tabla = new TableView<>(filas);
    private final ToggleGroup pestanas = new ToggleGroup();
    private final ToggleButton pestana1 = new ToggleButton();
    private final ToggleButton pestana2 = new ToggleButton();
    private final Label titulo = new Label();
    private final Label periodo = new Label();
    private final HBox acciones = new HBox(8);
    private final VBox banner = new VBox(6);
    private final Label resumen = new Label();
    private final Map<Campo, Map<Long, String>> ediciones = new HashMap<>();
    private RonaldmichaSemestreBeneficio semestre;
    private Beneficio datos;
    private boolean editando;
    private boolean cambiandoPestana;

    @FXML private VBox raiz;

    protected RonaldmichaBeneficiosController(RonaldmichaAppContext contexto, RonaldmichaTipoBeneficio tipo) {
        this.contexto = contexto;
        this.tipo = tipo;
        for (Campo c : Campo.values()) {
            ediciones.put(c, new HashMap<>());
        }
    }

    @FXML
    private void initialize() {
        RonaldmichaAtajos.registrarExportar(this::exportar);
        boolean grati = tipo == RonaldmichaTipoBeneficio.GRATIFICACION;
        pestana1.setText(grati ? "Julio" : "Mayo");
        pestana2.setText(grati ? "Diciembre" : "Noviembre");
        pestana1.setMinWidth(Region.USE_PREF_SIZE);
        pestana2.setMinWidth(Region.USE_PREF_SIZE);
        pestana1.setToggleGroup(pestanas);
        pestana2.setToggleGroup(pestanas);
        pestanas.selectedToggleProperty().addListener((o, anterior, actual) -> {
            if (actual == null) {
                pestanas.selectToggle(anterior);
            } else if (!cambiandoPestana) {
                irA(new RonaldmichaSemestreBeneficio(tipo, semestre.anio(), actual == pestana1 ? 1 : 2));
            }
        });
        HBox segmentado = new HBox(4, pestana1, pestana2);
        segmentado.getStyleClass().add("segmented");

        Button anterior = iconoBoton(RonaldmichaIcono.ANTERIOR, "Semestre anterior", () -> irA(semestre.anterior()));
        Button siguiente = iconoBoton(RonaldmichaIcono.SIGUIENTE, "Semestre siguiente", () -> irA(semestre.siguiente()));
        titulo.getStyleClass().add("title-md");
        periodo.getStyleClass().add("text-muted");
        VBox textos = new VBox(2, titulo, periodo);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        HBox fila = new HBox(12, anterior, segmentado, siguiente, textos, espacio, acciones);
        fila.setAlignment(Pos.CENTER_LEFT);
        resumen.getStyleClass().add("text-muted");
        VBox cabecera = new VBox(10, fila, resumen);
        cabecera.getStyleClass().add("card");

        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tabla.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Fila f, boolean vacio) {
                super.updateItem(f, vacio);
                getStyleClass().remove("fila-total");
                if (!vacio && f != null && f.esTotal()) {
                    getStyleClass().add("fila-total");
                }
            }
        });
        tabla.setPlaceholder(new RonaldmichaEmptyState(RonaldmichaIcono.VACIO, "Sin trabajadores",
                "No hay trabajadores con vínculo laboral en este semestre.")
                .conAccion("Ir a Trabajadores", () -> RonaldmichaNavegacion.ir(RonaldmichaVista.TRABAJADORES)));
        VBox tarjetaTabla = new VBox(tabla);
        tarjetaTabla.getStyleClass().addAll("card", "table-card");
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Label nota = new Label(grati ? RonaldmichaBeneficiosService.NOTA_GRATIFICACION : RonaldmichaBeneficiosService.NOTA_CTS);
        nota.setWrapText(true);
        nota.getStyleClass().addAll("text-muted", "nota-legal");

        raiz.getChildren().setAll(cabecera, banner, tarjetaTabla, nota);
        contexto.periodoProperty().addListener(new WeakChangeListener<>(alCambiarPeriodo));
        irA(semestreDe(contexto.periodoProperty().get()));
    }

    private RonaldmichaSemestreBeneficio semestreDe(YearMonth mes) {
        return RonaldmichaSemestreBeneficio.vigenteEn(tipo, mes);
    }

    private void irA(RonaldmichaSemestreBeneficio s) {
        semestre = s;
        editando = false;
        ediciones.values().forEach(Map::clear);
        cargar();
    }

    private void cargar() {
        cambiandoPestana = true;
        pestanas.selectToggle(semestre.semestre() == 1 ? pestana1 : pestana2);
        cambiandoPestana = false;
        titulo.setText(RonaldmichaBeneficiosService.etiqueta(semestre));
        periodo.setText("Periodo " + RonaldmichaBeneficiosService.periodoTexto(semestre) + " · "
                + (tipo == RonaldmichaTipoBeneficio.CTS ? "depósito" : "pago") + " hasta el " + RonaldmichaFormatos.fecha(semestre.fechaPago()));
        try {
            datos = contexto.beneficios().consultar(contexto.sesion(), semestre);
        } catch (RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
            return;
        }
        construirColumnas();
        List<Fila> nuevas = new ArrayList<>(datos.filas().stream().map(f -> new Fila(f, null)).toList());
        if (!datos.filas().isEmpty()) {
            nuevas.add(new Fila(null, Totales.de(semestre, datos.filas())));
        }
        filas.setAll(nuevas);
        mostrarBanner();
        mostrarAcciones();
        Totales t = Totales.de(semestre, datos.filas());
        resumen.setText(datos.filas().size() + " trabajadores · total " + RonaldmichaFormatos.moneda(t.total())
                + (editando ? " · modo edición" : ""));
    }

    private void mostrarBanner() {
        banner.getChildren().clear();
        datos.error().ifPresent(e -> banner.getChildren().add(alerta(e, "error")));
        long ajustados = datos.ajustados();
        if (ajustados == 0) {
            return;
        }
        Label texto = new Label(ajustados + (ajustados == 1 ? " trabajador con ajuste manual (●)"
                : " trabajadores con ajuste manual (●)"));
        texto.setWrapText(true);
        HBox.setHgrow(texto, Priority.ALWAYS);
        texto.setMaxWidth(Double.MAX_VALUE);
        HBox caja = new HBox(12, texto);
        caja.setAlignment(Pos.CENTER_LEFT);
        caja.getStyleClass().addAll("alert", "warning", "banner-ajustes");
        if (esAdmin()) {
            RonaldmichaConfirmButton restablecer = new RonaldmichaConfirmButton("Restablecer cálculo automático", this::restablecer);
            restablecer.getStyleClass().add("btn-sm");
            restablecer.setMinWidth(Region.USE_PREF_SIZE);
            caja.getChildren().add(restablecer);
        }
        banner.getChildren().add(caja);
    }

    private void mostrarAcciones() {
        acciones.getChildren().clear();
        if (editando) {
            Button cancelar = boton("Cancelar", null, () -> {
                editando = false;
                ediciones.values().forEach(Map::clear);
                cargar();
            });
            Button guardar = boton("Guardar", "btn-primary", this::guardar);
            acciones.getChildren().addAll(cancelar, guardar);
            return;
        }
        boolean hayDatos = datos.error().isEmpty() && !datos.filas().isEmpty();
        if (esAdmin()) {
            Button editar = boton("Editar", null, () -> {
                editando = true;
                cargar();
            });
            editar.setDisable(!hayDatos);
            acciones.getChildren().add(editar);
        }
        Button enviar = boton("Enviar", null, this::enviar);
        Button exportar = boton("Exportar", null, this::exportar);
        enviar.setDisable(!hayDatos);
        exportar.setDisable(!hayDatos);
        acciones.getChildren().addAll(enviar, exportar);
    }

    private void construirColumnas() {
        boolean grati = tipo == RonaldmichaTipoBeneficio.GRATIFICACION;
        List<TableColumn<Fila, ?>> columnas = new ArrayList<>();

        TableColumn<Fila, Fila> trabajador = new TableColumn<>("Trabajador");
        trabajador.setPrefWidth(270);
        trabajador.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        trabajador.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Fila f, boolean vacio) {
                super.updateItem(f, vacio);
                setText(null);
                if (vacio || f == null) {
                    setGraphic(null);
                    return;
                }
                if (f.esTotal()) {
                    Label l = new Label("TOTAL · " + f.totales().trabajadores() + " trabajadores");
                    l.getStyleClass().add("celda-nombre");
                    setGraphic(l);
                    return;
                }
                RonaldmichaTrabajador t = f.fila().trabajador();
                Label n = new Label(t.nombreCompleto());
                n.getStyleClass().add("celda-nombre");
                Label d = new Label(t.regimen().descripcion() + " · ingreso " + RonaldmichaFormatos.fecha(t.fechaIngreso()));
                d.getStyleClass().add("celda-detalle");
                setGraphic(new VBox(2, n, d));
            }
        });
        columnas.add(trabajador);
        columnas.add(entero("Meses", 64, f -> f.esTotal() ? null : f.fila().meses()));
        if (!grati) {
            columnas.add(entero("Días", 56, f -> f.esTotal() ? null : f.fila().dias()));
        }
        columnas.add(editable("Faltas", 80, Campo.FALTAS));
        columnas.add(grati ? editable("Básico", 120, Campo.BASICO)
                : monto("Básico", 110, FilaBeneficio::basico, Totales::basico));
        columnas.add(monto("Asig. familiar", 100, FilaBeneficio::asignacionFamiliar, Totales::asignacionFamiliar));
        for (YearMonth mes : semestre.meses()) {
            TableColumn<Fila, BigDecimal> col = monto(RonaldmichaBeneficiosService.mesCorto(mes), 92,
                    f -> f.variables().get(mes), t -> t.variables().get(mes));
            col.getStyleClass().add("col-mes");
            columnas.add(col);
        }
        columnas.add(monto("Promedio", 100, FilaBeneficio::promedioVariable, Totales::promedioVariable));
        if (grati) {
            columnas.add(monto("Gratificación", 120, FilaBeneficio::gratificacion, Totales::gratificacion));
            columnas.add(monto("Bonificación", 110, FilaBeneficio::bonificacion, Totales::bonificacion));
            TableColumn<Fila, BigDecimal> total = monto("Total", 120, FilaBeneficio::total, Totales::total);
            total.getStyleClass().add("col-total");
            columnas.add(total);
        } else {
            columnas.add(editable("Gratificación", 120, Campo.GRATIFICACION));
            columnas.add(monto("1/6", 96, FilaBeneficio::sextoGratificacion, Totales::sextoGratificacion));
            columnas.add(monto("Computable", 116, FilaBeneficio::computable, Totales::computable));
            TableColumn<Fila, BigDecimal> cts = monto("CTS", 116, FilaBeneficio::importe, Totales::importe);
            cts.getStyleClass().add("col-total");
            columnas.add(cts);
            TableColumn<Fila, String> banco = new TableColumn<>("Banco CTS");
            banco.setPrefWidth(170);
            banco.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().esTotal() ? ""
                    : textoBanco(c.getValue().fila().trabajador())));
            columnas.add(banco);
        }
        columnas.forEach(c -> c.setSortable(false));
        tabla.getColumns().setAll(columnas);
    }

    private static String textoBanco(RonaldmichaTrabajador t) {
        if (t.bancoCts() == null || t.bancoCts().isBlank()) {
            return "Sin cuenta CTS";
        }
        return t.bancoCts() + (t.ctaCts() == null ? "" : " · " + t.ctaCts());
    }

    private TableColumn<Fila, Fila> editable(String titulo, double ancho, Campo campo) {
        TableColumn<Fila, Fila> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Fila f, boolean vacio) {
                super.updateItem(f, vacio);
                getStyleClass().removeAll("ajustado", "num");
                setGraphic(null);
                setText(null);
                if (vacio || f == null) {
                    return;
                }
                getStyleClass().add("num");
                if (f.esTotal()) {
                    setText(switch (campo) {
                        case FALTAS -> String.valueOf(f.totales().faltas());
                        case BASICO -> RonaldmichaFormatos.moneda(f.totales().basico());
                        case GRATIFICACION -> RonaldmichaFormatos.moneda(f.totales().gratificacion());
                    });
                    return;
                }
                FilaBeneficio fb = f.fila();
                long id = fb.trabajador().id();
                if (editando) {
                    TextField campoTexto = new TextField(ediciones.get(campo).getOrDefault(id, valorEditable(fb, campo)));
                    campoTexto.getStyleClass().addAll("num", "input-sm", "celda-edicion");
                    campoTexto.setAccessibleText(titulo + " de " + fb.trabajador().nombreCompleto());
                    campoTexto.textProperty().addListener((o, a, b) -> ediciones.get(campo).put(id, b));
                    setGraphic(campoTexto);
                    return;
                }
                boolean ajustado = switch (campo) {
                    case FALTAS -> fb.faltasAjustadas();
                    case BASICO -> fb.basicoAjustado();
                    case GRATIFICACION -> fb.gratificacionAjustada();
                };
                String valor = campo == Campo.FALTAS ? String.valueOf(fb.faltas())
                        : RonaldmichaFormatos.moneda(campo == Campo.BASICO ? fb.basico() : fb.gratificacion());
                setText(ajustado ? "● " + valor : valor);
                if (ajustado) {
                    getStyleClass().add("ajustado");
                    setTooltip(new Tooltip("Ajuste manual · cálculo automático: " + (campo == Campo.FALTAS
                            ? fb.faltasAuto() : RonaldmichaFormatos.moneda(campo == Campo.BASICO ? fb.basicoAuto()
                            : fb.gratificacionAuto()))));
                } else {
                    setTooltip(null);
                }
            }
        });
        return col;
    }

    private static String valorEditable(FilaBeneficio f, Campo campo) {
        return switch (campo) {
            case FALTAS -> String.valueOf(f.faltas());
            case BASICO -> f.basico().toPlainString();
            case GRATIFICACION -> f.gratificacion().toPlainString();
        };
    }

    private static TableColumn<Fila, Integer> entero(String titulo, double ancho, Function<Fila, Integer> valor) {
        TableColumn<Fila, Integer> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.getStyleClass().add("num");
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(valor.apply(c.getValue())));
        return col;
    }

    private static TableColumn<Fila, BigDecimal> monto(String titulo, double ancho,
                                                       Function<FilaBeneficio, BigDecimal> deFila,
                                                       Function<Totales, BigDecimal> deTotal) {
        TableColumn<Fila, BigDecimal> col = new TableColumn<>(titulo);
        col.setPrefWidth(ancho);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().esTotal()
                ? deTotal.apply(c.getValue().totales()) : deFila.apply(c.getValue().fila())));
        col.setCellFactory(RonaldmichaMoneyCell.fabrica());
        return col;
    }

    private void guardar() {
        Map<Long, Ajuste> cambios = new LinkedHashMap<>();
        List<String> errores = new ArrayList<>();
        for (FilaBeneficio f : datos.filas()) {
            long id = f.trabajador().id();
            Integer faltas = null;
            BigDecimal basico = null;
            BigDecimal grati = null;
            String nombre = f.trabajador().nombreCompleto();
            String tFaltas = ediciones.get(Campo.FALTAS).get(id);
            if (tFaltas != null && !tFaltas.trim().equals(String.valueOf(f.faltas()))) {
                try {
                    faltas = Integer.parseInt(tFaltas.trim());
                } catch (NumberFormatException e) {
                    errores.add(nombre + ": las faltas deben ser un número entero.");
                }
            }
            basico = leerMonto(ediciones.get(Campo.BASICO).get(id), f.basico(), nombre + ": el básico", errores);
            grati = leerMonto(ediciones.get(Campo.GRATIFICACION).get(id), f.gratificacion(),
                    nombre + ": la gratificación", errores);
            if (faltas != null || basico != null || grati != null) {
                cambios.put(id, new Ajuste(basico, faltas, grati));
            }
        }
        if (!errores.isEmpty()) {
            RonaldmichaToast.error(raiz, String.join(" ", errores));
            return;
        }
        try {
            contexto.beneficios().guardarAjustes(contexto.sesion(), semestre, cambios);
            editando = false;
            ediciones.values().forEach(Map::clear);
            cargar();
            RonaldmichaToast.exito(raiz, cambios.isEmpty() ? "No hubo cambios."
                    : cambios.size() + (cambios.size() == 1 ? " ajuste guardado." : " ajustes guardados."));
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
        }
    }

    private static BigDecimal leerMonto(String texto, BigDecimal actual, String nombre, List<String> errores) {
        if (texto == null) {
            return null;
        }
        try {
            BigDecimal v = new BigDecimal(texto.trim().replace(",", ""));
            return v.compareTo(actual) == 0 ? null : v;
        } catch (NumberFormatException e) {
            errores.add(nombre + " debe ser un monto (por ejemplo 1500.00).");
            return null;
        }
    }

    private void restablecer() {
        try {
            int n = contexto.beneficios().restablecer(contexto.sesion(), semestre);
            irA(semestre);
            RonaldmichaToast.exito(raiz, n + (n == 1 ? " ajuste quitado" : " ajustes quitados") + ": cálculo automático.");
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(raiz, e.getMessage());
        }
    }

    private void enviar() {
        long conCorreo = datos.filas().stream().map(FilaBeneficio::trabajador)
                .filter(t -> t.correo() != null && !t.correo().isBlank()).count();
        Label texto = new Label("El envío por correo aún no está conectado a un servidor de correo, así que no se "
                + "envió nada.\n\n" + conCorreo + " de " + datos.filas().size()
                + " trabajadores tienen correo registrado. Mientras tanto, use Exportar para generar los documentos.");
        texto.setWrapText(true);
        RonaldmichaModal modal = new RonaldmichaModal("Enviar · " + RonaldmichaBeneficiosService.etiqueta(semestre), texto).ancho(560);
        modal.botonCancelar().setText("Cerrar");
        modal.mostrar(raiz);
    }

    private void exportar() {
        ComboBox<Destinatario> selector = new ComboBox<>();
        selector.getItems().add(new Destinatario(null, "Todos los trabajadores"));
        datos.filas().forEach(f -> selector.getItems().add(new Destinatario(f.trabajador().id(),
                f.trabajador().nombreCompleto() + " · " + f.trabajador().numDoc())));
        selector.getSelectionModel().selectFirst();
        selector.setMaxWidth(Double.MAX_VALUE);
        selector.setConverter(new StringConverter<>() {
            @Override
            public String toString(Destinatario d) {
                return d == null ? "" : d.texto();
            }

            @Override
            public Destinatario fromString(String s) {
                return null;
            }
        });
        RonaldmichaBeneficiosService b = contexto.beneficios();
        String base = (tipo == RonaldmichaTipoBeneficio.GRATIFICACION ? "gratificacion_" : "cts_") + semestre.anio()
                + String.format("%02d", semestre.mesPago().getValue());
        Supplier<Long> id = () -> selector.getValue() == null ? null : selector.getValue().id();
        boolean grati = tipo == RonaldmichaTipoBeneficio.GRATIFICACION;

        VBox contenido = new VBox(14,
                new VBox(4, etiquetaCampo("Trabajadores"), selector),
                reporte("Planilla", "Detalle del semestre con la variable de cada mes y los totales.",
                        descarga("Excel", base + ".xlsx", id, x -> b.planillaExcel(contexto.sesion(), semestre, x))),
                reporte(grati ? "Boleta de gratificación" : "Liquidación del depósito de CTS",
                        "Dos copias por hoja (empleador y trabajador).",
                        descarga("PDF", base + (grati ? "_boletas.pdf" : "_liquidaciones.pdf"), id,
                                x -> b.boletasPdf(contexto.sesion(), semestre, x)),
                        descarga("Word", base + (grati ? "_boletas.docx" : "_liquidaciones.docx"), id,
                                x -> b.boletasWord(contexto.sesion(), semestre, x))),
                reporte("Pagos masivos por banco", "Una hoja por banco; el TXT tiene un formato genérico "
                                + "(documento, nombre, banco, cuenta e importe).",
                        descarga("Excel", base + "_pagos.xlsx", id, x -> b.pagosExcel(contexto.sesion(), semestre, x)),
                        descarga("TXT", base + "_pagos.txt", id, x -> b.pagosTxt(contexto.sesion(), semestre, x))));
        RonaldmichaModal modal = new RonaldmichaModal("Exportar · " + RonaldmichaBeneficiosService.etiqueta(semestre), contenido).ancho(620);
        modal.botonCancelar().setText("Cerrar");
        modal.mostrar(raiz);
    }

    private static VBox reporte(String titulo, String descripcion, Button... botones) {
        Label t = new Label(titulo);
        t.getStyleClass().add("title-md");
        Label d = new Label(descripcion);
        d.getStyleClass().add("text-muted");
        d.setWrapText(true);
        FlowPane fila = new FlowPane(8, 8, botones);
        VBox caja = new VBox(6, t, d, fila);
        caja.getStyleClass().add("form-section");
        return caja;
    }

    private static RonaldmichaExportButton descarga(String texto, String nombre, Supplier<Long> id, Function<Long, byte[]> generar) {
        Long[] elegido = new Long[1];
        RonaldmichaExportButton b = new RonaldmichaExportButton(texto, RonaldmichaExportButton.Formato.de(nombre),
                () -> new RonaldmichaExportButton.Archivo(nombre, generar.apply(elegido[0])));
        b.conPrevio(continuar -> {
            elegido[0] = id.get();
            continuar.run();
        });
        return b;
    }

    private boolean esAdmin() {
        return contexto.sesion() != null && contexto.sesion().rol() == RonaldmichaRol.ADMIN;
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

    private static Button iconoBoton(RonaldmichaIcono icono, String ayuda, Runnable accion) {
        Button b = new Button();
        b.setGraphic(icono.nodo(16));
        b.getStyleClass().addAll("btn", "btn-ghost", "btn-icon");
        b.setTooltip(new Tooltip(ayuda));
        b.setAccessibleText(ayuda);
        b.setOnAction(e -> accion.run());
        return b;
    }

    private static Label etiquetaCampo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("field-label");
        return l;
    }

    private static Label alerta(String texto, String estilo) {
        Label l = new Label(texto);
        l.getStyleClass().addAll("alert", estilo);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }
}
