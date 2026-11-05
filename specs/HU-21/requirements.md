# HU-21 — Requirements: Pausar o dar de baja una publicación

> Módulo `espacios` (y `reservas` para el filtro de reservas afectadas) · Release 2 · Sprint 6 · tamaño S ·
> depende de HU-07 y HU-15 (`done`). Dueño: Sebastian Garate · Prueba y documenta: Adriana Callupe.
>
> Fuentes: `docs/historias/HU-21.md` (CA1, CA2, STRIDE E y R), `docs/requerimientos.md` (RF-B-09, RF-B-10,
> RNF-SEG-12, RNF-SEG-19, RN-15, RN-16), `docs/architecture.md` (estados del espacio, puerto `ReservasDelEspacio`,
> cancelación HU-15), `specs/HU-07`, `specs/HU-15` y `progress/explore_HU-21.md`.
>
> Las decisiones de producto tomadas por defecto (transiciones, qué reserva impide la baja, quién cancela, alcance del
> admin) están en `design.md` § 2 "Decisiones para revisar por el dueño". Los textos de los mensajes están en
> `design.md` § 9.

## Glosario

- **Arrendador habilitado:** sesión con rol `ARRENDADOR` y correo verificado (regla de HU-07, `CuentaService.habilitacionArrendador`).
- **Espacio propio:** espacio cuyo `arrendador_id` es el de la sesión. Uno ajeno responde igual que uno inexistente.
- **Reserva por terminar:** reserva del espacio cuyo estado leído con `Reserva.estadoEn(ahora)` es `CONFIRMADA` o
  `PENDIENTE_PAGO` (vigente) y que tiene al menos un bloque activo cuyo fin (`fecha` + `horaInicio` + 1 h, en
  `America/Lima`) es posterior a la hora actual de Lima. Incluye las futuras, las que están en curso y las pendientes de
  pago vigentes.
- **Rutas del arrendador:** `POST /api/arrendadores/actual/espacios/{id}/pausa`, `…/{id}/reanudacion` y `…/{id}/baja`.
- **Rutas del admin:** `POST /api/admin/espacios/{id}/pausa` y `POST /api/admin/espacios/{id}/baja`.

## Pausar (CA1, RF-B-09, RN-15)

## R1
CUANDO un arrendador habilitado envía `POST /api/arrendadores/actual/espacios/{id}/pausa` sobre un espacio propio en
estado `PUBLICADO`, el sistema DEBE dejar el espacio en `PAUSADO` y responder 200 con el `EspacioDTO` del espacio con
`estado = "PAUSADO"`.

## R2
MIENTRAS un espacio está en `PAUSADO` o en `BAJA`, el sistema NO DEBE incluirlo en los resultados de la búsqueda pública
`GET /api/espacios`.

## R3
MIENTRAS un espacio está en `PAUSADO` o en `BAJA`, el sistema DEBE responder 404 con el mensaje genérico a
`GET /api/espacios/{id}` (ficha pública).

## R4
MIENTRAS un espacio está en `PAUSADO` o en `BAJA`, el sistema DEBE rechazar con 404 un `POST /api/reservas` sobre ese
espacio sin crear la reserva ni retener bloques.

## R5
CUANDO un espacio se pausa, el sistema DEBE conservar sin cambios el estado y los bloques activos de sus reservas
`CONFIRMADA` y `PENDIENTE_PAGO`.

## R6
SI el espacio de la ruta de pausa (del arrendador o del admin) no está en `PUBLICADO` ENTONCES el sistema DEBE
responder 400 con el mensaje *solo puedes pausar un espacio publicado* sin cambiar el espacio.

## Reanudar (CA1, máquina de estados `PUBLICADO ⇄ PAUSADO`)

## R7
CUANDO un arrendador habilitado con identidad `VERIFICADA` envía `POST /api/arrendadores/actual/espacios/{id}/reanudacion`
sobre un espacio propio en `PAUSADO` que tiene al menos una foto, el sistema DEBE dejarlo en `PUBLICADO` y responder 200
con su `EspacioDTO`.

## R8
SI el espacio de la ruta de reanudación no está en `PAUSADO` ENTONCES el sistema DEBE responder 400 con el mensaje
*solo puedes reanudar un espacio pausado* sin cambiar el espacio.

## R9
SI el arrendador que reanuda no tiene la identidad `VERIFICADA` ENTONCES el sistema DEBE responder 400 con el mensaje
de HU-07 *para publicar tus espacios, primero debes verificar tu identidad* sin cambiar el espacio.

## R10
SI el espacio que se reanuda no tiene fotos ENTONCES el sistema DEBE responder 400 con el mensaje de HU-07 *agrega al
menos una foto antes de publicar* sin cambiar el espacio.

## R11
SI una reanudación incumple más de una de las reglas R8, R9 y R10 ENTONCES el sistema DEBE responder con el mensaje de
la primera en el orden estado → identidad → fotos.

## Dar de baja (CA2, RF-B-10, RN-16)

## R12
CUANDO un arrendador habilitado envía `POST /api/arrendadores/actual/espacios/{id}/baja` sobre un espacio propio en
`PUBLICADO` o `PAUSADO` que no tiene reservas por terminar, el sistema DEBE dejarlo en `BAJA` y responder 200 con su
`EspacioDTO`.

## R13
SI el espacio de la ruta de baja del arrendador tiene al menos una reserva `CONFIRMADA` cuyo inicio es posterior a la
hora actual de Lima ENTONCES el sistema DEBE responder 400 con un error en el campo `reservas` y el mensaje *este espacio
tiene reservas por terminar* sin cambiar el espacio.

## R14
SI el espacio de la ruta de baja tiene una reserva `CONFIRMADA` en curso (ya empezó y no terminó) o una
`PENDIENTE_PAGO` vigente ENTONCES el sistema DEBE rechazar la baja igual que en R13.

## R15
CUANDO se pide la baja de un espacio cuyas reservas están todas `CANCELADA`, `EXPIRADA`, `PENDIENTE_PAGO` vencidas o
`CONFIRMADA` ya terminadas, el sistema DEBE completar la baja como en R12.

## R16
SI el espacio de la ruta de baja (del arrendador o del admin) no está en `PUBLICADO` ni en `PAUSADO` ENTONCES el sistema
DEBE responder 400 con el mensaje *solo puedes dar de baja un espacio publicado o pausado*, antes de consultar sus
reservas y sin cambiar el espacio.

## R17
CUANDO una baja y la creación de una reserva del mismo espacio llegan a la vez, el sistema DEBE serializarlas en el
bloqueo de la fila del espacio, de modo que o la reserva se crea y la baja se rechaza por R13/R14, o la baja se completa
y la reserva responde 404.

## R18
MIENTRAS un espacio está en `BAJA`, el sistema DEBE rechazar con 400 y el mensaje *este espacio ya no se puede editar*
la edición de su ficha (`PUT /api/arrendadores/actual/espacios/{id}`).

## Reservas afectadas y su cancelación (CA2)

## R19
CUANDO un arrendador envía `GET /api/arrendadores/actual/reservas?grupo=proximas&espacioId={id}` con el id de un
espacio propio, el sistema DEBE devolver solo las reservas `CONFIRMADA` de ese espacio, con el mismo DTO, orden y
paginación de HU-14.

## R20
SI el parámetro `espacioId` de `GET /api/arrendadores/actual/reservas` no es un número entero positivo ENTONCES el
sistema DEBE responder 400 con un error en el campo `espacioId`, junto con los errores de `grupo` y `pagina` si los hay.

## R21
SI el `espacioId` de `GET /api/arrendadores/actual/reservas` no existe o es de otro arrendador ENTONCES el sistema DEBE
responder 404 con el mensaje genérico.

> Nota (2026-11-04, decisión de Sebastian Garate): R21 prevalece sobre HU-14 R7 solo para `espacioId` en
> `GET /api/arrendadores/actual/reservas`; `GET /api/reservas` sigue ignorando `espacioId`. Ver `design.md` DT-7 y DT-12.

## R22
CUANDO `GET /api/arrendadores/actual/reservas` llega sin `espacioId`, el sistema DEBE responder exactamente como en
HU-14 (todas las reservas `CONFIRMADA` de los espacios del arrendador).

## R23
CUANDO el arrendador confirma por `POST /api/arrendadores/actual/reservas/{id}/cancelacion` la cancelación de una
reserva `CONFIRMADA` futura de un espacio propio en `PAUSADO`, el sistema DEBE cancelarla con la política `ARRENDADOR` y
un reembolso del 100 % del monto pagado.

## R24
CUANDO el arrendador cancela la última reserva por terminar de un espacio cuya baja fue rechazada por R13, el sistema
DEBE completar la siguiente solicitud de baja de ese espacio como en R12.

## Control de acceso (STRIDE E, RNF-SEG-19)

## R25
SI la sesión de una ruta del arrendador no tiene el rol `ARRENDADOR` ENTONCES el sistema DEBE responder 403 sin leer el
espacio.

## R26
SI el arrendador de una ruta del arrendador no tiene el correo verificado ENTONCES el sistema DEBE responder 403 sin
cambiar el espacio.

## R27
SI el espacio de una ruta del arrendador no existe o pertenece a otro arrendador ENTONCES el sistema DEBE responder 404
con el mensaje genérico sin cambiar el espacio.

## R28
CUANDO una sesión `ADMIN` envía `POST /api/admin/espacios/{id}/pausa` sobre un espacio `PUBLICADO` de cualquier
arrendador, el sistema DEBE dejarlo en `PAUSADO` y responder 200 con `EstadoEspacioDTO(id, estado)`.

## R29
CUANDO una sesión `ADMIN` envía `POST /api/admin/espacios/{id}/baja` sobre un espacio `PUBLICADO` o `PAUSADO` de
cualquier arrendador sin reservas por terminar, el sistema DEBE dejarlo en `BAJA` y responder 200 con
`EstadoEspacioDTO(id, estado)`.

## R30
SI el espacio de `POST /api/admin/espacios/{id}/baja` tiene reservas por terminar ENTONCES el sistema DEBE responder
400 con un error en el campo `reservas` y el mensaje *el arrendador debe cancelarlas antes* sin cambiar el espacio.

## R31
SI el espacio de una ruta del admin no existe ENTONCES el sistema DEBE responder 404 con el mensaje genérico.

## R32
SI la sesión de una ruta del admin no tiene el rol `ADMIN` ENTONCES el sistema DEBE responder 403 sin leer el espacio.

## R33
SI una ruta del admin llega desde una IP que no está en `ADMIN_IPS_PERMITIDAS` ENTONCES el sistema DEBE responder 403
sin cambiar el espacio.

## Registro (STRIDE R, RNF-SEG-12)

## R34
CUANDO un espacio pasa de `PUBLICADO` a `PAUSADO`, el sistema DEBE registrar en la auditoría la acción `PAUSA_ESPACIO`
con entidad `espacio`, el id del espacio, el `usuario_id` de la sesión, la IP y el detalle `estado=PUBLICADO->PAUSADO`.

## R35
CUANDO un espacio pasa de `PAUSADO` a `PUBLICADO`, el sistema DEBE registrar en la auditoría la acción
`REANUDACION_ESPACIO` con entidad `espacio`, el id, el `usuario_id` de la sesión, la IP y el detalle
`estado=PAUSADO->PUBLICADO`.

## R36
CUANDO un espacio pasa a `BAJA`, el sistema DEBE registrar en la auditoría la acción `BAJA_ESPACIO` con entidad
`espacio`, el id, el `usuario_id` de la sesión, la IP y el detalle `estado=<anterior>->BAJA`.

## R37
CUANDO quien pausa o da de baja es un `ADMIN`, el sistema DEBE agregar al detalle de la auditoría
`;actor=ADMIN;arrendadorId=<id del dueño del espacio>`.

## R38
SI una solicitud de pausa, reanudación o baja se rechaza (403, 404 o 400) ENTONCES el sistema NO DEBE registrar
auditoría de esa acción ni cambiar `estado` ni `actualizado_en` del espacio.

## R39
CUANDO un espacio cambia de estado por pausa, reanudación o baja, el sistema DEBE fijar su `actualizado_en` en el
instante del `Clock`.

## Listado propio

## R40
CUANDO un arrendador habilitado envía `GET /api/arrendadores/actual/espacios`, el sistema DEBE incluir sus espacios en
`PAUSADO` y en `BAJA`, cada uno con su estado.

## Trazabilidad con la historia

| Origen (docs/historias/HU-21.md y docs/requerimientos.md)                         | Cubierto por |
|-----------------------------------------------------------------------------------|--------------|
| CA1: al pausar y confirmar, deja de aparecer en búsquedas                         | R1, R2, R3, R4, R6 |
| CA1: las reservas existentes se mantienen                                         | R5, R23 |
| CA1 (máquina de estados `PUBLICADO ⇄ PAUSADO`): reanudar                          | R7, R8, R9, R10, R11 |
| CA2: dar de baja al confirmar                                                     | R12, R15, R16, R18, R40 |
| CA2: con reservas futuras, veo las reservas afectadas                             | R13, R14, R19, R20, R21, R22 |
| CA2: debo cancelarlas (reembolso 100 %) antes de completar la baja                | R13, R14, R17, R23, R24 |
| (E) Solo el arrendador dueño o un admin; validado en backend                      | R25, R26, R27, R28, R29, R30, R31, R32, R33 |
| (R) Registro de pausas y bajas                                                    | R34, R35, R36, R37, R38, R39 |
| RF-B-09 / RN-15 (pausado no se muestra)                                           | R1, R2, R3, R4 |
| RF-B-10 / RN-16 (baja exige cancelar antes, 100 %)                                | R13, R14, R19, R23, R24 |
| RNF-SEG-12 (auditoría de cambios de espacios)                                     | R34–R39 |
| RNF-SEG-19 (propiedad antes de cada edición)                                      | R21, R27 |
