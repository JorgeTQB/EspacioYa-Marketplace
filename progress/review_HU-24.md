# Review — HU-24

**Veredicto:** APPROVED

Revisado el árbol de trabajo de `feature/HU-24-resenas-calificaciones` (todo sin commitear), el 2026-11-06.

## Verificación ejecutada por el reviewer

| Comprobación | Resultado |
|--------------|-----------|
| `docker info` tras reiniciar Docker | Responde (29.8.0) |
| `./init.sh` completo, 1.ª corrida | **Sin resultado:** salida 4, cortada en la sección 5, sin salida de Maven. Coincidió con la interrupción de la sesión, en la que Docker se apagó. No cuenta como evidencia a favor ni en contra |
| `./init.sh` completo, 2.ª corrida | **Salida 0, `[OK] Entorno listo`**: 1722 unitarias y 1044 IT, 0 fallos, 0 errores, 0 omitidas; ESLint y build OK; colores, arquitectura y secretos OK |
| Clases de HU-24 en `failsafe-reports` de esa corrida | `ResenaControllerIT` 68, `ResenaRepositoryIT` 12, `LimiteFichaIT` 9, `DatosPersonalesControllerIT` 36, sin fallos |

El veredicto se apoya en la 2.ª corrida, no en la evidencia de `progress/HU-24.md`, aunque coincide con ella en las cifras.
Con esa corrida la migración `V7_2`, `ddl-auto=validate`, las consultas JPQL y la carrera de R14 (5 rondas) quedaron ejecutadas
contra PostgreSQL real.

**No verificado:** las pantallas no se abrieron en un navegador. Se revisaron leyendo el código, con lint y build. Lo visible
queda para la caja negra de UAT (`docs/verification.md`, Nivel 3).

## Trazabilidad requirements ↔ tests

Los 43 `R<n>` tienen al menos un test que comprueba un resultado concreto. El mapa de `progress/impl_HU-24.md` coincide con el
código; la columna "IT, sin ejecutar" de ese informe está desactualizada. Abreviaturas: `RSTest` = `ResenaServiceTest`,
`RCIT` = `ResenaControllerIT`, `RRIT` = `ResenaRepositoryIT`, `DPIT` = `DatosPersonalesControllerIT`.

- R1: [x] `RSTest.creaLaResena`, `.espacioNoVisible`; `RCIT.identidadDelServidor`, `.espacioNoVisible`
- R2: [x] `RSTest.fechaEnLima`; `RCIT.creaYRespondeCincoCampos`
- R3: [x] `RSTest.identidadDelServidor`; `CrearResenaDTOTest.camposExtraDescartados`; `RCIT.identidadDelServidor`
- R4: [x] `ReservasParaResenaImplTest.finEnLima`, `.finConHora24`; `RCIT.finConBloquesInactivos`
- R5: [x] `ReglasResenaTest.finExacto`, `.unInstanteAntesDeVencer`; `RCIT.enElInstanteDelFin`, `.dentroDelPlazo`
- R6: [x] `ReglasResenaTest.noConfirmada`; `RSTest.noConfirmada`; `RCIT.estadosNoConfirmados`
- R7: [x] `ReglasResenaTest.unInstanteAntesDelFin`; `RSTest.sinTerminar`; `RCIT.sinTerminar`
- R8: [x] `ReglasResenaTest.venceExacto`; `RSTest.plazoVencido`; `RCIT.plazoVencido`
- R9: [x] `RSTest.ajena`; `ReservasParaResenaImplTest.ajenaOInexistente`; `RCIT.ajenaEInexistente`
- R10: [x] `RSTest.otroRol`; `RCIT.otroRol`
- R11: [x] `RCIT.sinSesion`
- R12: [x] `RSTest.yaResenada`, `.yaResenadaConPlazoVencido`; `RCIT.dobleResena`, `.yaResenadaOcultaOVencida`
- R13: [x] `RRIT.guardaYLee`, `.unaPorReserva`
- R14: [x] `RSTest.carreraContraElIndice`, `.otraViolacionSeRelanza`; `RCIT.dosEnviosALaVez`
- R15: [x] `CrearResenaDTOTest.reservaInvalida`; `RCIT.reservaInvalida`
- R16: [x] `CrearResenaDTOTest.calificacionInvalida`, `.calificacionValida`; `RCIT.calificacionInvalida`
- R17: [x] `CrearResenaDTOTest.comentarioInvalido`, `.largoDelComentario`; `RCIT.comentarioInvalido`, `.largoDelComentario`
- R18: [x] `RRIT.calificacionFueraDeRango`
- R19: [x] `RRIT.estadoInvalido`
- R20: [x] `RRIT.reservaConResena`, `.espacioYUsuarioConResena`
- R21: [x] `SanitizadorTextoTest` (9 pruebas); `RSTest.comentarioSanitizado`; `RCIT.guardaSanitizado`
- R22: [x] `SanitizadorTextoTest.quedaVacio`; `RSTest.comentarioQuedaVacio`; `RCIT.quedaVacio`, `.quedaVacioAntesDeLeerLaReserva`
- R23: [x] `RSTest.auditaLaCreacion`; `RCIT.auditaLaCreacion`
- R24: [x] `RSTest.rechazoSinAuditoria`; `RCIT.rechazosSinAuditoria`
- R25: [x] `RSTest.pendientes`, `.sinPendientes`; `RCIT.pendientesDelUsuario`, `.saleDePendientes`
- R26: [x] `RSTest.pendientesConOtroRol`; `RCIT.pendientesConOtroRol`
- R27: [x] `FichaServiceTest.fichaConResumen`; `RCIT.fichaConPromedio`
- R28: [x] `ResumenCalificacionesTest.medioExactoRedondeaHaciaArriba`, `.medioQueUnDoubleRedondeariaMal`, `.unDecimal`
- R29: [x] `ResumenCalificacionesTest.sinResenas`; `FichaServiceTest.fichaSinResenas`; `RCIT.fichaSinResenas`; `RRIT.totalesSinResenas`
- R30: [x] `RSTest.primeraPagina`, `.paginaPedida`; `RCIT.dosPaginas`, `.diezExactas`
- R31: [x] `RSTest.primeraPagina`; `RCIT.cuatroClaves`
- R32: [x] `RCIT.publica`
- R33: [x] `RSTest.idMalEscrito`, `.espacioInvisible`; `RCIT.espacioInvisible`, `.arrendadorSuspendido`, `.idMalo`
- R34: [x] `RSTest.paginaInvalida`, `.paginaEnorme`, `.ultimaPaginaConsultable`; `RCIT.paginaInvalida`, `.paginaEnorme`
- R35: [x] `RCIT.ocultaFueraDeTodo`; `RRIT.totalesSoloVisibles`, `.paginaSoloVisibles`
- R36: [x] `RCIT.apareceAlCrearla`
- R37: [x] `ReglasLimiteFichaTest.reglaDeLasResenas`, `.resenasCompartenElCupo`; `LimiteFichaIT.resenasCompartenElCupo`, `.resenasInvalidasCuentan`
- R38: [x] `LimiteFichaIT.resenasConCupoAgotado` (ver observación 2)
- R39: [x] `DatosPersonalesResenasImplTest.exportaLasResenas`, `.seccionVacia`, `.vaDespuesDePagos`; `DPIT.exportaLasResenasDelUsuario`, `.seccionDeResenasVacia`
- R40: [x] `DatosPersonalesResenasImplTest.arrendadorSinSeccion`; `DPIT.arrendadorSinSeccionDeResenas`
- R41: [x] `DatosPersonalesResenasImplTest.sinImpedimentos`; `DPIT.resenaNoImpideEliminar`
- R42: [x] `DatosPersonalesResenasImplTest.anonimiza`; `DPIT.eliminarAnonimizaLasResenas`; `RRIT.reemplazaSoloLasDelUsuario`
- R43: [x] `DPIT.eliminarAnonimizaLasResenas`

## Trazabilidad con la historia

- CA1: [x] R1, R2, R4, R5, R25, R27, R28, R29, R30, R36
- CA2: [x] R6, R7, R8, R9, R10, R11
- CA3: [x] R12, R13, R14, R25
- (S/T) Una reseña por reserva, validada contra reservas reales: [x] R1, R3, R9, R12, R13, R14. `ReservasParaResenaImpl` solo lee con `ReservaDAO.delUsuario`; `uq_resena_reserva` en `V7_2__resena.sql:11`
- (T) Texto sanitizado: [x] R17, R21, R22. `SanitizadorTexto` al guardar; ninguna página usa `dangerouslySetInnerHTML`
- (R) Registro de creación: [x] R23, R24. `AuditoriaService.registrar` es `@Transactional` por defecto y se une a la transacción de `crear`

## Decisiones P1–P18

- P1: [x] `ReglasResena.java:20-28`, intervalo semiabierto sobre `instanteDeFin`
- P2: [x] `POST /api/resenas`; 404, 400 y 403 según lo acordado
- P3: [x] `CrearResenaDTO`, obligatorio, hasta 500
- P4: [x] `SanitizadorTexto`, sin escapar a entidades (ver observación 1)
- P5: [x] `GET /api/resenas/pendientes`; `ReservaDTO` sin cambios
- P6: [x] `FichaPublicaDTO` con 2 campos, ruta paginada de 10, `HALF_UP` con enteros
- P7: [x] `ResultadoEspacioDTO` sin cambios; `BusquedaService` solo cambia la visibilidad de `pagina`
- P8: [x] `ResenaPublicaDTO` con 4 campos; `RCIT.cuatroClaves`
- P9: [x] `crear` no consulta el catálogo; IT con `PAUSADO`, `BAJA` y `OCULTO`; el arrendador suspendido queda cubierto por `RSTest.espacioNoVisible` (`verifyNoInteractions(catalogo)`)
- P10: [x] `busqueda` no importa `pagos`
- P11: [x] `DatosPersonalesResenasImpl`, `@Order(3)`
- P12: [x] único sin filtrar por estado; `RCIT.yaResenadaOcultaOVencida`
- P13: [x] sin límite propio; regla `catalogo` en `ReglasLimiteFicha`
- P14: [x] `ReservaDTO` no se amplió; `architecture.md` deja la nota solo para HU-26
- P15: [x] `busqueda` importa de `cuentas` solo `service.puertos`; ningún módulo importa `busqueda`
- P16: [x] mandan `feature_list.json` y `docs/historias/HU-24.md`; `specs/HU-14/` no se tocó
- P17: [x] `modelo-datos.md` actualizado
- P18: [x] `FichaPublicaDTO` queda con 12 campos

## Calidad del cambio

- Arquitectura: [x] capas, DTO `record`, identidad desde la sesión, sin puertos nuevos, sin dependencias circulares.
- Convenciones: [x] nombres, constructor, `V7_2` libre en `origin/develop` (última `V7_1`), `ErrorDTO` genérico. Las líneas de más de 120 caracteres en pruebas son anteriores a HU-24.
- Frontend: [x] solo tokens existentes, sin estilos en línea, kit reutilizado, llamadas solo en `api/busqueda.js`, un solo primario por pantalla. Coincide con `design.md` § 11. `Campo` entrega el valor y ofrece la opción vacía, así que "un `select` vacío viaja como `null`" se cumple.
- Desviación de `design.md` justificada: `deEspacio` responde vacío si el offset no cabe en un `int` (`ResenaService.java:121`), documentada en `impl_HU-24.md` y `architecture.md`, y probada con `RCIT.paginaEnorme`. No cambia ningún `R<n>`.
- Documentos de `design.md` § 16: [x] los cuatro actualizados.
- Patrón ★: n/a, HU-24 no tiene `patterns`.

## Tasks completas

- T1–T28: [x]
- T29: [ ] en `tasks.md`, **justificada y cumplida en el fondo**. `./init.sh` completo salió 0 en la corrida del reviewer y el mapa R1–R43 → test está en `progress/impl_HU-24.md`. El orquestador la marca al cerrar. Lo que dice `impl_HU-24.md` sobre T29 está desactualizado (ver observación 3).

## Checkpoints

- C1: [x] `./init.sh` completo, salida 0
- C2: [x] una sola `in_progress` por dueño; aprobación de Ariel Enriquez anotada en `progress/HU-24.md`. "Historia `done` con tests": n/a hasta el cierre
- C3: [x] todos los puntos, incluidos migraciones, alcance y frontend
- C4: [x] `*Test`/`*IT`, PostgreSQL real, `Clock` fijo, concurrencia simultánea, resultados concretos, `verify`, lint y build
- C5: [x] sin archivos sospechosos, rama `feature/HU-24-…`, `in_progress` correcto. n/a hasta el cierre: entrada de cierre en `history.md` y `progress/HU-24.md` de vuelta a la plantilla
- C6: [x] tres archivos del spec, EARS, tabla de trazabilidad, `R<n>` con test, §9 sin pendientes resueltos sin el dueño. "Historia `done` con tasks `[x]`": n/a hasta marcar T29

## Cambios requeridos

Ninguno.

## Observaciones que no bloquean

1. **Decisión del dueño, sanitización.** `2 < 4 y 5 > 3` se guarda como `2  3`: `< 4 y 5 >` parece una etiqueta y se borra. Cumple P4, DT-8 y R21, pero se pierde texto del usuario sin aviso. Si Ariel no lo acepta, es un cambio de spec.
2. **R38, prueba indirecta.** `resenasConCupoAgotado` comprueba el 429 y que no salen datos, no cuenta consultas. Basta porque el límite se aplica en un filtro, antes del controlador.
3. **Al cerrar:** marcar T29 y actualizar `progress/impl_HU-24.md`, que aún dice "la historia no está verificada" e "IT sin ejecutar".
4. **Corridas cortadas sin causa conocida.** Ya son dos: la que anotó el orquestador y la 1.ª de esta revisión, ambas en las pruebas del backend y con Docker apagado. Esta última coincidió con la interrupción de la sesión. No hay fallo de prueba asociado, pero la causa sigue sin determinarse; `ResenaControllerIT` suma un contexto y un contenedor propios, y Docker tiene unos 7.9 GB de memoria.
5. **Parpadeo posible, sin observar.** Tras publicar, "Calificar" puede verse un instante si "Pasadas" recarga antes que los pendientes (`MisReservas.jsx:65-77`). El servidor respondería *ya reseñada*.
6. **Migración.** Si HU-25 llega antes a `develop` con `V7_2`, renombrar antes del merge (`docs/conventions.md`).

---

# Review — HU-24 · Reapertura 2026-11-13 (★ Decorator, DAO)

**Veredicto:** CHANGES_REQUESTED

Revisado el árbol de trabajo de `feature/HU-24-resenas-calificaciones` (sin commitear), contra `develop` 415f9a9.

Hay un solo motivo de rechazo: **R58 no tiene ninguna prueba con `@DisplayName("HU-24 R58: …")`**. El propio `./init.sh` lo
avisa (`[WARN] HU-24: sin @DisplayName("HU-24 R<n>: ...") para R58`, sección 3), y `reviewer.md` exige al menos un test
etiquetado por cada `R<n>` (paso 4 y regla dura). Todo lo demás está bien; ver abajo.

## Verificación ejecutada por el reviewer

| Comprobación | Resultado |
|--------------|-----------|
| `./init.sh` completo (sin `--rapido`) | **Salida 0, `[OK] Entorno listo`**: 134 unitarias de las 11 clases tocadas y **1238 IT**, 0 fallos, 0 errores, 0 omitidas; arquitectura, colores y secretos OK; ESLint y build OK. Avisos: R58 sin `@DisplayName` y faltan las llaves de cifrado y firma (no afectan a las pruebas) |
| `git diff develop --stat` sobre `2_Backend/src/main/resources`, `busqueda/dto`, `busqueda/controller`, `comun`, `3_frontend` y las pruebas "Sin cambios" de `design.md` § 14 (`T/busqueda/controller`, `T/cuentas`, `T/administracion`, `T/comun`, `ResenaRepositoryIT`) | **Vacío.** `git status` tampoco muestra archivos nuevos en esas rutas |
| `ResenaRepository` en `src/main` | Solo lo usa `ResenaDAOJpa.java:26-28`; en `src/test` solo `ResenaRepositoryIT`, `ResenaDAOJpaTest` y `AccesoResenasTest` |

## Patrones ★ (design.md § 12)

**Decorator (GoF).** Los roles existen y coinciden con § 12.1:

- Component `PublicacionResena` (interfaz, `publicar(SolicitudResena)` devuelve `Resena`).
- ConcreteComponent `PublicacionResenaBase`: `delUsuario` → `existeParaReserva` → `ReglasResena` → `guardar`
  (`PublicacionResenaBase.java:31-43`). No sanitiza, no abre transacción, no audita.
- Decorator `PublicacionResenaDecorador` (abstracta, `envuelta` privada y no nula, `envuelta()` de paquete).
- ConcreteDecorators: `PublicacionSanitizada` actúa antes (`:22-28`), `PublicacionTransaccional` alrededor, con
  `TransactionTemplate` y el `catch` de DT-6 (`:26-35`), y `PublicacionAuditada` después (`:24-31`). Los tres llaman a
  `super.publicar`.
- Ensamblador `ConfiguracionPublicacionResena.cadena` (`:30-37`), con el orden Sanitizada → Transaccional → Auditada → Base y
  un único `@Bean`.
- Client `ResenaService.crear` (`:64-70`): comprueba el rol y llama a `publicacion.publicar`. Ya no tiene campos
  `AuditoriaService` ni `TransactionTemplate`.

**DAO.** La interfaz pública `ResenaDAO` no expone `Pageable`, `Slice` ni proyecciones. `ResenaDAOJpa` es `@Repository` de
paquete y solo depende de `ResenaRepository`, como `ReservaDAOJpa`. El tope de offset pasó del servicio al DAO
(`ResenaDAOJpa.java:49-53`, DT-26). Los TransferObject son `TrozoResenas`, `ResumenCalificaciones` y `Resena`, y los cuatro
BusinessObject usan solo `ResenaDAO`.

**Comportamiento equivalente.** Comparé con `git diff develop` el código que se borró de `ResenaService` (`crear`,
`crearEnTransaccion`, `auditar`, `esReservaYaResenada`) con lo que ahora hacen los decoradores y la base. El orden (DT-16), los
mensajes, el detalle de auditoría y la traducción por nombre de la restricción son idénticos. La única diferencia es que el
DTO ahora se arma fuera de la transacción, a partir de la entidad devuelta, y solo lee campos simples.

## Migración de HU-22 y HU-27 al DAO

- `DatosPersonalesResenasImpl`: `findByUsuarioIdOrderByCreadoEnAscIdAsc` → `delUsuario`, que es esa misma consulta
  (`ResenaDAOJpa.java:65-67`), y `reemplazarComentarios(id, Resena.COMENTARIO_ELIMINADO)` → `anonimizarComentarios`, que pasa
  la misma constante (`:70-72`). Se mantienen `MANDATORY` y el `@Order`.
- `ModeracionResenasImpl` (HU-27): `findById` → `buscar`, `findAllById(Set.copyOf(ids))` → `buscarVarias`, que hace el mismo
  `Set.copyOf` (`:80-82`), y `findParaActualizar` → `buscarParaActualizar`, con el mismo bloqueo. No cambian las anotaciones,
  los mensajes ni la interfaz `ModeracionResenas`.
- En los tests solo cambiaron los dobles: ningún `@DisplayName` ni ninguna aserción de comportamiento. El texto fijo de la
  anonimización pasó a verificarse en `ResenaDAOJpaTest.anonimizaConTextoFijo` y `ResenaDAOJpaIT.delUsuarioYAnonimizar`.
  `DatosPersonalesControllerIT` y las IT de HU-27 (`Reporte*IT`) no se tocaron y siguen en verde.

## Trazabilidad requirements ↔ tests

R1–R43 siguen cubiertos por las mismas pruebas de la revisión anterior. Los `@DisplayName` no cambiaron y no se borró ningún
test (`git diff develop -- 2_Backend/src/test`). Los tests de R1–R24 de `ResenaServiceTest` ahora recorren la cadena real
(`conReloj` → `ConfiguracionPublicacionResena.cadena`). Las IT de contrato (`ResenaControllerIT`, `FichaControllerIT`,
`LimiteFichaIT`, `DatosPersonalesControllerIT`) no se tocaron y pasan.

- R44: [x] `ConfiguracionPublicacionResenaTest.ordenDeLaCadena`, `.beanUsaLaCadena`, `.clientSoloConoceElComponent`; `PublicacionResenaIT.cadenaRealGuardaYAudita`
- R45: [x] `PublicacionSanitizadaTest.pasaElComentarioLimpio` (verifica la solicitud exacta reenviada y `isSameAs` del resultado)
- R46: [x] `PublicacionSanitizadaTest.vacioNoInvocaLaEnvuelta` (campo `comentario`, mensaje, `verifyNoInteractions`)
- R47: [x] `PublicacionTransaccionalTest.confirmaSiVuelve`, `.revierteSiLanza` (`commit` y `rollback` sobre el gestor simulado)
- R48: [x] `PublicacionTransaccionalTest.traduceLaCarrera`, `.otraViolacionIgual`, `.otraExcepcionIgual`
- R49: [x] `PublicacionAuditadaTest.registraDespues` (`InOrder`, `EventoAuditoria` exacto sin comentario)
- R50: [x] `PublicacionAuditadaTest.noRegistraSiLanza`
- R51: [x] `PublicacionResenaIT.auditoriaFallaSinFila` (contexto completo, PostgreSQL, `@MockitoSpyBean` que lanza, 0 filas)
- R52: [x] `PublicacionResenaBaseTest.ordenDeLaRegla`, `.ajenaSinDao`, `.yaResenadaAntesQueElPlazo`, `.fueraDePlazo`, `.noSanitiza`
- R53: [x] `AccesoResenasTest.sinRepositorio` (8 clases), `.conDao` (4); `ResenaDAOJpaTest.anonimizaConTextoFijo`; `ResenaDAOJpaIT.delUsuarioYAnonimizar`, `.busquedasDeModeracion`
- R54: [x] `ResenaDAOJpaTest.paginaConOrdenYFiltro`, `.sinMas`; `ResenaDAOJpaIT.dosPaginasSoloVisibles`
- R55: [x] `ResenaDAOJpaTest.offsetEnorme` (2 casos, `verifyNoInteractions`), `.ultimaQueConsulta`
- R56: [x] `ResenaDAOJpaTest.resumenSoloVisible`; `ResenaDAOJpaIT.resumenSoloVisibles`
- R57: [x] `ResenaDAOJpaTest.guardarConFlush`; `ResenaDAOJpaIT.guardarDuplicadoLanzaDesdeGuardar` (lanza dentro del callback, con la transacción aún sin completar)
- R58: [ ]  ← **Ningún test lleva `@DisplayName("HU-24 R58: …")`** (`./init.sh`, sección 3, lo marca como WARN). La evidencia de hoy es indirecta: las suites de contrato sin tocar y el `git diff`, que también comprobé yo. Pero el mapa de `progress/impl_HU-24.md` remite a pruebas etiquetadas con otros `R<n>` y a un comando de git, y eso no cumple `docs/specs.md:155` ni el paso 4 de `reviewer.md`

## Trazabilidad con la historia

- CA1: [x] R1, R2, R4, R5, R25, R27–R30, R36
- CA2: [x] R6–R11
- CA3: [x] R12, R13, R14, R25
- (S/T) Una reseña por reserva: [x] R1, R3, R9, R12–R14, R48, R52, R57. Implementado en `PublicacionResenaBase` y `PublicacionTransaccional`, sobre `uq_resena_reserva`
- (T) Texto sanitizado: [x] R17, R21, R22, R45, R46. Implementado en `PublicacionSanitizada`, fuera de la transacción
- (R) Registro de creación: [x] R23, R24, R49–R51. Implementado en `PublicacionAuditada`, dentro de la transacción (comprobado con `PublicacionResenaIT`)
- ★ Decorator: [x] R44–R52. ★ DAO: [x] R53–R57. Reapertura sin cambiar el contrato: R58, implementado (diff vacío) pero **sin test etiquetado**

## Tasks completas

- T1–T29: [x]
- T30–T40: [x]. T31 se hizo junto con T36, sin el paso intermedio de los dos accesos. Está justificado en `progress/impl_HU-24.md`, "Desvíos del spec" 1, y el resultado es el que fija T36

## Documentación ★

- `docs/informe/HU-24.md`: [x] § 7.7 (los dos diagramas de clases con roles y las tablas rol → clase), § 8.1 (`ESC-24`,
  HU-24, "Reseñar una reserva pasada", Usuario, Release 2 / Sprint 7, justificación), § 8.1.1 (secuencia con el flujo
  principal y los alternos ya reseñada, fuera de plazo y carrera, más su explicación) y § 8.1.2 (identificación, código,
  tabla mensaje → clase.método, explicación y archivos). Comparé uno por uno los fragmentos de `ResenaController.crear`,
  `ResenaService.crear`, `ConfiguracionPublicacionResena.cadena`, `PublicacionResenaDecorador.publicar`,
  `PublicacionSanitizada.publicar`, `PublicacionTransaccional.publicar` y `esReservaYaResenada`,
  `PublicacionAuditada.publicar`, `PublicacionResenaBase.publicar` y `ResenaDAOJpa.existeParaReserva` y `guardar`:
  **coinciden con el código**, salvo la indentación de la clase.
- `docs/historias/HU-24.md`: [x] línea "Se documenta a fondo (capítulo 8). Patrones: Decorator, DAO."
- `docs/architecture.md`: [x] fila ★ HU-24 en "Patrones por historia", viñeta de paginación (ahora en `ResenaDAOJpa`) y
  párrafo ★ en "Reseñas: qué deja HU-24".
- `docs/requerimientos.md` § 7: [x] `HU-24 ★` con `Decorator, DAO`.
- `feature_list.json`: [x] `patterns` con Decorator y DAO, status `in_progress`.

## Checkpoints

- C1: [x] arnés completo; `./init.sh` completo salió 0
- C2: [x] una sola `in_progress` por dueño; la aprobación de Ariel Enriquez de la reapertura está en `progress/HU-24.md:36`. "Historia `done` con tests": n/a (sigue `in_progress`)
- C3: [x] módulo `busqueda`, sin puertos nuevos, `ResenaDAOJpa` de paquete, inyección por constructor, sin comodines ni prints, DTO sin cambios, identidad desde la sesión (`SolicitudResena.usuarioId` sale de `UsuarioAutenticado`), sin migraciones nuevas ni editadas, frontend sin cambios. `@Configuration` en `service` tiene precedente (`reservas/service/ProgramacionExpiracion`)
- C4: [x] `*Test` con Mockito y `*IT` con Testcontainers, PostgreSQL real, `Clock` fijo en las unitarias, carrera de R14 en `ResenaControllerIT` sin tocar, resultados concretos, `verify`, lint y build en verde
- C5: [x] rama `feature/HU-24-…`, sin archivos sospechosos (los no trackeados son las clases, las pruebas y `docs/informe/` de la reapertura). n/a hasta el cierre: `history.md` y la plantilla de `progress/HU-24.md`
- C6: [ ] spec completo, EARS, trazabilidad y § 12 con los patrones. **Falla** "Cada `R<n>` cubierto por al menos un test concreto": R58 sin test etiquetado. "Historia `done` con tasks `[x]`": n/a

## Cambios requeridos

1. **Agregar al menos una prueba con `@DisplayName("HU-24 R58: …")`** que compruebe un resultado concreto del contrato
   después de la refactorización, sin modificar las suites "Sin cambios" de `design.md` § 14. Por ejemplo, en
   `PublicacionResenaIT` o en una IT nueva de `T/busqueda/`, contra la cadena real:
   - `POST /api/resenas` reseñable: 201 con exactamente las claves `id`, `reservaId`, `calificacion`, `comentario` y `fecha`;
   - un segundo envío de la misma reserva: 400 con el mensaje `ResenaService.MENSAJE_YA_RESENADA`;
   - un comentario que queda vacío: 400 en el campo `comentario` con `CrearResenaDTO.M_COMENTARIO`.

   Con eso desaparece el WARN de `./init.sh`, sección 3.
2. Agregar esa prueba a la fila R58 de `progress/impl_HU-24.md` y volver a correr `./init.sh` completo.

## Observaciones que no bloquean

1. `docs/informe/HU-24.md:273`: la "Salida" dice "fuera de plazo", pero el mensaje real es el de plazo vencido
   (`ResenaService.MENSAJE_PLAZO_VENCIDO`). Conviene alinear el término con `requirements.md` (Definiciones).
2. `PublicacionResenaIT` usa `@MockitoSpyBean`, que crea un contexto de Spring aparte: suma tiempo a `verify`, igual que
   `ResenaControllerIT`.
3. `ResenaServiceTest.paginaEnorme` y `.ultimaPaginaConsultable` (R34) ahora solo comprueban que el servicio pasa `n` y 10
   al DAO. El tope real quedó en `ResenaDAOJpaTest` (R55) y en `ResenaControllerIT.paginaEnorme`, sin tocar. Es correcto
   según § 14.

## Re-revisión 2026-11-13 (tras los cambios requeridos) — Veredicto final de la reapertura: APPROVED

Esta subsección reemplaza el veredicto CHANGES_REQUESTED de arriba. Lo que no se menciona aquí sigue igual.

**Cambio 1, prueba de R58: [x].** Nuevo `2_Backend/src/test/java/pe/edu/ulima/espacioya/busqueda/ContratoResenasTest.java`,
con 4 pruebas `@DisplayName("HU-24 R58: …")` que comprueban valores concretos:

- `rutasDelControlador`: `@PostMapping("/api/resenas")` con `@ResponseStatus(CREATED)` y retorno `ResenaCreadaDTO`;
  `/api/resenas/pendientes` con `ResenasPendientesDTO`; `/api/espacios/{id}/resenas` con `PaginaResenasDTO`.
- `camposDeLosDto`: nombre, tipo y orden de los componentes de `CrearResenaDTO`, `ResenaCreadaDTO`, `ResenasPendientesDTO`,
  `ResenaPublicaDTO`, `PaginaResenasDTO` y `ResenaExportadaDTO` (HU-22), y de los dos últimos campos de `FichaPublicaDTO`.
- `mensajes`: los textos literales de los mensajes de `ResenaService` y `CrearResenaDTO`, `Resena.COMENTARIO_ELIMINADO`,
  `RESENA_CREADA` y el tamaño de página 10.
- `firmasPublicas`: firmas de `ResenaService.crear`/`pendientes`/`deEspacio`, de `ModeracionResenas`
  (`reportable`, `describir`, `ocultar`; HU-27) y `SECCION_RESENAS` (HU-22).

Cubre rutas, DTO, mensajes y firmas de HU-22 y HU-27, que son la parte de R58 que no fijaban las suites existentes. Los
códigos HTTP de error y el esquema siguen respaldados por `ResenaControllerIT` y `ResenaRepositoryIT`, que no se tocaron.

**Pruebas "Sin cambios" de `design.md` § 14:** [x]. `git diff develop --stat` sigue vacío en `T/busqueda/controller`,
`T/cuentas`, `T/administracion`, `T/comun`, `ResenaRepositoryIT`, `2_Backend/src/main/resources`, `busqueda/dto`,
`busqueda/controller`, `comun` y `3_frontend`. Respecto de la revisión anterior, el único archivo nuevo es
`ContratoResenasTest.java`, y el código de `src/main` no cambió.

**Cambio 2, `progress/impl_HU-24.md`:** [x]. La fila R58 (línea 197) lista las 4 pruebas, y la línea 172 registra la nueva
corrida de `./init.sh`.

**`./init.sh` completo, corrido de nuevo por el reviewer:** salida 0, `[OK] Entorno listo`. **Ya no aparece el WARN de R58**
en la sección 3. Pasaron 138 unitarias de las 12 clases tocadas (incluida `ContratoResenasTest`) y **1238 IT**, con 0 fallos,
0 errores y 0 omitidas. Arquitectura, colores, secretos, ESLint y build, OK.

### Actualizaciones

- R58: [x] `ContratoResenasTest.rutasDelControlador`, `.camposDeLosDto`, `.mensajes`, `.firmasPublicas`; además, las suites
  de contrato sin tocar y el `git diff` vacío.
- Reapertura sin cambiar el contrato: [x] R58.
- C6: [x]. Todos los `R<n>` (R1–R58) tienen al menos un test etiquetado que comprueba un resultado concreto.
- Todos los demás checkpoints quedan como arriba (C1–C5 [x]; los puntos de cierre, n/a).

### Cambios requeridos

Ninguno.

### Observaciones que no bloquean

Siguen vigentes las 3 de arriba, en especial "fuera de plazo" frente a "plazo vencido" en `docs/informe/HU-24.md:273`.
Además, `ContratoResenasTest` es una prueba por reflexión: fija la forma del contrato, no su comportamiento en tiempo de
ejecución, que siguen cubriendo las IT sin cambios.
