ALTER TABLE vacacion ADD COLUMN acuerdo_escrito INTEGER NOT NULL DEFAULT 0 CHECK (acuerdo_escrito IN (0, 1));

ALTER TABLE aprobacion ADD COLUMN vacacion_id INTEGER REFERENCES vacacion (id) ON DELETE CASCADE;
CREATE INDEX ix_aprobacion_vacacion ON aprobacion (vacacion_id);

INSERT INTO vacacion (trabajador_id, tipo, desde, hasta, dias, observacion, estado, fecha_registro, usuario,
                      acuerdo_escrito)
SELECT t.id, v.tipo, v.desde, v.hasta, v.dias, v.observacion, v.estado, v.registro, 'admin', v.acuerdo
FROM trabajador t
JOIN (
    SELECT '40000001' AS dni, 'Goce' AS tipo, '2020-03-02' AS desde, '2020-03-31' AS hasta, 30 AS dias,
           'Periodo 2019-2020' AS observacion, 'Gozado' AS estado, '2020-02-10T10:00:00' AS registro, 0 AS acuerdo
    UNION ALL SELECT '40000001', 'Goce', '2021-03-01', '2021-03-30', 30, 'Periodo 2020-2021', 'Gozado', '2021-02-08T10:00:00', 0
    UNION ALL SELECT '40000001', 'Goce', '2022-02-01', '2022-03-02', 30, 'Periodo 2021-2022', 'Gozado', '2022-01-10T10:00:00', 0
    UNION ALL SELECT '40000001', 'Goce', '2023-02-01', '2023-03-02', 30, 'Periodo 2022-2023', 'Gozado', '2023-01-09T10:00:00', 0
    UNION ALL SELECT '40000001', 'Goce', '2024-07-01', '2024-07-30', 30, 'Periodo 2023-2024', 'Gozado', '2024-06-03T10:00:00', 0
    UNION ALL SELECT '40000001', 'Goce', '2025-03-03', '2025-04-01', 30, 'Periodo 2024-2025', 'Gozado', '2025-02-03T10:00:00', 0
    UNION ALL SELECT '40000001', 'Venta', '2025-04-02', '2025-04-16', 15, 'Venta de 15 días con acuerdo escrito', 'Gozado', '2025-02-03T10:05:00', 1
    UNION ALL SELECT '40000001', 'Goce', '2026-12-16', '2026-12-30', 15, 'Periodo 2025-2026', 'Programado', '2026-09-01T10:00:00', 0
    UNION ALL SELECT '40000002', 'Goce', '2022-07-18', '2022-08-16', 30, NULL, 'Gozado', '2022-07-01T10:00:00', 0
    UNION ALL SELECT '40000002', 'Goce', '2023-07-17', '2023-08-15', 30, NULL, 'Gozado', '2023-07-03T10:00:00', 0
    UNION ALL SELECT '40000002', 'Goce', '2024-07-15', '2024-08-13', 30, NULL, 'Gozado', '2024-07-01T10:00:00', 0
    UNION ALL SELECT '40000002', 'Goce', '2025-07-14', '2025-08-12', 30, NULL, 'Gozado', '2025-07-01T10:00:00', 0
    UNION ALL SELECT '40000003', 'Goce', '2019-02-04', '2019-03-05', 30, NULL, 'Gozado', '2019-01-14T10:00:00', 0
    UNION ALL SELECT '40000003', 'Goce', '2020-02-03', '2020-03-03', 30, NULL, 'Gozado', '2020-01-13T10:00:00', 0
    UNION ALL SELECT '40000003', 'Goce', '2021-02-01', '2021-03-02', 30, NULL, 'Gozado', '2021-01-11T10:00:00', 0
    UNION ALL SELECT '40000003', 'Goce', '2022-08-01', '2022-08-30', 30, NULL, 'Gozado', '2022-07-11T10:00:00', 0
    UNION ALL SELECT '40000003', 'Goce', '2023-08-01', '2023-08-30', 30, NULL, 'Gozado', '2023-07-10T10:00:00', 0
    UNION ALL SELECT '40000004', 'Goce', '2023-02-06', '2023-02-20', 15, NULL, 'Gozado', '2023-01-16T10:00:00', 0
    UNION ALL SELECT '40000004', 'Goce', '2024-02-05', '2024-02-19', 15, NULL, 'Gozado', '2024-01-15T10:00:00', 0
    UNION ALL SELECT '40000004', 'Goce', '2025-02-03', '2025-02-17', 15, NULL, 'Gozado', '2025-01-13T10:00:00', 0
    UNION ALL SELECT '40000005', 'Goce', '2024-05-06', '2024-05-20', 15, NULL, 'Gozado', '2024-04-15T10:00:00', 0
    UNION ALL SELECT '40000006', 'Goce', '2025-09-01', '2025-09-15', 15, NULL, 'Gozado', '2025-08-11T10:00:00', 0
) v ON v.dni = t.num_doc;

INSERT INTO vacacion (trabajador_id, tipo, desde, hasta, dias, observacion, estado, fecha_registro, usuario)
SELECT id, 'Goce', '2027-01-04', '2027-01-13', 10, 'Viaje familiar', 'Pendiente de aprobación',
       '2026-10-10T09:30:00', '40000002'
FROM trabajador WHERE num_doc = '40000002';

INSERT INTO aprobacion (tipo, trabajador_id, detalle, cambios_json, registrado_por, fecha, estado, vacacion_id)
SELECT 'Vacaciones', v.trabajador_id, 'Goce de 10 días del 04/01/2027 al 13/01/2027', '{}', '40000002',
       '2026-10-10T09:30:00', 'Pendiente', v.id
FROM vacacion v WHERE v.estado = 'Pendiente de aprobación';
