package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.Objects;

public final class RonaldmichaRecursos {

    private static final Logger log = LoggerFactory.getLogger(RonaldmichaRecursos.class);
    private static final String FXML = "/com/planillaperu/fxml/";
    public static final String TEMA = "/css/theme.css";
    private static final String[] FUENTES = {
            "IBMPlexSans-Regular.ttf", "IBMPlexSans-Medium.ttf", "IBMPlexSans-SemiBold.ttf",
            "IBMPlexSans-Bold.ttf", "IBMPlexMono-Regular.ttf", "IBMPlexMono-Medium.ttf"
    };

    private RonaldmichaRecursos() {
    }

    public static URL url(String ruta) {
        return Objects.requireNonNull(RonaldmichaRecursos.class.getResource(ruta), "Recurso no encontrado: " + ruta);
    }

    public static void cargarFuentes() {
        for (String archivo : FUENTES) {
            try (InputStream in = RonaldmichaRecursos.class.getResourceAsStream("/fonts/" + archivo)) {
                if (in == null || Font.loadFont(in, 13) == null) {
                    log.warn("No se pudo cargar la fuente {}", archivo);
                }
            } catch (IOException e) {
                log.warn("No se pudo cargar la fuente {}", archivo, e);
            }
        }
    }

    public static Parent cargarVista(String nombre, RonaldmichaAppContext contexto) {
        return cargar(nombre, contexto).getRoot();
    }

    public static FXMLLoader cargar(String nombre, RonaldmichaAppContext contexto) {
        FXMLLoader loader = new FXMLLoader(url(FXML + nombre));
        loader.setControllerFactory(tipo -> crearControlador(tipo, contexto));
        try {
            loader.load();
            return loader;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la vista " + nombre, e);
        }
    }

    private static Object crearControlador(Class<?> tipo, RonaldmichaAppContext contexto) {
        try {
            for (Constructor<?> c : tipo.getConstructors()) {
                if (c.getParameterCount() == 1 && c.getParameterTypes()[0] == RonaldmichaAppContext.class) {
                    return c.newInstance(contexto);
                }
            }
            return tipo.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo crear el controlador " + tipo.getName(), e);
        }
    }
}
