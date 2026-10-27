CREATE TABLE auditoria (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT REFERENCES usuario (id),
    accion     VARCHAR(60) NOT NULL,
    entidad    VARCHAR(60),
    entidad_id BIGINT,
    ip         VARCHAR(45),
    fecha      TIMESTAMPTZ NOT NULL,
    detalle    TEXT
);
CREATE INDEX ix_auditoria_usuario ON auditoria (usuario_id);
CREATE INDEX ix_auditoria_fecha ON auditoria (fecha);
