CREATE TABLE usuario (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    correo               VARCHAR(254) NOT NULL,
    hash_contrasena      VARCHAR(100) NOT NULL,
    rol                  VARCHAR(20)  NOT NULL,
    correo_verificado    BOOLEAN      NOT NULL DEFAULT FALSE,
    estado               VARCHAR(20)  NOT NULL,
    version_politica     VARCHAR(20)  NOT NULL,
    fecha_consentimiento TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_usuario_correo UNIQUE (correo),
    CONSTRAINT ck_usuario_correo_normalizado CHECK (correo = lower(btrim(correo))),
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('USUARIO', 'ARRENDADOR', 'ADMIN')),
    CONSTRAINT ck_usuario_estado CHECK (estado IN ('ACTIVA', 'SUSPENDIDA', 'ELIMINADA'))
);
