-- HU-09: historial de suspensiones y reactivaciones hechas por un admin. El estado vigente sigue en
-- usuario.estado; esta tabla registra quién hizo qué, cuándo y por qué (STRIDE R).
-- usuario_id en cascada porque las pruebas borran usuarios (HU-22 anonimiza); admin_id sin cascada,
-- como revision_identidad.
CREATE TABLE suspension_cuenta (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    admin_id    BIGINT       NOT NULL REFERENCES usuario (id),
    accion      VARCHAR(20)  NOT NULL,
    motivo      VARCHAR(500),
    fecha       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_suspension_cuenta_accion CHECK (accion IN ('SUSPENSION', 'REACTIVACION')),
    CONSTRAINT ck_suspension_cuenta_motivo CHECK (
        (accion = 'REACTIVACION' AND motivo IS NULL)
        OR (accion = 'SUSPENSION' AND motivo IS NOT NULL AND length(btrim(motivo)) > 0))
);
CREATE INDEX ix_suspension_cuenta_usuario ON suspension_cuenta (usuario_id, fecha);
