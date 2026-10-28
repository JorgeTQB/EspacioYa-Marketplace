# Review — HU-04

**Veredicto:** APPROVED

> Reviewer · 2026-10-28 · rama `feature/HU-04-recuperacion-contrasena` (cambios sin commitear sobre `develop` `832d7a1`).
> `./init.sh` completo (sin `--rapido`, JDK 25, Docker encendido): **EXIT=0**, "[OK] Entorno listo".
> `./mvnw verify`: 204 unitarias + 147 de integración, 0 fallos (incluye HU-01, HU-02, HU-03 y HU-05).
> ESLint sin advertencias y `npm run build` correcto.

## Trazabilidad requirements ↔ tests

Abreviaturas: `IT` = `cuentas/controller/RecuperacionContrasenaControllerIT`, `ST` = `cuentas/service/RecuperacionContrasenaServiceTest`.

- R1: [x] `IT.solicitudConCuentaResponde202` (202, cuerpo con solo `mensaje`), `IT.correoDesconocidoMismaRespuesta` (cuerpo idéntico byte a byte), `IT.cuentasNoHabilitadasMismaRespuesta`, `IT.cuartaSolicitudMismaRespuestaSinToken`
- R2: [x] `IT.guardaTokenConHashYFechas` (usuario_id, tipo, creado_en = reloj, expira_en = +30 min, usado_en nulo), `ST.emiteTokenYNotificaEnlace`, `TokenTest.recuperacionFijaFechasYEstado`
- R3: [x] `IT.guardaTokenConHashYFechas` (`hash_token` = SHA-256 del token del enlace; ninguna columna contiene el token)
- R4: [x] `IT.dosSolicitudesTokensDistintos`, `ST.dosSolicitudesTokensDistintos`
- R5: [x] `IT.notificaConCorreoNormalizadoYEnlace` (una llamada, correo normalizado, enlace `…/restablecer-contrasena#token=<43>`), `ST.emiteTokenYNotificaEnlace` (recorte de espacios)
- R6: [x] `IT.enlaceNuevoAnulaElAnterior` (usado_en = reloj; el primero 400, el segundo 204), `ST.anulaPendientesAntesDeGuardar`
- R7: [x] `IT.correoDesconocidoMismaRespuesta` (0 filas en `token`, notificador sin llamadas), `ST.correoDesconocidoNoEmite`
- R8: [x] `IT.cuentasNoHabilitadasMismaRespuesta` (suspendida, admin, sin verificar), `ST.cuentaNoHabilitadaNoEmite`
- R9: [x] `IT.cuartaSolicitudMismaRespuestaSinToken`, `IT.limiteSeLiberaTrasUnaHora`, `ST.cuartaSolicitudNoEmite`
- R10: [x] `IT.correoSinCuentaConsumeCupo`, `IT.limiteSeLiberaTrasUnaHora`, `ST.correoInexistenteConsumeCupo`
- R11: [x] `IT.rechazadasNoConsumenCupo` (400 y solicitudes por encima del límite no ocupan cupo), `ST.solicitudRechazadaNoCuenta`
- R12: [x] `IT.correoInvalidoResponde400` (vacío, sin formato, más de 254, ausente: 400 con error en `correo`, sin token)
- R13: [x] `NotificadorCorreoSimuladoTest.apagadaNoRegistraEnlace` (salida sin token, sin `restablecer-contrasena`, sin correo)
- R14: [x] `NotificadorCorreoSimuladoTest.encendidaRegistraEnlaceSinCorreo`
- R15: [x] `IT.validarTokenVigente` (204 y fila de `token` idéntica antes y después), `ST.validaTokenVigente`
- R16: [x] `IT.validarTokenInvalidoODesconocido`, `IT.validarTokenExpirado` (límite exacto de 30 min, también al confirmar), `IT.validarTokenDeCuentaSuspendida` (sin cambio de contraseña, token ni sesiones), `IT.segundoUsoRechazado`, `ST.validarRechazaCadaMotivo`, `ST.confirmarAuditaCadaMotivo`, `TokenTest.expiraEnElLimiteExacto`
- R17: [x] `ST.formaInvalidaSinConsultar` (`verifyNoInteractions(tokens, usuarios, sesiones, encoder)` en validar y confirmar), `TokenAleatorioTest.tokenGeneradoTieneFormato`, `TokenAleatorioTest.formasInvalidas`
- R18: [x] `IT.restableceYRespondeSoloMensaje` (200 solo con `mensaje`; hash bcrypt nuevo; login con la nueva 201 y con la anterior 401), `ST.restableceContrasena`, `UsuarioTest.cambiarContrasenaReemplazaElHash`
- R19: [x] `IT.segundoUsoRechazado` (usado_en = reloj; segundo uso 400 sin cambiar el hash)
- R20: [x] `IT.restablecerAnulaLosDemasTokens` (el pendiente de la cuenta queda anulado; el de otra cuenta sigue vigente)
- R21: [x] `IT.restablecerRevocaTodasLasSesiones` (dos sesiones dan 401 en `/api/sesiones/actual`; la de otra cuenta sigue 200), `ST.restableceContrasena`
- R22: [x] `IT.soloUnaConfirmacionGana` (dos hilos con `CountDownLatch`, resultado true/false, una sola auditoría `CONTRASENA_RESTABLECIDA` y una `RECUPERACION_RECHAZADA motivo=TOKEN_USADO`), `ST.carreraDeConfirmaciones` (`marcarUsado` = 0: sin `cambiarContrasena`, `anularPendientes` ni `revocarTodas`)
- R23: [x] `IT.contrasenaQueNoCumpleLaPolitica` (sin dígito, igual al correo, nula: 400 en `contrasena`; token sin usar; luego el mismo token sirve), `ST.contrasenaDebilNoMarcaToken`
- R24: [x] `IT.restablecerReiniciaLosFallosDeLogin` (5 fallos, 429, restablecer, 201), `ST.reiniciaContadorDeLogin`, `ST.rechazoConservaContadorDeLogin`
- R25: [x] `IT.restableceYRespondeSoloMensaje` (0 filas en `sesion`, respuesta sin `token`), `ST.restableceContrasena` (`sesiones.save` nunca)
- R26: [x] `IT.auditaSolicitudEmitida` (usuario, entidad `token` con su id, IP, fecha, detalle nulo), `ST.auditaSolicitudEmitida`
- R27: [x] `IT.auditaSolicitudesNoEmitidas` (`CORREO_DESCONOCIDO` con usuario nulo, `CUENTA_NO_ACTIVA`, `LIMITE_SUPERADO`), `ST.cuentaNoHabilitadaNoEmite` (`ROL_NO_PERMITIDO`, `CORREO_NO_VERIFICADO`)
- R28: [x] `IT.auditaRestablecimiento` (`sesionesRevocadas=2`), `ST.auditaRestablecimiento`
- R29: [x] `IT.auditaConfirmacionesRechazadas` (`TOKEN_EXPIRADO`, `TOKEN_USADO`, `CUENTA_NO_HABILITADA`, `TOKEN_DESCONOCIDO` dos veces con usuario nulo), `ST.confirmarTokenDesconocido`, `ST.confirmarAuditaCadaMotivo`
- R30: [x] `IT.auditoriasSinDatosSensibles` (ninguna columna de las 4 auditorías contiene token, hash, arroba ni contraseñas), más las comprobaciones de detalle en R26–R29
- R31: [x] `RecuperacionDTOTest.solicitudEnmascarada`, `.tokenEnmascarado`, `.restablecimientoEnmascarado`

## Trazabilidad con la historia

- CA1 (siempre el mismo mensaje): [x] R1, R7, R8, R9, R12. Implementado en `RecuperacionContrasenaController.solicitar` (líneas 32–38: 202 fijo con `MENSAJE_SOLICITUD_RECIBIDA`) y `RecuperacionContrasenaService.solicitar` (líneas 82–109, nunca lanza).
- CA1 (enlace válido 30 min si la cuenta existe): [x] R2, R5, R13, R14, R15. `Token.VIGENCIA_RECUPERACION` = 30 min; pantalla `RecuperarContrasena.jsx`.
- CA2 (expirado o usado: rechazo al abrirlo): [x] R15, R16, R17, R19, R22. Endpoint `POST /api/recuperaciones/validaciones`; pantalla `RestablecerContrasena.jsx` con `Alerta` de error.
- CA2 (puedo pedir otro): [x] R2, R6. Enlace "Solicitar un enlace nuevo" hacia `/recuperar-contrasena`.
- CA3 (se cierran todas las sesiones activas): [x] R18, R21, R25. `SesionRepository.revocarTodas` dentro de la transacción (`RecuperacionContrasenaService` líneas 131–143).
- (S/T) Token aleatorio, de un solo uso, guardado con hash: [x] R3, R4, R6, R16, R17, R19, R20, R22. `TokenAleatorio.generar()` (32 bytes de `SecureRandom`), solo `TokenAleatorio.hash` en `token.hash_token` (CHECK hex y UNIQUE en `V1_5__token.sql`), UPDATE condicional en `TokenRepository.marcarUsado`.
- (I) Respuesta idéntica exista o no el correo: [x] R1, R7, R8, R9, R13, R30, R31.
- (D) Máximo 3 solicitudes por correo cada hora: [x] R9, R10, R11. `limitador.intentar` con la clave `recuperacion|` + correo, 3 por 1 h, antes de buscar la cuenta (servicio, línea 85).
- (R) Registro de solicitud y uso: [x] R26, R27, R28, R29, R30.

### Puntos pedidos por el orquestador

- **Token nunca en claro (salvo D1 encendida):** [x] La tabla solo guarda el hash (la IT lo comprueba en todas las columnas). El token solo sale del servicio en el enlace que recibe el notificador. `NotificadorCorreoSimulado.notificarRecuperacion` (líneas 28–34) escribe el enlace únicamente si `registrarEnlace` es true. Ningún log de `src/main` recibe el DTO, el token ni el enlace fuera de esa rama. Los DTO enmascaran `toString` y `ManejadorGlobalErrores` no registra nada para `ReglaNegocioException`.
- **D1 apagada por defecto:** [x] `application.properties` línea 29 con valor por defecto `false`, `@Value` del constructor con valor por defecto `false`, `.env.example` en `false` con el aviso "nunca true en UAT". No hay propiedades de prueba que la activen.
- **Atomicidad del uso único (R22):** [x] `marcarUsado` es un UPDATE con `usado_en IS NULL AND expira_en > :ahora` en el WHERE. Si afecta 0 filas, la transacción devuelve false antes de tocar la cuenta. Hay prueba concurrente real con dos hilos contra PostgreSQL.
- **Revocación en la misma transacción (R21, S7):** [x] `marcarUsado`, `cambiarContrasena` (se escribe con el `flushAutomatically` de `anularPendientes`), `anularPendientes`, `revocarTodas` y la auditoría corren dentro del mismo `TransactionTemplate.execute`. `AuditoriaService.registrar` es `@Transactional` REQUIRED y se une a esa transacción. bcrypt va antes, fuera de ella (DT-4).
- **Sin cambios de comportamiento en HU-01/HU-03:** [x] `NotificadorCorreo` solo gana un método. `notificarVerificacion` del simulado no cambia. `InicioSesionService` solo cambia la visibilidad de `PREFIJO_CLAVE` (de `private` a paquete). Los dobles `NotificadorFalso` de `UsuarioControllerIT` y `ArrendadorControllerIT` solo añaden el método nuevo, sin cambiar sus pruebas. `NotificadorCorreoSimuladoTest` (HU-01 R4) solo amplía lo permitido a parámetros y campos `boolean`, como autoriza T5. Todas las IT de HU-01, HU-02, HU-03 y HU-05 están verdes.

## Tasks completas

- T1: [x]
- T2: [x]
- T3: [x]
- T4: [x]
- T5: [x]
- T6: [x]
- T7: [x]
- T8: [x]
- T9: [x]
- T10: [x]
- T11: [x]
- T12: [x]
- T13: [x]
- T14: [x]
- T15: [x] (verificado de nuevo por el reviewer: `./init.sh` completo con EXIT=0)

## Checkpoints

**C1 — Arnés completo**
- C1.1 Archivos base: [x]
- C1.2 Docs: [x]
- C1.3 `docs/historias/HU-XX.md` de cada historia: [x]
- C1.4 Los 4 subagentes: [x]
- C1.5 `./init.sh` sin `--rapido` con exit 0: [x]

**C2 — Estado coherente**
- C2.1 Como mucho una `in_progress` por dueño: [x] (solo HU-04)
- C2.2 `progress/HU-04.md` existe: [x]
- C2.3 Historias `done` con tests verdes: [x]
- C2.4 Aprobación del dueño anotada: [x] (`progress/HU-04.md`: "Spec aprobado por: Eduardo Saldaña, 2026-10-28", S1–S12 aceptados)
- C2.5 `progress/HU-04.md` solo con la sesión activa: [x]

**C3 — Arquitectura**
- C3.1 Paquetes en módulos previstos: [x]
- C3.2 Controladores sin repositorios: [x]
- C3.3 Sin `@Autowired` en main, sin Lombok, sin comodines: [x]
- C3.4 Sin prints de depuración: [x]
- C3.5 Dependencias entre módulos solo vía service o puerto: [x] (todo en `cuentas` y `comun`; sin puerto nuevo, D2)
- C3.6 DTO `record` y ninguna entidad en la API: [x] (`MensajeDTO`, `ErrorDTO`)
- C3.7 Identidad y estados decididos en el servidor: [x] (endpoints públicos; la cuenta sale del token, nunca de un id del cliente)
- C3.8 Esquema solo por Flyway, sin editar migraciones subidas: [x] (`V1_5__token.sql` nueva; `spring.flyway.out-of-order=true` aceptado por el dueño en S1)
- C3.9 Integraciones externas detrás de un Adapter: [x] (`NotificadorCorreo`)
- C3.10 Nada fuera del alcance: [x]
- C3.11 Sin secretos trackeados: [x]
- C3.12 Frontend sin colores fuera de tokens ni librerías: [x]
- C3.13 Frontend con el kit, mensajes según `docs/frontend.md` y API solo desde `src/api/`: [x] (`Campo`, un solo `Boton` primario por pantalla, `Alerta`; llamadas en `api/cuentas.js` con `autenticada: false`; coincide con la sección 9 "Pantallas" de `design.md`)

**C4 — Verificación real**
- C4.1 Nombres `<Clase>Test` / `<Clase>IT` con `@Import(TestcontainersConfiguration.class)`: [x]
- C4.2 Sin H2: [x]
- C4.3 Plazos con `Clock` fijo o `RelojControlable`: [x] (30 min y 1 h sin esperas)
- C4.4 Al menos una `*IT`; la concurrencia se prueba lanzando las dos operaciones a la vez: [x] (`soloUnaConfirmacionGana`)
- C4.5 Cada test comprueba un resultado concreto, incluido el error: [x]
- C4.6 `./mvnw verify` con más de 0 tests y todos verdes: [x] (204 + 147)
- C4.7 `npm run lint && npm run build`: [x]

**C5 — Cierre de sesión**
- C5.1 Sin archivos sin trackear sospechosos: [x]
- C5.2 `progress/history.md` con entrada de la sesión: n/a (lo hace el implementer al cerrar tras este APPROVED, paso 5 de `orquestador.md`)
- C5.3 `progress/HU-04.md` vuelto a la plantilla: n/a (igual: al cerrar)
- C5.4 Estado correcto en `feature_list.json`: [x] (`in_progress` durante la revisión; pasa a `done` al cerrar)
- C5.5 Trabajo en la rama `feature/HU-04-recuperacion-contrasena`: [x]

**C6 — SDD**
- C6.1 `specs/HU-04/` con los tres archivos: [x]
- C6.2 `requirements.md` en EARS con la tabla de trazabilidad completa: [x]
- C6.3 `design.md` con endpoints, clases, migración, secuencias y alternativas descartadas: [x] (sin patrón ★)
- C6.4 Tasks `[x]`: [x]
- C6.5 Cada `R<n>` con test documentado en `progress/impl_HU-04.md`: [x]
- C6.6 Ningún `R<n>` resuelve un pendiente de la sección 9 sin decisión del dueño: [x] (la sección 9 no tiene pendientes de HU-04)

## Cambios requeridos

Ninguno.

## Observaciones no bloqueantes (para el dueño y para HU-25)

1. `RecuperacionContrasenaService.solicitar` (líneas 102–108) llama a `notificador.notificarRecuperacion` dentro de la transacción. Coincide con la sección 2 de `design.md`, y con el envío simulado no puede fallar. Con el adaptador real de HU-25, una excepción del proveedor haría responder 500 solo a las cuentas existentes y habilitadas, y eso revelaría que la cuenta existe (STRIDE I). HU-25 debería capturar el fallo o enviar después del commit sin cambiar el 202.
2. Dos solicitudes simultáneas de la misma cuenta podrían dejar dos tokens vigentes, porque `anularPendientes` y `save` no se serializan. R6 no exige concurrencia y ambos tokens siguen siendo de un solo uso y de 30 minutos: el riesgo es bajo.
3. En `RestablecerContrasena.jsx` (líneas 47–51), un error de red al validar muestra la vista de enlace inválido con el texto "No pudimos conectar. Intenta de nuevo." y el enlace "Solicitar un enlace nuevo". El mensaje cumple `docs/frontend.md`, pero ofrecer otro enlace ante un fallo de red puede confundir. Es un detalle de UX para la caja negra en UAT.
4. `IT.rechazadasNoConsumenCupo` cubre los 400 de R11 con JSON mal formado, no con un correo inválido en forma. Es el caso que importa: un correo inválido usaría otra clave del limitador. Se acepta.
