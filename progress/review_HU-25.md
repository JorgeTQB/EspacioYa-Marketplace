# Review — HU-25

**Veredicto:** APPROVED

Revisor, 2026-11-08 (segunda revisión). Rama `feature/HU-25-recordatorio-reserva`; todo sigue sin commitear.
En la primera revisión (CHANGES_REQUESTED) quedaron B1 y B2. Los dos están **resueltos**. Quedan 4 no bloqueantes.

## Hallazgos

### Bloqueantes (resueltos)

1. **[B1] Dos botones primarios en `/panel/perfil`.** Resuelto.
   - `3_frontend/src/pages/cuentas/Recordatorios.jsx:80` ahora es
     `<Boton tipo="submit" variante="secundario" cargando={guardando}>`.
   - Botones que quedan en la pantalla (grep de `<Boton` en `Perfil.jsx`, `Recordatorios.jsx` y `DatosPersonales.jsx`):
     - el único sin `variante` es `Perfil.jsx:352` ("Guardar datos"), el primario de la pantalla;
     - `Perfil.jsx:370`, `:374`, `:394`, `:453`, `DatosPersonales.jsx:147` y `Recordatorios.jsx:80` son `secundario`;
     - `DatosPersonales.jsx:150` es `peligro`.
     - Los `Modal` (`peligro-confirmar`) no cuentan como botones de la pantalla.
   - El spec ya coincide con el código:
     - `specs/HU-25/design.md:520-521` (§ 12) dice `Boton variante="secundario"` y explica por qué;
     - `specs/HU-25/tasks.md` T20 también;
     - `docs/frontend.md:290` dice: secundario "Guardar"; el único primario de la pantalla es "Guardar datos".
   - `progress/impl_HU-25.md:151-152` registra el cambio.
2. **[B2] `./init.sh` completo en rojo.** Resuelto.
   - Antes de correr revisé los procesos java:
     - había otro Maven `-B -ntp test` sobre `2_Backend` (PID 18440, empezó 09:37:32);
     - no lo maté: esperé a que terminara;
     - al terminar solo quedaba el language server de VS Code (PID 22164), que no usa `target/`.
   - Después corrí `./init.sh` completo, sin `--rapido`, y terminó con **exit 0**:
     - unitarias: `Tests run: 1818, Failures: 0, Errors: 0, Skipped: 0`;
     - integración: `Tests run: 1067, Failures: 0, Errors: 0, Skipped: 0`;
     - `2_Backend/target/failsafe-reports/failsafe-summary.xml`: `completed 1067`, `errors 0`, `failures 0`;
     - ESLint sin errores ni advertencias; `npm run build` OK;
     - chequeos de arquitectura y del arnés OK;
     - resumen: "Entorno listo".
   - El único WARN es de llaves locales (`CIFRADO_LLAVE` y otras) del entorno de la máquina. No es de la historia.

### No bloqueantes (siguen vigentes)

3. **[N1]** `RecordatoriosReserva.java:72` / `ReservaRepository.findIdsPorRecordar`:
   - una reserva `CONFIRMADA` que ya empezó hoy y no tiene recordatorio sigue siendo candidata;
   - se bloquea con `findParaActualizar` en cada pasada (cada 5 min) hasta que termina el día;
   - el resultado es correcto (R16) y el efecto es acotado.
   - Se podría acotar `desde` a la fecha y hora de inicio. Opcional.
4. **[N2]** El árbol de trabajo mezcla cambios del arnés con los de la historia: `CLAUDE.md`, `.claude/agents/*.md` y
   `.claude/settings.json`.
   - Están registrados en `progress/history.md`.
   - Conviene subirlos en un commit aparte del de HU-25.
5. **[N3]** Sigue existiendo `progress/.init_review_HU-25.log`.
   - Lo ignora `.gitignore` (`*.log`).
   - Conviene borrarlo.
6. **[N4]** R10 solo se prueba con cuentas nuevas.
   - Para las cuentas existentes vale el `DEFAULT TRUE` de `V7_3__recordatorios.sql:3`.
   - Es aceptable.

## Trazabilidad requirements ↔ tests

Desde la primera revisión no cambió ninguna prueba ni clase de `src/main`. `git status` muestra los mismos archivos y
la única corrección nueva es la de `Recordatorios.jsx`. Por eso la tabla sigue valiendo, y ahora todas las pruebas están
**confirmadas en verde** (B2).

- R1: [x] `PreferenciasCorreoControllerIT.usuarioConsulta`, `PreferenciasCorreoServiceTest`
- R2: [x] `PreferenciasCorreoControllerIT.arrendadorDesactiva` / `.usuarioReactiva`, `UsuarioTest`
- R3: [x] `PreferenciasCorreoControllerIT.ignoraUsuarioIdDelCuerpo`
- R4: [x] `PreferenciasCorreoControllerIT.sinCampoDa400`
- R5: [x] `PreferenciasCorreoControllerIT.adminRecibe403`, `PreferenciasCorreoServiceTest.adminNoPuede`
- R6: [x] `PreferenciasCorreoControllerIT.sinSesion401`
- R7: [x] `PreferenciasCorreoControllerIT.usuarioConsulta` / `.arrendadorDesactiva` (`Cache-Control: no-store`)
- R8: [x] `PreferenciasCorreoControllerIT.arrendadorDesactiva` (auditoría)
- R9: [x] `PreferenciasCorreoControllerIT.mismoValorSinAuditoria`, `PreferenciasCorreoServiceTest`, `UsuarioTest`
- R10: [x] `PreferenciasCorreoControllerIT.cuentaNuevaConRecordatorios`, `RecordatoriosReservaIT.preferenciaPorDefecto` (N4)
- R11: [x] `RecordatoriosReservaIT.recordatorioAlUsuarioYAlArrendador`, `.reservaHechaConMenosDeVeinticuatroHoras`, `ReglaRecordatorioTest`
- R12: [x] `RecordatoriosReservaIT.recordatorioAlUsuarioYAlArrendador`
- R13: [x] `RecordatoriosReservaIT`, `.restriccionDeLaMarca`, `ReservaTest.marcaElRecordatorio`
- R14: [x] `RecordatoriosReservaIT.segundaPasadaNoReenvia`, `RecordatoriosReservaTest.yaRecordadaAlReleer`
- R15: [x] `RecordatoriosReservaIT.fueraDePlazo`, `RecordatoriosReservaTest.todaviaLejos`, `ReglaRecordatorioTest`
- R16: [x] `RecordatoriosReservaIT.fueraDePlazo`, `RecordatoriosReservaTest.yaEmpezo`, `ReglaRecordatorioTest.enElInicio`
- R17: [x] `RecordatoriosReservaIT.noConfirmadas`, `ReservaTest.admiteRecordatorio` / `.noMarcaSinConfirmar`
- R18: [x] `RecordatoriosReservaTest.canceladaAlReleer`, `.yaRecordadaAlReleer`
- R19: [x] `RecordatoriosReservaIT.usuarioDesactivado`, `RecordatoriosReservaTest.usuarioSinPreferencia`, `CuentaServiceImplTest`
- R20: [x] `RecordatoriosReservaIT.arrendadorDesactivado`, `RecordatoriosReservaTest.arrendadorSinPreferencia`
- R21: [x] `RecordatoriosReservaIT.cuentaSuspendida`, `CuentaServiceImplTest.correoParaRecordatorioNoActiva` / `.Inexistente`
- R22: [x] `RecordatoriosReservaTest.auditaConDestinatarios`, `RecordatoriosReservaIT`
- R23: [x] `RecordatoriosReservaTest.sinCommitNoEnvia`, `.enviaAlUsuarioYAlArrendador`
- R24: [x] `RecordatoriosReservaTest.fallaUnEnvio`, `RecordatoriosReservaIT.fallaUnEnvio`
- R25: [x] `RecordatoriosReservaTest.unaFallaNoDetieneLasDemas`
- R26: [x] `ProgramacionRecordatoriosTest.activo` / `.apagado` / `.delega`
- R27: [x] `WebhookPagoIT.pagoSinRecordatorios`
- R28: [x] `CancelacionReservaControllerIT.cancelacionSinRecordatorios`
- R29: [x] `RecordatoriosReservaIT.contenidoMinimo`, `FabricaRecordatorio*Test.cuerpoMinimo`, `FormatoCorreoTest`
- R30: [x] `EnvioCorreoSmtpTest.unSoloDestinatario`, `MensajeCorreoTest`, `FabricaCorreoTest`
- R31: [x] `FabricaCorreoTest.asuntoFijo`, `NotificadorCorreoImplTest.asuntoSinDatosDeUsuarios`
- R32: [x] `EnvioCorreoSmtpTest.traduceElMensaje`
- R33: [x] `EnvioCorreoSmtpTest.fallaDelServidor`, `.otraMailException`
- R34: [x] `EnvioCorreoConfiguracionTest`, `EnvioCorreoSmtpTest.sinRemitente` / `.sinHost`
- R35: [x] `EnvioCorreoSimuladoTest.soloUnLogSinDatos`, `EnvioCorreoConfiguracionTest.simulado`
- R36: [x] `NotificadorCorreoImplTest.cadaMetodoEnviaSuCorreo`, `.comprobanteSinTarjeta`
- R37: [x] `DatosPersonalesServiceTest`, `DatosPersonalesControllerIT.exportaLaPreferenciaDeRecordatorios`

## Trazabilidad con la historia

- CA1: [x] R11–R18, R22–R26
- CA2: [x] R1–R10, R19, R20
- CA3: [x] R27, R28
- (I) Sin datos de tarjeta ni documentos: [x] R29–R31, R35, R36
- (E) Destinatarios de la reserva en el servidor: [x] R3, R11, R12, R18, R21

## Tasks completas

- T1–T19: [x]
- T20: [x]. Ahora coincide con `docs/frontend.md`, porque el botón es secundario (B1 resuelto).
- T21: [x]. Se corrigió `docs/frontend.md:290`.
- T22: [x]. `./init.sh` completo en verde, reproducido por el revisor (B2 resuelto).
- `tasks.md` no tiene ninguna casilla `[ ]`.

## Patrones ★ (design § 3)

- Factory Method: [x]. Sin cambios desde la primera revisión: `comun/correo/FabricaCorreo.java` con `enviar` final y
  `crearCorreo` abstracto.
- Adapter: [x]. Sin cambios: `EnvioCorreoSmtp` sobre `JavaMailSender`, y `EnvioCorreoSimulado`.

## Checkpoints

- C1: [x] `./init.sh` sin `--rapido` termina con exit 0.
- C2: [x] Una sola historia `in_progress` del dueño, y `progress/HU-25.md` anota la aprobación de Jorge Quispe.
- C3: [x] Arquitectura, capas, DTO, identidad desde la sesión, migración `V7_3` y Adapter de correo.
- C3 (frontend): [x] Pantalla armada con el kit, solo tokens, API vía `src/api/cuentas.js` y un solo primario en
  `/panel/perfil`.
- C4: [x] 1818 unitarias y 1067 IT en verde, `Clock` fijo, Testcontainers, ESLint y build OK.
- C5: [x] Rama `feature/HU-25-...`, entrada en `history.md` y estado `in_progress` coherente.
  - "progress vuelto a la plantilla": n/a.
  - Para el commit, ver N2 y N3.
- C6: [x] Specs completos, con trazabilidad EARS y R→test en `impl_HU-25.md`.

## Cambios requeridos

Ninguno bloqueante. Opcional, antes del commit: N1 (acotar `desde`), N2 (commit aparte para el arnés) y N3 (borrar
`progress/.init_review_HU-25.log`).
