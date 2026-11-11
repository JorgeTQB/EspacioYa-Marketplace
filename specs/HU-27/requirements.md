# HU-27 — Requirements: Reportes y moderación de publicaciones y reseñas

> Fuente: `docs/historias/HU-27.md` (CA1–CA3, STRIDE E/S, D y R), `docs/requerimientos.md` (RF-F-10, RF-F-11,
> RF-F-12, RNF-SEG-04, RNF-SEG-12, RNF-SEG-17, RNF-USA-04, RN-15, §8 rate limiting y correo, §9 punto 4),
> `docs/architecture.md` ("Estado del espacio: qué deja HU-21", "Reseñas: qué deja HU-24", "Datos personales: qué deja
> HU-22", "Correo: qué deja HU-25", "Seguridad transversal": IPs permitidas + MFA), `docs/modelo-datos.md` (`reporte`,
> `espacio.estado`, `resena.estado`), `specs/HU-26/` (patrón del panel admin), `specs/HU-24/` y
> `progress/explore_HU-27.md` (puntos abiertos 1–8) y, para ocultar por fraude, `specs/HU-15/` (cancelación, Strategy de
> reembolso), `specs/HU-16/` (reembolso), `specs/HU-19/` (liquidación) y las respuestas del dueño del 2026-11-09.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la presentación está en
> `design.md` § 12 Pantallas. HU-27 no es ★: reutiliza el Factory Method de HU-25 sin cambiarlo.

## Decisiones por defecto que el dueño debe confirmar al aprobar

> Las fuentes no fijan estos puntos. El spec aplica el valor indicado; si **Kevin Chan** no lo acepta, cambian los
> requirements citados antes de pasar a `in_progress`. La columna "Origen" remite a `progress/explore_HU-27.md`
> § Puntos abiertos. El porqué y las alternativas están en `design.md` § 1 y § 14.
> **2026-11-09:** Kevin Chan respondió las dos preguntas abiertas: confirmó P8 (ocultar es definitivo) y cambió P9
> (ocultar un espacio **por fraude** cancela y reembolsa sus reservas confirmadas). P9, P10 y P17 ya lo reflejan; el
> resumen del cambio está en `progress/spec_delta_HU-27.md`.

| # | Pregunta | Por defecto en este spec | Origen | Afecta |
|---|----------|--------------------------|--------|--------|
| P1 | ¿Quién reporta? | Toda sesión con rol `USUARIO` o `ARRENDADOR`. `ADMIN` recibe 403 (modera, no reporta); sin sesión, 401. | 4 | R1, R2, R5, R6 |
| P2 | ¿Qué se puede reportar? | Solo contenido que hoy ve el público: un espacio visible (`PUBLICADO` con arrendador `ACTIVA`, la misma regla de la ficha) o una reseña `VISIBLE` de un espacio visible. Lo demás es 404 genérico. El contenido **propio** (espacio del arrendador de la sesión o reseña escrita por la sesión) es 400. Un arrendador sí puede reportar una reseña ajena sobre su espacio. | 4 | R7, R8, R9 |
| P3 | ¿Motivo libre o catálogo? | Catálogo cerrado, sin texto libre: `FRAUDE`, `INFORMACION_FALSA`, `CONTENIDO_INAPROPIADO`, `SPAM`. CA1 dice "eligiendo un motivo"; sin texto libre no hay nada que sanitizar ni anonimizar. | — | R1, R2, R15 |
| P4 | ¿Se puede reportar dos veces lo mismo? | No: **un reporte por persona y contenido**, en cualquier estado (restricción única). Otra persona sí puede reportarlo. | 3 | R10, R11, R12 |
| P5 | ¿Cómo se cuenta "10 reportes por usuario al día"? | Ventana **móvil de 24 h**, contando las filas de `reporte` del reportante (en cualquier estado) con `creado_en` posterior a *ahora − 24 h*, con la fila de `usuario` bloqueada. No usa `LimitadorIntentos` (en memoria y por IP). Al superarlo, **429** con el mensaje genérico de demasiados intentos. Las solicitudes rechazadas no cuentan. | 5 | R16–R19 |
| P6 | ¿Varios reportes del mismo contenido? | La cola lista cada reporte. La decisión sobre uno **resuelve todos** los reportes `POR_REVISAR` del mismo contenido con la misma decisión, motivo, admin y fecha. | 3 | R31, R33, R42 |
| P7 | ¿"Con un motivo" vale para mantener y para ocultar? | Sí: el motivo de la decisión es obligatorio en las dos (1–500 caracteres, sanitizado). Queda en el reporte; al ocultar un espacio, además va en el correo al arrendador. | — | R31, R44 |
| P8 | ¿Desde qué estados se oculta y se puede restaurar? | Espacio: desde `PUBLICADO` o `PAUSADO`; `BORRADOR`, `BAJA` y `OCULTO` responden 400. Reseña: desde `VISIBLE`. `OCULTO` y `OCULTA` son **finales y definitivos, sin restaurar** (**confirmado por el dueño** el 2026-11-09; `architecture.md`: "`OCULTO` no admite pausa, reanudación ni baja"). | 1 | R32, R34, R38, R39, R40, R55 |
| P9 | ¿Ocultar un espacio con reservas por terminar? | **Decidido por el dueño** (2026-11-09): con la decisión `OCULTAR_POR_FRAUDE` (P17) se cancelan, en la misma transacción, las reservas `CONFIRMADA` del espacio **que aún no empiezan**, con reembolso del **100 %** (política `MODERACION`) y fuera de toda liquidación. No se tocan las ya iniciadas o terminadas (su vía es el reclamo de HU-26), ni las `PENDIENTE_PAGO` (no pueden iniciar el pago con el espacio oculto, HU-13 D6, y expiran en 15 min). Con `OCULTAR` (sin fraude) las reservas no cambian. | 2 | R36, R46, R64–R74, R76, R77 |
| P10 | ¿Correos? | Al **ocultar un espacio**, al arrendador, con el nombre del espacio y el motivo de la decisión (CA3); si fue por fraude, con cuántas reservas se cancelaron. Por fraude, además, a cada **usuario** con una reserva cancelada, el aviso de cancelación de HU-15 (100 %). Sin correo al mantener, al ocultar una reseña ni al reportante. | 6 | R47, R48, R49, R69, R70 |
| P11 | ¿Estados del reporte? | `POR_REVISAR` → `MANTENIDO` u `OCULTADO` (finales). No se usa `PENDIENTE`: `EtiquetaEstado` ya lo usa para reembolsos con otro texto. | — | R1, R31, R33, R56 |
| P12 | ¿Datos personales (HU-22)? | Se **exporta** la sección `reportes` (los que hizo el titular, sin la decisión del admin). No genera impedimentos. Al eliminar la cuenta las filas se conservan sin cambios: no tienen texto libre y `reportante_id` apunta a la cuenta ya anonimizada. | 8 | R59, R60, R61 |
| P13 | ¿Se extrae `exigirAdmin`? | Sí (HU-26 DT-19: "extraerlo es de HU-27"): `comun.seguridad.RolesSesion.exigirAdmin` reemplaza las siete copias privadas, con el mismo comportamiento. Si el dueño prefiere no tocar código de otras historias, solo lo usa el código nuevo. | 7 | R5, R26, R62 |
| P14 | ¿El admin ve quién reportó? | No: la cola y el detalle no muestran el nombre, correo ni id del reportante (minimización, RNF-SEG-16). La auditoría sí lo guarda. | — | R24, R25 |
| P17 | ¿Cuándo es "por fraude"? | Lo **declara el admin** con una tercera decisión, `OCULTAR_POR_FRAUDE`, solo para reportes de **espacio**. No se deduce del motivo del reporte (`FRAUDE`): ese motivo lo elige el reportante, puede no haberlo usado ninguno de los reportes hermanos y un `SPAM` o `INFORMACION_FALSA` puede resultar fraude. `OCULTAR` sin fraude (contenido inapropiado, spam…) oculta sin tocar reservas: el espacio existe y las reservas pagadas se pueden cumplir. Queda en `reporte.por_fraude`. | Dueño (P9) | R33, R43, R46, R63, R64, R75, R78 |

### Decisiones del dueño (antes preguntas abiertas)

1. **Reservas de un espacio ocultado por fraude** (P9, P17): Kevin Chan (2026-11-09) decidió que **sí se cancelan** sus
   reservas confirmadas. El spec lo resuelve dentro de HU-27 reutilizando la cancelación de HU-15 (Strategy, con una
   política nueva de 100 %), el reembolso de HU-16 y la exclusión de liquidación de HU-19 (R63–R78).
2. **Restaurar contenido oculto** (P8): Kevin Chan (2026-11-09) confirmó que ocultar es **definitivo**, sin restaurar.
   No hay ruta ni CA de restauración (R55).

### Contradicciones entre documentos y código

| # | Contradicción | Cómo la resuelve este spec | Origen |
|---|---------------|----------------------------|--------|
| P15 | `modelo-datos.md` define `reporte(id, reportante_id, tipo_objetivo, objetivo_id, motivo, estado, admin_id, decision)`: `objetivo_id` polimórfico (sin llave foránea posible) y sin fechas ni motivo de la decisión. | `design.md` § 8 reemplaza `objetivo_id` por `espacio_id` y `resena_id` (una de las dos, con FK y `CHECK`, principio 7), agrega `creado_en`, `resuelto_en` y `motivo_decision`, y la decisión queda en `estado`. La API sigue hablando de `tipoObjetivo` + `objetivoId`. El implementer actualiza el documento en el mismo PR. | modelo-datos l.38 |
| P16 | RNF-SEG-17 pone "10 reportes por usuario al día" entre los límites de tasa, y §8 dice que el rate limiting usa `LimitadorIntentos`. | Es un cupo por usuario de negocio (como el de 3 pendientes de HU-12, §8): se cuenta en la base (P5). | requerimientos §8 |

## Definiciones

- **Usuario / Arrendador / Admin:** sesión válida con rol `USUARIO`, `ARRENDADOR` o `ADMIN`. **Reportante:** usuario o
  arrendador que envía un reporte.
- **Espacio reportable:** existe, está `PUBLICADO` y su arrendador está `ACTIVA` (`CatalogoEspaciosService.esVisible`).
- **Reseña reportable:** existe, está `VISIBLE` y su espacio es reportable.
- **Contenido propio:** espacio cuyo `arrendador_id` es el de la sesión, o reseña cuyo `usuario_id` es el de la sesión.
- **Contenido del reporte:** el espacio (`tipoObjetivo` = `ESPACIO`) o la reseña (`RESENA`) que señala.
- **Reportes hermanos:** los reportes `POR_REVISAR` del mismo contenido, incluido el que se decide.
- **Motivo válido:** texto JSON igual a `FRAUDE`, `INFORMACION_FALSA`, `CONTENIDO_INAPROPIADO` o `SPAM`.
- **Motivo de decisión válido:** texto JSON no vacío ni en blanco, de hasta 500 caracteres, que no queda vacío al
  sanitizarlo con `comun.texto.SanitizadorTexto.limpiar`.
- **Ventana del cupo:** `(ahora − 24 h, ahora]`.
- **Decisión de ocultar:** `decision` = `OCULTAR` u `OCULTAR_POR_FRAUDE`. **Ocultar por fraude:** `OCULTAR_POR_FRAUDE`
  sobre un reporte de espacio (P17).
- **Reserva por iniciar del espacio:** reserva de ese espacio con estado guardado `CONFIRMADA` cuyo inicio (fecha y
  hora del primer bloque, hora de Lima, `ReservaConHorario.instanteDeInicio`) es estrictamente posterior a *ahora*; es
  decir, aquella para la que `Reserva.motivoNoCancelable(inicio, ahora)` está vacío (HU-15).
- **Mensajes** (texto exacto en `design.md` § 6): *tipo inválido*, *contenido inválido*, *motivo inválido*, *contenido
  propio*, *ya reportado*, *decisión inválida*, *motivo de decisión inválido*, *ya resuelto*, *espacio no ocultable*,
  *reseña no ocultable*, *fraude solo en espacios*, *grupo inválido*, *página inválida*; el 429 usa
  `ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS`.

## Reportar un contenido (CA1, RF-F-10, STRIDE S)

## R1
CUANDO un usuario o un arrendador envía `POST /api/reportes` con `tipoObjetivo` = `ESPACIO`, el `objetivoId` de un
espacio reportable que no es propio y un motivo válido, el sistema DEBE crear una fila en `reporte` con estado
`POR_REVISAR`, `espacio_id` = ese id, el motivo, `reportante_id` = el de la sesión y `creado_en` = ahora.

## R2
CUANDO un usuario o un arrendador envía `POST /api/reportes` con `tipoObjetivo` = `RESENA`, el `objetivoId` de una
reseña reportable que no es propia y un motivo válido, el sistema DEBE crear una fila en `reporte` con estado
`POR_REVISAR`, `resena_id` = ese id, el motivo, `reportante_id` = el de la sesión y `creado_en` = ahora.

## R3
CUANDO el sistema crea un reporte, el sistema DEBE responder 201 con `id`, `tipoObjetivo`, `objetivoId`, `motivo`,
`estado` = `POR_REVISAR` y `creadoEn`.

## R4
El sistema DEBE tomar el reportante de la sesión, ignorando cualquier `reportanteId`, `usuarioId`, `estado`, `adminId`
u otro campo extra del cuerpo de `POST /api/reportes`.

## R5
SI el rol de la sesión en `POST /api/reportes` es `ADMIN` ENTONCES el sistema DEBE responder 403 sin leer el contenido
ni contar reportes.

## R6
SI una solicitud llega a `POST /api/reportes` o a una ruta de `/api/admin/reportes` sin una sesión válida ENTONCES el
sistema DEBE responder 401.

## R7
SI el contenido no existe o no es reportable ENTONCES el sistema DEBE responder 404 con el mensaje genérico, igual en
todos los casos (espacio inexistente, no `PUBLICADO` o de arrendador no `ACTIVA`; reseña inexistente, `OCULTA` o de un
espacio no reportable), sin crear el reporte.

## R8
SI el espacio reportado es del arrendador de la sesión ENTONCES el sistema DEBE rechazar la solicitud con 400 y el
mensaje *contenido propio*, sin crear el reporte.

## R9
SI la reseña reportada la escribió la cuenta de la sesión ENTONCES el sistema DEBE rechazar la solicitud con 400 y el
mensaje *contenido propio*, sin crear el reporte.

## R10
SI el reportante ya tiene un reporte, en cualquier estado, sobre el mismo contenido ENTONCES el sistema DEBE rechazar la
solicitud con 400 y el mensaje *ya reportado*, sin crear otro.

## R11
El sistema DEBE garantizar en la base de datos un solo reporte por reportante y espacio y un solo reporte por
reportante y reseña, con los índices únicos `uq_reporte_espacio` y `uq_reporte_resena`.

## R12
SI dos solicitudes simultáneas del mismo reportante sobre el mismo contenido chocan con `uq_reporte_espacio` o
`uq_reporte_resena` ENTONCES el sistema DEBE responder 400 con el mensaje *ya reportado* a la segunda, en lugar de un
500.

## Validación de la entrada (STRIDE T)

## R13
SI `tipoObjetivo` falta, no es un texto JSON o no es `ESPACIO` ni `RESENA` ENTONCES el sistema DEBE responder 400 en el
campo `tipoObjetivo` con el mensaje *tipo inválido*.

## R14
SI `objetivoId` falta, no es un número JSON entero, no cabe en un `long` o no es mayor que 0 ENTONCES el sistema DEBE
responder 400 en el campo `objetivoId` con el mensaje *contenido inválido*.

## R15
SI el motivo falta o no es un motivo válido ENTONCES el sistema DEBE responder 400 en el campo `motivo` con el mensaje
*motivo inválido*.

## Cupo de 10 reportes al día (STRIDE D, RNF-SEG-17)

## R16
SI el reportante ya creó 10 reportes dentro de la ventana del cupo ENTONCES el sistema DEBE responder 429 con el mensaje
`ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS`, sin crear el reporte.

## R17
El sistema DEBE contar en el cupo solo los reportes del reportante con `creado_en` estrictamente posterior a
*ahora − 24 h*, en cualquier estado, de modo que el décimo reporte de la ventana se acepte y uno creado hace exactamente
24 h ya no cuente.

## R18
El sistema DEBE contar el cupo después de bloquear la fila de `usuario` del reportante
(`CuentaService.bloquearCuenta`) y en la misma transacción que inserta el reporte, de modo que solicitudes simultáneas
del mismo reportante no superen los 10 reportes en la ventana.

## R19
SI el cupo está agotado ENTONCES el sistema DEBE responder 429 antes de comprobar si el contenido existe, es
reportable, es propio o ya fue reportado.

## Cola de moderación del admin (CA2, RF-F-11)

## R20
CUANDO un admin envía `GET /api/admin/reportes?grupo=pendientes&pagina=n`, el sistema DEBE responder 200 con `pagina`,
`reportes` y `hayMas`: los reportes `POR_REVISAR`, de a 10, por `creado_en` e `id` ascendentes (el más antiguo
primero), cada uno con `id`, `tipoObjetivo`, `objetivoId`, `espacioNombre` (el del espacio o el del espacio de la
reseña), `motivo`, `estado`, `creadoEn` y `resueltoEn`; sin `pagina`, la primera.

## R21
CUANDO un admin envía `GET /api/admin/reportes?grupo=resueltos&pagina=n`, el sistema DEBE responder 200 con los
reportes `MANTENIDO` u `OCULTADO`, de a 10, por `resuelto_en` e `id` descendentes, con los campos de R20 y `hayMas`.

## R22
SI `grupo` falta o no es `pendientes` ni `resueltos` ENTONCES el sistema DEBE responder 400 en el campo `grupo` con el
mensaje *grupo inválido*.

## R23
SI `pagina` está presente y no es un entero de 1 a `Integer.MAX_VALUE` ENTONCES el sistema DEBE responder 400 en el
campo `pagina` con el mensaje *página inválida*.

## R24
CUANDO un admin envía `GET /api/admin/reportes/{id}` de un reporte sobre un espacio, el sistema DEBE responder 200 con
los datos del reporte (`id`, `tipoObjetivo`, `objetivoId`, `motivo`, `estado`, `creadoEn`, `decision`,
`motivoDecision`, `resueltoEn`), `reportesPendientes` (cuántos reportes `POR_REVISAR` tiene ese contenido) y el espacio
en su estado actual (`nombre`, `tipo`, `zona`, `direccion`, `capacidad`, `precioHora`, `descripcion`, `estado`,
`arrendadorNombre`), sin correo ni teléfono del arrendador y sin datos del reportante.

## R25
CUANDO un admin envía `GET /api/admin/reportes/{id}` de un reporte sobre una reseña, el sistema DEBE responder 200 con
los datos del reporte y `reportesPendientes` de R24 y la reseña en su estado actual (`calificacion`, `comentario`,
`fecha`, `estado`, `espacioId`, `espacioNombre`), sin datos de su autor ni del reportante.

## R26
SI el rol de la sesión en una ruta de `/api/admin/reportes` no es `ADMIN` ENTONCES el sistema DEBE responder 403 sin
leer reportes.

## R27
SI el `id` de una ruta `/api/admin/reportes/{id}` está mal escrito o no existe ENTONCES el sistema DEBE responder 404
con el mensaje genérico.

## R28
El sistema DEBE responder todas las rutas `GET` de `/api/admin/reportes` con `Cache-Control: no-store`.

## Solo admins con MFA (STRIDE E/S, RNF-SEG-04)

## R29
SI una solicitud a una ruta de `/api/admin/reportes` llega desde una IP que no está en `ADMIN_IPS_PERMITIDAS` ENTONCES el
sistema DEBE responder 403 con el mensaje genérico sin llegar al servicio.

## R30
SI la sesión de una solicitud a `/api/admin/reportes` es de un admin sin MFA activo ENTONCES el sistema DEBE responder
401.

## Decidir: mantener u ocultar (CA2, CA3, RF-F-11, RF-F-12)

## R31
CUANDO un admin envía `POST /api/admin/reportes/{id}/decision` con `decision` = `MANTENER` y un motivo de decisión
válido sobre un reporte `POR_REVISAR`, el sistema DEBE dejar ese reporte y sus reportes hermanos `MANTENIDO`, con
`admin_id` = el de la sesión, `motivo_decision` sanitizado y `resuelto_en` = ahora, sin cambiar el contenido.

## R32
CUANDO un admin envía `POST /api/admin/reportes/{id}/decision` con `decision` = `OCULTAR` u `OCULTAR_POR_FRAUDE` y un
motivo de decisión válido sobre un reporte `POR_REVISAR` de un espacio `PUBLICADO` o `PAUSADO`, el sistema DEBE dejar el
espacio `OCULTO` con `actualizado_en` = ahora.

## R33
CUANDO un admin oculta el contenido de un reporte, el sistema DEBE dejar ese reporte y sus reportes hermanos
`OCULTADO`, con `admin_id`, `motivo_decision` sanitizado, `resuelto_en` = ahora y `por_fraude` = `true` solo si la
decisión es `OCULTAR_POR_FRAUDE`, en la misma transacción que oculta el contenido.

## R34
CUANDO un admin envía `POST /api/admin/reportes/{id}/decision` con `decision` = `OCULTAR` y un motivo de decisión
válido sobre un reporte `POR_REVISAR` de una reseña `VISIBLE`, el sistema DEBE dejar la reseña `OCULTA`.

## R35
MIENTRAS un espacio está `OCULTO`, el sistema NO DEBE devolverlo en `GET /api/espacios` ni en `GET /api/espacios/{id}`
(404).

## R36
MIENTRAS un espacio está `OCULTO`, el sistema DEBE rechazar con 404 la creación de una reserva sobre él.

## R37
MIENTRAS una reseña está `OCULTA`, el sistema NO DEBE devolverla en `GET /api/espacios/{id}/resenas` ni contarla en
`calificacionPromedio` ni en `totalResenas` de la ficha.

## R38
MIENTRAS un espacio está `OCULTO`, el sistema DEBE rechazar con 400 su edición, pausa, reanudación y baja por el
arrendador y su pausa y baja por el admin, sin cambiar su estado.

## R39
SI `decision` es `OCULTAR` u `OCULTAR_POR_FRAUDE` y el espacio del reporte no está `PUBLICADO` ni `PAUSADO` ENTONCES el
sistema DEBE rechazar la decisión con 400 y el mensaje *espacio no ocultable*, sin resolver ningún reporte, sin cancelar
reservas, sin auditar la decisión y sin enviar correo.

## R40
SI `decision` es `OCULTAR` y la reseña del reporte ya no está `VISIBLE` ENTONCES el sistema DEBE rechazar la decisión
con 400 y el mensaje *reseña no ocultable*, sin resolver ningún reporte ni auditar la decisión.

## R41
SI el reporte ya no está `POR_REVISAR` ENTONCES el sistema DEBE rechazar la decisión con 400 y el mensaje *ya
resuelto*, sin cambiar el contenido, sin auditar y sin enviar correo.

## R42
El sistema DEBE bloquear (`FOR UPDATE`), en orden de `id` ascendente, los reportes hermanos antes de comprobar el estado
del reporte y antes de bloquear el contenido, de modo que de dos decisiones simultáneas sobre reportes del mismo
contenido solo una tenga efecto y la otra reciba 400 *ya resuelto*.

## R43
SI `decision` falta, no es un texto JSON o no es `MANTENER`, `OCULTAR` ni `OCULTAR_POR_FRAUDE` ENTONCES el sistema DEBE
responder 400 en el campo `decision` con el mensaje *decisión inválida*.

## R44
SI `motivoDecision` falta, no es un texto JSON, está en blanco, supera 500 caracteres o queda vacío después de
sanitizarlo ENTONCES el sistema DEBE responder 400 en el campo `motivoDecision` con el mensaje *motivo de decisión
inválido*.

## R45
CUANDO el sistema registra una decisión, el sistema DEBE responder 200 con el detalle de R24 o R25 ya resuelto y
`reservasCanceladas` = la cantidad de reservas canceladas por esa decisión (0 salvo `OCULTAR_POR_FRAUDE`); el detalle de
`GET /api/admin/reportes/{id}` lleva `reservasCanceladas` = `null`.

## R46
CUANDO el sistema oculta un espacio con `decision` = `OCULTAR`, el sistema NO DEBE cambiar el estado de ninguna de sus
reservas ni solicitar reembolsos (P9, P17).

## Correo al arrendador (CA3, RF-F-12, RNF-USA-04)

## R47
CUANDO se confirma la transacción que oculta un espacio, el sistema DEBE enviar al correo del arrendador un aviso con el
nombre del espacio y el motivo de la decisión, sin el motivo del reporte ni datos del reportante; si la decisión fue
`OCULTAR_POR_FRAUDE`, el aviso DEBE decir además cuántas reservas se cancelaron con reembolso total y que no se
incluirán en sus liquidaciones (R70).

## R48
SI la transacción de una decisión se revierte ENTONCES el sistema NO DEBE enviar ningún correo.

## R49
CUANDO el admin mantiene un contenido u oculta una reseña, el sistema NO DEBE enviar ningún correo (P10).

## Auditoría (STRIDE R, RNF-SEG-12)

## R50
CUANDO el sistema crea un reporte, el sistema DEBE registrar en `auditoria`, en la misma transacción, la acción
`REPORTE_CREADO` con entidad `reporte`, su id, el reportante, la IP y el detalle
`tipo=<TIPO>;objetivoId=<id>;motivo=<MOTIVO>`.

## R51
CUANDO el sistema registra una decisión, el sistema DEBE registrar en `auditoria`, en la misma transacción, la acción
`REPORTE_RESUELTO` con entidad `reporte`, el id del reporte decidido, el admin, la IP y el detalle
`tipo=<TIPO>;objetivoId=<id>;decision=<MANTENER|OCULTAR|OCULTAR_POR_FRAUDE>;reportes=<cantidad resuelta>;reservasCanceladas=<n>`
(`n` = 0 salvo por fraude), sin el motivo de la decisión.

## R52
CUANDO el sistema oculta un espacio, el sistema DEBE registrar en `auditoria`, en la misma transacción, la acción
`OCULTACION_ESPACIO` con entidad `espacio`, su id, el admin, la IP y el detalle
`estado=<anterior>->OCULTO;actor=ADMIN;arrendadorId=<id>`.

## R53
CUANDO el sistema oculta una reseña, el sistema DEBE registrar en `auditoria`, en la misma transacción, la acción
`OCULTACION_RESENA` con entidad `resena`, su id, el admin, la IP y el detalle
`estado=VISIBLE->OCULTA;actor=ADMIN;espacioId=<id>`.

## R54
SI el sistema rechaza un reporte o una decisión ENTONCES el sistema NO DEBE registrar `REPORTE_CREADO`,
`REPORTE_RESUELTO`, `OCULTACION_ESPACIO`, `OCULTACION_RESENA`, `RESERVA_CANCELADA` ni `REEMBOLSO_SOLICITADO`.

## R55
El sistema NO DEBE ofrecer ninguna ruta que edite o borre un reporte o una decisión.

## Integridad en la base de datos (principio 7, STRIDE T y R)

## R56
El sistema DEBE rechazar en la base de datos un `tipo_objetivo` distinto de `ESPACIO` o `RESENA`, un `motivo` fuera del
catálogo, un `estado` distinto de `POR_REVISAR`, `MANTENIDO` u `OCULTADO`, y un reporte cuyo contenido no coincida con
su tipo (`ESPACIO` con solo `espacio_id`, `RESENA` con solo `resena_id`), con restricciones `CHECK`.

## R57
El sistema DEBE rechazar en la base de datos un `reporte` cuyos datos de decisión no sean coherentes con su estado
(`ck_reporte_decision`: `POR_REVISAR` sin admin, motivo ni fecha de decisión; `MANTENIDO` u `OCULTADO` con los tres y
`resuelto_en ≥ creado_en`).

## R58
El sistema DEBE rechazar en la base de datos el borrado de un `usuario`, un `espacio` o una `resena` referenciados por un
reporte, con llaves foráneas sin cascada.

## Datos personales (HU-22, RNF-CUM-04, ver P12)

## R59
CUANDO el titular con rol `USUARIO` o `ARRENDADOR` exporta sus datos, el sistema DEBE incluir, después de las secciones
existentes, la sección `reportes` con cada reporte que hizo: `tipoObjetivo`, `objetivoId`, `motivo`, `estado`,
`creadoEn` y `resueltoEn`, sin `motivoDecision` ni el admin, aunque la lista quede vacía.

## R60
CUANDO se evalúa la eliminación de una cuenta, el participante de reportes NO DEBE devolver ningún impedimento.

## R61
CUANDO se elimina una cuenta, el sistema DEBE conservar sin cambios los reportes que hizo y los que la mencionan como
admin.

## Defensa en profundidad del rol (STRIDE E)

## R62
SI `ModeracionEspacios.ocultar` o `ModeracionResenas.ocultar` se llaman con una sesión que no es `ADMIN` ENTONCES el
sistema DEBE lanzar `AccesoDenegadoException` sin leer ni bloquear el contenido.

## Ocultar por fraude: cancelación y reembolso de reservas (decisión del dueño, P9 y P17)

## R63
SI `decision` es `OCULTAR_POR_FRAUDE` y el reporte es de una reseña ENTONCES el sistema DEBE rechazar la decisión con
400 en el campo `decision` y el mensaje *fraude solo en espacios*, sin resolver ningún reporte, sin ocultar la reseña y
sin auditar.

## R64
CUANDO un admin oculta por fraude un espacio, el sistema DEBE, en la misma transacción que lo oculta, dejar cada
reserva por iniciar de ese espacio `CANCELADA` con `cancelada_por` = `ADMIN`, `cancelada_en` = ahora y
`monto_reembolso` = su `monto_total`, y desactivar sus bloques.

## R65
CUANDO un admin oculta por fraude un espacio, el sistema NO DEBE cambiar sus reservas `CONFIRMADA` ya iniciadas o
terminadas ni sus reservas `PENDIENTE_PAGO`, `EXPIRADA` o `CANCELADA`.

## R66
CUANDO el sistema cancela una reserva por fraude, el sistema DEBE solicitar en la misma transacción su reembolso por
`monto_total` con `PagoService.reembolsarCancelacion` (origen `CANCELACION`, `solicitadoPor` = el admin, la IP de la
solicitud), de modo que el reembolso quede `PENDIENTE` y la pasarela se llame solo después del commit (HU-16).

## R67
CUANDO el sistema calcula el reembolso de una cancelación con `QuienCancela.ADMIN`, el sistema DEBE aplicar la política
`MODERACION` con 100 % del monto pagado, cualquiera sea la anticipación, y ninguna otra política DEBE aplicar a esa
solicitud.

## R68
MIENTRAS una reserva está `CANCELADA` con `cancelada_por` = `ADMIN`, el sistema NO DEBE incluirla en ninguna
liquidación del arrendador (`Reserva.admiteLiquidacion` = `false`).

## R69
CUANDO se confirma la transacción que oculta por fraude un espacio, el sistema DEBE enviar a cada usuario con una
reserva cancelada el aviso de cancelación de HU-15 con el espacio, la fecha, el horario, "Cancelada por: EspacioYa",
100 % y el monto del reembolso, sin el motivo de la decisión, el motivo del reporte ni datos del reportante.

## R70
CUANDO el sistema cancela reservas por fraude, el sistema NO DEBE enviar al arrendador el aviso de cancelación de HU-15
por cada reserva: lo informa el aviso de R47.

## R71
CUANDO el sistema cancela una reserva por fraude, el sistema DEBE registrar en `auditoria`, en la misma transacción, la
acción `RESERVA_CANCELADA` con entidad `reserva`, su id, el admin, la IP y el detalle
`canceladaPor=ADMIN;espacioId=<id>;politica=MODERACION;porcentaje=100;monto=<monto>`.

## R72
SI falla la cancelación o la solicitud de reembolso de cualquiera de las reservas por iniciar ENTONCES el sistema DEBE
revertir toda la decisión: el espacio no queda `OCULTO`, los reportes siguen `POR_REVISAR`, ninguna reserva cambia, no
queda ningún reembolso ni auditoría, no se llama a la pasarela y no se envía ningún correo.

## R73
El sistema DEBE, al ocultar por fraude, bloquear el espacio antes que sus reservas y las reservas `CONFIRMADA` del
espacio en orden de `id` ascendente (`FOR UPDATE`), y evaluar si cada una está por iniciar después de bloquearla, de
modo que, frente a una cancelación simultánea de HU-15 de la misma reserva, solo una de las dos la cancele y solicite el
reembolso (la otra la ve `CANCELADA`: se omite o responde 400 *ya cancelada*).

## R74
El sistema DEBE admitir en la base de datos `reserva.cancelada_por` = `ADMIN` (`ck_reserva_cancelada_por`), además de
`USUARIO` y `ARRENDADOR`, sin cambiar `ck_reserva_cancelacion`.

## R75
El sistema DEBE rechazar en la base de datos un `reporte` con `por_fraude` = `true` cuyo estado no sea `OCULTADO` o cuyo
`tipo_objetivo` no sea `ESPACIO` (`ck_reporte_fraude`).

## R76
SI `CancelacionPorModeracion.cancelarPorFraude` se llama con una sesión que no es `ADMIN` ENTONCES el sistema DEBE
lanzar `AccesoDenegadoException` sin leer ni bloquear reservas.

## R77
SI `CancelacionReservaService.vistaPrevia` o `confirmar` (rutas de HU-15) reciben `QuienCancela.ADMIN` ENTONCES el
sistema DEBE lanzar `AccesoDenegadoException` sin leer la reserva: el admin solo cancela al ocultar por fraude.

## R78
El sistema DEBE devolver `decision` = `OCULTAR_POR_FRAUDE` en el detalle de un reporte `OCULTADO` con `por_fraude` =
`true`, y `OCULTAR` en uno `OCULTADO` con `por_fraude` = `false`.

## Trazabilidad con la historia

| Origen (`docs/historias/HU-27.md`)                                            | Cubierto por |
|-------------------------------------------------------------------------------|--------------|
| CA1: usuario autenticado reporta un espacio o reseña eligiendo un motivo       | R1, R2, R3, R4, R7, R8, R9, R10, R13, R14, R15 |
| CA1: el reporte entra a la cola de moderación                                  | R1, R2, R20 |
| CA2: el admin revisa un reporte y ve el contenido y el motivo                  | R20, R21, R24, R25, R27, R28 |
| CA2: puede mantener u ocultar con un motivo                                    | R31, R32, R33, R34, R39, R40, R41, R42, R43, R44, R45, R63, R78 |
| CA3: el arrendador recibe un correo con el motivo                              | R47, R48, R70 |
| CA3: el espacio deja de verse en el catálogo                                   | R32, R35, R36, R38, R46 |
| (E/S) Solo admins con MFA (HU-17)                                              | R5, R26, R29, R30, R62, R76, R77 |
| (D) Máximo 10 reportes por usuario al día                                      | R16, R17, R18, R19 |
| (R) Registro de reportes y acciones                                            | R50, R51, R52, R53, R54, R55, R57, R58, R71, R75 |
| Dueño 2026-11-09 (P9): ocultar por fraude cancela las reservas confirmadas     | R64, R65, R66, R67, R68, R69, R72, R73, R74 |
| Dueño 2026-11-09 (P8): ocultar es definitivo, sin restaurar                    | R38, R55 |

| Origen (`docs/requerimientos.md` y `architecture.md`)                          | Cubierto por |
|--------------------------------------------------------------------------------|--------------|
| RF-F-10                                                                        | R1, R2, R3, R20 |
| RF-F-11                                                                        | R24, R25, R31, R32, R34 |
| RF-F-12                                                                        | R32, R35, R47 |
| RN-15 (la búsqueda no muestra ocultos)                                         | R35, R36 |
| Reseñas de HU-24 ("las consultas ya filtran `VISIBLE`")                        | R34, R37 |
| `architecture.md`: "`OCULTO` no admite pausa, reanudación ni baja"             | R38, R39 |
| RNF-SEG-04 (MFA del admin) y §9 punto 4 (IPs permitidas)                       | R29, R30 |
| RNF-SEG-12                                                                     | R50, R51, R52, R53 |
| RNF-SEG-16 (minimización, por P14)                                             | R24, R25, R47 |
| RNF-SEG-17 ("10 reportes por usuario al día", por P5 y P16)                    | R16, R17, R18, R19 |
| RNF-SEG-18 (rol validado en el backend)                                        | R5, R26, R62 |
| RNF-USA-04 (aviso de moderación por correo, por P10)                           | R47, R49 |
| RNF-CUM-04 y puerto de HU-22 (por P12)                                         | R59, R60, R61 |
| Principio 7 (integridad en la base)                                            | R11, R56, R57, R58, R74, R75 |
| RN-06 y Strategy de HU-15 (★): reembolso por quien cancela                     | R64, R67, R77 |
| HU-16 (reembolso con Command tras el commit)                                   | R66, R72 |
| HU-19 D3 (qué reservas se liquidan)                                            | R68 |
| RN-14 / RNF-USA-04 (correo en cancelaciones)                                   | R69, R70 |
