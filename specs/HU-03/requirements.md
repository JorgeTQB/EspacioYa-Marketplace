# HU-03 — Requirements: Inicio y cierre de sesión

> Fuente: `docs/historias/HU-03.md` (CA1–CA4, STRIDE D/I/S/R), `docs/requerimientos.md`
> (RF-A-07…10, RNF-SEG-01, 02, 12, 13, 17, 18, RN-01, §8 "Gestión de sesión y tokens"),
> `docs/architecture.md` (puerto `comun.ValidadorSesion`, estados de la cuenta),
> `specs/HU-01/` y `specs/HU-02/` (componentes reutilizados y aviso de identidad pendiente
> para HU-03) y `progress/explore_HU-03.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la
> presentación está en `design.md` § 12 Pantallas. Las decisiones técnicas del
> spec_author están en `design.md` § 1.

## Decisiones por defecto que el dueño debe confirmar al aprobar

> ⚠ Las fuentes no fijan estos puntos. El spec aplica el valor por defecto indicado; si
> **Jorge Quispe** no lo acepta, cambian los requirements citados antes de pasar a
> `in_progress`. Detalle y alternativas en `design.md` § 1 y en `progress/HU-03.md`.
>
> | # | Pregunta | Por defecto en este spec | Afecta |
> |---|----------|--------------------------|--------|
> | P1 | ¿Las cuentas `ADMIN` inician sesión con HU-03? | **No.** Reciben el mismo 401 genérico. El acceso admin (con restricción de IP, RNF-SEG-04) lo habilita HU-06. | R13 |
> | P2 | ¿Cómo viaja la sesión? (`docs/frontend.md` lo deja a HU-03) | **Token opaco** aleatorio en `Authorization: Bearer`, guardado por la SPA en `sessionStorage`; en la BD solo su hash. | R1, R3, R22, R28, R36 |
> | P3 | CA3 "captcha o bloqueo temporal": ¿cuál y con qué clave? | **Bloqueo temporal** (sin captcha) **por correo normalizado**, ventana deslizante de 15 min; un login exitoso reinicia el contador. | R16–R21 |
> | P4 | ¿Textos de los mensajes? | Los de "Definiciones" (abajo). | R9, R26–R28 |
> | P5 | ¿Cuándo se muestra el aviso de identidad tras iniciar sesión? | Solo a `ARRENDADOR` con `estado_identidad = NO_INICIADA` (el único estado alcanzable hoy; HU-05 amplía). | R4, R5, R23 |
> | P6 | ¿Inactividad deslizante o expiración absoluta? | **Deslizante:** cada solicitud autenticada mueve `expira_en` a ahora + 30 min; sin tope absoluto. | R2, R24, R27 |
> | P7 | ¿Varias sesiones a la vez por usuario? | **Sí.** Cerrar sesión revoca solo la actual (HU-04 cerrará todas). | R29 |

## Definiciones

- **Correo normalizado:** el de `specs/HU-01/requirements.md` (sin espacios a los
  extremos y en minúsculas, `Usuario.normalizarCorreo`).
- **Cuenta habilitada para iniciar sesión:** usuario con rol `USUARIO` o `ARRENDADOR`
  (⚠ P1), `correo_verificado = true` y `estado = ACTIVA` (RN-01).
- **Solicitud de inicio de sesión válida en forma:** `POST /api/sesiones` con `correo`
  no vacío de hasta 254 caracteres y `contrasena` no vacía de hasta 72 caracteres.
- **Mensaje de credenciales inválidas:** `"Correo o contraseña incorrectos."` (⚠ P4).
- **Mensaje de sesión inválida:** `"Tu sesión no es válida o expiró. Inicia sesión de
  nuevo."` (⚠ P4).
- **Mensaje de demasiados intentos:** `"Hiciste demasiados intentos. Espera un momento e
  inténtalo de nuevo."` (`ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS` de HU-01).
- **Aviso de identidad:** `"Para publicar tus espacios, primero debes verificar tu
  identidad."` (el mismo de HU-02).
- **Token de sesión:** valor aleatorio que la API entrega al iniciar sesión y que el
  cliente envía en el encabezado `Authorization: Bearer <token>` (⚠ P2).
- **Sesión vigente:** fila de `sesion` con `revocada = false` y `expira_en` posterior a
  la fecha y hora actuales del reloj del sistema.
- **Periodo de inactividad:** 30 minutos.
- **Fallo de inicio de sesión:** solicitud de inicio de sesión válida en forma que el
  sistema responde con 401 según R9–R13.
- **Endpoint protegido:** todo endpoint que no está en la lista `permitAll` de
  `ConfiguracionSeguridad`; en esta historia, `GET /api/sesiones/actual` y
  `DELETE /api/sesiones/actual`.

## Inicio de sesión exitoso (CA1, RF-A-07)

## R1
CUANDO un cliente envía a `POST /api/sesiones` el correo (en cualquier combinación de
mayúsculas y espacios a los extremos) y la contraseña correctos de una cuenta habilitada
para iniciar sesión cuyo correo normalizado no está bloqueado (R16), el sistema DEBE
responder `201 Created` con un cuerpo que contenga solo `token`, `rol` y `aviso`.

## R2
CUANDO el sistema responde según R1, el sistema DEBE guardar una sesión con el id del
usuario, `creada_en` igual a la fecha y hora del reloj del sistema, `expira_en` igual a
`creada_en` más el periodo de inactividad y `revocada = false`.

## R3
El sistema DEBE guardar en `sesion.hash_token` el SHA-256 en hexadecimal (64 caracteres)
del token de sesión, y NO DEBE guardar el token en claro en ninguna columna.

## R4
CUANDO el sistema responde según R1 a una cuenta `ARRENDADOR` cuyo perfil tiene
`estado_identidad = NO_INICIADA`, el sistema DEBE devolver en `aviso` el aviso de
identidad (⚠ P5).

## R5
CUANDO el sistema responde según R1 a una cuenta `USUARIO` o a una cuenta `ARRENDADOR`
cuyo perfil tiene `estado_identidad` distinto de `NO_INICIADA`, el sistema DEBE devolver
`aviso` nulo.

## R6
El sistema DEBE generar cada token de sesión con 32 bytes de un generador aleatorio
criptográfico, codificados en base64url sin relleno (43 caracteres), de modo que dos
inicios de sesión de la misma cuenta reciban tokens distintos.

## R7
El sistema NO DEBE incluir el id del usuario, el correo, el hash de la contraseña ni el
hash del token en la respuesta de `POST /api/sesiones`.

## R8
CUANDO el sistema responde según R1, el sistema DEBE devolver en `rol` el rol guardado de
la cuenta aunque el cuerpo de la solicitud incluya un campo `rol` con otro valor.

## Credenciales incorrectas y mensaje genérico (CA2, RF-A-08, STRIDE I, RNF-SEG-13)

## R9
SI la contraseña enviada no coincide con el hash de la cuenta del correo normalizado
ENTONCES el sistema DEBE responder 401 con el mensaje de credenciales inválidas, sin
crear sesión.

## R10
SI el correo normalizado no pertenece a ninguna cuenta ENTONCES el sistema DEBE responder
con el mismo código y el mismo cuerpo que R9, sin crear sesión.

## R11
SI la cuenta tiene `correo_verificado = false` ENTONCES el sistema DEBE responder con el
mismo código y el mismo cuerpo que R9 aunque la contraseña sea correcta, sin crear sesión.

## R12
SI la cuenta tiene `estado` distinto de `ACTIVA` ENTONCES el sistema DEBE responder con el
mismo código y el mismo cuerpo que R9 aunque la contraseña sea correcta, sin crear sesión.

## R13
SI la cuenta tiene rol `ADMIN` ENTONCES el sistema DEBE responder con el mismo código y
el mismo cuerpo que R9 aunque la contraseña sea correcta, sin crear sesión (⚠ P1).

## R14
CUANDO el correo normalizado no pertenece a ninguna cuenta, el sistema DEBE ejecutar una
comparación bcrypt de la contraseña enviada contra un hash señuelo, para que el tiempo de
respuesta no revele si el correo existe.

## R15
SI la solicitud a `POST /api/sesiones` no es válida en forma ENTONCES el sistema DEBE
responder 400 con un error asociado al campo `correo` o `contrasena`, sin crear sesión ni
contar un fallo de inicio de sesión.

## Bloqueo temporal ante fuerza bruta (CA3, RF-A-09, STRIDE D, RNF-SEG-17)

## R16
SI el correo normalizado acumula 5 fallos de inicio de sesión en los últimos 15 minutos
ENTONCES el sistema DEBE responder 429 con el mensaje de demasiados intentos a toda
solicitud de inicio de sesión válida en forma con ese correo, sin comparar la contraseña
ni crear sesión, aunque la contraseña sea correcta (⚠ P3).

## R17
El sistema DEBE contar un fallo de inicio de sesión para el correo normalizado enviado
cada vez que responde 401 según R9–R13, exista o no una cuenta con ese correo.

## R18
El sistema NO DEBE contar como fallo de inicio de sesión una solicitud respondida con 400
(R15) ni con 429 (R16).

## R19
CUANDO han pasado 15 minutos desde un fallo de inicio de sesión, el sistema DEBE dejar de
contarlo para R16, de modo que la siguiente solicitud con ese correo se procese con
normalidad si quedan menos de 5 fallos en los últimos 15 minutos.

## R20
CUANDO el sistema responde según R1, el sistema DEBE reiniciar a cero los fallos de
inicio de sesión contados para ese correo normalizado.

## R21
MIENTRAS un correo normalizado está bloqueado según R16, el sistema DEBE procesar con
normalidad las solicitudes de inicio de sesión con otro correo, aunque lleguen desde la
misma IP.

## Uso de la sesión, cierre e inactividad (CA4, RF-A-10, STRIDE S, RNF-SEG-01, 02)

## R22
CUANDO un cliente envía una solicitud a un endpoint protegido con
`Authorization: Bearer <token>` de una sesión vigente, el sistema DEBE procesarla con el
usuario y el rol de esa sesión, sin aceptar un id de usuario ni un rol enviados por el
cliente.

## R23
CUANDO un cliente envía `GET /api/sesiones/actual` con una sesión vigente, el sistema DEBE
responder 200 con un cuerpo que contenga solo `rol` y `aviso`, con `aviso` calculado como
en R4 y R5.

## R24
CUANDO el sistema procesa una solicitud a un endpoint protegido con una sesión vigente,
el sistema DEBE fijar `expira_en` de esa sesión en la fecha y hora del reloj del sistema
más el periodo de inactividad (⚠ P6).

## R25
CUANDO un cliente envía `DELETE /api/sesiones/actual` con una sesión vigente, el sistema
DEBE marcar esa sesión con `revocada = true` y responder `204 No Content`.

## R26
SI un cliente envía una solicitud a un endpoint protegido con el token de una sesión
revocada ENTONCES el sistema DEBE responder 401 con el mensaje de sesión inválida.

## R27
SI un cliente envía una solicitud a un endpoint protegido con el token de una sesión cuyo
`expira_en` es igual o anterior a la fecha y hora del reloj del sistema ENTONCES el
sistema DEBE responder 401 con el mensaje de sesión inválida, sin modificar `expira_en`.

## R28
SI un cliente envía una solicitud a un endpoint protegido sin encabezado `Authorization`,
con un esquema distinto de `Bearer` o con un token que no corresponde a ninguna sesión
ENTONCES el sistema DEBE responder 401 con el mensaje de sesión inválida.

## R29
CUANDO el sistema revoca una sesión según R25, el sistema NO DEBE revocar las demás
sesiones vigentes del mismo usuario (⚠ P7).

## Auditoría de accesos (STRIDE R, RNF-SEG-12)

## R30
CUANDO el sistema responde según R1, el sistema DEBE registrar en la auditoría la acción
`LOGIN_EXITOSO` con el id del usuario, la entidad `sesion` con el id de la sesión creada,
la IP de la solicitud y la fecha y hora.

## R31
CUANDO el sistema responde 401 según R9–R13, el sistema DEBE registrar en la auditoría la
acción `LOGIN_FALLIDO` con el id de la cuenta del correo (nulo si no existe), la IP, la
fecha y hora y en el detalle `motivo=` seguido de `CONTRASENA_INCORRECTA`,
`CORREO_DESCONOCIDO`, `CORREO_NO_VERIFICADO`, `CUENTA_NO_ACTIVA` o `ROL_NO_PERMITIDO`.

## R32
CUANDO el sistema responde 429 según R16, el sistema DEBE registrar en la auditoría la
acción `LOGIN_BLOQUEADO` con el id de la cuenta del correo (nulo si no existe), la IP y la
fecha y hora.

## R33
CUANDO el sistema revoca una sesión según R25, el sistema DEBE registrar en la auditoría
la acción `CIERRE_SESION` con el id del usuario, la entidad `sesion` con el id de la
sesión, la IP y la fecha y hora.

## R34
El sistema NO DEBE escribir el correo, la contraseña ni el token de sesión en el detalle
de las auditorías de inicio y cierre de sesión.

## Protección en tránsito y minimización (STRIDE I)

## R35
El sistema NO DEBE incluir la contraseña ni el correo en la representación textual
(`toString`) del DTO de inicio de sesión.

## R36
CUANDO el navegador envía la solicitud previa CORS (`OPTIONS`) desde el origen
configurado del frontend pidiendo el encabezado `Authorization`, el sistema DEBE
permitirlo en `Access-Control-Allow-Headers`.

## Trazabilidad con la historia

| Origen (`docs/historias/HU-03.md` y fuentes)                                    | Cubierto por |
|---------------------------------------------------------------------------------|--------------|
| CA1: credenciales correctas + correo verificado → accedo al panel de mi rol     | R1, R2, R3, R6, R8, R11, R22, R23 (panel en design § 12) |
| CA1 / HU-02 CA3: aviso "verificar identidad" tras iniciar sesión (⚠ P5)         | R4, R5, R23 |
| CA2: credenciales incorrectas → mensaje genérico                                | R9, R10, R11, R12, R13, R14 |
| CA3: 5 fallos en 15 min → bloqueo temporal (⚠ P3)                               | R16, R17, R18, R19, R20, R21 |
| CA4: cierro sesión → la sesión ya no es válida                                  | R25, R26, R29 |
| CA4: 30 min inactivo → la sesión ya no es válida (⚠ P6)                         | R2, R24, R27 |
| (D) Rate limiting y bloqueo temporal ante fuerza bruta                          | R16, R17, R18, R19, R21 |
| (I) Mensaje de error genérico                                                   | R9–R14, R26, R27, R28 |
| (I) TLS en toda la comunicación (despliegue en UAT, design § 1 DT-11)            | R3, R7, R35, R36 |
| (S) Token invalidado en backend al cerrar sesión o expirar                      | R3, R25, R26, R27, R28 |
| (R) Registro de intentos de acceso (fecha, IP, resultado)                       | R30, R31, R32, R33, R34 |
| MFA fuera de la historia (HU-17)                                                | — (no se implementa) |
| RF-A-07 / 08 / 09 / 10                                                          | R1–R8 / R9–R14 / R16–R21 / R24–R29 |
| RNF-SEG-01 (identidad de la sesión) · 02 (invalidación en backend)              | R22 · R25–R28 |
| RNF-SEG-12 · 13 · 17 · 18                                                       | R30–R34 · R9–R14, R26–R28 · R16–R21 · R8, R13, R22 |
| RN-01 (correo verificado inicia sesión; suspendida no)                          | R1, R11, R12 |
