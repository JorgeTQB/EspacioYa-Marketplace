# HU-01 — Tasks: Registro de usuario con consentimiento

> Orden de ejecución. El implementer marca `[x]` al completar cada una. Firmas, SQL,
> mensajes y plan de pruebas están en `design.md`. Cada test lleva
> `@DisplayName("HU-01 R<n>: …")`. Antes de empezar, confirmar que el dueño aceptó los
> supuestos S1–S4 de `requirements.md` (S5 ya es decisión del dueño).

## Backend — base y `comun`

- [x] T1 — `application.properties`: propiedades de `design.md` §9 (sin `spring.mail.*`). `.env.example`: `FRONTEND_URL` sin valor. `pom.xml` sin cambios. Cubre: R7, R26. Test: `EspacioYaApplicationIT` sigue verde.
- [x] T2 — Migraciones `V1_1__usuarios.sql` y `V1_2__auditoria.sql` (design §8). Cubre: R1, R5, R9, R18. Test: `EspacioYaApplicationIT` (Flyway + `ddl-auto=validate`).
- [x] T3 — `comun/configuracion/ConfiguracionReloj` (bean `Clock` UTC) y `comun/web/IpCliente`. Cubre: R8, R9, R23. Test: indirecto en T6, T9.
- [x] T4 — `comun/error/`: `ReglaNegocioException`, `ErrorDTO`, `ErrorCampoDTO`, `ManejadorGlobalErrores` (tabla design §10). Cubre: R13, R24, R25. Test: `ManejadorGlobalErroresTest`.
- [x] T5 — `comun/auditoria/`: `Auditoria`, `AuditoriaRepository`, `EventoAuditoria`, `AuditoriaService` (fecha desde `Clock`, `@Transactional`). Cubre: R9, R16. Test: `UsuarioControllerIT` (T11).
- [x] T6 — `comun/limite/`: `ReglaLimite`, `LimitadorIntentos` (ventana deslizante, no registra al exceder), `FiltroLimiteIntentos` (429 con `ErrorDTO`). Cubre: R21, R22, R23. Test: `LimitadorIntentosTest` (5 aceptados, 6.º rechazado, el rechazado no cuenta, al avanzar 60 min se acepta; claves independientes).
- [x] T7 — `comun/seguridad/ConfiguracionSeguridad`: `SecurityFilterChain` (csrf, httpBasic y formLogin off; CORS con `espacioya.frontend.url`; `permitAll` de los 2 endpoints; resto `authenticated`) y `PasswordEncoder` `BCryptPasswordEncoder(12)`. Cubre: R19. Test: `UsuarioControllerIT` (T11).

## Backend — `cuentas`

- [x] T8 — `cuentas/domain/`: `Rol`, `EstadoUsuario`, `Usuario` (`registrar` con `correoVerificado = true`, `normalizarCorreo`), `PoliticaContrasena`. Cubre: R1, R5, R10, R11, R12, R20. Tests: `PoliticaContrasenaTest` (7/8/64/65 caracteres, >72 bytes, sin letra, sin dígito, igual al correo con otro caso, válida), `UsuarioTest` (`registrar` fija USUARIO/ACTIVA/verificado; `normalizarCorreo`).
- [x] T9 — `cuentas/repository/UsuarioRepository.findByCorreo`. Cubre: R14. Test: `UsuarioControllerIT` (T11).
- [x] T10 — `cuentas/service/`: `NotificadorCorreo` (interfaz), `NotificadorCorreoSimulado` (solo `log.info` SLF4J, sin el correo; nada de `System.out` ni `JavaMailSender`), `PoliticaPrivacidad`, `RegistroService.registrar` (flujo design §5, incluido el reintento por `DataIntegrityViolationException`). Cubre: R1, R3, R4, R7, R8, R9, R10–R12, R14–R18, R25. Tests: `NotificadorCorreoSimuladoTest` (R4, `OutputCaptureExtension`); `RegistroServiceTest` (Mockito, `Clock` fijo, `TransactionTemplate` que ejecuta el callback): un test por R3 (notificador llamado una vez con el correo normalizado), R7, R8, R9, R14/R15 (sin `save` y sin notificar), R16, R17 (no consulta el repositorio si la contraseña o la versión fallan), R18 (`DataIntegrityViolationException` → camino existente), R25 (excepción del notificador se propaga).
- [x] T11 — `cuentas/dto/` (`RegistroUsuarioDTO`, `MensajeDTO`, `VersionPoliticaDTO`) y `cuentas/controller/` (`UsuarioController`, `PoliticaPrivacidadController`, `ReglasLimiteCuentas`). Cubre: R1, R2, R6, R13, R20, R21, R26. Tests:
  - `UsuarioControllerIT` (Testcontainers, `NotificadorCorreo` falso `@Primary` y `Clock` controlable en una `@TestConfiguration`; IP distinta por test): R1, R2, R3 (una llamada capturada), R5, R6, R7, R8/R9, R10–R13, R14 (cuerpo y código idénticos a un registro nuevo), R15, R16, R17, R18 (dos hilos simultáneos), R19, R20, R21, R22, R25 (notificador falla → 500 y BD vacía).
  - `PoliticaPrivacidadControllerIT`: R26.

## Frontend

- [x] T12 — Agregar `react-router-dom` (`npm install`), `BrowserRouter` en `main.jsx` y rutas `/`, `/registro`, `/privacidad` en `App.jsx` (design §11). Cubre: pantallas Registro, Privacidad, Inicio.
- [x] T13 — Kit `src/components/ui/`: `Boton`, `Campo` (incluye `tipo="checkbox"`), `Alerta`, cada uno con su `.module.css` solo con tokens. Cubre: CA1, CA2, CA3 visibles.
- [x] T14 — `src/api/cliente.js` (`peticion`, `ErrorDTO`, error de red) y `src/api/cuentas.js` (`obtenerVersionPrivacidad`, `registrarUsuario`). Cubre: pantallas Registro y Privacidad.
- [x] T15 — `pages/cuentas/Registro.jsx` (+ `.module.css`). Cubre: pantalla Registro — CA1 (alerta de éxito "Te enviamos un correo de verificación…", envío simulado S5), CA2 (casilla obligatoria y error bajo la casilla), CA3 (requisitos bajo contraseña; correo existente se ve igual que CA1), 429/500/red.
- [x] T16 — `pages/cuentas/Privacidad.jsx` con la versión de la API y el texto provisto por el equipo (S3). Cubre: pantalla Privacidad (CA2, política aceptada).

## Cierre

- [x] T17 — Documentar en `progress/impl_HU-01.md` el mapa `R<n>` → test (R1–R26, todos con al menos un test).
- [x] T18 — Verificación: `./init.sh` (sin `--rapido`) en verde: `./mvnw verify` con todos los tests, `npm run lint -- --max-warnings=0` y `npm run build` sin errores, y los chequeos de C3 (sin `@Autowired`, sin colores fuera de `tokens.css`, controladores sin repositorios, sin secretos, sin `System.out`).
