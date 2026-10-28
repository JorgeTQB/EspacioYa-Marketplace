# Implementación HU-02 — Registro de arrendador

> implementer · 2026-10-27 · rama `feature/HU-02-registro-arrendador` · spec `specs/HU-02/`
> (aprobado por Adriana Callupe el 2026-10-27, incluida D1). Tasks T1–T17 todas `[x]`.
> Sin commit, push ni PR. Historia sigue `in_progress` a la espera del reviewer.

## Resultado de `./init.sh` (completo, sin `--rapido`)

`exit=0`, "[OK] Entorno listo". Docker encendido.

- Sección 3: `feature_list.json` válido; ningún aviso de R<n> sin `@DisplayName` para HU-02.
- Sección 4: todas las reglas en [OK] (sin repositorios en controladores, sin `@Autowired`,
  sin comodines, sin prints, sin colores fuera de `tokens.css`, sin secretos trackeados).
  El "bad substitution" conocido de la línea 214 **no apareció**: el `bash` del PATH es
  GNU bash 5.3 de Homebrew (`/opt/homebrew/bin/bash`). No se tocó `init.sh`.
- Sección 5 (`./mvnw verify`): unitarias **71/71** (29 de HU-01 + 42 de HU-02) e IT **64/64**
  (29 de HU-01: `UsuarioControllerIT` 27, `PoliticaPrivacidadControllerIT` 1,
  `EspacioYaApplicationIT` 1; + 35 de `ArrendadorControllerIT`). Ningún test de HU-01 se
  modificó; a `UsuarioTest` solo se le **agregó** un test de HU-02 (lo pide T7).
- Sección 6: `npm run lint -- --max-warnings=0` y `npm run build` en verde.

## Archivos

(N) nuevo · (M) modificado. Raíz de Java: `2_Backend/src/main/java/pe/edu/ulima/espacioya/`.

### Backend — `comun`
- (N) `comun/cifrado/CifradorCampos.java` — AES-256-GCM, IV de 12 bytes, tag 128, `"v1:" + base64`; falla al crearse si una llave es inválida o si son iguales.
- (N) `comun/cifrado/HuellaCampos.java` — HMAC-SHA256 en hex (64).
- (N) `comun/cifrado/LlaveCifrado.java` — helper de paquete (no público) que decodifica y valida una llave de 32 bytes; el mensaje nombra la variable y nunca el valor. No está en la tabla de `design.md` § 5: evita duplicar la validación de R38 en las dos clases; no cambia ninguna firma pública.
- (N) `comun/cifrado/ConvertidorCifrado.java` — `AttributeConverter` bean de Spring (DT-2 confirmado: Hibernate recibe el bean con su constructor).
- (M) `comun/limite/ReglaLimite.java` — componente `grupo` + constructor de 4 argumentos.
- (M) `comun/seguridad/ConfiguracionSeguridad.java` — `permitAll` para `POST /api/arrendadores`.

### Backend — `cuentas`
- (N) `cuentas/domain/TipoDocumento.java`, `EstadoIdentidad.java`, `ValidadorDocumento.java`, `PerfilArrendador.java`
- (M) `cuentas/domain/Usuario.java` — `nombre`, `telefono`, `registrarArrendador` (`registrar` de HU-01 sin cambios).
- (N) `cuentas/repository/PerfilArrendadorRepository.java`
- (N) `cuentas/service/RegistroArrendadorService.java`
- (N) `cuentas/dto/RegistroArrendadorDTO.java`, `RegistroArrendadorRecibidoDTO.java`
- (N) `cuentas/controller/ArrendadorController.java`
- (M) `cuentas/controller/ReglasLimiteCuentas.java` — `GRUPO_REGISTRO` y regla de `/api/arrendadores`.

### Recursos y configuración
- (N) `2_Backend/src/main/resources/db/migration/V1_3__perfil_arrendador.sql`
- (M) `2_Backend/src/main/resources/application.properties` — `espacioya.cifrado.llave` y `llave-huella` (default vacío).
- (M) `2_Backend/.env.example` — `CIFRADO_LLAVE=` y `CIFRADO_LLAVE_HUELLA=` sin valor, con cómo generarlas.
- (N) `2_Backend/src/test/resources/config/application.properties` — llaves fijas **solo de prueba** (DT-4).

### Tests (`2_Backend/src/test/java/pe/edu/ulima/espacioya/`)
- (N) `comun/cifrado/CifradorCamposTest.java` (9), `comun/cifrado/HuellaCamposTest.java` (4), `comun/limite/ReglaLimiteTest.java` (3)
- (N) `cuentas/domain/ValidadorDocumentoTest.java` (7), `cuentas/domain/PerfilArrendadorTest.java` (2)
- (M) `cuentas/domain/UsuarioTest.java` (+1 test HU-02; los 2 de HU-01 intactos)
- (N) `cuentas/service/RegistroArrendadorServiceTest.java` (15), `cuentas/dto/RegistroArrendadorDTOTest.java` (1)
- (N) `cuentas/controller/ArrendadorControllerIT.java` (35, Testcontainers, IPs `10.2.x.x`)

### Frontend (`3_frontend/src/`)
- (M) `components/ui/Campo.jsx` — `tipo="select"` con `opciones`.
- (M) `api/cuentas.js` — `registrarArrendador` → `POST /api/arrendadores` (sin `rol`).
- (N) `pages/cuentas/textosCuentas.js` — `REQUISITOS_CONTRASENA` (antes local en `Registro.jsx`).
- (N) `pages/cuentas/RegistroArrendador.jsx` + `RegistroArrendador.module.css` — ruta `/registro-arrendador`.
- (M) `pages/cuentas/Registro.jsx` + `Registro.module.css` — importa el texto compartido y agrega el enlace "¿Tienes un espacio? Regístrate como arrendador".
- (M) `App.jsx` — ruta `/registro-arrendador` y enlace en `/`.

### Docs y spec
- (M) `docs/modelo-datos.md` — `perfil_arrendador` con `hash_documento` (único, HMAC), `razon_social` y `direccion_fiscal` cifradas en lugar de `datos_facturacion`; fila nueva en "Restricciones clave" (DT-15).
- (M) `docs/architecture.md` — principio 7: `perfil_arrendador.hash_documento` único (HU-02). Sin puertos nuevos.
- (M) `docs/frontend.md` — fila `Campo` del kit con `select` / `opciones`.
- (M) `specs/HU-02/tasks.md` (T1–T17 `[x]`), `progress/HU-02.md` (bitácora).
- No se tocaron `3_frontend/package-lock.json` ni `package-lock.json` de la raíz (ya venían modificados o sin seguimiento antes de esta sesión).

## Trazabilidad

- R1 → `ArrendadorControllerIT.creaArrendadorActivoVerificado`, `UsuarioTest.registrarArrendadorFijaRolYDatos`, `RegistroArrendadorServiceTest.guardaUsuarioArrendador`
- R2 → `ArrendadorControllerIT.creaPerfilNoIniciado`, `PerfilArrendadorTest.crearDejaNoIniciada`, `PerfilArrendadorTest.perfilNuevo`, `RegistroArrendadorServiceTest.guardaPerfilDelArrendador`
- R3 → `ArrendadorControllerIT.guardaNombreYTelefono`, `UsuarioTest.registrarArrendadorFijaRolYDatos`, `RegistroArrendadorServiceTest.guardaUsuarioArrendador`
- R4 → `ArrendadorControllerIT.guardaConsentimiento`, `UsuarioTest.registrarArrendadorFijaRolYDatos`, `RegistroArrendadorServiceTest.guardaUsuarioArrendador`
- R5 → `ArrendadorControllerIT.responde202ConMensajeYAviso`
- R6 → `ArrendadorControllerIT.invocaAlNotificador`, `RegistroArrendadorServiceTest.notificaUnaVez`
- R7 → `ArrendadorControllerIT.guardaCorreoNormalizado`, `RegistroArrendadorServiceTest.guardaUsuarioArrendador`
- R8 → `ArrendadorControllerIT.rechazaSinAceptarPrivacidad`
- R9 → `ArrendadorControllerIT.rechazaVersionInvalida`, `RegistroArrendadorServiceTest.rechazaVersionNoVigente`
- R10 → `ArrendadorControllerIT.rechazaContrasenaDebil`, `RegistroArrendadorServiceTest.rechazaContrasenaDebil`
- R11 → `ArrendadorControllerIT.rechazaCorreoInvalido`
- R12 → `ArrendadorControllerIT.rechazaNombreInvalido`
- R13 → `ArrendadorControllerIT.rechazaTelefonoInvalido`
- R14 → `ArrendadorControllerIT.rechazaRazonSocialInvalida`
- R15 → `ArrendadorControllerIT.rechazaDireccionInvalida`
- R16 → `ArrendadorControllerIT.rechazaTipoDocumentoInvalido`
- R17 → `ArrendadorControllerIT.rechazaNumeroAusente`, `ValidadorDocumentoTest.numeroFaltante`, `ValidadorDocumentoTest.normalizaEspacios`, `RegistroArrendadorServiceTest.rechazaNumeroVacio`
- R18 → `ArrendadorControllerIT.rechazaDniInvalido`, `ValidadorDocumentoTest.dniDeOchoDigitos`, `RegistroArrendadorServiceTest.rechazaDocumentoInvalido`
- R19 → `ArrendadorControllerIT.rechazaRucDeLongitudInvalida`, `ValidadorDocumentoTest.rucDeOnceDigitos`, `RegistroArrendadorServiceTest.rechazaDocumentoInvalido`
- R20 → `ArrendadorControllerIT.rechazaRucConPrefijoInvalido`, `ValidadorDocumentoTest.prefijoDelRuc`, `RegistroArrendadorServiceTest.rechazaDocumentoInvalido`
- R21 → `ArrendadorControllerIT.rechazaRucConVerificadorInvalido`, `ValidadorDocumentoTest.digitoVerificador`, `ValidadorDocumentoTest.restosEspeciales`, `RegistroArrendadorServiceTest.rechazaDocumentoInvalido`
- R22 → `ArrendadorControllerIT.correoDeUsuarioExistente`, `RegistroArrendadorServiceTest.correoExistenteNoGuardaNiNotifica`
- R23 → `ArrendadorControllerIT.correoDeUsuarioExistente`, `RegistroArrendadorServiceTest.correoExistenteNoGuardaNiNotifica`
- R24 → `ArrendadorControllerIT.correoDeUsuarioExistente`, `RegistroArrendadorServiceTest.auditaCorreoExistente`
- R25 → `ArrendadorControllerIT.documentoExistente`, `RegistroArrendadorServiceTest.documentoExistenteNoGuardaNiNotifica`
- R26 → `ArrendadorControllerIT.documentoExistente`, `RegistroArrendadorServiceTest.documentoExistenteNoGuardaNiNotifica`
- R27 → `ArrendadorControllerIT.documentoExistente`, `RegistroArrendadorServiceTest.auditaDocumentoExistente`
- R28 → `ArrendadorControllerIT.validaAntesDeConsultarLaBd`, `RegistroArrendadorServiceTest.rechazaVersionNoVigente`, `RegistroArrendadorServiceTest.rechazaContrasenaDebil`, `RegistroArrendadorServiceTest.rechazaNumeroVacio`, `RegistroArrendadorServiceTest.rechazaDocumentoInvalido`
- R29 → `ArrendadorControllerIT.carreraPorCorreo`, `RegistroArrendadorServiceTest.carreraPorCorreo`
- R30 → `ArrendadorControllerIT.carreraPorDocumento`, `RegistroArrendadorServiceTest.carreraPorDocumento`
- R31 → `ArrendadorControllerIT.ignoraCamposDeRolYEstado`, `UsuarioTest.registrarArrendadorFijaRolYDatos`
- R32 → `ArrendadorControllerIT.auditaRegistro`, `RegistroArrendadorServiceTest.auditaRegistro`
- R33 → `ArrendadorControllerIT.auditoriaSinDatosDeFacturacion`, `RegistroArrendadorServiceTest.auditaRegistro`, `RegistroArrendadorServiceTest.auditaCorreoExistente`, `RegistroArrendadorServiceTest.auditaDocumentoExistente`
- R34 → `CifradorCamposTest.idaYVuelta`, `CifradorCamposTest.nullEsNull`, `ArrendadorControllerIT.guardaFacturacionCifrada`
- R35 → `CifradorCamposTest.dosCifradosDistintos`
- R36 → `CifradorCamposTest.byteAlteradoLanzaExcepcion`, `CifradorCamposTest.otraLlaveLanzaExcepcion`, `CifradorCamposTest.formatoDesconocidoLanzaExcepcion`
- R37 → `HuellaCamposTest.deterministaYHexadecimal`, `HuellaCamposTest.otraLlaveOtraHuella`, `HuellaCamposTest.distintaDelSha256`, `ArrendadorControllerIT.guardaHuellaDelDocumento`, `RegistroArrendadorServiceTest.guardaPerfilDelArrendador`
- R38 → `CifradorCamposTest.llaveInvalidaFalla`, `CifradorCamposTest.llaveHuellaInvalidaFalla`, `HuellaCamposTest.llaveInvalidaFalla`
- R39 → `CifradorCamposTest.llavesIgualesFalla`
- R40 → `ArrendadorControllerIT.respuestasSinDatosSensibles`
- R41 → `RegistroArrendadorDTOTest.toStringEnmascarado`
- R42 → `ReglaLimiteTest.constructorDeCuatroConservaLaClave`, `ReglaLimiteTest.mismoGrupoMismaClave`, `ReglaLimiteTest.aplicaASinCambios`, `ArrendadorControllerIT.cupoCompartidoConUsuarios`
- R43 → `ReglaLimiteTest.mismoGrupoMismaClave`, `ArrendadorControllerIT.cuentanLosIntentosInvalidosYExistentes`
- R44 → `ArrendadorControllerIT.falloDelNotificador`, `RegistroArrendadorServiceTest.falloDelNotificadorRevierte`

## Notas para el reviewer y para el despliegue

- **UAT necesita** `CIFRADO_LLAVE` y `CIFRADO_LLAVE_HUELLA` (32 bytes en base64, distintas:
  `openssl rand -base64 32`). Sin ellas el backend **no arranca** (R38/R39). Si se pierden, los
  datos cifrados no se recuperan: guardarlas como secreto del entorno.
- **Cifrado en tránsito (DT-10):** HTTPS lo pone el despliegue de UAT; no hay código en HU-02.
  Se verifica en la caja negra de la pareja.
- **D1:** el arrendador nace con `correo_verificado = true` y el aviso de identidad va en la
  confirmación del registro; el aviso tras iniciar sesión queda para HU-03.
- **Desviación menor del design:** clase de paquete `comun/cifrado/LlaveCifrado` (ver arriba).
  El mensaje de llave inválida es "Falta <VARIABLE> o no es una llave de 32 bytes en base64
  (genérala con: openssl rand -base64 32)" en vez del texto orientativo de § 5; cumple R38
  (nombra la variable, no muestra el valor).
- **Tasks sin completar:** ninguna.
- Matriz §7 de `docs/requerimientos.md`: no se tocó (la llena la pareja).
