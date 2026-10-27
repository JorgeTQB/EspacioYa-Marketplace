# HU-01 — Implementación: Registro de usuario con consentimiento

- **Agente:** implementer · **Fecha:** 2026-10-27 · **Rama:** `feature/HU-01-registro-consentimiento`
- **Spec:** `specs/HU-01/` (R1–R26, T1–T18), aprobado por Jorge Quispe el 2026-10-27.
- **Estado:** T1–T18 hechas (`[x]`). Ninguna task quedó incompleta. Status sigue en `in_progress`: falta el reviewer.

## Resumen

- Correo **simulado** (S5): puerto `cuentas.service.NotificadorCorreo` con una única implementación,
  `NotificadorCorreoSimulado`, que solo hace `log.info` sin el correo. No hay `spring-boot-starter-mail`,
  ni tabla `token`, ni endpoint de verificación. La cuenta nace con `correo_verificado = true`.
- `comun` transversal construido: errores (`ErrorDTO {mensaje, errores[]}` + `ManejadorGlobalErrores`),
  auditoría, rate limiting (ventana deslizante en memoria + filtro), seguridad (bcrypt 12, CORS) y `Clock`.
- Endpoints: `GET /api/politicas-privacidad/vigente` (200) y `POST /api/usuarios` (202 también con correo
  existente; 400 / 429 / 500 con `ErrorDTO`).
- Frontend: router, kit `Boton`/`Campo`/`Alerta`, `api/cliente.js` + `api/cuentas.js`, pantallas
  `/registro` y `/privacidad`, y el inicio `/` en `App.jsx`.

## Archivos

**Backend — nuevos** (`2_Backend/src/main/java/pe/edu/ulima/espacioya/`)

- `comun/configuracion/ConfiguracionReloj.java`
- `comun/web/IpCliente.java`
- `comun/error/ReglaNegocioException.java`, `ErrorDTO.java`, `ErrorCampoDTO.java`, `ManejadorGlobalErrores.java`
- `comun/auditoria/Auditoria.java`, `AuditoriaRepository.java`, `AuditoriaService.java`, `EventoAuditoria.java`
- `comun/limite/ReglaLimite.java`, `LimitadorIntentos.java`, `FiltroLimiteIntentos.java`
- `comun/seguridad/ConfiguracionSeguridad.java`
- `cuentas/domain/Usuario.java`, `Rol.java`, `EstadoUsuario.java`, `PoliticaContrasena.java`
- `cuentas/repository/UsuarioRepository.java`
- `cuentas/service/RegistroService.java`, `NotificadorCorreo.java`, `NotificadorCorreoSimulado.java`, `PoliticaPrivacidad.java`
- `cuentas/dto/RegistroUsuarioDTO.java`, `MensajeDTO.java`, `VersionPoliticaDTO.java`
- `cuentas/controller/UsuarioController.java`, `PoliticaPrivacidadController.java`, `ReglasLimiteCuentas.java`
- `src/main/resources/db/migration/V1_1__usuarios.sql`, `V1_2__auditoria.sql`

**Backend — modificados:** `src/main/resources/application.properties`, `2_Backend/.env.example`. `pom.xml` sin cambios.

**Backend — tests nuevos** (`2_Backend/src/test/java/pe/edu/ulima/espacioya/`)

- `RelojControlable.java` (apoyo: `Clock` que avanza a pedido)
- `comun/error/ManejadorGlobalErroresTest.java`, `comun/limite/LimitadorIntentosTest.java`
- `cuentas/domain/PoliticaContrasenaTest.java`, `cuentas/domain/UsuarioTest.java`
- `cuentas/service/RegistroServiceTest.java`, `cuentas/service/NotificadorCorreoSimuladoTest.java`
- `cuentas/controller/UsuarioControllerIT.java`, `cuentas/controller/PoliticaPrivacidadControllerIT.java`

**Frontend** (`3_frontend/`)

- Nuevos: `src/components/ui/{Boton,Campo,Alerta}.jsx` + `.module.css`, `src/api/cliente.js`, `src/api/cuentas.js`,
  `src/pages/cuentas/{Registro,Privacidad}.jsx` + `.module.css`, `src/App.module.css`
- Modificados: `src/main.jsx`, `src/App.jsx`, `package.json`, `package-lock.json` (`react-router-dom` ^7.18.4, S4)
- Eliminado: `src/App.css` (reemplazado por `App.module.css`)

**Docs:** sin cambios. HU-01 no agrega puertos entre módulos (`NotificadorCorreo` es interno de `cuentas`,
design §4) ni componentes fuera de los ya listados en `docs/frontend.md`.

## Trazabilidad (R<n> → test)

| R | Tests |
|---|-------|
| R1 | `UsuarioControllerIT.creaUsuarioActivoVerificado`, `RegistroServiceTest.guardaUsuarioNuevo`, `UsuarioTest.registrarFijaRolEstadoYVerificacion` |
| R2 | `UsuarioControllerIT.responde202SoloConMensaje` |
| R3 | `RegistroServiceTest.notificaUnaVezConCorreoNormalizado`, `UsuarioControllerIT.invocaAlNotificador` |
| R4 | `NotificadorCorreoSimuladoTest.sinColaboradoresDeRed` |
| R5 | `UsuarioControllerIT.guardaCorreoNormalizado`, `UsuarioTest.normalizaCorreo`, `RegistroServiceTest.guardaUsuarioNuevo` |
| R6 | `UsuarioControllerIT.rechazaSinAceptarPrivacidad`, `UsuarioControllerIT.rechazaSinCampoDeAceptacion` |
| R7 | `UsuarioControllerIT.rechazaVersionDistinta`, `UsuarioControllerIT.rechazaSinVersion`, `RegistroServiceTest.rechazaVersionNoVigente` |
| R8 | `RegistroServiceTest.guardaConsentimiento`, `UsuarioControllerIT.guardaConsentimientoYAuditoria` |
| R9 | `RegistroServiceTest.auditaRegistro`, `UsuarioControllerIT.guardaConsentimientoYAuditoria` |
| R10 | `PoliticaContrasenaTest.limitesDeLongitud`, `PoliticaContrasenaTest.rechazaMasDe72Bytes`, `PoliticaContrasenaTest.rechazaNula`, `RegistroServiceTest.contrasenaDebilNoConsultaElCorreo`, `UsuarioControllerIT.rechazaLongitudInvalida` |
| R11 | `PoliticaContrasenaTest.rechazaSinLetra`, `PoliticaContrasenaTest.rechazaSinDigito`, `UsuarioControllerIT.rechazaSinLetraOSinDigito` |
| R12 | `PoliticaContrasenaTest.rechazaIgualAlCorreo`, `UsuarioControllerIT.rechazaIgualAlCorreo` |
| R13 | `UsuarioControllerIT.rechazaCorreoInvalido`, `ManejadorGlobalErroresTest.reglaNegocioConCampo` |
| R14 | `UsuarioControllerIT.correoExistenteMismaRespuesta`, `RegistroServiceTest.correoExistenteNoGuardaNiNotifica`, `RegistroServiceTest.calculaHashConCorreoExistente` |
| R15 | `UsuarioControllerIT.correoExistenteNoCambiaNada`, `RegistroServiceTest.correoExistenteNoGuardaNiNotifica` |
| R16 | `RegistroServiceTest.auditaCorreoExistente`, `UsuarioControllerIT.correoExistenteAuditado` |
| R17 | `UsuarioControllerIT.invalidoConCorreoExistenteIgualQueNuevo`, `RegistroServiceTest.contrasenaDebilNoConsultaElCorreo`, `RegistroServiceTest.versionInvalidaNoConsultaElCorreo` |
| R18 | `UsuarioControllerIT.registrosSimultaneos` (2 hilos + `CountDownLatch`), `RegistroServiceTest.carreraPorCorreoUnico` |
| R19 | `UsuarioControllerIT.guardaHashBcrypt` (`$2a$12$`, `matches`, hashes distintos), `RegistroServiceTest.guardaUsuarioNuevo` |
| R20 | `UsuarioControllerIT.ignoraCamposDeRolYEstado`, `UsuarioTest.registrarFijaRolEstadoYVerificacion` |
| R21 | `LimitadorIntentosTest.aceptaCincoYRechazaElSexto`, `LimitadorIntentosTest.elRechazadoNoCuenta`, `LimitadorIntentosTest.clavesIndependientes`, `UsuarioControllerIT.sextoIntentoRecibe429` |
| R22 | `UsuarioControllerIT.cuentanLosIntentosInvalidos` (JSON mal formado, correo inválido, sin aceptar, contraseña débil y correo existente → el 6.º recibe 429) |
| R23 | `LimitadorIntentosTest.vuelveAAceptarAlPasarLaVentana`, `UsuarioControllerIT.aceptaTrasLaVentana` |
| R24 | `ManejadorGlobalErroresTest.errorInesperadoEsGenerico`, `UsuarioControllerIT.falloDelNotificador` |
| R25 | `UsuarioControllerIT.falloDelNotificador` (500 genérico, BD sin usuario ni auditoría), `RegistroServiceTest.falloDelNotificadorRevierte` |
| R26 | `PoliticaPrivacidadControllerIT.devuelveVersionVigente` |
| R27 | `UsuarioControllerIT.preflightDesdeElFrontendPermitido`, `UsuarioControllerIT.postDesdeElFrontendLlevaCabeceraCors` |
| R28 | `UsuarioControllerIT.preflightDesdeOrigenNoPermitido` |
| R29 | `NotificadorCorreoSimuladoTest.soloEscribeEnElLogSinElCorreo` |
| R30 | `NotificadorCorreoSimuladoTest.soloEscribeEnElLogSinElCorreo` |

Todos los tests llevan `@DisplayName("HU-01 R<n>: …")`; `./init.sh` no reporta R sin test.

## Verificación (T18)

`./init.sh` completo (sin `--rapido`), 2026-10-27: **exit 0**, `[OK] Entorno listo`.

- Entorno: Java 25, Node v24.11.0, Docker encendido.
- `feature_list.json` válido; specs, progress y tasks coherentes.
- Reglas C3: paquetes dentro de los módulos, controladores sin repositorios, sin `@Autowired`, sin Lombok,
  sin imports con comodín, sin prints de depuración, sin `console.log`, sin colores fuera de `tokens.css`,
  sin librerías de componentes/CSS, sin secretos trackeados.
- `./mvnw verify`: **29** unitarias + **26** de integración (Testcontainers, PostgreSQL 17), 0 fallos.
- `npm run lint -- --max-warnings=0` sin errores ni advertencias; `npm run build` compila.

## Decisiones de implementación (dentro del spec) y notas para el reviewer

1. `ManejadorGlobalErrores`: las excepciones propias de Spring MVC que implementan `ErrorResponse` con código
   4xx (404, 405, 415…) conservan su código con el mensaje genérico, en lugar de convertirse en 500. El resto
   de excepciones → 500 genérico (R24). Los errores por campo se deduplican (`"   "` dispara `@NotBlank` y
   `@Email` con el mismo mensaje).
2. `FiltroLimiteIntentos` es un `@Component` (filtro de servlet): corre después de la cadena de Spring Security
   (así el 429 lleva cabeceras CORS para que la SPA lea el mensaje) y antes del `DispatcherServlet`, por lo
   que cuenta las solicitudes que fallan en Bean Validation o JSON (R22).
3. Mensajes de Bean Validation fijados con `message = …` en `RegistroUsuarioDTO` según la tabla de design §3
   (`aceptaPrivacidad` lleva `@NotNull` además de `@AssertTrue`, porque `@AssertTrue` acepta `null`).
   `RegistroUsuarioDTO.toString()` oculta correo y contraseña.
4. `Campo.onChange` recibe el valor (texto o booleano), no el evento.
5. `/privacidad` muestra un texto provisional (S3): el texto legal definitivo lo provee el equipo.
6. Aviso no bloqueante en `./mvnw`: Mockito se auto-adjunta como agente en Java 25 ("will no longer work in
   future releases of the JDK"). No se tocó el `pom.xml` (design: sin cambios); conviene resolverlo en el arnés.
7. Pendiente para dueño/orquestador (ya anotado por spec_author): `docs/architecture.md` "Estados del sistema"
   dice que "correo pendiente" aplica al usuario recién registrado; con S5 deja de ser cierto hasta HU-25.

## Corrección CORS 2026-10-27

**Defecto:** desde el navegador (`http://localhost:5173`), `POST /api/usuarios` fallaba por CORS: el preflight
respondía 200 sin `Access-Control-Allow-Origin`, también con un origen no permitido.

**Causa confirmada** (bytecode de `CorsConfigurer` en `spring-security-config-7.1.2.jar`):
`getCorsConfigurationSource` hace `containsBeanDefinition("corsConfigurationSource")` y solo en ese caso usa el
bean. Si no lo encuentra, recurre a `MvcCorsFilter.getMvcCorsConfigurationSource` (el `HandlerMappingIntrospector`
de Spring MVC, que no tiene mapeos CORS). Así el `CorsFilter` no rechaza nada y tampoco agrega cabeceras. El bean
se llamaba `origenesPermitidos`, por lo que nuestra configuración nunca se aplicaba.

**Corrección:** se cambió el nombre del método `@Bean` a `corsConfigurationSource`, con un comentario que explica
por qué el nombre importa. Su contenido no cambió (orígenes, métodos, cabeceras, `/api/**`).
Por qué esta opción y no `.cors(c -> c.configurationSource(...))`: es un cambio de una línea y sigue la convención
documentada de Spring Security. Además, inyectar un `CorsConfigurationSource` por tipo en `cadenaSeguridad` sería
ambiguo: `HandlerMappingIntrospector` (`mvcHandlerMappingIntrospector`) también implementa esa interfaz, así que
habría que agregar un `@Qualifier` o llamar al método del bean, que es más código para el mismo resultado.
Si alguien vuelve a cambiar el nombre, los tests de abajo fallan.

**Cabeceras permitidas (`allowedHeaders = Content-Type`):** alcanzan. `cliente.js` envía `Accept: application/json`
y, si hay cuerpo, `Content-Type: application/json`. `Accept` es una cabecera *CORS-safelisted* (con ese valor), así
que el navegador no la incluye en `Access-Control-Request-Headers`. Solo pide `content-type`, que está permitido
(Spring compara sin distinguir mayúsculas). El test de preflight envía `content-type` en minúsculas, como lo hace
el navegador. No se tocó el frontend.

**Archivos modificados:**
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/comun/seguridad/ConfiguracionSeguridad.java` (nombre del bean + comentario)
- `2_Backend/src/test/java/pe/edu/ulima/espacioya/cuentas/controller/UsuarioControllerIT.java` (3 tests nuevos, sección "CORS")

**Tests de regresión añadidos** (`UsuarioControllerIT`, T7 / design §4):
- `preflightDesdeElFrontendPermitido`: `OPTIONS /api/usuarios` con `Origin: http://localhost:5173`, método `POST`
  y cabecera `content-type` → 200, `Access-Control-Allow-Origin: http://localhost:5173`, `Allow-Methods` incluye
  POST y `Allow-Headers` incluye content-type.
- `preflightDesdeOrigenNoPermitido`: `Origin: http://evil.com` → 403 sin `Access-Control-Allow-Origin`.
- `postDesdeElFrontendLlevaCabeceraCors`: el `POST` real con `Origin` del frontend → 202 con `Access-Control-Allow-Origin`.

**Comprobado que fallan con el código anterior:** con el bean renombrado de nuevo a `origenesPermitidos`,
`./mvnw verify -Dit.test=UsuarioControllerIT` → 27 tests, **3 fallos** (ACAO null ×2; 403 esperado pero fue 200).
Después se restauró la corrección.

**Verificación:** `./init.sh` completo (sin `--rapido`) → **exit 0**, `[OK] Entorno listo`. `./mvnw verify`:
29 unitarias + **29** de integración (26 + 3 nuevas), 0 fallos. ESLint y `npm run build` en verde.

**Nota de trazabilidad para el reviewer:** se usó `@DisplayName("HU-01 R19: …")` porque tasks T7 dice "Cubre: R19"
y así lo indicó el orquestador. Pero el texto de R19 en `requirements.md` solo habla del hash bcrypt: CORS no tiene un
R propio, solo aparece en design §4 y en T7. No se cambió la spec. Si el dueño lo considera necesario, conviene
agregar un R de CORS en una revisión de la spec.

> Histórico, reemplazado por la sección "T19 etiquetas R27": R19 (CORS, vía T7) → `UsuarioControllerIT.preflightDesdeElFrontendPermitido`, `UsuarioControllerIT.preflightDesdeOrigenNoPermitido`, `UsuarioControllerIT.postDesdeElFrontendLlevaCabeceraCors`

## Tests CORS independientes del entorno 2026-10-27

**Origen:** observación no bloqueante de `progress/review_HU-01.md` ("Revisión corrección CORS 2026-10-27"):
`UsuarioControllerIT` tenía `http://localhost:5173` escrito a mano y los IT de CORS fallaban si `FRONTEND_URL`
estaba definida en el entorno con otro valor.

**Opción elegida:** fijar la propiedad en el test con
`@SpringBootTest(properties = "espacioya.frontend.url=" + UsuarioControllerIT.FRONTEND)`, con
`FRONTEND = "http://frontend.espacioya.test:5173"` (la constante pasó de `private` a package-private para poder
usarla en la anotación). Motivos frente a inyectar `@Value`:
- Es determinista: las propiedades de `@SpringBootTest` tienen más prioridad que las variables de entorno, así que
  el test prueba siempre la misma configuración. Con `@Value`, el test toma lo que haya en el entorno; si alguien
  define `FRONTEND_URL=http://evil.com`, el test del origen ajeno se rompería.
- El valor es distinto del default de `application.properties` (`${FRONTEND_URL:http://localhost:5173}`). Así el
  test prueba que CORS lee `espacioya.frontend.url` y no que coincide por casualidad con el default.
- No agrega otro contexto de Spring: la clase ya tenía su propia combinación de `@Import(... Dobles)`, así que
  ya tenía una clave de caché propia.
- Sigue la convención de `docs/conventions.md`: la configuración sale de propiedades y no está escrita a mano en el
  código. No se tocó código de producción.

Los 3 tests siguen validando lo mismo: el preflight y el POST desde el origen configurado reciben
`Access-Control-Allow-Origin` con ese origen, y `http://evil.com` recibe 403 sin ACAO. No se cambiaron los
`@DisplayName`.

**Archivo modificado:** `2_Backend/src/test/java/pe/edu/ulima/espacioya/cuentas/controller/UsuarioControllerIT.java`
(anotación `@SpringBootTest` + constante `FRONTEND`).

**Verificación:**
- `FRONTEND_URL` sin definir: `./mvnw verify -Dit.test=UsuarioControllerIT` → 27 tests, 0 fallos.
- `FRONTEND_URL=http://otro:9999`: el mismo comando → 27 tests, 0 fallos, BUILD SUCCESS.
- Contraprueba: con la versión anterior del IT (HEAD) y `FRONTEND_URL=http://otro:9999` → 2 fallos
  (`preflightDesdeElFrontendPermitido`, `postDesdeElFrontendLlevaCabeceraCors`). Después se restauró la corrección.
- `./init.sh` completo: los tests del backend (29 unitarias + 29 de integración), ESLint y el build pasan. El script
  termina con **exit 1** por 2 checks de trazabilidad que salen de los cambios que otro agente está haciendo en
  paralelo en `specs/HU-01/`, y no de esta corrección: "HU-01 está done con 1 task(s) sin marcar en tasks.md" y
  "sin @DisplayName para R27". Se resuelven en la tarea siguiente, cuando se cambien las etiquetas (R19 → R de CORS)
  y se marque la task.

## T19 etiquetas R27 2026-10-27

**Origen:** revisión de spec aprobada hoy por Jorge Quispe (R27 CORS, T19).

**Cambios:**
- `2_Backend/src/test/java/pe/edu/ulima/espacioya/cuentas/controller/UsuarioControllerIT.java`: solo el `@DisplayName`
  de `preflightDesdeElFrontendPermitido`, `preflightDesdeOrigenNoPermitido` y `postDesdeElFrontendLlevaCabeceraCors`
  pasa de `HU-01 R19:` a `HU-01 R27:`. El cuerpo de los tests no cambia. `guardaHashBcrypt` sigue con R19.
- `progress/impl_HU-01.md`: fila R27 agregada a la tabla de trazabilidad. La fila suelta "R19 (CORS, vía T7)" de la
  nota para el reviewer queda como registro histórico; ahora el mapa vigente es R27.
- `specs/HU-01/tasks.md`: `[x] T19`.
- No se tocó código de producción, frontend ni `feature_list.json`.

**Verificación:** `./init.sh` completo → **exit 0**, `[OK] Entorno listo`. Los checks de trazabilidad que antes
fallaban ("1 task(s) sin marcar" y "sin @DisplayName para R27") pasan.

**Conteo de pruebas (Maven, `./mvnw verify`):** **29 unitarias + 29 de integración**, 0 fallos, 0 errores, 0 omitidas.
Desglose según `target/surefire-reports` y `target/failsafe-reports`:
- Unitarias (29): RegistroServiceTest 12, PoliticaContrasenaTest 7, LimitadorIntentosTest 4,
  ManejadorGlobalErroresTest 2, UsuarioTest 2, NotificadorCorreoSimuladoTest 2, EspacioYaApplicationTests 1.
- Integración (29): UsuarioControllerIT 27, PoliticaPrivacidadControllerIT 1, EspacioYaApplicationIT 1.

**29 vs 27:** el número correcto del total es **29 IT**. El número no bajó: el "27" venía de una ejecución filtrada
(`./mvnw verify -Dit.test=UsuarioControllerIT`), que solo corre esa clase (27 tests). El "29" es lo que corre
`./init.sh` con todas las clases (27 + 1 + 1). El "26" de la sección T18 es anterior a los 3 IT de CORS
(26 + 3 = 29).

## T20 etiqueta R28 2026-10-27

**Origen:** partición de R27 en R27/R28 aprobada hoy por Jorge Quispe (T20).

**Cambios:**
- `2_Backend/src/test/java/pe/edu/ulima/espacioya/cuentas/controller/UsuarioControllerIT.java`: solo el `@DisplayName`
  de `preflightDesdeOrigenNoPermitido` pasa de `HU-01 R27:` a `HU-01 R28:`. `preflightDesdeElFrontendPermitido` y
  `postDesdeElFrontendLlevaCabeceraCors` siguen en `HU-01 R27:`. El cuerpo de los tests no cambia.
- `progress/impl_HU-01.md`: en el mapa de trazabilidad, R27 queda con los 2 tests del origen permitido y se agrega
  la fila R28 → `UsuarioControllerIT.preflightDesdeOrigenNoPermitido`.
- `specs/HU-01/tasks.md`: solo la línea de T20 pasa a `[x]`. No se tocó nada más de `specs/` (el otro agente está
  editando R4 en paralelo).
- No se tocó código de producción, frontend, `feature_list.json` ni `progress/HU-01.md`.

**Verificación:** `./init.sh` completo → **exit 0**, `[OK] Entorno listo`. Specs, progress y tasks coherentes; ningún
check de trazabilidad falló (tampoco por R4 al momento de la ejecución). `./mvnw verify`: 29 unitarias + 29 de
integración, 0 fallos. ESLint y `npm run build` en verde.

## T21 etiquetas R4/R29/R30 2026-10-27

- **Check de trazabilidad:** `init.sh` (línea 121) lee `@DisplayName("HU-XX R1, R3: …")` con el regex
  `/@DisplayName\(\s*"(HU-\d{2})((?:[\s,y]+R\d+)+)\s*:/`, así que `"HU-01 R29, R30:"` se reconoce. No se cambió el script.
- **Test** `2_Backend/src/test/java/pe/edu/ulima/espacioya/cuentas/service/NotificadorCorreoSimuladoTest.java`:
  - `soloEscribeEnElLogSinElCorreo`: `@DisplayName` pasa de `HU-01 R4:` a `HU-01 R29, R30:`.
  - `sinColaboradoresDeRed`: se queda en `HU-01 R4:`.
- **Mensaje exacto:** el test ya comprueba `"Envío de correo de verificación simulado: no se envió ningún correo (HU-01)."`,
  que coincide con la definición "Mensaje de envío simulado" de `requirements.md`. No hizo falta cambiar el código de producción.
- **Mapa de trazabilidad:** R4 → `sinColaboradoresDeRed`; R29 y R30 → `soloEscribeEnElLogSinElCorreo`.
- **tasks.md:** `[x] T21`.
- **No se tocó:** código de producción, frontend, `feature_list.json`, `progress/HU-01.md`. No se hizo commit.
- **Verificación:** `./init.sh` completo, **exit 0**: trazabilidad, specs y arquitectura OK, `./mvnw verify` 29 unitarias + 29 IT
  sin fallos, ESLint y build del frontend OK, `[OK] Entorno listo`.
