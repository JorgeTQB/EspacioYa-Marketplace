# HU-15 — Tasks: Cancelación de reservas

> Orden de ejecución. El implementer marca `[x]` al completar cada una. Firmas, flujos, mensajes y plan de pruebas
> están en `design.md`. Cada test lleva `@DisplayName("HU-15 R<n>: …")`. Antes de empezar, confirmar que el dueño
> (Kevin Chan) aceptó las decisiones por defecto **D1–D12** (`requirements.md`) y acordar con Ariel Enriquez (HU-16)
> el contrato de `PagoService.reembolsarCancelacion` (design § 11.1) y el número de migración (`V4_2` para HU-15).
> Reglas: **ningún test existente cambia ni se borra**, salvo los 6 dobles de `NotificadorCorreo` que solo ganan dos
> métodos (DT-9); en `ReservaTest` y `NotificadorCorreoSimuladoTest` solo se **agregan** casos. HU-15 **no** llama a la
> pasarela, **no** cambia `pago.estado` y **no** crea la tabla `reembolso` (D7). Las unitarias e IT de las demás
> historias siguen verdes en cada paso.

## Backend — base de datos y dominio

- [x] T1 — Migración `V4_2__cancelacion_reserva.sql` (design § 9: `cancelada_por`, `cancelada_en`, `monto_reembolso`,
  `ck_reserva_cancelada_por`, `ck_reserva_cancelacion` solo exigible con datos de cancelación). Cubre: R20, R39. Test:
  `CancelacionReservaControllerIT` (T12, casos de R39), y `ReservaControllerIT` / `MisReservasControllerIT` **sin
  cambios** y en verde (sus `CANCELADA` sin datos siguen siendo válidas).
- [x] T2 — `reservas/domain`: `QuienCancela` (`rolRequerido`) y `MotivoNoCancelable`; `Reserva` (M: tres campos
  mapeados, `motivoNoCancelable(inicio, ahora)` en el orden `YA_CANCELADA`, `NO_CONFIRMADA`, `YA_INICIADA`,
  `cancelar(quien, inicio, montoReembolso, ahora)` con `IllegalStateException`, getters); `ReservaConHorario` (M:
  `instanteDeInicio()` con `ZonaHoraria.LIMA`). Cubre: R8, R9, R10, R14, R19, R20. Test: `ReservaTest` (M, casos
  agregados: cada motivo, inicio = *ahora*, vencida sin procesar → `NO_CONFIRMADA`, `cancelar` deja los tres campos y
  lanza si hay motivo), `ReservaConHorarioTest` (N: instante en Lima, `horaInicio = 0`).
- [x] T3 — ★ Strategy en `reservas/domain`: `SolicitudCancelacion` (`anticipacion`), `PoliticaReembolso` (interfaz con
  `ANTICIPACION_MINIMA` y `monto` por defecto, javadoc del patrón), `ReembolsoUsuarioConAnticipacion`,
  `ReembolsoUsuarioSinAnticipacion`, `ReembolsoCancelaArrendador`, `ReembolsoCalculado` y `CalculoReembolso`
  (`politicaFija()`, `calcular` exige exactamente una). Sin Spring. Cubre: R11, R12, R13, R14, R15, R16. Test:
  `CalculoReembolsoTest` (N: 48 h exactas → 100; 48 h − 1 s → 0; arrendador con 1 h y 10 días → 100; montos con escala
  2; rejilla de anticipaciones × `QuienCancela` → exactamente una aplica; lista vacía o con dos que aplican →
  `IllegalStateException`).

## Backend — `pagos` (punto de extensión del reembolso, D7)

- [x] T4 — `PagoRepository` (M: `findByReservaIdAndEstado`), `pagos/service/PagoService` (N, interfaz con el javadoc del
  contrato de design § 11.1), `pagos/service/ReembolsoPorCancelacion` (N) y `pagos/service/impl/PagoServiceImpl` (N,
  `MANDATORY`; pago `PAGADO` o `IllegalStateException`; `0 < monto ≤ pago.monto` o `IllegalArgumentException`;
  auditoría `REEMBOLSO_SOLICITADO` sobre el pago con `origen=CANCELACION;reservaId=…;monto=…`; sin tocar `pago.estado`
  ni `PasarelaPago`). Cubre: R24, R25. Test: `PagoServiceImplTest` (N, Mockito: audita con entidad `pago` y el detalle
  esperado; sin `PAGADO` → `IllegalStateException`; monto 0 y mayor que el pago → `IllegalArgumentException`; no
  interactúa con `PasarelaPago`).

## Backend — `cuentas` (correos, DT-9)

- [x] T5 — `cuentas/service`: records `AvisoCancelacionUsuario` y `AvisoCancelacionArrendador` (N, el del arrendador sin
  datos del usuario ni montos); `NotificadorCorreo` (M: `notificarCancelacionAlUsuario`,
  `notificarCancelacionAlArrendador`); `NotificadorCorreoSimulado` (M: una línea INFO por método, sin correo ni datos).
  Agregar los dos métodos vacíos a los 6 dobles (`PagosSoporte.NotificadorFalso`, `UsuarioControllerIT`,
  `RecuperacionContrasenaControllerIT`, `ArrendadorControllerIT`, `RevisionIdentidadControllerIT`,
  `CuentaAdminControllerIT`), sin cambiar ningún caso. Cubre: R26, R27. Test: `NotificadorCorreoSimuladoTest` (M, casos
  agregados con `OutputCaptureExtension`: la línea no contiene el correo).

## Backend — servicio y controladores de `reservas`

- [x] T6 — `ConversionReservas` (M: `idReserva(String) → Optional<Long>`, DT-10) y `ReservaServiceImpl` (M: `consultar`
  la usa; se borra `idValido`). Cubre: R4. Test: `ConversionReservasTest` (N: `1`, 19 dígitos válidos → presente;
  `abc`, `0`, `-1`, `1.5`, `""`, `null`, 20 dígitos, `9223372036854775808` → vacío); `ReservaServiceImplTest` y
  `ReservaControllerIT` **sin cambios** y en verde.
- [x] T7 — `reservas/dto`: `VistaCancelacionDTO`, `ConfirmarCancelacionDTO`, `CancelacionDTO` (design § 5). Cubre: R17,
  R19, R31. Test: claves exactas en `CancelacionReservaControllerIT` (T12).
- [x] T8 — `reservas/service/AvisosCancelacion` (N, copia de `AvisosPago`: `CuentaService.correoDe` del usuario y del
  arrendador dentro de la transacción; un `DespuesDelCommit.ejecutar` por aviso). Cubre: R26, R27, R28, R29. Test:
  `AvisosCancelacionTest` (N: con `TransactionSynchronizationManager` inicializado, dos sincronizaciones; al ejecutarlas,
  los dos avisos con los datos esperados; si el primero lanza, el segundo sale; el aviso al arrendador no lleva
  montos).
- [x] T9 — `reservas/service/CancelacionReservaService` (N: constantes de design § 5; `vistaPrevia` `readOnly` sin
  bloqueos; `confirmar` `@Transactional` con el orden de design § 7: rol → id → monto visto → `findEspacioIdById` →
  `bloquearEspacio` → `findParaActualizar` → propiedad → *ahora* → motivo → `calculo.calcular` → comparación →
  `cancelar` → `desactivarDeReserva` → `reembolsarCancelacion` si monto > 0 → auditoría `RESERVA_CANCELADA` →
  `avisos.programar`; sin `if` sobre quién cancela ni sobre las 48 h fuera del Strategy). Cubre: R2, R3, R4, R5, R6, R7,
  R8, R9, R10, R17, R18, R19, R20, R21, R22, R23, R30, R31, R32, R33, R35, R36. Test: `CancelacionReservaServiceTest`
  (N, Mockito, `Clock` fijo: 403 sin repositorios para cada rol ajeno en cada papel; id mal escrito → 404 sin
  repositorios; ajena por usuario y por arrendador → 404 sin `cancelar` ni auditoría; `InOrder` de R33; 100 % →
  `reembolsarCancelacion(id, monto, sesión, ip)`; 0 % → nunca; monto visto distinto → 400 sin `cancelar`,
  `desactivarDeReserva`, pagos, auditoría ni avisos; detalle de auditoría `canceladaPor=…;espacioId=…;politica=…;
  porcentaje=…;monto=…`; la vista previa no llama a `bloquearEspacio`, `desactivarDeReserva`, pagos ni auditoría).
- [x] T10 — Controladores (M): `ReservaController` (`GET` y `POST /{id}/cancelacion` con `QuienCancela.USUARIO`) y
  `ReservasDeMisEspaciosController` (`GET` y `POST /{id}/cancelacion` con `QuienCancela.ARRENDADOR`); `@RequestBody(required
  = false)` sin `@Valid`; IP con `IpCliente.de`. Cubre: R1, R2, R3, R4, R7, R17, R19. Test:
  `CancelacionReservaControllerIT` (T12).

## Backend — pruebas de integración

- [x] T11 — Soporte de la IT: en `CancelacionReservaControllerIT`, `@TestConfiguration` anidado con un `NotificadorCorreo`
  falso `@Primary` (guarda los dos avisos de cancelación y puede fallar uno), IP `10.17.x.x` por test, helper
  `filaPagoPagado(reserva, monto)` con `JdbcTemplate` y `@AfterEach` propio que borra `pago` antes del `limpiar()` de
  `ReservasControllerSoporte` (que no se toca). Cubre: R26–R29 (infraestructura). Test: T12.
- [x] T12 — `CancelacionReservaControllerIT` (N, extiende `ReservasControllerSoporte`) con todos los casos de design § 13:
  R1–R10, R17–R21, R24–R32, R35–R39; las 48 h exactas con `RelojControlable` (reserva 2026-10-30 10:00 Lima → 100 %;
  +1 s → 0 %); `CONFIRMADA` sin pago → 500 sin cambios (R25); tras cancelar, **otro usuario reserva las mismas horas →
  201** (R21); `GET /api/reservas/{id}` → `CANCELADA`, en pasadas del usuario y fuera de la lista del arrendador (R37,
  R38); restricciones de R39 con `JdbcTemplate`. Incluye el **caso de punta a punta** por la API (reservar → pagar →
  `pago/simulacion` `APROBADO` → vista previa → confirmar → volver a reservar). Cubre en IT: R1–R32, R35–R39.
- [x] T13 — `CancelacionConcurrenciaIT` (N, estilo `PagoConcurrenciaIT`): usuario y arrendador confirman la misma
  reserva a la vez → un 200 y un 400 *ya cancelada*; una sola fila `RESERVA_CANCELADA` y una `REEMBOLSO_SOLICITADO`.
  Cubre: R33, R34.

## Frontend

- [x] T14 — `components/ui/Modal.jsx` (M: prop `textoCerrar`, por defecto `"Cancelar"`; HU-06 y HU-09 no cambian).
  Cubre: modal de confirmación (CA2).
- [x] T15 — `src/api/reservas.js` (M: `obtenerCancelacion`, `cancelarReserva`, `obtenerCancelacionDeMiEspacio`,
  `cancelarReservaDeMiEspacio`, ids con `encodeURIComponent`) y `pages/reservas/textosReservas.js` (M: bloque HU-15
  con botón, título y botones del modal, las tres líneas por `politica`, mensajes de éxito). Cubre: vista previa y
  confirmación (CA2, CA3).
- [x] T16 — `pages/reservas/CancelarReserva.jsx` (N, + `.module.css`): botón `peligro` "Cancelar reserva" → vista
  previa → `Modal` `peligro-confirmar` con espacio, fecha, horario, monto pagado y la línea del reembolso con el monto
  de la API; confirmar envía `montoReembolsoVisto` = `montoReembolso` de la vista previa; *monto cambió* → `Alerta
  atencion` en el modal y nueva vista previa; otros errores → `Alerta error`. Cubre: CA2 (monto antes de confirmar),
  CA3 (confirmación), errores de R8–R10 y R32 visibles.
- [x] T17 — `pages/reservas/MisReservas.jsx` (M: `CancelarReserva` en `acciones` de las `CONFIRMADA` de "Próximas";
  contador `recarga` como `key` de las secciones; `Alerta exito`) y `pages/reservas/ReservasDeMisEspacios.jsx` (M:
  igual con las funciones del arrendador y `paraArrendador`; se quita el comentario "HU-15 agregará 'Cancelar'").
  Cubre: CA2 "desde Mis reservas", CA3 (la reserva pasa a "Cancelada" en Pasadas del usuario y desaparece de la lista
  del arrendador, D9, D10).

## Docs y cierre

- [x] T18 — Docs del mismo PR: `docs/architecture.md` ("Pagos: qué deja HU-13": `PagoService.reembolsarCancelacion` con
  el contrato de design § 11.1 en lugar de "HU-15 crea `PagoService`"; sección "Reservas" con lo que deja HU-15:
  Strategy `PoliticaReembolso` + `CalculoReembolso`, orden `espacio → reserva` al cancelar, `CANCELADA` con sus datos),
  `docs/modelo-datos.md` (columnas de `reserva` y las dos restricciones; quitar "los agrega HU-15 con su migración"),
  `docs/frontend.md` (prop `textoCerrar` de `Modal`; `Tarjeta` con "Cancelar reserva" en `acciones`). Sin cambios en
  la tabla de puertos. Cubre: DT-2, DT-7, DT-8, DT-9.
- [x] T19 — Avisos en `progress/HU-15.md`: a Ariel Enriquez (§ 11.1, contrato de `PagoService` y número de migración
  de HU-16), a Jorge Quispe (§ 11.2, dos métodos nuevos en `NotificadorCorreo` para HU-25) y a Sebastian Garate (§ 11.4,
  HU-21 puede reutilizar la cancelación del arrendador). Cubre: D7, DT-7, DT-9.
- [x] T20 — Verificación: `./init.sh` en verde (unitarias, IT con Docker, `npm --prefix 3_frontend run lint` y `build`,
  revisión de colores y secretos) y `progress/impl_HU-15.md` con el mapa R → test (`@DisplayName`), la confirmación de
  que ningún test existente cambió salvo los 6 dobles y los casos agregados, y la guía de la caja negra de UAT de
  design § 13 (100 %, 0 %, arrendador y correos en el log). Cubre: R1–R39.
