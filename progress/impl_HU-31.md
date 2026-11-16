# Implementación HU-31 · Fallo de reembolso simulado sin estado en memoria

- **Agente:** implementer · **Fecha:** 2026-11-15 · **Rama:** feature/HU-31-reembolso-simulado-sin-estado
- **Spec:** `specs/HU-31/{requirements,design,tasks}.md` (aprobado por Jorge Quispe el 2026-11-15)
- **Tasks:** T1–T10 hechas (ver `specs/HU-31/tasks.md`). Ninguna task quedó pendiente ni se desvió del spec.

## Archivos

### Código (main)
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/pagos/service/pasarela/SolicitudReembolsoPasarela.java` (M):
  componente `int numeroIntento`, constructor compacto (`< 1` → `IllegalArgumentException`), javadoc.
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/pagos/service/pasarela/PasarelaSimulada.java` (M): decide por
  `numeroIntento <= fallosReembolso`; se eliminó `llamadasDeReembolso` y el import de `AtomicInteger`; javadoc de clase y
  `log.info("… en el intento {}")`.
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/pagos/service/pasarela/MercadoPagoAdapter.java` (M, solo comentario).
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/pagos/service/reembolso/SolicitudReembolso.java` (M): pasa
  `intento.numero()`.
- `2_Backend/src/main/resources/application.properties` y `2_Backend/.env.example` (M, solo comentarios).

### Pruebas
- `pagos/service/pasarela/SolicitudReembolsoPasarelaTest.java` (N).
- `pagos/service/pasarela/PasarelaSimuladaTest.java` (M): helper `reembolso(id, clave, numero)`; se reemplazaron
  `fallaLasPrimerasLlamadasDeCadaClave` y `fallaExactamenteNLlamadas` (HU-16 R15, reemplazado) por
  `fallaLosIntentosHastaN`, `mismoResultadoParaLaMismaSolicitud`, `dosReplicasAlternadas`,
  `dosReplicasDanElMismoResultado` (parametrizado 1..5) e `instanciaNuevaTrasReiniciar`; `apruebaElReembolsoSimulado`
  usa el intento 1; `noReembolsaLoQueNoEmitio` (HU-16 R43) usa números 1 y 2, y el nuevo
  `noReembolsaLoQueNoEmitioConCualquierNumero` (HU-31 R12) va parametrizado con N ∈ {0, 1} × número ∈ {1, 2}.
- `pagos/service/reembolso/SolicitudReembolsoTest.java` (M): record de 4 componentes; números 1 y 2.
- `pagos/service/pasarela/MercadoPagoAdapterTest.java` (M): `REEMBOLSO` con número 1; nuevo `elNumeroDeIntentoNoSeEnvia`.
- `pagos/controller/ReembolsoAdminControllerIT.java` (M): `mismaClaveEnElReintento` (ya no exige records iguales;
  compara transacción, monto, clave y números 1, 2), `ignoraElCuerpo` (cuerpo con `numeroIntento`/`intentos` = 99 →
  número enviado 2 e `intentos = 2`), nuevos `reintentoTrasReiniciar` y `elFalloPorNumeroQuedaAuditado`; `@DisplayName`
  de `fallaYElAdminLoReintenta` cita HU-31 R5, R6, R13.
- `pagos/controller/ReembolsoAutomaticoIT.java` (M): `reembolsaContraElPagoOriginal` exige `numeroIntento() == 1`;
  `@DisplayName` de `cancelarReembolsa` cita HU-31 R7.

### Docs
- `docs/architecture.md` (M): firma del record en la tabla de piezas de pagos y párrafo de
  `espacioya.pagos.simulada.fallos-reembolso` (se borró "el contador vive en memoria"). Sin puertos nuevos.

## Trazabilidad R → test

| R | Test(s) |
|---|---------|
| R1 | `SolicitudReembolsoTest.solicitudConLosDatosDelIntento`, `SolicitudReembolsoTest.mismaClaveEnCadaEjecucion`, `ReembolsoAutomaticoIT.reembolsaContraElPagoOriginal` |
| R2 | `ReembolsoAdminControllerIT.ignoraElCuerpo` |
| R3 | `SolicitudReembolsoPasarelaTest.rechazaNumeroMenorQueUno`, `SolicitudReembolsoPasarelaTest.aceptaElIntentoUno` |
| R4 | `SolicitudReembolsoTest.mismaClaveEnCadaEjecucion`, `ReembolsoAdminControllerIT.mismaClaveEnElReintento`, `MercadoPagoAdapterTest.elNumeroDeIntentoNoSeEnvia` |
| R5 | `PasarelaSimuladaTest.fallaLosIntentosHastaN`, `ReembolsoAdminControllerIT.fallaYElAdminLoReintenta` |
| R6 | `PasarelaSimuladaTest.fallaLosIntentosHastaN`, `PasarelaSimuladaTest.apruebaElReembolsoSimulado`, `ReembolsoAdminControllerIT.fallaYElAdminLoReintenta` |
| R7 | `PasarelaSimuladaTest.apruebaElReembolsoSimulado`, `ReembolsoAutomaticoIT.cancelarReembolsa` |
| R8 | `PasarelaSimuladaTest.mismoResultadoParaLaMismaSolicitud`, `PasarelaSimuladaTest.dosReplicasAlternadas`, `PasarelaSimuladaTest.dosReplicasDanElMismoResultado` |
| R9 | `PasarelaSimuladaTest.dosReplicasAlternadas`, `PasarelaSimuladaTest.dosReplicasDanElMismoResultado` |
| R10 | `PasarelaSimuladaTest.instanciaNuevaTrasReiniciar` |
| R11 | `ReembolsoAdminControllerIT.reintentoTrasReiniciar` |
| R12 | `PasarelaSimuladaTest.noReembolsaLoQueNoEmitioConCualquierNumero` |
| R13 | `ReembolsoAdminControllerIT.elFalloPorNumeroQuedaAuditado`, `ReembolsoAdminControllerIT.fallaYElAdminLoReintenta` |
| R14 | `MercadoPagoAdapterTest.elNumeroDeIntentoNoSeEnvia` |

## Guía de caja negra para UAT

1. Con `PAGOS_SIMULADA_FALLOS_REEMBOLSO=1` y dos réplicas del backend: pagar una reserva con la simulada y cancelarla
   con ≥ 48 h → el reembolso queda `FALLIDO` con 1 intento (auditoría `REEMBOLSO_FALLIDO … intento=1`).
2. Reiniciar el backend, o dejar que la siguiente petición caiga en la otra réplica.
3. Como admin, en `/admin/reembolsos` pulsar "Reintentar" → `REEMBOLSADO` con 2 intentos (auditoría
   `REEMBOLSO_REALIZADO … intento=2;disparo=ADMIN`). Antes de HU-31, tras un reinicio el reintento volvía a fallar.
4. Con `PAGOS_SIMULADA_FALLOS_REEMBOLSO=0` (o sin la variable): cancelar una reserva pagada → `REEMBOLSADO` al primer
   intento.

## Nota de trazabilidad para el reviewer

- El validador de `init.sh` solo cuenta el HU con el que **empieza** el `@DisplayName` y corta en `;`. Por eso cada
  test empieza con un único HU y cita el otro entre paréntesis (p. ej. `"HU-31 R1: … (HU-16 R8, R9)"`). La primera
  corrida falló porque había nombres `"HU-16 R8, R9; HU-31 R1: …"`; se reescribieron.
- **HU-16 R15** (reemplazado por HU-31 R5 y R8, ver requirements "Reemplazos") sigue exigido por el validador porque
  HU-16 está `done`. Se agregó `R15` a la lista HU-16 de `ReembolsoAdminControllerIT.fallaYElAdminLoReintenta`, que
  prueba con N = 1 que el intento automático falla y el reintento se aprueba; el `@DisplayName` aclara que R15 queda
  reemplazado por HU-31 R5 y R8. Los dos tests que contaban llamadas se eliminaron, como pide el design § 8.
- Para que HU-16 R43 siga cubierto, `noReembolsaLoQueNoEmitio` (HU-16 R43, N ∈ {0, 1}) se mantiene, y se agregó
  `noReembolsaLoQueNoEmitioConCualquierNumero` (HU-31 R12, N × número).

## Verificación

`./init.sh` completo (sin `--rapido`, Docker encendido) → **[OK] Entorno listo**.

- Unitarias (surefire): **2228 pruebas, 0 fallas, 0 errores, 0 omitidas**.
- Integración `*IT` (failsafe, `./mvnw verify`): **1275 pruebas, 0 fallas, 0 errores, 0 omitidas**.
- Validador de trazabilidad: sin avisos para HU-31 ni HU-16. Reglas de arquitectura, ESLint y build del frontend: OK.

## Pendientes

Ninguno. La historia queda `in_progress` a la espera del reviewer (no se marcó `done`; sin commit, push ni PR).
