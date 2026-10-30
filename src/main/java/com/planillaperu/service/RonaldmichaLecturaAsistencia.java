package com.planillaperu.service;

import com.planillaperu.common.RonaldmichaBusqueda;
import com.planillaperu.common.RonaldmichaHoras;
import com.planillaperu.domain.RonaldmichaNovedad;
import com.planillaperu.domain.RonaldmichaTratamientoHe;
import com.planillaperu.export.RonaldmichaLectorTabla.Fila;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class RonaldmichaLecturaAsistencia {

    enum Campo { DOCUMENTO, FECHA, ENTRADA, SALIDA, NOVEDAD, AUTORIZADA, TRATAMIENTO }

    record Registro(int fila, String documento, LocalDate fecha, LocalTime entrada, LocalTime salida, RonaldmichaNovedad novedad,
                    boolean autorizada, RonaldmichaTratamientoHe tratamiento, List<String> errores) {

        boolean sinDatos() {
            return entrada == null && salida == null && novedad == RonaldmichaNovedad.NORMAL && !autorizada;
        }
    }

    private static final Map<String, Campo> NOMBRES = new HashMap<>();

    static {
        for (String n : List.of("documento", "dni", "num doc", "num_doc", "nro documento", "n° documento", "numero de documento")) {
            NOMBRES.put(n, Campo.DOCUMENTO);
        }
        NOMBRES.put("fecha", Campo.FECHA);
        for (String n : List.of("entrada", "ingreso", "hora de ingreso", "hora ingreso", "hora de entrada")) {
            NOMBRES.put(n, Campo.ENTRADA);
        }
        for (String n : List.of("salida", "hora de salida", "hora salida")) {
            NOMBRES.put(n, Campo.SALIDA);
        }
        NOMBRES.put("novedad", Campo.NOVEDAD);
        for (String n : List.of("he autorizada", "autorizada", "horas extra autorizadas")) {
            NOMBRES.put(n, Campo.AUTORIZADA);
        }
        for (String n : List.of("tratamiento he", "tratamiento", "tratamiento de he")) {
            NOMBRES.put(n, Campo.TRATAMIENTO);
        }
    }

    private static final List<Campo> OBLIGATORIOS = List.of(Campo.DOCUMENTO, Campo.FECHA, Campo.ENTRADA, Campo.SALIDA);

    private RonaldmichaLecturaAsistencia() {
    }

    static List<Registro> leer(List<Fila> filas) {
        if (filas.isEmpty()) {
            throw new RonaldmichaValidacionException("El archivo está vacío.");
        }
        Map<Campo, Integer> indices = null;
        int inicio = 0;
        for (int i = 0; i < filas.size() && indices == null; i++) {
            Map<Campo, Integer> encontrados = encabezados(filas.get(i).valores());
            if (encontrados.containsKey(Campo.DOCUMENTO) && encontrados.containsKey(Campo.FECHA)) {
                indices = encontrados;
                inicio = i + 1;
            }
        }
        if (indices == null) {
            if (!esCsvDelReloj(filas)) {
                throw new RonaldmichaValidacionException("No se encontró la fila de encabezados (Documento, Fecha, Entrada, "
                        + "Salida). Use la plantilla Excel o el CSV del reloj (DNI, fecha, ingreso, salida).");
            }
            indices = Map.of(Campo.DOCUMENTO, 0, Campo.FECHA, 1, Campo.ENTRADA, 2, Campo.SALIDA, 3);
        }
        List<String> faltan = new ArrayList<>();
        for (Campo c : OBLIGATORIOS) {
            if (!indices.containsKey(c)) {
                faltan.add(c.name().charAt(0) + c.name().substring(1).toLowerCase());
            }
        }
        if (!faltan.isEmpty()) {
            throw new RonaldmichaValidacionException("Faltan columnas en el archivo: " + String.join(", ", faltan) + ".");
        }

        List<Registro> registros = new ArrayList<>();
        for (Fila fila : filas.subList(inicio, filas.size())) {
            registros.add(registro(fila, indices));
        }
        return registros;
    }

    static Map<Campo, Integer> encabezados(List<String> valores) {
        Map<Campo, Integer> mapa = new HashMap<>();
        for (int i = 0; i < valores.size(); i++) {
            String nombre = RonaldmichaBusqueda.normalizar(valores.get(i).replaceAll("\\(.*", "")).replace('_', ' ');
            Campo campo = NOMBRES.get(nombre);
            if (campo != null) {
                mapa.putIfAbsent(campo, i);
            }
        }
        return mapa;
    }

    private static boolean esCsvDelReloj(List<Fila> filas) {
        return filas.stream().allMatch(f -> f.valores().size() >= 4 && f.valores().getFirst().trim().matches("\\d{8}"));
    }

    private static Registro registro(Fila fila, Map<Campo, Integer> indices) {
        List<String> errores = new ArrayList<>();
        String documento = valor(fila, indices, Campo.DOCUMENTO).orElse(null);
        if (documento == null) {
            errores.add("falta el documento");
        }
        LocalDate fecha = null;
        try {
            fecha = RonaldmichaHoras.leerFecha(valor(fila, indices, Campo.FECHA).orElse(null));
        } catch (IllegalArgumentException e) {
            errores.add(e.getMessage());
        }
        LocalTime entrada = hora(fila, indices, Campo.ENTRADA, "entrada", errores);
        LocalTime salida = hora(fila, indices, Campo.SALIDA, "salida", errores);
        RonaldmichaNovedad novedad = RonaldmichaNovedad.NORMAL;
        Optional<String> textoNovedad = valor(fila, indices, Campo.NOVEDAD);
        if (textoNovedad.isPresent()) {
            try {
                novedad = RonaldmichaNovedad.desdeTexto(textoNovedad.get());
            } catch (IllegalArgumentException e) {
                errores.add("novedad «" + textoNovedad.get() + "» no válida");
            }
        }
        boolean autorizada = false;
        Optional<String> textoAutorizada = valor(fila, indices, Campo.AUTORIZADA);
        if (textoAutorizada.isPresent()) {
            switch (RonaldmichaBusqueda.normalizar(textoAutorizada.get())) {
                case "si", "s", "1", "x", "true", "verdadero" -> autorizada = true;
                case "no", "n", "0", "false", "falso" -> autorizada = false;
                default -> errores.add("HE autorizada «" + textoAutorizada.get() + "» debe ser SI o NO");
            }
        }
        RonaldmichaTratamientoHe tratamiento = RonaldmichaTratamientoHe.PAGAR;
        Optional<String> textoTratamiento = valor(fila, indices, Campo.TRATAMIENTO);
        if (textoTratamiento.isPresent()) {
            try {
                tratamiento = RonaldmichaTratamientoHe.desdeTexto(textoTratamiento.get());
            } catch (IllegalArgumentException e) {
                errores.add("tratamiento HE «" + textoTratamiento.get() + "» debe ser Pagar o Compensar");
            }
        }
        return new Registro(fila.numero(), documento, fecha, entrada, salida, novedad, autorizada, tratamiento, errores);
    }

    private static LocalTime hora(Fila fila, Map<Campo, Integer> indices, Campo campo, String nombre, List<String> errores) {
        try {
            return RonaldmichaHoras.leer(valor(fila, indices, campo).orElse(null));
        } catch (IllegalArgumentException e) {
            errores.add(nombre + ": " + e.getMessage());
            return null;
        }
    }

    private static Optional<String> valor(Fila fila, Map<Campo, Integer> indices, Campo campo) {
        Integer i = indices.get(campo);
        if (i == null || i >= fila.valores().size()) {
            return Optional.empty();
        }
        String v = fila.valores().get(i).trim();
        return v.isEmpty() ? Optional.empty() : Optional.of(v);
    }
}
