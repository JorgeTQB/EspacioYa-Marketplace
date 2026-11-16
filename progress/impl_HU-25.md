# Implementación HU-25 · Recordatorio de reserva por correo

- **Implementer**, 2026-11-07/08. Rama `feature/HU-25-recordatorio-reserva`. Spec: `specs/HU-25/` (T1–T22 marcadas).
- **Patrones ★:** Factory Method (`comun.correo.FabricaCorreo<E>` / `Correo`; ConcreteCreators y ConcreteProducts en
  `reservas.service.recordatorio` y `cuentas.service.correo`) y Adapter (`comun.correo.EnvioCorreo` →
  `EnvioCorreoSmtp` sobre `JavaMailSender`, `EnvioCorreoSimulado`).
- **Verificación:** ver "Resultado de `./init.sh`" al final.

## Trazabilidad (R1–R37)

- R1 → `PreferenciasCorreoControllerIT.usuarioConsulta`, `PreferenciasCorreoServiceTest.consultaLaPreferencia`
- R2 → `PreferenciasCorreoControllerIT.arrendadorDesactiva`, `PreferenciasCorreoControllerIT.usuarioReactiva`,
  `PreferenciasCorreoServiceTest.actualizaYAudita`, `UsuarioTest.cambiaLosRecordatorios`
- R3 → `PreferenciasCorreoControllerIT.ignoraUsuarioIdDelCuerpo`
- R4 → `PreferenciasCorreoControllerIT.sinCampoDa400` (`{}` y `null`)
- R5 → `PreferenciasCorreoControllerIT.adminRecibe403`, `PreferenciasCorreoServiceTest.adminNoPuede`
- R6 → `PreferenciasCorreoControllerIT.sinSesion401`
- R7 → `PreferenciasCorreoControllerIT.usuarioConsulta` (GET) y `.arrendadorDesactiva` (PUT): `Cache-Control: no-store`
- R8 → `PreferenciasCorreoControllerIT.arrendadorDesactiva`, `PreferenciasCorreoServiceTest.actualizaYAudita`
- R9 → `PreferenciasCorreoControllerIT.mismoValorSinAuditoria`, `PreferenciasCorreoServiceTest.mismoValorSinAuditoria`,
  `UsuarioTest.mismoValorNoCambia`
- R10 → `PreferenciasCorreoControllerIT.cuentaNuevaConRecordatorios`, `RecordatoriosReservaIT.preferenciaPorDefecto`,
  `UsuarioTest.cuentaNuevaConRecordatorios`
- R11 → `RecordatoriosReservaIT.recordatorioAlUsuarioYAlArrendador`,
  `RecordatoriosReservaIT.reservaHechaConMenosDeVeinticuatroHoras` (D2), `RecordatoriosReservaTest.enviaAlUsuarioYAlArrendador`,
  `RecordatoriosReservaTest.reservaConMenosDeVeinticuatroHoras`, `ReglaRecordatorioTest.justoVeinticuatroHorasAntes`,
  `ReglaRecordatorioTest.dosHorasAntes`, `ReglaRecordatorioTest.ultimaFechaEnLima`
- R12 → `RecordatoriosReservaIT.recordatorioAlUsuarioYAlArrendador`, `RecordatoriosReservaTest.enviaAlUsuarioYAlArrendador`
- R13 → `RecordatoriosReservaIT.recordatorioAlUsuarioYAlArrendador` (`recordatorio_enviado_en` = ahora),
  `RecordatoriosReservaIT.restriccionDeLaMarca` (`ck_reserva_recordatorio`), `ReservaTest.marcaElRecordatorio`
- R14 → `RecordatoriosReservaIT.segundaPasadaNoReenvia`, `RecordatoriosReservaTest.yaRecordadaAlReleer`,
  `ReservaTest.admiteRecordatorio`
- R15 → `RecordatoriosReservaIT.fueraDePlazo`, `RecordatoriosReservaTest.todaviaLejos`,
  `ReglaRecordatorioTest.unSegundoAntesDelPlazo`
- R16 → `RecordatoriosReservaIT.fueraDePlazo`, `RecordatoriosReservaTest.yaEmpezo`, `ReglaRecordatorioTest.enElInicio`
- R17 → `RecordatoriosReservaIT.noConfirmadas`, `ReservaTest.admiteRecordatorio`, `ReservaTest.noMarcaSinConfirmar`
- R18 → `RecordatoriosReservaTest.canceladaAlReleer`, `RecordatoriosReservaTest.yaRecordadaAlReleer`
- R19 → `RecordatoriosReservaIT.usuarioDesactivado`, `RecordatoriosReservaTest.usuarioSinPreferencia`,
  `CuentaServiceImplTest.correoParaRecordatorioSinPreferencia`
- R20 → `RecordatoriosReservaIT.arrendadorDesactivado`, `RecordatoriosReservaTest.arrendadorSinPreferencia`
- R21 → `RecordatoriosReservaIT.cuentaSuspendida`, `CuentaServiceImplTest.correoParaRecordatorioNoActiva`,
  `CuentaServiceImplTest.correoParaRecordatorioInexistente`, `RecordatoriosReservaTest.ningunDestinatario`
- R22 → `RecordatoriosReservaTest.auditaConDestinatarios` (+ `usuarioSinPreferencia`, `arrendadorSinPreferencia`,
  `ningunDestinatario`), `RecordatoriosReservaIT.recordatorioAlUsuarioYAlArrendador`
- R23 → `RecordatoriosReservaTest.sinCommitNoEnvia`, `RecordatoriosReservaTest.enviaAlUsuarioYAlArrendador` (nada antes
  del commit)
- R24 → `RecordatoriosReservaTest.fallaUnEnvio` (log sin correos), `RecordatoriosReservaIT.fallaUnEnvio`
- R25 → `RecordatoriosReservaTest.unaFallaNoDetieneLasDemas`
- R26 → `ProgramacionRecordatoriosTest.activo`, `ProgramacionRecordatoriosTest.apagado`, `ProgramacionRecordatoriosTest.delega`
- R27 → `WebhookPagoIT.pagoSinRecordatorios`
- R28 → `CancelacionReservaControllerIT.cancelacionSinRecordatorios`
- R29 → `RecordatoriosReservaIT.contenidoMinimo` (sin últimos 4 sembrados, RUC, montos, correo, teléfono ni nombre de la
  otra parte), `FabricaRecordatorioUsuarioTest.cuerpoMinimo`, `FabricaRecordatorioArrendadorTest.cuerpoMinimo`,
  `FormatoCorreoTest.fecha`, `FormatoCorreoTest.horario`, `FormatoCorreoTest.horaFinVeinticuatro`
- R30 → `MensajeCorreoTest.conservaLosDatos`, `MensajeCorreoTest.rechazaSinDestinatario`, `MensajeCorreoTest.rechazaSinCuerpo`,
  `FabricaCorreoTest.creaElCorreoYLoEntrega`, `FabricaCorreoTest.sinDestinatarioNoEnvia`,
  `EnvioCorreoSmtpTest.unSoloDestinatario` (sin `cc`/`bcc`), `NotificadorCorreoImplTest.cadaMetodoEnviaSuCorreo`,
  `FabricaRecordatorio{Usuario,Arrendador}Test.enviaUnMensajeConAsuntoFijo`
- R31 → `FabricaCorreoTest.asuntoFijo`, `MensajeCorreoTest.rechazaSinAsunto`, `NotificadorCorreoImplTest.asuntoSinDatosDeUsuarios`,
  `NotificadorCorreoImplTest.cadaMetodoEnviaSuCorreo`, `FabricaRecordatorio{Usuario,Arrendador}Test.enviaUnMensajeConAsuntoFijo`
- R32 → `EnvioCorreoSmtpTest.traduceElMensaje`
- R33 → `EnvioCorreoSmtpTest.fallaDelServidor`, `EnvioCorreoSmtpTest.otraMailException`, `FabricaCorreoTest.propagaLaFallaDelTarget`,
  `NotificadorCorreoImplTest.propagaLaFalla`
- R34 → `EnvioCorreoConfiguracionTest.smtp`, `.otroValor`, `.smtpSinRemitente`, `.smtpSinHost`; `EnvioCorreoSmtpTest.sinRemitente`,
  `EnvioCorreoSmtpTest.sinHost`
- R35 → `EnvioCorreoSimuladoTest.soloUnLogSinDatos`, `EnvioCorreoConfiguracionTest.simulado`
- R36 → `NotificadorCorreoImplTest.cadaMetodoEnviaSuCorreo` (parametrizada, 13 métodos con la tabla C1; comprobante con
  solo los últimos 4; aviso de cambio de correo con el enmascarado), `NotificadorCorreoImplTest.comprobanteSinTarjeta`,
  `FormatoCorreoTest.monto`, `FormatoCorreoTest.instanteEnLima`, `NotificadorCorreoSimuladoTest` (sigue verde sin cambios)
- R37 → `DatosPersonalesServiceTest.perfilExportadoConRecordatorios`, `DatosPersonalesServiceTest.perfilExportadoConRecordatoriosActivos`,
  `DatosPersonalesControllerIT.exportaLaPreferenciaDeRecordatorios` (los asserts de HU-22 siguen verdes)

## Cómo se demuestra CA1 en UAT (D12, proveedor `simulado`)

UAT no envía correos reales (`CORREO_PROVEEDOR=simulado`, valor por defecto). Con `RESERVAS_RECORDATORIOS_ACTIVA=true`
(por defecto; para no esperar 5 min se puede fijar `RESERVAS_RECORDATORIOS_INTERVALO=PT30S`):

1. Con un usuario y un arrendador (los dos con "Recordatorios" activo en `/panel/perfil`), reservar un bloque que empiece
   dentro de las próximas 24 h y pagarlo con la pasarela simulada → la reserva queda `CONFIRMADA`.
2. En la siguiente pasada del proceso, el log del backend muestra **dos** líneas de
   `pe.edu.ulima.espacioya.comun.correo.EnvioCorreoSimulado`: "Envío de correo simulado: no se envió ningún correo."
   (una por destinatario; sin correo, asunto ni cuerpo).
3. En la base:
   `SELECT entidad, entidad_id, usuario_id, ip, detalle FROM auditoria WHERE accion = 'RECORDATORIO_RESERVA';`
   → una fila `reserva`, `<id>`, `NULL`, `NULL`, `origen=PROCESO;destinatarios=USUARIO,ARRENDADOR`; y
   `SELECT recordatorio_enviado_en FROM reserva WHERE id = <id>;` → no nulo. Una segunda pasada no agrega filas ni logs.
4. **CA2:** si el usuario desactiva "Recordatorios" antes de la pasada, la fila dice `destinatarios=ARRENDADOR` y el log
   muestra una sola línea; `PREFERENCIA_RECORDATORIOS` queda auditada con `recordatoriosActivos=true->false`.
5. **CA3:** con la preferencia desactivada, al pagar o cancelar siguen apareciendo los logs de
   `NotificadorCorreoSimulado` ("Envío de comprobante de pago simulado…", "Envío de aviso de cancelación…").

Para SMTP real: `docs/architecture.md`, sección "Correo: qué deja HU-25" (variables `CORREO_*`, sin cambios de código).

## Archivos

**Creados (main):**
- `2_Backend/src/main/resources/db/migration/V7_3__recordatorios.sql`
- `comun/correo/`: `MensajeCorreo`, `EnvioCorreo`, `EnvioCorreoSmtp`, `EnvioCorreoSimulado`, `Correo`, `FabricaCorreo`,
  `SinDatos`, `FormatoCorreo`
- `cuentas/dto/PreferenciasCorreoDTO`, `cuentas/service/PreferenciasCorreoService`,
  `cuentas/controller/PreferenciasCorreoController`, `cuentas/service/impl/NotificadorCorreoImpl`
- `cuentas/service/correo/`: 13 `FabricaCorreo*` + 13 `Correo*` (Bienvenida, Recuperacion, IdentidadAprobada,
  IdentidadRechazada, CuentaSuspendida, CuentaReactivada, PagoConfirmado, ReservaPagada, CancelacionUsuario,
  CancelacionArrendador, CambioCuentaBancaria, CambioCorreo, AvisoCambioCorreo) y `QuienCancelo` (texto de quién canceló)
- `reservas/domain/ReglaRecordatorio`
- `reservas/service/recordatorio/`: `RecordatoriosReserva`, `ProgramacionRecordatorios`, `DatosRecordatorio`,
  `CorreoRecordatorioUsuario`, `CorreoRecordatorioArrendador`, `FabricaRecordatorioUsuario`, `FabricaRecordatorioArrendador`
- `3_frontend/src/pages/cuentas/Recordatorios.jsx`, `Recordatorios.module.css`

**Modificados (main):** `pom.xml` (starter mail), `application.properties`, `.env.example`, `cuentas/domain/Usuario`,
`cuentas/service/CuentaService` + `impl/CuentaServiceImpl`, `cuentas/dto/PerfilExportadoDTO`,
`cuentas/service/NotificadorCorreo` (solo Javadoc), `cuentas/service/NotificadorCorreoSimulado`
(`@ConditionalOnProperty`), `reservas/domain/Reserva`, `reservas/repository/ReservaRepository`,
`3_frontend/src/api/cuentas.js`, `pages/cuentas/Perfil.jsx`, `pages/cuentas/textosCuentas.js`.

**Pruebas creadas:** `comun/correo/{MensajeCorreo,FabricaCorreo,FormatoCorreo,EnvioCorreoSmtp,EnvioCorreoSimulado,
EnvioCorreoConfiguracion}Test`, `cuentas/service/PreferenciasCorreoServiceTest`,
`cuentas/controller/PreferenciasCorreoControllerIT`, `cuentas/service/impl/NotificadorCorreoImplTest`,
`reservas/domain/ReglaRecordatorioTest`, `reservas/service/recordatorio/{RecordatoriosReserva,ProgramacionRecordatorios,
FabricaRecordatorioUsuario,FabricaRecordatorioArrendador}Test`, `reservas/service/recordatorio/RecordatoriosReservaIT`.

**Pruebas modificadas (casos nuevos HU-25):** `UsuarioTest`, `CuentaServiceImplTest`, `ReservaTest`,
`DatosPersonalesServiceTest`, `DatosPersonalesControllerIT`, `WebhookPagoIT`, `CancelacionReservaControllerIT`.

**Config de pruebas:** `src/test/resources/config/application.properties` (`recordatorios.activa=false`,
`correo.proveedor=simulado`).

**Docs:** `docs/architecture.md` (puerto `correoParaRecordatorio`, filas de reservas, sección "Correo: qué deja HU-25"
con la activación de SMTP, fila HU-25 de patrones, exportación con `recordatoriosActivos`), `docs/modelo-datos.md`
(`usuario.recordatorios_activos`, `reserva.recordatorio_enviado_en`, `ck_reserva_recordatorio`,
`ix_reserva_por_recordar`), `docs/requerimientos.md` §9 punto 8 resuelto, `docs/frontend.md` (tarjeta "Recordatorios").
No hay puerto nuevo entre módulos ni componentes nuevos del kit.

## Notas y desvíos

- Sin desvíos del spec. Detalles de implementación que el spec dejaba abiertos:
  - Texto de "quién canceló" en los correos de cancelación: `USUARIO` → "quien reservó", `ARRENDADOR` → "el arrendador"
    (`cuentas.service.correo.QuienCancelo`, clase de paquete).
  - El vencimiento de los enlaces se escribe con `Token.VIGENCIA_RECUPERACION` / `VIGENCIA_CAMBIO_CORREO` (30 min).
  - `RecordatoriosReserva` envuelve cada envío para registrar `"No se pudo enviar el recordatorio de la reserva {id} al
    {USUARIO|ARRENDADOR}"` (sin correo, R24), además del log genérico de `DespuesDelCommit`.
- La matriz §7 de `docs/requerimientos.md` no se tocó (la llena la pareja que prueba).

## Resultado de `./init.sh`

`./init.sh` completo (2026-11-08), en verde: unitarias todas (cambió `pom.xml`) **1818** tests, 0 fallos; integración
todas las `*IT` **1067** tests, 0 fallos (incluidas `RecordatoriosReservaIT`, `PreferenciasCorreoControllerIT`,
`DatosPersonalesControllerIT`, `WebhookPagoIT`, `CancelacionReservaControllerIT`); ESLint sin advertencias; build del
frontend OK; chequeos de arquitectura (sin `@Autowired`, sin comodines, sin prints, sin secretos) OK.

Re-corrida tras el review (2026-11-08, B1 y B2): `Recordatorios.jsx` cambió el botón "Guardar" a
`variante="secundario"` (B1, design § 12 / T20; no hay pruebas del frontend que dependan de la variante). Antes de
correr se comprobó que no hubiera otro Maven sobre `2_Backend`: había un `mvnw test` ajeno en curso, se esperó a que
terminara sin matarlo. `./init.sh` completo en verde (B2): unitarias **1818** tests, 0 fallos/errores; integración
**1067** tests, 0 fallos/errores; ESLint sin errores ni advertencias; build del frontend OK; chequeos de arquitectura OK.

## Ajuste posterior: camino C3 (caja blanca cap. 10)

- 2026-11-16. Solo test, sin cambios en código de producción ni en el estado de la historia.
- Agregado `RecordatoriosReservaTest.confirmadaSinHorario`
  (`@DisplayName("HU-25 R15, R16 (C3): una reserva confirmada sin horario no envía, no marca y no audita")`): cubre
  el camino C3 de `RecordatoriosReserva.recordarSiCorresponde()` (reserva CONFIRMADA que admite recordatorio, pero
  `bloques.horarios(List.of(id))` devuelve lista vacía → `horario.isEmpty()` → `false`). Afirma 0 marcadas,
  `recordatorioEnviadoEn` null, sin sincronizaciones ni correos, sin auditoría y sin consultar preferencias de correo.
- `./2_Backend/mvnw -f 2_Backend/pom.xml test -Dtest=RecordatoriosReservaTest`: **14** tests, 0 fallos, 0 errores.
