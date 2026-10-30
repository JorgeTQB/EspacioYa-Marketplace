# Implementación HU-09 · Suspensión y reactivación de cuentas

> Implementer · 2026-10-29 · rama `feature/HU-09-suspension-reactivacion`. Spec: `specs/HU-09/` (R1–R47, T1–T18),
> aprobado por Jorge Quispe con S1–S15 y D1–D4. Bitácora: `progress/HU-09.md`.

## Verificación

`./init.sh` completo (sin `--rapido`, Docker encendido), 2026-10-29: **[OK] Entorno listo**.

- `./mvnw verify`: 427 unitarias y 290 de integración, 0 fallos (antes de HU-09: 385 unitarias).
  `CuentaAdminControllerIT` 23/23, `CuentaServiceImplIT` 3/3, `FotoPublicaControllerIT` 6/6,
  `DisponibilidadServiceImplIT` 2/2, `SesionControllerIT` 28/28, `RevisionIdentidadControllerIT` 18/18.
- ESLint sin advertencias, `npm run build` en verde, sin colores fuera de `tokens.css`, sin secretos.
- Validación de trazabilidad de `init.sh`: ningún aviso para HU-09 (R1–R47 con `@DisplayName("HU-09 R<n>: …")`;
  70 tests de HU-09).
- Único `[WARN]`: faltan `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA` y `ALMACENAMIENTO_LLAVE_FIRMA` en el entorno local
  (solo afecta a `spring-boot:run`; las pruebas traen las suyas). Es previo a HU-09.
- `V3_1` confirmada libre en `origin/develop` (39a77aa) y en todas las ramas remotas al crear la migración. Si al unir
  HU-10/11/12 ya tomaron `V3_1`, renombrarla a la siguiente libre (S12).

## Trazabilidad (R → test)

Clases abreviadas: `CAIT` = `administracion.controller.CuentaAdminControllerIT`, `CAST` =
`administracion.service.CuentaAdminServiceTest`, `CSIT` = `cuentas.service.CuentaServiceImplTest`, `CSIIT` =
`cuentas.service.CuentaServiceImplIT`.

- R1 → `CAIT.buscaPorCorreoTodasLasCuentas`, `CAIT.buscaSinDistinguirMayusculas`, `CSIT.veintiunaCoincidenciasDanVeinteYHayMas`, `CAST.busquedaValidaRecortada`
- R2 → `CAIT.maximoVeinteConHayMas`, `CSIT.veintiunaCoincidenciasDanVeinteYHayMas`
- R3 → `CAIT.maximoVeinteConHayMas`, `CSIT.veintiunaCoincidenciasDanVeinteYHayMas`, `CSIT.veinteCoincidenciasSinHayMas`
- R4 → `CAIT.comodinesLiterales`, `CSIT.patronEscapadoYLimite`
- R5 → `CAIT.buscaPorCorreoTodasLasCuentas` (incluye `ELIMINADA`, `SUSPENDIDA` y `ADMIN`)
- R6 → `CAIT.busquedaInvalidaResponde400`, `CAST.busquedaInvalida` (sin llamar a `CuentaService`)
- R7 → `CAIT.detalleDeArrendadorActiva`, `CSIT.detalleDeArrendador`
- R8 → `CAIT.detalleDeArrendadorActiva`, `CAIT.detalleBanderasPorRolYEstado`, `CSIT.detalleDeArrendador`, `CSIT.detalleSinIdentidadYBanderas`
- R9 → `CAIT.detalleBanderasPorRolYEstado`, `CAIT.detalleConSuspensionVigente`, `CSIT.detalleSinIdentidadYBanderas`, `UsuarioTest.gestionablePorRolYEstado`, `UsuarioTest.banderasDeSuspensionYReactivacion`
- R10 → `CAIT.detalleConSuspensionVigente` (última de dos suspensiones), `CAST.detalleSuspendidaConSuspension`
- R11 → `CAIT.detalleDeArrendadorActiva`, `CAIT.detalleConSuspensionVigente` (nula tras reactivar), `CAST.detalleNoSuspendidaSinSuspension`
- R12 → `CAIT.detalleInexistenteResponde404`, `CSIT.detalleInexistenteDa404`, `CAST.detalleInexistente`
- R13 → `CAIT.buscaPorCorreoTodasLasCuentas`, `CAIT.detalleDeArrendadorActiva`
- R14 → `CAIT.suspenderCuenta`, `CAST.suspenderGuardaYAudita`
- R15 → `CAIT.suspenderCuenta`, `CSIT.suspenderRevocaYAvisaTrasElCommit`, `UsuarioTest.suspenderYReactivar`
- R16 → `CAIT.suspenderCuenta` (dos sesiones revocadas, la de otra cuenta no; el token revocado da 401), `CSIT.suspenderRevocaYAvisaTrasElCommit`
- R17 → `CAIT.suspenderCuenta` (`admin_id` de la sesión aunque el cuerpo traiga otro), `CAST.suspenderGuardaYAudita`, `SuspensionCuentaTest.suspenderConMotivo`, `MotivoSuspensionDTOTest.recortaElMotivo`
- R18 → `CAIT.suspenderCuenta`, `CSIT.suspenderRevocaYAvisaTrasElCommit`
- R19 → `CAIT.motivoInvalidoResponde400`, `MotivoSuspensionDTOTest.motivoAusenteOEnBlanco`, `MotivoSuspensionDTOTest.longitudMaxima`, `CSIT.suspenderSinMotivoLanza`
- R20 → `CAIT.inicioDeSesionTrasSuspenderYReactivar`
- R21 → `SesionServiceTest.cuentaNoActivaVacioSinRenovar`, `CAIT.sesionDeCuentaSuspendidaResponde401`
- R22 → `FotoPublicaControllerIT.fotoPublicadaSegunElEstadoDelArrendador`, `FotoEspacioServiceTest.verPublicadaDeArrendadorNoActivo`, `FotoEspacioServiceTest.verBorradorNoConsultaCuentas`
- R23 → `DisponibilidadServiceImplIT.horasLibresSegunElEstadoDelArrendador`, `DisponibilidadServiceImplTest.arrendadorNoActivoDevuelveVacio`
- R24 → `FotoPublicaControllerIT.fotoPublicadaSegunElEstadoDelArrendador`
- R25 → `DisponibilidadServiceImplIT.horasLibresSegunElEstadoDelArrendador`
- R26 → `CAIT.espacioConservaSuEstado`, `FotoPublicaControllerIT.fotoPublicadaSegunElEstadoDelArrendador`, `DisponibilidadServiceImplIT.horasLibresSegunElEstadoDelArrendador`
- R27 → `CSIIT.cuentaActivaContraLaBaseDeDatos`, `CSIT.cuentaActivaSoloSiExisteYEstaActiva`
- R28 → `CSIIT.arrendadoresNoActivosContraLaBaseDeDatos`, `CSIIT.arrendadoresNoActivosVacio`, `CSIT.arrendadoresNoActivosComoConjunto`
- R29 → `CAIT.reactivarCuenta`, `CAST.reactivarGuardaYAudita`
- R30 → `CAIT.reactivarCuenta`, `CSIT.reactivarAvisaTrasElCommit`, `UsuarioTest.suspenderYReactivar`
- R31 → `CAIT.reactivarCuenta`, `CAST.reactivarGuardaYAudita`, `SuspensionCuentaTest.reactivarSinMotivo`
- R32 → `CAIT.reactivarCuenta`, `CSIT.reactivarAvisaTrasElCommit`
- R33 → `CAIT.inicioDeSesionTrasSuspenderYReactivar`
- R34 → `CAIT.cuentaInexistenteResponde404`, `CSIT.cambioSobreCuentaInexistenteDa404`, `CAST.rechazoDeCuentaServiceSinRegistroNiAuditoria`
- R35 → `CAIT.cuentaNoGestionableResponde400` (otro `ADMIN`, el propio admin, `ELIMINADA`), `CSIT.cambioSobreCuentaNoGestionableDa400`, `UsuarioTest.suspenderFueraDeEstadoLanza`, `UsuarioTest.reactivarFueraDeEstadoLanza`
- R36 → `CAIT.estadoIgualResponde400`, `CSIT.cambioSobreEstadoIgualDa400`, `UsuarioTest.suspenderFueraDeEstadoLanza`
- R37 → `CAIT.estadoIgualResponde400`, `CSIT.cambioSobreEstadoIgualDa400`, `UsuarioTest.reactivarFueraDeEstadoLanza`
- R38 → `CAIT.suspensionesSimultaneas` (dos hilos con `CountDownLatch`: un 201, un 400, un registro, un aviso)
- R39 → `CSIT.suspensionYReactivacionRevertidasNoAvisan`, `CSIT.cambioSobreCuentaInexistenteDa404`, `CSIT.cambioSobreCuentaNoGestionableDa400`, `CSIT.cambioSobreEstadoIgualDa400`, `CAIT.motivoInvalidoResponde400`, `CAIT.cuentaInexistenteResponde404`, `CAIT.cuentaNoGestionableResponde400`, `CAIT.estadoIgualResponde400`
- R40 → `CSIT.rolDistintoDeAdminEnMetodosDeSuspension`
- R41 → `CAIT.rolDistintoDeAdminResponde403`, `CAST.rolDistintoDeAdmin`
- R42 → `CAIT.ipNoPermitidaResponde403`
- R43 → `CAIT.restriccionesDeLaTabla`, `SuspensionCuentaTest.suspenderSinMotivoLanza`
- R44 → `CAIT.auditaCadaAccion`, `CAST.suspenderGuardaYAudita`
- R45 → `CAIT.auditaCadaAccion`, `CAST.reactivarGuardaYAudita`
- R46 → `CAIT.auditaCadaAccion`, `CAST.suspenderGuardaYAudita`, `CAST.reactivarGuardaYAudita`
- R47 → `NotificadorCorreoSimuladoTest.cuentaSuspendidaSinCorreoNiMotivo`, `NotificadorCorreoSimuladoTest.cuentaReactivadaSinCorreo`

## Archivos

### Creados

- `2_Backend/src/main/resources/db/migration/V3_1__suspension_cuenta.sql`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/cuentas/dto/{CuentaResumenDTO,BusquedaCuentasDTO,DetalleCuentaDTO,CambioEstadoCuentaDTO}.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/administracion/domain/{AccionSuspension,SuspensionCuenta}.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/administracion/repository/SuspensionCuentaRepository.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/administracion/dto/{MotivoSuspensionDTO,AccionCuentaDTO,SuspensionVigenteDTO,CuentaAdminDTO}.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/administracion/service/CuentaAdminService.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/administracion/controller/CuentaAdminController.java`
- Tests: `administracion/controller/CuentaAdminControllerIT`, `administracion/service/CuentaAdminServiceTest`,
  `administracion/domain/SuspensionCuentaTest`, `administracion/dto/MotivoSuspensionDTOTest`,
  `cuentas/service/CuentaServiceImplIT`
- `3_frontend/src/pages/administracion/{BuscarCuentas,DetalleCuenta}.jsx` y sus `.module.css`

### Modificados

- `cuentas/domain/Usuario` (`esGestionablePorAdmin`, `puedeSuspenderse`, `puedeReactivarse`, `suspender`, `reactivar`)
- `cuentas/repository/UsuarioRepository` (`findParaActualizar`, `buscar`, `findIdsArrendadoresNoActivos`)
- `cuentas/service/CuentaService`, `CuentaServiceImpl` (6 métodos, inyecta `SesionRepository`, `log.warn` genérico)
- `cuentas/service/SesionService` (`validar` rechaza cuentas no `ACTIVA`)
- `cuentas/service/NotificadorCorreo`, `NotificadorCorreoSimulado` (2 avisos)
- `espacios/service/FotoEspacioService` (`ver`), `DisponibilidadService` (javadoc), `DisponibilidadServiceImpl` (`horasLibres`)
- `3_frontend/src/components/ui/EtiquetaEstado.jsx`, `pages/administracion/textosAdministracion.js`,
  `api/administracion.js`, `pages/cuentas/Panel.jsx`, `App.jsx`
- `docs/architecture.md`, `docs/modelo-datos.md`, `docs/frontend.md`

### Tests existentes que cambiaron (solo constructores, stubs y dobles)

- `CuentaServiceImplTest`: constructor con `SesionRepository` (los tests de HU-06 y HU-07 no cambian).
- `FotoEspacioServiceTest`: constructor con `CuentaService`; `verPublicada` y `verSinFotoOSinArchivo` stubbean
  `cuentaActiva → true`.
- `DisponibilidadServiceImplTest`: constructor con `CuentaService`; los 3 casos con espacio `PUBLICADO` stubbean
  `cuentaActiva → true`; `noPublicadoDevuelveVacio` verifica además que no se consulta `CuentaService`.
- `NotificadorFalso` de `UsuarioControllerIT`, `RecuperacionContrasenaControllerIT`, `ArrendadorControllerIT` y
  `RevisionIdentidadControllerIT`: implementan los dos avisos nuevos (lanzan `UnsupportedOperationException`).
- `UsuarioTest`, `SesionServiceTest`, `NotificadorCorreoSimuladoTest`, `FotoPublicaControllerIT`,
  `DisponibilidadServiceImplIT`: solo se agregaron casos de HU-09.
- Ningún test de HU-03/HU-04 (`SesionControllerIT`, `InicioSesionServiceTest`, `RecuperacionContrasenaControllerIT`)
  tuvo que cambiar por el filtro de `validar` (T5).

## Decisiones menores de implementación (dentro del spec)

- `Usuario.puedeSuspenderse()` / `puedeReactivarse()`: helpers de dominio de los que salen `puedeSuspender` y
  `puedeReactivar` del detalle (DT-6: "salen del dominio") y que usan `suspender`/`reactivar` como guarda.
- `CuentaServiceImpl.suspenderCuenta` lanza `IllegalArgumentException` si el motivo llega nulo o en blanco (defensa en
  profundidad; por la API nunca pasa porque `MotivoSuspensionDTO` lo valida antes, R19).
- `DetalleCuenta.jsx` muestra el teléfono solo si no es nulo (los `USUARIO` y `ADMIN` no tienen), igual que la
  identidad.

## Tasks no completadas

Ninguna.
