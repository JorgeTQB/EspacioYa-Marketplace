# Implementación HU-21 · Pausar o dar de baja una publicación

> implementer, 2026-11-04 · rama `feature/HU-21-pausar-baja-publicacion` · spec aprobado por Sebastian Garate
> (decisiones 1–13 de `design.md` § 2 tal como están). Sin commit, push ni PR. Bloqueo resuelto (opción 1); historia `in_progress`, pendiente del reviewer.

## Qué se hizo

Las 20 tasks de `specs/HU-21/tasks.md` están `[x]`. T20 se cerró tras resolver el conflicto de spec con HU-14 R7 (ver
"Bloqueo" y "Desbloqueo (opción 1)"). Reviewer: APPROVED (`progress/review_HU-21.md`).

- **Dominio (T1, T2).** `Espacio` gana `esPausable`, `esReanudable`, `admiteBaja`, `pausar`, `reanudar` y `darDeBaja`
  (`IllegalStateException` si el predicado falla; fijan `actualizadoEn`). `BloqueReservado.terminaDespuesDe(LocalDateTime)`
  = `fecha.atTime(horaInicio, 0).plusHours(1).isAfter(ahoraLocal)`.
- **Servicio y rutas de `espacios` (T3–T8).** `EspacioService.habilitacion` y `aDto` pasan a visibilidad de paquete (sin
  cambio de comportamiento). `EstadoEspacioService` con el orden de DT-4, las constantes y mensajes de § 9, reservas por
  terminar leídas con `ObjectProvider<ReservasDelEspacio>` + `VentanaReserva` **después** del bloqueo de fila
  (`findParaActualizar` / `findParaReservar`), auditoría `PAUSA_ESPACIO` / `REANUDACION_ESPACIO` / `BAJA_ESPACIO` con
  `;actor=ADMIN;arrendadorId=` si actúa el admin. `EstadoEspacioDTO(id, estado)`. `EstadoEspacioController`
  (`POST /api/arrendadores/actual/espacios/{id}/pausa|reanudacion|baja`) y `EspacioAdminController`
  (`POST /api/admin/espacios/{id}/pausa|baja`).
- **Reservas afectadas (T9–T12).** `ConsultaReservasService.reservasDeMisEspacios(usuario, grupo, pagina, espacioId)`
  (la de 3 parámetros delega con `null`); `CAMPO_ESPACIO = "espacioId"`, `MENSAJE_ESPACIO = "El espacio no es válido."`;
  validación conjunta (`grupo`, `pagina`, `espacioId`, en ese orden); 404 si el id no está en `nombresDelArrendador`;
  `ReservaDAO` sin cambios. `ReservasDeMisEspaciosController.listar` recibe `espacioId` opcional.
- **Frontend (T13–T17).** `pausarEspacio`, `reanudarEspacio`, `darDeBajaEspacio`; `listarReservasDeMisEspacios` con
  `espacioId` opcional. `TarjetaReservaDeMiEspacio.jsx` (+ CSS Module) extraída de `ReservasDeMisEspacios.jsx` con el mismo
  marcado (sus clases `datos`, `monto`, `acciones` se movieron a su CSS Module). `EstadoPublicacion.jsx` y
  `ReservasAfectadas.jsx` (+ CSS Modules) y avisos/solo lectura en `EditarEspacio.jsx`; textos en `textosEspacios.js`.
  Solo kit y tokens; sin componentes, estados ni colores nuevos.
- **Docs (T18, T19).** `docs/architecture.md`: línea del espacio en "Estados del sistema", nueva sección "Estado del
  espacio: qué deja HU-21 (HU-27)", HU-21 marcada como hecha en "Disponibilidad", filtro `espacioId` en "Mis reservas" y
  nota en "Cancelación". `docs/frontend.md`: entrada de `/panel/espacios/:id`. Tabla de puertos sin cambios (no hay puerto
  nuevo); `docs/modelo-datos.md` sin cambios (sin migración).

## Archivos

Creados (backend):
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/espacios/service/EstadoEspacioService.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/espacios/dto/EstadoEspacioDTO.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/espacios/controller/EstadoEspacioController.java`
- `2_Backend/src/main/java/pe/edu/ulima/espacioya/espacios/controller/EspacioAdminController.java`
- `2_Backend/src/test/java/pe/edu/ulima/espacioya/espacios/service/EstadoEspacioServiceTest.java`
- `2_Backend/src/test/java/pe/edu/ulima/espacioya/espacios/service/puertos/BloqueReservadoTest.java`
- `2_Backend/src/test/java/pe/edu/ulima/espacioya/espacios/controller/EstadoEspacioControllerIT.java`
- `2_Backend/src/test/java/pe/edu/ulima/espacioya/espacios/controller/EspacioAdminControllerIT.java`
- `2_Backend/src/test/java/pe/edu/ulima/espacioya/reservas/controller/BajaEspacioConReservasIT.java`
- `2_Backend/src/test/java/pe/edu/ulima/espacioya/reservas/controller/BajaEspacioConcurrenciaIT.java`

Modificados (backend):
- `espacios/domain/Espacio.java`, `espacios/service/puertos/BloqueReservado.java`, `espacios/service/EspacioService.java`
- `reservas/service/ConsultaReservasService.java`, `reservas/controller/ReservasDeMisEspaciosController.java`
- Tests: `espacios/domain/EspacioTest.java` (7 casos nuevos), `reservas/service/ConsultaReservasServiceTest.java`
  (7 casos nuevos, uno parametrizado). Único test existente cambiado: `MisReservasControllerIT` línea 383 (HU-14), en el
  desbloqueo con la opción 1 (ver "Desbloqueo (opción 1)").

Frontend (`3_frontend/src/`):
- Creados: `pages/espacios/EstadoPublicacion.jsx` + `.module.css`, `pages/espacios/ReservasAfectadas.jsx` + `.module.css`,
  `pages/reservas/TarjetaReservaDeMiEspacio.jsx` + `.module.css`.
- Modificados: `api/espacios.js`, `api/reservas.js`, `pages/espacios/EditarEspacio.jsx`, `pages/espacios/textosEspacios.js`,
  `pages/reservas/ReservasDeMisEspacios.jsx`, `pages/reservas/ReservasDeMisEspacios.module.css`.

Docs y seguimiento: `docs/architecture.md`, `docs/frontend.md`, `specs/HU-21/tasks.md`, `progress/HU-21.md`.

## Trazabilidad

Clases abreviadas: `EET` = `EspacioTest`, `BRT` = `BloqueReservadoTest`, `EEST` = `EstadoEspacioServiceTest`,
`EECIT` = `EstadoEspacioControllerIT`, `EACIT` = `EspacioAdminControllerIT`, `CRST` = `ConsultaReservasServiceTest`,
`BECRIT` = `BajaEspacioConReservasIT`, `BECIT` = `BajaEspacioConcurrenciaIT`.

- R1 → `EEST.pausaUnPublicado`, `EECIT.pausaUnPublicado`
- R2 → `EECIT.pausadoNoSeVeNiSeReserva`, `EECIT.bajaNoSeVeNiSeReserva`
- R3 → `EECIT.pausadoNoSeVeNiSeReserva`, `EECIT.bajaNoSeVeNiSeReserva`
- R4 → `EECIT.pausadoNoSeVeNiSeReserva`, `EECIT.bajaNoSeVeNiSeReserva`
- R5 → `BECRIT.pausaConservaReservas`
- R6 → `EET.predicadosDeEstado`, `EET.pausarFueraDePublicadoLanza`, `EEST.pausarFueraDePublicadoRechaza`,
  `EEST.adminPausaFueraDePublicadoRechaza`, `EECIT.pausarFueraDePublicado`, `EACIT.adminEstadoInvalido`
- R7 → `EEST.reanudaUnPausado`, `EECIT.reanudaUnPausado`
- R8 → `EET.reanudarFueraDePausadoLanza`, `EEST.reanudarFueraDePausadoRechaza`, `EECIT.reanudarFueraDePausado`
- R9 → `EEST.reanudarSinIdentidadRechaza`, `EECIT.reanudarSinIdentidad`
- R10 → `EEST.reanudarSinFotosRechaza`, `EECIT.reanudarSinFotos`
- R11 → `EEST.reanudarOrdenDeReglas`, `EECIT.reanudarSinIdentidad`, `EECIT.reanudarOrdenEstadoPrimero`
- R12 → `EEST.daDeBajaSinReservas`, `EEST.bajaSinPuertoSeCompleta`, `EECIT.daDeBajaPublicadoYPausado`
- R13 → `BRT.bloqueFuturoCuenta`, `BRT.bloqueDeLasOnceTerminaAMedianoche`, `EEST.bajaConReservaFuturaRechaza`,
  `EECIT.bajaConReservaFutura`, `BECRIT.bajaConReservaFuturaCanceladaAlCien`, `BECRIT.confirmadaFuturaImpideLaBajaDePublicado`
- R14 → `BRT.bloqueEnCursoCuenta`, `EEST.bajaConReservaEnCursoOPendienteRechaza`, `BECRIT.reservaEnCursoImpideLaBaja`,
  `BECRIT.pendienteVigenteImpideLaBaja`
- R15 → `BRT.bloqueQueTerminaAhoraNoCuenta`, `EEST.bajaConBloquesTerminadosSeCompleta`, `BECRIT.reservasQueNoImpidenLaBaja`
- R16 → `EET.darDeBajaNoAdmitidaLanza`, `EEST.bajaDeEstadoNoAdmitidoRechazaSinConsultarReservas`,
  `EEST.adminBajaDeEstadoNoAdmitidoRechaza`, `EECIT.bajaDeEstadoNoAdmitido`, `EACIT.adminEstadoInvalido`
- R17 → `BECIT.bajaYReservaALaVez`, `BECIT.losDosOrdenes`, `EEST.reservasSeLeenTrasElBloqueo`
- R18 → `EECIT.bajaNoSeEdita`
- R19 → `CRST.filtraPorEspacioPropio`, `BECRIT.bajaConReservaFuturaCanceladaAlCien`, `BECRIT.filtroSoloDeEseEspacio`
- R20 → `CRST.espacioIdInvalido` (7 valores), `CRST.espacioIdMaximo`, `CRST.espacioIdConOtroRol`, `BECRIT.filtroAjenoOInvalido`
- R21 → `CRST.espacioIdAjenoDa404`, `CRST.espacioIdSinEspaciosDa404`, `BECRIT.filtroAjenoOInvalido`
- R22 → `CRST.sinEspacioIdIgualQueHu14` (además, los tests de HU-14 siguen verdes sin cambios)
- R23 → `BECRIT.bajaConReservaFuturaCanceladaAlCien`
- R24 → `BECRIT.bajaConReservaFuturaCanceladaAlCien`
- R25 → `EEST.otroRolRecibe403`, `EECIT.usuarioRecibe403`
- R26 → `EEST.sinCorreoVerificadoRecibe403`, `EECIT.sinCorreoVerificadoRecibe403`
- R27 → `EEST.ajenoOInexistenteDa404`, `EECIT.ajenoOInexistenteDa404`
- R28 → `EEST.adminPausa`, `EACIT.adminPausa`
- R29 → `EEST.adminDaDeBaja`, `EACIT.adminDaDeBaja`
- R30 → `EEST.adminBajaConReservasRechaza`, `EACIT.adminBajaConReservas`
- R31 → `EEST.adminInexistenteDa404`, `EACIT.adminInexistente`
- R32 → `EEST.adminSinRolRecibe403`, `EACIT.sinRolAdmin`
- R33 → `EACIT.ipNoPermitida`
- R34 → `EEST.auditaPausa`, `EECIT.pausaUnPublicado`
- R35 → `EEST.auditaReanudacion`, `EECIT.reanudaUnPausado`
- R36 → `EEST.auditaBaja`, `EECIT.daDeBajaPublicadoYPausado`
- R37 → `EEST.auditaActorAdmin`, `EEST.auditaBajaActorAdmin`, `EACIT.adminPausa`, `EACIT.adminDaDeBaja`
- R38 → `EEST.rechazosNoAuditan`, `EECIT.pausarFueraDePublicado`, `EECIT.bajaConReservaFutura`,
  `EECIT.ajenoOInexistenteDa404`, `EECIT.sinSesionRecibe401`
- R39 → `EET.pausarPublicado`, `EET.reanudarPausado`, `EET.darDeBajaPublicadoOPausado`, `EEST.fijaActualizadoEn`,
  `EECIT.pausaUnPublicado`, `EECIT.daDeBajaPublicadoYPausado`
- R40 → `EECIT.listadoIncluyePausadoYBaja`

84 tests con `@DisplayName("HU-21 R<n>: …")`; R1–R40 cubiertos.

### Concurrencia (R17), sin intermitencia

`BajaEspacioConcurrenciaIT.bajaYReservaALaVez` lanza la baja y `POST /api/reservas` del mismo espacio en dos hilos con un
`CountDownLatch`, 6 rondas (espacio y usuario nuevos por ronda, para que el cupo de 3 pendientes de HU-12 no intervenga).
La aserción es el invariante (o reserva 201 + baja 400 + espacio `PUBLICADO` con 1 reserva activa, o baja 200 + reserva
404 + espacio `BAJA` sin reservas activas ni bloques), así que pasa gane quien gane: no depende del orden de los hilos.
`losDosOrdenes` prueba además los dos órdenes en secuencia, de forma determinista. Corrido 3 veces seguidas sin fallos.

## Verificación

- `npm --prefix 3_frontend run lint` y `npm --prefix 3_frontend run build`: sin advertencias.
- `./init.sh` completo, primera corrida: **rojo**. 1493 unitarias verdes; frontend (lint, build, tokens, librerías) OK;
  IT 893/894 (las cifras 1615/907 que anoté antes sumaban informes viejos de `target/`; el reviewer confirmó 1493/894):
  falla `MisReservasControllerIT.deSusEspaciosEnCualquierEstado` (HU-14, existente) con 404 en vez de 200. Todos los IT de
  HU-21 verdes (`EstadoEspacioControllerIT` 18, `EspacioAdminControllerIT` 7, `BajaEspacioConReservasIT` 8,
  `BajaEspacioConcurrenciaIT` 2).

## Bloqueo

HU-21 R21 (404 si `espacioId` es ajeno en `GET /api/arrendadores/actual/reservas`) choca con HU-14 R7 ("la ruta ignora
cualquier parámetro distinto de `grupo` y `pagina`, como … `espacioId`") y con su test
`MisReservasControllerIT.deSusEspaciosEnCualquierEstado` (línea 383), que envía `&espacioId=<ajeno>` y espera 200. El
spec de HU-21 dice que ningún test existente cambia, así que no lo modifiqué. Opciones y recomendación en
`progress/HU-21.md` § "Bloqueo". La opción recomendada (ajustar solo esa línea del test de HU-14 y documentarlo en el
`design.md` de HU-21) no cambia nada del código ya hecho.

## Desvíos del spec y notas

- Ninguna decisión de diseño nueva. Detalles de ejecución:
  - `EstadoEspacioControllerIT` siembra un horario semanal por JDBC para el espacio: la búsqueda pública
    `GET /api/espacios` exige `fecha` y filtra por bloques libres, así que sin horario el espacio no aparecería ni
    estando `PUBLICADO` (R2 se comprueba contra un "antes" visible).
  - `EstadoEspacioServiceTest` usa un `EspacioService` real construido con sus dependencias simuladas (Mockito), para
    probar la habilitación de verdad (R25, R26) sin mockear la clase probada.
  - Al extraer `TarjetaReservaDeMiEspacio`, las clases CSS de la tarjeta (`datos`, `monto`, `acciones`) se movieron de
    `ReservasDeMisEspacios.module.css` a `TarjetaReservaDeMiEspacio.module.css`; mismo marcado, sin cambio visible.
  - `EstadoPublicacion` remonta `ReservasAfectadas` (contador como `key`) en cada nuevo rechazo por reservas, para que la
    lista refleje el estado actual.
- T20 sin cerrar por el bloqueo; las demás tasks completas.

## Desbloqueo (opción 1)

- 2026-11-04: Sebastian Garate (dueño) eligió la opción 1: HU-21 R21 deja sin efecto la parte de HU-14 R7 sobre
  `espacioId` solo en `GET /api/arrendadores/actual/reservas`; la ruta del usuario (`GET /api/reservas`) sigue
  ignorándolo. El spec-author lo documenta en `specs/HU-21/design.md`.
- Cambio único: `2_Backend/src/test/java/pe/edu/ulima/espacioya/reservas/controller/MisReservasControllerIT.java`, línea
  383 (`deSusEspaciosEnCualquierEstado`): la petición pasa de `?grupo=proximas&arrendadorId=<otro>&espacioId=<ajeno>` a
  `?grupo=proximas&arrendadorId=<otro>`; sigue comprobando que `arrendadorId` se ignora. El 404 con `espacioId` ajeno ya
  lo cubren `ConsultaReservasServiceTest.espacioIdAjenoDa404` y `BajaEspacioConReservasIT.filtroAjenoOInvalido`. Sin
  cambios en código de HU-21.
- `./init.sh` completo: **exit 0, "Entorno listo"**. Backend: 1493 unitarias y 894 IT, 0 fallos y 0 errores. Frontend:
  ESLint sin errores ni advertencias y `npm run build` OK. Sin colores fuera de tokens ni librerías de componentes.
- T20 `[x]`: las 20 tasks están completas. La historia queda `in_progress` a la espera del reviewer (sin `done`, sin
  commit).
