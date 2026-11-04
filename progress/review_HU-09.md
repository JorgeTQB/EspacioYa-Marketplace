# Review — HU-09

**Veredicto:** APPROVED

> Reviewer, 2026-10-29. Rama `feature/HU-09-suspension-reactivacion`, cambios sin commitear sobre `develop` (39a77aa).
> `./init.sh` completo (sin `--rapido`, Docker encendido): **exit 0**. `./mvnw verify`: 427 unitarias + 290 IT,
> 0 fallos, 0 errores. ESLint sin advertencias, `npm run build` OK, sin colores sueltos ni secretos; validador de
> `feature_list.json` / specs / progress OK. Único `[WARN]`: faltan las llaves de cifrado en el entorno local
> (viene de antes de HU-09 y solo afecta a `spring-boot:run`).

## Trazabilidad requirements ↔ tests
Clases bajo `2_Backend/src/test/java/pe/edu/ulima/espacioya/`. `CAIT` = `administracion.controller.CuentaAdminControllerIT`,
`CAST` = `administracion.service.CuentaAdminServiceTest`, `CSIT` = `cuentas.service.CuentaServiceImplTest`,
`CSIIT` = `cuentas.service.CuentaServiceImplIT`. Leí el cuerpo de cada test citado, no solo el `@DisplayName`.

- R1: [x] `CAIT.buscaPorCorreoTodasLasCuentas` (claves exactas, orden, valores), `CAIT.buscaSinDistinguirMayusculas` (q recortado, mayúsculas en q, nombre y correo), `CSIT.veintiunaCoincidenciasDanVeinteYHayMas`, `CAST.busquedaValidaRecortada`
- R2: [x] `CAIT.maximoVeinteConHayMas` (21 filas insertadas en orden inverso → c01…c20 exactos), `CSIT.veintiunaCoincidenciasDanVeinteYHayMas`
- R3: [x] `CAIT.maximoVeinteConHayMas` (21 → `true`; 20 → `false`), `CSIT.veinteCoincidenciasSinHayMas`
- R4: [x] `CAIT.comodinesLiterales` (`p%q`, `u_v` y `r` + barra invertida + `s`, frente a `pzzq`, `uxv`, `rs`), `CSIT.patronEscapadoYLimite` (patrón escapado exacto y `Limit.of(21)`)
- R5: [x] `CAIT.buscaPorCorreoTodasLasCuentas` (ELIMINADA, SUSPENDIDA y ADMIN incluidas)
- R6: [x] `CAIT.busquedaInvalidaResponde400` (null, "", "  ab ", 101 → 400 con el campo `q` y su mensaje; 100 → 200), `CAST.busquedaInvalida` (`verifyNoInteractions(cuentas)`)
- R7: [x] `CAIT.detalleDeArrendadorActiva` (claves exactas; no aparecen documento, razón social ni dirección), `CSIT.detalleDeArrendador`
- R8: [x] `CAIT.detalleDeArrendadorActiva` (`NO_INICIADA`), `CAIT.detalleBanderasPorRolYEstado` (USUARIO/ADMIN → null), `CSIT.detalleSinIdentidadYBanderas` (sin consultar `perfiles`)
- R9: [x] `CAIT.detalleBanderasPorRolYEstado` (otro ADMIN, el propio admin y ELIMINADA → sin acciones), `CAIT.detalleConSuspensionVigente`, `UsuarioTest.gestionablePorRolYEstado`, `UsuarioTest.banderasDeSuspensionYReactivacion`
- R10: [x] `CAIT.detalleConSuspensionVigente` (la última de dos suspensiones, motivo recortado y fecha del reloj), `CAST.detalleSuspendidaConSuspension`
- R11: [x] `CAIT.detalleDeArrendadorActiva`, `CAIT.detalleConSuspensionVigente` (null tras reactivar), `CAST.detalleNoSuspendidaSinSuspension` (no lee el historial)
- R12: [x] `CAIT.detalleInexistenteResponde404` (mensaje genérico), `CSIT.detalleInexistenteDa404`, `CAST.detalleInexistente`
- R13: [x] `CAIT.buscaPorCorreoTodasLasCuentas`, `CAIT.detalleDeArrendadorActiva` (`Cache-Control` contiene `no-store`)
- R14: [x] `CAIT.suspenderCuenta` (201, solo `accion`/`estado`/`fecha`, fecha = reloj), `CAST.suspenderGuardaYAudita`
- R15: [x] `CAIT.suspenderCuenta` (`usuario.estado` en la BD), `CSIT.suspenderRevocaYAvisaTrasElCommit`, `UsuarioTest.suspenderYReactivar`
- R16: [x] `CAIT.suspenderCuenta` (las 2 sesiones de Ana revocadas, la de Beto no; el token revocado da 401), `CSIT.suspenderRevocaYAvisaTrasElCommit` (`revocarTodas` se llama con el estado ya SUSPENDIDA)
- R17: [x] `CAIT.suspenderCuenta` (`admin_id` de la sesión aunque el cuerpo traiga `adminId`; motivo recortado; fecha), `CAST.suspenderGuardaYAudita`, `SuspensionCuentaTest.suspenderConMotivo`, `MotivoSuspensionDTOTest.recortaElMotivo`
- R18: [x] `CAIT.suspenderCuenta` (un aviso con correo y motivo recortado), `CSIT.suspenderRevocaYAvisaTrasElCommit` (sin aviso antes de `afterCommit`, aviso después)
- R19: [x] `CAIT.motivoInvalidoResponde400` (`{}`, null, "", "   ", 501 → 400 con el campo `motivo`; sin cambios ni sesiones revocadas; 500 → 201), `MotivoSuspensionDTOTest.motivoAusenteOEnBlanco`, `.longitudMaxima`, `CSIT.suspenderSinMotivoLanza`
- R20: [x] `CAIT.inicioDeSesionTrasSuspenderYReactivar` (cuerpo idéntico al de una contraseña incorrecta, sin sesión nueva)
- R21: [x] `SesionServiceTest.cuentaNoActivaVacioSinRenovar` (SUSPENDIDA y ELIMINADA; `expira_en` intacto), `CAIT.sesionDeCuentaSuspendidaResponde401` (sesión insertada tras suspender → 401 *sesión inválida*, `expira_en` sin cambio)
- R22: [x] `FotoPublicaControllerIT.fotoPublicadaSegunElEstadoDelArrendador` (404 sin sesión y con la sesión de otra cuenta), `FotoEspacioServiceTest.verPublicadaDeArrendadorNoActivo` (no lee el archivo), `.verBorradorNoConsultaCuentas`
- R23: [x] `DisponibilidadServiceImplIT.horasLibresSegunElEstadoDelArrendador` (SUSPENDIDA y ELIMINADA → vacía), `DisponibilidadServiceImplTest.arrendadorNoActivoDevuelveVacio` (sin consultar horarios, bloqueos ni el puerto)
- R24: [x] `FotoPublicaControllerIT.fotoPublicadaSegunElEstadoDelArrendador` (200, `public, max-age=3600`, mismos bytes)
- R25: [x] `DisponibilidadServiceImplIT.horasLibresSegunElEstadoDelArrendador` (las mismas horas que antes)
- R26: [x] `CAIT.espacioConservaSuEstado` (sigue PUBLICADO tras suspender y tras reactivar por la API), además de las dos IT anteriores
- R27: [x] `CSIIT.cuentaActivaContraLaBaseDeDatos`, `CSIT.cuentaActivaSoloSiExisteYEstaActiva` (también null)
- R28: [x] `CSIIT.arrendadoresNoActivosContraLaBaseDeDatos` (ARRENDADOR SUSPENDIDA y ELIMINADA, sin USUARIO ni ADMIN), `CSIIT.arrendadoresNoActivosVacio`, `CSIT.arrendadoresNoActivosComoConjunto`
- R29: [x] `CAIT.reactivarCuenta` (201; el cuerpo enviado se ignora), `CAST.reactivarGuardaYAudita`
- R30: [x] `CAIT.reactivarCuenta`, `CSIT.reactivarAvisaTrasElCommit`, `UsuarioTest.suspenderYReactivar`
- R31: [x] `CAIT.reactivarCuenta` (fila sin motivo, `admin_id` y fecha), `CAST.reactivarGuardaYAudita`, `SuspensionCuentaTest.reactivarSinMotivo`
- R32: [x] `CAIT.reactivarCuenta` (aviso REACTIVADA), `CSIT.reactivarAvisaTrasElCommit` (sin `revocarTodas`; aviso solo tras `afterCommit`)
- R33: [x] `CAIT.inicioDeSesionTrasSuspenderYReactivar` (201 y `GET /api/sesiones/actual` → 200 ARRENDADOR)
- R34: [x] `CAIT.cuentaInexistenteResponde404`, `CSIT.cambioSobreCuentaInexistenteDa404`, `CAST.rechazoDeCuentaServiceSinRegistroNiAuditoria`
- R35: [x] `CAIT.cuentaNoGestionableResponde400` (otro ADMIN, el propio admin, ELIMINADA; la sesión del admin sigue válida), `CSIT.cambioSobreCuentaNoGestionableDa400`, `UsuarioTest.suspenderFueraDeEstadoLanza`, `.reactivarFueraDeEstadoLanza`
- R36: [x] `CAIT.estadoIgualResponde400`, `CSIT.cambioSobreEstadoIgualDa400`, `UsuarioTest.suspenderFueraDeEstadoLanza`
- R37: [x] `CAIT.estadoIgualResponde400`, `CSIT.cambioSobreEstadoIgualDa400`, `UsuarioTest.reactivarFueraDeEstadoLanza`
- R38: [x] `CAIT.suspensionesSimultaneas` (2 hilos con `CountDownLatch` → 201 y 400 *ya suspendida*, 1 fila, 1 auditoría, 1 aviso)
- R39: [x] `CSIT.suspensionYReactivacionRevertidasNoAvisan` (`ROLLED_BACK` → sin aviso), los tres `CSIT.cambioSobre...` (sin sincronización registrada), `CAIT.motivoInvalidoResponde400`, `.cuentaInexistenteResponde404`, `.cuentaNoGestionableResponde400`, `.estadoIgualResponde400` (sin avisos)
- R40: [x] `CSIT.rolDistintoDeAdminEnMetodosDeSuspension` (USUARIO, ARRENDADOR y null en los 4 métodos → `AccesoDenegadoException`; `verifyNoInteractions` de los repositorios)
- R41: [x] `CAIT.rolDistintoDeAdminResponde403` (USUARIO y ARRENDADOR, los 4 endpoints, cuerpo válido), `CAST.rolDistintoDeAdmin`
- R42: [x] `CAIT.ipNoPermitidaResponde403` (con y sin Bearer de admin, los 4 endpoints → 403, sin cambios)
- R43: [x] `CAIT.restriccionesDeLaTabla` (acción desconocida, SUSPENSION sin motivo o en blanco y REACTIVACION con motivo → `DataIntegrityViolationException`; las válidas entran), `SuspensionCuentaTest.suspenderSinMotivoLanza`
- R44: [x] `CAIT.auditaCadaAccion` (usuario, entidad `usuario`, entidad_id, IP, fecha y detalle `accion=SUSPENSION;registro=<id>;sesionesRevocadas=2`), `CAST.suspenderGuardaYAudita`
- R45: [x] `CAIT.auditaCadaAccion` (detalle `accion=REACTIVACION;registro=<id>`), `CAST.reactivarGuardaYAudita`
- R46: [x] `CAIT.auditaCadaAccion` (ningún detalle de `auditoria` contiene el motivo, el teléfono del motivo ni el correo; búsqueda y detalle no se auditan), `CAST.suspenderGuardaYAudita`, `.reactivarGuardaYAudita`
- R47: [x] `NotificadorCorreoSimuladoTest.cuentaSuspendidaSinCorreoNiMotivo`, `.cuentaReactivadaSinCorreo` (stdout y stderr)

## Trazabilidad con la historia
- CA1 (busco por correo o nombre → datos, rol y estado): [x] R1–R13. Pantallas `BuscarCuentas.jsx` y `DetalleCuenta.jsx` según design § 10
- CA2 (suspender con motivo y confirmar): [x] R14, R15, R17, R19, R35, R36, R38, R43. `CuentaAdminService.suspender` (`CuentaAdminService.java:80-88`) + `CuentaServiceImpl.suspenderCuenta` (`CuentaServiceImpl.java:177-194`)
- CA2 · sesiones invalidadas: [x] R16 (`revocarTodas`, `CuentaServiceImpl.java:191`), R21 (`SesionService.validar` filtra `ACTIVA` antes de renovar)
- CA2 · no puede iniciar sesión: [x] R20 (camino de HU-03 R12, probado de punta a punta)
- CA2 · espacios ocultos del catálogo: [x] R22, R23, R26, R27, R28. `FotoEspacioService.ver` y `DisponibilidadServiceImpl.horasLibres` con `cuentaActiva`; el catálogo y la ficha (HU-10/HU-11) quedan obligados por la regla escrita en `docs/architecture.md` § Puertos entre módulos (S14, aprobado)
- CA2 · correo con el motivo: [x] R18, R39, R47 (`despuesDelCommit`, `CuentaServiceImpl.java:192`)
- CA3 (reactivar): [x] R29, R30, R31, R37 · recupera el acceso: [x] R33 · espacios visibles otra vez: [x] R24, R25, R26
- (E) Rol admin validado en backend en cada solicitud: [x] R35, R40, R41. `exigirAdmin` en `CuentaAdminService.java:102-106` y otra vez en los 4 métodos admin de `CuentaServiceImpl`; el rol sale del `UsuarioAutenticado` que se relee en cada `validar`
- (S) Mismo control de IPs que HU-06: [x] R42. Rutas bajo `/api/admin/cuentas`, cubiertas por `FiltroIpsAdmin.PREFIJO = "/api/admin/"` sin tocar `comun`
- (R) Registro de cada acción (admin, cuenta, acción, motivo, fecha): [x] R17, R31, R43, R44, R45, R46. Tabla `suspension_cuenta` (V3_1) + `AuditoriaService` (REQUIRED, misma transacción)

## Tasks completas
- T1: [x] `Usuario` (`esGestionablePorAdmin`, `puedeSuspenderse`, `puedeReactivarse`, `suspender`, `reactivar`) + 5 tests
- T2: [x] `UsuarioRepository` (`findParaActualizar` con `PESSIMISTIC_WRITE`, `buscar` con `escape` y `Limit`, `findIdsArrendadoresNoActivos` con enums calificados)
- T3: [x] 4 DTO `record` de `cuentas`, 2 avisos en `NotificadorCorreo`, simulado sin correo ni motivo, los 4 `NotificadorFalso` (solo se agregaron los métodos)
- T4: [x] `CuentaService` / `CuentaServiceImpl` (6 métodos, mensajes de design § 4 al pie de la letra) + 16 tests unitarios + `CuentaServiceImplIT`
- T5: [x] `SesionService.validar` + test
- T6: [x] `FotoEspacioService.ver` + 2 unitarios + IT
- T7: [x] `DisponibilidadService` (javadoc) / `Impl` + unitario + IT
- T8: [x] `V3_1__suspension_cuenta.sql`, idéntica a design § 8; `V3_1` libre en `origin/develop`
- T9: [x] `AccionSuspension`, `SuspensionCuenta` (fábricas, sin setters), `SuspensionCuentaRepository` + `SuspensionCuentaTest`
- T10: [x] 4 DTO de `administracion` + `CuentaAdminService` + `MotivoSuspensionDTOTest` + `CuentaAdminServiceTest`
- T11: [x] `CuentaAdminController` (4 endpoints, `no-store` en los GET, `q` opcional, `@Valid`, `IpCliente`) + `CuentaAdminControllerIT` (23 casos)
- T12: [x] `EtiquetaEstado` (+3 estados con los colores de la tabla de `docs/frontend.md`) y `NOMBRES_ROL`
- T13: [x] 4 funciones en `api/administracion.js` (con `encodeURIComponent`)
- T14: [x] `BuscarCuentas.jsx` + CSS Module
- T15: [x] `DetalleCuenta.jsx` + CSS Module
- T16: [x] enlace en `Panel.jsx` y 2 rutas en `RutaProtegida` en `App.jsx`
- T17: [x] `docs/architecture.md`, `docs/modelo-datos.md`, `docs/frontend.md`; aviso a HU-10/11/12 anotado en `progress/HU-09.md:76-80`
- T18: [x] `./init.sh` en verde (lo confirmó este reviewer) + `progress/impl_HU-09.md` con el mapa R → test

## Puntos pedidos por el orquestador (verificados)
- **D1:** `CuentaService.cuentaActiva` y `arrendadoresNoActivos` (`CuentaServiceImpl.java:212-223`) no exigen rol, y su javadoc explica la colección vacía. `espacios → cuentas` va solo vía `CuentaService`. `cuentas` no importa `espacios`, `administracion` ni otros módulos, y ni `espacios` ni `administracion` importan `cuentas.domain` o `cuentas.repository`: no hay ciclos. `espacio.estado` no se toca (no hay escrituras sobre `espacio`; R26 probado en 3 IT). En `FotoEspacioService.ver` el `&&` hace que `cuentaActiva` solo se consulte para un PUBLICADO (`verBorradorNoConsultaCuentas`).
- **D2:** `Usuario.esGestionablePorAdmin` (USUARIO/ARRENDADOR y no ELIMINADA). `paraCambiarEstado` comprueba existe → gestionable → estado (`CuentaServiceImpl.java:227-233`). El propio admin queda fuera porque es ADMIN (probado por la IT, y su sesión sigue válida).
- **D3:** `SesionService.validar` agrega solo un filtro `estado == ACTIVA` antes de `renovar`; para una cuenta ACTIVA el comportamiento no cambia (SesionControllerIT 28/28 y SesionServiceTest de HU-03 sin modificar). `revocarTodas` corre dentro de la transacción de `CuentaAdminService.suspender`, después de `suspender()`. Con `flushAutomatically` el estado se escribe antes del UPDATE, y la entidad no se vuelve a usar tras el `clear`.
- **D4 / CA2:** los dos avisos salen por `despuesDelCommit` (`afterCommit`). Un fallo del envío solo deja un `log.warn` genérico, sin correo ni motivo. El simulado escribe un texto fijo (R47) y la auditoría lleva solo `accion`, `registro` y `sesionesRevocadas` (R46).
- **Concurrencia:** `findParaActualizar` con `@Lock(PESSIMISTIC_WRITE)`; probado con dos hilos reales.
- **Búsqueda:** `escaparComodines` escapa primero la barra invertida y luego `%` y `_` (`CuentaServiceImpl.java:236-238`); el JPQL declara `escape` en las dos ramas del `like`. Pide `Limit.of(21)` y devuelve 20.
- **Regresión HU-03/06/07/08:** solo cambiaron constructores, stubs (`cuentaActiva → true`) y los `NotificadorFalso`. El texto del `log.warn` de HU-06 cambió a uno genérico, que ningún test ni spec fija. La cola de HU-06 ya filtra `u.estado = ACTIVA` (`PerfilArrendadorRepository.java:38`), así que S10 se cumple sin código. Los cambios de comportamiento son exactamente los declarados en "Reemplazos y compatibilidad" de `requirements.md`.

## Observaciones no bloqueantes
1. `CuentaServiceImpl.suspenderCuenta` lanza `IllegalArgumentException` con un motivo nulo o en blanco (`:179-181`). Por la API nunca pasa (`@Valid` lo frena antes, R19), pero si otro módulo lo llamara así recibiría un 500. Está documentado en `progress/impl_HU-09.md`. Si HU-27 lo reutiliza, conviene cambiarlo por una `ReglaNegocioException` con el campo `motivo`.
2. En `DetalleCuenta.jsx`, con el `Modal` de reactivar abierto se ven a la vez el `Boton` primario de la página y el de confirmación del modal. Es lo que fija design § 10 y el mismo patrón de HU-06, así que se acepta.
3. Si HU-10, HU-11 o HU-12 llegan antes a `develop` con una `V3_1`, renombrar esta migración (S12).

## Checkpoints
- C1: [x] archivos base · [x] docs · [x] historias · [x] 4 subagentes · [x] `./init.sh` completo exit 0
- C2: [x] una `in_progress` por dueño · [x] existe `progress/HU-09.md` · n/a historias `done` (HU-09 no está cerrada) · [x] aprobación de Jorge Quispe anotada (`progress/HU-09.md:14, 48`) · [x] progress solo de la sesión activa
- C3: [x] paquetes en módulos · [x] ningún controller importa un repository · [x] sin `@Autowired`, Lombok ni comodines · [x] sin prints · [x] dependencias solo vía `CuentaService`; sin puerto nuevo (`administracion → cuentas` y `espacios → cuentas` permitidas) · [x] DTO `record`; ninguna entidad expuesta; el detalle no lleva documento, razón social ni dirección · [x] identidad del admin desde la sesión (`admin_id` ignora el cuerpo); estados y banderas calculados en el servidor · [x] migración nueva `V3_1__suspension_cuenta.sql`, ninguna editada · [x] correo detrás de `NotificadorCorreo` (Adapter simulado) · [x] nada fuera del alcance · [x] sin secretos · [x] sin colores fuera de tokens ni librerías · [x] pantallas con el kit (`Campo`, `Boton`, `Alerta`, `Tarjeta`, `EtiquetaEstado`, `Modal`), sin estilos en línea, mensajes de design § 10 y `docs/frontend.md`, API solo desde `src/api/administracion.js`
- C4: [x] `*Test` / `*IT` con `@Import(TestcontainersConfiguration.class)` · [x] sin H2 · [x] `Clock` fijo / `RelojControlable` (fechas del registro y la auditoría, vencimiento de la sesión) · [x] IT de endpoints, restricciones de BD y concurrencia con dos solicitudes a la vez · [x] cada test comprueba resultados concretos (HTTP, claves y valores, filas, auditoría, avisos) · [x] `mvnw verify`: 717 tests, todos verdes · [x] lint + build
- C5: [x] sin archivos sospechosos sin trackear · [ ] falta la entrada de esta sesión en `progress/history.md` (la agrega el orquestador al cerrar; no bloquea la revisión) · n/a progress a plantilla (historia aún no `done`) · [x] `feature_list.json` en `in_progress` (correcto hasta el cierre) · [x] rama `feature/HU-09-suspension-reactivacion`
- C6: [x] `specs/HU-09/` completo · [x] EARS + tabla de trazabilidad con CA1–CA3, (E), (S), (R) · [x] design con endpoints, clases, migración, 2 diagramas de secuencia, Pantallas y 7 alternativas descartadas; historia sin ★ · n/a tasks de `done` · [x] cada `R<n>` con test, documentado en `progress/impl_HU-09.md` · [x] ningún pendiente de §9 resuelto sin el dueño (D1–D4 y S1–S15 aprobados por Jorge Quispe)

## Cambios requeridos
Ninguno bloqueante. Al cerrar la sesión, agregar la entrada de HU-09 en `progress/history.md` (C5).
