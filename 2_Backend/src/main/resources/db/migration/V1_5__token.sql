-- HU-04: tokens de un solo uso (recuperación de contraseña). Solo se guarda el SHA-256 del token (hex),
-- nunca el token en claro. Usar o anular un token fija usado_en; las filas no se borran.
-- El CHECK de tipo admite los dos valores del modelo de datos; hoy el código solo usa RECUPERACION.
-- ON DELETE CASCADE porque las pruebas borran usuarios; HU-22 anonimiza.
CREATE TABLE token (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    tipo       VARCHAR(20) NOT NULL,
    hash_token CHAR(64)    NOT NULL,
    creado_en  TIMESTAMPTZ NOT NULL,
    expira_en  TIMESTAMPTZ NOT NULL,
    usado_en   TIMESTAMPTZ,
    CONSTRAINT uq_token_hash_token UNIQUE (hash_token),
    CONSTRAINT ck_token_hash_token CHECK (hash_token ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_token_tipo CHECK (tipo IN ('VERIFICACION', 'RECUPERACION')),
    CONSTRAINT ck_token_expira CHECK (expira_en > creado_en)
);

CREATE INDEX ix_token_usuario ON token (usuario_id);
