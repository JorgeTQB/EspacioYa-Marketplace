# EspacioYa — Marketplace de alquiler de espacios

Marketplace web para alquilar espacios deportivos y para eventos (canchas,
estadios, auditorios) con pago en línea.

- **Roles:** Usuario (arrendatario), Arrendador y Administrador.
- **Alcance:** 28 historias de usuario en 2 releases.
  - **Release 1 (MVP transaccional):** publicar → buscar → reservar → pagar →
    cancelar con reembolso.
  - **Release 2 (confianza y operación):** MFA, cobro del arrendador, perfil y
    datos personales, filtros, reseñas, reclamos y moderación.
- **Contexto:** Proyecto Final de Ingeniería de Software II (Universidad de
  Lima, 2026-2), con Scrum.

## Estructura

| Carpeta              | Contenido |
|----------------------|-----------|
| `1_Database/`        | PostgreSQL 17 local (`compose.yaml`) |
| `2_Backend/`         | API REST en Spring Boot (Java) |
| `3_frontend/`        | SPA en React + Vite |
| `4_Infraestructure/` | Arquitectura de despliegue en AWS (`arquitectura-aws.html`) |
| `5_Test/`            | Pruebas (vacía por ahora; las pruebas del backend viven en `2_Backend/src/test/`) |
| `docs/`              | Historias, requerimientos, arquitectura y convenciones |

Si trabajas con un agente de IA, el punto de entrada es [`AGENTS.md`](AGENTS.md).

## Cómo trabajar una historia con Claude Code

El repositorio trae un arnés de **Spec Driven Development**: Claude no
programa una historia sin que su dueño apruebe antes un spec.

| Pieza | Archivos |
|-------|----------|
| El repositorio es el sistema | `AGENTS.md`, `init.sh`, `feature_list.json`, `specs/`, `progress/`, `docs/` |
| Orquestación con subagentes | `.claude/agents/`: `orquestador`, `spec_author`, `implementer`, `reviewer` |
| Spec Driven Development | `docs/specs.md`: EARS y aprobación del dueño en `spec_ready` |
| Supervisión | `CHECKPOINTS.md`, hook de `.claude/settings.json`, pruebas en `2_Backend/src/test/` |

### Antes de empezar

Necesitas JDK 25, Node 20.19 o superior y Docker Desktop encendido. En
Windows, corre los comandos desde Git Bash.

```bash
./init.sh            # verificación completa; debe terminar en verde
./init.sh --rapido   # sin pruebas de integración ni build, para empezar rápido
```

### Paso a paso

1. Abre Claude Code en la raíz del repo (`claude`). `CLAUDE.md` lo obliga a
   actuar como **orquestador**: coordina subagentes y no edita código.
2. Dile quién eres, con el nombre que aparece en `owner` de
   `feature_list.json`: **«Soy Jorge Quispe, trabaja mi siguiente historia»**
   (o «trabaja HU-03»).
3. **Fase spec.** El `spec_author` escribe
   `specs/HU-XX/{requirements,design,tasks}.md` y la historia pasa a
   `spec_ready`. Claude se detiene.
4. Lee los tres archivos y responde **«aprobado»** o pide cambios. Solo vale
   la aprobación del dueño de la historia.
5. **Fase código.** El `implementer` sigue las tasks marcándolas `[x]` y el
   `reviewer` comprueba que cada `R<n>` tenga su test y que `./init.sh` esté en
   verde. Si el reviewer aprueba, la historia queda `done`.
6. Revisa los cambios, haz commit en tu rama `feature/HU-XX-descripcion-corta`
   y abre el PR hacia `develop`. Claude no hace commit ni push.

Si algo queda `blocked`, la razón y la pregunta para ti están en
`progress/HU-XX.md`.

### Dónde queda cada cosa

| Archivo | Lo escribe | Contiene |
|---------|------------|----------|
| `specs/HU-XX/requirements.md` | spec_author | Requirements en EARS (`R1`, `R2`…) y trazabilidad con los CA y STRIDE |
| `specs/HU-XX/design.md` | spec_author | Endpoints, clases, migraciones, diagramas y alternativa descartada |
| `specs/HU-XX/tasks.md` | spec_author | Checklist; el implementer la marca `[x]` |
| `progress/HU-XX.md` | orquestador e implementer | Estado vivo de la historia, aprobación y bloqueos |
| `progress/explore_HU-XX.md` | explorador | Código existente que la historia puede reutilizar |
| `progress/impl_HU-XX.md` | implementer | Archivos tocados y mapa `R<n> → test` |
| `progress/review_HU-XX.md` | reviewer | Veredicto contra `docs/`, el spec y `CHECKPOINTS.md` |
| `feature_list.json` | orquestador, spec_author e implementer | Estado: `pending` → `spec_ready` → `in_progress` → `done` |
| `progress/history.md` | implementer | Resumen de cada sesión cerrada (solo se agrega) |

Los subagentes no devuelven su trabajo por el chat: lo dejan en estos
archivos y queda versionado en git.
