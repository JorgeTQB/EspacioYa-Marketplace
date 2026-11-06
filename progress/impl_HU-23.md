# Implementación HU-23 — Filtros de búsqueda por precio, capacidad y características ★ Decorator

- **Dueño:** Sebastian Garate · **Rama:** `feature/HU-23-filtros-busqueda` · **Agente:** implementer · 2026-11-05
- **Spec:** `specs/HU-23/` (R1–R30, T1–T23), aprobado por el dueño el 2026-11-05 con P1–P8 por defecto.
- **Tasks:** T1–T23 hechas (`[x]` en `specs/HU-23/tasks.md`).
- **Verificación:** ver § Resultado al final.

## Trazabilidad R → test

| R | Tests |
|---|-------|
| R1 | `EspacioTest.crearGuardaCaracteristicas`, `CaracteristicaTest.deCodigos`, `FichaEspacioDTOTest.caracteristicasValidas`, `EspacioControllerIT.crearConCaracteristicas` |
| R2 | `EspacioTest.editarAnotaCambioDeCaracteristicas`, `EspacioServiceTest.editarCaracteristicasAudita`, `EspacioControllerIT.editarReemplazaCaracteristicas` |
| R3 | `FichaEspacioDTOTest.caracteristicasAusentes`, `FichaEspacioDTOTest.caracteristicasAusentesEnJson`, `EspacioTest.fichaSinCaracteristicas`, `EspacioControllerIT.sinCampoCaracteristicas` |
| R4 | `FichaEspacioDTOTest.caracteristicaDesconocida`, `CaracteristicaTest.esCodigoExacto`, `EspacioControllerIT.caracteristicasInvalidas` |
| R5 | `FichaEspacioDTOTest.caracteristicaRepetida`, `EspacioControllerIT.caracteristicasInvalidas` |
| R6 | `EspacioServiceTest.dtoConCaracteristicasEnOrdenFijo`, `EspacioControllerIT.consultarDevuelveCaracteristicas`, `EspacioControllerIT.crearConCaracteristicas`, `EspacioControllerIT.editarReemplazaCaracteristicas`, `EspacioControllerIT.minimizacion` (claves del DTO) |
| R7 | `CaracteristicaTest.describirEnOrdenFijo`, `EspacioTest.editarAnotaCambioDeCaracteristicas`, `EspacioTest.editarCaracteristicasDespuesDeDescripcion`, `EspacioServiceTest.editarCaracteristicasAudita`, `EspacioControllerIT.auditaCambioDeCaracteristicas` |
| R8 | `EspacioTest.editarMismasCaracteristicasSinCambios`, `EspacioServiceTest.editarMismasCaracteristicasNoAudita`, `EspacioControllerIT.auditaCambioDeCaracteristicas` |
| R9 | `EspacioServiceTest.editarNoEditableNoCambiaCaracteristicas`, `EspacioControllerIT.noEditableNoCambiaCaracteristicas` |
| R10 | `EspacioCaracteristicaIT.semillaCoincideConElEnum`, `EspacioCaracteristicaIT.llaveForaneaRechazaCodigoDesconocido`, `EspacioCaracteristicaIT.llavePrimariaRechazaParRepetido` |
| R11 | `EspacioCaracteristicaIT.borrarEspacioBorraSusCaracteristicas` |
| R12 | `FiltroPrecioMaximoTest.limiteInclusivoYOrden`, `FiltroPrecioMaximoTest.unaSolaLlamadaALaEnvuelta`, `BusquedaServiceTest.bordesValidos`, `BusquedaServiceTest.tresFiltrosCumplenTodo`, `BusquedaControllerIT.filtraPorPrecioMaximo` |
| R13 | `FiltroCapacidadMinimaTest.limiteInclusivoYOrden`, `FiltroCapacidadMinimaTest.minimaUnoAdmiteTodos`, `BusquedaServiceTest.bordesValidos`, `BusquedaControllerIT.filtraPorCapacidadMinima` |
| R14 | `FiltroCaracteristicasTest.todasLasPedidas`, `FiltroCaracteristicasTest.espacioSinCaracteristicas`, `FiltroCaracteristicasTest.conjuntoVacioLanza`, `BusquedaControllerIT.filtraPorCaracteristicas` |
| R15 | `FiltrosBusquedaTest.cadenaConLosTres`, `FiltrosBusquedaTest.soloLosPresentes`, `FiltrosBusquedaTest.permutacionesDanLaMismaLista`, `BusquedaServiceTest.tresFiltrosCumplenTodo`, `BusquedaControllerIT.tresFiltrosJuntos` |
| R16 | `FiltrosBusquedaTest.sinFiltrosDevuelveLaBase`, `BusquedaServiceTest.sinFiltrosUsaLaBase`, `BusquedaControllerIT.sinFiltrosComoHu10` (+ los tests HU-10 de `BusquedaServiceTest`/`BusquedaControllerIT`, que siguen verdes) |
| R17 | `BusquedaServiceTest.paginaSobreLaListaFiltrada`, `BusquedaControllerIT.paginaSobreLaListaFiltrada` |
| R18 | `BusquedaControllerIT.seisCamposConFiltros` |
| R19 | `BusquedaServiceTest.ningunoCumpleLosFiltros`, `BusquedaControllerIT.ningunoCumpleLosFiltros` |
| R20 | `BusquedaServiceTest.precioMaximoInvalido`, `BusquedaControllerIT.precioYCapacidadInvalidos` |
| R21 | `BusquedaServiceTest.capacidadMinimaInvalida`, `BusquedaControllerIT.precioYCapacidadInvalidos` |
| R22 | `BusquedaServiceTest.caracteristicaInvalida`, `BusquedaControllerIT.caracteristicaInvalida` (incluye `TECHADO,VESTUARIOS`) |
| R23 | `BusquedaServiceTest.caracteristicaRepetida`, `BusquedaControllerIT.caracteristicaInvalida` |
| R24 | `BusquedaServiceTest.erroresDeFiltrosEnOrden`, `BusquedaControllerIT.todosLosErroresEnOrden` |
| R25 | `BusquedaServiceTest.filtroInvalidoNoBusca` (+ `verifyNoInteractions(busqueda)` en R20–R23) |
| R26 | `BusquedaControllerIT.ignoraParametrosDesconocidos` |
| R27 | `LimiteCatalogoIT.filtrosDescuentanDelMismoCupo` |
| R28 | `LimiteCatalogoIT.filtrosCon429` |
| R29 | `CatalogoEspaciosServiceImplTest.publicadosConCapacidadYCaracteristicas`, `BusquedaControllerIT.tresFiltrosJuntos` (EntityGraph real, espacio con varias características aparece una vez) |
| R30 | `CatalogoEspaciosServiceImplTest.caracteristicasValidas`, `CaracteristicaTest.listaFijaEnOrden`, `CaracteristicaTest.esCodigoExacto` |

Todos llevan `@DisplayName("HU-23 R<n>: …")`.

## ★ Decorator

| Rol GoF | Clase |
|---------|-------|
| Component | `busqueda/service/BusquedaEspacios` (solo javadoc actualizado) |
| ConcreteComponent | `busqueda/service/BusquedaBase` (sin cambios) |
| Decorator | `busqueda/service/FiltroBusqueda` (abstracta, `buscar` final que reenvía y filtra con `admite`) |
| ConcreteDecorator | `FiltroPrecioMaximo`, `FiltroCapacidadMinima`, `FiltroCaracteristicas` |
| Ensamblador | `FiltrosBusqueda.envolver(base)` (solo los presentes; sin filtros devuelve la base) |
| Client | `BusquedaService.buscar(ConsultaBusqueda)` |

Prueba de composición: `FiltrosBusquedaTest` (cadena vía `envuelta()`, 6 permutaciones).

## Archivos

### Backend — nuevos
- `2_Backend/src/main/resources/db/migration/V7_1__caracteristicas.sql`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/espacios/domain/Caracteristica.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/espacios/dto/CaracteristicasDeLista.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/espacios/dto/ValidadorCaracteristicasDeLista.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/busqueda/service/{FiltroBusqueda, FiltroPrecioMaximo, FiltroCapacidadMinima, FiltroCaracteristicas, FiltrosBusqueda, ConsultaBusqueda}.java`
- Tests: `espacios/domain/CaracteristicaTest`, `espacios/repository/EspacioCaracteristicaIT`,
  `busqueda/service/{FiltroPrecioMaximoTest, FiltroCapacidadMinimaTest, FiltroCaracteristicasTest, FiltrosBusquedaTest}`

### Backend — modificados
- `espacios/domain/{Espacio, DatosEspacio}.java`, `espacios/dto/{FichaEspacioDTO, EspacioDTO}.java`,
  `espacios/service/{EspacioService, EspacioPublicado, CatalogoEspaciosService}.java`,
  `espacios/service/impl/CatalogoEspaciosServiceImpl.java`, `espacios/repository/EspacioRepository.java`
- `busqueda/service/{BusquedaService, BusquedaEspacios}.java`, `busqueda/controller/BusquedaController.java`
- Tests: `EspacioTest`, `FichaEspacioDTOTest`, `EspacioServiceTest`, `FotoEspacioServiceTest` (constructor de
  `EspacioDTO`), `CatalogoEspaciosServiceImplTest`, `BusquedaBaseTest` y `BusquedaServiceTest` (constructor de
  `EspacioPublicado`; HU-10 adaptado a `buscar(ConsultaBusqueda)` con un helper `buscar(tipo, zona, fecha, pagina)`),
  `EspacioControllerIT` (`CLAVES_DETALLE` + `caracteristicas`), `BusquedaControllerIT`, `LimiteCatalogoIT`

### Frontend
- Nuevos: `3_frontend/src/components/ui/GrupoCasillas.jsx`, `GrupoCasillas.module.css`
- Modificados: `components/ui/Campo.jsx` (`step`), `api/busqueda.js`, `pages/busqueda/BuscarEspacios.jsx`,
  `pages/busqueda/BuscarEspacios.module.css` (`.filaCompleta`, `.accionVacio`), `pages/busqueda/textosBusqueda.js`,
  `pages/espacios/EditarEspacio.jsx`, `pages/espacios/textosEspacios.js`

### Docs
- `docs/architecture.md` (párrafo de `CatalogoEspaciosService`, sección "Filtros: qué deja HU-23", fila HU-23 de
  "Patrones por historia"), `docs/modelo-datos.md` (`caracteristica` con PK `codigo`, `espacio_caracteristica`,
  restricciones), `docs/frontend.md` (`GrupoCasillas`, `step` de `Campo`, pantalla de búsqueda con filtros).

## Desviaciones y notas

- Sin desviaciones de requirements ni de decisiones de diseño.
- Detalles de implementación no fijados por el spec:
  - `CaracteristicasDeLista` lleva `@Target(FIELD)` (Bean Validation de records valida el campo; con más targets se
    duplicaría la violación).
  - `ConsultaBusqueda` copia con `Collections.unmodifiableList(new ArrayList<>(…))` (la alternativa que el design
    permite), no con `List.copyOf`.
  - `EspacioCaracteristicaIT` crea su propio arrendador (usuario + perfil) y borra solo sus filas, para no interferir
    con otras IT del mismo contexto.
  - En `BuscarEspacios.jsx` el efecto de búsqueda lee los parámetros desde `consulta` (texto) para depender de un valor
    estable (`getAll` devuelve un arreglo nuevo en cada render); `GrupoCasillas` de la búsqueda va en una fila completa
    de la grilla del formulario.
- R28 "sin ejecutar la búsqueda": se prueba de forma observable (con cupo agotado, una búsqueda con un filtro inválido
  responde 429 y no 400, así que el servicio no corrió), sin un espía sobre `BusquedaService` para no crear otro
  contexto de Spring.
- No se tocó la matriz §7 de `docs/requerimientos.md`.

## Resultado

`./init.sh` completo (sin `--rapido`) el 2026-11-05: exit 0.
- Backend unitarias (`./mvnw test`): 1605 tests, 0 fallos.
- Backend integración (`./mvnw verify`, Testcontainers PostgreSQL): 956 tests, 0 fallos.
- ESLint sin errores ni advertencias; `npm run build` OK; sin colores fuera de `tokens.css`; resto de chequeos del arnés [OK].

Pendiente: revisión del reviewer. No se marcó `done` ni se hizo commit.
