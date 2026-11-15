# HU-29 · Informe de implementación

- **Historia:** HU-29 · [Técnica] Almacenamiento de archivos en S3 seleccionable por configuración
- **Dueño:** Jorge Quispe · **Rama:** `feature/HU-29-almacenamiento-s3` · **Fecha:** 2026-11-15
- **Agente:** implementer · **Status en `feature_list.json`:** `in_progress` (no se marca `done` hasta el reviewer)

## Tasks

T1–T15 hechas (`[x]` en `specs/HU-29/tasks.md`). T15 se cerró en la ronda de review: `./init.sh` completo verde con
Docker encendido (ver "Resultado de pruebas").

## Archivos

Creados (`2_Backend/src/main/java/pe/edu/ulima/espacioya/comun/almacenamiento/`):
- `SeleccionAlmacenamiento.java` — `BeanFactoryPostProcessor` + `EnvironmentAware`; valida el tipo (R5).
- `FallaAlmacenamiento.java` — mensajes y `UncheckedIOException` comunes.
- `AlmacenamientoS3.java` — ★ Adapter sobre `S3Client`.
- `ConfiguracionAlmacenamientoS3.java` — crea el `S3Client` (solo con `s3`).

Modificados:
- `2_Backend/pom.xml` — `aws-sdk.version` 2.59.1, BOM `software.amazon.awssdk:bom`, dependencia `s3` sin `netty-nio-client`.
- `.../almacenamiento/AlmacenamientoLocal.java` — `@ConditionalOnProperty(local, matchIfMissing = true)` y mensajes desde
  `FallaAlmacenamiento` (comportamiento igual).
- `.../almacenamiento/AlmacenamientoArchivos.java` — solo javadoc (contrato y los dos adaptadores).
- `2_Backend/src/main/resources/application.properties` — `espacioya.almacenamiento.tipo`, `.s3.bucket`, `.s3.region`.
- `2_Backend/src/test/resources/config/application.properties` — `espacioya.almacenamiento.tipo=local`.
- `2_Backend/.env.example` — bloque `ALMACENAMIENTO_TIPO` / `ALMACENAMIENTO_S3_BUCKET` / `ALMACENAMIENTO_S3_REGION`.
- `docs/architecture.md` — fila "Archivos" del Stack, fila HU-29 en "Patrones por historia" y sección
  "Almacenamiento: qué deja HU-29 (★ Adapter)". La tabla de puertos no cambia (no hay puerto nuevo).

Pruebas creadas (`2_Backend/src/test/java/pe/edu/ulima/espacioya/comun/almacenamiento/`):
`SeleccionAlmacenamientoTest`, `AlmacenamientoS3Test`, `ConfiguracionAlmacenamientoS3Test`,
`ContratoAlmacenamientoArchivos` (abstracta), `AlmacenamientoLocalContratoTest`, `AlmacenamientoS3ContratoTest`,
`S3EnMemoria` (doble de `S3Client`).

Sin cambios (verificado con `git status`): `ArchivosPrivados`, `ArchivosPublicos`, `ArchivoController`,
`AlmacenamientoLocalTest`, `ArchivosPrivadosTest`, `ArchivosPublicosTest`, `ArchivoControllerTest`.
Sin migraciones, sin endpoints, sin frontend.

## Trazabilidad R → test

| R | Test |
|---|------|
| R1 | `SeleccionAlmacenamientoTest.sinTipoUsaElDisco` |
| R2 | `SeleccionAlmacenamientoTest.tipoLocalUsaElDisco` (`local`, `LOCAL`) |
| R3 | `SeleccionAlmacenamientoTest.sinClienteS3EnLocal` |
| R4 | `SeleccionAlmacenamientoTest.tipoS3UsaElBucket` (`s3`, `S3`); `AlmacenamientoS3ContratoTest` |
| R5 | `SeleccionAlmacenamientoTest.tipoInvalidoFalla` (`disco`, `""`, `" s3"`) |
| R6 | `AlmacenamientoS3Test.sinBucketNoArranca` |
| R7 | `ConfiguracionAlmacenamientoS3Test.sinRegionFalla` |
| R8 | `AlmacenamientoS3Test.compruebaElBucketAlArrancar` |
| R9 | `AlmacenamientoS3Test.bucketInexistente` (NoSuchBucket y S3Exception 404) |
| R10 | `AlmacenamientoS3Test.bucketInaccesible` |
| R11 | `AlmacenamientoS3Test.bucketEnOtraRegion` |
| R12 | `AlmacenamientoS3Test.otraFallaAlComprobar` (SdkClientException y 500) |
| R13 | `SeleccionAlmacenamientoTest.s3QueFallaNoPasaAlDisco` (sin bucket, bucket inexistente, sin región) |
| R14 | `AlmacenamientoS3Test.guardarEnviaPutObject` |
| R15 | `AlmacenamientoS3Test.guardarPideSseS3` |
| R16 | `AlmacenamientoS3Test.guardarSinAcl` |
| R17 | `AlmacenamientoS3Test.leerDevuelveLosBytes` |
| R18 | `AlmacenamientoS3Test.leerLlaveInexistente`; `ContratoAlmacenamientoArchivos.leerInexistenteEsVacio` (en `AlmacenamientoS3ContratoTest`) |
| R19 | `AlmacenamientoS3Test.eliminarEnviaDeleteObject` |
| R20 | `AlmacenamientoS3Test.guardarFalla` |
| R21 | `AlmacenamientoS3Test.leerFalla` (403, 500 y SdkClientException: nunca vacío) |
| R22 | `AlmacenamientoS3Test.eliminarFalla` |
| R23 | `AlmacenamientoS3Test.fallaSinDetalles` |
| R24 | `AlmacenamientoS3Test.claveInvalidaNoLlegaAS3` (`null`, `""`, `"  "`, `/x`, `../x`, `a/../b`, `a/..`) |
| R25 | `ContratoAlmacenamientoArchivos.{guardarYLeer, sobrescribirReemplaza, leerInexistenteEsVacio, eliminarDosVeces, claveFueraSeRechaza}` en `AlmacenamientoLocalContratoTest` y `AlmacenamientoS3ContratoTest` |
| R26 | `AlmacenamientoS3Test.archivosPrivadosSubeCifrado` |
| R27 | `ConfiguracionAlmacenamientoS3Test.credencialesDeLaCadenaPorDefecto` |
| R28 | `ConfiguracionAlmacenamientoS3Test.sinClavesDeAccesoDeAws` |
| R29 | `ConfiguracionAlmacenamientoS3Test.usaLaRegionConfigurada` |
| R30 | `ConfiguracionAlmacenamientoS3Test.limitaLosTiempos` |

Todas con `@DisplayName("HU-29 R<n>: …")` (el contrato: "HU-29 R25" y "HU-29 R25, R18").

## Notas

- **R27 (nota de design § 13):** no hizo falta extraer `proveedorCredenciales()`. El SDK 2.59.1 devuelve la misma
  instancia en `serviceClientConfiguration().credentialsProvider()`, que la prueba comprueba con
  `isInstanceOf(DefaultCredentialsProvider.class)`.
- **Cliente HTTP:** el design dice `apache-client`. En el SDK 2.59.1 el cliente síncrono por defecto que trae `s3` es
  `apache5-client` (Apache HttpClient 5, runtime). Sigue siendo síncrono y el SDK lo elige solo, así que no se fijó
  ningún cliente HTTP. `netty-nio-client` queda excluido y no entra ninguna autoconfiguración de AWS en Spring
  (`dependency:tree`).
- **R5:** la excepción de `SeleccionAlmacenamiento` sale tal cual, no envuelta en una `BeanCreationException`: corre
  antes de instanciar cualquier bean. La prueba fija `" s3"` con un `MapPropertySource`, porque `withPropertyValues`
  recorta los espacios.
- `ConfiguracionAlmacenamientoS3Test.sinClavesDeAccesoDeAws` lee `src/main/resources/application.properties` y
  `.env.example` relativos a `2_Backend/` (directorio de trabajo de surefire).
- Sin `*IT` nueva (design § 13, aprobado por el dueño sin IT contra AWS real). La verificación contra el bucket real
  (design § 11) queda para UAT.

## Resultado de pruebas

**Ronda de review (2026-11-15), tras `progress/review_HU-29.md`:**

- `./init.sh` completo (sin `--rapido`), con Docker Desktop encendido: **exit 0, "[OK] Entorno listo"**.
  - Unitarias: "Tests run: 2195, Failures: 0, Errors: 0, Skipped: 0".
  - Integración (`*IT`, `./mvnw verify`): "Tests run: 1238, Failures: 0, Errors: 0, Skipped: 0". El contexto completo
    arranca bien con `SeleccionAlmacenamiento` y el `@ConditionalOnProperty` de `AlmacenamientoLocal`; no hizo falta
    tocarlos.
  - Arnés, coherencia, reglas de arquitectura, ESLint y build del frontend: `[OK]`.
- Cambio 2 del review: el javadoc de `ConfiguracionAlmacenamientoS3` (línea 15) quedó partido en dos líneas ≤ 120.
- Cambio 3 (HU-25 en `history.md`): lo registró el orquestador; no se tocó.

**Primera ronda (histórico):**

- `./2_Backend/mvnw -f 2_Backend/pom.xml test` (sin `ALMACENAMIENTO_TIPO` definida): **verde**, 0 fallas y 0 errores en
  los reportes de surefire.
- `./init.sh` completo: **FAIL solo por el entorno**: "Docker no responde: enciende Docker Desktop" y "Sin Docker no se
  pueden correr las pruebas *IT (./mvnw verify)". `docker info` no conecta con `dockerDesktopLinuxEngine`. No se buscó
  ningún workaround. El resto de los pasos del script pasa: arnés, coherencia de `feature_list`/specs/progress, las
  reglas de arquitectura (sin `@Autowired`, sin Lombok, sin imports comodín, sin prints, sin secretos), ESLint y
  `npm run build`.
- `./init.sh --rapido`: **verde**, "Tests run: 2195, Failures: 0, Errors: 0, Skipped: 0". Corrió todas las unitarias
  porque cambió `pom.xml`.
- El script también avisa que faltan `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA` y `ALMACENAMIENTO_LLAVE_FIRMA` en el
  entorno local. Es un WARN previo a la historia y no afecta las pruebas.

## Pendientes

1. ~~Correr `./init.sh` completo con Docker Desktop encendido~~ **Resuelto** en la ronda de review: 1238 IT, 0 fallas;
   T15 marcada.
2. Verificación en UAT contra el bucket real (design § 11): bucket, `LabRole`, endpoint gateway de S3. La hace la caja
   negra, no esta sesión.
3. Reviewer. La historia sigue `in_progress`; no se marcó `done`. No hubo commit, push ni PR.
