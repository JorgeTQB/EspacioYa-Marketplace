# Implementación — HU-06 · Revisión de identidad de arrendadores

- **Agente:** implementer · **Fecha:** 2026-10-28 · **Rama:** `feature/HU-06-revision-identidad`
- **Spec:** `specs/HU-06/` aprobado por Ariel Enriquez (D1–D4, S1–S16). Tasks T1–T20 marcadas `[x]`.
- **Verificación:** `./init.sh` completo en verde (329 unitarias + 225 de integración, ESLint sin advertencias, build
  del frontend, revisión de colores, secretos y arquitectura). Sin avisos de trazabilidad para HU-06.
- **Migración:** `V2_3__revision_identidad.sql`. `V2_3` confirmada libre en `origin/develop` antes de crearla (regla
  "Migraciones que llegan tarde"); si `develop` la ocupa antes del merge, renombrar a la siguiente `V2_n` libre.

## Trazabilidad

`RIT` = `RevisionIdentidadControllerIT`. Todas las pruebas llevan `@DisplayName("HU-06 R<n>: …")`.

- R1 → `SesionControllerIT.adminDesdeIpPermitida`, `InicioSesionServiceTest.adminDesdeIpPermitida`,
  `UsuarioTest.adminActivoSinMotivoDeInicioSesion`
- R2 → `SesionControllerIT.adminDesdeIpNoPermitida`, `InicioSesionServiceTest.adminNoPermitido`,
  `InicioSesionServiceTest.adminContrasenaIncorrectaAntesQueIp`
- R3 → `SesionControllerIT.adminDesdeIpNoPermitida`, `InicioSesionServiceTest.adminNoPermitido`,
  `InicioSesionServiceTest.adminContrasenaIncorrectaAntesQueIp`
- R4 → `SesionControllerIT.adminDesdeIpNoPermitidaCuentaFallos`, `InicioSesionServiceTest.adminNoPermitido`
- R5 → `FiltroIpsAdminIT.listaVaciaNadieEntra`, `FiltroIpsAdminTest.listaVaciaBloqueaTodo`,
  `IpsPermitidasAdminTest.listaVaciaNoPermiteNada`, `InicioSesionServiceTest.adminConListaVacia`
- R6 → `RecuperacionContrasenaControllerIT.adminDesdeIpPermitidaSinRecuperacion`, `UsuarioTest.adminNoPermitido`,
  `UsuarioTest.recuperacionSinAdminIgualQueInicioSesion`
- R7 → `RIT.ipNoPermitidaResponde403`, `FiltroIpsAdminTest.rutaAdminDesdeIpNoPermitida`,
  `FiltroIpsAdminTest.rutaAdminCodificadaNoEsquivaElFiltro`, `FiltroIpsAdminTest.rutaAdminDesdeIpPermitida`
- R8 → `RIT.ipPermitidaSinSesionResponde401`
- R9 → `RIT.rolDistintoDeAdminResponde403`, `RevisionIdentidadServiceTest.rolDistintoDeAdmin`
- R10 → `IpsPermitidasAdminTest.permiteDireccionesExactas`, `IpsPermitidasAdminTest.permiteRangosCidr`,
  `IpsPermitidasAdminTest.ipNulaONoLiteral`
- R11 → `IpsPermitidasAdminTest.entradaInvalidaLanza`
- R12 → `RIT.archivosNoSeFiltranPorIp`, `FiltroIpsAdminTest.rutaNoAdminDesdeCualquierIp`
- R13 → `IpRealIT$ConexionDesdeProxyDeConfianza.usaLaIpDelEncabezado`
- R14 → `IpRealIT$SinProxiesDeConfianza.ignoraElEncabezado`
- R15 → `CreacionAdminServiceTest.creaLaCuentaAdmin`, `CreacionAdminServiceIT.guardaElAdminEnLaBaseDeDatos`,
  `UsuarioTest.registrarAdminFijaRolYDatos`, `CreadorAdminTest.conPropiedadCreaYCierra`
- R16 → `CreacionAdminServiceTest.auditaLaCreacion`, `CreacionAdminServiceIT.auditaLaCreacion`
- R17 → `CreacionAdminServiceTest.contrasenaFueraDePolitica`, `CreacionAdminServiceTest.correoInvalido`,
  `CreadorAdminTest.fallaDetieneElArranque`
- R18 → `CreacionAdminServiceTest.correoExistente`, `CreacionAdminServiceTest.carreraPorElMismoCorreo`,
  `CreacionAdminServiceIT.correoExistenteNoCambiaNada`
- R19 → `CreadorAdminTest.sinPropiedadNoHayComando`, `CreadorAdminTest.conPropiedadCreaYCierra`,
  `CreacionAdminServiceIT.sinComandoPorDefecto`
- R20 → `RIT.colaOrdenadaPorFechaDeEnvio`, `RIT.colaVacia`, `CuentaServiceImplTest.colaEnElOrdenDeLaConsulta`,
  `RevisionIdentidadServiceTest.colaDelegaEnCuentas`
- R21 → `RIT.colaOrdenadaPorFechaDeEnvio` (fechas distintas + empate por id), `CuentaServiceImplTest.colaEnElOrdenDeLaConsulta`
- R22 → `RIT.colaExcluyeOtrosEstados`
- R23 → `RIT.expedienteCompleto`, `CuentaServiceImplTest.expedienteFirmaLosDocumentos`
- R24 → `RIT.expedienteCompleto`, `CuentaServiceImplTest.expedienteFirmaLosDocumentos`
- R25 → `RIT.expedienteFueraDeLaColaDa404`, `CuentaServiceImplTest.expedienteFueraDeLaColaDa404`
- R26 → `RIT.colaOrdenadaPorFechaDeEnvio`, `RIT.expedienteCompleto`
- R27 → `RIT.aprobarIdentidad`, `RevisionIdentidadServiceTest.aprobarGuardaLaRevision`
- R28 → `RIT.aprobarIdentidad`, `PerfilArrendadorTest.aprobarDesdeEnRevision`, `CuentaServiceImplTest.aprobarAvisaTrasElCommit`
- R29 → `RIT.aprobarIdentidad` (admin de la sesión aunque el cuerpo traiga otro), `RevisionIdentidadTest.aprobarSinMotivo`,
  `RevisionIdentidadServiceTest.aprobarGuardaLaRevision`
- R30 → `RIT.aprobarIdentidad`, `CuentaServiceImplTest.aprobarAvisaTrasElCommit`,
  `CuentaServiceImplTest.falloDelEnvioNoSePropaga`
- R31 → `RIT.rechazarIdentidad`, `RevisionIdentidadServiceTest.rechazarGuardaLaRevision`
- R32 → `RIT.rechazarIdentidad`, `PerfilArrendadorTest.rechazarDesdeEnRevision`, `PerfilArrendadorTest.rechazarSinMotivoLanza`,
  `MotivoRechazoDTOTest.recortaElMotivo`, `CuentaServiceImplTest.rechazarAvisaTrasElCommit`
- R33 → `RIT.rechazarIdentidad`, `RevisionIdentidadTest.rechazarConMotivo`, `RevisionIdentidadTest.rechazarSinMotivoLanza`,
  `RevisionIdentidadServiceTest.rechazarGuardaLaRevision`
- R34 → `RIT.rechazarIdentidad`, `CuentaServiceImplTest.rechazarAvisaTrasElCommit`
- R35 → `RIT.motivoInvalidoResponde400`, `MotivoRechazoDTOTest.motivoAusenteOEnBlanco`, `MotivoRechazoDTOTest.longitudMaxima`,
  `MotivoRechazoDTOTest.recortaElMotivo`
- R36 → `RIT.restriccionesDeLaTabla`
- R37 → `RIT.decisionSobreArrendadorNoValido`, `CuentaServiceImplTest.decisionSobreArrendadorNoValidoDa404`,
  `RevisionIdentidadServiceTest.excepcionDeCuentasSinRevisionNiAuditoria`
- R38 → `RIT.decisionYaTomada`, `RIT.segundaDecisionTrasAprobar`, `PerfilArrendadorTest.decidirFueraDeEnRevisionLanza`,
  `CuentaServiceImplTest.decisionYaTomadaDa400`, `RevisionIdentidadServiceTest.excepcionDeCuentasSinRevisionNiAuditoria`
- R39 → `RIT.decisionesSimultaneas` (dos hilos con `CountDownLatch`: un 201, un 400, una revisión, un aviso)
- R40 → `CuentaServiceImplTest.rollbackNoAvisa`, `CuentaServiceImplTest.decisionSobreArrendadorNoValidoDa404`,
  `CuentaServiceImplTest.decisionYaTomadaDa400`, `RIT.ipNoPermitidaResponde403`, `RIT.rolDistintoDeAdminResponde403`,
  `RIT.motivoInvalidoResponde400`, `RIT.decisionSobreArrendadorNoValido`, `RIT.decisionYaTomada`,
  `RevisionIdentidadServiceTest.excepcionDeCuentasSinRevisionNiAuditoria`
- R41 → `CuentaServiceImplTest.rolDistintoDeAdmin`
- R42 → `RIT.auditaCadaDecision`, `RevisionIdentidadServiceTest.aprobarAudita`, `RevisionIdentidadServiceTest.rechazarAuditaSinMotivo`
- R43 → `RIT.auditaCadaDecision`, `RevisionIdentidadServiceTest.aprobarAudita`, `RevisionIdentidadServiceTest.rechazarAuditaSinMotivo`
- R44 → `ArchivoControllerTest.errorDeLecturaDa404`, `ArchivoControllerTest.lecturaCorrectaSirveElArchivo`
- R45 → `NotificadorCorreoSimuladoTest.identidadAprobadaSinCorreo`,
  `NotificadorCorreoSimuladoTest.identidadRechazadaSinCorreoNiMotivo`

## Tests de historias anteriores que cambiaron (HU-06 reemplaza HU-03 R13)

| Test | Cambio |
|---|---|
| `InicioSesionServiceTest.adminNoPermitido` | De "HU-03 R13, R31 … ROL_NO_PERMITIDO" a "HU-06 R2, R3, R4": admin desde IP no permitida → 401 y `motivo=IP_NO_PERMITIDA`. El constructor de los dos servicios del test recibe `IpsPermitidasAdmin("127.0.0.1,::1")` |
| `SesionControllerIT.rechazosConCuerpoIdentico` | El admin desde `10.3.x.x` sigue con el mismo 401; el detalle esperado pasa de `motivo=ROL_NO_PERMITIDO` a `motivo=IP_NO_PERMITIDA`. El `@DisplayName` conserva "HU-03 … R13" porque `init.sh` exige un test por cada `R<n>` de las historias `done` |
| `UsuarioTest.adminNoPermitido` | De "HU-03 R13" sobre `motivoRechazoInicioSesion` a "HU-06 R6" sobre `motivoRechazoRecuperacion` |
| `CuentaServiceImplTest` (setup) | Constructor con los 5 colaboradores; los 3 tests de HU-07 siguen iguales y en verde |
| `NotificadorFalso` de `UsuarioControllerIT`, `ArrendadorControllerIT`, `RecuperacionContrasenaControllerIT` | Implementan los 2 avisos nuevos (lanzan `UnsupportedOperationException`: esas clases no los usan) |

Sin cambios y en verde, como pide T4: `RecuperacionContrasenaServiceTest.cuentaNoHabilitadaNoEmite`, el caso del token
de admin y `RecuperacionContrasenaControllerIT.cuentasNoHabilitadasMismaRespuesta`.

## Archivos

**Backend — nuevos**
- `administracion/controller/RevisionIdentidadController.java`
- `administracion/domain/DecisionIdentidad.java`, `administracion/domain/RevisionIdentidad.java`
- `administracion/dto/MotivoRechazoDTO.java`, `administracion/dto/RevisionIdentidadDTO.java`
- `administracion/repository/RevisionIdentidadRepository.java`
- `administracion/service/RevisionIdentidadService.java`
- `comun/seguridad/FiltroIpsAdmin.java`, `comun/seguridad/IpsPermitidasAdmin.java`
- `cuentas/dto/ArrendadorEnRevisionDTO.java`, `cuentas/dto/ExpedienteIdentidadDTO.java`
- `cuentas/service/CreacionAdminService.java`, `cuentas/service/CreadorAdmin.java`
- `resources/db/migration/V2_3__revision_identidad.sql`

**Backend — modificados**
- `comun/almacenamiento/ArchivoController.java`, `comun/seguridad/ConfiguracionSeguridad.java`
- `cuentas/domain/Usuario.java`, `cuentas/domain/MotivoRechazoInicioSesion.java`, `cuentas/domain/PerfilArrendador.java`
- `cuentas/repository/PerfilArrendadorRepository.java`
- `cuentas/service/CuentaService.java`, `CuentaServiceImpl.java`, `InicioSesionService.java`, `NotificadorCorreo.java`,
  `NotificadorCorreoSimulado.java`, `RecuperacionContrasenaService.java`
- `resources/application.properties`, `2_Backend/.env.example`

**Pruebas — nuevas:** `RevisionIdentidadControllerIT`, `RevisionIdentidadTest`, `MotivoRechazoDTOTest`,
`RevisionIdentidadServiceTest`, `ArchivoControllerTest`, `FiltroIpsAdminIT`, `FiltroIpsAdminTest`,
`IpsPermitidasAdminTest`, `IpRealIT`, `CreacionAdminServiceIT`, `CreacionAdminServiceTest`, `CreadorAdminTest`.
**Modificadas:** `SesionControllerIT`, `RecuperacionContrasenaControllerIT`, `UsuarioControllerIT`,
`ArrendadorControllerIT`, `UsuarioTest`, `PerfilArrendadorTest`, `CuentaServiceImplTest`, `InicioSesionServiceTest`,
`NotificadorCorreoSimuladoTest`.

**Frontend:** nuevos `components/ui/Modal.jsx` (+ `.module.css`), `api/administracion.js`,
`pages/administracion/ColaIdentidades.jsx` (+ `.module.css`), `RevisarIdentidad.jsx` (+ `.module.css`),
`textosAdministracion.js`; modificados `App.jsx` (rutas `/admin/identidades` y `/admin/identidades/:id`) y
`pages/cuentas/Panel.jsx` (textos y enlace para `ADMIN`).

**Docs:** `docs/modelo-datos.md`, `docs/architecture.md`, `docs/frontend.md`, `docs/requerimientos.md` (§9-4 resuelto),
`README.md`.

## Decisiones de implementación dentro del spec (para el reviewer)

1. **R5 en `FiltroIpsAdminIT`, no en el RIT.** El plan de pruebas lo ponía en el RIT con `@TestPropertySource`. Un
   `@Nested` con otra configuración falla: JUnit inyecta también la instancia externa y lo hace con el contexto del
   anidado, que no tiene el reloj ni el notificador falsos. Se movió a una clase aparte del filtro, con el mismo caso.
2. **`FiltroIpsAdmin` usa `PathPatternRequestMatcher` sobre `/api/admin/**`** en lugar de `startsWith` sobre la URI
   cruda, para que `/api/%61dmin/...` (que Spring MVC sí enruta al controlador) no esquive el filtro. Cubre además
   `/api/admin` sin barra final, que no es ruta de nadie.
3. **`IpsPermitidasAdmin` solo acepta literales** (IPv4 con octetos ≤ 255, IPv6 con `:`, máscara de 1–3 dígitos) antes de
   crear el `IpAddressMatcher`: un nombre de host haría que `InetAddress` consulte el DNS. Una entrada vacía (p. ej. una
   coma final) también impide el arranque. El mensaje dice la posición de la entrada, nunca su valor.
4. **`RevisionIdentidad.rechazar` lanza `IllegalArgumentException` con motivo en blanco** y recorta el motivo; el
   `CHECK` de la BD es la última barrera (R36). `PerfilArrendador.rechazarIdentidad` lanza `IllegalStateException`
   como dice el design y también recorta.
5. **`ExpedienteIdentidadDTO.toString`** no muestra número de documento ni correo (por si algún día se loguea).
6. **README:** el comando de § 9 se documenta con `read -r -s` para que la contraseña no quede en el historial de la
   terminal; es el mismo comando (`ADMIN_CREAR=true … spring-boot:run -Dspring-boot.run.arguments=--server.port=0`).
7. **`CreadorAdmin` cierra el contexto dentro del runner:** comprobado que `SpringApplication.run` de Boot 4.1.1 solo
   publica `ApplicationReadyEvent` si `context.isRunning()`, así que el cierre no provoca un error al terminar.

## Tasks no completadas

Ninguna.
