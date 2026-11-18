-- HU-20: edición de perfil. correo_pendiente = correo pedido y aún no confirmado (uno a la vez; el correo de la cuenta
-- no cambia hasta confirmar). Sin UNIQUE a propósito: un índice único delataría que el correo existe (RNF-SEG-13); la
-- unicidad real la sigue dando uq_usuario_correo al confirmar. La foto de perfil es privada y va cifrada fuera de la
-- BD (ArchivosPrivados, carpeta "perfiles"): aquí solo su clave y su tipo real.
ALTER TABLE usuario
    ADD COLUMN correo_pendiente VARCHAR(254),
    ADD COLUMN foto_ruta        VARCHAR(60),
    ADD COLUMN foto_tipo_mime   VARCHAR(20);

ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_correo_pendiente CHECK (
        correo_pendiente IS NULL
        OR (correo_pendiente = lower(btrim(correo_pendiente)) AND correo_pendiente <> correo)),
    -- IS NOT NULL explícito: con una sola columna nula la otra rama daría NULL y el CHECK la dejaría pasar.
    ADD CONSTRAINT ck_usuario_foto CHECK (
        (foto_ruta IS NULL AND foto_tipo_mime IS NULL)
        OR (foto_ruta IS NOT NULL AND foto_tipo_mime IS NOT NULL
            AND foto_ruta ~ '^perfiles/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            AND foto_tipo_mime IN ('image/jpeg', 'image/png')));

-- Token de cambio de correo (HU-20). VERIFICACION queda para el correo de registro (HU-25).
ALTER TABLE token DROP CONSTRAINT ck_token_tipo;
ALTER TABLE token
    ADD CONSTRAINT ck_token_tipo CHECK (tipo IN ('VERIFICACION', 'RECUPERACION', 'CAMBIO_CORREO'));
