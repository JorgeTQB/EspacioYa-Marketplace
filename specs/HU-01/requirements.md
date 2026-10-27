# HU-01 — Requirements: Registro de usuario con consentimiento

> Fuente: `docs/historias/HU-01.md` (CA1–CA3, STRIDE T/I/D/R), `docs/requerimientos.md`
> (RF-A-01…03, RNF-SEG-05, 12, 13, 17, RNF-CUM-03, RNF-USA-03, §8, §9.5 y §9.7 resueltos)
> y las respuestas del dueño en `progress/HU-01.md` (2026-10-27).
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la
> presentación está en `design.md` § Pantallas.

## Definiciones

- **Versión vigente:** valor de la propiedad `espacioya.privacidad.version-vigente`
  (hoy `2026-10`).
- **Política de contraseña** (§9.5): de 8 a 64 caracteres, al menos una letra y al
  menos un dígito (`0`–`9`), y distinta del correo (sin distinguir mayúsculas).
- **Correo normalizado:** el correo sin espacios a los extremos y en minúsculas.
- **Mensaje de registro recibido:** `"Te enviamos un correo de verificación. Revisa tu
  bandeja de entrada."`
- **Requisitos de contraseña:** `"La contraseña debe tener entre 8 y 64 caracteres,
  al menos una letra y un número, y no puede ser igual a tu correo."`
- **Intento de registro:** toda solicitud `POST /api/usuarios` que llega al servidor
  y no es rechazada por el propio límite (R21), sea válida o no.
- **Notificador de correo:** el puerto `NotificadorCorreo` del módulo `cuentas`. En
  HU-01 su única implementación es simulada (supuesto S5).

## Supuestos a confirmar por el dueño al aprobar

> ⚠ S1–S4 **no** los decidió el dueño de forma explícita. Si alguno no se acepta, se
> ajustan los `R<n>` indicados antes de pasar a `in_progress`. S5 es una decisión del
> dueño y se deja aquí para que quede escrita.

| # | Supuesto | Afecta |
|---|----------|--------|
| S1 | bcrypt (Spring Security) no acepta contraseñas de más de **72 bytes** en UTF-8. Una contraseña de ≤ 64 caracteres con muchas tildes o emojis puede pasarse; se rechaza con el mismo mensaje de requisitos. No cambia nada para contraseñas ASCII. | R10 |
| S2 | Una solicitud rechazada con 429 **no** cuenta como intento (si contara, quien insiste quedaría bloqueado para siempre). | R21, R22 |
| S3 | El texto legal de la política de privacidad y de los términos lo provee el equipo; HU-01 solo agrega la página `/privacidad` que lo muestra con su versión. | Pantalla Privacidad |
| S4 | Se agrega la dependencia `react-router-dom` (frontend, ya prevista en `docs/frontend.md`). El backend no agrega dependencias. | Pantallas |
| S5 | **Decisión del dueño (2026-10-27):** el envío real del correo de verificación queda **fuera del alcance de HU-01**. El envío se simula: el registro llama al notificador de correo, cuya implementación no envía nada, y la API devuelve el mensaje de registro recibido. La cuenta queda con `correo_verificado = true` al registrarse, sin token ni enlace de verificación, para que HU-03 pueda iniciar sesión sin ese paso. CA1 ("recibo un correo de verificación") se da por cubierto con el envío simulado y el mensaje en pantalla. El adaptador real lo conecta HU-25. | R1, R2, R3, R4, R20 |

## Registro exitoso (CA1)

## R1
CUANDO un visitante envía `POST /api/usuarios` con un correo válido que no está
registrado, una contraseña que cumple la política, `aceptaPrivacidad = true` y
`versionPolitica` igual a la versión vigente, el sistema DEBE crear un usuario con
rol `USUARIO`, estado `ACTIVA` y `correo_verificado = true` (S5).

## R2
CUANDO el sistema crea la cuenta según R1, el sistema DEBE responder `202 Accepted`
con un cuerpo que contenga solo el mensaje de registro recibido (sin id, correo ni
otro dato de la cuenta).

## R3
CUANDO el sistema crea la cuenta según R1, el sistema DEBE invocar una sola vez la
notificación de verificación del notificador de correo con el correo normalizado.

## R4
La implementación del notificador de correo de HU-01 NO DEBE abrir conexiones a un
servidor de correo; DEBE solo escribir en el log, con nivel INFO, que el envío del
correo de verificación fue simulado, sin incluir el correo del usuario.

## R5
El sistema DEBE guardar el correo de la cuenta en su forma normalizada.

## Consentimiento (CA2, RNF-CUM-03, STRIDE R)

## R6
SI el visitante envía el registro con `aceptaPrivacidad` falso o ausente ENTONCES el
sistema DEBE responder 400 sin crear usuario ni invocar el notificador de correo.

## R7
SI el visitante envía el registro con `versionPolitica` ausente o distinta de la
versión vigente ENTONCES el sistema DEBE responder 400 sin crear usuario ni invocar el
notificador de correo.

## R8
CUANDO el sistema crea la cuenta según R1, el sistema DEBE guardar en el usuario la
versión de la política aceptada (`version_politica`) y la fecha y hora del
consentimiento (`fecha_consentimiento`) tomada del reloj del sistema.

## R9
CUANDO el sistema crea la cuenta según R1, el sistema DEBE registrar en la auditoría
la acción `REGISTRO_USUARIO` con el id del nuevo usuario, la IP de la solicitud, la
fecha y hora y la versión de la política aceptada en el detalle.

## Contraseña débil y formato (CA3, RNF-USA-03)

## R10
SI la contraseña tiene menos de 8 o más de 64 caracteres, o más de 72 bytes en UTF-8
ENTONCES el sistema DEBE responder 400 con los requisitos de contraseña asociados al
campo `contrasena`, sin crear usuario.

## R11
SI la contraseña no contiene al menos una letra o no contiene al menos un dígito
ENTONCES el sistema DEBE responder 400 con los requisitos de contraseña asociados al
campo `contrasena`, sin crear usuario.

## R12
SI la contraseña es igual al correo normalizado sin distinguir mayúsculas ENTONCES el
sistema DEBE responder 400 con los requisitos de contraseña asociados al campo
`contrasena`, sin crear usuario.

## R13
SI el correo falta, no tiene formato de correo o supera 254 caracteres ENTONCES el
sistema DEBE responder 400 con un error asociado al campo `correo`, sin crear usuario.

## Correo ya existente (CA3, STRIDE I)

## R14
CUANDO el registro es válido según R6, R7 y R10–R13 pero el correo normalizado ya pertenece a
una cuenta, el sistema DEBE responder con el mismo código y el mismo cuerpo que R2.

## R15
CUANDO el registro trae un correo que ya pertenece a una cuenta, el sistema NO DEBE
crear un usuario, modificar la cuenta existente ni invocar el notificador de correo.

## R16
CUANDO el registro trae un correo que ya pertenece a una cuenta, el sistema DEBE
registrar en la auditoría la acción `REGISTRO_CORREO_EXISTENTE` con el id de la cuenta
existente, la IP y la fecha y hora.

## R17
El sistema DEBE aplicar las validaciones de R6, R7 y R10–R13 antes de consultar si el correo
existe, de modo que un registro inválido con correo existente reciba la misma
respuesta que el mismo registro con un correo nuevo.

## R18
SI dos solicitudes de registro válidas con el mismo correo normalizado llegan a la
vez ENTONCES el sistema DEBE crear una sola cuenta y responder a ambas como en R2.

## Hash de contraseña (STRIDE T, RNF-SEG-05)

## R19
El sistema DEBE guardar la contraseña solo como hash bcrypt con sal (factor de costo
12), nunca en claro.

## Rol asignado en el servidor (STRIDE E transversal)

## R20
El sistema DEBE asignar rol `USUARIO`, estado `ACTIVA` y `correo_verificado = true`
aunque el cuerpo de la solicitud incluya campos `rol`, `estado` o `correoVerificado`.

## Límite de registros por IP (STRIDE D, RNF-SEG-17, §9.7)

## R21
SI una IP ya hizo 5 intentos de registro en los últimos 60 minutos ENTONCES el
sistema DEBE responder 429 con el mensaje genérico `"Hiciste demasiados intentos.
Espera un momento e inténtalo de nuevo."` sin procesar la solicitud.

## R22
El sistema DEBE contar como intento de registro toda solicitud que llega al límite,
incluidas las rechazadas con 400 y las de correo existente.

## R23
CUANDO han pasado 60 minutos desde el intento más antiguo dentro de la ventana de una
IP bloqueada, el sistema DEBE aceptar un nuevo intento de esa IP.

## Errores genéricos (STRIDE I, RNF-SEG-13)

## R24
SI ocurre un error no previsto al procesar una solicitud ENTONCES el sistema DEBE
responder 500 con un `ErrorDTO` de mensaje genérico, sin traza, nombre de excepción
ni detalle técnico.

## R25
SI el notificador de correo lanza una excepción al notificar la verificación ENTONCES
el sistema DEBE responder 500 con el mismo `ErrorDTO` genérico de R24 sin dejar
guardado el usuario de esa solicitud.

## Versión de la política (CA2)

## R26
CUANDO un visitante consulta `GET /api/politicas-privacidad/vigente`, el sistema DEBE
responder 200 con la versión vigente.

## Trazabilidad con la historia

| Origen (`docs/historias/HU-01.md` y fuentes)                         | Cubierto por |
|----------------------------------------------------------------------|--------------|
| CA1: cuenta creada con datos válidos                                 | R1, R2, R5, R8 |
| CA1: recibo un correo de verificación (envío simulado, S5)           | R2, R3, R4, R25 |
| CA2: sin aceptación de privacidad no se completa                     | R6, R7, R26 |
| CA3: contraseña débil → requisitos mínimos                           | R10, R11, R12, R13 |
| CA3: correo existente → mensaje genérico sin revelar la cuenta       | R14, R15, R17, R18 |
| (T) Contraseñas con hash seguro con sal                              | R19 |
| (I) Errores que no confirman si el correo existe                     | R14, R17, R18, R24, R25 |
| (D) Límite de registros por IP por hora (5/h, ventana deslizante)    | R21, R22, R23 |
| (R) Fecha, hora, IP y versión de la política aceptada                | R8, R9, R16 |
| RF-A-01 / RF-A-02 / RF-A-03                                          | R1–R4 / R6, R7 / R10–R17 |
| RNF-SEG-05 · 12 · 13 · 17 · CUM-03                                   | R19 · R9, R16 · R24, R14 · R21–R23 · R8, R9 |
| (E) transversal: rol asignado solo en el servidor                    | R20 |
