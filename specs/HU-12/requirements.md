# HU-12 — Requirements: Reserva con bloqueo temporal

> Fuente: `docs/historias/HU-12.md` (CA1–CA4, STRIDE S/E, T, D, R), `docs/requerimientos.md` (RF-D-01…04,
> RNF-SEG-01, 07, 09, 12, 17, RN-03, RN-05, §8 y §9-8), `docs/architecture.md` (puerto
> `espacios.ReservasDelEspacio`, contrato DT-10, HU-09 D1, "HU-12 (reservar)"), `docs/modelo-datos.md`
> (`reserva`, `reserva_bloque`), `specs/HU-08/`, `specs/HU-09/`, `specs/HU-10/`, `specs/HU-11/` (§ 13),
> `specs/HU-03/` y `progress/explore_HU-12.md`. Notación EARS estricta (`docs/specs.md`). Los requirements
> hablan de la **API** y de los servicios que usan otros módulos; la presentación está en `design.md` § 10
> Pantallas y las decisiones técnicas en `design.md` § 1.

## Decisiones del dueño (Eduardo Saldaña, 2026-10-30)

Tomadas sobre los puntos 1–4 del informe del explorador (`progress/HU-12.md`). No llevan ⚠.

| # | Decisión | Afecta |
|---|----------|--------|
| D1 | Solo el rol `USUARIO` reserva. `ARRENDADOR` y `ADMIN` reciben 403 genérico y la ficha no les ofrece el botón. "Usuario autenticado" de RF-D-01 significa rol `USUARIO`. | R1, R36 |
| D2 | Expiración en **tres capas**: (1) filtro en lectura: las vencidas no ocupan; (2) un proceso programado cada minuto las pasa a `EXPIRADA` y audita, con intervalo y activación por propiedad, apagado en tests; (3) al crear una reserva se liberan las vencidas de esos bloques. Cierra §9-8 para RF-D-02 (los recordatorios siguen abiertos para HU-25). | R9–R17, R27, R39, R43 |
| D3 | Tras reservar, la pantalla `/reservas/:id` muestra resumen, monto y contador; `GET /api/reservas/{id}` solo para su dueño (si no, 404). | R37–R41 |
| D4 | El cupo de 3 pendientes se protege bloqueando la fila del usuario con un método nuevo de `CuentaService` (propagación `MANDATORY`), con orden fijo `usuario → espacio → reserva`. Al superarlo: 400 con mensaje claro. | R25, R26, R28 |

## Supuestos a confirmar por el dueño al aprobar

> ⚠ Las fuentes no fijan estos puntos. El spec adopta la recomendación del explorador; si **Eduardo
> Saldaña** no la acepta, cambian los requirements citados antes de pasar a `in_progress`.

| # | Supuesto | Afecta |
|---|----------|--------|
| S1 | (punto 5) El conflicto de CA3 responde **400 sin campo** (`ReglaNegocioException`), no 409, como HU-08 DT-6. Texto: "Horario no disponible. Elige otro bloque." | R18, R19, R22 |
| S2 | (punto 6) **No** se define aún `pagos.ReservasPorCobrar` ni el paquete `pagos`: HU-13 los crea. `design.md` § 12 anota lo que necesitará. | — |
| S3 | (punto 7) El retorno tras el login usa `state.retorno` de React Router, no un parámetro en la URL; se cambian las **dos** salidas a `/panel` de `IniciarSesion.jsx`. Una sesión caducada (401 con token) también vuelve a donde estaba, leyendo `window.location` en `ProveedorSesion.jsx`: **cambia código de HU-03** y afecta a todas las rutas protegidas (avisar a Jorge Quispe). Los bloques elegidos **no** se conservan al volver: CA4 solo pide volver a la ficha. | R35 y Pantallas |
| S4 | (punto 8) Se permiten bloques que empiezan en menos de 15 minutos: basta con que no hayan empezado. Consecuencia aceptada: se podría pagar con el bloque ya empezado, y cancelar daría 0 %. | R1, R18 |
| S5 | (punto 9) Si el arrendador o el usuario se suspende, o el espacio se pausa u oculta, mientras hay una `PENDIENTE_PAGO`, esta **sigue y expira sola**: la suspensión no toca reservas (HU-09 S9). HU-13 comprobará `cuentaActiva` antes de cobrar; las `CONFIRMADA` son tema de HU-15. | R6, R9, R37 |
| S6 | (punto 10) Una reserva abarca **una sola fecha** y horas enteras consecutivas, sin cruzar medianoche (24:00 + 0:00 del día siguiente son dos reservas). Sin tope propio de horas: el día acota a 24. | R32 |
| S7 | (punto 11) **Riesgo aceptado:** un usuario puede renovar 3 pendientes indefinidamente o abarcar un día entero sin pagar, y no puede soltar una pendiente (espera los 15 minutos). No se agregan reglas ni la transición `PENDIENTE_PAGO → CANCELADA`, que no existe en RN-03. | R26, R27 |
| S8 | (punto 2) Sin modal de confirmación: "Reservar" crea la reserva directamente, porque nada se cobra aún. | Pantallas |
| S9 | (punto 12) La expiración se audita con `usuario_id` e `ip` nulos y `origen=PROCESO`, también cuando la dispara la reserva de otro usuario: quien expira es el sistema. | R43 |
| S10 | (punto 17) Al existir el bean real de `ReservasDelEspacio`, se agrega `@Primary` a los dobles de `BusquedaControllerIT` (HU-10) y `EspaciosControllerSoporte` (HU-08): **se tocan tests de otras historias** y se corrige `docs/architecture.md`, que afirma que el segundo ya lo tiene. | R6 |
| S11 | (puntos 14 y 15) `CatalogoEspaciosService` gana `nombres(ids)`, sin filtro de visibilidad, para mostrar una reserva cuyo espacio ya no es visible (sirve también a HU-14). `cancelada_por` y `cancelada_en` **no** se crean: las agrega HU-15 con su migración (avisar a Kevin Chan). | R37 |

## Definiciones

- **Usuario con sesión:** cuenta con rol `USUARIO` y sesión vigente (D1).
- **Espacio visible:** espacio `PUBLICADO` cuyo arrendador está `ACTIVA` (HU-09 D1).
- **Hoy, ahora local, ventana, fecha bien escrita:** como en HU-08 (`America/Lima`, de *hoy* a *hoy + 55 días*).
- **Ahora:** el instante que da el reloj del sistema.
- **Reserva vigente:** `estado = PENDIENTE_PAGO` y *ahora* anterior a `expira_en`.
- **Reserva vencida:** `estado = PENDIENTE_PAGO` y *ahora* igual o posterior a `expira_en`.
- **Reserva activa:** reserva vigente o `CONFIRMADA`.
- **Bloque:** una hora entera `h` (0–23) de una fecha, de `h` a `h + 1` (HU-08).
- **Bloque libre:** hora que `DisponibilidadService.horasLibres(espacioId, fecha)` devuelve para esa fecha:
  dentro del horario, en un día no bloqueado, no empezada y sin reserva activa.
- **Selección válida:** valor de `horas` que es una lista de 1 a 24 enteros JSON de 0 a 23, sin repetir, que al
  ordenarlos son consecutivos (⚠ S6). Un decimal, un texto o un nulo no es un entero.
- **Proceso de expiración:** `ExpiracionReservas.expirarVencidas()`, ejecutado por el proceso programado.
- **ReservaDTO:** cuerpo con `id`, `espacioId`, `espacioNombre`, `estado`, `fecha`, `horaInicio`, `horaFin`,
  `precioHora`, `montoTotal`, `expiraEn` y `segundosRestantes` (`design.md` § 4).
- **Mensajes** (texto exacto en `design.md` § 4): *horario no disponible*, *cupo*, *espacio*, *bloques*, *fecha
  inválida* y *fuera de ventana* (mismos textos que HU-08), *sesión inválida* (HU-03), *acceso denegado* y
  *recurso no encontrado*.

## Crear la reserva (CA1, RF-D-01, RN-03, RN-05, STRIDE S/E y T)

## R1
CUANDO un usuario con sesión con menos de 3 reservas vigentes envía `POST /api/reservas` con el `espacioId` de un
espacio visible, una `fecha` de la ventana y en `horas` una selección válida de bloques libres, el sistema DEBE
responder `201 Created` con un ReservaDTO con `estado = "PENDIENTE_PAGO"` (D1, ⚠ S4).

## R2
CUANDO el sistema responde según R1, el sistema DEBE guardar una fila de `reserva` con `estado = PENDIENTE_PAGO`,
`usuario_id` igual al id del usuario de la sesión, `creada_en` igual a *ahora* y `expira_en` igual a `creada_en` más
15 minutos.

## R3
CUANDO el sistema responde según R1, el sistema DEBE guardar en esa fila `precio_hora_aplicado` igual al
`precio_hora` del espacio en ese momento y `monto_total` igual a ese precio multiplicado por el número de bloques.

## R4
CUANDO el sistema responde según R1, el sistema DEBE guardar una fila de `reserva_bloque` por cada hora pedida, con
el id de la reserva, el del espacio, la fecha, esa hora y `activo = true`.

## R5
CUANDO el sistema procesa `POST /api/reservas`, el sistema DEBE ignorar cualquier `usuarioId`, `id`, `estado`,
`montoTotal`, `precioHora` o `expiraEn` enviado en el cuerpo.

## Retención de los bloques (CA1, RN-03, puerto de HU-08)

## R6
MIENTRAS una reserva es activa, el sistema DEBE devolver cada una de sus filas activas de `reserva_bloque` del rango
pedido en `ReservasDelEspacio.bloquesReservados`, con `pagado = true` solo si es `CONFIRMADA` (⚠ S5, S10).

## R7
CUANDO otro módulo invoca `ReservasDelEspacio.bloquesReservadosDelDia(espacioIds, fecha)`, el sistema DEBE devolver
por espacio los mismos bloques que R6 con una sola consulta a `reserva_bloque` (HU-10 DT-6).

## R8
MIENTRAS una reserva es activa, el sistema NO DEBE incluir sus horas en `GET /api/espacios/{id}/disponibilidad` de esa
fecha.

## Expiración (CA2, RF-D-02, D2)

## R9
CUANDO se ejecuta el proceso de expiración, el sistema DEBE cambiar a `EXPIRADA` el `estado` de cada reserva vencida
(⚠ S5).

## R10
CUANDO el sistema cambia una reserva a `EXPIRADA`, el sistema DEBE dejar `activo = false` en todas sus filas de
`reserva_bloque`.

## R11
CUANDO se ejecuta el proceso de expiración, el sistema NO DEBE cambiar una reserva vigente ni una reserva `CONFIRMADA`.

## R12
MIENTRAS una reserva es vencida, el sistema NO DEBE devolver sus bloques en `ReservasDelEspacio.bloquesReservados` ni
en `bloquesReservadosDelDia`, aunque el proceso de expiración aún no la haya procesado.

## R13
DONDE `espacioya.reservas.expiracion.activa` es `true`, el sistema DEBE ejecutar el proceso de expiración cada
`espacioya.reservas.expiracion.intervalo`, que por defecto vale 1 minuto.

## R14
DONDE `espacioya.reservas.expiracion.activa` es `false`, el sistema NO DEBE programar el proceso de expiración.

## R15
SI falla la expiración de una reserva ENTONCES el sistema DEBE continuar con las demás reservas vencidas sin deshacer
las ya expiradas.

## R16
SI al bloquear la fila de una reserva para expirarla esta ya no es vencida ENTONCES el sistema NO DEBE cambiarla ni
auditar su expiración.

## R17
CUANDO una solicitud de R1 pide un bloque que retiene una reserva vencida con `activo = true`, el sistema DEBE
expirarla según R10 y R43 en la misma transacción y responder según R1.

## Conflicto de horario (CA3, RF-D-03, RNF-SEG-09, STRIDE T)

## R18
SI alguna de las horas pedidas para un espacio visible no es un bloque libre ENTONCES el sistema DEBE responder 400
sin campo con el mensaje *horario no disponible*, sin guardar filas en `reserva` ni en `reserva_bloque` (⚠ S1, S4).

## R19
CUANDO dos usuarios con sesión envían a la vez `POST /api/reservas` con al menos un bloque en común, el sistema DEBE
crear una sola reserva y responder a la otra solicitud según R18.

## R20
El sistema DEBE impedir en la base de datos dos filas de `reserva_bloque` con `activo = true` y el mismo `espacio_id`,
`fecha` y `hora_inicio`.

## R21
El sistema DEBE permitir en la base de datos una fila de `reserva_bloque` con `activo = true` junto a filas con
`activo = false` del mismo `espacio_id`, `fecha` y `hora_inicio`.

## R22
SI la inserción en `reserva_bloque` viola la restricción de R20 ENTONCES el sistema DEBE responder 400 sin campo con el
mensaje *horario no disponible* (⚠ S1).

## R23
SI la creación falla por otra violación de integridad ENTONCES el sistema NO DEBE responder *horario no disponible*.

## R24
CUANDO llegan a la vez `POST /api/reservas` y `POST …/espacios/{id}/bloqueos` para el mismo espacio y la misma fecha, el
sistema NO DEBE dejar a la vez una reserva activa y una fila de `bloqueo_fecha` en esa fecha (HU-08 DT-10).

## R25
SI `DisponibilidadService.retenerSiLibres` o `CuentaService.bloquearCuenta` se invoca sin una transacción activa
ENTONCES el sistema DEBE lanzar `IllegalTransactionStateException` (D4).

## Cupo de pendientes (STRIDE D, RN-05, RNF-SEG-17)

## R26
SI el usuario de la sesión ya tiene 3 reservas vigentes ENTONCES el sistema DEBE responder a `POST /api/reservas` 400
sin campo con el mensaje *cupo*, sin guardar filas en `reserva` ni en `reserva_bloque` (D4, ⚠ S7).

## R27
El sistema DEBE contar en el cupo de R26 solo las reservas vigentes del usuario de la sesión (D2, ⚠ S7).

## R28
CUANDO un usuario con sesión y 2 reservas vigentes envía a la vez dos `POST /api/reservas` para espacios distintos, el
sistema DEBE crear una sola reserva y responder a la otra solicitud según R26 (D4).

## Validación de la solicitud (RNF-SEG-07)

## R29
SI `espacioId` falta o no es un entero JSON positivo ENTONCES el sistema DEBE responder 400 con un error en el campo
`espacioId` y el mensaje *espacio*.

## R30
SI `fecha` falta o no es una fecha bien escrita ENTONCES el sistema DEBE responder 400 con un error en el campo `fecha`
y el mensaje *fecha inválida*.

## R31
SI `fecha` es una fecha bien escrita fuera de la ventana ENTONCES el sistema DEBE responder 400 con un error en el campo
`fecha` y el mensaje *fuera de ventana*.

## R32
SI `horas` falta o no es una selección válida ENTONCES el sistema DEBE responder 400 con un error en el campo `horas` y
el mensaje *bloques* (RN-05, ⚠ S6).

## R33
SI `espacioId` no corresponde a un espacio visible ENTONCES el sistema DEBE responder 404 con el mensaje *recurso no
encontrado*, sin guardar filas en `reserva` ni en `reserva_bloque` (HU-09 D1).

## R34
CUANDO un cliente con sesión vigente envía `POST /api/reservas`, el sistema DEBE aplicar las comprobaciones en este
orden y responder con la primera que falle: forma del cuerpo (R29, R30 sin calendario, R32 con `horas` ausente o vacía),
rol (R36), fecha (R30, R31), selección (R32), cupo (R26), espacio (R33) y disponibilidad (R18).

## Sesión y rol (CA4, RF-D-04, STRIDE S/E, RNF-SEG-01)

## R35
SI un cliente sin sesión vigente envía `POST /api/reservas` o `GET /api/reservas/{id}` ENTONCES el sistema DEBE
responder 401 con el mensaje *sesión inválida*, sin guardar filas en `reserva` ni en `reserva_bloque` (⚠ S3).

## R36
SI una cuenta con rol distinto de `USUARIO` envía `POST /api/reservas` con un cuerpo de forma correcta ENTONCES el
sistema DEBE responder 403 con el mensaje *acceso denegado*, sin guardar filas en `reserva` ni en `reserva_bloque` (D1).

## Consulta de la reserva (D3, CA1, RNF-SEG-19)

## R37
CUANDO el usuario de la sesión envía `GET /api/reservas/{id}` de una reserva suya, el sistema DEBE responder 200 con un
ReservaDTO con la `fecha`, `horaInicio` y `horaFin` de todas sus filas de `reserva_bloque`, activas o no, y el nombre
del espacio aunque ya no sea visible (D3, ⚠ S5, S11).

## R38
SI `{id}` de `GET /api/reservas/{id}` está mal escrito, no existe o es de una reserva de otro usuario ENTONCES el
sistema DEBE responder 404 con el mensaje *recurso no encontrado* (D3).

## R39
MIENTRAS una reserva es vencida, el sistema DEBE responder en `GET /api/reservas/{id}` `estado = "EXPIRADA"` sin
escribir en la base de datos (D2).

## R40
El sistema DEBE responder en `segundosRestantes` del ReservaDTO los segundos enteros que faltan hasta `expira_en` si la
reserva es vigente, y 0 en otro caso.

## R41
El sistema NO DEBE incluir en el ReservaDTO el `usuario_id`, el id del arrendador ni ningún dato del arrendador.

## Registro y restricciones (STRIDE R, RNF-SEG-12)

## R42
CUANDO el sistema responde según R1, el sistema DEBE registrar en la auditoría la acción `RESERVA_CREADA` con el id del
usuario de la sesión, la entidad `reserva` con su id, la IP del cliente, la fecha y hora y el detalle
`espacioId=<id>;fecha=<AAAA-MM-DD>;horas=<h1,h2>;monto=<monto_total>`.

## R43
CUANDO el sistema cambia una reserva a `EXPIRADA`, el sistema DEBE registrar en la auditoría la acción
`RESERVA_EXPIRADA` con `usuario_id` e `ip` nulos, la entidad `reserva` con su id, la fecha y hora y el detalle
`origen=PROCESO;espacioId=<id>` (⚠ S9).

## R44
SI el sistema rechaza una solicitud según R18, R22, R26, R29–R33 o R36 ENTONCES el sistema NO DEBE registrar la acción
`RESERVA_CREADA`.

## R45
El sistema DEBE impedir en la base de datos una fila de `reserva` con `estado` distinto de `PENDIENTE_PAGO`,
`CONFIRMADA`, `EXPIRADA` o `CANCELADA`, con `monto_total` o `precio_hora_aplicado` no positivos, o con `expira_en` no
posterior a `creada_en`, y una fila de `reserva_bloque` con `hora_inicio` fuera de 0 a 23.

## Reemplazos y compatibilidad

- **HU-08 R21** sigue vigente sin el bean (prueba unitaria); con HU-12 hay implementación real del puerto, de modo que
  HU-08 R19, R20 y R24, y HU-11 R16, se cumplen con reservas reales.
- **HU-08 DT-10** se cumple: `retenerSiLibres` toma el mismo bloqueo de `espacio` que `CalendarioEspacioService`.
- **HU-09 S9** queda resuelto por ⚠ S5.
- **HU-11:** la ficha deja de ser de solo lectura para el usuario con sesión y para el visitante; sus DTO no cambian.
- **HU-03:** `IniciarSesion.jsx` y `ProveedorSesion.jsx` se amplían (⚠ S3). Sus requirements, escritos sobre la API, no
  cambian.
- **`docs/requerimientos.md` §9-8:** la expiración queda resuelta (D2); los recordatorios (RF-D-10) siguen abiertos.

## Trazabilidad con la historia

| Origen (`docs/historias/HU-12.md` y fuentes)                                | Cubierto por |
|-----------------------------------------------------------------------------|--------------|
| CA1: elijo bloques libres y confirmo → reserva "pendiente de pago"          | R1, R2, R4, R29–R34, R37, R40 (pantalla en design § 10) |
| CA1: … con el monto calculado en servidor                                   | R3, R5 |
| CA1: … los bloques quedan retenidos 15 minutos                              | R2, R4, R6, R7, R8 |
| CA2: pasan 15 minutos sin pago → "expirada"                                 | R9, R11, R13, R14, R15, R16, R39 |
| CA2: … los bloques se liberan                                               | R10, R12, R17, R21 |
| CA3: dos usuarios a la vez → solo uno obtiene la reserva                    | R19, R20, R22, R23, R24, R25 |
| CA3: … el otro ve "horario no disponible"                                   | R18, R19, R22 (pantalla en design § 10) |
| CA4: sin sesión, pulso "reservar" → login y vuelvo a la ficha               | R35 (redirección y retorno en design § 10) |
| (S/E) Reserva a nombre del usuario de la sesión, no de un ID del cliente    | R2, R5, R36, R38, R41 |
| (T) Restricción única por espacio y bloque en BD                            | R20, R21, R22, R45 |
| (D) Máximo 3 reservas pendientes simultáneas por usuario                    | R26, R27, R28 |
| (R) Registro de creación y expiración                                       | R42, R43, R44 |
| RF-D-01 / RF-D-02 / RF-D-03 / RF-D-04                                       | R1–R8, R29–R34 / R9–R17 / R18–R25 / R35 |
| RNF-SEG-01 · RNF-SEG-07 · RNF-SEG-09                                        | R2, R5 · R3, R5, R29–R32 · R20–R22 |
| RNF-SEG-12 · RNF-SEG-17                                                     | R42–R44 · R26–R28 |
| RNF-SEG-19 (propiedad de la reserva)                                        | R38 |
| RN-03 (estados de la reserva)                                               | R1, R9, R11, R45 |
| RN-05 (3 pendientes, bloques consecutivos)                                  | R26–R28, R32 |
| HU-09 D1 (arrendador no activo)                                             | R33 |

## Adenda 2026-11-16 — Escenarios de calidad AC001 y AC002

> Pedida por el orquestador con la historia en `done`, para medir dos escenarios del informe (capítulo 6.3) con la cantidad
> de intentos que prometen (`PROMPT-escenarios-calidad.md` § 1 y § 2). **Solo agrega pruebas de integración**: no hay
> `R<n>` nuevos ni cambios de producción. Las pruebas nuevas verifican R19 y R24, que ya existen, a mayor escala. Diseño en
> `design.md` § 14 y tasks T24–T26 en `tasks.md`. **Su implementación espera la aprobación del dueño (Eduardo Saldaña).**
> Nada se renumera.

### Escenarios que se miden

| AC | Atributo | Medida que promete el escenario | Requisito existente | Prueba hoy | Prueba nueva |
|----|----------|---------------------------------|---------------------|------------|--------------|
| AC001 | Confiabilidad – Consistencia | Exactamente 1 reserva activa por bloque en **20 intentos concurrentes** | R19 (con R18, R20, R22) | `ReservaConcurrenciaIT.dosUsuariosElMismoBloque`: 2 usuarios, 5 rondas | `ReservaConcurrenciaIT.veinteUsuariosElMismoBloque` |
| AC002 | Confiabilidad – Consistencia | 0 reservas activas en fechas bloqueadas en **20 intentos concurrentes** | R24 (con HU-08 R20 y R40) | `ReservaConcurrenciaIT.reservaYBloqueoDeFecha`: 1 reserva y 1 bloqueo, 5 rondas | `ReservaConcurrenciaIT.veinteReservasYBloqueosDeFecha` |

### AC001 — 20 usuarios a la vez con el mismo bloque (R19)

- **Solicitudes simultáneas:** 20 `POST /api/reservas`, una por cada uno de **20 usuarios distintos** con sesión, con el
  mismo `espacioId`, la misma `fecha` y la misma `horas` (un bloque). Usuarios distintos para que el cupo de R26 no
  intervenga.
- **Rondas:** `RONDAS` (5), cada una con una hora distinta, como `dosUsuariosElMismoBloque`.
- **Aserciones por ronda:**
  1. Exactamente **1** respuesta 201 y **19** respuestas 400.
  2. Cada respuesta 400 trae el mensaje *horario no disponible* (R18, R22).
  3. En `reserva_bloque` hay exactamente **1** fila con `activo = true` para ese espacio, fecha y hora (R20).

### AC002 — 20 reservas y bloqueos de la misma fecha a la vez (R24)

- **Solicitudes simultáneas:** 20 a la vez sobre la misma fecha del mismo espacio: **10** `POST /api/reservas` de 10
  usuarios distintos, cada uno con una hora distinta de esa fecha (para que no compitan entre sí por el bloque y la única
  carrera sea con el bloqueo), y **10** `POST /api/arrendadores/actual/espacios/{id}/bloqueos` del arrendador dueño con
  esa fecha.
- **Rondas:** 5, cada una con una fecha distinta (como `reservaYBloqueoDeFecha`, una semana más por ronda).
- **Aserciones por ronda** (mismo criterio que `reservaYBloqueoDeFecha`, R24):
  1. Toda respuesta es 201 o 400; al menos una operación es 201.
  2. En `bloqueo_fecha` hay **a lo sumo 1** fila para ese espacio y fecha (HU-08 R40).
  3. Si hay 1 fila de bloqueo: **0** filas activas de `reserva_bloque` en esa fecha, **0** reservas con 201 y exactamente
     **1** bloqueo con 201.
  4. Si no hay fila de bloqueo: **0** bloqueos con 201, y el número de filas activas de `reserva_bloque` en esa fecha es
     igual al número de reservas con 201 (al menos 1).
  5. Nunca coexisten en esa fecha una fila de `bloqueo_fecha` y una fila activa de `reserva_bloque`.
  6. Una reserva rechazada responde 400 *horario no disponible* (R18). Un bloqueo rechazado responde 400 con *ya
     bloqueada* (HU-08 R40) o *reserva pendiente* (HU-08 R20), según lo que ganó.

### Reglas de la adenda

- **Límites del entorno de prueba.** Si una cantidad choca con un límite (por ejemplo, el pool de conexiones de las pruebas
  o un límite por IP), **no se reduce la cantidad ni se toca producción**: solo se ajusta la configuración de prueba
  (`2_Backend/src/test/resources/config/application.properties` o la de la clase) con un mecanismo que ya exista, y se
  documenta el ajuste y su motivo en `progress/impl_HU-12.md` y en la evidencia (`progress/calidad_escenarios.md`). Si no
  hay forma limpia, se para y se pregunta al dueño. Hoy ninguna `ReglaLimite` aplica a `POST /api/reservas` ni a
  `POST …/bloqueos`, y el inicio de sesión solo cuenta fallos.
- **Defectos.** Si una prueba nueva falla por un defecto real (de forma consistente o intermitente), **no se modifica
  `2_Backend/src/main`** ni se relaja la aserción: se registra como `DEF-xx`, siguiendo la numeración que ya existe en
  `progress/`, con historia, esperado, obtenido, severidad y cómo reproducirlo, y se avisa al dueño.
- Las pruebas existentes no se modifican ni se borran.

### Trazabilidad de la adenda

| Escenario | Requisito existente | Prueba |
|-----------|---------------------|--------|
| AC001: 1 reserva activa por bloque en 20 intentos concurrentes | R19, R18, R20, R22 | `ReservaConcurrenciaIT.veinteUsuariosElMismoBloque` |
| AC002: 0 reservas activas en fechas bloqueadas en 20 intentos concurrentes | R24; HU-08 R20, R40 | `ReservaConcurrenciaIT.veinteReservasYBloqueosDeFecha` |
