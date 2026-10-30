# Planilla Perú — aplicación de escritorio

## Entorno
- Se desarrolla en IntelliJ IDEA con JDK 21. El proyecto debe abrirse e importarse como proyecto Maven sin pasos manuales.
- Para ejecutar: mvn javafx:run (o la configuración de ejecución de Maven en IntelliJ).

## Stack
- Java 21, JavaFX 21 (FXML + CSS), Maven, módulo único com.planillaperu.
- Base de datos SQLite embebida (archivo planilla.db en la carpeta del usuario), acceso con JDBC + patrón DAO/Repository. Migraciones con Flyway.
- Dinero SIEMPRE con BigDecimal, escala 2, RoundingMode.HALF_UP. Nunca double para montos.
- Apache POI (Excel .xlsx y Word .docx), OpenPDF (PDF), jBCrypt (contraseñas), SLF4J + Logback (logs).
- JUnit 5 + AssertJ para pruebas. El motor de cálculo debe tener pruebas unitarias.

## Arquitectura (capas)
- domain: entidades y enums (Trabajador, Empresa, Parametros, Asistencia, Novedad, Planilla, etc.).
- calc: motor de cálculo PURO, sin JavaFX ni base de datos (PlanillaCalculator, GratificacionCalculator, CtsCalculator, VacacionesCalculator, LiquidacionCalculator, RentaQuintaCalculator, HorasExtraCalculator, FeriadosPeru).
- repository: DAOs JDBC por entidad.
- service: reglas de negocio, validaciones, permisos, auditoría, cierre de periodos.
- export: generadores PDF, Word, Excel, TXT, ZIP (PLAME).
- ui: controladores FXML, componentes reutilizables y tema CSS.
- La UI nunca calcula montos: siempre llama a services, que llaman a calc.

## Diseño visual (tokens)
- Tipografía: IBM Plex Sans (texto) e IBM Plex Mono (números, con cifras tabulares). Empaquétalas en resources/fonts.
- Colores: primario #0E5A47 (hover #0A4738), texto #17201B, texto secundario #56625C, fondo #F3F4F1, tarjeta #FFFFFF, borde #DDE2DD, borde de input #C5CDC7, éxito suave #E5F0EB / #0B4A3A, aviso #FFF1DD / #7A4300, error #FBE9E7 / #7A1D14, info #E4ECFA / #1D3F7A, feriado #EFE6F7 / #4E2A73.
- Panel oscuro del dashboard: fondo #121A17, tarjetas #1A2420, borde #26332D, acento verde #26A686, acento naranja #CC6A3D, texto #E6EEE9.
- Barra lateral oscura #0F211B con ítem activo #2E8C6F.
- Radios: tarjetas 12px, botones e inputs 8px, chips 999px. Botones de 44px de alto mínimo (36px los pequeños).
- Montos alineados a la derecha en fuente mono, formato peruano: S/ 1,234.56.
- Animaciones suaves (fade 200 ms al cambiar de vista, toast que entra desde abajo), respetando la opción de reducir movimiento.
- Todo en español de Perú. Fechas dd/MM/yyyy.

## Reglas
- No inventes normas: usa solo las indicadas en los prompts. Los parámetros legales (RMV, UIT, tasas AFP, etc.) se leen de la tabla de parámetros versionados, nunca escritos fijos en el código.
- Cada acción importante se registra en la auditoría (usuario, fecha y hora, acción, detalle).
- Los periodos cerrados son de solo lectura.
- Después de cada tarea: compila con mvn clean verify y corrige todos los errores antes de terminar.
