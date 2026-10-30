package com.planillaperu.domain;

import com.planillaperu.common.RonaldmichaBusqueda;

public enum RonaldmichaNovedad {
    NORMAL("", "Normal", true),
    VACACIONES("vac", "Vacaciones", true),
    LICENCIA_CON_GOCE("lic", "Licencia con goce", true),
    DESCANSO_MEDICO("dm", "Descanso médico", true),
    LICENCIA_SIN_GOCE("sg", "Licencia sin goce", false),
    DESCANSO_SUSTITUTORIO("sust", "Descanso sustitutorio", true);

    private final String codigo;
    private final String descripcion;
    private final boolean pagado;

    RonaldmichaNovedad(String codigo, String descripcion, boolean pagado) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.pagado = pagado;
    }

    public String codigo() {
        return codigo;
    }

    public String descripcion() {
        return descripcion;
    }

    public boolean pagado() {
        return pagado;
    }

    public static RonaldmichaNovedad desdeCodigo(String valor) {
        String v = valor == null ? "" : valor.trim();
        for (RonaldmichaNovedad n : values()) {
            if (n.codigo.equalsIgnoreCase(v)) {
                return n;
            }
        }
        throw new IllegalArgumentException("Novedad desconocida: " + valor);
    }

    public static RonaldmichaNovedad desdeTexto(String valor) {
        String v = RonaldmichaBusqueda.normalizar(valor);
        for (RonaldmichaNovedad n : values()) {
            if (v.equals(n.codigo) || v.equals(RonaldmichaBusqueda.normalizar(n.descripcion))) {
                return n;
            }
        }
        throw new IllegalArgumentException("Novedad desconocida: " + valor);
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
