# Review — HU-15

**Veredicto:** APPROVED

Historia: HU-15 · Cancelación de reservas (★ Strategy) · dueño Kevin Chan · rama `feature/HU-15-cancelacion-reservas`
(cambios sin commitear sobre `develop` 9a4ab53). Revisado el 2026-10-31.

`./init.sh` completo (sin `--rapido`): **exit 0**, `[OK] Entorno listo`. Backend `./mvnw verify`: **954 unitarias** y
**660 IT** (Testcontainers/PostgreSQL), 0 fallos, 0 errores, 0 omitidas. ESLint sin errores ni advertencias; `npm run build`
compila. Único `[WARN]`: faltan `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA`, `ALMACENAMIENTO_LLAVE_FIRMA` en el entorno local
(preexistente, solo afecta a `spring-boot:run`, no a las pruebas).

## Trazabilidad requirements ↔ tests

Abreviaturas: `CRIT` = `CancelacionReservaControllerIT`, `CRST` = `CancelacionReservaServiceTest`. Todos llevan
`@DisplayName("HU-15 R<n>: …")` y comprueban un resultado concreto (código HTTP, mensaje, campos del DTO, filas de BD).

- R1: [x] `CRIT.sinSesion` (las 4 rutas → 401 *sesión inválida*, fila sigue `CONFIRMADA`)
- R2: [x] `CRIT.rolAjenoEnLaRutaDelUsuario` (ARRENDADOR y ADMIN, con/sin cuerpo → 403), `CRST.rolAjenoEsAccesoDenegado` (sin tocar repositorios)
- R3: [x] `CRIT.rolAjenoEnLaRutaDelArrendador`, `CRST.rolAjenoEsAccesoDenegado`
- R4: [x] `CRIT.idMalEscrito` (`abc`, `0`, `-1`, `1.5`, 20 dígitos × 4 rutas → 404), `ConversionReservasTest` (2), `CRST` id mal escrito
- R5: [x] `CRIT.ajenaDelUsuario` (otra y inexistente → 404, `sinCambios`, bloques activos), `CRST` (2 casos)
- R6: [x] `CRIT.ajenaDelArrendador`, `CRST` ajena del arrendador
- R7: [x] `CRIT.ignoraLoQueNoEsElMontoVisto` (cuerpo con `porcentaje`, `montoReembolso`, `politica`, `canceladaPor`, `usuarioId` → 200 con `USUARIO`, 0 %, `usuario_id` intacto)
- R8: [x] `ReservaTest` (2), `CRIT.yaCancelada` (GET/POST × 2 rutas → 400), `CRST` vista previa y confirmación
- R9: [x] `ReservaTest`, `CRIT.noConfirmadas` (vigente, vencida sin procesar, `EXPIRADA`), `CRST.confirmarPendiente`
- R10: [x] `ReservaTest.confirmadaYaIniciada` (inicio = ahora y pasado), `CRIT.yaIniciada`, `CRST.confirmarYaIniciada`
- R11: [x] `CalculoReembolsoTest` (48 h exactas, 10 días), `CRIT.limiteDeCuarentaYOchoHoras`, `CRIT.dePuntaAPunta`
- R12: [x] `CalculoReembolsoTest` (48 h − 1 s, 1 min), `CRIT.limiteDeCuarentaYOchoHoras` (+1 s → 0 %), `CRIT.ceroPorCientoSinReembolso`, `CRST.ceroPorCientoNoReembolsa`
- R13: [x] `CalculoReembolsoTest` (1 h y 10 días), `CRIT.arrendadorCancelaAlCien`, `CRST` (2)
- R14: [x] `CalculoReembolsoTest.anticipacionEsInicioMenosAhora`, `ReservaConHorarioTest` (Lima UTC−5 y medianoche), `CRIT.limiteDeCuarentaYOchoHoras`
- R15: [x] `CalculoReembolsoTest` (rejilla anticipaciones × `QuienCancela` → exactamente una; vacía y dos → `IllegalStateException`)
- R16: [x] `CalculoReembolsoTest.montoConEscalaDos`
- R17: [x] `CRIT.vistaPreviaDelUsuario` (claves exactas), `CRIT.vistaPreviaNoEscribe` (usuario y arrendador), `CRST` (2)
- R18: [x] `CRIT.vistaPreviaNoEscribe` (foto de `reserva`, `reserva_bloque`, `auditoria`, `pago` igual), `CRST.vistaPreviaNoEscribe`
- R19: [x] `CRIT.confirmaYGuarda` (claves exactas de `CancelacionDTO`), `ReservaTest`, `CRST.usuarioCancelaConAnticipacion`
- R20: [x] `CRIT.confirmaYGuarda` (`cancelada_por`, `cancelada_en` = reloj, `monto_reembolso` en la fila), `ReservaTest.cancelarGuardaLosTresDatos`
- R21: [x] `CRIT.liberaLosBloques` (antes 400 horario no disponible; tras cancelar, otro usuario → 201), `CRIT.dePuntaAPunta`
- R22: [x] `CRST.disparaElReembolso` (id, monto, sesión, IP), `CRIT.solicitaElReembolso`
- R23: [x] `CRST.ceroPorCientoNoReembolsa` (`verifyNoInteractions(pagos)`), `CRIT.ceroPorCientoSinReembolso`
- R24: [x] `PagoServiceImplTest` (audita con entidad `pago` y detalle; no cambia estado ni guarda; sin `PasarelaPago`; `MANDATORY`; monto 0 y mayor que el pago), `CRIT.solicitaElReembolso` (`pago.estado` sigue `PAGADO`, 1 fila de pago)
- R25: [x] `PagoServiceImplTest.sinPagoPagado`, `CRIT.sinPagoPagadoEsError` (500 genérico, `sinCambios`, bloques activos)
- R26: [x] `AvisosCancelacionTest`, `CRIT.enviaLosDosCorreos` (record exacto), `CRST.programaLosAvisos`, `NotificadorCorreoSimuladoTest`
- R27: [x] `AvisosCancelacionTest` (sin montos), `CRIT.enviaLosDosCorreos`, `NotificadorCorreoSimuladoTest`
- R28: [x] `AvisosCancelacionTest.unFalloNoImpideElOtro`, `CRIT.unCorreoFallaYLaCancelacionSigue`
- R29: [x] `AvisosCancelacionTest` (nada antes del commit), `CRIT.sinPagoPagadoEsError`, `CRIT.montoCambio` y todo caso con `sinCambios` (sin avisos)
- R30: [x] `CRIT.ignoraLoQueNoEsElMontoVisto`, `CRST.vistaPreviaDelUsuario`
- R31: [x] `CRIT.montoVistoInvalido` (sin cuerpo, objeto vacío, `-1`, `1.001`), `CRIT.montoVistoSinDecimales`, `CRST` (2)
- R32: [x] `CRIT.montoCambio` (161.00 visto vs 0.00 recalculado → 400 en el campo, sin cambios), `CRST.montoCambio`
- R33: [x] `CRST.ordenDeLosBloqueos` (`InOrder`: proyección → `bloquearEspacio` → `findParaActualizar` → `reloj.instant()`), `CancelacionConcurrenciaIT`
- R34: [x] `CancelacionConcurrenciaIT.dosConfirmacionesALaVez` (5 rondas usuario vs arrendador: un 200 y un 400 *ya cancelada*, 1 `RESERVA_CANCELADA`, 1 `REEMBOLSO_SOLICITADO`)
- R35: [x] `CRIT.auditaLaCancelacion` (usuario, entidad, id, IP, fecha del reloj, detalle exacto), `CRST.auditaLaCancelacion`
- R36: [x] helper `sinCambios` en 10 casos de error de `CRIT` (403, 404, 400, 500) + `CRST` (4)
- R37: [x] `CRIT.despuesDeCancelar` (`GET /api/reservas/{id}` y `pasadas` → `CANCELADA`; no en `proximas`)
- R38: [x] `CRIT.despuesDeCancelar`, `CRIT.arrendadorCancelaAlCien` (fuera de `proximas` y `pasadas` del arrendador)
- R39: [x] `CRIT.restriccionesDeCancelacion` (datos con `CONFIRMADA`, solo `cancelada_en`, `cancelada_por` = OTRO, −0.01, mayor que `monto_total` → `DataIntegrityViolationException` con el nombre de la restricción; `CANCELADA` sin datos se acepta)

Sin tests huérfanos relevantes: todos los `@DisplayName("HU-15 …")` citan un R<n> existente.

## Trazabilidad con la historia

- CA1 (48 h o más → 100 %; menos de 48 h → 0 %; arrendador → 100 %): [x] R11–R16, R23 — 3 estrategias + `CalculoReembolso`.
- CA2 (veo el monto antes de confirmar): [x] R17, R18, R8–R10 — vista previa `GET …/cancelacion` + modal de `CancelarReserva.jsx`.
- CA3 (pasa a cancelada, bloques liberados, se dispara el reembolso, correos): [x] R19–R29, R37, R38.
- (E) Solo el usuario de la reserva o el arrendador del espacio: [x] R1–R7 — `exigirRol` antes de leer nada; propiedad con `findByIdAndUsuarioId` / `nombresDelArrendador` (vista previa) y `reserva.usuarioId` / `EspacioBloqueado.arrendadorId` bajo bloqueo (confirmación); 403 por rol y 404 por ajena, como fija el spec.
- (T) Monto calculado en servidor: [x] R7, R30–R34 — `SolicitudCancelacion` solo con datos del servidor; `montoReembolsoVisto` solo se usa en la comparación de `CancelacionReservaService.java:132`; el monto guardado y reembolsado es siempre `reembolso.monto()`, recalculado bajo bloqueo.
- (R) Registro de quién, cuándo y monto: [x] R20, R35, R36, R39 — columnas `cancelada_por`, `cancelada_en`, `monto_reembolso` + auditoría `RESERVA_CANCELADA` con detalle; restricciones `CHECK` en BD.
- Simplificación "política fija": [x] R15 — `CalculoReembolso.politicaFija()`, sin beans ni propiedades.

## Tasks completas

- T1: [x] `V4_2__cancelacion_reserva.sql`, idéntica a design § 9.
- T2: [x] `QuienCancela`, `MotivoNoCancelable`, `Reserva` (3 campos, `motivoNoCancelable`, `cancelar`), `ReservaConHorario.instanteDeInicio`.
- T3: [x] Strategy completo (ver abajo).
- T4: [x] `PagoRepository.findByReservaIdAndEstado`, `PagoService`, `ReembolsoPorCancelacion`, `PagoServiceImpl` (`MANDATORY`).
- T5: [x] records + 2 métodos en `NotificadorCorreo` y `NotificadorCorreoSimulado`; los 6 dobles con 10 líneas agregadas y 0 borradas cada uno.
- T6: [x] `ConversionReservas.idReserva`; `ReservaServiceImpl.idValido` eliminado; tests de HU-12 intactos y verdes.
- T7: [x] 3 DTO `record` con los campos exactos de design § 5.
- T8: [x] `AvisosCancelacion` con un `DespuesDelCommit` por aviso.
- T9: [x] `CancelacionReservaService` con el orden de design § 7.
- T10: [x] 4 endpoints, `@RequestBody(required = false)` sin `@Valid`, IP con `IpCliente.de`.
- T11–T12: [x] `CRIT` (33 casos) con notificador falso, IP `10.17.x.x`, punta a punta con la pasarela simulada.
- T13: [x] `CancelacionConcurrenciaIT`.
- T14: [x] `Modal.textoCerrar` (por defecto "Cancelar").
- T15: [x] 4 funciones en `api/reservas.js` con `encodeURIComponent`; bloque de textos HU-15.
- T16: [x] `CancelarReserva.jsx` + `.module.css` (solo tokens).
- T17: [x] botón en Próximas de ambas pantallas, `recarga` como `key`, `Alerta exito`; se quitó el comentario de HU-14 sobre "Cancelar". (Sin `paraArrendador`: desviación 1, aceptada.)
- T18: [x] `architecture.md`, `modelo-datos.md`, `frontend.md` actualizados; tabla de puertos sin cambios.
- T19: [x] avisos a Ariel Enriquez, Jorge Quispe y Sebastian Garate en `progress/HU-15.md`.
- T20: [x] `./init.sh` verde (confirmado por este revisor) + `progress/impl_HU-15.md`.

## Patrón ★ Strategy

Conforme a design § 2: `PoliticaReembolso` (Strategy, `monto` por defecto con escala 2 y `HALF_UP`), tres
ConcreteStrategy excluyentes y exhaustivas, `CalculoReembolso` (Context: filtra por `aplicaA` y exige exactamente una) y
`CancelacionReservaService` (Client: arma `SolicitudCancelacion` y llama a `calcular`). Clases puras, sin Spring.
Fuera de las estrategias no hay ningún `if`/`switch` sobre la regla de reembolso: los `switch (quien)` de
`CancelacionReservaService.java:89-92` y `:167-170` solo resuelven la **propiedad** (de quién debe ser la reserva) y el de
`:186-190` traduce `MotivoNoCancelable` a su mensaje. El único `if` sobre el monto (`:138`, `signum` positivo) es D6
(disparar o no el reembolso), no la elección de la política.

## Límite con HU-16

Respetado: `PagoServiceImpl` no inyecta ni llama a `PasarelaPago` (lo verifica también `PagoServiceImplTest`), no cambia
`pago.estado` (`CRIT.solicitaElReembolso` comprueba `PAGADO` y una sola fila de pago) y no hay tabla `reembolso` (la
migración solo altera `reserva`). El contrato queda en el javadoc de `PagoService`, en `docs/architecture.md` y en el
aviso a Ariel Enriquez.

## Desviaciones declaradas (impl, sección "Desviaciones y notas")

1. `CancelarReserva` sin prop `paraArrendador`: **aceptable**. El único texto que cambia por papel es la línea del
   reembolso, y la elige la `politica` de la API (`ARRENDADOR` solo sale de la ruta del arrendador). Un prop sin uso lo
   marcaría ESLint. Sin efecto visible.
2. 48 h exactas en la IT con el reloj llevado a la próxima hora en punto: **aceptable**. Prueba el mismo borde
   (100 % → +1 s → 0 %) con `RelojControlable`, sin hora real ni esperas (C4). La justificación (reloj compartido que
   solo avanza) es correcta.
3. R10 "inicio = ahora" en la unitaria y "ayer" en la IT: **aceptable**; el borde exacto queda en
   `ReservaTest.confirmadaYaIniciada`.
4. `switch (quien)` en el servicio solo para propiedad y mensajes: **aceptable**; está en design § 7 (pasos 3 y 7) y no
   decide la política (ver sección Strategy).
5. `IllegalStateException` si `findParaActualizar` no encuentra la reserva: **aceptable**; las reservas no se borran y
   sigue el precedente de `ReservasPorCobrarImpl.bloquear`.
6. Dobles con métodos vacíos: **no es desviación**; DT-9 pide justo "los dos métodos vacíos".

## Checkpoints

- C1: [x] archivos base, docs, historias y 4 subagentes presentes; `./init.sh` sin `--rapido` con exit 0.
- C2: [x] una sola `in_progress` (HU-15); `progress/HU-15.md` existe; aprobación del spec anotada ("Kevin: aprobado",
  2026-10-31; sesión conducida por Jorge Quispe en su nombre); `progress/HU-15.md` solo describe esta sesión.
  "Toda `done` tiene tests": n/a para HU-15 (sigue `in_progress`).
- C3: [x] paquetes en `reservas`, `pagos`, `cuentas`; ningún controller importa un repository; inyección por
  constructor; sin Lombok, comodines ni prints. `reservas` usa `pagos` vía `PagoService` y `espacios`/`cuentas` vía sus
  servicios; sin puerto nuevo. DTO `record` en la API, sin entidades ni datos sensibles (aviso al arrendador sin datos
  del usuario ni montos). Identidad desde la sesión; monto y estado en el servidor. Migración `V4_2` nueva, ninguna
  subida editada. Correo detrás de `NotificadorCorreo`. Nada fuera de alcance. Sin secretos. Frontend: solo tokens, kit
  (`Boton`, `Modal`, `Alerta`, `Tarjeta`), errores y éxitos según `docs/frontend.md`, API solo desde `src/api/`;
  "Cancelar reserva" es `peligro`, no primario. Coincide con design § 12 Pantallas.
- C4: [x] `<Clase>Test` / `<Clase>IT` con Testcontainers (sin H2); 48 h con `Clock` fijo y `RelojControlable`; IT de
  endpoints, migración y restricciones; concurrencia lanzando las dos operaciones a la vez; resultados concretos en
  todos los caminos de error; `mvnw verify` con 954 + 660 en verde; lint y build OK.
- C5: [x] sin archivos sospechosos sin trackear (solo código, specs y progress de HU-15); rama
  `feature/HU-15-cancelacion-reservas`; `feature_list.json` en `in_progress` (correcto hasta el cierre). Entrada en
  `progress/history.md` y vuelta de `progress/HU-15.md` a la plantilla: n/a todavía (se hacen al cerrar la historia).
- C6: [x] `specs/HU-15/` completo; EARS con tabla de trazabilidad que cubre CA1–CA3 y E/T/R; design con endpoints,
  clases, migración, secuencias, Strategy y 11 alternativas descartadas; tasks 20/20 en `[x]`; cada R<n> con test,
  documentado en `progress/impl_HU-15.md`; D1–D12 decididos por el dueño al aprobar.

## Cambios requeridos (si aplica)

Ninguno bloqueante.

Observaciones menores (opcionales, no condicionan la aprobación):
1. `3_frontend/src/pages/reservas/CancelarReserva.jsx` (dentro del `dl` del modal): las etiquetas "Fecha" y "Horario"
   son literales en el JSX, mientras "Espacio" y "Monto pagado" usan constantes de `textosReservas.js`. Para ser
   coherente con el bloque "Solo textos" de HU-15, se pueden mover a `ETIQUETA_FECHA` y `ETIQUETA_HORARIO`.
2. Al cerrar: el dueño hace commit y PR; el orquestador pasa el resumen a `progress/history.md` y devuelve
   `progress/HU-15.md` a la plantilla (C5).
