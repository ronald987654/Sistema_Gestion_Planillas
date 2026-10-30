ALTER TABLE usuario ADD COLUMN intentos_fallidos INTEGER NOT NULL DEFAULT 0;
ALTER TABLE usuario ADD COLUMN bloqueado_hasta TEXT;
ALTER TABLE usuario ADD COLUMN ultimo_acceso TEXT;
ALTER TABLE usuario ADD COLUMN debe_cambiar_clave INTEGER NOT NULL DEFAULT 0 CHECK (debe_cambiar_clave IN (0, 1));

CREATE TABLE configuracion (
    clave TEXT PRIMARY KEY,
    valor TEXT NOT NULL
);
INSERT INTO configuracion (clave, valor) VALUES
    ('sesion.inactividad_minutos', '30'),
    ('acceso.intentos_maximos', '5'),
    ('acceso.bloqueo_minutos', '15');

ALTER TABLE documento_emitido ADD COLUMN fecha_recepcion TEXT;
ALTER TABLE documento_emitido ADD COLUMN recepcion TEXT;

CREATE INDEX ix_auditoria_accion ON auditoria (accion);
CREATE INDEX ix_documento_trabajador ON documento_emitido (trabajador_id, tipo, periodo);
