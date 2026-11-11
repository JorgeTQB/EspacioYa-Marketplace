# Implementación HU-27 — Reportes y moderación de publicaciones y reseñas

> Implementer, 2026-11-09, rama `feature/HU-27-reportes-moderacion`. Spec: `specs/HU-27/` (R1–R78, T1–T38).
> **Estado: implementación completa, lista para el reviewer.** T1–T38 `[x]`. `./init.sh` completo en verde
> (2026-11-10). El bloqueo previo por Docker quedó resuelto; al correr las IT aparecieron 4 fallos, corregidos abajo.

## Resultado de la verificación

| Comando | Resultado |
|---------|-----------|
| `./mvnw verify -Dit.test=ReporteRestriccionesIT,ReporteAdminControllerIT,ReporteControllerIT` | **OK.** 45 pruebas, 0 fallos, 0 errores |
| `./init.sh` (completo, 2026-11-10) | **OK, exit 0.** Docker encendido; arnés y reglas de arquitectura OK; unitarias `Tests run: 675, Failures: 0, Errors: 0`; `./mvnw verify` `Tests run: 1213, Failures: 0, Errors: 0, Skipped: 0` (todas las IT del proyecto, incluidas las de HU-13/15/16/19 para T37); ESLint sin advertencias; `npm run build` OK |

## Fallos de IT tras desbloquear Docker: causa y corrección

| Prueba | Causa raíz | Corrección |
|--------|-----------|------------|
| `ReporteRestriccionesIT.coherenciaDeLaDecision` (R57) | **Defecto real de `V8_2__reportes.sql`.** En `ck_reporte_decision`, `char_length(btrim(NULL)) >= 1` y `NULL >= creado_en` dan NULL; un CHECK con resultado NULL acepta la fila, así que se podía guardar `MANTENIDO`/`OCULTADO` sin `motivo_decision` o sin `resuelto_en`, contra R57. El SQL de `design.md` § migración tiene el mismo hueco | Migración: `motivo_decision IS NOT NULL` y `resuelto_en IS NOT NULL` explícitos en la rama resuelta del CHECK. `V8_2` no está en `develop` (archivo sin trackear), así que se edita en lugar de crear otra migración. Test sin cambios |
| `ReporteRestriccionesIT.catalogosYCoherencia` (R56) | Error del test. Un `tipo_objetivo = 'FOTO'` viola también `ck_reporte_objetivo`, y un `estado = 'PENDIENTE'` viola también `ck_reporte_decision`; PostgreSQL informa el primer CHECK violado por nombre, por lo que el mensaje nunca nombra `ck_reporte_tipo`/`ck_reporte_estado`. La base sí rechaza (R56 se cumple) | El test exige el rechazo con `violates check constraint` y comprueba aparte, con `pg_get_constraintdef`, que `ck_reporte_tipo` es exactamente `ESPACIO`/`RESENA` y `ck_reporte_estado` exactamente `POR_REVISAR`/`MANTENIDO`/`OCULTADO`. Motivo y coherencia tipo–contenido siguen comprobando su restricción por nombre |
| `ReporteAdminControllerIT.ocultaResena` (R34, R37, R49, R53) | Datos del test. `ReportesSoporte.resena` crea siempre la reserva a las 8 h de hoy−3; dos reseñas en el mismo espacio chocan en `uq_reserva_bloque_activo` | Nueva sobrecarga `ReportesSoporte.resena(autor, espacio, estado, hora)`; la segunda reseña usa las 10 h. Las aserciones no cambian |
| `ReporteAdminControllerIT.adminSinMfa` (R30) | Datos del test. `ck_mfa_activado` (V5_1) exige `activado_en IS NULL` cuando `activo = false` | El arreglo hace `UPDATE mfa SET activo = false, activado_en = NULL` (igual que `MfaInicioSesionIT`). Las aserciones (401) no cambian |

Nota: una base local de desarrollo que ya hubiera aplicado la versión anterior de `V8_2` dará error de checksum en
Flyway; basta con recrearla (la migración aún no está en `develop`).

## Tasks

| Task | Estado | Nota |
|------|--------|------|
| T1–T38 | `[x]` | Código + unitarias + IT en verde (frontend: lint y build). T2, T16, T23, T25, T31, T37 y T38 se marcaron tras `./init.sh` completo en verde |

## Trazabilidad R<n> → test

Todas en verde (unitarias e IT; las IT van marcadas con *).

- R1 → `ReporteServiceTest.reportaUnEspacio`, `ReporteTest.fabricasPorTipo`, *`ReporteControllerIT.usuarioReportaEspacio`, *`arrendadorReportaEspacioAjeno`
- R2 → `ReporteServiceTest.reportaUnaResena`, *`ReporteControllerIT.reportanResena`
- R3 → `ReporteServiceTest.reportaUnEspacio`, *`ReporteControllerIT.usuarioReportaEspacio`
- R4 → `ReporteServiceTest.reportanteDeLaSesion`, *`ReporteControllerIT.camposExtraIgnorados`
- R5 → `ReporteServiceTest.adminNoReporta`, *`ReporteControllerIT.adminRecibe403`
- R6 → *`ReporteControllerIT.sinSesion401`, *`ReporteAdminControllerIT.sinSesion`
- R7 → `ReporteServiceTest.noReportableDa404`, `ModeracionEspaciosImplTest.noVisibleNoEsReportable`, `ModeracionResenasImplTest.noReportable`, *`ReporteControllerIT.noReportableDa404`
- R8 → `ReporteServiceTest.espacioPropio`, *`ReporteControllerIT.espacioPropio`
- R9 → `ReporteServiceTest.resenaPropia`, *`ReporteControllerIT.resenaPropia`
- R10 → `ReporteServiceTest.yaReportado`, *`ReporteControllerIT.yaReportado`
- R11 → *`ReporteRestriccionesIT.unoPorReportanteYContenido`, *`ReporteControllerIT.dosSimultaneos`
- R12 → `ReporteServiceTest.carrera`, *`ReporteControllerIT.dosSimultaneos`
- R13 → *`ReporteControllerIT.tipoInvalido`
- R14 → *`ReporteControllerIT.objetivoInvalido`
- R15 → *`ReporteControllerIT.motivoInvalido`
- R16 → `ReporteServiceTest.cupoAgotado`, *`ReporteControllerIT.cupoDeDiez`
- R17 → `ReporteServiceTest.decimoSeAcepta`, `ReglasReporteTest.ventanaDe24Horas`, *`ReporteControllerIT.cupoDeDiez`
- R18 → `ReporteServiceTest.bloqueaAntesDeContar`, *`ReporteControllerIT.onceSimultaneos`
- R19 → `ReporteServiceTest.cupoAntesQueElContenido`, *`ReporteControllerIT.cupoAntesQueElContenido`
- R20 → `ReporteAdminServiceTest.listaPendientes`, *`ReporteAdminControllerIT.porRevisar`, *`paginacion`
- R21 → `ReporteAdminServiceTest.listaResueltos`, *`ReporteAdminControllerIT.resueltos`
- R22 → `ReporteAdminServiceTest.grupoInvalido`, *`ReporteAdminControllerIT.grupoYPaginaInvalidos`
- R23 → `ReporteAdminServiceTest.paginaInvalida`, *`ReporteAdminControllerIT.grupoYPaginaInvalidos`
- R24 → `ReporteAdminServiceTest.detalleDeEspacio`, `ModeracionEspaciosImplTest.describe`, *`ReporteAdminControllerIT.detalleDeEspacio`
- R25 → `ReporteAdminServiceTest.detalleDeResena`, `ModeracionResenasImplTest.describe`, *`ReporteAdminControllerIT.detalleDeResena`
- R26 → `ReporteAdminServiceTest.sinRolAdmin`, `RolesSesionTest.*`, *`ReporteAdminControllerIT.otrosRoles`
- R27 → `ReporteAdminServiceTest.inexistente`, `ModeracionResenasImplTest.inexistente`, *`ReporteAdminControllerIT.inexistente`
- R28 → *`ReporteAdminControllerIT.porRevisar`, *`detalleDeEspacio`
- R29 → *`ReporteAdminControllerIT.ipNoPermitida`
- R30 → *`ReporteAdminControllerIT.adminSinMfa`
- R31 → `ReporteAdminServiceTest.mantiene`, `ReporteTest.mantener`, *`ReporteAdminControllerIT.mantiene`
- R32 → `ReporteAdminServiceTest.ocultaEspacio`, `EstadoEspacioServiceTest.adminOculta`, `EspacioTest.ocultarPublicadoOPausado`, `ModeracionEspaciosImplTest.ocultarDelega`, *`ReporteAdminControllerIT.ocultaEspacio`
- R33 → `ReporteAdminServiceTest.ocultarResuelveHermanos`, `ReporteTest.ocultarYOcultarPorFraude`
- R34 → `ReporteAdminServiceTest.ocultaResena`, `ModeracionResenasImplTest.oculta`, `ResenaTest.ocultaUnaVisible`, *`ReporteAdminControllerIT.ocultaResena`
- R35 → *`ReporteAdminControllerIT.ocultaEspacio` (búsqueda y ficha)
- R36 → *`ReporteAdminControllerIT.ocultaEspacio` (reservar → 404)
- R37 → *`ReporteAdminControllerIT.ocultaResena` (lista, promedio y total)
- R38 → `EspacioTest.ocultoEsFinal`, *`ReporteAdminControllerIT.ocultoEsFinal`
- R39 → `ReporteAdminServiceTest.espacioNoOcultable`, `EstadoEspacioServiceTest.adminOcultarNoOcultableRechaza`, `EspacioTest.ocultarNoOcultableLanza`, *`ReporteAdminControllerIT.espacioEnBaja`
- R40 → `ReporteAdminServiceTest.resenaNoOcultable`, `ModeracionResenasImplTest.yaOculta`, `ResenaTest.noOcultaDosVeces`
- R41 → `ReporteAdminServiceTest.yaResuelto`, `ReporteTest.noSeResuelveDosVeces`, *`ReporteAdminControllerIT.segundaDecision`
- R42 → `ReporteAdminServiceTest.ordenDeBloqueos`, *`ReporteAdminControllerIT.decisionesSimultaneas`
- R43 → *`ReporteAdminControllerIT.decisionInvalida`
- R44 → `ReporteAdminServiceTest.motivoSoloEtiquetas`, *`ReporteAdminControllerIT.decisionInvalida`
- R45 → `ReporteAdminServiceTest.respuestaDeLaDecision`, *`ReporteAdminControllerIT.ocultaEspacio`, *`OcultarPorFraudeIT.ocultaPorFraude`
- R46 → `ReporteAdminServiceTest.ocultarSinFraudeNoCancela`, `EstadoEspacioServiceTest.ocultarNoConsultaReservas`, *`ReporteAdminControllerIT.ocultaEspacio`
- R47 → `CorreosModeracionTest.*`, `ReporteAdminServiceTest.ocultaEspacio`, `AvisosModeracionTest.falloDelProveedorNoLanza`, *`ReporteAdminControllerIT.ocultaEspacio`
- R48 → `AvisosModeracionTest.soloTrasElCommit`, `ReporteAdminServiceTest.sinCorreoSiFalla`, `AvisosCancelacionTest.alUsuarioSinCommitNoEnvia`
- R49 → `ReporteAdminServiceTest.mantenerSinCorreo`, `ReporteAdminServiceTest.ocultaResena`, *`ReporteAdminControllerIT.mantiene`
- R50 → `ReporteServiceTest.auditaLaCreacion`, *`ReporteControllerIT.auditaLaCreacion`
- R51 → `ReporteAdminServiceTest.auditaLaDecision`, *`ReporteAdminControllerIT.auditaLaOcultacion`
- R52 → `EstadoEspacioServiceTest.adminOculta`, *`ReporteAdminControllerIT.auditaLaOcultacion`
- R53 → `ModeracionResenasImplTest.oculta`, *`ReporteAdminControllerIT.ocultaResena`
- R54 → `ReporteServiceTest.rechazoNoAudita`, `ReporteAdminServiceTest.rechazoNoAudita`, `EstadoEspacioServiceTest.adminOcultarNoOcultableRechaza`, `ModeracionResenasImplTest.yaOculta`
- R55 → *`ReporteAdminControllerIT.sinEdicion`
- R56 → *`ReporteRestriccionesIT.catalogosYCoherencia`
- R57 → *`ReporteRestriccionesIT.coherenciaDeLaDecision`
- R58 → *`ReporteRestriccionesIT.llavesForaneasSinCascada`
- R59 → `DatosPersonalesReportesImplTest.exporta`, `seccionVaciaYAdmin`, *`DatosPersonalesReportesIT.usuarioExporta`, *`arrendadorYVacia`
- R60 → `DatosPersonalesReportesImplTest.sinImpedimentosNiCambios`, *`DatosPersonalesReportesIT.eliminarConservaLosReportes`
- R61 → `DatosPersonalesReportesImplTest.sinImpedimentosNiCambios`, *`DatosPersonalesReportesIT.eliminarConservaLosReportes`
- R62 → `EstadoEspacioServiceTest.ocultarSinRolAdminRecibe403`, `ModeracionResenasImplTest.sinRolAdmin`, `RolesSesionTest.otrosRolesSeRechazan`
- R63 → `ReporteAdminServiceTest.fraudeEnResena`, `ReporteTest.fraudeNoEnResena`, *`OcultarPorFraudeIT.fraudeEnResena`
- R64 → `CancelacionPorModeracionImplTest.cancelaSoloLasPorIniciar`, `ReporteAdminServiceTest.ocultaPorFraude`, `ReservaTest.canceladaPorAdminNoAdmiteLiquidacion`, *`OcultarPorFraudeIT.ocultaPorFraude`
- R65 → `CancelacionPorModeracionImplTest.cancelaSoloLasPorIniciar`, *`OcultarPorFraudeIT.ocultaPorFraude`
- R66 → `CancelacionPorModeracionImplTest.reembolsaElMontoTotal`, *`OcultarPorFraudeIT.ocultaPorFraude`
- R67 → `CalculoReembolsoTest.adminSiempreModeracionAlCien`, `moderacionSoloParaAdmin`, `ReembolsoPorModeracionTest.soloAdminAlCien`
- R68 → `ReservaTest.canceladaPorAdminNoAdmiteLiquidacion`, *`OcultarPorFraudeIT.canceladaNoSeLiquida`
- R69 → `CancelacionPorModeracionImplTest.avisaSoloALosUsuarios`, `AvisosCancelacionTest.alUsuarioTrasElCommit`, `QuienCanceloTest.*`, *`OcultarPorFraudeIT.ocultaPorFraude`
- R70 → `CancelacionPorModeracionImplTest.avisaSoloALosUsuarios`, `AvisosCancelacionTest.*`, `CorreosModeracionTest.porFraudeConReservas`
- R71 → `CancelacionPorModeracionImplTest.auditaLaCancelacion`, *`OcultarPorFraudeIT.ocultaPorFraude`
- R72 → `CancelacionPorModeracionImplTest.falloDelReembolsoSePropaga`, `ReporteAdminServiceTest.falloAlCancelar`, *`OcultarPorFraudeIT.rollbackTotal`
- R73 → `CancelacionPorModeracionImplTest.bloqueaEspacioAntesQueReservas`, `ReporteAdminServiceTest.ordenPorFraude`, *`OcultarPorFraudeIT.concurrenciaConHu15`
- R74 → *`ReporteRestriccionesIT.canceladaPorAdmin`, *`OcultarPorFraudeIT.ocultaPorFraude`
- R75 → *`ReporteRestriccionesIT.fraudeSoloEnEspacioOcultado`
- R76 → `CancelacionPorModeracionImplTest.sinRolAdmin`
- R77 → `CancelacionReservaServiceTest.adminNoCancelaPorLasRutasDeHu15`, *`OcultarPorFraudeIT.adminNoCancelaPorHu15`
- R78 → `ReporteTest.ocultarYOcultarPorFraude`, `ReporteAdminServiceTest.ocultaPorFraude`, *`OcultarPorFraudeIT.ocultaPorFraude`

## Archivos

Backend (`2_Backend/src/main/java/pe/edu/ulima/espacioya/`):

- **Nuevos:** `resources/db/migration/V8_2__reportes.sql`; `administracion/domain/{Reporte,EstadoReporte,TipoObjetivoReporte,MotivoReporte,DecisionReporte,ReglasReporte}.java`;
  `administracion/repository/ReporteRepository.java`; `administracion/dto/{CrearReporteDTO,ReporteCreadoDTO,DecisionReporteDTO,ReporteResumenDTO,PaginaReportesDTO,DetalleReporteAdminDTO,EspacioReportadoDTO,ResenaReportadaDTO,ReporteExportadoDTO}.java`;
  `administracion/service/{ReporteService,ReporteAdminService,VistaReportes,AvisosModeracion}.java`;
  `administracion/service/correo/{DatosEspacioOcultado,CorreoEspacioOcultado,FabricaEspacioOcultado}.java`;
  `administracion/service/impl/DatosPersonalesReportesImpl.java`; `administracion/controller/{ReporteController,ReporteAdminController}.java`;
  `comun/seguridad/RolesSesion.java`; `espacios/service/{ModeracionEspacios,EspacioReportable,EspacioModerado,EspacioOcultado}.java`, `espacios/service/impl/ModeracionEspaciosImpl.java`;
  `busqueda/service/{ModeracionResenas,ResenaReportable,ResenaModerada}.java`, `busqueda/service/impl/ModeracionResenasImpl.java`;
  `reservas/domain/ReembolsoPorModeracion.java`; `reservas/service/{CancelacionPorModeracion,ReservasCanceladasPorModeracion}.java`, `reservas/service/impl/CancelacionPorModeracionImpl.java`.
- **Modificados:** `espacios/domain/Espacio.java` (`esOcultable`, `ocultar`); `espacios/service/EstadoEspacioService.java` (`ocultarComoAdmin`, `RolesSesion`);
  `busqueda/domain/Resena.java` (`estaVisible`, `ocultar`); `busqueda/repository/ResenaRepository.java` (`findParaActualizar`); `busqueda/service/ResenaService.java` (javadoc);
  `reservas/domain/{QuienCancela,CalculoReembolso}.java`; `reservas/repository/ReservaRepository.java` (`findConfirmadasParaActualizar`);
  `reservas/service/CancelacionReservaService.java` (rechaza `ADMIN`; `ENTIDAD_RESERVA` pasa a `public`); `reservas/service/AvisosCancelacion.java` (`programarAlUsuario`);
  `cuentas/service/correo/QuienCancelo.java` (`ADMIN` → "EspacioYa"); `RolesSesion.exigirAdmin` en `administracion/service/{RevisionIdentidadService,CuentaAdminService,ReclamoAdminService}.java`,
  `cuentas/service/impl/CuentaServiceImpl.java`, `pagos/service/{LiquidacionAdminService,ReembolsoAdminService}.java`.

Pruebas (`2_Backend/src/test/java/pe/edu/ulima/espacioya/`):

- **Nuevas:** `administracion/domain/{ReporteTest,ReglasReporteTest}`; `administracion/service/{ReporteServiceTest,ReporteAdminServiceTest,AvisosModeracionTest}`, `administracion/service/correo/CorreosModeracionTest`,
  `administracion/service/impl/DatosPersonalesReportesImplTest`; `administracion/controller/{ReportesSoporte,ReporteRestriccionesIT,ReporteControllerIT,ReporteAdminControllerIT,OcultarPorFraudeIT}`;
  `comun/seguridad/RolesSesionTest`; `espacios/service/impl/ModeracionEspaciosImplTest`; `busqueda/domain/ResenaTest`, `busqueda/service/impl/ModeracionResenasImplTest`;
  `reservas/domain/ReembolsoPorModeracionTest`; `reservas/service/impl/CancelacionPorModeracionImplTest`; `cuentas/service/correo/QuienCanceloTest`; `cuentas/controller/DatosPersonalesReportesIT`.
- **Modificadas:** `espacios/domain/EspacioTest`, `espacios/service/EstadoEspacioServiceTest`, `reservas/domain/{CalculoReembolsoTest,ReservaTest}`, `reservas/service/{CancelacionReservaServiceTest,AvisosCancelacionTest}`,
  `administracion/controller/ReclamosSoporte` (borra `reporte`), `cuentas/controller/DatosPersonalesSoporte` (`ParticipanteQueFalla` a `@Order(6)`, borra `reporte`), `cuentas/controller/DatosPersonalesControllerIT` (sección `reportes`).

Frontend (`3_frontend/src/`): nuevos `pages/busqueda/ReportarContenido.jsx` + `.module.css`, `pages/administracion/{ReportesAdmin,RevisarReporte}.jsx` + `.module.css`;
modificados `api/administracion.js`, `components/ui/EtiquetaEstado.jsx`, `pages/busqueda/{FichaEspacio.jsx,ResenasEspacio.jsx,textosFicha.js}`, `pages/administracion/textosAdministracion.js`, `pages/cuentas/Panel.jsx`, `App.jsx`.

Docs: `docs/architecture.md` (interfaces públicas, `@Order(5)`, "Estado del espacio", Strategy `MODERACION`, `QuienCancela.ADMIN`, sección nueva "Moderación: qué deja HU-27", `RolesSesion`, estados), `docs/modelo-datos.md` (`reporte`, `cancelada_por`, restricciones), `docs/frontend.md` (filas Reseña y Reporte, pantallas de HU-27).

Tocados en la corrección del 2026-11-10: `resources/db/migration/V8_2__reportes.sql` (`ck_reporte_decision`),
`test/.../administracion/controller/{ReporteRestriccionesIT,ReporteAdminControllerIT,ReportesSoporte}.java`.

## Desviaciones y decisiones menores (dentro del spec)

- **Soportes de IT.** Solo se agregó `DELETE FROM reporte` en `ReclamosSoporte` y `DatosPersonalesSoporte`: cada contexto de
  Spring tiene su propio contenedor (`TestcontainersConfiguration` es un `@Bean` por contexto) y solo las IT de esos dos
  contextos (y `OcultarPorFraudeIT`, que extiende `ReclamosSoporte`) crean reportes. Los demás soportes nunca ven filas de
  `reporte`.
- **`ReportesSoporte`** (nuevo) extiende `ReclamosSoporte` para reutilizar el reloj fijo, el doble de correo y la limpieza.
- **`OcultarPorFraudeIT`** usa `@MockitoSpyBean NotificadorCorreo` (el de pruebas es `NotificadorCorreoSimulado`, que no pasa
  por `EnvioCorreo`) para comprobar el aviso al usuario; el texto "Cancelada por: EspacioYa" lo fija `QuienCanceloTest`. La
  prueba de liquidación adelanta el reloj compartido 6 días y lo devuelve en un `finally`.
- **Frontend.** Se agregó el texto `TITULO_MODAL_DECISION` ("¿Registrar esta decisión?") para el `Modal` cuando el admin
  aún no eligió una decisión (el servidor responde el 400 en `decision`); el design solo define los tres títulos con
  decisión elegida.
- **`ReporteAdminService.decidir`** guarda los hermanos con `saveAllAndFlush` antes de auditar (como `saveAndFlush` en
  HU-26), para que una violación de `ck_reporte_*` salga dentro de la transacción.
- `CancelacionReservaService.ENTIDAD_RESERVA` pasó de `private` a `public` (lo permite el design § 7).
