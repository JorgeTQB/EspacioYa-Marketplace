# Exploración HU-03 — Inicio y cierre de sesión

- **Fecha:** 2026-10-27 · **Rama:** feature/HU-03-inicio-cierre-sesion
- **Pregunta:** ¿qué código existente (HU-01/HU-02, `comun`) puede reutilizar HU-03 y qué falta construir o decidir?

## Archivos analizados

- Docs: `docs/historias/HU-03.md`, `docs/requerimientos.md` (RF-A-07…10, RNF-SEG-01/02/12/13/17, §8, §9), `docs/modelo-datos.md`, `docs/architecture.md` (puertos, estados), `docs/frontend.md` (mensajes, rutas), `specs/HU-01/{requirements,design}.md`, `specs/HU-02/requirements.md`.
- Backend: todo `2_Backend/src/main/java/pe/edu/ulima/espacioya/{comun,cuentas}/**`, `application.properties` (main y test), migraciones `V1_1..V1_3`, `pom.xml`, `.env.example`, tests (`TestcontainersConfiguration`, `RelojControlable`, `UsuarioControllerIT`).
- Frontend: `3_frontend/src/{api,components/ui,pages/cuentas}/**`, `App.jsx`, `main.jsx`, `package.json`, `vite.config.js`.

## Requisitos que aplican (resumen)

- RF-A-07 login con correo verificado → panel del rol (CA1); RF-A-08 mensaje genérico (CA2); RF-A-09 5 fallos/15 min → captcha o bloqueo temporal (CA3); RF-A-10 invalidar al cerrar sesión o tras 30 min de inactividad (CA4) — `docs/requerimientos.md:37-40`.
- RNF-SEG-02: tokens invalidados **en backend** al cerrar sesión, expirar (30 min), cambiar contraseña (HU-04) o suspender (HU-09) — `requerimientos.md:147`. RNF-SEG-01: identidad siempre de la sesión — `:146`.
- RNF-SEG-17: "login 5 fallos / 15 min" — `:162`. RNF-SEG-12: auditar accesos (fecha, IP, resultado) — `:157`.
- §8: "Gestión de sesión y tokens (invalidación, 30 min)" se construye en HU-03 y la reutilizan HU-04, HU-09, HU-17 — `requerimientos.md:279`.
- Modelo: tabla `sesion` (id, usuario_id, creada_en, expira_en, revocada) — `docs/modelo-datos.md:18`. Tabla `token` es para VERIFICACION/RECUPERACION (HU-04), no para la sesión — `:17`.
- Puerto previsto: `comun.ValidadorSesion` (lo define comun, lo implementa cuentas) "para que el filtro de seguridad revise si la sesión está revocada" — `docs/architecture.md:129`.
- Estados: "Correo verificado" habilita iniciar sesión; "Suspendida" no inicia sesión — `architecture.md:173,177`.
- HU-02 dejó pendiente para HU-03 el **aviso "verificar identidad" tras iniciar sesión** del arrendador — `specs/HU-02/requirements.md:24`, `specs/HU-02/design.md:528`.

## 1. Backend existente

### Entidades y tablas
- `usuario` (`db/migration/V1_1__usuarios.sql` + `V1_3__perfil_arrendador.sql:3-6`): `id`, `correo` (único, normalizado lower/trim por CHECK), `hash_contrasena` VARCHAR(100), `rol` (USUARIO|ARRENDADOR|ADMIN), `correo_verificado` BOOLEAN default false, `estado` (ACTIVA|SUSPENDIDA|ELIMINADA), `version_politica`, `fecha_consentimiento`, `nombre`, `telefono`.
- `cuentas/domain/Usuario.java`: getters para todo (`getHashContrasena` l.90, `getRol` l.94, `getEstado` l.98, `isCorreoVerificado` l.102); `normalizarCorreo` estático (l.78). Ambas fábricas fijan `correoVerificado = true` (l.63, decisión S5 de HU-01) → hoy toda cuenta registrada puede iniciar sesión. No hay métodos de dominio de login ni contador de fallos.
- `perfil_arrendador` / `PerfilArrendador.java`: `estado_identidad` (NO_INICIADA al registrarse) — útil para el aviso post-login del arrendador. `PerfilArrendadorRepository` (PK = usuario_id, `findById` sirve).
- Enums: `Rol` (USUARIO, ARRENDADOR, ADMIN), `EstadoUsuario` (ACTIVA, SUSPENDIDA, ELIMINADA), `EstadoIdentidad`.
- **No existen** tablas/entidades `sesion` ni `token`. HU-01 explícitamente no creó `token` (`specs/HU-01/design.md:392`).

### Repositorios
- `UsuarioRepository.findByCorreo(String correoNormalizado)` — `cuentas/repository/UsuarioRepository.java:11`. Reutilizable tal cual para login.

### Servicios / controladores / endpoints
- `POST /api/usuarios` (`UsuarioController`, 202) y `POST /api/arrendadores` (`ArrendadorController`, 202); `GET /api/politicas-privacidad/vigente`. Ningún endpoint de login/logout/"yo".
- Patrón a imitar: `RegistroService` (l.47-85): normaliza correo, `PasswordEncoder`, `TransactionTemplate`, `Clock` inyectado, auditoría vía `EventoAuditoria`, hash calculado siempre para no filtrar existencia por tiempo (l.55-56). HU-03 debería hacer un `matches` contra un hash ficticio cuando el correo no existe (mismo motivo).
- `NotificadorCorreo` (interfaz) + `NotificadorCorreoSimulado` — no aplica a HU-03.
- IP del cliente: `comun/web/IpCliente.de(req)` = `getRemoteAddr()` (sin X-Forwarded-For) — `comun/web/IpCliente.java:10-12`.

### Spring Security — `comun/seguridad/ConfiguracionSeguridad.java`
- l.26-35: CSRF **deshabilitado** con comentario "HU-03 decide el CSRF junto con la sesión"; httpBasic y formLogin off; CORS con defaults; `permitAll` solo para los 3 endpoints públicos; `anyRequest().authenticated()`. **No hay** `sessionManagement`, ningún filtro propio de autenticación, ni `AuthenticationEntryPoint` (un 401 hoy lo genera Spring por defecto, sin `ErrorDTO`).
- l.41-50: `corsConfigurationSource` con origen `espacioya.frontend.url`, métodos GET/POST/PUT/PATCH/DELETE/OPTIONS, **headers permitidos solo `Content-Type`** (si se usa `Authorization: Bearer` hay que añadirlo) y **sin `allowCredentials`** (si se usa cookie hay que activarlo).
- l.52-55: `PasswordEncoder` = `BCryptPasswordEncoder(12)` — reutilizable para `matches`.
- Diseño HU-01: "HU-03 extiende esta clase con la sesión" — `specs/HU-01/design.md:227-229`.

### JWT / sesión / cookies
- **Nada existe.** No hay dependencia JWT (jjwt, nimbus, oauth2-resource-server) en `pom.xml`; no hay cookies, ni sesión HTTP, ni propiedades de expiración en `application.properties`.

### Rate limiting — `comun/limite/`
- `LimitadorIntentos.intentar(clave, maximo, ventana)` (l.23-40): ventana deslizante en memoria (`ConcurrentHashMap<String, Deque<Instant>>`), usa `Clock`; **registra cada intento aceptado** y no registra los rechazados (l.32). No tiene método para consultar sin registrar ni para "registrar solo fallos" ni para limpiar una clave tras éxito.
- `ReglaLimite` (record: metodo, ruta, maximo, ventana, grupo) — clave = `grupo|ip` (l.19-21): **solo por IP**, no por correo.
- `FiltroLimiteIntentos` (`@Component OncePerRequestFilter`, l.20-53): aplica todas las `ReglaLimite` beans por método+ruta exacta, contando **todas** las solicitudes (no solo fallidas) y respondiendo 429 con `ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS`.
- Reglas actuales: `cuentas/controller/ReglasLimiteCuentas.java` (registro 5/h por IP, grupo compartido).
- ⇒ Para CA3 ("5 **fallos** en 15 min") el filtro tal cual no sirve (contaría éxitos también y solo por IP). Se puede reutilizar `LimitadorIntentos` desde el servicio de login, pero hace falta ampliarlo (p. ej. `estaBloqueado(clave, max, ventana)` sin registrar + `registrar(clave)` + quizá `limpiar(clave)`), o una pieza nueva.

### Auditoría — `comun/auditoria/`
- `AuditoriaService.registrar(EventoAuditoria)` (`@Transactional`, fecha con `Clock`); `EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle)`; tabla `auditoria` (`V1_2`). `usuario_id` admite NULL (útil para intentos con correo inexistente). Acciones existentes: `REGISTRO_USUARIO`, `REGISTRO_CORREO_EXISTENTE`, `REGISTRO_ARRENDADOR`, `REGISTRO_DOCUMENTO_EXISTENTE`. HU-03 añadiría p. ej. `LOGIN_EXITOSO`, `LOGIN_FALLIDO`, `LOGIN_BLOQUEADO`, `LOGOUT` (el correo no debe ir en claro en `detalle`, por Ley 29733 como hace `NotificadorCorreoSimulado`).

### Manejo de errores — `comun/error/`
- `ErrorDTO(mensaje, errores)` con constantes `MENSAJE_VALIDACION`, `MENSAJE_INESPERADO`, `MENSAJE_DEMASIADOS_INTENTOS` (l.7-10). `ReglaNegocioException` → siempre **400** (`ManejadorGlobalErrores.java:21-28`). No hay excepción/handler para 401 (credenciales inválidas) ni 403; HU-03 necesita uno (p. ej. `CredencialesInvalidasException` → 401 con mensaje genérico) y un `AuthenticationEntryPoint` que escriba `ErrorDTO` para tokens ausentes/expirados.
- Otros: `ConfiguracionReloj` (`Clock` bean UTC) — inyectable para expiración e inactividad; `comun/cifrado/*` (no aplica).

### Migraciones (Flyway)
- `db/migration/V1_1__usuarios.sql`, `V1_2__auditoria.sql`, `V1_3__perfil_arrendador.sql`. Siguiente número natural: **`V1_4__sesion.sql`** (y si se persisten intentos de login, otra tabla/migración). `ddl-auto=validate` (`application.properties`): la entidad debe calzar con la tabla.

### pom.xml
- Spring Boot **4.1.2**, Java 25. Starters: webmvc, validation, data-jpa, flyway (+ flyway-database-postgresql), postgresql, **security**; test: webmvc-test, validation-test, data-jpa-test, flyway-test, security-test, spring-boot-testcontainers, testcontainers-junit-jupiter/postgresql. Jackson 3 (`tools.jackson.databind.json.JsonMapper`, usado en `FiltroLimiteIntentos`). Sin JWT ni captcha.

### Tests
- Unitarios `*Test` (`./mvnw test`, JUnit5/AssertJ/Mockito) por clase de dominio/servicio/comun (p. ej. `LimitadorIntentosTest`, `RegistroServiceTest`).
- Integración `*IT` (`./mvnw verify`, failsafe): `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfiguration.class, Dobles.class)` con **PostgreSQL 17 en Testcontainers** (`TestcontainersConfiguration.java`). `Dobles` reemplaza `Clock` por `RelojControlable` (`avanzar(Duration)`) `@Primary` — ideal para probar 15 min de bloqueo y 30 min de inactividad. Cada test usa una IP distinta (`SIGUIENTE_IP`) para no chocar con el limitador en memoria; verifica auditoría con `JdbcTemplate`.
- Llaves de cifrado de prueba en `src/test/resources/config/application.properties`.

## 2. Frontend existente

- `src/api/cliente.js` (l.14-35): `peticion(ruta, {metodo, cuerpo})` sobre `fetch`, URL base `VITE_API_URL`, convierte errores a `{mensaje, errores}`; error de red "No pudimos conectar. Intenta de nuevo.". **No** envía `credentials: 'include'` ni header `Authorization`; no maneja 401 globalmente.
- `src/api/cuentas.js`: `obtenerVersionPrivacidad`, `registrarUsuario`, `registrarArrendador`. Falta `iniciarSesion`, `cerrarSesion`, (`obtenerSesion`/"yo").
- `src/components/ui/`: `Boton` (variante, `cargando` → "Enviando…", `tipo`), `Campo` (etiqueta, nombre, tipo incl. checkbox/select, valor, onChange, error, ayuda), `Alerta` (tipo exito|atencion|error|info). Suficientes para el formulario de login. `docs/frontend.md:34` lista también EtiquetaEstado, Tarjeta, Modal, SelectorBloques (no existen aún).
- Rutas (`App.jsx`): `/`, `/registro`, `/registro-arrendador`, `/privacidad`. No hay `/iniciar-sesion` ni paneles por rol, ni ruta protegida. `main.jsx` monta `BrowserRouter`.
- Páginas `pages/cuentas/Registro.jsx`, `RegistroArrendador.jsx` (patrón: estado local, `Campo`/`Boton`/`Alerta`, errores por campo del `ErrorDTO`); textos compartidos en `pages/cuentas/textosCuentas.js`.
- **No existe** contexto/estado de autenticación (no hay `AuthContext`, ni hooks, ni almacenamiento de token).
- No hay framework de pruebas frontend (package.json sin vitest/testing-library); solo eslint.
- `vite.config.js` sin proxy (backend y SPA en orígenes distintos: 5173 vs 8080) → si se usa cookie, cross-site: SameSite/CORS credentials importan.
- `docs/frontend.md`: no define mensajes específicos de login; reglas generales (`:183-193`): error de API → `Alerta error` con el mensaje del `ErrorDTO` tal cual; mensajes de seguridad genéricos sin revelar si el correo existe; textos en "tú". `:205`: "Cómo viaja la sesión (cookie o token) lo decide el spec de HU-03".

## 3. Huecos (qué construir)

Backend:
1. Migración `V1_4__sesion.sql` + entidad `Sesion` + `SesionRepository` (según modelo: id, usuario_id, creada_en, expira_en, revocada; probablemente + hash del token y última actividad para inactividad deslizante — ver decisión D3).
2. Endpoint(s): `POST /api/sesiones` (login), `DELETE /api/sesiones/actual` o `POST .../cerrar` (logout), y algo tipo `GET /api/sesiones/actual` (rol, nombre, aviso identidad) para que la SPA sepa a qué panel ir y restaure la sesión. Nombres REST plurales en minúsculas (RNF-ARQ-06).
3. Servicio de login: buscar por correo normalizado, `matches` (con hash señuelo si no existe), rechazar si `!correoVerificado` o `estado != ACTIVA` con el **mismo** mensaje genérico, crear sesión, auditar.
4. Filtro de autenticación en la cadena de Spring Security que valide el token contra `sesion` (puerto `comun.ValidadorSesion` implementado en cuentas) y renueve/rechace por inactividad; `AuthenticationEntryPoint` con `ErrorDTO` 401; `permitAll` para el login.
5. Rate limiting por fallos (CA3): extensión de `LimitadorIntentos` o componente nuevo; nueva constante de mensaje si difiere de `MENSAJE_DEMASIADOS_INTENTOS`.
6. Manejo de 401 en `ManejadorGlobalErrores` (hoy `ReglaNegocioException` = 400).
7. Ajustes CORS (header `Authorization` o `allowCredentials`) y decisión CSRF.
8. Propiedades nuevas (p. ej. `espacioya.sesion.inactividad=30m`) y, si JWT, secreto en `.env.example`.

Frontend:
9. `pages/cuentas/IniciarSesion.jsx` + ruta (p. ej. `/iniciar-sesion`), `api/cuentas.js` (login/logout/sesión), contexto de sesión, botón "Cerrar sesión" en el encabezado, paneles mínimos por rol (USUARIO / ARRENDADOR con aviso de identidad si `estado_identidad != VERIFICADA`), manejo global de 401 → volver a login. Enlace a login en `/`.

## 4. Decisiones abiertas (para spec_author / dueño)

- **D1 — Mecanismo de sesión:** docs no fija JWT vs sesión opaca (`docs/frontend.md:205`). RNF-SEG-02 exige invalidación en backend y el modelo tiene tabla `sesion` ⇒ encaja mejor un **token opaco aleatorio guardado con hash en `sesion`** (o un JWT con `jti` = id de sesión, que igual requiere consulta a BD; JWT añadiría dependencia y secreto sin beneficio claro).
- **D2 — Transporte/almacenamiento:** cookie `HttpOnly; Secure; SameSite` (requiere `allowCredentials`, `credentials:'include'` y decidir CSRF; ojo orígenes distintos 5173/8080) vs `Authorization: Bearer` guardado en memoria/sessionStorage (sin CSRF, pero expuesto a XSS; añadir `Authorization` a CORS). `ConfiguracionSeguridad.java:26` deja CSRF a esta decisión.
- **D3 — 30 min de inactividad:** expiración deslizante (actualizar `expira_en`/`ultima_actividad` en cada solicitud autenticada) vs expiración absoluta de 30 min desde el login. CA4 dice "inactivo" ⇒ deslizante; el modelo solo tiene `expira_en` (¿basta con moverlo?). ¿Tope absoluto adicional?
- **D4 — CA3 "captcha o bloqueo temporal":** el CA deja la opción abierta. No hay captcha en el stack ⇒ bloqueo temporal es lo más simple. Definir: clave del contador (por correo, por IP o ambas — por correo permite bloquear la cuenta atacada, pero permite DoS dirigido a un usuario; por IP no frena ataques distribuidos), duración del bloqueo (¿15 min deslizante como el limitador?), si un login exitoso reinicia el contador, si durante el bloqueo se responde 429 genérico aunque la contraseña sea correcta, y si el contador vive en memoria (como `LimitadorIntentos`; se pierde al reiniciar y no escala a varias instancias) o en BD.
- **D5 — Mensaje genérico:** texto exacto para credenciales incorrectas (p. ej. "Correo o contraseña incorrectos.") y si cuentas no verificadas/suspendidas/eliminadas reciben el mismo mensaje (RNF-SEG-13 sugiere que sí). Código HTTP (401).
- **D6 — Panel del rol:** no existe ningún panel; definir rutas mínimas (p. ej. `/panel` según rol, o `/mi-cuenta` y `/panel-arrendador`) y qué muestran; incluir el aviso de verificar identidad del arrendador heredado de HU-02. ADMIN: ¿se excluye de HU-03 (cuentas admin por script; panel admin con restricción de IP hasta HU-17)?
- **D7 — Sesiones concurrentes:** ¿se permiten varias sesiones por usuario? (HU-04 cerrará "todas las sesiones activas", así que el diseño debe soportar revocar por usuario).
- **D8 — Auditoría de intentos:** acciones y detalle (resultado, motivo sin revelar datos); `usuario_id` NULL cuando el correo no existe; si se audita el intento bloqueado.
- **D9 — IP real:** `IpCliente` usa `getRemoteAddr()`; detrás de un proxy en UAT el rate limiting por IP vería la IP del proxy (decisión transversal, no exclusiva de HU-03).
- **D10 — Limpieza de sesiones expiradas:** sin jobs (ver §9.8); validar al consultar es suficiente.
