package com.planillaperu.export;

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
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.planillaperu.domain.RonaldmichaEmpresa;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RonaldmichaMembretePdf {

    private static final Font TITULO = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(0x17, 0x20, 0x1B));
    private static final Font NORMAL = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(0x56, 0x62, 0x5C));
    private static final Font AVISO = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(0x7A, 0x43, 0x00));
    private static final Color AVISO_FONDO = new Color(0xFF, 0xF1, 0xDD);
    private static final float LOGO_ALTO = 48;
    private static final float FIRMA_ALTO = 56;

    private RonaldmichaMembretePdf() {
    }

    public static void encabezado(Document doc, RonaldmichaEmpresa e, Optional<String> aviso) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{1, 3});
        tabla.setWidthPercentage(100);

        PdfPCell celdaLogo = celdaSinBorde();
        Image logo = imagen(e.logo(), LOGO_ALTO);
        if (logo != null) {
            celdaLogo.addElement(logo);
        }
        tabla.addCell(celdaLogo);

        PdfPCell datos = celdaSinBorde();
        datos.setHorizontalAlignment(Element.ALIGN_RIGHT);
        datos.addElement(derecha(new Paragraph(valor(e.razonSocial(), "Empresa sin registrar"), TITULO)));
        datos.addElement(derecha(new Paragraph("RUC " + valor(e.ruc(), "—"), NORMAL)));
        String domicilio = Stream.of(e.direccion(), e.distrito(), e.provincia(), e.departamento())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" · "));
        if (!domicilio.isEmpty()) {
            datos.addElement(derecha(new Paragraph(domicilio, NORMAL)));
        }
        tabla.addCell(datos);
        doc.add(tabla);

        if (aviso.isPresent()) {
            PdfPTable caja = new PdfPTable(1);
            caja.setWidthPercentage(100);
            caja.setSpacingBefore(8);
            PdfPCell celda = new PdfPCell(new Phrase("Aviso: " + aviso.get(), AVISO));
            celda.setBackgroundColor(AVISO_FONDO);
            celda.setBorder(Rectangle.NO_BORDER);
            celda.setPadding(8);
            caja.addCell(celda);
            doc.add(caja);
        }
        doc.add(new Paragraph(Chunk.NEWLINE));
    }

    public static void firma(Document doc, RonaldmichaEmpresa e) throws DocumentException {
        Paragraph bloque = new Paragraph();
        bloque.setAlignment(Element.ALIGN_CENTER);
        bloque.setSpacingBefore(32);
        Image firma = imagen(e.firma(), FIRMA_ALTO);
        if (firma != null) {
            firma.setAlignment(Element.ALIGN_CENTER);
            doc.add(bloque);
            doc.add(firma);
            bloque = new Paragraph();
            bloque.setAlignment(Element.ALIGN_CENTER);
        }
        bloque.add(new Phrase("______________________________\n", NORMAL));
        bloque.add(new Phrase(valor(e.repNombre(), "Representante legal") + "\n", NORMAL));
        if (e.repCargo() != null) {
            bloque.add(new Phrase(e.repCargo() + "\n", NORMAL));
        }
        if (e.repDoc() != null) {
            bloque.add(new Phrase(valor(e.repTipoDoc(), "DNI") + " " + e.repDoc(), NORMAL));
        }
        doc.add(bloque);
    }

    public static byte[] vistaPrevia(RonaldmichaEmpresa e, Optional<String> aviso) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.open();
            encabezado(doc, e, aviso);
            doc.add(new Paragraph("Vista previa del membrete de los documentos.", NORMAL));
            firma(doc, e);
        } catch (DocumentException ex) {
            throw new IllegalStateException("No se pudo generar el PDF de vista previa", ex);
        } finally {
            doc.close();
        }
        return salida.toByteArray();
    }

    private static Image imagen(byte[] bytes, float alto) throws DocumentException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            Image img = Image.getInstance(bytes);
            img.scaleToFit(alto * 4, alto);
            return img;
        } catch (IOException ex) {
            throw new DocumentException(ex);
        }
    }

    private static PdfPCell celdaSinBorde() {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return celda;
    }

    private static Paragraph derecha(Paragraph p) {
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static String valor(String s, String porDefecto) {
        return s == null || s.isBlank() ? porDefecto : s;
    }
}
