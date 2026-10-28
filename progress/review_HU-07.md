# Review — HU-07

**Veredicto:** APPROVED

Revisado sobre el working tree de `feature/HU-07-publicar-editar-espacio` (sin commitear), contra
`specs/HU-07/{requirements,design,tasks}.md`, `docs/historias/HU-07.md`, `docs/architecture.md`,
`docs/conventions.md`, `docs/frontend.md`, `docs/verification.md` y `CHECKPOINTS.md`.

`./init.sh` completo (sin `--rapido`) ejecutado por el reviewer: **exit 0**.
272 pruebas unitarias y 195 IT (Testcontainers) en verde, 0 fallos. ESLint y build OK.
Único `[WARN]`: llaves `CIFRADO_LLAVE*` / `ALMACENAMIENTO_LLAVE_FIRMA` ausentes en el entorno local
(ya existía antes de HU-07, no afecta a las pruebas).

## Trazabilidad requirements ↔ tests

Todos los `@DisplayName("HU-07 R<n>: …")` existen y comprueban un resultado concreto (código HTTP,
cuerpo, filas en BD, archivo en disco, auditoría exacta).

- R1: [x] `EspacioControllerIT.crearBorrador` (201, BORRADOR, fotos vacías)
- R2: [x] `EspacioControllerIT.crearBorrador` (fila recortada, arrendador de la sesión, fechas del reloj), `EspacioTest.crearEnBorrador`
- R3: [x] `EspacioControllerIT.ignoraIdentidadYEstadoDelCuerpo`
- R4: [x] `EspacioControllerIT.fichaInvalida`, `EspacioControllerIT.sinDescripcion`, `FichaEspacioDTOTest.*` (límites de cada campo, descripción 9/10/2000/2001, recorte)
- R5: [x] `EspacioControllerIT.zonaInexistente`, `EspacioServiceTest.zonaInexistente`
- R6: [x] `EspacioControllerIT.fichaInvalida`, `EspacioControllerIT.zonaInexistente` (fila idéntica tras el rechazo)
- R7: [x] `EspacioControllerIT.minimizacion` (`containsOnlyKeys`, sin `arrendador`, correo, teléfono ni `espacios/`)
- R8: [x] `ZonaControllerIT.listaZonas` (50 zonas, solo `codigo`/`nombre`, ordenadas)
- R9: [x] `EspacioControllerIT.listaSoloLosPropios`
- R10: [x] `EspacioControllerIT.minimizacion`, `FotoEspacioControllerIT.urlEstable`, `EspacioServiceTest.consultarArmaUrlEstable`
- R11: [x] `FotoEspacioControllerIT.urlEstable`, `EspacioServiceTest.consultarArmaUrlEstable`
- R12: [x] `EspacioControllerIT.publica`
- R13: [x] `EspacioControllerIT.publica` (estado y `actualizado_en` en BD)
- R14: [x] `EspacioControllerIT.soloBorrador`, `EspacioServiceTest.soloBorradorAntesQueIdentidad`
- R15: [x] `EspacioControllerIT.identidadNoVerificada` (NO_INICIADA, EN_REVISION, RECHAZADA)
- R16: [x] `EspacioControllerIT.ordenDeMotivos`, `EspacioServiceTest.soloBorradorAntesQueIdentidad`, `EspacioServiceTest.identidadAntesQueFotos`
- R17: [x] `EspacioControllerIT.sinFotos`
- R18: [x] `EspacioControllerIT.soloBorrador`, `.identidadNoVerificada`, `.sinFotos` (fila idéntica)
- R19: [x] `EspacioControllerIT.soloPublicarPublica`
- R20: [x] `EspacioControllerIT.editar`
- R21: [x] `EspacioControllerIT.editar` (misma fila, `creado_en` intacto), `EspacioTest.editarConservaEstado`
- R22: [x] `EspacioControllerIT.editarPublicado`
- R23: [x] `EspacioControllerIT.estadoNoEditable`, `FotoEspacioControllerIT.estadoNoEditable`, `FotoEspacioServiceTest.noEditableNoTocaLaFoto`
- R24: [x] `EspacioControllerIT.precioNumerico` (lee `information_schema`: numeric(8,2))
- R25: [x] `FotoEspacioControllerIT.agregaFotos`
- R26: [x] `FotoEspacioControllerIT.agregaFotos`, `FotoEspacioTest.registrar`
- R27: [x] `FotoEspacioControllerIT.sextaFoto`, `FotoEspacioServiceTest.cupoAgotado`, `.cupoAgotadoDentroDeLaTransaccion`
- R28: [x] `FotoEspacioControllerIT.faltaFoto`, `ArchivoMultipartTest.parteAusenteOVacia`
- R29: [x] `FotoEspacioControllerIT.fotoDemasiadoGrande`, `ArchivoMultipartTest.parteDemasiadoGrandeNoSeLee`
- R30: [x] `FotoEspacioControllerIT.tipoDeFoto` (PDF y texto con nombre .jpg), `FotoEspacioServiceTest.pdfRechazado`
- R31: [x] `FotoEspacioControllerIT.imagenIlegible`, `FotoEspacioServiceTest.mensajesPorMotivo` (la rama de más de 40 MP la cubre `ValidadorArchivoTest.pngDemasiadosPixeles` de HU-05 junto con el mapeo `IMAGEN_ILEGIBLE`)
- R32: [x] `FotoEspacioControllerIT.{sextaFoto,faltaFoto,fotoDemasiadoGrande,tipoDeFoto,imagenIlegible,estadoNoEditable}` (sin filas ni archivos), `FotoEspacioServiceTest.falloEnTransaccionBorraArchivo`
- R33: [x] `FotoEspacioControllerIT.sinMetadatos` (EXIF y `tEXt` ausentes en disco)
- R34: [x] `FotoEspacioControllerIT.quitaFoto`, `FotoEspacioServiceTest.eliminarRenumera`
- R35: [x] `FotoEspacioControllerIT.quitaFoto`, `FotoPublicaControllerIT.fotoEliminadaOSinArchivo`
- R36: [x] `FotoEspacioControllerIT.fotoDeOtroEspacio`, `FotoEspacioServiceTest.eliminarFotoDeOtroEspacio`
- R37: [x] `FotoEspacioControllerIT.guardadaSinCifrarFueraDeLaBd` (firma JPEG/PNG en disco, sin columnas `bytea`/`oid`), `ArchivosPublicosTest.guardaSinCifrar`
- R38: [x] `FotoEspacioControllerIT.restriccionesDeOrden` (orden 6, 0 y repetido dan `DataIntegrityViolationException`)
- R39: [x] `FotoPublicaControllerIT.fotoPublicada` (sin sesión, otra cuenta y dueño; `Content-Type`, `nosniff`, `public, max-age=3600`, bytes iguales al disco)
- R40: [x] `EspacioControllerIT.ajenoOInexistente`, `FotoEspacioControllerIT.ajenoOInexistente`
- R41: [x] `EspacioControllerIT.rolUsuario` (7 endpoints, 403), `CuentaServiceImplTest.rolDistintoDeArrendador`
- R42: [x] `EspacioControllerIT.correoNoVerificado`
- R43: [x] `EspacioControllerIT.sinSesion`, `ZonaControllerIT.sinSesion`, `FotoPublicaControllerIT.fotoPublicada` (GET de fotos sin sesión no da 401)
- R44: [x] `FotoEspacioServiceTest.{rolOCorreoNoTocaLaFoto,ajenoNoTocaLaFoto,noEditableNoTocaLaFoto,cupoAgotado}` (`verifyNoInteractions(validador, archivos)`)
- R45: [x] `EspacioControllerIT.auditaCreacion` (detalle exacto)
- R46: [x] `EspacioControllerIT.auditaEdicion` (`precioHora=80.00->90.00;descripcion`)
- R47: [x] `EspacioControllerIT.auditaEdicion`, `EspacioServiceTest.editarSinCambiosNoAudita`
- R48: [x] `EspacioControllerIT.publica`
- R49: [x] `FotoEspacioControllerIT.auditaAgregada`, `FotoEspacioControllerIT.quitaFoto`
- R50: [x] `FotoEspacioControllerIT.sextaFoto`, `.tipoDeFoto`, `FotoEspacioServiceTest.pdfRechazado`
- R51: [x] `FotoEspacioControllerIT.auditoriaSinNombreNiClave`
- R52: [x] `EspacioControllerIT.fichaAntesQueRol`
- R53: [x] `EspacioControllerIT.propiedadAntesQueEstado`, `EspacioServiceTest.{rolPrimero,correoAntesQuePropiedad,propiedadAntesQueEstado,estadoAntesQueZona}`
- R54: [x] `FotoPublicaControllerIT.fotoNoPublicadaDelDueno` (BORRADOR y PAUSADO, `no-store`)
- R55: [x] `FotoPublicaControllerIT.fotoNoPublicadaAjena` (sin sesión, token inválido, otra cuenta), `.uuidInvalido` (inexistente, mal formado, mayúsculas, con `..`; mismo cuerpo), `.fotoEliminadaOSinArchivo`

## Trazabilidad con la historia

- CA1 (correo verificado + campos obligatorios, borrador; hasta 5 fotos): [x] R1-R6, R8, R24-R27, R34, R35, R38, R42. Implementado en `EspacioService.crear` y `FotoEspacioService.agregar/eliminar`.
- CA2 (identidad verificada, publica y queda visible en el catálogo): [x] R12, R13, R16, R17, R19, R39. `EspacioService.publicar`; la foto de un publicado se sirve sin sesión.
- CA3 (sin identidad se impide y se indica verificar): [x] R15, R16, R18. Mensaje en `EspacioService.java:51-52`; enlace "Verificar mi identidad" dentro de la alerta de error en `EditarEspacio.jsx` (`publicar`).
- CA4 (edito un publicado, cambios visibles; reservas pagadas conservan precio): [x] R20-R23. La parte de reservas pagadas queda a cargo de HU-12 (`reserva.precio_hora_aplicado`), decidido en P13 y aceptado por el dueño al aprobar el spec; HU-07 no tiene tabla `reserva`. Aceptable.
- (E) Propiedad del recurso en cada edición: [x] R3, R36, R40-R44, R52, R53. Todas las consultas del dueño filtran por `arrendador_id` de la sesión (`EspacioRepository.findByIdAndArrendadorId`, `findParaActualizar`); la identidad sale de `@AuthenticationPrincipal`.
- (T/I) Imágenes validadas en tipo y tamaño y reprocesadas sin metadatos: [x] R28-R33, R37. `ValidadorArchivo` de HU-05 + rechazo explícito de PDF (`FotoEspacioService.java:133-145`).
- (I) Fotos de borrador solo para su dueño, publicadas públicas (P4): [x] R11, R39, R54, R55. `FotoEspacioService.ver` (`FotoEspacioService.java:117-131`).
- (R) Registro de creación y edición: [x] R45-R51.

Decisiones del dueño verificadas:
- Fotos públicas sin cifrar con URL estable `GET /api/fotos/{uuid}`: [x] `ArchivosPublicos` no cifra; `FotoPublicaController` en `/api/fotos/{uuid}`; `permitAll` solo para `GET /api/fotos/*` (`ConfiguracionSeguridad.java:50-52`); `FotoEspacioDTO` con solo `id`, `orden`, `url` sin firma ni vencimiento.
- Fotos de borrador solo para su dueño, 404 a otros: [x] el mismo `RecursoNoEncontradoException` en todos los casos; probado sin sesión, con token inválido y con otra cuenta.
- `descripcion` obligatoria 10-2000: [x] `FichaEspacioDTO` con `@NotBlank` y `@Size(min = 10, max = 2000)` tras recortar; `descripcion VARCHAR(2000) NOT NULL` en `V2_2__espacio.sql`; el formulario no la marca como opcional.

## Tasks completas

- T1: [x] `comun/web/ArchivoMultipart` + refactor de `IdentidadArrendadorController` (IT de HU-05 verdes)
- T1b: [x] `comun/almacenamiento/ArchivosPublicos` + javadoc del adaptador
- T1c: [x] `GET /api/fotos/*` con `permitAll` y comentario
- T2: [x] `CuentaService`, `CuentaServiceImpl`, `HabilitacionArrendadorDTO`
- T3: [x] `V2_2__espacio.sql` (`zona` con 50 filas, `espacio`, `foto_espacio` con CHECK y UNIQUE diferido)
- T4: [x] dominio `espacios/domain`
- T5: [x] repositorios
- T6: [x] DTO como `record`
- T7: [x] `EspacioService`
- T8: [x] `EspacioController`, `ZonaController` + IT
- T9: [x] `FotoEspacioService.agregar/eliminar`, `FotoEspacioController` + tests
- T9b: [x] `FotoEspacioService.ver`, `FotoPublicaController` + tests
- T10: [x] `Campo tipo="textarea"`, `Tarjeta`, estados de `Espacio` en `EtiquetaEstado`
- T11: [x] `peticionArchivo`, `api/espacios.js`, `textosEspacios.js`
- T11b: [x] `FotoDelEspacio.jsx` (libera el objeto con `revokeObjectURL`)
- T12: [x] `MisEspacios.jsx`
- T13: [x] `EditarEspacio.jsx`
- T14: [x] `Panel.jsx`, `App.jsx` (`nuevo` antes de `:id`)
- T15: [x] `docs/modelo-datos.md`, `docs/frontend.md`, `docs/architecture.md`
- T16: [x] `./init.sh` verde (confirmado por el reviewer) y mapa R a test en `progress/impl_HU-07.md`

## Desvíos del spec (evaluación)

1. `Cache-Control` como literal `public, max-age=3600` / `no-store` (`FotoPublicaController.java`): **aceptable**. Cumple el texto exacto de R39/R54 con la misma semántica.
2. Orden de zonas con `Collator` es-PE en Java en vez de `ORDER BY`: **aceptable**. R8 solo pide "ordenadas por nombre" y así no depende de la collation de la BD; el IT lo comprueba con el mismo `Collator`.
3. Extracción de `errorDe` y `avisarSiPerdioSesion` en `api/cliente.js`: **aceptable**. `peticion` conserva firma y conducta (mismo objeto de error, mismo manejo del 401).
4. `<EditarEspacio key="nuevo" />` en `App.jsx`: **aceptable**. Solo fuerza el remontaje al pasar de `nuevo` a `:id`.
5. Casos con `..` de R55 probados como `a..b` y UUID con `..`: **aceptable**. Un segmento `..` o `%2F` lo corta antes el firewall de Spring Security con un 400 genérico, sin revelar si la foto existe.

## Checkpoints

- C1 Arnés completo: [x] (verificado por `./init.sh`, exit 0)
- C2 Estado coherente:
  - [x] una sola historia `in_progress` por dueño; existe `progress/HU-07.md`
  - n/a toda historia `done` con tests (HU-07 aún no está `done`)
  - [x] aprobación del dueño anotada en `progress/HU-07.md` (Sebastian Garate, 2026-10-28)
  - [x] `progress/HU-07.md` describe solo la sesión activa
- C3 Arquitectura:
  - [x] paquetes en módulos previstos; ningún controller importa repositorio; sin `@Autowired` en main, sin Lombok ni comodines; sin prints de depuración
  - [x] `espacios` usa `cuentas` solo vía la interfaz `CuentaService` (documentada en `docs/architecture.md`); sin ciclos; sin puertos nuevos
  - [x] la API recibe y devuelve DTO `record`; ninguna entidad sale; R7 probado
  - [x] identidad desde la sesión; el estado lo decide el servidor; `id`, `arrendadorId`, `usuarioId` y `estado` del cuerpo se ignoran
  - [x] esquema solo por `V2_2__espacio.sql`; ninguna migración existente editada
  - [x] almacenamiento detrás del Adapter `AlmacenamientoArchivos` vía `ArchivosPublicos`
  - [x] nada fuera del alcance (máx. 5 fotos, precio único por hora, sin mapa ni video)
  - [x] sin secretos; sin colores fuera de tokens (todos los `var(--…)` usados existen en `tokens.css`); sin librerías de UI
  - [x] pantallas con el kit (`Campo`, `Boton`, `Alerta`, `EtiquetaEstado`, `Tarjeta`), un solo `Boton` primario por pantalla ("Crear un espacio"; "Guardar borrador"/"Guardar cambios"), errores y éxitos según `docs/frontend.md`, colores de estado de `Espacio` según su tabla, llamadas solo vía `src/api/`; coincide con design § 13
- C4 Verificación real:
  - [x] `*Test` con Mockito y `*IT` (base `EspaciosControllerSoporte` con `@Import(TestcontainersConfiguration.class)`)
  - [x] sin H2
  - [x] `Clock` fijo / `RelojControlable` para las fechas
  - [x] IT para endpoints, migración y restricciones (R38); el cupo concurrente se serializa con `PESSIMISTIC_WRITE` y se revalida dentro de la transacción (`cupoAgotadoDentroDeLaTransaccion`)
  - [x] cada test comprueba un resultado concreto, incluidos los caminos de error
  - [x] `./mvnw verify`: 272 + 195 pruebas verdes
  - [x] `npm run lint && npm run build` sin errores
- C5 Cierre de sesión:
  - [x] sin archivos sin trackear sospechosos
  - n/a entrada de cierre en `progress/history.md` y vuelta de `progress/HU-07.md` a la plantilla (corresponden al cierre, después de esta aprobación)
  - [x] `feature_list.json` refleja `in_progress` (el orquestador/dueño la pasa a `done` al cerrar)
  - [x] trabajo en `feature/HU-07-publicar-editar-espacio`
- C6 SDD:
  - [x] `specs/HU-07/` completo
  - [x] EARS estricto y tabla de trazabilidad con todo CA y control STRIDE
  - [x] `design.md` con endpoints, clases, migración, secuencia y alternativas descartadas (HU-07 no es ★)
  - [x] todas las tasks en `[x]`
  - [x] R1-R55 con test, documentado en `progress/impl_HU-07.md`
  - [x] los pendientes P1-P16 los confirmó el dueño al aprobar el spec (P4 y P5 decididas explícitamente)

## Observaciones (no bloquean)

1. `2_Backend/src/main/java/pe/edu/ulima/espacioya/espacios/dto/FichaEspacioDTO.java`: `capacidad` es
   `Integer`. Según la configuración de Jackson, un JSON con `"capacidad": 20.7` podría convertirse en `20`
   en lugar de responder 400 (R4: "entero de 1 a 100 000"). Ningún test envía un decimal en `capacidad`.
   Conviene comprobarlo y, si se trunca, agregar el caso a `EspacioControllerIT.fichaInvalida` o
   desactivar la coerción de decimales a enteros. El frontend envía `Number(...)` del campo, así que
   también puede mandar decimales.
2. `FotoEspacioService.ver` (`FotoEspacioService.java:117-131`) no oculta las fotos de un arrendador
   `SUSPENDIDA` (`docs/architecture.md`: "sus espacios no se muestran"). Queda fuera de los R de HU-07;
   anotarlo para la historia que implemente la suspensión.
3. `FotoEspacioService.registrar` y `quitar` llaman a `espacio.tocar(...)`, que actualiza
   `actualizado_en` al agregar o quitar fotos. El spec no lo pide ni lo prohíbe; es coherente, pero
   conviene reflejarlo en `design.md` si se vuelve a tocar.

## Cambios requeridos

Ninguno.
