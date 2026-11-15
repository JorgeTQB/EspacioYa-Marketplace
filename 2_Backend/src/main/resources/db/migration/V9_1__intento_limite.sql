-- HU-30: intentos del limitador compartidos entre réplicas. Una fila por clave con los instantes vigentes
-- (ventana deslizante). La clave se guarda como HMAC-SHA256 hex (HuellaCampos), nunca en claro.
-- Sin índice en expira_en: así cada intento es una actualización HOT (fillfactor 70) y la limpieza recorre una
-- tabla pequeña (solo claves con intentos en la última hora).
CREATE TABLE intento_limite (
    clave_hash CHAR(64)      NOT NULL,
    instantes  TIMESTAMPTZ[] NOT NULL,
    expira_en  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_intento_limite PRIMARY KEY (clave_hash),
    CONSTRAINT ck_intento_limite_clave_hash CHECK (clave_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_intento_limite_instantes CHECK (cardinality(instantes) >= 1)
) WITH (fillfactor = 70);
