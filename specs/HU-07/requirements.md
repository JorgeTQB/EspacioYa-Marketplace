# HU-07 — Requirements: Publicar y editar un espacio

> Fuente: `docs/historias/HU-07.md` (CA1–CA4, STRIDE E, T/I, R), `docs/requerimientos.md`
> (RF-B-01…04, RNF-SEG-08, 12, 19, RNF-USA-02, RN-01, RN-09, RN-18, §8 "Almacenamiento privado
> cifrado con URL firmada", del que HU-07 reutiliza solo la validación, la re-codificación y el
> adaptador de disco: las fotos son públicas, sin cifrar y con URL estable), `docs/architecture.md` (módulo
> `espacios`, estados del espacio, dirección `espacios → cuentas`, fuera del alcance: más de 5
> fotos, video, precios por franja, mapa), `docs/modelo-datos.md` (`espacio`, `foto_espacio`),
> `specs/HU-05/` (`AlmacenamientoArchivos`, `ValidadorArchivo`, `AccesoDenegadoException`,
> `ValidacionException`) y `progress/explore_HU-07.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la
> presentación está en `design.md` § 13 Pantallas. Las decisiones técnicas están en
> `design.md` § 1.

## Decisiones por defecto que el dueño debe confirmar al aprobar

> ⚠ Las fuentes no fijan estos puntos. El spec aplica el valor por defecto indicado; si
> **Sebastian Garate** no lo acepta, cambian los requirements citados antes de pasar a
> `in_progress`. Detalle y alternativas en `design.md` § 1. P4 y P5 ya fueron **decididas por
> el dueño** (revisión del 2026-10-28) y no llevan ⚠.
>
> | # | Pregunta | Por defecto en este spec | Afecta |
> |---|----------|--------------------------|--------|
> | P1 | ¿Zona: texto libre o lista cerrada? | **Lista cerrada** en una tabla `zona` (código + nombre) sembrada por la migración con los **43 distritos de Lima Metropolitana y los 7 del Callao**. La API la expone en `GET /api/zonas`; el espacio guarda el código. HU-10 busca por ese código (sin comparar textos libres). | R5, R8 |
> | P2 | ¿Fotos en la misma solicitud que la ficha o por separado? | **Por separado, una foto por solicitud** (`POST …/espacios/{id}/fotos`). La ficha viaja en JSON; las fotos se agregan cuando el borrador ya existe. Así cada solicitud cabe en el límite multipart actual (6 MB por archivo) sin tocarlo. | R1, R25–R36 |
> | P3 | ¿Hace falta al menos una foto para publicar? | **Sí, al menos una.** La búsqueda (HU-10 CA2) muestra la "foto principal" y la ficha (HU-11 CA1) muestra fotos. Un borrador puede tener 0 a 5. | R17 |
> | P4 | ¿Dónde se guardan las fotos y cómo se sirven? | **Decidido por el dueño:** las fotos de un espacio no son sensibles. Se validan y re-codifican con `ValidadorArchivo` de HU-05 (sin EXIF/GPS, una por solicitud) y se guardan **sin cifrar** con el adaptador de disco `AlmacenamientoArchivos` (clave `espacios/<UUID>` no adivinable). Se sirven en **`GET /api/fotos/{uuid}`**, URL estable: si el espacio está `PUBLICADO`, a cualquiera (incluso sin sesión) y cacheable públicamente; si no, solo a su dueño autenticado (`no-store`) y 404 genérico a cualquier otro. Sin URL firmada. | R11, R33, R37, R39, R54, R55 |
> | P5 | ¿Campos obligatorios y rangos? | **Decidido por el dueño:** todos obligatorios: `nombre` (3–100), `tipo` (`CANCHA`, `ESTADIO`, `AUDITORIO`), `direccion` (5–200), `zona` (código del catálogo), `capacidad` (entero 1–100 000), `precioHora` (S/ 1.00–100 000.00, hasta 2 decimales, en soles) y `descripcion` (10–2000). Los textos se recortan antes de validar y las longitudes se cuentan tras recortar. `nombre` y `descripcion` no están en la HU pero sí en el modelo y en HU-10/HU-11. | R1, R4 |
> | P6 | ¿Qué estados se editan? ¿Un publicado vuelve a borrador al editarlo? | Se editan **`BORRADOR`, `PUBLICADO` y `PAUSADO`**; `BAJA` y `OCULTO` no. Editar un `PUBLICADO` **lo deja `PUBLICADO`** (los cambios se ven de inmediato, CA4). No hay "volver a borrador". | R20, R21, R23 |
> | P7 | ¿Qué responde CA3 (publicar sin identidad verificada)? | **400** con el mensaje *identidad no verificada* ("Para publicar tus espacios, primero debes verificar tu identidad.", el mismo texto del aviso de HU-02). La pantalla lo muestra con un enlace a `/panel/identidad`. | R15 |
> | P8 | ¿Qué responde ante un espacio ajeno o inexistente? | **404** con el mensaje genérico *recurso no encontrado*, sin revelar si existe (`docs/conventions.md`: "no existe o no es tuyo → 404"). | R40 |
> | P9 | ¿Rutas? | Bajo **`/api/arrendadores/actual/espacios`** (patrón `/actual` de HU-05: la identidad sale de la sesión). Deja libre `GET /api/espacios/{id}` para la ficha pública de HU-11. | Todas |
> | P10 | ¿Cómo sabe `espacios` si el correo y la identidad están verificados? | Por la **interfaz pública `CuentaService`** de `cuentas` (prevista en `docs/architecture.md`), nueva en esta historia. `espacios` no usa repositorios de `cuentas`. | R13, R15, R42, R43 |
> | P11 | ¿Rate limiting? | **No**, tampoco en `GET /api/fotos/{uuid}`. RNF-SEG-17 no fija límite para HU-07 y la historia no tiene control (D). El tope de 5 fotos por espacio acota el almacenamiento por espacio; las fotos publicadas son cacheables, lo que reduce las descargas repetidas. | — |
> | P12 | ¿Qué se audita como "qué cambió"? | Creación: los valores de la ficha. Edición: cada campo cambiado como `campo=anterior->nuevo`; `descripcion` solo con su nombre (texto largo). Si no cambia nada, no se audita. Fotos: agregada, eliminada y rechazada. Publicación. | R45–R51 |
> | P13 | ¿Cómo se cumple "las reservas ya pagadas conservan su precio" (CA4, RN-09)? | La tabla `reserva` aún no existe. Lo garantiza **HU-12** copiando el precio a `reserva.precio_hora_aplicado` al reservar (ya está en `docs/modelo-datos.md`). HU-07 no escribe fuera de `espacio`, `foto_espacio` y `auditoria`. | — (HU-12) |
> | P14 | ¿Orden de las fotos? | La foto principal es la de `orden = 1`. Una foto nueva va al final; al quitar una, las siguientes suben un lugar. **No hay reordenamiento** en HU-07. | R25, R34 |
> | P15 | ¿Se pueden borrar espacios? ¿Bloqueo optimista en la edición? | **No** a ambas: la baja es HU-21 y ningún CA pide resolver dos ediciones simultáneas (gana la última). | — |
> | P16 | ¿Número de migración? | **`V2_2__espacio.sql`** (sprint 2). Si HU-06 llega antes a `develop` con `V2_2`, se renombra al siguiente libre antes del merge (`docs/conventions.md`). | R2, R8, R38 |

## Definiciones

- **Arrendador habilitado:** cuenta con rol `ARRENDADOR` y `correo_verificado = true` (RN-01).
- **Identidad verificada:** `perfil_arrendador.estado_identidad = VERIFICADA` (RN-01).
- **Espacio propio:** fila de `espacio` cuyo `arrendador_id` es el id del usuario de la sesión.
- **Espacio editable:** espacio propio en estado `BORRADOR`, `PUBLICADO` o `PAUSADO` (⚠ P6).
- **Ficha:** cuerpo JSON con `nombre`, `tipo`, `direccion`, `zona`, `capacidad`, `precioHora` y
  `descripcion`.
- **Ficha válida** (P5): tras recortar espacios al inicio y al final de los textos,
  - `nombre` de 3 a 100 caracteres;
  - `tipo` igual a `CANCHA`, `ESTADIO` o `AUDITORIO`;
  - `direccion` de 5 a 200 caracteres;
  - `zona` igual al `codigo` de una fila de la tabla `zona` (⚠ P1);
  - `capacidad` entero de 1 a 100 000;
  - `precioHora` decimal de 1.00 a 100000.00 con como mucho 2 decimales (soles, RN-18: un solo
    precio por hora);
  - `descripcion` de 10 a 2000 caracteres (ausente, vacía o solo con espacios es inválida).
- **Solicitud de foto:** `POST /api/arrendadores/actual/espacios/{id}/fotos` con cuerpo
  `multipart/form-data` y la parte `foto` (⚠ P2).
- **Foto permitida:** parte no vacía, de hasta 5 242 880 bytes, cuya firma de tipo real (definida
  en `specs/HU-05/requirements.md`) es PNG o JPEG y que se decodifica como imagen de hasta
  40 000 000 píxeles (`ValidadorArchivo` de HU-05). Un PDF **no** es una foto permitida.
- **Máximo de fotos:** 5 por espacio (RNF-USA-02).
- **EspacioDTO:** cuerpo de respuesta con solo `id`, `nombre`, `tipo`, `direccion`, `zona`,
  `zonaNombre`, `capacidad`, `precioHora`, `descripcion`, `estado`, `creadoEn`, `actualizadoEn` y
  `fotos`; cada foto con solo `id`, `orden` y `url` (URL estable de la foto, R11).
- **UUID de la foto:** la parte `<UUID>` de su `ruta_archivo` (`espacios/<UUID>`).
- **Solicitud de imagen:** `GET /api/fotos/{uuid}`, con o sin sesión.
- **Dueño de la foto:** usuario de la sesión cuyo id es el `arrendador_id` del espacio de la foto.
- **Mensajes** (texto exacto en `design.md` § 4): *identidad no verificada*, *solo borrador*,
  *sin fotos*, *estado no editable*, *máximo de fotos*, *falta foto*, *tipo de foto*, *tamaño
  excedido*, *imagen ilegible*, mensajes por campo de la ficha, *acceso denegado*, *recurso no
  encontrado*.

## Creación del borrador (CA1, RF-B-01)

## R1
CUANDO un arrendador habilitado, con sesión vigente, envía
`POST /api/arrendadores/actual/espacios` con una ficha válida, el sistema DEBE responder
`201 Created` con un EspacioDTO cuyo `estado` es `BORRADOR` y cuya lista `fotos` está vacía.

## R2
CUANDO el sistema responde según R1, el sistema DEBE guardar una fila de `espacio` con
`arrendador_id` igual al id del usuario de la sesión, los valores recortados de la ficha,
`estado = BORRADOR` y `creado_en` y `actualizado_en` iguales a la fecha y hora del reloj del
sistema.

## R3
CUANDO el sistema procesa `POST /api/arrendadores/actual/espacios` o
`PUT /api/arrendadores/actual/espacios/{id}`, el sistema DEBE ignorar cualquier `id`,
`arrendadorId`, `usuarioId` o `estado` enviado en el cuerpo.

## R4
SI la ficha de `POST /api/arrendadores/actual/espacios` o de
`PUT /api/arrendadores/actual/espacios/{id}` incumple la definición de ficha válida en `nombre`,
`tipo`, `direccion`, `capacidad`, `precioHora` o `descripcion`, o trae `zona` ausente, vacía o
de más de 40 caracteres, ENTONCES el sistema DEBE
responder 400 con un error en `errores` por cada uno de esos campos inválidos, con el nombre del
campo y su mensaje.

## R5
SI la ficha de `POST /api/arrendadores/actual/espacios` o de
`PUT /api/arrendadores/actual/espacios/{id}` cumple todo salvo que `zona` no es el código de una
fila de `zona` ENTONCES el sistema DEBE responder 400 con un error en el campo `zona`.

## R6
SI el sistema rechaza una ficha según R4 o R5 ENTONCES el sistema DEBE dejar sin cambios las
filas de `espacio`.

## R7
El sistema NO DEBE incluir en ninguna respuesta de `/api/arrendadores/actual/espacios` el
`arrendador_id`, el correo, el teléfono ni los documentos del arrendador, ni la `ruta_archivo`
(`espacios/<UUID>`) de las fotos.

## Catálogo de zonas (⚠ P1)

## R8
CUANDO un cliente con sesión vigente envía `GET /api/zonas`, el sistema DEBE responder 200 con
la lista de todas las filas de `zona`, cada una con solo `codigo` y `nombre`, ordenadas por
`nombre`.

## Consulta de los espacios propios

## R9
CUANDO un arrendador con sesión vigente envía `GET /api/arrendadores/actual/espacios`, el
sistema DEBE responder 200 con solo los espacios propios, ordenados del más reciente al más
antiguo por `creado_en`, cada uno con solo `id`, `nombre`, `tipo`, `zona`, `zonaNombre`,
`precioHora` y `estado`.

## R10
CUANDO un arrendador con sesión vigente envía `GET /api/arrendadores/actual/espacios/{id}` de
un espacio propio, el sistema DEBE responder 200 con su EspacioDTO, con las fotos ordenadas por
`orden` ascendente.

## R11
CUANDO el sistema arma un EspacioDTO, el sistema DEBE incluir por cada foto la `url`
`/api/fotos/<UUID>` con el UUID de la foto, igual en todas las consultas mientras la foto exista
y sin parámetros de firma ni de expiración (P4).

## Publicación (CA2, CA3, RF-B-02, RF-B-03, RN-01)

## R12
CUANDO un arrendador habilitado con identidad verificada envía
`POST /api/arrendadores/actual/espacios/{id}/publicacion` de un espacio propio en `BORRADOR` con
al menos una foto, el sistema DEBE responder 200 con su EspacioDTO y `estado = PUBLICADO`.

## R13
CUANDO el sistema responde según R12, el sistema DEBE guardar `estado = PUBLICADO` y
`actualizado_en` igual a la fecha y hora del reloj del sistema en la fila del espacio.

## R14
SI se envía `POST /api/arrendadores/actual/espacios/{id}/publicacion` de un espacio propio cuyo
estado no es `BORRADOR` ENTONCES el sistema DEBE responder 400 con el mensaje *solo borrador*.

## R15
SI un arrendador habilitado sin identidad verificada envía
`POST /api/arrendadores/actual/espacios/{id}/publicacion` de un espacio propio en `BORRADOR`
ENTONCES el sistema DEBE responder 400 con el mensaje *identidad no verificada* (⚠ P7).

## R16
CUANDO el sistema rechaza una publicación por más de un motivo, el sistema DEBE responder con
el mensaje del primero en este orden: *solo borrador* (R14), *identidad no verificada* (R15),
*sin fotos* (R17).

## R17
SI un arrendador habilitado con identidad verificada envía
`POST /api/arrendadores/actual/espacios/{id}/publicacion` de un espacio propio en `BORRADOR` sin
fotos ENTONCES el sistema DEBE responder 400 con el mensaje *sin fotos* (⚠ P3).

## R18
SI el sistema rechaza una publicación según R14, R15 o R17 ENTONCES el sistema DEBE dejar sin
cambios el `estado` y `actualizado_en` del espacio.

## R19
MIENTRAS un espacio está en `BORRADOR`, el sistema NO DEBE cambiar su estado a `PUBLICADO` por
ninguna operación distinta de R12.

## Edición (CA4, RF-B-04)

## R20
CUANDO un arrendador habilitado envía `PUT /api/arrendadores/actual/espacios/{id}` con una
ficha válida para un espacio editable, el sistema DEBE responder 200 con su EspacioDTO con los
valores de la ficha.

## R21
CUANDO el sistema responde según R20 y al menos un campo cambió, el sistema DEBE guardar en la
misma fila del espacio los valores recortados de la ficha y `actualizado_en` igual a la fecha y
hora del reloj del sistema, sin cambiar `estado`, `arrendador_id` ni `creado_en`.

## R22
CUANDO un arrendador edita según R20 un espacio en `PUBLICADO`, el sistema DEBE devolver los
nuevos valores en las siguientes consultas de R9 y R10, con `estado = PUBLICADO`.

## R23
SI se envía `PUT /api/arrendadores/actual/espacios/{id}`, una solicitud de foto o
`DELETE /api/arrendadores/actual/espacios/{id}/fotos/{fotoId}` para un espacio propio en `BAJA`
u `OCULTO` ENTONCES el sistema DEBE responder 400 con el mensaje *estado no editable* sin cambiar
el espacio ni sus fotos (⚠ P6).

## R24
El sistema DEBE guardar `precio_hora` como un único valor `NUMERIC(8,2)` por espacio, igual al
`precioHora` recibido (RN-18).

## Fotos (CA1 "hasta 5 fotos", STRIDE T/I, RNF-SEG-08, RNF-USA-02)

## R25
CUANDO un arrendador habilitado envía una solicitud de foto para un espacio editable con menos
de 5 fotos y una foto permitida, el sistema DEBE responder `201 Created` con su EspacioDTO, en
el que la nueva foto tiene `orden` igual al número de fotos que tenía más 1.

## R26
CUANDO el sistema responde según R25, el sistema DEBE guardar una fila de `foto_espacio` con el
id del espacio, `orden` de R25, `tipo_mime` igual al tipo MIME real (`image/png` o
`image/jpeg`) y `ruta_archivo` con la forma `espacios/<UUID>`.

## R27
SI se envía una solicitud de foto para un espacio que ya tiene 5 fotos ENTONCES el sistema DEBE
responder 400 con el mensaje *máximo de fotos*.

## R28
SI la parte `foto` falta o mide 0 bytes ENTONCES el sistema DEBE responder 400 con un error en
el campo `foto` y el mensaje *falta foto*.

## R29
SI la parte `foto` mide más de 5 242 880 bytes ENTONCES el sistema DEBE responder 400 con un
error en el campo `foto` y el mensaje *tamaño excedido*.

## R30
SI el contenido de la parte `foto` no empieza con la firma de tipo real de PNG o JPEG, incluido
un PDF, ENTONCES el sistema DEBE responder 400 con un error en el campo `foto` y el mensaje
*tipo de foto*, aunque el nombre o el `Content-Type` declarados indiquen JPG o PNG.

## R31
SI la parte `foto` tiene firma PNG o JPEG pero no se decodifica como imagen o supera 40 000 000
píxeles ENTONCES el sistema DEBE responder 400 con un error en el campo `foto` y el mensaje
*imagen ilegible*.

## R32
SI el sistema rechaza una solicitud de foto según R23 o R27–R31 ENTONCES el sistema DEBE dejar
sin cambios las filas de `foto_espacio` y los archivos almacenados.

## R33
CUANDO el sistema acepta una foto, el sistema DEBE almacenar la imagen vuelta a codificar en su
mismo formato, sin los segmentos EXIF (`APP1`) ni los bloques de texto PNG (`tEXt`, `iTXt`,
`zTXt`) del archivo recibido.

## R34
CUANDO un arrendador habilitado envía
`DELETE /api/arrendadores/actual/espacios/{id}/fotos/{fotoId}` de una foto de un espacio
editable, el sistema DEBE responder 200 con su EspacioDTO sin esa foto y con las fotos restantes
numeradas en `orden` de 1 en adelante sin huecos, conservando su orden relativo.

## R35
CUANDO el sistema responde según R34, el sistema DEBE eliminar la fila de `foto_espacio` y el
archivo almacenado de esa foto.

## R36
SI `fotoId` de `DELETE /api/arrendadores/actual/espacios/{id}/fotos/{fotoId}` no es una foto del
espacio `{id}` ENTONCES el sistema DEBE responder 404 con el mensaje *recurso no encontrado* sin
eliminar ninguna foto.

## R37
CUANDO el sistema guarda una foto aceptada, el sistema DEBE escribir sin cifrar, en el
directorio de almacenamiento y bajo la clave `espacios/<UUID>`, exactamente los bytes de la
imagen re-codificada de R33, sin guardar su contenido en la base de datos (P4).

## R38
El sistema DEBE impedir en la base de datos que un espacio tenga una foto con `orden` fuera de
1 a 5 o dos fotos con el mismo `orden`.

## Servir las fotos (P4)

## R39
CUANDO un cliente, con o sin sesión, envía una solicitud de imagen cuyo `{uuid}` es el UUID de
una foto de un espacio en `PUBLICADO`, el sistema DEBE responder 200 con los bytes almacenados de
la foto, `Content-Type` igual a su `tipo_mime`, `X-Content-Type-Options: nosniff` y
`Cache-Control: public, max-age=3600`.

## R54
CUANDO el dueño de la foto, con sesión vigente, envía una solicitud de imagen cuyo `{uuid}` es
el UUID de una foto de un espacio en un estado distinto de `PUBLICADO`, el sistema DEBE
responder 200 con los bytes almacenados de la foto, `Content-Type` igual a su `tipo_mime`,
`X-Content-Type-Options: nosniff` y `Cache-Control: no-store`.

## R55
SI una solicitud de imagen trae un `{uuid}` que no tiene la forma de un UUID en minúsculas, que
no es el UUID de ninguna foto o cuyo archivo no está almacenado, o que es el de una foto de un
espacio en un estado distinto de `PUBLICADO` y la envía un cliente sin sesión vigente o distinto
del dueño de la foto, ENTONCES el sistema DEBE responder 404 con el mensaje *recurso no
encontrado*, el mismo cuerpo en todos esos casos y un `Cache-Control` que incluye `no-store`.

## Propiedad y acceso (STRIDE E, RNF-SEG-18, 19)

## R40
SI `{id}` no corresponde a un espacio propio en `GET`, `PUT`, `POST …/publicacion`,
`POST …/fotos` o `DELETE …/fotos/{fotoId}` de `/api/arrendadores/actual/espacios/{id}`
ENTONCES el sistema DEBE responder 404 con el mensaje *recurso no encontrado* sin cambiar ningún
espacio ni foto (⚠ P8).

## R41
SI una cuenta con rol distinto de `ARRENDADOR` envía cualquier solicitud a
`/api/arrendadores/actual/espacios` o a sus subrutas ENTONCES el sistema DEBE responder 403 con
el mensaje *acceso denegado*.

## R42
SI un arrendador con `correo_verificado = false` envía `POST` o `PUT` a
`/api/arrendadores/actual/espacios` o a sus subrutas, o `DELETE …/fotos/{fotoId}`, ENTONCES el
sistema DEBE responder 403 con el mensaje *acceso denegado*.

## R43
SI un cliente sin sesión vigente envía cualquier solicitud a
`/api/arrendadores/actual/espacios`, a sus subrutas o a `GET /api/zonas` ENTONCES el sistema
DEBE responder 401 con el mensaje de sesión inválida de HU-03. (La solicitud de imagen no exige
sesión: R39, R55.)

## R44
CUANDO el sistema rechaza una solicitud de foto según R23, R27, R40, R41 o R42, el sistema DEBE
responder sin validar ni almacenar el contenido de la parte `foto`.

## Registro de creación y edición (STRIDE R, RNF-SEG-12)

## R45
CUANDO el sistema responde según R1, el sistema DEBE registrar en la auditoría la acción
`CREACION_ESPACIO` con el id del usuario, la entidad `espacio` con el id de la fila, la IP de la
solicitud, la fecha y hora y en el detalle `tipo=`, `zona=`, `capacidad=` y `precioHora=` con
sus valores guardados.

## R46
CUANDO el sistema responde según R20 y al menos un campo cambió, el sistema DEBE registrar en la
auditoría la acción `EDICION_ESPACIO` con el id del usuario, la entidad `espacio` con su id, la
IP, la fecha y hora y en el detalle un elemento `campo=anterior->nuevo` por cada campo cambiado
distinto de `descripcion`, y el elemento `descripcion` si cambió la descripción (⚠ P12).

## R47
CUANDO el sistema responde según R20 sin que cambie ningún campo, el sistema NO DEBE registrar
una auditoría `EDICION_ESPACIO`.

## R48
CUANDO el sistema responde según R12, el sistema DEBE registrar en la auditoría la acción
`PUBLICACION_ESPACIO` con el id del usuario, la entidad `espacio` con su id, la IP, la fecha y
hora y el detalle `estado=BORRADOR->PUBLICADO`.

## R49
CUANDO el sistema responde según R25 o R34, el sistema DEBE registrar en la auditoría la acción
`FOTO_ESPACIO_AGREGADA` o `FOTO_ESPACIO_ELIMINADA` con el id del usuario, la entidad `espacio`
con su id, la IP, la fecha y hora y en el detalle `foto=<id de la foto>` y `orden=<orden>`; en
`FOTO_ESPACIO_AGREGADA` además `mime=` y `tamano=` (bytes recibidos).

## R50
CUANDO el sistema rechaza una solicitud de foto según R27–R31, el sistema DEBE registrar en la
auditoría la acción `FOTO_ESPACIO_RECHAZADA` con el id del usuario, la entidad `espacio` con su
id, la IP, la fecha y hora y en el detalle `foto=<MOTIVO>` o `fotos=MAXIMO`.

## R51
El sistema NO DEBE escribir en el detalle de las auditorías de espacios el contenido de las
fotos, su nombre original ni su clave de almacenamiento.

## Orden de las comprobaciones

## R52
CUANDO un cliente con sesión vigente envía `POST /api/arrendadores/actual/espacios` o
`PUT /api/arrendadores/actual/espacios/{id}` con una ficha que incumple R4, el sistema DEBE
responder según R4 sin comprobar rol, correo verificado, propiedad ni estado.

## R53
CUANDO un cliente con sesión vigente envía una solicitud a `/api/arrendadores/actual/espacios` o
a sus subrutas que no cae en R52, el sistema DEBE aplicar las comprobaciones en este orden y
responder con la primera que falle: rol (R41), correo verificado (R42), propiedad (R40), estado
(R14, R23) y luego las demás reglas de la operación.

## Trazabilidad con la historia

| Origen (`docs/historias/HU-07.md` y fuentes)                                          | Cubierto por |
|---------------------------------------------------------------------------------------|--------------|
| CA1: correo verificado + campos obligatorios → se guarda como borrador                | R1, R2, R3, R4, R5, R6, R42 (pantalla en design § 13) |
| CA1: … tipo, dirección, zona, capacidad, precio por hora                              | R1, R4, R5, R8, R24 |
| CA1: … hasta 5 fotos                                                                  | R25, R26, R27, R34, R35, R38 |
| CA2: identidad verificada → publico el borrador y queda visible en el catálogo        | R12, R13, R16, R17, R19, R39 (el catálogo de HU-10/11 lista `PUBLICADO` y muestra sus fotos sin sesión) |
| CA3: identidad no verificada → se impide y se indica completar la verificación        | R15, R16, R18 (enlace a `/panel/identidad` en design § 13) |
| CA4: edito un publicado → los cambios se reflejan en el catálogo                      | R20, R21, R22, R23 |
| CA4: … y las reservas ya pagadas conservan su precio                                  | — garantizado por HU-12 con `reserva.precio_hora_aplicado` (⚠ P13); HU-07 no toca otras tablas |
| (E) Validación de propiedad del recurso en backend en cada edición                    | R3, R36, R40, R41, R42, R43, R44, R52, R53 |
| (T/I) Imágenes validadas en tipo y tamaño                                             | R28, R29, R30, R31, R32 |
| (T/I) … y reprocesadas para eliminar metadatos                                        | R33, R37 |
| (I) fotos de borradores solo para su dueño; publicadas, públicas (P4)                 | R11, R39, R54, R55 |
| (R) Registro de creación y edición (quién, cuándo, qué cambió)                        | R45, R46, R47, R48, R49, R50, R51 |
| RNF-SEG-08 (tipo real, tamaño, imágenes sin metadatos)                                | R28–R33 |
| RNF-SEG-12                                                                            | R45–R51 |
| RNF-SEG-19 · RNF-SEG-18                                                               | R36, R40, R55 · R41 |
| RNF-USA-02 (hasta 5 fotos; 5 MB)                                                      | R27, R29, R38 |
| RN-01 (correo verificado crea borradores; identidad verificada publica)               | R1, R12, R15, R42 |
| RN-09 (editar el precio no afecta reservas pagadas)                                   | — HU-12 (⚠ P13) |
| RN-18 (precio único por hora)                                                         | R24 |
| RF-B-01 / 02 / 03 / 04                                                                | R1–R8, R25–R39, R54, R55 / R12, R13, R16, R17, R19, R39 / R15, R18 / R20–R23 |
| Minimización (RNF-SEG-16, anticipa HU-10/11)                                          | R7 |
