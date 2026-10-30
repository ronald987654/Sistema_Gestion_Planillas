# Cálculos de Planilla Perú

Fórmula de cada concepto, su base legal y el ejemplo numérico con el que se prueba
(`src/test/java/com/planillaperu/service/RonaldmichaCalculosOficialesTest.java`, casos C1 a C34).

**Reglas generales**

- Dinero siempre con `BigDecimal`, escala 2, `RoundingMode.HALF_UP` (`common/RonaldmichaDinero`).
- Cada concepto se redondea antes de sumarse con otro (por ejemplo, CTS = 526.17 + 43.85, no 570.0139 → 570.01).
- Mes comercial de 30 días; jornada del horario del trabajador (8 h en los ejemplos).
- Los parámetros legales salen de la versión de parámetros vigente en el mes (`parametros_version`), nunca del código.

**Parámetros de los ejemplos (octubre 2026):** RMV 1,230 · UIT 5,500 · asignación familiar 10 % · EsSalud 9 % ·
EPS 6.75 % · ONP 13 % · AFP aporte 10 % · prima 1.37 % con tope RMA 12,732.70 · comisión de flujo Integra 1.55 % ·
bonificación extraordinaria 9 % (6.75 % con EPS).

---

## 1. Planilla mensual (`calc/RonaldmichaPlanillaCalculator`)

| Concepto | Fórmula | Base legal | Ejemplo |
|---|---|---|---|
| Días del mes | Desde el día de ingreso (si ingresa en el mes) hasta el cese (30 si cesa el último día) | Mes comercial de 30 días | C9: ingreso el 16 → 15 días |
| Asignación familiar | RMV × 10 %, proporcional a los días del mes | Ley 25129 | 1,230 × 10 % = 123.00 |
| Básico | sueldo / 30 × (días − faltas − vacaciones − días subsidiados) | D.S. 003-97-TR | C8: 1,230 / 30 × 28 = 1,148.00 · C9: 1,230 / 30 × 15 = 615.00 |
| Remuneración afecta | básico + vacacional + AF + HE + feriados + comisiones + bonificación + otros afectos − tardanza | — | C1: 1,353.00 |
| Valor hora | (sueldo + AF) / 30 / horas de la jornada (se muestra con 4 decimales) | D.S. 007-2002-TR | C6: 1,353 / 30 / 8 = 5.6375 |
| Horas extra | por día, las 2 primeras horas × 1.25 y desde la 3.ª × 1.35 | D.S. 007-2002-TR, art. 10 | C6: 2 h = 14.09 · 1 h = 7.61. C7: 1 día de 3 h = 2 h al 25 % + 1 h al 35 %; 2 días de 2 h = 4 h al 25 % |
| Tardanza (se descuenta) | valor hora / 60 × minutos | — | C6: 5.6375 × 25 / 60 = 2.35 |
| Feriado o descanso trabajado sin descanso sustitutorio | remuneración diaria × 2 | D.Leg. 713, arts. 3 y 9 | C6: 1,353 / 30 × 2 = 90.20 |
| Subsidio por incapacidad | (sueldo + AF) / 30 × días subsidiados; afecto a pensión y renta, no a EsSalud | Ley 26790 | C12: 205.00 |
| Movilidad supeditada a la asistencia | movilidad / 30 × días laborados; sin aportes ni renta | D.S. 001-97-TR, art. 19 | C13 |
| ONP | base de pensión × 13 % | D.L. 19990 | C1: 1,353 × 13 % = 175.89 → neto 1,177.11 |
| AFP aporte | base × 10 % | D.S. 054-97-EF | C2: 135.30 |
| AFP prima | min(base, RMA) × 1.37 % | D.S. 054-97-EF; tope de la SBS | C2: 18.54 · C4: 12,732.70 × 1.37 % = 174.44 (no 205.50) |
| AFP comisión | flujo: base × tasa; **mixta: 0 en la planilla** (se cobra sobre el saldo) | Ley 29903 | C2: 20.97 (total 174.81, neto 1,178.19) · C3: Profuturo mixta = 0.00 → neto 1,949.86 |
| Base de pensión | remuneración afecta + subsidio | — | C12: 1,025 + 205 = 1,230.00 |
| EsSalud | max(afecta, RMV / 30 × (días − subsidiados)) × 9 % (6.75 % con EPS) | Ley 26790, art. 6; Ley 28791 | C1/C2: 121.77 · C3: 198.00 · C5 (tiempo parcial, básico 600): 1,230 × 9 % = 110.70 · C10 (EPS, 3,000): 202.50 · C12: 1,025 × 9 % = 92.25 |
| SIS (microempresa) | monto fijo del parámetro; sin EsSalud | D.Leg. 1086 / D.S. 013-2013-PRODUCE | C11: 15.00 |
| Neto | ingresos − (pensión + renta + adelantos + otros descuentos) | — | C1: 1,177.11 |

Microempresa (C11): no genera gratificación ni CTS (factor 0) y tiene 15 días de vacaciones.

---

## 2. Renta de quinta categoría: art. 40 del Reglamento de la LIR (`calc/RonaldmichaRentaQuintaCalculator`)

**Renta anual proyectada** = remuneración ordinaria del mes × meses que faltan del año (incluido el mes)
\+ gratificaciones de julio y diciembre que faltan recibir (con su bonificación extraordinaria)
\+ todo lo percibido en los meses anteriores del año (incluidas las gratificaciones ya pagadas)
\+ rentas de quinta de otros empleadores declaradas por el trabajador.

**Renta neta** = proyectada − 7 UIT. La deducción adicional de hasta 3 UIT **no** la aplica el empleador.

**Escala acumulativa** (cada tramo redondeado): hasta 5 UIT 8 % · más de 5 hasta 20 UIT 14 % · más de 20 hasta 35 UIT 17 % ·
más de 35 hasta 45 UIT 20 % · más de 45 UIT 30 %.

**Retención del mes**

| Mes | Retención |
|---|---|
| Enero a marzo | impuesto anual / 12 |
| Abril | (impuesto − retenido de enero a marzo) / 9 |
| Mayo a julio | (impuesto − retenido de enero a abril) / 8 |
| Agosto | (impuesto − retenido de enero a julio) / 5 |
| Setiembre a noviembre | (impuesto − retenido de enero a agosto) / 4 |
| Diciembre | impuesto definitivo con lo realmente percibido − retenido de enero a noviembre |

- **Pago extraordinario** del mes (la «bonificación» de la planilla: bono, utilidades, gratificación extraordinaria):
  se suma a la retención ordinaria el impuesto adicional que genera sobre la renta proyectada.
- **Otros empleadores**: su renta entra a la proyección; sus retenciones se restan junto con las del empleador desde abril.
  Se registran en el detalle de la planilla del trabajador (sección «Renta de quinta categoría»).
- **Historial**: la planilla de cada mes usa lo percibido y retenido en los meses anteriores del año. Al cerrar un mes se
  guardan sus retenciones (`renta_quinta_mes`). Al reabrirlo se borran, y al volver a cerrarlo se recalculan en cascada
  los meses siguientes ya cerrados (queda en la auditoría como `RENTA_QUINTA_RECALCULADA`).
- Gratificación del mes de pago (julio, diciembre): la real de Beneficios. Las futuras se proyectan con
  sueldo + AF × meses completos del semestre / 6 × factor del régimen × (1 + bonificación).

**Ejemplos**

- C14 (básico 7,800, sin AF, AFP, régimen general), enero: 7,800 × 12 + 2 × 7,800 × 1.09 = 93,600 + 17,004 = 110,604
  → neta 72,104 → 27,500 × 8 % + 44,604 × 14 % = 2,200.00 + 6,244.56 = 8,444.56 → retención 8,444.56 / 12 = **703.71**.
- C15, el mismo trabajador todo el año: 703.71 × 3 · 703.71 (abril) · 703.72 × 3 (mayo a julio) · 703.71 (agosto) ·
  703.71 × 3 (setiembre a noviembre) · 703.72 (diciembre) = **8,444.56**.
- C16: básico 1,230 → 0.00 todo el año (no supera 7 UIT).
- Bono de 10,000 en marzo: retención de marzo 703.71 + 10,000 × 14 % = 2,103.71; el año suma 9,844.56.
- C17: el certificado de rentas y retenciones (Reportes → Certificado de retenciones, art. 45) muestra renta bruta 110,604.00,
  deducción de 7 UIT 38,500.00, renta neta 72,104.00, impuesto 8,444.56 y retenido 8,444.56 (= suma de las retenciones mensuales).

---

## 3. Gratificación (Ley 27735, D.S. 005-2002-TR, Ley 30334) (`calc/RonaldmichaGratificacionCalculator`)

- **Computable** = básico + AF + promedio de la variable (horas extra, feriados trabajados, comisiones), que solo entra si
  se percibió en 3 o más meses del semestre: suma ÷ 6 (C23).
- **Gratificación** = computable / 6 × meses calendario completos − computable / 6 / 30 × faltas (cada parte redondeada)
  × factor del régimen (general 1, pequeña empresa 0.5, microempresa 0).
- **Bonificación extraordinaria** = gratificación × 9 % (6.75 % con EPS). Gratificación y bonificación no pagan ONP, AFP
  ni EsSalud, pero sí entran a la renta de quinta (C24).

| Caso | Cálculo | Resultado |
|---|---|---|
| C18 general, 1,230 + AF | 1,353 + 121.77 | 1,474.77 |
| C19 pequeña, 2,200 | 2,200 × 50 % = 1,100 + 99 | 1,199.00 |
| C20 EPS, 3,000 | 3,000 × 6.75 % | 202.50 |
| C21 2 faltas | 1,353.00 − 1,353 / 6 / 30 × 2 = 1,353.00 − 15.03 | 1,337.97 |
| C22 ingreso 10/03 | abril, mayo y junio: 1,353 / 6 × 3 | 676.50 |
| C23 variable en 2 meses / en 3 meses (100 + 200 + 300) | no entra / 600 ÷ 6 = 100 | 1,353.00 / 1,453.00 |

---

## 4. CTS (D.S. 001-97-TR) (`calc/RonaldmichaCtsCalculator`)

- **Computable** = básico + AF + promedio variable (regla de 3 meses) + 1/6 de la gratificación del semestre.
- **CTS** = computable / 12 × meses + computable / 360 × días − computable / 360 × faltas (cada parte redondeada)
  × factor (general 1, pequeña empresa 0.5, microempresa 0).

| Caso | Cálculo | Resultado |
|---|---|---|
| C25 noviembre (mayo–octubre) | 1,353 + 1,353 / 6 = 1,578.50 → 1,578.50 / 12 × 6 | 789.25 |
| C26 pequeña / micro | 789.25 × 50 % / 0 | 394.63 / 0.00 |
| C27 4 meses y 10 días | 526.17 + 43.85 | 570.02 |
| C28 3 faltas | 789.25 − 1,578.50 / 360 × 3 = 789.25 − 13.15 | 776.10 |

---

## 5. Vacaciones y liquidación (`calc/RonaldmichaLiquidacionCalculator`)

| Concepto | Fórmula | Base legal | Ejemplo |
|---|---|---|---|
| Vacaciones truncas | (computable / 12 × meses + computable / 360 × días, cada parte redondeada) × (días del régimen / 30) | D.Leg. 713, art. 22 | C29: 676.50 + 56.38 = 732.88; pequeña y micro: 366.44 |
| Vacaciones no gozadas | computable / 30 × días pendientes | D.Leg. 713, art. 23 | C30: 1,353 / 30 × 30 = 1,353.00 |
| Indemnización vacacional (aparte, sin aportes) | computable / 30 × días de periodos vencidos (pendientes − días del último periodo ganado) | D.Leg. 713, art. 23 c) | C30: 60 días pendientes, 30 vencidos → 1,353.00 |
| Pensión sobre vacaciones | (truncas + no gozadas) × tasa ONP o AFP (aporte + prima + comisión de flujo), con su tasa en la liquidación; sin EsSalud | D.L. 19990, D.S. 054-97-EF | C31: base 3,438.88 → ONP 13 % = 447.05; AFP 10 % = 343.89 + 1.37 % = 47.11 + 1.55 % = 53.30 |
| Indemnización por despido arbitrario | general 1.5 remuneraciones por año (tope 12); pequeña 20 remuneraciones diarias por año (tope 120); micro 10 (tope 90); fracciones por dozavos y treintavos | D.S. 003-97-TR, art. 38; D.S. 013-2013-PRODUCE | C32: 1.5 × 1,353 × 2.5 = 5,073.75; pequeña 2,255.00; micro 1,127.50 |
| Renuncia, término de contrato, mutuo disenso | indemnización 0 | — | C33 |
| Neto en letras | `RonaldmichaNumeroALetras.texto` | — | C34: 1,364.07 → «Mil trescientos sesenta y cuatro con 07/100 soles»; 0.50 → «Cero con 50/100 soles»; 21,000.00 → «Veintiún mil con 00/100 soles» |

---

## 6. Coherencia entre módulos (pruebas automáticas)

- El neto de cada trabajador es el mismo en la boleta, la planilla, los pagos masivos (TXT y Excel) y el Excel de la planilla.
- Boleta de pago (`export/doc/RonaldmichaBoletaPlantilla`, modelo del art. 19 del D.S. 001-98-TR): «Sueldo Computable» = sueldo / 30 ×
  días del vínculo (sin vacaciones ni días subsidiados, que van en su propia línea); las faltas (sueldo / 30 × días) y las
  tardanzas van en descuentos. Remuneraciones y descuentos suben por igual, así que el neto es exactamente el de la
  planilla, y las bases de ONP, AFP, EsSalud, Vida Ley y renta siguen siendo la remuneración afecta real.
  Caso de control (AFP Integra flujo, 2,800, movilidad 150, cese 21/10/2026): computable 1,960.00 + movilidad 105.00 =
  2,065.00; AFP 196.00 + 30.38 + 26.85 = 253.23; neto 1,811.77; EsSalud 176.40; Vida Ley 29.99.
- Los totales de la planilla son la suma de las filas; en cada fila, sus conceptos suman sus totales y neto = ingresos − descuentos.
- PLAME: la suma de los conceptos del archivo `.rem` por código es igual a la suma de la planilla por código.
- AFPnet: la remuneración asegurable y el aporte, la prima y la comisión de cada afiliado son los de la planilla.
- Un mes cerrado no cambia: no se puede registrar una versión de parámetros que lo afecte, y una versión posterior no lo toca.
  Su renta queda guardada.
- Una falta o una hora extra cambian la planilla y la boleta; una falta cambia además la gratificación y la CTS de su semestre.
