package com.planillaperu.ui.components;

import com.planillaperu.common.RonaldmichaBusqueda;
import com.planillaperu.domain.RonaldmichaTrabajadorResumen;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RonaldmichaWorkerPicker extends VBox {

    private static final double ALTO_FILA = 36;
    private static final int FILAS_VISIBLES = 8;

    private final ObservableList<RonaldmichaTrabajadorResumen> master = FXCollections.observableArrayList();
    private final FilteredList<RonaldmichaTrabajadorResumen> filtrados = new FilteredList<>(master, t -> true);
    private final ObjectProperty<RonaldmichaTrabajadorResumen> selected = new SimpleObjectProperty<>(this, "selected");
    private final Button campo = new Button();
    private final Label textoCampo = new Label();
    private final Button anterior = new Button("‹");
    private final Button siguiente = new Button("›");
    private final Label info = new Label();
    private final Popup popup = new Popup();
    private final TextField buscar = new TextField();
    private final ListView<RonaldmichaTrabajadorResumen> lista = new ListView<>(filtrados);
    private final VBox caja = new VBox(6, buscar, lista);

    public RonaldmichaWorkerPicker() {
        getStyleClass().add("worker-picker");
        setSpacing(6);

        Label flecha = new Label("▾");
        flecha.getStyleClass().add("worker-picker-arrow");
        textoCampo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textoCampo, Priority.ALWAYS);
        HBox contenidoCampo = new HBox(8, textoCampo, flecha);
        contenidoCampo.setAlignment(Pos.CENTER_LEFT);
        campo.setGraphic(contenidoCampo);
        campo.getStyleClass().add("worker-picker-field");
        campo.setMaxWidth(Double.MAX_VALUE);
        contenidoCampo.prefWidthProperty().bind(campo.widthProperty().subtract(26));
        HBox.setHgrow(campo, Priority.ALWAYS);

        for (Button b : List.of(anterior, siguiente)) {
            b.getStyleClass().addAll("btn", "btn-icon", "worker-picker-nav");
            b.setMinWidth(Region.USE_PREF_SIZE);
        }
        anterior.setAccessibleText("Trabajador anterior");
        siguiente.setAccessibleText("Trabajador siguiente");
        HBox fila = new HBox(6, anterior, campo, siguiente);
        fila.setAlignment(Pos.CENTER_LEFT);
        info.getStyleClass().add("worker-picker-info");
        getChildren().addAll(fila, info);

        buscar.setPromptText("Buscar por apellidos, nombres o DNI");
        lista.getStyleClass().add("worker-picker-list");
        lista.setFixedCellSize(ALTO_FILA);
        lista.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(1, Math.min(filtrados.size(), FILAS_VISIBLES)) * ALTO_FILA + 2, filtrados));
        lista.setCellFactory(l -> new Celda());
        lista.setPlaceholder(new Label("Sin coincidencias"));
        caja.getStyleClass().add("worker-picker-popup");
        caja.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #DDE2DD; -fx-border-radius: 8;"
                + " -fx-background-radius: 8; -fx-padding: 8;"
                + " -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.14), 16, 0, 0, 4);");
        popup.getContent().add(caja);

        campo.setOnAction(e -> abrir());
        anterior.setOnAction(e -> mover(-1));
        siguiente.setOnAction(e -> mover(+1));
        buscar.textProperty().addListener((o, a, b) -> {
            filtrados.setPredicate(t -> RonaldmichaBusqueda.coincide(etiqueta(t) + " " + t.codigo(), b));
            if (!filtrados.isEmpty()) {
                lista.getSelectionModel().selectFirst();
            }
        });
        buscar.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case DOWN -> {
                    lista.requestFocus();
                    lista.getSelectionModel().selectNext();
                    e.consume();
                }
                case ENTER -> {
                    elegirResaltado();
                    e.consume();
                }
                case ESCAPE -> popup.hide();
                default -> {
                }
            }
        });
        lista.setOnMouseClicked(e -> elegirResaltado());
        lista.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                elegirResaltado();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
            }
        });
        popup.setAutoHide(true);
        popup.setOnHidden(e -> buscar.clear());
        actualizarTexto();
    }

    private void abrir() {
        if (master.isEmpty() || getScene() == null) {
            return;
        }
        buscar.clear();
        filtrados.setPredicate(t -> true);
        RonaldmichaTrabajadorResumen actual = selected.get();
        if (actual == null) {
            lista.getSelectionModel().selectFirst();
        } else {
            lista.getSelectionModel().select(actual);
            lista.scrollTo(actual);
        }
        caja.getStylesheets().setAll(getScene().getStylesheets());
        caja.setPrefWidth(campo.getWidth());
        Bounds b = campo.localToScreen(campo.getBoundsInLocal());
        popup.show(campo, b.getMinX(), b.getMaxY() + 4);
        buscar.requestFocus();
    }

    private void elegirResaltado() {
        RonaldmichaTrabajadorResumen t = lista.getSelectionModel().getSelectedItem();
        if (t != null) {
            seleccionar(t);
        }
    }

    public void seleccionar(RonaldmichaTrabajadorResumen t) {
        popup.hide();
        selected.set(t);
        actualizarTexto();
    }

    private void mover(int d) {
        if (master.isEmpty()) {
            return;
        }
        int i = Math.max(0, master.indexOf(selected.get()));
        seleccionar(master.get(Math.floorMod(i + d, master.size())));
    }

    public void setPool(List<RonaldmichaTrabajadorResumen> pool) {
        RonaldmichaTrabajadorResumen previo = selected.get();
        master.setAll(pool);
        boolean vacio = pool.isEmpty();
        campo.setDisable(vacio);
        anterior.setDisable(vacio);
        siguiente.setDisable(vacio);
        if (vacio) {
            selected.set(null);
            actualizarTexto();
            return;
        }
        RonaldmichaTrabajadorResumen mismo = previo == null ? null
                : pool.stream().filter(t -> t.id() == previo.id()).findFirst().orElse(null);
        seleccionar(mismo != null ? mismo : pool.getFirst());
    }

    public void seleccionarPorId(Long id) {
        if (id != null) {
            master.stream().filter(t -> t.id() == id).findFirst().ifPresent(this::seleccionar);
        }
    }

    public ObjectProperty<RonaldmichaTrabajadorResumen> selectedProperty() {
        return selected;
    }

    public RonaldmichaTrabajadorResumen getSelected() {
        return selected.get();
    }

    public List<RonaldmichaTrabajadorResumen> getPool() {
        return master;
    }

    private void actualizarTexto() {
        RonaldmichaTrabajadorResumen t = selected.get();
        textoCampo.setText(t == null
                ? (master.isEmpty() ? "Sin trabajadores para este reporte" : "Elegir trabajador")
                : etiqueta(t));
        campo.setAccessibleText("Trabajador: " + textoCampo.getText());
        if (t == null) {
            info.setText(master.size() + " trabajadores");
            return;
        }
        String detalle = Stream.of(t.cargo(), t.area(), t.regimen() == null ? null : t.regimen().descripcion())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" · "));
        info.setText(detalle + " · " + (master.indexOf(t) + 1) + " de " + master.size());
    }

    private static String etiqueta(RonaldmichaTrabajadorResumen t) {
        return t.apellidos() + ", " + t.nombres() + " · " + t.tipoDoc() + " " + t.numDoc();
    }

    private static final class Celda extends ListCell<RonaldmichaTrabajadorResumen> {
        private final Label nombre = new Label();
        private final Label detalle = new Label();
        private final VBox textos = new VBox(0, nombre, detalle);

        Celda() {
            getStyleClass().add("worker-picker-cell");
            nombre.getStyleClass().add("celda-nombre");
            detalle.getStyleClass().add("celda-detalle");
            nombre.setStyle("-fx-font-weight: bold;");
            detalle.setStyle("-fx-text-fill: #56625C; -fx-font-size: 11.5px;");
            textos.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(RonaldmichaTrabajadorResumen t, boolean vacio) {
            super.updateItem(t, vacio);
            setText(null);
            if (vacio || t == null) {
                setGraphic(null);
                return;
            }
            nombre.setText(t.apellidos() + ", " + t.nombres());
            detalle.setText(t.tipoDoc() + " " + t.numDoc() + (t.cargo() == null || t.cargo().isBlank() ? "" : " · " + t.cargo()));
            setGraphic(textos);
        }
    }
}
