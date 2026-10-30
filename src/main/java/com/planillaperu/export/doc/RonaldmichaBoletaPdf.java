package com.planillaperu.export.doc;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Boleta;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Campo;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Conceptos;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Copia;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.FilaDatos;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.FirmasBoleta;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.GrupoDatos;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.LineaBoleta;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RonaldmichaBoletaPdf {

    static final float CM = 72f / 2.54f;
    private static final float MARGEN = CM;
    private static final float SEPARACION_FIRMAS = 0.6f * CM;
    private static final float ALTO_FIRMA = 2.5f * CM;
    private static final float ALTO_IMAGEN_FIRMA = 2f * CM;
    private static final float LINEA_FIRMA = 6f * CM;
    private static final float HUELLA_ANCHO = 2.5f * CM;
    private static final float HUELLA_ALTO = 3f * CM;
    private static final float HUECO = 0.012f;

    static final Color COLOR = Color.decode(Boleta.COLOR);
    static final Color BORDE = Color.decode(Boleta.BORDE);
    static final Color FONDO_TOTALES = Color.decode(Boleta.FONDO_TOTALES);
    static final Color TEXTO_TOTALES = Color.decode(Boleta.TEXTO_TOTALES);
    private static final Color GRIS = new Color(0x75, 0x75, 0x75);
    private static final Color ROJO = new Color(0xC6, 0x28, 0x28);

    private static final float[][] ESCALAS = {{1f, 1f}, {0.75f, 1f}, {0.5f, 1f}, {0.3f, 1f}, {0.15f, 0.92f},
            {0.05f, 0.85f}, {0f, 0.78f}};

    private record Estilo(float relleno, Font encabezado, Font valor, Font valorNegrita, Font codigo, Font rojo,
                          Font pie, Font neto, float letra) {

        static Estilo de(float interlineado, float letra) {
            return new Estilo(0.6f + 2.2f * interlineado,
                    new Font(Font.HELVETICA, 8 * letra, Font.BOLD, Color.WHITE),
                    new Font(Font.HELVETICA, 8.5f * letra, Font.NORMAL, Color.BLACK),
                    new Font(Font.HELVETICA, 8.5f * letra, Font.BOLD, Color.BLACK),
                    new Font(Font.HELVETICA, 7 * letra, Font.NORMAL, GRIS),
                    new Font(Font.HELVETICA, 8.5f * letra, Font.BOLD, ROJO),
                    new Font(Font.HELVETICA, 9 * letra, Font.BOLD, TEXTO_TOTALES),
                    new Font(Font.HELVETICA, 11 * letra, Font.BOLD, TEXTO_TOTALES),
                    letra);
        }
    }

    private RonaldmichaBoletaPdf() {
    }

    static byte[] render(List<RonaldmichaDocumentModel> documentos, RonaldmichaEmpresa e, LocalDateTime generado) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), MARGEN, MARGEN, MARGEN, MARGEN);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            doc.open();
            boolean primera = true;
            for (RonaldmichaDocumentModel d : documentos) {
                List<Copia> copias = d.copias().isEmpty() ? Collections.singletonList(null) : d.copias();
                for (Copia copia : copias) {
                    if (!primera) {
                        doc.newPage();
                    }
                    primera = false;
                    pagina(writer.getDirectContent(), doc, d.boleta(), copia, e, RonaldmichaFormatos.fechaHora(generado));
                    writer.setPageEmpty(false);
                }
            }
        } catch (DocumentException ex) {
            throw new IllegalStateException("No se pudo generar el PDF de la boleta", ex);
        } finally {
            doc.close();
        }
        return salida.toByteArray();
    }

    private static void pagina(PdfContentByte canvas, Document doc, Boleta b, Copia copia, RonaldmichaEmpresa e, String emitida) {
        float ancho = doc.right() - doc.left();
        float disponible = doc.top() - doc.bottom();
        List<PdfPTable> cuerpo = null;
        List<PdfPTable> pie = null;
        for (float[] escala : ESCALAS) {
            Estilo s = Estilo.de(escala[0], escala[1]);
            cuerpo = cuerpo(b, copia, e, s, ancho);
            pie = List.of(firmas(b.firmas(), copia, e, s, ancho), leyenda(Boleta.leyendaFinal(emitida), s, ancho));
            if (alto(cuerpo) + SEPARACION_FIRMAS + alto(pie) <= disponible) {
                break;
            }
        }
        float y = doc.top();
        for (PdfPTable t : cuerpo) {
            y = t.writeSelectedRows(0, -1, doc.left(), y, canvas);
        }
        float yPie = doc.bottom() + alto(pie);
        for (PdfPTable t : pie) {
            yPie = t.writeSelectedRows(0, -1, doc.left(), yPie, canvas);
        }
    }

    private static float alto(List<PdfPTable> tablas) {
        float total = 0;
        for (PdfPTable t : tablas) {
            total += t.getTotalHeight();
        }
        return total;
    }

    private static List<PdfPTable> cuerpo(Boleta b, Copia copia, RonaldmichaEmpresa e, Estilo s, float ancho) {
        List<PdfPTable> lista = new ArrayList<>();
        lista.add(encabezado(b, copia, e, s, ancho));
        for (FilaDatos f : b.datos()) {
            lista.add(espacio(3 * s.letra(), ancho));
            lista.add(datos(f, s, ancho));
        }
        lista.add(espacio(4 * s.letra(), ancho));
        lista.add(conceptos(b, s, ancho));
        lista.add(totales(b, s, ancho));
        PdfPTable son = fila(ancho);
        son.addCell(texto("Son: " + b.enLetras(), s.valorNegrita(), Element.ALIGN_LEFT, 3));
        lista.add(son);
        if (b.leyendaCese() != null) {
            PdfPTable cese = fila(ancho);
            cese.addCell(texto(b.leyendaCese(), new Font(Font.HELVETICA, 8 * s.letra(), Font.ITALIC, Color.BLACK),
                    Element.ALIGN_LEFT, 1));
            lista.add(cese);
        }
        return lista;
    }

    private static PdfPTable encabezado(Boleta b, Copia copia, RonaldmichaEmpresa e, Estilo s, float ancho) {
        PdfPTable t = tabla(new float[]{0.22f, 0.53f, 0.25f}, ancho);
        Image logo = imagen(e.logo(), 4.5f * CM, 1.6f * CM);
        PdfPCell celdaLogo = logo == null ? sinBorde() : new PdfPCell(logo, false);
        celdaLogo.setBorder(Rectangle.NO_BORDER);
        celdaLogo.setHorizontalAlignment(Element.ALIGN_LEFT);
        celdaLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(celdaLogo);

        PdfPCell centro = sinBorde();
        centro.addElement(centrado(Boleta.TITULO, new Font(Font.HELVETICA, 15 * s.letra(), Font.BOLD, Color.BLACK)));
        centro.addElement(centrado(Boleta.NORMA, new Font(Font.HELVETICA, 8.5f * s.letra(), Font.NORMAL, Color.BLACK)));
        Paragraph mes = centrado(b.mes(), new Font(Font.HELVETICA, 10 * s.letra(), Font.BOLD | Font.UNDERLINE,
                Color.BLACK));
        mes.setSpacingBefore(2);
        centro.addElement(mes);
        t.addCell(centro);

        PdfPCell derecha = sinBorde();
        if (copia != null) {
            derecha.addElement(alineado(copia.rotulo(), new Font(Font.HELVETICA, 8, Font.BOLD, Color.BLACK),
                    Element.ALIGN_RIGHT));
        }
        derecha.addElement(alineado("N° " + b.numero(), new Font(Font.HELVETICA, 10 * s.letra(), Font.BOLD,
                Color.BLACK), Element.ALIGN_RIGHT));
        derecha.addElement(alineado("Fecha de pago: " + b.fechaPago(), s.valor(), Element.ALIGN_RIGHT));
        t.addCell(derecha);
        return t;
    }

    private static PdfPTable datos(FilaDatos f, Estilo s, float ancho) {
        List<Float> anchos = new ArrayList<>();
        float pesos = 0;
        for (GrupoDatos g : f.grupos()) {
            pesos += g.ancho();
        }
        float util = 1f - HUECO * (f.grupos().size() - 1);
        for (int i = 0; i < f.grupos().size(); i++) {
            GrupoDatos g = f.grupos().get(i);
            if (i > 0) {
                anchos.add(HUECO);
            }
            float suma = 0;
            for (Campo c : g.campos()) {
                suma += c.ancho();
            }
            for (Campo c : g.campos()) {
                anchos.add(util * g.ancho() / pesos * c.ancho() / suma);
            }
        }
        PdfPTable t = tabla(arreglo(anchos), ancho);
        if (f.grupos().stream().anyMatch(g -> g.titulo() != null)) {
            for (int i = 0; i < f.grupos().size(); i++) {
                GrupoDatos g = f.grupos().get(i);
                if (i > 0) {
                    t.addCell(sinBorde());
                }
                PdfPCell barra = cabecera(g.titulo() == null ? "" : g.titulo(), s, Element.ALIGN_LEFT);
                barra.setColspan(g.campos().size());
                t.addCell(barra);
            }
        }
        for (int i = 0; i < f.grupos().size(); i++) {
            if (i > 0) {
                t.addCell(sinBorde());
            }
            for (Campo c : f.grupos().get(i).campos()) {
                t.addCell(cabecera(c.etiqueta(), s, Element.ALIGN_CENTER));
            }
        }
        for (int i = 0; i < f.grupos().size(); i++) {
            if (i > 0) {
                t.addCell(sinBorde());
            }
            for (Campo c : f.grupos().get(i).campos()) {
                t.addCell(borde(c.valor(), valorFont(c.valor(), s), Element.ALIGN_CENTER, s, Rectangle.BOX));
            }
        }
        return t;
    }

    private static PdfPTable conceptos(Boleta b, Estilo s, float ancho) {
        PdfPTable t = tabla(anchosCuerpo(), ancho);
        List<Conceptos> columnas = b.cuerpo();
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                t.addCell(sinBorde());
            }
            PdfPCell barra = cabecera(columnas.get(i).titulo(), s, Element.ALIGN_CENTER);
            barra.setColspan(columnas(i));
            t.addCell(barra);
        }
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                t.addCell(sinBorde());
            }
            t.addCell(cabecera("CONCEPTO", s, Element.ALIGN_LEFT));
            t.addCell(cabecera("MONTO", s, Element.ALIGN_RIGHT));
            if (columnas.get(i).conPorcentaje()) {
                t.addCell(cabecera("%", s, Element.ALIGN_CENTER));
            }
        }
        int filas = b.filasCuerpo();
        for (int f = 0; f < filas; f++) {
            boolean ultima = f == filas - 1;
            for (int i = 0; i < 3; i++) {
                if (i > 0) {
                    t.addCell(sinBorde());
                }
                Conceptos c = columnas.get(i);
                LineaBoleta l = f < c.lineas().size() ? c.lineas().get(f) : null;
                int abajo = ultima ? Rectangle.BOTTOM : 0;
                Phrase concepto = new Phrase();
                if (l != null) {
                    if (l.codigo() != null) {
                        concepto.add(new Chunk(l.codigo() + "  ", s.codigo()));
                    }
                    concepto.add(new Chunk(l.concepto(), s.valor()));
                }
                t.addCell(celda(concepto, Element.ALIGN_LEFT, s, Rectangle.LEFT | abajo));
                t.addCell(celda(new Phrase(l == null ? "" : RonaldmichaFormatos.numero(l.monto()), s.valor()), Element.ALIGN_RIGHT,
                        s, (c.conPorcentaje() ? 0 : Rectangle.RIGHT) | abajo));
                if (c.conPorcentaje()) {
                    t.addCell(celda(new Phrase(l == null || l.porcentaje() == null ? "" : l.porcentaje(), s.valor()),
                            Element.ALIGN_CENTER, s, Rectangle.RIGHT | abajo));
                }
            }
        }
        return t;
    }

    private static PdfPTable totales(Boleta b, Estilo s, float ancho) {
        PdfPTable t = tabla(anchosCuerpo(), ancho);
        t.addCell(barra("Total Remuneraciones", s.pie(), Element.ALIGN_LEFT, 1, s));
        t.addCell(barra(RonaldmichaFormatos.numero(b.totalRemuneraciones()), s.pie(), Element.ALIGN_RIGHT, 1, s));
        t.addCell(barra("", s.pie(), Element.ALIGN_LEFT, 1, s));
        t.addCell(barra("Total Descuentos", s.pie(), Element.ALIGN_LEFT, 1, s));
        t.addCell(barra(RonaldmichaFormatos.numero(b.totalDescuentos()), s.pie(), Element.ALIGN_RIGHT, 1, s));
        t.addCell(barra("", s.pie(), Element.ALIGN_LEFT, 1, s));
        t.addCell(barra("", s.pie(), Element.ALIGN_LEFT, 1, s));
        t.addCell(barra("Neto a Pagar", s.neto(), Element.ALIGN_LEFT, 1, s));
        t.addCell(barra(RonaldmichaFormatos.moneda(b.neto()), s.neto(), Element.ALIGN_RIGHT, 2, s));
        return t;
    }

    private static float[] anchosCuerpo() {
        float tercio = (1f - 2 * HUECO) / 3;
        return new float[]{tercio * 0.72f, tercio * 0.28f, HUECO, tercio * 0.6f, tercio * 0.24f, tercio * 0.16f, HUECO,
                tercio * 0.6f, tercio * 0.24f, tercio * 0.16f};
    }

    private static int columnas(int grupo) {
        return grupo == 0 ? 2 : 3;
    }

    private static PdfPTable firmas(FirmasBoleta f, Copia copia, RonaldmichaEmpresa e, Estilo s, float ancho) {
        PdfPTable t = tabla(f.huella() ? new float[]{0.4f, 0.4f, 0.2f} : new float[]{0.5f, 0.5f}, ancho);
        Font negrita = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.BLACK);
        Font normal = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);
        Font nota = new Font(Font.HELVETICA, 7, Font.NORMAL, GRIS);

        Image firma = f.firmaDigital() ? imagenDeAlto(e.firma(), ALTO_IMAGEN_FIRMA, ancho * 0.38f) : null;
        t.addCell(firma(firma, List.of(
                alineado(f.representante(), f.representante().contains(Boleta.COMPLETAR)
                        ? new Font(Font.HELVETICA, 8.5f, Font.BOLD, ROJO) : negrita, Element.ALIGN_CENTER),
                alineado(f.documentoRepresentante(), f.documentoRepresentante().contains(Boleta.COMPLETAR)
                        ? new Font(Font.HELVETICA, 8, Font.BOLD, ROJO) : normal, Element.ALIGN_CENTER),
                alineado(f.cargoRepresentante(), normal, Element.ALIGN_CENTER),
                alineado("Firma y sello del empleador", nota, Element.ALIGN_CENTER))));

        List<Paragraph> trabajador = new ArrayList<>(List.of(
                alineado(f.trabajador(), negrita, Element.ALIGN_CENTER),
                alineado(f.documentoTrabajador(), normal, Element.ALIGN_CENTER),
                alineado("Recibí conforme", normal, Element.ALIGN_CENTER),
                alineado("Fecha de recepción: ____/____/________", normal, Element.ALIGN_CENTER)));
        String notaCopia = Boleta.notaCopia(copia);
        if (notaCopia != null) {
            trabajador.add(alineado(notaCopia, nota, Element.ALIGN_CENTER));
        }
        t.addCell(firma(null, trabajador));

        if (f.huella()) {
            PdfPCell huella = sinBorde();
            PdfPTable caja = new PdfPTable(1);
            caja.setTotalWidth(HUELLA_ANCHO);
            caja.setLockedWidth(true);
            caja.setHorizontalAlignment(Element.ALIGN_CENTER);
            PdfPCell recuadro = new PdfPCell();
            recuadro.setFixedHeight(HUELLA_ALTO);
            recuadro.setBorderColor(BORDE);
            recuadro.setBorderWidth(0.6f);
            caja.addCell(recuadro);
            huella.addElement(caja);
            huella.addElement(alineado("Huella digital", nota, Element.ALIGN_CENTER));
            t.addCell(huella);
        }
        return t;
    }

    private static PdfPCell firma(Image imagen, List<Paragraph> textos) {
        PdfPCell celda = sinBorde();
        PdfPTable espacio = new PdfPTable(1);
        espacio.setWidthPercentage(100);
        PdfPCell hueco = imagen == null ? new PdfPCell() : new PdfPCell(imagen, false);
        hueco.setBorder(Rectangle.NO_BORDER);
        hueco.setFixedHeight(ALTO_FIRMA);
        hueco.setHorizontalAlignment(Element.ALIGN_CENTER);
        hueco.setVerticalAlignment(Element.ALIGN_BOTTOM);
        espacio.addCell(hueco);
        celda.addElement(espacio);
        PdfPTable linea = new PdfPTable(1);
        linea.setTotalWidth(LINEA_FIRMA);
        linea.setLockedWidth(true);
        linea.setHorizontalAlignment(Element.ALIGN_CENTER);
        PdfPCell trazo = new PdfPCell();
        trazo.setBorder(Rectangle.TOP);
        trazo.setBorderWidthTop(0.8f);
        trazo.setFixedHeight(3);
        linea.addCell(trazo);
        celda.addElement(linea);
        textos.forEach(celda::addElement);
        return celda;
    }

    private static PdfPTable leyenda(String texto, Estilo s, float ancho) {
        PdfPTable t = fila(ancho);
        PdfPCell c = texto(texto, new Font(Font.HELVETICA, 7, Font.NORMAL, GRIS), Element.ALIGN_CENTER, 2);
        c.setPaddingTop(4);
        t.addCell(c);
        return t;
    }

    private static PdfPTable tabla(float[] anchos, float ancho) {
        PdfPTable t = new PdfPTable(anchos);
        t.setTotalWidth(ancho);
        t.setLockedWidth(true);
        return t;
    }

    private static PdfPTable fila(float ancho) {
        return tabla(new float[]{1}, ancho);
    }

    private static PdfPTable espacio(float alto, float ancho) {
        PdfPTable t = fila(ancho);
        PdfPCell c = sinBorde();
        c.setFixedHeight(alto);
        t.addCell(c);
        return t;
    }

    private static PdfPCell cabecera(String texto, Estilo s, int alineacion) {
        PdfPCell c = borde(texto.toUpperCase(RonaldmichaFormatos.LOCALE_PE), s.encabezado(), alineacion, s, Rectangle.BOX);
        c.setBackgroundColor(COLOR);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return c;
    }

    private static PdfPCell barra(String texto, Font f, int alineacion, int colspan, Estilo s) {
        PdfPCell c = new PdfPCell(new Phrase(texto, f));
        c.setBackgroundColor(FONDO_TOTALES);
        c.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        c.setBorderColor(COLOR);
        c.setBorderWidth(0.8f);
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setColspan(colspan);
        c.setPadding(2 + s.relleno());
        return c;
    }

    private static PdfPCell borde(String texto, Font f, int alineacion, Estilo s, int bordes) {
        return celda(new Phrase(texto == null ? "" : texto, f), alineacion, s, bordes);
    }

    private static PdfPCell celda(Phrase frase, int alineacion, Estilo s, int bordes) {
        PdfPCell c = new PdfPCell(frase);
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBorder(bordes);
        c.setBorderColor(BORDE);
        c.setBorderWidth(0.5f);
        c.setPaddingTop(s.relleno());
        c.setPaddingBottom(s.relleno() + 1);
        c.setPaddingLeft(3);
        c.setPaddingRight(3);
        return c;
    }

    private static PdfPCell texto(String texto, Font f, int alineacion, float rellenoArriba) {
        PdfPCell c = new PdfPCell(new Phrase(texto, f));
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(alineacion);
        c.setPaddingTop(rellenoArriba);
        c.setPaddingLeft(0);
        return c;
    }

    private static Font valorFont(String texto, Estilo s) {
        return texto != null && texto.contains(Boleta.COMPLETAR) ? s.rojo() : s.valor();
    }

    private static PdfPCell sinBorde() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(0);
        return c;
    }

    private static Paragraph centrado(String texto, Font f) {
        return alineado(texto, f, Element.ALIGN_CENTER);
    }

    private static Paragraph alineado(String texto, Font f, int alineacion) {
        Paragraph p = new Paragraph(texto, f);
        p.setAlignment(alineacion);
        p.setLeading(f.getSize() * 1.25f);
        return p;
    }

    private static float[] arreglo(List<Float> valores) {
        float[] a = new float[valores.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = valores.get(i);
        }
        return a;
    }

    private static Image imagenDeAlto(byte[] bytes, float alto, float anchoMaximo) {
        Image img = imagen(bytes, Float.MAX_VALUE, Float.MAX_VALUE);
        if (img == null || img.getHeight() <= 0) {
            return null;
        }
        img.scalePercent(alto / img.getHeight() * 100);
        if (img.getScaledWidth() > anchoMaximo) {
            img.scaleToFit(anchoMaximo, alto);
        }
        return img;
    }

    static Image imagen(byte[] bytes, float ancho, float alto) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            Image img = Image.getInstance(bytes);
            img.scaleToFit(ancho, alto);
            return img;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }
}
