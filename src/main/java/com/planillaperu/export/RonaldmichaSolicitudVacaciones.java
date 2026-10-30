package com.planillaperu.export;

import com.planillaperu.export.RonaldmichaConstanciaBeneficio.Dato;

import java.util.List;

public record RonaldmichaSolicitudVacaciones(
        String ciudadFecha,
        String destinatario,
        String asunto,
        List<String> parrafos,
        List<Dato> resumen,
        String firmaTrabajador,
        String documentoTrabajador,
        String firmaEmpleador,
        String cargoEmpleador) {
}
