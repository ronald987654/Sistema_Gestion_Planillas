package com.planillaperu.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public record RonaldmichaParametrosVersion(
        Long id,
        YearMonth vigenteDesde,
        String norma,
        BigDecimal rmv,
        BigDecimal uit,
        BigDecimal asigFamiliarPct,
        BigDecimal essaludPct,
        BigDecimal epsPct,
        BigDecimal onpPct,
        BigDecimal afpAportePct,
        BigDecimal afpPrimaPct,
        BigDecimal rmaPrima,
        BigDecimal bonifExtPct,
        BigDecimal bonifExtEpsPct,
        BigDecimal vidaLeyPct,
        BigDecimal sisMonto,
        RonaldmichaEstadoParametros estado,
        String registradoPor,
        LocalDateTime fechaRegistro,
        List<RonaldmichaAfpComision> comisiones,
        String motivoAnulacion,
        String anuladoPor,
        LocalDateTime fechaAnulacion) {

    public static final List<String> AFPS = List.of("AFP Habitat", "AFP Integra", "AFP Prima", "AFP Profuturo");

    public RonaldmichaParametrosVersion {
        comisiones = List.copyOf(comisiones);
    }

    public Optional<RonaldmichaAfpComision> comision(String afp) {
        return comisiones.stream().filter(c -> c.afp().equals(afp)).findFirst();
    }

    public boolean vigente() {
        return estado == RonaldmichaEstadoParametros.VIGENTE;
    }
}
