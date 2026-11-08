-- HU-25: preferencia de recordatorios (opt-out, todas las cuentas empiezan activas) y marca de idempotencia del
-- recordatorio 24 h antes de cada reserva confirmada.
ALTER TABLE usuario ADD COLUMN recordatorios_activos BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE reserva ADD COLUMN recordatorio_enviado_en TIMESTAMPTZ;
-- Una reserva marcada puede cancelarse después, pero nunca se marca una pendiente o expirada.
ALTER TABLE reserva ADD CONSTRAINT ck_reserva_recordatorio
    CHECK (recordatorio_enviado_en IS NULL OR estado IN ('CONFIRMADA', 'CANCELADA'));

-- Candidatas del proceso: solo las confirmadas aún sin recordatorio.
CREATE INDEX ix_reserva_por_recordar ON reserva (id)
    WHERE estado = 'CONFIRMADA' AND recordatorio_enviado_en IS NULL;
