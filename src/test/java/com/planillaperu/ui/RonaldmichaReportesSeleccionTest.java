package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaTrabajadorResumen;
import com.planillaperu.ui.components.RonaldmichaWorkerPicker;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class RonaldmichaReportesSeleccionTest {

    private static final long ESPERA_MS = 5000;

    private RonaldmichaAppContext contexto;
    private static Stage ventana;

    @Start
    void iniciar(Stage stage) throws IOException {
        ventana = stage;
        List.copyOf(javafx.stage.Window.getWindows()).stream().filter(w -> w != stage).forEach(javafx.stage.Window::hide);
        Path carpeta = Files.createTempDirectory("reportes-ui");
        contexto = RonaldmichaAppContext.iniciar(carpeta.resolve("ui.db"));
        RonaldmichaRecursos.cargarFuentes();
        contexto.iniciarSesion(contexto.usuarios().autenticar("admin", "admin123", EnumSet.of(RonaldmichaRol.ADMIN, RonaldmichaRol.APROBADOR)));
        RonaldmichaVentanas.mostrarPrincipal(stage, contexto);
        stage.show();
        RonaldmichaNavegacion.ir(RonaldmichaVista.REPORTES);
    }

    @AfterEach
    void terminar() {
        RonaldmichaTareas.esperar(java.time.Duration.ofSeconds(30));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void alAbrirBoletaElPrimeroQuedaElegidoYSeVeSinHacerClic(FxRobot robot) throws TimeoutException {
        tarjeta(robot, "Boleta de pago");

        esperar(() -> picker(robot).getSelected() != null);
        RonaldmichaTrabajadorResumen primero = picker(robot).getSelected();
        assertThat(textos(campo(robot))).anyMatch(s -> s.contains(primero.apellidos()));
        esperarVistaCon(robot, nombre(primero));
    }

    @Test
    void escribirFlorYEnterEligeAFloresHuaman(FxRobot robot) throws TimeoutException {
        tarjeta(robot, "Boleta de pago");

        robot.clickOn(campo(robot));
        robot.write("flor");
        robot.type(KeyCode.ENTER);

        esperar(() -> picker(robot).getSelected() != null && picker(robot).getSelected().apPaterno().equals("Flores"));
        esperarVistaCon(robot, "Flores Huamán");
    }

    @Test
    void clicEnOtroDeLaListaCambiaLaVistaPrevia(FxRobot robot) throws TimeoutException {
        tarjeta(robot, "Boleta de pago");
        esperar(() -> picker(robot).getSelected() != null);
        RonaldmichaTrabajadorResumen antes = picker(robot).getSelected();

        robot.clickOn(campo(robot));
        @SuppressWarnings("unchecked")
        ListCell<RonaldmichaTrabajadorResumen> otra = robot.lookup(".worker-picker-cell").queryAllAs(ListCell.class).stream()
                .filter(c -> c.getItem() != null && !c.getItem().equals(antes))
                .findFirst().orElseThrow();
        RonaldmichaTrabajadorResumen otro = otra.getItem();
        robot.clickOn(otra);

        esperar(() -> otro.equals(picker(robot).getSelected()));
        esperarVistaCon(robot, nombre(otro));
    }

    @Test
    void lasFlechasCambianDeTrabajadorYDeVistaPrevia(FxRobot robot) throws TimeoutException {
        tarjeta(robot, "Boleta de pago");
        esperar(() -> picker(robot).getSelected() != null);
        RonaldmichaTrabajadorResumen primero = picker(robot).getSelected();

        robot.clickOn(flecha(robot, "Trabajador siguiente"));
        esperar(() -> picker(robot).getSelected() != null && !primero.equals(picker(robot).getSelected()));
        RonaldmichaTrabajadorResumen segundo = picker(robot).getSelected();
        esperarVistaCon(robot, nombre(segundo));

        robot.clickOn(flecha(robot, "Trabajador anterior"));
        esperar(() -> primero.equals(picker(robot).getSelected()));
        esperarVistaCon(robot, nombre(primero));
    }

    @Test
    void cambiarElMesYLaTarjetaActualizanLaVistaPrevia(FxRobot robot) throws TimeoutException {
        tarjeta(robot, "Boleta de pago");
        robot.clickOn(flecha(robot, "Trabajador siguiente"));
        esperar(() -> picker(robot).getSelected() != null);
        RonaldmichaTrabajadorResumen elegido = picker(robot).getSelected();
        esperarVistaCon(robot, nombre(elegido));

        @SuppressWarnings("unchecked")
        ComboBox<Month> mes = enVentana(robot).lookup(".combo-mes").queryAs(ComboBox.class);
        Month otroMes = contexto.periodoProperty().get().getMonth() == Month.APRIL ? Month.MAY : Month.APRIL;
        robot.interact(() -> mes.setValue(otroMes));
        esperar(() -> contexto.periodoProperty().get().getMonth() == otroMes);
        String periodo = YearMonth.of(contexto.periodoProperty().get().getYear(), otroMes)
                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", com.planillaperu.common.RonaldmichaFormatos.LOCALE_PE));
        esperarVistaCon(robot, nombre(elegido));
        esperarVistaCon(robot, periodo.toUpperCase(com.planillaperu.common.RonaldmichaFormatos.LOCALE_PE), periodo);
        assertThat(picker(robot).getSelected()).isEqualTo(elegido);

        tarjeta(robot, "Constancia de trabajo");
        esperarVistaCon(robot, "CONSTANCIA DE TRABAJO");
        esperar(() -> elegido.equals(picker(robot).getSelected()));
        esperarVistaCon(robot, elegido.nombres());
    }

    @Test
    void alSalirAInicioYVolverSeMantieneElTrabajador(FxRobot robot) throws TimeoutException {
        tarjeta(robot, "Boleta de pago");
        robot.clickOn(flecha(robot, "Trabajador siguiente"));
        esperar(() -> picker(robot).getSelected() != null);
        RonaldmichaTrabajadorResumen elegido = picker(robot).getSelected();
        RonaldmichaWorkerPicker mismo = picker(robot);

        robot.interact(() -> RonaldmichaNavegacion.ir(RonaldmichaVista.INICIO));
        robot.interact(() -> RonaldmichaNavegacion.ir(RonaldmichaVista.REPORTES));

        assertThat(picker(robot)).isSameAs(mismo);
        assertThat(picker(robot).getSelected()).isEqualTo(elegido);
        esperarVistaCon(robot, nombre(elegido));
    }

    private static org.testfx.service.query.NodeQuery enVentana(FxRobot robot) {
        return robot.from(ventana.getScene().getRoot());
    }

    private static void tarjeta(FxRobot robot, String titulo) {
        ToggleButton b = enVentana(robot).lookup(".tarjeta-reporte").queryAllAs(ToggleButton.class).stream()
                .filter(t -> textos(t).contains(titulo)).findFirst().orElseThrow();
        robot.clickOn(b);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static RonaldmichaWorkerPicker picker(FxRobot robot) {
        return enVentana(robot).lookup(".worker-picker").queryAs(RonaldmichaWorkerPicker.class);
    }

    private static Button campo(FxRobot robot) {
        return (Button) picker(robot).lookup(".worker-picker-field");
    }

    private static Button flecha(FxRobot robot, String accesible) {
        return picker(robot).lookupAll(".button").stream().map(Button.class::cast)
                .filter(b -> accesible.equals(b.getAccessibleText())).findFirst().orElseThrow();
    }

    private static String nombre(RonaldmichaTrabajadorResumen t) {
        return t.apellidos();
    }

    private static void esperarVistaCon(FxRobot robot, String... alguno) throws TimeoutException {
        esperar(() -> {
            String hoja = String.join("\n", textos(enVentana(robot).lookup(".contenedor-hoja").query()));
            for (String t : alguno) {
                if (hoja.contains(t)) {
                    return true;
                }
            }
            return false;
        });
    }

    private static void esperar(Callable<Boolean> condicion) throws TimeoutException {
        WaitForAsyncUtils.waitFor(ESPERA_MS, TimeUnit.MILLISECONDS, condicion);
    }

    private static List<String> textos(Node nodo) {
        List<String> lista = new ArrayList<>();
        if (nodo instanceof Labeled l && l.getText() != null) {
            lista.add(l.getText());
        }
        if (nodo instanceof Labeled l && l.getGraphic() != null) {
            lista.addAll(textos(l.getGraphic()));
        }
        if (nodo instanceof javafx.scene.text.Text t && t.getText() != null) {
            lista.add(t.getText());
        }
        if (nodo instanceof Parent p) {
            for (Node hijo : p.getChildrenUnmodifiable()) {
                lista.addAll(textos(hijo));
            }
        }
        return lista;
    }
}
