package com.planillaperu.export;

import com.planillaperu.domain.RonaldmichaHorario;
import com.planillaperu.domain.RonaldmichaTrabajador;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RonaldmichaPlantillaTrabajadores {

    public static final List<String> COLUMNAS = List.of(
            "codigo", "tipo_doc", "num_doc", "ap_paterno", "ap_materno", "nombres", "celular", "correo",
            "fecha_ingreso", "fecha_cese", "area", "cargo", "tipo_contrato", "regimen_laboral", "centro_costo",
            "sucursal", "sueldo", "movilidad", "salud", "sistema_pension", "cuspp", "tipo_comision",
            "asig_familiar", "vida_ley", "tipo_pago", "banco_sueldo", "cta_sueldo", "banco_cts", "cta_cts",
            "hora_ingreso", "hora_salida", "refrigerio_min", "tolerancia_min", "dias_laborables");

    public static final Set<String> OPCIONALES = Set.of("codigo", "ap_materno", "celular", "correo", "fecha_cese",
            "tipo_contrato", "centro_costo", "sucursal", "movilidad", "cuspp", "tipo_comision", "banco_sueldo",
            "cta_sueldo", "banco_cts", "cta_cts", "tolerancia_min", "refrigerio_min");

    private static final Set<String> NUMERICAS = Set.of("sueldo", "movilidad");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String CSV_SEPARADOR = ";";

    private static List<String> ejemplo() {
        return List.of(
                "", "DNI", "40000099", "Pérez", "Gómez", "Juan Carlos", "900000099", "juan.perez@ejemplo.pe",
                FECHA.format(LocalDate.now().withDayOfMonth(1)), "", "Operaciones", "Asistente", "Plazo fijo", "general",
                "CC-OPE",
                "Lima", "1500.00", "0.00", "EsSalud", "AFP Integra", "000000JPGOM0", "flujo",
                "NO", "NO", "Depósito en cuenta", "Banco Ejemplo", "000-0000099", "", "",
                "08:00", "17:00", "60", "5", "1,2,3,4,5");
    }

    private RonaldmichaPlantillaTrabajadores() {
    }

    public static byte[] csv() {
        String texto = "﻿" + lineaCsv(COLUMNAS) + "\r\n" + lineaCsv(ejemplo()) + "\r\n";
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    public static List<String> fila(RonaldmichaTrabajador t, RonaldmichaHorario h) {
        List<String> v = new ArrayList<>();
        v.add(t.codigo());
        v.add(t.tipoDoc().codigo());
        v.add(t.numDoc());
        v.add(t.apPaterno());
        v.add(n(t.apMaterno()));
        v.add(t.nombres());
        v.add(n(t.celular()));
        v.add(n(t.correo()));
        v.add(fecha(t.fechaIngreso()));
        v.add(fecha(t.fechaCese()));
        v.add(n(t.area()));
        v.add(n(t.cargo()));
        v.add(n(t.tipoContrato()));
        v.add(t.regimen().codigo());
        v.add(n(t.centroCosto()));
        v.add(n(t.sucursal()));
        v.add(t.sueldo().toPlainString());
        v.add(t.movilidad().toPlainString());
        v.add(t.salud().nombre());
        v.add(t.sistemaPension().nombre());
        v.add(n(t.cuspp()));
        v.add(t.tipoComision() == null ? "" : t.tipoComision().codigo());
        v.add(t.asigFamiliar() ? "SI" : "NO");
        v.add(t.vidaLey() ? "SI" : "NO");
        v.add(t.tipoPago().nombre());
        v.add(n(t.bancoSueldo()));
        v.add(n(t.ctaSueldo()));
        v.add(n(t.bancoCts()));
        v.add(n(t.ctaCts()));
        v.add(h == null ? "" : h.horaIngreso().toString());
        v.add(h == null ? "" : h.horaSalida().toString());
        v.add(h == null ? "" : String.valueOf(h.refrigerioMin()));
        v.add(h == null ? "" : String.valueOf(h.toleranciaMin()));
        v.add(h == null ? "" : h.diasComoTexto());
        return v;
    }

    public static byte[] excel(List<RonaldmichaTrabajador> trabajadores, Map<Long, RonaldmichaHorario> horarios, Optional<String> aviso) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Trabajadores");
            CellStyle cabecera = libro.createCellStyle();
            Font negrita = libro.createFont();
            negrita.setBold(true);
            cabecera.setFont(negrita);
            cabecera.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle monto = libro.createCellStyle();
            monto.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));

            Row filaCabecera = hoja.createRow(0);
            for (int c = 0; c < COLUMNAS.size(); c++) {
                Cell celda = filaCabecera.createCell(c);
                celda.setCellValue(COLUMNAS.get(c));
                celda.setCellStyle(cabecera);
            }
            int r = 1;
            for (RonaldmichaTrabajador t : trabajadores) {
                Row fila = hoja.createRow(r++);
                List<String> valores = fila(t, horarios.get(t.id()));
                for (int c = 0; c < valores.size(); c++) {
                    Cell celda = fila.createCell(c);
                    if (NUMERICAS.contains(COLUMNAS.get(c))) {
                        celda.setCellValue(new BigDecimal(valores.get(c)).doubleValue());
                        celda.setCellStyle(monto);
                    } else {
                        celda.setCellValue(valores.get(c));
                    }
                }
            }
            hoja.createFreezePane(0, 1);
            for (int c = 0; c < COLUMNAS.size(); c++) {
                hoja.setColumnWidth(c, 16 * 256);
            }
            if (aviso.isPresent()) {
                Sheet hojaAviso = libro.createSheet("Aviso");
                hojaAviso.createRow(0).createCell(0).setCellValue(aviso.get());
                hojaAviso.setColumnWidth(0, 120 * 256);
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel de trabajadores", e);
        }
    }

    private static String lineaCsv(List<String> valores) {
        List<String> campos = new ArrayList<>();
        for (String v : valores) {
            boolean citar = v.contains(CSV_SEPARADOR) || v.contains(",") || v.contains("\"") || v.contains("\n");
            campos.add(citar ? "\"" + v.replace("\"", "\"\"") + "\"" : v);
        }
        return String.join(CSV_SEPARADOR, campos);
    }

    private static String fecha(LocalDate f) {
        return f == null ? "" : FECHA.format(f);
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }
}
