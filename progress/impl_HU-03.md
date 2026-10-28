# Implementación HU-03 — Inicio y cierre de sesión

- **Fecha:** 2026-10-28 · **Agente:** implementer · **Rama:** feature/HU-03-inicio-cierre-sesion
- **Spec:** `specs/HU-03/` aprobado por Jorge Quispe el 2026-10-28 (P1–P7 con los valores por defecto).
- **Tasks:** T1–T19 todas `[x]`.
- **Verificación:** `./init.sh` completo en verde: 118 pruebas unitarias + 89 de integración
  (`./mvnw verify`, incluidas las de HU-01 y HU-02 sin cambios), `npm run lint -- --max-warnings=0`
  y `npm run build` en verde, chequeos de arquitectura en OK.

## Trazabilidad

- R1 → `SesionControllerIT.loginCorrectoResponde201ConCamposMinimos`, `InicioSesionServiceTest.loginCorrectoDeUsuario`, `UsuarioTest.cuentaHabilitadaSinMotivo`
- R2 → `SesionControllerIT.guardaSesionConFechasDelReloj`, `SesionTest.iniciarFijaFechasYEstado`, `InicioSesionServiceTest.loginCorrectoDeUsuario`
- R3 → `SesionControllerIT.guardaSoloElHashDelToken`, `TokenAleatorioTest.hashEsSha256Hex`, `TokenAleatorioTest.hashDistintoDelToken`, `InicioSesionServiceTest.loginCorrectoDeUsuario`
- R4 → `SesionControllerIT.arrendadorRecibeAviso`, `InicioSesionServiceTest.arrendadorNoIniciadaRecibeAviso`
- R5 → `SesionControllerIT.sinAvisoParaUsuarioYArrendadorVerificado`, `InicioSesionServiceTest.arrendadorVerificadoSinAviso`, `InicioSesionServiceTest.loginCorrectoDeUsuario`
- R6 → `SesionControllerIT.tokensDistintos`, `TokenAleatorioTest.tokenEnBase64UrlSinRelleno`, `TokenAleatorioTest.dosTokensDistintos`
- R7 → `SesionControllerIT.loginCorrectoResponde201ConCamposMinimos`
- R8 → `SesionControllerIT.ignoraRolDelCliente`
- R9 → `SesionControllerIT.rechazosConCuerpoIdentico`, `InicioSesionServiceTest.contrasenaIncorrecta`, `ManejadorGlobalErroresTest.credencialesInvalidasResponde401`
- R10 → `SesionControllerIT.rechazosConCuerpoIdentico`, `InicioSesionServiceTest.correoDesconocido`
- R11 → `SesionControllerIT.rechazosConCuerpoIdentico`, `InicioSesionServiceTest.correoNoVerificado`, `UsuarioTest.correoNoVerificado`
- R12 → `SesionControllerIT.rechazosConCuerpoIdentico`, `InicioSesionServiceTest.cuentaNoActiva`, `UsuarioTest.cuentaNoActiva`
- R13 → `SesionControllerIT.rechazosConCuerpoIdentico`, `InicioSesionServiceTest.adminNoPermitido`, `UsuarioTest.adminNoPermitido`
- R14 → `InicioSesionServiceTest.comparaContraHashSenuelo`
- R15 → `SesionControllerIT.malFormadasNoCuentan`
- R16 → `SesionControllerIT.bloqueaTrasCincoFallos`, `InicioSesionServiceTest.bloqueadoNoComparaContrasena`, `InicioSesionServiceTest.cuatroFallosNoBloquean`, `LimitadorIntentosTest.superaLimiteAlQuintoFallo`, `ManejadorGlobalErroresTest.demasiadosIntentosResponde429`
- R17 → `SesionControllerIT.correoInexistenteTambienBloquea`, `InicioSesionServiceTest.correoDesconocido`, `InicioSesionServiceTest.contrasenaIncorrecta`
- R18 → `SesionControllerIT.malFormadasNoCuentan`, `InicioSesionServiceTest.bloqueadoNoRegistraFallo`, `LimitadorIntentosTest.superaLimiteNoRegistra`
- R19 → `SesionControllerIT.desbloqueaAlPasarLaVentana`, `LimitadorIntentosTest.dejaDeContarAlPasarLaVentana`
- R20 → `SesionControllerIT.loginCorrectoReiniciaContador`, `InicioSesionServiceTest.loginCorrectoReiniciaFallos`, `LimitadorIntentosTest.limpiarReiniciaContador`
- R21 → `SesionControllerIT.otroCorreoMismaIpNoSeBloquea`, `LimitadorIntentosTest.clavesDeLoginIndependientes`
- R22 → `SesionControllerIT.actualUsaLaIdentidadDeLaSesion`, `FiltroAutenticacionSesionTest.bearerValidoAutentica`, `SesionServiceTest.vigenteRenuevaYDevuelveUsuario`
- R23 → `SesionControllerIT.actualUsaLaIdentidadDeLaSesion`, `SesionControllerIT.actualArrendadorConAviso`, `SesionServiceTest.actualConAviso`
- R24 → `SesionControllerIT.actividadRenuevaLaSesion`, `SesionTest.renovarMueveExpiracion`, `SesionServiceTest.vigenteRenuevaYDevuelveUsuario`
- R25 → `SesionControllerIT.cerrarSesionRevoca`, `SesionServiceTest.cerrarRevocaYAudita`, `SesionTest.revocadaNoVigente`
- R26 → `SesionControllerIT.cerrarSesionRevoca`, `SesionServiceTest.revocadaVacio`, `FiltroAutenticacionSesionTest.tokenSinSesionVigenteNoAutentica`
- R27 → `SesionControllerIT.inactividadInvalidaLaSesion`, `SesionTest.noVigenteEnElLimiteExacto`, `SesionServiceTest.vencidaVacioSinRenovar`
- R28 → `SesionControllerIT.sinTokenValidoResponde401`, `FiltroAutenticacionSesionTest.sinEncabezadoNoAutentica`, `FiltroAutenticacionSesionTest.esquemaBasicNoAutentica`, `FiltroAutenticacionSesionTest.bearerVacioNoAutentica`, `SesionServiceTest.tokenDesconocidoVacio`
- R29 → `SesionControllerIT.cerrarNoAfectaOtrasSesiones`, `SesionServiceTest.cerrarRevocaYAudita`
- R30 → `SesionControllerIT.auditaLoginExitoso`, `InicioSesionServiceTest.auditaLoginExitoso`
- R31 → `SesionControllerIT.auditaLoginFallido`, `SesionControllerIT.rechazosConCuerpoIdentico`, `InicioSesionServiceTest.contrasenaIncorrecta` (y un test por motivo)
- R32 → `SesionControllerIT.bloqueaTrasCincoFallos`, `InicioSesionServiceTest.bloqueadoNoComparaContrasena`, `InicioSesionServiceTest.bloqueadoCorreoInexistente`
- R33 → `SesionControllerIT.cerrarSesionRevoca`, `SesionServiceTest.cerrarRevocaYAudita`
- R34 → `SesionControllerIT.auditoriaSinDatosSensibles`, `InicioSesionServiceTest.detalleSinDatosSensibles`, `InicioSesionServiceTest.auditaLoginExitoso`
- R35 → `InicioSesionDTOTest.toStringEnmascarado`
- R36 → `SesionControllerIT.preflightPermiteAuthorization`

## Archivos

Backend (`2_Backend/src/main/java/pe/edu/ulima/espacioya/`), (N) nuevo · (M) modificado:

- `comun/seguridad/`: `TokenAleatorio` (N), `ValidadorSesion` (N), `UsuarioAutenticado` (N),
  `FiltroAutenticacionSesion` (N), `PuntoEntradaNoAutenticado` (N), `ConfiguracionSeguridad` (M).
- `comun/error/`: `CredencialesInvalidasException` (N), `DemasiadosIntentosException` (N), `ErrorDTO` (M),
  `ManejadorGlobalErrores` (M).
- `comun/limite/LimitadorIntentos` (M): `superaLimite`, `registrar`, `limpiar` (+ `purgar` privado; `intentar`
  sin cambio de comportamiento).
- `cuentas/domain/`: `Sesion` (N), `MotivoRechazoInicioSesion` (N), `Usuario` (M, `motivoRechazoInicioSesion`),
  `PerfilArrendador` (M, `AVISO_IDENTIDAD`).
- `cuentas/repository/SesionRepository` (N).
- `cuentas/service/`: `InicioSesionService` (N), `SesionService` (N), `AvisoCuenta` (N, clase de paquete).
- `cuentas/dto/`: `InicioSesionDTO`, `SesionIniciadaDTO`, `SesionActualDTO` (N).
- `cuentas/controller/`: `SesionController` (N), `ArrendadorController` (M, `AVISO_IDENTIDAD` apunta a
  `PerfilArrendador`).
- `src/main/resources/db/migration/V1_4__sesion.sql` (N).

Tests (`2_Backend/src/test/java/pe/edu/ulima/espacioya/`):

- (N) `comun/seguridad/TokenAleatorioTest`, `comun/seguridad/FiltroAutenticacionSesionTest`,
  `cuentas/domain/SesionTest`, `cuentas/dto/InicioSesionDTOTest`, `cuentas/service/InicioSesionServiceTest`,
  `cuentas/service/SesionServiceTest`, `cuentas/controller/SesionControllerIT`.
- (M, solo tests nuevos añadidos) `comun/limite/LimitadorIntentosTest`, `comun/error/ManejadorGlobalErroresTest`,
  `cuentas/domain/UsuarioTest`.

Frontend (`3_frontend/src/`):

- (N) `api/tokenSesion.js`, `sesion/ContextoSesion.js`, `sesion/ProveedorSesion.jsx`, `sesion/useSesion.js`,
  `sesion/RutaProtegida.jsx`, `pages/cuentas/IniciarSesion.jsx` (+ `.module.css`),
  `pages/cuentas/Panel.jsx` (+ `.module.css`).
- (M) `api/cliente.js`, `api/cuentas.js`, `main.jsx`, `App.jsx`, `App.module.css`.

Docs: `docs/modelo-datos.md` (`sesion.hash_token`), `docs/frontend.md` (carpeta `sesion/`, `tokenSesion.js`,
"Cómo viaja la sesión", rutas con sesión, regla de `localStorage`). `docs/architecture.md` sin cambios: el
puerto `comun.ValidadorSesion` ya figuraba en la tabla. Sin componentes nuevos en el kit ni estados nuevos.

## Decisiones de implementación dentro del spec

- `Sesion.hashToken` se mapea con `@JdbcTypeCode(SqlTypes.CHAR)` + `length = 64`: con `columnDefinition`
  Hibernate esperaba VARCHAR y `ddl-auto=validate` fallaba contra `CHAR(64)` (ajuste previsto en design § 9).
- `avisoPara` vive en la clase de paquete `AvisoCuenta` (opción que permite design § 5), para no acoplar
  `InicioSesionService` a `SesionService`. Las firmas de los constructores son las del design.
- La auditoría `LOGIN_FALLIDO` y `LOGIN_BLOQUEADO` usa `entidad = "usuario"` y `entidad_id` = id de la cuenta
  (nulo si no existe); R31/R32 no fijan la entidad.
- `SesionIniciadaDTO.toString` enmascara el token (minimización adicional, sin cambio de contrato).
- Frontend: `peticion` acepta `autenticada: false`, que usa `iniciarSesion` para no enviar un token viejo
  (un 401 del login son credenciales inválidas, no una sesión perdida). El error del cliente añade el campo
  `estado` (código HTTP) para que "Cerrar sesión" distinga 401 de un error de red sin comparar textos.
  `IniciarSesion` recibe `state.tipo` (`info` por defecto, `exito` para "Cerraste sesión.").

## Nota de despliegue (T18)

- **TLS (DT-11):** UAT debe servir backend y SPA por HTTPS; Spring Boot no termina TLS en este proyecto.
- **Una sola instancia del backend (DT-6):** el contador de fallos de login vive en memoria en
  `LimitadorIntentos`; se pierde al reiniciar y no se comparte entre instancias.
- `IpCliente` usa `getRemoteAddr()`: detrás de un proxy la auditoría verá la IP del proxy (decisión
  transversal fuera de HU-03).

## Tasks no completadas

Ninguna.
