# HU-08 — Requirements: Disponibilidad del espacio

> Fuente: `docs/historias/HU-08.md` (CA1–CA3, STRIDE E, R y la simplificación "bloques libres
> calculados al consultar"), `docs/requerimientos.md` (RF-B-05…07, RN-03, RN-04, RNF-SEG-12, 19, §8
> "Cálculo de disponibilidad (horario − bloqueos − reservas)", que construye HU-08 y reutilizan
> HU-10, 11 y 12), `docs/architecture.md` (módulo `espacios`, puerto `espacios.ReservasDelEspacio`
> "para HU-08 y HU-21", estados del espacio y de la reserva, simplificación "los bloques libres se
> calculan al consultar"), `docs/modelo-datos.md` (`horario_semanal`, `bloqueo_fecha`,
> `reserva_bloque`), `specs/HU-07/` (rutas `/api/arrendadores/actual/espacios`, orden rol → correo →
> propiedad → estado, `findParaActualizar`, mensajes) y `progress/explore_HU-08.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API** y del servicio
> público que usarán otros módulos; la presentación está en `design.md` § 13 Pantallas. Las
> decisiones técnicas están en `design.md` § 1.

## Decisiones por defecto que el dueño debe confirmar al aprobar

> ⚠ Las fuentes no fijan estos puntos (ninguno figura como pendiente en `docs/requerimientos.md`
> §9). El spec aplica el valor por defecto indicado; si **Sebastian Garate** no lo acepta, cambian
> los requirements citados antes de pasar a `in_progress`. Detalle y alternativas en `design.md` § 1.
>
> | # | Pregunta | Por defecto en este spec | Afecta |
> |---|----------|--------------------------|--------|
> | P1 | ¿En qué zona horaria se calculan "hoy", la hora actual y las 8 semanas? | **`America/Lima`** (UTC−5, sin horario de verano), a partir del `Clock` UTC existente. Una utilidad nueva `comun/configuracion/ZonaHoraria` la deja lista para HU-10/11/12. | R29 y todos los que usan *hoy* |
> | P2 | ¿Qué son "las próximas 8 semanas"? | Ventana **móvil de 56 días**: de *hoy* inclusive a *hoy + 55 días* inclusive; cada día se abre uno nuevo. Los bloques de *hoy* cuya hora de inicio ya llegó no están libres. | R13, R25, R28, R30, R31 |
> | P3 | ¿Un rango o varios por día? ¿Cómo se edita? | **Varias franjas por día**, sin superponerse (contiguas sí, p. ej. 8–12 y 12–14). El horario se **reemplaza completo** con un `PUT` (lista de franjas); una lista vacía deja el espacio sin horario. | R1–R7 |
> | P4 | ¿Cómo se representan día y horas? | `diaSemana` entero **1–7 (ISO: 1 = lunes … 7 = domingo)**; `horaInicio` entero 0–23 y `horaFin` entero 1–24 (fin exclusivo; 24 = medianoche). Bloque = (fecha, hora de inicio entera, 1 h), la misma granularidad que `reserva_bloque` de HU-12. | R4, R5, R9, R22 |
> | P5 | ¿Qué se bloquea y se puede desbloquear? | Se bloquea el **día completo** (el modelo solo tiene `fecha`). Solo fechas **dentro de la ventana**. Bloquear una fecha ya bloqueada → 400. **Sí se puede desbloquear** (`DELETE`), aunque ningún CA lo pide: sin eso un error del arrendador no tendría arreglo. | R10–R18 |
> | P6 | CA3: ¿qué es "reserva pagada" y qué pasa con una pendiente de pago? | Pagada = reserva `CONFIRMADA` con algún bloque activo en esa fecha → 400 *reserva pagada*. Una reserva `PENDIENTE_PAGO` vigente **también impide** el bloqueo (400 *reserva pendiente*, "intenta en unos minutos"): si se permitiera, el pago podría llegar después y quedaría una reserva pagada en un día bloqueado. El intento rechazado **no** se audita (no cambia nada). Código **400** (`ReglaNegocioException`, sin tocar `comun/error`). | R19, R20 |
> | P7 | ¿Se puede quitar del horario una hora con reservas futuras? | **Sí.** La reserva sigue válida; ese bloque solo deja de ofrecerse. Ningún CA lo restringe y la baja con reservas es HU-21. | — |
> | P8 | ¿En qué estados del espacio se edita la disponibilidad? | Como la ficha (HU-07 P6): **`BORRADOR`, `PUBLICADO` y `PAUSADO`**; `BAJA` y `OCULTO` → 400 *estado no editable*. Las escrituras exigen correo verificado; las lecturas, solo el rol. Publicar **no** exige horario (HU-07 no cambia). | R34, R36 |
> | P9 | ¿Cómo se demuestra en HU-08 que "esos bloques se pueden reservar" (CA1) y "dejan de estar disponibles" (CA2)? | Con una **consulta del dueño** `GET …/espacios/{id}/disponibilidad?fecha=` que devuelve los bloques del día con su estado, y con la **interfaz pública `DisponibilidadService.horasLibres`** que reutilizarán HU-10/11/12. La ruta pública de la ficha queda para HU-11. | R22–R31 |
> | P10 | El módulo `reservas` aún no existe (HU-12, sprint 3). | HU-08 define el puerto `espacios.service.puertos.ReservasDelEspacio`; mientras nadie lo implemente, **ninguna fecha tiene reservas**. CA3 se prueba con un doble del puerto. La firma se confirma con Eduardo Saldaña (HU-12). | R19–R21, R24 |
> | P11 | ¿Rate limiting? | **No.** HU-08 no tiene control (D) y RNF-SEG-17 no fija límite para el calendario del dueño (mismo criterio que HU-07 DT-14). | — |
> | P12 | ¿Número de migración? | **`V2_4__disponibilidad.sql`** (sprint 2; la última en `develop` es `V2_3`). Si otra historia del sprint 2 llega antes con `V2_4`, se renombra (`docs/conventions.md`). | R9, R18 |

## Definiciones

- **Hoy:** la fecha local en `America/Lima` del instante que da el reloj del sistema (⚠ P1).
- **Ahora local:** la fecha y hora local en `America/Lima` de ese instante.
- **Ventana:** las fechas desde *hoy* hasta *hoy + 55 días*, ambas inclusive (56 días = 8 semanas,
  ⚠ P2). `desde` = *hoy*; `hasta` = *hoy + 55 días*.
- **Arrendador habilitado:** cuenta con rol `ARRENDADOR` y `correo_verificado = true` (RN-01).
- **Espacio propio:** fila de `espacio` cuyo `arrendador_id` es el id del usuario de la sesión.
- **Espacio editable:** espacio propio en `BORRADOR`, `PUBLICADO` o `PAUSADO` (⚠ P8).
- **Franja:** objeto JSON con `diaSemana`, `horaInicio` y `horaFin` (⚠ P4).
- **Franja bien formada:** `diaSemana` número JSON entero de 1 a 7, `horaInicio` número JSON entero
  de 0 a 23 y `horaFin` número JSON entero de 1 a 24. Un decimal (`8.5`), un texto (`"8"`), un
  booleano o un valor ausente no es un entero.
- **Horas de una franja:** los enteros `h` con `horaInicio ≤ h < horaFin`.
- **Franjas superpuestas:** dos franjas del mismo `diaSemana` que comparten al menos una hora.
- **Horario válido:** cuerpo `{ "franjas": [...] }` con `franjas` presente (puede estar vacía), de
  como mucho 168 elementos, todas bien formadas, cada una con `horaFin > horaInicio` y sin franjas
  superpuestas.
- **Bloque:** una hora `h` (0–23) de una fecha; dura de `h` a `h + 1`.
- **Bloques del día:** las horas de las franjas guardadas cuyo `dia_semana` es el día de la semana
  ISO de la fecha.
- **Reserva activa / pagada / pendiente:** la que el puerto `ReservasDelEspacio` informa para ese
  espacio y fecha: activa = `PENDIENTE_PAGO` vigente o `CONFIRMADA` (RN-03); pagada = `CONFIRMADA`;
  pendiente = `PENDIENTE_PAGO` vigente.
- **Fecha bien escrita:** texto `AAAA-MM-DD` que es una fecha de calendario existente
  (`2026-02-30` no lo es).
- **HorarioDTO:** cuerpo `{ "franjas": [ { diaSemana, horaInicio, horaFin } ] }`.
- **BloqueosDTO:** cuerpo `{ "desde", "hasta", "fechas": [ "AAAA-MM-DD", ... ] }`.
- **DisponibilidadDiaDTO:** cuerpo `{ "fecha", "bloqueada", "bloques": [ { horaInicio, horaFin, estado } ] }`.
- **Mensajes** (texto exacto en `design.md` § 4): *rango de horas*, *franjas superpuestas*, mensajes
  por campo de la franja, *fecha inválida*, *fuera de ventana*, *ya bloqueada*, *reserva pagada*,
  *reserva pendiente*, *estado no editable* (HU-07), *acceso denegado*, *recurso no encontrado*.

## Horario semanal (CA1, RF-B-05, RN-04)

## R1
CUANDO un arrendador habilitado envía `PUT /api/arrendadores/actual/espacios/{id}/horario` con un
horario válido para un espacio editable, el sistema DEBE responder 200 con un HorarioDTO que
contiene exactamente las franjas recibidas, ordenadas por `diaSemana` y luego por `horaInicio`.

## R2
CUANDO el sistema responde según R1, el sistema DEBE dejar en `horario_semanal` exactamente una
fila por franja recibida para ese espacio, con sus valores de `dia_semana`, `hora_inicio` y
`hora_fin`, y ninguna otra fila de ese espacio.

## R3
CUANDO el sistema responde según R1 con `franjas` vacía, el sistema DEBE dejar el espacio sin
filas en `horario_semanal`.

## R4
SI el cuerpo de `PUT …/horario` no trae `franjas`, trae más de 168 franjas o trae alguna franja
que no está bien formada ENTONCES el sistema DEBE responder 400 con un error en `errores` por cada
campo inválido, con el campo `franjas` o `franjas[<i>].<nombre>` y su mensaje.

## R5
SI todas las franjas están bien formadas pero alguna tiene `horaFin ≤ horaInicio` ENTONCES el
sistema DEBE responder 400 con un error en el campo `franjas[<i>]` de cada una de esas franjas y
el mensaje *rango de horas*.

## R6
SI todas las franjas están bien formadas, con `horaFin > horaInicio`, pero hay franjas
superpuestas ENTONCES el sistema DEBE responder 400 con un error en el campo `franjas[<j>]` y el
mensaje *franjas superpuestas* por cada franja `j` que se superpone con otra de índice menor.

## R7
SI el sistema rechaza un `PUT …/horario` según R4, R5, R6, R32, R33, R34 o R36 ENTONCES el sistema
DEBE dejar sin cambios las filas de `horario_semanal`.

## R8
CUANDO un arrendador con sesión vigente envía `GET /api/arrendadores/actual/espacios/{id}/horario`
de un espacio propio, el sistema DEBE responder 200 con un HorarioDTO con las franjas guardadas,
ordenadas por `diaSemana` y `horaInicio` (lista vacía si no tiene).

## R9
El sistema DEBE impedir en la base de datos una fila de `horario_semanal` con `dia_semana` fuera
de 1 a 7, `hora_inicio` menor que 0, `hora_fin` mayor que 24, `hora_inicio ≥ hora_fin` o dos
filas con el mismo `espacio_id`, `dia_semana` y `hora_inicio`.

## Bloqueo de fechas (CA2, RF-B-06)

## R10
CUANDO un arrendador habilitado envía `POST /api/arrendadores/actual/espacios/{id}/bloqueos` con
`{ "fecha": "AAAA-MM-DD" }` bien escrita, dentro de la ventana, no bloqueada y sin reservas
activas, para un espacio editable, el sistema DEBE responder `201 Created` con `{ "fecha" }`.

## R11
CUANDO el sistema responde según R10, el sistema DEBE guardar una fila de `bloqueo_fecha` con el
id del espacio y esa fecha.

## R12
SI la `fecha` de `POST …/bloqueos`, el parámetro `fecha` de `GET …/disponibilidad` o el `{fecha}`
de `DELETE …/bloqueos/{fecha}` falta o no es una fecha bien escrita ENTONCES el sistema DEBE
responder 400 con un error en el campo `fecha` y el mensaje *fecha inválida*.

## R13
SI la `fecha` de `POST …/bloqueos` es anterior a *hoy* o posterior a *hoy + 55 días* ENTONCES el
sistema DEBE responder 400 con un error en el campo `fecha` y el mensaje *fuera de ventana* (⚠ P2).

## R14
SI la `fecha` de `POST …/bloqueos` ya está bloqueada para ese espacio ENTONCES el sistema DEBE
responder 400 con un error en el campo `fecha` y el mensaje *ya bloqueada*.

## R15
CUANDO un arrendador con sesión vigente envía `GET /api/arrendadores/actual/espacios/{id}/bloqueos`
de un espacio propio, el sistema DEBE responder 200 con un BloqueosDTO con `desde` y `hasta` de la
ventana y en `fechas` las fechas bloqueadas del espacio iguales o posteriores a *hoy*, en orden
ascendente.

## R16
CUANDO un arrendador habilitado envía `DELETE /api/arrendadores/actual/espacios/{id}/bloqueos/{fecha}`
de una fecha bloqueada de un espacio editable, el sistema DEBE responder `204 No Content` y
eliminar esa fila de `bloqueo_fecha` (⚠ P5).

## R17
SI `{fecha}` de `DELETE …/bloqueos/{fecha}` es una fecha bien escrita que no está bloqueada para
ese espacio ENTONCES el sistema DEBE responder 404 con el mensaje *recurso no encontrado* sin
eliminar ningún bloqueo.

## R18
El sistema DEBE impedir en la base de datos dos filas de `bloqueo_fecha` con el mismo
`espacio_id` y `fecha`.

## Conflicto con reservas (CA3, RF-B-07, RN-04)

## R19
SI la `fecha` de `POST …/bloqueos` tiene al menos un bloque de una reserva pagada del espacio
ENTONCES el sistema DEBE responder 400 sin campo con el mensaje *reserva pagada* y sin crear el
bloqueo.

## R20
SI la `fecha` de `POST …/bloqueos` no tiene reservas pagadas pero sí al menos un bloque de una
reserva pendiente del espacio ENTONCES el sistema DEBE responder 400 sin campo con el mensaje
*reserva pendiente* y sin crear el bloqueo (⚠ P6).

## R21
MIENTRAS ningún módulo implemente el puerto `ReservasDelEspacio`, el sistema DEBE tratar todas las
fechas como sin reservas activas (⚠ P10).

## Bloques del día (CA1, CA2, RN-04, simplificación "calculados al consultar")

## R22
CUANDO un arrendador con sesión vigente envía
`GET /api/arrendadores/actual/espacios/{id}/disponibilidad?fecha=AAAA-MM-DD` de un espacio propio
con una fecha de la ventana, el sistema DEBE responder 200 con un DisponibilidadDiaDTO con esa
`fecha`, `bloqueada` igual a si la fecha está bloqueada y en `bloques` un elemento por cada bloque
del día, en orden ascendente, con `horaInicio = h`, `horaFin = h + 1` y su `estado`.

## R23
CUANDO el sistema arma los bloques de una fecha bloqueada, el sistema DEBE asignar el estado
`BLOQUEADO` a todos sus bloques.

## R24
CUANDO el sistema arma los bloques de una fecha no bloqueada, el sistema DEBE asignar el estado
`RESERVADO` a cada bloque retenido por una reserva activa del espacio.

## R25
CUANDO el sistema arma los bloques de *hoy* no bloqueado, el sistema DEBE asignar el estado
`PASADO` a cada bloque no retenido por una reserva activa cuya hora de inicio es igual o anterior
a *ahora local*.

## R26
CUANDO el sistema arma los bloques de una fecha, el sistema DEBE asignar el estado `LIBRE` a cada
bloque que no recibe `BLOQUEADO`, `RESERVADO` ni `PASADO` según R23–R25.

## R27
SI el parámetro `fecha` de `GET …/disponibilidad` es una fecha bien escrita anterior a *hoy* o
posterior a *hoy + 55 días* ENTONCES el sistema DEBE responder 400 con un error en el campo
`fecha` y el mensaje *fuera de ventana*.

## R28
CUANDO el sistema calcula bloques para una fecha, el sistema DEBE calcularlos con el horario, los
bloqueos y las reservas vigentes en el momento de la consulta, sin guardar bloques en la base de
datos.

## R29
El sistema DEBE calcular *hoy*, *ahora local* y la ventana en la zona `America/Lima` a partir del
reloj del sistema (⚠ P1).

## Servicio de disponibilidad para otros módulos (§8, RN-04)

## R30
CUANDO otro módulo invoca `DisponibilidadService.horasLibres(espacioId, fecha)` para un espacio en
`PUBLICADO` y una fecha de la ventana, el sistema DEBE devolver, en orden ascendente, las horas de
inicio de los bloques de esa fecha cuyo estado es `LIBRE` según R22–R26.

## R31
SI `DisponibilidadService.horasLibres(espacioId, fecha)` recibe un espacio inexistente o en un
estado distinto de `PUBLICADO`, o una fecha fuera de la ventana, ENTONCES el sistema DEBE devolver
una lista vacía.

## Propiedad y acceso (STRIDE E, RNF-SEG-18, 19)

## R32
SI `{id}` no corresponde a un espacio propio en `GET` o `PUT …/horario`, `GET` o `POST …/bloqueos`,
`DELETE …/bloqueos/{fecha}` o `GET …/disponibilidad` ENTONCES el sistema DEBE responder 404 con el
mensaje *recurso no encontrado* sin cambiar horarios ni bloqueos.

## R33
SI una cuenta con rol distinto de `ARRENDADOR` envía cualquiera de las solicitudes de R32
ENTONCES el sistema DEBE responder 403 con el mensaje *acceso denegado*.

## R34
SI un arrendador con `correo_verificado = false` envía `PUT …/horario`, `POST …/bloqueos` o
`DELETE …/bloqueos/{fecha}` ENTONCES el sistema DEBE responder 403 con el mensaje *acceso
denegado*.

## R35
SI un cliente sin sesión vigente envía cualquiera de las solicitudes de R32 ENTONCES el sistema
DEBE responder 401 con el mensaje de sesión inválida de HU-03.

## R36
SI se envía `PUT …/horario`, `POST …/bloqueos` o `DELETE …/bloqueos/{fecha}` para un espacio
propio en `BAJA` u `OCULTO` ENTONCES el sistema DEBE responder 400 con el mensaje *estado no
editable* sin cambiar horarios ni bloqueos (⚠ P8).

## R37
CUANDO el sistema procesa `PUT …/horario` o `POST …/bloqueos`, el sistema DEBE ignorar cualquier
`id`, `espacioId`, `arrendadorId` o `usuarioId` enviado en el cuerpo.

## R38
CUANDO un cliente con sesión vigente envía `PUT …/horario` con un cuerpo que incumple R4, o
`POST …/bloqueos` sin `fecha` o con una `fecha` que no tiene la forma `AAAA-MM-DD`, el sistema DEBE
responder según R4 o R12 sin comprobar rol, correo verificado, propiedad ni estado.

## R39
CUANDO un cliente con sesión vigente envía una solicitud de R32 que no cae en R38, el sistema DEBE
aplicar las comprobaciones en este orden y responder con la primera que falle: rol (R33), correo
verificado (R34, solo escrituras), propiedad (R32), estado (R36, solo escrituras) y luego las
demás reglas de la operación en el orden R12, R13, R14, R19, R20 para `POST …/bloqueos` y R5, R6
para `PUT …/horario`.

## R40
SI dos solicitudes `POST …/bloqueos` para el mismo espacio y la misma fecha llegan a la vez
ENTONCES el sistema DEBE crear un solo bloqueo y responder a la otra 400 con el mensaje *ya
bloqueada*.

## Registro de cambios (STRIDE R, RNF-SEG-12)

## R41
CUANDO el sistema responde según R1 y el conjunto de franjas cambió, el sistema DEBE registrar en
la auditoría la acción `HORARIO_ESPACIO_ACTUALIZADO` con el id del usuario, la entidad `espacio`
con su id, la IP, la fecha y hora y el detalle `antes=<franjas>;despues=<franjas>` con el formato
de `design.md` § 6.

## R42
CUANDO el sistema responde según R1 sin que cambie el conjunto de franjas, el sistema NO DEBE
registrar una auditoría `HORARIO_ESPACIO_ACTUALIZADO`.

## R43
CUANDO el sistema responde según R10, el sistema DEBE registrar en la auditoría la acción
`BLOQUEO_FECHA_CREADO` con el id del usuario, la entidad `espacio` con su id, la IP, la fecha y
hora y el detalle `fecha=AAAA-MM-DD`.

## R44
CUANDO el sistema responde según R16, el sistema DEBE registrar en la auditoría la acción
`BLOQUEO_FECHA_ELIMINADO` con el id del usuario, la entidad `espacio` con su id, la IP, la fecha y
hora y el detalle `fecha=AAAA-MM-DD`.

## Minimización (RNF-SEG-16)

## R45
El sistema NO DEBE incluir en las respuestas de `…/horario`, `…/bloqueos` ni `…/disponibilidad` el
`arrendador_id`, los ids de las filas de `horario_semanal` o `bloqueo_fecha`, ni datos de las
reservas distintos del estado del bloque (usuario, monto o id de la reserva).

## Trazabilidad con la historia

| Origen (`docs/historias/HU-08.md` y fuentes)                                              | Cubierto por |
|-------------------------------------------------------------------------------------------|--------------|
| CA1: defino horarios por día de la semana (ej. lunes a viernes 8:00–22:00)                | R1, R2, R3, R4, R5, R6, R7, R8, R9 |
| CA1: … esos bloques se pueden reservar en las próximas 8 semanas                          | R22, R26, R27, R28, R29, R30, R31 (+ pantalla en design § 13) |
| CA2: bloqueo una fecha, guardo → sus bloques dejan de estar disponibles                   | R10, R11, R12, R13, R14, R15, R16, R17, R18, R23, R30, R40 |
| CA3: fecha con una reserva pagada → aviso del conflicto y no se bloquea                   | R19, R20, R21, R24 (aviso en design § 13) |
| (E) Solo el arrendador dueño modifica el calendario; validado en backend                  | R32, R33, R34, R35, R36, R37, R38, R39 |
| (R) Registro de cada cambio de disponibilidad                                             | R41, R42, R43, R44 |
| Simplificación: bloques libres = horario − bloqueos − reservas, al consultar, sin job     | R22–R28, R30 |
| RF-B-05 / RF-B-06 / RF-B-07                                                               | R1–R9, R22, R26, R30 / R10–R18, R23 / R19–R21 |
| RN-04 (bloques de 1 h, 8 semanas, cálculo al consultar, sin bloquear fecha con reserva pagada) | R4, R13, R19, R22–R31 |
| RN-03 (pendiente de pago retiene bloques; confirmada = pagada)                            | R20, R24 |
| RNF-SEG-12                                                                                | R41–R44 |
| RNF-SEG-19 · RNF-SEG-18                                                                   | R32, R37 · R33 |
| RNF-SEG-16 (minimización)                                                                 | R45 |
| §8 Cálculo de disponibilidad reutilizable por HU-10, 11, 12                               | R30, R31 |
