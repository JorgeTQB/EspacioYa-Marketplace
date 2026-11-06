# HU-23 — Tasks: Filtros de búsqueda por precio, capacidad y características ★ Decorator

> Orden pensado para que cada paso compile y sus pruebas pasen antes del siguiente. Todas las pruebas llevan
> `@DisplayName("HU-23 R<n>: …")`. El implementer documenta el mapa R → test en `progress/impl_HU-23.md`.
> Diseño: `specs/HU-23/design.md` (§ 6 para el patrón Decorator).

## Base de datos y dominio de `espacios` (CA1)

- [x] T1 — Crear `R/db/migration/V7_1__caracteristicas.sql` (tabla `caracteristica` sembrada con los 4 códigos,
  `espacio_caracteristica` con `pk_espacio_caracteristica`, FK a `caracteristica(codigo)` y FK a `espacio(id)`
  `ON DELETE CASCADE`), según `design.md` § 7. Antes, confirmar en `develop` que `V7_1` sigue libre. Cubre: R10, R11.
- [x] T2 — Añadir `EspacioCaracteristicaIT` (`T/espacios/repository/`): la semilla coincide con
  `Caracteristica.values()`; FK rechaza un código desconocido; PK rechaza un par repetido; borrar el espacio
  borra sus filas. Cubre: R10, R11.
- [x] T3 — Crear `espacios/domain/Caracteristica` (`esCodigo`, `deCodigos`, `describir`) y su
  `CaracteristicaTest`. Cubre: R4, R7, R30.
- [x] T4 — Ampliar `DatosEspacio` con `Set<Caracteristica> caracteristicas` y el constructor secundario de HU-07
  (DT-15). Ampliar `Espacio` con la `@ElementCollection`, `getCaracteristicas()` (orden del enum), `asignar` y el
  cambio `caracteristicas=[…]->[…]` en `editar`. Añadir a `EspacioTest` un test por R1 (crear), R7 y R8. Cubre:
  R1, R2, R7, R8.

## Ficha del espacio (CA1, STRIDE T)

- [x] T5 — Crear `espacios/dto/CaracteristicasDeLista` y `ValidadorCaracteristicasDeLista`; ampliar
  `FichaEspacioDTO` con `List<String> caracteristicas`, `M_CARACTERISTICAS`, `aDatos()` y el constructor
  secundario. Añadir a `FichaEspacioDTOTest` los casos válida, `null`, código desconocido, minúsculas, `""`,
  elemento `null` y repetido (error en el campo `caracteristicas`). Cubre: R3, R4, R5.
- [x] T6 — Ampliar `EspacioDTO` con `List<String> caracteristicas` y llenarlo en `EspacioService.aDto` (orden de la
  lista fija). Actualizar `FotoEspacioServiceTest` (constructor de `EspacioDTO`). Añadir a `EspacioServiceTest`
  un test de `aDto` con características desordenadas y otro de `editar` en `BAJA` que no cambia las
  características. Cubre: R6, R9.
- [x] T7 — Añadir a `EspacioControllerIT` (con `EspaciosControllerSoporte`): `POST` con características (R1),
  `PUT` que las reemplaza (R2), `PUT` sin el campo deja la lista vacía (R3), código inválido y repetido → 400 en
  `caracteristicas` sin cambios en `espacio_caracteristica` (R4, R5), `GET …/{id}` devuelve `caracteristicas`
  en orden fijo (R6), fila de `auditoria` `EDICION_ESPACIO` con `caracteristicas=[]->[…]` (R7), `PUT` sobre un
  espacio en `BAJA` → 400 *no editable* (R9). Cubre: R1, R2, R3, R4, R5, R6, R7, R9.

## Servicio público de `espacios` (datos para los filtros)

- [x] T8 — Ampliar `EspacioPublicado` con `int capacidad` y `Set<String> caracteristicas`; añadir
  `esCaracteristicaValida` a `CatalogoEspaciosService` e implementarlo en `CatalogoEspaciosServiceImpl`, que
  además mapea capacidad y características en `publicados`; poner
  `@EntityGraph(attributePaths = "caracteristicas")` en `EspacioRepository.buscarPublicados*`. Actualizar los
  `new EspacioPublicado(` de `BusquedaServiceTest`, `BusquedaBaseTest` y `CatalogoEspaciosServiceImplTest`.
  Añadir a `CatalogoEspaciosServiceImplTest` un test por R29 y R30. Cubre: R29, R30.

## ★ Decorator en `busqueda` (CA2)

- [x] T9 — Crear el **Decorator** base `busqueda/service/FiltroBusqueda` (abstracta, `implements
  BusquedaEspacios`, guarda la `envuelta`, `buscar` final que reenvía y conserva en orden lo que `admite`,
  `envuelta()` de paquete), con javadoc ★ y su rol GoF como en `PoliticaReembolso`/`ComandoReembolso`. Cubre: R12,
  R13, R14 (vía T10).
- [x] T10 — Crear los **ConcreteDecorator** `FiltroPrecioMaximo`, `FiltroCapacidadMinima` y
  `FiltroCaracteristicas`, y sus pruebas `FiltroPrecioMaximoTest` (límite inclusivo, orden conservado, una sola
  llamada a la envuelta), `FiltroCapacidadMinimaTest` (límite inclusivo) y `FiltroCaracteristicasTest` (todas las
  pedidas, espacio sin características, conjunto vacío → `IllegalArgumentException`). La envuelta en las pruebas
  es una lambda de `BusquedaEspacios`. Cubre: R12, R13, R14.
- [x] T11 — Crear el ensamblador `FiltrosBusqueda` (`record`, `NINGUNO`, `vacios()`, `envolver(base)` con el orden
  fijo de `design.md` § 6.4) y **`FiltrosBusquedaTest`, la prueba de composición**: sin filtros `envolver`
  devuelve la misma instancia de la base (R16); con los tres, la cadena recorrida con `envuelta()` es
  `FiltroCaracteristicas → FiltroCapacidadMinima → FiltroPrecioMaximo → base`; con solo capacidad y
  características, `FiltroCaracteristicas → FiltroCapacidadMinima → base`; las 6 permutaciones manuales de los
  tres decoradores sobre la misma base dan la misma lista que `envolver` (R15). Cubre: R15, R16.

## Búsqueda filtrada y lista blanca (CA2, CA3, STRIDE T)

- [x] T12 — Crear `ConsultaBusqueda`; cambiar `BusquedaService.buscar` a `buscar(ConsultaBusqueda)` con los
  campos, mensajes y validaciones de `design.md` § 4 (regex + rango, `catalogo.esCaracteristicaValida`, sin
  repetidos, un error por campo después de los de HU-10), armar `FiltrosBusqueda` y buscar con
  `filtros.envolver(busqueda)`. Adaptar los tests de HU-10 a la firma nueva. Cubre: R12, R13, R14, R17, R19, R20,
  R21, R22, R23, R24, R25.
- [x] T13 — Añadir a `BusquedaServiceTest` un test por R20, R21, R22 y R23 (con los ejemplos de cada
  requirement), uno de orden de errores con todos los campos inválidos (R24), uno que verifica que la búsqueda
  simulada no se llama con un filtro inválido (R25), uno de paginación sobre la lista filtrada con 25 candidatos
  de los que 21 cumplen (R17) y uno de lista vacía con filtros (R19). Cubre: R17, R19, R20, R21, R22, R23, R24, R25.
- [x] T14 — Ampliar `BusquedaController.buscar` con `precioMaximo`, `capacidadMinima` y
  `HttpServletRequest.getParameterValues("caracteristica")` → `ConsultaBusqueda` (DT-6). Cubre: R22, R26.
- [x] T15 — Añadir a `BusquedaControllerIT` semillas con capacidad y características (JDBC a
  `espacio_caracteristica`) y un test por R12, R13, R14, R15 (los tres filtros juntos, con un espacio de dos
  características que aparece una sola vez), R16 (sin filtros = HU-10), R18 (seis campos), R19 (vacío con
  filtros), R22 (`caracteristica=TECHADO,VESTUARIOS` → 400), R24 (orden de errores completo) y R26 (`precio`,
  `capacidad`, `caracteristicas` se ignoran). Cubre: R12, R13, R14, R15, R16, R18, R19, R22, R24, R26.

## Rate limiting compartido (STRIDE D)

- [x] T16 — Añadir a `LimiteCatalogoIT` (con la cifra baja de sus propiedades): búsquedas con filtros descuentan
  del mismo cupo que sin filtros y, agotado, `GET /api/espacios` con filtros responde 429 sin ejecutar la
  búsqueda. Cubre: R27, R28.

## Frontend

- [x] T17 — Kit: añadir `components/ui/GrupoCasillas.jsx` + `GrupoCasillas.module.css` (fieldset, leyenda, un
  `Campo tipo="checkbox"` por opción, error y ayuda del grupo, solo tokens) y la prop `step` a `Campo.jsx`.
  Cubre: pantallas "Buscar espacios" y "Editar espacio" (CA1, CA2).
- [x] T18 — Añadir `CARACTERISTICAS` a `pages/espacios/textosEspacios.js` y el `GrupoCasillas` "Características"
  a `pages/espacios/EditarEspacio.jsx` (`FICHA_VACIA`, `fichaDe`, `cuerpoDe`, error del campo
  `caracteristicas`). Cubre: pantalla "Editar espacio" (CA1).
- [x] T19 — Ampliar `buscarEspacios` en `api/busqueda.js` con `precioMaximo`, `capacidadMinima` y
  `caracteristicas` (un `append` por valor, omitiendo los vacíos). Cubre: pantalla "Buscar espacios" (CA2).
- [x] T20 — Ampliar `pages/busqueda/BuscarEspacios.jsx`: campos de precio máximo, capacidad mínima y
  características; filtros en la URL (`getAll('caracteristica')`), `alBuscar` sin filtros vacíos, `irAPagina`
  que conserva los filtros, `CAMPOS_FORMULARIO` con los tres campos nuevos. Cubre: pantalla "Buscar espacios"
  (CA2).
- [x] T21 — CA3: añadir `SIN_RESULTADOS_CON_FILTROS`, `SUGERENCIA_QUITAR_FILTRO` y `BOTON_QUITAR_FILTROS` a
  `textosBusqueda.js` y mostrarlos en `BuscarEspacios.jsx` cuando la página 1 está vacía y hay algún filtro en
  la URL, con el `Boton secundario` "Quitar filtros" que deja solo `tipo`, `zona`, `fecha` y `pagina=1`. Cubre:
  pantalla "Buscar espacios" (CA3).

## Documentación

- [x] T22 — Actualizar `docs/architecture.md` (fila HU-23 de "Patrones por historia" con los participantes,
  `CatalogoEspaciosService.esCaracteristicaValida` y `publicados` ampliado, sección "Filtros: qué deja HU-23"),
  `docs/modelo-datos.md` (`caracteristica` con PK `codigo`, `espacio_caracteristica` y sus restricciones) y
  `docs/frontend.md` (`GrupoCasillas` y `step` en el kit, filtros de la pantalla de búsqueda). Cubre: R10
  (documentación de la restricción), patrón ★ Decorator.

## Verificación

- [x] T23 — Ejecutar `./init.sh` completo en verde (backend `test` y `verify` con Testcontainers, ESLint y build
  del frontend, revisión de colores fuera de `tokens.css`) y dejar en `progress/impl_HU-23.md` el mapa
  R1–R30 → test. Cubre: R1–R30.
