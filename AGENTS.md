# AGENTS.md — Mapa de navegación para agentes de IA

> Este archivo es el **punto de entrada** para cualquier agente que trabaje en este
> repositorio. NO es una biblia de reglas: es un **mapa**. Lee solo lo que
> necesites cuando lo necesites (divulgación progresiva).

---

## 1. Antes de empezar (obligatorio)

1. Ejecuta `./init.sh` y verifica que termina sin errores. Si falla, **para**
   y resuelve el entorno antes de tocar código.
2. Lee `progress/HU-XX.md` de la historia que vas a trabajar para entender en qué
   estado quedó la última sesión.
3. Lee `feature_list.json`. Toda historia de usuario nueva (`"sdd": true`) pasa por
   **Spec Driven Development** — ver `docs/specs.md` y §4 de este archivo.
4. Lee `docs/specs.md` antes de tocar cualquier spec o historia `sdd: true`.

## 2. Mapa del repositorio

| Archivo / carpeta            | Qué contiene                                                                | Cuándo leerlo |
|------------------------------|-----------------------------------------------------------------------------|---------------|
| `feature_list.json`          | Lista de historias (HU-XX) con dueño y estado (`pending` / `spec_ready` / `in_progress` / `done` / `blocked`) | Siempre, al empezar |
| `progress/HU-XX.md`          | Estado de la sesión actual de esa historia (un archivo por historia)        | Siempre, al empezar |
| `progress/history.md`        | Bitácora append-only de sesiones anteriores                                 | Si necesitas contexto histórico |
| `specs/HU-XX/`               | `requirements.md` + `design.md` + `tasks.md` (Kiro-style)                   | Antes de implementar cualquier historia con `"sdd": true` |
| `docs/historias/HU-XX.md`    | Historia, criterios de aceptación y controles STRIDE                        | Antes de redactar el spec de esa historia |
| `docs/architecture.md`       | Qué significa "hacer un buen trabajo" en este proyecto                      | Antes de implementar |
| `docs/conventions.md`        | Reglas de estilo, nombres, estructura                                       | Antes de escribir código |
| `docs/specs.md`              | Proceso SDD: EARS notation, los 3 archivos, puerta de aprobación humana     | Antes de redactar o leer un spec |
| `docs/verification.md`       | Cómo verificar que tu trabajo funciona (incluye trazabilidad requirements)  | Antes de declarar una historia como `done` |
| `CHECKPOINTS.md`             | Criterios objetivos de "estado final correcto"                              | Para auto-evaluarte |
| `.claude/agents/`            | Definiciones de subagentes (`leader`, `spec_author`, `implementer`, `reviewer`) | Si orquestas trabajo |

## 3. Reglas duras (no negociables)

- **Una sola historia a la vez.** No mezcles cambios de varias historias en la misma
  sesión. Puede haber varias historias `in_progress` en el repo, pero como máximo
  una por dueño.
- **No declares una historia `done` sin pruebas verdes.** Ejecuta `./init.sh` y
  asegúrate de que el bloque de tests pasa al 100%.
- **No saltes la fase de spec.** Toda historia con `"sdd": true` debe pasar
  por `spec_author` y obtener aprobación humana antes de tocar código.
- **No saltes la puerta de aprobación humana.** El leader detiene el flujo
  en `spec_ready` y espera.
- **Documenta lo que haces** en `progress/HU-XX.md` mientras trabajas, no al final.
- **Deja el repositorio limpio** antes de cerrar la sesión (ver §5).
- **Si no sabes algo, busca en `docs/`** antes de inventarlo.

## 4. Flujo de trabajo (SDD)

```
pending → [spec_author] → spec_ready → ⏸ DUEÑO → in_progress → [implementer → reviewer] → done
```

1. El leader detecta la primera historia `pending` con `"sdd": true` del dueño.
2. El leader lanza `spec_author`, que crea
   `specs/HU-XX/{requirements,design,tasks}.md` y marca el status como
   `spec_ready`.
3. **Pausa.** El dueño de la historia lee el spec en `specs/HU-XX/` y aprueba
   (o pide cambios).
4. Una vez aprobado, el leader cambia el status a `in_progress` y lanza `implementer`.
5. El implementer ejecuta `tasks.md` una a una, marcándolas `[x]`.
6. El reviewer verifica trazabilidad `R<n>` ↔ test y tasks completas;
   aprueba o rechaza.
7. Si aprueba, el implementer marca `done` y mueve el resumen a
   `progress/history.md`.

## 5. Cierre de sesión (lifecycle)

Antes de terminar:

1. Ejecuta `./init.sh` — todo verde.
2. Si la historia está acabada: marca `status: "done"` en `feature_list.json`.
3. Mueve el resumen de `progress/HU-XX.md` al final de `progress/history.md`.
4. Vacía `progress/HU-XX.md` dejando solo la plantilla.
5. No dejes archivos temporales, ni prints de debug, ni TODOs sin contexto.

## 6. Si te bloqueas

- Relee la sección relevante de `docs/`.
- Si la herramienta no hace lo que esperas, **no inventes un workaround**:
  documenta el bloqueo en `progress/HU-XX.md` y para la sesión.
