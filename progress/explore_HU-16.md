# Exploración HU-16 — Reembolso automático (Command, Adapter)

> Explorador de solo lectura. Rutas relativas a la raíz del repo. Paquete base backend:
> `2_Backend/src/main/java/pe/edu/ulima/espacioya/` (abreviado `…/`).

Historia (`docs/historias/HU-16.md`): Release 1 · Sprint 4, tamaño S, depende de HU-13 y HU-15. Programa Ariel Enriquez,
prueba Eduardo Saldaña. ★ (capítulo 8), patrones Command + Adapter.
- CA1: cancelación con reembolso → se solicita a la pasarela **contra el pago original**; al confirmarse, la transacción
  pasa a "reembolsada".
- CA2: si la pasarela falla → "reembolso fallido", el admin la ve en su panel y puede **reintentar con un botón**.
- STRIDE: (T/I) solo contra el pago original vía API de la pasarela; (T) idempotencia: una cancelación no genera dos
  reembolsos; (R) registro de cada intento y resultado.

---

## 1. Pagos (HU-13): entidad, estados, servicio, repositorio, pasarela

**Archivos analizados:** `…/pagos/domain/Pago.java`, `…/pagos/domain/EstadoPago.java`,
`…/pagos/service/PagoService.java`, `…/pagos/service/impl/PagoServiceImpl.java`,
`…/pagos/repository/PagoRepository.java`, `…/pagos/service/pasarela/{PasarelaPago,MercadoPagoAdapter,PasarelaSimulada}.java`,
`2_Backend/src/main/resources/db/migration/V4_1__pago.sql`, `docs/architecture.md` §"Pagos: qué deja HU-13" (l. 282-317).

**Entidad `Pago`** (`…/pagos/domain/Pago.java`, tabla `pago`):
- Campos: `id`, `reservaId`, `idTransaccionPasarela` (id del pago en la pasarela; HU-13 DT-2: "HU-16 lo usa para
  reembolsar"), `monto` (NUMERIC 10,2), `estado` (`EstadoPago`, STRING), `ultimos4`, `creadoEn`.
- Solo fábrica `Pago.de(reservaId, idTransaccion, monto, estado, ultimos4, ahora)` y getters. **No tiene métodos de
  dominio para cambiar el estado** (HU-16 tendrá que agregar p. ej. `marcarReembolsado()` / `marcarReembolsoFallido()`;
  HU-13 explore l. 401 ya lo anticipaba: "`Pago` con métodos de dominio para estados").

**`EstadoPago`** (`…/pagos/domain/EstadoPago.java`): `PAGADO, RECHAZADO, PAGADO_TARDIO, MONTO_DISTINTO, REEMBOLSADO,
REEMBOLSO_FALLIDO`. Los dos últimos **ya existen en el enum y en el CHECK** de la BD, sin uso todavía.

**`PagoService`** (`…/pagos/service/PagoService.java`) — interfaz pública de `pagos` creada por HU-15:
```java
void reembolsarCancelacion(ReembolsoPorCancelacion solicitud);
```
`ReembolsoPorCancelacion` (`…/pagos/service/ReembolsoPorCancelacion.java`):
`record ReembolsoPorCancelacion(Long reservaId, BigDecimal monto, Long solicitadoPor, String ip)`.
Javadoc: "HU-16 reemplaza el cuerpo sin cambiar la firma ni la propagación: crea la fila de `reembolso` y ejecuta el
Command contra `PasarelaPago.reembolsar` después del commit. Un fallo de la pasarela no debe lanzar aquí (va a
`REEMBOLSO_FALLIDO`); cualquier excepción revierte la cancelación." Lanza `IllegalStateException` (sin pago PAGADO) e
`IllegalArgumentException` (monto ≤ 0 o > pago).

**`PagoServiceImpl`** (`…/pagos/service/impl/PagoServiceImpl.java`): `@Transactional(propagation = MANDATORY)`.
Hoy: `pagos.findByReservaIdAndEstado(reservaId, PAGADO)` → valida `0 < monto ≤ pago.monto` → audita
`REEMBOLSO_SOLICITADO` sobre entidad `pago` con detalle `origen=CANCELACION;reservaId=…;monto=…`. Constante pública
`ACCION_REEMBOLSO_SOLICITADO`. Dependencias: `PagoRepository`, `AuditoriaService`. **No** inyecta `PasarelaPago`
(test `2_Backend/src/test/java/…/pagos/service/impl/PagoServiceImplTest.java:90` lo verifica: "HU-15 R24: no depende de
PasarelaPago (HU-16 hará la llamada)" — HU-16 debe actualizar/retirar ese test).

**`PagoRepository`** (`…/pagos/repository/PagoRepository.java`, `JpaRepository<Pago, Long>`):
`existsByIdTransaccionPasarela`, `countByReservaIdAndEstadoAndCreadoEnGreaterThanEqual`, `countByReservaIdAndEstado`,
`findFirstByReservaIdOrderByCreadoEnDescIdDesc`, `findByReservaIdAndEstado(reservaId, estado)` (comentario: solo con
PAGADO, `uq_pago_reserva_pagado` garantiza ≤ 1). No hay consulta con bloqueo (`FOR UPDATE`) sobre `pago`.

**Pasarela (Adapter ya existe):** `…/pagos/service/pasarela/PasarelaPago.java` (Target, sin tipos del proveedor):
```java
CheckoutCreado crearCheckout(SolicitudCheckout solicitud);
void cerrarCheckout(String idCheckout);
Optional<String> idNotificado(NotificacionRecibida notificacion);
PagoConsultado consultarPago(String idTransaccion);
```
Fallas de red/API → `comun.error.ServicioExternoException` (503 genérico). **No existe `reembolsar`**; architecture.md
l. 286: "HU-16 le agrega `reembolsar`".
- `MercadoPagoAdapter` (`@ConditionalOnProperty espacioya.pagos.pasarela=mercadopago`): `RestClient` + HMAC, sin SDK
  (DT-1); timeouts 3 s / 7 s; helper privado `llamar(operacion, Supplier)` que traduce `RestClientResponseException` /
  `RestClientException` a `ServicioExternoException` sin filtrar el cuerpo del proveedor (R54). Base URL
  `espacioya.pagos.url-api` (`https://api.mercadopago.com`), Bearer con `PAGOS_LLAVE_SECRETA`. Reembolso real en MP
  sería `POST /v1/payments/{id}/refunds` (con `amount` para parcial; admite `X-Idempotency-Key`) — no implementado.
- `PasarelaSimulada` (`pasarela=simulada`, usada en pruebas, dev y UAT): ids `sim-<reserva>-<APROBADO|RECHAZADO|SIN_RESULTADO>-<n>`;
  mapa en memoria de montos (se pierde al reiniciar). Método extra `notificacionDe` fuera del Target.
  architecture.md l. 317 y HU-13 RS-5: "HU-16 implementa `reembolsar` también en `PasarelaSimulada`" y decide qué hacer
  con las filas `sim-` (ningún dinero se movió). Hoy no tiene forma de simular un fallo de reembolso (CA2 la necesitará
  para la demo/UAT: p. ej. un modo o regla configurable).
- Sin fábrica: la implementación se elige por `@ConditionalOnProperty` + DI. Propiedades `espacioya.pagos.*`
  (architecture.md l. 295-300).

**Otros de HU-13 relevantes:** `WebhookPagoService` y `SesionesCheckout` usan `TransactionTemplate` para no retener
filas durante llamadas externas (patrón a reutilizar en el Command). `AvisosPago` (correos tras confirmar).

## 2. Cancelación (HU-15): qué hace hoy y qué deja para HU-16

**Archivos analizados:** `…/reservas/service/CancelacionReservaService.java`, `…/reservas/domain/{CalculoReembolso,PoliticaReembolso}.java`,
`…/reservas/service/AvisosCancelacion.java`, `specs/HU-15/design.md` § 11.1 (l. 516-538) y § 10, `specs/HU-15/requirements.md`
(D7, D12, l. 247), `progress/impl_HU-15.md`, `progress/review_HU-15.md` §"Límite con HU-16", `progress/history.md` l. 1047-1067.

**`CancelacionReservaService.confirmar(usuario, id, quien, datos, ip)`** (`@Transactional`): rol → id → monto visto →
`findEspacioIdById` → `disponibilidad.bloquearEspacio` → `findParaActualizar` → propiedad → ahora → `exigirCancelable` →
`calculo.calcular(...)` (Strategy) → compara monto visto → `reserva.cancelar(quien, inicio, monto, ahora)` →
`bloques.desactivarDeReserva` → **si `monto > 0`: `pagos.reembolsarCancelacion(new ReembolsoPorCancelacion(reservaId,
monto, usuario.usuarioId(), ip))`** → auditoría `RESERVA_CANCELADA` → `avisos.programar(...)` (2 correos tras commit).
Devuelve `CancelacionDTO(id, estado, quien, ahora, politica, porcentaje, montoReembolso)`.

**Strategy:** `PoliticaReembolso` (`codigo`, `aplicaA`, `porcentaje`, `monto` HALF_UP escala 2) + 3 ConcreteStrategy
(usuario ≥48 h 100 %, <48 h 0 %, arrendador 100 %) + `CalculoReembolso.politicaFija()` (Context). **Solo calcula el
monto**; no toca pagos.

**Estado que deja hoy:** reserva `CANCELADA` con `cancelada_por`, `cancelada_en`, `monto_reembolso` (V4_2). Pago
**sigue `PAGADO`**; no hay tabla `reembolso`; solo la auditoría `REEMBOLSO_SOLICITADO`. No se llama a la pasarela.

**Contrato explícito con HU-16** (`specs/HU-15/design.md` § 11.1, replicado en `docs/architecture.md` l. 302-311):
1. Se llama una sola vez por reserva, **dentro** de la transacción de la cancelación, con reserva ya `CANCELADA`, bloques
   liberados y `monto > 0` (con 0 % no se llama, D6).
2. Monto = el de la política, ≤ `pago.monto` (con HU-13 son iguales: 100 %). `reserva.monto_reembolso` guarda el mismo.
3. HU-16 reemplaza el cuerpo (misma firma y `MANDATORY`): crea fila `reembolso` (`origen = CANCELACION`, "un reembolso
   por pago y origen"), arma el Command `SolicitudReembolso` y lo ejecuta **después del commit** (`DespuesDelCommit`)
   contra `PasarelaPago.reembolsar`, sin retener filas durante la llamada externa. Puede conservar o no
   `REEMBOLSO_SOLICITADO` (si no, ajusta R24 de HU-15 en su spec).
4. Si el cuerpo lanza, se revierte la cancelación (R25). Un fallo de pasarela **no** lanza: va a `REEMBOLSO_FALLIDO`.
5. Filas `sim-` (D12, RS-5): HU-15 no las filtra; HU-16 decide qué hace `PasarelaSimulada.reembolsar`.
- `specs/HU-15/design.md` l. 484: "HU-16 toma la siguiente `V4_n` libre"; l. 505: "La tabla `reembolso` es de HU-16 (D7)";
  l. 663 (alternativa descartada): crear en HU-15 la tabla y fila `PENDIENTE` — se dejó para HU-16.
- `progress/review_HU-15.md` l. 102-107: límite respetado (sin `PasarelaPago`, sin cambio de estado, sin tabla).
- Frontend HU-15 ya muestra "El reembolso de S/ X está en proceso." (`3_frontend/src/pages/reservas/textosReservas.js:64`,
  usado en `MisReservas.jsx:60-63`).

**Notas para el diseño:**
- `DespuesDelCommit.ejecutar` corre en `afterCommit`: ahí la transacción original ya terminó; cualquier escritura del
  Command (estado de `reembolso`/`pago`, auditoría de intento) necesita **su propia transacción** (`TransactionTemplate`
  con `PROPAGATION_REQUIRES_NEW`, o un bean con `REQUIRES_NEW`). `AuditoriaService.registrar` es `@Transactional`
  (REQUIRED) — dentro de `afterCommit` sin tx nueva podría no persistir. `DespuesDelCommit` además traga
  `RuntimeException` con un `log.warn` genérico.
- Si el proceso cae entre el commit y la llamada, el reembolso queda `PENDIENTE` sin ejecutar: considerar que el admin
  vea también `PENDIENTE` antiguos, o un barrido (`ExpiracionReservas`/`ProgramacionExpiracion` en `reservas/service`
  son el precedente de tarea programada).

## 3. Migraciones Flyway y modelo de datos

**Archivos analizados:** `2_Backend/src/main/resources/db/migration/*.sql`, `docs/modelo-datos.md` (l. 25-50, 89),
`docs/architecture.md` l. 74-83.

- Última versión: **`V4_2__cancelacion_reserva.sql`** (HU-15). Lista: V1_1…V1_5, V2_1…V2_4, V3_1…V3_3, `V4_1__pago.sql`,
  `V4_2__cancelacion_reserva.sql`. HU-16 → **`V4_3__reembolso.sql`** (siguiente `V4_n` libre, regla de `docs/conventions.md`).
- `V4_1__pago.sql`: tabla `pago` con `ck_pago_estado` que **ya incluye `REEMBOLSADO` y `REEMBOLSO_FALLIDO`**,
  `uq_pago_id_transaccion`, `ck_pago_monto > 0`, `ix_pago_reserva_estado (reserva_id, estado, creado_en)` y
  `uq_pago_reserva_pagado` parcial (`WHERE estado='PAGADO'`). Tabla `checkout_pago`. `pago` sin cascada desde reserva.
- `V4_2__cancelacion_reserva.sql`: solo altera `reserva` (`cancelada_por`, `cancelada_en`, `monto_reembolso` +
  CHECKs `ck_reserva_cancelada_por`, `ck_reserva_cancelacion`).
- **No existe tabla `reembolso`.** `docs/modelo-datos.md:29`: `reembolso` — id, pago_id, monto, origen (CANCELACION,
  RECLAMO), estado, intentos, creado_en — historias HU-16, HU-26. `:50`: restricción "Un reembolso por pago y origen —
  Una cancelación no genera dos reembolsos — HU-16" (→ `UNIQUE (pago_id, origen)`). `:89` ER: `PAGO ||--o{ REEMBOLSO :
  genera`. `docs/architecture.md:78`: misma regla en "Integridad también en la base de datos".
- El modelo no fija los valores de `reembolso.estado` (candidatos: `PENDIENTE`, `REEMBOLSADO`, `FALLIDO`) ni columnas
  para id del reembolso en la pasarela, `actualizado_en` o último error; HU-16 debe decidirlo (y actualizar
  `modelo-datos.md`). HU-26 reutilizará la tabla con `origen = RECLAMO` y montos parciales.
- Ambigüedad a resolver en el spec: `pago.estado` pasa a `REEMBOLSADO`/`REEMBOLSO_FALLIDO` (RN-07, architecture.md
  l. 438-440) **y** `reembolso.estado` existe: definir cuál es la fuente de verdad y cómo se mantienen coherentes. HU-28
  cuenta `PAGADO` y también los reembolsados (architecture.md l. 314).

## 4. Notificaciones y componentes transversales de `comun`

**Archivos analizados:** `…/cuentas/service/NotificadorCorreo.java`, `NotificadorCorreoSimulado.java`,
`…/pagos/service/AvisosPago.java`, `…/reservas/service/AvisosCancelacion.java`,
`…/comun/transaccion/DespuesDelCommit.java`, `…/comun/auditoria/AuditoriaService.java`,
`…/comun/error/ServicioExternoException.java`, `…/comun/limite/{ReglaLimite,LimitadorIntentos,FiltroLimiteIntentos}.java`,
`…/comun/seguridad/{FiltroIpsAdmin,IpsPermitidasAdmin}.java`, `…/comun/web/IpCliente.java`.

- **`NotificadorCorreo`** (en `cuentas.service`, no en `comun`; HU-25 lo moverá a `comun/correo` con Factory Method):
  métodos `notificarVerificacion`, `notificarRecuperacion`, `notificarIdentidadAprobada/Rechazada`,
  `notificarCuentaSuspendida/Reactivada`, `notificarPagoConfirmado(correo, ComprobantePago)`,
  `notificarReservaPagada(correo, AvisoReservaPagada)`, `notificarCancelacionAlUsuario(correo, AvisoCancelacionUsuario)`,
  `notificarCancelacionAlArrendador(...)`. Implementación única `NotificadorCorreoSimulado` (log). **No hay aviso de
  reembolso**; HU-16 no lo exige en sus CA, pero RNF-USA-04 lista "reembolso" entre los avisos clave. Si se agrega,
  patrón: `AvisosX.programar(...)` lee correos con `CuentaService.correoDe` y envía con `DespuesDelCommit`; coordinar
  con Jorge Quispe (HU-25), como hizo HU-15 (§ 11.2).
- **Auditoría:** `AuditoriaService.registrar(EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle))`,
  `@Transactional`. Cubre STRIDE (R) "registro de cada intento y resultado" (RNF-SEG-12 incluye reembolsos). En el
  reintento del admin, quien actúa sale de la sesión y la IP de `IpCliente.de(peticion)`; en el intento automático tras
  commit, `solicitadoPor`/`ip` vienen de `ReembolsoPorCancelacion`.
- **`DespuesDelCommit.ejecutar(Runnable)`**: corre tras commit, atrapa fallos (ver nota § 2 sobre transacción nueva).
- **Errores:** `ServicioExternoException` (503, `ErrorDTO.MENSAJE_SERVICIO_EXTERNO`), `ReglaNegocioException` (400),
  `RecursoNoEncontradoException` (404), `AccesoDenegadoException` (403), `ManejadorGlobalErrores`.
- **Rate limiting:** `ReglaLimite(metodo, ruta, maximo, ventana[, grupo])` + `FiltroLimiteIntentos`/`LimitadorIntentos`;
  reglas declaradas como beans por módulo (ej. `cuentas/controller/ReglasLimiteCuentas.java`,
  `busqueda/controller/LimiteCatalogo.java`). Opcional para el botón de reintento (límites en RNF-SEG-17).
- **Panel admin:** rutas `/api/admin/**` pasan por `comun/seguridad/FiltroIpsAdmin` (`ADMIN_IPS_PERMITIDAS`); el rol
  `ADMIN` lo valida el servicio. Precedente: `…/administracion/controller/CuentaAdminController.java`
  (`/api/admin/cuentas`, `CacheControl.noStore()`, `@AuthenticationPrincipal UsuarioAutenticado`, `IpCliente.de`,
  `POST /{id}/accion` → 201). El listado/reintento de reembolsos fallidos encaja como `/api/admin/reembolsos` (módulo
  `pagos` per architecture.md l. 54, o `administracion` vía `PagoService` — decidir en el spec).

## 5. Frontend

**Archivos analizados:** `3_frontend/src/api/{pagos,reservas,administracion,cliente}.js`, `3_frontend/src/App.jsx`,
`3_frontend/src/pages/reservas/{MisReservas.jsx,CancelarReserva.jsx,textosReservas.js}`,
`3_frontend/src/pages/administracion/*.jsx`.

- `api/pagos.js`: `iniciarPago(reservaId)`, `obtenerEstadoPago(reservaId)`, `obtenerSimulacion`, `simularPago` (HU-13).
  Nada de reembolsos.
- `api/reservas.js`: `crearReserva`, `obtenerReserva`, `listarMisReservas`, `listarReservasDeMisEspacios`,
  `obtenerCancelacion`, `cancelarReserva(id, montoReembolsoVisto)`, `obtenerCancelacionDeMiEspacio`,
  `cancelarReservaDeMiEspacio` (HU-15).
- `api/administracion.js`: identidades (HU-06) y cuentas (HU-09). **No hay funciones de reembolsos.** Todas usan
  `peticion` de `api/cliente.js`.
- Pantallas: ninguna muestra el estado real del reembolso. Solo el mensaje fijo "El reembolso de S/ X está en proceso."
  tras cancelar (`textosReservas.js:64`, `MisReservas.jsx:60-63`) y la línea de política en `CancelarReserva.jsx`.
  `ReservaDTO` (`…/reservas/dto/ReservaDTO.java`) no trae estado de reembolso; `EstadoPagoDTO` es
  `(ultimoResultado, intentosAgotados, pagoDisponible)`.
- Panel admin: rutas `/admin/identidades`, `/admin/identidades/:id`, `/admin/cuentas`, `/admin/cuentas/:id`
  (`App.jsx:219-244`), páginas en `3_frontend/src/pages/administracion/`. CA2 requiere una pantalla nueva (p. ej.
  `/admin/reembolsos`, lista de fallidos con botón "Reintentar") y funciones nuevas en `api/administracion.js` (o
  `api/pagos.js`).

## 6. `docs/requerimientos.md` y `docs/architecture.md` sobre reembolsos, Command y Adapter

**Archivos analizados:** `docs/requerimientos.md` (l. 21-22, 112-113, 152-157, 186, 195, 216, 231, 254, 283),
`docs/architecture.md` (l. 54, 58-67, 78, 121, 141, 282-317, 349-375, 406-409, 438-443, 465, 476, 499).

**requerimientos.md**
- RF-E-05 (HU-16 CA1, Alta): solicitar a la pasarela contra el pago original → "reembolsada" al confirmarse.
- RF-E-06 (HU-16 CA2, Alta): falla → "reembolso fallido", visible en panel admin, reintento con botón.
- RNF-SEG-07: montos (incluido reembolso) solo en servidor. RNF-SEG-11 (T): reembolsos idempotentes y solo contra el
  pago original. RNF-SEG-12 (R): auditoría incluye reembolsos. RNF-ARQ-08: pasarela detrás de interfaz propia (Adapter;
  HU-13, HU-16, HU-25). RNF-USA-04: aviso por correo de reembolso (evento clave).
- RN-07: estados de transacción: pagada → reembolsada, o reembolso fallido (admin puede reintentarlo).
- Actores (l. 21-22): el admin "gestiona reembolsos fallidos"; la pasarela externa ofrece "checkout, webhook firmado y
  reembolsos".
- Fuera de alcance (l. 231): "reembolso de pagos tras expirar la reserva" (los `PAGADO_TARDIO` siguen manuales; HU-13 D2).
- Trazabilidad (l. 254): HU-16 ★ → RF-E-05, 06; SEG-11, 12; ARQ-08; RN-07. Componente "Adaptador de la pasarela de pago"
  (l. 283): primera HU-13, lo reutilizan HU-16, HU-26, HU-28.

**architecture.md**
- l. 54: `pagos` contiene HU-13, HU-16, HU-18, HU-19, HU-28.
- l. 58-67: entre módulos solo interfaces `service`; integraciones externas detrás de Adapter.
- l. 78: integridad en BD: "un reembolso por pago y origen (HU-16)".
- l. 286: `PasarelaPago` — "HU-16 le agrega `reembolsar`". l. 302-311: contrato de `PagoService.reembolsarCancelacion`.
  l. 313-317: filas `sim-`; HU-16 implementa `reembolsar` también en `PasarelaSimulada`.
- l. 371: `reserva.monto_reembolso` lo lee HU-16 sin parsear auditoría.
- l. 406-409 (PCI DSS): el estado del pago cambia solo por webhook firmado; con la simulada no se demuestra (C1/C2 de
  HU-13). Para el reembolso, la respuesta síncrona de la API (o un webhook de refund) debe confirmar el cambio a
  `REEMBOLSADO` — decidir en el spec.
- l. 438-443: Pago `PAGADO` → `REEMBOLSADO` o `REEMBOLSO_FALLIDO` (el admin puede reintentarlo).
- **l. 465 (Patrones):** "HU-16 ★ | Command, Adapter | `pagos`: `SolicitudReembolso` con `ejecutar()`, idempotente y
  reintentable; reutiliza el adaptador de HU-13". El `design.md` debe mostrar el patrón en diagramas de clases y
  secuencia (l. 457-458).
- l. 476: no usar el SDK de la pasarela fuera de su adaptador. l. 499: devolución de `PAGADO_TARDIO` sigue manual.

**Resumen de lo que HU-16 debe crear (según docs):** `PasarelaPago.reembolsar(...)` + implementaciones en
`MercadoPagoAdapter` y `PasarelaSimulada`; tabla `reembolso` (`V4_3`) con `UNIQUE (pago_id, origen)`; entidad/repositorio
de reembolso; Command `SolicitudReembolso.ejecutar()` idempotente y reintentable; nuevo cuerpo de
`PagoServiceImpl.reembolsarCancelacion` (misma firma, `MANDATORY`, ejecución tras commit); métodos de dominio de estado
en `Pago`; endpoints admin de listado y reintento de fallidos; pantalla admin en el frontend; auditoría por intento;
actualizar `PagoServiceImplTest`, `docs/architecture.md` y `docs/modelo-datos.md`.
