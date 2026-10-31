# Implementación HU-15 · Cancelación de reservas (★ Strategy)

- **Dueño:** Kevin Chan · **Rama:** `feature/HU-15-cancelacion-reservas` · **Fecha:** 2026-10-31 · **Agente:** implementer
- **Spec:** `specs/HU-15/{requirements,design,tasks}.md` (aprobado por Kevin Chan el 2026-10-31).
- **Estado:** T1–T20 `[x]`. Historia sigue `in_progress` (no se marca `done` hasta el reviewer). Sin commit, push ni PR.

## Resultado de `./init.sh` (completo, sin `--rapido`)

`[OK] Entorno listo` (exit 0), ejecutado dos veces; la última tras el ajuste final de longitud de líneas en tests:

- Arnés, `feature_list.json`, specs/progress y reglas de arquitectura: `[OK]` (sin colores fuera de tokens, sin secretos).
- Backend `./mvnw verify`: **954 unitarias** (889 antes de HU-15) y **660 IT** con Testcontainers, 0 fallos, 0 errores.
  Incluye `CancelacionReservaControllerIT` (33) y `CancelacionConcurrenciaIT` (1, 5 rondas) y la regresión de HU-12/13/14.
- Frontend: ESLint sin errores ni advertencias; `npm run build` compila.
- Único `[WARN]` (preexistente, de entorno local): faltan `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA`,
  `ALMACENAMIENTO_LLAVE_FIRMA` para `spring-boot:run`; no afecta a las pruebas.

## Tareas

| Task | Hecho |
|------|-------|
| T1 | `V4_2__cancelacion_reserva.sql`: `cancelada_por`, `cancelada_en`, `monto_reembolso` + `ck_reserva_cancelada_por` y `ck_reserva_cancelacion` (solo exigible con datos de cancelación) |
| T2 | `QuienCancela` (`rolRequerido`), `MotivoNoCancelable`; `Reserva` (3 campos, `motivoNoCancelable`, `cancelar`, getters); `ReservaConHorario.instanteDeInicio()` |
| T3 | ★ Strategy: `SolicitudCancelacion`, `PoliticaReembolso`, `ReembolsoUsuarioConAnticipacion`, `ReembolsoUsuarioSinAnticipacion`, `ReembolsoCancelaArrendador`, `ReembolsoCalculado`, `CalculoReembolso` (`politicaFija`, exige exactamente una) |
| T4 | `PagoRepository.findByReservaIdAndEstado`; `PagoService` (contrato § 11.1 en el javadoc), `ReembolsoPorCancelacion`, `PagoServiceImpl` (`MANDATORY`; busca el `PAGADO`, valida `0 < monto ≤ pago`, audita `REEMBOLSO_SOLICITADO`; **sin** pasarela, **sin** tabla `reembolso`, **sin** tocar `pago.estado`) |
| T5 | `AvisoCancelacionUsuario`, `AvisoCancelacionArrendador`; 2 métodos en `NotificadorCorreo` y en `NotificadorCorreoSimulado` (una línea INFO sin datos); los 6 dobles ganan los 2 métodos vacíos |
| T6 | `ConversionReservas.idReserva`; `ReservaServiceImpl.consultar` la usa y se borró `idValido` |
| T7 | `VistaCancelacionDTO`, `ConfirmarCancelacionDTO`, `CancelacionDTO` |
| T8 | `AvisosCancelacion` (correos leídos en la transacción, un `DespuesDelCommit` por aviso) |
| T9 | `CancelacionReservaService` (`vistaPrevia` `readOnly` sin bloqueos; `confirmar` con el orden de design § 7) |
| T10 | `GET`/`POST /{id}/cancelacion` en `ReservaController` (USUARIO) y `ReservasDeMisEspaciosController` (ARRENDADOR) |
| T11–T12 | `CancelacionReservaControllerIT` (33 casos, notificador falso `@Primary` anidado, IP `10.17.x.x`, `filaPagoPagado`, `@AfterEach` que borra `pago`, punta a punta con la pasarela simulada) |
| T13 | `CancelacionConcurrenciaIT` (5 rondas usuario vs arrendador a la vez) |
| T14 | `Modal.jsx`: prop `textoCerrar` (por defecto "Cancelar") |
| T15 | `api/reservas.js` (4 funciones) y bloque HU-15 de `textosReservas.js` |
| T16 | `pages/reservas/CancelarReserva.jsx` + `.module.css` |
| T17 | `MisReservas.jsx` y `ReservasDeMisEspacios.jsx`: botón en Próximas (`CONFIRMADA`), `recarga` como `key`, `Alerta exito`; se quitó el comentario "HU-15 agregará 'Cancelar'" |
| T18 | `docs/architecture.md` (contrato de `PagoService` en "Pagos: qué deja HU-13", nueva sección "Cancelación: qué deja HU-15", fila de patrones), `docs/modelo-datos.md` (3 columnas y 2 restricciones), `docs/frontend.md` (`Modal.textoCerrar`, `Tarjeta.acciones`, pantalla de cancelación). Tabla de puertos sin cambios |
| T19 | Avisos a Ariel Enriquez (HU-16), Jorge Quispe (HU-25) y Sebastian Garate (HU-21) en `progress/HU-15.md` |
| T20 | `./init.sh` completo + este informe |

## Archivos

**Nuevos — backend (main)**
- `2_Backend/src/main/resources/db/migration/V4_2__cancelacion_reserva.sql`
- `reservas/domain/`: `QuienCancela`, `MotivoNoCancelable`, `SolicitudCancelacion`, `PoliticaReembolso`,
  `ReembolsoUsuarioConAnticipacion`, `ReembolsoUsuarioSinAnticipacion`, `ReembolsoCancelaArrendador`, `ReembolsoCalculado`,
  `CalculoReembolso`
- `reservas/dto/`: `VistaCancelacionDTO`, `ConfirmarCancelacionDTO`, `CancelacionDTO`
- `reservas/service/`: `CancelacionReservaService`, `AvisosCancelacion`
- `pagos/service/`: `PagoService`, `ReembolsoPorCancelacion`; `pagos/service/impl/PagoServiceImpl`
- `cuentas/service/`: `AvisoCancelacionUsuario`, `AvisoCancelacionArrendador`

**Modificados — backend (main)**
- `reservas/domain/Reserva.java`, `reservas/domain/ReservaConHorario.java`
- `reservas/service/ConversionReservas.java`, `reservas/service/impl/ReservaServiceImpl.java`
- `reservas/controller/ReservaController.java`, `reservas/controller/ReservasDeMisEspaciosController.java`
- `pagos/repository/PagoRepository.java`
- `cuentas/service/NotificadorCorreo.java`, `cuentas/service/NotificadorCorreoSimulado.java`

**Nuevos — tests**
- `reservas/domain/CalculoReembolsoTest`, `reservas/domain/ReservaConHorarioTest`
- `reservas/service/ConversionReservasTest`, `reservas/service/AvisosCancelacionTest`,
  `reservas/service/CancelacionReservaServiceTest`
- `pagos/service/impl/PagoServiceImplTest`
- `reservas/controller/CancelacionReservaControllerIT`, `reservas/controller/CancelacionConcurrenciaIT`

**Modificados — tests (solo lo permitido)**
- `reservas/domain/ReservaTest` (7 casos agregados + constante `EMPIEZA`), `cuentas/service/NotificadorCorreoSimuladoTest`
  (2 casos agregados + imports).
- Los 6 dobles de `NotificadorCorreo` (`PagosSoporte.NotificadorFalso`, `UsuarioControllerIT`,
  `RecuperacionContrasenaControllerIT`, `ArrendadorControllerIT`, `RevisionIdentidadControllerIT`,
  `CuentaAdminControllerIT`): solo 2 imports y 2 métodos vacíos. **Ningún caso existente cambió ni se borró**
  (`ReservaServiceImplTest`, `ReservaControllerIT`, `MisReservasControllerIT`, `ReservaDAOJpaIT` intactos y en verde).

**Frontend**
- Nuevos: `3_frontend/src/pages/reservas/CancelarReserva.jsx`, `CancelarReserva.module.css`
- Modificados: `components/ui/Modal.jsx`, `api/reservas.js`, `pages/reservas/MisReservas.jsx`,
  `pages/reservas/ReservasDeMisEspacios.jsx`, `pages/reservas/textosReservas.js`

**Docs / progreso**
- `docs/architecture.md`, `docs/modelo-datos.md`, `docs/frontend.md`, `specs/HU-15/tasks.md` (`[x]`), `progress/HU-15.md`

## Trazabilidad R → test

Abreviaturas: `CRIT` = `CancelacionReservaControllerIT`, `CRST` = `CancelacionReservaServiceTest`.

| R | Tests |
|---|-------|
| R1 | `CRIT.sinSesion` |
| R2 | `CRIT.rolAjenoEnLaRutaDelUsuario`, `CRST.rolAjenoEsAccesoDenegado` |
| R3 | `CRIT.rolAjenoEnLaRutaDelArrendador`, `CRST.rolAjenoEsAccesoDenegado` |
| R4 | `CRIT.idMalEscrito`, `ConversionReservasTest.idsBienEscritos`, `ConversionReservasTest.idsMalEscritos`, `CRST.idMalEscrito` |
| R5 | `CRIT.ajenaDelUsuario`, `CRST.ajenaDelUsuario`, `CRST.inexistente` |
| R6 | `CRIT.ajenaDelArrendador`, `CRST.ajenaDelArrendador` |
| R7 | `CRIT.ignoraLoQueNoEsElMontoVisto` |
| R8 | `ReservaTest.canceladaDaYaCancelada`, `ReservaTest.yaCanceladaVaPrimero`, `CRIT.yaCancelada`, `CRST.confirmarYaCancelada`, `CRST.vistaPreviaNoCancelable` |
| R9 | `ReservaTest.noConfirmadas`, `CRIT.noConfirmadas`, `CRST.confirmarPendiente`, `CRST.vistaPreviaNoCancelable` |
| R10 | `ReservaTest.confirmadaYaIniciada`, `CRIT.yaIniciada`, `CRST.confirmarYaIniciada` |
| R11 | `CalculoReembolsoTest.usuarioConCuarentaYOchoHorasExactas`, `CalculoReembolsoTest.usuarioConMuchaAnticipacion`, `CRIT.limiteDeCuarentaYOchoHoras` |
| R12 | `CalculoReembolsoTest.usuarioUnSegundoAntesDelLimite`, `CalculoReembolsoTest.usuarioConUnMinuto`, `CRIT.limiteDeCuarentaYOchoHoras`, `CRIT.ceroPorCientoSinReembolso`, `CRST.ceroPorCientoNoReembolsa` |
| R13 | `CalculoReembolsoTest.arrendadorSiempreCien`, `CRIT.arrendadorCancelaAlCien`, `CRST.arrendadorCancela`, `CRST.vistaPreviaDelArrendador` |
| R14 | `CalculoReembolsoTest.anticipacionEsInicioMenosAhora`, `ReservaConHorarioTest.instanteDeInicioEnLima`, `ReservaConHorarioTest.instanteDeInicioALaMedianoche`, `CRIT.limiteDeCuarentaYOchoHoras` |
| R15 | `CalculoReembolsoTest.exactamenteUnaAplica`, `CalculoReembolsoTest.ningunaAplica`, `CalculoReembolsoTest.dosAplican` |
| R16 | `CalculoReembolsoTest.montoConEscalaDos` (+ montos exactos en `usuarioConCuarentaYOchoHorasExactas` / `usuarioUnSegundoAntesDelLimite`) |
| R17 | `CRIT.vistaPreviaDelUsuario` (claves exactas), `CRIT.vistaPreviaNoEscribe`, `CRST.vistaPreviaDelUsuario`, `CRST.vistaPreviaDelArrendador` |
| R18 | `CRIT.vistaPreviaNoEscribe`, `CRST.vistaPreviaNoEscribe` |
| R19 | `CRIT.confirmaYGuarda` (claves exactas), `ReservaTest.confirmadaFuturaEsCancelable`, `ReservaTest.cancelarGuardaLosTresDatos`, `CRST.usuarioCancelaConAnticipacion` |
| R20 | `CRIT.confirmaYGuarda`, `ReservaTest.cancelarGuardaLosTresDatos`, `CRST.usuarioCancelaConAnticipacion` |
| R21 | `CRIT.liberaLosBloques` (otro usuario reserva → 201), `CRIT.dePuntaAPunta`, `CRST.usuarioCancelaConAnticipacion` |
| R22 | `CRST.disparaElReembolso`, `CRST.arrendadorCancela`, `CRIT.solicitaElReembolso` |
| R23 | `CRST.ceroPorCientoNoReembolsa`, `CRIT.ceroPorCientoSinReembolso` |
| R24 | `PagoServiceImplTest.auditaLaSolicitud`, `.noTocaElPago`, `.sinPasarela`, `.exigeTransaccion`, `.montoCero`, `.montoMayorQueElPago`, `CRIT.solicitaElReembolso` |
| R25 | `PagoServiceImplTest.sinPagoPagado`, `CRIT.sinPagoPagadoEsError` |
| R26 | `AvisosCancelacionTest.avisaAlUsuario`, `CRIT.enviaLosDosCorreos`, `CRST.programaLosAvisos`, `NotificadorCorreoSimuladoTest.cancelacionAlUsuarioSinCorreoNiDatos` |
| R27 | `AvisosCancelacionTest.avisaAlArrendadorSinMontos`, `CRIT.enviaLosDosCorreos`, `CRST.programaLosAvisos`, `NotificadorCorreoSimuladoTest.cancelacionAlArrendadorSinCorreoNiDatos` |
| R28 | `AvisosCancelacionTest.unFalloNoImpideElOtro`, `CRIT.unCorreoFallaYLaCancelacionSigue` |
| R29 | `AvisosCancelacionTest.nadaAntesDelCommit`, `CRIT.sinPagoPagadoEsError`, `CRIT.montoCambio` (y todo caso de error vía `sinCambios`: sin avisos) |
| R30 | `CRIT.ignoraLoQueNoEsElMontoVisto`, `CRST.vistaPreviaDelUsuario` |
| R31 | `CRIT.montoVistoInvalido`, `CRIT.montoVistoSinDecimales`, `CRST.montoVistoInvalido`, `CRST.montoVistoSinCerosEsValido` |
| R32 | `CRIT.montoCambio`, `CRIT.montoVistoSinDecimales`, `CRST.montoCambio` |
| R33 | `CRST.ordenDeLosBloqueos` (`InOrder`), `CancelacionConcurrenciaIT.dosConfirmacionesALaVez` |
| R34 | `CancelacionConcurrenciaIT.dosConfirmacionesALaVez`, `CRST.confirmarYaCancelada` |
| R35 | `CRIT.auditaLaCancelacion`, `CRST.auditaLaCancelacion` |
| R36 | `CRIT.rolAjenoEnLaRutaDelUsuario`, `.rolAjenoEnLaRutaDelArrendador`, `.ajenaDelUsuario`, `.ajenaDelArrendador`, `.yaCancelada`, `.noConfirmadas`, `.yaIniciada`, `.sinPagoPagadoEsError`, `.montoVistoInvalido`, `.montoCambio` (helper `sinCambios`: reserva, bloques, auditoría y pagos iguales, sin `RESERVA_CANCELADA` ni `REEMBOLSO_SOLICITADO`); `CRST.montoCambio`, `.confirmarYaCancelada`, `.confirmarPendiente`, `.confirmarYaIniciada` |
| R37 | `CRIT.despuesDeCancelar` |
| R38 | `CRIT.despuesDeCancelar`, `CRIT.arrendadorCancelaAlCien` |
| R39 | `CRIT.restriccionesDeCancelacion` |

## Desviaciones y notas

1. **`CancelarReserva` no recibe `paraArrendador`.** design § 12 lo lista como prop que "solo cambia textos", pero la línea del
   reembolso ya la elige la `politica` que devuelve la API (`ARRENDADOR` tiene su propio texto) y el resto del modal es igual
   para los dos papeles: el prop habría quedado sin uso (ESLint lo marca). Sin efecto visible.
2. **48 h exactas en la IT.** En lugar de la fecha fija "2026-10-30 10:00 Lima", el test lleva el reloj a la próxima hora
   en punto y siembra la reserva a `ahora + 48 h` (1 hora, S/ 80.50): el `RelojControlable` es compartido por todas las IT
   del contexto y solo avanza, así que su instante al empezar el test no es fijo. Mismo borde (100 % → +1 s → 0 %).
3. **R10 "inicio = ahora"** se prueba en `ReservaTest.confirmadaYaIniciada`; en la IT, con una reserva de ayer.
4. **`switch (quien)` en el servicio:** solo para la **propiedad** (design § 7 pasos 3 y 7: de quién debe ser la reserva) y
   para traducir `MotivoNoCancelable` a su mensaje. La política de reembolso no tiene ningún `if`/`switch`: la elige
   `CalculoReembolso`.
5. Si `findParaActualizar` no encuentra la reserva tras `findEspacioIdById` se lanza `IllegalStateException` (las reservas no
   se borran), como `ReservasPorCobrarImpl.bloquear`.
6. Los 6 dobles reciben métodos **vacíos** (design DT-9), no `UnsupportedOperationException` como sus otros métodos: con
   `DespuesDelCommit` una excepción ahí se tragaría igual.

## Caja negra UAT (design § 13, para la pareja)

Con la pasarela simulada y el notificador simulado:
- (a) Usuario con una reserva pagada a más de 48 h: "Mis reservas" › Próximas › "Cancelar reserva" → modal con
  "Se te devolverá S/ X (100 %)" → "Sí, cancelar reserva" → `Alerta exito` con "El reembolso de S/ X está en proceso."; la
  reserva aparece en Pasadas como "Cancelada" y el horario vuelve a estar libre en la ficha del espacio.
- (b) Usuario con una reserva pagada para mañana: modal con "No corresponde reembolso (0 %)"; al confirmar, mensaje sin
  línea de reembolso.
- (c) Arrendador: "Reservas de mis espacios" › Próximas › "Cancelar reserva" → modal "Al usuario se le devolverá S/ X
  (100 %)" → al confirmar la reserva desaparece de su lista.
- (d) Log del backend: dos líneas INFO "Envío de aviso de cancelación al usuario/arrendador simulado…" por cancelación, sin
  correos ni datos.
