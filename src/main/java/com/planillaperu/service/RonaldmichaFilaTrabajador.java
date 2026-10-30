package com.planillaperu.service;

import com.planillaperu.common.RonaldmichaBusqueda;
import com.planillaperu.domain.RonaldmichaHorario;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaSalud;
import com.planillaperu.domain.RonaldmichaSistemaPension;
import com.planillaperu.domain.RonaldmichaTipoComision;
import com.planillaperu.domain.RonaldmichaTipoDocumento;
import com.planillaperu.domain.RonaldmichaTipoPago;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.export.RonaldmichaPlantillaTrabajadores;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

final class RonaldmichaFilaTrabajador {

    private static final List<DateTimeFormatter> FECHAS = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("d-M-uuuu"));

    private final Map<String, Integer> indices;
    private final List<String> valores;
    private final List<String> errores = new ArrayList<>();

    private RonaldmichaFilaTrabajador(Map<String, Integer> indices, List<String> valores) {
        this.indices = indices;
        this.valores = valores;
    }

    static Map<String, Integer> indices(List<String> cabecera) {
        Map<String, Integer> mapa = new HashMap<>();
        for (int i = 0; i < cabecera.size(); i++) {
            String nombre = RonaldmichaBusqueda.normalizar(cabecera.get(i)).replace(' ', '_');
            if (RonaldmichaPlantillaTrabajadores.COLUMNAS.contains(nombre)) {
                mapa.putIfAbsent(nombre, i);
            }
        }
        return mapa;
    }

    static List<String> columnasFaltantes(Map<String, Integer> indices) {
        return RonaldmichaPlantillaTrabajadores.COLUMNAS.stream()
                .filter(c -> !RonaldmichaPlantillaTrabajadores.OPCIONALES.contains(c) && !indices.containsKey(c))
                .toList();
    }

    static RonaldmichaFilaTrabajador de(Map<String, Integer> indices, List<String> valores) {
        return new RonaldmichaFilaTrabajador(indices, valores);
    }

    List<String> errores() {
        return errores;
    }

    RonaldmichaTrabajador trabajador() {
        RonaldmichaTipoDocumento tipoDoc = enumerado("tipo_doc", "tipo de documento", RonaldmichaTipoDocumento::desdeCodigo);
        String numDoc = texto("num_doc");
        RonaldmichaSistemaPension sistema = enumerado("sistema_pension", "sistema de pensiones", RonaldmichaFilaTrabajador::sistema);
        BigDecimal movilidad = monto("movilidad");
        return new RonaldmichaTrabajador(null, texto("codigo"), tipoDoc, numDoc, texto("ap_paterno"), texto("ap_materno"),
                texto("nombres"), texto("celular"), texto("correo"), fecha("fecha_ingreso"), fecha("fecha_cese"),
                texto("area"), texto("cargo"), texto("tipo_contrato"),
                enumerado("regimen_laboral", "régimen laboral", RonaldmichaFilaTrabajador::regimen),
                texto("centro_costo"), texto("sucursal"), monto("sueldo"),
                movilidad == null ? BigDecimal.ZERO.setScale(2) : movilidad,
                enumerado("salud", "salud", RonaldmichaSalud::desdeNombre), sistema, texto("cuspp"),
                enumerado("tipo_comision", "tipo de comisión", RonaldmichaTipoComision::desdeCodigo),
                booleano("asig_familiar"), booleano("vida_ley"),
                enumerado("tipo_pago", "tipo de pago", RonaldmichaFilaTrabajador::tipoPago),
                texto("banco_sueldo"), texto("cta_sueldo"), texto("banco_cts"), texto("cta_cts"));
    }

    RonaldmichaHorario horario() {
        LocalTime ingreso = hora("hora_ingreso");
        LocalTime salida = hora("hora_salida");
        Integer refrigerio = entero("refrigerio_min");
        Integer tolerancia = entero("tolerancia_min");
        Set<DayOfWeek> dias = dias("dias_laborables");
        if (ingreso == null || salida == null || dias == null) {
            return null;
        }
        return new RonaldmichaHorario(ingreso, salida, refrigerio == null ? 60 : refrigerio, tolerancia == null ? 0 : tolerancia,
                dias, false);
    }

    private String texto(String columna) {
        Integer i = indices.get(columna);
        if (i == null || i >= valores.size()) {
            return null;
        }
        String v = valores.get(i).trim();
        return v.isEmpty() ? null : v;
    }

    private <T> T enumerado(String columna, String nombre, Function<String, T> conversor) {
        String v = texto(columna);
        if (v == null) {
            return null;
        }
        try {
            return conversor.apply(v);
        } catch (IllegalArgumentException e) {
            errores.add(nombre + " «" + v + "» no válido");
            return null;
        }
    }

    private LocalDate fecha(String columna) {
        String v = texto(columna);
        if (v == null) {
            return null;
        }
        for (DateTimeFormatter f : FECHAS) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignorada) {
            }
        }
        errores.add(columna + " «" + v + "» no es una fecha (use dd/mm/aaaa)");
        return null;
    }

    private BigDecimal monto(String columna) {
        String v = texto(columna);
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(v.replace("S/", "").replace(",", "").replace(" ", "")).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            errores.add(columna + " «" + v + "» no es un monto");
            return null;
        }
    }

    private Integer entero(String columna) {
        String v = texto(columna);
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(v).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            errores.add(columna + " «" + v + "» no es un número entero");
            return null;
        }
    }

    private boolean booleano(String columna) {
        String v = RonaldmichaBusqueda.normalizar(texto(columna));
        return switch (v) {
            case "", "no", "n", "0", "false", "falso" -> false;
            case "si", "s", "1", "true", "verdadero", "x" -> true;
            default -> {
                errores.add(columna + " «" + v + "» debe ser SI o NO");
                yield false;
            }
        };
    }

    private LocalTime hora(String columna) {
        String v = texto(columna);
        if (v == null) {
            errores.add("falta " + columna);
            return null;
        }
        try {
            if (v.matches("0?\\.\\d+")) {
                long minutos = new BigDecimal(v).multiply(BigDecimal.valueOf(24 * 60))
                        .setScale(0, RoundingMode.HALF_UP).longValue();
                return LocalTime.MIDNIGHT.plusMinutes(minutos);
            }
            return LocalTime.parse(v.length() == 4 ? "0" + v : v.substring(0, Math.min(5, v.length())));
        } catch (DateTimeParseException | NumberFormatException e) {
            errores.add(columna + " «" + v + "» no es una hora (use hh:mm)");
            return null;
        }
    }

    private Set<DayOfWeek> dias(String columna) {
        String v = texto(columna);
        if (v == null) {
            errores.add("falta " + columna);
            return null;
        }
        try {
            Set<DayOfWeek> dias = new TreeSet<>();
            for (String parte : v.split("[,;\\s]+")) {
                if (parte.contains("-")) {
                    String[] r = parte.split("-");
                    for (int d = Integer.parseInt(r[0].trim()); d <= Integer.parseInt(r[1].trim()); d++) {
                        dias.add(DayOfWeek.of(d));
                    }
                } else if (!parte.isBlank()) {
                    dias.add(DayOfWeek.of(Integer.parseInt(parte.trim())));
                }
            }
            return dias;
        } catch (RuntimeException e) {
            errores.add(columna + " «" + v + "» no válido (use 1 a 7 separados por comas)");
            return null;
        }
    }

    private static RonaldmichaRegimenLaboral regimen(String v) {
        String n = RonaldmichaBusqueda.normalizar(v);
        for (RonaldmichaRegimenLaboral r : RonaldmichaRegimenLaboral.values()) {
            if (n.equals(r.codigo()) || n.equals(RonaldmichaBusqueda.normalizar(r.descripcion()))) {
                return r;
            }
        }
        if (n.startsWith("pequen")) {
            return RonaldmichaRegimenLaboral.PEQUENA;
        }
        if (n.startsWith("micro")) {
            return RonaldmichaRegimenLaboral.MICRO;
        }
        throw new IllegalArgumentException(v);
    }

    private static RonaldmichaSistemaPension sistema(String v) {
        String n = RonaldmichaBusqueda.normalizar(v);
        for (RonaldmichaSistemaPension s : RonaldmichaSistemaPension.values()) {
            String nombre = RonaldmichaBusqueda.normalizar(s.nombre());
            if (n.equals(nombre) || ("afp " + n).equals(nombre)) {
                return s;
            }
        }
        throw new IllegalArgumentException(v);
    }

    private static RonaldmichaTipoPago tipoPago(String v) {
        String n = RonaldmichaBusqueda.normalizar(v);
        for (RonaldmichaTipoPago t : RonaldmichaTipoPago.values()) {
            if (n.equals(RonaldmichaBusqueda.normalizar(t.nombre()))) {
                return t;
            }
        }
        if (n.startsWith("deposito")) {
            return RonaldmichaTipoPago.DEPOSITO;
        }
        throw new IllegalArgumentException(v);
    }
}
