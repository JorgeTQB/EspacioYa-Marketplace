-- HU-19: liquidación al arrendador. La genera el admin con un botón (sin job) y la transferencia es manual (RN-10).
-- periodo = fecha del corte en America/Lima, informativa (D1). Montos calculados solo en el servidor; los CHECK fijan
-- la comisión de 10 % (round = HALF_UP en positivos) y neto = bruto - comisión (STRIDE T). Al pagar se guarda el banco
-- y la cuenta ENMASCARADA de destino; nunca el número completo. Sin cascadas: es dato contable (HU-22 anonimiza).
CREATE TABLE liquidacion (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    arrendador_id    BIGINT        NOT NULL REFERENCES perfil_arrendador (usuario_id),
    periodo          DATE          NOT NULL,
    monto_bruto      NUMERIC(12,2) NOT NULL,
    comision         NUMERIC(12,2) NOT NULL,
    monto_neto       NUMERIC(12,2) NOT NULL,
    estado           VARCHAR(20)   NOT NULL,
    simulada         BOOLEAN       NOT NULL,
    generada_en      TIMESTAMPTZ   NOT NULL,
    generada_por     BIGINT        NOT NULL REFERENCES usuario (id),
    numero_operacion VARCHAR(30),
    pagada_en        TIMESTAMPTZ,
    pagada_por       BIGINT REFERENCES usuario (id),
    banco_destino    VARCHAR(60),
    cuenta_destino   VARCHAR(8),
    CONSTRAINT ck_liquidacion_estado CHECK (estado IN ('PENDIENTE_CUENTA', 'GENERADA', 'PAGADA')),
    CONSTRAINT ck_liquidacion_montos CHECK (monto_bruto > 0
        AND comision = round(monto_bruto * 0.10, 2)
        AND monto_neto = monto_bruto - comision),
    CONSTRAINT ck_liquidacion_pagada CHECK ((estado = 'PAGADA') = (numero_operacion IS NOT NULL
        AND pagada_en IS NOT NULL AND pagada_por IS NOT NULL
        AND banco_destino IS NOT NULL AND cuenta_destino IS NOT NULL)),
    CONSTRAINT ck_liquidacion_numero_operacion CHECK (numero_operacion IS NULL
        OR numero_operacion ~ '^[A-Za-z0-9-]{4,30}$'),
    CONSTRAINT ck_liquidacion_fechas CHECK (pagada_en IS NULL OR pagada_en >= generada_en)
);

CREATE INDEX ix_liquidacion_estado ON liquidacion (estado, generada_en, id);
CREATE INDEX ix_liquidacion_arrendador ON liquidacion (arrendador_id, generada_en, id);

-- Una reserva (y su pago) se liquida una sola vez, cualquiera que sea el estado de la liquidación (principio 7).
CREATE TABLE liquidacion_detalle (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    liquidacion_id BIGINT        NOT NULL REFERENCES liquidacion (id),
    reserva_id     BIGINT        NOT NULL REFERENCES reserva (id),
    pago_id        BIGINT        NOT NULL REFERENCES pago (id),
    monto          NUMERIC(10,2) NOT NULL,
    simulado       BOOLEAN       NOT NULL,
    CONSTRAINT uq_liquidacion_detalle_reserva UNIQUE (reserva_id),
    CONSTRAINT uq_liquidacion_detalle_pago UNIQUE (pago_id),
    CONSTRAINT ck_liquidacion_detalle_monto CHECK (monto > 0)
);

CREATE INDEX ix_liquidacion_detalle_liquidacion ON liquidacion_detalle (liquidacion_id);
