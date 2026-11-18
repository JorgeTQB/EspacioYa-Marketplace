# HU-16 — Tasks: Reembolso automático

> Orden de ejecución. El implementer marca `[x]` al completar cada una. Firmas, flujos, mensajes y plan de pruebas
> están en `design.md`. Cada test lleva `@DisplayName("HU-16 R<n>: …")`. Antes de empezar, confirmar que el dueño
> (Ariel Enriquez) aceptó las decisiones por defecto **D1–D13** (`requirements.md`) y que `develop` no tiene ya una
> `V4_3` (si la tiene, usar la siguiente `V4_n` libre).
> Reglas: los únicos tests existentes que cambian son (a) `CancelacionReservaControllerIT.solicitaElReembolso` (pasa a
> esperar el pago `REEMBOLSADO` y una fila de `reembolso`), (b) `PagoServiceImplTest` (constructor nuevo y sincronización
> inicializada; sus casos de HU-15 se conservan) y (c) las limpiezas que borran `pago`, que ganan `DELETE FROM reembolso`
> antes. En `ReglasPagoTest`, `MercadoPagoAdapterTest` y `PasarelaSimuladaTest` solo se **agregan** casos. Ninguna
> llamada a la pasarela dentro de una transacción que retenga filas (DT-5).

## Backend — base de datos y dominio

- [x] T1 — Migración `V4_3__reembolso.sql` (design § 9: tabla `reembolso`, `uq_reembolso_pago_origen`, `CHECK` de origen,
  estado, monto, intentos, `ck_reembolso_confirmado`, `ck_reembolso_intentado`, índice parcial `ix_reembolso_por_atender`;
  FK a `pago` sin cascada). Cubre: R19, R42. Test: `ReembolsoRestriccionesIT` (N, `JdbcTemplate`, extiende
  `PagosSoporte`: cada fila inválida → `DataIntegrityViolationException`; las válidas se aceptan).
- [x] T2 — Limpiezas de tests: `DELETE FROM reembolso` antes de cada `DELETE FROM pago` en `PagosSoporte`,
  `CancelacionReservaControllerIT`, `CancelacionConcurrenciaIT` (los dos lugares), `MisReservasControllerIT` y
  `PagoConcurrenciaIT`, sin cambiar ningún caso. Cubre: — (infraestructura de R1–R42). Test: las IT existentes siguen en
  verde.
- [x] T3 — `pagos/domain`: `EstadoReembolso`, `OrigenReembolso`, `MotivoNoReintentable`, entidad `Reembolso`
  (`MARGEN_SIN_RESPUESTA`, `porCancelacion`, `motivoNoReintentable`, `iniciarIntento`, `marcarReembolsado`,
  `marcarFallido`, `claveIdempotencia`, getters; constructor protegido, sin setters de estado), record
  `ReembolsoPorAtender`; `Pago` (M: `marcarReembolsado`, `marcarReembolsoFallido`). Cubre: R1, R10, R11, R17, R18, R21,
  R22, R24. Test: `ReembolsoTest` (N: valores iniciales; motivos con `REEMBOLSADO`, `PENDIENTE` recién creado, a 1 min
  59 s y a 2 min exactos, `FALLIDO`; `iniciarIntento` suma y fija la hora o lanza; `marcar*` desde otro estado lanza;
  clave `espacioya-reembolso-<id>`), `PagoTest` (N: `PAGADO`/`REEMBOLSO_FALLIDO` → `REEMBOLSADO` y → `REEMBOLSO_FALLIDO`;
  desde `RECHAZADO`, `PAGADO_TARDIO`, `MONTO_DISTINTO`, `REEMBOLSADO` → `IllegalStateException`).
- [x] T4 — `ReglasPago` (M: `idBienEscrito`; `idDeReserva` delega sin cambiar su resultado). Cubre: R32. Test:
  `ReglasPagoTest` (M, casos agregados: `1`, 19 dígitos → valor; `abc`, `0`, `-1`, `1.5`, `""`, `null`, 20 dígitos,
  `9223372036854775808` → `null`; los casos de `idDeReserva` siguen verdes).
- [x] T5 — `ReembolsoRepository` (N: `findParaActualizar` con `PESSIMISTIC_WRITE`, `existsByPagoIdAndOrigen`,
  `porAtender(limite)` con la proyección y el orden de design § 6). Cubre: R20, R22, R28, R29. Test: `RegistroReembolsosIT`
  (T9) y `ReembolsoAdminControllerIT` (T13).

## Backend — ★ Adapter (`PasarelaPago.reembolsar`)

- [x] T6 — `pagos/service/pasarela`: records `SolicitudReembolsoPasarela` y `ReembolsoRealizado` (N); `PasarelaPago` (M:
  `reembolsar` con javadoc del contrato); `MercadoPagoAdapter` (M: `POST /v1/payments/{id}/refunds`, cuerpo `amount`,
  `X-Idempotency-Key`, `approved` + `id` o `ServicioExternoException`, reutilizando `llamar` y `texto`; `log.warn` sin
  cuerpo). `StubMercadoPago` (M, test: ruta de reembolsos configurable con estado y cuerpo; `Solicitud` gana el encabezado
  de idempotencia). Cubre: R12, R13, R41. Test: `MercadoPagoAdapterTest` (M, casos agregados de design § 13: ruta, cuerpo,
  llave, clave; `approved` → id; 400, 404, 500, demora mayor que el tiempo de espera, sin `id`, `rejected`, `in_process`
  → excepción; con `OutputCaptureExtension` el cuerpo del stub no aparece).
- [x] T7 — `PasarelaSimulada` (M: `fallos-reembolso` por constructor, contador por clave, `reembolsar` de DT-11);
  `application.properties` (M: `espacioya.pagos.simulada.fallos-reembolso=${PAGOS_SIMULADA_FALLOS_REEMBOLSO:0}`);
  `2_Backend/.env.example` (M: `# PAGOS_SIMULADA_FALLOS_REEMBOLSO=0` con su comentario). Cubre: R14, R15, R43. Test:
  `PasarelaSimuladaTest` (M, casos agregados: `sim-…` aprueba con `sim-reembolso-…`; con 1 la primera llamada de una
  clave falla y la segunda aprueba, otra clave empieza de nuevo; id sin `sim-` falla con 0 y con 1);
  `SeleccionPasarelaTest` sin cambios y en verde.

## Backend — ★ Command

- [x] T8 — `pagos/service/reembolso`: `Disparo`, `ResultadoReembolso`, `IntentoReembolso`, `ComandoReembolso`
  (interfaz con javadoc del patrón), `SolicitudReembolso` (constructor de paquete, `ejecutar()` de design § 7),
  `SolicitudesReembolso` (fábrica) y `EjecutorReembolsos` (`ejecutarDespuesDelCommit` con `DespuesDelCommit`;
  `ejecutarAhora` lanza si hay transacción activa). Cubre: R3, R4, R5, R8, R9, R16, R17, R21, R22, R24, R40. Test:
  `SolicitudReembolsoTest` (N, Mockito: `InOrder` `iniciar` → `reembolsar` → `registrarExito`; excepción →
  `registrarFallo` y `FALLIDO`; `iniciar` vacío → `NO_EJECUTADO` sin pasarela; solicitud con transacción, monto y clave
  del intento; dos ejecuciones → misma clave), `EjecutorReembolsosTest` (N: sin `afterCommit` no corre; con commit corre
  una vez; con rollback no corre; `ejecutarAhora` con transacción activa → `IllegalStateException`).
- [x] T9 — `RegistroReembolsos` (N: `TransactionTemplate` `REQUIRES_NEW` desde `PlatformTransactionManager`; `iniciar`,
  `registrarExito`, `registrarFallo`; bloqueo `reembolso` → `pago`; auditoría `REEMBOLSO_REALIZADO`/`REEMBOLSO_FALLIDO`
  con el detalle de DT-10 y el `usuario_id`/IP del comando). Cubre: R10, R11, R17, R18, R21, R22, R36, R37, R38. Test:
  `RegistroReembolsosIT` (N, extiende `PagosSoporte`: `iniciar` confirma intentos y hora aunque el llamador revierta su
  propia transacción; `iniciar` sobre `REEMBOLSADO` o en proceso → vacío sin cambios; `registrarExito` y `registrarFallo`
  dejan reembolso, pago y auditoría con los valores y detalles exactos; ningún detalle contiene texto del proveedor).

## Backend — Clients y API

- [x] T10 — `PagoServiceImpl` (M: constructor nuevo; cuerpo de design § 7 con `existsByPagoIdAndOrigen`, `saveAndFlush`,
  `REEMBOLSO_SOLICITADO` sin cambios y `ejecutarDespuesDelCommit(crear(…, AUTOMATICO, …))`; `MANDATORY` y firma
  iguales); `PagoService` (M: javadoc actualizado). Cubre: R1, R2, R3, R4, R6, R20, R40. Test: `PagoServiceImplTest` (M:
  casos de HU-15 conservados con el constructor nuevo y `TransactionSynchronizationManager` inicializado; nuevos: guarda
  el reembolso `PENDIENTE`; ya existe → `IllegalStateException` sin `save` ni comando; entrega un comando `AUTOMATICO` con
  `solicitadoPor` e IP al Invoker y no lo ejecuta en el acto).
- [x] T11 — `pagos/dto/ReembolsoAdminDTO` (N) y `pagos/service/ReembolsoAdminService` (N: constantes de design § 5;
  `porAtender` `readOnly` con rol `ADMIN`; `reintentar` sin transacción con el orden rol → id → existe → motivo →
  `ejecutarAhora` → `NO_EJECUTADO` = 400 → DTO releído). Cubre: R26, R28, R29, R30, R31, R32, R33, R34, R35, R39, R40.
  Test: `ReembolsoAdminServiceTest` (N, Mockito, `Clock` fijo: 403 sin repositorios; id mal escrito o inexistente → 404
  sin comando; `REEMBOLSADO` → 400 *ya reembolsado* y `PENDIENTE` reciente → 400 *en proceso* sin comando; comando
  `ADMIN` con el id e IP del admin; `NO_EJECUTADO` → 400 *en proceso*; `porAtender` usa el límite ahora − 2 min).
- [x] T12 — `pagos/controller/ReembolsoAdminController` (N: `GET /api/admin/reembolsos` y `POST /{id}/reintento`, `{id}`
  como `String`, sin cuerpo, `IpCliente.de`, `Cache-Control: no-store`, 200). Cubre: R25, R26, R27, R28, R30, R35. Test:
  `ReembolsoAdminControllerIT` (T13).

## Backend — pruebas de integración

- [x] T13 — `ReembolsoAdminControllerIT` (N, extiende `PagosSoporte`, IP `10.18.x.x`, `fallos-reembolso=1`, admin con
  `crearAdmin` y token desde `IP_ADMIN`): CA2 de punta a punta por la API (reservar → pagar `APROBADO` → cancelar → 200
  con el `CancelacionDTO` de HU-15 → reembolso `FALLIDO`, pago `REEMBOLSO_FALLIDO`, `REEMBOLSO_FALLIDO` en auditoría →
  aparece en `GET` → reintento → 200 `REEMBOLSADO`, `intentos = 2`, misma clave, auditoría con el admin → ya no aparece);
  y los casos de R16 (spy con `FOR UPDATE NOWAIT` desde otro hilo), R25–R35 y R39 de design § 13 (401, 403 por rol y por
  IP, claves exactas, filtro y orden del listado con filas sembradas, 404, 400, cuerpo ignorado, reintento que vuelve a
  fallar con spy). Cubre: R7, R16, R17, R18, R24, R25, R26, R27, R28, R29, R30, R31, R32, R33, R34, R35, R36, R37, R38,
  R39.
- [x] T14 — `ReembolsoAutomaticoIT` (N, extiende `PagosSoporte`, `fallos-reembolso=0`): CA1 de punta a punta (cancelar
  una reserva pagada con ≥ 48 h → reembolso `REEMBOLSADO` con `id_reembolso_pasarela` `sim-reembolso-…`, pago
  `REEMBOLSADO`, `intentos = 1`, `REEMBOLSO_SOLICITADO` y `REEMBOLSO_REALIZADO` con quien canceló y su IP); spy de
  `PasarelaPago` con `ArgumentCaptor` (transacción del pago original, 161.00, clave); cancelación rechazada (monto visto
  distinto → 400) sin reembolso ni llamada; 0 % sin reembolso. Cubre: R1, R2, R3, R4, R5, R8, R9, R10, R11, R14, R36, R38.
- [x] T15 — `ReembolsoConcurrenciaIT` (N, estilo `PagoConcurrenciaIT`): dos reintentos a la vez de un `FALLIDO` → un 200
  y un 400 *en proceso*; una sola llamada a la pasarela para ese intento; `intentos` sube en 1. Cubre: R22, R23.
- [x] T16 — `ArquitecturaReembolsoTest` (N, reflexión): ninguna clase de `pagos.service.reembolso` ni
  `ReembolsoAdminService` tiene campos o parámetros `RestClient`, `MercadoPagoAdapter` o `PasarelaSimulada`; solo
  `SolicitudReembolso` y `SolicitudesReembolso` reciben `PasarelaPago`. Cubre: R40, R41.
- [x] T17 — Regresión de HU-15: `CancelacionReservaControllerIT.solicitaElReembolso` (M: espera pago `REEMBOLSADO` y una
  fila de `reembolso` `REEMBOLSADO`; `@DisplayName` "HU-15 R22, R24 · HU-16 R1, R11: …"); `CancelacionConcurrenciaIT`
  sigue con un solo `REEMBOLSO_SOLICITADO`; las demás IT de HU-13, HU-14 y HU-15 en verde. Cubre: R2, R7, R20.

## Frontend

- [x] T18 — `components/ui/EtiquetaEstado.jsx` (M: `FALLIDO` → "Reembolso fallido", error; `PENDIENTE` → "Sin respuesta de
  la pasarela", atención, bajo `// Reembolso (HU-16)`). Cubre: CA2 "el admin la ve" (estado visible con texto).
- [x] T19 — `src/api/pagos.js` (M: `listarReembolsosPorAtender`, `reintentarReembolso(id)` con `encodeURIComponent`) y
  `pages/pagos/textosPagos.js` (M: bloque HU-16 con título, descripción, lista vacía, "Reintentar", textos del modal,
  mensajes de éxito y de nuevo fallo, `ORIGENES_REEMBOLSO`). Cubre: CA2 (listado y reintento).
- [x] T20 — `pages/pagos/ReembolsosPorAtender.jsx` (N, + `.module.css`): `Navigate` a `/panel` si no es `ADMIN`; `Tarjeta`
  por reembolso con `EtiquetaEstado`, monto con `formatoPrecio`, origen, intentos y fechas; `Boton secundario`
  "Reintentar" → `Modal primario` con `cargando` → `Alerta exito` / `Alerta error` según design § 12 y recarga de la
  lista. Cubre: CA2 "el admin la ve en su panel" y "puede reintentarla con un botón".
- [x] T21 — `App.jsx` (M: ruta `/admin/reembolsos` con `RutaProtegida`) y `pages/cuentas/Panel.jsx` (M: enlace
  "Reembolsos por atender" para `ADMIN` y descripción). Cubre: CA2 (acceso al panel).

## Docs y cierre

- [x] T22 — Docs del mismo PR (design § 11.3): `docs/architecture.md` (`PasarelaPago.reembolsar`; "Reembolsos: qué deja
  HU-16" con los roles del Command, `REQUIRES_NEW`, margen, clave de idempotencia, rutas admin y la propiedad de la
  simulada; filas `sim-`), `docs/modelo-datos.md` (fila `reembolso` y restricciones con nombre), `docs/frontend.md`
  (fila Reembolso en "Color de cada estado", nota de `EtiquetaEstado`, ruta `/admin/reembolsos`). Sin cambios en la tabla
  de puertos. Cubre: DT-1, DT-2, DT-3, DT-6, DT-7, DT-11.
- [x] T23 — Avisos en `progress/HU-16.md`: a Kevin Chan (§ 11.1: el caso de HU-15 que cambia y las limpiezas) y a Eduardo
  Saldaña (§ 11.2: HU-28 y la variable `PAGOS_SIMULADA_FALLOS_REEMBOLSO` para la caja negra). Cubre: D7, DT-12.
- [x] T24 — Verificación: `./init.sh` en verde (unitarias, IT con Docker, `npm --prefix 3_frontend run lint` y `build`,
  revisión de colores y secretos) y `progress/impl_HU-16.md` con el mapa R → test (`@DisplayName`), la lista de los
  únicos tests existentes que cambiaron (reglas de arriba) y la guía de la caja negra de UAT de design § 13 (con
  `PAGOS_SIMULADA_FALLOS_REEMBOLSO=1` y `0`). Cubre: R1–R43.

## Adenda 2026-11-16 — Escenario de calidad AC005

> Empiezan cuando **Ariel Enriquez** apruebe la adenda (`requirements.md`, "Adenda 2026-11-16"; `design.md` § 15). Solo
> se agrega un método de prueba en `ReembolsoConcurrenciaIT`; no se toca `2_Backend/src/main` ni las pruebas existentes.
> Si la prueba nueva falla por un defecto real, se registra como `DEF-xx` y se avisa al dueño, sin corregir producción.

- [x] T25 — `ReembolsoConcurrenciaIT.diezReintentosALaVez` (N): 10 `POST /api/admin/reembolsos/{id}/reintento` a la vez
  sobre el mismo `FALLIDO`, con el mismo `doAnswer` y latch que `dosReintentosALaVez`; `RONDAS` (5) rondas. Por ronda:
  `verify(pasarela, times(1)).reembolsar(any())`; un 200 y nueve 400; cada 400 con `MENSAJE_EN_PROCESO` o
  `MENSAJE_YA_REEMBOLSADO` (al menos uno *en proceso*); 1 fila de `reembolso` para ese pago y origen; `intentos` = 2;
  estado `REEMBOLSADO`; un `REEMBOLSO_REALIZADO` más por ronda. Cubre: R22, R23 (AC005); R19, R21, R30, R33, R34, R36,
  R39. Test: `ReembolsoConcurrenciaIT`.
- [x] T26 — Verificación: `./2_Backend/mvnw -f 2_Backend/pom.xml verify -Dit.test=ReembolsoConcurrenciaIT -Dtest=none
  -Dsurefire.failIfNoSpecifiedTests=false` en verde **tres veces seguidas** (y `./init.sh` completo en verde al final). En
  una sección nueva de `progress/impl_HU-16.md` (sin sobrescribir lo anterior): método y `@DisplayName`, solicitudes y
  rondas reales, todo ajuste de configuración de prueba con su motivo, y los `DEF-xx` si los hubo. Cubre: R22, R23
  (AC005). *Las tres corridas y el informe están hechos; el `./init.sh` completo final queda pendiente para el
  reviewer (ver `progress/impl_HU-16.md`, "Adenda AC005").*
