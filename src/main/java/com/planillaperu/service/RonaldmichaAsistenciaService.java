package com.planillaperu.service;

import com.planillaperu.calc.RonaldmichaAportePensionCalculator;
import com.planillaperu.calc.RonaldmichaAsistenciaCalculator;
import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.Dia;
import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.Resultado;
import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.Resumen;
import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.TipoDia;
import com.planillaperu.calc.RonaldmichaFeriadosPeru;
import com.planillaperu.calc.RonaldmichaHorasExtraCalculator;
import com.planillaperu.calc.RonaldmichaHorasExtraCalculator.Importes;
import com.planillaperu.calc.RonaldmichaHorasExtraCalculator.Tramos;
import com.planillaperu.calc.RonaldmichaJornadaCalculator;
import com.planillaperu.calc.RonaldmichaJornadaCalculator.Jornada;
import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.common.RonaldmichaHoras;
import com.planillaperu.domain.RonaldmichaFeriado;
import com.planillaperu.domain.RonaldmichaHorario;
import com.planillaperu.domain.RonaldmichaMarcacion;
import com.planillaperu.domain.RonaldmichaOrigenAsistencia;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaSituacionPeriodo;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaTratamientoHe;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.export.RonaldmichaAsistenciaExcel;
import com.planillaperu.export.RonaldmichaLectorTabla.Fila;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository.HoraExtraManual;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository.NovedadMes;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class RonaldmichaAsistenciaService {

    public static final String MOTIVO_NO_LABORABLE = "Día no laborable";
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final int MINUTOS_REFRIGERIO_MINIMO = 45;
    private static final int MINUTOS_JORNADA_CON_REFRIGERIO = 6 * 60;
    private static final BigDecimal HORAS_DIARIAS_MIN = BigDecimal.ONE;
    private static final BigDecimal HORAS_DIARIAS_MAX = BigDecimal.TEN;
    private static final BigDecimal HORAS_SEMANALES_MAX = BigDecimal.valueOf(48);

    public record MesAsistencia(
            RonaldmichaTrabajador trabajador,
            RonaldmichaHorario horario,
            Jornada jornada,
            YearMonth mes,
            RonaldmichaSituacionPeriodo situacion,
            List<Dia> dias,
            Resumen resumen,
            RonaldmichaOrigenAsistencia origen,
            List<RonaldmichaFeriado> feriados,
            List<String> alertas,
            Optional<Importes> importes) {

        public boolean soloLectura() {
            return situacion == RonaldmichaSituacionPeriodo.CERRADO;
        }
    }

    public record ResultadoImportacion(int dias, int trabajadores, List<String> observados) {

        public ResultadoImportacion {
            observados = List.copyOf(observados);
        }

        public String mensaje() {
            String base = dias + (dias == 1 ? " día importado" : " días importados") + " (" + trabajadores
                    + (trabajadores == 1 ? " trabajador)" : " trabajadores)");
            return observados.isEmpty() ? base + " · sin observaciones" : base + " · Observados: " + String.join("; ", observados);
        }
    }

    public record NovedadesPlanilla(
            boolean desdeAsistencia,
            int faltas,
            int tardanzaMin,
            int vacaciones,
            int licenciasConGoce,
            int descansosMedicos,
            int licenciasSinGoce,
            int feriadosTrabajados,
            int he25Min,
            int he35Min) {
    }

    private final RonaldmichaAsistenciaRepository repositorio;
    private final RonaldmichaTrabajadorRepository trabajadores;
    private final RonaldmichaPeriodoService periodos;
    private final RonaldmichaParametrosService parametros;
    private final RonaldmichaAuditoriaService auditoria;
    private final Clock reloj;

    public RonaldmichaAsistenciaService(RonaldmichaAsistenciaRepository repositorio, RonaldmichaTrabajadorRepository trabajadores, RonaldmichaPeriodoService periodos,
                             RonaldmichaParametrosService parametros, RonaldmichaAuditoriaService auditoria, Clock reloj) {
        this.repositorio = repositorio;
        this.trabajadores = trabajadores;
        this.periodos = periodos;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public MesAsistencia consultar(RonaldmichaUsuario usuario, long trabajadorId, YearMonth mes) {
        exigirVer(usuario, trabajadorId);
        RonaldmichaTrabajador t = trabajador(trabajadorId);
        return calcular(t, horarioDe(t), mes, repositorio.delMes(trabajadorId, mes),
                repositorio.feriadosExtra(mes.getYear()));
    }

    public Jornada jornada(RonaldmichaHorario h) {
        return RonaldmichaJornadaCalculator.calcular(h);
    }

    public List<RonaldmichaFeriado> feriados(YearMonth mes) {
        return feriados(mes, repositorio.feriadosExtra(mes.getYear()));
    }

    public List<RonaldmichaFeriado> feriadosDelAnio(int anio) {
        Map<LocalDate, String> extras = repositorio.feriadosExtra(anio);
        Set<LocalDate> nacionales = RonaldmichaFeriadosPeru.nacionales(anio).keySet();
        return RonaldmichaFeriadosPeru.combinar(anio, extras).entrySet().stream()
                .map(e -> new RonaldmichaFeriado(e.getKey(), e.getValue(), !nacionales.contains(e.getKey())))
                .toList();
    }

    public List<String> validarHorario(RonaldmichaHorario h) {
        List<String> errores = new ArrayList<>();
        if (h == null || h.horaIngreso() == null || h.horaSalida() == null) {
            errores.add("Complete el ingreso y la salida con el formato HH:MM.");
            return errores;
        }
        if (!h.horaSalida().isAfter(h.horaIngreso())) {
            errores.add("La salida debe ser posterior al ingreso.");
            return errores;
        }
        if (h.refrigerioMin() < 0 || h.toleranciaMin() < 0) {
            errores.add("El refrigerio y la tolerancia no pueden ser negativos.");
            return errores;
        }
        long tramo = Duration.between(h.horaIngreso(), h.horaSalida()).toMinutes();
        if (tramo > MINUTOS_JORNADA_CON_REFRIGERIO && h.refrigerioMin() < MINUTOS_REFRIGERIO_MINIMO) {
            errores.add("Si la jornada pasa de 6 horas, el refrigerio debe ser de al menos "
                    + MINUTOS_REFRIGERIO_MINIMO + " minutos.");
        }
        Jornada j = RonaldmichaJornadaCalculator.calcular(h);
        if (j.horasDiarias().compareTo(HORAS_DIARIAS_MIN) < 0 || j.horasDiarias().compareTo(HORAS_DIARIAS_MAX) > 0) {
            errores.add("La jornada diaria debe estar entre 1 y 10 horas (es de " + horas(j.horasDiarias()) + " h).");
        }
        if (h.diasLaborables().isEmpty()) {
            errores.add("Marque al menos un día laborable.");
        } else if (h.diasLaborables().size() == DayOfWeek.values().length) {
            errores.add("Debe quedar al menos un día de descanso a la semana.");
        } else if (!h.noFiscalizado() && j.horasSemanales().compareTo(HORAS_SEMANALES_MAX) > 0) {
            errores.add("La jornada semanal no puede pasar de 48 horas (es de " + horas(j.horasSemanales())
                    + " h), salvo personal de dirección o no fiscalizado.");
        }
        return errores;
    }

    public void guardarHorario(RonaldmichaUsuario usuario, long trabajadorId, RonaldmichaHorario h) {
        RonaldmichaPermisos.exigirAdmin(usuario, "modificar horarios");
        List<String> errores = validarHorario(h);
        if (!errores.isEmpty()) {
            throw new RonaldmichaValidacionException(errores);
        }
        RonaldmichaTrabajador t = trabajador(trabajadorId);
        trabajadores.actualizarHorario(trabajadorId, h);
        Jornada j = RonaldmichaJornadaCalculator.calcular(h);
        auditoria.registrar(usuario.usuario(), "HORARIO_ACTUALIZADO", describir(t) + " · " + textoHorario(h)
                + " · refrigerio " + h.refrigerioMin() + " min · tolerancia " + h.toleranciaMin() + " min · "
                + horas(j.horasDiarias()) + " h diarias / " + horas(j.horasSemanales()) + " h semanales"
                + (h.noFiscalizado() ? " · no fiscalizado" : ""));
    }

    public void guardarDia(RonaldmichaUsuario usuario, RonaldmichaMarcacion dia) {
        RonaldmichaPermisos.exigirAdmin(usuario, "registrar asistencia");
        YearMonth mes = YearMonth.from(dia.fecha());
        exigirEditable(mes);
        RonaldmichaTrabajador t = trabajador(dia.trabajadorId());
        RonaldmichaHorario h = horarioDe(t);
        validarHoras(dia, h).ifPresent(error -> {
            throw new RonaldmichaValidacionException(error);
        });
        RonaldmichaMarcacion anterior = repositorio.delMes(t.id(), mes).get(dia.fecha());
        RonaldmichaOrigenAsistencia origen = anterior == null ? RonaldmichaOrigenAsistencia.SISTEMA : anterior.origen().alEditar();
        repositorio.guardar(List.of(dia.conOrigen(origen)));
        auditoria.registrar(usuario.usuario(), "ASISTENCIA_EDITADA", describir(t) + " · "
                + RonaldmichaFormatos.fecha(dia.fecha()) + " · " + describir(dia));
    }

    public int llenarConHorario(RonaldmichaUsuario usuario, long trabajadorId, YearMonth mes) {
        RonaldmichaPermisos.exigirAdmin(usuario, "registrar asistencia");
        exigirEditable(mes);
        RonaldmichaTrabajador t = trabajador(trabajadorId);
        RonaldmichaHorario h = horarioDe(t);
        Map<LocalDate, RonaldmichaMarcacion> actuales = repositorio.delMes(trabajadorId, mes);
        Map<LocalDate, String> feriados = RonaldmichaFeriadosPeru.delMes(mes, repositorio.feriadosExtra(mes.getYear()));
        List<RonaldmichaMarcacion> nuevas = new ArrayList<>();
        for (LocalDate fecha : RonaldmichaAsistenciaCalculator.diasParaLlenar(mes, h, actuales, feriados, hoy(),
                t.fechaIngreso(), t.fechaCese())) {
            RonaldmichaMarcacion anterior = actuales.get(fecha);
            RonaldmichaMarcacion base = anterior == null ? RonaldmichaMarcacion.vacia(trabajadorId, fecha)
                    : anterior.conOrigen(anterior.origen().alEditar());
            nuevas.add(base.conHoras(h.horaIngreso(), h.horaSalida()));
        }
        if (!nuevas.isEmpty()) {
            repositorio.guardar(nuevas);
            auditoria.registrar(usuario.usuario(), "ASISTENCIA_LLENADA", describir(t) + " · " + MES.format(mes)
                    + " · " + nuevas.size() + " días con el horario " + textoHorario(h));
        }
        return nuevas.size();
    }

    public int borrarMes(RonaldmichaUsuario usuario, long trabajadorId, YearMonth mes) {
        RonaldmichaPermisos.exigirAdmin(usuario, "borrar asistencia");
        exigirEditable(mes);
        RonaldmichaTrabajador t = trabajador(trabajadorId);
        int borrados = repositorio.borrarMes(trabajadorId, mes);
        auditoria.registrar(usuario.usuario(), "ASISTENCIA_BORRADA", describir(t) + " · " + MES.format(mes)
                + " · " + borrados + " días borrados");
        return borrados;
    }

    public void agregarDiaNoLaborable(RonaldmichaUsuario usuario, LocalDate fecha, String motivo) {
        RonaldmichaPermisos.exigirAdmin(usuario, "agregar días no laborables");
        if (fecha == null) {
            throw new RonaldmichaValidacionException("Elija la fecha del día no laborable.");
        }
        exigirEditable(YearMonth.from(fecha));
        String nacional = RonaldmichaFeriadosPeru.nacionales(fecha.getYear()).get(fecha);
        if (nacional != null) {
            throw new RonaldmichaValidacionException("El " + RonaldmichaFormatos.fecha(fecha) + " ya es feriado nacional (" + nacional + ").");
        }
        String texto = motivo == null || motivo.isBlank() ? MOTIVO_NO_LABORABLE : motivo.trim();
        if (!repositorio.agregarFeriado(fecha, texto)) {
            throw new RonaldmichaValidacionException("El " + RonaldmichaFormatos.fecha(fecha) + " ya está agregado como día no laborable.");
        }
        auditoria.registrar(usuario.usuario(), "FERIADO_AGREGADO", RonaldmichaFormatos.fecha(fecha) + " · " + texto);
    }

    public void quitarDiaNoLaborable(RonaldmichaUsuario usuario, LocalDate fecha) {
        RonaldmichaPermisos.exigirAdmin(usuario, "quitar días no laborables");
        exigirEditable(YearMonth.from(fecha));
        if (!repositorio.quitarFeriado(fecha)) {
            throw new RonaldmichaValidacionException("El " + RonaldmichaFormatos.fecha(fecha) + " no es un día agregado.");
        }
        auditoria.registrar(usuario.usuario(), "FERIADO_QUITADO", RonaldmichaFormatos.fecha(fecha));
    }

    public ResultadoImportacion importar(List<Fila> filas, String archivo, RonaldmichaUsuario usuario, YearMonth mes) {
        RonaldmichaPermisos.exigirAdmin(usuario, "importar asistencia");
        if (periodos.situacion(mes) == RonaldmichaSituacionPeriodo.CERRADO) {
            throw new RonaldmichaValidacionException("El periodo " + MES.format(mes) + " está cerrado: solo se importan días "
                    + "del periodo abierto.");
        }
        List<RonaldmichaLecturaAsistencia.Registro> registros = RonaldmichaLecturaAsistencia.leer(filas);
        Map<String, RonaldmichaTrabajador> porDocumento = new HashMap<>();
        trabajadores.listarTodos().forEach(t -> porDocumento.put(t.numDoc(), t));
        Map<Long, RonaldmichaHorario> horarios = trabajadores.horarios();

        List<String> observados = new ArrayList<>();
        Map<String, RonaldmichaMarcacion> validas = new LinkedHashMap<>();
        for (RonaldmichaLecturaAsistencia.Registro r : registros) {
            if (r.sinDatos() && r.errores().isEmpty()) {
                continue;
            }
            List<String> errores = new ArrayList<>(r.errores());
            RonaldmichaTrabajador t = r.documento() == null ? null : porDocumento.get(r.documento().trim().toUpperCase());
            if (r.documento() != null && t == null) {
                errores.add("DNI " + r.documento() + " no registrado");
            }
            if (r.fecha() != null && !YearMonth.from(r.fecha()).equals(mes)) {
                errores.add("fecha " + RonaldmichaFormatos.fecha(r.fecha()) + " fuera del periodo " + MES.format(mes));
            }
            if (r.errores().isEmpty() && r.entrada() == null && r.salida() != null) {
                errores.add("falta la hora de entrada");
            } else if (r.errores().isEmpty() && r.entrada() != null && r.salida() == null) {
                errores.add("falta la hora de salida");
            }
            RonaldmichaMarcacion m = null;
            if (t != null && r.fecha() != null) {
                m = new RonaldmichaMarcacion(t.id(), r.fecha(), r.entrada(), r.salida(), r.novedad(), r.autorizada(),
                        r.tratamiento(), RonaldmichaOrigenAsistencia.EXCEL);
                RonaldmichaHorario h = horarios.get(t.id());
                if (h != null) {
                    validarHoras(m, h).ifPresent(e -> errores.add("salida antes de la entrada"));
                }
                if (validas.containsKey(t.id() + "|" + r.fecha()) && errores.isEmpty()) {
                    errores.add("día repetido en el archivo (" + r.documento() + " " + RonaldmichaFormatos.fecha(r.fecha()) + ")");
                }
            }
            if (!errores.isEmpty()) {
                observados.add("Fila " + r.fila() + ": " + String.join(", ", errores));
                continue;
            }
            validas.put(t.id() + "|" + r.fecha(), m);
        }
        repositorio.guardar(validas.values());
        int personas = (int) validas.values().stream().map(RonaldmichaMarcacion::trabajadorId).distinct().count();
        ResultadoImportacion resultado = new ResultadoImportacion(validas.size(), personas, observados);
        auditoria.registrar(usuario.usuario(), "ASISTENCIA_IMPORTADA",
                (archivo == null ? "" : archivo + " · ") + MES.format(mes) + " · " + resultado.mensaje());
        return resultado;
    }

    public byte[] plantillaExcel(RonaldmichaUsuario usuario, YearMonth mes) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "descargar la plantilla de asistencia");
        Map<LocalDate, String> feriados = RonaldmichaFeriadosPeru.delMes(mes, repositorio.feriadosExtra(mes.getYear()));
        Map<Long, Map<LocalDate, RonaldmichaMarcacion>> marcaciones = repositorio.delMes(mes);
        Map<Long, RonaldmichaHorario> horarios = trabajadores.horarios();
        List<RonaldmichaAsistenciaExcel.FilaPlantilla> filas = new ArrayList<>();
        List<RonaldmichaTrabajador> lista = vinculadosEn(mes);
        for (RonaldmichaTrabajador t : lista) {
            RonaldmichaHorario h = horarios.get(t.id());
            if (h == null) {
                continue;
            }
            Map<LocalDate, RonaldmichaMarcacion> delTrabajador = marcaciones.getOrDefault(t.id(), Map.of());
            for (int d = 1; d <= mes.lengthOfMonth(); d++) {
                LocalDate fecha = mes.atDay(d);
                if (!vinculado(t, fecha)) {
                    continue;
                }
                TipoDia tipo = RonaldmichaAsistenciaCalculator.tipo(fecha, h, feriados);
                String tipoTexto = tipo == TipoDia.FERIADO ? "Feriado · " + feriados.get(fecha) : tipo.etiqueta();
                filas.add(new RonaldmichaAsistenciaExcel.FilaPlantilla(t.numDoc(), t.nombreCompleto(), fecha, tipoTexto,
                        delTrabajador.get(fecha), textoHorario(h)));
            }
        }
        byte[] excel = RonaldmichaAsistenciaExcel.plantilla(mes, filas, feriadosDelAnio(mes.getYear()));
        auditoria.registrar(usuario.usuario(), "ASISTENCIA_PLANTILLA_DESCARGADA",
                MES.format(mes) + " · " + lista.size() + " trabajadores");
        return excel;
    }

    public byte[] exportarExcel(RonaldmichaUsuario usuario, YearMonth mes) {
        RonaldmichaPermisos.exigirPersonalDePlanillas(usuario, "exportar la asistencia");
        List<RonaldmichaTrabajador> lista = vinculadosEn(mes);
        byte[] excel = exportar(mes, lista);
        auditoria.registrar(usuario.usuario(), "ASISTENCIA_EXPORTADA", MES.format(mes) + " · " + lista.size()
                + " trabajadores · Excel");
        return excel;
    }

    public byte[] exportarMiAsistencia(RonaldmichaUsuario usuario, YearMonth mes) {
        if (usuario == null || usuario.trabajadorId() == null) {
            throw new RonaldmichaAccesoDenegadoException("Su usuario no está vinculado a un trabajador.");
        }
        RonaldmichaTrabajador t = trabajador(usuario.trabajadorId());
        byte[] excel = exportar(mes, List.of(t));
        auditoria.registrar(usuario.usuario(), "ASISTENCIA_PROPIA_DESCARGADA", describir(t) + " · " + MES.format(mes));
        return excel;
    }

    public NovedadesPlanilla desdeMarcaciones(RonaldmichaTrabajador t, RonaldmichaHorario h, YearMonth mes, Map<LocalDate, RonaldmichaMarcacion> marcaciones,
                                              Map<LocalDate, String> extrasDelAnio) {
        Resumen r = RonaldmichaAsistenciaCalculator.calcular(mes, h, marcaciones, RonaldmichaFeriadosPeru.delMes(mes, extrasDelAnio), hoy(),
                t.fechaIngreso(), t.fechaCese()).resumen();
        return new NovedadesPlanilla(true, r.faltas(), r.tardanzaMin(), r.vacaciones(), r.licenciasConGoce(),
                r.descansosMedicos(), r.licenciasSinGoce(), r.descansosConSobretasa(), r.he25Min(), r.he35Min());
    }

    public Map<LocalDate, String> feriadosExtra(int anio) {
        return repositorio.feriadosExtra(anio);
    }

    public NovedadesPlanilla novedadesParaPlanilla(long trabajadorId, YearMonth mes) {
        Map<LocalDate, RonaldmichaMarcacion> marcaciones = repositorio.delMes(trabajadorId, mes);
        if (!marcaciones.isEmpty()) {
            RonaldmichaTrabajador t = trabajador(trabajadorId);
            return desdeMarcaciones(t, horarioDe(t), mes, marcaciones, repositorio.feriadosExtra(mes.getYear()));
        }
        NovedadMes n = repositorio.novedadMes(trabajadorId, mes).orElse(NovedadMes.NINGUNA);
        int he25 = 0;
        int he35 = 0;
        Map<LocalDate, Integer> minutosPorDia = new HashMap<>();
        for (HoraExtraManual he : repositorio.horasExtraManuales(trabajadorId, mes)) {
            if (he.tratamiento() == RonaldmichaTratamientoHe.PAGAR) {
                int minutos = he.horas().multiply(BigDecimal.valueOf(60)).setScale(0, RonaldmichaDinero.REDONDEO).intValue();
                minutosPorDia.merge(he.fecha(), minutos, Integer::sum);
            }
        }
        for (int minutos : minutosPorDia.values()) {
            Tramos tramos = RonaldmichaHorasExtraCalculator.dividir(minutos);
            he25 += tramos.al25();
            he35 += tramos.al35();
        }
        return new NovedadesPlanilla(false, n.inasistencias(), n.tardanzaMin(), n.vacaciones(), n.licencia(), 0, 0,
                n.feriadosTrabajados(), he25, he35);
    }

    private MesAsistencia calcular(RonaldmichaTrabajador t, RonaldmichaHorario h, YearMonth mes, Map<LocalDate, RonaldmichaMarcacion> marcaciones,
                                   Map<LocalDate, String> extrasDelAnio) {
        Map<LocalDate, String> feriadosMes = RonaldmichaFeriadosPeru.delMes(mes, extrasDelAnio);
        Resultado r = RonaldmichaAsistenciaCalculator.calcular(mes, h, marcaciones, feriadosMes, hoy(), t.fechaIngreso(),
                t.fechaCese());
        Jornada jornada = RonaldmichaJornadaCalculator.calcular(h);
        List<String> alertas = new ArrayList<>();
        Optional<Importes> importes = Optional.empty();
        try {
            BigDecimal remuneracion = t.sueldo();
            if (t.asigFamiliar()) {
                remuneracion = remuneracion.add(RonaldmichaAportePensionCalculator.asignacionFamiliar(parametros.vigentes(mes)));
            }
            Resumen s = r.resumen();
            importes = Optional.of(RonaldmichaHorasExtraCalculator.calcular(remuneracion, jornada.horasDiarias(), s.he25Min(),
                    s.he35Min(), s.descansosConSobretasa()));
        } catch (RonaldmichaParametroNoEncontradoException e) {
            alertas.add("No se pudieron calcular los importes: " + e.getMessage());
        }
        alertas.addAll(alertas(h, r.resumen()));
        RonaldmichaOrigenAsistencia origen = RonaldmichaOrigenAsistencia.delMes(marcaciones.values().stream()
                .map(RonaldmichaMarcacion::origen).toList());
        return new MesAsistencia(t, h, jornada, mes, periodos.situacion(mes), r.dias(), r.resumen(), origen,
                feriados(mes, extrasDelAnio), alertas, importes);
    }

    private static List<String> alertas(RonaldmichaHorario h, Resumen r) {
        List<String> alertas = new ArrayList<>();
        if (r.diasHeSinAutorizar() > 0) {
            alertas.add("Horas extra sin autorización en " + r.diasHeSinAutorizar()
                    + (r.diasHeSinAutorizar() == 1 ? " día (" : " días (") + RonaldmichaHoras.duracion(r.heSinAutorizarMin())
                    + " h): se pagan igual, pero deben autorizarse.");
        }
        if (!r.salidasNocturnas().isEmpty()) {
            alertas.add("Salida después de las 10 p. m. el " + r.salidasNocturnas().stream().map(DIA_MES::format)
                    .collect(Collectors.joining(", ")) + ": revise la remuneración mínima nocturna.");
        }
        if (h.noFiscalizado()) {
            alertas.add("Personal de dirección o no fiscalizado: no se calculan sobretiempo ni tardanzas.");
        }
        if (r.incompletas() > 0) {
            alertas.add(r.incompletas() + (r.incompletas() == 1 ? " día tiene" : " días tienen")
                    + " la marcación incompleta (falta la entrada o la salida).");
        }
        return alertas;
    }

    private byte[] exportar(YearMonth mes, List<RonaldmichaTrabajador> lista) {
        Map<LocalDate, String> extras = repositorio.feriadosExtra(mes.getYear());
        Map<Long, Map<LocalDate, RonaldmichaMarcacion>> marcaciones = repositorio.delMes(mes);
        Map<Long, RonaldmichaHorario> horarios = trabajadores.horarios();
        List<RonaldmichaAsistenciaExcel.FilaResumen> resumen = new ArrayList<>();
        List<RonaldmichaAsistenciaExcel.FilaDetalle> detalle = new ArrayList<>();
        for (RonaldmichaTrabajador t : lista) {
            RonaldmichaHorario h = horarios.get(t.id());
            if (h == null) {
                continue;
            }
            MesAsistencia m = calcular(t, h, mes, marcaciones.getOrDefault(t.id(), Map.of()), extras);
            Resumen r = m.resumen();
            resumen.add(new RonaldmichaAsistenciaExcel.FilaResumen(t.numDoc(), t.nombreCompleto(), m.origen().etiqueta(),
                    r.diasLaborables(), r.asistencias(), r.faltas(), r.tardanzaMin(), r.minutosTrabajados(),
                    r.he25Min(), r.he35Min(), r.compensarMin(), r.descansosTrabajados(), r.heSinAutorizarMin(),
                    m.importes().map(Importes::he25).orElse(null), m.importes().map(Importes::he35).orElse(null),
                    m.importes().map(Importes::descansos).orElse(null)));
            for (Dia d : m.dias()) {
                if (d.estado() != RonaldmichaAsistenciaCalculator.EstadoDia.SIN_VINCULO) {
                    detalle.add(new RonaldmichaAsistenciaExcel.FilaDetalle(t.numDoc(), t.nombreCompleto(), d));
                }
            }
        }
        return RonaldmichaAsistenciaExcel.exportar(mes, resumen, detalle, feriados(mes, extras));
    }

    private List<RonaldmichaFeriado> feriados(YearMonth mes, Map<LocalDate, String> extrasDelAnio) {
        Set<LocalDate> nacionales = RonaldmichaFeriadosPeru.nacionales(mes.getYear()).keySet();
        return RonaldmichaFeriadosPeru.delMes(mes, extrasDelAnio).entrySet().stream()
                .map(e -> new RonaldmichaFeriado(e.getKey(), e.getValue(), !nacionales.contains(e.getKey())))
                .toList();
    }

    private static Optional<String> validarHoras(RonaldmichaMarcacion m, RonaldmichaHorario h) {
        boolean turnoNocturno = !h.horaSalida().isAfter(h.horaIngreso());
        if (m.completa() && !m.salida().isAfter(m.ingreso()) && !turnoNocturno) {
            return Optional.of("La salida (" + RonaldmichaHoras.texto(m.salida()) + ") debe ser posterior a la entrada ("
                    + RonaldmichaHoras.texto(m.ingreso()) + ").");
        }
        return Optional.empty();
    }

    private void exigirVer(RonaldmichaUsuario usuario, long trabajadorId) {
        if (usuario == null) {
            throw new RonaldmichaAccesoDenegadoException("Inicie sesión para ver la asistencia.");
        }
        if (usuario.rol() == RonaldmichaRol.TRABAJADOR && !Objects.equals(usuario.trabajadorId(), trabajadorId)) {
            throw new RonaldmichaAccesoDenegadoException("Solo puede ver su propia asistencia.");
        }
    }

    private void exigirEditable(YearMonth mes) {
        if (periodos.situacion(mes) == RonaldmichaSituacionPeriodo.CERRADO) {
            throw new RonaldmichaValidacionException("El periodo " + MES.format(mes) + " está cerrado: es de solo lectura.");
        }
    }

    private RonaldmichaTrabajador trabajador(long id) {
        return trabajadores.buscarPorId(id).orElseThrow(() -> new RonaldmichaValidacionException("El trabajador no existe."));
    }

    private RonaldmichaHorario horarioDe(RonaldmichaTrabajador t) {
        return trabajadores.horario(t.id()).orElseThrow(() -> new RonaldmichaValidacionException(
                t.nombreCompleto() + " no tiene horario registrado: complételo en su ficha."));
    }

    private List<RonaldmichaTrabajador> vinculadosEn(YearMonth mes) {
        return trabajadores.listarTodos().stream()
                .filter(t -> !t.fechaIngreso().isAfter(mes.atEndOfMonth()))
                .filter(t -> t.fechaCese() == null || !t.fechaCese().isBefore(mes.atDay(1)))
                .toList();
    }

    private static boolean vinculado(RonaldmichaTrabajador t, LocalDate fecha) {
        return !fecha.isBefore(t.fechaIngreso()) && (t.fechaCese() == null || !fecha.isAfter(t.fechaCese()));
    }

    public static String textoHorario(RonaldmichaHorario h) {
        String dias = new TreeSet<>(h.diasLaborables()).stream()
                .map(d -> {
                    String n = d.getDisplayName(TextStyle.SHORT_STANDALONE, RonaldmichaFormatos.LOCALE_PE).replace(".", "");
                    return n.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + n.substring(1);
                })
                .collect(Collectors.joining(", "));
        return RonaldmichaHoras.texto(h.horaIngreso()) + "–" + RonaldmichaHoras.texto(h.horaSalida()) + " · " + dias;
    }

    private static String describir(RonaldmichaTrabajador t) {
        return t.codigo() + " · " + t.tipoDoc().codigo() + " " + t.numDoc() + " · " + t.nombreCompleto();
    }

    private static String describir(RonaldmichaMarcacion m) {
        String horas = m.tieneMarcacion() ? RonaldmichaHoras.texto(m.ingreso()) + "–" + RonaldmichaHoras.texto(m.salida()) : "sin marcación";
        return horas + " · " + m.novedad().descripcion()
                + (m.heAutorizada() ? " · HE autorizada (" + m.heTratamiento().descripcion().toLowerCase() + ")" : "");
    }

    private static String horas(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }
}
