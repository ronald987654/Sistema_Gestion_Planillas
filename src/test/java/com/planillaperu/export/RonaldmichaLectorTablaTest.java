package com.planillaperu.export;

import com.planillaperu.export.RonaldmichaLectorTabla.Fila;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaLectorTablaTest {

    @TempDir
    Path carpeta;

    @Test
    void csvConPuntoYComaBomYFilasVacias() {
        List<Fila> filas = RonaldmichaLectorTabla.leerCsv("﻿a;b;c\r\n1;dos;3\r\n\r\n;;\r\n4;\"cinco; seis\";\"con \"\"comillas\"\"\"\r\n");

        assertThat(filas).extracting(Fila::numero).containsExactly(1, 2, 5);
        assertThat(filas.get(0).valores()).containsExactly("a", "b", "c");
        assertThat(filas.get(2).valores()).containsExactly("4", "cinco; seis", "con \"comillas\"");
    }

    @Test
    void csvConComaComoSeparador() {
        List<Fila> filas = RonaldmichaLectorTabla.leerCsv("dni,dias\n40000001,\"1,2,3\"\n");

        assertThat(filas.get(1).valores()).containsExactly("40000001", "1,2,3");
    }

    @Test
    void plantillaCsvTieneTodasLasColumnas() {
        List<Fila> filas = RonaldmichaLectorTabla.leerCsv(new String(RonaldmichaPlantillaTrabajadores.csv(), StandardCharsets.UTF_8));

        assertThat(filas.getFirst().valores()).containsExactlyElementsOf(RonaldmichaPlantillaTrabajadores.COLUMNAS);
        assertThat(filas.get(1).valores()).hasSize(RonaldmichaPlantillaTrabajadores.COLUMNAS.size());
    }

    @Test
    void xlsxConHorasYFechasDeExcelEnLaHojaPreferida() throws IOException {
        Path archivo = carpeta.resolve("reloj.xlsx");
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook libro = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            libro.createSheet("Instrucciones").createRow(0).createCell(0).setCellValue("léame");
            org.apache.poi.ss.usermodel.Sheet hoja = libro.createSheet("Asistencia");
            org.apache.poi.ss.usermodel.CellStyle hora = libro.createCellStyle();
            hora.setDataFormat(libro.createDataFormat().getFormat("h:mm"));
            org.apache.poi.ss.usermodel.CellStyle fecha = libro.createCellStyle();
            fecha.setDataFormat(libro.createDataFormat().getFormat("yyyy-mm-dd"));
            org.apache.poi.ss.usermodel.Row fila = hoja.createRow(0);
            fila.createCell(0).setCellValue(java.time.LocalDate.of(2026, 10, 5));
            fila.getCell(0).setCellStyle(fecha);
            fila.createCell(1).setCellValue(0.354166666667);
            fila.getCell(1).setCellStyle(hora);
            try (java.io.OutputStream salida = Files.newOutputStream(archivo)) {
                libro.write(salida);
            }
        }

        List<Fila> filas = RonaldmichaLectorTabla.leer(archivo, "Asistencia");

        assertThat(filas).singleElement().satisfies(f -> assertThat(f.valores()).containsExactly("05/10/2026", "08:30"));
        assertThat(RonaldmichaLectorTabla.leer(archivo).getFirst().valores()).containsExactly("léame");
    }

    @Test
    void xlsAntiguoPideGuardarComoXlsx() throws IOException {
        Path archivo = carpeta.resolve("viejo.xls");
        Files.write(archivo, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0});

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> RonaldmichaLectorTabla.leer(archivo))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("guárdelo como .xlsx");
    }

    @Test
    void leeUnXlsxPorExtensionYContenido() throws IOException {
        byte[] excel = RonaldmichaPlantillaTrabajadores.excel(List.of(), Map.of(), Optional.of("aviso"));
        Path archivo = carpeta.resolve("vacio.xlsx");
        Files.write(archivo, excel);

        List<Fila> filas = RonaldmichaLectorTabla.leer(archivo);

        assertThat(filas).singleElement()
                .satisfies(f -> assertThat(f.valores()).containsExactlyElementsOf(RonaldmichaPlantillaTrabajadores.COLUMNAS));
    }
}
