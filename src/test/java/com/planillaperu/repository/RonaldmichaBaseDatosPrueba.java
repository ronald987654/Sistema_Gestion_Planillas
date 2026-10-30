package com.planillaperu.repository;

import java.nio.file.Path;

public final class RonaldmichaBaseDatosPrueba {

    private RonaldmichaBaseDatosPrueba() {
    }

    public static RonaldmichaDatabase crear(Path carpeta) {
        RonaldmichaDatabase db = new RonaldmichaDatabase(carpeta.resolve("prueba.db"));
        db.migrar();
        return db;
    }
}
