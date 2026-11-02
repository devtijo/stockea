-- Datos de DEMOSTRACIÓN para un negocio ya registrado

-- Se conecta como stockea_app (sin privilegios de dueño) y fija el negocio

\set ON_ERROR_STOP on
SELECT set_config('app.negocio_id', :'negocio', false);

BEGIN;
WITH productos (nombre, categoria, unidad, precio, cantidad, dias, costo) AS (
    VALUES ('Leche entera 1 L',      'Lácteos',  'UNIDAD', 4200,  24, 6,  3300),
           ('Pan tajado',            'Panadería','UNIDAD', 6500,  12, 4,  5000),
           ('Huevos x 12',           'Huevos',   'UNIDAD', 9800,  15, 18, 7900),
           ('Arroz 500 g',           'Granos',   'UNIDAD', 2600,  40, 300, 1900),
           ('Gaseosa 400 ml',        'Bebidas',  'UNIDAD', 2500,  36, 120, 1700),
           ('Queso campesino 250 g', 'Lácteos',  'UNIDAD', 7500,   3, 8,  5800)
), nuevos AS (
INSERT INTO producto (id, negocio_id, nombre, categoria, unidad_venta, precio_venta)
SELECT gen_random_uuid(), :'negocio'::uuid, nombre, categoria, unidad, precio FROM productos
                                                                                       RETURNING id, nombre, precio_venta
), precios AS (
INSERT INTO historial_precio (id, negocio_id, producto_id, precio_venta, vigente_desde)
SELECT gen_random_uuid(), :'negocio'::uuid, id, precio_venta, now() FROM nuevos
    )
INSERT INTO lote (id, negocio_id, producto_id, fecha_vencimiento, cantidad_inicial, cantidad_disponible, costo_unitario)
SELECT gen_random_uuid(), :'negocio'::uuid, n.id, CURRENT_DATE + p.dias, p.cantidad, p.cantidad, p.costo
FROM nuevos n JOIN productos p ON p.nombre = n.nombre;
COMMIT;

SELECT nombre, precio_venta FROM producto ORDER BY nombre;
