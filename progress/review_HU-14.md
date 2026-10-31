# Review — HU-14

**Veredicto:** APPROVED

> reviewer · 2026-10-31 · rama `feature/HU-14-mis-reservas` (= `develop`, base `6afb5bc` con HU-13), cambios sin commitear.
> Validado contra `specs/HU-14/` (aprobado por Adriana Callupe el 2026-10-31 con D1–D12, `progress/HU-14.md:70`),
> `docs/` y `CHECKPOINTS.md`. `./init.sh` completo (sin `--rapido`) corrido por el reviewer: **EXIT 0**, unitarias
> **889** e IT **626** con 0 fallos, 0 errores y 0 omitidas; ESLint sin avisos; `npm run build` OK; reglas de
> arquitectura, colores y secretos OK.

## Trazabilidad requirements ↔ tests

Abreviaturas: `MRIT` = `reservas/controller/MisReservasControllerIT`, `CRST` = `reservas/service/ConsultaReservasServiceTest`,
`HRT` = `reservas/domain/HistorialReservasTest`, `DAOIT` = `reservas/repository/ReservaDAOJpaIT`,
`DAOT` = `reservas/repository/ReservaDAOJpaTest`. Todos con `@DisplayName("HU-14 R<n>: …")` y aserciones concretas
(código HTTP, mensaje, ids exactos, claves exactas, valores de campo).

- R1: [x] `MRIT.sinSesion` (401 + mensaje en las dos rutas, también con token inventado) — MRIT:157
- R2: [x] `MRIT.misReservasConOtroRol` (ARRENDADOR y ADMIN, también con `grupo=todas&pagina=0`; 403 + mensaje, sin `errores`) — MRIT:168; `CRST.misReservasConOtroRol` (`verifyNoInteractions(dao, catalogo, cuentas)`) — CRST:95
- R3: [x] `MRIT.deMisEspaciosConOtroRol` — MRIT:182; `CRST.reservasDeMisEspaciosConOtroRol` — CRST:108
- R4: [x] `MRIT.misReservasProximas` (todos los campos y 900 s) — MRIT:197; `MRIT.confirmadaDePuntaAPunta` — MRIT:313; `CRST.misReservasProximas` — CRST:167
- R5: [x] `MRIT.misReservasPasadasConHorarioInactivo` — MRIT:226; `CRST.misReservasPasadasConVencida` — CRST:186
- R6: [x] `MRIT.soloLasDelUsuarioDeLaSesion` (la ajena nunca aparece; el otro usuario solo ve la suya) — MRIT:295; `CRST.misReservasProximas` (`verify(dao).delUsuario(USUARIO_ID)`)
- R7: [x] `MRIT.soloLasDelUsuarioDeLaSesion` (`usuarioId`, `espacioId`, `arrendadorId` ignorados) — MRIT:304; `MRIT.deSusEspaciosEnCualquierEstado` (`arrendadorId`, `espacioId` ignorados) — MRIT:382
- R8: [x] `MRIT.misReservasPasadasConHorarioInactivo` (filas `activo=false`, 14–17) — MRIT:226; `DAOIT.horarioConFilasInactivasYMedianoche` — DAOIT:139
- R9: [x] `MRIT.nombreDeUnEspacioNoVisible` (PAUSADO y arrendador SUSPENDIDA, las dos rutas) — MRIT:276
- R10: [x] `MRIT.vencidaSinProcesar` (EXPIRADA en la respuesta, fila sigue `PENDIENTE_PAGO`, bloque activo) — MRIT:245; `CRST.misReservasPasadasConVencida`; `CRST.unSoloInstante`
- R11: [x] `MRIT.misReservasProximas` (900) y `MRIT.misReservasPasadasConHorarioInactivo` / `vencidaSinProcesar` (0); `CRST.unSoloInstante` (1 s con reloj que avanza por lectura) — CRST:216
- R12: [x] `MRIT.misReservasProximas` (claves exactas del ReservaDTO; sin `usuarioId`, arrendador ni pago) — MRIT:217-222
- R13: [x] `MRIT.confirmadaDePuntaAPunta` — MRIT:313; `MRIT.soloConfirmadas` — MRIT:346; `CRST.reservasDeMisEspaciosProximas` — CRST:286
- R14: [x] `MRIT.deSusEspaciosEnCualquierEstado` (pasada en espacio BAJA) — MRIT:361; `CRST.reservasDeMisEspaciosPasadas` — CRST:305
- R15: [x] `MRIT.soloConfirmadas` (pendiente vigente, vencida, EXPIRADA, CANCELADA fuera) — MRIT:346; `MRIT.confirmadaDePuntaAPunta` (vacía antes de pagar) — MRIT:319; `DAOIT.confirmadasSoloDeEsosEspacios`
- R16: [x] `MRIT.deSusEspaciosEnCualquierEstado` (espacio ajeno fuera, en los dos sentidos) — MRIT:377-388
- R17: [x] `MRIT.deSusEspaciosEnCualquierEstado` (PAUSADO, BAJA, OCULTO) — MRIT:364-381
- R18: [x] `MRIT.nombreDelUsuario` ("Dora Quispe") — MRIT:404; `CRST.reservasDeMisEspaciosProximas`
- R19: [x] `MRIT.nombreDelUsuario` (nulo y `"   "` → "Usuario de EspacioYa") — MRIT:405-406; `MRIT.confirmadaDePuntaAPunta` — MRIT:330
- R20: [x] `MRIT.minimizacionDelArrendador` (claves exactas; sin correo, teléfono, `usuarioId`, pago) — MRIT:410; `CRST.nombresSoloDeLaPagina` — CRST:317
- R21: [x] `MRIT.arrendadorSinEspacios` — MRIT:428; `CRST.arrendadorSinEspacios` (`verifyNoInteractions(dao, cuentas)`) — CRST:273
- R22: [x] `HRT.limiteDeLasDiez`, `bloqueDeMedianoche`, `vigenteYConfirmadaFuturas`, `noVigentesFuturasSonPasadas`, `vigenteYaTerminadaEsPasada` — HRT:52-108; `MRIT.limiteDeLasDiez` (incluye avance del reloj) — MRIT:441
- R23: [x] `HRT.noVigentesFuturasSonPasadas` — HRT:87; `MRIT.expiradaYCanceladaFuturasSonPasadas` — MRIT:261; `MRIT.vencidaSinProcesar`
- R24: [x] `HRT.ordenEnLosDosSentidos`, `HRT.empatePorId` — HRT:113,128; `MRIT.orden` (empate por id con segundo espacio) — MRIT:462
- R25: [x] mismos que R24 + `CRST.reservasDeMisEspaciosPasadas`; `MRIT.orden` también en la ruta del arrendador — MRIT:478
- R26: [x] `PaginaTest` (10, 11, página 2, página máxima) ; `MRIT.paginas` (las dos rutas) — MRIT:483; `CRST.paginaDeDiez` — CRST:254
- R27: [x] `PaginaTest.noConsumeDeMas` y casos de 10/11; `MRIT.paginas`; `CRST.paginaDeDiez`
- R28: [x] `MRIT.sinPagina` — MRIT:513; `CRST.sinPaginaEsLaUno` — CRST:157
- R29: [x] `MRIT.paginaInvalida` (`0`, `-1`, `abc`, `1.5`, `2147483648`, `""`; las dos rutas) — MRIT:519; `CRST.paginaInvalida` (+ `" 1"`)
- R30: [x] `MRIT.grupoInvalido` (ausente, `PROXIMAS`, `todas`, `""`, `pasadas%20`; las dos rutas) — MRIT:528; `GrupoReservasTest`; `CRST.sinGrupo`
- R31: [x] `MRIT.grupoYPaginaInvalidos` (un 400, dos errores en orden) — MRIT:537; `CRST.grupoYPaginaInvalidos`
- R32: [x] `PaginaTest.sinElementos`, `paginaMasAllaDelFinal`; `HRT.listaVacia`; `MRIT.paginas` (página 5 y 2147483647); `MRIT.sinPagina`; `CRST.paginaVaciaNoPideNombres`
- R33: [x] `DAOIT.delUsuarioSoloLasSuyas`, `usuarioSinReservas`, `horarioConFilasInactivasYMedianoche`; `DAOT.delUsuarioConDosConsultas`, `reservaSinHorarioSeOmite`
- R34: [x] `DAOIT.confirmadasSoloDeEsosEspacios`, `coleccionVacia`; `DAOT.confirmadasDeEspacios`, `espaciosVaciosNoConsulta` (`verifyNoInteractions(reservas, bloques)`)
- R35: [x] `DAOT.delUsuarioConDosConsultas` (`times(1)` a cada repositorio), `sinReservasNoConsultaHorarios`, `confirmadasDeEspacios`
- R36: [x] `CuentaServiceImplTest.nombresSoloConNombre` (nulo/blanco fuera, ids deduplicados, sin otros repositorios) y `nombresVacio`; `MRIT.nombreDelUsuario`
- R37: [x] `CatalogoEspaciosServiceImplTest.nombresDelArrendadorEnCualquierEstado`, `nombresDelArrendadorSoloLosSuyos`, `nombresDelArrendadorNull`; `MRIT.deSusEspaciosEnCualquierEstado`
- R38: [x] `MRIT.soloLectura` (filas de `reserva` y `reserva_bloque` idénticas y `auditoria` sin cambios tras las dos rutas, con una vencida sin procesar sembrada) — MRIT:554

## Trazabilidad con la historia

- CA1 (abrir "Mis reservas"; espacio, fecha, horario, monto, estado; próximas y pasadas): [x] R1, R2, R4, R5, R8–R12, R22–R32; pantalla `3_frontend/src/pages/reservas/MisReservas.jsx` (Tarjeta + EtiquetaEstado + Fecha/Horario/Monto, dos `SeccionReservas`)
- CA2 (abrir "Reservas de mis espacios"; confirmadas con nombre del usuario y horario): [x] R1, R3, R13–R21, R34, R36; pantalla `ReservasDeMisEspacios.jsx` ("Reservado por", horario); E2E con pasarela simulada `MRIT.confirmadaDePuntaAPunta`
- (E/I) Cada consulta filtra por el usuario de la sesión: [x] R2, R3, R6, R7, R16, R33, R34, R37. Implementado: identidad desde `@AuthenticationPrincipal` (`ReservaController.java` `misReservas`, `ReservasDeMisEspaciosController.java:30-34`), rol comprobado antes de validar (`ConsultaReservasService.java:70,90,119-123`), `dao.delUsuario(usuario.usuarioId())` (`:73`), espacios de `catalogo.nombresDelArrendador(usuario.usuarioId())` (`:93`)
- (I) El arrendador no ve correo, teléfono ni datos de pago: [x] R19, R20, R36. Implementado: `ReservaDeMiEspacioDTO.java:8` (9 campos, sin `usuarioId`), `NombreCuenta` (solo id y nombre), `UsuarioRepository.nombresDe` proyecta solo `u.id, u.nombre`; `correoDe` no se usa en `reservas` (grep vacío); nombres pedidos solo para los usuarios de la página (`ConsultaReservasService.java:98-100`)

## Tasks completas

- T1: [x] `GrupoReservas`, `ReservaConHorario`
- T2: [x] `HistorialReservas`, `IteradorReservas`
- T3: [x] `Pagina`
- T4: [x] `ReservaRepository` (+2 métodos), `ReservaBloqueRepository.horarios`, `HorarioReserva`
- T5: [x] `ReservaDAO`, `ReservaDAOJpa`, `ReservaDAOJpaIT`, `ReservaDAOJpaTest`
- T6: [x] `CatalogoEspaciosService.nombresDelArrendador` + Impl + 3 casos agregados
- T7: [x] `NombreCuenta`, `UsuarioRepository.nombresDe`, `CuentaService.nombres` + Impl + 2 casos agregados
- T8: [x] `ConversionReservas`; `ReservaServiceImpl.java:150` delega con `Instant.now(reloj)`
- T9: [x] `PaginaReservasDTO` (`List.copyOf`), `ReservaDeMiEspacioDTO`
- T10: [x] `ConsultaReservasService` (`@Transactional(readOnly = true)`, sin auditoría, un solo instante)
- T11: [x] `ReservaController` (GET sin id), `ReservasDeMisEspaciosController`, `MisReservasControllerIT` (34 casos, IP 10.16.x.x, `@AfterEach borrarPagos`, E2E CA2)
- T12: [x] `api/reservas.js` (`URLSearchParams`), `textosReservas.js`
- T13: [x] `SeccionReservas.jsx` + css
- T14: [x] `MisReservas.jsx` + css ("Ver reserva" en todas, D10)
- T15: [x] `ReservasDeMisEspacios.jsx` + css
- T16: [x] `App.jsx` (dos rutas en `RutaProtegida`), `Panel.jsx`
- T17: [x] `docs/architecture.md` (sección "Mis reservas: qué deja HU-14", fila de patrones, párrafos de contratos), `docs/frontend.md`
- T18: [x] avisos en `progress/HU-14.md` y `progress/impl_HU-14.md`, nota D12
- T19: [x] `./init.sh` verde (confirmado por el reviewer)

## Verificaciones específicas pedidas por el orquestador

### Diseño (DT-1…DT-13) y D1–D12
- DT-1/D7/D8: rutas y controladores como en design § 5; `ConfiguracionSeguridad` sin cambios; 401/403 probados. [x]
- DT-2: `PaginaReservasDTO<T>(pagina, reservas, hayMas)` sin total; `ReservaDTO` de HU-12 sin cambios. [x]
- DT-3/DT-6: `ReservaDAO` sin herencia de Spring Data, `ReservaDAOJpa` `@Repository` de paquete; única condición de estado en SQL = `CONFIRMADA` (`ReservaDAOJpa.java:40`). [x]
- DT-4: un solo `Instant` por solicitud (`ConsultaReservasService.java:72,92`) usado para clasificar y para `estadoEn` del DTO (`:77`, `:105`; `ConversionReservas.java:19-21`). [x]
- DT-7: sin migración (no hay archivos nuevos en `db/migration`). [x]
- DT-8: IP `10.16.x.x` (`MisReservasControllerIT.java:58-61`), `@AfterEach borrarPagos` con `DELETE FROM pago` (`:69-72`), `ReservasControllerSoporte` sin tocar. [x]
- DT-9/DT-13/D6: sin componentes nuevos en el kit, sin `Imagen`; "Usuario de EspacioYa" lo pone el servidor (`ConsultaReservasService.java:45,104`). [x]
- DT-10/R31: rol antes que validación; errores acumulados en una `ValidacionException`. [x]
- DT-11: `ConversionReservas.aDto` reproduce exactamente el cuerpo eliminado de `ReservaServiceImpl.aDto` (mismos 11 argumentos, mismo `Instant.now(reloj)`). [x]
- D9/D12: `MRIT.confirmadaDePuntaAPunta` (MRIT:312-341) hace `POST /api/reservas` → `POST …/pago` → `POST …/pago/simulacion` `{"resultado":"APROBADO"}` sin `JdbcTemplate` para llegar a `CONFIRMADA`. [x]
- D10: `MisReservas.jsx:20-29` pone "Ver reserva" → `/reservas/:id` en la tarjeta de **todas** las reservas, sin condición de estado. [x]
- D11: nada de HU-14 lee `pago` en producción. [x]

### Patrones ★
- **DAO**: `ReservaDAO` (interfaz, javadoc del patrón) + `ReservaDAOJpa` (dos consultas, unión en memoria) + `ReservaConHorario` (TransferObject) + `ConsultaReservasService` (BusinessObject, solo conoce la interfaz). Aparece en el diagrama de clases y en las secuencias de `design.md` § 2 y § 8, y en `docs/architecture.md` (tabla "Mis reservas: qué deja HU-14" y fila HU-14 de "Patrones por historia"). [x]
- **Iterator**: `HistorialReservas` (copia privada `toList()` inmutable, sin getters, fábrica de recorridos) + `IteradorReservas` (de paquete; `hasNext` no consume; `NoSuchElementException`; `remove` por defecto lanza) + `Pagina` (cliente, salto en `long`, `hayMas = hasNext()`). Mismos diagramas y docs. [x]
- Dueño en la firma del DAO: `delUsuario(usuarioId)` lo exige directamente; `confirmadasDeEspacios(espacioIds)` recibe el conjunto de espacios del dueño, tal como fija DT-3 aprobado (ver Observación 2).

### Sin regresiones
- `git diff -- 2_Backend/src/test`: solo inserciones en `CuentaServiceImplTest` (+2 casos e imports `Map`, `Set`, `NombreCuenta`) y `CatalogoEspaciosServiceImplTest` (+3 casos). Ningún test borrado ni modificado. [x]
- `ReservaServiceImpl` (1 método reducido a delegación equivalente), `ReservaRepository` y `ReservaBloqueRepository` (solo métodos nuevos), `ReservaController` (constructor + nuevo `GET` sin id; `POST` y `GET /{id}` intactos). `CuentaService`, `CatalogoEspaciosService`, `UsuarioRepository`: solo agregan. Suite completa de HU-12/HU-13 en verde. [x]

### Seguridad
- Nadie ve reservas ajenas: probado en las dos direcciones (MRIT:295-308, 361-389; DAOIT:121-178). [x]
- 403 por rol: MRIT:166-192, CRST:93-117. [x]
- Minimización RNF-SEG-16: DTO de 9 claves, ausencia de correo/teléfono/pago en el cuerpo (MRIT:410-425), sin `correoDe`. [x]
- Sin N+1: `Reserva` y `ReservaBloque` no tienen asociaciones JPA; exactamente una consulta a `reserva` y una agregada a `reserva_bloque` (`ReservaDAOJpa.java:44-59`), más una a `espacio` y como mucho una a `usuario`, independientes del número de reservas. [x]
- Estado con `estadoEn(ahora)`: `ReservaConHorario.esProxima` y ambos DTO. [x]

## Checkpoints

- C1: [x] arnés completo; `./init.sh` sin `--rapido` EXIT 0 (corrido por el reviewer).
- C2: [x] una sola historia `in_progress` por dueño; `progress/HU-14.md` existe; aprobación de la dueña anotada (`progress/HU-14.md:70`); historias `done` con tests verdes. `progress/HU-14.md` describe solo la sesión de HU-14.
- C3: [x] módulos y capas correctos; controladores sin repositorios; inyección por constructor; DTO `record`; identidad de la sesión; estados y monto calculados en el servidor; sin puertos nuevos (tabla sin cambios, justificado en DT-5); sin migraciones; sin integraciones externas nuevas; nada fuera del alcance; sin secretos; frontend solo con tokens, kit, `Alerta error`, ningún botón primario, llamadas solo vía `src/api/`; pantallas coinciden con `design.md` § 12.
- C4: [x] `*Test` Mockito y `*IT` con Testcontainers/PostgreSQL; `Clock` fijo / `RelojControlable`; IT para los dos endpoints; aserciones concretas incluido el camino de error; `mvnw verify` 889 + 626 verdes; lint y build OK.
- C5: n/a parcial — la historia sigue `in_progress` (correcto hasta el cierre); trabajo en `feature/HU-14-mis-reservas`; sin archivos sospechosos sin trackear. Pendiente para el cierre (orquestador): entrada de HU-14 en `progress/history.md` y devolver `progress/HU-14.md` a la plantilla.
- C6: [x] `specs/HU-14/` completo; EARS con tabla de trazabilidad que cubre CA1, CA2 y los dos controles STRIDE; design con endpoints, clases, migración (ninguna), secuencias, patrones ★ y 8 alternativas descartadas; T1–T19 `[x]`; cada R1–R38 con test documentado en `progress/impl_HU-14.md`; ningún pendiente de `requerimientos.md` §9 resuelto sin la dueña (D1–D12 aprobadas).

## Observaciones (no bloquean)

1. **Texto desactualizado en el spec.** `specs/HU-14/requirements.md:5` ("D1–D12 siguen pendientes de la aprobación de la
   dueña") y `specs/HU-14/design.md:7` ("D1–D12: decisiones por defecto a confirmar") contradicen la aprobación del
   2026-10-31 registrada en `progress/HU-14.md:70`. Conviene actualizar ambos encabezados antes del PR.
2. **Dueño en `confirmadasDeEspacios`.** `ReservaDAO.java:23` recibe `Collection<Long> espacioIds`, no el id del
   arrendador: la propiedad la garantiza el servicio (`ConsultaReservasService.java:93` → `catalogo.nombresDelArrendador`),
   no la firma del DAO. Coincide con DT-3 aprobado y está probado de punta a punta, pero `docs/architecture.md`
   ("métodos [que] exigen siempre el dueño como primer parámetro… No hay forma de pedir reservas sin dueño") lo
   sobreafirma. Si HU-15 amplía el DAO, considerar `confirmadasDelArrendador(arrendadorId, espacioIds)` o matizar el texto.
3. **CSS sin uso.** `3_frontend/src/pages/reservas/ReservasDeMisEspacios.module.css:33-37` define `.acciones`, que
   `ReservasDeMisEspacios.jsx:25` no usa (pone el `Link` directo). Quitarlo o dejarlo comentado para HU-15.
4. **Cierre de sesión.** Falta la entrada de HU-14 en `progress/history.md` (C5); la agrega el orquestador al cerrar.
