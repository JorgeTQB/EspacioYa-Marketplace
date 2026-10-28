# HU-04 — Requirements: Recuperación de contraseña

> Fuente: `docs/historias/HU-04.md` (CA1–CA3, STRIDE S/T, I, D, R), `docs/requerimientos.md`
> (RF-A-11…13, RNF-SEG-02, 06, 12, 13, 17; §8 "Gestión de sesión y tokens", "Rate limiting" y
> "Envío de correo"), `specs/HU-01/` y `specs/HU-03/` (componentes reutilizados),
> `progress/explore_HU-04.md` (riesgos R1–R14 del explorador) y las decisiones del dueño en
> `progress/HU-04.md` (2026-10-28).
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la
> presentación está en `design.md` § 9 Pantallas.

## Decisiones del dueño (Eduardo Saldaña, 2026-10-28)

| # | Decisión | Riesgo del explorador | Afecta |
|---|----------|-----------------------|--------|
| D1 | El notificador simulado escribe el enlace en el log **solo** si la propiedad de desarrollo está activada; está apagada por defecto. En UAT nunca se registra un token. | R2 | R13, R14 |
| D2 | El puerto se amplía en `cuentas/service/NotificadorCorreo` (no se mueve a `comun`; lo hará HU-25). | R4 | R5 |
| D3 | Al superar 3 solicitudes por correo por hora, la API responde el mismo 202 y el mismo mensaje, pero no genera enlace (sin 429). | R3 | R9 |
| D4 | Tras restablecer no se inicia sesión: la pantalla lleva al login con un aviso. | R8 | R25 |

## Supuestos a confirmar por el dueño al aprobar

> ⚠ Las fuentes no fijan estos puntos. El spec aplica la opción más segura y simple; si el
> dueño no acepta alguno, se ajustan los `R<n>` indicados antes de pasar a `in_progress`.
> Detalle y alternativas en `design.md` § 1 y § 11.

| # | Supuesto | Riesgo | Afecta |
|---|----------|--------|--------|
| S1 | Migración **`V1_5__token.sql`** (HU-04 es sprint 1) y `spring.flyway.out-of-order=true` en `application.properties`. Sin esa propiedad, Flyway no arranca en toda BD (local o UAT) que ya aplicó `V2_1` de HU-05. **Afecta a todo el equipo:** desde aquí Flyway aplica migraciones de número menor que lleguen tarde; la numeración por sprint sigue siendo obligatoria. Coordinar con HU-06 (Ariel Enriquez). | R1 | R2, R3 |
| S2 | A las cuentas **no habilitadas** (rol `ADMIN`, correo no verificado o `estado` ≠ `ACTIVA`, la misma regla de HU-03) no se les emite enlace: misma respuesta y auditoría con el motivo. Si la cuenta deja de estar habilitada antes de usar el enlace, se rechaza al validarlo o confirmarlo. | R5 | R8, R16, R27, R29 |
| S3 | Tabla `token` de `docs/modelo-datos.md` + columna `creado_en`, `hash_token` único con `CHECK` hex (como `sesion`), FK con `ON DELETE CASCADE` e índice por `usuario_id`. El `CHECK` de `tipo` admite los dos valores del modelo (`VERIFICACION`, `RECUPERACION`); el código solo usa `RECUPERACION`. El implementer actualiza `docs/modelo-datos.md` en el mismo PR. | R6 | R2, R3 |
| S4 | **Uso único atómico:** el token se marca usado con un `UPDATE` condicional (`usado_en IS NULL AND expira_en > ahora`) y se comprueban las filas afectadas. Pedir un enlace nuevo anula los pendientes; restablecer anula todos los pendientes. Anular = fijar `usado_en` (las filas no se borran). | R7 | R6, R19, R20, R22 |
| S5 | El token viaja en el **fragmento** del enlace (`#token=`), que el navegador no envía a ningún servidor, y hacia la API en el **cuerpo** de un `POST`. Se valida su forma antes de calcular el hash. Un **solo mensaje** para enlace inválido, expirado o usado. Hay un endpoint para validar el enlace al abrirlo (CA2 "cuando lo abro"). | R9 | R5, R15, R16, R17 |
| S6 | Mensajes y acciones de auditoría de "Definiciones" y R26–R30. La validación del enlace al abrirlo no se audita (no es "uso"); los rechazos al confirmar sí. | R10, R12 | R1, R16, R18, R26–R30 |
| S7 | Al restablecer, las sesiones se revocan en la misma transacción que el cambio de contraseña, y se reinicia el contador de fallos de inicio de sesión de HU-03 para ese correo (quien olvidó la contraseña suele haber agotado sus intentos). | R8 | R21, R24 |
| S8 | La nueva contraseña solo se valida con la política de HU-01 (`PoliticaContrasena`); no se rechaza si es igual a la actual. | R10 | R23 |
| S9 | Cuentan para el límite todas las solicitudes válidas en forma, exista o no la cuenta; no cuentan las rechazadas con 400 ni las que ya superaron el límite (como `LimitadorIntentos.intentar`). | R3 | R10, R11 |
| S10 | **Tiempo de respuesta:** la rama "cuenta existe" guarda un token y llama al notificador; la otra no. Con el envío simulado la diferencia es mínima; se acepta y se revisa en HU-25 con el envío real. | R11 | R7 |
| S11 | El contador de solicitudes vive **en memoria** (`LimitadorIntentos`): se pierde al reiniciar y no se comparte entre instancias. Mismo supuesto que HU-01 y HU-03: una sola instancia en UAT. | R13 | R9, R10 |
| S12 | **Sin límite por IP** en los endpoints de recuperación: RNF-SEG-17 solo da "3 por correo / hora" y el spec no inventa otra cifra. El token de 256 bits hace inútil la fuerza bruta contra la confirmación. | R3 | — |

## Definiciones

- **Correo normalizado:** el de HU-01 (`Usuario.normalizarCorreo`: sin espacios a los
  extremos y en minúsculas).
- **Correo válido en forma:** no vacío, con formato de correo y de hasta 254 caracteres (las
  reglas de `RegistroUsuarioDTO.correo`).
- **Cuenta habilitada:** usuario con rol `USUARIO` o `ARRENDADOR`, `correo_verificado = true` y
  `estado = ACTIVA` (`Usuario.motivoRechazoInicioSesion()` vacío, HU-03) (⚠ S2).
- **Token de recuperación:** valor de `TokenAleatorio.generar()` (32 bytes aleatorios en
  base64url sin relleno, 43 caracteres).
- **Forma válida del token:** exactamente 43 caracteres del alfabeto `A–Z a–z 0–9 - _`.
- **Vigencia:** 30 minutos desde `creado_en`.
- **Token vigente:** fila de `token` con `tipo = RECUPERACION`, `usado_en` nulo y `expira_en`
  posterior a la fecha y hora del reloj del sistema.
- **Enlace de recuperación:** `<espacioya.frontend.url>/restablecer-contrasena#token=<token>`
  (⚠ S5).
- **Límite de solicitudes:** 3 solicitudes contadas por correo normalizado en los 60 minutos
  anteriores (RNF-SEG-17) (⚠ S9, S11).
- **Propiedad de desarrollo:** `espacioya.correo.simulado.registrar-enlace`, `false` por defecto
  (D1).
- **Mensaje de solicitud recibida:** `"Si el correo está registrado, te enviamos un enlace para
  restablecer tu contraseña. Revisa tu bandeja de entrada."` (⚠ S6).
- **Mensaje de enlace inválido:** `"El enlace para restablecer tu contraseña no es válido o ya
  expiró. Solicita uno nuevo."` (⚠ S6).
- **Mensaje de contraseña restablecida:** `"Tu contraseña se cambió. Inicia sesión con tu nueva
  contraseña."` (⚠ S6).
- **Requisitos de contraseña:** `PoliticaContrasena.REQUISITOS` (HU-01).

## Solicitud del enlace (CA1, RF-A-11, STRIDE I y D)

## R1
CUANDO un cliente envía `POST /api/recuperaciones` con un correo válido en forma, el sistema
DEBE responder `202 Accepted` con un cuerpo que contenga solo el mensaje de solicitud recibida,
exista o no una cuenta con ese correo.

## R2
CUANDO el correo normalizado pertenece a una cuenta habilitada y no supera el límite de
solicitudes, el sistema DEBE guardar un token con el id de esa cuenta, `tipo = RECUPERACION`,
`creado_en` igual a la fecha y hora del reloj del sistema, `expira_en` igual a `creado_en` más
la vigencia y `usado_en` nulo.

## R3
El sistema DEBE guardar en `token.hash_token` el SHA-256 en hexadecimal (64 caracteres) del
token de recuperación, y NO DEBE guardar el token en claro en ninguna columna.

## R4
El sistema DEBE generar cada token de recuperación con `TokenAleatorio.generar()`, de modo que
dos solicitudes de la misma cuenta reciban tokens distintos.

## R5
CUANDO el sistema guarda un token según R2, el sistema DEBE invocar una sola vez
`NotificadorCorreo.notificarRecuperacion` con el correo normalizado y el enlace de recuperación
de ese token (D2, ⚠ S5).

## R6
CUANDO el sistema guarda un token según R2, el sistema DEBE fijar `usado_en` en la fecha y hora
del reloj del sistema en los demás tokens vigentes de esa cuenta (⚠ S4).

## R7
SI el correo normalizado no pertenece a ninguna cuenta ENTONCES el sistema DEBE responder
según R1 sin guardar un token ni invocar el notificador.

## R8
SI el correo normalizado pertenece a una cuenta no habilitada ENTONCES el sistema DEBE
responder según R1 sin guardar un token ni invocar el notificador (⚠ S2).

## R9
SI el correo normalizado ya tiene 3 solicitudes contadas en los 60 minutos anteriores
ENTONCES el sistema DEBE responder según R1 sin guardar un token ni invocar el notificador,
aunque la cuenta esté habilitada (D3).

## R10
El sistema DEBE contar una solicitud para el correo normalizado cada vez que responde según R1
sin aplicar R9, exista o no una cuenta con ese correo (⚠ S9).

## R11
El sistema NO DEBE contar para el límite una solicitud respondida con 400 (R12) ni una
solicitud a la que aplicó R9 (⚠ S9).

## R12
SI el correo enviado a `POST /api/recuperaciones` no es válido en forma ENTONCES el sistema
DEBE responder 400 con un error asociado al campo `correo`, sin guardar un token.

## Envío simulado (D1)

## R13
MIENTRAS la propiedad de desarrollo está desactivada, el notificador simulado NO DEBE escribir
en el log el enlace, el token ni el correo al notificar una recuperación.

## R14
DONDE la propiedad de desarrollo está activada, el notificador simulado DEBE escribir en el log
el enlace de recuperación, sin el correo.

## Apertura del enlace (CA2, RF-A-12)

## R15
CUANDO un cliente envía `POST /api/recuperaciones/validaciones` con un token vigente de una
cuenta habilitada, el sistema DEBE responder `204 No Content` sin modificar el token.

## R16
SI el token enviado a `POST /api/recuperaciones/validaciones` o a
`POST /api/recuperaciones/confirmaciones` no tiene forma válida, no corresponde a ningún token
de recuperación, tiene `expira_en` igual o anterior a la fecha y hora del reloj del sistema,
tiene `usado_en` no nulo o pertenece a una cuenta no habilitada ENTONCES el sistema DEBE
responder 400 con el mensaje de enlace inválido, sin modificar la contraseña, las sesiones ni
los tokens (⚠ S2, S5).

## R17
SI el token enviado no tiene forma válida ENTONCES el sistema DEBE rechazarlo según R16 sin
calcular su hash ni consultar la tabla `token` (⚠ S5).

## Restablecimiento (CA2, CA3, RF-A-12, RF-A-13, STRIDE S/T)

## R18
CUANDO un cliente envía `POST /api/recuperaciones/confirmaciones` con un token vigente de una
cuenta habilitada y una contraseña que cumple la política de HU-01 respecto del correo de esa
cuenta, el sistema DEBE reemplazar `usuario.hash_contrasena` por el hash bcrypt de la nueva
contraseña y responder `200 OK` con un cuerpo que contenga solo el mensaje de contraseña
restablecida.

## R19
CUANDO el sistema cambia la contraseña según R18, el sistema DEBE fijar `usado_en` del token
enviado en la fecha y hora del reloj del sistema, de modo que un segundo uso se rechace según
R16 (⚠ S4).

## R20
CUANDO el sistema cambia la contraseña según R18, el sistema DEBE fijar `usado_en` en todos los
demás tokens vigentes de esa cuenta (⚠ S4).

## R21
CUANDO el sistema cambia la contraseña según R18, el sistema DEBE marcar con `revocada = true`
todas las sesiones de esa cuenta que no estaban revocadas (CA3, RNF-SEG-02) (⚠ S7).

## R22
SI al marcar el token como usado otra confirmación ya lo había marcado ENTONCES el sistema DEBE
rechazar la solicitud según R16 sin cambiar la contraseña ni revocar sesiones (⚠ S4).

## R23
SI la nueva contraseña no cumple la política de HU-01 respecto del correo de la cuenta del
token ENTONCES el sistema DEBE responder 400 con los requisitos de contraseña asociados al
campo `contrasena`, sin marcar el token como usado (⚠ S8).

## R24
CUANDO el sistema cambia la contraseña según R18, el sistema DEBE reiniciar a cero los fallos
de inicio de sesión de HU-03 contados para el correo de esa cuenta (⚠ S7).

## R25
CUANDO el sistema cambia la contraseña según R18, el sistema NO DEBE crear una sesión ni
devolver un token de sesión (D4).

## Auditoría (STRIDE R, RNF-SEG-12)

## R26
CUANDO el sistema guarda un token según R2, el sistema DEBE registrar en la auditoría la acción
`RECUPERACION_SOLICITADA` con el id de la cuenta, la entidad `token` con el id del token, la IP
y la fecha y hora.

## R27
CUANDO el sistema responde según R7, R8 o R9, el sistema DEBE registrar en la auditoría la
acción `RECUPERACION_NO_EMITIDA` con el id de la cuenta (nulo si no existe), la IP, la fecha y
hora y en el detalle `motivo=` seguido de `LIMITE_SUPERADO`, `CORREO_DESCONOCIDO`,
`ROL_NO_PERMITIDO`, `CORREO_NO_VERIFICADO` o `CUENTA_NO_ACTIVA`.

## R28
CUANDO el sistema cambia la contraseña según R18, el sistema DEBE registrar en la auditoría la
acción `CONTRASENA_RESTABLECIDA` con el id de la cuenta, la entidad `usuario` con ese id, la
IP, la fecha y hora y en el detalle `sesionesRevocadas=` seguido del número de sesiones
revocadas según R21.

## R29
CUANDO el sistema rechaza según R16 o R22 una solicitud a
`POST /api/recuperaciones/confirmaciones`, el sistema DEBE registrar en la auditoría la acción
`RECUPERACION_RECHAZADA` con el id de la cuenta del token (nulo si no se encontró), la IP, la
fecha y hora y en el detalle `motivo=` seguido de `TOKEN_DESCONOCIDO`, `TOKEN_EXPIRADO`,
`TOKEN_USADO` o `CUENTA_NO_HABILITADA`.

## R30
El sistema NO DEBE escribir el token, su hash, el correo ni la contraseña en el detalle de las
auditorías de R26–R29.

## Minimización (STRIDE I)

## R31
El sistema NO DEBE incluir el correo, el token ni la contraseña en la representación textual
(`toString`) de los DTO de entrada de la recuperación.

## Trazabilidad con la historia

| Origen (`docs/historias/HU-04.md` y fuentes)                                   | Cubierto por |
|--------------------------------------------------------------------------------|--------------|
| CA1: siempre el mismo mensaje                                                  | R1, R7, R8, R9, R12 |
| CA1: si la cuenta existe, recibo un enlace válido por 30 minutos               | R2, R5, R13, R14, R15 (pantallas en design § 9) |
| CA2: enlace expirado o ya usado → se rechaza al abrirlo                        | R15, R16, R17, R19, R22 |
| CA2: puedo pedir otro                                                          | R2, R6 (nuevo enlace tras el rechazo; pantalla en design § 9) |
| CA3: al confirmar el cambio se cierran todas mis sesiones activas              | R18, R21, R25 |
| (S/T) Token aleatorio, de un solo uso, guardado con hash                       | R3, R4, R6, R16, R17, R19, R20, R22 |
| (I) Respuesta idéntica exista o no el correo                                   | R1, R7, R8, R9, R13, R30, R31 |
| (D) Máximo 3 solicitudes por correo cada hora                                  | R9, R10, R11 |
| (R) Registro de solicitud y uso                                                | R26, R27, R28, R29, R30 |
| RF-A-11 / 12 / 13                                                              | R1–R14 / R15–R20, R22, R23 / R21, R24, R25 |
| RNF-SEG-02 (tokens invalidados al cambiar la contraseña)                       | R21 |
| RNF-SEG-06 (recuperación aleatoria, un solo uso, con hash)                     | R3, R4, R19, R20, R22 |
| RNF-SEG-12 · 13 · 17                                                           | R26–R30 · R1, R7–R9, R16 · R9–R11 |
