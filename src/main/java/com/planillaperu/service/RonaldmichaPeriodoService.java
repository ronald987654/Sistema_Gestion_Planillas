package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaEstadoPeriodo;
import com.planillaperu.domain.RonaldmichaPeriodo;
import com.planillaperu.domain.RonaldmichaSituacionPeriodo;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;

import java.time.Clock;
import java.time.YearMonth;
import java.util.Optional;

public class RonaldmichaPeriodoService {

    private final RonaldmichaPeriodoRepository repositorio;
    private final Clock reloj;

    public RonaldmichaPeriodoService(RonaldmichaPeriodoRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    public RonaldmichaSituacionPeriodo situacion(YearMonth mes) {
        Optional<RonaldmichaPeriodo> registrado = repositorio.buscar(mes);
        if (registrado.isPresent()) {
            return registrado.get().estado() == RonaldmichaEstadoPeriodo.CERRADO ? RonaldmichaSituacionPeriodo.CERRADO
                    : RonaldmichaSituacionPeriodo.ABIERTO;
        }
        return mes.isAfter(ultimoMesAbierto()) ? RonaldmichaSituacionPeriodo.FUTURO : RonaldmichaSituacionPeriodo.ABIERTO;
    }

    public Optional<RonaldmichaPeriodo> buscar(YearMonth mes) {
        return repositorio.buscar(mes);
    }

    public YearMonth mesActual() {
        return YearMonth.now(reloj);
    }

    public YearMonth periodoDeTrabajo() {
        Optional<YearMonth> abierto = repositorio.primerAbierto();
        if (abierto.isPresent()) {
            return abierto.get();
        }
        return repositorio.ultimoCerrado().map(m -> m.plusMonths(1)).orElse(mesActual());
    }

    private YearMonth ultimoMesAbierto() {
        YearMonth actual = mesActual();
        return repositorio.ultimoRegistrado().filter(m -> m.isAfter(actual)).orElse(actual);
    }
}
