# Implementación HU-26 — Reclamos sobre una reserva

- **Fecha:** 2026-11-08 · **Rama:** feature/HU-26-reclamos-reserva · **Dueña:** Adriana Callupe
- **Agente:** implementer · **Spec:** `specs/HU-26/` (R1–R70, T1–T41), decisiones P1–P12 aprobadas sin cambios.
- **Tasks:** T1–T41 completas (`[x]` en `specs/HU-26/tasks.md`).
- **Verificación:** `./init.sh` completo — ver "Resultado de ./init.sh".

## Desviaciones del spec

1. **`ck_reclamo_resolucion` más estricto (design § 8).** El SQL del design aceptaba `RESUELTO_USUARIO` con
   `monto_reembolso` nulo y estados resueltos con `resuelto_en` nulo: en PostgreSQL un `CHECK` cuyo resultado es NULL
   (comparación con NULL) se acepta. `ReclamoRestriccionesIT.coherenciaDeLaResolucion` lo detectó. Se agregaron
   `IS NOT NULL` explícitos a `resolucion`, `resuelto_en` y `monto_reembolso` en las ramas resueltas. Cumple R65 tal como
   está escrito; la migración aún no está en `develop`.
2. **Parseo de `pagina`.** El design ponía `pagina(String)` en `ConversionReclamos` (controlador), pero los servicios
   reciben la página como texto para comprobar el rol antes (R10, R26, R38). Se dejó como `VistaReclamos.numeroDePagina`
   (capa service); `ConversionReclamos` solo tiene `idReclamo`. Mensaje y campo reutilizan `BusquedaService`.
3. **IT del reembolso fallido en clase aparte.** T32 pedía probar `PAGOS_SIMULADA_FALLOS_REEMBOLSO=1` dentro de
   `ReclamoAdminControllerIT`; esa propiedad necesita otro contexto de Spring, así que va en
   `ReclamoReembolsoFallidoIT` (mismo patrón que `ReembolsoAdminControllerIT` de HU-16).
4. **IT de HU-22 para reclamos en clase propia** (`DatosPersonalesReclamosIT`, extiende `DatosPersonalesSoporte`); en
   `DatosPersonalesControllerIT` solo se actualizaron las aserciones que fijan las secciones (`reclamos` al final).
5. **Pantalla de admin:** `ReclamosAdmin.jsx` reutiliza `pages/pagos/SeccionLiquidaciones.jsx` (lista genérica con "Ver
   más") adaptando `reclamos` → `liquidaciones`, en vez de duplicarla. No es del kit.
6. **Sin cambios a `docs/requerimientos.md` §9 punto 3:** el PO no confirmó la pregunta abierta 1 (T40).
7. **Número de migración:** `V8_1` sigue libre en `develop` al 2026-11-08; si HU-27/HU-28 lo toman antes del merge, hay
   que renombrar.

## Archivos

### Backend — nuevos (`2_Backend/src/main/java/pe/edu/ulima/espacioya/`)
- `administracion/domain/`: `EstadoReclamo`, `MotivoReclamo`, `PapelReclamo`, `TipoReembolsoReclamo`,
  `MotivoNoReclamable`, `ReglasReclamo`, `Reclamo`, `MensajeReclamo`.
- `administracion/repository/`: `ReclamoRepository`, `MensajeReclamoRepository`.
- `administracion/dto/`: `LectoresJson`, `CrearReclamoDTO`, `NuevoMensajeDTO`, `ResolucionReclamoDTO`,
  `DetalleReclamoDTO`, `DetalleReclamoAdminDTO`, `MensajeReclamoDTO`, `ReclamoResumenDTO`, `PaginaReclamosDTO`,
  `ReclamablesDTO`, `ReclamoExportadoDTO`.
- `administracion/service/`: `ReclamoService`, `ReclamoAdminService`, `VistaReclamos`, `AvisosReclamo`;
  `service/correo/`: `DatosReclamoAbierto`, `DatosResolucion`, `CorreoReclamoAbierto`, `FabricaReclamoAbierto`,
  `CorreoResolucionUsuario`, `FabricaResolucionUsuario`, `CorreoResolucionArrendador`, `FabricaResolucionArrendador`;
  `service/impl/`: `ReclamosAbiertosImpl`, `DatosPersonalesReclamosImpl`.
- `administracion/controller/`: `ConversionReclamos`, `ReclamoController`, `ReclamoAdminController`.
- `reservas/service/`: `ReservasParaReclamo`, `ReservaParaReclamo`; `reservas/service/impl/ReservasParaReclamoImpl`.
- `pagos/service/ReembolsoPorReclamo`, `pagos/service/puertos/ReclamosAbiertos`.
- `2_Backend/src/main/resources/db/migration/V8_1__reclamos.sql`.

### Backend — modificados
- `pagos/domain/Reembolso.java` (`porReclamo`), `pagos/repository/LiquidacionDetalleRepository.java`
  (`existsByReservaId`), `pagos/service/PagoService.java` y `impl/PagoServiceImpl.java` (3 métodos, `reembolsar` común),
  `pagos/service/GeneradorLiquidaciones.java` (puerto `ReclamosAbiertos`).

### Pruebas — nuevas (`2_Backend/src/test/java/pe/edu/ulima/espacioya/`)
- `administracion/domain/ReglasReclamoTest`, `ReclamoTest`; `administracion/dto/ReclamoDTOsTest`;
  `administracion/service/ReclamoServiceTest`, `ReclamoAdminServiceTest`, `AvisosReclamoTest`;
  `administracion/service/correo/CorreosReclamoTest`; `administracion/service/impl/DatosPersonalesReclamosImplTest`,
  `ReclamosAbiertosImplIT`; `administracion/controller/ReclamosSoporte`, `ReclamoRestriccionesIT`, `ReclamoControllerIT`,
  `ReclamoAdminControllerIT`, `ReclamoLiquidacionIT`, `ReclamoReembolsoFallidoIT`;
  `reservas/service/impl/ReservasParaReclamoImplTest`; `cuentas/controller/DatosPersonalesReclamosIT`.

### Pruebas — modificadas
- `pagos/service/impl/PagoServiceImplTest` (constructor + HU-26), `pagos/service/GeneradorLiquidacionesTest`
  (constructor + HU-26), `pagos/controller/PagosSoporte` y `cuentas/controller/DatosPersonalesSoporte` (borran
  `mensaje_reclamo` y `reclamo`; `ParticipanteQueFalla` → `@Order(5)`), `cuentas/controller/DatosPersonalesControllerIT`
  (sección `reclamos`).

### Frontend (`3_frontend/src/`)
- Nuevos: `pages/reservas/ReportarProblema.jsx` + `.module.css`; `pages/administracion/MisReclamos`, `DetalleReclamo`,
  `HiloReclamo`, `ReclamosAdmin`, `ResolverReclamo` (`.jsx` + `.module.css`).
- Modificados: `api/administracion.js` (8 funciones), `components/ui/EtiquetaEstado.jsx` (3 estados),
  `pages/administracion/textosAdministracion.js`, `pages/reservas/MisReservas.jsx`, `pages/cuentas/Panel.jsx`,
  `pages/cuentas/DatosPersonales.jsx`, `pages/cuentas/textosCuentas.js`, `App.jsx` (4 rutas).

### Documentación
- `docs/architecture.md` (puerto `ReclamosAbiertos`, `@Order(4)`, métodos de `PagoService`, `ReservasParaReclamo`, sección
  "Reclamos: qué deja HU-26", nota de HU-19), `docs/modelo-datos.md` (columnas, restricciones, marcador "Texto
  eliminado."), `docs/frontend.md` (fila Reclamo, `EtiquetaEstado`, pantallas).

## Trazabilidad

- R1 → `ReclamoServiceTest.abre`, `ReclamoControllerIT.abre`, `ReclamoTest.abre`
- R2 → `ReclamoControllerIT.abre`, `ReclamoServiceTest.abre`
- R3 → `ReclamoServiceTest.partesDelServidor`, `ReclamoControllerIT.identidadDelServidor`, `ReclamoDTOsTest.crearValido`, `ReservasParaReclamoImplTest.inicioFinYArrendador`
- R4 → `ReglasReclamoTest.enElInicio`, `ReglasReclamoTest.justoAntesDelBorde`, `ReglasReclamoTest.conHoraFin24`, `ReclamoServiceTest.enElInicio`, `ReclamoControllerIT.enCursoYEnElInicio`, `ReclamoControllerIT.dentroDeLas48Horas`, `ReservasParaReclamoImplTest.finConHora24`
- R5 → `ReglasReclamoTest.noConfirmada`, `ReclamoServiceTest.noConfirmada`, `ReclamoControllerIT.estadosNoConfirmados`, `ReservasParaReclamoImplTest.noConfirmadas`
- R6 → `ReglasReclamoTest.antesDelInicio`, `ReclamoServiceTest.antesDelInicio`, `ReclamoControllerIT.sinEmpezar`
- R7 → `ReglasReclamoTest.enElBorde`, `ReglasReclamoTest.mismoPlazoQueLaLiquidacion`, `ReclamoServiceTest.plazoVencido`, `ReclamoControllerIT.plazoVencido`
- R8 → `ReclamoServiceTest.yaLiquidada`, `ReclamoLiquidacionIT.yaLiquidada`, `PagoServiceImplTest.reservaLiquidada`
- R9 → `ReclamoServiceTest.ajena`, `ReclamoControllerIT.ajenaEInexistente`, `ReservasParaReclamoImplTest.ajenaOInexistente`
- R10 → `ReclamoServiceTest.otroRol`, `ReclamoControllerIT.otroRol`
- R11 → `ReclamoControllerIT.sinSesion`
- R12 → `ReclamoServiceTest.yaReclamada`, `ReclamoControllerIT.segundoReclamo`
- R13 → `ReclamoRestriccionesIT.unoPorReserva`, `ReclamoControllerIT.dosEnviosALaVez`
- R14 → `ReclamoServiceTest.carrera`, `ReclamoControllerIT.dosEnviosALaVez`
- R15 → `ReclamoDTOsTest.reservaInvalida`, `ReclamoControllerIT.reservaInvalida`
- R16 → `ReclamoDTOsTest.motivoInvalido`, `ReclamoControllerIT.motivoInvalido`
- R17 → `ReclamoDTOsTest.descripcionInvalida`, `ReclamoDTOsTest.largoDescripcion`, `ReclamoControllerIT.descripcionInvalida`
- R18 → `ReclamoServiceTest.sanitiza`, `ReclamoControllerIT.guardaSanitizada`, `ReclamoControllerIT.hiloDeLasDosPartes`, `ReclamoAdminServiceTest.resolucionSanitizada`, `ReclamoAdminControllerIT.resolucionSanitizada`
- R19 → `ReclamoServiceTest.quedaVacia`, `ReclamoControllerIT.quedaVaciaSinLeerLaReserva`
- R20 → `GeneradorLiquidacionesTest.excluyeConReclamoAbierto`, `GeneradorLiquidacionesTest.todasRetenidas`, `ReclamosAbiertosImplIT.soloAbiertos`, `ReclamoLiquidacionIT.retieneYLuegoLibera`
- R21 → `ReclamoServiceTest.bloqueaAntesDelReloj`, `PagoServiceImplTest.bloqueaLaGeneracion`, `GeneradorLiquidacionesTest.puertoUnaVezDespuesDelBloqueo`, `ReclamoLiquidacionIT.despuesDeGenerar`
- R22 → `ReclamoServiceTest.avisaAlArrendador`, `ReclamoControllerIT.avisaAlArrendador`, `AvisosReclamoTest.abiertoTrasElCommit`, `CorreosReclamoTest.reclamoAbierto`
- R23 → `ReclamoServiceTest.escribenLasDosPartes`, `ReclamoControllerIT.hiloDeLasDosPartes`
- R24 → `ReclamoServiceTest.reclamoCerrado`, `ReclamoControllerIT.reclamoCerrado`
- R25 → `ReclamoServiceTest.terceroOInexistente`, `ReclamoControllerIT.terceroOIdMalo`, `ReclamoTest.papeles`
- R26 → `ReclamoServiceTest.adminNoEsParte`, `ReclamoControllerIT.adminNoEsParte`
- R27 → `ReclamoServiceTest.limite`, `ReclamoServiceTest.decimoMensaje`, `ReclamoControllerIT.undecimoMensaje`
- R28 → `ReclamoServiceTest.textoVacio`, `ReclamoDTOsTest.textoInvalido`, `ReclamoControllerIT.textoInvalido`
- R29 → `ReclamoServiceTest.bajoBloqueo`, `ReclamoControllerIT.limiteConcurrente`
- R30 → `ReclamoControllerIT.sinEdicion`
- R31 → `ReclamoServiceTest.listarPorParte`, `ReclamoControllerIT.listasPorParte`
- R32 → `ReclamoServiceTest.detallePorParte`, `ReclamoServiceTest.puedeEscribir`, `ReclamoControllerIT.detalleDelArrendadorSinDatosDelUsuario`
- R33 → `ReclamoServiceTest.reclamables`, `ReclamoControllerIT.reclamablesDelUsuario`
- R34 → `ReclamoServiceTest.paginaInvalida`, `ReclamoServiceTest.paginaEnorme`, `ReclamoControllerIT.paginaInvalida`
- R35 → `ReclamoAdminServiceTest.abiertos`, `ReclamoAdminControllerIT.abiertos`
- R36 → `ReclamoAdminServiceTest.resueltos`, `ReclamoAdminControllerIT.resueltos`
- R37 → `ReclamoAdminServiceTest.detalle`, `ReclamoAdminControllerIT.detalleAdmin`
- R38 → `ReclamoAdminServiceTest.otroRol`, `ReclamoAdminControllerIT.otroRol`
- R39 → `ReclamoAdminServiceTest.grupoYPagina`, `ReclamoAdminControllerIT.grupoInvalido`
- R40 → `ReclamoAdminServiceTest.inexistente`, `ReclamoAdminControllerIT.noEncontrado`
- R41 → `ReclamoControllerIT.reclamablesDelUsuario`, `ReclamoControllerIT.hiloDeLasDosPartes`, `ReclamoControllerIT.listasPorParte`, `ReclamoAdminControllerIT.abiertos`, `ReclamoAdminControllerIT.detalleAdmin`
- R42 → `ReclamoAdminServiceTest.total`, `ReclamoAdminControllerIT.total`, `ReclamoTest.aFavorDelUsuario`
- R43 → `ReclamoAdminServiceTest.parcial`, `ReclamoAdminControllerIT.parcial`
- R44 → `PagoServiceImplTest.reclamoGuardaElReembolso`, `PagoServiceImplTest.reclamoAudita`, `PagoServiceImplTest.reclamoMontoFueraDeRango`, `PagoServiceImplTest.reclamoSinPago`, `PagoServiceImplTest.reclamoYaReembolsado`, `ReclamoAdminServiceTest.total`, `ReclamoAdminControllerIT.total`
- R45 → `PagoServiceImplTest.reclamoEntregaElComando`, `ReclamoAdminControllerIT.total`, `ReclamoReembolsoFallidoIT.pasarelaFalla`
- R46 → `ReclamoAdminServiceTest.montoInvalido`, `ReclamoAdminControllerIT.montoInvalido`, `ReclamoDTOsTest.montoComoNumero`
- R47 → `ReclamoAdminServiceTest.reembolsoInvalido`, `ReclamoAdminControllerIT.reembolsoInvalido`
- R48 → `ReclamoLiquidacionIT.conReembolsoNuncaEntra`
- R49 → `ReclamoAdminServiceTest.aFavorDelArrendador`, `ReclamoAdminControllerIT.aFavorDelArrendador`, `ReclamoTest.aFavorDelArrendador`
- R50 → `GeneradorLiquidacionesTest.sinReclamoAbiertoEntra`, `ReclamosAbiertosImplIT.soloAbiertos`, `ReclamoLiquidacionIT.retieneYLuegoLibera`
- R51 → `ReclamoAdminServiceTest.aFavorDelArrendador`, `ReclamoAdminControllerIT.aFavorDelArrendador`
- R52 → `ReclamoAdminServiceTest.resolucionVacia`, `ReclamoDTOsTest.resolucionInvalida`, `ReclamoDTOsTest.largoResolucion`, `ReclamoAdminControllerIT.camposInvalidos`
- R53 → `ReclamoDTOsTest.aFavorInvalido`, `ReclamoAdminControllerIT.camposInvalidos`
- R54 → `ReclamoAdminServiceTest.yaResuelto`, `ReclamoAdminControllerIT.yaResuelto`, `ReclamoTest.resolverDosVeces`
- R55 → `ReclamoAdminServiceTest.bajoBloqueo`, `ReclamoAdminControllerIT.dosResolucionesALaVez`
- R56 → `ReclamoAdminServiceTest.respondeDetalle`, `ReclamoAdminControllerIT.total`
- R57 → `ReclamoAdminServiceTest.correos`, `CorreosReclamoTest.usuarioAFavor`, `CorreosReclamoTest.usuarioEnContra`, `ReclamoAdminControllerIT.correosAFavorDelUsuario`, `ReclamoAdminControllerIT.correosAFavorDelArrendador`
- R58 → `CorreosReclamoTest.arrendadorEnContra`, `CorreosReclamoTest.arrendadorAFavor`, `ReclamoAdminControllerIT.correosAFavorDelUsuario`, `ReclamoAdminControllerIT.correosAFavorDelArrendador`
- R59 → `ReclamoAdminServiceTest.falloSinCorreos`, `ReclamoAdminControllerIT.rollbackSinCorreos`, `AvisosReclamoTest.resolucionUnoPorCorreo`
- R60 → `ReclamoServiceTest.auditaApertura`, `ReclamoControllerIT.auditaApertura`
- R61 → `ReclamoServiceTest.auditaMensaje`, `ReclamoControllerIT.auditaMensaje`
- R62 → `ReclamoAdminServiceTest.auditoria`, `ReclamoAdminServiceTest.auditoriaArrendador`, `ReclamoAdminControllerIT.auditaResolucion`
- R63 → `ReclamoServiceTest.rechazosSinAuditoria`, `ReclamoAdminServiceTest.yaResuelto`, `ReclamoAdminControllerIT.montoInvalido`
- R64 → `ReclamoRestriccionesIT.catalogos`
- R65 → `ReclamoRestriccionesIT.coherenciaDeLaResolucion`
- R66 → `ReclamoRestriccionesIT.llavesForaneasSinCascada`
- R67 → `DatosPersonalesReclamosImplTest.exportaUsuario`, `DatosPersonalesReclamosImplTest.seccionVacia`, `DatosPersonalesReclamosIT.usuarioExporta`, `DatosPersonalesReclamosIT.seccionVacia`, `DatosPersonalesControllerIT.usuarioExporta`
- R68 → `DatosPersonalesReclamosImplTest.exportaArrendador`, `DatosPersonalesReclamosIT.usuarioExporta`, `DatosPersonalesReclamosIT.arrendadorExporta`
- R69 → `DatosPersonalesReclamosImplTest.impedimento`, `DatosPersonalesReclamosIT.abiertoImpideEliminar`
- R70 → `DatosPersonalesReclamosImplTest.anonimiza`, `DatosPersonalesReclamosIT.eliminarReemplazaTextos`

## Resultado de ./init.sh

`./init.sh` completo (2026-11-08), exit code 0, "Entorno listo. Puedes empezar a trabajar.":
- Reglas de arquitectura y convenciones: todas OK.
- Unitarias de las clases tocadas (19 clases): 301 tests, 0 fallos.
- `./mvnw verify` con todas las `*IT`: 1159 tests, 0 fallos, 0 errores, 0 omitidos.
- Frontend: ESLint sin errores ni advertencias; `npm run build` OK.

`feature_list.json` sigue `in_progress`: falta que pase el reviewer. No se hizo commit, push ni PR.

## Cierre (2026-11-08)

Reviewer `APPROVED` (`progress/review_HU-26.md`). Cierre según `AGENTS.md` §5, sin tocar código de aplicación ni tests:
- `feature_list.json`: HU-26 → `done`.
- `progress/history.md`: entrada "2026-11-08 — HU-26 Reclamos sobre una reserva (done)" al final (reasignación, spec
  aprobado por Adriana Callupe el 2026-11-08, implementación, desviaciones, veredicto y pendientes).
- `progress/HU-26.md`: vuelve a ser idéntico a `progress/_plantilla.md`.
- Sin archivos temporales ni sueltos (`git status` solo muestra archivos de la historia, sus specs y su progress) y sin
  prints de depuración (`System.out`, `printStackTrace`, `console.log`, `debugger`) en el código tocado.
- `./init.sh --rapido`: salida OK. Paso 3 "Specs, progress y tasks coherentes con el estado de cada historia"; 301
  unitarias de las 19 clases tocadas en verde; reglas de arquitectura y ESLint OK. El conteo "1 in_progress" es HU-25
  (Jorge Quispe), que figura así en `develop`; no es de esta historia.
- Pendientes no bloqueantes para el PR: confirmar que `V8_1` sigue libre en `develop` antes del merge; preguntas abiertas
  1 (hilo de mensajes, §9 punto 3) y 2 (remanente del reembolso parcial) con el PO.
- No se hizo commit, push ni PR.
