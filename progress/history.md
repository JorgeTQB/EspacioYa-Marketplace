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
- 2026-10-27 — (orquestador, sdd:false) Ajuste: la regla "Comandos de shell" pasa también a `CLAUDE.md` (la sesión principal no lee `orquestador.md`). Backend se corre como `./2_Backend/mvnw -f 2_Backend/pom.xml …` y frontend como `npm --prefix 3_frontend …`, sin `cd` ni `timeout N`; ambos permitidos en `.claude/settings.json`.

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

- 2026-10-28 — (orquestador, sdd:false) `./mvnw spring-boot:run` fallaba en local con `Falta ALMACENAMIENTO_LLAVE_FIRMA o no es una llave de 32 bytes en base64`: HU-05 hizo obligatoria esa llave, pero el README solo documentaba las dos de cifrado de HU-02 y `2_Backend/.env.example` decía que en local no hacía falta nada. `README.md` (paso "2. Backend"): tabla con las tres llaves, comando para generar la de firma, aviso de no duplicar variables en `~/.bashrc` y qué hacer si ya se tenían las de cifrado. `2_Backend/.env.example`: aclara que las tres llaves también son obligatorias en local. Verificado: con la llave definida el backend llega a `Started EspacioYaApplication`. Pedido por SebGarate.

- 2026-10-28 — (orquestador, sdd:false) `init.sh`, bloque 1: nuevo chequeo de `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA` y `ALMACENAMIENTO_LLAVE_FIRMA` (definidas, 32 bytes en base64 y distintas entre sí, el mismo criterio que `LlaveCifrado`, `CifradorCampos` y `FirmadorUrl`). Es `[WARN]` y no `[FAIL]`: las pruebas traen sus llaves en `src/test/resources`, así que solo avisa que `./mvnw spring-boot:run` no arrancará. Nunca muestra los valores. Probado con 5 casos (ninguna, falta la de firma, 16 bytes, repetida, todas bien) y con `./init.sh --rapido` (exit 0, 204 unitarias OK). Pedido por SebGarate.

- 2026-10-28 — (orquestador, sdd:false) Subagente `spec_author` renombrado a `spec-author`: Claude Code no lo registraba (era el único de `.claude/agents/` con guion bajo en `name:`; los nombres de subagente admiten solo minúsculas, números y guiones), así que `subagent_type: "spec_author"` fallaba con "Agent type not found". `git mv .claude/agents/spec_author.md .claude/agents/spec-author.md` y referencias actualizadas en `CLAUDE.md`, `AGENTS.md`, `CHECKPOINTS.md`, `README.md`, `docs/specs.md`, `.claude/agents/orquestador.md` e `init.sh`. Los registros históricos (`progress/` y `specs/` de HU-01–05) quedan sin tocar. Hay que reiniciar la sesión de Claude Code para que cargue el agente. Pedido por Sebastian Garate.

- 2026-10-28 — (orquestador, sdd:false) `docs/requerimientos.md` §8, fila "Almacenamiento privado cifrado con URL firmada": HU-07 ya no reutiliza el cifrado ni la URL firmada para las fotos de espacios. Las fotos son públicas (no sensibles): se guardan sin cifrar en el adaptador de disco, con URL estable y cacheable; las de espacios en borrador solo las ve su dueño. Se mantiene la validación y re-codificación de `ValidadorArchivo` (elimina EXIF/GPS). Simplifica HU-10 y HU-11 (no firman URLs en cada consulta). Decisión de Sebastian Garate al revisar P4 del spec de HU-07.

## 2026-10-28 — HU-07 Publicar y editar un espacio (done)
- **Agente:** orquestador → explorador → spec-author → implementer → reviewer (dueño: Sebastian Garate).
- **Rama:** `feature/HU-07-publicar-editar-espacio` (desde `develop` en `a90508b`).
- **Spec:** `specs/HU-07/` con R1–R55, DT-1…DT-19 y T1–T16 (más T1b, T1c, T9b y
  T11b). Explorador en `progress/explore_HU-07.md`. Spec aprobado por Sebastian
  Garate el 2026-10-28. Decisiones del dueño: **P4** fotos de espacios públicas,
  guardadas sin cifrar detrás del adaptador (`ArchivosPublicos`, clave
  `espacios/<UUID>`), con URL estable `GET /api/fotos/{uuid}` (sin URL firmada);
  las de un `PUBLICADO` las ve cualquiera (`Cache-Control: public, max-age=3600`)
  y las de un borrador u otro estado solo su dueño con sesión (`no-store`); el
  resto recibe el mismo 404 genérico. Se mantiene `ValidadorArchivo` (5 MiB,
  magic bytes, 40 MP, re-codificación sin EXIF/GPS). **P5** `descripcion`
  obligatoria de 10 a 2000 caracteres tras recortar. P1–P16 restantes aceptadas
  con los valores por defecto: zona como lista cerrada (tabla `zona`, 43 distritos
  de Lima + 7 del Callao), una foto por solicitud y hasta 5, al menos 1 foto para
  publicar, editar un `PUBLICADO` lo deja `PUBLICADO`, interfaz `CuentaService`
  en `cuentas`, precio de reservas pagadas a cargo de HU-12 (P13), migración `V2_2`.
- **Cambios:** `comun/` (`web/ArchivoMultipart` compartido con
  `IdentidadArrendadorController`; `almacenamiento/ArchivosPublicos`; `permitAll`
  solo para `GET /api/fotos/*` en `ConfiguracionSeguridad`); `cuentas/`
  (`CuentaService`, `CuentaServiceImpl`, `HabilitacionArrendadorDTO`); migración
  `V2_2__espacio.sql` (`zona` con 50 filas, `espacio` con `precio_hora
  NUMERIC(8,2)`, `foto_espacio` con CHECK de orden 1–5 y UNIQUE diferido); módulo
  `espacios/` (dominio `Espacio`, `FotoEspacio`, `Zona`, `EstadoEspacio`,
  `TipoEspacio`, `DatosEspacio`; repositorios con bloqueo pesimista; DTO `record`;
  `EspacioService`, `FotoEspacioService`; `EspacioController`, `ZonaController`,
  `FotoEspacioController`, `FotoPublicaController`; auditoría de creación,
  edición con campos cambiados, publicación, foto agregada/quitada/rechazada).
  Frontend: `Campo tipo="textarea"`, `Tarjeta`, estados de `Espacio` en
  `EtiquetaEstado`, `peticionArchivo` en `api/cliente.js`, `api/espacios.js`,
  `textosEspacios.js`, pantallas `MisEspacios`, `EditarEspacio` y
  `FotoDelEspacio` (blob con `Bearer`), enlace en `Panel.jsx` y rutas
  `/panel/espacios`, `/panel/espacios/nuevo` y `/panel/espacios/:id`. Docs:
  `modelo-datos.md`, `frontend.md`, `architecture.md` (fila Archivos y excepción
  de las fotos en "Qué NO hacer").
- **Desvíos aceptados por el reviewer:** `Cache-Control` como texto literal
  (`public, max-age=3600` / `no-store`); zonas ordenadas con `Collator` es-PE en
  Java en vez de `ORDER BY`; `errorDe` y `avisarSiPerdioSesion` extraídos en
  `api/cliente.js` sin cambio de conducta; `<EditarEspacio key="nuevo" />` para
  remontar al pasar a `:id`; casos con `..` de R55 probados como `a..b` (un
  segmento `..` lo corta antes el firewall de Spring Security con 400 genérico).
- **Corrección tras la revisión (observación 1):** un JSON con `"capacidad": 20.7`
  se aceptaba truncado a `20` (201), contra R4 ("entero de 1 a 100 000").
  Corregido solo en `FichaEspacioDTO`: el componente `capacidad` usa el
  deserializador `FichaEspacioDTO.SoloEntero`, que acepta únicamente un número JSON
  entero; un decimal, texto (`"20"`), booleano, objeto, arreglo o entero fuera de
  `int` queda `null` y `@NotNull` responde 400 con el campo `capacidad` y el
  mensaje de R4. Sin cambios en la configuración global de Jackson ni en el
  frontend (ya muestra el error por campo). Tests: `EspacioControllerIT.
  capacidadNoEntera` (crear y editar, fila intacta) y `FichaEspacioDTOTest.
  capacidadDesdeJson`; trazabilidad de R4 actualizada en `design.md`.
- **Resultado:** `./init.sh` completo en verde tras la corrección (273 unitarias +
  196 IT con Testcontainers, lint y build OK; único `[WARN]` las llaves locales
  ausentes, previo a HU-07). Reviewer: APPROVED (`progress/review_HU-07.md`).
  Trazabilidad R1–R55 en `progress/impl_HU-07.md`. Status → `done`.
- **Pendientes (observaciones no bloqueantes del review):**
  2. (HU-09) `FotoEspacioService.ver` no oculta las fotos de un arrendador
     `SUSPENDIDA` (`docs/architecture.md`: "sus espacios no se muestran"). Fuera de
     los R de HU-07: avisar a Jorge Quispe, dueño de HU-09, para que lo cubra al
     implementar la suspensión.
  3. `FotoEspacioService.registrar` y `quitar` llaman a `espacio.tocar(...)`, que
     actualiza `actualizado_en` al agregar o quitar fotos. El spec no lo pide ni lo
     prohíbe; reflejarlo en `specs/HU-07/design.md` si se vuelve a tocar.
  - Todo está sin commitear: el dueño revisa y abre el PR hacia `develop`.
- 2026-10-28 — HU-07, observación 3 del review: documentado en `specs/HU-07/design.md` (DT-20, `Espacio.tocar` y flujos de agregar/quitar foto) que agregar o quitar una foto actualiza `espacio.actualizado_en`. Sin cambios de código, tests, requirements ni tasks.

## 2026-10-28 — HU-06 Revisión de identidad de arrendadores (done)
- **Agente:** orquestador → explorador → spec_author → implementer → reviewer (dueño: Ariel Enriquez).
  Ariel Enriquez trabajó desde la laptop de Eduardo Saldaña (su máquina no estaba disponible), a pedido suyo.
- **Rama:** `feature/HU-06-revision-identidad` (desde `develop` en `02a83b6`).
- **Spec:** `specs/HU-06/` con R1–R45, DT-1…DT-13 y T1–T20. Explorador en `progress/explore_HU-06.md`
  (15 decisiones abiertas). Aprobado por Ariel Enriquez el 2026-10-28. Decisiones del dueño: **D1** el admin inicia
  sesión por el mismo `POST /api/sesiones` solo desde una IP permitida (si no, el 401 genérico y auditoría
  `motivo=IP_NO_PERMITIDA`) y sigue sin recuperar contraseña hasta HU-17 (reemplaza HU-03 R13); **D2** filtro del
  backend en `comun/seguridad` sobre `/api/admin/**` con `ADMIN_IPS_PERMITIDAS` (CIDR, local `127.0.0.1,::1`, vacía =
  nadie entra), reutilizable por HU-09 y HU-27; **D3** IP real detrás de CloudFront/ALB por configuración de Tomcat
  (`server.forward-headers-strategy=native` + `PROXIES_CONFIANZA`), sin leer `X-Forwarded-For` a mano; **D4** cuentas
  admin por un `ApplicationRunner` de `cuentas` activado solo con `ADMIN_CREAR=true`, credenciales por variables de
  entorno, política de HU-01, bcrypt y auditoría. Supuestos S1–S16 aceptados tal cual: tabla `revision_identidad` en
  `administracion` con estado en `perfil_arrendador` y todo en una transacción (S1); 4 métodos nuevos en
  `CuentaService` con rol `ADMIN` validado en ambos módulos, 404 / 400 *ya revisada* (S2); correos de aprobación y
  rechazo en `NotificadorCorreo`, después del commit (S3); cola por `documento_identidad.subido_en` y desempate por id
  (S4); datos completos con `no-store` (S5); cola resumida + expediente sin paginación (S6); rutas `/api/admin/` con
  dos `POST` de decisión que responden 201 (S7); `GET /api/archivos` sin filtro de IPs y error de E/S → 404 (S8);
  `Modal` en el kit (S9); solo cuentas `ACTIVA` (S10); migración `V2_3` (S11); el filtro responde 403 *acceso
  denegado* (S12) y no audita (S13); proxies vacíos por defecto (S14); el comando no modifica cuentas existentes y
  cierra la app (S15); auditoría `decision=…;revision=…` sin motivo (S16).
- **Cambios:** `comun/seguridad` (`IpsPermitidasAdmin`, `FiltroIpsAdmin` antes de `FiltroAutenticacionSesion`);
  `comun/almacenamiento/ArchivoController` (`UncheckedIOException` → 404); `cuentas/` (`Usuario.registrarAdmin`,
  `esAdmin`, `motivoRechazoRecuperacion`; `IP_NO_PERMITIDA`; `PerfilArrendador.aprobarIdentidad/rechazarIdentidad`;
  `PerfilArrendadorRepository.findEnRevision`; `ArrendadorEnRevisionDTO`, `ExpedienteIdentidadDTO`; 4 métodos de
  `CuentaService` con bloqueo pesimista y aviso `afterCommit`; IP del admin en `InicioSesionService`; regla de
  recuperación separada en `RecuperacionContrasenaService`; 2 avisos de identidad en `NotificadorCorreo` y el simulado;
  `CreacionAdminService` + `CreadorAdmin`); módulo nuevo `administracion/` (`RevisionIdentidad`,
  `DecisionIdentidad`, repositorio, `RevisionIdentidadService`, `MotivoRechazoDTO`, `RevisionIdentidadDTO`,
  `RevisionIdentidadController`); migración `V2_3__revision_identidad.sql`; `application.properties` (IPs, proxies,
  comando admin). Frontend: `Modal` en el kit, `api/administracion.js`, `pages/administracion/ColaIdentidades.jsx` y
  `RevisarIdentidad.jsx`, textos y enlace de `ADMIN` en `Panel.jsx`, rutas `/admin/identidades` y
  `/admin/identidades/:id`. Docs: `modelo-datos.md`, `architecture.md`, `frontend.md`, `requerimientos.md` (§9-4
  resuelto), `README.md` (comando de creación de admin con `read -s`, IPs y proxies para UAT), `2_Backend/.env.example`.
  Tests de HU-03 actualizados por D1: `InicioSesionServiceTest.adminNoPermitido`,
  `SesionControllerIT.rechazosConCuerpoIdentico` (detalle `IP_NO_PERMITIDA`) y `UsuarioTest.adminNoPermitido`.
- **Desvíos aceptados por el reviewer:** el caso de R5 (lista de IPs vacía) vive en `comun/seguridad/FiltroIpsAdminIT`
  y no como `@Nested` del IT del controlador (un `@Nested` con otra configuración inyecta la clase externa con el
  contexto del anidado); `FiltroIpsAdmin` usa `PathPatternRequestMatcher` para que una ruta codificada no lo esquive;
  `IpsPermitidasAdmin` solo acepta literales (sin DNS).
- **Resultado:** `./init.sh` completo en verde (329 unitarias + 225 IT con Testcontainers, lint y build OK; único
  `[WARN]` las llaves locales ausentes, previo a HU-06). Reviewer: APPROVED (`progress/review_HU-06.md`), sin cambios
  requeridos. Trazabilidad R1–R45 en `progress/impl_HU-06.md`. Status → `done`.
- **Pendientes (observaciones no bloqueantes del review y avisos):**
  1. `specs/HU-03/requirements.md` (R13) sigue diciendo que el `ADMIN` recibe siempre el 401, y
     `SesionControllerIT.rechazosConCuerpoIdentico` conserva "HU-03 … R13" en su `@DisplayName` por la trazabilidad
     que exige `init.sh`. Anotar en ese R13 "Reemplazado por HU-06 R1–R4 (solo aplica desde una IP no permitida)".
  2. `specs/HU-06/design.md` § 3: la tabla tiene dos filas de encabezado; la segunda se ve como fila de datos
     (cosmético).
  3. `CreacionAdminService.crear`: si dos comandos crean el mismo admin a la vez, PostgreSQL/Hibernate escriben en el
     log el detalle de la violación de unicidad con el correo. Aceptable; tenerlo en cuenta al revisar logs de UAT.
  - **Avisar a Jorge Quispe** (dueño de HU-03 y de HU-09): HU-03 R13 queda reemplazado por D1 (el admin entra desde
    una IP permitida; `MotivoRechazoInicioSesion.IP_NO_PERMITIDA`), y D3 cambia la IP de toda la aplicación: con
    `PROXIES_CONFIANZA` configurado, la IP de auditoría y del límite de intentos pasa a ser la del cliente real. HU-09
    debe poner sus rutas bajo `/api/admin/` para heredar el filtro de IPs.
  - **Variables de entorno nuevas para UAT:** `ADMIN_IPS_PERMITIDAS` (IPs o CIDR de quienes administran; vacía = nadie
    entra al panel) y `PROXIES_CONFIANZA` (regex de las IPs internas del ALB/CloudFront; vacía = se usa la IP de la
    conexión, que detrás del balanceador sería la del balanceador). `ADMIN_CREAR`, `ADMIN_CORREO` y
    `ADMIN_CONTRASENA` solo para correr una vez el comando de creación de admin (README).
  - Antes del merge: si `develop` ya tiene una `V2_3`, renombrar `V2_3__revision_identidad.sql` a la siguiente libre.
  - Todo está sin commitear: el dueño revisa y abre el PR hacia `develop`.

- 2026-10-28 — Arnés: corregido frontmatter de `.claude/agents/spec-author.md` (la `description` contenía `"sdd": true` sin comillas envolventes → YAML inválido y el tipo `spec-author` no se cargaba). Se envolvió la descripción en comillas simples.

## 2026-10-28 — HU-08 Disponibilidad del espacio (done)

- **Dueño:** Sebastian Garate · **Tester:** Adriana Callupe · **Rama:** `feature/HU-08-disponibilidad-espacio`
- **Agentes:** orquestador → explorador (`progress/explore_HU-08.md`) → spec-author (`specs/HU-08/`, R1–R45, T1–T15,
  P1–P12, DT-1…DT-14) → aprobación de Sebastian Garate (2026-10-28, valores por defecto de P1–P12 y DT-1…DT-14) →
  implementer → reviewer (APPROVED) → implementer (cierre).
- **Cambios:** backend: `comun/configuracion/ZonaHoraria`; dominio de `espacios` (`FranjaHoraria`, `ReglasHorario`,
  `VentanaReserva`, `EstadoBloque`, `Bloque`, `CalculoDisponibilidad`, `HorarioSemanal`, `BloqueoFecha`);
  `HorarioSemanalRepository`, `BloqueoFechaRepository`; puerto `service/puertos/ReservasDelEspacio` + `BloqueReservado`
  (contrato de bloqueo DT-10, lo implementará HU-12); `DisponibilidadService` + `Impl` (`ObjectProvider` del puerto),
  `CalendarioEspacioService`; DTO `HorarioDTO`, `FranjaDTO`, `BloqueoFechaDTO`, `BloqueosDTO`, `DisponibilidadDiaDTO`,
  `BloqueDTO`; `CalendarioEspacioController` (6 endpoints); migración `V2_4__disponibilidad.sql`. Tests: 4 de dominio,
  `ZonaHorariaTest`, 2 de DTO, `DisponibilidadServiceImplTest`/`IT`, `CalendarioEspacioServiceTest`,
  `CalendarioEspacioControllerIT` (36 casos); en `EspaciosControllerSoporte` el doble `ReservasDelEspacioFalso` y su
  limpieza. Frontend: `SelectorBloques` nuevo en el kit, `Campo` con `min`/`max`, `api/espacios.js`,
  `textosEspacios.js`, página `DisponibilidadEspacio`, ruta en `App.jsx` y enlaces en `MisEspacios` y `EditarEspacio`.
  Docs: `modelo-datos.md`, `architecture.md` (puerto nuevo), `frontend.md`.
- **Cierre:** aplicada la recomendación opcional 2 del review: `DisponibilidadServiceImplIT.prepararBase()` limpia las
  mismas tablas que `EspaciosControllerSoporte.prepararBase()` (`foto_espacio`, `documento_identidad`, `sesion`,
  `auditoria`), para no depender del orden de las IT.
- **Desvíos aceptados:** en `BAJA`/`OCULTO` solo se deshabilitan los botones (el kit no tiene `deshabilitado` en
  `Campo`); `DisponibilidadServiceImplIT` usa su propio contexto sin doble del puerto (prueba R21 como en producción);
  `ErrorDTO` sin campo trae `errores: []`.
- **Resultado:** `./init.sh` completo en verde (385 unitarias + 262 IT con Testcontainers, lint y build OK). Reviewer:
  APPROVED (`progress/review_HU-08.md`). Trazabilidad R1–R45 en `progress/impl_HU-08.md`. Status → `done`.
- **Pendientes:**
  1. Confirmar con Eduardo Saldaña (dueño de HU-12) la firma del puerto `ReservasDelEspacio` / `BloqueReservado`
     (DT-7), antes de que HU-12 lo implemente.
  2. Antes del merge: si otra migración `V2_4` llega antes a `develop`, renombrar `V2_4__disponibilidad.sql` a la
     siguiente versión libre (`docs/conventions.md`).
  3. Revisar con Adriana Callupe (dueña de HU-11) los props de `SelectorBloques` (`docs/frontend.md`) antes de su
     spec.
  - Todo está sin commitear: el dueño revisa y abre el PR hacia `develop`.

- 2026-10-28 — (orquestador, sdd:false) Guía de integración de la disponibilidad para HU-10, HU-11, HU-12 y HU-21:
  `docs/architecture.md`, nueva subsección "Disponibilidad: qué deja HU-08 y qué falta" (piezas reutilizables y los
  huecos que cada historia resuelve en su `design.md`: método en lote para HU-10, ruta pública para HU-11, bloqueo del
  espacio sin filtrar por dueño para HU-12, porque `findParaActualizar` exige ser dueño y es un repositorio de
  `espacios`); `docs/frontend.md` § Bloques horarios: cómo armar el prop `bloques` de `SelectorBloques`. Pedido por
  Sebastian Garate.

## 2026-10-29 — HU-09 Suspensión y reactivación de cuentas (done)

- **Dueño:** Jorge Quispe · **Tester:** Kevin Chan · **Rama:** `feature/HU-09-suspension-reactivacion` (desde
  `develop` en `39a77aa`)
- **Equipo:** Jorge Quispe trabajó desde la laptop de Eduardo Saldaña, con su permiso (confirmado en la sesión).
- **Agentes:** orquestador → explorador (`progress/explore_HU-09.md`, 15 decisiones abiertas) → spec-author
  (`specs/HU-09/`, R1–R47, DT-1…DT-11, T1–T18) → aprobación de Jorge Quispe (2026-10-29) → implementer → reviewer
  (APPROVED, `progress/review_HU-09.md`) → implementer (cierre).
- **Decisiones del dueño:** D1 los espacios del arrendador no `ACTIVA` se ocultan consultando `cuentas` al leer
  (`CuentaService.cuentaActiva` / `arrendadoresNoActivos`, sin rol, sin columna en `espacio`; `espacio.estado` no
  cambia); D2 solo se suspenden o reactivan `USUARIO` y `ARRENDADOR` (nunca un `ADMIN`, tampoco uno mismo; `ELIMINADA`
  tampoco); D3 al suspender se revocan todas las sesiones y `SesionService.validar` rechaza sesiones de cuentas no
  `ACTIVA` (cambio en HU-03); D4 también se avisa por correo al reactivar (después del commit, sin correo ni motivo en
  el log). Supuestos S1–S15 aceptados tal cual: rutas `/api/admin/cuentas` (GET `?q=`, GET `/{id}`, POST
  `/{id}/suspension` y `/{id}/reactivacion` → 201); tabla `suspension_cuenta` en `administracion`; bloqueo
  `PESSIMISTIC_WRITE` de `usuario`; búsqueda "contiene" de 3–100 caracteres, máximo 20 con `hayMas`; `ADMIN` y
  `ELIMINADA` visibles sin acciones (400 *no gestionable*); búsqueda y detalle sin auditar; auditoría
  `accion=…;registro=…` sin motivo; la suspensión no toca reservas; tokens de recuperación y limitador sin cambios;
  `exigirAdmin` duplicado; migración `V3_1__suspension_cuenta.sql`; pantallas sin componentes nuevos; "espacios
  ocultos" probado sobre fotos y `horasLibres`; caché de fotos hasta 1 h aceptada.
- **Cambios:** backend `cuentas`: `Usuario` (`esGestionablePorAdmin`, `puedeSuspenderse`, `puedeReactivarse`,
  `suspender`, `reactivar`), `UsuarioRepository` (`findParaActualizar`, `buscar` con `escape` y `Limit`,
  `findIdsArrendadoresNoActivos`), 4 DTO, `CuentaService`/`Impl` (6 métodos, inyecta `SesionRepository`),
  `SesionService.validar`, `NotificadorCorreo` + simulado (2 avisos). `espacios`: `FotoEspacioService.ver` y
  `DisponibilidadServiceImpl.horasLibres` filtran por arrendador `ACTIVA`. `administracion`: `AccionSuspension`,
  `SuspensionCuenta`, `SuspensionCuentaRepository`, 4 DTO, `CuentaAdminService`, `CuentaAdminController`; migración
  `V3_1__suspension_cuenta.sql`. Tests nuevos: `CuentaAdminControllerIT` (23), `CuentaAdminServiceTest`,
  `SuspensionCuentaTest`, `MotivoSuspensionDTOTest`, `CuentaServiceImplIT`; casos agregados en `UsuarioTest`,
  `CuentaServiceImplTest`, `SesionServiceTest`, `NotificadorCorreoSimuladoTest`, `FotoEspacioServiceTest`,
  `FotoPublicaControllerIT`, `DisponibilidadServiceImplTest`/`IT`; los 4 `NotificadorFalso` implementan los avisos
  nuevos. Frontend: `EtiquetaEstado` (+`ACTIVA`, `SUSPENDIDA`, `ELIMINADA`), `NOMBRES_ROL`, `api/administracion.js`
  (+4), páginas `BuscarCuentas` y `DetalleCuenta`, enlace en `Panel`, rutas en `App`. Docs: `architecture.md` (métodos
  de `CuentaService`, regla de filtro para HU-10/11/12, contrato de `horasLibres`, `validar`), `modelo-datos.md`
  (`suspension_cuenta`), `frontend.md` (`EtiquetaEstado`).
- **Resultado:** `./init.sh` completo en verde (427 unitarias + 290 IT con Testcontainers, lint y build OK).
  Trazabilidad R1–R47 en `progress/impl_HU-09.md`. Reviewer: APPROVED sin cambios requeridos. Status → `done`.
- **Pendientes:**
  1. (Reviewer, obs. 1) `CuentaServiceImpl.suspenderCuenta` lanza `IllegalArgumentException` con un motivo nulo o en
     blanco: por la API no ocurre (`@Valid`), pero otro llamador recibiría un 500. Si HU-27 lo reutiliza, cambiarlo por
     una `ReglaNegocioException` con el campo `motivo`.
  2. (Reviewer, obs. 2) En `DetalleCuenta.jsx`, con el `Modal` de reactivar abierto se ven a la vez el botón primario
     de la página y el de confirmación del modal; es lo que fija design § 10 y el patrón de HU-06 (aceptado).
  3. (Reviewer, obs. 3) Antes del merge: si HU-10, HU-11 o HU-12 llegan antes a `develop` con una `V3_1`, renombrar
     `V3_1__suspension_cuenta.sql` a la siguiente libre (S12).
  - **Avisar a Sebastian Garate (HU-10), Adriana Callupe (HU-11) y Eduardo Saldaña (HU-12)** (D1): sus consultas deben
    ocultar los espacios de arrendadores no `ACTIVA` con `CuentaService.arrendadoresNoActivos()` (omitiendo el
    `not in` si el conjunto está vacío) o `CuentaService.cuentaActiva(id)`; `DisponibilidadService.horasLibres` ya
    devuelve vacío para ellos. Escrito en `docs/architecture.md` § Puertos entre módulos.
  - Todo está sin commitear: el dueño revisa y abre el PR hacia `develop`.

- 2026-10-29 · orquestador (Sebastian Garate, HU-10): `docs/requerimientos.md` §9 punto 9 — límite de búsqueda/ficha/filtros por IP resuelto por el dueño (120/min, cupo compartido HU-10/11/23, configurable; HU-10 extiende `ReglaLimite` a patrones de ruta).
- 2026-10-29 · orquestador (Sebastian Garate, HU-10): `docs/architecture.md` nota para HU-12 sobre `ReservasDelEspacio.bloquesReservadosDelDia`; `docs/requerimientos.md` §9.9 apunta a `application.properties`.

## 2026-10-29 — HU-10 Búsqueda por tipo, zona y fecha (done)

- **Dueño:** Sebastian Garate · **Rama:** `feature/HU-10-busqueda-espacios` (desde `develop`)
- **Agentes:** orquestador → explorador (`progress/explore_HU-10.md`) → spec-author (bloqueado por la cifra del rate
  limiting; el dueño la resolvió en `docs/requerimientos.md` §9.9: 120 peticiones/IP/min, ventana deslizante, cupo
  compartido HU-10/11/23, 429 genérico, configurable en `application.properties`) → spec-author (`specs/HU-10/`,
  R1–R47, D1–D12, T1–T20) → aprobación del dueño (2026-10-29; D2 y D4 discutidas, se mantiene orden por precio
  ascendente; selector de orden queda como idea para HU-23) → implementer (`progress/impl_HU-10.md`) → reviewer
  (`progress/review_HU-10.md`: APPROVED, sin cambios requeridos).
- **Cambios:** `comun/limite`: `ReglaLimite` pasa a `final class` con `PathPattern` (cierra la evasión con rutas
  codificadas) y `FiltroLimiteIntentos` descuenta un intento por grupo; `ConfiguracionSeguridad` con `permitAll` para
  `GET /api/espacios` y `GET /api/zonas` (reemplaza HU-07 R43 solo para `/api/zonas`, D2). `espacios`: consultas en
  lote en los repositorios, `DisponibilidadService.conBloquesLibres` (sin N+1), método default
  `ReservasDelEspacio.bloquesReservadosDelDia` (aviso a HU-12 en `docs/architecture.md`), `CatalogoEspaciosService`/
  `Impl` y `EspacioPublicado`. Módulo nuevo `busqueda`: `BusquedaController`, `LimiteCatalogo`, `ReglasLimiteBusqueda`,
  `BusquedaService`, `BusquedaEspacios`, `BusquedaBase`, `CriteriosBusqueda`, DTO `ResultadoBusquedaDTO` y
  `ResultadoEspacioDTO`. Migración `V3_2__indices_busqueda.sql` (índice `ix_espacio_busqueda`). Propiedades del cupo
  en `application.properties` y `.env.example` (comentadas). Tests nuevos: `BusquedaControllerIT`, `LimiteCatalogoIT`,
  `LimiteCatalogoTest`, `BusquedaServiceTest`, `BusquedaBaseTest`, `FiltroLimiteIntentosTest`,
  `CatalogoEspaciosServiceImplTest`; ampliados `ReglaLimiteTest` (3 de HU-02 intactos), `DisponibilidadServiceImplTest`/
  `IT`, `ZonaControllerIT` (`sinSesion` pasa a 200, nuevo `tokenInvalido`). Frontend: `Imagen` en el kit,
  `api/busqueda.js`, `textosBusqueda.js`, pantalla pública `/buscar` (`BuscarEspacios.jsx`), ruta y enlaces en
  `App.jsx`. Docs: `architecture.md`, `frontend.md`, `modelo-datos.md`.
- **Desvíos aceptados:** ejemplo de R8 probado con 8–11 excluido y 8–12 incluido; variables del cupo comentadas en
  `.env.example` (vacías impedirían arrancar); R12 probado con Bearer inexistente (revocado y vencido siguen el mismo
  camino).
- **Resultado:** `./init.sh` completo en verde (472 unitarias + 322 IT con Testcontainers, lint y build OK).
  Trazabilidad R1–R47 en `progress/impl_HU-10.md`. Reviewer: APPROVED sin cambios requeridos. Status → `done`.
- **Pendientes (observaciones no bloqueantes del review):**
  1. `3_frontend/src/App.jsx:87-90`: la clase `estilos.inicio` pasó del enlace "Crea tu cuenta" al nuevo "Buscar
     espacios"; "Crea tu cuenta" queda en un `<p>` sin clase. Revisar el espaciado de la portada en UAT.
  2. Al unir a `develop`, confirmar que `V3_2` sigue libre; si otra historia del sprint 3 lo tomó, renombrar
     `V3_2__indices_busqueda.sql` al siguiente libre (`docs/conventions.md`).
  3. El ejemplo de design § 13 para R8 ("10:00 con 8–11 → incluido") no coincide con `CalculoDisponibilidad`; corregir
     ese ejemplo si se vuelve a editar `specs/HU-10/design.md`.
  - HU-11 y HU-23 comparten el cupo agregando un `@Bean` con `limiteCatalogo.regla(...)`; HU-23 envuelve
    `BusquedaEspacios` (Decorator).
  - Todo está sin commitear: el dueño revisa y abre el PR hacia `develop`.

## 2026-10-30 — HU-11 Detalle del espacio (done)

- **Dueña:** Adriana Callupe · **Rama:** `feature/HU-11-detalle-espacio` (desde `develop`)
- **Agentes:** orquestador → explorador (`progress/explore_HU-11.md`, 11 huecos de producto y 11 técnicos; la dueña
  prefirió no responder preguntas) → spec-author (`specs/HU-11/`, R1–R33, DT-1…DT-16, T1–T13, decisiones por
  defecto D1–D8) → aprobación de la dueña (2026-10-30, D1–D8 confirmadas tal cual) → implementer
  (`progress/impl_HU-11.md`) → reviewer vuelta 0 (CHANGES_REQUESTED) → implementer vuelta 1 → reviewer vuelta 1
  (APPROVED, `progress/review_HU-11.md`).
- **Decisiones D1–D8 (confirmadas por Adriana Callupe el 2026-10-30):** D1 solo bloques libres del día con
  `DisponibilidadService.horasLibres` sin modificarlo, mostrados con `SelectorBloques` sin `onCambiar`; D2 ficha de
  solo lectura, sin selección ni botón "Reservar" (los agrega HU-12); D3 ningún dato del arrendador; D4 componente
  `Galeria` nuevo en el kit; D5 las tres reglas de RN-06 como texto estático; D6 fecha de `?fecha=` o hoy en Lima,
  reflejada en la URL; D7 tipo y zona visibles además de la dirección; D8 404 genérico e indistinguible con enlace a
  "Buscar espacios".
- **Cambios:** `espacios`: `FichaPublicada` (sin `arrendadorId`), `CatalogoEspaciosService.publicado/esVisible` e
  impl. `ConfiguracionSeguridad`: `permitAll` solo para `GET /api/espacios/*` y `GET /api/espacios/*/disponibilidad`.
  `busqueda`: `FichaController` (id como texto → 404 indistinguible; `fecha` obligatoria), `FichaService` (orden
  404 → 400 → `horasLibres`, `VentanaReserva`), `ReglasLimiteFicha` (dos reglas en el grupo `catalogo`, cupo
  compartido con HU-10), DTO `FichaPublicaDTO`, `DisponibilidadPublicaDTO`, `BloqueLibreDTO`. Sin migración, sin
  auditoría, sin puertos nuevos. Frontend: `Galeria` (kit), `obtenerEspacio`/`consultarBloquesLibres` en
  `api/busqueda.js`, `textosFicha.js`, pantalla pública `/espacios/:id` (`FichaEspacio.jsx`), ruta en `App.jsx` y
  enlace "Ver espacio" en `BuscarEspacios.jsx`. Docs: `architecture.md` y `frontend.md` (contrato para HU-12).
- **Tests:** nuevos `FichaControllerIT` (23), `LimiteFichaIT` (6), `FichaServiceTest`, `ReglasLimiteFichaTest`;
  ampliado `CatalogoEspaciosServiceImplTest` (solo casos añadidos). Primer uso de `@ParameterizedTest` en el repo
  (incluido en `spring-boot-starter-test`). Trazabilidad R1–R33 en `progress/impl_HU-11.md`.
- **Review:**
  - Vuelta 0 — CHANGES_REQUESTED: (1) revertir la línea `aaprobado` agregada a `docs/historias/HU-11.md:13`;
    (2) `@Primary` en el `@Bean reservasFalsas()` de `FichaControllerIT` para que no rompa con
    `NoUniqueBeanDefinitionException` cuando HU-12 registre su `ReservasDelEspacio` real.
  - Vuelta 1 — APPROVED: ambos cambios aplicados, sin otros cambios.
- **Resultado:** `./init.sh` completo en verde en las dos vueltas (523 unitarias = 472 previas + 51 de HU-11;
  351 IT con Testcontainers; lint y build OK). Status → `done`.
- **Observaciones abiertas (no bloqueantes, para UAT y HU-12):**
  1. `FichaEspacio.jsx:34`: sin `?fecha=`, la fecha inicial (hoy) no se escribe en la URL hasta que el usuario la
     cambia. HU-12 (Eduardo Saldaña), para "vuelvo a la ficha", debe armar la URL de retorno con la `fecha` en uso y
     no copiar `location` tal cual; acordarlo en el contrato de `design.md` § 13.
  2. `FichaEspacio.jsx:34`: con `?fecha=` vacío se muestra el 400 "Elige una fecha válida." bajo el campo (aceptable).
  3. `FichaEspacio.jsx:110`: `h1` para el nombre del espacio mientras `App.jsx` ya usa `h1` para la marca; revisar
     la jerarquía de encabezados en UAT.
  4. Contrato para HU-12: ruta `/espacios/:id?fecha=`, `obtenerEspacio(id)`, `consultarBloquesLibres(id, fecha)`,
     `SelectorBloques` intacto y listo para `seleccionados`/`onCambiar`, DTO ampliables (`docs/frontend.md`).
  - Todo está sin commitear: la dueña revisa y abre el PR hacia `develop`.

## 2026-10-30 — HU-12 Reserva con bloqueo temporal (done)

- **Dueño:** Eduardo Saldaña · **Prueba:** Ariel Enriquez · **Rama:** `feature/HU-12-reserva-bloqueo-temporal` (desde
  `develop` en `a8234a6`)
- **Agentes:** orquestador → explorador (`progress/explore_HU-12.md`, puntos 1–18) → decisiones D1–D4 del dueño →
  spec-author (`specs/HU-12/`, R1–R45, DT-1…DT-11, S1–S11, T1–T23) → aprobación del dueño (2026-10-30, S1–S11 aceptados
  tal cual) → implementer (`progress/impl_HU-12.md`) → reviewer vuelta 0 (CHANGES_REQUESTED) → implementer vuelta 1 →
  reviewer vuelta 1 (APPROVED, `progress/review_HU-12.md`).
- **Decisiones D1–D4 (Eduardo Saldaña, 2026-10-30):**
  - D1: solo el rol `USUARIO` reserva; `ARRENDADOR` y `ADMIN` reciben 403 y la ficha no les ofrece el botón.
  - D2: expiración en tres capas: las lecturas ignoran las vencidas, un job cada minuto las pasa a `EXPIRADA` y audita,
    y al crear una reserva se liberan las vencidas de esos bloques. Cierra §9-8 para RF-D-02; los recordatorios
    (RF-D-10) siguen abiertos para HU-25.
  - D3: tras reservar se va a `/reservas/:id` (resumen, monto y contador), con `GET /api/reservas/{id}` solo para su
    dueño (si no, 404).
  - D4: el cupo de 3 pendientes se protege bloqueando la fila del usuario con un método nuevo de `CuentaService`, con
    orden fijo `usuario → espacio → reserva`; al superarlo, 400 con mensaje claro.
- **Supuestos S1–S11 (aceptados por el dueño al aprobar):** S1 conflicto de CA3 como 400 sin campo, no 409; S2 no se
  define aún `pagos.ReservasPorCobrar`, lo crea HU-13; S3 retorno tras el login con `state.retorno`, en las dos salidas
  de `IniciarSesion` y también tras una sesión caducada en `ProveedorSesion` (cambia código de HU-03), sin conservar los
  bloques elegidos; S4 se permiten bloques que empiezan en menos de 15 minutos; S5 una `PENDIENTE_PAGO` sigue y expira
  sola si el arrendador o el usuario se suspende o el espacio se pausa u oculta, y HU-13 comprobará `cuentaActiva` antes
  de cobrar; S6 una sola fecha y horas consecutivas, sin cruzar medianoche; S7 riesgo aceptado: renovar 3 pendientes
  indefinidamente o abarcar un día entero, sin poder soltar una pendiente; S8 sin modal de confirmación; S9 la expiración
  se audita con `usuario_id` e `ip` nulos y `origen=PROCESO`; S10 `@Primary` en los dobles del puerto de
  `BusquedaControllerIT` y `EspaciosControllerSoporte`; S11 `CatalogoEspaciosService.nombres(ids)` sin filtro de
  visibilidad, y `cancelada_por` y `cancelada_en` quedan para HU-15.
- **Cambios:**
  - Módulo nuevo `reservas`: `Reserva`, `ReservaBloque`, `EstadoReserva`, `SeleccionBloques`, `ReservaRepository`,
    `ReservaBloqueRepository`, `CrearReservaDTO`, `ReservaDTO`, `ReservaService` e `Impl`, `ReservasDelEspacioImpl`
    (implementación real del puerto de HU-08, con `bloquesReservadosDelDia` en una sola consulta), `ExpiracionReservas`,
    `ProgramacionExpiracion` (primer proceso programado del backend) y `ReservaController` (`POST /api/reservas`,
    `GET /api/reservas/{id}`).
  - Migración `V3_3__reservas.sql` (`V3_3` confirmado libre en `develop`, local y origin): tablas, `CHECK`, índices e
    índice único parcial `uq_reserva_bloque_activo`.
  - `cuentas`: `CuentaService.bloquearCuenta`. `espacios`: `EspacioRepository.findParaReservar`,
    `DisponibilidadService.retenerSiLibres`, `EspacioRetenido`, `CatalogoEspaciosService.nombres`.
  - Propiedades `espacioya.reservas.expiracion.activa` e `.intervalo` (`RESERVAS_EXPIRACION_ACTIVA`,
    `RESERVAS_EXPIRACION_INTERVALO`), apagado en las pruebas.
  - Frontend: `api/reservas.js`, `pages/reservas/` (`ResumenReserva`, `textosReservas`), `CuentaRegresiva` (kit),
    estados de reserva en `EtiquetaEstado`, `sesion/retorno.js`, ficha con selección y "Reservar", login con retorno,
    `ProveedorSesion` y ruta `/reservas/:id`.
  - Docs: `architecture.md` (sección "Reservas: qué deja HU-12"), `modelo-datos.md`, `frontend.md` y `requerimientos.md`
    (§8 y §9-8).
- **Tests:** nuevos `ReservaTest`, `SeleccionBloquesTest`, `CrearReservaDTOTest`, `ReservaServiceImplTest`,
  `ExpiracionReservasTest`, `ProgramacionExpiracionTest`, `ReservasDelEspacioImplTest`, `ReservasDelEspacioImplIT` (7),
  `ReservaControllerIT` (69, incluidas la trampa del `flush` y la violación real del índice) y `ReservaConcurrenciaIT`
  (5, con hilos reales), sobre `ReservasControllerSoporte`. Ampliados `CuentaServiceImplTest`,
  `DisponibilidadServiceImplTest` y `CatalogoEspaciosServiceImplTest`. Trazabilidad R1–R45 en `progress/impl_HU-12.md`.
- **Tests de otras historias que cambiaron:** `BusquedaControllerIT` y `EspaciosControllerSoporte` (`@Primary`, S10);
  `DisponibilidadServiceImplIT` (comentario y `@DisplayName` sin R21, que sigue en la unitaria); `FichaControllerIT`
  (comentario); `CalendarioEspacioControllerIT.calculaAlConsultar` (HU-08 R28), que exigía que `bloqueo_fecha` fuera la
  única tabla con `bloque` en el nombre: `reserva_bloque` también coincide y guarda retenciones, no bloques calculados.
  T9 no lo había previsto; se documentó antes de tocarlo.
- **Desviaciones del diseño:**
  - § 4: `CrearReservaDTO` usa `SoloListaDeEnteros` y `SoloTexto` en lugar de `contentUsing`, para que `"horas": 14` o
    `"fecha": {}` den el error en su campo, como piden R30 y R32.
  - § 6, aprobada por el dueño (2026-10-30): `ahora` se toma después de `retenerSiLibres`, no después de
    `bloquearCuenta`; el cupo conserva su propia lectura del reloj. Sin cambios de firma y con el mismo orden de
    bloqueos. Los 15 minutos empiezan tras la espera del bloqueo. `design.md` no se editó.
  - § 10: "Reservar" no se muestra si el día no tiene bloques libres (el diseño decía "sin sesión, siempre activo").
- **Review:**
  - Vuelta 0 — CHANGES_REQUESTED: (1) CA2 en pantalla: la `key` del contador cambiaba al pedir y no al recibir, así que
    podía mostrar "Pendiente de pago" y unos 15:00 con los bloques ya liberados; (2) `@DisplayName` de
    `DisponibilidadServiceImplIT` y comentario de `FichaControllerIT`; (3) siete líneas de más de 120 caracteres.
  - Vuelta 1 — APPROVED: los tres cambios aplicados, más las observaciones 1 (IT de R22 con PostgreSQL real, sin
    defectos), 2 (`retornoValido`, a pedido del dueño) y 3 (el `ahora` de la desviación de § 6, a pedido del dueño).
- **Pendientes:**
  1. **Dueño, comprobar el contador en el navegador.** El arreglo de CA2 se aprobó por lectura del código y cálculo de
     tiempos: ninguna pantalla de HU-12 se ejecutó en un navegador. Los pasos están en `progress/impl_HU-12.md`,
     "Pendiente para el dueño". Si el contador salta a un tiempo mayor, la historia vuelve a `in_progress`.
  2. **Avisos al equipo, escritos aquí y aún sin entregar:**
     - Jorge Quispe (HU-03, S3): `IniciarSesion.jsx` va a `state.retorno` si `retornoValido` lo acepta, en sus dos
       salidas, y si no a `/panel`; `ProveedorSesion.jsx` agrega `retorno` al llevar al login tras un 401 con token, en
       todas las rutas protegidas.
     - Kevin Chan (HU-15, S11): `reserva` aún no tiene `cancelada_por` ni `cancelada_en`: las agrega HU-15 con su
       migración. Para liberar bloques al cancelar, poner `activo = false` con
       `ReservaBloqueRepository.desactivarDeReserva`; las filas se conservan.
     - Sebastian Garate (HU-08, HU-10, S10): los dobles del puerto de `BusquedaControllerIT` y
       `EspaciosControllerSoporte` llevan `@Primary`. `bloquesReservadosDelDia` está sobrescrito con una sola consulta y
       su firma queda confirmada, sin cambios.
     - Adriana Callupe (HU-14): puede reutilizar `CatalogoEspaciosService.nombres(ids)`, sin filtro de visibilidad, y
       `ReservaDTO`. El estado se lee con `Reserva.estadoEn(ahora)`, no con `getEstado()`.
  3. **Lo que necesitará HU-13 (`design.md` § 12, también en `docs/architecture.md`):** `reservas` implementará
     `pagos.ReservasPorCobrar`, que define HU-13. Obtener: id, usuario dueño, `montoTotal`, `expiraEn` y vigencia con
     `esVigente(ahora)`, no solo por estado. Confirmar: `Reserva.confirmar()` nuevo, respetando `usuario → espacio →
     reserva`; `retenerSiLibres` no sirve porque las horas ya las retiene la propia reserva, así que hace falta otro
     método que solo tome el bloqueo del espacio y luego `findParaActualizar` de la reserva, revalidando vigencia, porque
     el proceso y el webhook compiten por esa fila. Cobro y correos: comprobar `cuentaActiva` antes del checkout (S5);
     `EspacioRetenido` no trae el arrendador.
  4. **Observaciones del review que siguen abiertas (no bloquean):** la carrera entre el job y la creación está razonada,
     no probada con hilos (el spec no lo exige; R16 es Mockito), y `RutaProtegida` no guarda el retorno, de modo que abrir
     `/reservas/:id` sin sesión lleva al login y luego a `/panel` (fuera de S3, anotado en `docs/frontend.md`).
  5. **Para HU-20 (del explorador):** hoy `PUBLICADO` implica identidad verificada, y por eso la reserva no la comprueba;
     HU-20 CA3 / RN-02 devolverá la identidad a "en revisión", y allí se decide si esos espacios siguen reservándose.
- **Resultado:** `./init.sh` completo en verde en el cierre, con HU-12 ya en `done` (631 unitarias = 523 previas + 108 de
  HU-12; 432 IT con Testcontainers = 351 + 81 de HU-12; lint y build OK). Único aviso, ajeno a la historia: las llaves
  locales del backend. Status → `done`: código terminado, no aceptación; falta la caja negra de Ariel Enriquez en UAT.
  - Todo está sin commitear: el dueño revisa y abre el PR hacia `develop`.

- 2026-10-30 — (orquestador, sdd:false) HU-12: Eduardo Saldaña comprobó el contador en el navegador con una reserva a 30 segundos: bajó hasta `00:00` sin subir, la pantalla pasó sola a "Expirada" y en la base la reserva quedó `EXPIRADA`, el bloque inactivo y la auditoría `RESERVA_EXPIRADA` con `origen=PROCESO`. Se cierra ese pendiente.

- 2026-10-30 — (sdd:false, pedido visual) Rediseño de la página de inicio `/` (`pages/inicio/`) y del encabezado global según una imagen de referencia: hero con buscador hacia `/buscar`, tarjetas por tipo real (CANCHA, ESTADIO, AUDITORIO), banda de ventajas `#como-funciona` con el enlace a `/registro-arrendador`. Tokens nuevos de color en `tokens.css` y `docs/frontend.md`; el ancho de lectura pasa de `#root` a `.contenido` de `App.module.css`. Detalle en `progress/impl_inicio.md`. Sin commitear.

- 2026-10-30 — (orquestador, sdd:false) HU-13 deja de usar Factory Method por decisión de su dueño, Eduardo Saldaña: elegir entre la pasarela sandbox y la simulada ya lo resuelve la configuración con inyección de dependencias de Spring, y una fábrica a mano solo agregaría clases. Factory Method sigue en HU-25 (correo según el evento). Cambiados `feature_list.json`, `docs/architecture.md`, `docs/requerimientos.md` §7 y `docs/historias/HU-13.md`. **Pendiente:** confirmar con Ariel Enriquez (PO).


## 2026-10-31 — HU-13 Pago con tarjeta (done)

> **`done` no significa que Mercado Pago funcione dentro de la aplicación.** Significa código terminado con la pasarela
> simulada, con el reviewer en `APPROVED`. **T23 no se ejecutó.** **S1 (firma real), S5 (tipos de pago excluidos) y S6
> (cierre del checkout) siguen sin comprobar.** `MercadoPagoAdapter` solo corrió contra `StubMercadoPago`, y su firma solo
> se comprobó con el vector aceptado por el validador oficial. Lo único real con Mercado Pago es un pago de prueba aislado,
> hecho por el dueño fuera de la aplicación. UAT usa la pasarela simulada, sin pasarela certificada. Si T23 falla al
> conectar, la historia vuelve a `in_progress`, o a `blocked` si la firma no pasa.

- **Dueño:** Eduardo Saldaña · **Prueba:** Ariel Enriquez · **Rama:** `feature/HU-13-pago-tarjeta` (desde `develop` en
  `1fbf140`) · **Fechas:** 2026-10-30 y 2026-10-31. Sin commit ni push: el dueño revisa y abre el PR hacia `develop`.
- **Agentes:** orquestador → explorador (`progress/explore_HU-13.md`: 8 decisiones de producto y 15 técnicas) → prueba del
  proveedor por el dueño → decisiones D1–D7 → spec-author (`specs/HU-13/`, R1–R58, DT-1…DT-15, S1–S14, T1–T24; `design.md`
  en unas 415 líneas, sobre el objetivo de 350) → aprobación del dueño (S1–S14 aceptados) → implementer
  (`progress/impl_HU-13.md`) → reviewer vuelta 1 → D8, D9 y D7 revisada → spec-author, adenda (R59–R87, DT-16…DT-27,
  S15–S20, C1–C5, T25–T43) → aprobación del dueño → implementer → reviewer vuelta 2. La sesión del implementer se
  interrumpió el 2026-10-30 por un límite de uso y se retomó el 2026-10-31, revisando el estado antes de seguir.
- **Patrones:** Adapter (`PasarelaPago`, `MercadoPagoAdapter`, `PasarelaSimulada`) y Facade (`CheckoutFacade`). Factory
  Method se retiró de HU-13 por decisión del dueño: `@ConditionalOnProperty` elige la pasarela. Queda solo en HU-25.
- **Prueba del proveedor por el dueño (2026-10-30, fuera de la aplicación):** Stripe no ofrece Perú en su registro y se
  descartó; no se declaró otro país ni datos falsos. Con Mercado Pago Checkout Pro de prueba, un pago con vendedor y
  comprador de prueba **sí envió notificaciones** a una URL pública (webhook.site, pago `185919482939`). Llegan varias por
  un solo pago: `topic=payment`, `type=payment` con `action: payment.created` o `payment.updated`, y `topic=merchant_order`.
  Ninguna trae estado ni monto. `live_mode` llegó en `true` con un vendedor de prueba, así que no distingue el ambiente.
  La notificación `type=payment` trae `x-signature` y `x-request-id`. `GET /v1/payments/{id}` con credenciales de prueba
  devolvió `status: approved`, `currency_id: PEN`, monto y últimos 4 dígitos. Comprar exige vendedor y comprador de prueba;
  con otra cuenta sale "Una de las partes... es de prueba". La URL real decía `data.id=185919482939`: `data_id` era cómo lo
  mostraba webhook.site (S2 resuelto). El panel no dejó leer la firma secreta, y por eso S1 quedó abierto.
- **Prueba técnica del spec-author:** `sdk-java:3.7.0` corre en Java 25.0.5 con Boot 4.1.2, pero trae al menos 16 librerías y
  configuración estática: se eligió `RestClient` + HMAC (DT-1), probado en un proyecto temporal fuera del repositorio.
- **Decisiones (Eduardo Saldaña, 2026-10-30):**
  - D1: Mercado Pago Checkout Pro, en modo de prueba y en soles. Stripe, Culqi e Izipay descartadas. Sigue siendo el
    proveedor elegido, sin conectar por ahora (D8).
  - D2: un pago aprobado que llega con la reserva no vigente se registra como `PAGADO_TARDIO` y se audita, sin confirmar la
    reserva. Devolución manual, hasta que HU-16 la automatice si el PO cambia ese alcance. Corrige RN-08.
  - D3: el enlace vence 1 minuto antes que la reserva (configurable) y en ese último minuto no se inicia un pago.
  - D4: al tercer rechazo la reserva sigue hasta expirar, iniciar otro pago responde 429 y la pantalla indica reservar de
    nuevo cuando expire.
  - D5: comprobante con espacio, fecha, horario, monto, últimos 4 y referencia; correo al arrendador con espacio, fecha y
    horario, sin datos del usuario ni monto. Correo simulado hasta HU-25.
  - D6: con el espacio pausado u oculto o el arrendador suspendido no se inicia el pago; con el pago ya aprobado y la
    reserva vigente, se confirma igual.
  - D7 revisada (reemplaza a la original, "la simulada solo en pruebas y desarrollo; UAT usa Mercado Pago"): UAT usa la
    pasarela simulada hasta conectar Mercado Pago, y el informe lo declara.
  - D8: Mercado Pago no se conecta por ahora. El adaptador y sus pruebas quedan listos, la transacción se hace con la
    simulada y T23 se difiere.
  - D9: al pulsar **Pagar** con `PAGOS_PASARELA=simulada` se abre una pantalla con **Aprobar** y **Rechazar**, para el video
    y la caja negra, sin terminal. Solo existe con la simulada.
- **Supuestos aceptados por el dueño:**
  - S1 firma sin comprobar con una notificación real (**sigue abierto**); S2 `data.id` y `data_id`, resuelto; S3 estado
    `MONTO_DISTINTO`; S4 un segundo aprobado sobre una `CONFIRMADA` es `PAGADO_TARDIO`; S5 solo tarjeta y una cuota, con
    modo binario (**lista de excluidos sin comprobar**); S6 cierre de mejor esfuerzo al tercer rechazo (**sin comprobar**;
    riesgo aceptado: el corte real es el 429); S7 riesgo aceptado: límite por reserva, hasta 9 rechazos cada 15 minutos por
    usuario; S8 sin tolerancia al `ts` de la firma; S9 firma inválida responde 401 sin cuerpo y va solo al log.
  - S10 si la consulta falla, incluido un 404, responde 503; S11 sin `PAGOS_PASARELA` no arranca, ajustado por D7 revisada;
    S12 `back_urls` solo con frontend HTTPS; S13 tabla `checkout_pago`, y `pago` gana `PAGADO_TARDIO` y `MONTO_DISTINTO`; S14
    HU-19 y HU-28 cuentan solo `PAGADO`.
  - S15 `POST …/simulacion` no repite vigencia, último tramo ni espacio: una pantalla abierta hasta vencer produce
    `PAGADO_TARDIO`; S16 `PAGOS_SECRETO_WEBHOOK` vacío en UAT; S17 ids deterministas: `sim-<r>-APROBADO-1` y rechazos con el
    conteo de filas más 1; S18 `ultimos4 = 4242`, fijo, y referencia `sim-…`; S19 simular exige la fila de `checkout_pago`;
    S20 `MONTO_DISTINTO` no se produce desde la pantalla.
- **Contradicciones con reglas del proyecto, aceptadas por el dueño al aprobar la adenda (`design.md` § 14.13):** C1 en UAT no
  hay pasarela certificada (RNF-CUM-01); C2 el control de firma no se demuestra en UAT (RNF-CUM-02); C3 el cliente elige el
  resultado, como excepción acotada a la simulada y auditada (principio 6); C4 T23 queda como texto sin casilla, así que
  `init.sh` deja cerrar con una prueba sin ejecutar; C5 CA1 y CA4 de la historia no se tocaron y **quedan por decidir con
  Ariel Enriquez**. Riesgos en § 14.13: RS-1 quien tenga cuenta en UAT puede aprobar su propio pago; RS-2 video y caja negra
  demuestran el flujo, no la integración real; RS-3 la firma real podría no coincidir al conectar; RS-4 definir el secreto en
  UAT abre la ruta pública; RS-5 filas `sim-`; RS-6 reservas pendientes al cambiar de pasarela; RS-7 límite del `curl` local.
- **Cambios:**
  - Base de datos: `V4_1__pago.sql` (`pago`, `checkout_pago`). El 2026-10-30 `V4_1` seguía libre en `origin/develop`; si está
    ocupado al unir la rama, se renombra. La adenda no agrega migración.
  - Módulo `pagos`, nuevo: dominio, repositorios, `CheckoutFacade`, `EvaluacionCobro`, `SesionesCheckout`,
    `WebhookPagoService` (único camino que confirma), `AvisosPago`, pasarelas, puerto `ReservasPorCobrar`, `PagoController` y
    `WebhookPagoController`. Adenda: `SimulacionPago`, `PagoSimuladoController` y sus DTO, solo con la simulada; entregan la
    notificación a `WebhookPagoService.recibir`, que no cambió.
  - Otros módulos: `Reserva.confirmar`, `ReservasPorCobrarImpl`, `bloquearEspacio`, `CuentaService.correoDe`, dos métodos en
    `NotificadorCorreo`, `comun/transaccion/DespuesDelCommit` (movido de `CuentaServiceImpl`), `ServicioExternoException` y
    la ruta pública del webhook.
  - Frontend: **Pagar** en `/reservas/:id`, pantalla `/reservas/:id/pago-simulado`, `api/pagos.js`, DEF-01 y DEF-02.
  - Docs: RN-08 y RF-E-04, pendiente "Mercado Pago sin conectar" en `docs/requerimientos.md` § 9, `docs/architecture.md`,
    `docs/modelo-datos.md`, `docs/frontend.md`, README y `.env.example`. CA1 y CA4 no se tocaron (C5).
  - Detalle de archivos y mapa R1–R87 → prueba: `progress/impl_HU-13.md`. Material del informe: `progress/doc_HU-13.md`, que
    se conserva.
- **Hallazgo durante la implementación:** la primera carrera de R37 solo producía `EXPIRADA+PAGADO_TARDIO` en 30 de 30
  rondas, porque el webhook hace una llamada HTTP antes de bloquear. Se varió la espera (9 `CONFIRMADA` y 21 `EXPIRADA` en 30
  rondas) y se agregaron dos pruebas deterministas que esperan un bloqueo real en `pg_stat_activity`.
- **Vueltas del review (`progress/review_HU-13.md`):**
  - Vuelta 1 (2026-10-30) — APPROVED sobre T1–T22 y T24, con T23 abierta, y cuatro defectos de severidad baja: DEF-01
    (`pagando` tras volver con Atrás), DEF-02 (lectura fallida durante el sondeo), DEF-03 (huecos en la guía de T23 y
    credenciales en `export` en el README) y DEF-04 (RF-E-04 contra RN-08). El dueño pidió corregirlos.
  - Vuelta 2 (2026-10-31) — APPROVED sobre la adenda. DEF-01 a DEF-04 corregidos; DEF-01 y DEF-02 verificados leyendo el
    código, sin reproducir en navegador. DEF-05 (texto viejo en `progress/doc_HU-13.md` §1 y §13), corregido en este cierre,
    sin tocar código.
- **Pendientes:**
  1. **Dueño, probar en el navegador.** Ningún agente abrió estas pantallas: aprobar, rechazar, tres rechazos, pago tardío,
     DEF-01 y DEF-02. Pasos en `progress/impl_HU-13.md`, "Pendiente para el dueño". Lo que se vea distinto de la guía es un
     hallazgo: la historia vuelve a `in_progress`.
  2. **Dueño, quitar la URL de webhook.site del panel de Mercado Pago.** Quedó de la prueba aislada: a esa página le llegarían
     las notificaciones de la cuenta de prueba.
  3. **Avisos al equipo, escritos aquí y aún sin entregar:**
     - Todo el equipo (S11 ajustado): tras el merge, el backend no arranca sin `PAGOS_PASARELA`. En local,
       `PAGOS_PASARELA=simulada`; `PAGOS_SECRETO_WEBHOOK` solo hace falta para simular por `curl`. En UAT, `simulada` con
       `PAGOS_SECRETO_WEBHOOK` **vacío** (S16).
     - Todo el equipo (RS-5): en UAT, un `id_transaccion_pasarela` que empieza por `sim-` no movió dinero. Las historias que
       lean `pago` deben filtrar esas filas, y hay que decidir qué hacer con ellas al conectar.
     - Quien agregue un doble de `NotificadorCorreo` debe implementar `notificarPagoConfirmado` y `notificarReservaPagada`,
       que no tienen `default`.
     - Kevin Chan (HU-15): `PagoService` no existe aún, lo crea HU-15. Una reserva tiene como mucho un pago `PAGADO`
       (`uq_pago_reserva_pagado`).
     - Adriana Callupe (HU-14): `CONFIRMADA` ya existe, sale de `Reserva.confirmar`, y `ReservaDTO` no cambió.
     - Eduardo Saldaña (HU-19 y HU-28): contar solo `PAGADO` (HU-28, también los reembolsados) y excluir o limpiar las filas
       `sim-`; `PAGADO_TARDIO` y `MONTO_DISTINTO` no son ingresos.
  4. **Temas para Ariel Enriquez (PO y caja negra):**
     - Factory Method: confirmar el retiro de HU-13.
     - RN-08 cambió: `PAGADO_TARDIO`, devolución manual y enlace 1 minuto antes. También RF-E-04.
     - Estados nuevos de pago: `PAGADO_TARDIO` y `MONTO_DISTINTO`.
     - C5: decidir CA1 ("en el checkout de la pasarela") y CA4 ("expira junto con la reserva"), que no se tocaron. Además
       debe aceptar la historia en la review, con D7 revisada, D8 y D9 a la vista.
     - HU-16: `reembolsar` va en `PasarelaPago` y también en `PasarelaSimulada`, `id_transaccion_pasarela` sirve para pedirlo,
       y puede decidir si atiende `PAGADO_TARDIO`, que hoy se devuelve a mano.
     - Caja negra: todos los casos se prueban sin terminal, y `MONTO_DISTINTO` no es caso de pantalla (S20).
  5. **T23, al conectar Mercado Pago.** Guía en `progress/impl_HU-13.md`, sin ejecutar. Comprueba S1, S2 con firma real, S5 y
     S6. Antes de cambiar a `mercadopago`, esperar a que no haya reservas pendientes (RS-6) y decidir qué hacer con las filas
     `sim-` (RS-5). Registrado también en `docs/requerimientos.md` § 9.
  6. **Orquestador, arnés (`"sdd": false`):** la regla "Secretos trackeados en git" de `init.sh` solo mira nombres de archivo;
     conviene ampliarla a contenidos (`APP_USR-`, `TEST-`). No se tocó `init.sh`.
  7. **Orquestador, informe:** §2, §6 y §7 de `progress/doc_HU-13.md`, tras `APPROVED`. `WebhookPagoIT.puntaAPunta` corre
     contra `StubMercadoPago`: no se cita como prueba con Mercado Pago sin esa aclaración.
  8. **Observaciones del review que siguen abiertas (no bloquean):** `MercadoPagoAdapter.decimal` deja pasar un
     `NumberFormatException` si `transaction_amount` no es numérico (500 y no 503); `PAGO_EN_REVISION` promete el comprobante
     también si el pago termina tardío; `ReglasPago.idDeReserva` es la tercera copia de esa validación;
     `SesionesCheckout.cerrar` retiene la conexión durante el `PUT`, hasta 10 s, solo al tercer rechazo; cada `APROBADO`
     repetido escribe una fila `PAGO_SIMULADO` sin tope; `design.md` § 14.9 atribuye a `SimulacionPagoTest` una comprobación
     que hace `PasarelaSimuladaTest.armaLaNotificacion`.
- **Resultado:** `./init.sh` completo en verde en el cierre, con HU-13 ya en `done`: salida 0, 824 unitarias y 587 de
  integración con Testcontainers, sin fallos, errores ni omitidas (631 y 432 antes de la historia; 339 pruebas en sus clases
  nuevas: 184 unitarias y 155 de integración); lint y build OK; sin `R<n>` sin prueba (R1–R87). Único aviso, ajeno a la
  historia: las llaves locales del backend. Status → `done`: código terminado con la pasarela simulada, no aceptación y no
  Mercado Pago funcionando dentro de la aplicación. **T23 no se ejecutó; S1, S5 y S6 siguen sin comprobar.** Falta la prueba
  del dueño en el navegador y la caja negra de Ariel Enriquez en UAT.
  - Todo está sin commitear: el dueño revisa y abre el PR hacia `develop`.

## 2026-10-31 — HU-14 Mis reservas (done)

- **Dueña:** Adriana Callupe · **Prueba:** Sebastian Garate · **Rama:** `feature/HU-14-mis-reservas` (desde `develop` en
  `1fbf140`, avanzada por fast-forward a `6afb5bc` con HU-13) · **Fecha:** 2026-10-31. Sin commit ni push: la dueña revisa y
  abre el PR hacia `develop`.
- **Agentes:** orquestador → explorador (`progress/explore_HU-14.md`: P1–P8, T1–T9, H1–H6) → spec-author (`specs/HU-14/`,
  R1–R38, DT-1…DT-13, D1–D12, T1–T19) → integración de HU-13 → explorador (`progress/explore_HU-14_HU-13.md`) →
  spec-author, revisión → aprobación de la dueña → implementer (`progress/impl_HU-14.md`) → reviewer (`APPROVED`,
  `progress/review_HU-14.md`) → implementer, cierre.
- **Avanzar con HU-13 pendiente:** al empezar, HU-13 (Pago con tarjeta, Eduardo Saldaña) seguía `pending` y sin rama en el
  remoto. Se le avisó a la dueña el 2026-10-30 y decidió avanzar igual; el spec aisló todo lo que dependía de pagos
  (contrato § 11.3 que HU-13 debía respetar, D12 como verificación pendiente de integración).
- **Revisión del spec tras integrar HU-13:** HU-13 llegó a `develop` (`ec51197`, merge `6afb5bc`). El explorador confirmó
  el contrato § 11.3 ("spec válido con ajustes menores"). El spec-author ajustó: IP de `MisReservasControllerIT`
  `10.15.x.x` → `10.16.x.x`; D9 con un caso de punta a punta de CA2 con la pasarela simulada (y `@AfterEach` que borra
  `pago`); D12 sin verificación pendiente; D10 de la dueña; textos sobre HU-13. R1–R38, patrones, endpoints, DTO y mensajes
  sin cambios.
- **Decisiones D1–D12 (confirmadas por Adriana Callupe el 2026-10-31):**
  - D1: al arrendador se le muestra el `nombre` del usuario si no está en blanco; si no, "Usuario de EspacioYa". Nunca se
    deriva del correo. HU-20 lo completa sin cambiar el contrato.
  - D2: próxima = fin posterior a *ahora* y estado (`estadoEn(ahora)`) `PENDIENTE_PAGO` vigente o `CONFIRMADA`; el resto
    son pasadas, también `EXPIRADA`/`CANCELADA` con fecha futura. Corte en el servidor.
  - D3: próximas ascendentes, pasadas descendentes, empate por id; de 10 en 10 con `pagina` y `hayMas`, sin total.
  - D4: el usuario ve todos los estados con `EtiquetaEstado`.
  - D5: el arrendador ve espacio, fecha, horario, nombre (D1), estado y monto; nunca correo, teléfono ni pago.
  - D6: del espacio, solo nombre y "Ver espacio".
  - D7: rutas `/mis-reservas` y `/panel/reservas`, enlaces desde `Panel.jsx`.
  - D8: `GET /api/reservas` solo `USUARIO`; `GET /api/arrendadores/actual/reservas` solo `ARRENDADOR`; otro rol → 403.
  - D9: `CONFIRMADA` se alcanza en las IT por la API con la pasarela simulada (caso de punta a punta de CA2).
  - **D10 (decisión de la dueña):** "Ver reserva" → `/reservas/:id` en **todas** las reservas del usuario, en cualquier
    estado, para llegar al aviso de pago no aplicado de HU-13. HU-14 no agrega botones de pago.
  - D11: estado del pago, comprobante y `ultimos4` fuera (HU-28); HU-14 no lee `pago`.
  - D12: la caja negra de CA2 se hace completa en UAT con la pasarela simulada.
- **Patrones:** ★ **DAO** — `ReservaDAO` (interfaz, primer DAO explícito del repo) + `ReservaDAOJpa` (`@Repository` de
  paquete, dos consultas: `reserva` y una agregada a `reserva_bloque`, unión en memoria, sin N+1) + `ReservaConHorario`
  (TransferObject); `ConsultaReservasService` solo conoce la interfaz. ★ **Iterator** — `HistorialReservas` (colección
  inmutable, fábrica de recorridos) + `IteradorReservas` (`hasNext` no consume) + `Pagina` (cliente, `hayMas = hasNext()`).
- **Cambios:**
  - Backend `reservas`: `GrupoReservas`, `ReservaConHorario`, `HistorialReservas`, `IteradorReservas`, `Pagina`,
    `HorarioReserva`, `ReservaDAO`, `ReservaDAOJpa`, `ConversionReservas` (DT-11; `ReservaServiceImpl.aDto` delega),
    `PaginaReservasDTO`, `ReservaDeMiEspacioDTO`, `ConsultaReservasService` (`readOnly`, un solo instante, sin auditoría),
    `ReservaController` (`GET` sin id), `ReservasDeMisEspaciosController`; métodos nuevos en `ReservaRepository` y
    `ReservaBloqueRepository`.
  - Contratos nuevos en otros módulos (solo agregan): `CatalogoEspaciosService.nombresDelArrendador`;
    `CuentaService.nombres` con `NombreCuenta` y `UsuarioRepository.nombresDe`. Sin puertos nuevos, sin migración.
  - Frontend: `api/reservas.js`, `textosReservas.js`, `SeccionReservas`, `MisReservas`, `ReservasDeMisEspacios`, rutas en
    `App.jsx`, enlaces en `Panel.jsx`. Sin componentes nuevos en el kit.
  - Docs: `docs/architecture.md` (sección "Mis reservas: qué deja HU-14", contratos, fila de patrones) y
    `docs/frontend.md`. En el cierre se actualizaron los encabezados de `requirements.md` y `design.md` (D1–D12
    confirmadas) y se matizó en `docs/architecture.md` la frase sobre el dueño en la firma del DAO (observaciones 1 y 2).
- **Tests:** nuevos `GrupoReservasTest`, `HistorialReservasTest`, `PaginaTest`, `ReservaDAOJpaTest`, `ReservaDAOJpaIT`,
  `ConsultaReservasServiceTest`, `MisReservasControllerIT` (34 casos, incluido CA2 de punta a punta); casos agregados a
  `CuentaServiceImplTest` (+2) y `CatalogoEspaciosServiceImplTest` (+3). Ningún test existente cambió. R1–R38 con test
  (mapa en `progress/impl_HU-14.md`). `./init.sh` completo en verde dos veces: **889 unitarias y 626 IT**, 0 fallos, errores
  ni omitidas; lint y build OK.
- **Review:** `APPROVED` en la primera vuelta (`progress/review_HU-14.md`), con 4 observaciones que no bloquean: 1 (texto
  de D1–D12 en el spec) y 2 (texto del DAO en `architecture.md`) resueltas en el cierre; 4 (esta entrada) resuelta; 3
  pendiente (abajo).
- **Pendientes:**
  - **Avisos que Adriana debe enviar** (texto en `progress/impl_HU-14.md`, "Avisos para la dueña"):
    - Sebastian Garate (`espacios`): nuevo `CatalogoEspaciosService.nombresDelArrendador(arrendadorId)`, todos los
      espacios del arrendador en cualquier estado; nada existente cambió.
    - Jorge Quispe (`cuentas`): nuevo `CuentaService.nombres(usuarioIds)`, solo el nombre no en blanco, nunca correo ni
      teléfono; `NombreCuenta` y `UsuarioRepository.nombresDe`. HU-20 y HU-22 no deben romper el contrato.
    - Eduardo Saldaña (HU-13): contrato § 11.3 verificado, sin acción.
    - Kevin Chan (HU-15): `acciones` de `Tarjeta` libre para "Cancelar"; una `CANCELADA` cae en pasadas del usuario y
      sale de la lista del arrendador. Si HU-15 amplía el DAO, considerar `confirmadasDelArrendador(arrendadorId,
      espacioIds)` (observación 2).
  - **Observación 3 del review, para HU-15:** `ReservasDeMisEspacios.module.css:33-37` define `.acciones` sin uso
    (`ReservasDeMisEspacios.jsx` pone el `Link` directo). Quitarlo o usarlo en HU-15.
  - Caja negra de Sebastian Garate en UAT (CA2 completa con la pasarela simulada, D12) y prueba de la dueña en el
    navegador (`PAGOS_PASARELA=simulada` en el `.env` local).
- **Resultado:** `./init.sh` completo en verde (889 unitarias y 626 IT); en el cierre, `./init.sh --rapido` para confirmar
  la coherencia. Status → `done`. Todo sin commitear.

## 2026-10-31 — Reorganización de `service/impl/` (sin historia)
- **Agente:** Jorge Quispe con Claude Code. Cambio de estructura pedido por Jorge fuera del flujo SDD (`"sdd": false`):
  no toca comportamiento, solo paquetes y visibilidad.
- **Cambios:**
  - Movidas a `<modulo>/service/impl/` las 6 implementaciones que ya existían: `cuentas.CuentaServiceImpl`,
    `espacios.CatalogoEspaciosServiceImpl`, `espacios.DisponibilidadServiceImpl`, `reservas.ReservaServiceImpl`,
    `reservas.ReservasDelEspacioImpl` y `reservas.ReservasPorCobrarImpl`. Sus pruebas (`*ImplTest`, `*ImplIT`) se
    movieron al mismo paquete de `src/test`. No se crearon interfaces nuevas.
  - Imports explícitos agregados donde la clase usaba otras de `service/` sin import (incluidos
    `CalendarioEspacioService` y `CalendarioEspacioServiceTest`, que usan `DisponibilidadServiceImpl`).
  - Visibilidad ampliada a `public` porque las clases de `impl/` ya no comparten paquete: en
    `DisponibilidadServiceImpl`, `ventana()`, `reservasDelDia(...)` y `bloquesDelDia(...)` (los usa
    `CalendarioEspacioService`); la clase `reservas.service.ConversionReservas` y su `aDto(...)` (la usa
    `ReservaServiceImpl`); `EspacioService.urlDe(FotoEspacio)` (la usa `CatalogoEspaciosServiceImpl`). Ahora otros módulos
    pueden llamarlos; las reglas de `docs/architecture.md` (no usar el servicio de otro módulo salvo por su contrato
    público o un puerto) siguen vigentes.
  - `docs/conventions.md`: árbol con `service/impl/`, regla `<Nombre>Impl` y nota de que `repository` es la capa DAO
    (Spring Data genera la implementación, por eso no hay `dao/impl`). `docs/architecture.md` (principio 1) enlaza esa
    regla.
- **Tests:** ninguno cambió de contenido, solo de paquete. `./2_Backend/mvnw -f 2_Backend/pom.xml verify` en verde
  (626 IT, 0 fallos, errores ni omitidas). Frontend no afectado.
- **Pendientes:** avisar a Sebastian Garate (`espacios`) y Adriana (`reservas`) de que sus `*Impl` cambiaron de paquete
  y de los métodos que ahora son `public`. Las ramas abiertas que toquen esas clases tendrán conflictos de ruta al
  integrar `develop`.
- **Resultado:** sin commitear; lo revisa y commitea el dueño.

## 2026-10-31 — HU-15 Cancelación de reservas (done)

- **Dueño:** Kevin Chan (sesión conducida por Jorge Quispe en su nombre) · **Rama:** `feature/HU-15-cancelacion-reservas`
  (desde `develop` en `9a4ab53`) · **Fecha:** 2026-10-31. Sin commit ni push: el dueño revisa y abre el PR hacia `develop`.
- **Agentes:** orquestador → explorador (`progress/explore_HU-15.md`) → spec-author (`specs/HU-15/`, R1–R39, DT-1–DT-12,
  D1–D12, T1–T20; ★ Strategy) → aprobación del dueño → implementer (`progress/impl_HU-15.md`) → reviewer (`APPROVED`,
  `progress/review_HU-15.md`) → implementer, cierre.
- **Spec aprobado por:** Kevin Chan el 2026-10-31 ("Kevin: aprobado", dado en el chat de la sesión), con D1–D12
  confirmadas (entre ellas D2 "no se cancela una reserva ya empezada", D5 "monto visto" y D7 "HU-15 solo dispara el
  reembolso").
- **Cambios:**
  - Migración `V4_2__cancelacion_reserva.sql` (columnas `cancelada_por`, `cancelada_en`, `monto_reembolso` en `reserva` y
    dos restricciones `CHECK`).
  - Dominio `reservas`: `QuienCancela`, `MotivoNoCancelable`, `Reserva.cancelar`/`motivoNoCancelable`,
    `ReservaConHorario.instanteDeInicio`; ★ Strategy `PoliticaReembolso` + `ReembolsoUsuarioConAnticipacion` (≥ 48 h →
    100 %), `ReembolsoUsuarioSinAnticipacion` (< 48 h → 0 %), `ReembolsoCancelaArrendador` (100 %) y el Context
    `CalculoReembolso` (política fija, exactamente una estrategia aplica).
  - `CancelacionReservaService` (vista previa sin escritura y confirmación bajo bloqueo con `montoReembolsoVisto`),
    `AvisosCancelacion` (dos correos después del commit), 3 DTO y 4 rutas en `ReservaController` y
    `ReservasDeMisEspaciosController`; `ConversionReservas.idReserva` (sustituye a `ReservaServiceImpl.idValido`).
  - `pagos`: contrato `PagoService.reembolsarCancelacion(ReembolsoPorCancelacion)` (`MANDATORY`); `PagoServiceImpl` solo
    valida y audita `REEMBOLSO_SOLICITADO` (la pasarela y la tabla `reembolso` son de HU-16);
    `PagoRepository.findByReservaIdAndEstado`.
  - `cuentas`: `NotificadorCorreo` ganó `notificarCancelacionAlUsuario` y `notificarCancelacionAlArrendador` (records
    `AvisoCancelacionUsuario`, `AvisoCancelacionArrendador`); los 6 dobles de prueba los implementan vacíos.
  - Frontend: `Modal.textoCerrar`, 4 funciones en `api/reservas.js`, textos HU-15 en `textosReservas.js`,
    `CancelarReserva.jsx` (+ CSS) y botón "Cancelar reserva" en Próximas de `MisReservas` y `ReservasDeMisEspacios`. En
    el cierre, "Fecha" y "Horario" pasaron a `ETIQUETA_FECHA` y `ETIQUETA_HORARIO` (observación menor 1 del review).
  - Docs: `docs/architecture.md` (contrato de `PagoService`, sección "Cancelación: qué deja HU-15", fila de patrones),
    `docs/modelo-datos.md`, `docs/frontend.md`. Tabla de puertos sin cambios.
- **Tests:** nuevos `CalculoReembolsoTest`, `ReservaConHorarioTest`, `ConversionReservasTest`, `AvisosCancelacionTest`,
  `CancelacionReservaServiceTest`, `PagoServiceImplTest`, `CancelacionReservaControllerIT` (33 casos, punta a punta con la
  pasarela simulada) y `CancelacionConcurrenciaIT`; casos agregados a `ReservaTest` (+7) y `NotificadorCorreoSimuladoTest`
  (+2). Ningún caso existente cambió. R1–R39 con test (mapa en `progress/impl_HU-15.md`).
- **Review:** `APPROVED` en la primera vuelta (`progress/review_HU-15.md`); 6 desviaciones declaradas, todas aceptadas.
  Observación 1 (etiquetas literales) resuelta en el cierre; observación 2 (esta entrada y la plantilla) resuelta.
- **Avisos a otros dueños (T19):**
  - Ariel Enriquez (HU-16): `PagoService.reembolsarCancelacion(ReembolsoPorCancelacion(reservaId, monto, solicitadoPor,
    ip))`, `MANDATORY`, una vez por reserva dentro de la transacción de la cancelación y solo con `monto > 0`. HU-16
    reemplaza el cuerpo sin cambiar firma ni propagación (tabla `reembolso`, Command `SolicitudReembolso`,
    `PasarelaPago.reembolsar` después del commit); si quita el registro `REEMBOLSO_SOLICITADO`, ajusta R24 de HU-15.
    Migración: HU-16 toma la siguiente `V4_n` libre. Detalle en `specs/HU-15/design.md` § 11.1.
  - Jorge Quispe (HU-25): dos eventos de correo nuevos en `NotificadorCorreo` a contemplar al moverlo a `comun/correo`.
  - Sebastian Garate (HU-21): puede reutilizar `CancelacionReservaService` con `QuienCancela.ARRENDADOR` o agregar una
    estrategia a `CalculoReembolso` (`design.md` § 11.4).
- **Resultado:** `./init.sh` completo en verde en la implementación, en el review y en el cierre (954 unitarias y 660 IT,
  0 fallos, errores ni omitidas; ESLint limpio y build OK). Status → `done`. Todo sin commitear.

## 2026-11-01 — HU-16 Reembolso automático (done)

- **Dueño:** Ariel Enriquez · **Rama:** `feature/HU-16-reembolso-automatico` (desde `develop` en `d977a45`, con HU-15 ya
  fusionada) · **Fecha:** 2026-10-31 a 2026-11-01. Sin commit ni push: el dueño revisa y abre el PR hacia `develop`.
- **Agentes:** orquestador → explorador (`progress/explore_HU-16.md`) → spec-author (`specs/HU-16/`, R1–R43, D1–D13,
  T1–T24; ★ Command y ★ Adapter) → aprobación del dueño → implementer (`progress/impl_HU-16.md`; una pausa `blocked` por
  Docker Desktop apagado, reanudada al encenderlo) → reviewer (`APPROVED`, `progress/review_HU-16.md`) → implementer, cierre.
- **Spec aprobado por:** Ariel Enriquez el 2026-11-01, con D1–D13 confirmadas (entre ellas D1 "`reembolso` es la fuente
  de verdad", D2 "confirmación síncrona `approved`, sin webhook", D3 "llamada después del commit, en el mismo hilo", D5
  "`PENDIENTE` sin respuesta en 2 min se puede reintentar", D6 "clave de idempotencia `espacioya-reembolso-<id>`", D7
  "la simulada falla según `PAGOS_SIMULADA_FALLOS_REEMBOLSO`" y D11 "sin correo de reembolso").
- **Cambios:**
  - Migración `V4_3__reembolso.sql` (tabla `reembolso`, `uq_reembolso_pago_origen` y restricciones con nombre).
  - Dominio `pagos`: `Reembolso` (`admiteIntento`, `motivoNoReintentable`, margen de 2 min), `Pago.marcarReembolsado` /
    fallido, `ReglasPago.idBienEscrito`; `ReembolsoRepository`.
  - ★ Command en `pagos/service/reembolso/`: `ComandoReembolso`, `SolicitudReembolso` (ConcreteCommand),
    `EjecutorReembolsos` (Invoker, después del commit), fábrica `SolicitudesReembolso` y `RegistroReembolsos`
    (`REQUIRES_NEW`). Clients `PagoServiceImpl.reembolsarCancelacion` (`AUTOMATICO`, misma firma y `MANDATORY`) y
    `ReembolsoAdminService` (`ADMIN`).
  - ★ Adapter: `PasarelaPago.reembolsar(SolicitudReembolsoPasarela) → ReembolsoRealizado` en `MercadoPagoAdapter`
    (`X-Idempotency-Key`, cuerpo fuera del log) y `PasarelaSimulada` (aprueba los `sim-`, falla N veces por clave).
  - `ReembolsoAdminController`: `GET /api/admin/reembolsos` y `POST /api/admin/reembolsos/{id}/reintento` (sesión, rol
    ADMIN, IP permitida; DTO `ReembolsoAdminDTO`). Auditoría `REEMBOLSO_SOLICITADO`, `REEMBOLSO_REALIZADO`,
    `REEMBOLSO_FALLIDO`.
  - Frontend: `EtiquetaEstado` (`FALLIDO` error, `PENDIENTE` atención), `api/pagos.js`, `textosPagos.js`,
    `ReembolsosPorAtender.jsx` (+ CSS) en `/admin/reembolsos` y enlace en `Panel`.
  - Docs: `docs/architecture.md` (sección "Reembolsos: qué deja HU-16", `reembolsar`, filas `sim-`),
    `docs/modelo-datos.md` (tabla `reembolso`), `docs/frontend.md` (estados y ruta). Tabla de puertos sin cambios.
  - `.env.example`: una línea comentada `PAGOS_SIMULADA_FALLOS_REEMBOLSO`.
- **Tests:** nuevos `ReembolsoTest`, `SolicitudReembolsoTest`, `EjecutorReembolsosTest`, `ReembolsoAdminServiceTest`,
  `ArquitecturaReembolsoTest`, `RegistroReembolsosIT`, `ReembolsoRestriccionesIT`, `ReembolsoAutomaticoIT`,
  `ReembolsoAdminControllerIT`, `ReembolsoConcurrenciaIT`; casos agregados a `PagoTest`, `ReglasPagoTest`,
  `MercadoPagoAdapterTest`, `PasarelaSimuladaTest`, `PagoServiceImplTest`. De HU-15 solo cambian
  `CancelacionReservaControllerIT.solicitaElReembolso` (espera el pago `REEMBOLSADO`) y las limpiezas `DELETE FROM
  reembolso` en 6 IT. R1–R43 con test (mapa en `progress/impl_HU-16.md`).
- **Review:** `APPROVED` en la primera vuelta (`progress/review_HU-16.md`); desviaciones D-A a D-G declaradas, todas
  aceptadas.
- **Pendiente para el dueño antes del merge:** confirmar la desviación **D-A** (`Reembolso.admiteIntento`: el primer
  intento automático se admite aunque el reembolso recién creado cuente como *en proceso*; el admin sigue recibiendo 400
  *en proceso*, R34).
- **Avisos a otros dueños (T23):**
  - Kevin Chan (HU-15): `PagoService.reembolsarCancelacion` conserva firma, `MANDATORY` y excepciones, pero ahora deja el
    pago `REEMBOLSADO` o `REEMBOLSO_FALLIDO` tras el commit. Las IT nuevas que borren `pago` deben borrar antes
    `reembolso`.
  - Eduardo Saldaña (HU-28): el monto devuelto está en `reembolso.monto`. Para UAT, `PAGOS_SIMULADA_FALLOS_REEMBOLSO=1`
    hace fallar el primer intento (aparece en `/admin/reembolsos`) y el reintento lo aprueba.
- **Resultado:** `./init.sh` completo en verde en la implementación y en el review (1027 unitarias y 690 IT, 0 fallos,
  errores ni omitidas; ESLint limpio y build OK). Status → `done`. Todo sin commitear.

## 2026-11-03 — HU-17 Autenticación multifactor (MFA) (done)

- **Dueño:** Jorge Quispe · **Rama:** `feature/HU-17-autenticacion-multifactor` (desde `develop` en `5aa045f`) ·
  **Fecha:** 2026-11-03. Sin commit ni push: el dueño revisa y abre el PR hacia `develop`.
- **Agentes:** orquestador → spec-author (`specs/HU-17/`, R1–R66 en EARS, T1–T23) → aprobación del dueño → implementer
  (`progress/impl_HU-17.md`) → reviewer (`CHANGES_REQUESTED`, H1 concurrencia y H2 mapa R57) → implementer (vuelta 1) →
  reviewer (`APPROVED`, "Revisión 2" en `progress/review_HU-17.md`) → implementer, cierre.
- **Spec aprobado por:** Jorge Quispe el 2026-11-03, con las decisiones por defecto P1–P12 y las respuestas por defecto
  a las 3 preguntas abiertas (pérdida de app y códigos → borrar la fila de `mfa` por SQL, documentado en README;
  regenerar códigos sin desactivar MFA fuera de alcance; sin correo al activar/desactivar).
- **Decisiones clave:** desafío MFA en tabla `desafio_mfa` (202 + token de 5 min, un solo uso); admin sin MFA →
  configuración obligatoria en el login; `SesionService.validar` rechaza sesiones de admin sin MFA activo; TOTP propio
  (JDK, RFC 6238, SHA-1, 6 dígitos, 30 s, ±1 paso, sin replay); secreto cifrado con `ConvertidorCifrado`; 5 códigos de
  respaldo con HMAC (`HuellaCampos`); límite §9.7 = 5 fallos por desafío + 10 por usuario en 15 min; el control de IPs
  del admin se mantiene y complementa a MFA (P3); sin dependencias nuevas.
- **Cambios:**
  - Migración `V5_1__mfa.sql` (tablas `mfa`, `codigo_respaldo`, `desafio_mfa`).
  - Dominio `cuentas`: `Base32`, `Totp`, `Mfa` (`Persistable`), `CodigoRespaldo`, `DesafioMfa`, enums `PasoMfa`,
    `FactorMfa`, `ContextoMfa`; repositorios `MfaRepository`, `CodigoRespaldoRepository`, `DesafioMfaRepository`,
    `SesionRepository.revocarOtras`.
  - Servicios: `EmisorSesiones`, `GeneradorMfa`, `VerificadorMfa`, `VerificacionMfaService`, `MfaService`;
    `InicioSesionService` (201 o 202 con desafío) y `SesionService.validar` (exige MFA al admin).
  - API: `SesionController` (`POST /api/sesiones` → 201/202, `/api/sesiones/mfa`, `/api/sesiones/mfa/configuracion`,
    `no-store`) y `MfaController` (activar/desactivar desde la cuenta). `VerificacionExpiradaException` → 401.
  - Frontend: `api/cuentas.js` (6 funciones), `IniciarSesion` por pasos, `PasoCodigoMfa`, `ConfiguracionMfa`,
    `CodigosRespaldo`, `SeguridadCuenta` en `/panel/seguridad` + enlace en `Panel`, helper `erroresCampo.js`.
  - Docs: `docs/modelo-datos.md`, `docs/requerimientos.md` (§9 ítems 4 y 7), `docs/architecture.md`, `docs/frontend.md`,
    `README.md` (primer login del admin, pérdida → `DELETE FROM mfa`), `.env.example`, `application.properties`.
- **Tests:** nuevos `Base32Test`, `TotpTest` (vectores RFC), `MfaTest`, `CodigoRespaldoTest`, `DesafioMfaTest`,
  `VerificacionMfaDTOTest`, `DtoMfaTest`, `EmisorSesionesTest`, `GeneradorMfaTest`, `VerificadorMfaTest`,
  `VerificacionMfaServiceTest`, `MfaServiceTest`, `MfaInicioSesionIT` (con 4 IT de concurrencia: R13, R15, R16, R24) y
  `MfaControllerIT`; soporte `MfaDePrueba`. Adaptados: `InicioSesionServiceTest`, `SesionServiceTest`,
  `SesionControllerIT`, `CuentaAdminControllerIT`, `RevisionIdentidadControllerIT`, `FiltroIpsAdminIT`,
  `ManejadorGlobalErroresTest`, `PagosSoporte`, `ReservasControllerSoporte`. R1–R66 con test (mapa en
  `progress/impl_HU-17.md`). Desviaciones menores D-A a D-I declaradas y aceptadas.
- **Notas para el dueño antes del merge:**
  - **Migración `V5_1__mfa.sql`:** no hay otra `V5_*` en `develop` hoy; si HU-18 llega antes con `V5_1`, renombrar esta
    a la siguiente libre (`docs/conventions.md`).
  - **Pantallas sin revisión visual en navegador:** pasan lint y build; la revisión queda para la caja negra de UAT
    (login con TOTP y con respaldo, primer login del admin con configuración y códigos, `/panel/seguridad`).
  - **Hallazgo informativo H3:** en el paso de códigos de respaldo el token ya está guardado; si el admin recarga,
    `ProveedorSesion` restaura la sesión y no vuelve a ver sus 5 códigos. Conforme al design § 12; anotado para UAT.
  - Despliegue: una sola instancia (límite por cuenta en memoria), reloj del servidor con NTP; los admins existentes
    pierden sus sesiones abiertas y configuran MFA en su siguiente login.
- **Resultado:** `./init.sh` completo en verde en la implementación, en el review y en el cierre (1189 unitarias y 736
  IT, 0 fallos, errores ni omitidas; ESLint limpio y build OK; único WARN, llaves de cifrado locales, ajeno). Status →
  `done`. Todo sin commitear.

## 2026-11-03 — HU-18 Registro de cuenta bancaria del arrendador (done)

- **Agentes:** spec-author, implementer, reviewer · **Dueño:** Ariel Enriquez
- **Spec aprobado por:** Ariel Enriquez el 2026-11-03, con los valores por defecto de P1-P9.
- **Cambios:**
  - Migración `V5_2__cuenta_bancaria.sql` (FK a `perfil_arrendador`, una cuenta por arrendador, 3 CHECK).
  - Dominio `pagos`: `CuentaBancaria` (número cifrado con `ConvertidorCifrado`, retención de 72 h al cambiar),
    `EstadoCuentaBancaria`; `CuentaBancariaRepository`; DTO `GuardarCuentaBancariaDTO`, `CuentaBancariaDTO`,
    `MiCuentaBancariaDTO` (número siempre enmascarado); `CuentaBancariaService` (rol, identidad verificada, límite de
    5 fallos de contraseña, auditoría, aviso por correo tras el commit); `CuentaBancariaController` (`no-store`).
  - Puertos en `cuentas`: `CuentaService.confirmarContrasena` y `razonSocialDe`; `NotificadorCorreo.notificarCambioCuentaBancaria`
    con `AvisoCambioCuentaBancaria` (el simulado no registra datos sensibles).
  - Frontend: `api/pagos.js`, `EtiquetaEstado` (DISPONIBLE, RETENIDA), `pages/pagos/CuentaBancaria.jsx` en
    `/panel/cuenta-bancaria` y enlace en `Panel`.
  - Docs: `docs/modelo-datos.md`, `docs/architecture.md`, `docs/frontend.md`.
- **Tests:** `CuentaBancariaTest`, `GuardarCuentaBancariaDTOTest`, `CuentaBancariaServiceTest`, `CuentaBancariaRepositoryIT`,
  `CuentaBancariaControllerIT`; ampliados `CuentaServiceImplTest`, `NotificadorCorreoSimuladoTest` y los dobles de
  `NotificadorCorreo` en las IT existentes. R1-R25 con test (mapa en `progress/impl_HU-18.md`).
- **Resultado:** `./init.sh` completo en verde (1243 unitarias y 755 IT, 0 fallos; ESLint y build OK; JDK 25). Status →
  `done`. Todo sin commitear.
