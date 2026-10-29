# Review — HU-08

**Veredicto:** APPROVED

> Reviewer, 2026-10-28. Rama `feature/HU-08-disponibilidad-espacio`, cambios sin commitear.
> `./init.sh` completo (sin `--rapido`): **exit 0**. Backend `./mvnw verify`: 385 unitarias + 262 IT, 0 fallos,
> 0 errores. ESLint sin advertencias, `npm run build` OK, sin colores sueltos ni secretos, validador de
> `feature_list.json` / specs / progress OK.

## Trazabilidad requirements ↔ tests
Clases bajo `2_Backend/src/test/java/pe/edu/ulima/espacioya/`. Se leyó el cuerpo de cada test citado (no solo el `@DisplayName`).

- R1: [x] `CalendarioEspacioControllerIT.guardaHorario` (orden y valores en JSON), `.reemplazaHorario`; `CalendarioEspacioServiceTest.reemplazaHorario`
- R2: [x] `CalendarioEspacioControllerIT.guardaHorario` / `.reemplazaHorario` (filas exactas por `JdbcTemplate`; mismo `hora_inicio` sin chocar con el UNIQUE)
- R3: [x] `CalendarioEspacioControllerIT.vaciaHorario` (0 filas)
- R4: [x] `HorarioDTOTest.validaLaLista` (168 ok / 169 y ausente → `franjas`), `.validaDia`, `.validaHoraInicio`, `.validaHoraFin`, `.rechazaElementoNulo`; IT `.rechazaHorasQueNoSonEnteras` (8.5, "8", true), `.unErrorPorCampo` (3 errores, `franjas[0]`, `franjas`)
- R5: [x] `ReglasHorarioTest.reportaRango`; IT `.rechazaRango` (`franjas[1]` + mensaje)
- R6: [x] `ReglasHorarioTest.reportaSuperpuestaEnElIndiceMayor`, `.aceptaContiguasYOtrosDias`; IT `.rechazaSuperpuestas` (`franjas[2]`)
- R7: [x] IT `.rechazaHorasQueNoSonEnteras`, `.rechazaRango`, `.rechazaSuperpuestas`, `.estadoNoEditable`, `.ajenoEsNoEncontrado`, `.otroRolEsAccesoDenegado` (filas sin cambios)
- R8: [x] IT `.consultaHorario` (vacío, ordenado, también en `BAJA`)
- R9: [x] IT `.baseValidaElHorario` (día 8 y 0, fin 25, inicio −1, inicio = fin, inicio repetido → `DataIntegrityViolationException`)
- R10: [x] IT `.bloqueaFecha` (201 + `fecha`)
- R11: [x] IT `.bloqueaFecha` (fila en `bloqueo_fecha`)
- R12: [x] IT `.rechazaFechaInvalida` (POST `2026-02-30`, `07/11/2026`, ausente; GET `abc` y sin fecha; DELETE `abc`); `CalendarioEspacioServiceTest.rechazaFechaInexistente`
- R13: [x] IT `.soloDentroDeLaVentana` (hoy−1 / hoy+56 → 400; hoy / hoy+55 → 201); `VentanaReservaTest`
- R14: [x] IT `.yaBloqueada` (campo `fecha`, una sola auditoría)
- R15: [x] IT `.listaBloqueos` (desde/hasta, excluye la pasada, orden)
- R16: [x] IT `.desbloqueaFecha` (204 + fila borrada)
- R17: [x] IT `.desbloquearNoBloqueada` (404, el otro bloqueo intacto, sin auditoría)
- R18: [x] IT `.baseImpideBloqueoDuplicado`
- R19: [x] IT `.reservaPagadaImpideBloquear` (400 sin campo, 0 filas, 0 auditorías); `CalendarioEspacioServiceTest.reservaPagadaImpideBloquear` (pagada gana a pendiente)
- R20: [x] IT `.reservaPendienteImpideBloquear`; `CalendarioEspacioServiceTest.reservaPendienteImpideBloquear`
- R21: [x] `DisponibilidadServiceImplTest.sinPuertoNoHayReservas` (`getIfAvailable()` = null → `[]` y bloques LIBRE); `DisponibilidadServiceImplIT.horasLibresContraLaBase` (contexto real sin bean del puerto)
- R22: [x] IT `.bloquesDelDia` (orden, `horaFin = h+1`, claves exactas); `CalculoDisponibilidadTest.unBloquePorHora`
- R23: [x] IT `.bloqueadaEnElDia`; `CalculoDisponibilidadTest.bloqueadaGanaATodo`
- R24: [x] IT `.reservadoEnElDia`; `CalculoDisponibilidadTest.reservadaEsReservado`
- R25: [x] `CalculoDisponibilidadTest.horasEmpezadasSonPasado` (10:30), `.enPuntoEsPasado` (10:00; reserva gana a PASADO); `DisponibilidadServiceImplTest.hoyDescartaHorasEmpezadas`
- R26: [x] `CalculoDisponibilidadTest.futuraTodoLibre`; IT `.bloquesDelDia`
- R27: [x] IT `.diaFueraDeVentana`
- R28: [x] IT `.calculaAlConsultar` (refleja el horario nuevo; `information_schema` sin tabla de bloques materializados)
- R29: [x] `ZonaHorariaTest` (03:00Z → 2026-10-28 22:00 en Lima; medianoche de Lima); `DisponibilidadServiceImplTest.ventanaEnLima`
- R30: [x] `DisponibilidadServiceImplTest.publicadoDevuelveSoloLibres` (8, 15); `DisponibilidadServiceImplIT.horasLibresContraLaBase` (8, 9, 14, 15)
- R31: [x] `DisponibilidadServiceImplTest.noPublicadoDevuelveVacio` (inexistente, BORRADOR, PAUSADO), `.fueraDeVentanaDevuelveVacio`
- R32: [x] IT `.ajenoEsNoEncontrado` (los 6 endpoints, ajeno e inexistente, nada cambia)
- R33: [x] IT `.otroRolEsAccesoDenegado` (los 6)
- R34: [x] IT `.sinCorreoSoloLee` (PUT/POST/DELETE 403; GET 200)
- R35: [x] IT `.sinSesion` (los 6 → 401 con el mensaje de sesión)
- R36: [x] IT `.estadoNoEditable` (BAJA y OCULTO → 400 sin campo en las 3 escrituras; PAUSADO sí)
- R37: [x] IT `.ignoraIdsDelCuerpo`
- R38: [x] IT `.formaAntesQueRol` (cuenta USUARIO → 400 por campo, no 403)
- R39: [x] `CalendarioEspacioServiceTest.rolYCorreoAntesQuePropiedad`, `.rolAntesQuePropiedadEnLecturas`, `.propiedadAntesQueEstado`, `.estadoAntesQueFecha`, `.ventanaAntesQueYaBloqueada`, `.yaBloqueadaAntesQueReservas` (con `verifyNoInteractions` / `never()`); IT `.propiedadAntesQueEstado`
- R40: [x] IT `.bloqueosSimultaneos` (2 hilos + `CountDownLatch` → 201 y 400 *ya bloqueada*, 1 fila)
- R41: [x] IT `.auditaCambioDeHorario` (usuario, entidad, id, IP, fecha = reloj, `antes=ninguna;despues=1:8-22,2:8-22` y el segundo cambio)
- R42: [x] IT `.mismoHorarioNoAudita`; `CalendarioEspacioServiceTest.mismoHorarioNoAudita`
- R43: [x] IT `.bloqueaFecha` (todos los campos de la auditoría)
- R44: [x] IT `.desbloqueaFecha`
- R45: [x] IT `.minimizaRespuestas` (claves exactas de horario, franja, bloqueos y creado), `.bloquesDelDia`, `.reservadoEnElDia` (sin `pagado`, `usuario`, `monto`, `id`)

## Trazabilidad con la historia
- CA1 (horario por día + bloques reservables 8 semanas): [x] R1–R9, R22, R26–R31; secciones "Horario semanal" y "Ver un día" (`DisponibilidadEspacio.jsx:286-347`, `:390-407`)
- CA2 (bloquear fecha → bloques no disponibles): [x] R10–R18, R23, R30, R40; `Alerta` de éxito "Bloqueamos el …" (`DisponibilidadEspacio.jsx:237-240`)
- CA3 (reserva pagada → aviso y no bloquea): [x] R19–R21, R24; `Alerta` de error con el mensaje del servidor (`DisponibilidadEspacio.jsx:244-246`)
- (E) Solo el dueño modifica el calendario, validado en backend: [x] R32–R39. Implementado en `CalendarioEspacioService.java:163-175` (`findParaActualizar` / `findByIdAndArrendadorId` con el id de la sesión; rol → correo → propiedad → estado)
- (R) Registro de cada cambio: [x] R41–R44. `CalendarioEspacioService.java:101-104, 137, 149`
- Simplificación (calculado al consultar): [x] R22–R28, R30. `DisponibilidadServiceImpl.bloquesDelDia` + `CalculoDisponibilidad`; sin tablas de bloques

## Tasks completas
- T1: [x] `comun/configuracion/ZonaHoraria` + `ZonaHorariaTest`
- T2: [x] `V2_4__disponibilidad.sql` (CHECK, UNIQUE, `ON DELETE CASCADE`), igual a design § 9
- T3: [x] dominio completo + 4 tests de dominio
- T4: [x] repositorios (`borrarDelEspacio` probado por `.reemplazaHorario`)
- T5: [x] puerto `ReservasDelEspacio` + `BloqueReservado` con javadoc del contrato DT-10
- T6: [x] `DisponibilidadService` / `Impl` con `ObjectProvider`, más test unitario e IT
- T7: [x] 6 DTO `record`, mensajes de design § 4 al pie de la letra
- T8: [x] `CalendarioEspacioService` (6 operaciones) + 21 casos
- T9: [x] controller (6 endpoints) + doble en `EspaciosControllerSoporte.Dobles` + 36 casos IT
- T10: [x] `Campo` con `min`/`max`; `SelectorBloques` solo con tokens `--bloque-*`, solo lectura sin `onCambiar`
- T11: [x] 6 funciones en `api/espacios.js`; `DIAS_SEMANA`, `formatoHora`, `TEXTO_ESTADO_BLOQUE`
- T12: [x] pantalla con las 3 secciones; un solo botón primario ("Guardar horario")
- T13: [x] ruta en `RutaProtegida`, enlaces en `MisEspacios` y `EditarEspacio`
- T14: [x] `docs/modelo-datos.md`, `docs/architecture.md` (puerto en la tabla, `DisponibilidadService`, zona), `docs/frontend.md`
- T15: [x] `./init.sh` verde (lo confirmó este reviewer) + `progress/impl_HU-08.md`

## Desviaciones declaradas (progress/impl_HU-08.md § Desviaciones)
1. **Campos activos en BAJA/OCULTO**: ACEPTABLE. Todos los botones de envío están deshabilitados (`DisponibilidadEspacio.jsx:324, 334, 343, 364, 379`), así que no se puede enviar nada, y el servidor responde 400 igual (R36 probado). Agregar `deshabilitado` a `Campo` es un cambio de kit que el design no autorizaba. Si el dueño quiere también los campos deshabilitados, se pide como ajuste de spec o en otra historia.
2. **`DisponibilidadServiceImplIT` con su propio contexto**: ACEPTABLE, y mejor que la alternativa: prueba R21 en un contexto sin bean del puerto, como en producción. Cumple C4 (`*IT` + `@Import(TestcontainersConfiguration.class)`). Observación no bloqueante: su `prepararBase()` (`DisponibilidadServiceImplIT.java:52-54`) borra solo `espacio`, `perfil_arrendador` y `usuario`. Comparte la caché de contexto (y el contenedor) con `EspacioYaApplicationIT`, que hoy no inserta datos. Si mañana otra IT con la misma configuración deja filas en `sesion`, `auditoria` o `documento_identidad`, el `DELETE FROM usuario` fallaría por FK. Conviene alinearlo con `EspaciosControllerSoporte.prepararBase()`.
3. **R21 sin tocar `EspacioYaApplicationIT`**: ACEPTABLE. tasks.md prohíbe modificar las IT de HU-01…07. R21 queda cubierto por un unitario y una IT reales, y `EspacioYaApplicationIT` sigue verde (el contexto arranca sin implementación del puerto).
4. **`errores: []` en un 400 sin campo**: ACEPTABLE. Es el comportamiento del `ErrorDTO` existente, sin cambios en `comun/error`. "Sin campo" se cumple: las IT comprueban `$.errores` vacío (`CalendarioEspacioControllerIT.java:143-147`), y el frontend lo maneja (`cliente.js` siempre da un arreglo; la pantalla busca el campo `fecha`).
5. **Filtro redundante en `CalculoDisponibilidad.bloquesDelDia`** (`CalculoDisponibilidad.java:24-27`): ACEPTABLE. Es defensivo, no cambia el contrato y lo prueba `.ignoraOtrosDias`.

## Arquitectura y seguridad (puntos verificados)
- Propiedad: toda operación busca el espacio con el id de la sesión (`CalendarioEspacioService.java:165, 174`). Ajeno e inexistente dan el mismo 404, y el cuerpo nunca aporta identidad (R37).
- Orden de R39: en las escrituras, `arrendadorHabilitado` (rol + correo) → `findParaActualizar` (propiedad) → `esEditable` (estado) → fecha → ventana → ya bloqueada → pagada → pendiente (`CalendarioEspacioService.java:121-135`). En el horario, rango y superposición se revisan después del estado (`:87-96`). En las lecturas: rol → propiedad → fecha → ventana. La forma (R38) la rechaza `@Valid` antes del servicio.
- Concurrencia R40: `findParaActualizar` con `PESSIMISTIC_WRITE` (`EspacioRepository.java:23-25`) dentro de `@Transactional`; el segundo POST ve el primero y recibe 400 *ya bloqueada*. El UNIQUE de la BD queda como respaldo.
- Auditoría R41–R44: `AuditoriaService` + `EventoAuditoria` con usuario, entidad `espacio`, id, IP (`IpCliente`) y detalle exacto. R42 compara las listas ordenadas de antes y después (`CalendarioEspacioService.java:101`). Los rechazos no se auditan (P6).
- Minimización R45: los DTO solo llevan los campos del design; `BloqueReservado.pagado` no sale del servidor.
- Módulos: `espacios` no importa `reservas`. El puerto vive en `service/puertos/` y figura en la tabla de `docs/architecture.md`. `cuentas` se usa solo vía `CuentaService`. Ningún controller importa un repositorio. Inyección por constructor y `record` en los DTO.
- Frontend: solo tokens, sin estilos en línea, llamadas solo por `src/api/`, pantalla con el kit (`Tarjeta`, `Campo`, `Boton`, `Alerta`, `EtiquetaEstado`, `SelectorBloques`), un único `Boton` primario y los textos de éxito/error de design § 13.

## Checkpoints
- C1: [x] archivos base · [x] docs · [x] historias · [x] 4 subagentes · [x] `./init.sh` completo exit 0
- C2: [x] una `in_progress` por dueño · [x] existe `progress/HU-08.md` · n/a historias `done` (HU-08 no está cerrada) · [x] aprobación de Sebastian Garate anotada (`progress/HU-08.md:15, 29`) · [x] progress solo de la sesión activa
- C3: [x] paquetes en módulos · [x] controller sin repository · [x] sin `@Autowired`, Lombok ni comodines en `src/main` · [x] sin prints · [x] dependencias vía service/puerto; puerto nuevo en la tabla · [x] DTO `record`, sin entidades expuestas · [x] identidad desde la sesión; estados en el servidor · [x] migración `V2_4__disponibilidad.sql` nueva, ninguna editada · n/a adapters (sin integraciones externas nuevas) · [x] nada fuera del alcance · [x] sin secretos · [x] sin colores fuera de tokens ni librerías · [x] pantallas con el kit, mensajes según `docs/frontend.md`, API solo desde `src/api/`
- C4: [x] nombres `*Test` / `*IT` con `TestcontainersConfiguration` · [x] sin H2 · [x] `Clock` fijo / `RelojControlable` (ventana y "hoy") · [x] IT de endpoints y restricciones de BD; concurrencia con dos POST a la vez · [x] cada test comprueba resultados concretos (estado HTTP, campos, filas, auditoría) · [x] `mvnw verify` con más de 0 tests, todos verdes · [x] lint + build
- C5: [x] sin archivos sospechosos sin trackear · [ ] `progress/history.md` sin entrada de esta sesión (la añade el orquestador al cerrar; no bloquea la revisión) · n/a progress a plantilla (historia aún no `done`) · [x] `feature_list.json` en `in_progress` (correcto hasta el merge) · [x] rama `feature/HU-08-disponibilidad-espacio`
- C6: [x] `specs/HU-08/` completo · [x] EARS + tabla de trazabilidad con CA1–CA3, (E), (R) y la simplificación · [x] design con endpoints, clases, migración, secuencia y alternativas descartadas (§ 15); historia no ★ · n/a tasks de `done` · [x] cada `R<n>` con test, documentado en `progress/impl_HU-08.md` · [x] ningún pendiente de §9 resuelto sin el dueño (P1–P12 aprobados por Sebastian Garate)

## Cambios requeridos
Ninguno bloqueante. Recomendaciones:
1. Al cerrar la sesión, añadir la entrada de HU-08 en `progress/history.md` (C5).
2. (Opcional) Alinear la limpieza de `DisponibilidadServiceImplIT.prepararBase()` (`:52-54`) con la de `EspaciosControllerSoporte.prepararBase()` (borrar también `foto_espacio`, `documento_identidad`, `sesion` y `auditoria`) para que no dependa del orden de las IT.
3. Pendientes ya documentados: confirmar la firma de `ReservasDelEspacio` con Eduardo Saldaña (HU-12) y renombrar `V2_4` si otra historia del sprint 2 llega antes a `develop`.
