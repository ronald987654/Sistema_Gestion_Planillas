package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaDatosException;
import com.planillaperu.repository.RonaldmichaDatabase;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RonaldmichaRespaldoService {

    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");

    private final RonaldmichaDatabase db;
    private final RonaldmichaAuditoriaService auditoria;
    private final Clock reloj;

    public RonaldmichaRespaldoService(RonaldmichaDatabase db, RonaldmichaAuditoriaService auditoria, Clock reloj) {
        this.db = db;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public String nombreSugerido() {
        return "planilla_" + SELLO.format(LocalDateTime.now(reloj)) + ".db";
    }

    public void respaldar(RonaldmichaUsuario usuario, Path destino) {
        RonaldmichaPermisos.exigirAdmin(usuario, "respaldar la base de datos");
        if (destino.toAbsolutePath().normalize().equals(db.archivo().toAbsolutePath().normalize())) {
            throw new RonaldmichaValidacionException("Elija otro archivo: no se puede respaldar sobre la base en uso.");
        }
        ejecutar(() -> db.respaldar(destino));
        auditoria.registrar(usuario.usuario(), "BASE_RESPALDADA", destino.toAbsolutePath().toString());
    }

    public Path restaurar(RonaldmichaUsuario usuario, Path origen) {
        RonaldmichaPermisos.exigirAdmin(usuario, "restaurar la base de datos");
        if (origen.toAbsolutePath().normalize().equals(db.archivo().toAbsolutePath().normalize())) {
            throw new RonaldmichaValidacionException("Ese archivo es la base en uso: elija una copia de seguridad.");
        }
        String version = ejecutar(() -> RonaldmichaDatabase.validarRespaldo(origen));
        String actual = db.versionEsquema();
        if (comparar(version, actual) > 0) {
            throw new RonaldmichaValidacionException("La copia es de una versión más nueva del sistema (esquema " + version
                    + "; este programa usa el " + actual + "). Actualice Planilla Perú antes de restaurarla.");
        }
        Path previo = db.archivo().resolveSibling("planilla_antes_de_restaurar_"
                + SELLO.format(LocalDateTime.now(reloj)) + ".db");
        auditoria.registrar(usuario.usuario(), "BASE_RESTAURACION_INICIADA", "Desde " + origen.toAbsolutePath()
                + " · la base anterior se guarda en " + previo.getFileName());
        ejecutar(() -> db.restaurar(origen, previo));
        auditoria.registrar(usuario.usuario(), "BASE_RESTAURADA", "Desde " + origen.toAbsolutePath()
                + " (esquema " + version + ") · base anterior: " + previo.toAbsolutePath());
        return previo;
    }

    static int comparar(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? entero(x[i]) : 0;
            int q = i < y.length ? entero(y[i]) : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return 0;
    }

    private static int entero(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static <T> T ejecutar(java.util.function.Supplier<T> accion) {
        try {
            return accion.get();
        } catch (RonaldmichaDatosException e) {
            throw new RonaldmichaValidacionException(e.getMessage());
        }
    }

    private static void ejecutar(Runnable accion) {
        ejecutar(() -> {
            accion.run();
            return null;
        });
    }
}
