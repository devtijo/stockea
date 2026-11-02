-- V2 - Identidad y multi-negocio
-- Los usuarios se autentican en Keycloak; aquí vive su proyección local.

CREATE TABLE negocio (
                         id                       UUID PRIMARY KEY,
                         nombre                   VARCHAR(120) NOT NULL,
                         tipo                     VARCHAR(40)  NOT NULL,
                         ciudad                   VARCHAR(80)  NOT NULL,
                         direccion                VARCHAR(160) NOT NULL,
                         zona_horaria             VARCHAR(40)  NOT NULL DEFAULT 'America/Bogota',
                         porcentaje_max_descuento NUMERIC(5,2) NOT NULL DEFAULT 30,
                         creado_en                TIMESTAMPTZ  NOT NULL DEFAULT now(),
                         CONSTRAINT chk_negocio_descuento CHECK (porcentaje_max_descuento BETWEEN 0 AND 100)
);

CREATE TABLE usuario (
                         id               UUID PRIMARY KEY,                 -- "sub" del token de Keycloak
                         negocio_id       UUID         NOT NULL REFERENCES negocio(id) ON DELETE RESTRICT,
                         tipo_documento   VARCHAR(5)   NOT NULL,
                         numero_documento VARCHAR(20)  NOT NULL,
                         nombre           VARCHAR(120) NOT NULL,
                         correo           VARCHAR(160),
                         rol              VARCHAR(15)  NOT NULL,
                         activo           BOOLEAN      NOT NULL DEFAULT TRUE,
                         pin_sal          VARCHAR(64)  NOT NULL,              -- material de verificación offline
                         pin_iteraciones  INTEGER      NOT NULL,
                         pin_hash         VARCHAR(128) NOT NULL,
                         creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
                         creado_por       UUID,
                         CONSTRAINT uk_usuario_documento UNIQUE (tipo_documento, numero_documento),
                         CONSTRAINT uk_usuario_negocio UNIQUE (negocio_id, id),
                         CONSTRAINT chk_usuario_rol CHECK (rol IN ('CAJERO','SUPERVISOR','ADMINISTRADOR')),
                         CONSTRAINT chk_usuario_tipo_documento CHECK (tipo_documento IN ('CC','CE','PA','PPT','TI'))
);

-- evidencia de la aceptación de la política de datos (insert-only).
CREATE TABLE consentimiento (
                                id               UUID PRIMARY KEY,
                                negocio_id       UUID        NOT NULL REFERENCES negocio(id) ON DELETE RESTRICT,
                                usuario_id       UUID        NOT NULL,
                                version_politica VARCHAR(20) NOT NULL,
                                aceptado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
                                CONSTRAINT fk_consentimiento_usuario FOREIGN KEY (negocio_id, usuario_id) REFERENCES usuario (negocio_id, id)
);

-- Equipos POS. PK compuesta: el id lo genera el cliente y no puede chocar entre negocios.
CREATE TABLE dispositivo (
                             negocio_id     UUID        NOT NULL REFERENCES negocio(id) ON DELETE RESTRICT,
                             id             UUID        NOT NULL,
                             registrado_por UUID        NOT NULL,
                             registrado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
                             ultimo_uso_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
                             activo         BOOLEAN     NOT NULL DEFAULT TRUE,
                             PRIMARY KEY (negocio_id, id),
                             CONSTRAINT fk_dispositivo_usuario FOREIGN KEY (negocio_id, registrado_por) REFERENCES usuario (negocio_id, id)
);

-- Todo registro pertenece a un negocio que existe.
ALTER TABLE producto              ADD CONSTRAINT fk_producto_negocio     FOREIGN KEY (negocio_id) REFERENCES negocio(id);
ALTER TABLE lote                  ADD CONSTRAINT fk_lote_negocio         FOREIGN KEY (negocio_id) REFERENCES negocio(id);
ALTER TABLE historial_precio      ADD CONSTRAINT fk_historial_negocio    FOREIGN KEY (negocio_id) REFERENCES negocio(id);
ALTER TABLE operacion_sync        ADD CONSTRAINT fk_operacion_negocio    FOREIGN KEY (negocio_id) REFERENCES negocio(id);
ALTER TABLE venta                 ADD CONSTRAINT fk_venta_negocio        FOREIGN KEY (negocio_id) REFERENCES negocio(id);
ALTER TABLE item_venta            ADD CONSTRAINT fk_item_negocio         FOREIGN KEY (negocio_id) REFERENCES negocio(id);
ALTER TABLE asignacion_lote       ADD CONSTRAINT fk_asignacion_negocio   FOREIGN KEY (negocio_id) REFERENCES negocio(id);
ALTER TABLE movimiento_inventario ADD CONSTRAINT fk_movimiento_negocio   FOREIGN KEY (negocio_id) REFERENCES negocio(id);
ALTER TABLE discrepancia          ADD CONSTRAINT fk_discrepancia_negocio FOREIGN KEY (negocio_id) REFERENCES negocio(id);

-- Defensa en profundidad : una fila solo puede referenciar filas
-- de SU MISMO negocio. Las llaves foráneas ignoran RLS, así que sin estas
-- llaves compuestas un negocio podría apuntar a un producto o lote ajeno.
-- Se validan al COMMIT (DEFERRABLE) para no depender del orden de los INSERT.
ALTER TABLE producto   ADD CONSTRAINT uk_producto_negocio UNIQUE (negocio_id, id);
ALTER TABLE lote       ADD CONSTRAINT uk_lote_negocio     UNIQUE (negocio_id, id);
ALTER TABLE venta      ADD CONSTRAINT uk_venta_negocio    UNIQUE (negocio_id, id);
ALTER TABLE item_venta ADD CONSTRAINT uk_item_negocio     UNIQUE (negocio_id, id);

ALTER TABLE lote                  ADD CONSTRAINT fk_lote_producto_mismo_negocio        FOREIGN KEY (negocio_id, producto_id)   REFERENCES producto (negocio_id, id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE historial_precio      ADD CONSTRAINT fk_historial_producto_mismo_negocio   FOREIGN KEY (negocio_id, producto_id)   REFERENCES producto (negocio_id, id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE item_venta            ADD CONSTRAINT fk_item_producto_mismo_negocio        FOREIGN KEY (negocio_id, producto_id)   REFERENCES producto (negocio_id, id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE item_venta            ADD CONSTRAINT fk_item_venta_mismo_negocio           FOREIGN KEY (negocio_id, venta_id)      REFERENCES venta (negocio_id, id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE asignacion_lote       ADD CONSTRAINT fk_asignacion_item_mismo_negocio      FOREIGN KEY (negocio_id, item_venta_id) REFERENCES item_venta (negocio_id, id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE asignacion_lote       ADD CONSTRAINT fk_asignacion_lote_mismo_negocio      FOREIGN KEY (negocio_id, lote_id)       REFERENCES lote (negocio_id, id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE movimiento_inventario ADD CONSTRAINT fk_movimiento_lote_mismo_negocio      FOREIGN KEY (negocio_id, lote_id)       REFERENCES lote (negocio_id, id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE discrepancia          ADD CONSTRAINT fk_discrepancia_producto_mismo_negocio FOREIGN KEY (negocio_id, producto_id)  REFERENCES producto (negocio_id, id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE discrepancia          ADD CONSTRAINT fk_discrepancia_lote_mismo_negocio    FOREIGN KEY (negocio_id, lote_id)       REFERENCES lote (negocio_id, id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE discrepancia          ADD CONSTRAINT fk_discrepancia_venta_mismo_negocio   FOREIGN KEY (negocio_id, venta_id)      REFERENCES venta (negocio_id, id) DEFERRABLE INITIALLY DEFERRED;
