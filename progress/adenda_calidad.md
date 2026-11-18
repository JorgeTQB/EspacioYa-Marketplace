# Adenda de escenarios de calidad (AC001, AC002, AC003, AC005) — spec-author, 2026-11-16

Fuente: `PROMPT-escenarios-calidad.md` § 1–3. Patrón: adenda de HU-13 del 2026-10-30 (sección nueva en `requirements.md`,
sección nueva en `design.md` y tasks nuevas al final de `tasks.md`; nada se renumera ni se reescribe). Sin `R<n>` nuevos
ni cambios de producción: las adendas citan requisitos existentes. **Ningún `status` de `feature_list.json` cambió.**

## Archivos tocados

| Historia (dueño) | Archivo | Qué se agregó |
|---|---|---|
| HU-12 (Eduardo Saldaña) | `specs/HU-12/requirements.md` | "Adenda 2026-11-16 — Escenarios de calidad AC001 y AC002" (R19, R24; HU-08 R20, R40) |
| | `specs/HU-12/design.md` | § 14 (cómo se arman, qué ejercitan, límites, alternativas 8–10) |
| | `specs/HU-12/tasks.md` | T24 `veinteUsuariosElMismoBloque`, T25 `veinteReservasYBloqueosDeFecha`, T26 verificación |
| HU-13 (Eduardo Saldaña) | `specs/HU-13/requirements.md` | "Adenda 2026-11-16 — Escenario de calidad AC003" (R43; R42, R44, R45, R48, R49, R53) |
| | `specs/HU-13/design.md` | § 15 (alternativas 19–20) |
| | `specs/HU-13/tasks.md` | T44 `cincoWebhooksIguales`, T45 verificación |
| HU-16 (Ariel Enriquez) | `specs/HU-16/requirements.md` | "Adenda 2026-11-16 — Escenario de calidad AC005" (R22, R23; R19, R21, R30, R33, R34, R36, R39) |
| | `specs/HU-16/design.md` | § 15 (alternativas 12–13) |
| | `specs/HU-16/tasks.md` | T25 `diezReintentosALaVez`, T26 verificación |

Cada adenda trae: escenario y medida, solicitudes simultáneas y rondas (20×5, 20×5, 5×5, 10×5), aserciones, la regla de
límites (solo configuración de prueba con mecanismo existente, documentado) y la regla de defectos (`DEF-xx`, sin tocar
`2_Backend/src/main`).

## Dudas abiertas

1. **`init.sh` falla mientras las tasks nuevas estén abiertas.** `init.sh` (líneas 216–219) da `[FAIL]` si una historia
   `done` tiene `- [ ]` en `tasks.md`. Con las adendas, HU-12 (3), HU-13 (2) y HU-16 (2) quedan así hasta que el
   implementer las marque. La adenda de HU-13 de 2026-10-30 no sirve de precedente: se hizo con la historia en
   `in_progress` y **no cambió `status`**. Volver a `in_progress` tampoco sirve para HU-12 y HU-13 a la vez (mismo dueño,
   máximo 1 `in_progress`). Decide el orquestador o el dueño: aceptar el rojo temporal hasta T26/T45/T26, o pasar las
   historias de a una a `in_progress`.
2. **HU-16 cita también R21, R33 y R34.** Con 10 reintentos, un rechazo puede ser *ya reembolsado*
   (`MENSAJE_YA_REEMBOLSADO`, R21/R33) además de *en proceso* (`MENSAJE_EN_PROCESO`, R22/R34). La adenda acepta los dos y
   exige al menos un *en proceso*. Confirmar con Ariel Enriquez.
3. **AC002: horas distintas por usuario.** Para que la única carrera sea reserva frente a bloqueo, cada uno de los 10
   usuarios pide una hora distinta. Así, sin bloqueo pueden quedar hasta 10 reservas activas en la fecha; el criterio es
   "nunca coexisten", no "reservas + bloqueos = 1" como en la prueba de 2 solicitudes. Confirmar con Eduardo Saldaña.
4. **Pool de conexiones.** Las pruebas usan el pool por defecto (Hikari, 10; no hay `spring.datasource.hikari.*` en
   `src/test/resources/config/application.properties`). AC001 y AC002 lanzan 20 hilos: la mitad esperará conexión. Si
   hubiera que ajustarlo, ¿cuenta `spring.datasource.hikari.maximum-pool-size` en la configuración de prueba como
   "mecanismo ya existente"? Ninguna `ReglaLimite` cubre estas rutas (el inicio de sesión solo cuenta fallos).
5. **AC003 y `StubMercadoPago`.** Si el doble atiende las consultas de a una, las 5 notificaciones llegan escalonadas al
   bloqueo y la carrera es menor de lo que parece. La adenda pide medirlo tal cual y documentarlo, sin cambiar el doble.
