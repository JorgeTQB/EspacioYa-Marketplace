# HU-10 — Requirements: Búsqueda por tipo, zona y fecha

> Fuente: `docs/historias/HU-10.md` (CA1–CA3, STRIDE I/D e I), `docs/requerimientos.md` (RF-C-01…03,
> RNF-SEG-16, RNF-SEG-17, RNF-USA-01, RN-04, RN-15, §8 "Rate limiting" y "Cálculo de disponibilidad",
> **§9 punto 9** resuelto por Sebastian Garate el 2026-10-29), `docs/architecture.md` (módulo `busqueda`,
> dirección `busqueda → espacios`, regla HU-09 D1 de arrendadores no `ACTIVA`, `DisponibilidadService`,
> "Disponibilidad: qué deja HU-08 y qué falta", Decorator de HU-23), `docs/modelo-datos.md`,
> `specs/HU-07/` (zona por código, foto principal = orden 1, R8 y R43 de `GET /api/zonas`),
> `specs/HU-08/` (`VentanaReserva`, `ZonaHoraria`, `horasLibres`, puerto `ReservasDelEspacio`) y
> `progress/explore_HU-10.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**, del servicio público
> de `espacios` y del rate limiting de `comun`; la presentación está en `design.md` § 12 Pantallas.
> Las decisiones técnicas y las que debe confirmar el dueño están en `design.md` § 1 y § 15.

## Decisiones por defecto que el dueño debe confirmar al aprobar

> ⚠ Las fuentes no fijan estos puntos. El spec aplica el valor indicado; si **Sebastian Garate** no lo
> acepta, cambian los requirements citados antes de pasar a `in_progress`. Detalle y alternativas en
> `design.md` § 15 "Decisiones abiertas para el dueño".
>
> | # | Pregunta | Por defecto en este spec | Afecta |
> |---|----------|--------------------------|--------|
> | P1 | ¿Ruta de la búsqueda? | `GET /api/espacios?tipo=&zona=&fecha=&pagina=`, pública (sin sesión). HU-11 usará `/api/espacios/{id}`. | R1, R12 |
> | P2 | ¿El catálogo de zonas es público? | **Sí**: `GET /api/zonas` deja de exigir sesión (la pantalla pública llena su select). Reemplaza la parte de `GET /api/zonas` de HU-07 R43. | R13 |
> | P3 | ¿Forma de la paginación? | `pagina` desde 1 (por defecto 1), **tamaño fijo 20** no configurable por el cliente; respuesta con `pagina`, `resultados` y `hayMas`, **sin total** (precedente HU-09). | R17–R22 |
> | P4 | ¿Orden de los resultados? | `precioHora` ascendente y, a igual precio, `id` ascendente (estable para paginar). | R10 |
> | P5 | ¿Fecha inválida o fuera de las 8 semanas? | **400** en el campo `fecha`, con los mismos textos de HU-08. | R25, R26 |
> | P6 | ¿Tipo o zona inexistentes, o ausentes? | **400** en su campo. Los tres filtros son obligatorios (CA1). | R23, R24, R28 |
> | P7 | ¿Cuentan para el cupo las búsquedas que dan 400? | **Sí**: cuenta toda petición aceptada por el filtro, como en el registro (§9.7); las rechazadas con 429 no. | R37 |
> | P8 | ¿Dónde se configuran cifra y ventana? | `application.properties` (el proyecto no tiene `application.yml`): `espacioya.limite.catalogo.maximo=${LIMITE_CATALOGO_MAXIMO:120}` y `espacioya.limite.catalogo.ventana=${LIMITE_CATALOGO_VENTANA:PT1M}`. | R39 |
> | P9 | ¿Espacio publicado sin foto con orden 1? | No debería ocurrir (publicar exige foto, HU-07 DT-3); si ocurre, se muestra con `fotoPrincipal = null`. | R16 |

## Definiciones

- **Hoy / ahora local / ventana:** como en HU-08: fecha y hora en `America/Lima` del reloj del sistema
  (`ZonaHoraria`); ventana = de *hoy* a *hoy + 55 días*, ambas inclusive (`VentanaReserva`).
- **Tipo válido:** el texto exacto `CANCHA`, `ESTADIO` o `AUDITORIO` (sensible a mayúsculas).
- **Zona válida:** un `codigo` existente en la tabla `zona` (sensible a mayúsculas).
- **Fecha bien escrita:** texto `AAAA-MM-DD` que es una fecha de calendario existente (`2026-02-30` no).
- **Página válida:** parámetro `pagina` ausente, o texto formado solo por dígitos decimales cuyo valor es
  un entero de 1 a 2 147 483 647.
- **Búsqueda válida:** `tipo` válido, `zona` válida, `fecha` bien escrita dentro de la ventana y página
  válida.
- **Espacio visible:** fila de `espacio` con `estado = PUBLICADO` cuyo arrendador tiene
  `usuario.estado = ACTIVA` (RN-15, HU-09 D1).
- **Bloque libre en una fecha:** hora de inicio que `DisponibilidadService.horasLibres(espacioId, fecha)`
  devuelve (horario del día − fecha bloqueada − reservas activas del puerto − horas ya empezadas; RN-04).
- **Espacio disponible para una búsqueda:** espacio visible, con el tipo y la zona pedidos y con al menos
  un bloque libre en la fecha pedida.
- **Lista ordenada:** todos los espacios disponibles para la búsqueda, ordenados por `precio_hora`
  ascendente y luego por `id` ascendente (⚠ P4).
- **Tamaño de página:** 20 (`TAMANO_PAGINA`, ⚠ P3).
- **ResultadoBusquedaDTO:** `{ "pagina", "resultados": [ ResultadoEspacioDTO ], "hayMas" }`.
- **ResultadoEspacioDTO:** `{ "id", "nombre", "zona", "zonaNombre", "precioHora", "fotoPrincipal" }`.
- **URL de foto:** `/api/fotos/<UUID>`, la misma que arma HU-07 (`EspacioService.urlDe`).
- **Grupo catálogo:** el `grupo` `"catalogo"` de `ReglaLimite`; un solo cupo por IP para la búsqueda
  (HU-10), la ficha y su disponibilidad (HU-11) y los filtros (HU-23) (§9.9).
- **Cupo del catálogo:** `maximo` peticiones aceptadas por IP en una ventana deslizante de duración
  `ventana`; por defecto 120 y 1 minuto (⚠ P8).
- **Mensajes** (texto exacto en `design.md` § 4): *tipo inválido*, *zona inválida*, *fecha inválida*,
  *fuera de ventana*, *página inválida*, *validación* (`ErrorDTO.MENSAJE_VALIDACION`), *demasiados
  intentos* (`ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS`).

## Búsqueda (CA1, RF-C-01, RN-04, RN-15)

## R1
CUANDO un cliente, con o sin sesión, envía `GET /api/espacios` con una búsqueda válida, el sistema
DEBE responder 200 con un ResultadoBusquedaDTO.

## R2
CUANDO el sistema responde según R1, el sistema DEBE incluir en `resultados` solo espacios cuyo `tipo`
y cuya `zona` son los pedidos.

## R3
CUANDO el sistema responde según R1, el sistema DEBE excluir de `resultados` todo espacio cuyo estado
es `BORRADOR`, `PAUSADO`, `BAJA` u `OCULTO`.

## R4
CUANDO el sistema responde según R1, el sistema DEBE excluir de `resultados` todo espacio `PUBLICADO`
cuyo arrendador tiene `usuario.estado` `SUSPENDIDA` o `ELIMINADA`.

## R5
CUANDO el sistema responde según R1, el sistema DEBE excluir de `resultados` todo espacio que no tiene
franjas en `horario_semanal` para el día de la semana ISO de la fecha pedida.

## R6
CUANDO el sistema responde según R1, el sistema DEBE excluir de `resultados` todo espacio que tiene la
fecha pedida en `bloqueo_fecha`.

## R7
CUANDO el sistema responde según R1, el sistema DEBE excluir de `resultados` todo espacio cuyas horas
del día pedido están todas retenidas por reservas activas que informa el puerto `ReservasDelEspacio`.

## R8
MIENTRAS la fecha pedida es *hoy*, el sistema DEBE excluir de `resultados` todo espacio cuyos bloques de
ese día tienen todos una hora de inicio que no es posterior a la *ahora local*.

## R9
CUANDO el sistema responde según R1, el sistema DEBE devolver cada espacio disponible para la búsqueda
en exactamente una de las páginas `1, 2, …` (ninguno falta ni se repite entre páginas).

## R10
CUANDO el sistema responde según R1, el sistema DEBE ordenar `resultados` según la lista ordenada
(`precioHora` ascendente y, a igual precio, `id` ascendente).

## R11
SI no hay ningún espacio disponible para una búsqueda válida ENTONCES el sistema DEBE responder 200
con `resultados` vacío y `hayMas = false`.

## R12
SI la solicitud de R1 trae un encabezado `Authorization: Bearer` con un token inexistente, revocado o
vencido ENTONCES el sistema DEBE responder igual que sin sesión (200 según R1), no 401.

## R13
CUANDO un cliente sin sesión envía `GET /api/zonas`, el sistema DEBE responder 200 con las 50 zonas,
cada una con solo `codigo` y `nombre`, en el mismo orden que HU-07 R8.

## Contenido de cada resultado (CA2, RF-C-02, RNF-SEG-16)

## R14
CUANDO el sistema responde según R1, el sistema DEBE devolver cada elemento de `resultados` con
exactamente los campos `id`, `nombre`, `zona` (código), `zonaNombre`, `precioHora` y `fotoPrincipal`,
sin dirección, descripción, capacidad, estado, `arrendadorId` ni dato alguno del arrendador.

## R15
CUANDO el espacio de un elemento de `resultados` tiene una foto con `orden = 1`, el sistema DEBE poner
en `fotoPrincipal` la URL de foto de esa foto.

## R16
SI el espacio de un elemento de `resultados` no tiene foto con `orden = 1` ENTONCES el sistema DEBE
devolver `fotoPrincipal = null` sin omitir el espacio.

## Paginación (CA2, RNF-USA-01, STRIDE I/D)

## R17
CUANDO el sistema responde según R1, el sistema DEBE devolver como máximo 20 elementos en `resultados`.

## R18
CUANDO un cliente envía una búsqueda válida con `pagina = n`, el sistema DEBE devolver en `resultados`
los elementos de las posiciones `(n − 1)·20 + 1` a `n·20` de la lista ordenada que existan.

## R19
CUANDO un cliente envía una búsqueda válida sin el parámetro `pagina`, el sistema DEBE responder la
página 1 con `pagina = 1`.

## R20
CUANDO el sistema responde según R1 la página `n`, el sistema DEBE devolver `hayMas = true` si y solo si
la lista ordenada tiene más de `n·20` elementos.

## R21
SI una búsqueda válida pide una página posterior a la última con resultados ENTONCES el sistema DEBE
responder 200 con `resultados` vacío, `hayMas = false` y `pagina` igual a la pedida.

## R22
SI la solicitud de R1 trae parámetros adicionales (p. ej. `tamano`, `size`, `limit`) ENTONCES el sistema
DEBE ignorarlos y seguir devolviendo como máximo 20 elementos.

## Validación de la entrada

## R23
SI `tipo` falta, está vacío o no es un tipo válido ENTONCES el sistema DEBE responder 400 con un error
en el campo `tipo` y el mensaje *tipo inválido*.

## R24
SI `zona` falta, está vacía o no es una zona válida ENTONCES el sistema DEBE responder 400 con un error
en el campo `zona` y el mensaje *zona inválida*.

## R25
SI `fecha` falta o no es una fecha bien escrita ENTONCES el sistema DEBE responder 400 con un error en
el campo `fecha` y el mensaje *fecha inválida*.

## R26
SI `fecha` es una fecha bien escrita fuera de la ventana ENTONCES el sistema DEBE responder 400 con un
error en el campo `fecha` y el mensaje *fuera de ventana*.

## R27
SI `pagina` está presente y no es una página válida (p. ej. `0`, `-1`, `1.5`, `abc`, `""`,
`99999999999`) ENTONCES el sistema DEBE responder 400 con un error en el campo `pagina` y el mensaje
*página inválida*.

## R28
SI varios parámetros son inválidos a la vez ENTONCES el sistema DEBE responder 400 con
`mensaje = MENSAJE_VALIDACION` y un error por cada campo inválido, en el orden `tipo`, `zona`, `fecha`,
`pagina`.

## R29
SI la búsqueda no es válida ENTONCES el sistema NO DEBE calcular la disponibilidad de ningún espacio.

## Servicio público de `espacios` (RN-04, RN-15; reutilizable por HU-11, HU-23)

## R30
CUANDO otro módulo llama a `DisponibilidadService.conBloquesLibres(espacioIds, fecha)`, el sistema DEBE
devolver exactamente los ids de `espacioIds` para los que `horasLibres(id, fecha)` no es vacía.

## R31
CUANDO se ejecuta `conBloquesLibres` con una colección no vacía, el sistema DEBE consultar el horario,
los bloqueos y el puerto `ReservasDelEspacio` como máximo una vez cada uno para todo el lote, sin
importar cuántos ids reciba.

## R32
SI `conBloquesLibres` recibe una colección vacía o una fecha fuera de la ventana ENTONCES el sistema
DEBE devolver un conjunto vacío sin consultar repositorios ni el puerto.

## Rate limiting por IP (STRIDE I/D, RNF-SEG-17, §9.9)

## R33
MIENTRAS una IP tiene `maximo` peticiones aceptadas del grupo catálogo dentro de la última `ventana`,
el sistema DEBE responder 429 con `ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS` a cada `GET /api/espacios` de
esa IP, sin ejecutar la búsqueda.

## R34
CUANDO la petición aceptada más antigua de una IP sale de la ventana deslizante, el sistema DEBE volver
a aceptar una `GET /api/espacios` de esa IP.

## R35
MIENTRAS una IP tiene agotado el cupo del catálogo, el sistema DEBE seguir aceptando las
`GET /api/espacios` de otra IP que no lo tiene agotado.

## R36
CUANDO una petición recibe 429 por el cupo del catálogo, el sistema NO DEBE contarla como petición
aceptada del cupo.

## R37
CUANDO el filtro acepta una `GET /api/espacios`, el sistema DEBE contarla en el cupo del catálogo
aunque la búsqueda termine en 400.

## R38
MIENTRAS una IP tiene agotado el cupo del catálogo, el sistema DEBE responder `GET /api/zonas` y
`GET /api/fotos/<UUID>` como si no hubiera límite, sin descontarlas del cupo.

## R39
El sistema DEBE tomar `maximo` de `espacioya.limite.catalogo.maximo` (120 si no se define) y `ventana`
de `espacioya.limite.catalogo.ventana` (1 minuto si no se define).

## R40
SI `espacioya.limite.catalogo.maximo` es menor que 1 o `espacioya.limite.catalogo.ventana` no es una
duración positiva ENTONCES el sistema DEBE fallar al arrancar con `IllegalStateException`.

## R41
El sistema DEBE registrar la regla de `GET /api/espacios` en el grupo catálogo, de modo que toda regla
creada con el mismo grupo (HU-11, HU-23) comparta la clave de cupo por IP.

## Extensión de `ReglaLimite` a patrones de ruta (§9.9)

## R42
CUANDO la ruta de una `ReglaLimite` contiene variables de ruta (`{id}`) o comodines (`*`), el sistema
DEBE considerar que la regla aplica a toda ruta de solicitud que coincide con ese patrón según
`PathPattern` de Spring (p. ej. `/api/espacios/{id}` aplica a `/api/espacios/7` y no a `/api/espacios`
ni a `/api/espacios/7/fotos`).

## R43
CUANDO la ruta de una `ReglaLimite` es literal, el sistema DEBE considerar que la regla aplica solo a
esa misma ruta, con el método comparado sin distinguir mayúsculas (comportamiento de HU-01/HU-02).

## R44
SI la ruta de la solicitud escribe con codificación porcentual caracteres no reservados de una ruta
limitada (p. ej. `/api/%65spacios`) ENTONCES el sistema DEBE aplicarle la misma regla que a la ruta sin
codificar.

## R45
SI la ruta de una `ReglaLimite` no es un patrón válido ENTONCES el sistema DEBE lanzar
`IllegalArgumentException` al construir la regla.

## R46
CUANDO una solicitud coincide con varias reglas del mismo grupo, el sistema DEBE descontar un solo
intento de ese grupo.

## R47
MIENTRAS rigen las reglas de HU-10, el sistema DEBE seguir limitando `POST /api/usuarios` y
`POST /api/arrendadores` a 5 intentos por IP por hora en el grupo `registro` compartido (HU-01, HU-02).

## Trazabilidad con la historia

| Origen | Cubierto por |
|--------|--------------|
| CA1: solo publicados de arrendadores no suspendidos con al menos un bloque libre esa fecha | R1, R2, R3, R4, R5, R6, R7, R8, R9, R12, R13, R23, R24, R25, R26, R28, R30, R31, R32 |
| CA2: nombre, foto principal, precio por hora y zona, en páginas de 20 | R10, R14, R15, R16, R17, R18, R19, R20, R21, R27 |
| CA3: sin resultados, mensaje y sugerencia de cambiar fecha o zona | R11 (la presentación, en `design.md` § 12) |
| (I/D) scraping: rate limiting por IP | R33, R34, R35, R36, R37, R38, R39, R40, R41, R42, R43, R44, R45, R46, R47 |
| (I/D) scraping: máximo 20 resultados por página | R17, R22, R29 |
| (I) riesgo aceptado: catálogo público, sin datos personales del arrendador | R12, R13, R14 |
| RF-C-01 | R1–R9 |
| RF-C-02 | R14–R20 |
| RF-C-03 | R11 |
| RNF-SEG-16 (minimización) | R14 |
| RNF-SEG-17 (rate limiting compartido, §9.9) | R33–R47 |
| RNF-USA-01 (páginas de 20) | R17–R21 |
| RN-04 (bloques libres = horario − bloqueos − reservas) | R5, R6, R7, R8, R30 |
| RN-15 (solo publicados de no suspendidos) | R3, R4, R30 |
