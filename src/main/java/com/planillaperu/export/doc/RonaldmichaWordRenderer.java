package com.planillaperu.export.doc;

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
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Table;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Total;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Tri;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RonaldmichaWordRenderer {

    private static final String FUENTE = "Arial";
    private static final String VERDE = "0E5A47";
    private static final String FONDO = RonaldmichaDocumentModel.Boleta.FONDO_TOTALES.substring(1);

    private RonaldmichaWordRenderer() {
    }

    public static byte[] render(RonaldmichaDocumentModel d, RonaldmichaEmpresa e, Optional<String> aviso, LocalDateTime generado) {
        return renderVarios(List.of(d), d.archivo(), e, aviso, generado);
    }

    public static byte[] renderVarios(List<RonaldmichaDocumentModel> documentos, String archivo, RonaldmichaEmpresa e, Optional<String> aviso,
                                      LocalDateTime generado) {
        try (XWPFDocument doc = new XWPFDocument()) {
            boolean horizontal = !documentos.isEmpty() && documentos.getFirst().orientacion() == Orientacion.HORIZONTAL
                    && documentos.getFirst().copias().isEmpty();
            if (!documentos.isEmpty() && documentos.stream().allMatch(RonaldmichaDocumentModel::esBoleta)) {
                return RonaldmichaBoletaWord.render(documentos, e, generado);
            }
            pagina(doc, horizontal);
            encabezado(doc, e);
            pie(doc, "Planilla Perú · " + (archivo == null ? "documento" : archivo) + " · " + RonaldmichaFormatos.fechaHora(generado));
            if (documentos.isEmpty()) {
                texto(doc.createParagraph(), "No hay documentos para el filtro elegido.", false, 10);
            }
            boolean primera = true;
            for (RonaldmichaDocumentModel d : documentos) {
                List<Copia> copias = d.copias().isEmpty() ? Collections.singletonList(null) : d.copias();
                for (Copia copia : copias) {
                    XWPFParagraph inicio = doc.createParagraph();
                    if (!primera) {
                        inicio.setPageBreak(true);
                    }
                    primera = false;
                    aviso.ifPresent(a -> texto(inicio, "Aviso: " + a, true, 8));
                    if (copia != null) {
                        XWPFParagraph rotulo = doc.createParagraph();
                        rotulo.setAlignment(ParagraphAlignment.RIGHT);
                        texto(rotulo, copia.rotulo(), true, 9);
                    }
                    XWPFParagraph titulo = doc.createParagraph();
                    titulo.setAlignment(ParagraphAlignment.CENTER);
                    texto(titulo, d.titulo(), true, 14);
                    if (d.subtitulo() != null && !d.subtitulo().isBlank()) {
                        XWPFParagraph sub = doc.createParagraph();
                        sub.setAlignment(ParagraphAlignment.CENTER);
                        texto(sub, d.subtitulo(), false, 9);
                    }
                    for (Bloque b : d.bloques()) {
                        bloque(doc, b, e);
                    }
                }
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            doc.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el Word", ex);
        }
    }

    private static void bloque(XWPFDocument doc, Bloque b, RonaldmichaEmpresa e) {
        switch (b) {
            case Heading h -> {
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingBefore(160);
                XWPFRun r = texto(p, h.texto(), true, 11);
                r.setColor(VERDE);
            }
            case RonaldmichaDocumentModel.Paragraph p -> {
                XWPFParagraph par = doc.createParagraph();
                par.setAlignment(ParagraphAlignment.BOTH);
                par.setSpacingAfter(120);
                texto(par, p.texto(), false, 10);
            }
            case KeyValue kv -> {
                int filas = (kv.pares().size() + 1) / 2;
                XWPFTable t = tabla(doc, filas, 4);
                for (int i = 0; i < kv.pares().size(); i++) {
                    XWPFTableRow fila = t.getRow(i / 2);
                    int col = (i % 2) * 2;
                    celda(fila, col, kv.pares().get(i).etiqueta(), false, false);
                    celda(fila, col + 1, kv.pares().get(i).valor(), true, false);
                }
            }
            case Table tb -> {
                int columnas = Math.max(1, tb.encabezados().size());
                XWPFTable t = tabla(doc, tb.filas().size() + 1 + (tb.pie().isEmpty() ? 0 : 1), columnas);
                for (int i = 0; i < tb.encabezados().size(); i++) {
                    encabezado(t.getRow(0), i, tb.encabezados().get(i), tb.numerica(i));
                }
                for (int f = 0; f < tb.filas().size(); f++) {
                    List<String> fila = tb.filas().get(f);
                    for (int i = 0; i < columnas; i++) {
                        celda(t.getRow(f + 1), i, i < fila.size() ? fila.get(i) : "", false, tb.numerica(i));
                    }
                }
                if (!tb.pie().isEmpty()) {
                    XWPFTableRow pie = t.getRow(tb.filas().size() + 1);
                    for (int i = 0; i < columnas; i++) {
                        celda(pie, i, i < tb.pie().size() ? tb.pie().get(i) : "", true, tb.numerica(i));
                        pie.getCell(i).setColor(FONDO);
                    }
                }
            }
            case Tri tri -> {
                int filas = tri.columnas().stream().mapToInt(c -> c.items().size()).max().orElse(0);
                XWPFTable t = tabla(doc, filas + 2, 6);
                for (int c = 0; c < 3; c++) {
                    Columna col = tri.columnas().get(c);
                    encabezado(t.getRow(0), c * 2, col.titulo(), false);
                    t.getRow(0).getCell(c * 2 + 1).setColor(VERDE);
                    for (int i = 0; i < col.items().size(); i++) {
                        Item it = col.items().get(i);
                        celda(t.getRow(i + 1), c * 2, (it.codigo() == null ? "" : it.codigo() + " ") + it.concepto(),
                                false, false);
                        celda(t.getRow(i + 1), c * 2 + 1, RonaldmichaFormatos.numero(it.monto()), false, true);
                    }
                    celda(t.getRow(filas + 1), c * 2, "Total", true, false);
                    celda(t.getRow(filas + 1), c * 2 + 1, RonaldmichaFormatos.numero(col.total()), true, true);
                }
            }
            case Total tot -> {
                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.RIGHT);
                p.setSpacingBefore(160);
                XWPFRun r = texto(p, tot.etiqueta() + ": " + RonaldmichaFormatos.moneda(tot.monto()), true, 13);
                r.setColor(VERDE);
                if (tot.enLetras() != null) {
                    texto(doc.createParagraph(), "Son: " + tot.enLetras(), true, 10);
                }
            }
            case RonaldmichaDocumentModel.Boleta x -> throw new IllegalStateException("La boleta se dibuja con BoletaWord");
            case Firmas fs -> {
                XWPFTable t = doc.createTable(1, Math.max(1, fs.firmas().size()));
                t.removeBorders();
                t.setWidth("100%");
                for (int i = 0; i < fs.firmas().size(); i++) {
                    firma(t.getRow(0), i, fs.firmas().get(i), e);
                }
            }
        }
    }

    private static void firma(XWPFTableRow fila, int columna, Firma f, RonaldmichaEmpresa e) {
        XWPFParagraph p = fila.getCell(columna).getParagraphs().getFirst();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setFontFamily(FUENTE);
        run.setFontSize(9);
        boolean conImagen = f.representante() && imagen(run, e.firma());
        if (conImagen) {
            run.addBreak();
        } else {
            p.setSpacingBefore(840);
        }
        run.setText("______________________________");
        for (String linea : new String[]{f.nombre(), f.detalle()}) {
            if (linea != null && !linea.isBlank()) {
                run.addBreak();
                run.setText(linea);
            }
        }
    }

    private static boolean imagen(XWPFRun run, byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        boolean png = (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P';
        boolean jpg = (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8;
        if (!png && !jpg) {
            return false;
        }
        try {
            run.addPicture(new ByteArrayInputStream(bytes), png ? Document.PICTURE_TYPE_PNG : Document.PICTURE_TYPE_JPEG,
                    "firma", Units.toEMU(120), Units.toEMU(40));
            return true;
        } catch (IOException | InvalidFormatException ex) {
            return false;
        }
    }

    private static void pagina(XWPFDocument doc, boolean horizontal) {
        CTSectPr sect = doc.getDocument().getBody().isSetSectPr() ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz tamano = sect.isSetPgSz() ? sect.getPgSz() : sect.addNewPgSz();
        BigInteger corto = BigInteger.valueOf(11906);
        BigInteger largo = BigInteger.valueOf(16838);
        tamano.setW(horizontal ? largo : corto);
        tamano.setH(horizontal ? corto : largo);
        if (horizontal) {
            tamano.setOrient(STPageOrientation.LANDSCAPE);
        }
    }

    private static void encabezado(XWPFDocument doc, RonaldmichaEmpresa e) {
        XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph p = header.createParagraph();
        p.setAlignment(ParagraphAlignment.RIGHT);
        p.setBorderBottom(Borders.SINGLE);
        XWPFRun nombre = texto(p, valor(e.razonSocial(), "Empresa sin registrar"), true, 10);
        nombre.addBreak();
        String domicilio = Stream.of(e.direccion(), e.distrito(), e.provincia(), e.departamento())
                .filter(x -> x != null && !x.isBlank()).collect(Collectors.joining(" · "));
        texto(p, "RUC " + valor(e.ruc(), "—") + (domicilio.isEmpty() ? "" : " · " + domicilio), false, 8);
    }

    private static void pie(XWPFDocument doc, String texto) {
        XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph p = footer.createParagraph();
        texto(p, texto + " · Página ", false, 7);
        p.getCTP().addNewFldSimple().setInstr("PAGE");
        texto(p, " de ", false, 7);
        p.getCTP().addNewFldSimple().setInstr("NUMPAGES");
    }

    private static XWPFTable tabla(XWPFDocument doc, int filas, int columnas) {
        XWPFTable t = doc.createTable(Math.max(1, filas), columnas);
        t.setWidth("100%");
        return t;
    }

    private static void encabezado(XWPFTableRow fila, int columna, String valor, boolean derecha) {
        celda(fila, columna, valor, true, derecha);
        fila.getCell(columna).setColor(VERDE);
        fila.getCell(columna).getParagraphs().getFirst().getRuns().forEach(r -> r.setColor("FFFFFF"));
    }

    private static void celda(XWPFTableRow fila, int columna, String valor, boolean negrita, boolean derecha) {
        XWPFParagraph p = fila.getCell(columna).getParagraphs().getFirst();
        if (derecha) {
            p.setAlignment(ParagraphAlignment.RIGHT);
        }
        texto(p, valor == null ? "" : valor, negrita, 9);
    }

    private static XWPFRun texto(XWPFParagraph p, String valor, boolean negrita, int tamano) {
        XWPFRun run = p.createRun();
        run.setText(valor);
        run.setBold(negrita);
        run.setFontSize(tamano);
        run.setFontFamily(FUENTE);
        return run;
    }

    private static String valor(String s, String porDefecto) {
        return s == null || s.isBlank() ? porDefecto : s;
    }
}
