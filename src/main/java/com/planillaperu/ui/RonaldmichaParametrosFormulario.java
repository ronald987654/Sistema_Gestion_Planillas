package com.planillaperu.ui;

import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaAfpComision;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

final class RonaldmichaParametrosFormulario extends VBox {

    private final ComboBox<Month> mes = new ComboBox<>();
    private final ComboBox<Integer> anio = new ComboBox<>();
    private final TextField norma = new TextField();
    private final TextField rmv = numero();
    private final TextField uit = numero();
    private final TextField rmaPrima = numero();
    private final TextField sisMonto = numero();
    private final TextField asigFamiliar = numero();
    private final TextField essalud = numero();
    private final TextField eps = numero();
    private final TextField onp = numero();
    private final TextField afpAporte = numero();
    private final TextField afpPrima = numero();
    private final TextField bonifExt = numero();
    private final TextField bonifExtEps = numero();
    private final TextField vidaLey = numero();
    private final Map<String, TextField[]> comisiones = new LinkedHashMap<>();

    RonaldmichaParametrosFormulario(RonaldmichaParametrosVersion base, YearMonth desde) {
        setSpacing(16);

        mes.getItems().setAll(Month.values());
        mes.setConverter(new StringConverter<>() {
            @Override
            public String toString(Month m) {
                if (m == null) {
                    return "";
                }
                String n = m.getDisplayName(TextStyle.FULL_STANDALONE, RonaldmichaFormatos.LOCALE_PE);
                return n.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + n.substring(1);
            }

            @Override
            public Month fromString(String s) {
                return null;
            }
        });
        anio.getItems().setAll(IntStream.rangeClosed(desde.getYear() - 2, desde.getYear() + 2).boxed().toList());
        mes.setValue(desde.getMonth());
        anio.setValue(desde.getYear());
        mes.setMaxWidth(Double.MAX_VALUE);
        anio.setMaxWidth(Double.MAX_VALUE);
        norma.setPromptText("Norma que sustenta el cambio");

        GridPane vigencia = grilla(3);
        vigencia.add(campo("Vigente desde (mes) *", mes), 0, 0);
        vigencia.add(campo("Año *", anio), 1, 0);
        vigencia.add(campo("Norma", norma), 2, 0);

        GridPane montos = grilla(4);
        montos.add(campo("RMV (S/) *", rmv), 0, 0);
        montos.add(campo("UIT (S/) *", uit), 1, 0);
        montos.add(campo("Rem. máx. asegurable (S/) *", rmaPrima), 2, 0);
        montos.add(campo("SIS (S/) *", sisMonto), 3, 0);

        GridPane tasas = grilla(4);
        tasas.add(campo("Asignación familiar %", asigFamiliar), 0, 0);
        tasas.add(campo("EsSalud %", essalud), 1, 0);
        tasas.add(campo("EPS %", eps), 2, 0);
        tasas.add(campo("ONP %", onp), 3, 0);
        tasas.add(campo("Aporte AFP %", afpAporte), 0, 1);
        tasas.add(campo("Prima seguros AFP %", afpPrima), 1, 1);
        tasas.add(campo("Bonif. extraordinaria %", bonifExt), 2, 1);
        tasas.add(campo("Bonif. extr. con EPS %", bonifExtEps), 3, 1);
        tasas.add(campo("Vida Ley %", vidaLey), 0, 2);

        GridPane afps = grilla(3);
        afps.add(etiqueta("AFP"), 0, 0);
        afps.add(etiqueta("Comisión flujo %"), 1, 0);
        afps.add(etiqueta("Comisión mixta %"), 2, 0);
        int fila = 1;
        for (String afp : RonaldmichaParametrosVersion.AFPS) {
            TextField flujo = numero();
            TextField mixta = numero();
            comisiones.put(afp, new TextField[]{flujo, mixta});
            afps.add(new Label(afp), 0, fila);
            afps.add(flujo, 1, fila);
            afps.add(mixta, 2, fila);
            fila++;
        }

        getChildren().addAll(vigencia, seccion("Montos"), montos, seccion("Tasas"), tasas,
                seccion("Comisiones AFP"), afps);
        if (base != null) {
            copiar(base);
        }
    }

    void alCambiar(Runnable accion) {
        ChangeListener<Object> l = (o, a, b) -> accion.run();
        mes.valueProperty().addListener(l);
        anio.valueProperty().addListener(l);
        norma.textProperty().addListener(l);
        for (TextField t : todosLosNumeros()) {
            t.textProperty().addListener(l);
        }
    }

    RonaldmichaParametrosVersion leer() {
        YearMonth desde = mes.getValue() == null || anio.getValue() == null
                ? null : YearMonth.of(anio.getValue(), mes.getValue());
        List<RonaldmichaAfpComision> lista = new ArrayList<>();
        comisiones.forEach((afp, campos) -> lista.add(new RonaldmichaAfpComision(afp, dec(campos[0]), dec(campos[1]))));
        return new RonaldmichaParametrosVersion(null, desde, norma.getText(), dec(rmv), dec(uit), dec(asigFamiliar),
                dec(essalud), dec(eps), dec(onp), dec(afpAporte), dec(afpPrima), dec(rmaPrima), dec(bonifExt),
                dec(bonifExtEps), dec(vidaLey), dec(sisMonto), null, null, null,
                lista.stream().filter(c -> c.comisionFlujoPct() != null && c.comisionMixtaPct() != null).toList(),
                null, null, null);
    }

    private void copiar(RonaldmichaParametrosVersion v) {
        rmv.setText(v.rmv().toPlainString());
        uit.setText(v.uit().toPlainString());
        rmaPrima.setText(v.rmaPrima().toPlainString());
        sisMonto.setText(v.sisMonto().toPlainString());
        asigFamiliar.setText(v.asigFamiliarPct().toPlainString());
        essalud.setText(v.essaludPct().toPlainString());
        eps.setText(v.epsPct().toPlainString());
        onp.setText(v.onpPct().toPlainString());
        afpAporte.setText(v.afpAportePct().toPlainString());
        afpPrima.setText(v.afpPrimaPct().toPlainString());
        bonifExt.setText(v.bonifExtPct().toPlainString());
        bonifExtEps.setText(v.bonifExtEpsPct().toPlainString());
        vidaLey.setText(v.vidaLeyPct().toPlainString());
        for (RonaldmichaAfpComision c : v.comisiones()) {
            TextField[] campos = comisiones.get(c.afp());
            if (campos != null) {
                campos[0].setText(c.comisionFlujoPct().toPlainString());
                campos[1].setText(c.comisionMixtaPct().toPlainString());
            }
        }
    }

    private List<TextField> todosLosNumeros() {
        List<TextField> campos = new ArrayList<>(List.of(rmv, uit, rmaPrima, sisMonto, asigFamiliar, essalud, eps,
                onp, afpAporte, afpPrima, bonifExt, bonifExtEps, vidaLey));
        comisiones.values().forEach(par -> campos.addAll(List.of(par)));
        return campos;
    }

    private static BigDecimal dec(TextField campo) {
        String t = campo.getText() == null ? "" : campo.getText().replace(",", "").trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static TextField numero() {
        TextField t = new TextField();
        t.getStyleClass().add("num");
        return t;
    }

    private static GridPane grilla(int columnas) {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        for (int i = 0; i < columnas; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / columnas);
            g.getColumnConstraints().add(c);
        }
        return g;
    }

    private static Node campo(String texto, Node control) {
        return new VBox(6, etiqueta(texto), control);
    }

    private static Label etiqueta(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("field-label");
        return l;
    }

    private static Node seccion(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("title-md");
        return new HBox(l);
    }
}
