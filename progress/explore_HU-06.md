# Exploración — HU-06 · Revisión de identidad de arrendadores

> Explorador de solo lectura, 2026-10-28, rama `feature/HU-06-revision-identidad` (desde `develop`
> en `02a83b6`). Rutas relativas a la raíz del repo.
> `B` = `2_Backend/src/main/java/pe/edu/ulima/espacioya`, `T` = `2_Backend/src/test/java/pe/edu/ulima/espacioya`,
> `M` = `2_Backend/src/main/resources/db/migration`, `F` = `3_frontend/src`.

## Archivos analizados

- `docs/historias/HU-06.md`, `docs/architecture.md`, `docs/conventions.md`, `docs/modelo-datos.md`,
  `docs/requerimientos.md` (§2, RF-F, RNF-SEG, RNF-USA-04, RN-01, §7, §8, §9), `docs/frontend.md`
- `specs/HU-02/`, `specs/HU-03/`, `specs/HU-04/`, `specs/HU-05/`, `specs/HU-07/` (decisiones que tocan a HU-06),
  `progress/history.md`, `progress/HU-06.md`, `feature_list.json`
- `B/comun/**`, `B/cuentas/**`, `B/espacios/service/EspacioService.java` (uso de `CuentaService`),
  `B/EspacioYaApplication.java`, `application.properties`, `2_Backend/.env.example`, `M/*.sql`
- Tests: `T/cuentas/controller/SesionControllerIT.java`, `T/cuentas/service/NotificadorCorreoSimuladoTest.java`,
  dobles `NotificadorFalso` de 3 IT
- `F/App.jsx`, `F/api/*`, `F/sesion/*`, `F/components/ui/*`, `F/pages/cuentas/Panel.jsx`, `VerificarIdentidad.jsx`,
  `IniciarSesion.jsx`
- `4_Infraestructure/arquitectura-aws.html` (solo para saber si hay proxy delante del backend)
- Ramas remotas (`git ls-tree`) para la numeración de migraciones

---

## 1. HU-05 (documentos de identidad)

### Entidad, tabla y migración

- **Entidad** `B/cuentas/domain/DocumentoIdentidad.java`: `id`, `arrendadorId`, `tipo` (`TipoDocumentoIdentidad`
  = `IDENTIDAD`, `PROPIEDAD`), `rutaArchivo` (clave `identidad/<UUID>`), `tipoMime`, `tamano`, `subidoEn`
  (l. 22-43). Fábrica `registrar(...)` (l. 48-58). Solo getters, sin estado.
- **Tabla** `documento_identidad` en `M/V2_1__documento_identidad.sql` (l. 3-16): FK `arrendador_id →
  perfil_arrendador(usuario_id) ON DELETE CASCADE`, `UNIQUE (arrendador_id, tipo)` (un vigente por tipo),
  `UNIQUE (ruta_archivo)`, CHECK de tipo, mime (`application/pdf`, `image/jpeg`, `image/png`) y tamaño
  (1..5 242 880).
- **Repositorio** `B/cuentas/repository/DocumentoIdentidadRepository.java:11`:
  `findByArrendadorIdOrderByTipoAsc(Long)`. **No hay** consulta por estado ni por fecha.
- Los dos documentos de un envío se guardan con el **mismo `subidoEn`** (`IdentidadArrendadorService.java:183-187`,
  un solo `Instant.now(reloj)`). Un reenvío **borra** las filas anteriores y crea nuevas (l. 180-182), así
  que `subido_en` = fecha del último envío. Es la única marca de tiempo del envío: `perfil_arrendador`
  **no tiene** columna `enviado_en` ni similar.

### Almacenamiento (puerto/adaptador)

- Adapter `B/comun/almacenamiento/AlmacenamientoArchivos.java` (`guardar`, `leer`, `eliminar` por clave) con
  única implementación `AlmacenamientoLocal.java` (directorio `espacioya.almacenamiento.directorio`,
  `application.properties:45`; escritura atómica con `ATOMIC_MOVE`, l. 33-36).
- Servicio `B/comun/almacenamiento/ArchivosPrivados.java`: cifra con `CifradorArchivos` (AES-256-GCM,
  `CIFRADO_LLAVE`) antes de delegar (l. 32-39), valida la forma de la clave `^[a-z]+/<uuid>$` (l. 19-20, 58-60)
  y **`urlFirmada(clave, tipoMime)` es público** (l. 54-56). Es lo que HU-05 dejó listo para HU-06
  (`specs/HU-05/design.md` DT-16, `requirements.md` P10).

### URL firmada de corta duración — **ya existe**

- `B/comun/almacenamiento/FirmadorUrl.java`:
  - `DURACION = Duration.ofMinutes(5)` (l. 31), `RUTA = "/api/archivos"` (l. 32).
  - `firmar(clave, tipoMime)` → `UrlFirmada(url, expiraEn)` (l. 53-58). URL:
    `/api/archivos?clave=…&tipo=…&expira=<epoch s>&firma=<base64url(HMAC-SHA256)>`.
  - Firma HMAC-SHA256 de `clave \n tipoMime \n expira` (l. 71-81) con la llave `ALMACENAMIENTO_LLAVE_FIRMA`
    (`application.properties:46`), obligatoriamente distinta de las de cifrado (l. 42-48).
  - `esValida(...)` comprueba vencimiento con el `Clock` y compara en tiempo constante (`MessageDigest.isEqual`,
    l. 60-69).
- `B/comun/almacenamiento/UrlFirmada.java`: `record UrlFirmada(String url, Instant expiraEn)`.
- **Endpoint que sirve el archivo:** `B/comun/almacenamiento/ArchivoController.java` `GET /api/archivos`
  (l. 33-56), `permitAll` en `ConfiguracionSeguridad.java:49` ("la firma es la autorización"). Descifra y
  responde `inline`, `no-store`, `nosniff`. Cualquier fallo → 404 genérico… **salvo** un
  `UncheckedIOException` de `AlmacenamientoLocal.leer`, que hoy da 500: observación pendiente del review de
  HU-05 asignada explícitamente a "HU-06/HU-07" (`progress/history.md:283-286`).
- El arrendador ya consume estas URL: `IdentidadArrendadorService.aDto` (l. 197-201) → `DocumentoIdentidadDTO`
  (`id, tipo, tipoMime, tamano, subidoEn, url, urlExpiraEn`); el frontend las abre con
  `<a href={urlApi(documento.url)} target="_blank">` (`F/pages/cuentas/VerificarIdentidad.jsx:128`).
  **`DocumentoIdentidadDTO` sirve tal cual para el admin.**

### Paso a `EN_REVISION`

- `IdentidadArrendadorService.enviar` (`B/cuentas/service/IdentidadArrendadorService.java:85-122`): rol y
  correo → estado → valida archivos → guarda cifrados → en `TransactionTemplate` llama a `registrar`
  (l. 173-195), que relee el perfil con **`perfiles.findParaActualizar` (bloqueo `PESSIMISTIC_WRITE`,
  `PerfilArrendadorRepository.java:18-21`)**, reemplaza documentos, audita y llama a
  `perfil.enviarDocumentos()` (l. 193).
- `PerfilArrendador.enviarDocumentos()` (`B/cuentas/domain/PerfilArrendador.java:87-93`): exige
  `puedeEnviarDocumentos()` (`NO_INICIADA` o `RECHAZADA`, l. 83-85), pone `EN_REVISION` y `motivoRechazo = null`.

### Quién cambia `estado_identidad`; métodos para `VERIFICADA`/`RECHAZADA`

- El único método de dominio que cambia el estado es `enviarDocumentos()`. **No existen** métodos de dominio
  para pasar a `VERIFICADA` ni a `RECHAZADA` (ni setters). `crear(...)` fija `NO_INICIADA` (l. 68-80).
- Ningún servicio de `cuentas` cambia el estado salvo `IdentidadArrendadorService` (a `EN_REVISION`).
- `PerfilArrendador.avisoIdentidad()` (l. 95-102) ya tiene los textos de los 4 estados:
  `VERIFICADA → null`; `RECHAZADA → AVISO_IDENTIDAD_RECHAZADA + motivoRechazo` ("Tu identidad fue rechazada.
  Vuelve a subir tus documentos. Motivo: …", l. 29-30). Se muestra en el panel vía `AvisoCuenta`
  (`B/cuentas/service/AvisoCuenta.java`) en `POST /api/sesiones` y `GET /api/sesiones/actual`.

### Motivo de rechazo

- Columna **`perfil_arrendador.motivo_rechazo VARCHAR(500)`** (`M/V1_3__perfil_arrendador.sql:19`), mapeada en
  `PerfilArrendador.java:59-60` (`length = 500`). Hoy nadie la escribe con un valor no nulo.
- `IdentidadDTO.motivoRechazo` solo se devuelve si `RECHAZADA` (`IdentidadArrendadorService.java:132`).
- `docs/modelo-datos.md:16` prevé además la tabla **`revision_identidad` (id, arrendador_id, admin_id,
  decision, motivo, fecha)** para HU-06 (no existe aún).

### ¿Reenvío tras `RECHAZADA`?

- **Sí.** `puedeEnviarDocumentos()` admite `RECHAZADA`; el reenvío reemplaza filas y archivos y limpia
  `motivo_rechazo` (`specs/HU-05/requirements.md` P7, R4-R5; `design.md` DT-8). En `EN_REVISION` o
  `VERIFICADA` se rechaza con 400 (`MENSAJE_ESTADO_NO_PERMITIDO`, DT-9). Consecuencia para HU-06: mientras el
  arrendador está `EN_REVISION` sus documentos **no pueden cambiar** (estables durante la revisión).

---

## 2. Servicios públicos de `cuentas`

- **Existe** `B/cuentas/service/CuentaService.java` (interfaz pública, creada por HU-07) con un solo método:
  `HabilitacionArrendadorDTO habilitacionArrendador(UsuarioAutenticado usuario)` (l. 16), que exige rol
  `ARRENDADOR` (403 si no). Implementación `CuentaServiceImpl.java` (l. 25-37).
- Patrón de uso: `espacios` inyecta `CuentaService` e importa el DTO de `cuentas.dto`
  (`B/espacios/service/EspacioService.java:21-22, 62, 87`). Es decir, **un módulo sí usa DTO de `cuentas.dto`**.
- `docs/architecture.md:56-62, 136-141`: entre módulos solo la interfaz del `service`; dirección
  `administracion → (todos)` (l. 124). "Si una historia necesita un puerto nuevo, lo justifica en su
  `design.md` y lo agrega a esta tabla" (l. 140-141). `specs/HU-05/design.md` DT-16 ya dice: "HU-06 agrega en
  `cuentas` el método de servicio que lista los documentos de un arrendador para `administracion`".

**Lo que falta exponer** (nada de esto existe):

1. Listar arrendadores `EN_REVISION` del más antiguo al más reciente, con sus datos (de `usuario`: `nombre`,
   `correo`, `telefono`; de `perfil_arrendador`: `tipo_documento`, `numero_documento` (se descifra solo por
   `ConvertidorCifrado`), `razon_social`, `direccion_fiscal`) y sus documentos con URL firmada. Requiere una
   consulta nueva en `PerfilArrendadorRepository` (`findByEstadoIdentidad…`) y/o en
   `DocumentoIdentidadRepository` (orden por `subido_en`), más uno o varios DTO nuevos en `cuentas.dto`.
2. Aprobar y rechazar: métodos de dominio en `PerfilArrendador` (p. ej. `aprobarIdentidad()`,
   `rechazarIdentidad(motivo)`, que exijan `EN_REVISION`) y métodos en `CuentaService` que relean el perfil
   con `findParaActualizar` (ya existe) para serializar dos decisiones simultáneas, cambien el estado y
   disparen el correo (ver §5).
3. Datos para el correo: el `correo` del arrendador vive en `usuario`; `administracion` no puede leerlo por
   repositorio, así que o lo devuelve `CuentaService` o el envío ocurre dentro de `cuentas`.
4. Validación de rol `ADMIN`: `CuentaServiceImpl` hoy valida rol con `Rol.X.name().equals(usuario.rol())`
   (l. 28); el mismo patrón sirve para `ADMIN` (en `administracion` o en `cuentas`).

---

## 3. Rol ADMIN y seguridad

- **El rol existe**: `B/cuentas/domain/Rol.java` (`USUARIO, ARRENDADOR, ADMIN`) y CHECK en
  `M/V1_1__usuarios.sql:12`.
- **El ADMIN hoy NO puede iniciar sesión** (decisión DT-7 de HU-03, "HU-06 habilita el acceso admin con su
  control", `specs/HU-03/design.md:27`, `requirements.md` P1):
  `Usuario.motivoRechazoInicioSesion()` devuelve `ROL_NO_PERMITIDO` primero (`B/cuentas/domain/Usuario.java:84-95`,
  l. 85-86) → 401 genérico + auditoría `LOGIN_FALLIDO motivo=ROL_NO_PERMITIDO`.
  - El mismo método decide la elegibilidad de la **recuperación de contraseña** (HU-04 S2):
    `RecuperacionContrasenaService.java:96` y `:170`. Si HU-06 quita `ADMIN` de ese método, el admin pasaría a
    poder recuperar su contraseña por correo (cambio de alcance de HU-04).
  - Tests que fijan el bloqueo y habría que cambiar: `T/cuentas/controller/SesionControllerIT.java:300-325`
    (`rechazosConCuerpoIdentico`, admin en l. 307-315), `T/cuentas/service/InicioSesionServiceTest.java:271`
    (`adminNoPermitido`), `T/cuentas/domain/UsuarioTest.java:88-94`, y los de HU-04
    (`RecuperacionContrasenaControllerIT.java:302-311`, `RecuperacionContrasenaServiceTest.java:220-229, 450-462`).
- **Validación de rol por solicitud:**
  - `FiltroAutenticacionSesion` (`B/comun/seguridad/FiltroAutenticacionSesion.java:31-55`) valida el Bearer con
    `ValidadorSesion` y pone `ROLE_<rol>` como autoridad (l. 51). El rol se relee de la BD en cada solicitud:
    `SesionService.validar` (`B/cuentas/service/SesionService.java:44-52`) construye `UsuarioAutenticado(usuarioId,
    sesionId, rol)` desde `usuario.getRol()`. **No revisa `usuario.estado`** (pendiente para HU-09).
  - `ConfiguracionSeguridad` (`B/comun/seguridad/ConfiguracionSeguridad.java:40-53`) solo distingue `permitAll`
    vs `authenticated()`: **no hay `hasRole`, ni `@PreAuthorize`, ni `@EnableMethodSecurity`** en el código.
    Decisión vigente (DT-10 de HU-05): el rol se comprueba **en el servicio** y lanza `AccesoDenegadoException`
    → 403 `ErrorDTO` (sin `AccessDeniedHandler`). Patrón: `CuentaServiceImpl.java:28-30`,
    `IdentidadArrendadorService.java:136-142`.
  - `UsuarioAutenticado` (`B/comun/seguridad/UsuarioAutenticado.java`): `record(Long usuarioId, Long sesionId,
    String rol)`.
- **Script/migración para crear admins: no existe nada.** Ninguna migración inserta usuarios; no hay
  `CommandLineRunner`, perfil, ni script `.sql`/`.sh` (solo `init.sh` y un hook). `Usuario` no tiene fábrica para
  admin: `registrar` (l. 56-68) fija `USUARIO` y exige `versionPolitica`/`fechaConsentimiento` (`NOT NULL` en
  BD). Los tests crean admins con `UPDATE usuario SET rol = 'ADMIN'` por JDBC. RNF-SEG-18 y
  `docs/architecture.md:160-161`: "cuentas de administrador solo se crean por script interno".
- **Lista de IPs permitidas: no existe nada** (ni propiedad, ni filtro, ni variable en `.env.example`).
  - Pieza parecida reutilizable como modelo: `B/comun/limite/FiltroLimiteIntentos.java` (`@Component`
    `OncePerRequestFilter`, responde JSON `ErrorDTO` sin pasar por el controlador).
  - Spring Security 7.1.2 trae `org.springframework.security.web.util.matcher.IpAddressMatcher` (IP y CIDR,
    IPv4/IPv6) sin dependencias nuevas (conocimiento general; verificado que la clase está en el jar de
    `~/.m2`).
- **`IpCliente`** (`B/comun/web/IpCliente.java:10-12`): `solicitud.getRemoteAddr()`, nada más. No hay
  `server.forward-headers-strategy` ni `RemoteIpValve` en `application.properties`. **Riesgo:** la arquitectura
  de UAT (`4_Infraestructure/arquitectura-aws.html`) pone **CloudFront → Load Balancer con WAF → contenedor**;
  allí `getRemoteAddr()` sería la IP del balanceador, así que una lista de IPs basada en `IpCliente` no
  distinguiría admins (y la auditoría tampoco registra la IP real). Los tests fijan la IP con
  `setRemoteAddr(...)` (`SesionControllerIT.java:142`, `IdentidadArrendadorControllerIT.java:208`).
- **`docs/requerimientos.md`:**
  - §9 pendiente 4 (l. 291): "Control compensatorio de IPs del panel admin (HU-06, HU-09) hasta HU-17.
    Decidir si se retira o se mantiene junto con MFA." **Sigue abierto** (no está tachado).
  - RNF-SEG-04 (l. 149): MFA TOTP obligatorio para admin (HU-17); "Hasta HU-17, el panel admin solo es
    accesible desde IPs permitidas". Cita HU-06 (S), HU-09 (S), HU-27 (E/S).
  - RNF-SEG-18 (l. 163): roles validados en backend en cada solicitud; rol nunca del cliente; admins solo
    por script.
  - HU-09 (Jorge Quispe, sprint 3) depende de HU-06 y también exige rol admin y control (S): lo que HU-06
    construya (login admin, filtro de IPs, prefijo de rutas) lo reutilizará HU-09.

---

## 4. Módulo `administracion`

- **No existe ningún paquete ni clase** (`B/` solo tiene `comun`, `cuentas`, `espacios`). Solo se nombra en el
  Javadoc de `B/EspacioYaApplication.java:8`. `init.sh:241-242` ya admite `administracion` como paquete válido.
- Estructura esperada (`docs/architecture.md:43-54`, `docs/conventions.md:56-69`, RNF-ARQ-04):
  `B/administracion/{controller,dto,service,domain,repository}/` con `<Recurso>Controller`, `<Recurso>DTO`,
  `<Recurso>Service`, `<Entidad>`, `<Entidad>Repository`. Puertos (si hicieran falta) en `service/puertos/`.
  Interfaz pública solo si otro módulo lo usa (no es el caso en HU-06: clase concreta, YAGNI, l. 136-138).
- Regla: un controlador nunca importa un repositorio (`init.sh` lo verifica, l. 250-253); `administracion`
  nunca usa `cuentas.repository`.
- Frontend: páginas agrupadas por módulo → `F/pages/administracion/` (`docs/frontend.md:40-41`); llamadas en
  `F/api/administracion.js`.

---

## 5. Correo

- Puerto `B/cuentas/service/NotificadorCorreo.java` (l. 3-8): `notificarVerificacion(String correoNormalizado)`
  (HU-01) y `notificarRecuperacion(String correoNormalizado, String enlace)` (HU-04). Sin métodos `default`
  ("para que todo adaptador lo implemente", `specs/HU-04/design.md` DT-6).
- Simulado `B/cuentas/service/NotificadorCorreoSimulado.java`: `@Component`; constructor con
  `@Value("${espacioya.correo.simulado.registrar-enlace:false}") boolean` (l. 15-18); solo escribe `log.info`
  sin el correo (Ley 29733, l. 22-23); con la propiedad de desarrollo activa escribe el enlace (l. 28-35).
  Propiedad: `application.properties:27-29` (`CORREO_SIMULADO_REGISTRAR_ENLACE`, "nunca true en UAT"; también
  en `.env.example`).
- Restricciones de test a respetar: `NotificadorCorreoSimuladoTest.sinColaboradoresDeRed` (l. 29-39) exige que
  el simulado solo tenga parámetros `boolean` y campos `boolean`/slf4j. Hay **tres dobles** que implementan la
  interfaz y habría que ampliar si se agregan métodos: `T/cuentas/controller/ArrendadorControllerIT.java:104`,
  `RecuperacionContrasenaControllerIT.java:118`, `UsuarioControllerIT.java:94` (lo mismo hizo HU-04).
- Decisiones previas: HU-01 (`specs/HU-01/design.md:53, 544-547`) no creó `comun/correo`; HU-04 D2
  (`specs/HU-04/requirements.md:16`) amplió el puerto en `cuentas` y dejó escrito que **HU-25 lo moverá a
  `comun/correo`**. `docs/architecture.md:216`: HU-25 ★ aplica Factory Method + Adapter en `comun/correo`
  ("crear el correo según el evento"). `docs/requerimientos.md` §8 (l. 280): el componente de correo lo
  reutiliza HU-06. RNF-USA-04 (l. 195): avisos por correo en eventos de identidad. RNF-SEG-16: los correos
  no incluyen documentos.
- Pendiente de HU-04 (`progress/history.md:340-343`): notificar dentro de la transacción es un riesgo con el
  proveedor real; HU-25 debe capturar el fallo o enviar después del commit. Aplica igual a HU-06.
- Cómo podría agregarlo HU-06 (ver Riesgo R7): (a) dos métodos nuevos en `cuentas/service/NotificadorCorreo`
  (`notificarIdentidadAprobada(correo)`, `notificarIdentidadRechazada(correo, motivo)`) llamados desde el método
  de `CuentaService` que aprueba/rechaza; (b) un puerto propio en `administracion`. HU-05 P12
  (`specs/HU-05/requirements.md:32`): "los correos de identidad los envía HU-06 al decidir".

---

## 6. Auditoría

- `B/comun/auditoria/AuditoriaService.java:20-23`: `@Transactional registrar(EventoAuditoria)`; la fecha la pone
  el servicio con el `Clock` (`Instant.now(reloj)`).
- `B/comun/auditoria/EventoAuditoria.java`: `record(Long usuarioId, String accion, String entidad, Long entidadId,
  String ip, String detalle)`.
- Tabla `auditoria` (`M/V1_2__auditoria.sql`): `id`, `usuario_id` (FK a `usuario`, anulable), `accion
  VARCHAR(60) NOT NULL`, `entidad VARCHAR(60)`, `entidad_id BIGINT`, `ip VARCHAR(45)`, `fecha TIMESTAMPTZ NOT
  NULL`, `detalle TEXT`. Índices por usuario y fecha.
- Acciones existentes (constantes `ACCION_*` en cada servicio): `REGISTRO_USUARIO`, `REGISTRO_CORREO_EXISTENTE`,
  `REGISTRO_ARRENDADOR`, `REGISTRO_DOCUMENTO_EXISTENTE`, `LOGIN_EXITOSO`, `LOGIN_FALLIDO`, `LOGIN_BLOQUEADO`,
  `CIERRE_SESION`, `RECUPERACION_SOLICITADA`, `RECUPERACION_NO_EMITIDA`, `RECUPERACION_RECHAZADA`,
  `CONTRASENA_RESTABLECIDA`, `CARGA_DOCUMENTO_IDENTIDAD`, `CARGA_DOCUMENTO_RECHAZADA`, `CREACION_ESPACIO`,
  `EDICION_ESPACIO`, `PUBLICACION_ESPACIO`, `FOTO_ESPACIO_AGREGADA`, `FOTO_ESPACIO_ELIMINADA`,
  `FOTO_ESPACIO_RECHAZADA`. **Ninguna de identidad aprobada/rechazada.**
- **¿Alcanza para "admin, arrendador, decisión, motivo, fecha"?** Sí, sin cambiar la tabla:
  `usuario_id` = admin de la sesión; `entidad = "perfil_arrendador"`, `entidad_id` = id del arrendador;
  `accion` = p. ej. `IDENTIDAD_APROBADA` / `IDENTIDAD_RECHAZADA` (o una sola con `decision=` en el detalle);
  `detalle` = `motivo=…` (TEXT); `fecha` la pone el servicio; `ip` también. Ojo: la IP solo será la real si se
  resuelve el punto de `IpCliente` (§3).
- `revision_identidad` (modelo de datos) duplicaría esta información de forma consultable (historial de
  decisiones por arrendador): ver Riesgo R5.

---

## 7. Base de datos y migraciones

- Migraciones en `develop` (idénticas a la rama actual y a `origin/develop`):
  `V1_1__usuarios.sql`, `V1_2__auditoria.sql`, `V1_3__perfil_arrendador.sql`, `V1_4__sesion.sql`,
  `V1_5__token.sql`, `V2_1__documento_identidad.sql`, `V2_2__espacio.sql`. Ninguna rama remota tiene una `V2_3`.
- `spring.flyway.out-of-order=true` (`application.properties:15-18`).
- **Siguiente número libre del sprint 2: `V2_3`** (p. ej. `V2_3__revision_identidad.sql`). Reglas de
  `docs/conventions.md:76-95`: el número sale del sprint de la historia (HU-06 = sprint 2), siguiente `n` libre
  en `develop`; si al unir `develop` ya tiene `V2_3` (p. ej. HU-08, también sprint 2), se renombra antes del
  merge; una migración no depende de otra de número mayor (una `V2_3` que haga FK a `perfil_arrendador` (V1_3)
  o `usuario` (V1_1) está bien).
- `docs/modelo-datos.md`:
  - l. 14: `perfil_arrendador` (… `estado_identidad`, `motivo_rechazo`) lista HU-06.
  - l. 15: `documento_identidad` lista HU-05, HU-06.
  - l. 16: **`revision_identidad` | id, arrendador_id, admin_id, decision, motivo, fecha | HU-06**.
    No dice tipos, CHECK ni si el motivo es obligatorio solo para `RECHAZADA`. No prevé "fecha de revisión" en
    `perfil_arrendador` ni "revisor" fuera de `revision_identidad.admin_id`.
  - Cabecera (l. 3-7): si el `design.md` cambia nombre/atributo, lo justifica y actualiza el archivo en el mismo PR.
- `motivo_rechazo` admite hasta **500** caracteres (columna y `@Column(length = 500)`); el motivo de
  `revision_identidad` debería tener el mismo tope.

---

## 8. Errores, reloj y paginación

- `B/comun/error/` (mapeo en `ManejadorGlobalErrores.java`):
  - `ReglaNegocioException(campo?, mensaje)` → 400 (con `ErrorCampoDTO` si trae campo) (l. 23-30).
  - `ValidacionException(List<ErrorCampoDTO>)` → 400 con `MENSAJE_VALIDACION` (l. 59-62).
  - `MethodArgumentNotValidException` (Bean Validation en `@Valid @RequestBody`) → 400 por campo (l. 44-51).
  - `HttpMessageNotReadableException` → 400 genérico (l. 53-57).
  - `AccesoDenegadoException` → 403 `MENSAJE_ACCESO_DENEGADO` (l. 64-67).
  - `RecursoNoEncontradoException` → 404 `MENSAJE_RECURSO_NO_ENCONTRADO` (l. 69-72).
  - `CredencialesInvalidasException` → 401; `DemasiadosIntentosException` → 429.
  - Excepciones de Spring MVC con 4xx conservan su código con mensaje genérico; el resto → 500 (l. 87-96).
  - Sin sesión → 401 por `PuntoEntradaNoAutenticado` (`MENSAJE_SESION_INVALIDA`).
  - No hay excepción ni handler de 409; `docs/conventions.md:126` admite 400/409 para reglas y la práctica
    (HU-05 DT-9, HU-07) es 400.
- Reloj: bean `Clock` en `B/comun/configuracion/ConfiguracionReloj.java` (`Clock.systemUTC()`); en IT se
  reemplaza con `T/RelojControlable.java` (ej. `SesionControllerIT.java:94-95`).
- Paginación: **no existe ningún patrón** (`Pageable`, `Page<>`, `PageRequest` no aparecen). Los listados
  devuelven `List<…DTO>` ordenados en el repositorio (`EspacioRepository.findByArrendadorIdOrderByCreadoEnDesc`,
  `EspacioController.java:37-38`).
- Concurrencia: patrón de bloqueo pesimista ya disponible (`PerfilArrendadorRepository.findParaActualizar`).

---

## 9. Frontend

- Kit `F/components/ui/`: `Alerta` (`tipo` exito/atencion/error/info), `Boton` (`variante` primario,
  secundario, peligro, **`peligro-confirmar` ya tiene estilo** en `Boton.module.css:43-46`; `cargando`
  muestra "Enviando…"), `Campo` (text, select, checkbox, `archivo`, `textarea` con `filas`),
  `EtiquetaEstado` (ya mapea `VERIFICADA` éxito, `EN_REVISION` atención, `RECHAZADA` error, `NO_INICIADA`
  neutro, l. 29-33), `Tarjeta` (`titulo`, `children`, `acciones`). **`Modal` no existe** (está en la tabla del
  kit de `docs/frontend.md:169`: `abierto`, `titulo`, `onConfirmar`, `onCerrar`, "Confirmar acciones
  irreversibles"); por la regla "lo construye la primera historia que lo necesita" (l. 172-176), sería HU-06.
- `F/api/`: `cliente.js` (`peticion(ruta, {metodo, cuerpo, autenticada})`, `peticionArchivo`, `urlApi(ruta)` para
  enlaces que abre el navegador, `alPerderSesion`), `tokenSesion.js` (sessionStorage), `cuentas.js`
  (`obtenerSesionActual`, `iniciarSesion`, `obtenerIdentidad`, `enviarDocumentosIdentidad`, …),
  `espacios.js`. **No hay `administracion.js`.**
- Rutas (`F/App.jsx:75-139`): `/`, `/registro`, `/registro-arrendador`, `/privacidad`, `/iniciar-sesion`,
  `/recuperar-contrasena`, `/restablecer-contrasena`, y con `<RutaProtegida>`: `/panel`, `/panel/identidad`,
  `/panel/espacios`, `/panel/espacios/nuevo`, `/panel/espacios/:id`. **No existe ninguna ruta ni layout de
  admin.**
- Sesión y rol en el cliente: `ProveedorSesion` guarda `{ rol, aviso }` de `GET /api/sesiones/actual`
  (`F/sesion/ProveedorSesion.jsx:21-32`, "el rol solo elige qué mostrar"); `RutaProtegida` solo comprueba que
  haya sesión, **no el rol** (`F/sesion/RutaProtegida.jsx`). Tras el login se navega a `/panel`
  (`IniciarSesion.jsx:34-36`). `Panel.jsx:6-9` tiene textos solo para `USUARIO` y `ARRENDADOR` (un `ADMIN`
  caería en los de `USUARIO`) y enlaces solo para `ARRENDADOR` (l. 35-44).
- `docs/frontend.md`: color de identidad en l. 135 (ver arriba); l. 115-118: "El rojo es solo para errores y
  acciones destructivas… Solo dentro del `Modal` de confirmación el botón pasa a rojo relleno
  (`peligro-confirmar`)"; un solo botón primario por pantalla (l. 108-110); "Lista vacía: texto secundario"
  (l. 190); nunca mostrar ids internos (l. 194).
- Lo que ve el arrendador tras la decisión ya está construido: `VerificarIdentidad.jsx` muestra
  `EtiquetaEstado` (l. 105) y "Motivo del rechazo: …" (l. 112-113); el panel muestra el `aviso` (rechazada +
  motivo; nada si verificada). El formulario de reenvío aparece porque `puedeEnviar` lo decide el servidor.

---

## 10. Requerimientos (`docs/requerimientos.md`)

- §7 fila HU-06 (l. 244): épica F · R1 · S2 (8) · **RF-F-01…03** · **SEG-04, 12, 15, 18** · **RN-01** ·
  depende de HU-05 · programa Ariel Enriquez · prueba Eduardo Saldaña · patrón "—".
- RF-F-01 (l. 127): cola de arrendadores en "identidad en revisión" con sus datos y documentos, del más antiguo
  al más reciente (CA1). RF-F-02 (l. 128): aprobar → "identidad verificada" + correo de que ya puede publicar
  (CA2). RF-F-03 (l. 129): rechazar con motivo obligatorio → "identidad rechazada" + correo con el motivo (CA3).
- RNF-SEG-04 (l. 149), RNF-SEG-12 (l. 157, incluye "decisiones de identidad"), RNF-SEG-15 (l. 160, documentos
  en almacenamiento privado con URL firmada de corta duración; cita HU-06 (I)), RNF-SEG-18 (l. 163). Relacionados
  no listados en la fila: RNF-SEG-13 (errores genéricos), RNF-SEG-14 (datos sensibles cifrados), RNF-SEG-16
  (correos sin documentos), RNF-USA-04 (avisos de identidad por correo), RNF-SEG-17 (no fija límite para HU-06).
- RN-01 (l. 210): correo verificado → identidad en revisión → identidad verificada (publica…) o identidad
  rechazada (vuelve a subir documentos). "Nunca se dice 'verificado' a secas."
- §2 (l. 21): Administrador "se crea solo por script interno".
- §8: Auditoría (l. 276), Envío de correo detrás de interfaz propia (l. 280, reutiliza HU-06), Almacenamiento
  privado con URL firmada (l. 281, "Lo reutilizan: HU-06").
- §9 pendientes: **4 (IPs del panel admin) abierto**. Ningún otro pendiente toca HU-06.

---

## 11. Decisiones previas que condicionan HU-06

- **HU-02:** `perfil_arrendador` con `estado_identidad` y `motivo_rechazo` (500) nacen en V1_3; datos de
  facturación cifrados con `ConvertidorCifrado` (se descifran al leer la entidad).
- **HU-03:** DT-7/P1 — el ADMIN no inicia sesión "hasta HU-06, que habilita el acceso admin con su control"
  (restricción de IP, RNF-SEG-04). DT-8: mismo 401 genérico para admin. DT-2: token Bearer en
  `sessionStorage`; DT-9 y HU-05 DT-12: aviso por estado de identidad en la sesión. Si HU-06 habilita el login
  admin, **reemplaza HU-03 R13** (igual que HU-05 DT-12 reemplazó HU-03 R5): el spec debe decirlo.
- **HU-04:** D2 — el puerto de correo se amplía en `cuentas/service/NotificadorCorreo`; HU-25 lo moverá a
  `comun/correo`. D1 — propiedad de desarrollo del simulado. S2 — cuentas "no habilitadas" (incluye ADMIN) no
  reciben enlace de recuperación (misma regla que el login).
- **HU-05:** P10/DT-16 — HU-05 no construyó nada de admin; HU-06 agrega en `cuentas` el método que lista
  documentos para `administracion`. P12 — sin correo al pasar a `EN_REVISION`; los correos de identidad son de
  HU-06. P7/DT-8 — reenvío reemplaza documentos y limpia el motivo. P8 — solo envía desde `NO_INICIADA` o
  `RECHAZADA`. P13/DT-12 — el arrendador ve el motivo en el aviso del panel y en `/panel/identidad`. DT-3 — URL
  firmada de 5 min por `GET /api/archivos`. DT-10 — rol comprobado en el servicio con `AccesoDenegadoException`.
  DT-15 — "HU-06 usa la siguiente `V2_n`". Review: unificar el 500 de `ArchivoController` a 404.
- **HU-07:** `CuentaService` como interfaz pública de `cuentas`; publicar exige `VERIFICADA`, que "nadie
  alcanza hasta HU-06" (`specs/HU-07/design.md:4`, `feature_list.json` nota "visible tras HU-06"). DT-15: si HU-06
  hubiese tomado `V2_2`, HU-07 renombraba; ya está `V2_2` en `develop`.
- `progress/history.md:292-293, 353-354`: avisos explícitos a Ariel Enriquez — `V2_1` ocupada y regla
  `out-of-order` antes de numerar.

---

## Riesgos y decisiones abiertas para el spec

1. **Cómo inicia sesión el admin (reemplaza HU-03 R13).** Hoy `Usuario.motivoRechazoInicioSesion()` lo rechaza
   (`Usuario.java:85-86`) y ese método también decide la recuperación (HU-04 S2).
   - (a) Quitar `ADMIN` de `motivoRechazoInicioSesion()` y aceptar que el admin también recupere contraseña por
     correo. (b) Permitir el login admin solo desde IP permitida (si no, el mismo 401 genérico con
     `motivo=ROL_NO_PERMITIDO` o un motivo nuevo `IP_NO_PERMITIDA` en auditoría) y **mantener** al admin fuera
     de la recuperación con una regla aparte (p. ej. separar `motivoRechazoRecuperacion`). (c) Login admin por
     un endpoint propio.
   - **Recomendación: (b).** Mismo `POST /api/sesiones`, sin endpoint nuevo; la IP se comprueba en
     `InicioSesionService` para cuentas `ADMIN`; la recuperación admin sigue cerrada hasta HU-17 (MFA). El spec
     debe declarar que reemplaza HU-03 R13 y listar los tests de HU-03/HU-04 que cambian (§3).

2. **Control compensatorio de IPs (STRIDE S, pendiente §9-4 abierto).**
   - (a) Filtro propio en `comun/seguridad` sobre el prefijo de rutas admin (p. ej. `/api/admin/**`) con
     `IpAddressMatcher` y propiedad `espacioya.admin.ips-permitidas=${ADMIN_IPS_PERMITIDAS:127.0.0.1,::1}`
     (CIDR admitido), 403 o 404 genérico. (b) Solo en infraestructura (regla de IP en el WAF de AWS).
     (c) Ambas.
   - **Recomendación: (a) + login admin restringido (punto 1)**, con lista vacía = nadie entra (cerrado por
     defecto en UAT) y valor de desarrollo `127.0.0.1,::1`. Reutilizable por HU-09/HU-27. Documentar en
     `.env.example`. Dejar anotado que el WAF puede sumarse en UAT. Responder en el spec el pendiente §9-4
     ("se mantiene hasta HU-17; HU-17 decide").

3. **IP real detrás de CloudFront/ALB.** `IpCliente` usa `getRemoteAddr()`; en UAT sería la IP del balanceador
   y la lista de IPs no serviría (y la auditoría registraría la IP equivocada en todo el sistema).
   - (a) `server.forward-headers-strategy=native` + `server.tomcat.remoteip.internal-proxies` con los rangos de
     confianza (Tomcat `RemoteIpValve`); `IpCliente` no cambia. (b) Leer `X-Forwarded-For` a mano en
     `IpCliente` (falsificable si no se valida el proxy). (c) Dejarlo fuera de alcance y depender del WAF.
   - **Recomendación: (a)** como configuración por variable de entorno, sin tocar `IpCliente`; en local
     no cambia nada. Si el dueño prefiere no tocar la configuración global, (c) con nota explícita en el spec y
     en el README de despliegue. Nunca (b).

4. **Script interno para crear admins (STRIDE E).** No existe nada; `Usuario` no tiene fábrica de admin y
   `version_politica`/`fecha_consentimiento` son `NOT NULL`.
   - (a) `ApplicationRunner` de `cuentas` activado solo con una propiedad/perfil (p. ej. `--espacioya.admin.crear`)
     que lee correo y contraseña de variables de entorno, aplica la política de contraseña, hashea con el
     `PasswordEncoder` (bcrypt 12), crea `Usuario.registrarAdmin(...)` y audita `CREACION_ADMIN`; sin endpoint.
     (b) Script SQL con un hash bcrypt pegado a mano (fuera de Flyway). (c) Migración Flyway con un admin
     sembrado (descartable: credenciales en el repo).
   - **Recomendación: (a)**: reutiliza el hash y la política del backend, no deja secretos en el repo y se
     prueba con un test. Para `version_politica` usar la versión vigente
     (`espacioya.privacidad.version-vigente`) y `correo_verificado = true`. Documentar el comando en el README.

5. **Dónde vive la decisión y la tabla `revision_identidad`.**
   - (a) Crear `revision_identidad` (V2_3) como entidad de `administracion/domain` (`RevisionIdentidad`,
     `admin_id` y `arrendador_id` con FK, `decision` CHECK `APROBADA`/`RECHAZADA`, `motivo VARCHAR(500)` con
     CHECK "obligatorio si RECHAZADA", `fecha`); el estado sigue en `perfil_arrendador` y lo cambia `cuentas`
     vía `CuentaService`. (b) No crear la tabla y apoyarse solo en `perfil_arrendador.motivo_rechazo` + la
     auditoría (actualizando `modelo-datos.md`). (c) Tabla en `cuentas`.
   - **Recomendación: (a)**: cumple el modelo de datos, da historial de decisiones por arrendador (útil si
     hay varios rechazos), y la auditoría queda como registro (R). La operación de `administracion` es una
     transacción (`@Transactional`) que llama a `CuentaService` (propagación REQUIRED) e inserta la revisión:
     todo o nada.

6. **Métodos nuevos en `CuentaService` y DTO.** Propuesta a confirmar:
   `List<ArrendadorEnRevisionDTO> arrendadoresEnRevision()` (ordenado por `subido_en` ascendente y
   desempate por id, con datos y `DocumentoIdentidadDTO` firmados) y
   `void aprobarIdentidad(Long arrendadorId)` / `void rechazarIdentidad(Long arrendadorId, String motivo)` que
   usan `findParaActualizar` y métodos de dominio nuevos `PerfilArrendador.aprobarIdentidad()` /
   `rechazarIdentidad(motivo)` (exigen `EN_REVISION`; si no, 400 genérico "ya fue revisado" o 404).
   - Alternativa: que `CuentaService` reciba `UsuarioAutenticado` y valide el rol `ADMIN` él mismo (como
     `habilitacionArrendador`), o que lo valide `administracion`.
   - **Recomendación:** validar el rol `ADMIN` en el servicio de `administracion` (es su caso de uso) **y**
     en `CuentaService` (defensa en profundidad, mismo patrón que HU-07), pasando `UsuarioAutenticado`.
     Arrendador inexistente o no `EN_REVISION` → 404 genérico si no existe y 400 con mensaje fijo si ya se
     decidió (dos admins a la vez: el segundo espera el bloqueo y recibe 400).

7. **Correos de aprobación y rechazo.**
   - (a) Dos métodos nuevos en `cuentas/service/NotificadorCorreo` llamados desde `CuentaService` (consistente
     con HU-04 D2; HU-25 los moverá a `comun/correo`). (b) Puerto propio en `administracion` con su simulado.
   - **Recomendación: (a)**. El simulado registra una línea INFO sin correo ni motivo ("…simulado (HU-06)");
     actualizar los tres `NotificadorFalso` de las IT. Enviar **después del commit**
     (`TransactionSynchronization.afterCommit`) o al final del caso de uso fuera de la transacción, para no
     avisar una decisión que hizo rollback (pendiente de HU-04 para HU-25). Los correos no incluyen
     documentos (RNF-SEG-16). Texto de aprobación: "identidad verificada… ya puedes publicar"; nunca "verificado"
     a secas (RN-01).

8. **Orden de la cola y fecha de envío.** No hay `enviado_en` en el perfil.
   - (a) Ordenar por `documento_identidad.subido_en` (igual en ambos documentos y se renueva en cada reenvío).
     (b) Nueva columna `perfil_arrendador.enviado_en` en V2_3.
   - **Recomendación: (a)**, sin migración extra; desempate por `usuario_id`. Mostrar la fecha de envío en la
     cola.

9. **Qué datos ve el admin (minimización, RNF-SEG-14/16).** El número de documento está cifrado en reposo pero
   se descifra al leer la entidad.
   - (a) Mostrar nombre, correo, teléfono, tipo y número de documento completo, razón social, dirección fiscal y
     fecha de envío. (b) Igual pero con número enmascarado.
   - **Recomendación: (a)**: el admin debe cotejar el número con el documento; las respuestas llevan
     `Cache-Control: no-store` y nada de esto va al log ni a la auditoría.

10. **Una sola lista o lista + detalle; paginación.** No hay patrón de paginación.
    - (a) `GET` de la cola con todo (incluidas URL firmadas de 5 min). (b) Cola resumida + `GET` de detalle por
      arrendador que firma las URL al abrirlo. (c) Paginación.
    - **Recomendación: (b) sin paginación**: las URL se firman justo cuando el admin va a mirar (5 min no
      caducan mientras revisa una cola larga) y se evita firmar URL para todos. Volumen de UAT pequeño;
      paginación fuera de alcance.

11. **Rutas REST.** Propuesta: prefijo único `/api/admin/` (facilita el filtro de IPs y lo reutiliza HU-09):
    `GET /api/admin/arrendadores/en-revision`, `GET /api/admin/arrendadores/{id}/identidad`,
    `POST /api/admin/arrendadores/{id}/identidad/aprobacion` y `…/rechazo` (cuerpo `{ motivo }` con
    `@NotBlank @Size(max = 500)` tras recortar). El `{id}` es el recurso revisado, no la identidad del actor
    (que sale de la sesión), así que no choca con `docs/conventions.md:51-52`. Alternativa: un solo
    `POST …/revisiones` con `{ decision, motivo }`. **Recomendación:** dos endpoints (validación del motivo más
    simple y CA2/CA3 separados).

12. **Acceso del admin a `GET /api/archivos` (STRIDE I).** El endpoint es `permitAll` y la URL firmada es la
    autorización; queda fuera del filtro de IPs. **Recomendación:** mantenerlo (el control de acceso está al
    firmar, en un endpoint admin con IP y rol), y aprovechar para cerrar la observación de HU-05: convertir el
    `UncheckedIOException` de `ArchivoController.servir` en el 404 genérico. Opcional: auditar
    `DOCUMENTO_IDENTIDAD_CONSULTADO` al firmar para el admin (no lo pide la historia; decidir).

13. **Frontend del admin.**
    - Ruta(s): `/admin/identidades` (cola) y detalle con los documentos; o dentro de `/panel`. Hoy
      `RutaProtegida` no mira el rol y `Panel.jsx` trata a un ADMIN como USUARIO.
    - **Recomendación:** `Panel.jsx` con textos y enlace para `ADMIN`; páginas en `F/pages/administracion/`,
      llamadas en `F/api/administracion.js`; el backend sigue siendo quien autoriza (un no-admin recibe 403 y se
      muestra el `Alerta`). Construir **`Modal`** en el kit (primera historia que lo necesita; actualizar
      `docs/frontend.md`): rechazar abre el `Modal` con `Campo tipo="textarea"` para el motivo y
      `Boton variante="peligro-confirmar"`; aprobar con confirmación (botón primario). Documentos con
      `<a href={urlApi(url)} target="_blank">` como en `VerificarIdentidad.jsx`. Estados con `EtiquetaEstado`
      (ya mapeados). Lista vacía con texto secundario.

14. **Arrendador suspendido o eliminado en la cola.** HU-09 (suspensión) aún no existe; `SesionService.validar`
    no mira `usuario.estado`. **Recomendación:** listar solo `EN_REVISION` de cuentas `ACTIVA` (o no filtrar y
    dejarlo a HU-09); decidir y dejarlo escrito para Jorge Quispe (HU-09).

15. **Migración `V2_3` y choque con otras historias del sprint 2.** `V2_3` está libre hoy, pero HU-08 (sprint 2)
    podría tomarla. **Recomendación:** `V2_3__revision_identidad.sql`; antes del merge, actualizar `develop` y
    renombrar si ya está tomada (`docs/conventions.md:87-89`). Actualizar `docs/modelo-datos.md`
    (`revision_identidad` con tipos/CHECK), `docs/architecture.md` (métodos nuevos de `CuentaService`),
    `docs/frontend.md` (`Modal` construido) y `.env.example` (IPs permitidas, credenciales del script admin sin
    valores) en el mismo PR.
