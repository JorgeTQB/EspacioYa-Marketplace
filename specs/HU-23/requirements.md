# HU-23 — Requirements: Filtros de búsqueda por precio, capacidad y características

> Fuente: `docs/historias/HU-23.md` (CA1–CA3, STRIDE T y D), `docs/requerimientos.md` (RF-B-08, RF-C-06,
> RF-C-07, RNF-SEG-10, RNF-SEG-17, RN-15, RN-18, §9 punto 9 resuelto), `docs/architecture.md` (módulo
> `busqueda`, `CatalogoEspaciosService`, cupo del catálogo, fila HU-23 ★ Decorator de "Patrones por
> historia"), `docs/modelo-datos.md` (`caracteristica` / `espacio_caracteristica`), `specs/HU-07/`
> (ficha del espacio, auditoría `EDICION_ESPACIO`, estados editables), `specs/HU-10/` (R1–R47, interfaz
> envolvible `BusquedaEspacios`) y `progress/explore_HU-23.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API** y del servicio público de
> `espacios`; la presentación está en `design.md` § 11 Pantallas y el patrón Decorator en `design.md` § 6.

## Decisiones por defecto que el dueño debe confirmar al aprobar

> ⚠ Las fuentes no fijan estos puntos. El spec aplica el valor indicado; si **Sebastian Garate** no lo
> acepta, cambian los requirements citados antes de pasar a `in_progress`. Detalle y alternativas en
> `design.md` § 15 "Decisiones abiertas para el dueño".
>
> | # | Pregunta | Por defecto en este spec | Afecta |
> |---|----------|--------------------------|--------|
> | P1 | ¿Qué significa un `PUT` de la ficha sin `caracteristicas` (o con `null`)? | Lista vacía: el `PUT` reemplaza la ficha completa, también las características (HU-07). | R3 |
> | P2 | ¿Nombres y forma de los filtros en la URL? | `precioMaximo`, `capacidadMinima` y `caracteristica` **repetido** una vez por valor (`?caracteristica=TECHADO&caracteristica=VESTUARIOS`), sobre la misma `GET /api/espacios` de HU-10. | R12–R27 |
> | P3 | ¿Los límites son inclusivos? | Sí: `precioHora ≤ precioMaximo` y `capacidad ≥ capacidadMinima`. | R12, R13 |
> | P4 | ¿Rango y formato válidos de cada filtro? | Los mismos de la ficha (HU-07): precio de S/ 1.00 a S/ 100 000.00 con hasta 2 decimales; capacidad entera de 1 a 100 000. | R20, R21 |
> | P5 | ¿Un filtro presente pero vacío (`?precioMaximo=`)? | Inválido (400 en su campo), como `pagina` en HU-10 R27. La pantalla no envía los filtros vacíos. | R20–R22 |
> | P6 | ¿Una característica repetida en la ficha o en la búsqueda? | 400 en su campo; no se colapsa en silencio (lista blanca estricta, STRIDE T). | R5, R23 |
> | P7 | ¿El resultado y la ficha pública muestran capacidad o características? | **No**: `ResultadoEspacioDTO` sigue con los seis campos de HU-10 R14 y `FichaPublicaDTO` (HU-11) no cambia. Ningún CA lo pide. | R18 |
> | P8 | ¿Catálogo de características? | Cerrado y sembrado por la migración: `TECHADO` (Techado), `ILUMINACION` (Iluminación), `VESTUARIOS` (Vestuarios), `ESTACIONAMIENTO` (Estacionamiento), en ese orden. Tabla `caracteristica` con PK `codigo` (como `zona`), no `id`. | R1–R11, R30 |

## Definiciones

- **Lista fija de características:** los códigos `TECHADO`, `ILUMINACION`, `VESTUARIOS` y `ESTACIONAMIENTO`,
  exactamente así (sensibles a mayúsculas, sin espacios), en ese **orden fijo** (⚠ P8).
- **Código de característica válido:** un texto igual a uno de los cuatro códigos de la lista fija.
- **Lista de características válida (ficha):** valor JSON ausente, `null` o un arreglo de textos en el que cada
  elemento es un código de característica válido y ninguno se repite (de 0 a 4 elementos).
- **Arrendador habilitado / espacio propio / espacio editable:** como en HU-07: rol `ARRENDADOR` con correo
  verificado; espacio cuyo `arrendador_id` es el de la sesión; estado `BORRADOR`, `PUBLICADO` o `PAUSADO`.
- **Búsqueda válida:** la de HU-10 (`tipo`, `zona`, `fecha` y `pagina` válidos).
- **Filtros:** los parámetros de consulta opcionales `precioMaximo`, `capacidadMinima` y `caracteristica`
  (este último repetible) de `GET /api/espacios` (⚠ P2). Un filtro está **presente** si su parámetro aparece en
  la URL, aunque sea vacío.
- **Precio máximo válido:** texto formado por 1 a 6 dígitos decimales, opcionalmente seguido de un punto y 1 o 2
  dígitos, cuyo valor está entre 1.00 y 100 000.00 inclusive (⚠ P4, P5).
- **Capacidad mínima válida:** texto formado solo por 1 a 6 dígitos decimales cuyo valor entero está entre 1 y
  100 000 inclusive (⚠ P4, P5).
- **Características pedidas válidas:** todas las apariciones de `caracteristica` son un código de
  característica válido, sin comas ni espacios, y ningún código aparece dos veces (⚠ P6).
- **Búsqueda filtrada válida:** búsqueda válida en la que cada filtro presente es válido.
- **Espacio disponible para una búsqueda:** como en HU-10 (visible según RN-15, con el tipo y la zona pedidos y
  al menos un bloque libre en la fecha pedida).
- **Espacio que cumple los filtros:** espacio disponible cuyo `precio_hora` es menor o igual que el precio
  máximo (si está presente), cuya `capacidad` es mayor o igual que la capacidad mínima (si está presente) y que
  tiene **todas** las características pedidas (si hay alguna) (⚠ P3).
- **Lista filtrada:** todos los espacios que cumplen los filtros, en el orden de HU-10 (`precioHora`
  ascendente y luego `id` ascendente).
- **EspacioDTO:** la respuesta de las rutas del arrendador de HU-07 y HU-21
  (`/api/arrendadores/actual/espacios/**`), que gana el campo `caracteristicas`.
- **Mensajes** (texto exacto en `design.md` § 4): *característica de ficha inválida*, *precio máximo
  inválido*, *capacidad mínima inválida*, *característica inválida*, *no editable* (HU-07,
  `EspacioService.MENSAJE_ESTADO_NO_EDITABLE`), *validación* (`ErrorDTO.MENSAJE_VALIDACION`), *demasiados
  intentos* (`ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS`).

## Características del espacio (CA1, RF-B-08)

## R1
CUANDO un arrendador habilitado envía `POST /api/arrendadores/actual/espacios` con una ficha válida de HU-07 y
una lista de características válida, el sistema DEBE crear el espacio con exactamente esas características.

## R2
CUANDO un arrendador habilitado envía `PUT /api/arrendadores/actual/espacios/{id}` de un espacio propio y
editable con una ficha válida y una lista de características válida, el sistema DEBE reemplazar las
características del espacio por exactamente las enviadas.

## R3
SI el cuerpo de R1 o R2 no trae `caracteristicas` o lo trae en `null` ENTONCES el sistema DEBE dejar el espacio
sin ninguna característica.

## R4
SI `caracteristicas` del cuerpo de R1 o R2 contiene un elemento que no es un código de característica válido
(p. ej. `"techado"`, `"PISCINA"`, `""`, `null`) ENTONCES el sistema DEBE responder 400 con un error en el
campo `caracteristicas` y el mensaje *característica de ficha inválida*, sin crear ni modificar el espacio.

## R5
SI `caracteristicas` del cuerpo de R1 o R2 contiene el mismo código más de una vez ENTONCES el sistema DEBE
responder 400 con un error en el campo `caracteristicas` y el mensaje *característica de ficha inválida*, sin
crear ni modificar el espacio.

## R6
CUANDO el sistema responde con un EspacioDTO, el sistema DEBE incluir el campo `caracteristicas` con los
códigos del espacio en el orden de la lista fija, o una lista vacía si no tiene ninguna.

## R7
CUANDO una edición según R2 cambia el conjunto de características del espacio, el sistema DEBE incluir en el
detalle de la auditoría `EDICION_ESPACIO` el cambio `caracteristicas=[<anteriores>]->[<nuevas>]`, con los
códigos en el orden de la lista fija separados por `, ` (p. ej. `caracteristicas=[]->[TECHADO, VESTUARIOS]`).

## R8
SI una edición según R2 envía el mismo conjunto de características que el espacio ya tiene, en cualquier orden,
y ningún otro campo de la ficha cambia ENTONCES el sistema NO DEBE registrar auditoría ni cambiar
`actualizado_en`.

## R9
SI el espacio de R2 está en estado `BAJA` u `OCULTO` ENTONCES el sistema DEBE responder 400 con el mensaje
*no editable* sin cambiar sus características.

## R10
El sistema DEBE rechazar en la base de datos (llave foránea y llave primaria de `espacio_caracteristica`) toda
fila que referencia un código fuera del catálogo `caracteristica` o que repite el par (espacio, característica).

## R11
CUANDO se borra una fila de `espacio`, el sistema DEBE borrar en cascada sus filas de `espacio_caracteristica`.

## Filtros de la búsqueda (CA2, RF-C-06, RN-15)

## R12
CUANDO un cliente, con o sin sesión, envía `GET /api/espacios` con una búsqueda filtrada válida que incluye
`precioMaximo`, el sistema DEBE incluir en `resultados` solo espacios cuyo `precioHora` es menor o igual que
`precioMaximo`.

## R13
CUANDO un cliente envía `GET /api/espacios` con una búsqueda filtrada válida que incluye `capacidadMinima`, el
sistema DEBE incluir en `resultados` solo espacios cuya capacidad es mayor o igual que `capacidadMinima`.

## R14
CUANDO un cliente envía `GET /api/espacios` con una búsqueda filtrada válida que incluye uno o más
`caracteristica`, el sistema DEBE incluir en `resultados` solo espacios que tienen todas las características
pedidas.

## R15
CUANDO un cliente envía `GET /api/espacios` con una búsqueda filtrada válida que combina dos o tres filtros, el
sistema DEBE devolver exactamente los espacios disponibles para la búsqueda (tipo, zona, fecha y visibilidad de
HU-10 R2–R8) que cumplen todos los filtros presentes, sin importar el orden en que se apliquen.

## R16
CUANDO un cliente envía `GET /api/espacios` con una búsqueda válida sin ningún filtro, el sistema DEBE
responder exactamente lo mismo que HU-10 (R1–R22).

## R17
CUANDO el sistema responde una búsqueda filtrada válida, el sistema DEBE ordenar y paginar sobre la lista
filtrada: los elementos de las posiciones `(n − 1)·20 + 1` a `n·20` en el orden de HU-10 y `hayMas = true` si y
solo si la lista filtrada tiene más de `n·20` elementos.

## R18
CUANDO el sistema responde una búsqueda filtrada válida, el sistema DEBE devolver cada elemento de `resultados`
con exactamente los campos de HU-10 R14 (`id`, `nombre`, `zona`, `zonaNombre`, `precioHora`, `fotoPrincipal`),
sin capacidad ni características.

## Sin resultados (CA3, RF-C-07)

## R19
SI ningún espacio cumple los filtros de una búsqueda filtrada válida ENTONCES el sistema DEBE responder 200 con
`resultados` vacío, `hayMas = false` y `pagina` igual a la pedida.

## Lista blanca de los filtros (STRIDE T, RNF-SEG-10)

## R20
SI `precioMaximo` está presente y no es un precio máximo válido (p. ej. `""`, `0`, `0.99`, `100000.01`,
`12.345`, `1e3`, `-5`, `abc`) ENTONCES el sistema DEBE responder 400 con un error en el campo `precioMaximo` y
el mensaje *precio máximo inválido*.

## R21
SI `capacidadMinima` está presente y no es una capacidad mínima válida (p. ej. `""`, `0`, `100001`, `2.5`,
`-1`, `abc`) ENTONCES el sistema DEBE responder 400 con un error en el campo `capacidadMinima` y el mensaje
*capacidad mínima inválida*.

## R22
SI alguna aparición de `caracteristica` no es un código de característica válido (p. ej. `""`, `techado`,
`PISCINA`, `TECHADO,VESTUARIOS`) ENTONCES el sistema DEBE responder 400 con un error en el campo
`caracteristica` y el mensaje *característica inválida*.

## R23
SI el mismo código de `caracteristica` aparece más de una vez en la solicitud ENTONCES el sistema DEBE
responder 400 con un error en el campo `caracteristica` y el mensaje *característica inválida*.

## R24
SI varios parámetros de la búsqueda son inválidos a la vez ENTONCES el sistema DEBE responder 400 con
`mensaje = MENSAJE_VALIDACION` y un solo error por cada campo inválido, en el orden `tipo`, `zona`, `fecha`,
`pagina`, `precioMaximo`, `capacidadMinima`, `caracteristica`.

## R25
SI algún filtro presente es inválido ENTONCES el sistema NO DEBE calcular la disponibilidad de ningún espacio.

## R26
SI la solicitud trae parámetros cuyo nombre no es `tipo`, `zona`, `fecha`, `pagina`, `precioMaximo`,
`capacidadMinima` ni `caracteristica` (p. ej. `precio`, `capacidad`, `caracteristicas`) ENTONCES el sistema DEBE
ignorarlos y responder igual que sin ellos.

## Rate limiting compartido (STRIDE D, RNF-SEG-17, §9.9)

## R27
CUANDO el filtro de rate limiting acepta una `GET /api/espacios` con filtros, el sistema DEBE descontarla del
mismo cupo del catálogo por IP que la búsqueda de HU-10 (grupo `catalogo`).

## R28
MIENTRAS una IP tiene agotado el cupo del catálogo, el sistema DEBE responder 429 con
`ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS` a cada `GET /api/espacios` con filtros de esa IP, sin ejecutar la
búsqueda.

## Servicio público de `espacios` (para `busqueda`)

## R29
CUANDO otro módulo llama a `CatalogoEspaciosService.publicados(tipo, zona)`, el sistema DEBE devolver cada
`EspacioPublicado` con la capacidad del espacio y el conjunto de sus códigos de característica.

## R30
CUANDO otro módulo llama a `CatalogoEspaciosService.esCaracteristicaValida(codigo)`, el sistema DEBE devolver
`true` solo si `codigo` es un código de característica válido.

## Trazabilidad con la historia

| Origen | Cubierto por |
|--------|--------------|
| CA1: el arrendador marca las características de su espacio de una lista fija al editarlo | R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11 |
| CA2: los resultados cumplen todos los filtros junto con tipo, zona y fecha | R12, R13, R14, R15, R16, R17, R18, R29 |
| CA3: sin resultados con los filtros, se sugiere quitar alguno | R19 (la sugerencia en pantalla, en `design.md` § 11) |
| (T) parámetros de filtro validados en servidor contra una lista blanca | R4, R5, R10, R20, R21, R22, R23, R24, R25, R26, R30 |
| (D) rate limiting compartido con HU-10 | R27, R28 |
| RF-B-08 | R1–R11 |
| RF-C-06 | R12–R18, R29 |
| RF-C-07 | R19 |
| RNF-SEG-10 (lista blanca) | R4, R5, R20–R26, R30 |
| RNF-SEG-16 (minimización en la búsqueda) | R18 |
| RNF-SEG-17 (cupo compartido del catálogo) | R27, R28 |
| RN-15 (solo publicados de no suspendidos) | R15, R16 (vía HU-10 R3, R4) |
| RN-18 (precio único por hora) | R12, R20 |
