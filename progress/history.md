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
