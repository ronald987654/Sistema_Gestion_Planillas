package com.planillaperu.domain;

import java.time.LocalDate;

public record RonaldmichaEmpresa(
        Long id,
        String ruc,
        String razonSocial,
        String nombreComercial,
        String regimenTributario,
        String ciiu,
        LocalDate inicioActividades,
        String direccion,
        String departamento,
        String provincia,
        String distrito,
        String ubigeo,
        String ciudad,
        RonaldmichaRegimenLaboral regimenLaboral,
        String registroRemype,
        String eps,
        String periodicidad,
        Integer diaPago,
        boolean sctr,
        String repNombre,
        String repTipoDoc,
        String repDoc,
        String repCargo,
        String partidaRegistral,
        String telefono,
        String correo,
        String correoBoletas,
        byte[] logo,
        byte[] firma,
        String rubro,
        boolean firmaDigitalActiva,
        boolean huellaEnBoleta) {

    public RonaldmichaEmpresa(Long id, String ruc, String razonSocial, String nombreComercial, String regimenTributario,
                   String ciiu, LocalDate inicioActividades, String direccion, String departamento, String provincia,
                   String distrito, String ubigeo, String ciudad, RonaldmichaRegimenLaboral regimenLaboral, String registroRemype,
                   String eps, String periodicidad, Integer diaPago, boolean sctr, String repNombre, String repTipoDoc,
                   String repDoc, String repCargo, String partidaRegistral, String telefono, String correo,
                   String correoBoletas, byte[] logo, byte[] firma) {
        this(id, ruc, razonSocial, nombreComercial, regimenTributario, ciiu, inicioActividades, direccion, departamento,
                provincia, distrito, ubigeo, ciudad, regimenLaboral, registroRemype, eps, periodicidad, diaPago, sctr,
                repNombre, repTipoDoc, repDoc, repCargo, partidaRegistral, telefono, correo, correoBoletas, logo, firma,
                null, false, true);
    }

    public static RonaldmichaEmpresa vacia() {
        return new RonaldmichaEmpresa(null, null, null, null, null, null, null, null, null, null, null, null, null,
                RonaldmichaRegimenLaboral.GENERAL, null, null, null, null, false, null, null, null, null, null,
                null, null, null, null, null);
    }

    public boolean requiereRemype() {
        return regimenLaboral == RonaldmichaRegimenLaboral.PEQUENA || regimenLaboral == RonaldmichaRegimenLaboral.MICRO;
    }
}
