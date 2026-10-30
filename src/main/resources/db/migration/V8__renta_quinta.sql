CREATE TABLE renta_quinta_mes (
    trabajador_id INTEGER NOT NULL REFERENCES trabajador (id),
    periodo       TEXT    NOT NULL CHECK (periodo GLOB '[0-9][0-9][0-9][0-9][01][0-9]'),
    remuneracion  TEXT    NOT NULL,
    gratificacion TEXT    NOT NULL DEFAULT '0.00',
    proyectada    TEXT    NOT NULL,
    impuesto      TEXT    NOT NULL,
    retencion     TEXT    NOT NULL,
    PRIMARY KEY (trabajador_id, periodo)
);

CREATE TABLE renta_otro_empleador (
    trabajador_id INTEGER NOT NULL REFERENCES trabajador (id),
    anio          INTEGER NOT NULL,
    ruc           TEXT,
    razon_social  TEXT,
    renta         TEXT    NOT NULL,
    retencion     TEXT    NOT NULL DEFAULT '0.00',
    PRIMARY KEY (trabajador_id, anio)
);
