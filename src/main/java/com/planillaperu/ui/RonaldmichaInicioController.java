package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.calc.RonaldmichaObligacionesCalculator.Obligacion;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaRegistroAuditoria;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaInicioService.Alerta;
import com.planillaperu.service.RonaldmichaInicioService.Destino;
import com.planillaperu.service.RonaldmichaInicioService.GrupoPension;
import com.planillaperu.service.RonaldmichaInicioService.Kpi;
import com.planillaperu.service.RonaldmichaInicioService.Nivel;
import com.planillaperu.service.RonaldmichaInicioService.Paso;
import com.planillaperu.service.RonaldmichaInicioService.PuntoMes;
import com.planillaperu.service.RonaldmichaInicioService.Resumen;
import com.planillaperu.service.RonaldmichaReporte;
import com.planillaperu.service.RonaldmichaReporte.Formato;
import com.planillaperu.service.RonaldmichaReportesService.Archivo;
import com.planillaperu.service.RonaldmichaReportesService.Parametros;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaIcono;
import com.planillaperu.ui.components.RonaldmichaToast;
import javafx.animation.FadeTransition;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class RonaldmichaInicioController {

    private static final int ACTIVIDAD_MAXIMA = 5;
    private static final int VENCIMIENTOS_VISIBLES = 3;
    private static final double ALTO_ACCION = 56;
    private static final double ANCHO_MINIMO_ACCION = 168;
    private static final double ALTO_GRAFICO = 260;
    private static final double CIRCULO_PASO = 28;
    private static final double SEPARACION = 16;
    private static final double ANCHO_KPI_EN_UNA_FILA = 760;
    private static final double ANCHO_KPI_GRANDE = 1100;
    private static final double ANCHO_GRAFICO_Y_PENDIENTES = 880;
    private static final double ANCHO_INFERIOR_EN_TRES = 960;
    private static final Duration ENTRADA = Duration.millis(200);

    private static final DateTimeFormatter MES_ANIO = DateTimeFormatter.ofPattern("MMMM yyyy", RonaldmichaFormatos.LOCALE_PE);
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM", RonaldmichaFormatos.LOCALE_PE);
    private static final DateTimeFormatter MES_ABREV = DateTimeFormatter.ofPattern("MMM", RonaldmichaFormatos.LOCALE_PE);
    private static final List<String> SENSIBLES = List.of("PARAMETROS", "CAMBIO_SUELDO", "USUARIO", "CLAVE",
            "REABIERTA", "ANULA", "ELIMIN", "CESE", "CONFIGURACION", "RESPALDO", "RESTAUR", "RECHAZ");

    private record Accion(RonaldmichaIcono icono, String titulo, String formato, Consumer<StackPane> accion) {
    }

    private final RonaldmichaAppContext contexto;
    private final ChangeListener<YearMonth> alCambiarPeriodo = (o, a, periodo) -> {
        mostrar(periodo);
        entrada();
    };
    private RonaldmichaRefresco refresco;
    private final ChangeListener<RonaldmichaEvento> alCambiarDatos = (o, a, e) -> {
        if (e != null && e.tema() != RonaldmichaEvento.Tema.OTRO) {
            refresco.pedir();
        }
    };
    private final List<Runnable> adaptables = new ArrayList<>();

    @FXML private ScrollPane scroll;
    @FXML private VBox raiz;

    public RonaldmichaInicioController(RonaldmichaAppContext contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        RonaldmichaAtajos.registrarExportar(() -> descargar(RonaldmichaReporte.PLANILLA, Formato.EXCEL,
                new Parametros(contexto.periodoProperty().get(), null, false), null));
        raiz.setMinWidth(0);
        scroll.viewportBoundsProperty().addListener((o, a, b) -> {
            if (a == null || a.getWidth() != b.getWidth()) {
                adaptables.forEach(Runnable::run);
            }
        });
        contexto.periodoProperty().addListener(new WeakChangeListener<>(alCambiarPeriodo));
        refresco = new RonaldmichaRefresco(() -> mostrar(contexto.periodoProperty().get()));
        contexto.eventoProperty().addListener(new WeakChangeListener<>(alCambiarDatos));
        mostrar(contexto.periodoProperty().get());
        entrada();
    }

    private void mostrar(YearMonth mes) {
        Resumen r;
        try {
            r = contexto.inicio().resumen(contexto.sesion(), mes);
        } catch (RonaldmichaAccesoDenegadoException | RonaldmichaValidacionException e) {
            raiz.getChildren().setAll(new Label(e.getMessage()));
            return;
        }
        adaptables.clear();
        List<Node> secciones = List.of(encabezado(r), kpis(r), ciclo(r), acciones(mes), graficoYPendientes(r),
                inferior(r));
        for (Node n : secciones) {
            VBox.setVgrow(n, Priority.NEVER);
            if (n instanceof Region region) {
                region.setMaxHeight(Region.USE_PREF_SIZE);
            }
        }
        raiz.getChildren().setAll(secciones);
        adaptables.forEach(Runnable::run);
    }

    private HBox encabezado(Resumen r) {
        Label contextoMes = new Label("Planilla de " + MES_ANIO.format(r.mes()) + " · " + r.trabajadores()
                + (r.trabajadores() == 1 ? " trabajador" : " trabajadores") + " · pago el "
                + RonaldmichaFormatos.fecha(r.fechaPago()));
        contextoMes.getStyleClass().add("ini-contexto");
        Label saludo = new Label(saludo(LocalTime.now()) + ", " + nombreUsuario());
        saludo.getStyleClass().add("ini-saludo");
        saludo.setWrapText(true);
        VBox textos = new VBox(4, contextoMes, saludo);
        textos.setMinWidth(0);
        HBox.setHgrow(textos, Priority.ALWAYS);

        Button verPlanilla = new Button("Ver planilla");
        verPlanilla.getStyleClass().addAll("btn", "ini-btn");
        verPlanilla.setMinWidth(Region.USE_PREF_SIZE);
        verPlanilla.setOnAction(e -> RonaldmichaNavegacion.ir(RonaldmichaVista.PLANILLA_MENSUAL));

        Paso siguiente = r.ciclo().pasos().stream().filter(p -> !p.hecho()).findFirst().orElse(null);
        Button continuar = new Button(siguiente == null ? "Ciclo completo: ver reportes →"
                : "Continuar: " + siguiente.titulo() + " →");
        continuar.getStyleClass().addAll("btn", "btn-primary", "ini-btn");
        continuar.setMinWidth(Region.USE_PREF_SIZE);
        continuar.setOnAction(e -> ir(siguiente == null ? Destino.REPORTES : siguiente.destino()));

        HBox botones = new HBox(10, verPlanilla, continuar);
        botones.setAlignment(Pos.CENTER_RIGHT);
        botones.setMinWidth(Region.USE_PREF_SIZE);
        HBox caja = new HBox(16, textos, botones);
        caja.setAlignment(Pos.CENTER_LEFT);
        return caja;
    }

    static String saludo(LocalTime hora) {
        if (hora.getHour() >= 5 && hora.getHour() < 12) {
            return "Buenos días";
        }
        return hora.getHour() >= 12 && hora.getHour() < 19 ? "Buenas tardes" : "Buenas noches";
    }

    private String nombreUsuario() {
        RonaldmichaUsuario u = contexto.sesion();
        if (u == null) {
            return "";
        }
        if (u.trabajadorId() != null) {
            String nombres = contexto.trabajadores().buscar(u.trabajadorId()).map(RonaldmichaTrabajador::nombres).orElse(null);
            if (nombres != null && !nombres.isBlank()) {
                return nombres.trim().split("\\s+")[0];
            }
        }
        return mayuscula(u.usuario());
    }

    private GridPane kpis(Resumen r) {
        String[] detalles = {"Con vínculo laboral en el mes", "Total a depositar a los trabajadores",
                "EsSalud, SIS, Vida Ley y otros aportes", "Ingresos, aportes y provisiones"};
        YearMonth anterior = r.mes().minusMonths(1);
        List<Node> tarjetas = new ArrayList<>();
        for (int i = 0; i < r.kpis().size(); i++) {
            Kpi k = r.kpis().get(i);
            Label nombre = new Label(k.titulo());
            nombre.getStyleClass().add("ini-kpi-nombre");
            Label valor = new Label(k.moneda() ? RonaldmichaFormatos.moneda(k.actual()) : k.actual().toPlainString());
            valor.getStyleClass().add("ini-kpi-valor");
            valor.setMinWidth(0);
            Label variacion = variacion(k, anterior, detalles[i % detalles.length]);
            VBox tarjeta = new VBox(nombre, valor, variacion);
            tarjeta.getStyleClass().addAll("ini-tarjeta", "ini-kpi");
            if (i == 1) {
                tarjeta.getStyleClass().add("neto");
            }
            tarjetas.add(tarjeta);
        }
        GridPane grid = rejilla();
        adaptables.add(() -> {
            double ancho = ancho();
            colocar(grid, tarjetas, ancho >= ANCHO_KPI_EN_UNA_FILA ? 4 : 2);
            boolean compacto = ancho >= ANCHO_KPI_EN_UNA_FILA && ancho < ANCHO_KPI_GRANDE;
            tarjetas.forEach(t -> {
                t.getStyleClass().remove("compacto");
                if (compacto) {
                    t.getStyleClass().add("compacto");
                }
            });
            igualarAlto(grid, tarjetas);
        });
        return grid;
    }

    private static Label variacion(Kpi k, YearMonth anterior, String detalle) {
        Label l = new Label();
        l.getStyleClass().add("ini-variacion");
        String mes = MES.format(anterior);
        if (k.anterior() == null) {
            l.setText(detalle);
            return l;
        }
        BigDecimal diferencia = k.actual().subtract(k.anterior());
        int signo = diferencia.signum();
        if (signo == 0) {
            l.setText("= igual que " + mes);
            return l;
        }
        String cuanto = k.moneda()
                ? k.variacion().filter(v -> v.signum() != 0).map(v -> v.abs().toPlainString() + " %")
                        .orElse(RonaldmichaFormatos.moneda(diferencia.abs()))
                : diferencia.abs().toPlainString();
        l.setText((signo > 0 ? "▲ " : "▼ ") + cuanto + " vs. " + mes);
        l.getStyleClass().add(signo > 0 ? "sube" : "baja");
        return l;
    }

    private VBox ciclo(Resumen r) {
        List<Paso> pasos = r.ciclo().pasos();
        Label avance = new Label(r.ciclo().hechos() + " de " + pasos.size() + " pasos");
        avance.getStyleClass().addAll("chip", r.ciclo().hechos() == pasos.size() ? "success" : "neutral");
        avance.setMinWidth(Region.USE_PREF_SIZE);
        HBox cabecera = new HBox(12, titulo("Ciclo de la planilla · " + mayuscula(MES_ANIO.format(r.mes()))),
                espacio(), avance);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        int actual = pasos.size();
        for (int i = 0; i < pasos.size(); i++) {
            if (!pasos.get(i).hecho()) {
                actual = i;
                break;
            }
        }
        GridPane linea = new GridPane();
        linea.setMinWidth(0);
        for (int i = 0; i < pasos.size(); i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / pasos.size());
            c.setFillWidth(true);
            linea.getColumnConstraints().add(c);
            Region entra = i == 0 ? null : tramo(i <= actual);
            Region sale = i == pasos.size() - 1 ? null : tramo(i + 1 <= actual);
            linea.add(paso(pasos.get(i), i + 1, i == actual, entra, sale), i, 0);
        }
        VBox caja = new VBox(18, cabecera, linea);
        caja.getStyleClass().add("ini-tarjeta");
        return caja;
    }

    private static Region tramo(boolean hecho) {
        Region r = new Region();
        r.getStyleClass().add("ini-linea");
        if (hecho) {
            r.getStyleClass().add("hecha");
        }
        r.setMinHeight(2);
        r.setPrefHeight(2);
        r.setMaxHeight(2);
        r.setMaxWidth(Double.MAX_VALUE);
        return r;
    }

    private VBox paso(Paso p, int numero, boolean esActual, Region entra, Region sale) {
        StackPane circulo = new StackPane(p.hecho() ? RonaldmichaIcono.CHECK.nodo(14) : new Label(String.valueOf(numero)));
        circulo.getStyleClass().add("ini-circulo");
        circulo.setMinSize(CIRCULO_PASO, CIRCULO_PASO);
        circulo.setPrefSize(CIRCULO_PASO, CIRCULO_PASO);
        circulo.setMaxSize(CIRCULO_PASO, CIRCULO_PASO);

        HBox tramos = new HBox();
        tramos.setAlignment(Pos.CENTER);
        Region izquierda = entra == null ? new Region() : entra;
        Region derecha = sale == null ? new Region() : sale;
        HBox.setHgrow(izquierda, Priority.ALWAYS);
        HBox.setHgrow(derecha, Priority.ALWAYS);
        izquierda.setMaxWidth(Double.MAX_VALUE);
        derecha.setMaxWidth(Double.MAX_VALUE);
        Region hueco = new Region();
        hueco.setMinWidth(CIRCULO_PASO);
        hueco.setPrefWidth(CIRCULO_PASO);
        tramos.getChildren().addAll(izquierda, hueco, derecha);
        StackPane arriba = new StackPane(tramos, circulo);
        arriba.setMinHeight(CIRCULO_PASO);
        arriba.setPrefHeight(CIRCULO_PASO);
        arriba.setMaxHeight(CIRCULO_PASO);

        Label titulo = new Label(p.titulo());
        titulo.getStyleClass().add("ini-paso-titulo");
        titulo.setWrapText(true);
        titulo.setAlignment(Pos.CENTER);
        Label detalle = new Label(p.detalle());
        detalle.getStyleClass().add("ini-paso-detalle");
        detalle.setWrapText(true);
        detalle.setAlignment(Pos.CENTER);
        detalle.setMaxHeight(36);
        detalle.setTooltip(new Tooltip(p.detalle()));
        VBox textos = new VBox(2, titulo, detalle);
        textos.setAlignment(Pos.TOP_CENTER);
        textos.setPadding(new Insets(0, 4, 0, 4));

        VBox caja = new VBox(10, arriba, textos);
        caja.setAlignment(Pos.TOP_CENTER);
        caja.setMinWidth(0);
        caja.getStyleClass().addAll("ini-paso", p.hecho() ? "hecho" : esActual ? "actual" : "pendiente");
        clicable(caja, p.titulo() + ": " + (p.hecho() ? "hecho" : esActual ? "paso actual" : "pendiente") + ". "
                + p.detalle(), () -> ir(p.destino()));
        return caja;
    }

    private VBox acciones(YearMonth mes) {
        List<Accion> acciones = List.of(
                new Accion(RonaldmichaIcono.TRABAJADORES, "Nuevo trabajador", null, icono -> {
                    contexto.nuevoTrabajadorSolicitadoProperty().set(true);
                    RonaldmichaNavegacion.ir(RonaldmichaVista.TRABAJADORES);
                }),
                new Accion(RonaldmichaIcono.RELOJ, "Asistencia", null, icono -> RonaldmichaNavegacion.ir(RonaldmichaVista.ASISTENCIA)),
                new Accion(RonaldmichaIcono.CALENDARIO, "Planilla del mes", null, icono -> RonaldmichaNavegacion.ir(RonaldmichaVista.PLANILLA_MENSUAL)),
                new Accion(RonaldmichaIcono.DOCUMENTO, "Boletas del mes", "PDF", icono -> descargar(RonaldmichaReporte.BOLETA_PAGO,
                        Formato.PDF, new Parametros(mes, null, true), icono)),
                new Accion(RonaldmichaIcono.PLANILLA, "Planilla en Excel", "XLSX", icono -> descargar(RonaldmichaReporte.PLANILLA,
                        Formato.EXCEL, new Parametros(mes, null, false), icono)),
                new Accion(RonaldmichaIcono.REGALO, "Gratificación", null, icono -> RonaldmichaNavegacion.ir(RonaldmichaVista.GRATIFICACIONES)),
                new Accion(RonaldmichaIcono.BANCO, "CTS", null, icono -> RonaldmichaNavegacion.ir(RonaldmichaVista.CTS)),
                new Accion(RonaldmichaIcono.SOL, "Vacaciones", null, icono -> RonaldmichaNavegacion.ir(RonaldmichaVista.VACACIONES)),
                new Accion(RonaldmichaIcono.LIQUIDACION, "Liquidar", null, icono -> RonaldmichaNavegacion.ir(RonaldmichaVista.LIQUIDACIONES)),
                new Accion(RonaldmichaIcono.REPORTES, "Reportes", null, icono -> RonaldmichaNavegacion.ir(RonaldmichaVista.REPORTES)));
        List<Node> botones = new ArrayList<>();
        for (Accion a : acciones) {
            botones.add(accionRapida(a));
        }
        GridPane grid = rejilla();
        grid.setHgap(12);
        grid.setVgap(12);
        adaptables.add(() -> {
            int columnas = (int) Math.floor((ancho() + 12) / (ANCHO_MINIMO_ACCION + 12));
            colocar(grid, botones, columnas >= 5 ? 5 : Math.max(2, Math.min(columnas, 3)));
        });
        Label etiqueta = new Label("ACCIONES RÁPIDAS");
        etiqueta.getStyleClass().add("etiqueta-mayus");
        return new VBox(10, etiqueta, grid);
    }

    private HBox accionRapida(Accion a) {
        StackPane icono = new StackPane(a.icono().nodo(18));
        icono.getStyleClass().add("ini-accion-icono");
        icono.setMinSize(32, 32);
        icono.setPrefSize(32, 32);
        icono.setMaxSize(32, 32);
        Label titulo = new Label(a.titulo());
        titulo.getStyleClass().add("ini-accion-texto");
        titulo.setMinWidth(0);
        titulo.setWrapText(true);
        HBox boton = new HBox(icono, titulo);
        if (a.formato() != null) {
            Label formato = new Label(a.formato());
            formato.getStyleClass().addAll("ini-formato", a.formato().toLowerCase(Locale.ROOT));
            formato.setMinWidth(Region.USE_PREF_SIZE);
            boton.getChildren().addAll(espacio(), formato);
        }
        boton.setAlignment(Pos.CENTER_LEFT);
        boton.getStyleClass().add("ini-accion");
        boton.setMinHeight(ALTO_ACCION);
        boton.setPrefHeight(ALTO_ACCION);
        boton.setMaxHeight(ALTO_ACCION);
        String accesible = a.formato() == null ? a.titulo() : a.titulo() + ", descarga en " + a.formato();
        clicable(boton, accesible, () -> a.accion().accept(icono));
        return boton;
    }

    private void descargar(RonaldmichaReporte reporte, Formato formato, Parametros p, StackPane icono) {
        Node boton = icono == null ? null : icono.getParent();
        if (boton != null && boton.isDisabled()) {
            return;
        }
        List<Node> antes = icono == null ? List.of() : List.copyOf(icono.getChildren());
        if (icono != null) {
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.getStyleClass().add("spinner-accion");
            spinner.setPrefSize(18, 18);
            icono.getChildren().setAll(spinner);
            boton.setDisable(true);
        }
        RonaldmichaTareas.ejecutar(() -> {
            Archivo archivo = contexto.reportes().exportar(contexto.sesion(), reporte, formato, p);
            Path destino = libre(carpetaDescargas(), archivo.nombre());
            Files.write(destino, archivo.contenido());
            return destino;
        }, destino -> {
            restaurar(icono, boton, antes);
            RonaldmichaToast.archivo(raiz, destino);
        }, error -> {
            restaurar(icono, boton, antes);
            RonaldmichaToast.error(raiz, error instanceof RonaldmichaValidacionException || error instanceof RonaldmichaAccesoDenegadoException
                    ? error.getMessage() : "No se pudo generar el archivo: " + RonaldmichaTareas.mensaje(error));
        });
    }

    private static void restaurar(StackPane icono, Node boton, List<Node> antes) {
        if (icono != null) {
            icono.getChildren().setAll(antes);
            boton.setDisable(false);
        }
    }

    private static Path carpetaDescargas() {
        Path descargas = Path.of(System.getProperty("user.home"), "Downloads");
        if (!Files.isDirectory(descargas)) {
            descargas = Path.of(System.getProperty("user.home"));
        }
        return descargas;
    }

    private static Path libre(Path carpeta, String nombre) {
        Path destino = carpeta.resolve(nombre);
        int punto = nombre.lastIndexOf('.');
        String base = punto < 0 ? nombre : nombre.substring(0, punto);
        String extension = punto < 0 ? "" : nombre.substring(punto);
        for (int i = 2; Files.exists(destino); i++) {
            destino = carpeta.resolve(base + " (" + i + ")" + extension);
        }
        return destino;
    }

    private GridPane graficoYPendientes(Resumen r) {
        List<PuntoMes> serie = r.serie();
        String rango = mayuscula(MES.format(serie.getFirst().mes())) + "–" + MES.format(serie.getLast().mes()) + " "
                + serie.getLast().mes().getYear();
        HBox leyenda = new HBox(14, muestra("costo", "Costo laboral"), muestra("neto", "Neto pagado"));
        leyenda.setAlignment(Pos.CENTER_RIGHT);
        leyenda.setMinWidth(Region.USE_PREF_SIZE);
        HBox cabecera = new HBox(12, titulo("Costo laboral y neto pagado · " + rango), espacio(), leyenda);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        BarChart<String, Number> barras = barras(serie, r.mes());
        VBox.setVgrow(barras, Priority.ALWAYS);
        VBox grafico = new VBox(10, cabecera, barras);
        grafico.getStyleClass().add("ini-tarjeta");

        VBox pendientes = pendientes(r.alertas());
        List<Node> tarjetas = List.of(grafico, pendientes);
        GridPane grid = rejilla();
        adaptables.add(() -> {
            if (ancho() >= ANCHO_GRAFICO_Y_PENDIENTES) {
                colocar(grid, tarjetas, 63, 37);
                igualarAlto(grid, tarjetas);
            } else {
                colocar(grid, tarjetas, 1);
            }
        });
        return grid;
    }

    private static HBox muestra(String estilo, String texto) {
        Region cuadro = new Region();
        cuadro.getStyleClass().addAll("ini-muestra", estilo);
        cuadro.setMinSize(10, 10);
        cuadro.setPrefSize(10, 10);
        cuadro.setMaxSize(10, 10);
        Label l = new Label(texto);
        l.getStyleClass().add("ini-leyenda");
        HBox caja = new HBox(6, cuadro, l);
        caja.setAlignment(Pos.CENTER_LEFT);
        return caja;
    }

    private static BarChart<String, Number> barras(List<PuntoMes> serie, YearMonth actual) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setTickLabelFormatter(soles());
        y.setMinorTickVisible(false);
        double maximo = serie.stream().mapToDouble(p -> Math.max(p.costoLaboral().doubleValue(), p.neto().doubleValue()))
                .max().orElse(0);
        double paso = pasoRedondo(maximo / 5);
        y.setAutoRanging(false);
        y.setLowerBound(0);
        y.setTickUnit(paso);
        y.setUpperBound(Math.max(paso, Math.ceil(maximo / paso) * paso));
        BarChart<String, Number> grafico = new BarChart<>(x, y);
        grafico.getStyleClass().add("ini-grafico");
        grafico.setAnimated(false);
        grafico.setLegendVisible(false);
        grafico.setVerticalGridLinesVisible(false);
        grafico.setCategoryGap(22);
        grafico.setBarGap(3);
        grafico.setMinHeight(ALTO_GRAFICO);
        grafico.setPrefHeight(ALTO_GRAFICO);
        grafico.setMaxHeight(Double.MAX_VALUE);

        XYChart.Series<String, Number> costo = new XYChart.Series<>();
        costo.setName("Costo laboral");
        XYChart.Series<String, Number> neto = new XYChart.Series<>();
        neto.setName("Neto pagado");
        String etiquetaActual = mesCorto(actual);
        for (PuntoMes p : serie) {
            String texto = mayuscula(MES_ANIO.format(p.mes())) + "\n" + (p.conDatos()
                    ? "Costo laboral: " + RonaldmichaFormatos.moneda(p.costoLaboral()) + "\nNeto pagado: " + RonaldmichaFormatos.moneda(p.neto())
                    + "\nTrabajadores: " + p.trabajadores()
                    : "Sin planilla (faltan parámetros)");
            XYChart.Data<String, Number> c = new XYChart.Data<>(mesCorto(p.mes()), p.costoLaboral().doubleValue());
            XYChart.Data<String, Number> n = new XYChart.Data<>(mesCorto(p.mes()), p.neto().doubleValue());
            conTooltip(c, texto, p.mes().equals(actual));
            conTooltip(n, texto, false);
            costo.getData().add(c);
            neto.getData().add(n);
        }
        grafico.getData().add(costo);
        grafico.getData().add(neto);
        x.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) cambio -> resaltarMes(x, etiquetaActual));
        resaltarMes(x, etiquetaActual);
        return grafico;
    }

    static double pasoRedondo(double aproximado) {
        if (aproximado <= 0) {
            return 1000;
        }
        double potencia = Math.pow(10, Math.floor(Math.log10(aproximado)));
        for (double m : new double[] {1, 2, 2.5, 5, 10}) {
            if (m * potencia >= aproximado) {
                return m * potencia;
            }
        }
        return 10 * potencia;
    }

    private static void resaltarMes(CategoryAxis x, String etiqueta) {
        for (Node n : x.getChildrenUnmodifiable()) {
            if (n instanceof Text t && etiqueta.equals(t.getText()) && !t.getStyleClass().contains("mes-actual")) {
                t.getStyleClass().add("mes-actual");
            }
        }
    }

    private static void conTooltip(XYChart.Data<?, ?> d, String texto, boolean resaltar) {
        d.nodeProperty().addListener((o, a, nodo) -> {
            if (nodo != null) {
                Tooltip t = new Tooltip(texto);
                t.setShowDelay(Duration.millis(80));
                Tooltip.install(nodo, t);
                if (resaltar) {
                    nodo.getStyleClass().add("mes-actual");
                }
            }
        });
    }

    private VBox pendientes(List<Alerta> alertas) {
        Label cantidad = new Label(String.valueOf(alertas.size()));
        cantidad.getStyleClass().addAll("chip", alertas.isEmpty() ? "success" : "danger");
        cantidad.setMinWidth(Region.USE_PREF_SIZE);
        HBox cabecera = new HBox(10, titulo("Pendientes"), cantidad);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        VBox lista = new VBox(8);
        if (alertas.isEmpty()) {
            VBox ok = new VBox(2, negrita("Todo en orden"), detalle("No hay pendientes para este mes."));
            ok.getStyleClass().addAll("ini-pendiente", "ok");
            lista.getChildren().add(ok);
        }
        for (Alerta a : alertas) {
            boolean critico = a.nivel() == Nivel.CRITICO;
            Label det = detalle(a.detalle());
            det.setMaxHeight(36);
            det.setTooltip(new Tooltip(a.detalle()));
            VBox fila = new VBox(negrita(a.titulo()), det);
            fila.getStyleClass().addAll("ini-pendiente", critico ? "critico" : "atencion");
            clicable(fila, (critico ? "Crítico: " : "Atención: ") + a.titulo(), () -> ir(a.destino()));
            lista.getChildren().add(fila);
        }
        VBox caja = new VBox(14, cabecera, lista);
        caja.getStyleClass().add("ini-tarjeta");
        return caja;
    }

    private GridPane inferior(Resumen r) {
        VBox vencimientos = tarjeta("Próximos vencimientos", vencimientos(r.obligaciones()));
        VBox pensiones = tarjeta("Sistema de pensiones", pensiones(r.pensiones()));
        VBox actividad = tarjeta("Actividad reciente", actividad());
        List<Node> tarjetas = List.of(vencimientos, pensiones, actividad);
        GridPane grid = rejilla();
        adaptables.add(() -> {
            if (ancho() >= ANCHO_INFERIOR_EN_TRES) {
                colocar(grid, tarjetas, 3);
                igualarAlto(grid, tarjetas);
            } else {
                colocar(grid, tarjetas, 1);
            }
        });
        return grid;
    }

    private VBox vencimientos(List<Obligacion> obligaciones) {
        VBox caja = new VBox(14);
        LocalDate hoy = contexto.vacaciones().hoy();
        for (Obligacion o : obligaciones.subList(0, Math.min(VENCIMIENTOS_VISIBLES, obligaciones.size()))) {
            Label dia = new Label(String.valueOf(o.fecha().getDayOfMonth()));
            dia.getStyleClass().add("ini-fecha-dia");
            Label mes = new Label(MES_ABREV.format(o.fecha()).replace(".", "").toUpperCase(RonaldmichaFormatos.LOCALE_PE));
            mes.getStyleClass().add("ini-fecha-mes");
            VBox bloque = new VBox(-2, dia, mes);
            bloque.setAlignment(Pos.CENTER);
            bloque.getStyleClass().add("ini-fecha");
            bloque.setMinSize(48, 52);
            bloque.setPrefSize(48, 52);
            bloque.setMaxSize(48, 52);

            String plazo = o.desde() == null ? "" : "Del " + o.desde().getDayOfMonth() + " al "
                    + o.fecha().getDayOfMonth() + " · ";
            long restan = o.diasRestantes(hoy);
            Label cuando = new Label(restan == 0 ? "Vence hoy" : restan == 1 ? "Vence mañana" : "En " + restan + " días");
            cuando.getStyleClass().add("ini-meta");
            VBox textos = new VBox(1, negrita(o.titulo()), detalle(plazo + o.detalle()), cuando);
            textos.setMinWidth(0);
            HBox.setHgrow(textos, Priority.ALWAYS);
            HBox fila = new HBox(12, bloque, textos);
            fila.setAlignment(Pos.TOP_LEFT);
            caja.getChildren().add(fila);
        }
        if (obligaciones.isEmpty()) {
            caja.getChildren().add(vacio("Sin vencimientos próximos"));
        }
        return caja;
    }

    private static VBox pensiones(List<GrupoPension> grupos) {
        int total = grupos.stream().mapToInt(GrupoPension::trabajadores).sum();
        VBox caja = new VBox(16);
        for (int i = 0; i < grupos.size(); i++) {
            GrupoPension g = grupos.get(i);
            double fraccion = total == 0 ? 0 : (double) g.trabajadores() / total;
            Label cantidad = new Label(g.trabajadores() + (g.trabajadores() == 1 ? " afiliado" : " afiliados"));
            cantidad.getStyleClass().add("ini-detalle");
            Label monto = new Label(RonaldmichaFormatos.moneda(g.monto()));
            monto.getStyleClass().add("ini-monto");
            monto.setMinWidth(Region.USE_PREF_SIZE);
            HBox textos = new HBox(8, negrita(g.grupo()), cantidad, espacio(), monto);
            textos.setAlignment(Pos.BASELINE_LEFT);
            Region fondo = new Region();
            fondo.getStyleClass().add("ini-barra-fondo");
            Region relleno = new Region();
            relleno.getStyleClass().addAll("ini-barra", "tono-" + Math.min(i, 2));
            relleno.maxWidthProperty().bind(fondo.widthProperty().multiply(fraccion));
            StackPane barra = new StackPane(fondo, relleno);
            StackPane.setAlignment(relleno, Pos.CENTER_LEFT);
            barra.setMinHeight(6);
            barra.setPrefHeight(6);
            barra.setMaxHeight(6);
            caja.getChildren().add(new VBox(8, textos, barra));
        }
        return caja;
    }

    private VBox actividad() {
        VBox caja = new VBox(12);
        List<RonaldmichaRegistroAuditoria> registros = contexto.auditoria().recientes(ACTIVIDAD_MAXIMA);
        if (registros.isEmpty()) {
            caja.getChildren().add(vacio("Sin actividad registrada"));
            return caja;
        }
        for (RonaldmichaRegistroAuditoria r : registros) {
            Region punto = new Region();
            punto.getStyleClass().add("ini-punto");
            if (sensible(r.accion())) {
                punto.getStyleClass().add("sensible");
            }
            punto.setMinSize(8, 8);
            punto.setPrefSize(8, 8);
            punto.setMaxSize(8, 8);
            StackPane marco = new StackPane(punto);
            marco.setMinSize(8, 18);
            marco.setPrefSize(8, 18);
            marco.setMaxSize(8, 18);
            marco.setAlignment(Pos.CENTER);

            VBox textos = new VBox(1, negrita(RonaldmichaAcciones.texto(r.accion())));
            if (r.detalle() != null && !r.detalle().isBlank()) {
                Label det = detalle(r.detalle());
                det.setMaxHeight(34);
                det.setTooltip(new Tooltip(r.detalle()));
                textos.getChildren().add(det);
            }
            Label quien = new Label(r.usuario() + " · " + (r.fechaHora() == null ? "" : RonaldmichaFormatos.fechaHora(r.fechaHora())));
            quien.getStyleClass().add("ini-meta");
            textos.getChildren().add(quien);
            textos.setMinWidth(0);
            HBox.setHgrow(textos, Priority.ALWAYS);
            HBox fila = new HBox(10, marco, textos);
            fila.setAlignment(Pos.TOP_LEFT);
            caja.getChildren().add(fila);
        }
        return caja;
    }

    static boolean sensible(String accion) {
        String a = accion == null ? "" : accion.toUpperCase(Locale.ROOT);
        return SENSIBLES.stream().anyMatch(a::contains);
    }

    private void ir(Destino d) {
        if (d == Destino.APROBACIONES) {
            RonaldmichaNavegacion.abrirAprobaciones();
            return;
        }
        RonaldmichaAppContext.Seccion seccion = switch (d) {
            case PLANILLA_DETALLE -> RonaldmichaAppContext.Seccion.PLANILLA_DETALLADA;
            case PLANILLA_CIERRE -> RonaldmichaAppContext.Seccion.PLANILLA_CIERRE;
            case BOLETAS -> RonaldmichaAppContext.Seccion.BOLETA_PAGO;
            default -> null;
        };
        contexto.seccionSolicitadaProperty().set(seccion);
        RonaldmichaNavegacion.ir(switch (d) {
            case EMPRESA -> RonaldmichaVista.EMPRESA;
            case PARAMETROS -> RonaldmichaVista.PARAMETROS;
            case TRABAJADORES -> RonaldmichaVista.TRABAJADORES;
            case ASISTENCIA -> RonaldmichaVista.ASISTENCIA;
            case PLANILLA, PLANILLA_DETALLE, PLANILLA_CIERRE -> RonaldmichaVista.PLANILLA_MENSUAL;
            case REPORTES, BOLETAS, APROBACIONES -> RonaldmichaVista.REPORTES;
            case VACACIONES -> RonaldmichaVista.VACACIONES;
        });
    }

    private static void clicable(Region region, String textoAccesible, Runnable accion) {
        region.setFocusTraversable(true);
        region.setAccessibleRole(AccessibleRole.BUTTON);
        region.setAccessibleText(textoAccesible);
        region.getStyleClass().add("clicable");
        region.setOnMouseClicked(e -> accion.run());
        region.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                e.consume();
                accion.run();
            }
        });
    }

    private void entrada() {
        if (RonaldmichaPreferencias.reducirMovimiento()) {
            raiz.setOpacity(1);
            return;
        }
        FadeTransition fade = new FadeTransition(ENTRADA, raiz);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private double ancho() {
        double visible = Math.min(scroll.getViewportBounds().getWidth(), RonaldmichaPagina.ANCHO_MAXIMO);
        return visible - raiz.getInsets().getLeft() - raiz.getInsets().getRight();
    }

    private static GridPane rejilla() {
        GridPane g = new GridPane();
        g.setHgap(SEPARACION);
        g.setVgap(SEPARACION);
        g.setMinWidth(0);
        g.setMaxHeight(Region.USE_PREF_SIZE);
        return g;
    }

    private static void igualarAlto(GridPane g, List<Node> tarjetas) {
        g.getRowConstraints().forEach(rc -> rc.setFillHeight(true));
        tarjetas.forEach(t -> ((Region) t).setMaxHeight(Double.MAX_VALUE));
    }

    private static void colocar(GridPane g, List<Node> nodos, int columnas) {
        double[] pesos = new double[columnas];
        Arrays.fill(pesos, 1);
        colocar(g, nodos, pesos);
    }

    private static void colocar(GridPane g, List<Node> nodos, double... pesos) {
        double suma = Arrays.stream(pesos).sum();
        List<ColumnConstraints> columnas = new ArrayList<>();
        for (double p : pesos) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100 * p / suma);
            c.setFillWidth(true);
            columnas.add(c);
        }
        int filas = (nodos.size() + pesos.length - 1) / pesos.length;
        List<RowConstraints> lineas = new ArrayList<>();
        for (int i = 0; i < filas; i++) {
            RowConstraints rc = new RowConstraints();
            rc.setVgrow(Priority.NEVER);
            rc.setValignment(VPos.TOP);
            rc.setFillHeight(false);
            lineas.add(rc);
        }
        g.getChildren().clear();
        g.getColumnConstraints().setAll(columnas);
        g.getRowConstraints().setAll(lineas);
        for (int i = 0; i < nodos.size(); i++) {
            Node n = nodos.get(i);
            if (n instanceof Region r) {
                r.setMaxWidth(Double.MAX_VALUE);
                if (r.getMaxHeight() == Region.USE_COMPUTED_SIZE) {
                    r.setMaxHeight(Region.USE_PREF_SIZE);
                }
            }
            g.add(n, i % pesos.length, i / pesos.length);
        }
    }

    private static VBox tarjeta(String titulo, Node contenido) {
        VBox caja = new VBox(14, titulo(titulo), contenido);
        caja.getStyleClass().add("ini-tarjeta");
        VBox.setVgrow(contenido, Priority.NEVER);
        return caja;
    }

    private static Label titulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("ini-titulo");
        l.setWrapText(true);
        return l;
    }

    private static Label negrita(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("ini-negrita");
        l.setWrapText(true);
        return l;
    }

    private static Label detalle(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("ini-detalle");
        l.setWrapText(true);
        return l;
    }

    private static Label vacio(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("ini-vacio");
        return l;
    }

    private static Region espacio() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static StringConverter<Number> soles() {
        return new StringConverter<>() {
            @Override
            public String toString(Number n) {
                double v = n.doubleValue();
                if (Math.abs(v) < 1000) {
                    return "S/ " + Math.round(v);
                }
                BigDecimal miles = BigDecimal.valueOf(v / 1000).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
                return "S/ " + miles.toPlainString() + "K";
            }

            @Override
            public Number fromString(String s) {
                return 0;
            }
        };
    }

    private static String mayuscula(String s) {
        return s == null || s.isEmpty() ? "" : s.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + s.substring(1);
    }

    private static String mesCorto(YearMonth m) {
        return mayuscula(MES_ABREV.format(m).replace(".", ""));
    }
}
