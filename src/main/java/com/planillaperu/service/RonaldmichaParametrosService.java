package com.planillaperu.service;

import com.planillaperu.calc.RonaldmichaImpactoParametrosCalculator;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaAfpComision;
import com.planillaperu.domain.RonaldmichaEstadoParametros;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaParametrosRepository;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RonaldmichaParametrosService {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final RonaldmichaParametrosRepository repositorio;
    private final RonaldmichaPeriodoRepository periodos;
    private final RonaldmichaTrabajadorRepository trabajadores;
    private final RonaldmichaAuditoriaService auditoria;
    private final Clock reloj;

    public RonaldmichaParametrosService(RonaldmichaParametrosRepository repositorio, RonaldmichaPeriodoRepository periodos,
                             RonaldmichaTrabajadorRepository trabajadores, RonaldmichaAuditoriaService auditoria, Clock reloj) {
        this.repositorio = repositorio;
        this.periodos = periodos;
        this.trabajadores = trabajadores;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public RonaldmichaParametrosVersion vigentes(YearMonth periodo) {
        return repositorio.buscarVigente(periodo)
                .orElseThrow(() -> new RonaldmichaParametroNoEncontradoException(
                        "No hay parámetros vigentes para el periodo " + MES.format(periodo)
                                + ". Regístrelos en Parámetros."));
    }

    public List<RonaldmichaParametrosVersion> listar() {
        return repositorio.listar();
    }

    public List<String> validar(RonaldmichaParametrosVersion v) {
        List<String> errores = new ArrayList<>();
        if (v.vigenteDesde() == null) {
            errores.add("Indique el mes desde el que rige la versión.");
        }
        Map<String, BigDecimal> montos = new LinkedHashMap<>();
        montos.put("RMV", v.rmv());
        montos.put("UIT", v.uit());
        montos.put("Remuneración máxima asegurable", v.rmaPrima());
        montos.forEach((nombre, valor) -> {
            if (valor == null || valor.signum() <= 0) {
                errores.add(nombre + ": ingrese un monto mayor que cero.");
            }
        });
        if (v.sisMonto() == null || v.sisMonto().signum() < 0) {
            errores.add("SIS: ingrese un monto (0 o más).");
        }
        Map<String, BigDecimal> tasas = new LinkedHashMap<>();
        tasas.put("Asignación familiar", v.asigFamiliarPct());
        tasas.put("EsSalud", v.essaludPct());
        tasas.put("EPS", v.epsPct());
        tasas.put("ONP", v.onpPct());
        tasas.put("Aporte AFP", v.afpAportePct());
        tasas.put("Prima de seguros AFP", v.afpPrimaPct());
        tasas.put("Bonificación extraordinaria", v.bonifExtPct());
        tasas.put("Bonificación extraordinaria con EPS", v.bonifExtEpsPct());
        tasas.put("Vida Ley", v.vidaLeyPct());
        tasas.forEach((nombre, valor) -> {
            if (!porcentajeValido(valor)) {
                errores.add(nombre + ": ingrese un porcentaje entre 0 y 100.");
            }
        });
        Set<String> afps = v.comisiones().stream().map(RonaldmichaAfpComision::afp).collect(Collectors.toSet());
        if (!afps.equals(Set.copyOf(RonaldmichaParametrosVersion.AFPS)) || v.comisiones().size() != RonaldmichaParametrosVersion.AFPS.size()) {
            errores.add("Registre las comisiones de las cuatro AFP.");
        }
        for (RonaldmichaAfpComision c : v.comisiones()) {
            if (!porcentajeValido(c.comisionFlujoPct()) || !porcentajeValido(c.comisionMixtaPct())) {
                errores.add(c.afp() + ": las comisiones deben estar entre 0 y 100 %.");
            }
        }
        if (v.vigenteDesde() != null) {
            if (repositorio.existeVigenteDesde(v.vigenteDesde())) {
                errores.add("Ya existe una versión vigente desde " + MES.format(v.vigenteDesde())
                        + ". Anúlela antes de registrar otra para ese mes.");
            }
            periodos.primerCerradoDesde(v.vigenteDesde()).ifPresent(cerrado -> errores.add(
                    "El periodo " + MES.format(cerrado) + " está cerrado: no se pueden cambiar sus parámetros."));
        }
        return errores;
    }

    public RonaldmichaImpactoParametrosCalculator.Impacto impacto(RonaldmichaParametrosVersion nueva) {
        RonaldmichaParametrosVersion anterior = repositorio.buscarVigente(nueva.vigenteDesde()).orElse(null);
        return RonaldmichaImpactoParametrosCalculator.calcular(anterior, nueva,
                trabajadores.listarRemuneraciones(LocalDate.now(reloj)));
    }

    public RonaldmichaParametrosVersion registrar(RonaldmichaParametrosVersion borrador, RonaldmichaUsuario usuario) {
        RonaldmichaPermisos.exigirAdmin(usuario, "registrar parámetros");
        List<String> errores = validar(borrador);
        if (!errores.isEmpty()) {
            throw new RonaldmichaValidacionException(errores);
        }
        RonaldmichaParametrosVersion nueva = new RonaldmichaParametrosVersion(null, borrador.vigenteDesde(), texto(borrador.norma()),
                borrador.rmv(), borrador.uit(), borrador.asigFamiliarPct(), borrador.essaludPct(), borrador.epsPct(),
                borrador.onpPct(), borrador.afpAportePct(), borrador.afpPrimaPct(), borrador.rmaPrima(),
                borrador.bonifExtPct(), borrador.bonifExtEpsPct(), borrador.vidaLeyPct(), borrador.sisMonto(),
                RonaldmichaEstadoParametros.VIGENTE, usuario.usuario(), ahora(), borrador.comisiones(), null, null, null);
        RonaldmichaParametrosVersion guardada = repositorio.insertar(nueva);
        auditoria.registrar(usuario.usuario(), "PARAMETROS_REGISTRADOS",
                "Vigente desde " + MES.format(guardada.vigenteDesde())
                        + " · RMV " + RonaldmichaFormatos.moneda(guardada.rmv())
                        + " · UIT " + RonaldmichaFormatos.moneda(guardada.uit())
                        + (guardada.norma() == null ? "" : " · " + guardada.norma()));
        return guardada;
    }

    public void anular(long id, String motivo, RonaldmichaUsuario usuario) {
        RonaldmichaPermisos.exigirAdmin(usuario, "anular parámetros");
        if (motivo == null || motivo.isBlank()) {
            throw new RonaldmichaValidacionException("Indique el motivo de la anulación.");
        }
        RonaldmichaParametrosVersion v = repositorio.buscarPorId(id)
                .orElseThrow(() -> new RonaldmichaValidacionException("La versión de parámetros no existe."));
        if (!v.vigente()) {
            throw new RonaldmichaValidacionException("La versión ya está anulada.");
        }
        periodos.primerCerradoDesde(v.vigenteDesde()).ifPresent(cerrado -> {
            throw new RonaldmichaValidacionException("El periodo " + MES.format(cerrado)
                    + " está cerrado y usa esta versión: no se puede anular.");
        });
        repositorio.anular(id, motivo.trim(), usuario.usuario(), ahora());
        auditoria.registrar(usuario.usuario(), "PARAMETROS_ANULADOS",
                "Versión vigente desde " + MES.format(v.vigenteDesde()) + " anulada. Motivo: " + motivo.trim());
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
    }

    private static boolean porcentajeValido(BigDecimal valor) {
        return valor != null && valor.signum() >= 0 && valor.compareTo(CIEN) <= 0;
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
