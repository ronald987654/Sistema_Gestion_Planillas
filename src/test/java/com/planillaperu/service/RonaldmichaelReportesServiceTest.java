package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaDocumentoEmitido;
import com.planillaperu.domain.RonaldmichaEstadoDocumento;
import com.planillaperu.domain.RonaldmichaMotivoCese;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.export.doc.RonaldmichaDocumentModel;
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
import com.planillaperu.service.RonaldmichaelReporte.Formato;
import com.planillaperu.service.RonaldmichaelReportesService.Archivo;
import com.planillaperu.service.RonaldmichaelReportesService.Envio;
import com.planillaperu.service.RonaldmichaelReportesService.Parametros;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaelReportesServiceTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final String LINEA_REM = "01\\|40000001\\|0121\\|\\d+\\.\\d{2}\\|\\d+\\.\\d{2}\\|";
    private static final String LINEA_TXT =
            "DNI\\|40000001\\|QUISPE MAMANI ROSA ELENA\\|Banco Ejemplo\\|000-0000001\\|PEN\\|\\d+\\.\\d{2}";

    @TempDir
    Path carpeta;

    private RonaldmichaelReportesService servicio;
    private RonaldmichaelLiquidacionService liquidaciones;
    private RonaldmichaelAuditoriaService auditoria;
    private RonaldmichaTrabajadorRepository trabajadores;
    private RonaldmichaUsuario admin;
    private RonaldmichaUsuario aprobador;
    private RonaldmichaUsuario carlos;
    private RonaldmichaPeriodoRepository periodoRepositorio;

    @BeforeEach
    void preparar() {
        RonaldmichaDatabase db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
        auditoria = new RonaldmichaelAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        trabajadores = new RonaldmichaTrabajadorRepository(db);
        periodoRepositorio = new RonaldmichaPeriodoRepository(db);
        RonaldmichaelParametrosService parametros = new RonaldmichaelParametrosService(new RonaldmichaParametrosRepository(db), periodoRepositorio,
                trabajadores, auditoria, reloj);
        RonaldmichaelPeriodoService periodos = new RonaldmichaelPeriodoService(periodoRepositorio, reloj);
        RonaldmichaAsistenciaRepository asistenciaRepositorio = new RonaldmichaAsistenciaRepository(db);
        RonaldmichaelAsistenciaService asistencia = new RonaldmichaelAsistenciaService(asistenciaRepositorio, trabajadores, periodos, parametros,
                auditoria, reloj);
        RonaldmichaelPlanillaService planilla = new RonaldmichaelPlanillaService(trabajadores, asistenciaRepositorio, new RonaldmichaPlanillaRepository(db),
                periodoRepositorio, periodos, parametros, asistencia, auditoria, reloj);
        RonaldmichaelEmpresaService empresa = new RonaldmichaelEmpresaService(new RonaldmichaEmpresaRepository(db), auditoria);
        RonaldmichaelBeneficiosService beneficios = new RonaldmichaelBeneficiosService(trabajadores, new RonaldmichaBeneficioRepository(db), planilla,
                parametros, empresa, auditoria);
        RonaldmichaVacacionRepository vacacionRepositorio = new RonaldmichaVacacionRepository(db);
        RonaldmichaelVacacionesService vacaciones = new RonaldmichaelVacacionesService(trabajadores, vacacionRepositorio, periodos, empresa,
                auditoria, reloj);
        liquidaciones = new RonaldmichaelLiquidacionService(trabajadores, new RonaldmichaLiquidacionRepository(db), vacacionRepositorio,
                beneficios, parametros, periodos, empresa, auditoria, reloj);
        servicio = new RonaldmichaelReportesService(trabajadores, planilla, beneficios, liquidaciones, vacaciones, parametros, empresa,
                new RonaldmichaDocumentoRepository(db), RonaldmichaelMailService.NO_CONFIGURADO, auditoria, reloj);
        admin = new RonaldmichaUsuario(1L, "admin", "", RonaldmichaRol.ADMIN, id("40000001"), true);
        aprobador = new RonaldmichaUsuario(3L, "aprobador", "", RonaldmichaRol.APROBADOR, id("40000003"), true);
        carlos = new RonaldmichaUsuario(2L, "40000002", "", RonaldmichaRol.TRABAJADOR, id("40000002"), true);
    }

    @Test
    void catalogoSegunElRol() {
        assertThat(servicio.catalogo(admin)).hasSize(13).containsExactly(RonaldmichaelReporte.values());
        assertThat(servicio.catalogo(carlos)).containsExactly(RonaldmichaelReporte.BOLETA_PAGO, RonaldmichaelReporte.GRATIFICACION,
                RonaldmichaelReporte.CTS, RonaldmichaelReporte.RENTA_QUINTA, RonaldmichaelReporte.CONSTANCIA_TRABAJO, RonaldmichaelReporte.SOLICITUD_VACACIONES);
        assertThat(servicio.formatos(carlos, RonaldmichaelReporte.BOLETA_PAGO)).containsExactly(Formato.PDF);
        assertThat(servicio.formatos(admin, RonaldmichaelReporte.PAGOS)).containsExactly(Formato.TXT, Formato.EXCEL);
        assertThat(RonaldmichaelReporte.desdeTipo("PLR")).isEqualTo(RonaldmichaelReporte.PLAME);
        assertThat(servicio.trabajadores(carlos, RonaldmichaelReporte.BOLETA_PAGO, OCTUBRE)).extracting(t -> t.numDoc())
                .containsExactly("40000002");
        assertThat(servicio.trabajadores(admin, RonaldmichaelReporte.BOLETA_PAGO, OCTUBRE)).hasSize(7);
        assertThat(servicio.periodoTexto(RonaldmichaelReporte.GRATIFICACION, OCTUBRE)).isEqualTo("Gratificación de diciembre 2026");
    }

    @Test
    void boletaDePagoConConceptosPlameNetoEnLetrasYCopias() {
        RonaldmichaDocumentModel d = servicio.vistaPrevia(aprobador, RonaldmichaelReporte.BOLETA_PAGO, new Parametros(OCTUBRE, id("40000001"), false));

        assertThat(d.copias()).hasSize(2);
        assertThat(d.esBoleta()).isTrue();
        RonaldmichaDocumentModel.Boleta b = d.boleta();
        assertThat(b.mes()).isEqualTo("MES DE OCTUBRE 2026");
        assertThat(b.numero()).isEqualTo("BOL-202610-T0001");
        assertThat(b.remuneraciones().lineas()).anyMatch(l -> "0121".equals(l.codigo()));
        assertThat(b.enLetras()).endsWith("/100 soles").doesNotContain("SOLES");
    }

    @Test
    void numeracionCorrelativaYTodosEnUnPdf() {
        Archivo uno = servicio.exportar(admin, RonaldmichaelReporte.BOLETA_PAGO, Formato.PDF, new Parametros(OCTUBRE, id("40000001"), false));
        Archivo todos = servicio.exportar(admin, RonaldmichaelReporte.BOLETA_PAGO, Formato.PDF, new Parametros(OCTUBRE, null, true));
        Archivo word = servicio.exportar(admin, RonaldmichaelReporte.CONSTANCIA_TRABAJO, Formato.WORD,
                new Parametros(OCTUBRE, id("40000002"), false));

        assertThat(uno.numeros()).containsExactly("BOL-2026-000001");
        assertThat(new String(uno.contenido(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(uno.nombre()).isEqualTo("boleta_pago_202610_T0001.pdf");
        assertThat(todos.numeros()).hasSize(7).first().isEqualTo("BOL-2026-000002");
        assertThat(todos.numeros().getLast()).isEqualTo("BOL-2026-000008");
        assertThat(word.numeros()).containsExactly("CON-2026-000001");
        assertThat(new String(word.contenido(), 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
        assertThat(servicio.emitidos(admin)).hasSize(9);
        assertThat(auditoria.recientes(5)).anyMatch(r -> r.accion().equals("DOCUMENTO_EMITIDO")
                && r.detalle().contains("BOL-2026-000002 a BOL-2026-000008"));
    }

    @Test
    void cargoRecibidoSoloEnDocumentosQueLoRequieren() {
        servicio.exportar(admin, RonaldmichaelReporte.BOLETA_PAGO, Formato.PDF, new Parametros(OCTUBRE, id("40000001"), false));
        servicio.exportar(admin, RonaldmichaelReporte.PLANILLA, Formato.EXCEL, new Parametros(OCTUBRE, null, false));
        RonaldmichaDocumentoEmitido boleta = emitido("BOL-2026-000001");
        RonaldmichaDocumentoEmitido planilla = emitido("PLA-2026-000001");

        servicio.marcarCargoRecibido(admin, boleta);

        assertThat(emitido("BOL-2026-000001").estado()).isEqualTo(RonaldmichaEstadoDocumento.RECIBIDO);
        assertThat(planilla.trabajadorId()).isNull();
        assertThatThrownBy(() -> servicio.marcarCargoRecibido(admin, emitido("BOL-2026-000001")))
                .isInstanceOf(RonaldmichaelValidacionException.class);
        assertThatThrownBy(() -> servicio.marcarCargoRecibido(admin, planilla)).isInstanceOf(RonaldmichaelValidacionException.class);
        assertThat(auditoria.recientes(3)).anyMatch(r -> r.accion().equals("CARGO_RECIBIDO"));
    }

    @Test
    void planillaExcelDe33ColumnasConSuma() throws IOException {
        Archivo a = servicio.exportar(admin, RonaldmichaelReporte.PLANILLA, Formato.EXCEL, new Parametros(OCTUBRE, null, false));

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(a.contenido()))) {
            Sheet hoja = libro.getSheetAt(0);
            Row encabezado = hoja.getRow(2);
            assertThat(encabezado.getLastCellNum()).isEqualTo((short) 33);
            assertThat(encabezado.getCell(32).getStringCellValue()).isEqualTo("Aportes del empleador");
            Row total = hoja.getRow(3 + 7);
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("TOTAL");
            assertThat(total.getCell(31).getCellFormula()).isEqualTo("SUM(AF4:AF10)");
        }
        assertThat(new String(servicio.exportar(admin, RonaldmichaelReporte.PLANILLA, Formato.PDF,
                new Parametros(OCTUBRE, null, false)).contenido(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void pagosMasivosConObservaciones() throws IOException {
        Parametros p = new Parametros(OCTUBRE, null, false);
        assertThat(servicio.observaciones(admin, RonaldmichaelReporte.PAGOS, p))
                .anyMatch(o -> o.startsWith("Chávez Paredes, Ana Sofía") && o.contains("cheque"))
                .anyMatch(o -> o.startsWith("Ramos Condori, Jorge Luis") && o.contains("efectivo"));

        String txt = new String(servicio.exportar(admin, RonaldmichaelReporte.PAGOS, Formato.TXT, p).contenido(), StandardCharsets.UTF_8);
        assertThat(txt.lines().toList()).anyMatch(l -> l.matches(LINEA_TXT));
        assertThat(txt).doesNotContain("40000005", "40000006");

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                servicio.exportar(admin, RonaldmichaelReporte.PAGOS, Formato.EXCEL, p).contenido()))) {
            List<String> hojas = new ArrayList<>();
            libro.forEach(h -> hojas.add(h.getSheetName()));
            assertThat(hojas).containsExactly("Resumen", "Banco Ejemplo", "Sin cuenta");
        }
    }

    @Test
    void afpnetYArchivosPlame() throws IOException {
        Parametros p = new Parametros(OCTUBRE, null, false);
        assertThat(servicio.observaciones(admin, RonaldmichaelReporte.AFPNET, p)).isEmpty();
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                servicio.exportar(admin, RonaldmichaelReporte.AFPNET, Formato.EXCEL, p).contenido()))) {
            assertThat(libro.getSheet("AFP Integra").getRow(0).getCell(1).getStringCellValue()).isEqualTo("CUSPP");
            assertThat(libro.getSheet("AFP Integra").getRow(1).getCell(1).getStringCellValue()).isEqualTo("000000CFHUA0");
        }

        assertThat(servicio.observaciones(admin, RonaldmichaelReporte.PLAME, p)).anyMatch(o -> o.contains("RUC"));
        Archivo zip = servicio.exportar(admin, RonaldmichaelReporte.PLAME, Formato.ZIP, p);
        assertThat(zip.nombre()).isEqualTo("060120261000000000000.zip");
        List<String> nombres = new ArrayList<>();
        StringBuilder rem = new StringBuilder();
        StringBuilder jor = new StringBuilder();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip.contenido()))) {
            ZipEntry e;
            while ((e = entrada.getNextEntry()) != null) {
                nombres.add(e.getName());
                String contenido = new String(entrada.readAllBytes(), StandardCharsets.ISO_8859_1);
                if (e.getName().endsWith(".rem")) {
                    rem.append(contenido);
                } else if (e.getName().endsWith(".jor")) {
                    jor.append(contenido);
                }
            }
        }
        assertThat(nombres).containsExactly("060120261000000000000.rem", "060120261000000000000.jor",
                "060120261000000000000.snl");
        assertThat(rem.toString().lines()).anyMatch(l -> l.matches(LINEA_REM));
        assertThat(jor.toString().lines()).anyMatch(l -> l.startsWith("01|40000001|"));
        assertThat(servicio.vistaPrevia(admin, RonaldmichaelReporte.PLAME, p).orientacion())
                .isEqualTo(RonaldmichaDocumentModel.Orientacion.HORIZONTAL);
    }

    @Test
    void enviarPorCorreoSinServidorConfigurado() {
        Envio envio = servicio.enviarPorCorreo(admin, RonaldmichaelReporte.BOLETA_PAGO, new Parametros(OCTUBRE, null, true));

        assertThat(envio.enviados()).isZero();
        assertThat(envio.registrados()).isEqualTo(6);
        assertThat(envio.sinCorreo()).containsExactly("Ramos Condori, Jorge Luis");
        assertThat(envio.mensaje()).contains("no está configurado");
        assertThat(servicio.emitidos(admin)).hasSize(6).allMatch(d -> d.estado() == RonaldmichaEstadoDocumento.GENERADO);
        assertThat(auditoria.recientes(3)).anyMatch(r -> r.accion().equals("ENVIO_CORREO"));
    }

    @Test
    void permisosYDocumentosQueRequierenCese() {
        assertThatThrownBy(() -> servicio.exportar(carlos, RonaldmichaelReporte.BOLETA_PAGO, Formato.PDF,
                new Parametros(OCTUBRE, id("40000002"), false))).isInstanceOf(RonaldmichaelValidacionException.class)
                .hasMessageContaining("aún no está disponible");
        periodoRepositorio.cerrar(OCTUBRE, "admin", java.time.LocalDateTime.of(2026, 10, 31, 18, 0));
        assertThat(new String(servicio.exportar(carlos, RonaldmichaelReporte.BOLETA_PAGO, Formato.PDF,
                new Parametros(OCTUBRE, id("40000002"), false)).contenido(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(servicio.emitidos(carlos)).hasSize(1);
        assertThatThrownBy(() -> servicio.exportar(carlos, RonaldmichaelReporte.BOLETA_PAGO, Formato.PDF,
                new Parametros(OCTUBRE, id("40000001"), false))).isInstanceOf(RonaldmichaelAccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.vistaPrevia(carlos, RonaldmichaelReporte.PLANILLA, new Parametros(OCTUBRE, null, false)))
                .isInstanceOf(RonaldmichaelAccesoDenegadoException.class);

        long rosa = id("40000001");
        assertThatThrownBy(() -> servicio.vistaPrevia(admin, RonaldmichaelReporte.CERTIFICADO_TRABAJO, new Parametros(OCTUBRE, rosa, false)))
                .isInstanceOf(RonaldmichaelValidacionException.class).hasMessageContaining("constancia de trabajo");
        assertThatThrownBy(() -> servicio.vistaPrevia(admin, RonaldmichaelReporte.LIQUIDACION, new Parametros(OCTUBRE, rosa, false)))
                .isInstanceOf(RonaldmichaelValidacionException.class).hasMessageContaining("no tiene liquidación");

        liquidaciones.guardar(admin, null, new RonaldmichaelLiquidacionService.Borrador(rosa, LocalDate.of(2026, 10, 31),
                RonaldmichaMotivoCese.RENUNCIA, null, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO), false);
        assertThat(servicio.vistaPrevia(admin, RonaldmichaelReporte.LIQUIDACION, new Parametros(OCTUBRE, rosa, false)).titulo())
                .isEqualTo("LIQUIDACIÓN DE BENEFICIOS SOCIALES");
        RonaldmichaDocumentModel carta = servicio.vistaPrevia(admin, RonaldmichaelReporte.LIBERACION_CTS, new Parametros(OCTUBRE, rosa, false));
        assertThat(carta.bloques().toString()).contains("Caja Ejemplo", "100-0000001", "31/10/2026");
    }

    @Test
    void losTreceReportesTienenVistaPreviaYExportanCadaFormato() {
        long rosa = id("40000001");
        liquidaciones.guardar(admin, null, new RonaldmichaelLiquidacionService.Borrador(rosa, LocalDate.of(2026, 10, 31),
                RonaldmichaMotivoCese.RENUNCIA, null, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO), false);
        int emitidos = 0;
        for (RonaldmichaelReporte r : RonaldmichaelReporte.values()) {
            Parametros p = parametrosQueFuncionan(r);
            RonaldmichaDocumentModel d = servicio.vistaPrevia(admin, r, p);
            assertThat(d.bloques()).as("bloques de %s", r).isNotEmpty();
            if (d.esBoleta()) {
                assertThat(d.boleta().firmas().trabajador()).as("firma del trabajador en %s", r).isNotBlank();
            } else {
                assertThat(d.bloques().getLast()).as("firmas al final de %s", r).isInstanceOf(RonaldmichaDocumentModel.Firmas.class);
                assertThat(((RonaldmichaDocumentModel.Firmas) d.bloques().getLast()).firmas()).as("dos firmas en %s", r).hasSize(2);
            }
            for (Formato f : r.formatos()) {
                Archivo a = servicio.exportar(admin, r, f, p);
                emitidos++;
                assertThat(a.contenido()).as("%s en %s", r, f).isNotEmpty();
                assertThat(a.nombre()).endsWith("." + f.extension());
                assertThat(a.numeros()).singleElement().asString().startsWith(r.tipo() + "-2026-");
                String cabecera = new String(a.contenido(), 0, Math.min(4, a.contenido().length), StandardCharsets.ISO_8859_1);
                switch (f) {
                    case PDF -> assertThat(cabecera).isEqualTo("%PDF");
                    case WORD, EXCEL, ZIP -> assertThat(cabecera).startsWith("PK");
                    case TXT -> assertThat(cabecera).isNotBlank();
                }
            }
        }
        assertThat(servicio.emitidos(admin)).hasSize(emitidos);
        assertThat(emitidos).isEqualTo(26);
    }

    private Parametros parametrosQueFuncionan(RonaldmichaelReporte r) {
        if (!r.porTrabajador()) {
            return new Parametros(OCTUBRE, null, false);
        }
        for (var t : servicio.trabajadores(admin, r, OCTUBRE)) {
            Parametros p = new Parametros(OCTUBRE, t.id(), false);
            try {
                servicio.vistaPrevia(admin, r, p);
                return p;
            } catch (RonaldmichaelValidacionException noAplica) {
            }
        }
        throw new AssertionError("Ningún trabajador tiene " + r.titulo());
    }

    @Test
    void cesadosPrimeroYUnaSolaCopia() {
        long rosa = id("40000001");
        liquidaciones.guardar(admin, null, new RonaldmichaelLiquidacionService.Borrador(rosa, LocalDate.of(2026, 10, 31),
                RonaldmichaMotivoCese.RENUNCIA, null, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO), false);
        assertThat(servicio.trabajadores(admin, RonaldmichaelReporte.CERTIFICADO_TRABAJO, OCTUBRE)).hasSize(7)
                .first().extracting(t -> t.numDoc()).isEqualTo("40000001");

        Parametros una = new Parametros(OCTUBRE, rosa, false, false);
        assertThat(servicio.vistaPrevia(admin, RonaldmichaelReporte.BOLETA_PAGO, una).copias()).isEmpty();
        assertThat(servicio.vistaPrevia(admin, RonaldmichaelReporte.BOLETA_PAGO, new Parametros(OCTUBRE, rosa, false)).copias())
                .hasSize(2);
    }

    @Test
    void elTrabajadorSoloDescargaPdfDePeriodosCerrados() {
        long carlosId = id("40000002");
        periodoRepositorio.cerrar(OCTUBRE, "admin", LocalDateTime.of(2026, 10, 31, 18, 0));
        assertThatThrownBy(() -> servicio.exportar(carlos, RonaldmichaelReporte.CONSTANCIA_TRABAJO, Formato.WORD,
                new Parametros(OCTUBRE, carlosId, false))).isInstanceOf(RonaldmichaelAccesoDenegadoException.class)
                .hasMessageContaining("solo en PDF");
        assertThat(servicio.exportar(carlos, RonaldmichaelReporte.CONSTANCIA_TRABAJO, Formato.PDF,
                new Parametros(OCTUBRE, carlosId, false)).numeros()).singleElement().asString().startsWith("CON-");
        assertThatThrownBy(() -> servicio.vistaPrevia(carlos, RonaldmichaelReporte.GRATIFICACION, new Parametros(OCTUBRE, carlosId, false)))
                .isInstanceOf(RonaldmichaelValidacionException.class).hasMessageContaining("aún no está disponible");
        assertThat(servicio.vistaPrevia(carlos, RonaldmichaelReporte.RENTA_QUINTA, new Parametros(OCTUBRE, carlosId, false)).titulo())
                .contains("RETENCIONES");
    }

    @Test
    void certificadoDeRentasDeQuinta() {
        RonaldmichaDocumentModel d = servicio.vistaPrevia(aprobador, RonaldmichaelReporte.RENTA_QUINTA, new Parametros(OCTUBRE, id("40000003"), false));

        assertThat(d.titulo()).isEqualTo("CERTIFICADO DE RENTAS Y RETENCIONES DE QUINTA CATEGORÍA");
        assertThat(d.bloques().toString()).contains("enero", "Gratificación de julio 2026", "Deducción de 7 UIT",
                "Retenido por el empleador", "TOTAL RETENIDO");
    }

    @Test
    void portalMisDocumentosYCargoElectronico() {
        List<RonaldmichaelReportesService.BoletaPortal> antes = servicio.misBoletas(carlos);
        assertThat(antes.getFirst().mes()).isEqualTo(OCTUBRE);
        assertThat(antes.getFirst().disponible()).isFalse();
        assertThat(antes).hasSizeLessThanOrEqualTo(RonaldmichaelReportesService.MESES_PORTAL);
        assertThatThrownBy(() -> servicio.descargarMiBoleta(carlos, OCTUBRE)).isInstanceOf(RonaldmichaelValidacionException.class)
                .hasMessageContaining("aún no está disponible");
        assertThatThrownBy(() -> servicio.misBoletas(admin)).isInstanceOf(RonaldmichaelAccesoDenegadoException.class);

        periodoRepositorio.cerrar(OCTUBRE, "admin", LocalDateTime.of(2026, 10, 31, 18, 0));
        Archivo primera = servicio.descargarMiBoleta(carlos, OCTUBRE);
        Archivo segunda = servicio.descargarMiBoleta(carlos, OCTUBRE);
        assertThat(new String(primera.contenido(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(primera.numeros()).containsExactly("BOL-2026-000001");
        assertThat(segunda.numeros()).isEqualTo(primera.numeros());
        RonaldmichaelReportesService.BoletaPortal octubre = servicio.misBoletas(carlos).getFirst();
        assertThat(octubre.disponible()).isTrue();
        RonaldmichaDocumentoEmitido boleta = octubre.documento().orElseThrow();
        assertThat(boleta.cargoPendiente()).isTrue();

        String deRosa = servicio.exportar(admin, RonaldmichaelReporte.BOLETA_PAGO, Formato.PDF,
                new Parametros(OCTUBRE, id("40000001"), false)).numeros().getFirst();
        assertThatThrownBy(() -> servicio.confirmarRecepcion(carlos, emitido(deRosa).id()))
                .isInstanceOf(RonaldmichaelAccesoDenegadoException.class);

        RonaldmichaDocumentoEmitido recibido = servicio.confirmarRecepcion(carlos, boleta.id());
        assertThat(recibido.estado()).isEqualTo(RonaldmichaEstadoDocumento.RECIBIDO);
        assertThat(recibido.fechaRecepcion()).isEqualTo(LocalDateTime.of(2026, 10, 15, 10, 0));
        assertThat(recibido.recepcion()).contains("electrónico", "40000002");
        assertThatThrownBy(() -> servicio.confirmarRecepcion(carlos, boleta.id())).isInstanceOf(RonaldmichaelValidacionException.class);
        assertThat(auditoria.recientes(5)).anyMatch(r -> r.accion().equals("CARGO_ELECTRONICO")
                && r.detalle().contains("D.S. 009-2011-TR"));
    }

    private RonaldmichaDocumentoEmitido emitido(String numero) {
        return servicio.emitidos(admin).stream().filter(d -> d.numero().equals(numero)).findFirst().orElseThrow();
    }

    private long id(String dni) {
        return trabajadores.listarTodos().stream().filter(t -> t.numDoc().equals(dni)).findFirst().orElseThrow().id();
    }
}
