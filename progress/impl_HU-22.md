# Implementación HU-22 — Derechos sobre datos personales

- **Implementer:** 2026-11-04 · rama `feature/HU-22-derechos-datos-personales` · spec aprobado por Kevin Chan (D1–D9).
- **Estado:** T1–T20 hechas y verdes. `feature_list.json` → `in_progress`, a la espera del reviewer.
- **Retomada 2026-11-04 (implementer):** Docker activo, bloqueo levantado. T15 y T20 completadas sin correcciones de
  código: `DatosPersonalesControllerIT` pasó entera en su primera ejecución.

## Resultado de `./init.sh`

- `./2_Backend/mvnw -f 2_Backend/pom.xml verify -Dit.test=DatosPersonalesControllerIT` (solo la IT de HU-22):
  **31 tests, 0 fallos, 0 errores** (≈56 s). `DatosPersonalesSoporte` funcionó sin cambios.
- `./init.sh` **completo** (sin `--rapido`, Docker activo): **OK**, exit 0.
  - Unitarias (surefire): **1562 tests**, 0 fallos, 0 errores, 0 omitidos.
  - Integración (failsafe, Testcontainers): **925 tests**, 0 fallos, 0 errores, 0 omitidos (incluye los 31 de HU-22).
  - Reglas de arquitectura, ESLint, `npm run build`, coherencia de `feature_list.json`/specs/progress: OK.
  - Sin avisos de trazabilidad (R1–R45 con `@DisplayName("HU-22 R<n>: …")`).
- Correcciones hechas en esta sesión: **ninguna** (solo estado: `blocked` → `in_progress`, T15 y T20 marcadas).

## Corrección de la vuelta 1 del reviewer (2026-11-05)

- Nueva `2_Backend/src/test/java/pe/edu/ulima/espacioya/cuentas/controller/DatosPersonalesConcurrenciaIT.java`: hilos
  reales con `CountDownLatch` contra PostgreSQL (Testcontainers), 10 rondas con retraso creciente (0–1080 ms) para que
  salgan los dos órdenes, y además los dos órdenes en secuencia, de forma determinista. Sin cambios en el código de
  producción.

| Test | R | Resultado |
|------|---|-----------|
| `arrendadorEliminaMientrasReservan` | R43 | verde; traza: 9 × reserva 201 + eliminación 400 `reservas`, 1 × eliminación 200 + reserva 404 |
| `arrendadorLosDosOrdenes` | R43 | verde |
| `arrendadorEliminaMientrasSeLiquida` | R44 | verde; eliminación siempre 400 (8 × `liquidaciones`, 2 × `pagos`), cuenta ACTIVA, 1 liquidación |
| `liquidacionLosDosOrdenes` | R44 | verde |
| `dosEliminacionesALaVez` | R42 | verde; una 200, la otra 500 (design § 6.2 paso 1), 1 sola `CUENTA_ELIMINADA` |
| `usuarioLosDosOrdenes` | R42 | verde |

- `./init.sh` completo: **exit 0**. 1562 unitarias + 931 IT, 0 fallos; arquitectura, ESLint y build OK.
- **Hallazgo sin corregir (fuera del spec):** la carrera "el usuario elimina su cuenta mientras crea su reserva" puede
  dejar una cuenta `ELIMINADA` con una reserva vigente si la sesión se validó antes de que la eliminación confirme
  (se comprobó con una sonda). Por eso esa carrera no está en la IT. Kevin Chan aceptó el riesgo el 2026-11-05 (opción 2):
  documentado en `specs/HU-22/design.md` § 15.1 y en la entrada de HU-22 de `progress/history.md`.

## Tasks

| Task | Estado |
|------|--------|
| T1–T2 dominio | [x] |
| T3–T9 puerto, reautenticación, repos, DTO, servicio, pruebas, controlador | [x] |
| T10–T12 `pagos` | [x] |
| T13–T14 `reservas` | [x] |
| T15 `DatosPersonalesSoporte` + `DatosPersonalesControllerIT` | [x] (31 IT verdes) |
| T16–T18 frontend | [x] (lint y build verdes) |
| T19 documentación | [x] |
| T20 `./init.sh` completo | [x] (1562 unitarias + 925 IT, ESLint y build OK) |

## Archivos

**Nuevos (backend):** `cuentas/service/puertos/{ParticipanteDatosPersonales,TitularDatos,SeccionDatos,Impedimento}.java`,
`cuentas/service/DatosPersonalesService.java`, `cuentas/controller/DatosPersonalesController.java`,
`cuentas/dto/{ConfirmacionContrasenaDTO,ExportacionDatosDTO,PerfilExportadoDTO,ConsentimientoDTO,FacturacionExportadaDTO}.java`,
`pagos/service/{PagoDeReserva,ReembolsoDePago}.java`, `pagos/service/impl/DatosPersonalesPagosImpl.java`,
`pagos/dto/CuentaBancariaExportadaDTO.java`, `reservas/dto/{ReservaExportadaDTO,PagoExportadoDTO,ReembolsoExportadoDTO}.java`,
`reservas/service/impl/DatosPersonalesReservasImpl.java`.

**Modificados (backend):** `cuentas/domain/Usuario.java`, `cuentas/domain/PerfilArrendador.java`,
`cuentas/service/ReautenticacionPerfil.java`, `cuentas/repository/DesafioMfaRepository.java`,
`pagos/service/PagoService.java`, `pagos/service/impl/PagoServiceImpl.java`,
`pagos/repository/{PagoRepository,ReembolsoRepository,LiquidacionRepository}.java`.

**Pruebas:** nuevas `cuentas/domain/{UsuarioAnonimizacionTest,PerfilArrendadorAnonimizacionTest}`,
`cuentas/service/DatosPersonalesServiceTest`, `pagos/service/impl/DatosPersonalesPagosImplTest`,
`reservas/service/impl/DatosPersonalesReservasImplTest`, `cuentas/controller/{DatosPersonalesSoporte,DatosPersonalesControllerIT}`;
ampliadas `cuentas/service/ReautenticacionPerfilTest`, `pagos/service/impl/PagoServiceImplTest`.

**Frontend:** nuevos `pages/cuentas/DatosPersonales.jsx` y `.module.css`; modificados `api/cuentas.js`,
`pages/cuentas/Perfil.jsx`, `pages/cuentas/textosCuentas.js`.

**Docs:** `docs/architecture.md` (fila del puerto, `PagoService.pagosDeReservas`, nota de `cuenta_bancaria`, subsección
"Datos personales: qué deja HU-22"), `docs/frontend.md` (tarjeta de `/panel/perfil`), `docs/modelo-datos.md` (marcadores de
una cuenta `ELIMINADA`). Sin migraciones.

## Trazabilidad

Unitarias e IT, todas ejecutadas y verdes.

- R1 → `DatosPersonalesServiceTest.exportaUsuario`, `DatosPersonalesControllerIT.usuarioExporta`
- R2 → `DatosPersonalesServiceTest.exportaArrendador`, `DatosPersonalesControllerIT.arrendadorExporta`
- R3 → `DatosPersonalesServiceTest.perfilExportado`
- R4 → `DatosPersonalesServiceTest.facturacionEnmascarada`, `DatosPersonalesControllerIT.arrendadorExporta`
- R5 → `DatosPersonalesReservasImplTest.exportaReservasDelUsuario`
- R6 → `DatosPersonalesReservasImplTest.exportaPagosDelUsuario`, `PagoServiceImplTest.pagosDeReservasConReembolsos`, `.pagosDeReservasVacio`, `.pagosDeReservasSoloLectura`
- R7 → `DatosPersonalesReservasImplTest.exportaReservasDelArrendador`
- R8 → `DatosPersonalesPagosImplTest.exportaLiquidaciones`, `.usuarioSinNada`
- R9 → `DatosPersonalesPagosImplTest.exportaCuentaEnmascarada`, `.cuentaNullSinRegistro`, `DatosPersonalesControllerIT.arrendadorSinCuenta`
- R10 → `DatosPersonalesControllerIT.usuarioExporta`
- R11 → `DatosPersonalesControllerIT.usuarioExporta`, `.arrendadorExporta`, `.usuarioEliminaSuCuenta`
- R12 → `DatosPersonalesReservasImplTest.soloDelTitular`, `DatosPersonalesControllerIT.soloDatosDelTitular`
- R13 → `DatosPersonalesReservasImplTest.sinDatosDeQuienReservo`, `DatosPersonalesPagosImplTest.sinNumeroCompleto`, `PagoServiceImplTest.pagosDeReservasSinDatosDeTarjeta`, `DatosPersonalesControllerIT.sinDatosProhibidos`
- R14 → `DatosPersonalesServiceTest.auditaExportacion`, `DatosPersonalesControllerIT.exportacionAuditada`
- R15 → `DatosPersonalesServiceTest.sinContrasena`, `DatosPersonalesControllerIT.sinContrasena`
- R16 → `DatosPersonalesServiceTest.contrasenaIncorrecta`, `DatosPersonalesControllerIT.contrasenaIncorrecta`
- R17 → `ReautenticacionPerfilTest.exportacionAuditaOperacion`, `.eliminacionAuditaOperacion`, `DatosPersonalesControllerIT.contrasenaIncorrecta`
- R18 → `DatosPersonalesServiceTest.cupoAgotado`, `DatosPersonalesControllerIT.cupoAgotado`
- R19 → `DatosPersonalesServiceTest.adminRechazado`, `DatosPersonalesControllerIT.adminRechazado`
- R20 → `DatosPersonalesServiceTest.contrasenaIncorrectaNoRevelaImpedimentos`, `DatosPersonalesControllerIT.contrasenaIncorrectaNoRevelaImpedimentos`
- R21 → `UsuarioAnonimizacionTest.quedaEliminada`, `.adminYSuspendidaRechazadas`, `DatosPersonalesServiceTest.eliminaCuenta`, `DatosPersonalesControllerIT.usuarioEliminaSuCuenta`
- R22 → `UsuarioAnonimizacionTest.datosPersonalesNulos`, `.sinFotoDevuelveNull`, `DatosPersonalesServiceTest.anonimizaDatosDeUsuario`, `DatosPersonalesControllerIT.usuarioEliminaSuCuenta`
- R23 → `UsuarioAnonimizacionTest.hashReemplazado`, `DatosPersonalesServiceTest.hashFueraDeLaTransaccion`, `DatosPersonalesControllerIT.usuarioEliminaSuCuenta`
- R24 → `DatosPersonalesServiceTest.fotoTrasElCommit`, `.fotoNoSeBorraConRollback`, `DatosPersonalesControllerIT.usuarioEliminaSuCuenta`
- R25 → `PerfilArrendadorAnonimizacionTest.anonimizaCampos`, `.conservaTipoYEstado`, `DatosPersonalesServiceTest.anonimizaPerfil`, `DatosPersonalesControllerIT.arrendadorEliminaSuCuenta`
- R26 → `DatosPersonalesServiceTest.borraDocumentos`, `DatosPersonalesControllerIT.arrendadorEliminaSuCuenta`
- R27 → `DatosPersonalesPagosImplTest.borraCuentaBancaria`, `.sinCuentaNoFalla`, `DatosPersonalesControllerIT.arrendadorEliminaSuCuenta`
- R28 → `DatosPersonalesServiceTest.revocaSesionesAlFinal`, `DatosPersonalesControllerIT.revocaSesionesTokensYMfa`
- R29 → `DatosPersonalesServiceTest.anulaTokens`, `DatosPersonalesControllerIT.revocaSesionesTokensYMfa`
- R30 → `DatosPersonalesServiceTest.borraMfa`, `DatosPersonalesControllerIT.revocaSesionesTokensYMfa`
- R31 → `DatosPersonalesControllerIT.conservaRegistrosContables`, `.conservaPagosDelUsuario`
- R32 → `DatosPersonalesControllerIT.noIniciaSesion`
- R33 → `DatosPersonalesControllerIT.correoYDocumentoLibres`, `.correoDeUsuarioLibre`
- R34 → `DatosPersonalesServiceTest.auditaEliminacion`, `DatosPersonalesControllerIT.eliminacionAuditada`
- R35 → `DatosPersonalesServiceTest.participanteQueFallaRevierte`, `.fotoNoSeBorraConRollback`, `DatosPersonalesControllerIT.rollbackCompleto`
- R36 → `DatosPersonalesReservasImplTest.pendienteVigenteImpide`, `.confirmadaFuturaImpide`, `.enCursoImpide`, `DatosPersonalesControllerIT.usuarioConReservaFutura`, `.usuarioConPendienteVigente`
- R37 → `DatosPersonalesReservasImplTest.arrendadorConReservaPorTerminar`, `.arrendadorSoloConTerminadas`, `.arrendadorSinEspacios`, `DatosPersonalesControllerIT.arrendadorConReservaPorTerminar`
- R38 → `DatosPersonalesPagosImplTest.liquidacionPendiente`, `.sinImpedimentos`, `DatosPersonalesControllerIT.arrendadorConLiquidacionPendiente`, `.pendienteCuentaImpidePagadaNo`
- R39 → `DatosPersonalesPagosImplTest.cobroPorLiquidar`, `.cobrosQueNoCuentan`, `.incluirSimuladosSegunPropiedad`, `DatosPersonalesControllerIT.arrendadorConCobroPorLiquidar`, `.cobroDeOtroNoImpide`
- R40 → `DatosPersonalesServiceTest.impedimentosJuntos`, `DatosPersonalesPagosImplTest.dosImpedimentos`, `DatosPersonalesControllerIT.variosImpedimentos`
- R41 → `DatosPersonalesReservasImplTest.noImpiden`, `DatosPersonalesControllerIT.reservasQueNoImpiden`
- R42 → `DatosPersonalesServiceTest.impedimentosBajoElBloqueo`, `DatosPersonalesConcurrenciaIT.dosEliminacionesALaVez`,
  `.usuarioLosDosOrdenes` (ver el hallazgo de abajo: la carrera con la reserva propia no está cubierta)
- R43 → `DatosPersonalesReservasImplTest.bloqueoAntesDeLeer`, `.contratoDelPuerto`,
  `DatosPersonalesConcurrenciaIT.arrendadorEliminaMientrasReservan`, `.arrendadorLosDosOrdenes`
- R44 → `DatosPersonalesPagosImplTest.bloqueoPrimero`, `.exigenTransaccion`,
  `DatosPersonalesConcurrenciaIT.arrendadorEliminaMientrasSeLiquida`, `.liquidacionLosDosOrdenes`
- R45 → `DatosPersonalesServiceTest.todosLosParticipantes`, `.seccionRepetida`

## Desviaciones del spec (menores, sin cambiar requisitos)

1. **R29, tokens:** `TipoToken` solo tiene `RECUPERACION` y `CAMBIO_CORREO` (`VERIFICACION` existe en el `CHECK` de la
   base, pero no en el enum: queda para HU-25). Se anulan los pendientes de `TipoToken.values()`, así que cubre los tipos
   que el código puede crear y `VERIFICACION` cuando HU-25 lo agregue. No se agregó `VERIFICACION` al enum (sería tocar
   HU-25).
2. **Perfil del arrendador con bloqueo:** design § 6.2 dice `perfiles.findById`; se usa `perfiles.findParaActualizar`
   (orden `usuario → perfil_arrendador` de HU-20). Así una decisión del admin en paralelo no reescribe los campos ya
   anonimizados.
3. **Fecha del archivo (R10):** se calcula como la fecha de Lima de `generadaEn` (mismo instante del archivo) en lugar de
   un segundo `ZonaHoraria.hoy(reloj)`. En la práctica es "hoy en America/Lima" y coincide con la que usa el frontend.
4. **Impedimentos de `reservas`:** la hora de Lima y la ventana salen del `ahora` que pasa `cuentas` (un solo instante),
   no de otra lectura del reloj. En `impedimentos` del arrendador se corta en el primer espacio con reservas por terminar:
   la transacción se revierte igual.
5. **`ConfirmacionContrasenaDTO` sin Bean Validation:** si llevara `@NotBlank`, un `ADMIN` o una cuenta con el cupo agotado
   recibirían 400 antes del 403/429 que piden R18 y R19. La contraseña vacía la rechaza `ReautenticacionPerfil` (R15).
6. **Arrendador en la exportación (R7):** se usa el mismo `ReservaExportadaDTO` con `precioHora`, `creadaEn` y los datos de
   cancelación en `null` (el JSON los lleva como `null`).

## Pendiente para cerrar

- Revisión del reviewer. No hay tasks pendientes.
