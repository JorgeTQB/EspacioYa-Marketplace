# Review — HU-27

> Reviewer, 2026-11-10. Rama `feature/HU-27-reportes-moderacion` en `c3d6af6` (HU-28 debajo, ya commiteada) + árbol de
> trabajo sin commitear de HU-27. Solo se revisa HU-27; de HU-28 solo se comprobó que los archivos compartidos no la rompen.

**Veredicto:** CHANGES_REQUESTED (RECHAZADO)

Causa: (a) `./init.sh` completo **no pudo terminar en verde en esta sesión** porque Docker no responde (bloqueo de
entorno, no de código; ver § Verificación) y la regla dura prohíbe aprobar sin él; (b) un hallazgo de documentación
(`design.md` desalineado con la migración). El código de HU-27 no tiene hallazgos bloqueantes.

## Verificación ejecutada

| Comando | Resultado |
|---------|-----------|
| `docker info` | **Falla**: `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.` Docker Desktop apagado: **bloqueo de entorno** |
| `./init.sh` (completo) | **exit 1, solo por Docker**: `[FAIL] Docker no responde` y `[FAIL] Sin Docker no se pueden correr las pruebas *IT`. Todo lo demás `[OK]`: arnés, `feature_list.json` (28 historias: 26 done, 2 in_progress), specs/progress/tasks coherentes, **sin `[WARN]` de R<n> sin `@DisplayName`** (R1–R78 cubiertos), 10 reglas de arquitectura OK, ESLint sin advertencias, `npm run build` OK |
| `./2_Backend/mvnw -f 2_Backend/pom.xml test` (todas las unitarias) | **BUILD SUCCESS: `Tests run: 2105, Failures: 0, Errors: 0, Skipped: 0`** |
| `./mvnw verify` (todas las *IT) | **No ejecutado** (sin Docker). `progress/impl_HU-27.md` afirma `Tests run: 1213, Failures: 0` con Docker el 2026-11-10; el reviewer **no lo pudo reproducir** |

## Trazabilidad requirements ↔ tests

Comprobado contra `@DisplayName("HU-27 R<n>: …")` en `2_Backend/src/test/java/` (el validador de `init.sh` no reporta
ningún R sin cubrir) y por muestreo de aserciones. Los marcados (IT) **no se pudieron ejecutar** en esta sesión.

- R1: [x] `ReporteServiceTest.reportaUnEspacio`, `ReporteTest.fabricasPorTipo`, (IT) `ReporteControllerIT.usuarioReportaEspacio`
- R2: [x] `ReporteServiceTest.reportaUnaResena`, (IT) `ReporteControllerIT.reportanResena`
- R3: [x] `ReporteServiceTest.reportaUnEspacio`, (IT) `ReporteControllerIT.usuarioReportaEspacio`
- R4: [x] `ReporteServiceTest.reportanteDeLaSesion`, (IT) `ReporteControllerIT.camposExtraIgnorados`
- R5: [x] `ReporteServiceTest.adminNoReporta`, (IT) `ReporteControllerIT.adminRecibe403`
- R6: [x] (IT) `ReporteControllerIT.sinSesion401`, `ReporteAdminControllerIT.sinSesion`
- R7: [x] `ReporteServiceTest.noReportableDa404`, `ModeracionEspaciosImplTest.noVisibleNoEsReportable`, `ModeracionResenasImplTest.noReportable`
- R8: [x] `ReporteServiceTest.espacioPropio`, (IT) `ReporteControllerIT.espacioPropio`
- R9: [x] `ReporteServiceTest.resenaPropia`, (IT) `ReporteControllerIT.resenaPropia`
- R10: [x] `ReporteServiceTest.yaReportado`, (IT) `ReporteControllerIT.yaReportado`
- R11: [x] (IT) `ReporteRestriccionesIT.unoPorReportanteYContenido`, `ReporteControllerIT.dosSimultaneos`
- R12: [x] `ReporteServiceTest.carrera`, (IT) `ReporteControllerIT.dosSimultaneos`
- R13: [x] (IT) `ReporteControllerIT.tipoInvalido`
- R14: [x] (IT) `ReporteControllerIT.objetivoInvalido`
- R15: [x] (IT) `ReporteControllerIT.motivoInvalido`
- R16: [x] `ReporteServiceTest.cupoAgotado`, (IT) `ReporteControllerIT.cupoDeDiez`
- R17: [x] `ReporteServiceTest.decimoSeAcepta`, `ReglasReporteTest.ventanaDe24Horas`
- R18: [x] `ReporteServiceTest.bloqueaAntesDeContar`, (IT) `ReporteControllerIT.onceSimultaneos`
- R19: [x] `ReporteServiceTest.cupoAntesQueElContenido`, (IT) `ReporteControllerIT.cupoAntesQueElContenido`
- R20: [x] `ReporteAdminServiceTest.listaPendientes`, (IT) `ReporteAdminControllerIT.porRevisar`, `paginacion`
- R21: [x] `ReporteAdminServiceTest.listaResueltos`, (IT) `ReporteAdminControllerIT.resueltos`
- R22: [x] `ReporteAdminServiceTest.grupoInvalido`, (IT) `ReporteAdminControllerIT.grupoYPaginaInvalidos`
- R23: [x] `ReporteAdminServiceTest.paginaInvalida`, (IT) `ReporteAdminControllerIT.grupoYPaginaInvalidos`
- R24: [x] `ReporteAdminServiceTest.detalleDeEspacio`, `ModeracionEspaciosImplTest.describe`
- R25: [x] `ReporteAdminServiceTest.detalleDeResena`, `ModeracionResenasImplTest.describe`
- R26: [x] `ReporteAdminServiceTest.sinRolAdmin`, `RolesSesionTest`, (IT) `ReporteAdminControllerIT.otrosRoles`
- R27: [x] `ReporteAdminServiceTest.inexistente`, (IT) `ReporteAdminControllerIT.inexistente`
- R28: [x] (IT) `ReporteAdminControllerIT.porRevisar`, `ocultaEspacio` (`Cache-Control: no-store`, l. 363)
- R29: [x] (IT) `ReporteAdminControllerIT.ipNoPermitida`
- R30: [x] (IT) `ReporteAdminControllerIT.adminSinMfa`
- R31: [x] `ReporteAdminServiceTest.mantiene`, `ReporteTest.mantener`, (IT) `ReporteAdminControllerIT.mantiene` (l. 533)
- R32: [x] `ReporteAdminServiceTest.ocultaEspacio`, `EstadoEspacioServiceTest.adminOculta`, `EspacioTest.ocultarPublicadoOPausado`
- R33: [x] `ReporteAdminServiceTest.ocultarResuelveHermanos`, `ReporteTest.ocultarYOcultarPorFraude`
- R34: [x] `ResenaTest.ocultaUnaVisible`, `ModeracionResenasImplTest.oculta`, (IT) `ReporteAdminControllerIT.ocultaResena`
- R35: [x] (IT) `ReporteAdminControllerIT.ocultaEspacio` (l. 380-381: búsqueda y ficha 404)
- R36: [x] (IT) `ReporteAdminControllerIT.ocultaEspacio` (l. 382-385: reservar 404)
- R37: [x] (IT) `ReporteAdminControllerIT.ocultaResena` (l. 517-522: lista, total y promedio)
- R38: [x] `EspacioTest.ocultoEsFinal`, (IT) `ReporteAdminControllerIT.ocultoEsFinal`
- R39: [x] `ReporteAdminServiceTest.espacioNoOcultable`, `EstadoEspacioServiceTest.adminOcultarNoOcultableRechaza`, (IT) `espacioEnBaja`
- R40: [x] `ReporteAdminServiceTest.resenaNoOcultable`, `ModeracionResenasImplTest.yaOculta`, `ResenaTest.noOcultaDosVeces`
- R41: [x] `ReporteAdminServiceTest.yaResuelto`, `ReporteTest.noSeResuelveDosVeces`, (IT) `segundaDecision`
- R42: [x] `ReporteAdminServiceTest.ordenDeBloqueos`, (IT) `ReporteAdminControllerIT.decisionesSimultaneas` (lanza las dos a la vez)
- R43: [x] (IT) `ReporteAdminControllerIT.decisionInvalida`
- R44: [x] `ReporteAdminServiceTest.motivoSoloEtiquetas`, (IT) `decisionInvalida` (blanco, 501, solo etiquetas, no texto)
- R45: [x] `ReporteAdminServiceTest.respuestaDeLaDecision`, (IT) `ocultaEspacio`, `OcultarPorFraudeIT.ocultaPorFraude`
- R46: [x] `ReporteAdminServiceTest.ocultarSinFraudeNoCancela`, `EstadoEspacioServiceTest.ocultarNoConsultaReservas`
- R47: [x] `CorreosModeracionTest`, `AvisosModeracionTest.falloDelProveedorNoLanza`, (IT) `ocultaEspacio` (l. 389-395)
- R48: [x] `AvisosModeracionTest.soloTrasElCommit`, `ReporteAdminServiceTest.sinCorreoSiFalla`, `AvisosCancelacionTest`
- R49: [x] `ReporteAdminServiceTest.mantenerSinCorreo`, (IT) `mantiene`, `ocultaResena`
- R50: [x] `ReporteServiceTest.auditaLaCreacion`, (IT) `ReporteControllerIT.auditaLaCreacion`
- R51: [x] `ReporteAdminServiceTest.auditaLaDecision`, (IT) `auditaLaOcultacion` (l. 416-421)
- R52: [x] `EstadoEspacioServiceTest.adminOculta`, (IT) `auditaLaOcultacion` (l. 407-415)
- R53: [x] `ModeracionResenasImplTest.oculta`, (IT) `ocultaResena` (l. 524-528)
- R54: [x] `ReporteServiceTest.rechazoNoAudita`, `ReporteAdminServiceTest.rechazoNoAudita`
- R55: [x] (IT) `ReporteAdminControllerIT.sinEdicion` (PUT/DELETE: 405)
- R56: [x] (IT) `ReporteRestriccionesIT.catalogosYCoherencia`
- R57: [x] (IT) `ReporteRestriccionesIT.coherenciaDeLaDecision` (incluye los casos NULL de `motivo_decision` y `resuelto_en`, l. 112-120)
- R58: [x] (IT) `ReporteRestriccionesIT.llavesForaneasSinCascada`
- R59: [x] `DatosPersonalesReportesImplTest.exporta`, `seccionVaciaYAdmin`, (IT) `DatosPersonalesReportesIT`
- R60: [x] `DatosPersonalesReportesImplTest.sinImpedimentosNiCambios`
- R61: [x] `DatosPersonalesReportesImplTest.sinImpedimentosNiCambios`, (IT) `DatosPersonalesReportesIT.eliminarConservaLosReportes`
- R62: [x] `EstadoEspacioServiceTest` (l. 514), `ModeracionResenasImplTest` (l. 139), `RolesSesionTest`
- R63: [x] `ReporteAdminServiceTest.fraudeEnResena`, `ReporteTest.fraudeNoEnResena`, (IT) `OcultarPorFraudeIT` (l. 166)
- R64: [x] `CancelacionPorModeracionImplTest.cancelaSoloLasPorIniciar`, (IT) `OcultarPorFraudeIT.ocultaPorFraude`
- R65: [x] `CancelacionPorModeracionImplTest.cancelaSoloLasPorIniciar`, (IT) `OcultarPorFraudeIT.ocultaPorFraude`
- R66: [x] `CancelacionPorModeracionImplTest.reembolsaElMontoTotal`, (IT) `OcultarPorFraudeIT.ocultaPorFraude`
- R67: [x] `CalculoReembolsoTest` (l. 117, 127), `ReembolsoPorModeracionTest`
- R68: [x] `ReservaTest.canceladaPorAdminNoAdmiteLiquidacion`, (IT) `OcultarPorFraudeIT` (l. 143)
- R69: [x] `CancelacionPorModeracionImplTest.avisaSoloALosUsuarios`, `QuienCanceloTest`, (IT) `OcultarPorFraudeIT.ocultaPorFraude`
- R70: [x] `AvisosCancelacionTest` (l. 119), `CorreosModeracionTest.porFraudeConReservas`
- R71: [x] `CancelacionPorModeracionImplTest.auditaLaCancelacion`, (IT) `OcultarPorFraudeIT.ocultaPorFraude`
- R72: [x] `CancelacionPorModeracionImplTest.falloDelReembolsoSePropaga`, `ReporteAdminServiceTest.falloAlCancelar`, (IT) `OcultarPorFraudeIT` (l. 182)
- R73: [x] `CancelacionPorModeracionImplTest.bloqueaEspacioAntesQueReservas`, (IT) `OcultarPorFraudeIT` (l. 209, simultáneas)
- R74: [x] (IT) `ReporteRestriccionesIT.canceladaPorAdmin`, `OcultarPorFraudeIT.ocultaPorFraude`
- R75: [x] (IT) `ReporteRestriccionesIT.fraudeSoloEnEspacioOcultado`
- R76: [x] `CancelacionPorModeracionImplTest` (l. 193)
- R77: [x] `CancelacionReservaServiceTest.adminNoCancelaPorLasRutasDeHu15`, (IT) `OcultarPorFraudeIT` (l. 233)
- R78: [x] `ReporteTest.ocultarYOcultarPorFraude`, `ReporteAdminServiceTest.ocultaPorFraude`

## Trazabilidad con la historia

- CA1 (reporta con motivo; entra a la cola): [x] R1-R4, R7-R10, R13-R15, R20
- CA2 (revisa contenido y motivo; mantiene u oculta con motivo): [x] R20-R28, R31-R34, R39-R45, R63, R78
- CA3 (correo al arrendador con motivo; deja de verse): [x] R32, R35-R38, R46-R48, R70
- (E/S) Solo admins con MFA: [x] R5, R26, R29, R30, R62, R76, R77. `RolesSesion.exigirAdmin` en los servicios y en
  `ModeracionResenasImpl.ocultar`, `EstadoEspacioService.ocultarComoAdmin` y `CancelacionPorModeracionImpl.cancelarPorFraude`;
  IP + MFA por el prefijo `/api/admin/`
- (D) 10 reportes/día: [x] R16-R19. `ReporteService.reportarEnTransaccion` (l. 86-92): `bloquearCuenta`, conteo, 429
- (R) Registro de reportes y acciones: [x] R50-R55, R57, R58, R71, R75
- Decisiones del dueño 2026-11-09 (P8 definitivo; P9/P17 fraude cancela): [x] R38, R55, R63-R78

## Tasks completas

- T1-T38: [x] (38 tasks en `specs/HU-27/tasks.md`, ninguna `[ ]`)

## Revisión por archivo (resumen)

- **Arquitectura.** `administracion` usa `espacios`, `busqueda` y `reservas` solo por sus interfaces públicas nuevas
  (`ModeracionEspacios`, `ModeracionResenas`, `CancelacionPorModeracion`), documentadas en `docs/architecture.md`;
  `reservas` usa `pagos.PagoService` y `espacios.DisponibilidadService` (direcciones permitidas). Controladores sin
  repositorios, DTO `record`, identidad desde `@AuthenticationPrincipal`, montos (100 %) y estados calculados en el
  servidor, correo detrás de `EnvioCorreo` con el Factory Method de HU-25 y `DespuesDelCommit`. Sin hallazgos.
- **Concurrencia.** `ReporteAdminService.decidir` (l. 129-137): el `findById` sin bloqueo solo decide el tipo; el
  estado se evalúa con la consulta `FOR UPDATE ... estado = POR_REVISAR`, que filtra en la base, así que una decisión
  simultánea ve "ya resuelto" aunque la entidad esté en el contexto de persistencia. Correcto.
- **Migración.** `V8_2__reportes.sql` (sprint 8, n.º 2 tras `V8_1__reclamos.sql`); no está en `develop`, por eso la
  edición del `ck_reporte_decision` es legítima (C3). `ck_reserva_cancelacion` no cambia (R74).
- **Frontend.** Solo kit (`Boton`, `Modal`, `Campo`, `Alerta`, `Tarjeta`, `EtiquetaEstado`), CSS modules sin colores,
  llamadas solo por `src/api/administracion.js`, un único primario por pantalla en `RevisarReporte.jsx` (l. 227) y
  ninguno en `ReportesAdmin.jsx`; coincide con `design.md` § 12 y con la tabla de estados de `docs/frontend.md`.
- **No rompe HU-28** en los archivos compartidos: `EtiquetaEstado.jsx` conserva `PAGADO`/`REEMBOLSADO` (l. 35-37) y
  las claves nuevas (`VISIBLE`, `OCULTA`, `POR_REVISAR`, `MANTENIDO`, `OCULTADO`) no chocan; `App.jsx` y `Panel.jsx`
  solo agregan rutas y el enlace de admin; `docs/architecture.md` mantiene el ancla
  `#pagos-qué-deja-hu-13-hu-14-hu-15-hu-16-hu-19-hu-28` y `docs/frontend.md` solo agrega filas y una viñeta.
  `HistorialPagosServiceTest` y `GeneradorComprobantePdfTest` pasaron en la corrida de unitarias; las IT de HU-28
  (`HistorialPagosControllerIT`, `ReservasDelUsuarioImplIT`) quedan sin correr por el bloqueo de Docker.

## Checkpoints

- C1: [x] archivos base, docs, historias y subagentes; [ ] `./init.sh` completo exit 0 (**bloqueo de entorno: Docker**)
- C2: [x] como mucho 1 in_progress por dueño; [x] `progress/HU-27.md` existe; n/a historias done; [x] aprobación del
  dueño anotada (`progress/HU-27.md` l. 12 y 46); [x] progress solo de la sesión activa
- C3: [x] todos (módulos, controller sin repository, sin `@Autowired`/Lombok/comodines/prints, interfaces documentadas,
  DTO record, identidad de sesión, Flyway `V8_2`, Adapter de correo, nada fuera de alcance, sin secretos, sin colores,
  kit y `src/api/`)
- C4: [x] nombres `*Test`/`*IT`; [x] sin H2; [x] reloj fijo (`ReglasReporteTest`, `CancelacionPorModeracionImplTest`, IT
  con reloj compartido); [x] IT para endpoints, migración y concurrencia lanzada a la vez (R11, R18, R42, R73);
  [x] aserciones concretas; [ ] init.sh completo con *IT verdes (**no verificable sin Docker**); [x] lint + build OK
- C5: n/a hasta el cierre (history.md, plantilla y estado los deja el orquestador/dueño); [x] rama `feature/HU-27-...`;
  [x] sin archivos sospechosos sin trackear
- C6: [x] carpeta spec completa; [x] EARS + tabla de trazabilidad; [x] design con endpoints, clases, migración,
  secuencia y alternativas; [x] R<n> con test documentado en `progress/impl_HU-27.md`; [x] los pendientes los decidió
  el dueño (P8, P9 el 2026-11-09)

## Cambios requeridos

1. **Alinear `specs/HU-27/design.md` § 8 con la migración (hallazgo de documentación).** `specs/HU-27/design.md:623-626`
   todavía tiene el `ck_reporte_decision` con el hueco de NULL:
   `AND char_length(btrim(motivo_decision)) >= 1 AND resuelto_en >= creado_en`. Con `motivo_decision` o `resuelto_en`
   en NULL esa rama evalúa NULL y un CHECK con NULL **acepta** la fila (contra R57). La migración real ya lo corrige
   (`2_Backend/src/main/resources/db/migration/V8_2__reportes.sql:25-30`, con `motivo_decision IS NOT NULL` y
   `resuelto_en IS NOT NULL` explícitos) y `ReporteRestriccionesIT.coherenciaDeLaDecision` l. 112-120 lo prueba.
   **Sí debe alinearse**: el design es la referencia que leen las siguientes historias y hoy contradice el código.
   Basta con copiar las líneas 25-30 de `V8_2` a `design.md` § 8 (con su comentario). No cambia ningún R<n> ni test.
2. **Bloqueo de entorno (no es un defecto de código).** Encender Docker Desktop y volver a correr `./init.sh` completo;
   debe terminar con exit 0 y con todas las `*IT` (incluidas `ReporteRestriccionesIT`, `ReporteControllerIT`,
   `ReporteAdminControllerIT`, `OcultarPorFraudeIT`, `DatosPersonalesReportesIT` y las de HU-13/15/16/19/28) en verde.
   Con eso y el punto 1, la historia queda en condiciones de aprobarse sin otra revisión de código.

## Observaciones no bloqueantes

- `ReporteAdminController.java:50,59` usa `ConversionReclamos.idReclamo` para el id de un **reporte**. Lo prevé el design
  (DT-14) y funciona, pero el nombre confunde; si se vuelve a tocar, conviene un nombre neutral.
- `RevisarReporte.jsx:62-63,190-199` y `ReportesAdmin.jsx:34-36` tienen etiquetas literales ("Dirección:",
  "Capacidad: ... personas", "Motivo:", "Reportado el", "Resuelto el") fuera de `textosAdministracion.js`, como en
  pantallas anteriores; no contradice `docs/frontend.md`.

## Re-revisión 2026-11-10

**Veredicto:** APPROVED (APROBADO)

Revisión acotada a los dos motivos del rechazo anterior; el código no se volvió a revisar (no tenía hallazgos
bloqueantes).

1. **`design.md` alineado con la migración: resuelto.** `specs/HU-27/design.md:623-628` coincide línea a línea con
   `2_Backend/src/main/resources/db/migration/V8_2__reportes.sql:25-30` (comentario R57 incluido, con
   `motivo_decision IS NOT NULL` y `resuelto_en IS NOT NULL` explícitos). Detalle del cambio en
   `progress/spec_fix_HU-27.md`. `specs/HU-27/requirements.md` y `tasks.md` no se tocaron: su fecha de modificación
   (21:06:28) es anterior a la revisión previa (21:13:44); solo `design.md` cambió después (21:14:12).
2. **`./init.sh` completo con Docker: verde.** Salida de la corrida del orquestador (`bhh0ifs01.output`): `[OK] Docker
   encendido`, `[OK] Integración: todas las pruebas *IT (./mvnw verify)` con `Tests run: 896, Failures: 0, Errors: 0`
   y `Tests run: 1231, Failures: 0, Errors: 0`, `[OK] ESLint sin errores ni advertencias`, `[OK] El frontend compila`,
   `[exited with code 0]`. Ningún `[FAIL]` ni `[WARN]` en la salida.

### Checkpoints actualizados
- C1: [x] `./init.sh` completo exit 0
- C4: [x] init.sh completo con *IT verdes (incluye las IT de HU-27 y de HU-13/15/16/19/28)
- Resto sin cambios respecto de la revisión anterior (todos [x] o n/a).

Las observaciones no bloqueantes anteriores siguen vigentes como sugerencias.

## Revisión de ajustes 2026-11-10

**Veredicto:** APROBADO (0 hallazgos)

Revisión acotada a los ajustes de `progress/ajustes_HU-27.md` (atienden las dos observaciones no bloqueantes) y a la
alineación de `specs/HU-27/design.md` (`progress/spec_fix_HU-27.md`).

1. **Backend: id de reporte.**
   - `ConversionReportes.java:7-16`: clase `final` package-private con constructor privado, igual que
     `ConversionReclamos`; `idReporte(String)` delega en `ConversionReclamos.idReclamo`, así que el comportamiento ante
     ids inválidos es idéntico por construcción (null, fuera de `[0-9]{1,19}`, desborde y `<= 0` dan
     `RecursoNoEncontradoException`, 404 genérico, R27). Nombres en español, sin dependencias nuevas, en
     `administracion.controller`.
   - `ReporteAdminController.java:50,59`: usan `ConversionReportes.idReporte(id)`; no queda ninguna referencia a
     `ConversionReclamos` en el controlador.
   - `ConversionReportesTest.java:16-30`: `@DisplayName("HU-27 R27: ...")`, aserciones concretas (`42L`,
     `Long.MAX_VALUE`) y 9 casos inválidos (null, vacío, 0, -1, letras, decimal, espacio inicial, 20 dígitos,
     `Long.MAX_VALUE + 1`).
   - HU-26 sin cambios: `git diff --stat HEAD --` vacío para `ConversionReclamos.java`, `ReclamoController.java` y
     `ReclamoAdminController.java`.
2. **Frontend: textos.**
   - `textosAdministracion.js:173-191`: `direccionDelEspacio`, `capacidadEnPersonas`, `motivoDe`, `reportadoEl`,
     `resueltoEl`, mismo estilo que `arrendadorDelEspacio` (l. 169). Producen la misma cadena que el JSX anterior
     (`Dirección: x`, `Capacidad: n personas`, `Motivo: x`, `Reportado el <fechaHora>`, `Resuelto el <fechaHora>`); con
     `null` dan `''`, como React con `{null}`. `fechaHora` sigue exportada (la usa HU-26, `ReclamosAdmin.jsx:39`).
   - `RevisarReporte.jsx:66-67,194-195,202-203` y `ReportesAdmin.jsx:36-38` usan las funciones; `fechaHora` ya no se
     importa en ninguno de los dos y todos los imports restantes se usan (ESLint sin advertencias). Clases CSS module,
     sin estilos en línea ni colores; el único primario de `RevisarReporte.jsx` sigue en l. 231.
   - Quedan literales `por hora` (`RevisarReporte.jsx:69`) y `Espacio:` (l. 87), fuera de la lista de la observación;
     coherente con pantallas previas (`FichaEspacio.jsx:172-173`, `DetalleReclamo.jsx:96`). No es hallazgo.
3. **Spec.** `specs/HU-27/design.md:37` (DT-14), `:77` (fila nueva de `ConversionReportes.java`, N) y `:685`
   (dependencias) coinciden con el código. `design.md:9` sigue citando `ConversionReclamos` como lo reutilizado de
   HU-26, lo cual es correcto. `requirements.md` y `tasks.md` no cambiaron (modificación 21:06:28, anterior a la
   revisión previa).
4. **Verificación.** `./init.sh --rapido` en esta sesión: Docker `[OK]`, `feature_list.json` 27 done / 1 in_progress,
   specs/progress/tasks coherentes, 10 reglas de arquitectura `[OK]`, unitarias de 59 clases tocadas (incluida
   `ConversionReportesTest`) con `Tests run: 906, Failures: 0, Errors: 0, Skipped: 0`, ESLint sin errores ni
   advertencias; sin `[FAIL]`. Coincide con lo reportado por el implementer (906 unitarias). El cambio en el
   controlador es una delegación idéntica, así que no se repitió `verify` (el implementer reporta 1231 IT en verde,
   `ReporteAdminControllerIT` sin cambiar aserciones).

Las dos observaciones no bloqueantes de la revisión original quedan resueltas.
