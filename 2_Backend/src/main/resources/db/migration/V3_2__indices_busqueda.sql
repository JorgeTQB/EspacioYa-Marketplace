-- HU-10: búsqueda pública por tipo y zona de espacios publicados, ordenada por precio (DT-15).
CREATE INDEX ix_espacio_busqueda ON espacio (estado, tipo, zona, precio_hora, id);
