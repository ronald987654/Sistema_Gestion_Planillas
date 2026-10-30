package com.planillaperu.ui.components;

import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.export.doc.RonaldmichaDocumentModel;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Bloque;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Columna;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Firma;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Firmas;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Heading;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Item;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.KeyValue;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Table;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Total;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Tri;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RonaldmichaVistaDocumento extends VBox {

    private static final int FILAS_MAXIMAS = 200;

    public static final double ANCHO_MAXIMO = 820;
    private final DoubleProperty escalaBoleta = new SimpleDoubleProperty(1);
    private static final int COLUMNAS_ANCHA = 8;

    public RonaldmichaVistaDocumento() {
        super(10);
        parentProperty().addListener((o, antes, padre) -> {
            escalaBoleta.unbind();
            if (padre instanceof Region r) {
                escalaBoleta.bind(Bindings.createDoubleBinding(
                        () -> Math.max(0.4, Math.min(1, (r.getWidth() - 8) / RonaldmichaVistaBoleta.ANCHO)), r.widthProperty()));
            }
        });
        getStyleClass().add("hoja-documento");
        setMaxWidth(ANCHO_MAXIMO);
        setFillWidth(true);
    }

    public void mostrar(RonaldmichaDocumentModel d, RonaldmichaEmpresa e, Optional<String> aviso) {
        getChildren().clear();
        if (d.esBoleta()) {
            getStyleClass().setAll("hojas-boleta");
            setMinWidth(0);
            setSpacing(18);
            setMaxWidth(RonaldmichaVistaBoleta.ANCHO);
            for (Node hoja : RonaldmichaVistaBoleta.hojas(d, e)) {
                hoja.scaleXProperty().bind(escalaBoleta);
                hoja.scaleYProperty().bind(escalaBoleta);
                getChildren().add(new Group(hoja));
            }
            return;
        }
        getStyleClass().setAll("hoja-documento");
        setSpacing(10);
        setMinWidth(USE_COMPUTED_SIZE);
        setMaxWidth(ANCHO_MAXIMO);
        getChildren().add(encabezado(e));
        aviso.ifPresent(a -> {
            Label l = new Label("Aviso: " + a);
            l.getStyleClass().addAll("alert", "warning", "documento-aviso");
            l.setWrapText(true);
            l.setMaxWidth(Double.MAX_VALUE);
            getChildren().add(l);
        });
        if (!d.copias().isEmpty()) {
            Label rotulo = new Label(d.copias().getFirst().rotulo());
            rotulo.getStyleClass().add("documento-rotulo");
            rotulo.setMaxWidth(Double.MAX_VALUE);
            rotulo.setAlignment(Pos.CENTER_RIGHT);
            getChildren().add(rotulo);
        }
        Label titulo = new Label(d.titulo());
        titulo.getStyleClass().add("documento-titulo");
        titulo.setWrapText(true);
        titulo.setMaxWidth(Double.MAX_VALUE);
        titulo.setAlignment(Pos.CENTER);
        titulo.setTextAlignment(TextAlignment.CENTER);
        getChildren().add(titulo);
        if (d.subtitulo() != null && !d.subtitulo().isBlank()) {
            Label sub = new Label(d.subtitulo());
            sub.getStyleClass().addAll("text-muted", "documento-subtitulo");
            sub.setWrapText(true);
            sub.setMaxWidth(Double.MAX_VALUE);
            sub.setAlignment(Pos.CENTER);
            sub.setTextAlignment(TextAlignment.CENTER);
            getChildren().add(sub);
        }
        for (Bloque b : d.bloques()) {
            getChildren().add(bloque(b));
        }
    }

    private static VBox encabezado(RonaldmichaEmpresa e) {
        VBox datos = new VBox(2);
        datos.setAlignment(Pos.CENTER_RIGHT);
        Label razon = new Label(texto(e.razonSocial(), "Empresa sin registrar"));
        razon.getStyleClass().add("documento-empresa");
        Label ruc = new Label("RUC " + texto(e.ruc(), "—"));
        ruc.getStyleClass().add("documento-empresa-dato");
        datos.getChildren().addAll(razon, ruc);
        String domicilio = Stream.of(e.direccion(), e.distrito(), e.provincia(), e.departamento())
                .filter(x -> x != null && !x.isBlank()).collect(Collectors.joining(" · "));
        if (!domicilio.isEmpty()) {
            Label dir = new Label(domicilio);
            dir.getStyleClass().add("documento-empresa-dato");
            dir.setWrapText(true);
            dir.setTextAlignment(TextAlignment.RIGHT);
            datos.getChildren().add(dir);
        }
        HBox.setHgrow(datos, Priority.ALWAYS);
        datos.setMaxWidth(Double.MAX_VALUE);
        HBox fila = new HBox(12);
        fila.setAlignment(Pos.CENTER_LEFT);
        if (e.logo() != null && e.logo().length > 0) {
            Image img = new Image(new ByteArrayInputStream(e.logo()));
            if (!img.isError()) {
                ImageView logo = new ImageView(img);
                logo.setFitHeight(44);
                logo.setPreserveRatio(true);
                fila.getChildren().add(logo);
            }
        }
        fila.getChildren().add(datos);
        Region linea = new Region();
        linea.getStyleClass().add("documento-linea");
        linea.setMaxWidth(Double.MAX_VALUE);
        VBox caja = new VBox(8, fila, linea);
        caja.getStyleClass().add("documento-encabezado-empresa");
        return caja;
    }

    private static String texto(String valor, String siVacio) {
        return valor == null || valor.isBlank() ? siVacio : valor;
    }

    public void mostrarError(String mensaje) {
        getChildren().clear();
        getStyleClass().setAll("hoja-documento");
        setMaxWidth(ANCHO_MAXIMO);
        Label l = new Label(mensaje);
        l.getStyleClass().addAll("alert", "warning", "documento-error");
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        getChildren().add(l);
    }

    private static Node bloque(Bloque b) {
        return switch (b) {
            case Heading h -> {
                Label l = new Label(h.texto());
                l.getStyleClass().add("documento-seccion");
                yield l;
            }
            case RonaldmichaDocumentModel.Paragraph p -> {
                Label l = new Label(p.texto());
                l.setWrapText(true);
                yield l;
            }
            case KeyValue kv -> claveValor(kv);
            case Table t -> tabla(t);
            case Tri tri -> {
                HBox caja = new HBox(10);
                for (Columna c : tri.columnas()) {
                    caja.getChildren().add(columna(c));
                }
                yield caja;
            }
            case Total t -> {
                Label etiqueta = new Label(t.etiqueta());
                etiqueta.getStyleClass().add("neto-titulo");
                Label monto = new Label(RonaldmichaFormatos.moneda(t.monto()));
                monto.getStyleClass().add("neto-valor");
                Region espacio = new Region();
                HBox.setHgrow(espacio, Priority.ALWAYS);
                HBox fila = new HBox(12, etiqueta, espacio, monto);
                fila.setAlignment(Pos.CENTER_LEFT);
                VBox caja = new VBox(4, fila);
                if (t.enLetras() != null) {
                    Label letras = new Label("Son: " + t.enLetras());
                    letras.getStyleClass().add("neto-letras");
                    letras.setWrapText(true);
                    caja.getChildren().add(letras);
                }
                caja.getStyleClass().add("neto-caja");
                yield caja;
            }
            case RonaldmichaDocumentModel.Boleta x -> throw new IllegalStateException("La boleta se muestra con VistaBoleta");
            case Firmas f -> {
                HBox caja = new HBox(40);
                caja.setAlignment(Pos.CENTER);
                caja.getStyleClass().add("documento-firmas");
                for (Firma firma : f.firmas()) {
                    Region linea = new Region();
                    linea.getStyleClass().add("documento-firma-linea");
                    linea.setMaxWidth(Double.MAX_VALUE);
                    Label nombre = new Label(firma.nombre());
                    nombre.getStyleClass().add("documento-firma-nombre");
                    nombre.setWrapText(true);
                    VBox bloque = new VBox(2, linea, nombre);
                    if (firma.detalle() != null) {
                        Label detalle = new Label(firma.detalle());
                        detalle.getStyleClass().add("documento-firma");
                        detalle.setWrapText(true);
                        detalle.setTextAlignment(TextAlignment.CENTER);
                        bloque.getChildren().add(detalle);
                    }
                    bloque.setAlignment(Pos.TOP_CENTER);
                    bloque.setPrefWidth(240);
                    bloque.setMaxWidth(260);
                    caja.getChildren().add(bloque);
                }
                yield caja;
            }
        };
    }

    private static GridPane claveValor(KeyValue kv) {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(4);
        for (int i = 0; i < kv.pares().size(); i++) {
            Label etiqueta = new Label(kv.pares().get(i).etiqueta());
            etiqueta.getStyleClass().add("celda-detalle");
            Label valor = new Label(kv.pares().get(i).valor());
            valor.setWrapText(true);
            g.add(etiqueta, (i % 2) * 2, i / 2);
            g.add(valor, (i % 2) * 2 + 1, i / 2);
        }
        ColumnConstraints e = new ColumnConstraints();
        e.setPercentWidth(18);
        ColumnConstraints v = new ColumnConstraints();
        v.setPercentWidth(32);
        g.getColumnConstraints().setAll(e, v, e, v);
        return g;
    }

    private static GridPane tabla(Table t) {
        GridPane g = new GridPane();
        g.getStyleClass().add("documento-tabla");
        if (t.encabezados().size() > COLUMNAS_ANCHA) {
            g.getStyleClass().add("documento-tabla-ancha");
        }
        int columnas = t.encabezados().size();
        for (int i = 0; i < columnas; i++) {
            g.add(celda(t.encabezados().get(i), "documento-encabezado", t.numerica(i)), i, 0);
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.SOMETIMES);
            cc.setHalignment(t.numerica(i) ? HPos.RIGHT : HPos.LEFT);
            g.getColumnConstraints().add(cc);
        }
        List<List<String>> filas = t.filas();
        int mostradas = Math.min(filas.size(), FILAS_MAXIMAS);
        for (int f = 0; f < mostradas; f++) {
            for (int i = 0; i < columnas; i++) {
                List<String> fila = filas.get(f);
                g.add(celda(i < fila.size() ? fila.get(i) : "", null, t.numerica(i)), i, f + 1);
            }
        }
        int siguiente = mostradas + 1;
        if (filas.size() > mostradas) {
            Label mas = new Label("… y " + (filas.size() - mostradas) + " filas más en el archivo");
            mas.getStyleClass().add("text-muted");
            g.add(mas, 0, siguiente++, columnas, 1);
        }
        if (!t.pie().isEmpty()) {
            for (int i = 0; i < columnas; i++) {
                g.add(celda(i < t.pie().size() ? t.pie().get(i) : "", "documento-pie", t.numerica(i)), i, siguiente);
            }
        }
        return g;
    }

    private static Label celda(String texto, String estilo, boolean numerica) {
        Label l = new Label(texto);
        l.getStyleClass().add("documento-celda");
        if (estilo != null) {
            l.getStyleClass().add(estilo);
        }
        if (numerica) {
            l.getStyleClass().add("num");
        }
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private static VBox columna(Columna c) {
        Label titulo = new Label(c.titulo());
        titulo.getStyleClass().add("documento-seccion");
        VBox caja = new VBox(4, titulo);
        caja.getStyleClass().add("columna-conceptos");
        HBox.setHgrow(caja, Priority.ALWAYS);
        caja.setMaxWidth(Double.MAX_VALUE);
        caja.setPrefWidth(260);
        for (Item it : c.items()) {
            caja.getChildren().add(linea(it.codigo(), it.concepto(), it.monto(), "concepto-linea"));
        }
        caja.getChildren().add(linea(null, "Total", c.total(), "concepto-total"));
        return caja;
    }

    private static HBox linea(String codigo, String concepto, BigDecimal monto, String estilo) {
        HBox fila = new HBox(6);
        fila.getStyleClass().add(estilo);
        if (codigo != null) {
            Label cod = new Label(codigo);
            cod.getStyleClass().add("concepto-codigo");
            cod.setMinWidth(Region.USE_PREF_SIZE);
            fila.getChildren().add(cod);
        }
        Label desc = new Label(concepto);
        desc.setWrapText(true);
        HBox.setHgrow(desc, Priority.ALWAYS);
        desc.setMaxWidth(Double.MAX_VALUE);
        Label valor = new Label(RonaldmichaFormatos.numero(monto));
        valor.getStyleClass().add("num");
        valor.setMinWidth(Region.USE_PREF_SIZE);
        fila.getChildren().addAll(desc, valor);
        return fila;
    }
}
