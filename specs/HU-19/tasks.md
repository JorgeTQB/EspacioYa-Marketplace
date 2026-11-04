# HU-19 — Tasks: Liquidación semanal al arrendador

> Orden de ejecución. Cada task de backend lista los `R<n>` que cubre y su test; las de frontend, la pantalla o el CA
> visible (se verifican con lint, build y la caja negra en UAT). Cada test lleva `@DisplayName("HU-19 R<n>: …")`.
> Antes de empezar: el dueño aceptó D1–D8 y los supuestos S1–S15 de `requirements.md` al aprobar. **D3 sigue pendiente
> con el PO (Ariel Enriquez):** si lo rechaza, aplicar RK-1 de `design.md` § 17 antes de T6.
> Avisar al empezar a **Sebastian Garate** (T4), **Jorge Quispe** (T5), **Kevin Chan** y **Adriana Callupe** (T6) y
> **Ariel Enriquez** (T3), según `design.md` § 14.

## Base de datos y dominio (`pagos`)

- [x] T1 — Crear `V5_3__liquidacion.sql` (revisar en `develop` el siguiente `n` libre del sprint 5) con `liquidacion`,
  `liquidacion_detalle`, `uq_liquidacion_detalle_reserva`, `uq_liquidacion_detalle_pago`, los `CHECK` y los índices de
  `design.md` § 9. Cubre: R12, R23. Test: `LiquidacionRepositoryIT` (cada `CHECK` y cada `uq_*` violados dan error).
- [x] T2 — Crear `EstadoLiquidacion`, `MotivoNoPagable`, `AvisoArrendador`, `ReglasLiquidacion` (`TASA_COMISION`,
  `ESPERA`, `esLiquidable`, `comision`, `esSimulado`), `Liquidacion` (`generar`, `esPorPagar`, `motivoNoPagable`,
  `marcarPagada`) y `LiquidacionDetalle`. Agregar `ReglasPago.PREFIJO_SIMULADO` y usarla en `PasarelaSimulada` (sin
  cambio de conducta). Cubre: R8, R13, R15, R16, R20, R21, R25–R29. Tests: `ReglasLiquidacionTest`, `LiquidacionTest`
  (y `ReglasPagoTest`, `PasarelaSimuladaTest` siguen verdes).
- [x] T3 — Agregar `CuentaBancaria.numeroParaTransferir()` (Javadoc de D4). Cubre: R38. Test: `CuentaBancariaTest` (M).

## Extensiones de otros módulos

- [x] T4 — `espacios`: agregar `CatalogoEspaciosService.arrendadoresDe(Collection<Long>)` y su implementación (vacía →
  `Map.of()` sin consultar; sin filtro de estado). Actualizar dobles manuales si los hay. Cubre: R7.
  Test: `CatalogoEspaciosServiceImplTest` (M).
- [x] T5 — `cuentas`: agregar `CuentaService.arrendadoresSinIdentidadVerificada(Collection<Long>)`, su implementación y
  la consulta por lote en el repositorio de perfiles (vacía → `Set.of()` sin consultar; sin perfil cuenta como no
  verificada). Cubre: R37. Test: `CuentaServiceImplTest` (M).
- [x] T6 — `reservas`: agregar `Reserva.admiteLiquidacion()` (D3) y `ReservaConHorario.instanteDeFin()`. Cubre: R8, R10,
  R11. Tests: `ReservaTest` (M), `ReservaConHorarioTest` (M; `horaFin` 24 y zona de Lima).

## Puerto y repositorios

- [x] T7 — Crear el puerto `pagos.service.puertos.ReservasLiquidables` y el record `ReservaLiquidable`; crear
  `reservas.service.impl.ReservasLiquidablesImpl` (`readOnly`, una consulta por lote a reservas, horarios, nombres y
  arrendadores; vacía sin consultar; sin bloques → omitida). Cubre: R7, R8, R10, R11. Test: `ReservasLiquidablesImplTest`.
- [x] T8 — Crear `LiquidacionRepository` (`bloquearGeneracion` nativa `MANDATORY`, `findParaActualizar`,
  `findByEstadoIn`, `findByArrendadorId` con `Slice`, `existsByArrendadorIdAndEstadoIn`), `LiquidacionDetalleRepository`
  y `PagoRepository.pagadosSinLiquidar(incluirSimulados)`. Cubre: R9, R12, R21, R22, R33, R34, R42, R47.
  Test: `LiquidacionRepositoryIT` (pagos `PAGADO` con reembolso, ya liquidados, `RECHAZADO`/`PAGADO_TARDIO`/`MONTO_DISTINTO`
  excluidos; `sim-` incluidos o excluidos según el parámetro; orden y `hasNext` de los `Slice`).

## Servicios (`pagos`)

- [x] T9 — Crear `GeneradorLiquidaciones` (`MANDATORY`): bloqueo → corte → `pagadosSinLiquidar` (con la propiedad
  `espacioya.pagos.pasarela`) → `describir` → filtro `admitida` y 48 h → agrupación por arrendador → `GENERADA` o
  `PENDIENTE_CUENTA` → líneas → auditoría `LIQUIDACION_GENERADA` con avisos y simulada. Cubre: R7–R13, R15–R18, R20, R21,
  R24, R45. Test: `GeneradorLiquidacionesTest` (un test por R; `InOrder` bloqueo antes de leer el reloj; dos
  arrendadores, uno sin cuenta; borde exacto de 48 h; avisos no filtran).
- [x] T10 — Crear los DTO de `design.md` § 5 (`CuentaDestinoDTO.toString()` sin `numeroCompleto`;
  `MarcarLiquidacionPagadaDTO` con Bean Validation). Cubre: R19, R30, R36, R40, R44. Tests:
  `MarcarLiquidacionPagadaDTOTest` (nulo, vacío, 3 y 31 caracteres, espacio interno, caracteres no permitidos → error en
  `numeroOperacion`; 4 y 30 válidos) y una aserción de `toString` en `LiquidacionAdminServiceTest`.
- [x] T11 — Crear `VistaLiquidaciones` (líneas de la página en una consulta, `describir` una vez, nombres con
  `CuentaService.nombres` y "Arrendador #id" por defecto, avisos por lote; número completo solo si se pide). Cubre: R19,
  R36, R37, R40, R42, R44. Test: `LiquidacionAdminServiceTest` y `MisLiquidacionesServiceTest` (a través de los servicios,
  con el puerto y `CuentaService` simulados).
- [x] T12 — Crear `LiquidacionAdminService.generar` y `listar` (rol antes de leer; validación de `grupo` y `pagina` con
  `ValidacionException`; `Slice` de 20; orden por grupo; traducción de `uq_liquidacion_detalle_reserva` a
  `MENSAJE_GENERACION_CRUZADA`). Cubre: R2, R14, R18, R19, R22, R33, R34, R35, R37. Test: `LiquidacionAdminServiceTest`.
- [x] T13 — Crear `LiquidacionAdminService.detalle` (número completo y auditoría `CUENTA_BANCARIA_CONSULTADA_ADMIN` solo
  si es pagable) y `marcarPagada` (bloqueo `liquidacion → cuenta_bancaria`, *ahora* después de los bloqueos, los tres
  motivos con su mensaje, fecha de retención en Lima, auditoría `LIQUIDACION_PAGADA`, respuesta sin número completo).
  Cubre: R25–R29, R31, R36, R38, R39, R40, R46, R48. Test: `LiquidacionAdminServiceTest` (un test por R; R48 con
  `verifyNoInteractions` de `NotificadorCorreo`).
- [x] T14 — Crear `MisLiquidacionesService.listar` (403 antes de validar `pagina`; solo las del arrendador de la sesión;
  orden descendente; sin datos del usuario, del pago ni del admin) y `tieneLiquidacionesPendientes`. Cubre: R5, R6,
  R42, R43, R44, R47. Test: `MisLiquidacionesServiceTest`.

## Controladores e integración

- [x] T15 — Crear `LiquidacionAdminController` (`POST`, `GET`, `GET /{id}`, `POST /{id}/pago`, sin `@RequestBody` en la
  generación, `IpCliente.de`, `no-store`) y `MisLiquidacionesController` (`GET`, `no-store`). Cubre: R1–R5, R14, R31,
  R41. Test: `LiquidacionAdminControllerIT`, `MisLiquidacionesControllerIT`.
- [x] T16 — IT de punta a punta del admin: CA1 (reservar → pagar con la simulada → avanzar el reloj a fin + 48 h − 1 s:
  nada; a fin + 48 h: una liquidación `GENERADA` con bruto, comisión 10 % y neto, simulada y auditada); CA3 (arrendador
  sin cuenta → `PENDIENTE_CUENTA`; registra la cuenta → detalle pagable → marcar → `PAGADA`); CA2 (marcar con número de
  operación → `PAGADA`, destino enmascarado, auditoría); cuenta cambiada con `PUT` de HU-18 → 400 con la fecha; ya
  pagada → 400; número inválido → 400 de campo; id mal escrito o inexistente → 404; segunda generación → lista vacía;
  generación con cuerpo con montos → mismo resultado; número completo solo en el detalle pagable + auditoría y nunca en
  listas ni en la respuesta del pago; listas `por-pagar`/`pagadas` paginadas y con avisos de un arrendador suspendido;
  `grupo`/`pagina` inválidos → 400; sin sesión 401; otro rol 403; IP no permitida 403; `Cache-Control: no-store`.
  Cubre: R1, R2, R3, R7, R13–R16, R18–R20, R24–R31, R33–R41, R45, R46. Test: `LiquidacionAdminControllerIT`.
- [x] T17 — IT del arrendador: CA2 (ve su liquidación "PAGADA" con operación, fecha y destino enmascarado), CA3 (ve
  `PENDIENTE_CUENTA`), otro arrendador no la ve, sin datos del usuario ni del pago, `pagina` inválida → 400, sin sesión
  401, otro rol 403. Cubre: R4, R5, R6, R42, R43, R44. Test: `MisLiquidacionesControllerIT`.
- [x] T18 — IT de cancelaciones y reservas nuevas: cancelada por el usuario con < 48 h (0 %) → liquidada con
  `cancelada = true` al pasar fin + 48 h; cancelada con ≥ 48 h (100 %) o por el arrendador → fuera; segunda generación
  con una reserva nueva del mismo arrendador con una `PENDIENTE_CUENTA` previa → liquidación nueva, la vieja intacta.
  Cubre: R10, R11, R12, R17. Test: `LiquidacionCancelacionIT`.
- [x] T19 — IT de concurrencia (estilo `PagoConcurrenciaIT`, `CountDownLatch`): dos generaciones a la vez → ambas 200,
  cada reserva en una sola línea; dos marcados a la vez de la misma liquidación → uno 200 y otro 400 *ya pagada*, con el
  número de operación del primero. Cubre: R22, R32. Test: `LiquidacionConcurrenciaIT`.

## Frontend

- [x] T20 — Agregar `generarLiquidaciones`, `listarLiquidaciones`, `obtenerLiquidacion`, `marcarLiquidacionPagada` y
  `listarMisLiquidaciones` a `src/api/pagos.js`. Cubre: pantallas Liquidaciones, Detalle de la liquidación y Mis
  liquidaciones (CA1, CA2, CA3).
- [x] T21 — Agregar `GENERADA` ("Por pagar", atención), `PENDIENTE_CUENTA` ("Pendiente de cuenta", atención) y `PAGADA`
  ("Pagada", éxito) a `EtiquetaEstado.jsx`, y los textos de HU-19 a `textosPagos.js`. Cubre: CA2 y CA3 visibles.
- [x] T22 — Crear `pages/pagos/LineasLiquidacion.jsx` y `SeccionLiquidaciones.jsx` (+ `.module.css`). Cubre: pantallas
  Liquidaciones y Mis liquidaciones (CA1, CA2).
- [x] T23 — Crear `pages/pagos/Liquidaciones.jsx` (+ `.module.css`): botón primario "Generar liquidación" con `Modal`,
  resultado en `Alerta`, secciones "Por pagar" y "Pagadas" con "Ver más", avisos y simulada. Cubre: pantalla
  Liquidaciones (CA1, CA3).
- [x] T24 — Crear `pages/pagos/DetalleLiquidacion.jsx` (+ `.module.css`): cabecera, avisos, cuenta de destino (completa
  solo si la API la trae; retenida con fecha; sin cuenta), líneas y "Marcar como pagada" con `Modal` y `Campo`. Cubre:
  pantalla Detalle de la liquidación (CA2, CA3, D4, D5, D6).
- [x] T25 — Crear `pages/pagos/MisLiquidaciones.jsx` (+ `.module.css`): lista paginada con estado, montos, líneas, datos
  del pago, aviso de cuenta pendiente con enlace a `/panel/cuenta-bancaria`; sin totales. Cubre: pantalla Mis
  liquidaciones (CA2, CA3).
- [x] T26 — Registrar `/admin/liquidaciones`, `/admin/liquidaciones/:id` y `/panel/liquidaciones` (con `RutaProtegida`)
  en `App.jsx`, y los enlaces "Liquidaciones" (ADMIN, con la descripción nueva) y "Mis liquidaciones" (ARRENDADOR) en
  `Panel.jsx`. Cubre: las tres pantallas.

## Documentación y cierre

- [x] T27 — Actualizar `docs/architecture.md` (fila de `pagos.ReservasLiquidables` con la firma; `arrendadoresDe` y
  `arrendadoresSinIdentidadVerificada`; sección "Liquidación: qué deja HU-19" con el bloqueo, la regla de pagos, el
  borde de 48 h para HU-26 y `tieneLiquidacionesPendientes` para HU-22), `docs/modelo-datos.md` (columnas y
  restricciones de `liquidacion` y `liquidacion_detalle`) y `docs/frontend.md` (textos de los tres estados y las tres
  rutas). Cubre: R23, R47.
- [x] T28 — Registrar el mapa R↔test en `progress/impl_HU-19.md` y anotar en `progress/HU-19.md` si el PO confirmó D3.
  Cubre: R1–R48.
- [x] T29 — Verificación final: `./init.sh` en verde (lint y build del frontend, pruebas unitarias e integración).
  Cubre: R1–R48.
