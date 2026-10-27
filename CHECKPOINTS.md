# CHECKPOINTS — Evaluación del estado final

> En sistemas multi-agente no se evalúa el camino, se evalúa el destino.
> Estos son los checkpoints objetivos que un juez (humano o IA) puede usar
> para decidir si el proyecto está sano. Los marcados con ⚙ los comprueba
> `./init.sh` automáticamente; el resto los revisa el `reviewer`.

## C1 — El arnés está completo

- [ ] ⚙ Existen los archivos base: `AGENTS.md`, `CHECKPOINTS.md`, `init.sh`,
      `feature_list.json`, `progress/_plantilla.md`, `progress/history.md`.
- [ ] ⚙ Existen los docs: `docs/architecture.md`, `docs/conventions.md`,
      `docs/specs.md`, `docs/verification.md`, `docs/requerimientos.md`,
      `docs/modelo-datos.md`.
- [ ] ⚙ Toda historia de `feature_list.json` tiene su `docs/historias/HU-XX.md`.
- [ ] ⚙ Existen los 4 subagentes en `.claude/agents/`: `orquestador`, `spec_author`,
      `implementer`, `reviewer`.
- [ ] ⚙ `./init.sh` (sin `--rapido`) termina con exit code 0.

## C2 — El estado es coherente

- [ ] ⚙ Como mucho una historia `in_progress` por dueño (`owner`) en
      `feature_list.json`.
- [ ] ⚙ Toda historia `in_progress` o `blocked` tiene su `progress/HU-XX.md`.
- [ ] Toda historia `done` tiene tests asociados (`@DisplayName("HU-XX R<n>: …")`)
      y pasan.
- [ ] Ninguna historia pasó a `in_progress` sin la aprobación de su dueño
      (queda anotada en `progress/HU-XX.md`).
- [ ] Los `progress/HU-XX.md` describen solo la sesión activa (sin basura de
      sesiones anteriores).

## C3 — El código respeta la arquitectura

- [ ] ⚙ El código Java vive en `pe.edu.ulima.espacioya.<modulo>` con un módulo
      de la tabla de `docs/architecture.md` (`cuentas`, `espacios`, `busqueda`,
      `reservas`, `pagos`, `administracion`, `comun`).
- [ ] ⚙ Ningún `controller` importa un `repository`.
- [ ] ⚙ Sin `@Autowired` en `src/main` (inyección por constructor), sin Lombok
      y sin imports con comodín.
- [ ] ⚙ Sin `System.out.println`, `printStackTrace()` ni `console.log` de
      depuración.
- [ ] Un módulo solo usa otro módulo a través de su interfaz de `service` o de
      un puerto en `service/puertos/`; sin dependencias circulares. Todo puerto
      nuevo está en la tabla de `docs/architecture.md`.
- [ ] La API recibe y devuelve DTO (`record`); ninguna entidad JPA sale del
      backend ni se exponen campos sensibles.
- [ ] La identidad del usuario sale de la sesión, nunca de un id en la URL o el
      cuerpo. Montos, roles y estados se calculan solo en el servidor.
- [ ] El esquema sale solo de migraciones Flyway `V<sprint>_<n>__<descripcion>.sql`
      y ninguna migración ya subida a `develop` fue editada.
- [ ] Las integraciones externas (pasarela, correo, almacenamiento) están
      detrás de un Adapter.
- [ ] No se implementó nada de la lista "fuera del alcance" de
      `docs/architecture.md`.
- [ ] ⚙ No hay secretos en el repositorio (`.env` sin trackear; solo
      `2_Backend/.env.example`).
- [ ] ⚙ Frontend: ningún color (`#hex`, `rgb()`, `hsl()`) fuera de
      `3_frontend/src/styles/tokens.css` y ninguna librería de componentes o
      de CSS en `package.json`.
- [ ] Frontend: las pantallas usan el kit de `src/components/ui/`, muestran
      errores y éxitos según `docs/frontend.md` y llaman a la API solo desde
      `src/api/`.

## C4 — La verificación es real

- [ ] Las clases de prueba siguen `<Clase>Test` (unitaria, Mockito) y
      `<Clase>IT` (integración, `@Import(TestcontainersConfiguration.class)`).
- [ ] No se usa H2: la integración corre contra PostgreSQL real (Testcontainers).
- [ ] Los plazos (15 min, 48 h, 72 h) se prueban con un `Clock` fijo, no con la
      hora real ni con esperas.
- [ ] Las historias con endpoints, migraciones o restricciones de BD tienen al
      menos una prueba `*IT`; las reglas de concurrencia se prueban lanzando
      las dos operaciones a la vez.
- [ ] Cada test comprueba un resultado concreto (estado, monto, código HTTP,
      campos del DTO), incluido el camino de error.
- [ ] ⚙ `cd 2_Backend && ./mvnw verify` muestra > 0 tests y todos verdes.
- [ ] ⚙ `cd 3_frontend && npm run lint && npm run build` termina sin errores.

## C5 — La sesión se cerró bien

- [ ] No hay archivos sin trackear sospechosos (`*.tmp`, logs, `target/`,
      `node_modules/` fuera del `.gitignore`).
- [ ] `progress/history.md` tiene una entrada por la última sesión.
- [ ] El `progress/HU-XX.md` de una historia terminada volvió a la plantilla.
- [ ] La última historia trabajada está reflejada en su estado correcto en
      `feature_list.json`.
- [ ] El trabajo está en su rama `feature/HU-XX-descripcion-corta`, no en
      `develop` ni en `master`.

## C6 — Spec Driven Development

- [ ] ⚙ Toda historia con `"sdd": true` en `spec_ready`, `in_progress` o `done`
      tiene su carpeta `specs/HU-XX/` con `requirements.md`, `design.md` y
      `tasks.md`.
- [ ] `requirements.md` usa EARS estricto (ver `docs/specs.md`) y cierra con la
      tabla de trazabilidad: todo CA y todo control STRIDE de
      `docs/historias/HU-XX.md` queda cubierto por al menos un `R<n>`.
- [ ] `design.md` tiene endpoints, clases, migraciones, diagrama de secuencia y
      al menos una alternativa descartada. Si la historia es ★, aplica y
      muestra su patrón.
- [ ] ⚙ Toda historia `done` tiene todas sus tasks marcadas `[x]` en `tasks.md`.
- [ ] Cada `R<n>` de `requirements.md` está cubierto por al menos un test
      concreto, documentado en `progress/impl_HU-XX.md`.
- [ ] Ningún `R<n>` resuelve por su cuenta un pendiente de
      `docs/requerimientos.md` §9 sin que el dueño lo haya decidido.

---

**Cómo usar este archivo:** el agente revisor (`.claude/agents/reviewer.md`)
recorre cada checkbox, marca `[x]` o `[ ]`, y rechaza el cierre de la historia
si quedan boxes vacíos en C1-C6 que apliquen a ella.
