# Implementación HU-24 · Reseñas y calificaciones

- **Rama:** `feature/HU-24-resenas-calificaciones`, sin commit, push ni PR.
- **Spec:** `specs/HU-24/`, aprobado por Ariel Enriquez el 2026-11-06.
- **Estado:** historia verificada y aprobada por el reviewer (`progress/review_HU-24.md`). `./init.sh` completo salió 0.
- **Tasks:** T1–T29 `[x]`.

## Verificación: qué se ejecutó y qué no

Esta sección se actualizó al cerrar. Las pruebas de integración no se pudieron correr durante la implementación porque Docker
no respondía; se ejecutaron después, con Docker encendido.

| Comprobación | Resultado | Detalle |
|--------------|-----------|---------|
| `./init.sh` completo (corrida del reviewer, `progress/review_HU-24.md`) | **Ejecutado, salida 0** | `[OK] Entorno listo`: 1722 unitarias y 1044 IT, 0 fallos, 0 errores, 0 omitidas; ESLint y build OK; colores, arquitectura y secretos OK |
| `./init.sh` completo (corrida de cierre, 2026-11-06) | **Ejecutado, salida 0** | Mismas cifras. Queda el aviso de que faltan `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA` y `ALMACENAMIENTO_LLAVE_FIRMA` |
| Clases de HU-24 en `failsafe-reports` (corrida del reviewer) | **Ejecutado, verde** | `ResenaControllerIT` 68, `ResenaRepositoryIT` 12, `LimiteFichaIT` 9, `DatosPersonalesControllerIT` 36, sin fallos |
| Migración `V7_2__resena.sql` | **Ejecutada** | Aplicada a PostgreSQL real por las IT |
| Arranque del contexto de Spring | **Ejecutado** | `ddl-auto=validate` contra `resena` y beans nuevos, dentro de las IT |
| Consultas JPQL de `ResenaRepository` | **Ejecutadas** | Contra PostgreSQL real, incluida `totales` |
| Carrera de R14 | **Ejecutada** | 5 rondas contra PostgreSQL real |
| Corridas de `./init.sh` cortadas | **Sin resultado** | Dos corridas se cortaron en las pruebas del backend (salida 4, sin salida de Maven) con Docker apagado. Causa sin determinar; no cuentan a favor ni en contra (`review_HU-24.md`, observación 4) |
| Pantallas en el navegador | **NO ejecutado** | Solo lectura de código, lint y build. La revisión visual queda para la caja negra en UAT |

**Sin workaround:** no se generaron secretos, no se levantó otra base y no se tocó ninguna configuración. Las llaves que faltan
no afectan a `./init.sh`, porque las pruebas traen las suyas en `src/test/resources`; solo hacen falta para `spring-boot:run`.

### Sobre los `[x]` de las tasks de IT

T3, T12, T14, T19, T20 y T22 se marcaron `[x]` al escribir las pruebas, antes de poder ejecutarlas. T29 exigía verlas pasar y se
marcó al cerrar, con las dos partes cumplidas: `./init.sh` en verde y el mapa R1–R43 → test, que está abajo.

## Trazabilidad

43 de 43 `R<n>` tienen al menos una prueba con `@DisplayName("HU-24 R<n>: …")`, en 130 anotaciones. `U` = unitaria, `IT` = de
integración; ambas ejecutadas y en verde en `./init.sh` completo.

R11, R13, R18, R19, R20, R32, R36, R38 y R43 solo tienen IT.

| R | Unitaria (`U`) | Integración (`IT`) |
|---|----------------|----------------------------------|
| R1 | `ResenaServiceTest.creaLaResena`, `.espacioNoVisible` | `ResenaControllerIT.identidadDelServidor`, `.espacioNoVisible`; `ResenaRepositoryIT.guardaYLee` |
| R2 | `ResenaServiceTest.creaLaResena`, `.fechaEnLima` | `ResenaControllerIT.creaYRespondeCincoCampos` |
| R3 | `ResenaServiceTest.identidadDelServidor`; `CrearResenaDTOTest.camposExtraDescartados` | `ResenaControllerIT.identidadDelServidor` |
| R4 | `ReservasParaResenaImplTest.finEnLima`, `.finConHora24` | `ResenaControllerIT.finConBloquesInactivos` |
| R5 | `ReglasResenaTest.finExacto`, `.unInstanteAntesDeVencer`; `ResenaServiceTest.enElInstanteDelFin` | `ResenaControllerIT.enElInstanteDelFin`, `.dentroDelPlazo` |
| R6 | `ReglasResenaTest.noConfirmada`; `ResenaServiceTest.noConfirmada`; `ReservasParaResenaImplTest.pendienteVencida`, `.canceladaNoEstaConfirmada`, `.soloConfirmadas` | `ResenaControllerIT.estadosNoConfirmados` |
| R7 | `ReglasResenaTest.unInstanteAntesDelFin`; `ResenaServiceTest.sinTerminar` | `ResenaControllerIT.sinTerminar` |
| R8 | `ReglasResenaTest.venceExacto`; `ResenaServiceTest.plazoVencido` | `ResenaControllerIT.plazoVencido` |
| R9 | `ResenaServiceTest.ajena`; `ReservasParaResenaImplTest.ajenaOInexistente` | `ResenaControllerIT.ajenaEInexistente` |
| R10 | `ResenaServiceTest.otroRol` | `ResenaControllerIT.otroRol` |
| R11 | — | `ResenaControllerIT.sinSesion` |
| R12 | `ResenaServiceTest.yaResenada`, `.yaResenadaConPlazoVencido` | `ResenaControllerIT.dobleResena`, `.yaResenadaOcultaOVencida` |
| R13 | — | `ResenaRepositoryIT.guardaYLee`, `.unaPorReserva` |
| R14 | `ResenaServiceTest.carreraContraElIndice`, `.otraViolacionSeRelanza` | `ResenaControllerIT.dosEnviosALaVez` |
| R15 | `CrearResenaDTOTest.reservaInvalida`, `.cuerpoValido` | `ResenaControllerIT.reservaInvalida` |
| R16 | `CrearResenaDTOTest.calificacionInvalida`, `.calificacionValida`, `.cuerpoValido` | `ResenaControllerIT.calificacionInvalida` |
| R17 | `CrearResenaDTOTest.comentarioInvalido`, `.largoDelComentario`, `.cuerpoValido` | `ResenaControllerIT.comentarioInvalido`, `.largoDelComentario` |
| R18 | — | `ResenaRepositoryIT.calificacionFueraDeRango` |
| R19 | — | `ResenaRepositoryIT.estadoInvalido` |
| R20 | — | `ResenaRepositoryIT.reservaConResena`, `.espacioYUsuarioConResena` |
| R21 | `SanitizadorTextoTest` (9 pruebas: `script`, `atributosDeEvento`, `etiquetaAnidada`, `controlDentroDeUnaEtiqueta`, `angularesSueltos`, `saltosDeLinea`, `caracteresDeControl`, `textoNormalIntacto`, `recortaExtremos`); `ResenaServiceTest.comentarioSanitizado` | `ResenaControllerIT.guardaSanitizado` |
| R22 | `SanitizadorTextoTest.quedaVacio`; `ResenaServiceTest.comentarioQuedaVacio` | `ResenaControllerIT.quedaVacio`, `.quedaVacioAntesDeLeerLaReserva` |
| R23 | `ResenaServiceTest.auditaLaCreacion` | `ResenaControllerIT.auditaLaCreacion` |
| R24 | `ResenaServiceTest.rechazoSinAuditoria` | `ResenaControllerIT.rechazosSinAuditoria` |
| R25 | `ResenaServiceTest.pendientes`, `.sinPendientes` | `ResenaControllerIT.pendientesDelUsuario`, `.saleDePendientes` |
| R26 | `ResenaServiceTest.pendientesConOtroRol` | `ResenaControllerIT.pendientesConOtroRol` |
| R27 | `FichaServiceTest.fichaConResumen`; `ResenaServiceTest.resumenDeVisibles` | `ResenaControllerIT.fichaConPromedio` |
| R28 | `ResumenCalificacionesTest.medioExactoRedondeaHaciaArriba`, `.medioQueUnDoubleRedondeariaMal`, `.unDecimal` | `ResenaControllerIT.fichaConPromedio` |
| R29 | `ResumenCalificacionesTest.sinResenas`; `FichaServiceTest.fichaSinResenas`; `ResenaServiceTest.resumenVacio` | `ResenaControllerIT.fichaSinResenas`; `ResenaRepositoryIT.totalesSinResenas` |
| R30 | `ResenaServiceTest.primeraPagina`, `.paginaPedida` | `ResenaControllerIT.dosPaginas`, `.diezExactas` |
| R31 | `ResenaServiceTest.primeraPagina` | `ResenaControllerIT.cuatroClaves` |
| R32 | — | `ResenaControllerIT.publica` |
| R33 | `ResenaServiceTest.idMalEscrito`, `.espacioInvisible` | `ResenaControllerIT.espacioInvisible`, `.arrendadorSuspendido`, `.idMalo` |
| R34 | `ResenaServiceTest.paginaInvalida`, `.paginaEnorme`, `.ultimaPaginaConsultable` | `ResenaControllerIT.paginaInvalida`, `.paginaEnorme` |
| R35 | `ResenaServiceTest.resumenDeVisibles` | `ResenaControllerIT.ocultaFueraDeTodo`; `ResenaRepositoryIT.totalesSoloVisibles`, `.paginaSoloVisibles` |
| R36 | — | `ResenaControllerIT.apareceAlCrearla` |
| R37 | `ReglasLimiteFichaTest.reglaDeLasResenas`, `.resenasCompartenElCupo` | `LimiteFichaIT.resenasCompartenElCupo`, `.resenasInvalidasCuentan` |
| R38 | — | `LimiteFichaIT.resenasConCupoAgotado` |
| R39 | `DatosPersonalesResenasImplTest.exportaLasResenas`, `.seccionVacia`, `.vaDespuesDePagos` | `DatosPersonalesControllerIT.exportaLasResenasDelUsuario`, `.seccionDeResenasVacia` |
| R40 | `DatosPersonalesResenasImplTest.arrendadorSinSeccion` | `DatosPersonalesControllerIT.arrendadorSinSeccionDeResenas` |
| R41 | `DatosPersonalesResenasImplTest.sinImpedimentos` | `DatosPersonalesControllerIT.resenaNoImpideEliminar` |
| R42 | `DatosPersonalesResenasImplTest.anonimiza` | `DatosPersonalesControllerIT.eliminarAnonimizaLasResenas`; `ResenaRepositoryIT.reemplazaSoloLasDelUsuario` |
| R43 | — | `DatosPersonalesControllerIT.eliminarAnonimizaLasResenas` |

## Diferencias frente al spec

**Una corrección de código.** `design.md` § 6 dice que el offset de `PageRequest` es `long` y que una página enorme devuelve una
lista vacía. Lo segundo solo se cumple con una protección: `PageableUtils.getOffsetAsInteger` de spring-data-jpa 4.1.2 lanza
`InvalidDataAccessApiUsageException` si el offset pasa de `Integer.MAX_VALUE`, confirmado con `javap` sobre el jar. Sin
protección, `pagina` ≥ 214748366, válida según R34, daría un 500. `ResenaService.deEspacio` responde esa página vacía sin
consultar. No cambia ningún `R<n>`. El `design.md` no se editó porque es un spec aprobado; el comportamiento quedó en
`docs/architecture.md`.

**Cambios menores fuera de la lista del diseño:**

- `ParticipanteDatosPersonales`: solo el Javadoc, que decía "más adelante `busqueda`".
- `docs/architecture.md`: la nota de HU-14 que autorizaba a HU-24 a ampliar `ReservaDTO` ahora la deja solo para HU-26 (P14).
- `FichaControllerIT`: además de la aserción, cambió el texto del `@DisplayName` de HU-11 R2, porque decía "diez campos".
- `@AfterEach` con `DELETE FROM resena` en `ResenaRepositoryIT` y `ResenaControllerIT`. `ResenaRepositoryIT` comparte contexto y
  base con otras IT que borran `reserva` y `usuario`; sin él, esas IT fallarían por la FK sin cascada.

**Para que lo mire el reviewer:**

- Con `2 < 4 y 5 > 3`, la sanitización de P4 borra todo lo que está entre `<` y `>` y deja `2  3`: `< 4 y 5 >` parece una
  etiqueta. Cumple R21, pero la pérdida de texto es real. El spec solo discute `5 > 3`, que sí conserva los números.
- El T1 confirmó `V7_2` libre con `git fetch origin develop`: la última allí es `V7_1`. HU-25 también es del sprint 7; si llega
  antes, hay que renombrar antes del merge.
- `ResenaControllerIT` usa su propio reloj y, por tanto, su propio contexto y contenedor. Da aislamiento, pero suma tiempo a `verify`.

## Tasks no completadas

Ninguna. T29 quedó cumplida al cerrar; ver [Verificación](#verificación-qué-se-ejecutó-y-qué-no).

## Archivos

Raíces: `J/` = `2_Backend/src/main/java/pe/edu/ulima/espacioya/`, `T/` = `2_Backend/src/test/java/pe/edu/ulima/espacioya/`,
`F/` = `3_frontend/src/`.

**Creados, backend**

- `2_Backend/src/main/resources/db/migration/V7_2__resena.sql`
- `J/busqueda/domain/`: `EstadoResena`, `MotivoNoResenable`, `ReglasResena`, `Resena`, `ResumenCalificaciones`
- `J/busqueda/repository/`: `ResenaRepository`, `TotalesResena`
- `J/busqueda/dto/`: `CrearResenaDTO`, `PaginaResenasDTO`, `ResenaCreadaDTO`, `ResenaExportadaDTO`, `ResenaPublicaDTO`,
  `ResenasPendientesDTO`
- `J/busqueda/service/ResenaService`, `J/busqueda/service/impl/DatosPersonalesResenasImpl`, `J/busqueda/controller/ResenaController`
- `J/comun/texto/SanitizadorTexto`
- `J/reservas/service/`: `ReservaParaResena`, `ReservasParaResena`, `impl/ReservasParaResenaImpl`

**Creados, pruebas**

- `T/busqueda/domain/ReglasResenaTest`, `T/busqueda/domain/ResumenCalificacionesTest`, `T/busqueda/dto/CrearResenaDTOTest`
- `T/busqueda/service/ResenaServiceTest`, `T/busqueda/service/impl/DatosPersonalesResenasImplTest`
- `T/comun/texto/SanitizadorTextoTest`, `T/reservas/service/impl/ReservasParaResenaImplTest`
- `T/busqueda/repository/ResenaRepositoryIT`, `T/busqueda/controller/ResenaControllerIT`

**Creados, frontend**

- `F/pages/reservas/ResenarReserva.jsx` y `.module.css`
- `F/pages/busqueda/ResenasEspacio.jsx` y `.module.css`

**Modificados**

- `J/busqueda/controller/ReglasLimiteFicha`, `J/busqueda/dto/FichaPublicaDTO`, `J/busqueda/service/BusquedaService`,
  `J/busqueda/service/FichaService`, `J/comun/seguridad/ConfiguracionSeguridad`,
  `J/cuentas/service/puertos/ParticipanteDatosPersonales`
- `T/busqueda/controller/FichaControllerIT`, `T/busqueda/controller/LimiteFichaIT`, `T/busqueda/controller/ReglasLimiteFichaTest`,
  `T/busqueda/service/FichaServiceTest`, `T/cuentas/controller/DatosPersonalesControllerIT`,
  `T/cuentas/controller/DatosPersonalesSoporte`
- `F/api/busqueda.js`, `F/pages/busqueda/FichaEspacio.jsx`, `F/pages/busqueda/textosFicha.js`,
  `F/pages/reservas/MisReservas.jsx`, `F/pages/reservas/textosReservas.js`
- `docs/architecture.md`, `docs/frontend.md`, `docs/modelo-datos.md`, `docs/requerimientos.md`
- `specs/HU-24/tasks.md`, `progress/HU-24.md`

---

## Reapertura 2026-11-13 (★ Decorator, DAO)

- **Spec:** `specs/HU-24/` (R1–R58, DT-1–DT-27), aprobado por Ariel Enriquez el 2026-11-13. P21 = sí
  (`DatosPersonalesResenasImpl` y `ModeracionResenasImpl` migran al DAO); P22 = `ESC-24`.
- **Tasks:** T30–T40 `[x]`. Sin tasks pendientes.
- **Sin commit, push ni PR. Status sigue `in_progress`** (no se marca `done` hasta el reviewer).

### Verificación

| Comprobación | Resultado | Detalle |
|--------------|-----------|---------|
| `./2_Backend/mvnw -f 2_Backend/pom.xml test` | **Salida 0** | Tras T30–T36 |
| `./2_Backend/mvnw -f 2_Backend/pom.xml verify` (1.ª corrida) | **Salida 1, ajena a HU-24** | Surefire: **2152** unitarias, 0 fallos, 0 errores. Failsafe: **1238** IT, 0 fallos, **1 error** en `cuentas.controller.MfaInicioSesionIT.auditoriaSinSecretos`: `CannotGetJdbcConnectionException` / `PSQLException: This connection has been closed`, justo después de que Hikari avisara de "Thread starvation or clock leap detected (housekeeper delta=1m25s)" (pausa del host). No es de HU-24 ni usa reseñas: **no se tocó** |
| `./init.sh` completo (corrida final) | **Salida 0, `[OK] Entorno listo`** | 134 unitarias de las clases tocadas + **1238 IT, 0 fallos, 0 errores, 0 omitidas** (incluida `MfaInicioSesionIT`, que esta vez pasó); arquitectura, colores y secretos OK; ESLint y build OK |
| `./init.sh` completo tras el review (R58, 2026-11-13) | **Salida 0, `[OK] Entorno listo`** | `ContratoResenasTest` nuevo (4 pruebas `HU-24 R58`, verdes). 138 unitarias de las 12 clases tocadas + **1238 IT**, 0 fallos, 0 errores. Ya **no** aparece el WARN de R58; solo queda el de las llaves de cifrado y firma (no afecta a las pruebas) |
| Suites de HU-24 (en `verify`) | Verde | `ResenaControllerIT` 68, `ResenaRepositoryIT` 12, `ResenaDAOJpaIT` 5, `PublicacionResenaIT` 2, `FichaControllerIT` 23, `LimiteFichaIT` 9; unitarias `ResenaServiceTest` 44, `ResenaDAOJpaTest` 8, `PublicacionResenaBaseTest` 5, `PublicacionTransaccionalTest` 5, `PublicacionAuditadaTest` 2, `PublicacionSanitizadaTest` 2, `ConfiguracionPublicacionResenaTest` 3, `AccesoResenasTest` 12, `FichaServiceTest` 40 |
| Suites de HU-22 | Verde | `DatosPersonalesControllerIT` 37, `DatosPersonalesConcurrenciaIT` 6, `DatosPersonalesReclamosIT` 5, `DatosPersonalesReportesIT` 3, `DatosPersonalesResenasImplTest` 6 |
| Suites de HU-26 | Verde | `ReclamoControllerIT` 44, `ReclamoAdminControllerIT` 30, `ReclamoLiquidacionIT` 4, `ReclamoReembolsoFallidoIT` 1, `ReclamoRestriccionesIT` 4 |
| Suites de HU-27 | Verde | `ReporteControllerIT` 18, `ReporteAdminControllerIT` 21, `ReporteRestriccionesIT` 6, `ModeracionResenasImplTest` 7 |
| R58 | Cumplido | `git diff develop --stat` vacío en `2_Backend/src/main/resources/db/`, `busqueda/dto/`, `busqueda/controller/`, `comun/` y `3_frontend/`. Tampoco cambiaron las pruebas "Sin cambios" de `design.md` § 14 (`git diff develop --stat` vacío en `T/busqueda/controller/`, `T/cuentas/`, `T/administracion/`, `T/comun/` y `ResenaRepositoryIT`) |

### Trazabilidad R44–R58

| R | Pruebas |
|---|---------|
| R44 | `ConfiguracionPublicacionResenaTest.ordenDeLaCadena`, `.beanUsaLaCadena`, `.clientSoloConoceElComponent`; `PublicacionResenaIT.cadenaRealGuardaYAudita` |
| R45 | `PublicacionSanitizadaTest.pasaElComentarioLimpio` |
| R46 | `PublicacionSanitizadaTest.vacioNoInvocaLaEnvuelta` |
| R47 | `PublicacionTransaccionalTest.confirmaSiVuelve`, `.revierteSiLanza` |
| R48 | `PublicacionTransaccionalTest.traduceLaCarrera`, `.otraViolacionIgual`, `.otraExcepcionIgual` |
| R49 | `PublicacionAuditadaTest.registraDespues` |
| R50 | `PublicacionAuditadaTest.noRegistraSiLanza` |
| R51 | `PublicacionResenaIT.auditoriaFallaSinFila`, `.cadenaRealGuardaYAudita` |
| R52 | `PublicacionResenaBaseTest.ordenDeLaRegla`, `.ajenaSinDao`, `.yaResenadaAntesQueElPlazo`, `.fueraDePlazo`, `.noSanitiza` |
| R53 | `AccesoResenasTest.sinRepositorio` (8 clases), `.conDao` (4 clases); `ResenaDAOJpaTest.anonimizaConTextoFijo`; `ResenaDAOJpaIT.delUsuarioYAnonimizar`, `.busquedasDeModeracion` |
| R54 | `ResenaDAOJpaTest.paginaConOrdenYFiltro`, `.sinMas`; `ResenaDAOJpaIT.dosPaginasSoloVisibles` |
| R55 | `ResenaDAOJpaTest.offsetEnorme` (2 casos), `.ultimaQueConsulta` |
| R56 | `ResenaDAOJpaTest.resumenSoloVisible`; `ResenaDAOJpaIT.resumenSoloVisibles` |
| R57 | `ResenaDAOJpaTest.guardarConFlush`; `ResenaDAOJpaIT.guardarDuplicadoLanzaDesdeGuardar` |
| R58 | `ContratoResenasTest.rutasDelControlador`, `.camposDeLosDto`, `.mensajes`, `.firmasPublicas` (etiquetadas `HU-24 R58`, agregadas tras el review). Además, las pruebas "Sin cambios" de `design.md` § 14 pasan sin tocarse (`ResenaControllerIT`, `FichaControllerIT`, `FichaServiceTest`, `LimiteFichaIT`, `DatosPersonalesControllerIT`, IT de HU-27, `SanitizadorTextoTest`, pruebas de HU-26) + el `git diff` de la tabla anterior |

R1–R43 siguen cubiertos por el mapa de arriba. Los tests de R1–R24 de `ResenaServiceTest` ahora recorren la cadena real
(`ConfiguracionPublicacionResena.cadena` con dobles), y los de R25–R34 pasan por `ResenaDAO`.

### Pruebas adaptadas (sin cambiar ningún `@DisplayName`)

- `T/busqueda/service/ResenaServiceTest`: `@Mock ResenaDAO`; `preparar` y `fechaEnLima` usan el helper `conReloj(...)`,
  que arma `ConfiguracionPublicacionResena.cadena(...)`. `existsByReservaId` → `existeParaReserva`, `saveAndFlush` →
  `guardar`, `reservasResenadas` → `reservasResenadasPor`, `totales` → `resumenVisibles` (se borró el helper `totales`),
  `findByEspacioIdAndEstado` → `visiblesDeEspacio(ESPACIO_ID, n, 10)` con `TrozoResenas`. Las aserciones sobre
  `Pageable`/orden pasaron a `ResenaDAOJpaTest`; `paginaEnorme` y `ultimaPaginaConsultable` quedaron como "pasa n y 10 al
  DAO" (el tope de offset vive en `ResenaDAOJpaTest`).
- `T/busqueda/service/impl/DatosPersonalesResenasImplTest`: `@Mock ResenaDAO`; `delUsuario`, `anonimizarComentarios`.
- `T/busqueda/service/impl/ModeracionResenasImplTest` (HU-27): `@Mock ResenaDAO`; `buscar`, `buscarVarias(List.of(id))`,
  `buscarParaActualizar`.

### Desvíos del spec

1. **T31 y T36 juntas.** `tasks.md` pedía un paso intermedio en el que `ResenaService` recibía `ResenaRepository` y
   `ResenaDAO` a la vez. Se fue directo al constructor final `(PublicacionResena, ResenaDAO, ReservasParaResena,
   CatalogoEspaciosService, Clock)`; el resultado es el mismo que fija T36.
2. **Detalles menores no fijados por el diseño:** `TrozoResenas` copia la lista (`List.copyOf`) en su constructor
   compacto; `ResenaDAOJpa.MAS_NUEVAS_PRIMERO` es constante de paquete; `PublicacionTransaccional.RESTRICCION_UNA_POR_RESERVA`
   es de paquete; `PublicacionSanitizada` tiene su propia constante `CAMPO_COMENTARIO = "comentario"`.
3. **Pruebas extra** no listadas en § 14: `ResenaDAOJpaTest.guardarConFlush` y `.anonimizaConTextoFijo`;
   `ResenaDAOJpaIT.busquedasDeModeracion`; `PublicacionResenaIT.cadenaRealGuardaYAudita`;
   `ConfiguracionPublicacionResenaTest.beanUsaLaCadena`; `AccesoResenasTest.conDao`.
4. **`docs/informe/`** no existía; se creó con `HU-24.md` (T38).

Ninguna decisión de diseño nueva; contrato HTTP, DTO, mensajes, migraciones y frontend sin cambios.

### Archivos de la reapertura

**Creados, backend:** `J/busqueda/repository/ResenaDAO`, `ResenaDAOJpa`, `TrozoResenas`;
`J/busqueda/service/PublicacionResena`, `SolicitudResena`, `PublicacionResenaDecorador`, `PublicacionResenaBase`,
`PublicacionSanitizada`, `PublicacionTransaccional`, `PublicacionAuditada`, `ConfiguracionPublicacionResena`.

**Creados, pruebas:** `T/busqueda/AccesoResenasTest`, `T/busqueda/ContratoResenasTest` (R58, tras el review); `T/busqueda/repository/ResenaDAOJpaTest`, `ResenaDAOJpaIT`;
`T/busqueda/service/ConfiguracionPublicacionResenaTest`, `PublicacionResenaBaseTest`, `PublicacionSanitizadaTest`,
`PublicacionTransaccionalTest`, `PublicacionAuditadaTest`, `PublicacionResenaIT`.

**Modificados, backend:** `J/busqueda/repository/ResenaRepository` (solo Javadoc), `J/busqueda/service/ResenaService`,
`J/busqueda/service/impl/DatosPersonalesResenasImpl`, `J/busqueda/service/impl/ModeracionResenasImpl`.

**Modificados, pruebas:** `T/busqueda/service/ResenaServiceTest`, `T/busqueda/service/impl/DatosPersonalesResenasImplTest`,
`T/busqueda/service/impl/ModeracionResenasImplTest`.

**Documentación:** `docs/informe/HU-24.md` (nuevo: § 7.7, § 8.1, § 8.1.1, § 8.1.2), `docs/historias/HU-24.md`,
`docs/architecture.md` (fila ★ HU-24 en "Patrones por historia", viñeta de paginación y párrafo ★ en "Reseñas: qué deja
HU-24"), `docs/requerimientos.md` § 7 (fila HU-24 con ★ y `Decorator, DAO`), `specs/HU-24/tasks.md`, `progress/HU-24.md`.
