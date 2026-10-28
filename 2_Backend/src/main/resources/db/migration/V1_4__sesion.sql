-- HU-03: sesiones de inicio de sesión. Solo se guarda el SHA-256 del token (hex), nunca el token en claro.
-- expira_en se mueve en cada solicitud autenticada (30 min de inactividad). Las vencidas no se borran:
-- se rechazan al consultarlas. ON DELETE CASCADE porque las pruebas borran usuarios; HU-22 anonimiza.
CREATE TABLE sesion (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    hash_token CHAR(64)    NOT NULL,
    creada_en  TIMESTAMPTZ NOT NULL,
    expira_en  TIMESTAMPTZ NOT NULL,
    revocada   BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_sesion_hash_token UNIQUE (hash_token),
    CONSTRAINT ck_sesion_hash_token CHECK (hash_token ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_sesion_expira CHECK (expira_en > creada_en)
);

CREATE INDEX ix_sesion_usuario ON sesion (usuario_id);
