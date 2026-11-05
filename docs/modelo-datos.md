# Modelo de datos

> Modelo de referencia para PostgreSQL 17. Cada historia crea **solo** las
> tablas que necesita, con su propia migración Flyway (ver
> `docs/conventions.md`). Si el `design.md` de una historia necesita cambiar un
> nombre, un atributo o un estado de este modelo, lo justifica ahí y actualiza
> este archivo en el mismo PR.

## Entidades principales

| Grupo | Entidad | Atributos clave | Historias |
| --- | --- | --- | --- |
| Cuentas | `usuario` | id, nombre, correo (único), hash_contrasena, telefono, correo_pendiente (`VARCHAR(254)`, el correo pedido y aún no confirmado, normalizado y distinto del actual; uno a la vez y **sin** `UNIQUE` para no delatar si un correo existe; HU-20), foto_ruta (`VARCHAR(60)`, clave `perfiles/<UUID>` de `ArchivosPrivados`: la foto es privada y va cifrada, sin URL; reemplaza al `foto_url` del modelo original) y foto_tipo_mime (`image/jpeg` o `image/png`), rol (USUARIO, ARRENDADOR, ADMIN), correo_verificado, estado (ACTIVA, SUSPENDIDA, ELIMINADA), version_politica, fecha_consentimiento, recordatorios_activos | HU-01, HU-02, HU-09, HU-20, HU-25 |
| | `perfil_arrendador` | usuario_id, tipo_documento (DNI, RUC), numero_documento (cifrado), hash_documento (único, HMAC), razon_social (cifrada), direccion_fiscal (cifrada), estado_identidad (NO_INICIADA, EN_REVISION, VERIFICADA, RECHAZADA), motivo_rechazo | HU-02, HU-05, HU-06 |
| | `documento_identidad` | id, arrendador_id, tipo (IDENTIDAD, PROPIEDAD), ruta_archivo (clave aleatoria `identidad/<UUID>` del almacenamiento privado; el archivo va cifrado fuera de la BD y el nombre original no se guarda), tipo_mime (application/pdf, image/jpeg, image/png, según el contenido real), tamano (bytes recibidos, 1 a 5 242 880), subido_en. Solo los documentos vigentes: un reenvío reemplaza los anteriores | HU-05, HU-06 |
| | `token` | id, usuario_id, tipo (VERIFICACION, RECUPERACION, CAMBIO_CORREO; hoy se usan RECUPERACION y CAMBIO_CORREO, VERIFICACION queda para HU-25), hash_token (único, SHA-256 hex del token; el token nunca se guarda en claro), creado_en, expira_en (creado_en + 30 min en RECUPERACION y en CAMBIO_CORREO), usado_en (al usarlo o anularlo; las filas no se borran). CAMBIO_CORREO no guarda el destino: vive en `usuario.correo_pendiente` (`V6_1`) | HU-01, HU-04, HU-20 |
| | `sesion` | id, usuario_id, hash_token (único, SHA-256 hex del token; el token nunca se guarda en claro), creada_en, expira_en (se mueve a ahora + 30 min en cada solicitud autenticada), revocada | HU-03, HU-04, HU-09 |
| | `mfa` | usuario_id (PK y FK a `usuario`, en cascada), secreto (`TEXT`, Base32 de 20 bytes cifrado con AES-256-GCM `v1:`; nunca en claro), activo (`false` = configuración pendiente, secreto generado sin confirmar), creado_en, activado_en (solo si activo), ultimo_paso (último paso TOTP aceptado: un código de un paso igual o anterior no vale, RFC 6238 § 5.2). Una fila por cuenta (`V5_1`) | HU-17 |
| | `codigo_respaldo` | id, usuario_id (FK a `mfa`, en cascada: desactivar MFA borra sus códigos), hash_codigo (`CHAR(64)`, HMAC-SHA256 hex del código normalizado; el código nunca se guarda en claro), creado_en, usado_en (`TIMESTAMPTZ` al usarlo; reemplaza al `usado` booleano del modelo original, como `token.usado_en`). 5 por activación | HU-17 |
| | `desafio_mfa` | id, usuario_id (FK a `usuario`, en cascada), hash_token (único, SHA-256 hex del desafío del paso intermedio del inicio de sesión; nunca en claro), paso (CODIGO, CONFIGURACION), creado_en, expira_en (creado_en + 5 min), intentos_fallidos (0–5; al 5.º se cierra), cerrado_en (al canjearlo o agotarlo). No es una sesión; las filas no se borran: se rechazan al consultarlas | HU-17 |
| Espacios | `zona` | codigo (PK, p. ej. `MIRAFLORES`), nombre (único, con tildes). Catálogo cerrado sembrado por la migración: 43 distritos de Lima Metropolitana y 7 del Callao | HU-07, HU-10 |
| | `espacio` | id, arrendador_id, nombre (3–100), tipo (CANCHA, ESTADIO, AUDITORIO), direccion (5–200), zona (FK a `zona.codigo`), capacidad (1–100 000), precio_hora (`NUMERIC(8,2)`, S/ 1.00–100 000.00, un único precio por hora), descripcion (obligatoria, 10–2000), estado (BORRADOR, PUBLICADO, PAUSADO, BAJA, OCULTO), creado_en, actualizado_en. Índice `ix_espacio_busqueda` (estado, tipo, zona, precio_hora, id) para la búsqueda pública (HU-10, solo rendimiento) | HU-07, HU-10, HU-21, HU-27 |
| | `foto_espacio` | id, espacio_id, ruta_archivo (clave aleatoria `espacios/<UUID>` del almacenamiento; el archivo va **sin cifrar** fuera de la BD, re-codificado y sin metadatos, y su UUID forma la URL estable `/api/fotos/<UUID>`; el nombre original no se guarda), tipo_mime (image/jpeg, image/png, según el contenido real), orden (1–5; la principal es la 1) | HU-07 |
| | `horario_semanal` | id, espacio_id (FK en cascada), dia_semana (`SMALLINT`, ISO 1 = lunes … 7 = domingo), hora_inicio (`SMALLINT` 0–23), hora_fin (`SMALLINT` 1–24, exclusivo; 24 = medianoche). Varias franjas por día sin superponerse; el horario se reemplaza completo. Los bloques libres no se guardan: se calculan al consultar | HU-08 |
| | `bloqueo_fecha` | id, espacio_id (FK en cascada), fecha (`DATE`, día completo; se bloquea solo dentro de las próximas 8 semanas y se puede desbloquear) | HU-08 |
| | `caracteristica` / `espacio_caracteristica` | id, nombre / espacio_id + caracteristica_id | HU-23 |
| Reservas | `reserva` | id, usuario_id (FK a `usuario`, sin cascada), espacio_id (FK a `espacio`, sin cascada), estado (`VARCHAR(20)`: PENDIENTE_PAGO, CONFIRMADA, EXPIRADA, CANCELADA), precio_hora_aplicado (`NUMERIC(8,2)`, copia del precio al crear), monto_total (`NUMERIC(10,2)`: 24 × S/ 100 000.00 no cabe en `(8,2)`), creada_en, expira_en (`TIMESTAMPTZ`; creada_en + 15 min), cancelada_por (`VARCHAR(20)`: USUARIO, ARRENDADOR), cancelada_en (`TIMESTAMPTZ`), monto_reembolso (`NUMERIC(10,2)`, lo que calculó la política de RN-06); las tres nulas mientras la reserva no se cancela (HU-15, `V4_2`) | HU-12, HU-14, HU-15 |
| | `reserva_bloque` | id, reserva_id (FK a `reserva`, en cascada), espacio_id (FK a `espacio`, sin cascada), fecha (`DATE`), hora_inicio (`SMALLINT` 0–23, la misma granularidad que `horario_semanal`, HU-08), activo (`BOOLEAN`, por defecto `TRUE`). Liberar un bloque pone `activo = false`: las filas se conservan porque HU-14 muestra el horario de las reservas pasadas | HU-12 |
| Pagos | `pago` | id, reserva_id (FK a `reserva`, sin cascada), id_transaccion_pasarela (`VARCHAR(100)`, único, id del pago en la pasarela: cada intento tiene el suyo), monto (`NUMERIC(10,2)`), estado (PAGADO, RECHAZADO, PAGADO_TARDIO, MONTO_DISTINTO, REEMBOLSADO, REEMBOLSO_FALLIDO), ultimos4 (`VARCHAR(4)`, nulo si la pasarela no lo informa), creado_en (`TIMESTAMPTZ`) | HU-13, HU-28 |
| | `checkout_pago` | id, reserva_id (FK a `reserva`, en cascada, único), id_checkout, url_pago, creado_en. Un enlace por reserva, para reutilizarlo y no dejar dos cobros abiertos; no tiene estado porque su vencimiento depende solo de `reserva.expira_en` | HU-13 |
| | `reembolso` | id, pago_id (FK a `pago`, sin cascada), monto (`NUMERIC(10,2)`), origen (CANCELACION, RECLAMO), estado (PENDIENTE, REEMBOLSADO, FALLIDO; fuente de verdad del reembolso: `pago.estado` cambia en la misma transacción), intentos (`INTEGER`, 0 al crearse), creado_en (`TIMESTAMPTZ`), ultimo_intento_en (`TIMESTAMPTZ`, nulo hasta el primer intento), id_reembolso_pasarela (`VARCHAR(100)`, solo en `REEMBOLSADO`). Índice parcial `ix_reembolso_por_atender (creado_en, id)` sobre `PENDIENTE` y `FALLIDO` | HU-16, HU-26 |
| | `cuenta_bancaria` | id, arrendador_id (FK a `perfil_arrendador(usuario_id)`, sin cascada; `uq_cuenta_bancaria_arrendador`: una sola cuenta vigente por arrendador, el cambio la reemplaza y el historial vive en la auditoría), banco (`VARCHAR(60)`, `CHECK` de al menos 2 caracteres), numero (`TEXT`, cifrado AES-256-GCM con prefijo `v1:` vía `ConvertidorCifrado`; los 4 últimos dígitos se calculan al descifrar), titular (`VARCHAR(150)`, la razón social del perfil, `CHECK` no vacío), retener_hasta (`TIMESTAMPTZ`, nulo si nunca cambió; el cambio lo fija en ahora + 72 h, RN-11), creada_en, actualizada_en (`TIMESTAMPTZ`, `CHECK` actualizada_en >= creada_en). El estado `DISPONIBLE`/`RETENIDA` no se guarda: se calcula con `retener_hasta` y el reloj (`V5_2`) | HU-18, HU-19, HU-22 |
| | `liquidacion` | id, arrendador_id (FK a `perfil_arrendador(usuario_id)`, sin cascada: dato contable, HU-22 anonimiza), periodo (`DATE`, fecha del corte en `America/Lima`, informativa: cada generación liquida todo lo pendiente), monto_bruto, comision, monto_neto (`NUMERIC(12,2)`, calculados en el servidor), estado (`VARCHAR(20)`: PENDIENTE_CUENTA, GENERADA, PAGADA), simulada (`BOOLEAN`, alguna línea es de la pasarela simulada), generada_en (`TIMESTAMPTZ`, el corte), generada_por (FK a `usuario`, el admin), numero_operacion (`VARCHAR(30)`), pagada_en (`TIMESTAMPTZ`), pagada_por (FK a `usuario`), banco_destino (`VARCHAR(60)`) y cuenta_destino (`VARCHAR(8)`, **enmascarada** `****1234`; nunca el número completo); las cinco últimas solo en PAGADA (`V5_3`) | HU-19, HU-22 |
| | `liquidacion_detalle` | id, liquidacion_id (FK a `liquidacion`), reserva_id (FK a `reserva`, única), pago_id (FK a `pago`, única: el pago `PAGADO` que se liquida), monto (`NUMERIC(10,2)`, el `pago.monto`), simulado (`BOOLEAN`, el pago es `sim-`). Sin cascadas (`V5_3`) | HU-19 |
| Confianza | `resena` | id, reserva_id (único), usuario_id, espacio_id, calificacion (1–5), comentario, estado (VISIBLE, OCULTA) | HU-24, HU-27 |
| | `reclamo` / `mensaje_reclamo` | id, reserva_id (único), motivo, descripcion, estado, resolucion / reclamo_id, autor_id, texto, fecha | HU-26 |
| | `reporte` | id, reportante_id, tipo_objetivo (ESPACIO, RESENA), objetivo_id, motivo, estado, admin_id, decision | HU-27 |
| Administración | `revision_identidad` | id, arrendador_id (FK a `perfil_arrendador`, en cascada), admin_id (FK a `usuario`, sin cascada), decision (APROBADA, RECHAZADA), motivo (hasta 500; obligatorio y no en blanco solo si RECHAZADA, nulo si APROBADA), fecha. Historial de decisiones del admin: el estado vigente sigue en `perfil_arrendador` | HU-06 |
| | `suspension_cuenta` | id, usuario_id (FK a `usuario`, en cascada), admin_id (FK a `usuario`, sin cascada), accion (SUSPENSION, REACTIVACION), motivo (hasta 500; obligatorio y no en blanco solo si SUSPENSION, nulo si REACTIVACION), fecha. Historial de suspensiones y reactivaciones del admin: el estado vigente sigue en `usuario.estado` | HU-09 |
| Transversal | `auditoria` | id, usuario_id, accion, entidad, entidad_id, ip, fecha, detalle | Todas (R de STRIDE) |

## Restricciones clave

| Restricción | Regla de negocio | Historia |
| --- | --- | --- |
| Bloque único: `uq_reserva_bloque_activo` en (espacio_id, fecha, hora_inicio) solo para bloques activos (índice único parcial `WHERE activo`) | Dos usuarios no pueden reservar el mismo bloque | HU-12 |
| `reserva`: `CHECK` de estado (`ck_reserva_estado`), de montos positivos (`ck_reserva_montos`) y de `expira_en > creada_en` (`ck_reserva_expira`); `reserva_bloque`: `CHECK` de `hora_inicio` entre 0 y 23 (`ck_reserva_bloque_hora`). Índices `ix_reserva_usuario_estado` (usuario_id, estado), `ix_reserva_vencimiento` (expira_en, `WHERE estado = 'PENDIENTE_PAGO'`) e `ix_reserva_bloque_reserva` (reserva_id) | Una reserva nace con valores válidos y el cupo y la expiración se consultan sin recorrer la tabla | HU-12 |
| `reserva`: `CHECK` de `cancelada_por` en USUARIO o ARRENDADOR (`ck_reserva_cancelada_por`) y de coherencia de la cancelación (`ck_reserva_cancelacion`): o las tres columnas de cancelación son nulas, o la reserva está `CANCELADA` con las tres llenas y `0 ≤ monto_reembolso ≤ monto_total`. Una `CANCELADA` sin datos de cancelación sigue siendo válida (semillas de pruebas de HU-12 y HU-14) | Quién canceló, cuándo y cuánto se reembolsa queda registrado y es coherente (STRIDE R) | HU-15 |
| `pago.id_transaccion_pasarela` único y `NOT NULL` (`uq_pago_id_transaccion`) | El webhook es idempotente: un pago no se registra dos veces | HU-13 |
| Índice único parcial `uq_pago_reserva_pagado` en (reserva_id) `WHERE estado = 'PAGADO'` | Una reserva no se cobra dos veces; admite varios rechazos | HU-13 |
| `pago`: `CHECK` de estado (`ck_pago_estado`), de monto positivo (`ck_pago_monto`) y de `ultimos4` con 4 dígitos (`ck_pago_ultimos4`). Índice `ix_pago_reserva_estado` (reserva_id, estado, creado_en) | Un pago nace con valores válidos y los rechazos recientes se cuentan sin recorrer la tabla | HU-13 |
| `checkout_pago.reserva_id` único (`uq_checkout_pago_reserva`) | Una reserva tiene un solo enlace de pago | HU-13 |
| `reembolso`: `UNIQUE (pago_id, origen)` (`uq_reembolso_pago_origen`) | Un reembolso por pago y origen: una cancelación no genera dos reembolsos (STRIDE T) | HU-16 |
| `reembolso`: `CHECK` de origen (`ck_reembolso_origen`), estado (`ck_reembolso_estado`), `monto > 0` (`ck_reembolso_monto`) e `intentos >= 0` (`ck_reembolso_intentos`); `ck_reembolso_confirmado`: tiene `id_reembolso_pasarela` si y solo si está `REEMBOLSADO`; `ck_reembolso_intentado`: un `REEMBOLSADO` o `FALLIDO` tiene `intentos > 0` y `ultimo_intento_en`. `monto ≤ pago.monto` lo garantiza `PagoServiceImpl` (no cabe en un `CHECK` entre tablas) | Solo un reembolso confirmado por la pasarela tiene su id, y todo resultado viene de un intento registrado | HU-16 |
| `liquidacion_detalle`: `uq_liquidacion_detalle_reserva` (reserva_id) y `uq_liquidacion_detalle_pago` (pago_id); `ck_liquidacion_detalle_monto` (`monto > 0`) | Una reserva (y su pago) se liquida una sola vez, cualquiera que sea el estado de la liquidación anterior | HU-19 |
| `liquidacion`: `ck_liquidacion_estado`; `ck_liquidacion_montos` (`monto_bruto > 0`, `comision = round(monto_bruto * 0.10, 2)` —`round` de PostgreSQL redondea la mitad hacia arriba en positivos, igual que `HALF_UP`— y `monto_neto = monto_bruto - comision`); `ck_liquidacion_pagada` (PAGADA si y solo si tiene número de operación, fecha, admin, banco y cuenta de destino); `ck_liquidacion_numero_operacion` (`^[A-Za-z0-9-]{4,30}$`); `ck_liquidacion_fechas` (`pagada_en >= generada_en`). Índices `ix_liquidacion_estado` (estado, generada_en, id), `ix_liquidacion_arrendador` (arrendador_id, generada_en, id) e `ix_liquidacion_detalle_liquidacion` | Comisión del 10 % fija y montos coherentes también en la base (STRIDE T, RN-10); una liquidación pagada siempre dice con qué operación y a qué cuenta | HU-19 |
| `resena.reserva_id` único y calificación entre 1 y 5 (`CHECK`) | Una reseña por reserva | HU-24 |
| `reclamo.reserva_id` único | Un reclamo por reserva | HU-26 |
| `usuario.correo` único | No hay dos cuentas con el mismo correo | HU-01 |
| `perfil_arrendador.hash_documento` único (HMAC-SHA256 del número, que va cifrado) | No hay dos arrendadores con el mismo RUC o DNI | HU-02 |
| `documento_identidad` (arrendador_id, tipo) único | Como mucho un documento vigente de cada tipo por arrendador | HU-05 |
| `foto_espacio.orden` entre 1 y 5 (`CHECK`) y (espacio_id, orden) único, diferido para renumerar en una transacción | Como mucho 5 fotos por espacio, sin dos en el mismo lugar | HU-07 |
| `espacio.zona` FK a `zona.codigo`; `CHECK` de tipo, estado, capacidad y precio_hora | Un espacio solo está en una zona del catálogo, con valores válidos | HU-07 |
| `horario_semanal`: `CHECK` de dia_semana (1–7) y de horas (`hora_inicio >= 0`, `hora_fin <= 24`, `hora_inicio < hora_fin`); (espacio_id, dia_semana, hora_inicio) único. La superposición entre franjas la impide el servicio con el espacio bloqueado | Bloques de 1 hora en punto, sin franjas repetidas | HU-08 |
| `bloqueo_fecha` (espacio_id, fecha) único | Una fecha no se bloquea dos veces | HU-08 |
| `revision_identidad`: `CHECK` de decision (APROBADA, RECHAZADA) y de motivo (RECHAZADA con motivo no en blanco, APROBADA sin motivo) | Toda decisión de identidad queda registrada y un rechazo siempre dice por qué | HU-06 |
| `suspension_cuenta`: `CHECK` de accion (SUSPENSION, REACTIVACION) y de motivo (SUSPENSION con motivo no en blanco, REACTIVACION sin motivo) | Toda suspensión o reactivación queda registrada y una suspensión siempre dice por qué | HU-09 |
| `token.hash_token` único (SHA-256 hex del token) | Un enlace de un solo uso no se confunde con otro; el token en claro no se guarda | HU-04 |
| `usuario`: `ck_usuario_correo_pendiente` (nulo, o normalizado y distinto de `correo`) y `ck_usuario_foto` (ruta y tipo los dos nulos, o ruta `perfiles/<UUID>` con tipo JPG o PNG); `token`: `ck_token_tipo` con CAMBIO_CORREO (`V6_1`). La unicidad del correo nuevo la sigue dando `uq_usuario_correo` al confirmarlo | El correo de la cuenta solo cambia al confirmar el enlace; la foto se guarda con su tipo real (RNF-SEG-08) | HU-20 |
| `mfa`: `ck_mfa_activado` (activo con `activado_en`, pendiente sin él) y `ck_mfa_ultimo_paso` (nulo o ≥ 0); `codigo_respaldo`: (usuario_id, hash_codigo) único (`uq_codigo_respaldo_usuario_hash`) y `ck_codigo_respaldo_hash` (64 hex); `desafio_mfa`: `hash_token` único, `CHECK` de formato, de paso, de `expira_en > creado_en` y de `intentos_fallidos` entre 0 y 5. Índice `ix_desafio_mfa_usuario` | Un MFA activo dice desde cuándo; un código de respaldo no se repite en la cuenta; un desafío tiene vida corta y como mucho 5 fallos (STRIDE S, D) | HU-17 |
| Cuenta `ELIMINADA` (sin migración): la fila de `usuario` se conserva con `correo` = `eliminada-<id>@espacioya.invalid` (minúsculas, pasa `uq_usuario_correo` y `ck_usuario_correo_normalizado`), `hash_contrasena` = bcrypt de un secreto descartado, `nombre`, `telefono`, `correo_pendiente`, `foto_ruta` y `foto_tipo_mime` nulos; `perfil_arrendador` se conserva con `numero_documento`, `razon_social` y `direccion_fiscal` = "Cuenta eliminada" (cifrados como siempre), `hash_documento` = `eliminada-<id>` (no es hex de 64: no choca con una huella real) y `motivo_rechazo` nulo. Se **borran** `documento_identidad` (y sus archivos), `cuenta_bancaria`, `mfa`, `codigo_respaldo` y `desafio_mfa`; los tokens pendientes se anulan y las sesiones se revocan. `reserva`, `reserva_bloque`, `pago`, `reembolso`, `checkout_pago`, `liquidacion`, `liquidacion_detalle` y `auditoria` no cambian | Supresión de datos personales con los registros contables conservados (RF-A-24, RNF-CUM-04); el correo y el documento quedan libres para un registro nuevo | HU-22 |
| Llaves foráneas en todas las relaciones | No hay reservas, pagos ni reseñas huérfanas | Todas |

## Diagrama entidad-relación (núcleo)

```mermaid
erDiagram
  USUARIO ||--o| PERFIL_ARRENDADOR : "es (si es arrendador)"
  PERFIL_ARRENDADOR ||--o{ DOCUMENTO_IDENTIDAD : sube
  PERFIL_ARRENDADOR ||--o{ REVISION_IDENTIDAD : "recibe"
  USUARIO ||--o{ REVISION_IDENTIDAD : "decide (admin)"
  USUARIO ||--o{ SUSPENSION_CUENTA : "recibe"
  USUARIO ||--o{ SUSPENSION_CUENTA : "suspende o reactiva (admin)"
  PERFIL_ARRENDADOR ||--o{ ESPACIO : publica
  ZONA ||--o{ ESPACIO : ubica
  PERFIL_ARRENDADOR ||--o{ CUENTA_BANCARIA : registra
  PERFIL_ARRENDADOR ||--o{ LIQUIDACION : recibe
  ESPACIO ||--o{ FOTO_ESPACIO : tiene
  ESPACIO ||--o{ HORARIO_SEMANAL : define
  ESPACIO ||--o{ BLOQUEO_FECHA : bloquea
  ESPACIO }o--o{ CARACTERISTICA : tiene
  USUARIO ||--o{ RESERVA : hace
  ESPACIO ||--o{ RESERVA : recibe
  RESERVA ||--|{ RESERVA_BLOQUE : retiene
  RESERVA ||--o{ PAGO : "se paga con"
  RESERVA ||--o| CHECKOUT_PAGO : "abre"
  PAGO ||--o{ REEMBOLSO : genera
  LIQUIDACION ||--|{ LIQUIDACION_DETALLE : incluye
  LIQUIDACION_DETALLE }o--|| RESERVA : liquida
  LIQUIDACION_DETALLE |o--|| PAGO : "con su pago PAGADO"
  RESERVA ||--o| RESENA : "puede tener"
  RESERVA ||--o| RECLAMO : "puede tener"
  RECLAMO ||--o{ MENSAJE_RECLAMO : contiene
  USUARIO ||--o{ AUDITORIA : genera
  USUARIO ||--o| MFA : "configura"
  MFA ||--o{ CODIGO_RESPALDO : "tiene (5)"
  USUARIO ||--o{ DESAFIO_MFA : "recibe al iniciar sesión"
```

## Evolución entre releases

Las tablas de Release 2 (`mfa`, `cuenta_bancaria`, `liquidacion`, `resena`,
`reclamo`, `reporte`, `caracteristica`) se agregan con migraciones nuevas, sin
editar las de Release 1.
