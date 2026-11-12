-- HU-27: reportes de usuarios sobre un espacio o una reseña y la decisión del admin.
CREATE TABLE reporte (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reportante_id   BIGINT       NOT NULL,
    tipo_objetivo   VARCHAR(10)  NOT NULL,
    espacio_id      BIGINT,
    resena_id       BIGINT,
    motivo          VARCHAR(30)  NOT NULL,
    estado          VARCHAR(12)  NOT NULL,
    creado_en       TIMESTAMPTZ  NOT NULL,
    admin_id        BIGINT,
    motivo_decision VARCHAR(500),
    resuelto_en     TIMESTAMPTZ,
    por_fraude      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_reporte_reportante FOREIGN KEY (reportante_id) REFERENCES usuario (id),
    CONSTRAINT fk_reporte_espacio FOREIGN KEY (espacio_id) REFERENCES espacio (id),
    CONSTRAINT fk_reporte_resena FOREIGN KEY (resena_id) REFERENCES resena (id),
    CONSTRAINT fk_reporte_admin FOREIGN KEY (admin_id) REFERENCES usuario (id),
    CONSTRAINT ck_reporte_tipo CHECK (tipo_objetivo IN ('ESPACIO', 'RESENA')),
    CONSTRAINT ck_reporte_objetivo CHECK (
        (tipo_objetivo = 'ESPACIO' AND espacio_id IS NOT NULL AND resena_id IS NULL)
        OR (tipo_objetivo = 'RESENA' AND resena_id IS NOT NULL AND espacio_id IS NULL)),
    CONSTRAINT ck_reporte_motivo CHECK (motivo IN ('FRAUDE', 'INFORMACION_FALSA', 'CONTENIDO_INAPROPIADO', 'SPAM')),
    CONSTRAINT ck_reporte_estado CHECK (estado IN ('POR_REVISAR', 'MANTENIDO', 'OCULTADO')),
    -- Los IS NOT NULL explícitos evitan que un NULL vuelva la expresión NULL y el CHECK la deje pasar (R57).
    CONSTRAINT ck_reporte_decision CHECK (
        (estado = 'POR_REVISAR' AND admin_id IS NULL AND motivo_decision IS NULL AND resuelto_en IS NULL)
        OR (estado IN ('MANTENIDO', 'OCULTADO') AND admin_id IS NOT NULL
            AND motivo_decision IS NOT NULL AND char_length(btrim(motivo_decision)) >= 1
            AND resuelto_en IS NOT NULL AND resuelto_en >= creado_en)),
    -- Ocultar por fraude solo existe para un espacio ocultado (P17, R75).
    CONSTRAINT ck_reporte_fraude CHECK (NOT por_fraude OR (estado = 'OCULTADO' AND tipo_objetivo = 'ESPACIO'))
);
-- Un reporte por persona y contenido, en cualquier estado (P4).
CREATE UNIQUE INDEX uq_reporte_espacio ON reporte (reportante_id, espacio_id) WHERE espacio_id IS NOT NULL;
CREATE UNIQUE INDEX uq_reporte_resena ON reporte (reportante_id, resena_id) WHERE resena_id IS NOT NULL;
-- Cupo de 10 en 24 h y exportación de HU-22.
CREATE INDEX ix_reporte_reportante ON reporte (reportante_id, creado_en);
-- Cola del admin: por revisar por antigüedad y resueltos recientes.
CREATE INDEX ix_reporte_por_revisar ON reporte (creado_en, id) WHERE estado = 'POR_REVISAR';
CREATE INDEX ix_reporte_resueltos ON reporte (resuelto_en DESC, id DESC) WHERE estado <> 'POR_REVISAR';
-- Reportes hermanos de un contenido.
CREATE INDEX ix_reporte_espacio_estado ON reporte (espacio_id, estado) WHERE espacio_id IS NOT NULL;
CREATE INDEX ix_reporte_resena_estado ON reporte (resena_id, estado) WHERE resena_id IS NOT NULL;

-- Ocultar por fraude cancela reservas como ADMIN (R74). ck_reserva_cancelacion (V4_2) no cambia.
ALTER TABLE reserva DROP CONSTRAINT ck_reserva_cancelada_por;
ALTER TABLE reserva ADD CONSTRAINT ck_reserva_cancelada_por
    CHECK (cancelada_por IS NULL OR cancelada_por IN ('USUARIO', 'ARRENDADOR', 'ADMIN'));
-- Reservas confirmadas de un espacio (findConfirmadasParaActualizar); hoy no hay índice por espacio_id en reserva.
CREATE INDEX ix_reserva_espacio_confirmada ON reserva (espacio_id, id) WHERE estado = 'CONFIRMADA';
