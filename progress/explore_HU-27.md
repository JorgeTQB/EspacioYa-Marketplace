# Exploración HU-27 · Reportes y moderación de publicaciones y reseñas

> Explorador (solo lectura), 2026-11-09, rama `feature/HU-27-reportes-moderacion` (base `develop` `d5e0a80`).
> Rutas relativas a la raíz del repo. Backend = `2_Backend/src/main/java/pe/edu/ulima/espacioya/` (abreviado `BE/`).

## Historia (resumen)

`docs/historias/HU-27.md`: Release 2 · Sprint 8 · M · depende de HU-09, HU-17, HU-24. Dueño Kevin Chan.
- CA1 usuario autenticado reporta un **espacio** o una **reseña** eligiendo un motivo → entra a la cola de moderación.
- CA2 admin ve contenido + motivo y puede **mantener** u **ocultar** con un motivo.
- CA3 al ocultar una publicación: correo al arrendador con el motivo y el espacio sale del catálogo.
- STRIDE: (E/S) solo admins con MFA (HU-17); (D) máx. 10 reportes por usuario al día; (R) registrar reportes y acciones.

## Archivos analizados

- Migraciones: `2_Backend/src/main/resources/db/migration/V2_2__espacio.sql`, `V7_2__resena.sql`, `V8_1__reclamos.sql` (y listado completo).
- Espacios: `BE/espacios/domain/{Espacio,EstadoEspacio}.java`, `BE/espacios/service/{EstadoEspacioService,CatalogoEspaciosService}.java`,
  `BE/espacios/controller/EspacioAdminController.java`, `BE/espacios/repository/EspacioRepository.java`.
- Reseñas: `BE/busqueda/domain/{Resena,EstadoResena}.java`, `BE/busqueda/repository/ResenaRepository.java`,
  `BE/busqueda/service/ResenaService.java`, `BE/busqueda/controller/{ResenaController,ReglasLimiteFicha}.java`, `BE/busqueda/dto/ResenaPublicaDTO.java`.
- Administración: `BE/administracion/controller/{ReclamoAdminController,ReclamoController,CuentaAdminController,ConversionReclamos}.java`,
  `BE/administracion/service/{ReclamoService,ReclamoAdminService,AvisosReclamo}.java`, `BE/administracion/service/correo/*`,
  `BE/administracion/dto/{CrearReclamoDTO,MotivoSuspensionDTO}.java`, `BE/administracion/service/impl/DatosPersonalesReclamosImpl.java`.
- Común: `BE/comun/limite/*`, `BE/comun/auditoria/*`, `BE/comun/correo/FabricaCorreo.java`, `BE/comun/seguridad/{ConfiguracionSeguridad,FiltroIpsAdmin,UsuarioAutenticado}.java`,
  `BE/comun/error/ManejadorGlobalErrores.java`, `BE/cuentas/service/CuentaService.java`, `BE/cuentas/service/CambioCorreoService.java` (límite por cuenta).
- Docs: `docs/architecture.md` (l.120-176, 332-363, 580-736, 743-775), `docs/modelo-datos.md`, `docs/requerimientos.md`, `docs/conventions.md` (l.81-105), `docs/frontend.md` (l.136-151, 401-413), `specs/HU-17/design.md` (DT-3, DT-15).
- Frontend: `3_frontend/src/App.jsx`, `3_frontend/src/api/{administracion,busqueda}.js`, `3_frontend/src/components/ui/*`,
  `3_frontend/src/pages/busqueda/{FichaEspacio,ResenasEspacio}.jsx`, `3_frontend/src/pages/reservas/ReportarProblema.jsx`,
  `3_frontend/src/pages/administracion/textosAdministracion.js`, `3_frontend/src/pages/cuentas/Panel.jsx`, `3_frontend/src/pages/espacios/{EditarEspacio,DisponibilidadEspacio,EstadoPublicacion}.jsx`.

---

## 1. Backend: publicaciones (espacios) y reseñas

### Espacio
- **Tabla** `espacio` (`V2_2__espacio.sql` l.36-54). `estado VARCHAR(20)` con `ck_espacio_estado` que **ya admite `OCULTO`** (l.50): `BORRADOR, PUBLICADO, PAUSADO, BAJA, OCULTO`. No hay columna de motivo de ocultamiento → **no hace falta migración para el estado**; el motivo vivirá en `reporte` (o en otra columna/tabla nueva).
- **Enum** `BE/espacios/domain/EstadoEspacio.java` ya tiene `OCULTO` (l.8).
- **Entidad** `BE/espacios/domain/Espacio.java`: transiciones `publicar`, `pausar`, `reanudar`, `darDeBaja` (l.128-158) con predicados `esEditable` (BORRADOR/PUBLICADO/PAUSADO, l.164 → `OCULTO` ya **no** es editable), `esPausable` (PUBLICADO), `esReanudable` (PAUSADO), `admiteBaja` (PUBLICADO|PAUSADO). **No existe** `ocultar(Instant)` ni `esOcultable()`: HU-27 debe agregarlos (patrón: `IllegalStateException` si no cumple + `actualizadoEn = ahora`). Decidir desde qué estados se oculta (¿PUBLICADO y PAUSADO?) y si `OCULTO` es final (docs/architecture.md l.363: "`OCULTO` no admite pausa, reanudación ni baja"; l.807: "`OCULTO` por moderación (HU-27)").
- **Repositorio** `BE/espacios/repository/EspacioRepository.java`: `findParaActualizar(id, arrendadorId)` (l.27-29, `PESSIMISTIC_WRITE` con dueño) y `findParaReservar(id)` (l.31-34, mismo bloqueo **sin** dueño; es el que usa el admin en HU-21). `buscarPublicados*` filtran por estado.
- **Servicio de estado** `BE/espacios/service/EstadoEspacioService.java` (clase concreta, HU-21): `pausarComoAdmin` / `darDeBajaComoAdmin` (l.104-118) = patrón exacto a imitar para `ocultarComoAdmin`: `exigirAdmin` (l.166) → `cualquiera(id)` con `findParaReservar` (l.162) → regla de estado → cambio → auditoría con `ENTIDAD_ESPACIO` y detalle `estado=<ant>-><nuevo>;actor=ADMIN;arrendadorId=<id>` (l.172-183). Constantes de acción `PAUSA_ESPACIO`, `BAJA_ESPACIO`… La baja revisa reservas por terminar con el puerto `ReservasDelEspacio` (l.147-156): decidir si ocultar lo exige (CA3 no lo pide).
- **Rutas admin existentes** `BE/espacios/controller/EspacioAdminController.java`: `POST /api/admin/espacios/{id}/pausa` y `/baja` → `EstadoEspacioDTO(id, estado)`. El controlador vive en `espacios`, no en `administracion`.
- **Visibilidad pública ya cubre OCULTO** sin cambios: todo filtra `estado == PUBLICADO` → `CatalogoEspaciosServiceImpl` l.73-74 y l.148; `DisponibilidadServiceImpl` l.70, 89, 130; `FotoEspacioService` l.129 (fotos públicas solo si PUBLICADO). `CatalogoEspaciosService.esVisible` (l.39-40) = existe, PUBLICADO y arrendador ACTIVA. Ficha → 404, búsqueda no lo lista, no se reserva (CA3 "deja de verse" se cumple solo con cambiar el estado).
- **Interfaz pública de espacios** `BE/espacios/service/CatalogoEspaciosService.java`: sin sesión; útil para el admin: `nombres(ids)` (sin filtro, l.46), `arrendadoresDe(ids)` (l.59), `publicado(id)` (ficha visible). **No hay** método para ocultar ni para leer la ficha completa de un espacio no visible → HU-27 debe agregar una interfaz pública en `espacios` (regla docs/architecture.md l.142-152: "Solo los servicios que usan otros módulos llevan interfaz pública"; `administracion → (todos)` está permitido, l.125-129, no es puerto).

### Reseña (HU-24)
- **Tabla** `resena` (`V7_2__resena.sql`): `estado VARCHAR(10)` con `ck_resena_estado IN ('VISIBLE','OCULTA')` (l.16) — **ya preparado para HU-27** (comentario l.1). Índice parcial `ix_resena_espacio_visible ... WHERE estado='VISIBLE'` (l.20). `uq_resena_reserva` sin filtrar por estado: una `OCULTA` sigue contando como "ya reseñada".
- **Enum** `BE/busqueda/domain/EstadoResena.java`: `VISIBLE, OCULTA` (javadoc: "OCULTA queda para la moderación de HU-27").
- **Entidad** `BE/busqueda/domain/Resena.java`: solo `nueva(...)` y getters. **Falta `ocultar()`** (docs/architecture.md l.648: "HU-27 agrega `Resena.ocultar`"). Sin columna de motivo/fecha de ocultamiento.
- **Repositorio** `BE/busqueda/repository/ResenaRepository.java`: `findByEspacioIdAndEstado` (l.23) y `totales(espacioId, estado)` (l.25-27) ya filtran por `VISIBLE` desde el servicio → ocultar una reseña la saca de la ficha y del promedio sin tocar consultas. No hay `findParaActualizar` con bloqueo (agregar si se quiere serializar).
- **Servicio** `BE/busqueda/service/ResenaService.java`: clase concreta (l.40 "`administracion` aún no la usa (DT-18)"). Usa `VISIBLE` en `deEspacio` (l.124) y `resumen` (l.131). Auditoría `RESENA_CREADA`, entidad `"resena"` (l.46, 55, 160-165). docs/architecture.md l.621: "HU-27 creará la interfaz pública cuando `administracion` la necesite".
- **Rutas** `BE/busqueda/controller/ResenaController.java`: `POST /api/resenas`, `GET /api/resenas/pendientes`, `GET /api/espacios/{id}/resenas` (pública, cupo catálogo, `ReglasLimiteFicha.RUTA_RESENAS`).
- **DTO público** `BE/busqueda/dto/ResenaPublicaDTO.java`: `(id, calificacion, comentario, fecha)` → el `id` que el front usa para reportar (docs/architecture.md l.648-649: "lee el `id` de la reseña de la ficha para `reporte.objetivo_id`").
- **Datos personales**: `BE/busqueda/service/impl/DatosPersonalesResenasImpl.java` (`@Order(3)`) anonimiza el comentario a `Resena.COMENTARIO_ELIMINADO`, conserva estado.

## 2. Módulo `administracion`: qué reutilizar

- **Seguridad admin (sin código nuevo):**
  - `BE/comun/seguridad/FiltroIpsAdmin.java`: toda ruta `/api/admin/**` (l.28-31) exige IP permitida antes de autenticar (403 genérico).
  - MFA admin (HU-17): `SesionService.validar` rechaza la sesión de un `ADMIN` sin MFA activo (specs/HU-17/design.md DT-3). DT-15: "HU-27 ('solo admins con MFA') lo obtiene gratis" poniendo sus rutas bajo `/api/admin/`.
  - Rol: cada servicio hace `exigirAdmin(UsuarioAutenticado)` **antes de leer** (copiado en `ReclamoAdminService` l.179-184, `EstadoEspacioService` l.166-170, `CuentaServiceImpl` l.307). Comentario en `ReclamoAdminService` l.179: "Copiado como en los otros servicios del admin (DT-19): **extraerlo es de HU-27**" → HU-27 puede/debe extraer un helper común (p. ej. en `comun/seguridad` o `administracion`).
  - `ConfiguracionSeguridad` (`BE/comun/seguridad/ConfiguracionSeguridad.java` l.43-73): todo lo no listado es `authenticated()`; un `POST /api/reportes` no requiere cambios.
- **Patrón HU-26 (reclamos) — el más cercano:**
  - Usuario crea: `ReclamoController` `POST /api/reclamos` (201) → `ReclamoService.abrir` (l.96-111): rol → `SanitizadorTexto.limpiar` → `TransactionTemplate` → `saveAndFlush` → auditoría `RECLAMO_ABIERTO` (detalle sin textos) → correo con `AvisosReclamo` tras commit. Traduce violación de unicidad por nombre de restricción (l.236-240).
  - Admin: `ReclamoAdminController` `/api/admin/reclamos` con `GET ?grupo=abiertos|resueltos&pagina=n` (no-store), `GET /{id}`, `POST /{id}/resolucion`. `ReclamoAdminService.listar` (l.85-108): valida grupo y página con `ValidacionException` multi-campo, `PageRequest` de 10, guarda de offset > `Integer.MAX_VALUE`, orden "más antiguos primero" para la cola. `resolver` (l.117-158): bloqueo de fila `findParaActualizar` → "ya resuelto" → cambio → auditoría `RECLAMO_RESUELTO` → correos tras commit.
  - `ConversionReclamos.idReclamo` (l.16-29): id de ruta como texto, 1-19 dígitos, mal escrito = 404 genérico. Es package-private → copiar o generalizar.
  - DTOs con `@Pattern` para motivo de catálogo y `LectoresJson.SoloTexto/SoloEnteroLargo` (`CrearReclamoDTO`); motivo admin recortado con `strip()` + `@NotBlank @Size(max=500)` (`MotivoSuspensionDTO`).
  - `PaginaReclamosDTO`, `VistaReclamos.numeroDePagina` (paginación textual) reutilizables como plantilla.
- **Patrón HU-09 (suspensión con motivo):** `CuentaAdminController` `POST /api/admin/cuentas/{id}/suspension` con `MotivoSuspensionDTO`; correo con motivo vía `NotificadorCorreo` (en `cuentas`). Tabla historial `suspension_cuenta` (accion, motivo, admin_id, fecha) en `V3_1`.
- **Auditoría:** `BE/comun/auditoria/AuditoriaService.registrar(new EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle))` (tabla `auditoria`, `V1_2`). Convención: constantes `ACCION_*`, nunca textos libres en el detalle (HU-26: "nunca los textos").
- **Correos (Factory Method HU-25):** `BE/comun/correo/FabricaCorreo<E>` (Creator; `enviar(destinatario, evento)`), `Correo` (record con `asunto()`/`cuerpo()`), `FormatoCorreo`. Ejemplo completo en `BE/administracion/service/correo/` (`FabricaResolucionArrendador` + `CorreoResolucionArrendador` + `DatosResolucion`) y Client `BE/administracion/service/AvisosReclamo.java` (cada correo en `DespuesDelCommit.ejecutar`, `try/catch` + `log.warn` sin datos). Receta: docs/architecture.md l.711-714.
- **Correo del arrendador:** `CuentaService.correoDe(usuarioId)` (`BE/cuentas/service/CuentaService.java` l.114-120); arrendador del espacio con `CatalogoEspaciosService.arrendadoresDe(ids)` o `Espacio.getArrendadorId()`.
- **Datos personales (HU-22):** puerto `cuentas.service.puertos.ParticipanteDatosPersonales`; implementaciones `@Order(1..4)` (reclamos = `DatosPersonalesReclamosImpl` `@Order(4)`). docs/architecture.md l.616: "HU-26 y HU-27 igual con reclamos y reportes si guardan datos personales" → `reporte.reportante_id` (y quizá un texto libre) ⇒ evaluar `DatosPersonalesReportesImpl` `@Order(5)`.
- **Tests de referencia:** `2_Backend/src/test/java/pe/edu/ulima/espacioya/administracion/` (`ReclamoAdminControllerIT`, `ReclamosSoporte`, `AvisosReclamoTest`, `CorreosReclamoTest`, `ReclamoAdminServiceTest`). Admin en IT necesita `MfaDePrueba.activar` (HU-17). Las IT que creen reseñas las borran antes que `reserva`/`espacio`/`usuario` (sin cascada); igual con `reporte`.

## 3. Componentes transversales de `comun`

| Necesidad | Componente | Notas |
|---|---|---|
| Rate limiting por IP/ruta | `BE/comun/limite/{ReglaLimite,FiltroLimiteIntentos}.java` | Beans `ReglaLimite(metodo, ruta, maximo, ventana[, grupo])`; clave = grupo+IP; responde 429 `ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS`. **Es por IP**, no por usuario. |
| Límite por usuario | `BE/comun/limite/LimitadorIntentos.java` (`intentar(clave, maximo, ventana)`, `superaLimite`, `registrar`, `limpiar`) + `DemasiadosIntentosException` → 429 (`ManejadorGlobalErrores` l.44-48) | Ejemplo por cuenta: `CambioCorreoService` l.42-53, 101-102 (`"cambio-correo\|" + id`, 3/h). **En memoria** (se pierde al reiniciar; no compartido entre instancias). Alternativa durable: contar filas de `reporte` del reportante en las últimas 24 h (como el cupo de HU-12, que "no usa `LimitadorIntentos`", requerimientos.md l.277). Decidir en el design ("al día" = ventana móvil 24 h vs día calendario de Lima con `ZonaHoraria`). |
| Auditoría | `BE/comun/auditoria/*` | ver § 2 |
| Correo | `BE/comun/correo/*`, `BE/comun/transaccion/DespuesDelCommit.java` | ver § 2 |
| Errores | `BE/comun/error/`: `ReglaNegocioException(campo?, msg)` → 400; `ValidacionException(List<ErrorCampoDTO>)` → 400; `AccesoDenegadoException` → 403; `RecursoNoEncontradoException` → 404; `DemasiadosIntentosException` → 429; `ServicioExternoException` → 503 | `ManejadorGlobalErrores.java` |
| Sanitizar textos | `BE/comun/texto/SanitizadorTexto.limpiar(String)` | quita etiquetas, `<`/`>`, controles; puede devolver vacío |
| Usuario autenticado | `BE/comun/seguridad/UsuarioAutenticado(usuarioId, sesionId, rol)` vía `@AuthenticationPrincipal` | identidad siempre de la sesión |
| IP | `BE/comun/web/IpCliente.de(peticion)` | para auditoría |
| Paginación | no hay util común: cada módulo usa `PageRequest` + `Slice`, `BusquedaService.CAMPO_PAGINA/MENSAJE_PAGINA_INVALIDA`, `VistaReclamos.numeroDePagina` | guarda de offset > `Integer.MAX_VALUE` (arch. l.642-643) |
| Reloj / zona | `Clock` inyectado, `BE/comun/configuracion/ZonaHoraria` (`LIMA`, `hoy`, `ahora`) | |

## 4. Siguiente migración Flyway

Existentes: `V1_1..V1_5`, `V2_1..V2_4`, `V3_1..V3_3`, `V4_1..V4_3`, `V5_1..V5_3`, `V6_1`, `V7_1..V7_3`, `V8_1__reclamos.sql`.
Convención (`docs/conventions.md` l.81-103): `V<sprint>_<n>__<desc>.sql` con el sprint de la historia (HU-27 = sprint 8 en `feature_list.json` l.552) → **`V8_2__reportes.sql`** (o el siguiente `n` libre de sprint 8 en `develop` al unir). No editar `V2_2`/`V7_2`: los `CHECK` de estado ya admiten `OCULTO`/`OCULTA`; cualquier columna nueva sobre `espacio`/`resena` va en `V8_2`.

## 5. Frontend

- **Kit UI** `3_frontend/src/components/ui/`: `Alerta({tipo, children})`, `Boton({variante, cargando, deshabilitado, onClick})`, `Campo({etiqueta, nombre, tipo: text|select|textarea|date…, valor, onChange, error, ayuda, opciones, filas})`, `Modal({abierto, titulo, onConfirmar, onCerrar, textoConfirmar, textoCerrar, variante, cargando, children})`, `Tarjeta({titulo, children, acciones})`, `EtiquetaEstado({estado})`, `Galeria`, `Imagen`, `GrupoCasillas`, `SelectorBloques`, `CuentaRegresiva`.
  - `EtiquetaEstado.jsx` ya tiene `OCULTO` ('Oculto', error) (l.13); **falta** `VISIBLE`/`OCULTA` de reseña y los estados de reporte (ojo: `ABIERTO` ya existe para reclamo, l.36 — no reutilizar el mismo valor con otro texto). Tabla de colores en `docs/frontend.md` l.139-150 ya prevé Espacio `OCULTO` y Reseña `VISIBLE`/`OCULTA`.
- **API** `3_frontend/src/api/`: `cliente.js` (`peticion(ruta, {metodo, cuerpo, autenticada})`, `NO_AUTENTICADO`, `urlApi`), `administracion.js` (identidades, cuentas, reclamos de partes y admin, l.43-80 — lugar natural para `reportarContenido`, `listarReportesAdmin`, `obtenerReporteAdmin`, `resolverReporte`), `busqueda.js` (`obtenerEspacio`, `listarResenasDeEspacio`, `crearResena`…), `espacios.js` (incluye pausa/baja del arrendador).
- **Rutas admin** (`3_frontend/src/App.jsx` l.258-351, todas con `RutaProtegida`): `/admin/identidades[/:id]`, `/admin/cuentas[/:id]`, `/admin/reembolsos`, `/admin/liquidaciones[/:id]`, `/admin/reclamos[/:id]`. Enlaces del admin en `3_frontend/src/pages/cuentas/Panel.jsx` l.82-99 (`ENLACE_RECLAMOS_ADMIN` en `textosAdministracion.js` l.103). Páginas admin de referencia: `pages/administracion/ReclamosAdmin.jsx` (reutiliza `pages/pagos/SeccionLiquidaciones.jsx` para secciones paginadas) y `ResolverReclamo.jsx` (formulario + `Modal` de confirmación).
- **Dónde poner "Reportar":**
  - Espacio: `3_frontend/src/pages/busqueda/FichaEspacio.jsx` (ficha pública `/espacios/:id`); `espacio.id` disponible (l.156); usa `useSesion()` (l.46) para decidir mostrarlo (rol). Buen sitio: bajo los datos o junto a la política (l.166-179).
  - Reseña: `3_frontend/src/pages/busqueda/ResenasEspacio.jsx`, cada reseña es una `Tarjeta` (l.71-77) con `resena.id`; `Tarjeta` admite `acciones` → botón "Reportar" por reseña. Hoy no usa sesión.
  - Plantilla de modal con motivo: `3_frontend/src/pages/reservas/ReportarProblema.jsx` (HU-26: `Boton secundario` + `Modal` con `Campo select` motivo y `textarea`; errores de campo en el modal, el resto bajo el botón). **Cuidado con textos:** `BOTON_REPORTAR = 'Reportar un problema'` y `TITULO_REPORTAR` ya existen para reclamos (`textosAdministracion.js` l.30-31) → usar nombres distintos.
- **Arrendador y OCULTO:** `EditarEspacio.jsx` l.35-36 y `DisponibilidadEspacio.jsx` l.23 ya tratan `OCULTO` como no editable ("HU-27" en el comentario); `EstadoPublicacion.jsx` solo muestra acciones para `PUBLICADO`/`PAUSADO` (l.86-91), así que un `OCULTO` queda sin acciones. Mostrar el motivo al arrendador no existe (hoy solo llegaría por correo, CA3).

## 6. Qué dicen los docs

- `docs/requerimientos.md`:
  - RF-F-10 (l.136) reportar espacio o reseña con motivo → cola; RF-F-11 (l.137) admin ve contenido y motivo, mantiene u oculta con motivo; RF-F-12 (l.138) al ocultar, aviso al arrendador con motivo y retiro del catálogo.
  - RNF-SEG-04 (l.149) MFA obligatorio admin (HU-27 E/S). RNF-SEG-12 (l.157) auditoría incluye "moderación". **RNF-SEG-17 (l.162): "10 reportes por usuario al día"**; l.277 lista HU-27 entre las de rate limiting y aclara que un cupo por usuario de negocio no tiene por qué usar `LimitadorIntentos` (que es por IP en ventana).
  - RNF-USA-04 (l.195) correo en eventos clave, incluye "moderación". RN-15 (l.224) búsqueda solo muestra publicados, "no pausados ni ocultos" (HU-27 CA3).
- `docs/modelo-datos.md`:
  - l.38 tabla prevista **`reporte`**: `id, reportante_id, tipo_objetivo (ESPACIO, RESENA), objetivo_id, motivo, estado, admin_id, decision` (HU-27). Sin fechas, motivo de decisión ni catálogo de motivos definidos: el design debe precisarlos (p. ej. `creado_en`, `resuelto_en`, `motivo_decision VARCHAR(500)`, `CHECK` de tipo/estado/motivo, índices para la cola y para contar por reportante/día; `objetivo_id` polimórfico ⇒ sin FK o dos columnas con FK).
  - l.22 `espacio.estado` incluye `OCULTO` (HU-27); l.36 `resena.estado` VISIBLE/OCULTA ("HU-27 oculta"); l.59 `uq_resena_reserva` cuenta también las `OCULTA`.
  - l.117-119: `reporte` se agrega con migración nueva de Release 2.
  - Si el design cambia nombres/atributos, actualizar `docs/modelo-datos.md` en el mismo PR (l.3-7).
- `docs/architecture.md`: módulo `administracion` incluye HU-27 (l.55); `administracion → (todos)` (l.128); HU-27 puede llamar a las rutas admin de HU-21 y `OCULTO` no admite pausa/reanudación/baja (l.363); HU-27 agrega `Resena.ocultar` + interfaz pública de `busqueda` (l.621, 648-649); correo nuevo según receta (l.711); rutas bajo `/api/admin/` (l.758).

## Puntos abiertos a decidir en el spec (observados, no resueltos)

1. Estados de origen de `ocultar` en espacio (¿PUBLICADO y PAUSADO?, ¿BORRADOR/BAJA no?) y si `OCULTO` es final o reversible ("mantener" vs "restaurar").
2. ¿Ocultar un espacio con reservas futuras/pagadas? HU-21 bloquea la baja con reservas por terminar (`ReservasDelEspacio`); CA3 no lo menciona.
3. Varios reportes sobre el mismo objetivo: ¿se agrupan, se resuelven todos a la vez al ocultar?, ¿un usuario puede reportar dos veces lo mismo (unicidad `reportante_id, tipo, objetivo_id`)?
4. Quién puede reportar: "usuario autenticado" (¿también ARRENDADOR?, ¿su propio espacio/ reseña propia?, ADMIN no). Reportar contenido no visible → 404.
5. Implementación del límite 10/día: `LimitadorIntentos` (memoria) vs conteo en BD (durable) y definición de "día".
6. Correo: CA3 solo exige al arrendador al ocultar un espacio; ¿aviso al autor de una reseña oculta o al reportante? (requerimientos: "moderación" como evento de correo).
7. Interfaces públicas nuevas: en `espacios` (ocultar como admin + datos del espacio para la cola, cualquier estado) y en `busqueda` (leer reseña por id + ocultar). Extraer `exigirAdmin` común (DT-19 de HU-26).
8. Datos personales HU-22: participante para `reporte` (exportar/anonimizar, ¿impedimento?).
