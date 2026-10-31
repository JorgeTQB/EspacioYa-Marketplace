# HU-15 — Requirements: Cancelación de reservas

> Fuente: `docs/historias/HU-15.md` (CA1–CA3, STRIDE E, T, R; simplificación "política fija"),
> `docs/historias/HU-16.md` (límite: HU-16 ejecuta el reembolso), `docs/requerimientos.md` (RF-D-07, RF-D-08, RF-D-09,
> RNF-SEG-07, RNF-SEG-12, RNF-SEG-19, RNF-USA-04, RN-03, RN-06, RN-14), `docs/architecture.md` ("Reservas: qué deja
> HU-12", "Pagos: qué deja HU-13", "Mis reservas: qué deja HU-14", estados, patrón ★ Strategy), `docs/modelo-datos.md`
> (`reserva`, `reserva_bloque`, `pago`, `reembolso`), `specs/HU-14/design.md` § 11.4 y `progress/explore_HU-15.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API** y de las interfaces que usan otros
> módulos; la presentación está en `design.md` § 12 Pantallas y las decisiones técnicas (DT-n) en `design.md` § 1.

## Decisiones por defecto — confirmar en la aprobación

> ⚠ La historia no fija estos puntos. El spec adopta estos valores para que el dueño (**Kevin Chan**) los acepte o
> los cambie al aprobar; si alguno cambia, se ajustan los requirements citados antes de pasar a `in_progress`.

| # | Hueco | Decisión | Afecta |
|---|-------|----------|--------|
| D1 | Rutas | Una ruta por papel, como HU-14: el usuario usa `GET` y `POST /api/reservas/{id}/cancelacion`; el arrendador, `GET` y `POST /api/arrendadores/actual/reservas/{id}/cancelacion`. `GET` es la vista previa (CA2) y `POST` la confirmación (CA3). Cada ruta exige su rol; otro rol recibe 403. El papel de quien cancela (usuario o arrendador) sale de la ruta y del rol de la sesión, nunca del cuerpo. | R1–R7 |
| D2 | ¿Qué se puede cancelar? | Solo una reserva cuyo estado leído (`estadoEn(ahora)`) es `CONFIRMADA` y cuyo **inicio es posterior a *ahora***. Una reserva ya empezada o terminada no se cancela, tampoco por el arrendador. `PENDIENTE_PAGO` → `CANCELADA` no existe (HU-12 S7, `architecture.md`). | R8–R10 |
| D3 | Límite de 48 h | Anticipación = inicio de la reserva (fecha y menor `hora_inicio`, en `America/Lima`) − *ahora*. Con **48 h exactas o más** → 100 %; con menos, aunque sea un segundo, → 0 % (RN-06 "con 48 h o más"). | R11, R12, R14 |
| D4 | Base del monto | El reembolso se calcula sobre `reserva.monto_total`: con HU-13 una reserva solo llega a `CONFIRMADA` con un pago `PAGADO` de ese mismo monto (`MONTO_DISTINTO` no confirma). Monto = `monto_total × porcentaje / 100`, con 2 decimales. | R16 |
| D5 | Monto visto (CA2) | El `POST` lleva `montoReembolsoVisto`, el monto que la pantalla mostró en la vista previa. El servidor **recalcula** el monto bajo bloqueo y, si no coincide, rechaza con 400 sin cancelar (p. ej. la vista previa se pidió a 48 h y la confirmación llegó a 47 h 59 min). El servidor nunca usa ese valor como monto: solo lo compara. | R30–R32 |
| D6 | Reembolso de 0 % | La cancelación se hace igual (estado, bloques, auditoría con monto `0.00` y correos), pero **no** se dispara ningún reembolso en `pagos`. | R12, R23 |
| D7 | "Se dispara el reembolso (HU-16)" | HU-15 crea la interfaz `pagos.service.PagoService` (la pide `architecture.md`) con `reembolsarCancelacion(...)`, llamada **dentro** de la transacción de la cancelación. En HU-15 su implementación solo comprueba que existe el pago `PAGADO` de la reserva y registra la solicitud en la auditoría (`REEMBOLSO_SOLICITADO`); **no** llama a la pasarela, **no** cambia el estado del pago y **no** crea la tabla `reembolso`. HU-16 completa ese método (Command, `PasarelaPago.reembolsar`, tabla `reembolso`, idempotencia, `REEMBOLSADO` / `REEMBOLSO_FALLIDO`) sin cambiar la firma. | R22–R25 |
| D8 | Correos | Al usuario: espacio, fecha, horario, quién canceló, porcentaje y monto a reembolsar. Al arrendador: espacio, fecha, horario y quién canceló, **sin** nombre, correo ni teléfono del usuario y **sin** montos (como `AvisoReservaPagada` de HU-13, RNF-SEG-16). Se envían siempre (RN-14: no son desactivables), después del commit; si fallan, la cancelación no se revierte. | R26–R29 |
| D9 | ¿Dónde se ve la cancelada? | Sin cambios en HU-14: para el usuario cae en "Pasadas" con estado `CANCELADA` (HU-14 R23) y para el arrendador **desaparece** de "Reservas de mis espacios" (solo `CONFIRMADA`, HU-14 R15); su constancia es el correo. `ReservaDTO` no cambia. | R37, R38 |
| D10 | Botón | "Cancelar reserva" solo en las tarjetas `CONFIRMADA` de **Próximas**, en "Mis reservas" y en "Reservas de mis espacios" (HU-14 § 11.4). No en `/reservas/:id`. | Pantallas |
| D11 | Rate limiting | Ninguno: RNF-SEG-17 no fija cupo para esta acción, que exige sesión, rol y propiedad, y solo puede ocurrir una vez por reserva. | — |
| D12 | Filas `sim-` (RS-5) | HU-15 no distingue los pagos de la pasarela simulada: la solicitud se registra igual. Qué hacer con ellos al ejecutar el reembolso lo decide HU-16. | R24 |

## Definiciones

- **Usuario con sesión:** cuenta con rol `USUARIO` y sesión vigente. **Arrendador con sesión:** cuenta con rol
  `ARRENDADOR` y sesión vigente.
- **Ruta del usuario:** `GET` o `POST /api/reservas/{id}/cancelacion`. **Ruta del arrendador:** `GET` o
  `POST /api/arrendadores/actual/reservas/{id}/cancelacion`. **Las cuatro rutas:** ambas, con ambos métodos.
- **Vista previa:** el `GET` de una de las rutas. **Confirmación:** el `POST` de una de las rutas.
- **Quien cancela:** `USUARIO` en la ruta del usuario y `ARRENDADOR` en la ruta del arrendador (D1).
- **Id bien escrito:** texto de 1 a 19 dígitos cuyo valor es un `long` mayor que 0 (como `GET /api/reservas/{id}`,
  HU-12 DT-9).
- **Reserva propia:** en la ruta del usuario, la reserva cuyo `usuario_id` es el id de la sesión; en la del arrendador,
  la reserva cuyo espacio tiene `arrendador_id` igual al id de la sesión.
- **Ahora:** el instante del reloj del sistema (`Clock`). **Estado leído:** `Reserva.estadoEn(ahora)` (HU-12 D2).
- **Inicio de la reserva:** la fecha de sus filas de `reserva_bloque` a la menor `hora_inicio`, en `America/Lima`,
  como instante (D3). **Anticipación:** inicio − *ahora*.
- **Reserva cancelable:** estado leído `CONFIRMADA` e inicio posterior a *ahora* (D2).
- **Política de reembolso:** una de las tres reglas fijas de RN-06, con su código: `USUARIO_CON_ANTICIPACION`
  (usuario, anticipación ≥ 48 h, 100 %), `USUARIO_SIN_ANTICIPACION` (usuario, anticipación < 48 h, 0 %) y `ARRENDADOR`
  (arrendador, cualquier anticipación, 100 %).
- **Monto a reembolsar:** `monto_total × porcentaje / 100`, con escala 2 (D4).
- **VistaCancelacionDTO:** cuerpo con exactamente `reservaId`, `espacioNombre`, `fecha`, `horaInicio`, `horaFin`,
  `montoPagado`, `politica`, `porcentaje` y `montoReembolso` (`design.md` § 5).
- **CancelacionDTO:** cuerpo con exactamente `reservaId`, `estado`, `canceladaPor`, `canceladaEn`, `politica`,
  `porcentaje` y `montoReembolso` (`design.md` § 5).
- **Mensajes** (texto exacto en `design.md` § 5): *sesión inválida* (HU-03), *acceso denegado*, *no encontrado*,
  *ya cancelada*, *no confirmada*, *ya iniciada*, *monto visto* y *monto cambió*.

## Sesión, rol y propiedad (STRIDE E, RNF-SEG-19, D1)

## R1
SI un cliente sin sesión vigente envía una solicitud a cualquiera de las cuatro rutas ENTONCES el sistema DEBE
responder 401 con el mensaje *sesión inválida*.

## R2
SI una cuenta con rol distinto de `USUARIO` envía una solicitud a la ruta del usuario ENTONCES el sistema DEBE
responder 403 con el mensaje *acceso denegado* sin leer ni modificar la reserva.

## R3
SI una cuenta con rol distinto de `ARRENDADOR` envía una solicitud a la ruta del arrendador ENTONCES el sistema DEBE
responder 403 con el mensaje *acceso denegado* sin leer ni modificar la reserva.

## R4
SI el `{id}` de la ruta no es un id bien escrito ENTONCES el sistema DEBE responder 404 con el mensaje *no encontrado*.

## R5
SI un usuario con sesión envía una solicitud a la ruta del usuario para una reserva que no existe o que no es propia
ENTONCES el sistema DEBE responder 404 con el mensaje *no encontrado* sin modificar la reserva.

## R6
SI un arrendador con sesión envía una solicitud a la ruta del arrendador para una reserva que no existe o cuyo espacio
no es suyo ENTONCES el sistema DEBE responder 404 con el mensaje *no encontrado* sin modificar la reserva.

## R7
CUANDO el sistema procesa cualquiera de las cuatro rutas, el sistema DEBE tomar la identidad de quien cancela de la
sesión y su papel de la ruta, e ignorar cualquier dato de la solicitud distinto de `montoReembolsoVisto`, como
`usuarioId`, `canceladaPor`, `porcentaje`, `politica` o `montoReembolso` (RNF-SEG-01).

## Qué se puede cancelar (RN-03, D2)

## R8
SI el estado leído de la reserva propia es `CANCELADA` ENTONCES el sistema DEBE responder a la vista previa y a la
confirmación 400 con el mensaje *ya cancelada* sin modificar la reserva.

## R9
SI el estado leído de la reserva propia es `PENDIENTE_PAGO` o `EXPIRADA` ENTONCES el sistema DEBE responder a la vista
previa y a la confirmación 400 con el mensaje *no confirmada* sin modificar la reserva.

## R10
SI la reserva propia es `CONFIRMADA` y su inicio no es posterior a *ahora* ENTONCES el sistema DEBE responder a la
vista previa y a la confirmación 400 con el mensaje *ya iniciada* sin modificar la reserva.

## Política de reembolso (CA1, RN-06, RF-D-07, ★ Strategy)

## R11
CUANDO quien cancela es `USUARIO` y la anticipación es de 48 horas o más, el sistema DEBE aplicar la política
`USUARIO_CON_ANTICIPACION` con porcentaje 100.

## R12
CUANDO quien cancela es `USUARIO` y la anticipación es menor de 48 horas, el sistema DEBE aplicar la política
`USUARIO_SIN_ANTICIPACION` con porcentaje 0.

## R13
CUANDO quien cancela es `ARRENDADOR`, el sistema DEBE aplicar la política `ARRENDADOR` con porcentaje 100, sea cual sea
la anticipación.

## R14
El sistema DEBE calcular la anticipación como la diferencia entre el inicio de la reserva, tomado en
`America/Lima`, y *ahora* del reloj del sistema (D3).

## R15
CUANDO el servicio calcula el reembolso de una cancelación, el sistema DEBE elegir exactamente una política de
reembolso entre las tres reglas fijas, sin leer ninguna configuración (★ Strategy; simplificación "política fija").

## R16
El sistema DEBE calcular el monto a reembolsar como `monto_total × porcentaje / 100` con escala 2 (D4).

## Vista previa (CA2, RF-D-08)

## R17
CUANDO un usuario o un arrendador con sesión envía la vista previa de una reserva propia cancelable, el sistema DEBE
responder 200 con un VistaCancelacionDTO con la política, el porcentaje y el monto a reembolsar calculados en ese
momento.

## R18
CUANDO el sistema responde a la vista previa, el sistema NO DEBE escribir en `reserva`, `reserva_bloque`, `pago` ni
`auditoria`.

## Confirmación (CA3, RF-D-09)

## R19
CUANDO un usuario o un arrendador con sesión envía la confirmación de una reserva propia cancelable con un
`montoReembolsoVisto` igual al monto a reembolsar recalculado, el sistema DEBE dejar la reserva en `CANCELADA` y
responder 200 con un CancelacionDTO.

## R20
CUANDO el sistema cancela una reserva, el sistema DEBE guardar en ella `cancelada_por` (quien cancela), `cancelada_en`
(*ahora*) y `monto_reembolso` (el monto a reembolsar) (STRIDE R).

## R21
CUANDO el sistema cancela una reserva, el sistema DEBE dejar `activo = false` en todas sus filas de `reserva_bloque`,
de modo que esas horas se puedan reservar de nuevo (CA3 "los bloques se liberan").

## R22
CUANDO el sistema cancela una reserva con monto a reembolsar mayor que 0, el sistema DEBE invocar
`PagoService.reembolsarCancelacion` con el id de la reserva y ese monto dentro de la misma transacción de la
cancelación (CA3 "se dispara el reembolso", D7).

## R23
CUANDO el sistema cancela una reserva con monto a reembolsar igual a 0, el sistema NO DEBE invocar
`PagoService.reembolsarCancelacion` (D6).

## R24
CUANDO otro módulo invoca `PagoService.reembolsarCancelacion`, el sistema DEBE registrar en `auditoria` la acción
`REEMBOLSO_SOLICITADO` sobre el pago `PAGADO` de esa reserva, con el monto y `origen=CANCELACION`, sin llamar a la
pasarela ni cambiar el estado del pago (D7, D12).

## R25
SI se confirma la cancelación de una reserva con monto a reembolsar mayor que 0 y la reserva no tiene un pago `PAGADO`
ENTONCES el sistema DEBE responder 500 con el mensaje genérico y dejar la reserva, sus bloques y la auditoría como
estaban.

## R26
CUANDO se confirma la transacción de una cancelación, el sistema DEBE enviar al correo del usuario de la reserva un
aviso con el espacio, la fecha, el horario, quien canceló, el porcentaje y el monto a reembolsar (CA3, RNF-USA-04,
RN-14, D8).

## R27
CUANDO se confirma la transacción de una cancelación, el sistema DEBE enviar al correo del arrendador del espacio un
aviso con el espacio, la fecha, el horario y quien canceló, sin datos del usuario ni montos (CA3, RNF-SEG-16, D8).

## R28
SI falla el envío de uno de los correos de una cancelación ENTONCES el sistema DEBE mantener la cancelación,
intentar el otro correo y responder 200.

## R29
SI una confirmación termina con un error ENTONCES el sistema NO DEBE enviar ningún correo de cancelación.

## Monto calculado en el servidor (STRIDE T, RNF-SEG-07, D5)

## R30
El sistema DEBE calcular la política, el porcentaje y el monto a reembolsar de la vista previa y de la confirmación
solo con datos del servidor (reserva, horario, quien cancela y *ahora*), sin usar ningún valor enviado por el cliente.

## R31
SI la confirmación no trae `montoReembolsoVisto`, o lo trae negativo o con más de 2 decimales, ENTONCES el sistema
DEBE responder 400 con un error en el campo `montoReembolsoVisto` y el mensaje *monto visto* sin modificar la reserva.

## R32
SI el `montoReembolsoVisto` de la confirmación es distinto del monto a reembolsar recalculado ENTONCES el sistema DEBE
responder 400 con un error en el campo `montoReembolsoVisto` y el mensaje *monto cambió* sin modificar la reserva.

## R33
CUANDO el sistema procesa una confirmación, el sistema DEBE tomar el bloqueo del espacio y después el de la reserva, y
solo entonces leer *ahora*, el estado y el horario con los que decide y calcula (orden de HU-12 `espacio → reserva`).

## R34
SI llegan a la vez dos confirmaciones de la misma reserva ENTONCES el sistema DEBE responder 200 a una sola y 400 con
el mensaje *ya cancelada* a la otra, con un solo registro `RESERVA_CANCELADA` y como mucho un `REEMBOLSO_SOLICITADO`.

## Registro de la cancelación (STRIDE R, RNF-SEG-12)

## R35
CUANDO el sistema cancela una reserva, el sistema DEBE registrar en `auditoria` la acción `RESERVA_CANCELADA` sobre la
reserva, con el id del usuario de la sesión, la IP, la fecha del reloj y un detalle con quien cancela, el espacio, la
política, el porcentaje y el monto a reembolsar.

## R36
SI una solicitud a cualquiera de las cuatro rutas se responde con 400, 403, 404 o 500 ENTONCES el sistema NO DEBE
registrar `RESERVA_CANCELADA` ni `REEMBOLSO_SOLICITADO`, ni cambiar `reserva` ni `reserva_bloque`.

## Después de cancelar (compatibilidad con HU-12 y HU-14, D9)

## R37
MIENTRAS una reserva está `CANCELADA`, el sistema DEBE responderla con `estado = "CANCELADA"` en
`GET /api/reservas/{id}` y en el grupo `pasadas` de `GET /api/reservas`.

## R38
MIENTRAS una reserva está `CANCELADA`, el sistema NO DEBE incluirla en `GET /api/arrendadores/actual/reservas`.

## Integridad en la base de datos (principio 7)

## R39
SI se intenta guardar en `reserva` `cancelada_por`, `cancelada_en` o `monto_reembolso` con un estado distinto de
`CANCELADA`, con alguno de los tres nulos y otro no, con `cancelada_por` fuera de `USUARIO` y `ARRENDADOR`, o con
`monto_reembolso` negativo o mayor que `monto_total`, ENTONCES la base de datos DEBE rechazarlo.

## Reemplazos y compatibilidad

- **HU-12:** `POST /api/reservas`, `GET /api/reservas/{id}` y `ReservaDTO` **no cambian**. La regla del id bien escrito
  pasa de `ReservaServiceImpl` a `ConversionReservas` sin cambiar su resultado (`design.md` DT-10).
- **HU-14:** las dos rutas de listado, sus DTO y `ReservaDAO` **no cambian** (D9). Una `CANCELADA` sembrada sin los
  datos nuevos sigue siendo válida (R39 solo exige coherencia cuando hay datos de cancelación).
- **HU-13:** `pago`, `PasarelaPago`, el webhook y `ReservasPorCobrar` **no cambian**; solo se **agrega** un método
  derivado a `PagoRepository`.
- **`cuentas`:** solo se **agregan** dos métodos a `NotificadorCorreo` y dos records de contenido; los dobles de prueba
  existentes solo ganan esos dos métodos (`design.md` DT-9).
- **HU-16:** recibe el contrato de `PagoService.reembolsarCancelacion` (`design.md` § 11.1).

## Trazabilidad con la historia

| Origen (`docs/historias/HU-15.md` y fuentes)                                          | Cubierto por |
|---------------------------------------------------------------------------------------|--------------|
| CA1: usuario con 48 h o más → 100 %                                                   | R11, R14, R15, R16 |
| CA1: usuario con menos de 48 h → 0 %                                                  | R12, R14, R15, R16, R23 |
| CA1: arrendador cancela → 100 % al usuario                                            | R13, R15, R16 |
| CA2: desde "Mis reservas", al pulsar cancelar veo el monto antes de confirmar         | R17, R18, R8–R10 (pantalla en design § 12) |
| CA3: la reserva pasa a "cancelada"                                                    | R19, R20, R37, R38 |
| CA3: los bloques se liberan                                                           | R21 |
| CA3: se dispara el reembolso (HU-16)                                                  | R22, R23, R24, R25 |
| CA3: el usuario y el arrendador reciben un correo                                     | R26, R27, R28, R29 |
| (E) Solo el usuario de la reserva o el arrendador del espacio pueden cancelar         | R1, R2, R3, R4, R5, R6, R7 |
| (T) Monto de reembolso calculado en servidor                                          | R7, R30, R31, R32, R33, R34 |
| (R) Registro de quién canceló, cuándo y el monto                                      | R20, R35, R36, R39 |
| Simplificación: política fija, no configurable                                        | R15 |
| RF-D-07 / RF-D-08 / RF-D-09                                                           | R11–R16 / R17, R18 / R19–R29 |
| RNF-SEG-07 (montos solo en el servidor)                                               | R30–R33 |
| RNF-SEG-12 (auditoría de cancelaciones)                                               | R35, R36, R24 |
| RNF-SEG-19 (propiedad antes de cada acción)                                           | R5, R6 |
| RNF-USA-04, RN-14 (correos de cancelación, no desactivables)                          | R26, R27 |
| RN-03 (solo `CONFIRMADA` → `CANCELADA`)                                               | R8, R9, R19 |
| RN-06 (política fija)                                                                 | R11, R12, R13, R15 |
| ★ Strategy: una política por caso, elegida sin `if` por tipo                          | R11–R15 (design § 2) |
| Principio 7 (integridad en la base de datos)                                          | R39 |
