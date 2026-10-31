# HU-13 — Requirements: Pago con tarjeta

> Fuente: `docs/historias/HU-13.md` (CA1–CA4, STRIDE I/T, S/T, T, D, R), `docs/requerimientos.md` (RF-E-01…04,
> RNF-SEG-07, 11, 12, 16, 17, RNF-CUM-01, 02, RNF-ARQ-08, RN-03, RN-08, §8), `docs/architecture.md` (puerto
> `pagos.ReservasPorCobrar`, "Reservas: qué deja HU-12", patrones Adapter y Facade), `docs/modelo-datos.md` (`pago`),
> `specs/HU-12/`, `specs/HU-08/` (DT-10), `specs/HU-09/` (D1), `progress/explore_HU-13.md` y `progress/HU-13.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API** y de lo que el backend envía a la
> pasarela; la presentación está en `design.md` § 10 Pantallas y las decisiones técnicas en `design.md` § 1.
>
> **Adenda 2026-10-30:** D7 revisada, D8, D9 y R59–R87, antes de la tabla de trazabilidad. Nada se renumera. Lo que la
> adenda altera está marcado en su lugar y resumido en "Qué queda alterado".

## Decisiones del dueño (Eduardo Saldaña, 2026-10-30)

No llevan ⚠.

| # | Decisión | Afecta |
|---|----------|--------|
| D1 | Pasarela: **Mercado Pago Checkout Pro en modo de prueba, en soles**. Stripe se descartó porque Perú no aparece en su registro; Culqi porque no documenta firma de webhook y no es alojado; Izipay porque es embebido y exige trámites de comercio. *Adenda: sigue siendo el proveedor elegido, pero no se conecta por ahora (D8).* | R1–R5, R23–R28, R55 |
| D2 | Un pago aprobado que llega con la reserva no vigente se registra con estado propio (`PAGADO_TARDIO`) y se audita, **sin confirmar la reserva**. Devolución manual hasta que HU-16 la automatice, si el PO cambia ese alcance. RN-08 y `docs/architecture.md` se corrigen como task del implementer. | R34, R37, R52 |
| D3 | El enlace vence **1 minuto antes** que la reserva (`expiration_date_to`, margen configurable) y en ese último minuto no se inicia un pago. | R4, R11 |
| D4 | Al tercer rechazo la reserva sigue hasta expirar, iniciar otro pago responde **429** y la pantalla indica reservar de nuevo cuando expire. | R13, R19, R40 |
| D5 | Comprobante con espacio, fecha, horario, monto, últimos 4 y referencia. Correo al arrendador con espacio, fecha y horario, sin datos del usuario ni monto. Correo simulado hasta HU-25. | R48, R49 |
| D6 | Con el espacio pausado u oculto o el arrendador suspendido no se inicia el pago; con el pago ya aprobado y la reserva vigente, se confirma igual. | R12, R35 |
| D7 | ~~La pasarela simulada solo existe en pruebas y desarrollo; UAT usa Mercado Pago.~~ **Revisada el 2026-10-30:** UAT usa la simulada hasta conectar Mercado Pago, y el informe lo declara (ver Adenda). | R55, R56 |

## Hechos comprobados por el dueño con un pago de prueba real (2026-10-30)

Mandan sobre la documentación del proveedor y sobre las dudas del explorador.

| # | Hecho | Afecta |
|---|-------|--------|
| H1 | Un pago de prueba **sí** envía notificaciones a la `notification_url` de la preferencia, si es una URL HTTPS pública. | R5, R22 |
| H2 | Un solo pago genera varias notificaciones en formatos distintos: `type=payment`, `topic=payment` y `topic=merchant_order`; también se vio `action: payment.updated`. Se procesa solo `type=payment`. | R23, R42 |
| H3 | La notificación `type=payment` trae `x-signature` y `x-request-id`. No se comprobó en los otros formatos. | R23, R24 |
| H4 | Ninguna notificación trae estado ni monto: hay que consultar `GET /v1/payments/{id}`, que devolvió `status`, `currency_id`, `transaction_amount` y `last_four_digits` con credenciales de prueba. | R25, R26 |
| H5 | `live_mode` llegó en `true` con un vendedor de prueba: no se usa para distinguir el ambiente. | R26 |
| H6 | Pagar exige vendedor y comprador de prueba; el pago se hizo con `init_point`. | R1 |

## Supuestos a confirmar por el dueño al aprobar

> ⚠ Las fuentes no fijan estos puntos. Si **Eduardo Saldaña** no los acepta, cambian los requirements citados antes de
> pasar a `in_progress`.

| # | Supuesto | Afecta |
|---|----------|--------|
| S1 | **La firma aún no se comprobó contra una notificación real.** H3 vio los encabezados, pero nadie recalculó el HMAC. El algoritmo se tomó del `WebhookSignatureValidator` oficial del SDK 3.7.0 y el secreto es el de *Tus integraciones > Webhooks > Configurar notificación*. Si con una `notification_url` por preferencia ese secreto no firma, la historia se bloquea: la task T23 lo prueba con el túnel o UAT antes de cerrar. **Adenda (D8): S1 sigue SIN COMPROBAR.** T23 no se ejecutó y deja de condicionar el cierre: la prueba se hará al conectar Mercado Pago, y un fallo entonces reabre la historia. | R24 |
| S2 | **`data.id` frente a `data_id`.** La documentación de Mercado Pago y su SDK de Java leen `data.id`; el dueño observó `data_id` en webhook.site. Probablemente webhook.site cambia el punto por guion bajo (inferencia, no comprobada). Se aceptan los dos, con prioridad para `data.id`, y su valor entra igual a la firma. | R24, definición *id notificado* |
| S3 | Un pago aprobado con monto o moneda distintos a los de la reserva se guarda con un estado propio, **`MONTO_DISTINTO`**, que D2 no menciona. No confirma nada y no usa `PAGADO_TARDIO`, porque no es un problema de tiempo y hay que revisarlo a mano. | R33 |
| S4 | Un segundo pago aprobado sobre una reserva ya `CONFIRMADA` cae en D2: la reserva "no está vigente", así que es `PAGADO_TARDIO` y su devolución es manual. | R34 |
| S5 | El checkout ofrece **solo tarjeta y una cuota**, con modo binario, porque billetera, transferencias y cuotas están fuera del alcance. La lista final de tipos excluidos se confirma con `GET /v1/payment_methods` (T23). Si un pago aprobado llega sin tarjeta, igual confirma y `ultimos4` queda nulo. | R5, R30, R48 |
| S6 | Al tercer rechazo se intenta cerrar el checkout (`PUT /checkout/preferences/{id}`, no probado). Es de mejor esfuerzo: si falla, o si el usuario ya está en un cuarto intento en la página de la pasarela, ese intento puede pasar. **Riesgo aceptado:** el corte real es el 429 y la plataforma se entera por notificación, después. | R40, R41 |
| S7 | **Riesgo aceptado:** el límite es por reserva, como pide la historia. Con 3 pendientes (HU-12 S7) son hasta 9 rechazos cada 15 minutos por usuario, renovables. No se agrega un límite por persona. | R13 |
| S8 | No se aplica tolerancia al `ts` de la firma: no consta si un reintento cambia el `ts`, y una tolerancia podría perder una confirmación. Repetir una notificación es inofensivo, porque el estado se consulta y es idempotente. | R24, R42 |
| S9 | Una firma inválida responde **401 sin cuerpo**, como indica la documentación de Mercado Pago, y va solo a `log.warn`, sin auditoría: la ruta es pública y una inundación llenaría la tabla. | R24 |
| S10 | Si la consulta del pago falla, incluido un 404, se responde 503 para que la pasarela reintente: perder un aprobado es peor. Consecuencia: "Simular notificación" del panel, con un id falso, se verá como fallida. | R27 |
| S11 | **Sin `PAGOS_PASARELA` el backend no arranca** (no hay valor por defecto, para que ningún entorno elija pasarela por omisión). Tras el merge, **cada integrante** debe definir `PAGOS_PASARELA=simulada` en local: se avisa al equipo y se documenta en el README. **Adenda (D7 revisada):** UAT también define `simulada`, de forma explícita, hasta conectar Mercado Pago; ya no es un error que la simulada llegue a UAT. | R56, R57 |
| S12 | `back_urls` y `auto_return` solo se envían si `FRONTEND_URL` es HTTPS: Mercado Pago no admite `localhost`. En local con la pasarela real, sin túnel, el comprador no vuelve solo. | R5 |
| S13 | Se crea la tabla **`checkout_pago`** (un checkout por reserva), que no está en `docs/modelo-datos.md`, para reutilizar el enlace y evitar dos cobros abiertos. `pago` gana los estados `PAGADO_TARDIO` y `MONTO_DISTINTO`. | R6, R7, R47 |
| S14 | La corrección de RN-08 y los estados nuevos los debe conocer **Ariel Enriquez (PO)**, igual que el retiro de Factory Method, pendiente de confirmar con él. HU-19 y HU-28 deben contar solo `PAGADO`. | R33, R34 |

## Definiciones

- **Usuario con sesión, ahora, reserva vigente, reserva vencida:** como en HU-12.
- **Reserva propia:** reserva cuyo `usuario_id` es el del usuario de la sesión.
- **Margen de cierre:** `espacioya.pagos.margen-cierre`, por defecto 1 minuto (D3).
- **Último tramo:** reserva vigente con *ahora* igual o posterior a `expira_en` menos el margen de cierre.
- **Espacio visible:** `CatalogoEspaciosService.esVisible(espacioId)` es `true` (`PUBLICADO` y arrendador `ACTIVA`).
- **Rechazos recientes:** filas de `pago` de la reserva con `estado = RECHAZADO` y `creado_en` en los últimos 30 minutos.
- **Pago disponible:** reserva propia, vigente, fuera del último tramo, con espacio visible y menos de 3 rechazos
  recientes.
- **Notificación de pago:** `POST /api/pagos/webhook` con el parámetro de URL `type=payment`.
- **Id notificado:** valor del parámetro `data.id` o, si falta, de `data_id` (⚠ S2).
- **Firma válida:** `x-signature` con la forma `ts=<ts>,v1=<hex>`, donde `v1` es el HMAC-SHA256 en hexadecimal de
  `id:<id notificado>;request-id:<x-request-id>;ts:<ts>;` con el secreto `espacioya.pagos.secreto-webhook`.
- **Pago consultado:** respuesta de `GET /v1/payments/{id notificado}`. **Aprobado:** `status = approved`.
  **Rechazado:** `status = rejected`. Cualquier otro valor es **sin resultado final**.
- **Reserva del pago:** reserva cuyo id coincide con `external_reference` del pago consultado.
- **Pago coincidente:** `currency_id = PEN` y `transaction_amount` numéricamente igual a `monto_total` de la reserva.
- **Id de transacción:** `id` del pago consultado, como texto. Cada intento en la pasarela es un pago con id propio.
- **CheckoutDTO:** cuerpo con `urlPago`. **EstadoPagoDTO:** cuerpo con `ultimoResultado`, `intentosAgotados` y
  `pagoDisponible` (`design.md` § 4).
- **Mensajes** (texto exacto en `design.md` § 4): *pago no disponible*, *pasarela no disponible*, *demasiados intentos*,
  *sesión inválida*, *acceso denegado* y *recurso no encontrado*.

## Iniciar el pago (CA1, RF-E-01, RN-08, STRIDE I/T y T)

## R1
CUANDO un usuario con sesión envía `POST /api/reservas/{id}/pago` de una reserva con pago disponible, el sistema DEBE
responder 200 con un CheckoutDTO cuyo `urlPago` es el `init_point` del checkout de la pasarela.

> *Adenda:* con la pasarela simulada, `urlPago` es la URL de R59. `init_point` solo aplica a `mercadopago`.

## R2
CUANDO el sistema crea un checkout, el sistema DEBE enviar a la pasarela como `unit_price` el `monto_total` de la
reserva, `currency_id = PEN` y como `external_reference` el id de la reserva.

## R3
CUANDO el sistema procesa `POST /api/reservas/{id}/pago`, el sistema DEBE ignorar cualquier cuerpo enviado por el
cliente.

## R4
CUANDO el sistema crea un checkout, el sistema DEBE enviar `expires = true` y `expiration_date_to` igual a `expira_en`
de la reserva menos el margen de cierre, en formato ISO 8601 con desfase de Lima (D3).

## R5
CUANDO el sistema crea un checkout, el sistema DEBE enviar `notification_url`, `binary_mode = true`, una sola cuota y
los tipos de pago que no son tarjeta como excluidos (⚠ S5, S12).

## R6
MIENTRAS la reserva ya tiene una fila en `checkout_pago`, el sistema DEBE responder a R1 con la URL de esa fila, sin crear
otro checkout (⚠ S13).

## R7
CUANDO llegan a la vez dos `POST /api/reservas/{id}/pago` de una reserva sin checkout, el sistema DEBE dejar una sola
fila en `checkout_pago` y responder la URL de esa fila a las dos solicitudes.

## R8
CUANDO el sistema procesa `POST /api/reservas/{id}/pago`, el sistema NO DEBE cambiar la reserva ni crear filas en
`pago`.

## R9
CUANDO el sistema responde según R1, el sistema DEBE registrar en la auditoría la acción `CHECKOUT_INICIADO` con el id
del usuario de la sesión, la entidad `reserva` con su id, la IP del cliente y el detalle `monto=<monto_total>`.

## Cuándo no se inicia el pago (CA4, RF-E-04, D3, D4, D6, STRIDE D)

## R10
SI la reserva propia no es vigente ENTONCES el sistema DEBE responder a `POST /api/reservas/{id}/pago` 400 sin campo con
el mensaje *pago no disponible*, sin llamar a la pasarela.

## R11
MIENTRAS la reserva propia está en el último tramo, el sistema DEBE responder a `POST /api/reservas/{id}/pago` 400 sin
campo con el mensaje *pago no disponible*, sin llamar a la pasarela (D3).

## R12
SI el espacio de la reserva propia no es visible ENTONCES el sistema DEBE responder a `POST /api/reservas/{id}/pago` 400
sin campo con el mensaje *pago no disponible*, sin llamar a la pasarela (D6).

## R13
SI la reserva propia tiene 3 o más rechazos recientes ENTONCES el sistema DEBE responder a
`POST /api/reservas/{id}/pago` 429 con el mensaje *demasiados intentos*, sin llamar a la pasarela ni cambiar la reserva
(D4, ⚠ S7).

## R14
SI una cuenta con rol distinto de `USUARIO` envía `POST /api/reservas/{id}/pago` ENTONCES el sistema DEBE responder 403
con el mensaje *acceso denegado*, sin llamar a la pasarela.

## R15
SI `{id}` de `POST` o `GET /api/reservas/{id}/pago` está mal escrito, no existe o es de una reserva de otro usuario
ENTONCES el sistema DEBE responder 404 con el mensaje *recurso no encontrado*.

## R16
SI un cliente sin sesión vigente envía `POST` o `GET /api/reservas/{id}/pago` ENTONCES el sistema DEBE responder 401 con
el mensaje *sesión inválida*.

## R17
SI la pasarela no crea el checkout ENTONCES el sistema DEBE responder 503 con el mensaje *pasarela no disponible*, sin
guardar filas en `checkout_pago`.

## R18
CUANDO un cliente con sesión vigente envía `POST /api/reservas/{id}/pago`, el sistema DEBE aplicar las comprobaciones en
este orden y responder con la primera que falle: rol (R14), reserva (R15), vigencia (R10), último tramo (R11), espacio
(R12) y rechazos (R13).

## Estado del pago para la pantalla (CA3, CA4, D4)

## R19
CUANDO el usuario de la sesión envía `GET /api/reservas/{id}/pago` de una reserva propia, el sistema DEBE responder 200
con un EstadoPagoDTO con `ultimoResultado` igual al `estado` de su fila de `pago` más reciente o `null`,
`intentosAgotados = true` si tiene 3 o más rechazos recientes y `pagoDisponible = true` solo si tiene pago disponible.

## R20
El sistema NO DEBE incluir en el EstadoPagoDTO ni en el CheckoutDTO el id de transacción, los últimos 4 dígitos ni el
id del checkout.

## R21
CUANDO el sistema procesa `GET /api/reservas/{id}/pago`, el sistema NO DEBE escribir en la base de datos.

## Notificación de la pasarela (STRIDE S/T, RNF-SEG-11, RNF-CUM-02)

## R22
El sistema DEBE aceptar `POST /api/pagos/webhook` sin sesión.

## R23
CUANDO llega `POST /api/pagos/webhook` sin el parámetro `type=payment`, el sistema DEBE responder 200 sin verificar la
firma, sin consultar la pasarela y sin escribir en la base de datos (H2, H3).

## R24
SI una notificación de pago no trae id notificado o no trae firma válida ENTONCES el sistema DEBE responder 401 sin
cuerpo, sin consultar la pasarela y sin escribir en la base de datos (⚠ S1, S2, S8, S9).

> *Adenda:* con `mercadopago` no cambia, y la firma real sigue sin comprobar (S1). Con la pasarela simulada, firma válida
> es `x-signature` igual al secreto configurado o al token interno de R85; con el secreto vacío, ver R83.

## R25
CUANDO llega una notificación de pago con firma válida, el sistema DEBE consultar el pago en la pasarela antes de leer
o cambiar la reserva (H4).

## R26
El sistema DEBE tomar el estado, el monto, la moneda, los últimos 4 dígitos y la referencia solo del pago consultado, sin
leerlos del cuerpo de la notificación ni usar `live_mode` (H4, H5).

## R27
SI la consulta del pago falla ENTONCES el sistema DEBE responder 503 a la notificación sin escribir en la base de datos
(⚠ S10).

## R28
SI el pago consultado es sin resultado final ENTONCES el sistema DEBE responder 200 sin escribir en la base de datos.

## R29
SI el pago consultado no tiene reserva del pago ENTONCES el sistema DEBE responder 200, sin escribir en `pago`, y
registrar en la auditoría la acción `PAGO_SIN_RESERVA` con el detalle `origen=WEBHOOK;transaccion=<id de transacción>`.

## Pago aprobado (CA1, RF-E-01, RN-03, RNF-SEG-07, D2, D6)

## R30
CUANDO el pago consultado es aprobado, es coincidente y su reserva es vigente, el sistema DEBE guardar una fila de
`pago` con `estado = PAGADO`, la reserva, el id de transacción, `monto` igual a `monto_total`, `ultimos4` del pago
consultado o nulo y `creado_en` igual a *ahora*, y responder 200 (⚠ S5).

## R31
CUANDO el sistema guarda una fila según R30, el sistema DEBE cambiar la reserva a `CONFIRMADA` en la misma transacción.

## R32
CUANDO una reserva pasa a `CONFIRMADA`, el sistema DEBE mantener `activo = true` en sus filas de `reserva_bloque`, de
modo que `ReservasDelEspacio.bloquesReservados` las devuelva con `pagado = true`.

## R33
SI el pago consultado es aprobado y no es coincidente ENTONCES el sistema DEBE guardar una fila de `pago` con
`estado = MONTO_DISTINTO` y el monto consultado, sin cambiar la reserva, y responder 200 (⚠ S3).

## R34
SI el pago consultado es aprobado y coincidente y su reserva no es vigente ENTONCES el sistema DEBE guardar una fila de
`pago` con `estado = PAGADO_TARDIO`, sin cambiar la reserva ni sus bloques, y responder 200 (D2, ⚠ S4).

## R35
MIENTRAS la reserva sea vigente, el sistema DEBE aplicar R30 y R31 aunque el espacio ya no sea visible o el usuario o el
arrendador estén suspendidos (D6).

## R36
CUANDO el sistema procesa un pago aprobado o rechazado, el sistema DEBE tomar el bloqueo de `espacio`, después el de
`reserva`, y evaluar la vigencia con un *ahora* tomado después de los dos bloqueos (HU-08 DT-10, orden de HU-12).

## R37
CUANDO una notificación de pago aprobado y el proceso de expiración compiten por una reserva, el sistema DEBE dejar uno
de estos dos resultados: reserva `CONFIRMADA` con `PAGADO`, o reserva `EXPIRADA` con `PAGADO_TARDIO`.

## R38
SI `DisponibilidadService.bloquearEspacio`, `ReservasPorCobrar.bloquear` o `ReservasPorCobrar.confirmar` se invoca sin
una transacción activa ENTONCES el sistema DEBE lanzar `IllegalTransactionStateException`.

## Pago rechazado (CA3, RF-E-03, STRIDE D, D4)

## R39
CUANDO el pago consultado es rechazado y tiene reserva del pago, el sistema DEBE guardar una fila de `pago` con
`estado = RECHAZADO` y el monto consultado, sin cambiar la reserva, y responder 200.

## R40
CUANDO una fila guardada según R39 deja 3 o más rechazos recientes, el sistema DEBE pedir a la pasarela cerrar el
checkout de esa reserva después de confirmar la transacción (D4, ⚠ S6).

## R41
SI falla el cierre de R40 ENTONCES el sistema DEBE responder 200 y conservar la fila de `pago` (⚠ S6).

## Idempotencia (STRIDE S/T, RNF-SEG-11)

## R42
SI el id de transacción ya existe en `pago` ENTONCES el sistema DEBE responder 200 sin escribir en `pago`, en `reserva`
ni en `auditoria`, sin enviar correos y sin cerrar el checkout (H2).

## R43
CUANDO llegan a la vez dos notificaciones de pago con el mismo id notificado, el sistema DEBE dejar una sola fila de
`pago` y enviar una sola vez los correos de R48 y R49.

## R44
SI la inserción en `pago` viola la unicidad de `id_transaccion_pasarela` ENTONCES el sistema DEBE responder 200.

## R45
El sistema DEBE impedir en la base de datos dos filas de `pago` con el mismo `id_transaccion_pasarela` y una fila con ese
valor nulo.

## R46
El sistema DEBE impedir en la base de datos dos filas de `pago` con `estado = PAGADO` para la misma reserva.

## R47
El sistema DEBE impedir en la base de datos una fila de `pago` con `estado` distinto de `PAGADO`, `RECHAZADO`,
`PAGADO_TARDIO`, `MONTO_DISTINTO`, `REEMBOLSADO` o `REEMBOLSO_FALLIDO`, con `monto` no positivo o con `ultimos4` que no
sean 4 dígitos, y dos filas de `checkout_pago` para la misma reserva.

## Correos (CA1, CA2, RF-E-01, RF-E-02, RNF-SEG-16, D5)

## R48
CUANDO se confirma la transacción de R31, el sistema DEBE enviar al correo del usuario de la reserva un comprobante con
el nombre del espacio, la fecha, el horario, el monto, los últimos 4 dígitos si existen y el id de transacción como
referencia (D5).

## R49
CUANDO se confirma la transacción de R31, el sistema DEBE enviar al correo del arrendador un aviso con el nombre del
espacio, la fecha y el horario, sin datos del usuario, sin monto y sin datos del pago (D5).

## R50
SI el sistema no aplica R31 ENTONCES el sistema NO DEBE enviar los correos de R48 y R49.

## R51
SI falla el envío de un correo ENTONCES el sistema DEBE responder 200 y mantener la reserva `CONFIRMADA`.

## Registro y datos de tarjeta (STRIDE R, I/T, RNF-SEG-12, RNF-CUM-01)

## R52
CUANDO el sistema guarda una fila de `pago`, el sistema DEBE registrar en la auditoría la acción `PAGO_APROBADO`,
`PAGO_RECHAZADO`, `PAGO_TARDIO` o `PAGO_MONTO_DISTINTO` según su estado, con `usuario_id` nulo, la entidad `pago` con su
id, la IP de la solicitud y el detalle `origen=WEBHOOK;reservaId=<id>;transaccion=<id>;monto=<monto>`.

## R53
CUANDO una reserva pasa a `CONFIRMADA`, el sistema DEBE registrar en la auditoría la acción `RESERVA_CONFIRMADA` con
`usuario_id` e `ip` nulos, la entidad `reserva` con su id y el detalle `origen=WEBHOOK;espacioId=<id>`.

## R54
El sistema NO DEBE guardar del pago más datos de tarjeta que `ultimos4`, ni escribir en la auditoría la firma, el
secreto o la respuesta de la pasarela.

## Selección de la pasarela (RNF-ARQ-08, D1, D7 revisada)

## R55
DONDE `espacioya.pagos.pasarela` vale `mercadopago`, el sistema DEBE registrar solo el adaptador de Mercado Pago como
`PasarelaPago`.

> *Adenda:* el código y sus pruebas no cambian (D8). Ningún entorno usa este valor hasta conectar Mercado Pago.

## R56
DONDE `espacioya.pagos.pasarela` vale `simulada`, el sistema DEBE registrar solo la pasarela simulada como `PasarelaPago`
(D7 revisada, ⚠ S11).

> *Adenda:* el requirement no cambia, pero deja de estar restringido a pruebas y desarrollo: también aplica a UAT.

## R57
SI `espacioya.pagos.pasarela` está vacía o tiene otro valor ENTONCES el backend NO DEBE arrancar (D7 revisada, ⚠ S11).

## R58
SI `espacioya.pagos.pasarela` vale `mercadopago` y falta la llave secreta, el secreto del webhook o la URL de
notificación ENTONCES el backend NO DEBE arrancar.

## Reemplazos y compatibilidad

- **RN-08 y `docs/architecture.md` (D2, D3):** "ese caso no ocurre" deja de ser cierto; un pago tardío se registra y su
  devolución es manual. "El checkout expira junto con la reserva" pasa a "1 minuto antes".
- **HU-12:** `GET /api/reservas/{id}` y `ReservaDTO` no cambian. `Reserva` gana `confirmar`; el proceso de expiración no
  cambia y ya revalida bajo bloqueo (HU-12 R16). `contarVigentes` libera el cupo al confirmar sin tocar nada.
- **HU-12 S5:** se cumple con R12 y R35.
- **HU-08:** `MENSAJE_RESERVA_PAGADA` se activa por primera vez en `CalendarioEspacioService`, porque ahora existen
  reservas `CONFIRMADA`. Una fecha con una pendiente no se puede bloquear, así que D6 no choca con el contrato DT-10.
- **HU-06, HU-09:** `despuesDelCommit` se mueve a `comun` sin cambiar su comportamiento.
- **`docs/architecture.md`:** "HU-13 amplía `EspacioRetenido`" es incorrecto: ese record solo lo devuelve
  `retenerSiLibres`. Se corrige; `bloquearEspacio` devuelve `EspacioBloqueado`.

## Adenda 2026-10-30 (D7 revisada, D8, D9)

> Pedida por Eduardo Saldaña con la historia en `in_progress`, tras el `APPROVED` con T23 abierta. Es un cambio al spec
> aprobado: **su implementación espera la aprobación del dueño**. Fuentes: `progress/HU-13.md`,
> `progress/review_HU-13.md`, `progress/doc_HU-13.md` § 10. Diseño en `design.md` § 14 y tasks T25–T43 en `tasks.md`.

### Decisiones del dueño (Eduardo Saldaña, 2026-10-30)

| # | Decisión | Afecta |
|---|----------|--------|
| D7 revisada | UAT usa la pasarela simulada hasta conectar Mercado Pago, y el informe lo declara. Reemplaza a la D7 original. | R55–R57, R83, R84 |
| D8 | Mercado Pago no se conecta por ahora. `MercadoPagoAdapter` y sus pruebas se quedan listos, y la transacción se hace con la pasarela simulada. T23 se difiere. | R1, R5, R24, R40, T23 |
| D9 | Al pulsar **Pagar** con `PAGOS_PASARELA=simulada` se abre una pantalla de pago simulado con **Aprobar** y **Rechazar**, para el video y para la caja negra de Ariel Enriquez, sin terminal. Solo existe con la simulada. | R59–R87 |

### Qué queda alterado

| Elemento | Estado tras la adenda |
|----------|-----------------------|
| **S1** | **Sigue sin comprobar.** Ninguna notificación real de Mercado Pago ha pasado por `MercadoPagoAdapter`. Solo se comprobó el vector de firma aceptado por el validador oficial. Deja de condicionar el cierre: se prueba con T23, al conectar. |
| S2 | Sin cambio: la URL real observada dice `data.id`; se siguen aceptando los dos nombres. La firma con ese valor sigue sin comprobar (S1). |
| S5, S6 | **Siguen sin comprobar:** tipos excluidos y cierre del checkout. Se prueban con T23. |
| S10, S12 | Solo aplican a `mercadopago`. |
| S11 | Texto ajustado a D7 revisada: UAT define `simulada` explícitamente. |
| R1 | Con la simulada, `urlPago` es la de R59. |
| R2, R4, R5, R17, R25–R28 (en su llamada real) | Sin cambios de código. Probados solo contra `StubMercadoPago`, no contra Mercado Pago. |
| R24 | Con `mercadopago` no cambia. Con la simulada admite además el token interno (R85). |
| R40 | Con la simulada, cerrar no llama a ninguna pasarela (S6 no se comprueba). |
| R52, R53 | Sin cambios: un pago simulado también audita `origen=WEBHOOK`, porque usa ese camino. R86 agrega quién lo decidió. |
| R55 | Sin cambios de código; ningún entorno lo usa hasta conectar. |
| R56, R57 | Ya no restringen la simulada a pruebas y desarrollo. El backend sigue sin arrancar si no se elige pasarela. |
| Pruebas `PasarelaSimuladaTest.creaUnCheckoutSimulado` (R56) y `PagoControllerIT.iniciaElPago` (R1, R20) | Afirman `?pago=exito`: cambian a la URL de R59. |
| T23 | **No se ha ejecutado.** Pasa a texto sin casilla en `tasks.md`, "Diferida por decisión del dueño (D8)". |

### Supuestos de la adenda, a confirmar por el dueño al aprobarla

> ⚠ Las decisiones no fijan estos puntos. Si **Eduardo Saldaña** no los acepta, cambian los requirements citados.

| # | Supuesto | Afecta |
|---|----------|--------|
| S15 | `POST …/simulacion` **no repite** vigencia, último tramo ni espacio visible (R10–R12): son reglas de *iniciar* un pago, y la pantalla ya no ofrece los botones en esos casos (R60). Quien dejó la pantalla abierta y pulsa después está en el caso de D2, y decide `WebhookPagoService`: `PAGADO_TARDIO` si venció. Así el pago tardío se puede demostrar sin terminal. Con el espacio oculto y la reserva vigente confirma, como D6. | R62, R65, R79 |
| S16 | En UAT, `PAGOS_SECRETO_WEBHOOK` queda **vacío**: así nadie aprueba un pago con `curl` por la ruta pública del webhook y la pantalla es la única entrada. Si alguien define ese secreto en UAT, quien lo conozca puede aprobar pagos de cualquier reserva. | R83, R84 |
| S17 | El id de un **Aprobar** es siempre `sim-<reserva>-APROBADO-1`, para que dos envíos sean el mismo pago (R42) y no un `PAGADO_TARDIO` falso. Un **Rechazar** usa el número de filas `RECHAZADO` más 1: dos a la vez cuentan como uno, sin otros efectos. | R68–R70 |
| S18 | Un pago simulado guarda `ultimos4 = 4242`, valor fijo que no es de ninguna tarjeta, y su comprobante (correo simulado hasta HU-25) lleva como referencia `sim-…`. | R87 |
| S19 | Simular exige la fila de `checkout_pago`: no se decide un pago sin haber pulsado **Pagar**, y por eso siempre queda `CHECKOUT_INICIADO`. | R60, R78 |
| S20 | `MONTO_DISTINTO` **no se puede producir desde la pantalla**, porque el cliente no envía monto (R71). La regla se ejecuta igual y sigue probada en `WebhookPagoIT` con `StubMercadoPago`. No es caso de la caja negra. | R63, R71 |

### Definiciones de la adenda

- **Pasarela simulada activa:** `espacioya.pagos.pasarela` vale `simulada`.
- **Resultado simulado:** `resultado` del cuerpo, `APROBADO` o `RECHAZADO`. Es el único dato que el cliente elige.
- **SimulacionDTO:** cuerpo con `disponible`. **SimularPagoDTO:** cuerpo con `resultado` (`design.md` § 14.4).
- **Checkout abierto:** la reserva tiene una fila en `checkout_pago` y menos de 3 rechazos recientes.
- **Id simulado:** `sim-<reserva>-APROBADO-1` o `sim-<reserva>-RECHAZADO-<n>`, con `n` igual al número de filas `RECHAZADO`
  de la reserva más 1 (⚠ S17).
- **Token interno:** valor aleatorio que `PasarelaSimulada` genera al crearse y que solo usa dentro del proceso.

## Pantalla de pago simulado (D9, CA1, CA3, CA4)

## R59
DONDE la pasarela simulada está activa, el sistema DEBE devolver en el CheckoutDTO de R1 la URL
`<espacioya.frontend.url>/reservas/<id>/pago-simulado`.

## R60
CUANDO, con la pasarela simulada activa, el usuario de la sesión envía `GET /api/reservas/{id}/pago/simulacion` de una
reserva propia, el sistema DEBE responder 200 con un SimulacionDTO cuyo `disponible` es `true` solo si la reserva tiene
pago disponible y una fila en `checkout_pago` (⚠ S19).

## R61
CUANDO el sistema procesa `GET /api/reservas/{id}/pago/simulacion`, el sistema NO DEBE escribir en la base de datos.

## El pago simulado usa el camino real (D9, RNF-CUM-02, STRIDE S/T, D, R)

## R62
CUANDO, con la pasarela simulada activa, el usuario de la sesión envía `POST /api/reservas/{id}/pago/simulacion` con un
resultado simulado sobre una reserva propia con checkout abierto, el sistema DEBE procesarlo con una sola llamada a
`WebhookPagoService.recibir`, con una notificación de pago cuyo id notificado es el id simulado, y responder 204 sin
cuerpo (⚠ S15).

## R63
CUANDO se procesa según R62 un resultado `APROBADO` de una reserva vigente, el sistema DEBE aplicar R30 y R31, con la
reserva en `CONFIRMADA` y una fila `PAGADO` por `monto_total` (⚠ S20).

## R64
CUANDO se confirma una reserva según R63, el sistema DEBE enviar los correos de R48 y R49 después de confirmar la
transacción.

## R65
CUANDO se procesa según R62 un resultado `APROBADO` de una reserva no vigente, el sistema DEBE aplicar R34, con una fila
`PAGADO_TARDIO`, sin cambiar la reserva y sin enviar correos (D2, ⚠ S15).

## R66
CUANDO se procesa según R62 un resultado `RECHAZADO`, el sistema DEBE aplicar R39, con una fila `RECHAZADO` y sin cambiar
la reserva.

## R67
CUANDO una fila guardada según R66 deja 3 o más rechazos recientes, el sistema DEBE aplicar R40 después de confirmar la
transacción.

## R68
SI el id simulado de un resultado `APROBADO` ya existe en `pago` ENTONCES el sistema DEBE aplicar R42 y responder 204 (⚠ S17).

## R69
CUANDO llegan a la vez dos `POST /api/reservas/{id}/pago/simulacion` con `APROBADO` de una reserva vigente, el sistema
DEBE dejar una sola fila de `pago`, con `estado = PAGADO`, y enviar una sola vez los correos de R48 y R49.

## R70
CUANDO llegan uno después de otro dos resultados `RECHAZADO` de una reserva, el sistema DEBE guardar dos filas con distinto
id de transacción, aunque el backend se haya reiniciado entre ellos (⚠ S17).

## R71
CUANDO el sistema procesa `POST /api/reservas/{id}/pago/simulacion`, el sistema DEBE tomar el monto de `monto_total` de la
reserva e ignorar cualquier campo del cuerpo distinto de `resultado` (STRIDE T, ⚠ S20).

## R72
SI el backend se reinicia entre el `POST /api/reservas/{id}/pago` y el `POST /api/reservas/{id}/pago/simulacion`
ENTONCES el sistema DEBE procesar el segundo igual que sin reinicio.

## Quién puede simular (D9, STRIDE S, E)

## R73
SI `resultado` falta o es distinto de `APROBADO` o `RECHAZADO` ENTONCES el sistema DEBE responder 400 a
`POST /api/reservas/{id}/pago/simulacion`, sin llamar a `WebhookPagoService`.

## R74
SI una cuenta con rol distinto de `USUARIO` envía `GET` o `POST /api/reservas/{id}/pago/simulacion` ENTONCES el sistema
DEBE responder 403 con el mensaje *acceso denegado*, sin llamar a `WebhookPagoService`.

## R75
SI `{id}` de `GET` o `POST /api/reservas/{id}/pago/simulacion` está mal escrito, no existe o es de una reserva de otro
usuario ENTONCES el sistema DEBE responder 404 con el mensaje *recurso no encontrado*, sin llamar a `WebhookPagoService`.

## R76
SI un cliente sin sesión vigente envía `GET` o `POST /api/reservas/{id}/pago/simulacion` ENTONCES el sistema DEBE responder
401 con el mensaje *sesión inválida*.

## R77
SI la reserva propia tiene 3 o más rechazos recientes ENTONCES el sistema DEBE responder a
`POST /api/reservas/{id}/pago/simulacion` 429 con el mensaje *demasiados intentos*, sin llamar a `WebhookPagoService` (D4).

## R78
SI la reserva propia no tiene fila en `checkout_pago` ENTONCES el sistema DEBE responder a
`POST /api/reservas/{id}/pago/simulacion` 400 con el mensaje *pago no disponible*, sin llamar a `WebhookPagoService`
(⚠ S19).

## R79
CUANDO un cliente con sesión vigente envía `POST /api/reservas/{id}/pago/simulacion` con un cuerpo válido, el sistema DEBE
aplicar las comprobaciones en este orden y responder con la primera que falle: rol (R74), reserva (R75), rechazos (R77) y
checkout (R78).

## R80
El sistema NO DEBE incluir en el SimulacionDTO ni en el SimularPagoDTO datos de tarjeta, el monto, el id de transacción ni
el id del checkout (STRIDE I/T, RNF-CUM-01).

## Solo con la pasarela simulada (D9, D8)

## R81
DONDE `espacioya.pagos.pasarela` vale `mercadopago`, el sistema NO DEBE registrar `PagoSimuladoController` ni
`SimulacionPago`.

## R82
SI, con `mercadopago`, una cuenta con sesión envía `GET` o `POST /api/reservas/{id}/pago/simulacion` ENTONCES el sistema
DEBE responder 404, sin escribir en `pago` ni en `reserva`.

## R83
MIENTRAS la pasarela simulada está activa y `espacioya.pagos.secreto-webhook` está vacío, el sistema DEBE responder 401 sin
cuerpo a toda notificación de pago recibida en `POST /api/pagos/webhook` (D7 revisada, ⚠ S16).

## R84
MIENTRAS la pasarela simulada está activa y `espacioya.pagos.secreto-webhook` está vacío, el sistema DEBE aplicar R62 (⚠ S16).

## R85
El sistema DEBE firmar la notificación de R62 con el token interno, distinto en cada instancia de `PasarelaSimulada`, sin
guardarlo ni devolverlo en ninguna respuesta.

## Registro del pago simulado (STRIDE R, D7 revisada)

## R86
CUANDO el sistema va a procesar un resultado según R62, el sistema DEBE registrar en la auditoría la acción `PAGO_SIMULADO`
con el id del usuario de la sesión, la entidad `reserva` con su id, la IP del cliente y el detalle
`resultado=<resultado>;transaccion=<id simulado>`.

## R87
CUANDO el sistema guarda una fila de `pago` según R62, el sistema DEBE guardar como `id_transaccion_pasarela` el id
simulado, con el prefijo `sim-` (⚠ S18).

## Defectos corregidos por la adenda

DEF-01 y DEF-02 son de pantalla, no llevan `R<n>` y se verifican con lint, build y caja negra (`design.md` § 14.11).
DEF-03 y DEF-04 son de documentación (`tasks.md` T35 y T36). Ninguno viola un `R<n>` anterior.

## Trazabilidad con la historia

| Origen (`docs/historias/HU-13.md` y fuentes)                                | Cubierto por |
|-----------------------------------------------------------------------------|--------------|
| CA1: pago en el checkout de la pasarela                                     | R1–R7, R23–R28; con la simulada, R59, R60, R62 |
| CA1: … la reserva pasa a "confirmada"                                       | R30, R31, R32, R35–R38, R63, R72 |
| CA1: … recibo un correo con el comprobante                                  | R48, R50, R51, R64 |
| CA2: el arrendador recibe un correo con fecha, horario y espacio            | R49, R50, R51, R64 |
| CA3: pago rechazado → mensaje genérico                                      | R19, R20, R39, R66 (pantalla en design § 10 y § 14.10) |
| CA3: … puedo reintentar mientras la reserva siga vigente                    | R1, R6, R19, R39, R70 |
| CA4: la reserva expiró → el checkout ya no está disponible                  | R4, R10, R11, R19, R34, R60, R65 |
| CA4: … puedo reservar de nuevo                                              | R10 (texto y enlace en design § 10) |
| (I/T) Checkout PCI DSS; la plataforma nunca recibe ni guarda la tarjeta     | R1, R3, R20, R54, R80. En UAT no hay pasarela certificada hasta T23 (D8): la pantalla simulada no pide tarjeta |
| (S/T) Estado cambia solo por webhook con firma verificada                   | R8, R21, R24, R25, R26, R31, R62, R85. Firma real sin comprobar (S1); ver riesgos en design § 14.13 |
| (S/T) Webhook idempotente                                                   | R42–R46, R68, R69 |
| (T) Monto calculado en servidor                                             | R2, R3, R33, R71 |
| (D) Máximo 3 intentos fallidos por reserva cada 30 min                      | R13, R19, R39, R40, R41, R67, R77 |
| (R) Registro de cada transacción                                            | R9, R29, R30, R33, R34, R39, R52, R53, R86, R87 |
| RF-E-01 / RF-E-02 / RF-E-03 / RF-E-04                                       | R1–R9, R30–R32, R48 / R49 / R19, R39 / R4, R10, R11 |
| RNF-SEG-07 · RNF-SEG-11 · RNF-SEG-12                                        | R2, R3, R33 · R24, R42–R46 · R9, R52, R53 |
| RNF-SEG-16 · RNF-SEG-17                                                     | R20, R49 · R13, R39–R41 |
| RNF-CUM-01 · RNF-CUM-02 · RNF-ARQ-08                                        | R1, R20, R48, R54 · R8, R21, R24–R26, R31 · R55–R58 |
| RN-03 (estados de la reserva) · RN-08 (corregida por D2 y D3)               | R31, R32, R34, R37 · R4, R11, R34 |
| D7 revisada: UAT con la simulada, sin webhook público abierto               | R56, R57, R83, R84; el informe lo declara (`progress/doc_HU-13.md`) |
| D8: Mercado Pago sin conectar; adaptador listo                              | R55, R58, R81, R82; pendiente en T23 y en `docs/requerimientos.md` § 9 |
| D9: pantalla con Aprobar y Rechazar, solo con la simulada                   | R59–R62, R73–R82 |
| D9: el simulado pasa por las mismas reglas                                  | R36, R62–R72 |
| HU-12 S5 (espacio o arrendador que cambian)                                 | R12, R35 |
| Bloqueos `usuario → espacio → reserva`, HU-08 DT-10                         | R36, R37, R38 |
