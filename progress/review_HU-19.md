# Review — HU-19

**Veredicto:** APPROVED (con observaciones no bloqueantes)

`./init.sh` completo (sin `--rapido`, Docker activo) ejecutado por el revisor el 2026-11-04: **exit 0**,
`[OK] Entorno listo`. Backend `./mvnw verify`: 1358 unitarias + 794 de integración, 0 fallos, 0 errores, 0 omitidas.
ESLint sin errores ni advertencias; `npm run build` OK; reglas de arquitectura, colores, librerías y secretos OK.
Único `[WARN]`: faltan `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA`, `ALMACENAMIENTO_LLAVE_FIRMA` para `spring-boot:run`
local (entorno de la máquina; no afecta pruebas ni a HU-19).

Defectos bloqueantes (DEF-n): **ninguno**.

## Trazabilidad requirements ↔ tests

Todos los R1–R48 tienen al menos un `@DisplayName("HU-19 R<n>: …")` que comprueba un resultado concreto (código HTTP,
campos del DTO, fila en BD, detalle exacto de auditoría, mensaje exacto). Revisados uno por uno:

- R1: [x] `LiquidacionAdminControllerIT.sinSesion` (4 rutas, sin token y con token inventado → 401; 0 filas en `liquidacion_detalle` y `auditoria`)
- R2: [x] `LiquidacionAdminControllerIT.sinRolAdmin` (USUARIO y ARRENDADOR → 403 en las 4 rutas, nada cambia), `LiquidacionAdminServiceTest.soloElAdmin` (`verifyNoInteractions` de repositorios, cuentas y auditoría)
- R3: [x] `LiquidacionAdminControllerIT.ipNoPermitida`
- R4: [x] `MisLiquidacionesControllerIT.sinSesion`
- R5: [x] `MisLiquidacionesControllerIT.otroRol` (`pagina=abc` → 403, no 400), `MisLiquidacionesServiceTest.soloElArrendador`
- R6: [x] `MisLiquidacionesControllerIT.soloLasDeLaSesion` (`?arrendadorId=` ajeno ignorado), `MisLiquidacionesServiceTest.soloLasSuyas`
- R7: [x] `LiquidacionAdminControllerIT.ca1GeneraAlPasar48Horas`, `GeneradorLiquidacionesTest.unaPorArrendador`, `ReservasLiquidablesImplTest.*`, `CatalogoEspaciosServiceImplTest.arrendadoresDe*`
- R8: [x] `LiquidacionAdminControllerIT.ca1GeneraAlPasar48Horas` (fin + 48 h − 1 s → vacío; fin + 48 h → 1), `ReglasLiquidacionTest.bordeDe48Horas`, `GeneradorLiquidacionesTest.bordeDe48Horas`, `ReservaConHorarioTest.instanteDeFin*`
- R9: [x] `LiquidacionRepositoryIT.pagadosSinLiquidar` (reembolso PENDIENTE, RECHAZADO, PAGADO_TARDIO, MONTO_DISTINTO, REEMBOLSADO y REEMBOLSO_FALLIDO excluidos)
- R10: [x] `LiquidacionCancelacionIT.canceladas`, `.pendientesYExpiradasFuera`, `ReservaTest.admiteLiquidacionSegunElEstado`, `.canceladaConReembolsoNoAdmiteLiquidacion`, `ReservasLiquidablesImplTest.estadosNoAdmitidos`, `GeneradorLiquidacionesTest.excluyeNoAdmitidas`
- R11: [x] `LiquidacionCancelacionIT.canceladas` (cancelación real de HU-15 con 0 % → línea `cancelada=true`), `ReservaTest.canceladaSinReembolsoAdmiteLiquidacion`, `GeneradorLiquidacionesTest.incluyeCanceladaSinReembolso`
- R12: [x] `LiquidacionAdminControllerIT.segundaGeneracionVacia`, `LiquidacionCancelacionIT.reservasNuevasEnOtraLiquidacion`, `LiquidacionRepositoryIT.unaReservaYUnPagoUnaSolaVez`
- R13: [x] `LiquidacionAdminControllerIT.ca1GeneraAlPasar48Horas` (241.50 / 24.15 / 217.35 en API y BD), `GeneradorLiquidacionesTest.calculaLosMontos` (322.05 → 32.21 HALF_UP), `ReglasLiquidacionTest.comisionDel10PorCiento`, `LiquidacionTest.*`
- R14: [x] `LiquidacionAdminControllerIT.ignoraElCuerpo` (cuerpo con montos, arrendador, estado y corte falsos → mismo resultado), `LiquidacionAdminServiceTest.generarSoloConDatosDelServidor`
- R15: [x] `LiquidacionAdminControllerIT.cuentaRetenida` (nace `GENERADA` con la cuenta retenida tras un `PUT` real de HU-18), `GeneradorLiquidacionesTest.conCuentaRetenidaGenerada`
- R16: [x] `LiquidacionAdminControllerIT.ca3PendienteDeCuenta`, `GeneradorLiquidacionesTest.sinCuentaPendiente`
- R17: [x] `LiquidacionCancelacionIT.reservasNuevasEnOtraLiquidacion` (fila y líneas de la vieja idénticas), `GeneradorLiquidacionesTest.soloCreaNuevas`
- R18: [x] `LiquidacionAdminControllerIT.segundaGeneracionVacia`, `GeneradorLiquidacionesTest.sinPendientes`, `.ningunaMadura`, `LiquidacionAdminServiceTest.generarSinNada`
- R19: [x] `LiquidacionAdminControllerIT.ca1GeneraAlPasar48Horas`, `LiquidacionAdminServiceTest.generarRespondeLoCreado` ("Arrendador #8" por defecto)
- R20: [x] `LiquidacionAdminControllerIT.ca1GeneraAlPasar48Horas` (`simulada` en liquidación y línea), `GeneradorLiquidacionesTest.marcaLasSimuladas`
- R21: [x] `LiquidacionRepositoryIT.pagadosSinLiquidar` (con `false`), `GeneradorLiquidacionesTest.sinLaSimuladaExcluyeSimulados`
- R22: [x] `LiquidacionConcurrenciaIT.dosGeneracionesALaVez` (2 hilos + `CountDownLatch`, 3 rondas: ambas responden, 6 reservas una sola vez, 2 liquidaciones), `LiquidacionRepositoryIT.bloqueoConsultivo` (`pg_locks`), `GeneradorLiquidacionesTest.bloqueaAntesDelCorte` (`InOrder`), `LiquidacionAdminServiceTest.traduceLaGeneracionCruzada`
- R23: [x] `LiquidacionRepositoryIT.unaReservaYUnPagoUnaSolaVez`, `.restriccionesDeLaTabla` (cada CHECK por nombre), `.exigeElPerfil`
- R24: [x] `LiquidacionAdminControllerIT.suspendidoSeLiquidaConAvisos`, `GeneradorLiquidacionesTest.avisosNoFiltran`
- R25: [x] `LiquidacionAdminControllerIT.ca2MarcaPagada` (operación, fecha del reloj, admin, `banco_destino`, `****1234`), `LiquidacionAdminServiceTest.marcarGenerada`, `.ordenDeBloqueos`
- R26: [x] `LiquidacionAdminControllerIT.ca3PendienteDeCuenta`, `LiquidacionAdminServiceTest.marcarPendienteDeCuenta`
- R27: [x] `LiquidacionAdminControllerIT.ca3PendienteDeCuenta` (400 con mensaje exacto, estado intacto, 0 auditorías), `LiquidacionAdminServiceTest.marcarSinCuenta`
- R28: [x] `LiquidacionAdminControllerIT.cuentaRetenida` (mensaje con `retenerHasta` en Lima; a las 72 h exactas se puede pagar), `LiquidacionAdminServiceTest.marcarRetenida`
- R29: [x] `LiquidacionAdminControllerIT.yaPagada` (fila idéntica), `LiquidacionAdminServiceTest.marcarYaPagada`
- R30: [x] `LiquidacionAdminControllerIT.numeroDeOperacionInvalido` (5 cuerpos inválidos, fila y auditoría intactas), `MarcarLiquidacionPagadaDTOTest.*`
- R31: [x] `LiquidacionAdminControllerIT.idNoEncontrado`, `LiquidacionAdminServiceTest.idMalEscrito`, `.idInexistente`
- R32: [x] `LiquidacionConcurrenciaIT.dosMarcadosALaVez` (uno gana, el otro recibe el mensaje de ya pagada, queda la operación del ganador; 3 rondas)
- R33: [x] `LiquidacionAdminControllerIT.listaPorPagar` (21 filas: 20 + 1, orden, `hayMas`), `LiquidacionAdminServiceTest.listarPorPagar`, `.listarPrimeraPagina`
- R34: [x] `LiquidacionAdminControllerIT.listaPagadas`, `LiquidacionAdminServiceTest.listarPagadas`
- R35: [x] `LiquidacionAdminControllerIT.parametrosInvalidos` (2 errores a la vez), `LiquidacionAdminServiceTest.listarParametrosInvalidos`, `.listarGrupoInvalido`
- R36: [x] `LiquidacionAdminServiceTest.detallePagable` (líneas completas), `.detalleRetenido`, `.detalleSinCuentaYPagada`, `LiquidacionCancelacionIT.canceladas` (líneas por la API)
- R37: [x] `LiquidacionAdminControllerIT.listaPorPagar`, `.suspendidoSeLiquidaConAvisos`, `LiquidacionAdminServiceTest.listarConAvisos`, `CuentaServiceImplTest.sinIdentidadVerificada*`
- R38: [x] `LiquidacionAdminControllerIT.ca3PendienteDeCuenta` (`numeroCompleto` solo cuando es pagable), `LiquidacionAdminServiceTest.detallePagable`, `CuentaBancariaTest.numeroParaTransferir`
- R39: [x] `LiquidacionAdminControllerIT.ca3PendienteDeCuenta` (usuario, IP, entidad `cuenta_bancaria`, id y detalle exactos), `LiquidacionAdminServiceTest.detallePagableAudita`
- R40: [x] `LiquidacionAdminControllerIT.ca2MarcaPagada` (respuesta del pago, detalle `PAGADA`, lista `pagadas` y toda la tabla `auditoria` sin el número), `.cuentaRetenida`, `.listaPorPagar`, `LiquidacionAdminServiceTest.toStringSinNumero`. "Ni en los logs": verificado por inspección (ver O2)
- R41: [x] `LiquidacionAdminControllerIT.noStore` (incluidos 400 y 404), `MisLiquidacionesControllerIT.noStore`
- R42: [x] `MisLiquidacionesControllerIT.ca2VeLaPagada`, `.ca3VeLaPendiente`, `.paginaDeVeinte`, `MisLiquidacionesServiceTest.contenido`
- R43: [x] `MisLiquidacionesControllerIT.paginaInvalida`, `MisLiquidacionesServiceTest.paginaInvalida`
- R44: [x] `MisLiquidacionesControllerIT.ca2VeLaPagada` (`containsOnlyKeys` del DTO y de la línea; sin correo, `4242`, `sim-`, `generadaPor` ni `pagadaPor`), `MisLiquidacionesServiceTest.sinDatosAjenos`
- R45: [x] `LiquidacionAdminControllerIT.ca1GeneraAlPasar48Horas` (detalle exacto con `simulada=true`), `.suspendidoSeLiquidaConAvisos`, `GeneradorLiquidacionesTest.auditaLaGeneracion`, `.avisosNoFiltran`
- R46: [x] `LiquidacionAdminControllerIT.ca2MarcaPagada` (detalle exacto), `LiquidacionAdminServiceTest.marcarAudita`
- R47: [x] `MisLiquidacionesServiceTest.pendientesParaHu22`, `LiquidacionRepositoryIT.delArrendador` (ver O3)
- R48: [x] `LiquidacionAdminServiceTest.sinCorreo` (ningún campo `NotificadorCorreo` en los cuatro servicios; ver O9)

## Trazabilidad con la historia

- CA1: [x] R7, R8, R9, R10, R12, R13, R15, R17, R18, R19, R22, R23 — implementado en `GeneradorLiquidaciones` y `PagoRepository.pagadosSinLiquidar`
- CA2: [x] R25, R26, R29, R30, R32, R42 — `LiquidacionAdminService.marcarPagada`, `MisLiquidacionesService.listar`
- CA3: [x] R16, R26, R27, R42
- (T) montos solo en servidor: [x] R13, R14, R22, R23, R32 — `Liquidacion.generar` con `ReglasLiquidacion.comision`; `POST` sin `@RequestBody`; `ck_liquidacion_montos`; bloqueo consultivo y `uq_liquidacion_detalle_*`; `findParaActualizar` (`PESSIMISTIC_WRITE`)
- (E) solo el admin genera y marca: [x] R1, R2, R3, R5, R6, R31 — `FiltroIpsAdmin` (`/api/admin/**`, antes de autenticar), sesión admin solo con MFA (HU-17) y `exigirAdmin` como primera sentencia de las cuatro operaciones (`LiquidacionAdminService.java:93,111,138,159`). `ConfiguracionSeguridad` no exige rol por ruta, igual que el resto del panel admin
- (R) registro de cada liquidación y cambio de estado: [x] R39, R45, R46 — en la misma transacción (`GeneradorLiquidaciones.java:123`, `LiquidacionAdminService.java:145,172`)
- Simplificación (botón, transferencia manual, sin job ni API bancaria): [x] sin `@Scheduled` ni llamada externa
- D1: [x] todo lo pendiente a un solo corte; `periodo` informativo
- D2: [x] `sim-` solo con `espacioya.pagos.pasarela=simulada` (`GeneradorLiquidaciones.java:68` y el JPQL de `pagadosSinLiquidar`), marcados en línea, liquidación, API, UI y auditoría
- D3: [x] `Reserva.admiteLiquidacion()` (CONFIRMADA, o CANCELADA por USUARIO con `monto_reembolso = 0`); pendiente de confirmación del PO (ver O7)
- D4: [x] número completo solo en `GET /{id}` pagable, auditado y `no-store`; enmascarado en el resto; `CuentaDestinoDTO.toString` lo omite
- D5: [x] `MotivoNoPagable.CUENTA_RETENIDA` → 400 con `retener_hasta` en Lima; el detalle no revela el número mientras está retenida
- D6: [x] avisos calculados al leer, no filtran; los del momento de la generación quedan en la auditoría
- D7: [x] `PENDIENTE_CUENTA → PAGADA` revalidando la cuenta bajo bloqueo; reservas nuevas → liquidación nueva
- D8: [x] sin correos; `MisLiquidacionesService.tieneLiquidacionesPendientes`

## Tasks completas

- T1–T29: [x] todas marcadas en `specs/HU-19/tasks.md` y comprobadas en el código y las pruebas (T29 confirmada por el revisor con `./init.sh` completo).

## Revisión de código (puntos pedidos por el orquestador)

- **Dos generaciones a la vez:** la primera sentencia es `pg_advisory_xact_lock(190019)` (nativa, `MANDATORY`) y el corte se toma después (`GeneradorLiquidaciones.java:77-79`). La base corre en `READ COMMITTED` (la IT de 3 rondas lo confirma: la segunda generación no choca). `uq_liquidacion_detalle_reserva` y `uq_liquidacion_detalle_pago` existen (`V5_3__liquidacion.sql:44-45`), y la violación de la primera se traduce a 400 con rollback completo (`LiquidacionAdminService.java:97-103`).
- **Montos (STRIDE T):** se calculan en `Liquidacion.generar` y los fijan los `CHECK`. En la generación el cliente no envía nada; al pagar envía solo `numeroOperacion`.
- **Número de cuenta:** el único acceso es `CuentaBancaria.numeroParaTransferir()`, y solo lo usa `VistaLiquidaciones.cuentaDestino` con `conNumeroCompleto && pagable`. Nunca sale en listas, generación, respuesta del pago, detalle `PAGADA` o no pagable, rutas del arrendador, auditoría (el snapshot y el detalle usan `enmascarado()`) ni `toString`.
- **Módulos de otros dueños:** los cambios se limitan a lo de `design.md` § 14: `CatalogoEspaciosService.arrendadoresDe` (más su impl), `CuentaService.arrendadoresSinIdentidadVerificada` (más su impl y `PerfilArrendadorRepository.idsConIdentidadVerificada`), `Reserva.admiteLiquidacion`, `ReservaConHorario.instanteDeFin`, `CuentaBancaria.numeroParaTransferir`, y `ReglasPago.PREFIJO_SIMULADO` con su uso en `PasarelaSimulada` (sin cambio de conducta). Solo se agregan métodos; ninguna firma existente cambia, y pasan las 2152 pruebas, previas y nuevas. `pagos` no importa `reservas`, `espacios`, `busqueda` ni `administracion`.
- **Arquitectura y convenciones:** DTO `record`, inyección por constructor, migración nueva `V5_3__liquidacion.sql` (no se edita ninguna anterior), puerto `pagos.ReservasLiquidables` documentado en la tabla de `docs/architecture.md`, y docs (`architecture.md`, `modelo-datos.md`, `frontend.md`) actualizados.
- **Frontend:** las pantallas `/admin/liquidaciones`, `/admin/liquidaciones/:id` y `/panel/liquidaciones` coinciden con `design.md` § 15. Usan solo el kit (`Tarjeta`, `Boton`, `Modal`, `Campo`, `Alerta`, `EtiquetaEstado`), con un solo `Boton primario` por pantalla, solo tokens y sin estilos en línea, y llaman a la API solo desde `src/api/pagos.js`. Estados `GENERADA`/`PENDIENTE_CUENTA` en atención y `PAGADA` en éxito, según `docs/frontend.md`.

## Checkpoints

- C1: [x] arnés completo; `./init.sh` sin `--rapido` termina con exit 0.
- C2: [x] una sola `in_progress` de Eduardo Saldaña; `progress/HU-19.md` existe y registra la aprobación del spec (2026-11-04, con S1–S15). El punto "Toda historia `done`…" es n/a.
- C3: [x] módulos y capas correctos; sin controller→repository; sin `@Autowired`, Lombok, imports con comodín ni `println`; puertos e interfaces en su sitio; DTO sin campos sensibles; identidad de la sesión; migración Flyway nueva; pasarela detrás de un Adapter; nada fuera del alcance; sin secretos; colores solo en tokens; pantallas con el kit.
- C4: [x] `*Test` (Mockito, `Clock` fijo) y `*IT` (Testcontainers, `RelojControlable`); 48 h y 72 h con reloj controlado; concurrencia con dos hilos a la vez; caminos de error con resultado concreto; verify, lint y build verdes.
- C5: n/a para el revisor / pendiente del dueño: el trabajo está en `feature/HU-19-liquidacion-semanal` (correcto) y no hay archivos sospechosos sin trackear. Al cerrar faltan la entrada en `progress/history.md`, devolver `progress/HU-19.md` a la plantilla y el estado final en `feature_list.json`.
- C6: [x] `specs/HU-19/` completo; EARS con tabla de trazabilidad; design con endpoints, clases, migración, secuencias y alternativas A–H; mapa R↔test en `progress/impl_HU-19.md`; ningún pendiente de `docs/requerimientos.md` §9 resuelto sin el dueño.

## Cambios requeridos

Ninguno bloqueante.
