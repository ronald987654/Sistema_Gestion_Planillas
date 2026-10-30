CREATE TABLE empresa (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    ruc                TEXT    NOT NULL UNIQUE CHECK (length(ruc) = 11),
    razon_social       TEXT    NOT NULL,
    nombre_comercial   TEXT,
    regimen_tributario TEXT,
    ciiu               TEXT,
    inicio_actividades TEXT,
    direccion          TEXT,
    departamento       TEXT,
    provincia          TEXT,
    distrito           TEXT,
    ubigeo             TEXT CHECK (ubigeo IS NULL OR length(ubigeo) = 6),
    ciudad             TEXT,
    regimen_laboral    TEXT    NOT NULL DEFAULT 'general' CHECK (regimen_laboral IN ('general', 'pequena', 'micro')),
    registro_remype    TEXT,
    eps                TEXT,
    periodicidad       TEXT,
    dia_pago           INTEGER CHECK (dia_pago IS NULL OR dia_pago BETWEEN 1 AND 31),
    sctr               INTEGER NOT NULL DEFAULT 0 CHECK (sctr IN (0, 1)),
    rep_nombre         TEXT,
    rep_tipo_doc       TEXT,
    rep_doc            TEXT,
    rep_cargo          TEXT,
    partida_registral  TEXT,
    telefono           TEXT,
    correo             TEXT,
    correo_boletas     TEXT,
    logo               BLOB,
    firma              BLOB
);

CREATE TABLE parametros_version (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    vigente_desde     TEXT NOT NULL CHECK (vigente_desde GLOB '[0-9][0-9][0-9][0-9]-[01][0-9]'),
    norma             TEXT,
    rmv               TEXT NOT NULL,
    uit               TEXT NOT NULL,
    asig_familiar_pct TEXT NOT NULL,
    essalud_pct       TEXT NOT NULL,
    eps_pct           TEXT NOT NULL,
    onp_pct           TEXT NOT NULL,
    afp_aporte_pct    TEXT NOT NULL,
    afp_prima_pct     TEXT NOT NULL,
    rma_prima         TEXT NOT NULL,
    bonif_ext_pct     TEXT NOT NULL,
    bonif_ext_eps_pct TEXT NOT NULL,
    vida_ley_pct      TEXT NOT NULL,
    sis_monto         TEXT NOT NULL,
    estado            TEXT NOT NULL DEFAULT 'Vigente' CHECK (estado IN ('Vigente', 'Anulada')),
    registrado_por    TEXT NOT NULL,
    fecha_registro    TEXT NOT NULL
);
CREATE UNIQUE INDEX ux_parametros_vigente ON parametros_version (vigente_desde) WHERE estado = 'Vigente';

CREATE TABLE afp_comision (
    version_id         INTEGER NOT NULL REFERENCES parametros_version (id) ON DELETE CASCADE,
    afp                TEXT    NOT NULL CHECK (afp IN ('AFP Habitat', 'AFP Integra', 'AFP Prima', 'AFP Profuturo')),
    comision_flujo_pct TEXT    NOT NULL,
    comision_mixta_pct TEXT    NOT NULL,
    PRIMARY KEY (version_id, afp)
);

CREATE TABLE trabajador (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo          TEXT    NOT NULL UNIQUE,
    tipo_doc        TEXT    NOT NULL DEFAULT 'DNI' CHECK (tipo_doc IN ('DNI', 'CE', 'PAS')),
    num_doc         TEXT    NOT NULL UNIQUE,
    ap_paterno      TEXT    NOT NULL,
    ap_materno      TEXT,
    nombres         TEXT    NOT NULL,
    celular         TEXT,
    correo          TEXT,
    fecha_ingreso   TEXT    NOT NULL,
    fecha_cese      TEXT,
    area            TEXT,
    cargo           TEXT,
    tipo_contrato   TEXT,
    regimen_laboral TEXT    NOT NULL DEFAULT 'general' CHECK (regimen_laboral IN ('general', 'pequena', 'micro')),
    centro_costo    TEXT,
    sucursal        TEXT,
    sueldo          TEXT    NOT NULL,
    movilidad       TEXT    NOT NULL DEFAULT '0.00',
    salud           TEXT    NOT NULL DEFAULT 'EsSalud' CHECK (salud IN ('EsSalud', 'EPS', 'SIS')),
    sistema_pension TEXT    NOT NULL CHECK (sistema_pension IN ('ONP', 'AFP Habitat', 'AFP Integra', 'AFP Prima', 'AFP Profuturo')),
    cuspp           TEXT,
    tipo_comision   TEXT CHECK (tipo_comision IS NULL OR tipo_comision IN ('flujo', 'mixta')),
    asig_familiar   INTEGER NOT NULL DEFAULT 0 CHECK (asig_familiar IN (0, 1)),
    vida_ley        INTEGER NOT NULL DEFAULT 0 CHECK (vida_ley IN (0, 1)),
    tipo_pago       TEXT    NOT NULL DEFAULT 'Depósito en cuenta' CHECK (tipo_pago IN ('Depósito en cuenta', 'Efectivo', 'Cheque')),
    banco_sueldo    TEXT,
    cta_sueldo      TEXT,
    banco_cts       TEXT,
    cta_cts         TEXT,
    CHECK (fecha_cese IS NULL OR fecha_cese >= fecha_ingreso)
);
CREATE INDEX ix_trabajador_apellidos ON trabajador (ap_paterno, ap_materno, nombres);
CREATE INDEX ix_trabajador_cese ON trabajador (fecha_cese);

CREATE TABLE horario (
    trabajador_id   INTEGER PRIMARY KEY REFERENCES trabajador (id) ON DELETE CASCADE,
    hora_ingreso    TEXT    NOT NULL,
    hora_salida     TEXT    NOT NULL,
    refrigerio_min  INTEGER NOT NULL DEFAULT 60 CHECK (refrigerio_min >= 0),
    tolerancia_min  INTEGER NOT NULL DEFAULT 0 CHECK (tolerancia_min >= 0),
    dias_laborables TEXT    NOT NULL DEFAULT '1,2,3,4,5',
    no_fiscalizado  INTEGER NOT NULL DEFAULT 0 CHECK (no_fiscalizado IN (0, 1))
);

CREATE TABLE asistencia (
    trabajador_id  INTEGER NOT NULL REFERENCES trabajador (id) ON DELETE CASCADE,
    fecha          TEXT    NOT NULL,
    hora_ingreso   TEXT,
    hora_salida    TEXT,
    novedad        TEXT    NOT NULL DEFAULT '' CHECK (novedad IN ('', 'vac', 'lic', 'dm', 'sg', 'sust')),
    he_autorizada  INTEGER NOT NULL DEFAULT 0 CHECK (he_autorizada IN (0, 1)),
    he_tratamiento TEXT CHECK (he_tratamiento IS NULL OR he_tratamiento IN ('pago', 'comp')),
    origen         TEXT    NOT NULL DEFAULT 'Sistema' CHECK (origen IN ('Sistema', 'Excel', 'Excel + ajustes')),
    PRIMARY KEY (trabajador_id, fecha)
);
CREATE INDEX ix_asistencia_fecha ON asistencia (fecha);

CREATE TABLE feriado_extra (
    fecha  TEXT PRIMARY KEY,
    motivo TEXT NOT NULL
);

CREATE TABLE novedad_mes (
    trabajador_id       INTEGER NOT NULL REFERENCES trabajador (id) ON DELETE CASCADE,
    periodo             TEXT    NOT NULL CHECK (periodo GLOB '[0-9][0-9][0-9][0-9][01][0-9]'),
    inasistencias       INTEGER NOT NULL DEFAULT 0,
    vacaciones          INTEGER NOT NULL DEFAULT 0,
    tardanza_min        INTEGER NOT NULL DEFAULT 0,
    licencia            INTEGER NOT NULL DEFAULT 0,
    feriados_trabajados INTEGER NOT NULL DEFAULT 0,
    dias_subsidiados    INTEGER NOT NULL DEFAULT 0,
    comisiones          TEXT    NOT NULL DEFAULT '0.00',
    bonificacion        TEXT    NOT NULL DEFAULT '0.00',
    adelanto            TEXT    NOT NULL DEFAULT '0.00',
    otros_descuentos    TEXT    NOT NULL DEFAULT '0.00',
    PRIMARY KEY (trabajador_id, periodo)
);
CREATE INDEX ix_novedad_mes_periodo ON novedad_mes (periodo);

CREATE TABLE horas_extra_manual (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    trabajador_id INTEGER NOT NULL REFERENCES trabajador (id) ON DELETE CASCADE,
    fecha         TEXT    NOT NULL,
    horas         TEXT    NOT NULL,
    tratamiento   TEXT    NOT NULL DEFAULT 'pago' CHECK (tratamiento IN ('pago', 'comp')),
    autorizada    INTEGER NOT NULL DEFAULT 0 CHECK (autorizada IN (0, 1))
);
CREATE INDEX ix_horas_extra_trabajador_fecha ON horas_extra_manual (trabajador_id, fecha);

CREATE TABLE concepto_extra (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    trabajador_id INTEGER NOT NULL REFERENCES trabajador (id) ON DELETE CASCADE,
    periodo       TEXT    NOT NULL CHECK (periodo GLOB '[0-9][0-9][0-9][0-9][01][0-9]'),
    tipo          TEXT    NOT NULL CHECK (tipo IN ('ING', 'ING_NR', 'DES', 'APO')),
    codigo_plame  TEXT,
    descripcion   TEXT    NOT NULL,
    monto         TEXT    NOT NULL
);
CREATE INDEX ix_concepto_extra_trabajador_periodo ON concepto_extra (trabajador_id, periodo);

CREATE TABLE periodo (
    periodo           TEXT PRIMARY KEY CHECK (periodo GLOB '[0-9][0-9][0-9][0-9][01][0-9]'),
    estado            TEXT NOT NULL DEFAULT 'Abierto' CHECK (estado IN ('Abierto', 'Cerrado')),
    cerrado_por       TEXT,
    fecha_cierre      TEXT,
    motivo_reapertura TEXT
);

CREATE TABLE ajuste_beneficio (
    tipo          TEXT    NOT NULL CHECK (tipo IN ('GRATI', 'CTS')),
    anio          INTEGER NOT NULL,
    semestre      INTEGER NOT NULL CHECK (semestre IN (1, 2)),
    trabajador_id INTEGER NOT NULL REFERENCES trabajador (id) ON DELETE CASCADE,
    basico        TEXT,
    faltas        INTEGER NOT NULL DEFAULT 0,
    gratificacion TEXT,
    PRIMARY KEY (tipo, anio, semestre, trabajador_id)
);

CREATE TABLE vacacion (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    trabajador_id  INTEGER NOT NULL REFERENCES trabajador (id) ON DELETE CASCADE,
    tipo           TEXT    NOT NULL CHECK (tipo IN ('Goce', 'Venta')),
    desde          TEXT,
    hasta          TEXT,
    dias           INTEGER NOT NULL CHECK (dias > 0),
    observacion    TEXT,
    estado         TEXT    NOT NULL DEFAULT 'Programado'
                   CHECK (estado IN ('Programado', 'Gozado', 'Pendiente de aprobación', 'Rechazado', 'Anulado')),
    fecha_registro TEXT    NOT NULL,
    usuario        TEXT    NOT NULL,
    adjunto_nombre TEXT,
    adjunto        BLOB,
    CHECK (hasta IS NULL OR desde IS NULL OR hasta >= desde)
);
CREATE INDEX ix_vacacion_trabajador ON vacacion (trabajador_id, desde);

CREATE TABLE liquidacion (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    trabajador_id    INTEGER NOT NULL REFERENCES trabajador (id),
    fecha_cese       TEXT    NOT NULL,
    motivo           TEXT    NOT NULL,
    otros_ingresos   TEXT    NOT NULL DEFAULT '0.00',
    otros_descuentos TEXT    NOT NULL DEFAULT '0.00',
    faltas_cts       INTEGER NOT NULL DEFAULT 0,
    faltas_grati     INTEGER NOT NULL DEFAULT 0,
    ultima_grati     TEXT,
    estado           TEXT    NOT NULL DEFAULT 'Borrador' CHECK (estado IN ('Borrador', 'Cese registrado', 'Pagado')),
    fecha_registro   TEXT    NOT NULL,
    usuario          TEXT    NOT NULL
);
CREATE INDEX ix_liquidacion_trabajador ON liquidacion (trabajador_id);

CREATE TABLE usuario (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario       TEXT    NOT NULL UNIQUE,
    hash_password TEXT    NOT NULL,
    rol           TEXT    NOT NULL CHECK (rol IN ('ADMIN', 'APROBADOR', 'TRABAJADOR')),
    trabajador_id INTEGER REFERENCES trabajador (id),
    activo        INTEGER NOT NULL DEFAULT 1 CHECK (activo IN (0, 1)),
    CHECK (rol <> 'TRABAJADOR' OR trabajador_id IS NOT NULL)
);

CREATE TABLE aprobacion (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    tipo           TEXT    NOT NULL,
    trabajador_id  INTEGER REFERENCES trabajador (id) ON DELETE CASCADE,
    detalle        TEXT,
    cambios_json   TEXT,
    registrado_por TEXT    NOT NULL,
    fecha          TEXT    NOT NULL,
    estado         TEXT    NOT NULL DEFAULT 'Pendiente' CHECK (estado IN ('Pendiente', 'Aprobado', 'Rechazado')),
    resuelto_por   TEXT
);
CREATE INDEX ix_aprobacion_estado ON aprobacion (estado, fecha);

CREATE TABLE auditoria (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    fecha_hora TEXT NOT NULL,
    usuario    TEXT NOT NULL,
    accion     TEXT NOT NULL,
    detalle    TEXT
);
CREATE INDEX ix_auditoria_fecha ON auditoria (fecha_hora);
CREATE INDEX ix_auditoria_usuario ON auditoria (usuario);

CREATE TABLE documento_emitido (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    numero          TEXT    NOT NULL,
    tipo            TEXT    NOT NULL,
    trabajador_id   INTEGER REFERENCES trabajador (id),
    periodo         TEXT CHECK (periodo IS NULL OR periodo GLOB '[0-9][0-9][0-9][0-9][01][0-9]'),
    formato         TEXT    NOT NULL,
    fecha           TEXT    NOT NULL,
    estado          TEXT    NOT NULL DEFAULT 'Generado' CHECK (estado IN ('Generado', 'Enviado', 'Recibido', 'Declarado')),
    requiere_cargo  INTEGER NOT NULL DEFAULT 0 CHECK (requiere_cargo IN (0, 1)),
    archivo         TEXT,
    UNIQUE (tipo, numero)
);
CREATE INDEX ix_documento_trabajador_periodo ON documento_emitido (trabajador_id, periodo);
