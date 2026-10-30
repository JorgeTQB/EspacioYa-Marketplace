# Exploración HU-10 · Búsqueda por tipo, zona y fecha

Fecha: 2026-10-29 · Explorador (solo lectura; único archivo escrito: este).

## Pregunta

¿Qué existe ya en backend (`espacios`, disponibilidad, suspensión, identidad, `comun`), migraciones,
frontend y docs que HU-10 deba reutilizar o respetar para la búsqueda pública por tipo, zona y fecha?

## Archivos analizados

Backend (`2_Backend/src/main/java/pe/edu/ulima/espacioya/`):
- `espacios/domain/{Espacio,EstadoEspacio,TipoEspacio,Zona,FotoEspacio,CalculoDisponibilidad,VentanaReserva}.java`
- `espacios/repository/{EspacioRepository,FotoEspacioRepository,HorarioSemanalRepository,BloqueoFechaRepository}.java`
- `espacios/service/{EspacioService,FotoEspacioService,DisponibilidadService,DisponibilidadServiceImpl,CalendarioEspacioService}.java`, `espacios/service/puertos/ReservasDelEspacio.java`
- `espacios/controller/{EspacioController,FotoPublicaController,ZonaController,CalendarioEspacioController}.java`
- `cuentas/service/{CuentaService,CuentaServiceImpl}.java`, `cuentas/domain/{EstadoUsuario,PerfilArrendador}.java`, `cuentas/repository/UsuarioRepository.java`
- `comun/seguridad/ConfiguracionSeguridad.java`, `comun/limite/{FiltroLimiteIntentos,LimitadorIntentos,ReglaLimite}.java`, `cuentas/controller/ReglasLimiteCuentas.java`, `comun/error/{ManejadorGlobalErrores,ErrorDTO}.java`, `comun/configuracion/ZonaHoraria.java`
- Migraciones `2_Backend/src/main/resources/db/migration/*.sql`

Frontend: `3_frontend/src/App.jsx`, `src/api/{cliente,espacios}.js`, `src/components/ui/*`, `src/pages/espacios/FotoDelEspacio.jsx`.

Docs/specs: `docs/historias/HU-10.md`, `docs/requerimientos.md`, `docs/architecture.md`, `docs/modelo-datos.md`,
`docs/frontend.md`, `specs/HU-07/*`, `specs/HU-08/*`, `specs/HU-09/requirements.md`.

---

## 1. Módulo `espacios` (HU-07)

**Entidad `Espacio`** (`espacios/domain/Espacio.java`):
- Campos: `id`, `arrendadorId` (L34), `nombre` (L37), `tipo: TipoEspacio` (`@Enumerated STRING`, L39-41),
  `direccion`, `zona: String` (código, L46-47), `capacidad`, `precioHora: BigDecimal` (NUMERIC(8,2), L52-53),
  `descripcion`, `estado: EstadoEspacio` (L58-60), `creadoEn`, `actualizadoEn`.
- `EstadoEspacio` (enum): `BORRADOR, PUBLICADO, PAUSADO, BAJA, OCULTO`. "Visible" = `PUBLICADO` (RN-15:
  no pausados ni ocultos).
- `TipoEspacio` (enum): `CANCHA, ESTADIO, AUDITORIO`.
- **Zona**: tabla catálogo `zona(codigo PK, nombre único)`; entidad `@Immutable Zona` (`espacios/domain/Zona.java`).
  El espacio guarda el **código** (FK `espacio.zona → zona.codigo`). HU-07 P1 dice "HU-10 busca por ese código".
- **Precio por hora**: `precio_hora NUMERIC(8,2)`, S/ 1.00–100 000.00, un único precio (RN-18).
- **Fotos**: entidad `FotoEspacio` (`foto_espacio`): `espacioId`, `rutaArchivo` (`espacios/<UUID>`), `tipoMime`,
  `orden` 1–5. **La principal es `orden = 1`** (comentario L12 de `FotoEspacio.java`; modelo-datos L21).
  URL pública: `EspacioService.urlDe(foto)` (L191-195, package-private `static`) → `/api/fotos/<UUID>`.
- Publicar exige ≥ 1 foto (`EspacioService.publicar` L142; HU-07 DT-3 justificado por HU-10 CA2), así que
  todo `PUBLICADO` tiene foto principal.

**Repositorios**:
- `EspacioRepository` (L16-26): solo consultas del dueño (`findByArrendadorIdOrderByCreadoEnDesc`,
  `findByIdAndArrendadorId`, `findParaActualizar` con lock). **No hay consulta por estado/tipo/zona ni paginada.**
- `FotoEspacioRepository` (L10-20): `findByEspacioIdOrderByOrdenAsc`, `findByIdAndEspacioId`, `countByEspacioId`,
  `findByRutaArchivo`. No hay consulta en lote de fotos principales (`espacioId in :ids and orden = 1`).
- `ZonaRepository`: `findAll`, `existsById`, `findById`.

**Servicios**: `EspacioService` (concreto, sin interfaz; todo exige rol ARRENDADOR vía
`cuentas.habilitacionArrendador`), `FotoEspacioService`, `CalendarioEspacioService`, `DisponibilidadService`
(interfaz pública) + `DisponibilidadServiceImpl`. `EspacioService.listarZonas()` (L76-83) ordena con Collator es-PE.

**DTOs**: `EspacioDTO` (ficha completa del dueño, incluye `direccion`), `EspacioResumenDTO`
(id, nombre, tipo, zona, zonaNombre, precioHora, estado — del dueño), `FichaEspacioDTO`, `FotoEspacioDTO`,
`ZonaDTO(codigo, nombre)`, `FotoServida`.

**Controladores / endpoints**:
- `EspacioController` `/api/arrendadores/actual/espacios` (solo dueño).
- `CalendarioEspacioController` `…/{id}/horario`, `/bloqueos`, `/disponibilidad?fecha=` (solo dueño).
- `ZonaController` `GET /api/zonas` (L20-23). **Ojo: no está en `permitAll`** de `ConfiguracionSeguridad`
  (L43-56), así que hoy exige sesión. La pantalla pública de búsqueda necesita las zonas sin sesión: HU-10 debe
  abrirla (`GET /api/zonas` permitAll) o exponer otra ruta.
- `FotoPublicaController` `GET /api/fotos/{uuid}` (L32-40): **único endpoint público existente** del módulo;
  `permitAll` en `ConfiguracionSeguridad` L55. `Cache-Control: public, max-age=3600` si es pública.
  `FotoEspacioService.ver` (L121-136) solo sirve sin sesión si espacio `PUBLICADO` y `cuentas.cuentaActiva(arrendador)`.
- **No existe ningún endpoint público de listado o consulta de espacios.**

Arquitectura: `busqueda` es un módulo aparte (`docs/architecture.md` L50) y **solo puede usar la interfaz de
service de `espacios`, nunca sus repositorios** (L56-58, L183-184). Hoy la única interfaz pública de `espacios`
es `DisponibilidadService`; HU-10 debe agregar ahí (o en otra interfaz pública de `espacios`) los métodos de
catálogo que necesite. HU-07 design L755: "Los índices de búsqueda por `(estado, tipo, zona)` los agrega HU-10".

## 2. Disponibilidad (HU-08)

- Tablas (`V2_4__disponibilidad.sql`): `horario_semanal(espacio_id, dia_semana 1–7 ISO, hora_inicio 0–23,
  hora_fin 1–24 exclusivo)` con `UNIQUE(espacio_id, dia_semana, hora_inicio)`; `bloqueo_fecha(espacio_id, fecha)`
  día completo, `UNIQUE(espacio_id, fecha)`. Los bloques libres no se guardan.
- `CalculoDisponibilidad.bloquesDelDia(fecha, franjas, bloqueada, horasReservadas, ahoraLocal)` (L20-31): cálculo
  puro; precedencia BLOQUEADO > RESERVADO > PASADO > LIBRE (L33-45).
- `VentanaReserva.desde(hoy)` (L10-12): hoy … hoy+55 (`DIAS = 56`), `contiene(fecha)`. Usar para validar la fecha.
- `ZonaHoraria.hoy(reloj)` / `ahora(reloj)` en `America/Lima` (`comun/configuracion/ZonaHoraria.java` L20, L24).
- **Método reutilizable existente**: `DisponibilidadService.horasLibres(espacioId, fecha)` (interfaz L14;
  impl L52-68): lista vacía si fuera de ventana, no `PUBLICADO` o arrendador no `ACTIVA`. **Es de a un espacio**:
  `docs/architecture.md` L186-189 advierte que usarlo por candidato y paginar después es N+1 y rompe las páginas de
  20; propone un método en lote p. ej. `conBloquesLibres(Collection<Long> espacioIds, LocalDate fecha) → Set<Long>`
  que reutilice `CalculoDisponibilidad` y consulte horario, bloqueos y el puerto una vez por lote.
  Los repos actuales son por espacio (`findByEspacioIdAndDiaSemanaOrderByHoraInicioAsc`,
  `existsByEspacioIdAndFecha`); el lote necesita consultas nuevas `espacioId in :ids`.
- Helpers package-private en `DisponibilidadServiceImpl`: `ventana()` (L70), `reservasDelDia` (L74-80),
  `bloquesDelDia` (L82-95).
- **Reservas**: puerto `espacios/service/puertos/ReservasDelEspacio.bloquesReservados(espacioId, desde, hasta)`
  (L21), inyectado como `ObjectProvider`; sin bean (HU-12 aún no existe) → ninguna fecha tiene reservas
  (`DisponibilidadServiceImpl` L74-79). El puerto es por espacio; un lote tendría que llamarlo por espacio o
  extender el puerto (decisión del design).
- Ruta pública de disponibilidad para la ficha es de HU-11, "bajo `busqueda`, con el rate limiting de HU-10"
  (architecture L190).
- Validación de fecha en HU-08: llega como texto y el servicio la parsea, error en campo `fecha` con
  `MENSAJE_FECHA_INVALIDA` (`CalendarioEspacioService` L50, L176-186; controller L27, L71). Fuera de ventana → 400.

## 3. Suspensión (HU-09) e identidad (HU-06)

- Estado de cuenta: `usuario.estado` (`EstadoUsuario`: `ACTIVA, SUSPENDIDA, ELIMINADA`). Tabla
  `suspension_cuenta` (`V3_1`) es solo historial.
- `CuentaService` (interfaz pública, sin sesión ni rol):
  - `cuentaActiva(usuarioId)` (L92; impl `CuentaServiceImpl` L212-217).
  - `arrendadoresNoActivos() → Set<Long>` (L99; impl L221-223, `UsuarioRepository.findIdsArrendadoresNoActivos`
    L37-40: rol ARRENDADOR y estado ≠ ACTIVA). **Hecho para HU-10.**
- Regla documentada (`docs/architecture.md` L153-159; HU-09 D1): no leer `usuario` desde `busqueda`; la búsqueda
  paginada agrega `e.arrendadorId not in :ids` y **omite la condición si el conjunto está vacío** (no portable
  en JPQL). `espacio.estado` no cambia al suspender.
- Identidad: `PerfilArrendador.estadoIdentidad` (`EstadoIdentidad`: `NO_INICIADA, EN_REVISION, VERIFICADA,
  RECHAZADA`). Solo se puede **publicar** con identidad verificada (`EspacioService.publicar` L139-141), así que
  la búsqueda no necesita volver a filtrar por identidad: `PUBLICADO` ⇒ verificado al publicar. (No hay regla que
  despublique si cambia la identidad.)
- Visibilidad pública = `estado = PUBLICADO` ∧ arrendador `ACTIVA` (misma condición en `FotoEspacioService.ver`
  L129-130 y `horasLibres` L58-61).

## 4. `comun`

- **Paginación**: no hay utilidad común ni uso de `Pageable`/`Page`/`Slice` en el código. Precedente: HU-09
  `buscarCuentas` usa `Limit.of(20 + 1)` y `hayMas` sin paginar (`CuentaServiceImpl` L51, L151-157;
  `BusquedaCuentasDTO(cuentas, hayMas)`). HU-10 debe definir su forma (página/tamaño fijo 20, total o `hayMas`).
- **Errores**: `ManejadorGlobalErrores` (`comun/error/ManejadorGlobalErrores.java`): `ReglaNegocioException`
  → 400 (con campo → `ErrorDTO(MENSAJE_VALIDACION, [ErrorCampoDTO])`, L23-30), `ValidacionException` → 400 varios
  campos (L59-62), `RecursoNoEncontradoException` → 404, `DemasiadosIntentosException` → 429, otras → genérico.
  Mensajes en `ErrorDTO` (L7-15), p. ej. `MENSAJE_DEMASIADOS_INTENTOS`.
- **Rate limiting por IP**: existe y es reutilizable.
  - `FiltroLimiteIntentos` (`@Component`, L20-53): recorre todos los beans `ReglaLimite`; si
    `metodo` y `ruta` coinciden **exactamente** (`ReglaLimite.aplicaA` L15-17, `equals` de la ruta, sin
    patrones; el query string no cuenta) y `LimitadorIntentos.intentar` falla → 429 con `MENSAJE_DEMASIADOS_INTENTOS`.
  - `ReglaLimite(metodo, ruta, maximo, ventana, grupo)` (L9): reglas con el mismo `grupo` comparten cupo — útil
    para el "compartido" búsqueda/ficha/filtros (RNF-SEG-17). Para HU-11 (`/…/{id}`) el `equals` exacto no
    sirve con rutas variables: posible extensión a patrón (decisión de diseño).
  - `LimitadorIntentos` (L13-66): en memoria, ventana deslizante, los rechazados no cuentan.
  - Ejemplo de registro de reglas: `cuentas/controller/ReglasLimiteCuentas.java` (L18-27, `@Bean ReglaLimite`).
  - IP: `comun/web/IpCliente.de(request)`.
  - **Límite numérico para búsqueda: no definido.** `docs/requerimientos.md` RNF-SEG-17 (L162) dice "búsqueda,
    ficha y filtros por IP (compartido)" sin cifra; §9 (L286-295) no lo resuelve. Pendiente de decidir en el spec
    (preguntar al dueño).
- **Seguridad**: `ConfiguracionSeguridad.cadenaSeguridad` (L43-56). Rutas `permitAll` actuales:
  `GET /api/politicas-privacidad/vigente`, `POST /api/usuarios`, `POST /api/arrendadores`, `POST /api/sesiones`,
  `POST /api/recuperaciones{,/validaciones,/confirmaciones}`, `GET /api/archivos`, `GET /api/fotos/*`. Todo lo
  demás `authenticated()`. HU-10 debe agregar su ruta (p. ej. `GET /api/espacios` o `/api/busqueda`) y
  probablemente `GET /api/zonas`. CORS solo GET/POST/... sobre `/api/**` (L62-71).

## 5. Migraciones Flyway

Ruta: `2_Backend/src/main/resources/db/migration/`. Existentes: `V1_1__usuarios`, `V1_2__auditoria`,
`V1_3__perfil_arrendador`, `V1_4__sesion`, `V1_5__token`, `V2_1__documento_identidad`, `V2_2__espacio`,
`V2_3__revision_identidad`, `V2_4__disponibilidad`, `V3_1__suspension_cuenta`. **Última: `V3_1`** → la siguiente
libre es `V3_2` (HU-09 S12 avisa que HU-10/11/12 pueden chocar con numeración al unir).

Tablas relevantes: `zona` (50 filas sembradas: 43 Lima + 7 Callao, `V2_2` L5-34), `espacio` (`V2_2` L36-54;
CHECK tipo/estado; solo índice `ix_espacio_arrendador`), `foto_espacio` (`V2_2` L56-67, `uq_foto_espacio_orden
(espacio_id, orden)` sirve de índice), `horario_semanal`, `bloqueo_fecha` (`V2_4`), `usuario` (estado).
No hay índice `(estado, tipo, zona)`: lo agrega HU-10 (HU-07 design L755). `docs/modelo-datos.md` L19-23
coincide (zona catálogo cerrado, "HU-07, HU-10").

## 6. Frontend

- Kit `3_frontend/src/components/ui/`: `Alerta`, `Boton`, `Campo` (select, date con `min`/`max`, textarea,
  archivo), `EtiquetaEstado`, `Modal`, `SelectorBloques`, `Tarjeta` (`titulo`, `children`, `acciones`;
  docs/frontend.md L176: "espacio en resultados"). **No hay componente de paginación** ni de foto pública:
  si hace falta, se agrega al kit (frontend.md L166-168).
- Foto: `pages/espacios/FotoDelEspacio.jsx` descarga con token vía blob (para borradores). Para fotos públicas
  basta `<img src={urlApi(url)}>` (`api/cliente.js` L13-15 `urlApi`).
- `src/api/cliente.js`: `peticion(ruta, { metodo, cuerpo, autenticada = true })` (L46) — usar
  `autenticada: false` o dejar que mande token si existe; no hay helper de query string.
- `src/api/espacios.js`: `listarZonas()` → `GET /api/zonas` (L5-7, hoy requiere sesión), resto del dueño.
  No existe `src/api/busqueda.js`.
- Rutas (`App.jsx` L80-186): `/` es una página de enlaces inline (registro, arrendador, iniciar sesión,
  L81-96); **no hay página de búsqueda**. Resto: `/registro`, `/registro-arrendador`, `/privacidad`,
  `/iniciar-sesion`, `/recuperar-contrasena`, `/restablecer-contrasena`, `/panel/**`, `/admin/**`.
  Páginas por módulo: `pages/busqueda/` (frontend.md L40-41). Rutas en español con guiones (L207-208).
- Mensajes (`docs/frontend.md` L186-203): lista vacía → "Texto secundario que explique qué falta" (L198); errores
  de la API → `Alerta error` con el `ErrorDTO` tal cual (L194); red → "No pudimos conectar. Intenta de nuevo."
  (L195); en curso → `Boton cargando` (L197). Montos en `--color-texto` negrita (L119-120). Fecha elegible hoy…
  hoy+55 con `min`/`max` (L162). No hay texto predefinido para el estado vacío de HU-10 (CA3: mensaje + sugerencia
  de cambiar fecha o zona) — lo fija el spec.

## 7. Menciones a HU-10 / búsqueda en docs y specs

- `docs/requerimientos.md`: RF-C-01…03 (L77-79), RNF-SEG-10 lista blanca de filtros (L155, HU-23), RNF-SEG-16
  minimización: sin teléfono, correo ni documentos del arrendador (L161), RNF-SEG-17 rate limiting compartido
  (L162), RNF-USA-01 páginas de 20 (L192), RN-04 disponibilidad (L213), RN-15 solo publicados, no pausados ni
  ocultos, de no suspendidos (L224), fila de trazabilidad HU-10 (L248), HU-23 Decorator sobre la búsqueda base
  (L261; architecture L292: "cada filtro envuelve a la búsqueda base de HU-10" → diseñar la búsqueda como
  interfaz envolvible).
- `docs/architecture.md`: módulo `busqueda` (L50), dirección `busqueda → espacios` y `busqueda → reservas`
  permitidas (L121-125), `ParticipanteDatosPersonales` lo implementará `busqueda` (L130, HU-22, no HU-10),
  regla HU-09 D1 (L153-159), `DisponibilidadService` (L161-165), pendiente HU-10 método en lote (L186-189),
  HU-11 usa el rate limiting de HU-10 (L190).
- `specs/HU-07`: requirements P1 (zona catálogo, búsqueda por código), P3 (≥ 1 foto), CA2 trazado al catálogo de
  HU-10/11 (L378), RNF-SEG-16 R7 (L395); design DT-3 (L29), índices `(estado, tipo, zona)` para HU-10 (L755),
  componente de foto "si HU-10/11 lo necesita, se mueve" al kit (L850), zona texto libre descartada (L990).
- `specs/HU-08`: P1 `ZonaHoraria` lista para HU-10 (requirements L23), P9 `horasLibres` reutilizable (L31),
  DT-2 `VentanaReserva` para filtrar por fecha (design L26), trazabilidad §8 (requirements L318).
- `specs/HU-09`: D1 y S14 (requirements L18, L43), RN-15 → R27/R28 usados por HU-10 (L324), S12 numeración de
  migración (L41).

## Puntos abiertos para el spec (resumen)

1. Límite numérico de rate limiting por IP para búsqueda (RNF-SEG-17 sin cifra) y si el grupo se comparte ya con
   HU-11/HU-23 (clave de grupo común); `ReglaLimite` solo compara ruta exacta.
2. Forma de la paginación (número de página + `hayMas`/total) — no hay utilidad común.
3. Hacer público `GET /api/zonas` (hoy `authenticated`).
4. Nueva interfaz pública en `espacios` para candidatos paginados (estado PUBLICADO, tipo, zona, excluyendo
   `arrendadoresNoActivos()`) con foto principal en lote, más el método en lote de disponibilidad; cómo paginar
   cuando el filtro "≥ 1 bloque libre" se calcula en Java (filtrar en SQL por horario del día de la semana y
   ausencia de bloqueo, y en Java solo las horas pasadas de hoy / reservas).
5. Índice `(estado, tipo, zona)` en `V3_2__…`.
6. Orden de resultados (no definido en la HU).
7. Fecha fuera de la ventana de 8 semanas o inválida: 400 en campo `fecha` (patrón HU-08) vs. lista vacía.
