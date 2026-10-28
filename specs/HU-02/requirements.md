# HU-02 — Requirements: Registro de arrendador

> Fuente: `docs/historias/HU-02.md` (CA1–CA3, STRIDE E/I/R), `docs/requerimientos.md`
> (RF-A-04…06, RNF-SEG-12, 13, 14, 17, 18, RNF-CUM-03, RNF-USA-03, RN-01, §8 cifrado,
> §9.5 y §9.7 resueltos), `specs/HU-01/` (componentes reutilizados) y las decisiones del
> dueño en `progress/HU-02.md` (2026-10-27).
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la
> presentación está en `design.md` § Pantallas. Las decisiones técnicas tomadas por el
> spec_author están en `design.md` § 1 "Decisiones".

## Decisión confirmada por Adriana Callupe el 2026-10-27

> ⚠ **D1 — Alcance del CA3 y estado inicial "correo pendiente" (huecos 1-2).** El dueño
> no respondió. El spec aplica por defecto la regla **"como HU-01"** (supuesto S5 de
> HU-01: no hay correo real, ni token, ni endpoint de verificación hasta HU-25, y no hay
> inicio de sesión hasta HU-03):
>
> - El arrendador **nace con `correo_verificado = true`**, no en "correo pendiente" como
>   dice el CA1 literal. `docs/architecture.md` (Estados del sistema) ya dice que hoy no
>   se llega a "correo pendiente".
> - El aviso "debes verificar tu identidad para publicar" lo devuelve la API en la
>   **confirmación del registro** (R5), y el perfil queda con
>   `estado_identidad = NO_INICIADA` (R2).
> - El aviso **tras iniciar sesión** queda para **HU-03** (que depende de HU-02).
>
> **Adriana Callupe: confírmalo al aprobar.** Si no se acepta, cambian R1, R5, R31 y la
> pantalla de confirmación, antes de pasar a `in_progress`.

## Decisiones del dueño (2026-10-27, `progress/HU-02.md`)

| # | Decisión | Afecta |
|---|----------|--------|
| Hueco 4 | Nombre completo y teléfono obligatorio (9 dígitos, empieza con 9) en `usuario`. Facturación = tipo y número de documento, razón social o nombre, y dirección fiscal, en columnas cifradas separadas. | R3, R12–R16, R34 |
| Hueco 5 | DNI de 8 dígitos; RUC de 11 dígitos con prefijo 10/15/17/20 y dígito verificador módulo 11; el número debe coincidir con el tipo. | R17–R21 |
| Hueco 6 | Unicidad del documento con `hash_documento` (HMAC) e índice único. Un duplicado responde igual que un registro exitoso y no crea la cuenta. | R25–R27, R30, R37 |

## Definiciones

- **Versión vigente**, **política de contraseña**, **correo normalizado**, **mensaje de
  registro recibido** y **requisitos de contraseña**: los mismos de
  `specs/HU-01/requirements.md` (Definiciones).
- **Aviso de identidad:** `"Para publicar tus espacios, primero debes verificar tu
  identidad."`
- **Número normalizado:** el `numeroDocumento` sin espacios a los extremos.
- **Teléfono válido:** exactamente 9 caracteres, todos dígitos `0`–`9`, el primero `9`.
- **Registro válido:** solicitud `POST /api/arrendadores` con correo válido (≤ 254),
  contraseña que cumple la política, `aceptaPrivacidad = true`, `versionPolitica` igual
  a la versión vigente, `nombre` no vacío de hasta 100 caracteres, teléfono válido,
  `tipoDocumento` `DNI` o `RUC`, número normalizado válido para su tipo (R17–R21),
  `razonSocial` no vacía de hasta 150 caracteres y `direccionFiscal` no vacía de hasta
  200 caracteres.
- **Datos de facturación:** número de documento, razón social o nombre, y dirección fiscal.
- **Intento de registro:** toda solicitud `POST /api/usuarios` o `POST /api/arrendadores`
  que llega al servidor y no es rechazada por el propio límite (R42), sea válida o no.
- **Llave de cifrado / llave de huella:** valores en base64 de las variables de entorno
  `CIFRADO_LLAVE` y `CIFRADO_LLAVE_HUELLA` (propiedades `espacioya.cifrado.llave` y
  `espacioya.cifrado.llave-huella`).

## Registro exitoso (CA1)

## R1
CUANDO un visitante envía un registro válido a `POST /api/arrendadores` cuyo correo
normalizado no pertenece a ninguna cuenta y cuyo número normalizado no pertenece a
ningún perfil de arrendador, el sistema DEBE crear un usuario con rol `ARRENDADOR`,
estado `ACTIVA` y `correo_verificado = true` (⚠ D1).

## R2
CUANDO el sistema crea la cuenta según R1, el sistema DEBE crear el perfil de
arrendador de ese usuario con el `tipo_documento` enviado, `estado_identidad =
NO_INICIADA` y `motivo_rechazo` nulo.

## R3
CUANDO el sistema crea la cuenta según R1, el sistema DEBE guardar en el usuario el
`nombre` sin espacios a los extremos y el `telefono` enviado.

## R4
CUANDO el sistema crea la cuenta según R1, el sistema DEBE guardar en el usuario la
versión de la política aceptada (`version_politica`) y la fecha y hora del
consentimiento (`fecha_consentimiento`) tomada del reloj del sistema.

## R5
CUANDO el sistema crea la cuenta según R1, el sistema DEBE responder `202 Accepted` con
un cuerpo que contenga solo `mensaje` igual al mensaje de registro recibido y `aviso`
igual al aviso de identidad (⚠ D1).

## R6
CUANDO el sistema crea la cuenta según R1, el sistema DEBE invocar una sola vez la
notificación de verificación del notificador de correo con el correo normalizado.

## R7
El sistema DEBE guardar el correo de la cuenta del arrendador en su forma normalizada.

## Consentimiento (CA1, RNF-CUM-03)

## R8
SI el visitante envía el registro de arrendador con `aceptaPrivacidad` falso o ausente
ENTONCES el sistema DEBE responder 400 con un error asociado al campo
`aceptaPrivacidad`, sin crear usuario ni perfil ni invocar el notificador de correo.

## R9
SI el visitante envía el registro de arrendador con `versionPolitica` ausente o distinta
de la versión vigente ENTONCES el sistema DEBE responder 400 con un error asociado al
campo `versionPolitica`, sin crear usuario ni perfil ni invocar el notificador de correo.

## Datos de acceso, contacto y facturación (CA1 "datos válidos", RNF-USA-03)

## R10
SI la contraseña no cumple la política de contraseña ENTONCES el sistema DEBE responder
400 con los requisitos de contraseña asociados al campo `contrasena`, sin crear usuario
ni perfil.

## R11
SI el correo falta, no tiene formato de correo o supera 254 caracteres ENTONCES el
sistema DEBE responder 400 con un error asociado al campo `correo`, sin crear usuario
ni perfil.

## R12
SI el `nombre` falta, está vacío o supera 100 caracteres ENTONCES el sistema DEBE
responder 400 con un error asociado al campo `nombre`, sin crear usuario ni perfil.

## R13
SI el `telefono` falta o no es un teléfono válido ENTONCES el sistema DEBE responder 400
con un error asociado al campo `telefono`, sin crear usuario ni perfil.

## R14
SI la `razonSocial` falta, está vacía o supera 150 caracteres ENTONCES el sistema DEBE
responder 400 con un error asociado al campo `razonSocial`, sin crear usuario ni perfil.

## R15
SI la `direccionFiscal` falta, está vacía o supera 200 caracteres ENTONCES el sistema
DEBE responder 400 con un error asociado al campo `direccionFiscal`, sin crear usuario
ni perfil.

## R16
SI el `tipoDocumento` falta o es distinto de `DNI` y de `RUC` ENTONCES el sistema DEBE
responder 400 con el mensaje `"Elige el tipo de documento: DNI o RUC."` asociado al
campo `tipoDocumento`, sin crear usuario ni perfil.

## RUC / DNI (CA2, RF-A-05, RNF-USA-03)

## R17
SI el `numeroDocumento` falta o el número normalizado está vacío ENTONCES el sistema
DEBE responder 400 con el mensaje `"Ingresa tu número de documento."` asociado al campo
`numeroDocumento`, sin crear usuario ni perfil.

## R18
SI el `tipoDocumento` es `DNI` y el número normalizado no está formado por exactamente 8
dígitos ENTONCES el sistema DEBE responder 400 con el mensaje `"El DNI debe tener 8
dígitos."` asociado al campo `numeroDocumento`, sin crear usuario ni perfil.

## R19
SI el `tipoDocumento` es `RUC` y el número normalizado no está formado por exactamente
11 dígitos ENTONCES el sistema DEBE responder 400 con el mensaje `"El RUC debe tener 11
dígitos."` asociado al campo `numeroDocumento`, sin crear usuario ni perfil.

## R20
SI el `tipoDocumento` es `RUC`, el número tiene 11 dígitos y no empieza con `10`, `15`,
`17` ni `20` ENTONCES el sistema DEBE responder 400 con el mensaje `"El RUC debe empezar
con 10, 15, 17 o 20."` asociado al campo `numeroDocumento`, sin crear usuario ni perfil.

## R21
SI el `tipoDocumento` es `RUC`, el número tiene 11 dígitos con prefijo válido y su
último dígito no coincide con el dígito verificador módulo 11 (pesos 5, 4, 3, 2, 7, 6,
5, 4, 3, 2 sobre los 10 primeros dígitos; `11 − (suma mod 11)`, donde 10 → 0 y 11 → 1)
ENTONCES el sistema DEBE responder 400 con el mensaje `"El RUC no es válido. Revisa que
lo hayas escrito bien."` asociado al campo `numeroDocumento`, sin crear usuario ni perfil.

## Correo o documento ya registrados (anti-enumeración, RNF-SEG-13, hueco 6)

## R22
CUANDO el registro de arrendador es válido pero el correo normalizado ya pertenece a una
cuenta de cualquier rol, el sistema DEBE responder con el mismo código y el mismo cuerpo
que R5.

## R23
CUANDO el registro de arrendador trae un correo que ya pertenece a una cuenta, el
sistema NO DEBE crear usuario ni perfil, modificar la cuenta existente (incluido su rol)
ni invocar el notificador de correo.

## R24
CUANDO el registro de arrendador trae un correo que ya pertenece a una cuenta, el
sistema DEBE registrar en la auditoría la acción `REGISTRO_CORREO_EXISTENTE` con el id
de la cuenta existente, la IP y la fecha y hora.

## R25
CUANDO el registro de arrendador es válido, su correo no pertenece a ninguna cuenta y su
número normalizado ya pertenece a un perfil de arrendador, el sistema DEBE responder con
el mismo código y el mismo cuerpo que R5.

## R26
CUANDO el registro de arrendador trae un número de documento que ya pertenece a un
perfil de arrendador, el sistema NO DEBE crear usuario ni perfil ni invocar el
notificador de correo.

## R27
CUANDO el registro de arrendador trae un número de documento que ya pertenece a un
perfil de arrendador, el sistema DEBE registrar en la auditoría la acción
`REGISTRO_DOCUMENTO_EXISTENTE` con el id del titular de ese perfil, la IP y la fecha y
hora.

## R28
El sistema DEBE aplicar las validaciones de R8–R21 antes de consultar si el correo o el
documento existen, de modo que un registro inválido con correo o documento existente
reciba la misma respuesta que el mismo registro con correo y documento nuevos.

## R29
SI dos registros de arrendador válidos con el mismo correo normalizado llegan a la vez
ENTONCES el sistema DEBE crear una sola cuenta y responder a ambos como en R5.

## R30
SI dos registros de arrendador válidos con correos distintos y el mismo número
normalizado llegan a la vez ENTONCES el sistema DEBE crear una sola cuenta y responder
a ambos como en R5.

## Rol asignado en el servidor (STRIDE E, RNF-SEG-18)

## R31
El sistema DEBE asignar rol `ARRENDADOR`, estado `ACTIVA`, `correo_verificado = true` y
`estado_identidad = NO_INICIADA` aunque el cuerpo de la solicitud incluya campos `rol`,
`estado`, `correoVerificado` o `estadoIdentidad`.

## Auditoría del alta (STRIDE R, RNF-SEG-12, RNF-CUM-03)

## R32
CUANDO el sistema crea la cuenta según R1, el sistema DEBE registrar en la auditoría la
acción `REGISTRO_ARRENDADOR` con el id del nuevo usuario, la IP de la solicitud, la
fecha y hora y la versión de la política aceptada en el detalle.

## R33
El sistema NO DEBE escribir el número de documento, la razón social, la dirección
fiscal ni el teléfono en el detalle de las auditorías del registro de arrendador.

## Cifrado de datos de facturación en reposo (STRIDE I, RNF-SEG-14)

## R34
El sistema DEBE guardar `numero_documento`, `razon_social` y `direccion_fiscal` del
perfil de arrendador cifrados con AES-256-GCM, de modo que la columna no contenga el
texto claro y al descifrarla con la llave de cifrado se obtenga el valor original.

## R35
CUANDO el sistema cifra dos veces el mismo valor, el sistema DEBE producir dos textos
cifrados distintos.

## R36
SI un texto cifrado fue alterado o fue cifrado con otra llave ENTONCES el sistema DEBE
lanzar una excepción al descifrarlo, sin devolver texto.

## R37
El sistema DEBE guardar en `hash_documento` el HMAC-SHA256 del número normalizado
calculado con la llave de huella, en hexadecimal de 64 caracteres.

## R38
SI la llave de cifrado o la llave de huella está vacía o no decodifica en base64 a
exactamente 32 bytes ENTONCES el sistema DEBE fallar al iniciar con un mensaje que nombre
la variable de entorno sin mostrar su valor.

## R39
SI la llave de cifrado y la llave de huella son iguales ENTONCES el sistema DEBE fallar
al iniciar.

## Minimización en tránsito (STRIDE I)

## R40
El sistema NO DEBE incluir el número de documento, la razón social, la dirección fiscal,
el teléfono, el nombre ni la contraseña en ninguna respuesta de `POST /api/arrendadores`.

## R41
El sistema NO DEBE incluir la contraseña, el correo, el nombre, el teléfono, el número
de documento, la razón social ni la dirección fiscal en la representación textual
(`toString`) del DTO de registro de arrendador.

## Límite de registros por IP (STRIDE D transversal, RNF-SEG-17, §9.7)

## R42
SI una IP ya hizo 5 intentos de registro en los últimos 60 minutos, sumando
`POST /api/usuarios` y `POST /api/arrendadores`, ENTONCES el sistema DEBE responder 429
con el mensaje genérico `"Hiciste demasiados intentos. Espera un momento e inténtalo de
nuevo."` sin procesar la solicitud.

## R43
El sistema DEBE contar como intento de registro toda solicitud a
`POST /api/arrendadores` que llega al límite, incluidas las rechazadas con 400 y las de
correo o documento existente.

## Errores genéricos (RNF-SEG-13)

## R44
SI el notificador de correo lanza una excepción al notificar la verificación de un
arrendador ENTONCES el sistema DEBE responder 500 con el `ErrorDTO` genérico de HU-01
sin dejar guardados el usuario ni el perfil de esa solicitud.

## Trazabilidad con la historia

| Origen (`docs/historias/HU-02.md` y fuentes)                                   | Cubierto por |
|--------------------------------------------------------------------------------|--------------|
| CA1: formulario con datos válidos → cuenta con rol arrendador                  | R1, R2, R3, R5, R6, R7, R12–R16 |
| CA1: estado "correo pendiente" (⚠ D1: nace con correo verificado, como HU-01)  | R1, R31 |
| CA1: acepto la política de privacidad (consentimiento con versión, fecha, IP)  | R4, R8, R9, R32 |
| CA2: RUC/DNI con formato inválido → error de validación específico            | R16, R17, R18, R19, R20, R21 |
| CA3: "correo verificado" y aviso de verificar identidad (⚠ D1: en la confirmación; tras login en HU-03) | R1, R2, R5 |
| (E) Rol arrendador asignado en backend, no aceptado del cliente               | R1, R31 |
| (I) Datos de facturación cifrados en reposo                                    | R34, R35, R36, R37, R38, R39 |
| (I) Datos de facturación cifrados en tránsito (TLS en UAT, ver design § 1 DT-10; minimización) | R40, R41 |
| (R) Evento de creación con fecha, hora e IP                                    | R4, R32, R33 |
| Hueco 6: documento único, duplicado indistinguible                             | R25, R26, R27, R28, R30, R37 |
| RNF-SEG-13: no se revela si un correo existe                                   | R22, R23, R24, R28, R29, R44 |
| RNF-SEG-17 / §9.7: 5 registros por IP por hora                                 | R42, R43 |
| RF-A-04 / RF-A-05 / RF-A-06                                                    | R1–R16 / R16–R21 / R1, R2, R5 |
| RNF-SEG-12 · 14 · 18 · CUM-03 · USA-03 · RN-01                                 | R32, R33 · R34–R39 · R31 · R4, R8, R9, R32 · R10–R21 · R2, R5 (nunca "verificado" a secas) |
