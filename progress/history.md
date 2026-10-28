# Bitácora histórica (append-only)

> Cada vez que se cierra una sesión, su resumen se añade aquí.
> No edites entradas anteriores. Solo añades al final.

---

## 2026-10-26 — Bootstrap del arnés
- **Agente:** Jorge Quispe con Claude Code.
- **Cambios:** `AGENTS.md` como mapa; carpetas `progress/` y `specs/`;
  `docs/architecture.md`, `docs/conventions.md`, `docs/specs.md` y
  `docs/verification.md` a partir de la especificación anterior.
- **Resultado:** estructura base creada. Pendientes: `feature_list.json`,
  `CHECKPOINTS.md`, `init.sh` y `.claude/agents/`.

## 2026-10-27 — Limpieza de documentos heredados
- **Agente:** Jorge Quispe con Claude Code.
- **Cambios:** eliminados `AGENTS antiguo.md`, `arquitectura-propuesta.md` y
  `docs/estructura-backend.md` (recuperables desde git). Su contenido útil para
  agentes se movió: modelo de datos → `docs/modelo-datos.md` (nuevo); puertos
  entre módulos → `docs/architecture.md`; descripción del producto →
  `README.md`. Se descartó lo que solo
  sirve a personas o al informe (calendario, roles Scrum, DoR, reglas del
  informe, justificación de la BD y lista de clases por módulo).
  `docs/requerimientos.md` se mantiene (RNF-SEG-17, RN, componentes
  transversales y pendientes) con referencias actualizadas; `AGENTS.md` ahora
  lo enlaza junto con `docs/modelo-datos.md`.
- **Resultado:** sin referencias rotas. Siguen pendientes `feature_list.json`,
  `CHECKPOINTS.md`, `init.sh` y `.claude/agents/`.

## 2026-10-27 — Cierre del arnés SDD
- **Agente:** Jorge Quispe con Claude Code.
- **Cambios:** `feature_list.json` con las 28 historias (dueño, pareja, módulo,
  release, sprint, dependencias y patrones, tomados de `docs/historias/`), todas
  `pending` y `"sdd": true`; `CHECKPOINTS.md` (C1-C6 adaptados al stack);
  `init.sh` (entorno Java 25 / Node / Docker, archivos del arnés, coherencia de
  `feature_list.json` con specs, progress y `@DisplayName`, reglas de
  arquitectura por grep, `./mvnw verify`, lint y build del frontend; modo
  `--rapido`); subagentes `leader`, `spec_author`, `implementer` y `reviewer`
  en `.claude/agents/`; `.gitattributes` para que `init.sh` mantenga LF.
  La plantilla de progreso suma "Fase" y "Spec aprobado por".
- **Resultado:** `./init.sh` en verde (1 prueba de integración, frontend
  compila). Arnés completo; la siguiente sesión puede empezar HU-01.

## 2026-10-27 — Hook de verificación al cerrar
- **Agente:** Jorge Quispe con Claude Code.
- **Cambios:** `.claude/settings.json` con un hook `Stop` que corre
  `.claude/hooks/verificar-cierre.sh` (`./init.sh --rapido`); en rojo muestra
  los `[FAIL]` como aviso, en verde no dice nada.
- **Resultado:** probado en verde (~18 s) y en rojo. No reemplaza al
  `./init.sh` completo antes de `done`.

## 2026-10-27 — Orquestador, CLAUDE.md y README
- **Agente:** Jorge Quispe con Claude Code.
- **Cambios:** el subagente `leader` pasa a llamarse `orquestador`
  (`.claude/agents/orquestador.md`) en todo el arnés (las entradas anteriores
  de esta bitácora conservan el nombre viejo). `CLAUDE.md` obliga a actuar
  como orquestador, siguiendo el `CLAUDE.md` de referencia. El explorador se
  lanza como `general-purpose` y deja su informe en `progress/explore_HU-XX.md`.
  `README.md` explica al equipo cómo trabajar una historia con Claude Code.
- **Pendiente:** los permisos (`permissions.allow`) de `.claude/settings.json`
  los agrega a mano el usuario; Claude no puede darse permisos a sí mismo.

## 2026-10-27 — Definición de done y guía de frontend
- **Agente:** Jorge Quispe con Claude Code.
- **Cambios:** `docs/specs.md` y `docs/verification.md` separan "código
  terminado" (`done` del arnés) de "historia aceptada" (UAT y PO, columna
  Estado de la matriz §7). Nueva `docs/frontend.md`: los `R<n>` se escriben
  sobre la API y cada `design.md` trae una sección "Pantallas"; carpetas,
  paleta azul con contraste AA verificado, color por estado, kit de 7
  componentes (el kit base lo construye HU-01), mensajes, rutas y cliente HTTP.
  `3_frontend/src/styles/tokens.css` creado; `index.css`, `App.css` e
  `index.html` limpios de la plantilla de Vite (sin modo oscuro, `lang="es"`).
  `init.sh` rechaza colores fuera de `tokens.css` y librerías de UI;
  agentes, `CHECKPOINTS.md`, `AGENTS.md`, `conventions.md` y
  `architecture.md` apuntan a la guía.
- **Resultado:** `./init.sh` completo en verde.

## 2026-10-27 — Paleta definitiva del frontend
- **Agente:** Jorge Quispe con Claude Code.
- **Cambios:** `tokens.css` con la paleta exacta acordada (slate + azul, estados
  éxito/atención/error/neutro, `--radio-tarjeta`), importado en `main.jsx`.
  `docs/frontend.md` actualizado: tabla de colores, reglas de uso (un primario
  por pantalla, rojo solo para errores y acciones destructivas, montos en
  texto negrita), color de cada estado y botón `peligro-confirmar`. El bloque
  ocupado usa `--color-texto-secundario` porque `#94A3B8` no se lee (2.3:1).
- **Pendiente:** el borde de los campos (`#E2E8F0`, 1.2:1) no llega al 3:1;
  decidir si se agrega un borde más oscuro para campos.

## 2026-10-27 — Borde de controles
- **Agente:** Jorge Quispe con Claude Code.
- **Cambios:** nuevo `--color-borde-control` (`#64748B`, 4.76:1) para campos,
  casillas, radios y botón secundario; `--color-borde` queda para bordes
  decorativos. `docs/frontend.md` fija los bordes del campo según su estado
  (normal, foco con anillo de 2 px, error).
- **Resultado:** resuelto el pendiente del borde de los campos.

## 2026-10-27 — Endurecimiento del .gitignore
- **Agente:** Jorge Quispe con Claude Code.
- **Cambios:** `.gitignore` ahora ignora state y variables de Terraform/CDK
  (`*.tfstate`, `*.tfvars`, `.terraform/`, `.aws/`, `cdk.out/`), más llaves y
  certificados (`*.pfx`, `*.keystore`, `*.p8`, `*.ppk`, `id_rsa*`,
  `id_ed25519*`), credenciales (`credentials.json`, `*service-account*.json`,
  `secrets.*`), perfiles `application-uat.*`/`application-prod.*`, `.npmrc`,
  `*.har`, `http-client.private.env.json`, overrides de Docker Compose,
  `CLAUDE.local.md` y respaldos de base (`*.dump`, `*.backup`, `*.sql.gz`,
  `dumps/`). Nuevo `3_frontend/.env.example` que advierte que las variables
  `VITE_` son públicas.
- **Pendiente:** la regla "Secretos trackeados en git" de `init.sh` aún no
  revisa los patrones nuevos.

## 2026-10-27 — Agentes y frontend
- **Agente:** Jorge Quispe con Claude Code.
- **Cambios:** revisado que todos los agentes lleguen a `docs/frontend.md`.
  `spec_author` pide la sección "Pantallas" y separa tasks de backend (con
  `R<n>` y test) de tasks de frontend (con pantalla o CA visible);
  `implementer` ubica el código de frontend según la guía, actualiza sus
  tablas y verifica con lint y build en lugar de tests; `reviewer` lee la guía
  y rechaza si falta "Pantallas"; el explorador del `orquestador` también
  revisa el kit, `src/api/` y las rutas. `docs/specs.md` y
  `docs/verification.md` ya no exigen `R<n>` ni test a las tasks de frontend.

- 2026-10-27 — (orquestador, sdd:false) Resueltos §9.5 (política de contraseña) y §9.7 (límite de registros por IP) en `docs/requerimientos.md` por decisión de Jorge Quispe, para desbloquear HU-01.

## 2026-10-27 — HU-01 Registro de usuario con consentimiento (done)
- **Agente:** orquestador → spec_author → implementer → reviewer (dueño: Jorge Quispe).
- **Rama:** `feature/HU-01-registro-consentimiento`.
- **Spec:** `specs/HU-01/` con R1–R26 y T1–T18. Bloqueo inicial por §9.5 y
  §9.7 resuelto por el dueño (contraseña 8–64 con letra y dígito y distinta
  del correo; 5 registros por IP por hora, ventana deslizante, 429 genérico).
  Cambio pedido por el dueño: sin correo real en HU-01 (notificador simulado
  que solo escribe en el log; la cuenta nace con `correo_verificado = true`).
  Spec aprobado por Jorge Quispe el 2026-10-27.
- **Cambios:** migraciones `V1_1__usuarios.sql` y `V1_2__auditoria.sql`;
  `comun/` (manejo global de errores, auditoría, límite de intentos por IP,
  seguridad con BCrypt(12) y CORS, reloj UTC); `cuentas/` (dominio `Usuario`,
  `PoliticaContrasena`, `RegistroService`, puerto `NotificadorCorreo` con
  implementación simulada, `UsuarioController`, `PoliticaPrivacidadController`);
  propiedades `espacioya.privacidad.version-vigente` y `espacioya.frontend.url`.
  Frontend: `react-router-dom`, kit `Boton`/`Campo`/`Alerta`, `api/cliente.js`,
  `api/cuentas.js`, pantallas `/registro` y `/privacidad`.
- **Resultado:** `./init.sh` completo en verde (29 unitarias + 26 IT, lint y
  build). Reviewer: APPROVED (`progress/review_HU-01.md`). Trazabilidad en
  `progress/impl_HU-01.md`. Status → `done`.
- **Pendientes:** texto legal definitivo de `/privacidad` (lo provee el
  equipo); `docs/architecture.md` ("Estados del sistema") aún dice que
  "correo pendiente" aplica al recién registrado, lo que no rige hasta HU-25.

- 2026-10-27 — (orquestador, sdd:false) `docs/architecture.md`: el estado "Correo pendiente" ya no aplica al usuario recién registrado (correo simulado, HU-01 S5). `README.md`: nueva sección "Cómo levantar el proyecto para probarlo", con las pruebas manuales de HU-01.

- 2026-10-27 — (orquestador, sdd:false) Menos pedidos de permiso: `.claude/settings.json` permite comandos de solo lectura (`ls`, `cat`, `grep`, `head`, `tail`, `find`…), `./mvnw *`, `npm run *` y lectura de `~/.m2/repository` (agregado como directorio adicional). Los cuatro agentes tienen una sección "Comandos de shell": no anteponer `cd "<ruta>" &&`, preferir `Read`/`Grep`/`Glob`, sin bucles ni variables al leer archivos.

## 2026-10-27 — HU-02 Registro de arrendador (done)
- **Agente:** orquestador → explorador → spec_author → implementer → reviewer (dueña: Adriana Callupe).
- **Rama:** `feature/HU-02-registro-arrendador`.
- **Spec:** `specs/HU-02/` con R1–R44, DT-1…DT-16 y T1–T17. Explorador en
  `progress/explore_HU-02.md` (13 huecos). Decisiones de la dueña: nombre
  completo y teléfono obligatorio (9 dígitos, empieza con 9) en `usuario`;
  facturación = tipo y número de documento, razón social o nombre y dirección
  fiscal, en columnas cifradas separadas; validación completa de DNI (8
  dígitos) y RUC (11 dígitos, prefijo 10/15/17/20, dígito verificador módulo
  11); unicidad del documento con `hash_documento` (HMAC) e índice único, y un
  duplicado responde igual que un registro exitoso (anti-enumeración). D1
  (alcance CA3): nace con `correo_verificado = true` como HU-01, el aviso de
  verificar identidad va en la confirmación y el aviso tras login queda para
  HU-03. Spec (incluida D1) aprobado por Adriana Callupe el 2026-10-27.
- **Cambios:** `comun/cifrado/` (`CifradorCampos` AES-256-GCM con formato `v1:`,
  `HuellaCampos` HMAC-SHA256, `LlaveCifrado`, `ConvertidorCifrado`); llaves
  `CIFRADO_LLAVE` y `CIFRADO_LLAVE_HUELLA` (default vacío; llaves solo de
  prueba en `src/test/resources/config/`); `ReglaLimite` con grupo (cupo de 5/h
  compartido entre `/api/usuarios` y `/api/arrendadores`); `permitAll` para
  `POST /api/arrendadores`; migración `V1_3__perfil_arrendador.sql`;
  `cuentas/` (`TipoDocumento`, `EstadoIdentidad`, `ValidadorDocumento`,
  `PerfilArrendador`, `Usuario.registrarArrendador`, `PerfilArrendadorRepository`,
  `RegistroArrendadorService`, `ArrendadorController`, DTOs con `toString`
  enmascarado). Frontend: `Campo` con `tipo="select"`, `api/cuentas.js`
  `registrarArrendador`, `textosCuentas.js`, pantalla `/registro-arrendador` y
  enlaces desde `/` y `/registro`. Docs: `modelo-datos.md`, `architecture.md`
  (principio 7) y `frontend.md`.
- **Resultado:** `./init.sh` completo en verde: unitarias 71/71 (29 HU-01 + 42
  HU-02), IT 64/64 (29 HU-01 + 35 `ArrendadorControllerIT`, Testcontainers),
  lint y build OK. Reviewer: APPROVED (`progress/review_HU-02.md`). Trazabilidad
  R1–R44 en `progress/impl_HU-02.md`. Encabezado de D1 en `requirements.md`
  actualizado a "confirmada por Adriana Callupe el 2026-10-27" (observación 2).
  Status → `done`.
- **Pendientes (observaciones no bloqueantes del review):** descartar los
  `package-lock.json` ajenos (el de `3_frontend/` modificado y el vacío de la
  raíz) antes del PR; revisar la rama destino del PR (`develop` vs `master`);
  HTTPS y llaves de cifrado son del despliegue (DT-10); estético en `App.jsx:24`;
  posible IT de arranque sin llaves para R38.

## 2026-10-27 — README: llaves de cifrado para levantar el backend en local (sdd: false)

- **Motivo:** `./mvnw spring-boot:run` fallaba en local con
  `Falta CIFRADO_LLAVE o no es una llave de 32 bytes en base64`. Los tests traen
  llaves fijas propias, pero la app no tiene default (R38 de HU-02, a propósito).
- **Cambios:** `README.md` § "2. Backend": cada desarrollador genera sus propias
  llaves con `openssl rand -base64 32` y las guarda en `~/.bashrc` (Git Bash).
  Sin cambios en código ni en `application.properties`.

## 2026-10-28 — HU-03 Inicio y cierre de sesión (done)
- **Agente:** orquestador → explorador → spec_author → implementer → reviewer (dueño: Jorge Quispe).
- **Rama:** `feature/HU-03-inicio-cierre-sesion`.
- **Spec:** `specs/HU-03/` con R1–R36, DT-1…DT-15 y T1–T19. Explorador en
  `progress/explore_HU-03.md`. Spec aprobado por Jorge Quispe el 2026-10-28
  aceptando P1–P7 con los valores por defecto: el ADMIN no inicia sesión aún
  (llega con HU-06); token opaco en `Authorization: Bearer` guardado en
  `sessionStorage` y solo su SHA-256 en BD; bloqueo temporal por correo
  normalizado (5 fallos en ventana deslizante de 15 min, en memoria, se
  reinicia con login exitoso); mensajes genéricos "Correo o contraseña
  incorrectos." y "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
  aviso de identidad solo para `ARRENDADOR` con `NO_INICIADA`; inactividad
  deslizante de 30 min sin tope absoluto; sesiones concurrentes permitidas.
- **Cambios:** `comun/` (`TokenAleatorio`, `LimitadorIntentos.superaLimite/
  registrar/limpiar`, `CredencialesInvalidasException` y
  `DemasiadosIntentosException` con handlers 401/429 y mensajes en `ErrorDTO`,
  puerto `ValidadorSesion`, `UsuarioAutenticado`, `FiltroAutenticacionSesion`,
  `PuntoEntradaNoAutenticado`, `ConfiguracionSeguridad` STATELESS con CORS que
  admite `Authorization`); migración `V1_4__sesion.sql`; `cuentas/` (`Sesion`,
  `MotivoRechazoInicioSesion`, `Usuario.motivoRechazoInicioSesion`,
  `PerfilArrendador.AVISO_IDENTIDAD`, `SesionRepository`, `InicioSesionService`
  con hash señuelo, `SesionService` que implementa `ValidadorSesion`,
  `SesionController` con `POST /api/sesiones`, `GET /api/sesiones/actual` y
  `DELETE /api/sesiones/actual`; auditoría `LOGIN_EXITOSO`, `LOGIN_FALLIDO`,
  `LOGIN_BLOQUEADO` y `CIERRE_SESION`). Frontend: `api/tokenSesion.js`,
  `api/cliente.js` con Bearer y manejo de 401, `api/cuentas.js`, carpeta
  `sesion/` (`ProveedorSesion`, `useSesion`, `RutaProtegida`), pantallas
  `/iniciar-sesion` y `/panel`, encabezado de `App.jsx` con "Iniciar sesión" /
  "Mi panel" y "Cerrar sesión". Docs: `modelo-datos.md` y `frontend.md`.
- **Resultado:** `./init.sh` completo en verde (118 unitarias + 89 IT con
  Testcontainers, lint y build OK). Reviewer: APPROVED
  (`progress/review_HU-03.md`). Trazabilidad R1–R36 en `progress/impl_HU-03.md`.
  Status → `done`.
- **Pendientes (observaciones no bloqueantes del review):** `SesionService.validar`
  no revisa `usuario.estado`; HU-09 debe revocar las sesiones al suspender una
  cuenta. En `App.jsx` el error de red al cerrar sesión sigue visible al cambiar
  de ruta, y un 401 al cerrar sesión provoca dos navegaciones seguidas (estético).
  HTTPS y una sola instancia (contador en memoria) son del despliegue. Todo está
  sin commitear: el dueño revisa y abre el PR hacia `develop`.

## 2026-10-28 — HU-05 Envío de documentos de identidad del arrendador (done)
- **Agente:** orquestador → explorador → spec_author → implementer → reviewer (dueño: Kevin Chan).
- **Rama:** `feature/HU-05-documentos-identidad`.
- **Spec:** `specs/HU-05/` con R1–R42, DT-1…DT-17 y T1–T18. Explorador en
  `progress/explore_HU-05.md`. Spec aprobado por Kevin Chan el 2026-10-28
  aceptando P1–P16 con los valores por defecto: disco local
  (`ALMACENAMIENTO_DIRECTORIO`) detrás del Adapter `AlmacenamientoArchivos`;
  cifrado AES-256-GCM con `CIFRADO_LLAVE`; URL firmada propia
  `GET /api/archivos` (HMAC, 5 min, llave `ALMACENAMIENTO_LLAVE_FIRMA`); dos
  documentos obligatorios (`IDENTIDAD` y `PROPIEDAD`) en un solo envío; 5 MB
  inclusivo por archivo; JPG/PNG re-codificados sin metadatos (máx. 40 MP) y
  PDF sin cambios; el reenvío reemplaza filas y archivos; solo se envía desde
  `NO_INICIADA` o `RECHAZADA`; el arrendador ve sus documentos vigentes con
  URL firmada; acceso del admin diferido a HU-06; sin rate limiting ni correo;
  el `aviso` de sesión gana textos para `EN_REVISION` y `RECHAZADA`
  (reemplaza HU-03 R5 para esos estados); 403 para rol no arrendador o correo
  no verificado; retención fuera de alcance (HU-22); migración `V2_1`.
- **Cambios:** `comun/` (`AccesoDenegadoException`,
  `RecursoNoEncontradoException`, `ValidacionException` con handlers
  403/404/400 y `MaxUploadSizeExceededException`/`MultipartException`;
  `CifradorArchivos`; `FirmadorUrl` y `UrlFirmada`; `ValidadorArchivo` con
  magic bytes, tope de píxeles y re-codificación; `AlmacenamientoArchivos`,
  `AlmacenamientoLocal`, `ArchivosPrivados`; `ArchivoController` con
  `permitAll`); propiedades multipart/Tomcat/almacenamiento, `.env.example`
  y `.gitignore` (`2_Backend/almacenamiento/`); migración
  `V2_1__documento_identidad.sql`; `cuentas/` (`TipoDocumentoIdentidad`,
  `DocumentoIdentidad`, `PerfilArrendador.enviarDocumentos/
  puedeEnviarDocumentos/avisoIdentidad`, `DocumentoIdentidadRepository`,
  `findParaActualizar` con bloqueo pesimista, `IdentidadArrendadorService`,
  `IdentidadArrendadorController` con `POST` y `GET
  /api/arrendadores/actual/identidad`, DTOs `IdentidadDTO` y
  `DocumentoIdentidadDTO`; auditoría de cada carga y rechazo). Frontend:
  `Campo tipo="archivo"`, `EtiquetaEstado`, `cliente.js` con `FormData`,
  `cuentas.js` (`enviarDocumentosIdentidad`, `obtenerIdentidad`), pantalla
  `/panel/identidad` (`VerificarIdentidad.jsx`) y enlace desde `Panel.jsx`.
  Docs: `modelo-datos.md` y `frontend.md`.
- **Resultado:** `./init.sh` completo en verde (173 unitarias + 119 IT con
  Testcontainers, lint y build OK). Reviewer: APPROVED
  (`progress/review_HU-05.md`). Trazabilidad R1–R42 en `progress/impl_HU-05.md`.
  Status → `done`.
- **Pendientes (observaciones no bloqueantes del review):**
  1. (HU-06/HU-07) `ArchivoController` solo convierte `IllegalStateException`
     en 404; un `UncheckedIOException` de `AlmacenamientoLocal.leer` da 500
     genérico, y el design § 4 dice "cualquier fallo → 404". Unificarlo al
     reutilizar el componente.
  2. (UAT) `AlmacenamientoLocal` usa `ATOMIC_MOVE`: si el volumen de
     `ALMACENAMIENTO_DIRECTORIO` no admite movimientos atómicos, lanzará
     `AtomicMoveNotSupportedException` (500). Tenerlo en cuenta al configurarlo.
  - Además: probar a mano en UAT un archivo de más de 6 MB para confirmar el
    400 de R13 a través de Tomcat; avisar en el PR que DT-12 reemplaza HU-03 R5
    para `EN_REVISION` y `RECHAZADA`; avisar a Ariel Enriquez (HU-06) que
    `V2_1` ya está ocupada. Todo está sin commitear: el dueño revisa y abre el
    PR hacia `develop`.

## 2026-10-28 — HU-04 Recuperación de contraseña (done)
- **Agente:** orquestador → explorador → spec_author → implementer → reviewer (dueño: Eduardo Saldaña).
- **Rama:** `feature/HU-04-recuperacion-contrasena` (desde `develop` en `832d7a1`).
- **Spec:** `specs/HU-04/` con R1–R31, DT-1…DT-10 y T1–T15. Explorador en
  `progress/explore_HU-04.md`. Spec aprobado por Eduardo Saldaña el 2026-10-28.
  Decisiones del dueño: **D1** el notificador simulado escribe el enlace en el log
  solo con la propiedad de desarrollo `espacioya.correo.simulado.registrar-enlace`
  (apagada por defecto, nunca en UAT); **D2** el puerto se amplía en
  `cuentas/service/NotificadorCorreo` (HU-25 lo moverá a `comun/correo`); **D3** al
  superar 3 solicitudes por correo y hora se responde el mismo 202 sin emitir enlace
  (sin 429); **D4** tras restablecer no se inicia sesión: la pantalla lleva al login
  con un aviso. Supuestos S1–S12 aceptados tal cual: **S1** `V1_5__token.sql` +
  `spring.flyway.out-of-order=true`; **S2** sin enlace para cuentas no habilitadas
  (regla de HU-03), también al validar y confirmar; **S3** tabla `token` + `creado_en`,
  `hash_token` único con CHECK hex, `ON DELETE CASCADE`; **S4** uso único con UPDATE
  condicional y anulación de pendientes al pedir otro y al restablecer; **S5** token
  en `#token=` y en el cuerpo de un `POST`, forma validada antes del hash, un solo
  mensaje de enlace inválido, endpoint de validación al abrir; **S6** mensajes y
  acciones de auditoría (la validación al abrir no se audita); **S7** revocar
  sesiones en la misma transacción y reiniciar el contador de login; **S8** solo la
  política de HU-01; **S9** cuentan las solicitudes válidas en forma, exista o no la
  cuenta; **S10** diferencia de tiempo aceptada hasta HU-25; **S11** límite en
  memoria (una instancia); **S12** sin límite por IP.
- **Cambios:** `comun/seguridad` (`TokenAleatorio.tieneFormato`, tres `permitAll`);
  migración `V1_5__token.sql`; `application.properties` (`out-of-order`, propiedad de
  D1) y `.env.example` (`CORREO_SIMULADO_REGISTRAR_ENLACE=false`); `cuentas/`
  (`Token`, `TipoToken`, `MotivoRechazoRecuperacion`, `Usuario.cambiarContrasena`,
  `TokenRepository`, `SesionRepository.revocarTodas`, `NotificadorCorreo.
  notificarRecuperacion` y simulado con la propiedad, `InicioSesionService.
  PREFIJO_CLAVE` de paquete, `RecuperacionContrasenaService`, tres DTO con
  `toString` enmascarado, `RecuperacionContrasenaController` con `POST
  /api/recuperaciones`, `/validaciones` y `/confirmaciones`; auditoría
  `RECUPERACION_SOLICITADA`, `RECUPERACION_NO_EMITIDA`, `RECUPERACION_RECHAZADA` y
  `CONTRASENA_RESTABLECIDA`). Dobles `NotificadorFalso` de `UsuarioControllerIT` y
  `ArrendadorControllerIT` con el método nuevo. Frontend: `api/cuentas.js` (tres
  funciones con `autenticada: false`), `textosCuentas.js`, pantallas
  `/recuperar-contrasena` (`RecuperarContrasena.jsx`) y `/restablecer-contrasena`
  (`RestablecerContrasena.jsx`), enlace "¿Olvidaste tu contraseña?" en
  `IniciarSesion.jsx` y rutas en `App.jsx`. Docs: `modelo-datos.md` (`token`).
- **Resultado:** `./init.sh` completo en verde (204 unitarias + 147 IT con
  Testcontainers, incluida una prueba concurrente de dos confirmaciones; lint y
  build OK). Reviewer: APPROVED (`progress/review_HU-04.md`). Trazabilidad R1–R31
  en `progress/impl_HU-04.md`. Status → `done`.
- **Pendientes (observaciones no bloqueantes del review y avisos):**
  1. (HU-25) `RecuperacionContrasenaService.solicitar` notifica dentro de la
     transacción: con el proveedor real, un fallo daría 500 solo a cuentas
     existentes y revelaría que existen (STRIDE I). HU-25 debe capturar el fallo o
     enviar después del commit sin cambiar el 202.
  2. Dos solicitudes simultáneas de la misma cuenta pueden dejar dos tokens
     vigentes (`anularPendientes` y `save` no se serializan). Riesgo bajo: ambos
     son de un solo uso y de 30 minutos.
  3. (UX, caja negra en UAT) En `RestablecerContrasena.jsx`, un error de red al
     validar muestra la vista de enlace inválido con "No pudimos conectar…" y el
     enlace "Solicitar un enlace nuevo"; puede confundir.
  - **Aviso al equipo:** desde este PR rige `spring.flyway.out-of-order=true`
    (S1): Flyway aplica migraciones de número menor que lleguen tarde. La
    numeración por sprint sigue siendo obligatoria y una migración subida a
    `develop` no se edita. Avisar a Ariel Enriquez (HU-06) antes de numerar su
    `V2_n`.
  - **UAT:** `CORREO_SIMULADO_REGISTRAR_ENLACE` debe quedar en `false` (D1); el
    enlace solo se lee del log en una máquina de desarrollo. El límite de
    solicitudes vive en memoria: una sola instancia del backend (S11).
  - Todo está sin commitear: el dueño revisa y abre el PR hacia `develop`.

- 2026-10-28 — (orquestador, sdd:false) `docs/conventions.md`: nueva sección "Migraciones que llegan tarde (`out-of-order`)" con la regla de `spring.flyway.out-of-order=true` (S1 de HU-04), cómo elegir el número, qué hacer si choca y que una migración no depende de otra de número mayor. Pedido por Eduardo Saldaña.
