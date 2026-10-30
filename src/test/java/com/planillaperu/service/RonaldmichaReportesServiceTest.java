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
import com.planillaperu.service.RonaldmichaReporte.Formato;
import com.planillaperu.service.RonaldmichaReportesService.Archivo;
import com.planillaperu.service.RonaldmichaReportesService.Envio;
import com.planillaperu.service.RonaldmichaReportesService.Parametros;
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

class RonaldmichaReportesServiceTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final String LINEA_REM = "01\\|40000001\\|0121\\|\\d+\\.\\d{2}\\|\\d+\\.\\d{2}\\|";
    private static final String LINEA_TXT =
            "DNI\\|40000001\\|QUISPE MAMANI ROSA ELENA\\|Banco Ejemplo\\|000-0000001\\|PEN\\|\\d+\\.\\d{2}";

    @TempDir
    Path carpeta;

    private RonaldmichaReportesService servicio;
    private RonaldmichaLiquidacionService liquidaciones;
    private RonaldmichaAuditoriaService auditoria;
    private RonaldmichaTrabajadorRepository trabajadores;
    private RonaldmichaUsuario admin;
    private RonaldmichaUsuario aprobador;
    private RonaldmichaUsuario carlos;
    private RonaldmichaPeriodoRepository periodoRepositorio;

    @BeforeEach
    void preparar() {
        RonaldmichaDatabase db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
        auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        trabajadores = new RonaldmichaTrabajadorRepository(db);
        periodoRepositorio = new RonaldmichaPeriodoRepository(db);
        RonaldmichaParametrosService parametros = new RonaldmichaParametrosService(new RonaldmichaParametrosRepository(db), periodoRepositorio,
                trabajadores, auditoria, reloj);
        RonaldmichaPeriodoService periodos = new RonaldmichaPeriodoService(periodoRepositorio, reloj);
        RonaldmichaAsistenciaRepository asistenciaRepositorio = new RonaldmichaAsistenciaRepository(db);
        RonaldmichaAsistenciaService asistencia = new RonaldmichaAsistenciaService(asistenciaRepositorio, trabajadores, periodos, parametros,
                auditoria, reloj);
        RonaldmichaPlanillaService planilla = new RonaldmichaPlanillaService(trabajadores, asistenciaRepositorio, new RonaldmichaPlanillaRepository(db),
                periodoRepositorio, periodos, parametros, asistencia, auditoria, reloj);
        RonaldmichaEmpresaService empresa = new RonaldmichaEmpresaService(new RonaldmichaEmpresaRepository(db), auditoria);
        RonaldmichaBeneficiosService beneficios = new RonaldmichaBeneficiosService(trabajadores, new RonaldmichaBeneficioRepository(db), planilla,
                parametros, empresa, auditoria);
        RonaldmichaVacacionRepository vacacionRepositorio = new RonaldmichaVacacionRepository(db);
        RonaldmichaVacacionesService vacaciones = new RonaldmichaVacacionesService(trabajadores, vacacionRepositorio, periodos, empresa,
                auditoria, reloj);
        liquidaciones = new RonaldmichaLiquidacionService(trabajadores, new RonaldmichaLiquidacionRepository(db), vacacionRepositorio,
                beneficios, parametros, periodos, empresa, auditoria, reloj);
        servicio = new RonaldmichaReportesService(trabajadores, planilla, beneficios, liquidaciones, vacaciones, parametros, empresa,
                new RonaldmichaDocumentoRepository(db), RonaldmichaMailService.NO_CONFIGURADO, auditoria, reloj);
        admin = new RonaldmichaUsuario(1L, "admin", "", RonaldmichaRol.ADMIN, id("40000001"), true);
        aprobador = new RonaldmichaUsuario(3L, "aprobador", "", RonaldmichaRol.APROBADOR, id("40000003"), true);
        carlos = new RonaldmichaUsuario(2L, "40000002", "", RonaldmichaRol.TRABAJADOR, id("40000002"), true);
    }

    @Test
    void catalogoSegunElRol() {
        assertThat(servicio.catalogo(admin)).hasSize(13).containsExactly(RonaldmichaReporte.values());
        assertThat(servicio.catalogo(carlos)).containsExactly(RonaldmichaReporte.BOLETA_PAGO, RonaldmichaReporte.GRATIFICACION,
                RonaldmichaReporte.CTS, RonaldmichaReporte.RENTA_QUINTA, RonaldmichaReporte.CONSTANCIA_TRABAJO, RonaldmichaReporte.SOLICITUD_VACACIONES);
        assertThat(servicio.formatos(carlos, RonaldmichaReporte.BOLETA_PAGO)).containsExactly(Formato.PDF);
        assertThat(servicio.formatos(admin, RonaldmichaReporte.PAGOS)).containsExactly(Formato.TXT, Formato.EXCEL);
        assertThat(RonaldmichaReporte.desdeTipo("PLR")).isEqualTo(RonaldmichaReporte.PLAME);
        assertThat(servicio.trabajadores(carlos, RonaldmichaReporte.BOLETA_PAGO, OCTUBRE)).extracting(t -> t.numDoc())
                .containsExactly("40000002");
        assertThat(servicio.trabajadores(admin, RonaldmichaReporte.BOLETA_PAGO, OCTUBRE)).hasSize(7);
        assertThat(servicio.periodoTexto(RonaldmichaReporte.GRATIFICACION, OCTUBRE)).isEqualTo("Gratificación de diciembre 2026");
    }

    @Test
    void boletaDePagoConConceptosPlameNetoEnLetrasYCopias() {
        RonaldmichaDocumentModel d = servicio.vistaPrevia(aprobador, RonaldmichaReporte.BOLETA_PAGO, new Parametros(OCTUBRE, id("40000001"), false));

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
        Archivo uno = servicio.exportar(admin, RonaldmichaReporte.BOLETA_PAGO, Formato.PDF, new Parametros(OCTUBRE, id("40000001"), false));
        Archivo todos = servicio.exportar(admin, RonaldmichaReporte.BOLETA_PAGO, Formato.PDF, new Parametros(OCTUBRE, null, true));
        Archivo word = servicio.exportar(admin, RonaldmichaReporte.CONSTANCIA_TRABAJO, Formato.WORD,
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
        servicio.exportar(admin, RonaldmichaReporte.BOLETA_PAGO, Formato.PDF, new Parametros(OCTUBRE, id("40000001"), false));
        servicio.exportar(admin, RonaldmichaReporte.PLANILLA, Formato.EXCEL, new Parametros(OCTUBRE, null, false));
        RonaldmichaDocumentoEmitido boleta = emitido("BOL-2026-000001");
        RonaldmichaDocumentoEmitido planilla = emitido("PLA-2026-000001");

        servicio.marcarCargoRecibido(admin, boleta);

        assertThat(emitido("BOL-2026-000001").estado()).isEqualTo(RonaldmichaEstadoDocumento.RECIBIDO);
        assertThat(planilla.trabajadorId()).isNull();
        assertThatThrownBy(() -> servicio.marcarCargoRecibido(admin, emitido("BOL-2026-000001")))
                .isInstanceOf(RonaldmichaValidacionException.class);
        assertThatThrownBy(() -> servicio.marcarCargoRecibido(admin, planilla)).isInstanceOf(RonaldmichaValidacionException.class);
        assertThat(auditoria.recientes(3)).anyMatch(r -> r.accion().equals("CARGO_RECIBIDO"));
    }

    @Test
    void planillaExcelDe33ColumnasConSuma() throws IOException {
        Archivo a = servicio.exportar(admin, RonaldmichaReporte.PLANILLA, Formato.EXCEL, new Parametros(OCTUBRE, null, false));

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(a.contenido()))) {
            Sheet hoja = libro.getSheetAt(0);
            Row encabezado = hoja.getRow(2);
            assertThat(encabezado.getLastCellNum()).isEqualTo((short) 33);
            assertThat(encabezado.getCell(32).getStringCellValue()).isEqualTo("Aportes del empleador");
            Row total = hoja.getRow(3 + 7);
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("TOTAL");
            assertThat(total.getCell(31).getCellFormula()).isEqualTo("SUM(AF4:AF10)");
        }
        assertThat(new String(servicio.exportar(admin, RonaldmichaReporte.PLANILLA, Formato.PDF,
                new Parametros(OCTUBRE, null, false)).contenido(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void pagosMasivosConObservaciones() throws IOException {
        Parametros p = new Parametros(OCTUBRE, null, false);
        assertThat(servicio.observaciones(admin, RonaldmichaReporte.PAGOS, p))
                .anyMatch(o -> o.startsWith("Chávez Paredes, Ana Sofía") && o.contains("cheque"))
                .anyMatch(o -> o.startsWith("Ramos Condori, Jorge Luis") && o.contains("efectivo"));

        String txt = new String(servicio.exportar(admin, RonaldmichaReporte.PAGOS, Formato.TXT, p).contenido(), StandardCharsets.UTF_8);
        assertThat(txt.lines().toList()).anyMatch(l -> l.matches(LINEA_TXT));
        assertThat(txt).doesNotContain("40000005", "40000006");

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                servicio.exportar(admin, RonaldmichaReporte.PAGOS, Formato.EXCEL, p).contenido()))) {
            List<String> hojas = new ArrayList<>();
            libro.forEach(h -> hojas.add(h.getSheetName()));
            assertThat(hojas).containsExactly("Resumen", "Banco Ejemplo", "Sin cuenta");
        }
    }

    @Test
    void afpnetYArchivosPlame() throws IOException {
        Parametros p = new Parametros(OCTUBRE, null, false);
        assertThat(servicio.observaciones(admin, RonaldmichaReporte.AFPNET, p)).isEmpty();
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                servicio.exportar(admin, RonaldmichaReporte.AFPNET, Formato.EXCEL, p).contenido()))) {
            assertThat(libro.getSheet("AFP Integra").getRow(0).getCell(1).getStringCellValue()).isEqualTo("CUSPP");
            assertThat(libro.getSheet("AFP Integra").getRow(1).getCell(1).getStringCellValue()).isEqualTo("000000CFHUA0");
        }

        assertThat(servicio.observaciones(admin, RonaldmichaReporte.PLAME, p)).anyMatch(o -> o.contains("RUC"));
        Archivo zip = servicio.exportar(admin, RonaldmichaReporte.PLAME, Formato.ZIP, p);
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
        assertThat(servicio.vistaPrevia(admin, RonaldmichaReporte.PLAME, p).orientacion())
                .isEqualTo(RonaldmichaDocumentModel.Orientacion.HORIZONTAL);
    }

    @Test
    void enviarPorCorreoSinServidorConfigurado() {
        Envio envio = servicio.enviarPorCorreo(admin, RonaldmichaReporte.BOLETA_PAGO, new Parametros(OCTUBRE, null, true));

        assertThat(envio.enviados()).isZero();
        assertThat(envio.registrados()).isEqualTo(6);
        assertThat(envio.sinCorreo()).containsExactly("Ramos Condori, Jorge Luis");
        assertThat(envio.mensaje()).contains("no está configurado");
        assertThat(servicio.emitidos(admin)).hasSize(6).allMatch(d -> d.estado() == RonaldmichaEstadoDocumento.GENERADO);
        assertThat(auditoria.recientes(3)).anyMatch(r -> r.accion().equals("ENVIO_CORREO"));
    }

    @Test
    void permisosYDocumentosQueRequierenCese() {
        assertThatThrownBy(() -> servicio.exportar(carlos, RonaldmichaReporte.BOLETA_PAGO, Formato.PDF,
                new Parametros(OCTUBRE, id("40000002"), false))).isInstanceOf(RonaldmichaValidacionException.class)
                .hasMessageContaining("aún no está disponible");
        periodoRepositorio.cerrar(OCTUBRE, "admin", java.time.LocalDateTime.of(2026, 10, 31, 18, 0));
        assertThat(new String(servicio.exportar(carlos, RonaldmichaReporte.BOLETA_PAGO, Formato.PDF,
                new Parametros(OCTUBRE, id("40000002"), false)).contenido(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(servicio.emitidos(carlos)).hasSize(1);
        assertThatThrownBy(() -> servicio.exportar(carlos, RonaldmichaReporte.BOLETA_PAGO, Formato.PDF,
                new Parametros(OCTUBRE, id("40000001"), false))).isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.vistaPrevia(carlos, RonaldmichaReporte.PLANILLA, new Parametros(OCTUBRE, null, false)))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);

        long rosa = id("40000001");
        assertThatThrownBy(() -> servicio.vistaPrevia(admin, RonaldmichaReporte.CERTIFICADO_TRABAJO, new Parametros(OCTUBRE, rosa, false)))
                .isInstanceOf(RonaldmichaValidacionException.class).hasMessageContaining("constancia de trabajo");
        assertThatThrownBy(() -> servicio.vistaPrevia(admin, RonaldmichaReporte.LIQUIDACION, new Parametros(OCTUBRE, rosa, false)))
                .isInstanceOf(RonaldmichaValidacionException.class).hasMessageContaining("no tiene liquidación");

        liquidaciones.guardar(admin, null, new RonaldmichaLiquidacionService.Borrador(rosa, LocalDate.of(2026, 10, 31),
                RonaldmichaMotivoCese.RENUNCIA, null, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO), false);
        assertThat(servicio.vistaPrevia(admin, RonaldmichaReporte.LIQUIDACION, new Parametros(OCTUBRE, rosa, false)).titulo())
                .isEqualTo("LIQUIDACIÓN DE BENEFICIOS SOCIALES");
        RonaldmichaDocumentModel carta = servicio.vistaPrevia(admin, RonaldmichaReporte.LIBERACION_CTS, new Parametros(OCTUBRE, rosa, false));
        assertThat(carta.bloques().toString()).contains("Caja Ejemplo", "100-0000001", "31/10/2026");
    }

    @Test
    void losTreceReportesTienenVistaPreviaYExportanCadaFormato() {
        long rosa = id("40000001");
        liquidaciones.guardar(admin, null, new RonaldmichaLiquidacionService.Borrador(rosa, LocalDate.of(2026, 10, 31),
                RonaldmichaMotivoCese.RENUNCIA, null, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO), false);
        int emitidos = 0;
        for (RonaldmichaReporte r : RonaldmichaReporte.values()) {
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

    private Parametros parametrosQueFuncionan(RonaldmichaReporte r) {
        if (!r.porTrabajador()) {
            return new Parametros(OCTUBRE, null, false);
        }
        for (var t : servicio.trabajadores(admin, r, OCTUBRE)) {
            Parametros p = new Parametros(OCTUBRE, t.id(), false);
            try {
                servicio.vistaPrevia(admin, r, p);
                return p;
            } catch (RonaldmichaValidacionException noAplica) {
            }
        }
        throw new AssertionError("Ningún trabajador tiene " + r.titulo());
    }

    @Test
    void cesadosPrimeroYUnaSolaCopia() {
        long rosa = id("40000001");
        liquidaciones.guardar(admin, null, new RonaldmichaLiquidacionService.Borrador(rosa, LocalDate.of(2026, 10, 31),
                RonaldmichaMotivoCese.RENUNCIA, null, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO), false);
        assertThat(servicio.trabajadores(admin, RonaldmichaReporte.CERTIFICADO_TRABAJO, OCTUBRE)).hasSize(7)
                .first().extracting(t -> t.numDoc()).isEqualTo("40000001");

        Parametros una = new Parametros(OCTUBRE, rosa, false, false);
        assertThat(servicio.vistaPrevia(admin, RonaldmichaReporte.BOLETA_PAGO, una).copias()).isEmpty();
        assertThat(servicio.vistaPrevia(admin, RonaldmichaReporte.BOLETA_PAGO, new Parametros(OCTUBRE, rosa, false)).copias())
                .hasSize(2);
    }

    @Test
    void elTrabajadorSoloDescargaPdfDePeriodosCerrados() {
        long carlosId = id("40000002");
        periodoRepositorio.cerrar(OCTUBRE, "admin", LocalDateTime.of(2026, 10, 31, 18, 0));
        assertThatThrownBy(() -> servicio.exportar(carlos, RonaldmichaReporte.CONSTANCIA_TRABAJO, Formato.WORD,
                new Parametros(OCTUBRE, carlosId, false))).isInstanceOf(RonaldmichaAccesoDenegadoException.class)
                .hasMessageContaining("solo en PDF");
        assertThat(servicio.exportar(carlos, RonaldmichaReporte.CONSTANCIA_TRABAJO, Formato.PDF,
                new Parametros(OCTUBRE, carlosId, false)).numeros()).singleElement().asString().startsWith("CON-");
        assertThatThrownBy(() -> servicio.vistaPrevia(carlos, RonaldmichaReporte.GRATIFICACION, new Parametros(OCTUBRE, carlosId, false)))
                .isInstanceOf(RonaldmichaValidacionException.class).hasMessageContaining("aún no está disponible");
        assertThat(servicio.vistaPrevia(carlos, RonaldmichaReporte.RENTA_QUINTA, new Parametros(OCTUBRE, carlosId, false)).titulo())
                .contains("RETENCIONES");
    }

    @Test
    void certificadoDeRentasDeQuinta() {
        RonaldmichaDocumentModel d = servicio.vistaPrevia(aprobador, RonaldmichaReporte.RENTA_QUINTA, new Parametros(OCTUBRE, id("40000003"), false));

        assertThat(d.titulo()).isEqualTo("CERTIFICADO DE RENTAS Y RETENCIONES DE QUINTA CATEGORÍA");
        assertThat(d.bloques().toString()).contains("enero", "Gratificación de julio 2026", "Deducción de 7 UIT",
                "Retenido por el empleador", "TOTAL RETENIDO");
    }

    @Test
    void portalMisDocumentosYCargoElectronico() {
        List<RonaldmichaReportesService.BoletaPortal> antes = servicio.misBoletas(carlos);
        assertThat(antes.getFirst().mes()).isEqualTo(OCTUBRE);
        assertThat(antes.getFirst().disponible()).isFalse();
        assertThat(antes).hasSizeLessThanOrEqualTo(RonaldmichaReportesService.MESES_PORTAL);
        assertThatThrownBy(() -> servicio.descargarMiBoleta(carlos, OCTUBRE)).isInstanceOf(RonaldmichaValidacionException.class)
                .hasMessageContaining("aún no está disponible");
        assertThatThrownBy(() -> servicio.misBoletas(admin)).isInstanceOf(RonaldmichaAccesoDenegadoException.class);

        periodoRepositorio.cerrar(OCTUBRE, "admin", LocalDateTime.of(2026, 10, 31, 18, 0));
        Archivo primera = servicio.descargarMiBoleta(carlos, OCTUBRE);
        Archivo segunda = servicio.descargarMiBoleta(carlos, OCTUBRE);
        assertThat(new String(primera.contenido(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(primera.numeros()).containsExactly("BOL-2026-000001");
        assertThat(segunda.numeros()).isEqualTo(primera.numeros());
        RonaldmichaReportesService.BoletaPortal octubre = servicio.misBoletas(carlos).getFirst();
        assertThat(octubre.disponible()).isTrue();
        RonaldmichaDocumentoEmitido boleta = octubre.documento().orElseThrow();
        assertThat(boleta.cargoPendiente()).isTrue();

        String deRosa = servicio.exportar(admin, RonaldmichaReporte.BOLETA_PAGO, Formato.PDF,
                new Parametros(OCTUBRE, id("40000001"), false)).numeros().getFirst();
        assertThatThrownBy(() -> servicio.confirmarRecepcion(carlos, emitido(deRosa).id()))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);

        RonaldmichaDocumentoEmitido recibido = servicio.confirmarRecepcion(carlos, boleta.id());
        assertThat(recibido.estado()).isEqualTo(RonaldmichaEstadoDocumento.RECIBIDO);
        assertThat(recibido.fechaRecepcion()).isEqualTo(LocalDateTime.of(2026, 10, 15, 10, 0));
        assertThat(recibido.recepcion()).contains("electrónico", "40000002");
        assertThatThrownBy(() -> servicio.confirmarRecepcion(carlos, boleta.id())).isInstanceOf(RonaldmichaValidacionException.class);
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
