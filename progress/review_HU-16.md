# Review — HU-16

**Veredicto:** APPROVED

> Rama `feature/HU-16-reembolso-automatico`, cambios sin commit contra `develop` (d977a45). Revisado el 2026-11-01.
> `./init.sh` completo (sin `--rapido`): **exit 0**. `mvnw verify`: 1027 unitarias + 690 IT, 0 fallos, 0 errores,
> 0 omitidas. ESLint (`--max-warnings=0`) y `npm run build` en verde. Reglas de arquitectura, colores, librerías y
> secretos `[OK]`. Único `[WARN]`: faltan `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA` y `ALMACENAMIENTO_LLAVE_FIRMA` en la
> shell local. Es del entorno, no de HU-16, y solo afecta a `spring-boot:run`.

## Trazabilidad requirements ↔ tests

Cada `R<n>` tiene al menos un test con `@DisplayName("HU-16 R<n>: …")` que comprueba un resultado concreto (estado,
monto, código HTTP, claves del DTO, filas de auditoría con su detalle exacto, número de llamadas a la pasarela).

- R1: [x] `PagoServiceImplTest.guardaElReembolsoPendiente`, `ReembolsoTest` (valores iniciales), `ReembolsoAutomaticoIT.cancelarReembolsa`, `ReembolsoAutomaticoIT.ceroPorCientoSinReembolso`
- R2: [x] `ReembolsoAutomaticoIT.auditaLaSolicitudYElResultado` (detalle exacto de `REEMBOLSO_SOLICITADO`)
- R3: [x] `PagoServiceImplTest.entregaElComandoParaDespuesDelCommit`, `EjecutorReembolsosTest` (×2), `ReembolsoAutomaticoIT.soloTrasElCommit` / `rollbackDelLlamador` / `cancelacionRechazadaNoReembolsa`
- R4: [x] `EjecutorReembolsosTest` (rollback no corre), `ReembolsoAutomaticoIT.rollbackDelLlamador` (0 filas, sin auditoría, pago `PAGADO`, spy `never()`)
- R5: [x] `EjecutorReembolsosTest` (con commit corre), `ReembolsoTest` (primer intento), `RegistroReembolsosIT` (iniciar el primero), `ReembolsoAutomaticoIT.cancelarReembolsa` / `soloTrasElCommit`
- R6: [x] `PagoServiceImplTest.montoNegativo`, `sinPagoPagado`, `montoCero`, `montoMayorQueElPago` (`verifyNoInteractions(reembolsos, solicitudes, ejecutor)`)
- R7: [x] `ReembolsoAdminControllerIT.fallaYElAdminLoReintenta` (200 con `CancelacionDTO`, reserva `CANCELADA` aunque la pasarela falle)
- R8: [x] `SolicitudReembolsoTest` (solicitud con los datos del intento), `ReembolsoAutomaticoIT.reembolsaContraElPagoOriginal` (ArgumentCaptor)
- R9: [x] `SolicitudReembolsoTest`, `ReembolsoAutomaticoIT.reembolsaContraElPagoOriginal`, `ReembolsoAdminControllerIT.ignoraElCuerpo`
- R10: [x] `ReembolsoTest` (×2), `RegistroReembolsosIT` (registrar éxito), `ReembolsoAutomaticoIT.cancelarReembolsa` (`id_reembolso_pasarela` exacto)
- R11: [x] `PagoTest` (×2), `RegistroReembolsosIT`, `ReembolsoAutomaticoIT.cancelarReembolsa`, `CancelacionReservaControllerIT.solicitaElReembolso`
- R12: [x] `MercadoPagoAdapterTest` (×2: ruta, cuerpo, llave, `X-Idempotency-Key`; `approved` + id)
- R13: [x] `MercadoPagoAdapterTest` (×3: error HTTP, tiempo agotado, sin id o `status` distinto; cuerpo fuera del log)
- R14: [x] `PasarelaSimuladaTest.apruebaElReembolsoSimulado`, `ReembolsoAutomaticoIT.cancelarReembolsa`
- R15: [x] `PasarelaSimuladaTest.fallaLasPrimerasLlamadasDeCadaClave`, `fallaExactamenteNLlamadas`
- R16: [x] `ReembolsoAdminControllerIT.sinBloqueosDuranteLaLlamada` (`FOR UPDATE NOWAIT` desde otro hilo sobre reembolso, pago y reserva, en el automático y en el reintento), `SolicitudReembolsoTest` (InOrder), `EjecutorReembolsosTest`
- R17: [x] `PagoTest`, `ReembolsoTest`, `SolicitudReembolsoTest`, `RegistroReembolsosIT` (×2), `ReembolsoAdminControllerIT.fallaYElAdminLoReintenta`
- R18: [x] `ReembolsoTest`, `RegistroReembolsosIT` (×2, confirma aunque el llamador revierta), `ReembolsoAdminControllerIT.fallaYElAdminLoReintenta`
- R19: [x] `ReembolsoRestriccionesIT` (segundo `(pago_id, origen)` → `DataIntegrityViolationException`)
- R20: [x] `PagoServiceImplTest.yaTieneReembolso`
- R21: [x] `ReembolsoTest`, `SolicitudReembolsoTest`, `RegistroReembolsosIT`, `ReembolsoAdminControllerIT.yaReembolsado`
- R22: [x] `ReembolsoTest` (×4, incluidos 1 min 59 s y 2 min exactos), `SolicitudReembolsoTest`, `RegistroReembolsosIT`, `ReembolsoAdminControllerIT.enProceso`, `ReembolsoConcurrenciaIT`
- R23: [x] `ReembolsoConcurrenciaIT.dosReintentosALaVez` (5 rondas: 200 + 400 *en proceso*, `times(1)`, intentos 2), `ReembolsoAdminServiceTest`
- R24: [x] `ReembolsoTest`, `SolicitudReembolsoTest`, `ReembolsoAdminControllerIT.mismaClaveEnElReintento`
- R25: [x] `ReembolsoAdminControllerIT.sinSesion`
- R26: [x] `ReembolsoAdminServiceTest`, `ReembolsoAdminControllerIT.sinRolAdmin` (USUARIO y ARRENDADOR)
- R27: [x] `ReembolsoAdminControllerIT.ipNoPermitida`
- R28: [x] `ReembolsoAdminServiceTest`, `ReembolsoAdminControllerIT.listadoPorAtender` (claves exactas, orden, `no-store`), `listadoVacio`
- R29: [x] `ReembolsoAdminControllerIT.listadoPorAtender` (excluye `REEMBOLSADO` y en proceso; `auditoria`, `reembolso` y `pago` sin cambios)
- R30: [x] `ReembolsoAdminServiceTest`, `ReembolsoAdminControllerIT.fallaYElAdminLoReintenta`
- R31: [x] `ReembolsoAdminServiceTest`, `ReembolsoAdminControllerIT.elReintentoVuelveAFallar`
- R32: [x] `ReglasPagoTest` (×2), `ReembolsoAdminServiceTest` (×2), `ReembolsoAdminControllerIT.idNoEncontrado` (7 ids malos)
- R33: [x] `ReembolsoAdminServiceTest`, `ReembolsoAdminControllerIT.yaReembolsado`
- R34: [x] `ReembolsoAdminServiceTest`, `ReembolsoAdminControllerIT.enProceso` (recién creado y con intento de hace 1 min)
- R35: [x] `ReembolsoAdminServiceTest`, `ReembolsoAdminControllerIT.ignoraElCuerpo`
- R36: [x] `RegistroReembolsosIT`, `ReembolsoAutomaticoIT.auditaLaSolicitudYElResultado`, `ReembolsoAdminControllerIT.fallaYElAdminLoReintenta` (detalle exacto, fecha del reloj)
- R37: [x] `RegistroReembolsosIT`, `ReembolsoAdminControllerIT.fallaYElAdminLoReintenta` / `elReintentoVuelveAFallar`
- R38: [x] `SolicitudReembolsoTest`, `RegistroReembolsosIT` (×2), `ReembolsoAutomaticoIT` (quien canceló y su IP), `ReembolsoAdminControllerIT` (admin e `IP_ADMIN`)
- R39: [x] `ReembolsoAdminControllerIT.sinSesion`, `sinRolAdmin`, `ipNoPermitida`, `idNoEncontrado`, `yaReembolsado`, `enProceso` (auditoría y filas iguales, spy `never()`)
- R40: [x] `PagoServiceImplTest.entregaElComandoParaDespuesDelCommit`, `ReembolsoAdminServiceTest`, `EjecutorReembolsosTest`, `SolicitudReembolsoTest`, `ArquitecturaReembolsoTest.soloElComandoConoceLaPasarela`
- R41: [x] `ArquitecturaReembolsoTest.sinTiposDelProveedor`
- R42: [x] `ReembolsoRestriccionesIT` (filas inválidas → `DataIntegrityViolationException`; filas válidas aceptadas)
- R43: [x] `PasarelaSimuladaTest.noReembolsaLoQueNoEmitio` (con 0 y con 1)

## Trazabilidad con la historia

La tabla final de `requirements.md` cubre todos los CA y los tres controles STRIDE de `docs/historias/HU-16.md`, y cada
control está implementado:

- CA1 (se procesa, contra el pago original, pasa a "reembolsada"): [x] R1–R12, R14, R40, R41. Implementado en `PagoServiceImpl.reembolsarCancelacion`, `SolicitudReembolso.ejecutar`, `RegistroReembolsos.registrarExito` (`Pago.marcarReembolsado`) y `MercadoPagoAdapter.reembolsar` / `PasarelaSimulada.reembolsar`.
- CA2 (falla → "reembolso fallido", el admin la ve, reintenta con un botón): [x] R13, R15, R17, R18, R25–R35, R43. Implementado en `RegistroReembolsos.registrarFallo`, `ReembolsoAdminController` / `ReembolsoAdminService` y `ReembolsosPorAtender.jsx` (botón "Reintentar" + `Modal`).
- (T/I) reembolso solo contra el pago original, vía la API de la pasarela: [x] R8, R9, R12, R35, R41, R42. La transacción y el monto salen de `RegistroReembolsos.iniciar` (filas `reembolso` y `pago`), y el controlador no lee cuerpo.
- (T) idempotencia: [x] R19–R24. `uq_reembolso_pago_origen`, `existsByPagoIdAndOrigen` + `saveAndFlush`, reclamación con `PESSIMISTIC_WRITE` en `iniciar` y clave estable `espacioya-reembolso-<id>`.
- (R) registro de cada intento y resultado: [x] R2, R18, R36–R39. `REEMBOLSO_SOLICITADO`, más `REEMBOLSO_REALIZADO` / `REEMBOLSO_FALLIDO` en la misma `REQUIRES_NEW` que el cambio de estado, sin texto del proveedor.

## Tasks completas

- T1–T24: [x] las 24 marcadas en `specs/HU-16/tasks.md` y comprobadas en el código y en las pruebas (migración `V4_3`; limpiezas en las 6 IT; dominio; `ReglasPago.idBienEscrito`; repositorio; Adapter en `MercadoPagoAdapter` y `PasarelaSimulada`; Command en `pagos/service/reembolso/`; `PagoServiceImpl`; servicio, DTO y controlador del admin; IT T13–T17; frontend T18–T21; docs T22; avisos T23 en `progress/HU-16.md`; T24 con `init.sh` en verde e `impl_HU-16.md` completo).

## Patrones ★

- **Command**: [x] coincide con design § 2. Command `ComandoReembolso`; ConcreteCommand `SolicitudReembolso` (`final`, no es bean, constructor de paquete); Invoker `EjecutorReembolsos` (`ejecutarDespuesDelCommit` vía `DespuesDelCommit`; `ejecutarAhora` lanza con transacción activa); Clients `PagoServiceImpl` (`AUTOMATICO`) y `ReembolsoAdminService` (`ADMIN`), que no conocen `PasarelaPago`; Receiver `PasarelaPago`; fábrica `SolicitudesReembolso`; apoyo `RegistroReembolsos` con `TransactionTemplate` `REQUIRES_NEW`. El reintento ejecuta el mismo `ejecutar()` (R40, verificado por reflexión).
- **Adapter**: [x] Target `PasarelaPago.reembolsar(SolicitudReembolsoPasarela) → ReembolsoRealizado` con records propios; `MercadoPagoAdapter` reutiliza `llamar`/`texto`, manda `X-Idempotency-Key` y su `log.warn` no lleva el cuerpo; `PasarelaSimulada` sigue DT-11 (prefijo `sim-`, contador por clave en `ConcurrentHashMap<String, AtomicInteger>`). Ningún tipo del proveedor sale del adaptador.

## Desviaciones (`progress/impl_HU-16.md`)

- **D-A (primer intento admitido): justificada, no rompe el spec.** Leído al pie de la letra, design § 7 hacía imposible R5: un `PENDIENTE` recién creado cuenta como *en proceso* y `iniciar` lo rechazaba. `Reembolso.admiteIntento` (`Reembolso.java:89-91`) admite además el `PENDIENTE` con `intentos == 0`. Esto respeta el texto de R22 ("otro intento") y deja intacto `motivoNoReintentable`, así que R29 y R34 se mantienen: el admin sigue recibiendo 400 *en proceso* para un reembolso recién creado, cubierto por `ReembolsoAdminControllerIT.enProceso`. Dos intentos a la vez sobre uno de 0 intentos se serializan en el bloqueo de `iniciar`, y el segundo ve `ultimoIntentoEn = ahora`, o sea *en proceso*. `docs/architecture.md` ya documenta `admiteIntento`. **Pendiente para el dueño:** Ariel Enriquez debe confirmar D-A antes del merge, como pide `progress/HU-16.md`. No bloquea la revisión.
- **D-B (IT en `pagos.controller`, monto 241.50): justificada.** `PagosSoporte` es de paquete; `ReembolsosSoporte` lo extiende.
- **D-C (`new PasarelaSimulada(SECRETO, 0)` en 5 casos de HU-13): justificada.** Es el constructor nuevo de design § 6. Un segundo constructor exigiría `@Autowired`, prohibido. Ningún caso cambia de contenido.
- **D-D (`StubMercadoPago.Solicitud` + `claveIdempotencia`): justificada.** La pide design § 13 y tasks T6.
- **D-E (`@DisplayName` "HU-15 … · HU-16 R6" en `PagoServiceImplTest`): aceptable.** Entra en la excepción (b) de "Reemplazos y compatibilidad". Los casos de HU-15 conservan su aserción original y suman `verifyNoInteractions(reembolsos, solicitudes, ejecutor)`. `montoNegativo` deja R6 trazado para el regex de `init.sh`.
- **D-F y D-G: amplían la cobertura sin cambiar el contrato.**

**Pruebas de HU-15 (design § 11.1):** [x] solo cambian `CancelacionReservaControllerIT.solicitaElReembolso` (ahora espera el pago `REEMBOLSADO` y una fila de `reembolso` `REEMBOLSADO`/`CANCELACION`/161.00, y el `@DisplayName` cita HU-16 R1 y R11) y las limpiezas `DELETE FROM reembolso` antes de `DELETE FROM pago` en `CancelacionReservaControllerIT`, `CancelacionConcurrenciaIT` (los dos lugares), `MisReservasControllerIT`, `PagosSoporte` y `PagoConcurrenciaIT`. `CancelacionConcurrenciaIT` sigue exigiendo un solo `REEMBOLSO_SOLICITADO`. Todas las IT de HU-13, HU-14 y HU-15 están en verde. La firma y el `MANDATORY` de `PagoService.reembolsarCancelacion` no cambian.

## Checkpoints

- C1 — Arnés completo: [x] archivos base, docs, historias, 4 subagentes; `./init.sh` completo con exit 0.
- C2 — Estado coherente: [x] una sola historia `in_progress` (HU-16, de Ariel Enriquez) con su `progress/HU-16.md`. La aprobación del spec por el dueño está anotada (2026-11-01, D1–D13) y `progress/HU-16.md` describe solo esta sesión. "Toda `done` con tests": n/a para HU-16, que aún no está `done`.
- C3 — Arquitectura:
  - [x] paquetes en `pagos`; ningún controller importa un repository; sin `@Autowired`, Lombok, comodines ni prints.
  - [x] sin dependencia nueva entre módulos ni puerto nuevo; la tabla de puertos no cambia. `reservas → pagos` sigue por `PagoService`.
  - [x] la API usa DTO `record` (`ReembolsoAdminDTO`, sin datos del usuario, de la tarjeta ni de la pasarela).
  - [x] la identidad y la IP salen de `@AuthenticationPrincipal` e `IpCliente.de`; el monto, la transacción y el estado se deciden en el servidor.
  - [x] migración nueva `V4_3__reembolso.sql`; `develop` termina en `V4_2` y ninguna migración subida fue editada.
  - [x] la pasarela queda detrás del Adapter `PasarelaPago`.
  - [x] nada de "fuera del alcance": sin colas, `@Async`, `@Scheduled`, correo nuevo ni reembolso de pagos tras expirar.
  - [x] sin secretos; `.env.example` solo gana una línea comentada.
  - [x] frontend sin colores fuera de los tokens ni librerías nuevas. `ReembolsosPorAtender.jsx` usa `Tarjeta`, `Boton secundario`, `Modal primario` con `cargando`, `Alerta` y `EtiquetaEstado`; llama a la API solo desde `src/api/pagos.js` (`encodeURIComponent`) y no tiene estilos en línea. Los estados `FALLIDO` → error y `PENDIENTE` → atención siguen la fila nueva de "Color de cada estado" en `docs/frontend.md`, y los textos coinciden con design § 12.
- C4 — Verificación real: [x] nombres `*Test` / `*IT`, las IT con `TestcontainersConfiguration` (PostgreSQL real, sin H2) y el margen de 2 min probado con `Clock` fijo / `reloj.avanzar`. Hay IT para los endpoints, la migración y las restricciones; la concurrencia se prueba con hilos reales (`ReembolsoConcurrenciaIT`). Cada test comprueba valores concretos, también en el camino de error. `verify`: 1717 tests en verde; lint y build en verde.
- C5 — Cierre de sesión:
  - [x] sin archivos sospechosos sin trackear (solo los de HU-16).
  - n/a: la entrada de `progress/history.md` y la vuelta de `progress/HU-16.md` a la plantilla se hacen al cerrar la historia.
  - [x] `feature_list.json` deja HU-16 en `in_progress`, que es lo correcto antes del cierre por el dueño.
  - [x] el trabajo está en `feature/HU-16-reembolso-automatico`.
- C6 — SDD: [x] `specs/HU-16/` completo; `requirements.md` en EARS con la tabla de trazabilidad; `design.md` con endpoints, clases, migración, secuencias, 11 alternativas descartadas y los dos patrones ★; tasks en `[x]`; mapa R → test en `progress/impl_HU-16.md`. Los pendientes de §9 los decidió el dueño al aprobar D1–D13.

## Cambios requeridos

Ninguno bloqueante.

Observaciones para el dueño, antes del merge:
1. Confirmar la desviación **D-A** (`Reembolso.admiteIntento`: el primer intento automático se admite aunque el reembolso recién creado cuente como *en proceso*).
2. Al cerrar: entrada en `progress/history.md`, `progress/HU-16.md` de vuelta a la plantilla y HU-16 → `done` (lo hace el dueño, no el orquestador).
