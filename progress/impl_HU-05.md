# Implementación — HU-05 · Envío de documentos de identidad del arrendador

- **Fecha:** 2026-10-28
- **Agente:** implementer
- **Rama:** feature/HU-05-documentos-identidad
- **Spec:** `specs/HU-05/` (aprobado por Kevin Chan el 2026-10-28, P1–P16 por defecto)
- **Tasks:** T1–T18 todas `[x]`
- **Verificación:** `./init.sh` completo en verde: 173 unitarias + 119 IT (`./mvnw verify` con Testcontainers),
  ESLint sin advertencias, `npm run build` OK, sin colores fuera de tokens ni secretos trackeados, y sin
  avisos de R sin `@DisplayName`.

## Trazabilidad

- R1 → `IdentidadArrendadorControllerIT.envioAceptado`, `IdentidadArrendadorServiceTest.envioAceptado`
- R2 → `IdentidadArrendadorControllerIT.envioAceptado`, `PerfilArrendadorTest.enviarDesdeNoIniciada`, `IdentidadArrendadorServiceTest.envioAceptado`
- R3 → `IdentidadArrendadorControllerIT.envioAceptado`, `DocumentoIdentidadTest.registrarGuardaLosDatos`, `IdentidadArrendadorServiceTest.envioAceptado`
- R4 → `IdentidadArrendadorControllerIT.envioDesdeRechazadaLimpiaMotivo`, `PerfilArrendadorTest.enviarDesdeRechazadaLimpiaMotivo`, `IdentidadArrendadorServiceTest.envioAceptado`
- R5 → `IdentidadArrendadorControllerIT.reenvioReemplaza`, `IdentidadArrendadorServiceTest.envioAceptado`, `AlmacenamientoLocalTest.eliminarIdempotente`
- R6 → `ValidadorArchivoTest.limiteExactoSeAcepta`, `IdentidadArrendadorControllerIT.limiteExactoSeAcepta`
- R7 → `IdentidadArrendadorControllerIT.ignoraArrendadorIdEnviado`
- R8 → `ValidadorArchivoTest.vacioSeRechaza`, `IdentidadArrendadorControllerIT.faltaUnaParte`, `IdentidadArrendadorControllerIT.parteVacia`, `IdentidadArrendadorServiceTest.vacioUsaMensajePropio`
- R9 → `ValidadorArchivoTest.unByteDeMasSeRechaza`, `ValidadorArchivoTest.noLeidoPorTamanoSeRechaza`, `IdentidadArrendadorControllerIT.parteDemasiadoGrande`
- R10 → `ValidadorArchivoTest.tipoNoPermitido`, `IdentidadArrendadorControllerIT.tipoNoPermitido`, `IdentidadArrendadorControllerIT.tipoMimeSaleDelContenido`
- R11 → `ValidadorArchivoTest.tipoPorFirmaReal`, `IdentidadArrendadorControllerIT.tipoMimeSaleDelContenido`
- R12 → `ValidadorArchivoTest.pngIlegible`, `ValidadorArchivoTest.pngDemasiadosPixeles`, `IdentidadArrendadorControllerIT.imagenIlegible`
- R13 → `ManejadorGlobalErroresTest.archivoDemasiadoGrandeResponde400`, `ManejadorGlobalErroresTest.multipartIlegibleResponde400`
- R14 → `IdentidadArrendadorControllerIT.{faltaUnaParte, parteVacia, parteDemasiadoGrande, tipoNoPermitido, imagenIlegible, dosPartesInvalidas, estadoNoPermitido}`, `IdentidadArrendadorServiceTest.{dosPartesInvalidas, falloEnTransaccionEliminaArchivosNuevos, falloEnSegundoGuardadoEliminaElPrimero}`
- R15 → `IdentidadArrendadorControllerIT.dosPartesInvalidas`, `IdentidadArrendadorServiceTest.dosPartesInvalidas`, `ManejadorGlobalErroresTest.validacionVariosCamposResponde400`
- R16 → `IdentidadArrendadorControllerIT.estadoNoPermitido`, `IdentidadArrendadorControllerIT.enviosSimultaneos`, `PerfilArrendadorTest.enviarDesdeEstadoNoPermitidoLanza`, `IdentidadArrendadorServiceTest.estadoNoPermitido`, `IdentidadArrendadorServiceTest.carreraPorEstado`
- R17 → `IdentidadArrendadorControllerIT.usuarioNoArrendador`, `IdentidadArrendadorServiceTest.rolUsuarioDenegado`, `ManejadorGlobalErroresTest.accesoDenegadoResponde403`
- R18 → `IdentidadArrendadorControllerIT.correoNoVerificado`, `IdentidadArrendadorServiceTest.correoNoVerificadoDenegado`, `ManejadorGlobalErroresTest.accesoDenegadoResponde403`
- R19 → `IdentidadArrendadorControllerIT.sinSesion`
- R20 → `IdentidadArrendadorServiceTest.{rolUsuarioDenegado, correoNoVerificadoDenegado, estadoNoPermitido}`, `IdentidadArrendadorControllerIT.{usuarioNoArrendador, correoNoVerificado}`
- R21 → `IdentidadArrendadorControllerIT.consultaRechazada`, `IdentidadArrendadorServiceTest.consultarRechazada`
- R22 → `IdentidadArrendadorControllerIT.consultaRechazada`, `IdentidadArrendadorServiceTest.consultarRechazada`
- R23 → `IdentidadArrendadorControllerIT.consultaOtrosEstados`, `IdentidadArrendadorServiceTest.consultarOtrosEstados`
- R24 → `PerfilArrendadorTest.puedeEnviarDocumentosPorEstado`, `IdentidadArrendadorControllerIT.{consultaRechazada, consultaOtrosEstados}`, `IdentidadArrendadorServiceTest.{consultarRechazada, consultarOtrosEstados}`
- R25 → `IdentidadArrendadorControllerIT.consultaDocumentosVigentes`, `IdentidadArrendadorServiceTest.consultarRechazada`
- R26 → `IdentidadArrendadorControllerIT.sinNombreOriginalNiNumero`
- R27 → `IdentidadArrendadorControllerIT.sinNombreOriginalNiNumero`, `ArchivosPrivadosTest.claveAleatoria`, `ArchivosPrivadosTest.claveMalFormadaNoTocaElAdaptador`, `AlmacenamientoLocalTest.claveFueraDelDirectorio`
- R28 → `PerfilArrendadorTest.avisoEnRevision`, `IdentidadArrendadorControllerIT.avisoDeSesion`
- R29 → `PerfilArrendadorTest.avisoRechazadaConMotivo`, `IdentidadArrendadorControllerIT.avisoDeSesion`
- R30 → `CifradorArchivosTest.{idaYVuelta, dosCifradosDistintos, byteAlteradoLanzaExcepcion, otraLlaveFalla, formatoDesconocidoFalla, llaveInvalidaFalla}`, `ArchivosPrivadosTest.guardaCifradoYLeeDescifrado`, `IdentidadArrendadorControllerIT.archivoCifradoEnDisco`
- R31 → `IdentidadArrendadorControllerIT.sinColumnasBinarias`, `AlmacenamientoLocalTest.{guardaYLee, leerInexistente}`
- R32 → `ValidadorArchivoTest.jpegSinExif`, `ValidadorArchivoTest.pngSinTexto`, `IdentidadArrendadorControllerIT.imagenAlmacenadaSinMetadatos`
- R33 → `ValidadorArchivoTest.pdfSinCambios`, `IdentidadArrendadorControllerIT.archivoCifradoEnDisco`
- R34 → `IdentidadArrendadorControllerIT.urlFirmadaSirveElArchivo`
- R35 → `IdentidadArrendadorControllerIT.urlFirmadaSirveElArchivo`
- R36 → `FirmadorUrlTest.alteracionInvalida`, `FirmadorUrlTest.otraLlaveInvalida`, `IdentidadArrendadorControllerIT.urlAlteradaResponde404`, `ManejadorGlobalErroresTest.recursoNoEncontradoResponde404`
- R37 → `FirmadorUrlTest.vencidaInvalida`, `IdentidadArrendadorControllerIT.urlVencidaResponde404`, `ManejadorGlobalErroresTest.recursoNoEncontradoResponde404`
- R38 → `FirmadorUrlTest.firmaConVencimientoDeCincoMinutos`, `ArchivosPrivadosTest.urlFirmada`, `IdentidadArrendadorControllerIT.consultaDocumentosVigentes`, `IdentidadArrendadorServiceTest.consultarRechazada`
- R39 → `FirmadorUrlTest.llaveInvalidaFalla`, `FirmadorUrlTest.llaveIgualACifradoFalla`
- R40 → `IdentidadArrendadorControllerIT.auditaCadaCarga`, `IdentidadArrendadorServiceTest.envioAceptadoAudita`
- R41 → `IdentidadArrendadorControllerIT.auditaRechazos`, `IdentidadArrendadorServiceTest.{estadoNoPermitido, dosPartesInvalidas, carreraPorEstado}`
- R42 → `IdentidadArrendadorControllerIT.{auditaCadaCarga, auditaRechazos}`, `IdentidadArrendadorServiceTest.envioAceptadoAudita`

Las IT de HU-01/02/03 (`SesionControllerIT`, `ArrendadorControllerIT`, `UsuarioControllerIT`,
`PoliticaPrivacidadControllerIT`) y sus unitarias no se tocaron y siguen verdes (DT-12).

## Archivos

Base del backend: `2_Backend/src/main/java/pe/edu/ulima/espacioya/`. Tests: `2_Backend/src/test/java/pe/edu/ulima/espacioya/`.

**Creados (backend main)**
- `comun/almacenamiento/`: `AlmacenamientoArchivos`, `AlmacenamientoLocal`, `ArchivosPrivados`, `FirmadorUrl`, `UrlFirmada`, `ArchivoController`, `ValidadorArchivo`, `ArchivoRecibido`, `ArchivoValidado`, `TipoArchivo`, `MotivoRechazoArchivo`
- `comun/cifrado/CifradorArchivos`
- `comun/error/`: `AccesoDenegadoException`, `RecursoNoEncontradoException`, `ValidacionException`
- `cuentas/domain/`: `DocumentoIdentidad`, `TipoDocumentoIdentidad`
- `cuentas/repository/DocumentoIdentidadRepository`
- `cuentas/service/IdentidadArrendadorService`
- `cuentas/dto/`: `IdentidadDTO`, `DocumentoIdentidadDTO`
- `cuentas/controller/IdentidadArrendadorController`
- `2_Backend/src/main/resources/db/migration/V2_1__documento_identidad.sql`

**Modificados (backend main)**
- `comun/cifrado/LlaveCifrado` (pasa a `public`)
- `comun/error/ErrorDTO` (+3 mensajes), `comun/error/ManejadorGlobalErrores` (+5 handlers)
- `comun/seguridad/ConfiguracionSeguridad` (`permitAll` de `GET /api/archivos`)
- `cuentas/domain/PerfilArrendador` (`puedeEnviarDocumentos`, `enviarDocumentos`, `avisoIdentidad`, 2 constantes)
- `cuentas/repository/PerfilArrendadorRepository` (`findParaActualizar` con `PESSIMISTIC_WRITE`)
- `cuentas/service/AvisoCuenta` (delega en `PerfilArrendador.avisoIdentidad()`)
- `2_Backend/src/main/resources/application.properties` (multipart, Tomcat, almacenamiento)

**Tests**
- Creados: `comun/almacenamiento/{ArchivosDePrueba, ValidadorArchivoTest, FirmadorUrlTest, AlmacenamientoLocalTest, ArchivosPrivadosTest}`, `comun/cifrado/CifradorArchivosTest`, `cuentas/domain/DocumentoIdentidadTest`, `cuentas/service/IdentidadArrendadorServiceTest`, `cuentas/controller/IdentidadArrendadorControllerIT`
- Modificados (solo se agregaron casos): `comun/error/ManejadorGlobalErroresTest`, `cuentas/domain/PerfilArrendadorTest`
- `2_Backend/src/test/resources/config/application.properties` (llave de firma de prueba y directorio temporal `${java.io.tmpdir}/espacioya-it-almacenamiento`)

**Frontend (`3_frontend/src/`)**
- Creados: `components/ui/EtiquetaEstado.jsx` y `.module.css`, `pages/cuentas/VerificarIdentidad.jsx` y `.module.css`
- Modificados: `components/ui/Campo.jsx` y `.module.css` (`tipo="archivo"`), `api/cliente.js` (`FormData`, `urlApi`), `api/cuentas.js` (`enviarDocumentosIdentidad`, `obtenerIdentidad`), `pages/cuentas/Panel.jsx` (enlace), `App.jsx` (ruta `/panel/identidad`)

**Configuración y docs**
- `2_Backend/.env.example`: `ALMACENAMIENTO_DIRECTORIO=` y `ALMACENAMIENTO_LLAVE_FIRMA=` sin valores, con comentario
- `.gitignore`: `2_Backend/almacenamiento/`
- `docs/modelo-datos.md` (`documento_identidad` y su `UNIQUE (arrendador_id, tipo)`), `docs/frontend.md` (kit: `Campo tipo="archivo"`, `EtiquetaEstado`)
- `docs/architecture.md`: sin cambios (sin puertos nuevos entre módulos, design § 8)

## Detalles de implementación que el spec no fijaba

Ninguno cambia un requirement ni una decisión de diseño. Los anoto para el reviewer.

1. Las constantes con los nombres de las partes viven en el servicio (`IdentidadArrendadorService.CAMPO_IDENTIDAD/CAMPO_PROPIEDAD`) y el controlador las reexpone como `PARTE_IDENTIDAD/PARTE_PROPIEDAD`. Así el servicio no depende del controlador.
2. `MotivoRechazoArchivo.VACIO` tiene el mensaje por defecto "Sube un archivo.", que el design no fijaba. En HU-05 el servicio siempre lo reemplaza por el mensaje propio de cada parte (R8).
3. `FirmadorUrl.RUTA = "/api/archivos"` es una constante compartida con `ArchivoController`.
4. DT-14, la carrera entre envíos: si la relectura con bloqueo encuentra un estado que ya no permite enviar, el servicio borra los dos archivos nuevos y audita `CARGA_DOCUMENTO_RECHAZADA` con el estado encontrado, fuera de la transacción revertida. Para eso usa una subclase privada de `ReglaNegocioException`, que responde el mismo 400. La cubren `IdentidadArrendadorServiceTest.carreraPorEstado` y `IdentidadArrendadorControllerIT.enviosSimultaneos`.
5. R13 se prueba solo con la unitaria `ManejadorGlobalErroresTest`, como dice design § 14. MockMvc no aplica los límites de multipart de Tomcat.
6. R20: el controlador lee los bytes de las partes de hasta 5 MiB antes de llamar al servicio, porque Spring ya parsea el multipart antes del controlador. Con rol, correo o estado no permitidos, el contenido no se valida ni se almacena. Lo comprueban los mocks `validador` y `archivos` sin interacciones y el directorio vacío en la IT.
7. `ArchivosDePrueba` (test) genera en memoria los PDF, PNG y JPEG, el JPEG con `APP1 Exif`, el PNG con `tEXt`, el PNG de 8000 × 6000 y el ZIP. Ningún archivo de prueba queda en el repo.
8. Se verificó en la IT que el `Cache-Control` de `GET /api/archivos` sale como `no-store`. El encabezado por defecto de Spring Security no lo pisa.

## Tasks no completadas

Ninguna.

## Notas para UAT y para el dueño

- `ALMACENAMIENTO_LLAVE_FIRMA` es obligatoria: sin ella, o si es igual a una llave de cifrado, el backend no arranca. Se genera con `openssl rand -base64 32`.
- `ALMACENAMIENTO_DIRECTORIO` tiene que apuntar a un volumen persistente en UAT (DT-1). Si no, los documentos se pierden al redesplegar.
- HU-06 (Ariel Enriquez) debe usar la siguiente migración `V2_n`, porque `V2_1` ya la ocupa HU-05 (P16).
