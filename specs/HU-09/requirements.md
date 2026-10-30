# HU-09 — Requirements: Suspensión y reactivación de cuentas

> Fuente: `docs/historias/HU-09.md` (CA1–CA3, STRIDE E/S/R), `docs/requerimientos.md` (RF-F-04…06,
> RNF-SEG-02, 04, 12, 18, relacionados RNF-SEG-13 y RNF-USA-04, RN-01, RN-15, §8 sesión, auditoría y
> correo, §9-4 resuelto), `docs/architecture.md` (`administracion → (todos)`, `espacios → cuentas`,
> "si el arrendador está suspendido, sus espacios no se muestran pero conservan su estado"),
> `specs/HU-06/` (panel admin, filtro de IPs, R7–R9, R41), `specs/HU-03/` (R9, R12, R22–R26),
> `specs/HU-07/` (R39, R55), `specs/HU-08/` (R30, R31) y `progress/explore_HU-09.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la presentación
> está en `design.md` § 10 Pantallas y las decisiones técnicas en `design.md` § 1.

## Decisiones del dueño (Jorge Quispe, 2026-10-29)

Tomadas sobre los puntos 2, 4, 6 y 9 del informe del explorador (`progress/HU-09.md`). No llevan ⚠.

| # | Decisión | Afecta |
|---|----------|--------|
| D1 | Los espacios del arrendador suspendido se ocultan **consultando `cuentas` al leer**: métodos nuevos de `CuentaService` (`cuentaActiva(id)` y `arrendadoresNoActivos()`) que no exigen rol porque los usan rutas públicas; `espacios` los usa en `FotoEspacioService.ver` y `DisponibilidadService.horasLibres`. Sin columna nueva en `espacio`; `espacio.estado` no cambia. HU-10, HU-11 y HU-12 filtran con esos métodos (se documenta en `docs/architecture.md`). | R22–R28 |
| D2 | Solo se suspenden o reactivan cuentas `USUARIO` y `ARRENDADOR`. Una cuenta `ADMIN` nunca, tampoco la propia. Una cuenta `ELIMINADA` no se suspende ni se reactiva. | R9, R14, R29, R35 |
| D3 | Al suspender se revocan **todas** las sesiones de la cuenta **y** `SesionService.validar` rechaza las sesiones de cuentas con estado distinto de `ACTIVA` (cambio en HU-03). | R16, R21 |
| D4 | También se envía un correo **al reactivar**, con el mismo patrón que HU-06: después del commit y sin el correo ni el motivo en el log. | R32, R39, R47 |

## Supuestos a confirmar por el dueño al aprobar

> ⚠ Las fuentes no fijan estos puntos. El spec adopta la recomendación del explorador; si
> **Jorge Quispe** no la acepta, cambian los requirements citados antes de pasar a `in_progress`.

| # | Supuesto | Afecta |
|---|----------|--------|
| S1 | (punto 1) Rutas bajo `/api/admin/cuentas`: `GET ?q=` (búsqueda), `GET /{id}` (detalle), `POST /{id}/suspension` (cuerpo `{motivo}`) y `POST /{id}/reactivacion` (sin cuerpo); los dos `POST` responden `201` porque crean una fila de historial. El texto buscado viaja en el query string: hoy no hay access log en Tomcat y se acepta. | R1, R7, R14, R29 |
| S2 | (punto 3) Tabla nueva **`suspension_cuenta`** en `administracion` (historial: cuenta, admin, acción, motivo, fecha), calcada de `revision_identidad`: motivo de hasta 500 caracteres, obligatorio solo en `SUSPENSION`; la reactivación no lleva motivo. `usuario_id` en cascada (las IT borran usuarios), `admin_id` sin cascada. El estado vigente sigue en `usuario.estado` y lo cambia `cuentas`. Cambio de estado, historial y auditoría van en **una transacción**. | R10, R17, R31, R43 |
| S3 | (punto 5) La fila de `usuario` se bloquea (`PESSIMISTIC_WRITE`) antes de suspender o reactivar: la segunda solicitud simultánea espera, ve el estado nuevo y recibe el 400 correspondiente. | R36–R38 |
| S4 | (punto 7) Búsqueda por **"contiene"** sin distinguir mayúsculas sobre `correo` y `nombre` (la razón social va cifrada y no se busca); sin insensibilidad a tildes (no hay `unaccent`). Texto recortado de 3 a 100 caracteres. **Máximo 20 resultados** ordenados por correo, sin paginación (como HU-06 S6), con `hayMas` para pedir afinar. | R1–R4, R6 |
| S5 | (punto 7) El detalle muestra solo datos de la cuenta (`nombre`, `correo`, `telefono`, `rol`, `estado`, `correoVerificado`, `estadoIdentidad`) y, si está suspendida, el motivo y la fecha de la última suspensión. Nunca número de documento, razón social, dirección fiscal ni documentos (minimización). El servidor calcula `puedeSuspender` y `puedeReactivar` para que la pantalla no decida reglas. | R7–R11 |
| S6 | (punto 7) Las cuentas `ADMIN` y `ELIMINADA` **sí aparecen** en la búsqueda y el detalle (para ver su estado), sin acciones. Suspender o reactivar una cuenta no gestionable → 400 *no gestionable*; una cuenta inexistente → 404. | R5, R9, R34, R35 |
| S7 | (punto 7) La búsqueda y el detalle **no se auditan** (consultas, como la cola de HU-06). | R44, R45 |
| S8 | (punto 8) Orden: `administracion` abre la transacción → `CuentaService` (rol otra vez, bloqueo, cambio de estado, revocación de sesiones, correo `afterCommit`) → fila de historial → auditoría. El detalle de la auditoría es `accion=<ACCION>;registro=<id>` (más `sesionesRevocadas=<n>` al suspender): el motivo vive solo en `suspension_cuenta` (como HU-06 S16). | R14–R18, R44–R46 |
| S9 | (punto 10) La suspensión **no toca reservas** (aún no existen). Qué pasa con las reservas de un arrendador o un usuario suspendido lo deciden HU-12 y HU-15; HU-12 hereda el filtro si usa `horasLibres` o `cuentaActiva`. | — |
| S10 | (punto 11) Los tokens de recuperación pendientes no se anulan (HU-04 ya los rechaza para cuentas no `ACTIVA`); el limitador de inicio de sesión no cambia; la cola de HU-06 excluye sola al suspendido y lo vuelve a mostrar al reactivarlo si seguía `EN_REVISION`. Sin código. | — |
| S11 | (punto 12) La comprobación del rol `ADMIN` se **duplica** en el servicio nuevo de `administracion` (opción a), sin refactorizar HU-06. | R40, R41 |
| S12 | (punto 13) Migración **`V3_1__suspension_cuenta.sql`**, que solo depende de `V1_1`; si `develop` ya tiene `V3_1` al unir (HU-10, HU-11 o HU-12), se renombra a la siguiente libre. | R43 |
| S13 | (punto 14) Frontend: páginas `BuscarCuentas.jsx` y `DetalleCuenta.jsx` en `pages/administracion/`, **sin componentes nuevos** en el kit; `EtiquetaEstado` suma los estados de cuenta (ya están en la tabla de colores). | — (Pantallas) |
| S14 | (punto 15) Mientras no existan la búsqueda y la ficha públicas (HU-10, HU-11), "sus espacios se ocultan / vuelven a ser visibles" se prueba sobre `GET /api/fotos/{uuid}` y `DisponibilidadService.horasLibres`, más una prueba de que `espacio.estado` no cambia. El catálogo y la ficha lo heredan al filtrar con `arrendadoresNoActivos()` / `cuentaActiva()` (D1). | R22–R28 |
| S15 | (punto 6) Una foto pública ya guardada en caché por un navegador o un intermediario puede seguir viéndose hasta 1 hora (`max-age=3600` de HU-07); no se cambia ese tiempo. | R22 |

## Definiciones

- **Admin con acceso:** como en HU-06: admin con sesión vigente cuya solicitud llega desde una IP
  permitida.
- **Cuenta gestionable:** cuenta con rol `USUARIO` o `ARRENDADOR` y `estado` distinto de
  `ELIMINADA` (D2).
- **Texto de búsqueda válido:** valor de `q` que, tras recortar espacios al inicio y al final, mide
  de 3 a 100 caracteres (⚠ S4).
- **Cuenta que coincide:** cuenta cuyo `correo`, o cuyo `nombre` en minúsculas, contiene el texto de
  búsqueda recortado y en minúsculas (⚠ S4).
- **Motivo válido:** texto que, tras recortar espacios al inicio y al final, mide de 1 a 500
  caracteres.
- **Arrendador no activo:** cuenta con rol `ARRENDADOR` y `estado` distinto de `ACTIVA`.
- **Suspensión / reactivación:** `POST /api/admin/cuentas/{id}/suspension` /
  `POST /api/admin/cuentas/{id}/reactivacion`.
- **Mensajes** (texto exacto en `design.md` § 4): *sesión inválida* y *credenciales inválidas*
  (HU-03), *acceso denegado* y *recurso no encontrado* (HU-05), *búsqueda*, *motivo de suspensión*,
  *no gestionable*, *ya suspendida*, *ya activa*.

## Búsqueda de cuentas (CA1, RF-F-04)

## R1
CUANDO un admin con acceso envía `GET /api/admin/cuentas?q=<texto>` con un texto de búsqueda
válido, el sistema DEBE responder 200 con `cuentas`, una por cuenta que coincide, cada una con solo
`id`, `nombre`, `correo`, `rol` y `estado`, y con `hayMas` (⚠ S1, S4).

## R2
El sistema DEBE incluir en `cuentas` de R1 como máximo 20 cuentas, ordenadas por `correo`
ascendente.

## R3
El sistema DEBE responder en R1 `hayMas = true` si más de 20 cuentas coinciden y `hayMas = false`
en otro caso.

## R4
El sistema DEBE tratar los caracteres `%`, `_` y `\` del texto de búsqueda como caracteres
literales al buscar las cuentas que coinciden.

## R5
El sistema DEBE incluir en `cuentas` de R1 las cuentas que coinciden de cualquier rol y estado,
incluidas las `ADMIN` y las `ELIMINADA` (⚠ S6).

## R6
SI `q` falta o no es un texto de búsqueda válido ENTONCES el sistema DEBE responder 400 con un error
en el campo `q` y el mensaje *búsqueda*, sin consultar las cuentas.

## Detalle de una cuenta (CA1, STRIDE I)

## R7
CUANDO un admin con acceso envía `GET /api/admin/cuentas/{id}` de una cuenta existente, el sistema
DEBE responder 200 con solo `cuenta` (`id`, `nombre`, `correo`, `telefono`, `rol`, `estado`,
`correoVerificado`, `estadoIdentidad`, `puedeSuspender`, `puedeReactivar`) y `suspension` (⚠ S5).

## R8
El sistema DEBE responder en `estadoIdentidad` de R7 el `estado_identidad` del perfil si la cuenta
es `ARRENDADOR`, y nulo en otro caso.

## R9
El sistema DEBE responder en R7 `puedeSuspender = true` solo si la cuenta es gestionable y está
`ACTIVA`, y `puedeReactivar = true` solo si es gestionable y está `SUSPENDIDA` (D2).

## R10
MIENTRAS la cuenta de R7 está `SUSPENDIDA`, el sistema DEBE responder en `suspension` solo el
`motivo` y la `fecha` de su fila `SUSPENSION` más reciente de `suspension_cuenta` (⚠ S2).

## R11
MIENTRAS la cuenta de R7 no está `SUSPENDIDA`, el sistema DEBE responder `suspension` nulo.

## R12
SI el `{id}` de `GET /api/admin/cuentas/{id}` no corresponde a ninguna cuenta ENTONCES el sistema
DEBE responder 404 con el mensaje *recurso no encontrado*.

## R13
CUANDO el sistema responde 200 según R1 o R7, el sistema DEBE incluir un encabezado
`Cache-Control` que contenga `no-store`.

## Suspensión (CA2, RF-F-05, RNF-SEG-02, RN-01)

## R14
CUANDO un admin con acceso envía la suspensión con un `motivo` válido de una cuenta gestionable en
`ACTIVA`, el sistema DEBE responder `201 Created` con solo `accion = "SUSPENSION"`,
`estado = "SUSPENDIDA"` y `fecha` (D2, ⚠ S1).

## R15
CUANDO el sistema responde según R14, el sistema DEBE guardar `usuario.estado = SUSPENDIDA` en esa
cuenta.

## R16
CUANDO el sistema responde según R14, el sistema DEBE marcar como revocadas todas las sesiones no
revocadas de esa cuenta (D3).

## R17
CUANDO el sistema responde según R14, el sistema DEBE guardar una fila de `suspension_cuenta` con
el id de la cuenta, `admin_id` igual al id del usuario de la sesión (aunque el cuerpo traiga otro),
`accion = SUSPENSION`, `motivo` igual al motivo recortado y `fecha` igual a la fecha y hora del
reloj del sistema (⚠ S2).

## R18
CUANDO el sistema responde según R14, el sistema DEBE invocar una sola vez, después de confirmar la
transacción, el aviso de cuenta suspendida del notificador de correo con el correo de la cuenta y
el motivo recortado (⚠ S8).

## R19
SI el cuerpo de la suspensión no trae `motivo`, o el motivo no es un motivo válido, ENTONCES el
sistema DEBE responder 400 con un error en el campo `motivo` y el mensaje *motivo de suspensión*,
sin cambiar la cuenta ni sus sesiones ni guardar una fila de `suspension_cuenta`.

## R20
SI una cuenta suspendida según R14 envía a `POST /api/sesiones` su correo y contraseña correctos
ENTONCES el sistema DEBE responder con el mismo código y el mismo cuerpo que HU-03 R9, sin crear
sesión (HU-03 R12, probado de punta a punta).

## R21
SI una solicitud a un endpoint protegido trae el token de una sesión no revocada y no vencida cuya
cuenta tiene `estado` distinto de `ACTIVA` ENTONCES el sistema DEBE responder 401 con el mensaje
*sesión inválida* sin renovar `expira_en` de esa sesión (D3).

## Espacios del arrendador no activo (CA2, CA3, RN-01, RN-15, D1)

## R22
SI una solicitud sin sesión vigente pide `GET /api/fotos/{uuid}` de una foto de un espacio
`PUBLICADO` cuyo arrendador no está `ACTIVA` ENTONCES el sistema DEBE responder 404 con el mensaje
*recurso no encontrado* (⚠ S14, S15).

## R23
CUANDO otro módulo invoca `DisponibilidadService.horasLibres(espacioId, fecha)` para un espacio
cuyo arrendador no está `ACTIVA`, el sistema DEBE devolver una lista vacía.

## R24
CUANDO el arrendador de un espacio `PUBLICADO` vuelve a `ACTIVA`, el sistema DEBE responder a
`GET /api/fotos/{uuid}` sin sesión de una foto de ese espacio según HU-07 R39.

## R25
CUANDO el arrendador de un espacio `PUBLICADO` vuelve a `ACTIVA`, el sistema DEBE devolver en
`DisponibilidadService.horasLibres` las horas `LIBRE` de ese espacio según HU-08 R30.

## R26
El sistema NO DEBE cambiar `espacio.estado` de ningún espacio al suspender o reactivar una cuenta.

## R27
CUANDO otro módulo invoca `CuentaService.cuentaActiva(usuarioId)`, el sistema DEBE devolver `true`
solo si existe una cuenta con ese id y `estado = ACTIVA`, sin exigir sesión ni rol (D1).

## R28
CUANDO otro módulo invoca `CuentaService.arrendadoresNoActivos()`, el sistema DEBE devolver los ids
de todos los arrendadores no activos, o un conjunto vacío si no hay, sin exigir sesión ni rol (D1).

## Reactivación (CA3, RF-F-06)

## R29
CUANDO un admin con acceso envía la reactivación de una cuenta gestionable en `SUSPENDIDA`, el
sistema DEBE responder `201 Created` con solo `accion = "REACTIVACION"`, `estado = "ACTIVA"` y
`fecha` (D2, ⚠ S1).

## R30
CUANDO el sistema responde según R29, el sistema DEBE guardar `usuario.estado = ACTIVA` en esa
cuenta.

## R31
CUANDO el sistema responde según R29, el sistema DEBE guardar una fila de `suspension_cuenta` con
el id de la cuenta, `admin_id` igual al id del usuario de la sesión, `accion = REACTIVACION`,
`motivo` nulo y `fecha` igual a la fecha y hora del reloj del sistema (⚠ S2).

## R32
CUANDO el sistema responde según R29, el sistema DEBE invocar una sola vez, después de confirmar la
transacción, el aviso de cuenta reactivada del notificador de correo con el correo de la cuenta
(D4).

## R33
CUANDO una cuenta reactivada según R29 envía a `POST /api/sesiones` su correo y contraseña
correctos, el sistema DEBE responder según HU-03 R1 (recupera el acceso).

## Estado, concurrencia y fallos (D2, STRIDE E)

## R34
SI el `{id}` de una suspensión o reactivación no corresponde a ninguna cuenta ENTONCES el sistema
DEBE responder 404 con el mensaje *recurso no encontrado*, sin guardar una fila de
`suspension_cuenta`.

## R35
SI el `{id}` de una suspensión o reactivación corresponde a una cuenta no gestionable (rol `ADMIN`,
incluida la del propio admin de la sesión, o `estado = ELIMINADA`) ENTONCES el sistema DEBE
responder 400 con el mensaje *no gestionable*, sin cambiar la cuenta ni sus sesiones ni guardar una
fila de `suspension_cuenta` (D2, ⚠ S6).

## R36
SI se envía la suspensión de una cuenta gestionable en `SUSPENDIDA` ENTONCES el sistema DEBE
responder 400 con el mensaje *ya suspendida*, sin guardar una fila de `suspension_cuenta` (⚠ S3).

## R37
SI se envía la reactivación de una cuenta gestionable en `ACTIVA` ENTONCES el sistema DEBE
responder 400 con el mensaje *ya activa*, sin guardar una fila de `suspension_cuenta` (⚠ S3).

## R38
CUANDO llegan a la vez dos suspensiones de la misma cuenta gestionable en `ACTIVA`, el sistema DEBE
aceptar solo una y responder a la otra según R36 (⚠ S3).

## R39
SI el sistema rechaza una suspensión o reactivación según R19, R34–R37, R41 o R42, o su transacción
se revierte, ENTONCES el sistema NO DEBE invocar el notificador de correo.

## R40
SI un método de `CuentaService` para el administrador (búsqueda, detalle, suspensión o
reactivación) se invoca con un usuario de sesión cuyo rol no es `ADMIN` ENTONCES el sistema DEBE
lanzar `AccesoDenegadoException` sin consultar los repositorios (defensa en profundidad, ⚠ S11).

## R41
SI una cuenta con rol distinto de `ADMIN`, con sesión vigente, envía desde una IP permitida una
solicitud con un cuerpo válido a `/api/admin/cuentas` o a sus subrutas ENTONCES el sistema DEBE
responder 403 con el mensaje *acceso denegado* (rol releído del backend en cada solicitud,
RNF-SEG-18).

## R42
SI una solicitud a `/api/admin/cuentas` o a sus subrutas llega desde una IP no permitida, con o sin
sesión vigente, ENTONCES el sistema DEBE responder 403 con el mensaje *acceso denegado* sin ejecutar
el controlador (filtro de HU-06 R7, RNF-SEG-04).

## Registro de cada acción (STRIDE R, RNF-SEG-12)

## R43
El sistema DEBE impedir en la base de datos una fila de `suspension_cuenta` con `accion` distinta
de `SUSPENSION` o `REACTIVACION`, una `SUSPENSION` sin motivo o con motivo en blanco, o una
`REACTIVACION` con motivo (⚠ S2, S12).

## R44
CUANDO el sistema responde según R14, el sistema DEBE registrar en la auditoría la acción
`CUENTA_SUSPENDIDA` con el id del admin, la entidad `usuario` con el id de la cuenta, la IP del
cliente, la fecha y hora y el detalle
`accion=SUSPENSION;registro=<id de la fila de R17>;sesionesRevocadas=<n>` (⚠ S7, S8).

## R45
CUANDO el sistema responde según R29, el sistema DEBE registrar en la auditoría la acción
`CUENTA_REACTIVADA` con el id del admin, la entidad `usuario` con el id de la cuenta, la IP del
cliente, la fecha y hora y el detalle `accion=REACTIVACION;registro=<id de la fila de R31>`
(⚠ S7, S8).

## R46
El sistema NO DEBE escribir en el detalle de las auditorías de R44 y R45 el motivo ni el correo de
la cuenta (⚠ S8).

## R47
CUANDO el adaptador simulado de correo recibe un aviso de cuenta suspendida o reactivada, el sistema
NO DEBE escribir en el log el correo de la cuenta ni el motivo (Ley 29733, D4).

## Reemplazos y compatibilidad

- **HU-03 R22 y R24** se acotan: una sesión vigente de una cuenta con `estado` distinto de `ACTIVA`
  ya no se procesa ni se renueva (R21, D3). HU-03 R12 no cambia (R20 lo prueba de punta a punta).
- **HU-07 R39** se acota: la foto de un espacio `PUBLICADO` solo es pública si su arrendador está
  `ACTIVA` (R22); si no, aplica HU-07 R54/R55 (solo su dueño, que no tiene sesión mientras está
  suspendido).
- **HU-08 R30/R31** se amplían: `horasLibres` también devuelve vacío si el arrendador no está
  `ACTIVA` (R23).
- **HU-06 R22, R37** no cambian: el suspendido sale de la cola y vuelve al reactivarlo (S10).

## Trazabilidad con la historia

| Origen (`docs/historias/HU-09.md` y fuentes)                                      | Cubierto por |
|-----------------------------------------------------------------------------------|--------------|
| CA1: busco una cuenta por correo o nombre                                          | R1, R2, R3, R4, R5, R6 (pantalla en design § 10) |
| CA1: … veo sus datos, rol y estado                                                 | R7, R8, R9, R10, R11, R12, R13 |
| CA2: suspendo con un motivo y confirmo                                             | R14, R15, R17, R19, R35, R36, R38, R43 |
| CA2: … sus sesiones se invalidan                                                   | R16, R21 |
| CA2: … no puede iniciar sesión                                                     | R20 |
| CA2: … sus espacios se ocultan del catálogo                                        | R22, R23, R26, R27, R28 (catálogo y ficha: HU-10/HU-11 filtran con R27/R28, S14) |
| CA2: … recibe un correo con el motivo                                              | R18, R39, R47 |
| CA3: reactivo una cuenta suspendida y confirmo                                     | R29, R30, R31, R37 |
| CA3: … recupera el acceso                                                          | R33 |
| CA3: … sus espacios vuelven a ser visibles                                         | R24, R25, R26 |
| (E) Rol admin validado en backend en cada solicitud                                | R35, R40, R41 |
| (S) Mismo control de IPs permitidas que HU-06 hasta HU-17                          | R42 |
| (R) Registro de cada acción (admin, cuenta, acción, motivo, fecha)                 | R17, R31, R43, R44, R45, R46 |
| RF-F-04 / 05 / 06                                                                  | R1–R13 / R14–R23, R26–R28 / R24–R25, R29–R33 |
| RNF-SEG-02 (tokens invalidados al suspender)                                       | R16, R21 |
| RNF-SEG-04 · RNF-SEG-12 · RNF-SEG-18                                               | R42 · R44, R45, R46 · R40, R41 |
| RNF-SEG-13 (errores genéricos)                                                     | R12, R20, R21, R22, R34 |
| RNF-USA-04 (aviso por correo de la suspensión) y D4 (reactivación)                 | R18, R32 |
| RN-01 (suspendida: no inicia sesión y sus espacios se ocultan)                     | R20, R21, R22, R23 |
| RN-15 (la búsqueda excluye arrendadores suspendidos)                               | R27, R28 (los usa HU-10) |
