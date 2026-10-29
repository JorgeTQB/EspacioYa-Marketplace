-- HU-06: historial de decisiones del admin sobre la identidad de un arrendador. El estado vigente
-- sigue en perfil_arrendador; esta tabla registra quién decidió qué, cuándo y por qué (STRIDE R).
CREATE TABLE revision_identidad (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    arrendador_id BIGINT       NOT NULL REFERENCES perfil_arrendador (usuario_id) ON DELETE CASCADE,
    admin_id      BIGINT       NOT NULL REFERENCES usuario (id),
    decision      VARCHAR(20)  NOT NULL,
    motivo        VARCHAR(500),
    fecha         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_revision_identidad_decision CHECK (decision IN ('APROBADA', 'RECHAZADA')),
    CONSTRAINT ck_revision_identidad_motivo CHECK (
        (decision = 'APROBADA' AND motivo IS NULL)
        OR (decision = 'RECHAZADA' AND motivo IS NOT NULL AND length(btrim(motivo)) > 0))
);
CREATE INDEX ix_revision_identidad_arrendador ON revision_identidad (arrendador_id, fecha);
