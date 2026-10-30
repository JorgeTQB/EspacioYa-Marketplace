# Exploración HU-09 · Suspensión y reactivación de cuentas

> Explorador (`general-purpose`) · 2026-10-29 · rama `feature/HU-09-suspension-reactivacion` (desde `develop` en `39a77aa`).
> Solo lectura: no se ejecutaron builds ni tests. Rutas relativas a la raíz del repo; el paquete Java base
> `2_Backend/src/main/java/pe/edu/ulima/espacioya/` se abrevia como `…/`.

## 0. Resumen en 10 líneas

- El panel admin de HU-06 se reutiliza tal cual: `FiltroIpsAdmin` cubre todo `/api/admin/**`; el rol `ADMIN` se valida en el servicio de `administracion` y otra vez en `CuentaService` (patrón R41).
- `usuario.estado` ya admite `SUSPENDIDA` (CHECK en `V1_1`), pero **`Usuario` no tiene ningún método para cambiar el estado** ni existe columna para el motivo.
- El login ya rechaza `estado != ACTIVA` (`Usuario.motivoRechazoInicioSesion`) y la recuperación también (HU-04). **`SesionService.validar` NO mira el estado**: una sesión abierta sigue sirviendo hasta que se revoque o venza.
- `SesionRepository.revocarTodas(usuarioId)` (HU-04) existe y sirve tal cual.
- **No existe catálogo público todavía** (HU-10/HU-11 `pending`). Las únicas superficies públicas de espacios hoy son `GET /api/fotos/{uuid}` y la interfaz `DisponibilidadService.horasLibres`; ninguna mira el estado del arrendador.
- **No existe búsqueda de usuarios**: `UsuarioRepository` solo tiene `findByCorreo`. El nombre vive en `usuario.nombre` (en claro, solo arrendadores lo tienen); `razon_social` va cifrada y no se puede buscar.
- `NotificadorCorreo` tiene 4 métodos; HU-06 envía después del commit con `TransactionSynchronization` (en `CuentaServiceImpl`).
- La auditoría (`auditoria`) no guarda el motivo por decisión de HU-06 (S16); el patrón es una tabla de historial como `revision_identidad`.
- Siguiente migración: **`V3_1`** (no hay ninguna `V3_*` en ninguna rama local ni remota conocida; HU-10/11/12, también sprint 3, competirán por el número).

---

## 1. Panel admin de HU-06 (qué se reutiliza)

| Pieza | Dónde | Qué hace | Reutilizable en HU-09 |
|---|---|---|---|
| `FiltroIpsAdmin` | `…/comun/seguridad/FiltroIpsAdmin.java:26-57` | `PREFIJO = "/api/admin/"` (l. 28); `PathPatternRequestMatcher` sobre `/api/admin/**` (l. 31); IP no permitida → 403 `ErrorDTO.MENSAJE_ACCESO_DENEGADO` antes de autenticar (l. 44-56). No audita. | **Sí, tal cual.** Basta con poner las rutas bajo `/api/admin/` (lo pide `docs/architecture.md:207` y `progress/history.md:489-490`). |
| `IpsPermitidasAdmin` | `…/comun/seguridad/IpsPermitidasAdmin.java:15-74` | Lista `espacioya.admin.ips-permitidas` (`ADMIN_IPS_PERMITIDAS`, local `127.0.0.1,::1`, `application.properties:50`); IPv4/IPv6/CIDR; vacía = nadie. | Sí, sin cambios. |
| Registro del filtro | `…/comun/seguridad/ConfiguracionSeguridad.java:39-42` | `FiltroIpsAdmin` antes de `FiltroAutenticacionSesion`. `anyRequest().authenticated()` (l. 56): sin sesión → 401. Sin `hasRole` (el rol lo valida el servicio). | Sí, sin cambios. |
| Login del admin solo desde IP permitida | `…/cuentas/service/InicioSesionService.java:106-110` | `esAdmin() && !ipsAdmin.permite(ip)` → `IP_NO_PERMITIDA`. | Ya cubierto. |
| Controlador | `…/administracion/controller/RevisionIdentidadController.java` | `@RequestMapping("/api/admin/arrendadores")` (l. 34); GETs con `CacheControl.noStore()` (l. 46, 52); POSTs de decisión `201` con `IpCliente.de(peticion)` (l. 55-67); actor por `@AuthenticationPrincipal UsuarioAutenticado`, `{id}` = objetivo. | Patrón a copiar: nuevo `CuentaAdminController` (o similar) bajo `/api/admin/cuentas`. |
| Servicio | `…/administracion/service/RevisionIdentidadService.java` | `exigirAdmin` local (`"ADMIN".equals(admin.rol())`, l. 81-85) antes de llamar a `CuentaService`; `@Transactional` que cambia estado vía `CuentaService` + guarda historial + audita (l. 60-78); `auditar` sin motivo (l. 87-92). | Patrón a copiar. `exigirAdmin` es `private static`: o se duplica, o se extrae (p. ej. a `comun/seguridad`) — decisión del spec. |
| Rol en `CuentaService` | `…/cuentas/service/CuentaServiceImpl.java:131-136` | `exigirAdmin` con `Rol.ADMIN` → `AccesoDenegadoException` sin tocar repositorios (HU-06 R41). | Cada método nuevo de `CuentaService` para el admin debe repetirlo. |
| Tabla `revision_identidad` | `2_Backend/src/main/resources/db/migration/V2_3__revision_identidad.sql` | Historial: `arrendador_id` FK a `perfil_arrendador` (cascade), `admin_id` FK a `usuario` (sin cascade), `decision` CHECK, `motivo VARCHAR(500)` con CHECK condicional, `fecha`; índice `(arrendador_id, fecha)`. | Modelo para una tabla de historial de suspensiones (ver §7). |
| Entidad / DTO | `…/administracion/domain/RevisionIdentidad.java` (ids `Long`, sin asociaciones JPA, inmutable); `…/administracion/dto/MotivoRechazoDTO.java` (`@NotBlank` + `@Size(max=500)` tras `strip()` en el constructor compacto) | | `MotivoRechazoDTO` tiene un mensaje específico de rechazo de identidad (`M_MOTIVO`, l. 15): HU-09 necesita su propio DTO/mensaje de motivo de suspensión. |

Importante: `administracion` solo usa `cuentas` por `CuentaService` y sus DTO (`specs/HU-06/design.md:70`); la dirección `administracion → (todos)` está permitida (`docs/architecture.md:121-125`), así que **no hace falta puerto** para que `administracion` llame a `cuentas` o a `espacios`.

## 2. Estado de la cuenta

- Enum `EstadoUsuario { ACTIVA, SUSPENDIDA, ELIMINADA }` — `…/cuentas/domain/EstadoUsuario.java`. CHECK `ck_usuario_estado` en `V1_1__usuarios.sql:13`.
- `Usuario` (`…/cuentas/domain/Usuario.java`): `estado` se fija solo en `registrar` (l. 62) a `ACTIVA`. **No hay `suspender()`, `reactivar()` ni setter de estado.** Métodos de dominio existentes: `registrar*`, `esAdmin()` (l. 91), `motivoRechazoInicioSesion()` (l. 97-105), `motivoRechazoRecuperacion()` (l. 108-113), `cambiarContrasena()` (l. 115). No hay columna de motivo de suspensión en `usuario`.
- Dónde se comprueba hoy el estado:

| Punto | Archivo:línea | ¿Mira `estado`? |
|---|---|---|
| Login | `Usuario.java:101-103` (`CUENTA_NO_ACTIVA`), invocado desde `InicioSesionService.java:111` | **Sí.** 401 genérico, auditoría `LOGIN_FALLIDO motivo=CUENTA_NO_ACTIVA`, cuenta como fallo del limitador. Probado: `UsuarioTest:76`, `InicioSesionServiceTest:263`, `SesionControllerIT:311`. |
| Recuperación (HU-04) | `RecuperacionContrasenaService.java:96` (solicitar) y `:170` (validar/confirmar, vía `motivoRechazoRecuperacion`) | **Sí.** No emite token a suspendidas y rechaza un token emitido antes de la suspensión (`RecuperacionContrasenaControllerIT:536, 704, 754`). |
| Validación de sesión | `SesionService.java:42-52` | **No.** Busca la sesión por hash, comprueba `estaVigente`, carga el usuario y devuelve `UsuarioAutenticado` sin mirar el estado. Pendiente anotado para HU-09 en `progress/history.md:235-236` y `progress/review_HU-03.md:141`. |
| `GET /api/sesiones/actual` | `SesionService.java:54-59` | No. |
| Habilitación del arrendador (HU-07) | `CuentaServiceImpl.java:59-71` | No (solo rol, correo verificado e identidad). |
| Cola de HU-06 | `PerfilArrendadorRepository.java:31-43` (`u.estado = ACTIVA`) y `CuentaServiceImpl.arrendadorActivo` (l. 138-142) | **Sí**: un arrendador suspendido desaparece de la cola y sus decisiones dan 404 (HU-06 S10, R22, R37). Al reactivarlo, si seguía `EN_REVISION`, vuelve a la cola sin hacer nada. |

- **¿Dónde guardar el motivo?** Nada lo prevé hoy (`docs/modelo-datos.md:13` no tiene columna de motivo; no hay tabla). Opciones en §12 (riesgo 3).

## 3. Sesiones (HU-03 / HU-04)

- `SesionRepository.revocarTodas(Long usuarioId)` — `…/cuentas/repository/SesionRepository.java:16-18`: `@Modifying(flushAutomatically = true, clearAutomatically = true)` `update Sesion s set s.revocada = true where s.usuarioId = :usuarioId and s.revocada = false`; devuelve cuántas revocó. Usa el índice `ix_sesion_usuario` (`V1_4__sesion.sql:16`, creado para HU-04/HU-09, `specs/HU-03/design.md:30` DT-10).
- Uso existente: `RecuperacionContrasenaService.java:140`, dentro de la misma transacción que el cambio de contraseña, con detalle de auditoría `sesionesRevocadas=<n>` (l. 141). Patrón directo para HU-09.
- `clearAutomatically = true` limpia el contexto de persistencia: cualquier entidad cargada antes en la misma transacción (p. ej. el `Usuario` que se acaba de suspender) queda *detached*; si se cambia el estado **antes** de llamar a `revocarTodas`, el `flushAutomatically` lo escribe primero. El orden importa (cambiar estado → revocar → leer de nuevo si hace falta).
- `revocarTodas` es de `cuentas`: `administracion` no puede llamar al repositorio; tiene que ir en el método nuevo de `CuentaService`.
- **¿Una sesión abierta de un suspendido sigue sirviendo?** Sí, hasta que se revoque o pase 30 min sin uso: `SesionService.validar` solo mira `revocada` y `expira_en` (`Sesion.estaVigente`, `Sesion.java:55-57`) y además la **renueva** en cada solicitud (l. 49). Con `revocarTodas` en la suspensión queda cubierto el caso normal, pero hay una carrera: `InicioSesionService.iniciar` lee el usuario fuera de transacción (l. 77-78) y crea la sesión después en otra transacción (l. 88-91); si la suspensión se confirma entre ambos pasos, nace una sesión vigente de una cuenta suspendida que nadie revoca y que se renueva indefinidamente. Ver riesgo 4.
- Frontend: cualquier 401 con token borra el token y avisa a los suscriptores (`3_frontend/src/api/cliente.js:22-27`), así que el suspendido es expulsado en su siguiente solicitud sin cambios en el cliente.

## 4. Espacios y catálogo (HU-07 / HU-08; HU-10 no existe)

- Estados: `EstadoEspacio { BORRADOR, PUBLICADO, PAUSADO, BAJA, OCULTO }` (`…/espacios/domain/EstadoEspacio.java`; CHECK en `V2_2__espacio.sql:50`). Hoy solo existen las transiciones `BORRADOR → PUBLICADO` (`Espacio.publicar`, l. 109-115). `docs/architecture.md:244-246`: "Si el arrendador está suspendido, sus espacios no se muestran pero conservan su estado" → **HU-09 no debe cambiar `espacio.estado`** (y no usar `OCULTO`, que es de moderación, HU-27).
- `espacio.arrendador_id` es FK a `perfil_arrendador(usuario_id)` (`V2_2__espacio.sql:38`), índice `ix_espacio_arrendador`.
- **Catálogo público: no existe.** No hay módulo `busqueda` ni ruta pública de espacios (`ConfiguracionSeguridad.java:43-56`: el único `permitAll` de espacios es `GET /api/fotos/*`). `EspacioController` y `CalendarioEspacioController` son solo del dueño (`/api/arrendadores/actual/espacios…`). HU-10 (búsqueda, Sebastian Garate) y HU-11 (ficha, Adriana Callupe) están `pending` en el mismo sprint 3 (`feature_list.json:193-232`).
- Superficies "públicas" que hoy **no** filtran por estado del arrendador:

| Superficie | Archivo:línea | Qué filtra hoy | Qué faltaría |
|---|---|---|---|
| Foto de un espacio | `…/espacios/service/FotoEspacioService.java:117-131` (`ver`) | `PUBLICADO` → pública con `Cache-Control: public, max-age=3600` (`FotoPublicaController.java:23, 37`); si no, solo su dueño. | Tratar como no pública si el arrendador no está `ACTIVA` (404 al público). Pendiente explícito para HU-09 en `progress/history.md:426-429` y `progress/review_HU-07.md:170`. Las copias en caché pueden durar hasta 1 h. |
| Horas libres | `…/espacios/service/DisponibilidadServiceImpl.java:49-65` (`horasLibres`), contrato en `DisponibilidadService.java:6-13` | Espacio existe y `PUBLICADO`. | Devolver lista vacía si el arrendador no está `ACTIVA`, para que HU-10/11/12 lo hereden. Hoy no tiene consumidores. |
| Búsqueda / ficha | (no existen) | — | HU-10 CA1 ya exige "arrendadores no suspendidos" (`docs/historias/HU-10.md:14`, RF-C-01, RN-15). Lo ideal es que HU-09 deje el método listo y documentado para que HU-10/HU-11 lo usen. |

- **Puertos y dirección de dependencias** (`docs/architecture.md:114-149`): `comun ← cuentas ← espacios ← reservas → pagos → cuentas`, `busqueda → espacios/reservas`, `administracion → (todos)`. Puertos existentes: `comun.ValidadorSesion` (cuentas), `cuentas.ParticipanteDatosPersonales` (HU-22, aún no existe en código), `espacios.ReservasDelEspacio` (reservas; existe la interfaz, `…/espacios/service/puertos/ReservasDelEspacio.java`), `pagos.ReservasPorCobrar`, `pagos.ReservasLiquidables`, `pagos.ReclamosAbiertos`. **No hay ningún puerto entre `espacios` y `cuentas` porque `espacios → cuentas` está permitido**: `EspacioService` ya usa `CuentaService.habilitacionArrendador` (`EspacioService.java:62, 87, 159`).
- Consecuencias para HU-09 (sin ciclos):
  - `espacios` puede preguntar a `cuentas` por el estado del arrendador vía un método nuevo de `CuentaService` (dirección permitida). **No** al revés: `cuentas` no puede llamar a `espacios`.
  - `administracion` puede llamar a `espacios` (y a `cuentas`) si se elige marcar los espacios al suspender.
  - Un *join* JPQL desde `EspacioRepository` a la entidad `Usuario` acoplaría `espacios` al dominio de `cuentas` (contradice "nunca el repositorio de otro módulo", `docs/architecture.md:280`). El precedente de *join* por ids (`PerfilArrendadorRepository.findEnRevision`) es dentro del mismo módulo.
- Consultas a tocar en HU-09 (módulo `espacios`): `FotoEspacioService.ver` y `DisponibilidadServiceImpl.horasLibres`. En HU-10/HU-11 (módulo `busqueda`): la consulta paginada del catálogo y la ficha. Ver riesgo 6 para el mecanismo.

## 5. Búsqueda de cuentas (CA1)

- **No existe ningún método de búsqueda.** `UsuarioRepository` (`…/cuentas/repository/UsuarioRepository.java:9-12`) solo tiene `findByCorreo(String correoNormalizado)` (igualdad exacta). `PerfilArrendadorRepository` tiene `findByHashDocumento` (HMAC; no sirve para búsqueda libre).
- Dónde vive cada dato:
  - `usuario.correo` `VARCHAR(254)`, único, en claro y **normalizado en minúsculas** (CHECK `correo = lower(btrim(correo))`, `V1_1:11`; `Usuario.normalizarCorreo`, l. 87-89).
  - `usuario.nombre` `VARCHAR(100)` **anulable**, en claro (`V1_3:3-6`). Solo lo llenan los `ARRENDADOR` (`Usuario.registrarArrendador`, l. 70-77); `USUARIO` (HU-01) y `ADMIN` (HU-06) no tienen nombre hasta HU-20. Se guarda con `trim()` pero sin normalizar mayúsculas ni tildes.
  - `usuario.telefono` en claro (solo arrendadores).
  - `perfil_arrendador.razon_social`, `numero_documento`, `direccion_fiscal`: **cifrados** AES-GCM (`V1_3:8-10`) → no se pueden buscar con SQL.
  - `perfil_arrendador.estado_identidad` útil para mostrar en el detalle.
- Paginación / límites existentes: **ninguna** consulta del backend pagina hoy (HU-06 S6 decidió "sin paginación"). El único límite de producto es "páginas de 20" para el catálogo (RNF-USA-01, HU-10). No hay `Pageable` en ningún repositorio. Spring Data permite `Limit`/`Pageable`/`findTop20By…` sin dependencias nuevas.
- No hay extensión `unaccent` ni índices de texto; `usuario` no tiene índice por `nombre`. El volumen de UAT es pequeño (argumento usado por HU-06 DT-8).
- El rate limiting existente (`…/comun/limite/`) es por clave en memoria; no hay límite para rutas admin (no lo pide la historia).

## 6. Correo

- `NotificadorCorreo` (`…/cuentas/service/NotificadorCorreo.java:7-16`): `notificarVerificacion(correo)` (HU-01), `notificarRecuperacion(correo, enlace)` (HU-04), `notificarIdentidadAprobada(correo)` y `notificarIdentidadRechazada(correo, motivo)` (HU-06). Comentario: HU-25 lo moverá a `comun/correo`; ningún correo lleva documentos (RNF-SEG-16).
- Simulado (`…/cuentas/service/NotificadorCorreoSimulado.java`): `@Component`, solo `log.info` genérico; nunca escribe correo ni motivo (l. 37-46, Ley 29733, HU-06 R45).
- Envío después del commit (HU-06): `CuentaServiceImpl.despuesDelCommit` (`CuentaServiceImpl.java:155-167`) registra una `TransactionSynchronization.afterCommit`; un fallo se registra con `log.warn` sin datos y no revierte. Se captura el correo/motivo en variables antes (l. 114-115, 125-127). Es `private static` en `CuentaServiceImpl`: si el envío de suspensión vive en `cuentas`, se reutiliza directamente; si viviera en `administracion`, habría que duplicarlo o extraerlo.
- Contraste: HU-04 notifica **dentro** de la transacción (`RecuperacionContrasenaService.java:106`), pendiente para HU-25 (`progress/history.md:340-343`). HU-09 debería seguir el patrón de HU-06 (después del commit).
- Agregar: `notificarCuentaSuspendida(String correo, String motivo)` en la interfaz + simulado (y actualizar los dobles `NotificadorFalso` de las IT que implementan la interfaz, como hizo HU-04: `progress/history.md:329-330`). **Reactivación:** la historia no lo pide; RNF-USA-04 cita "suspensión" (`docs/requerimientos.md:195`), no reactivación. Ver riesgo 9.
- Cuentas sin correo real: hasta HU-25 todo es simulado; una cuenta `USUARIO` tiene correo siempre (es su login), así que el aviso aplica a cualquier rol.

## 7. Auditoría

- `AuditoriaService.registrar(EventoAuditoria)` (`…/comun/auditoria/AuditoriaService.java:20-23`), `@Transactional` (REQUIRED: se une a la transacción del llamador; si esta revierte, la auditoría también). `EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle)` (`EventoAuditoria.java:3-5`). Tabla `auditoria` (`V1_2__auditoria.sql`): `usuario_id` FK, `accion VARCHAR(60)`, `entidad VARCHAR(60)`, `entidad_id`, `ip VARCHAR(45)`, `fecha TIMESTAMPTZ`, `detalle TEXT`.
- Acciones existentes: `REGISTRO_USUARIO`, `REGISTRO_CORREO_EXISTENTE`, `REGISTRO_ARRENDADOR`, `REGISTRO_DOCUMENTO_EXISTENTE`, `LOGIN_EXITOSO`, `LOGIN_FALLIDO`, `LOGIN_BLOQUEADO`, `CIERRE_SESION`, `RECUPERACION_SOLICITADA`, `RECUPERACION_NO_EMITIDA`, `RECUPERACION_RECHAZADA`, `CONTRASENA_RESTABLECIDA`, `CARGA_DOCUMENTO_IDENTIDAD`, `CARGA_DOCUMENTO_RECHAZADA`, `CREACION_ADMIN`, `IDENTIDAD_APROBADA`, `IDENTIDAD_RECHAZADA` (HU-06), `CREACION_ESPACIO`, `EDICION_ESPACIO`, `PUBLICACION_ESPACIO`, `FOTO_ESPACIO_AGREGADA/ELIMINADA/RECHAZADA`, `HORARIO_ESPACIO_ACTUALIZADO`, `BLOQUEO_FECHA_CREADO/ELIMINADO`. Ninguna de suspensión.
- ¿Basta la auditoría para "admin, cuenta, acción, motivo, fecha"? Técnicamente sí (`usuario_id` = admin, `entidad='usuario'`, `entidad_id` = cuenta, `accion`, `fecha`, y el motivo cabría en `detalle TEXT`). Pero **HU-06 decidió S16/R43** (`specs/HU-06/requirements.md:48, 296-298`; código `RevisionIdentidadService.java:87`): "el motivo vive solo en `revision_identidad` y en el perfil: la auditoría no lo duplica"; el detalle es `decision=<D>;revision=<id>`. Coherencia → tabla de historial propia con el motivo y auditoría que la referencia por id.
- Detalle sugerido (a decidir en el spec): `accion=SUSPENSION;registro=<id>;sesionesRevocadas=<n>` (el `sesionesRevocadas` imita `RecuperacionContrasenaService.java:141`).

## 8. Base de datos

- Migraciones existentes (exactas): `V1_1__usuarios.sql`, `V1_2__auditoria.sql`, `V1_3__perfil_arrendador.sql`, `V1_4__sesion.sql`, `V1_5__token.sql`, `V2_1__documento_identidad.sql`, `V2_2__espacio.sql`, `V2_3__revision_identidad.sql`, `V2_4__disponibilidad.sql` (más `.gitkeep`).
- Siguiente número: **`V3_1`** según `docs/conventions.md:84-89` (sprint 3 de `feature_list.json:180`; ninguna `V3_*` en `develop` ni en las ramas listadas por `git branch -a`). HU-10, HU-11 y HU-12 son también sprint 3: quien llegue segundo a `develop` renombra la suya antes del merge. `spring.flyway.out-of-order=true` ya está (`application.properties:18`). Una `V3_1` que referencie `usuario` (V1_1) o altere `espacio` (V2_2) cumple "no depender de una migración de número mayor" (`docs/conventions.md:90-93`).
- `docs/modelo-datos.md`: `usuario.estado (ACTIVA, SUSPENDIDA, ELIMINADA)` con HU-09 entre sus historias (l. 13) y `sesion` con HU-09 (l. 17, por la revocación). **No prevé** columna de motivo ni tabla de suspensiones; el grupo "Administración" solo tiene `revision_identidad` (l. 34). La cabecera (l. 3-7) exige justificar en `design.md` y actualizar el archivo en el mismo PR si se agrega algo.
- Precedente de FK para tests: `sesion` usa `ON DELETE CASCADE` "porque las pruebas borran usuarios" (`V1_4:3`); `revision_identidad.admin_id` no tiene cascada.

## 9. Frontend admin (HU-06)

- Páginas: `3_frontend/src/pages/administracion/ColaIdentidades.jsx`, `RevisarIdentidad.jsx` (patrón: `useSesion`, `esAdmin = sesion?.rol === 'ADMIN'`, `<Navigate to="/panel">` si no lo es, l. 24-26 y 53; `Modal` con `Campo tipo="textarea"` para motivo, `variante="peligro-confirmar"`, l. 173-191; error por campo `motivo` desde `falla.errores`, l. 17-19 y 70-80), `textosAdministracion.js` (`NOMBRES_DOCUMENTO`, `fechaHora`).
- API: `3_frontend/src/api/administracion.js` (constante `ARRENDADORES = '/api/admin/arrendadores'`, `peticion(...)` con `metodo`/`cuerpo`). HU-09 agrega aquí sus funciones (buscar, detalle, suspender, reactivar).
- `Modal`: `3_frontend/src/components/ui/Modal.jsx` (props `abierto`, `titulo`, `onConfirmar`, `onCerrar`, `children`, `textoConfirmar`, `variante`, `cargando`); `docs/frontend.md:177` ya lo cita para "suspender".
- Rutas: `3_frontend/src/App.jsx:150-166` (`/admin/identidades`, `/admin/identidades/:id`, envueltas en `RutaProtegida`). HU-09 agregaría p. ej. `/admin/cuentas` y `/admin/cuentas/:id`.
- `Panel.jsx` (`3_frontend/src/pages/cuentas/Panel.jsx`): texto del ADMIN "Revisa las identidades de los arrendadores." (l. 11) y un único enlace "Revisar identidades" (l. 48-52) → agregar enlace "Buscar cuentas" y ajustar la descripción.
- `EtiquetaEstado` (`3_frontend/src/components/ui/EtiquetaEstado.jsx:5-16`): tiene identidad y espacio; **faltan `ACTIVA`, `SUSPENDIDA`, `ELIMINADA`** (sin choque de claves con las existentes). Colores en `docs/frontend.md:136`: Cuenta → éxito `ACTIVA` (correo verificado), error `SUSPENDIDA`, neutro `ELIMINADA` / correo pendiente.
- No hay mapeo de nombres de rol para mostrar (solo `TEXTOS_POR_ROL` en `Panel.jsx`); HU-09 necesitaría uno en `textosAdministracion.js`.
- Expulsión del suspendido: ya cubierta por el manejo global de 401 (`api/cliente.js:22-27`).

## 10. Requerimientos (`docs/requerimientos.md`)

- Matriz §7, fila HU-09 (l. 247): `F | R1 · S3 (9) | RF-F-04…06 | SEG-02, 04, 12, 18 | RN-01, RN-15 | HU-06 | Jorge Quispe | Kevin Chan | —`.
- RF (l. 130-132):
  - **RF-F-04** Permitir buscar una cuenta por correo o nombre y ver sus datos, rol y estado (CA1).
  - **RF-F-05** Permitir suspender una cuenta con motivo: se invalidan sus sesiones, no puede iniciar sesión, se ocultan sus espacios y recibe un correo con el motivo (CA2).
  - **RF-F-06** Permitir reactivar una cuenta suspendida: recupera el acceso y sus espacios vuelven a ser visibles (CA3).
- RNF:
  - **RNF-SEG-02** (l. 147) tokens invalidados en el backend al … suspender la cuenta.
  - **RNF-SEG-04** (l. 149) MFA para admin; hasta HU-17, panel solo desde IPs permitidas.
  - **RNF-SEG-12** (l. 157) auditoría quién/qué/cuándo/IP; incluye "suspensiones".
  - **RNF-SEG-18** (l. 163) roles validados en backend en cada solicitud; rol nunca del cliente.
  - Relacionados no citados en la fila: RNF-SEG-13 (errores genéricos, l. 158), RNF-USA-04 (avisos por correo, incluye "suspensión", l. 195).
- RN:
  - **RN-01** (l. 210) … "Suspendida: no inicia sesión y sus espacios se ocultan."
  - **RN-15** (l. 224) "La búsqueda solo muestra espacios publicados, no pausados ni ocultos, de arrendadores no suspendidos." (cita HU-10 CA1, HU-21 CA1, HU-09 CA2, HU-27 CA3).
- §8 componentes transversales (l. 276-284): Auditoría (la reutiliza); Gestión de sesión y tokens (HU-09 explícitamente entre quienes la reutilizan, l. 279); Envío de correo detrás de interfaz propia (HU-09 en la lista, l. 280).
- §9 pendientes: el 4 (control de IPs, l. 291) está **resuelto** por HU-06 D2 y dice "HU-09 y HU-27 reutilizan el mismo filtro". Ningún otro pendiente abierto (3, 6, 7-MFA, 8) toca HU-09.
- Otras historias que dependen de HU-09: HU-27 (moderación, sprint 8, depende de HU-09; `requerimientos.md:265`). HU-17 "reemplaza o complementa el control de IPs de HU-06 y HU-09" (`docs/historias/HU-17.md:23`).

## 11. Decisiones previas que condicionan HU-09

- **HU-03**: "cuenta habilitada para iniciar sesión" incluye `estado = ACTIVA` (`specs/HU-03/requirements.md:32-33`); R12 suspendida → mismo 401 que credenciales (`:109-111`); DT-8 mismo mensaje sin revelar el estado (`specs/HU-03/design.md:28`); DT-10 varias sesiones por usuario + `ix_sesion_usuario` para que HU-09 revoque todas (`:30`); R26 sesión revocada → 401 *sesión inválida*. Observación no resuelta: `validar` no mira el estado (`progress/review_HU-03.md:141`, `progress/history.md:235-236`).
- **HU-04**: S2/DT-7 "cuenta habilitada" = `motivoRechazoInicioSesion()` vacío, aplicado al solicitar, validar y confirmar (`specs/HU-04/design.md:22`); S7 revocar sesiones **en la misma transacción** (`progress/history.md:314-315`); `revocarTodas` existe. El admin no recupera contraseña hasta HU-17 (HU-06 R6) → un admin suspendido o que pierde el acceso no tiene vía de recuperación propia.
- **HU-06**: D1 admin solo desde IP permitida; D2 filtro reutilizable bajo `/api/admin/`; S2/R41 rol validado en `administracion` **y** en `CuentaService`; S3 correos después del commit; S10 la cola solo considera `ACTIVA` y "la suspensión es de HU-09" (`specs/HU-06/requirements.md:42`); S15 el comando `CreadorAdmin` **no modifica** cuentas existentes (`:47`) → no sirve para reactivar un admin suspendido; S16/R43 motivo fuera de la auditoría. HU-06 R37: decidir sobre una cuenta no `ACTIVA` → 404 (un arrendador suspendido con identidad en revisión no se puede decidir hasta reactivarlo).
- **HU-07**: pendiente explícito "(HU-09) `FotoEspacioService.ver` no oculta las fotos de un arrendador `SUSPENDIDA`" (`progress/history.md:426-429`). Las fotos de `PUBLICADO` se sirven con `max-age=3600`.
- **HU-08**: `DisponibilidadService.horasLibres` es "la única entrada pública" de `espacios` para HU-10/11/12 (`docs/architecture.md:146-149, 161`); su contrato solo dice "no está `PUBLICADO` → vacía". Para que HU-10/11/12 ignoren espacios de suspendidos sin repetir la regla, HU-09 puede ampliar ese contrato.
- **Nada en las specs** decide: suspender a un admin, suspenderse a uno mismo, ni qué pasa con reservas pendientes/confirmadas (no existen reservas: HU-12 `pending`). `docs/architecture.md:237` dice solo "Suspendida: nada; no inicia sesión y sus espacios se ocultan".
- `progress/HU-09.md`: Jorge Quispe trabaja desde la laptop de Eduardo Saldaña con su permiso; `./init.sh --rapido` verde (385 unitarias).

## 12. Riesgos y decisiones abiertas para el spec

1. **Rutas REST.** Opciones: (a) `GET /api/admin/cuentas?q=…` (búsqueda), `GET /api/admin/cuentas/{id}` (detalle), `POST /api/admin/cuentas/{id}/suspension` (cuerpo `{motivo}`, 201), `POST /api/admin/cuentas/{id}/reactivacion` (201); (b) `PATCH /api/admin/cuentas/{id}` con `{estado, motivo}`. (c) búsqueda por `POST /api/admin/cuentas/busquedas` con el texto en el cuerpo. **Recomendación:** (a), mismo estilo que HU-06 (dos POST que crean un registro de historial → 201; `no-store` en los GET). El correo buscado en el query string queda en logs de proxies/ALB; hoy no hay access log en Tomcat, así que se acepta y se deja escrito; si el dueño prefiere no exponerlo, (c).

2. **Quién se puede suspender (E).** Opciones: (a) cualquier cuenta `ACTIVA`, incluido un admin y uno mismo; (b) solo `USUARIO` y `ARRENDADOR`; (c) admins sí, pero nunca a uno mismo. Riesgo: un admin suspendido no tiene recuperación (HU-06 R6) ni el comando `CreadorAdmin` lo reactiva (S15); si se suspende al único admin, nadie puede reactivar. **Recomendación:** (b): suspender o reactivar una cuenta `ADMIN` → 404/400 genérico (y por tanto tampoco a uno mismo). Una cuenta `ELIMINADA` no se suspende ni se reactiva (404). Suspender una ya `SUSPENDIDA` o reactivar una `ACTIVA` → 400 "ya está …" (como *ya revisada* de HU-06).

3. **Dónde guardar el motivo y el historial (R).** Opciones: (a) tabla nueva en `administracion`, p. ej. `suspension_cuenta (id, usuario_id FK usuario, admin_id FK usuario sin cascada, accion SUSPENSION|REACTIVACION, motivo VARCHAR(500), fecha)` con CHECK "SUSPENSION exige motivo no en blanco", calcada de `revision_identidad`; (b) columnas `usuario.motivo_suspension`/`suspendida_en` en `cuentas` + auditoría; (c) solo auditoría con el motivo en `detalle`. (c) contradice S16; (b) pierde el historial de suspensiones/reactivaciones repetidas. **Recomendación:** (a), estado vigente en `usuario.estado` (lo cambia `cuentas`), historial en `administracion`, todo en una transacción (como HU-06 DT-5). Decidir: `ON DELETE CASCADE` en `usuario_id` (las IT borran usuarios; HU-22 anonimiza) y si la reactivación pide motivo (recomendado: opcional o sin motivo; la historia solo exige motivo para suspender). El detalle de CA1 puede mostrar el último motivo y fecha leyendo esta tabla.

4. **`SesionService.validar` no mira el estado.** Opciones: (a) solo `revocarTodas` al suspender; (b) además, `validar` rechaza (vacío → 401) si `usuario.estado != ACTIVA`. Con (a) queda la carrera login-vs-suspensión descrita en §3 (sesión creada después de la revocación, renovada sin fin). **Recomendación:** (a) + (b). Es un cambio en `cuentas` de HU-03 (avisar en el PR; tests de HU-03 no deberían romperse: ninguno valida una sesión de cuenta suspendida). Cubre también RNF-SEG-02 y deja `revocarTodas` como limpieza explícita.

5. **Concurrencia.** Dos admins suspenden/reactivan la misma cuenta a la vez, o suspensión mientras se reactiva. `UsuarioRepository` no tiene método con bloqueo. **Recomendación:** `UsuarioRepository.findParaActualizar(id)` con `PESSIMISTIC_WRITE` (patrón de `PerfilArrendadorRepository.findParaActualizar`), dentro de la transacción: el segundo espera, ve el estado nuevo y recibe el 400 de "ya está suspendida/activa". Ojo con `revocarTodas` (`clearAutomatically`): cambiar el estado antes de revocar.

6. **Ocultar los espacios del suspendido (CA2/CA3) sin ciclos.** Opciones:
   (a) **Consultar `cuentas` al leer**: nuevo método en `CuentaService`, p. ej. `boolean cuentaActiva(Long usuarioId)` y/o `Set<Long> arrendadoresNoActivos()` (sin exigir rol: lo usan rutas públicas); `espacios` lo usa en `FotoEspacioService.ver` y `DisponibilidadServiceImpl.horasLibres`, y HU-10/HU-11 en sus consultas (`e.arrendadorId not in :ids`, que respeta la paginación de 20). Dirección `espacios → cuentas` permitida, sin migración en `espacio`, y "conservan su estado" se cumple literalmente. Cuidado: `not in` con colección vacía en JPQL (Hibernate 6) debe probarse o resolverse con una rama.
   (b) **Marcar en `espacios` al suspender**: columna nueva (p. ej. `espacio.arrendador_suspendido BOOLEAN`) y un método público de `espacios` que `administracion` llama al suspender/reactivar; las consultas de `espacios`/`busqueda` filtran por la columna. Pagina bien y no consulta `cuentas` en cada lectura, pero duplica estado (riesgo de desincronización si otra vía cambia `usuario.estado`) y altera `espacio` (`V3_n`).
   (c) *Join* JPQL/nativo de `espacio` con `usuario` dentro de `espacios`/`busqueda`: más simple, pero acopla al dominio de otro módulo (contra `docs/architecture.md:280`).
   **Recomendación:** (a). HU-09 implementa el método en `CuentaService`, lo aplica en `FotoEspacioService.ver` (el público recibe 404; el dueño no tiene sesión porque está suspendido) y en `horasLibres` (lista vacía), amplía el contrato de `DisponibilidadService` y documenta en `docs/architecture.md` (tabla de `CuentaService` y sección de disponibilidad) que HU-10/HU-11/HU-12 deben filtrar con él. **Coordinar con Sebastian Garate (HU-10) y Adriana Callupe (HU-11)**, que trabajan el mismo sprint. Aceptar que una foto ya cacheada siga hasta 1 h (`max-age=3600`) o, si no se acepta, bajar ese tiempo (cambio de HU-07; no recomendado).

7. **Qué ve el admin en la búsqueda (CA1, I).** Opciones de alcance: (a) coincidencia exacta de correo + nombre contiene; (b) correo contiene + nombre contiene, sin distinguir mayúsculas; (c) además razón social (imposible: cifrada). **Recomendación:** (b) con `lower(...) like lower(:patron)` escapando `%`, `_` y `\`, texto recortado de 3 a 100 caracteres (400 si no), **máximo 20 resultados** ordenados por correo (sin paginación, como HU-06 S6; con un indicador de "hay más, afina la búsqueda" si se piden 21). Sin tildes-insensibilidad (no hay `unaccent`; aceptado y escrito). Resultado resumido: `id`, `nombre` (puede ser nulo), `correo`, `rol`, `estado`. Detalle (`GET …/{id}`): además `telefono`, `correoVerificado`, `estadoIdentidad` (solo arrendador), último motivo/fecha de suspensión; **nunca** documentos, número de documento, razón social ni dirección fiscal (minimización; esos datos ya los ve HU-06 cuando aplica). ¿Incluir cuentas `ELIMINADA` y `ADMIN` en los resultados? Recomendación: sí se listan (para ver su estado) pero sin acciones; decidir con el dueño. La búsqueda no se audita (consulta), igual que HU-06 no audita la cola; decidir si se audita el detalle (recomendado: no).

8. **Orden de operaciones y correo (CA2).** Recomendación: en una transacción `@Transactional` de `administracion`: `CuentaService.suspenderCuenta(admin, id, motivo)` (rol ADMIN otra vez, bloqueo de la fila, `Usuario.suspender()`, `revocarTodas`, devuelve n.º de sesiones revocadas y registra el correo `afterCommit`) → guardar fila de historial → auditar `CUENTA_SUSPENDIDA` sin motivo. Si algo falla, rollback y sin correo (HU-06 R40). `Usuario` gana `suspender()`/`reactivar()` con guarda de estado (`IllegalStateException` si no aplica) para que la regla viva en el dominio.

9. **Correo de reactivación.** La historia no lo pide; RNF-USA-04 solo cita "suspensión". Opciones: (a) no enviar; (b) enviar `notificarCuentaReactivada(correo)`. **Recomendación:** (b), es un método más del puerto con el mismo patrón y evita que el usuario no sepa que recuperó el acceso; si el dueño prefiere ceñirse a la historia, (a) y dejarlo para HU-25. Marcarlo como supuesto.

10. **Arrendador con reservas pendientes o confirmadas / usuario con reservas.** Hoy no existen reservas (HU-12 `pending`), así que nada que hacer en código. Riesgo futuro: un arrendador suspendido con reservas `CONFIRMADA` futuras (¿se cancelan con reembolso 100 % como RN-16?) o un `USUARIO` suspendido con `PENDIENTE_PAGO`. **Recomendación:** fuera del alcance de HU-09; dejarlo escrito como supuesto ("la suspensión no toca reservas; HU-12/HU-15 deciden") y que `ReservasDelEspacio`/`horasLibres` simplemente no ofrezcan bloques nuevos del suspendido (punto 6). Avisar a Eduardo Saldaña (HU-12): reservar debe rechazar espacios de arrendadores no activos (si HU-12 usa `horasLibres`, lo hereda).

11. **Otros efectos de la suspensión.** Tokens de recuperación pendientes (`token`): ya quedan inútiles porque `comprobar` rechaza cuentas no habilitadas (`RecuperacionContrasenaService.java:170`); no hace falta anularlos (opcional: `anularPendientes` por higiene). Limitador de login: sin cambios. Cola de HU-06: el suspendido sale solo; al reactivar vuelve si seguía `EN_REVISION` (aceptable, sin código).

12. **Rol en `administracion`.** `exigirAdmin` está duplicado (`RevisionIdentidadService`, `CuentaServiceImpl`). Opciones: (a) duplicarlo otra vez en el servicio nuevo; (b) extraerlo a `comun/seguridad` (p. ej. método estático en `UsuarioAutenticado` o una clase `ReglasAdmin`) y usarlo en ambos servicios de `administracion`. **Recomendación:** (a) para no tocar HU-06 en una historia S, o (b) si el dueño acepta un refactor mínimo con los tests de HU-06 en verde. HU-27 lo volverá a necesitar.

13. **Migración y choque de número.** **Recomendación:** `V3_1__suspension_cuenta.sql` (o el nombre que fije el spec), solo dependiente de `V1_1`; antes del merge actualizar `develop` y renombrar si HU-10/11/12 tomaron `V3_1`. Actualizar en el mismo PR `docs/modelo-datos.md` (tabla nueva en "Administración", restricción en "Restricciones clave"), `docs/architecture.md` (métodos nuevos de `CuentaService`, contrato ampliado de `DisponibilidadService`, nota de que HU-10/11/12 filtran por arrendador activo), `docs/frontend.md` (estados de cuenta en `EtiquetaEstado`).

14. **Frontend.** Recomendación: páginas `pages/administracion/BuscarCuentas.jsx` (`Campo` de texto + lista con `EtiquetaEstado`) y `DetalleCuenta.jsx` (datos, rol, estado, último motivo; botón `peligro` "Suspender cuenta" que abre `Modal` con `Campo tipo="textarea"` y `peligro-confirmar`; botón "Reactivar cuenta" con `Modal` `primario`), rutas `/admin/cuentas` y `/admin/cuentas/:id`, enlace en `Panel.jsx` para `ADMIN`, estados `ACTIVA`/`SUSPENDIDA`/`ELIMINADA` en `EtiquetaEstado` y nombres de rol en `textosAdministracion.js`. Sin componentes nuevos en el kit.

15. **Qué se puede probar de CA2/CA3 "sus espacios se ocultan/vuelven" mientras no exista catálogo.** Recomendación: IT sobre `GET /api/fotos/{uuid}` (404 al público con arrendador suspendido; 200 de nuevo al reactivar) y sobre `DisponibilidadService.horasLibres` (vacía / con horas), más una IT que compruebe que `espacio.estado` no cambia. Dejar escrito en el spec que la búsqueda y la ficha lo heredan cuando HU-10/HU-11 usen el método de `CuentaService`, para que el reviewer no lo marque como CA incompleto.
