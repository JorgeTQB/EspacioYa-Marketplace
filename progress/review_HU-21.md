# Review — HU-21

**Veredicto:** APPROVED

> reviewer, 2026-11-04 · rama `feature/HU-21-pausar-baja-publicacion` (cambios sin commitear) · dueño: Sebastian Garate.
> `./init.sh` completo (sin `--rapido`) desde la raíz: **exit 0, "Entorno listo"**. Backend `./mvnw verify`:
> **1493 unitarias + 894 IT, 0 fallos, 0 errores, 0 omitidas**. ESLint sin errores ni advertencias; `npm run build` OK;
> sin colores fuera de tokens, sin librerías de componentes, sin secretos.

## Discrepancia en el conteo de tests (1615/907 vs 1493/894)

No se excluyó ni se borró ningún test. Lo comprobé así:
- Mi corrida da exactamente **1493 unitarias y 894 IT**, con `Skipped: 0`.
- Cuadra con la línea base: al cerrar HU-20 (`progress/impl_HU-20.md`, línea 165) había 1438 unitarias + 859 IT (también
  1438 en el `--rapido` del arranque de HU-21, `progress/HU-21.md` línea 25). HU-21 agrega **55 unitarias** (`EspacioTest`
  7 + `BloqueReservadoTest` 4 + `EstadoEspacioServiceTest` 31 + `ConsultaReservasServiceTest` 6 `@Test` y 1
  `@ParameterizedTest` con 7 valores = 13) → 1438 + 55 = **1493**; y **35 IT** (`EstadoEspacioControllerIT` 18 +
  `EspacioAdminControllerIT` 7 + `BajaEspacioConReservasIT` 8 + `BajaEspacioConcurrenciaIT` 2) → 859 + 35 = **894**.
- `git status`: ningún archivo de test borrado (solo `M` y `??`); `2_Backend/src/test/java` pasa de 192 archivos
  trackeados a 198 en disco (los 6 nuevos). Ni `pom.xml` ni `init.sh` cambiaron. Ningún `@Disabled` ni `@Tag` en el
  árbol de tests.
- Conclusión: la cifra de la **primera** corrida (1615/907) era errónea (no cuadra con ninguna línea base ni con los tests
  agregados); la segunda es la correcta. No es un cambio requerido.

## Trazabilidad requirements ↔ tests

84 tests con `@DisplayName("HU-21 R<n>: …")` (8 clases). Todos comprueban resultados concretos (código HTTP, mensaje,
campo `errores[0].campo`, estado y `actualizado_en` en la fila, filas de `auditoria` con detalle exacto).
Abreviaturas: EET `EspacioTest`, BRT `BloqueReservadoTest`, EEST `EstadoEspacioServiceTest`, EECIT
`EstadoEspacioControllerIT`, EACIT `EspacioAdminControllerIT`, CRST `ConsultaReservasServiceTest`, BECRIT
`BajaEspacioConReservasIT`, BECIT `BajaEspacioConcurrenciaIT`.

- R1: [x] `EECIT.pausaUnPublicado` (200, `estado=PAUSADO`, fila), `EEST.pausaUnPublicado`
- R2: [x] `EECIT.pausadoNoSeVeNiSeReserva` (búsqueda 1 → 0 resultados), `EECIT.bajaNoSeVeNiSeReserva`
- R3: [x] mismos dos tests (ficha pública 404 con mensaje genérico)
- R4: [x] mismos dos tests (`POST /api/reservas` 404; 0 filas en `reserva` y `reserva_bloque`)
- R5: [x] `BECRIT.pausaConservaReservas` (estados y bloques idénticos antes/después, 3 bloques activos)
- R6: [x] `EET.pausarFueraDePublicadoLanza`, `EEST.pausarFueraDePublicadoRechaza`, `EECIT.pausarFueraDePublicado`, `EACIT.adminEstadoInvalido`
- R7: [x] `EECIT.reanudaUnPausado` (200 `PUBLICADO`, vuelve a la búsqueda), `EEST.reanudaUnPausado`
- R8: [x] `EECIT.reanudarFueraDePausado`, `EET.reanudarFueraDePausadoLanza`, `EEST`
- R9: [x] `EECIT.reanudarSinIdentidad` (`EN_REVISION` → mensaje de HU-07), `EEST.reanudarSinIdentidadRechaza`
- R10: [x] `EECIT.reanudarSinFotos`, `EEST.reanudarSinFotosRechaza`
- R11: [x] `EECIT.reanudarOrdenEstadoPrimero`, `EECIT.reanudarSinIdentidad`, `EEST.reanudarOrdenDeReglas`
- R12: [x] `EECIT.daDeBajaPublicadoYPausado`, `EEST.daDeBajaSinReservas`, `EEST.bajaSinPuertoSeCompleta`
- R13: [x] `EECIT.bajaConReservaFutura` (400 campo `reservas`, mensaje exacto), `BECRIT.confirmadaFuturaImpideLaBajaDePublicado` (fila del espacio idéntica), `BRT`
- R14: [x] `BECRIT.reservaEnCursoImpideLaBaja`, `BECRIT.pendienteVigenteImpideLaBaja`, `BRT.bloqueEnCursoCuenta`, `EEST`
- R15: [x] `BECRIT.reservasQueNoImpidenLaBaja` (pendiente vencida, EXPIRADA, CANCELADA, CONFIRMADA terminada → 200 `BAJA`), `BRT.bloqueQueTerminaAhoraNoCuenta`
- R16: [x] `EECIT.bajaDeEstadoNoAdmitido`, `EEST.bajaDeEstadoNoAdmitidoRechazaSinConsultarReservas`, `EACIT.adminEstadoInvalido`, `EET.darDeBajaNoAdmitidaLanza`
- R17: [x] `BECIT.bajaYReservaALaVez` (6 rondas con `CountDownLatch`), `BECIT.losDosOrdenes`, `EEST.reservasSeLeenTrasElBloqueo` (`inOrder`: bloqueo antes del puerto)
- R18: [x] `EECIT.bajaNoSeEdita` (400 "Este espacio ya no se puede editar.", nombre sin cambio)
- R19: [x] `CRST.filtraPorEspacioPropio`, `BECRIT.filtroSoloDeEseEspacio`, `BECRIT.bajaConReservaFuturaCanceladaAlCien`
- R20: [x] `CRST.espacioIdInvalido` (7 valores, junto a `grupo` y `pagina`), `CRST.espacioIdMaximo`, `BECRIT.filtroAjenoOInvalido`
- R21: [x] `CRST.espacioIdAjenoDa404`, `CRST.espacioIdSinEspaciosDa404`, `BECRIT.filtroAjenoOInvalido` (404 con mensaje genérico)
- R22: [x] `CRST.sinEspacioIdIgualQueHu14` + los IT de HU-14 en verde
- R23: [x] `BECRIT.bajaConReservaFuturaCanceladaAlCien` (política `ARRENDADOR`, 100 %, `montoReembolso` 161.0, `CANCELADA`)
- R24: [x] mismo test (la baja siguiente → 200 `BAJA`, una auditoría `estado=PAUSADO->BAJA`)
- R25: [x] `EECIT.usuarioRecibe403` (3 rutas), `EEST.otroRolRecibe403`
- R26: [x] `EECIT.sinCorreoVerificadoRecibe403`, `EEST.sinCorreoVerificadoRecibe403`
- R27: [x] `EECIT.ajenoOInexistenteDa404` (3 rutas ajeno + inexistente, sin cambio de `actualizado_en`)
- R28: [x] `EACIT.adminPausa` (`containsOnlyKeys("id","estado")`), `EEST.adminPausa`
- R29: [x] `EACIT.adminDaDeBaja`, `EEST.adminDaDeBaja`
- R30: [x] `EACIT.adminBajaConReservas` (campo `reservas`, mensaje admin), `EEST.adminBajaConReservasRechaza`
- R31: [x] `EACIT.adminInexistente`, `EEST.adminInexistenteDa404`
- R32: [x] `EACIT.sinRolAdmin` (ARRENDADOR dueño y USUARIO → 403), `EEST.adminSinRolRecibe403`
- R33: [x] `EACIT.ipNoPermitida` (403, sin cambio ni auditoría)
- R34: [x] `EECIT.pausaUnPublicado` (entidad, id, usuario, IP, detalle exactos), `EEST.auditaPausa`
- R35: [x] `EECIT.reanudaUnPausado`, `EEST.auditaReanudacion`
- R36: [x] `EECIT.daDeBajaPublicadoYPausado` (`PUBLICADO->BAJA`, `PAUSADO->BAJA`), `EEST.auditaBaja`
- R37: [x] `EACIT.adminPausa`, `EACIT.adminDaDeBaja` (`;actor=ADMIN;arrendadorId=<dueño>`), `EEST.auditaActorAdmin`
- R38: [x] `EEST.rechazosNoAuditan`, `EECIT.pausarFueraDePublicado`, `EECIT.bajaConReservaFutura`, `EECIT.ajenoOInexistenteDa404`, `EECIT.sinSesionRecibe401`
- R39: [x] `EET.pausarPublicado`/`reanudarPausado`/`darDeBajaPublicadoOPausado`, `EECIT` (`actualizado_en == reloj.instant()`), `EEST.fijaActualizadoEn`
- R40: [x] `EECIT.listadoIncluyePausadoYBaja`

## Trazabilidad con la historia

- CA1 (pausar → deja de aparecer; reservas se mantienen): [x] R1–R6, R23 · reanudar R7–R11
- CA2 (baja con reservas futuras → ver afectadas → cancelar 100 % → baja): [x] R12–R24, R40
- (E) Solo el dueño o un admin, validado en backend: [x] R25–R33. Implementado en `EstadoEspacioService`
  (`espacioService.habilitacion` + `findParaActualizar(id, arrendadorId)` líneas 70–71, 158–160; `exigirAdmin` antes de
  leer, líneas 106, 114, 166–170) y `FiltroIpsAdmin` para `/api/admin/**`. Identidad siempre desde `@AuthenticationPrincipal`.
- (R) Registro de pausas y bajas: [x] R34–R39. `auditar(...)` en la misma transacción (`EstadoEspacioService` líneas
  92, 126, 139, 180–183); los rechazos lanzan antes de auditar.
- La tabla final de `requirements.md` cubre CA1, CA2, E y R, más RF-B-09/10, RN-15/16, RNF-SEG-12/19.

## Tasks completas
- T1–T20: [x] (todas `[x]` en `specs/HU-21/tasks.md`; T20 cerrada tras el desbloqueo, con `./init.sh` verde que confirmé).

## Verificaciones pedidas por el orquestador

- **Única modificación a tests de otras historias:** [x] `git diff` sobre tests existentes muestra solo
  `MisReservasControllerIT.java` línea 383 (1 línea: se quita `&espacioId=" + ajeno`). Sigue verificando que se ignora
  `arrendadorId` (`?grupo=proximas&arrendadorId=<otro>` → `containsExactly(enPublicado, enPausado, enOculto)`, y la
  línea 388 con el otro arrendador). `ajeno` sigue usándose (líneas 374–375). `EspacioTest` y
  `ConsultaReservasServiceTest` solo reciben casos nuevos, sin tocar los existentes. Coincide con design.md DT-7/DT-12.
- **R17 determinista:** [x] `BajaEspacioConcurrenciaIT` usa reloj controlable (`ReservasControllerSoporte`), espacio y
  usuario nuevos por ronda, y aserta el invariante (`unaDeDos`: reserva 201 + baja 400 + `PUBLICADO` con 1 reserva
  activa, o baja 200 + reserva 404 + `BAJA` sin reservas ni bloques), que se cumple gane quien gane el bloqueo.
  `losDosOrdenes` fija ambos órdenes en secuencia. El código lo sostiene: `retenerSiLibres`
  (`DisponibilidadServiceImpl.java:129`) y la baja toman `PESSIMISTIC_WRITE` sobre la misma fila y la baja lee el puerto
  después del bloqueo (verificado con `inOrder` en `EEST.reservasSeLeenTrasElBloqueo`). Verde en mi corrida.

## Revisión de archivos

- Arquitectura: [x] rutas y servicio en `espacios`; `espacios` llega a reservas solo vía el puerto
  `ReservasDelEspacio` (`ObjectProvider`); `reservas` usa `CatalogoEspaciosService`; sin puerto nuevo ni migración;
  controladores sin repositorios; DTO `record` (`EstadoEspacioDTO`); estados calculados en el servidor.
- Convenciones: [x] nombres en español, inyección por constructor, `ErrorDTO` genérico (404/403) y mensajes de § 9.
- Frontend (`docs/frontend.md` y design § 13): [x] solo kit (`Boton`, `Modal`, `Alerta`, `Tarjeta`, `EtiquetaEstado`),
  CSS Modules con tokens existentes (`--espacio-3`, `--radio-tarjeta`, `--color-superficie`, `--color-borde`), sin estilos
  en línea, un solo primario ("Guardar cambios"; en `EstadoPublicacion` solo `secundario`/`peligro`), llamadas solo vía
  `src/api/`, 400 en campo `reservas` → `Alerta atencion` + `ReservasAfectadas`. Sección "Pantallas" presente en design.md.
- Docs: [x] `docs/architecture.md` (estados, sección HU-21, Disponibilidad, filtro `espacioId`, Cancelación) y
  `docs/frontend.md` actualizados.
- ★: n/a (sin patrón).

## Checkpoints
- C1: [x] Arnés completo; `./init.sh` sin `--rapido` exit 0.
- C2: [x] Una sola `in_progress` de Sebastian Garate; `progress/HU-21.md` existe, registra la aprobación del spec
  (2026-11-04) y la elección de la opción 1, y describe solo esta sesión. "Toda historia done con tests": n/a (no se cierra aquí).
- C3: [x] Módulos correctos; ningún controller importa repository; sin `@Autowired` en `src/main`, Lombok ni comodines;
  sin `println`/`console.log`; dependencias entre módulos solo vía service/puertos; DTO `record`; identidad desde la
  sesión; sin migración nueva (n/a Flyway); sin integraciones externas nuevas (n/a adapter); nada fuera del alcance;
  sin secretos; sin colores fuera de tokens; frontend con el kit y `src/api/`.
- C4: [x] `*Test` (Mockito) e `*IT` (Testcontainers, PostgreSQL); reloj controlable (sin esperas); IT de endpoints;
  concurrencia lanzando las dos operaciones a la vez; resultados concretos; `mvnw verify` 2387 tests verdes; lint y build OK.
- C5: [x] Sin archivos sospechosos sin trackear (`target/` ignorado); trabajo en `feature/HU-21-pausar-baja-publicacion`;
  `feature_list.json` en `in_progress`. Entrada en `history.md` y regreso de `progress/HU-21.md` a la plantilla: n/a
  todavía (son del cierre).
- C6: [x] `specs/HU-21/` completo; EARS con tabla de trazabilidad; design con endpoints, clases, secuencias, alternativas
  descartadas y decisiones del dueño; mapa R↔test en `progress/impl_HU-21.md`; la precedencia sobre HU-14 R7 la decidió
  el dueño (opción 1) y está documentada.

## Cambios requeridos
Ninguno.

## Observaciones no bloqueantes
1. `progress/impl_HU-21.md` línea 8 sigue diciendo "**T20 queda `[ ]`** … Status `blocked`" y la línea 52 "Ningún test
   existente cambió"; ambas las contradice la sección "Desbloqueo (opción 1)". Conviene actualizarlas al cerrar.
2. `3_frontend/src/pages/reservas/ReservasDeMisEspacios.jsx` línea 21 conserva `tarjetaDeReserva(reserva)` como
   envoltorio de una línea de `TarjetaReservaDeMiEspacio`; podría pasarse el componente directamente.
3. Al abrir el PR, avisar a Adriana Callupe (dueña de HU-14) del ajuste en `MisReservasControllerIT` línea 383, como
   anota `progress/HU-21.md`.
