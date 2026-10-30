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
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Bloque;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Columna;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Copia;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Firma;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Firmas;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Heading;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Item;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.KeyValue;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Orientacion;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Par;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Table;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Total;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Tri;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RonaldmichaPdfRenderer {

    private static final Color TEXTO = new Color(0x17, 0x20, 0x1B);
    private static final Color GRIS = new Color(0x56, 0x62, 0x5C);
    private static final Color PRIMARIO = new Color(0x0E, 0x5A, 0x47);
    private static final Color FONDO = Color.decode(RonaldmichaDocumentModel.Boleta.FONDO_TOTALES);
    private static final Color BLANCO = Color.WHITE;
    private static final Color BORDE = new Color(0xDD, 0xE2, 0xDD);
    private static final Color AVISO_FG = new Color(0x7A, 0x43, 0x00);
    private static final Color AVISO_BG = new Color(0xFF, 0xF1, 0xDD);
    private static final float MARGEN = 28;

    private record Estilo(Font titulo, Font empresa, Font normal, Font negrita, Font pequena, Font seccion,
                          Font total, Font aviso) {

        static Estilo de(boolean compacto) {
            float base = compacto ? 7f : 8.5f;
            return new Estilo(
                    new Font(Font.HELVETICA, base + 4, Font.BOLD, TEXTO),
                    new Font(Font.HELVETICA, base + 2, Font.BOLD, TEXTO),
                    new Font(Font.HELVETICA, base, Font.NORMAL, TEXTO),
                    new Font(Font.HELVETICA, base, Font.BOLD, TEXTO),
                    new Font(Font.HELVETICA, base - 1, Font.NORMAL, GRIS),
                    new Font(Font.HELVETICA, base + 1, Font.BOLD, PRIMARIO),
                    new Font(Font.HELVETICA, base + 4, Font.BOLD, PRIMARIO),
                    new Font(Font.HELVETICA, base - 1, Font.BOLD, AVISO_FG));
        }
    }

    private RonaldmichaPdfRenderer() {
    }

    public static byte[] render(RonaldmichaDocumentModel d, RonaldmichaEmpresa e, Optional<String> aviso, LocalDateTime generado) {
        return renderVarios(List.of(d), d.archivo(), e, aviso, generado);
    }

    public static byte[] renderVarios(List<RonaldmichaDocumentModel> documentos, String archivo, RonaldmichaEmpresa e, Optional<String> aviso,
                                      LocalDateTime generado) {
        if (!documentos.isEmpty() && documentos.stream().allMatch(RonaldmichaDocumentModel::esBoleta)) {
            return RonaldmichaBoletaPdf.render(documentos, e, generado);
        }
        byte[] borrador = generar(documentos, archivo, e, aviso, generado, 0);
        int paginas;
        try {
            PdfReader lector = new PdfReader(borrador);
            paginas = lector.getNumberOfPages();
            lector.close();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo contar las páginas del PDF", ex);
        }
        return generar(documentos, archivo, e, aviso, generado, paginas);
    }

    private static byte[] generar(List<RonaldmichaDocumentModel> documentos, String archivo, RonaldmichaEmpresa e, Optional<String> aviso,
                                  LocalDateTime generado, int paginas) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(tamano(documentos.isEmpty() ? null : documentos.getFirst()),
                MARGEN, MARGEN, MARGEN, MARGEN + 14);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            writer.setPageEvent(new Pie("Planilla Perú · " + (archivo == null ? "documento" : archivo) + " · "
                    + RonaldmichaFormatos.fechaHora(generado), paginas));
            doc.open();
            if (documentos.isEmpty()) {
                doc.add(new Paragraph("No hay documentos para el filtro elegido.", Estilo.de(false).normal()));
            }
            for (int i = 0; i < documentos.size(); i++) {
                RonaldmichaDocumentModel d = documentos.get(i);
                if (i > 0) {
                    doc.setPageSize(tamano(d));
                    doc.newPage();
                }
                if (d.copias().isEmpty()) {
                    for (Element el : contenido(d, null, e, aviso, Estilo.de(false))) {
                        doc.add(el);
                    }
                } else {
                    doc.add(copiasLadoALado(d, e, aviso));
                }
            }
        } catch (DocumentException ex) {
            throw new IllegalStateException("No se pudo generar el PDF", ex);
        } finally {
            doc.close();
        }
        return salida.toByteArray();
    }

    private static Rectangle tamano(RonaldmichaDocumentModel d) {
        boolean horizontal = d != null && (!d.copias().isEmpty() || d.orientacion() == Orientacion.HORIZONTAL);
        return horizontal ? PageSize.A4.rotate() : PageSize.A4;
    }

    private static PdfPTable copiasLadoALado(RonaldmichaDocumentModel d, RonaldmichaEmpresa e, Optional<String> aviso) {
        List<Copia> copias = d.copias();
        float[] anchos = new float[copias.size() * 2 - 1];
        for (int i = 0; i < anchos.length; i++) {
            anchos[i] = i % 2 == 0 ? 1f : 0.05f;
        }
        PdfPTable tabla = new PdfPTable(anchos);
        tabla.setWidthPercentage(100);
        Estilo estilo = Estilo.de(true);
        for (int i = 0; i < copias.size(); i++) {
            if (i > 0) {
                PdfPCell separador = sinBorde();
                separador.setCellEvent(new LineaPunteada());
                tabla.addCell(separador);
            }
            PdfPCell celda = sinBorde();
            celda.setPaddingRight(4);
            celda.setPaddingLeft(4);
            for (Element el : contenido(d, copias.get(i), e, aviso, estilo)) {
                celda.addElement(el);
            }
            tabla.addCell(celda);
        }
        return tabla;
    }

    private static List<Element> contenido(RonaldmichaDocumentModel d, Copia copia, RonaldmichaEmpresa e, Optional<String> aviso, Estilo s) {
        List<Element> lista = new ArrayList<>(encabezado(e, aviso, s));
        if (copia != null) {
            Paragraph rotulo = new Paragraph(copia.rotulo(), s.negrita());
            rotulo.setAlignment(Element.ALIGN_RIGHT);
            lista.add(rotulo);
        }
        Paragraph titulo = new Paragraph(d.titulo(), s.titulo());
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingBefore(4);
        lista.add(titulo);
        if (d.subtitulo() != null && !d.subtitulo().isBlank()) {
            Paragraph sub = new Paragraph(d.subtitulo(), s.pequena());
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(6);
            lista.add(sub);
        }
        for (Bloque b : d.bloques()) {
            lista.add(bloque(b, e, s));
        }
        return lista;
    }

    private static List<Element> encabezado(RonaldmichaEmpresa e, Optional<String> aviso, Estilo s) {
        List<Element> lista = new ArrayList<>();
        PdfPTable tabla = new PdfPTable(new float[]{1, 4});
        tabla.setWidthPercentage(100);
        PdfPCell logo = sinBorde();
        Image img = imagen(e.logo(), 36);
        if (img != null) {
            logo.addElement(img);
        }
        tabla.addCell(logo);
        PdfPCell datos = sinBorde();
        datos.addElement(derecha(new Paragraph(valor(e.razonSocial(), "Empresa sin registrar"), s.empresa())));
        datos.addElement(derecha(new Paragraph("RUC " + valor(e.ruc(), "—"), s.pequena())));
        String domicilio = Stream.of(e.direccion(), e.distrito(), e.provincia(), e.departamento())
                .filter(x -> x != null && !x.isBlank()).collect(Collectors.joining(" · "));
        if (!domicilio.isEmpty()) {
            datos.addElement(derecha(new Paragraph(domicilio, s.pequena())));
        }
        tabla.addCell(datos);
        lista.add(tabla);
        Paragraph linea = new Paragraph(new Chunk(new LineSeparator(1.5f, 100, PRIMARIO, Element.ALIGN_CENTER, -2)));
        lista.add(linea);
        aviso.ifPresent(a -> {
            PdfPTable caja = new PdfPTable(1);
            caja.setWidthPercentage(100);
            caja.setSpacingBefore(4);
            PdfPCell celda = new PdfPCell(new Phrase("Aviso: " + a, s.aviso()));
            celda.setBackgroundColor(AVISO_BG);
            celda.setBorder(Rectangle.NO_BORDER);
            celda.setPadding(5);
            caja.addCell(celda);
            lista.add(caja);
        });
        return lista;
    }

    private static Element bloque(Bloque b, RonaldmichaEmpresa e, Estilo s) {
        return switch (b) {
            case Heading h -> {
                Paragraph p = new Paragraph(h.texto(), s.seccion());
                p.setSpacingBefore(6);
                p.setSpacingAfter(2);
                yield p;
            }
            case RonaldmichaDocumentModel.Paragraph p -> {
                Paragraph par = new Paragraph(p.texto(), s.normal());
                par.setAlignment(Element.ALIGN_JUSTIFIED);
                par.setLeading(s.normal().getSize() * 1.5f);
                par.setSpacingAfter(4);
                yield par;
            }
            case KeyValue kv -> claveValor(kv, s);
            case Table t -> tabla(t, s);
            case Tri tri -> tri(tri, s);
            case Total t -> total(t, s);
            case Firmas f -> firmas(f, e, s);
            case RonaldmichaDocumentModel.Boleta x -> throw new IllegalStateException("La boleta se dibuja con BoletaPdf");
        };
    }

    private static PdfPTable claveValor(KeyValue kv, Estilo s) {
        PdfPTable tabla = new PdfPTable(new float[]{1.2f, 2f, 1.2f, 2f});
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(4);
        for (Par p : kv.pares()) {
            tabla.addCell(celda(p.etiqueta(), s.pequena(), Element.ALIGN_LEFT, null));
            tabla.addCell(celda(p.valor(), s.normal(), Element.ALIGN_LEFT, null));
        }
        if (kv.pares().size() % 2 == 1) {
            tabla.addCell(celda("", s.normal(), Element.ALIGN_LEFT, null));
            tabla.addCell(celda("", s.normal(), Element.ALIGN_LEFT, null));
        }
        return tabla;
    }

    private static PdfPTable tabla(Table t, Estilo s) {
        PdfPTable tabla = new PdfPTable(Math.max(1, t.encabezados().size()));
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(4);
        tabla.setHeaderRows(1);
        for (int i = 0; i < t.encabezados().size(); i++) {
            tabla.addCell(cabecera(t.encabezados().get(i), s, alineacion(t, i)));
        }
        for (List<String> fila : t.filas()) {
            for (int i = 0; i < t.encabezados().size(); i++) {
                tabla.addCell(celda(i < fila.size() ? fila.get(i) : "", s.normal(), alineacion(t, i), null));
            }
        }
        if (!t.pie().isEmpty()) {
            for (int i = 0; i < t.encabezados().size(); i++) {
                tabla.addCell(celda(i < t.pie().size() ? t.pie().get(i) : "", s.negrita(), alineacion(t, i), FONDO));
            }
        }
        return tabla;
    }

    private static PdfPTable tri(Tri tri, Estilo s) {
        PdfPTable tabla = new PdfPTable(3);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(6);
        for (Columna c : tri.columnas()) {
            PdfPTable interna = new PdfPTable(new float[]{1.1f, 2.6f, 1.4f});
            interna.setWidthPercentage(100);
            PdfPCell titulo = cabecera(c.titulo(), s, Element.ALIGN_LEFT);
            titulo.setColspan(3);
            interna.addCell(titulo);
            for (Item it : c.items()) {
                interna.addCell(celda(it.codigo() == null ? "" : it.codigo(), s.pequena(), Element.ALIGN_LEFT, null));
                interna.addCell(celda(it.concepto(), s.normal(), Element.ALIGN_LEFT, null));
                interna.addCell(celda(RonaldmichaFormatos.numero(it.monto()), s.normal(), Element.ALIGN_RIGHT, null));
            }
            PdfPCell etiqueta = celda("Total", s.negrita(), Element.ALIGN_RIGHT, null);
            etiqueta.setColspan(2);
            interna.addCell(etiqueta);
            interna.addCell(celda(RonaldmichaFormatos.numero(c.total()), s.negrita(), Element.ALIGN_RIGHT, null));
            PdfPCell contenedor = sinBorde();
            contenedor.setPadding(2);
            contenedor.addElement(interna);
            tabla.addCell(contenedor);
        }
        return tabla;
    }

    private static PdfPTable total(Total t, Estilo s) {
        PdfPTable tabla = new PdfPTable(new float[]{3, 1.4f});
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(6);
        PdfPCell etiqueta = celda(t.etiqueta(), s.total(), Element.ALIGN_RIGHT, FONDO);
        etiqueta.setPadding(6);
        tabla.addCell(etiqueta);
        PdfPCell monto = celda(RonaldmichaFormatos.moneda(t.monto()), s.total(), Element.ALIGN_RIGHT, FONDO);
        monto.setPadding(6);
        tabla.addCell(monto);
        if (t.enLetras() != null) {
            PdfPCell letras = celda("Son: " + t.enLetras(), s.negrita(), Element.ALIGN_LEFT, null);
            letras.setColspan(2);
            tabla.addCell(letras);
        }
        return tabla;
    }

    private static PdfPTable firmas(Firmas f, RonaldmichaEmpresa e, Estilo s) {
        PdfPTable tabla = new PdfPTable(Math.max(1, f.firmas().size()));
        tabla.setWidthPercentage(90);
        tabla.setSpacingBefore(18);
        for (Firma firma : f.firmas()) {
            PdfPCell celda = sinBorde();
            celda.setHorizontalAlignment(Element.ALIGN_CENTER);
            Image img = firma.representante() ? imagen(e.firma(), 34) : null;
            if (img != null) {
                img.setAlignment(Element.ALIGN_CENTER);
                celda.addElement(img);
            } else {
                Paragraph espacio = new Paragraph(" ", s.normal());
                espacio.setSpacingBefore(18);
                celda.addElement(espacio);
            }
            Paragraph texto = new Paragraph("______________________________\n" + firma.nombre()
                    + (firma.detalle() == null || firma.detalle().isBlank() ? "" : "\n" + firma.detalle()), s.pequena());
            texto.setAlignment(Element.ALIGN_CENTER);
            celda.addElement(texto);
            tabla.addCell(celda);
        }
        return tabla;
    }

    private static int alineacion(Table t, int columna) {
        return t.numerica(columna) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
    }

    private static PdfPCell cabecera(String texto, Estilo s, int alineacion) {
        Font f = new Font(s.negrita());
        f.setColor(BLANCO);
        return celda(texto, f, alineacion, PRIMARIO);
    }

    private static PdfPCell celda(String texto, Font f, int alineacion, Color fondo) {
        PdfPCell celda = new PdfPCell(new Phrase(texto == null ? "" : texto, f));
        celda.setHorizontalAlignment(alineacion);
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColor(BORDE);
        celda.setPadding(2.5f);
        if (fondo != null) {
            celda.setBackgroundColor(fondo);
        }
        return celda;
    }

    private static PdfPCell sinBorde() {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.NO_BORDER);
        return celda;
    }

    private static Paragraph derecha(Paragraph p) {
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static Image imagen(byte[] bytes, float alto) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            Image img = Image.getInstance(bytes);
            img.scaleToFit(alto * 4, alto);
            return img;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private static String valor(String s, String porDefecto) {
        return s == null || s.isBlank() ? porDefecto : s;
    }

    private static final class LineaPunteada implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle r, PdfContentByte[] canvases) {
            PdfContentByte c = canvases[PdfPTable.LINECANVAS];
            float x = (r.getLeft() + r.getRight()) / 2;
            c.saveState();
            c.setColorStroke(GRIS);
            c.setLineWidth(0.6f);
            c.setLineDash(3, 3, 0);
            c.moveTo(x, r.getTop());
            c.lineTo(x, r.getBottom());
            c.stroke();
            c.restoreState();
        }
    }

    private static final class Pie extends PdfPageEventHelper {
        private final String texto;
        private final int paginas;
        private BaseFont fuente;

        Pie(String texto, int paginas) {
            this.texto = texto;
            this.paginas = paginas;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                fuente = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            } catch (DocumentException | IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte c = writer.getDirectContent();
            c.saveState();
            c.beginText();
            c.setFontAndSize(fuente, 7);
            c.setColorFill(GRIS);
            c.setTextMatrix(document.left(), document.bottom() - 16);
            c.showText(texto + " · Página " + writer.getPageNumber() + " de " + paginas);
            c.endText();
            c.restoreState();
        }
    }
}
