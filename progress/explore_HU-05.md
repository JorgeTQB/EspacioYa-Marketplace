# Exploración — HU-05 · Envío de documentos de identidad del arrendador

- **Fecha:** 2026-10-28
- **Agente:** explorador (general-purpose), lanzado por el orquestador
- **Rama:** feature/HU-05-verificacion-identidad
- **Alcance:** solo lectura. Nada fuera de este archivo fue modificado.

## Preguntas

1. Backend: qué hay en `PerfilArrendador`, `Usuario` y los estados de identidad; cómo autentica HU-03
   y cómo un controlador obtiene el usuario y restringe por rol; errores en `comun`; auditoría; rate
   limiting; si ya hay algo de subida de archivos (multipart, almacenamiento, tipo/tamaño, cifrado);
   migraciones Flyway; dependencias del `pom.xml`; estructura de tests.
2. Frontend: kit `components/ui/`, `api/`, `sesion/`, `RutaProtegida`, `Panel`, rutas, y qué dice
   `docs/frontend.md` sobre formularios, archivos y mensajes.
3. Huecos: qué falta construir y qué decisiones siguen abiertas.

## Archivos analizados

- Docs: `docs/historias/HU-05.md`, `docs/historias/HU-06.md`, `docs/requerimientos.md`,
  `docs/architecture.md`, `docs/modelo-datos.md`, `docs/frontend.md`, `docs/conventions.md`,
  `docs/verification.md`, `4_Infraestructure/arquitectura-aws.html` (solo grep), `progress/HU-05.md`,
  `feature_list.json`.
- Specs: `specs/HU-02/design.md`, `specs/HU-03/design.md`, `specs/HU-03/requirements.md`.
- Backend: todo `2_Backend/src/main/java/pe/edu/ulima/espacioya/{comun,cuentas}/**`,
  `src/main/resources/application.properties`, `db/migration/V1_1..V1_4`,
  `src/test/resources/config/application.properties`, `pom.xml`, `.env.example`,
  `src/test/java/.../TestcontainersConfiguration.java`, `RelojControlable.java`,
  `cuentas/controller/SesionControllerIT.java`.
- Frontend: `3_frontend/src/{App.jsx, api/*, sesion/*, components/ui/*.jsx, pages/cuentas/Panel.jsx,
  pages/cuentas/textosCuentas.js}`, `package.json`.

---

## 1. Backend

### 1.1 Dominio de identidad

| Pieza | Ruta:línea | Qué hay hoy |
|---|---|---|
| `EstadoIdentidad` | `cuentas/domain/EstadoIdentidad.java:3-8` | `NO_INICIADA, EN_REVISION, VERIFICADA, RECHAZADA`. |
| `PerfilArrendador` | `cuentas/domain/PerfilArrendador.java:22-125` | Entidad 1-1 con PK = `usuario_id` e implementa `Persistable` (l. 19-24, 58-92): `save` hace `persist` para un perfil nuevo. Campos `estadoIdentidad` (l. 51-53) y `motivoRechazo` (VARCHAR 500, l. 55-56). `crear(...)` lo deja en `NO_INICIADA` con `motivoRechazo = null` (l. 64-76). |
| `AVISO_IDENTIDAD` | `PerfilArrendador.java:26` | "Para publicar tus espacios, primero debes verificar tu identidad." `ArrendadorController.java:22` lo referencia. |
| Transiciones | — | **No hay ninguna.** Solo hay getters (l. 94-124), así que HU-05 tiene que agregar al dominio la transición `NO_INICIADA \| RECHAZADA → EN_REVISION` (p. ej. `enviarDocumentos()`) y decidir qué pasa desde `EN_REVISION` y `VERIFICADA` (ver huecos). HU-06 agregará `aprobar` y `rechazar(motivo)`. |
| `Usuario` | `cuentas/domain/Usuario.java` | `rol` (l. 31-33), `correoVerificado` (l. 34-35), `estado` (l. 37-39). `registrar` fija `correoVerificado = true` (l. 64, decisión S5 de HU-01), así que **todo arrendador cumple hoy "correo verificado"**. `motivoRechazoInicioSesion()` (l. 84-95) rechaza el login de los `ADMIN` (l. 85-86). |
| Repositorio | `cuentas/repository/PerfilArrendadorRepository.java` | `JpaRepository<PerfilArrendador, Long>` + `findByHashDocumento`. |
| BD | `db/migration/V1_3__perfil_arrendador.sql` | `perfil_arrendador.usuario_id` FK `ON DELETE CASCADE` a `usuario`. `CHECK` de `estado_identidad` con los 4 valores. `motivo_rechazo VARCHAR(500)` anulable. |
| Modelo documentado | `docs/modelo-datos.md:14-16, 55` | `documento_identidad (id, arrendador_id, tipo, ruta_archivo, tipo_mime, tamano, subido_en)` es de HU-05. `revision_identidad (id, arrendador_id, admin_id, decision, motivo, fecha)` es de HU-06. Relación `PERFIL_ARRENDADOR \|\|--o{ DOCUMENTO_IDENTIDAD`. |

### 1.2 Autenticación por sesión (HU-03) y cómo restringir por rol

- `comun/seguridad/FiltroAutenticacionSesion.java:32-52`: lee `Authorization: Bearer`, valida con el
  puerto `ValidadorSesion` y pone en el `SecurityContext` un
  `UsernamePasswordAuthenticationToken` cuyo **principal es `UsuarioAutenticado`**, con authority
  `"ROLE_" + rol` (l. 51). No es un `@Component` (javadoc l. 17-20).
- `comun/seguridad/UsuarioAutenticado.java:3`: `record UsuarioAutenticado(Long usuarioId, Long sesionId, String rol)`.
- `cuentas/service/SesionService.java:44-52` implementa `ValidadorSesion`: busca por hash, comprueba
  que la sesión siga vigente, la renueva y devuelve el principal. La sesión expira tras 30 min de
  inactividad.
- **Cómo lo usa un controlador:** `@AuthenticationPrincipal UsuarioAutenticado usuario`, como en
  `cuentas/controller/SesionController.java:42-51`. La identidad siempre sale de
  `usuario.usuarioId()`, nunca de la URL (RNF-SEG-01, `docs/conventions.md:51`).
- `comun/seguridad/ConfiguracionSeguridad.java:33-46`: CSRF deshabilitado, sin estado
  (`STATELESS`). Rutas públicas: `GET /api/politicas-privacidad/vigente`, `POST /api/usuarios`,
  `POST /api/arrendadores` y `POST /api/sesiones`. Todo lo demás pide `.authenticated()`. **Hoy no hay
  ninguna regla por rol** (`hasRole`) y no hay `@EnableMethodSecurity`.
- 401: `PuntoEntradaNoAutenticado.java:12-29` responde `ErrorDTO.MENSAJE_SESION_INVALIDA`.
- **403: no hay `AccessDeniedHandler`.** Si HU-05 usa `.hasRole("ARRENDADOR")` o `@PreAuthorize`, la
  respuesta 403 no tendrá forma de `ErrorDTO`. Tampoco existe `AccesoDenegadoException` ni
  `RecursoNoEncontradoException`, aunque `docs/conventions.md:102-107` las lista: HU-05 sería la
  primera historia en necesitarlas, para "rol insuficiente → 403" y para "no existe o no es tuyo → 404"
  en la descarga de un documento ajeno (RNF-SEG-19).
- CORS (`ConfiguracionSeguridad.java:51-60`): permite los métodos GET, POST, PUT, PATCH, DELETE y
  OPTIONS y los encabezados `Content-Type` y `Authorization`. Alcanza para multipart: el navegador pone
  `Content-Type: multipart/form-data; boundary=...`.

### 1.3 El aviso de cuenta ya preparado para HU-05

- `cuentas/service/AvisoCuenta.java:13-26`: es una clase de paquete. Hoy devuelve `AVISO_IDENTIDAD`
  solo si el rol es `ARRENDADOR` y el estado es `NO_INICIADA` (l. 22). El javadoc (l. 9-11) dice
  textualmente: "HU-05 define los de EN_REVISION y RECHAZADA". `specs/HU-03/design.md:29` (DT-9) y
  `specs/HU-03/requirements.md:24` (P5) lo confirman.
- Dos servicios lo usan: `InicioSesionService.java:89`, que arma
  `SesionIniciadaDTO(token, rol, aviso)`, y `SesionService.actual` (l. 55-59), que arma
  `SesionActualDTO(rol, aviso)`.
- `SesionActualDTO.java:3` es `record SesionActualDTO(String rol, String aviso)` y **no trae
  `estadoIdentidad` ni `motivoRechazo`**. Para CA3 ("veo el motivo y puedo volver a subir") hay que
  ampliar el aviso (el motivo dentro del texto), ampliar el DTO o crear un endpoint propio (p. ej.
  `GET /api/arrendadores/actual/identidad`). Ojo: el aviso actual es un `String` y el frontend lo pinta
  siempre como `Alerta` de tipo `atencion` (ver 2.4).
- Test que ya cubre ese comportamiento: `SesionControllerIT.java:264-274` (`VERIFICADA` → aviso nulo)
  y `:441-450`. Si se cambia `AvisoCuenta`, estas IT de HU-03 deben seguir en verde.

### 1.4 Manejo de errores (`comun/error`)

- `ErrorDTO(String mensaje, List<ErrorCampoDTO> errores)` en `ErrorDTO.java:5-21`, con las constantes
  `MENSAJE_VALIDACION`, `MENSAJE_INESPERADO`, `MENSAJE_DEMASIADOS_INTENTOS`,
  `MENSAJE_CREDENCIALES_INVALIDAS` y `MENSAJE_SESION_INVALIDA` (l. 7-12).
- `ReglaNegocioException(campo, mensaje)` en `ReglaNegocioException.java`. El manejador
  (`ManejadorGlobalErrores.java:21-28`) responde 400: si hay `campo`, con
  `{mensaje: "Revisa los datos del formulario.", errores:[{campo, mensaje}]}`. **Es la vía natural para
  CA2** (formato o tamaño no permitido, con el motivo bajo el campo del archivo).
- `ManejadorGlobalErrores.java:57-70`: las excepciones que implementan `ErrorResponse` y no son 5xx
  conservan su código, pero llevan el **mensaje genérico** "Ocurrió un problema...". Todo lo demás es
  500. Consecuencias para multipart:
  - `MaxUploadSizeExceededException`, cuando el archivo supera `spring.servlet.multipart.max-file-size`,
    se resuelve antes del controlador. Respondería 413 (si implementa `ErrorResponse`) o 500, **siempre
    con mensaje genérico, que no cumple CA2**. Hace falta un `@ExceptionHandler` propio con el motivo
    ("El archivo supera 5 MB") o subir el límite de multipart y validar el tamaño en el servicio.
  - `MultipartException` y `MissingServletRequestPartException` tampoco tienen handler propio.
- No existen `RecursoNoEncontradoException` ni `AccesoDenegadoException` (ver 1.2).

### 1.5 Auditoría (`comun/auditoria`)

- `AuditoriaService.registrar(EventoAuditoria)` en `AuditoriaService.java:21-24`, con fecha del
  `Clock`.
- `record EventoAuditoria(Long usuarioId, String accion, String entidad, Long entidadId, String ip,
  String detalle)` en `EventoAuditoria.java:3-4`.
- Tabla `auditoria` (`V1_2__auditoria.sql`): `accion VARCHAR(60)`, `entidad VARCHAR(60)`,
  `detalle TEXT`.
- El patrón de uso está en `SesionService.java:61-68`: constantes `ACCION_*` públicas en el servicio,
  `ENTIDAD_*` privadas y la IP sacada de `IpCliente.de(peticion)` en el controlador
  (`comun/web/IpCliente.java:9-11`, `getRemoteAddr`).
- RNF-SEG-12 (`docs/requerimientos.md:157`) incluye "cargas de documentos" y la historia pide
  "(R) Registro de cada carga". Hay que definir los nombres de acción, p. ej.
  `CARGA_DOCUMENTO_IDENTIDAD` y `CARGA_DOCUMENTO_RECHAZADA`, y que `detalle` no lleve contenido ni
  número de documento: a lo sumo tipo, MIME y tamaño.

### 1.6 Rate limiting (`comun/limite`)

- `LimitadorIntentos.java:23-37` trabaja con una ventana deslizante en memoria por clave. Tiene
  `intentar` y, desde HU-03, `superaLimite`, `registrar` y `limpiar` (l. 39-60).
- `FiltroLimiteIntentos.java:34-46` es un `@Component` que aplica a todas las `ReglaLimite` registradas
  como bean. La clave va **por IP** y la respuesta es 429 con `MENSAJE_DEMASIADOS_INTENTOS`.
- `ReglaLimite.java:8-22` es `record(metodo, ruta, maximo, ventana, grupo)` y `aplicaA` compara la
  **ruta exacta** (`equals`, l. 15-17): no acepta patrones ni variables de ruta.
- Las reglas de cuentas están en `cuentas/controller/ReglasLimiteCuentas.java:18-27` (registro: 5 por
  IP por hora, grupo `registro`).
- HU-05 no exige rate limiting (no figura en su STRIDE). Si se quiere frenar el abuso de subidas, se
  puede añadir una `ReglaLimite` para la ruta de carga, por IP. Por usuario habría que llamar a
  `LimitadorIntentos` desde el servicio con clave `"carga|" + usuarioId`, como hizo HU-03 con el login.
  Es una decisión abierta.

### 1.7 Cifrado (`comun/cifrado`) reutilizable para archivos

- `CifradorCampos.java:21-84`: usa AES-256-GCM con IV de 12 bytes y tag de 128. **Solo trabaja con
  `String`**: `cifrar(String)` (l. 46-61) y `descifrar(String)` (l. 63-83) pasan por UTF-8 y producen el
  formato `"v1:" + base64`. Para archivos binarios hace falta una variante `byte[]`. Puede ser un
  método nuevo y aditivo en `CifradorCampos` (`cifrarBytes` y `descifrarBytes`, con el mismo formato
  binario `iv ‖ cifrado+tag`) o un componente `CifradorArchivos` en `comun/cifrado` que reutilice la
  llave `espacioya.cifrado.llave`. `LlaveCifrado` es **package-private** (`LlaveCifrado.java:5,13`),
  así que un componente nuevo tiene que vivir en el mismo paquete `comun.cifrado`.
- `ConvertidorCifrado` (JPA `AttributeConverter`) sirve para cifrar columnas de
  `documento_identidad` si se decide (p. ej. `ruta_archivo`), pero no sirve para el archivo en sí.
- `HuellaCampos.java:30-38` calcula un HMAC-SHA256 con `espacioya.cifrado.llave-huella`. Para firmar
  URLs conviene **una llave propia** (p. ej. `espacioya.almacenamiento.llave-firma`), por el mismo
  argumento de llaves separadas de DT-3 de HU-02 (`specs/HU-02/design.md:23`). Es una decisión
  abierta.
- Llaves: en `application.properties:24-25` (por variables de entorno, vacías por defecto, y el
  backend no arranca sin ellas) y en `src/test/resources/config/application.properties:3-4` (llaves de
  prueba, DT-4 de HU-02). Cualquier llave nueva sigue ese mismo patrón, y además va en
  `2_Backend/.env.example`.

### 1.8 Subida de archivos y almacenamiento: no existe nada

- `grep -i "multipart|MultipartFile"` sobre `2_Backend/src` y `3_frontend/src` da **0 resultados**.
- No hay ninguna propiedad `spring.servlet.multipart.*` en `application.properties`. Los valores por
  defecto de Spring Boot son `max-file-size=1MB` y `max-request-size=10MB`: **con ellos un archivo de
  más de 1 MB falla antes del controlador**, así que hay que configurarlos (p. ej. 5MB por archivo y
  unos 11MB por solicitud si viajan dos archivos). Ojo también con `server.tomcat.max-swallow-size`
  (2 MB por defecto): si la solicitud lo supera, Tomcat puede cortar la conexión y el cliente no
  recibe un mensaje legible.
- No hay ningún componente de almacenamiento. `docs/architecture.md:19, 54, 64, 88-89, 111, 223, 225`
  exige:
  - almacenamiento privado fuera de la BD;
  - URL firmada de corta duración;
  - un **adaptador** en `comun` ("almacenamiento" figura en la lista de `comun`);
  - "No usar el SDK... del almacenamiento fuera de su adaptador";
  - "No guardar archivos en la base de datos ni servirlos con URL pública".
- `docs/requerimientos.md:281` (§8): "Almacenamiento privado cifrado con URL firmada" es un componente
  transversal **cuya primera historia es HU-05** (Sprint 2), y lo reutilizan HU-06 y HU-07 (fotos).
  Por lo tanto lo construye HU-05, en `comun`.
- En UAT el destino previsto es S3 privado: `4_Infraestructure/arquitectura-aws.html` muestra
  "Núcleo → S3 privado" con la etiqueta "URL FIRMADA". **No hay SDK de AWS en el `pom.xml`** y en
  local no existe MinIO ni nada parecido (`1_Database/compose.yaml` solo levanta PostgreSQL).
- No hay detección del tipo real. **No hay Apache Tika**: validarlo con *magic bytes* sin dependencias
  es viable: PDF `25 50 44 46 2D` (`%PDF-`), PNG `89 50 4E 47 0D 0A 1A 0A`, JPEG `FF D8 FF`.
  RNF-SEG-08 (`docs/requerimientos.md:153`) pide además "imágenes reprocesadas para eliminar
  metadatos", citando HU-05 (T) y HU-07 (T/I).

### 1.9 Migraciones Flyway

| Archivo | Historia | Contenido |
|---|---|---|
| `V1_1__usuarios.sql` | HU-01 | `usuario` |
| `V1_2__auditoria.sql` | HU-01 | `auditoria` + índices |
| `V1_3__perfil_arrendador.sql` | HU-02 | `usuario.nombre/telefono`, `perfil_arrendador` |
| `V1_4__sesion.sql` | HU-03 | `sesion` |

- La siguiente es **`V1_5__documento_identidad.sql`**.
- Patrón que se repite: identidad `BIGINT GENERATED ALWAYS AS IDENTITY`, `TIMESTAMPTZ`, `CONSTRAINT`
  con nombre (`uq_`, `ck_`, `ix_`) y comentario inicial con la historia.
- **FK con `ON DELETE CASCADE`**: las IT de HU-01, HU-02 y HU-03 hacen `DELETE FROM perfil_arrendador`
  y `DELETE FROM usuario` en su `@BeforeEach` (`SesionControllerIT.java:99-109`). Si
  `documento_identidad.arrendador_id` apunta a `perfil_arrendador(usuario_id)` sin cascada, esas IT
  fallan en cuanto quede un documento en la BD compartida. Es el mismo motivo de DT-11 de HU-02 y de
  la cascada de `V1_4`.
- `ddl-auto=validate` (`application.properties:12`): la entidad JPA tiene que coincidir exactamente con
  la tabla.

### 1.10 Dependencias (`2_Backend/pom.xml`)

- Spring Boot **4.1.2** con Java **25** (l. 9, 22). Starters: `webmvc`, `validation`, `data-jpa`,
  `flyway` + `flyway-database-postgresql`, `postgresql`, `security`. Jackson 3 (`tools.jackson.*`,
  visto en `ConfiguracionSeguridad.java:21`).
- Test: `webmvc-test`, `validation-test`, `data-jpa-test`, `flyway-test`, `security-test`,
  `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`.
- `maven-failsafe-plugin`: `*IT` corre con `./mvnw verify` y las unitarias con `./mvnw test`
  (l. 113-117).
- **No hay** Tika, SDK de AWS (S3), ImageIO extra ni una librería de PDF. El multipart viene incluido en
  `spring-boot-starter-webmvc` (Servlet `Part` de Tomcat), sin dependencia nueva.

### 1.11 Estructura de tests

- **Unitarias** (sin Spring): `cuentas/domain/*Test` (p. ej. `PerfilArrendadorTest`, `UsuarioTest`),
  `cuentas/service/*ServiceTest` con Mockito, y `comun/**/*Test` (`CifradorCamposTest`,
  `LimitadorIntentosTest`, `FiltroAutenticacionSesionTest`, `ManejadorGlobalErroresTest`).
- **IT**: `@SpringBootTest @AutoConfigureMockMvc @Import({TestcontainersConfiguration.class,
  X.Dobles.class})` (`SesionControllerIT.java:52-54`), con PostgreSQL 17 real
  (`TestcontainersConfiguration.java:14-22`). `Dobles` reemplaza el `Clock` por un `RelojControlable`
  `@Primary` (l. 89-97).
- Utilidades que HU-05 puede copiar de `SesionControllerIT`:
  - `crearArrendador` crea el usuario y su `PerfilArrendador` (l. 124-130);
  - `token(correo)` inicia sesión por la API y devuelve el token;
  - `desde(...)` fija la IP remota;
  - un `jdbc.update("UPDATE perfil_arrendador SET estado_identidad = ...")` fuerza estados (l. 268-269),
    útil para CA3 con `RECHAZADA` mientras HU-06 no exista.
- Convención de IPs por historia: HU-01 usa `10.1.x.x`, HU-02 `10.2.x.x` y HU-03 `10.3.x.x`
  (comentario en l. 114-117). Para HU-05 corresponde `10.5.x.x`.
- La limpieza va en `@BeforeEach` con `DELETE` en orden de FK, y la nueva tabla tendría que entrar en
  esa lista. Para el almacenamiento local en las IT conviene un directorio temporal (propiedad
  sobrescrita en `Dobles`, `@TempDir` o `@DynamicPropertySource`) para no ensuciar el repo.
- Multipart en MockMvc: `MockMvcRequestBuilders.multipart(...)` con
  `.file(new MockMultipartFile(...))`, también disponible en `webmvc-test`.

---

## 2. Frontend

### 2.1 Kit `src/components/ui/`

| Componente | Ruta:línea | Notas para HU-05 |
|---|---|---|
| `Campo` | `components/ui/Campo.jsx:4-78` | Sus tipos son `text` (por defecto), `checkbox` (l. 23-39) y `select` (l. 43-63). El genérico (l. 65-76) pasa `evento.target.value` a `onChange` y es **controlado** (`value={valor}`). **No sirve para `type="file"`**: un input de archivo no puede ser controlado y hay que leer `evento.target.files[0]`. Hace falta añadir `tipo="archivo"` (con `accept`) a `Campo` o crear un componente nuevo en el kit (`docs/frontend.md:158-160`: "si falta uno, se agrega al kit"). Ya trae etiqueta, `ayuda` y `error` con `aria-describedby`. |
| `Boton` | `components/ui/Boton.jsx:3-22` | Tiene `variante`, `cargando` (muestra "Enviando…" y queda deshabilitado), `deshabilitado` y `tipo`. |
| `Alerta` | `components/ui/Alerta.jsx:3-9` | Tiene `tipo` (`exito`, `atencion`, `error`, `info`) y aplica `role="alert"` cuando el tipo es error. |
| `EtiquetaEstado` | — | **No existe.** `docs/frontend.md:127-135` define los colores de identidad: `VERIFICADA` éxito, `EN_REVISION` atención, `RECHAZADA` error, `NO_INICIADA` neutro. HU-02 la dejó de lado a propósito (DT-16: "la primera historia que muestre un estado lo construye"). Si HU-05 muestra el estado de identidad, le toca construirla. |
| `Tarjeta`, `Modal` | — | No existen. |

### 2.2 `src/api/`

- `api/cliente.js:25-60`, `peticion(ruta, { metodo, cuerpo, autenticada })`:
  - **siempre pone `Content-Type: application/json` y hace `JSON.stringify(cuerpo)`** (l. 27-30). No
    admite `FormData`. Hay que extenderlo de forma aditiva: si `cuerpo instanceof FormData`, no poner
    `Content-Type` (el navegador agrega el boundary) y no serializar.
  - Pone el Bearer solo si hay token (l. 31-34).
  - Ante un 401 con token, borra el token y avisa a `alPerderSesion` (l. 53-56).
  - Normaliza los errores a `{estado, mensaje, errores}` (l. 48-52) y los de red a "No pudimos
    conectar. Intenta de nuevo." (l. 5).
- `api/cuentas.js`: una función por endpoint. `obtenerSesionActual()` está en la l. 52. HU-05 agregaría
  aquí, por ejemplo, `enviarDocumentosIdentidad(formData)` y `obtenerEstadoIdentidad()`.
- `api/tokenSesion.js`: lee y escribe el token en `sessionStorage` con la clave `espacioya.token`.

### 2.3 Sesión y rutas

- `sesion/ProveedorSesion.jsx`: guarda el estado `{ rol, aviso }` (l. 19, 41) y lo restaura con
  `obtenerSesionActual()` al montar. `entrar` y `salir` se exponen por el contexto.
- `sesion/RutaProtegida.jsx:4-9`: con sesión muestra `children`; sin sesión redirige a
  `/iniciar-sesion`. **No filtra por rol.** Para una ruta solo de arrendador, el rol "solo elige qué
  mostrar" (`docs/frontend.md:213-214`): el backend es el que restringe.
- `App.jsx:70-112`: rutas `/`, `/registro`, `/registro-arrendador`, `/privacidad`, `/iniciar-sesion` y
  `/panel` (protegida, l. 91-98). Las rutas van en español, en minúsculas y con guiones
  (`docs/frontend.md:199-200`), p. ej. `/verificar-identidad` o `/panel/identidad`.

### 2.4 `Panel`

- `pages/cuentas/Panel.jsx`: `TEXTOS_POR_ROL` (l. 7-10). Al montar vuelve a pedir
  `obtenerSesionActual()` y actualiza la sesión (l. 15-27). Muestra `sesion.aviso` como
  `Alerta tipo="atencion"` (l. 34).
- Para CA3, el panel tiene que mostrar el motivo de rechazo y un acceso para volver a subir. Para eso
  necesita saber el estado (o recibirlo en un aviso diferenciado) y `Alerta` de tipo `error` o
  `atencion` según lo decida el spec. Hoy no hay ningún enlace a otra pantalla dentro del panel.

### 2.5 Lo que dice `docs/frontend.md`

- Errores por campo (l. 185): texto `--color-error-texto` bajo el `Campo`. El cliente valida solo como
  ayuda y el servidor revalida siempre. Esto aplica a "formato o tamaño no permitido": el cliente puede
  avisar antes con `accept=".pdf,.jpg,.jpeg,.png"` y con `file.size`, pero CA2 se cumple en el backend.
- Errores de la API (l. 186): `Alerta` de error arriba con el `ErrorDTO.mensaje` tal cual, y además los
  `errores[]` bajo cada `Campo`.
- Operación en curso (l. 189): `Boton cargando`.
- Mensajes de seguridad genéricos y sin códigos HTTP (l. 192-195).
- Un solo botón primario por pantalla (l. 108-110).
- Nada específico sobre subida de archivos: no hay patrón previo.
- Pruebas de frontend: no hay ninguna. El mínimo es `npm run lint && npm run build`
  (`docs/verification.md:104-117`). `init.sh` rechaza colores fuera de `tokens.css` y librerías de UI.
- `package.json`: solo `react`, `react-dom` y `react-router-dom`. Sin librerías de subida, cosa que no
  hace falta.

---

## 3. Huecos y decisiones abiertas

### 3.1 Qué falta construir

1. **Componente transversal de almacenamiento en `comun`** (p. ej. `comun/almacenamiento/`):
   - una interfaz propia (Adapter), por ejemplo `AlmacenamientoArchivos` con
     `guardar(bytes, ruta) / leer(ruta) / urlFirmada(ruta, duracion) / eliminar(ruta)`;
   - una implementación local en disco para desarrollo e IT;
   - quizás una implementación S3 para UAT;
   - el cifrado del contenido en reposo (AES-GCM con una variante `byte[]`, ver 1.7).
   Lo reutilizan HU-06 y HU-07.
2. **URL firmada de corta duración**: para el adaptador local, un endpoint propio
   (p. ej. `GET /api/archivos/{id}?expira=...&firma=HMAC`) que valide la firma y el vencimiento,
   descifre y sirva el archivo con `Content-Disposition` y `X-Content-Type-Options: nosniff`.
   Con S3 sería una URL prefirmada, pero el archivo va cifrado por la app, así que S3 devolvería
   bytes cifrados: hay que decidir si el cifrado es de la app o del proveedor (SSE-KMS).
3. **Validación del tipo real y del tamaño**: *magic bytes* para PDF, JPG y PNG; una lista blanca de
   extensiones y MIME declarados (la extensión y el `Content-Type` del cliente no bastan); el límite de
   5 MB; archivo vacío; y mensajes por campo para CA2 (`ReglaNegocioException(campo, motivo)`).
4. **Configurar `spring.servlet.multipart.*`** y escribir un handler de `MaxUploadSizeExceededException`
   y `MultipartException` con un mensaje claro (RNF-USA-03), no el genérico.
5. **Entidad, repositorio y migración `documento_identidad`** (`V1_5`), con FK en cascada y `tipo`
   restringido por `CHECK`.
6. **Transición de dominio** en `PerfilArrendador` (`NO_INICIADA|RECHAZADA → EN_REVISION`), con su
   test unitario. Al reenviar se limpia o se conserva `motivoRechazo` (por decidir).
7. **Servicio y controlador** en `cuentas`, p. ej. `POST /api/arrendadores/actual/documentos`
   (multipart), con:
   - la identidad sacada de `@AuthenticationPrincipal`;
   - la restricción a `ARRENDADOR`: con `hasRole` en `ConfiguracionSeguridad` y un handler de 403 con
     `ErrorDTO`, o con la comprobación en el servicio y una `AccesoDenegadoException` nueva en
     `comun/error` (`docs/conventions.md:107`);
   - un 409 o 400 si el estado no permite subir.
8. **Auditoría** de cada carga, aceptada o rechazada.
9. **`AvisoCuenta`**: textos para `EN_REVISION` y `RECHAZADA`, este último con el motivo o con un
   aviso que lleve a reenviar. Puede hacer falta ampliar `SesionActualDTO` o crear un endpoint del
   estado de identidad.
10. **Frontend**:
    - soporte de `FormData` en `cliente.js`;
    - `Campo` de archivo, o un componente nuevo en el kit;
    - quizás `EtiquetaEstado`;
    - una pantalla nueva `pages/cuentas/<VerificarIdentidad>.jsx` con su ruta protegida;
    - un enlace desde `Panel`.
11. **Docs que se tocan en el mismo PR**:
    - `docs/modelo-datos.md`, si cambian las columnas de `documento_identidad`;
    - `docs/architecture.md` (principio 7), si se agrega alguna restricción;
    - `docs/frontend.md` (kit, `EtiquetaEstado`, archivo);
    - `2_Backend/.env.example`, si hay llaves o rutas nuevas.

### 3.2 Decisiones abiertas (nada en `docs/` las cierra)

| # | Pregunta | Qué dicen los docs |
|---|---|---|
| A1 | **¿Dónde se guardan los archivos?** ¿Disco local con un adaptador intercambiable (local e IT) más S3 en UAT, o solo local por ahora? | "Almacenamiento privado fuera de la BD" + Adapter (`architecture.md:19, 64, 223`). La infraestructura AWS dibuja un S3 privado. No hay SDK ni configuración. |
| A2 | **¿Cifrado en la app (AES-GCM) o del proveedor (SSE)?** ¿Con la misma llave `CIFRADO_LLAVE` o con una nueva? | La historia pide "(I) Documentos cifrados en almacenamiento privado". RNF-SEG-14 dice "documentos". |
| A3 | **¿Cuánto dura la URL firmada** (p. ej. 5 min) y quién la genera y la consume? ¿Con una llave de firma propia? | Solo dice "corta duración" (RNF-SEG-15). |
| A4 | **¿Qué documentos son exactamente?** ¿Dos obligatorios (DNI/RUC y propiedad o autorización) en una sola solicitud? ¿Valores del enum `tipo`? ¿Más de un archivo por tipo (p. ej. anverso y reverso del DNI)? | CA1: "los documentos requeridos". RF-A-14: "DNI/RUC y un documento de propiedad o autorización". |
| A5 | **¿El límite de 5 MB es por archivo o por envío?** ¿Cuenta el tamaño en MB decimales o MiB? | CA1 y RNF-USA-02: "PDF/JPG/PNG de hasta 5 MB". |
| A6 | **¿Se reprocesan las imágenes para quitar metadatos (EXIF)?** RNF-SEG-08 cita HU-05. ¿Y con los PDF? | RNF-SEG-08: "imágenes reprocesadas para eliminar metadatos" (HU-05 (T), HU-07 (T/I)). |
| A7 | **¿Qué pasa al reenviar tras un rechazo?** ¿Se borran los documentos anteriores, se marcan como reemplazados o se conservan como historial? ¿Se limpia `motivo_rechazo` al pasar a `EN_REVISION`? | CA3: "puedo volver a subir documentos". No dice nada de retención. |
| A8 | **¿Se puede subir estando en `EN_REVISION` o `VERIFICADA`?** | RN-01: el correo verificado sube documentos y el rechazado vuelve a subirlos. RN-02 / HU-20: cambiar el RUC con identidad verificada vuelve a "en revisión", pero eso es de HU-20. |
| A9 | **¿Cuánto tiempo se conservan los documentos y cómo se eliminan?** | No hay plazo. HU-22 (supresión) los afectará. El plazo contable (§9.6) también sigue pendiente. |
| A10 | **¿Quién revisa y cómo accede el admin?** HU-06 (Ariel Enriquez, módulo `administracion`) muestra la cola y los documentos con URL firmada. Hoy el admin **no puede iniciar sesión** (DT-7 de HU-03, `Usuario.java:85-86`). | HU-05 (E): "solo el propio arrendador y el admin acceden". HU-05 tiene que dejar listo el puerto o servicio para que HU-06 genere URLs firmadas (p. ej. un método de `cuentas` que use `administracion`, según la dirección `administracion → (todos)`, `architecture.md:124`). La parte del admin no se puede probar de punta a punta en HU-05. |
| A11 | **¿Puede el arrendador ver o descargar lo que subió?** | (E) lo permite, pero ningún CA lo pide. |
| A12 | **¿Se aplica rate limiting a las subidas?** | No está en el STRIDE de HU-05. RNF-SEG-17 no la lista. |
| A13 | **¿Se avisa por correo al pasar a "en revisión"?** | RNF-USA-04: avisos por correo en los eventos de "identidad". `NotificadorCorreo` existe, y está simulado. |
| A14 | **¿Cómo se ve el motivo de rechazo** en el panel (`Alerta` de error o de atención, `EtiquetaEstado`) y por qué vía llega (aviso, DTO ampliado o endpoint nuevo)? | `frontend.md:135` fija los colores por estado. |
| A15 | **¿Cómo se nombran los archivos y su ruta?** Hay que evitar el nombre del cliente (path traversal, fuga de datos): ruta aleatoria y nombre original no guardado o cifrado. | `ruta_archivo` está en el modelo, sin más detalle. |

### 3.3 Riesgos detectados

- Si las propiedades de multipart no se configuran, cualquier archivo de más de 1 MB falla con 500 o 413
  y mensaje genérico. CA1 y CA2 no pasarían.
- Si la FK de `documento_identidad` no tiene cascada, se rompen las IT existentes de HU-01, HU-02 y
  HU-03, por sus `DELETE FROM perfil_arrendador/usuario`.
- Si se usa `hasRole` sin `AccessDeniedHandler`, el 403 no tendrá forma de `ErrorDTO`.
- Si se cambia `AvisoCuenta` hay que mantener en verde `SesionControllerIT` (`:253-274`, `:441-450`).
- Las IT que escriban archivos tienen que usar un directorio temporal. Ningún archivo de prueba debe
  quedar en el repo: `init.sh` revisa secretos y archivos trackeados.
