package com.planillaperu.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

public final class RonaldmichaTablaExcel {

    public record Hoja(String nombre, String titulo, List<String> encabezados, List<List<Object>> filas,
                       boolean ultimaEsTotal, List<Integer> columnasSuma) {

        public Hoja(String nombre, String titulo, List<String> encabezados, List<List<Object>> filas,
                    boolean ultimaEsTotal) {
            this(nombre, titulo, encabezados, filas, ultimaEsTotal, List.of());
        }
    }

    private RonaldmichaTablaExcel() {
    }

    public static byte[] crear(List<Hoja> hojas) {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            CellStyle titulo = libro.createCellStyle();
            Font fTitulo = libro.createFont();
            fTitulo.setBold(true);
            fTitulo.setFontHeightInPoints((short) 13);
            titulo.setFont(fTitulo);

            CellStyle cabecera = libro.createCellStyle();
            Font fCabecera = libro.createFont();
            fCabecera.setBold(true);
            cabecera.setFont(fCabecera);
            cabecera.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecera.setWrapText(true);

            CellStyle monto = libro.createCellStyle();
            monto.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));
            CellStyle montoTotal = libro.createCellStyle();
            montoTotal.cloneStyleFrom(monto);
            montoTotal.setFont(fCabecera);
            CellStyle textoTotal = libro.createCellStyle();
            textoTotal.setFont(fCabecera);

            for (Hoja h : hojas) {
                Sheet hoja = libro.createSheet(nombreValido(h.nombre()));
                int r = 0;
                if (h.titulo() != null) {
                    Cell c = hoja.createRow(r).createCell(0);
                    c.setCellValue(h.titulo());
                    c.setCellStyle(titulo);
                    r += 2;
                }
                Row fila = hoja.createRow(r++);
                for (int i = 0; i < h.encabezados().size(); i++) {
                    Cell c = fila.createCell(i);
                    c.setCellValue(h.encabezados().get(i));
                    c.setCellStyle(cabecera);
                }
                int primeraDato = r;
                for (int f = 0; f < h.filas().size(); f++) {
                    boolean total = h.ultimaEsTotal() && f == h.filas().size() - 1;
                    Row datos = hoja.createRow(r++);
                    List<Object> valores = h.filas().get(f);
                    for (int i = 0; i < valores.size(); i++) {
                        Object v = valores.get(i);
                        Cell c = datos.createCell(i);
                        if (v instanceof BigDecimal b) {
                            c.setCellValue(b.doubleValue());
                            c.setCellStyle(total ? montoTotal : monto);
                        } else if (v instanceof Integer n) {
                            c.setCellValue(n);
                            if (total) {
                                c.setCellStyle(textoTotal);
                            }
                        } else {
                            c.setCellValue(v == null ? "" : v.toString());
                            if (total) {
                                c.setCellStyle(textoTotal);
                            }
                        }
                    }
                }
                if (!h.columnasSuma().isEmpty() && !h.filas().isEmpty()) {
                    Row total = hoja.createRow(r);
                    Cell etiqueta = total.createCell(0);
                    etiqueta.setCellValue("TOTAL");
                    etiqueta.setCellStyle(textoTotal);
                    for (int col : h.columnasSuma()) {
                        String letra = CellReference.convertNumToColString(col);
                        Cell c = total.createCell(col);
                        c.setCellFormula("SUM(" + letra + (primeraDato + 1) + ":" + letra + r + ")");
                        c.setCellStyle(montoTotal);
                    }
                }
                for (int i = 0; i < h.encabezados().size(); i++) {
                    hoja.autoSizeColumn(i);
                }
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel", e);
        }
    }

    private static String nombreValido(String nombre) {
        String limpio = nombre.replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
        return limpio.length() > 31 ? limpio.substring(0, 31) : limpio;
    }
}
