# HU-04 — Tasks: Recuperación de contraseña

> Orden de ejecución. El implementer marca `[x]` al completar cada una. Firmas, SQL, mensajes
> y plan de pruebas están en `design.md`. Cada test lleva `@DisplayName("HU-04 R<n>: …")`.
> Antes de empezar, confirmar que el dueño aceptó **S1–S12** (`requirements.md`); D1–D4 ya son
> decisiones del dueño.
> Regla: las pruebas de HU-01, HU-02, HU-03 y HU-05 siguen verdes en cada paso; solo se tocan
> los dobles de prueba que T5 indica.

## Backend

- [x] T1 — `comun/seguridad/TokenAleatorio` (M): `tieneFormato(String)` (no nulo y `^[A-Za-z0-9_-]{43}$`). Cubre: R17. Test: `TokenAleatorioTest` (nuevos casos: un `generar()` cumple; nulo, vacío, 42/44 caracteres, `+`, `/` y `=` no cumplen).
- [x] T2 — Migración `V1_5__token.sql` (design § 7) y `spring.flyway.out-of-order=true` en `application.properties` con un comentario del porqué (S1). Cubre: R2, R3. Test: `EspacioYaApplicationIT` (Flyway + `ddl-auto=validate`).
- [x] T3 — `cuentas/domain/`: entidad `Token` (`recuperacion`, `fueUsado`, `expiro`, `VIGENCIA_RECUPERACION = 30 min`), enums `TipoToken` y `MotivoRechazoRecuperacion`, `Usuario.cambiarContrasena(nuevoHash)`. Cubre: R2, R16, R18. Tests: `TokenTest` (expira a los 30 min; `expira_en == ahora` → expiró; usado), `UsuarioTest` (nuevo: `cambiarContrasena`).
- [x] T4 — `cuentas/repository/TokenRepository` (`findByHashTokenAndTipo`, `marcarUsado`, `anularPendientes`) y `SesionRepository.revocarTodas` (M). Cubre: R6, R16, R19, R20, R21, R22. Test: `RecuperacionContrasenaControllerIT` (T8).
- [x] T5 — `NotificadorCorreo.notificarRecuperacion(correo, enlace)` (sin `default`) y `NotificadorCorreoSimulado` (M) con el constructor `boolean registrarEnlace` (design § 6); propiedad `espacioya.correo.simulado.registrar-enlace=${CORREO_SIMULADO_REGISTRAR_ENLACE:false}` en `application.properties` y `CORREO_SIMULADO_REGISTRAR_ENLACE=false` en `2_Backend/.env.example` con el aviso "solo desarrollo; nunca `true` en UAT". Actualizar dobles: `NotificadorFalso` de `UsuarioControllerIT` y `ArrendadorControllerIT` implementan el método nuevo (sin cambiar sus pruebas); en `NotificadorCorreoSimuladoTest`, `new NotificadorCorreoSimulado(false)` y el test de HU-01 R4 admite solo parámetros `boolean` y campos `org.slf4j.*` o `boolean`. Cubre: R5, R13, R14. Test: `NotificadorCorreoSimuladoTest` (nuevos: apagada → línea fija sin enlace, token ni correo; encendida → contiene el enlace y no el correo; los de HU-01 siguen verdes).
- [x] T6 — `InicioSesionService` (M): `PREFIJO_CLAVE` con visibilidad de paquete, sin otro cambio. Cubre: R24. Test: `InicioSesionServiceTest` existente sigue verde.
- [x] T7 — `cuentas/service/RecuperacionContrasenaService` (flujos de design § 2: límite antes de buscar la cuenta; anular pendientes, guardar, notificar y auditar en una transacción; comprobación forma → hash → token → usado → expirado → cuenta habilitada; bcrypt fuera de la transacción; `marcarUsado` = 0 → rechazo auditado fuera de la transacción; `limpiar` del contador de login). Cubre: R1–R11, R15–R30. Test: `RecuperacionContrasenaServiceTest` (Mockito, `Clock` fijo, `TransactionTemplate` que ejecuta el callback, `LimitadorIntentos` real con el mismo reloj): R7, R8 y R9 sin `save` ni notificador; R10/R11 (`intentar` antes de `findByCorreo`); R17 (forma inválida → sin llamadas a `TokenRepository`); R22 (`marcarUsado` = 0 → 400 sin `cambiarContrasena` ni `revocarTodas`, audita `TOKEN_USADO`); R23 (sin `marcarUsado`); R24 (`limpiar("login|" + correo)`); R26–R30 (acción, entidad, motivo y detalle sin token, hash ni correo).
- [x] T8 — `cuentas/dto/` (`SolicitudRecuperacionDTO`, `TokenRecuperacionDTO`, `RestablecimientoContrasenaDTO` con `toString` enmascarado), `cuentas/controller/RecuperacionContrasenaController` (`POST /api/recuperaciones` 202, `.../validaciones` 204, `.../confirmaciones` 200; IP con `IpCliente`) y `ConfiguracionSeguridad` (M) con los tres `permitAll`. Cubre: R1–R31. Tests:
  - `RecuperacionDTOTest`: R31.
  - `RecuperacionContrasenaControllerIT` (design § 10): R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R15, R16, R18, R19, R20, R21, R23, R24, R25, R26, R27, R28, R29, R30.

## Frontend

- [x] T9 — `src/api/cuentas.js` (M): `solicitarRecuperacion`, `validarEnlaceRecuperacion`, `restablecerContrasena` con `autenticada: false`; `textosCuentas.js` (M): `CONTRASENAS_NO_COINCIDEN`. Cubre: pantallas Recuperar y Restablecer contraseña (CA1, CA2, CA3).
- [x] T10 — `pages/cuentas/RecuperarContrasena.jsx` (+ `.module.css`) en `/recuperar-contrasena`. Cubre: CA1 (`Alerta` éxito con el mensaje de la API, igual exista o no el correo), error por campo y de red.
- [x] T11 — `pages/cuentas/RestablecerContrasena.jsx` (+ `.module.css`) en `/restablecer-contrasena`: token desde el fragmento y URL limpia, validación al abrir, formulario con confirmación en el cliente. Cubre: CA2 (`Alerta` error + "Solicitar un enlace nuevo"), CA3/D4 (`borrarToken()`, `salir()` y `/iniciar-sesion` con aviso de éxito), requisitos bajo el campo.
- [x] T12 — `IniciarSesion.jsx` (M): enlace "¿Olvidaste tu contraseña?" → `/recuperar-contrasena`; `App.jsx` (M): las dos rutas públicas. Cubre: CA1 (acceso a la recuperación), D4 (aviso tras restablecer).

## Docs y cierre

- [x] T13 — `docs/modelo-datos.md`: `token` con `creado_en` y `hash_token (único, SHA-256 hex; el token nunca se guarda en claro)`; "Restricciones clave": `token.hash_token` único. Cubre: R3 (S3).
- [x] T14 — `progress/impl_HU-04.md`: mapa `R<n>` → test (R1–R31, todos con al menos un test) y notas para el equipo: `spring.flyway.out-of-order=true` (S1, avisar a HU-06), propiedad de desarrollo apagada en UAT (D1) y una sola instancia del backend (S11).
- [x] T15 — Verificación: `./init.sh` (sin `--rapido`) en verde: `./mvnw verify` con todas las pruebas (incluidas las de HU-01, HU-02, HU-03 y HU-05), `npm run lint -- --max-warnings=0` y `npm run build` sin errores, y los chequeos de arquitectura (sin `@Autowired` en `src/main`, sin colores fuera de `tokens.css`, controladores sin repositorios, sin secretos trackeados, sin `System.out`).
