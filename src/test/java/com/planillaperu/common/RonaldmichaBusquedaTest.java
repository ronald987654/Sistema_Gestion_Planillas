package com.planillaperu.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaBusquedaTest {

    private static final String ETIQUETA = "Díaz Salazar, Patricia · DNI 40000007";

    @Test
    void ignoraTildesMayusculasYOrden() {
        assertThat(RonaldmichaBusqueda.coincide(ETIQUETA, "diaz")).isTrue();
        assertThat(RonaldmichaBusqueda.coincide(ETIQUETA, "PATRICIA díaz")).isTrue();
        assertThat(RonaldmichaBusqueda.coincide(ETIQUETA, "40000007")).isTrue();
        assertThat(RonaldmichaBusqueda.coincide(ETIQUETA, "")).isTrue();
    }

    @Test
    void todasLasPalabrasDebenAparecer() {
        assertThat(RonaldmichaBusqueda.coincide(ETIQUETA, "patricia quispe")).isFalse();
    }
}
