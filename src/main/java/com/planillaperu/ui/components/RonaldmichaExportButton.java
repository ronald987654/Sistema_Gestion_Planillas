package com.planillaperu.ui.components;

import com.planillaperu.ui.RonaldmichaArchivos;
import com.planillaperu.ui.RonaldmichaTareas;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Region;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class RonaldmichaExportButton extends Button {

    public enum Formato {
        PDF("btn-pdf", "PDF (*.pdf)", "*.pdf"),
        WORD("btn-word", "Word (*.docx)", "*.docx"),
        EXCEL("btn-excel", "Excel (*.xlsx)", "*.xlsx"),
        CSV("btn-excel", "CSV (*.csv)", "*.csv"),
        TXT("btn-txt", "Texto (*.txt)", "*.txt"),
        ZIP("btn-zip", "ZIP (*.zip)", "*.zip");

        private final String estilo;
        private final String descripcion;
        private final String extension;

        Formato(String estilo, String descripcion, String extension) {
            this.estilo = estilo;
            this.descripcion = descripcion;
            this.extension = extension;
        }

        public String estilo() {
            return estilo;
        }

        public static Formato de(String nombreArchivo) {
            String n = nombreArchivo == null ? "" : nombreArchivo.toLowerCase(java.util.Locale.ROOT);
            for (Formato f : values()) {
                if (n.endsWith(f.extension.substring(1))) {
                    return f;
                }
            }
            return PDF;
        }
    }

    public record Archivo(String nombre, byte[] contenido) {
    }

    private final Formato formato;
    private final String texto;
    private Callable<Archivo> generador;
    private Consumer<Runnable> previo = Runnable::run;
    private Consumer<File> alGuardar = f -> { };
    private Consumer<Archivo> alCancelar = a -> { };
    private boolean generando;

    public RonaldmichaExportButton(String texto, Formato formato, Callable<Archivo> generador) {
        super(texto);
        this.texto = texto;
        this.formato = formato;
        this.generador = generador;
        getStyleClass().addAll("btn", "export-button", formato.estilo());
        setMinWidth(Region.USE_PREF_SIZE);
        setOnAction(e -> exportar());
    }

    public RonaldmichaExportButton conPrevio(Consumer<Runnable> paso) {
        this.previo = paso;
        return this;
    }

    public RonaldmichaExportButton alGuardar(Consumer<File> accion) {
        this.alGuardar = accion;
        return this;
    }

    public RonaldmichaExportButton alCancelar(Consumer<Archivo> accion) {
        this.alCancelar = accion;
        return this;
    }

    public void setGenerador(Callable<Archivo> generador) {
        this.generador = generador;
    }

    public Formato getFormato() {
        return formato;
    }

    public boolean isGenerando() {
        return generando;
    }

    public void exportar() {
        if (generando || generador == null) {
            return;
        }
        previo.accept(this::generar);
    }

    private void generar() {
        generando = true;
        Node antes = getGraphic();
        ProgressIndicator indicador = new ProgressIndicator();
        indicador.setPrefSize(16, 16);
        indicador.setMaxSize(16, 16);
        setGraphic(indicador);
        setText("Generando…");
        setDisable(true);
        RonaldmichaTareas.ejecutar(generador, archivo -> {
            restaurar(antes);
            File destino = RonaldmichaArchivos.guardar(this, archivo.nombre(), formato.descripcion, formato.extension,
                    archivo.contenido());
            if (destino == null) {
                alCancelar.accept(archivo);
                return;
            }
            RonaldmichaToast.archivo(this, destino.toPath());
            alGuardar.accept(destino);
        }, error -> {
            restaurar(antes);
            RonaldmichaToast.error(this, "No se pudo generar el archivo: " + RonaldmichaTareas.mensaje(error));
        });
    }

    private void restaurar(Node grafico) {
        generando = false;
        setGraphic(grafico);
        setText(texto);
        setDisable(false);
    }
}
