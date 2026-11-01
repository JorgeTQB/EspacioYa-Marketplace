-- HU-16: reembolsos contra el pago original. Un reembolso por pago y origen: una cancelación no genera dos (STRIDE T).
-- Sin cascada desde pago: es historia financiera, como pago desde reserva.
CREATE TABLE reembolso (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pago_id               BIGINT        NOT NULL REFERENCES pago (id),
    monto                 NUMERIC(10,2) NOT NULL,
    origen                VARCHAR(20)   NOT NULL,
    estado                VARCHAR(20)   NOT NULL,
    intentos              INTEGER       NOT NULL DEFAULT 0,
    creado_en             TIMESTAMPTZ   NOT NULL,
    ultimo_intento_en     TIMESTAMPTZ,
    id_reembolso_pasarela VARCHAR(100),
    CONSTRAINT uq_reembolso_pago_origen UNIQUE (pago_id, origen),
    CONSTRAINT ck_reembolso_origen CHECK (origen IN ('CANCELACION', 'RECLAMO')),
    CONSTRAINT ck_reembolso_estado CHECK (estado IN ('PENDIENTE', 'REEMBOLSADO', 'FALLIDO')),
    CONSTRAINT ck_reembolso_monto CHECK (monto > 0),
    CONSTRAINT ck_reembolso_intentos CHECK (intentos >= 0),
    -- Solo un reembolso confirmado tiene el id que dio la pasarela.
    CONSTRAINT ck_reembolso_confirmado CHECK ((estado = 'REEMBOLSADO') = (id_reembolso_pasarela IS NOT NULL)),
    -- Un resultado siempre viene de un intento registrado.
    CONSTRAINT ck_reembolso_intentado
        CHECK (estado = 'PENDIENTE' OR (intentos > 0 AND ultimo_intento_en IS NOT NULL))
);
-- Listado del admin (FALLIDO y PENDIENTE sin respuesta) sin recorrer los ya reembolsados.
CREATE INDEX ix_reembolso_por_atender ON reembolso (creado_en, id) WHERE estado IN ('PENDIENTE', 'FALLIDO');
