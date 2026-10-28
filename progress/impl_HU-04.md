# Implementación HU-04 · Recuperación de contraseña

> Implementer · 2026-10-28 · rama `feature/HU-04-recuperacion-contrasena`.
> Spec: `specs/HU-04/` (R1–R31, T1–T15, todas `[x]`). Sin commit ni push: el dueño revisa y abre el PR.

## Trazabilidad

Clases de prueba (paquete `pe.edu.ulima.espacioya`):
`ServiceTest` = `cuentas.service.RecuperacionContrasenaServiceTest` (unitaria, Mockito, `Clock` fijo,
`LimitadorIntentos` real) · `ControllerIT` = `cuentas.controller.RecuperacionContrasenaControllerIT`
(Testcontainers + `RelojControlable` + `NotificadorFalso`).

- R1 → `ControllerIT.solicitudConCuentaResponde202`, `ControllerIT.correoDesconocidoMismaRespuesta`, `ControllerIT.cuentasNoHabilitadasMismaRespuesta`, `ControllerIT.cuartaSolicitudMismaRespuestaSinToken`
- R2 → `ControllerIT.guardaTokenConHashYFechas`, `ServiceTest.emiteTokenYNotificaEnlace`, `TokenTest.recuperacionFijaFechasYEstado`
- R3 → `ControllerIT.guardaTokenConHashYFechas`, `ServiceTest.emiteTokenYNotificaEnlace`, `TokenTest.recuperacionFijaFechasYEstado`
- R4 → `ControllerIT.dosSolicitudesTokensDistintos`, `ServiceTest.dosSolicitudesTokensDistintos`, `ServiceTest.emiteTokenYNotificaEnlace`
- R5 → `ControllerIT.notificaConCorreoNormalizadoYEnlace`, `ServiceTest.emiteTokenYNotificaEnlace`
- R6 → `ControllerIT.enlaceNuevoAnulaElAnterior`, `ServiceTest.anulaPendientesAntesDeGuardar`
- R7 → `ControllerIT.correoDesconocidoMismaRespuesta`, `ServiceTest.correoDesconocidoNoEmite`
- R8 → `ControllerIT.cuentasNoHabilitadasMismaRespuesta`, `ServiceTest.cuentaNoHabilitadaNoEmite`
- R9 → `ControllerIT.cuartaSolicitudMismaRespuestaSinToken`, `ControllerIT.limiteSeLiberaTrasUnaHora`, `ServiceTest.cuartaSolicitudNoEmite`
- R10 → `ControllerIT.limiteSeLiberaTrasUnaHora`, `ControllerIT.correoSinCuentaConsumeCupo`, `ServiceTest.correoInexistenteConsumeCupo`
- R11 → `ControllerIT.rechazadasNoConsumenCupo`, `ServiceTest.solicitudRechazadaNoCuenta`
- R12 → `ControllerIT.correoInvalidoResponde400`
- R13 → `NotificadorCorreoSimuladoTest.apagadaNoRegistraEnlace`
- R14 → `NotificadorCorreoSimuladoTest.encendidaRegistraEnlaceSinCorreo`
- R15 → `ControllerIT.validarTokenVigente`, `ServiceTest.validaTokenVigente`
- R16 → `ControllerIT.validarTokenInvalidoODesconocido`, `ControllerIT.validarTokenExpirado`, `ControllerIT.validarTokenDeCuentaSuspendida`, `ControllerIT.segundoUsoRechazado`, `ServiceTest.validarRechazaCadaMotivo`, `ServiceTest.confirmarTokenDesconocido`, `ServiceTest.confirmarAuditaCadaMotivo`, `TokenTest.expiraEnElLimiteExacto`, `TokenTest.tokenUsado`
- R17 → `ServiceTest.formaInvalidaSinConsultar`, `ControllerIT.validarTokenInvalidoODesconocido`, `TokenAleatorioTest.tokenGeneradoTieneFormato`, `TokenAleatorioTest.formasInvalidas`
- R18 → `ControllerIT.restableceYRespondeSoloMensaje`, `ServiceTest.restableceContrasena`, `UsuarioTest.cambiarContrasenaReemplazaElHash`
- R19 → `ControllerIT.segundoUsoRechazado`, `ServiceTest.restableceContrasena`
- R20 → `ControllerIT.restablecerAnulaLosDemasTokens`, `ServiceTest.restableceContrasena`
- R21 → `ControllerIT.restablecerRevocaTodasLasSesiones`, `ServiceTest.restableceContrasena`
- R22 → `ControllerIT.soloUnaConfirmacionGana` (dos hilos con el mismo token), `ServiceTest.carreraDeConfirmaciones`
- R23 → `ControllerIT.contrasenaQueNoCumpleLaPolitica`, `ServiceTest.contrasenaDebilNoMarcaToken`
- R24 → `ControllerIT.restablecerReiniciaLosFallosDeLogin`, `ServiceTest.reiniciaContadorDeLogin`, `ServiceTest.rechazoConservaContadorDeLogin`
- R25 → `ControllerIT.restableceYRespondeSoloMensaje`, `ServiceTest.restableceContrasena`
- R26 → `ControllerIT.auditaSolicitudEmitida`, `ServiceTest.auditaSolicitudEmitida`
- R27 → `ControllerIT.auditaSolicitudesNoEmitidas`, `ServiceTest.correoDesconocidoNoEmite`, `ServiceTest.cuentaNoHabilitadaNoEmite`, `ServiceTest.cuartaSolicitudNoEmite`
- R28 → `ControllerIT.auditaRestablecimiento`, `ServiceTest.auditaRestablecimiento`
- R29 → `ControllerIT.auditaConfirmacionesRechazadas`, `ServiceTest.carreraDeConfirmaciones`, `ServiceTest.confirmarTokenDesconocido`, `ServiceTest.confirmarAuditaCadaMotivo`
- R30 → `ControllerIT.auditoriasSinDatosSensibles`, `ControllerIT.auditaSolicitudEmitida`, `ControllerIT.auditaSolicitudesNoEmitidas`, `ControllerIT.auditaRestablecimiento`, `ControllerIT.auditaConfirmacionesRechazadas`, `ServiceTest.auditaSolicitudEmitida`, `ServiceTest.auditaRestablecimiento`, `ServiceTest.correoDesconocidoNoEmite`, `ServiceTest.carreraDeConfirmaciones`, `ServiceTest.confirmarTokenDesconocido`
- R31 → `RecuperacionDTOTest.solicitudEnmascarada`, `RecuperacionDTOTest.tokenEnmascarado`, `RecuperacionDTOTest.restablecimientoEnmascarado`

Pantallas (sin `R<n>` propios, `docs/verification.md`): `npm run lint -- --max-warnings=0` y `npm run build`
verdes; la pareja las verifica en UAT con la caja negra.

## Archivos

**Backend — nuevos**
- `2_Backend/src/main/resources/db/migration/V1_5__token.sql`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/cuentas/domain/Token.java`, `TipoToken.java`, `MotivoRechazoRecuperacion.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/cuentas/repository/TokenRepository.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/cuentas/service/RecuperacionContrasenaService.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/cuentas/dto/SolicitudRecuperacionDTO.java`, `TokenRecuperacionDTO.java`, `RestablecimientoContrasenaDTO.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/cuentas/controller/RecuperacionContrasenaController.java`
- Pruebas: `cuentas/domain/TokenTest.java`, `cuentas/dto/RecuperacionDTOTest.java`, `cuentas/service/RecuperacionContrasenaServiceTest.java`, `cuentas/controller/RecuperacionContrasenaControllerIT.java`

**Backend — modificados**
- `comun/seguridad/TokenAleatorio.java` (`tieneFormato`), `comun/seguridad/ConfiguracionSeguridad.java` (tres `permitAll`)
- `cuentas/domain/Usuario.java` (`cambiarContrasena`), `cuentas/repository/SesionRepository.java` (`revocarTodas`)
- `cuentas/service/NotificadorCorreo.java` (`notificarRecuperacion`), `cuentas/service/NotificadorCorreoSimulado.java` (propiedad de desarrollo), `cuentas/service/InicioSesionService.java` (`PREFIJO_CLAVE` de paquete)
- `2_Backend/src/main/resources/application.properties` (`spring.flyway.out-of-order=true`, `espacioya.correo.simulado.registrar-enlace`), `2_Backend/.env.example` (`CORREO_SIMULADO_REGISTRAR_ENLACE=false`)
- Pruebas: `TokenAleatorioTest`, `UsuarioTest`, `NotificadorCorreoSimuladoTest` (constructor `boolean`; HU-01 R4 admite `boolean`), dobles `NotificadorFalso` de `UsuarioControllerIT` y `ArrendadorControllerIT` (solo implementan el método nuevo)

**Frontend**
- Nuevos: `3_frontend/src/pages/cuentas/RecuperarContrasena.jsx` (+ `.module.css`), `RestablecerContrasena.jsx` (+ `.module.css`)
- Modificados: `3_frontend/src/api/cuentas.js`, `pages/cuentas/textosCuentas.js`, `pages/cuentas/IniciarSesion.jsx` (+ `.module.css`, clase `.olvido`), `App.jsx`

**Docs**
- `docs/modelo-datos.md`: `token` con `creado_en` y `hash_token` único; restricción en "Restricciones clave".
- `docs/architecture.md` y `docs/frontend.md` sin cambios: no hay puerto nuevo entre módulos (D2) ni componentes o estados nuevos en el kit.

## Decisiones menores tomadas al implementar (dentro del spec)

- `@Modifying(flushAutomatically = true, clearAutomatically = true)` en `marcarUsado`, `anularPendientes` y
  `revocarTodas` (design § 6 lo deja al implementer): así el cambio de hash del `Usuario` se escribe antes de
  limpiar el contexto. Parámetros con `@Param`, como `PerfilArrendadorRepository`.
- Auditoría: `RECUPERACION_NO_EMITIDA` usa entidad `usuario` (id nulo si no existe, como `LOGIN_FALLIDO`);
  `RECUPERACION_RECHAZADA` usa entidad `token` con el id del token (nulo si no se encontró). R27/R29 no fijan
  la entidad; el detalle es solo `motivo=…`.
- Un token sin forma válida se audita al confirmar como `motivo=TOKEN_DESCONOCIDO` (R29 no tiene un motivo
  propio para la forma).
- La URL del frontend se usa sin `/` final para armar el enlace.
- En la IT, las cuentas se crean con `UsuarioRepository` (como `SesionControllerIT`) y se ajustan con
  `JdbcTemplate`; no por `POST /api/usuarios`, para no depender del límite de 5 registros por IP.
- `@Email` (igual que en HU-01) rechaza un correo con espacios a los extremos: la IT de R5 prueba la
  normalización con mayúsculas; el recorte de espacios se prueba en `ServiceTest.emiteTokenYNotificaEnlace`.

## Tasks no completadas

Ninguna.

## Notas para el equipo

- **`spring.flyway.out-of-order=true` (S1):** desde este PR Flyway aplica migraciones de número menor que
  lleguen tarde (aquí `V1_5` después de `V2_1`). La numeración por sprint sigue siendo obligatoria y una
  migración ya subida a `develop` no se edita. **Avisar a Ariel Enriquez (HU-06)** antes de numerar su
  migración `V2_n`.
- **Propiedad de desarrollo (D1):** `CORREO_SIMULADO_REGISTRAR_ENLACE` queda en `false` por defecto. Solo en
  una máquina de desarrollo se pone en `true` para leer el enlace del log; **nunca en UAT**. Para la caja
  negra en UAT, el enlace no se ve en el log: hace falta HU-25 o probar en local.
- **Una sola instancia del backend (S11):** el límite de 3 solicitudes por correo y hora vive en memoria
  (`LimitadorIntentos`); se pierde al reiniciar y no se comparte entre instancias, igual que HU-01 y HU-03.

## Verificación

`./init.sh` completo (sin `--rapido`), 2026-10-28, con JDK 25 y Docker encendido: **[OK] Entorno listo**.
- `./mvnw verify`: 204 pruebas unitarias y 147 de integración, 0 fallos (incluidas las de HU-01, HU-02,
  HU-03 y HU-05). Las nuevas: 20 en `ServiceTest`, 28 en `ControllerIT`, 3 en `TokenTest`, 3 en
  `RecuperacionDTOTest`, 2 en `TokenAleatorioTest`, 2 en `NotificadorCorreoSimuladoTest`, 1 en `UsuarioTest`.
- Chequeo de trazabilidad del arnés: sin avisos (R1–R31 tienen `@DisplayName("HU-04 R<n>: …")`).
- Reglas de arquitectura: sin `@Autowired` en `src/main`, controladores sin repositorios, sin imports con
  comodín, sin prints, sin colores fuera de `tokens.css`, sin secretos trackeados.
- Frontend: `npm run lint -- --max-warnings=0` y `npm run build` sin errores.
