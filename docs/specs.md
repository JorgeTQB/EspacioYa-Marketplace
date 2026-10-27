# Spec Driven Development (SDD)

> Este proyecto sigue un flujo Kiro-style: requirements → design → tasks → code.
> El código no se escribe hasta que el spec está aprobado por un humano.

## Estructura

Cada historia (`"sdd": true` en `feature_list.json`) tiene una carpeta
dedicada en cuanto deja `pending`:

```
specs/HU-XX/
├── requirements.md   # QUÉ se necesita (EARS notation)
├── design.md         # CÓMO se construirá (decisiones técnicas)
└── tasks.md          # PASOS concretos a implementar
```

El nombre de la carpeta coincide con el id de la historia (`HU-01`, `HU-13`…).

**Fuentes del spec** (el `spec_author` las lee antes de escribir):

- `docs/historias/HU-XX.md` — historia, criterios de aceptación (CA) y STRIDE.
- Los archivos de las historias de las que depende.
- Los RF, RNF y RN de la historia en `docs/requerimientos.md` (matriz §7).
- `docs/architecture.md` y `docs/conventions.md`.

## Estados de una historia

| Estado         | Significado                                                    |
|----------------|----------------------------------------------------------------|
| `pending`      | Sin spec. El `spec_author` es el primero en actuar.            |
| `spec_ready`   | Spec redactado. Esperando aprobación del dueño. NO se toca código. |
| `in_progress`  | Spec aprobado. `implementer` trabajando.                       |
| `done`         | Pruebas verdes, `reviewer` aprobó, sesión cerrada.             |
| `blocked`      | Atascado. Razón en `progress/HU-XX.md`.                        |

Puede haber varias historias `in_progress` a la vez, pero como máximo una por dueño.

## La puerta de aprobación humana

El flujo automático se detiene **una vez**: cuando el `spec_author` termina
sus tres archivos, marca la historia como `spec_ready` y para. El **dueño de la
historia** lee `specs/HU-XX/` y dice "aprobado" (o pide cambios).

Solo entonces el `orquestador` transiciona `spec_ready → in_progress` y lanza
el `implementer`.

```
pending → [spec_author] → spec_ready → ⏸ DUEÑO → in_progress → [implementer → reviewer] → done
```

## requirements.md — EARS estricto

Las requirements se redactan en **EARS** (Easy Approach to Requirements
Syntax). Cada requirement es un párrafo numerado con uno de estos cinco
patrones:

| Patrón         | Plantilla                                                   |
|----------------|-------------------------------------------------------------|
| **Ubicuo**     | `El sistema DEBE <acción>.`                                 |
| **Evento**     | `CUANDO <disparador>, el sistema DEBE <acción>.`            |
| **Estado**     | `MIENTRAS <estado>, el sistema DEBE <acción>.`              |
| **Opcional**   | `DONDE <feature opcional>, el sistema DEBE <acción>.`       |
| **No deseado** | `SI <evento no deseado> ENTONCES el sistema DEBE <acción>.` |

Reglas duras:

- Cada requirement tiene un id estable: `R1`, `R2`, ...
- Cada requirement DEBE ser verificable por al menos un test concreto.
- No mezcles varios `DEBE` en un mismo requirement. Si hay más de uno, parte.
- No uses verbos blandos ("podría", "puede", "soporta"). Solo `DEBE` / `NO DEBE`.
- Todo CA y todo control STRIDE de la historia queda cubierto por al menos un `R<n>`.

Ejemplo:

```markdown
## R1
CUANDO un visitante envía `POST /api/usuarios` con correo válido, contraseña
que cumple la política y la aceptación de privacidad marcada, el sistema DEBE
crear la cuenta en estado de correo pendiente.

## R2
SI el visitante envía el registro sin marcar la aceptación de privacidad
ENTONCES el sistema DEBE rechazarlo con 400 sin crear la cuenta.
```

Al final del archivo va la tabla de trazabilidad con la historia:

```markdown
## Trazabilidad con la historia

| Origen (docs/historias/HU-01.md)                 | Cubierto por |
|--------------------------------------------------|--------------|
| CA1: cuenta creada y correo de verificación      | R1, R3       |
| CA2: sin aceptación no se completa               | R2           |
| (T) contraseña con hash y sal                    | R5           |
```

## design.md — decisiones técnicas

Captura **antes** de tocar código:

- Qué archivos se crean / modifican (por módulo y capa).
- Caso de uso y diagrama de secuencia del flujo principal y de un flujo de
  error (PlantUML o Mermaid; luego se pasan a la herramienta CASE del curso).
- Endpoints con su método, ruta, DTO de entrada y de salida y códigos de respuesta.
- Clases nuevas y sus firmas públicas (entidades, servicios, repositorios).
- Migraciones Flyway y restricciones nuevas en la base de datos.
- Qué excepciones de `comun/error/` se reutilizan o se añaden.
- Qué componentes de `comun` se reutilizan (auditoría, rate limiting, correo…).
- Si la historia es ★: cómo se aplica su patrón (ver `docs/architecture.md`).
- Qué alternativa se descartó y por qué (mínimo una).

NO es ingeniería desde primeros principios — apóyate en
`docs/architecture.md` y `docs/conventions.md`. El `design.md` documenta los
puntos donde tu historia roza la frontera de esas reglas.

## tasks.md — checklist ejecutable

Pasos discretos en orden, cada uno con checkbox. Cada task referencia al
menos un `R<n>` que cubre.

Ejemplo:

```markdown
- [ ] T1 — Crear migración `V1_1__usuarios_y_tokens.sql` con `usuario.correo` único. Cubre: R4.
- [ ] T2 — Añadir `RegistroService.registrar(RegistroUsuarioDTO)`. Cubre: R1, R2, R5.
- [ ] T3 — Añadir `RegistroController` con `POST /api/usuarios`. Cubre: R1, R2.
- [ ] T4 — Añadir `RegistroServiceTest` con un test por R1, R2 y R5. Cubre: R1, R2, R5.
```

El `implementer` marca `[x]` cada task al completarla. El `reviewer`
rechaza si queda alguna `[ ]` sin justificación documentada.

## Trazabilidad (regla dura)

- Cada test debe poder mapearse a un `R<n>` de su spec (vía `@DisplayName`).
- Cada `R<n>` debe tener al menos un test concreto.
- El `reviewer` comprueba esta correspondencia explícitamente y rechaza
  si falta.

El `implementer` documenta el mapa en `progress/impl_HU-XX.md`:

```markdown
## Trazabilidad
- R1 → `RegistroServiceTest.registraUsuarioConDatosValidos`
- R2 → `RegistroServiceTest.rechazaRegistroSinAceptarPrivacidad`
- R3 → `RegistroControllerIT.enviaCorreoDeVerificacion`
```

## Cuándo NO aplica SDD

Las tareas que no son una historia de usuario (configurar el entorno, agregar
dependencias, ajustar el arnés) van con `"sdd": false` y no tienen spec.
