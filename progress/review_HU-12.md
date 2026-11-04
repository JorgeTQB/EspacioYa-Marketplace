# Review — HU-12

**Veredicto:** APPROVED (vuelta 1; la vuelta 0 fue CHANGES_REQUESTED, ver abajo)

> Revisado el 2026-10-30 sobre la rama `feature/HU-12-reserva-bloqueo-temporal` (cambios sin commitear;
> `develop` = `HEAD` = `a8234a6`). El backend cumple el spec y la concurrencia está bien resuelta: no encontré ningún
> defecto en doble reserva, expiración, orden de bloqueos, cupo, monto, identidad ni propiedad. Se rechaza por **un
> defecto real de pantalla en CA2** (el contador se reinicia en vez de mostrar "Expirada") y dos arreglos menores.
> Ver "Cambios requeridos". Con esos tres arreglos se puede aprobar sin otra revisión del backend.

## `./init.sh` completo (sin `--rapido`)
- Exit 0, `[OK] Entorno listo`.
- Backend `./mvnw verify`: **630 unitarias** y **431 IT**, 0 fallos, 0 errores, 0 omitidas.
- Reglas de arquitectura (paquetes, sin repositorio en controladores, sin `@Autowired`, sin Lombok, sin comodines, sin
  prints, sin colores fuera de tokens, sin librerías de UI, sin secretos): todas `[OK]`.
- Frontend: ESLint sin errores ni advertencias; `npm run build` compila.
- Único `[WARN]`: faltan llaves locales del backend. No afecta a las pruebas, ajeno a la historia.

## Trazabilidad requirements ↔ tests
RCIT = `ReservaControllerIT`, CIT = `ReservaConcurrenciaIT`, SIT = `ReservaServiceImplTest`, ERT = `ExpiracionReservasTest`.
Leí estas clases completas; el mapa de `progress/impl_HU-12.md` coincide con los `@DisplayName` reales.

- R1: [x] `RCIT.creaLaReservaConSuDto` (claves exactas, horas en desorden, 900 s), `RCIT.reservaTodoElHorario`, `SIT.creaLaReserva`
- R2: [x] `RCIT.guardaLaFilaDeReserva`, `ReservaTest.naceConLaRetencionDeQuinceMinutos`
- R3: [x] `RCIT.guardaPrecioYMonto` (241.50), `RCIT.elPrecioQuedaCopiado`, `SIT.elMontoSaleDelServidor`
- R4: [x] `RCIT.guardaLosBloques`, `SIT.guardaUnBloquePorHora`
- R5: [x] `RCIT.ignoraLoQueDecideElServidor`, `CrearReservaDTOTest.ignoraCamposDesconocidos`
- R6: [x] `ReservasDelEspacioImplIT.vigenteYConfirmada`, `.todasLasFilasActivas`, `.reservasNoActivas`, `.otroEspacio`, `.extremosDelRango`
- R7: [x] `ReservasDelEspacioImplTest.loteConUnaConsulta`, `.loteVacioNoConsulta`, `ReservasDelEspacioImplIT.loteContraLaBase`
- R8: [x] `RCIT.laRetencionSaleDeLaRutaPublica`, `RCIT.unaConfirmadaNoSeOfrece`
- R9: [x] `RCIT.expiraALosQuinceMinutos` (14:59 no, 15:00 sí), `ERT.expiraYDesactiva`
- R10: [x] `RCIT.expiraALosQuinceMinutos`, `ERT.expiraYDesactiva`
- R11: [x] `RCIT.noTocaVigentesNiConfirmadas`, `ERT.sinVencidas`, `ReservaTest.noExpiraOtrosEstados`
- R12: [x] `ReservasDelEspacioImplIT.vencidaSinProcesar`, `RCIT.vencidaSinProcesarNoOcupa`
- R13: [x] `ProgramacionExpiracionTest.activaEjecutaElProceso`, `.valoresPorDefecto`
- R14: [x] `ProgramacionExpiracionTest.inactivaNoPrograma`
- R15: [x] `ERT.unFalloNoDetieneLasDemas`
- R16: [x] `ERT.yaNoVencidaTrasElBloqueo` (vigente, CONFIRMADA, EXPIRADA, inexistente)
- R17: [x] `RCIT.reservaUnBloqueVencidoSinProcesar` (trampa del `flush`, contra PostgreSQL real), `ERT.liberaLasVencidasQueChocan`
- R18: [x] `RCIT.bloqueRetenido`, `.bloqueConfirmado`, `.fechaBloqueada`, `.horaYaEmpezada`, `.horaFueraDeHorario`
- R19: [x] `CIT.dosUsuariosElMismoBloque` (hilos reales, 5 rondas: 201 + 400 con el mensaje, una fila activa), `CIT.coincidenEnUnBloqueDeVarios`
- R20: [x] `RCIT.indiceImpideDosActivas`
- R21: [x] `RCIT.indiceIgnoraLasInactivas`
- R22: [x] `SIT.violacionDelIndiceUnico` (Mockito; ver observación 1)
- R23: [x] `SIT.otraViolacionSeRelanza`
- R24: [x] `CIT.reservaYBloqueoDeFecha` (5 rondas: 201 + 400 y solo uno queda)
- R25: [x] `CIT.exigenUnaTransaccion` (por el proxy real)
- R26: [x] `RCIT.cupoSuperado`, `SIT.cupoSuperado`
- R27: [x] `RCIT.soloCuentanLasVigentesPropias`
- R28: [x] `CIT.elCupoSeSerializaPorUsuario` (5 rondas: 201 + 400 *cupo*, 3 pendientes)
- R29: [x] `RCIT.espacioInvalido`, `CrearReservaDTOTest.rechazaEspacioInvalido`
- R30: [x] `RCIT.fechaInvalida`, `CrearReservaDTOTest.rechazaFechaInvalida`
- R31: [x] `RCIT.fechaFueraDeVentana`, `SIT.extremosDeLaVentana`
- R32: [x] `RCIT.horasInvalidas`, `SeleccionBloquesTest.rechazaSeleccionInvalida`
- R33: [x] `RCIT.espacioInexistente`, `.espacioNoPublicado` (4 estados), `.arrendadorSuspendido`
- R34: [x] `RCIT.formaAntesQueRol`, `RCIT.ordenDeLasComprobaciones`, `SIT.cupoAntesQueEspacio`
- R35: [x] `RCIT.sinSesion`, `RCIT.tokenInvalido`
- R36: [x] `RCIT.rolSinPermiso` (ARRENDADOR y ADMIN), `SIT.rolDistintoDeUsuario`
- R37: [x] `RCIT.consultaSuReserva`, `.horarioDeUnaExpirada`, `.nombreDeUnEspacioNoVisible`
- R38: [x] `RCIT.ajenaInexistenteYMalEscrita` (cuerpos iguales), `SIT.idMalEscrito`, `SIT.ajenaOInexistente`
- R39: [x] `RCIT.vencidaSeLeeExpiradaSinEscribir`
- R40: [x] `RCIT.segundosRestantes`, `ReservaTest.segundosRestantes`
- R41: [x] `RCIT.noExponeDatosDePersonas`, `SIT.elDtoNoExponeDatosDePersonas`
- R42: [x] `RCIT.auditaLaCreacion` (fila exacta), `SIT.auditaLaCreacion`
- R43: [x] `RCIT.elProcesoAuditaLaExpiracion`, `RCIT.reservaUnBloqueVencidoSinProcesar` (capa 3), `ERT.auditaLaExpiracion`
- R44: [x] `RCIT.losRechazosNoSeAuditan`, `RCIT.rolSinPermiso`, `SIT.violacionDelIndiceUnico`
- R45: [x] `RCIT.checkDeEstado`, `.checkDeMontos`, `.checkDeVencimiento`, `.checkDeHora`

## Trazabilidad con la historia
- CA1 (bloques, monto en servidor, retención 15 min): [x] R1–R8, R29–R34, R37, R40
- CA2 (a los 15 minutos expira y libera): [x] en la API (R9–R17, R21, R39). **[ ] en la pantalla** ← T20 dice "al llegar a cero relee del servidor y muestra `EXPIRADA`", y lo normal es que no ocurra (cambio requerido 1)
- CA3 (solo uno gana; el otro ve "horario no disponible"): [x] R18–R25; `FichaEspacio.jsx:116-123`
- CA4 (login y vuelta a la ficha): [x] R35; `FichaEspacio.jsx:105-110`, `IniciarSesion.jsx:20,27,38`
- (S/E) A nombre de la sesión: [x] R2, R5, R36, R38, R41. `ReservaController.java:34-36`, `ReservaServiceImpl.java:119-127`
- (T) Restricción única en BD: [x] R20–R22, R45. `V3_3__reservas.sql:29`
- (D) Máximo 3 pendientes: [x] R26–R28
- (R) Registro de creación y expiración: [x] R42–R44
- La tabla final de `requirements.md:273-295` cubre todos los CA y los cuatro controles STRIDE.

## Tasks completas
- T1–T19: [x] en `specs/HU-12/tasks.md` y comprobadas contra el código.
- T20: [x] en `tasks.md`, pero **no se cumple** su "CA2 (al llegar a cero relee del servidor y muestra `EXPIRADA`)": ver cambio requerido 1.
- T21–T23: [x]

## Foco pedido por el orquestador
- **Doble reserva:** [x] índice parcial `WHERE activo` (`V3_3__reservas.sql:29`), IT con dos hilos reales y conexiones propias, un 201 y un 400, una fila activa. La traducción a *horario no disponible* (`ReservaServiceImpl.java:94-98, 172-175`) reconoce el nombre de la restricción y relanza lo demás.
- **Expiración en tres capas:** [x] capa 1 en las tres lecturas (`ReservaBloqueRepository.java:29-31, 39-41`, `ReservaRepository.java:25`, `Reserva.estadoEn`); capa 2 con una transacción por reserva y apagada en tests (`src/test/resources/config/application.properties`); capa 3 bajo el bloqueo del espacio. `flushAutomatically` en `ReservaBloqueRepository.java:21`, cubierto por una IT real. Auditoría con `usuario_id` e `ip` nulos, comprobada en la fila (S9).
- **Job y creación concurrentes:** [x] por análisis, sin prueba concurrente. Si el job tiene la fila, `findVencidasQueRetienen` espera y PostgreSQL revalida `estado` al desbloquear, así que la descarta. Si la tiene `crear`, el job relee con `findParaActualizar` en una sesión nueva y `estaVencida` da `false` (`ExpiracionReservas.java:74-78`). En ningún caso se audita dos veces.
- **Orden `usuario → espacio → reserva`:** [x] `ReservaServiceImpl.java:119-125`, verificado con `InOrder` en `SIT.ordenDeLaTransaccion`. Revisé todos los `findParaActualizar` de `src/main`: nadie toma `usuario` después de `espacio`, y el job solo toma una `reserva` y nunca `espacio`. Sin riesgo de interbloqueo.
- **Cupo bajo concurrencia:** [x] `MANDATORY` en `CuentaServiceImpl.java:227`; el conteo va después del bloqueo; cada sentencia ve lo confirmado (READ COMMITTED).
- **Monto:** [x] sale de `EspacioRetenido`, leído bajo el bloqueo como primera lectura del espacio en la transacción. Nada del cliente llega al cálculo.
- **`GET` ajeno:** [x] 404 con `findByIdAndUsuarioId`; `RCIT.ajenaInexistenteYMalEscrita` compara los cuerpos.
- **Solo rol `USUARIO`:** [x] `ReservaServiceImpl.java:84-86`; la ficha no ofrece el botón a `ARRENDADOR` ni `ADMIN`.
- **Arrendador no activo y espacio no visible:** [x] `DisponibilidadServiceImpl.java:126-129`, con 404 en IT.
- **HU-08 DT-10:** [x] `findParaReservar` toma el mismo bloqueo que `CalendarioEspacioService.java:165`, y `bloquear` relee el puerto bajo ese bloqueo (`:129`).
- **CA4 y S3, redirección abierta:** [x] `state.retorno` no llega por URL. El único valor no armado por la app es `pathname + search` de `ProveedorSesion.jsx:39-40`, de mismo origen. El caso límite de una ruta que empiece con doble barra no redirige: `navegar` usa `replace` y `history.replaceState` (`react-router 7.18.4`, `chunk-OB3PAWPO.mjs:329`) lanza `SecurityError` con otro origen (observación 2). Las dos salidas de `IniciarSesion` usan `destino`; sin `retorno` sigue yendo a `/panel`.
- **S10, `@Primary`:** [x] en `DisponibilidadServiceImpl` el doble gana con `getIfAvailable`, de modo que `BusquedaControllerIT`, `FichaControllerIT` y `EspaciosControllerSoporte` prueban lo mismo. **Excepción:** `DisponibilidadServiceImplIT` no usa doble y cambió de sentido (cambio requerido 2).

## Sin regresiones en HU-03, HU-08, HU-09, HU-10 y HU-11
- [x] `CuentaService`, `CatalogoEspaciosService`, `DisponibilidadService` y `EspacioRepository` solo agregan métodos, según su diff.
- [x] `CalendarioEspacioControllerIT.calculaAlConsultar`: cambio documentado (`impl_HU-12.md`, nota 1) y legítimo; `reserva_bloque` guarda retenciones, y la prueba sigue exigiendo que los bloques libres no se persistan.
- [x] `bloquesReservadosDelDia` real conserva la firma del puerto (HU-10 DT-6).
- [x] `FichaEspacio.jsx`: con rol distinto de `USUARIO` queda de solo lectura, como en HU-11.
- [x] HU-03: `retorno` es el único cambio, declarado en S3. Avisos a los dueños en `progress/HU-12.md` (T21).

## Checkpoints
- C1 (arnés completo, `./init.sh` exit 0): [x]
- C2 una `in_progress` por dueño y `progress/HU-12.md` existe: [x]
- C2 historias `done` con tests verdes: n/a (HU-12 aún no es `done`)
- C2 aprobación del dueño anotada: [x] `progress/HU-12.md:14, 40`
- C2 `progress/HU-12.md` solo con la sesión activa: [x]
- C3 paquetes, controladores sin repositorios, sin `@Autowired`, Lombok, comodines ni prints: [x]
- C3 dependencias entre módulos solo vía service o puerto: [x] `reservas` usa `CuentaService`, `DisponibilidadService` y `CatalogoEspaciosService`, e implementa `ReservasDelEspacio`. Ningún puerto nuevo.
- C3 DTO `record`, sin entidades ni campos sensibles: [x]
- C3 identidad de la sesión, montos y estados en el servidor: [x]
- C3 migraciones: [x] `V3_3` libre en `develop`, ninguna editada
- C3 adapters para integraciones externas: n/a
- C3 nada fuera del alcance: [x]
- C3 sin secretos, sin colores fuera de tokens ni librerías de UI: [x]
- C3 frontend con el kit, mensajes según `docs/frontend.md`, API solo desde `src/api/`: [x]
- C4 nombres `*Test` e `*IT` con `@Import(TestcontainersConfiguration.class)`: [x]
- C4 sin H2: [x]
- C4 plazos con `Clock` fijo: [x] `Clock.fixed` y `RelojControlable`
- C4 IT para endpoints y restricciones, concurrencia con operaciones a la vez: [x]
- C4 resultados concretos, incluido el camino de error: [x], con la salvedad de la etiqueta del cambio requerido 2
- C4 `mvnw verify` > 0 tests y verde; lint y build: [x]
- C5 sin archivos sueltos sospechosos: [x]
- C5 `progress/history.md` con entrada de la sesión: n/a hasta el cierre
- C5 `progress/HU-12.md` vuelto a plantilla: n/a (historia no terminada)
- C5 estado en `feature_list.json`: [x] `in_progress`
- C5 rama de la historia: [x]
- C6 carpeta `specs/HU-12/` completa: [x]
- C6 EARS y tabla de trazabilidad: [x]
- C6 `design.md` con endpoints, clases, migraciones, secuencias, Pantallas y alternativas: [x]; patrón ★: n/a
- C6 todas las tasks `[x]`: [ ] ← T20 está marcada pero no cumple su CA2 visible
- C6 cada R-n con test documentado en `impl_HU-12.md`: [x]
- C6 ningún R-n resuelve un pendiente §9 sin decisión del dueño: [x] §9-8 lo cerró D2

## Cambios requeridos
1. **CA2 en pantalla: el contador se reinicia en lugar de mostrar "Expirada".**
   `3_frontend/src/pages/reservas/ResumenReserva.jsx:92` usa `key={recarga}`, y `recarga` cambia al **pedir** la relectura
   (`:45-47`), no al recibirla. `CuentaRegresiva.jsx:18` fija `fin` una sola vez, al montarse. Secuencia:
   - El servidor da segundos enteros hacia abajo (`Reserva.java:81`): una reserva recién creada llega con 899, no 900.
   - El contador llega a cero casi 1 s antes del vencimiento real. Con latencias que sumen menos de 1 s, que es el caso
     normal, la relectura encuentra la reserva aún vigente: `PENDIENTE_PAGO` con `segundosRestantes = 0`.
   - `recargar()` ya cambió la `key`, así que el contador se volvió a montar con los segundos **viejos** (899). La
     respuesta nueva no lo remonta porque la `key` no cambia, y `segundos = 0` se ignora.
   - Resultado: la pantalla muestra "Pendiente de pago" y un contador de unos 15:00 durante otros 15 minutos, cuando el
     servidor ya liberó los bloques. El usuario cree que los conserva.

   Además, aunque la relectura responda `EXPIRADA`, mientras espera se ve un instante el tiempo viejo. Corregir para que
   el contador se reinicie con la **respuesta** y no con la petición (por ejemplo, una `key` que cambie al guardar la
   respuesta), de modo que con `segundos = 0` vuelva a leer enseguida y termine en `EXPIRADA`. Comprobarlo en el
   navegador. Conclusión obtenida leyendo el código y calculando los tiempos; no lo ejecuté en un navegador, y el
   frontend no tiene pruebas que lo cubran.
2. **`2_Backend/src/test/java/pe/edu/ulima/espacioya/espacios/service/DisponibilidadServiceImplIT.java:79`**: el
   `@DisplayName` dice "HU-08 R21, R30: … sin el puerto de reservas", y desde HU-12 esta prueba usa el bean real
   (el comentario de la clase, línea 33, ya lo dice). Ya no verifica R21, que sigue cubierto en
   `DisponibilidadServiceImplTest.java:102`. Quitar R21 y corregir el texto. Corregir también el comentario obsoleto de
   `busqueda/controller/FichaControllerIT.java:52` ("HU-12 aún no existe"), que `impl_HU-12.md` (nota 6) dejó anotado.
3. **Líneas de más de 120 caracteres** (`docs/conventions.md`), todas de esta historia:
   `reservas/domain/SeleccionBloques.java:16` (código de producción); `reservas/service/ExpiracionReservasTest.java:126`,
   `reservas/dto/CrearReservaDTOTest.java:122`, `reservas/domain/ReservaTest.java:35`,
   `reservas/controller/ReservaConcurrenciaIT.java:67` y `reservas/controller/ReservaControllerIT.java:424, 627`.

Tras estos cambios: `./init.sh` completo y la vuelta 1 puede limitarse a verificar los tres puntos.

## Observaciones (no bloquean)
1. **R22 solo se prueba con Mockito** y un `SQLException` armado a mano, y es el primer uso de `getMostSpecificCause` en
   `src/main`. Por lectura del código la traducción es correcta, pero **no la vi funcionar de punta a punta**: bajo el
   bloqueo del espacio el camino normal nunca llega al índice. Una IT barata la cubriría con PostgreSQL real: insertar
   una reserva `CANCELADA` con su bloque `activo = true` (no ocupa para el puerto ni la libera `liberarVencidas`) y
   hacer `POST` de esa hora: debe dar 400 *horario no disponible*.
2. `IniciarSesion.jsx:20` acepta `state.retorno` sin validarlo. No es una redirección abierta, pero si la URL actual
   tiene una ruta con doble barra inicial y la sesión caduca en esa misma pestaña, `replaceState` lanzaría
   `SecurityError` y la pantalla podría quedar en blanco. Exigir que empiece con una sola barra lo evita.
3. `ReservaServiceImpl.java:120` toma `ahora` antes de esperar el bloqueo del espacio, y el puerto usa su propio instante.
   Si una reserva vence justo entre ambos, el resultado es un 400 falso de horario no disponible (el índice lo ataja).
   Es seguro y dura milisegundos.
4. La carrera entre job y creación (ver "Foco") está razonada, no probada con hilos. El spec no lo exige (R16 es Mockito).
5. `impl_HU-12.md` nota 5: `RutaProtegida` no guarda retorno. Queda fuera de S3 y está documentado.

## Vuelta 1

**Veredicto:** APPROVED

Revisado el 2026-10-30 sobre la misma rama (cambios sin commitear), contra `progress/impl_HU-12.md`, "Vuelta 1 del review".
Se verifican los tres cambios requeridos, las tres observaciones aplicadas y `./init.sh` completo.

1. Cambio 1, contador (CA2): [x] `ResumenReserva.jsx:95` usa `key={actual.lectura}`, y `lectura` sube solo al guardar una
   respuesta correcta (`:35`). Si la relectura trae `PENDIENTE_PAGO` con 0 segundos, el contador se vuelve a montar con
   `segundos = 0`, espera su primer tic de 1 s y lee otra vez. Esa lectura ya responde `EXPIRADA`: `segundosRestantes = 0`
   significa que quedaba menos de 1 s, y ya pasó más de 1 s. Sin bucle: como mucho una lectura extra. Mientras la
   petición viaja, el contador queda en `00:00` y no muestra el tiempo viejo. El reinicio tras un error de red no aplica:
   la pantalla pasa a la `Alerta`. T20 y su CA2 quedan cumplidos por lectura del código y cálculo de tiempos.
2. Cambio 2: [x] `DisponibilidadServiceImplIT.java:79` ya no cita R21 y dice "con el puerto real sin reservas"; R21 sigue
   en `DisponibilidadServiceImplTest.java:102`. Comentario de `FichaControllerIT.java:52` corregido.
3. Cambio 3: [x] ningún `.java` de `reservas` pasa de 120 caracteres. Quedan 3 líneas en `LimiteFichaIT`,
   `LimiteCatalogoIT` y `FichaControllerIT`, de historias anteriores (ya estaban en la vuelta 0).
4. Observación 1, R22: [x] `RCIT.violacionRealDelIndice` recorre el camino real con PostgreSQL. Confirma antes que la hora
   figura libre, de modo que el rechazo solo puede venir del índice, y comprueba 400 *horario no disponible* sin campo,
   ninguna fila nueva (también se revierte la hora 13) y sin `RESERVA_CREADA`.
5. Observación 2, `retornoValido`: [x] lo ejecuté con Node. Acepta `/espacios/5?fecha=2026-11-04`, `/panel`, `/` y
   `/reservas/7`; rechaza una ruta con doble barra inicial, una con barra invertida tras la primera, una URL absoluta,
   texto sin barra, `undefined`, `null` y un número.
   - CA4: [x] la ficha arma `/espacios/<id>?fecha=…`, que pasa la validación; las dos salidas usan `destino`
     (`IniciarSesion.jsx:21, 28, 39`).
   - HU-03: [x] sin `retorno` o con uno rechazado, `destino` sigue siendo `/panel`. En `ProveedorSesion.jsx:41`, desde el
     login o con una ruta rechazada, el `state` lleva `retorno: null`, y `mensaje` y `tipo` no cambian.
   - `docs/frontend.md:233` documenta `sesion/retorno.js`.
6. Observación 3: [x] `ReservaServiceImpl.java:119-127`: `bloquearCuenta` → cupo con su propia lectura del reloj →
   `retenerSiLibres` → `ahora` → `liberarVencidas` → `save`. **El orden `usuario → espacio → reserva` no cambia**: solo se
   movió una lectura del reloj, sin bloqueos nuevos. El cupo sigue antes del espacio (R34). Una reserva que vence entre
   la consulta del puerto y `ahora` se rechaza como ocupada, lo cual es correcto porque seguía vigente al consultar. R2
   se cumple: `creada_en` es *ahora* y `expira_en` son 15 minutos después. Lo cubre
   `SIT.elInstanteSeTomaTrasRetenerElEspacio`, con un reloj que avanza durante `retenerSiLibres`. La desviación de
   `design.md` § 6 está justificada y anotada como aprobada por el dueño (`progress/HU-12.md:65-67`).
7. `./init.sh` completo (sin `--rapido`): [x] exit 0, `[OK] Entorno listo`. Backend: **631 unitarias y 432 IT**, 0 fallos,
   0 errores, 0 omitidas (una más de cada tipo que en la vuelta 0). Reglas de arquitectura en `[OK]`. ESLint limpio y
   `npm run build` compila. Único `[WARN]`, las llaves locales, ajeno a la historia.
8. Sin otros cambios: [x] `git status --short` agrega respecto de la vuelta 0 solo `3_frontend/src/sesion/retorno.js`
   (nuevo) y `FichaControllerIT.java` (comentario). Limitación: no releí el resto del código, y los archivos sin
   commitear no tienen una copia previa para comparar línea a línea.

**Checkpoints:** C6 "todas las tasks `[x]`" pasa a [x]; el resto, sin cambios respecto de la vuelta 0, en `[x]` o `n/a`.
R1–R45, CA1–CA4, los cuatro controles STRIDE y T1–T23: cubiertos. No quedan cambios requeridos.

**Pendiente, no bloquea (acordado con el orquestador):** el arreglo del contador no se ha visto funcionar en un navegador.
El dueño tiene los pasos en `progress/impl_HU-12.md`, "Pendiente para el dueño". Revisé que el contenedor, el usuario y la
base del paso 3 coinciden con `1_Database/compose.yaml` y que el `UPDATE` respeta `ck_reserva_expira`. Si el contador salta
a un tiempo mayor, la historia vuelve a `in_progress`.

La carrera entre el job y la creación sigue razonada, no probada con hilos (observación 4 de la vuelta 0).
