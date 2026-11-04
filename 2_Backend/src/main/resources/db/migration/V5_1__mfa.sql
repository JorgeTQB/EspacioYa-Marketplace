-- HU-17: MFA (TOTP, RFC 6238). Tablas de Release 2: no se editan las de Release 1.
-- secreto va cifrado con AES-256-GCM ("v1:" + base64, CifradorCampos): sin CHECK de formato.
-- Una fila por cuenta: activo = false es una configuración pendiente (secreto generado, sin confirmar).
-- ultimo_paso: último paso TOTP aceptado; un código de un paso igual o anterior no se acepta (RFC 6238 § 5.2).
-- ON DELETE CASCADE porque las pruebas borran usuarios; HU-22 anonimiza.
CREATE TABLE mfa (
    usuario_id  BIGINT      PRIMARY KEY REFERENCES usuario (id) ON DELETE CASCADE,
    secreto     TEXT        NOT NULL,
    activo      BOOLEAN     NOT NULL DEFAULT FALSE,
    creado_en   TIMESTAMPTZ NOT NULL,
    activado_en TIMESTAMPTZ,
    ultimo_paso BIGINT,
    CONSTRAINT ck_mfa_activado CHECK ((activo AND activado_en IS NOT NULL) OR (NOT activo AND activado_en IS NULL)),
    CONSTRAINT ck_mfa_ultimo_paso CHECK (ultimo_paso IS NULL OR ultimo_paso >= 0)
);

-- hash_codigo: HMAC-SHA256 hex (HuellaCampos) del código normalizado; el código nunca se guarda en claro.
-- Cuelga de mfa: borrar la configuración borra sus códigos.
CREATE TABLE codigo_respaldo (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES mfa (usuario_id) ON DELETE CASCADE,
    hash_codigo CHAR(64)    NOT NULL,
    creado_en   TIMESTAMPTZ NOT NULL,
    usado_en    TIMESTAMPTZ,
    CONSTRAINT uq_codigo_respaldo_usuario_hash UNIQUE (usuario_id, hash_codigo),
    CONSTRAINT ck_codigo_respaldo_hash CHECK (hash_codigo ~ '^[0-9a-f]{64}$')
);

-- Paso intermedio del inicio de sesión: solo el SHA-256 hex del token. Vigencia de 5 min, un solo uso,
-- 5 códigos incorrectos como máximo. Las filas no se borran: se rechazan al consultarlas.
CREATE TABLE desafio_mfa (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id        BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    hash_token        CHAR(64)    NOT NULL,
    paso              VARCHAR(20) NOT NULL,
    creado_en         TIMESTAMPTZ NOT NULL,
    expira_en         TIMESTAMPTZ NOT NULL,
    intentos_fallidos INTEGER     NOT NULL DEFAULT 0,
    cerrado_en        TIMESTAMPTZ,
    CONSTRAINT uq_desafio_mfa_hash_token UNIQUE (hash_token),
    CONSTRAINT ck_desafio_mfa_hash_token CHECK (hash_token ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_desafio_mfa_paso CHECK (paso IN ('CODIGO', 'CONFIGURACION')),
    CONSTRAINT ck_desafio_mfa_expira CHECK (expira_en > creado_en),
    CONSTRAINT ck_desafio_mfa_intentos CHECK (intentos_fallidos BETWEEN 0 AND 5)
);

CREATE INDEX ix_desafio_mfa_usuario ON desafio_mfa (usuario_id);
