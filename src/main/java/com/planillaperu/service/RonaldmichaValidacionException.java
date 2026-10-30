package com.planillaperu.service;

import java.util.List;

public class RonaldmichaValidacionException extends RuntimeException {

    private final List<String> errores;

    public RonaldmichaValidacionException(List<String> errores) {
        super(String.join(" ", errores));
        this.errores = List.copyOf(errores);
    }

    public RonaldmichaValidacionException(String error) {
        this(List.of(error));
    }

    public List<String> errores() {
        return errores;
    }
}
