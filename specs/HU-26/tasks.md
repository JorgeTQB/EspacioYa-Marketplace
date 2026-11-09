# HU-26 — Tasks: Reclamos sobre una reserva

> Requirements: `specs/HU-26/requirements.md` (R1–R70). Design: `specs/HU-26/design.md`. Cada test lleva
> `@DisplayName("HU-26 R<n>: …")`. Las `*IT` llevan `@Import(TestcontainersConfiguration.class)`. Antes de empezar,
> confirmar con la dueña las decisiones P1–P12 y las dos preguntas abiertas: si cambian, se ajustan primero los
> requirements y estas tasks.

## Base de datos y dominio

- [x] T1 — Crear `V8_1__reclamos.sql` con `reclamo`, `mensaje_reclamo`, restricciones e índices (design § 8); antes,
  comprobar en `develop` que `V8_1` sigue libre. Cubre: R13, R64, R65, R66.
- [x] T2 — Añadir `ReclamoRestriccionesIT`: único por reserva, `CHECK` de estado, motivo, papel y coherencia de la
  resolución, y FK sin cascada. Cubre: R13, R64, R65, R66.
- [x] T3 — Añadir los enums `EstadoReclamo`, `MotivoReclamo`, `PapelReclamo`, `TipoReembolsoReclamo`,
  `MotivoNoReclamable` y la regla pura `ReglasReclamo` (`PLAZO = ReglasLiquidacion.ESPERA`, 10 mensajes). Cubre: R4,
  R5, R6, R7, R27.
- [x] T4 — Añadir `ReglasReclamoTest`: bordes `inicio` (incluido), `fin + 48 h − 1 ns` (dentro), `fin + 48 h`
  (vencido), antes del inicio, no confirmada, `horaFin = 24`. Cubre: R4, R5, R6, R7.
- [x] T5 — Añadir las entidades `Reclamo` (`abrir`, `estaAbierto`, `papelDe`, `aFavorDe`, `resolverAFavorDelUsuario`,
  `resolverAFavorDelArrendador`, `TEXTO_ELIMINADO`) y `MensajeReclamo`. Cubre: R1, R23, R42, R43, R49, R54.
- [x] T6 — Añadir `ReclamoTest`: transiciones válidas, `IllegalStateException` al resolver dos veces o con monto ≤ 0,
  `papelDe` para usuario, arrendador y ajeno. Cubre: R42, R49, R54, R25.
- [x] T7 — Añadir `ReclamoRepository` y `MensajeReclamoRepository` (design § 7). Cubre: R12, R20, R29, R31, R35, R36,
  R69, R70.

## Interfaces de otros módulos

- [x] T8 — Añadir `reservas.service.ReservasParaReclamo`, `ReservaParaReclamo` y `ReservasParaReclamoImpl`
  (`delUsuario` y `confirmadasDe` con `ReservaDAO.delUsuario`; `describir` por lote sin DAO, con
  `CatalogoEspaciosService.nombres` y `arrendadoresDe`). Cubre: R3, R4, R9.
- [x] T9 — Añadir `ReservasParaReclamoImplTest`: reserva ajena o inexistente → 404 igual; inicio y fin en Lima con
  bloques inactivos y `horaFin = 24`; `describir` vacío no consulta. Cubre: R3, R4, R9.
- [x] T10 — Añadir `Reembolso.porReclamo`, `ReembolsoPorReclamo`, `LiquidacionDetalleRepository.existsByReservaId` y en
  `PagoService`/`PagoServiceImpl` los métodos `bloquearGeneracionLiquidaciones`, `reservaLiquidada` y
  `reembolsarReclamo` (extraer la parte común con `reembolsarCancelacion` sin cambiar su comportamiento; `Disparo
  .AUTOMATICO`). Cubre: R8, R21, R44, R45.
- [x] T11 — Ampliar `PagoServiceImplTest`: `reembolsarReclamo` crea `RECLAMO` `PENDIENTE`, audita
  `origen=RECLAMO;reservaId=…;monto=…` y entrega el comando con `AUTOMATICO`; rechaza monto 0, mayor que el pago,
  sin pago `PAGADO` y un segundo `RECLAMO`; `reservaLiquidada`; las pruebas de `reembolsarCancelacion` siguen verdes.
  Cubre: R44, R45, R8.
- [x] T12 — Añadir el puerto `pagos.service.puertos.ReclamosAbiertos` y filtrar en
  `GeneradorLiquidaciones.liquidablesPorArrendador` las reservas con reclamo abierto. Cubre: R20, R50.
- [x] T13 — Actualizar `GeneradorLiquidacionesTest` (nuevo constructor) y agregar casos: reserva con reclamo abierto
  queda fuera; sin reclamo entra; el puerto se consulta una vez por generación. Cubre: R20, R50.
- [x] T14 — Añadir `administracion.service.impl.ReclamosAbiertosImpl` (vacía → `Set.of()` sin consultar) y
  `ReclamosAbiertosImplIT`. Cubre: R20, R50.

## Abrir un reclamo

- [x] T15 — Añadir `LectoresJson`, `CrearReclamoDTO` y los DTO de salida (`DetalleReclamoDTO`, `MensajeReclamoDTO`,
  `ReclamablesDTO`, `ReclamoResumenDTO`, `PaginaReclamosDTO`) y `VistaReclamos`. Cubre: R2, R15, R16, R17, R32.
- [x] T16 — Añadir los correos `DatosReclamoAbierto`, `CorreoReclamoAbierto`, `FabricaReclamoAbierto` y el Client
  `AvisosReclamo` (un `DespuesDelCommit` por correo). Cubre: R22.
- [x] T17 — Añadir `CorreosReclamoTest`: asunto fijo y cuerpo del correo al arrendador al abrir, sin descripción ni
  datos del usuario. Cubre: R22.
- [x] T18 — Añadir `ReclamoService.abrir` y `reclamables` con el orden del design § 4 (`TransactionTemplate`, bloqueo de
  generación antes de `ahora`, traducción de `uq_reclamo_reserva`, auditoría `RECLAMO_ABIERTO`). Cubre: R1, R3, R4,
  R5, R6, R7, R8, R9, R10, R12, R14, R18, R19, R21, R22, R33, R60, R63.
- [x] T19 — Añadir `ReclamoServiceTest` (abrir): un test por R1, R3, R5, R6, R7, R8, R9, R10, R12, R19, R60 y R63;
  `InOrder` para R21 (`bloquearGeneracionLiquidaciones` antes de leer el reloj y de `reservaLiquidada`); el correo se
  registra para después del commit (R22); `reclamables` filtra ventana y reclamos existentes (R33). Cubre: R1, R3, R5,
  R6, R7, R8, R9, R10, R12, R19, R21, R22, R33, R60, R63.
- [x] T20 — Añadir `ConversionReclamos` y `ReclamoController` con `POST /api/reclamos` y `GET
  /api/reclamos/reclamables`. Cubre: R2, R10, R11, R15, R16, R17.
- [x] T21 — Añadir `ReclamoControllerIT` (abrir): 201 con el detalle, 400 por campo (`reservaId` como texto, decimal,
  0; motivo fuera de catálogo; descripción en blanco, de 1001 o solo etiquetas), 401, 403 para `ARRENDADOR` y `ADMIN`,
  404 ajena, texto guardado sin `<`/`>`, carrera de dos `POST` (una 201 y otra 400 *ya reclamada*) y `Cache-Control:
  no-store` en `reclamables`. Cubre: R1, R2, R10, R11, R13, R14, R15, R16, R17, R18, R19, R41.
- [x] T22 — Añadir `ReclamoLiquidacionIT`: reserva con reclamo abierto no entra en `POST /api/admin/liquidaciones`;
  resuelta a favor del arrendador entra en la siguiente; con reembolso por reclamo nunca entra; abrir un reclamo de una
  reserva ya liquidada responde *plazo vencido*. Cubre: R8, R20, R48, R50.

## Mensajes y consulta por las partes

- [x] T23 — Añadir `NuevoMensajeDTO` y en `ReclamoService` `listar`, `detalle` y `enviarMensaje` (`findParaActualizar`,
  partes, estado, límite por parte, auditoría `RECLAMO_MENSAJE`). Cubre: R23, R24, R25, R26, R27, R28, R29, R31, R32,
  R34, R61.
- [x] T24 — Ampliar `ReclamoServiceTest` (mensajes y consulta): un test por R23, R24, R25, R26, R27, R28, R31 (usuario y
  arrendador ven solo lo suyo), R32 (el arrendador no recibe `montoReembolso`; `puedeEscribir`), R34 y R61. Cubre:
  R23, R24, R25, R26, R27, R28, R31, R32, R34, R61.
- [x] T25 — Agregar a `ReclamoController` `GET /api/reclamos`, `GET /api/reclamos/{id}` y `POST
  /api/reclamos/{id}/mensajes`. Cubre: R23, R25, R26, R31, R32, R34, R41.
- [x] T26 — Ampliar `ReclamoControllerIT`: hilo de usuario y arrendador, 404 para un tercero y para id mal escrito, 403
  `ADMIN`, 400 *reclamo cerrado*, 11.º mensaje 400, dos mensajes simultáneos en el límite (solo uno entra), `DELETE`
  y `PUT` responden 405, paginación y `no-store`. Cubre: R23, R24, R25, R26, R27, R28, R29, R30, R31, R32, R34, R41.

## Panel del admin y resolución

- [x] T27 — Añadir `ResolucionReclamoDTO`, `DetalleReclamoAdminDTO`, los correos `DatosResolucion`,
  `CorreoResolucionUsuario`, `FabricaResolucionUsuario`, `CorreoResolucionArrendador`, `FabricaResolucionArrendador` y
  `AvisosReclamo.reclamoResuelto`. Cubre: R57, R58.
- [x] T28 — Ampliar `CorreosReclamoTest`: resolución al usuario (con monto si fue a su favor) y al arrendador (sin
  monto ni datos del usuario), para las dos decisiones. Cubre: R57, R58.
- [x] T29 — Añadir `ReclamoAdminService` (`listar`, `detalle`, `resolver` con el orden del design § 4, `exigirAdmin`,
  monto `TOTAL` del servidor, validación de `PARCIAL`, `reembolsarReclamo`, auditoría `RECLAMO_RESUELTO`, correos tras
  el commit). Cubre: R35, R36, R37, R38, R39, R40, R42, R43, R44, R46, R47, R49, R51, R52, R53, R54, R56, R57, R58,
  R59, R62, R63.
- [x] T30 — Añadir `ReclamoAdminServiceTest`: un test por R35, R36, R37, R38, R39, R42, R43, R46 (0, igual al pagado,
  3 decimales, texto), R47, R49, R51 (no llama a `reembolsarReclamo`), R52, R53, R54, R56, R59 (con una excepción en
  `reembolsarReclamo` no se registran correos) y R62. Cubre: R35, R36, R37, R38, R39, R42, R43, R46, R47, R49, R51,
  R52, R53, R54, R56, R59, R62.
- [x] T31 — Añadir `ReclamoAdminController` (`/api/admin/reclamos`, `no-store` en las `GET`). Cubre: R35, R36, R37,
  R40, R41.
- [x] T32 — Añadir `ReclamoAdminControllerIT`: listas abiertos y resueltos con su orden, detalle, 403 para
  `USUARIO`/`ARRENDADOR`, 404 inexistente y mal escrito, resolución total y parcial con la pasarela simulada (reembolso
  `RECLAMO` y pago `REEMBOLSADO`), con `PAGOS_SIMULADA_FALLOS_REEMBOLSO=1` (reembolso `FALLIDO`, reclamo igual
  resuelto y visible en `/api/admin/reembolsos`), a favor del arrendador sin reembolso, dos resoluciones simultáneas
  (una 200 y otra 400 *ya resuelto*), auditoría y correos con el doble de `EnvioCorreo`. Cubre: R35, R36, R37, R38,
  R40, R41, R42, R43, R44, R45, R49, R51, R54, R55, R56, R57, R58, R60, R62.

## Datos personales (HU-22)

- [x] T33 — Añadir `ReclamoExportadoDTO` y `DatosPersonalesReclamosImpl` (`@Order(4)`); mover el doble
  `ParticipanteQueFalla` a `@Order(5)`. Cubre: R67, R68, R69, R70.
- [x] T34 — Añadir `DatosPersonalesReclamosImplTest` y ampliar la IT de exportación y eliminación de HU-22: sección
  `reclamos` para usuario y arrendador sin textos de la otra parte, impedimento `reclamos` con reclamo abierto, textos
  reemplazados por "Texto eliminado." al eliminar. Cubre: R67, R68, R69, R70.

## Frontend

- [x] T35 — Agregar a `api/administracion.js` las ocho funciones del design § 12 y a `EtiquetaEstado` los tres estados
  del reclamo (§ 12.6). Cubre: pantallas 12.1–12.5 (CA1, CA2, CA3).
- [x] T36 — Añadir `pages/reservas/ReportarProblema.jsx` y conectar `listarReclamables` en `MisReservas.jsx`. Cubre:
  pantalla `/mis-reservas` (CA1).
- [x] T37 — Añadir `pages/administracion/MisReclamos.jsx`, `DetalleReclamo.jsx` y `HiloReclamo.jsx`. Cubre: pantallas
  `/reclamos` y `/reclamos/:id` (CA1 respuesta del arrendador, CA2 y CA3 vistos por las partes).
- [x] T38 — Añadir `pages/administracion/ReclamosAdmin.jsx` y `ResolverReclamo.jsx`. Cubre: pantallas `/admin/reclamos`
  y `/admin/reclamos/:id` (CA2, CA3).
- [x] T39 — Rutas en `App.jsx`, enlaces en `Panel.jsx`, impedimento `reclamos` en `DatosPersonales.jsx` y textos en
  `textosAdministracion.js`; `npm --prefix 3_frontend run lint` y `run build` sin advertencias. Cubre: pantallas
  12.1–12.7.

## Documentación

- [x] T40 — Actualizar `docs/architecture.md`, `docs/modelo-datos.md` y `docs/frontend.md` según el design § 16; §9
  punto 3 de `docs/requerimientos.md` solo si el PO confirmó la pregunta abierta 1. Cubre: trazabilidad documental de
  R20, R21, R64–R66 y § 12.6.

## Verificación

- [x] T41 — Ejecutar `./init.sh` (completo, con IT y build) en verde y documentar el mapa `R<n>` → test en
  `progress/impl_HU-26.md`. Cubre: R1–R70.
