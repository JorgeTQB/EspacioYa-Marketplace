# Review — HU-26

> Transcrito por el orquestador desde la respuesta del reviewer (sin herramienta de escritura en su sesión).

**Veredicto:** APPROVED

## Resultado de ./init.sh
`./init.sh` completo, exit 0 ("Entorno listo"):
- Reglas de arquitectura y convenciones: todas OK.
- Unitarias: 301 tests, 0 fallos, 0 errores (19 clases tocadas).
- `./mvnw verify`: 1159 tests, 0 fallos, 0 errores, 0 omitidos (`2_Backend/target/failsafe-reports/failsafe-summary.xml`).
- Frontend: ESLint sin advertencias y `npm run build` OK.

## Trazabilidad requirements ↔ tests
R1–R70 tienen al menos un `@DisplayName("HU-26 R<n>: …")`. Los tests clave comprueban resultados concretos (HTTP, JSON, filas en BD, auditoría, correos).
- R1, R2: `ReclamoControllerIT.abre`, `ReclamoServiceTest.abre`
- R3: `ReclamoControllerIT.identidadDelServidor`
- R4: `ReglasReclamoTest`, `ReclamoControllerIT.enCursoYEnElInicio`, `dentroDeLas48Horas`
- R5: `ReclamoControllerIT.estadosNoConfirmados`
- R6: `ReclamoControllerIT.sinEmpezar`
- R7: `ReclamoControllerIT.plazoVencido`, `ReglasReclamoTest.enElBorde`
- R8: `ReclamoLiquidacionIT.yaLiquidada`
- R9: `ReclamoControllerIT.ajenaEInexistente`
- R10: `ReclamoControllerIT.otroRol`
- R11: `ReclamoControllerIT.sinSesion`
- R12: `ReclamoControllerIT.segundoReclamo`
- R13, R14: `ReclamoControllerIT.dosEnviosALaVez`, `ReclamoRestriccionesIT.unoPorReserva`
- R15–R17, R19: `ReclamoControllerIT.reservaInvalida`, `motivoInvalido`, `descripcionInvalida`, `quedaVaciaSinLeerLaReserva`; `ReclamoDTOsTest`
- R18: `ReclamoControllerIT.guardaSanitizada`, `ReclamoAdminControllerIT.resolucionSanitizada`
- R20, R50: `ReclamoLiquidacionIT.retieneYLuegoLibera`, `GeneradorLiquidacionesTest`, `ReclamosAbiertosImplIT`
- R21: `ReclamoServiceTest.bloqueaAntesDelReloj`, `ReclamoLiquidacionIT.despuesDeGenerar`
- R22: `ReclamoControllerIT.avisaAlArrendador`, `rechazoSinCorreo`, `CorreosReclamoTest`
- R23–R29: `ReclamoControllerIT.hiloDeLasDosPartes`, `reclamoCerrado`, `terceroOIdMalo`, `adminNoEsParte`, `undecimoMensaje`, `textoInvalido`, `limiteConcurrente`; `ReclamoServiceTest`
- R30: `ReclamoControllerIT.sinEdicion`
- R31–R34: `ReclamoControllerIT.listasPorParte`, `detalleDelArrendadorSinDatosDelUsuario`, `reclamablesDelUsuario`, `paginaInvalida`
- R35–R41: `ReclamoAdminControllerIT.abiertos`, `resueltos`, `detalleAdmin`, `otroRol`, `grupoInvalido`, `noEncontrado`
- R42–R44, R56: `ReclamoAdminControllerIT.total`, `parcial`; `PagoServiceImplTest.reclamo*`
- R45: `ReclamoAdminControllerIT.total`, `ReclamoReembolsoFallidoIT.pasarelaFalla`
- R46, R47: `ReclamoAdminControllerIT.montoInvalido`, `reembolsoInvalido`; `ReclamoAdminServiceTest`
- R48: `ReclamoLiquidacionIT.conReembolsoNuncaEntra`
- R49, R51: `ReclamoAdminControllerIT.aFavorDelArrendador`
- R52, R53: `ReclamoAdminControllerIT.camposInvalidos`, `ReclamoDTOsTest`
- R54, R55: `ReclamoAdminControllerIT.yaResuelto`, `dosResolucionesALaVez`
- R57, R58: `ReclamoAdminControllerIT.correosAFavorDelUsuario`, `correosAFavorDelArrendador`; `CorreosReclamoTest`
- R59: `ReclamoAdminServiceTest.falloSinCorreos`, `ReclamoAdminControllerIT.rollbackSinCorreos`
- R60–R63: `ReclamoControllerIT.auditaApertura`, `auditaMensaje`; `ReclamoAdminControllerIT.auditaResolucion`; `ReclamoServiceTest.rechazosSinAuditoria`
- R64–R66: `ReclamoRestriccionesIT.catalogos`, `coherenciaDeLaResolucion`, `llavesForaneasSinCascada`
- R67–R70: `DatosPersonalesReclamosIT`, `DatosPersonalesReclamosImplTest`

## Trazabilidad con la historia
- CA1: R1–R9, R15–R17, R19–R33
- CA2: R35, R37, R42–R48, R56–R59
- CA3: R36, R49–R52, R56–R59
- STRIDE E: R3, R9, R10, R25, R26, R38. STRIDE D: R12–R14 (`uq_reclamo_reserva`). STRIDE R: R23, R30, R60–R63, R65, R66.
- Sin fotos: ningún endpoint acepta archivos.

## Tasks
T1–T41 marcadas y verificadas en código y pruebas.

## Arquitectura y convenciones
Capas y módulos correctos (`administracion` usa otros módulos solo por interfaces de `service`; `pagos` llega a `administracion` por el puerto `ReclamosAbiertos`). Identidad desde la sesión, montos validados en el servidor, DTO `record`, arrendador sin `montoReembolso`. Frontend con kit y tokens, un solo primario por pantalla, llamadas por `src/api/administracion.js`.

Desviaciones documentadas en `progress/impl_HU-26.md`, todas justificadas:
1. `ck_reclamo_resolucion` más estricto que el design (`IS NOT NULL`), cumple R65.
2. `numeroDePagina` en `VistaReclamos` y no en el controller.
3. IT del reembolso fallido en `ReclamoReembolsoFallidoIT`.
4. `ReclamosAdmin.jsx` reutiliza `SeccionLiquidaciones`.

## Checkpoints
C1–C4 y C6: OK. C5: rama correcta, sin archivos sueltos; el cierre en `history.md` y la plantilla de `progress/HU-26.md` quedan para el cierre.

## Cambios requeridos
Ninguno bloqueante. Para el PR:
1. Confirmar antes del merge que `V8_1` sigue libre en `develop` (HU-27 y HU-28, mismo sprint).
2. Pendiente con el PO: hilo de mensajes (pregunta abierta 1) y remanente del reembolso parcial (pregunta abierta 2).
3. No bloquea: si una reserva no tiene bloques, `ReclamoAdminService.resolver` (líneas 150–156) no envía correos sin dejar rastro; caso imposible con los datos actuales.
