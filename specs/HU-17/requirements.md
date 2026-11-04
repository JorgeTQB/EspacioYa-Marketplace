# HU-17 — Requirements: Autenticación multifactor (MFA)

> Fuente: `docs/historias/HU-17.md` (CA1–CA3, STRIDE S/I, D, R y "reemplaza o complementa el control de IPs"),
> `docs/requerimientos.md` (RF-A-17…19, RNF-SEG-04, 12, 14, 17, §8 "Gestión de sesión y tokens", "Rate limiting" y
> "Cifrado de campos sensibles", §9.4 y §9.7), `docs/modelo-datos.md` (`mfa`, `codigo_respaldo`),
> `docs/architecture.md` (Seguridad transversal, panel admin "hasta HU-17"), `specs/HU-03/` (inicio de sesión, sesión
> con token opaco), `specs/HU-06/` (D1, D2: IPs del admin, R1–R7) y `progress/explore_HU-17.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la presentación está en
> `design.md` § 12 Pantallas. Las decisiones técnicas están en `design.md` § 1.

## Decisiones por defecto que el dueño debe confirmar al aprobar

> ⚠ Las fuentes no fijan estos puntos. El spec aplica el valor por defecto indicado; si **Jorge Quispe** no lo
> acepta, cambian los requirements citados antes de pasar a `in_progress`. Detalle y alternativas en `design.md`.

| # | Pregunta | Por defecto en este spec | Afecta |
|---|----------|--------------------------|--------|
| P1 | ¿Cómo se representa el paso intermedio entre la contraseña y el código? | **Desafío MFA**: tras la contraseña correcta, `POST /api/sesiones` responde **202** con un token de desafío de un solo uso, vigente **5 min**, sin sesión; el segundo paso (`POST /api/sesiones/mfa`) lo canjea por la sesión de HU-03. Los usuarios sin MFA siguen recibiendo el 201 de HU-03. | R1–R22 |
| P2 | Límite de intentos MFA (§9.7, pendiente: "definirlo en el diseño") | **5 códigos incorrectos por desafío** → el desafío se invalida y hay que volver a poner la contraseña; además **10 códigos incorrectos por usuario en 15 min** (ventana deslizante, todas las vías) → 429 hasta que baje de 10. Un código aceptado reinicia el contador del usuario. | R19–R21, R45–R49 |
| P3 | ¿Se retira el control de IPs del admin (§9.4, HU-06 D2)? | **Se mantiene y complementa a MFA** (filtro de `/api/admin/**` y login del admin solo desde IPs permitidas). Si se quiere "reemplazar", basta configurar `ADMIN_IPS_PERMITIDAS=0.0.0.0/0,::/0`, sin cambiar código. | R9 |
| P4 | ¿Se habilita la recuperación de contraseña del admin ahora que hay MFA? | **No.** Sigue bloqueada como en HU-04 S2 / HU-06 D1 (fuera de los CA). | — (sin cambio) |
| P5 | ¿Cómo se desactiva MFA? | Solo `USUARIO` y `ARRENDADOR`, con **contraseña y un código vigente** (TOTP o de respaldo). El `ADMIN` no puede desactivarlo (403). | R40–R43 |
| P6 | ¿Qué pasa con las sesiones abiertas al activar MFA? | **Se revocan todas las demás** sesiones del usuario (una sesión robada antes de activar MFA no sobrevive). Desactivar no revoca nada. | R35 |
| P7 | ¿QR? | **Sin QR** ni dependencias nuevas: la API entrega la clave Base32 y el enlace `otpauth://`; la pantalla muestra la clave para escribirla en la app y el enlace para abrirla desde el celular. | R23, R29, R55 |
| P8 | Formato y ciclo de vida de los códigos de respaldo | 5 códigos de **10 caracteres Base32** (`XXXXX-XXXXX`), guardados como **HMAC-SHA256** con la llave de huella; se muestran **una sola vez**; se aceptan sin guion y en minúsculas. **Sin regeneración** en esta historia: se renuevan desactivando y activando de nuevo (el admin no puede). | R16, R17, R36–R38 |
| P9 | Parámetros TOTP | RFC 6238 con **HMAC-SHA1, 6 dígitos, paso de 30 s**, tolerancia **±1 paso**, y **sin reutilizar** un paso ya aceptado. | R14, R15, R51–R53 |
| P10 | ¿Y las sesiones de admin abiertas antes de HU-17 (sin MFA)? | **Se rechazan** (401) mientras ese admin no tenga MFA activo. | R28 |
| P11 | Textos de los mensajes | Los de "Definiciones" (abajo). | R18, R19, R30, R33, R41, R43 |
| P12 | ¿Dónde configura MFA el usuario? (no hay pantalla de perfil; HU-20 es de otra dueña) | Pantalla propia **`/panel/seguridad`**, enlazada desde el panel para los tres roles. | — (Pantallas) |

## Definiciones

- **Admin:** cuenta con `usuario.rol = ADMIN`. **Usuario final:** cuenta con rol `USUARIO` o `ARRENDADOR`.
- **Credenciales aceptadas:** solicitud a `POST /api/sesiones` que hoy recibiría `201` según HU-03 R1 (usuario final)
  o HU-06 R1 (admin desde una IP permitida): correo normalizado no bloqueado, contraseña correcta, correo verificado y
  cuenta `ACTIVA`.
- **MFA activo:** la cuenta tiene una fila en `mfa` con `activo = true`.
- **Configuración pendiente:** la cuenta tiene una fila en `mfa` con `activo = false` (secreto generado, aún sin
  confirmar con un código).
- **Secreto TOTP:** 20 bytes de un generador aleatorio criptográfico, codificados en Base32 (RFC 4648, alfabeto
  `A–Z2–7`) sin relleno: 32 caracteres.
- **Paso TOTP:** `floor(segundos epoch del reloj del sistema / 30)`.
- **Código TOTP aceptado:** 6 dígitos (se ignoran los espacios) iguales al valor RFC 6238 (HMAC-SHA1, 6 dígitos,
  `T0 = 0`, `X = 30 s`) del secreto para el paso actual, el anterior o el siguiente, y cuyo paso es mayor que
  `mfa.ultimo_paso` (o `ultimo_paso` es nulo).
- **Código de respaldo normalizado:** el texto enviado sin espacios ni guiones y en mayúsculas. **Código de respaldo
  aceptado:** su forma normalizada son 10 caracteres del alfabeto Base32 y coincide con un código de respaldo de la
  cuenta con `usado_en` nulo.
- **Desafío MFA:** token que la API entrega en el paso 1, de 32 bytes aleatorios en base64url sin relleno
  (43 caracteres), con un **paso** `CODIGO` o `CONFIGURACION`.
- **Desafío vigente para un endpoint:** existe una fila de `desafio_mfa` con el SHA-256 del token, `cerrado_en` nulo y
  `expira_en` posterior al reloj del sistema; su cuenta tiene `correo_verificado = true` y `estado = ACTIVA`; si es de
  paso `CODIGO`, la cuenta tiene MFA activo y, si es de paso `CONFIGURACION`, no lo tiene; y su paso es uno de los que
  admite el endpoint (`POST /api/sesiones/mfa`: los dos; `POST /api/sesiones/mfa/configuracion`: solo
  `CONFIGURACION`).
- **Fallo de código MFA:** solicitud con un código que el sistema rechaza según R19, R21, R27, R34 o R41.
- **Endpoints de código:** `POST /api/sesiones/mfa`, `POST /api/mfa/activacion` y `POST /api/mfa/desactivacion`.
- **Mensajes** (⚠ P11):
  - *verificación expirada:* `"Tu verificación expiró o no es válida. Inicia sesión de nuevo."`
  - *código inválido:* `"El código no es válido o ya se usó."`
  - *MFA ya activo:* `"La verificación en dos pasos ya está activa en tu cuenta."`
  - *sin configuración pendiente:* `"Primero genera tu clave de configuración."`
  - *MFA no activo:* `"La verificación en dos pasos no está activa en tu cuenta."`
  - *desactivación rechazada:* `"La contraseña o el código no son correctos."`
  - *credenciales inválidas*, *sesión inválida*, *demasiados intentos* y *acceso denegado*: los de `ErrorDTO`
    (HU-03, HU-05).

## Inicio de sesión: primer paso (CA1, CA2, RF-A-17, RF-A-18)

## R1
CUANDO un cliente envía a `POST /api/sesiones` credenciales aceptadas de una cuenta con MFA activo, el sistema DEBE
responder `202 Accepted` con un cuerpo que contenga solo `desafio` y `paso = "CODIGO"` (⚠ P1).

## R2
CUANDO un cliente envía a `POST /api/sesiones` credenciales aceptadas de un admin sin MFA activo (con o sin
configuración pendiente), el sistema DEBE responder `202 Accepted` con un cuerpo que contenga solo `desafio` y
`paso = "CONFIGURACION"`.

## R3
CUANDO un cliente envía a `POST /api/sesiones` credenciales aceptadas de un usuario final sin MFA activo (con o sin
configuración pendiente), el sistema DEBE responder según HU-03 R1 (`201` con `token`, `rol` y `aviso`).

## R4
CUANDO el sistema responde según R1 o R2, el sistema NO DEBE crear ninguna fila en `sesion`.

## R5
CUANDO el sistema responde según R1 o R2, el sistema DEBE guardar un desafío con el id del usuario, el `paso`
respondido, `creado_en` igual al reloj del sistema, `expira_en` igual a `creado_en` más 5 minutos,
`intentos_fallidos = 0` y `cerrado_en` nulo.

## R6
El sistema DEBE guardar en `desafio_mfa.hash_token` el SHA-256 en hexadecimal (64 caracteres) del desafío, y NO DEBE
guardar el desafío en claro en ninguna columna.

## R7
El sistema DEBE generar cada desafío con 32 bytes de un generador aleatorio criptográfico codificados en base64url
sin relleno (43 caracteres), de modo que dos inicios de sesión de la misma cuenta reciban desafíos distintos.

## R8
SI la contraseña enviada a `POST /api/sesiones` no es correcta o la cuenta no está habilitada (HU-03 R9–R12) para una
cuenta con MFA activo ENTONCES el sistema DEBE responder con el mismo código y el mismo cuerpo que HU-03 R9, sin
crear desafío.

## R9
SI un admin con MFA activo envía a `POST /api/sesiones` su correo y contraseña correctos desde una IP no permitida
ENTONCES el sistema DEBE responder según HU-06 R2 (401 de credenciales inválidas), sin crear desafío (⚠ P3).

## Inicio de sesión: segundo paso con el código (CA1, CA2, STRIDE S)

## R10
CUANDO un cliente envía a `POST /api/sesiones/mfa` un desafío vigente de paso `CODIGO` y un código TOTP aceptado para
el secreto de esa cuenta, el sistema DEBE responder `201 Created` con un cuerpo que contenga solo `token`, `rol`,
`aviso` (calculado como en HU-03 R4 y R5) y `codigosRespaldo = null`.

## R11
CUANDO un cliente envía a `POST /api/sesiones/mfa` un desafío vigente de paso `CODIGO` y un código de respaldo
aceptado de esa cuenta, el sistema DEBE responder como en R10.

## R12
CUANDO el sistema responde según R10, R11 o R26, el sistema DEBE crear la sesión según HU-03 R2, R3 y R6.

## R13
CUANDO el sistema responde según R10, R11 o R26, el sistema DEBE fijar `cerrado_en` del desafío en el reloj del
sistema, de modo que reenviarlo se responda según R18.

## R14
CUANDO el sistema acepta un código TOTP (R10, R25, R31, R40), el sistema DEBE guardar en `mfa.ultimo_paso` el paso
TOTP con el que coincidió el código.

## R15
SI el código TOTP enviado coincide solo con un paso igual o anterior a `mfa.ultimo_paso` ENTONCES el sistema DEBE
rechazarlo como código no aceptado (⚠ P9).

## R16
CUANDO el sistema acepta un código de respaldo (R11, R40), el sistema DEBE fijar su `usado_en` en el reloj del
sistema, de modo que el mismo código enviado otra vez no sea aceptado.

## R17
El sistema DEBE comparar los códigos de respaldo en su forma normalizada, de modo que `abcde-fghij`, `ABCDEFGHIJ` y
`ABCDE FGHIJ` sean el mismo código (⚠ P8).

## R18
SI un cliente envía a `POST /api/sesiones/mfa` o a `POST /api/sesiones/mfa/configuracion` un desafío que no es
vigente para ese endpoint ENTONCES el sistema DEBE responder 401 con el mensaje de verificación expirada, sin crear
sesión ni contar un fallo de código MFA.

## R19
SI un cliente envía a `POST /api/sesiones/mfa` un desafío vigente y un código no aceptado, y tras contarlo el desafío
tiene menos de 5 fallos, ENTONCES el sistema DEBE responder 400 con un error en el campo `codigo` con el mensaje de
código inválido, sin crear sesión (⚠ P2).

## R20
CUANDO el sistema rechaza un código según R19 o R21, el sistema DEBE sumar 1 a `intentos_fallidos` del desafío.

## R21
SI un código no aceptado lleva `intentos_fallidos` del desafío a 5 ENTONCES el sistema DEBE fijar `cerrado_en` del
desafío y responder 401 con el mensaje de verificación expirada, sin crear sesión (⚠ P2).

## R22
SI la solicitud a `POST /api/sesiones/mfa` tiene `desafio` vacío o de más de 64 caracteres, o `codigo` vacío o de más
de 20 caracteres ENTONCES el sistema DEBE responder 400 con un error asociado a ese campo, sin buscar el desafío ni
contar un fallo de código MFA.

## Configuración obligatoria del admin al iniciar sesión (CA1, CA3, RF-A-17)

## R23
CUANDO un cliente envía a `POST /api/sesiones/mfa/configuracion` un desafío vigente de paso `CONFIGURACION`, el
sistema DEBE responder 200 con un cuerpo que contenga solo `secreto` (secreto TOTP nuevo) y `uri` (R55).

## R24
CUANDO el sistema responde según R23 o R29, el sistema DEBE dejar la cuenta con una configuración pendiente cuyo
secreto es el devuelto, reemplazando el secreto de una configuración pendiente anterior.

## R25
CUANDO un cliente envía a `POST /api/sesiones/mfa` un desafío vigente de paso `CONFIGURACION` y un código TOTP
aceptado para el secreto de la configuración pendiente de esa cuenta, el sistema DEBE dejar su fila de `mfa` con
`activo = true` y `activado_en` igual al reloj del sistema.

## R26
CUANDO el sistema activa MFA según R25, el sistema DEBE responder `201 Created` con un cuerpo que contenga solo
`token`, `rol`, `aviso` y `codigosRespaldo` con los 5 códigos de R36 (CA3).

## R27
SI un cliente envía a `POST /api/sesiones/mfa` un desafío vigente de paso `CONFIGURACION` y la cuenta no tiene
configuración pendiente o el código no es un código TOTP aceptado para su secreto (un código de respaldo tampoco lo
es) ENTONCES el sistema DEBE responder según R19 o R21, sin activar MFA.

## R28
SI un cliente envía una solicitud a un endpoint protegido con el token de una sesión vigente de un admin sin MFA
activo ENTONCES el sistema DEBE responder 401 con el mensaje de sesión inválida (CA1, ⚠ P10).

## Activación por el usuario final (CA2, CA3, RF-A-18, RF-A-19)

## R29
CUANDO un cliente con sesión vigente de una cuenta sin MFA activo envía `POST /api/mfa/configuracion`, el sistema DEBE
responder 200 con un cuerpo que contenga solo `secreto` (secreto TOTP nuevo) y `uri` (R55).

## R30
SI un cliente con sesión vigente de una cuenta con MFA activo envía `POST /api/mfa/configuracion` ENTONCES el sistema
DEBE responder 400 con el mensaje de MFA ya activo, sin cambiar su secreto.

## R31
CUANDO un cliente con sesión vigente de una cuenta con configuración pendiente envía `POST /api/mfa/activacion` con un
código TOTP aceptado para ese secreto, el sistema DEBE dejar su fila de `mfa` con `activo = true` y `activado_en`
igual al reloj del sistema.

## R32
CUANDO el sistema activa MFA según R31, el sistema DEBE responder 200 con un cuerpo que contenga solo `codigos` con
los 5 códigos de R36 (CA3).

## R33
SI un cliente con sesión vigente de una cuenta sin configuración pendiente (sin fila de `mfa` o con MFA activo) envía
`POST /api/mfa/activacion` ENTONCES el sistema DEBE responder 400 con el mensaje de sin configuración pendiente, sin
contar un fallo de código MFA.

## R34
SI un cliente con sesión vigente de una cuenta con configuración pendiente envía `POST /api/mfa/activacion` con un
código que no es un código TOTP aceptado para ese secreto ENTONCES el sistema DEBE responder 400 con un error en el
campo `codigo` con el mensaje de código inválido, sin activar MFA.

## R35
CUANDO el sistema activa MFA según R25 o R31, el sistema DEBE revocar todas las sesiones vigentes de esa cuenta salvo
la sesión de la solicitud (R31) o la creada por ella (R26) (⚠ P6).

## Códigos de respaldo (CA3, RF-A-19, STRIDE S/I)

## R36
CUANDO el sistema activa MFA (R25, R31), el sistema DEBE generar exactamente 5 códigos de respaldo distintos, cada
uno de 10 caracteres del alfabeto Base32 tomados de un generador aleatorio criptográfico y devueltos con el formato
`XXXXX-XXXXX`.

## R37
El sistema DEBE guardar de cada código de respaldo solo el HMAC-SHA256 en hexadecimal de su forma normalizada
(`HuellaCampos`), y NO DEBE guardar el código en claro en ninguna columna.

## R38
El sistema NO DEBE incluir los códigos de respaldo en ninguna respuesta distinta de las de R26 y R32.

## Estado y desactivación (STRIDE R, ⚠ P5)

## R39
CUANDO un cliente con sesión vigente envía `GET /api/mfa`, el sistema DEBE responder 200 con un cuerpo que contenga
solo `activo`, `codigosRespaldoRestantes` (códigos con `usado_en` nulo; 0 sin MFA activo) y `desactivable` (`true`
solo si MFA está activo y la cuenta no es admin).

## R40
CUANDO un usuario final con MFA activo envía `POST /api/mfa/desactivacion` con su contraseña correcta y un código
TOTP o de respaldo aceptado, el sistema DEBE borrar su fila de `mfa` y todos sus códigos de respaldo y responder
`204 No Content`.

## R41
SI un usuario final con MFA activo envía `POST /api/mfa/desactivacion` con una contraseña incorrecta o un código no
aceptado ENTONCES el sistema DEBE responder 400 con el mensaje de desactivación rechazada, sin indicar cuál de los dos
falló y sin desactivar MFA.

## R42
SI un admin envía `POST /api/mfa/desactivacion` ENTONCES el sistema DEBE responder 403 con el mensaje de acceso
denegado, sin comprobar la contraseña ni el código y sin cambiar su MFA.

## R43
SI un usuario final sin MFA activo envía `POST /api/mfa/desactivacion` ENTONCES el sistema DEBE responder 400 con el
mensaje de MFA no activo, sin contar un fallo de código MFA.

## R44
SI un cliente envía una solicitud a `/api/mfa` o a cualquier ruta bajo `/api/mfa/` sin una sesión vigente ENTONCES
el sistema DEBE responder 401 con el mensaje de sesión inválida.

## Límite de intentos de código (STRIDE D, RNF-SEG-17, §9.7, ⚠ P2)

## R45
El sistema DEBE contar un fallo de código MFA para la cuenta cada vez que rechaza un código según R19, R21, R27, R34
o R41.

## R46
SI la cuenta acumula 10 fallos de código MFA en los últimos 15 minutos ENTONCES el sistema DEBE responder 429 con el
mensaje de demasiados intentos a toda solicitud de esa cuenta a un endpoint de código, sin comprobar el código ni la
contraseña, sin crear sesión, sin cambiar su MFA y sin sumar fallos al desafío.

## R47
CUANDO han pasado 15 minutos desde un fallo de código MFA, el sistema DEBE dejar de contarlo para R46.

## R48
CUANDO el sistema acepta un código (R10, R11, R25, R31, R40), el sistema DEBE reiniciar a cero los fallos de código
MFA contados para esa cuenta.

## R49
El sistema NO DEBE contar como fallo de código MFA una solicitud respondida según R18, R22, R33, R43 o R46.

## Secreto y algoritmo TOTP (STRIDE S/I, RNF-SEG-04, RNF-SEG-14)

## R50
El sistema DEBE guardar en `mfa.secreto` el secreto TOTP cifrado con `CifradorCampos` (AES-256-GCM, prefijo `v1:`),
y NO DEBE guardar el secreto en claro en ninguna columna.

## R51
El sistema DEBE calcular los códigos TOTP según RFC 6238 con HMAC-SHA1, 6 dígitos, `T0 = 0` y paso de 30 segundos,
de modo que con el secreto ASCII `12345678901234567890` produzca los 6 últimos dígitos de los vectores del
Apéndice B (p. ej. `287082` en el segundo 59) (⚠ P9).

## R52
El sistema DEBE aceptar el código TOTP del paso actual, del anterior y del siguiente.

## R53
SI el código TOTP enviado coincide solo con un paso a 2 o más pasos del actual ENTONCES el sistema DEBE rechazarlo
como código no aceptado.

## R54
El sistema DEBE generar cada secreto TOTP con 20 bytes de un generador aleatorio criptográfico, de modo que dos
configuraciones reciban secretos distintos.

## R55
El sistema DEBE devolver en `uri` el texto
`otpauth://totp/EspacioYa:<correo>?secret=<secreto>&issuer=EspacioYa&algorithm=SHA1&digits=6&period=30`, con el
correo de la cuenta codificado para URL (⚠ P7).

## R56
El sistema NO DEBE incluir el secreto TOTP en ninguna respuesta distinta de las de R23 y R29.

## R57
El sistema NO DEBE incluir el desafío, el código, la contraseña, el secreto, la URI, el token ni los códigos de
respaldo en la representación textual (`toString`) de los DTO de esta historia.

## R58
El sistema DEBE responder `GET /api/mfa`, `POST /api/mfa/configuracion`, `POST /api/mfa/activacion`,
`POST /api/sesiones/mfa` y `POST /api/sesiones/mfa/configuracion` con el encabezado `Cache-Control` que contenga
`no-store`.

## Auditoría (STRIDE R, RNF-SEG-12)

## R59
CUANDO el sistema responde según R1 o R2, el sistema DEBE registrar en la auditoría la acción `LOGIN_MFA_PENDIENTE`
con el id del usuario, la entidad `desafio_mfa` con el id del desafío, la IP, la fecha y hora y el detalle
`paso=CODIGO` o `paso=CONFIGURACION`.

## R60
CUANDO el sistema crea una sesión según R12, el sistema DEBE registrar en la auditoría la acción `LOGIN_EXITOSO` con
el id del usuario, la entidad `sesion` con el id de la sesión, la IP, la fecha y hora y el detalle `factor=TOTP` o
`factor=RESPALDO`.

## R61
CUANDO el sistema activa MFA según R25 o R31, el sistema DEBE registrar en la auditoría la acción `MFA_ACTIVADO` con
el id del usuario, la entidad `usuario` con ese id, la IP, la fecha y hora y el detalle `contexto=INICIO_SESION` (R25)
o `contexto=CUENTA` (R31).

## R62
CUANDO el sistema desactiva MFA según R40, el sistema DEBE registrar en la auditoría la acción `MFA_DESACTIVADO` con
el id del usuario, la entidad `usuario` con ese id, la IP y la fecha y hora.

## R63
CUANDO el sistema acepta un código de respaldo (R11, R40), el sistema DEBE registrar en la auditoría la acción
`MFA_CODIGO_RESPALDO_USADO` con el id del usuario, la entidad `usuario` con ese id, la IP, la fecha y hora y el detalle
`contexto=INICIO_SESION;restantes=<n>` o `contexto=DESACTIVACION;restantes=<n>`, con `<n>` los códigos sin usar que
le quedan.

## R64
CUANDO el sistema cuenta un fallo de código MFA (R45), el sistema DEBE registrar en la auditoría la acción
`MFA_CODIGO_FALLIDO` con el id del usuario, la entidad `usuario` con ese id, la IP, la fecha y hora y el detalle
`contexto=INICIO_SESION`, `contexto=ACTIVACION` o `contexto=DESACTIVACION`.

## R65
CUANDO el sistema responde 429 según R46, el sistema DEBE registrar en la auditoría la acción `MFA_BLOQUEADO` con el
id del usuario, la entidad `usuario` con ese id, la IP y la fecha y hora.

## R66
El sistema NO DEBE escribir el desafío, el código enviado, la contraseña, el secreto, la URI, el token de sesión ni
los códigos de respaldo en el detalle de ninguna auditoría.

## Reemplazos y compatibilidad

- **HU-06 R1** (el admin recibe `201` con `rol = "ADMIN"`): HU-17 lo **reemplaza** por R1/R2 + R10/R26. El admin sigue
  entrando solo desde una IP permitida (HU-06 R2–R4 sin cambios, R9): el control de IPs **complementa** a MFA (⚠ P3).
  En `SesionControllerIT` los casos de HU-06 R1 pasan a esperar `202` y completar el segundo paso; su `@DisplayName`
  cita también HU-17 R2 y R26.
- **HU-03 R1** se mantiene para usuarios finales sin MFA activo (R3); **HU-03 R30** gana el detalle `factor=…` solo
  cuando la sesión nace de un segundo paso (R60): un inicio de sesión sin MFA sigue con detalle nulo.
- **HU-06 R7 / `FiltroIpsAdmin`**, **HU-09** y **HU-16** (rutas `/api/admin/**`): sin cambios de conducta. Sus IT que
  inician sesión como admin pasan a usar el soporte de pruebas de MFA (`design.md` § 11): mismo resultado, ahora con
  el segundo paso.
- **HU-04 S2 / HU-06 D1:** el admin sigue sin recuperar su contraseña por correo (⚠ P4); solo cambia el comentario
  "hasta que exista MFA".
- `SesionService.validar` gana la regla de R28; para usuarios finales no cambia (HU-03 R22–R28, HU-09 R21).

## Preguntas abiertas para el dueño (no bloquean)

1. **Admin que pierde su app y sus 5 códigos.** No hay recuperación del admin (P4). Por defecto el spec documenta en el
   README un procedimiento operativo: borrar su fila de `mfa` por SQL (sus códigos caen en cascada); el siguiente
   inicio de sesión le exige configurar MFA otra vez (R2) y R28 deja inservibles sus sesiones mientras tanto. ¿Lo
   aceptas o prefieres otro procedimiento?
2. **Regenerar códigos de respaldo** sin desactivar MFA: fuera de esta historia por defecto (P8). ¿Hace falta?
3. **Aviso por correo** al activar o desactivar MFA: no lo piden los CA; por defecto no se envía. ¿Lo quieres?

## Trazabilidad con la historia

| Origen (`docs/historias/HU-17.md` y fuentes)                                         | Cubierto por |
|--------------------------------------------------------------------------------------|--------------|
| CA1: el admin debe configurar MFA al iniciar sesión                                  | R2, R4, R5, R23, R24, R25, R26, R27 |
| CA1: el admin debe usar MFA en cada inicio de sesión                                 | R1, R10, R11, R12, R13 |
| CA1: sin MFA el admin no accede al panel                                             | R2, R4, R28, R42 |
| CA2: usuario o arrendador activa MFA en su configuración                             | R29, R24, R31, R34, R35, R39 |
| CA2: los siguientes inicios de sesión piden el código                                | R1, R3, R10, R11 |
| CA3: al terminar la configuración recibo 5 códigos de respaldo de un solo uso        | R26, R32, R36, R16, R38 |
| (S/I) TOTP estándar RFC 6238                                                         | R51, R52, R53, R14, R15, R54, R55 |
| (S/I) Secretos cifrados                                                              | R50, R56, R57, R58 |
| (S/I) Códigos de respaldo con hash                                                   | R37, R17, R38 |
| (D) Límite de intentos de código por sesión                                          | R19, R20, R21, R45, R46, R47, R48, R49 |
| (R) Registro de activación, desactivación y uso                                      | R59, R60, R61, R62, R63, R64, R65, R66 |
| Reemplaza (o complementa) el control de IPs de HU-06 y HU-09                         | R9 (se mantiene, ⚠ P3; design § 10) |
| Desactivación (implícita en STRIDE R)                                                | R40, R41, R42, R43 |
| Desafío de un solo uso y de corta vida (STRIDE S)                                    | R5, R6, R7, R8, R13, R18, R22 |
| RF-A-17 / 18 / 19                                                                    | R2, R23–R28 / R1, R3, R29–R35 / R26, R32, R36–R38 |
| RNF-SEG-04 (TOTP, obligatorio admin, secretos cifrados, respaldo con hash)           | R2, R28, R50–R55, R37 |
| RNF-SEG-12 · 14 · 17                                                                 | R59–R66 · R50 · R45–R49 |
| §9.4 (IPs del admin) · §9.7 (límite de intentos MFA)                                 | R9 · R19–R21, R45–R49 |
