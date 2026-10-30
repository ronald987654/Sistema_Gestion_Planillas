package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.ui.components.RonaldmichaModal;
import com.planillaperu.ui.components.RonaldmichaToast;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RonaldmichaNavegacionSmokeTest {

    private static boolean hayPantalla;

    @TempDir
    Path carpeta;

    @BeforeAll
    static void iniciarJavaFx() throws Exception {
        CompletableFuture<Boolean> listo = new CompletableFuture<>();
        try {
            Platform.startup(() -> listo.complete(true));
            Platform.setImplicitExit(false);
            hayPantalla = listo.get(10, TimeUnit.SECONDS);
        } catch (IllegalStateException yaIniciado) {
            hayPantalla = true;
        } catch (UnsupportedOperationException | Error sinPantalla) {
            hayPantalla = false;
        }
    }

    @Test
    void administradorYTrabajadorNavegan() throws Exception {
        assumeTrue(hayPantalla, "Sin entorno gráfico");
        RonaldmichaAppContext contexto = RonaldmichaAppContext.iniciar(carpeta.resolve("ui.db"));

        enFx(() -> {
            RonaldmichaRecursos.cargarFuentes();
            Stage stage = new Stage();
            RonaldmichaVentanas.mostrarLogin(stage, contexto);
            RonaldmichaModal modal = new RonaldmichaModal("Prueba", new Label("contenido"));
            modal.mostrar(stage.getScene().getRoot());
            modal.cerrar();
            RonaldmichaToast.exito(stage.getScene().getRoot(), "Aviso de prueba");

            recorrer(stage, contexto, "admin", "admin123", RonaldmichaRol.ADMIN, 13);
            recorrer(stage, contexto, "aprobador", "aprob123", RonaldmichaRol.APROBADOR, 13);
            recorrer(stage, contexto, "40000002", "123456", RonaldmichaRol.TRABAJADOR, 5);
            stage.close();
        });
        assertThat(RonaldmichaTareas.esperar(java.time.Duration.ofSeconds(60))).as("tareas en segundo plano terminadas").isTrue();
        enFx(() -> { });
        enFx(() -> { });

        assertThat(contexto.auditoria().recientes(100))
                .extracting(r -> r.accion())
                .contains("INICIO_SESION", "CIERRE_SESION");
    }

    private static void recorrer(Stage stage, RonaldmichaAppContext contexto, String usuario, String clave, RonaldmichaRol rol,
                                 int vistasEsperadas) {
        contexto.iniciarSesion(contexto.usuarios().autenticar(usuario, clave,
                rol == RonaldmichaRol.TRABAJADOR ? EnumSet.of(RonaldmichaRol.TRABAJADOR) : EnumSet.of(RonaldmichaRol.ADMIN, RonaldmichaRol.APROBADOR)));
        RonaldmichaVentanas.mostrarPrincipal(stage, contexto);
        StackPane raiz = (StackPane) stage.getScene().getRoot();
        raiz.applyCss();
        raiz.layout();

        List<ToggleButton> items = raiz.lookupAll(".sidebar-item").stream()
                .map(ToggleButton.class::cast).toList();
        assertThat(items).hasSize(vistasEsperadas);

        for (ToggleButton item : items) {
            item.fire();
            RonaldmichaVista vista = (RonaldmichaVista) item.getUserData();
            Label titulo = (Label) raiz.lookup(".view-title");
            assertThat(titulo.getText()).isEqualTo(vista.titulo());
            Node contenido = ((StackPane) raiz.lookup(".content")).getChildren().getFirst();
            assertThat(contenido).as("contenido de %s", vista).isNotNull();
            if (vista == RonaldmichaVista.TRABAJADORES) {
                revisarTrabajadores(raiz, contenido, rol);
            }
            if (vista == RonaldmichaVista.ASISTENCIA || vista == RonaldmichaVista.MI_ASISTENCIA) {
                revisarAsistencia(raiz, contexto, rol);
            }
            if (vista == RonaldmichaVista.PLANILLA_MENSUAL) {
                revisarPlanilla(raiz, rol);
            }
            if (vista == RonaldmichaVista.GRATIFICACIONES || vista == RonaldmichaVista.CTS) {
                revisarBeneficio(raiz, rol, vista);
            }
            if (vista == RonaldmichaVista.REPORTES) {
                revisarReportes(raiz, rol);
            }
            if (vista == RonaldmichaVista.LIQUIDACIONES) {
                revisarLiquidaciones(raiz, rol);
            }
            if (vista == RonaldmichaVista.VACACIONES || vista == RonaldmichaVista.MIS_VACACIONES) {
                revisarVacaciones(raiz, rol);
            }
            if (vista == RonaldmichaVista.INICIO) {
                revisarInicio(raiz, contexto, rol);
            }
            if (vista == RonaldmichaVista.RESUMEN_ANUAL) {
                revisarResumenAnual(raiz);
            }
            if (vista == RonaldmichaVista.USUARIOS) {
                revisarUsuarios(raiz, rol);
            }
            if (vista == RonaldmichaVista.MIS_DOCUMENTOS) {
                revisarMisDocumentos(raiz);
            }
            if (vista == RonaldmichaVista.MIS_DATOS) {
                revisarMisDatos(raiz);
            }
            capturar(raiz, rol + "-" + vista);
        }

        raiz.resize(1000, 700);
        raiz.layout();
        assertThat(raiz.lookup(".sidebar").getStyleClass()).contains("collapsed");
        capturar(raiz, rol + "-angosto");
        raiz.resize(1280, 800);

        contexto.cerrarSesion();
        RonaldmichaVentanas.mostrarLogin(stage, contexto);
    }

    private static void revisarTrabajadores(StackPane raiz, Node contenido, RonaldmichaRol rol) {
        contenido.applyCss();
        TableView<?> tabla = (TableView<?>) contenido.lookup(".table-view");
        assertThat(tabla.getItems()).hasSize(7);
        Button nuevo = contenido.lookupAll(".btn-primary").stream()
                .map(Button.class::cast)
                .filter(b -> b.getText().contains("Nuevo trabajador"))
                .findFirst().orElseThrow();
        assertThat(nuevo.isVisible()).isEqualTo(rol == RonaldmichaRol.ADMIN);
        if (rol != RonaldmichaRol.ADMIN) {
            return;
        }
        nuevo.fire();
        raiz.applyCss();
        raiz.layout();
        Node overlay = raiz.lookup(".modal-overlay");
        assertThat(overlay).isNotNull();
        assertThat(overlay.lookup(".recuadro-pension")).isNotNull();
        capturar(raiz, rol + "-FORMULARIO");
        overlay.lookupAll(".button").stream()
                .map(Button.class::cast)
                .filter(b -> "Cancelar".equals(b.getText()))
                .findFirst().orElseThrow().fire();
    }

    private static void revisarAsistencia(StackPane raiz, RonaldmichaAppContext contexto, RonaldmichaRol rol) {
        raiz.applyCss();
        raiz.layout();
        Node contenido = ((StackPane) raiz.lookup(".content")).getChildren().getFirst();
        TableView<?> tabla = (TableView<?>) contenido.lookup(".table-view");
        assertThat(tabla.getItems()).hasSize(contexto.periodoProperty().get().lengthOfMonth());
        assertThat(contenido.lookupAll(".kpi-sm")).hasSize(9);
        List<String> botones = contenido.lookupAll(".btn").stream()
                .filter(b -> b instanceof Button && b.isVisible()).map(b -> ((Button) b).getText()).toList();
        if (rol == RonaldmichaRol.TRABAJADOR) {
            assertThat(botones).contains("Descargar mi asistencia (Excel)").doesNotContain("Llenar días con el horario",
                    "Guardar horario");
        } else if (rol == RonaldmichaRol.ADMIN) {
            assertThat(botones).contains("Llenar días con el horario", "Importar Excel", "Plantilla Excel",
                    "Exportar Excel", "Borrar marcaciones del mes", "Ver en la planilla", "Guardar horario");
        } else {
            assertThat(botones).contains("Exportar Excel").doesNotContain("Llenar días con el horario");
        }
    }

    private static void revisarPlanilla(StackPane raiz, RonaldmichaRol rol) {
        raiz.applyCss();
        raiz.layout();
        Node contenido = ((StackPane) raiz.lookup(".content")).getChildren().getFirst();
        assertThat(contenido.lookupAll(".mes-boton")).hasSize(12);
        assertThat(contenido.lookupAll(".kpi")).hasSize(5);
        TableView<?> tabla = (TableView<?>) contenido.lookup(".table-view");
        assertThat(tabla.getItems()).hasSize(8);
        List<String> botones = contenido.lookupAll(".button").stream().filter(Node::isVisible)
                .map(b -> ((Button) b).getText()).filter(java.util.Objects::nonNull).toList();
        assertThat(botones.stream().anyMatch(t -> t.startsWith("Cerrar planilla de"))).isEqualTo(rol == RonaldmichaRol.ADMIN);
        assertThat(botones).contains("Enviar boletas", "Excel", "PDF");

        Button editar = contenido.lookupAll(".button").stream().map(Button.class::cast)
                .filter(b -> "Editar".equals(b.getText()) || "Ver".equals(b.getText()))
                .findFirst().orElseThrow();
        editar.fire();
        raiz.applyCss();
        raiz.layout();
        Node overlay = List.copyOf(raiz.lookupAll(".modal-overlay")).getLast();
        assertThat(overlay).isNotNull();
        javafx.scene.control.ScrollPane desplazable = (javafx.scene.control.ScrollPane) overlay.lookup(".modal-scroll");
        assertThat(desplazable.getContent().lookup(".conceptos-planilla")).isNotNull();
        assertThat(desplazable.getContent().lookup(".neto-valor")).isNotNull();
        capturar(raiz, rol + "-PLANILLA-DETALLE");
        overlay.lookupAll(".button").stream().map(Button.class::cast)
                .filter(b -> "Cancelar".equals(b.getText()) || "Cerrar".equals(b.getText()))
                .findFirst().orElseThrow().fire();

        ToggleButton detallada = contenido.lookupAll(".toggle-button").stream().map(ToggleButton.class::cast)
                .filter(b -> "Detallada".equals(b.getText())).findFirst().orElseThrow();
        detallada.fire();
        raiz.applyCss();
        raiz.layout();
        TableView<?> tablaDetallada = (TableView<?>) contenido.lookup(".table-view");
        assertThat(tablaDetallada.getColumns().size()).isGreaterThan(9);
        capturar(raiz, rol + "-PLANILLA-DETALLADA");
    }

    private static void revisarBeneficio(StackPane raiz, RonaldmichaRol rol, RonaldmichaVista vista) {
        raiz.applyCss();
        raiz.layout();
        Node contenido = ((StackPane) raiz.lookup(".content")).getChildren().getFirst();
        TableView<?> tabla = (TableView<?>) contenido.lookup(".table-view");
        assertThat(tabla.getItems()).hasSize(8);
        assertThat(tabla.getColumns().stream().filter(c -> c.getStyleClass().contains("col-mes"))).hasSize(6);
        assertThat(botones(contenido)).contains("Enviar", "Exportar");
        assertThat(botones(contenido).contains("Editar")).isEqualTo(rol == RonaldmichaRol.ADMIN);
        if (rol == RonaldmichaRol.ADMIN) {
            boton(contenido, "Editar").fire();
            raiz.applyCss();
            raiz.layout();
            assertThat(botones(contenido)).contains("Guardar", "Cancelar").doesNotContain("Editar");
            capturar(raiz, rol + "-" + vista + "-EDICION");
            boton(contenido, "Cancelar").fire();
            raiz.applyCss();
            raiz.layout();
            assertThat(botones(contenido)).contains("Editar").doesNotContain("Guardar");
        }
        boton(contenido, "Exportar").fire();
        raiz.applyCss();
        raiz.layout();
        Node overlay = List.copyOf(raiz.lookupAll(".modal-overlay")).getLast();
        assertThat(overlay.lookupAll(".form-section")).hasSize(3);
        capturar(raiz, rol + "-" + vista + "-EXPORTAR");
        boton(overlay, "Cerrar").fire();
    }

    private static void revisarInicio(StackPane raiz, RonaldmichaAppContext contexto, RonaldmichaRol rol) {
        YearMonth octubre = YearMonth.of(2026, 10);
        contexto.periodoProperty().set(octubre);
        Node contenido = inicioListo(raiz);
        assertThat(contenido.lookup(".dark-panel")).as("tema claro, sin panel oscuro").isNull();
        assertThat(contenido).isInstanceOf(javafx.scene.control.ScrollPane.class);
        assertThat(((javafx.scene.control.ScrollPane) contenido).isFitToWidth()).isTrue();

        assertThat(textos(contenido, ".ini-contexto").getFirst()).startsWith("Planilla de octubre 2026 · 7 trabajadores · pago el ");
        assertThat(textos(contenido, ".ini-saludo").getFirst()).matches("Buen(os días|as tardes|as noches), .+");
        assertThat(contenido.lookupAll(".btn-primary").stream().map(b -> ((Button) b).getText()))
                .anyMatch(t -> t.startsWith("Continuar: ") && t.endsWith(" →"));

        List<Node> acciones = List.copyOf(contenido.lookupAll(".ini-accion"));
        assertThat(acciones).hasSize(10);
        assertThat(acciones).allSatisfy(a -> assertThat(a.getLayoutBounds().getHeight()).isEqualTo(56.0));
        assertThat(acciones.stream().map(Node::getLayoutY).distinct()).as("5 × 2").hasSize(2);
        assertThat(textos(contenido, ".ini-formato")).containsExactly("PDF", "XLSX");
        List<Node> pasos = List.copyOf(contenido.lookupAll(".ini-paso"));
        assertThat(pasos).hasSize(7);
        assertThat(contenido.lookupAll(".ini-paso.actual")).hasSize(1);
        assertThat(pasos.stream().map(Node::getLayoutY).distinct()).as("los 7 pasos en una fila").hasSize(1);
        assertThat(contenido.lookupAll(".ini-kpi-valor")).hasSize(4);
        assertThat(contenido.lookupAll(".ini-kpi.neto")).hasSize(1);
        assertThat(contenido.lookupAll(".ini-grafico")).hasSize(1);
        assertThat(contenido.lookupAll(".ini-pendiente.critico")).isNotEmpty();
        assertThat(contenido.lookupAll(".ini-pendiente.atencion")).isNotEmpty();
        assertThat(textos(contenido, ".ini-negrita")).anyMatch(t -> t.contains("aprobaci"));
        assertThat(contenido.lookupAll(".ini-fecha")).hasSize(3);
        assertThat(contenido.lookupAll(".ini-punto").size()).isBetween(1, 5);
        capturar(raiz, "INICIO");
        capturar(contenido.lookup(".inicio"), "INICIO-COMPLETO");
        if (rol != RonaldmichaRol.ADMIN) {
            return;
        }

        String[] destinos = {"Datos de la empresa", "Trabajadores", "Asistencia y horas extra", "Planilla mensual",
                "Planilla mensual", "Planilla mensual", "Reportes"};
        for (int i = 0; i < destinos.length; i++) {
            Node paso = List.copyOf(inicioListo(raiz).lookupAll(".ini-paso")).get(i);
            paso.getOnMouseClicked().handle(null);
            assertThat(((Label) raiz.lookup(".view-title")).getText()).as("paso %d", i + 1).isEqualTo(destinos[i]);
            raiz.applyCss();
            raiz.layout();
            Node vista = ((StackPane) raiz.lookup(".content")).getChildren().getFirst();
            if (i == 4) {
                assertThat(vista.lookupAll(".toggle-button").stream().map(ToggleButton.class::cast)
                        .filter(t -> "Detallada".equals(t.getText()) || "Vista detallada".equals(t.getText()))
                        .anyMatch(ToggleButton::isSelected)).as("Revisión abre la vista detallada").isTrue();
            }
            if (i == 6) {
                assertThat(vista.lookupAll(".tarjeta-reporte").stream().map(ToggleButton.class::cast)
                        .filter(ToggleButton::isSelected).map(Node::getUserData).toList())
                        .containsExactly(com.planillaperu.service.RonaldmichaReporte.BOLETA_PAGO);
            }
            RonaldmichaNavegacion.ir(RonaldmichaVista.INICIO);
        }

        String[][] rapidas = {{"Asistencia", "Asistencia y horas extra"}, {"Planilla del mes", "Planilla mensual"},
                {"Gratificación", "Gratificaciones"}, {"CTS", "CTS"}, {"Vacaciones", "Vacaciones"},
                {"Liquidar", "Liquidaciones"}, {"Reportes", "Reportes"}};
        for (String[] r : rapidas) {
            Node accion = inicioListo(raiz).lookupAll(".ini-accion").stream()
                    .filter(a -> a.getAccessibleText().equals(r[0])).findFirst().orElseThrow();
            accion.getOnMouseClicked().handle(null);
            assertThat(((Label) raiz.lookup(".view-title")).getText()).as("acción %s", r[0]).isEqualTo(r[1]);
            RonaldmichaNavegacion.ir(RonaldmichaVista.INICIO);
        }

        List<String> kpisOctubre = textos(inicioListo(raiz), ".ini-kpi-valor");
        contexto.periodoProperty().set(octubre.minusMonths(1));
        Node setiembre = inicioListo(raiz);
        assertThat(textos(setiembre, ".ini-kpi-valor")).isNotEqualTo(kpisOctubre);
        assertThat(textos(setiembre, ".ini-titulo")).contains("Ciclo de la planilla · Setiembre 2026");
        javafx.scene.chart.XYChart<?, ?> barras = (javafx.scene.chart.XYChart<?, ?>) setiembre.lookup(".ini-grafico");
        assertThat(barras.getData()).hasSize(2);
        assertThat(barras.getData().getFirst().getData().getLast().getXValue()).isEqualTo("Set");
        capturar(raiz, "INICIO-SETIEMBRE");
        contexto.periodoProperty().set(octubre);
    }

    private static Node inicioListo(StackPane raiz) {
        raiz.applyCss();
        raiz.layout();
        Node contenido = ((StackPane) raiz.lookup(".content")).getChildren().getFirst();
        contenido.setOpacity(1);
        if (contenido.lookup(".inicio") instanceof Node pagina) {
            pagina.setOpacity(1);
        }
        raiz.applyCss();
        raiz.layout();
        return contenido;
    }

    private static List<String> textos(Node contenido, String selector) {
        return contenido.lookupAll(selector).stream().map(n -> ((Label) n).getText()).toList();
    }

    private static void revisarReportes(StackPane raiz, RonaldmichaRol rol) {
        esperarVistaPrevia();
        raiz.applyCss();
        raiz.layout();
        Node contenido = ((StackPane) raiz.lookup(".content")).getChildren().getFirst();
        List<ToggleButton> tarjetas = contenido.lookupAll(".tarjeta-reporte").stream().map(ToggleButton.class::cast).toList();
        boolean trabajador = rol == RonaldmichaRol.TRABAJADOR;
        assertThat(tarjetas).hasSize(trabajador ? 6 : 13);
        assertThat(contenido.lookupAll(".grupo-reportes").stream().filter(Node::isVisible)
                .map(g -> ((Label) g).getText()).filter(t -> !"TRABAJADOR".equals(t)).toList())
                .containsExactlyElementsOf(trabajador ? List.of("MIS DOCUMENTOS")
                        : List.of("PLANILLA Y PAGOS", "DECLARACIONES", "BENEFICIOS Y CESE", "CONSTANCIAS Y CERTIFICADOS"));
        assertThat(tarjetas.stream().filter(ToggleButton::isSelected)).hasSize(1);
        assertThat(contenido.lookup(".registro-emitidos")).isNotNull();
        assertThat(contenido.lookupAll(".scroll-pane")).as("sin scroll interno en la vista previa")
                .noneMatch(s -> s != contenido && s.lookup(".hoja-documento") != null);

        for (ToggleButton tarjeta : tarjetas) {
            tarjeta.fire();
            esperarVistaPrevia();
            raiz.applyCss();
            raiz.layout();
            com.planillaperu.service.RonaldmichaReporte r = (com.planillaperu.service.RonaldmichaReporte) tarjeta.getUserData();
            javafx.scene.Parent hoja = (javafx.scene.Parent) contenido.lookup(".hoja-documento");
            if (hoja == null) {
                hoja = (javafx.scene.Parent) contenido.lookup(".hojas-boleta");
                assertThat(hoja).as("vista previa de %s", r).isNotNull();
                assertThat(r).isEqualTo(com.planillaperu.service.RonaldmichaReporte.BOLETA_PAGO);
                assertThat(hoja.lookupAll(".hoja-boleta")).as("dos copias en %s", r).hasSize(2);
                capturar(hoja, rol + "-HOJA-BOLETA");
            }
            assertThat(hoja.getChildrenUnmodifiable()).as("vista previa de %s", r).isNotEmpty();
            assertThat(contenido.lookup(".selector-trabajador").isVisible()).as("selector en %s", r)
                    .isEqualTo(r.porTrabajador() && !trabajador);
            List<String> textos = botones(contenido);
            if (trabajador) {
                assertThat(textos).contains("PDF").doesNotContain("Word", "Enviar por correo");
            } else {
                assertThat(textos).as("botones de %s", r).contains(switch (r) {
                    case PLANILLA -> "Excel";
                    case PAGOS -> "TXT banco";
                    case PLAME -> "Archivos PLAME (ZIP)";
                    case AFPNET -> "Excel AFPnet";
                    default -> "Word";
                });
                assertThat(textos.stream().anyMatch(t -> t.startsWith("Todos ("))).as("todos en %s", r)
                        .isEqualTo(r.todosEnUnPdf());
                if (r.porTrabajador() && r != com.planillaperu.service.RonaldmichaReporte.BOLETA_PAGO
                        && hoja.lookup(".documento-error") == null) {
                    assertThat(hoja.lookup(".documento-empresa")).as("encabezado en %s", r).isNotNull();
                    assertThat(hoja.lookupAll(".documento-firma-linea")).as("firmas en %s", r).hasSize(2);
                }
                if (!r.porTrabajador()) {
                    assertThat(hoja.lookup(".documento-error")).as("vista previa sin error en %s", r).isNull();
                    assertThat(hoja.lookup(".neto-letras")).as("Son: … soles en %s", r).isNotNull();
                }
            }
            capturar(raiz, rol + "-REPORTES-" + r);
        }
        tarjetas.getFirst().fire();
        esperarVistaPrevia();
        if (trabajador) {
            raiz.applyCss();
            Node alerta = contenido.lookup(".hoja-documento").lookup(".documento-error");
            assertThat(((Label) alerta).getText()).contains("aún no está disponible");
        }
    }

    @SuppressWarnings("unchecked")
    private static void revisarLiquidaciones(StackPane raiz, RonaldmichaRol rol) {
        raiz.applyCss();
        raiz.layout();
        Node contenido = ((StackPane) raiz.lookup(".content")).getChildren().getFirst();
        TableView<?> tabla = (TableView<?>) contenido.lookup(".table-view");
        assertThat(tabla.getItems()).isEmpty();
        assertThat(botones(contenido).contains("+ Nueva liquidación")).isEqualTo(rol == RonaldmichaRol.ADMIN);
        if (rol != RonaldmichaRol.ADMIN) {
            return;
        }
        boton(contenido, "+ Nueva liquidación").fire();
        raiz.applyCss();
        raiz.layout();
        Node overlay = List.copyOf(raiz.lookupAll(".modal-overlay")).getLast();
        javafx.scene.control.ScrollPane desplazable = (javafx.scene.control.ScrollPane) overlay.lookup(".modal-scroll");
        javafx.scene.control.ComboBox<Object> trabajador =
                (javafx.scene.control.ComboBox<Object>) desplazable.getContent().lookup(".combo-box");
        trabajador.getSelectionModel().selectFirst();
        raiz.applyCss();
        raiz.layout();
        assertThat(desplazable.getContent().lookupAll(".bloque-liquidacion")).hasSize(4);
        assertThat(desplazable.getContent().lookup(".neto-letras")).isNotNull();
        assertThat(botones(overlay)).contains("Reporte PDF", "Reporte Word", "Certificado de trabajo", "Cancelar",
                "Guardar", "Guardar y registrar cese");
        capturar(raiz, rol + "-LIQUIDACION-DETALLE");
        boton(overlay, "Cancelar").fire();
    }

    private static void revisarVacaciones(StackPane raiz, RonaldmichaRol rol) {
        raiz.applyCss();
        raiz.layout();
        Node contenido = ((StackPane) raiz.lookup(".content")).getChildren().getFirst();
        if (rol != RonaldmichaRol.TRABAJADOR) {
            TableView<?> tabla = (TableView<?>) contenido.lookup(".table-view");
            assertThat(tabla.getItems()).hasSize(7);
            assertThat(contenido.lookup(".panel-vacaciones").isVisible()).isFalse();
            boton(contenido, "Detalle").fire();
            raiz.applyCss();
            raiz.layout();
            assertThat(tabla.getSelectionModel().getSelectedIndex()).isZero();
            assertThat(botones(contenido)).contains("Cerrar", "Word", "PDF");
        }
        Node panel = contenido.lookup(".panel-vacaciones");
        assertThat(panel.isVisible()).isTrue();
        assertThat(panel.lookupAll(".kpi")).hasSize(6);
        String planificar = rol == RonaldmichaRol.TRABAJADOR ? "+ Solicitar vacaciones" : "+ Planificar vacaciones";
        assertThat(botones(contenido).contains(planificar)).isEqualTo(rol != RonaldmichaRol.APROBADOR);
        capturar(raiz, rol + "-VACACIONES-DETALLE");
        if (rol == RonaldmichaRol.APROBADOR) {
            return;
        }
        boton(contenido, planificar).fire();
        raiz.applyCss();
        raiz.layout();
        Node overlay = List.copyOf(raiz.lookupAll(".modal-overlay")).getLast();
        assertThat(overlay.lookupAll(".toggle-button")).extracting(b -> ((ToggleButton) b).getText())
                .containsExactly("Planificar (goce)", "Comprar (venta)");
        assertThat(overlay.lookup(".dias-calculados")).isNotNull();
        capturar(raiz, rol + "-VACACIONES-MODAL");
        boton(overlay, "Cancelar").fire();
    }

    private static void revisarUsuarios(StackPane raiz, RonaldmichaRol rol) {
        raiz.applyCss();
        raiz.layout();
        Node contenido = ((StackPane) raiz.lookup(".content")).getChildren().getFirst();
        List<ToggleButton> secciones = contenido.lookupAll(".seccion-seguridad").stream()
                .map(ToggleButton.class::cast).toList();
        assertThat(secciones).hasSize(5);
        assertThat(((TableView<?>) contenido.lookup(".tabla-usuarios")).getItems()).hasSize(3);
        assertThat(botones(contenido).contains("+ Nuevo usuario")).isEqualTo(rol == RonaldmichaRol.ADMIN);
        capturar(raiz, rol + "-USUARIOS");

        secciones.get(1).fire();
        raiz.applyCss();
        raiz.layout();
        assertThat(contenido.lookup(".matriz-permisos")).isNotNull();
        assertThat(contenido.lookupAll(".permiso-si")).isNotEmpty();
        capturar(raiz, rol + "-PERMISOS");

        secciones.get(2).fire();
        raiz.applyCss();
        raiz.layout();
        assertThat(contenido.lookupAll(".regla-seguridad")).hasSize(6);

        secciones.get(3).fire();
        raiz.applyCss();
        raiz.layout();
        assertThat(secciones.get(3).getText()).isEqualTo("Aprobaciones (1)");
        assertThat(((TableView<?>) contenido.lookup(".tabla-aprobaciones")).getItems()).hasSize(1);

        secciones.get(4).fire();
        raiz.applyCss();
        raiz.layout();
        assertThat(((TableView<?>) contenido.lookup(".tabla-auditoria")).getItems()).isNotEmpty();
        assertThat(botones(contenido)).contains("Filtrar", "Exportar Excel");
        capturar(raiz, rol + "-AUDITORIA");
    }

    private static void revisarMisDocumentos(StackPane raiz) {
        raiz.applyCss();
        raiz.layout();
        Node contenido = ((StackPane) raiz.lookup(".content")).getChildren().getFirst();
        assertThat(((TableView<?>) contenido.lookup(".tabla-boletas")).getItems()).isNotEmpty();
        assertThat(botones(contenido)).doesNotContain("Descargar PDF");
        capturar(raiz, "MIS-DOCUMENTOS");
    }

    private static void revisarResumenAnual(StackPane raiz) {
        raiz.applyCss();
        raiz.layout();
        Node contenido = ((StackPane) raiz.lookup(".content")).getChildren().getFirst();
        TableView<?> tabla = (TableView<?>) contenido.lookup(".tabla-resumen-anual");
        assertThat(tabla.getItems()).hasSize(8);
        assertThat(tabla.getColumns().stream().filter(c -> c.getStyleClass().contains("col-mes-anual"))).hasSize(12);
        assertThat(tabla.getColumns().stream().filter(c -> c.getStyleClass().contains("col-grati"))).hasSize(2);
        assertThat(((TableView<?>) contenido.lookup(".tabla-conceptos-anual")).getItems()).hasSize(7);
        assertThat(botones(contenido)).contains("Exportar Excel");
        capturar(raiz, "RESUMEN-ANUAL");
        contenido.lookupAll(".toggle-button").stream().map(ToggleButton.class::cast)
                .filter(b -> "Costo laboral".equals(b.getText())).findFirst().orElseThrow().fire();
        assertThat(tabla.getItems()).hasSize(8);
    }

    private static void revisarMisDatos(StackPane raiz) {
        raiz.applyCss();
        raiz.layout();
        Node contenido = ((StackPane) raiz.lookup(".content")).getChildren().getFirst();
        assertThat(contenido.lookupAll(".tarjeta-mis-datos")).hasSize(3);
        assertThat(botones(contenido)).contains("Guardar contacto", "Solicitar cambio de cuenta");
        capturar(raiz, "MIS-DATOS");
    }

    private static List<String> botones(Node contenido) {
        return contenido.lookupAll(".button").stream().filter(Node::isVisible)
                .map(b -> ((Button) b).getText()).filter(java.util.Objects::nonNull).toList();
    }

    private static Button boton(Node contenido, String texto) {
        return contenido.lookupAll(".button").stream().map(Button.class::cast)
                .filter(b -> texto.equals(b.getText())).findFirst().orElseThrow();
    }

    private static void capturar(Node nodo, String nombre) {
        String carpeta = System.getProperty("capturas");
        if (carpeta == null) {
            return;
        }
        visible(nodo);
        nodo.applyCss();
        for (int i = 0; i < 3; i++) {
            if (nodo instanceof javafx.scene.Parent p) {
                p.layout();
            }
            RonaldmichaEstilos.aplicar(nodo);
        }
        if (nodo instanceof javafx.scene.Parent p) {
            p.layout();
        }
        javafx.scene.SnapshotParameters ventana = new javafx.scene.SnapshotParameters();
        if (nodo instanceof javafx.scene.layout.Region r) {
            ventana.setViewport(new javafx.geometry.Rectangle2D(0, 0, r.getWidth(), r.getHeight()));
        }
        guardarPng(nodo.snapshot(ventana, null), carpeta, nombre);
        if (nodo.lookup(".content") instanceof StackPane contenido && !contenido.getChildren().isEmpty()
                && contenido.getChildren().getFirst() instanceof javafx.scene.control.ScrollPane scroll
                && scroll.getContent() != null) {
            guardarPng(scroll.getContent().snapshot(null, null), carpeta, nombre + "-completo");
        }
    }

    private static void visible(Node nodo) {
        if (nodo.getOpacity() < 1 && !nodo.opacityProperty().isBound()) {
            nodo.setOpacity(1);
        }
        if (nodo instanceof javafx.scene.Parent p) {
            p.getChildrenUnmodifiable().forEach(RonaldmichaNavegacionSmokeTest::visible);
        }
        if (nodo instanceof javafx.scene.control.ScrollPane s && s.getContent() != null) {
            visible(s.getContent());
        }
    }

    private static void guardarPng(javafx.scene.image.WritableImage img, String carpeta, String nombre) {
        int ancho = (int) img.getWidth();
        int alto = (int) img.getHeight();
        java.awt.image.BufferedImage salida =
                new java.awt.image.BufferedImage(ancho, alto, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javafx.scene.image.PixelReader lector = img.getPixelReader();
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                salida.setRGB(x, y, lector.getArgb(x, y));
            }
        }
        try {
            javax.imageio.ImageIO.write(salida, "png", Path.of(carpeta, nombre + ".png").toFile());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void esperarVistaPrevia() {
        assertThat(RonaldmichaTareas.esperar(java.time.Duration.ofSeconds(30))).as("vista previa generada").isTrue();
        Object clave = new Object();
        Platform.runLater(() -> Platform.exitNestedEventLoop(clave, null));
        Platform.enterNestedEventLoop(clave);
    }

    private static void enFx(Runnable accion) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CompletableFuture<Void> fin = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                accion.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                fin.complete(null);
            }
        });
        fin.get(180, TimeUnit.SECONDS);
        if (error.get() != null) {
            throw new AssertionError("Fallo en la interfaz", error.get());
        }
    }
}
