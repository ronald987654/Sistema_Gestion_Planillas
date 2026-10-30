package com.planillaperu.calc;

import com.planillaperu.calc.RonaldmichaHorasExtraCalculator.Tramos;
import com.planillaperu.domain.RonaldmichaHorario;
import com.planillaperu.domain.RonaldmichaMarcacion;
import com.planillaperu.domain.RonaldmichaNovedad;
import com.planillaperu.domain.RonaldmichaTratamientoHe;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RonaldmichaAsistenciaCalculator {

    public static final int TRAMO_CON_REFRIGERIO_MIN = 5 * 60;
    public static final LocalTime INICIO_HORARIO_NOCTURNO = LocalTime.of(22, 0);
    private static final int MINUTOS_DIA = 24 * 60;

    public enum TipoDia {
        LABORABLE("Laborable"),
        DESCANSO("Descanso"),
        FERIADO("Feriado");

        private final String etiqueta;

        TipoDia(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }

    public enum EstadoDia {
        ASISTIO("Asistió"),
        TARDANZA("Tardanza"),
        INCOMPLETA("Marcación incompleta"),
        FALTA("Falta"),
        POR_REGISTRAR("Por registrar"),
        DESCANSO("Descanso"),
        FERIADO_PAGADO("Feriado (pagado)"),
        TRABAJO_DESCANSO("Trabajó en descanso"),
        TRABAJO_FERIADO("Trabajó en feriado"),
        VACACIONES("Vacaciones"),
        LICENCIA_CON_GOCE("Licencia con goce"),
        DESCANSO_MEDICO("Descanso médico"),
        LICENCIA_SIN_GOCE("Licencia sin goce"),
        DESCANSO_SUSTITUTORIO("Descanso sustitutorio"),
        SIN_VINCULO("Sin vínculo");

        private final String etiqueta;

        EstadoDia(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }

    public record Dia(
            LocalDate fecha,
            TipoDia tipo,
            String feriado,
            RonaldmichaMarcacion marcacion,
            int minutosTrabajados,
            int tardanzaMin,
            int sobretiempoMin,
            int he25Min,
            int he35Min,
            int compensarMin,
            EstadoDia estado,
            boolean trabajoEnDescanso,
            boolean conSobretasa,
            boolean salidaNocturna) {

        public boolean laborable() {
            return tipo == TipoDia.LABORABLE;
        }

        public boolean heSinAutorizar() {
            return sobretiempoMin > 0 && !marcacion.heAutorizada();
        }
    }

    public record Resumen(
            int diasLaborables,
            int asistencias,
            int faltas,
            int tardanzaMin,
            int he25Min,
            int he35Min,
            int compensarMin,
            int descansosTrabajados,
            int descansosConSobretasa,
            int minutosTrabajados,
            int heSinAutorizarMin,
            int diasHeSinAutorizar,
            int vacaciones,
            int licenciasConGoce,
            int descansosMedicos,
            int licenciasSinGoce,
            int feriadosNoTrabajados,
            int porRegistrar,
            int incompletas,
            List<LocalDate> salidasNocturnas) {

        public Resumen {
            salidasNocturnas = List.copyOf(salidasNocturnas);
        }
    }

    public record Resultado(List<Dia> dias, Resumen resumen) {

        public Resultado {
            dias = List.copyOf(dias);
        }
    }

    private RonaldmichaAsistenciaCalculator() {
    }

    public static Resultado calcular(YearMonth mes, RonaldmichaHorario horario, Map<LocalDate, RonaldmichaMarcacion> marcaciones,
                                     Map<LocalDate, String> feriados, LocalDate hoy, LocalDate ingreso, LocalDate cese) {
        int jornadaMin = RonaldmichaJornadaCalculator.calcular(horario).horasDiarias().multiply(BigDecimal.valueOf(60))
                .intValue();
        long trabajadorId = marcaciones.values().stream().findFirst().map(RonaldmichaMarcacion::trabajadorId).orElse(0L);
        List<Dia> dias = new ArrayList<>();
        for (int d = 1; d <= mes.lengthOfMonth(); d++) {
            LocalDate fecha = mes.atDay(d);
            RonaldmichaMarcacion m = marcaciones.getOrDefault(fecha, RonaldmichaMarcacion.vacia(trabajadorId, fecha));
            boolean vinculado = (ingreso == null || !fecha.isBefore(ingreso)) && (cese == null || !fecha.isAfter(cese));
            dias.add(dia(fecha, tipo(fecha, horario, feriados), feriados.get(fecha), m, horario, jornadaMin, hoy, vinculado));
        }
        return new Resultado(dias, resumir(dias));
    }

    public static TipoDia tipo(LocalDate fecha, RonaldmichaHorario horario, Map<LocalDate, String> feriados) {
        if (feriados.containsKey(fecha)) {
            return TipoDia.FERIADO;
        }
        return horario.diasLaborables().contains(fecha.getDayOfWeek()) ? TipoDia.LABORABLE : TipoDia.DESCANSO;
    }

    public static List<LocalDate> diasParaLlenar(YearMonth mes, RonaldmichaHorario horario, Map<LocalDate, RonaldmichaMarcacion> marcaciones,
                                                 Map<LocalDate, String> feriados, LocalDate hoy, LocalDate ingreso,
                                                 LocalDate cese) {
        List<LocalDate> fechas = new ArrayList<>();
        for (int d = 1; d <= mes.lengthOfMonth(); d++) {
            LocalDate fecha = mes.atDay(d);
            if (fecha.isAfter(hoy) || ingreso != null && fecha.isBefore(ingreso) || cese != null && fecha.isAfter(cese)) {
                continue;
            }
            RonaldmichaMarcacion m = marcaciones.get(fecha);
            boolean libre = m == null || !m.tieneMarcacion() && m.novedad() == RonaldmichaNovedad.NORMAL;
            if (libre && tipo(fecha, horario, feriados) == TipoDia.LABORABLE) {
                fechas.add(fecha);
            }
        }
        return fechas;
    }

    public static int minutosTrabajados(LocalTime ingreso, LocalTime salida, int refrigerioMin) {
        if (ingreso == null || salida == null) {
            return 0;
        }
        int tramo = minutos(salida) - minutos(ingreso);
        if (tramo <= 0) {
            tramo += MINUTOS_DIA;
        }
        return Math.max(0, tramo > TRAMO_CON_REFRIGERIO_MIN ? tramo - refrigerioMin : tramo);
    }

    private static Dia dia(LocalDate fecha, TipoDia tipo, String feriado, RonaldmichaMarcacion m, RonaldmichaHorario h, int jornadaMin,
                           LocalDate hoy, boolean vinculado) {
        if (!vinculado) {
            return new Dia(fecha, tipo, feriado, m, 0, 0, 0, 0, 0, 0, EstadoDia.SIN_VINCULO, false, false, false);
        }
        int trabajados = m.completa() ? minutosTrabajados(m.ingreso(), m.salida(), h.refrigerioMin()) : 0;
        boolean nocturna = m.completa() && finRelativo(m.ingreso(), m.salida()) > minutos(INICIO_HORARIO_NOCTURNO);

        if (tipo != TipoDia.LABORABLE) {
            if (!m.tieneMarcacion()) {
                EstadoDia estado = tipo == TipoDia.FERIADO ? EstadoDia.FERIADO_PAGADO : EstadoDia.DESCANSO;
                return new Dia(fecha, tipo, feriado, m, 0, 0, 0, 0, 0, 0, estado, false, false, false);
            }
            if (!m.completa()) {
                return new Dia(fecha, tipo, feriado, m, 0, 0, 0, 0, 0, 0, EstadoDia.INCOMPLETA, false, false, false);
            }
            int sobretiempo = h.noFiscalizado() ? 0 : Math.max(0, trabajados - jornadaMin);
            boolean sobretasa = m.novedad() != RonaldmichaNovedad.DESCANSO_SUSTITUTORIO;
            EstadoDia estado = tipo == TipoDia.FERIADO ? EstadoDia.TRABAJO_FERIADO : EstadoDia.TRABAJO_DESCANSO;
            return conHorasExtra(fecha, tipo, feriado, m, trabajados, 0, sobretiempo, estado, true, sobretasa, nocturna);
        }

        if (!m.tieneMarcacion()) {
            EstadoDia estado = switch (m.novedad()) {
                case NORMAL -> fecha.isAfter(hoy) ? EstadoDia.POR_REGISTRAR : EstadoDia.FALTA;
                case VACACIONES -> EstadoDia.VACACIONES;
                case LICENCIA_CON_GOCE -> EstadoDia.LICENCIA_CON_GOCE;
                case DESCANSO_MEDICO -> EstadoDia.DESCANSO_MEDICO;
                case LICENCIA_SIN_GOCE -> EstadoDia.LICENCIA_SIN_GOCE;
                case DESCANSO_SUSTITUTORIO -> EstadoDia.DESCANSO_SUSTITUTORIO;
            };
            return new Dia(fecha, tipo, feriado, m, 0, 0, 0, 0, 0, 0, estado, false, false, false);
        }

        int tardanza = 0;
        if (!h.noFiscalizado() && m.ingreso() != null) {
            int tarde = minutos(m.ingreso()) - minutos(h.horaIngreso());
            tardanza = tarde > h.toleranciaMin() ? tarde - h.toleranciaMin() : 0;
        }
        if (!m.completa()) {
            return new Dia(fecha, tipo, feriado, m, 0, tardanza, 0, 0, 0, 0, EstadoDia.INCOMPLETA, false, false, false);
        }
        int sobretiempo = h.noFiscalizado() ? 0
                : Math.max(0, finRelativo(m.ingreso(), m.salida()) - finRelativo(h.horaIngreso(), h.horaSalida()));
        EstadoDia estado = tardanza > 0 ? EstadoDia.TARDANZA : EstadoDia.ASISTIO;
        return conHorasExtra(fecha, tipo, feriado, m, trabajados, tardanza, sobretiempo, estado, false, false, nocturna);
    }

    private static Dia conHorasExtra(LocalDate fecha, TipoDia tipo, String feriado, RonaldmichaMarcacion m, int trabajados,
                                     int tardanza, int sobretiempo, EstadoDia estado, boolean enDescanso,
                                     boolean sobretasa, boolean nocturna) {
        int compensar = 0;
        Tramos tramos = new Tramos(0, 0);
        if (m.heTratamiento() == RonaldmichaTratamientoHe.COMPENSAR) {
            compensar = sobretiempo;
        } else {
            tramos = RonaldmichaHorasExtraCalculator.dividir(sobretiempo);
        }
        return new Dia(fecha, tipo, feriado, m, trabajados, tardanza, sobretiempo, tramos.al25(), tramos.al35(),
                compensar, estado, enDescanso, sobretasa, nocturna);
    }

    private static Resumen resumir(List<Dia> dias) {
        int laborables = 0, asistencias = 0, faltas = 0, tardanza = 0, he25 = 0, he35 = 0, compensar = 0;
        int descansos = 0, conSobretasa = 0, trabajados = 0, sinAutorizarMin = 0, sinAutorizarDias = 0;
        int vacaciones = 0, licencias = 0, medicos = 0, sinGoce = 0, feriados = 0, porRegistrar = 0, incompletas = 0;
        List<LocalDate> nocturnas = new ArrayList<>();
        for (Dia d : dias) {
            if (d.estado() == EstadoDia.SIN_VINCULO) {
                continue;
            }
            if (d.laborable()) {
                laborables++;
            }
            switch (d.estado()) {
                case ASISTIO, TARDANZA -> asistencias++;
                case INCOMPLETA -> {
                    incompletas++;
                    if (d.laborable()) {
                        asistencias++;
                    }
                }
                case FALTA -> faltas++;
                case LICENCIA_SIN_GOCE -> {
                    faltas++;
                    sinGoce++;
                }
                case VACACIONES -> vacaciones++;
                case LICENCIA_CON_GOCE -> licencias++;
                case DESCANSO_MEDICO -> medicos++;
                case FERIADO_PAGADO -> feriados++;
                case POR_REGISTRAR -> porRegistrar++;
                default -> {
                }
            }
            if (d.trabajoEnDescanso()) {
                descansos++;
                if (d.conSobretasa()) {
                    conSobretasa++;
                }
            }
            tardanza += d.tardanzaMin();
            he25 += d.he25Min();
            he35 += d.he35Min();
            compensar += d.compensarMin();
            trabajados += d.minutosTrabajados();
            if (d.heSinAutorizar()) {
                sinAutorizarDias++;
                sinAutorizarMin += d.sobretiempoMin();
            }
            if (d.salidaNocturna()) {
                nocturnas.add(d.fecha());
            }
        }
        return new Resumen(laborables, asistencias, faltas, tardanza, he25, he35, compensar, descansos, conSobretasa,
                trabajados, sinAutorizarMin, sinAutorizarDias, vacaciones, licencias, medicos, sinGoce, feriados,
                porRegistrar, incompletas, nocturnas);
    }

    private static int minutos(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }

    private static int finRelativo(LocalTime ingreso, LocalTime salida) {
        int fin = minutos(salida);
        return fin <= minutos(ingreso) ? fin + MINUTOS_DIA : fin;
    }
}
