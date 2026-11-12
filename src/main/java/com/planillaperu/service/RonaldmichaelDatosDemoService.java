package com.planillaperu.service;

import com.planillaperu.repository.RonaldmichaDatabase;

public class RonaldmichaelDatosDemoService {

    private final RonaldmichaDatabase db;
    private final RonaldmichaelAuditoriaService auditoria;

    public RonaldmichaelDatosDemoService(RonaldmichaDatabase db, RonaldmichaelAuditoriaService auditoria) {
        this.db = db;
        this.auditoria = auditoria;
    }

    public void restablecer(String usuario) {
        db.restablecer();
        auditoria.registrar(usuario, "DATOS_DEMO_RESTABLECIDOS",
                "Se borraron todos los datos y se cargaron de nuevo los datos de demostración");
    }
}
