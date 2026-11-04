-- Pagos con tarjeta (HU-13). id_transaccion_pasarela guarda el id del pago en la pasarela: cada intento tiene el suyo.
CREATE TABLE pago (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reserva_id              BIGINT        NOT NULL REFERENCES reserva (id),
    id_transaccion_pasarela VARCHAR(100)  NOT NULL,
    monto                   NUMERIC(10,2) NOT NULL,
    estado                  VARCHAR(20)   NOT NULL,
    ultimos4                VARCHAR(4),
    creado_en               TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_pago_id_transaccion UNIQUE (id_transaccion_pasarela),
    CONSTRAINT ck_pago_estado CHECK (estado IN ('PAGADO', 'RECHAZADO', 'PAGADO_TARDIO', 'MONTO_DISTINTO',
                                                'REEMBOLSADO', 'REEMBOLSO_FALLIDO')),
    CONSTRAINT ck_pago_monto CHECK (monto > 0),
    CONSTRAINT ck_pago_ultimos4 CHECK (ultimos4 ~ '^[0-9]{4}$')
);
CREATE INDEX ix_pago_reserva_estado ON pago (reserva_id, estado, creado_en);
-- Una reserva no se cobra dos veces (HU-15 y HU-19 leen este pago).
CREATE UNIQUE INDEX uq_pago_reserva_pagado ON pago (reserva_id) WHERE estado = 'PAGADO';

-- Un enlace por reserva: su vencimiento depende solo de reserva.expira_en.
CREATE TABLE checkout_pago (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reserva_id  BIGINT       NOT NULL REFERENCES reserva (id) ON DELETE CASCADE,
    id_checkout VARCHAR(100) NOT NULL,
    url_pago    TEXT         NOT NULL,
    creado_en   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_checkout_pago_reserva UNIQUE (reserva_id)
);
