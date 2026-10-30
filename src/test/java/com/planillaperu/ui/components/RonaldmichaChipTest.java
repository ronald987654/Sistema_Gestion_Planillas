package com.planillaperu.ui.components;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaChipTest {

    @Test
    void cadaEstadoTieneSuColor() {
        assertThat(RonaldmichaChip.estilo("Abierto")).isEqualTo("success");
        assertThat(RonaldmichaChip.estilo("Hecho")).isEqualTo("success");
        assertThat(RonaldmichaChip.estilo("Recibido")).isEqualTo("success");
        assertThat(RonaldmichaChip.estilo("Pendiente de aprobación")).isEqualTo("warning");
        assertThat(RonaldmichaChip.estilo("Borrador")).isEqualTo("warning");
        assertThat(RonaldmichaChip.estilo("Cerrado")).isEqualTo("neutral");
        assertThat(RonaldmichaChip.estilo("Anulado")).isEqualTo("neutral");
        assertThat(RonaldmichaChip.estilo("Enviado")).isEqualTo("info");
        assertThat(RonaldmichaChip.estilo("Feriado")).isEqualTo("holiday");
        assertThat(RonaldmichaChip.estilo("ÁBIERTO")).as("sin tildes ni mayúsculas").isEqualTo("success");
        assertThat(RonaldmichaChip.estilo("desconocido")).isEqualTo("neutral");
        assertThat(RonaldmichaChip.estilo(null)).isEqualTo("neutral");
    }
}
