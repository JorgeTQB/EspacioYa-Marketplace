# Implementación HU-18 — Registro de cuenta bancaria del arrendador

- **Agente:** implementer · **Fecha:** 2026-11-03 · **Dueño:** Ariel Enriquez
- **Estado:** T1–T19 hechas (`./init.sh` completo en verde). Aprobada por el reviewer; status `done`.

## Verificación

| Comprobación | Resultado |
|--------------|-----------|
| `mvnw test` (JDK 25, 1243 pruebas unitarias del backend, incluye las nuevas de HU-18) | verde |
| `CuentaBancariaRepositoryIT`, `CuentaBancariaControllerIT` (y todas las IT: 755) | verde |
| `npm run lint`, `npm run build` (frontend) | verde |
| `./init.sh` completo (JDK 25 con `JAVA_HOME` y `PATH` exportados en el comando) | verde |

## Mapa R → test

Unitarias: `CuentaBancariaTest` (CBT), `GuardarCuentaBancariaDTOTest` (DTO), `CuentaBancariaServiceTest` (SVC),
`CuentaServiceImplTest` (CSI), `NotificadorCorreoSimuladoTest` (NCS). Integración (**sin ejecutar**):
`CuentaBancariaControllerIT` (IT) y `CuentaBancariaRepositoryIT` (RIT).

| R | Test |
|---|------|
| R1 | IT.sinSesion |
| R2 | SVC.consultarOtroRol, SVC.guardarOtroRol; IT.otroRol |
| R3 | SVC.sinIdentidadVerificada, SVC.identidadRevisadaBajoElBloqueo; IT.ca3SinIdentidad |
| R4 | SVC.usaElIdDeLaSesion; IT.soloLaPropiaCuenta |
| R5 | SVC.consultarSinCuenta, SVC.consultarConCuenta; IT.ca1RegistraYConsulta |
| R6 | DTO.contrasenaEnBlanco, DTO.contrasenaNula; IT.sinContrasena |
| R7 | SVC.contrasenaIncorrecta; CSI.confirmarContrasenaCompara, CSI.confirmarContrasenaSinCuenta; IT.contrasenaIncorrectaYBloqueo |
| R8 | SVC.cincoFallosBloquean, SVC.ventanaVence; IT.contrasenaIncorrectaYBloqueo |
| R9 | SVC.aciertoReiniciaElContador; IT.aciertoReinicia |
| R10 | DTO.bancoCorto, DTO.bancoLargo, DTO.numeroInvalido, DTO.numeroEnLosLimites, DTO.sinDeclaracion, DTO.variosErrores; IT.camposInvalidos |
| R11 | SVC.registra; CSI.razonSocialDelPerfil, CSI.razonSocialSinPerfil; IT.ca1RegistraYConsulta |
| R12 | CBT.cambiarRetiene72Horas, CBT.cambiarReiniciaLaRetencion; SVC.cambia; IT.ca2Cambia |
| R13 | SVC.avisaTrasElCommit; IT.ca2Cambia |
| R14 | SVC.registroSinCorreo; IT.ca1RegistraYConsulta |
| R15 | SVC.falloDelEnvioNoRevierte, SVC.sinCorreoConRollback; IT.falloDelCorreoNoRevierte |
| R16 | CBT.esLaMisma; SVC.mismaCuenta; IT.mismaCuenta |
| R17 | CBT.registrarSinRetencion; SVC.registra; RIT.primeraCuentaSinRetencion; IT.ca1RegistraYConsulta |
| R18 | CBT.estadoSegunElReloj; SVC.consultarRetenidaYDisponible; IT.retenidaYLuegoDisponible |
| R19 | CBT.enmascara; DTO.respuestasSoloEnmascaradas; SVC.consultarConCuenta; IT.ca1RegistraYConsulta |
| R20 | CBT.numeroConConvertidorCifrado; RIT.numeroCifradoEnReposo; IT.ca1RegistraYConsulta |
| R21 | IT.ca1RegistraYConsulta (`Cache-Control: no-store` en GET y PUT) |
| R22 | DTO.toStringSinSecretos; NCS.cambioDeCuentaSinCorreoNiDatos; SVC.auditaElRegistro, SVC.auditaElCambio; IT.ca2Cambia |
| R23 | SVC.auditaElRegistro; IT.ca1RegistraYConsulta |
| R24 | SVC.auditaElCambio; IT.ca2Cambia |
| R25 | RIT.unaCuentaPorArrendador, RIT.restriccionesDeLaTabla, RIT.exigeElPerfil; SVC.bloqueaEnOrden; IT.dosPutSimultaneos |

Las IT ya se ejecutaron y pasan, por lo que R1, R21 y R25 quedan demostradas de extremo a extremo.

## Archivos

**Nuevos (backend, main):** `db/migration/V5_2__cuenta_bancaria.sql`; `pagos/domain/{CuentaBancaria,EstadoCuentaBancaria}`;
`pagos/repository/CuentaBancariaRepository`; `pagos/dto/{GuardarCuentaBancariaDTO,CuentaBancariaDTO,MiCuentaBancariaDTO}`;
`pagos/service/CuentaBancariaService`; `pagos/controller/CuentaBancariaController`;
`cuentas/service/AvisoCambioCuentaBancaria`.

**Modificados (backend, main):** `cuentas/service/{CuentaService,NotificadorCorreo,NotificadorCorreoSimulado}`;
`cuentas/service/impl/CuentaServiceImpl` (nuevo parámetro `PasswordEncoder` en el constructor).

**Nuevos (tests):** `pagos/domain/CuentaBancariaTest`; `pagos/dto/GuardarCuentaBancariaDTOTest`;
`pagos/service/CuentaBancariaServiceTest`; `pagos/controller/{CuentaBancariaControllerIT,CuentaBancariaRepositoryIT}`.

**Modificados (tests):** `cuentas/service/impl/CuentaServiceImplTest`; `cuentas/service/NotificadorCorreoSimuladoTest`;
`pagos/controller/PagosSoporte` (método nuevo del doble, lista `cambiosCuenta`, y `DELETE FROM cuenta_bancaria` antes de
`perfil_arrendador` en la limpieza); los dobles de `NotificadorCorreo` de `RevisionIdentidadControllerIT`,
`CuentaAdminControllerIT`, `ArrendadorControllerIT`, `UsuarioControllerIT`, `RecuperacionContrasenaControllerIT` y
`CancelacionReservaControllerIT`.

**Frontend:** nuevos `pages/pagos/CuentaBancaria.jsx` y `CuentaBancaria.module.css`; modificados `api/pagos.js`,
`pages/pagos/textosPagos.js`, `components/ui/EtiquetaEstado.jsx`, `pages/cuentas/Panel.jsx`, `App.jsx`.

**Docs:** `docs/modelo-datos.md`, `docs/architecture.md`, `docs/frontend.md`; `specs/HU-18/tasks.md` (T1–T18 marcadas).

## Tasks no completadas

- **T19** (`./init.sh` completo): en verde el 2026-11-03 (JDK 25, Docker activo): backend 1243 unitarias + 755 integración sin fallos, ESLint y build del frontend OK.
- Ninguna pendiente: lint, build e IT de T3, T11, T12 y T13–T16 ejecutados en verde.

## Notas de diseño aplicadas

- `.env.example` no existe en el repositorio y HU-18 no agrega variables: sin cambios.
- Se respeta DT-7: rol → identidad → límite → contraseña fuera de la transacción; luego `bloquearCuenta` → releer identidad
  → `findParaActualizar`. El rol se compara con una constante `"ARRENDADOR"` en `pagos` para no importar `cuentas.domain`.
