-- HU-23: catálogo cerrado de características (como zona) y su relación N:M con espacio.
CREATE TABLE caracteristica (
    codigo VARCHAR(20) PRIMARY KEY,
    nombre VARCHAR(40) NOT NULL,
    CONSTRAINT uq_caracteristica_nombre UNIQUE (nombre)
);

INSERT INTO caracteristica (codigo, nombre) VALUES
    ('TECHADO', 'Techado'),
    ('ILUMINACION', 'Iluminación'),
    ('VESTUARIOS', 'Vestuarios'),
    ('ESTACIONAMIENTO', 'Estacionamiento');

-- La cascada borra las características junto con el espacio (R11).
CREATE TABLE espacio_caracteristica (
    espacio_id     BIGINT      NOT NULL,
    caracteristica VARCHAR(20) NOT NULL,
    CONSTRAINT pk_espacio_caracteristica PRIMARY KEY (espacio_id, caracteristica),
    CONSTRAINT fk_espacio_caracteristica_espacio
        FOREIGN KEY (espacio_id) REFERENCES espacio (id) ON DELETE CASCADE,
    CONSTRAINT fk_espacio_caracteristica_caracteristica
        FOREIGN KEY (caracteristica) REFERENCES caracteristica (codigo)
);
