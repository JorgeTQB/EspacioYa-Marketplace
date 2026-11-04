# Exploración HU-19 — Liquidación semanal al arrendador

> Explorador (solo lectura), 2026-11-04, rama `feature/HU-19-liquidacion-semanal`. Dueño: Eduardo Saldaña.
> Responde las 8 preguntas del orquestador con rutas y nombres concretos. Las propuestas marcadas **(sugerencia)** son
> del explorador, no decisiones: las decide el spec o el dueño (lista P1… al final).

Rutas Java abreviadas: `…/` = `2_Backend/src/main/java/pe/edu/ulima/espacioya/`.

## Archivos analizados

- Docs: `docs/historias/HU-19.md`, `HU-22.md`, `HU-26.md`, `HU-28.md`; `AGENTS.md`; `docs/architecture.md` (principios,
  puertos, secciones "Cuenta bancaria: qué deja HU-18", "Pagos: qué deja HU-13", "Reembolsos: qué deja HU-16",
  "Mis reservas", "Cancelación", estados, "Qué NO hacer"); `docs/modelo-datos.md`; `docs/conventions.md` (migraciones);
  `docs/frontend.md` (estados, kit, pantallas); `docs/requerimientos.md` (RF-E-10…12, RN-06, RN-07, RN-10…12, RN-17,
  RNF-SEG-07/12/18, §9); `progress/history.md` (HU-13, HU-16, HU-17, HU-18); `progress/HU-19.md`;
  `specs/HU-18/{requirements,design}.md` (P8, P9, DT-1, DT-9); `specs/HU-13/design.md` (RS-5); `feature_list.json`.
- Migraciones: `V1_2__auditoria.sql`, `V3_3__reservas.sql`, `V4_1__pago.sql`, `V4_2__cancelacion_reserva.sql`,
  `V4_3__reembolso.sql`, `V5_2__cuenta_bancaria.sql` (y listado completo de `db/migration/`).
- Backend `pagos`: `domain/{Pago,EstadoPago,ReglasPago,Reembolso,EstadoReembolso,OrigenReembolso,CuentaBancaria,
  EstadoCuentaBancaria}`, `repository/{PagoRepository,ReembolsoRepository,CuentaBancariaRepository}`,
  `service/{PagoService,CuentaBancariaService,ReembolsoAdminService}`, `service/puertos/{ReservasPorCobrar,
  ReservaPorCobrar}`, `controller/ReembolsoAdminController`, `dto/{CuentaBancariaDTO,MiCuentaBancariaDTO}`,
  `service/pasarela/PasarelaSimulada` (prefijos `sim-`).
- Backend `reservas`: `domain/{Reserva,EstadoReserva,ReservaConHorario,PoliticaReembolso}`,
  `repository/{ReservaDAO,ReservaDAOJpa,ReservaRepository,ReservaBloqueRepository,HorarioReserva}`,
  `service/ConsultaReservasService`, `service/impl/ReservasPorCobrarImpl`, `controller/ReservasDeMisEspaciosController`.
- Backend otros: `comun/configuracion/ZonaHoraria`, `comun/auditoria/{AuditoriaService,EventoAuditoria}`,
  `comun/seguridad/{FiltroIpsAdmin,UsuarioAutenticado}`, `cuentas/service/{CuentaService,NotificadorCorreo}`,
  `espacios/service/CatalogoEspaciosService`, `administracion/controller/CuentaAdminController`,
  `administracion/service/{CuentaAdminService,RevisionIdentidadService}` (acciones de auditoría).
- Frontend: `App.jsx`, `pages/cuentas/Panel.jsx`, `pages/pagos/{ReembolsosPorAtender.jsx,textosPagos.js}`,
  `api/pagos.js`, `components/ui/EtiquetaEstado.jsx`.

---

## 1. Pagos (HU-13, HU-16)

**Entidad y tabla.** `…/pagos/domain/Pago.java` ↔ tabla `pago` (`V4_1__pago.sql`): `id`, `reserva_id` (FK `reserva`, sin
cascada), `id_transaccion_pasarela VARCHAR(100)` único (`uq_pago_id_transaccion`), `monto NUMERIC(10,2)` (`ck_pago_monto > 0`),
`estado VARCHAR(20)` (`ck_pago_estado`), `ultimos4`, `creado_en TIMESTAMPTZ`. Índices: `ix_pago_reserva_estado (reserva_id,
estado, creado_en)` y **`uq_pago_reserva_pagado (reserva_id) WHERE estado = 'PAGADO'`** — como mucho un `PAGADO` por
reserva; el comentario de la migración ya dice "HU-15 y HU-19 leen este pago".

**Estados** (`EstadoPago`): `PAGADO`, `RECHAZADO`, `PAGADO_TARDIO`, `MONTO_DISTINTO`, `REEMBOLSADO`, `REEMBOLSO_FALLIDO`. El
Javadoc de `EstadoPago` y `docs/architecture.md` ("Lo que deben saber las historias que siguen") dicen explícitamente:
**"HU-19 y HU-28 deben contar solo `PAGADO`"**; `PAGADO_TARDIO` y `MONTO_DISTINTO` no cobraron nada para la reserva (se
devuelven a mano). Aviso de HU-13 a Eduardo Saldaña (`progress/history.md` l. 893): contar solo `PAGADO` y excluir o
limpiar las filas `sim-`.

**Monto bruto.** Un pago solo queda `PAGADO` si `monto == reserva.monto_total` y moneda `PEN`
(`ReglasPago.estadoDeAprobado`); si no, `MONTO_DISTINTO`. Por tanto `pago.monto` (PAGADO) = `reserva.monto_total`: cualquiera
de los dos sirve de bruto por reserva. Leerlo de `pago` lo mantiene dentro de `pagos` sin cruzar módulos.

**Filas `sim-`.** Con la pasarela simulada, `id_transaccion_pasarela` = `sim-<reservaId>-<RESULTADO>-<n>`
(`PasarelaSimulada`, `PREFIJO_SIMULADO = "sim-"`); ningún dinero se movió. RS-5 de HU-13 pide a HU-19 filtrarlas o
limpiarlas al conectar Mercado Pago. **Ojo:** en UAT todo pago es `sim-` (la pasarela real no está conectada, §9.10 de
requerimientos): si se filtran siempre, en UAT nunca habrá nada que liquidar y no se podrá demostrar CA1–CA3 → P3.

**Reembolsos (HU-16).** `…/pagos/domain/Reembolso.java` ↔ tabla `reembolso` (`V4_3`): `pago_id` (FK `pago`), `monto`,
`origen` (`CANCELACION`, `RECLAMO`), `estado` (`PENDIENTE`, `REEMBOLSADO`, `FALLIDO`), `intentos`, `creado_en`,
`ultimo_intento_en`, `id_reembolso_pasarela`; `uq_reembolso_pago_origen (pago_id, origen)`. `reembolso` es la fuente de
verdad; `pago.estado` pasa a `REEMBOLSADO`/`REEMBOLSO_FALLIDO` en la misma transacción (`Pago.marcarReembolsado`,
`marcarReembolsoFallido`, solo desde `PAGADO` o `REEMBOLSO_FALLIDO`).

**Pagada y luego reembolsada.**
- **Cancelación (HU-15):** solo antes del inicio (`Reserva.motivoNoCancelable` → `YA_INICIADA`), así que una reserva
  cancelada **nunca es "usada"**. La reserva queda `CANCELADA` con `cancelada_por`, `cancelada_en`, `monto_reembolso`
  (`V4_2`). Con reembolso > 0 hay fila `reembolso` y el pago pasa a `REEMBOLSADO` o `REEMBOLSO_FALLIDO`. **Con 0 % (usuario
  cancela con < 48 h, RN-06) no se llama a `PagoService`: el pago sigue `PAGADO` y la reserva `CANCELADA`** → caso a decidir
  (P5): ¿ese dinero retenido es ingreso del arrendador?
- **Parcial:** `Pago.marcarReembolsado` pone `REEMBOLSADO` aunque el reembolso sea parcial (no hay `REEMBOLSADO_PARCIAL`).
  Hoy un reembolso parcial solo puede venir de HU-26 (`origen = RECLAMO`, aún no implementado; RN-06 da 0 % o 100 %). Si
  HU-19 cuenta "solo `PAGADO`", una reserva usada con reembolso parcial por reclamo quedaría fuera **entera**; el remanente
  sería `pago.monto − Σ reembolso.monto (REEMBOLSADO)` → P6.
- Cómo distinguirlas: `pago.estado` + filas de `reembolso` del `pago_id` (por origen y estado); para cancelación, además
  `reserva.estado = CANCELADA` y `reserva.monto_reembolso`.

**`ReglasPago` reutilizable** (`…/pagos/domain/ReglasPago.java`): `MONEDA = "PEN"`; `idBienEscrito(String)` (1–19 dígitos,
> 0, sin desbordar; lo usa `ReembolsoAdminService` para las rutas `/{id}` → 404 si no) — sirve para
`/api/admin/liquidaciones/{id}/…`. **No** contiene redondeo. El único redondeo monetario del repo está en
`reservas.domain.PoliticaReembolso.monto` (`multiply(porcentaje).divide(100, 2, RoundingMode.HALF_UP)`), pero `pagos` no
puede importar `reservas` (dirección `reservas → pagos`): hay que replicar la regla en `pagos.domain` **(sugerencia:**
`ReglasLiquidacion` puro, `COMISION = 10 %`, escala 2 `HALF_UP`, `neto = bruto − comision` para que sumen exacto).

**Repositorios existentes:** `PagoRepository.findByReservaIdAndEstado(reservaId, estado)` (Optional, uno por reserva),
`countByReservaIdAndEstado`, `findFirstByReservaIdOrderByCreadoEnDescIdDesc`; `ReembolsoRepository.existsByPagoIdAndOrigen`,
`porAtender(limite)`. No hay consulta por lote de pagos `PAGADO`: HU-19 la agrega.

## 2. Reservas

**Entidad.** `…/reservas/domain/Reserva.java` ↔ tabla `reserva` (`V3_3`, `V4_2`): `usuario_id`, `espacio_id`, `estado`
(`EstadoReserva`: `PENDIENTE_PAGO`, `CONFIRMADA`, `EXPIRADA`, `CANCELADA`), `precio_hora_aplicado`, `monto_total
NUMERIC(10,2)`, `creada_en`, `expira_en`, `cancelada_por`, `cancelada_en`, `monto_reembolso`. **No hay columna de fecha/hora
de fin ni estado "USADA"/"COMPLETADA"**: una reserva usada es una `CONFIRMADA` cuyo horario ya terminó. Leer el estado con
`estadoEn(ahora)` (una `CONFIRMADA` se lee siempre `CONFIRMADA`).

**Horario.** Vive en `reserva_bloque` (`fecha DATE`, `hora_inicio SMALLINT 0–23`, `activo`); las filas se conservan
(liberar = `activo = false`). `ReservaBloqueRepository.horarios(reservaIds)` → `HorarioReserva(reservaId, fecha,
min(hora_inicio), max(hora_inicio))` sobre **todas** las filas, en una consulta por lote. `ReservaDAOJpa.conHorario` lo
une en memoria en `ReservaConHorario(reserva, fecha, horaInicio, horaFin)` con `horaFin = max + 1` (1–24).
`ReservaConHorario.fin()` → `LocalDateTime` (24 → 00:00 del día siguiente); `instanteDeInicio()` → `Instant` en
`ZonaHoraria.LIMA`. **No existe `instanteDeFin()`**: HU-19 lo agregaría igual (`fin().atZone(ZonaHoraria.LIMA).toInstant()`).

**"Usada hace más de 48 h"** (sugerencia): `estadoEn(ahora) == CONFIRMADA && instanteDeFin() + 48 h <= ahora`, con `ahora =
Instant.now(reloj)` tomado una vez (patrón de `ConsultaReservasService`: un solo instante por solicitud). `Clock` es un bean
en UTC (`comun/configuracion/ConfiguracionReloj`); fechas locales con `ZonaHoraria.hoy/ahora(reloj)` y `LIMA`. En pruebas,
`src/test/java/pe/edu/ulima/espacioya/RelojControlable.java`. Ya hay una constante de 48 h
(`PoliticaReembolso.ANTICIPACION_MINIMA`), pero es de otra regla y de `reservas`: HU-19 define la suya.

**Reserva → espacio → arrendador.** `reserva.espacio_id` → `espacio.arrendador_id` (FK a `perfil_arrendador.usuario_id`).
`reservas` no lee `EspacioRepository`; usa `espacios.service.CatalogoEspaciosService` (`nombres(espacioIds)`,
`nombresDelArrendador(arrendadorId)`) y `DisponibilidadService.bloquearEspacio(espacioId) → EspacioBloqueado(arrendadorId,
nombre)` (`MANDATORY`, toma bloqueo: no apto para una consulta en lote). **No hay hoy un método "arrendador de estos
espacios" en lote**: el puerto de HU-19 (implementado en `reservas`) necesitará agregar uno a `CatalogoEspaciosService`
(p. ej. `arrendadoresDe(espacioIds) → Map<Long, Long>`, sin filtro de visibilidad, como `nombres`) → afecta a `espacios`
(dueño Sebastian Garate; agregar un método a la interfaz pública es el patrón ya usado por HU-12/HU-14).

**Puertos y desacoplamiento.** Dirección permitida: `comun ← cuentas ← espacios ← reservas → pagos → cuentas`;
`administracion → (todos)`. `pagos` **no** puede usar `reservas`: define interfaces en `pagos/service/puertos/` y
`reservas` las implementa (DIP). Precedente: `pagos.service.puertos.ReservasPorCobrar` (+ records `ReservaPorCobrar`,
`ReservaBloqueada`) implementado por `reservas.service.impl.ReservasPorCobrarImpl` (`@Service`, `Clock` inyectado,
`@Transactional(readOnly = true)` para lecturas). `docs/architecture.md` (tabla de puertos) **ya reserva dos nombres**:
- `pagos.ReservasLiquidables` → lo implementa `reservas`, "HU-19" (sin firma: la define HU-19).
- `pagos.ReclamosAbiertos` → lo implementa `administracion`, "RN-12" (para HU-26).
Ninguno existe en el código. `ReservaDAO` (HU-14) exige el dueño en cada consulta (STRIDE E/I); el puerto de HU-19 es una
consulta del sistema para el admin, así que conviene que sea un método nuevo del puerto/repositorio y no del DAO, o
justificarlo en el design.

**Forma sugerida del flujo (sugerencia, a validar en el spec):** `pagos` parte de lo suyo — pagos `PAGADO` cuya
`reserva_id` no está en `liquidacion_detalle` (consulta propia) — y pregunta al puerto
`ReservasLiquidables.usadasAntesDe(Collection<Long> reservaIds, Instant limite) → List<ReservaLiquidable(reservaId,
arrendadorId, espacioId, nombreEspacio, fecha, horaInicio, horaFin)>` solo por las `CONFIRMADA` con fin ≤ límite. Así
`reservas` no conoce `liquidacion` y `pagos` no conoce `reserva_bloque`. Alternativa: el puerto devuelve todas las usadas
y `pagos` resta las ya liquidadas (crece sin límite). → P9.

## 3. Cuenta bancaria (HU-18)

- Tabla `cuenta_bancaria` (`V5_2`): `arrendador_id` (FK `perfil_arrendador(usuario_id)`, **una fila por arrendador**,
  `uq_cuenta_bancaria_arrendador`), `banco VARCHAR(60)`, `numero TEXT` **cifrado** AES-256-GCM `v1:` con
  `@Convert(converter = ConvertidorCifrado.class)` (`comun/cifrado/CifradorCampos`), `titular VARCHAR(150)` (razón social),
  `retener_hasta TIMESTAMPTZ` (nulo si nunca cambió), `creada_en`, `actualizada_en`. Sin historial: un cambio reemplaza la
  fila (el historial está en la auditoría `CUENTA_BANCARIA_CAMBIADA`).
- Entidad `…/pagos/domain/CuentaBancaria.java`: `estaRetenida(ahora)`, `estado(ahora)` → `EstadoCuentaBancaria`
  (`DISPONIBLE`, `RETENIDA`; no se guarda), `RETENCION = 72 h`, `enmascarado()` → `"****" + 4 últimos`,
  `getBanco()`, `getTitular()`, `getRetenerHasta()`. **No hay getter del número completo** (deliberado): si el admin
  debe verlo para transferir, HU-19 agrega el método.
- Cómo saber si tiene cuenta: `CuentaBancariaRepository.findByArrendadorId(arrendadorId)` (vacío → `PENDIENTE_CUENTA`);
  `findParaActualizar` toma `PESSIMISTIC_WRITE`. Contrato documentado (architecture § "Cuenta bancaria: qué deja HU-18",
  HU-18 DT-1/DT-9): HU-19 es del mismo módulo, lee el repositorio directo y decide con `estaRetenida(ahora)`, **no** con
  la columna a mano. `CuentaBancariaService` es clase concreta sin interfaz; sus métodos públicos exigen rol `ARRENDADOR`
  (`consultar`, `guardar`), así que el admin no puede reutilizarlos tal cual.
- Retención (RN-11): "tras **cambiar** la cuenta, la siguiente liquidación no se **paga** antes de `retener_hasta`"; el
  primer registro no retiene. La aplicación es de HU-19 → P11.
- Qué mostrar al admin: hoy solo `banco`, `numeroEnmascarado`, `titular`, `estado` (`CuentaBancariaDTO`). HU-18 P8: "HU-18
  no expone el número completo en ninguna ruta; cómo lo ve el admin lo decide HU-19" → P10. RNF-SEG-14/§3 de
  architecture: nunca exponer datos bancarios salvo lo necesario; `Cache-Control: no-store` obligatorio.
- HU-18 P9: si la identidad deja de estar `VERIFICADA` (HU-20) la cuenta no se borra; "si las liquidaciones se pausan lo
  decide HU-19/HU-20" → P12.

## 4. Admin y seguridad

- **IPs:** `comun/seguridad/FiltroIpsAdmin` aplica a `/api/admin/**` (`PathPatternRequestMatcher`), antes de la
  autenticación; IP fuera de `espacioya.admin.ips-permitidas` (`ADMIN_IPS_PERMITIDAS`, por defecto `127.0.0.1,::1`) → 403
  genérico. Basta con poner las rutas bajo `/api/admin/`.
- **Sesión y MFA (HU-17):** `SesionService.validar` rechaza (401) la sesión de un admin sin MFA activo; el admin entra
  siempre con TOTP. Nada que hacer en HU-19.
- **Rol:** no hay `hasRole` en `ConfiguracionSeguridad`; el **servicio** exige el rol antes de leer nada:
  `private static void exigirAdmin(UsuarioAutenticado admin)` → `AccesoDenegadoException` (403) si `rol != "ADMIN"`
  (`ReembolsoAdminService`, `CuentaAdminService`, `RevisionIdentidadService`). Para el arrendador, igual con `"ARRENDADOR"`
  (`CuentaBancariaService.exigirArrendador`, `ConsultaReservasService.exigirRol`). `UsuarioAutenticado(usuarioId,
  sesionId, rol)` llega con `@AuthenticationPrincipal`; IP con `IpCliente.de(HttpServletRequest)`.
- **Modelo de controlador admin en `pagos`:** `…/pagos/controller/ReembolsoAdminController` (`/api/admin/reembolsos`):
  `GET` → `ResponseEntity.ok().cacheControl(CacheControl.noStore())`; `POST /{id}/reintento` sin cuerpo, `@PathVariable
  String id` validado con `ReglasPago.idBienEscrito` (404 si no) y 200 con el estado resultante (DT-8). Servicio
  `ReembolsoAdminService`. Con cuerpo y `@Valid`: `CuentaAdminController.suspender` (`/api/admin/cuentas/{id}/suspension`,
  201, `MotivoSuspensionDTO`). Errores: `ReglaNegocioException` → 400 (con campo opcional, p. ej. `new
  ReglaNegocioException("contrasena", msg)`), `RecursoNoEncontradoException` → 404, `ValidacionException` → 400 con
  errores por campo.
- **Ruta del arrendador:** convención `/api/arrendadores/actual/...` (id del token, nunca de la URL):
  `ReservasDeMisEspaciosController`, `CuentaBancariaController`.
- **Auditoría (STRIDE R):** tabla `auditoria` (`V1_2`): `usuario_id` (FK, nulo para procesos), `accion VARCHAR(60)`,
  `entidad VARCHAR(60)`, `entidad_id`, `ip VARCHAR(45)`, `fecha`, `detalle TEXT`. API:
  `AuditoriaService.registrar(new EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle))` (`@Transactional`,
  se une a la del llamador). Convenciones: acción en MAYÚSCULAS como constante `public static final String ACCION_…`
  (p. ej. `REEMBOLSO_REALIZADO`, `CUENTA_SUSPENDIDA`, `CUENTA_BANCARIA_CAMBIADA`), entidad = nombre de tabla, detalle
  `clave=valor;clave=valor` sin datos sensibles (montos sí; número de cuenta solo enmascarado). **`origen` no es columna**:
  va en el detalle (`origen=WEBHOOK`, `origen=PROCESO`, `origen=CANCELACION`); un admin actúa con su `usuario_id` e IP.
  **(Sugerencia)** `LIQUIDACION_GENERADA` (una por liquidación: `arrendadorId`, `reservas=n`, `bruto`, `comision`,
  `neto`, `estado`) y/o un resumen del lote, `LIQUIDACION_PAGADA` (`numeroOperacion`, `cuenta=banco/****1234`) y, si se
  revela el número completo, `CUENTA_BANCARIA_CONSULTADA_ADMIN`.

## 5. Migraciones Flyway

- Carpeta `2_Backend/src/main/resources/db/migration/`. Convención `V<sprint>_<n>__<descripcion>.sql`
  (`docs/conventions.md`); `spring.flyway.out-of-order=true`; una migración subida no se edita; no depender de una de
  número mayor.
- Existentes: `V1_1…V1_5`, `V2_1…V2_4`, `V3_1…V3_3`, `V4_1…V4_3`, `V5_1__mfa.sql` (HU-17), `V5_2__cuenta_bancaria.sql`
  (HU-18). HU-19 es Sprint 5 y la única pendiente de ese sprint → **`V5_3__liquidacion.sql`** (verificar `develop` antes).
- **Nada creado aún para liquidación** (ni tablas, ni columnas, ni puertos). Lo que hay reservado es documental:
  - `docs/modelo-datos.md`: `liquidacion` (id, arrendador_id, periodo, monto_bruto, comision, monto_neto, estado
    `PENDIENTE_CUENTA|GENERADA|PAGADA`, numero_operacion) y `liquidacion_detalle` (liquidacion_id + reserva_id), con
    **`liquidacion_detalle.reserva_id` único** ("una reserva no se liquida dos veces", también principio 7 de
    architecture). ER: `PERFIL_ARRENDADOR ||--o{ LIQUIDACION`, `LIQUIDACION ||--|{ LIQUIDACION_DETALLE }o--|| RESERVA`.
  - `docs/architecture.md` estados: "Liquidación: `GENERADA` → `PAGADA` (con número de operación); `PENDIENTE_CUENTA` si
    el arrendador no registró cuenta bancaria."
  - Comentarios en `V4_1` y `V5_2` mencionan a HU-19.
- **(Sugerencias para el design):** FK `arrendador_id → perfil_arrendador(usuario_id)` sin cascada (dato contable, como
  `pago` y `cuenta_bancaria`; HU-22 anonimiza, no borra); `CHECK` de estado, de montos ≥ 0 y `monto_neto = monto_bruto −
  comision`, de coherencia `(estado = 'PAGADA') = (numero_operacion IS NOT NULL)` (+ `pagada_en`, `pagada_por`), como
  `ck_reembolso_confirmado`; `generada_en`/`generada_por`; en `liquidacion_detalle`, guardar también `pago_id` y el
  `monto` de la línea (trazabilidad y que HU-26/HU-28 no recalculen); FK de `reserva_id` y `pago_id` sin cascada. El
  modelo dice `periodo` sin tipo → P1.

## 6. Frontend

- **Panel** `3_frontend/src/pages/cuentas/Panel.jsx`: enlaces por rol con `<Link>`; ARRENDADOR (espacios, reservas,
  identidad, `ENLACE_CUENTA_BANCARIA`), ADMIN (identidades, cuentas, `/admin/reembolsos`); texto
  `TEXTOS_POR_ROL.ADMIN.descripcion` = "Revisa identidades, gestiona cuentas y atiende reembolsos." (ampliar con
  liquidaciones). Agregar "Mis liquidaciones" (ARRENDADOR) y "Liquidaciones" (ADMIN).
- **Modelo de lista admin** `pages/pagos/ReembolsosPorAtender.jsx` (+ `.module.css`): guarda `esAdmin` → `<Navigate
  to="/panel">`, carga con `useEffect` + bandera `activo`, `recarga` como contador, una `Tarjeta` por fila con
  `EtiquetaEstado`, monto con `formatoPrecio` (`pages/espacios/textosEspacios.js`) y fechas con `fechaHora`
  (`pages/administracion/textosAdministracion.js`), `Boton secundario` por fila → `Modal` `primario` de confirmación,
  resultado en `Alerta`. Sirve casi tal cual para la lista admin y "Marcar como pagada" (Modal con un `Campo` para el
  número de operación; precedente de `Campo` dentro de `Modal`: `SeguridadCuenta.jsx`, HU-17). "Generar liquidación" =
  único `Boton primario` arriba + `Modal` de confirmación.
- **Modelo de lista del arrendador:** `pages/reservas/ReservasDeMisEspacios.jsx` / `SeccionReservas.jsx` (paginado con
  `hayMas`), o la forma simple de `ReembolsosPorAtender` si no se pagina (P15).
- **API** `src/api/pagos.js`: funciones finas sobre `peticion()` de `api/cliente.js` (`listarReembolsosPorAtender`,
  `reintentarReembolso`, `obtenerCuentaBancaria`…). Agregar p. ej. `generarLiquidaciones()`, `listarLiquidaciones()`,
  `marcarLiquidacionPagada(id, numeroOperacion)`, `listarMisLiquidaciones()`. Sin montos en el cuerpo.
- **Kit** `src/components/ui/`: `Boton`, `Campo`, `Alerta`, `EtiquetaEstado`, `Tarjeta`, `Modal`, `SelectorBloques`,
  `Imagen`, `Galeria`, `CuentaRegresiva`. No hay componente `Tabla`; las listas son `Tarjeta`s. Si se quisiera una tabla,
  se agrega al kit y se anota en el design (docs/frontend.md).
- **`EtiquetaEstado.jsx`**: mapa único por **valor de enum** (no por entidad). Agregar `GENERADA`, `PAGADA`,
  `PENDIENTE_CUENTA`; `docs/frontend.md` § "Color de cada estado" ya fija `PAGADA` = éxito y `PENDIENTE_CUENTA`, `GENERADA`
  = atención (textos los decide el spec, p. ej. "Pendiente de cuenta", "Generada"/"Por pagar", "Pagada"). **No usar**
  `PENDIENTE` ni `RETENIDA` como estado de liquidación: ya existen con otro texto (reembolso, cuenta bancaria).
- **Rutas** `App.jsx`: todas con `<RutaProtegida>`; admin bajo `/admin/...` (comentario "la API exige IP permitida y rol
  ADMIN"), arrendador bajo `/panel/...`. Sugerencia: `/admin/liquidaciones` y `/panel/liquidaciones`.
- **Textos** `pages/pagos/textosPagos.js`: bloques por historia con comentario "HU-xx. Solo textos: … lo decide el
  servidor"; ya habla de "liquidaciones" (`AVISO_CAMBIO_CUENTA`, `DISPONIBLE` = "Disponible para liquidaciones").
- Fuera del alcance (architecture "Qué NO hacer"): **"Panel de ingresos y ocupación"** descartado → "Mis liquidaciones"
  debe ser una lista de liquidaciones, sin totales/gráficos de ingresos.

## 7. Interacciones futuras

- **HU-22 (Kevin Chan, Sprint 6, depende de HU-19):** CA2/CA3 y RN-17/RF-A-24/25: no se elimina la cuenta si hay
  "liquidaciones pendientes" y se debe decir qué resolver. Necesita de HU-19: (a) qué estados cuentan como pendientes
  (`GENERADA` y `PENDIENTE_CUENTA` seguro; ¿también reservas usadas aún no liquidadas o dentro de las 48 h?) y (b) una
  forma de preguntarlo desde `cuentas`. architecture prevé el puerto `cuentas.ParticipanteDatosPersonales` (implementado
  por `reservas`, `pagos`, `busqueda`; **no existe aún**) para exportar, verificar impedimentos y anonimizar: HU-19 puede
  dejar un método de consulta en `pagos` (p. ej. `tieneLiquidacionesPendientes(arrendadorId)`) y HU-22 lo conecta al
  puerto. Además CA1 de HU-22 exporta "perfil, reservas y pagos" (¿liquidaciones del arrendador?) y CA2 conserva
  "registros contables": `liquidacion` sin cascada. → P16.
- **HU-26 (Ariel Enriquez, Sprint 8, depende de HU-19):** CA1/RN-12/RF-F-07: reclamo solo si la reserva "fue hace menos de
  48 h"; con reclamo abierto la reserva "no entra en ninguna liquidación hasta resolverse"; CA3: a favor del arrendador
  "vuelve a ser liquidable"; CA2: a favor del usuario → reembolso total o parcial (HU-16, `origen = RECLAMO`,
  `Reembolso.porReclamo` futuro). Puerto previsto `pagos.ReclamosAbiertos` (implementa `administracion`). Implicaciones
  para HU-19: (1) las dos ventanas de 48 h deben medirse desde el **mismo instante** (fin de la reserva) y con el mismo
  borde (`<` vs `≥`) para que no haya hueco ni solape; (2) si la generación incluye "todo lo pendiente", una reserva que
  vuelve a ser liquidable entra sola en la siguiente; con periodos fijos se perdería; (3) decidir si HU-19 ya define el
  puerto `ReclamosAbiertos` (sin implementación, vía `ObjectProvider` como `ReservasDelEspacio`) o lo deja a HU-26; (4)
  reembolso parcial tras reclamo → P6.
- **HU-28 (Eduardo Saldaña, Sprint 8, depende de HU-13 y HU-16, no de HU-19):** historial del **usuario** (pagado,
  reembolsado, comprobante PDF); cuenta `PAGADO` y `REEMBOLSADO`, monto devuelto en `reembolso.monto`. No lee
  liquidaciones; solo compartiría la regla "qué es un pago real" (filas `sim-`, P3). Si HU-19 crea una consulta/regla
  "pago efectivo neto de reembolsos", HU-28 podría reutilizarla.

## 8. Dudas de producto (detalle en la lista final)

Periodo semanal fijo vs. todo lo pendiente; qué es "usada"; reservas canceladas con 0 % de reembolso; reembolsos
parciales; filas `sim-`; redondeo y base de la comisión; arrendador suspendido o con identidad no verificada; generar dos
veces; cuenta registrada después de `PENDIENTE_CUENTA`; retención de 72 h; cómo ve el admin el número completo; formato
del número de operación; correo al arrendador; paginación; HU-22/HU-26.

---

## Decisiones abiertas

- **P1 — Periodo.** CA1 dice "reservas usadas hace más de 48 h y no liquidadas antes" (todo lo pendiente), pero el modelo
  tiene `periodo` y la historia se llama "semanal". Opciones: (a) cada clic liquida todo lo pendiente y `periodo` es solo
  la fecha/semana de generación (informativo); (b) semana fija lunes–domingo en hora de Lima (reservas cuyo fin cae en la
  semana anterior y ya pasaron 48 h). (a) es más simple y robusto con HU-26 (CA3) y con reservas que maduran tarde.
  Definir también el tipo de `periodo` (`DATE` de generación, o `periodo_desde/hasta`).
- **P2 — Definición de "usada".** ¿`CONFIRMADA` cuyo **fin** (último bloque + 1 h, Lima) + 48 h ≤ ahora? ¿Borde inclusivo?
  Alinear con la ventana de reclamo de HU-26.
- **P3 — Filas `sim-`.** ¿Excluirlas siempre (en UAT nunca habría liquidaciones), incluirlas solo con
  `espacioya.pagos.pasarela=simulada`, o incluirlas siempre marcándolas como simuladas en la UI/auditoría? RS-5 de HU-13
  pide decidirlo.
- **P4 — Base del bruto.** `pago.monto` del `PAGADO` (dentro de `pagos`) o `reserva.monto_total` vía puerto (son iguales
  por `estadoDeAprobado`). Recomendado: `pago.monto`.
- **P5 — Cancelada con 0 % de reembolso** (usuario cancela con < 48 h): el pago sigue `PAGADO`, la reserva `CANCELADA` y
  nunca fue "usada". ¿Se liquida al arrendador (¿con qué fecha de referencia?) o queda para la plataforma? CA1 literal la
  excluye.
- **P6 — Reembolsos parciales / estados de pago.** ¿Solo `PAGADO` (lo que piden HU-13 y architecture) o `PAGADO` +
  `REEMBOLSADO` parcial con bruto = `pago.monto − Σ reembolso.monto REEMBOLSADO`? ¿Qué pasa con `REEMBOLSO_FALLIDO` o un
  reembolso `PENDIENTE`? Hoy solo HU-26 puede producir parciales; decidir si HU-19 lo deja preparado o lo deja a HU-26.
- **P7 — Comisión y redondeo.** 10 % fijo (no configurable, como RN-06). ¿Por reserva y se suma, o sobre el total del
  arrendador? Escala 2, `HALF_UP` (como `PoliticaReembolso`), `neto = bruto − comision`. ¿Guardar la tasa aplicada en la
  fila (por si cambia)?
- **P8 — Generar dos veces.** Con P1(a), un segundo clic solo crea liquidaciones nuevas si hay reservas nuevas (o ninguna
  y responde "no hay nada que liquidar"). ¿Se impide más de una por arrendador y periodo? Concurrencia: dos admins a la
  vez → `uq` en `liquidacion_detalle.reserva_id` + bloqueo (p. ej. advisory lock o `SELECT … FOR UPDATE` sobre una fila)
  para que el segundo no falle con 500. ¿Arrendador con 0 reservas elegibles → sin liquidación?
- **P9 — Forma del puerto `ReservasLiquidables`.** `pagos` filtra primero por lo suyo (pagos `PAGADO` no liquidados) y
  pregunta al puerto por esas reservas, o el puerto devuelve todas las usadas. Y el método nuevo en
  `CatalogoEspaciosService` para espacio → arrendador en lote (toca `espacios`, avisar a Sebastian Garate).
- **P10 — Número de cuenta completo para el admin** (HU-18 P8). Opciones: mostrarlo descifrado solo en el detalle de una
  liquidación por pagar (ruta admin `no-store`, auditando cada consulta), o solo enmascarado y el admin lo obtiene fuera
  del sistema. ¿Snapshot de banco/enmascarado en la liquidación al generarla o al marcarla pagada (la cuenta puede cambiar
  entre ambos momentos)?
- **P11 — Retención 72 h (RN-11).** ¿Se aplica al **marcar como pagada** (400 si `estaRetenida(ahora)`), al generar (otro
  estado, p. ej. "retenida", que no existe en el modelo), o solo se muestra como aviso? Recomendado: bloquear el marcado
  con mensaje y mostrar la fecha `retenerHasta`.
- **P12 — Arrendador suspendido o con identidad no verificada** (HU-09, HU-20, HU-18 P9). ¿Se le genera la liquidación
  igual (el dinero se le debe), se excluye, o se genera y no se puede marcar pagada? Sus reservas confirmadas siguen
  existiendo aunque sus espacios no se vean.
- **P13 — `PENDIENTE_CUENTA` → ?** Cuando el arrendador registra la cuenta: (a) pasa sola a `GENERADA` (¿al registrar, que
  toca HU-18, o al leer?), (b) el admin puede marcarla pagada directamente si ahora hay cuenta (revalidando), o (c) se
  rehace en la siguiente generación. ¿Las nuevas reservas de ese arrendador van a una liquidación nueva o se suman a la
  `PENDIENTE_CUENTA` existente? Transiciones válidas: `PENDIENTE_CUENTA → GENERADA → PAGADA` y `PENDIENTE_CUENTA →
  PAGADA`?
- **P14 — Número de operación.** Formato (dígitos/alfanumérico, longitud), obligatorio, ¿único?, inmutable una vez
  `PAGADA` (sin "despagar" ni corregir). ¿Fecha de pago la pone el servidor?
- **P15 — Pantallas y datos.** Lista admin: ¿todas o solo no pagadas por defecto, paginada? Detalle con las reservas
  incluidas (espacio, fecha, monto)? "Mis liquidaciones" del arrendador: ¿con detalle por reserva y número de operación
  visible? ¿Paginación como HU-14 (`hayMas`)? Sin totales de ingresos (fuera de alcance).
- **P16 — Correo y HU-22.** ¿Correo al arrendador al marcar pagada o al quedar `PENDIENTE_CUENTA` (no lo pide ningún CA;
  `NotificadorCorreo` no tiene método)? ¿Qué cuenta como "liquidación pendiente" para HU-22 y si HU-19 deja ya el método de
  consulta?
- **P17 — Reclamos (HU-26).** ¿HU-19 define ya el puerto `pagos.ReclamosAbiertos` sin implementación (todas las reservas
  sin reclamo) o lo deja entero a HU-26?
- **P18 — Detalle de líneas.** ¿`liquidacion_detalle` guarda `pago_id` y `monto` por reserva además de `reserva_id`
  (recomendado para trazabilidad y para no recalcular)?
