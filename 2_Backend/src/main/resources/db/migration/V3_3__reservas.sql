-- Reservas con retención temporal de bloques (HU-12).
-- cancelada_por y cancelada_en las agrega HU-15 con su propia migración.
CREATE TABLE reserva (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id           BIGINT        NOT NULL REFERENCES usuario (id),
    espacio_id           BIGINT        NOT NULL REFERENCES espacio (id),
    estado               VARCHAR(20)   NOT NULL,
    precio_hora_aplicado NUMERIC(8,2)  NOT NULL,
    monto_total          NUMERIC(10,2) NOT NULL,
    creada_en            TIMESTAMPTZ   NOT NULL,
    expira_en            TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_reserva_estado CHECK (estado IN ('PENDIENTE_PAGO', 'CONFIRMADA', 'EXPIRADA', 'CANCELADA')),
    CONSTRAINT ck_reserva_montos CHECK (precio_hora_aplicado > 0 AND monto_total > 0),
    CONSTRAINT ck_reserva_expira CHECK (expira_en > creada_en)
);
CREATE INDEX ix_reserva_usuario_estado ON reserva (usuario_id, estado);
CREATE INDEX ix_reserva_vencimiento ON reserva (expira_en) WHERE estado = 'PENDIENTE_PAGO';

CREATE TABLE reserva_bloque (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reserva_id  BIGINT   NOT NULL REFERENCES reserva (id) ON DELETE CASCADE,
    espacio_id  BIGINT   NOT NULL REFERENCES espacio (id),
    fecha       DATE     NOT NULL,
    hora_inicio SMALLINT NOT NULL,
    activo      BOOLEAN  NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_reserva_bloque_hora CHECK (hora_inicio BETWEEN 0 AND 23)
);
-- Última defensa contra la doble reserva (RNF-SEG-09): un bloque liberado deja de contar.
CREATE UNIQUE INDEX uq_reserva_bloque_activo ON reserva_bloque (espacio_id, fecha, hora_inicio) WHERE activo;
CREATE INDEX ix_reserva_bloque_reserva ON reserva_bloque (reserva_id);
