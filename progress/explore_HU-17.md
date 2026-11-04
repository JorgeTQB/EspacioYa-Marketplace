# Exploración HU-17 · Autenticación multifactor (MFA)

## Pregunta

Contexto del código existente para diseñar HU-17 (TOTP RFC 6238 obligatorio para ADMIN, opcional para
USUARIO/ARRENDADOR, 5 códigos de respaldo, secretos cifrados, códigos con hash, límite de intentos por
sesión, auditoría). Depende de HU-03 (login/sesión).

## Archivos analizados

Docs: `docs/historias/HU-17.md`, `docs/requerimientos.md` (RF-A-17..19 l.47-49; RNF-SEG-04 l.149, -12 l.157,
-14 l.159, -17 l.162; §9.4 l.291, §9.7 l.294), `docs/modelo-datos.md` (l.18, l.101), `docs/architecture.md`
(l.14, l.432-438), `docs/conventions.md` (l.81-111), `docs/frontend.md` (l.12-20, l.219-290),
`feature_list.json` (l.345-361), `progress/HU-17.md`.

Backend (`2_Backend/src/main/java/pe/edu/ulima/espacioya/`):
`cuentas/controller/SesionController.java`, `cuentas/service/InicioSesionService.java`,
`cuentas/service/SesionService.java`, `cuentas/dto/{InicioSesionDTO,SesionIniciadaDTO,SesionActualDTO}.java`,
`cuentas/domain/{Usuario,Sesion,Token,MotivoRechazoInicioSesion}.java`,
`cuentas/repository/{UsuarioRepository,SesionRepository}.java`, `cuentas/controller/ReglasLimiteCuentas.java`,
`cuentas/controller/UsuarioController.java`, `cuentas/service/{NotificadorCorreo,AvisoCuenta,CreadorAdmin}.java`,
`comun/seguridad/*`, `comun/cifrado/*`, `comun/limite/*`, `comun/auditoria/*`, `comun/error/*`,
`comun/configuracion/ConfiguracionReloj.java`, `administracion/service/CuentaAdminService.java` (l.40, l.103),
`2_Backend/pom.xml`, `src/main/resources/application.properties`, `src/main/resources/db/migration/*`.

Tests: `2_Backend/src/test/java/pe/edu/ulima/espacioya/{RelojControlable,TestcontainersConfiguration}.java`,
`.../cuentas/controller/SesionControllerIT.java`, `.../cuentas/service/InicioSesionServiceTest.java`,
`src/test/resources/config/application.properties`.

Frontend (`3_frontend/`): `package.json`, `src/App.jsx`, `src/api/{cliente,tokenSesion,cuentas}.js`,
`src/sesion/{ProveedorSesion,RutaProtegida,useSesion,retorno,ContextoSesion}.js(x)`,
`src/pages/cuentas/{IniciarSesion,Panel}.jsx`, `src/components/ui/*`.

## Hallazgos

### 1. Backend login/sesión (HU-03)

- **Endpoint:** `cuentas/controller/SesionController.java` → `/api/sesiones`:
  - `POST /api/sesiones` (201, `permitAll`) → `InicioSesionService.iniciar(InicioSesionDTO, ip)` → `SesionIniciadaDTO(token, rol, aviso)`.
  - `GET /api/sesiones/actual` → `SesionActualDTO(rol, aviso)`.
  - `DELETE /api/sesiones/actual` (204) → revoca y audita `CIERRE_SESION`.
- **DTOs:** `InicioSesionDTO(correo ≤254, contrasena ≤72)` con `toString` enmascarado; `SesionIniciadaDTO` también enmascara el token.
- **Tokens / sesiones:** sin JWT ni cookie. Token opaco `TokenAleatorio.generar()` (32 bytes, base64url, 43 chars); en BD solo `SHA-256` hex
  (`sesion.hash_token`). Tabla `sesion` (`V1_4__sesion.sql`): id, usuario_id, hash_token CHAR(64) único, creada_en, expira_en, revocada.
  `Sesion.INACTIVIDAD = 30 min`, se renueva en cada solicitud (`SesionService.validar`). Viaja en `Authorization: Bearer` →
  `comun/seguridad/FiltroAutenticacionSesion` (no es @Component) usa el puerto `comun/seguridad/ValidadorSesion` (implementado por
  `SesionService`) → `UsuarioAutenticado(usuarioId, sesionId, rol)` con autoridad `ROLE_<rol>`. Stateless, CSRF deshabilitado (DT-3).
  `SesionService.validar` rechaza cuentas no ACTIVA (HU-09 D3). `SesionRepository.revocarTodas(usuarioId)`.
- **Contraseña:** `PasswordEncoder` = `BCryptPasswordEncoder(12)` (bean en `comun/seguridad/ConfiguracionSeguridad.java`). Correo desconocido
  compara contra un `hashSenuelo` (tiempo constante). Siempre 401 genérico `CredencialesInvalidasException` ("Correo o contraseña incorrectos.");
  el motivo (`MotivoRechazoInicioSesion`: CONTRASENA_INCORRECTA, CORREO_DESCONOCIDO, CORREO_NO_VERIFICADO, CUENTA_NO_ACTIVA, ROL_NO_PERMITIDO,
  IP_NO_PERMITIDA) va solo al detalle de auditoría.
- **Intentos fallidos:** `InicioSesionService`: `MAXIMO_FALLOS = 5`, `VENTANA_FALLOS = 15 min`, clave `"login|" + correo`
  (`LimitadorIntentos.superaLimite/registrar/limpiar`). Superado → `DemasiadosIntentosException` (429) + auditoría `LOGIN_BLOQUEADO`. No hay
  bloqueo persistente de cuenta: es en memoria. El éxito limpia el contador.
- **Admin hoy:** `InicioSesionService.motivoRechazo` (l.106-110) rechaza al admin fuera de `IpsPermitidasAdmin` *después* de validar la
  contraseña; además `FiltroIpsAdmin` bloquea `/api/admin/**` antes de autenticar (403). Comentarios explícitos "hasta HU-17".
  La creación de la sesión y la auditoría `LOGIN_EXITOSO` ocurren dentro de `TransactionTemplate` (l.88-91).
- **Autorización admin:** no hay `@PreAuthorize`/`hasRole`; cada servicio admin compara `UsuarioAutenticado.rol()` con `"ADMIN"` y lanza
  `AccesoDenegadoException` (p. ej. `administracion/service/CuentaAdminService.java` l.103). Rutas públicas listadas en
  `ConfiguracionSeguridad.cadenaSeguridad` (l.44-66); todo lo demás `authenticated()`.
- **Rate limiting por IP:** `comun/limite/FiltroLimiteIntentos` (@Component) recorre beans `ReglaLimite(metodo, ruta, maximo, ventana, grupo)`
  declarados en `@Configuration` por módulo (ej. `cuentas/controller/ReglasLimiteCuentas.java`: registros 5/IP/hora, grupo compartido).
  Responde 429 con `ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS`. El login no tiene regla por IP, solo por correo en el servicio.

### 2. Entidad/tabla `usuario` y migraciones

- `cuentas/domain/Usuario.java`: id, correo, hashContrasena, rol (`Rol`: USUARIO, ARRENDADOR, ADMIN), correoVerificado, estado
  (`EstadoUsuario`: ACTIVA, SUSPENDIDA, ELIMINADA), versionPolitica, fechaConsentimiento, nombre, telefono. Fábricas `registrar`,
  `registrarArrendador`, `registrarAdmin` (solo por `CreadorAdmin`/`CreacionAdminService`, HU-06 D4). `esAdmin()`.
  `motivoRechazoRecuperacion()` bloquea la recuperación del admin "hasta que exista MFA" (l.107-113).
- SQL: `V1_1__usuarios.sql` (tabla base + CHECK de rol/estado), `V1_3__perfil_arrendador.sql` (añade nombre, telefono).
- **Migraciones** en `2_Backend/src/main/resources/db/migration/`: V1_1…V1_5, V2_1…V2_4, V3_1…V3_3, V4_1…V4_3. **Última: `V4_3__reembolso.sql`.**
  Convención (`docs/conventions.md` l.81-103): numeradas por sprint; HU-17 es sprint 5 → **`V5_1__mfa.sql`** (no existe aún ninguna V5).
  `spring.flyway.out-of-order=true`. `ddl-auto=validate`.
- `docs/modelo-datos.md` l.18 prevé `mfa` (usuario_id, secreto cifrado, activo) y `codigo_respaldo` (usuario_id?, hash_codigo, usado);
  l.101: tablas de Release 2 con migraciones nuevas sin editar las de Release 1. FK con `ON DELETE CASCADE` a usuario es el patrón (las IT
  borran usuarios; HU-22 anonimiza).

### 3. Componentes reutilizables de `comun`

- **Cifrado:** `comun/cifrado/CifradorCampos` (AES-256-GCM, formato `v1:base64(iv‖ct+tag)`, llave `espacioya.cifrado.llave` = `CIFRADO_LLAVE`;
  exige que sea distinta de la de huella) + `ConvertidorCifrado` (`@Convert(converter = ConvertidorCifrado.class)` en un campo String de la
  entidad, como en `PerfilArrendador`). `HuellaCampos` (HMAC-SHA256 hex, `CIFRADO_LLAVE_HUELLA`) para búsqueda/unicidad determinística.
  `LlaveCifrado.decodificar` valida 32 bytes base64. Llaves de prueba en `src/test/resources/config/application.properties`.
- **Hashing:** `TokenAleatorio.hash` (SHA-256 hex) y `TokenAleatorio.generar` (SecureRandom); `PasswordEncoder` bcrypt(12). Para códigos de
  respaldo cortos, SHA-256 sin llave es fuerza-bruteable offline → usar bcrypt o `HuellaCampos` (HMAC con llave) — decisión del spec.
- **Correo:** `cuentas/service/NotificadorCorreo` (interfaz) + `NotificadorCorreoSimulado`; ningún método de MFA hoy (opcional: aviso de
  MFA activado/desactivado).
- **Auditoría:** `comun/auditoria/AuditoriaService.registrar(new EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle))`;
  `accion` ≤60 chars; acciones como constantes `public static final String` en el servicio (p. ej. `LOGIN_EXITOSO`).
- **Errores:** `comun/error/ManejadorGlobalErrores`: `ReglaNegocioException(campo?, mensaje)`→400, `ValidacionException`→400,
  `CredencialesInvalidasException`→401, `AccesoDenegadoException`→403, `RecursoNoEncontradoException`→404, `DemasiadosIntentosException`→429,
  `ServicioExternoException`→503. Mensajes constantes en `ErrorDTO`. `PuntoEntradaNoAutenticado` → 401 `MENSAJE_SESION_INVALIDA`.
- **Reloj:** bean `Clock` en `comun/configuracion/ConfiguracionReloj` (UTC); en tests `RelojControlable` (@Primary en `@TestConfiguration`).
  Necesario para TOTP determinista.
- **Rate limiter:** `comun/limite/LimitadorIntentos` (en memoria, ventana deslizante, `intentar/superaLimite/registrar/limpiar` por clave
  String) — reutilizable con clave tipo `"mfa|" + idDesafio` o por usuario. `FiltroLimiteIntentos` + `ReglaLimite` para límites por IP.
- **IP:** `comun/web/IpCliente.de(request)`. **Transacciones:** `comun/transaccion/DespuesDelCommit`.

### 4. Dependencias (`2_Backend/pom.xml`)

- Spring Boot 4.1.2 (parent), Java 25. starters: webmvc, validation, data-jpa, flyway (+ flyway-database-postgresql), postgresql,
  **security** (comentario "Autenticación, roles y MFA", l.57). Tests: *-test starters, spring-boot-testcontainers, testcontainers-junit-jupiter,
  testcontainers-postgresql. Jackson 3 (`tools.jackson.databind.json.JsonMapper`).
- **No hay librería TOTP, Base32 ni QR.** `commons-codec` (Base32) existe en `~/.m2` solo como transitiva/caché, no declarada.
  TOTP RFC 6238 se puede implementar con JDK (`javax.crypto.Mac` HmacSHA1, ya usado en `HuellaCampos`) + Base32 propio (~30 líneas) sin
  dependencias; agregar una lib (p. ej. `dev.samstevens.totp`, `com.eatthepie:java-totp`) o zxing para QR es decisión del spec
  (CLAUDE.md: agregar dependencias se discute). QR en backend no es necesario: basta devolver la URI `otpauth://` + secreto Base32.
- Frontend: `package.json` solo react 19, react-dom, react-router-dom 7; `docs/frontend.md` l.18-20: "agregar cualquier dependencia al
  package.json se discute antes" → no hay lib de QR en el front.

### 5. Frontend

- **Login:** `3_frontend/src/pages/cuentas/IniciarSesion.jsx` (+ `.module.css`): form correo/contraseña → `iniciarSesion()` →
  `guardarToken(respuesta.token)` → `entrar({rol, aviso})` → `navegar(destino)`. Destino `retornoValido(state.retorno) ?? '/panel'`.
  Habrá que añadir un segundo paso (código TOTP/respaldo) y, para admin sin MFA, el paso de configuración obligatoria.
- **API:** `3_frontend/src/api/cuentas.js`: `iniciarSesion` (l.44, `POST /api/sesiones`, `autenticada:false`), `obtenerSesionActual` (l.52),
  `cerrarSesion` (l.56). Cliente común `src/api/cliente.js` (`peticion(ruta, {metodo, cuerpo, autenticada})`; errores `{estado, mensaje,
  errores}`; un 401 con token borra el token y avisa a `alPerderSesion`). Token en `sessionStorage` (`src/api/tokenSesion.js`, DT-2).
- **Contexto auth:** `src/sesion/ProveedorSesion.jsx` (estado `{rol, aviso}`, carga con `GET /api/sesiones/actual`, `entrar/salir`),
  `useSesion.js`, `ContextoSesion.js`, `RutaProtegida.jsx` (solo comprueba que haya sesión; no roles), `retorno.js`.
  Si el login devolviera un token "parcial" (pendiente de MFA), el `alPerderSesion` y `RutaProtegida` deben tenerlo en cuenta.
- **UI kit** (`src/components/ui/`): `Campo` (text/password/email/select/textarea/checkbox/archivo; props etiqueta, nombre, tipo, valor,
  onChange, error, ayuda), `Boton` (variante primario/secundario, cargando, deshabilitado, tipo), `Alerta` (tipo info/error/exito/atencion),
  `Modal`, `Tarjeta`, `EtiquetaEstado`, `CuentaRegresiva` (útil para la ventana de 30 s del TOTP, opcional), `Imagen`, `Galeria`,
  `SelectorBloques`. Textos por módulo en `pages/cuentas/textosCuentas.js`.
- **Rutas existentes** (`src/App.jsx`): `/`, `/buscar`, `/espacios/:id`, `/reservas/:id`, `/reservas/:id/pago-simulado`, `/mis-reservas`,
  `/registro`, `/registro-arrendador`, `/privacidad`, `/iniciar-sesion`, `/recuperar-contrasena`, `/restablecer-contrasena`, `/panel`,
  `/panel/identidad`, `/panel/espacios[/nuevo|/:id|/:id/disponibilidad]`, `/panel/reservas`, `/admin/identidades[/:id]`,
  `/admin/cuentas[/:id]`, `/admin/reembolsos`.
- **Dónde va la configuración de MFA:** no existe pantalla de perfil/cuenta (HU-20 "Edición de perfil", dueña Adriana Callupe, `pending`).
  Lo natural: `/panel/seguridad` (`pages/cuentas/SeguridadCuenta.jsx` o `ConfigurarMfa.jsx`) con enlace desde `pages/cuentas/Panel.jsx`
  para los tres roles, envuelto en `RutaProtegida`. El QR habría que mostrarlo sin librería (mostrar secreto Base32 + URI `otpauth://`
  para copiar) o discutir una dependencia.

### 6. Patrones de tests (HU-03)

- **Unitarios** (`./mvnw test`, sin Docker): `cuentas/service/InicioSesionServiceTest.java` (19 tests) — `@ExtendWith(MockitoExtension)`,
  `@Mock` repositorios/encoder/auditoría, `Clock.fixed`, `new IpsPermitidasAdmin("127.0.0.1,::1")`, `TransactionTemplate` con
  `PlatformTransactionManager` mock, `ArgumentCaptor<EventoAuditoria>`. También `SesionServiceTest` (7), `cuentas/domain/SesionTest`,
  `UsuarioTest`, `cuentas/dto/InicioSesionDTOTest`. `@DisplayName` con historia y R<n>.
- **Integración** (`*IT`, `./mvnw verify`, failsafe): `cuentas/controller/SesionControllerIT.java` (29 tests) — `@SpringBootTest`,
  `@AutoConfigureMockMvc`, `@Import({TestcontainersConfiguration.class, Dobles.class})` con `RelojControlable` `@Primary`; limpia tablas con
  `JdbcTemplate` en `@BeforeEach`; avanza el reloj 1 h para vaciar el limitador en memoria; IP distinta por test (`10.3.x.x`, cada
  historia usa su prefijo: HU-01 10.1, HU-02 10.2, HU-03 10.3) con `.with(req -> req.setRemoteAddr(ip))`. Para HU-17: limpiar también
  las tablas `mfa`/`codigo_respaldo` y en *todas* las IT que hoy hacen `DELETE FROM usuario` si las FK no son en cascada.
- **Frontend:** no hay tests (no hay vitest ni script `test`); `docs/frontend.md` l.14-17: los R<n> se verifican con tests del backend;
  la pantalla con lint, build y caja negra en UAT.
- **Ojo:** las IT existentes que hacen login como ADMIN (HU-06, HU-09, HU-16: `RevisionIdentidadControllerIT`, `CuentaAdminControllerIT`,
  reembolsos admin, etc.) se romperán si el login del admin pasa a exigir MFA. Hay que prever un helper de test para crear admin con MFA
  y completar el segundo factor (o sembrar sesión directamente en BD).

### 7. Referencias existentes a MFA/TOTP/2FA

- Código: comentarios "hasta HU-17" en `InicioSesionService.java` l.106, `Usuario.java` l.107, `IpsPermitidasAdmin.java` l.12,
  `FiltroIpsAdmin.java` (javadoc), `application.properties` l.48, `2_Backend/.env.example` l.36, `pom.xml` l.57.
  Ninguna implementación de MFA/TOTP.
- Docs: `docs/requerimientos.md` RF-A-17/18/19, RNF-SEG-04/12/14/17, §9.4 (HU-17 decide si retirar o mantener el filtro de IPs) y
  §9.7 (**límite de intentos MFA pendiente: definirlo en el diseño**); `docs/modelo-datos.md` l.18/l.101; `docs/architecture.md` l.14,
  l.432; `docs/historias/HU-27.md` l.20 ("Solo admins con MFA (HU-17)"); `README.md` l.10, l.120; `specs/HU-03/requirements.md` l.250,
  `specs/HU-03/design.md` l.11; `specs/HU-06/{requirements,design}.md`; `progress/review_HU-03.md` l.63.

## Riesgos / huecos para el spec

1. **Flujo de login en dos pasos:** hoy `POST /api/sesiones` emite directamente la sesión completa. Decidir cómo representar el estado
   intermedio: (a) un "desafío MFA" con token propio de corta vida (p. ej. tabla/fila con hash, 5 min, intentos) y un
   `POST /api/sesiones/mfa` que lo canjea por la sesión; o (b) `sesion` con columna `mfa_pendiente` que `SesionService.validar` no acepta
   para rutas normales. (a) mantiene intacto `ValidadorSesion` y el contrato `SesionIniciadaDTO`; cambiará la respuesta de `POST /api/sesiones`
   (p. ej. 200/202 con `{mfaRequerido, desafio}` en vez de 201 con token) → afecta al frontend y a muchas IT.
2. **Admin sin MFA configurado (CA1):** necesita un paso de "enrolamiento obligatorio" antes de tener sesión completa → el desafío/sesión
   parcial debe permitir solo los endpoints de configuración. Definir qué ve el admin y que ningún `/api/admin/**` funcione sin MFA.
   Los admins creados por `CreadorAdmin` nacen sin MFA.
3. **Filtro de IPs del admin (§9.4):** decidir si se retira, se mantiene o se combina. Retirarlo cambia `FiltroIpsAdmin`,
   `IpsPermitidasAdmin`, `ConfiguracionSeguridad`, `application.properties`, `.env.example`, README y las IT de HU-06/09/16. Mantenerlo
   es lo menos riesgoso (defensa en profundidad); la historia dice "reemplaza (o complementa)".
4. **Recuperación de contraseña del admin** (`Usuario.motivoRechazoRecuperacion`): sigue bloqueada "hasta MFA". ¿Se habilita en HU-17? Si
   sí, la recuperación no debe saltarse el MFA. Probablemente fuera de alcance; dejarlo explícito.
5. **Límite de intentos MFA (§9.7 pendiente):** definir número y ventana (p. ej. 5 por desafío y además por usuario/15 min), qué pasa al
   superarlo (invalidar desafío, volver a pedir contraseña), y si cuenta para el contador `login|correo`. `LimitadorIntentos` es en memoria
   (se pierde al reiniciar, no escala a varias instancias) — aceptable como en HU-03, pero el contador "por sesión/desafío" podría ir en BD.
6. **Reutilización de código TOTP (replay):** RFC 6238 recomienda no aceptar el mismo paso dos veces → guardar último paso usado
   (`ultimo_paso` en `mfa`). Definir tolerancia de deriva (±1 paso de 30 s), 6 dígitos, HmacSHA1 (compatibilidad con apps).
7. **Almacenamiento:** secreto con `ConvertidorCifrado` (AES-GCM). Códigos de respaldo: 5 códigos, formato y entropía (p. ej. 10 chars
   base32), hash con bcrypt o HMAC (`HuellaCampos`); SHA-256 plano de un código corto es atacable offline. Regenerar/invalidar al
   desactivar o reconfigurar. `usado`→ preferir `usado_en` TIMESTAMPTZ como `token.usado_en`.
8. **Activación en dos fases:** generar secreto (pendiente) → confirmar con un código válido → activo + entregar códigos (CA3, mostrarlos
   una sola vez). Definir desactivación (¿pide contraseña y/o código? RNF-SEG-03 exige reautenticación para operaciones sensibles), y que
   el admin no puede desactivar MFA.
9. **Sesiones existentes al activar/desactivar MFA:** ¿se revocan las otras sesiones (`SesionRepository.revocarTodas`)? Decidir.
10. **Auditoría (R):** nuevas acciones p. ej. `MFA_ACTIVADO`, `MFA_DESACTIVADO`, `MFA_VERIFICADO`, `MFA_FALLIDO`, `MFA_BLOQUEADO`,
    `CODIGO_RESPALDO_USADO`; ≤60 chars; nunca el código ni el secreto en `detalle`.
11. **Sin dependencias TOTP/QR:** decidir implementación propia (JDK) vs. librería; QR en front sin lib → mostrar secreto + URI
    `otpauth://EspacioYa:<correo>?secret=...&issuer=EspacioYa`. El `toString` de DTOs con secreto/códigos debe enmascarar.
12. **Impacto en tests existentes:** todas las IT que inician sesión como ADMIN y quizá helpers compartidos; las IT que borran `usuario`
    necesitan cascada en las nuevas FK. Prever tiempo (tamaño M).
13. **Frontend:** no hay pantalla de perfil (HU-20 pendiente de otro dueño) → crear ruta propia (`/panel/seguridad`) sin pisar HU-20.
    `ProveedorSesion` y `IniciarSesion` cambian; `RutaProtegida` no distingue roles. Verificación solo por lint/build/UAT.
14. **Docs a actualizar en el PR:** `docs/modelo-datos.md` (tablas reales), `docs/requerimientos.md` §9.4 y §9.7, `docs/architecture.md`
    l.432, `docs/frontend.md` (nueva ruta), README (admin/IPs).
