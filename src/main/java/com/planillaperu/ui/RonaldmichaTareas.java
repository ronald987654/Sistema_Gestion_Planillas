package com.planillaperu.ui;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class RonaldmichaTareas {

    private static final Logger log = LoggerFactory.getLogger(RonaldmichaTareas.class);
    private static final AtomicInteger NUMERO = new AtomicInteger();
    private static final AtomicInteger EN_CURSO = new AtomicInteger();
    private static final ExecutorService HILOS = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "planilla-tarea-" + NUMERO.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    private RonaldmichaTareas() {
    }

    public static <T> void ejecutar(Callable<T> trabajo, Consumer<T> alTerminar, Consumer<Throwable> alFallar) {
        EN_CURSO.incrementAndGet();
        HILOS.execute(() -> {
            try {
                T resultado = trabajo.call();
                Platform.runLater(() -> alTerminar.accept(resultado));
            } catch (Exception | Error e) {
                log.warn("Tarea en segundo plano fallida", e);
                Throwable causa = e;
                Platform.runLater(() -> alFallar.accept(causa));
            } finally {
                EN_CURSO.decrementAndGet();
            }
        });
    }

    public static boolean esperar(Duration limite) {
        long fin = System.nanoTime() + limite.toNanos();
        while (EN_CURSO.get() > 0) {
            if (System.nanoTime() > fin) {
                return false;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    public static String mensaje(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && (t.getMessage() == null || t.getMessage().isBlank())) {
            t = t.getCause();
        }
        return t.getMessage() == null || t.getMessage().isBlank() ? "Ocurrió un error inesperado." : t.getMessage();
    }
}
