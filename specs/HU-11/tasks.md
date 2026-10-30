# HU-11 — Tasks: Detalle del espacio

> Orden de ejecución. El implementer marca `[x]` al completar cada una. Firmas, flujos, mensajes y plan de
> pruebas están en `design.md`. Cada test lleva `@DisplayName("HU-11 R<n>: …")`. Antes de empezar, confirmar que
> la dueña aceptó D1–D8 (`requirements.md`).
> Reglas: sin migración, sin auditoría, sin cambios en `DisponibilidadService` ni en el código de HU-10 salvo el
> enlace de `BuscarEspacios.jsx` (T11). Las unitarias (472) e IT de HU-01…HU-10 no se modifican y deben seguir
> verdes en cada paso.

## Backend — `espacios`

- [x] T1 — `espacios/service/FichaPublicada` (N, record) y `CatalogoEspaciosService` (M: `publicado(Long)` y `esVisible(Long)` con javadoc), implementados en `CatalogoEspaciosServiceImpl` (M, design § 6.2: `findById`, `PUBLICADO`, `CuentaService.cuentaActiva`, `zonaNombre`, fotos por `findByEspacioIdOrderByOrdenAsc` → `EspacioService.urlDe`). Cubre: R31, R32, R33. Test: `CatalogoEspaciosServiceImplTest` (M: casos nuevos; los de HU-10 intactos).

## Backend — `comun`

- [x] T2 — `comun/seguridad/ConfiguracionSeguridad` (M): `permitAll` para `GET /api/espacios/*` y `GET /api/espacios/*/disponibilidad`, con comentario HU-11 DT-12. Cubre: R1, R8, R13. Test: en T6.

## Backend — `busqueda`

- [x] T3 — `busqueda/dto/FichaPublicaDTO` (N, con `de(FichaPublicada)`), `BloqueLibreDTO` (N) y `DisponibilidadPublicaDTO` (N). Cubre: R2, R7, R19. Test: `FichaServiceTest` y T6 (`containsOnlyKeys`).
- [x] T4 — `busqueda/service/FichaService` (N, design § 6.1): id como texto (`^[0-9]{1,19}$`, sin desbordar, > 0) → 404 sin consultar; `catalogo.publicado` / `esVisible` → 404; fecha estricta y `VentanaReserva` → `ReglaNegocioException` en `fecha` con las constantes de `BusquedaService`; `horasLibres` → `BloqueLibreDTO(h, h + 1)`. Cubre: R1, R9, R11, R13, R14, R18, R20, R21, R22, R23. Test: `FichaServiceTest` (N, Mockito, `Clock` fijo; `verifyNoInteractions` en R11 y R20).
- [x] T5 — `busqueda/controller/ReglasLimiteFicha` (N: constantes `RUTA_FICHA`, `RUTA_DISPONIBILIDAD` y dos `@Bean` con `LimiteCatalogo.regla("GET", …)`) y `busqueda/controller/FichaController` (N: `@PathVariable String id`, `@RequestParam(required = false) String fecha`). Cubre: R1, R13, R26, R27, R28, R30. Test: `ReglasLimiteFichaTest` (N, R30) y T6/T7.

## Backend — pruebas de integración

- [x] T6 — `T/busqueda/controller/FichaControllerIT` (N, Testcontainers, `RelojControlable` en `2026-10-28T15:00:00Z`, doble de `ReservasDelEspacio` como `@Bean`, IP `10.12.x.x` por test, arrendador con correo, teléfono, nombre, documento y razón social reconocibles): casos de design § 14. Cubre: R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14, R15, R16, R17, R18, R19, R20, R21, R22, R23, R24, R25.
- [x] T7 — `T/busqueda/controller/LimiteFichaIT` (N, `@SpringBootTest(properties = {"espacioya.limite.catalogo.maximo=3", "espacioya.limite.catalogo.ventana=PT1M"})`, `RelojControlable`, IP `10.13.x.x`): cupo agotado en la ficha y en la disponibilidad, cupo compartido con `GET /api/espacios`, 404 y 400 cuentan. Cubre: R26, R27, R28, R29.

## Frontend

- [x] T8 — Kit: `components/ui/Galeria.jsx` + `Galeria.module.css` (N, design § 12: props `fotos`, `alt`; principal con `Imagen`, miniaturas como `<button type="button" aria-pressed>` con `aria-label`, elegida con borde `--color-primario`; sin fotos → `Imagen` "Sin foto"; solo tokens). Cubre: pantalla Ficha del espacio, CA1 (fotos) y R6 visible.
- [x] T9 — `src/api/busqueda.js` (M: `obtenerEspacio(id)` y `consultarBloquesLibres(id, fecha)`, `autenticada: false`, id con `encodeURIComponent`) y `pages/busqueda/textosFicha.js` (N: política de cancelación con las tres reglas de RN-06, `SIN_BLOQUES`, `ENLACE_BUSCAR`, títulos y `CARGANDO`). Cubre: pantalla Ficha del espacio (CA1 política D5, CA2 mensaje sin bloques D1).
- [x] T10 — `pages/busqueda/FichaEspacio.jsx` + `FichaEspacio.module.css` (N, design § 12): carga ficha y bloques; fecha inicial de `?fecha=` o `ventanaBusqueda().hoy`; `Campo date` con `min`/`max`; `setSearchParams({ fecha }, { replace: true })` al cambiar y solo recarga bloques, descartando respuestas viejas; datos con `nombreTipo`, `zonaNombre`, dirección, capacidad, `formatoPrecio` en negrita, descripción, política; `SelectorBloques` sin `onCambiar`; `SIN_BLOQUES`; 404 con `Alerta` y `Link` "Buscar espacios"; 400 de fecha bajo el `Campo`; 429/red en `Alerta`. Sin botón primario ni datos del arrendador. Cubre: CA1, CA2, D1–D8 visibles.
- [x] T11 — `App.jsx` (M: ruta pública `/espacios/:id` sin `RutaProtegida`) y `pages/busqueda/BuscarEspacios.jsx` (M: `acciones` de cada `Tarjeta` con `Link` "Ver espacio" a `/espacios/${id}?fecha=${fecha}`; nada más cambia). Cubre: navegación a la pantalla de CA1–CA2 (HU-10 D12) y destino de HU-12 CA4.

## Docs y cierre

- [x] T12 — `docs/architecture.md` (`CatalogoEspaciosService`: `publicado` y `esVisible`; § Disponibilidad, HU-11 hecho: ruta pública con `horasLibres`, solo libres; § Rate limiting: rutas de la ficha registradas) y `docs/frontend.md` (fila `Galeria` en el kit y en la lista de carpetas; `SelectorBloques` en solo lectura en la ficha de HU-11 y contrato para HU-12 de design § 13). Cubre: R30, R31, R33 (documentación de DT-2, DT-11, DT-14).
- [x] T13 — Verificación: `./init.sh` en verde (unitarias, IT con Docker, `npm --prefix 3_frontend run lint` y `build`, revisión de colores y secretos) y `progress/impl_HU-11.md` con el mapa R1–R33 → test (`@DisplayName`). Cubre: R1–R33.
