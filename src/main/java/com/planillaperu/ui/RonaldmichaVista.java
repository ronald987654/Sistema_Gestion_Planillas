package com.planillaperu.ui;

import com.planillaperu.domain.RonaldmichaPermiso;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.ui.components.RonaldmichaIcono;

import java.util.List;

public enum RonaldmichaVista {
    INICIO("Inicio", RonaldmichaIcono.INICIO, "inicio.fxml", RonaldmichaPermiso.VER_PLANILLAS),
    TRABAJADORES("Trabajadores", RonaldmichaIcono.TRABAJADORES, "trabajadores.fxml", RonaldmichaPermiso.VER_PLANILLAS),
    ASISTENCIA("Asistencia y horas extra", RonaldmichaIcono.RELOJ, "asistencia.fxml", RonaldmichaPermiso.VER_PLANILLAS),
    VACACIONES("Vacaciones", RonaldmichaIcono.SOL, "vacaciones.fxml", RonaldmichaPermiso.VER_PLANILLAS),
    PLANILLA_MENSUAL("Planilla mensual", RonaldmichaIcono.PLANILLA, "planilla.fxml", RonaldmichaPermiso.VER_PLANILLAS),
    RESUMEN_ANUAL("Resumen anual", RonaldmichaIcono.CALENDARIO, "resumen-anual.fxml", RonaldmichaPermiso.VER_PLANILLAS),
    REPORTES("Reportes", RonaldmichaIcono.REPORTES, "reportes.fxml", RonaldmichaPermiso.GENERAR_REPORTES, RonaldmichaPermiso.PORTAL_PROPIO),
    GRATIFICACIONES("Gratificaciones", RonaldmichaIcono.REGALO, "gratificaciones.fxml", RonaldmichaPermiso.VER_PLANILLAS),
    CTS("CTS", RonaldmichaIcono.BANCO, "cts.fxml", RonaldmichaPermiso.VER_PLANILLAS),
    LIQUIDACIONES("Liquidaciones", RonaldmichaIcono.LIQUIDACION, "liquidaciones.fxml", RonaldmichaPermiso.VER_PLANILLAS),
    EMPRESA("Datos de la empresa", RonaldmichaIcono.EMPRESA, "empresa.fxml", RonaldmichaPermiso.VER_PLANILLAS),
    PARAMETROS("Parámetros", RonaldmichaIcono.AJUSTES, "parametros.fxml", RonaldmichaPermiso.VER_PLANILLAS),
    USUARIOS("Usuarios y seguridad", RonaldmichaIcono.ESCUDO, "usuarios.fxml", RonaldmichaPermiso.VER_AUDITORIA),
    MIS_DOCUMENTOS("Mis documentos", RonaldmichaIcono.CARPETA, "mis-documentos.fxml", RonaldmichaPermiso.PORTAL_PROPIO),
    MI_ASISTENCIA("Mi asistencia", RonaldmichaIcono.RELOJ, "asistencia.fxml", RonaldmichaPermiso.PORTAL_PROPIO),
    MIS_VACACIONES("Mis vacaciones", RonaldmichaIcono.SOL, "vacaciones.fxml", RonaldmichaPermiso.PORTAL_PROPIO),
    MIS_DATOS("Mis datos", RonaldmichaIcono.USUARIO, "mis-datos.fxml", RonaldmichaPermiso.PORTAL_PROPIO);

    public record Grupo(String titulo, List<RonaldmichaVista> vistas) {
    }

    private static final List<Grupo> MENU_ADMIN = List.of(
            new Grupo(null, List.of(INICIO)),
            new Grupo("Personal", List.of(TRABAJADORES, ASISTENCIA, VACACIONES)),
            new Grupo("Planilla", List.of(PLANILLA_MENSUAL, RESUMEN_ANUAL, REPORTES)),
            new Grupo("Beneficios sociales", List.of(GRATIFICACIONES, CTS, LIQUIDACIONES)),
            new Grupo("Configuración", List.of(EMPRESA, PARAMETROS, USUARIOS)));

    private static final List<Grupo> MENU_TRABAJADOR = List.of(
            new Grupo(null, List.of(MIS_DOCUMENTOS, REPORTES, MI_ASISTENCIA, MIS_VACACIONES, MIS_DATOS)));

    private final String titulo;
    private final RonaldmichaIcono icono;
    private final String fxml;
    private final List<RonaldmichaPermiso> permisos;

    RonaldmichaVista(String titulo, RonaldmichaIcono icono, String fxml, RonaldmichaPermiso... permisos) {
        this.titulo = titulo;
        this.icono = icono;
        this.fxml = fxml;
        this.permisos = List.of(permisos);
    }

    public String titulo() {
        return titulo;
    }

    public RonaldmichaIcono icono() {
        return icono;
    }

    public String fxml() {
        return fxml;
    }

    public List<RonaldmichaPermiso> permisos() {
        return permisos;
    }

    public static List<Grupo> menu(RonaldmichaRol rol) {
        return rol == RonaldmichaRol.TRABAJADOR ? MENU_TRABAJADOR : MENU_ADMIN;
    }

    public boolean permitidaPara(RonaldmichaRol rol) {
        return menu(rol).stream().anyMatch(g -> g.vistas().contains(this));
    }
}
