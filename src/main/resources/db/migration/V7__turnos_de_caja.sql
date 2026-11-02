-- Turnos de caja. Se abren y se cierran en el dispositivo, también sin internet:
CREATE TABLE turno_caja (
                            negocio_id        UUID          NOT NULL REFERENCES negocio(id) ON DELETE RESTRICT,
                            id                UUID          NOT NULL,          -- lo genera el dispositivo
                            dispositivo_id    UUID          NOT NULL,
                            cajero_id         UUID          NOT NULL,
                            estado            VARCHAR(10)   NOT NULL DEFAULT 'ABIERTO',
                            monto_inicial     NUMERIC(14,2) NOT NULL,
                            abierto_en        TIMESTAMPTZ   NOT NULL,
                            cerrado_en        TIMESTAMPTZ,
                            monto_contado     NUMERIC(14,2),
                            efectivo_esperado NUMERIC(14,2),                   -- monto inicial + ventas en efectivo del turno
                            diferencia        NUMERIC(14,2),                   -- contado - esperado (negativo = faltante)
                            ventas_cantidad   INTEGER,
                            PRIMARY KEY (negocio_id, id),
                            CONSTRAINT fk_turno_cajero FOREIGN KEY (negocio_id, cajero_id) REFERENCES usuario (negocio_id, id),
                            CONSTRAINT chk_turno_estado CHECK (estado IN ('ABIERTO', 'CERRADO')),
                            CONSTRAINT chk_turno_montos CHECK (monto_inicial >= 0 AND (monto_contado IS NULL OR monto_contado >= 0))
);
CREATE INDEX idx_turno_abierto ON turno_caja (negocio_id, abierto_en DESC);

ALTER TABLE turno_caja ENABLE ROW LEVEL SECURITY;
ALTER TABLE turno_caja FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_negocio ON turno_caja
    USING (negocio_id = NULLIF(current_setting('app.negocio_id', true), '')::uuid)
    WITH CHECK (negocio_id = NULLIF(current_setting('app.negocio_id', true), '')::uuid);

