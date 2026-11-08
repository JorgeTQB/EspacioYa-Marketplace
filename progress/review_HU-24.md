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
