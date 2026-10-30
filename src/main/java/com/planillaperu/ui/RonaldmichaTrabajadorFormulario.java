package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.calc.RonaldmichaAportePensionCalculator;
import com.planillaperu.calc.RonaldmichaAportePensionCalculator.AportePension;
import com.planillaperu.calc.RonaldmichaJornadaCalculator.Jornada;
import com.planillaperu.common.RonaldmichaFormatos;
import com.planillaperu.domain.RonaldmichaAprobacion;
import com.planillaperu.domain.RonaldmichaCategoria;
import com.planillaperu.domain.RonaldmichaHorario;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaSalud;
import com.planillaperu.domain.RonaldmichaSistemaPension;
import com.planillaperu.domain.RonaldmichaTipoComision;
import com.planillaperu.domain.RonaldmichaTipoDocumento;
import com.planillaperu.domain.RonaldmichaTipoPago;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.service.RonaldmichaConsultaReniec;
import com.planillaperu.service.RonaldmichaParametroNoEncontradoException;
import com.planillaperu.ui.components.RonaldmichaToast;
import com.planillaperu.ui.components.RonaldmichaValidacion;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

final class RonaldmichaTrabajadorFormulario extends VBox {

    private static final List<String> TIPOS_CONTRATO = List.of("Indeterminado", "Plazo fijo");

    private final RonaldmichaAppContext contexto;
    private final YearMonth periodo;
    private final RonaldmichaTrabajador original;

    private final Label errores = new Label();
    private final RonaldmichaValidacion validacion = new RonaldmichaValidacion();

    private final TextField codigo = new TextField();
    private final ComboBox<RonaldmichaTipoDocumento> tipoDoc = combo(RonaldmichaTipoDocumento.values(), RonaldmichaTipoDocumento::descripcion);
    private final TextField numDoc = num();
    private final TextField apPaterno = new TextField();
    private final TextField apMaterno = new TextField();
    private final TextField nombres = new TextField();
    private final TextField celular = num();
    private final TextField correo = new TextField();
    private final DatePicker fechaNacimiento = fecha();
    private final TextField hijos = num();
    private final TextField direccion = new TextField();

    private final DatePicker fechaIngreso = fecha();
    private final DatePicker fechaCese = fecha();
    private final ComboBox<String> area = new ComboBox<>();
    private final TextField cargo = new TextField();
    private final ComboBox<String> tipoContrato = new ComboBox<>();
    private final ComboBox<RonaldmichaRegimenLaboral> regimen = combo(RonaldmichaRegimenLaboral.values(), RonaldmichaRegimenLaboral::descripcion);
    private final TextField centroCosto = new TextField();
    private final TextField sucursal = new TextField();
    private final ComboBox<RonaldmichaCategoria> categoria = combo(RonaldmichaCategoria.values(), RonaldmichaCategoria::nombre);
    private final TextField horaIngreso = num();
    private final TextField horaSalida = num();
    private final TextField refrigerio = num();
    private final TextField tolerancia = num();
    private final Map<DayOfWeek, ToggleButton> dias = new EnumMap<>(DayOfWeek.class);
    private final CheckBox noFiscalizado = new CheckBox("Trabajador no fiscalizado (sin control de asistencia)");
    private final Label jornada = new Label();

    private final TextField sueldo = num();
    private final TextField movilidad = num();
    private final CheckBox asigFamiliar = new CheckBox("Asignación familiar");
    private final CheckBox vidaLey = new CheckBox("Seguro Vida Ley");
    private final ComboBox<RonaldmichaSalud> salud = combo(RonaldmichaSalud.values(), RonaldmichaSalud::nombre);
    private final ComboBox<RonaldmichaSistemaPension> sistema = combo(RonaldmichaSistemaPension.values(), RonaldmichaSistemaPension::nombre);
    private final TextField cuspp = num();
    private final ComboBox<RonaldmichaTipoComision> tipoComision = combo(RonaldmichaTipoComision.values(), RonaldmichaTipoComision::descripcion);
    private final Node campoCuspp;
    private final Node campoComision;
    private final VBox recuadroPension = new VBox(4);

    private final ComboBox<RonaldmichaTipoPago> tipoPago = combo(RonaldmichaTipoPago.values(), RonaldmichaTipoPago::nombre);
    private final TextField bancoSueldo = new TextField();
    private final TextField ctaSueldo = num();
    private final TextField bancoCts = new TextField();
    private final TextField ctaCts = num();
    private final Label etiquetaBanco = etiqueta("Banco (sueldo)");
    private final Label etiquetaCuenta = etiqueta("N° de cuenta (sueldo)");

    RonaldmichaTrabajadorFormulario(RonaldmichaAppContext contexto, RonaldmichaTrabajador trabajador, RonaldmichaHorario horario, List<String> areas,
                         YearMonth periodo) {
        this.contexto = contexto;
        this.periodo = periodo;
        this.original = trabajador;
        setSpacing(16);
        getStyleClass().add("trabajador-form");

        errores.getStyleClass().addAll("alert", "error");
        errores.setWrapText(true);
        errores.setMaxWidth(Double.MAX_VALUE);
        mostrarErrores(List.of());

        codigo.setEditable(false);
        codigo.setFocusTraversable(false);
        codigo.getStyleClass().add("num");
        area.setEditable(true);
        area.getItems().setAll(areas);
        area.setMaxWidth(Double.MAX_VALUE);
        tipoContrato.setEditable(true);
        tipoContrato.getItems().setAll(TIPOS_CONTRATO);
        tipoContrato.setMaxWidth(Double.MAX_VALUE);
        numDoc.setPromptText("8 dígitos");
        cuspp.setPromptText("12 caracteres");
        horaIngreso.setPromptText("08:00");
        horaSalida.setPromptText("17:00");
        correo.setPromptText("nombre@empresa.pe");

        campoCuspp = campo("CUSPP *", cuspp);
        campoComision = campo("Tipo de comisión *", tipoComision);

        getChildren().addAll(errores, avisoDobleControl(), seccionPersonal(), seccionLaboral(),
                seccionRemuneracion(), seccionPago());

        cargar(trabajador, horario);
        configurarDinamica();
        configurarValidacion();
    }

    ReadOnlyBooleanProperty validoProperty() {
        return validacion.validoProperty();
    }

    private void configurarValidacion() {
        validacion
                .regla(numDoc, () -> {
                    String n = numDoc.getText() == null ? "" : numDoc.getText().trim();
                    if (tipoDoc.getValue() == null) {
                        return "Elija el tipo de documento.";
                    }
                    if (n.isEmpty()) {
                        return "Ingrese el número de documento.";
                    }
                    if (tipoDoc.getValue() == RonaldmichaTipoDocumento.DNI) {
                        return n.matches("\\d{8}") ? null : "El DNI debe tener 8 dígitos.";
                    }
                    return n.toUpperCase(RonaldmichaFormatos.LOCALE_PE).matches("[A-Z0-9]{1,12}") ? null
                            : "Hasta 12 letras o números.";
                }, numDoc.textProperty(), tipoDoc.valueProperty())
                .regla(apPaterno, () -> vacio(apPaterno.getText()) ? "Ingrese el apellido paterno." : null,
                        apPaterno.textProperty())
                .regla(nombres, () -> vacio(nombres.getText()) ? "Ingrese los nombres." : null, nombres.textProperty())
                .regla(correo, () -> vacio(correo.getText())
                        || correo.getText().trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") ? null
                        : "El correo no es válido.", correo.textProperty())
                .regla(celular, () -> vacio(celular.getText()) || celular.getText().trim().matches("\\+?\\d{6,15}")
                        ? null : "Solo números (6 a 15 dígitos).", celular.textProperty())
                .regla(hijos, () -> hijosEscritos() >= 0 ? null : "Ingrese un entero de 0 o más.", hijos.textProperty())
                .regla(fechaNacimiento, () -> fechaNacimiento.getValue() == null || fechaIngreso.getValue() == null
                        || fechaNacimiento.getValue().isBefore(fechaIngreso.getValue()) ? null
                        : "Debe ser anterior a la fecha de ingreso.", fechaNacimiento.valueProperty(),
                        fechaIngreso.valueProperty())
                .regla(fechaIngreso, () -> fechaIngreso.getValue() == null ? "Ingrese la fecha de ingreso." : null,
                        fechaIngreso.valueProperty())
                .regla(fechaCese, () -> fechaCese.getValue() == null || fechaIngreso.getValue() == null
                        || !fechaCese.getValue().isBefore(fechaIngreso.getValue()) ? null
                        : "No puede ser anterior a la fecha de ingreso.", fechaCese.valueProperty(),
                        fechaIngreso.valueProperty())
                .regla(area, () -> vacio(textoCombo(area)) ? "Ingrese el área." : null, area.getEditor().textProperty(),
                        area.valueProperty())
                .regla(cargo, () -> vacio(cargo.getText()) ? "Ingrese el cargo." : null, cargo.textProperty())
                .regla(horaIngreso, () -> horaValida(horaIngreso) ? null : "Hora con formato hh:mm.",
                        horaIngreso.textProperty())
                .regla(horaSalida, () -> horaValida(horaSalida) ? null : "Hora con formato hh:mm.",
                        horaSalida.textProperty())
                .regla(sueldo, () -> {
                    BigDecimal s = dec(sueldo);
                    return s == null || s.signum() <= 0 ? "Ingrese el sueldo básico (mayor que 0)." : null;
                }, sueldo.textProperty())
                .regla(movilidad, () -> {
                    BigDecimal m = decCero(movilidad);
                    return m == null || m.signum() < 0 ? "La movilidad debe ser 0 o más." : null;
                }, movilidad.textProperty())
                .regla(cuspp, () -> sistema.getValue() == null || !sistema.getValue().esAfp()
                        || (cuspp.getText() != null
                        && cuspp.getText().trim().toUpperCase(RonaldmichaFormatos.LOCALE_PE).matches("[A-Z0-9]{12}")) ? null
                        : "Obligatorio para AFP: 12 letras o números.", cuspp.textProperty(), sistema.valueProperty())
                .regla(tipoComision, () -> sistema.getValue() != null && sistema.getValue().esAfp()
                        && tipoComision.getValue() == null ? "Elija flujo o mixta." : null, tipoComision.valueProperty(),
                        sistema.valueProperty());
    }

    private static boolean horaValida(TextField campo) {
        try {
            hora(campo);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private Node seccionPersonal() {
        Button reniec = new Button("RENIEC");
        reniec.getStyleClass().add("btn");
        reniec.setMinWidth(Button.USE_PREF_SIZE);
        reniec.setOnAction(e -> consultarReniec());
        HBox documento = new HBox(8, numDoc, reniec);
        HBox.setHgrow(numDoc, Priority.ALWAYS);

        GridPane g = grilla(3);
        g.add(campo("Código", codigo), 0, 0);
        g.add(campo("Tipo de documento *", tipoDoc), 1, 0);
        g.add(campo("N° de documento *", documento), 2, 0);
        g.add(campo("Apellido paterno *", apPaterno), 0, 1);
        g.add(campo("Apellido materno", apMaterno), 1, 1);
        g.add(campo("Nombres *", nombres), 2, 1);
        g.add(campo("Celular", celular), 0, 2);
        g.add(campo("Correo", correo), 1, 2, 2, 1);
        g.add(campo("Fecha de nacimiento", fechaNacimiento), 0, 3);
        g.add(campo("N° de hijos", hijos), 1, 3);
        g.add(campo("Dirección", direccion), 2, 3);
        return seccion("Datos personales", g);
    }

    private Node seccionLaboral() {
        GridPane g = grilla(3);
        g.add(campo("Fecha de ingreso *", fechaIngreso), 0, 0);
        g.add(campo("Fecha de cese", fechaCese), 1, 0);
        g.add(campo("Régimen laboral *", regimen), 2, 0);
        g.add(campo("Área *", area), 0, 1);
        g.add(campo("Cargo *", cargo), 1, 1);
        g.add(campo("Tipo de contrato", tipoContrato), 2, 1);
        g.add(campo("Centro de costo", centroCosto), 0, 2);
        g.add(campo("Sucursal", sucursal), 1, 2);
        g.add(campo("Categoría *", categoria), 2, 2);

        HBox botonesDias = new HBox(6);
        botonesDias.getStyleClass().add("dias");
        for (DayOfWeek d : DayOfWeek.values()) {
            String nombre = d.getDisplayName(TextStyle.SHORT_STANDALONE, RonaldmichaFormatos.LOCALE_PE).replace(".", "");
            ToggleButton b = new ToggleButton(nombre.substring(0, 1).toUpperCase(RonaldmichaFormatos.LOCALE_PE) + nombre.substring(1));
            b.getStyleClass().add("dia-toggle");
            dias.put(d, b);
            botonesDias.getChildren().add(b);
        }

        GridPane h = grilla(4);
        h.add(campo("Hora de ingreso *", horaIngreso), 0, 0);
        h.add(campo("Hora de salida *", horaSalida), 1, 0);
        h.add(campo("Refrigerio (min)", refrigerio), 2, 0);
        h.add(campo("Tolerancia (min)", tolerancia), 3, 0);
        h.add(campo("Días laborables *", botonesDias), 0, 1, 4, 1);

        jornada.getStyleClass().add("jornada");
        Label subtitulo = new Label("Horario (de él se toma la jornada)");
        subtitulo.getStyleClass().add("field-label");
        return seccion("Datos laborales", g, subtitulo, h, noFiscalizado, jornada);
    }

    private Node seccionRemuneracion() {
        GridPane g = grilla(3);
        g.add(campo("Sueldo básico (S/) *", sueldo), 0, 0);
        g.add(campo("Movilidad (S/)", movilidad), 1, 0);
        g.add(new VBox(10, asigFamiliar, vidaLey), 2, 0);
        g.add(campo("Salud *", salud), 0, 1);
        g.add(campo("Sistema de pensiones *", sistema), 1, 1);
        g.add(campoCuspp, 2, 1);
        g.add(campoComision, 0, 2);
        recuadroPension.getStyleClass().addAll("alert", "info", "recuadro-pension");
        return seccion("Remuneración y seguridad social", g, recuadroPension);
    }

    private Node seccionPago() {
        GridPane g = grilla(3);
        g.add(campo("Forma de pago *", tipoPago), 0, 0);
        g.add(new VBox(6, etiquetaBanco, bancoSueldo), 1, 0);
        g.add(new VBox(6, etiquetaCuenta, ctaSueldo), 2, 0);
        g.add(campo("Banco (CTS)", bancoCts), 1, 1);
        g.add(campo("N° de cuenta (CTS)", ctaCts), 2, 1);
        return seccion("Forma de pago", g);
    }

    private Node avisoDobleControl() {
        VBox caja = new VBox(6);
        if (original == null || original.id() == null) {
            caja.setManaged(false);
            caja.setVisible(false);
            return caja;
        }
        Label info = new Label("Los cambios de sueldo y de cuentas bancarias no se aplican de inmediato: quedan "
                + "pendientes hasta que otro usuario con rol APROBADOR los apruebe.");
        info.getStyleClass().addAll("alert", "info");
        info.setWrapText(true);
        info.setMaxWidth(Double.MAX_VALUE);
        caja.getChildren().add(info);
        contexto.trabajadores().cambioPendiente(original.id()).ifPresent((RonaldmichaAprobacion p) -> {
            Label pendiente = new Label("Cambio pendiente de aprobación (" + p.registradoPor() + ", "
                    + RonaldmichaFormatos.fechaHora(p.fecha()) + "): " + p.detalle());
            pendiente.getStyleClass().addAll("alert", "warning");
            pendiente.setWrapText(true);
            pendiente.setMaxWidth(Double.MAX_VALUE);
            caja.getChildren().add(pendiente);
        });
        return caja;
    }

    private void configurarDinamica() {
        ChangeListener<Object> pension = (o, a, b) -> actualizarPension();
        sueldo.textProperty().addListener(pension);
        asigFamiliar.selectedProperty().addListener(pension);
        sistema.valueProperty().addListener(pension);
        tipoComision.valueProperty().addListener(pension);

        ChangeListener<Object> horario = (o, a, b) -> actualizarJornada();
        horaIngreso.textProperty().addListener(horario);
        horaSalida.textProperty().addListener(horario);
        refrigerio.textProperty().addListener(horario);
        dias.values().forEach(b -> b.selectedProperty().addListener(horario));

        tipoPago.valueProperty().addListener((o, a, b) -> actualizarPago());
        tipoDoc.valueProperty().addListener((o, a, t) ->
                numDoc.setPromptText(t == RonaldmichaTipoDocumento.DNI ? "8 dígitos" : "Hasta 12 caracteres"));

        try {
            asigFamiliar.setText("Asignación familiar (" + RonaldmichaFormatos.moneda(contexto.trabajadores().asignacionFamiliar(periodo)) + ")");
        } catch (RonaldmichaParametroNoEncontradoException e) {
            asigFamiliar.setText("Asignación familiar");
        }
        actualizarPension();
        actualizarJornada();
        actualizarPago();
    }

    private void actualizarPension() {
        RonaldmichaSistemaPension s = sistema.getValue();
        boolean afp = s != null && s.esAfp();
        mostrar(campoCuspp, afp);
        mostrar(campoComision, afp);
        recuadroPension.getChildren().clear();
        if (s == null) {
            recuadroPension.getChildren().add(linea("Elija el sistema de pensiones para ver el aporte."));
            return;
        }
        AportePension a;
        try {
            a = contexto.trabajadores().aportePension(s, tipoComision.getValue(), dec(sueldo),
                    asigFamiliar.isSelected(), periodo);
        } catch (RonaldmichaParametroNoEncontradoException e) {
            recuadroPension.getChildren().add(linea(e.getMessage()));
            return;
        }
        if (!afp) {
            recuadroPension.getChildren().addAll(
                    titulo("ONP – D.L. 19990"),
                    linea("Aporte " + pct(a.tasaAporte()) + " (código " + RonaldmichaAportePensionCalculator.CODIGO_ONP + ") = "
                            + RonaldmichaFormatos.moneda(a.aporte()) + base(a) + "."),
                    linea("No paga prima ni comisión. No requiere CUSPP. Se declara en el PDT PLAME."),
                    linea("Jubilación: 20 años de aportes y 65 años de edad."),
                    linea("Puede pasar a AFP; de AFP a ONP solo por desafiliación aprobada por la SBS."));
            return;
        }
        recuadroPension.getChildren().addAll(
                titulo(s.nombre() + " – Sistema Privado de Pensiones"),
                linea("Aporte obligatorio " + pct(a.tasaAporte()) + " (código " + RonaldmichaAportePensionCalculator.CODIGO_AFP_APORTE
                        + ") = " + RonaldmichaFormatos.moneda(a.aporte()) + base(a)),
                linea("Prima de seguros " + pct(a.tasaPrima()) + " (código " + RonaldmichaAportePensionCalculator.CODIGO_AFP_PRIMA
                        + ") sobre " + RonaldmichaFormatos.moneda(a.basePrima()) + ", con tope RMA " + RonaldmichaFormatos.moneda(a.topePrima())
                        + (a.primaTopada() ? " (aplicado)" : "") + " = " + RonaldmichaFormatos.moneda(a.prima())),
                linea(a.tipoComision() == RonaldmichaTipoComision.MIXTA
                        ? "Comisión mixta: se cobra sobre el saldo (" + pct(a.tasaComision()) + " anual), no sobre la remuneración."
                        : "Comisión de flujo " + pct(a.tasaComision()) + " (código "
                        + RonaldmichaAportePensionCalculator.CODIGO_AFP_COMISION + ") = " + RonaldmichaFormatos.moneda(a.comision())),
                totalLinea("Total descuento AFP: " + RonaldmichaFormatos.moneda(a.total())),
                linea("Se declara en AFPnet."));
    }

    private void actualizarJornada() {
        RonaldmichaHorario h = leerHorario();
        jornada.getStyleClass().removeAll("chip", "warning", "success");
        if (h == null || h.diasLaborables().isEmpty()) {
            jornada.setText("Jornada: complete el horario.");
            return;
        }
        Jornada j = contexto.trabajadores().jornada(h);
        jornada.setText("Jornada: " + horas(j.horasDiarias()) + " h diarias · " + horas(j.horasSemanales())
                + " h semanales (" + j.dias() + (j.dias() == 1 ? " día" : " días") + ")"
                + (j.tiempoParcial() ? " · Tiempo parcial: se permite sueldo menor a la RMV" : ""));
        jornada.getStyleClass().addAll("chip", j.tiempoParcial() ? "warning" : "success");
    }

    private void actualizarPago() {
        boolean deposito = tipoPago.getValue() == RonaldmichaTipoPago.DEPOSITO;
        etiquetaBanco.setText("Banco (sueldo)" + (deposito ? " *" : ""));
        etiquetaCuenta.setText("N° de cuenta (sueldo)" + (deposito ? " *" : ""));
    }

    private void consultarReniec() {
        try {
            RonaldmichaConsultaReniec.DatosReniec datos = contexto.reniec().consultar(numDoc.getText());
            apPaterno.setText(datos.apPaterno());
            apMaterno.setText(datos.apMaterno());
            nombres.setText(datos.nombres());
        } catch (RonaldmichaConsultaReniec.ServicioNoConectadoException e) {
            RonaldmichaToast.error(this, e.getMessage());
        }
    }

    void mostrarErrores(List<String> lista) {
        errores.setText(String.join("\n", lista.stream().map(m -> "• " + m).toList()));
        mostrar(errores, !lista.isEmpty());
    }

    RonaldmichaTrabajador leer() {
        return new RonaldmichaTrabajador(original == null ? null : original.id(), original == null ? null : original.codigo(),
                tipoDoc.getValue(), numDoc.getText(), apPaterno.getText(), apMaterno.getText(), nombres.getText(),
                celular.getText(), correo.getText(), fechaIngreso.getValue(), fechaCese.getValue(),
                textoCombo(area), cargo.getText(), textoCombo(tipoContrato), regimen.getValue(),
                centroCosto.getText(), sucursal.getText(), dec(sueldo), decCero(movilidad), salud.getValue(),
                sistema.getValue(), cuspp.getText(), tipoComision.getValue(), asigFamiliar.isSelected(),
                vidaLey.isSelected(), tipoPago.getValue(), bancoSueldo.getText(), ctaSueldo.getText(),
                bancoCts.getText(), ctaCts.getText(), fechaNacimiento.getValue(), hijosEscritos(), direccion.getText(),
                categoria.getValue());
    }

    RonaldmichaHorario leerHorario() {
        try {
            LocalTime ingreso = hora(horaIngreso);
            LocalTime salida = hora(horaSalida);
            Set<DayOfWeek> elegidos = new TreeSet<>();
            dias.forEach((d, b) -> {
                if (b.isSelected()) {
                    elegidos.add(d);
                }
            });
            return new RonaldmichaHorario(ingreso, salida, entero(refrigerio, 60), entero(tolerancia, 0), elegidos,
                    noFiscalizado.isSelected());
        } catch (DateTimeParseException | NumberFormatException e) {
            return null;
        }
    }

    private void cargar(RonaldmichaTrabajador t, RonaldmichaHorario h) {
        if (t == null) {
            codigo.setText(contexto.trabajadores().siguienteCodigo());
            tipoDoc.setValue(RonaldmichaTipoDocumento.DNI);
            regimen.setValue(RonaldmichaRegimenLaboral.GENERAL);
            salud.setValue(RonaldmichaSalud.ESSALUD);
            tipoPago.setValue(RonaldmichaTipoPago.DEPOSITO);
            movilidad.setText("0.00");
            hijos.setText("0");
            categoria.setValue(RonaldmichaCategoria.EMPLEADO);
        } else {
            codigo.setText(t.codigo());
            tipoDoc.setValue(t.tipoDoc());
            numDoc.setText(t.numDoc());
            apPaterno.setText(t.apPaterno());
            apMaterno.setText(t.apMaterno());
            nombres.setText(t.nombres());
            celular.setText(t.celular());
            correo.setText(t.correo());
            fechaIngreso.setValue(t.fechaIngreso());
            fechaCese.setValue(t.fechaCese());
            area.setValue(t.area());
            cargo.setText(t.cargo());
            tipoContrato.setValue(t.tipoContrato());
            regimen.setValue(t.regimen());
            centroCosto.setText(t.centroCosto());
            sucursal.setText(t.sucursal());
            sueldo.setText(t.sueldo().toPlainString());
            movilidad.setText(t.movilidad().toPlainString());
            asigFamiliar.setSelected(t.asigFamiliar());
            vidaLey.setSelected(t.vidaLey());
            salud.setValue(t.salud());
            sistema.setValue(t.sistemaPension());
            cuspp.setText(t.cuspp());
            tipoComision.setValue(t.tipoComision());
            tipoPago.setValue(t.tipoPago());
            bancoSueldo.setText(t.bancoSueldo());
            ctaSueldo.setText(t.ctaSueldo());
            bancoCts.setText(t.bancoCts());
            ctaCts.setText(t.ctaCts());
            fechaNacimiento.setValue(t.fechaNacimiento());
            hijos.setText(String.valueOf(t.hijos()));
            direccion.setText(t.direccion());
            categoria.setValue(t.categoria());
        }
        RonaldmichaHorario horario = h != null ? h : new RonaldmichaHorario(LocalTime.of(8, 0), LocalTime.of(17, 0), 60, 0,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                false);
        horaIngreso.setText(horario.horaIngreso().toString());
        horaSalida.setText(horario.horaSalida().toString());
        refrigerio.setText(String.valueOf(horario.refrigerioMin()));
        tolerancia.setText(String.valueOf(horario.toleranciaMin()));
        dias.forEach((d, b) -> b.setSelected(horario.diasLaborables().contains(d)));
        noFiscalizado.setSelected(horario.noFiscalizado());
    }

    private static <T> ComboBox<T> combo(T[] valores, Function<T, String> texto) {
        ComboBox<T> c = new ComboBox<>();
        c.getItems().setAll(valores);
        c.setMaxWidth(Double.MAX_VALUE);
        c.setConverter(new StringConverter<>() {
            @Override
            public String toString(T v) {
                return v == null ? "" : texto.apply(v);
            }

            @Override
            public T fromString(String s) {
                return null;
            }
        });
        return c;
    }

    private static DatePicker fecha() {
        DatePicker d = new DatePicker();
        d.setPromptText("dd/mm/aaaa");
        d.setMaxWidth(Double.MAX_VALUE);
        d.setConverter(new StringConverter<>() {
            @Override
            public String toString(java.time.LocalDate f) {
                return RonaldmichaFormatos.fecha(f);
            }

            @Override
            public java.time.LocalDate fromString(String s) {
                try {
                    return s == null || s.isBlank() ? null : RonaldmichaFormatos.leerFecha(s);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });
        return d;
    }

    private static TextField num() {
        TextField t = new TextField();
        t.getStyleClass().add("num");
        return t;
    }

    private static GridPane grilla(int columnas) {
        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(12);
        for (int i = 0; i < columnas; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / columnas);
            g.getColumnConstraints().add(c);
        }
        return g;
    }

    private static Node seccion(String titulo, Node... contenido) {
        Label l = new Label(titulo);
        l.getStyleClass().add("title-md");
        VBox caja = new VBox(12, l);
        caja.getChildren().addAll(contenido);
        caja.getStyleClass().add("form-section");
        return caja;
    }

    private static VBox campo(String texto, Node control) {
        if (control instanceof javafx.scene.layout.Region r) {
            r.setMaxWidth(Double.MAX_VALUE);
        }
        return new VBox(6, etiqueta(texto), control);
    }

    private static Label etiqueta(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("field-label");
        return l;
    }

    private static Label titulo(String texto) {
        Label l = linea(texto);
        l.getStyleClass().add("texto-info-negrita");
        return l;
    }

    private static Label totalLinea(String texto) {
        Label l = titulo(texto);
        l.getStyleClass().add("num");
        return l;
    }

    private static Label linea(String texto) {
        Label l = new Label(texto);
        l.setWrapText(true);
        l.getStyleClass().add("texto-info");
        return l;
    }

    private static void mostrar(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    private static String base(AportePension a) {
        return a.asignacionFamiliar().signum() > 0
                ? " sobre " + RonaldmichaFormatos.moneda(a.remuneracion()) + " (básico + asignación familiar)"
                : "";
    }

    private static String pct(BigDecimal valor) {
        return valor == null ? "" : valor.stripTrailingZeros().toPlainString() + "%";
    }

    private static String horas(BigDecimal h) {
        return h.stripTrailingZeros().toPlainString();
    }

    private static String textoCombo(ComboBox<String> combo) {
        String escrito = combo.getEditor() == null ? null : combo.getEditor().getText();
        return escrito != null && !escrito.isBlank() ? escrito : combo.getValue();
    }

    private static LocalTime hora(TextField campo) {
        String t = campo.getText() == null ? "" : campo.getText().trim();
        return LocalTime.parse(t.length() == 4 ? "0" + t : t);
    }

    private int hijosEscritos() {
        try {
            return entero(hijos, 0);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int entero(TextField campo, int porDefecto) {
        String t = campo.getText() == null ? "" : campo.getText().trim();
        return t.isEmpty() ? porDefecto : Integer.parseInt(t);
    }

    private static BigDecimal dec(TextField campo) {
        String t = campo.getText() == null ? "" : campo.getText().replace(",", "").trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decCero(TextField campo) {
        String t = campo.getText() == null ? "" : campo.getText().trim();
        return t.isEmpty() ? BigDecimal.ZERO : dec(campo);
    }
}
