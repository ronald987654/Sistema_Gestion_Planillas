package com.planillaperu.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RonaldmichaTrabajador(
        Long id,
        String codigo,
        RonaldmichaTipoDocumento tipoDoc,
        String numDoc,
        String apPaterno,
        String apMaterno,
        String nombres,
        String celular,
        String correo,
        LocalDate fechaIngreso,
        LocalDate fechaCese,
        String area,
        String cargo,
        String tipoContrato,
        RonaldmichaRegimenLaboral regimen,
        String centroCosto,
        String sucursal,
        BigDecimal sueldo,
        BigDecimal movilidad,
        RonaldmichaSalud salud,
        RonaldmichaSistemaPension sistemaPension,
        String cuspp,
        RonaldmichaTipoComision tipoComision,
        boolean asigFamiliar,
        boolean vidaLey,
        RonaldmichaTipoPago tipoPago,
        String bancoSueldo,
        String ctaSueldo,
        String bancoCts,
        String ctaCts,
        LocalDate fechaNacimiento,
        int hijos,
        String direccion,
        RonaldmichaCategoria categoria) {

    public RonaldmichaTrabajador {
        categoria = categoria == null ? RonaldmichaCategoria.EMPLEADO : categoria;
    }

    public RonaldmichaTrabajador(Long id, String codigo, RonaldmichaTipoDocumento tipoDoc, String numDoc, String apPaterno, String apMaterno,
                      String nombres, String celular, String correo, LocalDate fechaIngreso, LocalDate fechaCese,
                      String area, String cargo, String tipoContrato, RonaldmichaRegimenLaboral regimen, String centroCosto,
                      String sucursal, BigDecimal sueldo, BigDecimal movilidad, RonaldmichaSalud salud,
                      RonaldmichaSistemaPension sistemaPension, String cuspp, RonaldmichaTipoComision tipoComision, boolean asigFamiliar,
                      boolean vidaLey, RonaldmichaTipoPago tipoPago, String bancoSueldo, String ctaSueldo, String bancoCts,
                      String ctaCts) {
        this(id, codigo, tipoDoc, numDoc, apPaterno, apMaterno, nombres, celular, correo, fechaIngreso, fechaCese,
                area, cargo, tipoContrato, regimen, centroCosto, sucursal, sueldo, movilidad, salud, sistemaPension,
                cuspp, tipoComision, asigFamiliar, vidaLey, tipoPago, bancoSueldo, ctaSueldo, bancoCts, ctaCts,
                null, 0, null, RonaldmichaCategoria.EMPLEADO);
    }

    public RonaldmichaTrabajador conDatosPersonales(LocalDate nacimiento, int numeroHijos, String domicilio, RonaldmichaCategoria cat) {
        return new RonaldmichaTrabajador(id, codigo, tipoDoc, numDoc, apPaterno, apMaterno, nombres, celular, correo,
                fechaIngreso, fechaCese, area, cargo, tipoContrato, regimen, centroCosto, sucursal,
                sueldo, movilidad, salud, sistemaPension, cuspp, tipoComision, asigFamiliar, vidaLey, tipoPago,
                bancoSueldo, ctaSueldo, bancoCts, ctaCts, nacimiento, numeroHijos, domicilio, cat);
    }

    public String apellidos() {
        return apMaterno == null || apMaterno.isBlank() ? apPaterno : apPaterno + " " + apMaterno;
    }

    public String nombreCompleto() {
        return apellidos() + ", " + nombres;
    }

    public boolean activoEn(LocalDate fecha) {
        return fechaCese == null || !fechaCese.isBefore(fecha);
    }

    public RonaldmichaTrabajadorResumen resumen() {
        return new RonaldmichaTrabajadorResumen(id == null ? 0 : id, codigo, tipoDoc.codigo(), numDoc, apPaterno, apMaterno,
                nombres, cargo, area, regimen);
    }

    public RonaldmichaTrabajador conDatosSensiblesDe(RonaldmichaTrabajador origen) {
        return new RonaldmichaTrabajador(id, codigo, tipoDoc, numDoc, apPaterno, apMaterno, nombres, celular, correo,
                fechaIngreso, fechaCese, area, cargo, tipoContrato, regimen, centroCosto, sucursal,
                origen.sueldo, movilidad, salud, sistemaPension, cuspp, tipoComision, asigFamiliar, vidaLey, tipoPago,
                origen.bancoSueldo, origen.ctaSueldo, origen.bancoCts, origen.ctaCts, fechaNacimiento, hijos, direccion,
                categoria);
    }

    public RonaldmichaTrabajador conIdYCodigo(Long nuevoId, String nuevoCodigo) {
        return new RonaldmichaTrabajador(nuevoId, nuevoCodigo, tipoDoc, numDoc, apPaterno, apMaterno, nombres, celular, correo,
                fechaIngreso, fechaCese, area, cargo, tipoContrato, regimen, centroCosto, sucursal,
                sueldo, movilidad, salud, sistemaPension, cuspp, tipoComision, asigFamiliar, vidaLey, tipoPago,
                bancoSueldo, ctaSueldo, bancoCts, ctaCts, fechaNacimiento, hijos, direccion, categoria);
    }
}
