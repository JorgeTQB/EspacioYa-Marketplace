# HU-06 — Requirements: Revisión de identidad de arrendadores

> Fuente: `docs/historias/HU-06.md` (CA1–CA3, STRIDE E/S/I/R), `docs/requerimientos.md`
> (RF-F-01…03, RNF-SEG-04, 12, 15, 18, relacionados RNF-SEG-13, 14, 16, RNF-USA-04, RN-01, §2
> "Administrador se crea solo por script interno", §8 auditoría, correo y almacenamiento con URL
> firmada, §9 pendiente 4), `docs/architecture.md` (módulo `administracion`, dirección
> `administracion → (todos)`, `CuentaService`), `docs/modelo-datos.md` (`revision_identidad`),
> `specs/HU-03/` (inicio de sesión, R13), `specs/HU-04/` (S2), `specs/HU-05/` (documentos, URL
> firmada, P10/DT-16) y `progress/explore_HU-06.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la
> presentación está en `design.md` § 11 Pantallas. Las decisiones técnicas están en
> `design.md` § 1.

## Decisiones del dueño (Ariel Enriquez, 2026-10-28)

Tomadas sobre los puntos 1–4 del informe del explorador (`progress/HU-06.md`). No llevan ⚠.

| # | Decisión | Afecta |
|---|----------|--------|
| D1 | El admin inicia sesión por el mismo `POST /api/sesiones`, **solo desde una IP permitida**; desde otra IP recibe el mismo 401 genérico y se audita con un motivo propio. El admin **sigue sin recuperar contraseña por correo** hasta HU-17: la regla de recuperación se separa de la de inicio de sesión para no cambiar la conducta de HU-04. **Reemplaza HU-03 R13** (y amplía la definición de "cuenta habilitada para iniciar sesión" y la lista de motivos de HU-03 R31). | R1–R6 |
| D2 | Control compensatorio hasta HU-17: **filtro del backend en `comun/seguridad` sobre `/api/admin/**`**, lista de IPs (CIDR admitido) por variable de entorno `ADMIN_IPS_PERMITIDAS`; valor local `127.0.0.1,::1`; **lista vacía = nadie entra**; respuesta genérica. Reutilizable por HU-09 y HU-27. Resuelve el pendiente §9-4 de `docs/requerimientos.md` como "se mantiene hasta HU-17; HU-17 decide si se retira". | R5, R7–R12 |
| D3 | IP real detrás de CloudFront/ALB **por configuración de Tomcat** (`server.forward-headers-strategy=native` + proxies internos de confianza por variable de entorno). `IpCliente` no cambia; nunca se lee `X-Forwarded-For` a mano. | R13, R14 |
| D4 | Las cuentas admin se crean con un **`ApplicationRunner` de `cuentas` activado solo por propiedad**, con correo y contraseña de variables de entorno, política de contraseña de HU-01, bcrypt, `correo_verificado = true`, versión de privacidad vigente y auditoría. Sin endpoint ni secretos en el repo; el comando se documenta en el README. | R15–R19 |

## Supuestos a confirmar por el dueño al aprobar

> ⚠ Las fuentes no fijan estos puntos. El spec adopta la recomendación del explorador (puntos
> 5–15) o el valor indicado; si **Ariel Enriquez** no lo acepta, cambian los requirements
> citados antes de pasar a `in_progress`.

| # | Supuesto | Afecta |
|---|----------|--------|
| S1 | (punto 5) Se crea la tabla **`revision_identidad`** como entidad de `administracion` (historial de decisiones: admin, arrendador, decisión, motivo, fecha), con `CHECK` "motivo obligatorio solo si `RECHAZADA`" y motivo de hasta 500 caracteres (como `perfil_arrendador.motivo_rechazo`). El estado sigue en `perfil_arrendador` y lo cambia `cuentas`. Decisión, revisión y auditoría van en **una sola transacción**. | R29, R33, R36, R42 |
| S2 | (punto 6) `CuentaService` gana cuatro métodos (cola, expediente, aprobar, rechazar). El rol `ADMIN` se valida **en `administracion` y otra vez en `CuentaService`**. Arrendador inexistente, no `ARRENDADOR` o con cuenta no `ACTIVA` → 404; ya decidido (estado ≠ `EN_REVISION`) → 400 *ya revisada*. | R9, R25, R37, R38, R41 |
| S3 | (punto 7) Correos de aprobación y rechazo como **dos métodos nuevos de `NotificadorCorreo`** de `cuentas` (HU-25 los moverá a `comun/correo`), enviados **después del commit**; el adaptador simulado no escribe el correo ni el motivo en el log. | R30, R34, R40, R45 |
| S4 | (punto 8) La cola se ordena por la **fecha de envío** = `documento_identidad.subido_en` (igual en los dos documentos y renovada en cada reenvío), desempate por id del arrendador. Sin columna nueva. | R20, R21 |
| S5 | (punto 9) El admin ve los **datos completos** del arrendador (número de documento descifrado incluido) para cotejarlos con el documento; las respuestas llevan `Cache-Control: no-store` y nada de esto va a la auditoría. | R23, R26, R43 |
| S6 | (punto 10) **Cola resumida + detalle** por arrendador que firma las URL al abrirlo; **sin paginación**. | R20, R23, R24 |
| S7 | (punto 11) Rutas bajo el prefijo único **`/api/admin/`**, con **dos endpoints de decisión** (aprobación y rechazo); cada decisión responde `201` con la decisión, el estado resultante y la fecha. | R20–R38 |
| S8 | (punto 12) `GET /api/archivos` **no cambia** (la firma es la autorización y queda fuera del filtro de IPs), salvo que un error de lectura del almacenamiento pasa a ser el 404 genérico (observación del review de HU-05). No se audita la consulta de documentos. | R12, R44 |
| S9 | (punto 13) Frontend: **`Modal` nuevo en el kit** (primera historia que lo necesita) y páginas en `pages/administracion/`. | — (Pantallas) |
| S10 | (punto 14) La cola y las decisiones solo consideran arrendadores con cuenta **`ACTIVA`**; la suspensión es de HU-09 (Jorge Quispe). | R22, R25, R37 |
| S11 | (punto 15) Migración **`V2_3__revision_identidad.sql`** con la regla de `docs/conventions.md` (si `develop` ya tiene `V2_3` al unir, se renombra a la siguiente libre). | R36 |
| S12 | El filtro de IPs responde **403 con el mensaje *acceso denegado***, el mismo cuerpo que la falta de rol: no revela qué control falló. | R7 |
| S13 | El filtro de IPs **no audita** las solicitudes que rechaza (no hay usuario resuelto y un barrido llenaría la tabla); el inicio de sesión admin desde una IP no permitida sí se audita (R3). | R3, R7 |
| S14 | Los proxies de confianza (`PROXIES_CONFIANZA`, expresión regular de Tomcat) están **vacíos por defecto**: sin configurarlos, la IP es la de la conexión, como hoy. | R13, R14 |
| S15 | El comando de creación de admin **no modifica** una cuenta existente con ese correo (falla), y al terminar **cierra la aplicación**. | R18 |
| S16 | El detalle de la auditoría de una decisión es `decision=<DECISION>;revision=<id>`: el motivo vive solo en `revision_identidad` y en el perfil, no se duplica en la auditoría. | R42, R43 |

## Definiciones

- **Admin:** cuenta con `usuario.rol = ADMIN`.
- **Lista de IPs permitidas:** valor de `espacioya.admin.ips-permitidas` (variable
  `ADMIN_IPS_PERMITIDAS`; por defecto `127.0.0.1,::1`), entradas separadas por comas, cada una
  una dirección IPv4 o IPv6 o un rango CIDR, sin espacios a los extremos (D2).
- **IP del cliente:** la que devuelve `IpCliente.de(solicitud)` tras la configuración de R13/R14.
- **IP permitida:** IP del cliente que coincide con alguna entrada de la lista de IPs permitidas.
- **Ruta admin:** toda ruta de la API que empieza con `/api/admin/`.
- **Admin con acceso:** admin con sesión vigente (HU-03) cuya solicitud llega desde una IP
  permitida.
- **Arrendador en la cola:** cuenta con rol `ARRENDADOR` y `estado = ACTIVA` cuyo perfil tiene
  `estado_identidad = EN_REVISION` (⚠ S10).
- **Fecha de envío:** el menor `subido_en` de los documentos vigentes del arrendador (⚠ S4).
- **Motivo válido:** texto que, tras recortar espacios al inicio y al final, mide de 1 a 500
  caracteres.
- **Decisión:** `POST /api/admin/arrendadores/{id}/identidad/aprobacion` o
  `POST /api/admin/arrendadores/{id}/identidad/rechazo`.
- **Proxies de confianza:** expresión regular de `server.tomcat.remoteip.internal-proxies`
  (variable `PROXIES_CONFIANZA`; vacía por defecto) (⚠ S14).
- **Mensajes** (texto exacto en `design.md` § 4): *credenciales inválidas* y *sesión inválida*
  (HU-03), *acceso denegado* y *recurso no encontrado* (HU-05), *ya revisada*, *motivo*.

## Acceso del administrador (D1, STRIDE S/E, RNF-SEG-04)

## R1
CUANDO un admin con `correo_verificado = true` y `estado = ACTIVA` envía a `POST /api/sesiones`
su correo y contraseña correctos desde una IP permitida, el sistema DEBE responder según HU-03
R1 con `rol = "ADMIN"` y `aviso` nulo. (Reemplaza HU-03 R13.)

## R2
SI un admin envía a `POST /api/sesiones` su correo y contraseña correctos desde una IP no
permitida ENTONCES el sistema DEBE responder con el mismo código y el mismo cuerpo que HU-03 R9,
sin crear sesión.

## R3
CUANDO el sistema responde según R2, el sistema DEBE registrar en la auditoría la acción
`LOGIN_FALLIDO` con el id del admin, la IP del cliente, la fecha y hora y el detalle
`motivo=IP_NO_PERMITIDA`.

## R4
CUANDO el sistema responde según R2, el sistema DEBE contar un fallo de inicio de sesión para el
correo normalizado, igual que HU-03 R17.

## R5
MIENTRAS la lista de IPs permitidas está vacía, el sistema DEBE tratar toda IP del cliente como
no permitida en R1, R2 y R7.

## R6
SI un admin solicita `POST /api/recuperaciones` desde cualquier IP, incluida una permitida,
ENTONCES el sistema DEBE responder según HU-04 R1 sin guardar un token ni invocar el notificador
(se mantiene HU-04 S2 hasta HU-17).

## Control compensatorio de IPs (D2, STRIDE S, RNF-SEG-04)

## R7
SI una solicitud a una ruta admin llega desde una IP no permitida, con o sin sesión vigente,
ENTONCES el sistema DEBE responder 403 con el mensaje *acceso denegado* sin ejecutar el
controlador (⚠ S12, S13).

## R8
SI una solicitud a una ruta admin llega desde una IP permitida sin sesión vigente ENTONCES el
sistema DEBE responder 401 con el mensaje *sesión inválida*.

## R9
SI una cuenta con rol distinto de `ADMIN`, con sesión vigente, envía desde una IP permitida una
solicitud a una ruta admin con un cuerpo válido ENTONCES el sistema DEBE responder 403 con el
mensaje *acceso denegado* (rol releído del backend en cada solicitud, RNF-SEG-18).

## R10
El sistema DEBE considerar permitida una IP del cliente que pertenece a un rango CIDR IPv4 o IPv6
de la lista de IPs permitidas.

## R11
SI la lista de IPs permitidas contiene una entrada que no es una dirección IP ni un rango CIDR
válido ENTONCES el sistema DEBE impedir el arranque con un error que nombre la variable
`ADMIN_IPS_PERMITIDAS`.

## R12
El sistema NO DEBE rechazar por IP las solicitudes a rutas que no son rutas admin, incluida
`GET /api/archivos` (⚠ S8).

## IP real detrás del proxy (D3)

## R13
CUANDO una solicitud llega desde una dirección que coincide con los proxies de confianza y trae
el encabezado `X-Forwarded-For`, el sistema DEBE usar como IP del cliente la dirección más a la
derecha de ese encabezado que no coincide con los proxies de confianza.

## R14
SI una solicitud llega desde una dirección que no coincide con los proxies de confianza
ENTONCES el sistema DEBE usar como IP del cliente la dirección de la conexión, ignorando
`X-Forwarded-For` (⚠ S14).

## Creación de cuentas admin por script interno (D4, STRIDE E, RNF-SEG-18)

## R15
DONDE la propiedad `espacioya.admin.crear` vale `true`, el sistema DEBE crear al arrancar una
cuenta con rol `ADMIN`, el correo normalizado de `ADMIN_CORREO`, el hash bcrypt de
`ADMIN_CONTRASENA`, `correo_verificado = true`, `estado = ACTIVA`, la versión de privacidad
vigente y `fecha_consentimiento` igual a la fecha y hora del reloj del sistema.

## R16
CUANDO el sistema crea una cuenta según R15, el sistema DEBE registrar en la auditoría la acción
`CREACION_ADMIN` con el id de la cuenta creada, la entidad `usuario` con ese id, IP nula y
detalle nulo.

## R17
SI `ADMIN_CONTRASENA` no cumple la política de contraseña de HU-01 o `ADMIN_CORREO` está vacío o
no tiene forma de correo ENTONCES el sistema DEBE detener el arranque sin crear la cuenta, con un
error que nombre la variable sin mostrar su valor.

## R18
SI ya existe una cuenta con el correo normalizado de `ADMIN_CORREO` ENTONCES el sistema DEBE
detener el arranque sin crear ni modificar ninguna cuenta (⚠ S15).

## R19
MIENTRAS la propiedad `espacioya.admin.crear` no vale `true`, el sistema NO DEBE registrar el
componente que crea cuentas admin.

## Cola de revisión (CA1, RF-F-01)

## R20
CUANDO un admin con acceso envía `GET /api/admin/arrendadores/en-revision`, el sistema DEBE
responder 200 con una lista que tenga un elemento por arrendador en la cola, cada uno con solo
`arrendadorId`, `nombre`, `razonSocial`, `tipoDocumento` y `enviadoEn` (fecha de envío)
(⚠ S6, S7).

## R21
El sistema DEBE ordenar la lista de R20 por `enviadoEn` ascendente y, a igual fecha, por
`arrendadorId` ascendente (⚠ S4).

## R22
El sistema NO DEBE incluir en la lista de R20 arrendadores con `estado_identidad` distinto de
`EN_REVISION` ni cuentas con `estado` distinto de `ACTIVA` (⚠ S10).

## Expediente del arrendador (CA1, STRIDE I, RNF-SEG-15)

## R23
CUANDO un admin con acceso envía `GET /api/admin/arrendadores/{id}/identidad` de un arrendador en
la cola, el sistema DEBE responder 200 con solo `arrendadorId`, `nombre`, `correo`, `telefono`,
`tipoDocumento`, `numeroDocumento` (descifrado), `razonSocial`, `direccionFiscal`,
`estadoIdentidad`, `enviadoEn` y `documentos` (⚠ S5).

## R24
El sistema DEBE devolver en `documentos` de R23 un elemento por documento vigente con los campos
de HU-05 R25, cuya `url` es una URL firmada con `expira` y `urlExpiraEn` iguales a la fecha y
hora del reloj del sistema más 5 minutos.

## R25
SI el `{id}` de `GET /api/admin/arrendadores/{id}/identidad` no corresponde a un arrendador en la
cola ENTONCES el sistema DEBE responder 404 con el mensaje *recurso no encontrado*, sin firmar
ninguna URL.

## R26
CUANDO el sistema responde 200 según R20 o R23, el sistema DEBE incluir un encabezado
`Cache-Control` que contenga `no-store`.

## Aprobación (CA2, RF-F-02, RN-01)

## R27
CUANDO un admin con acceso envía `POST /api/admin/arrendadores/{id}/identidad/aprobacion` de un
arrendador con cuenta `ACTIVA` y `estado_identidad = EN_REVISION`, el sistema DEBE responder
`201 Created` con solo `decision = "APROBADA"`, `estadoIdentidad = "VERIFICADA"` y `fecha`.

## R28
CUANDO el sistema responde según R27, el sistema DEBE guardar en el perfil del arrendador
`estado_identidad = VERIFICADA` y `motivo_rechazo` nulo.

## R29
CUANDO el sistema responde según R27, el sistema DEBE guardar una fila de `revision_identidad`
con el id del arrendador, `admin_id` igual al id del usuario de la sesión (aunque el cuerpo traiga
otro), `decision = APROBADA`, `motivo` nulo y `fecha` igual a la fecha y hora del reloj del
sistema (⚠ S1).

## R30
CUANDO el sistema responde según R27, el sistema DEBE invocar una sola vez, después de confirmar
la transacción, el aviso de identidad aprobada del notificador de correo con el correo del
arrendador (⚠ S3).

## Rechazo (CA3, RF-F-03, RN-01)

## R31
CUANDO un admin con acceso envía `POST /api/admin/arrendadores/{id}/identidad/rechazo` con un
`motivo` válido de un arrendador con cuenta `ACTIVA` y `estado_identidad = EN_REVISION`, el
sistema DEBE responder `201 Created` con solo `decision = "RECHAZADA"`,
`estadoIdentidad = "RECHAZADA"` y `fecha`.

## R32
CUANDO el sistema responde según R31, el sistema DEBE guardar en el perfil del arrendador
`estado_identidad = RECHAZADA` y `motivo_rechazo` igual al motivo recortado.

## R33
CUANDO el sistema responde según R31, el sistema DEBE guardar una fila de `revision_identidad`
con el id del arrendador, `admin_id` igual al id del usuario de la sesión, `decision = RECHAZADA`,
`motivo` igual al motivo recortado y `fecha` igual a la fecha y hora del reloj del sistema
(⚠ S1).

## R34
CUANDO el sistema responde según R31, el sistema DEBE invocar una sola vez, después de confirmar
la transacción, el aviso de identidad rechazada del notificador de correo con el correo del
arrendador y el motivo recortado (⚠ S3).

## R35
SI el cuerpo de `POST …/identidad/rechazo` no trae `motivo`, o el motivo no es un motivo válido,
ENTONCES el sistema DEBE responder 400 con un error en el campo `motivo` y el mensaje *motivo*,
sin cambiar el perfil ni guardar una revisión.

## R36
El sistema DEBE impedir en la base de datos una fila de `revision_identidad` con `decision`
distinta de `APROBADA` o `RECHAZADA`, una `RECHAZADA` sin motivo o con motivo en blanco, o una
`APROBADA` con motivo (⚠ S1, S11).

## Estado, concurrencia y fallos (RN-01, STRIDE E)

## R37
SI el `{id}` de una decisión no corresponde a una cuenta `ARRENDADOR` con perfil y
`estado = ACTIVA` ENTONCES el sistema DEBE responder 404 con el mensaje *recurso no encontrado*
sin cambiar ningún perfil ni guardar una revisión (⚠ S2, S10).

## R38
SI el arrendador de una decisión tiene `estado_identidad` distinto de `EN_REVISION` ENTONCES el
sistema DEBE responder 400 con el mensaje *ya revisada* sin cambiar el perfil ni guardar una
revisión (⚠ S2).

## R39
CUANDO llegan a la vez dos decisiones sobre el mismo arrendador en `EN_REVISION`, el sistema DEBE
aceptar solo una y responder a la otra según R38.

## R40
SI el sistema rechaza una decisión según R7–R9, R35, R37 o R38, o la transacción de la decisión
se revierte, ENTONCES el sistema NO DEBE invocar el notificador de correo (⚠ S3).

## R41
SI un método de `CuentaService` para el administrador se invoca con un usuario de sesión cuyo
rol no es `ADMIN` ENTONCES el sistema DEBE lanzar `AccesoDenegadoException` sin consultar los
repositorios (defensa en profundidad, ⚠ S2).

## Registro de cada decisión (STRIDE R, RNF-SEG-12)

## R42
CUANDO el sistema responde según R27 o R31, el sistema DEBE registrar en la auditoría la acción
`IDENTIDAD_APROBADA` o `IDENTIDAD_RECHAZADA` con el id del admin, la entidad `perfil_arrendador`
con el id del arrendador, la IP del cliente, la fecha y hora y el detalle
`decision=<DECISION>;revision=<id de la fila de R29 o R33>` (⚠ S16).

## R43
El sistema NO DEBE escribir en el detalle de las auditorías de R42 el motivo, el número de
documento, el correo ni las URL firmadas del arrendador (⚠ S5, S16).

## Documentos y correos (STRIDE I, RNF-SEG-15, 16)

## R44
SI al servir `GET /api/archivos` con una URL firmada válida la lectura del almacenamiento falla
con un error de entrada/salida ENTONCES el sistema DEBE responder 404 con el mensaje *recurso no
encontrado* (⚠ S8).

## R45
CUANDO el adaptador simulado de correo recibe un aviso de identidad aprobada o rechazada, el
sistema NO DEBE escribir en el log el correo del arrendador ni el motivo (Ley 29733, ⚠ S3).

## Reemplazos y compatibilidad

- **HU-03 R13** ("el ADMIN recibe el 401 genérico") queda reemplazado por R1–R4. La definición
  de HU-03 "cuenta habilitada para iniciar sesión" incluye ahora al admin desde una IP
  permitida, y la lista de motivos de HU-03 R31 suma `IP_NO_PERMITIDA`. El resto de HU-03 no
  cambia.
- **HU-04 S2 / R8, R16, R27, R29** no cambian: el admin sigue sin recuperar contraseña (R6). La
  regla de recuperación se separa de la de inicio de sesión (`design.md` DT-2).
- **HU-05 P10 / DT-16** se cumple: `cuentas` expone a `administracion` los documentos con URL
  firmada (R24).
- **`docs/requerimientos.md` §9-4** se resuelve: el control de IPs se mantiene hasta HU-17, que
  decide si se retira.

## Trazabilidad con la historia

| Origen (`docs/historias/HU-06.md` y fuentes)                                         | Cubierto por |
|--------------------------------------------------------------------------------------|--------------|
| CA1: arrendadores en revisión → veo sus datos y documentos                            | R20, R22, R23, R24, R25 (pantallas en design § 11) |
| CA1: … ordenados del más antiguo al más reciente                                      | R21 |
| CA2: apruebo y confirmo → "identidad verificada"                                      | R27, R28, R29 (HU-07 ya publica con `VERIFICADA`) |
| CA2: … y recibe un correo avisándole que ya puede publicar                            | R30, R40 |
| CA3: rechazo con motivo obligatorio → "identidad rechazada"                           | R31, R32, R33, R35, R36 |
| CA3: … y recibe un correo con el motivo                                               | R34, R40 |
| (E) Cuentas admin creadas solo por script interno                                     | R15, R16, R17, R18, R19 |
| (E) Rol validado en backend en cada solicitud                                         | R9, R37, R38, R39, R41 |
| (S) Hasta HU-17, panel admin accesible solo desde IPs permitidas                      | R1, R2, R3, R4, R5, R7, R8, R10, R11, R12, R13, R14 |
| (I) Documentos visibles solo mediante URL firmada de corta duración                   | R24, R25, R26, R44 |
| (R) Registro de cada decisión (admin, arrendador, decisión, motivo, fecha)            | R29, R33, R36, R42, R43 |
| RF-F-01 / 02 / 03                                                                     | R20–R26 / R27–R30 / R31–R36 |
| RNF-SEG-04 (MFA en HU-17; hasta entonces IPs permitidas)                              | R1–R14 |
| RNF-SEG-12 · RNF-SEG-15 · RNF-SEG-18                                                  | R3, R16, R42, R43 · R24, R44 · R9, R15–R19, R41 |
| RNF-SEG-13 (errores genéricos)                                                        | R2, R7, R25, R37 |
| RNF-SEG-14 · RNF-SEG-16                                                               | R26, R43 · R30, R34, R45 (el correo no lleva documentos: el puerto solo recibe correo y motivo) |
| RNF-USA-04 (avisos de identidad por correo)                                           | R30, R34 |
| RN-01 (en revisión → verificada o rechazada; rechazada vuelve a subir)                | R27, R31, R38 (el reenvío tras `RECHAZADA` ya es de HU-05) |
| HU-04 S2 (admin sin recuperación hasta HU-17)                                         | R6 |
