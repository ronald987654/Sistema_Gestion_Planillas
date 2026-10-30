package com.planillaperu.service;

import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.domain.RonaldmichaMotivoCese;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.export.doc.RonaldmichaDocumentModel;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Boleta;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Conceptos;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.LineaBoleta;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaBeneficioRepository;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaDocumentoRepository;
import com.planillaperu.repository.RonaldmichaEmpresaRepository;
import com.planillaperu.repository.RonaldmichaLiquidacionRepository;
import com.planillaperu.repository.RonaldmichaParametrosRepository;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;
import com.planillaperu.repository.RonaldmichaPlanillaRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.repository.RonaldmichaVacacionRepository;
import com.planillaperu.service.RonaldmichaPlanillaService.Borrador;
import com.planillaperu.service.RonaldmichaPlanillaService.FilaPlanilla;
import com.planillaperu.service.RonaldmichaReporte.Formato;
import com.planillaperu.service.RonaldmichaReportesService.Archivo;
import com.planillaperu.service.RonaldmichaReportesService.Parametros;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaBoletaPagoTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);

    @TempDir
    Path carpeta;

    private RonaldmichaDatabase db;
    private RonaldmichaReportesService servicio;
    private RonaldmichaPlanillaService planilla;
    private RonaldmichaLiquidacionService liquidaciones;
    private RonaldmichaTrabajadorRepository trabajadores;
    private RonaldmichaPeriodoRepository periodos;
    private RonaldmichaUsuario admin;
    private RonaldmichaUsuario carlos;

    @BeforeEach
    void preparar() {
        db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
        RonaldmichaAuditoriaService auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        trabajadores = new RonaldmichaTrabajadorRepository(db);
        periodos = new RonaldmichaPeriodoRepository(db);
        RonaldmichaParametrosService parametros = new RonaldmichaParametrosService(new RonaldmichaParametrosRepository(db), periodos, trabajadores,
                auditoria, reloj);
        RonaldmichaPeriodoService periodoService = new RonaldmichaPeriodoService(periodos, reloj);
        RonaldmichaAsistenciaRepository asistenciaRepositorio = new RonaldmichaAsistenciaRepository(db);
        RonaldmichaAsistenciaService asistencia = new RonaldmichaAsistenciaService(asistenciaRepositorio, trabajadores, periodoService,
                parametros, auditoria, reloj);
        planilla = new RonaldmichaPlanillaService(trabajadores, asistenciaRepositorio, new RonaldmichaPlanillaRepository(db), periodos,
                periodoService, parametros, asistencia, auditoria, reloj);
        RonaldmichaEmpresaService empresa = new RonaldmichaEmpresaService(new RonaldmichaEmpresaRepository(db), auditoria);
        RonaldmichaBeneficiosService beneficios = new RonaldmichaBeneficiosService(trabajadores, new RonaldmichaBeneficioRepository(db), planilla,
                parametros, empresa, auditoria);
        RonaldmichaVacacionRepository vacacionRepositorio = new RonaldmichaVacacionRepository(db);
        RonaldmichaVacacionesService vacaciones = new RonaldmichaVacacionesService(trabajadores, vacacionRepositorio, periodoService, empresa,
                auditoria, reloj);
        liquidaciones = new RonaldmichaLiquidacionService(trabajadores, new RonaldmichaLiquidacionRepository(db), vacacionRepositorio,
                beneficios, parametros, periodoService, empresa, auditoria, reloj);
        servicio = new RonaldmichaReportesService(trabajadores, planilla, beneficios, liquidaciones, vacaciones, parametros,
                empresa, new RonaldmichaDocumentoRepository(db), RonaldmichaMailService.NO_CONFIGURADO, auditoria, reloj);
        admin = new RonaldmichaUsuario(1L, "admin", "", RonaldmichaRol.ADMIN, id("40000001"), true);
        carlos = new RonaldmichaUsuario(2L, "40000002", "", RonaldmichaRol.TRABAJADOR, id("40000002"), true);
    }

    @Test
    void casoDeControlConCeseEl21DeOctubre() {
        long id = id("40000002");
        sql("UPDATE trabajador SET fecha_cese = '2026-10-21' WHERE id = " + id);

        Boleta b = boleta(id);

        assertThat(b.mes()).isEqualTo("MES DE OCTUBRE 2026");
        assertThat(b.numero()).isEqualTo("BOL-202610-T0002");
        assertThat(b.fechaPago()).isEqualTo("31/10/2026");
        assertThat(monto(b.remuneraciones(), "Sueldo Computable")).isEqualByComparingTo("1960.00");
        assertThat(monto(b.remuneraciones(), "Mov. Sup. Asistencia")).isEqualByComparingTo("105.00");
        assertThat(monto(b.remuneraciones(), "Asignación Familiar")).isEqualByComparingTo("0.00");
        assertThat(b.totalRemuneraciones()).isEqualByComparingTo("2065.00");
        assertThat(monto(b.descuentos(), "AFP Fondo")).isEqualByComparingTo("196.00");
        assertThat(monto(b.descuentos(), "AFP Comisión")).isEqualByComparingTo("30.38");
        assertThat(monto(b.descuentos(), "AFP Seguro")).isEqualByComparingTo("26.85");
        assertThat(linea(b.descuentos(), "AFP Fondo").porcentaje()).isEqualTo("10.00%");
        assertThat(linea(b.descuentos(), "AFP Comisión").porcentaje()).isEqualTo("1.55%");
        assertThat(linea(b.descuentos(), "AFP Seguro").porcentaje()).isEqualTo("1.37%");
        assertThat(linea(b.descuentos(), "AFP Fondo").codigo()).isEqualTo("0608");
        assertThat(b.totalDescuentos()).isEqualByComparingTo("253.23");
        assertThat(b.neto()).isEqualByComparingTo("1811.77");
        assertThat(b.enLetras()).isEqualTo("Mil ochocientos once con 77/100 soles");
        assertThat(monto(b.aportes(), "Essalud")).isEqualByComparingTo("176.40");
        assertThat(linea(b.aportes(), "Essalud").porcentaje()).isEqualTo("9.00%");
        assertThat(monto(b.aportes(), "Vida Ley")).isEqualByComparingTo("29.99");
        assertThat(b.leyendaCese()).startsWith("Trabajador cesado el 21/10/2026. Sus beneficios sociales (CTS, "
                + "vacaciones y gratificación truncas) se pagan en la");
        assertThat(valor(b, "F. CESE")).isEqualTo("21/10/2026");
        assertThat(valor(b, "ONP")).isEqualTo("NO");
        assertThat(valor(b, "A.F.P.")).isEqualTo("Integra");
        assertThat(valor(b, "CTA. DE DEPÓSITO")).isEqualTo("Banco Ejemplo 000-0000002");
        assertThat(valor(b, "F. NAC.")).isEqualTo("03/09/1992");
        assertThat(valor(b, "CATEGORÍA")).isEqualTo("Empleado");
        assertThat(b.neto()).isEqualByComparingTo(fila(id).resultado().netoAPagar());
    }

    @Test
    void laLeyendaDeCeseUsaElNumeroRealDeLaLiquidacion() {
        long id = id("40000002");
        liquidaciones.guardar(admin, null, new RonaldmichaLiquidacionService.Borrador(id, LocalDate.of(2026, 10, 21),
                RonaldmichaMotivoCese.RENUNCIA, null, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO), false);
        sql("UPDATE trabajador SET fecha_cese = '2026-10-21' WHERE id = " + id);
        String numero = servicio.exportar(admin, RonaldmichaReporte.LIQUIDACION, Formato.PDF, new Parametros(OCTUBRE, id, false))
                .numeros().getFirst();

        assertThat(boleta(id).leyendaCese()).endsWith("se pagan en la Liquidación N° " + numero + ".");
    }

    @Test
    void conDosFaltasElNetoEsElDeLaPlanilla() {
        long id = id("40000002");
        planilla.guardar(admin, id, OCTUBRE, new Borrador(2, 0, 0, 0, 0, 0, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO,
                RonaldmichaDinero.CERO, List.of(), List.of()));

        Boleta b = boleta(id);
        FilaPlanilla f = fila(id);

        assertThat(monto(b.remuneraciones(), "Sueldo Computable")).isEqualByComparingTo("2800.00");
        assertThat(monto(b.descuentos(), "Faltas")).isEqualByComparingTo("186.67");
        assertThat(b.neto()).isEqualByComparingTo(f.resultado().netoAPagar());
        assertThat(b.totalRemuneraciones().subtract(b.totalDescuentos())).isEqualByComparingTo(f.resultado().netoAPagar());
        assertThat(monto(b.descuentos(), "AFP Fondo")).isEqualByComparingTo(f.resultado().pension().aporte());
        assertThat(valor(b, "DÍAS NO LABORADOS")).isEqualTo("2");
        assertThat(valor(b, "DÍAS LABORADOS")).isEqualTo("28");
    }

    @Test
    void lasFilasFijasSiempreEstanYLasOpcionalesSoloSiHayMonto() {
        Boleta b = boleta(id("40000001"));

        assertThat(b.remuneraciones().lineas()).extracting(LineaBoleta::concepto)
                .startsWith("Sueldo Computable", "Asignación Familiar", "Remuner. Vacacional", "Mov. Sup. Asistencia")
                .doesNotContain("HE 25%", "Comisiones");
        assertThat(b.descuentos().lineas()).extracting(LineaBoleta::concepto)
                .containsSequence("AFP Fondo", "AFP Comisión", "AFP Seguro", "ONP", "Faltas", "Retenciones 5ta",
                        "Retenciones Judiciales", "Adelantos")
                .doesNotContain("Tardanzas");
        assertThat(linea(b.descuentos(), "ONP").porcentaje()).isEqualTo("13.00%");
        assertThat(b.aportes().lineas()).extracting(LineaBoleta::concepto).startsWith("Essalud", "S.C.T.R.");
    }

    @Test
    void faltanDatosDeLaEmpresaSeMarcanYSeAvisaAntesDeExportar() {
        Boleta b = boleta(id("40000001"));

        assertThat(valor(b, "RUBRO DE LA EMPRESA")).isEqualTo(Boleta.COMPLETAR);
        assertThat(servicio.observaciones(admin, RonaldmichaReporte.BOLETA_PAGO, new Parametros(OCTUBRE, id("40000001"), false)))
                .anyMatch(o -> o.contains("[COMPLETAR]") && o.contains("rubro"));
    }

    @Test
    void elPdfTieneDosPaginasPorTrabajadorConLaFirmaEnCadaUna() throws IOException {
        List<FilaPlanilla> filas = planilla.delMes(OCTUBRE).filas();
        Archivo todos = servicio.exportar(admin, RonaldmichaReporte.BOLETA_PAGO, Formato.PDF, new Parametros(OCTUBRE, null, true));

        List<String> paginas = paginas(todos.contenido());
        assertThat(paginas).hasSize(2 * filas.size());
        for (int i = 0; i < paginas.size(); i++) {
            String p = paginas.get(i);
            assertThat(p).as("página %d", i + 1).contains(i % 2 == 0 ? "COPIA EMPLEADOR" : "COPIA TRABAJADOR",
                    "BOLETA DE PAGO DE REMUNERACIONES", "MES DE OCTUBRE 2026", "Neto a Pagar", "Total Descuentos",
                    "Recibí conforme", "Firma y sello del empleador", "Huella digital", "Página 1 de 1");
            assertThat(p).doesNotContain("se asigna al exportar", "lado a lado", "2017", "MAYO");
        }
        assertThat(paginas.getFirst()).contains("Constancia de entrega: el trabajador recibe su copia");
        assertThat(paginas.get(1)).contains("Conserve esta boleta");
        for (FilaPlanilla f : filas) {
            Archivo uno = servicio.exportar(admin, RonaldmichaReporte.BOLETA_PAGO, Formato.PDF,
                    new Parametros(OCTUBRE, f.trabajador().id(), false));
            assertThat(paginas(uno.contenido())).as(f.trabajador().codigo()).hasSize(2);
        }
    }

    @Test
    void sinDosCopiasEsUnaPaginaSinRotulo() throws IOException {
        Archivo a = servicio.exportar(admin, RonaldmichaReporte.BOLETA_PAGO, Formato.PDF,
                new Parametros(OCTUBRE, id("40000002"), false, false));

        List<String> paginas = paginas(a.contenido());
        assertThat(paginas).hasSize(1);
        assertThat(paginas.getFirst()).doesNotContain("COPIA EMPLEADOR", "COPIA TRABAJADOR").contains("Recibí conforme");
    }

    @Test
    void elWordLlevaUnaCopiaPorPagina() throws IOException {
        Archivo a = servicio.exportar(admin, RonaldmichaReporte.BOLETA_PAGO, Formato.WORD, new Parametros(OCTUBRE, id("40000002"),
                false));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(a.contenido()));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String texto = extractor.getText();
            assertThat(texto).contains("COPIA EMPLEADOR", "COPIA TRABAJADOR", "MES DE OCTUBRE 2026",
                    "BOL-202610-T0002", "Neto a Pagar", "Recibí conforme", "Huella digital");
            assertThat(doc.getParagraphs()).anyMatch(p -> p.isPageBreak());
        }
    }

    @Test
    void elPortalEntregaSoloLaCopiaDelTrabajador() throws IOException {
        periodos.cerrar(OCTUBRE, "admin", LocalDateTime.of(2026, 10, 31, 18, 0));

        Archivo a = servicio.descargarMiBoleta(carlos, OCTUBRE);

        List<String> paginas = paginas(a.contenido());
        assertThat(paginas).hasSize(1);
        assertThat(paginas.getFirst()).contains("COPIA TRABAJADOR", "Conserve esta boleta")
                .doesNotContain("COPIA EMPLEADOR");
    }

    private Boleta boleta(long trabajadorId) {
        RonaldmichaDocumentModel d = servicio.vistaPrevia(admin, RonaldmichaReporte.BOLETA_PAGO, new Parametros(OCTUBRE, trabajadorId, false));
        assertThat(d.esBoleta()).isTrue();
        return d.boleta();
    }

    private FilaPlanilla fila(long trabajadorId) {
        return planilla.delMes(OCTUBRE).filas().stream().filter(f -> f.trabajador().id() == trabajadorId)
                .findFirst().orElseThrow();
    }

    private static LineaBoleta linea(Conceptos c, String concepto) {
        return c.lineas().stream().filter(l -> l.concepto().equals(concepto)).findFirst()
                .orElseThrow(() -> new AssertionError("Sin «" + concepto + "» en " + c.titulo()));
    }

    private static BigDecimal monto(Conceptos c, String concepto) {
        return linea(c, concepto).monto();
    }

    private static String valor(Boleta b, String etiqueta) {
        return b.datos().stream().flatMap(f -> f.grupos().stream()).flatMap(g -> g.campos().stream())
                .filter(c -> c.etiqueta().equals(etiqueta)).findFirst().orElseThrow().valor();
    }

    private static List<String> paginas(byte[] pdf) throws IOException {
        PdfReader lector = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            List<String> lista = new ArrayList<>();
            for (int i = 1; i <= lector.getNumberOfPages(); i++) {
                lista.add(extractor.getTextFromPage(i));
            }
            return lista;
        } finally {
            lector.close();
        }
    }

    private void sql(String sentencia) {
        try (Connection c = db.conexion(); Statement st = c.createStatement()) {
            st.executeUpdate(sentencia);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private long id(String dni) {
        return trabajadores.listarTodos().stream().filter(t -> t.numDoc().equals(dni)).findFirst().orElseThrow().id();
    }
}
