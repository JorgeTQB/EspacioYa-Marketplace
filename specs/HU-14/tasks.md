# HU-14 — Tasks: Mis reservas

> Orden de ejecución. El implementer marca `[x]` al completar cada una. Firmas, flujos, mensajes y plan de pruebas
> están en `design.md`. Cada test lleva `@DisplayName("HU-14 R<n>: …")`. Antes de empezar, confirmar que la dueña
> (Adriana Callupe) aceptó las decisiones por defecto **D1–D12** (`requirements.md`). Reglas: HU-14 **no** usa código
> de HU-13 ni de `pagos` en producción (la IT de punta a punta de CA2 solo llama a la API de pagos, D9); **ningún test existente cambia ni se borra** (solo se agregan casos); **sin migración**
> (DT-7). Las unitarias e IT de las demás historias siguen verdes en cada paso.
>
> **Precondiciones** (spec revisado el 2026-10-31 tras integrar HU-13, `ec51197`):
> - La rama incluye HU-13 (`6afb5bc`); implementar sobre esa base (`ReservaRepository`, `CuentaService`, `App.jsx`,
>   `textosReservas.js` y `docs/architecture.md` ya traen cambios de HU-13).
> - El entorno local necesita `PAGOS_PASARELA=simulada` en `2_Backend/.env` para **arrancar la app** (sin ella el
>   backend no arranca). Las IT **no** la necesitan: usan `src/test/resources/config/application.properties`, que ya
>   fija `espacioya.pagos.pasarela=simulada`.

## Backend — dominio (★ Iterator)

- [x] T1 — `reservas/domain/GrupoReservas` (`desde(texto)`: solo `proximas` o `pasadas` exactos) y
  `reservas/domain/ReservaConHorario` (`inicio`, `fin` con `horaFin = 24` → día siguiente, `esProxima(ahora,
  ahoraLocal)` con `reserva.estadoEn(ahora)`). Cubre: R22, R23, R30. Test: `GrupoReservasTest` (`proximas`,
  `pasadas` válidos; `PROXIMAS`, ` proximas`, `todas`, `""`, `null` → vacío), `HistorialReservasTest` (T2).
- [x] T2 — `reservas/domain/HistorialReservas` (copia privada ordenada por `inicio` e id; `proximas`, `pasadas`,
  `recorrer`; sin getters de la lista) e `IteradorReservas` (de paquete; sentido y criterio; `NoSuchElementException`;
  `remove` sin implementar). Cubre: R22, R23, R24, R25. Test: `HistorialReservasTest` (casos de design § 13: límite de
  las 10:00, 23–24 a las 23:59, vigente, vencida en `expiraEn` exacto, `CONFIRMADA`, `EXPIRADA` y `CANCELADA` futuras;
  orden y empates por id en los dos sentidos; lista vacía; `next` agotado; `remove`; la lista de entrada modificada
  después no altera el recorrido).
- [x] T3 — `reservas/domain/Pagina` (`de(recorrido, numero, tamano)` con salto en `long` y `hayMas =
  recorrido.hasNext()`). Cubre: R26, R27, R32. Test: `PaginaTest` (0, 10 y 11 elementos; página 2 de 11; página
  2 147 483 647 vacía sin desbordar; el iterador no se consume más allá de un elemento tras la página).

## Backend — acceso a datos (★ DAO)

- [x] T4 — `ReservaRepository` (M: `findByUsuarioId`, `findByEspacioIdInAndEstado`), `ReservaBloqueRepository` (M:
  `horarios(reservaIds)` con `min`/`max` sin filtrar `activo`) y el record `HorarioReserva` (N). Cubre: R8, R33, R34,
  R35. Test: `ReservaDAOJpaIT` (T5).
- [x] T5 — `reservas/repository/ReservaDAO` (N, interfaz con javadoc del patrón) y `ReservaDAOJpa` (N, `@Repository`,
  design § 7). Cubre: R6, R8, R15, R16, R33, R34, R35. Test: `ReservaDAOJpaIT` (Testcontainers sin MockMvc, estilo
  `ReservasDelEspacioImplIT`: otro usuario y otros espacios nunca aparecen; solo `CONFIRMADA`; horario con filas
  `activo = false`; `horaFin = 24`), `ReservaDAOJpaTest` (Mockito: una llamada a cada repositorio; sin reservas no
  llama a `horarios`; colección de espacios vacía no consulta; reserva sin horario se omite).

## Backend — `espacios` y `cuentas` (contratos nuevos, design § 11)

- [x] T6 — `espacios/service/CatalogoEspaciosService` y `CatalogoEspaciosServiceImpl` (M: `nombresDelArrendador`, con
  `EspacioRepository.findByArrendadorIdOrderByCreadoEnDesc`; `null` → `Map.of()`). Cubre: R16, R17, R21, R37. Test:
  `CatalogoEspaciosServiceImplTest` (M, casos agregados: todos los estados; nada de otro arrendador; `null` vacío).
- [x] T7 — `cuentas/repository/NombreCuenta` (N), `UsuarioRepository` (M: `nombresDe(ids)`), `CuentaService` y
  `CuentaServiceImpl` (M: `nombres(usuarioIds)`, filtra el blanco en Java; colección vacía sin consultar; javadoc con
  RNF-SEG-16). Cubre: R18, R19, R36. Test: `CuentaServiceImplTest` (M, casos agregados: con nombre, nulo y en blanco;
  colección vacía sin repositorio).

## Backend — servicio, DTO y controladores

- [x] T8 — `reservas/service/ConversionReservas` (N, DT-11) y `ReservaServiceImpl` (M: `aDto` delega en
  `ConversionReservas` con `Instant.now(reloj)`; mismo resultado). Cubre: R11, R12. Test: `ReservaServiceImplTest`,
  `ReservaControllerIT` y `ReservaConcurrenciaIT` **sin cambios** y en verde.
- [x] T9 — `reservas/dto/PaginaReservasDTO` (N, genérico, `List.copyOf`) y `ReservaDeMiEspacioDTO` (N, 9 campos).
  Cubre: R12, R20, R26, R27. Test: claves exactas en `MisReservasControllerIT` (T11).
- [x] T10 — `reservas/service/ConsultaReservasService` (N: `misReservas` y `reservasDeMisEspacios`, design § 7;
  rol antes de validar; `ValidacionException` con todos los errores; un solo instante; nombres solo de la página;
  constantes de design § 5; `@Transactional(readOnly = true)`; sin auditoría). Cubre: R2–R6, R9–R11, R13–R21, R26–R32,
  R38. Test: `ConsultaReservasServiceTest` (Mockito, `Clock` fijo: 403 sin DAO ni catálogo para cada rol ajeno; grupo
  y página inválidos → `ValidacionException` con dos errores y sin DAO; arrendador sin espacios → DAO no invocado;
  `cuentas.nombres` solo con los ids de la página; fallback "Usuario de EspacioYa"; el `estado` del DTO usa el mismo
  instante que la clasificación).
- [x] T11 — `reservas/controller/ReservaController` (M: `GET` sin id, `@RequestParam(required = false)` para `grupo` y
  `pagina`) y `ReservasDeMisEspaciosController` (N, `/api/arrendadores/actual/reservas`). Cubre: R1–R5, R7, R13, R14,
  R28–R31. Test: `MisReservasControllerIT` (N; extiende `ReservasControllerSoporte`; IP `10.16.x.x`; semillas con
  `filaReserva` y `filaBloque` para pasadas, fechas distintas y estados raros; todos los casos de design § 13).
  Incluye el **caso de punta a punta de CA2** (D9; R4, R13, R15): `POST /api/reservas` → `POST /api/reservas/{id}/pago`
  → `POST /api/reservas/{id}/pago/simulacion` con `APROBADO` → aparece en
  `GET /api/arrendadores/actual/reservas?grupo=proximas` y como `CONFIRMADA` en `GET /api/reservas?grupo=proximas`.
  `MisReservasControllerIT` declara su propio `@AfterEach` que hace `DELETE FROM pago` (corre antes del `limpiar()` del
  soporte, que no se toca: `pago` no tiene cascada). Cubre en IT: R1–R21, R22–R32, R36, R38.

## Frontend

- [x] T12 — `src/api/reservas.js` (M: `listarMisReservas`, `listarReservasDeMisEspacios` con `URLSearchParams`) y
  `pages/reservas/textosReservas.js` (M: títulos, textos de lista vacía, "Ver más", "Ver espacio", "Ver reserva",
  "Reservado por"). Cubre: pantallas Mis reservas y Reservas de mis espacios (CA1, CA2).
- [x] T13 — `pages/reservas/SeccionReservas.jsx` (N, + `.module.css`: página 1 al montar con patrón `activo`; "Ver más"
  con `Boton secundario` y `cargando`; agrega sin repetir ids; lista vacía; `Alerta error`). Cubre: separación y páginas
  visibles (CA1, CA2).
- [x] T14 — `pages/reservas/MisReservas.jsx` (N, + `.module.css`) en `/mis-reservas`: `Tarjeta`, `EtiquetaEstado`,
  fecha, horario y monto; "Ver espacio"; "Ver reserva" → `/reservas/:id` en **todas** las reservas, en cualquier estado (D10: ahí están el botón
  "Pagar" y el aviso de pago no aplicado de HU-13; `ResumenReserva` no se modifica). Cubre: CA1 (espacio, fecha,
  horario, monto, estado; próximas y pasadas), D4, D6, D10.
- [x] T15 — `pages/reservas/ReservasDeMisEspacios.jsx` (N, + `.module.css`) en `/panel/reservas`: `Tarjeta` con
  "Reservado por" `usuarioNombre`, fecha, horario, monto y `EtiquetaEstado`; "Ver espacio" → `/panel/espacios/:id`.
  Cubre: CA2 (confirmadas, nombre del usuario y horario), D5.
- [x] T16 — `App.jsx` (M: las dos rutas en `RutaProtegida`, sin tocar `RutaProtegida`) y `pages/cuentas/Panel.jsx` (M:
  enlace "Mis reservas" para `USUARIO`, "Reservas de mis espacios" para `ARRENDADOR`). Cubre: navegación a CA1 y CA2
  (D7).

## Docs y cierre

- [x] T17 — Docs del mismo PR: `docs/architecture.md` (sección "Reservas": HU-14 con `ReservaDAO` como primer DAO
  explícito, `HistorialReservas` como Iterator, clasificación con `estadoEn` y un solo instante; párrafos de
  `CatalogoEspaciosService.nombresDelArrendador` y `CuentaService.nombres`), `docs/frontend.md` (rutas `/mis-reservas` y
  `/panel/reservas`; `Tarjeta` en "Mis reservas" y "Reservas de mis espacios"; "Ver más" por sección). Sin cambios en la
  tabla de puertos ni en `modelo-datos.md`. Cubre: DT-3, DT-4, DT-5, DT-9.
- [x] T18 — Avisos en `progress/HU-14.md`: a Sebastian Garate (§ 11.1, `nombresDelArrendador`), a Jorge Quispe (§ 11.2,
  `CuentaService.nombres`), a Eduardo Saldaña (§ 11.3: **contrato verificado, sin acción**) y a Kevin Chan (§ 11.4,
  `acciones` para "Cancelar"; `CANCELADA` en pasadas y fuera de la lista del arrendador). Anotar D12: la caja negra de
  CA2 se hace completa en UAT con la pasarela simulada (reservar → "Pagar" → "Aprobar pago simulado" → la reserva
  aparece en "Reservas de mis espacios"); no queda verificación pendiente de integración. Cubre: D9, D12, DT-5.
- [x] T19 — Verificación: `./init.sh` en verde (unitarias, IT con Docker, `npm run lint && npm run build`, revisión de
  colores y secretos) y `progress/impl_HU-14.md` con el mapa R → test (`@DisplayName`) y la confirmación de que ningún
  test existente cambió (solo casos agregados en `CuentaServiceImplTest` y `CatalogoEspaciosServiceImplTest`). Cubre:
  R1–R38.
