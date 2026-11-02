-- acumulados que calcula el Worker a partir del evento VentaRegistrada

-- Unidades vendidas por producto y día del negocio. Se ACTUALIZA (es un acumulado,
-- no historia): la historia inmutable sigue estando en venta / item_venta
CREATE TABLE venta_diaria_producto (
                                       negocio_id     UUID          NOT NULL REFERENCES negocio(id) ON DELETE RESTRICT,
                                       producto_id    UUID          NOT NULL,
                                       fecha          DATE          NOT NULL,
                                       unidades       NUMERIC(14,3) NOT NULL DEFAULT 0,
                                       ventas         INTEGER       NOT NULL DEFAULT 0,
                                       actualizado_en TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                       PRIMARY KEY (negocio_id, producto_id, fecha),
                                       CONSTRAINT fk_vdp_producto FOREIGN KEY (negocio_id, producto_id) REFERENCES producto (negocio_id, id)
);

-- Eventos ya procesados: RabbitMQ entrega "al menos una vez", así que el
-- consumidor descarta los repetidos (idempotencia del lado del consumidor)
CREATE TABLE evento_consumido (
                                  negocio_id   UUID        NOT NULL REFERENCES negocio(id) ON DELETE RESTRICT,
                                  evento       VARCHAR(40) NOT NULL,
                                  llave        UUID        NOT NULL,
                                  consumido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  PRIMARY KEY (negocio_id, evento, llave)
);

-- Los permisos SELECT/INSERT/UPDATE de stockea_app llegan por ALTER DEFAULT PRIVILEGES
-- El registro de eventos consumidos es solo de inserción
REVOKE UPDATE ON evento_consumido FROM stockea_app;

-- Mismo aislamiento por negocio que el resto
ALTER TABLE venta_diaria_producto ENABLE ROW LEVEL SECURITY;
ALTER TABLE venta_diaria_producto FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_negocio ON venta_diaria_producto
    USING (negocio_id = NULLIF(current_setting('app.negocio_id', true), '')::uuid);

ALTER TABLE evento_consumido ENABLE ROW LEVEL SECURITY;
ALTER TABLE evento_consumido FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_negocio ON evento_consumido
    USING (negocio_id = NULLIF(current_setting('app.negocio_id', true), '')::uuid);
