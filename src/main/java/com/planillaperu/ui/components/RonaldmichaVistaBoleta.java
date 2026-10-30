package com.planillaperu.ui.components;

import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.export.doc.RonaldmichaDocumentModel;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Boleta;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Campo;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Conceptos;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Copia;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.FilaDatos;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.FirmasBoleta;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.GrupoDatos;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.LineaBoleta;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RonaldmichaVistaBoleta {

    static final double ANCHO = 1060;
    private static final double ALTO = ANCHO * 210 / 297;
    private static final double ESCALA = (ANCHO - 2 * 38) / 785.0;

    private static final String FUENTE = "-fx-font-family: 'Arial', 'Helvetica', sans-serif;";
    private static final String NEGRO = "#000000";
    private static final String GRIS = "#757575";
    private static final String ROJO = "#C62828";

    private RonaldmichaVistaBoleta() {
    }

    static List<Node> hojas(RonaldmichaDocumentModel d, RonaldmichaEmpresa e) {
        List<Copia> copias = d.copias().isEmpty() ? Collections.singletonList(null) : d.copias();
        String emitida = RonaldmichaFormatos.fechaHora(LocalDateTime.now());
        List<Node> lista = new ArrayList<>();
        for (Copia copia : copias) {
            lista.add(hoja(d.boleta(), copia, e, emitida));
        }
        return lista;
    }

    private static VBox hoja(Boleta b, Copia copia, RonaldmichaEmpresa e, String emitida) {
        VBox cuerpo = new VBox(px(3));
        cuerpo.getChildren().add(encabezado(b, copia, e));
        for (FilaDatos f : b.datos()) {
            cuerpo.getChildren().add(datos(f));
        }
        Region aire = new Region();
        aire.setMinHeight(px(2));
        cuerpo.getChildren().addAll(aire, conceptos(b), totales(b));
        Label son = texto("Son: " + b.enLetras(), 8.5, true, NEGRO);
        son.setPadding(new Insets(px(2), 0, 0, 0));
        cuerpo.getChildren().add(son);
        if (b.leyendaCese() != null) {
            Label cese = texto(b.leyendaCese(), 8, false, NEGRO);
            cese.setStyle(cese.getStyle() + "-fx-font-style: italic;");
            cese.setWrapText(true);
            cuerpo.getChildren().add(cese);
        }
        Region resto = new Region();
        resto.setMinHeight(px(0.6 * 72 / 2.54));
        VBox.setVgrow(resto, Priority.ALWAYS);
        Label leyenda = texto(Boleta.leyendaFinal(emitida), 7, false, GRIS);
        leyenda.setMaxWidth(Double.MAX_VALUE);
        leyenda.setAlignment(Pos.CENTER);
        VBox hoja = new VBox(0, cuerpo, resto, firmas(b.firmas(), copia, e), leyenda);
        hoja.setPadding(new Insets(38));
        hoja.setPrefWidth(ANCHO);
        hoja.setMaxWidth(ANCHO);
        hoja.setMinHeight(ALTO);
        hoja.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 2;"
                + " -fx-effect: dropshadow(gaussian, rgba(23, 32, 27, 0.18), 18, 0, 0, 4);");
        hoja.getStyleClass().add("hoja-boleta");
        return hoja;
    }

    private static Node encabezado(Boleta b, Copia copia, RonaldmichaEmpresa e) {
        StackPane logo = new StackPane();
        logo.setAlignment(Pos.CENTER_LEFT);
        if (e.logo() != null && e.logo().length > 0) {
            Image img = new Image(new ByteArrayInputStream(e.logo()));
            if (!img.isError()) {
                ImageView vista = new ImageView(img);
                vista.setFitHeight(px(1.6 * 72 / 2.54));
                vista.setFitWidth(px(4.5 * 72 / 2.54));
                vista.setPreserveRatio(true);
                logo.getChildren().add(vista);
            }
        }
        Label titulo = texto(Boleta.TITULO, 15, true, NEGRO);
        Label norma = texto(Boleta.NORMA, 8.5, false, NEGRO);
        Label mes = texto(b.mes(), 10, true, NEGRO);
        mes.setUnderline(true);
        VBox centro = new VBox(px(1), titulo, norma, mes);
        centro.setAlignment(Pos.TOP_CENTER);
        VBox derecha = new VBox(px(1));
        derecha.setAlignment(Pos.TOP_RIGHT);
        if (copia != null) {
            derecha.getChildren().add(texto(copia.rotulo(), 8, true, NEGRO));
        }
        derecha.getChildren().addAll(texto("N° " + b.numero(), 10, true, NEGRO),
                texto("Fecha de pago: " + b.fechaPago(), 8.5, false, NEGRO));
        GridPane g = new GridPane();
        g.getColumnConstraints().setAll(columna(22), columna(53), columna(25));
        g.add(logo, 0, 0);
        g.add(centro, 1, 0);
        g.add(derecha, 2, 0);
        return g;
    }

    private static Node datos(FilaDatos f) {
        GridPane g = new GridPane();
        float pesos = 0;
        for (GrupoDatos grupo : f.grupos()) {
            pesos += grupo.ancho();
        }
        double hueco = 1.2;
        double util = 100 - hueco * (f.grupos().size() - 1);
        boolean conTitulo = f.grupos().stream().anyMatch(x -> x.titulo() != null);
        int columna = 0;
        for (int i = 0; i < f.grupos().size(); i++) {
            GrupoDatos grupo = f.grupos().get(i);
            if (i > 0) {
                g.getColumnConstraints().add(columna(hueco));
                columna++;
            }
            float suma = 0;
            for (Campo c : grupo.campos()) {
                suma += c.ancho();
            }
            int fila = 0;
            if (conTitulo) {
                g.add(cabecera(grupo.titulo() == null ? "" : grupo.titulo(), Pos.CENTER_LEFT), columna, fila++,
                        grupo.campos().size(), 1);
            }
            for (Campo c : grupo.campos()) {
                g.getColumnConstraints().add(columna(util * grupo.ancho() / pesos * c.ancho() / suma));
                g.add(cabecera(c.etiqueta(), Pos.CENTER), columna, fila);
                boolean falta = c.valor().contains(Boleta.COMPLETAR);
                Label valor = celda(c.valor(), Pos.CENTER, falta ? ROJO : NEGRO, falta, "1 1 1 1");
                g.add(valor, columna, fila + 1);
                columna++;
            }
        }
        return g;
    }

    private static Node conceptos(Boleta b) {
        GridPane g = new GridPane();
        double tercio = (100 - 2 * 1.2) / 3;
        g.getColumnConstraints().setAll(columna(tercio * 0.72), columna(tercio * 0.28), columna(1.2),
                columna(tercio * 0.6), columna(tercio * 0.24), columna(tercio * 0.16), columna(1.2),
                columna(tercio * 0.6), columna(tercio * 0.24), columna(tercio * 0.16));
        int[] inicio = {0, 3, 7};
        List<Conceptos> columnas = b.cuerpo();
        int filas = b.filasCuerpo();
        for (int i = 0; i < 3; i++) {
            Conceptos c = columnas.get(i);
            g.add(cabecera(c.titulo(), Pos.CENTER), inicio[i], 0, c.conPorcentaje() ? 3 : 2, 1);
            g.add(cabecera("CONCEPTO", Pos.CENTER_LEFT), inicio[i], 1);
            g.add(cabecera("MONTO", Pos.CENTER_RIGHT), inicio[i] + 1, 1);
            if (c.conPorcentaje()) {
                g.add(cabecera("%", Pos.CENTER), inicio[i] + 2, 1);
            }
            for (int f = 0; f < filas; f++) {
                LineaBoleta l = f < c.lineas().size() ? c.lineas().get(f) : null;
                String abajo = f == filas - 1 ? "1" : "0";
                g.add(concepto(l, "0 0 " + abajo + " 1"), inicio[i], f + 2);
                g.add(celda(l == null ? "" : RonaldmichaFormatos.numero(l.monto()), Pos.CENTER_RIGHT, NEGRO, false,
                        "0 " + (c.conPorcentaje() ? "0" : "1") + " " + abajo + " 0"), inicio[i] + 1, f + 2);
                if (c.conPorcentaje()) {
                    g.add(celda(l == null || l.porcentaje() == null ? "" : l.porcentaje(), Pos.CENTER, NEGRO, false,
                            "0 1 " + abajo + " 0"), inicio[i] + 2, f + 2);
                }
            }
        }
        return g;
    }

    private static Node totales(Boleta b) {
        HBox barra = new HBox(px(10),
                total("Total Remuneraciones", RonaldmichaFormatos.numero(b.totalRemuneraciones()), 9),
                total("Total Descuentos", RonaldmichaFormatos.numero(b.totalDescuentos()), 9),
                total("Neto a Pagar", RonaldmichaFormatos.moneda(b.neto()), 11));
        barra.setStyle("-fx-background-color: " + Boleta.FONDO_TOTALES + "; -fx-border-color: " + Boleta.COLOR
                + "; -fx-border-width: 1 0 1 0;");
        barra.setPadding(new Insets(px(4), px(5), px(4), px(5)));
        return barra;
    }

    private static Node total(String etiqueta, String monto, double tamano) {
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox caja = new HBox(px(6), texto(etiqueta, tamano, true, Boleta.TEXTO_TOTALES), espacio,
                texto(monto, tamano, true, Boleta.TEXTO_TOTALES));
        caja.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(caja, Priority.ALWAYS);
        caja.setPrefWidth(1);
        return caja;
    }

    private static Node firmas(FirmasBoleta f, Copia copia, RonaldmichaEmpresa e) {
        double espacioFirma = px(2.5 * 72 / 2.54);
        double linea = px(6 * 72 / 2.54);
        VBox empleador = firma(espacioFirma, linea, f.firmaDigital() ? e.firma() : null);
        empleador.getChildren().addAll(
                centrado(texto(f.representante(), 8.5, true, f.representante().contains(Boleta.COMPLETAR) ? ROJO : NEGRO)),
                centrado(texto(f.documentoRepresentante(), 8, false,
                        f.documentoRepresentante().contains(Boleta.COMPLETAR) ? ROJO : NEGRO)),
                centrado(texto(f.cargoRepresentante(), 8, false, NEGRO)),
                centrado(texto("Firma y sello del empleador", 7, false, GRIS)));
        VBox trabajador = firma(espacioFirma, linea, null);
        trabajador.getChildren().addAll(centrado(texto(f.trabajador(), 8.5, true, NEGRO)),
                centrado(texto(f.documentoTrabajador(), 8, false, NEGRO)),
                centrado(texto("Recibí conforme", 8, false, NEGRO)),
                centrado(texto("Fecha de recepción: ____/____/________", 8, false, NEGRO)));
        String nota = Boleta.notaCopia(copia);
        if (nota != null) {
            trabajador.getChildren().add(centrado(texto(nota, 7, false, GRIS)));
        }
        GridPane g = new GridPane();
        if (f.huella()) {
            Region recuadro = new Region();
            recuadro.setMinSize(px(2.5 * 72 / 2.54), px(3 * 72 / 2.54));
            recuadro.setMaxSize(px(2.5 * 72 / 2.54), px(3 * 72 / 2.54));
            recuadro.setStyle("-fx-border-color: " + Boleta.BORDE + "; -fx-border-width: 1;");
            VBox huella = new VBox(px(2), recuadro, texto("Huella digital", 7, false, GRIS));
            huella.setAlignment(Pos.TOP_CENTER);
            g.getColumnConstraints().setAll(columna(40), columna(40), columna(20));
            g.add(huella, 2, 0);
        } else {
            g.getColumnConstraints().setAll(columna(50), columna(50));
        }
        g.add(empleador, 0, 0);
        g.add(trabajador, 1, 0);
        return g;
    }

    private static VBox firma(double alto, double ancho, byte[] imagen) {
        StackPane espacio = new StackPane();
        espacio.setMinHeight(alto);
        espacio.setPrefHeight(alto);
        espacio.setAlignment(Pos.BOTTOM_CENTER);
        if (imagen != null && imagen.length > 0) {
            Image img = new Image(new ByteArrayInputStream(imagen));
            if (!img.isError()) {
                ImageView vista = new ImageView(img);
                vista.setFitHeight(px(2 * 72 / 2.54));
                vista.setPreserveRatio(true);
                espacio.getChildren().add(vista);
            }
        }
        Region linea = new Region();
        linea.setMinSize(ancho, 1);
        linea.setMaxSize(ancho, 1);
        linea.setStyle("-fx-background-color: " + NEGRO + ";");
        VBox caja = new VBox(px(1.5), espacio, linea);
        caja.setAlignment(Pos.TOP_CENTER);
        return caja;
    }

    private static Label cabecera(String texto, Pos alineacion) {
        Label l = celda(texto.toUpperCase(RonaldmichaFormatos.LOCALE_PE), alineacion, "#FFFFFF", true, "1 1 1 1");
        l.setStyle(l.getStyle() + "-fx-background-color: " + Boleta.COLOR + "; -fx-font-size: " + px(8) + "px;");
        return l;
    }

    private static Node concepto(LineaBoleta l, String bordes) {
        HBox fila = new HBox(px(4));
        fila.setAlignment(Pos.CENTER_LEFT);
        if (l != null) {
            if (l.codigo() != null) {
                fila.getChildren().add(texto(l.codigo(), 7, false, GRIS));
            }
            fila.getChildren().add(texto(l.concepto(), 8.5, false, NEGRO));
        } else {
            fila.getChildren().add(texto(" ", 8.5, false, NEGRO));
        }
        fila.setPadding(new Insets(px(1.5), px(3), px(2), px(3)));
        fila.setStyle("-fx-border-color: " + Boleta.BORDE + "; -fx-border-width: " + bordes + ";");
        fila.setMaxWidth(Double.MAX_VALUE);
        return fila;
    }

    private static Label celda(String texto, Pos alineacion, String color, boolean negrita, String bordes) {
        Label l = texto(texto, 8.5, negrita, color);
        l.setAlignment(alineacion);
        l.setTextAlignment(alineacion == Pos.CENTER ? TextAlignment.CENTER : TextAlignment.LEFT);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setMaxHeight(Double.MAX_VALUE);
        l.setPadding(new Insets(px(1.5), px(3), px(2), px(3)));
        l.setStyle(l.getStyle() + "-fx-border-color: " + Boleta.BORDE + "; -fx-border-width: " + bordes + ";");
        GridPane.setFillHeight(l, true);
        GridPane.setHalignment(l, HPos.CENTER);
        return l;
    }

    private static Label texto(String texto, double puntos, boolean negrita, String color) {
        Label l = new Label(texto);
        l.setStyle(FUENTE + " -fx-font-size: " + px(puntos) + "px; -fx-text-fill: " + color + ";"
                + (negrita ? " -fx-font-weight: bold;" : ""));
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    private static Label centrado(Label l) {
        l.setMaxWidth(Double.MAX_VALUE);
        l.setAlignment(Pos.CENTER);
        l.setTextAlignment(TextAlignment.CENTER);
        l.setWrapText(true);
        return l;
    }

    private static ColumnConstraints columna(double porcentaje) {
        ColumnConstraints c = new ColumnConstraints();
        c.setPercentWidth(porcentaje);
        c.setFillWidth(true);
        return c;
    }

    private static double px(double puntos) {
        return Math.round(puntos * ESCALA * 10) / 10.0;
    }
}
