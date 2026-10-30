package com.planillaperu.ui.components;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.SVGPath;

public enum RonaldmichaIcono {
    INICIO("M3 11 L12 4 L21 11 M5 9.5 V20 H10 V14 H14 V20 H19 V9.5"),
    TRABAJADORES("M5 8 A4 4 0 1 0 13 8 A4 4 0 1 0 5 8 Z M2 20 C2 15.5 5 13.5 9 13.5 C13 13.5 16 15.5 16 20"
            + " M15.5 4.3 A4 4 0 0 1 15.5 11.7 M18 14 C20.5 15 22 17 22 20"),
    RELOJ("M3 12 A9 9 0 1 0 21 12 A9 9 0 1 0 3 12 Z M12 7 V12 L15.5 14"),
    SOL("M8 12 A4 4 0 1 0 16 12 A4 4 0 1 0 8 12 Z M12 2.5 V4.5 M12 19.5 V21.5 M2.5 12 H4.5 M19.5 12 H21.5"
            + " M5.3 5.3 L6.7 6.7 M17.3 17.3 L18.7 18.7 M5.3 18.7 L6.7 17.3 M17.3 6.7 L18.7 5.3"),
    PLANILLA("M4 4 H20 V20 H4 Z M4 9 H20 M4 14.5 H20 M10 9 V20"),
    CALENDARIO("M4 6 H20 V20 H4 Z M4 10 H20 M8 3.5 V7.5 M16 3.5 V7.5"),
    REPORTES("M4 20 H20 M7 20 V12 M12 20 V6 M17 20 V15"),
    REGALO("M4 11 H20 V20 H4 Z M3 7.5 H21 V11 H3 Z M12 7.5 V20 M12 7.5 C10.5 3.5 6.5 4 8 7.5"
            + " M12 7.5 C13.5 3.5 17.5 4 16 7.5"),
    BANCO("M3 9.5 L12 4 L21 9.5 Z M5.5 9.5 V17.5 M10 9.5 V17.5 M14 9.5 V17.5 M18.5 9.5 V17.5 M3 20 H21"),
    LIQUIDACION("M14 3.5 H6 V20.5 H18 V7.5 Z M14 3.5 V7.5 H18 M9 14 L11 16 L15 12"),
    DOCUMENTO("M14 3.5 H6 V20.5 H18 V7.5 Z M14 3.5 V7.5 H18 M9 12 H15 M9 16 H15"),
    DINERO("M3 6.5 H21 V17.5 H3 Z M9.5 12 A2.5 2.5 0 1 0 14.5 12 A2.5 2.5 0 1 0 9.5 12 Z M6 9.5 V9.6 M18 14.4 V14.5"),
    EMPRESA("M4 20 V4 H14 V20 M14 9 H20 V20 M2 20 H22 M7 8 H11 M7 12 H11 M7 16 H11 M17 13 H17.5 M17 16.5 H17.5"),
    AJUSTES("M4 6 H20 M4 12 H20 M4 18 H20 M8 4 V8 M15 10 V14 M10 16 V20"),
    ESCUDO("M12 3 L20 6 V12 C20 16.5 16.5 19.5 12 21 C7.5 19.5 4 16.5 4 12 V6 Z M9 12 L11 14 L15 10"),
    CARPETA("M3 6 H9.5 L11.5 8 H21 V19 H3 Z"),
    USUARIO("M8 8 A4 4 0 1 0 16 8 A4 4 0 1 0 8 8 Z M4 21 C4 16.5 7.5 14 12 14 C16.5 14 20 16.5 20 21"),
    CAMPANA("M6 16.5 V11 C6 7.7 8.7 5 12 5 C15.3 5 18 7.7 18 11 V16.5 L19.5 18 H4.5 Z M10 21 H14 M12 3 V5"),
    BUSCAR("M4 11 A7 7 0 1 0 18 11 A7 7 0 1 0 4 11 Z M16 16 L20.5 20.5"),
    ANTERIOR("M15 5 L8 12 L15 19"),
    SIGUIENTE("M9 5 L16 12 L9 19"),
    CERRAR("M6 6 L18 18 M18 6 L6 18"),
    SALIR("M14 4 H19 V20 H14 M10 8 L6 12 L10 16 M6 12 H16"),
    VACIO("M3 13 L6 5 H18 L21 13 V19 H3 Z M3 13 H8 L9.5 15.5 H14.5 L16 13 H21"),
    DESCARGA("M12 4 V15 M7 10.5 L12 15.5 L17 10.5 M5 20 H19"),
    CORREO("M3 6 H21 V18 H3 Z M3 6 L12 13 L21 6"),
    EDITAR("M4 20 H8 L19 9 L15 5 L4 16 Z M13 7 L17 11"),
    CHECK("M5 12.5 L10 17 L19 7.5");

    private final String trazo;

    RonaldmichaIcono(String trazo) {
        this.trazo = trazo;
    }

    public Node nodo(double tamano) {
        SVGPath path = new SVGPath();
        path.setContent(trazo);
        path.getStyleClass().add("icon");
        double escala = tamano / 24.0;
        path.setScaleX(escala);
        path.setScaleY(escala);
        return new Group(path);
    }

    public Node nodo() {
        return nodo(20);
    }
}
