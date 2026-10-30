# HU-11 — Requirements: Detalle del espacio

> Fuente: `docs/historias/HU-11.md` (CA1, CA2, STRIDE I y D), `docs/requerimientos.md` (RF-C-04, RF-C-05,
> RNF-SEG-16, RNF-SEG-17, RN-04, RN-06, RN-15, §8 "Rate limiting" y "Cálculo de disponibilidad", §9.9 resuelto
> por Sebastian Garate el 2026-10-29), `docs/architecture.md` (módulo `busqueda`, regla HU-09 D1,
> `DisponibilidadService`, `CatalogoEspaciosService`, "Disponibilidad: qué deja HU-08 y qué falta"),
> `docs/frontend.md` (§ Bloques horarios, kit), `specs/HU-07/`, `specs/HU-08/`, `specs/HU-10/` y
> `progress/explore_HU-11.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API** y del servicio público de
> `espacios`; la presentación (galería, política de cancelación, mensajes) está en `design.md` § 12 Pantallas.
> Las decisiones técnicas están en `design.md` § 1 (DT-n).

## Decisiones D1–D8 — confirmadas por Adriana Callupe el 2026-10-30

> La dueña (**Adriana Callupe**) prefirió no responder preguntas antes del spec, que aplicó estos valores por
> defecto a los huecos de producto de `progress/explore_HU-11.md`. Las confirmó tal cual al aprobar el spec el
> 2026-10-30.
>
> | # | Hueco | Por defecto en este spec | Afecta |
> |---|-------|--------------------------|--------|
> | D1 | 3 y 11: ¿qué bloques se ven? | **Solo los bloques libres** del día, con `DisponibilidadService.horasLibres` de HU-08 **sin modificar** `DisponibilidadService`. Si no hay ninguno: "No hay bloques libres este día. Prueba con otra fecha.". Se muestran con `SelectorBloques` **sin cambiarlo** (sus props ya lo permiten: `bloques` con `libre: true` y sin `onCambiar` = solo lectura). | R13–R19, § 12 |
> | D2 | 5 y 6: ¿selección y botón "Reservar"? | La ficha es de **solo lectura**: no se eligen bloques ni hay botón "Reservar". Ambos los agrega HU-12 (Eduardo Saldaña) sobre esta misma ficha; el contrato que usará está en `design.md` § 13. | § 12, § 13 |
> | D3 | 2: ¿datos del arrendador? | **Ninguno**, igual que en HU-10 (ni nombre). | R7 |
> | D4 | 7: ¿galería? | Foto principal grande y miniaturas debajo para cambiarla, con un componente **`Galeria` nuevo** en el kit. | R5, R6, § 12 |
> | D5 | 1: ¿texto de la política de cancelación? | Las **tres reglas de RN-06**, como texto estático del frontend (la política es fija y no configurable). | § 12 (CA1) |
> | D6 | 4: ¿fecha inicial? | La de la búsqueda si viene en la URL (`/espacios/:id?fecha=AAAA-MM-DD`); si no, **hoy en Lima**. La fecha elegida se refleja en la URL. | § 12 (CA2) |
> | D7 | 8: ¿datos extra? | Se muestran el **tipo** (nombre legible) y la **zona** (nombre), además de la dirección. | R2, R4 |
> | D8 | 9: ¿espacio no disponible? | **404 genérico e indistinguible** (no existe, no publicado, de arrendador no activo, id mal escrito), con un enlace a "Buscar espacios". | R9–R12, R20, § 12 |

## Definiciones

- **Hoy / ahora local / ventana:** como en HU-08 y HU-10: fecha y hora en `America/Lima` del reloj del sistema
  (`ZonaHoraria`); ventana = de *hoy* a *hoy + 55 días*, ambas inclusive (`VentanaReserva`).
- **Espacio visible:** fila de `espacio` con `estado = PUBLICADO` cuyo arrendador tiene `usuario.estado = ACTIVA`
  (RN-15, HU-09 D1).
- **Id bien escrito:** segmento de ruta formado solo por 1 a 19 dígitos decimales cuyo valor es un entero de 1 a
  9 223 372 036 854 775 807 (`Long.MAX_VALUE`).
- **Fecha bien escrita:** texto `AAAA-MM-DD` que es una fecha de calendario existente (`2026-02-30` no).
- **Bloque libre en una fecha:** hora de inicio que `DisponibilidadService.horasLibres(espacioId, fecha)` devuelve
  (horario del día − fecha bloqueada − reservas activas del puerto `ReservasDelEspacio` − horas ya empezadas;
  RN-04).
- **URL de foto:** `/api/fotos/<UUID>`, la misma que arma HU-07 (`EspacioService.urlDe`).
- **FichaPublicaDTO:** `{ "id", "nombre", "tipo", "direccion", "zona", "zonaNombre", "capacidad", "precioHora",
  "descripcion", "fotos": [ URL de foto ] }`.
- **DisponibilidadPublicaDTO:** `{ "fecha", "bloquesLibres": [ { "horaInicio", "horaFin" } ] }`.
- **404 genérico:** respuesta 404 con cuerpo `ErrorDTO` de mensaje `ErrorDTO.MENSAJE_RECURSO_NO_ENCONTRADO`
  ("No encontramos lo que buscas o el enlace expiró.") y sin errores por campo.
- **Grupo catálogo / cupo del catálogo:** los de HU-10 (`LimiteCatalogo.GRUPO = "catalogo"`; por defecto 120
  peticiones aceptadas por IP en una ventana deslizante de 1 minuto, §9.9).
- **Mensajes de fecha** (los mismos de HU-08 y HU-10, `design.md` § 4): *fecha inválida* = "Elige una fecha
  válida."; *fuera de ventana* = "Elige una fecha entre hoy y las próximas 8 semanas.".

## Ficha pública (CA1, RF-C-04, RN-15)

## R1
CUANDO un cliente, con o sin sesión, envía `GET /api/espacios/{id}` con el id de un espacio visible, el sistema
DEBE responder 200 con el FichaPublicaDTO de ese espacio.

## R2
CUANDO el sistema responde según R1, el sistema DEBE devolver exactamente los campos `id`, `nombre`, `tipo`,
`direccion`, `zona`, `zonaNombre`, `capacidad`, `precioHora`, `descripcion` y `fotos`.

## R3
CUANDO el sistema responde según R1, el sistema DEBE devolver en `nombre`, `tipo` (código `CANCHA`, `ESTADIO` o
`AUDITORIO`), `direccion`, `zona` (código), `capacidad`, `precioHora` y `descripcion` los valores guardados en la
fila de `espacio`.

## R4
CUANDO el sistema responde según R1, el sistema DEBE devolver en `zonaNombre` el nombre de la fila de `zona` cuyo
código es el `zona` del espacio.

## R5
CUANDO el sistema responde según R1, el sistema DEBE devolver en `fotos` la URL de foto de cada foto del espacio,
ordenadas por `foto_espacio.orden` ascendente (como máximo 5, RNF-USA-02).

## R6
SI el espacio visible no tiene ninguna foto ENTONCES el sistema DEBE responder 200 según R1 con `fotos` igual a
una lista vacía.

## R7
CUANDO el sistema responde según R1, el sistema NO DEBE incluir en el cuerpo `arrendadorId`, `estado`,
`creadoEn`, `actualizadoEn`, la ruta de almacenamiento de las fotos ni dato alguno del arrendador (nombre, correo,
teléfono, número de documento, razón social o dirección fiscal).

## R8
SI una solicitud a `GET /api/espacios/{id}` o a `GET /api/espacios/{id}/disponibilidad` trae un encabezado
`Authorization: Bearer` con un token inexistente, revocado o vencido ENTONCES el sistema DEBE responder igual que
sin sesión, no 401.

## 404 indistinguible (CA1, RN-15, STRIDE I)

## R9
SI el id bien escrito de `GET /api/espacios/{id}` no corresponde a ningún espacio, o corresponde a un espacio en
estado `BORRADOR`, `PAUSADO`, `BAJA` u `OCULTO` ENTONCES el sistema DEBE responder el 404 genérico.

## R10
SI el id de `GET /api/espacios/{id}` corresponde a un espacio `PUBLICADO` cuyo arrendador tiene `usuario.estado`
`SUSPENDIDA` o `ELIMINADA` ENTONCES el sistema DEBE responder el 404 genérico.

## R11
SI el `{id}` de `GET /api/espacios/{id}` no es un id bien escrito (p. ej. `abc`, `0`, `-1`, `1.5`, `007a`,
`99999999999999999999`) ENTONCES el sistema DEBE responder el 404 genérico, no 400 ni 500.

## R12
CUANDO el sistema responde 404 según R9, R10, R11 o R20, el sistema DEBE devolver el mismo cuerpo en todos los
casos, sin distinguir la causa.

## Bloques libres del día (CA2, RF-C-05, RN-04)

## R13
CUANDO un cliente, con o sin sesión, envía `GET /api/espacios/{id}/disponibilidad?fecha=F` con el id de un
espacio visible y una fecha `F` bien escrita dentro de la ventana, el sistema DEBE responder 200 con un
DisponibilidadPublicaDTO con `fecha` igual a `F`.

## R14
CUANDO el sistema responde según R13, el sistema DEBE devolver en `bloquesLibres` un elemento por cada bloque
libre del espacio en `F`, en orden ascendente de `horaInicio`, con `horaFin = horaInicio + 1`.

## R15
CUANDO el sistema responde según R13 y la fecha `F` está en `bloqueo_fecha` para ese espacio, el sistema DEBE
devolver `bloquesLibres` vacía.

## R16
CUANDO el sistema responde según R13, el sistema DEBE excluir de `bloquesLibres` toda hora que el puerto
`ReservasDelEspacio` informa como reservada para ese espacio en `F`.

## R17
MIENTRAS la fecha `F` es *hoy*, el sistema DEBE excluir de `bloquesLibres` todo bloque cuya hora de inicio no es
posterior a la *ahora local*.

## R18
SI el espacio visible no tiene franjas en `horario_semanal` para el día de la semana ISO de `F` ENTONCES el
sistema DEBE responder 200 según R13 con `bloquesLibres` vacía.

## R19
CUANDO el sistema responde según R13, el sistema DEBE devolver solo los campos `fecha` y `bloquesLibres`, y en
cada bloque solo `horaInicio` y `horaFin`, sin bloques ocupados ni el motivo por el que una hora no está libre
(reservada, bloqueada o pasada).

## R20
SI el `{id}` de `GET /api/espacios/{id}/disponibilidad` no es un id bien escrito o no corresponde a un espacio
visible ENTONCES el sistema DEBE responder el 404 genérico sin calcular la disponibilidad.

## R21
SI el `{id}` de `GET /api/espacios/{id}/disponibilidad` corresponde a un espacio visible y `fecha` falta, está
vacía o no es una fecha bien escrita ENTONCES el sistema DEBE responder 400 con un único error en el campo
`fecha` y el mensaje *fecha inválida*.

## R22
SI el `{id}` de `GET /api/espacios/{id}/disponibilidad` corresponde a un espacio visible y `fecha` es una fecha
bien escrita fuera de la ventana ENTONCES el sistema DEBE responder 400 con un único error en el campo `fecha` y
el mensaje *fuera de ventana*.

## R23
SI `GET /api/espacios/{id}/disponibilidad` trae a la vez un `{id}` que no corresponde a un espacio visible y una
`fecha` inválida ENTONCES el sistema DEBE responder el 404 genérico (no 400).

## Respuestas sin caché ni auditoría

## R24
CUANDO el sistema responde según R1 o R13, el sistema DEBE incluir el encabezado `Cache-Control` con la
directiva `no-store`.

## R25
CUANDO el sistema responde `GET /api/espacios/{id}` o `GET /api/espacios/{id}/disponibilidad` con cualquier
código, el sistema NO DEBE registrar ningún evento en la tabla `auditoria`.

## Rate limiting compartido con HU-10 (STRIDE D, RNF-SEG-17, §9.9)

## R26
MIENTRAS una IP tiene agotado el cupo del catálogo, el sistema DEBE responder 429 con
`ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS` a cada `GET /api/espacios/{id}` de esa IP, sin consultar el espacio.

## R27
MIENTRAS una IP tiene agotado el cupo del catálogo, el sistema DEBE responder 429 con
`ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS` a cada `GET /api/espacios/{id}/disponibilidad` de esa IP, sin calcular la
disponibilidad.

## R28
CUANDO el filtro acepta una `GET /api/espacios/{id}` o una `GET /api/espacios/{id}/disponibilidad`, el sistema
DEBE descontarla del mismo cupo por IP que `GET /api/espacios` (búsqueda de HU-10).

## R29
CUANDO el filtro acepta una `GET /api/espacios/{id}` o una `GET /api/espacios/{id}/disponibilidad`, el sistema
DEBE contarla en el cupo del catálogo aunque la respuesta sea 404 o 400.

## R30
El sistema DEBE registrar las reglas de `GET /api/espacios/{id}` y de `GET /api/espacios/{id}/disponibilidad`
como patrones de ruta del grupo catálogo creados con `LimiteCatalogo.regla`.

## Servicio público de `espacios` (RN-15, HU-09 D1; reutilizable por HU-12, HU-23, HU-24)

## R31
CUANDO otro módulo llama a `CatalogoEspaciosService.publicado(espacioId)` con el id de un espacio visible, el
sistema DEBE devolver un `FichaPublicada` con sus datos y las URL de sus fotos en orden ascendente.

## R32
SI `CatalogoEspaciosService.publicado(espacioId)` recibe `null`, el id de un espacio inexistente, no `PUBLICADO`
o de un arrendador no `ACTIVA` ENTONCES el sistema DEBE devolver un `Optional` vacío.

## R33
CUANDO otro módulo llama a `CatalogoEspaciosService.esVisible(espacioId)`, el sistema DEBE devolver `true` si y
solo si el id corresponde a un espacio visible.

## Trazabilidad con la historia

| Origen | Cubierto por |
|--------|--------------|
| CA1: fotos, descripción, dirección en texto, capacidad, precio por hora | R1, R2, R3, R4, R5, R6, R31 |
| CA1: política de cancelación (48 h) | Texto estático (D5, RN-06): `design.md` § 12; se verifica con lint, build y caja negra en UAT. La API no la devuelve porque es fija y no configurable |
| CA1: "abro un espacio" (solo los visibles; los demás, 404 genérico) | R9, R10, R11, R12, R32, R33 |
| CA2: elijo/cambio la fecha y veo los bloques libres de ese día | R13, R14, R15, R16, R17, R18, R20, R21, R22, R23 (la presentación y la recarga al cambiar la fecha, en `design.md` § 12) |
| (I) No se muestran teléfono, correo ni documentos del arrendador | R7, R19, R12, R2 |
| (D) Rate limiting compartido con HU-10 | R26, R27, R28, R29, R30 |
| Acceso público (con o sin sesión) | R1, R8, R13 |
| Lecturas públicas sin caché ni auditoría (DT-9, DT-10) | R24, R25 |
| RF-C-04 | R1–R7 |
| RF-C-05 | R13–R18 |
| RNF-SEG-16 (minimización) | R2, R7, R19 |
| RNF-SEG-17 (rate limiting compartido, §9.9) | R26–R30 |
| RN-04 (bloques libres = horario − bloqueos − reservas, ventana de 8 semanas) | R14, R15, R16, R17, R18, R22 |
| RN-06 (política fija) | D5, `design.md` § 12 |
| RN-15 (solo publicados de arrendadores no suspendidos) | R9, R10, R20, R32, R33 |
