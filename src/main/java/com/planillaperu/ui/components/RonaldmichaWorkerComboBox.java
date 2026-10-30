package com.planillaperu.ui.components;

import com.planillaperu.common.RonaldmichaBusqueda;
import com.planillaperu.domain.RonaldmichaTrabajadorResumen;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.List;

public class RonaldmichaWorkerComboBox extends ComboBox<RonaldmichaTrabajadorResumen> {

    private final ObservableList<RonaldmichaTrabajadorResumen> todos = FXCollections.observableArrayList();
    private final FilteredList<RonaldmichaTrabajadorResumen> filtrados = new FilteredList<>(todos);

    public RonaldmichaWorkerComboBox() {
        setEditable(true);
        setItems(filtrados);
        setVisibleRowCount(10);
        setMaxWidth(Double.MAX_VALUE);
        setConverter(new StringConverter<>() {
            @Override
            public String toString(RonaldmichaTrabajadorResumen t) {
                return t == null ? "" : t.etiqueta();
            }

            @Override
            public RonaldmichaTrabajadorResumen fromString(String texto) {
                return resolver(texto);
            }
        });

        getEditor().textProperty().addListener((o, antes, texto) -> {
            RonaldmichaTrabajadorResumen actual = getValue();
            if (actual != null && actual.etiqueta().equals(texto)) {
                return;
            }
            Platform.runLater(() -> {
                filtrados.setPredicate(t -> RonaldmichaBusqueda.coincide(t.etiqueta() + " " + t.codigo(), texto));
                if (getEditor().isFocused() && !filtrados.isEmpty() && !isShowing()) {
                    show();
                }
            });
        });
        valueProperty().addListener((o, antes, nuevo) -> Platform.runLater(() -> filtrados.setPredicate(null)));
        getEditor().focusedProperty().addListener((o, antes, enfocado) -> {
            if (enfocado) {
                Platform.runLater(getEditor()::selectAll);
            } else {
                getEditor().setText(getConverter().toString(getValue()));
            }
        });
    }

    public void setTrabajadores(List<RonaldmichaTrabajadorResumen> trabajadores) {
        todos.setAll(trabajadores);
    }

    public List<RonaldmichaTrabajadorResumen> getTrabajadores() {
        return todos;
    }

    public void seleccionarPorId(Long id) {
        setValue(id == null ? null : todos.stream().filter(t -> t.id() == id).findFirst().orElse(null));
    }

    private RonaldmichaTrabajadorResumen resolver(String texto) {
        if (texto == null || texto.isBlank()) {
            return getValue();
        }
        for (RonaldmichaTrabajadorResumen t : todos) {
            if (t.etiqueta().equals(texto)) {
                return t;
            }
        }
        return todos.stream()
                .filter(t -> RonaldmichaBusqueda.coincide(t.etiqueta() + " " + t.codigo(), texto))
                .findFirst()
                .orElse(getValue());
    }
}
