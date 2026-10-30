package com.planillaperu.service;

public interface RonaldmichaConsultaReniec {

    record DatosReniec(String apPaterno, String apMaterno, String nombres) {
    }

    class ServicioNoConectadoException extends RuntimeException {
        public ServicioNoConectadoException(String mensaje) {
            super(mensaje);
        }
    }

    DatosReniec consultar(String dni);

    RonaldmichaConsultaReniec NO_CONECTADA = dni -> {
        throw new ServicioNoConectadoException("RENIEC: servicio no conectado. Ingrese los datos manualmente.");
    };
}
