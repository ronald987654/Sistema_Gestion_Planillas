package com.planillaperu.domain;

public record RonaldmichaTrabajadorResumen(
        long id,
        String codigo,
        String tipoDoc,
        String numDoc,
        String apPaterno,
        String apMaterno,
        String nombres,
        String cargo,
        String area,
        RonaldmichaRegimenLaboral regimen) {

    public String apellidos() {
        return apMaterno == null || apMaterno.isBlank() ? apPaterno : apPaterno + " " + apMaterno;
    }

    public String etiqueta() {
        return apellidos() + ", " + nombres + " · " + tipoDoc + " " + numDoc;
    }
}
