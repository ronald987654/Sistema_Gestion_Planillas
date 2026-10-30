package com.planillaperu.export.doc;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Columna;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Firma;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Firmas;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Heading;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Item;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.KeyValue;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Orientacion;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Par;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Paragraph;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Table;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Total;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Tri;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaRenderersTest {

    private static final LocalDateTime GENERADO = LocalDateTime.of(2026, 10, 15, 10, 30);
    private static final RonaldmichaEmpresa EMPRESA = new RonaldmichaEmpresa(1L, "20600000005", "Empresa de Ejemplo S.A.C.", null, null,
            null, null, "Av. Ejemplo 123", "Lima", "Lima", "Miraflores", null, "Lima", null, null, null, null, null,
            false, "Juan Pérez", "DNI", "40000009", "Gerente general", null, null, null, null, null, null);

    @Test
    void pdfConCopiasLadoALadoYPie() throws IOException {
        byte[] pdf = RonaldmichaPdfRenderer.render(modelo(RonaldmichaDocumentModel.DOS_COPIAS), EMPRESA, Optional.empty(), GENERADO);

        PdfReader lector = new PdfReader(pdf);
        assertThat(lector.getNumberOfPages()).isEqualTo(1);
        assertThat(lector.getPageSizeWithRotation(1).getWidth()).isGreaterThan(lector.getPageSizeWithRotation(1).getHeight());
        String texto = new PdfTextExtractor(lector).getTextFromPage(1);
        assertThat(texto).contains("COPIA EMPLEADOR", "COPIA TRABAJADOR", "Empresa de Ejemplo S.A.C.", "RUC 20600000005",
                "BOLETA DE PRUEBA", "0121", "Mil trescientos sesenta y cuatro con 07/100 soles",
                "Planilla Perú · boleta.pdf · 15/10/2026 10:30 · Página 1 de 1");
        lector.close();
    }

    @Test
    void pdfSinCopiasVerticalYVariosDocumentos() throws IOException {
        RonaldmichaDocumentModel d = modelo(List.of());
        byte[] pdf = RonaldmichaPdfRenderer.renderVarios(List.of(d, d), "todos.pdf", EMPRESA, Optional.of("faltan datos"), GENERADO);

        PdfReader lector = new PdfReader(pdf);
        assertThat(lector.getNumberOfPages()).isEqualTo(2);
        assertThat(lector.getPageSizeWithRotation(1).getHeight()).isGreaterThan(lector.getPageSizeWithRotation(1).getWidth());
        String texto = new PdfTextExtractor(lector).getTextFromPage(2);
        assertThat(texto).contains("Aviso: faltan datos", "Página 2 de 2").doesNotContain("COPIA EMPLEADOR");
        lector.close();
    }

    @Test
    void wordEditableConUnaCopiaPorPagina() throws IOException {
        byte[] word = RonaldmichaWordRenderer.render(modelo(RonaldmichaDocumentModel.DOS_COPIAS), EMPRESA, Optional.empty(), GENERADO);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word))) {
            String texto = doc.getParagraphs().stream().map(XWPFParagraph::getText).collect(Collectors.joining("\n"));
            assertThat(texto).contains("COPIA EMPLEADOR", "COPIA TRABAJADOR", "BOLETA DE PRUEBA",
                    "Son: Mil trescientos sesenta y cuatro con 07/100 soles");
            assertThat(doc.getParagraphs().stream().filter(XWPFParagraph::isPageBreak)).hasSize(1);
            assertThat(doc.getTables()).hasSize(8);
            assertThat(doc.getHeaderList().getFirst().getText()).contains("Empresa de Ejemplo S.A.C.");
            assertThat(doc.getFooterList().getFirst().getText()).contains("Planilla Perú · boleta.pdf");
        }
    }

    private static RonaldmichaDocumentModel modelo(List<RonaldmichaDocumentModel.Copia> copias) {
        BigDecimal neto = new BigDecimal("1364.07");
        return new RonaldmichaDocumentModel("BOLETA DE PRUEBA", "Periodo octubre 2026", copias, Orientacion.VERTICAL, List.of(
                new KeyValue(List.of(new Par("Trabajador", "Quispe Mamani, Rosa Elena"), new Par("Documento", "DNI 40000001"),
                        new Par("Cargo", "Jefa"))),
                new Heading("Días y horas"),
                new Table(List.of("Días", "Horas"), List.of(List.of("30", "240:00")), List.of("30", "240:00"),
                        List.of(true, true)),
                new Tri(new Columna("Ingresos", List.of(new Item("0121", "Remuneración básica", new BigDecimal("1500.00"))),
                        new BigDecimal("1500.00")),
                        new Columna("Descuentos", List.of(new Item("0607", "ONP", new BigDecimal("135.93"))),
                                new BigDecimal("135.93")),
                        new Columna("Aportes del empleador", List.of(), BigDecimal.ZERO)),
                new Total("NETO A PAGAR", neto, "Mil trescientos sesenta y cuatro con 07/100 soles"),
                new Paragraph("Nota de prueba."),
                new Firmas(List.of(new Firma("Juan Pérez", "Empleador", true), new Firma("Rosa Quispe", "Trabajador", false)))),
                "boleta.pdf");
    }
}
