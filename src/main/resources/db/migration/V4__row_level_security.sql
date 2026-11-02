
-- La aplicación fija app.negocio_id en cada conexión a partir del token.
-- Sin valor fijado, NULLIF(...) da NULL y no se ve ninguna fila (falla cerrada).
-- FORCE aplica la política también al dueño de las tablas.

DO $$
DECLARE
tabla TEXT;
BEGIN
    FOREACH tabla IN ARRAY ARRAY['producto', 'lote', 'historial_precio', 'operacion_sync', 'venta',
                                 'item_venta', 'asignacion_lote', 'movimiento_inventario', 'discrepancia',
                                 'usuario', 'consentimiento', 'dispositivo']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tabla);
EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tabla);
EXECUTE format($p$CREATE POLICY aislamiento_negocio ON %I
                          USING (negocio_id = NULLIF(current_setting('app.negocio_id', true), '')::uuid)
                          WITH CHECK (negocio_id = NULLIF(current_setting('app.negocio_id', true), '')::uuid)$p$,
               tabla);
END LOOP;
END;
$$;

ALTER TABLE negocio ENABLE ROW LEVEL SECURITY;
ALTER TABLE negocio FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_negocio ON negocio
    USING (id = NULLIF(current_setting('app.negocio_id', true), '')::uuid)
    WITH CHECK (id = NULLIF(current_setting('app.negocio_id', true), '')::uuid);
