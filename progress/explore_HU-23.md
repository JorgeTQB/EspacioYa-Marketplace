# Exploración HU-23 — Filtros de búsqueda (precio, capacidad, características) · Decorator

## Pregunta

Cómo extender la búsqueda pública de HU-10 con filtros componibles (precio máximo, capacidad mínima,
características de lista fija) con Decorator, y qué hay que agregar a `espacios` (HU-07) para que el
arrendador marque características (CA1). Historia: `docs/historias/HU-23.md` (CA1 marcar características al
editar; CA2 resultados cumplen todos los filtros + tipo/zona/fecha; CA3 sin resultados → sugerir quitar
filtro; STRIDE T lista blanca en servidor; D rate limiting compartido con HU-10). Trazabilidad en
`docs/requerimientos.md:69` (RF-B-08), `:82-83` (RF-C-06, RF-C-07), `:155` (RNF-SEG-10), `:224` (RN-15),
`:261` (fila HU-23: SEG-10, 17; RN-15).

## Archivos analizados

- Backend búsqueda: `2_Backend/src/main/java/pe/edu/ulima/espacioya/busqueda/{controller/BusquedaController,
  controller/ReglasLimiteBusqueda, controller/LimiteCatalogo, service/BusquedaService, service/BusquedaEspacios,
  service/BusquedaBase, service/CriteriosBusqueda, dto/ResultadoBusquedaDTO, dto/ResultadoEspacioDTO,
  dto/FichaPublicaDTO}.java`
- Backend espacios: `espacios/domain/{Espacio, DatosEspacio}.java`, `espacios/dto/{FichaEspacioDTO, EspacioDTO}.java`,
  `espacios/service/{EspacioService, EspacioPublicado}.java`, `espacios/service/impl/CatalogoEspaciosServiceImpl.java`,
  `espacios/repository/EspacioRepository.java`
- Migraciones: `2_Backend/src/main/resources/db/migration/` (V2_2, V3_2 y listado)
- `comun/` (listado), `comun/error/ManejadorGlobalErrores.java`
- Tests: `busqueda/service/{BusquedaBaseTest, BusquedaServiceTest}`, `busqueda/controller/BusquedaControllerIT`
- Frontend: `3_frontend/src/pages/busqueda/{BuscarEspacios.jsx, textosBusqueda.js}`, `api/busqueda.js`,
  `components/ui/Campo.jsx`, `pages/espacios/EditarEspacio.jsx`, `package.json`
- Docs: `docs/architecture.md` (§ catálogo 235-257, patrones 668-681), `docs/modelo-datos.md`,
  `docs/requerimientos.md`, `docs/frontend.md` (kit 175-190), `specs/HU-10/design.md:468-469`,
  `specs/HU-11/design.md:31,429`

## Hallazgos

### 1. Búsqueda actual (HU-10)

- **Endpoint:** `GET /api/espacios` (`ReglasLimiteBusqueda.RUTA_BUSQUEDA`, `busqueda/controller/ReglasLimiteBusqueda.java:11`).
  `BusquedaController.buscar` (`BusquedaController.java:20-26`) recibe `tipo`, `zona`, `fecha`, `pagina` como
  `String` opcionales (`@RequestParam(required = false)`) y delega en `BusquedaService.buscar`. Otros parámetros
  se ignoran (comentario línea 19; IT "R22: tamano, size y limit se ignoran", `BusquedaControllerIT.java:401`).
- **Servicio:** `BusquedaService.buscar` (`BusquedaService.java:53-88`), `@Transactional(readOnly = true)`:
  - Valida y acumula `ErrorCampoDTO` en orden tipo → zona → fecha → pagina (líneas 55-71) y lanza
    `ValidacionException(errores)` (línea 74) → 400 con errores por campo (`ManejadorGlobalErrores.java:65`;
    mensaje general "Revisa los datos del formulario." según el IT). Constantes `CAMPO_*` y `MENSAJE_*` en 28-39.
  - Tipo: `catalogo.esTipoValido` (enum `TipoEspacio`); zona: `catalogo.existeZona`; fecha: ISO estricta y
    dentro de `VentanaReserva` (hoy..hoy+55, `ZonaHoraria.hoy(reloj)`); pagina: solo dígitos, ≥1, por defecto 1.
  - R29: si hay errores no se llama a la búsqueda ni a la disponibilidad (línea 72).
  - Arma `CriteriosBusqueda(tipo, zona, dia)` y llama `busqueda.buscar(...)` (línea 77): recibe **todos** los
    disponibles ya ordenados; pagina en memoria (`TAMANO_PAGINA = 20`, skip/limit, `hayMas`), pide
    `fotosPrincipales` solo para los ids de la página y mapea a `ResultadoEspacioDTO`.
- **DTO respuesta:** `ResultadoBusquedaDTO(int pagina, List<ResultadoEspacioDTO> resultados, boolean hayMas)` sin
  total (DT-8). `ResultadoEspacioDTO(id, nombre, zona, zonaNombre, precioHora, fotoPrincipal)` — no expone
  capacidad ni características hoy.
- **Búsqueda base:** `BusquedaBase` (`@Service`, `BusquedaBase.java:12-33`): `catalogo.publicados(tipo, zona)` →
  candidatos; `disponibilidad.conBloquesLibres(ids, fecha)` en lote; filtra conservando orden.
- **Consulta:** JPQL en `EspacioRepository.java:35-46` (`buscarPublicados` / `buscarPublicadosExcluyendo`),
  `estado = PUBLICADO and tipo and zona [and arrendadorId not in :excluidos] order by precioHora asc, id asc`.
  `CatalogoEspaciosServiceImpl.publicados` (`:56-77`) aplica la regla HU-09 D1 (arrendadores no ACTIVA fuera) y
  mapea a `EspacioPublicado(id, nombre, tipo, zona, zonaNombre, precioHora)`
  (`espacios/service/EspacioPublicado.java:6`). **No lleva capacidad ni características.**
  `busqueda` nunca lee repositorios de `espacios` (`docs/architecture.md:241`).

### 2. Punto de extensión para Decorator y patrones previos

- **Ya preparado:** `busqueda/service/BusquedaEspacios.java` (interfaz Component: `List<EspacioPublicado>
  buscar(CriteriosBusqueda)`), javadoc líneas 7-10: "HU-23 agrega filtros como decoradores que implementan
  esta interfaz y envuelven a BusquedaBase". `BusquedaBase` = ConcreteComponent. `BusquedaService` = Client,
  depende solo de la interfaz (`BusquedaService.java:43,47`).
- `specs/HU-10/design.md:468-469`: "HU-23 agregará decoradores `implements BusquedaEspacios` que envuelven a
  `BusquedaBase` (y se marcan `@Primary` o se componen en una `@Configuration`); `BusquedaService` no cambia."
  `docs/architecture.md:679`: "cada filtro envuelve a la búsqueda base de HU-10 y se combinan libremente".
- `CriteriosBusqueda` (`CriteriosBusqueda.java:6`) es un record `(tipo, zona, fecha)`: hay que ampliarlo con los
  filtros opcionales (p. ej. `precioMaximo`, `capacidadMinima`, `Set<Caracteristica>`) o crear un objeto de
  filtros aparte. Se construye en un solo sitio de main (`BusquedaService.java:77`) y en tests.
- **Patrones existentes a imitar (estilo javadoc con ★ y rol GoF):**
  - Strategy: `reservas/domain/PoliticaReembolso.java:8`, Context `reservas/domain/CalculoReembolso.java:6`,
    ConcreteStrategy `ReembolsoUsuarioSinAnticipacion.java:3`; Client `CancelacionReservaService.java:36`.
  - Command: `pagos/service/reembolso/{ComandoReembolso, SolicitudReembolso, SolicitudesReembolso}.java`.
  - Adapter/Facade: `pagos/service/pasarela/{PasarelaPago, MercadoPagoAdapter}.java`, `pagos/service/CheckoutFacade.java`.
  - Iterator/DAO: `reservas/domain/{HistorialReservas, IteradorReservas, Pagina}.java`, `reservas/repository/{ReservaDAO, ReservaDAOJpa}.java`.
  - No hay ningún Decorator previo en el código.

### 3. Modelo de datos de espacios

- Entidad `espacios/domain/Espacio.java`, tabla `espacio` (`V2_2__espacio.sql:36-53`):
  - `precio_hora NUMERIC(8,2)` NOT NULL, `CHECK 1..100000` (precio **por hora**, único, RN-18; `Espacio.java:53-54`
    `BigDecimal precioHora`, escala 2 con `RoundingMode.UNNECESSARY`).
  - `capacidad INTEGER` NOT NULL, `CHECK 1..100000` (`Espacio.java:50-51`, `int`).
  - **Características: no existen** en código ni BD. `docs/modelo-datos.md:26` las planifica como
    `caracteristica` (id, nombre) / `espacio_caracteristica` (espacio_id + caracteristica_id), relación N:M
    (`:91`), y `:112-114` dice que tablas de Release 2 van en migraciones nuevas. Lista fija según HU:
    techado, iluminación, vestuarios, estacionamiento (`requerimientos.md:69`).
  - Precedentes de "catálogo cerrado": `zona` (tabla sembrada en V2_2 con PK código texto) y `TipoEspacio`
    (enum + `CHECK`). Cualquiera de los dos estilos sirve; el doc pide tabla `caracteristica` (id, nombre).
- Índice: `V3_2__indices_busqueda.sql:2` `ix_espacio_busqueda (estado, tipo, zona, precio_hora, id)` — cubre
  orden y un filtro `precio_hora <=`; capacidad no está indexada.
- **Última migración: `V6_1__perfil_usuario.sql`.** Lista completa: V1_1..V1_5, V2_1..V2_4, V3_1..V3_3, V4_1..V4_3,
  V5_1..V5_3, V6_1. HU-23 (Sprint 7) usaría `V7_1__caracteristicas.sql` (convención versión = sprint, verificar
  en `docs/conventions.md`).
- Escritura de la ficha: `FichaEspacioDTO` (`espacios/dto/FichaEspacioDTO.java:29-106`, Bean Validation +
  `aDatos()`), `DatosEspacio` record (`domain/DatosEspacio.java:8`), `Espacio.editar` devuelve la lista de
  cambios para auditoría (`Espacio.java:86-108`), `EspacioService.crear/editar` (`EspacioService.java:105-129`)
  auditan `EDICION` con esos cambios; `EspacioDTO` (`dto/EspacioDTO.java:10`) no tiene características.
  `PUT /api/espacios/mios/{id}`-like en `EspacioController.java:54-56` (`@Valid @RequestBody FichaEspacioDTO`).
- Ficha pública: `FichaPublicaDTO` (`busqueda/dto/FichaPublicaDTO.java:12`); HU-11 DT-16 prevé que HU-23 le
  agregue características (`specs/HU-11/design.md:31,429`) — opcional, no está en los CA de HU-23.

### 4. Componentes de `comun` y requisitos

- Errores: `comun/error/ValidacionException` (varios `ErrorCampoDTO`) → 400 en `ManejadorGlobalErrores.java:65`;
  `ReglaNegocioException(campo, mensaje)`; `ErrorDTO`. Patrón de validación manual con texto, igual que HU-10.
- Rate limiting: `comun/limite/{ReglaLimite, FiltroLimiteIntentos, LimitadorIntentos}`; cupo de catálogo
  `busqueda/controller/LimiteCatalogo` (grupo `catalogo`, 120/IP/min). Si los filtros van como **query params
  del mismo `GET /api/espacios`**, ya cuentan en la regla existente sin cambios (D cubierto). Si se crea otra
  ruta (p. ej. `GET /api/caracteristicas`), registrarla con `limite.regla("GET", ...)` en un `@Bean`
  (`docs/architecture.md:248-257`).
- Paginación: no hay componente común; `BusquedaService` pagina en memoria (20). `reservas/domain/Pagina` es de
  `reservas`.
- Requisitos: RF-B-08 (marcar de lista fija), RF-C-06 (filtrar AND con tipo/zona/fecha), RF-C-07 (sugerir
  quitar filtro), RNF-SEG-10 (lista blanca), RNF-SEG-17 (rate limit), RN-15 (solo publicados de arrendadores
  no suspendidos), RNF-SEG-16 (minimización de datos en búsqueda).

### 5. Frontend

- `pages/busqueda/BuscarEspacios.jsx`: los filtros viven en la URL con `useSearchParams`
  (`?tipo=&zona=&fecha=&pagina=`, comentario línea 25); `hayBusqueda` exige tipo, zona y fecha (línea 33);
  borrador del formulario en estado local reiniciado al cambiar la URL (70-72); `alBuscar` y `irAPagina`
  reescriben los params (81-88) — **hay que propagar los nuevos filtros en ambos**. `CAMPOS_FORMULARIO`
  (línea 15) filtra los errores por campo que se muestran: hay que añadir los nuevos campos.
  Vacío en página 1 muestra `SIN_RESULTADOS` + `SUGERENCIA_SIN_RESULTADOS` (136-141) → punto de CA3 (texto
  distinto si hay filtros activos: "quita algún filtro").
- `pages/busqueda/textosBusqueda.js`: textos `SIN_RESULTADOS`, `SUGERENCIA_SIN_RESULTADOS`, `SIN_MAS_RESULTADOS`.
- `api/busqueda.js:4-7` `buscarEspacios({ tipo, zona, fecha, pagina })` con `URLSearchParams`; sin token.
  Para varias características: `parametros.append('caracteristica', x)` repetido o lista separada por comas
  (decidir en el design; el backend hoy recibe `String`).
- Kit `components/ui/Campo.jsx`: soporta `select`, `checkbox` (línea 36-52, `valor` booleano, `onChange(checked)`),
  `textarea`, `archivo` y genérico con `min`/`max` (sirve para `tipo="number"`). **No tiene prop `step`** (para
  precio con decimales) ni un grupo de casillas/fieldset; según `docs/frontend.md:177-179` lo que falte se
  agrega al kit y se anota en la tabla (`:184`). No hay componente de rango.
- `pages/espacios/EditarEspacio.jsx`: formulario de la ficha (`FICHA_VACIA` 36-44, `fichaDe` 46-56, `cuerpoDe`
  63-65, campos 257-314) → aquí van las casillas de características (CA1); `textosEspacios.js` tiene
  `TIPOS_ESPACIO` y `formatoPrecio` (posible lugar para la lista de características con etiquetas).
- `docs/frontend.md:381` todo `Campo` con `<label>`; `:402` no crear campos propios en pantallas.
- No hay tests de frontend (no hay script `test` en `3_frontend/package.json`, solo `build` y `lint`).

### 6. Tests existentes

- `busqueda/service/BusquedaBaseTest.java`: Mockito (`@ExtendWith(MockitoExtension.class)`), mocks de
  `CatalogoEspaciosService` y `DisponibilidadService`, `@DisplayName("HU-10 Rx: ...")`, AssertJ
  `extracting(EspacioPublicado::id).containsExactly(...)`. Modelo para tests unitarios de cada decorador.
- `busqueda/service/BusquedaServiceTest.java`: validación por campo, paginación, R29 (no llama a la búsqueda si
  hay errores); DisplayNames `HU-10 R18..R29`. Extender con validación de los nuevos params.
- `busqueda/controller/BusquedaControllerIT.java`: `@SpringBootTest @AutoConfigureMockMvc`, Testcontainers
  PostgreSQL, `RelojControlable` fijo 2026-10-28 10:00 Lima, doble `ReservasFalsas` (`@Primary`), una IP por test
  para no compartir cupo, semillas por JDBC (`DELETE FROM espacio` y demás en `@BeforeEach`, 129-139). Casos
  R1..R41 incl. ignorar params extra (401), errores en orden (475) y cupo (490).
- Otros: `espacios/service/impl/CatalogoEspaciosServiceImplTest`, `espacios/controller/EspaciosControllerSoporte`
  (ficha de espacio) para CA1.

## Riesgos / dudas abiertas

1. **Inyección de beans con varios `BusquedaEspacios`:** `BusquedaBase` es `@Service`; si los decoradores
   también son beans que implementan la interfaz, `BusquedaService` tendrá ambigüedad. Opciones: componer la
   cadena en una `@Configuration` (un solo bean `BusquedaEspacios` expuesto, `BusquedaBase` inyectado por tipo
   concreto) o `@Primary`. Además, como los filtros son opcionales por petición, la composición estática siempre
   envuelve todos y cada decorador se salta si su filtro es nulo — o bien se arma la cadena por petición en el
   servicio (pero entonces `BusquedaService` cambia, contra `specs/HU-10/design.md:469`). Decidir en el design.
2. **Datos insuficientes en `EspacioPublicado`:** no lleva `capacidad` ni características; los decoradores
   necesitan esos datos. Ampliar el record rompe 5 `new EspacioPublicado(` (1 main, 4 tests:
   `BusquedaServiceTest`, `BusquedaBaseTest`, `CatalogoEspaciosServiceImplTest` x2). Alternativa: los
   decoradores consultan `CatalogoEspaciosService` por lote (nuevo método, p. ej. `caracteristicasDe(ids)`).
   `busqueda` no puede leer repositorios de `espacios`.
3. **Filtrar en memoria vs. en SQL:** con Decorator sobre la lista, los filtros se aplican tras la
   disponibilidad (más trabajo de disponibilidad). Para eficiencia, cada decorador puede filtrar antes de la
   disponibilidad solo si se reestructura `BusquedaBase`; el orden de envolvimiento afecta el costo, no el
   resultado. Documentar la elección (tamaño S, catálogo pequeño → memoria es razonable).
4. **Lista blanca vs. R22 de HU-10:** HU-10 ignora parámetros desconocidos; HU-23 exige validar filtros contra
   lista blanca. Interpretación probable: los **valores** de `caracteristica` deben pertenecer al catálogo
   fijo (400 por campo si no), `precioMaximo`/`capacidadMinima` numéricos y en rango (1..100000, ≤2 decimales);
   nombres de parámetros desconocidos se siguen ignorando. Confirmar; definir orden de errores (después de
   pagina o antes) sin romper el IT R28 (`BusquedaControllerIT.java:475`).
5. **Formato multi-valor de características en la query** (`?caracteristica=A&caracteristica=B` vs.
   `?caracteristicas=A,B`) y límite de repeticiones (≤4, sin duplicados).
6. **CA1 y compatibilidad de `FichaEspacioDTO`:** el PUT reemplaza la ficha completa. Si se añade
   `caracteristicas`, decidir si ausente/null = lista vacía (borra) o "sin cambio"; los tests existentes de
   HU-07/HU-21 envían JSON sin ese campo. Auditar el cambio de características en `EDICION`
   (`Espacio.editar` devuelve cambios; con relación N:M fuera de la entidad hay que añadirlo aparte).
   Estados no editables (BAJA, OCULTO) deben rechazar también el cambio de características.
7. **Migración nueva** (`V7_1__...`): con FK `espacio_id ... ON DELETE CASCADE`, porque 11 clases de test
   hacen `DELETE FROM espacio` (sin borrar tablas hijas nuevas). Sembrar el catálogo fijo en la misma migración
   y `CHECK`/PK para lista cerrada. Decidir tabla `caracteristica` (como dice `modelo-datos.md:26`) vs. enum +
   tabla de relación con `CHECK`; si se desvía, actualizar `docs/modelo-datos.md` en el mismo PR.
8. **CA3:** el backend no distingue "sin resultados por filtros"; basta con que el frontend muestre la
   sugerencia de quitar filtros cuando hay alguno activo. Confirmar si se quiere una pista del servidor (no
   recomendable: revelaría cuántos hay sin filtros).
9. **Exponer capacidad/características en `ResultadoEspacioDTO`** no es necesario para CA2; añadirlas cambia el
   contrato (IT R14/R15 verifica "seis campos", `BusquedaControllerIT.java:219`). Igual con `FichaPublicaDTO`
   (HU-11 DT-16 lo deja abierto).
10. **Kit frontend:** `Campo` no tiene `step` (precio decimal) ni grupo de casillas; habrá que ampliarlo y anotar
    en `docs/frontend.md:184`. Sin infraestructura de tests de frontend.
