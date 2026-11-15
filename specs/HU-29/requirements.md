# HU-29 · Requirements — Almacenamiento de archivos en S3 seleccionable por configuración

> Fuente: [`docs/historias/HU-29.md`](../../docs/historias/HU-29.md) (CA1–CA5 y STRIDE). Depende de HU-05
> (`comun.almacenamiento`). Historia técnica (épica T): no tiene fila con RF en la matriz §7 de
> `docs/requerimientos.md`; roza RNF-SEG-15 (documentos privados) y RNF-SEG-14 (cifrado en reposo), que **no cambian**.
>
> Glosario: *el tipo* = la propiedad `espacioya.almacenamiento.tipo` (variable `ALMACENAMIENTO_TIPO`);
> *el bucket* = `espacioya.almacenamiento.s3.bucket` (`ALMACENAMIENTO_S3_BUCKET`); *la región* =
> `espacioya.almacenamiento.s3.region` (`ALMACENAMIENTO_S3_REGION`); *el adaptador S3* = `AlmacenamientoS3`;
> *el adaptador local* = `AlmacenamientoLocal`; *el cliente S3* = `software.amazon.awssdk.services.s3.S3Client`.
> Los valores del tipo se comparan sin distinguir mayúsculas (igual que `@ConditionalOnProperty`).

## Selección del adaptador

## R1
MIENTRAS el tipo no está definido, el sistema DEBE arrancar con el adaptador local como único
`AlmacenamientoArchivos`, aunque no exista ninguna propiedad `espacioya.almacenamiento.s3.*`.

## R2
MIENTRAS el tipo vale `local`, el sistema DEBE arrancar con el adaptador local como único `AlmacenamientoArchivos`.

## R3
MIENTRAS el tipo no está definido o vale `local`, el sistema NO DEBE crear ningún cliente S3.

## R4
MIENTRAS el tipo vale `s3`, el sistema DEBE usar el adaptador S3 como único `AlmacenamientoArchivos`.

## R5
SI el tipo tiene un valor distinto de `local` y de `s3` (incluido el valor vacío) ENTONCES el sistema DEBE fallar el
arranque con un mensaje que nombra `espacioya.almacenamiento.tipo`, `ALMACENAMIENTO_TIPO`, el valor recibido y los
valores válidos `local` y `s3`.

## Configuración y comprobación del bucket al arrancar

## R6
SI el tipo vale `s3` y el bucket no está definido o está en blanco ENTONCES el sistema DEBE fallar el arranque con un
mensaje que nombra `espacioya.almacenamiento.s3.bucket` y `ALMACENAMIENTO_S3_BUCKET`, sin llamar a S3.

## R7
SI el tipo vale `s3` y la región no está definida o está en blanco ENTONCES el sistema DEBE fallar el arranque con un
mensaje que nombra `espacioya.almacenamiento.s3.region` y `ALMACENAMIENTO_S3_REGION`.

## R8
CUANDO el backend arranca con el tipo `s3`, el sistema DEBE comprobar el bucket con una llamada `HeadBucket` sobre el
nombre configurado antes de dejar listo el adaptador S3.

## R9
SI la comprobación del bucket responde que no existe (404, `NoSuchBucketException`) ENTONCES el sistema DEBE fallar el
arranque con un mensaje que nombra el bucket y dice que no existe.

## R10
SI la comprobación del bucket responde 403 ENTONCES el sistema DEBE fallar el arranque con un mensaje que nombra el
bucket y dice que no es accesible con las credenciales del rol.

## R11
SI la comprobación del bucket responde 301 (el bucket está en otra región) ENTONCES el sistema DEBE fallar el arranque
con un mensaje que nombra el bucket y `ALMACENAMIENTO_S3_REGION`.

## R12
SI la comprobación del bucket falla por cualquier otro motivo (otro código de S3, red, credenciales no disponibles)
ENTONCES el sistema DEBE fallar el arranque con un mensaje que nombra el bucket.

## R13
SI el tipo vale `s3` y la configuración o la comprobación del bucket fallan ENTONCES el sistema NO DEBE completar el
arranque con el adaptador local.

## Operaciones contra el bucket

## R14
CUANDO se llama a `guardar(clave, contenido)` del adaptador S3, el sistema DEBE enviar un `PutObject` al bucket
configurado con la llave del objeto igual a `clave` (sin prefijo) y el cuerpo igual a `contenido`, byte a byte.

## R15
CUANDO el adaptador S3 envía un `PutObject`, el sistema DEBE pedir cifrado en reposo SSE-S3
(`ServerSideEncryption.AES256`).

## R16
El adaptador S3 NO DEBE asignar ACL al objeto en el `PutObject`.

## R17
CUANDO se llama a `leer(clave)` del adaptador S3 y el objeto existe, el sistema DEBE devolver un `Optional` con los
bytes exactos del objeto cuya llave es `clave` en el bucket configurado.

## R18
SI al `leer(clave)` S3 responde que la llave no existe (`NoSuchKeyException`) ENTONCES el adaptador S3 DEBE devolver
`Optional.empty()`.

## R19
CUANDO se llama a `eliminar(clave)` del adaptador S3, el sistema DEBE enviar un `DeleteObject` al bucket configurado
con la llave igual a `clave`.

## R20
SI `guardar` falla en S3 (`S3Exception` o `SdkClientException`) ENTONCES el adaptador S3 DEBE lanzar
`UncheckedIOException` con el mismo mensaje que el adaptador local (`No se pudo guardar el archivo`).

## R21
SI `leer` falla en S3 por un motivo distinto de una llave inexistente ENTONCES el adaptador S3 DEBE lanzar
`UncheckedIOException` con el mismo mensaje que el adaptador local (`No se pudo leer el archivo`).

## R22
SI `eliminar` falla en S3 ENTONCES el adaptador S3 DEBE lanzar `UncheckedIOException` con el mismo mensaje que el
adaptador local (`No se pudo eliminar el archivo`).

## R23
SI una operación del adaptador S3 falla ENTONCES la excepción lanzada NO DEBE contener, ni en su mensaje ni en el de su
causa, la clave, el nombre del bucket ni el mensaje del proveedor.

## R24
SI el adaptador S3 recibe una clave nula, en blanco, que empieza con `/` o que tiene un segmento `..` ENTONCES DEBE
lanzar `IllegalArgumentException` sin llamar al cliente S3.

## R25
El adaptador S3 y el adaptador local DEBEN cumplir el mismo contrato de `AlmacenamientoArchivos`: guardar y luego leer
devuelve los mismos bytes, guardar de nuevo la misma clave reemplaza el contenido, leer una clave inexistente devuelve
vacío y eliminar dos veces la misma clave no falla y deja la clave inexistente.

## R26
CUANDO `ArchivosPrivados.guardar` delega en el adaptador S3, el cuerpo del `PutObject` DEBE ser el contenido cifrado
por `CifradorArchivos` y no el contenido en claro.

## Cliente S3 y credenciales

## R27
El cliente S3 DEBE obtener las credenciales de la cadena por defecto del SDK (`DefaultCredentialsProvider`).

## R28
`application.properties` y `.env.example` NO DEBEN declarar claves de acceso de AWS (`AWS_ACCESS_KEY_ID`,
`AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` ni propiedades equivalentes).

## R29
El cliente S3 DEBE usar la región configurada en `espacioya.almacenamiento.s3.region`.

## R30
El cliente S3 DEBE limitar cada intento de llamada a 10 segundos y cada operación completa (con reintentos) a
30 segundos.

## Trazabilidad con la historia

| Origen (docs/historias/HU-29.md)                                                        | Cubierto por |
|-----------------------------------------------------------------------------------------|--------------|
| CA1: sin tipo o `local` → disco, sin configuración ni dependencia de AWS en ejecución   | R1, R2, R3 |
| CA2: `s3` con bucket y región → guardar, leer y eliminar contra el bucket con la misma clave; el resto no nota la diferencia | R4, R8, R14, R17, R19, R25, R29 |
| CA3: `s3` y bucket inexistente, inaccesible o sin nombre → arranque falla con mensaje claro; sin pasar a disco | R6, R7, R8, R9, R10, R11, R12, R13 |
| CA4: otro valor → falla indicando `local` y `s3`                                        | R5 |
| CA5: clave inexistente en el bucket → `leer` devuelve vacío                             | R18, R25 |
| (I) bucket privado y cifrado en reposo (SSE-S3); documentos cifrados por la app antes de subir | R15, R16, R26 (Block Public Access y el cifrado por defecto del bucket son infraestructura: ver `design.md` § 11) |
| (S/E) sin claves de acceso en código, `.env` ni repositorio; cadena por defecto del SDK | R27, R28 |
| (T) claves validadas (sin `..` ni rutas absolutas) antes de llegar a cualquier adaptador | R24 (y HU-05 R27, sin cambios, en `ArchivosPrivadosTest`/`ArchivosPublicosTest`) |
| (D) falla de S3 → misma excepción que el adaptador local, sin detalles del proveedor     | R20, R21, R22, R23, R30 |
| Fuera del alcance: URL prefirmadas, migrar archivos del disco, crear el bucket          | No se implementa (ver `design.md` § 12) |
