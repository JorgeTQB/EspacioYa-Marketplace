# Exploración — HU-07 · Publicar y editar un espacio

> Explorador de solo lectura, 2026-10-28. Rutas relativas a la raíz del repo.
> `B` = `2_Backend/src/main/java/pe/edu/ulima/espacioya`, `F` = `3_frontend/src`.

## Preguntas

1. Estructura del backend; qué hay reutilizable en `comun`.
2. Usuario autenticado y rol en un controlador; correo verificado e identidad del arrendador.
3. Subida de archivos de HU-05; qué se reutiliza para las fotos.
4. Migraciones Flyway y lo que dice `docs/modelo-datos.md` sobre espacios.
5. Frontend: kit `components/ui/`, `src/api/`, rutas, área del arrendador.
6. Lo relevante de `docs/requerimientos.md` y `docs/architecture.md`.

## Archivos analizados

- `docs/historias/HU-07.md`, `docs/architecture.md`, `docs/modelo-datos.md`, `docs/requerimientos.md` (RF-B, RNF-SEG, RN, §8), `docs/conventions.md` (migraciones), `docs/frontend.md` (kit, estados, rutas)
- `B/comun/**` (almacenamiento, auditoria, error, limite, seguridad, web), `B/cuentas/**` (controller, domain, service y repository de HU-02/03/05)
- `2_Backend/src/main/resources/application.properties`, `.../db/migration/*.sql`
- `F/App.jsx`, `F/api/cliente.js`, `F/api/cuentas.js`, `F/components/ui/*`, `F/pages/cuentas/Panel.jsx`, `F/sesion/*`
- `feature_list.json` (HU-06 `pending`, HU-07 `pending`, sprint 2, módulo `espacios`)

## 1. Estructura del backend y `comun`

- Paquetes: `B/comun/` y `B/cuentas/` (`controller`, `domain`, `dto`, `repository`, `service`). **No existe nada de `espacios`** (ni paquete, ni tabla, ni frontend). `docs/architecture.md` Principio 4: `espacios` para HU-07, 08 y 21.
- Lo reutilizable de `comun`:
  - **Errores** (`B/comun/error/`): `ReglaNegocioException(campo?, mensaje)` → 400 (con error por campo si trae `campo`); `ValidacionException(List<ErrorCampoDTO>)` → 400 con varios campos; `AccesoDenegadoException` → 403 genérico; `RecursoNoEncontradoException` → 404 genérico; `DemasiadosIntentosException` → 429; `MaxUploadSizeExceededException`/`MultipartException` → 400. Respuesta: `ErrorDTO(mensaje, errores)` + `ErrorCampoDTO(campo, mensaje)`; constantes de mensajes en `ErrorDTO`. Todo lo maneja `ManejadorGlobalErrores`.
  - **Auditoría** (`B/comun/auditoria/`): `AuditoriaService.registrar(new EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle))`; `detalle` es TEXT libre (sirve para el "qué cambió" de (R)). IP con `IpCliente.de(request)` (`B/comun/web/IpCliente.java`). Convención: constantes `ACCION_*` y `ENTIDAD_*` en el servicio (ver `IdentidadArrendadorService`).
  - **Rate limiting** (`B/comun/limite/`): `ReglaLimite(metodo, ruta, maximo, ventana, grupo?)` registrada como `@Bean` (ver `B/cuentas/controller/ReglasLimiteCuentas.java`), aplicada por `FiltroLimiteIntentos` **por IP y con coincidencia exacta de ruta** (no soporta variables de ruta como `/api/espacios/{id}`). `LimitadorIntentos` también tiene `intentar/superaLimite/registrar/limpiar` para usarlo desde un servicio con otra clave.
  - **Almacenamiento** (`B/comun/almacenamiento/`): ver §3.
  - **Cifrado** (`B/comun/cifrado/`): `ConvertidorCifrado` (JPA), `CifradorArchivos`, `HuellaCampos`.
  - **Seguridad** (`B/comun/seguridad/`): `UsuarioAutenticado`, `FiltroAutenticacionSesion`, `ConfiguracionSeguridad`, puerto `ValidadorSesion`.
  - **Reloj**: `B/comun/configuracion/ConfiguracionReloj.java` expone un `Clock` (tests usan `RelojControlable`). Transacciones programáticas con `TransactionTemplate` (usado en HU-05).

## 2. Usuario autenticado, rol, correo e identidad

- **En el controlador:** `@AuthenticationPrincipal UsuarioAutenticado usuario` → `record UsuarioAutenticado(Long usuarioId, Long sesionId, String rol)` (`B/comun/seguridad/UsuarioAutenticado.java`). Authority `ROLE_<rol>`. `ConfiguracionSeguridad`: `anyRequest().authenticated()`, sin `@PreAuthorize`; **el rol se valida en el servicio** (`if (!Rol.ARRENDADOR.name().equals(usuario.rol())) throw new AccesoDenegadoException()`, ver `IdentidadArrendadorService.cuentaArrendador`). Sin sesión → 401 vía `PuntoEntradaNoAutenticado`.
- **Correo verificado:** `usuario.correo_verificado` (BOOLEAN) → `Usuario.isCorreoVerificado()` (`B/cuentas/domain/Usuario.java`). **Hoy siempre es `true`**: `Usuario.registrar` lo fija así hasta HU-25 (decisión S5 de HU-01); además el login rechaza cuentas sin correo verificado (`motivoRechazoInicioSesion`). HU-05 igual lo comprueba (`!cuenta.isCorreoVerificado()` → 403).
- **Identidad del arrendador:** tabla `perfil_arrendador` (PK `usuario_id`), `estado_identidad` ∈ `NO_INICIADA | EN_REVISION | VERIFICADA | RECHAZADA` (`B/cuentas/domain/EstadoIdentidad.java`, `PerfilArrendador.java`). `PerfilArrendador.AVISO_IDENTIDAD` = "Para publicar tus espacios, primero debes verificar tu identidad." (útil para CA3); `avisoIdentidad()` da el texto por estado. Repos: `UsuarioRepository`, `PerfilArrendadorRepository` (`findParaActualizar` con `PESSIMISTIC_WRITE`).
- `usuario.estado` ∈ `ACTIVA | SUSPENDIDA | ELIMINADA` (`EstadoUsuario`).
- **Restricción de módulos:** `espacios` no puede usar repositorios de `cuentas` (architecture Principio 5 / "Qué NO hacer"). Debe pasar por una interfaz de servicio de `cuentas` (architecture menciona `CuentaService` como servicio público, **aún no existe**). El design deberá crearla (p. ej. consulta de "correo verificado" y "estado de identidad" del arrendador). Nota: hoy **nadie pasa a `VERIFICADA`** hasta HU-06 (`pending`): para probar CA2 los tests deberán fijar el estado en BD (de ahí "visible tras HU-06").
- Sesión actual: `GET /api/sesiones/actual` → `SesionActualDTO(rol, aviso)`; `aviso` sale de `AvisoCuenta` (por estado de identidad).

## 3. Subida de archivos en HU-05 y reutilización para fotos

Flujo (`B/cuentas/controller/IdentidadArrendadorController.java` + `B/cuentas/service/IdentidadArrendadorService.java`):

1. Controlador `multipart/form-data` con `@RequestPart(required=false) MultipartFile`; convierte a `ArchivoRecibido(contenido, tamano)` sin leer bytes si `size > ValidadorArchivo.TAMANO_MAXIMO` (helper privado `recibido(...)`, copiable).
2. Servicio: rol/correo/estado **antes** de mirar archivos; luego `ValidadorArchivo.validar(ArchivoRecibido)` → `ResultadoValidacion.Aceptado(ArchivoValidado)` | `Rechazado(MotivoRechazoArchivo)`; errores por campo acumulados → `ValidacionException`; rechazos auditados.
3. `ValidadorArchivo` (`B/comun/almacenamiento/ValidadorArchivo.java`): máx. 5 MiB, tipo real por magic bytes (PDF/PNG/JPEG), tope 40 MP de píxeles, **reencodifica PNG/JPEG con ImageIO (JPEG calidad 0.9), eliminando EXIF/metadatos** → cubre (T/I) de HU-07.
4. `ArchivosPrivados.guardar(carpeta, bytes)` cifra (`CifradorArchivos`) y guarda vía adaptador `AlmacenamientoArchivos` (`AlmacenamientoLocal`, escritura atómica, directorio `espacioya.almacenamiento.directorio`); devuelve clave `<carpeta>/<UUID>` (carpeta debe cumplir `^[a-z]+$`, p. ej. `espacios`). `leer`, `eliminar`, `urlFirmada(clave, mime)`.
5. Persistencia en transacción (`TransactionTemplate`); si falla se borran los archivos nuevos; los reemplazados se borran tras el commit (`eliminarSinFallar`).
6. Servir: `FirmadorUrl` (HMAC, **5 min**) + `ArchivoController` `GET /api/archivos?clave&tipo&expira&firma` (público, `no-store`, `nosniff`).

Reutilizable tal cual: `ValidadorArchivo`, `ArchivoRecibido/ArchivoValidado`, `ArchivosPrivados`, `FirmadorUrl`/`ArchivoController`, patrón de compensación de archivos y auditoría de rechazos.

Puntos a decidir en el design:
- `ValidadorArchivo` **acepta PDF**: para fotos hay que rechazar `TipoArchivo.PDF` (en el servicio o con una variante "solo imagen"); el mensaje `MotivoRechazoArchivo.TIPO_NO_PERMITIDO` dice "PDF, JPG o PNG" → hace falta otro mensaje para fotos.
- `ArchivoController` pone `filename="documento.<ext>"` fijo y `Cache-Control: no-store`: funciona para fotos, aunque no es ideal para catálogo; URL de 5 min → el catálogo (HU-10/11) tendría que firmar en cada consulta. Las fotos también quedarían cifradas (requerimientos §8 dice que HU-07 reutiliza este componente para fotos).
- Límites multipart (`application.properties`): `max-file-size=6MB`, `max-request-size=13MB`, `max-swallow-size=20MB`. Subir 5 fotos de 5 MB en una sola solicitud (25 MB) **excede** el límite: o se sube foto a foto (endpoint por foto) o se ajustan las propiedades.
- Máximo 5 fotos: validar en servidor y, de ser posible, en BD (`orden` 1–5 con CHECK + único `(espacio_id, orden)`).

## 4. Migraciones y modelo de datos

- En `2_Backend/src/main/resources/db/migration/`: `V1_1__usuarios.sql`, `V1_2__auditoria.sql`, `V1_3__perfil_arrendador.sql`, `V1_4__sesion.sql`, `V1_5__token.sql`, `V2_1__documento_identidad.sql`. **Última: `V2_1`**. HU-07 es sprint 2 → siguiente libre `V2_2__espacio.sql` (confirmar en `develop`; HU-06, también sprint 2, podría tomar `V2_2` antes → renombrar si choca).
- `spring.flyway.out-of-order=true` (`application.properties`), reglas en `docs/conventions.md` §"Migraciones que llegan tarde": número = sprint de la historia + siguiente `n` libre en `develop`; no editar migraciones subidas; una migración no depende de otra de número mayor. `ddl-auto=validate`.
- Estilo de las migraciones: `BIGINT GENERATED ALWAYS AS IDENTITY`, `TIMESTAMPTZ`, CHECK para enums (`ck_<tabla>_<campo>`), `uq_`, `ix_`, FKs, comentario de cabecera con la HU.
- `docs/modelo-datos.md`:
  - `espacio`: id, arrendador_id, nombre, tipo (`CANCHA`, `ESTADIO`, `AUDITORIO`), direccion, zona, capacidad, precio_hora, descripcion, estado (`BORRADOR`, `PUBLICADO`, `PAUSADO`, `BAJA`, `OCULTO`) — HU-07, 21, 27.
  - `foto_espacio`: id, espacio_id, ruta_archivo, orden (máximo 5) — HU-07.
  - ER: `PERFIL_ARRENDADOR ||--o{ ESPACIO`, `ESPACIO ||--o{ FOTO_ESPACIO` (FK de espacio a `perfil_arrendador(usuario_id)`, como `documento_identidad`).
  - `reserva.precio_hora_aplicado` (HU-12) es lo que garantiza RN-09/CA4: el precio se copia a la reserva, así que editar `precio_hora` no la afecta. Tabla `reserva` **aún no existe**.
  - **Zona:** no hay catálogo de zonas en ningún doc (ni tabla, ni enum): solo "zona" como atributo. Pregunta abierta (texto libre vs. lista cerrada de distritos; afecta a la búsqueda de HU-10).
  - No se mencionan `tipo_mime` ni `tamano` en `foto_espacio` (sí en `documento_identidad`); si el design los agrega, actualizar `modelo-datos.md` en el mismo PR.

## 5. Frontend

- Kit `F/components/ui/`: `Boton` (`variante`, `cargando`, `deshabilitado`, `onClick`, `tipo`), `Campo` (`etiqueta`, `nombre`, `tipo` incl. `select` con `opciones` y `archivo` de **un solo archivo**, no controlado, con `accept`; `valor`, `onChange`, `error`, `ayuda`), `Alerta` (`tipo`), `EtiquetaEstado` (mapa solo con estados de identidad; `docs/frontend.md` fija para Espacio: `PUBLICADO` éxito, `PAUSADO` atención, `OCULTO` error, `BORRADOR`/`BAJA` neutro → HU-07 debe agregarlos). **No existen** `Tarjeta` ni `Modal` (listados en `docs/frontend.md`; los construye la primera historia que los necesite).
- `F/api/cliente.js`: `peticion(ruta, { metodo, cuerpo, autenticada })`, soporta `FormData`, convierte errores a `{ estado, mensaje, errores }`, 401 → pierde sesión; `urlApi(ruta)` para URLs firmadas. `F/api/cuentas.js`: funciones de cuentas (incl. `enviarDocumentosIdentidad` con `FormData`, `obtenerIdentidad`). Para HU-07 corresponde un nuevo `F/api/espacios.js`.
- Rutas (`F/App.jsx`): `/`, `/registro`, `/registro-arrendador`, `/privacidad`, `/iniciar-sesion`, `/recuperar-contrasena`, `/restablecer-contrasena`, `/panel` y `/panel/identidad` (ambas con `<RutaProtegida>`).
- Área del arrendador: solo `F/pages/cuentas/Panel.jsx` (muestra `sesion.aviso` y, si `rol === 'ARRENDADOR'`, enlace "Verificar mi identidad") y `VerificarIdentidad.jsx`. **No hay** pantallas de espacios ("Mis espacios", crear/editar). Sesión vía `useSesion()` → `{ rol, aviso }`. Rutas en español con guiones (`docs/frontend.md`).

## 6. Requerimientos y arquitectura relevantes

- RF-B-01…04 (`docs/requerimientos.md` l.62-65): crear ficha (tipo, dirección, zona, capacidad, precio por hora, hasta 5 fotos) como borrador con correo verificado; publicar solo con identidad verificada; impedir publicar sin ella e indicar completar la verificación; editar publicado reflejando cambios, reservas pagadas conservan precio.
- RNF: SEG-08 (tipo real, tamaño, reprocesar imágenes), SEG-12 (auditoría de "cambios de espacios"), SEG-19 (propiedad del recurso antes de cada edición/consulta), USA-02 (hasta 5 fotos; 5 MB). **RNF-SEG-17 no fija límite para HU-07** (no hace falta rate limiting salvo que el design lo justifique). RN-09 (editar precio no afecta reservas pagadas), RN-18 (precio único por hora), RN-01 (correo verificado crea borradores; identidad verificada publica; nunca "verificado" a secas).
- `docs/architecture.md`: estados del espacio `BORRADOR → PUBLICADO` (requiere identidad verificada) `⇄ PAUSADO`; `PUBLICADO → BAJA`; `OCULTO` por moderación; suspendido → espacios no se muestran pero conservan estado. HU-07 sin patrón ★ (solo DAO/Repository). Identidad siempre desde la sesión, nunca del cuerpo/URL; controlador nunca llama a repositorio; entidades no salen en la API; reglas/estados solo en servidor; archivos nunca en BD ni con URL pública. Fuera de alcance: más de 5 fotos, video, precios por franja, mapa interactivo. Dirección de dependencias `comun ← cuentas ← espacios`; puertos en `service/puertos/` (p. ej. `espacios.ReservasDelEspacio` lo usarán HU-08/21, no HU-07).
- "Visible en el catálogo" (CA2/CA4): el catálogo es HU-10/11 (pendientes); en HU-07 equivale a quedar en `PUBLICADO` (y quizás un endpoint de lectura pública mínima, a decidir en el design).

## Preguntas abiertas para el spec

1. Zona: ¿lista cerrada (enum/tabla de distritos) o texto libre?
2. Fotos: ¿en la misma solicitud que la ficha o endpoint por foto (límite multipart 13 MB)? ¿obligatoria al menos 1 para publicar?
3. ¿Fotos cifradas en el almacenamiento privado con URL firmada de 5 min (lo que dice §8) o variante pública para catálogo?
4. Interfaz de `cuentas` para que `espacios` consulte correo verificado y estado de identidad (crear `CuentaService` u otra).
5. Campos obligatorios para borrador vs. para publicar (nombre y descripción están en el modelo pero no en la HU); rangos de capacidad y precio (decimal, moneda PEN).
6. Edición de un `PUBLICADO`: ¿sigue publicado? ¿se puede volver a borrador? ¿qué se audita en `detalle` (campos cambiados, sin datos sensibles)?
