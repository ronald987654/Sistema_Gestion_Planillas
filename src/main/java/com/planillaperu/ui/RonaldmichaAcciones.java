package com.planillaperu.ui;

import com.planillaperu.common.RonaldmichaFormatos;

import java.util.Map;

public final class RonaldmichaAcciones {

    private static final Map<String, String> TEXTOS = Map.ofEntries(
            Map.entry("INICIO_SESION", "Inicio de sesión"),
            Map.entry("INICIO_SESION_FALLIDO", "Intento de inicio de sesión fallido"),
            Map.entry("INICIO_SESION_BLOQUEADO", "Inicio de sesión bloqueado"),
            Map.entry("CIERRE_SESION", "Cierre de sesión"),
            Map.entry("CIERRE_SESION_INACTIVIDAD", "Sesión cerrada por inactividad"),
            Map.entry("PLANILLA_CERRADA", "Planilla cerrada"),
            Map.entry("PLANILLA_REABIERTA", "Planilla reabierta"),
            Map.entry("PLANILLA_NOVEDADES_GUARDADAS", "Novedades de la planilla guardadas"),
            Map.entry("TRABAJADOR_REGISTRADO", "Trabajador registrado"),
            Map.entry("TRABAJADOR_ACTUALIZADO", "Trabajador actualizado"),
            Map.entry("TRABAJADORES_IMPORTADOS", "Trabajadores importados"),
            Map.entry("TRABAJADORES_EXPORTADOS", "Trabajadores exportados"),
            Map.entry("HORARIO_ACTUALIZADO", "Horario actualizado"),
            Map.entry("ASISTENCIA_EDITADA", "Asistencia editada"),
            Map.entry("ASISTENCIA_IMPORTADA", "Asistencia importada"),
            Map.entry("ASISTENCIA_LLENADA", "Asistencia llenada con el horario"),
            Map.entry("ASISTENCIA_BORRADA", "Marcaciones borradas"),
            Map.entry("FERIADO_AGREGADO", "Día no laborable agregado"),
            Map.entry("FERIADO_QUITADO", "Día no laborable quitado"),
            Map.entry("DOCUMENTO_EMITIDO", "Documento emitido"),
            Map.entry("BOLETA_DESCARGADA", "Boleta descargada"),
            Map.entry("CARGO_RECIBIDO", "Cargo de recepción registrado"),
            Map.entry("CARGO_ELECTRONICO", "Recepción confirmada en el portal"),
            Map.entry("ENVIO_CORREO", "Documentos enviados por correo"),
            Map.entry("PARAMETROS_REGISTRADOS", "Parámetros registrados"),
            Map.entry("PARAMETROS_ANULADOS", "Parámetros anulados"),
            Map.entry("EMPRESA_REGISTRADA", "Empresa registrada"),
            Map.entry("EMPRESA_ACTUALIZADA", "Datos de la empresa actualizados"),
            Map.entry("APROBACION_SOLICITADA", "Aprobación solicitada"),
            Map.entry("APROBACION_APROBADA", "Cambio aprobado"),
            Map.entry("APROBACION_RECHAZADA", "Cambio rechazado"),
            Map.entry("LIQUIDACION_GUARDADA", "Liquidación guardada"),
            Map.entry("LIQUIDACION_PAGADA", "Liquidación pagada"),
            Map.entry("CESE_REGISTRADO", "Cese registrado"),
            Map.entry("VACACION_EDITADA", "Vacaciones editadas"),
            Map.entry("VACACION_ANULADA", "Vacaciones anuladas"),
            Map.entry("USUARIO_CREADO", "Usuario creado"),
            Map.entry("CLAVE_CAMBIADA", "Contraseña cambiada"),
            Map.entry("CLAVE_RESTABLECIDA", "Contraseña restablecida"),
            Map.entry("DATOS_DEMO_RESTABLECIDOS", "Datos de demostración restablecidos"));

    private RonaldmichaAcciones() {
    }

    public static String texto(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return "";
        }
        String conocido = TEXTOS.get(codigo);
        if (conocido != null) {
            return conocido;
        }
        String s = codigo.replace('_', ' ').toLowerCase(RonaldmichaFormatos.LOCALE_PE);
        return s.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + s.substring(1);
    }
}
