# HU-24 — Requirements: Reseñas y calificaciones

> Fuente: `docs/historias/HU-24.md` (CA1–CA3, STRIDE S/T, T y R), `docs/requerimientos.md` (RF-C-08, RF-C-09,
> RF-C-10, RNF-SEG-10, RNF-SEG-12, RN-13), `docs/architecture.md` (módulo `busqueda`, puertos, "Mis reservas: qué deja
> HU-14", "Datos personales: qué deja HU-22"), `docs/modelo-datos.md` (`resena`), `specs/HU-11/design.md` (DT-16),
> `specs/HU-14/`, `specs/HU-22/design.md` (DT-2) y `progress/explore_HU-24.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la presentación está en
> `design.md` § 11 Pantallas. HU-24 no es ★: no exige patrón.

## Decisiones por defecto que el dueño debe confirmar al aprobar

> Las fuentes no fijan estos puntos o se contradicen. El spec aplica el valor indicado; si **Ariel Enriquez** no lo
> acepta, cambian los requirements citados antes de pasar a `in_progress`. La columna "Origen" remite a
> `progress/explore_HU-24.md` § 8. El porqué y las alternativas están en `design.md` § 1 y § 13.

### Decisiones abiertas

| # | Pregunta | Por defecto en este spec | Origen | Afecta |
|---|----------|--------------------------|--------|--------|
| P1 | ¿Desde cuándo y hasta cuándo se reseña? | Desde el **fin del último bloque** en hora de Lima (el `instanteDeFin` de HU-19 y HU-26), no desde la fecha calendario: `fin ≤ ahora < fin + 14 días`, 14 × 24 h. Una reserva de hoy ya terminada se puede reseñar. | C7 | R5, R7, R8, R25 |
| P2 | ¿Forma de la ruta y código de cada rechazo? | `POST /api/resenas` con `reservaId` en el cuerpo, la regla es **por reserva**. Reserva ajena o inexistente: 404 genérico. Reserva propia no reseñable: 400 con mensaje. Otro rol: 403. CA2 ("no tengo una reserva pasada en ese espacio") queda cubierto porque sin una reserva propia reseñable no hay forma de crear una reseña. | C9 | R1, R6–R10 |
| P3 | ¿El comentario es obligatorio y cuánto mide? | **Obligatorio**, de 1 a 500 caracteres: la historia dice "calificarlo de 1 a 5 **y** dejar un comentario". | C6 | R17, R22 |
| P4 | ¿Cómo se sanitiza el texto? | **Al guardar**, en el servidor, sin dependencias nuevas: se quitan etiquetas, todo `<` y `>` restante y los caracteres de control. No se escapa a entidades (quedarían `&lt;` en la ficha y en la exportación) y no se rechaza el comentario salvo que quede vacío. React sigue escapando al mostrar. | C5 | R21, R22 |
| P5 | ¿Cómo sabe la pantalla qué reservas se pueden reseñar? | Con una ruta propia, `GET /api/resenas/pendientes`, que devuelve los ids. **`ReservaDTO` no cambia**: ampliarlo rompe 7 aserciones de HU-12 y HU-14, y exigiría un puerto nuevo porque `reservas` no puede llamar a `busqueda`. | C1, C2 | R25, R26 |
| P6 | ¿Cómo va la información en la ficha? | `FichaPublicaDTO` gana `calificacionPromedio` y `totalResenas` (HU-11 DT-16). Las reseñas van en la ruta paginada `GET /api/espacios/{id}/resenas`, de 10 en 10, de la más nueva a la más antigua. Promedio con 1 decimal, redondeo `HALF_UP`; `null` y `0` sin reseñas. Se calcula al consultar, no se guarda. | C12 | R27–R34 |
| P7 | ¿Los resultados de búsqueda muestran el promedio, o se ordena o filtra por él? | **No**: ningún requisito lo pide y `ResultadoEspacioDTO` sigue con los seis campos de HU-10 R14 y HU-23 R18. | § 3 | Ninguno |
| P8 | ¿Qué se muestra del autor en la ficha pública? | **Nada**: solo calificación, comentario y fecha (día). Minimización (RNF-SEG-16): ningún CA pide el nombre y `usuario.nombre` puede ser nulo. Tampoco se expone `usuarioId`, `reservaId` ni la hora exacta. | C8 | R31 |
| P9 | ¿Se puede reseñar si el espacio ya no es visible (`PAUSADO`, `BAJA`, `OCULTO`, arrendador suspendido)? | **Sí**: el usuario sí usó el espacio. La reseña no se ve mientras la ficha dé 404 y reaparece si el espacio vuelve a publicarse. | C10 | R1, R33 |
| P10 | ¿Una reserva `CONFIRMADA` con reembolso por reclamo (HU-26) se puede reseñar? | **Sí**: sigue `CONFIRMADA` y el usuario la usó. HU-24 no consulta `pagos` ni reclamos. | C11 | R1 |
| P11 | ¿Qué pasa con las reseñas al ejercer los derechos de HU-22? | Se **exportan** en la sección `resenas`, solo para el rol `USUARIO`. Al eliminar la cuenta, el **comentario** se reemplaza por "Comentario eliminado." (texto libre que puede tener datos personales) y la **calificación se conserva**, para no alterar el promedio de otros. No generan impedimentos. | § 6, C8b | R39–R43 |
| P12 | ¿Qué pasa con una reseña que HU-27 oculte? | No aparece ni cuenta en el promedio y **tampoco se puede volver a reseñar** esa reserva: el único va sobre `reserva_id` sin filtrar por estado. HU-24 crea el estado, no la acción de ocultar. | § 7 | R12, R13, R35 |
| P13 | ¿Hay límite de reseñas por usuario? | **No**: ni RNF-SEG-17 ni la historia lo piden, y una reseña por reserva más la ventana de 14 días ya lo acotan. La ruta pública nueva sí usa el cupo `catalogo` (120 por IP por minuto), como la ficha. | § 4 | R37, R38 |

### Contradicciones entre documentos y código

| # | Contradicción | Cómo la resuelve este spec | Origen |
|---|---------------|----------------------------|--------|
| P14 | `architecture.md` y `specs/HU-14/design.md` dicen que `ReservaDTO` se puede ampliar "sin romper", pero 7 aserciones fijan sus 11 campos. | Se respeta el contrato vigente: no se amplía (P5). La autorización de ampliar sigue disponible para HU-26. | C1 |
| P15 | El diagrama de dependencias no muestra `busqueda → cuentas`, pero el puerto de HU-22 la implica ("busqueda lo sumará con HU-24"). | `busqueda` solo importa `cuentas.service.puertos`. No usa `CuentaService`. El implementer agrega la flecha al diagrama. | C8 |
| P16 | `specs/HU-14/design.md` nombra a Adriana Callupe como responsable de HU-24; la historia se reasignó a Ariel Enriquez. | Mandan `feature_list.json` y `docs/historias/HU-24.md`. Un spec cerrado no se edita. | C4 |
| P17 | `docs/modelo-datos.md` no define `creado_en`, longitud de `comentario`, cascadas ni índices de `resena`. | `design.md` § 7 los fija y el implementer actualiza el documento en el mismo PR, como exige su encabezado. | C6 |
| P18 | HU-11 dice que HU-23 agrega características a `FichaPublicaDTO`, pero HU-23 no lo hizo. | Sin efecto: el DTO sigue con 10 campos y HU-24 le suma 2. | C3 |

## Definiciones

- **Usuario:** sesión válida con rol `USUARIO`.
- **Reserva propia:** reserva cuyo `usuario_id` es el de la sesión.
- **Fin de la reserva:** instante, en `America/Lima`, en que termina su último bloque, contando todas sus filas de
  `reserva_bloque`, activas o no. Con `horaFin` 24 son las 00:00 del día siguiente (`ReservaConHorario.instanteDeFin`).
- **Dentro del plazo:** `fin ≤ ahora < fin + 14 días` (ver P1).
- **Reserva reseñable:** reserva propia, cuyo estado leído con `estadoEn(ahora)` es `CONFIRMADA`, dentro del plazo y sin
  ninguna fila en `resena`, en cualquier estado.
- **Calificación válida:** número JSON entero de 1 a 5. Un texto (`"4"`) o un decimal (`4.5`) no lo son.
- **Comentario válido:** texto JSON no vacío ni en blanco, de hasta 500 caracteres (ver P3).
- **Sanitizar:** normalizar los saltos de línea a `\n`, quitar los caracteres de control salvo `\n` y `\t`, quitar cada
  secuencia `<…>` sin `<` ni `>` dentro, quitar todo `<` y `>` restante y recortar los espacios de los extremos (ver P4).
- **Reseña visible:** fila de `resena` con estado `VISIBLE`.
- **Espacio visible:** `CatalogoEspaciosService.esVisible(id)` es `true` (`PUBLICADO` y arrendador `ACTIVA`).
- **Mensajes** (texto exacto en `design.md` § 5): *reserva inválida*, *calificación inválida*, *comentario inválido*,
  *no reseñable*, *plazo vencido*, *ya reseñada*, *comentario eliminado*, *página inválida*
  (`BusquedaService.MENSAJE_PAGINA_INVALIDA`), *demasiados intentos* (`ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS`).

## Crear una reseña (CA1, RF-C-08, STRIDE S/T)

## R1
CUANDO un usuario envía `POST /api/resenas` con el `reservaId` de una reserva reseñable, una calificación válida y un
comentario válido, el sistema DEBE crear una fila en `resena` con estado `VISIBLE`, también si el espacio de esa reserva
ya no es visible.

## R2
CUANDO el sistema crea una reseña, el sistema DEBE responder 201 con `id`, `reservaId`, `calificacion`, `comentario`
sanitizado y `fecha` (día de creación en `America/Lima`).

## R3
El sistema DEBE tomar `usuario_id` de la sesión y `espacio_id` de la reserva, ignorando cualquier `usuarioId`,
`espacioId`, `estado` u otro campo extra del cuerpo.

## R4
El sistema DEBE calcular el fin de la reserva con todas sus filas de `reserva_bloque`, activas o no, en `America/Lima`.

## R5
MIENTRAS `fin ≤ ahora < fin + 14 días`, el sistema DEBE tratar una reserva con estado leído `CONFIRMADA` como dentro del
plazo, incluido el instante exacto `fin`.

## Rechazos (CA2, RF-C-09, RN-13)

## R6
SI el estado leído de la reserva propia no es `CONFIRMADA` ENTONCES el sistema DEBE rechazar la solicitud con 400 y el
mensaje *no reseñable*, sin crear la reseña.

## R7
SI la reserva propia `CONFIRMADA` aún no llega a su fin ENTONCES el sistema DEBE rechazar la solicitud con 400 y el
mensaje *no reseñable*, sin crear la reseña.

## R8
SI `ahora ≥ fin + 14 días` en una reserva propia `CONFIRMADA` ENTONCES el sistema DEBE rechazar la solicitud con 400 y el
mensaje *plazo vencido*, sin crear la reseña.

## R9
SI el `reservaId` es de otra cuenta o no existe ENTONCES el sistema DEBE responder 404 con el mensaje genérico, igual en
los dos casos, sin crear la reseña.

## R10
SI el rol de la sesión en `POST /api/resenas` no es `USUARIO` ENTONCES el sistema DEBE responder 403 sin leer la reserva.

## R11
SI una solicitud llega a `POST /api/resenas` o a `GET /api/resenas/pendientes` sin una sesión válida ENTONCES el sistema
DEBE responder 401.

## Una reseña por reserva (CA3, RF-C-10, STRIDE S/T)

## R12
SI la reserva propia ya tiene una fila en `resena`, en cualquier estado ENTONCES el sistema DEBE rechazar la solicitud
con 400 y el mensaje *ya reseñada*, sin crear otra, también si el plazo ya venció.

## R13
El sistema DEBE garantizar una sola fila de `resena` por `reserva_id` con la restricción única `uq_resena_reserva`.

## R14
SI dos solicitudes simultáneas chocan con `uq_resena_reserva` ENTONCES el sistema DEBE responder 400 con el mensaje *ya
reseñada* a la segunda, en lugar de un 500.

## Validación de la entrada (STRIDE T)

## R15
SI `reservaId` falta, no es un número JSON entero, no cabe en un `long` o no es mayor que 0 ENTONCES el sistema DEBE
responder 400 en el campo `reservaId` con el mensaje *reserva inválida*.

## R16
SI la calificación no es válida ENTONCES el sistema DEBE responder 400 en el campo `calificacion` con el mensaje
*calificación inválida*.

## R17
SI el comentario no es válido ENTONCES el sistema DEBE responder 400 en el campo `comentario` con el mensaje *comentario
inválido*.

## R18
El sistema DEBE rechazar en la base de datos una `calificacion` fuera de 1 a 5, con la restricción `ck_resena_calificacion`.

## R19
El sistema DEBE rechazar en la base de datos un `estado` distinto de `VISIBLE` u `OCULTA`, con la restricción
`ck_resena_estado`.

## R20
El sistema DEBE rechazar en la base de datos el borrado de una `reserva`, un `espacio` o un `usuario` referenciados por
una reseña, con llaves foráneas sin cascada.

## Sanitización (STRIDE T, RNF-SEG-10)

## R21
CUANDO el sistema recibe un comentario válido, el sistema DEBE guardarlo sanitizado, de modo que el texto guardado no
contenga ningún `<` ni `>`.

## R22
SI el comentario queda vacío después de sanitizarlo ENTONCES el sistema DEBE responder 400 en el campo `comentario` con
el mensaje *comentario inválido*, sin leer la reserva.

## Auditoría (STRIDE R, RNF-SEG-12)

## R23
CUANDO el sistema crea una reseña, el sistema DEBE registrar en `auditoria`, en la misma transacción, la acción
`RESENA_CREADA` con entidad `resena`, su id, el usuario, la IP y el detalle
`reservaId=<id>;espacioId=<id>;calificacion=<n>`, sin el texto del comentario.

## R24
SI el sistema rechaza una solicitud de `POST /api/resenas` ENTONCES el sistema NO DEBE registrar `RESENA_CREADA`.

## Reservas por reseñar (CA1, ver P5)

## R25
CUANDO un usuario envía `GET /api/resenas/pendientes`, el sistema DEBE responder 200 con `reservaIds`: los ids de sus
reservas reseñables, en orden ascendente, sin incluir reservas ajenas, no confirmadas, fuera de plazo o ya reseñadas.

## R26
SI el rol de la sesión en `GET /api/resenas/pendientes` no es `USUARIO` ENTONCES el sistema DEBE responder 403 sin leer
las reservas.

## Reseñas en la ficha (CA1, RF-C-08, ver P6)

## R27
CUANDO se consulta `GET /api/espacios/{id}` de un espacio visible, el sistema DEBE incluir `calificacionPromedio` y
`totalResenas`, calculados sobre sus reseñas visibles.

## R28
El sistema DEBE calcular `calificacionPromedio` con 1 decimal y redondeo `HALF_UP`, sin pasar por `double` (4, 4, 4 y 5
dan `4.3`).

## R29
MIENTRAS un espacio no tiene reseñas visibles, el sistema DEBE responder `calificacionPromedio` nulo y `totalResenas` 0.

## R30
CUANDO se consulta `GET /api/espacios/{id}/resenas?pagina=n` de un espacio visible, el sistema DEBE responder 200 con
`pagina`, `resenas` y `hayMas`: de a 10 reseñas visibles, por `creado_en` e `id` descendentes; sin `pagina`, la primera.

## R31
El sistema DEBE devolver cada reseña pública solo con `id`, `calificacion`, `comentario` y `fecha`, sin datos del autor,
de la reserva, del espacio ni el estado.

## R32
CUANDO llega `GET /api/espacios/{id}/resenas` sin sesión o con un token inválido, el sistema DEBE atenderla como pública.

## R33
SI el `id` de `GET /api/espacios/{id}/resenas` está mal escrito, no existe o su espacio no es visible ENTONCES el sistema
DEBE responder 404 genérico, antes de validar `pagina`.

## R34
SI `pagina` está presente y no es un entero de 1 a `Integer.MAX_VALUE` ENTONCES el sistema DEBE responder 400 en el campo
`pagina` con el mensaje *página inválida*.

## R35
SI una reseña tiene estado `OCULTA` ENTONCES el sistema NO DEBE incluirla en `resenas`, `calificacionPromedio` ni
`totalResenas`.

## R36
CUANDO el sistema crea una reseña de un espacio visible, el sistema DEBE incluirla en `resenas`, `totalResenas` y
`calificacionPromedio` desde la siguiente lectura.

## R37
El sistema DEBE contar `GET /api/espacios/{id}/resenas` en el cupo `catalogo` por IP, compartido con la búsqueda y la
ficha, incluidas las respuestas 400 y 404.

## R38
SI el cupo `catalogo` de la IP está agotado ENTONCES el sistema DEBE responder 429 a `GET /api/espacios/{id}/resenas`
con el mensaje *demasiados intentos*, sin ejecutar la consulta.

## Datos personales (HU-22, RNF-CUM-04, ver P11)

## R39
CUANDO el titular con rol `USUARIO` exporta sus datos, el sistema DEBE incluir, después de `pagos`, la sección `resenas`
con todas sus reseñas, visibles u ocultas, cada una con `reservaId`, `espacioNombre`, `calificacion`, `comentario`,
`estado` y `fecha`, aunque la lista quede vacía.

## R40
CUANDO el titular de la exportación no tiene rol `USUARIO`, el sistema NO DEBE agregar la sección `resenas`.

## R41
CUANDO se evalúan los impedimentos de una eliminación de cuenta, el participante de reseñas DEBE devolver una lista
vacía.

## R42
CUANDO se elimina una cuenta, el sistema DEBE reemplazar el `comentario` de todas sus reseñas por el mensaje *comentario
eliminado*, en la misma transacción.

## R43
CUANDO se elimina una cuenta, el sistema DEBE conservar la `calificacion`, el `estado` y el `usuario_id` de sus reseñas,
sin alterar `calificacionPromedio` ni `totalResenas` del espacio.

## Trazabilidad con la historia

| Origen (`docs/historias/HU-24.md`)                                  | Cubierto por |
|---------------------------------------------------------------------|--------------|
| CA1: reseña dentro de 14 días, aparece en la ficha y sube el promedio | R1, R2, R4, R5, R25, R27, R28, R29, R30, R36 |
| CA2: sin reserva pasada en ese espacio no se permite                | R6, R7, R8, R9, R10, R11 |
| CA3: no se reseña dos veces la misma reserva                        | R12, R13, R14, R25 |
| (S/T) Una reseña por reserva, validada contra reservas reales       | R1, R3, R9, R12, R13, R14 |
| (T) Texto sanitizado contra inyección de scripts                    | R17, R21, R22 |
| (R) Registro de creación                                            | R23, R24 |

| Origen (`docs/requerimientos.md`)                                   | Cubierto por |
|---------------------------------------------------------------------|--------------|
| RF-C-08                                                             | R1, R5, R27, R30, R36 |
| RF-C-09                                                             | R6, R7, R8, R9 |
| RF-C-10                                                             | R12, R13, R14 |
| RN-13: calificación de 1 a 5 y plazo de 14 días                     | R5, R8, R13, R16, R18 |
| RNF-SEG-10                                                          | R21, R22 |
| RNF-SEG-12                                                          | R23 |
| RNF-SEG-16 (minimización, por P8)                                   | R31 |
| RNF-SEG-17 (cupo compartido, por P13)                               | R37, R38 |
| RNF-CUM-04 y puerto de HU-22 (por P11)                              | R39, R40, R41, R42, R43 |
| Preparación para HU-27 (`modelo-datos.md`)                          | R19, R31, R35 |
| Principio 7 de `architecture.md` (integridad en la base)            | R13, R18, R19, R20 |
