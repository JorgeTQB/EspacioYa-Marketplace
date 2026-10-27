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
