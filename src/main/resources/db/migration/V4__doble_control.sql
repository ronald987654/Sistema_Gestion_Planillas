ALTER TABLE aprobacion ADD COLUMN fecha_resolucion TEXT;
ALTER TABLE aprobacion ADD COLUMN motivo_rechazo TEXT;
CREATE INDEX ix_aprobacion_trabajador ON aprobacion (trabajador_id, estado);

UPDATE usuario SET trabajador_id = (SELECT id FROM trabajador WHERE num_doc = '40000001') WHERE usuario = 'admin';

INSERT INTO usuario (usuario, hash_password, rol, trabajador_id, activo)
SELECT 'aprobador', '$2a$10$a6XawyW4US/diyryyKExduM6t0ZkVeKWXP2dM03yEj1pAy3e8OAKy', 'APROBADOR', id, 1
FROM trabajador WHERE num_doc = '40000003';
