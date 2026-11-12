package com.planillaperu.service;

public interface RonaldmichaelMailService {

    record Correo(String para, String asunto, String cuerpo, String adjuntoNombre, byte[] adjunto) {
    }

    record Resultado(boolean enviado, String mensaje) {
    }

    boolean configurado();

    Resultado enviar(Correo correo);

    RonaldmichaelMailService NO_CONFIGURADO = new RonaldmichaelMailService() {
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
