# HU-26 — Requirements: Reclamos sobre una reserva

> Fuente: `docs/historias/HU-26.md` (CA1–CA3, STRIDE E, D, R y la simplificación "sin adjuntar fotos"),
> `docs/requerimientos.md` (RF-F-07, RF-F-08, RF-F-09, RNF-SEG-12, RNF-SEG-17, RNF-SEG-19, RN-12, RNF-USA-04, §8
> sanitización y correo, §9 punto 3), `docs/architecture.md` (puerto `pagos.ReclamosAbiertos`, "Reembolsos: qué deja
> HU-16", "Liquidación: qué deja HU-19", "Mis reservas: qué deja HU-14", "Datos personales: qué deja HU-22", "Correo:
> qué deja HU-25"), `docs/modelo-datos.md` (`reclamo`, `mensaje_reclamo`), `specs/HU-24/` (modelo de interfaz angosta
> y sanitizado) y `progress/explore_HU-26.md` (huecos H1–H14).
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la presentación está en
> `design.md` § 12 Pantallas. HU-26 no es ★: no exige patrón del curso (reutiliza el Command de HU-16 y el Factory
> Method de HU-25 sin cambiarlos).

## Decisiones por defecto que la dueña debe confirmar al aprobar

> Las fuentes no fijan estos puntos o se contradicen. El spec aplica el valor indicado; si **Adriana Callupe** no lo
> acepta, cambian los requirements citados antes de pasar a `in_progress`. La columna "Origen" remite a
> `progress/explore_HU-26.md` § Huecos. El porqué y las alternativas están en `design.md` § 1 y § 14.

| # | Pregunta | Por defecto en este spec | Origen | Afecta |
|---|----------|--------------------------|--------|--------|
| P1 | ¿Cómo "responde" el arrendador (CA1)? | **Hilo de mensajes** en `mensaje_reclamo` entre las dos partes (usuario y arrendador) mientras el reclamo está `ABIERTO`; el admin lo lee y no escribe. La descripción del usuario va en `reclamo.descripcion` y no cuenta como mensaje. Cada parte envía **hasta 10 mensajes** por reclamo, de 1 a 1000 caracteres, sanitizados. Sin fotos. Sin correo por mensaje. | H1 | R23–R30 |
| P2 | ¿Desde cuándo se puede abrir un reclamo? | Desde el **inicio** de la reserva (quien llega y encuentra el espacio cerrado reclama en el momento) hasta **antes de `fin + 48 h`**: `inicio ≤ ahora < fin + 48 h`, con `ReservaConHorario.instanteDeInicio/instanteDeFin` y `ReglasLiquidacion.ESPERA`. El borde superior coincide con el inferior de la liquidación (`corte ≥ fin + 48 h`): sin hueco ni solape. | H2 | R4–R7 |
| P3 | ¿Qué reservas admiten reclamo? | Solo las de estado leído **`CONFIRMADA`**. Una `CANCELADA` (aunque sea con 0 % y se liquide) no se usó: no admite reclamo. | H2 | R5 |
| P4 | ¿Motivo libre o catálogo? | Catálogo cerrado: `ESPACIO_CERRADO`, `DISTINTO_A_LO_PUBLICADO`, `OTRO` (los dos ejemplos de la historia y un comodín). La descripción es obligatoria, de 1 a 1000 caracteres, sanitizada. | H6 | R1, R16, R17 |
| P5 | ¿Estados del reclamo? | `ABIERTO`, `RESUELTO_USUARIO`, `RESUELTO_ARRENDADOR`. Sin "en revisión": nada lo pide. | H6 | R1, R42, R49 |
| P6 | ¿Quién fija el monto del reembolso? | El admin elige `TOTAL` (el servidor usa el monto del pago `PAGADO`) o `PARCIAL` con un `montoReembolso` mayor que 0 y **menor** que el monto pagado, con 2 decimales como máximo. El servidor valida siempre. | H4 | R42–R47 |
| P7 | ¿Qué pasa con el remanente de un reembolso parcial? | **No se liquida**: toda reserva con una fila de `reembolso` queda fuera de la liquidación (`pagadosSinLiquidar`, HU-19), y el pago pasa a `REEMBOLSADO` aunque el reembolso sea parcial (`Pago.marcarReembolsado`, HU-16). Liquidar el remanente exigiría cambiar HU-19 y HU-16: fuera de este spec. | H4 | R48 |
| P8 | ¿Estado de la reserva tras resolver a favor del usuario? | **Sigue `CONFIRMADA`**: no hay estado "reembolsada" en `reserva` y HU-24 P10 ya cuenta con eso. `ck_reserva_cancelacion` no se toca. | H5 | R42 |
| P9 | ¿Correos? | Al **abrir**: uno al arrendador (sin la descripción ni datos del usuario) para que sepa que puede responder (RNF-USA-04). Al **resolver**: uno a cada parte (CA2, CA3); el del arrendador sin montos. Sin correo por mensaje. | H9 | R22, R57, R58 |
| P10 | ¿Datos personales (HU-22)? | Se **exporta** la sección `reclamos` para `USUARIO` y `ARRENDADOR` con los textos que escribió el titular (no los de la otra parte). Un reclamo `ABIERTO` del que el titular es parte **impide** eliminar la cuenta (campo `reclamos`). Al anonimizar, la descripción y los mensajes del titular pasan a "Texto eliminado." y se conserva todo lo demás. | H8 | R67–R70 |
| P11 | ¿`ReservaDTO` se amplía? | **No**, como HU-24 P5: la pantalla pide `GET /api/reclamos/reclamables` y los reclamos propios en `GET /api/reclamos`. | H10 | R31, R33 |
| P12 | ¿El arrendador ve el monto del reembolso? | **No** (RNF-SEG-16: el arrendador no ve datos de pago del usuario). Ve a favor de quién se resolvió y el motivo de la resolución. | H9, H10 | R32, R58 |

### Preguntas abiertas

> Estas dos no las resuelve ninguna fuente y no conviene inventarlas en silencio. El spec aplica el valor por defecto
> de la tabla anterior para no bloquear; al aprobar, la dueña confirma o cambia.

1. **§9 punto 3 de `docs/requerimientos.md` sigue pendiente con el PO** ("respuesta del arrendador en reclamos": cómo
   se ve y qué se valida). `architecture.md` lo lista en "Qué NO hacer" junto con "fotos en reclamos", con una
   redacción ambigua. Este spec **lee la fila como** "respuesta del arrendador a reseñas" (fuera de HU-24) y "fotos en
   reclamos" (fuera de HU-26), porque CA1 dice expresamente "el arrendador puede responder". **¿El PO confirma el hilo
   de P1 (ambas partes, 10 mensajes cada una, mientras esté abierto)?** Si decide "una sola respuesta del arrendador",
   cambian R23 y R27 (límite 1 y solo papel `ARRENDADOR`); si decide que la respuesta queda fuera, se eliminan
   R23–R30 y la pantalla de mensajes, y hay que corregir CA1.
2. **¿El remanente de un reembolso parcial debe liquidarse al arrendador?** (P7). Con la regla vigente de HU-19, no.
   Si la respuesta es sí, hace falta una historia (o un cambio de HU-19) que toque `pagadosSinLiquidar` y el estado
   del pago; HU-26 no lo hace.

### Contradicciones entre documentos y código

| # | Contradicción | Cómo la resuelve este spec | Origen |
|---|---------------|----------------------------|--------|
| P13 | `docs/historias/HU-26.md` dice "Programa: Ariel Enriquez"; `feature_list.json` dice Adriana Callupe. | Manda `feature_list.json` (reasignación anotada en `progress/HU-26.md`). | — |
| P14 | `modelo-datos.md` define `reclamo(id, reserva_id, motivo, descripcion, estado, resolucion)` sin partes, fechas ni monto. | `design.md` § 8 agrega `usuario_id`, `arrendador_id`, `abierto_en`, `resuelto_por`, `resuelto_en`, `monto_reembolso` y, en `mensaje_reclamo`, `papel`. El implementer actualiza el documento en el mismo PR. | H6 |
| P15 | RNF-SEG-17 lista "1 reclamo por reserva" entre los límites de tasa. | Se cumple con la restricción única `uq_reclamo_reserva` (principio 7), no con `LimitadorIntentos`. | § 4 |

## Definiciones

- **Usuario / Arrendador / Admin:** sesión válida con rol `USUARIO`, `ARRENDADOR` o `ADMIN`.
- **Reserva propia:** reserva cuyo `usuario_id` es el de la sesión.
- **Inicio y fin de la reserva:** instantes, en `America/Lima`, en que empieza su primer bloque y termina su último,
  contando todas sus filas de `reserva_bloque`, activas o no (`ReservaConHorario.instanteDeInicio` e
  `instanteDeFin`; con `horaFin` 24, las 00:00 del día siguiente).
- **Ventana de reclamo:** `inicio ≤ ahora < fin + 48 h` (P2).
- **Reserva reclamable:** reserva propia, de estado leído `CONFIRMADA`, dentro de la ventana, sin ninguna fila en
  `reclamo` y sin fila en `liquidacion_detalle`.
- **Partes del reclamo:** el usuario que lo abrió (papel `USUARIO`) y el arrendador del espacio de la reserva (papel
  `ARRENDADOR`).
- **Monto pagado:** el `monto` del pago `PAGADO` de la reserva (HU-13: hay como mucho uno).
- **Motivo válido:** texto JSON igual a `ESPACIO_CERRADO`, `DISTINTO_A_LO_PUBLICADO` u `OTRO` (P4).
- **Texto válido de N:** texto JSON no vacío ni en blanco, de hasta N caracteres. Descripción: 1000; mensaje: 1000;
  resolución: 500.
- **Sanitizar:** `comun.texto.SanitizadorTexto.limpiar` (HU-24 DT-8): sin etiquetas, sin `<` ni `>`, sin caracteres de
  control salvo `\n` y `\t`, recortado.
- **Mensajes** (texto exacto en `design.md` § 6): *reserva inválida*, *motivo inválido*, *descripción inválida*,
  *texto inválido*, *resolución inválida*, *a favor inválido*, *reembolso inválido*, *monto inválido*, *grupo
  inválido*, *página inválida*, *no reclamable*, *plazo vencido*, *ya reclamada*, *reclamo cerrado*, *límite de
  mensajes*, *ya resuelto*, *texto eliminado*, *reclamo abierto* (impedimento de HU-22).

## Abrir un reclamo (CA1, RF-F-07, STRIDE E)

## R1
CUANDO un usuario envía `POST /api/reclamos` con el `reservaId` de una reserva reclamable, un motivo válido y una
descripción válida, el sistema DEBE crear una fila en `reclamo` con estado `ABIERTO`, el motivo, la descripción
sanitizada y `abierto_en` = ahora.

## R2
CUANDO el sistema crea un reclamo, el sistema DEBE responder 201 con el detalle del reclamo: `id`, `reservaId`,
`espacioNombre`, `fecha`, `horaInicio`, `horaFin`, `motivo`, `descripcion`, `estado` `ABIERTO`, `abiertoEn` y
`mensajes` vacío.

## R3
El sistema DEBE tomar `usuario_id` de la sesión y `arrendador_id` del espacio de la reserva, ignorando cualquier
`usuarioId`, `arrendadorId`, `estado` u otro campo extra del cuerpo.

## R4
MIENTRAS `inicio ≤ ahora < fin + 48 h`, el sistema DEBE tratar una reserva propia `CONFIRMADA` como dentro de la
ventana de reclamo, incluido el instante exacto `inicio`.

## R5
SI el estado leído de la reserva propia no es `CONFIRMADA` ENTONCES el sistema DEBE rechazar la solicitud con 400 y el
mensaje *no reclamable*, sin crear el reclamo.

## R6
SI la reserva propia `CONFIRMADA` aún no llega a su inicio ENTONCES el sistema DEBE rechazar la solicitud con 400 y el
mensaje *no reclamable*, sin crear el reclamo.

## R7
SI `ahora ≥ fin + 48 h` en una reserva propia `CONFIRMADA` ENTONCES el sistema DEBE rechazar la solicitud con 400 y el
mensaje *plazo vencido*, sin crear el reclamo.

## R8
SI la reserva propia ya figura en `liquidacion_detalle` ENTONCES el sistema DEBE rechazar la solicitud con 400 y el
mensaje *plazo vencido*, sin crear el reclamo.

## R9
SI el `reservaId` es de otra cuenta o no existe ENTONCES el sistema DEBE responder 404 con el mensaje genérico, igual en
los dos casos, sin crear el reclamo.

## R10
SI el rol de la sesión en `POST /api/reclamos` o en `GET /api/reclamos/reclamables` no es `USUARIO` ENTONCES el sistema
DEBE responder 403 sin leer reservas.

## R11
SI una solicitud llega a una ruta de `/api/reclamos` o de `/api/admin/reclamos` sin una sesión válida ENTONCES el
sistema DEBE responder 401.

## Un reclamo por reserva (STRIDE D, RN-12, RNF-SEG-17)

## R12
SI la reserva propia ya tiene una fila en `reclamo`, en cualquier estado ENTONCES el sistema DEBE rechazar la solicitud
con 400 y el mensaje *ya reclamada*, sin crear otro.

## R13
El sistema DEBE garantizar una sola fila de `reclamo` por `reserva_id` con la restricción única `uq_reclamo_reserva`.

## R14
SI dos solicitudes simultáneas chocan con `uq_reclamo_reserva` ENTONCES el sistema DEBE responder 400 con el mensaje
*ya reclamada* a la segunda, en lugar de un 500.

## Validación de la entrada al abrir (STRIDE T)

## R15
SI `reservaId` falta, no es un número JSON entero, no cabe en un `long` o no es mayor que 0 ENTONCES el sistema DEBE
responder 400 en el campo `reservaId` con el mensaje *reserva inválida*.

## R16
SI el motivo no es válido ENTONCES el sistema DEBE responder 400 en el campo `motivo` con el mensaje *motivo inválido*.

## R17
SI la descripción falta, no es un texto JSON, está en blanco o supera 1000 caracteres ENTONCES el sistema DEBE
responder 400 en el campo `descripcion` con el mensaje *descripción inválida*.

## R18
CUANDO el sistema guarda una descripción, un mensaje o una resolución, el sistema DEBE guardarlos sanitizados, de modo
que el texto guardado no contenga ningún `<` ni `>`.

## R19
SI la descripción queda vacía después de sanitizarla ENTONCES el sistema DEBE responder 400 en el campo `descripcion`
con el mensaje *descripción inválida*, sin leer la reserva.

## Retención de la liquidación (CA1, RF-F-07, RN-12)

## R20
MIENTRAS una reserva tiene un reclamo `ABIERTO`, el sistema NO DEBE incluirla en ninguna liquidación que genere
`POST /api/admin/liquidaciones`, aunque cumpla las demás reglas de HU-19.

## R21
CUANDO el sistema abre un reclamo, el sistema DEBE tomar el bloqueo de generación de liquidaciones
(`pg_advisory_xact_lock(190019)`) antes de leer el instante con el que evalúa la ventana y antes de consultar
`liquidacion_detalle`, en la misma transacción que inserta el reclamo.

## R22
CUANDO la transacción que abre un reclamo se confirma, el sistema DEBE enviar al correo del arrendador un aviso con el
nombre del espacio, la fecha, el horario y el motivo, sin la descripción ni el nombre, correo o teléfono del usuario.

## Mensajes entre las partes (CA1 "el arrendador puede responder", STRIDE R, ver P1)

## R23
CUANDO una parte envía `POST /api/reclamos/{id}/mensajes` con un texto válido de 1000 sobre un reclamo `ABIERTO`, el
sistema DEBE crear una fila en `mensaje_reclamo` con `autor_id` de la sesión, su papel (`USUARIO` o `ARRENDADOR`), el
texto sanitizado y `fecha` = ahora, y responder 201 con `id`, `papel`, `texto` y `fecha`.

## R24
SI el reclamo de `POST /api/reclamos/{id}/mensajes` no está `ABIERTO` ENTONCES el sistema DEBE rechazar la solicitud
con 400 y el mensaje *reclamo cerrado*, sin crear el mensaje.

## R25
SI el `id` de una ruta `/api/reclamos/{id}` está mal escrito, no existe o la sesión no es una de sus partes ENTONCES el
sistema DEBE responder 404 con el mensaje genérico, igual en los tres casos.

## R26
SI el rol de la sesión en `GET /api/reclamos`, `GET /api/reclamos/{id}` o `POST /api/reclamos/{id}/mensajes` es
`ADMIN` ENTONCES el sistema DEBE responder 403 sin leer reclamos.

## R27
SI la parte ya envió 10 mensajes en ese reclamo ENTONCES el sistema DEBE rechazar la solicitud con 400 y el mensaje
*límite de mensajes*, sin crear el mensaje.

## R28
SI el texto del mensaje falta, no es un texto JSON, está en blanco, supera 1000 caracteres o queda vacío después de
sanitizarlo ENTONCES el sistema DEBE responder 400 en el campo `texto` con el mensaje *texto inválido*.

## R29
El sistema DEBE contar los mensajes de una parte y comprobar el estado del reclamo con la fila de `reclamo` bloqueada
(`FOR UPDATE`), de modo que dos envíos simultáneos no superen el límite de R27 ni se acepten después de la resolución.

## R30
El sistema NO DEBE ofrecer ninguna ruta que edite o borre un reclamo, un mensaje o una resolución.

## Consulta por las partes (CA1, RNF-SEG-19)

## R31
CUANDO una parte envía `GET /api/reclamos?pagina=n`, el sistema DEBE responder 200 con `pagina`, `reclamos` y `hayMas`:
de a 10, por `abierto_en` e `id` descendentes, solo los reclamos que abrió (`USUARIO`) o los de reservas de sus
espacios (`ARRENDADOR`), cada uno con `id`, `reservaId`, `espacioNombre`, `fecha`, `motivo`, `estado` y `abiertoEn`;
sin `pagina`, la primera.

## R32
CUANDO una parte envía `GET /api/reclamos/{id}` de un reclamo suyo, el sistema DEBE responder 200 con el detalle de R2,
los mensajes en orden de `fecha` e `id` ascendentes, `aFavorDe`, `resolucion` y `resueltoEn` (nulos mientras esté
`ABIERTO`), `papel` de la sesión, `mensajesRestantes` de la sesión, `puedeEscribir` (`ABIERTO` y
`mensajesRestantes > 0`), y `montoReembolso` solo si la sesión es el usuario; al arrendador, sin montos ni nombre,
correo o teléfono del usuario.

## R33
CUANDO un usuario envía `GET /api/reclamos/reclamables`, el sistema DEBE responder 200 con `reservaIds`: los ids de sus
reservas reclamables, en orden ascendente.

## R34
SI `pagina` está presente y no es un entero de 1 a `Integer.MAX_VALUE` ENTONCES el sistema DEBE responder 400 en el campo
`pagina` con el mensaje *página inválida*.

## Panel del admin (CA2, CA3, RF-F-08, RF-F-09)

## R35
CUANDO un admin envía `GET /api/admin/reclamos?grupo=abiertos&pagina=n`, el sistema DEBE responder 200 con los reclamos
`ABIERTO`, de a 10, por `abierto_en` e `id` ascendentes (el más antiguo primero), con `hayMas`.

## R36
CUANDO un admin envía `GET /api/admin/reclamos?grupo=resueltos&pagina=n`, el sistema DEBE responder 200 con los
reclamos resueltos, de a 10, por `resuelto_en` e `id` descendentes, con `hayMas`.

## R37
CUANDO un admin envía `GET /api/admin/reclamos/{id}`, el sistema DEBE responder 200 con los datos del reclamo, de la
reserva, los mensajes y la resolución de R32 (sin `papel`, `mensajesRestantes` ni `puedeEscribir`) más
`usuarioNombre`, `arrendadorNombre`, `montoPagado` y `montoReembolso`, sin correo ni teléfono de las partes ni datos de
la tarjeta.

## R38
SI el rol de la sesión en una ruta de `/api/admin/reclamos` no es `ADMIN` ENTONCES el sistema DEBE responder 403 sin
leer reclamos.

## R39
SI `grupo` falta o no es `abiertos` ni `resueltos` ENTONCES el sistema DEBE responder 400 en el campo `grupo` con el
mensaje *grupo inválido*.

## R40
SI el `id` de una ruta `/api/admin/reclamos/{id}` está mal escrito o no existe ENTONCES el sistema DEBE responder 404
con el mensaje genérico.

## R41
El sistema DEBE responder todas las rutas `GET` de `/api/reclamos` y `/api/admin/reclamos` con `Cache-Control:
no-store`.

## Resolver a favor del usuario (CA2, RF-F-08)

## R42
CUANDO un admin envía `POST /api/admin/reclamos/{id}/resolucion` con `aFavorDe` = `USUARIO`, `reembolso` = `TOTAL` y una
resolución válida de 500 sobre un reclamo `ABIERTO`, el sistema DEBE dejarlo `RESUELTO_USUARIO` con `monto_reembolso` =
monto pagado, `resolucion` sanitizada, `resuelto_por` = el admin y `resuelto_en` = ahora, sin cambiar el estado de la
reserva.

## R43
CUANDO un admin resuelve a favor del usuario con `reembolso` = `PARCIAL` y un `montoReembolso` mayor que 0 y menor que
el monto pagado, el sistema DEBE dejar el reclamo `RESUELTO_USUARIO` con `monto_reembolso` = ese monto.

## R44
CUANDO un admin resuelve a favor del usuario, el sistema DEBE crear, en la misma transacción, una fila de `reembolso`
`PENDIENTE` con `origen` = `RECLAMO` y `monto` = `monto_reembolso` contra el pago `PAGADO` de la reserva, y auditar
`REEMBOLSO_SOLICITADO` con el detalle `origen=RECLAMO;reservaId=<id>;monto=<monto>`.

## R45
CUANDO se confirma la transacción de una resolución a favor del usuario, el sistema DEBE pedir el reembolso a la
pasarela con el Command de HU-16 (`SolicitudesReembolso` + `EjecutorReembolsos.ejecutarDespuesDelCommit`) y dejar
reembolso y pago `REEMBOLSADO`, o `FALLIDO` y `REEMBOLSO_FALLIDO` si la pasarela falla, sin revertir la resolución.

## R46
SI `reembolso` es `PARCIAL` y `montoReembolso` falta, no es un número, tiene más de 2 decimales, no es mayor que 0 o
no es menor que el monto pagado ENTONCES el sistema DEBE responder 400 en el campo `montoReembolso` con el mensaje *monto
inválido*, sin resolver ni crear el reembolso.

## R47
SI `aFavorDe` es `USUARIO` y `reembolso` falta o no es `TOTAL` ni `PARCIAL` ENTONCES el sistema DEBE responder 400 en el
campo `reembolso` con el mensaje *reembolso inválido*, sin resolver.

## R48
MIENTRAS una reserva tiene un reembolso de origen `RECLAMO`, en cualquier estado, el sistema NO DEBE incluirla en
ninguna liquidación (P7).

## Resolver a favor del arrendador (CA3, RF-F-09)

## R49
CUANDO un admin envía `POST /api/admin/reclamos/{id}/resolucion` con `aFavorDe` = `ARRENDADOR` y una resolución válida
de 500 sobre un reclamo `ABIERTO`, el sistema DEBE dejarlo `RESUELTO_ARRENDADOR` con `resolucion` sanitizada,
`resuelto_por`, `resuelto_en` y `monto_reembolso` nulo.

## R50
CUANDO un reclamo pasa a `RESUELTO_ARRENDADOR`, el sistema DEBE incluir su reserva en la siguiente generación de
liquidaciones si cumple las reglas de HU-19.

## R51
CUANDO un admin resuelve a favor del arrendador, el sistema NO DEBE crear ningún reembolso, aunque el cuerpo traiga
`reembolso` o `montoReembolso`.

## Reglas comunes de la resolución (CA2, CA3, STRIDE E)

## R52
SI la resolución falta, no es un texto JSON, está en blanco, supera 500 caracteres o queda vacía después de
sanitizarla ENTONCES el sistema DEBE responder 400 en el campo `resolucion` con el mensaje *resolución inválida*.

## R53
SI `aFavorDe` falta o no es `USUARIO` ni `ARRENDADOR` ENTONCES el sistema DEBE responder 400 en el campo `aFavorDe` con
el mensaje *a favor inválido*.

## R54
SI el reclamo ya no está `ABIERTO` ENTONCES el sistema DEBE rechazar la resolución con 400 y el mensaje *ya resuelto*,
sin crear reembolso, correos ni auditoría.

## R55
El sistema DEBE resolver con la fila de `reclamo` bloqueada (`FOR UPDATE`), de modo que de dos resoluciones
simultáneas del mismo reclamo solo una tenga efecto y la otra reciba 400 *ya resuelto*.

## R56
CUANDO el sistema resuelve un reclamo, el sistema DEBE responder 200 con el detalle de R37 ya resuelto.

## Correos de la resolución (CA2, CA3, RNF-USA-04)

## R57
CUANDO se confirma la transacción de una resolución, el sistema DEBE enviar al correo del usuario un aviso con el nombre
del espacio, la fecha, el horario, a favor de quién se resolvió, la resolución y, si fue a su favor, el monto del
reembolso solicitado.

## R58
CUANDO se confirma la transacción de una resolución, el sistema DEBE enviar al correo del arrendador un aviso con el
nombre del espacio, la fecha, el horario, a favor de quién se resolvió y la resolución, sin montos ni datos del
usuario.

## R59
SI la transacción de una resolución se revierte ENTONCES el sistema NO DEBE enviar los correos ni llamar a la pasarela.

## Auditoría (STRIDE R, RNF-SEG-12)

## R60
CUANDO el sistema crea un reclamo, el sistema DEBE registrar en `auditoria`, en la misma transacción, la acción
`RECLAMO_ABIERTO` con entidad `reclamo`, su id, el usuario, la IP y el detalle `reservaId=<id>;motivo=<MOTIVO>`, sin la
descripción.

## R61
CUANDO el sistema crea un mensaje, el sistema DEBE registrar en `auditoria`, en la misma transacción, la acción
`RECLAMO_MENSAJE` con entidad `reclamo`, su id, el autor, la IP y el detalle `mensajeId=<id>;papel=<PAPEL>`, sin el
texto.

## R62
CUANDO el sistema resuelve un reclamo, el sistema DEBE registrar en `auditoria`, en la misma transacción, la acción
`RECLAMO_RESUELTO` con entidad `reclamo`, su id, el admin, la IP y el detalle `reservaId=<id>;aFavorDe=<PARTE>` más
`;reembolso=<TOTAL|PARCIAL>;monto=<monto>` si fue a favor del usuario, sin el texto de la resolución.

## R63
SI el sistema rechaza una solicitud de abrir, enviar un mensaje o resolver ENTONCES el sistema NO DEBE registrar
`RECLAMO_ABIERTO`, `RECLAMO_MENSAJE` ni `RECLAMO_RESUELTO`.

## Integridad en la base de datos (principio 7, STRIDE T y R)

## R64
El sistema DEBE rechazar en la base de datos un `estado` de `reclamo` distinto de `ABIERTO`, `RESUELTO_USUARIO` o
`RESUELTO_ARRENDADOR`, un `motivo` fuera del catálogo y un `papel` de `mensaje_reclamo` distinto de `USUARIO` o
`ARRENDADOR`, con restricciones `CHECK`.

## R65
El sistema DEBE rechazar en la base de datos un `reclamo` cuyos datos de resolución no sean coherentes con su estado
(`ck_reclamo_resolucion`: `ABIERTO` sin resolución; `RESUELTO_USUARIO` con resolución, admin, fecha y `monto_reembolso
> 0`; `RESUELTO_ARRENDADOR` con resolución, admin y fecha y sin monto).

## R66
El sistema DEBE rechazar en la base de datos el borrado de una `reserva`, un `usuario` o un `reclamo` referenciados por
un reclamo o un mensaje, con llaves foráneas sin cascada.

## Datos personales (HU-22, RNF-CUM-04, ver P10)

## R67
CUANDO el titular con rol `USUARIO` o `ARRENDADOR` exporta sus datos, el sistema DEBE incluir, después de las secciones
existentes, la sección `reclamos` con cada reclamo del que es parte: `reservaId`, `espacioNombre`, `motivo`, `estado`,
`abiertoEn`, `resueltoEn`, `aFavorDe` y `misTextos` (la descripción si lo abrió y sus mensajes), aunque la lista quede
vacía.

## R68
CUANDO el sistema exporta la sección `reclamos`, el sistema NO DEBE incluir los textos escritos por la otra parte ni la
resolución del admin.

## R69
MIENTRAS el titular es parte de un reclamo `ABIERTO`, el participante de reclamos DEBE devolver el impedimento con
campo `reclamos` y el mensaje *reclamo abierto* al evaluar la eliminación de su cuenta.

## R70
CUANDO se elimina una cuenta, el sistema DEBE reemplazar por el mensaje *texto eliminado*, en la misma transacción, la
descripción de los reclamos que abrió y el texto de los mensajes que escribió, conservando estados, fechas, motivos,
montos y resoluciones.

## Trazabilidad con la historia

| Origen (`docs/historias/HU-26.md`)                                         | Cubierto por |
|----------------------------------------------------------------------------|--------------|
| CA1: reclamo con motivo y descripción en menos de 48 h → "reclamo abierto" | R1, R2, R3, R4, R5, R6, R7, R8, R9, R15, R16, R17, R19, R31, R32, R33 |
| CA1: el arrendador puede responder                                         | R22, R23, R24, R25, R27, R28, R29, R32 |
| CA1: la reserva no entra en ninguna liquidación hasta resolverse           | R20, R21 |
| CA2: admin a favor del usuario → reembolso total o parcial (HU-16)         | R35, R37, R42, R43, R44, R45, R46, R47, R48, R56 |
| CA2: ambos reciben un correo con la resolución                             | R57, R58, R59 |
| CA3: admin a favor del arrendador con motivo → vuelve a ser liquidable     | R36, R49, R50, R51, R52, R56 |
| CA3: ambos reciben un correo                                               | R57, R58, R59 |
| (E) Solo el usuario de la reserva abre el reclamo                          | R3, R9, R10, R25, R26 |
| (E) Solo un admin lo resuelve                                              | R38, R53, R54, R55 |
| (D) Un reclamo por reserva                                                 | R12, R13, R14, R27, R29 |
| (R) Registro de mensajes y resolución                                      | R23, R30, R60, R61, R62, R63, R65, R66 |
| Simplificación: sin adjuntar fotos                                         | R1, R23 (solo texto; ninguna ruta acepta archivos) |

| Origen (`docs/requerimientos.md` y `architecture.md`)                      | Cubierto por |
|----------------------------------------------------------------------------|--------------|
| RF-F-07                                                                    | R1, R4, R20, R23 |
| RF-F-08                                                                    | R42, R43, R44, R45, R57, R58 |
| RF-F-09                                                                    | R49, R50, R57, R58 |
| RN-12: retención, un reclamo por reserva, menos de 48 h                    | R7, R8, R12, R13, R20, R21 |
| RNF-SEG-10 y §8 (sanitización de texto libre)                              | R18, R19, R28, R52 |
| RNF-SEG-12                                                                 | R44, R60, R61, R62 |
| RNF-SEG-16 (minimización, por P12)                                         | R22, R32, R37, R58 |
| RNF-SEG-17 ("1 reclamo por reserva", por P15)                              | R12, R13, R14 |
| RNF-SEG-19 (propiedad del reclamo)                                         | R9, R25, R31 |
| RNF-USA-04 (avisos por correo, por P9)                                     | R22, R57, R58 |
| RNF-CUM-04 y puerto de HU-22 (por P10)                                     | R67, R68, R69, R70 |
| Principio 7 (integridad en la base)                                        | R13, R64, R65, R66 |
| Puerto `pagos.ReclamosAbiertos` y regla de 48 h de HU-19                   | R4, R7, R20, R21, R50 |
