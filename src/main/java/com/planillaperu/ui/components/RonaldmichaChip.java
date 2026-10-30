package com.planillaperu.ui.components;

import com.planillaperu.common.RonaldmichaBusqueda;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.Map;

public final class RonaldmichaChip {

    private static final List<String> ESTILOS = List.of("success", "warning", "neutral", "info", "holiday", "error",
            "danger");

    private static final Map<String, String> POR_ESTADO = Map.ofEntries(
            Map.entry("abierto", "success"), Map.entry("hecho", "success"), Map.entry("recibido", "success"),
            Map.entry("pagado", "success"), Map.entry("vigente", "success"), Map.entry("activo", "success"),
            Map.entry("aprobado", "success"), Map.entry("gozado", "success"), Map.entry("completo", "success"),
            Map.entry("disponible", "success"),
            Map.entry("pendiente", "warning"), Map.entry("borrador", "warning"), Map.entry("incompleto", "warning"),
            Map.entry("cerrado", "neutral"), Map.entry("anulado", "neutral"), Map.entry("cesado", "neutral"),
            Map.entry("generado", "neutral"), Map.entry("futuro", "neutral"), Map.entry("inactivo", "neutral"),
            Map.entry("enviado", "info"), Map.entry("programado", "info"), Map.entry("cese", "info"),
            Map.entry("feriado", "holiday"), Map.entry("declarado", "holiday"),
            Map.entry("rechazado", "danger"), Map.entry("bloqueado", "danger"));

    private RonaldmichaChip() {
    }

    public static Label estado(String texto) {
        return de(texto, estilo(texto));
    }

    public static Label de(String texto, String estilo) {
        Label l = new Label(texto);
        l.getStyleClass().add("chip");
        if (estilo != null && !estilo.isBlank()) {
            l.getStyleClass().add(estilo);
        }
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    public static void actualizar(Label chip, String texto) {
        chip.setText(texto);
        chip.getStyleClass().removeAll(ESTILOS);
        if (!chip.getStyleClass().contains("chip")) {
            chip.getStyleClass().add("chip");
        }
        chip.getStyleClass().add(estilo(texto));
    }

    public static String estilo(String estado) {
        if (estado == null || estado.isBlank()) {
            return "neutral";
        }
        String primera = RonaldmichaBusqueda.normalizar(estado).trim().split("[\\s:·(]+")[0];
        return POR_ESTADO.getOrDefault(primera, "neutral");
    }
}
