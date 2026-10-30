package com.planillaperu.service;

import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.domain.RonaldmichaTrabajador;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class RonaldmichaTextosDocumentos {

    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy",
            RonaldmichaFormatos.LOCALE_PE);

    private RonaldmichaTextosDocumentos() {
    }

    static String ciudadFecha(RonaldmichaEmpresa e, LocalDate fecha) {
        String ciudad = primero(e.ciudad(), e.provincia(), e.distrito(), e.departamento());
        return (ciudad == null ? "" : ciudad + ", ") + FECHA_LARGA.format(fecha);
    }

    static List<String> certificadoTrabajo(RonaldmichaEmpresa e, RonaldmichaTrabajador t, LocalDate cese) {
        return List.of(
                razon(e) + ", certifica que:",
                nombre(t) + ", con " + t.tipoDoc().codigo() + " N.° " + t.numDoc()
                        + ", ha laborado en nuestra empresa desde el " + RonaldmichaFormatos.fecha(t.fechaIngreso())
                        + " hasta el " + RonaldmichaFormatos.fecha(cese) + ", desempeñando el cargo de "
                        + valor(t.cargo(), "—") + " en el área de " + valor(t.area(), "—") + ".",
                "Se expide el presente certificado a solicitud del interesado, para los fines que estime conveniente.");
    }

    static List<String> constanciaTrabajo(RonaldmichaEmpresa e, RonaldmichaTrabajador t) {
        return List.of(
                razon(e) + ", hace constar que:",
                nombre(t) + ", con " + t.tipoDoc().codigo() + " N.° " + t.numDoc()
                        + ", labora en nuestra empresa desde el " + RonaldmichaFormatos.fecha(t.fechaIngreso())
                        + " a la fecha, desempeñando el cargo de " + valor(t.cargo(), "—") + " en el área de "
                        + valor(t.area(), "—") + ", con contrato " + valor(t.tipoContrato(), "—").toLowerCase(RonaldmichaFormatos.LOCALE_PE)
                        + ".",
                "Se expide la presente constancia a solicitud del interesado, para los fines que estime conveniente.");
    }

    static List<String> liberacionCts(RonaldmichaEmpresa e, RonaldmichaTrabajador t, LocalDate cese) {
        return List.of(
                "Señores:\n" + valor(t.bancoCts(), "Entidad depositaria de CTS") + "\nPresente.-",
                "Asunto: Liberación de la compensación por tiempo de servicios",
                "Por medio de la presente, " + razon(e) + " comunica que " + nombre(t) + ", con "
                        + t.tipoDoc().codigo() + " N.° " + t.numDoc() + ", titular de la cuenta CTS N.° "
                        + valor(t.ctaCts(), "—") + ", cesó en sus labores el " + RonaldmichaFormatos.fecha(cese)
                        + ", por lo que solicitamos se le permita disponer del íntegro de su depósito de CTS y sus "
                        + "intereses, conforme al D.S. 001-97-TR.",
                "Sin otro particular, quedamos de ustedes.");
    }

    private static String razon(RonaldmichaEmpresa e) {
        return valor(e.razonSocial(), "La empresa") + (e.ruc() == null || e.ruc().isBlank() ? "" : ", con RUC " + e.ruc());
    }

    private static String nombre(RonaldmichaTrabajador t) {
        return t.nombres() + " " + t.apellidos();
    }

    static String valor(String s, String porDefecto) {
        return s == null || s.isBlank() ? porDefecto : s;
    }

    static String primero(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
