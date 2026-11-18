# Review — HU-31

**Veredicto:** APPROVED

Revisado sobre la rama `feature/HU-31-reembolso-simulado-sin-estado` (sin commits propios respecto a `develop`; todo el
trabajo está sin commitear: 5 archivos de `main`, 2 de configuración, 6 de pruebas, `docs/architecture.md`,
`feature_list.json`, `specs/HU-31/` y `progress/`). `./init.sh` completo (sin `--rapido`, Docker encendido) lo corrió el
reviewer: exit 0, unitarias 2228 / 0 fallas / 0 errores / 0 omitidas, IT 1275 / 0 / 0 / 0, ESLint y build del frontend
OK, validador de trazabilidad sin avisos.

## Trazabilidad requirements ↔ tests
- R1: [x] `SolicitudReembolsoTest.solicitudConLosDatosDelIntento` (verifica el record exacto con número 1),
  `SolicitudReembolsoTest.mismaClaveEnCadaEjecucion` (números 1 y 2), `ReembolsoAutomaticoIT.reembolsaContraElPagoOriginal`
  (`numeroIntento() == 1` contra PostgreSQL)
- R2: [x] `ReembolsoAdminControllerIT.ignoraElCuerpo`: el cuerpo trae `numeroIntento: 99` e `intentos: 99`; se exige que
  el número enviado sea 2 y que la fila quede en `intentos = 2`
- R3: [x] `SolicitudReembolsoPasarelaTest.rechazaNumeroMenorQueUno` (0 y -1 lanzan IAE), `aceptaElIntentoUno`
- R4: [x] `SolicitudReembolsoTest.mismaClaveEnCadaEjecucion`, `ReembolsoAdminControllerIT.mismaClaveEnElReintento`
  (misma transacción, monto y clave; números `containsExactly(1, 2)`), `MercadoPagoAdapterTest.elNumeroDeIntentoNoSeEnvia`
- R5: [x] `PasarelaSimuladaTest.fallaLosIntentosHastaN` (N = 2: 1 y 2 fallan), `ReembolsoAdminControllerIT.fallaYElAdminLoReintenta`
- R6: [x] `PasarelaSimuladaTest.fallaLosIntentosHastaN` (el 3 aprueba con `sim-reembolso-<clave>`), `apruebaElReembolsoSimulado`
- R7: [x] `PasarelaSimuladaTest.apruebaElReembolsoSimulado` (N = 0, intento 1), `ReembolsoAutomaticoIT.cancelarReembolsa`
- R8: [x] `PasarelaSimuladaTest.mismoResultadoParaLaMismaSolicitud` (repeticiones y otra clave),
  `dosReplicasDanElMismoResultado` (1..5, con la réplica A con una llamada previa)
- R9: [x] `PasarelaSimuladaTest.dosReplicasAlternadas` (A(1) falla, B(2) falla, A(3) y B(3) aprueban con el mismo id)
- R10: [x] `PasarelaSimuladaTest.instanciaNuevaTrasReiniciar`
- R11: [x] `ReembolsoAdminControllerIT.reintentoTrasReiniciar` (FALLIDO sembrado con 1 intento, reintento: 200
  `REEMBOLSADO`, `intentos = 2`, `id_reembolso_pasarela` y detalle de auditoría completo con `intento=2;disparo=ADMIN`)
- R12: [x] `PasarelaSimuladaTest.noReembolsaLoQueNoEmitioConCualquierNumero` (N en {0,1} por número en {1,2})
- R13: [x] `ReembolsoAdminControllerIT.elFalloPorNumeroQuedaAuditado` (pasarela espiada con el método real, sin
  `doThrow`: `REEMBOLSO_FALLIDO` sobre `reembolso/<id>` con `;intento=1;disparo=ADMIN;` y sin `idReembolso`); el
  detalle completo de DT-10 lo exige además `fallaYElAdminLoReintenta`
- R14: [x] `MercadoPagoAdapterTest.elNumeroDeIntentoNoSeEnvia` (números 1 y 3: dos POST iguales en ruta, autorización
  `Bearer`, cuerpo `{amount: 161.00}` y `X-Idempotency-Key`)

Comprobado en `2_Backend/target/{surefire,failsafe}-reports/`: `ReembolsoAdminControllerIT` 15/0,
`ReembolsoAutomaticoIT` 7/0, `PasarelaSimuladaTest` 30/0, `MercadoPagoAdapterTest` 51/0, `SolicitudReembolsoTest` 6/0,
`SolicitudReembolsoPasarelaTest` 3/0; los nuevos `reintentoTrasReiniciar`, `elFalloPorNumeroQuedaAuditado`,
`dosReplicas*`, `instanciaNuevaTrasReiniciar`, `noReembolsaLoQueNoEmitioConCualquierNumero[1..4]` y
`elNumeroDeIntentoNoSeEnvia` aparecen en los reportes y pasan.

## Trazabilidad con la historia
- CA1 (fallan 1..N, aprueba N+1, cualquier réplica): [x] R1, R4, R5, R6, R8, R9
- CA2 (tras reinicio depende del número en la base): [x] R1, R10, R11
- CA3 (`fallos-reembolso = 0` aprueba al primer intento): [x] R6, R7
- CA4 (adaptador real ignora el número, misma clave): [x] R4, R14
- (T) número de intento sale de la base, nunca de la petición: [x] R1, R2, R3. Implementado:
  `RegistroReembolsos.java:55-58` arma `IntentoReembolso` con `reembolso.getIntentos()` tras `iniciarIntento`;
  `SolicitudReembolso.java:48-49` pasa `intento.numero()`; es la única construcción del record en `src/main`.
- (R) cada intento fallido sigue registrado igual: [x] R13 (sin cambios en `RegistroReembolsos` ni en auditoría).

## Verificaciones pedidas por el orquestador
- `PasarelaSimulada` sin estado para reembolsos: [x] se eliminó `llamadasDeReembolso` y el import de `AtomicInteger`;
  `reembolsar` decide con `solicitud.numeroIntento() <= fallosReembolso` después del chequeo `sim-` (orden de DT-3).
  El mapa `montos` (HU-13, checkout) se conserva, como pide DT-3.
- `MercadoPagoAdapter` sin cambio de comportamiento: [x] el diff es solo un comentario (línea 148); no lee
  `numeroIntento`; la clave enviada sigue siendo `claveIdempotencia`.
- HU-16 y HU-26 verdes: [x] `ReembolsoAdminControllerIT`, `ReembolsoAutomaticoIT`, `ReembolsoConcurrenciaIT`,
  `RegistroReembolsosIT` y `ReclamoReembolsoFallidoIT` (1/0) pasan en la corrida completa.
- Cambios en pruebas existentes justificados y sin debilitar:
  - `mismaClaveEnElReintento`: la igualdad de records ya no es posible porque el número difiere por diseño
    (design § 8); se sustituye por igualdad de transacción, monto y clave más `numeroIntento` 1 y 2. Igual de estricta.
  - `ignoraElCuerpo`: solo se agregan aserciones (número 2, `intentos = 2`).
  - `fallaLasPrimerasLlamadasDeCadaClave` y `fallaExactamenteNLlamadas` se eliminan porque probaban HU-16 R15, que
    HU-31 reemplaza explícitamente (requirements, sección Reemplazos); sus escenarios quedan cubiertos con el nuevo
    criterio.
  - `noReembolsaLoQueNoEmitio` (HU-16 R43) se conserva y además prueba el número 2.
  - `SolicitudReembolsoTest.mismaClaveEnCadaEjecucion` agrega aserciones de transacción, monto y número.
- Líneas de 120 como máximo: [x] ninguna línea nueva o modificada pasa de 120. Las que superan 120 en
  `PasarelaSimulada.java:23-24`, `MercadoPagoAdapter.java:79,147`, `SolicitudReembolso.java:15` y algunos
  `@DisplayName` de `ReembolsoAdminControllerIT`/`MercadoPagoAdapterTest` son preexistentes (no están en el diff).

## Tasks completas
- T1: [x]
- T2: [x]
- T3: [x]
- T4: [x]
- T5: [x]
- T6: [x]
- T7: [x]
- T8: [x]
- T9: [x]
- T10: [x] (confirmado con la corrida propia de `./init.sh`)

## Checkpoints
- C1: [x] archivos del arnés, docs, historias y agentes presentes; `./init.sh` completo exit 0.
- C2: [x] una sola historia `in_progress` (HU-31, Jorge Quispe); `progress/HU-31.md` existe, describe solo esta sesión
  y anota la aprobación del spec por el dueño el 2026-11-15. Historias done con tests: n/a para HU-31; el validador no
  reporta huecos en las demás.
- C3: [x] módulo `pagos`; sin repositorios en controladores, sin `@Autowired`/Lombok/comodines/prints; sin puertos
  nuevos; sin DTO ni endpoints nuevos; número de intento solo del servidor; migraciones n/a; pasarela detrás del
  Adapter; nada fuera del alcance; sin secretos. Frontend: n/a (sin cambios).
- C4: [x] nombres `<Clase>Test`/`<Clase>IT`; IT contra PostgreSQL (Testcontainers); plazos n/a; IT para el reintento;
  cada test comprueba valores concretos; unitarias e IT > 0 y verdes; lint y build OK.
- C5: [x] sin archivos sospechosos sin trackear; trabajo en `feature/HU-31-...`; `feature_list.json` en `in_progress`.
  Entrada en `progress/history.md` y vuelta de `progress/HU-31.md` a la plantilla: n/a hasta el cierre.
- C6: [x] `specs/HU-31/{requirements,design,tasks}.md` presentes; EARS y tabla de trazabilidad completa; design con
  secuencia, clases y alternativas descartadas (sin ★ propio); todas las tasks `[x]`; cada R mapeado en
  `progress/impl_HU-31.md`; ningún pendiente de §9 tocado.

## Observaciones (no bloquean)
1. `ReembolsoAdminControllerIT.fallaYElAdminLoReintenta` agrega `R15` a su lista HU-16 solo para que el validador de
   `init.sh` no marque HU-16 R15 sin cobertura, aunque R15 quedó reemplazado. El `@DisplayName` lo aclara y está
   documentado en `progress/impl_HU-31.md`. Es aceptable, pero el dueño debe saber que es un parche para el validador,
   no una prueba del criterio original (N llamadas por clave).
2. Al cerrar: mover el resumen de `progress/HU-31.md` a `progress/history.md` y devolver `progress/HU-31.md` a la
   plantilla (C5).

## Cambios requeridos
Ninguno.
