package com.planillaperu.service;

import com.planillaperu.repository.RonaldmichaDatabase;

public class RonaldmichaDatosDemoService {

    private final RonaldmichaDatabase db;
    private final RonaldmichaAuditoriaService auditoria;

    public RonaldmichaDatosDemoService(RonaldmichaDatabase db, RonaldmichaAuditoriaService auditoria) {
        this.db = db;
        this.auditoria = auditoria;
    }

    public void restablecer(String usuario) {
        db.restablecer();
        auditoria.registrar(usuario, "DATOS_DEMO_RESTABLECIDOS",
                "Se borraron todos los datos y se cargaron de nuevo los datos de demostración");
    }
}
