# Exploración — HU-12 · Reserva con bloqueo temporal

- **Fecha:** 2026-10-30 · **Rama:** `feature/HU-12-reserva-bloqueo-temporal` · **Dueño:** Eduardo Saldaña · **Prueba:** Ariel Enriquez
- **Alcance:** solo lectura. No se modificó nada fuera de este archivo; no se ejecutaron builds ni tests.
- **Prefijos:** `B = 2_Backend/src/main/java/pe/edu/ulima/espacioya`, `T = 2_Backend/src/test/java/pe/edu/ulima/espacioya`, `F = 3_frontend/src`.
- **Procedencia:** los hechos salen del repositorio (con su archivo). Lo marcado *(conocimiento general)* es comportamiento de Hibernate, Spring o PostgreSQL que no está en el repo ni se comprobó ejecutando. No se usaron notas personales: el mapa de arquitectura consultado no tiene un subtema de concurrencia o expiración.

## Resumen: lo que más condiciona el spec

1. El módulo `reservas` **no existe** (ni `pagos`): no hay paquete, tablas, `ReservaService` ni implementación del puerto. Tampoco hay ninguna implementación provisional que reemplazar: `ObjectProvider` sin bean equivale a "sin reservas".
2. **El índice parcial `WHERE activo` no entiende el tiempo.** Una `PENDIENTE_PAGO` vencida sigue con `activo = true` hasta que alguien la expira. Si la lectura ya la trata como libre pero nadie la expiró, el `INSERT` choca con el índice y da un falso "horario no disponible". Ver § 7 y decisión 3.
3. **No existe ningún proceso programado** en el backend. §9-8 sigue abierto y HU-12 debe cerrarlo (solo la parte de expiración).
4. **Dos dobles de prueba romperán el arranque de sus IT** al aparecer el bean real, porque no tienen `@Primary`, aunque `docs/architecture.md:225` diga lo contrario para uno de ellos. Ver § 1.
5. `reservas` **no tiene hoy cómo obtener** `arrendadorId` ni un nombre de espacio no visible por una interfaz pública. El método `retenerSiLibres` previsto por `architecture.md` debe resolver el primero.
6. El retorno tras el login **no existe**: `/panel` está fijo en dos sitios de `IniciarSesion.jsx`. Ver § 5.

---

## 1. Disponibilidad (HU-08)

**Representación de bloques.** 1 hora en punto. `hora_inicio` entero 0–23 y `hora_fin` 1–24 exclusivo (`specs/HU-08/design.md` DT-4). Los bloques no se guardan: se calculan al consultar. `HorarioSemanal` mapea `SMALLINT` a `short` (`B/espacios/domain/HorarioSemanal.java:23-29`). El bean `Clock` está en UTC (`B/comun/configuracion/ConfiguracionReloj.java`); el calendario se calcula en `America/Lima` con `ZonaHoraria.hoy/ahora` (`B/comun/configuracion/ZonaHoraria.java:15-26`).

**Piezas existentes:**

| Pieza | Dónde | Qué hace |
|-------|-------|----------|
| `DisponibilidadService.horasLibres(id, fecha)` | `B/espacios/service/DisponibilidadService.java:16`, impl `DisponibilidadServiceImpl.java:57-73` | Horas libres ascendentes. Vacía si está fuera de ventana, no `PUBLICADO` o arrendador no `ACTIVA`. `readOnly`, sin bloqueo |
| `conBloquesLibres(ids, fecha)` | `DisponibilidadServiceImpl.java:75-118` | Lote de HU-10; usa `bloquesReservadosDelDia` |
| `CalculoDisponibilidad.bloquesDelDia` | `B/espacios/domain/CalculoDisponibilidad.java:20-45` | Precedencia `BLOQUEADO > RESERVADO > PASADO > LIBRE`. Una hora es `PASADO` si `fecha.atTime(h,0)` no es posterior a ahora local |
| `VentanaReserva` | `B/espacios/domain/VentanaReserva.java` | 56 días desde hoy inclusive (`hasta = hoy + 55`), `contiene(fecha)` |
| Bloqueo de fechas | `CalendarioEspacioService.java:120-139` | Rechaza con 400 si hay reserva pagada (`MENSAJE_RESERVA_PAGADA`) o pendiente (`MENSAJE_RESERVA_PENDIENTE`) según `BloqueReservado.pagado` (DT-6) |

**Contrato del puerto** (`B/espacios/service/puertos/ReservasDelEspacio.java`, `BloqueReservado.java`; `docs/architecture.md:131`):

```java
List<BloqueReservado> bloquesReservados(Long espacioId, LocalDate desde, LocalDate hasta);
default Map<Long, List<BloqueReservado>> bloquesReservadosDelDia(Collection<Long> espacioIds, LocalDate fecha);
record BloqueReservado(LocalDate fecha, int horaInicio, boolean pagado)
```

- Criterio: reservas activas = `PENDIENTE_PAGO` **vigente** o `CONFIRMADA`; `pagado` = `CONFIRMADA`. Rango con ambos extremos inclusive.
- Llega por `ObjectProvider` (`DisponibilidadServiceImpl.java:42`). **No hay implementación provisional.** HU-12 solo agrega su bean.
- `architecture.md:228-234` pide (no obliga) sobrescribir `bloquesReservadosDelDia` con una sola consulta `espacio_id IN (…) AND fecha = …`, porque la búsqueda lo invoca con todos los espacios del tipo y la zona. Los ids sin reservas pueden faltar en el mapa.
- `progress/history.md:526`: sigue pendiente confirmar la firma con Eduardo. Sirve tal cual para HU-12.

**Cómo se restan los bloques.** Basta con que el puerto devuelva las horas: `DisponibilidadServiceImpl.bloquesDelDia` (`:132-145`) y `conBloquesLibres` (`:102-116`) ya las marcan `RESERVADO`. HU-10, HU-11 y el calendario del dueño no cambian.

**Lo que HU-12 debe agregar en `espacios`** (`architecture.md:217-224`):

- Un método de `DisponibilidadService` (ejemplo del documento: `retenerSiLibres(espacioId, fecha, horas)` con `@Transactional(propagation = MANDATORY)`) que tome el bloqueo de `espacio` sin filtrar por dueño y compruebe las horas contra `horasLibres` en esa misma transacción.
- `EspacioRepository` **no tiene** esa consulta: `findParaActualizar(id, arrendadorId)` (`EspacioRepository.java:25-28`) exige el dueño. Hace falta una nueva (p. ej. `findParaReservar(id)` con `PESSIMISTIC_WRITE`). `reservas` no puede llamar a ese repositorio directamente.

**Riesgo de orden** *(conocimiento general)*: si `Espacio` ya está cargado en la sesión de Hibernate antes de pedir el bloqueo, la consulta bloquea la fila pero devuelve la instancia antigua sin refrescarla. El bloqueo debe ser **la primera lectura** de `Espacio` en la transacción, y precio y arrendador deben leerse después.

**Pruebas que afecta la llegada del bean real:**

| Archivo | Estado | Consecuencia |
|---------|--------|--------------|
| `T/busqueda/controller/FichaControllerIT.java:103-107` | Con `@Primary` | Sin cambios |
| `T/busqueda/controller/BusquedaControllerIT.java:99-102` | **Sin `@Primary`** | Dos beans del tipo: `NoUniqueBeanDefinitionException` (ya detectado en HU-11, `progress/history.md:660-661`, pero solo se arregló la ficha) |
| `T/espacios/controller/EspaciosControllerSoporte.java:113-117` | **Sin `@Primary`** | Igual. **`docs/architecture.md:225` afirma que sí lo tiene: es incorrecto.** Lo heredan todas las IT de `espacios` que extienden el soporte (`EspacioControllerIT`, `CalendarioEspacioControllerIT`, `FotoEspacioControllerIT`, `FotoPublicaControllerIT`, `ZonaControllerIT`) |
| `T/espacios/service/DisponibilidadServiceImplIT.java:33` | Sin doble | Pasará a usar el bean real y debe seguir en verde con tablas vacías. Su comentario de la línea 33 quedará obsoleto |

Corregir estos `@Primary` toca tests existentes de otras historias: el `design.md` debe declararlo.

## 2. Espacios (HU-07)

- `Espacio` (`B/espacios/domain/Espacio.java`): `precioHora` `BigDecimal` con `precision 8, scale 2` (`:52-53`), en **soles** por convención: no hay columna de moneda (comentario `:140`). `escalar` usa `RoundingMode.UNNECESSARY`: "un monto nunca se redondea en silencio". Rango en BD: `ck_espacio_precio_hora BETWEEN 1 AND 100000` (`V2_2__espacio.sql:44,52`). `arrendador_id` es FK a `perfil_arrendador(usuario_id)` (`V2_2:38`).
- Estados: `BORRADOR, PUBLICADO, PAUSADO, BAJA, OCULTO`. Editar el precio toma el mismo bloqueo de fila (`EspacioService.editar`, `:117-129`), así que queda serializado con la reserva.
- `EspacioService` es una **clase concreta** orientada a la sesión del arrendador: no sirve a `reservas`.
- `CatalogoEspaciosService` (`B/espacios/service/CatalogoEspaciosService.java`, interfaz sin sesión): `publicado(id) → Optional<FichaPublicada>` y `esVisible(id)`. Ambas aplican `PUBLICADO` + `cuentaActiva`.

**Huecos para `reservas`:**

- `FichaPublicada` trae `precioHora` y `nombre`, pero **deliberadamente no** `arrendadorId` ni estado (`FichaPublicada.java:6-10`). Ninguna interfaz pública devuelve el arrendador.
- `publicado` se vacía si el espacio deja de ser visible, así que no sirve para mostrar el nombre de una reserva ya creada (HU-12 resumen, HU-14). Falta una lectura sin filtro de visibilidad.
- Leer precio con `publicado` **antes** del bloqueo sería una condición de carrera y además caería en el riesgo del § 1.

## 3. Arrendador activo (HU-09, D1)

- `CuentaService.cuentaActiva(usuarioId)` (`CuentaService.java:92`, impl `CuentaServiceImpl.java:210-217`): `true` solo si existe y está `ACTIVA`. Sin sesión ni rol.
- `arrendadoresNoActivos()` (`:99`, impl `:219-223`): `Set<Long>`; si está vacío, se omite el `not in`.
- `architecture.md:153-159`: no se leen `usuario` desde `reservas`. La reserva pregunta `cuentaActiva(arrendadorId)` **o hereda el filtro si usa `horasLibres`**. Usar `horasLibres` dentro del bloqueo ya lo cumple.
- `specs/HU-09/requirements.md:38` (S9): la suspensión no toca reservas; qué pasa con las de un suspendido lo deciden HU-12 y HU-15. Ver decisión 9.
- El reservante en sí ya está cubierto: `SesionService.validar` rechaza con 401 las sesiones de cuentas no `ACTIVA` (`architecture.md:246-247`).

**Identidad verificada.** No hace falta comprobarla al reservar:

- `architecture.md:282`: "Identidad verificada" habilita "recibir reservas pagadas".
- `EspacioService.publicar:139` la exige para pasar a `PUBLICADO`, y `PerfilArrendador` hoy no tiene transición de salida desde `VERIFICADA` (`B/cuentas/domain/PerfilArrendador.java:84-112`). Por tanto, hoy `PUBLICADO` implica identidad verificada.
- Esa invariante se romperá con **HU-20 CA3 / RN-02**, que devuelve la identidad a "en revisión". Es tarea de HU-20 ocultar o decidir; se deja anotado.
- Además, no hay método para consultar la identidad de un tercero: `habilitacionArrendador` exige rol `ARRENDADOR` en sesión y responde con la cuenta propia (`CuentaServiceImpl.java:75-85`).

## 4. Ficha y búsqueda (HU-10, HU-11)

- Pantalla: `F/pages/busqueda/FichaEspacio.jsx`, ruta pública `/espacios/:id` (`F/App.jsx:106`), con `?fecha=` (`:34`; sin parámetro usa hoy en Lima).
- Endpoints: `GET /api/espacios/{id}` y `GET /api/espacios/{id}/disponibilidad?fecha=` (`B/busqueda/controller/FichaController.java`), con `obtenerEspacio` y `consultarBloquesLibres` (`F/api/busqueda.js:10-17`, ambos `autenticada: false`).
- Muestra **solo los bloques libres** con `SelectorBloques` sin `onCambiar` (`FichaEspacio.jsx:149-151`). `FichaService.disponibilidad` da 404 si no es visible y 400 en `fecha` si es inválida o está fuera de ventana (`B/busqueda/service/FichaService.java:50-67`).
- **Dónde va "Reservar"** (`specs/HU-11/design.md:413-428`, `docs/frontend.md:162`): estado `seleccionados` en `FichaEspacio.jsx`, pasar `seleccionados` y `onCambiar` a `SelectorBloques`, vaciar la selección al cambiar de fecha, y el **único botón primario** "Reservar" debajo de "Bloques libres" (después de la línea 151). `precioHora` es solo para mostrar. La ficha aún no importa `useSesion`, `Boton` ni `useNavigate`.
- La disponibilidad pública es solo de consulta: la reserva se valida en su propia transacción (`design.md:426-428`). Los DTO de HU-11 se amplían, no se cambian.
- Tras reservar o tras un conflicto, volver a llamar a `consultarBloquesLibres` para refrescar.
- Filtro de no activos: búsqueda con `arrendadoresNoActivos()` (`CatalogoEspaciosServiceImpl.java:60-63`); ficha y disponibilidad con `visible()` (`:101-108`); `horasLibres` con `cuentaActiva` (`DisponibilidadServiceImpl.java:66`).

## 5. Sesión y CA4

**Backend**

- Identidad: `UsuarioAutenticado(usuarioId, sesionId, rol)` (`B/comun/seguridad/UsuarioAutenticado.java`), inyectado con `@AuthenticationPrincipal` (ejemplo `B/espacios/controller/CalendarioEspacioController.java:39`). IP con `IpCliente.de(peticion)`.
- `ConfiguracionSeguridad.java:43-63`: `POST /api/reservas` (o cualquier ruta nueva) ya queda en `anyRequest().authenticated()`; **no hay que tocar nada**. Sin sesión responde 401 por `PuntoEntradaNoAutenticado`.
- No hay `hasRole` en ningún sitio: los roles se comprueban en los servicios (`exigirAdmin`, `habilitacionArrendador`), con `AccesoDenegadoException` → 403.

**Frontend: el retorno no existe**

- `F/sesion/RutaProtegida.jsx:7`: `<Navigate to="/iniciar-sesion" replace />`, sin guardar de dónde venía.
- `F/pages/cuentas/IniciarSesion.jsx`: `/panel` fijo en **dos** sitios: `:25` (`if (sesion) return <Navigate to="/panel" replace />`) y `:36` (`navegar('/panel', { replace: true })`). Si solo se cambia `:36`, `:25` puede ganar al volver a renderizar tras `entrar()`.
- `IniciarSesion.jsx:18` ya lee `useLocation().state` (como `{ mensaje, tipo }`), así que `state` puede llevar también `retorno`. Ninguna búsqueda encontró `retorno`, `returnTo` ni similar en `F`.
- `F/sesion/ProveedorSesion.jsx:34-37`: al perder la sesión (401 con token) navega a `/iniciar-sesion` con `{ mensaje, tipo }`, **sin retorno**. Esto importa: la sesión caduca a los 30 minutos sin actividad y el estado `sesion` del cliente sigue lleno, así que un usuario "con sesión" puede pulsar Reservar y recibir 401. `cliente.js:22-27` dispara el aviso antes de que la pantalla vea el error.
- `ProveedorSesion` arranca con `cargando = true` si hay token (`:12`): mientras carga, `sesion` es `null`, y no debe tomarse por anónimo al pulsar Reservar.
- `progress/history.md:666-668`: la URL de retorno se arma con la `fecha` en uso, **no** copiando `location`, porque sin `?fecha=` la fecha no está en la URL.

**Rol: quién reserva.** No hay una regla explícita.

- A favor de solo `USUARIO`: `docs/requerimientos.md:19-20` (usuario = "reserva y paga"; arrendador = "publica"); `HU-14` CA1 da "Mis reservas" al usuario y CA2 "Reservas de mis espacios" al arrendador; `Panel.jsx:9-10` ("Aquí verás tus reservas" solo para `USUARIO`).
- En contra (literal): HU-12 dice "usuario autenticado" y RF-D-01 igual.
- `ADMIN` no aparece como reservante en ningún documento.
- Arrendador reservando su propio espacio: ningún documento lo dice. Si solo reserva `USUARIO`, el caso desaparece, porque `espacio.arrendador_id` apunta a `perfil_arrendador`.

Ver decisión 1.

## 6. Módulo `reservas`

- **No existe nada:** ni `B/reservas/`, ni `B/pagos/`, ni tablas. Búsquedas de `ReservaService`, `PagoService`, `ReservasPorCobrar`, `ReservasLiquidables` y `ParticipanteDatosPersonales` en `2_Backend/src`: cero archivos.
- Estructura esperada (`docs/conventions.md:56-65`, `architecture.md:43`): `reservas/{controller,dto,service,domain,repository}`, con `Reserva`, `ReservaBloque`, `EstadoReserva`, `ReservaController`, `CrearReservaDTO`, `ReservaDTO`, repositorios y la implementación del puerto.
- `architecture.md:136-137`: `ReservaService` figura entre los servicios con interfaz pública. Dirección permitida: `espacios ← reservas → pagos → cuentas`, `busqueda → reservas` (`:121-125`). **`reservas` no puede importar `busqueda`**, así que no reutiliza `BusquedaService.MENSAJE_*`.

**Modelo** (`docs/modelo-datos.md`):

- `:25` `reserva`: id, usuario_id, espacio_id, estado (`PENDIENTE_PAGO, CONFIRMADA, EXPIRADA, CANCELADA`), monto_total, precio_hora_aplicado, creada_en, expira_en, cancelada_por, cancelada_en. Tipos sin fijar.
- `:26` `reserva_bloque`: id, reserva_id, espacio_id, fecha, hora_inicio, activo. Se recomienda `hora_inicio SMALLINT`.
- `:42` y `architecture.md:72`: bloque único (espacio_id, fecha, hora_inicio) solo para activos, con índice único parcial `WHERE activo`.
- Máquina de estados (`architecture.md:294-295`, RN-03): `PENDIENTE_PAGO → CONFIRMADA | EXPIRADA`; `CONFIRMADA → CANCELADA`. **`PENDIENTE_PAGO → CANCELADA` no existe.**

**Cómo se libera un bloque.** Poniendo `activo = false` en sus filas, al expirar (HU-12) o cancelar (HU-15). Las filas no se borran: HU-14 debe mostrar el horario de reservas pasadas, y por eso existe `activo`. Una `CONFIRMADA` conserva `activo = true`.

**Migraciones.** Ocupadas: `V1_1`–`V1_5`, `V2_1`–`V2_4`, `V3_1__suspension_cuenta`, `V3_2__indices_busqueda`. **Siguiente libre: `V3_3`** (p. ej. `V3_3__reservas.sql`). Confirmar antes de unir (`docs/conventions.md:71-95`; `spring.flyway.out-of-order=true`). `ddl-auto=validate` (`application.properties:12`): `hora_inicio SMALLINT` debe mapearse a `short`, como `HorarioSemanal`, o fallará el arranque. Precedente de nombres: `uq_…`, `ix_…`, `ck_…`.

**Bosquejo de columnas** *(propuesta, no está en los documentos)*: `id BIGINT GENERATED ALWAYS AS IDENTITY`; `usuario_id` FK a `usuario`; `espacio_id` FK a `espacio`; `estado VARCHAR(20)` con `CHECK`; `precio_hora_aplicado NUMERIC(8,2)`; `monto_total NUMERIC(10,2)`; `creada_en` y `expira_en TIMESTAMPTZ`. En `reserva_bloque`: `CHECK (hora_inicio BETWEEN 0 AND 23)`, `activo BOOLEAN NOT NULL DEFAULT TRUE`. Índices: `CREATE UNIQUE INDEX uq_reserva_bloque_activo ON reserva_bloque (espacio_id, fecha, hora_inicio) WHERE activo`; `ix_reserva_vencimiento ON reserva (expira_en) WHERE estado = 'PENDIENTE_PAGO'`; `ix_reserva_usuario_estado ON reserva (usuario_id, estado)`.

## 7. Expiración (CA2)

- **No existe ningún proceso programado:** cero coincidencias de `@Scheduled`, `@EnableScheduling`, `@EnableAsync` o `TaskScheduler` en `2_Backend`. `EspacioYaApplication.java` no activa nada.
- **§9-8** (`docs/requerimientos.md:295`), **abierto:** "Expiración de reservas y envío de recordatorios (RF-D-02, RF-D-10): requieren un proceso programado. La simplificación de §5 solo descarta jobs para la disponibilidad y la liquidación, no para estos casos; confirmarlo en el diseño."
- `architecture.md:337` prohíbe colas y módulos nuevos sin discutirlo. `@Scheduled` no agrega dependencias: viene en `spring-context` *(conocimiento general)*.
- `Clock`: bean UTC, inyectable. `T/RelojControlable.java` tiene `avanzar(Duration)`, y las IT lo registran con `@Primary`. Se prueba llamando directamente al método de expiración tras avanzar 15 minutos.

**Opciones:**

| Opción | Ventaja | Problema |
|--------|---------|----------|
| Solo job | Estado y auditoría reales | Hasta un intervalo de retraso reteniendo un bloque ya vencido |
| Solo perezosa al consultar | Sin scheduler | Nadie escribe `EXPIRADA` ni la auditoría (R) si nadie consulta. `activo` sigue en `true` y **el índice bloquea para siempre**. `horasLibres` es `readOnly` y además público |
| **Ambas, con filtro en lectura** | Exacta al segundo y durable | Más piezas |

**Recomendación: tres capas**, cada una con su motivo:

1. **Lectura (sin escribir):** puerto, conteo de pendientes y consultas de HU-13 filtran `CONFIRMADA OR (PENDIENTE_PAGO AND expira_en > ahora)`. Es el "vigente" del contrato del puerto. Vigente si `ahora < expira_en`.
2. **Job** cada minuto: materializa `EXPIRADA`, `activo = false` y la auditoría (R). Una transacción por reserva con `TransactionTemplate` (precedente `RegistroService.java:58`), para que un fallo no deshaga el lote. Es seguro tras un reinicio porque se basa en `expira_en`.
3. **Al crear:** dentro del bloqueo del espacio, expira las vencidas que **chocan** con las horas pedidas antes de insertar. Sin esto, durante el intervalo del job el usuario vería un bloque libre y recibiría un falso conflicto.

**Trampas:**

- *(conocimiento general)* Hibernate ejecuta los `INSERT` antes que los `UPDATE` pendientes, y con `IDENTITY` los inserta al persistir. Sin un `flush()` explícito tras liberar los bloques vencidos, el `INSERT` de `reserva_bloque` choca con el índice.
- **Orden de bloqueos:** crear toma `espacio → reserva`; HU-13 confirmará con `espacio → reserva`. El job **no debe** bloquear `espacio` después de bloquear `reserva`, o habrá interbloqueo. No lo necesita: solo escribe en sus filas.
- Carrera job/webhook (HU-13): ambos deben bloquear la fila de `reserva` y revalidar estado y vigencia. `FOR UPDATE SKIP LOCKED` en el job evita esperas *(conocimiento general)*.
- Con `@EnableScheduling` activo en las IT, el job correría en segundo plano sobre la BD de prueba, en paralelo a los `DELETE` de `prepararBase`. Se necesita una propiedad para apagarlo, en `2_Backend/src/test/resources/config/application.properties`.
- `GET` de una reserva vencida aún no expirada por el job: debe mostrarla como `EXPIRADA` (estado calculado con `ahora`), sin escribir.

## 8. Monto (CA1)

- RN-18 (`requerimientos.md:227`): precio único por hora. `monto_total = precio_hora × nº de bloques`. Con `BigDecimal` de escala 2 por un entero **no hay redondeo** (coherente con `RoundingMode.UNNECESSARY`).
- Moneda: soles implícitos, sin columna. Se muestra con `formatoPrecio` (`F/pages/espacios/textosEspacios.js:12-14`: `S/ 0.00`).
- Tipo: el máximo por hora es 100 000.00, así que 24 bloques dan 2 400 000.00, que **no cabe** en `NUMERIC(8,2)`. Usar `NUMERIC(10,2)` en `monto_total`.
- `reserva.precio_hora_aplicado` copia el precio al crear: lo exige HU-07 (`specs/HU-07/design.md:43` DT-17, `requirements.md:36` P13, RN-09). Editar el precio tampoco afecta a una pendiente ya creada.
- Comisión: 10 % solo en la liquidación al arrendador (RF-E-10, RN-10, HU-19). No se cobra al usuario y no entra en el monto. **IGV o impuestos: cero menciones en `docs/`.**
- El frontend no calcula el total (principio 6, `specs/HU-11/design.md:422`): el total solo se ve tras la respuesta del servidor.

**Relación con HU-13.** Tabla de puertos (`architecture.md:132`): `pagos.ReservasPorCobrar`, que implementa `reservas`, para "obtener la reserva vigente con su monto y confirmarla". HU-13 necesitará:

- id, usuario dueño (propiedad, RNF-SEG-19), `montoTotal`, `expiraEn` (el checkout expira con la reserva, RN-08), y vigencia evaluada con `ahora`, no solo por estado.
- `confirmar`: de `PENDIENTE_PAGO` vigente a `CONFIRMADA`, con el bloqueo de `espacio` del contrato DT-10 ("al crear **o confirmar**", `architecture.md:131`) y bloqueo de la fila de `reserva`. **`retenerSiLibres` no sirve para confirmar**, porque las horas ya no están libres: las retiene la propia reserva.
- Datos del arrendador y del espacio para el correo (HU-13 CA2), sin exponerlos en la API.
- HU-13 CA3 reintenta mientras la reserva siga vigente.

¿Definir ya el puerto? Ver decisión 6. Eduardo es dueño de ambas historias.

## 9. Máximo 3 pendientes (D)

- Regla: RN-05 (`requerimientos.md:214`) y HU-12 (D). RNF-SEG-17 (`:162`) la lista junto a los rate limits, y §8 (`:277`) cita HU-12 entre quienes reutilizan el rate limiting.
- **`LimitadorIntentos` no sirve:** cuenta eventos en memoria dentro de una ventana (`B/comun/limite/LimitadorIntentos.java:23-37`), y aquí el cupo se libera al pagar o expirar. `FiltroLimiteIntentos` va por IP, y el límite es por usuario. Es **regla de negocio**: `count` de `PENDIENTE_PAGO` vigentes del usuario de la sesión.
- Respuesta: **no está especificada**. `DemasiadosIntentosException` da 429 pero el manejador fuerza el texto genérico (`ManejadorGlobalErrores.java:38-42`). `ReglaNegocioException` da 400 con su mensaje.
- **Carrera:** el bloqueo de `espacio` no serializa a un mismo usuario reservando dos espacios a la vez: dos solicitudes pueden contar 2 y terminar en 4. `UsuarioRepository.findParaActualizar` existe (`:22-24`) pero es de `cuentas`. Ver decisión 4.

## 10. Auditoría (R)

- `AuditoriaService.registrar(EventoAuditoria)` (`B/comun/auditoria/AuditoriaService.java:20-23`), `@Transactional`: se une a la transacción del llamador. `EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle)`. La fecha la pone el `Clock`.
- **`usuario_id` e `ip` admiten nulos** en BD (`V1_2__auditoria.sql:3,7`) y en la entidad. Precedentes: sin IP, `CreacionAdminService.java:75-76` (proceso sin HTTP); sin usuario, `InicioSesionService.java:72-73,80-82` (login con un correo que no existe).
- Convención: constantes `ACCION_*` en el servicio, en mayúsculas (`CREACION_ESPACIO`, `BLOQUEO_FECHA_CREADO`). Entidad en minúsculas, `detalle` como `clave=valor;…`.
- Propuesta: `RESERVA_CREADA` y `RESERVA_EXPIRADA`, entidad `reserva`, `entidadId` = id de la reserva. Detalle: `espacioId=…;fecha=…;horas=14,15;monto=…`. Expiración sin sesión: decisión 12.
- Los intentos rechazados no se auditan (precedente `specs/HU-08/design.md` DT-6, y la historia solo pide creación y expiración).

## 11. Errores y mensajes

- `B/comun/error/`: `ReglaNegocioException` (400, con campo opcional), `ValidacionException` (400, varios campos), `AccesoDenegadoException` (403), `RecursoNoEncontradoException` (404), `DemasiadosIntentosException` (429), `CredencialesInvalidasException` (401). `ErrorDTO(mensaje, errores)`.
- **`DataIntegrityViolationException` no tiene manejador global**: cae en `inesperado` → 500 (`ManejadorGlobalErrores.java:87-96`). Se traduce en cada servicio.
- **"Horario no disponible" no existe** en ningún `.java`, `.js` o `.jsx`; hay que crear la constante. CA3 pide el texto "horario no disponible".
- Precedentes de traducción: `RegistroService.java:57-62`, `RegistroArrendadorService.java:81`, `CreacionAdminService.java:61-65`. Patrón común: `TransactionTemplate` **por fuera**, `saveAndFlush` dentro y `catch` fuera de la transacción. Con `@Transactional` en el método, el fallo llega al confirmar y no se puede capturar ahí. Tras la violación, la transacción de PostgreSQL queda abortada, así que no se puede auditar en ella.
- En CA3 hay dos caminos: el normal es la comprobación bajo bloqueo; el `catch` de la restricción es la última defensa (`architecture.md:222`). Conviene distinguir por nombre de restricción, para no presentar otro fallo de integridad como conflicto de horario.
- Mensajes reutilizables: `MENSAJE_FECHA_INVALIDA` y `MENSAJE_FUERA_DE_VENTANA` están duplicados en `CalendarioEspacioService.java:50-51` (`espacios`, importable) y `BusquedaService.java:37-38` (`busqueda`, no importable desde `reservas`). Campo `fecha`.
- Fechas en DTO como texto validado (HU-08 DT-14): `String` con conversión en el servicio, para señalar el campo. Enteros estrictos: `FichaEspacioDTO.SoloEntero` (`:94`).

## 12. Frontend

- **Kit disponible** (`F/components/ui/`): `Alerta`, `Boton` (`variante`, `cargando`, `deshabilitado`, `tipo`, `onClick`; con `cargando` muestra "Enviando…"), `Campo`, `EtiquetaEstado`, `Galeria`, `Imagen`, `Modal`, `SelectorBloques`, `Tarjeta`.
- **`SelectorBloques` existe** (`F/components/ui/SelectorBloques.jsx`, HU-08): con `onCambiar`, cada libre es `<button aria-pressed>` y devuelve el arreglo ordenado. **No valida consecutivos**: lo hacen la pantalla y el servidor (`docs/frontend.md:161`).
- Colores: `--bloque-libre-*`, `--bloque-seleccionado-*`, `--bloque-ocupado-*` en `F/styles/tokens.css:39-45`.
- `EtiquetaEstado` (`F/components/ui/EtiquetaEstado.jsx:5-20`): **no tiene estados de reserva**. Hay que agregar `PENDIENTE_PAGO` (atención), `EXPIRADA` (neutro) y, según `docs/frontend.md:132`, `CONFIRMADA` (éxito) y `CANCELADA` (neutro). Sin enum conocido muestra el valor crudo.
- `F/api/`: `cliente.js`, `tokenSesion.js`, `cuentas.js`, `espacios.js`, `administracion.js`, `busqueda.js`. **Falta `api/reservas.js`**; `peticion` con `autenticada: true` por defecto.
- No existe `F/pages/reservas/`. Tampoco hay ningún `setInterval` o `setTimeout` en `F`: **no hay precedente de cuenta regresiva**, que `docs/frontend.md:132` pide ("con el contador de 15 min"). Si se crea un componente, va al kit y se anota en "Pantallas" del `design.md` (`:167-169`).
- Selección ligada a la fecha: seguir el patrón de `FichaEspacio.jsx:35,69-70` (guardar el valor junto a su clave `consultaBloques` y derivar), que evita un efecto para vaciarla.

**Pantallas necesarias:**

| Pantalla | Archivo | Qué hace |
|----------|---------|----------|
| Ficha ampliada | `pages/busqueda/FichaEspacio.jsx` (M) | Selección, validación de consecutivos como ayuda, botón "Reservar"; sin sesión → login con retorno; tras error, `Alerta` y refrescar bloques |
| Login ampliado | `pages/cuentas/IniciarSesion.jsx` (M), posiblemente `ProveedorSesion.jsx` (M) | Retorno |
| Resumen | `pages/reservas/ResumenReserva.jsx` (nuevo, `RutaProtegida`) | Espacio, fecha, horario, monto, `EtiquetaEstado`, cuenta regresiva; en cero, relee del servidor. HU-13 pondrá ahí "Pagar" |
| Apoyo | `api/reservas.js`, `pages/reservas/textosReservas.js`, `EtiquetaEstado.jsx` (M), `App.jsx` (M) | |

## 13. Requerimientos

**Fila de la matriz** (`docs/requerimientos.md:250`): HU-12 · Épica D · R1 · S3 (9) · RF-D-01…04 · SEG-01, 07, 09, 12, 17 · RN-03, RN-05 · depende de HU-03, HU-08, HU-11 · programa Eduardo Saldaña · prueba Ariel Enriquez · sin patrón.

| ID | Texto (resumido fielmente) | Línea |
|----|---------------------------|-------|
| RF-D-01 | Usuario autenticado reserva uno o más bloques libres consecutivos: "pendiente de pago", monto en servidor, retención de 15 minutos | 92 |
| RF-D-02 | Expirar sin pago confirmado a los 15 minutos y liberar los bloques | 93 |
| RF-D-03 | Ante dos confirmaciones simultáneas, reserva a uno solo y "horario no disponible" al otro | 94 |
| RF-D-04 | Sin sesión, redirigir al login y devolver a la ficha | 95 |
| RNF-SEG-01 | Identidad siempre de la sesión | 146 |
| RNF-SEG-07 | Todo se valida en servidor; montos solo ahí | 152 |
| RNF-SEG-09 | Restricción única por espacio y bloque en BD | 154 |
| RNF-SEG-12 | Auditoría: quién, qué, cuándo, IP; incluye reservas | 157 |
| RNF-SEG-17 | Rate limiting; incluye "3 reservas pendientes por usuario" | 162 |
| RN-03 | Estados de la reserva | 212 |
| RN-05 | Máximo 3 pendientes por usuario; uno o más bloques consecutivos | 214 |

**§8 (`:270-284`):** se reutilizan auditoría, rate limiting (ver § 9), manejador global, sesión y cálculo de disponibilidad. HU-12 no crea ninguno nuevo, salvo el proceso programado.

**§9:** punto 1 resuelto (PostgreSQL por los índices parciales para SEG-09 y ACID para RF-D-03). Punto 8 **abierto**: HU-12 cierra la parte de expiración; los recordatorios quedan para HU-25.

## 14. Decisiones previas que condicionan HU-12

| Origen | Qué dejó |
|--------|----------|
| HU-07 DT-17 / P13 | Copiar el precio a `precio_hora_aplicado` |
| HU-08 DT-2 | Usar `VentanaReserva` para validar; una hora empezada no se reserva |
| HU-08 DT-4 | `hora_inicio SMALLINT` |
| HU-08 DT-6 | Pendiente vigente también impide bloquear fecha; conflictos como 400 sin campo; 409 descartado explícitamente (alternativa 5, `design.md:778-780`) |
| HU-08 DT-7 | Firma del puerto, `ObjectProvider`; HU-12 solo agrega su bean |
| HU-08 DT-10 | Tomar el bloqueo de `espacio` al crear y confirmar |
| HU-08 (`history.md:526`) | Confirmar la firma con Eduardo |
| HU-09 D1, S9 | Filtrar por arrendador activo; decidir qué pasa con reservas de suspendidos |
| HU-09 S12 | Si hay colisión de `V3_x`, renombrar |
| HU-10 DT-6, D7 | Sobrescribir `bloquesReservadosDelDia` (recomendado) |
| HU-11 D2, § 13 | Selección y botón en la ficha; ruta de retorno `/espacios/:id?fecha=` |
| HU-11 A2 | Mostrar ocupados queda abierto para HU-12 si lo pide; hoy solo libres |
| HU-11 obs. 1 (`history.md:666`) | Armar el retorno con la fecha en uso |

---

## Riesgos y decisiones abiertas para el spec

### Decisiones de producto (para el dueño)

**1. ¿Quién puede reservar?**
- A) Solo rol `USUARIO`; `ARRENDADOR` y `ADMIN` → 403 genérico, y la ficha no les ofrece el botón.
- B) Todo autenticado, salvo el dueño del espacio.
- C) `USUARIO` y `ARRENDADOR`, salvo sobre lo propio.
- **Recomendación: A.** Es lo que sugieren actores, HU-14 y el panel, y elimina el caso de auto-reserva. Un arrendador que quiera reservar usa una cuenta de usuario. Requiere aclarar que "usuario autenticado" significa rol `USUARIO`.

**2. ¿A dónde va el usuario tras reservar?**
- A) Pantalla propia `/reservas/:id` con `GET /api/reservas/{id}` (dueño o 404), resumen y contador.
- B) Solo una `Alerta` en la ficha.
- **Recomendación: A.** Con B, al recargar la reserva desaparece, y hasta HU-13 y HU-14 no habría cómo verla. HU-13 reutiliza esa pantalla para "Pagar".
- Sin modal de confirmación: "Reservar" crea directamente, porque nada se cobra aún.

**3. Expiración.**
- Las opciones están en § 7.
- **Recomendación: las tres capas** (filtro en lectura, job de 1 minuto, liberación de vencidas al crear). Cierra §9-8 para RF-D-02. Propiedades: intervalo y activación, apagado en tests.
- Sin esto, CA2 no se cumple o aparecen falsos conflictos.

**4. Máximo 3 pendientes: respuesta y carrera.**
- Respuesta: A) 400 con mensaje específico ("Ya tienes 3 reservas pendientes de pago…"); B) 429 genérico.
- **Recomendación: A.** Es RN-05, el dato es del propio usuario, el texto genérico no le dice qué hacer, y no toca `comun`.
- Carrera: A) nuevo método `MANDATORY` en `CuentaService` que bloquee la fila del usuario (reutiliza `findParaActualizar`); B) `pg_advisory_xact_lock` por usuario en `reservas`; C) aceptar el riesgo.
- **Recomendación: A.** Orden fijo `usuario → espacio → reserva`. Si se prefiere no tocar `cuentas`, B.

**5. Código HTTP de CA3.**
- A) 400 sin campo; B) 409 con excepción y manejador nuevos.
- **Recomendación: A**, por HU-08 DT-6 y su alternativa 5. La ficha refresca los bloques ante cualquier error de reserva. Texto propuesto: "Horario no disponible. Elige otro bloque." (contiene el de CA3).

**6. ¿Definir ya `pagos.ReservasPorCobrar`?**
- A) No: crear solo `ReservaService` y dejar en el dominio `esVigente(ahora)` y `expirar(ahora)`; HU-13 crea `pagos` y su puerto.
- B) Crear ahora `pagos/service/puertos/` e implementarlo sin consumidor.
- **Recomendación: A** (YAGNI). B crearía un paquete vacío, con una firma sin validar. Dejar anotado en `design.md` qué necesitará HU-13 (§ 8).

**7. Retorno tras el login (CA4).**
- Mecanismo: A) `state.retorno` de React Router; B) `?retorno=` en la URL.
- **Recomendación: A.** No es manipulable con un enlace y `IniciarSesion` ya lee `state`. B obliga a validar contra redirección abierta (solo rutas internas, no `//`).
- Cambiar **ambas** salidas de `IniciarSesion` (`:25` y `:36`).
- Sesión caducada (401): incluir el retorno en `ProveedorSesion` leyendo `window.location`; afecta a todas las rutas protegidas. **Recomendación: incluirlo**, es el caso real más probable, avisando a Jorge Quispe (HU-03).
- ¿Conservar los bloques elegidos al volver? **Recomendación: no.** CA4 solo pide volver a la ficha, y los bloques pueden haber cambiado.

**8. Bloques que empiezan en menos de 15 minutos.**
- A) Permitirlos (la regla actual: basta con que no hayan empezado). B) Exigir una anticipación mínima.
- **Recomendación: A.** Ningún documento pide otra cosa. Aviso: se puede pagar con el bloque ya empezado, y la cancelación daría 0 %.

**9. Suspensión mientras hay pendientes (HU-09 S9).**
- A) La pendiente sigue y expira sola; HU-13 comprueba `cuentaActiva` antes de cobrar. B) Cancelarlas al suspender.
- **Recomendación: A.** B toca HU-09 y agrega `PENDIENTE_PAGO → CANCELADA`, que no existe. Igual para un espacio pausado u oculto. Las `CONFIRMADA` son tema de HU-15.

**10. Alcance de una reserva.**
- Una sola fecha, horas enteras consecutivas, sin cruzar medianoche (`hora_fin` 24 + 0 h del día siguiente son dos reservas). Sin tope propio de horas: el horario ya acota a 24.
- **Recomendación: aceptar**, y validar en el servidor: lista no vacía, sin duplicados, de 0 a 23, consecutiva.
- Orden de validación: formato → consecutivos → cupo → bloqueo → disponibilidad.

**11. Abuso de la retención.** Un usuario puede renovar 3 pendientes indefinidamente, o abarcar un día entero, y bloquear espacios sin pagar. HU-12 no pide más que el cupo. Tampoco se puede soltar una pendiente: esperará 15 minutos. **Recomendación: documentarlo como riesgo aceptado** y no agregar reglas ni transiciones nuevas.

### Decisiones técnicas (para el spec-author)

**12. Auditoría de la expiración.**
- A) `usuario_id = null`, `ip = null`, detalle con `origen=PROCESO`. B) `usuario_id` del dueño de la reserva.
- **Recomendación: A.** "Quién" fue el sistema, y `entidadId` lleva a la reserva y a su usuario. Ya hay eventos con `usuario_id` nulo (login fallido, § 10).

**13. Forma de `retenerSiLibres`.**
- **Recomendación:** que devuelva un record con `id`, `arrendadorId`, `nombre` y `precioHora` leídos **bajo el bloqueo** (p. ej. `EspacioRetenido`), y lance excepción en caso contrario. Evita una segunda lectura, la carrera de precio y el problema de instancia desactualizada.
- Distinguir: espacio no visible → 404 genérico (como `FichaService`); hora no libre → conflicto; fecha fuera de ventana → 400 en `fecha`, antes del bloqueo.
- Documentar el cambio en `DisponibilidadService` y `architecture.md`.

**14. Nombre del espacio en reservas ya creadas.**
- A) Método nuevo en `CatalogoEspaciosService` sin filtro de visibilidad (`nombres(ids)`), ampliando su comentario "para busqueda". B) Copiar el nombre a `reserva`, con cambio en el modelo.
- **Recomendación: A**, que además sirve a HU-14.

**15. Columnas `cancelada_por` y `cancelada_en`.**
- A) Dejarlas para HU-15 con su migración. B) Crearlas ya.
- **Recomendación: A.** `modelo-datos.md:3` dice que cada historia crea lo que necesita, y su tipo no está definido. Avisar a Kevin Chan.

**16. Interfaz `ReservaService`.** **Recomendación:** crear la interfaz con `crear` y `consultar` e implementación aparte, como `CuentaService`, porque `architecture.md:136` la prevé pública. La implementación del puerto va en una clase separada y el job en otra (`ExpiracionReservas`). `@EnableScheduling` en una configuración propia, con `@ConditionalOnProperty` o equivalente.

**17. Pruebas.**
- Agregar `@Primary` a `BusquedaControllerIT` y `EspaciosControllerSoporte`, y corregir `architecture.md:225` (§ 1).
- Pruebas de reservas: IT de concurrencia real (dos hilos con la misma hora: un 201 y un 400, `reserva_bloque` con una fila activa); prueba directa del índice con `JdbcTemplate`, activa frente a inactiva; IT de "vencida pero aún no expirada por el job: se puede reservar de nuevo", que protege la trampa del `flush`.
- Limpieza: las FK sin cascada hacen fallar `DELETE FROM espacio`/`usuario` en cada `prepararBase` que comparta contexto. `TestcontainersConfiguration` crea un contenedor por contexto de Spring, así que el riesgo afecta a las IT del mismo contexto en caché. Limpiar `reserva_bloque` y `reserva` en las IT de HU-12.
- Unitarias de dominio con `RelojControlable`: al menos una por CA (RNF-CAL-01).

**18. Docs del mismo PR.** `docs/modelo-datos.md` (tipos e índices), `docs/architecture.md` (método nuevo, firma de `CuentaService` si aplica, proceso programado, línea 225), `docs/frontend.md` (estados, contador, retorno), `docs/requerimientos.md` §9-8 (cerrar la parte de expiración), y aclarar en §4.1 o §8 que el límite de 3 pendientes es regla de negocio y no usa `LimitadorIntentos`.

### Fuera del alcance de HU-12

Pago y confirmación (HU-13), "Mis reservas" (HU-14), cancelación (HU-15), correos de reserva (ninguna CA de HU-12 los pide), recordatorios (HU-25), `ParticipanteDatosPersonales` (HU-22), y mostrar bloques ocupados en la ficha.
