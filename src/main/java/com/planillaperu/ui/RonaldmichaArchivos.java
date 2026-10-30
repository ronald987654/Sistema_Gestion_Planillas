package com.planillaperu.ui;

import com.planillaperu.ui.components.RonaldmichaToast;
import javafx.scene.Node;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class RonaldmichaArchivos {

    private static File ultimaCarpeta;

    private RonaldmichaArchivos() {
    }

    public static File guardar(Node nodo, String nombre, String descripcion, String extension, byte[] contenido) {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar " + nombre);
        selector.setInitialFileName(nombre);
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(descripcion, extension));
        if (ultimaCarpeta != null && ultimaCarpeta.isDirectory()) {
            selector.setInitialDirectory(ultimaCarpeta);
        }
        File destino = selector.showSaveDialog(nodo.getScene().getWindow());
        if (destino == null) {
            return null;
        }
        try {
            Files.write(destino.toPath(), contenido);
            ultimaCarpeta = destino.getParentFile();
            return destino;
        } catch (IOException e) {
            RonaldmichaToast.error(nodo, "No se pudo guardar el archivo: " + e.getMessage());
            return null;
        }
    }

    public static void generarYGuardar(Node nodo, String nombre, String descripcion, String extension,
                                       Callable<byte[]> generar) {
        generarYGuardar(nodo, nombre, descripcion, extension, generar, f -> { });
    }

    public static void generarYGuardar(Node nodo, String nombre, String descripcion, String extension,
                                       Callable<byte[]> generar, Consumer<File> alGuardar) {
        RonaldmichaTareas.ejecutar(generar, datos -> {
            File destino = guardar(nodo, nombre, descripcion, extension, datos);
            if (destino != null) {
                RonaldmichaToast.archivo(nodo, destino.toPath());
                alGuardar.accept(destino);
            }
        }, e -> RonaldmichaToast.error(nodo, "No se pudo generar el archivo: " + RonaldmichaTareas.mensaje(e)));
    }

    public static void abrirCarpeta(Node nodo, Path carpeta) {
        if (carpeta == null || !Desktop.isDesktopSupported()) {
            RonaldmichaToast.error(nodo, "No se puede abrir la carpeta en este equipo.");
            return;
        }
        RonaldmichaTareas.ejecutar(() -> {
            Desktop.getDesktop().open(carpeta.toFile());
            return carpeta;
        }, c -> {
        }, e -> RonaldmichaToast.error(nodo, "No se pudo abrir la carpeta."));
    }
}
