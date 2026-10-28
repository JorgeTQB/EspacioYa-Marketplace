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

---

## Revisión final R27 2026-10-27

**Veredicto:** RECHAZADO (CHANGES_REQUESTED)

Motivo único bloqueante: R27 no cumple el EARS estricto de `docs/specs.md`. Lo demás está correcto.

### 1. Spec (`specs/HU-01/`)
- [ ] **R27 en EARS** — `requirements.md:177-182` junta **dos `DEBE`** y dos patrones en un solo requirement:
  uno de evento ("CUANDO … `Origin` igual a `espacioya.frontend.url`, el sistema DEBE responder con ACAO…") y uno
  no deseado ("SI … `Origin` distinto … ENTONCES el sistema DEBE rechazarlo con 403 sin ACAO"). Esto incumple la regla
  dura de `docs/specs.md:78`: "No mezcles varios `DEBE` en un mismo requirement. Si hay más de uno, parte".
  Cada patrón por separado está bien escrito. (R4, `requirements.md:56-59`, tiene el mismo defecto, pero es
  anterior y ya estaba aprobado. Se anota y no bloquea esta revisión.)
- [x] Tabla de trazabilidad: `requirements.md:200` agrega "CA1 desde el navegador … (CORS) | R27".
- [x] R19 queda solo para bcrypt: `requirements.md:132-134`, `requirements.md:193,198` y `design.md:506`.
- [x] No quedan referencias de CORS a R19 en la spec: `design.md:49` dice "bcrypt(12) (R19), CORS (R27)";
  `design.md:222-226` pone R27 para CORS y R19 para el `PasswordEncoder`; `design.md:512` es la fila de pruebas de R27.
  Las únicas apariciones de "R19 … CORS" están en `progress/impl_HU-01.md:164-169`, como registro histórico.
- [x] T7 (`tasks.md:16`) dice "R19 (bcrypt), R27 (CORS)". T11 (`tasks.md:24`) dice "R27 (3 IT CORS)". T19
  (`tasks.md:42`) es `[x]` y su texto coincide con lo que se hizo. Son coherentes.

### 2. `ConfiguracionSeguridad`
- [x] El bean se llama `corsConfigurationSource` (`ConfiguracionSeguridad.java:41`). La corrección es mínima: en el
  commit `2a77bac` solo cambian el nombre del método y un comentario de 2 líneas (+4/−2). Esta revisión no cambia
  código de producción.

### 3. `UsuarioControllerIT`
- [x] `@SpringBootTest(properties = "espacioya.frontend.url=" + UsuarioControllerIT.FRONTEND)` (línea 49), con
  `FRONTEND = "http://frontend.espacioya.test:5173"` (línea 62). Este valor es distinto del default
  `${FRONTEND_URL:http://localhost:5173}` (`application.properties:20`), así que el test prueba que CORS lee la
  propiedad.
- [x] No dependen de `FRONTEND_URL`: ejecuté `FRONTEND_URL=http://otro:9999 ./init.sh` (completo) y terminó con
  exit 0. Los 3 casos CORS aparecen en `target/failsafe-reports/TEST-…UsuarioControllerIT.xml` sin fallos (27/27).
  `init.sh` no redefine `FRONTEND_URL`, así que la variable llega a la JVM de las pruebas.
- [x] Las etiquetas son `HU-01 R27:` (líneas 445, 455 y 463). Validan:
  - ACAO = `FRONTEND` en el preflight (líneas 447-451: 200, ACAO, métodos y cabeceras).
  - ACAO = `FRONTEND` en el POST (líneas 470-475: 202).
  - 403 sin ACAO para `http://evil.com` (líneas 457-459).
- [x] Fijar la propiedad no rompe los demás IT de la clase. `FRONTEND` solo se usa en los 3 IT CORS, y los 27 IT de
  la clase pasan. La clase ya tenía su propia clave de contexto por `@Import(… Dobles)`. `PoliticaPrivacidadControllerIT`
  y `EspacioYaApplicationIT` siguen en verde.

### 4. Checkpoints
- `./init.sh` completo (sin `--rapido`, con `FRONTEND_URL=http://otro:9999`): **exit 0**, `[OK] Entorno listo`.
  29 unitarias + 29 IT, 0 fallos y 0 errores. ESLint y build OK.
- C1: [x] · C2: [x] · C3: [x] · C4: [x] · C5: [x] (rama `feature/HU-01-registro-consentimiento`, sin archivos sin
  trackear) · C6: [ ] porque "`requirements.md` usa EARS estricto" falla por R27 (§1). El resto de C6 está en [x].

### 5. Alcance
- [x] `git status` muestra solo `UsuarioControllerIT.java`, `progress/HU-01.md`, `progress/impl_HU-01.md` y
  `specs/HU-01/{requirements,design,tasks}.md`. No hay cambios en `3_frontend/` ni en `feature_list.json`, ni archivos
  sin trackear.

### Cambios requeridos
1. `spec_author`: partir R27 en dos requirements:
   - **R27** (evento): CUANDO llega a `/api/**` una solicitud (incluido el preflight `OPTIONS`) con `Origin` igual a
     `espacioya.frontend.url`, el sistema DEBE responder con `Access-Control-Allow-Origin` igual a ese origen.
   - **R28** (no deseado): SI llega a `/api/**` un preflight `OPTIONS` con `Origin` distinto de
     `espacioya.frontend.url` ENTONCES el sistema DEBE rechazarlo con 403 sin `Access-Control-Allow-Origin`.

   Después hay que actualizar:
   - la tabla de trazabilidad (`requirements.md:200` → R27, R28);
   - `design.md:49,222,512` (R28 aparte en el plan de pruebas);
   - T7, T11 y T19 (o una T20) en `tasks.md`.

   El dueño (Jorge Quispe) debe aprobar esta revisión de spec.
2. `implementer`: cambiar la etiqueta de `preflightDesdeOrigenNoPermitido` (línea 455) a `HU-01 R28: …`, agregar
   R28 al mapa de `progress/impl_HU-01.md` y volver a correr `./init.sh` completo.

### Observaciones no bloqueantes
a. `progress/HU-01.md:8-9,15,17-18` está desactualizado: dice "Fase: corrección aprobada por reviewer", "la
   corrección no cambia la spec", "Incumple R19" y "validar contra R19". Hay que actualizarlo y, al cerrar,
   devolverlo a la plantilla (C5).
b. `progress/impl_HU-01.md:169`: la fila "R19 (CORS, vía T7)" sigue con formato de tabla. Conviene tacharla o
   marcarla "obsoleta → R27/R28" para que no se lea como mapa vigente.

## Revisión final integral 2026-10-27

**Veredicto:** APROBADO (APPROVED)

Cambios revisados: `git diff HEAD` (8 archivos sin commit) y el código de producción de `2a77bac`.

### 1. Spec (`specs/HU-01/`)
- [x] **R4** (`requirements.md:58-60`): patrón de evento, un solo `NO DEBE` ("abrir conexiones a un servidor de correo"). Es verificable y está escrito sobre el backend.
- [x] **R29** (`requirements.md:62-64`): patrón de evento, un `DEBE` (una línea INFO con el mensaje de envío simulado). Verificable.
- [x] **R30** (`requirements.md:66-68`): patrón de evento, un `NO DEBE` (el correo del usuario no va al log). Verificable.
- [x] **R27** (`requirements.md:186-189`): patrón de evento, un `DEBE` (ACAO igual al origen, también en el preflight). Está escrito sobre la API.
- [x] **R28** (`requirements.md:191-194`): patrón no deseado `SI … ENTONCES`, un `DEBE` (403 sin ACAO). Está escrito sobre la API.
- [x] Definición "Mensaje de envío simulado" (`requirements.md:24-25`): es el mismo texto, carácter por carácter, que `NotificadorCorreoSimulado.java` (`log.info`) y que la aserción de `NotificadorCorreoSimuladoTest.java:21`.
- [x] Tabla de trazabilidad:
  - CA1 correo → R2, R3, R4, R25, R29, R30;
  - RF-A-01 → R1–R4, R29, R30;
  - CORS → R27, R28 (`requirements.md:212`);
  - S5 → R29, R30.
- [x] `design.md`:
  - :49 → bcrypt (R19), CORS (R27, R28);
  - :64 y :202-203 → R4/R29/R30;
  - :223-227 → nombre del bean `corsConfigurationSource` y R19 solo para el `PasswordEncoder`;
  - :430 → R30;
  - §12 :500-501 y :514-515 → filas separadas para R4, R29/R30, R27 y R28.
- [x] `tasks.md`:
  - T7 → R19 (bcrypt), R27, R28;
  - T10 → R4, R29, R30 con sus tests;
  - T11 → R27 (2 IT) y R28 (1 IT);
  - T19, T20 y T21 están `[x]` (líneas 42, 46 y 50). T19 dice "3 IT a R27" y T20 corrige uno a R28; se lee bien como historial.
- [x] No quedan referencias viejas: `grep R19` en `specs/HU-01` solo da bcrypt/Usuario. R27 nunca aparece solo como requisito doble. Las menciones "R19 (CORS)" están solo en `progress/impl_HU-01.md:167-172`, marcadas como histórico.

### 2. `ConfiguracionSeguridad`
- [x] El bean `corsConfigurationSource(@Value("${espacioya.frontend.url}") …)` está en `ConfiguracionSeguridad.java:41`, con `.cors(Customizer.withDefaults())` en la línea 30.
- [x] La corrección es mínima: en `2a77bac` cambian solo el nombre del método y un comentario de 2 líneas. Los cambios sin commit no tocan `src/main`.

### 3. Tests
- [x] `UsuarioControllerIT.java:49`: `@SpringBootTest(properties = "espacioya.frontend.url=" + FRONTEND)` con `FRONTEND = "http://frontend.espacioya.test:5173"` (línea 62). Este valor es distinto del default de `application.properties:20`.
- [x] Etiquetas:
  - `HU-01 R27:` en el preflight permitido (línea 445: 200, ACAO = FRONTEND, métodos y cabeceras);
  - `HU-01 R27:` en el POST permitido (línea 463: 202, ACAO = FRONTEND);
  - `HU-01 R28:` en el origen no permitido (línea 455: 403 y `doesNotExist(ACAO)`).
- [x] `NotificadorCorreoSimuladoTest.java`:
  - `HU-01 R29, R30:` en `soloEscribeEnElLogSinElCorreo` (línea 15): comprueba INFO, el mensaje exacto y que `ana.secreta` no esté en stdout ni en stderr;
  - `HU-01 R4:` en `sinColaboradoresDeRed` (línea 27): constructores sin parámetros y solo campos `org.slf4j.*`.

### 4. Mapa de trazabilidad (`progress/impl_HU-01.md`)
- [x] R4 → `sinColaboradoresDeRed` (:63).
- [x] R27 → los 2 IT del origen permitido (:86).
- [x] R28 → `preflightDesdeOrigenNoPermitido` (:87).
- [x] R29 y R30 → `soloEscribeEnElLogSinElCorreo` (:88-89).
- [x] R1–R30 tienen al menos un test.

### 5. Checkpoints
- `./init.sh` completo (sin `--rapido`, entorno por defecto): **exit 0**, `[OK] Entorno listo`. 29 unitarias + 29 IT, 0 fallos. ESLint y build OK.
- `FRONTEND_URL=http://otro:9999 ./init.sh` completo: **exit 0**, con los mismos conteos y 0 fallos.
- C1: [x]
- C2: [x]
- C3: [x] (sin cambios en `src/main` desde la revisión anterior; el notificador sigue siendo un Adapter detrás del puerto `NotificadorCorreo`)
- C4: [x]
- C5: [x] Está en la rama `feature/HU-01-registro-consentimiento` y no hay archivos sin trackear (solo ignorados: `target/`, `node_modules/`, `.idea/`, `dist/`, `3_frontend/.env`). Al cerrar falta una entrada en `progress/history.md` y devolver `progress/HU-01.md` a la plantilla; ver la observación (a).
- C6: [x] EARS estricto en R4 y R27–R30, trazabilidad completa, tasks `[x]` y mapa al día.

### 6. Alcance
- [x] `git status` muestra solo `UsuarioControllerIT.java`, `NotificadorCorreoSimuladoTest.java`, `progress/{HU-01,impl_HU-01,review_HU-01}.md` y `specs/HU-01/{requirements,design,tasks}.md`.
- [x] No hay cambios en `3_frontend/` ni en `feature_list.json`, ni archivos sin trackear.

### Cambios requeridos
Ninguno.

### Observaciones no bloqueantes
a. `progress/HU-01.md` está desactualizado:
   - :8 todavía dice "Fase: revisión de spec (R27/R28)";
   - :19 del plan dice "validar contra R19";
   - la entrada de T19 está duplicada en :29-30.
   Al cerrar hay que devolverlo a la plantilla y registrar la sesión en `progress/history.md` (C5).
b. `soloEscribeEnElLogSinElCorreo` comprueba `contains("INFO")` y el mensaje por separado, no en la misma línea. Hoy es suficiente porque el notificador emite una sola línea, pero es una aserción algo débil para R29.
