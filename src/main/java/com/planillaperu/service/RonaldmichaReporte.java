package com.planillaperu.service;

import java.util.List;
import java.util.Map;

public enum RonaldmichaReporte {
    PLANILLA("PLA", Grupo.PLANILLA, "Planilla de remuneraciones", "Resumen del periodo",
            "D.S. 001-98-TR", false, false, false, false, Formato.PDF, Formato.EXCEL),
    BOLETA_PAGO("BOL", Grupo.PLANILLA, "Boleta de pago", "Por trabajador",
            "D.S. 001-98-TR · entregar a más tardar el 3er día hábil siguiente al pago", true, true, true, true,
            Formato.PDF, Formato.WORD),
    PAGOS("PAG", Grupo.PLANILLA, "Pagos masivos", "Haberes por banco",
            "Abono de remuneraciones en cuenta de sueldo", false, false, false, false, Formato.TXT, Formato.EXCEL),
    PLAME("PLZ", Grupo.DECLARACIONES, "PLAME", "Tributos, jornada y no laborados",
            "PDT PLAME · SUNAT · archivos .rem, .jor y .snl para importar", false, false, false, false,
            Formato.ZIP, Formato.PDF),
    AFPNET("AFP", Grupo.DECLARACIONES, "AFPnet", "Aportes por AFP",
            "Sistema Privado de Pensiones · formato de carga AFPnet", false, false, false, false,
            Formato.EXCEL, Formato.PDF),
    GRATIFICACION("GRA", Grupo.BENEFICIOS, "Boleta de gratificación", "Julio / diciembre",
            "Ley 27735, D.S. 005-2002-TR y Ley 30334", true, true, true, true, Formato.PDF, Formato.WORD),
    CTS("CTS", Grupo.BENEFICIOS, "Constancia de CTS", "Depósito semestral",
            "D.S. 001-97-TR", true, true, true, true, Formato.PDF, Formato.WORD),
    SOLICITUD_VACACIONES("VAC", Grupo.BENEFICIOS, "Solicitud de vacaciones", "Último registro",
            "D.Leg. 713 y D.S. 012-92-TR", true, false, false, true, Formato.PDF, Formato.WORD),
    LIQUIDACION("LIQ", Grupo.BENEFICIOS, "Liquidación", "Beneficios sociales",
            "D.Leg. 713, Ley 27735, Ley 30334 y D.S. 001-97-TR", true, false, true, false, Formato.PDF, Formato.WORD),
    RENTA_QUINTA("R5T", Grupo.CONSTANCIAS, "Certificado de retenciones", "Renta de 5ta categoría",
            "Art. 45 del Reglamento de la Ley del Impuesto a la Renta", true, false, false, true,
            Formato.PDF, Formato.WORD),
    CERTIFICADO_TRABAJO("CTR", Grupo.CONSTANCIAS, "Certificado de trabajo", "Al cese",
            "Se entrega al término del vínculo laboral", true, false, true, false, Formato.PDF, Formato.WORD),
    CONSTANCIA_TRABAJO("CON", Grupo.CONSTANCIAS, "Constancia de trabajo", "Trabajador vigente",
            "A solicitud del trabajador", true, false, false, true, Formato.PDF, Formato.WORD),
    LIBERACION_CTS("LCT", Grupo.CONSTANCIAS, "Carta de liberación de CTS", "Al banco depositario",
            "D.S. 001-97-TR", true, false, false, false, Formato.PDF, Formato.WORD);

    private static final Map<String, RonaldmichaReporte> TIPOS_ANTERIORES = Map.of("PLR", PLAME);

    public enum Grupo {
        PLANILLA("Planilla y pagos"),
        DECLARACIONES("Declaraciones"),
        BENEFICIOS("Beneficios y cese"),
        CONSTANCIAS("Constancias y certificados");

        private final String titulo;

        Grupo(String titulo) {
            this.titulo = titulo;
        }

        public String titulo() {
            return titulo;
        }
    }

    public enum Formato {
        PDF("PDF", "pdf", "PDF (*.pdf)"),
        WORD("Word", "docx", "Word (*.docx)"),
        EXCEL("Excel", "xlsx", "Excel (*.xlsx)"),
        TXT("TXT", "txt", "Texto (*.txt)"),
        ZIP("ZIP", "zip", "ZIP (*.zip)");

        private final String etiqueta;
        private final String extension;
        private final String filtro;

        Formato(String etiqueta, String extension, String filtro) {
            this.etiqueta = etiqueta;
            this.extension = extension;
            this.filtro = filtro;
        }

        public String etiqueta() {
            return etiqueta;
        }

        public String extension() {
            return extension;
        }

        public String filtro() {
            return filtro;
        }

        public boolean validaAntes() {
            return this == EXCEL || this == TXT || this == ZIP;
        }
    }

    private final String tipo;
    private final Grupo grupo;
    private final String titulo;
    private final String descripcion;
    private final String baseLegal;
    private final boolean porTrabajador;
    private final boolean todosEnUnPdf;
    private final boolean requiereCargo;
    private final boolean paraTrabajador;
    private final List<Formato> formatos;

    RonaldmichaReporte(String tipo, Grupo grupo, String titulo, String descripcion, String baseLegal, boolean porTrabajador,
            boolean todosEnUnPdf, boolean requiereCargo, boolean paraTrabajador, Formato... formatos) {
        this.tipo = tipo;
        this.grupo = grupo;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.baseLegal = baseLegal;
        this.porTrabajador = porTrabajador;
        this.todosEnUnPdf = todosEnUnPdf;
        this.requiereCargo = requiereCargo;
        this.paraTrabajador = paraTrabajador;
        this.formatos = List.of(formatos);
    }

    public String tipo() {
        return tipo;
    }

    public Grupo grupo() {
        return grupo;
    }

    public String titulo() {
        return titulo;
    }

    public String descripcion() {
        return descripcion;
    }

    public String baseLegal() {
        return baseLegal;
    }

    public boolean porTrabajador() {
        return porTrabajador;
    }

    public boolean todosEnUnPdf() {
        return todosEnUnPdf;
    }

    public boolean requiereCargo() {
        return requiereCargo;
    }

    public boolean paraTrabajador() {
        return paraTrabajador;
    }

    public List<Formato> formatos() {
        return formatos;
    }

    public boolean incluyeCesados() {
        return this == LIBERACION_CTS || this == CERTIFICADO_TRABAJO;
    }

    public static RonaldmichaReporte desdeTipo(String tipo) {
        for (RonaldmichaReporte r : values()) {
            if (r.tipo.equals(tipo)) {
                return r;
            }
        }
        return TIPOS_ANTERIORES.get(tipo);
    }
}
