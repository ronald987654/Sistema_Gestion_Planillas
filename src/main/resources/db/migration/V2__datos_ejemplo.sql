INSERT INTO parametros_version (vigente_desde, norma, rmv, uit, asig_familiar_pct, essalud_pct, eps_pct, onp_pct,
                                afp_aporte_pct, afp_prima_pct, rma_prima, bonif_ext_pct, bonif_ext_eps_pct,
                                vida_ley_pct, sis_monto, estado, registrado_por, fecha_registro)
VALUES ('2025-01', 'Datos de ejemplo', '1130.00', '5350.00', '10', '9', '6.75', '13',
        '10', '1.37', '12732.70', '9', '6.75', '1.53', '15.00', 'Vigente', 'sistema', '2025-01-01T00:00:00'),
       ('2026-01', 'Datos de ejemplo', '1130.00', '5500.00', '10', '9', '6.75', '13',
        '10', '1.37', '12732.70', '9', '6.75', '1.53', '15.00', 'Vigente', 'sistema', '2026-01-01T00:00:00'),
       ('2026-10', 'Datos de ejemplo', '1230.00', '5500.00', '10', '9', '6.75', '13',
        '10', '1.37', '12732.70', '9', '6.75', '1.53', '15.00', 'Vigente', 'sistema', '2026-10-01T00:00:00');

INSERT INTO afp_comision (version_id, afp, comision_flujo_pct, comision_mixta_pct)
SELECT pv.id, c.column1, c.column2, c.column3
FROM parametros_version pv
CROSS JOIN (VALUES ('AFP Habitat', '1.47', '1.25'),
                   ('AFP Integra', '1.55', '0.78'),
                   ('AFP Prima', '1.60', '1.25'),
                   ('AFP Profuturo', '1.69', '0.68')) c;

INSERT INTO trabajador (codigo, tipo_doc, num_doc, ap_paterno, ap_materno, nombres, celular, correo,
                        fecha_ingreso, area, cargo, tipo_contrato, regimen_laboral, centro_costo, sucursal,
                        sueldo, movilidad, salud, sistema_pension, cuspp, tipo_comision,
                        asig_familiar, vida_ley, tipo_pago, banco_sueldo, cta_sueldo, banco_cts, cta_cts)
VALUES ('T0001', 'DNI', '40000001', 'Quispe', 'Mamani', 'Rosa Elena', '900000001', 'rosa.quispe@ejemplo.pe',
        '2019-03-01', 'Administración', 'Jefa de administración', 'Indeterminado', 'general', 'CC-ADM', 'Lima',
        '3500.00', '0.00', 'EsSalud', 'ONP', NULL, NULL,
        1, 1, 'Depósito en cuenta', 'Banco Ejemplo', '000-0000001', 'Caja Ejemplo', '100-0000001'),
       ('T0002', 'DNI', '40000002', 'Flores', 'Huamán', 'Carlos Alberto', '900000002', 'carlos.flores@ejemplo.pe',
        '2021-07-15', 'Operaciones', 'Analista de operaciones', 'Plazo fijo', 'general', 'CC-OPE', 'Lima',
        '2800.00', '150.00', 'EsSalud', 'AFP Integra', '000000CFHUA0', 'flujo',
        0, 1, 'Depósito en cuenta', 'Banco Ejemplo', '000-0000002', 'Caja Ejemplo', '100-0000002'),
       ('T0003', 'DNI', '40000003', 'Rojas', 'Castillo', 'Lucía Fernanda', '900000003', 'lucia.rojas@ejemplo.pe',
        '2018-01-08', 'Gerencia', 'Gerenta general', 'Indeterminado', 'general', 'CC-GER', 'Lima',
        '5200.00', '0.00', 'EPS', 'AFP Prima', '000000LRCAS0', 'mixta',
        1, 1, 'Depósito en cuenta', 'Banco Ejemplo', '000-0000003', 'Caja Ejemplo', '100-0000003'),
       ('T0004', 'DNI', '40000004', 'Torres', 'Vargas', 'Miguel Ángel', '900000004', 'miguel.torres@ejemplo.pe',
        '2022-02-01', 'Ventas', 'Vendedor', 'Plazo fijo', 'pequena', 'CC-VEN', 'Arequipa',
        '1800.00', '100.00', 'EsSalud', 'AFP Habitat', '000000MTVAR0', 'flujo',
        1, 0, 'Depósito en cuenta', 'Banco Ejemplo', '000-0000004', 'Caja Ejemplo', '100-0000004'),
       ('T0005', 'DNI', '40000005', 'Chávez', 'Paredes', 'Ana Sofía', '900000005', 'ana.chavez@ejemplo.pe',
        '2023-05-02', 'Ventas', 'Asistente comercial', 'Indeterminado', 'pequena', 'CC-VEN', 'Arequipa',
        '2200.00', '0.00', 'EsSalud', 'AFP Profuturo', '000000ACPAR0', 'mixta',
        0, 0, 'Cheque', NULL, NULL, 'Caja Ejemplo', '100-0000005'),
       ('T0006', 'DNI', '40000006', 'Ramos', 'Condori', 'Jorge Luis', '900000006', NULL,
        '2024-08-19', 'Almacén', 'Almacenero', 'Plazo fijo', 'micro', 'CC-ALM', 'Puno',
        '1300.00', '0.00', 'SIS', 'ONP', NULL, NULL,
        0, 0, 'Efectivo', NULL, NULL, NULL, NULL),
       ('T0007', 'DNI', '40000007', 'Díaz', 'Salazar', 'Patricia', '900000007', 'patricia.diaz@ejemplo.pe',
        '2025-03-10', 'Almacén', 'Auxiliar de almacén', 'Plazo fijo', 'micro', 'CC-ALM', 'Puno',
        '1500.00', '0.00', 'EsSalud', 'AFP Habitat', '000000PDSAL0', 'mixta',
        0, 0, 'Depósito en cuenta', 'Banco Ejemplo', '000-0000007', NULL, NULL);

INSERT INTO horario (trabajador_id, hora_ingreso, hora_salida, refrigerio_min, tolerancia_min, dias_laborables, no_fiscalizado)
SELECT id, '08:00', '17:00', 60, 5, '1,2,3,4,5', 0 FROM trabajador WHERE num_doc IN ('40000001', '40000002');
INSERT INTO horario (trabajador_id, hora_ingreso, hora_salida, refrigerio_min, tolerancia_min, dias_laborables, no_fiscalizado)
SELECT id, '09:00', '18:00', 60, 0, '1,2,3,4,5', 1 FROM trabajador WHERE num_doc = '40000003';
INSERT INTO horario (trabajador_id, hora_ingreso, hora_salida, refrigerio_min, tolerancia_min, dias_laborables, no_fiscalizado)
SELECT id, '09:00', '17:00', 45, 10, '1,2,3,4,5,6', 0 FROM trabajador WHERE num_doc IN ('40000004', '40000005');
INSERT INTO horario (trabajador_id, hora_ingreso, hora_salida, refrigerio_min, tolerancia_min, dias_laborables, no_fiscalizado)
SELECT id, '07:00', '15:00', 60, 5, '1,2,3,4,5,6', 0 FROM trabajador WHERE num_doc IN ('40000006', '40000007');

INSERT INTO usuario (usuario, hash_password, rol, trabajador_id, activo)
VALUES ('admin', '$2a$10$i61xDiUyOiMYqlgnHMsZTuLzpBNnn49E8TzkSfhcaaLft6uRskfYm', 'ADMIN', NULL, 1);
INSERT INTO usuario (usuario, hash_password, rol, trabajador_id, activo)
SELECT '40000002', '$2a$10$FdULcODvB2EsH6n7ZJZtcutPcMQtW350y2OXmKcSQBRsLapUIRx/.', 'TRABAJADOR', id, 1
FROM trabajador WHERE num_doc = '40000002';
