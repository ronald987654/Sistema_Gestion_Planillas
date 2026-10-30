package com.planillaperu.export.doc;

import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.common.RonaldmichaNumeroALetras;
import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.export.RonaldmichaConstanciaBeneficio;
import com.planillaperu.export.RonaldmichaConstanciaBeneficio.Dato;
import com.planillaperu.export.RonaldmichaReporteLiquidacion;
import com.planillaperu.export.RonaldmichaReporteLiquidacion.Bloque;
import com.planillaperu.export.RonaldmichaReporteLiquidacion.Linea;
import com.planillaperu.export.RonaldmichaSolicitudVacaciones;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Firma;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Firmas;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Heading;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.KeyValue;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Orientacion;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Par;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Paragraph;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Table;
import com.planillaperu.export.doc.RonaldmichaDocumentModel.Total;

import java.util.ArrayList;
import java.util.List;

public final class RonaldmichaPlantillas {

    private RonaldmichaPlantillas() {
    }

    public static Firma firmaEmpleador(RonaldmichaEmpresa e) {
        String nombre = e.repNombre() == null || e.repNombre().isBlank() ? "Representante legal" : e.repNombre();
        return new Firma(nombre, e.repCargo() == null || e.repCargo().isBlank() ? "Empleador" : e.repCargo() + " · Empleador",
                true);
    }

    public static final String NUMERO_PREVIO = "se asigna al exportar";

    public static String subtitulo(String base, String numero) {
        String n = numero == null || NUMERO_PREVIO.equals(numero) ? "" : "N.° " + numero;
        if (base == null || base.isBlank()) {
            return n;
        }
        return n.isEmpty() ? base : base + " · " + n;
    }

    public static RonaldmichaDocumentModel constancia(RonaldmichaConstanciaBeneficio c, String numero, RonaldmichaEmpresa e) {
        List<RonaldmichaDocumentModel.Bloque> bloques = new ArrayList<>();
        bloques.add(new KeyValue(c.datos().stream().map(d -> new Par(d.etiqueta(), d.valor())).toList()));
        List<RonaldmichaConstanciaBeneficio.Linea> lineas = c.lineas();
        RonaldmichaConstanciaBeneficio.Linea ultima = lineas.isEmpty() ? null : lineas.getLast();
        List<List<String>> filas = lineas.stream().limit(Math.max(0, lineas.size() - 1))
                .map(l -> List.of(l.concepto(), RonaldmichaFormatos.numero(l.monto()))).toList();
        bloques.add(new Table(List.of("Concepto", "Importe (S/)"), filas, List.of(), List.of(false, true)));
        if (ultima != null) {
            bloques.add(new Total(ultima.concepto(), ultima.monto(), RonaldmichaNumeroALetras.texto(ultima.monto())));
        }
        if (c.nota() != null) {
            bloques.add(new Paragraph(c.nota()));
        }
        String trabajador = c.datos().stream().filter(d -> d.etiqueta().equals("Trabajador")).map(Dato::valor)
                .findFirst().orElse("Trabajador");
        bloques.add(new Firmas(List.of(firmaEmpleador(e), new Firma(trabajador, "Trabajador", false))));
        return new RonaldmichaDocumentModel(c.titulo(), subtitulo(c.periodo(), numero), RonaldmichaDocumentModel.DOS_COPIAS,
                Orientacion.VERTICAL, bloques, null);
    }

    public static RonaldmichaDocumentModel liquidacion(RonaldmichaReporteLiquidacion r, String numero, RonaldmichaEmpresa e) {
        List<RonaldmichaDocumentModel.Bloque> bloques = new ArrayList<>();
        bloques.add(new KeyValue(r.datos().stream().map(d -> new Par(d.etiqueta(), d.valor())).toList()));
        for (Bloque b : r.bloques()) {
            bloques.add(new Heading(b.titulo()));
            List<List<String>> filas = new ArrayList<>();
            for (Linea l : b.lineas()) {
                filas.add(List.of(l.concepto(), l.detalle() == null ? "" : l.detalle(), RonaldmichaFormatos.numero(l.monto())));
            }
            bloques.add(new Table(List.of("Concepto", "Detalle", "Importe (S/)"), filas,
                    List.of("Subtotal", "", RonaldmichaFormatos.numero(b.subtotal())), List.of(false, false, true)));
        }
        bloques.add(new KeyValue(List.of(new Par("Total bruto", RonaldmichaFormatos.moneda(r.bruto())),
                new Par("Total descuentos", RonaldmichaFormatos.moneda(r.descuentos())))));
        bloques.add(new Total("NETO A PAGAR", r.neto(), RonaldmichaNumeroALetras.texto(r.neto())));
        if (r.nota() != null) {
            bloques.add(new Paragraph(r.nota()));
        }
        bloques.add(new Firmas(List.of(firmaEmpleador(e), new Firma(r.firmaTrabajador(),
                r.documentoTrabajador() + " · Trabajador", false))));
        return new RonaldmichaDocumentModel(r.titulo(), subtitulo(r.subtitulo(), numero), List.of(), Orientacion.VERTICAL,
                bloques, null);
    }

    public static RonaldmichaDocumentModel solicitudVacaciones(RonaldmichaSolicitudVacaciones s, String numero) {
        List<RonaldmichaDocumentModel.Bloque> bloques = new ArrayList<>();
        bloques.add(new Paragraph(s.ciudadFecha()));
        bloques.add(new Paragraph("Señores:\n" + s.destinatario() + "\nPresente.-"));
        bloques.add(new Paragraph("Asunto: " + s.asunto()));
        s.parrafos().forEach(p -> bloques.add(new Paragraph(p)));
        bloques.add(new Table(List.of("Resumen", ""),
                s.resumen().stream().map(d -> List.of(d.etiqueta(), d.valor())).toList(), List.of(),
                List.of(false, true)));
        bloques.add(new Paragraph("Atentamente,"));
        bloques.add(new Firmas(List.of(
                new Firma(s.firmaTrabajador(), s.documentoTrabajador() + " · Trabajador", false),
                new Firma("V°B° " + s.firmaEmpleador(), s.cargoEmpleador() == null ? "Empleador"
                        : s.cargoEmpleador() + " · Empleador", true))));
        return new RonaldmichaDocumentModel(s.asunto().toUpperCase(RonaldmichaFormatos.LOCALE_PE), subtitulo(null, numero), List.of(),
                Orientacion.VERTICAL, bloques, null);
    }

    public static Firmas firmasReporte(RonaldmichaEmpresa e) {
        return new Firmas(List.of(new Firma("Responsable de planillas", "Elaborado por", false), firmaEmpleador(e)));
    }

    public static Firma recibiConforme(String nombre, String documento) {
        return new Firma(nombre, documento + " · Recibí conforme", false);
    }

    public static RonaldmichaDocumentModel carta(String titulo, String numero, List<String> parrafos, String ciudadFecha, RonaldmichaEmpresa e,
                                      Firma receptor) {
        List<RonaldmichaDocumentModel.Bloque> bloques = new ArrayList<>();
        parrafos.forEach(p -> bloques.add(new Paragraph(p)));
        bloques.add(new Paragraph(ciudadFecha));
        bloques.add(new Firmas(receptor == null ? List.of(firmaEmpleador(e)) : List.of(firmaEmpleador(e), receptor)));
        return new RonaldmichaDocumentModel(titulo, subtitulo(null, numero), List.of(), Orientacion.VERTICAL, bloques, null);
    }
}
