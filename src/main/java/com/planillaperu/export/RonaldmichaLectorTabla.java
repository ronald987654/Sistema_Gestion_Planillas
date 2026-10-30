package com.planillaperu.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RonaldmichaLectorTabla {

    public record Fila(int numero, List<String> valores) {

        public Fila {
            valores = List.copyOf(valores);
        }
    }

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private RonaldmichaLectorTabla() {
    }

    public static List<Fila> leer(Path archivo) throws IOException {
        return leer(archivo, null);
    }

    public static List<Fila> leer(Path archivo, String hojaPreferida) throws IOException {
        byte[] bytes = Files.readAllBytes(archivo);
        String nombre = archivo.getFileName().toString().toLowerCase(Locale.ROOT);
        if (nombre.endsWith(".xlsx") || esZip(bytes)) {
            return leerXlsx(new ByteArrayInputStream(bytes), hojaPreferida);
        }
        if (nombre.endsWith(".xls")) {
            throw new IOException("El formato .xls (Excel 97-2003) no está soportado: guárdelo como .xlsx o .csv.");
        }
        return leerCsv(new String(bytes, StandardCharsets.UTF_8));
    }

    public static List<Fila> leerCsv(String contenido) {
        String texto = contenido.startsWith("﻿") ? contenido.substring(1) : contenido;
        char separador = detectarSeparador(texto);
        List<Fila> filas = new ArrayList<>();
        List<String> actual = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean comillas = false;
        int linea = 1;
        int inicioFila = 1;

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (comillas) {
                if (c == '"') {
                    if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                        campo.append('"');
                        i++;
                    } else {
                        comillas = false;
                    }
                } else {
                    if (c == '\n') {
                        linea++;
                    }
                    campo.append(c);
                }
            } else if (c == '"') {
                comillas = true;
            } else if (c == separador) {
                actual.add(campo.toString().trim());
                campo.setLength(0);
            } else if (c == '\r') {
            } else if (c == '\n') {
                actual.add(campo.toString().trim());
                campo.setLength(0);
                agregarSiNoVacia(filas, inicioFila, actual);
                actual = new ArrayList<>();
                linea++;
                inicioFila = linea;
            } else {
                campo.append(c);
            }
        }
        if (campo.length() > 0 || !actual.isEmpty()) {
            actual.add(campo.toString().trim());
            agregarSiNoVacia(filas, inicioFila, actual);
        }
        return filas;
    }

    private static char detectarSeparador(String texto) {
        int puntoComa = 0;
        int coma = 0;
        boolean comillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '"') {
                comillas = !comillas;
            } else if (!comillas && (c == '\n' || c == '\r')) {
                break;
            } else if (!comillas && c == ';') {
                puntoComa++;
            } else if (!comillas && c == ',') {
                coma++;
            }
        }
        return coma > puntoComa ? ',' : ';';
    }

    public static List<Fila> leerXlsx(InputStream entrada) throws IOException {
        return leerXlsx(entrada, null);
    }

    public static List<Fila> leerXlsx(InputStream entrada, String hojaPreferida) throws IOException {
        List<Fila> filas = new ArrayList<>();
        DataFormatter formato = new DataFormatter(Locale.ROOT);
        try (Workbook libro = new XSSFWorkbook(entrada)) {
            if (libro.getNumberOfSheets() == 0) {
                return filas;
            }
            Sheet hoja = hojaPreferida == null ? null : libro.getSheet(hojaPreferida);
            if (hoja == null) {
                hoja = libro.getSheetAt(0);
            }
            int columnas = 0;
            for (Row fila : hoja) {
                columnas = Math.max(columnas, fila.getLastCellNum());
            }
            for (Row fila : hoja) {
                List<String> valores = new ArrayList<>();
                for (int c = 0; c < columnas; c++) {
                    valores.add(texto(fila.getCell(c), formato));
                }
                agregarSiNoVacia(filas, fila.getRowNum() + 1, valores);
            }
        } catch (RuntimeException e) {
            throw new IOException("No se pudo leer el archivo Excel: " + e.getMessage(), e);
        }
        return filas;
    }

    private static String texto(Cell celda, DataFormatter formato) {
        if (celda == null) {
            return "";
        }
        CellType tipo = celda.getCellType() == CellType.FORMULA ? celda.getCachedFormulaResultType() : celda.getCellType();
        if (tipo == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(celda)) {
                if (celda.getNumericCellValue() < 1) {
                    return HORA.format(celda.getLocalDateTimeCellValue().toLocalTime());
                }
                return FECHA.format(celda.getLocalDateTimeCellValue().toLocalDate());
            }
            return new BigDecimal(String.valueOf(celda.getNumericCellValue())).stripTrailingZeros().toPlainString();
        }
        if (tipo == CellType.BOOLEAN) {
            return celda.getBooleanCellValue() ? "SI" : "NO";
        }
        return formato.formatCellValue(celda).trim();
    }

    private static void agregarSiNoVacia(List<Fila> filas, int numero, List<String> valores) {
        if (valores.stream().anyMatch(v -> !v.isBlank())) {
            filas.add(new Fila(numero, valores));
        }
    }

    private static boolean esZip(byte[] bytes) {
        return bytes.length > 3 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4;
    }
}
