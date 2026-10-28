# Exploración HU-04 · Recuperación de contraseña

> Explorador del arnés SDD · 2026-10-28 · rama `feature/HU-04-recuperacion-contrasena` (desde `develop` en `832d7a1`).
> Solo lectura. Rutas Java relativas a `2_Backend/src/main/java/pe/edu/ulima/espacioya/` salvo que se indique otra cosa.
> Fuente de la historia: `docs/historias/HU-04.md` (CA1 enlace de 30 min y mensaje idéntico; CA2 enlace expirado o usado se rechaza y se puede pedir otro; CA3 al cambiar la contraseña se cierran todas las sesiones; STRIDE S/T, I, D, R).

---

## 1. Sesiones (HU-03)

**Tabla:** `sesion`, creada por `2_Backend/src/main/resources/db/migration/V1_4__sesion.sql`:
`id`, `usuario_id` (FK a `usuario` con `ON DELETE CASCADE`), `hash_token CHAR(64)` (único `uq_sesion_hash_token`, `CHECK ^[0-9a-f]{64}$`), `creada_en`, `expira_en`, `revocada BOOLEAN DEFAULT FALSE`; `CHECK expira_en > creada_en`; índice **`ix_sesion_usuario (usuario_id)`**, creado a propósito para que HU-04/HU-09 revoquen todas las sesiones de un usuario (`specs/HU-03/design.md` DT-10, línea 30).

**Entidad:** `cuentas/domain/Sesion.java`
- `INACTIVIDAD = Duration.ofMinutes(30)` (l. 20).
- `Sesion.iniciar(usuarioId, hashToken, ahora)` → `expiraEn = ahora + 30 min`, `revocada = false` (l. 45-53).
- `estaVigente(ahora)` = `!revocada && ahora.isBefore(expiraEn)` (l. 55-57).
- `renovar(ahora)` mueve `expiraEn` a ahora + 30 min (l. 59-61).
- `revocar()` pone `revocada = true` (l. 63-65).

**Repositorio:** `cuentas/repository/SesionRepository.java` solo tiene `findByHashToken(String)` (l. 11).
**No existe** ningún método para revocar todas las sesiones de un usuario (ni `findByUsuarioId`, ni un `@Modifying UPDATE`). CA3 tiene que agregarlo (p. ej. `@Modifying @Query("update Sesion s set s.revocada = true where s.usuarioId = :id and s.revocada = false")` o `findByUsuarioIdAndRevocadaFalse` + `revocar()` por fila). El índice ya existe.

**Creación:** `cuentas/service/InicioSesionService.java#iniciar` (l. 64-90): genera `TokenAleatorio.generar()` (l. 83), guarda `Sesion.iniciar(cuenta.getId(), TokenAleatorio.hash(token), Instant.now(reloj))` dentro de `TransactionTemplate` (l. 84-87), audita `LOGIN_EXITOSO` y devuelve `SesionIniciadaDTO(token, rol, aviso)`. bcrypt se compara fuera de la transacción y hay un hash señuelo (`hashSenuelo`, l. 61, 92-102) para que el tiempo no revele si el correo existe.

**Validación:** `cuentas/service/SesionService.java` implementa `ValidadorSesion`:
- `validar(token)` (l. 42-52): busca por `TokenAleatorio.hash(token)`, filtra `estaVigente`, carga el usuario y **renueva** la sesión (inactividad deslizante); devuelve `UsuarioAutenticado(usuarioId, sesionId, rol)`.
- ⚠ No revisa `usuario.estado` (pendiente anotado en `progress/history.md` l. 234-235, para HU-09).
- `cerrar(usuario, ip)` (l. 61-68): revoca **solo** la sesión del token (`usuario.sesionId()`) y audita `CIERRE_SESION` (entidad `sesion`).

**Puerto:** `comun/seguridad/ValidadorSesion.java` — `Optional<UsuarioAutenticado> validar(String token)`; lo define `comun` y lo implementa `cuentas` (tabla de puertos de `docs/architecture.md` l. 129). `UsuarioAutenticado` es el record `comun/seguridad/UsuarioAutenticado.java` (`usuarioId`, `sesionId`, `rol`).

**Filtro:** `comun/seguridad/FiltroAutenticacionSesion.java` (no es `@Component`; se crea en `ConfiguracionSeguridad` l. 39) lee `Authorization: Bearer <token>`, llama al puerto y llena el `SecurityContext` con `ROLE_<rol>`. Sin token válido, `PuntoEntradaNoAutenticado` responde 401 con `ErrorDTO.MENSAJE_SESION_INVALIDA`.

**Expiración por inactividad:** deslizante, sin tope absoluto; cada solicitud autenticada fija `expira_en = ahora + 30 min` (`SesionService.validar` l. 49). Las vencidas no se borran ni hay jobs: se rechazan al consultarlas (comentario de `V1_4__sesion.sql`, DT-4 de HU-03). Sesiones concurrentes permitidas (P7 / DT-10 de HU-03).

**Endpoints de sesión:** `cuentas/controller/SesionController.java` → `POST /api/sesiones` (201), `GET /api/sesiones/actual`, `DELETE /api/sesiones/actual` (204).

---

## 2. Correo

**Puerto:** `cuentas/service/NotificadorCorreo.java` — un solo método: `void notificarVerificacion(String correoNormalizado)` (l. 5). Vive en `cuentas/service/`, **no** en `comun` ni en `service/puertos/`.

**Implementación única:** `cuentas/service/NotificadorCorreoSimulado.java` (`@Component`) — escribe en el log solo `log.info("Envío de correo de verificación simulado: no se envió ningún correo (HU-01).")` (l. 15). El correo **no** va al log (comentario l. 14, Ley 29733); "HU-25 conecta el envío real".

**Quién lo llama:**
- HU-01: `cuentas/service/RegistroService.java:74` (`notificador.notificarVerificacion(correo)`, dentro de la transacción de registro).
- HU-02: `cuentas/service/RegistroArrendadorService.java:101`.
- HU-03: no lo usa. HU-05: no lo usa ("sin rate limiting ni correo", `progress/history.md` l. 254).

**Dobles de prueba que implementan el puerto** (se rompen al compilar si se agrega un método abstracto a la interfaz):
- `2_Backend/src/test/java/.../cuentas/controller/UsuarioControllerIT.java:94` (`NotificadorFalso implements NotificadorCorreo`).
- `2_Backend/src/test/java/.../cuentas/controller/ArrendadorControllerIT.java:104` (`NotificadorFalso`, guarda los correos y puede simular fallo).
- `RegistroServiceTest:54` y `RegistroArrendadorServiceTest:69` usan un mock de Mockito (no se rompen).
- `NotificadorCorreoSimuladoTest` (`2_Backend/src/test/java/.../cuentas/service/`) verifica (HU-01 R29, R30, R4) que el log no lleve el correo y que el simulado **no tenga parámetros de constructor ni campos que no sean de `org.slf4j.`** (l. 27-34). Si HU-04 le inyecta la URL del frontend o un `Clock`, ese test falla y hay que ajustarlo.

**URL del frontend:** existe la propiedad `espacioya.frontend.url=${FRONTEND_URL:http://localhost:5173}` (`2_Backend/src/main/resources/application.properties:20`, también en `2_Backend/.env.example` como `FRONTEND_URL`). Hoy **solo** la usa el bean CORS (`comun/seguridad/ConfiguracionSeguridad.java:54`). No hay ninguna propiedad ni código que construya enlaces para correos.

**¿Algo de correo en `comun`?** No. No existe `comun/correo/` ni nada movido o duplicado. `docs/architecture.md` sí lo prevé ahí: l. 54 (`comun` incluye "correo"), l. 103 (`comun: auditoría, correo, …`) y l. 214 (HU-25 ★ "`comun/correo`: crear el correo según el evento; adaptador del proveedor"). `docs/requerimientos.md` §8 (l. 280): "Envío de correo detrás de una interfaz propia", primera historia HU-01, lo reutiliza HU-04, y HU-25 formaliza Factory Method + Adapter.

---

## 3. Límite de intentos

**`comun/limite/LimitadorIntentos.java`** (`@Component`, en memoria, `ConcurrentHashMap<String, Deque<Instant>>`, ventana deslizante con el `Clock`):
- `intentar(clave, maximo, ventana)` (l. 23-37): purga, acepta si hay cupo y **solo registra el intento si se acepta** (un 429 no cuenta).
- `superaLimite(clave, maximo, ventana)` (l. 39-47), `registrar(clave)` (l. 49-55), `limpiar(clave)` (l. 57-59): añadidos por HU-03 (DT-6).
- La clave es un `String` libre: el limitador no sabe de IP ni de correo.

**`comun/limite/ReglaLimite.java`** — `record ReglaLimite(metodo, ruta, maximo, ventana, grupo)`; `aplicaA` compara método y **ruta exacta** (l. 15-17); `clave(String ip)` = `grupo + "|" + ip` (l. 19-21).

**`comun/limite/FiltroLimiteIntentos.java`** (`@Component`, recibe `List<ReglaLimite>`): para cada regla que aplica llama a `limitador.intentar(regla.clave(IpCliente.de(solicitud)), …)` (l. 38-39) y responde 429 + `ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS` (l. 47-52). **Solo limita por IP**: no lee el cuerpo (leerlo en el filtro consumiría el stream y obligaría a envolver la solicitud).

**`cuentas/controller/ReglasLimiteCuentas.java`** (`@Configuration`): dos beans `ReglaLimite` para `POST /api/usuarios` y `POST /api/arrendadores`, 5 por IP por hora, grupo compartido `"registro"` (l. 13-27).

**Lo que añadió HU-03:** límite por **correo** hecho **en el servicio**, no en el filtro: `InicioSesionService` usa la clave `"login|" + correoNormalizado` (`PREFIJO_CLAVE` l. 38, l. 66-67) con `superaLimite` / `registrar` / `limpiar`, y lanza `DemasiadosIntentosException` (→ 429 por `ManejadorGlobalErrores`). Cuenta también para correos inexistentes para que el 429 no delate si la cuenta existe (DT-5).

**Cómo agregaría HU-04 "3 por correo por hora":** igual que HU-03, en el servicio de recuperación, con `limitador.intentar("recuperacion|" + correoNormalizado, 3, Duration.ofHours(1))` sobre el correo normalizado (`Usuario.normalizarCorreo`), contando exista o no la cuenta. Queda por decidir qué se responde al superarlo (ver Riesgos R3). Opcionalmente, un bean `ReglaLimite` adicional por IP en `ReglasLimiteCuentas` para el endpoint de solicitud (y/o el de confirmación) si se quiere frenar el barrido de correos desde una IP; no lo pide la historia.

---

## 4. Auditoría

- `comun/auditoria/AuditoriaService.java#registrar(EventoAuditoria)` (l. 20-23), `@Transactional`, fecha con el `Clock`.
- `comun/auditoria/EventoAuditoria.java` es un **record**, no un enum: `(Long usuarioId, String accion, String entidad, Long entidadId, String ip, String detalle)`.
- Entidad `comun/auditoria/Auditoria.java` / tabla `auditoria` (`V1_2__auditoria.sql`): `accion VARCHAR(60) NOT NULL`, `entidad VARCHAR(60)`, `entidad_id`, `ip VARCHAR(45)`, `fecha`, `detalle TEXT`; `usuario_id` anulable con FK a `usuario`.
- **Acciones existentes** (constantes `ACCION_*` en cada servicio):
  - `RegistroService`: `REGISTRO_USUARIO`, `REGISTRO_CORREO_EXISTENTE` (l. 22-23).
  - `RegistroArrendadorService`: `REGISTRO_ARRENDADOR`, `REGISTRO_DOCUMENTO_EXISTENTE` (l. 28-29).
  - `InicioSesionService`: `LOGIN_EXITOSO`, `LOGIN_FALLIDO`, `LOGIN_BLOQUEADO` (l. 30-32); el motivo va en `detalle` (`"motivo=" + MotivoRechazoInicioSesion`).
  - `SesionService`: `CIERRE_SESION` (l. 23).
  - `IdentidadArrendadorService`: `CARGA_DOCUMENTO_IDENTIDAD`, `CARGA_DOCUMENTO_RECHAZADA` (l. 47-48).
  - Entidades usadas: `"usuario"`, `"sesion"`.
- **IP:** `comun/web/IpCliente.de(HttpServletRequest)` = `solicitud.getRemoteAddr()` (l. 10-12). El controlador la obtiene y la pasa como `String ip` al servicio (p. ej. `SesionController.java:39`, `UsuarioController.java:35`). No hay manejo de `X-Forwarded-For`.
- Precedente de anti-enumeración en auditoría: cuando el correo no existe se audita con `usuarioId = null` (`InicioSesionService` l. 68-69, 76-78).

---

## 5. Tokens y hashing

- **Utilidad reutilizable:** `comun/seguridad/TokenAleatorio.java`
  - `generar()` (l. 19-23): 32 bytes de `SecureRandom` estático, base64url sin relleno (43 caracteres).
  - `hash(String)` (l. 25-32): SHA-256, hex en minúsculas (64 caracteres). Es lo que HU-03 guarda en `sesion.hash_token`. `specs/HU-03/design.md` l. 78: "lo reutilizará HU-04".
- **Tabla/entidad de token de recuperación o verificación:** **no existe.** HU-01 eliminó el token `VERIFICACION` (decisión S5) y dejó dicho que "la tabla `token` no se crea en HU-01 … la crea HU-04 con su migración, con el `CHECK` de tipos que necesite" (`specs/HU-01/design.md` l. 392-393).
- Otro hashing existente (no aplica a tokens): `comun/cifrado/HuellaCampos` (HMAC-SHA256 con llave, para `hash_documento`) y `CifradorCampos` (AES-256-GCM). Para el token de recuperación basta SHA-256 sin llave, como la sesión (32 bytes aleatorios no se pueden adivinar).

---

## 6. Contraseña

- `cuentas/domain/PoliticaContrasena.java`: `cumple(contrasena, correoNormalizado)` (l. 18-29): 8–64 code points, ≤ 72 bytes UTF-8 (tope de bcrypt), al menos una letra y un dígito `0-9`, distinta del correo sin distinguir mayúsculas. Mensaje `REQUISITOS` (l. 7-8): "La contraseña debe tener entre 8 y 64 caracteres, al menos una letra y un número, y no puede ser igual a tu correo." `specs/HU-01/design.md` l. 534: "deja una sola fuente que HU-04 (recuperación) reutiliza".
- Uso actual: los servicios lanzan `new ReglaNegocioException("contrasena", PoliticaContrasena.REQUISITOS)` (`RegistroService.java:52-54`); los DTO de registro solo llevan `@NotNull(message = PoliticaContrasena.REQUISITOS)` (`RegistroUsuarioDTO`).
- `PasswordEncoder`: bean `codificadorContrasenas()` = `new BCryptPasswordEncoder(12)` (`comun/seguridad/ConfiguracionSeguridad.java:26, 64-67`).
- `cuentas/domain/Usuario.java`: campo `hashContrasena` (`hash_contrasena VARCHAR(100)`, l. 27-28) con solo `getHashContrasena()` (l. 105). **No hay** método para cambiar la contraseña ni setters (entidad sin `@Version`). HU-04 debe agregar algo como `Usuario.cambiarContrasena(String nuevoHash)`.
- `Usuario.normalizarCorreo` (l. 79-81) y `motivoRechazoInicioSesion()` (l. 84-95, orden ADMIN → correo no verificado → estado no `ACTIVA`).
- Frontend: el texto de requisitos está duplicado en `3_frontend/src/pages/cuentas/textosCuentas.js` (`REQUISITOS_CONTRASENA`).

---

## 7. Seguridad web

`comun/seguridad/ConfiguracionSeguridad.java#cadenaSeguridad` (l. 28-49):
- CSRF deshabilitado (l. 33; comentario: el token va en `Authorization`, el navegador no lo envía solo, HU-03 DT-3), `httpBasic` y `formLogin` deshabilitados, `STATELESS`, CORS con defaults, `PuntoEntradaNoAutenticado`, filtro de sesión antes de `UsernamePasswordAuthenticationFilter`.
- `permitAll` actuales (l. 41-46): `GET /api/politicas-privacidad/vigente`, `POST /api/usuarios`, `POST /api/arrendadores`, `POST /api/sesiones`, `GET /api/archivos`. El resto `.anyRequest().authenticated()` (l. 47).
- **Cómo agregar uno nuevo:** otra línea `.requestMatchers(HttpMethod.POST, "/api/<ruta>").permitAll()` antes de `anyRequest()`. Si la ruta lleva el token en la URL (p. ej. `/api/recuperaciones/{token}`), el matcher necesita patrón (`/api/recuperaciones/*`) y `ReglaLimite.aplicaA` no serviría (compara ruta exacta); con el token en el cuerpo, ruta fija y ambos funcionan.
- CORS (`corsConfigurationSource`, l. 53-62): origen único `espacioya.frontend.url`; métodos GET, POST, PUT, PATCH, DELETE, OPTIONS; encabezados `Content-Type`, `Authorization`; solo `/api/**`.

---

## 8. Base de datos

**Migraciones existentes** en `2_Backend/src/main/resources/db/migration/` (más `.gitkeep`):
1. `V1_1__usuarios.sql`
2. `V1_2__auditoria.sql`
3. `V1_3__perfil_arrendador.sql`
4. `V1_4__sesion.sql`
5. `V2_1__documento_identidad.sql` (HU-05, sprint 2, ya en `develop`)

**Siguiente número libre de sprint 1:** `V1_5__…` (`docs/conventions.md` l. 71-74: `V<sprint>_<n>__<descripcion>.sql`; una migración subida a `develop` no se edita). ⚠ Pero ver Riesgos R1: `V1_5` es **anterior** a `V2_1`, que ya está aplicada en `develop`.

**Tablas relevantes:**
- `usuario` (`V1_1` + `V1_3`): `id`, `correo` (único `uq_usuario_correo`, `CHECK` normalizado), `hash_contrasena VARCHAR(100)`, `rol` (`USUARIO`, `ARRENDADOR`, `ADMIN`), `correo_verificado`, `estado` (`ACTIVA`, `SUSPENDIDA`, `ELIMINADA`), `version_politica`, `fecha_consentimiento`, `nombre`, `telefono`.
- `sesion` (`V1_4`): ver §1.
- `auditoria` (`V1_2`): ver §4.

**`docs/modelo-datos.md`:** sí prevé la tabla (l. 17): **`token` — id, usuario_id, tipo (VERIFICACION, RECUPERACION), hash_token, expira_en, usado_en — historias HU-01, HU-04.** No trae `creado_en` (útil para auditar o para invalidar los anteriores) ni restricción de unicidad del hash en "Restricciones clave". La regla del encabezado (l. 4-6): si el `design.md` cambia un nombre/atributo/estado, lo justifica y actualiza el archivo en el mismo PR. Convención de enums: `VARCHAR` + `@Enumerated(STRING)` + `CHECK` (`specs/HU-01/design.md` l. 400). Como `VERIFICACION` hoy no se usa (S5), el `CHECK` puede admitir solo `RECUPERACION` o ambos (decisión del spec).

**Limpieza en las IT:** cada `*ControllerIT` borra en `@BeforeEach` en orden de FK (p. ej. `SesionControllerIT` l. 101-104: `sesion`, `auditoria`, `perfil_arrendador`, `usuario`). Con FK `ON DELETE CASCADE` desde `token` no habría que tocar esas IT; sin cascada, sí.

---

## 9. Reloj y errores

- **`Clock`:** bean `reloj()` = `Clock.systemUTC()` en `comun/configuracion/ConfiguracionReloj.java:11-14`. Lo inyectan `LimitadorIntentos`, `AuditoriaService`, `InicioSesionService`, `SesionService`, `RegistroService`, etc. (`Instant.now(reloj)`).
- **`RelojControlable`** (`2_Backend/src/test/java/pe/edu/ulima/espacioya/RelojControlable.java`): `Clock` que solo avanza con `avanzar(Duration)`. Las IT lo registran como `@Bean @Primary` en un `@TestConfiguration static class Dobles` (p. ej. `SesionControllerIT.java:89-97`) y avanzan 1 h en cada `@BeforeEach` porque el limitador en memoria se comparte entre tests (l. 105-106). Sirve para probar los 30 min de CA1/CA2 y la ventana de 1 h.
- **Excepciones de `comun/error/` y su mapeo en `ManejadorGlobalErrores.java`:**

  | Excepción | HTTP | Cuerpo |
  |---|---|---|
  | `ReglaNegocioException(campo?, mensaje)` (l. 23-30) | 400 | sin campo: `ErrorDTO(mensaje)`; con campo: `MENSAJE_VALIDACION` + `[{campo, mensaje}]` |
  | `CredencialesInvalidasException` (l. 32-36) | 401 | "Correo o contraseña incorrectos." |
  | `DemasiadosIntentosException` (l. 38-42) | 429 | "Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo." |
  | `MethodArgumentNotValidException` (l. 44-51) | 400 | `MENSAJE_VALIDACION` + errores por campo |
  | `HttpMessageNotReadableException` (l. 53-57) | 400 | `MENSAJE_VALIDACION` |
  | `ValidacionException` (l. 59-62) | 400 | `MENSAJE_VALIDACION` + varios campos |
  | `AccesoDenegadoException` (l. 64-67) | 403 | "No tienes permiso para realizar esta acción." |
  | `RecursoNoEncontradoException` (l. 69-72) | 404 | **"No encontramos lo que buscas o el enlace expiró."** (candidato para CA2) |
  | `MaxUploadSizeExceededException` / `MultipartException` (l. 75-85) | 400 | mensajes de archivo / validación |
  | `Exception` (l. 87-96) | código de Spring si no es 5xx; si no, 500 | "Ocurrió un problema. Intenta de nuevo más tarde." |

  Constantes de mensajes en `comun/error/ErrorDTO.java:7-15` (incluida `MENSAJE_SESION_INVALIDA`). El 401 sin sesión lo escribe `PuntoEntradaNoAutenticado`, no el manejador.
- `cuentas/dto/MensajeDTO` (`record MensajeDTO(String mensaje)`) es el cuerpo de respuesta "mensaje idéntico" que ya usa `UsuarioController` (202 + mensaje fijo, l. 22-23, 31-37).

---

## 10. Frontend

- **Kit `3_frontend/src/components/ui/`:** `Boton({ variante='primario', cargando, deshabilitado, onClick, tipo='button', children })` (muestra "Enviando…" al cargar); `Campo({ etiqueta, nombre, tipo='text', valor, onChange, error, ayuda, opciones, accept })`; `Alerta({ tipo='info', children })` con `exito`, `atencion`, `error`, `info`; `EtiquetaEstado({ estado })`. Cada uno con su `.module.css`. (`Tarjeta`, `Modal`, `SelectorBloques` están en `docs/frontend.md` pero aún no existen.)
- **`src/api/cliente.js`:** `peticion(ruta, { metodo='GET', cuerpo, autenticada=true })` sobre `fetch` con `VITE_API_URL`; agrega `Authorization: Bearer` si hay token y `autenticada`; convierte errores a `{ estado, mensaje, errores }`; con 401 **y** token borra el token y avisa a `alPerderSesion`; error de red "No pudimos conectar. Intenta de nuevo."; exporta `NO_AUTENTICADO`, `urlApi`, `alPerderSesion`.
- **`src/api/cuentas.js`:** `obtenerVersionPrivacidad`, `registrarUsuario`, `registrarArrendador`, `iniciarSesion` (con `autenticada: false`, l. 43-50), `obtenerSesionActual`, `cerrarSesion`, `enviarDocumentosIdentidad`, `obtenerIdentidad`. Las funciones de HU-04 (solicitar y confirmar) deberían ir aquí con `autenticada: false`.
- **`src/api/tokenSesion.js`:** `leerToken`/`guardarToken`/`borrarToken` en **`sessionStorage`**, clave `espacioya.token`; nunca `localStorage` ni cookies (HU-03 DT-2).
- **Sesión en React:** `src/sesion/ProveedorSesion.jsx` (restaura con `GET /api/sesiones/actual` si hay token; con `alPerderSesion` navega a `/iniciar-sesion` con `state: { mensaje, tipo: 'info' }`), `useSesion`, `RutaProtegida`. Router: `BrowserRouter` en `src/main.jsx`.
- **Rutas en `src/App.jsx`** (l. 71-108): `/`, `/registro`, `/registro-arrendador`, `/privacidad`, `/iniciar-sesion`, `/panel` (protegida), `/panel/identidad` (protegida). No hay ruta de recuperación.
- **Pantalla de login de HU-03:** `src/pages/cuentas/IniciarSesion.jsx`. **No** tiene enlace "¿Olvidaste tu contraseña?"; solo "¿No tienes cuenta? Regístrate" (l. 71-73). Ya muestra un aviso que llega por `useLocation().state` (`{ mensaje, tipo }`, l. 18, 48): sirve para volver al login tras restablecer con "Tu contraseña se cambió. Inicia sesión." Si el usuario tiene sesión, redirige a `/panel` (l. 25).
- Convenciones (`docs/frontend.md` l. 199-214): rutas en español, minúsculas y con guiones (p. ej. `/recuperar-contrasena`, `/restablecer-contrasena`); componentes nunca llaman a `fetch`; un solo botón primario por pantalla.

---

## 11. Requerimientos (`docs/requerimientos.md`)

**Fila de HU-04 en la matriz §7** (l. 242): Épica A · R1 · S1 (sem. 8) · RF-A-11…13 · RNF **SEG-02, 06, 12, 13, 17** · RN **—** · depende de HU-03 · programa Eduardo Saldaña · prueba Ariel Enriquez · patrón —.

**RF:**
- RF-A-11 (l. 41): "Permitir solicitar la recuperación de contraseña mostrando siempre el mismo mensaje y, si la cuenta existe, enviar un enlace válido por 30 minutos." (CA1, Alta)
- RF-A-12 (l. 42): "Rechazar enlaces de recuperación expirados o ya usados y permitir pedir otro." (CA2, Alta)
- RF-A-13 (l. 43): "Cerrar todas las sesiones activas al cambiar la contraseña." (CA3, Alta)

**RNF:**
- RNF-SEG-02 (l. 147): tokens invalidados en el backend al cerrar sesión, expirar (30 min), **cambiar la contraseña** o suspender la cuenta.
- RNF-SEG-06 (l. 151): tokens de recuperación aleatorios, de un solo uso y guardados con hash.
- RNF-SEG-12 (l. 156): auditoría reutilizable (quién, qué, cuándo, IP) en acciones sensibles, incluida "recuperación".
- RNF-SEG-13 (l. 158): TLS, mensajes de error genéricos; "Las respuestas no confirman si un correo existe."
- RNF-SEG-17 (l. 161): rate limiting … "**recuperación 3 por correo / hora**".
- También relevantes (no en la fila): RNF-SEG-05 (bcrypt), RNF-ARQ-08 (l. 186, correo detrás de interfaz propia / Adapter), RNF-USA-04 (l. 195, avisos por correo en eventos clave).

**RN:** ninguna citada.

**§8 componentes transversales relevantes** (l. 274-282): Auditoría (HU-01, HU-03); **Rate limiting — primera historia "HU-01, HU-03, HU-04 (Sprint 1)"**; manejador global de errores; **Gestión de sesión y tokens — HU-03, lo reutiliza HU-04**; **Envío de correo detrás de una interfaz propia — HU-01, lo reutiliza HU-04; HU-25 formaliza Factory Method + Adapter**.

**§9 pendientes** (l. 286-295): ninguno abierto toca HU-04. Los que tocan contraseña o límites están cerrados: §9.5 política de contraseña (resuelto) y §9.7 límite de registros por IP (resuelto; el MFA sigue pendiente pero es HU-17).

---

## 12. Decisiones previas que condicionan HU-04

- **S5 de HU-01 (Jorge Quispe, 2026-10-27)** (`specs/HU-01/requirements.md` l. 37; `progress/history.md` l. 130-132): **no hay correo real** hasta HU-25; el notificador es simulado y solo escribe una línea en el log; las cuentas nacen con `correo_verificado = true`; el CA de "recibo un correo" se dio por cubierto con el envío simulado y el mensaje en pantalla. `docs/architecture.md` l. 172 repite que "Correo pendiente" no se alcanza mientras el correo sea simulado. Precedente directo para HU-04 CA1, pero HU-04 además necesita **entregar un token** (ver R2).
- **Anti-enumeración:** HU-01 responde 202 + mensaje fijo exista o no el correo y calcula el hash siempre (`specs/HU-01/design.md` l. 525-529; `RegistroService` l. 55-56); HU-02 igual con el documento; HU-03 usa 401 genérico idéntico para correo inexistente, contraseña errada, correo no verificado, cuenta no `ACTIVA` y `ADMIN` (DT-8), y hash señuelo (DT-12).
- **Mensajes ya definidos:** `ErrorDTO` (§9). "Te enviamos un correo de verificación. Revisa tu bandeja de entrada." (`UsuarioController`). `PoliticaContrasena.REQUISITOS`. `MENSAJE_RECURSO_NO_ENCONTRADO` = "No encontramos lo que buscas o el enlace expiró."
- **Cuentas suspendidas / admin (HU-03, P1 y DT-7/DT-8):** `SUSPENDIDA`/`ELIMINADA` y `ADMIN` no inician sesión (401 genérico); el admin llega con HU-06. HU-04 debe decidir si a esas cuentas se les emite enlace (ver R5).
- **Arrendador:** inicia sesión igual que el usuario (`Rol.ARRENDADOR`); nada en HU-02/HU-05 lo excluye de la recuperación. HU-04 dice "Como usuario o arrendador".
- **Bloqueo de login (HU-03 DT-5/DT-6):** clave `"login|" + correo`, 5 fallos / 15 min, en memoria, una instancia. HU-04 decide si un restablecimiento exitoso borra ese contador (`limitador.limpiar("login|" + correo)`).
- **Sesiones concurrentes** y "HU-04 cerrará todas" (HU-03 P7, DT-10).
- **Token opaco + SHA-256** (HU-03 DT-1), sin JWT.
- **Migraciones por sprint** (HU-05 P16: `V2_1`, "no `V1_5` como sugería el explorador").
- **Pendientes heredados:** `SesionService.validar` no revisa `usuario.estado` (HU-09).

---

## 13. Otros hallazgos

- `InicioSesionService` y `RegistroService` usan `TransactionTemplate` para dejar bcrypt (~250 ms con costo 12) fuera de la transacción: mismo patrón aplicable al confirmar el cambio de contraseña.
- `NotificadorCorreo` se invoca **dentro** de la transacción de registro (`RegistroService.java:74`): si el adaptador real fallara, revierte el registro (comportamiento probado con `NotificadorFalso.fallar` en `ArrendadorControllerIT`).
- `InicioSesionDTO` sobrescribe `toString()` para no imprimir datos (`correo=***, contrasena=***`); los DTO nuevos con contraseña o token deberían hacer lo mismo.
- `IpCliente` usa `getRemoteAddr()`: detrás de un proxy en UAT todas las IP serían la del proxy (afecta a cualquier `ReglaLimite` por IP, no al límite por correo).

---

## Riesgos y decisiones abiertas para el spec

**R1 — Número de migración (choque de Flyway).** La convención da `V1_5__…` (HU-04 es sprint 1), pero `V2_1__documento_identidad.sql` ya está en `develop`. Flyway, con `outOfOrder=false` (no hay `spring.flyway.*` en `application.properties`), **rechaza al arrancar** una BD que ya aplicó `V2_1` y encuentra `V1_5` pendiente ("Detected resolved migration not applied to database"). En Testcontainers (BD nueva) no se nota; sí en la BD local de quien ya corrió HU-05 y en UAT si ya se desplegó. Opciones: (a) `V1_5` + `spring.flyway.out-of-order=true`; (b) `V1_5` y pedir recrear las BD locales (no vale si UAT ya tiene `V2_1`); (c) `V2_2` (rompe la regla "por sprint" y puede chocar con HU-06 de Ariel Enriquez, a quien se le dijo "la siguiente `V2_n`"). Decisión del dueño; coordinar con HU-06.

**R2 — Correo simulado vs. enlace de punta a punta (CA1, CA2).** El notificador simulado no envía nada y su log no lleva datos; sin otra vía nadie puede abrir el enlace, ni el tester (Ariel) ni la prueba manual. Opciones: (a) agregar `notificarRecuperacion(correo, enlace)` y en el simulado no registrar el enlace (las IT capturan el token con un `NotificadorFalso`; la prueba manual queda imposible hasta HU-25); (b) que el simulado escriba el enlace en el log **solo** con un perfil o propiedad de desarrollo, apagado por defecto (un token en el log es un secreto en texto claro: STRIDE I/S); (c) adelantar un adaptador SMTP de pruebas (Mailpit/MailHog en `1_Database/compose.yaml`), que es alcance de HU-25. Decidirlo explícitamente como S5 de HU-01. En cualquier caso: `NotificadorCorreoSimuladoTest` exige un simulado sin campos ni parámetros (l. 27-34), y los dos `NotificadorFalso` de `UsuarioControllerIT:94` y `ArrendadorControllerIT:104` dejan de compilar si se agrega un método abstracto al puerto (se actualizan o se usa un método `default`).

**R3 — Respuesta al superar "3 por correo por hora".** (I) pide respuesta idéntica exista o no la cuenta. Si se responde 429, debe darse igual para correos inexistentes (contar todos los correos, como HU-03 DT-5): no delata la cuenta, pero sí dice "este correo ya pidió 3". Alternativa: responder siempre el mismo 202/mensaje y simplemente no emitir enlace (más fuerte para I, menos claro para el usuario). Definir también si cuenta la solicitud de un correo con formato inválido (400) y si se añade además un tope por IP (`ReglaLimite`) para frenar el barrido.

**R4 — Ubicación del puerto de correo.** `docs/architecture.md` (l. 54, 103, 214) y §8 lo ubican en `comun` (`comun/correo`, que HU-25 formaliza), pero hoy vive en `cuentas/service/NotificadorCorreo`. HU-04 puede (a) añadir el método al puerto actual en `cuentas` (mínimo cambio, YAGNI; HU-25 lo moverá) o (b) moverlo ya a `comun/correo` (toca HU-01/HU-02 y sus tests). Si lo mueve, justificarlo en `design.md`.

**R5 — Cuentas que no pueden iniciar sesión.** ¿Se emite enlace a `SUSPENDIDA`, `ELIMINADA` o `ADMIN`? Recomendación a validar: no emitir (respuesta idéntica y auditoría con motivo, como `MotivoRechazoInicioSesion`), coherente con HU-03 P1/DT-7 y RN-01; y si se emitió y la cuenta se suspende antes de usarlo, ¿se rechaza al confirmar?

**R6 — Diseño de la tabla `token`.** El modelo prevé `token(id, usuario_id, tipo, hash_token, expira_en, usado_en)`. Decidir: nombre (`token` genérico vs. `token_recuperacion`), `CHECK` de `tipo` (solo `RECUPERACION` o también `VERIFICACION`), `hash_token CHAR(64)` único con `CHECK` hex (como `sesion`), añadir `creado_en` (actualizar `docs/modelo-datos.md` en el mismo PR), FK con `ON DELETE CASCADE` (como `sesion`; las IT borran usuarios), índice por `usuario_id`.

**R7 — Uso único y concurrencia.** Dos confirmaciones simultáneas con el mismo token no deben cambiar la contraseña dos veces: marcar `usado_en` con un `UPDATE … WHERE usado_en IS NULL AND expira_en > ahora` y comprobar filas afectadas, o bloqueo pesimista (precedente: `findParaActualizar` de HU-05). Decidir también si al pedir un enlace nuevo se invalidan los anteriores no usados (CA2 "puedo pedir otro") y si al cambiar la contraseña se invalidan todos los tokens de recuperación pendientes del usuario.

**R8 — CA3 y la sesión de quien confirma.** No existe método para revocar todas las sesiones (`SesionRepository` solo tiene `findByHashToken`); hay que agregarlo, en la misma transacción que el cambio de hash y la marca de uso. Decidir: ¿se inicia sesión automáticamente tras restablecer (crearía una sesión nueva después de revocar todas) o se lleva al login con un aviso? ¿Se borra el contador `"login|" + correo` de HU-03? ¿Se audita la revocación masiva (p. ej. `detalle="sesionesRevocadas=n"`)?

**R9 — Transporte del token en el enlace y en la API.** El enlace lo arma el backend con `espacioya.frontend.url` (hoy solo para CORS). Si el token va en la query (`/restablecer-contrasena?token=…`) queda en el historial y puede filtrarse por `Referer`; en el fragmento (`#token=…`) no llega a ningún servidor. Hacia la API conviene enviarlo en el **cuerpo** del `POST` de confirmación (ruta fija: sirve `ReglaLimite.aplicaA` y el `permitAll` exacto) y no en la URL (logs de acceso). Decidir si hay un endpoint previo para validar el token al abrir el enlace (CA2 "cuando lo abro, se rechaza") o si el rechazo llega al enviar la nueva contraseña. El token que llega se valida en forma (base64url de 43 caracteres) antes de hashearlo.

**R10 — Mensajes y códigos.** Definir: mensaje idéntico de la solicitud (p. ej. "Si el correo está registrado, te enviamos un enlace para restablecer tu contraseña.") y su código (202 + `MensajeDTO`, como HU-01); código y texto para enlace inválido/expirado/usado (¿404 con `MENSAJE_RECURSO_NO_ENCONTRADO`, 400 con mensaje propio?); un solo mensaje para los tres casos (no distinguir "expirado" de "usado" evita dar información; CA2 solo exige rechazar y ofrecer pedir otro); validación de la nueva contraseña con `PoliticaContrasena` (necesita el correo del dueño del token) y si se rechaza una igual a la actual.

**R11 — Tiempo de respuesta y enumeración.** En la solicitud, la rama "existe" genera token, inserta fila y llama al notificador; la rama "no existe" no hace nada. Con el simulado la diferencia es pequeña, pero con SMTP real (HU-25) será medible. Decidir si se iguala el trabajo (p. ej. auditar en ambas ramas, enviar fuera de la transacción) o se acepta y se documenta.

**R12 — Auditoría (R de STRIDE).** Nombres de acción nuevos (≤ 60 caracteres), p. ej. `RECUPERACION_SOLICITADA` (con `usuarioId` o `null` si el correo no existe, y `detalle` con el motivo si no se emitió), `RECUPERACION_LIMITADA` (429), `RECUPERACION_RECHAZADA` (token inválido/expirado/usado; sin el token en `detalle`), `CONTRASENA_RESTABLECIDA` (con nº de sesiones revocadas). Nunca registrar el token ni el correo en claro de un correo inexistente en `detalle` si no hace falta (Ley 29733).

**R13 — Límite en memoria.** El contador por correo vive en `LimitadorIntentos` (se pierde al reiniciar, no se comparte entre instancias, y el mapa crece con correos distintos hasta que se vuelven a consultar). Mismo supuesto aceptado en HU-01/HU-03 (una instancia en UAT); conviene repetirlo como supuesto.

**R14 — Frontend.** Faltan: enlace "¿Olvidaste tu contraseña?" en `IniciarSesion.jsx`, pantalla de solicitud (correo) y pantalla de nueva contraseña (con `REQUISITOS_CONTRASENA` como ayuda y, si se decide, confirmación de la contraseña solo en el cliente), dos funciones en `api/cuentas.js` con `autenticada: false`, dos rutas públicas en `App.jsx`. Si quien restablece tiene una sesión abierta en esa pestaña, tras CA3 su token quedará revocado y el próximo 401 lo llevará al login vía `alPerderSesion`; decidir si la pantalla borra el token local al confirmar.
