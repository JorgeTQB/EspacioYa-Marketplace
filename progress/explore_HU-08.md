# Exploración — HU-08 · Disponibilidad del espacio

> Explorador de solo lectura, 2026-10-28. Rutas relativas a la raíz del repo.
> `B` = `2_Backend/src/main/java/pe/edu/ulima/espacioya`, `T` = `2_Backend/src/test/java/pe/edu/ulima/espacioya`,
> `F` = `3_frontend/src`, `M` = `2_Backend/src/main/resources/db/migration`.

## Preguntas

1. Backend de `espacios` (HU-07): entidades, repos, servicios, controladores, DTO, endpoints, validaciones,
   autorización (propiedad). Migraciones existentes, siguiente número y si ya hay algo de disponibilidad.
2. `comun` reutilizable: excepciones/errores, auditoría, rate limiting, seguridad/usuario, fechas y zona horaria.
3. Patrones de tests (unitarios, IT con Testcontainers, nombres, trazado de R<n>), con HU-07 como referencia.
4. Frontend: pantallas/rutas de HU-07, kit `components/ui/` (calendario, fecha/hora, tabla, modal), `api/espacios.js`,
   manejo de mensajes/errores.
5. Qué de HU-10, HU-11, HU-12 (y HU-21) depende del modelo de disponibilidad de HU-08.
6. Ambigüedades o huecos para el spec-author.

## Archivos analizados

- `docs/historias/HU-08.md`, `HU-10.md`, `HU-11.md`, `HU-12.md`, `HU-21.md`
- `docs/architecture.md` (principios, puertos, estados, simplificaciones), `docs/modelo-datos.md`,
  `docs/requerimientos.md` (RF-B-05…07, RN-04, RNF-SEG-12/17/19, §8, §9), `docs/conventions.md`,
  `docs/verification.md`, `docs/frontend.md`
- `specs/HU-07/design.md` (DT-1…DT-20, endpoints, firmas), `progress/HU-08.md`, `progress/explore_HU-07.md`,
  `feature_list.json`
- `B/espacios/**` (controller, domain, dto, repository, service), `B/comun/**` (error, auditoria, limite,
  seguridad, configuracion, web), `B/cuentas/service/CuentaService.java`
- `M/*.sql` (en especial `V1_2__auditoria.sql`, `V2_2__espacio.sql`), `application.properties`
- `T/espacios/**` (`EspaciosControllerSoporte`, `EspacioControllerIT`, `EspacioServiceTest`), `T/RelojControlable.java`
- `F/App.jsx`, `F/api/cliente.js`, `F/api/espacios.js`, `F/components/ui/*`, `F/pages/espacios/*`,
  `F/styles/tokens.css`

---

## 1. Backend del módulo `espacios` (HU-07)

### Archivos

| Capa | Archivo | Contenido relevante |
|---|---|---|
| domain | `B/espacios/domain/Espacio.java` | Entidad `espacio`. `crear`, `editar` (devuelve lista de cambios `campo=a->b`), `publicar`, `tocar(Instant)` (solo `actualizadoEn`), `esEditable()` = BORRADOR/PUBLICADO/PAUSADO, `esPublicable()` = BORRADOR. Getters, sin setters. |
| | `EstadoEspacio.java` | `BORRADOR, PUBLICADO, PAUSADO, BAJA, OCULTO` (los 5 ya existen) |
| | `TipoEspacio.java`, `DatosEspacio.java`, `FotoEspacio.java`, `Zona.java` | — |
| repository | `B/espacios/repository/EspacioRepository.java` | `findByArrendadorIdOrderByCreadoEnDesc`, `findByIdAndArrendadorId` (lectura), `findParaActualizar(id, arrendadorId)` con `@Lock(PESSIMISTIC_WRITE)` (HU-07 DT-12: serializa operaciones sobre el mismo espacio) |
| | `FotoEspacioRepository.java`, `ZonaRepository.java` | — |
| service | `B/espacios/service/EspacioService.java` | `listarZonas`, `listar`, `consultar`, `crear`, `editar`, `publicar`. Método **package-private** `Long arrendadorHabilitado(UsuarioAutenticado)` (rol ARRENDADOR + correo verificado; lo reutiliza `FotoEspacioService`). Constantes `ACCION_*`, `ENTIDAD_ESPACIO = "espacio"`, `MENSAJE_ESTADO_NO_EDITABLE = "Este espacio ya no se puede editar."`. Método privado `auditar(...)`. |
| | `FotoEspacioService.java` | Patrón para un servicio hermano dentro de `espacios`: inyecta `EspacioService` (para `arrendadorHabilitado` y `consultar`), `EspacioRepository`, `AuditoriaService`, `TransactionTemplate`, `Clock`; `exigirEditable(espacio)` lanza `ReglaNegocioException(MENSAJE_ESTADO_NO_EDITABLE)`. |
| dto | `B/espacios/dto/FichaEspacioDTO.java` | Record de entrada con Bean Validation, constantes `M_*` con el texto exacto, constructor compacto que recorta; deserializador `SoloEntero` para rechazar decimales/textos en un entero (útil si HU-08 recibe horas como enteros). |
| | `EspacioDTO`, `EspacioResumenDTO`, `FotoEspacioDTO`, `ZonaDTO`, `FotoServida` | Salidas |
| controller | `B/espacios/controller/EspacioController.java` | `@RequestMapping("/api/arrendadores/actual/espacios")` |
| | `FotoEspacioController.java` | `@RequestMapping("/api/arrendadores/actual/espacios/{id}/fotos")` |
| | `ZonaController.java`, `FotoPublicaController.java` | Rutas públicas |

### Endpoints existentes

| Método y ruta | Notas |
|---|---|
| `GET /api/arrendadores/actual/espacios` | lista del dueño (rol ARRENDADOR) |
| `POST /api/arrendadores/actual/espacios` | crear borrador (rol + correo verificado) → 201 |
| `GET /api/arrendadores/actual/espacios/{id}` | detalle propio |
| `PUT /api/arrendadores/actual/espacios/{id}` | editar ficha |
| `POST /api/arrendadores/actual/espacios/{id}/publicacion` | publicar |
| `POST /api/arrendadores/actual/espacios/{id}/fotos` (multipart) · `DELETE …/{id}/fotos/{fotoId}` | fotos |
| `GET /api/zonas` | catálogo (autenticado: no está en `permitAll`) |
| `GET /api/fotos/{uuid}` | `permitAll` en `B/comun/seguridad/ConfiguracionSeguridad.java` |

HU-07 DT-7 reserva **`GET /api/espacios/{id}`** para la ficha pública de HU-11; `docs/conventions.md` usa
`/api/espacios/{id}/disponibilidad` como ejemplo de ruta. Las rutas del dueño siguen el patrón `/actual`
(la identidad sale del token). Ruta natural para HU-08: `/api/arrendadores/actual/espacios/{id}/horario`,
`…/{id}/bloqueos` (o `…/{id}/disponibilidad`).

### Autorización (cómo se verifica el dueño)

Orden fijo en `EspacioService` (comentario de clase, R53 de HU-07): **rol → correo verificado → propiedad → estado**.

1. `cuentas.habilitacionArrendador(usuario)` (`B/cuentas/service/CuentaService.java`) lanza
   `AccesoDenegadoException` (403) si el rol no es `ARRENDADOR`; devuelve `HabilitacionArrendadorDTO(correoVerificado, identidadVerificada)`.
2. Si `!correoVerificado` → `AccesoDenegadoException` (403). Las lecturas (`listar`, `consultar`) **no** exigen correo.
3. Propiedad **en la consulta**: `findByIdAndArrendadorId` / `findParaActualizar(id, usuario.usuarioId())`;
   ajeno o inexistente → `RecursoNoEncontradoException` (404 idéntico; HU-07 DT-8, RNF-SEG-19).
4. Estado: `!esEditable()` → `ReglaNegocioException(MENSAJE_ESTADO_NO_EDITABLE)` (400).

Validaciones de entrada: `@Valid @RequestBody` + Bean Validation en records → handler de
`MethodArgumentNotValidException` agrupa errores por campo. Reglas que no caben en anotaciones →
`ReglaNegocioException(campo, mensaje)` (un campo) o `ValidacionException(List<ErrorCampoDTO>)` (varios).

### Migraciones Flyway (`M/`)

`V1_1__usuarios.sql`, `V1_2__auditoria.sql`, `V1_3__perfil_arrendador.sql`, `V1_4__sesion.sql`, `V1_5__token.sql`,
`V2_1__documento_identidad.sql`, `V2_2__espacio.sql` (HU-07: `zona`, `espacio`, `foto_espacio`),
`V2_3__revision_identidad.sql` (HU-06).

- HU-08 es sprint 2 → **siguiente libre: `V2_4__…`** (p. ej. `V2_4__disponibilidad.sql`). Regla de
  `docs/conventions.md`: si al unir con `develop` ya existe `V2_4`, renombrar; `spring.flyway.out-of-order=true`.
- `spring.jpa.hibernate.ddl-auto=validate`: los tipos de columna deben coincidir exactamente con las entidades
  (p. ej. `SMALLINT` ↔ `short`/`Short`, `TIME` ↔ `LocalTime`, `DATE` ↔ `LocalDate`).
- **No existe** ninguna tabla/columna de disponibilidad, horario ni bloqueo. `espacio` no tiene columnas de
  horario. Patrón de FK de HU-07: `espacio_id BIGINT NOT NULL REFERENCES espacio (id) ON DELETE CASCADE`,
  `GENERATED ALWAYS AS IDENTITY`, `CONSTRAINT ck_…`/`uq_…` con nombre.
- `docs/modelo-datos.md` ya define: `horario_semanal` (id, espacio_id, dia_semana, hora_inicio, hora_fin) y
  `bloqueo_fecha` (id, espacio_id, fecha), ambas HU-08. No fija tipos, unicidad ni CHECK: el design debe
  fijarlos y actualizar `modelo-datos.md` (tabla "Restricciones clave") en el mismo PR.
- `reserva_bloque` (HU-12) identifica un bloque por `(espacio_id, fecha, hora_inicio)` con índice único parcial
  `WHERE activo`: HU-08 debe usar la **misma granularidad** (bloque = fecha + hora de inicio entera).

---

## 2. Paquete `comun` reutilizable

| Pieza | Ruta | Uso para HU-08 |
|---|---|---|
| Errores | `B/comun/error/ReglaNegocioException.java` (campo opcional) → **400**; `ValidacionException` (varios campos) → 400; `AccesoDenegadoException` → 403 genérico; `RecursoNoEncontradoException` → 404 genérico; `ManejadorGlobalErrores.java`; `ErrorDTO(mensaje, errores)` + `ErrorCampoDTO(campo, mensaje)` | CA3 (conflicto con reserva pagada) encaja como `ReglaNegocioException` → 400 con mensaje. **No hay manejador de 409**: `docs/conventions.md` dice "regla violada → 400/409", pero hoy todo sale 400. Agregar un 409 implica tocar `comun/error` (decisión de diseño). |
| Auditoría | `B/comun/auditoria/AuditoriaService.registrar(new EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle))`; tabla `auditoria` (`accion VARCHAR(60)`, `detalle TEXT`) | (R) "registro de cada cambio de disponibilidad": constantes `ACCION_*` en el servicio (estilo HU-07: `CREACION_ESPACIO`…), `entidad = "espacio"` y `entidadId = espacioId` (o una entidad propia), detalle con el diff. IP: `B/comun/web/IpCliente.de(request)` en el controlador. Precedente de auditar rechazos fuera de la transacción: `FotoEspacioService`. |
| Rate limiting | `B/comun/limite/ReglaLimite.java`, `FiltroLimiteIntentos.java`, `LimitadorIntentos.java`; reglas como `@Bean` (ej. `B/cuentas/controller/ReglasLimiteCuentas.java`) | Coincidencia **exacta** de ruta (`ruta.equals(...)`): no soporta `{id}`. HU-08 (STRIDE) no pide rate limiting; RNF-SEG-17 lo pide para búsqueda/ficha (HU-10/11), no para el calendario del dueño. |
| Seguridad | `B/comun/seguridad/UsuarioAutenticado` (`usuarioId, sesionId, rol`) vía `@AuthenticationPrincipal`; `ConfiguracionSeguridad` (todo `anyRequest().authenticated()` salvo `permitAll` listados; CORS ya permite GET/POST/PUT/PATCH/DELETE) | Rutas del dueño no requieren cambios en seguridad. Una ruta pública de disponibilidad (si HU-08 la creara) requeriría un `permitAll` nuevo. |
| Reloj | `B/comun/configuracion/ConfiguracionReloj.java`: `@Bean Clock reloj() = Clock.systemUTC()` | **No hay utilidad de zona horaria** ni `ZoneId` en todo el código (grep `ZoneId|America/Lima|LocalDate|LocalTime|DayOfWeek` solo encuentra `T/RelojControlable.java`). "Hoy", "fecha pasada" y "próximas 8 semanas" necesitan la zona de Lima: hay que decidir dónde vive (`comun/configuracion`, p. ej. constante/bean `ZoneId` `America/Lima`) para que HU-10/11/12 la reutilicen. |
| Transacciones | `TransactionTemplate` inyectable (usado en `FotoEspacioService`) | Si se audita un rechazo (CA3) fuera de la transacción. |
| Interfaz de cuentas | `B/cuentas/service/CuentaService.habilitacionArrendador` | Para rol + correo (vía `EspacioService.arrendadorHabilitado`, package-private, mismo paquete `espacios.service`). |

### Puerto previsto para reservas

`docs/architecture.md` ("Puertos entre módulos") ya lista **`espacios.ReservasDelEspacio`**, implementado por
`reservas`, "para HU-08 y HU-21", ubicado en `B/espacios/service/puertos/` (dirección permitida
`espacios ← reservas`; `espacios` no puede depender de `reservas`).

- **El módulo `reservas` no existe** (ni paquete, ni tabla `reserva`/`reserva_bloque`): HU-12 es sprint 3.
  Con HU-08 en sprint 2, el puerto no tendrá implementación real. El design debe resolver cómo arranca Spring
  sin implementación (p. ej. implementación por defecto "sin reservas" en `espacios`, registrada con
  `@ConditionalOnMissingBean`/`@Primary` invertido, u `ObjectProvider`), y cómo se prueba CA3 (mock del puerto en
  unitarias; un bean de prueba en IT).
- Métodos que el puerto probablemente necesita (a fijar en design): "¿hay reserva pagada (`CONFIRMADA`) del
  espacio en la fecha X?" (CA3) y "horas ocupadas por reservas activas (pendientes + confirmadas) del espacio en
  la fecha X" (para restar reservas en "horario − bloqueos − reservas"). HU-21 lo reutilizará para "reservas
  futuras" del espacio: conviene nombrarlo de forma general.

---

## 3. Patrones de tests (referencia HU-07)

- **Unitarias** (`*Test`, sin Docker, `./2_Backend/mvnw -f 2_Backend/pom.xml test`):
  `T/espacios/service/EspacioServiceTest.java` → `@ExtendWith(MockitoExtension.class)`, mocks de repos,
  `CuentaService`, `AuditoriaService`; `Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC)`;
  `UsuarioAutenticado(ID, 31L, "ARRENDADOR")`; entidades instanciadas por reflexión (`ReflectionTestUtils`) cuando
  hace falta un id. También tests de dominio (`T/espacios/domain/EspacioTest.java`) y de DTO
  (`T/espacios/dto/FichaEspacioDTOTest.java`, validación Bean Validation).
- **Integración** (`*IT`, Testcontainers PostgreSQL real, `verify`): base abstracta
  `T/espacios/controller/EspaciosControllerSoporte.java` con `@SpringBootTest @AutoConfigureMockMvc
  @Import({TestcontainersConfiguration.class, EspaciosControllerSoporte.Dobles.class})` (un solo contexto
  compartido). Provee:
  - `RelojControlable` (`T/RelojControlable.java`, zona UTC) como `@Primary Clock`, inicio `2026-10-28T15:00:00Z`,
    y **`prepararBase()` lo avanza 1 h en cada test** → la fecha "de hoy" cambia a lo largo de la suite: los tests
    de HU-08 deben calcular fechas relativas a `reloj.instant()` (y a la zona de Lima), nunca literales.
  - `prepararBase()` borra con `jdbc.update("DELETE FROM …")` en orden de FK (`foto_espacio`, `espacio`, …). Las
    tablas nuevas tienen que borrarse ahí (o caer por `ON DELETE CASCADE` al borrar `espacio`).
  - IP distinta por test (`10.7.x.x` para HU-07; HU-08 debería usar otro rango, p. ej. `10.8.x.x`).
  - Helpers: `crearArrendador`, `crearUsuario`, `token(correo)` (login real por `/api/sesiones`),
    `forzarIdentidad`, `forzarEstado(espacioId, estado)`, `quitarCorreoVerificado`, `crearEspacio(token)`,
    `con(token, solicitud)`, `mapa/lista` (JSON), `auditorias(accion)`, `instante(...)`.
  - Las IT de HU-08 pueden extender esta base (mismo paquete `espacios.controller`) o una propia.
- **Trazado de R<n>**: `@DisplayName("HU-07 R1, R2: …")` en cada test (`docs/conventions.md`,
  `docs/verification.md` Nivel 4); el implementer documenta el mapa `R<n> → Clase.metodo` en
  `progress/impl_HU-XX.md`. Una prueba por CA (incluido el de error) y por control STRIDE (E: dueño ajeno → 404,
  rol → 403; R: fila de auditoría).
- Concurrencia: ejemplo con `CountDownLatch` + `Executors` en `docs/verification.md` (útil si se prueba
  bloqueo de fecha vs. reserva simultánea, aunque sin `reservas` no aplica todavía).
- `./init.sh --rapido` verde con 329 tests backend (`progress/HU-08.md`).

---

## 4. Frontend

- **Rutas** (`F/App.jsx`), todas bajo `<RutaProtegida>`: `/panel/espacios` → `F/pages/espacios/MisEspacios.jsx`;
  `/panel/espacios/nuevo` y `/panel/espacios/:id` → `F/pages/espacios/EditarEspacio.jsx`. Las pantallas redirigen a
  `/panel` si `sesion.rol !== 'ARRENDADOR'`. Ruta natural para HU-08: `/panel/espacios/:id/disponibilidad`
  (rutas en español, minúsculas, guiones — `docs/frontend.md`), con enlace desde la `Tarjeta` de `MisEspacios`
  (prop `acciones`, hoy solo "Editar") y/o desde `EditarEspacio`.
- **Kit `F/components/ui/`**: `Boton`, `Campo`, `Alerta`, `EtiquetaEstado`, `Tarjeta`, `Modal`.
  - **No existe** calendario, selector de fecha/hora, tabla ni `SelectorBloques`.
  - `Campo` (`F/components/ui/Campo.jsx`): tipos `select`, `textarea`, `archivo`, `checkbox` y una rama genérica que
    pasa `tipo` a `<input type>` → `tipo="date"` / `tipo="time"` funcionan como inputs nativos sin tocar el kit
    (`onChange` recibe `evento.target.value`). `select` con `opciones=[{valor, etiqueta}]` sirve para horas enteras
    (00–23). `checkbox` para días de la semana.
  - `Modal` (`F/components/ui/Modal.jsx`, HU-06): `<dialog>`, props `abierto, titulo, onConfirmar, onCerrar,
    textoConfirmar, variante ('primario'|'peligro-confirmar'), cargando` — útil para confirmar un bloqueo.
  - `SelectorBloques` está en el kit previsto (`docs/frontend.md`, props `bloques, seleccionados, onCambiar`, "HU-11,
    HU-12") pero **no está construido**. Tokens ya definidos en `F/styles/tokens.css` (`--bloque-libre-*`,
    `--bloque-seleccionado-*`, `--bloque-ocupado-*`). Regla del kit: lo construye la primera historia que lo
    necesita; si HU-08 muestra una vista previa de bloques, le tocaría construirlo (y anotarlo en `docs/frontend.md`).
  - Una tabla (horario semanal) no existe en el kit: regla "si falta uno, se agrega al kit, no se improvisa".
- **API** (`F/api/espacios.js`): `listarZonas`, `listarMisEspacios`, `obtenerMiEspacio(id)`, `crearEspacio`,
  `editarEspacio`, `publicarEspacio`, `subirFotoEspacio`, `eliminarFotoEspacio`, `descargarFotoEspacio`; constante
  `MIS_ESPACIOS = '/api/arrendadores/actual/espacios'`. HU-08 agrega aquí sus funciones (una por endpoint).
  `F/api/cliente.js`: `peticion(ruta, { metodo, cuerpo, autenticada })` → JSON; errores lanzados como
  `{ estado, mensaje, errores[] }`; 204 → `null`; 401 con token → borra token y avisa (`alPerderSesion`).
- **Mensajes/errores** (patrón de `EditarEspacio.jsx` y `docs/frontend.md`): estado `alerta {tipo, texto}` →
  `<Alerta tipo="error">{error.mensaje}</Alerta>` tal cual del `ErrorDTO`; `erroresPorCampo(error.errores)` →
  `Campo error=…`; éxito con `Alerta tipo="exito"`; `Boton cargando` mientras envía; textos como constantes en el
  componente o en `F/pages/espacios/textosEspacios.js` (presentación, sin reglas de negocio); "Cargando…" en texto
  secundario; 404 → alerta con enlace "Volver a mis espacios". Solo colores vía `tokens.css` (lo revisa `init.sh`).

---

## 5. Dependencias de HU-10, HU-11, HU-12 (y HU-21) sobre el modelo de HU-08

| Historia | Qué necesita de HU-08 | Implicación para el diseño |
|---|---|---|
| HU-10 (busqueda, Sebastian) | CA1: espacios publicados "con al menos un bloque libre **esa fecha**". RN-04/§8: "Cálculo de disponibilidad … HU-08 (Sprint 2) lo reutilizan HU-10, 11, 12". Paginado de 20, rate limiting. | El cálculo (horario − bloqueos − reservas) debe exponerse desde `espacios` como **interfaz pública de servicio** (dirección `busqueda → espacios` permitida), no duplicarse. Debe poder evaluarse para **muchos espacios a la vez** (o traducirse a consulta SQL) para no hacer N+1 al filtrar la búsqueda. Las fechas fuera de las 8 semanas o pasadas deben dar "sin bloques". |
| HU-11 (busqueda, Adriana) | CA2: "bloques libres de ese día" en la ficha pública. Usa `SelectorBloques`. | Una operación `bloquesLibres(espacioId, fecha)` reutilizable (lista de horas de inicio, con estado libre/ocupado). La ruta pública probablemente es de HU-11 (`/api/espacios/{id}/…`, HU-07 DT-7); HU-08 debería dejar el servicio listo sin exponer datos del arrendador. |
| HU-12 (reservas, Eduardo) | Reservar "bloques libres consecutivos"; `reserva_bloque(espacio_id, fecha, hora_inicio, activo)` con único parcial. | Bloque = (fecha, hora_inicio entera, 1 h). `reservas` necesita validar que los bloques pedidos están en el horario, no bloqueados y dentro de 8 semanas → mismo servicio de `espacios`. `reservas` implementará `espacios.service.puertos.ReservasDelEspacio`. Carrera "arrendador bloquea fecha" vs "usuario reserva/paga": HU-08 bloquea con `findParaActualizar` (lock del espacio); HU-12 debería tomar el mismo lock o revalidar el bloqueo dentro de su transacción — dejarlo documentado. |
| HU-21 (espacios) | Dar de baja con reservas futuras → listar reservas afectadas. | Reusa el mismo puerto `ReservasDelEspacio`: nombrarlo y diseñar sus métodos sin atarlos solo a CA3. |

Nota: `busqueda` puede depender de `espacios` y `reservas` (diagrama de dirección en `docs/architecture.md`).
Solo los servicios usados por otros módulos llevan interfaz pública (`CuentaService` como precedente); HU-08
probablemente crea la primera interfaz pública de `espacios` (p. ej. `DisponibilidadService`) y lo anota en
`docs/architecture.md`.

---

## 6. Ambigüedades y huecos para el spec-author

1. **Zona horaria.** El `Clock` es UTC y no hay `ZoneId` en el código. ¿"Hoy", "fecha pasada" y "8 semanas" se
   calculan en `America/Lima`? ¿Dónde vive esa constante/bean (`comun/configuracion`) para HU-10/11/12?
2. **Ventana de 8 semanas.** ¿Desde hoy inclusive hasta hoy + 55 o + 56 días? ¿Las horas de hoy que ya
   empezaron/pasaron cuentan como libres? ¿La ventana es móvil (cada día se abre uno nuevo)?
3. **Forma del horario semanal.** ¿Un solo rango por día o varios (p. ej. 8–12 y 14–22)? El modelo permite varias
   filas por día. ¿Se reemplaza el horario completo en un `PUT` (más simple de auditar como diff) o se edita fila a
   fila? ¿Rangos solapados/contiguos se rechazan o se fusionan?
4. **Representación de horas.** Solo horas enteras (bloques de 1 h). ¿`hora_fin` puede ser 24:00 (medianoche)?
   `LocalTime` no admite 24:00 → ¿`TIME` con fin exclusivo y 23:59, o `SMALLINT` 0–24? Representación de
   `dia_semana` (1–7 ISO lunes=1, o texto `LUNES`…) y `CHECK` en BD. Unicidad: `bloqueo_fecha (espacio_id, fecha)`
   único; ¿algún único en `horario_semanal`?
5. **Bloqueo de fecha.** ¿Se bloquea el día completo o también horas puntuales? (CA2 habla de "fecha"; el modelo solo
   tiene `fecha`.) ¿Se puede **desbloquear**? (No hay CA, pero es natural.) ¿Se permiten fechas pasadas o fuera de las
   8 semanas? ¿Bloquear una fecha ya bloqueada es idempotente o error?
6. **CA3 — qué es "reserva pagada".** ¿Solo `CONFIRMADA`? ¿Qué pasa con una `PENDIENTE_PAGO` (retiene 15 min) en esa
   fecha: se permite bloquear, se rechaza, o se bloquea y la pendiente no podrá pagarse? Código HTTP del conflicto:
   400 (`ReglaNegocioException`, handler actual) o 409 (no hay handler; tocaría `comun/error`). Texto exacto del aviso
   (¿incluye cuántas reservas/horas?). ¿Se audita el intento rechazado?
7. **Cambio de horario con reservas existentes.** Si el arrendador quita horas del horario semanal donde ya hay
   reservas confirmadas futuras, ¿se rechaza (como CA3) o se permite (la reserva sigue válida y el bloque no se
   vuelve a ofrecer)? Ningún CA lo cubre.
8. **Sin módulo `reservas`.** El puerto `ReservasDelEspacio` no tendrá implementación hasta HU-12 (sprint 3). Definir la
   implementación por defecto y cómo se prueba CA3 (mock/bean de prueba). Confirmar con Eduardo Saldaña (HU-12) la
   firma del puerto.
9. **Estados del espacio.** ¿En qué estados se edita la disponibilidad? Probablemente `esEditable()` (BORRADOR,
   PUBLICADO, PAUSADO); ¿BAJA/OCULTO → "no se puede editar"? ¿Exige correo verificado como las escrituras de HU-07?
   ¿Un espacio sin horario puede publicarse? (HU-07 no lo exige; si no tiene horario, no aparece en HU-10.)
10. **Cómo se demuestra CA1/CA2 en HU-08.** "Esos bloques se pueden reservar" / "dejan de estar disponibles" requieren
    algo observable: ¿HU-08 expone una consulta de bloques libres (del dueño, `GET …/actual/espacios/{id}/disponibilidad
    ?fecha=`) o solo el servicio interno que usarán HU-10/11/12? ¿Una ruta pública queda para HU-11?
11. **Valores por defecto.** ¿Un espacio nuevo nace sin horario (nada reservable) o con uno por defecto?
12. **Auditoría.** Nombres de acciones (`HORARIO_ESPACIO_ACTUALIZADO`, `BLOQUEO_FECHA_CREADO`, `…_ELIMINADO`,
    `…_RECHAZADO`?), entidad (`espacio` con `entidadId` = espacio, o `horario_semanal`/`bloqueo_fecha`) y formato del
    `detalle` (diff del horario).
13. **Frontend.** Qué componente nuevo agrega al kit (tabla de horario semanal, ¿`SelectorBloques` para vista previa?)
    y si se usa `<input type="date">` nativo vía `Campo` o un calendario. Si HU-08 construye `SelectorBloques`, HU-11
    (Adriana) lo reutiliza: coordinar sus props.
14. **Concurrencia.** Bloqueo de fecha simultáneo con creación/pago de reserva (HU-12): ¿basta con
    `findParaActualizar` del espacio y que HU-12 tome el mismo lock? Documentarlo como contrato.
15. **Rate limiting.** HU-08 no lo pide (DT-14 de HU-07 aplicó el mismo criterio); confirmar que no se agrega.
16. **Docs a actualizar en el mismo PR.** `docs/modelo-datos.md` (tipos, CHECK y unicidad de las dos tablas),
    `docs/architecture.md` (firma del puerto `ReservasDelEspacio`, interfaz pública de disponibilidad, zona horaria),
    `docs/frontend.md` (componentes nuevos del kit).
