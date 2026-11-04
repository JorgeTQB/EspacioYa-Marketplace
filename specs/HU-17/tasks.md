# HU-17 — Tasks: Autenticación multifactor (MFA)

> Orden de ejecución. El implementer marca `[x]` al completar cada una. Firmas, SQL, mensajes, flujos y plan de pruebas
> están en `design.md`. Cada test lleva `@DisplayName("HU-17 R<n>: …")`. Antes de empezar, confirmar que el dueño
> aceptó **P1–P12** y respondió las preguntas abiertas de `requirements.md`. Regla: salvo los casos listados en
> `requirements.md` "Reemplazos y compatibilidad" y `design.md` § 11, las pruebas de HU-01…HU-16 no cambian de
> resultado y siguen verdes en cada paso. IP de las IT nuevas: `10.17.x.x`.

## Backend — `comun`

- [x] T1 — `comun/error/`: `VerificacionExpiradaException` (N), `ErrorDTO.MENSAJE_VERIFICACION_EXPIRADA` (M) y su
  handler 401 en `ManejadorGlobalErrores` (M). Cubre: R18, R21. Test: `ManejadorGlobalErroresTest` (nuevo caso 401 con
  el `ErrorDTO` exacto).
- [x] T2 — `comun/seguridad/ConfiguracionSeguridad` (M): `permitAll` de `POST /api/sesiones/mfa` y
  `POST /api/sesiones/mfa/configuracion`; `/api/mfa/**` queda en `authenticated()`. `IpsPermitidasAdmin` y
  `FiltroIpsAdmin` (M): solo comentario/javadoc "complementa a MFA (HU-17, P3)", sin cambio de código. Cubre: R9, R10,
  R23, R44. Test: `MfaInicioSesionIT`, `MfaControllerIT` (T14, T15); `FiltroIpsAdminIT` sigue verde.

## Backend — `cuentas` dominio y datos

- [x] T3 — `cuentas/domain/Base32` y `cuentas/domain/Totp` (N, `public final`, puras; design § 5 y § 7). Cubre: R14,
  R15, R51, R52, R53. Tests: `Base32Test` (vectores RFC 4648 § 10, sin relleno, ida y vuelta, `esBase32` rechaza `0`,
  `1`, `8`, `=` y vacío; `decodificar` inválido → `IllegalArgumentException`), `TotpTest` (los 6 vectores de RFC 6238
  Apéndice B en 6 dígitos con `GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ`; `pasoDe`; acepta paso −1, 0, +1 y rechaza ±2;
  rechaza paso ≤ `ultimoPaso`; texto que no son 6 dígitos → vacío; ignora espacios).
- [x] T4 — Migración `V5_1__mfa.sql` (design § 9; si `develop` ya tiene `V5_1` al unir, renombrar a la siguiente
  libre). Cubre: R5, R6, R37, R50. Test: `EspacioYaApplicationIT` (Flyway + `ddl-auto=validate`).
- [x] T5 — Entidades `Mfa`, `CodigoRespaldo`, `DesafioMfa` y enums `PasoMfa`, `FactorMfa`, `ContextoMfa` (N);
  `Usuario` (M: solo el comentario de `motivoRechazoRecuperacion`, DT-14). Cubre: R5, R13, R16, R20, R21, R25, R31,
  R50. Tests: `MfaTest` (`pendiente` inactivo; `reemplazarSecreto` limpia `ultimoPaso` y falla si activo; `activar` fija
  `activadoEn` y falla dos veces; `registrarPaso` rechaza paso ≤ último; `secretoEnBytes`), `CodigoRespaldoTest`
  (`usar` dos veces falla), `DesafioMfaTest` (`crear` +5 min; `estaAbierto` en `expira_en` exacto → falso; 4 fallos
  abierto, 5.º cierra y devuelve `true`; `cerrar` dos veces falla).
- [x] T6 — Repositorios `MfaRepository`, `CodigoRespaldoRepository`, `DesafioMfaRepository` (N) y
  `SesionRepository.revocarOtras` (M) (design § 5). Cubre: R5, R16, R35, R39, R40. Test: `MfaInicioSesionIT`,
  `MfaControllerIT` (T14, T15).

## Backend — `cuentas` servicios

- [x] T7 — `cuentas/service/EmisorSesiones` (N, `MANDATORY`) e `InicioSesionService` (M: usa el emisor; paso `CODIGO`
  / `CONFIGURACION` / sesión directa; desafío + `LOGIN_MFA_PENDIENTE`; devuelve `RespuestaInicioSesion`; comentario de
  IPs según DT-13). Cubre: R1–R9, R12, R59, R60. Tests: `EmisorSesionesTest` (guarda la sesión con hash, audita
  `LOGIN_EXITOSO` con el detalle recibido, aviso del arrendador); `InicioSesionServiceTest` (M: constructor nuevo; los
  19 casos de HU-03/HU-06 siguen; nuevos: R1, R2 con pendiente, R3 con pendiente, R4, R5, R6, R7, R8, R9, R59; el caso
  de admin desde IP permitida pasa a esperar `DesafioMfaDTO` `CONFIGURACION`).
- [x] T8 — `cuentas/service/GeneradorMfa` (N). Cubre: R24, R36, R37, R54, R55. Test: `GeneradorMfaTest`
  (`nuevoSecreto` 32 caracteres Base32 y dos llamadas distintas; `configuracion` arma la `uri` exacta de R55 con un
  correo con `+`; `reemplazarCodigos` borra los anteriores, guarda 5 `CodigoRespaldo` con la huella del normalizado y
  devuelve 5 distintos con `^[A-Z2-7]{5}-[A-Z2-7]{5}$`; `normalizar`).
- [x] T9 — `cuentas/service/VerificadorMfa` (N). Cubre: R14–R17, R45–R49, R63–R65. Test: `VerificadorMfaTest`
  (`LimitadorIntentos` real con `Clock` fijo; TOTP aceptado registra el paso; respaldo normalizado aceptado, marcado y
  auditado con `restantes`; respaldo con `admiteRespaldo = false` → vacío; usado → vacío; 9 fallos no bloquean, 10
  sí y auditan `MFA_BLOQUEADO`; tras 15 min no bloquea; `limpiarFallos`; `registrarFallo` audita
  `MFA_CODIGO_FALLIDO contexto=…`; ningún detalle contiene el código).
- [x] T10 — `cuentas/service/VerificacionMfaService` (N: `configurar` y `verificar` con `TransactionTemplate` que
  devuelve el resultado y lanza fuera; orden de bloqueos `desafio → usuario → mfa`; orden de DT-12 para `revocarTodas`).
  Cubre: R10–R13, R18–R21, R23–R27, R35, R36, R46, R48, R60, R61. Test: `VerificacionMfaServiceTest` (Mockito +
  `Clock` fijo: un test por R10, R11, R13, R18 (cada causa de no vigente), R19, R20, R21, R23, R24, R25, R26, R27, R35,
  R46 (sin sumar al desafío), R48, R60, R61; el fallo se guarda aunque la respuesta sea error).
- [x] T11 — `cuentas/service/MfaService` (N: `estado`, `iniciarConfiguracion`, `activar`, `desactivar`; bloqueos
  `usuario → mfa`; en `desactivar` contraseña y código se evalúan siempre los dos). Cubre: R24, R29–R36, R39–R43,
  R45, R46, R48, R61, R62. Test: `MfaServiceTest` (un test por R29, R30, R31, R32, R33 (sin contar), R34, R35
  (`revocarOtras` con la sesión actual), R39 (admin → `desactivable = false`), R40, R41 (contraseña mala y código
  bueno; contraseña buena y código malo: mismo mensaje), R42 (sin llamar a `encoder`), R43, R46, R62).
- [x] T12 — `cuentas/service/SesionService` (M: `validar` exige MFA activo al admin, DT-3). Cubre: R28. Test:
  `SesionServiceTest` (M: constructor; admin sin MFA → vacío sin renovar; admin con MFA → presente; usuario final sin
  MFA → presente; los 7 casos de HU-03 siguen).

## Backend — `cuentas` API

- [x] T13 — DTO de design § 5 (N) con Bean Validation, mensajes de § 4 y `toString` enmascarado;
  `SesionIniciadaDTO` (M: `implements RespuestaInicioSesion`). Cubre: R22, R57. Tests: `VerificacionMfaDTOTest`
  (vacíos y largos con su mensaje), `DtoMfaTest` (`toString` de los 9 DTO nuevos no contiene desafío, código,
  contraseña, secreto, URI, token ni códigos).
- [x] T14 — `SesionController` (M: `POST /api/sesiones` → `ResponseEntity<?>` 201/202; `POST /api/sesiones/mfa` 201 y
  `POST /api/sesiones/mfa/configuracion` 200, ambos con `CacheControl.noStore()` e `IpCliente`). Cubre: R1–R28, R46,
  R58–R61, R63–R66. Test: `MfaInicioSesionIT` (N, Testcontainers, `RelojControlable`, IP `10.17.x.x`, limpieza de
  design § 13; casos de § 13 para R1–R28, R45–R49, R58–R61, R63–R66, incluido el flujo admin completo de CA1/CA3 y el
  de usuario con TOTP y con respaldo de CA2).
- [x] T15 — `MfaController` (N, `/api/mfa`: `GET`, `POST /configuracion`, `POST /activacion` 200,
  `POST /desactivacion` 204; `noStore` en las respuestas con cuerpo). Cubre: R29–R45, R50, R54–R56, R58, R61, R62,
  R64, R66. Test: `MfaControllerIT` (N, mismas reglas que T14; casos de design § 13 para R29–R45, R50, R54–R56, R58,
  R62, R66, y que los fallos de activación cuentan para el límite del login).

## Backend — pruebas existentes

- [x] T16 — Soporte `src/test/java/pe/edu/ulima/espacioya/MfaDePrueba.java` (N, design § 11) y adaptación de las IT
  que inician sesión como admin (`SesionControllerIT` casos HU-06 R1–R4, `CuentaAdminControllerIT`,
  `RevisionIdentidadControllerIT`, `FiltroIpsAdminIT`, `ReembolsosSoporte` y lo que encuentre el `grep` de § 11): el
  admin se crea con `MfaDePrueba.activar` y entra con `MfaDePrueba.iniciarSesion`. `SesionControllerIT` HU-06 R1
  espera `202` + segundo paso (`@DisplayName` cita también HU-17 R2, R26). Cubre: R2, R9, R10, R28 (compatibilidad
  HU-06, HU-09, HU-16). Test: las IT citadas, con los mismos asserts de antes.

## Frontend

- [x] T17 — `src/api/cuentas.js` (M): `verificarMfa`, `configurarMfaEnInicio` (ambas `autenticada: false`),
  `obtenerMfa`, `iniciarConfiguracionMfa`, `activarMfa`, `desactivarMfa`. Textos de las pantallas en
  `pages/cuentas/textosCuentas.js` (M). Cubre: pantallas Iniciar sesión y Seguridad de la cuenta (CA1, CA2, CA3).
- [x] T18 — `pages/cuentas/PasoCodigoMfa.jsx`, `ConfiguracionMfa.jsx` y `CodigosRespaldo.jsx` (N, con su
  `.module.css`, sin componentes nuevos en el kit ni colores fuera de tokens). Cubre: CA1 (configuración obligatoria),
  CA2 (código al iniciar sesión), CA3 (5 códigos mostrados una vez).
- [x] T19 — `pages/cuentas/IniciarSesion.jsx` (M): pasos `credenciales → codigo | configuracion → codigos` con el
  desafío solo en el estado de React; 401 vuelve a credenciales con el mensaje; 400 bajo el `Campo`; 429 en el mismo
  paso; `entrar` solo tras "Ya los guardé, continuar" en el paso códigos (design § 12). Cubre: CA1, CA2, CA3 visibles.
- [x] T20 — `pages/cuentas/SeguridadCuenta.jsx` (+ `.module.css`) en `/panel/seguridad` con `RutaProtegida`
  (`App.jsx` M) y enlace "Seguridad de la cuenta" en `Panel.jsx` (M) para los tres roles: estados inactivo / activo /
  admin obligatorio, activación con códigos y desactivación en `Modal peligro-confirmar` (design § 12). Cubre: CA2,
  CA3 y desactivación visibles.

## Docs y cierre

- [x] T21 — Docs del mismo PR (design § 11): `docs/modelo-datos.md` (`mfa`, `codigo_respaldo` con `usado_en`,
  `desafio_mfa`, restricciones), `docs/requerimientos.md` §9.4 y §9.7 resueltos (DT-13, DT-8), `docs/architecture.md`
  (Seguridad transversal: IPs + MFA; `SesionService.validar` exige MFA al admin), `docs/frontend.md` (`/panel/seguridad`
  y pasos MFA del login), `README.md` (primer login del admin, procedimiento ante pérdida según la respuesta a la
  pregunta abierta 1, `ADMIN_IPS_PERMITIDAS` complementa a MFA), `2_Backend/.env.example` y el comentario de
  `application.properties` l.48. Cubre: R9 (P3), R28 (DT-3).
- [x] T22 — `progress/impl_HU-17.md` con el mapa `R<n>` → test (R1–R66, todos con al menos un test) y la nota de
  despliegue: una sola instancia del backend (límite por usuario en memoria, DT-8) y reloj del servidor sincronizado
  (TOTP ±30 s).
- [x] T23 — Verificación: `./init.sh` (sin `--rapido`) en verde: `./2_Backend/mvnw -f 2_Backend/pom.xml verify` con
  todas las pruebas (incluidas las de HU-01…HU-16), `npm --prefix 3_frontend run lint -- --max-warnings=0` y
  `npm --prefix 3_frontend run build` sin errores, y los chequeos de arquitectura (sin `@Autowired` en `src/main`, sin
  colores fuera de `tokens.css`, controladores sin repositorios, sin secretos trackeados, sin `System.out`).
