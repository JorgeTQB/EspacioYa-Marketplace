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
