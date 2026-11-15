# HU-29 · Tasks — Almacenamiento de archivos en S3 seleccionable por configuración

> Diseño: [`design.md`](design.md). Requirements: [`requirements.md`](requirements.md). Todo en `2_Backend/`
> (paquete `pe.edu.ulima.espacioya.comun.almacenamiento`) salvo T13. Sin frontend ni migraciones.

- [x] T1 — `pom.xml`: propiedad `aws-sdk.version` (última 2.x estable), BOM `software.amazon.awssdk:bom` en
  `dependencyManagement` y dependencia `software.amazon.awssdk:s3` sin `netty-nio-client` (design § 8.4). Revisar con
  `./2_Backend/mvnw -f 2_Backend/pom.xml dependency:tree` que queda `apache-client` y ninguna autoconfiguración de AWS.
  Cubre: R3, R27 (habilita). Test: los de T5–T9.
- [x] T2 — `application.properties`: `espacioya.almacenamiento.tipo=${ALMACENAMIENTO_TIPO:local}`,
  `espacioya.almacenamiento.s3.bucket=${ALMACENAMIENTO_S3_BUCKET:}` y `espacioya.almacenamiento.s3.region=${ALMACENAMIENTO_S3_REGION:}`
  con el comentario de design § 8.1; `src/test/resources/config/application.properties`: `espacioya.almacenamiento.tipo=local`;
  `.env.example`: bloque de design § 8.3, sin nombrar variables de claves de AWS. Cubre: R1, R2, R28. Test:
  `SeleccionAlmacenamientoTest`, `ConfiguracionAlmacenamientoS3Test`.
- [x] T3 — Crear `SeleccionAlmacenamiento` (`BeanFactoryPostProcessor`, `EnvironmentAware`, constantes `PROPIEDAD`,
  `LOCAL`, `S3`, `validar(String)` con el mensaje de design § 7.1). Cubre: R5. Test: `SeleccionAlmacenamientoTest`.
- [x] T4 — Crear `FallaAlmacenamiento` (design § 7.2) y modificar `AlmacenamientoLocal`: `@ConditionalOnProperty(name =
  PROPIEDAD, havingValue = LOCAL, matchIfMissing = true)` y sus tres `UncheckedIOException` desde `FallaAlmacenamiento`,
  sin cambiar el comportamiento. Actualizar el javadoc de `AlmacenamientoArchivos` con el contrato (design § 7.6).
  Cubre: R1, R2, R3, R25. Test: `SeleccionAlmacenamientoTest`, `AlmacenamientoLocalContratoTest` y
  `AlmacenamientoLocalTest` (existente, sigue verde).
- [x] T5 — Crear `ConfiguracionAlmacenamientoS3` con `clienteS3(region)` y `crearCliente(region)`: región obligatoria,
  `DefaultCredentialsProvider`, timeouts de 10 s por intento y 30 s por operación (design § 7.4). Cubre: R7, R27, R29,
  R30. Test: `ConfiguracionAlmacenamientoS3Test`.
- [x] T6 — Crear `AlmacenamientoS3`: constructor con bucket obligatorio y `comprobarBucket()` con `HeadBucket` y los
  cuatro mensajes de arranque (design § 7.5). Cubre: R4, R6, R8, R9, R10, R11, R12, R13. Test: `AlmacenamientoS3Test`,
  `SeleccionAlmacenamientoTest`.
- [x] T7 — En `AlmacenamientoS3`, `guardar`, `leer` y `eliminar` con `validarClave`, `PutObject` con `AES256` y sin ACL,
  `getObjectAsBytes` con `NoSuchKeyException`/404 → vacío, `DeleteObject`, y `describir(SdkException)` para la causa
  sin clave, bucket ni texto del proveedor (design § 7.5). Cubre: R14, R15, R16, R17, R18, R19, R20, R21, R22, R23, R24.
  Test: `AlmacenamientoS3Test`.
- [x] T8 — Crear `AlmacenamientoS3Test` (Mockito sobre `S3Client`) con al menos un test por requirement: R6 (bucket
  vacío, sin interacciones con el cliente), R8 (`headBucket` con el bucket), R9 (404), R10 (403), R11 (301), R12
  (`SdkClientException` y otro código), R14 (llave = clave, cuerpo exacto), R15 (`AES256`), R16 (`acl()` nulo), R17,
  R18, R19, R20, R21 (403 al leer no es vacío), R22, R23 (mensaje y causa sin clave, bucket ni mensaje del proveedor),
  R24 (`null`, `""`, `/x`, `../x`, `a/../b`, sin llamar al cliente) y R26 (`ArchivosPrivados` real sobre el adaptador:
  el cuerpo capturado no es el claro y `CifradorArchivos.descifrar` lo devuelve). Cubre: R6, R8–R24, R26.
  Test: `AlmacenamientoS3Test`.
- [x] T9 — Crear `ConfiguracionAlmacenamientoS3Test`: región vacía y `null` fallan nombrando la propiedad y la variable
  (R7); el cliente creado usa `DefaultCredentialsProvider` (R27; ver la nota de design § 13), la región `us-east-1`
  (R29) y los dos timeouts (R30); `application.properties` y `.env.example` no contienen `AWS_ACCESS_KEY_ID`,
  `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`, `aws.accessKeyId` ni `aws.secretAccessKey` (R28). Cubre: R7, R27, R28,
  R29, R30. Test: `ConfiguracionAlmacenamientoS3Test`.
- [x] T10 — Crear `SeleccionAlmacenamientoTest` (`ApplicationContextRunner`): sin tipo y sin propiedades `s3.*` →
  `AlmacenamientoLocal` único (R1); `local` y `LOCAL` → local (R2); sin `S3Client` en el contexto con local (R3); `s3` y
  `S3` con un `S3Client` simulado → `AlmacenamientoS3` único y sin `AlmacenamientoLocal` (R4); `disco`, `""` y `" s3"`
  → el contexto falla y la causa raíz nombra la propiedad, la variable, el valor y `local`/`s3` (R5); `s3` sin bucket o
  con `headBucket` que lanza `NoSuchBucketException` → el contexto falla (R13). Cubre: R1, R2, R3, R4, R5, R13.
  Test: `SeleccionAlmacenamientoTest`.
- [x] T11 — Crear `S3EnMemoria` (doble de `S3Client`), `ContratoAlmacenamientoArchivos` (abstracta: guardar/leer
  iguales, sobrescribir reemplaza, leer inexistente vacío, eliminar dos veces sin error y luego vacío, clave `../fuera`
  → `IllegalArgumentException`) y sus dos subclases `AlmacenamientoLocalContratoTest` y `AlmacenamientoS3ContratoTest`
  (design § 13). Cubre: R25, R18. Test: `AlmacenamientoLocalContratoTest`, `AlmacenamientoS3ContratoTest`.
- [x] T12 — Revisar que ninguna clase fuera de `AlmacenamientoS3` y `ConfiguracionAlmacenamientoS3` importe
  `software.amazon.awssdk` (los tests sí pueden) y que `ArchivosPrivados`, `ArchivosPublicos` y `ArchivoController`
  no cambiaron. Cubre: R4, R25. Test: `AlmacenamientoS3ContratoTest`, `ArchivosPrivadosTest`, `ArchivosPublicosTest`
  y `ArchivoControllerTest` (existentes, siguen verdes).
- [x] T13 — `docs/architecture.md`: fila HU-29 en "Patrones por historia", fila "Archivos" del Stack y sección
  "Almacenamiento: qué deja HU-29 (★ Adapter)" (design § 15). Documentación; sin test.
- [x] T14 — Documentar en `progress/impl_HU-29.md` el mapa `R<n>` → test (todos los R1–R30) y, si aplica, la nota de
  R27. Cubre: trazabilidad de R1–R30.
- [x] T15 — Verificación: `./init.sh` en verde (unitarias de las clases tocadas, todas las `*IT` y el build), y
  `./2_Backend/mvnw -f 2_Backend/pom.xml test` sin `ALMACENAMIENTO_TIPO` definida. La verificación contra el bucket real
  (design § 11) queda para UAT, con la caja negra.
