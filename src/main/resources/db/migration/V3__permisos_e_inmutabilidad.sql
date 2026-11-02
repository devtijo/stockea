--   Capa 1: el rol de la aplicación no tiene UPDATE ni DELETE sobre registros mercantiles (solo SELECT e INSERT).
--   Capa 2: un trigger rechaza UPDATE/DELETE incluso al dueño del esquema.
-- La aplicación tampoco tiene DELETE en ninguna tabla: no hay endpoints destructivos.

GRANT USAGE ON SCHEMA public TO stockea_app;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO stockea_app;
REVOKE ALL ON flyway_schema_history FROM stockea_app;

REVOKE UPDATE ON venta, item_venta, asignacion_lote, movimiento_inventario,
    historial_precio, operacion_sync, consentimiento
    FROM stockea_app;

-- Tablas que creen migraciones futuras: mismos permisos base.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO stockea_app;

CREATE FUNCTION bloquear_modificacion() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Registro inmutable (ADR-004): % sobre % no está permitido. Use un registro de corrección.',
        TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

DO $$
DECLARE
tabla TEXT;
BEGIN
    FOREACH tabla IN ARRAY ARRAY['venta', 'item_venta', 'asignacion_lote', 'movimiento_inventario',
                                 'historial_precio', 'operacion_sync', 'consentimiento']
    LOOP
        EXECUTE format('CREATE TRIGGER trg_inmutable BEFORE UPDATE OR DELETE ON %I
                        FOR EACH ROW EXECUTE FUNCTION bloquear_modificacion()', tabla);
END LOOP;
END;
$$;
