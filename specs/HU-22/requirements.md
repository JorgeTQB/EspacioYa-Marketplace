# HU-22 — Requirements: Derechos sobre datos personales

> Módulo `cuentas` (con participantes en `reservas` y `pagos`) · Release 2 · Sprint 6 · tamaño M · depende de HU-01,
> HU-19 y HU-20 (`done`). Dueño: Kevin Chan · Prueba y documenta: Jorge Quispe.
>
> Fuentes: `docs/historias/HU-22.md` (CA1–CA3, STRIDE S, I, R), `docs/requerimientos.md` (RF-A-23, RF-A-24, RF-A-25,
> RNF-SEG-03, RNF-SEG-12, RNF-CUM-04, RN-17, §9 punto 6), `docs/architecture.md` (puerto
> `cuentas.ParticipanteDatosPersonales`, § Perfil HU-20, § Cuenta bancaria HU-18, § Liquidación HU-19, § Mis reservas
> HU-14, § Estado del espacio HU-21), `docs/modelo-datos.md`, `specs/HU-20`, `specs/HU-19` y `progress/explore_HU-22.md`.
>
> Las decisiones de producto tomadas por defecto están en `design.md` § 2 "Decisiones para revisar por el dueño" y los
> supuestos al final de este archivo. Los textos de los mensajes están en `design.md` § 10.

## Glosario

- **Titular:** cuenta de la sesión con rol `USUARIO` o `ARRENDADOR` (la regla de HU-20 `PerfilService.exigirTitular`).
  La identidad sale siempre de la sesión: ninguna ruta lleva un id.
- **Ruta de exportación:** `POST /api/usuarios/actual/exportaciones`, cuerpo `{ "contrasena": "…" }`.
- **Ruta de eliminación:** `POST /api/usuarios/actual/eliminacion`, cuerpo `{ "contrasena": "…" }`.
- **Cupo de contraseña:** el de HU-20 (`ReautenticacionPerfil`): 5 fallos en 15 minutos por cuenta con la clave
  `perfil|<id>`, compartido con el cambio de correo y la facturación.
- **Reserva futura del usuario:** reserva del titular `USUARIO` con `ReservaConHorario.esProxima(ahora, ahoraLocal)`:
  estado leído `PENDIENTE_PAGO` (vigente) o `CONFIRMADA` y fin posterior a la hora actual de Lima. Incluye las que están
  en curso.
- **Reserva por terminar del arrendador:** el criterio de HU-21: algún bloque de
  `ReservasDelEspacio.bloquesReservados(espacioId, ventana)` de un espacio del titular `ARRENDADOR` (en cualquier
  estado del espacio) con `BloqueReservado.terminaDespuesDe(ahoraLocal)`.
- **Liquidación pendiente:** liquidación del arrendador en `GENERADA` o `PENDIENTE_CUENTA`
  (`MisLiquidacionesService.tieneLiquidacionesPendientes`).
- **Cobro por liquidar:** pago `PAGADO` de una reserva de un espacio del arrendador que `PagoRepository.pagadosSinLiquidar`
  devuelve (sin reembolso y sin línea de liquidación) y cuya reserva `ReservasLiquidables.describir` informa como
  `admitida`.
- **Registros contables:** filas de `reserva`, `reserva_bloque`, `pago`, `reembolso`, `checkout_pago`, `liquidacion` y
  `liquidacion_detalle`, y la `auditoria`.
- **Marcador de correo:** `eliminada-<id>@espacioya.invalid` (en minúsculas, único porque lleva el id).

## Exportación (CA1, RF-A-23, RNF-CUM-04)

## R1
CUANDO un titular `USUARIO` envía la ruta de exportación con su contraseña correcta, el sistema DEBE responder 200 con un
JSON que contiene `generadaEn`, `perfil` y `secciones.reservas` y `secciones.pagos`.

## R2
CUANDO un titular `ARRENDADOR` envía la ruta de exportación con su contraseña correcta, el sistema DEBE responder 200 con
un JSON que contiene `generadaEn`, `perfil`, `secciones.reservas`, `secciones.liquidaciones` y
`secciones.cuentaBancaria`.

## R3
CUANDO el sistema arma la exportación, el bloque `perfil` DEBE contener `rol`, `correo`, `correoPendiente`, `nombre`,
`telefono`, `tieneFoto`, `mfaActivo` y `consentimiento` (`versionPolitica` y `fechaConsentimiento`) leídos de la cuenta
del titular.

## R4
CUANDO el titular es `ARRENDADOR`, el bloque `perfil` de la exportación DEBE incluir `facturacion` con `tipoDocumento`,
`numeroDocumento` **enmascarado** (`****` + 4 últimos), `razonSocial`, `direccionFiscal` y `estadoIdentidad`.

## R5
CUANDO el titular es `USUARIO`, `secciones.reservas` DEBE listar todas sus reservas, en cualquier estado, cada una con
`id`, `espacio` (nombre), `fecha`, `horaInicio`, `horaFin`, `estado` leído con `estadoEn(ahora)`, `precioHora`,
`montoTotal`, `creadaEn` y, si está cancelada, `canceladaPor`, `canceladaEn` y `montoReembolso`.

## R6
CUANDO el titular es `USUARIO`, `secciones.pagos` DEBE listar los pagos de sus reservas, cada uno con `reservaId`,
`monto`, `estado`, `creadoEn` y la lista `reembolsos` (`monto`, `estado`, `creadoEn`).

## R7
CUANDO el titular es `ARRENDADOR`, `secciones.reservas` DEBE listar las reservas `CONFIRMADA` de sus espacios con `id`,
`espacio`, `fecha`, `horaInicio`, `horaFin`, `estado` y `montoTotal`.

## R8
CUANDO el titular es `ARRENDADOR`, `secciones.liquidaciones` DEBE listar todas sus liquidaciones con los campos de
`MiLiquidacionDTO` (HU-19), de la más reciente a la más antigua.

## R9
CUANDO el titular es `ARRENDADOR`, `secciones.cuentaBancaria` DEBE contener `banco`, `numero` enmascarado, `titular` y
`estado` de su cuenta bancaria, o `null` si no la registró.

## R10
CUANDO la exportación responde 200, el sistema DEBE incluir el encabezado
`Content-Disposition: attachment; filename="espacioya-mis-datos-<AAAA-MM-DD>.json"` con la fecha de hoy en
`America/Lima`.

## R11
El sistema DEBE responder con `Cache-Control: no-store` las respuestas 200 de la ruta de exportación y de la de
eliminación.

## R12
(I) SI otra cuenta tiene reservas, pagos o liquidaciones ENTONCES el sistema NO DEBE incluir ninguno de esos registros
en la exportación del titular de la sesión.

## R13
(I) El sistema NO DEBE incluir en la exportación el hash de la contraseña, el número de documento completo, el número
de cuenta bancaria completo, el secreto MFA, los `ultimos4` ni el `id_transaccion_pasarela` de un pago, ni el id,
nombre, correo o teléfono del usuario que reservó un espacio del arrendador.

## R14
(R) CUANDO una exportación responde 200, el sistema DEBE registrar en la auditoría `DATOS_EXPORTADOS` con el id del
titular, la IP, `entidad = usuario`, `entidad_id` = id del titular y `detalle = "secciones=<claves separadas por coma>"`,
sin valores personales.

## Reautenticación (STRIDE S, RNF-SEG-03)

## R15
(S) SI la contraseña falta o está en blanco en la ruta de exportación o en la de eliminación ENTONCES el sistema DEBE
responder 400 con un error en el campo `contrasena` y el mensaje *Ingresa tu contraseña para confirmar.*, sin exportar
ni cambiar la cuenta.

## R16
(S) SI la contraseña no es la de la cuenta de la sesión ENTONCES el sistema DEBE responder 400 con un error en el campo
`contrasena` y el mensaje *La contraseña no es correcta.*, sin exportar ni cambiar la cuenta.

## R17
(R) CUANDO la contraseña de la ruta de exportación o de la de eliminación es incorrecta, el sistema DEBE registrar en la
auditoría `PERFIL_CONTRASENA_FALLIDA` con `detalle = "operacion=EXPORTACION"` u `"operacion=ELIMINACION"`.

## R18
(S) SI la cuenta ya acumuló 5 fallos en los últimos 15 minutos en el cupo de contraseña ENTONCES el sistema DEBE
responder 429 en la ruta de exportación y en la de eliminación sin comprobar la contraseña.

## R19
SI el rol de la sesión es `ADMIN` ENTONCES el sistema DEBE responder 403 en la ruta de exportación y en la de
eliminación sin comprobar la contraseña ni leer datos.

## R20
SI la ruta de eliminación recibe una contraseña incorrecta y la cuenta tiene impedimentos ENTONCES el sistema DEBE
responder solo el error de `contrasena` (R16), sin informar los impedimentos.

## Eliminación sin impedimentos (CA2, RF-A-24, RNF-CUM-04)

## R21
CUANDO un titular sin impedimentos envía la ruta de eliminación con su contraseña correcta, el sistema DEBE dejar
`usuario.estado = 'ELIMINADA'` y responder 200 con el mensaje *Tu cuenta fue eliminada.*

## R22
CUANDO se elimina una cuenta, el sistema DEBE reemplazar `usuario.correo` por el marcador de correo y dejar en nulo
`nombre`, `telefono`, `correo_pendiente`, `foto_ruta` y `foto_tipo_mime`.

## R23
CUANDO se elimina una cuenta, el sistema DEBE reemplazar `usuario.hash_contrasena` por el hash bcrypt de un secreto
aleatorio que no se guarda ni se devuelve.

## R24
CUANDO se elimina una cuenta con foto de perfil, el sistema DEBE borrar el archivo de la foto de `ArchivosPrivados`
después del commit.

## R25
CUANDO se elimina una cuenta `ARRENDADOR`, el sistema DEBE reemplazar en `perfil_arrendador` `numero_documento`,
`razon_social` y `direccion_fiscal` por el texto fijo de anonimización (cifrado como siempre), `hash_documento` por
`eliminada-<id>` y `motivo_rechazo` por nulo.

## R26
CUANDO se elimina una cuenta `ARRENDADOR`, el sistema DEBE borrar sus filas de `documento_identidad` y, después del
commit, sus archivos de `ArchivosPrivados`.

## R27
CUANDO se elimina una cuenta `ARRENDADOR`, el sistema DEBE borrar su fila de `cuenta_bancaria`.

## R28
CUANDO se elimina una cuenta, el sistema DEBE revocar todas sus sesiones, incluida la de la solicitud.

## R29
CUANDO se elimina una cuenta, el sistema DEBE anular sus tokens pendientes de tipo `VERIFICACION`, `RECUPERACION` y
`CAMBIO_CORREO`.

## R30
CUANDO se elimina una cuenta, el sistema DEBE borrar sus filas de `mfa`, `codigo_respaldo` y `desafio_mfa`.

## R31
CUANDO se elimina una cuenta, el sistema DEBE conservar sin cambios los registros contables de la cuenta (reservas,
bloques, pagos, reembolsos, enlaces de pago, liquidaciones y sus líneas) y sus filas de auditoría.

## R32
SI tras eliminar una cuenta alguien intenta iniciar sesión con el correo y la contraseña anteriores ENTONCES el sistema
DEBE responder el 401 genérico de credenciales inválidas.

## R33
CUANDO una cuenta fue eliminada, el sistema DEBE aceptar un registro nuevo (`POST /api/usuarios` o
`POST /api/arrendadores`) con el correo anterior y, para el arrendador, con el documento anterior.

## R34
(R) CUANDO se elimina una cuenta, el sistema DEBE registrar en la auditoría `CUENTA_ELIMINADA` con el id del titular, la
IP, `entidad = usuario`, `entidad_id` = id del titular y `detalle = "rol=<ROL>"`, en la misma transacción.

## R35
SI falla cualquier paso de la eliminación antes del commit ENTONCES el sistema DEBE revertirla entera: la cuenta sigue
`ACTIVA`, con sus datos, sus sesiones y sin la fila `CUENTA_ELIMINADA`.

## Impedimentos (CA3, RF-A-25, RN-17)

## R36
SI el titular `USUARIO` tiene alguna reserva futura ENTONCES el sistema DEBE responder 400 a la ruta de eliminación con
un error en el campo `reservas` y el mensaje *MENSAJE_RESERVAS_USUARIO*, sin cambiar la cuenta.

## R37
SI el titular `ARRENDADOR` tiene alguna reserva por terminar en sus espacios ENTONCES el sistema DEBE responder 400 a la
ruta de eliminación con un error en el campo `reservas` y el mensaje *MENSAJE_RESERVAS_ARRENDADOR*, sin cambiar la
cuenta.

## R38
SI el titular `ARRENDADOR` tiene alguna liquidación pendiente ENTONCES el sistema DEBE responder 400 a la ruta de
eliminación con un error en el campo `liquidaciones` y el mensaje *MENSAJE_LIQUIDACIONES*, sin cambiar la cuenta.

## R39
SI el titular `ARRENDADOR` tiene algún cobro por liquidar ENTONCES el sistema DEBE responder 400 a la ruta de
eliminación con un error en el campo `pagos` y el mensaje *MENSAJE_COBROS_POR_LIQUIDAR*, sin cambiar la cuenta.

## R40
SI el titular tiene varios impedimentos a la vez ENTONCES el sistema DEBE devolverlos todos en la misma respuesta 400,
un error por campo, en el orden `reservas`, `liquidaciones`, `pagos`.

## R41
MIENTRAS una reserva del usuario está `EXPIRADA`, `CANCELADA` o `CONFIRMADA` ya terminada, el sistema NO DEBE contarla
como impedimento.

## Concurrencia e integridad

## R42
CUANDO el sistema evalúa los impedimentos de la eliminación, DEBE hacerlo después de bloquear la fila de `usuario` del
titular y en la misma transacción que la anonimización.

## R43
CUANDO el titular es `ARRENDADOR`, el sistema DEBE leer las reservas de cada uno de sus espacios después de tomar el
bloqueo de ese espacio (`DisponibilidadService.bloquearEspacio`) y en la misma transacción de la eliminación.

## R44
CUANDO el titular es `ARRENDADOR`, el sistema DEBE leer sus liquidaciones pendientes y sus cobros por liquidar después
de tomar el bloqueo de generación de liquidaciones (`LiquidacionRepository.bloquearGeneracion`) en la misma transacción
de la eliminación.

## R45
El sistema DEBE pedir la exportación, los impedimentos y la anonimización a **todos** los
`ParticipanteDatosPersonales` registrados, sin que `cuentas` conozca su módulo.

## Trazabilidad con la historia

| Origen (docs/historias/HU-22.md)                                                       | Cubierto por |
|----------------------------------------------------------------------------------------|--------------|
| CA1: con contraseña, descargo un JSON con mi perfil, reservas y pagos                   | R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R45 |
| CA2: sin reservas futuras ni liquidaciones pendientes, la cuenta se desactiva           | R21, R28, R32, R35 |
| CA2: mis datos personales se anonimizan                                                 | R22, R23, R24, R25, R26, R27, R29, R30, R33, R45 |
| CA2: los registros contables se conservan                                               | R31 |
| CA3: con reservas futuras o liquidaciones pendientes, se me indica qué resolver         | R36, R37, R38, R39, R40, R41, R42, R43, R44 |
| (S) Reautenticación antes de exportar o eliminar                                        | R15, R16, R18, R19, R20 |
| (I) La descarga solo incluye datos del usuario de la sesión                             | R12, R13 |
| (R) Registro de cada exportación y eliminación                                          | R14, R17, R34 |
| RF-A-23 / RF-A-24 / RF-A-25                                                             | R1–R11 / R21–R35 / R36–R41 |
| RN-17                                                                                   | R36, R37, R38, R39 |
| RNF-SEG-03                                                                              | R15, R16, R18 |
| RNF-SEG-12                                                                              | R14, R17, R34 |
| RNF-CUM-04                                                                              | R1–R10, R21–R31 |

## Supuestos / preguntas abiertas

Ninguno bloquea la redacción; el dueño los confirma o corrige al aprobar el spec.

1. **Plazo legal de conservación (§9 punto 6, sigue abierto).** Esta historia **no borra** ningún registro contable ni
   programa una purga: los conserva sin fecha de fin (R31), lo que cumple cualquier plazo mínimo. Fijar el plazo y
   purgar al vencer queda para cuando el PO lo defina (otra historia o un `sdd: false`). **Pregunta para el dueño:**
   ¿confirmas que basta con conservarlos indefinidamente por ahora?
2. **Qué conserva un registro contable.** Se conservan las filas con sus montos, fechas e ids; la razón social y el
   documento del arrendador **sí** se anonimizan (R25). Las liquidaciones `PAGADA` ya guardan banco y cuenta enmascarada.
   Si el PO decide que la razón social o el RUC deben quedar para fines tributarios, cambia R25.
3. **Cobros por liquidar bloquean (R39).** `docs/architecture.md` (§ Liquidación) deja a este spec decidirlo: se
   bloquea porque es dinero que la plataforma debe al arrendador y la eliminación borra la cuenta bancaria a la que se
   le pagaría.
4. **Reembolsos `PENDIENTE`/`FALLIDO` y pagos `PAGADO_TARDIO`/`MONTO_DISTINTO` del usuario no bloquean.** RN-17 no los
   menciona y el reintento del admin (HU-16) y la devolución manual no necesitan datos personales de la cuenta.
5. **El correo y el documento quedan libres (R33).** Supresión: la misma persona puede volver a registrarse como una
   cuenta nueva, sin relación con la eliminada.
6. **No se envía correo de confirmación** de la eliminación ni de la exportación: la historia no lo pide y el correo se
   anonimiza en la misma operación.
7. **La auditoría no se exporta ni se anonimiza** (STRIDE R): conserva `usuario_id`, IP y fecha. La IP del
   consentimiento (HU-01) solo vive ahí y no entra en el JSON.
8. **Los espacios del arrendador eliminado conservan su estado** y quedan ocultos por la regla de HU-09 D1 (cuenta no
   `ACTIVA`); sus fotos públicas siguen en el almacenamiento. Pasarlos a `BAJA` exigiría un participante en `espacios`
   y no lo pide la historia.
9. **Sin límite propio de exportaciones.** RNF-SEG-17 no fija uno; solo aplica el cupo de fallos de contraseña (R18).
10. **Escritura concurrente tras la eliminación (riesgo aceptado, 2026-11-05).** Una petición validada antes de la
    eliminación puede escribir sobre la cuenta ya `ELIMINADA`; Kevin Chan acepta el riesgo sin cambiar requisitos. Ver
    `design.md` § 15.1.
