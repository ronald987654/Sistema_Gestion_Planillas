package com.planillaperu.export;

import com.planillaperu.calc.RonaldmichaAsistenciaCalculator.Dia;
import com.planillaperu.common.RonaldmichaHoras;
import com.planillaperu.domain.RonaldmichaFeriado;
import com.planillaperu.domain.RonaldmichaMarcacion;
import com.planillaperu.domain.RonaldmichaNovedad;
import com.planillaperu.domain.RonaldmichaTratamientoHe;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class RonaldmichaAsistenciaExcel {

    public static final String HOJA_ASISTENCIA = "Asistencia";

    public static final List<String> COLUMNAS = List.of("Documento", "Apellidos y nombres", "Fecha (AAAA-MM-DD)",
            "Día", "Tipo de día", "Entrada (HH:MM)", "Salida", "Novedad", "HE autorizada (SI/NO)",
            "Tratamiento HE (Pagar/Compensar)", "Horario");

    private static final Locale ES_PE = Locale.forLanguageTag("es-PE");

    public record FilaPlantilla(String documento, String nombre, LocalDate fecha, String tipoDia, RonaldmichaMarcacion marcacion,
                                String horario) {
    }

    public record FilaResumen(String documento, String nombre, String origen, int diasLaborables, int asistencias,
                              int faltas, int tardanzaMin, int minutosTrabajados, int he25Min, int he35Min,
                              int compensarMin, int feriadosTrabajados, int heSinAutorizarMin, BigDecimal importeHe25,
                              BigDecimal importeHe35, BigDecimal importeFeriados) {
    }

    public record FilaDetalle(String documento, String nombre, Dia dia) {
    }

    private RonaldmichaAsistenciaExcel() {
    }

    public static byte[] plantilla(YearMonth mes, List<FilaPlantilla> filas, List<RonaldmichaFeriado> feriadosAnio) {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            RonaldmichaEstilos e = new RonaldmichaEstilos(libro);
            Sheet hoja = libro.createSheet(HOJA_ASISTENCIA);
            cabecera(hoja, e, COLUMNAS);
            int r = 1;
            for (FilaPlantilla f : filas) {
                RonaldmichaMarcacion m = f.marcacion();
                Row fila = hoja.createRow(r++);
                texto(fila, 0, f.documento(), e.texto);
                texto(fila, 1, f.nombre(), null);
                texto(fila, 2, f.fecha().toString(), e.texto);
                texto(fila, 3, dia(f.fecha()), null);
                texto(fila, 4, f.tipoDia(), null);
                texto(fila, 5, m == null ? "" : RonaldmichaHoras.texto(m.ingreso()), e.texto);
                texto(fila, 6, m == null ? "" : RonaldmichaHoras.texto(m.salida()), e.texto);
                texto(fila, 7, (m == null ? RonaldmichaNovedad.NORMAL : m.novedad()).descripcion(), null);
                texto(fila, 8, m != null && m.heAutorizada() ? "SI" : "NO", null);
                texto(fila, 9, (m == null ? RonaldmichaTratamientoHe.PAGAR : m.heTratamiento()).descripcion(), null);
                texto(fila, 10, f.horario(), null);
            }
            int ultima = Math.max(1, r - 1);
            lista(hoja, 7, ultima, Arrays.stream(RonaldmichaNovedad.values()).map(RonaldmichaNovedad::descripcion).toArray(String[]::new));
            lista(hoja, 8, ultima, "SI", "NO");
            lista(hoja, 9, ultima, "Pagar", "Compensar");
            anchos(hoja, 12, 32, 18, 11, 24, 15, 10, 22, 20, 28, 30);
            hoja.createFreezePane(2, 1);

            Sheet instrucciones = libro.createSheet("Instrucciones");
            String[] lineas = {
                    "Plantilla de asistencia · " + mesTexto(mes),
                    "",
                    "1. Llene Entrada y Salida con el formato HH:MM (por ejemplo 08:00 y 17:30). También se aceptan 8:00 a. m. o celdas con formato de hora.",
                    "2. La Fecha va como AAAA-MM-DD (o como fecha de Excel). No cambie el Documento: debe ser el de un trabajador registrado.",
                    "3. Novedad: Normal, Vacaciones, Licencia con goce, Descanso médico, Licencia sin goce o Descanso sustitutorio.",
                    "4. Si hay horas extra, indique si están autorizadas (SI/NO) y si se pagan o se compensan (Pagar/Compensar).",
                    "5. Las filas sin entrada, salida ni novedad se ignoran. Solo se importan los días del periodo abierto.",
                    "6. Los encabezados se buscan por nombre: puede mover o quitar columnas informativas (Día, Tipo de día, Horario).",
                    "7. Guarde el archivo como .xlsx (no .xls) y use «Importar Excel» en la vista Asistencia.",
                    "",
                    "También se acepta el CSV del reloj, sin encabezados, con las columnas: DNI, fecha, ingreso, salida.",
            };
            for (int i = 0; i < lineas.length; i++) {
                texto(instrucciones.createRow(i), 0, lineas[i], i == 0 ? e.negrita : null);
            }
            instrucciones.setColumnWidth(0, 140 * 256);

            hojaFeriados(libro.createSheet("Feriados " + mes.getYear()), e, feriadosAnio);
            return escribir(libro);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar la plantilla de asistencia", ex);
        }
    }

    public static byte[] exportar(YearMonth mes, List<FilaResumen> resumen, List<FilaDetalle> detalle,
                                  List<RonaldmichaFeriado> feriadosMes) {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            RonaldmichaEstilos e = new RonaldmichaEstilos(libro);

            Sheet hoja = libro.createSheet("Resumen");
            cabecera(hoja, e, List.of("Documento", "Apellidos y nombres", "Origen", "Días laborables", "Asistencias",
                    "Faltas", "Tardanza (min)", "Horas trabajadas", "HE 25% (h)", "HE 35% (h)", "A compensar (h)",
                    "Feriados/descansos trabajados", "HE sin autorizar (h)", "Importe HE 25% (S/)",
                    "Importe HE 35% (S/)", "Importe feriados (S/)", "Total (S/)"));
            int r = 1;
            for (FilaResumen f : resumen) {
                Row fila = hoja.createRow(r++);
                texto(fila, 0, f.documento(), e.texto);
                texto(fila, 1, f.nombre(), null);
                texto(fila, 2, f.origen(), null);
                entero(fila, 3, f.diasLaborables());
                entero(fila, 4, f.asistencias());
                entero(fila, 5, f.faltas());
                entero(fila, 6, f.tardanzaMin());
                horas(fila, 7, f.minutosTrabajados(), e);
                horas(fila, 8, f.he25Min(), e);
                horas(fila, 9, f.he35Min(), e);
                horas(fila, 10, f.compensarMin(), e);
                entero(fila, 11, f.feriadosTrabajados());
                horas(fila, 12, f.heSinAutorizarMin(), e);
                monto(fila, 13, f.importeHe25(), e);
                monto(fila, 14, f.importeHe35(), e);
                monto(fila, 15, f.importeFeriados(), e);
                monto(fila, 16, sumar(f.importeHe25(), f.importeHe35(), f.importeFeriados()), e);
            }
            anchos(hoja, 12, 32, 16, 10, 10, 8, 10, 11, 10, 10, 11, 14, 12, 14, 14, 14, 12);
            hoja.createFreezePane(2, 1);

            Sheet diario = libro.createSheet("Detalle diario");
            cabecera(diario, e, List.of("Documento", "Apellidos y nombres", "Fecha", "Día", "Tipo de día", "Entrada",
                    "Salida", "Horas trabajadas", "Tardanza (min)", "Sobretiempo (h)", "HE 25% (h)", "HE 35% (h)",
                    "A compensar (h)", "Estado", "Novedad", "HE autorizada", "Tratamiento HE", "Origen"));
            r = 1;
            for (FilaDetalle f : detalle) {
                Dia d = f.dia();
                RonaldmichaMarcacion m = d.marcacion();
                Row fila = diario.createRow(r++);
                texto(fila, 0, f.documento(), e.texto);
                texto(fila, 1, f.nombre(), null);
                texto(fila, 2, d.fecha().toString(), e.texto);
                texto(fila, 3, dia(d.fecha()), null);
                texto(fila, 4, d.feriado() == null ? d.tipo().etiqueta() : "Feriado · " + d.feriado(), null);
                texto(fila, 5, RonaldmichaHoras.texto(m.ingreso()), e.texto);
                texto(fila, 6, RonaldmichaHoras.texto(m.salida()), e.texto);
                horas(fila, 7, d.minutosTrabajados(), e);
                entero(fila, 8, d.tardanzaMin());
                horas(fila, 9, d.sobretiempoMin(), e);
                horas(fila, 10, d.he25Min(), e);
                horas(fila, 11, d.he35Min(), e);
                horas(fila, 12, d.compensarMin(), e);
                texto(fila, 13, d.estado().etiqueta(), null);
                texto(fila, 14, m.novedad().descripcion(), null);
                texto(fila, 15, d.sobretiempoMin() > 0 ? (m.heAutorizada() ? "SI" : "NO") : "", null);
                texto(fila, 16, d.sobretiempoMin() > 0 ? m.heTratamiento().descripcion() : "", null);
                texto(fila, 17, m.vacia() ? "" : m.origen().etiqueta(), null);
            }
            anchos(diario, 12, 32, 12, 11, 30, 9, 9, 11, 10, 11, 10, 10, 11, 22, 20, 11, 13, 15);
            diario.createFreezePane(3, 1);

            hojaFeriados(libro.createSheet("Feriados"), e, feriadosMes);
            return escribir(libro);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el Excel de asistencia", ex);
        }
    }

    private static void hojaFeriados(Sheet hoja, RonaldmichaEstilos e, List<RonaldmichaFeriado> feriados) {
        cabecera(hoja, e, List.of("Fecha", "Día", "Feriado", "Origen"));
        int r = 1;
        for (RonaldmichaFeriado f : feriados) {
            Row fila = hoja.createRow(r++);
            texto(fila, 0, f.fecha().toString(), e.texto);
            texto(fila, 1, dia(f.fecha()), null);
            texto(fila, 2, f.nombre(), null);
            texto(fila, 3, f.agregado() ? "Agregado por la empresa" : "Nacional", null);
        }
        anchos(hoja, 13, 12, 40, 24);
    }

    private static void cabecera(Sheet hoja, RonaldmichaEstilos e, List<String> columnas) {
        Row fila = hoja.createRow(0);
        for (int c = 0; c < columnas.size(); c++) {
            texto(fila, c, columnas.get(c), e.cabecera);
        }
    }

    private static void texto(Row fila, int columna, String valor, CellStyle estilo) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor == null ? "" : valor);
        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
    }

    private static void entero(Row fila, int columna, int valor) {
        fila.createCell(columna).setCellValue(valor);
    }

    private static void horas(Row fila, int columna, int minutos, RonaldmichaEstilos e) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(RonaldmichaHoras.enHoras(minutos).doubleValue());
        celda.setCellStyle(e.decimal);
    }

    private static void monto(Row fila, int columna, BigDecimal valor, RonaldmichaEstilos e) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor == null ? 0 : valor.doubleValue());
        celda.setCellStyle(e.decimal);
    }

    private static BigDecimal sumar(BigDecimal... montos) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal m : montos) {
            if (m != null) {
                total = total.add(m);
            }
        }
        return total;
    }

    private static void lista(Sheet hoja, int columna, int ultimaFila, String... valores) {
        DataValidationHelper ayuda = hoja.getDataValidationHelper();
        DataValidation validacion = ayuda.createValidation(ayuda.createExplicitListConstraint(valores),
                new CellRangeAddressList(1, ultimaFila, columna, columna));
        validacion.setShowErrorBox(true);
        hoja.addValidationData(validacion);
    }

    private static void anchos(Sheet hoja, int... caracteres) {
        for (int c = 0; c < caracteres.length; c++) {
            hoja.setColumnWidth(c, caracteres[c] * 256);
        }
    }

    private static String dia(LocalDate fecha) {
        String d = fecha.getDayOfWeek().getDisplayName(TextStyle.FULL, ES_PE);
        return d.substring(0, 1).toUpperCase(ES_PE) + d.substring(1);
    }

    private static String mesTexto(YearMonth mes) {
        String m = mes.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, ES_PE);
        return m.substring(0, 1).toUpperCase(ES_PE) + m.substring(1) + " " + mes.getYear();
    }

    private static byte[] escribir(XSSFWorkbook libro) throws IOException {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private static final class RonaldmichaEstilos {
        final CellStyle cabecera;
        final CellStyle negrita;
        final CellStyle texto;
        final CellStyle decimal;

        RonaldmichaEstilos(XSSFWorkbook libro) {
            Font fuenteNegrita = libro.createFont();
            fuenteNegrita.setBold(true);
            cabecera = libro.createCellStyle();
            cabecera.setFont(fuenteNegrita);
            cabecera.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecera.setWrapText(true);
            negrita = libro.createCellStyle();
            negrita.setFont(fuenteNegrita);
            texto = libro.createCellStyle();
            texto.setDataFormat(libro.createDataFormat().getFormat("@"));
            decimal = libro.createCellStyle();
            decimal.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));
        }
    }
}
