# Implementación HU-14 — Mis reservas (★ Iterator, DAO)

> implementer · 2026-10-31 · rama `feature/HU-14-mis-reservas` (base `6afb5bc`, con HU-13). Spec aprobado por Adriana
> Callupe el 2026-10-31 (D1–D12). Sin commit, push ni PR. La historia sigue `in_progress` hasta que la apruebe el reviewer.

## Resultado

- Tasks T1–T19 de `specs/HU-14/tasks.md`: todas `[x]`. No quedó ninguna sin completar.
- `./init.sh` completo: ver [Verificación](#verificación).
- Sin migración, sin puerto nuevo y sin cambios en `comun` (DT-7, § 10).
- **Ningún test existente cambió ni se borró**: `git diff --stat -- 2_Backend/src/test` muestra solo inserciones
  (+22 en `CuentaServiceImplTest`, +32 en `CatalogoEspaciosServiceImplTest`; 0 líneas borradas). En
  `CuentaServiceImplTest` se agregaron además los imports `java.util.Map` y `java.util.Set` que usan los casos nuevos.
- Comportamiento de HU-12 y HU-13 sin cambios: `ReservaServiceImpl.aDto` delega en `ConversionReservas` con el mismo
  `Instant.now(reloj)`; `ReservaServiceImplTest`, `ReservaControllerIT`, `ReservaConcurrenciaIT` y todas las IT de pagos
  siguen verdes sin tocarlas. `ReservasControllerSoporte` no se tocó.

## Archivos

### Backend — nuevos (`2_Backend/src/main/java/pe/edu/ulima/espacioya/`)

| Archivo | Para |
|---|---|
| `reservas/domain/GrupoReservas.java` | `desde(texto)`: solo `proximas` o `pasadas` exactos |
| `reservas/domain/ReservaConHorario.java` | TransferObject del DAO: `inicio`, `fin` (24 → día siguiente), `esProxima(ahora, ahoraLocal)` |
| `reservas/domain/HistorialReservas.java` | ★ Iterator · Aggregate |
| `reservas/domain/IteradorReservas.java` | ★ Iterator · ConcreteIterator (de paquete) |
| `reservas/domain/Pagina.java` | ★ Iterator · Client: `Pagina.de(recorrido, n, tamano)` |
| `reservas/repository/HorarioReserva.java` | Proyección `min(fecha)`, `min`/`max(hora_inicio)` |
| `reservas/repository/ReservaDAO.java` | ★ DAO · interfaz (dueño siempre en la firma) |
| `reservas/repository/ReservaDAOJpa.java` | ★ DAO · implementación `@Repository` (dos consultas) |
| `reservas/dto/PaginaReservasDTO.java` | `(pagina, reservas, hayMas)`, `List.copyOf` |
| `reservas/dto/ReservaDeMiEspacioDTO.java` | 9 claves, sin `usuarioId`, correo, teléfono ni pago |
| `reservas/service/ConsultaReservasService.java` | Casos de uso `misReservas` y `reservasDeMisEspacios`; constantes de design § 5 |
| `reservas/service/ConversionReservas.java` | DT-11: única fuente del `ReservaDTO` |
| `reservas/controller/ReservasDeMisEspaciosController.java` | `GET /api/arrendadores/actual/reservas` |
| `cuentas/repository/NombreCuenta.java` | Proyección `(id, nombre)` |

### Backend — modificados

| Archivo | Cambio |
|---|---|
| `reservas/repository/ReservaRepository.java` | `findByUsuarioId`, `findByEspacioIdInAndEstado` |
| `reservas/repository/ReservaBloqueRepository.java` | `horarios(reservaIds)` sin filtrar `activo` |
| `reservas/service/ReservaServiceImpl.java` | `aDto` delega en `ConversionReservas` (mismo resultado) |
| `reservas/controller/ReservaController.java` | `GET /api/reservas` (sin id) → `ConsultaReservasService.misReservas` |
| `espacios/service/CatalogoEspaciosService.java` + `Impl` | `nombresDelArrendador(arrendadorId)` |
| `cuentas/service/CuentaService.java` + `Impl`, `cuentas/repository/UsuarioRepository.java` | `nombres(usuarioIds)`, `nombresDe(ids)` |

### Backend — tests (`2_Backend/src/test/java/pe/edu/ulima/espacioya/`)

| Archivo | Tipo | Casos |
|---|---|---|
| `reservas/domain/GrupoReservasTest.java` (N) | unitaria | 10 |
| `reservas/domain/HistorialReservasTest.java` (N) | unitaria | 13 |
| `reservas/domain/PaginaTest.java` (N) | unitaria | 9 |
| `reservas/repository/ReservaDAOJpaTest.java` (N) | unitaria (Mockito) | 5 |
| `reservas/repository/ReservaDAOJpaIT.java` (N) | IT (Testcontainers, sin MockMvc) | 5 |
| `reservas/service/ConsultaReservasServiceTest.java` (N) | unitaria (Mockito, `Clock` fijo) | 23 |
| `reservas/controller/MisReservasControllerIT.java` (N) | IT (extiende `ReservasControllerSoporte`, IP `10.16.x.x`, `@AfterEach borrarPagos`) | 34 |
| `cuentas/service/CuentaServiceImplTest.java` (M, solo se agregan casos) | unitaria | +2 |
| `espacios/service/CatalogoEspaciosServiceImplTest.java` (M, solo se agregan casos) | unitaria | +3 |

### Frontend (`3_frontend/src/`)

| Archivo | Cambio |
|---|---|
| `api/reservas.js` (M) | `listarMisReservas`, `listarReservasDeMisEspacios` (`URLSearchParams`) |
| `pages/reservas/textosReservas.js` (M) | Títulos, textos de lista vacía, "Ver más", "Ver espacio", "Ver reserva", "Reservado por" |
| `pages/reservas/SeccionReservas.jsx` + `.module.css` (N) | Sección con página 1 al montar (patrón `activo`), "Ver más" secundario, sin repetir ids, `Alerta error` |
| `pages/reservas/MisReservas.jsx` + `.module.css` (N) | `/mis-reservas` (CA1); "Ver espacio" y "Ver reserva" en todas (D10) |
| `pages/reservas/ReservasDeMisEspacios.jsx` + `.module.css` (N) | `/panel/reservas` (CA2); "Reservado por", "Ver espacio" → `/panel/espacios/:id` |
| `App.jsx` (M) | Dos rutas en `RutaProtegida` (que no cambia) |
| `pages/cuentas/Panel.jsx` (M) | Enlace "Mis reservas" (`USUARIO`) y "Reservas de mis espacios" (`ARRENDADOR`) |

Sin componentes nuevos en el kit ni estados nuevos en `EtiquetaEstado`. Solo tokens.

### Docs y progreso

- `docs/architecture.md`: sección nueva "Mis reservas: qué deja HU-14"; párrafos de `CuentaService.nombres` y
  `CatalogoEspaciosService.nombresDelArrendador`; fila HU-14 de "Patrones por historia". Tabla de puertos sin cambios.
- `docs/frontend.md`: `Tarjeta` en las dos pantallas; rutas `/mis-reservas` y `/panel/reservas` con "Ver más".
- `specs/HU-14/tasks.md` (T1–T19 `[x]`), `progress/HU-14.md` (bitácora y avisos), este archivo.

## Dónde quedó cada patrón

### ★ DAO (forma B, DT-3)

| Rol | Clase |
|---|---|
| DataAccessObject | `reservas/repository/ReservaDAO.java` — `delUsuario(usuarioId)`, `confirmadasDeEspacios(espacioIds)`; el dueño es siempre el primer parámetro |
| Implementación | `reservas/repository/ReservaDAOJpa.java` — `@Repository` de paquete; `findByUsuarioId` / `findByEspacioIdInAndEstado(…, CONFIRMADA)` + `horarios(ids)`, unión en memoria; colección vacía o sin reservas → `List.of()` sin la segunda consulta |
| DataSource | `ReservaRepository`, `ReservaBloqueRepository` (Spring Data JPA) |
| TransferObject | `reservas/domain/ReservaConHorario.java` |
| BusinessObject | `reservas/service/ConsultaReservasService.java` (solo conoce `ReservaDAO`) |

### ★ Iterator (DT-4)

| Rol | Clase |
|---|---|
| Aggregate | `reservas/domain/HistorialReservas.java` — copia privada ordenada por (inicio, id), sin getters; `proximas()`, `pasadas()`, `recorrer(grupo)` |
| Iterator | `java.util.Iterator<ReservaConHorario>` |
| ConcreteIterator | `reservas/domain/IteradorReservas.java` — cursor en un sentido con criterio (`esProxima` o su negación); `NoSuchElementException`; `remove` no soportado |
| Client | `reservas/domain/Pagina.java` (`de(recorrido, n, 10)`, `hayMas = hasNext()`) y `ConsultaReservasService` |

Documentado en `docs/architecture.md` § "Mis reservas: qué deja HU-14" y en la fila HU-14 de "Patrones por historia".

## Trazabilidad (R → test)

`MRIT` = `MisReservasControllerIT`, `CRST` = `ConsultaReservasServiceTest`, `HRT` = `HistorialReservasTest`. Cada test
lleva `@DisplayName("HU-14 R<n>: …")`.

| R | Tests |
|---|---|
| R1 | `MRIT.sinSesion` |
| R2 | `MRIT.misReservasConOtroRol` (ARRENDADOR, ADMIN; también `grupo=todas&pagina=0`), `CRST.misReservasConOtroRol` (sin DAO, catálogo ni cuentas) |
| R3 | `MRIT.deMisEspaciosConOtroRol` (USUARIO, ADMIN; también grupo inválido), `CRST.reservasDeMisEspaciosConOtroRol` |
| R4 | `MRIT.misReservasProximas`, `MRIT.confirmadaDePuntaAPunta`, `CRST.misReservasProximas` |
| R5 | `MRIT.misReservasPasadasConHorarioInactivo`, `CRST.misReservasPasadasConVencida` |
| R6 | `MRIT.soloLasDelUsuarioDeLaSesion`, `CRST.misReservasProximas` (DAO invocado con el id de la sesión) |
| R7 | `MRIT.soloLasDelUsuarioDeLaSesion` (`usuarioId`, `espacioId`, `arrendadorId` ignorados), `MRIT.deSusEspaciosEnCualquierEstado` (`arrendadorId`, `espacioId` ignorados) |
| R8 | `MRIT.misReservasPasadasConHorarioInactivo`, `ReservaDAOJpaIT.horarioConFilasInactivasYMedianoche` |
| R9 | `MRIT.nombreDeUnEspacioNoVisible` (PAUSADO y arrendador SUSPENDIDA, las dos rutas), `CRST.misReservasProximas` |
| R10 | `MRIT.vencidaSinProcesar` (fila sigue `PENDIENTE_PAGO`), `CRST.misReservasPasadasConVencida`, `CRST.unSoloInstante` |
| R11 | `MRIT.misReservasProximas` (900), `MRIT.misReservasPasadasConHorarioInactivo` y `MRIT.vencidaSinProcesar` (0), `CRST.misReservasProximas`, `CRST.unSoloInstante` |
| R12 | `MRIT.misReservasProximas` (claves exactas; sin `usuarioId`, arrendador ni pago) |
| R13 | `MRIT.confirmadaDePuntaAPunta` (pasarela simulada), `MRIT.soloConfirmadas`, `CRST.reservasDeMisEspaciosProximas` |
| R14 | `MRIT.deSusEspaciosEnCualquierEstado`, `CRST.reservasDeMisEspaciosPasadas` |
| R15 | `MRIT.soloConfirmadas` (pendiente vigente, vencida, EXPIRADA y CANCELADA fuera), `MRIT.confirmadaDePuntaAPunta` |
| R16 | `MRIT.deSusEspaciosEnCualquierEstado` (espacio de otro arrendador fuera, en los dos sentidos) |
| R17 | `MRIT.deSusEspaciosEnCualquierEstado` (PAUSADO, BAJA, OCULTO) |
| R18 | `MRIT.nombreDelUsuario`, `CRST.reservasDeMisEspaciosProximas` |
| R19 | `MRIT.nombreDelUsuario` (nulo y `"   "`), `MRIT.confirmadaDePuntaAPunta`, `CRST.reservasDeMisEspaciosProximas` |
| R20 | `MRIT.minimizacionDelArrendador` (claves exactas; sin correo, teléfono, `usuarioId` ni pago), `CRST.nombresSoloDeLaPagina` |
| R21 | `MRIT.arrendadorSinEspacios`, `CRST.arrendadorSinEspacios` (DAO no invocado) |
| R22 | `HRT.limiteDeLasDiez`, `HRT.bloqueDeMedianoche`, `HRT.vigenteYConfirmadaFuturas`, `HRT.noVigentesFuturasSonPasadas`, `HRT.vigenteYaTerminadaEsPasada`, `HRT.recorrerPorGrupo`, `HRT.nextAgotado`, `HRT.removeNoSoportado`, `HRT.copiaPrivada`, `HRT.recorridosIndependientes`, `MRIT.limiteDeLasDiez`, `CRST.unSoloInstante` |
| R23 | `HRT.noVigentesFuturasSonPasadas`, `MRIT.expiradaYCanceladaFuturasSonPasadas`, `MRIT.vencidaSinProcesar` |
| R24 | `HRT.ordenEnLosDosSentidos`, `HRT.empatePorId`, `MRIT.orden` |
| R25 | `HRT.ordenEnLosDosSentidos`, `HRT.empatePorId`, `MRIT.orden`, `CRST.reservasDeMisEspaciosPasadas` |
| R26 | `PaginaTest.diezElementos`, `onceElementosPaginaUno`, `onceElementosPaginaDos`, `paginaMaxima`, `argumentosInvalidos`, `elementosInmutables`; `MRIT.paginas`; `CRST.paginaDeDiez` |
| R27 | `PaginaTest.diezElementos`, `onceElementosPaginaUno`, `onceElementosPaginaDos`, `noConsumeDeMas`; `MRIT.paginas`; `CRST.paginaDeDiez` |
| R28 | `MRIT.sinPagina`, `CRST.sinPaginaEsLaUno` |
| R29 | `MRIT.paginaInvalida` (`0`, `-1`, `abc`, `1.5`, `2147483648`, `""`; las dos rutas), `CRST.paginaInvalida` |
| R30 | `MRIT.grupoInvalido` (ausente, `PROXIMAS`, `todas`, `""`, `pasadas%20`; las dos rutas), `GrupoReservasTest.gruposValidos`, `GrupoReservasTest.gruposInvalidos`, `CRST.sinGrupo` |
| R31 | `MRIT.grupoYPaginaInvalidos`, `CRST.grupoYPaginaInvalidos` |
| R32 | `PaginaTest.sinElementos`, `paginaMasAllaDelFinal`, `paginaMaxima`; `HRT.listaVacia`; `MRIT.paginas`, `MRIT.sinPagina`; `CRST.paginaVaciaNoPideNombres` |
| R33 | `ReservaDAOJpaIT.delUsuarioSoloLasSuyas`, `horarioConFilasInactivasYMedianoche`, `usuarioSinReservas`; `ReservaDAOJpaTest.delUsuarioConDosConsultas`, `reservaSinHorarioSeOmite` |
| R34 | `ReservaDAOJpaIT.confirmadasSoloDeEsosEspacios`, `coleccionVacia`; `ReservaDAOJpaTest.confirmadasDeEspacios`, `espaciosVaciosNoConsulta` |
| R35 | `ReservaDAOJpaTest.delUsuarioConDosConsultas`, `sinReservasNoConsultaHorarios`, `confirmadasDeEspacios` |
| R36 | `CuentaServiceImplTest.nombresSoloConNombre`, `nombresVacio`; `MRIT.nombreDelUsuario` |
| R37 | `CatalogoEspaciosServiceImplTest.nombresDelArrendadorEnCualquierEstado`, `nombresDelArrendadorSoloLosSuyos`, `nombresDelArrendadorNull`; `MRIT.deSusEspaciosEnCualquierEstado` |
| R38 | `MRIT.soloLectura` (filas de `reserva` y `reserva_bloque` idénticas y `auditoria` sin filas nuevas tras las dos rutas) |
| HU-12 (regresión) | `ReservaServiceImplTest`, `ReservaControllerIT`, `ReservaConcurrenciaIT` sin cambios y en verde |

## Notas de implementación (dentro del spec)

- `HorarioReserva` usa `short`, como dice el design: Hibernate resolvió el constructor sin pasar a `Short`.
- `Pagina.de` lanza `IllegalArgumentException` con `numero < 1` o `tamano < 1`. El servicio nunca le pasa esos valores,
  porque valida antes.
- `MisReservasControllerIT` reemplaza en su `@BeforeEach` la IP `10.14` del soporte por una `10.16.x.x`, porque ese
  `@BeforeEach` corre después del del soporte. `borrarPagos` (`@AfterEach`) corre antes que `limpiar()` del soporte.
- En `MRIT.limiteDeLasDiez`, tras avanzar el reloj 1 hora se inicia otra sesión, porque la sesión vence a los 30 minutos
  sin actividad. En `MRIT.orden`, el empate por id usa un segundo espacio del mismo arrendador: dos filas activas en el
  mismo espacio, día y hora violan `uq_reserva_bloque_activo`.
- Para `USUARIO`, `Panel.jsx` reemplaza la descripción "Aquí verás tus reservas." por el enlace "Mis reservas". Con un rol
  desconocido sigue mostrando la descripción.

## Verificación

`./init.sh` completo (sin `--rapido`), 2026-10-31: **EXIT 0**, `[OK] Entorno listo`.

- Entorno: Java 25, Node v24.15.0, Docker encendido. `[WARN]` por las llaves `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA` y
  `ALMACENAMIENTO_LLAVE_FIRMA`, que no están en el shell. Solo afecta a `spring-boot:run`; las pruebas traen las suyas.
- Archivos del arnés, `feature_list.json` (13 done, 1 in_progress, 14 pending), specs, progress y tasks: OK.
- Reglas de arquitectura: OK (sin controladores con repositorios, sin `@Autowired`, sin comodines, sin prints ni
  `console.log`, sin colores fuera de `tokens.css`, sin librerías de UI, sin secretos).
- Backend `./mvnw verify`: unitarias **889** y IT **626**, con 0 fallos, 0 errores y 0 omitidas.
- Frontend: ESLint sin errores ni advertencias; `npm run build` OK.

## Avisos para la dueña

> Adriana: el implementer no puede contactar a nadie. Estos avisos (T18) quedan redactados para que los envíes tú. También
> están en `progress/HU-14.md`.

**A Sebastian Garate (módulo `espacios`) — `CatalogoEspaciosService.nombresDelArrendador`**

> Hola, Sebastian. En HU-14 (Mis reservas) agregué un método a la interfaz pública `CatalogoEspaciosService`:
> `Map<Long, String> nombresDelArrendador(Long arrendadorId)`. Devuelve el id y el nombre de **todos** los espacios del
> arrendador, en cualquier estado (`BORRADOR`, `PUBLICADO`, `PAUSADO`, `BAJA`, `OCULTO`) y sin filtro de visibilidad.
> Para `null` o un arrendador sin espacios devuelve vacío. No exige sesión ni rol: `reservas` le pasa el id de la sesión.
> Está implementado en `CatalogoEspaciosServiceImpl` con `EspacioRepository.findByArrendadorIdOrderByCreadoEnDesc`, que ya
> existía, y probado en `CatalogoEspaciosServiceImplTest` (solo agregué casos). No cambié ningún método existente. Así
> `reservas` no lee `EspacioRepository`. Una cosa a tener en cuenta: si algún día un espacio se borrara físicamente, sus
> reservas dejarían de verse en "Reservas de mis espacios". Hoy la FK sin cascada lo impide. Está documentado en
> `docs/architecture.md`.

**A Jorge Quispe (módulo `cuentas`) — `CuentaService.nombres`**

> Hola, Jorge. En HU-14 (Mis reservas) agregué a `CuentaService` el método `Map<Long, String> nombres(Collection<Long>
> usuarioIds)`. Devuelve **solo** el nombre de las cuentas cuyo `nombre` no es nulo ni está en blanco; las demás no
> aparecen. Nunca devuelve correo ni teléfono (RNF-SEG-16), no exige sesión ni rol y, con una colección vacía, devuelve
> `Map.of()` sin consultar. Para eso agregué la proyección `cuentas/repository/NombreCuenta` y
> `UsuarioRepository.nombresDe(ids)`; el nombre en blanco se filtra en Java. Lo usa "Reservas de mis espacios" para
> mostrarle al arrendador quién reservó; si no hay nombre, `reservas` muestra "Usuario de EspacioYa". Solo agregué casos
> a `CuentaServiceImplTest`; ningún método existente cambió. Dos notas: HU-20 (editar perfil) completará el nombre sin
> cambiar este contrato, y si HU-22 (anonimizar) deja `nombre` nulo o vacío, el arrendador verá "Usuario de EspacioYa",
> que es lo esperado. `correoDe` (HU-13) **no** se usa para el arrendador.

**A Eduardo Saldaña (HU-13) — contrato verificado, sin acción**

> Hola, Eduardo. HU-14 verificó contra tu código de HU-13 (`ec51197`) los siete puntos del contrato de
> `specs/HU-14/design.md` § 11.3: no hay estado intermedio, solo una vigente pasa a `CONFIRMADA`, se conservan las filas de
> `reserva_bloque`, `monto_total` es el monto cobrado, no hay interferencia de bloqueos, los datos de pago no van por los
> DTO de HU-14 y "Pagar" está en `/reservas/:id`. **No tienes que hacer nada.** HU-14 no usa código de `pagos` en
> producción. Solo `MisReservasControllerIT` llama a tu API simulada (reservar → pagar → aprobar) y borra `pago` al
> terminar. Si una historia futura cambia `estado` o `expira_en` de la reserva, avísame.

**A Kevin Chan (HU-15) — puntos de extensión**

> Hola, Kevin. HU-14 deja libre `acciones` de `Tarjeta` para que pongas "Cancelar" en las `CONFIRMADA` próximas, tanto en
> `/mis-reservas` (`MisReservas.jsx`, junto a "Ver espacio" y "Ver reserva") como en `/panel/reservas`
> (`ReservasDeMisEspacios.jsx`). Ojo: una `CANCELADA` aparece en las **pasadas** del usuario aunque su fecha sea futura, y
> **desaparece** de la lista del arrendador, que solo muestra `CONFIRMADA`. Si quieres mostrársela al arrendador, amplía
> `ReservaDAO.confirmadasDeEspacios` o agrega un método al DAO, siempre con el dueño en la firma. Para saber si una
> reserva es futura puedes usar `ReservaConHorario.esProxima`. Detalle en `docs/architecture.md`, "Mis reservas: qué deja
> HU-14".

**Nota D12 (sin destinatario)**

La caja negra de CA2 se puede hacer **completa** en UAT con la pasarela simulada: reservar → "Pagar" → "Aprobar pago
simulado" → la reserva aparece en "Reservas de mis espacios". No queda ninguna verificación pendiente de integración. Lo
que falte con Mercado Pago real es de HU-13. Para arrancar el backend en local hace falta `PAGOS_PASARELA=simulada` en
`2_Backend/.env`.
