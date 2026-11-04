# HU-18 — Requirements: Registro de cuenta bancaria del arrendador

> Fuente: `docs/historias/HU-18.md` (CA1–CA3, STRIDE S/T, I, R), `docs/requerimientos.md` (RF-E-07…09,
> RNF-SEG-03, 12, 14, relacionados RNF-SEG-01, 13, 16, 18, 19, RN-01, RN-11, §8 auditoría, correo y cifrado de
> campos), `docs/architecture.md` (módulo `pagos`, dirección `pagos → cuentas`, tabla de estados "Identidad
> verificada: … registrar cuenta bancaria"), `docs/modelo-datos.md` (`cuenta_bancaria`), `specs/HU-02/` (cifrado de
> campos), `specs/HU-06/` (identidad verificada), `specs/HU-17/` (patrón de reautenticación con contraseña y límite de
> intentos) y el código existente de `comun/` y `cuentas/`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la presentación está en
> `design.md` § 11 Pantallas. Las decisiones técnicas están en `design.md` § 1.

## Preguntas abiertas y supuestos a confirmar por el dueño (Ariel Enriquez)

> ⚠ Las fuentes no fijan estos puntos. El spec adopta el valor por defecto indicado para poder redactar; si el
> dueño no lo acepta al aprobar, cambian los `R<n>` citados **antes** de pasar a `in_progress`. Ninguno es un
> pendiente de `docs/requerimientos.md` §9.

| # | Pregunta | Valor por defecto del spec | Afecta |
|---|----------|----------------------------|--------|
| P1 | "A mi nombre o al de mi RUC" (CA1): ¿cómo se comprueba la titularidad? No hay forma de validar con el banco. | El titular **no se escribe**: el servidor lo toma de `perfil_arrendador.razon_social` (que para DNI es el nombre y para RUC la razón social) y el arrendador debe marcar una declaración (`declaraTitularidad = true`) de que la cuenta está a ese nombre o a su RUC. | R9, R10, R11 |
| P2 | ¿La contraseña (RNF-SEG-03) se pide también al **registrar por primera vez** o solo al cambiar? CA2 y STRIDE hablan solo de cambios. | Se pide en **ambos** (una sesión robada podría registrar una cuenta propia y recibir los pagos). El **correo de aviso y la retención de 72 h** son solo del **cambio** (RN-11: "tras cambiar"). | R6, R12, R14, R17 |
| P3 | Límite de intentos de la contraseña de reautenticación: no está en RNF-SEG-17 ni en §9. | Se reutiliza el de HU-03: **5 fallos en 15 min por cuenta** (ventana deslizante, `LimitadorIntentos`); al superarlo, 429 con el mensaje genérico. Un acierto reinicia el contador. | R8 |
| P4 | Formato del banco y del número de cuenta. | `banco`: texto libre de 2 a 60 caracteres (sin lista cerrada). `numero`: solo dígitos, de 10 a 20 (cubre cuentas locales y CCI de 20), sin validar dígito de control. | R10 |
| P5 | Enviar la misma cuenta que ya está registrada. | 400 *ya registrada*, sin retención ni correo (evita reiniciar las 72 h sin cambio real). | R16 |
| P6 | Un cambio mientras ya hay una retención en curso. | Cada cambio reinicia la retención: `retener_hasta = ahora + 72 h`. | R12 |
| P7 | ¿Una o varias cuentas por arrendador? La historia dice "registrar y cambiar **la** cuenta". | **Una sola cuenta vigente** por arrendador; el cambio la reemplaza y el historial queda en la auditoría (con datos enmascarados). | R12, R22, R25 |
| P8 | HU-19 (generar liquidación) necesitará el número **completo** para que el admin transfiera a mano. | HU-18 **no** expone el número completo en ninguna ruta. Cómo lo ve el admin lo decide HU-19 (Eduardo Saldaña). HU-18 solo deja la columna cifrada y `retener_hasta` legible. | R19 |
| P9 | Si la identidad deja de estar `VERIFICADA` después de registrar la cuenta (HU-20, RN-02), ¿qué pasa con la cuenta? | HU-18 no la borra ni la bloquea: solo impide **registrar o cambiar** sin identidad verificada. Si las liquidaciones se pausan lo decide HU-19/HU-20. | R3 |

## Definiciones

- **Arrendador:** cuenta con `usuario.rol = ARRENDADOR` y sesión vigente (HU-03).
- **Identidad verificada:** `perfil_arrendador.estado_identidad = VERIFICADA` (HU-06), leída con
  `CuentaService.habilitacionArrendador`.
- **Cuenta bancaria vigente:** la única fila de `cuenta_bancaria` del arrendador (P7).
- **Registro:** guardar la primera cuenta. **Cambio:** guardar una cuenta distinta a la vigente (otro `banco` u otro
  `numero`).
- **Número enmascarado:** `****` seguido de los 4 últimos dígitos del número (p. ej. `****1234`).
- **Retención:** `cuenta_bancaria.retener_hasta` posterior al instante de la lectura. La siguiente liquidación del
  arrendador no se paga hasta pasar esa fecha (RN-11; la aplica HU-19).
- **Estado de la cuenta:** `RETENIDA` si hay retención; `DISPONIBLE` si no (la calcula el servidor al leer, con el
  reloj).
- **Contraseña correcta:** la que coincide con el hash de la cuenta de la sesión (bcrypt, HU-01).
- **Mensajes** (texto exacto en `design.md` § 4).

## Acceso y roles (STRIDE S/E)

## R1
SI una solicitud a cualquier ruta de `/api/arrendadores/actual/cuenta-bancaria` llega sin sesión vigente ENTONCES el
sistema DEBE responder 401 sin leer ni escribir `cuenta_bancaria`.

## R2
SI una sesión vigente cuyo rol no es `ARRENDADOR` envía una solicitud válida a cualquier ruta de
`/api/arrendadores/actual/cuenta-bancaria` ENTONCES el sistema DEBE responder 403 sin leer ni escribir
`cuenta_bancaria` y sin comprobar la contraseña.

## R3
SI un arrendador cuya identidad no está verificada envía `PUT /api/arrendadores/actual/cuenta-bancaria` ENTONCES el
sistema DEBE rechazarlo con 400 y el mensaje de identidad no verificada, sin comprobar la contraseña, sin contar un
intento fallido y sin crear ni modificar ninguna cuenta (CA3).

## R4
El sistema DEBE tomar la identidad del arrendador siempre de la sesión: ninguna ruta de cuenta bancaria acepta un id
de arrendador en la URL ni en el cuerpo, y el arrendador solo lee y modifica su propia cuenta.

## Consulta (CA1, STRIDE I)

## R5
CUANDO un arrendador envía `GET /api/arrendadores/actual/cuenta-bancaria`, el sistema DEBE responder 200 con si su
identidad está verificada y su cuenta vigente (banco, número enmascarado, titular y estado), o con la cuenta nula si
no tiene ninguna.

## R6
CUANDO un arrendador envía `PUT /api/arrendadores/actual/cuenta-bancaria` sin `contrasena` o con `contrasena` en
blanco, el sistema DEBE rechazarlo con 400 y un error en el campo `contrasena`, sin comprobar nada más ni contar un
intento fallido (S/T).

## R7
SI un arrendador con identidad verificada envía `PUT /api/arrendadores/actual/cuenta-bancaria` con una `contrasena`
incorrecta ENTONCES el sistema DEBE rechazarlo con 400 y un error en el campo `contrasena`, sin crear ni modificar
ninguna cuenta, contar el intento fallido y auditar `CUENTA_BANCARIA_CONTRASENA_FALLIDA` con usuario e IP (S/T, R).

## R8
SI el arrendador acumuló 5 contraseñas incorrectas en cuenta bancaria en los últimos 15 minutos ENTONCES el sistema
DEBE responder 429 con el mensaje genérico de demasiados intentos a cualquier `PUT` siguiente, sin comprobar la
contraseña ni modificar nada, hasta que venza la ventana (⚠ P3).

## R9
CUANDO el arrendador envía una `contrasena` correcta, el sistema DEBE reiniciar su contador de contraseñas
incorrectas de cuenta bancaria.

## Registro y cambio (CA1, CA2)

## R10
SI el cuerpo del `PUT` trae un `banco` en blanco o de menos de 2 o más de 60 caracteres, un `numero` que no son de
10 a 20 dígitos, o `declaraTitularidad` distinto de `true` ENTONCES el sistema DEBE rechazarlo con 400 y un error por
cada campo inválido, sin comprobar la contraseña ni guardar nada (⚠ P1, P4).

## R11
CUANDO un arrendador con identidad verificada y sin cuenta vigente envía `PUT /api/arrendadores/actual/cuenta-bancaria`
con datos válidos y su contraseña correcta, el sistema DEBE guardar la cuenta con el titular tomado de la
razón social de su perfil, sin retención, y responder 200 con la cuenta enmascarada y estado `DISPONIBLE` (CA1, ⚠ P1,
P2).

## R12
CUANDO un arrendador con identidad verificada y con cuenta vigente envía `PUT /api/arrendadores/actual/cuenta-bancaria`
con datos válidos, distintos de la vigente, y su contraseña correcta, el sistema DEBE reemplazar los datos de la
cuenta y fijar `retener_hasta` en el instante actual más 72 horas, y responder 200 con estado `RETENIDA` (CA2, RN-11,
⚠ P6, P7).

## R13
CUANDO el sistema cambia la cuenta de un arrendador (R12) y la transacción se confirma, el sistema DEBE enviar un
correo de aviso al correo de la cuenta del arrendador con el banco, los 4 últimos dígitos y la fecha hasta la cual
queda retenida la liquidación (CA2, S/T).

## R14
El sistema NO DEBE enviar ningún correo de aviso cuando registra la primera cuenta de un arrendador ni cuando rechaza
una solicitud (⚠ P2).

## R15
SI el envío del correo de aviso falla ENTONCES el sistema DEBE mantener el cambio ya confirmado y responder 200, sin
propagar el fallo al arrendador; y SI la transacción del cambio se revierte ENTONCES el sistema NO DEBE enviar el
correo.

## R16
SI el `PUT` trae el mismo `banco` y el mismo `numero` que la cuenta vigente ENTONCES el sistema DEBE rechazarlo con 400
(*ya registrada*), sin modificar `retener_hasta`, sin auditar un cambio y sin enviar correo (⚠ P5).

## R17
CUANDO un arrendador registra su primera cuenta (R11), el sistema DEBE dejar `retener_hasta` nulo (⚠ P2).

## Retención (CA2)

## R18
CUANDO un arrendador consulta su cuenta, el sistema DEBE responder estado `RETENIDA` con `retenidaHasta` mientras el
reloj esté antes de `retener_hasta`, y `DISPONIBLE` sin `retenidaHasta` desde ese instante, sin ninguna escritura.

## Datos sensibles (STRIDE I)

## R19
El sistema DEBE devolver el número de cuenta solo enmascarado (`****` y los 4 últimos dígitos) en toda respuesta de
la API, y NO DEBE devolver nunca el número completo, la contraseña ni la huella de ninguno (⚠ P8).

## R20
El sistema DEBE guardar `cuenta_bancaria.numero` cifrado en reposo con el cifrado de campos de `comun/cifrado`
(AES-256-GCM, prefijo `v1:`), de modo que el valor de la columna no contenga el número en claro.

## R21
El sistema DEBE responder todas las rutas de cuenta bancaria con `Cache-Control: no-store`.

## R22
El sistema NO DEBE incluir el número completo ni la contraseña en el detalle de la auditoría, en el cuerpo del correo,
en los logs ni en la representación `toString()` de los DTO de entrada y salida.

## Auditoría (STRIDE R)

## R23
CUANDO el sistema registra la primera cuenta, el sistema DEBE auditar `CUENTA_BANCARIA_REGISTRADA` sobre la entidad
`cuenta_bancaria` con el usuario, la IP y, en el detalle, el banco y el número enmascarado.

## R24
CUANDO el sistema cambia la cuenta (R12), el sistema DEBE auditar `CUENTA_BANCARIA_CAMBIADA` en la misma transacción
con el usuario, la IP y, en el detalle, el banco y el número enmascarado anteriores y nuevos y el `retener_hasta`
resultante.

## Integridad y concurrencia (STRIDE T)

## R25
El sistema DEBE impedir en la base de datos que un arrendador tenga más de una fila en `cuenta_bancaria`
(`uq_cuenta_bancaria_arrendador`) y que `numero` o `banco` queden vacíos, y DEBE serializar dos `PUT` simultáneos del
mismo arrendador de modo que ambos terminen sin error de integridad y la cuenta final sea la del último en confirmar
(⚠ P7).

## Trazabilidad con la historia

| Origen (`docs/historias/HU-18.md` y `docs/requerimientos.md`)              | Cubierto por |
|----------------------------------------------------------------------------|--------------|
| CA1: con identidad verificada registro una cuenta a mi nombre o RUC y queda disponible para liquidaciones | R5, R10, R11, R17, R18, R20 |
| CA2: al cambiarla confirmo con contraseña, recibo correo y la siguiente liquidación se retiene 72 h | R6, R7, R12, R13, R14, R15, R16, R18 |
| CA3: sin identidad verificada el sistema no lo permite                     | R3 |
| (S/T) reautenticación con contraseña                                       | R6, R7, R8, R9 |
| (S/T) aviso por correo ante cambios                                        | R13, R14, R15 |
| (S/T) retención de 72 h ante cambios                                       | R12, R17, R18 |
| (I) datos bancarios cifrados                                               | R20 |
| (I) datos bancarios mostrados enmascarados                                 | R5, R19, R21, R22 |
| (R) registro de cada cambio de cuenta                                      | R7, R23, R24 |
| RNF-SEG-01 identidad de la sesión                                          | R1, R4 |
| RNF-SEG-18 rol validado en el backend                                      | R2 |
| RNF-SEG-19 propiedad del recurso                                           | R4 |
| RF-E-07 / RF-E-08 / RF-E-09 (CA1 / CA2 / CA3)                              | R11 / R12, R13 / R3 |
| RN-11 retención de 72 h (la aplicación en la liquidación es de HU-19)      | R12, R18 |
| Integridad en BD (una cuenta por arrendador)                               | R25 |
