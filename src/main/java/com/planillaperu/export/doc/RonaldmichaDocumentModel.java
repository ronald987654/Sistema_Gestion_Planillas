package com.planillaperu.export.doc;

import java.math.BigDecimal;
import java.util.List;

public record RonaldmichaDocumentModel(
        String titulo,
        String subtitulo,
        List<Copia> copias,
        Orientacion orientacion,
        List<Bloque> bloques,
        String archivo) {

    public enum Copia {
        EMPLEADOR("COPIA EMPLEADOR"),
        TRABAJADOR("COPIA TRABAJADOR");

        private final String rotulo;

        Copia(String rotulo) {
            this.rotulo = rotulo;
        }

        public String rotulo() {
            return rotulo;
        }
    }

    public enum Orientacion { VERTICAL, HORIZONTAL }

    public static final List<Copia> DOS_COPIAS = List.of(Copia.EMPLEADOR, Copia.TRABAJADOR);

    public RonaldmichaDocumentModel {
        copias = List.copyOf(copias);
        bloques = List.copyOf(bloques);
    }

    public RonaldmichaDocumentModel conArchivo(String nombre) {
        return new RonaldmichaDocumentModel(titulo, subtitulo, copias, orientacion, bloques, nombre);
    }

    public RonaldmichaDocumentModel sinCopias() {
        return new RonaldmichaDocumentModel(titulo, subtitulo, List.of(), orientacion, bloques, archivo);
    }

    public RonaldmichaDocumentModel conSubtitulo(String nuevo) {
        return new RonaldmichaDocumentModel(titulo, nuevo, copias, orientacion, bloques, archivo);
    }

    public sealed interface Bloque permits Heading, Paragraph, KeyValue, Table, Tri, Total, Firmas, Boleta {
    }

    public boolean esBoleta() {
        return bloques.size() == 1 && bloques.getFirst() instanceof Boleta;
    }

    public Boleta boleta() {
        return (Boleta) bloques.getFirst();
    }

    public RonaldmichaDocumentModel conCopias(List<Copia> nuevas) {
        return new RonaldmichaDocumentModel(titulo, subtitulo, nuevas, orientacion, bloques, archivo);
    }

    public record Heading(String texto) implements Bloque {
    }

    public record Paragraph(String texto) implements Bloque {
    }

    public record KeyValue(List<Par> pares) implements Bloque {

        public KeyValue {
            pares = List.copyOf(pares);
        }
    }

    public record Par(String etiqueta, String valor) {
    }

    public record Table(List<String> encabezados, List<List<String>> filas, List<String> pie, List<Boolean> numericas)
            implements Bloque {

        public Table {
            encabezados = List.copyOf(encabezados);
            filas = filas.stream().map(List::copyOf).toList();
            pie = List.copyOf(pie);
            numericas = List.copyOf(numericas);
        }

        public boolean numerica(int columna) {
            return columna < numericas.size() && numericas.get(columna);
        }
    }

    public record Tri(Columna ingresos, Columna descuentos, Columna aportes) implements Bloque {

        public List<Columna> columnas() {
            return List.of(ingresos, descuentos, aportes);
        }
    }

    public record Columna(String titulo, List<Item> items, BigDecimal total) {

        public Columna {
            items = List.copyOf(items);
        }
    }

    public record Item(String codigo, String concepto, BigDecimal monto) {
    }

    public record Total(String etiqueta, BigDecimal monto, String enLetras) implements Bloque {
    }

    public record Firmas(List<Firma> firmas) implements Bloque {

        public Firmas {
            firmas = List.copyOf(firmas);
        }
    }

    public record Firma(String nombre, String detalle, boolean representante) {
    }

    public record Boleta(
            String numero,
            String mes,
            String fechaPago,
            List<FilaDatos> datos,
            Conceptos remuneraciones,
            Conceptos descuentos,
            Conceptos aportes,
            BigDecimal totalRemuneraciones,
            BigDecimal totalDescuentos,
            BigDecimal neto,
            String enLetras,
            String leyendaCese,
            FirmasBoleta firmas) implements Bloque {

        public static final String COLOR = "#0E5A47";
        public static final String FONDO_TOTALES = "#E3EFEA";
        public static final String TEXTO_TOTALES = "#083A2E";
        public static final String BORDE = "#9E9E9E";
        public static final String COMPLETAR = "[COMPLETAR]";
        public static final String TITULO = "BOLETA DE PAGO DE REMUNERACIONES";
        public static final String NORMA = "D.S. N° 001-98-TR y modificatorias (D.S. N° 009-2011-TR)";

        public Boleta {
            datos = List.copyOf(datos);
        }

        public List<Conceptos> cuerpo() {
            return List.of(remuneraciones, descuentos, aportes);
        }

        public int filasCuerpo() {
            return cuerpo().stream().mapToInt(c -> c.lineas().size()).max().orElse(0);
        }

        public static String notaCopia(Copia copia) {
            if (copia == null) {
                return null;
            }
            return copia == Copia.EMPLEADOR ? "Constancia de entrega: el trabajador recibe su copia"
                    : "Conserve esta boleta";
        }

        public static String leyendaFinal(String emitida) {
            return "Boleta emitida el " + emitida + " por Planilla Perú · Entréguese a más tardar el tercer día hábil "
                    + "siguiente a la fecha de pago (D.S. 001-98-TR) · Página 1 de 1";
        }
    }

    public record FilaDatos(List<GrupoDatos> grupos) {

        public FilaDatos {
            grupos = List.copyOf(grupos);
        }
    }

    public record GrupoDatos(String titulo, List<Campo> campos, float ancho) {

        public GrupoDatos {
            campos = List.copyOf(campos);
        }
    }

    public record Campo(String etiqueta, String valor, float ancho) {
    }

    public record Conceptos(String titulo, boolean conPorcentaje, List<LineaBoleta> lineas) {

        public Conceptos {
            lineas = List.copyOf(lineas);
        }
    }

    public record LineaBoleta(String codigo, String concepto, BigDecimal monto, String porcentaje) {
    }

    public record FirmasBoleta(String representante, String documentoRepresentante, String cargoRepresentante,
                               String trabajador, String documentoTrabajador, boolean firmaDigital, boolean huella) {
    }
}
