-- HU-24: una reseña por reserva. estado lo usa HU-27; HU-24 solo crea VISIBLE.
CREATE TABLE resena (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reserva_id   BIGINT       NOT NULL,
    usuario_id   BIGINT       NOT NULL,
    espacio_id   BIGINT       NOT NULL,
    calificacion SMALLINT     NOT NULL,
    comentario   VARCHAR(500) NOT NULL,
    estado       VARCHAR(10)  NOT NULL,
    creado_en    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_resena_reserva UNIQUE (reserva_id),
    CONSTRAINT fk_resena_reserva FOREIGN KEY (reserva_id) REFERENCES reserva (id),
    CONSTRAINT fk_resena_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT fk_resena_espacio FOREIGN KEY (espacio_id) REFERENCES espacio (id),
    CONSTRAINT ck_resena_calificacion CHECK (calificacion BETWEEN 1 AND 5),
    CONSTRAINT ck_resena_estado CHECK (estado IN ('VISIBLE', 'OCULTA')),
    CONSTRAINT ck_resena_comentario CHECK (char_length(btrim(comentario)) >= 1)
);
-- La ficha pagina y promedia solo las visibles.
CREATE INDEX ix_resena_espacio_visible ON resena (espacio_id, creado_en DESC, id DESC) WHERE estado = 'VISIBLE';
-- Pendientes, exportación y anonimización de HU-22.
CREATE INDEX ix_resena_usuario ON resena (usuario_id);
