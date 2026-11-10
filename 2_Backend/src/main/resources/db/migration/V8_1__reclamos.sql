-- HU-26: un reclamo por reserva y el hilo de mensajes de sus dos partes.
CREATE TABLE reclamo (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reserva_id      BIGINT        NOT NULL,
    usuario_id      BIGINT        NOT NULL,
    arrendador_id   BIGINT        NOT NULL,
    motivo          VARCHAR(30)   NOT NULL,
    descripcion     VARCHAR(1000) NOT NULL,
    estado          VARCHAR(20)   NOT NULL,
    abierto_en      TIMESTAMPTZ   NOT NULL,
    resolucion      VARCHAR(500),
    resuelto_por    BIGINT,
    resuelto_en     TIMESTAMPTZ,
    monto_reembolso NUMERIC(10, 2),
    CONSTRAINT uq_reclamo_reserva UNIQUE (reserva_id),
    CONSTRAINT fk_reclamo_reserva FOREIGN KEY (reserva_id) REFERENCES reserva (id),
    CONSTRAINT fk_reclamo_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT fk_reclamo_arrendador FOREIGN KEY (arrendador_id) REFERENCES usuario (id),
    CONSTRAINT fk_reclamo_resuelto_por FOREIGN KEY (resuelto_por) REFERENCES usuario (id),
    CONSTRAINT ck_reclamo_motivo CHECK (motivo IN ('ESPACIO_CERRADO', 'DISTINTO_A_LO_PUBLICADO', 'OTRO')),
    CONSTRAINT ck_reclamo_estado CHECK (estado IN ('ABIERTO', 'RESUELTO_USUARIO', 'RESUELTO_ARRENDADOR')),
    CONSTRAINT ck_reclamo_descripcion CHECK (char_length(btrim(descripcion)) >= 1),
    CONSTRAINT ck_reclamo_resolucion CHECK (
        (estado = 'ABIERTO' AND resolucion IS NULL AND resuelto_por IS NULL AND resuelto_en IS NULL
            AND monto_reembolso IS NULL)
        -- IS NOT NULL explícito: un CHECK acepta una comparación con NULL (resultado desconocido).
        OR (estado = 'RESUELTO_USUARIO' AND resolucion IS NOT NULL AND char_length(btrim(resolucion)) >= 1
            AND resuelto_por IS NOT NULL AND resuelto_en IS NOT NULL AND resuelto_en >= abierto_en
            AND monto_reembolso IS NOT NULL AND monto_reembolso > 0)
        OR (estado = 'RESUELTO_ARRENDADOR' AND resolucion IS NOT NULL AND char_length(btrim(resolucion)) >= 1
            AND resuelto_por IS NOT NULL AND resuelto_en IS NOT NULL AND resuelto_en >= abierto_en
            AND monto_reembolso IS NULL))
);
-- Cola del admin (abiertos por antigüedad) y lista de resueltos.
CREATE INDEX ix_reclamo_estado ON reclamo (estado, abierto_en, id);
CREATE INDEX ix_reclamo_resueltos ON reclamo (resuelto_en DESC, id DESC) WHERE estado <> 'ABIERTO';
-- "Mis reclamos" de cada parte, exportación e impedimentos de HU-22.
CREATE INDEX ix_reclamo_usuario ON reclamo (usuario_id, abierto_en DESC, id DESC);
CREATE INDEX ix_reclamo_arrendador ON reclamo (arrendador_id, abierto_en DESC, id DESC);

CREATE TABLE mensaje_reclamo (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reclamo_id  BIGINT        NOT NULL,
    autor_id    BIGINT        NOT NULL,
    papel       VARCHAR(12)   NOT NULL,
    texto       VARCHAR(1000) NOT NULL,
    fecha       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_mensaje_reclamo_reclamo FOREIGN KEY (reclamo_id) REFERENCES reclamo (id),
    CONSTRAINT fk_mensaje_reclamo_autor FOREIGN KEY (autor_id) REFERENCES usuario (id),
    CONSTRAINT ck_mensaje_reclamo_papel CHECK (papel IN ('USUARIO', 'ARRENDADOR')),
    CONSTRAINT ck_mensaje_reclamo_texto CHECK (char_length(btrim(texto)) >= 1)
);
CREATE INDEX ix_mensaje_reclamo_reclamo ON mensaje_reclamo (reclamo_id, fecha, id);
CREATE INDEX ix_mensaje_reclamo_autor ON mensaje_reclamo (autor_id);
