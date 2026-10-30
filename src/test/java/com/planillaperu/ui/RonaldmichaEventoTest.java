package com.planillaperu.ui;

import com.planillaperu.ui.RonaldmichaEvento.Tema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaEventoTest {

    @Test
    void temaDeCadaAccion() {
        assertThat(RonaldmichaEvento.tema("PLANILLA_CERRADA")).isEqualTo(Tema.PERIODO);
        assertThat(RonaldmichaEvento.tema("PLANILLA_REABIERTA")).isEqualTo(Tema.PERIODO);
        assertThat(RonaldmichaEvento.tema("PLANILLA_NOVEDADES_GUARDADAS")).isEqualTo(Tema.PLANILLA);
        assertThat(RonaldmichaEvento.tema("TRABAJADOR_ACTUALIZADO")).isEqualTo(Tema.TRABAJADORES);
        assertThat(RonaldmichaEvento.tema("ASISTENCIA_IMPORTADA")).isEqualTo(Tema.ASISTENCIA);
        assertThat(RonaldmichaEvento.tema("FERIADO_AGREGADO")).isEqualTo(Tema.ASISTENCIA);
        assertThat(RonaldmichaEvento.tema("DOCUMENTO_EMITIDO")).isEqualTo(Tema.DOCUMENTOS);
        assertThat(RonaldmichaEvento.tema("APROBACION_APROBADA")).isEqualTo(Tema.SEGURIDAD);
        assertThat(RonaldmichaEvento.tema("PARAMETROS_REGISTRADOS")).isEqualTo(Tema.PARAMETROS);
        assertThat(RonaldmichaEvento.tema("INICIO_SESION")).isEqualTo(Tema.OTRO);
        assertThat(new RonaldmichaEvento(Tema.PLANILLA, "X", 1).es(Tema.ASISTENCIA, Tema.PLANILLA)).isTrue();
    }

    @Test
    void textoLegibleDeLaAccion() {
        assertThat(RonaldmichaAcciones.texto("INICIO_SESION")).isEqualTo("Inicio de sesión");
        assertThat(RonaldmichaAcciones.texto("RENTA_QUINTA_RECALCULADA")).isEqualTo("Renta quinta recalculada");
        assertThat(RonaldmichaAcciones.texto(null)).isEmpty();
    }
}
