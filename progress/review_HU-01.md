# Review — HU-01

**Veredicto:** APPROVED

- **Revisor:** reviewer · **Fecha:** 2026-10-27 · **Rama:** `feature/HU-01-registro-consentimiento` (cambios sin commitear)
- **`./init.sh` completo (sin `--rapido`):** exit 0, `[OK] Entorno listo`. `./mvnw verify`: 29 unitarias + 26 IT
  (Testcontainers, PostgreSQL), 0 fallos, 0 errores, 0 omitidas. ESLint sin advertencias, `npm run build` OK.
  Todos los chequeos C3 automáticos en `[OK]`.

## Decisiones del dueño (S5) — verificadas
- Puerto `cuentas/service/NotificadorCorreo.java` + única implementación `NotificadorCorreoSimulado.java:12-16`
  (solo `log.info`, sin el correo, sin colaboradores). [x]
- Sin `spring-boot-starter-mail`: `2_Backend/pom.xml` sin cambios (diff vacío; ninguna coincidencia de "mail");
  sin `JavaMailSender` ni `spring.mail.*` en `src/`. [x]
- Sin token: no hay tabla `token`, ni tipo `VERIFICACION`, ni endpoint de verificación. [x]
- `correo_verificado = true`: `Usuario.registrar` (`Usuario.java:57`); probado en `UsuarioTest`,
  `RegistroServiceTest.guardaUsuarioNuevo` y `UsuarioControllerIT.creaUsuarioActivoVerificado` (columna real). [x]

## Trazabilidad requirements ↔ tests
- R1: [x] `UsuarioControllerIT.creaUsuarioActivoVerificado` (rol/estado/correo_verificado en BD), `RegistroServiceTest.guardaUsuarioNuevo`, `UsuarioTest.registrarFijaRolEstadoYVerificacion`
- R2: [x] `UsuarioControllerIT.responde202SoloConMensaje` (202, JSON estricto `{mensaje}`, sin id/correo)
- R3: [x] `RegistroServiceTest.notificaUnaVezConCorreoNormalizado` (`times(1)`), `UsuarioControllerIT.invocaAlNotificador` (`containsExactly`)
- R4: [x] `NotificadorCorreoSimuladoTest.soloEscribeEnElLogSinElCorreo` (INFO, mensaje, sin el correo), `sinColaboradoresDeRed`
- R5: [x] `UsuarioControllerIT.guardaCorreoNormalizado`, `UsuarioTest.normalizaCorreo` (trim + minúsculas)
- R6: [x] `UsuarioControllerIT.rechazaSinAceptarPrivacidad`, `rechazaSinCampoDeAceptacion` (400, campo, BD vacía, sin notificar)
- R7: [x] `UsuarioControllerIT.rechazaVersionDistinta`, `rechazaSinVersion`, `RegistroServiceTest.rechazaVersionNoVigente`
- R8: [x] `RegistroServiceTest.guardaConsentimiento` (Clock fijo), `UsuarioControllerIT.guardaConsentimientoYAuditoria`
- R9: [x] `RegistroServiceTest.auditaRegistro`, `UsuarioControllerIT.guardaConsentimientoYAuditoria` (usuario_id, ip, fecha, detalle)
- R10: [x] `PoliticaContrasenaTest.limitesDeLongitud` (7/8/64/65), `rechazaMasDe72Bytes`, `UsuarioControllerIT.rechazaLongitudInvalida`
- R11: [x] `PoliticaContrasenaTest.rechazaSinLetra`, `rechazaSinDigito`, `UsuarioControllerIT.rechazaSinLetraOSinDigito`
- R12: [x] `PoliticaContrasenaTest.rechazaIgualAlCorreo`, `UsuarioControllerIT.rechazaIgualAlCorreo`
- R13: [x] `UsuarioControllerIT.rechazaCorreoInvalido` (null, vacío, mal formado, más de 254), `ManejadorGlobalErroresTest.reglaNegocioConCampo`
- R14: [x] `UsuarioControllerIT.correoExistenteMismaRespuesta` (código y cuerpo idénticos), `RegistroServiceTest.calculaHashConCorreoExistente`
- R15: [x] `UsuarioControllerIT.correoExistenteNoCambiaNada` (fila idéntica, 1 usuario, sin notificar), `RegistroServiceTest.correoExistenteNoGuardaNiNotifica`
- R16: [x] `UsuarioControllerIT.correoExistenteAuditado`, `RegistroServiceTest.auditaCorreoExistente`
- R17: [x] `UsuarioControllerIT.invalidoConCorreoExistenteIgualQueNuevo`, `RegistroServiceTest.contrasenaDebilNoConsultaElCorreo`, `versionInvalidaNoConsultaElCorreo`
- R18: [x] `UsuarioControllerIT.registrosSimultaneos` (2 hilos + `CountDownLatch`, 1 fila, dos 202), `RegistroServiceTest.carreraPorCorreoUnico`
- R19: [x] `UsuarioControllerIT.guardaHashBcrypt` (prefijo bcrypt de costo 12, `matches`, hashes distintos)
- R20: [x] `UsuarioControllerIT.ignoraCamposDeRolYEstado`, `UsuarioTest.registrarFijaRolEstadoYVerificacion`
- R21: [x] `UsuarioControllerIT.sextoIntentoRecibe429` (429 + mensaje), `LimitadorIntentosTest.aceptaCincoYRechazaElSexto`, `elRechazadoNoCuenta`, `clavesIndependientes`
- R22: [x] `UsuarioControllerIT.cuentanLosIntentosInvalidos` (JSON roto, correo inválido, sin aceptar, débil, existente → el 6.º recibe 429)
- R23: [x] `LimitadorIntentosTest.vuelveAAceptarAlPasarLaVentana`, `UsuarioControllerIT.aceptaTrasLaVentana` (Clock controlable)
- R24: [x] `ManejadorGlobalErroresTest.errorInesperadoEsGenerico` (500, sin trace/exception/detalle), `UsuarioControllerIT.falloDelNotificador`
- R25: [x] `UsuarioControllerIT.falloDelNotificador` (500 genérico, BD sin usuario ni auditoría), `RegistroServiceTest.falloDelNotificadorRevierte` (rollback, sin commit)
- R26: [x] `PoliticaPrivacidadControllerIT.devuelveVersionVigente`

## Trazabilidad con la historia
- CA1 (cuenta creada): [x] R1, R2, R5, R8
- CA1 (correo de verificación, simulado S5): [x] R2, R3, R4, R25
- CA2 (sin aceptación no se completa): [x] R6, R7, R26
- CA3 (contraseña débil → requisitos): [x] R10–R13
- CA3 (correo existente → genérico): [x] R14, R15, R17, R18
- (T) hash con sal: [x] R19 — `BCryptPasswordEncoder(12)` en `ConfiguracionSeguridad.java:50-52`
- (I) errores que no confirman el correo: [x] R14, R17, R18, R24, R25 — 202 idéntico y hash siempre calculado (`RegistroService.java:56`)
- (D) límite por IP: [x] R21–R23 — `FiltroLimiteIntentos` + `LimitadorIntentos` + `ReglasLimiteCuentas` (5 por hora)
- (R) fecha, hora, IP y versión: [x] R8, R9, R16 — `AuditoriaService` con `Clock`
- (E) transversal, rol en el servidor: [x] R20

## Tasks completas
- T1: [x] · T2: [x] · T3: [x] · T4: [x] · T5: [x] · T6: [x] · T7: [x] · T8: [x] · T9: [x]
- T10: [x] · T11: [x] · T12: [x] · T13: [x] · T14: [x] · T15: [x] · T16: [x] · T17: [x] · T18: [x]
  (contrastadas con los archivos; T18 la reprodujo el revisor con `./init.sh` completo)

## Revisión por archivo (resumen)
- Arquitectura: capas controller → service → domain/repository respetadas; `UsuarioController` solo usa
  `RegistroService`; `comun` no importa `cuentas` (`Auditoria.usuarioId` es `Long`); DTO `record` en la API;
  ninguna entidad sale; rol, estado y verificación fijados en el servidor.
- Convenciones: nombres en español, inyección por constructor, migraciones `V1_1__usuarios.sql` y
  `V1_2__auditoria.sql` idénticas a design §8, `ErrorDTO` con mensajes genéricos, comentarios solo de "por qué".
- Frontend: solo tokens (todas las variables usadas existen en `tokens.css`), sin estilos en línea, CSS Modules,
  pantallas con `Boton`/`Campo`/`Alerta`, un solo botón primario ("Crear cuenta"), `fetch` solo en
  `src/api/cliente.js`, mensajes según la tabla de `docs/frontend.md`; coincide con design §11 (Registro,
  Privacidad, Inicio). Única dependencia nueva: `react-router-dom` (S4, aprobado).
- Design: sin desviaciones injustificadas. Las dos decisiones menores (los 4xx de Spring MVC conservan su código con
  mensaje genérico; `@NotNull` además de `@AssertTrue` en `aceptaPrivacidad`) están documentadas en
  `progress/impl_HU-01.md` y no contradicen ningún R.
- Patrones ★: n/a (HU-01 no es ★).

## Checkpoints
- C1: [x] arnés completo; `./init.sh` sin `--rapido` termina con exit 0
- C2: [x] una sola historia `in_progress` del dueño; `progress/HU-01.md` existe y anota la aprobación de Jorge Quispe;
  describe solo la sesión activa. "Toda historia done tiene tests": n/a (ninguna `done`)
- C3: [x] paquetes `comun`/`cuentas`; controladores sin repositorios; sin `@Autowired`, Lombok, comodines ni prints;
  dependencias entre módulos: n/a (no hay) y sin puertos nuevos entre módulos; DTO `record`; roles y estados en el
  servidor; esquema solo por Flyway, ninguna migración de `develop` editada; correo detrás de `NotificadorCorreo`;
  nada fuera del alcance; sin secretos (`.env.example` solo con nombres); frontend sin colores fuera de tokens ni
  librerías de CSS; pantallas con el kit y API solo vía `src/api/`
- C4: [x] `<Clase>Test`/`<Clase>IT` con `@Import(TestcontainersConfiguration.class)`; sin H2; ventana de 60 min con
  `Clock` controlable; hay IT; la concurrencia de R18 se prueba con dos hilos simultáneos; resultados concretos,
  también en el camino de error; `mvnw verify` con 55 tests verdes; lint y build OK
- C5: [x] sin archivos sospechosos sin trackear (`target/` y `node_modules/` ignorados); `progress/history.md` tiene
  entrada de la sesión; estado `in_progress` correcto; rama `feature/HU-01-registro-consentimiento`.
  "El progress de una historia terminada vuelve a la plantilla": n/a (aún no está `done`)
- C6: [x] `specs/HU-01/` completo; EARS + tabla de trazabilidad que cubre CA1–CA3 y T/I/D/R; design con endpoints,
  clases, migraciones, secuencias y 8 alternativas; todas las tasks `[x]`; mapa R→test en `progress/impl_HU-01.md`;
  §9.5 y §9.7 resueltos por el dueño (registrado en `docs/requerimientos.md`)

## Cambios requeridos
Ninguno.

## Observaciones no bloqueantes (para el dueño / orquestador)
1. `docs/architecture.md`, "Estados del sistema", dice que "correo pendiente" aplica al usuario recién registrado;
   con S5 deja de ser cierto hasta HU-25. Ajustarlo es tarea `sdd:false` del dueño u orquestador.
2. `RegistroUsuarioDTO.java:13`: es probable que `@Email` rechace un correo con espacios en los extremos, así que el
   trim de `Usuario.normalizarCorreo` casi no se ejerce desde la API (R5 se cumple igual y está probado en
   `UsuarioTest`). Aceptar correos con espacios exigiría normalizar antes de validar: decisión de UX, no de este spec.
3. `ManejadorGlobalErrores.java:48-50`: un 404/405 de Spring devuelve el mensaje genérico de error inesperado.
   Cumple RNF-SEG-13, pero cuando exista `RecursoNoEncontradoException` convendrá un texto propio.
4. `UsuarioControllerIT.registrosSimultaneos` comprueba el resultado (1 fila, dos 202) pero no garantiza pasar por la
   rama `DataIntegrityViolationException`; esa rama la cubre `RegistroServiceTest.carreraPorCorreoUnico`.
5. Al correr `./mvnw` aparece el aviso de que Mockito se auto-adjunta en Java 25: conviene resolverlo en el arnés
   más adelante.

---

## Revisión corrección CORS 2026-10-27

**Veredicto:** APPROVED (APROBADO), con observaciones de trazabilidad para el dueño. No bloquean.

### 1. Corrección: mínima, correcta y fiel al design
- `ConfiguracionSeguridad.java:38-41`: solo cambia el nombre del método `@Bean` de `origenesPermitidos` a
  `corsConfigurationSource` y agrega un comentario de 2 líneas. El contenido no cambia: orígenes =
  `${espacioya.frontend.url}`, métodos, `Content-Type` y `/api/**`. Coincide con design §4 ("CORS con origen
  `espacioya.frontend.url`") y con T7.
- El diagnóstico es correcto: `.cors(Customizer.withDefaults())` busca un bean llamado `corsConfigurationSource`. Si no
  lo encuentra, usa el `HandlerMappingIntrospector` de MVC, que no tiene mapeos, y entonces responde sin ACAO y sin
  rechazar nada. Elegir el nombre en lugar de `configurationSource(...)` está justificado en `impl_HU-01.md`: evita la
  ambigüedad por tipo con `mvcHandlerMappingIntrospector`.
- `application.properties:20` define `espacioya.frontend.url=${FRONTEND_URL:http://localhost:5173}`, que es lo que
  piden la spec y la convención de secretos.

### 2. Tests (`UsuarioControllerIT.java:433-474`)
- `preflightDesdeElFrontendPermitido`: comprueba 200, que ACAO sea exactamente el origen y que Allow-Methods ⊇ POST y
  Allow-Headers ⊇ content-type. Simula el preflight real del navegador.
- `preflightDesdeOrigenNoPermitido`: comprueba 403 y que no haya ACAO. Es el control negativo.
- `postDesdeElFrontendLlevaCabeceraCors`: comprueba el 202 real con ACAO y usa una IP propia por test, así no choca con
  el límite de R21.
- Las tres aserciones son concretas (código HTTP y valor de las cabeceras). El implementer comprobó que fallan con el
  nombre anterior (3 fallos). Están en `<Clase>IT` con Testcontainers, usan MockMvc y nombres en español. Cumplen la
  convención.
- Se ejecutaron: aparecen en `target/failsafe-reports/TEST-…UsuarioControllerIT.xml` sin fallos ni errores.

### 3. Trazabilidad: la spec es inconsistente
- `requirements.md:132-134` (R19) dice solo: "guardar la contraseña solo como hash bcrypt con sal (costo 12)". La
  tabla de trazabilidad (`requirements.md:184,189`) asocia R19 a (T) y a RNF-SEG-05. **Ningún `R<n>` habla de CORS.**
- `design.md:49` ("CORS (R19)") y `tasks.md:16` (T7 "Cubre: R19") ponen CORS bajo R19 solo porque T7 agrupa en una
  clase el `PasswordEncoder` (que sí es R19) y la cadena de seguridad. El "Cubre: R19" de T7 se refiere a la parte de
  bcrypt. Atribuirle CORS en `design.md:49` es un error de la spec. Además, `design.md:504` (mapa de pruebas de R19)
  solo lista pruebas de hash, lo que confirma que R19 = bcrypt.
- **Respuesta:** hoy no hay ninguna etiqueta correcta para estos tests. `HU-01 R19` es engañosa: los tests no
  comprueban nada de R19, y la fila `| R19 (CORS, vía T7) |` de `progress/impl_HU-01.md` repite el error. R19 sigue
  bien cubierto por sus tests de hash, así que no queda ningún `R<n>` sin cobertura y esto no bloquea.
- **Recomendación para el dueño** (revisión de spec por `spec_author`, que el dueño debe aprobar): agregar
  `R27 — El sistema DEBE permitir solicitudes CORS a /api/** solo desde el origen espacioya.frontend.url y rechazar el
  preflight de cualquier otro origen`. Después: corregir `design.md:49` a "CORS (R27)", poner en T7 "Cubre: R19, R27",
  agregar R27 al mapa de pruebas de design §10, cambiar los 3 `@DisplayName` a `HU-01 R27: …` y cambiar esa fila en
  `impl_HU-01.md`. CORS no proviene de ningún CA ni control STRIDE de `docs/historias/HU-01.md`, así que en la tabla
  de trazabilidad iría como requisito técnico de soporte de CA1, porque sin CORS el registro no funciona desde el
  navegador.

### 4. Checkpoints
- `./init.sh` completo (sin `--rapido`): **exit 0**, `[OK] Entorno listo`. `mvnw verify`: 29 unitarias + 29 IT, 0
  fallos. ESLint y build OK.
- C1: [x] · C2: [x], con la observación (b) de abajo · C3: [x] (sin cambios de capas; config en `comun/seguridad`) ·
  C4: [x] · C5: [x] (rama `feature/HU-01-registro-consentimiento`; sin archivos sin trackear) · C6: [x] (todas las
  tasks `[x]`; la inconsistencia de etiqueta queda anotada en §3).

### 5. Alcance de los cambios
- `git status`: solo cambiaron `ConfiguracionSeguridad.java`, `UsuarioControllerIT.java`, `progress/HU-01.md` y
  `progress/impl_HU-01.md`. **No se tocaron** `3_frontend/` ni `feature_list.json` (HU-01 sigue `done`), y no hay
  archivos sin trackear.

### Observaciones no bloqueantes
a. La relación entre la etiqueta R19 y CORS es un defecto de la spec (§3). Corregirlo en una revisión de spec
   aprobada por el dueño.
b. `progress/HU-01.md` (Bitácora): la entrada del implementer está **duplicada**, con dos líneas casi iguales del
   2026-10-27. Hay que limpiarla, y al cerrar la corrección el archivo debe volver a la plantilla (C5).
c. `UsuarioControllerIT.java:60` fija `FRONTEND = "http://localhost:5173"`. Si alguien corre los tests con
   `FRONTEND_URL` definida en el entorno, los 3 tests fallan. Es más robusto inyectar
   `@Value("${espacioya.frontend.url}")` o fijar la propiedad en el test con `@TestPropertySource`.
