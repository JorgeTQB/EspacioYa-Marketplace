# Review — HU-05

**Veredicto:** APPROVED

Rama `feature/HU-05-documentos-identidad`. Todo el trabajo está sin commitear (22 archivos modificados más los nuevos; `git diff develop...HEAD` está vacío). Revisado contra `specs/HU-05/` (aprobado por Kevin Chan con P1–P16 en sus valores por defecto, anotado en `progress/HU-05.md`), `docs/` y `CHECKPOINTS.md`.

`./init.sh` completo (sin `--rapido`): **exit 0**. Backend con 173 unitarias y 119 IT (Testcontainers), 0 fallos. ESLint sin advertencias, `npm run build` OK, sin colores fuera de tokens y sin secretos trackeados.

## Trazabilidad requirements ↔ tests
Todos los tests llevan `@DisplayName("HU-05 R<n>: …")` y comprueban un resultado concreto (código HTTP, campos JSON, filas de BD, bytes en disco, filas de auditoría).
- R1: [x] `IdentidadArrendadorControllerIT.envioAceptado` (201, `estadoIdentidad=EN_REVISION`, solo 4 claves), `IdentidadArrendadorServiceTest.envioAceptado`
- R2: [x] `IdentidadArrendadorControllerIT.envioAceptado` (perfil `EN_REVISION` en BD), `PerfilArrendadorTest` (R2)
- R3: [x] `IdentidadArrendadorControllerIT.envioAceptado` (arrendador_id, tipo, tipo_mime, tamano, subido_en = reloj), `DocumentoIdentidadTest`
- R4: [x] `IdentidadArrendadorControllerIT.envioDesdeRechazadaLimpiaMotivo`, `PerfilArrendadorTest` (R4)
- R5: [x] `IdentidadArrendadorControllerIT.reenvioReemplaza` (ids nuevos, archivos anteriores ya no existen, quedan 2), `AlmacenamientoLocalTest` (eliminar idempotente)
- R6: [x] `ValidadorArchivoTest` (5 242 880 aceptado), `IdentidadArrendadorControllerIT.limiteExactoSeAcepta`
- R7: [x] `IdentidadArrendadorControllerIT.ignoraArrendadorIdEnviado` (parámetros y parte `arrendadorId`/`usuarioId` de otro arrendador, que sigue en NO_INICIADA)
- R8: [x] `IdentidadArrendadorControllerIT.faltaUnaParte`, `.parteVacia`, `IdentidadArrendadorServiceTest.vacioUsaMensajePropio`, `ValidadorArchivoTest` (R8)
- R9: [x] `IdentidadArrendadorControllerIT.parteDemasiadoGrande` (5 242 881 → 400 en su campo), `ValidadorArchivoTest` (R9 ×2)
- R10: [x] `IdentidadArrendadorControllerIT.tipoNoPermitido` (texto declarado `application/pdf`), `ValidadorArchivoTest` (texto y ZIP)
- R11: [x] `IdentidadArrendadorControllerIT.tipoMimeSaleDelContenido` (PNG como `x.pdf` → `image/png`), `ValidadorArchivoTest` (R11)
- R12: [x] `IdentidadArrendadorControllerIT.imagenIlegible`, `ValidadorArchivoTest` (basura y 8000 × 6000)
- R13: [x] `ManejadorGlobalErroresTest` (`MaxUploadSizeExceededException` → 400 con el mensaje de tamaño). Solo unitaria, como prevé design § 14 (MockMvc no aplica los límites de Tomcat)
- R14: [x] `IdentidadArrendadorControllerIT` (`sinCambios` tras cada 400: estado, 0 filas, directorio vacío), `IdentidadArrendadorServiceTest.falloEnTransaccionEliminaArchivosNuevos`, `.falloEnSegundoGuardadoEliminaElPrimero`
- R15: [x] `IdentidadArrendadorControllerIT.dosPartesInvalidas` (2 errores con campo y mensaje), `IdentidadArrendadorServiceTest.dosPartesInvalidas`
- R16: [x] `IdentidadArrendadorControllerIT.estadoNoPermitido` (EN_REVISION y VERIFICADA), `.enviosSimultaneos` (dos hilos a la vez, solo uno aceptado), `IdentidadArrendadorServiceTest.carreraPorEstado`
- R17: [x] `IdentidadArrendadorControllerIT.usuarioNoArrendador` (403 en POST y GET), `IdentidadArrendadorServiceTest.rolUsuarioDenegado`
- R18: [x] `IdentidadArrendadorControllerIT.correoNoVerificado`, `IdentidadArrendadorServiceTest.correoNoVerificadoDenegado`
- R19: [x] `IdentidadArrendadorControllerIT.sinSesion` (sin token y con token inventado → 401 con `MENSAJE_SESION_INVALIDA`)
- R20: [x] `IdentidadArrendadorServiceTest` (`verifyNoInteractions(validador, archivos…)` en 403 y en R16), IT con el directorio vacío
- R21: [x] `IdentidadArrendadorControllerIT.consultaRechazada` (`containsOnlyKeys` con las 4 claves)
- R22: [x] `IdentidadArrendadorControllerIT.consultaRechazada`, `IdentidadArrendadorServiceTest.consultarRechazada`
- R23: [x] `IdentidadArrendadorControllerIT.consultaOtrosEstados` (motivo guardado pero nulo en la respuesta)
- R24: [x] `PerfilArrendadorTest` (R24), `IdentidadArrendadorControllerIT.consultaOtrosEstados`, `.correoNoVerificado` (`puedeEnviar=false`)
- R25: [x] `IdentidadArrendadorControllerIT.consultaDocumentosVigentes` (`containsOnlyKeys` con los 7 campos)
- R26: [x] `IdentidadArrendadorControllerIT.sinNombreOriginalNiNumero`
- R27: [x] `IdentidadArrendadorControllerIT.sinNombreOriginalNiNumero` (`^identidad/<uuid>$` en BD y en disco), `ArchivosPrivadosTest` (R27 ×2), `AlmacenamientoLocalTest` (R27)
- R28: [x] `IdentidadArrendadorControllerIT.avisoDeSesion`, `PerfilArrendadorTest` (R28)
- R29: [x] `IdentidadArrendadorControllerIT.avisoDeSesion` (GET y POST `/api/sesiones`), `PerfilArrendadorTest` (R29)
- R30: [x] `IdentidadArrendadorControllerIT.archivoCifradoEnDisco`, `CifradorArchivosTest` (6 casos), `ArchivosPrivadosTest` (R30)
- R31: [x] `IdentidadArrendadorControllerIT.sinColumnasBinarias` (`information_schema` sin `bytea` ni `oid`), `AlmacenamientoLocalTest`
- R32: [x] `IdentidadArrendadorControllerIT.imagenAlmacenadaSinMetadatos`, `ValidadorArchivoTest` (JPEG sin Exif, PNG sin tEXt)
- R33: [x] `IdentidadArrendadorControllerIT.archivoCifradoEnDisco` (descifrado == PDF), `ValidadorArchivoTest` (R33)
- R34: [x] `IdentidadArrendadorControllerIT.urlFirmadaSirveElArchivo` (sin Bearer, 200, `content().bytes(pdf)`)
- R35: [x] `IdentidadArrendadorControllerIT.urlFirmadaSirveElArchivo` (`nosniff`, `no-store`, `Content-Disposition` inline con `documento.pdf` y `documento.png`)
- R36: [x] `IdentidadArrendadorControllerIT.urlAlteradaResponde404` (firma, clave, tipo, sin firma, path traversal), `FirmadorUrlTest` (R36 ×2)
- R37: [x] `IdentidadArrendadorControllerIT.urlVencidaResponde404` (200 a 4:59 y 404 a 5:00), `FirmadorUrlTest` (R37)
- R38: [x] `FirmadorUrlTest` (R38), `IdentidadArrendadorControllerIT.consultaDocumentosVigentes` (`urlExpiraEn` = reloj + 5 min), `ArchivosPrivadosTest` (R38)
- R39: [x] `FirmadorUrlTest` (vacía, ausente, largo inválido, igual a `CIFRADO_LLAVE` o a `CIFRADO_LLAVE_HUELLA`; el mensaje nombra la variable sin el valor)
- R40: [x] `IdentidadArrendadorControllerIT.auditaCadaCarga` (usuario, entidad, entidad_id = id de la fila, IP, fecha, detalle exacto), `IdentidadArrendadorServiceTest` (R40)
- R41: [x] `IdentidadArrendadorControllerIT.auditaRechazos` (`<parte>=<MOTIVO>` y `estado=EN_REVISION`), `IdentidadArrendadorServiceTest` (estado, dos partes, carrera)
- R42: [x] `IdentidadArrendadorControllerIT.sinDatosSensibles` (sin nombre original, número, `identidad/`, clave ni `%PDF`)

## Trazabilidad con la historia
- CA1: [x] R1, R2, R3, R6, R7, R11. Pantalla `VerificarIdentidad.jsx`: `Alerta` de éxito, `EtiquetaEstado` y sesión refrescada
- CA2: [x] R8, R9, R10, R12, R13, R14, R15. Pantalla: `Alerta` de error y error bajo cada `Campo`
- CA3 (veo el motivo en mi panel): [x] R22, R29. `Panel.jsx` muestra el `aviso` con el motivo y enlaza a `/panel/identidad`
- CA3 (puedo volver a subir): [x] R1, R4, R5, R24. El formulario se muestra solo si `puedeEnviar`
- (I) cifrados en almacenamiento privado: [x] R27, R30, R31. `ArchivosPrivados.guardar` siempre cifra (AES-256-GCM, `CifradorArchivos`) y el directorio está fuera del repo (`.gitignore:80`)
- (I) URL firmada de corta duración: [x] R25, R34–R39. `FirmadorUrl` (HMAC-SHA256, 5 min, `MessageDigest.isEqual`) y `ArchivoController`
- (E) solo el propio arrendador: [x] R7, R17, R19, R21, R25, R36. La identidad sale de `@AuthenticationPrincipal` y no hay ids en la URL
- (E) el admin: [x] diferido a HU-06 según P10, aprobado por el dueño. Quedan listos `ArchivosPrivados.urlFirmada` y la tabla
- (T) tipo real y tamaño: [x] R9–R13. `ValidadorArchivo` revisa los magic bytes, el tope de píxeles antes de decodificar y re-codifica las imágenes
- (R) registro de cada carga: [x] R40–R42

## Tasks completas
- T1: [x] · T2: [x] · T3: [x] · T4: [x] · T5: [x] · T6: [x] · T7: [x] · T8: [x] · T9: [x]
- T10: [x] · T11: [x] · T12: [x] · T13: [x] · T14: [x] · T15: [x] · T16: [x] · T17: [x] · T18: [x]

Las 18 están en `[x]` en `specs/HU-05/tasks.md` y se comprobó que el entregable de cada una existe en el árbol de trabajo (clases, migración, propiedades, `.env.example`, `.gitignore`, docs y frontend).

## Revisión de secretos y archivos
- `git ls-files` no contiene `.env`, llaves ni nada bajo `almacenamiento/`. `2_Backend/.env` está ignorado (`.gitignore:4`) y `2_Backend/almacenamiento/` también (`.gitignore:80`).
- `2_Backend/.env.example` agrega `ALMACENAMIENTO_DIRECTORIO=` y `ALMACENAMIENTO_LLAVE_FIRMA=` **sin valores**.
- `src/test/resources/config/application.properties` agrega una llave de firma de prueba, que en base64 es "pruebas-espacioya-firma-url-0003". Es solo para tests, igual que las llaves de prueba de HU-02, y está rotulada "SOLO PARA PRUEBAS". No es un secreto real.
- No hay archivos de prueba en el repo: `ArchivosDePrueba` genera PDF, PNG, JPEG y ZIP en memoria. Las IT escriben en `${java.io.tmpdir}/espacioya-it-almacenamiento` y lo vacían en `@BeforeEach`.
- Entre los archivos sin trackear (`git status`) no hay nada sospechoso (`*.tmp`, logs, `target/`).

## Checkpoints
- C1: [x] arnés completo y `./init.sh` completo con exit 0
- C2: [x] una sola historia `in_progress` por dueño · [x] `progress/HU-05.md` existe · n/a historia `done` con tests (HU-05 aún no está `done`) · [x] aprobación del dueño anotada en `progress/HU-05.md` · [x] el progress describe solo la sesión activa
- C3: [x] paquetes en `comun` y `cuentas` · [x] ningún controller importa un repository · [x] sin `@Autowired`, Lombok ni comodines · [x] sin prints · [x] `cuentas → comun` sin puertos nuevos y `comun` no conoce `cuentas` (el tipo viaja firmado, DT-3) · [x] DTO `record` (`IdentidadDTO`, `DocumentoIdentidadDTO`) sin campos sensibles · [x] identidad desde la sesión y estado y `puedeEnviar` calculados en el servidor · [x] `V2_1__documento_identidad.sql` nueva, sin migraciones de `develop` editadas · [x] almacenamiento detrás del Adapter `AlmacenamientoArchivos` · [x] nada fuera del alcance · [x] sin secretos · [x] sin colores fuera de tokens ni librerías de UI · [x] la pantalla usa el kit (`Campo tipo="archivo"`, `EtiquetaEstado`, `Alerta`, `Boton`), llama a la API solo vía `src/api/`, tiene un único botón primario, no usa estilos en línea, los tokens que usa existen todos en `tokens.css` y el error de red sale de `cliente.js` con "No pudimos conectar. Intenta de nuevo."
- C4: [x] `<Clase>Test` / `<Clase>IT` con `@Import(TestcontainersConfiguration.class)` · [x] PostgreSQL real · [x] `RelojControlable` / `Clock` fijo para los 5 min · [x] IT presente y concurrencia probada con dos hilos (`enviosSimultaneos`) · [x] resultados concretos, incluidos los caminos de error · [x] `./mvnw verify` con 292 tests en verde · [x] lint y build OK
- C5: [x] sin archivos sospechosos sin trackear · [ ] `progress/history.md` todavía no tiene entrada de HU-05: la agrega el orquestador al cerrar la sesión, no bloquea al reviewer · n/a progress vuelto a la plantilla (la historia no está `done`) · [x] `feature_list.json` en `in_progress` · [x] rama `feature/HU-05-documentos-identidad`
- C6: [x] `specs/HU-05/` completo · [x] EARS con tabla de trazabilidad que cubre CA1–CA3 y STRIDE I/E/T/R · [x] design con endpoints, clases, migración, secuencias y 7 alternativas descartadas (sin ★; aplica Adapter) · [x] tasks completas · [x] mapa R → test en `progress/impl_HU-05.md` · [x] ningún pendiente de §9 resuelto sin el dueño (P15, la retención, queda para HU-22)

## Observaciones no bloqueantes
1. `ArchivoController.java:43-48` solo convierte `IllegalStateException` (fallo al descifrar) en 404. Si `AlmacenamientoLocal.leer` lanza `UncheckedIOException` (error de E/S distinto de "no existe"), la respuesta es un 500 genérico. No filtra datos, pero el design § 4 dice "cualquier fallo → 404". Conviene unificarlo en una historia posterior (HU-06/HU-07 reutilizan el componente).
2. `AlmacenamientoLocal.java:36` usa `ATOMIC_MOVE`. Funciona porque el temporal está en el mismo directorio, pero si en UAT el volumen no admite movimientos atómicos lanzará `AtomicMoveNotSupportedException` (500). Hay que tenerlo en cuenta al configurar `ALMACENAMIENTO_DIRECTORIO`.
3. R13 solo se prueba con la unitaria del manejador (justificado en design § 14 e impl § 5). Conviene probar a mano en UAT un archivo de más de 6 MB para confirmar el 400 a través de Tomcat (`max-swallow-size`).
4. DT-12 reemplaza HU-03 R5 para `EN_REVISION` y `RECHAZADA`. El dueño lo aceptó con P13; hay que avisarlo en el PR para quien mantenga HU-03.
5. Avisar a Ariel Enriquez (HU-06) que `V2_1` ya está ocupada (P16).
6. Todo está sin commitear: el dueño debe revisar y hacer commit y PR hacia `develop`.

## Cambios requeridos
Ninguno.
