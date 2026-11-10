# HU-25 · Recordatorio de reserva por correo — Tasks

> Orden pensado para que cada paso compile. Cada task de backend lista los `R<n>` y la prueba que los cubre
> (`@DisplayName("HU-25 R<n>: …")`). Pruebas unitarias: `./2_Backend/mvnw -f 2_Backend/pom.xml test -Dtest=<Clase>Test`;
> integración: `./2_Backend/mvnw -f 2_Backend/pom.xml verify -Dit.test=<Clase>IT` (Docker).

## Base de datos y configuración

- [x] T1 — Confirmar en `develop` que `V7_3` sigue libre y crear `V7_3__recordatorios.sql` (design § 8). Cubre: R10, R13, R14. Prueba: `PreferenciasCorreoControllerIT` (cuenta nueva con `true`) y `RecordatoriosReservaIT`.
- [x] T2 — Agregar `spring-boot-starter-mail` al `pom.xml`; propiedades de design § 9 en `application.properties` (`CORREO_PROVEEDOR` por defecto `simulado`, D12), en `src/test/resources/config/application.properties` (`recordatorios.activa=false`, `proveedor=simulado`) y en `.env.example` (variables de design § 9.1 sin valores, con un comentario de cómo activar SMTP). Cubre: R26, R32, R34, R35.

## `comun/correo` (★ Adapter y Factory Method)

- [x] T3 — Crear `MensajeCorreo`, `EnvioCorreo`, `Correo`, `SinDatos`, `FabricaCorreo<E>` y `FormatoCorreo`. Cubre: R30, R31. Pruebas: `MensajeCorreoTest` (rechaza vacíos), `FabricaCorreoTest` (el Creator llama al método fábrica y entrega un único `MensajeCorreo` con el destinatario, el asunto y el cuerpo del producto), `FormatoCorreoTest`.
- [x] T4 — Crear `EnvioCorreoSmtp` (Adapter sobre `JavaMailSender`) con validación al construir. Cubre: R30, R32, R33, R34. Prueba: `EnvioCorreoSmtpTest` (con `JavaMailSender` de Mockito y `ArgumentCaptor<SimpleMailMessage>`: remitente, un solo `to`, sin `cc`/`bcc`; `MailSendException` → `ServicioExternoException` sin el texto del proveedor; remitente o host vacío → `IllegalStateException`).
- [x] T5 — Crear `EnvioCorreoSimulado`. Cubre: R35. Prueba: `EnvioCorreoSimuladoTest` (`OutputCaptureExtension`: el log contiene "Envío de correo simulado" y no contiene destinatario, asunto ni cuerpo).
- [x] T6 — Selección del proveedor por propiedad. Cubre: R34, R35. Prueba: `EnvioCorreoConfiguracionTest` (`ApplicationContextRunner`: `smtp` → `EnvioCorreoSmtp` y `NotificadorCorreoImpl`; `simulado` → `EnvioCorreoSimulado` y `NotificadorCorreoSimulado`; valor desconocido → sin bean de `EnvioCorreo`).

## `cuentas`: preferencia

- [x] T7 — `Usuario`: campo `recordatoriosActivos`, `tieneRecordatoriosActivos()`, `cambiarRecordatorios(boolean)`. Cubre: R2, R9. Prueba: `UsuarioTest`.
- [x] T8 — `CuentaService.correoParaRecordatorio` en `CuentaServiceImpl`. Cubre: R19, R20, R21. Prueba: `CuentaServiceImplTest` (activa con preferencia → correo; preferencia `false`, `SUSPENDIDA`, `ELIMINADA` o inexistente → vacío).
- [x] T9 — `PreferenciasCorreoDTO`, `PreferenciasCorreoService` y `PreferenciasCorreoController`. Cubre: R1, R2, R3, R4, R5, R6, R7, R8, R9, R10. Pruebas: `PreferenciasCorreoServiceTest` (rol, auditoría solo si cambia) y `PreferenciasCorreoControllerIT` (200 `GET`/`PUT` de `USUARIO` y `ARRENDADOR`, `usuarioId` en el cuerpo ignorado, 400 sin campo o `null`, 403 `ADMIN`, 401, `no-store`, valor por defecto `true`).
- [x] T10 — Exportación de HU-22 (D13): agregar `boolean recordatoriosActivos` a `PerfilExportadoDTO` después de `mfaActivo`, leído en `de(...)` con `cuenta.tieneRecordatoriosActivos()`, y actualizar su Javadoc. `DatosPersonalesService` no cambia. Cubre: R37. Pruebas: `DatosPersonalesServiceTest` (nuevo caso `HU-25 R37`: cuenta con preferencia `false` → `perfil.recordatoriosActivos()` es `false`; con `true` → `true`; los asserts existentes de HU-22 siguen verdes) y `DatosPersonalesControllerIT` (nuevo caso `HU-25 R37`: tras `PUT /preferencias-correo` con `false`, la exportación trae `$.perfil.recordatoriosActivos` = `false` junto a `$.perfil.mfaActivo`).

## `cuentas`: adaptador real de `NotificadorCorreo` (★ Factory Method)

- [x] T11 — Crear en `cuentas/service/correo` los 13 ConcreteCreators y sus ConcreteProducts (design § 7, tabla C1). Cubre: R29, R31, R36. Prueba: `NotificadorCorreoImplTest` (parametrizada: cada método envía un único mensaje al correo recibido con el asunto fijo y los datos de C1; el comprobante lleva solo los últimos 4 y nunca un número completo; el aviso de cambio de correo lleva el enmascarado).
- [x] T12 — Crear `NotificadorCorreoImpl` (`proveedor=smtp`), poner `@ConditionalOnProperty(proveedor=simulado)` a `NotificadorCorreoSimulado` y actualizar el Javadoc de `NotificadorCorreo` (D10). Cubre: R36. Pruebas: `NotificadorCorreoImplTest`, `NotificadorCorreoSimuladoTest` (sigue verde sin cambios).

## `reservas`: recordatorio

- [x] T13 — `ReglaRecordatorio` y `Reserva` (`recordatorioEnviadoEn`, `admiteRecordatorio`, `marcarRecordatorio`). Cubre: R13, R14, R15, R16, R17. Pruebas: `ReglaRecordatorioTest` (bordes: justo 24 h antes entra, 24 h + 1 s no, inicio exacto no, 2 h antes entra — reserva hecha con < 24 h, D2) y `ReservaTest`.
- [x] T14 — `ReservaRepository.findIdsPorRecordar`. Cubre: R11, R14, R17. Prueba: `RecordatoriosReservaIT`.
- [x] T15 — `DatosRecordatorio`, `CorreoRecordatorioUsuario`, `CorreoRecordatorioArrendador`, `FabricaRecordatorioUsuario`, `FabricaRecordatorioArrendador`. Cubre: R29, R30, R31. Pruebas: `FabricaRecordatorioUsuarioTest` y `FabricaRecordatorioArrendadorTest` (asunto de C1; cuerpo con espacio, fecha y horario; sin montos ni otros datos).
- [x] T16 — `RecordatoriosReserva.enviarPendientes()`. Cubre: R11, R12, R13, R14, R15, R16, R17, R18, R19, R20, R21, R22, R23, R24, R25. Pruebas: `RecordatoriosReservaTest` (Mockito + `TransactionSynchronizationManager` como `AvisosCancelacionTest`: relectura bajo bloqueo, preferencia de cada parte, auditoría con `destinatarios=…`, sin commit no envía, falla de un envío no impide el otro, excepción en una reserva no detiene las demás, log sin correo) y `RecordatoriosReservaIT` (PostgreSQL + `RelojControlable` + doble `@Primary` de `EnvioCorreo` que guarda los mensajes: dos mensajes a los correos del usuario y del arrendador de la reserva y a nadie más; reserva confirmada 2 h antes de su inicio también recibe (D2); segunda pasada no reenvía; fuera de plazo, empezada, pendiente, expirada o cancelada no envía; preferencia desactivada; el cuerpo no contiene los últimos 4 del pago sembrado, el documento del arrendador ni el correo o teléfono de la otra parte).
- [x] T17 — `ProgramacionRecordatorios` con su propio `@EnableScheduling`. Cubre: R26. Prueba: `ProgramacionRecordatoriosTest` (`ApplicationContextRunner`: con `activa=true` existe el bean, con `false` no).

## CA3: correos que no se desactivan

- [x] T18 — Pruebas de que pago y cancelación no leen la preferencia. Cubre: R27, R28. Pruebas: `WebhookPagoIT` (usuario y arrendador con `recordatorios_activos=false` → comprobante y aviso de reserva pagada) y `CancelacionReservaControllerIT` (mismos datos → los dos avisos de cancelación).

## Frontend

- [x] T19 — `obtenerPreferenciasCorreo` y `actualizarPreferenciasCorreo` en `3_frontend/src/api/cuentas.js`. Cubre: pantalla Perfil, tarjeta Recordatorios (CA2).
- [x] T20 — `pages/cuentas/Recordatorios.jsx`, textos en `textosCuentas.js` y la tarjeta en `Perfil.jsx` (design § 12; el botón "Guardar" de la tarjeta es `variante="secundario"`, porque `docs/frontend.md` § Botones pide un solo primario por pantalla y el de `/panel/perfil` es "Guardar datos" — B1 del review). Cubre: pantalla Perfil, tarjeta Recordatorios (CA2, CA3 visible). Verificación: `npm --prefix 3_frontend run lint` y `npm --prefix 3_frontend run build`.

## Documentación y verificación

- [x] T21 — Actualizar `docs/architecture.md` (incluida la configuración para activar SMTP real, design § 9.1), `docs/modelo-datos.md`, `docs/requerimientos.md` §9 punto 8 y `docs/frontend.md` según design § 15. Cubre: R26 (D1), R32–R36 (patrones documentados), R37 (D13, si `docs/` lista los campos de la exportación).
- [x] T22 — Mapa R → test en `progress/impl_HU-25.md` (R1–R37) y `./init.sh` en verde (unitarias, IT de la historia —incluidas `DatosPersonalesServiceTest` y `DatosPersonalesControllerIT` de HU-22—, lint y build). Para UAT (D12, proveedor `simulado`), dejar en `progress/impl_HU-25.md` cómo se demuestra CA1: log "Envío de correo simulado" y fila de auditoría `RECORDATORIO_RESERVA`.
