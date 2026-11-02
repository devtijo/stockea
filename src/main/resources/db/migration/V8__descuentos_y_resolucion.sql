-- Descuentos por lote El supervisor los crea;
-- el servidor los aplica según el lote que realmente se vende (FEFO).
CREATE TABLE descuento (
                           id          UUID         PRIMARY KEY,
                           negocio_id  UUID         NOT NULL REFERENCES negocio(id) ON DELETE RESTRICT,
                           lote_id     UUID         NOT NULL,
                           porcentaje  NUMERIC(5,2) NOT NULL,
                           motivo      VARCHAR(30)  NOT NULL DEFAULT 'PROXIMO_A_VENCER',
                           activo      BOOLEAN      NOT NULL DEFAULT TRUE,
                           creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
                           creado_por  UUID,
                           CONSTRAINT uk_descuento_negocio UNIQUE (negocio_id, id),
                           CONSTRAINT fk_descuento_lote FOREIGN KEY (negocio_id, lote_id) REFERENCES lote (negocio_id, id),
                           CONSTRAINT chk_descuento_porcentaje CHECK (porcentaje > 0 AND porcentaje <= 100)
);
-- Un solo descuento activo por lote
CREATE UNIQUE INDEX uk_descuento_lote_activo ON descuento (negocio_id, lote_id) WHERE activo;

ALTER TABLE asignacion_lote
    ADD CONSTRAINT fk_asignacion_descuento FOREIGN KEY (negocio_id, descuento_id) REFERENCES descuento (negocio_id, id);

ALTER TABLE descuento ENABLE ROW LEVEL SECURITY;
ALTER TABLE descuento FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_negocio ON descuento
    USING (negocio_id = NULLIF(current_setting('app.negocio_id', true), '')::uuid)
    WITH CHECK (negocio_id = NULLIF(current_setting('app.negocio_id', true), '')::uuid);

-- El supervisor deja por escrito cómo resolvió cada discrepancia.
ALTER TABLE discrepancia ADD COLUMN nota_resolucion VARCHAR(300);
