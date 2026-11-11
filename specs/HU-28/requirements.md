# HU-28 · Historial de pagos y comprobantes — Requirements

> Épica E · módulo `pagos` · Release 2, Sprint 8 · tamaño S · depende de HU-13 y HU-16.
> Cubre RF-E-13, RF-E-14, RNF-SEG-19 y RNF-CUM-01 (comprobantes con los últimos 4 dígitos), y usa RN-07.
> Rutas de este spec: `GET /api/transacciones?pagina=n` y `GET /api/transacciones/{id}/comprobante`.
> Una "transacción" es una fila de `pago`; su `id` es `pago.id`. El usuario sale siempre de la sesión.

## R1
CUANDO un usuario con rol `USUARIO` envía `GET /api/transacciones`, el sistema DEBE responder 200 con sus
transacciones, cada una con `id`, `reservaId`, `fecha` (día del pago en `America/Lima`), `espacio` (nombre), `monto` y
`estado`.

## R2
El sistema DEBE listar solo los pagos en estado `PAGADO` o `REEMBOLSADO`.

## R3
SI un pago de la cuenta está en `RECHAZADO`, `PAGADO_TARDIO`, `MONTO_DISTINTO` o `REEMBOLSO_FALLIDO` ENTONCES el
sistema NO DEBE incluirlo en el historial ni permitir descargar su comprobante.

## R4
CUANDO un pago listado está `REEMBOLSADO`, el sistema DEBE devolver `estado = "REEMBOLSADO"` y `montoReembolsado` con la
suma de sus reembolsos en estado `REEMBOLSADO`; con el pago `PAGADO`, `montoReembolsado` DEBE ser `null`.

## R5
El sistema DEBE ordenar el historial de la transacción más reciente a la más antigua (`creado_en` e `id`
descendentes), de 10 en 10, con `pagina` (desde 1) y `hayMas`, sin total.

## R6
SI `pagina` está presente y no es un entero positivo ENTONCES el sistema DEBE rechazar la solicitud con 400 y un error
en el campo `pagina`.

## R7
SI la cuenta no tiene ninguna transacción elegible ENTONCES el sistema DEBE responder 200 con la lista vacía y
`hayMas = false`.

## R8
El sistema DEBE devolver en el historial solo pagos de reservas cuyo `usuario_id` es el de la sesión, y NO DEBE leer
ningún identificador de usuario del cuerpo, de la URL ni de los parámetros (RNF-SEG-19, STRIDE E/I).

## R9
SI el rol de la sesión no es `USUARIO` ENTONCES el sistema DEBE responder 403 en el historial y en el comprobante, antes
de validar `pagina` o `id`.

## R10
SI la solicitud no trae una sesión válida ENTONCES el sistema DEBE responder 401 en el historial y en el comprobante.

## R11
CUANDO un `USUARIO` envía `GET /api/transacciones/{id}/comprobante` de una transacción propia y elegible, el sistema
DEBE responder 200 con un PDF (`Content-Type: application/pdf`, cuerpo que empieza con `%PDF-`) y
`Content-Disposition: attachment; filename="comprobante-<id>.pdf"`.

## R12
El comprobante DEBE mostrar el número de comprobante (`id`), la fecha del pago en `America/Lima`, el espacio, la fecha y
el horario de la reserva, el monto pagado y el estado.

## R13
CUANDO la transacción está `REEMBOLSADO`, el comprobante DEBE mostrar además el monto reembolsado.

## R14
El comprobante DEBE mostrar de la tarjeta solo los últimos 4 dígitos (STRIDE I, RNF-CUM-01).

## R15
SI `ultimos4` es nulo o no son exactamente 4 dígitos ENTONCES el comprobante DEBE indicar que la tarjeta no está
informada, sin ningún dígito.

## R16
El comprobante NO DEBE contener el `id_transaccion_pasarela`, el correo, el teléfono ni el nombre del usuario, ni datos
del arrendador (RNF-SEG-16).

## R17
SI la transacción pedida es de otro usuario, no existe, no es elegible (R3) o su `id` está mal escrito ENTONCES el
sistema DEBE responder el mismo 404 genérico en todos los casos, sin generar el PDF (RNF-SEG-19, STRIDE E).

## R18
El sistema NO DEBE incluir en el listado ni en el DTO `ultimos4`, `id_transaccion_pasarela` ni `usuarioId`.

## R19
DONDE la pasarela configurada es `simulada`, el sistema DEBE incluir los pagos `sim-` con `simulada = true` y el
comprobante DEBE indicar que es un pago simulado en el que no se cobró dinero.

## R20
SI la pasarela configurada no es `simulada` ENTONCES el sistema NO DEBE listar los pagos cuyo `id_transaccion_pasarela`
empieza por `sim-` y DEBE responder 404 a su comprobante.

## R21
El sistema DEBE responder el historial y el comprobante con `Cache-Control: no-store`.

## R22
El sistema NO DEBE modificar ninguna fila de `pago`, `reembolso` ni `reserva` al listar o descargar.

## Trazabilidad con la historia

| Origen (docs/historias/HU-28.md)                                                    | Cubierto por             |
|-------------------------------------------------------------------------------------|--------------------------|
| CA1: veo fecha, espacio, monto y estado (pagado, reembolsado)                       | R1, R2, R3, R4, R5, R6   |
| CA1 (sin transacciones, lista vacía)                                                | R7                       |
| CA2: selecciono una transacción y descargo un PDF                                   | R11, R12, R13            |
| (E/I) cada consulta y descarga valida que la transacción sea del usuario de sesión | R8, R9, R10, R17, R18    |
| (I) el comprobante muestra solo los últimos 4 dígitos de la tarjeta                | R14, R15, R16            |
| RN-07 (estados de la transacción: pagada, reembolsada; fallido no es "pagado")      | R2, R3, R4               |
| Pasarela simulada (docs/architecture.md, filas `sim-`, RS-5)                        | R19, R20                 |
| Minimización y no-store (RNF-SEG-16, datos financieros)                             | R16, R21, R22            |
