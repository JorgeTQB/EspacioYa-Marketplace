# Exploración — HU-11 · Detalle del espacio

- **Fecha:** 2026-10-30 · **Rama:** `feature/HU-11-detalle-espacio` · **Dueña:** Adriana Callupe
- **Alcance:** solo lectura. No se modificó nada fuera de este archivo.

## Qué pide la historia (`docs/historias/HU-11.md`)

- CA1: al abrir un espacio, se ven fotos, descripción, dirección en texto, capacidad, precio por hora y la política de
  cancelación (48 h).
- CA2: al elegir o cambiar una fecha, se ven los bloques libres de ese día.
- STRIDE (I): no se muestran teléfono, correo ni documentos del arrendador (RNF-SEG-16).
- STRIDE (D): rate limiting compartido con HU-10 (RNF-SEG-17, §9.9).
- Trazabilidad (`docs/requerimientos.md:249`): RF-C-04, RF-C-05 · RNF-SEG-16, 17 · RN-04, RN-06 · depende de HU-07 y
  HU-08 · **HU-12 depende de HU-11** (CA4 de HU-12: "vuelvo a la ficha después" del login).

## Preguntas

1. Backend `espacios`, `busqueda` y `comun`: entidades, repositorios, servicios, endpoints públicos, visibilidad, datos
   del arrendador, 404, rate limiting y seguridad.
2. Migraciones Flyway frente a `docs/modelo-datos.md`, incluidas las reseñas.
3. Cómo consultar la disponibilidad de HU-08 para la ficha.
4. Patrones de tests de HU-07, HU-08 y HU-10.
5. Frontend: búsqueda, enlace a la ficha, kit `components/ui/`, `src/api/`, rutas, galería o calendario.
6. Decisiones previas (specs HU-07, HU-08, HU-10, `progress/history.md`) y reglas de `docs/requerimientos.md`.

## Archivos analizados

Prefijo backend `B = 2_Backend/src/main/java/pe/edu/ulima/espacioya`, tests `T = 2_Backend/src/test/java/pe/edu/ulima/espacioya`.

- `B/busqueda/**` (controller, dto, service), `B/espacios/controller/*`, `B/espacios/service/*` y `service/puertos/*`,
  `B/espacios/domain/{Espacio,EstadoEspacio,Bloque,EstadoBloque,CalculoDisponibilidad,VentanaReserva,FotoEspacio,TipoEspacio}.java`,
  `B/espacios/dto/{EspacioDTO,FotoEspacioDTO,DisponibilidadDiaDTO,BloqueDTO,FichaEspacioDTO}.java`,
  `B/espacios/repository/*`.
- `B/cuentas/service/CuentaService.java`, `CuentaServiceImpl.java:212-223`, `B/cuentas/domain/{Usuario,PerfilArrendador}.java`.
- `B/comun/seguridad/ConfiguracionSeguridad.java`, `B/comun/limite/*`, `B/comun/error/{ManejadorGlobalErrores,ErrorDTO,RecursoNoEncontradoException}.java`.
- `2_Backend/src/main/resources/application.properties`, `db/migration/V*.sql`.
- Tests: `T/busqueda/**`, `T/espacios/controller/EspaciosControllerSoporte.java`, listados de `T/espacios/**`.
- `3_frontend/src/App.jsx`, `api/{cliente,busqueda,espacios}.js`, `pages/busqueda/*`, `pages/espacios/{DisponibilidadEspacio.jsx,textosEspacios.js}`,
  `components/ui/*`, `package.json`.
- `docs/{architecture,frontend,modelo-datos,requerimientos}.md`, `specs/HU-07`, `specs/HU-08`, `specs/HU-10`,
  `progress/history.md`, `progress/impl_HU-10.md`, `docs/historias/HU-12.md`.

---

## 1. Backend

### 1.1 Entidades (módulo `espacios`)

| Concepto | Dónde | Notas para HU-11 |
|----------|-------|------------------|
| Espacio | `B/espacios/domain/Espacio.java` (tabla `espacio`) | Campos: `id`, `arrendadorId`, `nombre`, `tipo` (`TipoEspacio`: CANCHA, ESTADIO, AUDITORIO), `direccion` (texto 5–200), `zona` (código), `capacidad` (int), `precioHora` (`BigDecimal`, escala 2), `descripcion` (10–2000), `estado`, `creadoEn`, `actualizadoEn`. Todo lo de CA1 ya existe salvo la política de cancelación (no es un dato del espacio: es fija, RN-06). |
| Estado | `EstadoEspacio.java`: `BORRADOR, PUBLICADO, PAUSADO, BAJA, OCULTO` | Solo `PUBLICADO` es público (RN-15). `PAUSADO`/`BAJA` (HU-21) y `OCULTO` (HU-27) aún no tienen flujo, pero el enum y el CHECK existen. |
| Zona | `Zona.java` (`codigo`, `nombre`), repo `ZonaRepository` | El nombre con tildes se obtiene por `zonas.findById(codigo)`. |
| Fotos | `FotoEspacio.java` (tabla `foto_espacio`): `espacioId`, `rutaArchivo` (`espacios/<UUID>`), `tipoMime`, `orden` 1–5 | La principal es `orden = 1`. URL pública estable: `EspacioService.urlDe(foto)` → `/api/fotos/<UUID>` (`EspacioService.java:191-195`, **package-private `static`**). |
| Características | **No existen.** `caracteristica`/`espacio_caracteristica` son de HU-23 (Release 2, `docs/modelo-datos.md:24`). | Nada que mostrar. |
| Ubicación | Solo `direccion` (texto) y `zona`. Sin coordenadas. | El mapa interactivo está **descartado** (`docs/architecture.md` § Qué NO hacer). CA1 pide "dirección en texto". |
| Precio | `precioHora` único (RN-18). | Formato en el front: `formatoPrecio` de `textosEspacios.js`. |
| Disponibilidad | `HorarioSemanal`, `BloqueoFecha`, `FranjaHoraria`, `Bloque(horaInicio, estado)`, `EstadoBloque {LIBRE, RESERVADO, BLOQUEADO, PASADO}`, `VentanaReserva` (hoy…hoy+55), `CalculoDisponibilidad.bloquesDelDia(...)` (puro) | Ver § 3. |

### 1.2 Repositorios y servicios reutilizables

Regla de arquitectura: `busqueda` **nunca** lee repositorios de `espacios` ni de `cuentas`; usa las interfaces públicas
(`docs/architecture.md:153-176`, HU-10 DT-3).

- `B/espacios/service/CatalogoEspaciosService.java` (interfaz pública para `busqueda`, "HU-10, HU-11, HU-23", sin sesión):
  `esTipoValido`, `existeZona`, `publicados(tipo, zona) → List<EspacioPublicado>`, `fotosPrincipales(ids) → Map<Long,String>`.
  **No tiene** un método "un espacio publicado por id" con dirección, capacidad, descripción y todas sus fotos. HU-11
  debe agregarlo aquí (p. ej. `Optional<FichaPublica> publicado(Long id)`), implementado en `CatalogoEspaciosServiceImpl`.
- `B/espacios/service/EspacioPublicado.java`: record `(id, nombre, tipo, zona, zonaNombre, precioHora)`. No trae
  `direccion`, `capacidad`, `descripcion` ni fotos: o se amplía o se crea otro record de cruce (no es DTO de la API).
- `B/espacios/service/DisponibilidadService.java` (interfaz pública "HU-10, HU-11, HU-12"):
  `horasLibres(espacioId, fecha) → List<Integer>` y `conBloquesLibres(ids, fecha) → Set<Long>`.
- `EspacioRepository` (`B/espacios/repository/EspacioRepository.java`): `findById` (JPA), `findByIdAndArrendadorId`,
  `findParaActualizar` (lock, exige dueño), `buscarPublicados`, `buscarPublicadosExcluyendo`.
- `FotoEspacioRepository`: `findByEspacioIdOrderByOrdenAsc(espacioId)` ← lo que necesita la galería.
- `EspacioService.consultar/aDto` (`EspacioService.java:97-103, 180-189`) arma un `EspacioDTO` completo, pero **es del
  dueño** (exige rol y propiedad) e incluye `estado`, `creadoEn`, `actualizadoEn`: no sirve tal cual para lo público.

### 1.3 Endpoints públicos existentes

| Ruta | Dónde | DTO | Notas |
|------|-------|-----|-------|
| `GET /api/espacios?tipo=&zona=&fecha=&pagina=` | `B/busqueda/controller/BusquedaController.java:20-26` (ruta en `ReglasLimiteBusqueda.RUTA_BUSQUEDA`) | `ResultadoBusquedaDTO(pagina, resultados, hayMas)` con `ResultadoEspacioDTO(id, nombre, zona, zonaNombre, precioHora, fotoPrincipal)` | Sin dirección ni datos del arrendador "(la da HU-11 en la ficha)" (HU-10 R14, `specs/HU-10/design.md:434`). Validación en `BusquedaService` con parámetros `String`, `ValidacionException` por campo. |
| `GET /api/zonas` | `ZonaController.java` | `List<ZonaDTO>` | Público desde HU-10 DT-2; no cuenta en el cupo. |
| `GET /api/fotos/{uuid}` | `FotoPublicaController.java:32-40` | binario | Público si el espacio está `PUBLICADO` y el arrendador `ACTIVA`; si no, solo el dueño; si no, 404. `Cache-Control: public, max-age=3600`. No cuenta en el cupo. |

La ruta **`GET /api/espacios/{id}` está libre y reservada para HU-11** (HU-07 P9/DT-7, HU-10 P1/DT-1). HU-08 rechazó
exponer `/api/espacios/{id}/disponibilidad` para dejárselo a HU-11 (`specs/HU-08/design.md:788-790`).

Rutas del dueño que **no** sirven para lo público: `/api/arrendadores/actual/espacios/{id}` (+ `/horario`,
`/bloqueos`, `/disponibilidad`, `/fotos`).

### 1.4 Visibilidad de un espacio

- **Estado de publicación:** solo `PUBLICADO` (RN-15). Publicar (`EspacioService.publicar`, `:131-150`) exige
  `BORRADOR`, identidad `VERIFICADA` (HU-06) y ≥ 1 foto (HU-07 DT-3).
- **Identidad del arrendador (HU-06):** se comprueba **solo al publicar**. Un espacio ya `PUBLICADO` sigue visible
  aunque la identidad cambie después (no hay hoy un flujo que la revierta; HU-20/RN-02 la devolvería a
  "en revisión" en Release 2). Ni la búsqueda ni las fotos ni `horasLibres` miran `estado_identidad`.
- **Suspensión (HU-09 D1):** los espacios de un arrendador no `ACTIVA` conservan su estado pero no se muestran. Regla
  explícita para HU-11 (`docs/architecture.md:153-160`): "la ficha … pregunta `cuentaActiva(arrendadorId)` o hereda el
  filtro si usa `DisponibilidadService.horasLibres`". `CuentaService.cuentaActiva(Long)` (`CuentaService.java:88-92`,
  impl `CuentaServiceImpl.java:212-217`) no exige sesión. Ya lo aplican `FotoEspacioService.ver` (`:121-136`),
  `horasLibres` (`DisponibilidadServiceImpl.java:59-73`) y `CatalogoEspaciosServiceImpl.publicados` (`:51-70`).
- Patrón del 404 indistinguible: `FotoEspacioService.ver` responde el mismo `RecursoNoEncontradoException` para
  "no existe", "no publicado" y "arrendador no activo" (R55 de HU-07).

### 1.5 Datos del arrendador

- `usuario` (`V1_1`, `V1_3`): `correo`, `telefono` y `nombre` **en claro** (no cifrados). `perfil_arrendador`:
  `numero_documento`, `razon_social`, `direccion_fiscal` **cifrados** (`@Convert(ConvertidorCifrado)`,
  `PerfilArrendador.java:40-53`); `hash_documento` HMAC. Documentos de identidad en almacenamiento privado cifrado.
- RNF-SEG-16 / STRIDE (I) de HU-11: no exponer teléfono, correo ni documentos. `EspacioDTO` (dueño) ya excluye al
  arrendador (`EspacioDTO.java:7-9`, HU-07 R7) y `ResultadoEspacioDTO` también (HU-10 R14).
- **No existe** en `CuentaService` un método público que devuelva un nombre visible del arrendador. Si la ficha
  quisiera mostrar "Publicado por …", habría que agregarlo (decisión de producto, § Huecos). Ningún CA lo pide.
- `arrendadorId` tampoco debería salir en la respuesta (no lo hace ningún DTO público hoy).

### 1.6 404, rate limiting y seguridad de rutas públicas

- **404:** `RecursoNoEncontradoException` → `ManejadorGlobalErrores.recursoNoEncontrado` (`:69-72`) → 404 con
  `ErrorDTO.MENSAJE_RECURSO_NO_ENCONTRADO` = "No encontramos lo que buscas o el enlace expiró." Un `{id}` no numérico
  en `@PathVariable Long` lanza `MethodArgumentTypeMismatchException` → cae en `inesperado` (`:87-96`); como es un
  `ErrorResponse` 4xx de Spring, conserva su código (400) con mensaje genérico. Si se quiere 404 para ids no numéricos,
  hay que recibir el id como `String` (como HU-10 recibe parámetros como texto) — decisión técnica.
- **400 por campo:** `ValidacionException` (varios campos) o `ReglaNegocioException(campo, mensaje)` (uno).
  Textos de fecha ya fijados: `"Elige una fecha válida."` y `"Elige una fecha entre hoy y las próximas 8 semanas."`
  (`CalendarioEspacioService.java:50-51`, copiados en `BusquedaService.java:37-38`; `BusquedaServiceTest`
  comprueba que no se separen).
- **Rate limiting:** `B/busqueda/controller/LimiteCatalogo.java` (grupo `"catalogo"`, `regla(metodo, patron)`),
  `ReglasLimiteBusqueda.java` (`@Bean` para `GET /api/espacios`). `ReglaLimite` acepta patrones `PathPattern`
  (`ReglaLimite.java:22-29, 55-66`), `FiltroLimiteIntentos` cuenta **un intento por grupo** por solicitud
  (`:38-46`) y responde 429 con `ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS`. Configuración:
  `espacioya.limite.catalogo.maximo=${LIMITE_CATALOGO_MAXIMO:120}` y `.ventana=PT1M` (`application.properties`, final).
  §9.9 de `docs/requerimientos.md:296`: "un solo cupo compartido por la búsqueda (HU-10), **la ficha y su
  disponibilidad (HU-11)** y los filtros (HU-23)". HU-11 solo registra `@Bean`s con `limite.regla("GET", …)`.
  **Ojo:** `"/api/espacios/{id}"` como `PathPattern` coincide solo con un segmento; una ruta
  `/api/espacios/{id}/disponibilidad` necesita su **propia** regla del mismo grupo.
- **Seguridad:** `ConfiguracionSeguridad.java:43-60`. Hoy son `permitAll`: `GET /api/espacios` (exacto),
  `GET /api/zonas`, `GET /api/fotos/*`, etc.; el resto `authenticated()`. HU-11 debe añadir `permitAll` para
  `GET /api/espacios/*` y, si existe, `GET /api/espacios/*/disponibilidad`. Un Bearer inválido en ruta pública se
  trata como anónimo (HU-10 R12, `BusquedaControllerIT.tokenInvalidoComoAnonimo`). CORS ya cubre `/api/**`.
- **Auditoría:** HU-10 DT-17 decidió **no auditar** lecturas públicas (vector de DoS). RNF-SEG-12 no lo pide para la ficha.

## 2. Migraciones Flyway vs `docs/modelo-datos.md`

| Migración | Tablas | Relevancia |
|-----------|--------|-----------|
| `V1_1__usuarios.sql` | `usuario` (correo, rol, estado ACTIVA/SUSPENDIDA/ELIMINADA…) | estado del arrendador |
| `V1_3__perfil_arrendador.sql` | `usuario.nombre`, `usuario.telefono`; `perfil_arrendador` (campos cifrados, `estado_identidad`) | datos que **no** se exponen |
| `V2_2__espacio.sql` | `zona` (50 distritos sembrados), `espacio`, `foto_espacio` | ficha y galería |
| `V2_4__disponibilidad.sql` | `horario_semanal`, `bloqueo_fecha` | bloques del día |
| `V3_1__suspension_cuenta.sql` | historial de suspensiones | no se usa |
| `V3_2__indices_busqueda.sql` | `ix_espacio_busqueda (estado, tipo, zona, precio_hora, id)` | la ficha busca por PK, no necesita índice |

- Coinciden con `docs/modelo-datos.md:13-23`. La ficha **no necesita migración nueva** si solo lee lo existente
  (por PK de `espacio` y `ix`/`uq` de `foto_espacio (espacio_id, orden)`).
- **Reseñas:** `resena` (id, reserva_id único, usuario_id, espacio_id, calificacion 1–5, comentario, estado
  VISIBLE/OCULTA) solo existe en el **modelo documental** (`docs/modelo-datos.md:31, 46, 85`) y está marcada como
  tabla de **Release 2** (`:93`). **No hay migración ni entidad.** RF-C-08 (HU-24, `pending`) dice que la reseña
  "aparece en la ficha y actualiza el promedio": eso lo agregará HU-24 sobre la ficha de HU-11.
- `reserva`/`reserva_bloque` (HU-12) tampoco existen aún; la disponibilidad trata todas las fechas como sin reservas
  (puerto sin bean, HU-08 R21).
- Siguiente número libre para una eventual migración de sprint 3: `V3_3` (ver § Huecos, técnico).

## 3. Disponibilidad de HU-08 para la ficha

- **Única entrada pública hoy:** `DisponibilidadService.horasLibres(espacioId, fecha)` (`DisponibilidadServiceImpl.java:57-73`):
  devuelve las horas de inicio `LIBRE` ascendentes; **lista vacía** si la fecha está fuera de la ventana, el espacio no
  existe, no está `PUBLICADO` o su arrendador no está `ACTIVA`. No lanza errores ni distingue esos casos.
- Cálculo: `bloquesDelDia(espacioId, fecha)` (`:132-145`, **package-private**) = franjas del día ISO
  (`horarios.findByEspacioIdAndDiaSemanaOrderByHoraInicioAsc`) − bloqueo de la fecha − horas del puerto
  `ReservasDelEspacio` (si hay bean) − horas ya empezadas (`ZonaHoraria.ahora(reloj)`, Lima), vía
  `CalculoDisponibilidad.bloquesDelDia` con precedencia BLOQUEADO > RESERVADO > PASADO > LIBRE.
- Ventana: `VentanaReserva.desde(ZonaHoraria.hoy(reloj))` → hoy…hoy+55; `contiene(fecha)`. `DisponibilidadServiceImpl.ventana()`
  es package-private; desde `busqueda` se usa `VentanaReserva` + `ZonaHoraria` + `Clock` como hace
  `BusquedaService.java:65`.
- **Calendario:** no existe un método público del calendario (horario semanal o fechas bloqueadas) para terceros;
  las rutas `/horario` y `/bloqueos` son del dueño. Para CA2 basta "elegir una fecha → bloques de ese día".
- Guía escrita para HU-11 (`docs/architecture.md:205-209`): "necesita su propia ruta pública de disponibilidad (bajo
  `busqueda`, con el rate limiting de HU-10). Con `horasLibres` basta para el CA2; si además quiere mostrar los
  ocupados, agrega un método que devuelva los `Bloque` con su estado, sin `PASADO`/`BLOQUEADO`/`RESERVADO`
  distinguidos hacia afuera (todos "no disponible", RNF-SEG-16)."
- Validación de fecha recomendada: mismo patrón que HU-10 (`BusquedaService.fecha` estricto `LocalDate.parse`, luego
  ventana), con los textos de HU-08 → 400 en el campo `fecha`. Con `horasLibres` solo, una fecha fuera de ventana
  daría `[]` en silencio.
- Riesgo a resolver en design: `horasLibres` hace su propio `findById` + `cuentaActiva`; si la ruta de disponibilidad
  responde `[]` para un espacio invisible, revela menos que un 404 pero no permite distinguir "espacio no existe" de
  "sin horario". Decidir si la ruta de disponibilidad responde 404 para espacios no visibles (consistencia con la ficha).

## 4. Tests a imitar

- **IT de controlador público:** `T/busqueda/controller/BusquedaControllerIT.java`.
  - `@SpringBootTest @AutoConfigureMockMvc @Import({TestcontainersConfiguration.class, …Dobles.class})`
    (PostgreSQL real con Testcontainers).
  - Reloj fijo: `RelojControlable` `@Primary` en `2026-10-28T15:00:00Z` (miércoles 10:00 Lima); `LUNES = 2026-11-02`.
  - Doble del puerto `ReservasFalsas implements ReservasDelEspacio` con `cargar(...)` y `limpiar()`.
  - `@BeforeEach` borra tablas en orden (`suspension_cuenta`, `foto_espacio`, `espacio`, `documento_identidad`,
    `sesion`, `auditoria`, `perfil_arrendador`, `usuario`) — si HU-11 inserta en `horario_semanal`/`bloqueo_fecha`,
    se borran en cascada con `espacio`.
  - Datos por SQL directo (`jdbc`): `espacio(...)`, `franja(...)`, `foto(...)` con UUID; arrendador con
    `Usuario.registrarArrendador` + `PerfilArrendador.crear`. Estados forzados por SQL (`PAUSADO`, `SUSPENDIDA`…).
  - **IP única por test** (`peticion.setRemoteAddr`) para no compartir el cupo: HU-10 usa `10.10.x.x`,
    `LimiteCatalogoIT` `10.11.x.x`; HU-01…09 usan `10.1`…`10.9`. HU-11 debería tomar otro rango (p. ej. `10.12.x.x`).
  - Pruebas tipo de HU-10 que HU-11 replica: excluye no publicados (R3), excluye arrendadores no activos (R4), horas ya
    empezadas hoy (R8), token inválido = anónimo (R12), sin foto principal (R16), fecha inválida/fuera de ventana
    (R25, R26), cupo por defecto y grupo (R39, R41).
  - Comprobación de minimización de datos: los datos de prueba usan dirección "Av. Secreta 123", correo y teléfono
    reconocibles, y el test verifica qué campos salen (en HU-11 la dirección **sí** debe salir; correo/teléfono/
    documento/razón social no).
  - `@DisplayName("HU-10 Rn: …")` con el id del requisito: trazabilidad exigida por `docs/specs.md`.
- **Rate limiting:** `T/busqueda/controller/LimiteCatalogoIT.java` (cupo reducido a 3 por propiedades de test, 4.ª
  → 429, ventana deslizante con `reloj`, cupo compartido entre rutas del grupo) y `LimiteCatalogoTest.java` (unitaria).
  HU-11 debe probar que ficha + disponibilidad + búsqueda **comparten** el mismo cupo.
- **Unitarias de servicio:** `T/busqueda/service/BusquedaServiceTest.java` (`@ExtendWith(MockitoExtension.class)`,
  `@Mock` de `BusquedaEspacios` y `CatalogoEspaciosService`, `Clock` fijo; `verifyNoInteractions` cuando la entrada es
  inválida — R29). `T/espacios/service/CatalogoEspaciosServiceImplTest.java` (mocks de repos + `CuentaService`).
  `T/espacios/service/DisponibilidadServiceImplTest.java` / `DisponibilidadServiceImplIT.java` (sin bean del puerto, R21).
- **HU-07/08:** `T/espacios/controller/EspaciosControllerSoporte.java` (base abstracta con `ReservasDelEspacioFalso`
  `@Primary`, helpers `crearEspacio`, `publicar`, `subir`, `forzarEstado`, `forzarIdentidad`, `fotosEnBd`) y
  `FotoPublicaControllerIT.java` (foto pública vs. privada, 404 indistinguible, cache-control).
- **Frontend:** no hay tests (ni Vitest ni Testing Library en `3_frontend/package.json`); solo `eslint` y `vite build`.

## 5. Frontend

- **Búsqueda (HU-10):** `3_frontend/src/pages/busqueda/BuscarEspacios.jsx`. Filtros en la URL
  (`?tipo=&zona=&fecha=&pagina=`). Cada resultado es una `Tarjeta` con `Imagen`, `zonaNombre` y precio
  (`:146-161`). **No enlaza a ninguna ficha** (HU-10 D12: "sin enlace a la ficha hasta HU-11"). HU-11 debe agregar el
  enlace (p. ej. `Link` a `/espacios/:id?fecha=<fecha de la búsqueda>`); `Tarjeta` acepta `titulo`, `children`,
  `acciones`, y no tiene prop de enlace.
- `pages/busqueda/textosBusqueda.js`: `ventanaBusqueda()` → `{hoy, hasta}` en hora de Lima (min/max del campo
  fecha); reutilizable para la ficha.
- **API:** `src/api/busqueda.js` (`buscarEspacios`, `peticion(..., { autenticada: false })`); `src/api/cliente.js`
  (`peticion`, `urlApi(ruta)` para `<img src>`, errores `{estado, mensaje, errores}`); `src/api/espacios.js` (todas
  del dueño salvo `listarZonas`; `consultarDisponibilidad(id, fecha)` es la del **dueño**). HU-11 agregaría p. ej.
  `obtenerEspacio(id)` y `consultarBloques(id, fecha)` en `api/busqueda.js`, sin token.
- **Kit `components/ui/`:** `Alerta`, `Boton`, `Campo` (con `tipo="date"`, `min`, `max`), `EtiquetaEstado`,
  `Imagen` (4:3, "Sin foto"), `Modal`, `SelectorBloques`, `Tarjeta`. Contrato en `docs/frontend.md:170-180`.
  - `SelectorBloques` (`components/ui/SelectorBloques.jsx`): props `bloques [{horaInicio, horaFin, libre, detalle}]`,
    `seleccionados`, `onCambiar` (sin él, solo lectura). **Lista vacía → no renderiza nada**: la pantalla debe mostrar
    su propio texto "no hay bloques libres". `docs/frontend.md:148-163`: en la ficha pública el `detalle` de un
    ocupado es genérico ("No disponible").
  - `textosEspacios.js`: `TIPOS_ESPACIO`, `nombreTipo`, `formatoPrecio`, `formatoHora`, `TEXTO_ESTADO_BLOQUE`
    (Reservado/Bloqueado/Ya pasó — **no** usar en la ficha pública).
- **Galería / calendario:** no existe componente de galería (solo `Imagen` individual y `FotoDelEspacio`, que es para
  borradores con token). No hay calendario: la fecha se elige con `Campo tipo="date"` (patrón de
  `DisponibilidadEspacio.jsx` y `BuscarEspacios.jsx`). `docs/frontend.md`: "Si falta uno, se agrega al kit, no se
  improvisa en la pantalla". Más de 5 fotos y video están descartados.
- **Rutas (`src/App.jsx:82-193`):** `/`, `/buscar` (pública), `/registro`, `/registro-arrendador`, `/privacidad`,
  `/iniciar-sesion`, `/recuperar-contrasena`, `/restablecer-contrasena`, `/panel/**` y `/admin/**` (con
  `RutaProtegida`). **No hay ruta de ficha.** HU-11 agregaría una pública (p. ej. `/espacios/:id`), que HU-12 usará
  para "volver a la ficha" tras el login (HU-12 CA4).

## 6. Decisiones previas y reglas que afectan a HU-11

- **HU-07:** P9/DT-7 — `GET /api/espacios/{id}` queda para la ficha pública. P3/DT-3 — publicar exige ≥ 1 foto
  porque la ficha muestra fotos. Fotos públicas sin cifrar, URL estable `/api/fotos/<UUID>`, caché 1 h
  (`progress/history.md:368`). Descripción obligatoria "porque la ficha la muestra" (`specs/HU-07/design.md:1019`).
- **HU-08:** P9/DT-8 — `DisponibilidadService.horasLibres` como interfaz pública; la ruta pública de disponibilidad
  queda para HU-11 (`specs/HU-08/design.md:32, 788-790`). P4 — bloques de 1 h, horas enteras. DT-1 — "hoy" en Lima.
  Pendiente en `progress/history.md:530`: "Revisar con Adriana Callupe (dueña de HU-11) los props de
  `SelectorBloques` antes de su spec".
- **HU-09 D1:** avisar a HU-11: ocultar espacios de arrendadores no `ACTIVA` con `cuentaActiva(id)` o heredar de
  `horasLibres` (`progress/history.md:585`, `docs/architecture.md:153`).
- **HU-10:** DT-1 (HU-11 cuelga de `/api/espacios/{id}`), DT-3 (solo interfaces públicas de `espacios`), DT-10
  (parámetros como texto, 400 por campo), DT-13 (`LimiteCatalogo.regla` para HU-11), DT-17 (sin auditoría de
  lecturas públicas), R14 (dirección en la ficha, no en la búsqueda), D12 (enlace a la ficha lo pone HU-11).
  Observación abierta: `App.jsx:87-90`, espaciado de la portada.
- **Reglas y RNF:** RF-C-04, RF-C-05; RNF-SEG-16 (minimización); RNF-SEG-17 + §9.9 (120/min por IP compartido); RN-04
  (ventana de 8 semanas, bloques calculados); RN-06 (política fija: ≥ 48 h → 100 %, < 48 h → 0 %, arrendador cancela →
  100 %; **no configurable**); RN-15 (solo publicados de arrendadores no suspendidos); RN-18 (precio único).
  Fuera de alcance: mapa interactivo, galería > 5 fotos y video, favoritos, política configurable.
- **Release 2 que tocará la ficha:** HU-23 (características y filtros, Decorator sobre `BusquedaEspacios`; comparte
  cupo), HU-24 (reseñas y promedio en la ficha), HU-27 (reportar espacio desde la ficha, estado `OCULTO`).

---

## Huecos / decisiones abiertas

### De producto (las toma la dueña, Adriana Callupe)

1. **Texto de la política de cancelación (CA1).** ¿Solo "Cancelación gratuita hasta 48 h antes" o las tres reglas de
   RN-06 (≥ 48 h 100 %, < 48 h 0 %, si cancela el arrendador 100 %)? Al ser fija (no configurable), puede ser texto
   estático del frontend o venir del backend.
2. **¿Qué se muestra del arrendador?** Nada (lo mínimo, como HU-10) o solo un nombre visible (`usuario.nombre`, en
   claro). Teléfono, correo, documento y razón social nunca (RNF-SEG-16).
3. **Bloques ocupados en CA2.** ¿Solo los libres (lo que pide el CA, con `horasLibres`) o todo el horario del día con
   los ocupados como "No disponible" genérico? Lo segundo exige un método nuevo en `DisponibilidadService`.
4. **Fecha inicial de la ficha.** ¿Hoy en Lima por defecto, o la fecha de la búsqueda (pasada en la URL, p. ej.
   `/espacios/:id?fecha=`)? ¿Se guarda la fecha elegida en la URL para compartir el enlace?
5. **¿Se pueden seleccionar bloques ya en la ficha** (`SelectorBloques` con `onCambiar`) o es solo lectura y la
   selección empieza en HU-12? Afecta el contrato con Eduardo Saldaña (HU-12 CA4 vuelve a la ficha tras el login).
6. **Botón "Reservar".** ¿HU-11 lo muestra (deshabilitado o sin efecto) o lo agrega HU-12?
7. **Galería.** Presentación de las hasta 5 fotos: lista/rejilla de `Imagen` o foto principal grande + miniaturas.
   Si se necesita un componente nuevo (`Galeria`), va al kit (`docs/frontend.md`).
8. **Datos extra en la ficha:** ¿mostrar `tipo` (nombre legible) y `zonaNombre` además de la dirección? (No están en
   CA1, pero ya están disponibles y no son sensibles.)
9. **Mensaje cuando el espacio no está disponible** (no existe, pausado, oculto, de arrendador suspendido): ¿el 404
   genérico ("No encontramos lo que buscas…") con enlace a "Buscar espacios" basta?
10. **Mensaje sin bloques libres** en la fecha elegida (p. ej. "No hay bloques libres este día. Prueba con otra fecha.").
11. Revisar los props de `SelectorBloques` (pendiente de HU-08, `progress/history.md:530`).

### Técnicas (las resuelve el spec/design)

1. **Contrato público nuevo en `espacios`:** agregar a `CatalogoEspaciosService` un método por id que devuelva solo
   espacios `PUBLICADO` de arrendadores `ACTIVA` (con `cuentaActiva`), con dirección, capacidad, descripción, zona y
   fotos ordenadas; nuevo record de cruce (o ampliar `EspacioPublicado`, que hoy usa HU-10/HU-23). `EspacioService.urlDe`
   es package-private: reutilizarlo dentro de `espacios`.
2. **DTO público de la ficha** en `busqueda/dto` (p. ej. `FichaEspacioPublicaDTO`): sin `arrendadorId`, `estado`,
   `creadoEn`, `actualizadoEn` ni claves de almacenamiento. Ojo con el nombre: `espacios/dto/FichaEspacioDTO` ya existe
   (es la **entrada** de crear/editar de HU-07).
3. **Rutas:** `GET /api/espacios/{id}` y `GET /api/espacios/{id}/disponibilidad?fecha=` (o `…/bloques`), ambas en
   `busqueda`, ambas `permitAll` en `ConfiguracionSeguridad` (que hoy solo tiene `GET /api/espacios` exacto) y **dos**
   reglas en el grupo `catalogo` (un `PathPattern` `/api/espacios/{id}` no cubre la subruta). ¿Una sola llamada
   (ficha + bloques de hoy) o dos?
4. **404 indistinguible** para inexistente / no publicado / arrendador no activo, como `FotoEspacioService.ver`.
   ¿Id no numérico → 404 (recibir `String`) o el 400 genérico actual de Spring?
5. **Disponibilidad:** `horasLibres` no distingue "espacio invisible" de "sin horario" ni valida la fecha. Validar la
   fecha en `busqueda` (estricto + ventana, textos de HU-08, 400 en `fecha`) y decidir si la ruta de bloques primero
   comprueba visibilidad (404) o confía en `[]`. Si se muestran ocupados, nuevo método público (p. ej.
   `bloquesPublicos(id, fecha)`) que colapse `RESERVADO/BLOQUEADO/PASADO` en "no disponible".
6. **Fecha ausente** en la ruta de bloques: ¿400 o hoy por defecto?
7. **Caché HTTP:** la ficha y los bloques no deberían cachearse (`no-store`, como el resto de la API JSON; Spring
   Security ya lo añade) — los bloques cambian con reservas.
8. **Sin auditoría** (precedente HU-10 DT-17). **Sin migración** nueva (si hiciera falta, `V3_3`).
9. **IT:** rango de IP propio (p. ej. `10.12.x.x`), prueba de cupo compartido ficha + disponibilidad + búsqueda,
   prueba de minimización (que no salgan correo, teléfono, documento, razón social ni `arrendadorId`).
10. **Frontend:** ruta pública nueva en `App.jsx` (p. ej. `/espacios/:id`), enlace desde la `Tarjeta` de
    `BuscarEspacios.jsx` (`Tarjeta` no tiene prop de enlace: usar `acciones` o envolver el título), funciones nuevas en
    `api/busqueda.js` sin token, textos en `pages/busqueda/textosBusqueda.js` (o archivo propio), `ventanaBusqueda()`
    para el `min`/`max` del `Campo date`, aviso propio cuando `SelectorBloques` recibe lista vacía, y cancelar
    respuestas viejas al cambiar rápido de fecha (patrón `activo` de `BuscarEspacios.jsx:55-68`).
11. **Coordinación:** HU-12 (Eduardo Saldaña) usará la ruta de la ficha para CA4 y probablemente la ruta de bloques;
    HU-23/HU-24 (Release 2) extenderán la ficha con características y reseñas.
