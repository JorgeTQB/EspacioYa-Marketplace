# HU-20 — Requirements: Edición de perfil

> Fuente: `docs/historias/HU-20.md` (CA1–CA3, STRIDE E, S, R/T), `docs/requerimientos.md` (RF-A-20…22, RNF-SEG-01,
> 03, 12; relacionados RNF-SEG-02, 08, 13, 14, 16, 17, 18, RNF-USA-02, 03, RN-01, RN-02, §8 auditoría, correo,
> almacenamiento y cifrado), `docs/architecture.md` (módulo `cuentas`, `CuentaService.confirmarContrasena` de HU-18,
> estados de identidad), `docs/modelo-datos.md` (`usuario`, `perfil_arrendador`, `token`), `specs/HU-03/` (sesión),
> `specs/HU-04/` (tokens de un solo uso), `specs/HU-18/` (reautenticación con límite de fallos) y
> `progress/explore_HU-20.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la presentación está en
> `design.md` § 12 Pantallas. Las decisiones (D1–D22) y su porqué están en `design.md` § 1, en la tabla
> **"Decisiones para revisar por la dueña"**: si Adriana Callupe cambia alguna, cambian los `R<n>` que cita **antes**
> de pasar a `in_progress`.

## Definiciones

- **Titular de la sesión:** la cuenta cuyo id viene en `UsuarioAutenticado` (HU-03). Nunca un id del cliente.
- **Rutas de perfil:** `GET`/`PUT /api/usuarios/actual/perfil`, `GET`/`PUT`/`DELETE /api/usuarios/actual/foto`,
  `POST /api/usuarios/actual/cambios-correo` y `PUT /api/arrendadores/actual/facturacion`. La confirmación del correo
  (`POST /api/cambios-correo/confirmaciones`) **no** es una ruta de perfil: es pública (D21).
- **Datos básicos:** `usuario.nombre` y `usuario.telefono`. No exigen contraseña (D5).
- **Datos de facturación:** `perfil_arrendador.tipo_documento`, `numero_documento`, `razon_social` y
  `direccion_fiscal`. Solo existen para `ARRENDADOR` (D7) y exigen contraseña (STRIDE S).
- **Datos de identidad:** el documento (`tipo_documento` + `numero_documento`) y la `razon_social` (D9).
- **Contraseña de perfil:** la `contrasena` que se pide para cambiar el correo o la facturación; se compara con
  `CuentaService.confirmarContrasena`. Sus fallos se cuentan por cuenta con la clave `perfil|<usuarioId>` (D6).
- **Correo pendiente:** `usuario.correo_pendiente`, el correo pedido y aún no confirmado (D1).
- **Token de cambio de correo:** fila de `token` con `tipo = CAMBIO_CORREO`, SHA-256 hex, vigente 30 minutos (D1, D22).
- **Número enmascarado:** `****` + los 4 últimos dígitos. **Correo enmascarado:** primer carácter + `***` + `@` +
  dominio (`j***@correo.pe`).
- **Mensajes:** texto exacto en `design.md` § 4.

## Acceso e identidad (STRIDE E, RNF-SEG-01, 18)

## R1
SI una solicitud a cualquier ruta de perfil llega sin sesión vigente ENTONCES el sistema DEBE responder 401 sin leer
ni escribir `usuario`, `perfil_arrendador`, `token` ni el almacenamiento.

## R2
SI una sesión con rol `ADMIN` envía una solicitud a `/api/usuarios/actual/perfil`, `/api/usuarios/actual/foto` o
`/api/usuarios/actual/cambios-correo` ENTONCES el sistema DEBE responder 403 sin leer ni escribir nada y sin comprobar
ninguna contraseña (D7).

## R3
SI una sesión cuyo rol no es `ARRENDADOR` envía `PUT /api/arrendadores/actual/facturacion` con un cuerpo válido
ENTONCES el sistema DEBE responder 403 sin comprobar la contraseña ni contar un intento fallido.

## R4
El sistema DEBE tomar la cuenta a leer o modificar solo de la sesión: ninguna ruta de perfil tiene un id en la URL, y
un `id`, `usuarioId`, `rol`, `estadoIdentidad`, `correoVerificado` o `correo` enviado en un cuerpo que no lo pide se
ignora sin modificar ninguna otra cuenta ni esos campos.

## Consulta (CA1)

## R5
CUANDO un `USUARIO` o `ARRENDADOR` envía `GET /api/usuarios/actual/perfil`, el sistema DEBE responder 200 con su rol,
nombre, teléfono, correo, correo pendiente (o nulo), si tiene foto y, solo para `ARRENDADOR`, su facturación (tipo de
documento, número enmascarado, razón social, dirección fiscal y estado de identidad); para `USUARIO` la facturación es
nula.

## R6
El sistema NO DEBE devolver en ninguna respuesta de perfil el número de documento completo, `hash_documento`, la
contraseña ni su hash, la clave de almacenamiento de la foto ni ningún token.

## R7
El sistema DEBE responder todas las rutas de perfil y la confirmación de correo con `Cache-Control: no-store`.

## Datos básicos (CA1)

## R8
CUANDO un `USUARIO` o `ARRENDADOR` envía `PUT /api/usuarios/actual/perfil` con datos válidos, el sistema DEBE guardar
el nombre (sin espacios extremos) y el teléfono y responder 200 con el perfil ya actualizado, de modo que un `GET`
inmediato devuelva los mismos valores (CA1).

## R9
SI el `PUT /api/usuarios/actual/perfil` trae un `nombre` de más de 100 caracteres o un `telefono` no vacío que no
cumple `^9[0-9]{8}$` ENTONCES el sistema DEBE rechazarlo con 400 y un error por cada campo inválido, sin guardar nada.

## R10
SI un `ARRENDADOR` envía `PUT /api/usuarios/actual/perfil` con el `nombre` en blanco o sin `telefono` ENTONCES el
sistema DEBE rechazarlo con 400 y un error en cada campo faltante, sin guardar nada (D8).

## R11
CUANDO un `USUARIO` envía `PUT /api/usuarios/actual/perfil` con `nombre` o `telefono` nulos o en blanco, el sistema
DEBE guardar ese campo como nulo (D8).

## R12
El sistema NO DEBE exigir contraseña en `PUT /api/usuarios/actual/perfil` ni modificar con ella el correo, la foto o
la facturación (D5).

## Foto (CA1, RNF-SEG-08)

## R13
CUANDO un `USUARIO` o `ARRENDADOR` envía `PUT /api/usuarios/actual/foto` (multipart, campo `foto`) con una imagen JPG o
PNG real de hasta 5 MiB, el sistema DEBE guardarla re-codificada y sin metadatos, cifrada en el almacenamiento privado
(carpeta `perfiles`), registrarla como su foto y responder 200 con el perfil y `tieneFoto = true` (D15).

## R14
SI la foto falta, está vacía, supera 5 MiB, no es una imagen legible o su contenido real no es JPG ni PNG (incluido un
PDF) ENTONCES el sistema DEBE rechazarla con 400 y un error en el campo `foto`, sin guardar ningún archivo y sin cambiar
la foto vigente.

## R15
CUANDO el sistema reemplaza o quita una foto y la transacción se confirma, el sistema DEBE eliminar del almacenamiento
el archivo anterior; y SI la transacción que registra una foto nueva se revierte ENTONCES el sistema DEBE eliminar el
archivo nuevo y conservar el anterior.

## R16
CUANDO el titular envía `GET /api/usuarios/actual/foto`, el sistema DEBE responder 200 con los bytes de su foto, su tipo
real (`image/jpeg` o `image/png`) y `X-Content-Type-Options: nosniff`, o 404 si no tiene foto (D15).

## R17
CUANDO el titular envía `DELETE /api/usuarios/actual/foto`, el sistema DEBE dejar su cuenta sin foto y responder 200
con `tieneFoto = false`; sin foto previa, la respuesta es la misma y no se audita nada.

## Reautenticación (STRIDE S, RNF-SEG-03)

## R18
SI `POST /api/usuarios/actual/cambios-correo` o `PUT /api/arrendadores/actual/facturacion` llega sin `contrasena` o con
`contrasena` en blanco ENTONCES el sistema DEBE rechazarlo con 400 y un error en el campo `contrasena`, sin comprobar
nada más ni contar un intento fallido.

## R19
SI la `contrasena` de `POST /api/usuarios/actual/cambios-correo` o `PUT /api/arrendadores/actual/facturacion` es
incorrecta ENTONCES el sistema DEBE rechazarlo con 400 y un error en el campo `contrasena`, sin modificar nada, contar
el fallo con la clave `perfil|<usuarioId>` y auditar `PERFIL_CONTRASENA_FALLIDA` con usuario, IP y la ruta usada
(`operacion=CORREO` o `operacion=FACTURACION`).

## R20
SI la cuenta acumuló 5 fallos de contraseña de perfil en los últimos 15 minutos ENTONCES el sistema DEBE responder 429
con el mensaje genérico de demasiados intentos a cualquier `POST /api/usuarios/actual/cambios-correo` o
`PUT /api/arrendadores/actual/facturacion` siguiente, sin comprobar la contraseña ni modificar nada, hasta que venza la
ventana (D6).

## R21
CUANDO la contraseña de perfil es correcta, el sistema DEBE reiniciar el contador de fallos de `perfil|<usuarioId>`.

## Cambio de correo (CA2)

## R22
SI `POST /api/usuarios/actual/cambios-correo` trae un `correo` en blanco, sin formato de correo o de más de 254
caracteres ENTONCES el sistema DEBE rechazarlo con 400 y un error en el campo `correo`, sin comprobar la contraseña.

## R23
SI el correo pedido, normalizado (minúsculas y sin espacios extremos), es igual al correo actual de la cuenta ENTONCES
el sistema DEBE rechazarlo con 400 y un error en el campo `correo` (*ya es tu correo*), sin emitir token ni enviar
correos.

## R24
SI, con la contraseña correcta, la cuenta ya hizo 3 solicitudes de cambio de correo en la última hora ENTONCES el
sistema DEBE responder 429 con el mensaje genérico, sin modificar `correo_pendiente`, sin emitir token y sin enviar
correos (D18).

## R25
CUANDO un `USUARIO` o `ARRENDADOR` envía `POST /api/usuarios/actual/cambios-correo` con un correo válido, distinto del
actual y que no pertenece a otra cuenta, y su contraseña correcta, el sistema DEBE guardar el correo normalizado en
`correo_pendiente`, anular sus tokens `CAMBIO_CORREO` pendientes, crear uno nuevo (solo su SHA-256, vigente 30
minutos), mantener sin cambios `usuario.correo` y responder 202 con el mensaje genérico de solicitud recibida (CA2).

## R26
CUANDO la transacción de R25 se confirma, el sistema DEBE enviar al **correo nuevo** el enlace
`<espacioya.frontend.url>/confirmar-correo#token=<token>` (CA2).

## R27
CUANDO la transacción de una solicitud de cambio de correo aceptada (R25 o R29) se confirma, el sistema DEBE enviar al
**correo actual** un aviso de que se pidió cambiar el correo de la cuenta, con el correo nuevo enmascarado (CA2, D2).

## R28
SI el envío de un correo de R26 o R27 falla ENTONCES el sistema DEBE mantener la solicitud confirmada y responder 202;
y SI la transacción se revierte ENTONCES el sistema NO DEBE enviar ninguno de los dos correos.

## R29
SI el correo pedido ya pertenece a otra cuenta ENTONCES el sistema DEBE responder exactamente lo mismo que en R25 (202 y
el mismo mensaje), guardar igual `correo_pendiente`, anular igual los tokens pendientes y enviar igual el aviso de R27,
pero NO DEBE crear un token ni enviar el enlace de R26 (RNF-SEG-13, D3).

## R30
MIENTRAS un correo esté pendiente sin confirmar, el sistema DEBE seguir usando `usuario.correo` como el correo de la
cuenta: el inicio de sesión con el correo pendiente responde 401 genérico y con el correo actual funciona (CA2).

## R31
CUANDO se envía `POST /api/cambios-correo/confirmaciones` con un token `CAMBIO_CORREO` vigente y no usado de una cuenta
`ACTIVA` con correo pendiente, el sistema DEBE marcar el token como usado, reemplazar `usuario.correo` por
`correo_pendiente`, dejar `correo_pendiente` nulo y responder 200 con el mensaje de correo confirmado (CA2).

## R32
SI el token de la confirmación está mal formado, no existe, no es de tipo `CAMBIO_CORREO`, ya se usó, expiró, o su
cuenta no está `ACTIVA` o no tiene correo pendiente ENTONCES el sistema DEBE responder 400 con el mensaje genérico de
enlace no válido, sin modificar ninguna cuenta.

## R33
SI al confirmar, el correo pendiente ya pertenece a otra cuenta (carrera contra `uq_usuario_correo`), o dos
confirmaciones del mismo token llegan a la vez ENTONCES el sistema DEBE cambiar el correo como mucho una vez y
responder a las demás 400 con el mismo mensaje genérico de R32, sin error 500 (D3).

## R34
El sistema DEBE aceptar `POST /api/cambios-correo/confirmaciones` sin sesión, con el token solo en el cuerpo, y NO DEBE
aceptar el token en la URL ni en un parámetro de consulta (D21).

## Facturación e identidad (CA3, RN-02)

## R35
SI `PUT /api/arrendadores/actual/facturacion` trae un `tipoDocumento` distinto de `DNI` o `RUC`, un `numeroDocumento`
no vacío que no cumple `ValidadorDocumento` para ese tipo, un `tipoDocumento` distinto del actual sin
`numeroDocumento`, una `razonSocial` en blanco o de más de 150 caracteres, o una `direccionFiscal` en blanco o de más de
200 ENTONCES el sistema DEBE rechazarlo con 400 y un error por cada campo inválido, sin comprobar la contraseña (D16).

## R36
CUANDO un `ARRENDADOR` envía `PUT /api/arrendadores/actual/facturacion` con datos válidos y su contraseña correcta, el
sistema DEBE guardar los datos que cambiaron (el número normalizado y con su huella recalculada si se envió uno
distinto; un `numeroDocumento` nulo o vacío conserva el actual) y responder 200 con el perfil actualizado (CA1).

## R37
CUANDO un `ARRENDADOR` con identidad `VERIFICADA` guarda un `tipoDocumento` o `numeroDocumento` distinto del actual, el
sistema DEBE dejar `estado_identidad = EN_REVISION` en la misma transacción (CA3, RN-02).

## R38
CUANDO un `ARRENDADOR` con identidad `VERIFICADA` guarda una `razonSocial` distinta de la actual, el sistema DEBE dejar
`estado_identidad = EN_REVISION` en la misma transacción (⚠ D9).

## R39
CUANDO un `ARRENDADOR` con identidad `VERIFICADA` cambia solo la `direccionFiscal`, el sistema DEBE mantener
`estado_identidad = VERIFICADA` (D9).

## R40
CUANDO un `ARRENDADOR` con identidad `NO_INICIADA` o `RECHAZADA` guarda su facturación, el sistema DEBE mantener su
`estado_identidad` (y su `motivo_rechazo`) sin cambios (D10).

## R41
SI un `ARRENDADOR` con identidad `EN_REVISION` envía, con la contraseña correcta, un documento o una razón social
distintos de los actuales ENTONCES el sistema DEBE rechazarlo con 400 y el mensaje de identidad en revisión, sin
modificar nada (D10).

## R42
SI el número de documento nuevo ya pertenece a otro arrendador (`uq_perfil_arrendador_hash_documento`) ENTONCES el
sistema DEBE rechazarlo con 400 y el mensaje genérico de documento no disponible, sin modificar nada, sin revelar la
otra cuenta y contando un fallo en `perfil|<usuarioId>` (D13).

## R43
SI el `PUT /api/arrendadores/actual/facturacion` no cambia ningún dato ENTONCES el sistema DEBE responder 200 con el
perfil sin escribir en `perfil_arrendador`, sin cambiar el estado de identidad y sin auditar una actualización.

## R44
CUANDO la identidad vuelve a `EN_REVISION` por R37 o R38, el sistema DEBE responder en `GET /api/sesiones/actual` el
aviso de identidad en revisión y en `CuentaService.habilitacionArrendador` la identidad como no verificada, desde la
solicitud siguiente (CA3, D12).

## Datos sensibles (STRIDE I, RNF-SEG-14)

## R45
El sistema DEBE mantener cifrados en reposo el número de documento, la razón social y la dirección fiscal tras
editarlos (columna `v1:…`, sin el valor en claro), y DEBE guardar la foto cifrada (el archivo en disco no contiene los
bytes de la imagen en claro).

## R46
El sistema NO DEBE incluir la contraseña, el número de documento, la razón social, la dirección, el teléfono, el nombre,
el correo nuevo completo ni el token en el detalle de la auditoría, en los logs ni en el `toString()` de los DTO de
entrada; el enlace de R26 solo se escribe en el log con `espacioya.correo.simulado.registrar-enlace=true` y nunca junto
al correo.

## Auditoría (STRIDE R/T, RNF-SEG-12)

## R47
CUANDO el sistema guarda datos básicos que cambiaron, el sistema DEBE auditar `PERFIL_ACTUALIZADO` sobre `usuario` con
usuario, IP y en el detalle solo los nombres de los campos cambiados (`campos=nombre,telefono`).

## R48
CUANDO el sistema registra o quita una foto, el sistema DEBE auditar `FOTO_PERFIL_ACTUALIZADA` (con tipo y tamaño) o
`FOTO_PERFIL_ELIMINADA` sobre `usuario` con usuario e IP.

## R49
CUANDO el sistema acepta una solicitud de cambio de correo, el sistema DEBE auditar `CORREO_CAMBIO_SOLICITADO` (R25) o
`CORREO_CAMBIO_NO_EMITIDO` con `motivo=CORREO_EN_USO` (R29), con usuario, IP y el correo nuevo enmascarado.

## R50
CUANDO se confirma o se rechaza una confirmación de correo, el sistema DEBE auditar `CORREO_CAMBIADO` (con el correo
anterior y el nuevo enmascarados) o `CORREO_CAMBIO_RECHAZADO` (con el motivo: `TOKEN_DESCONOCIDO`, `TOKEN_USADO`,
`TOKEN_EXPIRADO`, `CUENTA_NO_HABILITADA` o `CORREO_EN_USO`), con la IP y el usuario si se conoce.

## R51
CUANDO el sistema guarda facturación que cambió, o la rechaza por R42, el sistema DEBE auditar `FACTURACION_ACTUALIZADA`
(con los nombres de los campos cambiados y el estado de identidad anterior y resultante) o
`FACTURACION_DOCUMENTO_RECHAZADO` (`motivo=DOCUMENTO_EN_USO`), sobre `perfil_arrendador` con usuario e IP.

## Integridad y concurrencia (STRIDE T)

## R52
El sistema DEBE tomar, en toda escritura de perfil, el bloqueo de la fila de `usuario` antes que el de
`perfil_arrendador`, de modo que dos `PUT` de facturación simultáneos de la misma cuenta, o uno de facturación y la
decisión del admin sobre esa identidad, terminen sin error de integridad y el resultado final sea el de una ejecución en
serie.

## Trazabilidad con la historia

| Origen (`docs/historias/HU-20.md` y `docs/requerimientos.md`)                         | Cubierto por |
|---------------------------------------------------------------------------------------|--------------|
| CA1: autenticado, guardo datos válidos y los cambios se reflejan de inmediato         | R5, R8, R9, R10, R11, R12, R13, R14, R15, R16, R17, R36, R43 |
| CA2: el nuevo correo solo se activa tras verificarlo                                  | R22, R23, R24, R25, R26, R29, R30, R31, R32, R33, R34 |
| CA2: el correo anterior recibe un aviso                                               | R27, R28, R29 |
| CA3: arrendador verificado que cambia RUC/DNI vuelve a "en revisión"                  | R37, R38, R39, R40, R41, R42, R44 |
| (E) Solo se edita el perfil de la sesión                                              | R1, R2, R3, R4 |
| (S) Cambiar correo o facturación exige reingresar la contraseña                       | R18, R19, R20, R21 |
| (R/T) Registro de cambios sensibles                                                   | R19, R47, R48, R49, R50, R51 |
| RF-A-20 / RF-A-21 / RF-A-22                                                           | R8, R13, R36 / R25–R31 / R37 |
| RN-02 (cambio de RUC/DNI con identidad verificada → en revisión)                      | R37 (R38 lo extiende, ⚠ D9) |
| RNF-SEG-01 identidad de la sesión                                                     | R1, R4 |
| RNF-SEG-03 reautenticación para correo y facturación                                  | R18, R19, R20, R21 |
| RNF-SEG-08 archivos por tipo real, tamaño y sin metadatos                             | R13, R14 |
| RNF-SEG-12 auditoría de cambios de perfil                                             | R47–R51 |
| RNF-SEG-13 no confirmar si un correo existe                                           | R29, R32, R33, R42 |
| RNF-SEG-14 facturación cifrada                                                        | R6, R45 |
| RNF-SEG-16 minimización                                                               | R6, R16, R46 |
| RNF-SEG-17 rate limiting (límites nuevos, D6 y D18)                                   | R20, R24 |
| RNF-SEG-18 rol validado en el backend                                                 | R2, R3 |
| Integridad en BD (`uq_usuario_correo`, `uq_perfil_arrendador_hash_documento`)         | R33, R42, R52 |
| Cabeceras y caché                                                                     | R7, R16 |
