# Exploración HU-14 — Mis reservas (★ Iterator, DAO)

> Explorador (solo lectura) · 2026-10-31 · rama `feature/HU-14-mis-reservas` (desde `develop` 1fbf140).
> Dueña: Adriana Callupe. Depende de HU-12 (`done`) y de HU-13 (`pending`, sin código).

## Preguntas

1. Backend de `reservas` (HU-12): entidad, estados y ciclo de vida, repositorios, servicios, endpoints, DTO, sesión, rutas protegidas y lo que HU-12 deja para HU-13.
2. Pagos: qué existe, qué dicen HU-13 y `modelo-datos.md`, qué necesita HU-14 y cómo avanzar sin HU-13.
3. Patrones: precedentes de Iterator y DAO, qué pide `architecture.md` y dónde encaja un DAO frente a Spring Data.
4. Contratos de `espacios` y `busqueda` para nombre, foto y dirección.
5. Migraciones de reservas frente a `modelo-datos.md`.
6. Tests de HU-12 que conviene imitar.
7. Frontend: páginas de HU-12, rutas protegidas, sesión, kit, `api/` y navegación.
8. Decisiones previas (specs HU-11 y HU-12, `history.md`) y RNF que aplican.

## Archivos analizados

- `docs/historias/HU-14.md`, `HU-13.md`, `HU-15.md`, `HU-20.md`, `HU-28.md`, `HU-01.md`
- `docs/architecture.md`, `docs/modelo-datos.md`, `docs/requerimientos.md`, `docs/frontend.md`, `docs/specs.md`, `docs/conventions.md`
- `specs/HU-12/{requirements,design,tasks}.md`, `specs/HU-11/design.md` §13, `specs/HU-10/design.md` (Decorator preparado)
- `progress/history.md` (cierre de HU-12, líneas 680-778), `progress/HU-14.md`, `progress/explore_HU-12.md`
- `feature_list.json`
- Backend: todo `reservas/**`, `espacios/service/{CatalogoEspaciosService,CatalogoEspaciosServiceImpl,FichaPublicada,EspacioPublicado,EspacioRetenido,EspacioService,FotoEspacioService}.java`, `espacios/service/puertos/*`, `cuentas/service/CuentaService.java`, `cuentas/domain/Usuario.java`, `comun/seguridad/{UsuarioAutenticado,ConfiguracionSeguridad}.java`, `comun/configuracion/ZonaHoraria.java`, `busqueda/service/{BusquedaBase,BusquedaService}.java`, `busqueda/dto/ResultadoBusquedaDTO.java`
- Migraciones: `V1_3__perfil_arrendador.sql`, `V2_2__espacio.sql`, `V3_3__reservas.sql`
- Tests: `reservas/controller/{ReservasControllerSoporte,ReservaControllerIT,ReservaConcurrenciaIT}.java`, `reservas/service/{ReservaServiceImplTest,ReservasDelEspacioImplIT}.java`, `RelojControlable.java`, `src/test/resources/config/application.properties`
- Frontend: `App.jsx`, `api/{reservas,cliente,espacios,busqueda}.js`, `sesion/{RutaProtegida,ProveedorSesion}.jsx`, `components/ui/{Tarjeta,EtiquetaEstado,Imagen}.jsx`, `pages/reservas/{ResumenReserva.jsx,textosReservas.js}`, `pages/cuentas/Panel.jsx`, `pages/busqueda/BuscarEspacios.jsx`

---

## 1. Backend del módulo `reservas` (HU-12)

Raíz: `2_Backend/src/main/java/pe/edu/ulima/espacioya/reservas/`.

### Entidad, estados y ciclo de vida

- `domain/EstadoReserva.java`: `PENDIENTE_PAGO, CONFIRMADA, EXPIRADA, CANCELADA`.
- `domain/Reserva.java` (sin asociaciones JPA, solo ids `Long`, HU-12 DT-10). Campos: `id`, `usuarioId`, `espacioId`, `estado`, `precioHoraAplicado`, `montoTotal`, `creadaEn`, `expiraEn`.
  - `pendiente(...)` (l. 56-66): nace `PENDIENTE_PAGO`, `montoTotal = precioHora × bloques` y `expiraEn = ahora + 15 min` (`MINUTOS_RETENCION_RESERVA`).
  - `esVigente(ahora)` (l. 68), `estaVencida(ahora)` (l. 72), **`estadoEn(ahora)`** (l. 76-78): una pendiente vencida se lee `EXPIRADA` aunque la fila siga en `PENDIENTE_PAGO`. **HU-14 debe usar `estadoEn(ahora)`, no `getEstado()`.** Así lo dicen `architecture.md:239` y el aviso a Adriana en `history.md:757`.
  - `segundosRestantes(ahora)` (l. 80) y `expirar(ahora)` (l. 84, lanza si no está vencida).
  - **No existen** `confirmar()` ni `cancelar()`: los agregan HU-13 y HU-15.
- `domain/ReservaBloque.java`: una fila por hora (`reservaId`, `espacioId`, `fecha`, `horaInicio`, `activo`). Al liberar se pone `activo = false` y la fila se conserva "porque HU-14 muestra el horario de las reservas pasadas" (javadoc l. 12-14).
- `domain/SeleccionBloques.java`: las horas son consecutivas, del mismo día y sin cruzar la medianoche (HU-12 S6). Por eso cada reserva tiene **una sola fecha** y un rango `[min hora, max hora + 1)`.
- Ciclo de vida (`architecture.md:321-323`, RN-03): `PENDIENTE_PAGO` → `CONFIRMADA` (pago) o `EXPIRADA`; `CONFIRMADA` → `CANCELADA`. `PENDIENTE_PAGO → CANCELADA` no existe (HU-12 S7).
- La expiración tiene tres capas (D2): las lecturas comparan con *ahora*; `service/ExpiracionReservas.expirarVencidas()` corre con `ProgramacionExpiracion` cada `PT1M` (apagado en los tests) y audita `RESERVA_EXPIRADA`; y `liberarVencidas` actúa al crear.

### Repositorios

- `repository/ReservaRepository.java`:
  - `findByIdAndUsuarioId(id, usuarioId)` (l. 20): el único método filtrado por dueño;
  - `contarVigentes` (l. 22-26), `findIdsVencidas` (l. 28-31), `findParaActualizar` (lock, l. 33-35) y `findVencidasQueRetienen` (l. 37-45).
  - **No hay ningún método que liste las reservas de un usuario ni de un espacio.**
- `repository/ReservaBloqueRepository.java`: `findByReservaIdOrderByHoraInicioAsc` (l. 17), `desactivarDeReserva` (l. 21-23), `bloquesActivos` y `bloquesActivosDelDia` (proyección `BloqueActivo`, l. 25-43).
- `repository/BloqueActivo.java`: un record de proyección JPQL `select new ...`. Es el precedente de proyección por constructor en este módulo.

### Servicios y endpoints

- `service/ReservaService.java` es la interfaz pública con dos métodos:
  - `crear(usuario, datos, ip)`;
  - `consultar(usuario, id)`.
- `service/ReservaServiceImpl.java`:
  - `crear` (l. 82-100): solo el rol `USUARIO` (l. 84, DT-8, que se comprueba en el servicio y no con `hasRole`). Usa `TransactionTemplate` y el orden de bloqueos `usuario → espacio → reserva`.
  - `consultar` (l. 102-115), con `@Transactional(readOnly = true)`, funciona así:
    - valida el id con `idValido` (texto de 1 a 19 dígitos y mayor que 0, l. 180-190);
    - lo busca con `findByIdAndUsuarioId`; un id inexistente o ajeno da el mismo 404;
    - lee las filas de `reserva_bloque`, **activas o no**;
    - toma el nombre con `catalogo.nombres(List.of(espacioId))`;
    - arma el DTO con `fecha`, `horaInicio = primera`, `horaFin = última + 1`.
    - **No comprueba el rol**: cualquier dueño de una reserva la ve.
  - `aDto(...)` (l. 149-154) usa `estadoEn(ahora).name()` y `segundosRestantes(ahora)`.
  - El reloj se inyecta como `Clock` (beans `ConfiguracionReloj`). La fecha de Lima sale de `ZonaHoraria.hoy(reloj)` y `ZonaHoraria.ahora(reloj)` (`comun/configuracion/ZonaHoraria.java:15-24`, `America/Lima`).
- `service/ReservasDelEspacioImpl.java` implementa el puerto `espacios.ReservasDelEspacio`. No sirve para listar porque devuelve bloques sin reserva ni usuario.
- `controller/ReservaController.java`, bajo `@RequestMapping("/api/reservas")`:
  - `POST` (l. 32-37);
  - `GET /{id}` con `@PathVariable String id` (l. 39-43).
  - Un `GET /api/reservas` sin id **está libre**.
  - Para el arrendador, el patrón de la casa es `/api/arrendadores/actual/...` (HU-05, HU-07 DT-7): la identidad sale del token.

### DTO

- `dto/ReservaDTO.java`: `(id, espacioId, espacioNombre, estado, fecha, horaInicio, horaFin, precioHora, montoTotal, expiraEn, segundosRestantes)`.
  - El javadoc dice "sin `usuarioId` ni datos del arrendador" (HU-12 R41).
  - Ya cubre casi todo CA1 (espacio, fecha, horario, monto y estado). HU-12 T21 y `history.md:757` sugieren que HU-14 lo reutilice.
  - Para una lista, `expiraEn` y `segundosRestantes` sobran salvo en las pendientes.
- `dto/CrearReservaDTO.java` no interesa a HU-14.

### Sesión (HU-03) y protección de rutas

- `comun/seguridad/UsuarioAutenticado.java` es un `record(usuarioId, sesionId, rol)`. El controlador lo recibe con `@AuthenticationPrincipal UsuarioAutenticado usuario`, poblado por `FiltroAutenticacionSesion` a partir del Bearer.
- En `comun/seguridad/ConfiguracionSeguridad.java:63` está `.anyRequest().authenticated()`, de modo que `/api/reservas/**` y `/api/arrendadores/actual/**` ya exigen sesión sin tocar nada. El 401 lo da `PuntoEntradaNoAutenticado`.
- El rol se valida en el servicio con `AccesoDenegadoException` (403), por ejemplo `ReservaServiceImpl:84` y `CuentaService.habilitacionArrendador` (`CuentaService.java:20-24`, que lanza 403 si el rol no es `ARRENDADOR`).

### Qué espera HU-12 de HU-13 (puntos de extensión)

- `specs/HU-12/design.md` §12 (l. 341-348) y `architecture.md:259-263` dicen que `reservas` implementará `pagos.ReservasPorCobrar`, que define HU-13 y que figura en la tabla de puertos (`architecture.md:132`).
  - **Obtener:** el id, el usuario dueño, `montoTotal`, `expiraEn` y la vigencia con `esVigente(ahora)`.
  - **Confirmar:** un `Reserva.confirmar()` nuevo y un método que tome solo el bloqueo del espacio y luego `findParaActualizar`, revalidando la vigencia.
- El estado que queda pendiente de pago es `PENDIENTE_PAGO`, vigente 15 minutos. HU-13 lo pasará a `CONFIRMADA`.
- `ResumenReserva.jsx` (`/reservas/:id`) es la pantalla donde "HU-13 pondrá 'Pagar'" (`specs/HU-12/design.md:305`, `frontend.md:253`).
- **Hoy ninguna reserva llega a `CONFIRMADA` por la aplicación**: solo los tests la insertan con `JdbcTemplate`.

## 2. Pagos

- **No existe nada de `pagos`**:
  - no hay paquete `pagos/`;
  - no hay tabla `pago` ni migración;
  - no hay enum de estado de pago ni `ReservasPorCobrar`.
  - (`find` y `grep` sobre `2_Backend/src` no encuentran nada.) HU-12 S2 lo dejó así a propósito.
- `docs/historias/HU-13.md`:
  - CA1 confirma la reserva tras el pago y envía un correo con el comprobante;
  - CA2 envía un correo al arrendador;
  - CA3 cubre el rechazo y el reintento mientras la reserva siga vigente;
  - CA4 dice que el checkout expira con la reserva.
  - En STRIDE: monto calculado en el servidor, webhook firmado e idempotente, 3 intentos fallidos cada 30 minutos y registro de cada transacción.
- `docs/modelo-datos.md:27`: `pago` (`id, reserva_id, id_transaccion_pasarela (único), monto, estado (PAGADO, RECHAZADO, REEMBOLSADO, REEMBOLSO_FALLIDO), ultimos4, creado_en`), de HU-13 y HU-28. La relación es `RESERVA ||--o{ PAGO` (l. 82): una reserva puede tener varios pagos, porque los rechazados se reintentan. El comprobante es un PDF de HU-28 CA2. HU-13 solo lo envía por correo.
- `docs/frontend.md:141` define los colores del estado de pago: `PAGADO` éxito, `RECHAZADO` y `REEMBOLSO_FALLIDO` error, `REEMBOLSADO` neutro.
- **Qué necesita mostrar HU-14 según sus CA:**
  - CA1 pide "monto y estado". El monto ya está en `reserva.monto_total` y el estado es el **de la reserva** (`PENDIENTE_PAGO`, `CONFIRMADA`, `EXPIRADA`, `CANCELADA`). Ningún CA pide el estado del pago, el comprobante ni `ultimos4`, que son de HU-28 (historial de pagos).
  - CA2 muestra solo las `CONFIRMADA`, y "confirmada" ya implica "pagada" (RN-03).
  - El STRIDE (I) prohíbe que el arrendador vea "datos de pago del usuario".
- **Cómo avanzar sin HU-13:**
  - HU-14 lee solo `reserva` y `reserva_bloque`. "Confirmada" es `estado = 'CONFIRMADA'`, que es lo que escribirá HU-13, sin depender de `pago`. Los tests insertan las `CONFIRMADA` con `JdbcTemplate`, como ya hacen `ReservaControllerIT` (R8, R11, R18) y `ReservasDelEspacioImplIT`.
  - El único acoplamiento con HU-13 es el nombre del estado, que ya está fijado en el enum, el `CHECK` y `EtiquetaEstado`.
  - Si se quisiera mostrar algo del pago, haría falta un puerto `pagos → reservas` o leer `PagoService`, que no existen. Conviene no hacerlo.

## 3. Patrones

- **Precedentes en el repo:**
  - No hay ninguna clase que implemente `Iterable` o `Iterator`, ni un DAO propio. Todo el acceso a datos son interfaces Spring Data (`JpaRepository`) y no hay `EntityManager` ni `JdbcTemplate` en `src/main` (verificado con `grep`).
  - Ninguna historia ★ está hecha todavía (HU-13, 15, 16, 23 y 25 siguen `pending`).
  - El único antecedente de "dejar preparado un patrón" es HU-10, que creó `busqueda/service/BusquedaEspacios` y `BusquedaBase` como interfaz envolvible para el Decorator de HU-23 (`specs/HU-10/design.md:7, 71, 307`).
  - Las proyecciones por constructor JPQL (`BloqueActivo`) y los métodos con dueño en la firma (`findByIdAndUsuarioId`, `EspacioRepository.findByIdAndArrendadorId`) son la base natural del DAO.
- **`docs/architecture.md`:**
  - l. 33: "`repository` — acceso a datos con Spring Data JPA (DAO)".
  - l. 81-84: "DAO/Repository en toda la persistencia".
  - l. 340-341: "Las historias marcadas ★ se documentan a fondo en el informe; su `design.md` debe aplicar el patrón y mostrarlo en el diagrama de clases y de secuencia".
  - l. 346, HU-14 ★ Iterator y DAO: "`reservas`: recorrer reservas próximas y pasadas sin exponer la colección; consultas siempre filtradas por el usuario de la sesión".
  - l. 351: "Todas: DAO / Repository — un repositorio por agregado".
  - `docs/specs.md:123`: "Si la historia es ★: cómo se aplica su patrón".
- **Dónde encajaría el DAO.** Como todos usan Spring Data, el valor ★ de HU-14 está en hacer **explícito** el DAO. Hay dos caminos:
  - **A.** Documentar `ReservaRepository` como el DAO y agregarle consultas cuya firma **obliga** a pasar el dueño (`usuarioId` o los ids de los espacios del arrendador). La lista y el horario salen de un `select new ...` con `min(fecha)`, `min(hora)` y `max(hora)` agrupado por reserva, que evita el N+1 de `consultar`. Es lo más barato, pero el patrón queda poco visible.
  - **B.** Crear una interfaz `repository/ReservaDAO` (o `ConsultaReservasDAO`) con métodos como `delUsuario(usuarioId, …)` y `confirmadasDeEspacios(espacioIds, …)`, y una implementación (`ReservaDAOJpa`) que use `ReservaRepository` o `EntityManager` por dentro. Así el servicio depende de la abstracción y el diagrama muestra el DAO. Sería el primer DAO propio del repo, así que convendría anotarlo en `architecture.md`.
- **Dónde encajaría el Iterator.** "Sin exponer la colección" apunta a:
  - un objeto de dominio o servicio, por ejemplo `HistorialReservas`, que **implemente `Iterable<…>`** y entregue dos recorridos, `proximas()` y `pasadas()`, cada uno con su `Iterator` que filtra por el instante de corte (*ahora* en Lima) y por el orden;
  - o un iterador que pida **páginas al DAO bajo demanda** (`hasNext` y `next` cargan la siguiente página filtrada por usuario). Esta opción une los dos patrones y sirve si se pagina.
  - Al controlador o al DTO llegaría la lista ya recorrida (`List.copyOf`, como en `ResultadoBusquedaDTO`).

## 4. Datos de espacios y búsqueda

`espacios/service/CatalogoEspaciosService.java` es una interfaz pública sin sesión. Su javadoc (l. 8-10) ya nombra a `reservas` (HU-12, HU-14). Sus métodos:

| Método | ¿Filtra visibilidad? | Uso para HU-14 |
|---|---|---|
| `nombres(Collection<Long>) → Map<Long,String>` (l. 38-42) | **No** (HU-12 S11) | Nombre del espacio de cada reserva, en una sola consulta (`findAllById`, `Impl` l. 101-109). Pensado para HU-14 |
| `fotosPrincipales(Collection<Long>) → Map<Long,String>` (l. 26-27) | No filtra al leer, pero **la URL `/api/fotos/<UUID>` solo se sirve** si el espacio está `PUBLICADO` y el arrendador `ACTIVA`, o a su dueño con token (`FotoEspacioService.ver`, l. 115-136) | Para un espacio pausado o un arrendador suspendido, el `<img>` del usuario da 404. `Imagen.jsx` no tiene `onError` y mostraría una imagen rota |
| `publicado(Long) → Optional<FichaPublicada>` (l. 29-33) | **Sí** | Trae `direccion`, `zonaNombre` y `fotos`, pero queda vacío si el espacio dejó de ser visible. Además es de uno en uno (N+1) |
| `esVisible(Long)` | Sí | Permitiría mostrar un enlace "Ver espacio" solo si el espacio sigue visible |

- **No hay** ningún método que dé la **dirección sin filtro**, ni otro que dé **los espacios de un arrendador** a otro módulo. `EspacioService.listar(usuario)` (`EspacioService.java:85-95`) es una clase concreta pensada para el controlador de `espacios`, no una interfaz entre módulos (principio 5). Para CA2 haría falta un método nuevo en `CatalogoEspaciosService`, por ejemplo `delArrendador(arrendadorId) → Map<Long,String>` con id y nombre, sin filtro de visibilidad, implementado con `EspacioRepository.findByArrendadorIdOrderByCreadoEnDesc` (`EspacioRepository.java:21`).
- Hay una alternativa: un JPQL en `reservas` que una `Reserva` con `Espacio` por `arrendadorId`. Pero así `reservas` leería la entidad de `espacios`, cosa que `architecture.md` "Qué NO hacer" prohíbe ("ni al repositorio de otro módulo"). No se recomienda.
- **Nombre del usuario (CA2):** `CuentaService` (l. 86-109) no tiene ningún método que devuelva nombres de usuarios. Haría falta uno nuevo, por ejemplo `nombres(Collection<Long>) → Map<Long,String>`, que devuelva **solo** el nombre (RNF-SEG-16) y nunca el correo ni el teléfono.
  - ⚠ **Hueco grande:** las cuentas `USUARIO` **no tienen nombre**. `Usuario.registrar(...)` (`Usuario.java:56-68`) no lo pide, `V1_3__perfil_arrendador.sql:2` dice "nombre y telefono son anulables: los USUARIO de HU-01 no los tienen", y solo HU-20 (Release 2, de Adriana) permite editarlo (RF-A-20).
  - Hoy, por lo tanto, todo usuario que reserva tiene `nombre = NULL`. Ver la decisión P1.
- **`specs/HU-11/design.md` §13** (l. 413-431), el contrato para HU-12 y Release 2:
  - la ficha pública es `/espacios/:id?fecha=…`;
  - los DTO de HU-11 se pueden **ampliar**, pero no cambiar ni quitar campos;
  - `obtenerEspacio` y `consultarBloquesLibres` no cambian de firma.
  - Para HU-14 sirve como destino del enlace "Ver espacio" desde cada reserva (como hace `ResumenReserva`, "Volver al espacio"). Si el espacio ya no es visible, la ficha da el 404 de HU-11.

## 5. Migraciones Flyway

- `V3_3__reservas.sql`:
  - `reserva` (l. 3-15): `id`, `usuario_id`, `espacio_id`, `estado VARCHAR(20)`, `precio_hora_aplicado NUMERIC(8,2)`, `monto_total NUMERIC(10,2)`, `creada_en` y `expira_en TIMESTAMPTZ`, con los `CHECK` de estado, montos y `expira > creada`;
  - `reserva_bloque` (l. 19-27): `reserva_id` (en cascada), `espacio_id`, `fecha DATE`, `hora_inicio SMALLINT 0-23` y `activo`;
  - índices: `ix_reserva_usuario_estado (usuario_id, estado)` (l. 16), `ix_reserva_vencimiento` parcial (l. 17), `uq_reserva_bloque_activo` parcial (l. 29) e `ix_reserva_bloque_reserva (reserva_id)` (l. 30).
- Frente a `docs/modelo-datos.md:25-26, 42-43` **coincide exactamente**, incluida la nota de que `cancelada_por` y `cancelada_en` las agrega HU-15.
- **Para HU-14:**
  - La lista del usuario se apoya en `ix_reserva_usuario_estado`, que sirve aunque no se filtre por estado, porque `usuario_id` es el prefijo.
  - La lista del arrendador filtra por `espacio_id IN (...)` y `estado = 'CONFIRMADA'`. **No hay índice en `reserva(espacio_id)`**. Para el volumen de UAT no hace falta; si se quisiera, sería una migración `V4_n__...` (sprint 4, `conventions.md:71-72`).
  - La fecha y las horas viven solo en `reserva_bloque`, así que ordenar o separar en próximas y pasadas exige unir las tablas o agregar. No hace falta columna nueva: el horario sale de `min` y `max` sobre las filas, activas o no.
- No existe tabla `pago`. HU-14 no necesita migración salvo que se decida agregar el índice.

## 6. Tests de HU-12 que conviene imitar

- **IT con Testcontainers**: `reservas/controller/ReservasControllerSoporte.java`.
  - `@SpringBootTest @AutoConfigureMockMvc @Import({TestcontainersConfiguration, Dobles})` con `RelojControlable` `@Primary`, fijado en `2026-10-28T15:00:00Z` (miércoles a las 10:00 de Lima, l. 52-101).
  - `@BeforeEach` crea un arrendador (con nombre "Ana Reservada"), un usuario sin nombre y un espacio `PUBLICADO` con su franja. `@AfterEach` borra `reserva`, `foto_espacio`, `espacio`, `sesion`, `auditoria`, `perfil_arrendador` y `usuario` en orden por las FK (l. 103-127).
  - Cada test usa una IP propia: `10.14.x.x` es de HU-12 y HU-14 debería tomar otro rango, por ejemplo `10.15` (l. 129-133).
  - Ayudas reutilizables:
    - `token(correo)` inicia sesión con `POST /api/sesiones` (l. 210-221);
    - `con(token, solicitud)` agrega el Bearer (l. 203-208);
    - `reservaEnBd(usuario, espacio, estado, expiraEn, activo, horas...)`, `filaReserva` y `filaBloque` insertan con `JdbcTemplate` **cualquier estado**, también `CONFIRMADA` (l. 167-186). Ojo: `reservaEnBd` usa siempre el campo `fecha`, así que para las pasadas conviene llamar a `filaBloque` con otra fecha;
    - `dentroDe` y `haceMinutos`, junto con `reloj.avanzar(...)`, sirven para el corte próximas/pasadas.
  - Al ser `abstract` y de paquete, HU-14 puede extenderlo si sus IT viven en `reservas/controller`, o copiar su forma.
- **`ReservaControllerIT`**:
  - `esNoEncontrado` y `esErrorSinCampo` (l. 45-60);
  - claves exactas con `mapa(texto(r)).keySet()).containsExactlyInAnyOrder(...)` (l. 89-91 y R37 en l. 581), que es justo lo que pide la minimización de datos de CA2 (STRIDE I);
  - sin sesión da 401 (l. 546-559, R35), los roles distintos dan 403 (l. 561-575, R36) y una reserva ajena da 404 (l. 631, R38);
  - una vencida sin procesar se lee `EXPIRADA` sin escribir (l. 645, R39);
  - el nombre de un espacio pausado o de un arrendador suspendido sigue apareciendo (l. 615, R37).
- **`ReservasDelEspacioImplIT`**: `@SpringBootTest @Import(TestcontainersConfiguration)` sin MockMvc, para probar consultas del repositorio o el DAO contra PostgreSQL real con vigente, confirmada, vencida, expirada y de otro espacio. Es el modelo para el IT del DAO, sobre todo el filtro por usuario.
- **Unitarias con Mockito**: `ReservaServiceImplTest` (`@ExtendWith(MockitoExtension)`, `RelojControlable` o `Clock` fijo, `ArgumentCaptor`, `inOrder`) y `ReservasDelEspacioImplTest` ("una sola consulta; lista vacía sin consultar"), que es el modelo para probar que no hay N+1. `ReservaTest` sirve para los métodos de dominio en sus límites (`estadoEn`).
- Configuración de prueba: `src/test/resources/config/application.properties` apaga el proceso programado (`espacioya.reservas.expiracion.activa=false`).
- Las rutas autenticadas siguen el mismo esquema: obtener un token por `POST /api/sesiones`, mandar `Authorization: Bearer` y probar sin token (401), con otro rol (403) y con otro usuario (404 o lista vacía, sin filtrar datos ajenos).

## 7. Frontend (`3_frontend/src/`)

- **Páginas de HU-12:**
  - `pages/reservas/ResumenReserva.jsx` (`/reservas/:id`): `Tarjeta` con `titulo = espacioNombre`, `EtiquetaEstado`, una `<dl>` con Fecha, Horario (`formatoHora(horaInicio)–formatoHora(horaFin)`), Precio por hora y Monto total, y `CuentaRegresiva` si la reserva está `PENDIENTE_PAGO`. El enlace "Volver al espacio" va en `acciones`. Usa el patrón `activo` en el efecto y descarta la respuesta si cambió el id.
  - `pages/reservas/textosReservas.js` tiene `formatoFecha` (formato es-PE leído en UTC para no mover el día), `CARGANDO`, `ENLACE_BUSCAR`, etc.
  - `formatoHora` y `formatoPrecio` salen de `pages/espacios/textosEspacios.js`.
- **Rutas protegidas y sesión:**
  - `App.jsx:123-131` envuelve `/reservas/:id` en `<RutaProtegida>`. Las del arrendador viven bajo `/panel/...` (`/panel/espacios`, l. 154-186).
  - `sesion/RutaProtegida.jsx` redirige a `/iniciar-sesion` sin `retorno` (`frontend.md:248`, "`RutaProtegida` aún no guarda el retorno"). Abrir "Mis reservas" sin sesión llevaría al login y luego a `/panel`.
  - `ProveedorSesion.jsx` guarda `{ rol, aviso }`, que se lee con `useSesion()`, y maneja el 401 con `retorno`.
- **Kit (`components/ui/`):**
  - `Tarjeta`: `titulo`, `children`, `acciones`. `docs/frontend.md:186` ya la reserva para la "reserva en 'Mis reservas'".
  - `EtiquetaEstado`: ya mapea los 4 estados de la reserva (l. 20-24).
  - `Imagen`: con `src` nulo muestra "Sin foto", pero no maneja `onError`.
  - También hay `Alerta`, `Boton`, `Modal` (HU-15 lo usará para cancelar), `CuentaRegresiva`, `Galeria` y `SelectorBloques`.
  - **No hay componente de paginación ni de pestañas** en el kit. `pages/busqueda/BuscarEspacios.jsx` (l. 25-32, 86-92, 136-142) pagina por su cuenta con `?pagina=` en la URL y `hayMas`, sin total.
- **`api/reservas.js`**: solo `crearReserva` y `obtenerReserva(id)`. HU-14 agregaría, por ejemplo, `listarMisReservas()` y `listarReservasDeMisEspacios()`. `api/cliente.js` expone `peticion(ruta, { metodo, cuerpo, autenticada })`.
- **Navegación hacia "Mis reservas":**
  - No existe. `pages/cuentas/Panel.jsx:9` muestra para `USUARIO` el texto "Aquí verás tus reservas." sin enlace. Para `ARRENDADOR` hay enlaces a "Mis espacios" y "Verificar mi identidad" (l. 38-47).
  - El encabezado de `App.jsx` (l. 89-97) solo tiene "Mi panel".
  - `docs/frontend.md:221` da `/mis-reservas` como ejemplo de ruta.
- La pantalla se verifica con lint, build y la caja negra en UAT. **No hay tests de frontend** (`package.json` solo tiene `lint`), según `frontend.md:14-17`.

## 8. Contexto, decisiones previas y RNF

- **HU-12:**
  - S11 y DT-10: `nombres(ids)` sin filtro y `reserva_bloque` conservada para HU-14;
  - D1: solo `USUARIO` reserva;
  - D3: `GET /{id}` solo para el dueño;
  - el aviso de `history.md:757`: reutilizar `nombres` y `ReservaDTO`, y leer el estado con `estadoEn(ahora)`.
  - Las observaciones abiertas de HU-12 incluyen el `RutaProtegida` sin retorno (`history.md:765-768`).
- **HU-11:** el 404 indistinguible (DT-6), ampliar sin romper los DTO (§13) y el patrón `activo` en los efectos.
- **HU-15** (Kevin Chan) depende de HU-14: su CA2 dice "cancelo desde 'Mis reservas'". La tarjeta debe dejar sitio en `acciones` para "Cancelar" en las `CONFIRMADA`, tanto para el usuario como para el arrendador (HU-15 STRIDE E). HU-24 (reseñas de confirmadas pasadas, RN-13) y HU-26 (reclamos) también dependen de HU-14 y cuelgan de las reservas pasadas.
- **`docs/requerimientos.md`:**
  - RF-D-05 y RF-D-06 (l. 96-97): CA1 y CA2.
  - RNF-SEG-01 (l. 146): la identidad sale de la sesión.
  - RNF-SEG-16 (l. 161): "el arrendador no ve correo, teléfono ni datos de pago del usuario".
  - RNF-SEG-19 (l. 164): se valida la propiedad antes de cada consulta.
  - RN-03 (l. 212): los estados de la reserva.
  - **RNF-USA-01 (l. 192): paginar de 20 en 20 solo se exige para la búsqueda.** No hay regla de paginación ni de orden para "Mis reservas", así que hay que decidirla.
  - RNF-SEG-12 (auditoría): solo cubre acciones sensibles, y una consulta no lo es. Ninguna historia audita lecturas, salvo los accesos del admin.
  - RNF-SEG-17 no fija límite para estas consultas.
  - RN-17 (HU-22) y RN-16 (HU-21) usan "reservas futuras", y les vendría bien la misma definición de "próxima".

---

## Huecos / decisiones abiertas

### Producto (decide la dueña, Adriana Callupe)

- **P1. Nombre del usuario en CA2.** Las cuentas `USUARIO` no tienen nombre hasta HU-20 (Release 2).
  - A) Mostrar el nombre si existe y, si no, un texto genérico ("Usuario de EspacioYa").
  - B) Agregar un nombre obligatorio al registro de usuario. Cambia HU-01 y HU-03, que son de otro dueño.
  - C) Derivarlo del correo. **Prohibido** por RNF-SEG-16.
  - Recomendación: **A**, y anotar que HU-20 lo completa.
- **P2. Qué es "próxima" y qué es "pasada".** Puede cortarse por el inicio o por el fin del horario frente a *ahora* en Lima. ¿Una reserva en curso es próxima? ¿Dónde van `EXPIRADA` y `CANCELADA` con fecha futura: en próximas con su etiqueta, o siempre en pasadas como "finalizadas"?
  - Recomendación: próxima si `fecha + horaFin > ahora` y el estado es `PENDIENTE_PAGO` vigente o `CONFIRMADA`; todo lo demás va a pasadas.
- **P3. Orden y volumen.** Las próximas, ascendentes y las pasadas, descendentes. ¿Se pagina? Si sí, ¿de a cuánto y con `hayMas` sin total, como HU-10? ¿O se limita el historial, por ejemplo a las últimas N pasadas?
- **P4. Qué estados ve el usuario.** ¿Se muestran las `EXPIRADA`? Son ruido, porque nunca se pagaron, pero ocultarlas también oculta que ocurrieron. ¿Y las `PENDIENTE_PAGO` vigentes con su contador o con un enlace a `/reservas/:id`, donde HU-13 pondrá "Pagar"?
- **P5. Alcance de CA2.**
  - Además de nombre y horario, ¿el arrendador ve fecha, espacio y **monto**? RNF-SEG-16 prohíbe "datos de pago del usuario". El monto es de la reserva y no del medio de pago, pero conviene que la dueña lo diga.
  - ¿Se separan también en próximas y pasadas, o se muestran solo las próximas?
  - ¿Se filtra por espacio?
- **P6. Foto y dirección.** Ningún CA las pide: CA1 solo dice "espacio". Mostrarlas obliga a métodos nuevos en `espacios`, y la foto de un espacio pausado da 404 al usuario (§4).
  - Recomendación: solo el nombre y un enlace "Ver espacio".
- **P7. Rutas y navegación.**
  - Usuario: `/mis-reservas` (ejemplo de `frontend.md`) o `/panel/reservas`.
  - Arrendador: `/panel/reservas` o `/panel/espacios/reservas`.
  - Enlaces desde `Panel.jsx` o desde el encabezado.
  - ¿Se arregla aquí el `retorno` de `RutaProtegida`? Hoy está fuera de alcance y es código de HU-03 (Jorge Quispe).
- **P8. Roles.**
  - ¿Un `ARRENDADOR` que abre `GET` de "Mis reservas" recibe 403 o una lista vacía? Hoy los arrendadores no pueden reservar (HU-12 D1).
  - ¿Un `USUARIO` en "Reservas de mis espacios" recibe 403?
  - ¿Se le exige al arrendador `habilitacionArrendador` (correo verificado) o basta el rol?

### Técnicas

- **T1. Endpoints:**
  - usuario: `GET /api/reservas`, libre en el controlador actual;
  - arrendador: `GET /api/arrendadores/actual/reservas`, con el patrón `/actual`;
  - se pueden agregar parámetros `?pagina=` o `?grupo=proximas|pasadas` si P3 lo pide;
  - no hace falta tocar `ConfiguracionSeguridad`.
- **T2. DTO:**
  - ¿Reutilizar `ReservaDTO` (11 campos, incluidos `expiraEn` y `segundosRestantes`) o crear uno de lista (`ReservaResumenDTO`)?
  - Para el arrendador hace falta un DTO propio (`ReservaDeMiEspacioDTO`) **sin** `usuarioId`, correo, teléfono ni pago, con claves exactas probadas en IT.
  - ¿El listado agrupado viene como `{ proximas: [...], pasadas: [...] }` (el servidor decide el corte, principio 6) o como una lista plana? Recomendación: agrupado en el servidor, porque el frontend no debe calcular estados ni cortes.
- **T3. Forma del DAO** (§3): A, ampliar `ReservaRepository` y documentarlo como DAO, o B, una interfaz `ReservaDAO` con su implementación.
  - Ninguna de las dos tiene precedente. B es más visible para el capítulo 8 y conviene anotarla en `architecture.md`.
  - En los dos casos, toda firma exige el dueño y una sola consulta agregada trae fecha y horas, sin N+1.
- **T4. Forma del Iterator:**
  - un `Iterable` de dominio con dos recorridos;
  - o un iterador paginado sobre el DAO.
  - Hay que decidir el instante de corte (`Clock` y `ZonaHoraria.ahora`) y dónde vive (`domain` o `service`). Debe aparecer en los diagramas de clases y de secuencia (`architecture.md:340-341`).
- **T5. Contratos nuevos entre módulos:**
  - `CatalogoEspaciosService.delArrendador(arrendadorId)` (o el nombre que se elija) en `espacios`;
  - `CuentaService.nombres(usuarioIds)` en `cuentas`, que devuelve solo el nombre y no exige rol.
  - Las dos son direcciones permitidas (`reservas → espacios`, `reservas → cuentas`) y no hacen falta puertos. Hay que avisar a sus dueños (Sebastian Garate por `espacios`, Jorge Quispe o quien custodie `cuentas`) y actualizar `architecture.md`.
- **T6. El estado se lee siempre con `estadoEn(ahora)`.** En una consulta SQL que filtre o agrupe por estado, una vencida sin procesar sigue `PENDIENTE_PAGO` en la fila. El filtro tiene que reproducir `expira_en > :ahora` (como `contarVigentes`) o reclasificar en Java.
- **T7. Índice `reserva(espacio_id, estado)`** para CA2. Es opcional y llevaría una migración `V4_n`.
- **T8. Tests:**
  - IT nuevas con el rango de IP `10.15.x.x`;
  - semillas de reservas pasadas con `filaBloque` y otra fecha;
  - IT del DAO al estilo de `ReservasDelEspacioImplIT`, que pruebe que nunca devuelve filas de otro usuario ni de espacios ajenos;
  - unitarias del Iterator (corte en el límite, orden y lista vacía).
- **T9. Frontend:**
  - funciones en `api/reservas.js`;
  - `pages/reservas/MisReservas.jsx` y `ReservasDeMisEspacios.jsx` con `Tarjeta` y `EtiquetaEstado`;
  - si P3 pide paginación, decidir si se extrae un `Paginacion` al kit o se copia el patrón de `BuscarEspacios`. Agregar al kit exige actualizar `frontend.md`.
  - Si se muestran fotos, darle a `Imagen` un `onError` que caiga en "Sin foto".

### Dependen de HU-13 (Eduardo Saldaña)

- **H1. `CONFIRMADA` no se alcanza por la aplicación.** Hasta que HU-13 confirme reservas:
  - CA2 solo se prueba con datos insertados por `JdbcTemplate`;
  - la caja negra en UAT y el video del release no pueden mostrar reservas confirmadas de punta a punta.
  - Hay que decidir con la dueña si se usa una semilla de demo o se espera a HU-13.
- **H2. El nombre `CONFIRMADA` ya está fijado** (enum, `CHECK` y `EtiquetaEstado`). Si HU-13 agrega un estado intermedio (por ejemplo, un pago en proceso) o cambia la transición, HU-14 debe ajustar su clasificación. Conviene que HU-13 lo confirme.
- **H3. Acción "Pagar" desde "Mis reservas".** HU-13 la pondrá en `/reservas/:id`. HU-14 solo debería enlazar ahí las `PENDIENTE_PAGO` vigentes, sin construir el checkout.
- **H4. Datos de pago en la lista** (estado del pago, comprobante, `ultimos4`). No los pide ningún CA, son de HU-28 y requieren `pago` y `PagoService` de HU-13. Recomendación: quedan fuera de HU-14.
- **H5. Orden de bloqueos y concurrencia.** HU-14 solo lee (`readOnly`), así que no toma bloqueos ni compite con el webhook ni con el proceso. Basta con que HU-13 no cambie la semántica de `estado` y `expira_en`.
- **H6. Cuándo se cierra `done`.** `feature_list.json` declara HU-13 como dependencia. Hay que decidir si HU-14 puede llegar a `done` con HU-13 `pending`, o si queda a la espera de la integración (la caja negra de CA2 con una confirmación real).
