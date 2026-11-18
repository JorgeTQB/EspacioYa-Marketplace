# HU-16 — Requirements: Reembolso automático

> Fuente: `docs/historias/HU-16.md` (CA1, CA2; STRIDE T/I, T, R), `docs/historias/HU-15.md` y `specs/HU-15/design.md`
> § 11.1 (contrato de `PagoService.reembolsarCancelacion`), `docs/historias/HU-13.md` y `specs/HU-13/` (Adapter
> `PasarelaPago`), `docs/requerimientos.md` (RF-E-05, RF-E-06, RNF-SEG-07, RNF-SEG-11, RNF-SEG-12, RNF-ARQ-08, RN-07),
> `docs/architecture.md` ("Pagos: qué deja HU-13", estados del pago, patrones ★ Command y Adapter, panel admin),
> `docs/modelo-datos.md` (`pago`, `reembolso`, "un reembolso por pago y origen") y `progress/explore_HU-16.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API** y de las interfaces internas que usan
> otros módulos; la presentación está en `design.md` § 12 Pantallas y las decisiones técnicas (DT-n) en `design.md` § 1.

## Decisiones por defecto — confirmar en la aprobación

> ⚠ La historia no fija estos puntos. El spec adopta estos valores para que el dueño (**Ariel Enriquez**) los acepte o
> los cambie al aprobar; si alguno cambia, se ajustan los requirements citados antes de pasar a `in_progress`.

| # | Hueco | Decisión | Afecta |
|---|-------|----------|--------|
| D1 | ¿Qué es "la transacción" de CA1 y CA2? | Es el **pago** (`pago.estado`, RN-07): `PAGADO` → `REEMBOLSADO` o `REEMBOLSO_FALLIDO`, y `REEMBOLSO_FALLIDO` → `REEMBOLSADO` al reintentar con éxito. El proceso de reembolso vive en una fila nueva de `reembolso` (`PENDIENTE`, `REEMBOLSADO`, `FALLIDO`), que es la **fuente de verdad** del reembolso; `pago.estado` se actualiza en la **misma transacción** que `reembolso.estado`, de modo que nunca se contradicen. | R10, R11, R17, R18 |
| D2 | ¿Qué es "al confirmarse"? | La respuesta **síncrona** de la API de reembolsos de la pasarela con el reembolso aprobado (Mercado Pago: `POST /v1/payments/{id}/refunds` → `status = approved`). No se espera un webhook de reembolso: la llamada la inicia el servidor con su llave secreta sobre TLS, así que la respuesta es tan confiable como un aviso firmado, y la historia no pide otro canal. Cualquier otra respuesta (error, sin respuesta, `status` distinto de `approved`) cuenta como **fallo** (CA2). | R10–R14, R17 |
| D3 | ¿Cuándo se llama a la pasarela? | Una vez, **después** de que se confirma la transacción de la cancelación (intento automático), en el mismo hilo de la solicitud, sin retener filas durante la llamada. La respuesta de la cancelación (HU-15) no cambia: es 200 aunque el reembolso falle. | R3–R7, R16 |
| D4 | Reintento | Solo el **admin**, con un botón por reembolso en su panel (`POST /api/admin/reembolsos/{id}/reintento`). Se reintenta un reembolso `FALLIDO` o uno `PENDIENTE` **sin respuesta** (D5). No hay reintentos automáticos ni proceso programado. | R29–R35 |
| D5 | Reembolso atascado | Si el proceso cae entre la confirmación de la cancelación y el resultado de la pasarela, el reembolso queda `PENDIENTE`. Se considera **sin respuesta** cuando su último intento (o su creación, si nunca se intentó) tiene **2 minutos o más** (`Reembolso.MARGEN_SIN_RESPUESTA`; los tiempos de espera de la pasarela suman 10 s). El admin lo ve en su panel y lo puede reintentar como uno fallido. Uno `PENDIENTE` más reciente está **en proceso** y no se puede reintentar. | R22, R26, R27, R33 |
| D6 | Idempotencia ante la pasarela | Todos los intentos de un mismo reembolso envían la **misma** clave de idempotencia, `espacioya-reembolso-<id del reembolso>` (Mercado Pago: encabezado `X-Idempotency-Key`). Si un intento llegó a la pasarela pero su respuesta se perdió, el reintento recibe el mismo reembolso en vez de crear otro. | R8, R24 |
| D7 | Pagos de la pasarela simulada (`sim-`, RS-5) | `PasarelaSimulada.reembolsar` **aprueba** el reembolso de un pago cuyo id empieza por `sim-` (ningún dinero se movió; solo deja una línea INFO) y **falla** si el id no empieza por `sim-`. Para demostrar CA2 en UAT y en las pruebas, la propiedad `espacioya.pagos.simulada.fallos-reembolso` (`PAGOS_SIMULADA_FALLOS_REEMBOLSO`, por defecto `0`) hace fallar las primeras *N* llamadas de cada clave de idempotencia: con `1`, el intento automático falla y el reintento del admin funciona, sin reiniciar el backend. Con `mercadopago`, un pago `sim-` se envía igual y la pasarela lo rechaza (queda `FALLIDO`); limpiarlos al conectar Mercado Pago sigue siendo RS-5. | R14, R15, R43 |
| D8 | ¿Qué ve el admin? | Solo los reembolsos **por atender** (`FALLIDO` y `PENDIENTE` sin respuesta), del más antiguo al más reciente, sin paginar (riesgo aceptado: se esperan pocos). Por cada uno: id, reserva, monto, origen, estado, intentos, creado y último intento. **Sin** datos del usuario, id de la transacción de la pasarela, últimos 4 dígitos ni motivo técnico del fallo (STRIDE I, RNF-SEG-16). | R28, R29 |
| D9 | Respuesta del reintento | 200 con el reembolso y su estado **resultante** (`REEMBOLSADO` o `FALLIDO`): un fallo de la pasarela al reintentar no es un error de la solicitud. | R30, R31 |
| D10 | Registro (STRIDE R) | Se conserva `REEMBOLSO_SOLICITADO` de HU-15 (en la transacción de la cancelación) y se agrega **un registro por intento** con su resultado: `REEMBOLSO_REALIZADO` o `REEMBOLSO_FALLIDO`, sobre el reembolso. En el intento automático, quien actúa es quien canceló (`solicitadoPor` e IP de `ReembolsoPorCancelacion`); en el reintento, el admin de la sesión y su IP. | R36–R39 |
| D11 | Correo al usuario (RNF-USA-04) | **Ninguno.** La historia no lo pide en sus CA, la matriz §7 no cita RNF-USA-04 para HU-16 y el usuario ya recibe el correo de cancelación con el monto (HU-15). Si el dueño lo quiere, se agrega a `NotificadorCorreo` coordinando con Jorge Quispe (HU-25). | — |
| D12 | Rate limiting del reintento | Ninguno: RNF-SEG-17 no fija cupo, la ruta exige rol `ADMIN` e IP permitida, y un reembolso en proceso o ya hecho no se vuelve a enviar (R22, R23). | — |
| D13 | Monto parcial | Con HU-15 el monto es siempre el 100 % del pago. Si fuera menor, el pago pasa igual a `REEMBOLSADO` (RN-07 no tiene un estado parcial); HU-26 (reclamos, montos parciales) decide si necesita otro. | R11 |

## Definiciones

- **Reembolso:** fila de `reembolso` con `pago_id`, `monto`, `origen` (`CANCELACION`; `RECLAMO` queda para HU-26),
  `estado` (`PENDIENTE`, `REEMBOLSADO`, `FALLIDO`), `intentos`, `creado_en`, `ultimo_intento_en` e
  `id_reembolso_pasarela` (`design.md` § 9).
- **Pago original:** el pago cuyo id es el `pago_id` del reembolso. **Transacción original:** su
  `id_transaccion_pasarela`.
- **Clave de idempotencia:** `espacioya-reembolso-<id del reembolso>` (D6).
- **Intento:** una ejecución del comando `SolicitudReembolso` que llega a llamar a la pasarela. **Intento automático:**
  el que se ejecuta tras confirmar la cancelación. **Reintento:** el que pide el admin con
  `POST /api/admin/reembolsos/{id}/reintento`.
- **Ahora:** el instante del reloj del sistema (`Clock`).
- **Sin respuesta:** reembolso `PENDIENTE` cuyo `ultimo_intento_en` (o `creado_en`, si es nulo) es anterior o igual a
  *ahora* − 2 minutos (D5). **En proceso:** `PENDIENTE` que no está sin respuesta.
- **Reintentable:** `FALLIDO`, o `PENDIENTE` sin respuesta. **Por atender:** lo mismo, para el listado del admin.
- **Admin con sesión:** cuenta con rol `ADMIN`, sesión vigente y desde una IP de `ADMIN_IPS_PERMITIDAS`.
- **Rutas del admin:** `GET /api/admin/reembolsos` y `POST /api/admin/reembolsos/{id}/reintento`.
- **Id bien escrito:** texto de 1 a 19 dígitos cuyo valor es un `long` mayor que 0 (como HU-13 y HU-15).
- **ReembolsoAdminDTO:** cuerpo con exactamente `id`, `reservaId`, `monto`, `origen`, `estado`, `intentos`, `creadoEn`
  y `ultimoIntentoEn` (`design.md` § 5).
- **Mensajes** (texto exacto en `design.md` § 5): *sesión inválida* (HU-03), *acceso denegado*, *no encontrado*,
  *ya reembolsado* y *en proceso*.

## Solicitud del reembolso al cancelar (CA1, contrato de HU-15)

## R1
CUANDO otro módulo invoca `PagoService.reembolsarCancelacion` para una reserva con pago `PAGADO` y un monto mayor que 0
que no supera el del pago, el sistema DEBE crear, en la transacción del llamador, un reembolso con el `pago_id` de ese
pago, ese monto, `origen = CANCELACION`, `estado = PENDIENTE`, `intentos = 0` y `creado_en` igual a *ahora*.

## R2
CUANDO otro módulo invoca `PagoService.reembolsarCancelacion` con datos válidos, el sistema DEBE seguir registrando en
`auditoria`, en la transacción del llamador, la acción `REEMBOLSO_SOLICITADO` de HU-15 R24 (entidad `pago`, detalle
`origen=CANCELACION;reservaId=…;monto=…`).

## R3
CUANDO otro módulo invoca `PagoService.reembolsarCancelacion`, el sistema NO DEBE llamar a la pasarela antes de que la
transacción del llamador se confirme.

## R4
SI la transacción del llamador de `PagoService.reembolsarCancelacion` se revierte ENTONCES el sistema NO DEBE llamar a
la pasarela ni dejar ningún reembolso guardado.

## R5
CUANDO se confirma la transacción en la que se creó un reembolso por cancelación, el sistema DEBE ejecutar un intento
automático de ese reembolso.

## R6
SI la reserva no tiene pago `PAGADO`, o el monto no es mayor que 0 o supera el del pago, ENTONCES el sistema DEBE lanzar
la misma excepción que en HU-15 (`IllegalStateException` o `IllegalArgumentException`) sin crear ningún reembolso.

## R7
SI el intento automático falla por cualquier motivo ENTONCES el sistema DEBE mantener la cancelación confirmada y
responder a la confirmación de la cancelación con el mismo 200 y el mismo `CancelacionDTO` de HU-15.

## Ejecución contra el pago original (CA1, STRIDE T/I, RNF-SEG-11, ★ Command + Adapter)

## R8
CUANDO el sistema ejecuta un intento, el sistema DEBE pedir a la pasarela, mediante `PasarelaPago.reembolsar`, el
reembolso de la transacción original del reembolso, por el `monto` del reembolso y con su clave de idempotencia.

## R9
El sistema DEBE tomar la transacción original y el monto de cada intento solo de la fila de `reembolso` y de su pago
original, sin usar ningún id de pago, de transacción ni monto enviado por un cliente.

## R10
CUANDO la pasarela confirma el reembolso de un intento, el sistema DEBE dejar el reembolso en `REEMBOLSADO` con el id
del reembolso que devolvió la pasarela en `id_reembolso_pasarela`.

## R11
CUANDO la pasarela confirma el reembolso de un intento, el sistema DEBE dejar el pago original en `REEMBOLSADO`, en la
misma transacción en que el reembolso pasa a `REEMBOLSADO` (D1, RN-07).

## R12
CUANDO `MercadoPagoAdapter.reembolsar` se invoca, el sistema DEBE enviar `POST /v1/payments/{transacción original}/refunds`
con el cuerpo `{"amount": <monto>}`, la llave secreta de la pasarela y el encabezado `X-Idempotency-Key` con la clave de
idempotencia, y devolver el id del reembolso solo si la respuesta trae un `id` y `status = "approved"`.

## R13
SI Mercado Pago responde a un reembolso con un código de error, no responde dentro de los tiempos de espera del
adaptador, o responde sin `id` o con un `status` distinto de `approved` ENTONCES `MercadoPagoAdapter.reembolsar` DEBE
lanzar `ServicioExternoException` sin escribir el cuerpo de la respuesta del proveedor en ningún registro.

## R14
CUANDO `PasarelaSimulada.reembolsar` recibe una transacción original que empieza por `sim-` y no le toca fallar (R15),
el sistema DEBE aprobar el reembolso y devolver un id que empieza por `sim-reembolso-` (D7).

## R15
DONDE `espacioya.pagos.simulada.fallos-reembolso` vale *N* mayor que 0, `PasarelaSimulada.reembolsar` DEBE lanzar
`ServicioExternoException` exactamente en las primeras *N* llamadas de cada clave de idempotencia (las siguientes siguen
R14) (D7).

## R43
SI `PasarelaSimulada.reembolsar` recibe una transacción original que no empieza por `sim-` ENTONCES el sistema DEBE
lanzar `ServicioExternoException`, sea cual sea `fallos-reembolso` (D7: la simulada nunca emitió ese pago).

## R16
MIENTRAS el sistema espera la respuesta de la pasarela en un intento, el sistema NO DEBE retener bloqueos sobre las filas
del reembolso, del pago original ni de la reserva (regla de HU-13: ninguna llamada externa retiene filas).

## Fallo de la pasarela (CA2, RF-E-06)

## R17
SI la pasarela lanza una excepción o no confirma el reembolso en un intento ENTONCES el sistema DEBE dejar el reembolso
en `FALLIDO` y el pago original en `REEMBOLSO_FALLIDO`, en una misma transacción.

## R18
CUANDO el sistema inicia un intento, el sistema DEBE sumar 1 a `intentos` y guardar `ultimo_intento_en = ahora` en una
transacción confirmada antes de llamar a la pasarela.

## Idempotencia (STRIDE T, RNF-SEG-11)

## R19
SI se intenta guardar un segundo reembolso con el mismo `pago_id` y el mismo `origen` ENTONCES la base de datos DEBE
rechazarlo (`uq_reembolso_pago_origen`).

## R20
SI `PagoService.reembolsarCancelacion` se invoca otra vez para una reserva que ya tiene un reembolso por cancelación
ENTONCES el sistema DEBE lanzar una excepción sin crear un segundo reembolso y sin llamar a la pasarela.

## R21
MIENTRAS un reembolso está `REEMBOLSADO`, el sistema NO DEBE iniciar ningún intento suyo.

## R22
MIENTRAS un reembolso está en proceso, el sistema NO DEBE iniciar otro intento suyo.

## R23
SI llegan a la vez dos reintentos del mismo reembolso reintentable ENTONCES el sistema DEBE llamar a la pasarela una sola
vez, responder 200 a uno y 400 con el mensaje *en proceso* al otro.

## R24
El sistema DEBE enviar la misma clave de idempotencia en todos los intentos de un mismo reembolso (D6).

## Panel del admin (CA2, RF-E-06, D4, D8, D9)

## R25
SI un cliente sin sesión vigente envía una solicitud a cualquiera de las rutas del admin ENTONCES el sistema DEBE
responder 401 con el mensaje *sesión inválida*.

## R26
SI una cuenta con rol distinto de `ADMIN` envía una solicitud a cualquiera de las rutas del admin ENTONCES el sistema
DEBE responder 403 con el mensaje *acceso denegado* sin leer ni modificar ningún reembolso.

## R27
SI una solicitud a cualquiera de las rutas del admin llega desde una IP que no está en `ADMIN_IPS_PERMITIDAS`
ENTONCES el sistema DEBE responder 403 con el mensaje *acceso denegado* sin leer ni modificar ningún reembolso.

## R28
CUANDO un admin con sesión envía `GET /api/admin/reembolsos`, el sistema DEBE responder 200 con
`Cache-Control: no-store` y una lista de ReembolsoAdminDTO con exactamente los reembolsos por atender, ordenados por
`creado_en` y luego por `id`, ascendentes.

## R29
CUANDO el sistema responde `GET /api/admin/reembolsos`, el sistema NO DEBE incluir los reembolsos `REEMBOLSADO` ni los
`PENDIENTE` en proceso, ni escribir en `reembolso`, `pago` ni `auditoria`.

## R30
CUANDO un admin con sesión envía `POST /api/admin/reembolsos/{id}/reintento` para un reembolso reintentable, el sistema
DEBE ejecutar un intento y responder 200 con un ReembolsoAdminDTO con el estado resultante (`REEMBOLSADO` si la
pasarela lo confirmó).

## R31
SI la pasarela vuelve a fallar en un reintento ENTONCES el sistema DEBE responder 200 con un ReembolsoAdminDTO en
`FALLIDO` y con `intentos` aumentado en 1.

## R32
SI el `{id}` del reintento no es un id bien escrito o no corresponde a ningún reembolso ENTONCES el sistema DEBE
responder 404 con el mensaje *no encontrado*.

## R33
SI el reembolso del reintento está `REEMBOLSADO` ENTONCES el sistema DEBE responder 400 con el mensaje
*ya reembolsado* sin llamar a la pasarela.

## R34
SI el reembolso del reintento está en proceso ENTONCES el sistema DEBE responder 400 con el mensaje *en proceso* sin
llamar a la pasarela.

## R35
CUANDO el sistema procesa un reintento, el sistema DEBE tomar la identidad del admin de la sesión y su IP de la conexión,
e ignorar cualquier cuerpo de la solicitud, como `pagoId`, `monto` o `idTransaccion`.

## Registro de cada intento (STRIDE R, RNF-SEG-12, D10)

## R36
CUANDO la pasarela confirma el reembolso de un intento, el sistema DEBE registrar en `auditoria`, en la misma
transacción en que el reembolso pasa a `REEMBOLSADO`, la acción `REEMBOLSO_REALIZADO` sobre la entidad `reembolso`, con
la fecha del reloj y un detalle con el pago, la reserva, el número de intento, el disparo (`AUTOMATICO` o `ADMIN`), el
monto y el id del reembolso de la pasarela.

## R37
SI un intento falla ENTONCES el sistema DEBE registrar en `auditoria`, en la misma transacción en que el reembolso pasa
a `FALLIDO`, la acción `REEMBOLSO_FALLIDO` sobre la entidad `reembolso`, con la fecha del reloj y un detalle con el pago,
la reserva, el número de intento, el disparo y el monto, sin ningún dato de la respuesta del proveedor.

## R38
CUANDO el sistema registra el resultado de un intento, el sistema DEBE usar como `usuario_id` e `ip` los de quien
canceló (`ReembolsoPorCancelacion.solicitadoPor` e `ip`) en el intento automático, y los del admin de la sesión en el
reintento.

## R39
SI un reintento se responde con 400, 401, 403 o 404 ENTONCES el sistema NO DEBE registrar ninguna acción en `auditoria`
ni cambiar `reembolso` ni `pago`.

## Patrones (★ Command, Adapter; RNF-ARQ-08)

## R40
El sistema DEBE ejecutar el intento automático y el reintento con el mismo comando `SolicitudReembolso.ejecutar()`, sin
otro camino que llame a `PasarelaPago.reembolsar` (★ Command).

## R41
El sistema DEBE pedir los reembolsos a la pasarela solo a través de la interfaz `PasarelaPago`, sin que ninguna clase
fuera de `MercadoPagoAdapter` use tipos, rutas o clientes HTTP del proveedor (★ Adapter, RNF-ARQ-08).

## Integridad en la base de datos (principio 7)

## R42
SI se intenta guardar en `reembolso` un `origen` fuera de `CANCELACION` y `RECLAMO`, un `estado` fuera de `PENDIENTE`,
`REEMBOLSADO` y `FALLIDO`, un `monto` menor o igual que 0, `intentos` negativo, un `pago_id` que no existe en `pago`, un
`REEMBOLSADO` sin `id_reembolso_pasarela` o un `id_reembolso_pasarela` en otro estado, o un `REEMBOLSADO` o `FALLIDO`
sin intentos ni `ultimo_intento_en` ENTONCES la base de datos DEBE rechazarlo.

## Reemplazos y compatibilidad

- **HU-15 R24** decía "sin llamar a la pasarela ni cambiar el estado del pago": HU-16 lo **reemplaza** en esa parte
  (D7 de HU-15 lo preveía). `REEMBOLSO_SOLICITADO` se mantiene igual (R2). En `CancelacionReservaControllerIT` el caso
  `solicitaElReembolso` pasa a esperar el pago `REEMBOLSADO` y un reembolso; su `@DisplayName` cita también HU-16 R1 y R11.
- **HU-15** (resto): las cuatro rutas, `CancelacionDTO`, R22, R23, R25 (500 y rollback sin pago `PAGADO`), R34 (una sola
  cancelación) y los correos **no cambian**. La firma de `PagoService.reembolsarCancelacion` y su `MANDATORY` tampoco.
- **HU-13:** `PasarelaPago` solo **gana** `reembolsar`; `crearCheckout`, `cerrarCheckout`, `idNotificado`,
  `consultarPago`, el webhook y la pantalla simulada no cambian. `GET /api/reservas/{id}/pago` de una reserva cancelada
  puede responder `ultimoResultado = "REEMBOLSADO"` o `"REEMBOLSO_FALLIDO"`: la pantalla de HU-13 ya trata cualquier
  valor distinto de `RECHAZADO`, `PAGADO_TARDIO` y `MONTO_DISTINTO` como neutro, y una cancelada no ofrece "Pagar".
- **Pruebas existentes:** solo cambian (a) el caso de HU-15 citado arriba, (b) `PagoServiceImplTest` (constructor nuevo y
  sincronización de transacción inicializada; sus casos de HU-15 siguen) y (c) las limpiezas que borran `pago`, que ganan
  `DELETE FROM reembolso` antes (`reembolso` no tiene cascada desde `pago`). `StubMercadoPago` gana la ruta de reembolsos.
- **HU-26** reutiliza `reembolso` (`origen = RECLAMO`) y el comando `SolicitudReembolso`; **HU-28** cuenta los pagos
  `REEMBOLSADO`.

## Trazabilidad con la historia

| Origen (`docs/historias/HU-16.md` y fuentes)                                           | Cubierto por |
|----------------------------------------------------------------------------------------|--------------|
| CA1: cuando una cancelación genera reembolso, se procesa                               | R1, R2, R3, R4, R5, R6, R7 |
| CA1: se solicita a la pasarela contra el pago original                                 | R8, R9, R12, R14, R40, R41 |
| CA1: la transacción pasa a "reembolsada" al confirmarse                                | R10, R11, R12, R14 |
| CA2: si la pasarela falla, la transacción queda en "reembolso fallido"                 | R13, R15, R17, R18, R43 |
| CA2: el admin la ve en su panel                                                        | R25, R26, R27, R28, R29 |
| CA2: puede reintentarla con un botón                                                   | R30, R31, R32, R33, R34, R35 (pantalla en design § 12) |
| (T/I) Reembolso solo contra el pago original, vía API de la pasarela                   | R8, R9, R12, R35, R41, R42 |
| (T) Idempotencia: una cancelación no genera dos reembolsos                             | R19, R20, R21, R22, R23, R24 |
| (R) Registro de cada intento y resultado                                               | R2, R18, R36, R37, R38, R39 |
| RF-E-05 / RF-E-06                                                                       | R1–R16 / R17, R25–R35 |
| RNF-SEG-07 (montos solo en el servidor)                                                 | R9, R35 |
| RNF-SEG-11 (reembolsos idempotentes y solo contra el pago original)                     | R8, R9, R19–R24 |
| RNF-SEG-12 (auditoría de reembolsos)                                                    | R2, R36–R39 |
| RNF-ARQ-08 (pasarela detrás de interfaz propia, ★ Adapter)                              | R12–R15, R41, R43 |
| RN-07 (pagada → reembolsada, o reembolso fallido que el admin puede reintentar)         | R11, R17, R30, R31 |
| ★ Command: `SolicitudReembolso.ejecutar()` idempotente y reintentable                   | R21–R24, R40 (design § 2) |
| Regla de HU-13: ninguna llamada externa retiene filas                                   | R3, R16, R18 |
| Principio 7 (integridad en la base de datos)                                            | R19, R42 |

## Adenda 2026-11-16 — Escenario de calidad AC005

> Pedida por el orquestador con la historia en `done`, para medir un escenario del informe (capítulo 6.3) con la cantidad
> de intentos que promete (`PROMPT-escenarios-calidad.md` § 1 y § 2). **Solo agrega una prueba de integración**: no hay
> `R<n>` nuevos ni cambios de producción. La prueba nueva verifica R22 y R23, que ya existen, a mayor escala. Diseño en
> `design.md` § 15 y tasks T25–T26 en `tasks.md`. **Su implementación espera la aprobación del dueño (Ariel Enriquez).**
> Nada se renumera.

### Escenario que se mide

| AC | Atributo | Medida que promete el escenario | Requisito existente | Prueba hoy | Prueba nueva |
|----|----------|---------------------------------|---------------------|------------|--------------|
| AC005 | Confiabilidad – Consistencia | Como máximo 1 reembolso por pago y origen después de **10 reintentos** | R22, R23 (con R19, R21, R30, R33, R34, R36, R39) | `ReembolsoConcurrenciaIT.dosReintentosALaVez`: 2 reintentos, 5 rondas | `ReembolsoConcurrenciaIT.diezReintentosALaVez` |

### AC005 — 10 reintentos a la vez del mismo reembolso FALLIDO (R22, R23)

- **Solicitudes simultáneas:** 10 `POST /api/admin/reembolsos/{id}/reintento` del admin con sesión, a la vez, sobre el
  **mismo** reembolso en estado `FALLIDO` (`intentos = 1`), como `dosReintentosALaVez`.
- **Rondas:** `RONDAS` (5), cada una con un reembolso `FALLIDO` sembrado de nuevo.
- **Orden forzado, como en la prueba existente:** quien llega a la pasarela espera a que al menos otra solicitud haya
  respondido. Por eso la primera respuesta es siempre un rechazo *en proceso*; las que llegan después de que el ganador
  termina pueden ver el reembolso ya `REEMBOLSADO`.
- **Aserciones por ronda:**
  1. La pasarela recibe **una sola** solicitud de reembolso en la ronda (`verify(pasarela, times(1)).reembolsar(any())`)
     (R23).
  2. Exactamente **1** respuesta 200 (R30) y **9** respuestas 400 (R23).
  3. Cada 400 trae uno de los dos rechazos válidos y ningún otro: *en proceso*
     (`ReembolsoAdminService.MENSAJE_EN_PROCESO`, R22, R34) o *ya reembolsado*
     (`ReembolsoAdminService.MENSAJE_YA_REEMBOLSADO`, R21, R33), según el orden. Al menos uno es *en proceso*.
  4. Queda exactamente **1** fila en `reembolso` para ese `pago_id` y ese `origen` (R19).
  5. `intentos` del reembolso sube **en 1** (de 1 a 2) y su estado final es `REEMBOLSADO`.
  6. Se registra **un solo** `REEMBOLSO_REALIZADO` por ronda (R36); los 400 no registran nada (R39).

### Reglas de la adenda

- **Límites del entorno de prueba.** Si la cantidad choca con un límite (pool de conexiones de las pruebas, el filtro de
  IPs del admin, un límite por IP), **no se reduce la cantidad ni se toca producción**: solo se ajusta la configuración de
  prueba con un mecanismo que ya exista y se documenta el ajuste y su motivo en `progress/impl_HU-16.md` y en la evidencia
  (`progress/calidad_escenarios.md`). Si no hay forma limpia, se para y se pregunta al dueño. Hoy ninguna `ReglaLimite`
  aplica a las rutas del admin.
- **Defectos.** Si la prueba falla por un defecto real (de forma consistente o intermitente), **no se modifica
  `2_Backend/src/main`** ni se relaja la aserción: se registra como `DEF-xx`, siguiendo la numeración que ya existe en
  `progress/`, con historia, esperado, obtenido, severidad y cómo reproducirlo, y se avisa al dueño.
- Las pruebas existentes no se modifican ni se borran.

### Trazabilidad de la adenda

| Escenario | Requisito existente | Prueba |
|-----------|---------------------|--------|
| AC005: como máximo 1 reembolso por pago y origen después de 10 reintentos | R22, R23; R19, R21, R30, R33, R34, R36, R39 | `ReembolsoConcurrenciaIT.diezReintentosALaVez` |
