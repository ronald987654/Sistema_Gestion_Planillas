package com.planillaperu.common;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaJsonPlanoTest {

    @Test
    void idaYVueltaConCaracteresEspeciales() {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("sueldo", "3000.00");
        valores.put("banco", "Banco \"Ejemplo\" \\ Perú");
        valores.put("nota", "línea 1\nlínea 2\t·");
        valores.put("vacio", "");

        String json = RonaldmichaJsonPlano.escribir(valores);

        assertThat(json).startsWith("{\"sueldo\":\"3000.00\"");
        assertThat(RonaldmichaJsonPlano.leer(json)).containsExactlyEntriesOf(valores);
    }

    @Test
    void objetoVacioYNulo() {
        assertThat(RonaldmichaJsonPlano.leer("{}")).isEmpty();
        assertThat(RonaldmichaJsonPlano.leer(null)).isEmpty();
        assertThat(RonaldmichaJsonPlano.leer(" { \"a\" : \"1\" , \"b\":\"2\" } ")).containsEntry("a", "1").containsEntry("b", "2");
    }
}
