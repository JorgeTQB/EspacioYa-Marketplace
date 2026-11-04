# Review — HU-20

**Veredicto:** APPROVED (tras la re-revisión de la vuelta 1; el veredicto inicial fue CHANGES_REQUESTED)

Motivo único y bloqueante: `./init.sh` completo (sin `--rapido`), ejecutado por el revisor, terminó con **exit 1**. Falló
`CambioCorreoControllerIT.confirmacionesSimultaneas` (R33). Al volver a correr solo esa clase pasó (18/18), así que la
prueba es **intermitente**: tiene una carrera real en `CambioCorreoService` (ver "Cambios requeridos"). Todo lo demás
(trazabilidad, tasks, STRIDE, docs, arquitectura, frontend) está bien y no pide cambios. ESLint y `npm run build` OK.

```
CambioCorreoControllerIT.confirmacionesSimultaneas:462
Expecting actual: ["motivo=CUENTA_NO_HABILITADA"]  to contain exactly: ["motivo=TOKEN_USADO"]
```

## Trazabilidad requirements <-> tests

Cada `R<n>` tiene al menos un `@DisplayName("HU-20 R<n>…")` que comprueba un resultado concreto (código HTTP, campo de
`ErrorDTO`, fila en BD, detalle de auditoría, correo capturado por el doble, bytes en disco). Lo revisé contra el código de
las pruebas, no solo contra el mapa de `progress/impl_HU-20.md`.
- R1: [x] `PerfilControllerIT.sinSesion401` (las 7 rutas dan 401; la fila, la auditoría y los tokens no cambian)
- R2: [x] `PerfilControllerIT.admin403`, `PerfilServiceTest`, `CambioCorreoServiceTest` (sin `PERFIL_CONTRASENA_FALLIDA`)
- R3: [x] `PerfilControllerIT.facturacionDeOtroRol403` (6 intentos por rol, ningún fallo contado), `FacturacionControllerIT.usuario403`, `FacturacionServiceTest`
- R4: [x] `PerfilControllerIT.cuerpoNoEligeLaCuenta` (id/rol/correo/estado en el cuerpo se ignoran; la otra cuenta queda igual)
- R5: [x] `PerfilControllerIT.consultaArrendador`, `consultaUsuario`, `PerfilServiceTest`
- R6: [x] `PerfilControllerIT.consultaArrendador` (el cuerpo no trae el RUC, la huella, `$2a$`, `perfiles/` ni tokens), `PerfilArrendadorTest.numeroEnmascarado`
- R7: [x] IT con `Cache-Control: no-store` en GET/PUT perfil, foto, 202 de correo, 200/400 de confirmación y PUT facturación
- R8: [x] `PerfilControllerIT.ca1GuardaYSeReflejaDeInmediato` (PUT y GET inmediato con el mismo valor, recortado), `UsuarioTest`, `PerfilServiceTest`
- R9: [x] `PerfilControllerIT.datosInvalidos` (2 errores, nada guardado), `PerfilDTOValidacionTest`
- R10: [x] `PerfilControllerIT.arrendadorSinObligatorios` (2 errores), `PerfilServiceTest`
- R11: [x] `PerfilControllerIT.usuarioVaciaDatos`, `UsuarioTest`, `PerfilServiceTest`
- R12: [x] `PerfilControllerIT.datosSinContrasena`, `PerfilServiceTest.datosNoTocanLoDemas`
- R13: [x] `PerfilControllerIT.subeYVeFoto` (JPG con EXIF: se sirve sin EXIF; en disco cifrado y se descifra a lo servido)
- R14: [x] `PerfilControllerIT.rechazaFotos` (PDF renombrado, texto, ilegible, vacío, >5 MiB, sin parte; la foto vigente y la carpeta no cambian)
- R15: [x] `PerfilControllerIT.reemplazaYQuita` (archivo anterior borrado), `PerfilServiceTest.falloBorraNueva` (rollback: borra el nuevo, nunca el anterior)
- R16: [x] `PerfilControllerIT.subeYVeFoto` (tipo y `nosniff`), `otroNoVeLaFoto` (404), `PerfilServiceTest.fotoSinArchivo`
- R17: [x] `PerfilControllerIT.reemplazaYQuita`, `quitarSinFoto` (no audita)
- R18: [x] `CambioCorreoControllerIT.sinContrasena`, `FacturacionControllerIT.sinContrasena`, `ReautenticacionPerfilTest`, `PerfilDTOValidacionTest`
- R19: [x] `CambioCorreoControllerIT.contrasenaIncorrectaYBloqueo` (auditoría `operacion=CORREO` con usuario e IP), `FacturacionControllerIT.contrasenaIncorrecta` (`operacion=FACTURACION`)
- R20: [x] `CambioCorreoControllerIT.contrasenaIncorrectaYBloqueo` (el 6.º con la correcta da 429; vence la ventana), `cupoCompartido` (3+2 → 429 en las dos rutas), `ReautenticacionPerfilTest`
- R21: [x] `CambioCorreoControllerIT.aciertoReiniciaContador`, `ReautenticacionPerfilTest.aciertoLimpia`
- R22: [x] `CambioCorreoControllerIT.correoInvalido` (sin fallo contado ni token), `PerfilDTOValidacionTest`
- R23: [x] `CambioCorreoControllerIT.mismoCorreo` (mayúsculas; sin pendiente, token ni correos), `CambioCorreoServiceTest`
- R24: [x] `CambioCorreoControllerIT.cuartaSolicitud429` (pendiente = 3.º, 3 tokens, sin correos; pasa al vencer la hora)
- R25: [x] `CambioCorreoControllerIT.ca2FlujoCompleto` (pendiente normalizado, correo intacto, solo el hash), `arrendadorCambiaCorreo`, `TokenTest`, `PerfilUsuarioMigracionIT`
- R26: [x] `CambioCorreoControllerIT.ca2FlujoCompleto` (enlace `…/confirmar-correo#token=` al correo nuevo), `CambioCorreoServiceTest.enviaCorreosTrasCommit`
- R27: [x] `CambioCorreoControllerIT.ca2FlujoCompleto` y `correoDeOtraCuenta` (aviso al actual con `j***@correo.pe`)
- R28: [x] `CambioCorreoControllerIT.falloDelCorreo`, `rollbackNoEnvia`; `CambioCorreoServiceTest.falloDelEnvioNoAfecta`, `rollbackNoEnvia`
- R29: [x] `CambioCorreoControllerIT.correoDeOtraCuenta` (cuerpo idéntico, pendiente guardado, tokens anulados, sin enlace, con aviso)
- R30: [x] `CambioCorreoControllerIT.ca2FlujoCompleto` (login con el pendiente 401, con el actual 201)
- R31: [x] `CambioCorreoControllerIT.ca2FlujoCompleto`, `CambioCorreoServiceTest.confirmaCambio`
- R32: [x] `CambioCorreoControllerIT.enlacesInvalidos`, `enlaceVencido`, `cuentaSuspendida`; `CambioCorreoServiceTest` (un test por motivo)
- R33: [x] cubierto (`confirmacionesSimultaneas`, `correoTomadoAntesDeConfirmar`, `CambioCorreoServiceTest.violacionDeUnicidad`), **pero `confirmacionesSimultaneas` es intermitente** (ver cambio 1)
- R34: [x] `PerfilControllerIT.confirmacionPublica` (400, nunca 401), `tokenEnLaUrlNoConfirma` (GET/query no confirman)
- R35: [x] `FacturacionControllerIT.documentoInvalido` (sin fallo contado), `camposInvalidos`, `FacturacionServiceTest`, `PerfilArrendadorTest`
- R36: [x] `FacturacionControllerIT.ca3CambiaRucYVuelveARevision`, `soloDireccion` (número vacío conserva la huella)
- R37: [x] `FacturacionControllerIT.ca3CambiaRucYVuelveARevision`, `PerfilArrendadorTest` (número y tipo)
- R38: [x] `FacturacionControllerIT.cambiaRazonSocial`, `PerfilArrendadorTest`, `FacturacionServiceTest`
- R39: [x] `FacturacionControllerIT.soloDireccion` (sigue `VERIFICADA`, sin aviso en la sesión)
- R40: [x] `FacturacionControllerIT.noIniciadaYRechazada` (estado y `motivo_rechazo` intactos)
- R41: [x] `FacturacionControllerIT.enRevision` (documento y razón social: 400 sin cambios; dirección sí)
- R42: [x] `FacturacionControllerIT.documentoDeOtroArrendador` (400 genérico, sin revelar la otra cuenta, cuenta un fallo: 4 más → 429), `FacturacionServiceTest.carreraDelDocumento`
- R43: [x] `FacturacionControllerIT.sinCambios` (fila igual, sin auditoría), `FacturacionServiceTest`
- R44: [x] `FacturacionControllerIT.ca3CambiaRucYVuelveARevision` (`/api/sesiones/actual` con el aviso; `habilitacionArrendador().identidadVerificada() == false`)
- R45: [x] `FacturacionControllerIT.ca3…`/`cambiaRazonSocial` (columnas `v1:` sin el valor en claro), `PerfilControllerIT.subeYVeFoto` (foto cifrada en disco), `PerfilUsuarioMigracionIT`
- R46: [x] `CambioCorreoControllerIT.sinDatosSensibles` y `FacturacionControllerIT.sinDatosSensibles` (logs con `OutputCaptureExtension` + auditoría), `PerfilDTOValidacionTest.toStringEnmascarado`, `NotificadorCorreoSimuladoTest`
- R47: [x] `PerfilControllerIT.ca1GuardaYSeReflejaDeInmediato` (`campos=nombre,telefono`; sin cambios no audita)
- R48: [x] `PerfilControllerIT.subeYVeFoto` (`mime=…;tamano=…`), `reemplazaYQuita` (`FOTO_PERFIL_ELIMINADA`)
- R49: [x] `CambioCorreoControllerIT.ca2FlujoCompleto` (`nuevo=j***@correo.pe`), `correoDeOtraCuenta` (`motivo=CORREO_EN_USO;nuevo=…`)
- R50: [x] `CambioCorreoControllerIT.ca2FlujoCompleto` (`anterior=…;nuevo=…`), `enlacesInvalidos`, `enlaceVencido`, `cuentaSuspendida`, `correoTomadoAntesDeConfirmar`
- R51: [x] `FacturacionControllerIT.ca3…` (`campos=numeroDocumento;identidad=VERIFICADA->EN_REVISION` sobre `perfil_arrendador`), `documentoDeOtroArrendador` (`motivo=DOCUMENTO_EN_USO`)
- R52: [x] `FacturacionControllerIT.dosPutSimultaneos` (un ganador, el otro R41, una sola auditoría), `facturacionYDecisionDelAdmin` (los dos terminan, resultado en serie)

## Trazabilidad con la historia
- CA1 (cambios visibles de inmediato): [x] R5, R8–R17, R36, R43, implementados y probados
- CA2 (el correo nuevo solo se activa tras verificarlo): [x] R22–R26, R29–R34
- CA2 (el anterior recibe un aviso): [x] R27–R29
- CA3 (verificado que cambia RUC/DNI → en revisión): [x] R37–R42, R44
- (E) Solo se edita el perfil de la sesión: [x] R1–R4. Ninguna ruta tiene id; `PerfilService.exigirTitular` y `FacturacionService` validan el rol en el servidor; la cuenta siempre es `usuario.usuarioId()`
- (S) Correo o facturación exigen reingresar la contraseña: [x] R18–R21. `ReautenticacionPerfil.exigir` usa `CuentaService.confirmarContrasena` con la clave `perfil|<id>` (5/15 min), que comparten las dos rutas
- (R/T) Registro de cambios sensibles: [x] R19, R47–R51. Los detalles llevan solo nombres de campo, estados y correos enmascarados (verificado en IT)
- Docs pedidos por el spec (T22): [x] `docs/modelo-datos.md` (columnas `correo_pendiente`, `foto_ruta`, `foto_tipo_mime` en lugar de `foto_url`; `CAMBIO_CORREO` de 30 min; restricciones de `V6_1`), [x] `docs/requerimientos.md` RNF-SEG-17 (contraseña de perfil 5/15 min por cuenta; cambio de correo 3 por cuenta/hora; HU-20 en el origen) y la nota de D9 en RN-02, [x] `docs/architecture.md` (sección "Perfil: qué deja HU-20", transición `VERIFICADA → EN_REVISION`), [x] `docs/frontend.md` (rutas y `refrescar()`)

## Desvíos declarados por el implementer (`progress/impl_HU-20.md`)
Los 7 son aceptables. Ninguno contradice D1–D22:
1. `ck_usuario_foto` con `IS NOT NULL` explícito: corrige un hueco real del CHECK del design (NULL deja pasar), refuerza R45 y la IT lo prueba. `V6_1` todavía no está en `develop`, así que no se edita una migración ya subida. Aceptable.
2. `ValidacionException` en lugar de `ReglaNegocioException` para R10: hace falta para devolver un error por campo, con la misma forma de `ErrorDTO`. Aceptable.
3. Repetir en la transacción la comparación con el correo actual: es defensivo y convierte una carrera en el 400 de R23. Aceptable.
4. Con `EN_REVISION`, documento y razón social se muestran como texto (`<dl>`) y no se agrega `deshabilitado` al kit. Coincide con el design § 12 ("solo la dirección queda editable") y evita tocar el kit. Aceptable.
5. Los errores sin campo se muestran en el modal y los de campo lo cierran. Coincide con el design § 12 (429 en el modal; errores por campo bajo su `Campo`). Aceptable.
6. `MockMvcPrint.NONE` en las IT: solo afecta a las pruebas y hace falta para que R46 sea verificable. Aceptable.
7. Un correo con espacios en los extremos da 400 por `@Email`: es coherente con el registro (HU-01) y no viola R22/R23 (la respuesta sigue siendo 400 en `correo`). Aceptable.

## Tasks completas
- T1–T24: [x] (todas `[x]` en `specs/HU-20/tasks.md`). T24 dice "`./init.sh` en verde", pero en el review no se reprodujo de forma estable (ver cambio 1).

## Checkpoints
- C1: [ ] Archivos, docs, historias y subagentes presentes [x]; **`./init.sh` sin `--rapido` terminó con exit 1** [ ]
- C2: [x] Una sola `in_progress` por dueño; existe `progress/HU-20.md` con la aprobación de Adriana Callupe del 2026-11-04; describe solo la sesión activa. "Toda historia done con tests": n/a para HU-20 (sigue `in_progress`)
- C3: [x] Paquetes en `cuentas`/`comun`; ningún controller importa un repository; inyección por constructor, sin Lombok ni comodines; sin `println`/`console.log`; sin puertos nuevos ni dependencias entre módulos (D20, el admin no toca `pagos`/`espacios`); DTO `record` y ninguna entidad expuesta; identidad desde la sesión y estado de identidad calculado en el servidor; migración `V6_1__perfil_usuario.sql`, que no edita ninguna anterior; correo detrás de `NotificadorCorreo` y archivos en `ArchivosPrivados`; nada fuera del alcance; sin secretos; frontend solo con tokens, el kit, un único primario por pantalla y llamadas solo desde `src/api/`
- C4: [ ] Nombres `*Test`/`*IT` con `TestcontainersConfiguration`, sin H2, `Clock` controlable (30 min, 15 min, 1 h) y concurrencia lanzada de verdad [x]; pruebas con resultados concretos [x]; **`mvnw verify` no quedó todo verde: `confirmacionesSimultaneas` falla de forma intermitente** [ ]; lint y build OK [x]
- C5: [x] Sin archivos sospechosos sin trackear; trabajo en `feature/HU-20-edicion-perfil`; `feature_list.json` en `in_progress`. La entrada de `history.md` y el regreso de `progress/HU-20.md` a la plantilla son del cierre: n/a todavía
- C6: [x] `specs/HU-20/` completo; EARS con tabla de trazabilidad que cubre CA1–CA3 y E/S/R-T; design con endpoints, clases, migración, secuencias y alternativas (sin ★); mapa R↔test en `progress/impl_HU-20.md`; ningún `R<n>` resuelve un pendiente de §9

## Cambios requeridos
1. **Quitar la carrera de `CambioCorreoService.confirmar` que deja intermitente R33/R50**
   (`2_Backend/src/main/java/pe/edu/ulima/espacioya/cuentas/service/CambioCorreoService.java`, `comprobar`, líneas
   172–193, y `habilitada`, línea 195).
   **Causa:** `comprobar` corre fuera de la transacción y lee el token y la cuenta en dos consultas. Con dos
   confirmaciones a la vez, B lee el token todavía sin usar; A confirma y hace commit (deja `correo_pendiente = null`);
   recién entonces B lee la cuenta con `findById`, `habilitada` falla porque ya no hay pendiente y audita
   `CUENTA_NO_HABILITADA` en lugar de `TOKEN_USADO`. El resultado para el usuario cumple R33 (un solo cambio y un 400
   genérico), pero el motivo auditado no es la causa real (R50) y
   `CambioCorreoControllerIT.confirmacionesSimultaneas` (línea 462) falla de forma intermitente: en el review falló en
   `./init.sh` y pasó al correr solo la clase.
   **Arreglo sugerido:** fuera de la transacción, revisar solo que la cuenta esté `ACTIVA`, y comprobar
   `correoPendiente != null` únicamente dentro de la transacción, después de `marcarUsado` (que ya da `TOKEN_USADO` al
   que pierde) y del bloqueo de `usuario`. Ese chequeo ya existe en la línea 145. No basta con relajar la aserción de la
   prueba. Después, correr `./init.sh` completo en verde y, si se puede, la clase IT varias veces.
   Hay que mantener `CambioCorreoServiceTest.cuentaNoHabilitada` y `CambioCorreoControllerIT.cuentaSuspendida` (cuenta
   suspendida → `CUENTA_NO_HABILITADA`) y que una cuenta sin pendiente siga dando 400 genérico (R32).

## Observaciones no bloqueantes
- O1 (R52/STRIDE T, fuera del texto de R52): `solicitar` bloquea `usuario` y luego actualiza `token` (`anularPendientes`), y `confirmar` hace lo contrario (`marcarUsado` y después `findParaActualizar(usuario)`), tal como dice el design § 6. Si llegan a la vez una solicitud nueva y la confirmación de la anterior de la misma cuenta, PostgreSQL puede detectar un deadlock y abortar una con 500 (el estado final queda consistente). El arreglo del cambio 1 es buena ocasión para tomar primero el bloqueo de `usuario` en `confirmar` (y entonces `marcarUsado`).
- O2 (R35): un `numeroDocumento` inválido junto con otro campo inválido de Bean Validation solo devuelve los errores de Bean Validation, porque `ValidadorDocumento` corre después, en el servicio. Es el orden que fija el design § 4, pero "un error por cada campo inválido" se cumple por etapas.
- O3: en `FacturacionService.actualizar` (líneas 83–86), cualquier `DataIntegrityViolationException` de la transacción, no solo la de `uq_perfil_arrendador_hash_documento`, se trata como "documento en uso" y cuenta un fallo. Convendría filtrar por el nombre de la restricción.

## Re-revisión (vuelta 1)

**Veredicto:** APPROVED

- **Cambio requerido 1 (carrera en R33/R50): resuelto.** En `CambioCorreoService.comprobar` el chequeo fuera de la
  transacción ahora es solo `activa` (cuenta `ACTIVA`). `correo_pendiente != null` se revisa dentro de la transacción,
  con `usuario` bloqueado y después de `marcarUsado`. De dos confirmaciones simultáneas, la segunda espera el bloqueo de
  `usuario`; cuando A hace commit, su `marcarUsado` devuelve 0 → `TOKEN_USADO`, de forma determinista. Si `comprobar` de
  B corre después del commit de A, el token ya figura usado → `TOKEN_USADO`. Una cuenta suspendida sigue dando
  `CUENTA_NO_HABILITADA` antes de la transacción, sin consumir el token (`CambioCorreoServiceTest.cuentaNoHabilitada`,
  `CambioCorreoControllerIT.cuentaSuspendida`).
- **Cambio de orden de bloqueos (O1, desvío del design § 6): aceptable.** `confirmar` hace ahora
  `findParaActualizar(usuario)` → `marcarUsado` → se relee la cuenta (ya bloqueada). Es el mismo orden
  `usuario → token` que `solicitar`, así que desaparece el posible deadlock entre una solicitud nueva y la confirmación de
  la anterior. No rompe ningún R:
  - R31: sigue verde.
  - R32: una cuenta sin pendiente da el 400 genérico sin modificar ninguna cuenta. El token queda marcado como usado,
    pero no es visible y ese token ya no podía confirmar nada.
  - R33: queda reforzado.
  - R50: el motivo auditado es ahora el real.
  - R52: no se toca el orden `usuario → perfil_arrendador`.

  Tampoco contradice ninguna de D1–D22: D4 sigue sin revocar sesiones, D21 sigue con la confirmación pública y D3 sigue
  revisando otra vez la unicidad (`CORREO_EN_USO` con rollback). Queda documentado como desvío en
  `progress/impl_HU-20.md` § "Vuelta 1". La nueva `CambioCorreoServiceTest.bloqueaCuentaAntesDelToken` verifica el
  orden con `inOrder`.
- **La aserción no se relajó.** `CambioCorreoControllerIT.confirmacionesSimultaneas` (líneas 438–465) mantiene
  `containsExactlyInAnyOrder(true, false)`, un solo `CORREO_CAMBIADO` y `motivosDeRechazo()` `containsExactly("motivo=TOKEN_USADO")`.
  Además pasó de `@Test` a `@RepeatedTest(10)`, lo que la endurece.
- **`./init.sh` completo (sin `--rapido`) ejecutado por el revisor: exit 0, "Entorno listo".** Backend: 1438 unitarias y
  859 de integración, 0 fallos y 0 errores. `CambioCorreoControllerIT` dio 27/27, con las 10 repeticiones en verde.
  ESLint y build OK. Todas las reglas de arquitectura, colores y secretos en OK.
- **Checkpoints actualizados:** C1 [x] (init exit 0), C4 [x] (`mvnw verify` todo verde). C2, C3, C5 y C6 quedan como
  en la primera revisión, sin cambios. No queda ningún cambio requerido.
- **Observaciones no bloqueantes que siguen abiertas** (decide la dueña si las atiende después):
  - O2: R35 con validación en dos etapas.
  - O3: el `catch` de `DataIntegrityViolationException` en `FacturacionService` no filtra por restricción.
