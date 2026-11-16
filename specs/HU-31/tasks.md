# HU-31 · Tasks — Fallo de reembolso simulado sin estado en memoria

> Orden pensado para que el proyecto compile al terminar T3. Cada test lleva `@DisplayName("HU-31 R<n>: …")`.
> Sin migraciones ni tareas de frontend.

- [x] T1 — `SolicitudReembolsoPasarela` (M): cuarto componente `int numeroIntento` y constructor compacto que lanza
  `IllegalArgumentException` si es menor que 1; javadoc (design § 5). Cubre: R1, R3. Test: `SolicitudReembolsoPasarelaTest`
  (N: 0 y −1 lanzan, 1 se acepta).
- [x] T2 — `SolicitudReembolso.ejecutar()` (M): construye la solicitud con `intento.numero()`. Cubre: R1, R2, R4. Test:
  `SolicitudReembolsoTest` (M: `solicitudConLosDatosDelIntento` espera el número 1; `mismaClaveEnCadaEjecucion` exige
  números 1 y 2 con la misma clave).
- [x] T3 — `PasarelaSimulada.reembolsar` (M): decide por `solicitud.numeroIntento() <= fallosReembolso` (DT-3), elimina
  `llamadasDeReembolso` y el import de `AtomicInteger`, actualiza el javadoc de clase (líneas 30-32) y el `log.info` del
  fallo ("en el intento {}"). Cubre: R5, R6, R7, R8, R9, R10, R12. Test: `PasarelaSimuladaTest` (M: helper con número;
  reemplazar `fallaLasPrimerasLlamadasDeCadaClave` y `fallaExactamenteNLlamadas` por los casos (a)–(d) de design § 8,
  incluidos el de **dos instancias** (réplicas) y el de **instancia nueva** (reinicio); adaptar
  `apruebaElReembolsoSimulado` y `noReembolsaLoQueNoEmitio`).
- [x] T4 — `MercadoPagoAdapter` (M, solo comentario de la línea 147: el número no se envía). Cubre: R14. Test:
  `MercadoPagoAdapterTest` (M: constante `REEMBOLSO` con número 1; test nuevo con números 1 y 3 que exige solicitudes
  `POST` iguales en ruta, autorización, cuerpo y `X-Idempotency-Key`).
- [x] T5 — `ReembolsoAdminControllerIT` (M): ajustar `mismaClaveEnElReintento` (misma transacción, monto y clave;
  números 1 y 2) y `ignoraElCuerpo` (cuerpo con `numeroIntento` e `intentos` = 99 → número enviado 2); agregar el test de
  reinicio (`fallidoSembrado` → reintento → `REEMBOLSADO`, `intentos = 2`, auditoría `intento=2;disparo=ADMIN`) y el de
  registro (`PENDIENTE` sin respuesta con 0 intentos → reintento → `FALLIDO`, `REEMBOLSO_FALLIDO` con `intento=1`);
  agregar `HU-31 R5, R6, R13` al `@DisplayName` de `fallaYElAdminLoReintenta`. Cubre: R2, R4, R5, R6, R11, R13. Test:
  `ReembolsoAdminControllerIT`.
- [x] T6 — `ReembolsoAutomaticoIT` (M): `reembolsaContraElPagoOriginal` exige `numeroIntento() == 1`; agregar
  `HU-31 R7` al `@DisplayName` de `cancelarReembolsa`. Cubre: R1, R7. Test: `ReembolsoAutomaticoIT`.
- [x] T7 — Comentarios de configuración (M): `application.properties` (líneas 118-119) y `2_Backend/.env.example`
  (líneas 108-110) describen "los intentos 1 a N de cada reembolso, por el número guardado en la base, también con
  varias réplicas y tras reiniciar". Sin propiedades nuevas. Cubre: R5. Test: `ReembolsoAdminControllerIT` (la
  propiedad sigue leyéndose con *N* = 1).
- [x] T8 — `docs/architecture.md` (M): firma del record en la línea 412 y párrafo de la propiedad (líneas 490-492) según
  design § 9. Cubre: R4, R5, R14 (documentación).
- [x] T9 — `progress/impl_HU-31.md`: mapa R → test (`@DisplayName`), lista de tests existentes que cambiaron (design § 8)
  y guía de caja negra para UAT: con `PAGOS_SIMULADA_FALLOS_REEMBOLSO=1` y dos réplicas, cancelar una reserva pagada
  (falla), reiniciar el backend o dejar que el reintento caiga en la otra réplica, reintentar desde `/admin/reembolsos`
  → `REEMBOLSADO` con 2 intentos; con `0`, cancelar → reembolsado al primer intento. Cubre: R1–R14.
- [x] T10 — Verificación: `./init.sh` en verde (unitarias, IT con Docker, lint y build del frontend). Cubre: R1–R14.
