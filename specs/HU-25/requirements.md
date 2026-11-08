# HU-25 · Recordatorio de reserva por correo — Requirements

> Fuentes: `docs/historias/HU-25.md` (CA1–CA3, STRIDE I y E), `docs/requerimientos.md` (RF-D-10, RF-D-11, RF-A-26,
> RN-14, RNF-SEG-01, RNF-SEG-16, RNF-ARQ-08, RNF-USA-04, §8 fila "Envío de correo", §9 punto 8),
> `docs/architecture.md` (Patrones por historia: HU-25 ★ Factory Method + Adapter en `comun/correo`; Perfil: "HU-25
> implementa los dos métodos nuevos de `NotificadorCorreo` en el adaptador real") y `progress/explore_HU-25.md`.
> Las decisiones (D1–D13) están en `design.md`. Las respuestas del dueño (Jorge Quispe, 2026-11-07) a las preguntas
> P1–P3 quedaron cerradas en `design.md` § 14: UAT con proveedor `simulado` (D12), recordatorio también para reservas
> hechas con menos de 24 h (D2) y `recordatoriosActivos` en la exportación de HU-22 (D13, R37).
>
> Términos:
> - **Inicio de la reserva:** fecha y hora del primer bloque (`ReservaConHorario.instanteDeInicio()`, hora de
>   `America/Lima` convertida a `Instant`).
> - **Plazo del recordatorio:** `inicio − 24 h ≤ ahora < inicio`.
> - **Proceso de recordatorios:** `RecordatoriosReserva.enviarPendientes()`, llamado por el proceso programado o por
>   las pruebas.
> - **Ruta de preferencias:** `/api/usuarios/actual/preferencias-correo`.

## A. Preferencia de recordatorios (CA2, RF-A-26)

## R1
CUANDO una cuenta con rol `USUARIO` o `ARRENDADOR` envía `GET /api/usuarios/actual/preferencias-correo` con una
sesión válida, el sistema DEBE responder 200 con `{ "recordatoriosActivos": <boolean> }` de la cuenta de la sesión.

## R2
CUANDO una cuenta con rol `USUARIO` o `ARRENDADOR` envía `PUT /api/usuarios/actual/preferencias-correo` con
`recordatoriosActivos` `true` o `false`, el sistema DEBE guardar ese valor en `usuario.recordatorios_activos` de la
cuenta de la sesión y responder 200 con el valor guardado.

## R3
SI el cuerpo del `PUT` de la ruta de preferencias trae además un campo `usuarioId` (u otro campo desconocido)
ENTONCES el sistema DEBE ignorarlo y cambiar solo la preferencia de la cuenta de la sesión.

## R4
SI el `PUT` de la ruta de preferencias llega sin `recordatoriosActivos` o con `null` ENTONCES el sistema DEBE
responder 400 con un error en el campo `recordatoriosActivos` (`MENSAJE_RECORDATORIOS_OBLIGATORIO`) sin cambiar la
preferencia.

## R5
SI una cuenta con rol `ADMIN` llama al `GET` o al `PUT` de la ruta de preferencias ENTONCES el sistema DEBE responder
403 sin leer ni cambiar ninguna preferencia.

## R6
SI se llama al `GET` o al `PUT` de la ruta de preferencias sin sesión válida ENTONCES el sistema DEBE responder 401.

## R7
El sistema DEBE responder el `GET` y el `PUT` de la ruta de preferencias con `Cache-Control: no-store`.

## R8
CUANDO el `PUT` de la ruta de preferencias cambia el valor guardado, el sistema DEBE registrar en la auditoría la
acción `PREFERENCIA_RECORDATORIOS` con el id de la cuenta, la IP, la entidad `usuario` y el detalle
`recordatoriosActivos=<anterior>-><nuevo>`.

## R9
SI el `PUT` de la ruta de preferencias envía el mismo valor que ya estaba guardado ENTONCES el sistema DEBE responder
200 sin registrar auditoría.

## R10
El sistema DEBE dejar `recordatorios_activos = true` en toda cuenta existente al migrar y en toda cuenta nueva.

## B. Envío del recordatorio (CA1, CA2, RF-D-10, RF-D-11, RN-14)

## R11
CUANDO el proceso de recordatorios se ejecuta y una reserva `CONFIRMADA` sin recordatorio está dentro del plazo del
recordatorio, el sistema DEBE enviar un recordatorio al correo de la cuenta `reserva.usuario_id`, leído en el servidor.

## R12
CUANDO el proceso de recordatorios se ejecuta y una reserva `CONFIRMADA` sin recordatorio está dentro del plazo del
recordatorio, el sistema DEBE enviar un recordatorio al correo del arrendador del espacio de la reserva
(`CatalogoEspaciosService.arrendadoresDe`), leído en el servidor.

## R13
CUANDO el proceso de recordatorios procesa una reserva dentro del plazo, el sistema DEBE guardar
`reserva.recordatorio_enviado_en` con el *ahora* de la pasada, en la misma transacción que lee los destinatarios.

## R14
SI una reserva ya tiene `recordatorio_enviado_en` ENTONCES el proceso de recordatorios NO DEBE enviarle otro
recordatorio.

## R15
SI todavía falta más de 24 h para el inicio de la reserva (`ahora < inicio − 24 h`) ENTONCES el proceso de
recordatorios NO DEBE enviar el recordatorio ni guardar `recordatorio_enviado_en`.

## R16
SI la reserva ya empezó (`ahora ≥ inicio`) ENTONCES el proceso de recordatorios NO DEBE enviar el recordatorio ni
guardar `recordatorio_enviado_en`.

## R17
SI la reserva está `PENDIENTE_PAGO`, `EXPIRADA` o `CANCELADA` ENTONCES el proceso de recordatorios NO DEBE enviar el
recordatorio.

## R18
SI entre la lectura de candidatas y el bloqueo de su fila la reserva deja de estar `CONFIRMADA` o ya recibió su
recordatorio ENTONCES el proceso de recordatorios NO DEBE enviar el recordatorio (relee la fila con
`findParaActualizar`).

## R19
MIENTRAS el usuario de la reserva tenga `recordatorios_activos = false`, el proceso de recordatorios NO DEBE enviarle
el recordatorio (al arrendador se le envía según su propia preferencia).

## R20
MIENTRAS el arrendador del espacio tenga `recordatorios_activos = false`, el proceso de recordatorios NO DEBE enviarle
el recordatorio (al usuario se le envía según su propia preferencia).

## R21
SI la cuenta del usuario o del arrendador no está `ACTIVA` (suspendida o eliminada) ENTONCES el proceso de
recordatorios NO DEBE enviarle el recordatorio.

## R22
CUANDO el proceso de recordatorios procesa una reserva dentro del plazo, el sistema DEBE registrar en la auditoría la
acción `RECORDATORIO_RESERVA` con `usuario_id` e `ip` nulos, la entidad `reserva`, su id y el detalle
`origen=PROCESO;destinatarios=<USUARIO,ARRENDADOR | USUARIO | ARRENDADOR | NINGUNO>`.

## R23
SI la transacción que procesa una reserva se revierte ENTONCES el sistema NO DEBE enviar ninguno de sus recordatorios
(se envían con `DespuesDelCommit`).

## R24
SI falla el envío del recordatorio a uno de los destinatarios ENTONCES el sistema DEBE enviar igual el del otro y
conservar `recordatorio_enviado_en` (no se reintenta), registrando el fallo en el log sin el correo del destinatario.

## R25
SI falla el procesamiento de una reserva (excepción) ENTONCES el proceso de recordatorios DEBE seguir con las demás
reservas candidatas de la misma pasada.

## R26
DONDE `espacioya.reservas.recordatorios.activa=true`, el sistema DEBE ejecutar el proceso de recordatorios cada
`espacioya.reservas.recordatorios.intervalo` (por defecto `PT5M`) con retraso fijo entre pasadas; con `false` NO
DEBE programarlo.

## C. Correos que no se desactivan (CA3, RF-D-11, RN-14)

## R27
MIENTRAS el usuario y el arrendador tengan `recordatorios_activos = false`, CUANDO se confirma el pago de una reserva
(webhook de HU-13), el sistema DEBE enviar igual el comprobante al usuario y el aviso de reserva pagada al
arrendador.

## R28
MIENTRAS el usuario y el arrendador tengan `recordatorios_activos = false`, CUANDO se cancela una reserva (HU-15),
el sistema DEBE enviar igual el aviso de cancelación al usuario y al arrendador.

## D. Contenido del correo (STRIDE I, RNF-SEG-16)

## R29
El sistema DEBE armar cada recordatorio solo con el nombre del espacio, la fecha (`dd/MM/yyyy`) y el horario
(`HH:00 a HH:00`) de la reserva: sin datos de tarjeta (ni últimos 4 dígitos), documentos, montos, ni correo o
teléfono de la otra parte.

## R30
El sistema DEBE enviar cada correo en un mensaje propio con un único destinatario, sin copia ni copia oculta.

## R31
El sistema DEBE usar como asunto de cada correo un texto fijo por evento (tabla C1), sin datos ingresados por
usuarios.

## E. Proveedor de correo (★ Adapter, RNF-ARQ-08)

## R32
DONDE `espacioya.correo.proveedor=smtp`, el sistema DEBE entregar cada `MensajeCorreo` al servidor SMTP configurado
como un mensaje de texto plano con remitente `espacioya.correo.remitente`, el destinatario, el asunto y el cuerpo del
mensaje.

## R33
SI el servidor SMTP falla al enviar (cualquier `MailException`) ENTONCES `EnvioCorreoSmtp` DEBE lanzar
`ServicioExternoException` sin el texto del proveedor ni el destinatario.

## R34
SI `espacioya.correo.proveedor=smtp` y falta `espacioya.correo.remitente` o `spring.mail.host`, o si
`espacioya.correo.proveedor` tiene un valor distinto de `smtp` o `simulado`, ENTONCES el backend DEBE fallar al
arrancar.

## R35
DONDE `espacioya.correo.proveedor=simulado` (valor por defecto), el sistema NO DEBE enviar ningún correo real ni
escribir en el log el destinatario, el asunto o el cuerpo del mensaje.

## R36
DONDE `espacioya.correo.proveedor=smtp`, CUANDO un servicio llama a un método de `NotificadorCorreo`, el sistema DEBE
enviar un único `MensajeCorreo` al correo recibido con el asunto y los datos de la tabla C1 para ese evento.

### Tabla C1 — Asunto y contenido de cada correo

Formatos (`comun.correo.FormatoCorreo`): fecha `dd/MM/yyyy`; horario `HH:00 a HH:00` (la hora fin 24 se escribe
`24:00`); monto `S/ 1234.50` (escala 2); instante en `America/Lima` `dd/MM/yyyy HH:mm`. Texto plano, sin HTML.

| Evento (método de `NotificadorCorreo` o recordatorio) | Asunto (fijo) | El cuerpo incluye | El cuerpo NO incluye |
|---|---|---|---|
| `notificarVerificacion` | `Bienvenido a EspacioYa` | Que la cuenta ya está lista para iniciar sesión | Contraseña, enlace |
| `notificarRecuperacion` | `Recupera tu contraseña de EspacioYa` | El enlace y que vence en 30 minutos | Contraseña |
| `notificarIdentidadAprobada` | `Tu identidad fue verificada` | Que ya puede publicar espacios | Documentos |
| `notificarIdentidadRechazada` | `Tu identidad no fue verificada` | El motivo y que puede volver a subir documentos | Documentos |
| `notificarCuentaSuspendida` | `Tu cuenta fue suspendida` | El motivo | — |
| `notificarCuentaReactivada` | `Tu cuenta fue reactivada` | Que ya puede iniciar sesión | — |
| `notificarPagoConfirmado` | `Comprobante de tu reserva` | Espacio, fecha, horario, monto, referencia y, si los hay, los últimos 4 dígitos de la tarjeta (HU-13 D5) | Número completo de la tarjeta |
| `notificarReservaPagada` | `Tienes una reserva pagada` | Espacio, fecha, horario | Datos del usuario o del pago |
| `notificarCancelacionAlUsuario` | `Tu reserva fue cancelada` | Espacio, fecha, horario, quién canceló, porcentaje y monto del reembolso | Datos de la tarjeta |
| `notificarCancelacionAlArrendador` | `Se canceló una reserva de tu espacio` | Espacio, fecha, horario, quién canceló | Datos del usuario o del reembolso |
| `notificarCambioCuentaBancaria` | `Cambió tu cuenta bancaria` | Banco, número enmascarado y hasta cuándo se retiene la liquidación | Número completo |
| `notificarCambioCorreo` | `Confirma tu nuevo correo` | El enlace y que vence en 30 minutos | — |
| `notificarAvisoCambioCorreo` | `Se pidió cambiar el correo de tu cuenta` | El correo nuevo enmascarado | El correo nuevo completo |
| Recordatorio al usuario | `Recordatorio: tu reserva es mañana` | Espacio, fecha, horario | Ver R29 |
| Recordatorio al arrendador | `Recordatorio: reserva en tu espacio` | Espacio, fecha, horario | Ver R29 |

## F. Exportación de datos (HU-22, decisión P3 del dueño)

## R37
CUANDO el sistema arma la exportación de "descargar mis datos" (HU-22, `POST /api/usuarios/actual/exportaciones`),
el bloque `perfil` DEBE incluir además el campo `recordatoriosActivos` con el valor de `usuario.recordatorios_activos`
del titular (`true` o `false`), sin cambiar los demás campos del bloque (HU-22 R3, R4).

## Trazabilidad con la historia

| Origen (docs/historias/HU-25.md y requerimientos) | Cubierto por |
|---|---|
| CA1: faltan 24 h para una reserva confirmada → usuario y arrendador reciben un recordatorio | R11, R12, R13, R14, R15, R16, R17, R18, R22, R23, R24, R25, R26 |
| CA2: desactivo los recordatorios en mi perfil → no recibo ese correo | R1, R2, R4, R5, R6, R7, R8, R9, R10, R19, R20 |
| CA3: con recordatorios desactivados sigo recibiendo los correos de pago y cancelación | R27, R28 |
| (I) Los correos no incluyen datos de tarjeta ni documentos | R29, R30, R31, R35, R36 (tabla C1) |
| (E) Destinatarios obtenidos de la reserva en servidor | R3, R11, R12, R18, R21 |
| RF-D-10 / RN-14 (recordatorio 24 h antes) | R11, R12, R15, R16 |
| RF-D-11 / RN-14 (solo el recordatorio se desactiva) | R19, R20, R27, R28 |
| RF-A-26 (activar o desactivar desde el perfil) | R1, R2 |
| RNF-SEG-01 (identidad de la sesión, también en recordatorios) | R1, R2, R3, R6 |
| RNF-SEG-16 (minimización de datos en correos) | R29, R30, tabla C1 |
| RNF-ARQ-08 (★ Adapter del proveedor de correo) | R32, R33, R34, R35 |
| ★ Factory Method "crear el correo según el evento" (`docs/architecture.md`) | R29, R31, R36 (ver `design.md` § 3.1) |
| §9 punto 8 (proceso programado para RF-D-10) | R26 |
| Decisión P3 del dueño: la preferencia se incluye en la exportación de HU-22 (D13) | R37 |
| Decisión P1 del dueño: UAT con proveedor `simulado` (D12); CA1 se demuestra con el log y la auditoría | R22, R35 |
| Decisión P2 del dueño: reservas hechas con < 24 h también reciben el recordatorio (D2) | R11, R12, R16 |
