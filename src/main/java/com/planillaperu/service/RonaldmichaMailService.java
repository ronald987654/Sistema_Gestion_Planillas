package com.planillaperu.service;

public interface RonaldmichaMailService {

    record Correo(String para, String asunto, String cuerpo, String adjuntoNombre, byte[] adjunto) {
    }

    record Resultado(boolean enviado, String mensaje) {
    }

    boolean configurado();

    Resultado enviar(Correo correo);

    RonaldmichaMailService NO_CONFIGURADO = new RonaldmichaMailService() {
        @Override
        public boolean configurado() {
            return false;
        }

        @Override
        public Resultado enviar(Correo correo) {
            return new Resultado(false, "Correo no configurado: no se envió a " + correo.para() + ".");
        }
    };
}
