# HU-22 — Tasks: Derechos sobre datos personales

> Orden pensado para que cada paso compile y sus pruebas pasen antes del siguiente. Diseño: `design.md`.
> IP de las IT: `10.22.x.x`. Cada test lleva `@DisplayName("HU-22 R<n>: …")`. Sin migraciones (design § 11).

## Backend — dominio de `cuentas`

- [x] T1 — `Usuario`: `TEXTO_ANONIMIZADO`, `marcadorCorreo(Long)`, `puedeEliminarse()` y `anonimizar(String hashNuevo)`
  (devuelve la ruta de la foto; `IllegalStateException` si no es `USUARIO`/`ARRENDADOR` `ACTIVA`). Prueba
  `UsuarioAnonimizacionTest` (campos nulos, marcador en minúsculas, estado `ELIMINADA`, hash reemplazado, admin y
  suspendida rechazados). Cubre: R21, R22, R23.
- [x] T2 — `PerfilArrendador`: `marcadorHuella(Long)` y `anonimizar()`. Prueba `PerfilArrendadorAnonimizacionTest`.
  Cubre: R25.

## Backend — puerto y caso de uso en `cuentas`

- [x] T3 — Crear `cuentas/service/puertos/ParticipanteDatosPersonales`, `TitularDatos`, `SeccionDatos` e `Impedimento`
  (design § 6.1). Cubre: R45.
- [x] T4 — `ReautenticacionPerfil.Operacion`: agregar `EXPORTACION` y `ELIMINACION`; ampliar
  `ReautenticacionPerfilTest` con el detalle `operacion=EXPORTACION` y `operacion=ELIMINACION`. Cubre: R17.
- [x] T5 — Repositorios: `DesafioMfaRepository.borrarDeUsuario` y `CodigoRespaldoRepository.borrarDeUsuario`
  (`@Modifying`, `flushAutomatically`). Cubre: R30.
- [x] T6 — DTO `ConfirmacionContrasenaDTO` (con `toString` enmascarado), `ExportacionDatosDTO`, `PerfilExportadoDTO`,
  `ConsentimientoDTO`, `FacturacionExportadaDTO` (número con `numeroEnmascarado()`). Cubre: R3, R4, R13.
- [x] T7 — `DatosPersonalesService` con `exportar` y `eliminar` según design § 6.2 (orden DT-5, hash fuera de la
  transacción DT-6, `revocarTodas` al final DT-11, archivos con `DespuesDelCommit` DT-10, auditorías § 10). Cubre: R1–R4,
  R14–R16, R18–R31, R34, R35, R40, R42, R45.
- [x] T8 — `DatosPersonalesServiceTest` (Mockito, `Clock.fixed`, dos participantes falsos): un test por R1, R2, R3, R4,
  R14, R15, R16, R18, R19 (sin llamar a `ReautenticacionPerfil` ni a repositorios), R20 (contraseña incorrecta no
  consulta impedimentos), R21, R22, R23 (hash calculado antes de `transacciones`), R24 (archivo no se borra si hay
  rollback), R25, R26, R28 (`revocarTodas` último, `InOrder`), R29 (los tres tipos), R30, R34, R35 (participante que
  lanza → sin auditoría `CUENTA_ELIMINADA`), R40 (impedimentos de los dos participantes juntos y sin `anonimizar`), R42
  (`findParaActualizar` antes de `impedimentos`, `InOrder`) y R45. Cubre: R1–R4, R14–R16, R18–R26, R28–R30, R34, R35,
  R40, R42, R45.
- [x] T9 — `DatosPersonalesController`: `POST /api/usuarios/actual/exportaciones` (200 con `Content-Disposition`
  `attachment; filename="espacioya-mis-datos-<AAAA-MM-DD>.json"` con `ZonaHoraria.hoy(reloj)`, `application/json`,
  `no-store`) y `POST /api/usuarios/actual/eliminacion` (200 `MensajeDTO`, `no-store`); IP con `IpCliente.de`. Cubre:
  R1, R2, R10, R11, R21.

## Backend — `pagos`

- [x] T10 — `PagoService.pagosDeReservas(Collection<Long>)` con `PagoDeReserva` y `ReembolsoDePago`;
  `PagoRepository.findByReservaIdInOrderByCreadoEnAscIdAsc` y `ReembolsoRepository.findByPagoIdInOrderByCreadoEnAscIdAsc`;
  implementación en `PagoServiceImpl` (`readOnly`, colección vacía → `List.of()` sin consultar, sin `ultimos4` ni id de
  pasarela). Ampliar `PagoServiceImplTest`. Cubre: R6, R13.
- [x] T11 — `LiquidacionRepository.findByArrendadorIdOrderByGeneradaEnDescIdDesc`, `CuentaBancariaExportadaDTO` y
  `DatosPersonalesPagosImpl` (`@Component`, `@Order(2)`) según design § 6.4, con `MENSAJE_LIQUIDACIONES`,
  `MENSAJE_COBROS_POR_LIQUIDAR`, `CAMPO_LIQUIDACIONES`, `CAMPO_PAGOS`. Cubre: R8, R9, R27, R38, R39, R44.
- [x] T12 — `DatosPersonalesPagosImplTest`: usuario sin secciones ni impedimentos; liquidaciones en orden; cuenta
  bancaria enmascarada y `null` sin cuenta; liquidación `GENERADA` y `PENDIENTE_CUENTA` → impedimento; `PAGADA` no;
  cobro por liquidar de otro arrendador no cuenta, uno no `admitida` tampoco; `bloquearGeneracion` antes de leer
  (`InOrder`); `anonimizar` borra la cuenta bancaria y no falla sin ella; `incluirSimulados` según la propiedad. Cubre:
  R8, R9, R13, R27, R38, R39, R44.

## Backend — `reservas`

- [x] T13 — `ReservaExportadaDTO`, `PagoExportadoDTO`, `ReembolsoExportadoDTO` y `DatosPersonalesReservasImpl`
  (`@Component`, `@Order(1)`) según design § 6.3, con `MENSAJE_RESERVAS_USUARIO`, `MENSAJE_RESERVAS_ARRENDADOR` y
  `CAMPO_RESERVAS`. Cubre: R5, R6, R7, R12, R13, R36, R37, R41, R43.
- [x] T14 — `DatosPersonalesReservasImplTest`: exportación del usuario (todas sus reservas y estado leído, pagos de
  `pagosDeReservas`), del arrendador (solo `CONFIRMADA`, sin datos del usuario); impedimento del usuario con
  `PENDIENTE_PAGO` vigente, `CONFIRMADA` futura y en curso; sin impedimento con `EXPIRADA`, `CANCELADA`, vencida y
  `CONFIRMADA` terminada; impedimento del arrendador con un bloque que termina después de ahora; `bloquearEspacio`
  antes de `bloquesReservados` en cada espacio, por id ascendente (`InOrder`); arrendador sin espacios → sin
  impedimentos. Cubre: R5, R6, R7, R12, R13, R36, R37, R41, R43.

## Backend — integración

- [x] T15 — `DatosPersonalesSoporte` (siembra con `JdbcTemplate`, `RelojControlable`, doble `@Primary` de
  `NotificadorCorreo`, participante de prueba que lanza solo si el test lo activa, `limpiar` en orden FK) y
  `DatosPersonalesControllerIT`: exportación de usuario y de arrendador (claves, encabezados, fila `DATOS_EXPORTADOS`);
  datos de otra cuenta ausentes; cuerpo sin hash, documento completo, número de cuenta, `ultimos4`, id de pasarela ni
  datos de quien reservó; contraseña vacía, incorrecta (y su auditoría), 429 tras 5 fallos (también en eliminación) y
  `ADMIN` 403 en ambas rutas; eliminación de usuario y de arrendador con verificación fila por fila de `usuario`,
  `perfil_arrendador`, `documento_identidad`, `cuenta_bancaria`, `sesion`, `token`, `mfa`, `codigo_respaldo`,
  `desafio_mfa`, archivos borrados y registros contables intactos; token anterior → 401; inicio de sesión anterior →
  401 genérico; registro nuevo con el mismo correo y documento → aceptado; cada impedimento (R36–R39), los tres a la
  vez en orden, reserva pasada/cancelada/expirada no bloquea; contraseña incorrecta con impedimentos → solo
  `contrasena`; rollback con el participante que lanza. Cubre: R1, R2, R10–R13, R15–R41.

## Frontend

- [x] T16 — `api/cuentas.js`: `exportarMisDatos({ contrasena })` y `eliminarMiCuenta({ contrasena })` (design § 14).
  Cubre: pantalla "Mis datos personales" (CA1, CA2, CA3).
- [x] T17 — `pages/cuentas/DatosPersonales.jsx` + `DatosPersonales.module.css` y textos en `textosCuentas.js`: tarjeta,
  modal de descarga con `Blob` y `<a download>` (CA1), modal `peligro-confirmar` de eliminación con `borrarToken()`,
  `salir()` y navegación a `/iniciar-sesion` con el mensaje (CA2), `Alerta atencion` con los impedimentos y sus enlaces
  (CA3), errores de `contrasena`/429 dentro del modal. Cubre: pantalla "Mis datos personales" (CA1, CA2, CA3).
- [x] T18 — `pages/cuentas/Perfil.jsx`: renderizar `<DatosPersonales />` como quinta `Tarjeta`. Cubre: pantalla
  `/panel/perfil` (CA1, CA2, CA3).

## Documentación

- [x] T19 — `docs/architecture.md`: fila del puerto `cuentas.ParticipanteDatosPersonales` (contrato de las tres
  operaciones, `MANDATORY`, implementadores `reservas` y `pagos`; `busqueda` lo sumará HU-24), `PagoService.pagosDeReservas`
  y subsección "Datos personales: qué deja HU-22" (marcadores, qué se borra, qué se conserva, orden de bloqueos
  `usuario → espacio` y `usuario → advisory 190019 → cuenta_bancaria`). `docs/frontend.md`: viñeta de `/panel/perfil`
  con la tarjeta "Mis datos personales". `docs/modelo-datos.md`: marcadores de una cuenta `ELIMINADA` en `usuario` y
  `perfil_arrendador`, y que HU-22 borra la fila de `cuenta_bancaria`. Cubre: R45 (documentación del puerto).

## Verificación

- [x] T20 — `./init.sh` en verde (unitarias, IT con Testcontainers, ESLint y build del frontend) y mapa
  `R<n>` → test en `progress/impl_HU-22.md`. Cubre: R1–R45.
