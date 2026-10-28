# Review — HU-03

**Veredicto:** APPROVED

> Revisado el 2026-10-28 sobre la rama `feature/HU-03-inicio-cierre-sesion` (todo el trabajo
> está sin commitear; `git log develop..HEAD` vacío). Spec aprobado por Jorge Quispe con
> P1–P7 por defecto (anotado en `progress/HU-03.md`). `./init.sh` completo (sin `--rapido`):
> **EXIT=0**. 118 unitarias y 89 IT (`./mvnw verify`, Testcontainers) en verde, ESLint sin
> advertencias, build OK y chequeos de arquitectura en OK.

## Trazabilidad requirements ↔ tests
- R1: [x] `SesionControllerIT.loginCorrectoResponde201ConCamposMinimos` (correo con mayúsculas y espacios → 201, claves exactas token/rol/aviso), `InicioSesionServiceTest.loginCorrectoDeUsuario`
- R2: [x] `SesionControllerIT.guardaSesionConFechasDelReloj` (usuario_id, creada_en = reloj, expira_en = +30 min, revocada = false), `SesionTest.iniciarFijaFechasYEstado`
- R3: [x] `SesionControllerIT.guardaSoloElHashDelToken` (hash = SHA-256, ninguna columna contiene el token), `TokenAleatorioTest.hashEsSha256Hex` (vector conocido de "abc")
- R4: [x] `SesionControllerIT.arrendadorRecibeAviso`, `InicioSesionServiceTest.arrendadorNoIniciadaRecibeAviso`
- R5: [x] `SesionControllerIT.sinAvisoParaUsuarioYArrendadorVerificado`, `InicioSesionServiceTest.arrendadorVerificadoSinAviso`
- R6: [x] `TokenAleatorioTest.tokenEnBase64UrlSinRelleno` (regex de 43 caracteres base64url), `TokenAleatorioTest.dosTokensDistintos`, `SesionControllerIT.tokensDistintos`
- R7: [x] `SesionControllerIT.loginCorrectoResponde201ConCamposMinimos` (sin correo, hash ni id)
- R8: [x] `SesionControllerIT.ignoraRolDelCliente` (rol ADMIN en el cuerpo → USUARIO)
- R9: [x] `SesionControllerIT.rechazosConCuerpoIdentico`, `InicioSesionServiceTest.contrasenaIncorrecta`, `ManejadorGlobalErroresTest.credencialesInvalidasResponde401`
- R10: [x] `SesionControllerIT.rechazosConCuerpoIdentico` (cuerpo idéntico a R9), `InicioSesionServiceTest.correoDesconocido`
- R11: [x] `SesionControllerIT.rechazosConCuerpoIdentico`, `InicioSesionServiceTest.correoNoVerificado`, `UsuarioTest.correoNoVerificado`
- R12: [x] `SesionControllerIT.rechazosConCuerpoIdentico`, `InicioSesionServiceTest.cuentaNoActiva`, `UsuarioTest.cuentaNoActiva`
- R13: [x] `SesionControllerIT.rechazosConCuerpoIdentico`, `InicioSesionServiceTest.adminNoPermitido`, `UsuarioTest.adminNoPermitido`
- R14: [x] `InicioSesionServiceTest.comparaContraHashSenuelo` (matches contra el señuelo una sola vez)
- R15: [x] `SesionControllerIT.malFormadasNoCuentan` (correo vacío, contraseña vacía y de 73 → 400 por campo; sin sesión)
- R16: [x] `SesionControllerIT.bloqueaTrasCincoFallos` (6.º intento con contraseña correcta → 429, sin sesión), `InicioSesionServiceTest.bloqueadoNoComparaContrasena` (sin matches)
- R17: [x] `SesionControllerIT.correoInexistenteTambienBloquea`, `InicioSesionServiceTest.correoDesconocido` y `contrasenaIncorrecta` (el limitador registra el fallo)
- R18: [x] `SesionControllerIT.malFormadasNoCuentan` (5 respuestas 400 + 4 fallos → 201), `InicioSesionServiceTest.bloqueadoNoRegistraFallo`, `LimitadorIntentosTest.superaLimiteNoRegistra`
- R19: [x] `SesionControllerIT.desbloqueaAlPasarLaVentana` (15 min menos 1 s → 429; +2 s → 201), `LimitadorIntentosTest.dejaDeContarAlPasarLaVentana`
- R20: [x] `SesionControllerIT.loginCorrectoReiniciaContador`, `InicioSesionServiceTest.loginCorrectoReiniciaFallos`, `LimitadorIntentosTest.limpiarReiniciaContador`
- R21: [x] `SesionControllerIT.otroCorreoMismaIpNoSeBloquea`, `LimitadorIntentosTest.clavesDeLoginIndependientes`
- R22: [x] `SesionControllerIT.actualUsaLaIdentidadDeLaSesion` (parámetros usuarioId y rol ignorados), `FiltroAutenticacionSesionTest.bearerValidoAutentica`, `SesionServiceTest.vigenteRenuevaYDevuelveUsuario`
- R23: [x] `SesionControllerIT.actualUsaLaIdentidadDeLaSesion` (claves exactas rol/aviso), `SesionControllerIT.actualArrendadorConAviso`, `SesionServiceTest.actualConAviso`
- R24: [x] `SesionControllerIT.actividadRenuevaLaSesion` (20 + 20 min → 200 y expira_en = ahora + 30), `SesionTest.renovarMueveExpiracion`, `SesionServiceTest.vigenteRenuevaYDevuelveUsuario`
- R25: [x] `SesionControllerIT.cerrarSesionRevoca` (204, cuerpo vacío, revocada = true), `SesionServiceTest.cerrarRevocaYAudita`
- R26: [x] `SesionControllerIT.cerrarSesionRevoca` (GET y DELETE posteriores → 401 sesión inválida), `SesionServiceTest.revocadaVacio`
- R27: [x] `SesionControllerIT.inactividadInvalidaLaSesion` (+30 min exactos → 401, expira_en sin cambio), `SesionTest.noVigenteEnElLimiteExacto`, `SesionServiceTest.vencidaVacioSinRenovar`
- R28: [x] `SesionControllerIT.sinTokenValidoResponde401` (sin encabezado, Basic, token inventado, DELETE sin token), `FiltroAutenticacionSesionTest` (3 casos), `SesionServiceTest.tokenDesconocidoVacio`
- R29: [x] `SesionControllerIT.cerrarNoAfectaOtrasSesiones`
- R30: [x] `SesionControllerIT.auditaLoginExitoso` (usuario, entidad sesion + id, IP, fecha = reloj), `InicioSesionServiceTest.auditaLoginExitoso`
- R31: [x] `SesionControllerIT.auditaLoginFallido`, `SesionControllerIT.rechazosConCuerpoIdentico` (los cinco motivos en orden), `InicioSesionServiceTest` (un test por motivo)
- R32: [x] `SesionControllerIT.bloqueaTrasCincoFallos` (usuario, IP, fecha), `SesionControllerIT.correoInexistenteTambienBloquea` (usuario nulo), `InicioSesionServiceTest.bloqueadoCorreoInexistente`
- R33: [x] `SesionControllerIT.cerrarSesionRevoca` (usuario, entidad sesion + id, IP, fecha), `SesionServiceTest.cerrarRevocaYAudita`
- R34: [x] `SesionControllerIT.auditoriaSinDatosSensibles` (las 4 acciones), `InicioSesionServiceTest.detalleSinDatosSensibles`
- R35: [x] `InicioSesionDTOTest.toStringEnmascarado`
- R36: [x] `SesionControllerIT.preflightPermiteAuthorization`

Todos los tests comprueban un resultado concreto (código HTTP, cuerpo, fila en BD, evento de auditoría o estado del dominio). Los plazos usan `Clock` fijo o `RelojControlable`, sin esperas.

## Trazabilidad con la historia
- CA1 (credenciales correctas + correo verificado → panel del rol): [x] R1, R2, R3, R6, R8, R11, R22, R23; panel en `Panel.jsx` + `RutaProtegida` (design § 12)
- CA1 / HU-02 CA3 (aviso de identidad): [x] R4, R5, R23. `AvisoCuenta.para`, `Alerta tipo="atencion"` en `Panel.jsx`
- CA2 (mensaje genérico): [x] R9 a R14. `CredencialesInvalidasException` → 401 único; `IniciarSesion.jsx` muestra el `ErrorDTO` tal cual
- CA3 (5 fallos en 15 min → bloqueo temporal): [x] R16 a R21. `LimitadorIntentos.superaLimite/registrar/limpiar`, clave `login|<correo>`
- CA4 (cerrar sesión → inválida): [x] R25, R26, R29. `SesionService.cerrar`, `Sesion.revocar`
- CA4 (30 min inactivo → inválida): [x] R2, R24, R27. `Sesion.estaVigente/renovar`, `SesionService.validar`
- (D) Rate limiting y bloqueo ante fuerza bruta: [x] R16 a R19 y R21, implementados en `InicioSesionService.iniciar` (líneas 67-71 y 77)
- (I) Mensaje de error genérico: [x] R9 a R14, R26 a R28 (`ErrorDTO.MENSAJE_CREDENCIALES_INVALIDAS`, `MENSAJE_SESION_INVALIDA`, `PuntoEntradaNoAutenticado`)
- (I) TLS: [x] lo cubre el despliegue (DT-11) más R3, R7, R35 y R36 en código; nota de despliegue en `progress/impl_HU-03.md`
- (S) Token invalidado en backend al cerrar o expirar: [x] R3, R25 a R28 (`FiltroAutenticacionSesion` + puerto `comun.ValidadorSesion` implementado por `cuentas.SesionService`)
- (R) Registro de intentos (fecha, IP, resultado): [x] R30 a R34 (`LOGIN_EXITOSO`, `LOGIN_FALLIDO` con motivo, `LOGIN_BLOQUEADO`, `CIERRE_SESION`)
- MFA fuera de la historia: [x] no se implementó nada de MFA

## Tasks completas
- T1: [x] `comun/seguridad/TokenAleatorio.java` + `TokenAleatorioTest`
- T2: [x] `LimitadorIntentos` (`superaLimite`, `registrar`, `limpiar`, `purgar` privado; `intentar` sin cambio de comportamiento) + 5 tests nuevos; tests previos intactos
- T3: [x] excepciones, constantes de `ErrorDTO`, handlers 401/429 + `ManejadorGlobalErroresTest`
- T4: [x] `ValidadorSesion`, `UsuarioAutenticado`, `FiltroAutenticacionSesion` (no es `@Component`), `PuntoEntradaNoAutenticado` + `FiltroAutenticacionSesionTest`
- T5: [x] `ConfiguracionSeguridad`: STATELESS, filtro con `addFilterBefore`, punto de entrada, CORS con `Authorization`, `permitAll` de `POST /api/sesiones`, comentario CSRF (DT-3)
- T6: [x] `V1_4__sesion.sql` idéntica al design § 9 (ninguna migración previa editada)
- T7: [x] `Sesion`, `MotivoRechazoInicioSesion`, `Usuario.motivoRechazoInicioSesion` (orden ADMIN, no verificado, no activa), `PerfilArrendador.AVISO_IDENTIDAD`, `ArrendadorController.AVISO_IDENTIDAD` apunta a ella + `SesionTest`, `UsuarioTest`
- T8: [x] `SesionRepository.findByHashToken`
- T9: [x] `InicioSesionService` sigue el flujo de design § 6 + `InicioSesionServiceTest`
- T10: [x] `SesionService` + `SesionServiceTest`
- T11: [x] DTOs, `SesionController`, `InicioSesionDTOTest`, `SesionControllerIT` (25 tests)
- T12: [x] `api/tokenSesion.js`, `api/cliente.js` (Bearer, 401 con token → `borrarToken` + `alPerderSesion`, 204 → null), `api/cuentas.js`
- T13: [x] `sesion/ContextoSesion.js`, `ProveedorSesion.jsx`, `useSesion.js`, `RutaProtegida.jsx`; `main.jsx` con `ProveedorSesion` dentro de `BrowserRouter`
- T14: [x] `pages/cuentas/IniciarSesion.jsx` (+ CSS) en `/iniciar-sesion`
- T15: [x] `pages/cuentas/Panel.jsx` (+ CSS) en `/panel` dentro de `RutaProtegida`
- T16: [x] `App.jsx`: rutas, encabezado con "Iniciar sesión" o "Mi panel" + `Boton` secundario "Cerrar sesión", enlace en `/`
- T17: [x] `docs/modelo-datos.md` (`sesion.hash_token`), `docs/frontend.md` (carpeta `sesion/`, "Cómo viaja la sesión", regla de `localStorage`)
- T18: [x] `progress/impl_HU-03.md` con el mapa R1 a R36 y la nota de despliegue (HTTPS, una instancia)
- T19: [x] `./init.sh` completo en verde (verificado por el reviewer, EXIT=0)

## Revisión por archivo (resumen)
- **Arquitectura:** capas respetadas; `SesionController` solo usa servicios; `comun` no importa ningún módulo (grep sin coincidencias); el puerto `comun.ValidadorSesion` ya estaba en la tabla de `docs/architecture.md`. La identidad sale siempre de `@AuthenticationPrincipal UsuarioAutenticado`; el rol se calcula en el servidor. Las entidades no salen de la API (records `SesionIniciadaDTO`, `SesionActualDTO`).
- **Convenciones:** nombres en español, records para DTO, inyección por constructor con campos `final`, migración `V1_4__sesion.sql`, `ErrorDTO` con mensajes genéricos, sin comodines ni Lombok. Los comentarios que hay explican un "por qué" (filtro que no es `@Component`, orden de motivos, señuelo, CHAR(64)).
- **Desviaciones del design, todas justificadas en `impl_HU-03.md`:** `AvisoCuenta` como clase de paquete (opción explícita en design § 5); `@JdbcTypeCode(SqlTypes.CHAR)` (ajuste previsto en design § 9); entidad `usuario` en `LOGIN_FALLIDO` y `LOGIN_BLOQUEADO` (R31/R32 no la fijan); `toString` enmascarado de `SesionIniciadaDTO`; `autenticada: false` en el login y campo `estado` en el error del cliente.
- **Frontend (`docs/frontend.md` y design § 12):** solo tokens (`var(--...)`), CSS Modules, sin estilos en línea, pantallas con `Boton`/`Campo`/`Alerta` del kit, sin componentes nuevos. Hay un solo botón primario por pantalla (el de "Iniciar sesión"; "Cerrar sesión" es secundario). Los errores de API van en `Alerta error` con el mensaje tal cual, los errores por campo bajo cada `Campo` y el error de red con el texto estándar. El éxito "Cerraste sesión." se muestra como `Alerta exito` y el aviso de identidad como `Alerta atencion`. Las llamadas van solo por `src/api/` y el token solo en `sessionStorage`.
- **Patrón ★:** n/a (`patterns: []`).

## Checkpoints
- C1 (arnés completo): [x] archivos base, docs, historias, 4 subagentes; `./init.sh` EXIT=0
- C2 (estado coherente):
  - una historia `in_progress` por dueño: [x]
  - `progress/HU-03.md` existe: [x]
  - historias `done` con tests verdes: [x] (HU-01 y HU-02 siguen verdes)
  - aprobación del dueño anotada antes de `in_progress`: [x] (`progress/HU-03.md`)
  - `progress/HU-03.md` solo de la sesión activa: [x]
- C3 (arquitectura):
  - paquetes en módulos previstos: [x]
  - controller sin repository: [x]
  - sin `@Autowired` en `src/main`, sin Lombok, sin comodines: [x]
  - sin prints de depuración: [x]
  - dependencias entre módulos vía service o puerto; `comun` sin dependencias a módulos: [x]
  - API con DTO `record`, sin campos sensibles: [x]
  - identidad desde la sesión; roles y estados en el servidor: [x]
  - esquema solo por Flyway, sin editar migraciones subidas: [x]
  - integraciones externas tras Adapter: n/a (no hay integraciones externas nuevas)
  - nada fuera del alcance: [x]
  - sin secretos trackeados: [x]
  - frontend sin colores fuera de `tokens.css` ni librerías de UI: [x]
  - pantallas con el kit, mensajes según `frontend.md`, API solo desde `src/api/`: [x]
- C4 (verificación real):
  - `<Clase>Test` / `<Clase>IT` con `@Import(TestcontainersConfiguration.class)`: [x]
  - sin H2: [x]
  - plazos (15 min, 30 min) con `Clock` fijo o `RelojControlable`: [x]
  - historia con endpoints y migración tiene `*IT`: [x]; concurrencia: n/a (DT-6 acepta que el contador no sea atómico)
  - cada test comprueba un resultado concreto, incluido el camino de error: [x]
  - `./mvnw verify` con más de 0 tests y todos verdes (118 + 89): [x]
  - `npm run lint` y `npm run build` sin errores: [x]
- C5 (cierre de sesión):
  - sin archivos sin trackear sospechosos (`target/`, `dist/`, `node_modules/` ignorados): [x]
  - entrada en `progress/history.md` por esta sesión: n/a (la escribe el orquestador al cerrar la historia)
  - `progress/HU-03.md` vuelto a la plantilla: n/a (la historia aún no está `done`)
  - estado en `feature_list.json` coherente (`in_progress`): [x]
  - trabajo en `feature/HU-03-inicio-cierre-sesion`: [x]
- C6 (SDD):
  - `specs/HU-03/` con los 3 archivos: [x]
  - requirements en EARS con tabla de trazabilidad que cubre CA1 a CA4 y STRIDE D/I/S/R: [x]
  - design con endpoints, clases, migración, secuencia y alternativas descartadas: [x]
  - tasks todas `[x]`: [x]
  - cada `R<n>` con test documentado en `impl_HU-03.md`: [x]
  - ningún `R<n>` resuelve un pendiente de §9 sin decisión del dueño: [x] (P1 a P7 aprobadas por Jorge Quispe)

## Cambios requeridos
Ninguno.

## Observaciones no bloqueantes
1. `SesionService.validar` (`SesionService.java`, líneas 44-52) no comprueba `usuario.estado`: si una cuenta se suspende, sus sesiones abiertas siguen valiendo hasta que se revoquen o venzan. Está fuera del alcance de HU-03 (HU-09 debe revocar las sesiones al suspender, con `ix_sesion_usuario`); conviene anotarlo para HU-09.
2. `App.jsx`: el `Alerta` de `errorCierre` (error de red al cerrar sesión) sigue visible al navegar a otra ruta hasta el siguiente intento. Es un detalle estético.
3. Con 401 en "Cerrar sesión", `cliente.js` dispara `alPerderSesion` (navega con tipo info) y luego `terminarSesion` vuelve a navegar con "Cerraste sesión." (exito). El resultado final es el que pide design § 12, pero hay dos navegaciones seguidas.
4. Antes del PR: todo está sin commitear; el dueño debe revisar y hacer commit y PR hacia `develop`. La entrada de `progress/history.md` y la vuelta de `progress/HU-03.md` a la plantilla quedan para el cierre.
