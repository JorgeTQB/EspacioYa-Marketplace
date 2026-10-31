# Exploración HU-14 contra el código real de HU-13

> Explorador de HU-14 ("Mis reservas", dueña Adriana Callupe), 2026-10-31. Solo lectura, sin cambiar de rama.
> Base: rama `feature/HU-14-mis-reservas` (HEAD = `1fbf140`) frente a `origin/develop` (`ec51197` HU-13 y merge
> `6afb5bc`). Todo se leyó con `git show origin/develop:<ruta>` y `git diff 1fbf140 origin/develop`. Las líneas
> `develop:L` se refieren a `origin/develop`; las de `specs/HU-14/` son de la copia local sin commitear.

**Pregunta central:** ¿el spec de HU-14 (`spec_ready`, sin aprobar) sigue siendo válido con el HU-13 integrado?
**Respuesta corta:** sí. HU-13 respetó todo el contrato de § 11.3. Hay que hacer **ajustes menores**: textos que dicen
que HU-13 "no existe", el rango de IP de las IT (`10.15.x.x` ya es de HU-13), y D9 y D12, que ahora pueden
endurecerse porque `CONFIRMADA` ya se alcanza por la aplicación con la pasarela simulada.

---

## 1. Archivos analizados

| Área | Archivos (en `origin/develop`) |
|---|---|
| Spec HU-14 (local) | `specs/HU-14/requirements.md` (D1–D12, R1–R38), `specs/HU-14/design.md` (DT-1…DT-13, § 9, § 11.3, § 12, § 13, § 14), `specs/HU-14/tasks.md`, `progress/HU-14.md` |
| `reservas` | `reservas/domain/Reserva.java`, `reservas/domain/EstadoReserva.java`, `reservas/repository/ReservaRepository.java`, `reservas/service/ReservasPorCobrarImpl.java`, `test/.../reservas/domain/ReservaTest.java`, `test/.../reservas/controller/ReservasControllerSoporte.java` |
| `pagos` | `pagos/service/WebhookPagoService.java`, `EvaluacionCobro.java`, `SimulacionPago.java`, `pagos/service/puertos/{ReservasPorCobrar,ReservaPorCobrar}.java`, `pagos/domain/EstadoPago.java`, `pagos/controller/{PagoController,PagoSimuladoController}.java`, `pagos/repository/PagoRepository.java`, `test/.../pagos/controller/PagosSoporte.java` |
| `cuentas`, `espacios` | `cuentas/service/{CuentaService,CuentaServiceImpl,NotificadorCorreo}.java`, `espacios/service/DisponibilidadService.java` |
| BD y config | `db/migration/V4_1__pago.sql`, `db/migration/V3_3__reservas.sql`, `main/resources/application.properties`, `test/resources/config/application.properties`, `2_Backend/.env.example` |
| Frontend | `3_frontend/src/App.jsx`, `pages/reservas/{ResumenReserva.jsx,textosReservas.js}`, `components/ui/EtiquetaEstado.jsx`, `api/pagos.js`, `pages/pagos/*` |
| Docs | `docs/architecture.md`, `docs/modelo-datos.md`, `docs/requerimientos.md`, `docs/frontend.md`, `specs/HU-13/{requirements,design,tasks}.md`, `progress/history.md`, `progress/explore_HU-13.md`, `feature_list.json` |

---

## 2. Hallazgos por pregunta

### P1. Contrato § 11.3 (H2, H5): `estado`, `expira_en`, transiciones y `estadoEn`

- **Sin estados nuevos de reserva.** `EstadoReserva` sigue `PENDIENTE_PAGO, CONFIRMADA, EXPIRADA, CANCELADA`
  (`reservas/domain/EstadoReserva.java:4`). `ck_reserva_estado` no cambió (`V3_3__reservas.sql:12`). La única migración
  nueva es `V4_1__pago.sql`, que crea `pago` y `checkout_pago` y **no toca `reserva`**.
- **`estadoEn`, `esVigente`, `estaVencida`, `segundosRestantes` y `expirar` sin cambios** (`Reserva.java:68–90`, el
  diff solo agrega líneas 91–98).
- **Transición nueva `Reserva.confirmar(ahora)`** (`Reserva.java:92–97`): lanza `IllegalStateException` si no es
  `esVigente(ahora)`, es decir, solo `PENDIENTE_PAGO` vigente → `CONFIRMADA`. No toca `expira_en` ni los bloques
  (siguen `activo = true`, javadoc en la línea 91). Probado en `ReservaTest` (`develop`, tests "HU-13 R31",
  "HU-13 R31, R34": confirma una vigente, no confirma una vencida, ni una `CONFIRMADA` o `EXPIRADA`).
- **Un único camino a `CONFIRMADA`:** `WebhookPagoService.registrar` → `reservas.confirmar(...)` solo si el pago es
  `PAGADO` (`WebhookPagoService.java:100–102`); `ReservasPorCobrarImpl.confirmar` usa `findParaActualizar` y audita
  `RESERVA_CONFIRMADA` (`ReservasPorCobrarImpl.java:78–85`).
- **Pagos tardíos o de otro monto no tocan la reserva:** `PAGADO_TARDIO` y `MONTO_DISTINTO` se guardan en `pago` y la
  reserva queda como estaba (`PENDIENTE_PAGO` → se lee `EXPIRADA`, o ya `EXPIRADA`). Ver `EstadoPago.java:3–6` y
  `docs/architecture.md` (develop) en "Estados" y en RN-08 de `docs/requerimientos.md`.
- **Concurrencia (H5):** HU-13 bloquea `espacio → reserva` (`ReservasPorCobrarImpl.bloquear`, líneas 60–75) y compite
  con la expiración. HU-14 solo lee en `readOnly`, así que no se ve afectada.
- **`CONFIRMADA` aunque el espacio ya no se vea** (HU-13 D6, `DisponibilidadService.bloquearEspacio`, javadoc
  añadido): confirma R17 de HU-14 (el arrendador ve `CONFIRMADA` de espacios `PAUSADO`, `BAJA`, `OCULTO`).

**Conclusión P1:** los 7 puntos de § 11.3 se cumplen; no hay que cambiar la clasificación (D2, DT-6) ni R10, R15, R22
y R23.

### P2. Cambios de HU-13 en `reservas` frente a lo que HU-14 crea o modifica

| Archivo | HU-13 (develop) | HU-14 (design § 4 / § 6) | ¿Choca? |
|---|---|---|---|
| `ReservaRepository` | Agrega `findEspacioIdById` (líneas 37–39). Ya tenía `findByIdAndUsuarioId`, `contarVigentes`, `findIdsVencidas`, `findParaActualizar`, `findVencidasQueRetienen` | Agrega `findByUsuarioId`, `findByEspacioIdInAndEstado` | No: nombres distintos. Solo conflicto textual si se implementa sobre la base vieja (ver P6) |
| `ReservaBloqueRepository` | Sin cambios (`ReservasPorCobrarImpl` usa `findByReservaIdOrderByHoraInicioAsc`, ya existía) | Agrega `horarios(reservaIds)` | No |
| `Reserva.java` | Agrega `confirmar` | No lo modifica | No |
| `ReservaServiceImpl`, `ReservaController`, `ReservaDTO` | **Sin cambios** (no aparecen en el diff) | DT-11 mueve `aDto` a `ConversionReservas`; `GET /api/reservas` sin id | No. `PagoController` mapea `/api/reservas/{id}/pago` (`PagoController.java:20`) y `PagoSimuladoController` `/api/reservas/{id}/pago/simulacion`: no colisionan con `GET /api/reservas` |
| `ReservasPorCobrarImpl` (N, `reservas/service`) | Implementa el puerto `pagos.service.puertos.ReservasPorCobrar` | HU-14 no la usa | No. **No hay nada que HU-14 deba reutilizar**: el puerto es por reserva y para cobrar, no para listar. `ReservaDAO` sigue siendo el primer DAO explícito |
| `ReservaDAO`, `ReservaDAOJpa`, `HorarioReserva` | No existen en develop (`git grep` sin coincidencias) | Nuevos | No |
| `CuentaService` | Agrega `correoDe(usuarioId)` al final de la interfaz (`CuentaService.java`, ~línea 117) y su `Impl`; `despuesDelCommit` pasa a `comun.transaccion.DespuesDelCommit` | Agrega `nombres(usuarioIds)` | No semántico. Conflicto textual si ambos agregan al final sobre bases distintas. `correoDe` **no** sirve a HU-14 (devuelve correo: prohibido por RNF-SEG-16) |
| `CatalogoEspaciosService` | Sin cambios | Agrega `nombresDelArrendador` | No |
| Dobles de interfaces | Nadie implementa `CuentaService` ni `CatalogoEspaciosService` fuera de su `Impl` (solo `@Mock` en tests) | DT-8 lo asume | Se mantiene |
| `NotificadorCorreo` | Gana `notificarPagoConfirmado` y `notificarReservaPagada`, sin `default` | `ReservasControllerSoporte` no tiene doble de `NotificadorCorreo` | No, mientras HU-14 no agregue un doble |

### P3. Pagos: tabla `pago`, estados y servicios; vigencia de D1–D12

- **`pago`** (`V4_1__pago.sql:2–18`): `estado` en `PAGADO, RECHAZADO, PAGADO_TARDIO, MONTO_DISTINTO, REEMBOLSADO,
  REEMBOLSO_FALLIDO`; `ultimos4`; FK a `reserva` **sin cascada**; `uq_pago_reserva_pagado` (un solo `PAGADO` por
  reserva). `checkout_pago` con cascada (líneas 21–28).
- **`PagoService` no existe.** HU-13 lo dejó para HU-15 (`docs/architecture.md` develop, "Pagos: qué deja HU-13", y
  `specs/HU-13/design.md` § 12). Las piezas son `CheckoutFacade`, `EvaluacionCobro`, `WebhookPagoService`,
  `SimulacionPago`. Nada de esto debe usarlo HU-14.
- **Estado del pago al usuario:** HU-13 lo expone con su propio `GET /api/reservas/{id}/pago` (`EstadoPagoDTO`),
  **sin ampliar `ReservaDTO`** (HU-13 DT-11: "HU-14 reutiliza ese DTO"). D11 y R12 siguen válidos tal cual.
- **¿Se llega a `CONFIRMADA` por la aplicación?** **Sí.** Con `espacioya.pagos.pasarela=simulada`:
  `POST /api/reservas` → `POST /api/reservas/{id}/pago` (crea `checkout_pago`) →
  `POST /api/reservas/{id}/pago/simulacion` con `{"resultado":"APROBADO"}` → `SimulacionPago.simular` →
  `WebhookPagoService.recibir` → `Reserva.confirmar` (`SimulacionPago.java`, método `simular`;
  `PagoSimuladoController.java:26–46`). Las IT ya corren con la simulada
  (`test/resources/config/application.properties:14–15`), y UAT también la usa (`.env.example`, `architecture.md`
  fila "Pagos"). En el navegador: `/reservas/:id` → "Pagar" → `/reservas/:id/pago-simulado` → "Aprobar pago simulado".
  **CA2 se puede probar de punta a punta** hoy, en IT y en UAT.
- **Caso nuevo que toca a "Mis reservas":** un `PAGADO_TARDIO` deja la reserva `EXPIRADA` (pasadas, sin enlace según
  D10/§ 12), y `ResumenReserva` muestra en `/reservas/:id` el aviso `PAGO_NO_APLICADO` ("Recibimos un pago que no
  pudimos aplicar…", `textosReservas.js` develop). Con D10 tal cual, el usuario no llega a ese aviso desde "Mis
  reservas". También, con `?pago=exito`, `ResumenReserva` informa la confirmación. Es una mejora opcional (ver tabla,
  D10).

### P4. Frontend

- **`App.jsx`** (develop): HU-13 agrega solo `/reservas/:id/pago-simulado` (líneas ~133–141), dentro de
  `RutaProtegida`. Rutas existentes de `/panel`: `/panel`, `/panel/identidad`, `/panel/espacios`,
  `/panel/espacios/nuevo`, `/panel/espacios/:id`, `/panel/espacios/:id/disponibilidad`. **No hay `/mis-reservas` ni
  `/panel/reservas`**: sin colisión de rutas (D7 válida).
- **`EtiquetaEstado.jsx` sin cambios**: los cuatro estados de reserva siguen (`PENDIENTE_PAGO`, `CONFIRMADA`,
  `EXPIRADA`, `CANCELADA`). HU-13 no agregó los estados de pago a propósito ("HU-28 agregará", `docs/frontend.md`
  develop). § 12 de HU-14 ("ningún estado nuevo") sigue válido.
- **`ResumenReserva.jsx`**: ahora lee `obtenerReserva` + `obtenerEstadoPago`, tiene el botón "Pagar" y el sondeo con
  `?pago=exito`. HU-14 no modifica este archivo. El enlace "Ver reserva" de HU-14 a `/reservas/:id` cae justo en el
  botón "Pagar": D10 queda **cumplida por HU-13**.
- **`textosReservas.js`**: HU-13 agregó 9 constantes (`BOTON_PAGAR` … `PAGO_NO_APLICADO`) tras `CARGANDO` (líneas
  ~11–20). HU-14 (T12) agrega títulos y textos de lista vacía en el mismo archivo: **conflicto textual probable** si se
  implementa sin traer develop. Sin colisión de nombres previsible (HU-14 usa títulos, "Ver más", "Ver espacio",
  "Ver reserva", "Reservado por").
- **`Panel.jsx`** sin cambios en HU-13: T16 sigue válida.

### P5. Docs

- **`docs/architecture.md`** (develop): reescribió la nota "Lo que necesita HU-13" como "HU-13, hecho" (≈ líneas
  265–271, dentro de "Reservas: qué deja HU-12 (HU-13, HU-14, HU-15)", línea 240) y agregó "Pagos: qué deja HU-13
  (HU-14, HU-15, HU-16, HU-19, HU-28)" (línea 273). Para HU-14 dice: `PagoService` no existe; contar solo `PAGADO`;
  filas `sim-` en UAT. Nada de esto obliga a HU-14 porque no lee `pago` (D11). La fila del patrón HU-14 (Iterator,
  DAO) no cambió. **T17 debe escribir sobre esta versión** de `architecture.md`, no sobre la de `1fbf140`.
- **`docs/modelo-datos.md`**: `reserva` y `reserva_bloque` sin cambios; agrega `pago` (con los nuevos estados) y
  `checkout_pago`. HU-14 sigue sin migración (DT-7, § 9).
- **`docs/requerimientos.md`**: RN-08 y RF-E-04 cambian (pago tardío, enlace 1 minuto antes). RN-03 y RF-D-05/06
  (los de HU-14) no cambian. La matriz sigue con HU-14 dependiendo de HU-12 y HU-13.
- **`docs/frontend.md`**: fila "Pago" de colores con `PAGADO_TARDIO` y `MONTO_DISTINTO` (no la usa HU-14); describe
  "Pagar" en `/reservas/:id` y la pantalla simulada.
- **`specs/HU-13/design.md`**: DT-11 (línea 24: "`GET` propio en `pagos`, sin ampliar `ReservaDTO`… HU-14 reutiliza
  ese DTO") y § 12 (línea 404: "HU-14: `CONFIRMADA` sale de `Reserva.confirmar` y `ReservaDTO` no cambió").
- **`progress/history.md`** (develop, línea 892): aviso "Adriana Callupe (HU-14): `CONFIRMADA` ya existe, sale de
  `Reserva.confirmar`, y `ReservaDTO` no cambió". Otros avisos que afectan a HU-14 en el mismo bloque: el backend no
  arranca sin `PAGOS_PASARELA` (en local, `simulada`); quien agregue un doble de `NotificadorCorreo` implementa los dos
  métodos nuevos; las IT que crean pagos los borran antes que la reserva (`pago` sin cascada).
- **`progress/explore_HU-13.md:399`**: lo que HU-13 asumía de HU-14 coincide con el spec (nada de datos de pago al
  arrendador, `estadoEn`, no `getEstado`).

### P6. Conflictos previsibles al traer `develop`

- **La rama está en `1fbf140`, que es el padre de develop:** traer develop es un **fast-forward**. No hay conflictos
  con lo commiteado.
- **`feature_list.json` modificado sin commitear** (HU-14 `pending` → `spec_ready`, línea ~298). Develop cambia el
  bloque de HU-13 (quita "Factory Method", `pending` → `done`, líneas ~267–275). Los cambios no se pisan, pero
  `git merge` se niega a tocar un archivo con cambios locales: hace falta commitear o guardar ese cambio antes.
  Después se aplica limpio.
- **Sin colisión con archivos sin seguimiento:** develop no trae `specs/HU-14/`, `progress/HU-14.md` ni
  `progress/explore_HU-14.md` (solo `docs/historias/HU-14.md`, que ya existe).
- **Si se implementa HU-14 antes de traer develop**, habría conflictos textuales en `ReservaRepository.java`,
  `CuentaService.java`, `CuentaServiceImpl.java`, `App.jsx`, `textosReservas.js` y `docs/architecture.md`. Ninguno es
  de fondo. **Recomendación: traer develop antes de pasar a `in_progress`.**
- **Entorno tras traer develop:** el backend no arranca sin `PAGOS_PASARELA` (`application.properties` develop:
  `espacioya.pagos.pasarela=${PAGOS_PASARELA:}`). En local hay que poner `PAGOS_PASARELA=simulada` en `.env` antes de
  correr la app. Las IT no lo necesitan (usan `test/resources/config/application.properties`).
- **IT:** `PagosSoporte.java:48,213–216` usa **`10.15.x.x`** ("HU-12 usa 10.14.x.x"), que es el rango que HU-14 planea
  para `MisReservasControllerIT` (design DT-8 línea 20, § 13 línea 542, tasks T11 línea 62). Son contextos Spring
  distintos y no compartirían cupos, pero rompe la convención de un rango por historia: **usar `10.16.x.x`** (sin uso
  en develop).
- **IT con pagos:** `ReservasControllerSoporte.limpiar()` (develop, líneas 117–126) hace `DELETE FROM reserva` **sin
  borrar antes `pago`**. Si HU-14 agrega un caso de punta a punta con pago aprobado, `MisReservasControllerIT` necesita
  su propio `@AfterEach` que borre `pago` (en JUnit 5, el `@AfterEach` de la subclase corre antes que el de la
  superclase), sin tocar el soporte de HU-12 (DT-8: ningún test existente cambia).

---

## 3. Supuesto del spec HU-14 → realidad en HU-13 → ¿cambiar? → cambio propuesto

| Supuesto del spec HU-14 | Realidad en HU-13 (develop) | ¿Cambiar el spec? | Cambio propuesto |
|---|---|---|---|
| **D1** nombre del usuario o "Usuario de EspacioYa"; nunca correo | HU-13 agregó `CuentaService.correoDe` (devuelve correo, para el webhook). No toca `usuario.nombre` | No | Ninguno. Opcional: en § 11.2 anotar que `correoDe` existe y **no** debe usarse para el arrendador (RNF-SEG-16) |
| **D2** próxima = `estadoEn` `PENDIENTE_PAGO` vigente o `CONFIRMADA` y fin futuro | Sin estados nuevos; `estadoEn` igual; `confirmar` solo desde vigente | No | Ninguno |
| **D3** orden y páginas de 10 | No afectado | No | Ninguno |
| **D4** todos los estados con `EtiquetaEstado`; pendientes enlazan a `/reservas/:id`; HU-14 no construye el pago | `EtiquetaEstado` sin cambios; el pago está en `/reservas/:id` | No | Cambiar "HU-14 no construye el pago" por "el pago está en `/reservas/:id` (HU-13)" (solo redacción) |
| **D5** el arrendador nunca ve correo, teléfono ni pago | HU-13 no amplía DTO de reservas; los correos al arrendador van por `NotificadorCorreo` | No | Ninguno |
| **D6** solo nombre del espacio + "Ver espacio" | No afectado | No | Ninguno |
| **D7** `/mis-reservas` y `/panel/reservas` | Rutas libres en `App.jsx` develop; HU-13 solo agregó `/reservas/:id/pago-simulado` | No | Ninguno (implementar sobre el `App.jsx` de develop) |
| **D8** 403 por rol | `EvaluacionCobro` usa el mismo criterio (`ROL_QUE_PAGA = "USUARIO"`) | No | Ninguno |
| **D9** `CONFIRMADA` solo sembrada con `JdbcTemplate` ("hoy ninguna reserva llega a `CONFIRMADA` por la aplicación") | **Falso ahora:** con `pasarela=simulada` (activa en IT y en UAT) se confirma por la API: `POST …/pago` + `POST …/pago/simulacion` `APROBADO` | **Sí (menor)** | Reescribir D9: mantener la siembra para pasadas, fechas distintas y estados raros, y **agregar un caso de punta a punta** en `MisReservasControllerIT` (reservar → pagar → aprobar simulado → aparece en `GET /api/arrendadores/actual/reservas?grupo=proximas` y como `CONFIRMADA` en la del usuario), con un `@AfterEach` propio que borre `pago` antes de la limpieza del soporte. Anotarlo en § 13 (R13, R15) y en T11 |
| **D10** solo enlace "Ver reserva" a `/reservas/:id` para `PENDIENTE_PAGO`; "Pagar" lo pondrá HU-13 | "Pagar" ya existe en `ResumenReserva`. Esa pantalla también muestra "pago no aplicado" (`PAGADO_TARDIO`/`MONTO_DISTINTO`) y la confirmación | **Sí (redacción); opcional en alcance** | Redacción: "el botón 'Pagar' está en esa pantalla (HU-13)" (requirements L27, design § 12 L522, § 11.3.7). **Decisión para la dueña:** mostrar "Ver reserva" **en todas** las reservas del usuario (no solo pendientes), para que llegue al aviso de pago no aplicado de una `EXPIRADA`. Si lo acepta, cambia § 12 y T14; si no, D10 queda igual |
| **D11** fuera el estado del pago, comprobante y `ultimos4`; HU-14 no lee `pago` | HU-13 los expone por su `GET /api/reservas/{id}/pago`, sin tocar `ReservaDTO` (HU-13 DT-11). `pago` ya existe. Filas `sim-` en UAT | No (solo texto) | Corregir "No lee `pago`, que aún no existe" (design § 9 L439) y la alternativa 6 (L577: "No existe (HU-13 `pending`)") por "existe desde HU-13 (`V4_1`) y no se lee: es de HU-28" |
| **D12** HU-14 puede cerrarse con HU-13 `pending`; CA2 real "pendiente de integración" | HU-13 está `done` en develop, y CA2 se puede verificar en UAT con la pasarela simulada | **Sí** | Quitar la "verificación pendiente de integración". La caja negra de CA2 se hace completa en UAT (pago simulado aprobado → aparece al arrendador). Actualizar T18 (L92–94). Si se quiere, anotar que con Mercado Pago real sigue pendiente lo de HU-13 (T23), que no es de HU-14 |
| **§ 11.3.1 (H2)** sin estado intermedio; `CONFIRMADA` existente | Cumplido: `EstadoReserva` y `ck_reserva_estado` sin cambios; estados nuevos solo en `pago` | No | Pasar § 11.3 de "contrato que debe respetar" a "**verificado** contra HU-13 (`ec51197`)" |
| **§ 11.3.2 (H2)** solo vigente → `CONFIRMADA`; no reabre `EXPIRADA`; no cambia `expira_en` | Cumplido: `Reserva.confirmar` (`Reserva.java:92–97`) + `ReservaTest`. Pago tardío → `PAGADO_TARDIO` sin tocar la reserva | No | Igual que arriba |
| **§ 11.3.3** no borra `reserva_bloque` | Cumplido: los bloques siguen `activo = true` al confirmar | No | — |
| **§ 11.3.4** `monto_total` = monto cobrado | Cumplido: `PAGADO` solo si el monto coincide; si no, `MONTO_DISTINTO` y la reserva no se confirma (`ReglasPago.estadoDeAprobado`; `WebhookPagoService.java:113–124`) | No | — |
| **§ 11.3.5 (H5)** HU-14 solo lee; HU-13 no cambia la semántica | Cumplido: los bloqueos de HU-13 son `espacio → reserva` en escrituras; HU-14 es `readOnly` | No | — |
| **§ 11.3.6** HU-13 no expone datos de pago por los DTO de HU-14 | Cumplido (DTO propio `EstadoPagoDTO` en `pagos`) | No | — |
| **§ 11.3.7** "Pagar" en `/reservas/:id` | Cumplido (`ResumenReserva.jsx`, botón `BOTON_PAGAR`) | No (redacción) | "va" → "está"; ver D10 |
| **DT-1** `GET /api/reservas` libre | Sigue libre: `ReservaController` sin cambios; pagos usa `/api/reservas/{id}/pago…` | No | — |
| **DT-2** `ReservaDTO` sin cambios | HU-13 no lo cambió | No | — |
| **DT-3** `ReservaDAO` primer DAO explícito | No existe otro DAO; `ReservasPorCobrarImpl` es un adaptador de puerto, no un DAO | No | — |
| **DT-5** contratos nuevos sin puertos | Tabla de puertos: solo cambió la fila de `pagos.ReservasPorCobrar` | No | — |
| **DT-7** sin migración; "evita chocar con la `V4_1` que probablemente tome HU-13" | `V4_1__pago.sql` ya existe | No (texto) | "…`V4_1` ya es de HU-13; un índice futuro iría en `V4_n` libre" |
| **DT-8** IT con IP `10.15.x.x`; ningún test existente cambia | `10.15.x.x` es de HU-13 (`PagosSoporte.java:213–216`). `ReservasControllerSoporte.limpiar` no borra `pago` | **Sí** | Cambiar a **`10.16.x.x`** en DT-8 (L20), § 13 (L542) y T11 (L62). Si se adopta el caso de punta a punta (D9), un `@AfterEach` propio en `MisReservasControllerIT` que borre `pago` |
| **DT-11** mover `aDto` a `ConversionReservas` | `ReservaServiceImpl` sin cambios en HU-13 | No | — |
| **Encabezado de design** "HU-13 (`pending`, **sin código**)" (L3) | HU-13 `done` | Sí (texto) | "HU-13 (`done`, `ec51197`); HU-14 no usa su código" |
| **tasks.md** "HU-14 no usa código de HU-13 ni de `pagos`" (L5–6) | Sigue siendo correcto para el código de producción | No (matiz) | Precisar: "ni en producción; la IT de punta a punta solo llama a la API de pagos" (si se adopta D9) |
| **Compatibilidad** (requirements L238) "HU-14 no depende de su código" | Correcto | No | Añadir "verificado contra develop" |
| **T17** docs sobre `architecture.md` | Develop reescribió la sección de Reservas y agregó "Pagos: qué deja HU-13" | No | Precondición: traer develop antes de implementar |
| **T18** aviso a Eduardo Saldaña (contrato H2/H5) y D12 pendiente | Contrato ya cumplido; Eduardo ya avisó a Adriana (`history.md:892`) | Sí (menor) | Sustituir el aviso por "contrato verificado, sin acción"; quitar lo de D12 pendiente |

---

## 4. Ajustes propuestos al spec (resumen para el `spec_author`)

1. **IP de las IT:** `10.15.x.x` → `10.16.x.x` (design DT-8 L20, § 13 L542; tasks T11 L62). **Necesario.**
2. **D9 y D12** (requirements L26 y L29; design § 13; tasks T11 y T18): `CONFIRMADA` ya se alcanza por la API con la
   pasarela simulada. Agregar un caso de punta a punta de CA2 en `MisReservasControllerIT`, con limpieza de `pago` en
   su propio `@AfterEach`, y quitar la "verificación pendiente de integración".
3. **Textos desactualizados:** design L3 (encabezado), § 9 L438–439, § 11.3 (pasar a "verificado"), § 12 L522, § 14
   alt. 6 L577, DT-7 L19; requirements D4, D10 (L27) y L238; tasks L5–6 y T18 (L90–94); `progress/HU-14.md` L24–25.
4. **Decisión opcional para la dueña (D10):** "Ver reserva" en todas las reservas del usuario, no solo en las
   pendientes, para que un pago no aplicado (`PAGADO_TARDIO`) sea visible desde "Mis reservas". No cambia la API.
5. **Proceso, no spec:** traer `origin/develop` (fast-forward) **antes** de pasar a `in_progress`, guardando primero el
   cambio local de `feature_list.json`. Después, en `.env` local, `PAGOS_PASARELA=simulada` para que arranque el backend.

No hace falta tocar ningún requirement R1–R38, el diseño de Iterator y DAO, los endpoints, los DTO, los mensajes ni la
pantalla (salvo la opción 4).

---

## 5. Veredicto

**Spec válido con ajustes menores.**
