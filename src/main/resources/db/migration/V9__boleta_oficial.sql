ALTER TABLE trabajador ADD COLUMN fecha_nacimiento TEXT;
ALTER TABLE trabajador ADD COLUMN hijos INTEGER NOT NULL DEFAULT 0 CHECK (hijos >= 0);
ALTER TABLE trabajador ADD COLUMN direccion TEXT;
ALTER TABLE trabajador ADD COLUMN categoria TEXT NOT NULL DEFAULT 'Empleado' CHECK (categoria IN ('Empleado', 'Obrero'));

ALTER TABLE empresa ADD COLUMN rubro TEXT;
ALTER TABLE empresa ADD COLUMN firma_digital_activa INTEGER NOT NULL DEFAULT 0 CHECK (firma_digital_activa IN (0, 1));
ALTER TABLE empresa ADD COLUMN huella_en_boleta INTEGER NOT NULL DEFAULT 1 CHECK (huella_en_boleta IN (0, 1));

UPDATE trabajador SET fecha_nacimiento = '1985-04-12', hijos = 2, categoria = 'Empleado',
       direccion = 'Av. Ejemplo 101, Cercado de Lima' WHERE num_doc = '40000001';
UPDATE trabajador SET fecha_nacimiento = '1992-09-03', hijos = 0, categoria = 'Empleado',
       direccion = 'Jr. Ficticio 202, San Miguel' WHERE num_doc = '40000002';
UPDATE trabajador SET fecha_nacimiento = '1980-01-25', hijos = 1, categoria = 'Empleado',
       direccion = 'Calle Muestra 303, Miraflores' WHERE num_doc = '40000003';
UPDATE trabajador SET fecha_nacimiento = '1995-11-18', hijos = 1, categoria = 'Empleado',
       direccion = 'Av. Prueba 404, Cayma' WHERE num_doc = '40000004';
UPDATE trabajador SET fecha_nacimiento = '1998-06-30', hijos = 0, categoria = 'Empleado',
       direccion = 'Pasaje Demo 505, Yanahuara' WHERE num_doc = '40000005';
UPDATE trabajador SET fecha_nacimiento = '2000-02-14', hijos = 0, categoria = 'Obrero',
       direccion = 'Jr. Ejemplo 606, Puno' WHERE num_doc = '40000006';
UPDATE trabajador SET fecha_nacimiento = '1997-08-08', hijos = 0, categoria = 'Obrero',
       direccion = 'Av. Ficticia 707, Puno' WHERE num_doc = '40000007';
