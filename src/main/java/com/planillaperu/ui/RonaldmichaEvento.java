package com.planillaperu.ui;

public record RonaldmichaEvento(Tema tema, String accion, long numero) {

    public enum Tema {
        TRABAJADORES, ASISTENCIA, PLANILLA, PERIODO, BENEFICIOS, VACACIONES, LIQUIDACIONES, EMPRESA, PARAMETROS,
        SEGURIDAD, DOCUMENTOS, OTRO
    }

    public static Tema tema(String accion) {
        if (accion == null) {
            return Tema.OTRO;
        }
        if (accion.equals("PLANILLA_CERRADA") || accion.equals("PLANILLA_REABIERTA")) {
            return Tema.PERIODO;
        }
        if (accion.startsWith("PLANILLA_") || accion.startsWith("RENTA_")) {
            return Tema.PLANILLA;
        }
        if (accion.startsWith("TRABAJADOR") || accion.startsWith("HORARIO_") || accion.startsWith("CESE_")
                || accion.startsWith("DATOS_CONTACTO") || accion.startsWith("CAMBIO_SUELDO")) {
            return Tema.TRABAJADORES;
        }
        if (accion.startsWith("ASISTENCIA_") || accion.startsWith("FERIADO_")) {
            return Tema.ASISTENCIA;
        }
        if (accion.startsWith("AJUSTES_BENEFICIO") || accion.startsWith("BENEFICIO_")) {
            return Tema.BENEFICIOS;
        }
        if (accion.startsWith("VACACION")) {
            return Tema.VACACIONES;
        }
        if (accion.startsWith("LIQUIDACION")) {
            return Tema.LIQUIDACIONES;
        }
        if (accion.startsWith("EMPRESA")) {
            return Tema.EMPRESA;
        }
        if (accion.startsWith("PARAMETROS")) {
            return Tema.PARAMETROS;
        }
        if (accion.startsWith("DOCUMENTO") || accion.startsWith("BOLETA_") || accion.startsWith("CARGO_")
                || accion.startsWith("ENVIO_") || accion.startsWith("CERTIFICADO")) {
            return Tema.DOCUMENTOS;
        }
        if (accion.startsWith("USUARIO") || accion.startsWith("APROBACION") || accion.startsWith("CLAVE")
                || accion.startsWith("CONFIGURACION")) {
            return Tema.SEGURIDAD;
        }
        return Tema.OTRO;
    }

    public boolean es(Tema... temas) {
        for (Tema t : temas) {
            if (t == tema) {
                return true;
            }
        }
        return false;
    }
}
