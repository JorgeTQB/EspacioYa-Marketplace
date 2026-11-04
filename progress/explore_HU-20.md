# Exploración HU-20 · Edición de perfil

> Explorador (general-purpose), 2026-11-04. Solo lectura del código; este es el único archivo escrito.
> Rutas relativas a la raíz del repo. `B/` = `2_Backend/src/main/java/pe/edu/ulima/espacioya/`,
> `M/` = `2_Backend/src/main/resources/db/migration/`, `F/` = `3_frontend/src/`.

## Preguntas

1. Modelo de usuario/arrendador (entidades, tablas, migraciones) y columnas que faltan para HU-20.
2. Cómo obtiene un controlador el usuario de la sesión; cómo se verifica una contraseña (reautenticación).
3. Verificación de correo existente (tokens, envío de correos); qué se reutiliza para "nuevo correo pendiente" y "aviso al anterior".
4. Estados de identidad, dónde cambian y cómo devolverla a "en revisión".
5. Almacenamiento de archivos para la foto de perfil (servicio, URL firmadas, validación tipo/tamaño).
6. Auditoría.
7. Cifrado / huellas.
8. Rate limiting y límites aplicables.
9. Frontend: rutas, pantallas de cuenta, `api/`, componentes UI, sesión en cliente.
10. Endpoints existentes tipo `/perfil` o `/me`.

## Archivos analizados

- Historia y docs: `docs/historias/HU-20.md`, `docs/modelo-datos.md`, `docs/requerimientos.md`, `docs/architecture.md` (§ interfaz `CuentaService`), `specs/HU-01/design.md` (decisión S5), `progress/HU-20.md`.
- Migraciones: `M/V1_1__usuarios.sql`, `M/V1_2__auditoria.sql`, `M/V1_3__perfil_arrendador.sql`, `M/V1_5__token.sql`, `M/V2_1__documento_identidad.sql`, `M/V2_3__revision_identidad.sql`, `M/V5_2__cuenta_bancaria.sql`.
- Dominio `cuentas`: `Usuario`, `PerfilArrendador`, `EstadoIdentidad`, `Token`, `TipoToken`, `TipoDocumento`, `ValidadorDocumento`.
- Servicios: `SesionService`, `CuentaService` + `impl/CuentaServiceImpl`, `RecuperacionContrasenaService`, `RegistroArrendadorService`, `IdentidadArrendadorService`, `MfaService`, `NotificadorCorreo` + `NotificadorCorreoSimulado`, `AvisoCuenta`, `pagos/service/CuentaBancariaService`, `espacios/service/FotoEspacioService`.
- Repositorios: `UsuarioRepository`, `PerfilArrendadorRepository`, `TokenRepository`.
- `comun`: `seguridad/*` (`UsuarioAutenticado`, `FiltroAutenticacionSesion`, `ConfiguracionSeguridad`, `TokenAleatorio`), `auditoria/*`, `cifrado/*`, `almacenamiento/*`, `limite/*`, `web/ArchivoMultipart`, `error/ManejadorGlobalErrores`, `transaccion/DespuesDelCommit`.
- Controladores: `cuentas/controller/*`, `pagos/controller/CuentaBancariaController`, `espacios/controller/FotoPublicaController`.
- `2_Backend/src/main/resources/application.properties`.
- Frontend: `F/App.jsx`, `F/api/{cliente,cuentas,pagos,tokenSesion}.js`, `F/sesion/*`, `F/components/ui/*`, `F/pages/cuentas/{Panel,SeguridadCuenta,erroresCampo}.jsx/js`, `F/pages/pagos/CuentaBancaria.jsx`, `F/pages/cuentas/VerificarIdentidad.jsx`, `F/pages/espacios/EditarEspacio.jsx`.

---

## 1. Modelo de usuario / arrendador

**Tabla `usuario`** (`M/V1_1__usuarios.sql:1-14` + `M/V1_3__perfil_arrendador.sql:3-6`):
`id, correo (VARCHAR 254, UNIQUE uq_usuario_correo, CHECK normalizado lower/btrim), hash_contrasena (VARCHAR 100), rol, correo_verificado (BOOLEAN DEFAULT FALSE), estado, version_politica, fecha_consentimiento, nombre (VARCHAR 100, nullable), telefono (VARCHAR 9, nullable, CHECK ck_usuario_telefono '^9[0-9]{8}$')`.

Entidad `B/cuentas/domain/Usuario.java`:
- Campos `correo` (:24-25), `hashContrasena` (:27-28), `correoVerificado` (:34-35), `nombre` (:47-48), `telefono` (:50-51).
- Fábricas: `registrar` (:56-68, rol USUARIO, **sin nombre ni teléfono**, `correoVerificado = true` por decisión S5 de HU-01, :63-64), `registrarArrendador` (:70-77, con nombre y teléfono), `registrarAdmin` (:80-85).
- `normalizarCorreo` (:87-89). `cambiarContrasena` (:115-117) es el único mutador de datos personales; **no hay setters/métodos para nombre, teléfono ni correo**.

**Tabla `perfil_arrendador`** (`M/V1_3__perfil_arrendador.sql:11-24`):
`usuario_id (PK/FK cascade), tipo_documento (DNI|RUC), numero_documento (TEXT cifrado), hash_documento (VARCHAR 64, UNIQUE uq_perfil_arrendador_hash_documento), razon_social (TEXT cifrado), direccion_fiscal (TEXT cifrado), estado_identidad (NO_INICIADA|EN_REVISION|VERIFICADA|RECHAZADA), motivo_rechazo (VARCHAR 500)`.

Entidad `B/cuentas/domain/PerfilArrendador.java`: campos cifrados con `@Convert(converter = ConvertidorCifrado.class)` (:40-53); `crear` (:68-80); transiciones `enviarDocumentos` (:87-93), `aprobarIdentidad` (:96-100), `rechazarIdentidad` (:102-109); `avisoIdentidad` (:117-124). **No hay mutadores de tipo/número de documento, razón social ni dirección fiscal.** Implementa `Persistable` (:24, :126-140).

Validación de documento reutilizable: `B/cuentas/domain/ValidadorDocumento.java` (`normalizar` :23, `error(tipo, numero)` :27-44, módulo 11 SUNAT :47-53). Validaciones de formato de los campos en `B/cuentas/dto/RegistroArrendadorDTO.java:34-62` (nombre ≤100, teléfono `9[0-9]{8}`, tipo `DNI|RUC`, razón social ≤150, dirección ≤200; mensajes en constantes).

Otras tablas relacionadas: `documento_identidad` (`M/V2_1__documento_identidad.sql`), `revision_identidad` (`M/V2_3__revision_identidad.sql`, historial de decisiones admin), `cuenta_bancaria` (`M/V5_2__cuenta_bancaria.sql`; `titular` = copia de la razón social al registrar, `CuentaBancariaService.java:119-122`).

**Contraste con `docs/modelo-datos.md:13`**: `usuario` debe tener `foto_url` (y `recordatorios_activos`, de HU-25) y lista HU-20 entre sus historias. Faltan en BD/entidad:
- `foto_url` (o mejor `ruta_foto` / clave de almacenamiento + `tipo_mime_foto`): **no existe**.
- Correo pendiente de verificación: **no existe** columna (p. ej. `correo_pendiente`) ni tipo de token para cambio de correo. `token.tipo` admite solo `VERIFICACION|RECUPERACION` (`M/V1_5__token.sql:15`; `TipoToken` solo tiene `RECUPERACION`, `B/cuentas/domain/TipoToken.java:3-5`).
- "Datos de facturación" del USUARIO (rol USUARIO): **no hay ninguno**; solo el arrendador tiene `razon_social`/`direccion_fiscal`/documento en `perfil_arrendador`.
- `recordatorios_activos` pertenece a HU-25, fuera de alcance.

Siguiente migración: el sprint 6 → `V6_1__...` (las actuales llegan a `V5_3`; `spring.flyway.out-of-order=true`, `application.properties:18`).

## 2. Sesión y verificación de contraseña

- El filtro `B/comun/seguridad/FiltroAutenticacionSesion.java:31-55` lee `Authorization: Bearer`, valida con `SesionService.validar` (`B/cuentas/service/SesionService.java:49-62`, renueva 30 min; solo cuentas ACTIVA) y pone como principal el record `UsuarioAutenticado(usuarioId, sesionId, rol)` (`B/comun/seguridad/UsuarioAutenticado.java:3`).
- Los controladores lo reciben con `@AuthenticationPrincipal UsuarioAutenticado usuario` (p. ej. `B/cuentas/controller/SesionController.java:74-77`, `MfaController.java:37`, `CuentaBancariaController.java:37,43`). Convención de rutas "del usuario de la sesión": `/api/arrendadores/actual/...` (`IdentidadArrendadorController.java:23`, `CuentaBancariaController.java:27`) y `/api/sesiones/actual`. Comentario de convención en `B/espacios/controller/EspacioController.java:26` ("La identidad sale de la sesión (/actual), nunca de la URL ni del cuerpo").
- Toda ruta no listada en `ConfiguracionSeguridad.java:43-69` exige sesión (`anyRequest().authenticated()` :69). CORS ya permite `PUT`, `PATCH`, `DELETE` (:79).
- IP del cliente: `B/comun/web/IpCliente.de(peticion)` (usado en `SesionController.java:50,82`).
- **Reautenticación reutilizable**: `CuentaService.confirmarContrasena(UsuarioAutenticado, String) → boolean` (`B/cuentas/service/CuentaService.java:128-132`; impl `impl/CuentaServiceImpl.java:251-257`, bcrypt `encoder.matches`). No cuenta intentos: lo hace el llamador. `docs/architecture.md:165-166` dice explícitamente que "HU-20 y HU-22 pueden reutilizarlo".
- Patrón completo de reautenticación con límite de fallos: `B/pagos/service/CuentaBancariaService.java:81-99` — `limitador.superaLimite(clave, 5, 15 min)` → 429 `DemasiadosIntentosException`; contraseña mala → `limitador.registrar`, auditoría `CUENTA_BANCARIA_CONTRASENA_FALLIDA` fuera de transacción, `ReglaNegocioException("contrasena", "La contraseña no es correcta.")`; correcta → `limitador.limpiar` y luego `TransactionTemplate` con bloqueo de fila (bcrypt fuera de la transacción, :101). Constantes :35-46.
- Otro ejemplo: `MfaService.procesarDesactivacion` (`B/cuentas/service/MfaService.java:~145-167`) con `encoder.matches` directo.
- DTO con contraseña: `B/cuentas/dto/DesactivacionMfaDTO.java:7-21` (`@NotBlank @Size(max = 72)`, `toString` enmascarado) — patrón a copiar.
- Encoder: `ConfiguracionSeguridad.java:86-89` (`BCryptPasswordEncoder(12)`).
- Sesión actual: `GET /api/sesiones/actual` → `SesionActualDTO(rol, aviso)` (`B/cuentas/dto/SesionActualDTO.java:3`; `SesionService.actual` :64-69). No devuelve nombre/correo/foto.
- MFA (HU-17) no interviene en la edición de perfil; solo es relevante si se decide exigir código además de contraseña (no lo pide la historia).

## 3. Verificación de correo y envío de correos

- **No existe verificación de correo real.** Decisión S5 de HU-01 (`specs/HU-01/design.md:11-14`, `:389-390`): las cuentas nacen con `correo_verificado = true` (`Usuario.java:63-64`); HU-25 conectará el correo real. `NotificadorCorreo.notificarVerificacion` solo escribe un log (`B/cuentas/service/NotificadorCorreoSimulado.java:21-24`).
- Puerto de correo: `B/cuentas/service/NotificadorCorreo.java:8-31` (interfaz con un método por aviso; HU-25 lo moverá a `comun/correo`). Única impl: `NotificadorCorreoSimulado.java` (solo logs; nunca escribe el correo ni datos personales en el log; con `espacioya.correo.simulado.registrar-enlace=true` — `application.properties:29` — registra el enlace de recuperación para pruebas, :26-35). Para HU-20 habría que **agregar** p. ej. `notificarVerificacionCambioCorreo(correoNuevo, enlace)` y `notificarCambioCorreoAlAnterior(correoAnterior)`.
- Envío tras commit: `B/comun/transaccion/DespuesDelCommit.ejecutar(Runnable)` (usado en `CuentaServiceImpl.java:132,144,195,209`, `CuentaBancariaService.java:139`).
- **Tokens de un solo uso reutilizables** (de HU-04):
  - Tabla `token` (`M/V1_5__token.sql:5-19`): `hash_token` SHA-256 hex único, `creado_en`, `expira_en`, `usado_en`; CHECK de tipo `VERIFICACION|RECUPERACION` (:15) — un nuevo tipo (p. ej. `CAMBIO_CORREO`) exige migración que cambie el CHECK, o reutilizar `VERIFICACION`.
  - Entidad `B/cuentas/domain/Token.java` (solo fábrica `recuperacion` :51-59, vigencia 30 min :22; `fueUsado`, `expiro`). No guarda a qué correo nuevo apunta → hace falta columna en `usuario` (`correo_pendiente`) o en `token`.
  - `B/comun/seguridad/TokenAleatorio.java` (`generar` 32 bytes base64url :21-25, `tieneFormato` :27-29, `hash` :31-38).
  - `B/cuentas/repository/TokenRepository.java`: `findByHashTokenAndTipo` (:16), `marcarUsado` condicional (:19-21), `anularPendientes` (:23-27).
  - Flujo modelo completo: `B/cuentas/service/RecuperacionContrasenaService.java` — generar+hash+anular pendientes+guardar+notificar con enlace `urlFrontend + RUTA + "#token="` (:101-108, ruta :50, `urlFrontend` :67-78), comprobar forma→hash→existe→usado→expirado→cuenta (:153-176), canje con `UPDATE` condicional contra carreras (:131-148), rutas públicas `POST /api/recuperaciones/validaciones|confirmaciones` (`ConfiguracionSeguridad.java:52-54`, `RecuperacionContrasenaController.java:40-50`).
- Unicidad de correo: `uq_usuario_correo` (`V1_1:10`) + `UsuarioRepository.findByCorreo` (`:19`). Para no revelar si el correo nuevo ya existe (RNF-SEG-13), ver el manejo de "correo existente" en registro (`RegistroArrendadorService.java` ~:81-90 `auditarSiExiste`, carrera `DataIntegrityViolationException` ~:79-88).

## 4. Identidad (HU-05/HU-06)

- Estados: `B/cuentas/domain/EstadoIdentidad.java:3-8` (`NO_INICIADA, EN_REVISION, VERIFICADA, RECHAZADA`); CHECK en `V1_3:22-23`.
- Transiciones en `PerfilArrendador`: `enviarDocumentos` solo desde NO_INICIADA/RECHAZADA → EN_REVISION (:83-93); `aprobarIdentidad`/`rechazarIdentidad` solo desde EN_REVISION (:96-115).
- Quién las usa: `IdentidadArrendadorService` (envío de documentos, bloqueo `perfiles.findParaActualizar` :175, `perfil.enviarDocumentos()` :193); `CuentaServiceImpl.aprobarIdentidad/rechazarIdentidad` (:125-146, con correo tras commit) y `administracion/service/RevisionIdentidadService` (registra `revision_identidad`).
- Bloqueo pesimista: `PerfilArrendadorRepository.findParaActualizar` (`:23-25`).
- **No existe transición VERIFICADA → EN_REVISION.** Hay que agregar un método de dominio (p. ej. `volverARevision()` / `cambiarDocumento(...)`) que, si el estado es `VERIFICADA`, pase a `EN_REVISION` (RN-02, `docs/requerimientos.md:211`).
- Efecto colateral relevante: la cola del admin (`PerfilArrendadorRepository.findEnRevision`, :32-44) hace JOIN con `documento_identidad` y ordena por `min(subido_en)`: un perfil devuelto a EN_REVISION aparece en la cola con sus documentos anteriores (los vigentes no se borran) y con la fecha de su envío original (quedaría primero en la cola). Si el arrendador estaba en `NO_INICIADA` y nunca subió documentos, no aparecería.
- Al dejar de estar `VERIFICADA`: `EspacioService` exige identidad verificada para publicar (`B/espacios/service/EspacioService.java:139`), `CuentaBancariaService` para registrar/cambiar cuenta (:83, :105), y `CuentaService.arrendadoresSinIdentidadVerificada` hace que HU-19 avise al admin en liquidaciones (`CuentaService.java:141-146`). No hay lógica que oculte espacios ya publicados al perder la verificación.
- `SesionActualDTO.aviso` y `PerfilArrendador.avisoIdentidad` (:117-124) ya muestran el aviso de "en revisión" al arrendador tras el cambio.

## 5. Almacenamiento de archivos (foto de perfil)

- Adapter: `B/comun/almacenamiento/AlmacenamientoArchivos.java:10-17` (impl `AlmacenamientoLocal`, directorio `espacioya.almacenamiento.directorio`, `application.properties:45`).
- Dos fachadas:
  - `ArchivosPrivados` (cifrado + URL firmada; `guardar(carpeta, bytes)` :32, `leer` :41, `eliminar` :48, `urlFirmada(clave, mime)` :54) — usado por documentos de identidad (carpeta `identidad`).
  - `ArchivosPublicos` (`B/comun/almacenamiento/ArchivosPublicos.java:14-46`; sin cifrar, clave `carpeta/<UUID>`, carpeta `^[a-z]+$`) — usado por fotos de espacios (carpeta `espacios`, `FotoEspacioService.java:45`).
- URL firmadas: `FirmadorUrl` (`ALMACENAMIENTO_LLAVE_FIRMA`, 5 min, HMAC sobre clave+mime+expira, `B/comun/almacenamiento/FirmadorUrl.java:28-86`), servidas por `ArchivoController` `GET /api/archivos` (público, `ConfiguracionSeguridad.java:56`).
- Fotos de espacios usan **URL estable** `/api/fotos/{uuid}` (`B/espacios/controller/FotoPublicaController.java:32-40`, `Cache-Control` según visibilidad, `X-Content-Type-Options: nosniff`) en vez de URL firmada.
- Validación: `B/comun/almacenamiento/ValidadorArchivo.java` — 5 MiB (`TAMANO_MAXIMO` :28), tope de píxeles 40 M (:30), tipo real por magic bytes PDF/PNG/JPEG (:70-81), **re-codifica imágenes y elimina EXIF** (:87-134), devuelve `Aceptado(ArchivoValidado)` o `Rechazado(MotivoRechazoArchivo)`. Para imagen-solo, el patrón de `FotoEspacioService.validar` rechaza PDF (`FotoEspacioService.java:138-150`) con mensajes `MENSAJE_TIPO_FOTO` "La foto debe ser JPG o PNG." (:49-51).
- Multipart → `ArchivoRecibido`: `B/comun/web/ArchivoMultipart.recibido(MultipartFile)` (:21-33, no lee bytes si excede). Límites de Spring: `max-file-size=6MB`, `max-request-size=13MB` (`application.properties:38-40`); `ManejadorGlobalErrores` maneja `MaxUploadSizeExceededException`/`MultipartException` (:87-97).
- Campo UI: `Campo tipo="archivo"` con `accept` (`F/components/ui/Campo.jsx:95`), usado en `VerificarIdentidad.jsx:144,153` y `EditarEspacio.jsx:335`.
- Limpieza de archivo si la transacción falla: ver `FotoEspacioService.java:~210-216` (`archivos.eliminar(clave)`).

## 6. Auditoría

- Tabla `auditoria` (`M/V1_2__auditoria.sql:1-12`): `usuario_id, accion (VARCHAR 60), entidad, entidad_id, ip, fecha, detalle (TEXT)`.
- Componente: `B/comun/auditoria/AuditoriaService.registrar(EventoAuditoria)` (`AuditoriaService.java:20-23`, `@Transactional`: se une a la transacción abierta o se confirma sola si no la hay); `EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle)` (`EventoAuditoria.java:3-5`).
- Convención: constantes `ACCION_*` públicas en el servicio (`CuentaBancariaService.java:35-37`, `MfaService.java:36`, `RecuperacionContrasenaService.java:30-33`), `detalle` como `clave=valor;clave=valor` y **nunca datos sensibles en claro** (cuenta enmascarada en `CuentaBancariaService.java:123-136`). RNF-SEG-12 (`docs/requerimientos.md:157`) incluye "cambios de perfil".
- No existe tabla de historial específica del perfil; la auditoría basta (como HU-18: "el historial vive en la auditoría", `V5_2:1-2`).

## 7. Cifrado y huellas

- `B/comun/cifrado/CifradorCampos.java` (`CIFRADO_LLAVE`, AES-256-GCM, formato `v1:` + base64, :21-84) aplicado vía `ConvertidorCifrado` (`@Convert`, `ConvertidorCifrado.java:13-32`).
- `B/comun/cifrado/HuellaCampos.java` (`CIFRADO_LLAVE_HUELLA`, HMAC-SHA256 hex, `calcular(claro)` :30-38) para unicidad sin guardar en claro.
- Campos cifrados hoy: `perfil_arrendador.numero_documento`, `razon_social`, `direccion_fiscal` (`PerfilArrendador.java:40-53`), `cuenta_bancaria.numero`, `mfa.secreto`; huella `perfil_arrendador.hash_documento` (UNIQUE) y `codigo_respaldo.hash_codigo`. `CifradorArchivos` cifra los documentos privados.
- Para HU-20: al cambiar el número de documento hay que recalcular `hash_documento = huella.calcular(numeroNormalizado)` (como `RegistroArrendadorService.java:~76`) y manejar colisión con `uq_perfil_arrendador_hash_documento` (otro arrendador con ese documento) sin revelar la otra cuenta. `nombre`, `telefono` y `correo` **no** se cifran (correo necesita UNIQUE/búsqueda; `UsuarioRepository.buscar` busca por nombre y correo, :28-34).

## 8. Rate limiting y límites

- Componentes: `B/comun/limite/LimitadorIntentos.java` (en memoria; `intentar` :23, `superaLimite` :39, `registrar` :49, `limpiar` :57) y `ReglaLimite` + `FiltroLimiteIntentos` para límites por IP y ruta declarados como `@Bean` (ejemplo `B/cuentas/controller/ReglasLimiteCuentas.java:11-28`, 5 registros/IP/hora).
- `docs/requerimientos.md` RNF-SEG-17 (:162) **no lista límite para HU-20**. Aplicables por analogía: reautenticación con contraseña 5 fallos / 15 min por usuario (HU-18, `CuentaBancariaService.java:38-39,86-90`), recuperación 3 por correo / hora (`RecuperacionContrasenaService.java:34-35`) como referencia para limitar reenvíos del correo de verificación de cambio de correo.
- RNF-SEG-03 (:148): reautenticación con contraseña para cambiar correo o facturación. RNF-SEG-01 (:146): identidad desde la sesión (cita HU-20 E). RNF-SEG-13 (:158): no confirmar si un correo existe. RNF-SEG-14 (:159): facturación cifrada. RNF-SEG-16 (:161): minimización de datos. RNF-USA-02 (:193): archivos hasta 5 MB. RF-A-20/21/22 (:50-52), RN-02 (:211).
- Códigos HTTP: `ReglaNegocioException` 400 con campo (`ManejadorGlobalErrores.java:23`), `DemasiadosIntentosException` 429 (:44-46), `AccesoDenegadoException` 403 (:70-72).

## 9. Frontend

- Rutas (`F/App.jsx:126-310`): públicas `/`, `/buscar`, `/espacios/:id`, `/registro`, `/registro-arrendador`, `/privacidad`, `/iniciar-sesion`, `/recuperar-contrasena`, `/restablecer-contrasena`; protegidas con `RutaProtegida` `/panel` (:165-172), `/panel/seguridad` (:174-181, HU-17), `/panel/identidad` (:182-189), `/panel/cuenta-bancaria` (:191-197), `/panel/espacios*`, `/panel/reservas`, `/panel/liquidaciones`, `/mis-reservas`, `/reservas/:id*`, `/admin/*`. **No hay ruta de perfil** (candidata: `/panel/perfil`). Tampoco hay ruta pública para confirmar un cambio de correo (candidata: `/verificar-correo#token=...`, análoga a `/restablecer-contrasena`).
- Panel: `F/pages/cuentas/Panel.jsx` enlaza por rol (:45-86; "Seguridad" :86 para todos) — punto para añadir "Mi perfil".
- Encabezado: `App.jsx` muestra "Mi panel" / "Cerrar sesión" (:~100-110), sin nombre ni foto del usuario.
- API: `F/api/cuentas.js` (registro, sesión, identidad, recuperación, MFA; :3-127) — **no hay funciones de perfil**. Cliente `F/api/cliente.js`: `peticion(ruta, {metodo, cuerpo, autenticada})` admite JSON o `FormData` (:46-76), `peticionArchivo` para blobs con token (:80-100), `urlApi` (:13-15), 401 → `alPerderSesion` (:17-27). Ejemplo de llamada con contraseña: `F/api/pagos.js:40` (`guardarCuentaBancaria({..., contrasena})`).
- Sesión cliente: `F/sesion/ProveedorSesion.jsx` guarda solo `{ rol, aviso }` de `GET /api/sesiones/actual` (:17-33, :47); `entrar`/`salir`; `useSesion`, `RutaProtegida`, token en `F/api/tokenSesion.js`. Si el aviso de identidad cambia (CA3) habría que refrescar la sesión (no existe un `refrescar()`; hoy solo `entrar`).
- Componentes UI reutilizables (`F/components/ui/`): `Campo` (texto, `checkbox`, `select`, `textarea`, `archivo` con `accept`, errores accesibles; `Campo.jsx:4-128`), `Boton` (variantes, `cargando`), `Alerta` (`tipo`), `Modal` (`<dialog>`, `onConfirmar`/`onCerrar`, `cargando`, `textoConfirmar`; `Modal.jsx:8-52`), `Tarjeta`, `EtiquetaEstado`, `Imagen`. Errores por campo: `F/pages/cuentas/erroresCampo.js:1-3` (`erroresPorCampo`).
- Patrones de "reingresar contraseña": modal con contraseña en `F/pages/cuentas/SeguridadCuenta.jsx:42-47,100-125,182-208` (desactivar MFA); campo de contraseña inline en formulario en `F/pages/pagos/CuentaBancaria.jsx:33,88,164-167` (se limpia tras enviar).
- Textos centralizados por módulo (`F/pages/cuentas/textosCuentas.js`).

## 10. Endpoints tipo `/perfil` o `/me`

- **No existe ninguno.** Lo más cercano: `GET /api/sesiones/actual` (solo rol y aviso, `SesionController.java:74-77`), `GET /api/arrendadores/actual/identidad` (`IdentidadArrendadorController.java:46-47`) y `GET|PUT /api/arrendadores/actual/cuenta-bancaria` (`CuentaBancariaController.java:27-43`). El admin ve nombre/correo/teléfono vía `DetalleCuentaDTO` (`CuentaServiceImpl.java:163-176`) y el expediente con datos de facturación descifrados (`:107-123`).
- Convención para nuevos: recurso `actual` de la sesión, p. ej. `GET/PUT /api/usuarios/actual/perfil` o `/api/cuentas/actual` (decisión del spec; `/api/usuarios` y `/api/arrendadores` hoy solo tienen `POST` público de registro, `ConfiguracionSeguridad.java:45-46`; cuidado de no abrir más rutas con `permitAll`).

---

## Huecos / decisiones para el spec

1. **Migración `V6_1`**: añadir a `usuario` la foto (`foto_ruta`/`foto_tipo_mime` o `foto_url` como dice `modelo-datos.md:13`) y el correo pendiente (`correo_pendiente VARCHAR(254)` con CHECK de normalización; ¿UNIQUE parcial?). Decidir si el token de cambio de correo usa el tipo `VERIFICACION` ya permitido por el CHECK o un tipo nuevo (`CAMBIO_CORREO`, exige alterar `ck_token_tipo`), su vigencia (¿30 min como recuperación, 24 h?) y si el correo destino vive en `usuario.correo_pendiente` o en el token. Actualizar `docs/modelo-datos.md`.
2. **Sin correo real (S5)**: el "nuevo correo solo se activa tras verificarlo" requiere un enlace que hoy no se envía; habrá que añadir métodos a `NotificadorCorreo` y, para probarlo, registrar el enlace en el log con la propiedad de desarrollo (como HU-04). Definir la ruta pública de confirmación (backend `permitAll` + página frontend) y el texto del aviso al correo anterior (sin datos del nuevo correo completo, o enmascarado).
3. **Correo nuevo ya registrado por otra cuenta**: respuesta genérica sin revelar existencia (RNF-SEG-13); verificar unicidad otra vez al confirmar (carrera contra `uq_usuario_correo`). ¿Qué pasa con las sesiones al activar el nuevo correo (¿revocar otras como en MFA `revocarOtras`)? ¿Se limpia el límite de login del correo viejo?
4. **Qué exige contraseña**: la historia dice correo o facturación. Definir si "facturación" = razón social, dirección fiscal y tipo/número de documento del arrendador, y si nombre/teléfono/foto se guardan sin contraseña. Reutilizar `CuentaService.confirmarContrasena` + `LimitadorIntentos` (5 fallos / 15 min por usuario, clave propia p. ej. `perfil|<id>`) + auditoría del fallo fuera de transacción, como HU-18.
5. **USUARIO (rol) y facturación**: el usuario normal no tiene datos de facturación ni nombre al registrarse. ¿HU-20 solo le deja editar nombre/teléfono/foto/correo? ¿O se le crean columnas de facturación? (Recomendación del explorador: facturación solo para arrendador, ya que solo él tiene `perfil_arrendador`.) Admin: ¿puede editar su perfil? (no recupera contraseña por correo; cambiar su correo afecta su acceso).
6. **CA3 – cambio de RUC/DNI**: nuevo método de dominio en `PerfilArrendador` (cambiar tipo/número + hash y, si `VERIFICADA`, pasar a `EN_REVISION`; ¿también si `RECHAZADA` o `NO_INICIADA` se mantiene?). Validar con `ValidadorDocumento`, recalcular `hash_documento` con `HuellaCampos`, colisión con `uq_perfil_arrendador_hash_documento` sin revelar la otra cuenta. Bloquear con `findParaActualizar`. Decidir si cambiar razón social/dirección (sin cambiar documento) también devuelve a revisión (RN-02 solo habla de RUC/DNI). Efectos: deja de poder publicar y registrar cuenta bancaria, liquidaciones avisan al admin (HU-19 D6); los documentos vigentes siguen siendo los del documento anterior y el envío conserva su `subido_en` antiguo en la cola del admin: ¿exigir resubir documentos (→ `NO_INICIADA`/forzar envío) o reutilizar los vigentes? ¿Se oculta lo ya publicado? ¿Bloquear el cambio de documento mientras está `EN_REVISION`?
7. **Coherencia con HU-18**: `cuenta_bancaria.titular` es copia de la razón social al registrar (`CuentaBancariaService.java:120`); si cambia la razón social, ¿se actualiza el titular o se deja? Documentarlo.
8. **Foto de perfil**: `ArchivosPublicos` (carpeta nueva, p. ej. `perfiles`, URL estable tipo `/api/fotos/...` o nuevo `GET /api/usuarios/{uuid}/foto`) vs `ArchivosPrivados` + URL firmada de 5 min. Quién puede verla (¿solo el dueño? ¿se muestra al arrendador/usuario en reservas? RNF-SEG-16). Validación con `ValidadorArchivo` + rechazo de PDF (patrón `FotoEspacioService.validar`), 5 MiB. Borrar el archivo anterior al reemplazar y el nuevo si falla la transacción. ¿Permitir quitar la foto?
9. **Endpoints y forma**: `GET` del perfil propio (qué devuelve: nombre, teléfono, correo, correo pendiente, foto, y para arrendador datos de facturación descifrados y estado de identidad — exponer el número de documento completo o enmascarado al propio dueño); `PUT/PATCH` de datos básicos; endpoint separado para correo (con contraseña) y para facturación (con contraseña); `POST/PUT` multipart para foto. Todo con el principal de sesión, sin id en URL/cuerpo.
10. **Auditoría**: nombres de acciones (`PERFIL_ACTUALIZADO`, `CORREO_CAMBIO_SOLICITADO`, `CORREO_CAMBIADO`, `FACTURACION_ACTUALIZADA`, `IDENTIDAD_DEVUELTA_A_REVISION`, `PERFIL_CONTRASENA_FALLIDA`, `FOTO_PERFIL_*`), con `detalle` sin datos personales en claro (solo campos cambiados; correos enmascarados o ausentes).
11. **Rate limit**: definir límite de solicitudes de cambio de correo (p. ej. 3/h por usuario, análogo a recuperación) y de fallos de contraseña; ninguno figura en RNF-SEG-17 → añadirlo a `docs/requerimientos.md`.
12. **Frontend**: nueva página `F/pages/cuentas/Perfil.jsx` en `/panel/perfil` + enlace en `Panel.jsx`; funciones nuevas en `F/api/cuentas.js`; página pública de confirmación de correo; reutilizar `Campo`, `Modal` (contraseña), `Alerta`, `Boton`. Tras CA3, refrescar `aviso` de la sesión (hoy `ProveedorSesion` no tiene un `refrescar`; podría llamarse a `entrar` con el resultado de `obtenerSesionActual`).
13. **Interfaz `CuentaService`**: si otro módulo no la necesita, la lógica vive dentro de `cuentas` (servicio nuevo, p. ej. `PerfilService`), sin ampliar la interfaz pública. Documentar cualquier cambio en `docs/architecture.md`.
