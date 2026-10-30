package com.planillaperu.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaRucTest {

    @ParameterizedTest
    @ValueSource(strings = {"20100070970", "20600000005", "10400000013", "15123456782", "17000000019"})
    void aceptaRucConDigitoVerificadorCorrecto(String ruc) {
        assertThat(RonaldmichaRuc.esValido(ruc)).isTrue();
    }

    @Test
    void rechazaDigitoVerificadorIncorrecto() {
        assertThat(RonaldmichaRuc.error("20600000006")).hasValue("El dígito verificador del RUC no es correcto.");
    }

    @Test
    void rechazaLongitudYCaracteres() {
        assertThat(RonaldmichaRuc.error("2060000000")).hasValue("El RUC debe tener 11 dígitos.");
        assertThat(RonaldmichaRuc.error("2060000000A")).hasValue("El RUC debe tener 11 dígitos.");
        assertThat(RonaldmichaRuc.error("")).hasValue("Ingrese el RUC.");
        assertThat(RonaldmichaRuc.error(null)).hasValue("Ingrese el RUC.");
    }

    @Test
    void rechazaPrefijoNoPermitido() {
        assertThat(RonaldmichaRuc.error("30600000005")).hasValue("El RUC debe empezar con 10, 15, 17 o 20.");
    }

    @Test
    void digitoVerificadorConRestosEspeciales() {
        assertThat(RonaldmichaRuc.digitoVerificador("2010007097")).isZero();
        assertThat(RonaldmichaRuc.digitoVerificador("1000000003")).isEqualTo(1);
        assertThat(RonaldmichaRuc.esValido("10000000031")).isTrue();
    }
}
