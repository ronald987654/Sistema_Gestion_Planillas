package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.calc.RonaldmichaRentaQuintaCalculator;
import com.planillaperu.calc.RonaldmichaResultadoPlanilla;
import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.common.RonaldmichaHoras;
import com.planillaperu.domain.RonaldmichaConceptoExtra;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaSituacionPeriodo;
import com.planillaperu.domain.RonaldmichaTipoConceptoExtra;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaTratamientoHe;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository.HoraExtraManual;
import com.planillaperu.service.RonaldmichaAccesoDenegadoException;
import com.planillaperu.service.RonaldmichaAsistenciaService.NovedadesPlanilla;
import com.planillaperu.service.RonaldmichaPlanillaService;
import com.planillaperu.service.RonaldmichaPlanillaService.Borrador;
import com.planillaperu.service.RonaldmichaPlanillaService.Detalle;
import com.planillaperu.service.RonaldmichaPlanillaService.Simulacion;
import com.planillaperu.service.RonaldmichaValidacionException;
import com.planillaperu.ui.components.RonaldmichaConceptosPlanilla;
import com.planillaperu.ui.components.RonaldmichaModal;
import com.planillaperu.ui.components.RonaldmichaToast;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;

final class RonaldmichaPlanillaDetalle {

    private final RonaldmichaAppContext contexto;
    private final Detalle detalle;
    private final boolean editable;
    private final Runnable alGuardar;
    private RonaldmichaModal modal;

    private final TextField diasPeriodo = soloLectura();
    private final TextField diasLaborados = soloLectura();
    private final TextField inasistencias = numero();
    private final TextField vacaciones = numero();
    private final TextField tardanza = numero();
    private final TextField licencia = numero();
    private final TextField feriados = numero();
    private final TextField subsidiados = numero();
    private final TextField comisiones = numero();
    private final TextField bonificacion = numero();
    private final TextField adelanto = numero();
    private final TextField otrosDescuentos = numero();

    private final VBox filasSobretiempo = new VBox(8);
    private final List<FilaSobretiempo> sobretiempo = new ArrayList<>();
    private final VBox filasConceptos = new VBox(8);
    private final List<FilaConcepto> conceptos = new ArrayList<>();
    private final VBox errores = new VBox(6);
    private final RonaldmichaConceptosPlanilla columnas = new RonaldmichaConceptosPlanilla();
    private final Button guardar = new Button("Guardar cambios");
    private final Label rentaDetalle = new Label();
    private final TextField otroRazon = new TextField();
    private final TextField otroRenta = numero();
    private final TextField otroRetencion = numero();

    private RonaldmichaPlanillaDetalle(RonaldmichaAppContext contexto, Detalle detalle, Runnable alGuardar) {
        this.contexto = contexto;
        this.detalle = detalle;
        this.alGuardar = alGuardar;
        this.editable = detalle.editable() && contexto.sesion().rol() == RonaldmichaRol.ADMIN;
    }

    static void abrir(RonaldmichaAppContext contexto, long trabajadorId, YearMonth mes, Node origen, Runnable alGuardar) {
        Detalle d;
        try {
            d = contexto.planilla().detalle(contexto.sesion(), trabajadorId, mes);
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(origen, e.getMessage());
            return;
        }
        new RonaldmichaPlanillaDetalle(contexto, d, alGuardar).mostrar(origen);
    }

    private void mostrar(Node origen) {
        VBox contenido = new VBox(16, cabecera());
        contenido.getStyleClass().add("planilla-detalle");
        if (detalle.desdeAsistencia()) {
            contenido.getChildren().add(avisoAsistencia());
        }
        if (!detalle.editable()) {
            contenido.getChildren().add(alerta(detalle.situacion() == RonaldmichaSituacionPeriodo.CERRADO
                    ? "Periodo cerrado: solo lectura. Reabra la planilla con un motivo para modificarla."
                    : "Este periodo aún no está abierto: los montos son referenciales.", "warning"));
        } else if (!editable) {
            contenido.getChildren().add(alerta("Solo un administrador puede modificar la planilla.", "info"));
        }
        contenido.getChildren().addAll(seccion("Días y novedades", camposNovedades()), seccionSobretiempo(),
                seccionConceptos(), seccionRenta(), errores, columnas);

        cargarValores();
        ScrollPane desplazable = new ScrollPane(contenido);
        desplazable.setFitToWidth(true);
        desplazable.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        desplazable.setPrefViewportHeight(620);
        desplazable.getStyleClass().add("modal-scroll");

        RonaldmichaTrabajador t = detalle.trabajador();
        modal = new RonaldmichaModal(t.nombreCompleto() + " · " + mesTexto(detalle.mes()), desplazable).ancho(1180);
        if (editable) {
            guardar.getStyleClass().addAll("btn", "btn-primary");
            guardar.setOnAction(e -> guardar());
            modal.agregarAccion(guardar);
        } else {
            modal.botonCancelar().setText("Cerrar");
        }
        modal.mostrar(origen);
        recalcular();
    }

    private Node cabecera() {
        RonaldmichaTrabajador t = detalle.trabajador();
        Label nombre = new Label(t.nombreCompleto());
        nombre.getStyleClass().add("title-lg");
        Label datos = new Label(t.tipoDoc().codigo() + " " + t.numDoc() + " · " + texto(t.cargo()) + " · "
                + t.regimen().descripcion() + " · " + RonaldmichaPlanillaController.pension(t));
        datos.getStyleClass().add("text-muted");
        datos.setWrapText(true);
        return new VBox(4, nombre, datos);
    }

    private Node avisoAsistencia() {
        Label texto = new Label("Los datos vienen de Asistencia: inasistencias, vacaciones, tardanza, licencias, "
                + "feriados trabajados y sobretiempo salen de las marcaciones del mes y aquí son de solo lectura.");
        texto.setWrapText(true);
        HBox.setHgrow(texto, Priority.ALWAYS);
        texto.setMaxWidth(Double.MAX_VALUE);
        Button ir = new Button("Ir a Asistencia");
        ir.getStyleClass().addAll("btn", "btn-sm");
        ir.setMinWidth(Region.USE_PREF_SIZE);
        ir.setOnAction(e -> {
            contexto.trabajadorSeleccionadoProperty().set(detalle.trabajador().id());
            modal.cerrar();
            RonaldmichaNavegacion.ir(RonaldmichaVista.ASISTENCIA);
        });
        HBox caja = new HBox(12, texto, ir);
        caja.setAlignment(Pos.CENTER_LEFT);
        caja.getStyleClass().addAll("alert", "info");
        return caja;
    }

    private Node camposNovedades() {
        GridPane grilla = new GridPane();
        grilla.setHgap(12);
        grilla.setVgap(10);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(25);
            grilla.getColumnConstraints().add(c);
        }
        Node[] campos = {
            campo("Días del periodo", diasPeriodo),
            campo("Días laborados (calculados)", diasLaborados),
            campo("Inasistencias (días)", inasistencias),
            campo("Vacaciones (días)", vacaciones),
            campo("Tardanza (min)", tardanza),
            campo("Licencia con goce (días)", licencia),
            campo("Feriado trabajado (días)", feriados),
            campo("Días subsidiados", subsidiados),
            campo("Comisiones (S/)", comisiones),
            campo("Bonificación extraordinaria (S/)", bonificacion),
            campo("Adelanto (S/)", adelanto),
            campo("Otros descuentos (S/)", otrosDescuentos)
        };
        for (int i = 0; i < campos.length; i++) {
            grilla.add(campos[i], i % 4, i / 4);
        }
        boolean deAsistencia = detalle.desdeAsistencia();
        for (TextField f : List.of(inasistencias, vacaciones, tardanza, licencia, feriados)) {
            bloquear(f, deAsistencia || !editable);
        }
        for (TextField f : List.of(subsidiados, comisiones, bonificacion, adelanto, otrosDescuentos)) {
            bloquear(f, !editable);
        }
        return grilla;
    }

    private Node seccionSobretiempo() {
        if (detalle.desdeAsistencia()) {
            NovedadesPlanilla a = detalle.asistencia();
            Label l = new Label("Sobretiempo de las marcaciones: " + RonaldmichaHoras.duracion(a.he25Min()) + " h al 25 % y "
                    + RonaldmichaHoras.duracion(a.he35Min()) + " h al 35 %. Se registra en Asistencia.");
            l.getStyleClass().add("text-muted");
            l.setWrapText(true);
            return seccion("Registro de sobretiempo", l);
        }
        Label ayuda = new Label("Para meses sin marcaciones. Horas en múltiplos de 0.5; un día no puede pasar de "
                + RonaldmichaPlanillaService.HORAS_MAXIMAS_DIA + " h (jornada de " + horas(detalle.horasDiarias())
                + " h + sobretiempo).");
        ayuda.getStyleClass().add("text-muted");
        ayuda.setWrapText(true);
        VBox caja = new VBox(10, ayuda, filasSobretiempo);
        if (editable) {
            Button agregar = new Button("+ Agregar sobretiempo");
            agregar.getStyleClass().addAll("btn", "btn-sm");
            agregar.setOnAction(e -> {
                agregarSobretiempo(new HoraExtraManual(null, null, RonaldmichaTratamientoHe.PAGAR, true));
                recalcular();
            });
            caja.getChildren().add(agregar);
        }
        return seccion("Registro de sobretiempo", caja);
    }

    private Node seccionConceptos() {
        VBox caja = new VBox(10, filasConceptos);
        if (editable) {
            FlowPane botones = new FlowPane(8, 8);
            botones.getChildren().addAll(
                    botonConcepto("+ Ingreso remunerativo", RonaldmichaTipoConceptoExtra.ING),
                    botonConcepto("+ Ingreso no remunerativo", RonaldmichaTipoConceptoExtra.ING_NR),
                    botonConcepto("+ Descuento", RonaldmichaTipoConceptoExtra.DES),
                    botonConcepto("+ Aporte empleador", RonaldmichaTipoConceptoExtra.APO));
            caja.getChildren().add(botones);
        }
        return seccion("Otros conceptos del periodo", caja);
    }

    private Node seccionRenta() {
        rentaDetalle.getStyleClass().add("text-muted");
        rentaDetalle.setWrapText(true);
        int anio = detalle.mes().getYear();
        var otro = contexto.planilla().otroEmpleador(contexto.sesion(), detalle.trabajador().id(), anio);
        otroRazon.setPromptText("Razón social");
        otroRazon.setText(otro.map(o -> o.razonSocial() == null ? "" : o.razonSocial()).orElse(""));
        otroRenta.setText(otro.map(o -> monto(o.renta())).orElse("0.00"));
        otroRetencion.setText(otro.map(o -> monto(o.retencion())).orElse("0.00"));
        GridPane grilla = new GridPane();
        grilla.setHgap(12);
        grilla.setVgap(10);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / 3);
            grilla.getColumnConstraints().add(c);
        }
        grilla.add(campo("Otro empleador " + anio, otroRazon), 0, 0);
        grilla.add(campo("Renta de 5.ª percibida allí (S/)", otroRenta), 1, 0);
        grilla.add(campo("Retención que le hicieron (S/)", otroRetencion), 2, 0);
        boolean puede = contexto.sesion().rol() == RonaldmichaRol.ADMIN;
        for (TextField f : List.of(otroRazon, otroRenta, otroRetencion)) {
            bloquear(f, !puede);
        }
        VBox caja = new VBox(10, rentaDetalle, grilla);
        if (puede) {
            Button guardarOtro = new Button("Guardar otro empleador");
            guardarOtro.getStyleClass().addAll("btn", "btn-sm");
            guardarOtro.setOnAction(e -> {
                List<String> invalidos = new ArrayList<>();
                BigDecimal renta = dinero(otroRenta, invalidos);
                BigDecimal retencion = dinero(otroRetencion, invalidos);
                if (!invalidos.isEmpty()) {
                    RonaldmichaToast.error(guardarOtro, "Revise los montos del otro empleador.");
                    return;
                }
                try {
                    contexto.planilla().guardarOtroEmpleador(contexto.sesion(), detalle.trabajador().id(), anio, null,
                            otroRazon.getText(), renta, retencion);
                    Node raiz = guardarOtro.getScene().getRoot();
                    modal.cerrar();
                    alGuardar.run();
                    RonaldmichaToast.exito(raiz, "Rentas de otros empleadores guardadas: se recalculó la renta de 5.ª.");
                } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException ex) {
                    RonaldmichaToast.error(guardarOtro, ex.getMessage());
                }
            });
            caja.getChildren().add(guardarOtro);
        }
        return seccion("Renta de quinta categoría", caja);
    }

    private Button botonConcepto(String texto, RonaldmichaTipoConceptoExtra tipo) {
        Button b = new Button(texto);
        b.getStyleClass().addAll("btn", "btn-sm");
        b.setOnAction(e -> {
            agregarConcepto(new RonaldmichaConceptoExtra(tipo, "", "", null));
            recalcular();
        });
        return b;
    }

    private void cargarValores() {
        Borrador g = detalle.guardado();
        NovedadesPlanilla a = detalle.asistencia();
        diasPeriodo.setText(String.valueOf(detalle.diasMes()));
        if (a != null) {
            inasistencias.setText(String.valueOf(a.faltas()));
            vacaciones.setText(String.valueOf(a.vacaciones()));
            tardanza.setText(String.valueOf(a.tardanzaMin()));
            licencia.setText(String.valueOf(a.licenciasConGoce()));
            feriados.setText(String.valueOf(a.feriadosTrabajados()));
        } else {
            inasistencias.setText(String.valueOf(g.inasistencias()));
            vacaciones.setText(String.valueOf(g.vacaciones()));
            tardanza.setText(String.valueOf(g.tardanzaMin()));
            licencia.setText(String.valueOf(g.licenciaConGoce()));
            feriados.setText(String.valueOf(g.feriadosTrabajados()));
            g.horasExtra().forEach(this::agregarSobretiempo);
        }
        subsidiados.setText(String.valueOf(g.diasSubsidiados()));
        comisiones.setText(monto(g.comisiones()));
        bonificacion.setText(monto(g.bonificacion()));
        adelanto.setText(monto(g.adelanto()));
        otrosDescuentos.setText(monto(g.otrosDescuentos()));
        g.conceptos().forEach(this::agregarConcepto);
        for (TextField f : List.of(inasistencias, vacaciones, tardanza, licencia, feriados, subsidiados, comisiones,
                bonificacion, adelanto, otrosDescuentos)) {
            f.textProperty().addListener((o, antes, ahora) -> recalcular());
        }
    }

    private void recalcular() {
        List<String> invalidos = new ArrayList<>();
        Simulacion s = contexto.planilla().simular(detalle, borrador(invalidos));
        RonaldmichaResultadoPlanilla r = s.resultado();
        diasLaborados.setText(String.valueOf(r.diasLaborados()));
        columnas.mostrar(r);
        RonaldmichaRentaQuintaCalculator.Renta renta = r.renta();
        rentaDetalle.setText("Renta anual proyectada " + RonaldmichaFormatos.moneda(renta.rentaAnualProyectada())
                + " − 7 UIT " + RonaldmichaFormatos.moneda(renta.deduccion()) + " = renta neta " + RonaldmichaFormatos.moneda(renta.rentaNeta())
                + " · impuesto anual " + RonaldmichaFormatos.moneda(renta.impuestoAnual())
                + (renta.retenidoADeducir().signum() > 0 ? " − retenido " + RonaldmichaFormatos.moneda(renta.retenidoADeducir()) : "")
                + " ÷ " + renta.divisor()
                + (renta.impuestoExtraordinario().signum() > 0
                        ? " + impuesto de la bonificación " + RonaldmichaFormatos.moneda(renta.impuestoExtraordinario()) : "")
                + " → retención del mes " + RonaldmichaFormatos.moneda(r.rentaQuinta()) + ".");

        List<String> mensajes = new ArrayList<>();
        if (!invalidos.isEmpty()) {
            mensajes.add("Revise los campos marcados en rojo: " + String.join(", ", invalidos) + ".");
        }
        mensajes.addAll(s.errores());
        errores.getChildren().setAll(mensajes.stream().map(m -> (Node) alerta(m, "error")).toList());
        guardar.setDisable(!mensajes.isEmpty());
    }

    private void guardar() {
        try {
            RonaldmichaResultadoPlanilla r = contexto.planilla().guardar(contexto.sesion(), detalle.trabajador().id(),
                    detalle.mes(), borrador(new ArrayList<>()));
            Node raiz = guardar.getScene().getRoot();
            modal.cerrar();
            alGuardar.run();
            RonaldmichaToast.exito(raiz, "Planilla de " + detalle.trabajador().nombreCompleto() + " guardada · neto "
                    + RonaldmichaFormatos.moneda(r.netoAPagar()) + ".");
        } catch (RonaldmichaValidacionException | RonaldmichaAccesoDenegadoException e) {
            RonaldmichaToast.error(guardar, e.getMessage());
        }
    }

    private Borrador borrador(List<String> invalidos) {
        return new Borrador(entero(inasistencias, invalidos), entero(vacaciones, invalidos),
                entero(tardanza, invalidos), entero(licencia, invalidos), entero(feriados, invalidos),
                entero(subsidiados, invalidos), dinero(comisiones, invalidos), dinero(bonificacion, invalidos),
                dinero(adelanto, invalidos), dinero(otrosDescuentos, invalidos),
                sobretiempo.stream().map(f -> f.valor(invalidos)).toList(),
                conceptos.stream().map(f -> f.valor(invalidos)).toList());
    }

    private void agregarSobretiempo(HoraExtraManual he) {
        FilaSobretiempo fila = new FilaSobretiempo(he);
        sobretiempo.add(fila);
        filasSobretiempo.getChildren().add(fila.nodo);
    }

    private void agregarConcepto(RonaldmichaConceptoExtra c) {
        FilaConcepto fila = new FilaConcepto(c);
        conceptos.add(fila);
        filasConceptos.getChildren().add(fila.nodo);
    }

    private final class FilaSobretiempo {
        private final DatePicker fecha = selectorFecha(detalle.mes());
        private final TextField horas = numero();
        private final ComboBox<RonaldmichaTratamientoHe> tratamiento = new ComboBox<>();
        private final CheckBox autorizado = new CheckBox("Autorizado");
        private final HBox nodo = new HBox(10);

        FilaSobretiempo(HoraExtraManual he) {
            fecha.setValue(he.fecha());
            horas.setText(he.horas() == null ? "" : he.horas().stripTrailingZeros().toPlainString());
            horas.setPromptText("0.5");
            horas.setPrefWidth(90);
            tratamiento.getItems().setAll(RonaldmichaTratamientoHe.values());
            tratamiento.setValue(he.tratamiento());
            autorizado.setSelected(he.autorizada());
            Button quitar = new Button("Quitar");
            quitar.getStyleClass().addAll("btn", "btn-sm", "btn-ghost");
            quitar.setOnAction(e -> {
                sobretiempo.remove(this);
                filasSobretiempo.getChildren().remove(nodo);
                recalcular();
            });
            nodo.getChildren().addAll(fecha, horas, new Label("h"), tratamiento, autorizado, quitar);
            nodo.setAlignment(Pos.CENTER_LEFT);
            fecha.valueProperty().addListener((o, a, b) -> recalcular());
            horas.textProperty().addListener((o, a, b) -> recalcular());
            tratamiento.valueProperty().addListener((o, a, b) -> recalcular());
            nodo.setDisable(!editable);
            quitar.setVisible(editable);
        }

        HoraExtraManual valor(List<String> invalidos) {
            BigDecimal h = decimal(horas, "horas de sobretiempo", invalidos);
            return new HoraExtraManual(fecha.getValue(), h, tratamiento.getValue(), autorizado.isSelected());
        }
    }

    private final class FilaConcepto {
        private final ComboBox<RonaldmichaTipoConceptoExtra> tipo = new ComboBox<>();
        private final TextField codigo = new TextField();
        private final TextField descripcion = new TextField();
        private final TextField monto = numero();
        private final HBox nodo = new HBox(10);

        FilaConcepto(RonaldmichaConceptoExtra c) {
            tipo.getItems().setAll(RonaldmichaTipoConceptoExtra.values());
            tipo.setValue(c.tipo());
            tipo.setPrefWidth(220);
            codigo.setText(c.codigoPlame() == null ? "" : c.codigoPlame());
            codigo.setPromptText("0000");
            codigo.setPrefWidth(90);
            codigo.getStyleClass().add("mono");
            codigo.setTextFormatter(new TextFormatter<String>(cambio ->
                    cambio.getControlNewText().matches("\\d{0,4}") ? cambio : null));
            descripcion.setText(c.descripcion() == null ? "" : c.descripcion());
            descripcion.setPromptText("Descripción");
            HBox.setHgrow(descripcion, Priority.ALWAYS);
            monto.setText(c.monto() == null ? "" : monto(c.monto()));
            monto.setPromptText("0.00");
            monto.setPrefWidth(120);
            Button quitar = new Button("Quitar");
            quitar.getStyleClass().addAll("btn", "btn-sm", "btn-ghost");
            quitar.setOnAction(e -> {
                conceptos.remove(this);
                filasConceptos.getChildren().remove(nodo);
                recalcular();
            });
            nodo.getChildren().addAll(tipo, codigo, descripcion, monto, quitar);
            nodo.setAlignment(Pos.CENTER_LEFT);
            tipo.valueProperty().addListener((o, a, b) -> recalcular());
            codigo.textProperty().addListener((o, a, b) -> recalcular());
            descripcion.textProperty().addListener((o, a, b) -> recalcular());
            monto.textProperty().addListener((o, a, b) -> recalcular());
            nodo.setDisable(!editable);
            quitar.setVisible(editable);
        }

        RonaldmichaConceptoExtra valor(List<String> invalidos) {
            return new RonaldmichaConceptoExtra(tipo.getValue(), codigo.getText(), descripcion.getText(),
                    decimal(monto, "monto de un concepto", invalidos));
        }
    }

    private static int entero(TextField campo, List<String> invalidos) {
        String v = campo.getText() == null ? "" : campo.getText().trim();
        marcar(campo, false);
        if (v.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            marcar(campo, true);
            invalidos.add(nombre(campo));
            return 0;
        }
    }

    private static BigDecimal dinero(TextField campo, List<String> invalidos) {
        BigDecimal v = decimal(campo, nombre(campo), invalidos);
        return v == null ? RonaldmichaDinero.CERO : v;
    }

    private static BigDecimal decimal(TextField campo, String nombre, List<String> invalidos) {
        String v = campo.getText() == null ? "" : campo.getText().trim().replace(",", "");
        marcar(campo, false);
        if (v.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            marcar(campo, true);
            invalidos.add(nombre);
            return null;
        }
    }

    private static void marcar(TextField campo, boolean error) {
        campo.getStyleClass().remove("campo-error");
        if (error) {
            campo.getStyleClass().add("campo-error");
        }
    }

    private static String nombre(TextField campo) {
        return campo.getAccessibleText() == null ? "un campo" : campo.getAccessibleText().toLowerCase(RonaldmichaFormatos.LOCALE_PE);
    }

    private static void bloquear(TextField campo, boolean soloLectura) {
        campo.setEditable(!soloLectura);
        if (soloLectura) {
            campo.getStyleClass().add("readonly");
            campo.setFocusTraversable(false);
        }
    }

    private static VBox seccion(String titulo, Node contenido) {
        Label l = new Label(titulo);
        l.getStyleClass().add("title-md");
        VBox caja = new VBox(12, l, contenido);
        caja.getStyleClass().add("form-section");
        return caja;
    }

    private static VBox campo(String etiqueta, TextField control) {
        Label l = new Label(etiqueta);
        l.getStyleClass().add("field-label");
        control.setAccessibleText(etiqueta);
        control.setMaxWidth(Double.MAX_VALUE);
        return new VBox(4, l, control);
    }

    private static Label alerta(String texto, String estilo) {
        Label l = new Label(texto);
        l.getStyleClass().addAll("alert", estilo);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private static TextField numero() {
        TextField t = new TextField();
        t.getStyleClass().add("num");
        return t;
    }

    private static TextField soloLectura() {
        TextField t = numero();
        bloquear(t, true);
        return t;
    }

    private static DatePicker selectorFecha(YearMonth mes) {
        DatePicker d = new DatePicker();
        d.setPromptText("dd/mm/aaaa");
        d.setPrefWidth(160);
        d.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate f) {
                return RonaldmichaFormatos.fecha(f);
            }

            @Override
            public LocalDate fromString(String s) {
                try {
                    return s == null || s.isBlank() ? null : RonaldmichaFormatos.leerFecha(s);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });
        d.setOnShowing(e -> {
            if (d.getValue() == null) {
                d.setValue(mes.atDay(1));
            }
        });
        return d;
    }

    private static String monto(BigDecimal valor) {
        return valor == null || valor.signum() == 0 ? "" : RonaldmichaDinero.redondear(valor).toPlainString();
    }

    private static String horas(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? "Sin cargo" : valor;
    }

    static String mesTexto(YearMonth mes) {
        String n = mes.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, RonaldmichaFormatos.LOCALE_PE);
        return n.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + n.substring(1) + " " + mes.getYear();
    }
}
