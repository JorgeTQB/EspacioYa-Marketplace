-- HU-02: datos de contacto del usuario y perfil del arrendador.
-- nombre y telefono son anulables: los USUARIO de HU-01 no los tienen.
ALTER TABLE usuario
    ADD COLUMN nombre   VARCHAR(100),
    ADD COLUMN telefono VARCHAR(9),
    ADD CONSTRAINT ck_usuario_telefono CHECK (telefono IS NULL OR telefono ~ '^9[0-9]{8}$');

-- numero_documento, razon_social y direccion_fiscal van cifrados con AES-256-GCM ("v1:" + base64),
-- por eso no llevan CHECK de formato. hash_documento es el HMAC-SHA256 del número normalizado:
-- da unicidad sin guardar el número en claro.
CREATE TABLE perfil_arrendador (
    usuario_id       BIGINT       PRIMARY KEY REFERENCES usuario (id) ON DELETE CASCADE,
    tipo_documento   VARCHAR(3)   NOT NULL,
    numero_documento TEXT         NOT NULL,
    hash_documento   VARCHAR(64)  NOT NULL,
    razon_social     TEXT         NOT NULL,
    direccion_fiscal TEXT         NOT NULL,
    estado_identidad VARCHAR(20)  NOT NULL,
    motivo_rechazo   VARCHAR(500),
    CONSTRAINT uq_perfil_arrendador_hash_documento UNIQUE (hash_documento),
    CONSTRAINT ck_perfil_arrendador_tipo_documento CHECK (tipo_documento IN ('DNI', 'RUC')),
    CONSTRAINT ck_perfil_arrendador_estado_identidad
        CHECK (estado_identidad IN ('NO_INICIADA', 'EN_REVISION', 'VERIFICADA', 'RECHAZADA'))
);
