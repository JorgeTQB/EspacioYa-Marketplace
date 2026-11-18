# Evidencia de escenarios de calidad AC001, AC002, AC003 y AC005

Sesión del 2026-11-16 (hora de Lima, -05:00), rama `develop`. Ejecuta: Sebastian Garate (orquestador).
Adendas en `specs/HU-12/`, `specs/HU-13/` y `specs/HU-16/` (sección "Adenda 2026-11-16"), aprobadas en esta sesión por
sus dueños: **Eduardo Saldaña** (HU-12 y HU-13, incluido el criterio de AC002) y **Ariel Enriquez** (HU-16).

Solo se agregaron pruebas de integración. No se tocó `2_Backend/src/main` ni la configuración de prueba, y ninguna prueba
existente cambió (verificado por el reviewer con `git diff`). Detalle de cada adenda: `progress/impl_HU-12.md`,
`progress/impl_HU-13.md`, `progress/impl_HU-16.md`; veredictos: `progress/review_HU-12.md`, `progress/review_HU-13.md`,
`progress/review_HU-16.md` (los tres **APROBADO**).

Comando de cada clase (Failsafe, sin unitarias):
`./2_Backend/mvnw -f 2_Backend/pom.xml verify -Dit.test=<Clase> -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`.
Cifras copiadas de `2_Backend/target/failsafe-reports/`.

---

## AC001 — Confiabilidad – Consistencia (HU-12)

- **Prueba:** `ReservaConcurrenciaIT.veinteUsuariosElMismoBloque`
- **@DisplayName:** `HU-12 R19 (AC001): 20 usuarios a la vez con el mismo bloque: un 201, 19 rechazos y una fila activa`
- **Carga:** 20 `POST /api/reservas` simultáneos (20 usuarios distintos, mismo espacio, fecha y hora) × **5 rondas**
  = 100 intentos por corrida.
- **Afirma por ronda:** exactamente 1 respuesta 201; 19 respuestas 400 con *"Horario no disponible. Elige otro bloque."*;
  exactamente 1 fila activa en `reserva_bloque` para ese espacio, fecha y hora.

| Corrida | Inicio | Resultado (literal, clase completa) | Tiempo del método |
|---|---|---|---|
| 1 | 21:39:31 | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 27.55 s` | 5.934 s |
| 2 | 21:40:21 | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 26.27 s` | 5.86 s |
| 3 | 21:41:05 | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 26.69 s` | 6.689 s |

**Resultado:** cumple. 15 de 15 rondas con un 201, 19 rechazos y una sola fila activa.

## AC002 — Confiabilidad – Consistencia (HU-12)

- **Prueba:** `ReservaConcurrenciaIT.veinteReservasYBloqueosDeFecha`
- **@DisplayName:** `HU-12 R24 (AC002): 10 reservas y 10 bloqueos de la misma fecha a la vez: nunca coexisten bloqueo y reserva`
- **Carga:** 20 solicitudes simultáneas sobre la misma fecha del mismo espacio: 10 `POST /api/reservas` (10 usuarios,
  una hora distinta cada uno) y 10 `POST /api/arrendadores/actual/espacios/{id}/bloqueos` del arrendador dueño,
  × **5 rondas** (una fecha distinta por ronda).
- **Afirma por ronda:** toda respuesta es 201 o 400; a lo sumo 1 fila en `bloqueo_fecha` (HU-08 R40); si hay bloqueo,
  0 reservas activas en la fecha; si no hay bloqueo, ningún bloqueo 201 y filas activas = reservas 201; nunca coexisten
  bloqueo y reserva activa; cada rechazo trae su mensaje (*horario no disponible*, *ya bloqueada* o *reserva pendiente*).
- **Criterio aprobado por el dueño:** se reemplaza "reservas + bloqueos = 1" de la prueba de 2 solicitudes por "nunca
  coexisten y como máximo un bloqueo", porque sin bloqueo pueden quedar hasta 10 reservas legítimas en horas distintas.

| Corrida | Inicio | Resultado (literal, clase completa) | Tiempo del método |
|---|---|---|---|
| 1 | 21:39:31 | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 27.55 s` | 3.362 s |
| 2 | 21:40:21 | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 26.27 s` | 3.681 s |
| 3 | 21:41:05 | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 26.69 s` | 4.028 s |

**Resultado:** cumple. 0 rondas con bloqueo y reserva activa a la vez.
**Limitación:** la prueba no registra qué lado ganó en cada ronda, así que no se puede afirmar que ambos casos (gana el
bloqueo / ganan las reservas) se ejercitaron; la exclusión se cumplió en todas.

## AC003 — Confiabilidad – Consistencia (HU-13)

- **Prueba:** `PagoConcurrenciaIT.cincoWebhooksIguales`
- **@DisplayName:** `HU-13 R43 (AC003): cinco notificaciones iguales a la vez dejan una fila, una confirmación y un correo de cada tipo`
- **Carga:** 5 `POST /api/pagos/webhook` simultáneos e iguales (mismo `data.id` aprobado, firma válida) × **5 rondas**.
- **Afirma por ronda:** las 5 respuestas son 200; exactamente 1 fila en `pago` con ese `id_transaccion_pasarela`; la
  reserva queda `CONFIRMADA` con exactamente 1 auditoría `RESERVA_CONFIRMADA`; 1 comprobante al usuario y 1 aviso al
  arrendador.

| Corrida | Inicio | Resultado (literal, clase completa) | Tiempo del método |
|---|---|---|---|
| 1 | 21:59:42 | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 17.92 s` | 0.403 s |
| 2 | 22:00:21 | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 17.14 s` | 0.362 s |
| 3 | 22:00:59 | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 21.37 s` | 0.414 s |

**Resultado:** cumple. 15 de 15 rondas con 1 pago, 1 confirmación y 1 correo de cada tipo.
**Observación:** el doble `StubMercadoPago` atiende en paralelo (no escalona las entregas). Las 4 notificaciones
repetidas se frenaron siempre por R42 (id ya guardado tras el bloqueo); la ruta de R44 (violación de unicidad atrapada)
no se ejercitó por la API y sigue cubierta por `WebhookPagoServiceTest`.

## AC005 — Confiabilidad – Consistencia (HU-16)

- **Prueba:** `ReembolsoConcurrenciaIT.diezReintentosALaVez`
- **@DisplayName:** `HU-16 R22, R23 (AC005): diez reintentos a la vez de un FALLIDO llaman una vez a la pasarela, responden un 200 y nueve 400, y dejan un solo reembolso`
- **Carga:** 10 `POST /api/admin/reembolsos/{id}/reintento` simultáneos sobre el mismo reembolso `FALLIDO` × **5 rondas**.
- **Afirma por ronda:** la pasarela recibe 1 sola solicitud (`verify times(1)`); 1 respuesta 200 y 9 respuestas 400 con
  *en proceso* o *ya reembolsado* (al menos uno *en proceso*); 1 reembolso para ese pago y origen; `intentos` sube en 1;
  estado final `REEMBOLSADO`; la auditoría sube exactamente en 1.

| Corrida | Inicio | Resultado (literal, clase completa) | Tiempo del método |
|---|---|---|---|
| 1 | 22:04:03 | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 22.73 s` | 0.891 s |
| 2 | 22:04:50 | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 18.33 s` | 0.776 s |
| 3 | 22:05:30 | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 16.07 s` | 0.673 s |

Rechazos por ronda (*en proceso* / *ya reembolsado*, registrados por la prueba con `LOG.info`):

| Corrida | R1 | R2 | R3 | R4 | R5 |
|---|---|---|---|---|---|
| 1 | 5 / 4 | 5 / 4 | 9 / 0 | 9 / 0 | 9 / 0 |
| 2 | 9 / 0 | 9 / 0 | 9 / 0 | 9 / 0 | 9 / 0 |
| 3 | 9 / 0 | 9 / 0 | 9 / 0 | 9 / 0 | 9 / 0 |

**Resultado:** cumple. 15 de 15 rondas con una sola llamada a la pasarela y un solo reembolso.

---

## Límites ajustados

Ninguno. Con 20 hilos y el pool de Hikari por defecto (10 conexiones) no hubo timeouts (las solicitudes hacen cola y
terminan dentro de los 30 s de la clase). Ningún límite por IP aplica a estas rutas. No se cambió configuración de prueba.

## Defectos

Ninguno. No se registró `DEF-xx`. No hubo fallos intermitentes, así que no hicieron falta corridas extra.

## Verificación final

`./init.sh` completo (corrido por el reviewer tras las adendas), exit 0:

```
[OK]    Integración: todas las pruebas *IT (./mvnw verify)
          Tests run: 1279, Failures: 0, Errors: 0, Skipped: 0
[OK]    Todos los tests del backend pasan (./mvnw verify)
[OK]    Entorno listo. Puedes empezar a trabajar.
```

Nota del reviewer (no bloqueante): el regex de trazabilidad de `init.sh` no reconoce los `@DisplayName` con `(ACxxx)`
tras la lista de R; no afecta el resultado porque esos R ya están cubiertos por otras pruebas.

---

## Texto para la tabla 10.3.7 del informe

| AC | Atributo | Medida de la respuesta | Prueba que lo verifica | Resultado obtenido |
|---|---|---|---|---|
| AC001 | Confiabilidad – Consistencia | 1 reserva activa por bloque en 20 intentos concurrentes | `ReservaConcurrenciaIT`, HU-12 R19 (AC001): 20 usuarios a la vez con el mismo bloque, 5 rondas | Cumple: en cada ronda, un 201 y 19 rechazos (400, horario no disponible) y una sola fila activa; 3 corridas sin fallos (15 rondas, 300 intentos). |
| AC002 | Confiabilidad – Consistencia | 0 reservas activas en fechas bloqueadas en 20 intentos concurrentes | `ReservaConcurrenciaIT`, HU-12 R24 (AC002): 10 reservas y 10 bloqueos de la misma fecha a la vez, 5 rondas | Cumple: en ninguna ronda coexistieron un bloqueo y una reserva activa de la fecha, y hubo como máximo un bloqueo; 3 corridas sin fallos (15 rondas, 300 operaciones). |
| AC003 | Confiabilidad – Consistencia | Como máximo 1 pago y 1 confirmación por `id_transaccion_pasarela` tras 5 entregas repetidas | `PagoConcurrenciaIT`, HU-13 R43 (AC003): 5 notificaciones iguales a la vez, 5 rondas | Cumple: en cada ronda, 5 respuestas 200, 1 fila en `pago`, la reserva confirmada una sola vez y un correo de cada tipo; 3 corridas sin fallos (15 rondas, 75 notificaciones). |
| AC005 | Confiabilidad – Consistencia | Como máximo 1 reembolso por pago y origen después de 10 reintentos | `ReembolsoConcurrenciaIT`, HU-16 R22, R23 (AC005): 10 reintentos a la vez de un reembolso FALLIDO, 5 rondas | Cumple: en cada ronda, una sola llamada a la pasarela, un 200 y nueve 400 (en proceso o ya reembolsado) y un solo reembolso REEMBOLSADO; 3 corridas sin fallos (15 rondas, 150 reintentos). |

Las cuatro medidas se alcanzaron tal como las promete el escenario; no hace falta cambiar la redacción de la sección 6.3.
AC008 (rendimiento) queda fuera de esta sesión.
