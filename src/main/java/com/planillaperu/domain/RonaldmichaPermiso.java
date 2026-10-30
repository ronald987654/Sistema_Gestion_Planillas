package com.planillaperu.domain;

public enum RonaldmichaPermiso {
    VER_PLANILLAS("Consultar", "Ver trabajadores, asistencia, planillas, beneficios, empresa y parámetros"),
    EDITAR_TRABAJADORES("Personal", "Registrar y modificar trabajadores (sueldo y cuentas pasan por aprobación)"),
    REGISTRAR_ASISTENCIA("Personal", "Registrar asistencia, horarios, novedades y vacaciones"),
    EDITAR_PLANILLA("Planilla", "Calcular y ajustar la planilla, gratificaciones, CTS y liquidaciones"),
    CERRAR_PERIODO("Planilla", "Cerrar y reabrir periodos"),
    GENERAR_REPORTES("Reportes", "Generar reportes y documentos de toda la empresa"),
    CONFIGURAR_EMPRESA("Configuración", "Modificar los datos de la empresa y los parámetros legales"),
    APROBAR_CAMBIOS("Aprobaciones", "Aprobar o rechazar cambios de sueldo y cuentas bancarias de otros"),
    RESOLVER_VACACIONES("Aprobaciones", "Aprobar o rechazar solicitudes de vacaciones de otros"),
    GESTIONAR_USUARIOS("Seguridad", "Crear y desactivar usuarios, restablecer contraseñas y ajustar la sesión"),
    VER_AUDITORIA("Seguridad", "Ver la seguridad, la cola de aprobaciones y la auditoría; exportarla a Excel"),
    PORTAL_PROPIO("Portal", "Ver solo su información: boletas, asistencia, vacaciones y datos personales");

    private final String grupo;
    private final String descripcion;

    RonaldmichaPermiso(String grupo, String descripcion) {
        this.grupo = grupo;
        this.descripcion = descripcion;
    }

    public String grupo() {
        return grupo;
    }

    public String descripcion() {
        return descripcion;
    }
}
