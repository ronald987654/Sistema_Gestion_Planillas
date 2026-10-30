package com.planillaperu.export.doc;

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
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDocDefaults;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHeightRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalJc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RonaldmichaBoletaWord {

    private static final String FUENTE = "Arial";
    private static final String COLOR = Boleta.COLOR.substring(1);
    private static final String BORDE = Boleta.BORDE.substring(1);
    private static final String GRIS = "757575";
    private static final String ROJO = "C62828";
    private static final int LARGO = 16838;
    private static final int CORTO = 11906;
    private static final int MARGEN = 567;
    private static final int ANCHO = LARGO - 2 * MARGEN;
    private static final int CM = 567;
    private static final int HUECO = 180;

    private record Estilo(double valor, double encabezado, int relleno) {

        static Estilo para(int filasCuerpo) {
            if (filasCuerpo <= 11) {
                return new Estilo(8.5, 8, 30);
            }
            if (filasCuerpo <= 15) {
                return new Estilo(8.5, 8, 0);
            }
            return new Estilo(7.5, 7, 0);
        }
    }

    private RonaldmichaBoletaWord() {
    }

    static byte[] render(List<RonaldmichaDocumentModel> documentos, RonaldmichaEmpresa e, LocalDateTime generado) {
        try (XWPFDocument doc = new XWPFDocument()) {
            pagina(doc);
            boolean primera = true;
            for (RonaldmichaDocumentModel d : documentos) {
                List<Copia> copias = d.copias().isEmpty() ? Collections.singletonList(null) : d.copias();
                for (Copia copia : copias) {
                    copia(doc, d.boleta(), copia, e, RonaldmichaFormatos.fechaHora(generado), !primera);
                    primera = false;
                }
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            doc.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el Word de la boleta", ex);
        }
    }

    private static void copia(XWPFDocument doc, Boleta b, Copia copia, RonaldmichaEmpresa e, String emitida, boolean saltoDePagina) {
        Estilo s = Estilo.para(b.filasCuerpo());
        if (saltoDePagina) {
            XWPFParagraph salto = parrafo(doc, ParagraphAlignment.LEFT);
            salto.setPageBreak(true);
            salto.setSpacingBetween(2, org.apache.poi.xwpf.usermodel.LineSpacingRule.EXACT);
        }
        encabezado(doc, b, copia, e, s);
        for (FilaDatos f : b.datos()) {
            espacio(doc, 60);
            datos(doc, f, s);
        }
        espacio(doc, 80);
        conceptos(doc, b, s);
        totales(doc, b);
        XWPFParagraph son = parrafo(doc, ParagraphAlignment.LEFT);
        son.setSpacingBefore(40);
        run(son, "Son: " + b.enLetras(), true, s.valor(), null);
        if (b.leyendaCese() != null) {
            XWPFParagraph cese = parrafo(doc, ParagraphAlignment.LEFT);
            run(cese, b.leyendaCese(), false, 8, null).setItalic(true);
        }
        espacio(doc, (int) (0.6 * CM));
        firmas(doc, b.firmas(), copia, e);
        XWPFParagraph leyenda = parrafo(doc, ParagraphAlignment.CENTER);
        leyenda.setSpacingBefore(60);
        run(leyenda, Boleta.leyendaFinal(emitida), false, 7, GRIS);
    }

    private static void encabezado(XWPFDocument doc, Boleta b, Copia copia, RonaldmichaEmpresa e, Estilo s) {
        int[] anchos = {(int) (ANCHO * 0.22), (int) (ANCHO * 0.53), ANCHO - (int) (ANCHO * 0.22) - (int) (ANCHO * 0.53)};
        XWPFTable t = tabla(doc, anchos);
        XWPFTableRow fila = fila(t, anchos, new int[]{1, 1, 1});
        XWPFParagraph logo = primerParrafo(fila.getCell(0), ParagraphAlignment.LEFT);
        imagen(run(logo, "", false, 8, null), e.logo(), 4.5 * CM, 1.6 * CM, "logo");
        fila.getCell(0).setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);

        XWPFTableCell centro = fila.getCell(1);
        run(primerParrafo(centro, ParagraphAlignment.CENTER), Boleta.TITULO, true, 15, null);
        run(parrafo(centro, ParagraphAlignment.CENTER), Boleta.NORMA, false, 8.5, null);
        XWPFRun mes = run(parrafo(centro, ParagraphAlignment.CENTER), b.mes(), true, 10, null);
        mes.setUnderline(UnderlinePatterns.SINGLE);

        XWPFTableCell derecha = fila.getCell(2);
        XWPFParagraph rotulo = primerParrafo(derecha, ParagraphAlignment.RIGHT);
        if (copia != null) {
            run(rotulo, copia.rotulo(), true, 8, null);
            rotulo = parrafo(derecha, ParagraphAlignment.RIGHT);
        }
        run(rotulo, "N° " + b.numero(), true, 10, null);
        run(parrafo(derecha, ParagraphAlignment.RIGHT), "Fecha de pago: " + b.fechaPago(), false, s.valor(), null);
        quitarFilaInicial(t);
    }

    private static void datos(XWPFDocument doc, FilaDatos f, Estilo s) {
        List<Integer> anchos = new ArrayList<>();
        List<Integer> grupoDeColumna = new ArrayList<>();
        float pesos = 0;
        for (GrupoDatos g : f.grupos()) {
            pesos += g.ancho();
        }
        int util = ANCHO - HUECO * (f.grupos().size() - 1);
        for (int i = 0; i < f.grupos().size(); i++) {
            GrupoDatos g = f.grupos().get(i);
            if (i > 0) {
                anchos.add(HUECO);
                grupoDeColumna.add(-1);
            }
            float suma = 0;
            for (Campo c : g.campos()) {
                suma += c.ancho();
            }
            for (Campo c : g.campos()) {
                anchos.add((int) (util * g.ancho() / pesos * c.ancho() / suma));
                grupoDeColumna.add(i);
            }
        }
        int[] grilla = anchos.stream().mapToInt(Integer::intValue).toArray();
        XWPFTable t = tabla(doc, grilla);
        if (f.grupos().stream().anyMatch(g -> g.titulo() != null)) {
            List<Integer> spans = new ArrayList<>();
            for (int i = 0; i < f.grupos().size(); i++) {
                if (i > 0) {
                    spans.add(1);
                }
                spans.add(f.grupos().get(i).campos().size());
            }
            XWPFTableRow barra = fila(t, grilla, spans.stream().mapToInt(Integer::intValue).toArray());
            int celda = 0;
            for (int i = 0; i < f.grupos().size(); i++) {
                if (i > 0) {
                    sinBordes(barra.getCell(celda++));
                }
                String titulo = f.grupos().get(i).titulo();
                cabecera(barra.getCell(celda++), titulo == null ? "" : titulo, ParagraphAlignment.LEFT, s);
            }
        }
        int[] simples = new int[grilla.length];
        java.util.Arrays.fill(simples, 1);
        XWPFTableRow encabezados = fila(t, grilla, simples);
        XWPFTableRow valores = fila(t, grilla, simples);
        int columna = 0;
        for (int i = 0; i < f.grupos().size(); i++) {
            if (i > 0) {
                sinBordes(encabezados.getCell(columna));
                sinBordes(valores.getCell(columna));
                columna++;
            }
            for (Campo c : f.grupos().get(i).campos()) {
                cabecera(encabezados.getCell(columna), c.etiqueta(), ParagraphAlignment.CENTER, s);
                XWPFTableCell v = valores.getCell(columna);
                bordes(v, true, true, true, true);
                margen(v, s);
                run(primerParrafo(v, ParagraphAlignment.CENTER), c.valor(), c.valor().contains(Boleta.COMPLETAR),
                        s.valor(), c.valor().contains(Boleta.COMPLETAR) ? ROJO : null);
                columna++;
            }
        }
        quitarFilaInicial(t);
    }

    private static void conceptos(XWPFDocument doc, Boleta b, Estilo s) {
        int[] grilla = anchosCuerpo();
        XWPFTable t = tabla(doc, grilla);
        List<Conceptos> columnas = b.cuerpo();
        XWPFTableRow barra = fila(t, grilla, new int[]{2, 1, 3, 1, 3});
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                sinBordes(barra.getCell(i * 2 - 1));
            }
            cabecera(barra.getCell(i * 2), columnas.get(i).titulo(), ParagraphAlignment.CENTER, s);
        }
        int[] simples = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
        XWPFTableRow encabezados = fila(t, grilla, simples);
        int[] inicio = {0, 3, 7};
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                sinBordes(encabezados.getCell(inicio[i] - 1));
            }
            cabecera(encabezados.getCell(inicio[i]), "CONCEPTO", ParagraphAlignment.LEFT, s);
            cabecera(encabezados.getCell(inicio[i] + 1), "MONTO", ParagraphAlignment.RIGHT, s);
            if (columnas.get(i).conPorcentaje()) {
                cabecera(encabezados.getCell(inicio[i] + 2), "%", ParagraphAlignment.CENTER, s);
            }
        }
        int filas = b.filasCuerpo();
        for (int f = 0; f < filas; f++) {
            boolean ultima = f == filas - 1;
            XWPFTableRow r = fila(t, grilla, simples);
            for (int i = 0; i < 3; i++) {
                if (i > 0) {
                    sinBordes(r.getCell(inicio[i] - 1));
                }
                Conceptos c = columnas.get(i);
                LineaBoleta l = f < c.lineas().size() ? c.lineas().get(f) : null;
                XWPFTableCell concepto = r.getCell(inicio[i]);
                bordes(concepto, false, ultima, true, false);
                margen(concepto, s);
                XWPFParagraph p = primerParrafo(concepto, ParagraphAlignment.LEFT);
                if (l != null) {
                    if (l.codigo() != null) {
                        run(p, l.codigo() + "  ", false, 7, GRIS);
                    }
                    run(p, l.concepto(), false, s.valor(), null);
                }
                XWPFTableCell monto = r.getCell(inicio[i] + 1);
                bordes(monto, false, ultima, false, !c.conPorcentaje());
                margen(monto, s);
                run(primerParrafo(monto, ParagraphAlignment.RIGHT), l == null ? "" : RonaldmichaFormatos.numero(l.monto()), false,
                        s.valor(), null);
                if (c.conPorcentaje()) {
                    XWPFTableCell pct = r.getCell(inicio[i] + 2);
                    bordes(pct, false, ultima, false, true);
                    margen(pct, s);
                    run(primerParrafo(pct, ParagraphAlignment.CENTER),
                            l == null || l.porcentaje() == null ? "" : l.porcentaje(), false, s.valor(), null);
                }
            }
        }
        quitarFilaInicial(t);
    }

    private static void totales(XWPFDocument doc, Boleta b) {
        int[] grilla = anchosCuerpo();
        XWPFTable t = tabla(doc, grilla);
        XWPFTableRow r = fila(t, grilla, new int[]{1, 1, 1, 1, 1, 1, 1, 1, 2});
        String[] textos = {"Total Remuneraciones", RonaldmichaFormatos.numero(b.totalRemuneraciones()), "", "Total Descuentos",
                RonaldmichaFormatos.numero(b.totalDescuentos()), "", "", "Neto a Pagar", RonaldmichaFormatos.moneda(b.neto())};
        for (int i = 0; i < textos.length; i++) {
            XWPFTableCell c = r.getCell(i);
            c.setColor(Boleta.FONDO_TOTALES.substring(1));
            c.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            sinBordes(c);
            boolean monto = i == 1 || i == 4 || i == 8;
            run(primerParrafo(c, monto ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT), textos[i], true,
                    i >= 7 ? 11 : 9, Boleta.TEXTO_TOTALES.substring(1));
        }
        t.setCellMargins(50, 80, 50, 80);
        quitarFilaInicial(t);
    }

    private static int[] anchosCuerpo() {
        int tercio = (ANCHO - 2 * HUECO) / 3;
        return new int[]{(int) (tercio * 0.72), tercio - (int) (tercio * 0.72), HUECO, (int) (tercio * 0.6),
                (int) (tercio * 0.24), tercio - (int) (tercio * 0.6) - (int) (tercio * 0.24), HUECO,
                (int) (tercio * 0.6), (int) (tercio * 0.24), tercio - (int) (tercio * 0.6) - (int) (tercio * 0.24)};
    }

    private static void firmas(XWPFDocument doc, FirmasBoleta f, Copia copia, RonaldmichaEmpresa e) {
        int huella = (int) (2.5 * CM);
        int[] grilla;
        if (f.huella()) {
            int firma = (int) (ANCHO * 0.4);
            int resto = ANCHO - 2 * firma - huella;
            grilla = new int[]{firma, firma, resto / 2, huella, resto - resto / 2};
        } else {
            grilla = new int[]{ANCHO / 2, ANCHO - ANCHO / 2};
        }
        int[] simples = new int[grilla.length];
        java.util.Arrays.fill(simples, 1);
        XWPFTable t = tabla(doc, grilla);
        XWPFTableRow espacio = fila(t, grilla, simples);
        alto(espacio, (int) (2.5 * CM));
        XWPFTableRow linea = fila(t, grilla, simples);
        alto(linea, CM / 2);
        XWPFTableRow textos = fila(t, grilla, simples);
        for (XWPFTableRow r : List.of(espacio, linea, textos)) {
            r.getTableCells().forEach(RonaldmichaBoletaWord::sinBordes);
            r.setCantSplitRow(true);
        }

        XWPFTableCell espacioEmpleador = espacio.getCell(0);
        espacioEmpleador.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.BOTTOM);
        if (f.firmaDigital()) {
            imagen(run(primerParrafo(espacioEmpleador, ParagraphAlignment.CENTER), "", false, 8, null), e.firma(),
                    grilla[0] * 0.9, 2 * CM, "firma");
        }
        for (int i = 0; i < 2; i++) {
            XWPFParagraph trazo = primerParrafo(linea.getCell(i), ParagraphAlignment.CENTER);
            int sangria = Math.max(0, (grilla[i] - 6 * CM) / 2);
            trazo.setIndentationLeft(sangria);
            trazo.setIndentationRight(sangria);
            trazo.setBorderTop(Borders.SINGLE);
        }

        boolean faltaRep = f.representante().contains(Boleta.COMPLETAR);
        XWPFTableCell empleador = textos.getCell(0);
        run(primerParrafo(empleador, ParagraphAlignment.CENTER), f.representante(), true, 8.5, faltaRep ? ROJO : null);
        boolean faltaDoc = f.documentoRepresentante().contains(Boleta.COMPLETAR);
        run(parrafo(empleador, ParagraphAlignment.CENTER), f.documentoRepresentante(), faltaDoc, 8, faltaDoc ? ROJO : null);
        run(parrafo(empleador, ParagraphAlignment.CENTER), f.cargoRepresentante(), false, 8, null);
        run(parrafo(empleador, ParagraphAlignment.CENTER), "Firma y sello del empleador", false, 7, GRIS);

        XWPFTableCell trabajador = textos.getCell(1);
        run(primerParrafo(trabajador, ParagraphAlignment.CENTER), f.trabajador(), true, 8.5, null);
        run(parrafo(trabajador, ParagraphAlignment.CENTER), f.documentoTrabajador(), false, 8, null);
        run(parrafo(trabajador, ParagraphAlignment.CENTER), "Recibí conforme", false, 8, null);
        run(parrafo(trabajador, ParagraphAlignment.CENTER), "Fecha de recepción: ____/____/________", false, 8, null);
        String nota = Boleta.notaCopia(copia);
        if (nota != null) {
            run(parrafo(trabajador, ParagraphAlignment.CENTER), nota, false, 7, GRIS);
        }

        if (f.huella()) {
            bordes(espacio.getCell(3), true, false, true, true);
            bordes(linea.getCell(3), false, true, true, true);
            run(primerParrafo(textos.getCell(3), ParagraphAlignment.CENTER), "Huella digital", false, 7, GRIS);
        }
        quitarFilaInicial(t);
    }

    private static void pagina(XWPFDocument doc) {
        CTStyles estilos = CTStyles.Factory.newInstance();
        CTDocDefaults defecto = estilos.addNewDocDefaults();
        CTRPr letra = defecto.addNewRPrDefault().addNewRPr();
        CTFonts fuentes = letra.addNewRFonts();
        fuentes.setAscii(FUENTE);
        fuentes.setHAnsi(FUENTE);
        fuentes.setCs(FUENTE);
        letra.addNewSz().setVal(BigInteger.valueOf(14));
        CTSpacing espaciado = defecto.addNewPPrDefault().addNewPPr().addNewSpacing();
        espaciado.setBefore(BigInteger.ZERO);
        espaciado.setAfter(BigInteger.ZERO);
        espaciado.setLine(BigInteger.valueOf(240));
        espaciado.setLineRule(STLineSpacingRule.AUTO);
        doc.createStyles().setStyles(estilos);
        CTSectPr sect = doc.getDocument().getBody().isSetSectPr() ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz tamano = sect.isSetPgSz() ? sect.getPgSz() : sect.addNewPgSz();
        tamano.setW(BigInteger.valueOf(LARGO));
        tamano.setH(BigInteger.valueOf(CORTO));
        tamano.setOrient(STPageOrientation.LANDSCAPE);
        CTPageMar margen = sect.isSetPgMar() ? sect.getPgMar() : sect.addNewPgMar();
        BigInteger m = BigInteger.valueOf(MARGEN);
        margen.setTop(m);
        margen.setBottom(m);
        margen.setLeft(m);
        margen.setRight(m);
        margen.setHeader(BigInteger.ZERO);
        margen.setFooter(BigInteger.ZERO);
    }

    private static XWPFTable tabla(XWPFDocument doc, int[] anchos) {
        XWPFTable t = doc.createTable();
        CTTblPr pr = t.getCTTbl().getTblPr() == null ? t.getCTTbl().addNewTblPr() : t.getCTTbl().getTblPr();
        (pr.isSetTblLayout() ? pr.getTblLayout() : pr.addNewTblLayout()).setType(STTblLayoutType.FIXED);
        t.setWidth(String.valueOf(ANCHO));
        t.setWidthType(org.apache.poi.xwpf.usermodel.TableWidthType.DXA);
        CTTblGrid grid = t.getCTTbl().getTblGrid() == null ? t.getCTTbl().addNewTblGrid() : t.getCTTbl().getTblGrid();
        while (grid.sizeOfGridColArray() > 0) {
            grid.removeGridCol(0);
        }
        for (int a : anchos) {
            grid.addNewGridCol().setW(BigInteger.valueOf(a));
        }
        t.removeBorders();
        t.setCellMargins(0, 60, 0, 60);
        return t;
    }

    private static XWPFTableRow fila(XWPFTable t, int[] grilla, int[] spans) {
        XWPFTableRow r = t.insertNewTableRow(t.getNumberOfRows());
        int columna = 0;
        for (int span : spans) {
            XWPFTableCell c = r.addNewTableCell();
            CTTcPr pr = c.getCTTc().isSetTcPr() ? c.getCTTc().getTcPr() : c.getCTTc().addNewTcPr();
            int ancho = 0;
            for (int i = 0; i < span; i++) {
                ancho += grilla[columna + i];
            }
            pr.addNewTcW().setW(BigInteger.valueOf(ancho));
            pr.getTcW().setType(STTblWidth.DXA);
            if (span > 1) {
                pr.addNewGridSpan().setVal(BigInteger.valueOf(span));
            }
            columna += span;
            if (c.getParagraphs().isEmpty()) {
                c.addParagraph();
            }
            espaciado(c.getParagraphs().getFirst());
        }
        return r;
    }

    private static void quitarFilaInicial(XWPFTable t) {
        if (t.getNumberOfRows() > 1 && t.getRow(0).getTableCells().size() <= 1
                && t.getRow(0).getCell(0).getText().isEmpty()) {
            t.removeRow(0);
        }
    }

    private static void alto(XWPFTableRow r, int twips) {
        r.setHeight(twips);
        CTTrPr pr = r.getCtRow().isSetTrPr() ? r.getCtRow().getTrPr() : r.getCtRow().addNewTrPr();
        pr.getTrHeightArray(0).setHRule(STHeightRule.EXACT);
    }

    private static void cabecera(XWPFTableCell c, String texto, ParagraphAlignment alineacion, Estilo s) {
        c.setColor(COLOR);
        c.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        bordes(c, true, true, true, true);
        margen(c, s);
        run(primerParrafo(c, alineacion), texto.toUpperCase(RonaldmichaFormatos.LOCALE_PE), true, s.encabezado(), "FFFFFF");
    }

    private static void margen(XWPFTableCell c, Estilo s) {
        CTTcPr pr = c.getCTTc().isSetTcPr() ? c.getCTTc().getTcPr() : c.getCTTc().addNewTcPr();
        var mar = pr.isSetTcMar() ? pr.getTcMar() : pr.addNewTcMar();
        (mar.isSetTop() ? mar.getTop() : mar.addNewTop()).setW(BigInteger.valueOf(s.relleno()));
        mar.getTop().setType(STTblWidth.DXA);
        (mar.isSetBottom() ? mar.getBottom() : mar.addNewBottom()).setW(BigInteger.valueOf(s.relleno()));
        mar.getBottom().setType(STTblWidth.DXA);
        (pr.isSetVAlign() ? pr.getVAlign() : pr.addNewVAlign()).setVal(STVerticalJc.CENTER);
    }

    private static void sinBordes(XWPFTableCell c) {
        bordes(c, false, false, false, false);
    }

    private static void bordes(XWPFTableCell c, boolean arriba, boolean abajo, boolean izquierda, boolean derecha) {
        CTTcPr pr = c.getCTTc().isSetTcPr() ? c.getCTTc().getTcPr() : c.getCTTc().addNewTcPr();
        CTTcBorders b = pr.isSetTcBorders() ? pr.getTcBorders() : pr.addNewTcBorders();
        lado(b.isSetTop() ? b.getTop() : b.addNewTop(), arriba);
        lado(b.isSetBottom() ? b.getBottom() : b.addNewBottom(), abajo);
        lado(b.isSetLeft() ? b.getLeft() : b.addNewLeft(), izquierda);
        lado(b.isSetRight() ? b.getRight() : b.addNewRight(), derecha);
    }

    private static void lado(CTBorder borde, boolean visible) {
        borde.setVal(visible ? STBorder.SINGLE : STBorder.NIL);
        if (visible) {
            borde.setSz(BigInteger.valueOf(4));
            borde.setColor(BORDE);
        }
    }

    private static XWPFParagraph primerParrafo(XWPFTableCell c, ParagraphAlignment alineacion) {
        XWPFParagraph p = c.getParagraphs().getFirst();
        p.setAlignment(alineacion);
        espaciado(p);
        return p;
    }

    private static XWPFParagraph parrafo(XWPFTableCell c, ParagraphAlignment alineacion) {
        XWPFParagraph p = c.addParagraph();
        p.setAlignment(alineacion);
        espaciado(p);
        return p;
    }

    private static XWPFParagraph parrafo(XWPFDocument doc, ParagraphAlignment alineacion) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(alineacion);
        espaciado(p);
        return p;
    }

    private static void espacio(XWPFDocument doc, int twips) {
        XWPFParagraph p = parrafo(doc, ParagraphAlignment.LEFT);
        p.setSpacingBetween(twips / 20.0, org.apache.poi.xwpf.usermodel.LineSpacingRule.EXACT);
        run(p, "", false, 2, null);
    }

    private static void espaciado(XWPFParagraph p) {
        p.setSpacingBefore(0);
        p.setSpacingAfter(0);
    }

    private static XWPFRun run(XWPFParagraph p, String texto, boolean negrita, double tamano, String color) {
        XWPFRun r = p.createRun();
        r.setText(texto);
        r.setBold(negrita);
        r.setFontFamily(FUENTE);
        r.setFontSize(tamano);
        if (color != null) {
            r.setColor(color);
        }
        return r;
    }

    private static void imagen(XWPFRun run, byte[] bytes, double ancho, double alto, String nombre) {
        if (bytes == null || bytes.length < 4) {
            return;
        }
        boolean png = (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P';
        boolean jpg = (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8;
        if (!png && !jpg) {
            return;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null || img.getWidth() == 0 || img.getHeight() == 0) {
                return;
            }
            double escala = Math.min(ancho / img.getWidth(), alto / img.getHeight());
            run.addPicture(new ByteArrayInputStream(bytes), png ? Document.PICTURE_TYPE_PNG : Document.PICTURE_TYPE_JPEG,
                    nombre, Units.toEMU(img.getWidth() * escala / 20), Units.toEMU(img.getHeight() * escala / 20));
        } catch (IOException | InvalidFormatException | RuntimeException ex) {
        }
    }
}
