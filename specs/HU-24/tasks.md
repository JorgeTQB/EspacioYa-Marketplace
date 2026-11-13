# HU-24 — Tasks: Reseñas y calificaciones

> Orden pensado para que cada paso compile y sus pruebas pasen antes del siguiente. Todas las pruebas llevan
> `@DisplayName("HU-24 R<n>: …")`. El implementer documenta el mapa R → test en `progress/impl_HU-24.md`.
> Diseño: `specs/HU-24/design.md`. Abreviaturas: `J/`, `T/`, `R/`, `F/` como en `design.md` § 2.
> Antes de empezar, aplicar los cambios que Ariel Enriquez haya pedido sobre las decisiones P1–P18.

## Base de datos y dominio

- [x] T1 — Crear `R/db/migration/V7_2__resena.sql` según `design.md` § 7. Antes, confirmar en `develop` que `V7_2` sigue
  libre (HU-25 también es del sprint 7). Cubre: R13, R18, R19, R20.
- [x] T2 — Crear `EstadoResena`, `Resena` y `ResenaRepository` con `TotalesResena`. `calificacion` es `short` por
  `ddl-auto=validate` (DT-12). Cubre: R1, R13.
- [x] T3 — Añadir `ResenaRepositoryIT` (`T/busqueda/repository/`): único por `reserva_id`, `ck_resena_calificacion`,
  `ck_resena_estado`, FK sin cascada para `reserva`, `espacio` y `usuario`, `totales` y `findByEspacioIdAndEstado` solo con
  `VISIBLE`, y `reemplazarComentarios`. Cubre: R13, R18, R19, R20, R35.
- [x] T4 — Crear `MotivoNoResenable` y `ReglasResena`, con `ReglasResenaTest`: `fin` exacto, un instante antes,
  `fin + 14 días` exacto, un instante antes y no confirmada. Cubre: R5, R6, R7, R8.
- [x] T5 — Crear `ResumenCalificaciones` y `ResumenCalificacionesTest`: 4, 4, 4 y 5 dan `4.3`, un `HALF_UP` exacto
  (4.25 → 4.3) y sin reseñas (`null`, 0). Cubre: R28, R29.
- [x] T6 — Crear `J/comun/texto/SanitizadorTexto` y `SanitizadorTextoTest`: `<script>…</script>`, atributos de evento,
  `<scr<script>ipt>`, `<` y `>` sueltos, `\r\n`, `U+0000`, tildes y `&` intactos, y texto que queda vacío. Cubre: R21, R22.

## Reservas para reseñar

- [x] T7 — Crear `ReservaParaResena`, `ReservasParaResena` y `ReservasParaResenaImpl` con `ReservaDAO`, y
  `ReservasParaResenaImplTest`: id ajeno o inexistente → 404, `confirmada` con `estadoEn`, `horaFin` 24 → 00:00 del día
  siguiente y `confirmadasDe` sin canceladas ni pendientes. Cubre: R4, R6, R9.

## Crear una reseña

- [x] T8 — Crear `CrearResenaDTO` con sus lectores estrictos y `ResenaCreadaDTO`, y `CrearResenaDTOTest`: `reservaId`
  ausente, texto, decimal, 0 y fuera de `long`; `calificacion` ausente, `"4"`, `4.5`, 0, 6 y fuera de `int`; `comentario`
  ausente, número, en blanco y de 501 caracteres; 500 caracteres válidos. Cubre: R15, R16, R17.
- [x] T9 — Crear `ResenaService.crear` con `TransactionTemplate`, en el orden de DT-16, y los mensajes de `design.md`
  § 5. Cubre: R1, R3, R6, R7, R8, R9, R10, R12, R14, R21, R22, R23.
- [x] T10 — Añadir `ResenaServiceTest` con un test por R1, R3, R6, R7, R8, R9, R10 (sin tocar el puerto de reservas), R12
  (también con el plazo vencido), R14 (otra violación de integridad se relanza), R21, R22 (sin leer la reserva), R23 y
  R24. Cubre: R1, R3, R6, R7, R8, R9, R10, R12, R14, R21, R22, R23, R24.
- [x] T11 — Crear `ResenaController` con `POST /api/resenas`. Cubre: R1, R2.
- [x] T12 — Añadir `ResenaControllerIT`: 201 con sus cinco campos (R2); fila `VISIBLE` con usuario de la sesión y espacio
  de la reserva pese a `usuarioId`, `espacioId` y `estado` en el cuerpo (R1, R3); espacio `PAUSADO` (R1); pendiente,
  expirada, cancelada, futura y vencida (R6, R7, R8); ajena e inexistente con la misma respuesta (R9); `ARRENDADOR` y
  `ADMIN` (R10); sin sesión (R11); doble reseña (R12); dos envíos simultáneos, un 201 y un 400 (R14); errores por campo
  (R15, R16, R17); `<script>` guardado sin `<` ni `>` (R21); fila de `auditoria` sin el comentario (R23); sin fila tras
  un rechazo (R24). Cubre: R1, R2, R3, R6, R7, R8, R9, R10, R11, R12, R14, R15, R16, R17, R21, R23, R24.

## Reservas por reseñar

- [x] T13 — Añadir `ResenaService.pendientes`, `ResenasPendientesDTO` y `GET /api/resenas/pendientes`. Cubre: R25, R26.
- [x] T14 — Añadir a `ResenaServiceTest` un test por R25 y R26, y a `ResenaControllerIT` uno con una reserva reseñable y
  una de cada caso excluido, orden ascendente, otro rol y sin sesión. Cubre: R11, R25, R26.

## Ficha pública

- [x] T15 — Añadir `ResenaService.resumen`; ampliar `FichaPublicaDTO` y `FichaService`; actualizar `FichaServiceTest:57, :72`
  y `FichaControllerIT:267` (`design.md` § 14). Cubre: R27, R28, R29.
- [x] T16 — Añadir `ResenaPublicaDTO`, `PaginaResenasDTO`, `ResenaService.deEspacio` y `GET /api/espacios/{id}/resenas`;
  pasar `BusquedaService.pagina` y `FichaService.idValido` a paquete. Cubre: R30, R31, R33, R34, R35.
- [x] T17 — Añadir `GET /api/espacios/*/resenas` a `ConfiguracionSeguridad` y `RUTA_RESENAS` con su regla a
  `ReglasLimiteFicha`. Cubre: R32, R37, R38.
- [x] T18 — Añadir a `ResenaServiceTest` un test por R30, R33 (id malo, invisible, antes de validar `pagina`) y R34, y a
  `FichaServiceTest` uno por R27 y R29. Cubre: R27, R29, R30, R33, R34.
- [x] T19 — Añadir a `ResenaControllerIT`: 11 reseñas en dos páginas con su orden y `hayMas` (R30); exactamente cuatro
  claves por reseña (R31); sin sesión y con token inválido (R32); espacio no visible (R33); `pagina` inválida (R34); una
  `OCULTA` sembrada por JDBC fuera de lista, promedio y total (R35); crear y volver a leer ficha y lista (R36). Cubre:
  R30, R31, R32, R33, R34, R35, R36.
- [x] T20 — Añadir a `ReglasLimiteFichaTest` la regla nueva y a `LimiteFichaIT`: la ruta descuenta del cupo compartido,
  incluidos 400 y 404, y agotado responde 429 sin consultar. Cubre: R37, R38.

## Datos personales

- [x] T21 — Crear `ResenaExportadaDTO` y `DatosPersonalesResenasImpl` (`@Order(3)`, § 10), con
  `DatosPersonalesResenasImplTest`: un test por R39, R40, R41 y R42. Cubre: R39, R40, R41, R42.
- [x] T22 — Actualizar `DatosPersonalesSoporte` (`@Order(4)` en el doble y `resena` en `limpiar`) y
  `DatosPersonalesControllerIT:116, :138`. Añadir: exportación con reseñas visibles y ocultas (R39), arrendador sin la
  sección (R40), y eliminación con comentario reemplazado, calificación, estado y `usuario_id` intactos y resumen igual
  (R42, R43). Cubre: R39, R40, R42, R43.

## Frontend

- [x] T23 — Añadir `crearResena`, `listarResenasPendientes` y `listarResenasDeEspacio` a `F/api/busqueda.js`. Cubre:
  pantallas "Mis reservas" y "Ficha del espacio" (CA1).
- [x] T24 — Crear `F/pages/reservas/ResenarReserva.jsx` + `.module.css` y sus textos en `textosReservas.js`. Cubre:
  pantalla "Mis reservas" (CA1, CA2, CA3).
- [x] T25 — Ampliar `F/pages/reservas/MisReservas.jsx`: pendientes en un `Set` que se relee con `recarga`, `tarjetaPasada`
  y `Alerta exito`. Cubre: pantalla "Mis reservas" (CA1, CA2, CA3).
- [x] T26 — Crear `F/pages/busqueda/ResenasEspacio.jsx` + `.module.css` y sus textos en `textosFicha.js`. Cubre: pantalla
  "Ficha del espacio" (CA1).
- [x] T27 — Ampliar `F/pages/busqueda/FichaEspacio.jsx` con el resumen y la sección al final, con `key={espacio.id}`.
  Cubre: pantalla "Ficha del espacio" (CA1).

## Documentación

- [x] T28 — Actualizar `docs/modelo-datos.md`, `docs/architecture.md`, `docs/requerimientos.md` y `docs/frontend.md`
  según `design.md` § 16. Cubre: R13, R18, R19, R20 (documentación de las restricciones), P15 y P17.

## Verificación

- [x] T29 — Ejecutar `./init.sh` completo en verde (backend `test` y `verify` con Testcontainers, ESLint y build del
  frontend, revisión de colores fuera de `tokens.css`) y dejar en `progress/impl_HU-24.md` el mapa R1–R43 → test. Cubre:
  R1–R43.

## Reapertura ★: Decorator y DAO

> Refactorización del backend ya implementado (`design.md` DT-20 a DT-27 y § 12). **No** se tocan migraciones, DTO,
> controladores, `comun/`, `SanitizadorTexto` ni `3_frontend/` (R58). Antes de empezar, aplicar lo que Ariel Enriquez
> haya decidido sobre P19–P22. Las pruebas nuevas llevan `@DisplayName("HU-24 R<n>: …")`; las adaptadas conservan el
> suyo. Después de cada task, `./2_Backend/mvnw -f 2_Backend/pom.xml test` en verde.

### DAO

- [x] T30 — Crear `J/busqueda/repository/ResenaDAO`, `TrozoResenas` y `ResenaDAOJpa` (`@Repository`, de paquete, solo
  con `ResenaRepository`) según `design.md` § 6 y § 12.2, con el tope de offset de DT-26. Añadir el Javadoc "solo lo usa
  `ResenaDAOJpa`" a `ResenaRepository` sin cambiar sus métodos. Pruebas: `ResenaDAOJpaTest` (unitaria: `PageRequest`
  con página `n − 1`, tamaño y orden `creadoEn`, `id` descendentes, filtro `VISIBLE`, offset enorme sin consultar y el
  último que aún consulta) y `ResenaDAOJpaIT` (PostgreSQL: 11 visibles y una `OCULTA` en dos páginas con `hayMas`;
  resumen solo `VISIBLE`; `guardar` duplicado lanza `DataIntegrityViolationException` desde `guardar` dentro de un
  `TransactionTemplate`; `delUsuario` en orden ascendente y `anonimizarComentarios` solo del usuario). Cubre: R54, R55,
  R56, R57.
- [x] T31 — Pasar `ResenaService.pendientes`, `deEspacio` y `resumen` a `ResenaDAO` (`reservasResenadasPor`,
  `visiblesDeEspacio(id, n, TAMANO_PAGINA)`, `resumenVisibles`) y adaptar esas partes de `ResenaServiceTest` según
  `design.md` § 14 "Reapertura" (sin cambiar ningún `@DisplayName`). Hasta T36, `crear` sigue con `ResenaRepository`,
  así que el constructor recibe los dos un momento; T36 quita el repositorio. Prueba: `ResenaServiceTest`. Cubre: R25, R26, R27,
  R29, R30, R33, R34, R53.
- [x] T32 — Pasar `DatosPersonalesResenasImpl` a `ResenaDAO` (`delUsuario`, `anonimizarComentarios`) y adaptar los
  dobles de `DatosPersonalesResenasImplTest`. Prueba: `DatosPersonalesResenasImplTest`. Cubre: R39, R40, R41, R42, R53.
- [x] T33 — Pasar `ModeracionResenasImpl` (HU-27) a `ResenaDAO` (`buscar`, `buscarVarias`, `buscarParaActualizar`) sin
  cambiar `ModeracionResenas`, sus transacciones ni sus mensajes, y adaptar los dobles de `ModeracionResenasImplTest`.
  Si el dueño no aceptó P21, saltar esta task, quitar esos tres métodos del DAO y excluir la clase de R53. Prueba:
  `ModeracionResenasImplTest`. Cubre: R53.

### Decorator

- [x] T34 — Crear `J/busqueda/service/PublicacionResena` (Component), `SolicitudResena` con `conComentario`,
  `PublicacionResenaDecorador` (Decorator abstracto, `envuelta()` de paquete) y `PublicacionResenaBase`
  (ConcreteComponent: el actual `crearEnTransaccion` sin la auditoría, con `ResenaDAO`). Prueba:
  `PublicacionResenaBaseTest` (con `InOrder`: `delUsuario` → `existeParaReserva` → `guardar`; 404 sin consultar el DAO;
  *ya reseñada* antes que el plazo; comentario con `<b>` guardado tal cual). Cubre: R52.
- [x] T35 — Crear los ConcreteDecorators `PublicacionSanitizada`, `PublicacionTransaccional` (con el `catch` de DT-6,
  que sale de `ResenaService`) y `PublicacionAuditada` (detalle de R23). Pruebas, cada una con un doble de su envuelta:
  `PublicacionSanitizadaTest` (comentario limpio y demás campos iguales; vacío sin invocar la envuelta, campo
  `comentario`), `PublicacionTransaccionalTest` (`commit` si vuelve, `rollback` si lanza, `uq_resena_reserva` → *ya
  reseñada*, otra violación y otra excepción se relanzan iguales) y `PublicacionAuditadaTest` (registra después y devuelve
  la misma `Resena`; si la envuelta lanza, ningún `registrar` y la misma excepción). Cubre: R45, R46, R47, R48, R49, R50.
- [x] T36 — Crear `ConfiguracionPublicacionResena` (`@Bean` + `cadena(...)` estático) y convertir `ResenaService.crear`
  en Client: `exigirRol` → `publicacion.publicar(new SolicitudResena(...))` → `ResenaCreadaDTO`; el constructor queda
  `(PublicacionResena, ResenaDAO, ReservasParaResena, CatalogoEspaciosService, Clock)`. Adaptar `preparar` y
  `fechaEnLima` de `ResenaServiceTest` para usar `ConfiguracionPublicacionResena.cadena(...)` con los dobles y renombrar
  `existsByReservaId`/`saveAndFlush` (todos los tests de R1–R24 deben pasar sin cambiar su `@DisplayName`). Pruebas:
  `ConfiguracionPublicacionResenaTest` (tipos y orden de la cadena por `envuelta()`; `ResenaService` solo recibe
  `PublicacionResena` para crear) y `ResenaServiceTest`. Cubre: R44, y vuelve a cubrir R1, R3, R6–R10, R12, R14, R21–R24.
- [x] T37 — Añadir `PublicacionResenaIT` (contexto completo, `@MockitoSpyBean AuditoriaService` que lanza en
  `registrar`, reserva reseñable sembrada: la llamada falla y no queda ninguna fila en `resena`) y `AccesoResenasTest`
  (por reflexión, ningún campo `ResenaRepository` en `ResenaService`, las cuatro clases de la cadena,
  `DatosPersonalesResenasImpl` y `ModeracionResenasImpl`). Cubre: R51, R53.

### Documentación del informe y de `docs/`

- [x] T38 — Crear `docs/informe/HU-24.md` según `design.md` § 12.4, en Mermaid editable y con código copiado de los
  archivos ya refactorizados: § 7.7 (diagrama de clases de Decorator y de DAO con roles GoF y tabla "rol → clase" de
  cada uno), § 8.1 (fila del escenario: ID de P22, HU-24, "Reseñar una reserva pasada", Usuario, Release 2 / Sprint 7,
  justificación), § 8.1.1 (diagrama de secuencia UML del escenario con el flujo principal y los alternos *ya reseñada*,
  *fuera de plazo* y carrera por `uq_resena_reserva`, y su explicación breve) y § 8.1.2 (tabla de identificación del
  escenario, fragmentos de código real por clase y método, tabla "mensaje del diagrama → clase.método", explicación y
  referencia a archivos). Cubre: documentación ★ de R44–R57.
- [x] T39 — Actualizar `docs/historias/HU-24.md` (línea `- **Se documenta a fondo (capítulo 8).** Patrones: Decorator,
  DAO.`, como en HU-23), `docs/architecture.md` (fila ★ de HU-24 en "Patrones por historia" y párrafo en "Reseñas: qué
  deja HU-24") y `docs/requerimientos.md` § 7 (`★` y columna "Patrón" `Decorator, DAO` en la fila de HU-24), según
  `design.md` § 16 "Reapertura". Cubre: documentación ★ de R44–R57.

### Verificación

- [x] T40 — Ejecutar `./init.sh` completo en verde. Confirmar R58: `git diff develop --stat` no muestra cambios en
  `2_Backend/src/main/resources/db/`, `busqueda/dto/`, `busqueda/controller/`, `comun/` ni `3_frontend/`, y las pruebas
  listadas como "Sin cambios" en `design.md` § 14 no se tocaron. Agregar a `progress/impl_HU-24.md` el mapa
  R44–R58 → test y la lista de pruebas adaptadas. Cubre: R1–R58.
