# Implementación — HU-08 · Disponibilidad del espacio

> Implementer, 2026-10-28. Rama `feature/HU-08-disponibilidad-espacio`. Spec aprobado por Sebastian Garate el
> 2026-10-28 (P1–P12 y DT-1…DT-14 con sus valores por defecto). Sin commit, push ni PR. Status sigue `in_progress`.

## Resumen

- **Tasks:** 15/15 marcadas `[x]` en `specs/HU-08/tasks.md`.
- **`./init.sh` (completo):** verde, exit 0. Backend: 385 pruebas unitarias y 262 IT, sin fallos (`./mvnw verify`
  con Docker). ESLint sin advertencias, `npm run build` OK, sin colores sueltos ni secretos. El validador de
  `feature_list.json` no avisa de ningún `R<n>` de HU-08 sin `@DisplayName`.
- **Antes de la migración:** se revisó `origin/develop`: `V2_4` estaba libre y el kit no traía `SelectorBloques`.

## Qué se hizo, por task

| Task | Resultado |
|---|---|
| T1 | `comun/configuracion/ZonaHoraria` (`LIMA`, `hoy`, `ahora` con `reloj.instant()`). `ZonaHorariaTest`. |
| T2 | `V2_4__disponibilidad.sql`, igual al SQL de design § 9 (CHECK de día y horas, UNIQUE, `ON DELETE CASCADE`). |
| T3 | Dominio: `FranjaHoraria`, `ReglasHorario`, `VentanaReserva`, `EstadoBloque`, `Bloque`, `CalculoDisponibilidad`, entidades `HorarioSemanal` (campos `short`) y `BloqueoFecha`. Cuatro tests de dominio. |
| T4 | `HorarioSemanalRepository` (con `borrarDelEspacio` JPQL `@Modifying(flushAutomatically, clearAutomatically)`) y `BloqueoFechaRepository`. |
| T5 | Puerto `service/puertos/ReservasDelEspacio` + `BloqueReservado`, javadoc con el contrato de bloqueo de DT-10. |
| T6 | `DisponibilidadService` (interfaz pública) + `DisponibilidadServiceImpl` (`ObjectProvider<ReservasDelEspacio>`; `ventana`, `reservasDelDia`, `bloquesDelDia` de paquete). `DisponibilidadServiceImplTest` + `DisponibilidadServiceImplIT`. |
| T7 | `HorarioDTO`, `FranjaDTO` (enteros estrictos con `FichaEspacioDTO.SoloEntero`), `BloqueoFechaDTO`, `BloqueosDTO`, `DisponibilidadDiaDTO`, `BloqueDTO`. `HorarioDTOTest`, `BloqueoFechaDTOTest`. |
| T8 | `CalendarioEspacioService` (6 operaciones, mensajes de design § 4, auditoría DT-11). `CalendarioEspacioServiceTest` (22 casos). |
| T9 | `CalendarioEspacioController` (6 endpoints). En `EspaciosControllerSoporte`: bean `ReservasDelEspacioFalso` en `Dobles` y `reservasFalsas.limpiar()` en `prepararBase()`. `CalendarioEspacioControllerIT` (36 casos, IP `10.8.x.x`, fechas relativas a `ZonaHoraria.hoy(reloj)`, R40 con `CountDownLatch` + `Executors`). |
| T10 | `Campo` con `min`/`max` en la rama genérica; `SelectorBloques` nuevo (+ `.module.css`, tokens `--bloque-*`, modo solo lectura sin `onCambiar`). |
| T11 | `api/espacios.js`: `obtenerHorario`, `guardarHorario`, `listarBloqueos`, `bloquearFecha`, `desbloquearFecha`, `consultarDisponibilidad`. `textosEspacios.js`: `DIAS_SEMANA`, `formatoHora`, `TEXTO_ESTADO_BLOQUE`. |
| T12 | `pages/espacios/DisponibilidadEspacio.jsx` (+ `.module.css`): secciones "Horario semanal", "Fechas bloqueadas" y "Ver un día" según design § 13. |
| T13 | Ruta `/panel/espacios/:id/disponibilidad` en `App.jsx`; enlace "Disponibilidad" en `MisEspacios`; "Definir disponibilidad" en `EditarEspacio`. |
| T14 | `docs/modelo-datos.md` (tipos, CHECK, UNIQUE, recomendación `reserva_bloque.hora_inicio SMALLINT`), `docs/architecture.md` (fila completa del puerto + contrato DT-10, `DisponibilidadService` como interfaz pública, zona `America/Lima`), `docs/frontend.md` (`SelectorBloques` y `Campo` `min`/`max`). |
| T15 | `./init.sh` completo verde; este informe. |

## Trazabilidad R → test

Todas las pruebas llevan `@DisplayName("HU-08 R<n>: …")`. Clases: `T/` = `2_Backend/src/test/java/pe/edu/ulima/espacioya/`.

| R | Tests |
|---|---|
| R1 | `CalendarioEspacioControllerIT.guardaHorario`, `.reemplazaHorario`; `CalendarioEspacioServiceTest.reemplazaHorario`; `FranjaHorariaTest.ordenaPorDiaEInicio`; `HorarioDTOTest.conviertenSinPerderElOrden` |
| R2 | `CalendarioEspacioControllerIT.guardaHorario`, `.reemplazaHorario`; `CalendarioEspacioServiceTest.reemplazaHorario` |
| R3 | `CalendarioEspacioControllerIT.vaciaHorario`; `CalendarioEspacioServiceTest.vaciaElHorario`; `HorarioDTOTest.validaLaLista` |
| R4 | `HorarioDTOTest.validaDia`, `.validaHoraInicio`, `.validaHoraFin`, `.rechazaNulos`, `.rechazaElementoNulo`, `.validaLaLista`; `CalendarioEspacioControllerIT.rechazaHorasQueNoSonEnteras`, `.unErrorPorCampo` |
| R5 | `ReglasHorarioTest.reportaRango`, `.rangoAntesQueSuperposicion`; `CalendarioEspacioControllerIT.rechazaRango`; `CalendarioEspacioServiceTest.rechazaRango` |
| R6 | `ReglasHorarioTest.reportaSuperpuestaEnElIndiceMayor`, `.aceptaContiguasYOtrosDias`, `.rangoAntesQueSuperposicion`; `FranjaHorariaTest.detectaSuperposicion`; `CalendarioEspacioControllerIT.rechazaSuperpuestas`; `CalendarioEspacioServiceTest.rechazaSuperpuestas` |
| R7 | `CalendarioEspacioControllerIT.rechazaHorasQueNoSonEnteras`, `.rechazaRango`, `.rechazaSuperpuestas`, `.estadoNoEditable`; `CalendarioEspacioServiceTest.rechazaRango`, `.rechazaSuperpuestas` |
| R8 | `CalendarioEspacioControllerIT.consultaHorario`; `CalendarioEspacioServiceTest.consultaHorario` |
| R9 | `CalendarioEspacioControllerIT.baseValidaElHorario` |
| R10 | `CalendarioEspacioControllerIT.bloqueaFecha`; `CalendarioEspacioServiceTest.bloqueaFecha`; `BloqueoFechaDTOTest.aceptaFechaBienEscrita` |
| R11 | `CalendarioEspacioControllerIT.bloqueaFecha`; `CalendarioEspacioServiceTest.bloqueaFecha` |
| R12 | `CalendarioEspacioControllerIT.rechazaFechaInvalida`; `CalendarioEspacioServiceTest.rechazaFechaInexistente`, `.disponibilidadSinFecha`; `BloqueoFechaDTOTest.rechazaFechaMalEscrita` |
| R13 | `CalendarioEspacioControllerIT.soloDentroDeLaVentana`; `VentanaReservaTest.vaDeHoyAHoyMas55`, `.contieneSoloSusFechas`; `CalendarioEspacioServiceTest.ventanaAntesQueYaBloqueada` |
| R14 | `CalendarioEspacioControllerIT.yaBloqueada`; `CalendarioEspacioServiceTest.yaBloqueadaAntesQueReservas` |
| R15 | `CalendarioEspacioControllerIT.listaBloqueos`; `CalendarioEspacioServiceTest.listaBloqueos` |
| R16 | `CalendarioEspacioControllerIT.desbloqueaFecha`; `CalendarioEspacioServiceTest.desbloqueaFecha` |
| R17 | `CalendarioEspacioControllerIT.desbloquearNoBloqueada`; `CalendarioEspacioServiceTest.desbloquearNoBloqueada` |
| R18 | `CalendarioEspacioControllerIT.baseImpideBloqueoDuplicado` |
| R19 | `CalendarioEspacioControllerIT.reservaPagadaImpideBloquear`; `CalendarioEspacioServiceTest.reservaPagadaImpideBloquear` |
| R20 | `CalendarioEspacioControllerIT.reservaPendienteImpideBloquear`; `CalendarioEspacioServiceTest.reservaPendienteImpideBloquear` |
| R21 | `DisponibilidadServiceImplTest.sinPuertoNoHayReservas`; `DisponibilidadServiceImplIT.horasLibresContraLaBase` (contexto real sin implementación del puerto) |
| R22 | `CalculoDisponibilidadTest.unBloquePorHora`, `.ignoraOtrosDias`; `FranjaHorariaTest.contieneSusHoras`; `CalendarioEspacioControllerIT.bloquesDelDia` |
| R23 | `CalculoDisponibilidadTest.bloqueadaGanaATodo`; `CalendarioEspacioControllerIT.bloqueadaEnElDia`; `DisponibilidadServiceImplTest.bloqueadaNoConsultaPuerto` |
| R24 | `CalculoDisponibilidadTest.reservadaEsReservado`; `CalendarioEspacioControllerIT.reservadoEnElDia`; `DisponibilidadServiceImplTest.publicadoDevuelveSoloLibres` |
| R25 | `CalculoDisponibilidadTest.horasEmpezadasSonPasado`, `.enPuntoEsPasado`; `DisponibilidadServiceImplTest.hoyDescartaHorasEmpezadas` |
| R26 | `CalculoDisponibilidadTest.futuraTodoLibre`; `CalendarioEspacioControllerIT.bloquesDelDia` |
| R27 | `CalendarioEspacioControllerIT.diaFueraDeVentana`; `CalendarioEspacioServiceTest.disponibilidadFueraDeVentana`; `VentanaReservaTest.contieneSoloSusFechas` |
| R28 | `CalendarioEspacioControllerIT.calculaAlConsultar`; `CalculoDisponibilidadTest.futuraTodoLibre` |
| R29 | `ZonaHorariaTest.calculaHoyYAhoraEnLima`, `.cambiaDeDiaALaMedianocheDeLima`; `DisponibilidadServiceImplTest.ventanaEnLima` |
| R30 | `DisponibilidadServiceImplTest.publicadoDevuelveSoloLibres`, `.bloqueadaNoConsultaPuerto`, `.hoyDescartaHorasEmpezadas`; `DisponibilidadServiceImplIT.horasLibresContraLaBase` |
| R31 | `DisponibilidadServiceImplTest.noPublicadoDevuelveVacio`, `.fueraDeVentanaDevuelveVacio` |
| R32 | `CalendarioEspacioControllerIT.ajenoEsNoEncontrado`; `CalendarioEspacioServiceTest.propiedadAntesQueEstado` |
| R33 | `CalendarioEspacioControllerIT.otroRolEsAccesoDenegado`; `CalendarioEspacioServiceTest.rolYCorreoAntesQuePropiedad`, `.rolAntesQuePropiedadEnLecturas` |
| R34 | `CalendarioEspacioControllerIT.sinCorreoSoloLee`; `CalendarioEspacioServiceTest.rolYCorreoAntesQuePropiedad` |
| R35 | `CalendarioEspacioControllerIT.sinSesion` |
| R36 | `CalendarioEspacioControllerIT.estadoNoEditable`; `CalendarioEspacioServiceTest.estadoAntesQueFecha` |
| R37 | `CalendarioEspacioControllerIT.ignoraIdsDelCuerpo` |
| R38 | `CalendarioEspacioControllerIT.formaAntesQueRol`; `BloqueoFechaDTOTest.rechazaFechaMalEscrita` |
| R39 | `CalendarioEspacioServiceTest.rolYCorreoAntesQuePropiedad`, `.rolAntesQuePropiedadEnLecturas`, `.propiedadAntesQueEstado`, `.estadoAntesQueFecha`, `.ventanaAntesQueYaBloqueada`, `.yaBloqueadaAntesQueReservas`; `CalendarioEspacioControllerIT.propiedadAntesQueEstado` |
| R40 | `CalendarioEspacioControllerIT.bloqueosSimultaneos` |
| R41 | `CalendarioEspacioControllerIT.auditaCambioDeHorario`; `CalendarioEspacioServiceTest.reemplazaHorario`, `.vaciaElHorario`; `FranjaHorariaTest.escribeSuTexto` |
| R42 | `CalendarioEspacioControllerIT.mismoHorarioNoAudita`; `CalendarioEspacioServiceTest.mismoHorarioNoAudita` |
| R43 | `CalendarioEspacioControllerIT.bloqueaFecha`; `CalendarioEspacioServiceTest.bloqueaFecha` |
| R44 | `CalendarioEspacioControllerIT.desbloqueaFecha`; `CalendarioEspacioServiceTest.desbloqueaFecha` |
| R45 | `CalendarioEspacioControllerIT.minimizaRespuestas`, `.bloquesDelDia`, `.reservadoEnElDia` |

## Archivos

**Nuevos (backend, `2_Backend/src/main/java/pe/edu/ulima/espacioya/`):**
`comun/configuracion/ZonaHoraria.java`;
`espacios/domain/{FranjaHoraria,ReglasHorario,VentanaReserva,EstadoBloque,Bloque,CalculoDisponibilidad,HorarioSemanal,BloqueoFecha}.java`;
`espacios/repository/{HorarioSemanalRepository,BloqueoFechaRepository}.java`;
`espacios/service/puertos/{ReservasDelEspacio,BloqueReservado}.java`;
`espacios/service/{DisponibilidadService,DisponibilidadServiceImpl,CalendarioEspacioService}.java`;
`espacios/dto/{HorarioDTO,FranjaDTO,BloqueoFechaDTO,BloqueosDTO,DisponibilidadDiaDTO,BloqueDTO}.java`;
`espacios/controller/CalendarioEspacioController.java`;
`2_Backend/src/main/resources/db/migration/V2_4__disponibilidad.sql`.

**Nuevos (tests, `T/`):** `comun/configuracion/ZonaHorariaTest`; `espacios/domain/{FranjaHorariaTest,ReglasHorarioTest,VentanaReservaTest,CalculoDisponibilidadTest}`;
`espacios/dto/{HorarioDTOTest,BloqueoFechaDTOTest}`; `espacios/service/{DisponibilidadServiceImplTest,DisponibilidadServiceImplIT,CalendarioEspacioServiceTest}`;
`espacios/controller/CalendarioEspacioControllerIT`.

**Modificado (tests):** `T/espacios/controller/EspaciosControllerSoporte.java` (solo el doble del puerto y su limpieza, como permite tasks.md).

**Frontend (`3_frontend/src/`):** nuevos `components/ui/SelectorBloques.{jsx,module.css}`,
`pages/espacios/DisponibilidadEspacio.{jsx,module.css}`; modificados `components/ui/Campo.jsx`, `api/espacios.js`,
`pages/espacios/textosEspacios.js`, `App.jsx`, `pages/espacios/MisEspacios.{jsx,module.css}`, `pages/espacios/EditarEspacio.jsx`.

**Docs:** `docs/modelo-datos.md`, `docs/architecture.md`, `docs/frontend.md`. **Arnés:** `specs/HU-08/tasks.md` (15 `[x]`), `progress/HU-08.md` (bitácora).

## Desviaciones y notas

1. **Controles deshabilitados en `BAJA`/`OCULTO` (design § 13):** se deshabilitan todos los botones de edición
   (Guardar horario, Agregar franja, Quitar, Bloquear fecha, Desbloquear), pero los `Campo` (select y date) siguen
   activos porque el kit no tiene una prop `deshabilitado` en `Campo` y el design solo autoriza agregar `min`/`max`.
   Sin los botones no se puede enviar nada, y el servidor responde 400 igual. Si el dueño quiere también los campos
   deshabilitados, hace falta agregar `deshabilitado` a `Campo` (cambio de kit, a pedir en el spec).
2. **`DisponibilidadServiceImplIT`** usa su propio contexto (`@SpringBootTest` + `TestcontainersConfiguration`, en el
   paquete `espacios.service`) en vez de extender `EspaciosControllerSoporte`, que es package-private en
   `espacios.controller` y registra el doble del puerto. Así prueba R30 y además R21 en un contexto sin implementación
   del puerto, como en producción. Cuesta un arranque de contexto más en `verify`.
3. **R21 y `EspacioYaApplicationIT`:** el plan de pruebas lo citaba, pero esa IT es de HU-01…07 y no se modificó
   (tasks.md lo prohíbe). R21 queda cubierto por `DisponibilidadServiceImplTest.sinPuertoNoHayReservas` y
   `DisponibilidadServiceImplIT.horasLibresContraLaBase`. `EspacioYaApplicationIT` sigue verde, así que el contexto
   arranca sin implementación del puerto.
4. **`ErrorDTO` sin campo trae `errores: []`** (no lo omite). Las IT de R19, R20 y R36 comprueban `errores` vacío;
   el comportamiento es el del manejador existente, sin cambios en `comun/error`.
5. **`CalculoDisponibilidad.bloquesDelDia`** filtra además por el día de la semana de la fecha y descarta horas
   repetidas; es redundante con lo que ya hace el llamador y no cambia el contrato del design.
6. **Pendientes que no son de esta sesión:** la firma del puerto `ReservasDelEspacio` sigue pendiente de confirmar con
   Eduardo Saldaña (HU-12), como dice DT-7. Si otra historia del sprint 2 llega a `develop` con `V2_4` antes del
   merge, hay que renombrar la migración (`docs/conventions.md`).

Ninguna task quedó sin completar y no hubo bloqueos.
