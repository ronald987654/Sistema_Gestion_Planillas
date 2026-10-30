package com.planillaperu.export.doc;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Boleta;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Campo;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Conceptos;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.FilaDatos;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.FirmasBoleta;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.GrupoDatos;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.LineaBoleta;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Orientacion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaBoletaRenderTest {

    private static final LocalDateTime GENERADO = LocalDateTime.of(2026, 10, 22, 9, 15);
    private static final RonaldmichaEmpresa EMPRESA = new RonaldmichaEmpresa(1L, "20600000005", "Empresa de Ejemplo S.A.C.", null, null,
            null, null, "Av. Ejemplo 123", "Lima", "Lima", "Miraflores", null, "Lima", null, null, null, null, null,
            false, "Juan Pérez", "DNI", "40000009", "Gerente general", null, null, null, null, null, null);

    @Test
    void conMuchosConceptosLaFirmaQuedaEnLaMismaPagina() throws IOException {
        RonaldmichaDocumentModel d = modelo(30);

        byte[] pdf = RonaldmichaPdfRenderer.renderVarios(List.of(d, d), "boletas.pdf", EMPRESA, Optional.empty(), GENERADO);

        PdfReader lector = new PdfReader(pdf);
        assertThat(lector.getNumberOfPages()).isEqualTo(4);
        PdfTextExtractor extractor = new PdfTextExtractor(lector);
        for (int i = 1; i <= 4; i++) {
            String texto = extractor.getTextFromPage(i);
            assertThat(texto).as("página %d", i).contains("Concepto 30", "Neto a Pagar", "Recibí conforme",
                    "Boleta emitida el 22/10/2026 09:15 por Planilla Perú", "Página 1 de 1");
        }
        assertThat(extractor.getTextFromPage(1)).contains("COPIA EMPLEADOR");
        assertThat(extractor.getTextFromPage(2)).contains("COPIA TRABAJADOR");
        lector.close();
    }

    @Test
    void elWordTambienSeGeneraConMuchosConceptos() {
        byte[] word = RonaldmichaWordRenderer.render(modelo(30), EMPRESA, Optional.empty(), GENERADO);

        assertThat(word).isNotEmpty();
    }

    private static RonaldmichaDocumentModel modelo(int conceptos) {
        List<LineaBoleta> lineas = new ArrayList<>();
        for (int i = 1; i <= conceptos; i++) {
            lineas.add(new LineaBoleta("0" + (100 + i), "Concepto " + i, new BigDecimal("10.00"), null));
        }
        BigDecimal total = new BigDecimal(10 * conceptos + ".00");
        GrupoDatos empresa = new GrupoDatos("DATOS DE LA EMPRESA", List.of(new Campo("RUC", "20600000005", 1),
                new Campo("RAZÓN SOCIAL", "Empresa de Ejemplo S.A.C.", 2), new Campo("RUBRO DE LA EMPRESA",
                        Boleta.COMPLETAR, 1.5f), new Campo("DIRECCIÓN", "Av. Ejemplo 123 - Miraflores", 3)), 1);
        Boleta b = new Boleta("BOL-202610-T0001", "MES DE OCTUBRE 2026", "31/10/2026", List.of(new FilaDatos(
                List.of(empresa))), new Conceptos("REMUNERACIONES", false, lineas),
                new Conceptos("RETENCIONES / DESCUENTOS", true, List.of(new LineaBoleta("0607", "ONP",
                        BigDecimal.ZERO.setScale(2), "13.00%"))),
                new Conceptos("APORTACIONES DEL EMPLEADOR", true, List.of()), total, BigDecimal.ZERO.setScale(2), total,
                "Trescientos con 00/100 soles", null,
                new FirmasBoleta("Juan Pérez", "DNI 40000009", "Gerente general", "Rosa Quispe", "DNI 40000001", false,
                        true));
        return new RonaldmichaDocumentModel(Boleta.TITULO, null, RonaldmichaDocumentModel.DOS_COPIAS, Orientacion.HORIZONTAL, List.of(b), null);
    }
}
