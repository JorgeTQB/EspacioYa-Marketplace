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
