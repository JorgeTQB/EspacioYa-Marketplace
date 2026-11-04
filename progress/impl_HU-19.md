# Implementación HU-19 — Liquidación semanal al arrendador

> Implementer, 2026-11-04, rama `feature/HU-19-liquidacion-semanal`. Dueño: Eduardo Saldaña. Spec: `specs/HU-19/`
> (aprobado con D1–D8 y S1–S15). Tasks T1–T29 de `specs/HU-19/tasks.md`. Sin commit, push ni PR. Status sigue
> `in_progress` hasta el reviewer.

## Resultado de la verificación

- `./init.sh` completo: ver sección final "Resultado de `./init.sh`".
- IT de HU-19 corridas aparte antes de `./init.sh`: `LiquidacionAdminControllerIT` (17), `MisLiquidacionesControllerIT`
  (8), `LiquidacionCancelacionIT` (3), `LiquidacionConcurrenciaIT` (2), `LiquidacionRepositoryIT` (9): 39/39 verdes.
- Unitarias nuevas o ampliadas: `ReglasLiquidacionTest`, `LiquidacionTest`, `CuentaBancariaTest`, `ReservaTest`,
  `ReservaConHorarioTest`, `ReservasLiquidablesImplTest`, `CatalogoEspaciosServiceImplTest`, `CuentaServiceImplTest`,
  `GeneradorLiquidacionesTest`, `LiquidacionAdminServiceTest`, `MisLiquidacionesServiceTest`,
  `MarcarLiquidacionPagadaDTOTest`: verdes.
- Frontend: `npm run lint -- --max-warnings=0` y `npm run build` verdes.
- **Docker Desktop estaba apagado** al empezar; lo encendí (prerrequisito documentado de `./init.sh` para las `*IT`).

## Archivos

### Creados
- Migración: `2_Backend/src/main/resources/db/migration/V5_3__liquidacion.sql` (V5_3 libre en `develop` `670aacf`).
- `pagos/domain/`: `Liquidacion`, `LiquidacionDetalle`, `EstadoLiquidacion`, `MotivoNoPagable`, `AvisoArrendador`,
  `ReglasLiquidacion`.
- `pagos/repository/`: `LiquidacionRepository`, `LiquidacionDetalleRepository`.
- `pagos/service/puertos/`: `ReservasLiquidables`, `ReservaLiquidable`.
- `pagos/service/`: `GeneradorLiquidaciones`, `VistaLiquidaciones`, `LiquidacionAdminService`, `MisLiquidacionesService`.
- `pagos/dto/`: `MarcarLiquidacionPagadaDTO`, `GeneracionLiquidacionesDTO`, `LiquidacionAdminDTO`,
  `LiquidacionAdminDetalleDTO`, `CuentaDestinoDTO`, `PagoLiquidacionDTO`, `LineaLiquidacionDTO`, `MiLiquidacionDTO`,
  `PaginaLiquidacionesDTO`.
- `pagos/controller/`: `LiquidacionAdminController`, `MisLiquidacionesController`.
- `reservas/service/impl/ReservasLiquidablesImpl`.
- Pruebas: `pagos/domain/ReglasLiquidacionTest`, `LiquidacionTest`; `pagos/dto/MarcarLiquidacionPagadaDTOTest`;
  `pagos/service/GeneradorLiquidacionesTest`, `LiquidacionAdminServiceTest`, `MisLiquidacionesServiceTest`;
  `reservas/service/impl/ReservasLiquidablesImplTest`; `pagos/controller/LiquidacionesSoporte` (base de IT),
  `LiquidacionRepositoryIT`, `LiquidacionAdminControllerIT`, `MisLiquidacionesControllerIT`, `LiquidacionCancelacionIT`,
  `LiquidacionConcurrenciaIT`.
- Frontend: `src/pages/pagos/Liquidaciones.jsx`, `DetalleLiquidacion.jsx`, `MisLiquidaciones.jsx`,
  `SeccionLiquidaciones.jsx`, `LineasLiquidacion.jsx` y sus `.module.css`.

### Modificados
- `pagos/domain/ReglasPago` (`PREFIJO_SIMULADO` pública), `pagos/service/pasarela/PasarelaSimulada` (la usa; sin cambio de
  conducta), `pagos/domain/CuentaBancaria` (`numeroParaTransferir()`), `pagos/repository/PagoRepository`
  (`pagadosSinLiquidar`).
- `reservas/domain/Reserva` (`admiteLiquidacion()`), `reservas/domain/ReservaConHorario` (`instanteDeFin()`).
- `espacios/service/CatalogoEspaciosService` + `impl/CatalogoEspaciosServiceImpl` (`arrendadoresDe`).
- `cuentas/service/CuentaService` + `impl/CuentaServiceImpl` (`arrendadoresSinIdentidadVerificada`),
  `cuentas/repository/PerfilArrendadorRepository` (`idsConIdentidadVerificada`).
- Pruebas: `CuentaBancariaTest`, `ReservaTest`, `ReservaConHorarioTest`, `CatalogoEspaciosServiceImplTest`,
  `CuentaServiceImplTest`, `pagos/controller/PagosSoporte` (`limpiar` borra `liquidacion_detalle` y `liquidacion` primero).
  No había dobles manuales de `CatalogoEspaciosService` ni `CuentaService` que actualizar.
- Frontend: `src/api/pagos.js`, `src/components/ui/EtiquetaEstado.jsx` (`GENERADA`, `PENDIENTE_CUENTA`, `PAGADA`),
  `src/pages/pagos/textosPagos.js`, `src/App.jsx` (tres rutas), `src/pages/cuentas/Panel.jsx` (dos enlaces y la
  descripción del admin).
- Docs: `docs/architecture.md` (fila de `pagos.ReservasLiquidables` con la firma, `arrendadoresDe`,
  `arrendadoresSinIdentidadVerificada`, sección "Liquidación: qué deja HU-19"), `docs/modelo-datos.md` (columnas,
  restricciones y relación con `pago` en el ER), `docs/frontend.md` (textos de los tres estados y las tres rutas).
- `specs/HU-19/tasks.md` (T1–T29 marcadas), `progress/HU-19.md` (bitácora).

### Pequeñas decisiones de implementación (dentro del spec)
- `AvisoArrendador.de(cuentaNoActiva, identidadNoVerificada)`: arma la lista de avisos en un solo lugar (lo usan el
  generador para la auditoría y la vista para las respuestas).
- `VistaLiquidaciones` agrupa las líneas con `LinkedHashMap` para que la llamada al puerto y el orden de las líneas sigan el
  orden del repositorio.
- `SeccionLiquidaciones` acepta `titulo` opcional: "Mis liquidaciones" la usa sin título de sección.
- Las respuestas JSON llevan los nulos (`numeroOperacion`, `pagadaEn`, `cuenta`, `pago`, `numeroCompleto`) como `null`,
  igual que el resto de la API; el número completo nunca va con valor fuera del detalle pagable.

## Trazabilidad R<n> → prueba

- R1 → `LiquidacionAdminControllerIT.sinSesion`
- R2 → `LiquidacionAdminControllerIT.sinRolAdmin`, `LiquidacionAdminServiceTest.soloElAdmin`
- R3 → `LiquidacionAdminControllerIT.ipNoPermitida`
- R4 → `MisLiquidacionesControllerIT.sinSesion`
- R5 → `MisLiquidacionesControllerIT.otroRol`, `MisLiquidacionesServiceTest.soloElArrendador`
- R6 → `MisLiquidacionesControllerIT.soloLasDeLaSesion`, `MisLiquidacionesServiceTest.soloLasSuyas`
- R7 → `LiquidacionAdminControllerIT.ca1GeneraAlPasar48Horas`, `GeneradorLiquidacionesTest.unaPorArrendador`,
  `ReservasLiquidablesImplTest.describeConArrendadorYHorario`, `.vaciaSinConsultar`, `.sinBloquesSeOmite`,
  `CatalogoEspaciosServiceImplTest.arrendadoresDeSinFiltro`, `.arrendadoresDeVacio`, `LiquidacionTest.sinLineasNoSeGenera`
- R8 → `LiquidacionAdminControllerIT.ca1GeneraAlPasar48Horas` (fin + 48 h − 1 s nada; fin + 48 h sí),
  `ReglasLiquidacionTest.bordeDe48Horas`, `GeneradorLiquidacionesTest.bordeDe48Horas`,
  `ReservaConHorarioTest.instanteDeFinEnLima`, `.instanteDeFinConHoraFin24`
- R9 → `LiquidacionRepositoryIT.pagadosSinLiquidar`, `GeneradorLiquidacionesTest.conLaSimuladaIncluyeSimulados`
- R10 → `LiquidacionCancelacionIT.canceladas`, `.pendientesYExpiradasFuera`, `GeneradorLiquidacionesTest.excluyeNoAdmitidas`,
  `ReservaTest.admiteLiquidacionSegunElEstado`, `.canceladaConReembolsoNoAdmiteLiquidacion`,
  `ReservasLiquidablesImplTest.estadosNoAdmitidos`
- R11 → `LiquidacionCancelacionIT.canceladas`, `GeneradorLiquidacionesTest.incluyeCanceladaSinReembolso`,
  `ReservaTest.canceladaSinReembolsoAdmiteLiquidacion`, `ReservasLiquidablesImplTest.canceladaSinReembolsoSeAdmite`
- R12 → `LiquidacionAdminControllerIT.segundaGeneracionVacia`, `LiquidacionCancelacionIT.reservasNuevasEnOtraLiquidacion`,
  `LiquidacionRepositoryIT.unaReservaYUnPagoUnaSolaVez`, `.pagadosSinLiquidar`, `.lineasPorLote`
- R13 → `LiquidacionAdminControllerIT.ca1GeneraAlPasar48Horas`, `LiquidacionTest.calculaLosMontos`,
  `.redondeaSobreElTotal`, `ReglasLiquidacionTest.comisionDel10PorCiento`, `GeneradorLiquidacionesTest.calculaLosMontos`,
  `LiquidacionRepositoryIT.guardaYReleeLaEntidad`
- R14 → `LiquidacionAdminControllerIT.ignoraElCuerpo`, `LiquidacionAdminServiceTest.generarSoloConDatosDelServidor`
- R15 → `LiquidacionAdminControllerIT.ca1GeneraAlPasar48Horas`, `.cuentaRetenida`, `LiquidacionTest.conCuentaNaceGenerada`,
  `GeneradorLiquidacionesTest.conCuentaRetenidaGenerada`
- R16 → `LiquidacionAdminControllerIT.ca3PendienteDeCuenta`, `LiquidacionTest.sinCuentaNacePendienteDeCuenta`,
  `GeneradorLiquidacionesTest.sinCuentaPendiente`
- R17 → `LiquidacionCancelacionIT.reservasNuevasEnOtraLiquidacion`, `GeneradorLiquidacionesTest.soloCreaNuevas`
- R18 → `LiquidacionAdminControllerIT.segundaGeneracionVacia`, `GeneradorLiquidacionesTest.sinPendientes`, `.ningunaMadura`,
  `LiquidacionAdminServiceTest.generarSinNada`
- R19 → `LiquidacionAdminControllerIT.ca1GeneraAlPasar48Horas`, `LiquidacionAdminServiceTest.generarRespondeLoCreado`,
  `LiquidacionTest.periodoEnLima`
- R20 → `LiquidacionAdminControllerIT.ca1GeneraAlPasar48Horas`, `GeneradorLiquidacionesTest.marcaLasSimuladas`,
  `.conLaSimuladaIncluyeSimulados`, `LiquidacionRepositoryIT.pagadosSinLiquidar`, `ReglasLiquidacionTest.pagoSimulado`
- R21 → `GeneradorLiquidacionesTest.sinLaSimuladaExcluyeSimulados`, `LiquidacionRepositoryIT.pagadosSinLiquidar`,
  `ReglasLiquidacionTest.pagoSimulado`
- R22 → `LiquidacionConcurrenciaIT.dosGeneracionesALaVez`, `LiquidacionRepositoryIT.bloqueoConsultivo`,
  `GeneradorLiquidacionesTest.bloqueaAntesDelCorte`, `LiquidacionAdminServiceTest.traduceLaGeneracionCruzada`
- R23 → `LiquidacionRepositoryIT.unaReservaYUnPagoUnaSolaVez`, `.restriccionesDeLaTabla`, `.exigeElPerfil`,
  `.guardaYReleeLaEntidad`, `LiquidacionTest.lineaDeDetalle`
- R24 → `LiquidacionAdminControllerIT.suspendidoSeLiquidaConAvisos`, `GeneradorLiquidacionesTest.avisosNoFiltran`
- R25 → `LiquidacionAdminControllerIT.ca2MarcaPagada`, `LiquidacionAdminServiceTest.marcarGenerada`, `.ordenDeBloqueos`,
  `LiquidacionTest.marcarPagadaGuardaElDestinoEnmascarado`
- R26 → `LiquidacionAdminControllerIT.ca3PendienteDeCuenta`, `LiquidacionAdminServiceTest.marcarPendienteDeCuenta`,
  `LiquidacionTest.pendienteDeCuentaPasaAPagada`
- R27 → `LiquidacionAdminControllerIT.ca3PendienteDeCuenta`, `LiquidacionAdminServiceTest.marcarSinCuenta`,
  `LiquidacionTest.sinCuenta`
- R28 → `LiquidacionAdminControllerIT.cuentaRetenida` (tras un `PUT` real de HU-18), `LiquidacionAdminServiceTest.marcarRetenida`,
  `LiquidacionTest.cuentaRetenida`
- R29 → `LiquidacionAdminControllerIT.yaPagada`, `LiquidacionAdminServiceTest.marcarYaPagada`, `LiquidacionTest.yaPagada`
- R30 → `LiquidacionAdminControllerIT.numeroDeOperacionInvalido`, `MarcarLiquidacionPagadaDTOTest.numeroInvalido`,
  `.numeroValido`, `.treintaCaracteres`
- R31 → `LiquidacionAdminControllerIT.idNoEncontrado`, `LiquidacionAdminServiceTest.idMalEscrito`, `.idInexistente`
- R32 → `LiquidacionConcurrenciaIT.dosMarcadosALaVez`, `LiquidacionAdminServiceTest.marcarYaPagada`
- R33 → `LiquidacionAdminControllerIT.listaPorPagar`, `LiquidacionAdminServiceTest.listarPorPagar`, `.listarPrimeraPagina`,
  `LiquidacionRepositoryIT.paginaPorEstado`
- R34 → `LiquidacionAdminControllerIT.listaPagadas`, `LiquidacionAdminServiceTest.listarPagadas`,
  `LiquidacionRepositoryIT.paginaPorEstado`
- R35 → `LiquidacionAdminControllerIT.parametrosInvalidos`, `LiquidacionAdminServiceTest.listarParametrosInvalidos`,
  `.listarGrupoInvalido`
- R36 → `LiquidacionAdminControllerIT.cuentaRetenida`, `LiquidacionAdminServiceTest.detallePagable`, `.detalleRetenido`,
  `.detalleSinCuentaYPagada`
- R37 → `LiquidacionAdminControllerIT.listaPorPagar`, `.suspendidoSeLiquidaConAvisos`,
  `LiquidacionAdminServiceTest.generarRespondeLoCreado`, `.listarConAvisos`, `CuentaServiceImplTest.sinIdentidadVerificada`,
  `.sinIdentidadVerificadaVacio`
- R38 → `LiquidacionAdminControllerIT.ca3PendienteDeCuenta`, `LiquidacionAdminServiceTest.detallePagable`,
  `CuentaBancariaTest.numeroParaTransferir`
- R39 → `LiquidacionAdminControllerIT.ca3PendienteDeCuenta`, `LiquidacionAdminServiceTest.detallePagableAudita`
- R40 → `LiquidacionAdminControllerIT.ca2MarcaPagada`, `.cuentaRetenida`, `.listaPorPagar`,
  `LiquidacionAdminServiceTest.detalleRetenido`, `.detalleSinCuentaYPagada`, `.listarConAvisos`, `.toStringSinNumero`
- R41 → `LiquidacionAdminControllerIT.noStore`, `MisLiquidacionesControllerIT.noStore`
- R42 → `MisLiquidacionesControllerIT.ca2VeLaPagada`, `.ca3VeLaPendiente`, `.paginaDeVeinte`,
  `MisLiquidacionesServiceTest.soloLasSuyas`, `.contenido`, `LiquidacionRepositoryIT.delArrendador`
- R43 → `MisLiquidacionesControllerIT.paginaInvalida`, `MisLiquidacionesServiceTest.paginaInvalida`
- R44 → `MisLiquidacionesControllerIT.ca2VeLaPagada`, `MisLiquidacionesServiceTest.sinDatosAjenos`
- R45 → `LiquidacionAdminControllerIT.ca1GeneraAlPasar48Horas`, `.suspendidoSeLiquidaConAvisos`,
  `GeneradorLiquidacionesTest.auditaLaGeneracion`, `.avisosNoFiltran`
- R46 → `LiquidacionAdminControllerIT.ca2MarcaPagada`, `LiquidacionAdminServiceTest.marcarAudita`
- R47 → `MisLiquidacionesServiceTest.pendientesParaHu22`, `LiquidacionRepositoryIT.delArrendador`
- R48 → `LiquidacionAdminServiceTest.sinCorreo`

Tasks sin completar: ninguna.

## Avisos para otros dueños (design.md § 14)

- **Sebastian Garate (`espacios`):** nuevo `CatalogoEspaciosService.arrendadoresDe(Collection<Long>) → Map<Long, Long>`
  (sin filtro de estado ni visibilidad, como `nombres`; vacía → `Map.of()` sin consultar) e implementación en
  `CatalogoEspaciosServiceImpl`. No cambia ningún método existente.
- **Jorge Quispe (`cuentas`):** nuevo `CuentaService.arrendadoresSinIdentidadVerificada(Collection<Long>) → Set<Long>`, su
  implementación y la consulta `PerfilArrendadorRepository.idsConIdentidadVerificada` (solo ids). Sin sesión ni rol.
- **Kevin Chan (HU-15, y HU-22):** `Reserva.admiteLiquidacion()` lee `estado`, `canceladaPor` y `montoReembolso`; si RN-06
  o la forma de guardar `monto_reembolso` cambian, hay que revisarlo. Para HU-22: usar
  `MisLiquidacionesService.tieneLiquidacionesPendientes(arrendadorId)` (`GENERADA` o `PENDIENTE_CUENTA`); `liquidacion` no
  tiene cascada.
- **Adriana Callupe (HU-14):** `ReservaConHorario.instanteDeFin()` nuevo, gemelo de `instanteDeInicio()`.
- **Ariel Enriquez (HU-18 y PO; HU-26):** `CuentaBancaria.numeroParaTransferir()` es el único acceso al número completo y
  solo lo usa el detalle admin de una liquidación pagable. Para HU-26: reclamo solo si `ahora < fin + 48 h` con
  `ReservaConHorario.instanteDeFin` y `ReglasLiquidacion.ESPERA`; el filtro `pagos.ReclamosAbiertos` va en
  `GeneradorLiquidaciones`.
- **Todos los que escriben IT que borran `pago`, `reserva`, `perfil_arrendador` o `usuario`:** si su IT llega a crear
  liquidaciones, borrar antes `liquidacion_detalle` y `liquidacion` (`PagosSoporte.limpiar` ya lo hace).

## Pendientes para el dueño

- **D3 (RK-1) sigue sin confirmar por el PO (Ariel Enriquez).** Está implementado según el spec (R11). Si el PO lo rechaza:
  quitar el caso `CANCELADA` de `Reserva.admiteLiquidacion`, retirar R11 y sus pruebas y ajustar R10.
- **Prueba en navegador no hecha:** no levanté backend ni frontend para recorrer las tres pantallas
  (`/admin/liquidaciones`, `/admin/liquidaciones/:id`, `/panel/liquidaciones`). Queda para el dueño o la pareja de UAT:
  generar (con y sin reservas maduras), ver el detalle con la cuenta completa, marcar como pagada (número inválido bajo el
  campo, cuenta retenida con su fecha) y "Mis liquidaciones" con `PAGADA`, `PENDIENTE_CUENTA` y `GENERADA`.
- En UAT todas las liquidaciones salen `simulada` (pasarela simulada, RK-2).
- Para demostrar CA1 en UAT hay que esperar 48 h tras el fin de una reserva (no hay forma de mover el reloj en UAT).

## Resultado de `./init.sh`

`./init.sh` completo (2026-11-04, con Docker Desktop encendido): **`[OK] Entorno listo`**, salida 0.
- feature_list, progress y specs coherentes; reglas de arquitectura y convenciones sin hallazgos.
- Backend `./mvnw verify`: unitarias 1358, 0 fallos, 0 errores; integración 794, 0 fallos, 0 errores.
- Frontend: ESLint sin errores ni advertencias; `npm run build` compila.
