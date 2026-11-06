# Review — HU-23

**Veredicto:** APPROVED

> Historia: HU-23 "Filtros de búsqueda por precio, capacidad y características" ★ Decorator · dueño Sebastian Garate ·
> rama `feature/HU-23-filtros-busqueda` · cambios sin commitear (34 modificados + 22 nuevos, ver `git status`).
> `./init.sh` completo (sin `--rapido`) corrido por el reviewer el 2026-11-05: **exit 0** — backend `test`
> 1605 tests / 0 fallos, `verify` (Testcontainers PostgreSQL) 956 tests / 0 fallos, ESLint sin avisos,
> `npm run build` OK, sin colores fuera de `tokens.css`, sin secretos.

## Trazabilidad requirements ↔ tests

Todos los tests llevan `@DisplayName("HU-23 R<n>: …")` (conteo por R verificado con grep en `2_Backend/src/test/java`; R1–R30 con al menos uno).

- R1: [x] `EspacioControllerIT.crearConCaracteristicas` (201, JSON y filas de `espacio_caracteristica`), `EspacioTest.crearGuardaCaracteristicas`
- R2: [x] `EspacioControllerIT.editarReemplazaCaracteristicas` (BD = exactamente las nuevas), `EspacioServiceTest.editarCaracteristicasAudita`
- R3: [x] `EspacioControllerIT.sinCampoCaracteristicas` (ausente y `null`, en POST y PUT; BD vacía), `FichaEspacioDTOTest.caracteristicasAusentes*`
- R4: [x] `EspacioControllerIT.caracteristicasInvalidas` (`techado`, `PISCINA`, `""`, `null` → 400 en `caracteristicas`, sin crear ni modificar, sin auditoría), `FichaEspacioDTOTest.caracteristicaDesconocida`
- R5: [x] `EspacioControllerIT.caracteristicasInvalidas` (repetido), `FichaEspacioDTOTest.caracteristicaRepetida`
- R6: [x] `EspacioControllerIT.consultarDevuelveCaracteristicas` (orden fijo), `EspacioServiceTest.dtoConCaracteristicasEnOrdenFijo`
- R7: [x] `EspacioControllerIT.auditaCambioDeCaracteristicas` (detalle exacto `caracteristicas=[]->[TECHADO, VESTUARIOS]`), `EspacioTest.editarAnotaCambioDeCaracteristicas`
- R8: [x] `EspacioControllerIT.auditaCambioDeCaracteristicas` (una sola auditoría, `actualizado_en` igual), `EspacioTest.editarMismasCaracteristicasSinCambios`
- R9: [x] `EspacioControllerIT.noEditableNoCambiaCaracteristicas` (BAJA y OCULTO), `EspacioServiceTest.editarNoEditableNoCambiaCaracteristicas`
- R10: [x] `EspacioCaracteristicaIT.llaveForaneaRechazaCodigoDesconocido`, `.llavePrimariaRechazaParRepetido` (`DataIntegrityViolationException`), `.semillaCoincideConElEnum`
- R11: [x] `EspacioCaracteristicaIT.borrarEspacioBorraSusCaracteristicas`
- R12: [x] `BusquedaControllerIT.filtraPorPrecioMaximo` (80 inclusivo, 79.99), `FiltroPrecioMaximoTest.limiteInclusivoYOrden`
- R13: [x] `BusquedaControllerIT.filtraPorCapacidadMinima`, `FiltroCapacidadMinimaTest.limiteInclusivoYOrden`
- R14: [x] `BusquedaControllerIT.filtraPorCaracteristicas` (AND), `FiltroCaracteristicasTest.todasLasPedidas`
- R15: [x] `FiltrosBusquedaTest.permutacionesDanLaMismaLista`, `BusquedaControllerIT.tresFiltrosJuntos` (excluye PAUSADO y sin horario; un espacio con varias características no se repite)
- R16: [x] `FiltrosBusquedaTest.sinFiltrosDevuelveLaBase` (`isSameAs`), `BusquedaControllerIT.sinFiltrosComoHu10` + tests HU-10 verdes
- R17: [x] `BusquedaServiceTest.paginaSobreLaListaFiltrada` (25 → 21, páginas de 20 y 1, `hayMas`), `BusquedaControllerIT.paginaSobreLaListaFiltrada`
- R18: [x] `BusquedaControllerIT.seisCamposConFiltros` (`containsOnlyKeys` de los seis campos)
- R19: [x] `BusquedaControllerIT.ningunoCumpleLosFiltros`, `BusquedaServiceTest.ningunoCumpleLosFiltros`
- R20: [x] `BusquedaServiceTest.precioMaximoInvalido` (todos los ejemplos del requirement + `+5`, `5.`, `.5`, `50,60`), `BusquedaControllerIT.precioYCapacidadInvalidos`
- R21: [x] `BusquedaServiceTest.capacidadMinimaInvalida`, `BusquedaControllerIT.precioYCapacidadInvalidos`
- R22: [x] `BusquedaControllerIT.caracteristicaInvalida` (incluye `TECHADO,VESTUARIOS` y `caracteristica=`), `BusquedaServiceTest.caracteristicaInvalida`
- R23: [x] `BusquedaServiceTest.caracteristicaRepetida`, `BusquedaControllerIT.caracteristicaInvalida` (`TECHADO` dos veces)
- R24: [x] `BusquedaControllerIT.todosLosErroresEnOrden` (7 errores en el orden fijo), `BusquedaServiceTest.erroresDeFiltrosEnOrden`
- R25: [x] `BusquedaServiceTest.filtroInvalidoNoBusca` (`verifyNoInteractions(busqueda)`, sin `publicados` ni `fotosPrincipales`)
- R26: [x] `BusquedaControllerIT.ignoraParametrosDesconocidos`
- R27: [x] `LimiteCatalogoIT.filtrosDescuentanDelMismoCupo` (búsquedas con y sin filtros agotan el mismo cupo)
- R28: [x] `LimiteCatalogoIT.filtrosCon429` (429 con `MENSAJE_DEMASIADOS_INTENTOS`; "sin ejecutar" se prueba de forma observable: con un filtro inválido responde 429 y no 400; justificado en `progress/impl_HU-23.md` § Desviaciones)
- R29: [x] `CatalogoEspaciosServiceImplTest.publicadosConCapacidadYCaracteristicas`; el `@EntityGraph` real lo cubre `BusquedaControllerIT.tresFiltrosJuntos`
- R30: [x] `CatalogoEspaciosServiceImplTest.caracteristicasValidas` (`null`, `""`, minúsculas, comas, espacio inicial), `CaracteristicaTest.esCodigoExacto`

## Trazabilidad con la historia (`docs/historias/HU-23.md`)

- CA1: [x] R1–R11. Implementado en `Espacio` (`@ElementCollection`, `editar`/`asignar`), `FichaEspacioDTO` + `@CaracteristicasDeLista`, `EspacioDTO`, migración `V7_1__caracteristicas.sql` y `EditarEspacio.jsx` (`GrupoCasillas` "Características").
- CA2: [x] R12–R18, R29. Decorator en `busqueda/service`, `BusquedaService.buscar(ConsultaBusqueda)`, `BuscarEspacios.jsx` con los tres filtros en la URL.
- CA3: [x] R19 + `BuscarEspacios.jsx` (`SIN_RESULTADOS_CON_FILTROS`, `SUGERENCIA_QUITAR_FILTRO` y `Boton variante="secundario"` "Quitar filtros", que deja tipo/zona/fecha/pagina=1), como en `design.md` § 11.2.
- (T) parámetros validados en servidor contra una lista blanca: [x] R4, R5, R10, R20–R26, R30. Regex antes de convertir (`BusquedaService.PRECIO`, `CAPACIDAD`), rangos, `esCaracteristicaValida`, sin repetidos, `getParameterValues` sin partir comas (`BusquedaController`), FK + PK en la BD.
- (D) rate limiting compartido con HU-10: [x] R27, R28. Misma ruta `ReglasLimiteBusqueda.RUTA_BUSQUEDA`, sin regla nueva.

La tabla final de `specs/HU-23/requirements.md` cubre los 3 CA y los 2 controles STRIDE.

## Tasks completas (`specs/HU-23/tasks.md`)

- T1: [x] `V7_1__caracteristicas.sql` coincide con `design.md` § 7; no hay `V7_*` en `develop` (verificado con `git ls-tree`).
- T2: [x] `EspacioCaracteristicaIT` (`@Import(TestcontainersConfiguration.class)`).
- T3: [x] `Caracteristica` + `CaracteristicaTest`.
- T4: [x] `DatosEspacio` (constructor compacto + secundario DT-15) y `Espacio` (colección, `getCaracteristicas` en el orden del enum, cambio en `editar`).
- T5: [x] `CaracteristicasDeLista`, `ValidadorCaracteristicasDeLista`, `FichaEspacioDTO.M_CARACTERISTICAS`, `aDatos`.
- T6: [x] `EspacioDTO.caracteristicas`, `EspacioService.aDto`.
- T7: [x] `EspacioControllerIT` (7 tests nuevos + `caracteristicas` en las claves del DTO).
- T8: [x] `EspacioPublicado`, `esCaracteristicaValida`, `@EntityGraph` en `buscarPublicados*`.
- T9: [x] `FiltroBusqueda` abstracta, `buscar` `final`, `envuelta()` de paquete, javadoc con el rol GoF.
- T10: [x] los tres ConcreteDecorator y sus tests.
- T11: [x] `FiltrosBusqueda` + `FiltrosBusquedaTest` (prueba de composición).
- T12: [x] `ConsultaBusqueda` y `BusquedaService.buscar(ConsultaBusqueda)`; tests de HU-10 adaptados.
- T13: [x] tests de R17 y R19–R25 en `BusquedaServiceTest`.
- T14: [x] `BusquedaController` con `precioMaximo`, `capacidadMinima` y `getParameterValues("caracteristica")`.
- T15: [x] `BusquedaControllerIT` (12 tests nuevos).
- T16: [x] `LimiteCatalogoIT` (2 tests nuevos).
- T17: [x] `GrupoCasillas.jsx` + `.module.css` (solo tokens) y `step` en `Campo.jsx`.
- T18: [x] `CARACTERISTICAS` en `textosEspacios.js`; `GrupoCasillas` en `EditarEspacio.jsx` (`FICHA_VACIA`, `fichaDe`; `cuerpoDe` envía el arreglo con el spread de `ficha`).
- T19: [x] `api/busqueda.js` (omite vacíos, un `append` por característica).
- T20: [x] `BuscarEspacios.jsx` (filtros en la URL, `getAll`, `alBuscar` sin vacíos, `irAPagina` conserva filtros, `CAMPOS_FORMULARIO`).
- T21: [x] textos de CA3 y botón secundario "Quitar filtros".
- T22: [x] `docs/architecture.md` (párrafo de `CatalogoEspaciosService`, sección "Filtros: qué deja HU-23", fila HU-23 de patrones), `docs/modelo-datos.md`, `docs/frontend.md`.
- T23: [x] `./init.sh` completo en verde (re-ejecutado por el reviewer) y mapa R → test en `progress/impl_HU-23.md`.

## Patrón Decorator ★ (fila HU-23 de "Patrones por historia")

Implementado tal como `design.md` § 6:
- Component `BusquedaEspacios` (solo cambia el javadoc) y ConcreteComponent `BusquedaBase` (sin cambios).
- Decorator `2_Backend/src/main/java/pe/edu/ulima/espacioya/busqueda/service/FiltroBusqueda.java`: `Objects.requireNonNull(envuelta)` y `public final List<EspacioPublicado> buscar(...)`, que reenvía y filtra con `admite` conservando el orden.
- ConcreteDecorators `FiltroPrecioMaximo` (`compareTo <= 0`, rechaza `null`), `FiltroCapacidadMinima` (`>=`) y `FiltroCaracteristicas` (`containsAll`; vacío lanza `IllegalArgumentException`).
- Ensamblador `FiltrosBusqueda.envolver`: precio adentro, luego capacidad, características afuera; sin filtros devuelve la base misma.
- Client `BusquedaService`: `filtros.envolver(busqueda).buscar(...)`, sin `if` sobre qué filtro aplicar al buscar. Los decoradores no son beans (sigue habiendo un solo bean `BusquedaEspacios`).
- **Prueba de composición**: `2_Backend/src/test/java/pe/edu/ulima/espacioya/busqueda/service/FiltrosBusquedaTest.java`. Recorre la cadena con `envuelta()` (líneas 43–52 y 63–83), comprueba la misma instancia sin filtros (54–60) y que las 6 permutaciones dan lo mismo que `envolver` (85–109).

## Checkpoints

- C1 arnés completo: [x] (los automáticos los verifica `./init.sh`, exit 0)
- C2 estado coherente:
  - [x] una sola historia `in_progress` del dueño
  - [x] existe `progress/HU-23.md`
  - n/a historias `done` con tests (HU-23 aún no está `done`)
  - [x] aprobación del dueño anotada en `progress/HU-23.md` ("Spec aprobado por Sebastian Garate el 2026-11-05")
  - [x] `progress/HU-23.md` describe solo la sesión activa
- C3 arquitectura:
  - [x] paquetes de módulo válidos, ningún controller importa un repository, sin `@Autowired`/Lombok/comodines, sin `System.out`/`console.log`
  - [x] `busqueda` usa `espacios` solo vía `CatalogoEspaciosService` y el record `EspacioPublicado`; el enum `Caracteristica` no cruza el módulo; sin puertos nuevos
  - [x] la API usa DTO `record` (`EspacioDTO`, `FichaEspacioDTO`; `ResultadoBusquedaDTO` sin cambios); no expone campos sensibles nuevos (R18)
  - [x] identidad desde la sesión (rutas `/actual/` de HU-07 sin cambios); filtros y estados se calculan en el servidor
  - [x] migración nueva `V7_1__caracteristicas.sql`; no se editó ninguna migración de `develop`
  - n/a adapters de integraciones externas (no hay integración nueva)
  - [x] nada de "fuera del alcance"
  - [x] sin secretos; sin colores fuera de `tokens.css` ni librerías nuevas
  - [x] el frontend usa el kit (`Campo`, `Boton`, `Alerta` y el nuevo `GrupoCasillas`, documentado en `docs/frontend.md`), un solo primario por pantalla ("Buscar" / "Guardar"), errores bajo su campo y API solo desde `src/api/`; coincide con `design.md` § 11
- C4 verificación real:
  - [x] nombres `<Clase>Test` / `<Clase>IT` con `@Import(TestcontainersConfiguration.class)`
  - [x] sin H2
  - [x] `Clock` fijo en `BusquedaServiceTest` y reloj controlado en las IT (no hay plazos nuevos)
  - [x] hay IT para endpoints, migración y restricciones (`EspacioCaracteristicaIT`, `EspacioControllerIT`, `BusquedaControllerIT`, `LimiteCatalogoIT`); n/a concurrencia
  - [x] cada test comprueba resultados concretos (ids, códigos HTTP, campos y mensajes de error, filas en la BD, detalle de auditoría)
  - [x] `mvnw verify` con más de 0 tests, todos verdes; lint y build OK
- C5 cierre de sesión:
  - [x] sin archivos sin trackear sospechosos (solo fuentes, specs y progress de HU-23)
  - n/a entrada en `progress/history.md` (se escribe al cerrar la sesión, después de esta revisión)
  - n/a `progress/HU-23.md` de vuelta a la plantilla (la historia aún no está terminada)
  - [x] `feature_list.json` refleja `in_progress`
  - [x] rama `feature/HU-23-filtros-busqueda`
- C6 SDD:
  - [x] existen `specs/HU-23/{requirements,design,tasks}.md`
  - [x] EARS y tabla de trazabilidad completa
  - [x] `design.md` con endpoints, clases, migración, secuencias, alternativas A1–A7 y el patrón ★ en § 6
  - n/a tasks de historias `done`; en esta historia T1–T23 están todas `[x]`
  - [x] cada R cubierto y documentado en `progress/impl_HU-23.md`
  - [x] no resuelve pendientes de §9 sin el dueño (§9.9 ya estaba resuelto; P1–P8 aprobadas por el dueño)

## Observaciones (no bloqueantes)

1. `3_frontend/src/pages/espacios/EditarEspacio.jsx:318-326`: el `GrupoCasillas` no se deshabilita en `BAJA`/`OCULTO`, igual que los demás `Campo` de la ficha (solo el botón usa `soloLectura`, línea 327). Es coherente con HU-21 y el servidor lo rechaza (R9). Se puede mejorar más adelante con una prop `deshabilitado` en el kit.
2. R28 ("sin ejecutar la búsqueda") se prueba de forma indirecta (429 en lugar de 400 con un filtro inválido), sin espía sobre `BusquedaService`. Es aceptable y está documentado en `progress/impl_HU-23.md`.

## Cambios requeridos

Ninguno.
