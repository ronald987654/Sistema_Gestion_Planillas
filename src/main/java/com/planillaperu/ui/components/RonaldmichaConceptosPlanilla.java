package com.planillaperu.ui.components;

import com.planillaperu.calc.RonaldmichaResultadoPlanilla;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla.Concepto;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla.TipoConcepto;
import com.planillaperu.common.RonaldmichaFormatos;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;

public class RonaldmichaConceptosPlanilla extends VBox {

    private final VBox ingresos = columna();
    private final VBox descuentos = columna();
    private final VBox aportes = columna();
    private final Label neto = new Label("—");

    public RonaldmichaConceptosPlanilla() {
        super(12);
        getStyleClass().add("conceptos-planilla");
        HBox columnas = new HBox(12, ingresos, descuentos, aportes);
        Label tituloNeto = new Label("NETO A PAGAR");
        tituloNeto.getStyleClass().add("neto-titulo");
        neto.getStyleClass().add("neto-valor");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox cajaNeto = new HBox(12, tituloNeto, espacio, neto);
        cajaNeto.setAlignment(Pos.CENTER_LEFT);
        cajaNeto.getStyleClass().add("neto-caja");
        getChildren().addAll(columnas, cajaNeto);
    }

    public void mostrar(RonaldmichaResultadoPlanilla r) {
        llenar(ingresos, "Ingresos", r.conceptos().stream()
                .filter(c -> c.tipo() == TipoConcepto.INGRESO || c.tipo() == TipoConcepto.REBAJA).toList(),
                r.totalIngresos(), "Total ingresos");
        llenar(descuentos, "Descuentos", delTipo(r, TipoConcepto.DESCUENTO), r.totalDescuentos(), "Total descuentos");
        llenar(aportes, "Aportes del empleador", delTipo(r, TipoConcepto.APORTE), r.totalAportes(), "Total aportes");
        neto.setText(RonaldmichaFormatos.moneda(r.netoAPagar()));
    }

    public String netoTexto() {
        return neto.getText();
    }

    private static List<Concepto> delTipo(RonaldmichaResultadoPlanilla r, TipoConcepto tipo) {
        return r.conceptos().stream().filter(c -> c.tipo() == tipo).toList();
    }

    private static void llenar(VBox columna, String titulo, List<Concepto> conceptos, BigDecimal total,
                               String etiquetaTotal) {
        Label encabezado = new Label(titulo);
        encabezado.getStyleClass().add("title-md");
        columna.getChildren().setAll(encabezado);
        if (conceptos.isEmpty()) {
            Label vacio = new Label("Sin conceptos");
            vacio.getStyleClass().add("text-muted");
            columna.getChildren().add(vacio);
        }
        for (Concepto c : conceptos) {
            BigDecimal monto = c.tipo() == TipoConcepto.REBAJA ? c.monto().negate() : c.monto();
            columna.getChildren().add(linea(c.codigoPlame(), c.descripcion(), monto, "concepto-linea"));
        }
        Region espacio = new Region();
        VBox.setVgrow(espacio, Priority.ALWAYS);
        columna.getChildren().addAll(espacio, linea(null, etiquetaTotal, total, "concepto-total"));
    }

    private static HBox linea(String codigo, String descripcion, BigDecimal monto, String estilo) {
        HBox fila = new HBox(8);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add(estilo);
        if (codigo != null) {
            Label cod = new Label(codigo);
            cod.getStyleClass().add("concepto-codigo");
            cod.setMinWidth(Region.USE_PREF_SIZE);
            fila.getChildren().add(cod);
        }
        Label desc = new Label(descripcion);
        desc.setWrapText(true);
        HBox.setHgrow(desc, Priority.ALWAYS);
        desc.setMaxWidth(Double.MAX_VALUE);
        Label valor = new Label(RonaldmichaFormatos.numero(monto));
        valor.getStyleClass().add("num");
        valor.setMinWidth(Region.USE_PREF_SIZE);
        fila.getChildren().addAll(desc, valor);
        return fila;
    }

    private static VBox columna() {
        VBox v = new VBox(6);
        v.getStyleClass().add("columna-conceptos");
        HBox.setHgrow(v, Priority.ALWAYS);
        v.setMaxWidth(Double.MAX_VALUE);
        v.setPrefWidth(300);
        return v;
    }
}
