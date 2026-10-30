# Revisión integral final · Planilla Perú

Registro de avance por fases. Si el trabajo se interrumpe, continuar desde la primera fase marcada como **pendiente**.

| Fase | Estado |
|---|---|
| 1 · Diagnóstico real | hecho |
| 2 · Sistema de diseño único (theme.css) | hecho |
| 3 · Componentes reutilizables | hecho |
| 4 · Funcionamiento y dinamismo | hecho |
| 5 · Pulido de cada vista | en curso |
| 6 · Verificación final | pendiente |

---

## Fase 1 · Diagnóstico real (hecho)

**Método.** `NavegacionSmokeTest` recorre todas las vistas con ADMIN, APROBADOR y TRABAJADOR (13 + 13 + 5 ítems del menú),
pulsa los 13 reportes, abre formularios, modales, detalle de planilla, edición de beneficios, liquidación, vacaciones,
permisos, auditoría y el ancho angosto, y toma capturas con `node.snapshot`. Ahora guarda también la página completa
(`*-completo.png`, con el scroll desplegado). Capturas: `docs/revision/antes/` (179 imágenes).
Las pruebas de servicio (`ReportesServiceTest`, `BoletaPagoTest`, `CalculosOficialesTest`, …) cubren las exportaciones.

### Transversal (todas las vistas)
- **Encabezado de alto variable**: 64 px en Inicio, Trabajadores, Asistencia y Vacaciones; 74 px en Planilla, Resumen
  anual, Gratificaciones, CTS, Liquidaciones, Empresa, Parámetros y Usuarios (el título se mueve al cambiar de vista).
- **Combo de año cortado**: en Asistencia se ve «2…»; con ventana angosta (1000 px) el año queda vacío y el mes «Oct…».
- **Tablas con columnas truncadas** («DNI 40…», «S/ 2,…», «02/0…», botones «E…», «Liq…») y **filas vacías estiradas**
  hasta el alto del contenedor (Trabajadores, Parámetros, Gratificaciones, CTS).
- **Encabezados de tabla mezclados**: unas columnas en IBM Plex Mono («Código», «Documento», «Meses») y otras en Sans.
- **Tarjetas sin sombra** y sin alto de contenido uniforme; contenido sin ancho máximo (en pantallas anchas se estira).
- **Colores escritos a mano**: 18 colores hex en `InicioController` y ~40 en reglas de `theme.css` (panel oscuro,
  aviso ámbar, gráficos, celdas de futuro/gratificación) en lugar de variables.
- **Botones de exportar sin color por formato** (todos blancos) y sin indicador de «Generando…».
- **Inputs de 44 px** (se pide 40 px); foco con borde de 1 px y sin halo; el error solo pinta el fondo.

### Por vista
| Vista | No funciona | A medias | Se ve mal |
|---|---|---|---|
| Inicio | — | «Actividad reciente» muestra el código crudo (`INICIO_SESION`) | Títulos de acciones rápidas cortados («Nuevo trabaj…», «Planilla en Ex…») |
| Trabajadores | — | El formulario no valida en vivo | Barra de filtros en 2 filas; columnas y botones truncados; chips de régimen cortados; filas vacías |
| Asistencia | — | Con «Sin registro» el resumen marca 18 faltas aunque la planilla no las descuenta (dato engañoso) | Tarjeta de resumen desborda a la derecha; columnas «Tard…», «Sobret…» |
| Vacaciones | — | — | Columnas «Vend…», «Planif…», fecha «02/05/20…» truncadas |
| Planilla mensual | — | — | KPI «Renta de 5ta» sola en una segunda fila; columnas de resumen y detallada truncadas |
| Resumen anual | — | — | Totales «S/ 25,59…» truncados |
| Reportes | — | — | (la boleta ya se rediseñó; bien) |
| Gratificaciones / CTS | — | — | Selector «Ma…» (Mayo) y subtítulo «15/11/20…» cortados; filas vacías |
| Liquidaciones | — | — | Columna «Docum…» cortada |
| Datos de la empresa | — | — | Formulario no ocupa el ancho; barra superior propia distinta del resto |
| Parámetros | — | — | Tabla de comisiones AFP con scroll interno (oculta Profuturo); «1.6 %» vs «1.47 %» sin 2 decimales |
| Usuarios y seguridad | — | — | (la pestaña Auditoría salía en blanco en la captura: era el fade en curso, no un error) |
| Portal (Mis datos) | — | No muestra fecha de nacimiento, hijos ni dirección (datos nuevos de la ficha) | — |
| Portal (Mis documentos, Mi asistencia, Mis vacaciones) | — | — | Tabla de boletas con scroll interno |

### Funcionamiento
- Las 278 pruebas automáticas pasan (cálculos, reportes, exportaciones, navegación de los 3 roles).
- El periodo al abrir es el mes de hoy, no «el primer mes no cerrado».
- Los cambios se propagan con listeners puntuales (periodo, aprobaciones); no hay un bus de eventos de dominio.
- Búsqueda de datos de ejemplo escritos fijos en el código (2017, MAYO, JURÍDICA SAC, MARGOT): **ninguno encontrado**.

---

## Fase 2 · Sistema de diseño único (hecho)

`mvn clean verify`: 278 pruebas en verde.

**Hecho**
- `theme.css` reescrito: todas las variables en `.root` con la paleta pedida (marca, neutros, estados, barra lateral,
  panel oscuro, colores de formato). Los nombres anteriores (`-card`, `-text-muted`, `-error-fg`, …) quedan como alias
  de los nuevos, así ninguna regla se quedó sin color.
- Sin colores escritos a mano en Java ni FXML: los 18 hex de `InicioController` pasaron a tonos `.tono-*` en el CSS;
  los `setStyle` de Empresa, Parámetros y el formulario de trabajador pasaron a clases (`.mensaje-ok`, `.texto-info`…).
  Única excepción deliberada: `VistaBoleta`, que reproduce el documento con las constantes de `DocumentModel.Boleta`.
- Escala tipográfica 24 · 18 · 15 · 14 · 12.5 · 11.5; tarjetas con sombra suave; inputs y botones de 40 px; foco con
  borde de 2 px y halo `-primary-soft`; error con borde `-danger` (`.campo-error`) y `.mensaje-campo` en rojo.
- Tablas: encabezado `-surface-alt` en 11.5 px y **mayúsculas** (JavaFX no tiene `text-transform`: lo hace
  `ui/Estilos`, también para columnas que se agregan después), filas de 40 px, hover y selección `-primary-soft` con
  borde izquierdo `-primary`, totales en negrita sobre `-primary-soft`, negativos en `-danger`.
- Chips: success / warning / neutral / info / holiday (+ danger). Íconos de trazo 1.75.
- Botones por formato: `.btn-pdf`, `.btn-word`, `.btn-excel`, `.btn-txt`, `.btn-zip`, `.btn-correo`.
- Encabezado superior de 64 px fijo en todas las vistas; combos de mes (132 px) y año (96 px) con ancho mínimo
  (ya no se ven «2…» ni vacíos).
- Documentos: `BOLETA_COLOR = #0E5A47` en barras y encabezados; la barra de totales de la boleta y las filas de total
  de todos los documentos PDF/Word usan `-primary-soft` (#E3EFEA) con texto `-primary-strong`. Formato art. 19 intacto.
- `ui/Tareas`: un solo grupo de hilos daemon para el trabajo en segundo plano (resultado de vuelta al hilo de JavaFX).
  Corrigió además un fallo intermitente de la prueba de navegación (una descarga del Inicio seguía usando la base
  temporal cuando JUnit la borraba).

**Pendiente / limitaciones**
- JavaFX no soporta `letter-spacing`: las etiquetas en mayúsculas no llevan espaciado extra.
- `PlanillaRendimientoTest` (500 trabajadores < 1 s) falló una vez con 1,032 ms con la máquina sin memoria libre
  (~1 GB); pasa 3 de 3 veces en aislado. Es sensible a la carga del equipo.

---

## Fase 3 · Componentes reutilizables (hecho)

`mvn clean verify`: 279 pruebas en verde.

**Hecho**
- **ExportButton** (nuevo): color por formato (PDF, Word, Excel, CSV, TXT, ZIP); genera en segundo plano con
  «Generando…» y un indicador; luego «Guardar como» (propone la última carpeta) y toast con **«Abrir carpeta»**; si
  falla, toast rojo con el motivo. Admite un paso previo (p. ej. el modal de observaciones de Reportes).
  En uso en: Reportes (13 tarjetas, «Todos en un PDF»), Planilla (Excel y PDF directos; antes «Exportar» solo llevaba a
  Reportes), Asistencia (plantilla, exportar, mi asistencia), Gratificaciones y CTS (3 reportes), Liquidaciones y
  Vacaciones (PDF/Word por fila), Resumen anual. Trabajadores, Auditoría, Empresa (vista previa PDF), Mis documentos y
  los reportes del modal de liquidación usan el mismo flujo con `Archivos.generarYGuardar`.
- **Toast**: nuevo `Toast.archivo(...)` con el enlace «Abrir carpeta».
- **Chip** (nuevo): un solo mapa estado → color (Abierto/Hecho/Recibido = success · Pendiente/Borrador = warning ·
  Cerrado/Anulado = neutro · Enviado = info · Feriado = holiday). El chip del periodo del encabezado mostraba «Cerrado»
  en ámbar: ahora neutro. Borrador de liquidación pasó a warning. Prueba: `ChipTest`.
- **MoneyCell**: los negativos van en `-danger`.
- **Validación en vivo** (nueva `components/Validacion`): borde rojo y mensaje debajo del campo mientras se escribe
  (desde que el usuario toca el campo); «Guardar» se habilita solo si todo es válido. Aplicada al formulario de
  trabajador (documento, nombres, correo, celular, hijos, fechas, área, cargo, horario, sueldo, movilidad, CUSPP y
  comisión según ONP/AFP) y a Datos de la empresa (RUC, razón social, REMYPE, ubigeo, día de pago, correos). Lo que
  necesita la base (duplicados, RMV del periodo) se sigue validando al guardar. Enter guarda en el formulario.
- **WorkerPicker / WorkerComboBox**: revisados; ya cumplían búsqueda sin tildes, flechas ‹ ›, «cargo · área · régimen ·
  N de M», selección automática del primero y `seleccionadoProperty`. El pool por reporte lo arma `ReportesService`.
- **Modal** (Esc y clic fuera), **ConfirmButton** (dos clics), **EmptyState** (con acción), **KpiCard**: ya existían.

**Pendiente**
- Validación en vivo en los formularios menores (usuario nuevo, parámetros, vacaciones, liquidación).

---

## Fase 4 · Funcionamiento y dinamismo (hecho)

`mvn clean verify`: 282 pruebas en verde (incluye `CalculosOficialesTest` sin cambios).

**Hecho**
- **Periodo global**: ya existía `AppContext.periodoProperty()` y Planilla, Asistencia, Beneficios, Reportes (con la
  boleta) e Inicio se recalculaban al cambiar mes o año. Ahora también **Resumen anual** sigue el año del encabezado.
- **Al abrir, el primer mes no cerrado** (`PeriodoService.periodoDeTrabajo`): el primer periodo abierto registrado (el
  que abre un cierre, o uno reabierto); si no hay, el siguiente al último cerrado; sin cierres, el mes actual. Prueba en
  `PeriodoYDemoServiceTest`.
- **Selector de años dinámico**: va del año de la primera versión de parámetros al año siguiente al actual (o al del
  periodo elegido); antes empezaba en 2025 escrito fijo.
- **Eventos de dominio**: cada acción auditada (`AuditoriaService.registrar`, por donde pasa todo lo importante) publica
  un `Evento` con su tema en `AppContext.eventoProperty()`, siempre en el hilo de JavaFX. Las vistas lo escuchan con
  `WeakChangeListener` y se refrescan una sola vez por pulso (`Refresco`): el **chip del periodo** pasa a «Cerrado» al
  cerrar la planilla (antes seguía «Abierto» hasta cambiar de mes), la campana de aprobaciones se actualiza al
  aprobar o solicitar, Planilla se recarga con cambios de trabajadores, asistencia, parámetros o aprobaciones, Reportes
  actualiza el registro de documentos y la vista previa, e Inicio se refresca con cualquier dato guardado (por ejemplo,
  al terminar una descarga en segundo plano). Prueba: `EventoTest`.
- **Segundo plano**: exportaciones (Fase 3), importación de trabajadores e importación de asistencia (con «Importando…»).
  La carga de cada vista sigue en el hilo de JavaFX: medido con `PlanillaRendimientoTest`, la planilla tarda ~2 ms por
  trabajador (≈15 ms con los 7 de demo, ≈200 ms con 100).
- **Datos escritos fijos**: la fila de ejemplo de la plantilla CSV tenía «01/02/2026»; ahora es el primer día del mes en
  curso. No hay datos de ejemplo tipo 2017, MAYO o nombres de prueba en el código.
- **Asistencia**: con «Sin registro» el resumen ya no muestra «18 faltas» que la planilla no descuenta: muestra «—» y
  «Sin marcaciones: la planilla usa las novedades».
- **Inicio**: «Actividad reciente» muestra textos legibles («Inicio de sesión») en vez del código crudo.
- **Mis datos (portal)**: muestra fecha de nacimiento, hijos, dirección y categoría.
- Módulos de punta a punta: cubiertos por las pruebas de servicio existentes (Trabajadores con doble control e
  importación, Asistencia, Planilla con cierre y reapertura, Beneficios, Vacaciones, Liquidaciones, Reportes, Seguridad,
  portal) y por la prueba de navegación de los 3 roles; el flujo completo de un mes se agrega en la Fase 6.

**Pendiente / limitaciones**
- Cargar una vista con planillas de varios cientos de trabajadores puede pasar de 200 ms; pasarla a segundo plano exige
  rehacer la prueba de navegación (hoy verifica el contenido de forma síncrona).
