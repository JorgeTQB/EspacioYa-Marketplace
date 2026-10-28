-- HU-05: documentos de identidad del arrendador. El contenido NO está aquí: vive cifrado en el
-- almacenamiento privado; ruta_archivo es la clave aleatoria (identidad/<UUID>).
CREATE TABLE documento_identidad (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    arrendador_id BIGINT       NOT NULL REFERENCES perfil_arrendador (usuario_id) ON DELETE CASCADE,
    tipo          VARCHAR(20)  NOT NULL,
    ruta_archivo  VARCHAR(100) NOT NULL,
    tipo_mime     VARCHAR(30)  NOT NULL,
    tamano        INTEGER      NOT NULL,
    subido_en     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_documento_identidad_arrendador_tipo UNIQUE (arrendador_id, tipo),
    CONSTRAINT uq_documento_identidad_ruta UNIQUE (ruta_archivo),
    CONSTRAINT ck_documento_identidad_tipo CHECK (tipo IN ('IDENTIDAD', 'PROPIEDAD')),
    CONSTRAINT ck_documento_identidad_mime CHECK (tipo_mime IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT ck_documento_identidad_tamano CHECK (tamano BETWEEN 1 AND 5242880)
);
