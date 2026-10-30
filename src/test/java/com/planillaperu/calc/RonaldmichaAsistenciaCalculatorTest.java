package com.planillaperu.calc;

import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.Dia;
import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.EstadoDia;
import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.Resultado;
import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.Resumen;
import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.TipoDia;
import com.planillaperu.domain.RonaldmichaHorario;
import com.planillaperu.domain.RonaldmichaMarcacion;
import com.planillaperu.domain.RonaldmichaNovedad;
import com.planillaperu.domain.RonaldmichaOrigenAsistencia;
import com.planillaperu.domain.RonaldmichaTratamientoHe;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaAsistenciaCalculatorTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 15);
    private static final Map<LocalDate, String> FERIADOS = RonaldmichaFeriadosPeru.delMes(OCTUBRE, Map.of());
    private static final RonaldmichaHorario HORARIO = horario(false);

    private final Map<LocalDate, RonaldmichaMarcacion> marcaciones = new HashMap<>();

    private Resultado calcular(RonaldmichaHorario h) {
        return RonaldmichaAsistenciaCalculator.calcular(OCTUBRE, h, marcaciones, FERIADOS, HOY, null, null);
    }

    private Dia dia(Resultado r, int d) {
        return r.dias().get(d - 1);
    }

    private void cargarMesDePrueba() {
        marca(1, "08:12", "17:00");
        marca(2, "08:00", "20:30").autorizar(RonaldmichaTratamientoHe.PAGAR);
        marca(4, "08:00", "19:00");
        novedad(6, RonaldmichaNovedad.VACACIONES);
        novedad(7, RonaldmichaNovedad.LICENCIA_SIN_GOCE);
        marca(9, "08:00", "23:00").autorizar(RonaldmichaTratamientoHe.COMPENSAR);
        marca(11, "09:00", "13:00").con(RonaldmichaNovedad.DESCANSO_SUSTITUTORIO);
        novedad(12, RonaldmichaNovedad.DESCANSO_SUSTITUTORIO);
        novedad(13, RonaldmichaNovedad.DESCANSO_MEDICO);
        marca(14, "08:03", "17:00");
    }

    @Test
    void estadosDeCadaDia() {
        cargarMesDePrueba();
        Resultado r = calcular(HORARIO);

        assertThat(r.dias()).hasSize(31);
        assertThat(dia(r, 1).estado()).isEqualTo(EstadoDia.TARDANZA);
        assertThat(dia(r, 1).tardanzaMin()).isEqualTo(7);
        assertThat(dia(r, 1).minutosTrabajados()).isEqualTo(468);
        assertThat(dia(r, 3).tipo()).isEqualTo(TipoDia.DESCANSO);
        assertThat(dia(r, 3).estado()).isEqualTo(EstadoDia.DESCANSO);
        assertThat(dia(r, 5).estado()).isEqualTo(EstadoDia.FALTA);
        assertThat(dia(r, 6).estado()).isEqualTo(EstadoDia.VACACIONES);
        assertThat(dia(r, 7).estado()).isEqualTo(EstadoDia.LICENCIA_SIN_GOCE);
        assertThat(dia(r, 8).tipo()).isEqualTo(TipoDia.FERIADO);
        assertThat(dia(r, 8).feriado()).isEqualTo("Combate de Angamos");
        assertThat(dia(r, 8).estado()).isEqualTo(EstadoDia.FERIADO_PAGADO);
        assertThat(dia(r, 12).estado()).isEqualTo(EstadoDia.DESCANSO_SUSTITUTORIO);
        assertThat(dia(r, 13).estado()).isEqualTo(EstadoDia.DESCANSO_MEDICO);
        assertThat(dia(r, 14).estado()).isEqualTo(EstadoDia.ASISTIO);
        assertThat(dia(r, 14).tardanzaMin()).isZero();
        assertThat(dia(r, 15).estado()).as("hoy sin marcación").isEqualTo(EstadoDia.FALTA);
        assertThat(dia(r, 16).estado()).as("futuro").isEqualTo(EstadoDia.POR_REGISTRAR);
    }

    @Test
    void horasExtraPorDia() {
        cargarMesDePrueba();
        Resultado r = calcular(HORARIO);

        Dia viernes = dia(r, 2);
        assertThat(viernes.minutosTrabajados()).isEqualTo(690);
        assertThat(viernes.sobretiempoMin()).isEqualTo(210);
        assertThat(viernes.he25Min()).isEqualTo(120);
        assertThat(viernes.he35Min()).isEqualTo(90);
        assertThat(viernes.heSinAutorizar()).isFalse();

        Dia compensado = dia(r, 9);
        assertThat(compensado.sobretiempoMin()).isEqualTo(360);
        assertThat(compensado.compensarMin()).isEqualTo(360);
        assertThat(compensado.he25Min() + compensado.he35Min()).as("lo compensado no se paga").isZero();
        assertThat(compensado.salidaNocturna()).isTrue();
    }

    @Test
    void descansoTrabajadoConYSinSobretasa() {
        cargarMesDePrueba();
        Resultado r = calcular(HORARIO);

        Dia domingo = dia(r, 4);
        assertThat(domingo.estado()).isEqualTo(EstadoDia.TRABAJO_DESCANSO);
        assertThat(domingo.trabajoEnDescanso()).isTrue();
        assertThat(domingo.conSobretasa()).isTrue();
        assertThat(domingo.minutosTrabajados()).isEqualTo(600);
        assertThat(domingo.sobretiempoMin()).as("lo que pasa de la jornada de 8 h").isEqualTo(120);
        assertThat(domingo.heSinAutorizar()).isTrue();

        Dia sustitutorio = dia(r, 11);
        assertThat(sustitutorio.trabajoEnDescanso()).isTrue();
        assertThat(sustitutorio.conSobretasa()).isFalse();
        assertThat(sustitutorio.minutosTrabajados()).as("tramo de 4 h: sin refrigerio").isEqualTo(240);
        assertThat(sustitutorio.sobretiempoMin()).isZero();
    }

    @Test
    void resumenDelMes() {
        cargarMesDePrueba();
        Resumen s = calcular(HORARIO).resumen();

        assertThat(s.diasLaborables()).isEqualTo(21);
        assertThat(s.asistencias()).isEqualTo(4);
        assertThat(s.faltas()).isEqualTo(3);
        assertThat(s.licenciasSinGoce()).isEqualTo(1);
        assertThat(s.vacaciones()).isEqualTo(1);
        assertThat(s.descansosMedicos()).isEqualTo(1);
        assertThat(s.porRegistrar()).isEqualTo(11);
        assertThat(s.tardanzaMin()).isEqualTo(7);
        assertThat(s.he25Min()).isEqualTo(240);
        assertThat(s.he35Min()).isEqualTo(90);
        assertThat(s.compensarMin()).isEqualTo(360);
        assertThat(s.descansosTrabajados()).isEqualTo(2);
        assertThat(s.descansosConSobretasa()).isEqualTo(1);
        assertThat(s.feriadosNoTrabajados()).isEqualTo(1);
        assertThat(s.diasHeSinAutorizar()).isEqualTo(1);
        assertThat(s.heSinAutorizarMin()).isEqualTo(120);
        assertThat(s.minutosTrabajados()).isEqualTo(468 + 690 + 600 + 840 + 240 + 477);
        assertThat(s.salidasNocturnas()).containsExactly(LocalDate.of(2026, 10, 9));
    }

    @Test
    void feriadoTrabajadoPagaSobretasa() {
        marca(8, "08:00", "17:00");
        Dia feriado = dia(calcular(HORARIO), 8);

        assertThat(feriado.estado()).isEqualTo(EstadoDia.TRABAJO_FERIADO);
        assertThat(feriado.conSobretasa()).isTrue();
        assertThat(feriado.sobretiempoMin()).isZero();
    }

    @Test
    void personalNoFiscalizadoSinTardanzaNiSobretiempo() {
        cargarMesDePrueba();
        Resultado r = calcular(horario(true));

        assertThat(dia(r, 1).tardanzaMin()).isZero();
        assertThat(dia(r, 1).estado()).isEqualTo(EstadoDia.ASISTIO);
        assertThat(dia(r, 2).sobretiempoMin()).isZero();
        assertThat(r.resumen().he25Min() + r.resumen().he35Min() + r.resumen().compensarMin()).isZero();
        assertThat(r.resumen().descansosConSobretasa()).as("el feriado o descanso trabajado se sigue pagando").isEqualTo(1);
    }

    @Test
    void diasFueraDelVinculoNoCuentan() {
        Resultado r = RonaldmichaAsistenciaCalculator.calcular(OCTUBRE, HORARIO, marcaciones, FERIADOS, HOY,
                LocalDate.of(2026, 10, 12), null);

        assertThat(dia(r, 5).estado()).isEqualTo(EstadoDia.SIN_VINCULO);
        assertThat(r.resumen().faltas()).isEqualTo(4);
        assertThat(r.resumen().diasLaborables()).isEqualTo(15);
    }

    @Test
    void marcacionIncompletaNoEsFalta() {
        marcaciones.put(fecha(5), new RonaldmichaMarcacion(1, fecha(5), LocalTime.of(8, 20), null, RonaldmichaNovedad.NORMAL, false,
                RonaldmichaTratamientoHe.PAGAR, RonaldmichaOrigenAsistencia.SISTEMA));
        Resultado r = calcular(HORARIO);

        assertThat(dia(r, 5).estado()).isEqualTo(EstadoDia.INCOMPLETA);
        assertThat(dia(r, 5).tardanzaMin()).isEqualTo(15);
        assertThat(r.resumen().incompletas()).isEqualTo(1);
        assertThat(r.resumen().asistencias()).isEqualTo(1);
    }

    @Test
    void llenarSoloLaborablesHastaHoySinRegistro() {
        cargarMesDePrueba();

        assertThat(RonaldmichaAsistenciaCalculator.diasParaLlenar(OCTUBRE, HORARIO, marcaciones, FERIADOS, HOY, null, null))
                .containsExactly(fecha(5), fecha(15));
    }

    @Test
    void refrigerioSoloSiElTramoPasaDeCincoHoras() {
        assertThat(RonaldmichaAsistenciaCalculator.minutosTrabajados(LocalTime.of(8, 0), LocalTime.of(13, 0), 60)).isEqualTo(300);
        assertThat(RonaldmichaAsistenciaCalculator.minutosTrabajados(LocalTime.of(8, 0), LocalTime.of(13, 1), 60)).isEqualTo(241);
        assertThat(RonaldmichaAsistenciaCalculator.minutosTrabajados(LocalTime.of(22, 0), LocalTime.of(6, 0), 60)).isEqualTo(420);
    }

    private static RonaldmichaHorario horario(boolean noFiscalizado) {
        return new RonaldmichaHorario(LocalTime.of(8, 0), LocalTime.of(17, 0), 60, 5, RonaldmichaHorario.leerDias("1,2,3,4,5"), noFiscalizado);
    }

    private static LocalDate fecha(int d) {
        return OCTUBRE.atDay(d);
    }

    private Editor marca(int d, String ingreso, String salida) {
        marcaciones.put(fecha(d), new RonaldmichaMarcacion(1, fecha(d), LocalTime.parse(ingreso), LocalTime.parse(salida),
                RonaldmichaNovedad.NORMAL, false, RonaldmichaTratamientoHe.PAGAR, RonaldmichaOrigenAsistencia.SISTEMA));
        return new Editor(fecha(d));
    }

    private void novedad(int d, RonaldmichaNovedad n) {
        marcaciones.put(fecha(d), RonaldmichaMarcacion.vacia(1, fecha(d)).conNovedad(n));
    }

    private final class Editor {
        private final LocalDate fecha;

        Editor(LocalDate fecha) {
            this.fecha = fecha;
        }

        void autorizar(RonaldmichaTratamientoHe tratamiento) {
            marcaciones.put(fecha, marcaciones.get(fecha).conHorasExtra(true, tratamiento));
        }

        void con(RonaldmichaNovedad n) {
            marcaciones.put(fecha, marcaciones.get(fecha).conNovedad(n));
        }
    }
}
