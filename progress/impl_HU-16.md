# Implementación HU-16 — Reembolso automático (★ Command, ★ Adapter)

- **Dueño:** Ariel Enriquez · **Rama:** `feature/HU-16-reembolso-automatico` · **Fecha:** 2026-11-01
- **Agente:** implementer (2 sesiones: la primera se bloqueó por Docker apagado; esta la reanudó)
- **Estado:** T1–T24 hechas (`[x]` en `specs/HU-16/tasks.md`). Status sigue `in_progress`: espera al reviewer.

## Resultado de `./init.sh` (completo, sin `--rapido`)

**exit 0 — "Entorno listo".** Corrida final del 2026-11-01, con Docker 29.0.1:

- `./mvnw verify`: **1027** unitarias y **690** de integración, 0 fallos, 0 errores, 0 omitidas.
- ESLint sin errores ni advertencias (`--max-warnings=0`); `npm run build` compila.
- Reglas de arquitectura, colores, librerías y secretos: todas `[OK]`. Validación de `feature_list.json`/specs: `[OK]`,
  sin avisos de HU-16 (R1–R43 trazados por `@DisplayName("HU-16 R<n>: …")`).
- Único `[WARN]`: las llaves `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA` y `ALMACENAMIENTO_LLAVE_FIRMA` no están en el
  entorno de la shell local (solo afecta a `spring-boot:run`; las pruebas traen las suyas). Ajeno a HU-16.

## Desviaciones del spec

- **D-A (lógica, a confirmar por Ariel Enriquez) — el primer intento se admite aunque el reembolso "esté en proceso".**
  Con el glosario de `requirements.md` y DT-6, un `PENDIENTE` recién creado (0 intentos, `creado_en` = ahora) está *en
  proceso*, y design § 7 dice que `RegistroReembolsos.iniciar` devuelve vacío si `motivoNoReintentable(ahora)` está
  presente. Literalmente, el intento automático (R5) **nunca** podía correr: `ReembolsoAutomaticoIT` lo mostró (4/7
  fallaban con el reembolso `PENDIENTE`, 0 intentos). R22 prohíbe iniciar *otro* intento mientras uno está en proceso; el
  primero no tiene ninguno antes. Corrección mínima, sin requirements nuevos:
  - `Reembolso.admiteIntento(Instant ahora)` (público, nuevo) = `PENDIENTE` con `intentos == 0` **o**
    `motivoNoReintentable(ahora)` vacío. `iniciarIntento` lanza si `!admiteIntento`. `RegistroReembolsos.iniciar` usa
    `admiteIntento` en vez de `motivoNoReintentable`.
  - `motivoNoReintentable` **no cambia**: `ReembolsoAdminService` sigue respondiendo 400 *en proceso* a un reintento de un
    reembolso recién creado (R34) y el listado no lo muestra hasta pasados 2 min (R29).
  - Consecuencia aceptada: un `PENDIENTE` con 0 intentos más viejo que 2 min (el proceso cayó antes de `afterCommit`) lo
    puede reintentar el admin, como ya decía D5. Dos intentos a la vez sobre un 0-intentos se siguen serializando en el
    bloqueo de `iniciar`: el primero lo deja en 1 intento y el segundo ve *en proceso*.
  - Tests: `ReembolsoTest.primerIntentoSeAdmite` (nuevo), `RegistroReembolsosIT.iniciarElPrimero`.
- **D-B — IT de HU-16 en el paquete `pagos.controller`.** `ReembolsoRestriccionesIT` y `RegistroReembolsosIT` (que el
  spec pide "extiende `PagosSoporte`") viven en `pagos.controller` porque `PagosSoporte` es de paquete. Comparten
  `ReembolsosSoporte` (extiende `PagosSoporte`, IP `10.18.x.x`, siembra reservas `PENDIENTE_PAGO` de S/ 241.50 que se
  pagan y cancelan por la API, y pagos/reembolsos sembrados por JDBC). Los montos de las IT son 241.50, no 161.00 como
  en los ejemplos del design.
- **D-C — `PasarelaSimuladaTest`:** las 5 llamadas existentes `new PasarelaSimulada(SECRETO)` pasan a
  `new PasarelaSimulada(SECRETO, 0)` porque el constructor gana `fallosReembolso` (design § 6) y un segundo constructor
  exigiría `@Autowired`, prohibido en `src/main`. Ningún caso cambia de contenido.
- **D-D — `StubMercadoPago.Solicitud`** gana el componente `claveIdempotencia` (lo pide design § 13).
- **D-E — `PagoServiceImplTest`:** los 3 casos de error de HU-15 suman "· HU-16 R6" al `@DisplayName` y verifican además
  que no se toca `reembolso` ni el comando. Como el regex de `init.sh` no cuenta una R de HU-16 tras "HU-15 … ·", se
  agregó `montoNegativo` ("HU-16 R6: …") para que R6 quede trazado. Lo mismo pasa con "HU-16 R1, R11" en
  `CancelacionReservaControllerIT.solicitaElReembolso` (R1 y R11 ya están cubiertos por otros tests de HU-16).
- **D-F — R16 se prueba en el automático y en el reintento.** El spy de `ReembolsoAdminControllerIT` comprueba con
  `FOR UPDATE NOWAIT` desde otro hilo las filas de `reembolso`, `pago` y `reserva` en las dos llamadas a la pasarela.
- **D-G — `ReembolsoConcurrenciaIT` (R23):** para que el perdedor vea siempre *en proceso* (y no *ya reembolsado* si el
  ganador terminó antes), el spy de la pasarela espera a que el otro reintento haya respondido antes de delegar. 5 rondas.

## Tests existentes que cambiaron (y solo estos)

| Test | Cambio |
|---|---|
| `reservas/controller/CancelacionReservaControllerIT` | `solicitaElReembolso` espera pago `REEMBOLSADO` y una fila `reembolso` `REEMBOLSADO`, `CANCELACION`, 161.00; `@DisplayName` "HU-15 R22, R24 · HU-16 R1, R11: …". Limpieza: `DELETE FROM reembolso` antes de `pago` |
| `reservas/controller/CancelacionConcurrenciaIT` | Limpieza en los dos lugares. Sigue un solo `REEMBOLSO_SOLICITADO` |
| `reservas/controller/MisReservasControllerIT`, `pagos/controller/PagosSoporte`, `pagos/controller/PagoConcurrenciaIT` | Limpieza: `DELETE FROM reembolso` antes de `pago` |
| `pagos/service/impl/PagoServiceImplTest` | Constructor nuevo y `TransactionSynchronizationManager` inicializado; casos de HU-15 conservados (D-E); 4 casos nuevos de HU-16 |
| `pagos/domain/ReglasPagoTest`, `pagos/service/pasarela/MercadoPagoAdapterTest`, `PasarelaSimuladaTest` | Solo casos agregados (y D-C) |
| `pagos/service/pasarela/StubMercadoPago` | Ruta de reembolsos configurable y `claveIdempotencia` en `Solicitud` (D-D) |

## Mapa R → test (`@DisplayName("HU-16 R<n>: …")`)

`RAIT` = `pagos.controller.ReembolsoAutomaticoIT` · `RACIT` = `pagos.controller.ReembolsoAdminControllerIT` ·
`REGIT` = `pagos.controller.RegistroReembolsosIT` · `RRIT` = `pagos.controller.ReembolsoRestriccionesIT`.

| R | Tests |
|---|---|
| R1 | `ReembolsoTest.valoresIniciales`, `PagoServiceImplTest.guardaElReembolsoPendiente`, `RAIT.cancelarReembolsa`, `RAIT.ceroPorCientoSinReembolso` |
| R2 | `RAIT.auditaLaSolicitudYElResultado` |
| R3 | `PagoServiceImplTest.entregaElComandoParaDespuesDelCommit`, `EjecutorReembolsosTest.noCorreAntesDelCommit`, `EjecutorReembolsosTest.despuesDelCommitExigeTransaccion`, `RAIT.cancelacionRechazadaNoReembolsa`, `RAIT.rollbackDelLlamador`, `RAIT.soloTrasElCommit` |
| R4 | `EjecutorReembolsosTest.noCorreConRollback`, `RAIT.cancelacionRechazadaNoReembolsa`, `RAIT.rollbackDelLlamador` |
| R5 | `EjecutorReembolsosTest.correTrasElCommit`, `ReembolsoTest.primerIntentoSeAdmite`, `REGIT.iniciarElPrimero`, `RAIT.cancelarReembolsa`, `RAIT.soloTrasElCommit` |
| R6 | `PagoServiceImplTest.montoNegativo` (+ `sinPagoPagado`, `montoCero`, `montoMayorQueElPago`) |
| R7 | `RACIT.fallaYElAdminLoReintenta` |
| R8 | `SolicitudReembolsoTest.solicitudConLosDatosDelIntento`, `RAIT.reembolsaContraElPagoOriginal` |
| R9 | `SolicitudReembolsoTest.solicitudConLosDatosDelIntento`, `RAIT.reembolsaContraElPagoOriginal`, `RACIT.ignoraElCuerpo` |
| R10 | `ReembolsoTest.marcarResultado`, `ReembolsoTest.marcarDesdeOtroEstado`, `REGIT.registrarExito`, `RAIT.cancelarReembolsa` |
| R11 | `PagoTest.pasaAReembolsado`, `PagoTest.otrosEstadosNoSeReembolsan`, `REGIT.registrarExito`, `RAIT.cancelarReembolsa` |
| R12 | `MercadoPagoAdapterTest.enviaElReembolso`, `MercadoPagoAdapterTest.reembolsoAprobadoConIdDeTexto` |
| R13 | `MercadoPagoAdapterTest.reembolsoConError`, `reembolsoConTiempoAgotado`, `reembolsoNoAprobado` |
| R14 | `PasarelaSimuladaTest.apruebaElReembolsoSimulado`, `RAIT.cancelarReembolsa` |
| R15 | `PasarelaSimuladaTest` (primera llamada por clave falla; `fallaExactamenteNLlamadas`) |
| R16 | `RACIT.sinBloqueosDuranteLaLlamada`, `SolicitudReembolsoTest` (InOrder), `EjecutorReembolsosTest.ahoraSinTransaccion` |
| R17 | `PagoTest.pasaAReembolsoFallido`, `ReembolsoTest.marcarResultado`, `SolicitudReembolsoTest.falloDeLaPasarela`, `REGIT.registrarFallo`, `REGIT.falloDesdePagado`, `RACIT.fallaYElAdminLoReintenta` |
| R18 | `ReembolsoTest.iniciarIntento`, `REGIT.iniciarConfirmaElIntento`, `REGIT.iniciarElPrimero`, `RACIT.fallaYElAdminLoReintenta` |
| R19 | `RRIT.unoPorPagoYOrigen` |
| R20 | `PagoServiceImplTest.yaTieneReembolso` |
| R21 | `ReembolsoTest.reembolsadoNoSeReintenta`, `SolicitudReembolsoTest.reclamacionVacia`, `REGIT.iniciarSinMotivo`, `RACIT.yaReembolsado` |
| R22 | `ReembolsoTest.margenSinRespuesta`, `margenDesdeElUltimoIntento`, `fallidoSeReintenta`, `primerIntentoSeAdmite`, `SolicitudReembolsoTest.reclamacionVacia`, `REGIT.iniciarSinMotivo`, `RACIT.enProceso`, `ReembolsoConcurrenciaIT.dosReintentosALaVez` |
| R23 | `ReembolsoConcurrenciaIT.dosReintentosALaVez`, `ReembolsoAdminServiceTest.otroReintentoGano` |
| R24 | `ReembolsoTest.claveIdempotencia`, `SolicitudReembolsoTest.mismaClaveEnCadaEjecucion`, `RACIT.mismaClaveEnElReintento` |
| R25 | `RACIT.sinSesion` |
| R26 | `ReembolsoAdminServiceTest.rolDistintoDeAdmin`, `RACIT.sinRolAdmin` |
| R27 | `RACIT.ipNoPermitida` |
| R28 | `ReembolsoAdminServiceTest.porAtenderUsaElMargen`, `RACIT.listadoPorAtender`, `RACIT.listadoVacio` |
| R29 | `RACIT.listadoPorAtender` |
| R30 | `ReembolsoAdminServiceTest` (comando `ADMIN`), `RACIT.fallaYElAdminLoReintenta` |
| R31 | `ReembolsoAdminServiceTest.reintentoQueVuelveAFallar`, `RACIT.elReintentoVuelveAFallar` |
| R32 | `ReglasPagoTest.idBienEscritoDeReembolso`, `idMalEscritoDeReembolso`, `ReembolsoAdminServiceTest.idMalEscrito`, `inexistente`, `RACIT.idNoEncontrado` |
| R33 | `ReembolsoAdminServiceTest.yaReembolsado`, `RACIT.yaReembolsado` |
| R34 | `ReembolsoAdminServiceTest.enProceso`, `RACIT.enProceso` |
| R35 | `ReembolsoAdminServiceTest` (comando con id e IP del admin), `RACIT.ignoraElCuerpo` |
| R36 | `REGIT.registrarExito`, `RAIT.auditaLaSolicitudYElResultado`, `RACIT.fallaYElAdminLoReintenta` |
| R37 | `REGIT.registrarFallo`, `RACIT.fallaYElAdminLoReintenta` |
| R38 | `SolicitudReembolsoTest.guardaQuienYComo`, `REGIT.registrarExito`, `REGIT.registrarFallo`, `RAIT.auditaLaSolicitudYElResultado`, `RACIT.fallaYElAdminLoReintenta` |
| R39 | `RACIT.sinSesion`, `sinRolAdmin`, `ipNoPermitida`, `idNoEncontrado`, `yaReembolsado`, `enProceso` |
| R40 | `PagoServiceImplTest.entregaElComandoParaDespuesDelCommit`, `ReembolsoAdminServiceTest`, `EjecutorReembolsosTest.ahoraDevuelveElResultado`, `SolicitudReembolsoTest`, `ArquitecturaReembolsoTest.soloElComandoConoceLaPasarela` |
| R41 | `ArquitecturaReembolsoTest.sinTiposDelProveedor` |
| R42 | `RRIT.filasInvalidas`, `RRIT.filasValidas` |
| R43 | `PasarelaSimuladaTest.noReembolsaLoQueNoEmitio` |

Regresión HU-13, HU-14 y HU-15: todas sus IT y unitarias en verde en el `./init.sh` de arriba.

## Archivos

**Backend `src/main` (N):** `pagos/domain/{Reembolso, EstadoReembolso, OrigenReembolso, MotivoNoReintentable,
ReembolsoPorAtender}.java`; `pagos/repository/ReembolsoRepository.java`; `pagos/service/reembolso/{ComandoReembolso,
SolicitudReembolso, SolicitudesReembolso, EjecutorReembolsos, RegistroReembolsos, Disparo, ResultadoReembolso,
IntentoReembolso}.java`; `pagos/service/pasarela/{SolicitudReembolsoPasarela, ReembolsoRealizado}.java`;
`pagos/service/ReembolsoAdminService.java`; `pagos/dto/ReembolsoAdminDTO.java`;
`pagos/controller/ReembolsoAdminController.java`; `resources/db/migration/V4_3__reembolso.sql`.
**Backend `src/main` (M):** `pagos/domain/{Pago, ReglasPago}.java`; `pagos/service/PagoService.java`;
`pagos/service/impl/PagoServiceImpl.java`; `pagos/service/pasarela/{PasarelaPago, MercadoPagoAdapter,
PasarelaSimulada}.java`; `resources/application.properties`; `2_Backend/.env.example`.
**Tests (N):** `pagos/controller/{ReembolsosSoporte, ReembolsoAutomaticoIT, ReembolsoAdminControllerIT,
ReembolsoConcurrenciaIT, ReembolsoRestriccionesIT, RegistroReembolsosIT}.java`; `pagos/domain/{ReembolsoTest,
PagoTest}.java`; `pagos/service/ReembolsoAdminServiceTest.java`; `pagos/service/reembolso/{SolicitudReembolsoTest,
EjecutorReembolsosTest, ArquitecturaReembolsoTest}.java`.
**Tests (M):** ver la tabla "Tests existentes que cambiaron".
**Frontend (N):** `3_frontend/src/pages/pagos/ReembolsosPorAtender.jsx`, `ReembolsosPorAtender.module.css`.
**Frontend (M):** `components/ui/EtiquetaEstado.jsx`, `api/pagos.js`, `pages/pagos/textosPagos.js`, `App.jsx`,
`pages/cuentas/Panel.jsx`.
**Docs (M):** `docs/architecture.md`, `docs/modelo-datos.md`, `docs/frontend.md`. **Progreso:** `progress/HU-16.md`,
`specs/HU-16/tasks.md`.

No se tocó la tabla "Puertos entre módulos", `comun`, ni la matriz §7 de `docs/requerimientos.md`. Sin commit ni push.

## Caja negra de UAT (design § 13)

Backend con `PAGOS_PASARELA=simulada`.

1. Con `PAGOS_SIMULADA_FALLOS_REEMBOLSO=1`:
   a. Un usuario reserva a ≥ 48 h, paga con "Aprobar pago simulado" y cancela desde "Mis reservas" → 200 y el mensaje de
      HU-15 "El reembolso de S/ X está en proceso.".
   b. Un admin (desde una IP de `ADMIN_IPS_PERMITIDAS`) abre Panel → "Reembolsos por atender" → ve "Reserva #N" con
      "Reembolso fallido", el monto, "Origen: Cancelación de la reserva" e "Intentos: 1".
   c. "Reintentar" → modal "¿Reintentar el reembolso?" → "Reintentar" → `Alerta` "Reembolso de S/ X realizado." y la
      lista queda vacía ("No hay reembolsos por atender.").
2. Con `PAGOS_SIMULADA_FALLOS_REEMBOLSO=0` (por defecto): repetir 1a → el reembolso no aparece nunca en el panel (CA1) y
   el log del backend muestra "Reembolso simulado: no se llamó a ninguna pasarela".
3. Un usuario o arrendador que escribe `/admin/reembolsos` vuelve a `/panel`.

## Tasks no completadas

Ninguna.

## Adenda AC005 (2026-11-16)

Implementa T25–T26 (`requirements.md` "Adenda 2026-11-16", `design.md` § 15), aprobada por Ariel Enriquez (incluido que
los 400 tardíos pueden decir *ya reembolsado* además de *en proceso*). Agente: implementer.

### Alcance

- **Único archivo de código tocado:** `2_Backend/src/test/java/pe/edu/ulima/espacioya/pagos/controller/ReembolsoConcurrenciaIT.java`.
  Solo se agregaron: un método `@Test`, las constantes `DIEZ` (10) y `LOG` (SLF4J, para la evidencia por ronda), tres
  ayudantes privados (`mensajesDeLosRechazos`, `reembolsosDelPagoYOrigen`, `totalAuditorias`) y los imports `Map`,
  `Logger` y `LoggerFactory`.
- `dosReintentosALaVez` y `aLaVez(...)` no se modificaron. `ReembolsosSoporte`, `PagosSoporte` y `2_Backend/src/main` no
  se tocaron. **Ningún ajuste de configuración de prueba**: pool de Hikari por defecto, `SEGUNDOS_MAXIMOS` = 30 s sin
  cambio, todas las solicitudes desde `IP_ADMIN`.

### Método agregado

**`diezReintentosALaVez`** — `@DisplayName("HU-16 R22, R23 (AC005): diez reintentos a la vez de un FALLIDO llaman una vez a la pasarela, responden un 200 y nueve 400, y dejan un solo reembolso")`
(el texto de `design.md` § 15.1; en el código va partido en dos literales concatenados por el largo de línea).

- **10 `POST /api/admin/reembolsos/{id}/reintento` simultáneos** por ronda con una sola sesión de admin (`tokenAdmin()`),
  sobre el mismo reembolso `FALLIDO` (`intentos = 1`) sembrado con `fallidoSembrado()`. **5 rondas** (`RONDAS`), cada una
  con `clearInvocations(pasarela)` y un `FALLIDO` nuevo.
- **Latch:** el mismo patrón que `dosReintentosALaVez` y que fija design § 15.1: un `CountDownLatch(1)` que se libera con
  la **primera** respuesta, y el `doAnswer` sobre `pasarela.reembolsar` lo espera antes de `callRealMethod()`. Se descartó
  esperar las 9 respuestas: obligaría a que los nueve 400 fueran *en proceso* (alternativa 13, descartada en § 15.4) y
  la prueba ya no mediría el rechazo tardío *ya reembolsado*. Con el latch de 1 el resultado no depende del orden: el
  primer 400 siempre es *en proceso* y los demás pueden ser de cualquiera de los dos tipos.
- Afirma por ronda (aserciones 1–6 de la adenda):
  1. `verify(pasarela, times(1)).reembolsar(any())` (R23);
  2. exactamente **1** respuesta 200 y **9** respuestas 400 (R30, R23);
  3. cada 400 trae `MENSAJE_EN_PROCESO` o `MENSAJE_YA_REEMBOLSADO` y ningún otro, y al menos uno es *en proceso*
     (R22, R34; R21, R33);
  4. `count(*)` de `reembolso` con el `pago_id` y el `origen` de la fila sembrada = **1** (R19);
  5. `intentos` = 2 y estado `REEMBOLSADO`;
  6. `auditorias("REEMBOLSO_REALIZADO")` tiene `ronda + 1` filas (igual que la prueba existente; la auditoría se limpia
     por test, no por ronda) (R36), y además el total de filas de `auditoria` sube **exactamente en 1** en la ronda, así
     que los nueve 400 no registraron nada (R39).
- Cubre R22, R23 (AC005); R19, R21, R30, R33, R34, R36, R39.

### Corridas

Comando: `./2_Backend/mvnw -f 2_Backend/pom.xml verify -Dit.test=ReembolsoConcurrenciaIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`
(en la corrida 3 se agregó `-q` solo para acortar la consola; no cambia lo que se ejecuta). Resultado copiado de
`2_Backend/target/failsafe-reports/pe.edu.ulima.espacioya.pagos.controller.ReembolsoConcurrenciaIT.txt` y tiempo del
método del `TEST-…ReembolsoConcurrenciaIT.xml`. Hora de Lima (-05:00), 2026-11-16. Tres corridas seguidas, sin cambios
entre ellas.

| Corrida | Inicio | Resultado (literal) | `diezReintentosALaVez` (5 rondas) |
|---------|--------|---------------------|-----------------------------------|
| 1 | 22:04:03 | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 22.73 s -- in pe.edu.ulima.espacioya.pagos.controller.ReembolsoConcurrenciaIT` | 0.891 s |
| 2 | 22:04:50 | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 18.33 s -- in pe.edu.ulima.espacioya.pagos.controller.ReembolsoConcurrenciaIT` | 0.776 s |
| 3 | 22:05:30 | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 16.07 s -- in pe.edu.ulima.espacioya.pagos.controller.ReembolsoConcurrenciaIT` | 0.673 s |

Tres corridas seguidas en verde (150 reintentos en total: 3 corridas × 5 rondas × 10). Sin fallos intermitentes, así que
no hicieron falta las 5 corridas extra.

**Rechazos por tipo** (línea `AC005 ronda n: en proceso = x, ya reembolsado = y` que la prueba registra con `LOG.info`):

| Corrida | Ronda 1 | Ronda 2 | Ronda 3 | Ronda 4 | Ronda 5 |
|---------|---------|---------|---------|---------|---------|
| 1 | 5 / 4 | 5 / 4 | 9 / 0 | 9 / 0 | 9 / 0 |
| 2 | 9 / 0 | 9 / 0 | 9 / 0 | 9 / 0 | 9 / 0 |
| 3 | 9 / 0 | 9 / 0 | 9 / 0 | 9 / 0 | 9 / 0 |

(*en proceso* / *ya reembolsado*). En 13 de 15 rondas los nueve rechazos fueron *en proceso*. En las rondas 1 y 2 de la
corrida 1, 4 solicitudes llegaron después de que el ganador terminó y recibieron *ya reembolsado*: así se ejercitó la
rama tardía (R21, R33) que la adenda acepta.

**`./init.sh` completo:** no se corrió en esta sesión, por instrucción del orquestador; queda para el reviewer (es la
parte final de T26).

### Límites y hallazgos

- **Pool de conexiones:** con 10 hilos y el pool de Hikari por defecto (10) no hubo timeouts. Cada ronda tarda unos
  55–80 ms, salvo la primera de cada corrida (unos 500 ms, arranque en frío). No se cambió configuración.
- **Retención del ganador:** el `doAnswer` nunca agotó los 30 s; la primera respuesta llegó siempre en milisegundos.
- **Límites por IP / filtro de IPs del admin:** ninguno interfirió (hoy ninguna `ReglaLimite` aplica a las rutas del
  admin).
- **Simultaneidad:** `aLaVez` suelta los 10 hilos con un mismo `CountDownLatch`, pero no mide si llegaron a la vez al
  bloqueo de la fila; los conteos por tipo de rechazo muestran que en la mayoría de las rondas los nueve perdedores
  compitieron mientras el ganador seguía en la pasarela.
- **Defectos:** ninguno. No se registró `DEF-xx`.
