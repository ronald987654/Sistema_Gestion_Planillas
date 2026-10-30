package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.domain.RonaldmichaRuc;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.export.RonaldmichaMembretePdf;
import com.planillaperu.repository.RonaldmichaEmpresaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RonaldmichaEmpresaService {

    public static final int MAX_IMAGEN_BYTES = 2 * 1024 * 1024;

    private final RonaldmichaEmpresaRepository repositorio;
    private final RonaldmichaAuditoriaService auditoria;

    public RonaldmichaEmpresaService(RonaldmichaEmpresaRepository repositorio, RonaldmichaAuditoriaService auditoria) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
    }

    public RonaldmichaEmpresa obtener() {
        return repositorio.obtener().orElse(RonaldmichaEmpresa.vacia());
    }

    public List<String> validar(RonaldmichaEmpresa e) {
        List<String> errores = new ArrayList<>();
        RonaldmichaRuc.error(e.ruc()).ifPresent(errores::add);
        if (vacio(e.razonSocial())) {
            errores.add("Ingrese la razón social.");
        }
        if (e.regimenLaboral() == null) {
            errores.add("Elija el régimen laboral.");
        } else if (e.requiereRemype() && vacio(e.registroRemype())) {
            errores.add("El N° REMYPE es obligatorio para " + e.regimenLaboral().descripcion().toLowerCase() + ".");
        }
        if (!vacio(e.ubigeo()) && !e.ubigeo().trim().matches("\\d{6}")) {
            errores.add("El ubigeo debe tener 6 dígitos.");
        }
        if (e.diaPago() != null && (e.diaPago() < 1 || e.diaPago() > 31)) {
            errores.add("El día de pago debe estar entre 1 y 31.");
        }
        if (tamano(e.logo()) > MAX_IMAGEN_BYTES || tamano(e.firma()) > MAX_IMAGEN_BYTES) {
            errores.add("El logo y la firma deben pesar como máximo 2 MB.");
        }
        return errores;
    }

    public List<String> faltantes(RonaldmichaEmpresa e) {
        List<String> faltan = new ArrayList<>();
        if (!RonaldmichaRuc.esValido(e.ruc())) faltan.add("RUC");
        if (vacio(e.razonSocial())) faltan.add("razón social");
        if (vacio(e.direccion())) faltan.add("dirección");
        if (vacio(e.distrito()) || vacio(e.provincia()) || vacio(e.departamento())) faltan.add("distrito, provincia y departamento");
        if (e.requiereRemype() && vacio(e.registroRemype())) faltan.add("N° REMYPE");
        if (vacio(e.repNombre()) || vacio(e.repDoc())) faltan.add("representante legal");
        if (tamano(e.logo()) == 0) faltan.add("logo");
        if (tamano(e.firma()) == 0) faltan.add("firma");
        return faltan;
    }

    public List<String> faltantes() {
        return faltantes(obtener());
    }

    public Optional<String> avisoDatosIncompletos() {
        return avisoDatosIncompletos(obtener());
    }

    public Optional<String> avisoDatosIncompletos(RonaldmichaEmpresa e) {
        List<String> faltan = faltantes(e);
        return faltan.isEmpty()
                ? Optional.empty()
                : Optional.of("Faltan los datos de la empresa: " + String.join(", ", faltan)
                        + ". Complételos en Configuración › Datos de la empresa.");
    }

    public byte[] vistaPreviaPdf(RonaldmichaEmpresa e) {
        return RonaldmichaMembretePdf.vistaPrevia(e, avisoDatosIncompletos(e));
    }

    public RonaldmichaEmpresa guardar(RonaldmichaEmpresa e, RonaldmichaUsuario usuario) {
        RonaldmichaPermisos.exigirAdmin(usuario, "modificar los datos de la empresa");
        RonaldmichaEmpresa limpia = normalizar(e);
        List<String> errores = validar(limpia);
        if (!errores.isEmpty()) {
            throw new RonaldmichaValidacionException(errores);
        }
        boolean nueva = limpia.id() == null;
        RonaldmichaEmpresa guardada = repositorio.guardar(limpia);
        List<String> faltan = faltantes(guardada);
        auditoria.registrar(usuario.usuario(), nueva ? "EMPRESA_REGISTRADA" : "EMPRESA_ACTUALIZADA",
                "RUC " + guardada.ruc() + " · " + guardada.razonSocial()
                        + (faltan.isEmpty() ? "" : " · Faltan: " + String.join(", ", faltan)));
        return guardada;
    }

    private static RonaldmichaEmpresa normalizar(RonaldmichaEmpresa e) {
        return new RonaldmichaEmpresa(e.id(), t(e.ruc()), t(e.razonSocial()), t(e.nombreComercial()), t(e.regimenTributario()),
                t(e.ciiu()), e.inicioActividades(), t(e.direccion()), t(e.departamento()), t(e.provincia()),
                t(e.distrito()), t(e.ubigeo()), t(e.ciudad()), e.regimenLaboral(),
                t(e.registroRemype()), t(e.eps()), t(e.periodicidad()),
                e.diaPago(), e.sctr(), t(e.repNombre()), t(e.repTipoDoc()), t(e.repDoc()), t(e.repCargo()),
                t(e.partidaRegistral()), t(e.telefono()), t(e.correo()), t(e.correoBoletas()),
                tamano(e.logo()) == 0 ? null : e.logo(), tamano(e.firma()) == 0 ? null : e.firma(),
                t(e.rubro()), e.firmaDigitalActiva(), e.huellaEnBoleta());
    }

    private static String t(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static int tamano(byte[] b) {
        return b == null ? 0 : b.length;
    }
}
