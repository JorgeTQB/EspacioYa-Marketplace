# Exploración HU-15 · Cancelación de reservas (★ Strategy)

> Explorador de solo lectura, 2026-10-31, rama `feature/HU-15-cancelacion-reservas`. Rutas relativas a la raíz del
> repo. Paquete base Java: `2_Backend/src/main/java/pe/edu/ulima/espacioya/` (abreviado `…/`).

## Archivos analizados

- Historias: `docs/historias/HU-15.md`, `docs/historias/HU-16.md`.
- Docs: `docs/architecture.md` (§ principios 4–9, Puertos entre módulos, "Reservas: qué deja HU-12", "Pagos: qué deja
  HU-13", "Mis reservas: qué deja HU-14", estados, patrones por historia, qué NO hacer), `docs/modelo-datos.md`
  (tablas `reserva`, `pago`, `reembolso`), `docs/requerimientos.md` (RF-D-07…09, RF-E-05/06, RNF-SEG-07/12/19,
  RNF-USA-04, RN-03, RN-06, RN-07, RN-14).
- Specs: `specs/HU-14/{design,tasks}.md` (§ 11.4 avisos a HU-15), greps en `specs/HU-0x`, `specs/HU-13`.
- Backend `reservas`, `pagos`, `comun`, `cuentas/service`, `espacios/service`; migraciones; tests de `reservas` y `pagos`.
- Frontend `3_frontend/src/pages/reservas/*`, `api/reservas.js`, `api/cliente.js`, `components/ui/Modal.jsx`,
  `EtiquetaEstado.jsx`, `App.jsx`, `pages/busqueda/textosFicha.js`.

---

## 1. Backend reservas

**Entidad** `…/reservas/domain/Reserva.java` (`@Table("reserva")`): `id`, `usuarioId`, `espacioId`, `estado`
(`EstadoReserva`, `@Enumerated(STRING)`), `precioHoraAplicado`, `montoTotal`, `creadaEn`, `expiraEn`. Métodos:
`pendiente(...)` (fábrica), `esVigente(ahora)`, `estaVencida(ahora)`, `estadoEn(ahora)`, `segundosRestantes`,
`expirar(ahora)`, `confirmar(ahora)` (lanzan `IllegalStateException` si el estado no permite la transición). **No hay
método `cancelar` ni campos `cancelada_por` / `cancelada_en`** (el comentario de `V3_3__reservas.sql` y
`docs/modelo-datos.md` dicen que los agrega HU-15 con su migración).

**Estados** `…/reservas/domain/EstadoReserva.java`: `PENDIENTE_PAGO, CONFIRMADA, EXPIRADA, CANCELADA` — **`CANCELADA` ya
existe** en el enum y en el `CHECK ck_reserva_estado`. Transición permitida por docs: solo `CONFIRMADA → CANCELADA`
(`architecture.md` l. 396–397: `PENDIENTE_PAGO → CANCELADA` no existe, HU-12 S7). Frontend `EtiquetaEstado.jsx` ya
mapea `CANCELADA: { texto: 'Cancelada', tipo: 'neutro' }`.

**`ReservaBloque`** `…/reservas/domain/ReservaBloque.java`: `reservaId`, `espacioId`, `fecha` (`LocalDate`),
`horaInicio` (short 0–23), `activo`. Sin setter de `activo`: se libera por query.

**Repositorios / DAO**
- `…/reservas/repository/ReservaRepository.java`: `findByIdAndUsuarioId`, `findByUsuarioId`,
  `findByEspacioIdInAndEstado`, `contarVigentes`, `findIdsVencidas`, **`findParaActualizar(id)`
  (`PESSIMISTIC_WRITE`)**, **`findEspacioIdById(id)`** (proyección, para no cachear la entidad antes del bloqueo),
  `findVencidasQueRetienen`.
- `…/reservas/repository/ReservaBloqueRepository.java`: `findByReservaIdOrderByHoraInicioAsc(reservaId)`,
  `horarios(reservaIds)`, **`desactivarDeReserva(reservaId)`** (`@Modifying(flushAutomatically=true)`, `UPDATE …
  activo=false`), `bloquesActivos`, `bloquesActivosDelDia`.
- `…/reservas/repository/ReservaDAO.java` + `ReservaDAOJpa.java` (★ DAO HU-14): `delUsuario(usuarioId)`,
  `confirmadasDeEspacios(espacioIds)`. Toda consulta con el dueño en la firma. `ReservaConHorario` (record:
  `reserva, fecha, horaInicio, horaFin`; `inicio()`, `fin()`, `esProxima(ahora, ahoraLocal)`).

**Servicios**
- `…/reservas/service/ReservaService.java` (interfaz pública) / `service/impl/ReservaServiceImpl.java`:
  `crear(usuario, datos, ip)` (rol `USUARIO`; `TransactionTemplate` sin `@Transactional` para traducir
  `uq_reserva_bloque_activo`), `consultar(usuario, id)` (id como texto, `idValido` → 404 si mal escrito/ajeno, DT-9).
  Constantes `ACCION_RESERVA_CREADA`, `ENTIDAD_RESERVA = "reserva"`.
- `…/reservas/service/ConsultaReservasService.java` (HU-14): `misReservas` (rol `USUARIO`) y `reservasDeMisEspacios`
  (rol `ARRENDADOR`; espacios con `CatalogoEspaciosService.nombresDelArrendador(usuarioId)`), `exigirRol` →
  `AccesoDenegadoException` (403) antes de validar. Solo lectura, sin auditoría.
- `…/reservas/service/ConversionReservas.java`: única fuente de `ReservaDTO`.
- `…/reservas/service/ExpiracionReservas.java`: patrón de cambio de estado + `bloques.desactivarDeReserva` +
  auditoría (`RESERVA_EXPIRADA`, `origen=PROCESO`).
- `…/reservas/service/impl/ReservasPorCobrarImpl.java` (implementa puerto `pagos.service.puertos.ReservasPorCobrar`):
  `bloquear(reservaId)` = `findEspacioIdById` → `disponibilidad.bloquearEspacio(espacioId)` (devuelve
  `EspacioBloqueado(arrendadorId, nombre)`) → `findParaActualizar` → horario → `ahora` tras los bloqueos. **Es la
  plantilla exacta para cancelar** (orden `espacio → reserva`, y da el `arrendadorId` para validar propiedad).

**Controladores / endpoints**
- `…/reservas/controller/ReservaController.java` `@RequestMapping("/api/reservas")`: `POST` (crear),
  `GET` (mis reservas, `?grupo&pagina`), `GET /{id}` (consultar, id `String`).
- `…/reservas/controller/ReservasDeMisEspaciosController.java` `@RequestMapping("/api/arrendadores/actual/reservas")`:
  `GET` (`?grupo&pagina`).
- Pagos cuelga de la reserva: `/api/reservas/{id}/pago` (`PagoController`), `/api/reservas/{id}/pago/simulacion`.

**DTOs** `…/reservas/dto/`: `ReservaDTO(id, espacioId, espacioNombre, estado, fecha, horaInicio, horaFin, precioHora,
montoTotal, expiraEn, segundosRestantes)` (HU-14 dice que HU-24/26 pueden **ampliarlo** sin quitar campos);
`ReservaDeMiEspacioDTO(id, espacioId, espacioNombre, usuarioNombre, estado, fecha, horaInicio, horaFin, montoTotal)`;
`PaginaReservasDTO<T>`; `CrearReservaDTO`.

**Usuario autenticado y propiedad**
- `@AuthenticationPrincipal UsuarioAutenticado usuario` — record `…/comun/seguridad/UsuarioAutenticado(usuarioId,
  sesionId, rol)`; rol es `String` (`"USUARIO"`, `"ARRENDADOR"`, `"ADMIN"`). Se rellena en
  `comun/seguridad/FiltroAutenticacionSesion`. IP con `comun/web/IpCliente.de(peticion)`.
- Usuario: `reservas.findByIdAndUsuarioId(id, usuarioId)` → 404 si es ajena (no 403, para no revelar existencia).
- Arrendador: no hay método directo "reserva de mi espacio". Opciones existentes: `EspacioBloqueado.arrendadorId()`
  (tras `bloquearEspacio`) comparado con `usuario.usuarioId()`, o `CatalogoEspaciosService.nombresDelArrendador(id)`
  (mapa de sus espacios). HU-14 § 11.4: si HU-15 necesita más, "amplía `confirmadasDeEspacios` o agrega un método al
  DAO, siempre con el dueño en la firma".
- HU-14 § 11.4: las tarjetas dejan libre `acciones` para "Cancelar" en las `CONFIRMADA` **próximas** (usuario y
  arrendador). Una `CANCELADA` cae en pasadas del usuario y **desaparece** de la lista del arrendador (solo
  `CONFIRMADA`, HU-14 R15).

## 2. Bloques / disponibilidad, 48 h y reloj

- Al reservar (HU-12): `ReservaServiceImpl.crearEnTransaccion` → `cuentas.bloquearCuenta` → `disponibilidad
  .retenerSiLibres` (bloqueo de `espacio`) → `liberarVencidas` → `save(Reserva.pendiente)` → `bloques.saveAllAndFlush`
  (filas `activo = true`). **Orden fijo de bloqueos `usuario → espacio → reserva`** (architecture.md l. 259).
- Al pagar (HU-13): `ReservasPorCobrarImpl.confirmar` solo cambia el estado; los bloques **siguen `activo = true`**.
- Ocupación: `ReservaBloqueRepository.bloquesActivos*` filtran `activo = true` **y** estado (`CONFIRMADA` o
  `PENDIENTE_PAGO` vigente) → una `CANCELADA` ya no ocupa para el puerto aunque su bloque siga activo
  (`ReservaControllerIT` l. 355 lo prueba). **Pero** el índice parcial `uq_reserva_bloque_activo (espacio_id, fecha,
  hora_inicio) WHERE activo` sí la cuenta: **para liberar hay que llamar `bloques.desactivarDeReserva(reservaId)`**
  dentro de la transacción (como `ExpiracionReservas.expirar`). Las filas se conservan (HU-14 muestra el horario de
  pasadas con filas inactivas).
- Inicio de la reserva: no hay columna; sale de `reserva_bloque` (`min(fecha)`, `min(hora_inicio)`), p. ej.
  `findByReservaIdOrderByHoraInicioAsc(id).getFirst()` o `ReservaConHorario.inicio()` (`LocalDateTime` local de Lima).
- Zona horaria: `…/comun/configuracion/ZonaHoraria.java` (`LIMA = America/Lima`, `hoy(reloj)`, `ahora(reloj)`); el
  `Clock` (bean de `comun/configuracion/ConfiguracionReloj.java`) va en UTC; se lee siempre `reloj.instant()` /
  `Instant.now(reloj)`. Para 48 h: `fecha.atTime(horaInicio,0).atZone(LIMA).toInstant()` vs `Instant.now(reloj)`
  (Lima sin DST). Tests: `src/test/java/.../RelojControlable.java` (`@Primary` en los soportes IT, fijado en
  `2026-10-28T15:00:00Z` = miércoles 10:00 Lima).
- Política ya publicada al usuario como texto estático: `3_frontend/src/pages/busqueda/textosFicha.js`
  (`POLITICA_CANCELACION`, tres reglas de RN-06). Límite exacto "48 h o más" → 100 % (≥ 48 h).

## 3. Pagos (HU-13) y punto de extensión para el reembolso

- Entidad `…/pagos/domain/Pago.java` (`reservaId`, `idTransaccionPasarela`, `monto`, `estado`, `ultimos4`,
  `creadoEn`); `Pago.de(...)`. Sin setters de estado.
- `…/pagos/domain/EstadoPago.java`: `PAGADO, RECHAZADO, PAGADO_TARDIO, MONTO_DISTINTO, REEMBOLSADO, REEMBOLSO_FALLIDO`
  (los dos últimos ya están en enum y `CHECK`, sin uso: son de HU-16).
- `…/pagos/repository/PagoRepository.java`: `existsByIdTransaccionPasarela`, `countByReservaIdAndEstado…`,
  `findFirstByReservaIdOrderByCreadoEnDescIdDesc`. **Índice `uq_pago_reserva_pagado`: como mucho un `PAGADO` por
  reserva** (comentario de `V4_1__pago.sql`: "HU-15 y HU-19 leen este pago").
- Adapter: `…/pagos/service/pasarela/PasarelaPago.java` (`crearCheckout`, `cerrarCheckout`, `idNotificado`,
  `consultarPago`), implementaciones `MercadoPagoAdapter`, `PasarelaSimulada` (`@ConditionalOnProperty`). **No hay
  `reembolsar`**: architecture.md l. 285 "HU-16 le agrega `reembolsar`".
- Facade: `…/pagos/service/CheckoutFacade.java` (entrada de `PagoController`). Webhook: `WebhookPagoService`.
- **Reembolso: no existe nada** (ni tabla `reembolso`, ni clase, ni Command). `docs/modelo-datos.md`: tabla
  `reembolso(id, pago_id, monto, origen CANCELACION|RECLAMO, estado, intentos, creado_en)` es de HU-16/HU-26;
  restricción "un reembolso por pago y origen" es de HU-16. architecture.md l. 425: HU-16 = `pagos`:
  `SolicitudReembolso` con `ejecutar()` (Command).
- **No existe `PagoService`.** architecture.md l. 59–60 y l. 301: "`reservas` usa `PagoService`, no
  `PagoRepository`" y "**HU-15 crea `PagoService`**: nadie lo usaba aún". Dirección `reservas → pagos` permitida
  (`comun ← cuentas ← espacios ← reservas → pagos → cuentas`); `pagos` ya depende de `reservas` solo vía el puerto
  `ReservasPorCobrar` (implementado en `reservas`), así que `reservas` puede depender de la interfaz `PagoService`.
- Filas `sim-`: con la pasarela simulada `id_transaccion_pasarela` empieza por `sim-`; HU-15/16 "deben filtrarlas o
  limpiarlas al conectar Mercado Pago (RS-5)".
- **Puntos de extensión posibles** para "disparar el reembolso" sin implementar HU-16:
  1. **Interfaz `pagos.service.PagoService`** (la que pide architecture.md) con, p. ej., `montoPagado(reservaId)` →
     `Optional<BigDecimal>` (lee el `PAGADO`) y `solicitarReembolso(reservaId, monto, origen)` (`MANDATORY`, se une a
     la transacción de la cancelación). En HU-15 la implementación solo registra/audita la solicitud (o deja una
     intención persistida); HU-16 la completa con el Command + `PasarelaPago.reembolsar` + tabla `reembolso`.
  2. Evento de dominio Spring (`ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`) —
     **no hay precedente en el repo** (se usa `DespuesDelCommit` en su lugar); añadiría un mecanismo nuevo.
  3. Puerto en `reservas/service/puertos/` implementado por `pagos` — **no aplica**: la dirección `reservas → pagos` ya
     es la permitida; un puerto aquí sería innecesario.

## 4. Correo

- Puerto `…/cuentas/service/NotificadorCorreo.java` (vive en `cuentas`; "HU-25 lo moverá a `comun/correo`"). Métodos
  actuales: `notificarVerificacion`, `notificarRecuperacion`, `notificarIdentidadAprobada/Rechazada`,
  `notificarCuentaSuspendida/Reactivada`, `notificarPagoConfirmado(correo, ComprobantePago)`,
  `notificarReservaPagada(correoArrendador, AvisoReservaPagada)`.
- Única implementación: `…/cuentas/service/NotificadorCorreoSimulado.java` (una línea INFO por método, **sin correo ni
  datos** en el log, Ley 29733).
- Records de contenido en `cuentas/service`: `ComprobantePago`, `AvisoReservaPagada(espacio, fecha, horaInicio,
  horaFin)` — sin datos del usuario ni monto al arrendador (RNF-SEG-16).
- Uso HU-13: `…/pagos/service/AvisosPago.programar(reserva, pago)`: lee `CuentaService.correoDe(usuarioId)` y
  `correoDe(arrendadorId)` **dentro** de la transacción y envía con `DespuesDelCommit.ejecutar(...)` (uno por
  sincronización: si falla uno, el otro sale). HU-04/06/09 usan el mismo patrón (después del commit).
- `…/comun/transaccion/DespuesDelCommit.ejecutar(Runnable)`: exige transacción activa; atrapa y loguea el fallo.
- Agregar un método a `NotificadorCorreo` obliga a implementarlo en el simulado **y en los 6 dobles de test**:
  `pagos/controller/PagosSoporte.java` (`NotificadorFalso`), `cuentas/controller/UsuarioControllerIT`,
  `RecuperacionContrasenaControllerIT`, `ArrendadorControllerIT`, `administracion/controller/RevisionIdentidadControllerIT`,
  `CuentaAdminControllerIT`. `ReservasControllerSoporte` **no** tiene notificador falso (usa el simulado).
- RF-D-11 / RN-14: los correos de cancelación **siempre** se envían (no son desactivables; HU-25).

## 5. Auditoría (STRIDE R)

- `…/comun/auditoria/AuditoriaService.registrar(EventoAuditoria)` (`@Transactional`, se une a la del llamador; fecha con
  `Clock`). `EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle)`. Tabla `auditoria`
  (`V1_2__auditoria.sql`): `usuario_id` (FK, nulo para procesos), `accion` (60), `entidad` (60), `entidad_id`, `ip`
  (45), `fecha`, `detalle TEXT`.
- Convención: constante `ACCION_* = "RESERVA_CREADA"` pública en el servicio, `ENTIDAD_RESERVA = "reserva"`, detalle
  `clave=valor;clave=valor` (p. ej. `"espacioId=…;fecha=…;horas=…;monto=…"`, `"origen=WEBHOOK;espacioId=…"`). Montos con
  `toPlainString()`. Ejemplos: `ReservaServiceImpl.auditar`, `ExpiracionReservas.expirar`,
  `ReservasPorCobrarImpl.confirmar`, `WebhookPagoService.registrar`. HU-06/HU-09 auditan sus decisiones igual dentro de
  la transacción (además tienen tablas propias `revision_identidad`, `suspension_cuenta`).
- Para R de HU-15 ("quién, cuándo, monto"): `usuarioId` de la sesión, `ip`, y detalle con `canceladaPor=USUARIO|
  ARRENDADOR;espacioId;monto=…;porcentaje=…`; además las columnas nuevas `cancelada_por`/`cancelada_en` en `reserva`
  (y quizá `monto_reembolso`).

## 6. Migraciones Flyway

`2_Backend/src/main/resources/db/migration/`: `V1_1…V1_5`, `V2_1…V2_4`, `V3_1__suspension_cuenta`,
`V3_2__indices_busqueda`, `V3_3__reservas`, **`V4_1__pago`** (última). Convención `V<sprint>_<n>__<nombre_snake>.sql`;
HU-15 es Sprint 4 → **`V4_2__cancelacion_reserva.sql`** (HU-16, mismo sprint, tomará la siguiente; coordinar número).
Comentario breve en la cabecera; `CHECK` y nombres de restricción explícitos (`ck_…`, `uq_…`, `ix_…`).
- `reserva`: `estado VARCHAR(20)` con `ck_reserva_estado` (ya incluye `CANCELADA`), `monto_total NUMERIC(10,2)`,
  `creada_en`, `expira_en TIMESTAMPTZ`. Faltan `cancelada_por`, `cancelada_en` (previstos para HU-15). Un `CHECK` de
  coherencia (`estado = 'CANCELADA'` ⇔ columnas no nulas) sería consistente con el estilo.
- `pago`: `monto NUMERIC(10,2)`, `estado` con `ck_pago_estado`, `uq_pago_reserva_pagado` parcial.

## 7. Rate limiting y errores (`comun`)

- Errores `…/comun/error/`: `ReglaNegocioException(mensaje)` o `(campo, mensaje)` → 400; `ValidacionException(List<
  ErrorCampoDTO>)` → 400; `AccesoDenegadoException` → 403; `RecursoNoEncontradoException` → 404;
  `ServicioExternoException` → 503; `DemasiadosIntentosException` → 429; genérico 500. `ManejadorGlobalErrores`
  (`@RestControllerAdvice`). Formato `ErrorDTO(mensaje, errores[])` con constantes `ErrorDTO.MENSAJE_*`.
- Rate limiting `…/comun/limite/`: `ReglaLimite(metodo, rutaPatron, maximo, ventana[, grupo])` como `@Bean`/`@Component`
  recogidos por `FiltroLimiteIntentos` (**por IP**, no por usuario) con `LimitadorIntentos`. Ejemplos:
  `cuentas/controller/ReglasLimiteCuentas.java`, `busqueda/controller/LimiteCatalogo.java` (propiedades
  `espacioya.limite.*`). HU-15 no exige rate limiting en su historia; no hay regla para `/api/reservas` hoy.

## 8. Frontend

- Rutas (`3_frontend/src/App.jsx`, dentro de `RutaProtegida`): `/mis-reservas` → `pages/reservas/MisReservas.jsx`;
  `/panel/reservas` → `pages/reservas/ReservasDeMisEspacios.jsx`; `/reservas/:id` → `ResumenReserva.jsx`.
- `MisReservas.jsx` / `ReservasDeMisEspacios.jsx`: funciones `tarjetaDeReserva(reserva)` que renderizan `Tarjeta` con
  `acciones` (enlaces "Ver espacio"/"Ver reserva"), `EtiquetaEstado`, `<dl>` fecha/horario/monto; dos
  `SeccionReservas` (proximas/pasadas). Comentario en `ReservasDeMisEspacios.jsx` l. 20: "HU-15 agregará 'Cancelar' en
  acciones". `renderReserva` es una función pura, sin estado → para un modal habrá que pasar callbacks o un componente
  tarjeta con estado; y para refrescar tras cancelar, `SeccionReservas` (carga en `useEffect([cargar, grupo])`) no
  expone recarga (habría que añadir una `key`/prop de recarga o un callback).
- `SeccionReservas.jsx`: paginación "Ver más", errores con `Alerta tipo="error"` y `falla.mensaje`.
- API `3_frontend/src/api/reservas.js`: `crearReserva`, `obtenerReserva`, `listarMisReservas`,
  `listarReservasDeMisEspacios` sobre `peticion(ruta, { metodo, cuerpo, autenticada })` de `api/cliente.js` (errores
  `{estado, mensaje, errores}`; 204 → `null`). Ids con `encodeURIComponent`.
- UI reutilizable `components/ui/`: `Modal.jsx` (`abierto, titulo, onConfirmar, onCerrar, textoConfirmar, variante,
  cargando`; usado en `administracion/DetalleCuenta.jsx` y `RevisarIdentidad.jsx` con `variante="peligro-confirmar"`).
  **Ojo:** el botón de cierre del Modal está fijo con el texto **"Cancelar"** → en "¿Cancelar reserva?" quedarían dos
  "Cancelar"; probablemente haga falta un prop `textoCerrar` (con valor por defecto, sin romper HU-06/HU-09).
  `Boton.jsx` (`variante` primario/secundario/peligro, `cargando`, `deshabilitado`), `Alerta.jsx`
  (error/exito/info/atencion), `Tarjeta.jsx`, `EtiquetaEstado.jsx` (ya con `CANCELADA`).
- Textos: constantes en `pages/reservas/textosReservas.js`, agrupadas por historia con comentario `// HU-xx. Solo
  textos: … lo decide el servidor.`; `formatoFecha`; `formatoPrecio`/`formatoHora` en `pages/espacios/textosEspacios.js`.

## 9. Tests (estilo HU-13 / HU-14)

- Unitarios (JUnit 5 + Mockito + AssertJ, `@DisplayName("HU-xx R<n>: …")`): dominio (`ReservaTest`,
  `HistorialReservasTest`, `ReglasPagoTest` — reglas puras, ideal para las estrategias), servicios con `Clock` fijo
  (`ConsultaReservasServiceTest`, `ReservasPorCobrarImplTest`, `ExpiracionReservasTest`, `AvisosPagoTest`,
  `WebhookPagoServiceTest`), `NotificadorCorreoSimuladoTest` (`OutputCaptureExtension`).
- IT (Testcontainers PostgreSQL, `@SpringBootTest` + MockMvc, sufijo `IT`): extienden soportes abstractos
  `reservas/controller/ReservasControllerSoporte.java` (`@Import({TestcontainersConfiguration, Dobles})`,
  `RelojControlable @Primary`, semillas `filaReserva`/`filaBloque`, `limpiar()`) o
  `pagos/controller/PagosSoporte.java` (además `NotificadorFalso` que guarda correos; borra `pago` antes que
  `reserva`). Ejemplos: `MisReservasControllerIT` (IP `10.16.x.x` por clase para no chocar con límites; `@AfterEach
  DELETE FROM pago`), `ReservaConcurrenciaIT`, `PagoConcurrenciaIT` (hilos simultáneos), `ReservaDAOJpaIT` (sin
  MockMvc). IT de punta a punta vía API: reservar → `POST /api/reservas/{id}/pago` → `POST …/pago/simulacion` con
  `APROBADO` → CONFIRMADA (útil para sembrar una reserva pagada real con su `pago`).
- Frontend: **no hay tests** (ni Vitest). Verificación = `npm --prefix 3_frontend run lint` + `build` (en `./init.sh`) y
  caja negra en UAT con la pasarela simulada.
- Regla de las tasks HU-14: ningún test existente cambia ni se borra (solo se agregan casos); `progress/impl_HU-XX.md`
  con mapa R → test.

## 10. Código de cancelación o Strategy existente

- **No existe** código de cancelación (ni endpoint, ni servicio, ni `cancelar()` en `Reserva`), ni `PoliticaReembolso`,
  ni ninguna implementación de Strategy en el repo (los únicos `strategy` son `GenerationType.IDENTITY` y
  `forward-headers-strategy`). Solo hay: `EstadoReserva.CANCELADA`, `EtiquetaEstado` `CANCELADA`, textos de política en
  `textosFicha.js`, y tests HU-12/14 que siembran `CANCELADA`.
- architecture.md l. 424 fija el diseño esperado: "`reservas`: interfaz `PoliticaReembolso` con una estrategia por caso;
  el servicio la elige sin `if` por tipo".

---

## Recomendaciones para el spec

1. **Strategy en `reservas/domain`**: interfaz `PoliticaReembolso` (`porcentaje`/`montoReembolso(reserva, inicio,
   ahora)`), tres estrategias (usuario ≥ 48 h → 100 %, usuario < 48 h → 0 %, arrendador → 100 %), elegidas sin `if`
   por tipo (p. ej. `Map<QuienCancela, …>` o selector por enum `CanceladaPor { USUARIO, ARRENDADOR }`; el caso
   usuario puede ser una estrategia compuesta que decide por el umbral). Reglas puras → tests unitarios en el borde
   (exactamente 48 h, 47:59:59), con `ZonaHoraria.LIMA` y `Clock`.
2. **Dos operaciones**: vista previa (GET, solo lectura, monto calculado en servidor — CA2) y confirmación (POST). El
   monto de la confirmación se **recalcula** en servidor bajo bloqueo (T), nunca se recibe del cliente. Rutas
   sugeridas: `GET|POST /api/reservas/{id}/cancelacion` (usuario) y `…/api/arrendadores/actual/reservas/{id}/cancelacion`
   (arrendador), o una sola ruta que decida por rol; id como texto → 404 si mal escrito/ajeno (DT-9).
3. **Transacción** con `TransactionTemplate` o `@Transactional`, orden `espacio → reserva` copiando
   `ReservasPorCobrarImpl.bloquear` (`findEspacioIdById` → `bloquearEspacio` → `findParaActualizar`), re-evaluar estado
   tras los bloqueos; solo `CONFIRMADA` y **no iniciada** se cancela (decidir: ¿se puede cancelar una ya empezada/
   pasada? recomendable no). Propiedad: usuario = `reserva.usuarioId`, arrendador = `EspacioBloqueado.arrendadorId`;
   ajena → 404. `Reserva.cancelar(canceladaPor, ahora)` con `IllegalStateException` como `confirmar`.
4. **Liberar bloques** con `ReservaBloqueRepository.desactivarDeReserva` (obligatorio por `uq_reserva_bloque_activo`).
5. **Migración `V4_2__cancelacion_reserva.sql`**: `cancelada_por` (`USUARIO|ARRENDADOR`), `cancelada_en`, y considerar
   `monto_reembolso NUMERIC(10,2)` + `CHECK` de coherencia con `estado`. Coordinar número con HU-16.
6. **Reembolso**: crear la interfaz `pagos.service.PagoService` (lo pide architecture.md) con un método mínimo para
   leer el pago `PAGADO` y otro para "solicitar reembolso" (`MANDATORY`); en HU-15 la implementación no llama a la
   pasarela (HU-16 añade `reembolsar`, la tabla `reembolso`, el Command e idempotencia). Con 0 % no se dispara nada.
   Acordar la firma con Ariel Enriquez (HU-16) y registrarlo en la tabla de servicios/puertos de architecture.md.
   Considerar filas `sim-`.
7. **Correo**: dos métodos nuevos en `NotificadorCorreo` (usuario con monto a reembolsar; arrendador sin datos
   personales ni pago, como `AvisoReservaPagada`), enviados con `DespuesDelCommit`; correos leídos con
   `CuentaService.correoDe` dentro de la transacción. Implementarlos en el simulado y en los 6 dobles de test.
8. **Auditoría** `RESERVA_CANCELADA` con `usuarioId`, `ip` y detalle `canceladaPor=…;espacioId=…;monto=…;porcentaje=…`.
9. **Frontend**: botón "Cancelar reserva" en `acciones` de las `CONFIRMADA` próximas en `MisReservas.jsx` y
   `ReservasDeMisEspacios.jsx`; `Modal` con el monto de la vista previa (añadir `textoCerrar` al Modal, p. ej. "Volver");
   recargar las secciones tras cancelar; funciones nuevas en `api/reservas.js`; textos en `textosReservas.js`.
   Decidir si el arrendador ve sus `CANCELADA` (hoy desaparecen de su lista, HU-14 R15).
10. **Tests**: unitarias de estrategias y del servicio (Mockito, `Clock` fijo); IT `CancelacionReservaControllerIT`
    extendiendo `ReservasControllerSoporte` o `PagosSoporte` (este trae `NotificadorFalso` y limpia `pago`), con IP
    propia, casos 48 h exactas, ajena 404, rol ajeno, doble cancelación, bloques liberados (se puede volver a reservar
    el mismo horario), auditoría y correos; IT de concurrencia (dos cancelaciones simultáneas → una sola). Ningún test
    existente cambia salvo los dobles de `NotificadorCorreo`.
