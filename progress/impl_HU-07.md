# Implementación — HU-07 · Publicar y editar un espacio

- **Dueño:** Sebastian Garate · **Rama:** `feature/HU-07-publicar-editar-espacio` · **Fecha:** 2026-10-28
- **Agente:** implementer · **Spec:** `specs/HU-07/` (aprobado por el dueño el 2026-10-28)
- **Estado:** todas las tasks `[x]` (T1, T1b, T1c, T2–T9, T9b, T10, T11, T11b, T12–T16). Reviewer: APPROVED
  (`progress/review_HU-07.md`). Corregida la observación 1 y cerrada: status `done`. Sin commit, push ni PR.

## Corrección tras la revisión (observación 1: `capacidad` decimal)

- **Comprobado:** `POST` con `"capacidad": 20.7` respondía **201** y guardaba `20` (Jackson trunca decimales
  a `Integer` y acepta `"20"` como texto), contra R4 ("entero de 1 a 100 000").
- **Corrección (acotada a HU-07):** `espacios/dto/FichaEspacioDTO.java` — el componente `capacidad` lleva
  `@JsonDeserialize(using = FichaEspacioDTO.SoloEntero.class)`. `SoloEntero` (clase anidada) acepta solo un
  número JSON entero dentro de `int`; cualquier otro valor (decimal, texto, booleano, objeto, arreglo, entero
  fuera de `int`) se lee como `null` y `@NotNull` responde 400 con el campo `capacidad` y `M_CAPACIDAD`.
  Sin cambios en la configuración global de Jackson (el resto de la API no se ve afectada) ni en el frontend
  (envía `Number(...)` y ya pinta el error por campo de `capacidad`).
- **Tests nuevos:** `EspacioControllerIT.capacidadNoEntera` (`20.7`, `0.5`, `"20"`, `"20.7"`, `true` → 400
  con un error en `capacidad`, 0 filas; `PUT` con `30.2` → 400 y fila intacta) y
  `FichaEspacioDTOTest.capacidadDesdeJson` (`20` y `100000` válidos; `20.7`, `0.5`, `20.0`, `1e2`, `"20"`,
  `true`, objeto, arreglo, `3000000000` y `null` → `null` e inválida con `M_CAPACIDAD`).
- **Spec:** `specs/HU-07/design.md` (fila `FichaEspacioDTO` de la tabla de clases y fila R4 de la matriz de
  trazabilidad).
- **`./init.sh` completo tras la corrección:** exit 0; 273 unitarias y 196 IT, 0 fallos; ESLint y build OK.

## Resultado de `./init.sh` (completo, sin `--rapido`)

```
[OK]    feature_list.json válido (28 historias: 5 done, 22 pending, 1 in_progress)
[OK]    Specs, progress y tasks coherentes con el estado de cada historia
[OK]    Paquetes Java dentro de los módulos previstos / sin controladores con repositorios / sin @Autowired
[OK]    Sin Lombok, imports con comodín, prints de depuración, console.log, colores fuera de tokens, secretos
          Tests run: 272, Failures: 0, Errors: 0, Skipped: 0   (unitarias, ./mvnw verify → surefire)
          Tests run: 195, Failures: 0, Errors: 0, Skipped: 0   (IT con Testcontainers → failsafe)
[OK]    Todos los tests del backend pasan (./mvnw verify)
[OK]    ESLint sin errores ni advertencias
[OK]    El frontend compila (npm run build)
[OK]    Entorno listo. Puedes empezar a trabajar.
```

Único `[WARN]`: las llaves `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA` y `ALMACENAMIENTO_LLAVE_FIRMA` no están en
el entorno de esta máquina (solo afecta a `spring-boot:run`; las pruebas traen las suyas). Ya existía antes de
HU-07. Las IT de HU-01…HU-05 siguen verdes sin cambios (también tras el refactor de T1 y las cascadas de
`V2_2`). Antes de la historia había 204 pruebas unitarias; HU-07 agrega 68 unitarias y 48 IT.

## Tasks

| Task | Hecho |
|---|---|
| T1 | `comun/web/ArchivoMultipart.recibido` (N); `IdentidadArrendadorController` lo usa en vez de su método privado (sin cambio de conducta). Test `ArchivoMultipartTest`. |
| T1b | `comun/almacenamiento/ArchivosPublicos` (N): guardar sin cifrar en `carpeta/<UUID>`, leer y eliminar con la validación de clave de `ArchivosPrivados.claveValida`. Javadoc de `AlmacenamientoArchivos`. `ArchivosPrivados`, `FirmadorUrl` y `ArchivoController` no cambian. Test `ArchivosPublicosTest` (adaptador falso). |
| T1c | `ConfiguracionSeguridad`: `GET /api/fotos/*` `permitAll` con comentario. Probado en `FotoPublicaControllerIT` (sin sesión → 200/404, nunca 401) y `EspacioControllerIT.sinSesion` (POST/DELETE de fotos → 401). |
| T2 | `cuentas/service/CuentaService` + `CuentaServiceImpl` + `cuentas/dto/HabilitacionArrendadorDTO`. Test `CuentaServiceImplTest`. |
| T3 | `db/migration/V2_2__espacio.sql`: `zona` (50 filas), `espacio`, `foto_espacio`, tal cual design § 9. `V2_2` libre en `origin/develop`. Validado por `EspacioYaApplicationIT` (Flyway + `ddl-auto=validate`). |
| T4 | `espacios/domain`: `EstadoEspacio`, `TipoEspacio`, `DatosEspacio`, `Espacio`, `FotoEspacio` (`orden` como `short` por el `SMALLINT`), `Zona` (`@Immutable`). Tests `EspacioTest`, `FotoEspacioTest`. |
| T5 | `EspacioRepository` (con `findParaActualizar` `PESSIMISTIC_WRITE`), `FotoEspacioRepository` (con `findByRutaArchivo`), `ZonaRepository`. Probados en las IT. |
| T6 | `FichaEspacioDTO` (Bean Validation, recorte en el constructor compacto, `aDatos()`, constantes `M_*`), `EspacioDTO`, `FotoEspacioDTO` (`id`, `orden`, `url`), `EspacioResumenDTO`, `ZonaDTO`, `FotoServida`. Test `FichaEspacioDTOTest`. |
| T7 | `EspacioService` (`listarZonas`, `listar`, `consultar`, `crear`, `editar`, `publicar`, `arrendadorHabilitado`). Test `EspacioServiceTest`. |
| T8 | `EspacioController`, `ZonaController`. Tests `EspacioControllerIT`, `ZonaControllerIT`. |
| T9 | `FotoEspacioService.agregar/eliminar` (no transaccionales; `TransactionTemplate`; compensación del archivo; borrado tras el commit) y `FotoEspacioController`. Tests `FotoEspacioServiceTest`, `FotoEspacioControllerIT`. |
| T9b | `FotoEspacioService.ver` y `FotoPublicaController` (`GET /api/fotos/{uuid}`). Tests `FotoEspacioServiceTest`, `FotoPublicaControllerIT`. |
| T10 | Kit: `Campo tipo="textarea"` (prop `filas`), `Tarjeta` (N; `develop` no la trae), estados de `Espacio` en `EtiquetaEstado`. |
| T11 | `api/cliente.js` (`peticionArchivo`), `api/espacios.js` (N), `pages/espacios/textosEspacios.js` (N). |
| T11b | `pages/espacios/FotoDelEspacio.jsx` + `.module.css`. |
| T12 | `pages/espacios/MisEspacios.jsx` + `.module.css`. |
| T13 | `pages/espacios/EditarEspacio.jsx` + `.module.css`. |
| T14 | `Panel.jsx` (enlace "Mis espacios") y `App.jsx` (3 rutas en `RutaProtegida`, `nuevo` antes de `:id`). |
| T15 | `docs/modelo-datos.md`, `docs/frontend.md`, `docs/architecture.md` (DT-15). Sin puertos nuevos: la tabla "Puertos entre módulos" no cambia. |
| T16 | `./init.sh` completo verde y este informe. |

## Archivos

**Backend — nuevos** (`2_Backend/src/main/java/pe/edu/ulima/espacioya/`)
- `comun/web/ArchivoMultipart.java`, `comun/almacenamiento/ArchivosPublicos.java`
- `cuentas/service/CuentaService.java`, `cuentas/service/CuentaServiceImpl.java`, `cuentas/dto/HabilitacionArrendadorDTO.java`
- `espacios/domain/{EstadoEspacio,TipoEspacio,DatosEspacio,Espacio,FotoEspacio,Zona}.java`
- `espacios/repository/{EspacioRepository,FotoEspacioRepository,ZonaRepository}.java`
- `espacios/dto/{FichaEspacioDTO,EspacioDTO,FotoEspacioDTO,EspacioResumenDTO,ZonaDTO,FotoServida}.java`
- `espacios/service/{EspacioService,FotoEspacioService}.java`
- `espacios/controller/{EspacioController,FotoEspacioController,FotoPublicaController,ZonaController}.java`
- `2_Backend/src/main/resources/db/migration/V2_2__espacio.sql`

**Backend — modificados**
- `comun/almacenamiento/AlmacenamientoArchivos.java` (solo javadoc)
- `comun/seguridad/ConfiguracionSeguridad.java` (`GET /api/fotos/*` `permitAll`)
- `cuentas/controller/IdentidadArrendadorController.java` (usa `ArchivoMultipart`)

**Pruebas — nuevas** (`2_Backend/src/test/java/pe/edu/ulima/espacioya/`)
- `comun/web/ArchivoMultipartTest.java`, `comun/almacenamiento/ArchivosPublicosTest.java`
- `cuentas/service/CuentaServiceImplTest.java`
- `espacios/domain/{EspacioTest,FotoEspacioTest}.java`, `espacios/dto/FichaEspacioDTOTest.java`
- `espacios/service/{EspacioServiceTest,FotoEspacioServiceTest}.java`
- `espacios/controller/EspaciosControllerSoporte.java` (base abstracta común: un solo contexto Spring y un solo
  PostgreSQL para las 4 IT; `@BeforeEach` de design § 14, IP `10.7.x.x`, `RelojControlable` `@Primary`)
- `espacios/controller/{EspacioControllerIT,FotoEspacioControllerIT,FotoPublicaControllerIT,ZonaControllerIT}.java`

Ninguna prueba de HU-01…HU-05 se modificó. Las imágenes se generan en memoria con `ArchivosDePrueba` (HU-05).

**Frontend** (`3_frontend/src/`)
- Nuevos: `api/espacios.js`, `components/ui/Tarjeta.jsx` + `.module.css`,
  `pages/espacios/{textosEspacios.js, FotoDelEspacio.jsx, MisEspacios.jsx, EditarEspacio.jsx}` + sus `.module.css`
- Modificados: `api/cliente.js`, `components/ui/Campo.jsx` + `.module.css`, `components/ui/EtiquetaEstado.jsx`,
  `pages/cuentas/Panel.jsx`, `App.jsx`

**Docs**: `docs/modelo-datos.md`, `docs/frontend.md`, `docs/architecture.md`.

## Desvíos del spec (menores, sin cambiar requirements ni decisiones)

1. **`Cache-Control` como texto literal** (`FotoPublicaController`). El design sugería
   `CacheControl.maxAge(1h).cachePublic()`, pero Spring lo serializa como `max-age=3600, public` y R39 fija el
   valor exacto `public, max-age=3600`. Se escriben las constantes `CACHE_PUBLICA = "public, max-age=3600"` y
   `CACHE_PRIVADA = "no-store"`. Semántica idéntica.
2. **Orden de `GET /api/zonas`** (`EspacioService.listarZonas`): se ordena por `nombre` en Java con
   `Collator` es-PE en vez de `ORDER BY nombre` en SQL, para no depender de la collation de la base (UAT vs.
   Testcontainers) con nombres con tildes ("Lurín", "Jesús María"). R8 solo pide "ordenadas por nombre".
3. **`api/cliente.js`**: para no duplicar código en `peticionArchivo` se extrajeron dos funciones internas
   (`errorDe`, `avisarSiPerdioSesion`) que `peticion` también usa. La firma y la conducta de `peticion` no
   cambian (mismo objeto de error, mismo manejo del 401).
4. **`App.jsx`**: la ruta `/panel/espacios/nuevo` monta `<EditarEspacio key="nuevo" />` para que, tras crear el
   borrador y navegar a `/panel/espacios/:id`, la pantalla se monte de cero (el mensaje de CA1 viaja en el
   `state` de la navegación). No cambia lo que se ve.
5. **Prueba de R55 con `..`**: los casos de valor con `..` usan `a..b` y el UUID con `-` reemplazado por `..`.
   Un segmento de ruta `..` o `%2F` lo rechaza antes el firewall de Spring Security (400), no llega al
   controlador; no forma parte del contrato de R55.

Ninguna task quedó sin completar.

## Notas para el reviewer

- **R44**: el controlador lee los bytes de la parte (como HU-05) antes de llamar al servicio; el servicio no
  valida ni almacena nada hasta pasar rol, correo, propiedad, estado y cupo (`FotoEspacioServiceTest`).
- **R43 en fotos**: `POST`/`DELETE` de fotos sin sesión → 401 están en `EspacioControllerIT.sinSesion`; el
  `GET /api/fotos/*` sin sesión en `FotoPublicaControllerIT.fotoPublicada` (200) y `fotoNoPublicadaAjena` (404).
- **R38**: el `UNIQUE (espacio_id, orden)` es diferido; con autocommit el `INSERT` duplicado falla al confirmar y
  Spring lo traduce a `DataIntegrityViolationException` (probado).
- **CA4 "reservas pagadas conservan su precio"** sigue a cargo de HU-12 (P13/DT-17): HU-07 no escribe fuera de
  `espacio`, `foto_espacio` y `auditoria`.
- La matriz §7 de `docs/requerimientos.md` no se tocó (la llena la pareja que prueba).

## Trazabilidad

- R1 → `EspacioControllerIT.crearBorrador`, `EspacioServiceTest.crearAudita`
- R2 → `EspacioControllerIT.crearBorrador`, `EspacioTest.crearEnBorrador`, `EspacioServiceTest.crearAudita`
- R3 → `EspacioControllerIT.ignoraIdentidadYEstadoDelCuerpo`, `FichaEspacioDTOTest.aDatos`
- R4 → `EspacioControllerIT.fichaInvalida`, `EspacioControllerIT.sinDescripcion`, `EspacioControllerIT.capacidadNoEntera`, `FichaEspacioDTOTest.capacidadDesdeJson`, `FichaEspacioDTOTest.fichaValida`, `FichaEspacioDTOTest.nombre`, `FichaEspacioDTOTest.nombreRecortado`, `FichaEspacioDTOTest.tipo`, `FichaEspacioDTOTest.direccion`, `FichaEspacioDTOTest.zona`, `FichaEspacioDTOTest.capacidad`, `FichaEspacioDTOTest.precio`, `FichaEspacioDTOTest.descripcionInvalida`, `FichaEspacioDTOTest.descripcionValida`, `FichaEspacioDTOTest.variosCampos`
- R5 → `EspacioControllerIT.zonaInexistente`, `EspacioServiceTest.zonaInexistente`
- R6 → `EspacioControllerIT.fichaInvalida`, `EspacioControllerIT.zonaInexistente`, `EspacioServiceTest.zonaInexistente`
- R7 → `EspacioControllerIT.minimizacion`
- R8 → `ZonaControllerIT.listaZonas`, `EspacioServiceTest.zonasOrdenadas`
- R9 → `EspacioControllerIT.listaSoloLosPropios`
- R10 → `EspacioControllerIT.minimizacion`, `FotoEspacioControllerIT.urlEstable`, `EspacioServiceTest.consultarArmaUrlEstable`
- R11 → `FotoEspacioControllerIT.urlEstable`, `EspacioServiceTest.consultarArmaUrlEstable`
- R12 → `EspacioControllerIT.publica`, `EspacioTest.publicarBorrador`, `EspacioServiceTest.publica`
- R13 → `EspacioControllerIT.publica`, `EspacioTest.publicarBorrador`, `EspacioServiceTest.publica`
- R14 → `EspacioControllerIT.soloBorrador`, `EspacioTest.publicarFueraDeBorradorLanza`, `EspacioTest.editableYPublicablePorEstado`, `EspacioServiceTest.soloBorradorAntesQueIdentidad`
- R15 → `CuentaServiceImplTest.identidadVerificadaSoloEnVerificada`, `EspacioControllerIT.identidadNoVerificada`, `EspacioServiceTest.identidadAntesQueFotos`
- R16 → `EspacioControllerIT.ordenDeMotivos`, `EspacioServiceTest.soloBorradorAntesQueIdentidad`, `EspacioServiceTest.identidadAntesQueFotos`
- R17 → `EspacioControllerIT.sinFotos`, `EspacioServiceTest.sinFotos`
- R18 → `EspacioControllerIT.soloBorrador`, `EspacioControllerIT.identidadNoVerificada`, `EspacioControllerIT.sinFotos`, `EspacioServiceTest.sinFotos`
- R19 → `EspacioControllerIT.soloPublicarPublica`, `EspacioTest.publicarFueraDeBorradorLanza`
- R20 → `EspacioControllerIT.editar`, `EspacioServiceTest.editarConCambiosAudita`
- R21 → `EspacioControllerIT.editar`, `EspacioTest.editarDevuelveCambios`, `EspacioTest.editarConservaEstado`, `EspacioServiceTest.editarConCambiosAudita`
- R22 → `EspacioControllerIT.editarPublicado`, `EspacioTest.editarConservaEstado`
- R23 → `EspacioControllerIT.estadoNoEditable`, `FotoEspacioControllerIT.estadoNoEditable`, `EspacioTest.editarNoEditableLanza`, `EspacioTest.editableYPublicablePorEstado`, `EspacioServiceTest.estadoAntesQueZona`, `FotoEspacioServiceTest.noEditableNoTocaLaFoto`
- R24 → `EspacioControllerIT.precioNumerico`, `FichaEspacioDTOTest.precio`
- R25 → `FotoEspacioControllerIT.agregaFotos`, `FotoEspacioServiceTest.fotoAceptada`
- R26 → `FotoEspacioControllerIT.agregaFotos`, `FotoEspacioTest.registrar`, `FotoEspacioServiceTest.fotoAceptada`
- R27 → `FotoEspacioControllerIT.sextaFoto`, `FotoEspacioServiceTest.cupoAgotado`, `FotoEspacioServiceTest.cupoAgotadoDentroDeLaTransaccion`
- R28 → `ArchivoMultipartTest.parteAusenteOVacia`, `ArchivoMultipartTest.parteNormal`, `FotoEspacioControllerIT.faltaFoto`, `FotoEspacioServiceTest.mensajesPorMotivo`
- R29 → `ArchivoMultipartTest.parteDemasiadoGrandeNoSeLee`, `ArchivoMultipartTest.parteNormal`, `FotoEspacioControllerIT.fotoDemasiadoGrande`, `FotoEspacioServiceTest.mensajesPorMotivo`
- R30 → `FotoEspacioControllerIT.tipoDeFoto`, `FotoEspacioServiceTest.pdfRechazado`, `FotoEspacioServiceTest.mensajesPorMotivo`
- R31 → `FotoEspacioControllerIT.imagenIlegible`, `FotoEspacioServiceTest.mensajesPorMotivo`
- R32 → `FotoEspacioControllerIT.sextaFoto`, `FotoEspacioControllerIT.faltaFoto`, `FotoEspacioControllerIT.fotoDemasiadoGrande`, `FotoEspacioControllerIT.tipoDeFoto`, `FotoEspacioControllerIT.imagenIlegible`, `FotoEspacioControllerIT.estadoNoEditable`, `FotoEspacioServiceTest.falloEnTransaccionBorraArchivo`, `FotoEspacioServiceTest.cupoAgotadoDentroDeLaTransaccion`
- R33 → `ArchivosPublicosTest.guardaSinCifrar`, `FotoEspacioControllerIT.sinMetadatos`
- R34 → `FotoEspacioControllerIT.quitaFoto`, `EspacioTest.tocar`, `FotoEspacioTest.moverA`, `FotoEspacioServiceTest.eliminarRenumera`
- R35 → `ArchivosPublicosTest.eliminaLoGuardado`, `FotoEspacioControllerIT.quitaFoto`, `FotoPublicaControllerIT.fotoEliminadaOSinArchivo`, `FotoEspacioServiceTest.eliminarRenumera`
- R36 → `FotoEspacioControllerIT.fotoDeOtroEspacio`, `FotoEspacioServiceTest.eliminarFotoDeOtroEspacio`
- R37 → `ArchivosPublicosTest.guardaSinCifrar`, `ArchivosPublicosTest.carpetaInvalida`, `FotoEspacioControllerIT.guardadaSinCifrarFueraDeLaBd`, `FotoEspacioServiceTest.fotoAceptada`
- R38 → `FotoEspacioControllerIT.restriccionesDeOrden`, `FotoEspacioTest.registrarFueraDeRango`, `FotoEspacioTest.moverA`
- R39 → `ArchivosPublicosTest.leeLoGuardado`, `ArchivosPublicosTest.claveMalFormadaNoTocaElAdaptador`, `FotoPublicaControllerIT.fotoPublicada`, `FotoEspacioServiceTest.verPublicada`
- R40 → `EspacioControllerIT.ajenoOInexistente`, `FotoEspacioControllerIT.ajenoOInexistente`, `EspacioServiceTest.consultarAjeno`, `EspacioServiceTest.propiedadAntesQueEstado`, `FotoEspacioServiceTest.ajenoNoTocaLaFoto`
- R41 → `CuentaServiceImplTest.rolDistintoDeArrendador`, `EspacioControllerIT.rolUsuario`, `EspacioServiceTest.rolPrimero`, `EspacioServiceTest.arrendadorHabilitado`, `FotoEspacioServiceTest.rolOCorreoNoTocaLaFoto`
- R42 → `CuentaServiceImplTest.correoVerificado`, `EspacioControllerIT.correoNoVerificado`, `EspacioServiceTest.consultaSinCorreoVerificado`, `EspacioServiceTest.correoAntesQuePropiedad`, `FotoEspacioServiceTest.rolOCorreoNoTocaLaFoto`
- R43 → `EspacioControllerIT.sinSesion`, `FotoPublicaControllerIT.fotoPublicada`, `ZonaControllerIT.sinSesion`
- R44 → `FotoEspacioServiceTest.rolOCorreoNoTocaLaFoto`, `FotoEspacioServiceTest.ajenoNoTocaLaFoto`, `FotoEspacioServiceTest.noEditableNoTocaLaFoto`, `FotoEspacioServiceTest.cupoAgotado`
- R45 → `EspacioControllerIT.auditaCreacion`, `EspacioServiceTest.crearAudita`
- R46 → `EspacioControllerIT.auditaEdicion`, `EspacioTest.editarDevuelveCambios`, `EspacioServiceTest.editarConCambiosAudita`
- R47 → `EspacioControllerIT.auditaEdicion`, `EspacioTest.editarSinCambios`, `EspacioServiceTest.editarSinCambiosNoAudita`
- R48 → `EspacioControllerIT.publica`, `EspacioServiceTest.publica`
- R49 → `FotoEspacioControllerIT.quitaFoto`, `FotoEspacioControllerIT.auditaAgregada`, `FotoEspacioServiceTest.fotoAceptada`, `FotoEspacioServiceTest.eliminarRenumera`
- R50 → `FotoEspacioControllerIT.sextaFoto`, `FotoEspacioControllerIT.tipoDeFoto`, `FotoEspacioServiceTest.cupoAgotado`, `FotoEspacioServiceTest.pdfRechazado`, `FotoEspacioServiceTest.cupoAgotadoDentroDeLaTransaccion`
- R51 → `FotoEspacioControllerIT.auditoriaSinNombreNiClave`
- R52 → `EspacioControllerIT.fichaAntesQueRol`
- R53 → `EspacioControllerIT.propiedadAntesQueEstado`, `EspacioServiceTest.rolPrimero`, `EspacioServiceTest.correoAntesQuePropiedad`, `EspacioServiceTest.propiedadAntesQueEstado`, `EspacioServiceTest.estadoAntesQueZona`
- R54 → `ArchivosPublicosTest.leeLoGuardado`, `ArchivosPublicosTest.claveMalFormadaNoTocaElAdaptador`, `FotoPublicaControllerIT.fotoNoPublicadaDelDueno`, `FotoEspacioServiceTest.verBorradorDelDueno`
- R55 → `FotoPublicaControllerIT.fotoNoPublicadaAjena`, `FotoPublicaControllerIT.uuidInvalido`, `FotoPublicaControllerIT.fotoEliminadaOSinArchivo`, `FotoEspacioServiceTest.uuidMalFormado`, `FotoEspacioServiceTest.verBorradorAjeno`, `FotoEspacioServiceTest.verSinFotoOSinArchivo`
