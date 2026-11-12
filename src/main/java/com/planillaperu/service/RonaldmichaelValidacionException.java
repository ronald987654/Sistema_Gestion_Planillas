package com.planillaperu.service;

import java.util.List;

public class RonaldmichaelValidacionException extends RuntimeException {

    private final List<String> errores;

    public RonaldmichaelValidacionException(List<String> errores) {
        super(String.join(" ", errores));
        this.errores = List.copyOf(errores);
    }

    public RonaldmichaelValidacionException(String error) {
        this(List.of(error));
    }

    public List<String> errores() {
        return errores;
    }
}
