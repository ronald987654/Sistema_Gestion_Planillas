package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaAprobacion;
import com.planillaperu.domain.RonaldmichaAprobacionPendiente;
import com.planillaperu.domain.RonaldmichaHorario;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaSalud;
import com.planillaperu.domain.RonaldmichaSistemaPension;
import com.planillaperu.domain.RonaldmichaTipoComision;
import com.planillaperu.domain.RonaldmichaTipoDocumento;
import com.planillaperu.domain.RonaldmichaTipoPago;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.export.RonaldmichaLectorTabla;
import com.planillaperu.export.RonaldmichaPlantillaTrabajadores;
import com.planillaperu.repository.RonaldmichaAprobacionRepository;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaEmpresaRepository;
import com.planillaperu.repository.RonaldmichaParametrosRepository;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
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
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaTrabajadorServiceTest {

    private static final YearMonth OCTUBRE_2026 = YearMonth.of(2026, 10);
    private static final YearMonth SETIEMBRE_2026 = YearMonth.of(2026, 9);
    private static final RonaldmichaHorario COMPLETO = horario("08:00", "17:00", 60);

    @TempDir
    Path carpeta;

    private RonaldmichaAuditoriaService auditoria;
    private RonaldmichaTrabajadorService servicio;
    private RonaldmichaAprobacionService aprobaciones;
    private RonaldmichaUsuario admin;
    private RonaldmichaUsuario admin2;
    private RonaldmichaUsuario aprobador;

    @BeforeEach
    void preparar() {
        RonaldmichaDatabase db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
        auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        RonaldmichaTrabajadorRepository repo = new RonaldmichaTrabajadorRepository(db);
        RonaldmichaParametrosService parametros = new RonaldmichaParametrosService(new RonaldmichaParametrosRepository(db), new RonaldmichaPeriodoRepository(db),
                repo, auditoria, reloj);
        RonaldmichaAprobacionRepository aprobacionRepo = new RonaldmichaAprobacionRepository(db);
        servicio = new RonaldmichaTrabajadorService(repo, aprobacionRepo, parametros,
                new RonaldmichaEmpresaService(new RonaldmichaEmpresaRepository(db), auditoria), auditoria, reloj);
        aprobaciones = new RonaldmichaAprobacionService(aprobacionRepo, auditoria, reloj);
        admin = new RonaldmichaUsuario(1L, "admin", "", RonaldmichaRol.ADMIN, porDni("40000001").id(), true);
        admin2 = new RonaldmichaUsuario(9L, "admin2", "", RonaldmichaRol.ADMIN, null, true);
        aprobador = new RonaldmichaUsuario(3L, "aprobador", "", RonaldmichaRol.APROBADOR, porDni("40000003").id(), true);
    }

    @Test
    void registraConElSiguienteCodigoYAudita() {
        RonaldmichaTrabajadorService.Guardado g = servicio.guardar(nuevo("40000010"), COMPLETO, admin, OCTUBRE_2026);

        assertThat(g.trabajador().codigo()).isEqualTo("T0008");
        assertThat(g.pendiente()).isEmpty();
        assertThat(servicio.horario(g.trabajador().id())).contains(COMPLETO);
        assertThat(auditoria.recientes(5)).extracting(r -> r.accion()).contains("TRABAJADOR_REGISTRADO");
    }

    @Test
    void dniDeOchoDigitosYUnico() {
        assertThat(servicio.validar(nuevo("1234567"), COMPLETO, OCTUBRE_2026)).contains("El DNI debe tener 8 dígitos.");
        assertThat(servicio.validar(nuevo("4000000A"), COMPLETO, OCTUBRE_2026)).contains("El DNI debe tener 8 dígitos.");
        assertThat(servicio.validar(nuevo("40000001"), COMPLETO, OCTUBRE_2026))
                .anySatisfy(e -> assertThat(e).startsWith("DNI duplicado"));
        assertThat(servicio.validar(nuevo("40000010"), COMPLETO, OCTUBRE_2026)).isEmpty();
    }

    @Test
    void sueldoNoMenorQueLaRmvDelPeriodoSalvoTiempoParcial() {
        RonaldmichaTrabajador t = conSueldo(nuevo("40000010"), "1200.00");

        assertThat(servicio.validar(t, COMPLETO, OCTUBRE_2026))
                .anySatisfy(e -> assertThat(e).contains("menor que la RMV de 10/2026 (S/ 1,230.00)"));
        assertThat(servicio.validar(t, COMPLETO, SETIEMBRE_2026)).isEmpty();
        assertThat(servicio.validar(t, horario("08:00", "11:00", 0), OCTUBRE_2026)).isEmpty();
    }

    @Test
    void sisSoloParaMicroempresa() {
        RonaldmichaTrabajador general = conSalud(nuevo("40000010"), RonaldmichaSalud.SIS, RonaldmichaRegimenLaboral.GENERAL);
        RonaldmichaTrabajador micro = conSalud(nuevo("40000010"), RonaldmichaSalud.SIS, RonaldmichaRegimenLaboral.MICRO);

        assertThat(servicio.validar(general, COMPLETO, OCTUBRE_2026))
                .contains("El SIS solo corresponde a trabajadores de microempresa.");
        assertThat(servicio.validar(micro, COMPLETO, OCTUBRE_2026)).isEmpty();
    }

    @Test
    void cusppObligatorioEnAfpYNoSeGuardaEnOnp() {
        RonaldmichaTrabajador sinCuspp = conPension(nuevo("40000010"), RonaldmichaSistemaPension.INTEGRA, null, RonaldmichaTipoComision.FLUJO);
        RonaldmichaTrabajador corto = conPension(nuevo("40000010"), RonaldmichaSistemaPension.INTEGRA, "ABC", RonaldmichaTipoComision.FLUJO);
        RonaldmichaTrabajador sinComision = conPension(nuevo("40000010"), RonaldmichaSistemaPension.INTEGRA, "000000ABCDE1", null);
        RonaldmichaTrabajador onp = conPension(nuevo("40000010"), RonaldmichaSistemaPension.ONP, "000000ABCDE1", RonaldmichaTipoComision.FLUJO);

        assertThat(servicio.validar(sinCuspp, COMPLETO, OCTUBRE_2026)).anySatisfy(e -> assertThat(e).startsWith("El CUSPP"));
        assertThat(servicio.validar(corto, COMPLETO, OCTUBRE_2026)).anySatisfy(e -> assertThat(e).startsWith("El CUSPP"));
        assertThat(servicio.validar(sinComision, COMPLETO, OCTUBRE_2026))
                .contains("Elija el tipo de comisión AFP (flujo o mixta).");

        RonaldmichaTrabajador guardado = servicio.guardar(onp, COMPLETO, admin, OCTUBRE_2026).trabajador();
        assertThat(guardado.cuspp()).isNull();
        assertThat(guardado.tipoComision()).isNull();
    }

    @Test
    void depositoEnCuentaExigeBancoYCuenta() {
        RonaldmichaTrabajador t = conCuentas(nuevo("40000010"), null, null, null, null);

        assertThat(servicio.validar(t, COMPLETO, OCTUBRE_2026))
                .contains("Para depósito en cuenta indique el banco y la cuenta de sueldo.");
    }

    @Test
    void soloElAdministradorRegistra() {
        assertThatThrownBy(() -> servicio.guardar(nuevo("40000010"), COMPLETO, aprobador, OCTUBRE_2026))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    @Test
    void aportePensionUsaLosParametrosDelPeriodo() {
        assertThat(servicio.aportePension(RonaldmichaSistemaPension.ONP, null, new BigDecimal("1000.00"), false, OCTUBRE_2026)
                .aporte()).isEqualByComparingTo("130.00");
        assertThat(servicio.asignacionFamiliar(SETIEMBRE_2026)).isEqualByComparingTo("113.00");
        assertThat(servicio.asignacionFamiliar(OCTUBRE_2026)).isEqualByComparingTo("123.00");
    }

    @Test
    void cambiarSueldoCreaAprobacionYNoSeAplicaDirecto() {
        RonaldmichaTrabajador carlos = porDni("40000002");
        RonaldmichaTrabajador editado = conCargo(conSueldo(carlos, "3000.00"), "Analista senior");

        RonaldmichaTrabajadorService.Guardado g = servicio.guardar(editado, COMPLETO, admin, OCTUBRE_2026);

        assertThat(g.pendiente()).isPresent();
        assertThat(g.pendiente().get().tipo()).isEqualTo("Cambio de sueldo");
        assertThat(g.pendiente().get().detalle()).contains("S/ 2,800.00 → S/ 3,000.00");
        RonaldmichaTrabajador actual = porDni("40000002");
        assertThat(actual.sueldo()).isEqualByComparingTo("2800.00");
        assertThat(actual.cargo()).isEqualTo("Analista senior");
        assertThat(auditoria.recientes(5)).extracting(r -> r.accion()).contains("APROBACION_SOLICITADA");
    }

    @Test
    void aprobadorApruebaYElCambioSeAplica() {
        RonaldmichaAprobacion a = solicitarSueldo("40000002", "3000.00", admin);

        aprobaciones.aprobar(a.id(), aprobador);

        assertThat(porDni("40000002").sueldo()).isEqualByComparingTo("3000.00");
        assertThat(aprobaciones.pendientes(admin)).extracting(p -> p.id()).doesNotContain(a.id());
        assertThat(auditoria.recientes(5)).extracting(r -> r.accion()).contains("APROBACION_APROBADA");
    }

    @Test
    void rechazoExigeMotivoYNoAplicaElCambio() {
        RonaldmichaAprobacion a = solicitarSueldo("40000002", "3000.00", admin);

        assertThatThrownBy(() -> aprobaciones.rechazar(a.id(), " ", aprobador))
                .isInstanceOf(RonaldmichaValidacionException.class);
        aprobaciones.rechazar(a.id(), "No corresponde", aprobador);

        assertThat(porDni("40000002").sueldo()).isEqualByComparingTo("2800.00");
        assertThat(aprobaciones.buscar(a.id()).orElseThrow().motivoRechazo()).isEqualTo("No corresponde");
    }

    @Test
    void nadieResuelveLoQueRegistroNiLoQueLoAfecta() {
        RonaldmichaAprobacion a = solicitarSueldo("40000002", "3000.00", admin);
        assertThatThrownBy(() -> aprobaciones.aprobar(a.id(), admin))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class)
                .hasMessageContaining("usted mismo");

        RonaldmichaAprobacion propio = solicitarSueldo("40000003", "6000.00", admin);
        assertThatThrownBy(() -> aprobaciones.aprobar(propio.id(), aprobador))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class)
                .hasMessageContaining("propio");
    }

    @Test
    void unAdministradorNoPuedeCambiarSuPropioSueldo() {
        RonaldmichaTrabajador propio = conSueldo(porDni("40000001"), "9000.00");

        assertThatThrownBy(() -> servicio.guardar(propio, COMPLETO, admin, OCTUBRE_2026))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class)
                .hasMessage("Un administrador no puede cambiar su propio sueldo.");
        assertThat(porDni("40000001").sueldo()).isEqualByComparingTo("3500.00");
        assertThat(servicio.guardar(propio, COMPLETO, admin2, OCTUBRE_2026).pendiente()).isPresent();
    }

    @Test
    void cambiarCuentasTambienPasaPorAprobacion() {
        RonaldmichaTrabajador t = porDni("40000002");
        RonaldmichaTrabajador editado = conCuentas(t, "Banco Nuevo", "999-1234567", t.bancoCts(), t.ctaCts());

        RonaldmichaAprobacion a = servicio.guardar(editado, COMPLETO, admin, OCTUBRE_2026).pendiente().orElseThrow();

        assertThat(a.tipo()).isEqualTo("Cambio de cuentas bancarias");
        assertThat(porDni("40000002").ctaSueldo()).isEqualTo("000-0000002");
        aprobaciones.aprobar(a.id(), aprobador);
        assertThat(porDni("40000002").ctaSueldo()).isEqualTo("999-1234567");
        assertThat(porDni("40000002").bancoSueldo()).isEqualTo("Banco Nuevo");
    }

    @Test
    void noSeAceptaOtroCambioSensibleMientrasHayUnoPendiente() {
        solicitarSueldo("40000002", "3000.00", admin);

        assertThatThrownBy(() -> solicitarSueldo("40000002", "3100.00", admin))
                .isInstanceOf(RonaldmichaValidacionException.class)
                .hasMessageContaining("Ya hay un cambio pendiente");
    }

    @Test
    void importaCsvEInformaLasFilasObservadas() {
        String plantilla = new String(RonaldmichaPlantillaTrabajadores.csv(), StandardCharsets.UTF_8);
        String ejemplo = plantilla.lines().toList().get(1);
        String csv = plantilla
                + ejemplo.replace("40000099", "40000001") + "\r\n"
                + ejemplo.replace("40000099", "123") + "\r\n"
                + ejemplo.replace("40000099", "40000098").replace("1500.00", "900.00") + "\r\n";

        RonaldmichaTrabajadorService.ResultadoImportacion r = servicio.importar(RonaldmichaLectorTabla.leerCsv(csv), "prueba.csv", admin,
                OCTUBRE_2026);

        assertThat(r.importados()).isEqualTo(1);
        assertThat(r.observados()).hasSize(3);
        assertThat(r.observados().get(0)).startsWith("Fila 3: DNI duplicado");
        assertThat(r.observados().get(1)).startsWith("Fila 4: El DNI debe tener 8 dígitos");
        assertThat(r.observados().get(2)).startsWith("Fila 5: El sueldo");
        assertThat(r.mensaje()).startsWith("1 importado · Observados: Fila 3: DNI duplicado");
        assertThat(servicio.todos()).anySatisfy(t -> {
            assertThat(t.numDoc()).isEqualTo("40000099");
            assertThat(t.codigo()).isEqualTo("T0008");
        });
        assertThat(servicio.horario(porDni("40000099").id()).orElseThrow().diasLaborables()).hasSize(5);
    }

    @Test
    void faltanColumnasObligatorias() {
        assertThatThrownBy(() -> servicio.importar(RonaldmichaLectorTabla.leerCsv("num_doc;nombres\n40000010;Ana\n"), null,
                admin, OCTUBRE_2026))
                .isInstanceOf(RonaldmichaValidacionException.class)
                .hasMessageContaining("Faltan columnas de la plantilla");
    }

    @Test
    void elExcelExportadoTieneLasColumnasDeLaPlantillaYSePuedeReimportar() throws IOException {
        byte[] excel = servicio.exportarExcel(admin);
        List<RonaldmichaLectorTabla.Fila> filas = RonaldmichaLectorTabla.leerXlsx(new ByteArrayInputStream(excel));

        assertThat(filas).hasSize(8);
        assertThat(filas.getFirst().valores()).containsExactlyElementsOf(RonaldmichaPlantillaTrabajadores.COLUMNAS);
        int dni = RonaldmichaPlantillaTrabajadores.COLUMNAS.indexOf("num_doc");
        int sueldo = RonaldmichaPlantillaTrabajadores.COLUMNAS.indexOf("sueldo");
        assertThat(filas.subList(1, 8)).extracting(f -> f.valores().get(dni)).contains("40000001", "40000007");
        assertThat(filas.subList(1, 8)).extracting(f -> f.valores().get(sueldo)).contains("3500", "1300");

        RonaldmichaTrabajadorService.ResultadoImportacion r = servicio.importar(filas, "export.xlsx", admin, OCTUBRE_2026);
        assertThat(r.importados()).isZero();
        assertThat(r.observados()).hasSize(7).allSatisfy(o -> assertThat(o).contains("DNI duplicado"));
    }

    @Test
    void unTrabajadorNoPuedeExportar() {
        RonaldmichaUsuario trabajador = new RonaldmichaUsuario(2L, "40000002", "", RonaldmichaRol.TRABAJADOR, porDni("40000002").id(), true);

        assertThatThrownBy(() -> servicio.exportarExcel(trabajador)).isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    @Test
    void elTrabajadorActualizaSuContactoYPideElCambioDeCuenta() {
        RonaldmichaTrabajador carlosFicha = porDni("40000002");
        RonaldmichaUsuario carlos = new RonaldmichaUsuario(2L, "40000002", "", RonaldmichaRol.TRABAJADOR, carlosFicha.id(), true);

        assertThat(servicio.misDatos(carlos).trabajador().numDoc()).isEqualTo("40000002");
        assertThatThrownBy(() -> servicio.misDatos(admin)).isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.actualizarContacto(carlos, "abc", "sin-arroba"))
                .isInstanceOf(RonaldmichaValidacionException.class).hasMessageContaining("celular").hasMessageContaining("correo");
        RonaldmichaTrabajador actualizado = servicio.actualizarContacto(carlos, "987654321", "carlos@ejemplo.pe");
        assertThat(actualizado.celular()).isEqualTo("987654321");
        assertThat(actualizado.correo()).isEqualTo("carlos@ejemplo.pe");
        assertThat(auditoria.recientes(1).getFirst().accion()).isEqualTo("DATOS_CONTACTO_ACTUALIZADOS");

        RonaldmichaAprobacion solicitud = servicio.solicitarCambioCuenta(carlos, RonaldmichaTrabajadorService.TipoCuenta.CTS, "Banco Nuevo",
                "200-0000099");
        assertThat(porDni("40000002").ctaCts()).isEqualTo(carlosFicha.ctaCts());
        assertThat(servicio.misDatos(carlos).pendiente()).isPresent();
        assertThatThrownBy(() -> servicio.solicitarCambioCuenta(carlos, RonaldmichaTrabajadorService.TipoCuenta.SUELDO, "Otro",
                "300-0000001")).hasMessageContaining("pendiente");
        assertThatThrownBy(() -> aprobaciones.aprobar(solicitud.id(), carlos)).isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        aprobaciones.aprobar(solicitud.id(), admin);
        assertThat(porDni("40000002").bancoCts()).isEqualTo("Banco Nuevo");
        assertThat(porDni("40000002").ctaCts()).isEqualTo("200-0000099");
    }

    @Test
    void elAdministradorApruebaCambiosDeOtrosPeroNoLosPropios() {
        RonaldmichaAprobacion deOtro = solicitarSueldo("40000002", "3100.00", admin2);
        aprobaciones.aprobar(deOtro.id(), admin);
        assertThat(porDni("40000002").sueldo()).isEqualByComparingTo("3100.00");

        RonaldmichaAprobacion propio = solicitarSueldo("40000001", "8000.00", admin2);
        assertThatThrownBy(() -> aprobaciones.aprobar(propio.id(), admin))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class).hasMessageContaining("propio");
        RonaldmichaAprobacionPendiente pendiente = aprobaciones.pendientes(admin).stream()
                .filter(p -> p.id() == propio.id()).findFirst().orElseThrow();
        assertThat(aprobaciones.puedeResolver(pendiente, admin)).isFalse();
        assertThat(aprobaciones.puedeResolver(pendiente, aprobador)).isTrue();
    }

    private RonaldmichaAprobacion solicitarSueldo(String dni, String sueldo, RonaldmichaUsuario usuario) {
        return servicio.guardar(conSueldo(porDni(dni), sueldo), COMPLETO, usuario, OCTUBRE_2026).pendiente().orElseThrow();
    }

    private RonaldmichaTrabajador porDni(String dni) {
        return servicio.todos().stream().filter(t -> t.numDoc().equals(dni)).findFirst().orElseThrow();
    }

    private static RonaldmichaHorario horario(String ingreso, String salida, int refrigerio) {
        return new RonaldmichaHorario(LocalTime.parse(ingreso), LocalTime.parse(salida), refrigerio, 0,
                RonaldmichaHorario.leerDias("1,2,3,4,5"), false);
    }

    private static RonaldmichaTrabajador nuevo(String dni) {
        return new RonaldmichaTrabajador(null, null, RonaldmichaTipoDocumento.DNI, dni, "Prueba", "Ficticia", "Ana", null, null,
                LocalDate.of(2026, 10, 1), null, "Ventas", "Vendedora", "Plazo fijo", RonaldmichaRegimenLaboral.GENERAL,
                null, null, new BigDecimal("1500.00"), BigDecimal.ZERO, RonaldmichaSalud.ESSALUD, RonaldmichaSistemaPension.ONP, null, null,
                false, false, RonaldmichaTipoPago.DEPOSITO, "Banco Ejemplo", "000-0000010", null, null);
    }

    private static RonaldmichaTrabajador conSueldo(RonaldmichaTrabajador t, String sueldo) {
        return new RonaldmichaTrabajador(t.id(), t.codigo(), t.tipoDoc(), t.numDoc(), t.apPaterno(), t.apMaterno(), t.nombres(),
                t.celular(), t.correo(), t.fechaIngreso(), t.fechaCese(), t.area(), t.cargo(), t.tipoContrato(),
                t.regimen(), t.centroCosto(), t.sucursal(), new BigDecimal(sueldo), t.movilidad(), t.salud(),
                t.sistemaPension(), t.cuspp(), t.tipoComision(), t.asigFamiliar(), t.vidaLey(), t.tipoPago(),
                t.bancoSueldo(), t.ctaSueldo(), t.bancoCts(), t.ctaCts());
    }

    private static RonaldmichaTrabajador conCargo(RonaldmichaTrabajador t, String cargo) {
        return new RonaldmichaTrabajador(t.id(), t.codigo(), t.tipoDoc(), t.numDoc(), t.apPaterno(), t.apMaterno(), t.nombres(),
                t.celular(), t.correo(), t.fechaIngreso(), t.fechaCese(), t.area(), cargo, t.tipoContrato(),
                t.regimen(), t.centroCosto(), t.sucursal(), t.sueldo(), t.movilidad(), t.salud(),
                t.sistemaPension(), t.cuspp(), t.tipoComision(), t.asigFamiliar(), t.vidaLey(), t.tipoPago(),
                t.bancoSueldo(), t.ctaSueldo(), t.bancoCts(), t.ctaCts());
    }

    private static RonaldmichaTrabajador conSalud(RonaldmichaTrabajador t, RonaldmichaSalud salud, RonaldmichaRegimenLaboral regimen) {
        return new RonaldmichaTrabajador(t.id(), t.codigo(), t.tipoDoc(), t.numDoc(), t.apPaterno(), t.apMaterno(), t.nombres(),
                t.celular(), t.correo(), t.fechaIngreso(), t.fechaCese(), t.area(), t.cargo(), t.tipoContrato(),
                regimen, t.centroCosto(), t.sucursal(), t.sueldo(), t.movilidad(), salud,
                t.sistemaPension(), t.cuspp(), t.tipoComision(), t.asigFamiliar(), t.vidaLey(), t.tipoPago(),
                t.bancoSueldo(), t.ctaSueldo(), t.bancoCts(), t.ctaCts());
    }

    private static RonaldmichaTrabajador conPension(RonaldmichaTrabajador t, RonaldmichaSistemaPension sistema, String cuspp, RonaldmichaTipoComision comision) {
        return new RonaldmichaTrabajador(t.id(), t.codigo(), t.tipoDoc(), t.numDoc(), t.apPaterno(), t.apMaterno(), t.nombres(),
                t.celular(), t.correo(), t.fechaIngreso(), t.fechaCese(), t.area(), t.cargo(), t.tipoContrato(),
                t.regimen(), t.centroCosto(), t.sucursal(), t.sueldo(), t.movilidad(), t.salud(),
                sistema, cuspp, comision, t.asigFamiliar(), t.vidaLey(), t.tipoPago(),
                t.bancoSueldo(), t.ctaSueldo(), t.bancoCts(), t.ctaCts());
    }

    private static RonaldmichaTrabajador conCuentas(RonaldmichaTrabajador t, String banco, String cuenta, String bancoCts, String ctaCts) {
        return new RonaldmichaTrabajador(t.id(), t.codigo(), t.tipoDoc(), t.numDoc(), t.apPaterno(), t.apMaterno(), t.nombres(),
                t.celular(), t.correo(), t.fechaIngreso(), t.fechaCese(), t.area(), t.cargo(), t.tipoContrato(),
                t.regimen(), t.centroCosto(), t.sucursal(), t.sueldo(), t.movilidad(), t.salud(),
                t.sistemaPension(), t.cuspp(), t.tipoComision(), t.asigFamiliar(), t.vidaLey(), t.tipoPago(),
                banco, cuenta, bancoCts, ctaCts);
    }
}
