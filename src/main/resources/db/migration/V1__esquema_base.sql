
-- Producto (inventario)
CREATE TABLE producto (
                          id              UUID PRIMARY KEY,
                          negocio_id      UUID NOT NULL,
                          nombre          VARCHAR(120) NOT NULL,
                          categoria       VARCHAR(60),
                          unidad_venta    VARCHAR(12) NOT NULL,
                          precio_venta    NUMERIC(14,2) NOT NULL,
                          stock_minimo    NUMERIC(14,2) NOT NULL DEFAULT 0,
                          activo          BOOLEAN NOT NULL DEFAULT TRUE,
                          creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
                          actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
                          CONSTRAINT chk_producto_unidad CHECK (unidad_venta IN ('UNIDAD','KILOGRAMO','LIBRA'))
);

-- Lote (inventario)
CREATE TABLE lote (
                      id                  UUID PRIMARY KEY,
                      negocio_id          UUID NOT NULL,
                      producto_id         UUID NOT NULL REFERENCES producto(id) ON DELETE RESTRICT,
                      fecha_vencimiento   DATE NOT NULL,
                      fecha_entrada       TIMESTAMPTZ NOT NULL DEFAULT now(),
                      cantidad_inicial    NUMERIC(12,3) NOT NULL,
                      cantidad_disponible NUMERIC(12,3) NOT NULL,
                      costo_unitario      NUMERIC(14,2) NOT NULL,
                      estado              VARCHAR(10) NOT NULL DEFAULT 'ACTIVO',
                      creado_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
                      CONSTRAINT chk_lote_cantidad CHECK (cantidad_disponible >= 0),
                      CONSTRAINT chk_lote_estado CHECK (estado IN ('ACTIVO','AGOTADO','VENCIDO'))
);
CREATE INDEX idx_lote_fefo ON lote (negocio_id, producto_id, fecha_vencimiento);

-- Historial de precios (inventario)
CREATE TABLE historial_precio (
                                  id              UUID PRIMARY KEY,
                                  negocio_id      UUID NOT NULL,
                                  producto_id     UUID NOT NULL REFERENCES producto(id) ON DELETE RESTRICT,
                                  precio_venta    NUMERIC(14,2) NOT NULL,
                                  vigente_desde   TIMESTAMPTZ NOT NULL,
                                  creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  creado_por      UUID
);
CREATE INDEX idx_historial_precio ON historial_precio (negocio_id, producto_id, vigente_desde DESC);

-- Operación de sincronización (sync) - IDEMPOTENCIA
CREATE TABLE operacion_sync (
                                negocio_id             UUID NOT NULL,
                                operacion_id           UUID NOT NULL,
                                dispositivo_id        UUID NOT NULL,
                                usuario_id            UUID NOT NULL,
                                tipo                  VARCHAR(20) NOT NULL,
                                secuencia_dispositivo BIGINT NOT NULL,
                                estado                VARCHAR(30) NOT NULL,
                                payload               JSONB,
                                creada_en_cliente     TIMESTAMPTZ NOT NULL,
                                recibida_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
                                procesada_en          TIMESTAMPTZ,
                                resultado_id          UUID,
                                PRIMARY KEY (negocio_id, operacion_id)
);
CREATE UNIQUE INDEX idx_operacion_sync_secuencia
    ON operacion_sync (negocio_id, dispositivo_id, secuencia_dispositivo);

-- Venta (ventas)
CREATE TABLE venta (
                       id               UUID PRIMARY KEY,
                       negocio_id       UUID NOT NULL,
                       operacion_id     UUID NOT NULL,
                       dispositivo_id  UUID NOT NULL,
                       cajero_id        UUID NOT NULL,
                       turno_caja_id    UUID NOT NULL,
                       estado           VARCHAR(20) NOT NULL,
                       metodo_pago      VARCHAR(20) NOT NULL,
                       subtotal         NUMERIC(14,2) NOT NULL,
                       descuento_total  NUMERIC(14,2) NOT NULL DEFAULT 0,
                       total            NUMERIC(14,2) NOT NULL,
                       monto_recibido   NUMERIC(14,2) NOT NULL,
                       cambio           NUMERIC(14,2) NOT NULL DEFAULT 0,
                       fecha_cliente    TIMESTAMPTZ NOT NULL,
                       registrada_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
                       CONSTRAINT uk_venta_operacion UNIQUE (negocio_id, operacion_id),
                       CONSTRAINT chk_venta_metodo CHECK (metodo_pago IN ('EFECTIVO','TRANSFERENCIA','TARJETA')),
                       CONSTRAINT chk_venta_estado CHECK (estado IN ('ACEPTADA','REASIGNADA','DISCREPANCIA'))
);

-- Items de la venta (ventas)
CREATE TABLE item_venta (
                            id                      UUID PRIMARY KEY,
                            negocio_id              UUID NOT NULL,
                            venta_id                UUID NOT NULL REFERENCES venta(id) ON DELETE RESTRICT,
                            producto_id             UUID NOT NULL REFERENCES producto(id) ON DELETE RESTRICT,
                            linea                   SMALLINT NOT NULL,
                            cantidad                NUMERIC(12,3) NOT NULL,
                            precio_unitario         NUMERIC(14,2) NOT NULL,
                            descuento_total_linea   NUMERIC(14,2) NOT NULL DEFAULT 0,
                            descuento_sin_cobertura NUMERIC(14,2) NOT NULL DEFAULT 0,
                            cantidad_sin_cobertura  NUMERIC(12,3) NOT NULL DEFAULT 0,
                            subtotal                NUMERIC(14,2) NOT NULL,
                            CONSTRAINT chk_item_cantidad CHECK (cantidad > 0),
                            CONSTRAINT uk_item_venta_linea UNIQUE (venta_id, linea)
);
CREATE INDEX idx_item_venta_venta ON item_venta (negocio_id, venta_id);

-- Asignación de lote (ventas)
CREATE TABLE asignacion_lote (
                                 id              UUID PRIMARY KEY,
                                 negocio_id      UUID NOT NULL,
                                 item_venta_id   UUID NOT NULL REFERENCES item_venta(id) ON DELETE RESTRICT,
                                 lote_id         UUID NOT NULL REFERENCES lote(id) ON DELETE RESTRICT,
                                 cantidad        NUMERIC(12,3) NOT NULL,
                                 descuento_id    UUID,
                                 monto_descuento NUMERIC(14,2) NOT NULL DEFAULT 0,
                                 origen          VARCHAR(20) NOT NULL DEFAULT 'DISPOSITIVO',
                                 CONSTRAINT chk_asignacion_cantidad CHECK (cantidad > 0),
                                 CONSTRAINT chk_asignacion_origen CHECK (origen IN ('DISPOSITIVO','REASIGNACION_FEFO'))
);

-- Movimiento de inventario (inventario) - append-only
CREATE TABLE movimiento_inventario (
                                       id              UUID PRIMARY KEY,
                                       negocio_id      UUID NOT NULL,
                                       lote_id         UUID NOT NULL REFERENCES lote(id) ON DELETE RESTRICT,
                                       tipo            VARCHAR(12) NOT NULL,
                                       cantidad        NUMERIC(12,3) NOT NULL,
                                       operacion_id    UUID,
                                       discrepancia_id UUID,
                                       creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
                                       creado_por      UUID,
                                       CONSTRAINT chk_movimiento_tipo CHECK (tipo IN ('ENTRADA','VENTA','CORRECCION'))
);
CREATE INDEX idx_movimiento_lote ON movimiento_inventario (negocio_id, lote_id, creado_en);

-- Discrepancia (sync)
CREATE TABLE discrepancia (
                              id                       UUID PRIMARY KEY,
                              negocio_id               UUID NOT NULL,
                              operacion_id             UUID NOT NULL,
                              venta_id                 UUID NOT NULL REFERENCES venta(id) ON DELETE RESTRICT,
                              item_venta_id            UUID REFERENCES item_venta(id) ON DELETE RESTRICT,
                              producto_id              UUID NOT NULL REFERENCES producto(id) ON DELETE RESTRICT,
                              lote_id                  UUID REFERENCES lote(id) ON DELETE RESTRICT,
                              motivo                   VARCHAR(25) NOT NULL,
                              cantidad_faltante        NUMERIC(12,3) NOT NULL DEFAULT 0,
                              precio_cobrado           NUMERIC(14,2),
                              precio_esperado          NUMERIC(14,2),
                              estado                   VARCHAR(10) NOT NULL DEFAULT 'ABIERTA',
                              detectada_en             TIMESTAMPTZ NOT NULL DEFAULT now(),
                              resuelta_por             UUID,
                              resuelta_en              TIMESTAMPTZ,
                              movimiento_correccion_id UUID REFERENCES movimiento_inventario(id) ON DELETE RESTRICT,
                              CONSTRAINT fk_discrepancia_operacion
                                  FOREIGN KEY (negocio_id, operacion_id)
                                      REFERENCES operacion_sync (negocio_id, operacion_id)
                                      DEFERRABLE INITIALLY DEFERRED,
                              CONSTRAINT chk_discrepancia_motivo CHECK (motivo IN (
                                                                                   'STOCK_INSUFICIENTE','SIN_LOTE_DISPONIBLE','LOTE_VENCIDO',
                                                                                   'PRODUCTO_INACTIVO','PRECIO_DIFERENTE')),
                              CONSTRAINT chk_discrepancia_estado CHECK (estado IN ('ABIERTA','RESUELTA'))
);
CREATE INDEX idx_discrepancia_estado ON discrepancia (negocio_id, estado);