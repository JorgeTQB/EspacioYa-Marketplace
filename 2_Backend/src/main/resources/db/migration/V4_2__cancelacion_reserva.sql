-- HU-15: quién canceló una reserva, cuándo y cuánto se reembolsa (STRIDE R). Nulos mientras no se cancela.
-- La coherencia solo se exige si hay datos de cancelación: una CANCELADA sin ellos sigue siendo válida.
ALTER TABLE reserva
    ADD COLUMN cancelada_por   VARCHAR(20),
    ADD COLUMN cancelada_en    TIMESTAMPTZ,
    ADD COLUMN monto_reembolso NUMERIC(10, 2);

ALTER TABLE reserva
    ADD CONSTRAINT ck_reserva_cancelada_por
        CHECK (cancelada_por IS NULL OR cancelada_por IN ('USUARIO', 'ARRENDADOR'));

ALTER TABLE reserva
    ADD CONSTRAINT ck_reserva_cancelacion
        CHECK ((cancelada_por IS NULL AND cancelada_en IS NULL AND monto_reembolso IS NULL)
            OR (estado = 'CANCELADA' AND cancelada_por IS NOT NULL AND cancelada_en IS NOT NULL
                AND monto_reembolso IS NOT NULL AND monto_reembolso >= 0 AND monto_reembolso <= monto_total));
