-- HU-08: horario semanal en bloques de 1 hora y fechas bloqueadas de cada espacio.
-- Los bloques libres no se guardan: se calculan al consultar (horario − bloqueos − reservas).
-- Horas como enteros: hora_inicio 0–23 y hora_fin 1–24 (exclusivo; 24 = medianoche). dia_semana ISO: 1 = lunes.
CREATE TABLE horario_semanal (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    espacio_id  BIGINT   NOT NULL REFERENCES espacio (id) ON DELETE CASCADE,
    dia_semana  SMALLINT NOT NULL,
    hora_inicio SMALLINT NOT NULL,
    hora_fin    SMALLINT NOT NULL,
    CONSTRAINT ck_horario_semanal_dia CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT ck_horario_semanal_horas CHECK (hora_inicio >= 0 AND hora_fin <= 24 AND hora_inicio < hora_fin),
    -- La superposición entre franjas la impide el servicio con el espacio bloqueado; esto evita al menos dos
    -- franjas que empiezan a la misma hora el mismo día. Sirve también de índice por espacio y día.
    CONSTRAINT uq_horario_semanal_inicio UNIQUE (espacio_id, dia_semana, hora_inicio)
);

CREATE TABLE bloqueo_fecha (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    espacio_id BIGINT NOT NULL REFERENCES espacio (id) ON DELETE CASCADE,
    fecha      DATE   NOT NULL,
    CONSTRAINT uq_bloqueo_fecha UNIQUE (espacio_id, fecha)
);
