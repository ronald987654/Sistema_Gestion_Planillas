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
import com.planillaperu.service.RonaldmichaAprobacionService;
import com.planillaperu.service.RonaldmichaAsistenciaService;
import com.planillaperu.service.RonaldmichaAuditoriaService;
import com.planillaperu.service.RonaldmichaBeneficiosService;
import com.planillaperu.service.RonaldmichaConsultaReniec;
import com.planillaperu.service.RonaldmichaDatosDemoService;
import com.planillaperu.service.RonaldmichaEmpresaService;
import com.planillaperu.service.RonaldmichaInicioService;
import com.planillaperu.service.RonaldmichaLiquidacionService;
import com.planillaperu.service.RonaldmichaParametrosService;
import com.planillaperu.service.RonaldmichaMailService;
import com.planillaperu.service.RonaldmichaPeriodoService;
import com.planillaperu.service.RonaldmichaPlanillaService;
import com.planillaperu.service.RonaldmichaReportesService;
import com.planillaperu.service.RonaldmichaResumenAnualService;
import com.planillaperu.service.RonaldmichaRespaldoService;
import com.planillaperu.service.RonaldmichaSeguridadService;
import com.planillaperu.service.RonaldmichaTrabajadorService;
import com.planillaperu.service.RonaldmichaUsuarioService;
import com.planillaperu.service.RonaldmichaVacacionesService;
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
    private final RonaldmichaAuditoriaService auditoriaService;
    private final RonaldmichaParametrosService parametroService;
    private final RonaldmichaUsuarioService usuarioService;
    private final RonaldmichaSeguridadService seguridadService;
    private final RonaldmichaTrabajadorService trabajadorService;
    private final RonaldmichaAprobacionService aprobacionService;
    private final RonaldmichaAsistenciaService asistenciaService;
    private final RonaldmichaPeriodoService periodoService;
    private final RonaldmichaPlanillaService planillaService;
    private final RonaldmichaBeneficiosService beneficiosService;
    private final RonaldmichaVacacionesService vacacionesService;
    private final RonaldmichaLiquidacionService liquidacionService;
    private final RonaldmichaReportesService reportesService;
    private final RonaldmichaInicioService inicioService;
    private final RonaldmichaResumenAnualService resumenAnualService;
    private final RonaldmichaRespaldoService respaldoService;
    private final RonaldmichaMailService mailService = RonaldmichaMailService.NO_CONFIGURADO;
    private final RonaldmichaDatosDemoService datosDemoService;
    private final RonaldmichaEmpresaService empresaService;
    private final RonaldmichaConsultaReniec consultaReniec = RonaldmichaConsultaReniec.NO_CONECTADA;

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
        this.auditoriaService = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(database), reloj);
        this.auditoriaService.suscribir(r -> publicar(r.accion()));
        RonaldmichaPeriodoRepository periodoRepository = new RonaldmichaPeriodoRepository(database);
        RonaldmichaTrabajadorRepository trabajadorRepository = new RonaldmichaTrabajadorRepository(database);
        this.parametroService = new RonaldmichaParametrosService(new RonaldmichaParametrosRepository(database), periodoRepository,
                trabajadorRepository, auditoriaService, reloj);
        RonaldmichaConfiguracionRepository configuracionRepository = new RonaldmichaConfiguracionRepository(database);
        this.usuarioService = new RonaldmichaUsuarioService(new RonaldmichaUsuarioRepository(database), trabajadorRepository,
                configuracionRepository, auditoriaService, reloj);
        this.seguridadService = new RonaldmichaSeguridadService(configuracionRepository, usuarioService, auditoriaService);
        this.empresaService = new RonaldmichaEmpresaService(new RonaldmichaEmpresaRepository(database), auditoriaService);
        RonaldmichaAprobacionRepository aprobacionRepository = new RonaldmichaAprobacionRepository(database);
        this.trabajadorService = new RonaldmichaTrabajadorService(trabajadorRepository, aprobacionRepository, parametroService,
                empresaService, auditoriaService, reloj);
        this.aprobacionService = new RonaldmichaAprobacionService(aprobacionRepository, auditoriaService, reloj);
        this.periodoService = new RonaldmichaPeriodoService(periodoRepository, reloj);
        RonaldmichaAsistenciaRepository asistenciaRepository = new RonaldmichaAsistenciaRepository(database);
        this.asistenciaService = new RonaldmichaAsistenciaService(asistenciaRepository, trabajadorRepository,
                periodoService, parametroService, auditoriaService, reloj);
        this.planillaService = new RonaldmichaPlanillaService(trabajadorRepository, asistenciaRepository,
                new RonaldmichaPlanillaRepository(database), periodoRepository, periodoService, parametroService,
                asistenciaService, auditoriaService, reloj);
        this.beneficiosService = new RonaldmichaBeneficiosService(trabajadorRepository, new RonaldmichaBeneficioRepository(database),
                planillaService, parametroService, empresaService, auditoriaService);
        RonaldmichaVacacionRepository vacacionRepository = new RonaldmichaVacacionRepository(database);
        this.vacacionesService = new RonaldmichaVacacionesService(trabajadorRepository, vacacionRepository,
                periodoService, empresaService, auditoriaService, reloj);
        this.liquidacionService = new RonaldmichaLiquidacionService(trabajadorRepository, new RonaldmichaLiquidacionRepository(database),
                vacacionRepository, beneficiosService, parametroService, periodoService, empresaService,
                auditoriaService, reloj);
        RonaldmichaDocumentoRepository documentoRepository = new RonaldmichaDocumentoRepository(database);
        this.reportesService = new RonaldmichaReportesService(trabajadorRepository, planillaService, beneficiosService,
                liquidacionService, vacacionesService, parametroService, empresaService, documentoRepository,
                mailService, auditoriaService, reloj);
        this.inicioService = new RonaldmichaInicioService(planillaService, parametroService, empresaService, vacacionesService,
                periodoService, asistenciaRepository, documentoRepository, aprobacionService, reloj);
        this.resumenAnualService = new RonaldmichaResumenAnualService(planillaService, beneficiosService, auditoriaService, reloj);
        this.respaldoService = new RonaldmichaRespaldoService(database, auditoriaService, reloj);
        this.datosDemoService = new RonaldmichaDatosDemoService(database, auditoriaService);
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

    public RonaldmichaAuditoriaService auditoria() {
        return auditoriaService;
    }

    public RonaldmichaParametrosService parametros() {
        return parametroService;
    }

    public RonaldmichaUsuarioService usuarios() {
        return usuarioService;
    }

    public RonaldmichaSeguridadService seguridad() {
        return seguridadService;
    }

    public RonaldmichaTrabajadorService trabajadores() {
        return trabajadorService;
    }

    public RonaldmichaAprobacionService aprobaciones() {
        return aprobacionService;
    }

    public RonaldmichaAsistenciaService asistencia() {
        return asistenciaService;
    }

    public RonaldmichaBeneficiosService beneficios() {
        return beneficiosService;
    }

    public RonaldmichaInicioService inicio() {
        return inicioService;
    }

    public RonaldmichaResumenAnualService resumenAnual() {
        return resumenAnualService;
    }

    public RonaldmichaRespaldoService respaldo() {
        return respaldoService;
    }

    public RonaldmichaReportesService reportes() {
        return reportesService;
    }

    public RonaldmichaLiquidacionService liquidaciones() {
        return liquidacionService;
    }

    public RonaldmichaVacacionesService vacaciones() {
        return vacacionesService;
    }

    public RonaldmichaPlanillaService planilla() {
        return planillaService;
    }

    public RonaldmichaPeriodoService periodos() {
        return periodoService;
    }

    public RonaldmichaDatosDemoService datosDemo() {
        return datosDemoService;
    }

    public RonaldmichaEmpresaService empresa() {
        return empresaService;
    }

    public RonaldmichaConsultaReniec reniec() {
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
