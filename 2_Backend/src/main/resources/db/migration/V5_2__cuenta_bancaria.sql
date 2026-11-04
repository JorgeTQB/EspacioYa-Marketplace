-- HU-18: cuenta bancaria del arrendador. Una sola cuenta vigente por arrendador (el cambio la reemplaza; el
-- historial vive en la auditoría). El número va cifrado con AES-256-GCM ("v1:" + base64, CifradorCampos): sin CHECK
-- de formato. retener_hasta: la siguiente liquidación no se paga antes de esa fecha (RN-11); nulo si nunca cambió.
-- Sin cascada desde usuario: es dato financiero, como pago. HU-22 anonimiza.
CREATE TABLE cuenta_bancaria (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    arrendador_id  BIGINT       NOT NULL REFERENCES perfil_arrendador (usuario_id),
    banco          VARCHAR(60)  NOT NULL,
    numero         TEXT         NOT NULL,
    titular        VARCHAR(150) NOT NULL,
    retener_hasta  TIMESTAMPTZ,
    creada_en      TIMESTAMPTZ  NOT NULL,
    actualizada_en TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_cuenta_bancaria_arrendador UNIQUE (arrendador_id),
    CONSTRAINT ck_cuenta_bancaria_banco CHECK (length(btrim(banco)) >= 2),
    CONSTRAINT ck_cuenta_bancaria_titular CHECK (length(btrim(titular)) >= 1),
    CONSTRAINT ck_cuenta_bancaria_fechas CHECK (actualizada_en >= creada_en)
);
