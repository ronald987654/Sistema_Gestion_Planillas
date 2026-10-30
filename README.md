# Planilla Perú

Aplicación de escritorio para la planilla de remuneraciones de una empresa peruana: trabajadores, asistencia y
horas extra, planilla mensual, gratificaciones, CTS, vacaciones, liquidaciones, boletas y reportes (PLAME,
AFPnet, pagos), portal del trabajador, usuarios con doble control y auditoría.

Java 21 · JavaFX 21 · SQLite (Flyway) · Apache POI · OpenPDF · jBCrypt · JUnit 5 + AssertJ + JaCoCo.

> **Datos de demostración.** La empresa, los trabajadores y los usuarios que trae la base son ficticios.
> Antes de usar el sistema con datos reales lea la sección [Validar con un contador antes de producción](#validar-con-un-contador-antes-de-producción).

---

## Requisitos

- **JDK 21** (por ejemplo, el que descarga IntelliJ IDEA: *File → Project Structure → SDK → Download JDK*).
  No hace falta instalar Maven: el proyecto trae el *wrapper* `mvnw` / `mvnw.cmd`.
- Para el instalador `.msi`: **WiX Toolset 3.x** (ver [Empaquetar](#empaquetar-instalador-para-windows)).

La base de datos se crea sola en `%USERPROFILE%\planilla.db` (carpeta del usuario) la primera vez que se abre
el programa; los registros (*logs*) quedan en `%USERPROFILE%\.planillaperu\logs\planilla.log`.

## Compilar y probar

```powershell
# Windows (PowerShell o CMD); defina JAVA_HOME con un JDK 21 si no está definido
.\mvnw.cmd clean verify
```

```bash
# Git Bash, Linux o macOS
./mvnw clean verify
```

`verify` compila, ejecuta todas las pruebas y genera el informe de cobertura de JaCoCo en
`target/site/jacoco/index.html`. La compilación **falla si el paquete `calc` (motor de cálculo) baja de 85 %**
de líneas o de ramas cubiertas. Una de las pruebas (`RonaldmichaPlanillaRendimientoTest`) crea 500 trabajadores ficticios
y exige que la planilla del mes se calcule en menos de 1 segundo.

## Ejecutar

### Desde IntelliJ IDEA

1. *File → Open…* y elija la carpeta `planilla-peru` (la que tiene `pom.xml`). IntelliJ lo importa como proyecto
   Maven sin pasos manuales.
2. Verifique que el SDK del proyecto sea un JDK 21 (*File → Project Structure → Project*).
3. Opción A: abra `src/main/java/com/planillaperu/RonaldmichaLauncher.java` y pulse ▶ junto a `main`.
   Opción B: en la ventana *Maven*, *Plugins → javafx → javafx:run*.

### Desde la terminal

```powershell
.\mvnw.cmd javafx:run
```

### Usuarios de demostración

| Acceso en el login | Usuario | Contraseña | Rol |
|---|---|---|---|
| Administrador | `admin` | `admin123` | ADMIN (también es la trabajadora T0001) |
| Administrador | `aprobador` | `aprob123` | APROBADOR / gerencia (también es la trabajadora T0003) |
| Trabajador | `40000002` | `123456` | TRABAJADOR (Carlos, T0002; entra con su DNI) |

Tras 5 intentos fallidos seguidos la cuenta se bloquea 15 minutos (el administrador puede desbloquearla en
*Usuarios y seguridad*). La sesión se cierra tras 30 minutos sin actividad (configurable).
En el login, *Restablecer datos de demostración* borra todo y vuelve a cargar estos datos.

### Atajos de teclado

| Atajo | Acción |
|---|---|
| `Ctrl+F` | Ir al buscador global de trabajadores |
| `Ctrl+N` | Nuevo trabajador (solo administrador) |
| `Ctrl+E` | Exportar en la vista actual (planilla, trabajadores, asistencia, gratificaciones, CTS, reportes, resumen anual, auditoría) |
| `Esc` | Cerrar el modal abierto |

### Copia de seguridad

En el menú del usuario (arriba a la derecha), el administrador tiene:

- **Respaldar base de datos…**: guarda una copia completa y coherente de `planilla.db` con la fecha en el nombre
  (`planilla_2026-10-20_1530.db`).
- **Restaurar base de datos…**: reemplaza la base por una copia. Antes guarda automáticamente la base actual como
  `planilla_antes_de_restaurar_<fecha>.db` junto a `planilla.db`, aplica las migraciones que le falten a la copia
  y vuelve al login. No acepta archivos que no sean copias de Planilla Perú ni copias de una versión más nueva.

Ambas operaciones quedan en la auditoría.

## Empaquetar (instalador para Windows)

El script `empaquetar.ps1` compila, copia las dependencias a `target/instalador` y ejecuta `jpackage` con el
ícono `src/main/packaging/planilla-peru.ico`, el nombre **Planilla Perú**, accesos directos en el escritorio y en
el menú Inicio, y la opción de elegir la carpeta de instalación.

```powershell
$env:JAVA_HOME = 'C:\ruta\a\jdk-21'
.\empaquetar.ps1                  # target\dist\Planilla Perú-0.1.0.msi
.\empaquetar.ps1 -Tipo exe        # instalador .exe
.\empaquetar.ps1 -Tipo app-image  # carpeta target\dist\Planilla Perú\ con Planilla Perú.exe (sin instalar)
```

- `.msi` y `.exe` necesitan **WiX Toolset 3.x** (`candle.exe` y `light.exe` en el `PATH`); descárguelo de
  <https://wixtoolset.org/>. Sin WiX, el script se detiene con un aviso; `-Tipo app-image` funciona sin WiX.
- El paquete incluye su propio Java (no hace falta instalar Java en el equipo destino).
- Si PowerShell bloquea el script: `powershell -ExecutionPolicy Bypass -File .\empaquetar.ps1`.
- El ícono se genera con `java tools/RonaldmichaGenerarIcono.java` (también deja `icono.png` para la ventana).

## Estructura

| Paquete | Contenido |
|---|---|
| `domain` | Entidades y enums (Trabajador, Empresa, Parámetros, Rol, Permiso…) |
| `calc` | Motor de cálculo puro, sin JavaFX ni base de datos (planilla, gratificación, CTS, vacaciones, liquidación, renta de 5.ª, horas extra, feriados, obligaciones) |
| `repository` | DAOs JDBC sobre SQLite; migraciones Flyway en `src/main/resources/db/migration` |
| `service` | Reglas de negocio, permisos (matriz por rol), auditoría, cierre de periodos, reportes, respaldo |
| `export` | PDF, Word, Excel, TXT y ZIP (PLAME) |
| `ui` | Controladores JavaFX, componentes y tema CSS (`src/main/resources/css/theme.css`) |

Los parámetros legales (RMV, UIT, tasas de AFP, EsSalud, etc.) se leen de la tabla de **parámetros versionados**
(vista *Parámetros*); no están escritos en el código.

---

## Validar con un contador antes de producción

El sistema se construyó con fines académicos. Estos puntos **deben revisarse con un contador o especialista en
planillas** (y contra las normas y publicaciones vigentes de SUNAT, SBS y MTPE) antes de usarlo con datos reales:

1. **Códigos de suspensión PLAME (archivo `.snl`).** El ZIP de PLAME usa por ahora tres códigos fijos:
   `07` falta (inasistencia injustificada), `21` incapacidad temporal subsidiada y `23` vacaciones
   (`RonaldmichaReportesService`, sección PLAME). Faltan otros tipos de suspensión (licencias con y sin goce, descanso
   médico de los primeros 20 días, maternidad, etc.). Confirme cada código con la tabla vigente de tipos de
   suspensión de la relación laboral de SUNAT antes de importar el archivo en el PDT PLAME. Revise también los
   códigos de conceptos de ingresos, descuentos y aportes que aparecen en boletas y en el `.rem`.

2. **Tasas AFP vigentes.** Las comisiones por flujo y mixta de cada AFP, el aporte obligatorio, la prima de
   seguro y la remuneración máxima asegurable de los datos de demostración son **referenciales**. Cárguelas en
   *Parámetros* (versionadas por mes) con los valores que publica la SBS para el periodo, y vuelva a revisarlas
   cuando cambien. Lo mismo para la RMV, la UIT, EsSalud, ONP y el seguro Vida Ley.

3. **Renta de quinta categoría: cálculo mensual exacto (art. 40 del Reglamento de la Ley del Impuesto a la
   Renta).** `RonaldmichaRentaQuintaCalculator` hace una versión **simplificada**: proyecta el año con la remuneración del
   mes, resta 7 UIT, aplica la escala progresiva y retiene un doceavo. El procedimiento del art. 40 recalcula la
   retención cada mes con las remuneraciones ya pagadas, la proyección de los meses que faltan (incluidas
   gratificaciones), las retenciones de meses anteriores y un divisor que cambia según el mes (12 hasta marzo;
   luego abril, mayo a julio, agosto, setiembre a noviembre y diciembre como ajuste final), además de las rentas
   de otros empleadores y las deducciones adicionales de 3 UIT. Debe implementarse y validarse con casos reales
   antes de producción; el certificado de rentas y retenciones también depende de este cálculo.

4. **Remuneración mínima nocturna.** El trabajo en horario nocturno (entre las 10:00 p. m. y las 6:00 a. m.) no
   puede pagarse por debajo de la remuneración mínima nocturna (RMV más una sobretasa). El sistema **solo alerta**
   en *Asistencia* cuando una salida pasa de las 10:00 p. m.; **no calcula ni verifica** esa remuneración mínima
   ni la sobretasa. Confirme la base legal y el porcentaje vigentes, y defina con el contador cómo registrar a
   quienes trabajan en turno nocturno.

Otros supuestos a confirmar: la fecha del PLAME del calendario del Inicio es referencial (siga el cronograma
oficial de SUNAT según el último dígito del RUC), y los datos de la empresa de demostración están incompletos
(sin RUC).
