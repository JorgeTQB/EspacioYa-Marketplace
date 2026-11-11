# HU-27 — Tasks: Reportes y moderación de publicaciones y reseñas

> Requirements: `specs/HU-27/requirements.md` (R1–R78). Design: `specs/HU-27/design.md`. Cada test lleva
> `@DisplayName("HU-27 R<n>: …")`. Las `*IT` llevan `@Import(TestcontainersConfiguration.class)`; el admin de las IT
> necesita `MfaDePrueba.activar` (HU-17). Las dos preguntas abiertas ya están respondidas (2026-11-09: ocultar es
> definitivo; ocultar por fraude cancela reservas). Las tasks T32–T38 (ocultar por fraude) se agregaron con ese cambio:
> T32–T37 se ejecutan **después de T12 y antes de T13**, y T38 después de T23; los números se mantienen para no romper
> referencias.

## Base de datos y dominio

- [x] T1 — Crear `V8_2__reportes.sql` con `reporte` (incluido `por_fraude` y `ck_reporte_fraude`), restricciones e
  índices, el cambio de `ck_reserva_cancelada_por` y `ix_reserva_espacio_confirmada` (design § 8); antes, comprobar en
  `develop` que `V8_2` sigue libre. Cubre: R11, R56, R57, R58, R74, R75.
- [x] T2 — Añadir `ReporteRestriccionesIT`: `uq_reporte_espacio` y `uq_reporte_resena` (y que otro reportante sí puede),
  `CHECK` de tipo, motivo, estado, coherencia tipo–contenido y de la decisión, `ck_reporte_fraude` (rechaza `por_fraude`
  en `POR_REVISAR`, `MANTENIDO` o en un reporte de reseña), FK sin cascada hacia `usuario`, `espacio` y `resena`, y
  `reserva.cancelada_por` = `ADMIN` aceptado y `'OTRO'` rechazado. Cubre: R11, R56, R57, R58, R74, R75.
- [x] T3 — Añadir los enums `EstadoReporte`, `TipoObjetivoReporte`, `MotivoReporte`, `DecisionReporte` (con
  `OCULTAR_POR_FRAUDE`), la regla `ReglasReporte` y la entidad `Reporte` (`deEspacio`, `deResena`, `getObjetivoId`,
  `estaPorRevisar`, `decision`, `isPorFraude`, `resolver`). Cubre: R1, R2, R17, R31, R33, R78.
- [x] T4 — Añadir `ReglasReporteTest` (inicio de la ventana = ahora − 24 h) y `ReporteTest` (fábricas por tipo,
  `resolver` a `MANTENIDO`, `OCULTADO` y `OCULTADO` con `porFraude`, `decision()` devuelve `OCULTAR_POR_FRAUDE` solo con
  `porFraude`, `IllegalStateException` al resolver dos veces y con `OCULTAR_POR_FRAUDE` en un reporte de reseña). Cubre:
  R17, R31, R33, R41, R63, R78.
- [x] T5 — Añadir `ReporteRepository` (design § 7). Cubre: R10, R16, R17, R20, R21, R24, R42, R59.

## Rol del admin (común)

- [x] T6 — Añadir `comun.seguridad.RolesSesion.exigirAdmin` y reemplazar las siete copias privadas de `exigirAdmin`
  (DT-13) sin cambiar su comportamiento. Cubre: R5, R26, R62.
- [x] T7 — Añadir `RolesSesionTest` (`null`, `USUARIO`, `ARRENDADOR` → 403; `ADMIN` pasa) y comprobar que las pruebas
  de 403 de los siete servicios siguen verdes. Cubre: R26, R62.

## Interfaces de `espacios` y `busqueda`

- [x] T8 — Añadir `Espacio.esOcultable` y `Espacio.ocultar` y ampliar `EspacioTest`: oculta desde `PUBLICADO` y
  `PAUSADO`, `IllegalStateException` desde `BORRADOR`, `BAJA` y `OCULTO`; un `OCULTO` no es editable, pausable,
  reanudable ni admite baja. Cubre: R32, R38, R39.
- [x] T9 — Añadir a `EstadoEspacioService` `ACCION_OCULTACION`, `MENSAJE_NO_OCULTABLE` y `ocultarComoAdmin`
  (`MANDATORY`: rol → `findParaReservar` → estado → `ocultar` → auditoría), y `ModeracionEspacios`,
  `EspacioReportable`, `EspacioModerado`, `EspacioOcultado` y `ModeracionEspaciosImpl`. Cubre: R7, R24, R32, R39, R46,
  R52, R62.
- [x] T10 — Ampliar `EstadoEspacioServiceTest` y añadir `ModeracionEspaciosImplTest`: `ocultarComoAdmin` deja `OCULTO`
  y audita `estado=PUBLICADO->OCULTO;actor=ADMIN;arrendadorId=…`; 400 *espacio no ocultable* desde `BAJA`/`OCULTO`/
  `BORRADOR` sin auditar; 403 sin leer con rol no admin; no consulta `ReservasDelEspacio`; `reportable` vacío para no
  visible o de arrendador no `ACTIVA`; `describir` vacío no consulta. Cubre: R7, R32, R39, R46, R52, R54, R62.
- [x] T11 — Añadir `Resena.estaVisible` y `Resena.ocultar`, `ResenaRepository.findParaActualizar`, `ModeracionResenas`,
  `ResenaReportable`, `ResenaModerada` y `ModeracionResenasImpl` (`reportable`, `describir`, `ocultar` `MANDATORY` con
  auditoría `OCULTACION_RESENA`); actualizar el javadoc de `ResenaService`. Cubre: R7, R25, R34, R40, R53, R62.
- [x] T12 — Añadir `ResenaTest` (`ocultar` desde `VISIBLE`; `IllegalStateException` si ya `OCULTA`) y
  `ModeracionResenasImplTest`: `reportable` vacío para `OCULTA`, inexistente o de espacio no visible; `ocultar` audita
  `estado=VISIBLE->OCULTA;actor=ADMIN;espacioId=…`; 400 *reseña no ocultable*; 403 sin leer. Cubre: R7, R34, R40, R53,
  R54, R62.

## Ocultar por fraude: cancelación de reservas (`reservas`, `cuentas`) — ejecutar tras T12

- [x] T32 — Añadir `QuienCancela.ADMIN`, el ConcreteStrategy `ReembolsoPorModeracion` (`MODERACION`, 100 %, solo
  `ADMIN`) y agregarlo a `CalculoReembolso.politicaFija()`. Cubre: R64, R67.
- [x] T33 — Añadir `ReembolsoPorModeracionTest` y ampliar el test de `CalculoReembolso`: con `ADMIN` aplica solo
  `MODERACION` con 100 % y monto = monto pagado, con 0 h, 47 h y 72 h de anticipación; con `USUARIO`/`ARRENDADOR` sigue
  aplicando una sola política y nunca `MODERACION`. Ampliar `ReservaTest`: `cancelar(ADMIN, …)` deja `CANCELADA` con
  `canceladaPor = ADMIN` y `admiteLiquidacion()` = `false`. Cubre: R64, R67, R68.
- [x] T34 — En `CancelacionReservaService`, rechazar `QuienCancela.ADMIN` (`exigirRol` y `case ADMIN` en los `switch`)
  y ampliar `CancelacionReservaServiceTest`: `vistaPrevia` y `confirmar` con `ADMIN` (aun con sesión `ADMIN`) →
  `AccesoDenegadoException` sin llamar al repositorio. En `QuienCancelo`, `"ADMIN"` → `"EspacioYa"`, con su test de
  correo (cuerpo con "Cancelada por: EspacioYa"). Cubre: R69, R77.
- [x] T35 — Añadir `ReservaRepository.findConfirmadasParaActualizar`, `AvisosCancelacion.programarAlUsuario`,
  `CancelacionPorModeracion`, `ReservasCanceladasPorModeracion` y `CancelacionPorModeracionImpl` (design § 7: rol →
  `bloquearEspacio` → bloqueo de confirmadas por id → por iniciar → Strategy → `cancelar` → `desactivarDeReserva` →
  `reembolsarCancelacion` → auditoría `RESERVA_CANCELADA` → correo al usuario). Cubre: R64, R65, R66, R69, R70, R71,
  R73, R76.
- [x] T36 — Añadir `CancelacionPorModeracionImplTest`: cancela solo las por iniciar (una a 1 min del inicio sí; una
  iniciada, una terminada, una `PENDIENTE_PAGO`, una `EXPIRADA` y una `CANCELADA` no); `cancelar(ADMIN, …,
  montoTotal)`, `desactivarDeReserva` y `reembolsarCancelacion(id, montoTotal, adminId, ip)` por cada una; auditoría
  `canceladaPor=ADMIN;espacioId=…;politica=MODERACION;porcentaje=100;monto=…`; `programarAlUsuario` por usuario y
  nunca el aviso al arrendador; devuelve la cantidad; 403 sin leer con rol no admin; `InOrder` `bloquearEspacio` →
  `findConfirmadasParaActualizar`; si `reembolsarCancelacion` lanza, la excepción se propaga. Y
  `AvisosCancelacionTest`: `programarAlUsuario` sin commit no envía y nunca llama al aviso del arrendador. Cubre: R64,
  R65, R66, R69, R70, R71, R72, R73, R76.
- [x] T37 — Si alguna IT de HU-15 fija los valores de `cancelada_por` o la lista de políticas, actualizarla; comprobar
  que las pruebas de HU-13, HU-15, HU-16 y HU-19 siguen verdes. Cubre: R67, R68, R74, R77.

## Reportar (CA1)

- [x] T13 — Añadir `CrearReporteDTO`, `ReporteCreadoDTO` y `ReporteService.reportar` con el orden del design § 4
  (`TransactionTemplate`, `bloquearCuenta`, cupo, `reportable`, propio, *ya reportado*, traducción de los índices,
  auditoría `REPORTE_CREADO`). Cubre: R1, R2, R3, R4, R5, R7, R8, R9, R10, R12, R16, R17, R18, R19, R50, R54.
- [x] T14 — Añadir `ReporteServiceTest`: un test por R1, R2, R4, R5 (403 sin llamar a `bloquearCuenta`), R7 (espacio y
  reseña), R8, R9, R10, R16, R17 (9 previos → se crea; 10 → 429), R19 (cupo agotado y contenido inexistente → 429, sin
  llamar a `reportable`), R50 y R54; `InOrder` para R18 (`bloquearCuenta` antes de contar). Cubre: R1, R2, R4, R5, R7,
  R8, R9, R10, R16, R17, R18, R19, R50, R54.
- [x] T15 — Añadir `ReporteController` con `POST /api/reportes`. Cubre: R3, R6, R13, R14, R15.
- [x] T16 — Añadir `ReporteControllerIT`: 201 para `USUARIO` y `ARRENDADOR` sobre espacio y reseña; 400 por campo
  (`tipoObjetivo` fuera de lista o número, `objetivoId` texto, decimal, 0; motivo fuera de catálogo); 401; 403 `ADMIN`;
  404 igual para inexistente, `PAUSADO`, arrendador suspendido y reseña `OCULTA`; 400 contenido propio (espacio y
  reseña); 400 *ya reportado*; campos extra ignorados; 11.º reporte en 24 h → 429 (con reloj fijo) y el que se creó hace
  24 h no cuenta; dos `POST` simultáneos iguales (uno 201, otro 400) y once simultáneos de un mismo usuario con 9 previos
  (solo uno entra); auditoría `REPORTE_CREADO`. Cubre: R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14,
  R15, R16, R17, R18, R50.

## Panel del admin y decisión (CA2, CA3)

- [x] T17 — Añadir `DatosEspacioOcultado`, `CorreoEspacioOcultado`, `FabricaEspacioOcultado` y el Client
  `AvisosModeracion` (un `DespuesDelCommit`). Cubre: R47, R48.
- [x] T18 — Añadir `CorreosModeracionTest` (asunto fijo; cuerpo con nombre y motivo, sin motivo del reporte ni datos del
  reportante; sin fraude, "se mantienen"; por fraude con n > 0, la cantidad, 100 % y "no se incluirán en tus
  liquidaciones"; por fraude con n = 0, su texto) y `AvisosModeracionTest` (sin transacción confirmada no envía). Cubre:
  R47, R48, R70.
- [x] T19 — Añadir `DecisionReporteDTO`, los DTO de salida (`ReporteResumenDTO`, `PaginaReportesDTO`,
  `DetalleReporteAdminDTO` con `reservasCanceladas`, `EspacioReportadoDTO`, `ResenaReportadaDTO`) y `VistaReportes`
  (`decision` derivada con `por_fraude`). Cubre: R20, R21, R24, R25, R43, R44, R45, R78.
- [x] T20 — Añadir `ReporteAdminService` (`listar`, `detalle`, `decidir` con el orden del design § 4: rol, sanitizar,
  `findById`, *fraude solo en espacios*, bloqueo de hermanos por id, *ya resuelto*, `ocultar` del módulo, si es por
  fraude `CancelacionPorModeracion.cancelarPorFraude`, `resolver` de cada hermano, auditoría `REPORTE_RESUELTO` con
  `reservasCanceladas`, correo solo al ocultar un espacio, con `porFraude` y la cantidad). Cubre: R20, R21, R22, R23,
  R24, R25, R26, R27, R31, R32, R33, R34, R39, R40, R41, R42, R44, R45, R46, R47, R48, R49, R51, R54, R63, R64, R72.
- [x] T21 — Añadir `ReporteAdminServiceTest`: un test por R20, R21, R22, R23, R24 (sin correo ni datos del reportante),
  R25, R26 (403 sin leer), R31 (mantiene y resuelve los hermanos sin llamar a `ocultar`), R32, R33, R34, R39 y R40 (no
  resuelve, no audita, no envía, no cancela), R41, R44 (solo etiquetas → vacío), R45, R46 (`OCULTAR` nunca llama a
  `CancelacionPorModeracion`), R49 (mantener y ocultar reseña no envían correo), R48 (excepción en `ocultar` → no se
  registra correo), R51 (con `reservasCanceladas`), R63 (`OCULTAR_POR_FRAUDE` en reseña → 400 en `decision` sin
  bloquear ni ocultar), R64 (`OCULTAR_POR_FRAUDE` llama a `cancelarPorFraude` después de `ocultar` y pasa la cantidad a
  `DatosEspacioOcultado` y a la respuesta) y R72 (excepción en `cancelarPorFraude` → se propaga, no resuelve, no
  registra correo); `InOrder` para R42 y R73 (hermanos → `ocultar` → `cancelarPorFraude`). Cubre: R20, R21, R22, R23,
  R24, R25, R26, R31, R32, R33, R34, R39, R40, R41, R42, R44, R45, R46, R48, R49, R51, R63, R64, R72, R73.
- [x] T22 — Añadir `ReporteAdminController` (`/api/admin/reportes`, `no-store`). Cubre: R20, R21, R24, R25, R27, R28,
  R43.
- [x] T23 — Añadir `ReporteAdminControllerIT`: cola por revisar (más antiguo primero) y resueltos con su orden y
  paginación; detalle de espacio y de reseña; 401 sin sesión; 403 para `USUARIO`/`ARRENDADOR`; 403 desde una IP no
  permitida; 401 para admin sin MFA; 404 inexistente y mal escrito; `no-store`; 400 `decision` y `motivoDecision` (en blanco, 501, solo
  etiquetas); **ocultar un espacio con 3 reportes**: espacio `OCULTO`, los 3 `OCULTADO`, no aparece en `GET
  /api/espacios`, su ficha da 404, reservarlo da 404, sus reservas confirmadas siguen `CONFIRMADA`, el arrendador no lo
  puede editar, pausar, reanudar ni dar de baja y el admin no lo puede pausar ni dar de baja (400), correo con el doble
  de `EnvioCorreo`, auditoría `OCULTACION_ESPACIO` y `REPORTE_RESUELTO`; **ocultar una reseña**: `OCULTA`, fuera de
  `GET /api/espacios/{id}/resenas` y del promedio y total de la ficha, sin correo; **mantener**: contenido igual, sin
  correo; espacio `BAJA` → 400 *espacio no ocultable* y el reporte sigue `POR_REVISAR`; segunda decisión → 400 *ya
  resuelto*; dos decisiones simultáneas sobre reportes hermanos (una 200, otra 400); `PUT`/`DELETE` → 405. Cubre: R20,
  R21, R24, R25, R26, R27, R28, R29, R30, R6, R31, R32, R33, R34, R35, R36, R37, R38, R39, R41, R42, R43, R44, R45, R46,
  R47, R49, R51, R52, R53, R55.
- [x] T38 — Añadir `OcultarPorFraudeIT` (Testcontainers, reloj fijo, doble de `EnvioCorreo`/`NotificadorCorreo` y
  pasarela simulada): espacio con una reserva `CONFIRMADA` pagada para mañana, otra en curso, otra terminada, una
  `PENDIENTE_PAGO` vigente y una `CANCELADA`; `OCULTAR_POR_FRAUDE` → 200 con `reservasCanceladas = 1` y `decision =
  OCULTAR_POR_FRAUDE`; el espacio `OCULTO`; los reportes `OCULTADO` con `por_fraude`; solo la de mañana `CANCELADA` con
  `cancelada_por = ADMIN` y `monto_reembolso = monto_total`, sus bloques inactivos; un reembolso `PENDIENTE` → tras el
  commit `REEMBOLSADO` (origen `CANCELACION`, monto total); auditorías `RESERVA_CANCELADA` y `REEMBOLSO_SOLICITADO`;
  correo de cancelación al usuario ("Cancelada por: EspacioYa", 100 %) y un solo correo al arrendador con la cantidad;
  las otras reservas sin cambios; generar liquidaciones con un corte posterior no incluye la cancelada. `OCULTAR_POR_FRAUDE`
  sobre un reporte de reseña → 400 en `decision`. Rollback: con un doble de `PagoService` que lanza al reembolsar (o una
  reserva `CONFIRMADA` sin pago `PAGADO`) → 500, espacio sigue `PUBLICADO`, reportes `POR_REVISAR`, reserva
  `CONFIRMADA`, sin reembolso, sin auditorías y sin correos. Concurrencia: `confirmar` de HU-15 (usuario) y
  `OCULTAR_POR_FRAUDE` simultáneos sobre la misma reserva → un solo reembolso y la reserva `CANCELADA` una vez. HU-15 con
  `ADMIN`: las rutas de usuario y arrendador no aceptan a un admin (403). Cubre: R45, R63, R64, R65, R66, R68, R69, R70,
  R71, R72, R73, R74, R77, R78.

## Datos personales (HU-22)

- [x] T24 — Añadir `ReporteExportadoDTO` y `DatosPersonalesReportesImpl` (`@Order(5)`); mover el doble
  `ParticipanteQueFalla` a `@Order(6)` y borrar `reporte` en los soportes de IT que borran `resena`, `espacio` o
  `usuario`. Cubre: R59, R60, R61.
- [x] T25 — Añadir `DatosPersonalesReportesImplTest` y ampliar la IT de exportación y eliminación de HU-22: sección
  `reportes` para usuario y arrendador (vacía si no reportó, sin `motivoDecision`), sin impedimento, y los reportes del
  titular y los que decidió como admin intactos tras eliminar la cuenta. Cubre: R59, R60, R61.

## Frontend

- [x] T26 — Agregar a `api/administracion.js` `reportarContenido`, `listarReportesAdmin`, `obtenerReporteAdmin` y
  `decidirReporte`, y a `EtiquetaEstado` `VISIBLE`, `OCULTA`, `POR_REVISAR`, `MANTENIDO` y `OCULTADO` (design § 12.5).
  Cubre: pantallas 12.1–12.4 (CA1, CA2, CA3).
- [x] T27 — Añadir `pages/busqueda/ReportarContenido.jsx` y conectarlo en `FichaEspacio.jsx` ("Reportar publicación") y
  en `ResenasEspacio.jsx` ("Reportar reseña" en `acciones`), con los textos y motivos en `textosFicha.js`. Cubre:
  pantallas `/espacios/:id` y reseñas de la ficha (CA1).
- [x] T28 — Añadir `pages/administracion/ReportesAdmin.jsx` (reutiliza `SeccionLiquidaciones`) y `RevisarReporte.jsx`
  (contenido, formulario "Decidir" con la opción "Ocultar por fraude y cancelar sus reservas" solo para espacios, `Modal`
  según la decisión y éxito con `reservasCanceladas`; texto de la decisión `OCULTAR_POR_FRAUDE` en un reporte resuelto).
  Cubre: pantallas `/admin/reportes` y `/admin/reportes/:id` (CA2 ver contenido y motivo, mantener u ocultar con
  motivo; CA3 aviso de correo al ocultar; decisión del dueño: ocultar por fraude cancela reservas).
- [x] T29 — Rutas en `App.jsx`, enlace "Reportes" en `Panel.jsx` y textos en `textosAdministracion.js`;
  `npm --prefix 3_frontend run lint` y `npm --prefix 3_frontend run build` sin advertencias. Cubre: pantallas
  12.1–12.6.

## Documentación

- [x] T30 — Actualizar `docs/architecture.md`, `docs/modelo-datos.md` y `docs/frontend.md` según el design § 16
  (incluidos `CancelacionPorModeracion`, la política `MODERACION`, `cancelada_por = ADMIN`, `por_fraude` y el orden de
  bloqueos DT-23). Cubre: trazabilidad documental de R11, R56–R58, R62, R67, R73–R75 y § 12.5.

## Verificación

- [x] T31 — Ejecutar `./init.sh` (completo, con IT y build) en verde y documentar el mapa `R<n>` → test en
  `progress/impl_HU-27.md`. Cubre: R1–R78.
