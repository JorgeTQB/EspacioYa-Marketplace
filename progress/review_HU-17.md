# Review — HU-17

**Veredicto:** CHANGES_REQUESTED

> Revisor: reviewer · 2026-11-03 · rama `feature/HU-17-autenticacion-multifactor` (sin commitear sobre `origin/develop` @ 5aa045f).
> `./init.sh` completo (sin `--rapido`): **exit 0**, "Entorno listo" — `mvnw verify` 1189 unitarias + 732 IT, 0 fallos/errores/omitidas;
> ESLint 0 avisos; build OK; chequeos de arquitectura, colores, librerías y secretos `[OK]`. Único `[WARN]`: llaves de cifrado locales (ajeno).
>
> La implementación es correcta y de buena calidad; el **único bloqueo** es de verificación: las reglas de concurrencia que el propio
> design declara (DT-11) no tienen prueba con dos operaciones a la vez (`docs/verification.md` l.55-57, `CHECKPOINTS.md` C4).

## Hallazgos

| # | Severidad | Dónde | Hallazgo |
|---|-----------|-------|----------|
| H1 | **Media (bloquea)** | `specs/HU-17/design.md` l.25 (DT-11); `VerificacionMfaService.java:142-146`; `MfaService.java:79-91,120-124,145-149`; sin test en `2_Backend/src/test/java/.../cuentas/` | DT-11 declara reglas de concurrencia: "el bloqueo del desafío serializa dos envíos del mismo código (uno solo crea sesión)" y "el de `usuario` serializa dos configuraciones a la vez (sin él, dos `INSERT` en `mfa` chocarían en la PK)". Las garantías de **un solo uso** del desafío (R13), **sin replay** de paso TOTP (R14/R15) y **respaldo de un solo uso** (R16) dependen bajo carrera de esos `PESSIMISTIC_WRITE`. Ningún test de HU-17 lanza dos solicitudes a la vez (no hay `CountDownLatch`/`Executor` en `MfaInicioSesionIT` ni `MfaControllerIT`). `docs/verification.md` l.55-57 y `CHECKPOINTS.md` C4 lo exigen, y hay precedente directo en `RecuperacionContrasenaControllerIT.java:651` (HU-04 R22, token de un solo uso). El código parece correcto (orden de bloqueos `desafio → usuario → mfa`, consultas después del bloqueo), pero no está verificado. |
| H2 | Baja | `progress/impl_HU-17.md` l.131 | El mapa dice `R57 → MfaInicioSesionIT.auditoriaSinSecretos`, pero ese test (`MfaInicioSesionIT.java:757`) cita solo `HU-17 R66`. R57 sí está cubierto de verdad por `DtoMfaTest` (9 DTO); corregir la línea del mapa. |
| H3 | Informativa | `3_frontend/src/pages/cuentas/IniciarSesion.jsx` (`activarYEntrar`) | En el paso `codigos` el token ya está guardado (`guardarToken`) pero aún no se llamó `entrar`: si el admin recarga, `ProveedorSesion` restaura la sesión y no vuelve a ver sus 5 códigos. Es lo que pide design § 12 ("201 → `guardarToken` y pasa a códigos"); queda anotado para la caja negra de UAT, no se pide cambio. |
| H4 | Informativa | `MfaService.java:156-162` | En la desactivación con contraseña incorrecta y TOTP válido, el paso TOTP queda consumido (`ultimo_paso`), igual que el respaldo (design § 6 lo pide para el respaldo). Coherente con R15; sin cambio. |
| H5 | Informativa | `VerificadorMfa.java:75` | El respaldo se busca por igualdad de HMAC en BD (no tiempo constante). Aceptable: sin la llave de `HuellaCampos` el tiempo no filtra nada útil (DT-7). TOTP sí usa `MessageDigest.isEqual` (`Totp.java:70`). |

### Seguridad revisada (sin hallazgos)

- Secreto TOTP cifrado en reposo: `Mfa.java:31-33` `@Convert(ConvertidorCifrado)`; `MfaControllerIT.configurarGuardaSecretoCifrado` comprueba `v1:`, que no contiene el Base32 y que descifra al mismo valor.
- Códigos de respaldo: 5, 10 car. Base32 de `SecureRandom` (`GeneradorMfa.java:58-89`), guardados como `HuellaCampos` (HMAC-SHA256) y `usado_en` al usarse (`VerificadorMfa.java:75-83`); la IT verifica huellas, uso único y `restantes`.
- Comparación TOTP en tiempo constante (`Totp.java:70`), ±1 paso, rechazo de paso ≤ `ultimo_paso` (`Totp.java:66`, `Mfa.registrarPaso`).
- Límites: 5 por desafío persistido (`DesafioMfa.registrarFallo`, CHECK 0–5 en BD) y 10/15 min por cuenta compartido por login/activación/desactivación (`VerificadorMfa.bloqueado`, consultado antes de comprobar). Fallos persistidos fuera del rollback (TransactionTemplate + excepción fuera; probado en `VerificacionMfaServiceTest.falloSeGuardaAunqueResponda400`).
- Desafío: 32 bytes `TokenAleatorio`, solo SHA-256 en BD, 5 min, `cerrado_en` al canjear/agotar, vigencia por endpoint y por estado de cuenta/MFA (`VerificacionMfaService.Bloqueo.vigente`). No es sesión: no pasa por `Authorization`.
- `toString` enmascarado en los 9 DTO nuevos; auditorías sin desafío/código/secreto/URI/token/respaldos (dos IT de R66).
- `Cache-Control: no-store` en los 200/201 nuevos y en el 202 (D-E).
- `SesionService.validar` (`SesionService.java:57`) rechaza la sesión de un admin sin MFA activo, sin renovar; consulta solo para admins.
- Admin no desactiva: 403 antes de tocar BD/encoder (`MfaService.java:108-110`, `verifyNoInteractions`).
- Identidad solo de la sesión (`@AuthenticationPrincipal`), rol del servidor; controladores sin repositorios.

### Tests existentes modificados (no se debilitaron)

- `SesionControllerIT.adminDesdeIpPermitida`: conserva `rol=ADMIN`, `aviso` nulo, 1 sesión y `LOGIN_EXITOSO` del admin; **añade** 202 `CONFIGURACION`, 0 sesiones tras el paso 1 y 5 códigos. Reemplazo previsto en requirements "Reemplazos".
- `InicioSesionServiceTest`: los casos HU-03/HU-06 mantienen sus asserts (solo cast a `SesionIniciadaDTO`); HU-06 R1 pasa a esperar `DesafioMfaDTO` sin `sesiones.save` y con `LOGIN_MFA_PENDIENTE`, como manda la spec.
- `SesionServiceTest`, `CuentaAdminControllerIT`, `RevisionIdentidadControllerIT`, `FiltroIpsAdminIT` (añade assert "sin desafío"), `PagosSoporte`, `ReservasControllerSoporte`, `ManejadorGlobalErroresTest`: cambio mecánico (MFA activo + login de dos pasos), mismos asserts.

### Migración

`V5_1__mfa.sql` coincide con design § 9 y con `docs/modelo-datos.md` (tablas `mfa`, `codigo_respaldo` con `usado_en`, `desafio_mfa`, restricciones e índice, ER actualizado). Única `V5_*`; no edita migraciones previas; nombre `V<sprint>_<n>__…` correcto.

### Desvíos D-A…D-I

- D-A `Mfa implements Persistable`: **aceptado** (mismo patrón que `PerfilArrendador`; evita `merge`).
- D-B sin `PerfilArrendadorRepository` en `InicioSesionService`: **aceptado** (lo usa `EmisorSesiones`).
- D-C `limpiarFallos` tras el commit: **aceptado** (mejor que dentro: un commit fallido no limpia).
- D-D `codigos.flush()` antes de contar: **aceptado** (R63 exacto; probado con `restantes=4`).
- D-E `no-store` también en el 202: **aceptado** (el 202 lleva el desafío; endurece R58).
- D-F `MfaDePrueba.iniciarSesion` olvida el paso por correo: **aceptado** (solo soporte de pruebas).
- D-G soporte de pagos/reservas: **aceptado** (cubre las IT con admin sin tocarlas; todas verdes).
- D-H `erroresCampo.js`: **aceptado** (helper de módulo, no del kit).
- D-I `contexto=CUENTA` literal: **aceptado** (R61 y R64 piden textos distintos para el mismo caso).

## Trazabilidad requirements ↔ tests

(Todos con `@DisplayName("HU-17 R<n>: …")`; se leyó el cuerpo de los tests de IT y de servicio: comprueban códigos HTTP, cuerpos exactos, filas de BD y auditorías.)

- R1: [x] `MfaInicioSesionIT.conMfaActivoResponde202`, `InicioSesionServiceTest.conMfaActivoDevuelveDesafioCodigo`
- R2: [x] `MfaInicioSesionIT.adminSinMfaRecibeConfiguracion` (sin fila y con pendiente), `SesionControllerIT.adminDesdeIpPermitida`
- R3: [x] `MfaInicioSesionIT.usuarioConPendienteRecibeSesion`, `InicioSesionServiceTest.usuarioFinalSinMfaRecibeSesion`
- R4: [x] `MfaInicioSesionIT.conMfaActivoResponde202` (0 filas en `sesion`)
- R5: [x] `MfaInicioSesionIT.guardaDesafio`, `InicioSesionServiceTest.guardaDesafioConHash`
- R6: [x] `MfaInicioSesionIT.guardaDesafio` (hash de 64 y ninguna columna con el token)
- R7: [x] `MfaInicioSesionIT.guardaDesafio`, `InicioSesionServiceTest.desafiosDistintos`
- R8: [x] `MfaInicioSesionIT.contrasenaIncorrectaConMfa` (cuerpo idéntico), `InicioSesionServiceTest.cuentaSuspendidaConMfaSinDesafio`
- R9: [x] `MfaInicioSesionIT.adminConMfaDesdeIpNoPermitida`, `FiltroIpsAdminIT.listaVaciaNadieEntra`
- R10: [x] `MfaInicioSesionIT.totpCreaSesion` (claves exactas, `codigosRespaldo` nulo, sesión usable)
- R11: [x] `MfaInicioSesionIT.respaldoCreaSesionUnaVez`, `MfaControllerIT.siguienteInicioPideCodigo`
- R12: [x] `MfaInicioSesionIT.totpCreaSesion` (fila `sesion`, `expira_en` +30 min), `EmisorSesionesTest.guardaSesionConHash`
- R13: [x] `MfaInicioSesionIT.desafioDeUnSoloUso` (secuencial; falta la carrera, ver H1)
- R14: [x] `MfaInicioSesionIT.totpCreaSesion` (`ultimo_paso`), `VerificadorMfaTest.totpAceptadoRegistraPaso`
- R15: [x] `MfaInicioSesionIT.totpNoSeReutiliza`, `TotpTest.rechazaPasoYaUsado` (secuencial; ver H1)
- R16: [x] `MfaInicioSesionIT.respaldoCreaSesionUnaVez`, `VerificadorMfaTest.respaldoUsadoNoAceptado` (secuencial; ver H1)
- R17: [x] `MfaInicioSesionIT.respaldoCreaSesionUnaVez` (`abcde-fghij`, `ABCDE FGHIJ`, `klmno pqrst`), `GeneradorMfaTest.normalizar`
- R18: [x] `MfaInicioSesionIT.desafiosNoVigentes` (5 causas), `desafioCodigoEnConfiguracion`, `VerificacionMfaServiceTest` (7 casos)
- R19: [x] `MfaInicioSesionIT.cincoFallosCierranElDesafio` (4 × 400 en `codigo`)
- R20: [x] `MfaInicioSesionIT.cincoFallosCierranElDesafio` (`intentos_fallidos` = i), `DesafioMfaTest.quintoFalloCierra`
- R21: [x] `MfaInicioSesionIT.cincoFallosCierranElDesafio` (5.º → 401, `cerrado_en`, el código bueno ya no sirve)
- R22: [x] `MfaInicioSesionIT.solicitudesMalFormadas` (sin contar; luego el bueno entra), `VerificacionMfaDTOTest`
- R23: [x] `MfaInicioSesionIT.adminConfiguraMfaAlIniciarSesion`
- R24: [x] `MfaInicioSesionIT.segundaConfiguracionReemplazaSecreto`, `MfaControllerIT.configurarDosVeces`
- R25: [x] `MfaInicioSesionIT.adminConfiguraMfaAlIniciarSesion` (`activo`, `activado_en`)
- R26: [x] `MfaInicioSesionIT.adminConfiguraMfaAlIniciarSesion`, `SesionControllerIT.adminDesdeIpPermitida`
- R27: [x] `MfaInicioSesionIT.configuracionRechazada` (sin pendiente, código erróneo, respaldo)
- R28: [x] `MfaInicioSesionIT.sesionDeAdminSinMfaRechazada`, `SesionServiceTest.adminSinMfaVacioSinRenovar`
- R29: [x] `MfaControllerIT.configurarGuardaSecretoCifrado`
- R30: [x] `MfaControllerIT.configurarYaActivo`
- R31: [x] `MfaControllerIT.activarDevuelveCodigos`
- R32: [x] `MfaControllerIT.activarDevuelveCodigos` (solo `codigos`)
- R33: [x] `MfaControllerIT.activarSinPendiente` (11 veces sin contar)
- R34: [x] `MfaControllerIT.activarCodigoIncorrecto`
- R35: [x] `MfaControllerIT.activarRevocaOtrasSesiones`, `MfaInicioSesionIT.activarRevocaSesionesPrevias`
- R36: [x] `MfaControllerIT.activarDevuelveCodigos`, `GeneradorMfaTest.reemplazarCodigos`
- R37: [x] `MfaControllerIT.activarDevuelveCodigos` (HMAC del normalizado, ninguna columna con el código)
- R38: [x] `MfaControllerIT.activarDevuelveCodigos` (`GET /api/mfa` sin códigos)
- R39: [x] `MfaControllerIT.estadoSinMfa`, `MfaServiceTest.estado*`
- R40: [x] `MfaControllerIT.desactivar`, `desactivarConRespaldo`
- R41: [x] `MfaControllerIT.desactivarRechazada` (mismo cuerpo), `MfaServiceTest.desactivarCompruebaAmbos`
- R42: [x] `MfaControllerIT.adminNoDesactiva`, `MfaServiceTest.desactivarAdmin`
- R43: [x] `MfaControllerIT.desactivarSinMfa`
- R44: [x] `MfaControllerIT.sinSesion` (las 4 rutas y token inventado)
- R45: [x] `MfaInicioSesionIT.cincoFallosCierranElDesafio`, `MfaControllerIT.desactivarRechazada`, `activarCodigoIncorrecto`
- R46: [x] `MfaInicioSesionIT.diezFallosBloquean`, `MfaControllerIT.activacionesFallidasBloquean`, `fallosDeDesactivacionBloqueanElLogin`
- R47: [x] `MfaInicioSesionIT.bloqueoCaducaA15Minutos` (reloj controlable), `VerificadorMfaTest.falloCaducaALos15Minutos`
- R48: [x] `MfaInicioSesionIT.aciertoReiniciaContador`
- R49: [x] `MfaInicioSesionIT.desafiosNoVigentes`, `solicitudesMalFormadas`, `MfaControllerIT.activarSinPendiente`, `desactivarSinMfa`
- R50: [x] `MfaControllerIT.configurarGuardaSecretoCifrado`
- R51: [x] `TotpTest.vectoresRfc6238`, `Base32Test.codificaVectoresRfc4648`
- R52: [x] `TotpTest.aceptaToleranciaDeUnPaso`
- R53: [x] `TotpTest.rechazaDosPasosOMas`
- R54: [x] `GeneradorMfaTest.nuevoSecreto`, `MfaControllerIT.configurarDosVeces`
- R55: [x] `MfaControllerIT.configurarGuardaSecretoCifrado` (uri exacta con `%2B` y `%40`), `GeneradorMfaTest.configuracionArmaUri`
- R56: [x] `MfaControllerIT.activarDevuelveCodigos` (estado sin secreto)
- R57: [x] `DtoMfaTest` (9 DTO) — ver H2 sobre el mapa
- R58: [x] `MfaInicioSesionIT.conMfaActivoResponde202`, `totpCreaSesion`, `adminConfiguraMfaAlIniciarSesion`, `MfaControllerIT.estadoSinMfa`, `configurarGuardaSecretoCifrado`, `activarDevuelveCodigos`
- R59: [x] `MfaInicioSesionIT.auditaLoginMfaPendiente`
- R60: [x] `MfaInicioSesionIT.totpCreaSesion`, `respaldoCreaSesionUnaVez`
- R61: [x] `MfaInicioSesionIT.adminConfiguraMfaAlIniciarSesion` (`INICIO_SESION`), `MfaControllerIT.activarDevuelveCodigos` (`CUENTA`)
- R62: [x] `MfaControllerIT.desactivar`
- R63: [x] `MfaInicioSesionIT.respaldoCreaSesionUnaVez`, `MfaControllerIT.desactivarConRespaldo`
- R64: [x] `MfaInicioSesionIT.cincoFallosCierranElDesafio`, `MfaControllerIT.activarCodigoIncorrecto`, `desactivarRechazada`
- R65: [x] `MfaInicioSesionIT.diezFallosBloquean`
- R66: [x] `MfaInicioSesionIT.auditoriaSinSecretos`, `MfaControllerIT.auditoriaSinSecretos`

## Trazabilidad con la historia

- CA1 (el admin configura y usa MFA; sin él no accede): [x] R1, R2, R4, R5, R10–R13, R23–R28, R42 — implementado (`InicioSesionService.responder`, `VerificacionMfaService`, `SesionService.validar`).
- CA2 (usuario/arrendador activa MFA; los siguientes inicios piden código): [x] R1, R3, R10, R11, R29–R35, R39.
- CA3 (5 códigos de respaldo de un solo uso): [x] R16, R26, R32, R36, R38.
- (S/I) TOTP RFC 6238: [x] R14, R15, R51–R55 — `Totp`, vectores del RFC verdes.
- (S/I) Secretos cifrados: [x] R50, R56–R58 — `ConvertidorCifrado`.
- (S/I) Códigos de respaldo con hash: [x] R17, R37, R38 — `HuellaCampos`.
- (D) Límite de intentos por sesión: [x] R19–R21, R45–R49 — 5 por desafío en BD + 10 por cuenta / 15 min.
- (R) Registro de activación, desactivación y uso: [x] R59–R66.
- Reemplaza o complementa el control de IPs: [x] R9 — se mantiene (P3 aprobada); docs, README y `.env.example` actualizados.

## Tasks completas

- T1: [x] · T2: [x] · T3: [x] · T4: [x] · T5: [x] · T6: [x] · T7: [x] · T8: [x] · T9: [x] · T10: [x] · T11: [x] · T12: [x]
- T13: [x] · T14: [x] · T15: [x] · T16: [x] · T17: [x] · T18: [x] · T19: [x] · T20: [x] · T21: [x] · T22: [x] · T23: [x]

Las 23 están `[x]` en `specs/HU-17/tasks.md` y se comprobaron en el código y los docs. (T14/T15 cumplen su plan de design § 13; el hueco de H1 viene de que § 13 no incluyó la prueba de concurrencia que DT-11 implica.)

## Checkpoints

- C1 — Arnés completo: [x] (los 5; `./init.sh` completo exit 0)
- C2 — Estado coherente:
  - [x] una `in_progress` por dueño · [x] `progress/HU-17.md` existe · n/a historias `done` (HU-17 aún no lo es)
  - [x] aprobación del dueño anotada (`progress/HU-17.md`: "Spec aprobado por Jorge Quispe el 2026-11-03")
  - [x] `progress/HU-17.md` describe solo la sesión activa
- C3 — Arquitectura:
  - [x] paquetes en módulos · [x] controladores sin repositorios · [x] sin `@Autowired`/Lombok/comodines · [x] sin prints
  - [x] todo en `cuentas` (+ excepción y mensaje en `comun`); sin puertos nuevos (DT-15)
  - [x] DTO `record`; ninguna entidad sale; secreto y códigos solo en R23/R26/R29/R32
  - [x] identidad desde la sesión; rol y estados en el servidor
  - [x] esquema solo por Flyway `V5_1__mfa.sql`; ninguna migración previa editada
  - n/a adapters (sin integraciones externas nuevas)
  - [x] nada fuera del alcance · [x] sin secretos trackeados · [x] colores solo en tokens y sin librerías de UI
  - [x] frontend con el kit (`Campo`, `Boton`, `Alerta`, `Modal peligro-confirmar`), un primario por estado, llamadas solo vía `src/api/cuentas.js`, coincide con design § 12 Pantallas
- C4 — Verificación real:
  - [x] `<Clase>Test` / `<Clase>IT` con `TestcontainersConfiguration` · [x] sin H2 · [x] plazos (5 min, 15 min, pasos de 30 s) con `RelojControlable` / `Clock` fijo
  - [ ] **hay IT, pero las reglas de concurrencia de DT-11 no se prueban lanzando dos operaciones a la vez** ← H1
  - [x] cada test comprueba un resultado concreto, incluido el camino de error
  - [x] `mvnw verify` > 0 tests y todos verdes · [x] lint y build sin errores
- C5 — Cierre de sesión:
  - [x] sin archivos sin trackear sospechosos · [x] `feature_list.json` en `in_progress` · [x] rama `feature/HU-17-…`
  - n/a entrada en `history.md` y vuelta a plantilla de `progress/HU-17.md` (los hace el orquestador al cerrar, tras el APPROVED)
- C6 — SDD:
  - [x] `specs/HU-17/` completo · [x] EARS + tabla de trazabilidad · [x] design con endpoints, clases, migración, secuencias y alternativas descartadas (no ★)
  - n/a "historia `done` con todas las tasks `[x]`" (aún `in_progress`; igualmente 23/23 `[x]`)
  - [x] cada R<n> con test documentado en `progress/impl_HU-17.md` (corregir H2)
  - [x] §9.4 y §9.7 resueltos con decisión del dueño (P2, P3 aprobadas)

## Cambios requeridos

1. **(H1, obligatorio)** Agregar pruebas de concurrencia (patrón de `docs/verification.md` l.59-75 y `RecuperacionContrasenaControllerIT.java:651`: `CountDownLatch` + 2 hilos), en `MfaInicioSesionIT` o en una `MfaConcurrenciaIT` nueva, con `@DisplayName("HU-17 R<n>: …")`:
   - a. **R13:** dos `POST /api/sesiones/mfa` simultáneos con el **mismo desafío** y el mismo TOTP válido → exactamente un 201 y un 401 (verificación expirada); 1 fila en `sesion`; `cerrado_en` fijado.
   - b. **R15:** dos `POST /api/sesiones/mfa` simultáneos con **dos desafíos distintos** de la misma cuenta y el **mismo** TOTP → exactamente un 201 y un 400 en el campo `codigo`; 1 sesión; `ultimo_paso` = ese paso.
   - c. **R16:** dos `POST /api/sesiones/mfa` simultáneos con dos desafíos y el **mismo código de respaldo** → exactamente un 201; ese código con `usado_en` y una sola auditoría `MFA_CODIGO_RESPALDO_USADO`.
   - d. **R24 / DT-11:** dos `POST /api/mfa/configuracion` (o `POST /api/sesiones/mfa/configuracion`) simultáneos de una cuenta **sin fila de `mfa`** → ambos 200 (ninguno 500 por choque de PK) y exactamente 1 fila en `mfa`, cuyo secreto descifrado es uno de los dos devueltos.
   Si alguna falla, corregir el bloqueo correspondiente en `VerificacionMfaService.bloquear` / `MfaService`.
2. **(H2, menor)** En `progress/impl_HU-17.md` l.131, quitar `MfaInicioSesionIT.auditoriaSinSecretos` de la fila R57 (o añadir R57 a su `@DisplayName`) y sumar al mapa los tests nuevos de (1).
3. Volver a correr `./init.sh` completo y actualizar el resultado en `progress/impl_HU-17.md`.

---

# Revisión 2 — HU-17

**Veredicto:** APPROVED

> Revisor: reviewer · 2026-11-03 · misma rama, aún sin commitear. `./init.sh` completo (sin `--rapido`): **exit 0**, "Entorno listo" —
> `mvnw verify` **1189 unitarias + 736 IT** (732 + 4 nuevas), 0 fallos/errores/omitidas; ESLint 0 avisos; build OK; chequeos de
> arquitectura, colores, librerías y secretos `[OK]`.

## Alcance de la vuelta

- `git status` muestra los mismos archivos que en la Revisión 1; no aparece nada nuevo.
- Ningún archivo de `2_Backend/src/main` ni de `3_frontend/src` es más reciente que la primera revisión (`find -newer progress/review_HU-17.md` vacío;
  `MfaService.java`/`VerificacionMfaService.java` siguen a las 14:46–14:47). Se confirma lo que dice el implementer: **sin cambios en `src/main`**.
  Solo cambió `MfaInicioSesionIT.java` (18:29), además de `progress/impl_HU-17.md` y `progress/HU-17.md`. Lo aprobado en la Revisión 1 se mantiene sin repetir.

## H1 — concurrencia (DT-11): resuelto

Helper `aLaVez` (`MfaInicioSesionIT.java:761-775`): `CountDownLatch(1)` como compuerta, `Executors.newFixedThreadPool(2)`, los dos hilos
hacen `listos.await()` y luego `countDown()` los suelta juntos; se recogen ambos `MvcResult` con `get()` (una excepción en un hilo hace fallar el test).
Es el mismo patrón que HU-04 R22. La clase no es `@Transactional` (limpia con `DELETE` en `@BeforeEach`, l.110-118), así que los dos hilos usan
transacciones reales y los bloqueos `PESSIMISTIC_WRITE` de verdad entran en juego.

| Pedido | Test | Lo que comprueba | ¿Fuerte? |
|---|---|---|---|
| a. R13 | `desafioSimultaneoSoloUnoGana` (l.800-814) | mismo desafío + mismo TOTP → `containsExactlyInAnyOrder(201, 401)`; el 401 con `VERIFICACION_EXPIRADA`; `sesion` = 1; `cerrado_en` = ahora; 1 `LOGIN_EXITOSO` | [x] |
| b. R15 | `totpSimultaneoNoSeReutiliza` (l.816-832) | dos desafíos + mismo TOTP → `{201, 400}`; el 400 con `mensaje` de validación y **un solo** error en `codigo` (`esCodigoInvalido`, l.791-798); `sesion` = 1; `ultimo_paso` = `Totp.pasoDe(ahora)`; 1 `LOGIN_EXITOSO` | [x] |
| c. R16 | `respaldoSimultaneoUnSoloUso` (l.834-855) | dos desafíos + mismo respaldo → `{201, 400 en codigo}`; `sesion` = 1; ese código con `usado_en` = ahora; exactamente 1 respaldo usado; una sola auditoría `MFA_CODIGO_RESPALDO_USADO` con `detalle` exacto `contexto=INICIO_SESION;restantes=4` | [x] |
| d. R24 / DT-11 | `configuracionSimultaneaSinChoque` (l.857-878) | admin sin fila `mfa`, dos desafíos CONFIGURACION → `containsExactly(200, 200)` (ningún 500 por PK); los dos secretos devueltos distintos; `mfa` = 1 fila; su secreto descifrado `isIn` los devueltos; `activo` = false | [x] |

No hay asserts débiles: cada test fija los códigos HTTP exactos (no `is4xx`), el cuerpo del perdedor, el conteo de filas y la auditoría.
`@DisplayName("HU-17 R13|R15|R16|R24: …")` correctos.

## H2 — mapa: resuelto

- `progress/impl_HU-17.md` l.131: R57 cita solo los 9 `DtoMfaTest.*`; ya no aparece `MfaInicioSesionIT.auditoriaSinSecretos`, que se queda en R66 (l.140).
- R13 (l.87), R15 (l.89), R16 (l.90) y R24 (l.98) incluyen los tests nuevos, con el R correcto de su `@DisplayName`.
- La sección "Vuelta 1 de revisión" (l.194-214) anota archivos y resultado de `init.sh`, que coincide con esta corrida (736 IT).

## Checkpoints actualizados

- C4 — "hay IT que prueban las reglas de concurrencia lanzando dos operaciones a la vez": **[x]** (antes `[ ]`, H1).
- C6 — "cada R<n> con test documentado en `progress/impl_HU-17.md`": [x] (H2 corregido).
- El resto, igual que en la Revisión 1.

## Hallazgos nuevos

Ninguno. H3–H5 siguen como informativos (sin cambio pedido; H3 queda para la caja negra de UAT).

## Cambios requeridos

Ninguno.
