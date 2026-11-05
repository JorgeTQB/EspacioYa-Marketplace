# HU-21 — Tasks: Pausar o dar de baja una publicación

> Orden pensado para que cada paso compile y sus pruebas pasen antes del siguiente. Diseño: `design.md`.
> IP de las IT: `10.21.x.x`. Cada test lleva `@DisplayName("HU-21 R<n>: …")`.

## Backend — dominio

- [x] T1 — `Espacio`: agregar `esPausable`, `esReanudable`, `admiteBaja`, `pausar(Instant)`, `reanudar(Instant)` y
  `darDeBaja(Instant)` (DT-3, `IllegalStateException` si el predicado falla; fijan `actualizadoEn`). Casos nuevos en
  `EspacioTest` (cada transición válida e inválida desde los cinco estados). Cubre: R6, R8, R16, R39.
- [x] T2 — `BloqueReservado.terminaDespuesDe(LocalDateTime)` (DT-5) y `BloqueReservadoTest` (bloque que termina justo
  ahora → `false`; en curso → `true`; futuro → `true`; 23:00 de hoy). Cubre: R13, R14, R15.

## Backend — servicio y controladores de `espacios`

- [x] T3 — `EspacioService`: `habilitacion` y `aDto` a visibilidad de paquete, sin cambiar comportamiento (los tests
  existentes siguen verdes). Cubre: R26 (reutilización).
- [x] T4 — Crear `EstadoEspacioDTO` y `EstadoEspacioService` con `pausar`, `reanudar`, `darDeBaja`, `pausarComoAdmin`,
  `darDeBajaComoAdmin`, mensajes y acciones de § 9, orden de DT-4, reservas por terminar con
  `ObjectProvider<ReservasDelEspacio>` + `VentanaReserva` + `terminaDespuesDe` leídas tras el bloqueo (DT-5, DT-6),
  auditoría de DT-10. Cubre: R1, R6–R16, R25–R32, R34–R39.
- [x] T5 — `EstadoEspacioServiceTest` (Mockito): un test por R1, R6, R7, R8, R9, R10, R11 (orden), R12, R13, R14, R15,
  R16 (sin consultar el puerto), R25, R26, R27, R28, R29, R30, R31, R32 (sin leer el repositorio), R34, R35, R36, R37,
  R38, R39, y "sin bean del puerto la baja se completa". Cubre: R1, R6–R16, R25–R32, R34–R39.
- [x] T6 — `EstadoEspacioController` (`POST …/{id}/pausa`, `/reanudacion`, `/baja`, IP con `IpCliente.de`) y
  `EspacioAdminController` (`POST /api/admin/espacios/{id}/pausa`, `/baja`). Cubre: R1, R7, R12, R28, R29.
- [x] T7 — `EstadoEspacioControllerIT` (extiende `EspaciosControllerSoporte`; bloques cargados en
  `ReservasDelEspacioFalso`): pausa OK y su fila de auditoría; tras pausar y tras dar de baja, `GET /api/espacios` no lo
  lista y `GET /api/espacios/{id}` da 404; `POST /api/reservas` da 404; reanudación (OK, estado, identidad, fotos);
  baja OK desde `PUBLICADO` y `PAUSADO`; baja con bloque futuro → 400 campo `reservas`; baja de `BORRADOR`/`BAJA` → 400;
  `PUT` de un `BAJA` → 400; `USUARIO` → 403; ajeno → 404; rechazos sin auditoría ni cambio de `actualizado_en`;
  `GET …/espacios` lista `PAUSADO` y `BAJA`. Cubre: R1, R2, R3, R4, R6–R13, R16, R18, R25–R27, R34–R36, R38–R40.
- [x] T8 — `EspacioAdminControllerIT`: admin con MFA e IP permitida pausa y da de baja un espacio de otro arrendador
  (200 `EstadoEspacioDTO`, auditoría con `;actor=ADMIN;arrendadorId=`); baja con bloque futuro → 400 campo `reservas`;
  estado inválido → 400; inexistente → 404; `ARRENDADOR` y `USUARIO` → 403; IP no permitida → 403 sin cambio.
  Cubre: R28–R33, R37.

## Backend — `reservas` (reservas afectadas)

- [x] T9 — `ConsultaReservasService`: sobrecarga con `espacioId` (DT-7; la de 3 parámetros delega con `null`), validación
  conjunta con `CAMPO_ESPACIO`/`MENSAJE_ESPACIO`, 404 si no es propio, `dao.confirmadasDeEspacios(Set.of(espacioId))`.
  `ReservasDeMisEspaciosController.listar` recibe `@RequestParam(required = false) String espacioId`. Cubre: R19–R22.
- [x] T10 — Casos nuevos en `ConsultaReservasServiceTest`: filtra por espacio; `espacioId` `abc`, `0`, `-1` y mayor que
  `Long.MAX_VALUE` → 400 en `espacioId` junto a `grupo`; ajeno o inexistente → 404; sin `espacioId` igual que HU-14.
  Cubre: R19, R20, R21, R22.
- [x] T11 — `BajaEspacioConReservasIT` (extiende `ReservasControllerSoporte`, puerto real, reloj controlable): espacio
  con reserva `CONFIRMADA` futura → pausa conserva reserva y bloques; baja → 400 `reservas`; `GET …/reservas?espacioId=`
  la lista; la cancelación HU-15 sobre el espacio pausado da política `ARRENDADOR` y 100 %; la baja siguiente → 200
  `BAJA`. Casos aparte: `CONFIRMADA` en curso y `PENDIENTE_PAGO` vigente bloquean; `PENDIENTE_PAGO` vencida, `EXPIRADA`,
  `CANCELADA` y `CONFIRMADA` terminada no bloquean; `espacioId` ajeno → 404. Borra `reembolso` y `pago` antes del
  `limpiar()` del soporte. Cubre: R5, R13, R14, R15, R19, R21, R23, R24.
- [x] T12 — `BajaEspacioConcurrenciaIT`: baja y `POST /api/reservas` del mismo espacio en dos hilos con un
  `CountDownLatch`, repetido; en cada vuelta, o reserva 201 y baja 400, o baja 200 y reserva 404; nunca `BAJA` con una
  reserva activa. Cubre: R17.

## Frontend

- [x] T13 — `api/espacios.js`: `pausarEspacio`, `reanudarEspacio`, `darDeBajaEspacio`. `api/reservas.js`:
  `listarReservasDeMisEspacios({ grupo, pagina, espacioId })` agrega `espacioId` solo si viene. Cubre: pantalla
  `/panel/espacios/:id` (CA1, CA2).
- [x] T14 — Extraer `pages/reservas/TarjetaReservaDeMiEspacio.jsx` desde `ReservasDeMisEspacios.jsx` sin cambio visible.
  Cubre: pantalla "Reservas afectadas" (CA2).
- [x] T15 — Textos de `design.md` § 13 en `pages/espacios/textosEspacios.js`; crear `EstadoPublicacion.jsx` (+ CSS
  Module) con Pausar (`Modal primario`), Reanudar y Dar de baja (`Boton peligro` + `Modal peligro-confirmar`) y el manejo
  del 400 en el campo `reservas`. Cubre: pantalla `/panel/espacios/:id` (CA1, CA2).
- [x] T16 — Crear `ReservasAfectadas.jsx` (+ CSS Module) con `SeccionReservas` filtrada por `espacioId` y
  `CancelarReserva`. Cubre: CA2 "veo las reservas afectadas y debo cancelarlas (100 %)".
- [x] T17 — `EditarEspacio.jsx`: avisos de `PAUSADO` y `BAJA`, botones `deshabilitado` en `BAJA`/`OCULTO` y bloque
  `EstadoPublicacion` en `PUBLICADO`/`PAUSADO`. Cubre: pantalla `/panel/espacios/:id` (CA1, CA2).

## Documentación

- [x] T18 — `docs/architecture.md`: (a) en "Estados del sistema", la línea del espacio con `PAUSADO → BAJA`, reanudar con
  identidad verificada y foto, `BAJA` final, y pausa/baja del admin; (b) subsección "Estado del espacio: qué deja HU-21"
  (rutas del arrendador y del admin, `EstadoEspacioService`, regla de "reserva por terminar" con el puerto y
  `BloqueReservado.terminaDespuesDe`, bloqueo del espacio, auditoría, 400 en `reservas`); (c) en "Disponibilidad", marcar
  "HU-21 (baja con reservas)" como hecho; (d) en "Mis reservas", el parámetro opcional `espacioId` de
  `GET /api/arrendadores/actual/reservas` (404 si no es propio). La tabla de puertos no cambia. Cubre: R13, R19, R28.
- [x] T19 — `docs/frontend.md`: entrada en "Rutas y llamadas a la API" para `/panel/espacios/:id` con
  `EstadoPublicacion` y `ReservasAfectadas` (sin componentes nuevos del kit, estados ni colores). Cubre: pantallas CA1,
  CA2.

## Verificación

- [x] T20 — `npm --prefix 3_frontend run lint` y `npm --prefix 3_frontend run build` sin advertencias;
  `./init.sh` en verde (unitarias, IT y build); mapa `R<n>` → test en `progress/impl_HU-21.md` con R1–R40 cubiertos.
  Antes, ajustar `MisReservasControllerIT.deSusEspaciosEnCualquierEstado` (HU-14): quitar `&espacioId=` de la consulta
  del arrendador y mantener la verificación de que se ignora `arrendadorId`; la de `GET /api/reservas` no cambia
  (`design.md` DT-7 y DT-12: R21 prevalece sobre HU-14 R7 solo para `espacioId` en la ruta del arrendador).
