# HU-20 — Tasks: Edición de perfil

> Orden de ejecución. Cada task de backend lista los `R<n>` que cubre y su test; las de frontend, la pantalla o el CA
> visible (se verifican con lint, build y la caja negra en UAT). Cada test lleva `@DisplayName("HU-20 R<n>: …")`.
> Antes de empezar, la dueña (Adriana Callupe) aprueba o cambia las decisiones D1–D22 de `design.md` § 1 (las ⚠
> primero). Si cambia alguna, se ajustan aquí las tasks y en `requirements.md` los `R<n>` citados.

## Base de datos y dominio

- [x] T1 — Crear `V6_1__perfil_usuario.sql` (revisar en `develop` el siguiente `n` libre del sprint 6): columnas
  `correo_pendiente`, `foto_ruta`, `foto_tipo_mime`, `ck_usuario_correo_pendiente`, `ck_usuario_foto` y `ck_token_tipo`
  con `CAMBIO_CORREO`. Cubre: R25, R45, R52. Test: `PerfilUsuarioMigracionIT` (un `correo_pendiente` sin normalizar o
  igual al correo viola el `CHECK`; una foto con ruta sin tipo lo viola; un token `CAMBIO_CORREO` se inserta).
- [x] T2 — Agregar `CAMBIO_CORREO` a `TipoToken` y `Token.cambioCorreo` con `VIGENCIA_CAMBIO_CORREO` de 30 min.
  Cubre: R25, R32. Test: `TokenTest`.
- [x] T3 — Agregar a `Usuario` los campos nuevos y `actualizarDatos` (blanco → null, devuelve los campos cambiados),
  `solicitarCambioCorreo`, `confirmarCambioCorreo`, `cambiarFoto`, `quitarFoto`, `tieneFoto` y `enmascararCorreo`.
  Cubre: R8, R11, R15, R17, R25, R31, R49. Test: `UsuarioTest`.
- [x] T4 — Crear `CampoFacturacion`, `CambioFacturacion` y agregar a `PerfilArrendador` `admiteCambioDeIdentidad`,
  `cambiosPropuestos`, `actualizarFacturacion` (VERIFICADA + documento o razón social → EN_REVISION; dirección sola no;
  NO_INICIADA y RECHAZADA sin cambio de estado; número `null` conserva) y `numeroEnmascarado`. Cubre: R36, R37, R38,
  R39, R40, R41, R43. Test: `PerfilArrendadorTest`.
- [x] T5 — Crear `MotivoRechazoCambioCorreo`. Cubre: R32, R50. Test: `CambioCorreoServiceTest`.

## Correo

- [x] T6 — Agregar `notificarCambioCorreo(correoNuevo, enlace)` y `notificarAvisoCambioCorreo(correoActual,
  correoNuevoEnmascarado)` a `NotificadorCorreo` y `NotificadorCorreoSimulado` (el enlace solo con
  `registrar-enlace=true` y nunca con el correo; el aviso sin correos en el log); actualizar los dobles de prueba que
  implementan la interfaz. Cubre: R26, R27, R46. Test: `NotificadorCorreoSimuladoTest`.

## Servicios

- [x] T7 — Crear los DTO `DatosPerfilDTO`, `SolicitudCambioCorreoDTO`, `ConfirmacionCorreoDTO`,
  `ActualizacionFacturacionDTO` (validación y `toString` enmascarado) y `PerfilDTO`, `FacturacionDTO`. Cubre: R6, R9,
  R18, R22, R35, R46. Test: `PerfilDTOValidacionTest` (Bean Validation por campo y `toString` de cada DTO de entrada).
- [x] T8 — Crear `ReautenticacionPerfil` (`exigir` con `CuentaService.confirmarContrasena`, `LimitadorIntentos` clave
  `perfil|<id>` 5/15 min, auditoría `PERFIL_CONTRASENA_FALLIDA` con `operacion`; `registrarFallo`). Cubre: R19, R20,
  R21. Test: `ReautenticacionPerfilTest` (429 sin llamar a `confirmarContrasena`; fallo cuenta y audita; acierto limpia).
- [x] T9 — Crear `PerfilService.consultar` y `actualizarDatos` (ADMIN 403; obligatorios por rol; bloqueo `usuario`;
  auditoría solo de campos cambiados). Cubre: R2, R4, R5, R6, R8, R9, R10, R11, R12, R47. Test: `PerfilServiceTest`.
- [x] T10 — Crear `PerfilService.guardarFoto`, `foto` y `quitarFoto` (`ValidadorArchivo` + rechazo de PDF,
  `ArchivosPrivados` carpeta `perfiles`, borrar el anterior tras el commit y el nuevo si la transacción falla).
  Cubre: R13, R14, R15, R16, R17, R48. Test: `PerfilServiceTest`.
- [x] T11 — Crear `CambioCorreoService.solicitar` (orden de § 6: rol, contraseña, mismo correo, límite 3/h, correo en
  uso con respuesta idéntica, token, auditoría, dos correos con `DespuesDelCommit`). Cubre: R2, R18, R19, R23, R24,
  R25, R26, R27, R28, R29, R49. Test: `CambioCorreoServiceTest` (un test por R; R29 sin token ni enlace pero con aviso;
  R28 con el envío que lanza y con rollback).
- [x] T12 — Crear `CambioCorreoService.confirmar` (forma → hash → tipo → usado → expirado → cuenta; `marcarUsado`
  condicional; correo en uso y `DataIntegrityViolationException` → mismo 400). Cubre: R31, R32, R33, R50.
  Test: `CambioCorreoServiceTest`.
- [x] T13 — Crear `FacturacionService.actualizar` (orden de § 6: rol, validación de documento, contraseña, huella,
  bloqueo `usuario → perfil`, sin cambios, en revisión, documento en uso con fallo contado, auditoría). Cubre: R3, R35,
  R36, R37, R38, R39, R40, R41, R42, R43, R51, R52. Test: `FacturacionServiceTest`.

## API

- [x] T14 — Crear `PerfilController` (`/api/usuarios/actual/perfil`, `/foto`, `/cambios-correo`; `no-store`; `nosniff`
  en la foto; 202 en la solicitud de correo) y `FacturacionController` (`PUT /api/arrendadores/actual/facturacion`,
  responde con `PerfilService.consultar`). Cubre: R1, R2, R3, R4, R7, R16. Test: `PerfilControllerIT`.
- [x] T15 — Crear `ConfirmacionCorreoController` y agregar en `ConfiguracionSeguridad` el `permitAll` solo para
  `POST /api/cambios-correo/confirmaciones`. Cubre: R7, R34. Test: `PerfilControllerIT` (sin sesión responde 200/400 y
  nunca 401; un `GET` o un token en la URL no confirman).
- [x] T16 — Pruebas de integración de punta a punta de CA1: `PUT` de datos y `GET` inmediato con los mismos valores;
  foto JPG con EXIF → se devuelve sin EXIF y el archivo en disco está cifrado; PDF renombrado `.jpg` → 400; reemplazo y
  quitar borran el archivo anterior; otro usuario no ve la foto. Cubre: R5, R8–R17, R45. Test: `PerfilControllerIT`.
- [x] T17 — Pruebas de integración de CA2: solicitud con el enlace capturado del doble de `NotificadorCorreo`, el login con
  el correo pendiente da 401 y con el actual funciona, la confirmación cambia el correo y el login con el nuevo
  funciona; correo en uso → 202 idéntico sin enlace; enlace usado, vencido (reloj controlable) o de otra solicitud
  anulada → 400; dos confirmaciones simultáneas → una sola aplica; contraseña incorrecta ×5 → 429; 4.ª solicitud en la
  hora → 429. Cubre: R18–R34, R49, R50. Test: `CambioCorreoControllerIT`.
- [x] T18 — Pruebas de integración de CA3: arrendador `VERIFICADA` cambia el RUC → `EN_REVISION`, `GET
  /api/sesiones/actual` da el aviso de revisión y `habilitacionArrendador` no verificada; cambia la razón social →
  `EN_REVISION`; cambia solo la dirección → sigue `VERIFICADA`; `EN_REVISION` + documento → 400; documento de otro
  arrendador → 400 genérico y cuenta un fallo; columnas `v1:…` tras editar; `USUARIO` → 403; dos `PUT` simultáneos sin
  error de integridad. Cubre: R3, R35–R45, R51, R52. Test: `FacturacionControllerIT`.
- [x] T19 — Revisar que ningún log, detalle de auditoría ni `toString` de las pruebas anteriores contenga contraseña,
  número de documento, razón social, dirección, teléfono, nombre, correo nuevo completo ni token. Cubre: R46.
  Test: `CambioCorreoControllerIT`, `FacturacionControllerIT` (captura de logs con `OutputCaptureExtension`).

## Frontend

- [x] T20 — Agregar a `src/api/cuentas.js` `obtenerPerfil`, `actualizarDatosPerfil`, `subirFotoPerfil`,
  `obtenerFotoPerfil` (`peticionArchivo`), `quitarFotoPerfil`, `solicitarCambioCorreo`, `confirmarCambioCorreo`
  (`autenticada: false`) y `actualizarFacturacion`; agregar `refrescar()` a `ProveedorSesion`. Cubre: pantallas Mi
  perfil y Confirmar correo (CA1, CA2, CA3).
- [x] T21 — Crear `pages/cuentas/Perfil.jsx` + `Perfil.module.css` (secciones Datos personales, Foto, Correo con
  `Modal` de contraseña y, solo `ARRENDADOR`, Datos de facturación con sus avisos) y `pages/cuentas/ConfirmarCorreo.jsx`
  + `.module.css` (botón explícito, limpia el `#`); textos en `textosCuentas.js`; rutas `/panel/perfil` (con
  `RutaProtegida`) y `/confirmar-correo` (pública) en `App.jsx`; enlace "Mi perfil" en `Panel.jsx` para `USUARIO` y
  `ARRENDADOR`. Cubre: pantallas Mi perfil (CA1, CA2, CA3) y Confirmar correo (CA2).

## Documentación y cierre

- [x] T22 — Actualizar `docs/modelo-datos.md` (`usuario`: `correo_pendiente`, `foto_ruta`, `foto_tipo_mime` en lugar de
  `foto_url`; `token.tipo` con `CAMBIO_CORREO` de 30 min; restricciones nuevas), `docs/requerimientos.md` (RNF-SEG-17:
  "contraseña de perfil 5 fallos / 15 min por cuenta; cambio de correo 3 por cuenta / hora"; y, si la dueña mantiene D9,
  la nota en RN-02 de que la razón social también devuelve a revisión), `docs/architecture.md` (lo que deja HU-20:
  rutas, `ReautenticacionPerfil`, efectos de volver a revisión D11/D12/D14 y notas para HU-22/HU-25) y
  `docs/frontend.md` (rutas `/panel/perfil` y `/confirmar-correo`). Cubre: R20, R24, R25, R37, R38.
- [x] T23 — Registrar el mapa R↔test en `progress/impl_HU-20.md` (R1–R52). Sin variables nuevas en `.env.example`.
  Cubre: R1–R52.
- [x] T24 — Verificación final: `./init.sh` en verde (lint, build del frontend, pruebas unitarias e integración).
  Cubre: R1–R52.
