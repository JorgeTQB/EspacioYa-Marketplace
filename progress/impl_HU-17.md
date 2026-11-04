# Implementación HU-17 — Autenticación multifactor (MFA)

- **Dueño:** Jorge Quispe · **Rama:** `feature/HU-17-autenticacion-multifactor` · **Fecha:** 2026-11-03
- **Agente:** implementer (1 sesión)
- **Spec:** aprobado por Jorge Quispe el 2026-11-03 con P1–P12 por defecto y las respuestas por defecto a las 3 preguntas
  abiertas (recuperación del admin por SQL documentada en README; sin regenerar códigos; sin correo al activar/desactivar).
- **Estado:** T1–T23 hechas (`[x]` en `specs/HU-17/tasks.md`). Status sigue `in_progress`: espera al reviewer. Sin
  commit, push ni PR.

## Resultado de `./init.sh` (completo, sin `--rapido`)

**exit 0 — "Entorno listo. Puedes empezar a trabajar."** Corrida final del 2026-11-03, con Docker 29.0.1:

- `./mvnw verify`: **1189** unitarias y **732** de integración, 0 fallos, 0 errores, 0 omitidas (antes de HU-17: 1027
  unitarias).
- ESLint sin errores ni advertencias (`--max-warnings=0`); `npm run build` compila.
- Reglas de arquitectura, colores, librerías y secretos: todas `[OK]`. Validación de `feature_list.json`/specs: `[OK]`,
  sin avisos de HU-17 (R1–R66 trazados por `@DisplayName("HU-17 R<n>: …")`, con el mismo regex que usa `init.sh`).
- Único `[WARN]`: `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA` y `ALMACENAMIENTO_LLAVE_FIRMA` no están en el entorno de la
  shell local (solo afecta a `spring-boot:run`; las pruebas traen sus propias llaves). Ajeno a HU-17.

## Desviaciones del spec (menores, sin requirements ni decisiones nuevas)

- **D-A — `Mfa implements Persistable<Long>`.** design § 5 dice "`@Id usuarioId` asignado, sin `@GeneratedValue`". Con id
  asignado, `save` haría `merge` (un `SELECT` previo y, sin fila, un `INSERT` igual); se sigue el mismo patrón que
  `PerfilArrendador` (`isNew` + `@PostLoad/@PostPersist`) para que `save` haga `persist` y `delete` funcione con la
  entidad cargada. Sin cambio de conducta.
- **D-B — `InicioSesionService` ya no recibe `PerfilArrendadorRepository`.** design § 5 lista "+ `MfaRepository`,
  `DesafioMfaRepository`, `EmisorSesiones`; − `SesionRepository`". El aviso del arrendador lo calcula ahora
  `EmisorSesiones`, así que `perfiles` quedaba sin uso y se quitó del constructor.
- **D-C — `limpiarFallos` tras el commit.** design § 6 paso 5 llama `verificador.limpiarFallos` dentro de la
  transacción; se llama después, en el `switch` del resultado `ACEPTADO` (igual en `MfaService.activar/desactivar`).
  El contador es en memoria: así un commit fallido no deja la cuenta sin sus fallos contados. Misma conducta observable
  (R48).
- **D-D — `VerificadorMfa.comprobar` hace `codigos.flush()` antes de contar `restantes`.** Garantiza que el conteo de
  R63 excluya el respaldo recién usado, sin depender del auto-flush de Hibernate.
- **D-E — 202 con `Cache-Control: no-store`.** R58 no lista `POST /api/sesiones`; se agregó `noStore()` a la respuesta
  202 (lleva el desafío). El 201 de HU-03 no cambia.
- **D-F — `MfaDePrueba.iniciarSesion` olvida el último paso por correo** (`UPDATE mfa … WHERE usuario_id = (SELECT id
  FROM usuario WHERE correo = ?)`), porque recibe el correo y no el id. `olvidarUltimoPaso(jdbc, usuarioId)` existe
  igual, con la firma de design § 11.
- **D-G — Soporte de IT de pagos y reservas.** `PagosSoporte.crearAdmin` y `ReservasControllerSoporte.crearAdmin`
  activan MFA (`MfaDePrueba.activar`) y su `token(correo, origen)` hace los dos pasos si la cuenta es admin. Cubre
  `ReembolsoAdminControllerIT`, `RegistroReembolsosIT`, `PagoControllerIT`, `PagoSimuladoIT`, `ReservaControllerIT`,
  `MisReservasControllerIT` y `CancelacionReservaControllerIT` sin tocar esos archivos. `CreacionAdminServiceIT` y
  `RecuperacionContrasenaControllerIT` no inician sesión como admin: sin cambios.
- **D-H — Helper de frontend `pages/cuentas/erroresCampo.js`.** `erroresPorCampo` estaba dentro de `IniciarSesion.jsx`;
  se movió a un archivo del módulo para reusarlo en `PasoCodigoMfa`, `ConfiguracionMfa` y `SeguridadCuenta`. No es del
  kit ni agrega estilos.
- **D-I — `ContextoMfa` no tiene `CUENTA`.** R61 pide `contexto=CUENTA` para la activación desde la cuenta; el detalle
  se escribe literal en `MfaService` y el enum (design § 5: `INICIO_SESION, ACTIVACION, DESACTIVACION`) se usa para
  `MFA_CODIGO_FALLIDO` (R64, `contexto=ACTIVACION`). Es lo que piden R61 y R64 tal cual.

## Tests existentes que cambiaron (y solo estos)

- `InicioSesionServiceTest`: constructor nuevo (con `EmisorSesiones` real sobre los mismos mocks); los 19 casos de
  HU-03/HU-06 siguen con los mismos asserts (los de éxito castean a `SesionIniciadaDTO`), salvo **HU-06 R1**, que ahora
  espera un `DesafioMfaDTO` `CONFIGURACION` sin sesión (`@DisplayName` cita HU-17 R2, R4). +10 casos HU-17.
- `SesionServiceTest`: constructor (+ `MfaRepository`); +3 casos R28.
- `SesionControllerIT.adminDesdeIpPermitida` (HU-06 R1): espera 202 + configuración + 201 con 5 códigos y la sesión
  sirve (`@DisplayName` cita HU-17 R2, R26). Los demás casos de HU-06 (admin sin MFA desde IP no permitida → 401) no
  cambian.
- `CuentaAdminControllerIT`, `RevisionIdentidadControllerIT`: el admin del `@BeforeEach` se crea con
  `MfaDePrueba.activar` y `tokenAdmin()` usa `MfaDePrueba.iniciarSesion`. Mismos asserts (el conteo de auditorías del
  admin con entidad `usuario` sigue en 2: `LOGIN_MFA_PENDIENTE` es de `desafio_mfa` y `LOGIN_EXITOSO` de `sesion`).
- `FiltroIpsAdminIT`: el admin tiene MFA activo; se agrega el assert "sin desafío" (`@DisplayName` cita HU-17 R9).
- `PagosSoporte`, `ReservasControllerSoporte`: D-G.
- `ManejadorGlobalErroresTest`: +1 caso (401 de verificación expirada).

## Mapa R → test (`@DisplayName("HU-17 R<n>: …")`)

Generado de los `@DisplayName` de `2_Backend/src/test/java`: los 66 requirements tienen al menos un test (unitaria y, si
toca la API o la BD, IT).

- R1 → `MfaControllerIT.siguienteInicioPideCodigo`, `MfaInicioSesionIT.conMfaActivoResponde202`, `InicioSesionServiceTest.adminConMfaActivoDevuelveCodigo`, `InicioSesionServiceTest.conMfaActivoDevuelveDesafioCodigo`
- R2 → `MfaInicioSesionIT.adminSinMfaRecibeConfiguracion`, `SesionControllerIT.adminDesdeIpPermitida`, `InicioSesionServiceTest.adminConPendienteDevuelveConfiguracion`, `InicioSesionServiceTest.adminDesdeIpPermitida`
- R3 → `MfaInicioSesionIT.usuarioConPendienteRecibeSesion`, `InicioSesionServiceTest.usuarioFinalSinMfaRecibeSesion`
- R4 → `MfaInicioSesionIT.conMfaActivoResponde202`, `InicioSesionServiceTest.adminDesdeIpPermitida`, `InicioSesionServiceTest.conMfaActivoDevuelveDesafioCodigo`
- R5 → `MfaInicioSesionIT.guardaDesafio`, `DesafioMfaTest.crearFijaCampos`, `InicioSesionServiceTest.guardaDesafioConHash`
- R6 → `MfaInicioSesionIT.guardaDesafio`, `InicioSesionServiceTest.guardaDesafioConHash`
- R7 → `MfaInicioSesionIT.guardaDesafio`, `InicioSesionServiceTest.desafiosDistintos`
- R8 → `MfaInicioSesionIT.contrasenaIncorrectaConMfa`, `InicioSesionServiceTest.contrasenaIncorrectaConMfaSinDesafio`, `InicioSesionServiceTest.cuentaSuspendidaConMfaSinDesafio`
- R9 → `FiltroIpsAdminIT.listaVaciaNadieEntra`, `MfaInicioSesionIT.adminConMfaDesdeIpNoPermitida`, `InicioSesionServiceTest.adminConMfaDesdeIpNoPermitida`
- R10 → `MfaInicioSesionIT.totpCreaSesion`, `DtoMfaTest.sesionVerificadaCopiaLista`, `EmisorSesionesTest.avisoDelArrendador`, `VerificacionMfaServiceTest.totpCreaSesion`
- R11 → `MfaControllerIT.siguienteInicioPideCodigo`, `MfaInicioSesionIT.respaldoCreaSesionUnaVez`, `VerificacionMfaServiceTest.respaldoCreaSesion`, `VerificadorMfaTest.respaldoNormalizadoAceptado`
- R12 → `MfaInicioSesionIT.totpCreaSesion`, `EmisorSesionesTest.guardaSesionConHash`, `VerificacionMfaServiceTest.totpCreaSesion`
- R13 → `MfaInicioSesionIT.desafioDeUnSoloUso`, `MfaInicioSesionIT.desafioSimultaneoSoloUnoGana`, `DesafioMfaTest.cerrarDosVecesFalla`, `VerificacionMfaServiceTest.cierraDesafio`
- R14 → `MfaInicioSesionIT.totpCreaSesion`, `MfaInicioSesionIT.totpNoSeReutiliza`, `MfaTest.registrarPasoRechazaRepetido`, `TotpTest.aceptaPasoSiguienteTrasUltimo`, `VerificacionMfaServiceTest.guardaUltimoPaso`, `VerificadorMfaTest.totpAceptadoRegistraPaso`
- R15 → `MfaInicioSesionIT.totpNoSeReutiliza`, `MfaInicioSesionIT.totpSimultaneoNoSeReutiliza`, `MfaTest.registrarPasoRechazaRepetido`, `TotpTest.aceptaPasoSiguienteTrasUltimo`, `TotpTest.rechazaFormatoInvalido`, `TotpTest.rechazaNulo`, `TotpTest.rechazaPasoYaUsado`, `VerificadorMfaTest.totpNoSeReutiliza`
- R16 → `MfaInicioSesionIT.respaldoCreaSesionUnaVez`, `MfaInicioSesionIT.respaldoSimultaneoUnSoloUso`, `CodigoRespaldoTest.usarDosVecesFalla`, `VerificacionMfaServiceTest.respaldoCreaSesion`, `VerificadorMfaTest.respaldoNormalizadoAceptado`, `VerificadorMfaTest.respaldoUsadoNoAceptado`
- R17 → `MfaInicioSesionIT.respaldoCreaSesionUnaVez`, `Base32Test.esBase32RechazaCaracteresFuera`, `Base32Test.esBase32VacioYValido`, `GeneradorMfaTest.huellaDeFormasEquivalentes`, `GeneradorMfaTest.normalizar`, `VerificadorMfaTest.respaldoConEspacio`, `VerificadorMfaTest.respaldoConFormatoInvalido`, `VerificadorMfaTest.respaldoNormalizadoAceptado`
- R18 → `MfaInicioSesionIT.desafioCodigoEnConfiguracion`, `MfaInicioSesionIT.desafioDeUnSoloUso`, `MfaInicioSesionIT.desafiosNoVigentes`, `DesafioMfaTest.noAbiertoEnElLimiteExacto`, `ManejadorGlobalErroresTest.verificacionExpiradaResponde401`, `VerificacionMfaDTOTest.desafioDTO`, `VerificacionMfaServiceTest.codigoEnConfiguracion`, `VerificacionMfaServiceTest.codigoSinMfaActivo`, `VerificacionMfaServiceTest.configuracionConMfaActivo`, `VerificacionMfaServiceTest.cuentaNoHabilitada`, `VerificacionMfaServiceTest.desafioCerrado`, `VerificacionMfaServiceTest.desafioInexistente`, `VerificacionMfaServiceTest.desafioVencido`
- R19 → `MfaInicioSesionIT.cincoFallosCierranElDesafio`, `VerificacionMfaServiceTest.codigoIncorrecto`
- R20 → `MfaInicioSesionIT.cincoFallosCierranElDesafio`, `DesafioMfaTest.quintoFalloCierra`, `VerificacionMfaServiceTest.codigoIncorrecto`, `VerificacionMfaServiceTest.falloSeGuardaAunqueResponda400`
- R21 → `MfaInicioSesionIT.cincoFallosCierranElDesafio`, `DesafioMfaTest.quintoFalloCierra`, `ManejadorGlobalErroresTest.verificacionExpiradaResponde401`, `VerificacionMfaServiceTest.quintoFalloCierraDesafio`
- R22 → `MfaInicioSesionIT.solicitudesMalFormadas`, `VerificacionMfaDTOTest.codigoLargo`, `VerificacionMfaDTOTest.codigoVacio`, `VerificacionMfaDTOTest.desafioDTO`, `VerificacionMfaDTOTest.desafioLargo`, `VerificacionMfaDTOTest.desafioVacio`, `VerificacionMfaDTOTest.valido`
- R23 → `MfaInicioSesionIT.adminConfiguraMfaAlIniciarSesion`, `GeneradorMfaTest.configuracionArmaUri`, `VerificacionMfaServiceTest.configurarCreaPendiente`
- R24 → `MfaControllerIT.configurarDosVeces`, `MfaInicioSesionIT.configuracionSimultaneaSinChoque`, `MfaInicioSesionIT.adminConfiguraMfaAlIniciarSesion`, `MfaInicioSesionIT.segundaConfiguracionReemplazaSecreto`, `MfaServiceTest.iniciarConfiguracionCrea`, `MfaServiceTest.iniciarConfiguracionReemplaza`, `MfaTest.pendienteInactivo`, `MfaTest.reemplazarSecretoLimpiaUltimoPaso`, `VerificacionMfaServiceTest.configurarCreaPendiente`, `VerificacionMfaServiceTest.configurarReemplazaPendiente`
- R25 → `MfaInicioSesionIT.adminConfiguraMfaAlIniciarSesion`, `MfaTest.activarFijaActivadoEn`, `VerificacionMfaServiceTest.configuracionActivaYDevuelveCodigos`
- R26 → `MfaInicioSesionIT.adminConfiguraMfaAlIniciarSesion`, `SesionControllerIT.adminDesdeIpPermitida`, `VerificacionMfaServiceTest.configuracionActivaYDevuelveCodigos`
- R27 → `MfaInicioSesionIT.configuracionRechazada`, `VerificacionMfaServiceTest.configuracionConRespaldo`, `VerificacionMfaServiceTest.configuracionSinPendiente`, `VerificadorMfaTest.respaldoNoAdmitido`, `VerificadorMfaTest.respaldoSinMfaActivo`
- R28 → `MfaInicioSesionIT.sesionDeAdminSinMfaRechazada`, `SesionServiceTest.adminConMfaPresente`, `SesionServiceTest.adminSinMfaVacioSinRenovar`, `SesionServiceTest.usuarioFinalSinMfaPresente`
- R29 → `MfaControllerIT.configurarGuardaSecretoCifrado`, `MfaServiceTest.iniciarConfiguracionCrea`
- R30 → `MfaControllerIT.configurarYaActivo`, `MfaServiceTest.iniciarConfiguracionYaActivo`, `MfaTest.reemplazarSecretoFallaSiActivo`
- R31 → `MfaControllerIT.activarDevuelveCodigos`, `MfaServiceTest.activarConTotp`, `MfaTest.activarFijaActivadoEn`
- R32 → `MfaControllerIT.activarDevuelveCodigos`, `MfaServiceTest.activarConTotp`
- R33 → `MfaControllerIT.activarSinPendiente`, `MfaServiceTest.activarSinFila`, `MfaServiceTest.activarYaActivo`
- R34 → `MfaControllerIT.activarCodigoIncorrecto`, `MfaServiceTest.activarCodigoIncorrecto`, `VerificacionMfaDTOTest.codigoMfaDTO`
- R35 → `MfaControllerIT.activarRevocaOtrasSesiones`, `MfaInicioSesionIT.activarRevocaSesionesPrevias`, `MfaServiceTest.activarRevocaOtras`, `VerificacionMfaServiceTest.configuracionRevocaAntesDeEmitir`
- R36 → `MfaControllerIT.activarDevuelveCodigos`, `MfaInicioSesionIT.adminConfiguraMfaAlIniciarSesion`, `GeneradorMfaTest.reemplazarCodigos`, `MfaServiceTest.activarConTotp`, `VerificacionMfaServiceTest.configuracionActivaYDevuelveCodigos`
- R37 → `MfaControllerIT.activarDevuelveCodigos`, `MfaInicioSesionIT.adminConfiguraMfaAlIniciarSesion`, `CodigoRespaldoTest.nuevoSinUsar`, `GeneradorMfaTest.huellaDeFormasEquivalentes`, `GeneradorMfaTest.reemplazarCodigos`
- R38 → `MfaControllerIT.activarDevuelveCodigos`
- R39 → `MfaControllerIT.estadoSinMfa`, `MfaServiceTest.estadoActivoAdmin`, `MfaServiceTest.estadoActivoUsuarioFinal`, `MfaServiceTest.estadoPendiente`, `MfaServiceTest.estadoSinMfa`
- R40 → `MfaControllerIT.desactivar`, `MfaControllerIT.desactivarConRespaldo`, `MfaServiceTest.desactivarConRespaldo`, `MfaServiceTest.desactivarConTotp`
- R41 → `MfaControllerIT.desactivarRechazada`, `MfaServiceTest.desactivarCompruebaAmbos`, `MfaServiceTest.desactivarRechazadaMismoMensaje`, `VerificacionMfaDTOTest.desactivacionMfaDTO`
- R42 → `MfaControllerIT.adminNoDesactiva`, `MfaServiceTest.desactivarAdmin`
- R43 → `MfaControllerIT.desactivarSinMfa`, `MfaServiceTest.desactivarSinMfa`
- R44 → `MfaControllerIT.sinSesion`
- R45 → `MfaControllerIT.activarCodigoIncorrecto`, `MfaControllerIT.desactivarRechazada`, `MfaControllerIT.fallosDeDesactivacionBloqueanElLogin`, `MfaInicioSesionIT.cincoFallosCierranElDesafio`, `MfaServiceTest.activarCodigoIncorrecto`, `MfaServiceTest.desactivarRechazadaMismoMensaje`, `VerificacionMfaServiceTest.codigoIncorrecto`, `VerificadorMfaTest.registrarFalloAudita`
- R46 → `MfaControllerIT.activacionesFallidasBloquean`, `MfaControllerIT.fallosDeDesactivacionBloqueanElLogin`, `MfaInicioSesionIT.diezFallosBloquean`, `MfaServiceTest.activarBloqueado`, `MfaServiceTest.desactivarBloqueado`, `VerificacionMfaServiceTest.bloqueadoNoComprueba`, `VerificadorMfaTest.diezFallosBloquean`, `VerificadorMfaTest.nueveFallosNoBloquean`
- R47 → `MfaInicioSesionIT.bloqueoCaducaA15Minutos`, `VerificadorMfaTest.falloCaducaALos15Minutos`
- R48 → `MfaInicioSesionIT.aciertoReiniciaContador`, `MfaServiceTest.activarAuditaYLimpia`, `MfaServiceTest.desactivarConRespaldo`, `VerificacionMfaServiceTest.aciertoReiniciaFallos`, `VerificadorMfaTest.limpiarFallos`
- R49 → `MfaControllerIT.activarSinPendiente`, `MfaControllerIT.desactivarSinMfa`, `MfaInicioSesionIT.desafiosNoVigentes`, `MfaInicioSesionIT.solicitudesMalFormadas`, `MfaServiceTest.activarSinFila`, `MfaServiceTest.desactivarSinMfa`, `VerificacionMfaServiceTest.bloqueadoNoComprueba`
- R50 → `MfaControllerIT.configurarGuardaSecretoCifrado`, `MfaTest.secretoEnBytes`
- R51 → `Base32Test.codificaSecretoDeRfc6238`, `Base32Test.codificaVectoresRfc4648`, `Base32Test.decodificaVectoresRfc4648`, `Base32Test.decodificarInvalidoFalla`, `TotpTest.pasoDe`, `TotpTest.vectoresRfc6238`
- R52 → `TotpTest.aceptaToleranciaDeUnPaso`, `TotpTest.ignoraEspacios`
- R53 → `TotpTest.rechazaDosPasosOMas`
- R54 → `MfaControllerIT.configurarDosVeces`, `MfaInicioSesionIT.segundaConfiguracionReemplazaSecreto`, `Base32Test.idaYVuelta`, `GeneradorMfaTest.nuevoSecreto`
- R55 → `MfaControllerIT.configurarGuardaSecretoCifrado`, `GeneradorMfaTest.configuracionArmaUri`
- R56 → `MfaControllerIT.activarDevuelveCodigos`
- R57 → `DtoMfaTest.codigo`, `DtoMfaTest.codigosRespaldo`, `DtoMfaTest.configuracion`, `DtoMfaTest.desactivacion`, `DtoMfaTest.desafio`, `DtoMfaTest.desafioMfa`, `DtoMfaTest.estado`, `DtoMfaTest.sesionVerificada`, `DtoMfaTest.verificacion`
- R58 → `MfaControllerIT.activarDevuelveCodigos`, `MfaControllerIT.configurarGuardaSecretoCifrado`, `MfaControllerIT.estadoSinMfa`, `MfaInicioSesionIT.adminConfiguraMfaAlIniciarSesion`, `MfaInicioSesionIT.conMfaActivoResponde202`, `MfaInicioSesionIT.totpCreaSesion`
- R59 → `MfaInicioSesionIT.auditaLoginMfaPendiente`, `InicioSesionServiceTest.auditaLoginMfaPendiente`
- R60 → `MfaInicioSesionIT.respaldoCreaSesionUnaVez`, `MfaInicioSesionIT.totpCreaSesion`, `EmisorSesionesTest.auditaConDetalle`, `VerificacionMfaServiceTest.auditaFactorRespaldo`, `VerificacionMfaServiceTest.auditaFactorTotp`
- R61 → `MfaControllerIT.activarDevuelveCodigos`, `MfaInicioSesionIT.adminConfiguraMfaAlIniciarSesion`, `MfaServiceTest.activarAuditaYLimpia`, `VerificacionMfaServiceTest.auditaMfaActivadoEnInicio`
- R62 → `MfaControllerIT.desactivar`, `MfaServiceTest.desactivarConTotp`
- R63 → `MfaControllerIT.desactivarConRespaldo`, `MfaInicioSesionIT.respaldoCreaSesionUnaVez`, `VerificadorMfaTest.respaldoEnDesactivacion`, `VerificadorMfaTest.respaldoNormalizadoAceptado`
- R64 → `MfaControllerIT.activarCodigoIncorrecto`, `MfaInicioSesionIT.cincoFallosCierranElDesafio`, `MfaServiceTest.activarCodigoIncorrecto`, `VerificacionMfaServiceTest.codigoIncorrecto`, `VerificadorMfaTest.registrarFalloAudita`
- R65 → `MfaInicioSesionIT.diezFallosBloquean`, `VerificadorMfaTest.diezFallosBloquean`
- R66 → `MfaControllerIT.auditoriaSinSecretos`, `MfaInicioSesionIT.auditoriaSinSecretos`, `InicioSesionServiceTest.auditaLoginMfaPendiente`, `VerificadorMfaTest.detalleSinCodigo`

## Archivos

**Backend `src/main` — nuevos:** `comun/error/VerificacionExpiradaException`; `cuentas/domain/{Base32, Totp, Mfa,
CodigoRespaldo, DesafioMfa, PasoMfa, FactorMfa, ContextoMfa}`; `cuentas/repository/{MfaRepository,
CodigoRespaldoRepository, DesafioMfaRepository}`; `cuentas/service/{EmisorSesiones, GeneradorMfa, VerificadorMfa,
VerificacionMfaService, MfaService}`; `cuentas/dto/{RespuestaInicioSesion, DesafioMfaDTO, DesafioDTO, VerificacionMfaDTO,
SesionVerificadaDTO, ConfiguracionMfaDTO, CodigoMfaDTO, CodigosRespaldoDTO, DesactivacionMfaDTO, EstadoMfaDTO}`;
`cuentas/controller/MfaController`; `resources/db/migration/V5_1__mfa.sql`.

**Backend `src/main` — modificados:** `comun/error/{ErrorDTO, ManejadorGlobalErrores}`;
`comun/seguridad/{ConfiguracionSeguridad, IpsPermitidasAdmin (javadoc), FiltroIpsAdmin (javadoc)}`;
`cuentas/domain/Usuario` (comentario); `cuentas/dto/SesionIniciadaDTO`; `cuentas/repository/SesionRepository`
(`revocarOtras`); `cuentas/service/{InicioSesionService, SesionService}`; `cuentas/controller/SesionController`;
`resources/application.properties` (comentario); `2_Backend/.env.example` (comentario).

**Backend `src/test` — nuevos:** `MfaDePrueba`; `cuentas/domain/{Base32Test, TotpTest, MfaTest, CodigoRespaldoTest,
DesafioMfaTest}`; `cuentas/dto/{VerificacionMfaDTOTest, DtoMfaTest}`; `cuentas/service/{EmisorSesionesTest,
GeneradorMfaTest, VerificadorMfaTest, VerificacionMfaServiceTest, MfaServiceTest}`;
`cuentas/controller/{MfaInicioSesionIT, MfaControllerIT}`. **Modificados:** ver la sección anterior.

**Frontend:** `src/api/cuentas.js` (6 funciones); `src/App.jsx` (ruta `/panel/seguridad`);
`src/pages/cuentas/{IniciarSesion.jsx, IniciarSesion.module.css, Panel.jsx, textosCuentas.js}` (M);
`src/pages/cuentas/{PasoCodigoMfa, ConfiguracionMfa, CodigosRespaldo, SeguridadCuenta}.jsx` + `.module.css` y
`erroresCampo.js` (N). Sin componentes nuevos en el kit, sin estados nuevos de `EtiquetaEstado`, sin colores fuera de
tokens, sin dependencias nuevas.

**Docs:** `docs/modelo-datos.md`, `docs/requerimientos.md` (§9 ítems 4 y 7), `docs/architecture.md` (Seguridad
transversal), `docs/frontend.md` (Rutas y llamadas a la API), `README.md`. La tabla "Puertos entre módulos" no cambia
(DT-15). La matriz §7 de `docs/requerimientos.md` no se tocó (la llena la pareja que prueba).

## Nota de despliegue (T22)

- **Una sola instancia del backend.** El límite de 10 fallos por cuenta en 15 min vive en memoria (`LimitadorIntentos`,
  DT-8), como el de HU-03: con varias instancias cada una contaría por separado. El límite de 5 por desafío sí está en
  BD (`desafio_mfa.intentos_fallidos`).
- **Reloj del servidor sincronizado (NTP).** TOTP tolera ±1 paso (±30 s): un servidor desfasado más de ~30 s rechaza
  códigos válidos.
- **Migración `V5_1__mfa.sql`:** no hay otra `V5_*` en `origin/develop` hoy; si HU-18 llega antes con `V5_1`, renombrar
  esta a la siguiente libre antes del merge (`docs/conventions.md`).
- **Admins existentes:** al desplegar, sus sesiones abiertas dejan de valer (R28) y su siguiente inicio de sesión les
  pide configurar MFA (R2). Procedimiento ante pérdida de la app y los códigos: README (`DELETE FROM mfa …`).

## Verificación visual

Las pantallas no tienen tests (docs/frontend.md): pasan `npm run lint -- --max-warnings=0` y `npm run build`. No se
hizo una revisión en navegador en esta sesión; queda para la caja negra de la pareja en UAT (login de usuario con TOTP y
con respaldo, primer login del admin con configuración y códigos, `/panel/seguridad` activar/desactivar).

## Tasks no completadas

Ninguna.

## Vuelta 1 de revisión (2026-11-03, `progress/review_HU-17.md`)

- **H1 — concurrencia (DT-11).** 4 IT nuevas en `MfaInicioSesionIT` (helper `aLaVez`: `CountDownLatch` + 2 hilos,
  patrón de HU-04 R22), cada una con `@DisplayName("HU-17 R<n>: …")`:
  - R13 `desafioSimultaneoSoloUnoGana`: mismo desafío + mismo TOTP → {201, 401 verificación expirada}; 1 sesión,
    `cerrado_en` fijado, 1 `LOGIN_EXITOSO`.
  - R15 `totpSimultaneoNoSeReutiliza`: dos desafíos + mismo TOTP → {201, 400 en `codigo`}; 1 sesión, `ultimo_paso` =
    ese paso.
  - R16 `respaldoSimultaneoUnSoloUso`: dos desafíos + mismo respaldo → {201, 400 en `codigo`}; ese código con
    `usado_en`, 1 solo usado, 1 sola auditoría `MFA_CODIGO_RESPALDO_USADO` (`restantes=4`).
  - R24 `configuracionSimultaneaSinChoque`: admin sin fila de `mfa`, dos desafíos CONFIGURACION y dos
    `POST /api/sesiones/mfa/configuracion` a la vez → ambos 200 (ningún 500), 1 fila en `mfa` (inactiva) cuyo secreto
    descifrado es uno de los dos devueltos.
  Pasaron a la primera: los bloqueos `desafio_mfa → usuario → mfa` de `VerificacionMfaService`/`MfaService` ya eran
  correctos; **no se cambió código de `src/main`**.
- **H2 — mapa.** R57 ya no cita `MfaInicioSesionIT.auditoriaSinSecretos` (solo `DtoMfaTest`); R13, R15, R16 y R24
  incluyen los tests nuevos.
- **Archivos modificados en esta vuelta:** `2_Backend/src/test/java/pe/edu/ulima/espacioya/cuentas/controller/MfaInicioSesionIT.java`,
  `progress/impl_HU-17.md`, `progress/HU-17.md`.
- **`./init.sh` completo:** exit 0, "Entorno listo". `mvnw verify`: **1189** unitarias y **736** IT (732 + 4), 0 fallos,
  0 errores, 0 omitidas. Lint, build y chequeos `[OK]`; único `[WARN]` el de siempre (llaves de cifrado locales, ajeno).
