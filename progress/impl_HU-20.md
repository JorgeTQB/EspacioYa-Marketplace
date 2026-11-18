# Implementación HU-20 · Edición de perfil

- **Dueña:** Adriana Callupe · **Rama:** `feature/HU-20-edicion-perfil` · **Agente:** implementer · **Fecha:** 2026-11-04
- **Spec:** `specs/HU-20/` aprobado el 2026-11-04 (D1–D22 tal como están). Tasks T1–T24 en `[x]`.
- **Estado:** `in_progress` (no se marca `done` hasta que apruebe el reviewer). Sin commit, push ni PR.

## Qué se hizo

- **BD (T1):** `V6_1__perfil_usuario.sql` (V6_1 era el primer número libre del sprint 6 en `develop`): `usuario.correo_pendiente`,
  `foto_ruta`, `foto_tipo_mime`, `ck_usuario_correo_pendiente`, `ck_usuario_foto` y `ck_token_tipo` con `CAMBIO_CORREO`.
- **Dominio (T2–T5):** `TipoToken.CAMBIO_CORREO`, `Token.cambioCorreo` (30 min), `Usuario` (`actualizarDatos`,
  `solicitarCambioCorreo`, `confirmarCambioCorreo`, `cambiarFoto`, `quitarFoto`, `tieneFoto`, `enmascararCorreo`),
  `CampoFacturacion`, `CambioFacturacion`, `PerfilArrendador` (`admiteCambioDeIdentidad`, `cambiosPropuestos`,
  `actualizarFacturacion`, `numeroEnmascarado`), `MotivoRechazoCambioCorreo`.
- **Correo (T6):** dos métodos en `NotificadorCorreo` y `NotificadorCorreoSimulado` (enlace solo con
  `registrar-enlace=true` y nunca con el correo); los 7 dobles de prueba que implementan la interfaz ganan los dos métodos.
- **Servicios (T7–T13):** DTO (`DatosPerfilDTO`, `SolicitudCambioCorreoDTO`, `ConfirmacionCorreoDTO`,
  `ActualizacionFacturacionDTO` con `toString` enmascarado; `PerfilDTO`, `FacturacionDTO`), `ReautenticacionPerfil`
  (`perfil|<id>`, 5/15 min), `PerfilService`, `CambioCorreoService` (3/h por cuenta), `FacturacionService` (bloqueo
  `usuario → perfil_arrendador`).
- **API (T14–T15):** `PerfilController`, `ConfirmacionCorreoController`, `FacturacionController`; `permitAll` solo para
  `POST /api/cambios-correo/confirmaciones`. Todas las respuestas con `no-store`; la foto con `nosniff`.
- **IT (T16–T19):** `PerfilUsuarioMigracionIT`, `PerfilControllerIT`, `CambioCorreoControllerIT`, `FacturacionControllerIT`
  sobre la base común `PerfilSoporte` (reloj controlable y doble de `NotificadorCorreo` que captura enlace y aviso).
- **Frontend (T20–T21):** 8 funciones en `api/cuentas.js`, `refrescar()` en `ProveedorSesion`, `pages/cuentas/Perfil.jsx`
  y `ConfirmarCorreo.jsx` (+ CSS Modules), textos en `textosCuentas.js`, rutas `/panel/perfil` (con `RutaProtegida`) y
  `/confirmar-correo` (pública), enlace "Mi perfil" en `Panel` para `USUARIO` y `ARRENDADOR`. Sin componentes nuevos en el
  kit, sin estados ni colores nuevos, sin dependencias nuevas.
- **Docs (T22):** `docs/modelo-datos.md` (columnas, `token.tipo`, restricciones), `docs/requerimientos.md` (RNF-SEG-17 con
  los dos límites nuevos; nota de D9 en RN-02; no se tocó la matriz §7), `docs/architecture.md` (sección "Perfil: qué deja
  HU-20", métodos nuevos de `NotificadorCorreo`, transición `VERIFICADA → EN_REVISION`), `docs/frontend.md` (rutas
  `/panel/perfil`, `/confirmar-correo` y `refrescar()`). Sin puertos nuevos entre módulos. Sin variables nuevas en
  `.env.example`.

## Resultado de `./init.sh` (completo, sin `--rapido`)

Exit 0, "Entorno listo". Backend `./mvnw verify`: 1437 unitarias y 850 de integración, 0 fallos, 0 errores (incluye las
135 de HU-20). ESLint sin errores ni advertencias, `npm run build` correcto, reglas de arquitectura, colores y secretos en OK.

## Desvíos del spec (menores, sin decisiones nuevas de producto)

1. **`ck_usuario_foto` (design § 8) tenía un hueco de NULL:** con solo una de las dos columnas nula, la rama
   `foto_ruta ~ … AND foto_tipo_mime IN (…)` evalúa a NULL y PostgreSQL deja pasar el CHECK. Se agregó
   `foto_ruta IS NOT NULL AND foto_tipo_mime IS NOT NULL` a esa rama. Lo detectó `PerfilUsuarioMigracionIT`
   (lo que pedía T1: "una foto con ruta sin tipo lo viola"). La migración aún no está en `develop`.
2. **R10 con dos errores a la vez:** el design nombra `ReglaNegocioException(campo, mensaje)`, que admite un solo campo; para
   dar "un error en cada campo faltante" se usó `ValidacionException` (ya existente en `comun/error`, misma forma de
   `ErrorDTO`).
3. **`CambioCorreoService.solicitar`** vuelve a comparar el correo nuevo con el actual dentro de la transacción (además de
   la comparación previa del paso 3) para que una carrera responda el 400 de R23 y no un 500.
4. **Pantalla con `EN_REVISION`:** el `Campo` del kit no tiene `deshabilitado`; en vez de agregar uno al kit, el tipo, el
   número y la razón social se muestran como texto (`<dl>`) y solo la dirección queda como `Campo`. El servidor revalida (R41).
5. **Pantalla, errores del modal:** los errores sin campo (429, en revisión, documento no disponible) se muestran dentro del
   modal de contraseña; los de un campo del formulario lo cierran y se ven bajo su `Campo`.
6. **IT:** las cuatro IT de HU-20 usan `@AutoConfigureMockMvc(print = MockMvcPrint.NONE)` para que la captura de logs de
   R46 (`OutputCaptureExtension`) no vea el volcado de MockMvc (que incluye los cuerpos con la contraseña de prueba).
7. Un correo con espacios en los extremos lo rechaza `@Email` (400 en `correo`), igual que en el registro; la normalización
   de R25 cubre mayúsculas.

## Archivos

**Nuevos (backend main):** `cuentas/controller/{PerfilController,ConfirmacionCorreoController,FacturacionController}.java`,
`cuentas/domain/{CambioFacturacion,CampoFacturacion,MotivoRechazoCambioCorreo}.java`,
`cuentas/dto/{ActualizacionFacturacionDTO,ConfirmacionCorreoDTO,DatosPerfilDTO,FacturacionDTO,PerfilDTO,SolicitudCambioCorreoDTO}.java`,
`cuentas/service/{CambioCorreoService,FacturacionService,PerfilService,ReautenticacionPerfil}.java`,
`resources/db/migration/V6_1__perfil_usuario.sql`.

**Modificados (backend main):** `comun/seguridad/ConfiguracionSeguridad.java`, `cuentas/domain/{PerfilArrendador,TipoToken,Token,Usuario}.java`,
`cuentas/service/{NotificadorCorreo,NotificadorCorreoSimulado}.java`.

**Nuevos (tests):** `cuentas/controller/{PerfilSoporte,PerfilUsuarioMigracionIT,PerfilControllerIT,CambioCorreoControllerIT,FacturacionControllerIT}.java`,
`cuentas/dto/PerfilDTOValidacionTest.java`, `cuentas/service/{ReautenticacionPerfilTest,PerfilServiceTest,CambioCorreoServiceTest,FacturacionServiceTest}.java`.

**Modificados (tests):** `cuentas/domain/{TokenTest,UsuarioTest,PerfilArrendadorTest}.java`, `cuentas/service/NotificadorCorreoSimuladoTest.java`
y los dobles de `NotificadorCorreo` en `cuentas/controller/{UsuarioControllerIT,ArrendadorControllerIT,RecuperacionContrasenaControllerIT}.java`,
`administracion/controller/{CuentaAdminControllerIT,RevisionIdentidadControllerIT}.java`, `reservas/controller/CancelacionReservaControllerIT.java`,
`pagos/controller/PagosSoporte.java` (solo los dos métodos vacíos).

**Frontend:** nuevos `pages/cuentas/{Perfil.jsx,Perfil.module.css,ConfirmarCorreo.jsx,ConfirmarCorreo.module.css}`;
modificados `App.jsx`, `api/cuentas.js`, `pages/cuentas/{Panel.jsx,textosCuentas.js}`, `sesion/ProveedorSesion.jsx`.

**Docs y progreso:** `docs/{architecture,frontend,modelo-datos,requerimientos}.md`, `specs/HU-20/tasks.md` (T1–T24 `[x]`),
`progress/HU-20.md` (bitácora), este archivo.

## Trazabilidad (R1–R52; 135 tests con `@DisplayName("HU-20 R<n>: …")`)

- R1 → `PerfilControllerIT.sinSesion401`
- R2 → `PerfilServiceTest.adminRecibe403`, `CambioCorreoServiceTest.adminRecibe403`, `PerfilControllerIT.admin403`
- R3 → `FacturacionServiceTest.otroRolRecibe403`, `PerfilControllerIT.facturacionDeOtroRol403`, `FacturacionControllerIT.usuario403`
- R4 → `PerfilServiceTest.consultaUsuario`, `PerfilControllerIT.cuerpoNoEligeLaCuenta`
- R5 → `PerfilServiceTest.consultaArrendador`, `PerfilServiceTest.consultaUsuario`, `PerfilControllerIT.consultaArrendador`, `PerfilControllerIT.consultaUsuario`
- R6 → `PerfilArrendadorTest.numeroEnmascarado`, `PerfilServiceTest.consultaArrendador`, `PerfilControllerIT.consultaArrendador`
- R7 → `PerfilControllerIT.consultaArrendador`, `PerfilControllerIT.ca1GuardaYSeReflejaDeInmediato`, `PerfilControllerIT.confirmacionPublica`, `CambioCorreoControllerIT.ca2FlujoCompleto`, `FacturacionControllerIT.ca3CambiaRucYVuelveARevision`
- R8 → `UsuarioTest.actualizarDatosDevuelveLosCamposCambiados`, `PerfilDTOValidacionTest.datosValidos`, `PerfilServiceTest.actualizaDatos`, `PerfilControllerIT.ca1GuardaYSeReflejaDeInmediato`
- R9 → `PerfilDTOValidacionTest.datosInvalidos`, `PerfilControllerIT.datosInvalidos`
- R10 → `PerfilServiceTest.arrendadorConObligatorios`, `PerfilControllerIT.arrendadorSinObligatorios`
- R11 → `UsuarioTest.actualizarDatosBlancoEsNulo`, `PerfilDTOValidacionTest.datosValidos`, `PerfilServiceTest.usuarioBorraDatos`, `PerfilControllerIT.usuarioVaciaDatos`
- R12 → `PerfilServiceTest.datosNoTocanLoDemas`, `PerfilControllerIT.datosSinContrasena`
- R13 → `PerfilServiceTest.guardaFoto`, `PerfilControllerIT.subeYVeFoto`
- R14 → `PerfilServiceTest.rechazaFotos`, `PerfilControllerIT.rechazaFotos`
- R15 → `UsuarioTest.fotoDePerfil`, `PerfilServiceTest.reemplazoBorraAnteriorTrasCommit`, `PerfilServiceTest.falloBorraNueva`, `PerfilServiceTest.quitaFoto`, `PerfilControllerIT.reemplazaYQuita`
- R16 → `PerfilServiceTest.verFoto`, `PerfilServiceTest.fotoSinArchivo`, `PerfilControllerIT.subeYVeFoto`, `PerfilControllerIT.otroNoVeLaFoto`
- R17 → `UsuarioTest.fotoDePerfil`, `PerfilServiceTest.quitaFoto`, `PerfilServiceTest.quitarSinFoto`, `PerfilControllerIT.reemplazaYQuita`, `PerfilControllerIT.quitarSinFoto`
- R18 → `PerfilDTOValidacionTest.correoSinContrasena`, `PerfilDTOValidacionTest.facturacionContrasenaYNumeroOpcional`, `ReautenticacionPerfilTest.sinContrasena`, `CambioCorreoControllerIT.sinContrasena`, `FacturacionControllerIT.sinContrasena`
- R19 → `ReautenticacionPerfilTest.contrasenaIncorrecta`, `CambioCorreoServiceTest.reautenticacionFalla`, `FacturacionServiceTest.reautenticacionFalla`, `CambioCorreoControllerIT.contrasenaIncorrectaYBloqueo`, `FacturacionControllerIT.contrasenaIncorrecta`
- R20 → `ReautenticacionPerfilTest.cincoFallosDan429`, `ReautenticacionPerfilTest.cuatroFallosTodaviaComprueba`, `CambioCorreoServiceTest.reautenticacionFalla`, `FacturacionServiceTest.reautenticacionFalla`, `CambioCorreoControllerIT.contrasenaIncorrectaYBloqueo`, `CambioCorreoControllerIT.cupoCompartido`
- R21 → `ReautenticacionPerfilTest.aciertoLimpia`, `CambioCorreoControllerIT.aciertoReiniciaContador`
- R22 → `PerfilDTOValidacionTest.correoInvalido`, `CambioCorreoControllerIT.correoInvalido`
- R23 → `UsuarioTest.cambioDeCorreoInvalido`, `CambioCorreoServiceTest.mismoCorreo`, `CambioCorreoControllerIT.mismoCorreo`
- R24 → `CambioCorreoServiceTest.cuartaSolicitudDa429`, `CambioCorreoControllerIT.cuartaSolicitud429`
- R25 → `TokenTest.cambioCorreoFijaTipoYVigencia`, `UsuarioTest.cambioDeCorreoEnDosPasos`, `CambioCorreoServiceTest.solicitaCambio`, `PerfilUsuarioMigracionIT.correoPendienteValido`, `PerfilUsuarioMigracionIT.correoPendienteInvalido`, `PerfilUsuarioMigracionIT.tipoDeToken`, `CambioCorreoControllerIT.ca2FlujoCompleto`, `CambioCorreoControllerIT.arrendadorCambiaCorreo`
- R26 → `NotificadorCorreoSimuladoTest.cambioDeCorreoEnlaceSoloEnDesarrollo`, `CambioCorreoServiceTest.enviaCorreosTrasCommit`, `CambioCorreoControllerIT.ca2FlujoCompleto`
- R27 → `NotificadorCorreoSimuladoTest.avisoCambioDeCorreoSinCorreos`, `CambioCorreoServiceTest.enviaCorreosTrasCommit`, `CambioCorreoControllerIT.ca2FlujoCompleto`
- R28 → `CambioCorreoServiceTest.falloDelEnvioNoAfecta`, `CambioCorreoServiceTest.rollbackNoEnvia`, `CambioCorreoControllerIT.falloDelCorreo`, `CambioCorreoControllerIT.rollbackNoEnvia`
- R29 → `CambioCorreoServiceTest.correoEnUso`, `CambioCorreoControllerIT.correoDeOtraCuenta`
- R30 → `CambioCorreoControllerIT.ca2FlujoCompleto`
- R31 → `UsuarioTest.cambioDeCorreoEnDosPasos`, `CambioCorreoServiceTest.confirmaCambio`, `CambioCorreoControllerIT.ca2FlujoCompleto`
- R32 → `TokenTest.cambioCorreoFijaTipoYVigencia`, `UsuarioTest.cambioDeCorreoInvalido`, `PerfilDTOValidacionTest.tokenInvalido`, `CambioCorreoServiceTest.tokenDesconocido`, `CambioCorreoServiceTest.tokenDeOtroTipo`, `CambioCorreoServiceTest.tokenUsado`, `CambioCorreoServiceTest.tokenExpirado`, `CambioCorreoServiceTest.cuentaNoHabilitada`, `CambioCorreoControllerIT.enlacesInvalidos`, `CambioCorreoControllerIT.enlaceVencido`, `CambioCorreoControllerIT.cuentaSuspendida`
- R33 → `CambioCorreoServiceTest.carreraDelToken`, `CambioCorreoServiceTest.correoTomadoAlConfirmar`, `CambioCorreoServiceTest.violacionDeUnicidad`, `PerfilUsuarioMigracionIT.correoPendienteSinUnico`, `CambioCorreoControllerIT.correoTomadoAntesDeConfirmar`, `CambioCorreoControllerIT.confirmacionesSimultaneas`
- R34 → `PerfilControllerIT.confirmacionPublica`, `PerfilControllerIT.tokenEnLaUrlNoConfirma`
- R35 → `PerfilArrendadorTest.tipoSinNumeroYNumeroSinHuella`, `PerfilDTOValidacionTest.facturacionInvalida`, `FacturacionServiceTest.numeroInvalido`, `FacturacionServiceTest.tipoSinNumero`, `FacturacionControllerIT.documentoInvalido`, `FacturacionControllerIT.camposInvalidos`
- R36 → `PerfilArrendadorTest.sinCambios`, `PerfilDTOValidacionTest.facturacionContrasenaYNumeroOpcional`, `FacturacionServiceTest.cambiaDocumentoVerificado`, `FacturacionControllerIT.ca3CambiaRucYVuelveARevision`, `FacturacionControllerIT.soloDireccion`
- R37 → `PerfilArrendadorTest.verificadaConOtroDocumentoVuelveARevision`, `PerfilArrendadorTest.verificadaConOtroTipoVuelveARevision`, `FacturacionServiceTest.cambiaDocumentoVerificado`, `FacturacionControllerIT.ca3CambiaRucYVuelveARevision`
- R38 → `PerfilArrendadorTest.verificadaConOtraRazonSocialVuelveARevision`, `FacturacionServiceTest.cambiaRazonSocialVerificada`, `FacturacionControllerIT.cambiaRazonSocial`
- R39 → `PerfilArrendadorTest.verificadaSoloDireccionSigueVerificada`, `FacturacionServiceTest.cambiaSoloDireccion`, `FacturacionControllerIT.soloDireccion`
- R40 → `PerfilArrendadorTest.noIniciadaYRechazadaConservanEstado`, `FacturacionServiceTest.noIniciadaYRechazada`, `FacturacionControllerIT.noIniciadaYRechazada`
- R41 → `PerfilArrendadorTest.enRevisionNoAdmiteCambioDeIdentidad`, `FacturacionServiceTest.enRevision`, `FacturacionControllerIT.enRevision`
- R42 → `ReautenticacionPerfilTest.registrarFalloCuenta`, `FacturacionServiceTest.documentoDeOtro`, `FacturacionServiceTest.carreraDelDocumento`, `FacturacionControllerIT.documentoDeOtroArrendador`
- R43 → `PerfilArrendadorTest.sinCambios`, `FacturacionServiceTest.sinCambios`, `FacturacionControllerIT.sinCambios`
- R44 → `FacturacionControllerIT.ca3CambiaRucYVuelveARevision`
- R45 → `PerfilUsuarioMigracionIT.restriccionesDeLaFoto`, `PerfilControllerIT.subeYVeFoto`, `FacturacionControllerIT.ca3CambiaRucYVuelveARevision`, `FacturacionControllerIT.cambiaRazonSocial`
- R46 → `NotificadorCorreoSimuladoTest.cambioDeCorreoEnlaceSoloEnDesarrollo`, `NotificadorCorreoSimuladoTest.avisoCambioDeCorreoSinCorreos`, `PerfilDTOValidacionTest.toStringEnmascarado`, `CambioCorreoControllerIT.sinDatosSensibles`, `FacturacionControllerIT.sinDatosSensibles`
- R47 → `PerfilServiceTest.actualizaDatos`, `PerfilServiceTest.sinCambiosNoAudita`, `PerfilControllerIT.ca1GuardaYSeReflejaDeInmediato`
- R48 → `PerfilServiceTest.guardaFoto`, `PerfilServiceTest.quitaFoto`, `PerfilControllerIT.subeYVeFoto`, `PerfilControllerIT.reemplazaYQuita`
- R49 → `UsuarioTest.enmascaraCorreo`, `CambioCorreoServiceTest.correoEnUso`, `CambioCorreoServiceTest.auditaSolicitud`, `CambioCorreoControllerIT.ca2FlujoCompleto`, `CambioCorreoControllerIT.correoDeOtraCuenta`
- R50 → `CambioCorreoServiceTest.confirmaCambio`, `CambioCorreoServiceTest.tokenDesconocido`, `CambioCorreoServiceTest.tokenUsado`, `CambioCorreoServiceTest.tokenExpirado`, `CambioCorreoServiceTest.cuentaNoHabilitada`, `CambioCorreoControllerIT.ca2FlujoCompleto`, `CambioCorreoControllerIT.enlacesInvalidos`
- R51 → `FacturacionServiceTest.cambiaDocumentoVerificado`, `FacturacionServiceTest.documentoDeOtro`, `FacturacionControllerIT.ca3CambiaRucYVuelveARevision`, `FacturacionControllerIT.documentoDeOtroArrendador`
- R52 → `FacturacionServiceTest.cambiaDocumentoVerificado`, `FacturacionControllerIT.dosPutSimultaneos`, `FacturacionControllerIT.facturacionYDecisionDelAdmin`

## Vuelta 1 (review CHANGES_REQUESTED, `progress/review_HU-20.md`)

**Cambio 1: carrera en `CambioCorreoService.confirmar` (R33/R50).** Fuera de la transacción, `comprobar` ahora revisa
solo que la cuenta esté `ACTIVA` (`activa`). Que tenga `correo_pendiente` se revisa **solo dentro de la transacción**,
con la fila de `usuario` bloqueada y después de `marcarUsado`. Así, de dos confirmaciones simultáneas, la que pierde
espera el bloqueo, `marcarUsado` le devuelve 0 y se audita `TOKEN_USADO` (el motivo real) y no `CUENTA_NO_HABILITADA`.
Una cuenta suspendida sigue rechazándose antes de la transacción (`CUENTA_NO_HABILITADA`, sin marcar el token), y una
cuenta sin pendiente sigue dando el 400 genérico (R32), ahora desde la transacción (el token queda marcado como usado,
lo que no cambia nada visible porque ese token ya no puede confirmar nada).

**O1 (orden de bloqueos), resuelto.** `confirmar` toma primero el bloqueo de `usuario` (`findParaActualizar`) y luego
hace `marcarUsado`, el mismo orden `usuario → token` que `solicitar`. Una solicitud nueva y la confirmación de la
anterior de la misma cuenta se ordenan sin deadlock. Como `marcarUsado` limpia el contexto de persistencia, la cuenta se
vuelve a leer con `findParaActualizar` (la fila ya está bloqueada por esta transacción). **Desvío del design § 6**, que
ordenaba `marcarUsado` → `findParaActualizar(usuario)`. No cambia ningún R ni ninguna decisión D1–D22: solo el orden
interno de los bloqueos, como sugirió el reviewer. O2 y O3 no se tocaron (no bloqueantes, no se pidieron).

**Pruebas.**
- `CambioCorreoServiceTest.cuentaNoHabilitada` ahora cubre los dos casos: la suspendida (sin `marcarUsado`) y la cuenta
  sin pendiente (rechazada en la transacción, sin `saveAndFlush`). `carreraDelToken` se ajustó al bloqueo previo. Nueva
  `bloqueaCuentaAntesDelToken` (HU-20 R33, R52): verifica el orden `findParaActualizar` → `marcarUsado` →
  `findParaActualizar`.
- `CambioCorreoControllerIT.confirmacionesSimultaneas` pasó de `@Test` a `@RepeatedTest(10)` (DisplayName "HU-20 R33,
  R50"), con la misma aserción estricta (`motivo=TOKEN_USADO`), no relajada. Corrí la clase 3 veces: 30 de 30
  repeticiones en verde (27/27 tests por corrida).
- `./init.sh` completo (sin `--rapido`): **exit 0**, "Entorno listo". Backend 1438 unitarias + 859 de integración, 0
  fallos, 0 errores; ESLint y `npm run build` OK.

**Trazabilidad añadida:** R33 → `CambioCorreoServiceTest.bloqueaCuentaAntesDelToken`; R50 →
`CambioCorreoControllerIT.confirmacionesSimultaneas`; R52 → `CambioCorreoServiceTest.bloqueaCuentaAntesDelToken`.

**Archivos tocados en la vuelta 1:** `2_Backend/src/main/java/pe/edu/ulima/espacioya/cuentas/service/CambioCorreoService.java`,
`2_Backend/src/test/java/pe/edu/ulima/espacioya/cuentas/service/CambioCorreoServiceTest.java`,
`2_Backend/src/test/java/pe/edu/ulima/espacioya/cuentas/controller/CambioCorreoControllerIT.java`, este archivo y
`progress/HU-20.md`.
