package com.planillaperu.service;

import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.EstadoDia;
import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.TipoDia;
import com.planillaperu.domain.RonaldmichaHorario;
import com.planillaperu.domain.RonaldmichaMarcacion;
import com.planillaperu.domain.RonaldmichaNovedad;
import com.planillaperu.domain.RonaldmichaOrigenAsistencia;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaTratamientoHe;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.export.RonaldmichaAsistenciaExcel;
import com.planillaperu.export.RonaldmichaLectorTabla;
import com.planillaperu.export.RonaldmichaLectorTabla.Fila;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaParametrosRepository;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.service.RonaldmichaAsistenciaService.MesAsistencia;
import com.planillaperu.service.RonaldmichaAsistenciaService.NovedadesPlanilla;
import com.planillaperu.service.RonaldmichaAsistenciaService.ResultadoImportacion;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaAsistenciaServiceTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);

    @TempDir
    Path carpeta;

    private RonaldmichaDatabase db;
    private RonaldmichaAuditoriaService auditoria;
    private RonaldmichaAsistenciaService servicio;
    private RonaldmichaTrabajadorRepository trabajadores;
    private RonaldmichaUsuario admin;
    private RonaldmichaUsuario aprobador;
    private RonaldmichaTrabajador carlos;

    @BeforeEach
    void preparar() {
        db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
        auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        trabajadores = new RonaldmichaTrabajadorRepository(db);
        RonaldmichaPeriodoRepository periodos = new RonaldmichaPeriodoRepository(db);
        RonaldmichaParametrosService parametros = new RonaldmichaParametrosService(new RonaldmichaParametrosRepository(db), periodos, trabajadores,
                auditoria, reloj);
        servicio = new RonaldmichaAsistenciaService(new RonaldmichaAsistenciaRepository(db), trabajadores, new RonaldmichaPeriodoService(periodos, reloj),
                parametros, auditoria, reloj);
        admin = new RonaldmichaUsuario(1L, "admin", "", RonaldmichaRol.ADMIN, null, true);
        aprobador = new RonaldmichaUsuario(3L, "aprobador", "", RonaldmichaRol.APROBADOR, null, true);
        carlos = porDni("40000002");
    }

    @Test
    void validaElHorario() {
        assertThat(servicio.validarHorario(horario("08:00", "17:00", 60, "1,2,3,4,5"))).isEmpty();
        assertThat(servicio.validarHorario(horario("17:00", "08:00", 60, "1,2,3,4,5")))
                .containsExactly("La salida debe ser posterior al ingreso.");
        assertThat(servicio.validarHorario(horario("08:00", "15:00", 30, "1,2,3,4,5")))
                .anyMatch(e -> e.contains("refrigerio debe ser de al menos 45 minutos"));
        assertThat(servicio.validarHorario(horario("07:00", "19:00", 60, "1,2,3,4,5")))
                .anyMatch(e -> e.contains("entre 1 y 10 horas (es de 11 h)"));
        assertThat(servicio.validarHorario(horario("08:00", "18:00", 60, "1,2,3,4,5,6")))
                .anyMatch(e -> e.contains("no puede pasar de 48 horas (es de 54 h)"));
        assertThat(servicio.validarHorario(horario("08:00", "17:00", 60, "1,2,3,4,5,6,7")))
                .containsExactly("Debe quedar al menos un día de descanso a la semana.");
        RonaldmichaHorario direccion = new RonaldmichaHorario(LocalTime.of(8, 0), LocalTime.of(18, 0), 60, 0, RonaldmichaHorario.leerDias("1,2,3,4,5,6"), true);
        assertThat(servicio.validarHorario(direccion)).isEmpty();
    }

    @Test
    void guardaElHorarioYAudita() {
        RonaldmichaHorario nuevo = horario("07:30", "16:30", 60, "1,2,3,4,5");

        servicio.guardarHorario(admin, carlos.id(), nuevo);

        assertThat(trabajadores.horario(carlos.id())).contains(nuevo);
        assertThat(acciones()).contains("HORARIO_ACTUALIZADO");
        assertThatThrownBy(() -> servicio.guardarHorario(aprobador, carlos.id(), nuevo))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    @Test
    void consultaElMesConFeriadosYResumen() {
        servicio.guardarDia(admin, marca(2, "08:00", "20:30").conHorasExtra(true, RonaldmichaTratamientoHe.PAGAR));

        MesAsistencia m = servicio.consultar(admin, carlos.id(), OCTUBRE);

        assertThat(m.dias()).hasSize(31);
        assertThat(m.dias().get(7).tipo()).isEqualTo(TipoDia.FERIADO);
        assertThat(m.feriados()).extracting(f -> f.nombre()).containsExactly("Combate de Angamos");
        assertThat(m.origen()).isEqualTo(RonaldmichaOrigenAsistencia.SISTEMA);
        assertThat(m.resumen().he25Min()).isEqualTo(120);
        assertThat(m.resumen().he35Min()).isEqualTo(90);
        assertThat(m.importes()).isPresent();
        assertThat(m.importes().get().he25()).isPositive();
        assertThat(acciones()).contains("ASISTENCIA_EDITADA");
    }

    @Test
    void alertasDeHorasExtraSinAutorizarYSalidaNocturna() {
        servicio.guardarDia(admin, marca(2, "08:00", "22:30"));

        assertThat(servicio.consultar(admin, carlos.id(), OCTUBRE).alertas())
                .anyMatch(a -> a.startsWith("Horas extra sin autorización en 1 día"))
                .anyMatch(a -> a.contains("Salida después de las 10 p. m. el 02/10"));
        assertThat(servicio.consultar(admin, porDni("40000003").id(), OCTUBRE).alertas())
                .anyMatch(a -> a.startsWith("Personal de dirección o no fiscalizado"));
    }

    @Test
    void salidaAntesDeLaEntradaSeRechaza() {
        assertThatThrownBy(() -> servicio.guardarDia(admin, marca(2, "17:00", "08:00")))
                .isInstanceOf(RonaldmichaValidacionException.class)
                .hasMessageContaining("debe ser posterior a la entrada");
    }

    @Test
    void llenarConHorarioYBorrarMes() {
        servicio.guardarDia(admin, RonaldmichaMarcacion.vacia(carlos.id(), OCTUBRE.atDay(6)).conNovedad(RonaldmichaNovedad.VACACIONES));

        assertThat(servicio.llenarConHorario(admin, carlos.id(), OCTUBRE)).isEqualTo(9);
        assertThat(servicio.llenarConHorario(admin, carlos.id(), OCTUBRE)).isZero();
        MesAsistencia m = servicio.consultar(admin, carlos.id(), OCTUBRE);
        assertThat(m.resumen().faltas()).isZero();
        assertThat(m.resumen().asistencias()).isEqualTo(9);
        assertThat(m.dias().get(15).estado()).isEqualTo(EstadoDia.POR_REGISTRAR);

        assertThat(servicio.borrarMes(admin, carlos.id(), OCTUBRE)).isEqualTo(10);
        assertThat(servicio.consultar(admin, carlos.id(), OCTUBRE).origen()).isEqualTo(RonaldmichaOrigenAsistencia.SIN_REGISTRO);
        assertThat(acciones()).contains("ASISTENCIA_LLENADA", "ASISTENCIA_BORRADA");
    }

    @Test
    void periodoCerradoEsSoloLectura() throws SQLException {
        cerrar("202609");
        YearMonth setiembre = YearMonth.of(2026, 9);

        assertThat(servicio.consultar(admin, carlos.id(), setiembre).soloLectura()).isTrue();
        assertThatThrownBy(() -> servicio.guardarDia(admin, RonaldmichaMarcacion.vacia(carlos.id(), setiembre.atDay(1))
                .conHoras(LocalTime.of(8, 0), LocalTime.of(17, 0))))
                .hasMessageContaining("está cerrado");
        assertThatThrownBy(() -> servicio.llenarConHorario(admin, carlos.id(), setiembre)).hasMessageContaining("cerrado");
        assertThatThrownBy(() -> servicio.importar(List.of(), "x.csv", admin, setiembre))
                .hasMessageContaining("solo se importan días del periodo abierto");
    }

    @Test
    void agregaYQuitaDiasNoLaborables() {
        LocalDate viernes = OCTUBRE.atDay(9);

        servicio.agregarDiaNoLaborable(admin, viernes, null);

        MesAsistencia m = servicio.consultar(admin, carlos.id(), OCTUBRE);
        assertThat(m.dias().get(8).tipo()).isEqualTo(TipoDia.FERIADO);
        assertThat(m.dias().get(8).estado()).isEqualTo(EstadoDia.FERIADO_PAGADO);
        assertThat(m.feriados()).filteredOn(f -> f.agregado()).singleElement()
                .satisfies(f -> assertThat(f.nombre()).isEqualTo(RonaldmichaAsistenciaService.MOTIVO_NO_LABORABLE));
        assertThatThrownBy(() -> servicio.agregarDiaNoLaborable(admin, viernes, null)).hasMessageContaining("ya está agregado");
        assertThatThrownBy(() -> servicio.agregarDiaNoLaborable(admin, OCTUBRE.atDay(8), null))
                .hasMessageContaining("ya es feriado nacional (Combate de Angamos)");

        servicio.quitarDiaNoLaborable(admin, viernes);
        assertThat(servicio.feriados(OCTUBRE)).hasSize(1);
        assertThat(acciones()).contains("FERIADO_AGREGADO", "FERIADO_QUITADO");
    }

    @Test
    void importaConErroresPorFila() {
        List<Fila> filas = RonaldmichaLectorTabla.leerCsv(String.join("\n",
                "Asistencia de octubre",
                "Documento;Apellidos y nombres;Fecha (AAAA-MM-DD);Entrada (HH:MM);Salida;Novedad;HE autorizada (SI/NO)",
                "40000002;Flores;2026-10-01;08:00;17:00;;",
                "99999999;Nadie;2026-10-01;08:00;17:00;;",
                "40000002;Flores;2026-10-02;25:00;17:00;;",
                "40000002;Flores;2026-10-05;17:00;08:00;;",
                "40000002;Flores;2026-11-02;08:00;17:00;;",
                "40000002;Flores;06/10/2026;8:00 a. m.;5:30 p. m.;Normal;SI",
                "40000002;Flores;2026-10-07;;;Vacaciones;",
                "40000002;Flores;2026-10-09;;;;"));

        ResultadoImportacion r = servicio.importar(filas, "asistencia.csv", admin, OCTUBRE);

        assertThat(r.dias()).isEqualTo(3);
        assertThat(r.trabajadores()).isEqualTo(1);
        assertThat(r.observados()).containsExactly(
                "Fila 4: DNI 99999999 no registrado",
                "Fila 5: entrada: hora inválida «25:00»",
                "Fila 6: salida antes de la entrada",
                "Fila 7: fecha 02/11/2026 fuera del periodo 10/2026");
        assertThat(r.mensaje()).startsWith("3 días importados (1 trabajador) · Observados: Fila 4: DNI 99999999 no registrado");

        MesAsistencia m = servicio.consultar(admin, carlos.id(), OCTUBRE);
        assertThat(m.origen()).isEqualTo(RonaldmichaOrigenAsistencia.EXCEL);
        assertThat(m.dias().get(5).marcacion().salida()).isEqualTo(LocalTime.of(17, 30));
        assertThat(m.dias().get(5).marcacion().heAutorizada()).isTrue();
        assertThat(m.dias().get(6).estado()).isEqualTo(EstadoDia.VACACIONES);
        assertThat(acciones()).contains("ASISTENCIA_IMPORTADA");

        servicio.guardarDia(admin, m.dias().get(0).marcacion().conHoras(LocalTime.of(8, 10), LocalTime.of(17, 0)));
        assertThat(servicio.consultar(admin, carlos.id(), OCTUBRE).origen()).isEqualTo(RonaldmichaOrigenAsistencia.EXCEL_AJUSTES);
    }

    @Test
    void importaElCsvDelRelojSinEncabezados() {
        List<Fila> filas = RonaldmichaLectorTabla.leerCsv("40000002,2026-10-01,08:00,17:00\n40000004,01/10/2026,09:05,17:00\n");

        ResultadoImportacion r = servicio.importar(filas, "reloj.csv", admin, OCTUBRE);

        assertThat(r.dias()).isEqualTo(2);
        assertThat(r.trabajadores()).isEqualTo(2);
        assertThat(r.observados()).isEmpty();
    }

    @Test
    void sinEncabezadosReconociblesSeRechaza() {
        assertThatThrownBy(() -> servicio.importar(RonaldmichaLectorTabla.leerCsv("a;b\n1;2\n"), "x.csv", admin, OCTUBRE))
                .hasMessageContaining("No se encontró la fila de encabezados");
    }

    @Test
    void plantillaExcelSeVuelveAImportar() throws IOException {
        servicio.guardarDia(admin, marca(1, "08:00", "17:00"));

        byte[] plantilla = servicio.plantillaExcel(admin, OCTUBRE);

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(plantilla))) {
            assertThat(hojas(libro)).containsExactly("Asistencia", "Instrucciones", "Feriados 2026");
            Sheet hoja = libro.getSheet("Asistencia");
            List<String> cabecera = new ArrayList<>();
            hoja.getRow(0).forEach(c -> cabecera.add(c.getStringCellValue()));
            assertThat(cabecera).containsExactlyElementsOf(RonaldmichaAsistenciaExcel.COLUMNAS);
            assertThat(hoja.getLastRowNum()).isEqualTo(7 * 31);
            assertThat(libro.getSheet("Feriados 2026").getLastRowNum()).isEqualTo(16);
        }

        List<Fila> filas = RonaldmichaLectorTabla.leerXlsx(new ByteArrayInputStream(plantilla), RonaldmichaAsistenciaExcel.HOJA_ASISTENCIA);
        ResultadoImportacion r = servicio.importar(filas, "plantilla.xlsx", admin, OCTUBRE);

        assertThat(r.dias()).isEqualTo(1);
        assertThat(r.observados()).isEmpty();
        assertThat(servicio.consultar(admin, carlos.id(), OCTUBRE).origen()).isEqualTo(RonaldmichaOrigenAsistencia.EXCEL);
    }

    @Test
    void exportaResumenDetalleYFeriados() throws IOException {
        servicio.guardarDia(admin, marca(2, "08:00", "20:30"));

        byte[] excel = servicio.exportarExcel(aprobador, OCTUBRE);

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertThat(hojas(libro)).containsExactly("Resumen", "Detalle diario", "Feriados");
            assertThat(libro.getSheet("Resumen").getLastRowNum()).isEqualTo(7);
            assertThat(libro.getSheet("Detalle diario").getLastRowNum()).isEqualTo(7 * 31);
            assertThat(libro.getSheet("Feriados").getRow(1).getCell(2).getStringCellValue()).isEqualTo("Combate de Angamos");
        }
        assertThat(acciones()).contains("ASISTENCIA_EXPORTADA");
    }

    @Test
    void elTrabajadorSoloVeYDescargaLoSuyo() throws IOException {
        RonaldmichaUsuario trabajador = new RonaldmichaUsuario(5L, "40000002", "", RonaldmichaRol.TRABAJADOR, carlos.id(), true);

        assertThat(servicio.consultar(trabajador, carlos.id(), OCTUBRE).trabajador().numDoc()).isEqualTo("40000002");
        assertThatThrownBy(() -> servicio.consultar(trabajador, porDni("40000003").id(), OCTUBRE))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.guardarDia(trabajador, marca(1, "08:00", "17:00")))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.exportarExcel(trabajador, OCTUBRE)).isInstanceOf(RonaldmichaAccesoDenegadoException.class);

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(servicio.exportarMiAsistencia(trabajador, OCTUBRE)))) {
            assertThat(libro.getSheet("Resumen").getLastRowNum()).isEqualTo(1);
            assertThat(libro.getSheet("Resumen").getRow(1).getCell(0).getStringCellValue()).isEqualTo("40000002");
        }
    }

    @Test
    void laPlanillaTomaLaAsistenciaSiHayMarcacionesYSiNoLasNovedadesManuales() throws SQLException {
        try (Connection c = db.conexion(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO novedad_mes (trabajador_id, periodo, inasistencias, tardanza_min, vacaciones)"
                    + " VALUES (" + carlos.id() + ", '202610', 2, 30, 1)");
            st.executeUpdate("INSERT INTO horas_extra_manual (trabajador_id, fecha, horas, tratamiento, autorizada)"
                    + " VALUES (" + carlos.id() + ", '2026-10-02', '3.5', 'pago', 1),"
                    + " (" + carlos.id() + ", '2026-10-03', '4', 'comp', 1)");
        }

        NovedadesPlanilla manual = servicio.novedadesParaPlanilla(carlos.id(), OCTUBRE);
        assertThat(manual.desdeAsistencia()).isFalse();
        assertThat(manual.faltas()).isEqualTo(2);
        assertThat(manual.tardanzaMin()).isEqualTo(30);
        assertThat(manual.he25Min()).isEqualTo(120);
        assertThat(manual.he35Min()).isEqualTo(90);

        servicio.guardarDia(admin, marca(1, "08:20", "17:00"));
        NovedadesPlanilla asistencia = servicio.novedadesParaPlanilla(carlos.id(), OCTUBRE);
        assertThat(asistencia.desdeAsistencia()).isTrue();
        assertThat(asistencia.tardanzaMin()).isEqualTo(15);
        assertThat(asistencia.faltas()).isEqualTo(9);
    }

    private RonaldmichaMarcacion marca(int dia, String ingreso, String salida) {
        return RonaldmichaMarcacion.vacia(carlos.id(), OCTUBRE.atDay(dia)).conHoras(LocalTime.parse(ingreso), LocalTime.parse(salida));
    }

    private static RonaldmichaHorario horario(String ingreso, String salida, int refrigerio, String dias) {
        return new RonaldmichaHorario(LocalTime.parse(ingreso), LocalTime.parse(salida), refrigerio, 5, RonaldmichaHorario.leerDias(dias), false);
    }

    private RonaldmichaTrabajador porDni(String dni) {
        return trabajadores.listarTodos().stream().filter(t -> t.numDoc().equals(dni)).findFirst().orElseThrow();
    }

    private void cerrar(String periodo) throws SQLException {
        try (Connection c = db.conexion(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO periodo (periodo, estado, cerrado_por, fecha_cierre) VALUES ('" + periodo
                    + "', 'Cerrado', 'admin', '2026-10-01T10:00:00')");
        }
    }

    private List<String> acciones() {
        return auditoria.recientes(50).stream().map(r -> r.accion()).toList();
    }

    private static List<String> hojas(Workbook libro) {
        return Stream.iterate(0, i -> i < libro.getNumberOfSheets(), i -> i + 1).map(libro::getSheetName).toList();
    }
}
