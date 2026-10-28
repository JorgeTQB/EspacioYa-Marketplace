# HU-03 — Tasks: Inicio y cierre de sesión

> Orden de ejecución. El implementer marca `[x]` al completar cada una. Firmas, SQL,
> mensajes y plan de pruebas están en `design.md`. Cada test lleva
> `@DisplayName("HU-03 R<n>: …")`. Antes de empezar, confirmar que el dueño aceptó
> **P1–P7** (`requirements.md`) y las decisiones DT-1…DT-15 (`design.md` § 1), en especial
> DT-2 (Bearer + `sessionStorage`), DT-5 (bloqueo por correo) y DT-7 (admin excluido).
> Regla: las unitarias e IT de HU-01 y HU-02 no se modifican y deben seguir verdes en
> cada paso.

## Backend — `comun`

- [x] T1 — `comun/seguridad/TokenAleatorio` (`generar`: 32 bytes `SecureRandom` en base64url sin relleno; `hash`: SHA-256 hex minúsculas). Cubre: R3, R6. Test: `TokenAleatorioTest` (43 caracteres del alfabeto base64url; dos llamadas distintas; `hash` coincide con un SHA-256 conocido y mide 64).
- [x] T2 — `comun/limite/LimitadorIntentos` (M): métodos `superaLimite(clave, maximo, ventana)` (purga y consulta sin registrar), `registrar(clave)` y `limpiar(clave)`; `intentar` sin cambios. Cubre: R16, R17, R19, R20. Test: `LimitadorIntentosTest` (nuevos tests; los existentes intactos): 4 registros → no supera, 5 → supera; `superaLimite` no registra; tras avanzar la ventana deja de superar; `limpiar` reinicia.
- [x] T3 — `comun/error/`: `CredencialesInvalidasException`, `DemasiadosIntentosException`; `ErrorDTO` (+`MENSAJE_CREDENCIALES_INVALIDAS`, +`MENSAJE_SESION_INVALIDA`); `ManejadorGlobalErrores` (401 y 429). Cubre: R9, R16. Test: `ManejadorGlobalErroresTest` (nuevos casos 401 y 429 con su `ErrorDTO`).
- [x] T4 — `comun/seguridad/`: puerto `ValidadorSesion`, record `UsuarioAutenticado`, `FiltroAutenticacionSesion` (no `@Component`) y `PuntoEntradaNoAutenticado`. Cubre: R22, R26, R27, R28. Test: `FiltroAutenticacionSesionTest` (Mockito: Bearer válido → `SecurityContext` con el principal y `ROLE_<rol>`; sin encabezado, `Basic`, token desconocido → contexto vacío y la cadena continúa).
- [x] T5 — `comun/seguridad/ConfiguracionSeguridad` (M): `permitAll` de `POST /api/sesiones`, `STATELESS`, filtro con `addFilterBefore`, punto de entrada, CORS con `Authorization`, comentario de CSRF según DT-3. Cubre: R1, R22, R26, R27, R28, R36. Test: `SesionControllerIT` (T11); `UsuarioControllerIT`, `ArrendadorControllerIT` y `PoliticaPrivacidadControllerIT` siguen verdes.

## Backend — `cuentas`

- [x] T6 — Migración `V1_4__sesion.sql` (design § 9). Cubre: R2, R3, R29. Test: `EspacioYaApplicationIT` (Flyway + `ddl-auto=validate`).
- [x] T7 — `cuentas/domain/`: entidad `Sesion` (`iniciar`, `estaVigente`, `renovar`, `revocar`, `INACTIVIDAD = 30 min`), enum `MotivoRechazoInicioSesion`, `Usuario.motivoRechazoInicioSesion()` (orden: ADMIN, correo no verificado, no activa), `PerfilArrendador.AVISO_IDENTIDAD` y `ArrendadorController.AVISO_IDENTIDAD` apuntando a ella. Cubre: R2, R11, R12, R13, R24, R25, R27. Tests: `SesionTest` (expira a los 30 min; `renovar` mueve `expira_en`; `expira_en == ahora` → no vigente; revocada → no vigente), `UsuarioTest` (nuevos: un test por motivo y vacío para `USUARIO`/`ARRENDADOR` activos y verificados).
- [x] T8 — `cuentas/repository/SesionRepository.findByHashToken`. Cubre: R22, R26, R28. Test: `SesionControllerIT` (T11).
- [x] T9 — `cuentas/service/InicioSesionService.iniciar` (flujo design § 6: bloqueo antes de buscar, hash señuelo, motivo, registrar fallo, auditoría, sesión en transacción, limpiar contador, aviso). Cubre: R1, R2, R3, R4, R5, R8, R9, R10, R11, R12, R13, R14, R16, R17, R18, R20, R30, R31, R32, R34. Test: `InicioSesionServiceTest` (Mockito, `Clock` fijo, `TransactionTemplate` que ejecuta el callback, `LimitadorIntentos` real con el mismo reloj): un test por R4, R5, R9–R13 (motivo auditado y excepción), R14 (`matches` con el señuelo), R16 (bloqueado: sin `matches`, audita `LOGIN_BLOQUEADO`), R17 (correo inexistente registra fallo), R18 (429 no registra), R20, R30, R31, R34 (detalle solo `motivo=…`).
- [x] T10 — `cuentas/service/SesionService` (implementa `ValidadorSesion`: busca por hash, rechaza revocada o vencida sin renovar, renueva la vigente, carga el rol; `actual`; `cerrar` revoca solo la sesión del principal y audita `CIERRE_SESION`). Cubre: R22, R23, R24, R25, R26, R27, R29, R33. Test: `SesionServiceTest` (Mockito, `Clock` fijo): vigente → presente y `expira_en` renovado; revocada → vacío; vencida → vacío y `expira_en` igual; token desconocido → vacío; `cerrar` → `revocada` y auditoría.
- [x] T11 — `cuentas/dto/InicioSesionDTO` (Bean Validation, mensajes de design § 4, `toString` enmascarado), `SesionIniciadaDTO`, `SesionActualDTO` y `cuentas/controller/SesionController` (`POST /api/sesiones` 201, `GET /api/sesiones/actual`, `DELETE /api/sesiones/actual` 204, IP con `IpCliente`). Cubre: R1–R36. Tests:
  - `InicioSesionDTOTest`: R35.
  - `SesionControllerIT` (Testcontainers; `RelojControlable` `@Primary`; `@BeforeEach` borra `sesion`, `auditoria`, `perfil_arrendador`, `usuario`; IP distinta por test en `10.3.x.x`): R1, R2, R3, R4, R5, R6, R7, R8, R9–R13 (cuerpo idéntico y sin filas en `sesion`), R15, R16, R17, R19, R20, R21, R22, R23, R24, R25, R26, R27, R28, R29, R30, R31, R32, R33, R34, R36 (casos en design § 13).

## Frontend

- [x] T12 — `src/api/tokenSesion.js` (N) y `src/api/cliente.js` (M): encabezado `Authorization: Bearer` si hay token; 401 con token → `borrarToken()` y aviso a `alPerderSesion`; 204 sin cuerpo. `src/api/cuentas.js` (M): `iniciarSesion`, `obtenerSesionActual`, `cerrarSesion`. Cubre: pantallas Iniciar sesión y Panel (CA1, CA4).
- [x] T13 — `src/sesion/ContextoSesion.js`, `ProveedorSesion.jsx`, `useSesion.js`, `RutaProtegida.jsx` (N) y `main.jsx` (M) con `<ProveedorSesion>` dentro de `BrowserRouter` (design § 12). Cubre: CA4 (sesión perdida → `/iniciar-sesion` con mensaje), CA1 (restaurar sesión al recargar).
- [x] T14 — `pages/cuentas/IniciarSesion.jsx` (+ `.module.css`) en `/iniciar-sesion`. Cubre: CA1 (navega a `/panel`), CA2 (`Alerta` error con el mensaje genérico), CA3 (`Alerta` error con el 429), CA4 (`Alerta` info con `state.mensaje`), errores por campo, error de red.
- [x] T15 — `pages/cuentas/Panel.jsx` (+ `.module.css`) en `/panel` dentro de `RutaProtegida`. Cubre: CA1 (título según rol), aviso de identidad de HU-02 (`Alerta` atención).
- [x] T16 — `App.jsx` (M): rutas `/iniciar-sesion` y `/panel`; encabezado con "Iniciar sesión" o "Mi panel" + `Boton` secundario "Cerrar sesión"; enlace "Iniciar sesión" en `/`. Cubre: CA4 (cerrar sesión → `Alerta` éxito "Cerraste sesión." en `/iniciar-sesion`).

## Docs y cierre

- [x] T17 — `docs/modelo-datos.md`: `sesion` con `hash_token (único)`. `docs/frontend.md`: carpeta `src/sesion/` en la estructura y "Cómo viaja la sesión" según DT-2 (Bearer en encabezado, token en `sessionStorage`). Cubre: R3 (DT-15).
- [x] T18 — Documentar en `progress/impl_HU-03.md` el mapa `R<n>` → test (R1–R36, todos con al menos un test) y la nota de despliegue: UAT con HTTPS (DT-11) y una sola instancia del backend (contador en memoria, DT-6).
- [x] T19 — Verificación: `./init.sh` (sin `--rapido`) en verde: `./mvnw verify` con todas las pruebas (incluidas las de HU-01 y HU-02 sin cambios), `npm run lint -- --max-warnings=0` y `npm run build` sin errores, y los chequeos de arquitectura (sin `@Autowired` en `src/main`, sin colores fuera de `tokens.css`, controladores sin repositorios, sin secretos trackeados, sin `System.out`).
