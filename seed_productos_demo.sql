-- Productos y lotes del catálogo de prueba del frontend.
-- negocio_id = TenantDev.NEGOCIO_ID(). Ejecutar contra la BD de desarrollo:
--   docker exec -i stockea-postgres psql -U postgres -d stockea < seed_productos_demo.sql
INSERT INTO producto (id, negocio_id, nombre, unidad_venta, precio_venta) VALUES
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'Leche 1L',   'UNIDAD', 4000),
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'Pan tajado', 'UNIDAD', 3500),
  ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'Huevos x12', 'UNIDAD', 8000)
ON CONFLICT (id) DO NOTHING;

-- Leche con 2 lotes (7 vence antes, 3 después) para mostrar FEFO y fraccionamiento.
INSERT INTO lote (id, negocio_id, producto_id, fecha_vencimiento, cantidad_inicial, cantidad_disponible, costo_unitario) VALUES
  ('20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', CURRENT_DATE + 5,  7,  7,  2800),
  ('20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', CURRENT_DATE + 20, 3,  3,  2800),
  ('20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', CURRENT_DATE + 3,  20, 20, 2000),
  -- Huevos con solo 10: dos cajas que vendan 8 y 5 sin conexión generan la discrepancia de 3 (demo ESC-DIS-0008).
  ('20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000003', CURRENT_DATE + 15, 10, 10, 6000)
ON CONFLICT (id) DO NOTHING;
