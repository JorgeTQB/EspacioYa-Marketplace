# Review — HU-06

**Veredicto:** APPROVED

Revisado sobre la rama `feature/HU-06-revision-identidad` (cambios sin commitear sobre `develop` `02a83b6`).
`./init.sh` completo (sin `--rapido`) en verde, exit 0: 329 unitarias + 225 de integración (Testcontainers),
ESLint sin advertencias, build del frontend, reglas de arquitectura, colores y secretos. Único `[WARN]`: llaves
locales ausentes (previo a HU-06, no afecta a los tests).

## Trazabilidad requirements ↔ tests

`RIT` = `administracion/controller/RevisionIdentidadControllerIT`. Todos con `@DisplayName("HU-06 R<n>: …")`.

- R1: [x] `SesionControllerIT.adminDesdeIpPermitida` (201, `rol=ADMIN`, `aviso` nulo, 1 sesión, `LOGIN_EXITOSO`), `InicioSesionServiceTest.adminDesdeIpPermitida`, `UsuarioTest.adminActivoSinMotivoDeInicioSesion`
- R2: [x] `SesionControllerIT.adminDesdeIpNoPermitida` (cuerpo idéntico al 401 genérico, sin sesión), `InicioSesionServiceTest.adminNoPermitido`
- R3: [x] `SesionControllerIT.adminDesdeIpNoPermitida` (usuario_id, ip, fecha y `motivo=IP_NO_PERMITIDA`)
- R4: [x] `SesionControllerIT.adminDesdeIpNoPermitidaCuentaFallos` (6.º intento → 429, también desde `127.0.0.1`)
- R5: [x] `comun/seguridad/FiltroIpsAdminIT.listaVaciaNadieEntra` (403 en ruta admin y 401 + `IP_NO_PERMITIDA` en login desde `127.0.0.1`), `FiltroIpsAdminTest.listaVaciaBloqueaTodo`, `IpsPermitidasAdminTest.listaVaciaNoPermiteNada`, `InicioSesionServiceTest.adminConListaVacia`
- R6: [x] `RecuperacionContrasenaControllerIT.adminDesdeIpPermitidaSinRecuperacion` (0 tokens, 0 envíos, `motivo=ROL_NO_PERMITIDO`), `UsuarioTest.adminNoPermitido`, `UsuarioTest.recuperacionSinAdminIgualQueInicioSesion`
- R7: [x] `RIT.ipNoPermitidaResponde403` (4 endpoints, con y sin token, `sinCambios`), `FiltroIpsAdminTest.rutaAdminDesdeIpNoPermitida` (cuerpo exacto, cadena no ejecutada), `FiltroIpsAdminTest.rutaAdminCodificadaNoEsquivaElFiltro`
- R8: [x] `RIT.ipPermitidaSinSesionResponde401` (sin token y token inventado → 401 *sesión inválida*)
- R9: [x] `RIT.rolDistintoDeAdminResponde403` (USUARIO y ARRENDADOR en los 4 endpoints), `RevisionIdentidadServiceTest.rolDistintoDeAdmin`
- R10: [x] `IpsPermitidasAdminTest.permiteDireccionesExactas`, `permiteRangosCidr` (v4 y v6 dentro/fuera), `ipNulaONoLiteral`
- R11: [x] `IpsPermitidasAdminTest.entradaInvalidaLanza` (8 entradas inválidas → `IllegalStateException` con `ADMIN_IPS_PERMITIDAS` y sin el valor)
- R12: [x] `RIT.archivosNoSeFiltranPorIp` (URL firmada desde IP ajena → 200), `FiltroIpsAdminTest.rutaNoAdminDesdeCualquierIp`
- R13: [x] `comun/web/IpRealIT$ConexionDesdeProxyDeConfianza.usaLaIpDelEncabezado` (HTTP real, 3 casos de `X-Forwarded-For`)
- R14: [x] `IpRealIT$SinProxiesDeConfianza.ignoraElEncabezado` (con `internal-proxies` vacío la IP es `127.0.0.1`; prueba además que el valor vacío anula el regex por defecto de Tomcat, que sí confiaría en loopback)
- R15: [x] `CreacionAdminServiceIT.guardaElAdminEnLaBaseDeDatos` (correo normalizado, rol, estado, flag, versión, fecha del reloj, `encoder.matches`), `CreacionAdminServiceTest.creaLaCuentaAdmin`, `UsuarioTest.registrarAdminFijaRolYDatos`, `CreadorAdminTest.conPropiedadCreaYCierra`
- R16: [x] `CreacionAdminServiceIT.auditaLaCreacion` (usuario_id, entidad, entidad_id, IP y detalle nulos), `CreacionAdminServiceTest.auditaLaCreacion`
- R17: [x] `CreacionAdminServiceTest.contrasenaFueraDePolitica`, `correoInvalido`, `CreadorAdminTest.fallaDetieneElArranque`
- R18: [x] `CreacionAdminServiceIT.correoExistenteNoCambiaNada` (fila idéntica, 1 sola auditoría), `CreacionAdminServiceTest.correoExistente`, `carreraPorElMismoCorreo`
- R19: [x] `CreadorAdminTest.sinPropiedadNoHayComando` (sin propiedad, `false` y `si`), `CreacionAdminServiceIT.sinComandoPorDefecto`
- R20: [x] `RIT.colaOrdenadaPorFechaDeEnvio` (claves exactas y valores), `RIT.colaVacia`, `CuentaServiceImplTest.colaEnElOrdenDeLaConsulta`
- R21: [x] `RIT.colaOrdenadaPorFechaDeEnvio` (fechas distintas + empate resuelto por id)
- R22: [x] `RIT.colaExcluyeOtrosEstados` (VERIFICADA, RECHAZADA, NO_INICIADA, SUSPENDIDA y USUARIO excluidos)
- R23: [x] `RIT.expedienteCompleto` (11 claves exactas, número descifrado y cifrado en BD), `CuentaServiceImplTest.expedienteFirmaLosDocumentos`
- R24: [x] `RIT.expedienteCompleto` (`urlExpiraEn` y `expira` = reloj + 5 min; cada URL → 200 con su MIME)
- R25: [x] `RIT.expedienteFueraDeLaColaDa404` (VERIFICADA, USUARIO, admin, inexistente; sin `/api/archivos` en el cuerpo), `CuentaServiceImplTest.expedienteFueraDeLaColaDa404` (sin tocar `archivos`)
- R26: [x] `RIT.colaOrdenadaPorFechaDeEnvio`, `RIT.expedienteCompleto` (`Cache-Control` contiene `no-store`)
- R27: [x] `RIT.aprobarIdentidad` (201, 3 claves exactas y valores)
- R28: [x] `RIT.aprobarIdentidad` (perfil VERIFICADA, motivo nulo), `PerfilArrendadorTest.aprobarDesdeEnRevision`
- R29: [x] `RIT.aprobarIdentidad` (`admin_id` de la sesión aunque el cuerpo traiga otro), `RevisionIdentidadServiceTest.aprobarGuardaLaRevision`, `RevisionIdentidadTest.aprobarSinMotivo`
- R30: [x] `RIT.aprobarIdentidad` (un aviso exacto), `CuentaServiceImplTest.aprobarAvisaTrasElCommit` (nada antes de `afterCommit`), `falloDelEnvioNoSePropaga`
- R31: [x] `RIT.rechazarIdentidad`, `RevisionIdentidadServiceTest.rechazarGuardaLaRevision`
- R32: [x] `RIT.rechazarIdentidad` (motivo recortado en el perfil y aviso de sesión del arrendador), `PerfilArrendadorTest.rechazarDesdeEnRevision`
- R33: [x] `RIT.rechazarIdentidad` (fila con motivo recortado), `RevisionIdentidadTest.rechazarConMotivo`
- R34: [x] `RIT.rechazarIdentidad` (aviso con correo y motivo recortado), `CuentaServiceImplTest.rechazarAvisaTrasElCommit`
- R35: [x] `RIT.motivoInvalidoResponde400` (`{}`, `null`, `""`, espacios, 501 → 400 en `motivo`; 500 → 201; sin cambios), `MotivoRechazoDTOTest`
- R36: [x] `RIT.restriccionesDeLaTabla` (4 `INSERT` inválidos → `DataIntegrityViolationException`; 2 válidos pasan)
- R37: [x] `RIT.decisionSobreArrendadorNoValido` (inexistente, USUARIO, admin, SUSPENDIDA → 404 sin cambios), `CuentaServiceImplTest.decisionSobreArrendadorNoValidoDa404`
- R38: [x] `RIT.decisionYaTomada` (VERIFICADA, RECHAZADA, NO_INICIADA → 400 *ya revisada*), `RIT.segundaDecisionTrasAprobar`, `PerfilArrendadorTest.decidirFueraDeEnRevisionLanza`
- R39: [x] `RIT.decisionesSimultaneas` (dos hilos con `CountDownLatch`: {201, 400}, una revisión, un aviso, estado coherente)
- R40: [x] `CuentaServiceImplTest.rollbackNoAvisa` (`STATUS_ROLLED_BACK` sin aviso) y los `RIT` de R7–R9, R35, R37, R38 con `sinCambios`/avisos vacíos
- R41: [x] `CuentaServiceImplTest.rolDistintoDeAdmin` (4 métodos × 2 roles, `verifyNoInteractions` de repositorios, almacenamiento y notificador)
- R42: [x] `RIT.auditaCadaDecision` (usuario, entidad, entidad_id, IP, fecha y detalle exacto `decision=…;revision=<id>`), `RevisionIdentidadServiceTest.aprobarAudita`, `rechazarAuditaSinMotivo`
- R43: [x] `RIT.auditaCadaDecision` (ningún detalle de auditoría contiene motivo, número, correo ni `/api/archivos`)
- R44: [x] `ArchivoControllerTest.errorDeLecturaDa404` (`UncheckedIOException` → `RecursoNoEncontradoException`, log sin la clave), `lecturaCorrectaSirveElArchivo`
- R45: [x] `NotificadorCorreoSimuladoTest.identidadAprobadaSinCorreo`, `identidadRechazadaSinCorreoNiMotivo`

## Trazabilidad con la historia

- CA1 (datos y documentos de los arrendadores en revisión): [x] R20, R22, R23, R24, R25 — `CuentaServiceImpl.arrendadoresEnRevision` / `expedienteIdentidad` y pantallas `ColaIdentidades.jsx` / `RevisarIdentidad.jsx`
- CA1 (orden del más antiguo al más reciente): [x] R21 — JPQL `PerfilArrendadorRepository.findEnRevision` (`order by min(d.subidoEn), p.usuarioId`)
- CA2 (aprobar → identidad verificada): [x] R27, R28, R29
- CA2 (correo de aviso): [x] R30, R40 — `afterCommit` en `CuentaServiceImpl.despuesDelCommit`
- CA3 (rechazo con motivo obligatorio → identidad rechazada): [x] R31, R32, R33, R35, R36
- CA3 (correo con el motivo): [x] R34, R40
- (E) cuentas admin solo por script interno: [x] R15–R19 — `CreadorAdmin` (`@ConditionalOnProperty`) + `CreacionAdminService`; sin endpoint ni admin en migraciones
- (E) rol validado en backend en cada solicitud: [x] R9, R37, R38, R39, R41 — rol comprobado en `RevisionIdentidadService.exigirAdmin` y otra vez en `CuentaServiceImpl.exigirAdmin`
- (S) panel admin solo desde IPs permitidas hasta HU-17: [x] R1–R5, R7, R8, R10–R14 — `FiltroIpsAdmin` antes de `FiltroAutenticacionSesion` (`ConfiguracionSeguridad.java:42`), login admin con `IpsPermitidasAdmin` (`InicioSesionService.java:108`), IP real por `RemoteIpValve`
- (I) documentos solo con URL firmada de corta duración: [x] R24, R25, R26, R44
- (R) registro de cada decisión (admin, arrendador, decisión, motivo, fecha): [x] R29, R33, R36, R42, R43 — tabla `revision_identidad` + auditoría `decision=…;revision=…`

La tabla final de `specs/HU-06/requirements.md` cubre los 3 CA y los 4 controles STRIDE, y todos están implementados.

## Tasks completas

- T1: [x]
- T2: [x]
- T3: [x]
- T4: [x]
- T5: [x]
- T6: [x]
- T7: [x]
- T8: [x]
- T9: [x]
- T10: [x]
- T11: [x]
- T12: [x]
- T13: [x] (el caso de R5 vive en `FiltroIpsAdminIT` y no como `@Nested` del RIT; desvío justificado en `progress/impl_HU-06.md`, decisión 1)
- T14: [x]
- T15: [x]
- T16: [x]
- T17: [x]
- T18: [x]
- T19: [x]
- T20: [x]

## Puntos de atención pedidos por el orquestador

- **Filtro de IPs del panel admin:** [x] `FiltroIpsAdmin` (`comun/seguridad/FiltroIpsAdmin.java:44`) se registra en la cadena de Spring Security antes de `FiltroAutenticacionSesion`, así que corre antes de autenticar; usa `PathPatternRequestMatcher` sobre `/api/admin/**` (una ruta con `%61dmin` no lo esquiva: `FiltroIpsAdminTest.rutaAdminCodificadaNoEsquivaElFiltro`). Lista vacía → ningún matcher → `permite` siempre `false` (`IpsPermitidasAdmin.java:30-43`), probado en unitaria e IT. El 403 tiene el mismo cuerpo que la falta de rol (S12). El rol `ADMIN` se valida en `RevisionIdentidadService.java:81` y en `CuentaServiceImpl.exigirAdmin`.
- **D1:** [x] el admin solo inicia sesión desde una IP permitida (`InicioSesionService.java:108`, después de la contraseña: una contraseña incorrecta sigue auditando `CONTRASENA_INCORRECTA`). HU-04 sigue cerrada para el admin: `RecuperacionContrasenaService.java:96` y `:170` usan `Usuario.motivoRechazoRecuperacion()`, que para el resto de cuentas devuelve exactamente lo mismo que antes (`Usuario.java:108-113`: el chequeo de `ADMIN` va primero, como iba antes en `motivoRechazoInicioSesion`). `RecuperacionContrasenaServiceTest` no se tocó; en `RecuperacionContrasenaControllerIT` solo se agregó el caso de R6 y los dos métodos del doble del notificador.
- **D3:** [x] `server.forward-headers-strategy=native` + `server.tomcat.remoteip.internal-proxies=${PROXIES_CONFIANZA:}`. `IpCliente` sigue leyendo solo `getRemoteAddr()`; ningún código de `src/main` lee `X-Forwarded-For`. `IpRealIT$SinProxiesDeConfianza` prueba con HTTP real que, con el valor vacío, un `X-Forwarded-For` desde loopback se ignora (el regex por defecto de Tomcat sí confiaría en loopback, así que el test demuestra que el vacío lo anula).
- **D4:** [x] sin secretos en el repo (`.env.example` sin valores; README con `read -r -s` y `unset`); `CreadorAdmin` solo escribe "Cuenta admin creada" (`CreadorAdminTest` comprueba que no salen correo ni contraseña); los errores nombran la variable y nunca el valor; una cuenta existente no se toca (`CreacionAdminService.java:70-72`; `CreacionAdminServiceIT.correoExistenteNoCambiaNada` compara la fila completa).
- **Reglas de dependencia:** [x] `administracion` solo importa `cuentas.service.CuentaService` y DTO de `cuentas.dto`, nunca repositorios; `comun` no importa ningún módulo. Aviso por correo con `TransactionSynchronization.afterCommit`, sin propagar fallos. S16: la auditoría solo lleva `decision=…;revision=…`; el simulado de correo y los `log.warn` no escriben correo, motivo ni clave.
- **HU-03 / HU-04 / HU-05:** [x] solo cambia lo que declara el spec: HU-03 R13 (admin) → HU-06 R1–R4 y el motivo nuevo `IP_NO_PERMITIDA`; HU-04 sin cambios de conducta; HU-05 solo R44 (`UncheckedIOException` → 404). Las 329 + 225 pruebas, incluidas las de las historias `done`, pasan.

## Checkpoints

- C1 Arnés completo: [x] (verificado por `./init.sh`, exit 0)
- C2 Estado coherente:
  - [x] una sola historia `in_progress` por dueño (HU-06, Ariel Enriquez); existe `progress/HU-06.md`
  - n/a toda historia `done` con tests (HU-06 aún no está `done`; las `done` siguen verdes)
  - [x] aprobación del dueño anotada en `progress/HU-06.md` (Ariel Enriquez, 2026-10-28, D1–D4 y S1–S16)
  - [x] `progress/HU-06.md` describe solo la sesión activa
- C3 Arquitectura:
  - [x] paquetes en módulos previstos (`administracion`, de la tabla); ningún controller importa repositorio; sin `@Autowired` en main, sin Lombok ni comodines; sin prints de depuración
  - [x] `administracion` usa `cuentas` solo vía `CuentaService` (ampliación documentada en `docs/architecture.md`); sin ciclos; sin puertos nuevos
  - [x] la API recibe y devuelve DTO `record`; ninguna entidad sale; claves exactas comprobadas en el RIT
  - [x] el admin que decide sale de la sesión (R29 con `adminId` falso en el cuerpo); estados decididos en el servidor
  - [x] esquema solo por `V2_3__revision_identidad.sql` (`V2_3` libre en `develop` y `origin/develop`); ninguna migración existente editada
  - [x] correo detrás de `NotificadorCorreo`; almacenamiento vía `ArchivosPrivados`
  - [x] nada fuera del alcance
  - [x] sin secretos; sin colores fuera de tokens (todas las `var(--…)` usadas existen en `tokens.css`; el fondo del `Modal` usa `color-mix` sobre `--color-texto`); sin librerías de UI
  - [x] pantallas con el kit (`Tarjeta`, `EtiquetaEstado`, `Alerta`, `Boton`, `Campo` y el `Modal` nuevo, anotado en `docs/frontend.md`); un solo `Boton` primario en la pantalla ("Aprobar identidad"; el "Aprobar" del `Modal` es la confirmación prevista en design § 11); errores de la API en `Alerta error` tal cual, error del motivo bajo el `Campo`, éxito por estado de navegación, lista vacía en texto secundario; llamadas solo vía `src/api/administracion.js`; coincide con design § 11
- C4 Verificación real:
  - [x] `*Test` con Mockito y `*IT` con `@Import(TestcontainersConfiguration.class)` (en `IpRealIT`, en cada `@Nested`)
  - [x] sin H2
  - [x] `RelojControlable` / `Clock` fijo para fechas y para el vencimiento de 5 min
  - [x] IT para endpoints, migración y `CHECK` (R36); concurrencia de R39 lanzada a la vez con dos hilos
  - [x] cada test comprueba un resultado concreto, incluidos los caminos de error
  - [x] `./mvnw verify`: 329 + 225 pruebas verdes
  - [x] `npm run lint && npm run build` sin errores
- C5 Cierre de sesión:
  - [x] sin archivos sin trackear sospechosos
  - n/a entrada de cierre en `progress/history.md` y vuelta de `progress/HU-06.md` a la plantilla (corresponden al cierre, después de esta aprobación)
  - [x] `feature_list.json` refleja `in_progress` (el orquestador/dueño la pasa a `done` al cerrar)
  - [x] trabajo en `feature/HU-06-revision-identidad`
- C6 SDD:
  - [x] `specs/HU-06/` completo
  - [x] EARS estricto y tabla de trazabilidad con todo CA y control STRIDE
  - [x] `design.md` con endpoints, clases, migración, secuencias (principal y de error) y 9 alternativas descartadas (HU-06 no es ★)
  - [x] todas las tasks en `[x]`
  - [x] R1–R45 con test, documentado en `progress/impl_HU-06.md`
  - [x] §9-4 de `docs/requerimientos.md` resuelto por decisión del dueño (D2), no por un `R<n>` por su cuenta

## Observaciones (no bloquean)

1. `specs/HU-03/requirements.md:113-115` sigue diciendo que el `ADMIN` recibe siempre el 401, y
   `SesionControllerIT.rechazosConCuerpoIdentico` conserva "HU-03 … R13" en su `@DisplayName` (justificado en
   `progress/impl_HU-06.md`: `init.sh` exige un test por cada `R<n>` de las historias `done`). Conviene anotar en
   el R13 de HU-03 "Reemplazado por HU-06 R1–R4 (solo aplica desde una IP no permitida)" para que quien lea ese
   spec no crea que el admin nunca entra.
2. `specs/HU-06/design.md:56-59`: la tabla de § 3 tiene dos filas de encabezado (`| Módulo / capa | Archivo | Para |`
   y `| Módulo / capa | Archivos | Para |`); la segunda se ve como una fila de datos. Solo cosmético.
3. `CreacionAdminService.crear`: en la carrera que cubre `carreraPorElMismoCorreo`, PostgreSQL/Hibernate escriben en
   el log el detalle de la violación de unicidad, que incluye el correo (`Key (correo)=(…) already exists`). No es un
   secreto y solo pasa si dos comandos crean el mismo admin a la vez, pero contradice el cuidado de no loguear el
   correo (`CreadorAdmin.java:40`). Aceptable tal cual; tenerlo en cuenta al revisar los logs de UAT.

## Cambios requeridos

Ninguno.
