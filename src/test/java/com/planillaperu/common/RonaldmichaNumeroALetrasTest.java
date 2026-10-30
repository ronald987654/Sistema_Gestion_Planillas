package com.planillaperu.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaNumeroALetrasTest {

    @Test
    void enteros() {
        assertThat(RonaldmichaNumeroALetras.entero(0)).isEqualTo("cero");
        assertThat(RonaldmichaNumeroALetras.entero(16)).isEqualTo("dieciséis");
        assertThat(RonaldmichaNumeroALetras.entero(21)).isEqualTo("veintiuno");
        assertThat(RonaldmichaNumeroALetras.entero(45)).isEqualTo("cuarenta y cinco");
        assertThat(RonaldmichaNumeroALetras.entero(100)).isEqualTo("cien");
        assertThat(RonaldmichaNumeroALetras.entero(101)).isEqualTo("ciento uno");
        assertThat(RonaldmichaNumeroALetras.entero(500)).isEqualTo("quinientos");
        assertThat(RonaldmichaNumeroALetras.entero(1000)).isEqualTo("mil");
        assertThat(RonaldmichaNumeroALetras.entero(1001)).isEqualTo("mil uno");
        assertThat(RonaldmichaNumeroALetras.entero(21000)).isEqualTo("veintiún mil");
        assertThat(RonaldmichaNumeroALetras.entero(31500)).isEqualTo("treinta y un mil quinientos");
        assertThat(RonaldmichaNumeroALetras.entero(1_000_000)).isEqualTo("un millón");
        assertThat(RonaldmichaNumeroALetras.entero(2_500_000)).isEqualTo("dos millones quinientos mil");
        assertThat(RonaldmichaNumeroALetras.entero(21_000_000)).isEqualTo("veintiún millones");
    }

    @Test
    void soles() {
        assertThat(RonaldmichaNumeroALetras.soles(new BigDecimal("3500.25"))).isEqualTo("TRES MIL QUINIENTOS CON 25/100 SOLES");
        assertThat(RonaldmichaNumeroALetras.soles(new BigDecimal("50137.14")))
                .isEqualTo("CINCUENTA MIL CIENTO TREINTA Y SIETE CON 14/100 SOLES");
        assertThat(RonaldmichaNumeroALetras.texto(new BigDecimal("1364.07")))
                .isEqualTo("Mil trescientos sesenta y cuatro con 07/100 soles");
        assertThat(RonaldmichaNumeroALetras.texto(new BigDecimal("21.50"))).isEqualTo("Veintiuno con 50/100 soles");
        assertThat(RonaldmichaNumeroALetras.soles(BigDecimal.ZERO)).isEqualTo("CERO CON 00/100 SOLES");
        assertThat(RonaldmichaNumeroALetras.soles(new BigDecimal("999999.999"))).isEqualTo("UN MILLÓN CON 00/100 SOLES");
    }
}
