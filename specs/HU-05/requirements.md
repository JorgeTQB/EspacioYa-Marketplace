# HU-05 — Requirements: Envío de documentos de identidad del arrendador

> Fuente: `docs/historias/HU-05.md` (CA1–CA3, STRIDE I/E/T/R), `docs/requerimientos.md`
> (RF-A-14…16, RNF-SEG-08, 12, 14, 15, 19, RNF-USA-02, 03, RN-01, §8 "Almacenamiento privado
> cifrado con URL firmada", cuya primera historia es esta), `docs/architecture.md` (Adapter
> para el almacenamiento, estados de la cuenta, "no guardar archivos en la BD ni servirlos con
> URL pública"), `docs/modelo-datos.md` (`documento_identidad`), `specs/HU-02/` (cifrado AES-GCM),
> `specs/HU-03/` (sesión, `AvisoCuenta`, DT-9) y `progress/explore_HU-05.md`.
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la
> presentación está en `design.md` § 13 Pantallas. Las decisiones técnicas están en
> `design.md` § 1.

## Decisiones por defecto que el dueño debe confirmar al aprobar

> ⚠ Las fuentes no fijan estos puntos. El spec aplica el valor por defecto indicado; si
> **Kevin Chan** no lo acepta, cambian los requirements citados antes de pasar a
> `in_progress`. Detalle y alternativas en `design.md` § 1 y en `progress/HU-05.md`.
>
> | # | Pregunta | Por defecto en este spec | Afecta |
> |---|----------|--------------------------|--------|
> | P1 | ¿Dónde se guardan los archivos? | En **disco local** (directorio configurable, fuera del repo y de la BD) detrás de una interfaz propia `AlmacenamientoArchivos` (Adapter). Un adaptador S3 queda para una tarea `sdd:false` posterior (exige agregar el SDK de AWS, que se discute antes). En UAT, el directorio debe estar en un volumen persistente. | R30, R31 |
> | P2 | ¿Cifrado de la app o del proveedor? ¿Qué llave? | **De la app:** AES-256-GCM con la llave existente `CIFRADO_LLAVE` (HU-02), IV aleatorio por archivo. | R30 |
> | P3 | ¿Duración de la URL firmada, quién la genera y con qué llave? | **5 minutos.** La genera el backend con HMAC-SHA256 y una **llave nueva** `ALMACENAMIENTO_LLAVE_FIRMA`; apunta a un endpoint propio `GET /api/archivos` que valida firma y vencimiento, descifra y sirve el archivo. | R34–R39 |
> | P4 | ¿Qué documentos exactamente? | **Dos, ambos obligatorios, un archivo cada uno y en una sola solicitud:** `IDENTIDAD` (DNI o RUC) y `PROPIEDAD` (propiedad o autorización del espacio). Anverso y reverso van en un mismo PDF o imagen. | R1, R3, R8 |
> | P5 | ¿5 MB por archivo o por envío? ¿MB o MiB? | **Por archivo**, límite inclusivo de **5 242 880 bytes** (5 × 1024 × 1024). | R6, R9, R13 |
> | P6 | ¿Se reprocesan las imágenes para quitar metadatos (RNF-SEG-08)? ¿Y los PDF? | **Sí para JPG/PNG:** se decodifican y se vuelven a codificar con `javax.imageio` (JDK, sin dependencias), lo que descarta EXIF y bloques de texto; máximo 40 000 000 píxeles. **PDF:** se guarda sin cambios (no hay librería de PDF en el stack). | R12, R32, R33 |
> | P7 | ¿Qué pasa con los documentos anteriores al reenviar tras un rechazo? ¿Y con `motivo_rechazo`? | **Se reemplazan:** se borran las filas y los archivos anteriores (minimización); `motivo_rechazo` queda nulo al pasar a `EN_REVISION`. | R4, R5 |
> | P8 | ¿Se puede subir en `EN_REVISION` o `VERIFICADA`? | **No.** Solo desde `NO_INICIADA` o `RECHAZADA` (RN-01); en otro estado, 400 con mensaje. El cambio de RUC con identidad verificada es de HU-20. | R16, R24 |
> | P9 | ¿El arrendador ve lo que subió? | **Sí, solo los documentos vigentes**, con URL firmada, en `GET /api/arrendadores/actual/identidad`. | R21, R25 |
> | P10 | ¿Cómo accede el admin? | **Lo construye HU-06** (el admin hoy no inicia sesión, DT-7 de HU-03). HU-05 deja listo `ArchivosPrivados.urlFirmada` en `comun` y la tabla `documento_identidad` para que HU-06 los use. | (E) admin: — |
> | P11 | ¿Rate limiting en las subidas? | **No.** No figura en el STRIDE ni en RNF-SEG-17; tras un envío aceptado el estado pasa a `EN_REVISION` y se rechazan los siguientes (R16). | — |
> | P12 | ¿Correo al pasar a "identidad en revisión"? | **No.** Ningún CA lo pide; los correos de identidad los envía HU-06 al decidir. | — |
> | P13 | ¿Cómo ve el panel el motivo de rechazo (CA3)? | El `aviso` de la sesión (HU-03) gana textos para `EN_REVISION` y `RECHAZADA`; el de `RECHAZADA` **incluye el motivo**. El panel lo muestra y enlaza a la pantalla de identidad. | R28, R29 |
> | P14 | ¿Qué responde a un rol no arrendador o a un correo no verificado? | **403** con un mensaje genérico (`AccesoDenegadoException`, nueva en `comun/error`). | R17, R18 |
> | P15 | ¿Plazo de conservación de los documentos? | Fuera de HU-05 (HU-22, pendiente §9.6). No se borra nada por plazo. | — |
> | P16 | ¿Número de migración? | **`V2_1__documento_identidad.sql`** (sprint 2, `docs/conventions.md`), no `V1_5` como sugería el explorador. HU-06 usa la siguiente `V2_n`. | R3 |

## Definiciones

- **Arrendador habilitado para enviar:** cuenta con rol `ARRENDADOR`, `correo_verificado =
  true` y perfil con `estado_identidad` `NO_INICIADA` o `RECHAZADA` (RN-01, ⚠ P8).
- **Solicitud de envío:** `POST /api/arrendadores/actual/documentos` con cuerpo
  `multipart/form-data` y las partes `documentoIdentidad` y `documentoPropiedad` (⚠ P4).
- **Límite de tamaño:** 5 242 880 bytes por archivo, inclusivo (⚠ P5).
- **Firma de tipo real:** los primeros bytes del contenido: PDF `25 50 44 46 2D` (`%PDF-`),
  PNG `89 50 4E 47 0D 0A 1A 0A`, JPEG `FF D8 FF`.
- **Archivo permitido:** parte no vacía, de tamaño menor o igual al límite, con firma de tipo
  real PDF, PNG o JPEG y, si es PNG o JPEG, que se decodifica como imagen de hasta 40 000 000
  píxeles.
- **Tipo MIME real:** `application/pdf`, `image/png` o `image/jpeg` según la firma de tipo real.
- **Mensajes** (texto exacto en `design.md` § 4): *falta identidad*, *falta propiedad*,
  *tamaño excedido*, *tipo no permitido*, *imagen ilegible*, *estado no permitido*, *acceso
  denegado*, *recurso no encontrado*, *aviso en revisión*, *aviso rechazada*.
- **URL firmada:** ruta `GET /api/archivos?clave=…&tipo=…&expira=…&firma=…` donde `firma` es el
  HMAC-SHA256 de `clave`, `tipo` y `expira` con la llave de firma (⚠ P3).
- **Duración de la URL firmada:** 5 minutos (⚠ P3).
- **Documentos vigentes:** las filas de `documento_identidad` del arrendador (como mucho una
  por tipo, R5).

## Envío aceptado (CA1, RF-A-14)

## R1
CUANDO un arrendador habilitado para enviar, con sesión vigente, envía una solicitud de envío
cuyas dos partes son archivos permitidos, el sistema DEBE responder `201 Created` con el
mismo cuerpo que R21 y `estadoIdentidad = "EN_REVISION"`.

## R2
CUANDO el sistema responde según R1, el sistema DEBE guardar `estado_identidad = EN_REVISION`
en el perfil del arrendador.

## R3
CUANDO el sistema responde según R1, el sistema DEBE guardar una fila de
`documento_identidad` por parte, con el id del arrendador de la sesión, `tipo` `IDENTIDAD` o
`PROPIEDAD` según la parte, `tipo_mime` igual al tipo MIME real, `tamano` igual a los bytes
recibidos y `subido_en` igual a la fecha y hora del reloj del sistema.

## R4
CUANDO el sistema responde según R1 a un arrendador con `estado_identidad = RECHAZADA`, el
sistema DEBE dejar `motivo_rechazo` nulo (⚠ P7).

## R5
CUANDO el sistema responde según R1 a un arrendador que ya tenía documentos, el sistema DEBE
eliminar las filas y los archivos almacenados de los documentos anteriores, de modo que solo
queden los dos del último envío (⚠ P7).

## R6
CUANDO una parte mide exactamente el límite de tamaño y cumple el resto de la definición de
archivo permitido, el sistema DEBE aceptarla.

## R7
CUANDO el sistema procesa una solicitud de envío, el sistema DEBE asociar los documentos al
usuario de la sesión, ignorando cualquier `usuarioId` o `arrendadorId` enviado como parámetro
o parte de la solicitud.

## Archivo de formato o tamaño no permitido (CA2, RF-A-15, STRIDE T, RNF-SEG-08, RNF-USA-03)

## R8
SI la parte `documentoIdentidad` o `documentoPropiedad` falta o mide 0 bytes ENTONCES el
sistema DEBE responder 400 con un error en el campo de esa parte y el mensaje *falta
identidad* o *falta propiedad* respectivamente.

## R9
SI una parte mide más que el límite de tamaño ENTONCES el sistema DEBE responder 400 con un
error en el campo de esa parte y el mensaje *tamaño excedido*.

## R10
SI el contenido de una parte no empieza con la firma de tipo real de PDF, PNG o JPEG
ENTONCES el sistema DEBE responder 400 con un error en el campo de esa parte y el mensaje
*tipo no permitido*, aunque el nombre del archivo o el `Content-Type` declarado indiquen PDF,
JPG o PNG.

## R11
CUANDO el sistema guarda un documento, el sistema DEBE tomar `tipo_mime` de la firma de tipo
real, sin usar el `Content-Type` declarado ni la extensión del nombre del archivo.

## R12
SI una parte con firma de tipo real PNG o JPEG no se puede decodificar como imagen o supera
40 000 000 píxeles ENTONCES el sistema DEBE responder 400 con un error en el campo de esa
parte y el mensaje *imagen ilegible* (⚠ P6).

## R13
SI una parte supera el límite de multipart configurado y la solicitud se rechaza antes de
llegar al controlador ENTONCES el sistema DEBE responder 400 con un `ErrorDTO` cuyo mensaje es
*tamaño excedido*.

## R14
SI el sistema rechaza una solicitud de envío según R8–R13 o R16 ENTONCES el sistema DEBE dejar
sin cambios `estado_identidad`, las filas de `documento_identidad` y los archivos almacenados.

## R15
SI las dos partes de una solicitud de envío incumplen la definición de archivo permitido
ENTONCES el sistema DEBE responder 400 con un error por cada parte en `errores`.

## Estado y acceso (STRIDE E, RNF-SEG-18, 19, RN-01)

## R16
SI un arrendador con `estado_identidad` `EN_REVISION` o `VERIFICADA` envía una solicitud de
envío ENTONCES el sistema DEBE responder 400 con el mensaje *estado no permitido* (⚠ P8).

## R17
SI una cuenta con rol distinto de `ARRENDADOR` envía una solicitud de envío o
`GET /api/arrendadores/actual/identidad` ENTONCES el sistema DEBE responder 403 con el mensaje
*acceso denegado* (⚠ P14).

## R18
SI un arrendador con `correo_verificado = false` envía una solicitud de envío ENTONCES el
sistema DEBE responder 403 con el mensaje *acceso denegado* (⚠ P14).

## R19
SI un cliente sin sesión vigente envía una solicitud de envío o
`GET /api/arrendadores/actual/identidad` ENTONCES el sistema DEBE responder 401 con el mensaje
de sesión inválida de HU-03.

## R20
CUANDO el sistema rechaza una solicitud de envío según R16, R17 o R18, el sistema DEBE
responder sin validar ni almacenar el contenido de las partes.

## Estado de identidad y reenvío (CA3, RF-A-16)

## R21
CUANDO un arrendador con sesión vigente envía `GET /api/arrendadores/actual/identidad`, el
sistema DEBE responder 200 con un cuerpo que contenga solo `estadoIdentidad`, `motivoRechazo`,
`puedeEnviar` y `documentos`.

## R22
MIENTRAS el perfil del arrendador tiene `estado_identidad = RECHAZADA`, el sistema DEBE
devolver en `motivoRechazo` de R21 el `motivo_rechazo` guardado.

## R23
MIENTRAS el perfil del arrendador tiene `estado_identidad` distinto de `RECHAZADA`, el sistema
DEBE devolver `motivoRechazo` nulo en R21.

## R24
El sistema DEBE devolver en `puedeEnviar` de R21 `true` si `estado_identidad` es
`NO_INICIADA` o `RECHAZADA` y el correo está verificado, y `false` en otro caso.

## R25
El sistema DEBE devolver en `documentos` de R21 un elemento por documento vigente con solo
`id`, `tipo`, `tipoMime`, `tamano`, `subidoEn`, `url` (URL firmada) y `urlExpiraEn`.

## R26
El sistema NO DEBE incluir en las respuestas de R1 y R21 el contenido de los archivos, el
nombre original de los archivos ni el número de documento del perfil.

## R27
El sistema DEBE nombrar cada archivo almacenado con una clave aleatoria de la forma
`identidad/<UUID>`, sin guardar en ninguna parte el nombre original del archivo enviado.

## R28
CUANDO el sistema calcula el `aviso` de `POST /api/sesiones` o `GET /api/sesiones/actual`
para un `ARRENDADOR` con `estado_identidad = EN_REVISION`, el sistema DEBE devolver el
mensaje *aviso en revisión* (⚠ P13).

## R29
CUANDO el sistema calcula el `aviso` de `POST /api/sesiones` o `GET /api/sesiones/actual`
para un `ARRENDADOR` con `estado_identidad = RECHAZADA`, el sistema DEBE devolver el mensaje
*aviso rechazada* seguido del `motivo_rechazo` guardado (⚠ P13).

## Almacenamiento privado y cifrado (STRIDE I, RNF-SEG-14, 15)

## R30
CUANDO el sistema guarda un archivo aceptado, el sistema DEBE escribirlo en el almacenamiento
privado cifrado con AES-256-GCM, de modo que los bytes almacenados difieran del contenido
aceptado y solo se recuperen al descifrarlos con la llave de cifrado (⚠ P1, P2).

## R31
El sistema NO DEBE guardar el contenido de los archivos en la base de datos.

## R32
CUANDO el sistema acepta una parte PNG o JPEG, el sistema DEBE almacenar la imagen vuelta a
codificar en su mismo formato, sin los segmentos EXIF (`APP1`) ni los bloques de texto PNG
(`tEXt`, `iTXt`, `zTXt`) del archivo recibido (⚠ P6).

## R33
CUANDO el sistema acepta una parte PDF, el sistema DEBE almacenar un contenido que, al
descifrarlo, es idéntico byte a byte al recibido.

## URL firmada de corta duración (STRIDE I, RNF-SEG-15)

## R34
CUANDO un cliente, con o sin sesión, envía `GET /api/archivos` con una URL firmada válida cuyo
`expira` es posterior a la fecha y hora del reloj del sistema, el sistema DEBE responder 200
con el contenido descifrado del archivo y `Content-Type` igual al tipo firmado.

## R35
CUANDO el sistema responde según R34, el sistema DEBE incluir los encabezados
`X-Content-Type-Options: nosniff`, `Cache-Control: no-store` y
`Content-Disposition: inline; filename="documento.<extensión>"`.

## R36
SI la `firma` de `GET /api/archivos` no coincide con el HMAC de `clave`, `tipo` y `expira`
ENTONCES el sistema DEBE responder 404 con el mensaje *recurso no encontrado*.

## R37
SI el `expira` de `GET /api/archivos` es igual o anterior a la fecha y hora del reloj del
sistema ENTONCES el sistema DEBE responder 404 con el mensaje *recurso no encontrado*.

## R38
CUANDO el sistema genera una URL firmada para R25, el sistema DEBE fijar `expira` y
`urlExpiraEn` en la fecha y hora del reloj del sistema más la duración de la URL firmada.

## R39
SI la llave de firma `ALMACENAMIENTO_LLAVE_FIRMA` falta, no mide 32 bytes en base64 o es igual
a `CIFRADO_LLAVE` o `CIFRADO_LLAVE_HUELLA` ENTONCES el sistema DEBE impedir el arranque con un
error que nombre la variable sin mostrar su valor.

## Registro de cada carga (STRIDE R, RNF-SEG-12)

## R40
CUANDO el sistema responde según R1, el sistema DEBE registrar en la auditoría, por cada
documento guardado, la acción `CARGA_DOCUMENTO_IDENTIDAD` con el id del usuario, la entidad
`documento_identidad` con el id de la fila, la IP de la solicitud, la fecha y hora y en el
detalle `tipo=`, `mime=` y `tamano=`.

## R41
CUANDO el sistema rechaza una solicitud de envío según R8–R12, R15 o R16, el sistema DEBE
registrar en la auditoría la acción `CARGA_DOCUMENTO_RECHAZADA` con el id del usuario, la
entidad `perfil_arrendador` con el id del usuario, la IP, la fecha y hora y en el detalle el
motivo de cada parte rechazada (`<parte>=<MOTIVO>`) o `estado=<ESTADO_IDENTIDAD>`.

## R42
El sistema NO DEBE escribir en el detalle de las auditorías de carga el contenido de los
archivos, su nombre original, la clave de almacenamiento ni el número de documento.

## Trazabilidad con la historia

| Origen (`docs/historias/HU-05.md` y fuentes)                                         | Cubierto por |
|--------------------------------------------------------------------------------------|--------------|
| CA1: correo verificado + documentos PDF/JPG/PNG ≤ 5 MB → "identidad en revisión"     | R1, R2, R3, R6, R7, R11 (pantalla en design § 13) |
| CA2: formato o tamaño no permitido → se rechaza indicando el motivo                  | R8, R9, R10, R12, R13, R14, R15 |
| CA3: identidad rechazada → veo el motivo en mi panel                                 | R22, R29 (panel en design § 13) |
| CA3: … y puedo volver a subir documentos                                             | R1, R4, R5, R24 |
| (I) Documentos cifrados en almacenamiento privado                                    | R27, R30, R31 |
| (I) Servidos con URL firmada de corta duración                                       | R25, R34, R35, R36, R37, R38, R39 |
| (E) Solo el propio arrendador accede a sus documentos                                | R7, R17, R19, R21, R25, R36 |
| (E) … y el admin                                                                     | — acceso admin en HU-06 (⚠ P10); HU-05 deja `ArchivosPrivados.urlFirmada` |
| (T) Validación del tipo real del archivo y su tamaño                                 | R9, R10, R11, R12, R13 |
| (R) Registro de cada carga                                                           | R40, R41, R42 |
| RNF-SEG-08 (tipo real, tamaño, imágenes sin metadatos)                               | R9–R13, R32, R33 |
| RNF-SEG-14 · 15                                                                      | R30, R31 · R34–R39 |
| RNF-SEG-12                                                                           | R40, R41, R42 |
| RNF-SEG-18 · 19                                                                      | R17, R18 · R7, R21, R25, R36 |
| RNF-USA-02 · 03                                                                      | R6, R9, R10 · R8–R13, R16 |
| RN-01 (correo verificado sube; rechazado vuelve a subir; en revisión espera)         | R1, R16, R18, R24 |
| RF-A-14 / 15 / 16                                                                    | R1–R7 / R8–R15 / R21–R29 |
