package com.planillaperu.ui;

import com.planillaperu.common.RonaldmichaFormatos;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

public final class RonaldmichaEstilos {

    private static final String MARCA = "estilos.mayusculas";
    private static final String AUTO_ALTO = "estilos.autoAlto";
    private static final int FILAS_MAXIMAS = 15;
    private static final int FILAS_VACIA = 4;
    private static final double ALTO_FILA = 40;

    private RonaldmichaEstilos() {
    }

    public static void aplicar(Node raiz) {
        encabezadosEnMayusculas(raiz);
        altoSegunContenido(raiz);
        anchoSegunContenido(raiz);
    }

    private static final double ANCHO_MAXIMO_COLUMNA = 300;

    public static void anchoSegunContenido(Node raiz) {
        if (raiz == null) {
            return;
        }
        for (Node n : raiz.lookupAll(".table-view")) {
            if (!(n instanceof TableView<?> t) || t.getStyleClass().contains("ancho-libre")) {
                continue;
            }
            java.util.Map<TableColumnBase<?, ?>, Double> anchos = new java.util.HashMap<>();
            for (Node c : t.lookupAll(".table-cell")) {
                if (c instanceof javafx.scene.control.TableCell<?, ?> celda && !celda.isEmpty()
                        && celda.getTableColumn() != null && celda.getTableRow() != null && !celda.getTableRow().isEmpty()) {
                    anchos.merge(celda.getTableColumn(), anchoContenido(celda), Math::max);
                }
            }
            for (Node h : t.lookupAll(".column-header")) {
                if (h instanceof javafx.scene.control.skin.TableColumnHeader encabezado
                        && encabezado.getTableColumn() != null && encabezado.getTableColumn().getColumns().isEmpty()) {
                    Node etiqueta = encabezado.lookup(".label");
                    double ancho = etiqueta instanceof Region r ? r.prefWidth(-1) + 24 : 0;
                    anchos.merge(encabezado.getTableColumn(), ancho, Math::max);
                }
            }
            anchos.forEach((columna, ancho) -> {
                double minimo = Math.ceil(Math.min(ancho, ANCHO_MAXIMO_COLUMNA));
                if (minimo > columna.getMinWidth() + 0.5) {
                    columna.setMinWidth(minimo);
                    if (columna.getPrefWidth() < minimo) {
                        columna.setPrefWidth(minimo);
                    }
                }
            });
            permitirScrollHorizontal(t);
        }
    }

    private static final String POLITICA_ORIGINAL = "estilos.politica";

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void permitirScrollHorizontal(TableView t) {
        if (t.getWidth() <= 0) {
            return;
        }
        double minimos = 0;
        for (Object c : t.getVisibleLeafColumns()) {
            minimos += ((TableColumnBase<?, ?>) c).getMinWidth();
        }
        double disponible = t.getWidth() - t.snappedLeftInset() - t.snappedRightInset() - 14;
        Object original = t.getProperties().get(POLITICA_ORIGINAL);
        boolean ajustada = t.getColumnResizePolicy() != TableView.UNCONSTRAINED_RESIZE_POLICY;
        if (ajustada && minimos > disponible) {
            t.getProperties().put(POLITICA_ORIGINAL, t.getColumnResizePolicy());
            t.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        } else if (!ajustada && original != null && minimos <= disponible) {
            t.getProperties().remove(POLITICA_ORIGINAL);
            t.setColumnResizePolicy((javafx.util.Callback) original);
        }
    }

    public static void altoSegunContenido(Node raiz) {
        if (raiz == null) {
            return;
        }
        for (Node n : raiz.lookupAll(".table-view")) {
            if (n instanceof TableView<?> t) {
                ajustarAlto(t);
            }
        }
    }

    private static void ajustarAlto(TableView<?> t) {
        Object marca = t.getProperties().get(AUTO_ALTO);
        if (marca == null) {
            boolean auto = t.getPrefHeight() == Region.USE_COMPUTED_SIZE && !t.getStyleClass().contains("alto-fijo");
            t.getProperties().put(AUTO_ALTO, auto);
            if (!auto) {
                return;
            }
            t.setMaxHeight(Region.USE_PREF_SIZE);
            VBox.setVgrow(t, Priority.NEVER);
        } else if (!Boolean.TRUE.equals(marca)) {
            return;
        }
        double encabezado = alto(t.lookup(".column-header-background"), ALTO_FILA);
        double suma = 0;
        int dibujadas = 0;
        for (Node n : t.lookupAll(".table-row-cell")) {
            if (n instanceof TableRow<?> r && !r.isEmpty() && r.getHeight() > 0 && r.isVisible()) {
                suma += r.getHeight();
                dibujadas++;
            }
        }
        double fila = dibujadas == 0 ? ALTO_FILA : suma / dibujadas;
        double barra = 0;
        for (Node n : t.lookupAll(".scroll-bar")) {
            if (n instanceof ScrollBar b && b.getOrientation() == Orientation.HORIZONTAL && b.isVisible()) {
                barra = b.getHeight();
            }
        }
        int filas = t.getItems().isEmpty() ? FILAS_VACIA : Math.min(t.getItems().size(), FILAS_MAXIMAS);
        double cuerpo = dibujadas == t.getItems().size() && dibujadas > 0 ? suma : filas * fila;
        double deseado = Math.ceil(encabezado + cuerpo + barra + t.snappedTopInset() + t.snappedBottomInset());
        if (Math.abs(t.getPrefHeight() - deseado) > 1) {
            t.setPrefHeight(deseado);
        }
    }

    private static double anchoContenido(javafx.scene.control.TableCell<?, ?> celda) {
        double ancho = celda.snappedLeftInset() + celda.snappedRightInset() + 2;
        String texto = celda.getText();
        boolean conTexto = texto != null && !texto.isEmpty();
        if (conTexto) {
            javafx.scene.text.Text medida = new javafx.scene.text.Text(texto);
            medida.setFont(celda.getFont());
            ancho += medida.getLayoutBounds().getWidth();
        }
        if (celda.getGraphic() != null) {
            ancho += celda.getGraphic().prefWidth(-1) + (conTexto ? celda.getGraphicTextGap() : 0);
        }
        return ancho;
    }

    private static double alto(Node n, double porDefecto) {
        return n instanceof Region r && r.getHeight() > 0 ? r.getHeight() : porDefecto;
    }

    public static void encabezadosEnMayusculas(Node raiz) {
        if (raiz == null) {
            return;
        }
        for (Node n : raiz.lookupAll(".table-view")) {
            if (n instanceof TableView<?> t) {
                tabla(t, t.getColumns());
            }
        }
        for (Node n : raiz.lookupAll(".tree-table-view")) {
            if (n instanceof TreeTableView<?> t) {
                tabla(t, t.getColumns());
            }
        }
    }

    private static void tabla(Node tabla, List<? extends TableColumnBase<?, ?>> columnas) {
        if (tabla.getProperties().containsKey(MARCA)) {
            return;
        }
        tabla.getProperties().put(MARCA, Boolean.TRUE);
        columnas(columnas);
        if (columnas instanceof javafx.collections.ObservableList<? extends TableColumnBase<?, ?>> lista) {
            lista.addListener((ListChangeListener<TableColumnBase<?, ?>>) c -> columnas(c.getList()));
        }
    }

    private static void columnas(List<? extends TableColumnBase<?, ?>> columnas) {
        for (TableColumnBase<?, ?> c : columnas) {
            if (!c.getProperties().containsKey(MARCA)) {
                c.getProperties().put(MARCA, Boolean.TRUE);
                c.textProperty().addListener((o, antes, ahora) -> mayusculas(c));
                if (c.getColumns() instanceof javafx.collections.ObservableList<? extends TableColumnBase<?, ?>> hijas) {
                    hijas.addListener((ListChangeListener<TableColumnBase<?, ?>>) ch -> columnas(ch.getList()));
                }
            }
            mayusculas(c);
            columnas(c.getColumns());
        }
    }

    private static void mayusculas(TableColumnBase<?, ?> c) {
        String texto = c.getText();
        if (texto != null) {
            String mayus = texto.toUpperCase(RonaldmichaFormatos.LOCALE_PE);
            if (!mayus.equals(texto)) {
                c.setText(mayus);
            }
        }
    }
}
