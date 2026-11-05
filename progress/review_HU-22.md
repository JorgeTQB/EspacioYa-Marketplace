# Review — HU-22

**Veredicto:** APPROVED (vuelta 2, 2026-11-05). La vuelta 1 fue CHANGES_REQUESTED por C4; se conserva abajo como historial.

Revisado el 2026-11-05 sobre `feature/HU-22-derechos-datos-personales` contra `develop` (18 archivos tracked modificados y
archivos nuevos sin trackear). `./init.sh` completo (sin `--rapido`) ejecutado por el reviewer: **exit 0**, 1562
unitarias + 925 IT, 0 fallos; arquitectura, ESLint y build OK.

Motivo único del rechazo: **C4, concurrencia sin probar.** R42, R43 y R44 son reglas de concurrencia (bloqueos para que
una eliminación no pueda pasar por encima de una reserva o una liquidación que se crea al mismo tiempo) y solo están
cubiertas por `InOrder` sobre mocks. No hay ninguna prueba que lance las dos operaciones a la vez contra PostgreSQL
(`docs/verification.md` § Nivel 2, `CHECKPOINTS.md` C4). Las historias anteriores con reglas de bloqueo sí la tienen:
`LiquidacionConcurrenciaIT` (HU-19), `FacturacionControllerIT`/`CambioCorreoControllerIT` (HU-20) y
`BajaEspacioConcurrenciaIT` (HU-21). Todo lo demás está correcto.

## Trazabilidad requirements ↔ tests
- R1: [x] `DatosPersonalesServiceTest.exportaUsuario` (:224), `DatosPersonalesControllerIT.usuarioExporta` (:101)
- R2: [x] `DatosPersonalesServiceTest.exportaArrendador` (:243), `DatosPersonalesControllerIT.arrendadorExporta` (:143)
- R3: [x] `DatosPersonalesServiceTest.perfilExportado` (:260): los 8 campos y el consentimiento
- R4: [x] `DatosPersonalesServiceTest.facturacionEnmascarada` (:281) `****5678`; IT `arrendadorExporta` `****0970`
- R5: [x] `DatosPersonalesReservasImplTest.exportaReservasDelUsuario` (:138): orden, estado leído (EXPIRADA), cancelación
- R6: [x] `DatosPersonalesReservasImplTest.exportaPagosDelUsuario` (:168), `PagoServiceImplTest.pagosDeReservasConReembolsos` (:255), `.pagosDeReservasVacio`
- R7: [x] `DatosPersonalesReservasImplTest.exportaReservasDelArrendador` (:184)
- R8: [x] `DatosPersonalesPagosImplTest.exportaLiquidaciones` (:115)
- R9: [x] `DatosPersonalesPagosImplTest.exportaCuentaEnmascarada` (:127), `.cuentaNullSinRegistro` (:142), IT `arrendadorSinCuenta` (:167)
- R10: [x] `DatosPersonalesControllerIT.usuarioExporta` (:108): encabezado exacto con la fecha de Lima
- R11: [x] IT `usuarioExporta` (:110), `arrendadorExporta` (:149), `usuarioEliminaSuCuenta` (:326)
- R12: [x] IT `soloDatosDelTitular` (:181), `DatosPersonalesReservasImplTest.soloDelTitular` (:197)
- R13: [x] IT `sinDatosProhibidos` (:201): busca en el cuerpo hash, RUC completo, número de cuenta, `v1:`, huella, secreto MFA, datos de quien reservó, `ultimos4` e ids de pasarela
- R14: [x] `DatosPersonalesServiceTest.auditaExportacion` (:298), IT `exportacionAuditada` (:125) con la fila completa
- R15: [x] `DatosPersonalesServiceTest.sinContrasena` (:327), IT `sinContrasena` (:229)
- R16: [x] `DatosPersonalesServiceTest.contrasenaIncorrecta` (:342), IT `contrasenaIncorrecta` (:245)
- R17: [x] `ReautenticacionPerfilTest.exportacionAuditaOperacion` / `.eliminacionAuditaOperacion`, IT `contrasenaIncorrecta` (:255)
- R18: [x] `DatosPersonalesServiceTest.cupoAgotado` (:357), IT `cupoAgotado` (:264) en las dos rutas
- R19: [x] `DatosPersonalesServiceTest.adminRechazado` (:372), IT `adminRechazado` (:280)
- R20: [x] `DatosPersonalesServiceTest.contrasenaIncorrectaNoRevelaImpedimentos` (:381), IT (:294) con un solo error
- R21: [x] `UsuarioAnonimizacionTest.quedaEliminada`, `DatosPersonalesServiceTest.eliminaCuenta` (:396), IT `usuarioEliminaSuCuenta` (:319)
- R22: [x] `UsuarioAnonimizacionTest.datosPersonalesNulos`, IT `usuarioEliminaSuCuenta` (:329-336) fila por fila
- R23: [x] `DatosPersonalesServiceTest.hashFueraDeLaTransaccion` (:419), IT (:338-340): bcrypt que no coincide con la contraseña anterior
- R24: [x] `DatosPersonalesServiceTest.fotoTrasElCommit` (:432), `.fotoNoSeBorraConRollback` (:442), IT (:341)
- R25: [x] `PerfilArrendadorAnonimizacionTest.anonimizaCampos`, IT `arrendadorEliminaSuCuenta` (:378-385), incluido el cifrado `v1:`
- R26: [x] `DatosPersonalesServiceTest.borraDocumentos` (:468), IT (:386-388)
- R27: [x] `DatosPersonalesPagosImplTest.borraCuentaBancaria` (:257), `.sinCuentaNoFalla`, IT (:389)
- R28: [x] `DatosPersonalesServiceTest.revocaSesionesAlFinal` (:484), IT `revocaSesionesTokensYMfa` (:356-359), incluida la sesión de la solicitud
- R29: [x] `DatosPersonalesServiceTest.anulaTokens` (:500), IT (:360-361). Ver desviación 1
- R30: [x] `DatosPersonalesServiceTest.borraMfa` (:510), IT (:362-364)
- R31: [x] IT `conservaRegistrosContables` (:396) compara las 7 tablas y la auditoría antes y después; `conservaPagosDelUsuario` (:425)
- R32: [x] IT `noIniciaSesion` (:439): cuerpo igual al 401 genérico
- R33: [x] IT `correoYDocumentoLibres` (:452), `correoDeUsuarioLibre` (:470)
- R34: [x] `DatosPersonalesServiceTest.auditaEliminacion` (:525), IT `eliminacionAuditada` (:485)
- R35: [x] `DatosPersonalesServiceTest.participanteQueFallaRevierte` (:535), IT `rollbackCompleto` (:503) con un participante `@Order(3)` que falla
- R36: [x] `DatosPersonalesReservasImplTest.pendienteVigenteImpide` / `.confirmadaFuturaImpide` / `.enCursoImpide`, IT `usuarioConReservaFutura` (:527), `usuarioConPendienteVigente` (:549)
- R37: [x] `DatosPersonalesReservasImplTest.arrendadorConReservaPorTerminar` (:261), IT (:564)
- R38: [x] `DatosPersonalesPagosImplTest.liquidacionPendiente` (:164), IT `arrendadorConLiquidacionPendiente` (:584), `pendienteCuentaImpidePagadaNo` (:600)
- R39: [x] `DatosPersonalesPagosImplTest.cobroPorLiquidar` (:187), `.cobrosQueNoCuentan`, IT (:616), `cobroDeOtroNoImpide` (:633)
- R40: [x] `DatosPersonalesServiceTest.impedimentosJuntos` (:550), IT `variosImpedimentos` (:646) en el orden reservas, liquidaciones, pagos
- R41: [x] `DatosPersonalesReservasImplTest.noImpiden` (:251), IT `reservasQueNoImpiden` (:667)
- R42: [x] (orden) `DatosPersonalesServiceTest.impedimentosBajoElBloqueo` (:577). Sin prueba concurrente ← C4
- R43: [x] (orden) `DatosPersonalesReservasImplTest.bloqueoAntesDeLeer` (:282), `.contratoDelPuerto`. Sin prueba concurrente ← C4
- R44: [x] (orden) `DatosPersonalesPagosImplTest.bloqueoPrimero` (:232), `.exigenTransaccion`. Sin prueba concurrente ← C4
- R45: [x] `DatosPersonalesServiceTest.todosLosParticipantes` (:591), `.seccionRepetida`; IT con un tercer participante registrado (`DatosPersonalesSoporte.Dobles`)

## Trazabilidad con la historia
- CA1: [x] R1–R11, R45
- CA2 (desactivar): [x] R21, R28, R32, R35
- CA2 (anonimizar): [x] R22–R27, R29, R30, R33, R45
- CA2 (conservar registros contables): [x] R31
- CA3: [x] R36–R44
- (S) Reautenticación: [x] R15, R16, R18, R19, R20. `ReautenticacionPerfil.exigir` con `EXPORTACION`/`ELIMINACION` y el mismo cupo `perfil|<id>`
- (I) Solo datos del titular: [x] R12, R13. La identidad sale de `@AuthenticationPrincipal`; ninguna ruta lleva id
- (R) Registro: [x] R14, R17, R34

## Tasks completas
- T1–T20: [x] todas marcadas y comprobadas en el código. Nota: T5 pide `CodigoRespaldoRepository.borrarDeUsuario`, que ya existía desde HU-17 (`CodigoRespaldoRepository.java:20`) y se reutiliza. Correcto.

## Desviaciones declaradas en `progress/impl_HU-22.md`
1. **R29, `TipoToken.values` en lugar de los tres tipos.** Aceptable. `TipoToken` solo tiene `RECUPERACION` y `CAMBIO_CORREO`; `VERIFICACION` está en `ck_token_tipo` (`V6_1`) pero ningún código lo crea, así que hoy no puede haber un token pendiente de ese tipo. Cuando HU-25 lo agregue al enum, `DatosPersonalesService.java:149` lo cubrirá solo. **Aviso para HU-25:** comprobar entonces que la eliminación lo anula.
2. **`perfiles.findParaActualizar` en lugar de `findById`** (`DatosPersonalesService.java:177`). Aceptable y mejor: sigue el orden `usuario → perfil_arrendador` de HU-20.
3. **Fecha del archivo desde `generadaEn` en Lima** (`DatosPersonalesController.java:48`). Aceptable: es el mismo reloj, la fecha de hoy en America/Lima, y coincide con el nombre que arma el frontend.
4. **Un solo `ahora` y corte en el primer espacio con reservas por terminar** (`DatosPersonalesReservasImpl.java:142-155`). Aceptable: si hay un impedimento, la transacción se revierte. Los espacios que no se leen tampoco se bloquean, y no hace falta.
5. **`ConfirmacionContrasenaDTO` sin `@NotBlank`.** Aceptable y necesario para el orden 403, luego 429, luego 400 (R18, R19). R15 se cumple en `ReautenticacionPerfil.java:56`.
6. **El arrendador usa `ReservaExportadaDTO` con `precioHora`, `creadaEn` y los datos de cancelación en null.** Aceptable: están los campos que pide R7 y no afecta a R13, porque no hay ningún campo de quien reservó (probado en la IT `sinDatosProhibidos`).

## Revisión por archivo (arquitectura, convenciones, design)
- El puerto está en `cuentas/service/puertos/` y en la tabla de `docs/architecture.md`; `reservas` y `pagos` lo implementan sin que `cuentas` los conozca (DIP/OCP). `reservas → pagos` solo pasa por `PagoService.pagosDeReservas`. No crea ciclos.
- DTO como `record`; `toString` con la contraseña enmascarada; inyección por constructor; sin migraciones (design § 11); solo excepciones existentes.
- Frontend (`DatosPersonales.jsx`): solo componentes del kit (`Tarjeta`, `Boton` secundario y peligro, `Modal` primario y `peligro-confirmar`, `Campo`, `Alerta`). Sin estilos en línea y solo tokens en el `.module.css`. Llama a la API solo desde `api/cuentas.js`. No agrega otro botón primario a la pantalla. Textos, enlaces y flujo coinciden con design § 14 y `docs/frontend.md`.
- Observación que no bloquea: `DatosPersonales.jsx:91-97` (`mostrarError`) supone que el error trae `errores`. Los errores de `peticion` siempre lo traen, pero si `descargarJson` (:112) lanza un error del navegador, `falla.errores.filter` da `TypeError`. Se puede proteger con un arreglo vacío por defecto.

## Checkpoints
- C1: [x] Arnés completo; `./init.sh` completo con exit 0 (lo ejecutó el reviewer).
- C2: [x] Una historia `in_progress` por dueño. `progress/HU-22.md` existe, describe la sesión activa y anota la aprobación de Kevin Chan (2026-11-04). n/a: tests de historias `done`.
- C3: [x] Módulos y paquetes correctos; el controller no importa repositorios; sin `@Autowired`, Lombok ni comodines. Los módulos se usan solo vía `service` o puertos, y el puerto está en la tabla. DTO `record` sin campos sensibles (R13 probado); identidad desde la sesión. Sin migraciones nuevas ni migraciones editadas; archivos vía `ArchivosPrivados`; nada fuera del alcance; sin secretos. Frontend con tokens, kit y `src/api/`.
- C4: [ ] `*Test`/`*IT` con Testcontainers, sin H2, con `RelojControlable`/`Clock.fixed` [x]; resultados concretos y caminos de error [x]; verify, lint y build verdes [x]; **las reglas de concurrencia (R42, R43, R44) no tienen una prueba que lance las dos operaciones a la vez** [ ].
- C5: [x] Sin archivos sospechosos sin trackear (`3_frontend/dist/` está ignorado); rama `feature/HU-22-…`; estado `in_progress` correcto mientras se revisa. n/a: entrada de `history.md` y vuelta a la plantilla (se hacen al cerrar).
- C6: [x] Carpeta `specs/HU-22/` completa; EARS con tabla de trazabilidad; el design tiene endpoints, clases, secuencias y alternativas; mapa R→test en `impl_HU-22.md`; el dueño decidió §9 punto 6 (D1). n/a: tasks `[x]` de historias `done`.

## Cambios requeridos
1. **Agregar una IT de concurrencia** (por ejemplo `cuentas/controller/DatosPersonalesConcurrenciaIT.java`, con dos hilos y `CountDownLatch` como en `docs/verification.md` y `BajaEspacioConcurrenciaIT`; varias rondas si el resultado depende del orden) con `@DisplayName("HU-22 R<n>: …")`:
   - **R43:** el arrendador elimina su cuenta mientras un usuario crea una reserva futura en su espacio. Resultado aceptable: o la eliminación da 400 `reservas` y la reserva existe, o la eliminación da 200 y la reserva da 404. Nunca una cuenta `ELIMINADA` con una reserva `PENDIENTE_PAGO`/`CONFIRMADA` por terminar en sus espacios.
   - **R44:** el arrendador elimina su cuenta mientras se generan las liquidaciones, con un cobro suyo por liquidar. Resultado aceptable: nunca una cuenta `ELIMINADA` (y sin `cuenta_bancaria`) con una liquidación `GENERADA`/`PENDIENTE_CUENTA` a su nombre.
   - **R42:** el usuario elimina su cuenta mientras crea una reserva propia (o dos eliminaciones de la misma cuenta a la vez). Resultado aceptable: nunca una cuenta `ELIMINADA` con una reserva futura vigente; con dos eliminaciones, una sola fila `CUENTA_ELIMINADA`.
2. Agregar esas pruebas al mapa R42–R44 de `progress/impl_HU-22.md` y volver a correr `./init.sh` completo.

---

# Vuelta 2 — 2026-11-05

**Veredicto:** APPROVED

`./init.sh` completo (sin `--rapido`) ejecutado por el reviewer: **exit 0**. 1562 unitarias + **931 IT** (925 + las 6
nuevas), 0 fallos, 0 errores, 0 omitidos; arquitectura, ESLint, build y coherencia de `feature_list.json`/specs/progress OK.

## 1. Nueva IT `cuentas/controller/DatosPersonalesConcurrenciaIT.java`
Hilos reales (`Executors` + `CountDownLatch` de salida, `get` con tope de 30 s), cada uno en su conexión a PostgreSQL
(Testcontainers), por la API real (MockMvc) salvo la generación de liquidaciones, que llama a `LiquidacionAdminService`
como lo hace el admin. 10 rondas con un retraso creciente (0–1080 ms, `PASO_MS = 120`, :44-45) para la segunda operación,
que cubre los dos bcrypt de la eliminación y hace salir los dos órdenes. Cada ronda usa cuentas, espacio e IP nuevos
(:59-63, :95-105), así que no se acumulan límites ni datos entre rondas; `DatosPersonalesSoporte.limpiar` vacía la base
antes de cada test.

- **R43** `arrendadorEliminaMientrasReservan` (:190-207): comprueba el invariante en cada ronda (`unaDeDosArrendador`,
  :173-188): o reserva 201 + eliminación 400 solo en `reservas` + cuenta `ACTIVA` + 1 reserva vigente + 0 auditorías, o
  eliminación 200 + reserva 404 + `ELIMINADA` + 0 reservas vigentes + 1 auditoría. El implementer registra los dos
  resultados (9 + 1). `arrendadorLosDosOrdenes` (:209-227) fija los dos órdenes en secuencia. [x]
- **R44** `arrendadorEliminaMientrasSeLiquida` (:231-263): con un cobro `PAGADO` por liquidar, la eliminación siempre
  debe dar 400 (`liquidaciones` o `pagos`), la cuenta sigue `ACTIVA`, la `cuenta_bancaria` sigue ahí y hay exactamente 1
  liquidación pendiente. La prueba sí detecta la carrera: sin el advisory lock compartido, con READ COMMITTED la
  eliminación podría leer "sin liquidaciones" antes del commit de la generación y "sin cobros por liquidar" después, y
  confirmar; el test fallaría. `liquidacionLosDosOrdenes` (:265-285) prueba los dos órdenes en secuencia. [x]
- **R42** `dosEliminacionesALaVez` (:332-355): exactamente un 200, la otra 400/401/500 (500 = `IllegalStateException`
  de design § 6.2 paso 1, ya especificado), cuenta `ELIMINADA` y una sola fila `CUENTA_ELIMINADA`. `usuarioLosDosOrdenes`
  (:310-330) prueba los dos órdenes deterministas (reserva primero → 400 `reservas`; eliminación primero → 401). Es la
  alternativa que se aceptó en la vuelta 1. [x]

**Estabilidad:** las aserciones aceptan los dos órdenes legítimos y solo fallan si se rompe el invariante, así que el
resultado no depende del orden de llegada. La carrera que no es determinista (usuario elimina mientras crea su reserva) se
excluye a propósito y con justificación (§ 15.1). No veo fuentes de inestabilidad: sin `sleep` en las aserciones,
datos aislados por ronda, tope de 30 s por hilo. Pasó en la ejecución del implementer y en la mía.

## 2. Riesgo aceptado por el dueño (fuera del spec)
Bien documentado. `specs/HU-22/design.md` § 15.1 (:535-558) explica qué pasa, cómo se comprobó, qué servicios siguen el
mismo patrón sin comprobar (HU-18 `CuentaBancariaService`, `PerfilService`, `FacturacionService`, `MfaService`,
`CambioCorreoService`), la ventana, por qué R43/R44 no se ven afectadas (`retenerSiLibres` relee `cuentaActiva` bajo el
bloqueo del espacio, `DisponibilidadServiceImpl.java:128-131`; advisory lock compartido en R44), cómo queda cubierta R42,
la decisión de Kevin Chan con fecha y la corrección propuesta para más adelante. Además está en el supuesto 10 de
`requirements.md` y en `progress/HU-22.md` § "Decisión del dueño (2026-11-05)" (:117-123). No bloquea.

## 3. Regresiones respecto a la vuelta 1
Ninguna. El conjunto de archivos es el mismo más la IT nueva; el implementer declara que no tocó código de producción y
la suite completa sigue verde. Trazabilidad R1–R45, CA/STRIDE y T1–T20 sin cambios respecto a la vuelta 1. Mapa R42–R44
actualizado en `progress/impl_HU-22.md` (:125-130).

## Trazabilidad R42–R44 (actualizada)
- R42: [x] `DatosPersonalesServiceTest.impedimentosBajoElBloqueo`, `DatosPersonalesConcurrenciaIT.dosEliminacionesALaVez`, `.usuarioLosDosOrdenes`
- R43: [x] `DatosPersonalesReservasImplTest.bloqueoAntesDeLeer`, `DatosPersonalesConcurrenciaIT.arrendadorEliminaMientrasReservan`, `.arrendadorLosDosOrdenes`
- R44: [x] `DatosPersonalesPagosImplTest.bloqueoPrimero`, `DatosPersonalesConcurrenciaIT.arrendadorEliminaMientrasSeLiquida`, `.liquidacionLosDosOrdenes`

## Checkpoints (vuelta 2)
- C1: [x] `./init.sh` completo con exit 0 (lo ejecutó el reviewer).
- C2: [x] Sin cambios; una sola historia `in_progress` del dueño; `progress/HU-22.md` al día con la decisión.
- C3: [x] Sin cambios de producción; las reglas de arquitectura siguen en verde.
- C4: [x] Ahora hay pruebas concurrentes reales contra PostgreSQL para R42, R43 y R44, además de las de orden con mocks.
- C5: [x] Sin archivos sospechosos sin trackear; rama correcta. n/a: `history.md` y vuelta a la plantilla (al cerrar).
- C6: [x] Specs completos; § 15.1 documenta el riesgo aceptado; mapa R→test actualizado.

## Observaciones que no bloquean (para el dueño antes del commit)
1. Hay comentarios que quedaron viejos tras la decisión del dueño: `DatosPersonalesConcurrenciaIT.java:306-308` dice
   "Hallazgo pendiente de decisión del dueño" y `progress/impl_HU-22.md:36-39` apunta a "Hallazgo que requiere decisión
   del dueño". Conviene cambiarlos para que apunten a `design.md` § 15.1 (riesgo aceptado).
2. Sigue vigente la observación de la vuelta 1 sobre `DatosPersonales.jsx:91-97` (`falla.errores` puede no existir).
3. Aviso para HU-25 (vuelta 1, desviación 1) y para la tarea futura de § 15.1 (que `bloquearCuenta` vuelva a comprobar
   `ACTIVA` bajo el bloqueo).
