# Review — HU-02

**Veredicto:** APPROVED

- **Revisor:** reviewer · **Fecha:** 2026-10-27 · **Rama:** `feature/HU-02-registro-arrendador` (cambios sin commitear)
- **`./init.sh` completo (sin `--rapido`), corrido por el revisor:** exit 0, `[OK] Entorno listo`.
  `./mvnw verify`: unitarias 71/71 (29 HU-01 + 42 HU-02) e IT 64/64 (29 HU-01 + 35 `ArrendadorControllerIT`,
  Testcontainers/PostgreSQL), 0 fallos, 0 errores, 0 omitidas. ESLint sin advertencias, `npm run build` OK.
  Todas las reglas de la sección 4 en `[OK]` (controladores sin repositorios, sin `@Autowired`, sin comodines,
  sin prints, sin colores fuera de `tokens.css`, sin librerías de UI, sin secretos trackeados).

## Puntos pedidos por el orquestador

### Tests de HU-01 intactos y verdes
- `git diff` sobre `2_Backend/src/test/` solo toca `cuentas/domain/UsuarioTest.java`, y solo **agrega**
  `registrarArrendadorFijaRolYDatos` (líneas 35-49), como pide T7. `UsuarioControllerIT`, `RegistroServiceTest`,
  `LimitadorIntentosTest`, `PoliticaPrivacidadControllerIT` y `EspacioYaApplicationIT` sin cambios y verdes. [x]
- `Usuario.java`: `registrar` (l. 60) sin cambios; campos nuevos anulables `nombre` (l. 47) y `telefono` (l. 50);
  `registrarArrendador` (l. 69-76) reutiliza `registrar` y solo cambia `rol` a `ARRENDADOR` (l. 72), nombre recortado
  y teléfono. [x]
- `ReglaLimite.java`: componente `grupo` (l. 9) y constructor de 4 argumentos que conserva la clave
  `metodo + " " + ruta` (l. 12); `aplicaA` sin cambios; `clave` = `grupo + "|" + ip` (l. 20). Cambio aditivo. [x]
- `ConfiguracionSeguridad.java:34`: solo agrega `permitAll` para `POST /api/arrendadores`. [x]
- `ReglasLimiteCuentas.java:16-27`: `GRUPO_REGISTRO = "registro"` para las dos reglas (DT-7). [x]
- `Registro.jsx`: importa `REQUISITOS_CONTRASENA` de `textosCuentas.js` (mismo texto) y agrega el enlace
  (l. 113-115). Sin otros cambios de comportamiento. [x]

### Seguridad
- **Cifrado (DT-1):** `CifradorCampos.java` usa `AES/GCM/NoPadding` (l. 27), llave de 32 bytes (AES-256, validada
  en `LlaveCifrado.java:23`), IV de 12 bytes de `SecureRandom` por valor (l. 50-51), tag de 128 bits (l. 29),
  formato `"v1:" + base64(iv ‖ cifrado+tag)` (l. 56-57). Al descifrar, alteración / otra llave / formato
  inválido → `IllegalStateException` sin texto claro ni llave (l. 67-82). Sin dependencias nuevas (`pom.xml` intacto). [x]
- **Llaves:** `application.properties:24-25` con default vacío desde `CIFRADO_LLAVE` / `CIFRADO_LLAVE_HUELLA`;
  `.env.example` con los nombres sin valor. La única llave literal del repo está en
  `src/test/resources/config/application.properties:3-4`, marcada "SOLO PARA PRUEBAS" (DT-4); no hay `.env`,
  `.key` ni `.pem` trackeados. Arranque falla si una llave falta, no mide 32 bytes (`LlaveCifrado.java:14-25`,
  mensaje con el nombre de la variable y sin el valor) o si son iguales (`CifradorCampos.java:39-42`). [x]
- **Huella (DT-5):** `HuellaCampos` = HMAC-SHA256 hex con llave propia; índice único
  `uq_perfil_arrendador_hash_documento` en `V1_3__perfil_arrendador.sql:20`. [x]
- **Anti-enumeración:** validaciones de versión, contraseña y documento antes de la BD
  (`RegistroArrendadorService.java:65-73`); bcrypt y huella siempre (l. 75-76); correo antes que documento y
  ambos caminos devuelven sin error (l. 105-117) → el controlador responde el mismo 202 con el mismo cuerpo
  (`ArrendadorController.java:35-36`). Verificado en IT con `content().json(RESPUESTA_202, true)` para correo
  existente y documento existente, y con igualdad byte a byte del 400 en R28. Carreras resueltas por
  `DataIntegrityViolationException` (l. 81-88). [x]
- **Rol en el servidor (STRIDE E):** `RegistroArrendadorDTO` no tiene `rol`, `estado`, `correoVerificado` ni
  `estadoIdentidad`; el rol lo fija `Usuario.registrarArrendador` (l. 72) y el estado de identidad
  `PerfilArrendador.crear` (l. 71). Un `USUARIO` existente no se convierte (DT-8, probado en
  `ArrendadorControllerIT.correoDeUsuarioExistente`). `api/cuentas.js` no envía `rol`. [x]

## Trazabilidad requirements ↔ tests
- R1: [x] `ArrendadorControllerIT.creaArrendadorActivoVerificado` (rol/estado/correo_verificado en BD), `UsuarioTest.registrarArrendadorFijaRolYDatos`, `RegistroArrendadorServiceTest.guardaUsuarioArrendador`
- R2: [x] `ArrendadorControllerIT.creaPerfilNoIniciado`, `PerfilArrendadorTest.crearDejaNoIniciada`, `RegistroArrendadorServiceTest.guardaPerfilDelArrendador`
- R3: [x] `ArrendadorControllerIT.guardaNombreYTelefono` (nombre recortado en BD), `UsuarioTest.registrarArrendadorFijaRolYDatos`
- R4: [x] `ArrendadorControllerIT.guardaConsentimiento` (versión y fecha = reloj), `RegistroArrendadorServiceTest.guardaUsuarioArrendador` (Clock fijo)
- R5: [x] `ArrendadorControllerIT.responde202ConMensajeYAviso` (JSON estricto `{mensaje, aviso}`)
- R6: [x] `ArrendadorControllerIT.invocaAlNotificador`, `RegistroArrendadorServiceTest.notificaUnaVez` (`times(1)`, correo normalizado)
- R7: [x] `ArrendadorControllerIT.guardaCorreoNormalizado`
- R8: [x] `ArrendadorControllerIT.rechazaSinAceptarPrivacidad` (falso y ausente; sin filas ni notificación)
- R9: [x] `ArrendadorControllerIT.rechazaVersionInvalida`, `RegistroArrendadorServiceTest.rechazaVersionNoVigente`
- R10: [x] `ArrendadorControllerIT.rechazaContrasenaDebil`, `RegistroArrendadorServiceTest.rechazaContrasenaDebil`
- R11: [x] `ArrendadorControllerIT.rechazaCorreoInvalido` (ausente, sin formato, > 254)
- R12: [x] `ArrendadorControllerIT.rechazaNombreInvalido`
- R13: [x] `ArrendadorControllerIT.rechazaTelefonoInvalido`
- R14: [x] `ArrendadorControllerIT.rechazaRazonSocialInvalida`
- R15: [x] `ArrendadorControllerIT.rechazaDireccionInvalida`
- R16: [x] `ArrendadorControllerIT.rechazaTipoDocumentoInvalido` (mensaje exacto)
- R17: [x] `ArrendadorControllerIT.rechazaNumeroAusente`, `ValidadorDocumentoTest.numeroFaltante`, `RegistroArrendadorServiceTest.rechazaNumeroVacio`
- R18: [x] `ArrendadorControllerIT.rechazaDniInvalido` (incluye DNI con 11 dígitos), `ValidadorDocumentoTest.dniDeOchoDigitos`
- R19: [x] `ArrendadorControllerIT.rechazaRucDeLongitudInvalida`, `ValidadorDocumentoTest.rucDeOnceDigitos`
- R20: [x] `ArrendadorControllerIT.rechazaRucConPrefijoInvalido`, `ValidadorDocumentoTest.prefijoDelRuc`
- R21: [x] `ArrendadorControllerIT.rechazaRucConVerificadorInvalido`, `ValidadorDocumentoTest.digitoVerificador`, `ValidadorDocumentoTest.restosEspeciales` (10→0 y 11→1; verifiqué a mano 20100070970 suma 89 y 20100000131 suma 22)
- R22: [x] `ArrendadorControllerIT.correoDeUsuarioExistente` (mismo 202 estricto), `RegistroArrendadorServiceTest.correoExistenteNoGuardaNiNotifica`
- R23: [x] `ArrendadorControllerIT.correoDeUsuarioExistente` (fila idéntica, sigue `USUARIO`, sin perfil ni correo)
- R24: [x] `ArrendadorControllerIT.correoDeUsuarioExistente` (id, IP, fecha), `RegistroArrendadorServiceTest.auditaCorreoExistente`
- R25: [x] `ArrendadorControllerIT.documentoExistente` (mismo 202 estricto, número con espacios)
- R26: [x] `ArrendadorControllerIT.documentoExistente`, `RegistroArrendadorServiceTest.documentoExistenteNoGuardaNiNotifica`
- R27: [x] `ArrendadorControllerIT.documentoExistente` (id del titular, IP, fecha), `RegistroArrendadorServiceTest.auditaDocumentoExistente`
- R28: [x] `ArrendadorControllerIT.validaAntesDeConsultarLaBd` (cuerpos 400 idénticos), `RegistroArrendadorServiceTest.rechazaVersionNoVigente` / `rechazaContrasenaDebil` / `rechazaNumeroVacio` / `rechazaDocumentoInvalido` (`verifyNoInteractions` con repositorios)
- R29: [x] `ArrendadorControllerIT.carreraPorCorreo` (2 hilos + `CountDownLatch`: una cuenta, dos 202 iguales), `RegistroArrendadorServiceTest.carreraPorCorreo`
- R30: [x] `ArrendadorControllerIT.carreraPorDocumento`, `RegistroArrendadorServiceTest.carreraPorDocumento`
- R31: [x] `ArrendadorControllerIT.ignoraCamposDeRolYEstado` (`rol: ADMIN`, `estado: SUSPENDIDA`, `correoVerificado: false`, `estadoIdentidad: VERIFICADA` ignorados)
- R32: [x] `ArrendadorControllerIT.auditaRegistro`, `RegistroArrendadorServiceTest.auditaRegistro` (detalle exacto `versionPolitica=2026-10`)
- R33: [x] `ArrendadorControllerIT.auditoriaSinDatosDeFacturacion` (los 3 tipos de evento), `RegistroArrendadorServiceTest.auditaRegistro` / `auditaCorreoExistente` / `auditaDocumentoExistente`
- R34: [x] `ArrendadorControllerIT.guardaFacturacionCifrada` (columnas `v1:`, sin claro, descifran al original), `CifradorCamposTest.idaYVuelta`
- R35: [x] `CifradorCamposTest.dosCifradosDistintos`
- R36: [x] `CifradorCamposTest.byteAlteradoLanzaExcepcion`, `otraLlaveLanzaExcepcion`, `formatoDesconocidoLanzaExcepcion`
- R37: [x] `ArrendadorControllerIT.guardaHuellaDelDocumento` (= `HuellaCampos.calcular(numero normalizado)`), `HuellaCamposTest.deterministaYHexadecimal`, `otraLlaveOtraHuella`, `distintaDelSha256`
- R38: [x] `CifradorCamposTest.llaveInvalidaFalla` (vacía, blanca, null, no base64, 16 bytes; nombra la variable y no el valor), `llaveHuellaInvalidaFalla`, `HuellaCamposTest.llaveInvalidaFalla`
- R39: [x] `CifradorCamposTest.llavesIgualesFalla`
- R40: [x] `ArrendadorControllerIT.respuestasSinDatosSensibles` (202 y 400)
- R41: [x] `RegistroArrendadorDTOTest.toStringEnmascarado`
- R42: [x] `ArrendadorControllerIT.cupoCompartidoConUsuarios` (3 a `/api/usuarios` + 2 a `/api/arrendadores` → 6.º 429 con mensaje exacto, y `/api/usuarios` también bloqueado), `ReglaLimiteTest.*`
- R43: [x] `ArrendadorControllerIT.cuentanLosIntentosInvalidosYExistentes` (JSON ilegible, 400, correo existente, documento existente → 6.º 429), `ReglaLimiteTest.mismoGrupoMismaClave`
- R44: [x] `ArrendadorControllerIT.falloDelNotificador` (500 genérico estricto, sin usuario, perfil ni auditoría), `RegistroArrendadorServiceTest.falloDelNotificadorRevierte` (rollback, sin commit)

## Trazabilidad con la historia
- CA1 (datos válidos → cuenta con rol arrendador): [x] R1, R2, R3, R5, R6, R7, R12–R16
- CA1 ("correo pendiente", resuelto por D1 aprobada por la dueña el 2026-10-27, `progress/HU-02.md`): [x] R1, R31
- CA1 (acepta la política: versión, fecha, IP): [x] R4, R8, R9, R32
- CA2 (RUC/DNI inválido → error específico): [x] R16–R21; la pantalla muestra el mensaje bajo "Número de documento" (`RegistroArrendador.jsx:153-160`)
- CA3 (aviso de verificar identidad; D1: en la confirmación, tras login en HU-03): [x] R1, R2, R5; `RegistroArrendador.jsx:82-83` (`Alerta exito` + `Alerta atencion`)
- (E) Rol asignado en backend: [x] R1, R31 — implementado (ver Seguridad)
- (I) Facturación cifrada en reposo: [x] R34–R39 — implementado con AES-256-GCM + HMAC
- (I) Facturación cifrada en tránsito: [x] R40, R41 en código; TLS es del despliegue (DT-10), anotado para UAT en `progress/impl_HU-02.md`
- (R) Evento de creación con fecha, hora e IP: [x] R4, R32, R33 — implementado

## Tasks completas
- T1: [x] · T2: [x] · T3: [x] · T4: [x] · T5: [x] · T6: [x] · T7: [x] · T8: [x] · T9: [x]
- T10: [x] · T11: [x] · T12: [x] · T13: [x] · T14: [x] · T15: [x] · T16: [x] · T17: [x]
- Todas `[x]` en `specs/HU-02/tasks.md`; comprobadas contra el código y contra el resultado de `./init.sh`.

## Coincidencia con `design.md`
- DT-1 [x] · DT-2 [x] (`ConvertidorCifrado` es bean y recibe `CifradorCampos`; probado por `guardaFacturacionCifrada`)
  · DT-3 [x] · DT-4 [x] · DT-5 [x] · DT-6 [x] · DT-7 [x] · DT-8 [x] · DT-9 [x] (correo antes que documento,
  `RegistroArrendadorService.java:105-117`) · DT-10 [x] (nada en código; nota de despliegue) · DT-11 [x]
  (`V1_3__perfil_arrendador.sql:12`) · DT-12 [x] (`RegistroService` sin cambios) · DT-13 [x] (`tipo_documento` en
  claro con `CHECK`) · DT-14 [x] · DT-15 [x] (`docs/modelo-datos.md`, `docs/architecture.md` principio 7)
  · DT-16 [x] (`Campo.jsx` con `tipo="select"`, `docs/frontend.md` actualizado).
- D1 [x]: nace con `correo_verificado = true` y el aviso va en la confirmación.
- Migración `V1_3__perfil_arrendador.sql` idéntica a design § 9.
- Desviación documentada y aceptable: clase de paquete `comun/cifrado/LlaveCifrado` y texto del mensaje de llave
  inválida (sigue nombrando la variable sin mostrar el valor). No cambia firmas públicas.
- Pantalla `/registro-arrendador` coincide con design § 12: secciones Acceso/Contacto/Facturación, `Campo`
  select con DNI/RUC, ayudas, un solo `Boton` primario con `cargando`, deshabilitado sin aceptación o sin versión,
  errores por campo + `Alerta` error, llamadas solo vía `api/cuentas.js`, solo tokens en el CSS Module.

## Checkpoints
- C1: [x] arnés completo; `./init.sh` completo exit 0.
- C2: [x] una sola historia `in_progress` de la dueña; `progress/HU-02.md` existe; aprobación del spec (incluida D1) por Adriana Callupe anotada; HU-01 `done` con tests verdes.
- C3: [x] paquetes `cuentas` y `comun`; controlador sin repositorios; constructor injection; sin Lombok ni comodines; DTO `record`; sin entidades en la API; rol y estados en el servidor; migración `V1_3__…` nueva (no se editó `V1_1`/`V1_2`); notificador detrás del puerto de HU-01; nada fuera del alcance; sin secretos trackeados; frontend con kit, tokens y `src/api/`.
- C4: [x] `*Test` con Mockito y `*IT` con `TestcontainersConfiguration` (PostgreSQL real); `Clock` fijo / `RelojControlable`; concurrencia probada con dos hilos y `CountDownLatch`; asserts sobre resultados concretos; `mvnw verify` 135 tests verdes; lint y build OK.
- C5: [x] sin archivos sospechosos sin trackear (`target/` y `node_modules/` ignorados); trabajo en `feature/HU-02-registro-arrendador`; estado `in_progress` correcto hasta el cierre. n/a por ahora: entrada de `progress/history.md` y vuelta de `progress/HU-02.md` a la plantilla (se hacen al cerrar la historia).
- C6: [x] `specs/HU-02/` completo; EARS con tabla de trazabilidad que cubre CA1–CA3 y E/I/R; `design.md` con endpoints, clases, migración, secuencias y 8 alternativas; tasks todas `[x]`; R1–R44 mapeados en `progress/impl_HU-02.md`; ningún pendiente de §9 resuelto sin decisión de la dueña. n/a: patrón ★ (la historia no es ★).

## Observaciones (no bloquean)
1. **Cambios ajenos a la historia:** `3_frontend/package-lock.json` (11 líneas borradas) y `package-lock.json` vacío
   en la raíz (sin seguimiento), ambos de un `npm install` previo. Conviene descartarlos o sacarlos del commit de HU-02
   (`git restore 3_frontend/package-lock.json` y borrar el de la raíz) para que el PR solo traiga HU-02.
2. `specs/HU-02/requirements.md:11-27` sigue diciendo que D1 está "pendiente de confirmar", aunque
   `progress/HU-02.md` registra que la dueña la aprobó. Sugiero cambiar el encabezado a "D1 — confirmada por
   Adriana Callupe el 2026-10-27" antes del PR.
3. Tiempo de respuesta: el camino "correo o documento existente" no hace los `INSERT` ni el cifrado, así que es algo
   más rápido que un alta real. Es lo mismo que acepta HU-01 y el design solo exige igualar bcrypt y huella (§ 6 paso 5);
   se deja anotado por si en el futuro se quiere igualar más.
4. `App.jsx:24`: el segundo `<p>` del inicio no lleva `className` (el primero usa `estilos.inicio`). Solo estético.
5. R38 se prueba en el constructor de los componentes, no levantando un contexto sin llaves. Basta porque son
   `@Component` con inyección por constructor (si el constructor lanza, el contexto no arranca), pero una IT de
   arranque fallido lo dejaría demostrado de punta a punta.
6. `progress/HU-02.md` dice "Rama creada desde `develop`"; la rama principal del repo es `master`. Revisar a qué rama
   apunta el PR.

## Cambios requeridos
Ninguno.
