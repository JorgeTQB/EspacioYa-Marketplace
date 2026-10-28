-- HU-07: espacios del arrendador, sus fotos y el catálogo de zonas.
-- Las fotos NO están aquí: viven sin cifrar en el directorio de almacenamiento (no son sensibles,
-- decisión del dueño); ruta_archivo es la clave aleatoria (espacios/<UUID>) y su UUID forma la URL
-- estable /api/fotos/<UUID>.
CREATE TABLE zona (
    codigo VARCHAR(40) PRIMARY KEY,
    nombre VARCHAR(60) NOT NULL,
    CONSTRAINT uq_zona_nombre UNIQUE (nombre)
);

INSERT INTO zona (codigo, nombre) VALUES
    -- Lima Metropolitana (43 distritos)
    ('ANCON', 'Ancón'), ('ATE', 'Ate'), ('BARRANCO', 'Barranco'), ('BRENA', 'Breña'),
    ('CARABAYLLO', 'Carabayllo'), ('CHACLACAYO', 'Chaclacayo'), ('CHORRILLOS', 'Chorrillos'),
    ('CIENEGUILLA', 'Cieneguilla'), ('COMAS', 'Comas'), ('EL_AGUSTINO', 'El Agustino'),
    ('INDEPENDENCIA', 'Independencia'), ('JESUS_MARIA', 'Jesús María'), ('LA_MOLINA', 'La Molina'),
    ('LA_VICTORIA', 'La Victoria'), ('LIMA', 'Lima (Cercado)'), ('LINCE', 'Lince'),
    ('LOS_OLIVOS', 'Los Olivos'), ('LURIGANCHO', 'Lurigancho-Chosica'), ('LURIN', 'Lurín'),
    ('MAGDALENA_DEL_MAR', 'Magdalena del Mar'), ('MIRAFLORES', 'Miraflores'),
    ('PACHACAMAC', 'Pachacámac'), ('PUCUSANA', 'Pucusana'), ('PUEBLO_LIBRE', 'Pueblo Libre'),
    ('PUENTE_PIEDRA', 'Puente Piedra'), ('PUNTA_HERMOSA', 'Punta Hermosa'),
    ('PUNTA_NEGRA', 'Punta Negra'), ('RIMAC', 'Rímac'), ('SAN_BARTOLO', 'San Bartolo'),
    ('SAN_BORJA', 'San Borja'), ('SAN_ISIDRO', 'San Isidro'),
    ('SAN_JUAN_DE_LURIGANCHO', 'San Juan de Lurigancho'),
    ('SAN_JUAN_DE_MIRAFLORES', 'San Juan de Miraflores'), ('SAN_LUIS', 'San Luis'),
    ('SAN_MARTIN_DE_PORRES', 'San Martín de Porres'), ('SAN_MIGUEL', 'San Miguel'),
    ('SANTA_ANITA', 'Santa Anita'), ('SANTA_MARIA_DEL_MAR', 'Santa María del Mar'),
    ('SANTA_ROSA', 'Santa Rosa'), ('SANTIAGO_DE_SURCO', 'Santiago de Surco'),
    ('SURQUILLO', 'Surquillo'), ('VILLA_EL_SALVADOR', 'Villa El Salvador'),
    ('VILLA_MARIA_DEL_TRIUNFO', 'Villa María del Triunfo'),
    -- Callao (7 distritos)
    ('CALLAO', 'Callao'), ('BELLAVISTA', 'Bellavista'),
    ('CARMEN_DE_LA_LEGUA_REYNOSO', 'Carmen de la Legua-Reynoso'), ('LA_PERLA', 'La Perla'),
    ('LA_PUNTA', 'La Punta'), ('MI_PERU', 'Mi Perú'), ('VENTANILLA', 'Ventanilla');

CREATE TABLE espacio (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    arrendador_id  BIGINT        NOT NULL REFERENCES perfil_arrendador (usuario_id) ON DELETE CASCADE,
    nombre         VARCHAR(100)  NOT NULL,
    tipo           VARCHAR(20)   NOT NULL,
    direccion      VARCHAR(200)  NOT NULL,
    zona           VARCHAR(40)   NOT NULL REFERENCES zona (codigo),
    capacidad      INTEGER       NOT NULL,
    precio_hora    NUMERIC(8, 2) NOT NULL,
    descripcion    VARCHAR(2000) NOT NULL,
    estado         VARCHAR(20)   NOT NULL,
    creado_en      TIMESTAMPTZ   NOT NULL,
    actualizado_en TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_espacio_tipo CHECK (tipo IN ('CANCHA', 'ESTADIO', 'AUDITORIO')),
    CONSTRAINT ck_espacio_estado CHECK (estado IN ('BORRADOR', 'PUBLICADO', 'PAUSADO', 'BAJA', 'OCULTO')),
    CONSTRAINT ck_espacio_capacidad CHECK (capacidad BETWEEN 1 AND 100000),
    CONSTRAINT ck_espacio_precio_hora CHECK (precio_hora BETWEEN 1 AND 100000)
);
CREATE INDEX ix_espacio_arrendador ON espacio (arrendador_id);

CREATE TABLE foto_espacio (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    espacio_id   BIGINT       NOT NULL REFERENCES espacio (id) ON DELETE CASCADE,
    ruta_archivo VARCHAR(100) NOT NULL,
    tipo_mime    VARCHAR(30)  NOT NULL,
    orden        SMALLINT     NOT NULL,
    CONSTRAINT uq_foto_espacio_ruta UNIQUE (ruta_archivo),
    -- Diferida: al quitar una foto se renumeran las siguientes dentro de la misma transacción.
    CONSTRAINT uq_foto_espacio_orden UNIQUE (espacio_id, orden) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_foto_espacio_orden CHECK (orden BETWEEN 1 AND 5),
    CONSTRAINT ck_foto_espacio_mime CHECK (tipo_mime IN ('image/jpeg', 'image/png'))
);
