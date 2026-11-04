# HU-10 — Tasks: Búsqueda por tipo, zona y fecha

> Orden de ejecución. El implementer marca `[x]` al completar cada una. Firmas, consultas, mensajes y plan
> de pruebas están en `design.md`. Cada test lleva `@DisplayName("HU-10 R<n>: …")`. Antes de empezar,
> confirmar que el dueño aceptó P1–P9 (`requirements.md`) y D1–D12 (`design.md` § 15).
> Regla: las unitarias e IT de HU-01…HU-09 no se modifican (salvo `ZonaControllerIT.sinSesion`, T13) y
> deben seguir verdes en cada paso. Antes de crear la migración, actualizar `develop` y confirmar que
> `V3_2` sigue libre.

## Backend — `comun` (rate limiting con patrones)

- [x] T1 — `comun/limite/ReglaLimite` (M): `record` → `final class` con los mismos constructores (4 y 5 argumentos) y accesores, `PathPattern` precompilado con `PathPatternParser.defaultInstance`, `aplicaA` con `PathContainer.parsePath` (escape mal formado → `false`) y `toString` (design § 5.1, DT-11). Cubre: R42, R43, R44, R45. Test: `ReglaLimiteTest` (M: los 3 tests de HU-02 intactos; nuevos para R42–R45).
- [x] T2 — `comun/limite/FiltroLimiteIntentos` (M): un intento por grupo por solicitud (`Set<String>` de grupos contados, DT-12). Cubre: R33, R36, R46. Test: `FiltroLimiteIntentosTest` (N: `MockHttpServletRequest`/`Response`, `LimitadorIntentos` real con `Clock` fijo, `FilterChain` simulado).
- [x] T3 — `comun/seguridad/ConfiguracionSeguridad` (M): `permitAll` para `GET /api/espacios` y `GET /api/zonas`. Cubre: R1, R12, R13. Test: en T12 y T13.

## Backend — `espacios`

- [x] T4 — Migración `R/db/migration/V3_2__indices_busqueda.sql` con `ix_espacio_busqueda ON espacio (estado, tipo, zona, precio_hora, id)` (design § 7). Cubre: R10 (orden servido por índice; rendimiento de R1). Test: `EspacioYaApplicationIT` (Flyway en base vacía) y T12.
- [x] T5 — Repositorios (M): `EspacioRepository.buscarPublicados` y `buscarPublicadosExcluyendo`, `HorarioSemanalRepository.findByEspacioIdInAndDiaSemana`, `BloqueoFechaRepository.espaciosBloqueados`, `FotoEspacioRepository.findByEspacioIdInAndOrden` (design § 5.3). Cubre: R2, R3, R4, R5, R6, R10, R15, R31. Test: en T8 y T12.
- [x] T6 — Puerto `espacios/service/puertos/ReservasDelEspacio` (M): método `default bloquesReservadosDelDia(Collection<Long>, LocalDate)` con javadoc (DT-6). `ReservasDelEspacioFalso` de `EspaciosControllerSoporte` no cambia. Cubre: R7, R31. Test: en T7.
- [x] T7 — `DisponibilidadService.conBloquesLibres` (M, interfaz) y su implementación en `DisponibilidadServiceImpl` (design § 6.2); `horasLibres` sin cambios. Cubre: R5, R6, R7, R8, R30, R31, R32. Test: `DisponibilidadServiceImplTest` (M: R7, R8, R31 con `times(1)`, R32 con `verifyNoInteractions`) y `DisponibilidadServiceImplIT` (M: R30, equivalencia con `horasLibres` sobre espacios con y sin horario, bloqueados, `BORRADOR`, de arrendador `SUSPENDIDA` y con todas las horas de hoy pasadas).
- [x] T8 — `espacios/service/EspacioPublicado` (N, record), `CatalogoEspaciosService` (N, interfaz) y `CatalogoEspaciosServiceImpl` (N; `esTipoValido`, `existeZona`, `publicados` con la regla HU-09 D1 de conjunto vacío, `fotosPrincipales` con `EspacioService.urlDe`; design § 6.3). Cubre: R2, R3, R4, R10, R15, R16, R23, R24. Test: `CatalogoEspaciosServiceImplTest` (N, Mockito: rama con y sin excluidos de R4; `esTipoValido`/`existeZona` con `null`, blank, minúsculas) y T12.

## Backend — `busqueda`

- [x] T9 — `busqueda/controller/LimiteCatalogo` (N, `@Component`, `GRUPO = "catalogo"`, validación de `maximo`/`ventana`, `regla(metodo, patron)`), `busqueda/controller/ReglasLimiteBusqueda` (N, `@Bean` de `GET /api/espacios`), propiedades en `R/application.properties` (`espacioya.limite.catalogo.maximo=${LIMITE_CATALOGO_MAXIMO:120}`, `espacioya.limite.catalogo.ventana=${LIMITE_CATALOGO_VENTANA:PT1M}`, con comentario HU-10 §9.9) y `2_Backend/.env.example` (M: las dos variables, vacías, con comentario). Cubre: R33, R39, R40, R41. Test: `LimiteCatalogoTest` (N: R40, R41) y R39 en T12 (contexto sin propiedades: `maximo() == 120`, `ventana() == PT1M`).
- [x] T10 — `busqueda/dto/ResultadoBusquedaDTO` y `ResultadoEspacioDTO` (N); `busqueda/service/CriteriosBusqueda` (N), `BusquedaEspacios` (N, interfaz envolvible para HU-23), `BusquedaBase` (N, design § 6.4). Cubre: R2, R9, R10, R14. Test: `BusquedaBaseTest` (N: filtra por `conBloquesLibres` conservando el orden; sin candidatos no llama a disponibilidad).
- [x] T11 — `busqueda/service/BusquedaService` (N: constantes de design § 4, `TAMANO_PAGINA = 20`, validación acumulada en orden `tipo`, `zona`, `fecha`, `pagina` → `ValidacionException`, paginación en `long`, fotos solo de la página; design § 6.1) y `busqueda/controller/BusquedaController` (N: `GET /api/espacios`, cuatro `@RequestParam(required = false) String`). Cubre: R1, R11, R16, R17, R18, R19, R20, R21, R22, R23, R24, R25, R26, R27, R28, R29. Test: `BusquedaServiceTest` (N, Mockito, `Clock` fijo: 45 disponibles en páginas 1–4; cada valor inválido de R27; combinación de R28 con el orden de errores; R29 con `verifyNoInteractions(busqueda)`; textos de fecha iguales a los de HU-08; `fotosPrincipales` llamado solo con los ids de la página y no llamado si la página está vacía).

## Backend — pruebas de integración

- [x] T12 — `T/busqueda/controller/BusquedaControllerIT` (N, Testcontainers, `RelojControlable` en `2026-10-28T15:00:00Z`, doble de `ReservasDelEspacio` como `@Bean`, IP distinta por test `10.10.x.x`; datos con repositorios y `JdbcTemplate`): casos de design § 13. Cubre: R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R14, R15, R16, R17, R19, R21, R22, R23, R24, R25, R26, R27, R28, R39.
- [x] T13 — `T/espacios/controller/ZonaControllerIT` (M): `sinSesion` espera 200 con las 50 zonas (`codigo`, `nombre`, orden de HU-07 R8), `@DisplayName("HU-10 R13: …")`; anotar en `progress/impl_HU-10.md` que reemplaza HU-07 R43 para `/api/zonas`. Cubre: R13.
- [x] T14 — `T/busqueda/controller/LimiteCatalogoIT` (N, `@SpringBootTest(properties = {"espacioya.limite.catalogo.maximo=3", "espacioya.limite.catalogo.ventana=PT1M"})`, `RelojControlable` propio `@Primary`): casos de design § 13. Cubre: R33, R34, R35, R36, R37, R38, R44, R47.

## Frontend

- [x] T15 — Kit: `components/ui/Imagen.jsx` + `Imagen.module.css` (N: props `src`, `alt`; 4:3, `object-fit: cover`, `--radio`, `loading="lazy"`; sin `src` → caja `--color-gris-suave` con "Sin foto"; solo tokens). Cubre: pantalla Buscar espacios, CA2 (foto principal) y R16 visible.
- [x] T16 — `src/api/busqueda.js` (N: `buscarEspacios({ tipo, zona, fecha, pagina })` → `GET /api/espacios` con `URLSearchParams` y `autenticada: false`) y `pages/busqueda/textosBusqueda.js` (N: textos de CA3, "No hay más resultados.", `ventanaBusqueda()` en `America/Lima`). Cubre: pantalla Buscar espacios (CA1, CA2, CA3).
- [x] T17 — `pages/busqueda/BuscarEspacios.jsx` + `BuscarEspacios.module.css` (N, design § 12): formulario con `Campo` select tipo (`TIPOS_ESPACIO`), select zona (`listarZonas`), date con `min`/`max`; `Boton` primario "Buscar" con `cargando`; filtros en la URL con `useSearchParams`; `Tarjeta` por resultado con `Imagen`, zona y precio en negrita (`formatoPrecio`); "Página n" con "Anterior"/"Siguiente"; errores por campo y `Alerta error` (400 sin campo, 429, red, zonas). Cubre: CA1 (lista del servidor), CA2 (nombre, foto, precio, zona, páginas de 20), CA3 (mensaje y sugerencia de cambiar fecha o zona).
- [x] T18 — `App.jsx` (M): ruta pública `/buscar` (sin `RutaProtegida`), enlace "Buscar espacios" en `/` y en el encabezado (con y sin sesión). Cubre: navegación a la pantalla de CA1–CA3.

## Docs y cierre

- [x] T19 — `docs/architecture.md` (tabla de puertos: `bloquesReservadosDelDia` default en `espacios.ReservasDelEspacio`; `CatalogoEspaciosService` como interfaz pública de `espacios` para `busqueda`; `conBloquesLibres` hecho en "Disponibilidad: qué deja HU-08 y qué falta"; `ReglaLimite` con patrones `PathPattern`, grupo `catalogo` y `LimiteCatalogo.regla` para HU-11/HU-23), `docs/frontend.md` (fila `Imagen` en el kit; `Tarjeta` usada en resultados de búsqueda) y `docs/modelo-datos.md` (índice `ix_espacio_busqueda` en la fila de `espacio`). Cubre: R31, R41, R42 (documentación de DT-6, DT-11, DT-13, DT-15).
- [x] T20 — Verificación: `./init.sh` en verde (unitarias, IT con Docker, `npm --prefix 3_frontend run lint` y `build`, revisión de colores y secretos) y `progress/impl_HU-10.md` con el mapa R1–R47 → test (`@DisplayName`). Cubre: R1–R47.
