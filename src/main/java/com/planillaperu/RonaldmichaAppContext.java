package com.planillaperu;

import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaAprobacionRepository;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBeneficioRepository;
import com.planillaperu.repository.RonaldmichaConfiguracionRepository;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaDocumentoRepository;
import com.planillaperu.repository.RonaldmichaEmpresaRepository;
import com.planillaperu.repository.RonaldmichaLiquidacionRepository;
import com.planillaperu.repository.RonaldmichaParametrosRepository;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;
import com.planillaperu.repository.RonaldmichaPlanillaRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import com.planillaperu.repository.RonaldmichaUsuarioRepository;
import com.planillaperu.repository.RonaldmichaVacacionRepository;
import com.planillaperu.service.RonaldmichaelAprobacionService;
import com.planillaperu.service.RonaldmichaelAsistenciaService;
import com.planillaperu.service.RonaldmichaelAuditoriaService;
import com.planillaperu.service.RonaldmichaelBeneficiosService;
import com.planillaperu.service.RonaldmichaelConsultaReniec;
import com.planillaperu.service.RonaldmichaelDatosDemoService;
import com.planillaperu.service.RonaldmichaelEmpresaService;
import com.planillaperu.service.RonaldmichaelInicioService;
import com.planillaperu.service.RonaldmichaelLiquidacionService;
import com.planillaperu.service.RonaldmichaelParametrosService;
import com.planillaperu.service.RonaldmichaelMailService;
import com.planillaperu.service.RonaldmichaelPeriodoService;
import com.planillaperu.service.RonaldmichaelPlanillaService;
import com.planillaperu.service.RonaldmichaelReportesService;
import com.planillaperu.service.RonaldmichaelResumenAnualService;
import com.planillaperu.service.RonaldmichaelRespaldoService;
import com.planillaperu.service.RonaldmichaelSeguridadService;
import com.planillaperu.service.RonaldmichaelTrabajadorService;
import com.planillaperu.service.RonaldmichaelUsuarioService;
import com.planillaperu.service.RonaldmichaelVacacionesService;
import com.planillaperu.ui.RonaldmichaEvento;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.nio.file.Path;
import java.time.Clock;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicLong;

public final class RonaldmichaAppContext {

    private static final String USUARIO_SISTEMA = "sistema";

    private final RonaldmichaDatabase database;
    private final RonaldmichaelAuditoriaService auditoriaService;
    private final RonaldmichaelParametrosService parametroService;
    private final RonaldmichaelUsuarioService usuarioService;
    private final RonaldmichaelSeguridadService seguridadService;
    private final RonaldmichaelTrabajadorService trabajadorService;
    private final RonaldmichaelAprobacionService aprobacionService;
    private final RonaldmichaelAsistenciaService asistenciaService;
    private final RonaldmichaelPeriodoService periodoService;
    private final RonaldmichaelPlanillaService planillaService;
    private final RonaldmichaelBeneficiosService beneficiosService;
    private final RonaldmichaelVacacionesService vacacionesService;
    private final RonaldmichaelLiquidacionService liquidacionService;
    private final RonaldmichaelReportesService reportesService;
    private final RonaldmichaelInicioService inicioService;
    private final RonaldmichaelResumenAnualService resumenAnualService;
    private final RonaldmichaelRespaldoService respaldoService;
    private final RonaldmichaelMailService mailService = RonaldmichaelMailService.NO_CONFIGURADO;
    private final RonaldmichaelDatosDemoService datosDemoService;
    private final RonaldmichaelEmpresaService empresaService;
    private final RonaldmichaelConsultaReniec consultaReniec = RonaldmichaelConsultaReniec.NO_CONECTADA;

    private RonaldmichaUsuario sesion;
    private final ObjectProperty<YearMonth> periodo = new SimpleObjectProperty<>();
    private final ObjectProperty<Long> trabajadorSeleccionado = new SimpleObjectProperty<>();
    private final ObjectProperty<Long> trabajadorALiquidar = new SimpleObjectProperty<>();
    private final BooleanProperty nuevoTrabajadorSolicitado = new SimpleBooleanProperty();
    private final ObjectProperty<Seccion> seccionSolicitada = new SimpleObjectProperty<>();
    private final IntegerProperty aprobacionesPendientes = new SimpleIntegerProperty();
    private final ReadOnlyObjectWrapper<RonaldmichaEvento> evento = new ReadOnlyObjectWrapper<>();
    private final AtomicLong eventos = new AtomicLong();

    public enum Seccion { PLANILLA_DETALLADA, PLANILLA_CIERRE, BOLETA_PAGO }

    private RonaldmichaAppContext(RonaldmichaDatabase database, Clock reloj) {
        this.database = database;
        this.auditoriaService = new RonaldmichaelAuditoriaService(new RonaldmichaAuditoriaRepository(database), reloj);
        this.auditoriaService.suscribir(r -> publicar(r.accion()));
        RonaldmichaPeriodoRepository periodoRepository = new RonaldmichaPeriodoRepository(database);
        RonaldmichaTrabajadorRepository trabajadorRepository = new RonaldmichaTrabajadorRepository(database);
        this.parametroService = new RonaldmichaelParametrosService(new RonaldmichaParametrosRepository(database), periodoRepository,
                trabajadorRepository, auditoriaService, reloj);
        RonaldmichaConfiguracionRepository configuracionRepository = new RonaldmichaConfiguracionRepository(database);
        this.usuarioService = new RonaldmichaelUsuarioService(new RonaldmichaUsuarioRepository(database), trabajadorRepository,
                configuracionRepository, auditoriaService, reloj);
        this.seguridadService = new RonaldmichaelSeguridadService(configuracionRepository, usuarioService, auditoriaService);
        this.empresaService = new RonaldmichaelEmpresaService(new RonaldmichaEmpresaRepository(database), auditoriaService);
        RonaldmichaAprobacionRepository aprobacionRepository = new RonaldmichaAprobacionRepository(database);
        this.trabajadorService = new RonaldmichaelTrabajadorService(trabajadorRepository, aprobacionRepository, parametroService,
                empresaService, auditoriaService, reloj);
        this.aprobacionService = new RonaldmichaelAprobacionService(aprobacionRepository, auditoriaService, reloj);
        this.periodoService = new RonaldmichaelPeriodoService(periodoRepository, reloj);
        RonaldmichaAsistenciaRepository asistenciaRepository = new RonaldmichaAsistenciaRepository(database);
        this.asistenciaService = new RonaldmichaelAsistenciaService(asistenciaRepository, trabajadorRepository,
                periodoService, parametroService, auditoriaService, reloj);
        this.planillaService = new RonaldmichaelPlanillaService(trabajadorRepository, asistenciaRepository,
                new RonaldmichaPlanillaRepository(database), periodoRepository, periodoService, parametroService,
                asistenciaService, auditoriaService, reloj);
        this.beneficiosService = new RonaldmichaelBeneficiosService(trabajadorRepository, new RonaldmichaBeneficioRepository(database),
                planillaService, parametroService, empresaService, auditoriaService);
        RonaldmichaVacacionRepository vacacionRepository = new RonaldmichaVacacionRepository(database);
        this.vacacionesService = new RonaldmichaelVacacionesService(trabajadorRepository, vacacionRepository,
                periodoService, empresaService, auditoriaService, reloj);
        this.liquidacionService = new RonaldmichaelLiquidacionService(trabajadorRepository, new RonaldmichaLiquidacionRepository(database),
                vacacionRepository, beneficiosService, parametroService, periodoService, empresaService,
                auditoriaService, reloj);
        RonaldmichaDocumentoRepository documentoRepository = new RonaldmichaDocumentoRepository(database);
        this.reportesService = new RonaldmichaelReportesService(trabajadorRepository, planillaService, beneficiosService,
                liquidacionService, vacacionesService, parametroService, empresaService, documentoRepository,
                mailService, auditoriaService, reloj);
        this.inicioService = new RonaldmichaelInicioService(planillaService, parametroService, empresaService, vacacionesService,
                periodoService, asistenciaRepository, documentoRepository, aprobacionService, reloj);
        this.resumenAnualService = new RonaldmichaelResumenAnualService(planillaService, beneficiosService, auditoriaService, reloj);
        this.respaldoService = new RonaldmichaelRespaldoService(database, auditoriaService, reloj);
        this.datosDemoService = new RonaldmichaelDatosDemoService(database, auditoriaService);
        this.periodo.set(periodoService.periodoDeTrabajo());
    }

    public static RonaldmichaAppContext iniciar(Path archivoBd) {
        RonaldmichaDatabase db = new RonaldmichaDatabase(archivoBd);
        db.migrar();
        return new RonaldmichaAppContext(db, Clock.systemDefaultZone());
    }

    public RonaldmichaDatabase database() {
        return database;
    }

    public RonaldmichaelAuditoriaService auditoria() {
        return auditoriaService;
    }

    public RonaldmichaelParametrosService parametros() {
        return parametroService;
    }

    public RonaldmichaelUsuarioService usuarios() {
        return usuarioService;
    }

    public RonaldmichaelSeguridadService seguridad() {
        return seguridadService;
    }

    public RonaldmichaelTrabajadorService trabajadores() {
        return trabajadorService;
    }

    public RonaldmichaelAprobacionService aprobaciones() {
        return aprobacionService;
    }

    public RonaldmichaelAsistenciaService asistencia() {
        return asistenciaService;
    }

    public RonaldmichaelBeneficiosService beneficios() {
        return beneficiosService;
    }

    public RonaldmichaelInicioService inicio() {
        return inicioService;
    }

    public RonaldmichaelResumenAnualService resumenAnual() {
        return resumenAnualService;
    }

    public RonaldmichaelRespaldoService respaldo() {
        return respaldoService;
    }

    public RonaldmichaelReportesService reportes() {
        return reportesService;
    }

    public RonaldmichaelLiquidacionService liquidaciones() {
        return liquidacionService;
    }

    public RonaldmichaelVacacionesService vacaciones() {
        return vacacionesService;
    }

    public RonaldmichaelPlanillaService planilla() {
        return planillaService;
    }

    public RonaldmichaelPeriodoService periodos() {
        return periodoService;
    }

    public RonaldmichaelDatosDemoService datosDemo() {
        return datosDemoService;
    }

    public RonaldmichaelEmpresaService empresa() {
        return empresaService;
    }

    public RonaldmichaelConsultaReniec reniec() {
        return consultaReniec;
    }

    public RonaldmichaUsuario sesion() {
        return sesion;
    }

    public void iniciarSesion(RonaldmichaUsuario usuario) {
        this.sesion = usuario;
        this.periodo.set(periodoService.periodoDeTrabajo());
        this.trabajadorSeleccionado.set(null);
        refrescarAprobaciones();
    }

    public void cerrarSesionPorInactividad(int minutos) {
        if (sesion != null) {
            usuarioService.cerrarSesionPorInactividad(sesion, minutos);
        }
        sesion = null;
        trabajadorSeleccionado.set(null);
    }

    public void cerrarSesion() {
        if (sesion != null) {
            usuarioService.cerrarSesion(sesion);
        }
        sesion = null;
        trabajadorSeleccionado.set(null);
    }

    public String usuarioActual() {
        return sesion == null ? USUARIO_SISTEMA : sesion.usuario();
    }

    public ObjectProperty<YearMonth> periodoProperty() {
        return periodo;
    }

    public ReadOnlyObjectProperty<RonaldmichaEvento> eventoProperty() {
        return evento.getReadOnlyProperty();
    }

    private void publicar(String accion) {
        RonaldmichaEvento e = new RonaldmichaEvento(RonaldmichaEvento.tema(accion), accion, eventos.incrementAndGet());
        try {
            if (Platform.isFxApplicationThread()) {
                evento.set(e);
            } else {
                Platform.runLater(() -> evento.set(e));
            }
        } catch (IllegalStateException sinJavaFx) {
        }
    }

    public IntegerProperty aprobacionesPendientesProperty() {
        return aprobacionesPendientes;
    }

    public void refrescarAprobaciones() {
        boolean personal = sesion != null && sesion.rol() != RonaldmichaRol.TRABAJADOR;
        aprobacionesPendientes.set(personal ? aprobacionService.pendientes(sesion).size() : 0);
    }

    public BooleanProperty nuevoTrabajadorSolicitadoProperty() {
        return nuevoTrabajadorSolicitado;
    }

    public ObjectProperty<Seccion> seccionSolicitadaProperty() {
        return seccionSolicitada;
    }

    public ObjectProperty<Long> trabajadorALiquidarProperty() {
        return trabajadorALiquidar;
    }

    public ObjectProperty<Long> trabajadorSeleccionadoProperty() {
        return trabajadorSeleccionado;
    }
}
