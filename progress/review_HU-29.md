# Review — HU-29 (ronda 2)

**Veredicto:** APPROVED

> Segunda ronda. La primera (CHANGES_REQUESTED) aprobó el código, las pruebas y la trazabilidad, y solo dejó tres
> pendientes: `./init.sh` completo sin correr porque Docker estaba apagado (más T15 en `[ ]`), una línea de javadoc de
> más de 120 caracteres y el registro de HU-25 en `progress/history.md`. Verifiqué yo mismo que los tres están
> resueltos. No hay cambios nuevos que alteren lo aprobado en la ronda 1.

Fecha: 2026-11-15 · Rama `feature/HU-29-almacenamiento-s3` · Todo sin commitear.

## Evidencia ejecutada en esta revisión

- `./init.sh` completo (sin `--rapido`), con Docker Desktop encendido: **exit 0**, "[OK] Entorno listo. Puedes empezar
  a trabajar."
  - "[OK] Docker encendido".
  - Unitarias: "Tests run: 2195, Failures: 0, Errors: 0, Skipped: 0".
  - Integración (`*IT`, `./mvnw verify`): "Tests run: 1238, Failures: 0, Errors: 0, Skipped: 0". El contexto completo
    arranca con `SeleccionAlmacenamiento` y el `@ConditionalOnProperty` de `AlmacenamientoLocal`, que era el riesgo
    señalado en la ronda 1.
  - Reglas de arquitectura y convenciones, ESLint y build del frontend: `[OK]`. Sin `[FAIL]`.
  - Sigue el `[WARN]` previo a la historia (faltan `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA` y
    `ALMACENAMIENTO_LLAVE_FIRMA` en el entorno local). No afecta las pruebas ni es de HU-29.
- Las cifras coinciden con las que informa `progress/impl_HU-29.md` ("Resultado de pruebas").

## Pendientes de la ronda 1

1. **`./init.sh` completo y T15:** [x] Resuelto. Exit 0 con 1238 IT verdes (arriba). `specs/HU-29/tasks.md:62` tiene
   `- [x] T15`.
2. **Javadoc de más de 120 caracteres:** [x] Resuelto.
   `2_Backend/src/main/java/pe/edu/ulima/espacioya/comun/almacenamiento/ConfiguracionAlmacenamientoS3.java:15-16` ahora
   es una línea partida en dos. Un Grep `^.{121,}$` sobre los `.java` de `comun/almacenamiento/` solo encuentra
   `ArchivoControllerTest.java:44` (`@DisplayName` de HU-06). Ese archivo es previo y HU-29 no lo modifica.
3. **HU-25 en `progress/history.md`:** [x] Resuelto. Está en la entrada "2026-11-15 — HU-25 marcada `done` por su dueño
   (registro de estado)" (líneas 1780-1783). La registró el orquestador.

## Sin regresiones respecto de la ronda 1

- `git status` muestra exactamente el mismo conjunto de archivos modificados y nuevos que en la ronda 1. No hay
  archivos nuevos ni tocados en otros módulos.
- `git diff --stat` de los archivos trackeados es coherente con lo ya revisado. `AlmacenamientoLocal.java` (+/-15) y
  `AlmacenamientoArchivos.java` (+/-13) mantienen el alcance aprobado: la anotación, las excepciones vía
  `FallaAlmacenamiento` y el javadoc.
- `ConfiguracionAlmacenamientoS3.java` solo cambia en el salto de línea del javadoc. Sigue usando
  `DefaultCredentialsProvider.builder().build()` (línea 40), la región es obligatoria y los tiempos son de 10 s / 30 s.

## Trazabilidad requirements ↔ tests

Sin cambios desde la ronda 1, y ahora con la suite completa verde. Todas las pruebas están en
`2_Backend/src/test/java/pe/edu/ulima/espacioya/comun/almacenamiento/`, con `@DisplayName("HU-29 R<n>: …")`.

- R1: [x] `SeleccionAlmacenamientoTest.sinTipoUsaElDisco`
- R2: [x] `SeleccionAlmacenamientoTest.tipoLocalUsaElDisco`
- R3: [x] `SeleccionAlmacenamientoTest.sinClienteS3EnLocal`
- R4: [x] `SeleccionAlmacenamientoTest.tipoS3UsaElBucket`
- R5: [x] `SeleccionAlmacenamientoTest.tipoInvalidoFalla`
- R6: [x] `AlmacenamientoS3Test.sinBucketNoArranca`
- R7: [x] `ConfiguracionAlmacenamientoS3Test.sinRegionFalla`
- R8: [x] `AlmacenamientoS3Test.compruebaElBucketAlArrancar`
- R9: [x] `AlmacenamientoS3Test.bucketInexistente`
- R10: [x] `AlmacenamientoS3Test.bucketInaccesible`
- R11: [x] `AlmacenamientoS3Test.bucketEnOtraRegion`
- R12: [x] `AlmacenamientoS3Test.otraFallaAlComprobar`
- R13: [x] `SeleccionAlmacenamientoTest.s3QueFallaNoPasaAlDisco`
- R14: [x] `AlmacenamientoS3Test.guardarEnviaPutObject`
- R15: [x] `AlmacenamientoS3Test.guardarPideSseS3`
- R16: [x] `AlmacenamientoS3Test.guardarSinAcl`
- R17: [x] `AlmacenamientoS3Test.leerDevuelveLosBytes`
- R18: [x] `AlmacenamientoS3Test.leerLlaveInexistente`, `ContratoAlmacenamientoArchivos.leerInexistenteEsVacio`
- R19: [x] `AlmacenamientoS3Test.eliminarEnviaDeleteObject`
- R20: [x] `AlmacenamientoS3Test.guardarFalla`
- R21: [x] `AlmacenamientoS3Test.leerFalla`
- R22: [x] `AlmacenamientoS3Test.eliminarFalla`
- R23: [x] `AlmacenamientoS3Test.fallaSinDetalles`
- R24: [x] `AlmacenamientoS3Test.claveInvalidaNoLlegaAS3`
- R25: [x] `ContratoAlmacenamientoArchivos` en `AlmacenamientoLocalContratoTest` y `AlmacenamientoS3ContratoTest`
- R26: [x] `AlmacenamientoS3Test.archivosPrivadosSubeCifrado`
- R27: [x] `ConfiguracionAlmacenamientoS3Test.credencialesDeLaCadenaPorDefecto`
- R28: [x] `ConfiguracionAlmacenamientoS3Test.sinClavesDeAccesoDeAws`
- R29: [x] `ConfiguracionAlmacenamientoS3Test.usaLaRegionConfigurada`
- R30: [x] `ConfiguracionAlmacenamientoS3Test.limitaLosTiempos`

## Trazabilidad con la historia

- CA1: [x] R1, R2, R3
- CA2: [x] R4, R8, R14, R17, R19, R25, R29
- CA3: [x] R6–R13
- CA4: [x] R5
- CA5: [x] R18, R25
- (I) bucket privado, SSE-S3 y cifrado de la aplicación: [x] R15, R16, R26. Block Public Access queda para
  infraestructura de UAT, con justificación (design § 11).
- (S/E) sin claves de acceso: [x] R27, R28
- (T) claves validadas: [x] R24
- (D) falla de S3 con la misma excepción y sin detalles: [x] R20–R23, R30

## Tasks completas

- T1–T15: [x] (`specs/HU-29/tasks.md`). T15 está verificada con el `./init.sh` completo de esta ronda.

## Checkpoints

- C1 arnés completo: [x]. `./init.sh` completo terminó con exit 0.
- C2 estado coherente:
  - Una sola historia `in_progress` por dueño: [x]
  - `progress/HU-29.md` existe: [x]
  - Historias `done` con tests: n/a
  - Aprobación del dueño anotada: [x]
  - Progress solo con la sesión activa: [x]
- C3 arquitectura:
  - Módulo `comun`: [x]
  - Controller sin repository: [x]
  - Sin `@Autowired`, Lombok ni comodines: [x]
  - Sin prints: [x]
  - Dependencias entre módulos: [x]
  - DTO en la API: n/a
  - Identidad desde la sesión, montos y estados en el servidor: n/a
  - Migraciones: n/a
  - Integración externa detrás de un Adapter (★ `AlmacenamientoS3`): [x]
  - Fuera del alcance: [x]
  - Sin secretos: [x]
  - Frontend: n/a
- C4 verificación:
  - Nombres `<Clase>Test`: [x]
  - Sin H2: [x]
  - Clock fijo: n/a
  - `*IT` nueva: n/a (design § 13, aprobada por el dueño)
  - Resultado concreto en cada test: [x]
  - `./init.sh` completo con IT verdes: [x] (1238 IT, 0 fallas)
  - Lint y build del frontend: [x]
- C5 cierre:
  - Sin archivos sospechosos: [x]
  - Entrada de HU-29 en `progress/history.md`: [ ]. Le toca al cierre que hace el dueño. No bloquea esta revisión.
  - Progress a la plantilla: n/a (lo hace el dueño al cerrar)
  - Estado en `feature_list.json`: [x] (`in_progress`, el dueño lo pasa a `done`)
  - Rama: [x]
- C6 SDD: [x] (spec completo, EARS, design con patrón, tasks `[x]` y mapa R → test en `impl_HU-29.md`)

## Cambios requeridos

Ninguno. Al cerrar, el dueño debe:

1. Registrar la entrada de HU-29 en `progress/history.md` y volver `progress/HU-29.md` a la plantilla.
2. Marcar HU-29 como `done` y hacer commit/PR. Conviene que el cambio de HU-25 en `feature_list.json` y el de
   `4_Infraestructure/arquitectura-aws.html` (sdd: false) vayan en commits identificables, separados de HU-29.
