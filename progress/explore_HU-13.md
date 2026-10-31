# Exploración — HU-13 · Pago con tarjeta

- **Fecha:** 2026-10-30 · **Rama:** `feature/HU-13-pago-tarjeta` (desde `develop` en `1fbf140`) · **Dueño:** Eduardo Saldaña · **Prueba:** Ariel Enriquez
- **Alcance:** solo lectura. No se modificó nada fuera de este archivo; no se ejecutaron builds ni tests.
- **Prefijos:** `B = 2_Backend/src/main/java/pe/edu/ulima/espacioya`, `T = 2_Backend/src/test/java/pe/edu/ulima/espacioya`, `F = 3_frontend/src`.
- **Procedencia, marcada en cada caso:**
  - Sin marca: hecho del repositorio, con su archivo.
  - *(general)*: comportamiento de Hibernate, Spring, PostgreSQL o del navegador que no está en el repo ni se comprobó ejecutando.
  - *(web)*: documentación externa leída el 2026-10-30; las fuentes están al final, numeradas `[n]`.
  - *(notas)*: base de conocimiento personal del dueño, citada por el nombre de la nota.

## Resumen: lo que más condiciona el spec

1. **El módulo `pagos` no existe.** No hay paquete, tabla `pago`, propiedades ni referencias a un proveedor en el código. El único rastro es documental.
2. **Stripe y RN-08 chocan.** Una sesión de Stripe Checkout no puede expirar antes de 30 minutos [1][2] y la reserva dura 15. Con Stripe, "el checkout expira con la reserva" solo se logra cerrándola por API. Ver § 8.
3. **RN-08 dice que el pago tardío "no ocurre", pero sí puede ocurrir** con cualquier pasarela asíncrona: pago aprobado a tiempo y webhook que llega después, o carrera con el job. El spec debe definir qué se hace. Ver § 1 y decisión de producto 2.
4. **El proveedor está sin resolver por una razón práctica.** Stripe cumple casi todo, pero Perú no está en su lista de países [3] y no se pudo confirmar que un estudiante peruano abra un sandbox. Mercado Pago sí opera en Perú, pero su documentación dice que los pagos con credenciales de prueba **no envían notificaciones** y prohíbe `localhost` [4]. Conviene probarlo a mano antes del spec. Ver decisión 1.
5. **`id_transaccion_pasarela` único puede chocar con los reintentos.** En Stripe un `PaymentIntent` sobrevive a los rechazos dentro de la misma sesión [5], así que usarlo como id impediría registrar varios `RECHAZADO`. Ver § 7.
6. **El límite de 3 fallos no se puede garantizar al 100 %** con checkout alojado: los intentos ocurren en la pasarela y la plataforma los conoce por webhook, después. Además, la ventana de 30 min es mayor que la vida de la reserva (15 min). Ver § 5.
7. **`pagos` no puede importar `reservas` ni `espacios`.** Solo `pagos → cuentas`. Todo dato de la reserva o del espacio tiene que llegarle por el puerto `ReservasPorCobrar`.
8. **`EspacioRetenido` no es el lugar para el arrendador.** `docs/architecture.md:263` dice que HU-13 lo amplía, pero ese record solo lo devuelve `retenerSiLibres`, que HU-13 no usa. Ver § 4.

---

## 1. Reservas (HU-12)

### Piezas existentes

| Pieza | Dónde | Qué hace |
|---|---|---|
| `EstadoReserva` | `B/reservas/domain/EstadoReserva.java` | `PENDIENTE_PAGO`, `CONFIRMADA`, `EXPIRADA`, `CANCELADA` |
| `Reserva` | `B/reservas/domain/Reserva.java` | `pendiente` :56; `esVigente` :68 (`PENDIENTE_PAGO` y `ahora < expiraEn`); `estaVencida` :72; `estadoEn` :76; `segundosRestantes` :80; `expirar` :84. **No hay `confirmar()`** |
| `ReservaServiceImpl` | `B/reservas/service/ReservaServiceImpl.java` | `crear` :83 sin `@Transactional`, con `TransactionTemplate` y `catch` fuera; `crearEnTransaccion` :117; `consultar` :104 |
| `ExpiracionReservas` | `B/reservas/service/ExpiracionReservas.java` | `expirarVencidas` :50 (una transacción por id); `liberarVencidas` :66 (`MANDATORY`); `expirarSiSigueVencida` :74 (revalida tras el bloqueo); `expirar` :80 (`usuario_id` e `ip` nulos, `origen=PROCESO`) |
| `ProgramacionExpiracion` | `B/reservas/service/ProgramacionExpiracion.java:14,23` | `@ConditionalOnProperty` y `fixedDelay` |
| `ReservasDelEspacioImpl` | `B/reservas/service/ReservasDelEspacioImpl.java:37,45` | Implementa el puerto de HU-08; `pagado = CONFIRMADA` :55 |
| `ReservaRepository` | `B/reservas/repository/ReservaRepository.java` | `findByIdAndUsuarioId` :20; `contarVigentes` :22; `findIdsVencidas` :28; `findParaActualizar` :33 (`PESSIMISTIC_WRITE`); `findVencidasQueRetienen` :38 (`PESSIMISTIC_WRITE`) |
| `ReservaBloqueRepository` | `B/reservas/repository/ReservaBloqueRepository.java` | `findByReservaIdOrderByHoraInicioAsc` :17; `desactivarDeReserva` :21-23; `bloquesActivos` :25; `bloquesActivosDelDia` :35 |
| `EspacioRepository.findParaReservar` | `B/espacios/repository/EspacioRepository.java:31-33` | `PESSIMISTIC_WRITE` sin filtrar por dueño |
| `CuentaService.bloquearCuenta` | `B/cuentas/service/CuentaServiceImpl.java:226-230` | `MANDATORY`; bloquea `usuario` |

### Qué falta para `pagos.ReservasPorCobrar` y `Reserva.confirmar()`

Lo que ya pide `specs/HU-12/design.md` § 12 y `docs/architecture.md:259-263`, más lo que encontré:

1. **Puerto `pagos.service.puertos.ReservasPorCobrar`:** lo define `pagos`, lo implementa `reservas`. Debe agregarse a la tabla de `docs/architecture.md:127`, donde ya está anunciado (:132).
2. **`Reserva.confirmar(Instant ahora)`:** `IllegalStateException` si `!esVigente(ahora)`, igual que `expirar` :84. Los bloques no cambian: siguen `activo = true`, y `ReservasDelEspacioImpl` pasa a informarlos `pagado`. Eso activa por primera vez `MENSAJE_RESERVA_PAGADA` en `CalendarioEspacioService.java:130`.
3. **Método nuevo en `espacios`** que solo tome el bloqueo (ver abajo).
4. **Datos para el correo:** hoy `reservas` no puede obtener `arrendadorId` (§ 4).

`confirmar` no necesita tocar el cupo: `contarVigentes` :22 solo cuenta `PENDIENTE_PAGO`, así que confirmar lo libera solo.

### Cómo tomar el bloqueo del espacio sin `retenerSiLibres`

`retenerSiLibres` (`DisponibilidadServiceImpl.java:125-138`) no sirve por dos motivos: exige que las horas estén `LIBRE` (`:134`) y las ocupa la propia reserva, y filtra `PUBLICADO` y `cuentaActiva` (`:127-128`), lo que convertiría en 404 un pago ya hecho si el espacio se pausó.

Propuesta: `DisponibilidadService.bloquearEspacio(espacioId)` con `@Transactional(propagation = MANDATORY)`, que llama a `findParaReservar` sin filtros. Puede devolver un record con `arrendadorId` y `nombre` leídos bajo bloqueo, lo que resuelve también § 4. `reservas → espacios` ya está permitida.

**Por qué hace falta ese bloqueo:** es el contrato HU-08 DT-10 (`ReservasDelEspacio.java:13-16`, `docs/architecture.md:131`), y además ordena a `confirmar` frente a `crear`, que dentro de su bloqueo de espacio ejecuta `liberarVencidas`.

**Bloqueo de `usuario`: no hace falta en el webhook.** Solo protege el cupo y confirmar lo reduce. Tomar un subconjunto respetando el orden no rompe `usuario → espacio → reserva`.

### Trampa: leer la reserva antes de bloquearla

El webhook conoce `reservaId` pero necesita `espacioId` para bloquear primero el espacio. Si para eso carga la entidad `Reserva` dentro de la misma transacción, `findParaActualizar` devolvería la instancia ya cargada, sin refrescar `estado` *(general: Hibernate no refresca una entidad ya presente en el contexto; `Reserva` no tiene `@Version`; confirmar con una IT)*. Es el mismo motivo por el que `retenerSiLibres` exige ser "la primera lectura" (`DisponibilidadService.java:28`).

Opciones: leer `espacioId` con una proyección (`select r.espacioId from Reserva r where r.id = :id`), o en una transacción anterior. `espacioId` no cambia, así que es seguro.

### Cómo compiten webhook y job por la misma fila

Orden propuesto en el webhook: `espacio` → `findParaActualizar(reserva)` → `ahora = Instant.now(reloj)` **después** de los bloqueos → revalidar → `confirmar` e insertar `pago`. Es la misma lección de `ReservaServiceImpl.java:124-126`.

| Quién gana | Qué pasa |
|---|---|
| **Webhook, antes de vencer** | `CONFIRMADA`. El job espera en `findParaActualizar` :75; PostgreSQL relee la fila ya actualizada *(general)*; `estaVencida` da `false` y no hace nada (R16). El `ahora` del job es de antes del bucle (`:51`), pero no importa: el estado ya no es `PENDIENTE_PAGO` |
| **Job primero** | `EXPIRADA`, bloques inactivos, auditoría. El webhook ve `EXPIRADA` y entra al camino de pago tardío |
| **`crear` de otro usuario, capa 3** | Tiene el bloqueo del espacio y expira la vencida con `liberarVencidas`. El webhook espera el espacio y luego ve `EXPIRADA`, con los bloques ya de otro |
| **Webhook tras `expira_en`, fila aún `PENDIENTE_PAGO`** | `esVigente` da `false` aunque nadie haya tocado los bloques. Es el caso más frecuente de "tardío" y ocurre aunque el pago se haya aprobado a tiempo |

**Sin interbloqueo:** `crear` usa `usuario → espacio → reserva`; el webhook, `espacio → reserva`; el job, solo `reserva`, y nunca `espacio` (`ExpiracionReservas.java:22-24`).

### Pago aprobado para una reserva que acaba de expirar

- `requerimientos.md:217` (RN-08): "El checkout expira junto con la reserva; no hay reembolso de pagos tardíos porque ese caso no ocurre."
- `architecture.md:382`: "Reembolso de pagos que llegan tras expirar la reserva | Simplificada: el checkout expira con la reserva".

**Ninguna de las dos dice qué hacer si llega.** Y puede llegar: el webhook es asíncrono; Stripe reintenta [6] y Mercado Pago también, a los 15 y 30 minutos [4]. El código no debe confirmar una `EXPIRADA`, cuyos bloques pueden ser de otro (RNF-SEG-09). Tampoco debe dar error: la pasarela reintentaría sin fin. El mínimo es responder 2xx, registrar el `pago`, auditar y no tocar la reserva. Qué pasa con el dinero es la decisión de producto 2.

---

## 2. Módulo `pagos`

**No existe.** `Glob` de `B/pagos/**` sin resultados; `pago` no aparece en ninguna migración.

- **Paquete:** `architecture.md:52` lo asigna a HU-13, 16, 18, 19, 28. Estructura `controller`, `dto`, `service`, `domain`, `repository` y `service/puertos/` (`:43-44`, `conventions.md:56-65`).
- **Dirección de dependencias:** `architecture.md:122`: `comun ← cuentas ← espacios ← reservas → pagos → cuentas`.
  - `reservas` importa `pagos` (lo usará HU-15 con `PagoService`, `:58`).
  - `pagos` importa `cuentas` y `comun`.
  - `pagos` **no** importa `reservas` ni `espacios`. El diagrama no muestra `pagos → espacios`; si el spec la necesitara, debería justificarla. Con el puerto no hace falta.
- **Puertos:** `:132` `pagos.ReservasPorCobrar` (HU-13), `:133` `ReservasLiquidables` (HU-19), `:134` `ReclamosAbiertos` (RN-12), y `:130` `cuentas.ParticipanteDatosPersonales`, que también implementará `pagos` en HU-22.
- **`PagoService`:** se menciona como interfaz pública (`:58`, `:136-137`), pero no existe. HU-13 la crea o la deja para HU-15, según lo que necesite ya.
- **`CuentaService`:** `B/cuentas/service/CuentaService.java`. No hay método para obtener el correo de una cuenta; ver § 4.
- **Adapter:** `:63-65` y `:359` prohíben el SDK fuera del adaptador; `:345` fija `PasarelaPago`, adaptador y `CheckoutFacade` "reserva vigente → monto en servidor → checkout".
- **Tabla `pago`** (`modelo-datos.md:27`): id, reserva_id, id_transaccion_pasarela (único), monto, estado (`PAGADO`, `RECHAZADO`, `REEMBOLSADO`, `REEMBOLSO_FALLIDO`), ultimos4, creado_en. `RESERVA ||--o{ PAGO` (`:82`): varias filas por reserva, coherente con los reintentos.
- **Tabla `reembolso`** (`:28`): id, pago_id, monto, origen, estado, intentos, creado_en. Es de HU-16: no se crea ahora.
- **Restricciones:** `:44` único de `id_transaccion_pasarela`; `:45` un reembolso por pago y origen; `:59` FK en todas las relaciones; `architecture.md:73`.
- **Estados:** `architecture.md:324-326`. No existe un estado "en proceso": mientras el checkout está abierto no hay fila que lo represente (decisión técnica 2).

---

## 3. Precedentes de Adapter

| Interfaz | Implementación | Cómo se elige hoy | En pruebas |
|---|---|---|---|
| `comun/almacenamiento/AlmacenamientoArchivos` | `AlmacenamientoLocal` (`@Component`, `:18`) | No se elige: hay una sola. Solo la usan `ArchivosPrivados` y `ArchivosPublicos` | Directorio temporal por propiedad (`src/test/resources/config/application.properties:8`) |
| `cuentas/service/NotificadorCorreo` | `NotificadorCorreoSimulado` (`@Component`, `:8`) | Una sola implementación | Cinco `NotificadorFalso` con `@Primary` |

- **Selección por propiedad:** `@ConditionalOnProperty` existe en dos sitios, ambos para **encender o apagar** un bean, no para elegir entre dos: `CreadorAdmin.java:18` y `ProgramacionExpiracion.java:14`. `@Profile` no se usa en ningún archivo. HU-13 sería el primer caso de dos implementaciones alternativas.
- **Forma propuesta, sin fábrica escrita a mano (decisión del dueño, `progress/history.md:780`):** dos clases con `@ConditionalOnProperty(name = "espacioya.pagos.pasarela", havingValue = "...")`. Spring inyecta la única que exista en `PasarelaPago`.
- **Reemplazo en pruebas:** `@TestConfiguration` con `@Bean @Primary`, importada con `@Import` (`T/reservas/controller/ReservasControllerSoporte.java:54,93-101`). Con `@ConditionalOnProperty` basta además `espacioya.pagos.pasarela=simulada` en `src/test/resources/config/application.properties`.
- **Sin llave no arranca:** `CifradorCampos`, `LlaveCifrado` y `FirmadorUrl` fallan al iniciar si falta la llave (`progress/history.md:196`, HU-02 R38). Es buen precedente para el adaptador real, con una diferencia: como el bean es condicional, solo exigirá llaves cuando esté activo.
- **Riesgo:** un valor por defecto `simulada` que llegue a UAT deja la app "confirmando" sin pasarela real. Ver decisión técnica 7.

*(notas)* [[Patrón Adapter]] lista literalmente "cuando integras una pasarela de pago o un proveedor externo" y advierte: no meter lógica de negocio en el Adapter (solo traduce) y no dejar llamadas directas al Adaptee. [[DIP - principio de inversión de dependencias]] usa el mismo ejemplo y recuerda que con inyección de dependencias en las pruebas se pasa una implementación falsa. [[Patrón Facade]]: "si el cliente debe hablar con muchas clases para completar una sola operación, usa una Facade", con la advertencia de no convertirla en "clase Dios": debe coordinar, no hacer el trabajo. [[Patrón Factory Method]]: su regla es no aplicarlo sin variación real; aquí hay dos variantes, pero Spring ya resuelve cuál instanciar, y eso respalda la decisión del dueño.

---

## 4. Correos (CA1, CA2)

- **`NotificadorCorreo`:** `B/cuentas/service/NotificadorCorreo.java`. Seis métodos, sin `default`, sin nada de pagos. HU-25 lo moverá a `comun/correo`.
- **Simulado:** `NotificadorCorreoSimulado.java`. Solo escribe en el log que no se envió nada, sin correo ni motivo (Ley 29733). **Hoy no se envía ningún correo real**, así que CA1 y CA2 se prueban con un doble que registra la llamada, como en HU-06 y HU-09.
- **Después del commit:** `CuentaServiceImpl.despuesDelCommit` :272-283, con `TransactionSynchronizationManager`. Atrapa `RuntimeException`, registra `log.warn` y no revierte. Es `private static`: `pagos` tendría que repetirlo o moverlo a `comun` (`@TransactionalEventListener` no se usa en el repo).
- **Observación de HU-04:** `RecuperacionContrasenaService.java:106` notifica **dentro** de la transacción; `progress/history.md:340-343` lo anota como deuda para HU-25. HU-13 sigue el camino bueno: después del commit.
- **Fallo del correo:** no debe alterar la respuesta al webhook, porque un error haría que la pasarela reintente; la idempotencia lo absorbe, pero el correo no se reenviaría.

**Datos y de dónde salen sin romper dependencias:**

| Dato | Origen | Existe hoy |
|---|---|---|
| Fecha y horario | `ReservaBloqueRepository.findByReservaIdOrderByHoraInicioAsc`; ya se arma en `ReservaServiceImpl.java:111-114` | Sí, dentro de `reservas` |
| Nombre del espacio | `CatalogoEspaciosService.nombres` :42, sin filtro de visibilidad | Sí |
| Monto | `Reserva.getMontoTotal` / `pago.monto` | Sí |
| Últimos 4 y referencia | Evento de la pasarela (§ 7) | No |
| `arrendadorId` | `espacio.arrendador_id`. `EspacioRetenido` y `FichaPublicada` no lo traen, `CatalogoEspaciosService` tampoco | **No** |
| Correo del usuario y del arrendador | `usuario.correo`; `CuentaService` no lo expone | **No** |

**Propuesta:** la implementación del puerto (`reservas`) arma un record con fecha, horas, nombre, `usuarioId`, `arrendadorId` y monto, usando el valor que devuelve `bloquearEspacio`. `pagos` pide los correos con un método nuevo de `CuentaService` (p. ej. `correoDe(id)`, sin sesión ni rol, como `cuentaActiva`). Así `pagos` no toca `espacios`.

**Ampliar `EspacioRetenido` no sirve:** solo lo devuelve `retenerSiLibres`, que no está en el camino de confirmar. La frase de `architecture.md:263` debe corregirse en el mismo PR.

**Dobles afectados:** agregar métodos no `default` a `NotificadorCorreo` obliga a tocar `NotificadorCorreoSimulado` y cinco dobles: `RevisionIdentidadControllerIT:130`, `CuentaAdminControllerIT:123`, `UsuarioControllerIT:94`, `ArrendadorControllerIT:104` y `RecuperacionContrasenaControllerIT:118`. HU-04 y HU-09 ya pagaron ese costo.

**Requisitos:**

- `requerimientos.md:195` (RNF-USA-04): aviso por correo en eventos clave, incluido el pago.
- `requerimientos.md:161` (RNF-SEG-16): el arrendador no ve correo, teléfono ni datos de pago del usuario; los correos no incluyen tarjeta. Por eso CA2 solo lleva fecha, horario y espacio.
- HU-25 CA3: los correos de pago no se pueden desactivar. No hay que condicionarlos a `recordatorios_activos`.
- HU-25 (I)(E): sin datos de tarjeta; destinatarios obtenidos de la reserva en el servidor.
- `Usuario.registrar` :56 no recibe nombre, así que un `USUARIO` normalmente tiene `nombre` nulo. El correo no debe depender de él.

---

## 5. Anti-carding (D)

**Texto:** "Máximo 3 intentos fallidos por reserva cada 30 min" (`docs/historias/HU-13.md:26`); RNF-SEG-17, `requerimientos.md:162`: "3 pagos fallidos por reserva / 30 min". §8 (`:277`) incluye a HU-13 entre las que reutilizan rate limiting.

**Tres hechos que la historia no dice:**

1. **La ventana de 30 min no llega a rotar.** Una reserva vive 15 min y no se reintenta tras expirar. En la práctica la regla es "3 fallos por reserva".
2. **La plataforma se entera tarde.** Con checkout alojado, los intentos ocurren en la página de la pasarela. En Stripe el cliente reintenta en la misma sesión hasta que expire [5]. Un cuarto intento puede colarse antes de que llegue el webhook del tercero. El control real es cortar la sesión al llegar al tercero.
3. **Por reserva no es por persona.** Con el cupo de 3 pendientes (S7 de HU-12) son hasta 9 fallos cada 15 minutos por usuario, y se renueva. La pasarela tiene sus propios controles, pero en HU-13 solo se pide "por reserva".

**`LimitadorIntentos` frente a contar filas:**

| | `LimitadorIntentos` con clave `pago:<reservaId>` | Contar `pago` con `RECHAZADO` |
|---|---|---|
| Encaje técnico | Hay precedente de clave no IP (`InicioSesionService.java:71,81`: `superaLimite` + `registrar`). `FiltroLimiteIntentos` **no** sirve: su clave es grupo e IP (`ReglaLimite.java:68`) | `countByReservaIdAndEstado…` con un índice |
| Reinicio | Se pierde (mapa en memoria, `LimitadorIntentos.java:16`) | Se conserva |
| Varias instancias | Una sola (`progress/history.md:356`) | Da igual |
| Fila duplicada | Un webhook repetido contaría doble salvo control aparte | El `UNIQUE` lo impide |
| Costo | Ninguna columna | Las filas se guardan igual por (R) |

**Recomendación: contar filas.** La fila `RECHAZADO` ya se escribe para cumplir (R), es transaccional y sobrevive a un reinicio. Es lo que no resuelve `LimitadorIntentos`, y evita sumar otro contador en memoria. La ventana se aplica igual con `creado_en >= ahora - 30 min`, por fidelidad al requisito y por si cambia la duración de la reserva.

**Respuesta de la API:** `POST` de iniciar pago con 3 fallos → `DemasiadosIntentosException` → 429 con `ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS` ("Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo.", `ManejadorGlobalErrores.java:38-42`). Sin campo y sin detalle. El texto no admite otro mensaje (`DemasiadosIntentosException.java:5`), y "espera un momento" no es exacto si la reserva ya no podrá pagarse. Ver decisión de producto 4.

---

## 6. Webhook

- **Hoy:** `ConfiguracionSeguridad.java:63` deja `anyRequest().authenticated()`, así que el webhook daría 401. Falta `requestMatchers(HttpMethod.POST, "<ruta exacta>").permitAll()` en :43-63, una sola ruta y sin comodines.
- **Sesión:** `FiltroAutenticacionSesion` (`:34-38`) deja pasar lo que no trae `Bearer`, sin autenticar. El webhook no recibe `@AuthenticationPrincipal`.
- **CSRF:** desactivado para toda la API (`:33`). No hay nada que excluir.
- **CORS:** configurado para `/api/**` (`:76`). La pasarela llama de servidor a servidor, sin `Origin` *(general)*: no aplica y no hay que agregar orígenes.
- **Filtro de IPs admin:** solo `/api/admin/**`. No afecta.
- **Cuerpo crudo:** la firma se calcula sobre los bytes exactos [6]. `@RequestBody byte[] cuerpo` con `@RequestHeader` para la firma *(general: `ByteArrayHttpMessageConverter` acepta cualquier tipo)*. No usar un DTO ni `String`: reconstruir el JSON o decodificar mal invalida la firma. Ningún filtro del repo lee el cuerpo antes (`FiltroLimiteIntentos`, `FiltroAutenticacionSesion`).
- **Verificación:** dentro del adaptador. `PasarelaPago` recibe bytes y encabezados y devuelve un evento neutro del dominio, o falla si la firma no es válida. Comparación en tiempo constante [6], por ejemplo `MessageDigest.isEqual` *(general)*. Stripe incluye un timestamp y sus librerías usan 5 min de tolerancia [6]; `Clock` inyectado para probarlo.
- **Respuesta a la pasarela:**
  - Firma inválida o cuerpo ilegible → 400 sin cuerpo, sin procesar nada [6].
  - Evento válido, ya procesado, de un tipo que no interesa, reserva desconocida, monto distinto o pago tardío → **200**. Otro código solo causa reintentos inútiles.
  - Error transitorio nuestro → 5xx, para que reintente.
  - No se debe usar `ManejadorGlobalErrores` para esto: el 400 de una `ReglaNegocioException` provocaría reintentos.
- **Rapidez:** Stripe pide responder 2xx pronto y sugiere una cola para volúmenes altos [6]; `architecture.md:365` prohíbe colas sin discutirlo. A este volumen, procesar en la misma petición es razonable. Mercado Pago da 22 segundos [4]. Stripe espera hasta 10 s al webhook antes de redirigir al cliente, salvo con el CLI, donde redirige de inmediato [7].
- **Rate limit:** RNF-SEG-17 no lo pide para el webhook. Una `ReglaLimite` por IP podría devolver 429 a la propia pasarela; no se recomienda.
- **Infraestructura:** `4_Infraestructure/arquitectura-aws.html:70` ya dibuja el webhook firmado por el Load Balancer. Bajo `/api/` pasa por CloudFront y ALB.
- **Firma de prueba:** las IT calculan el HMAC con un secreto de prueba y hacen `POST` con `MockMvc`. Así se prueba el camino real, incluidos firma mala y cuerpo alterado.

---

## 7. Idempotencia

**Precedentes de traducción:**

- `ReservaServiceImpl.java:92-99,174-177`: `DataIntegrityViolationException` fuera de la transacción y `getMostSpecificCause().getMessage()` con el nombre de la restricción; cualquier otra se relanza (HU-12 DT-7, R23).
- `RegistroService.java:59`, `RegistroArrendadorService.java:81`, `CreacionAdminService.java:63`: mismo `catch` fuera.

Con `@Transactional` la violación llegaría al confirmar y no se podría traducir: por eso `TransactionTemplate`.

**Doble defensa:** primero el bloqueo y la lectura de `pago`, que serializa dos webhooks iguales; el `UNIQUE` es la última defensa. Stripe advierte que la función puede llamarse "multiple times, possibly concurrently" [7].

**Qué hacer con cada evento:**

| Evento | Resultado |
|---|---|
| Aprobado, reserva vigente, monto y moneda iguales | Fila `PAGADO`, `confirmar`, auditoría, correos tras el commit |
| Aprobado repetido (mismo id) | 200, sin cambios ni correos ni auditoría de confirmación |
| Dos eventos distintos del mismo pago | Stripe: `checkout.session.completed` y `payment_intent.succeeded` son eventos distintos. Escuchar uno solo [6] |
| Aprobado con otro id, y la reserva ya tiene `PAGADO` | Segundo cobro (dos sesiones abiertas). No confirmar otra vez; auditar. Evitarlo con una sola sesión abierta (decisión técnica 2) |
| Rechazado | Fila `RECHAZADO`, auditoría, cuenta para el límite. La reserva sigue `PENDIENTE_PAGO` |
| Rechazado repetido | 200 |
| **Fuera de orden:** rechazado después del aprobado | Fila `RECHAZADO` propia, sin tocar `CONFIRMADA` ni `PAGADO`. Stripe no garantiza el orden [6]; por eso `created` no sirve para ordenar |
| **Monto o moneda distintos** | No se confirma. Auditoría `PAGO_MONTO_DISTINTO`, 200. El monto se compara contra `reserva.monto_total` en el servidor (T de la historia) |
| Reserva inexistente | 200, auditoría sin usuario |
| Reserva no vigente | § 1 y decisión de producto 2 |

**El punto delicado: qué id se guarda.**

- **Stripe:** el cliente "can re-attempt the payment on the same Checkout Session until it expires" [5]; el fallo llega como `payment_intent.payment_failed`, sobre el mismo `PaymentIntent` *(general: su `id` no cambia entre intentos)*. Si `id_transaccion_pasarela` guarda el `pi_…`, un segundo rechazo viola el `UNIQUE`, y el aprobado final choca con el `RECHAZADO` anterior. Hay que guardar el id del **intento** (el cargo `ch_…`). `last_payment_error` permite llegar al cargo [5], pero **no pude confirmar** que siempre exista un cargo en todo tipo de fallo. Hay que probarlo en el sandbox.
- **Mercado Pago:** cada intento es un pago con su propio id *(general)* y el evento trae `data.id`, con el detalle en `GET /v1/payments/{id}` [4]. Encaja directo.

**Columnas:**

| Columna | Observación |
|---|---|
| `reserva_id` | `BIGINT NOT NULL REFERENCES reserva (id)`, sin cascada. **Las limpiezas de las IT hacen `DELETE FROM reserva`** (`ReservasControllerSoporte.java:119`): un soporte con filas `pago` debe borrarlas antes |
| `id_transaccion_pasarela` | `VARCHAR(255)`, único. Debe ser `NOT NULL` si siempre hay id de intento. Si la pasarela puede no darlo, usar un id sintético derivado del id del evento antes que `NULL`: PostgreSQL permite varios `NULL` en un `UNIQUE` *(general)* y se perdería la idempotencia |
| `monto` | `NUMERIC(10,2)`, igual que `reserva.monto_total` (`V3_3`), con `CHECK > 0`. Las pasarelas usan unidades menores: Stripe, céntimos [8] |
| `ultimos4` | `CHAR(4)`, **nullable**: un rechazo puede no traerlo. `CHECK` de 4 dígitos. Stripe no lo trae en la sesión; hay que consultarlo *(general)* |
| `estado` | `VARCHAR(20)` con `CHECK`, incluidos los valores futuros de HU-16, como `reserva.estado` |
| `creado_en` | `TIMESTAMPTZ`, del `Clock` |

**Índices sugeridos:** `(reserva_id, estado)` para contar rechazos, y un índice único parcial `(reserva_id) WHERE estado = 'PAGADO'`: una reserva no se cobra dos veces, y sigue el estilo de `uq_reserva_bloque_activo`.

**Moneda:** `modelo-datos.md` no la tiene. El sistema solo maneja soles (`formatoPrecio`, RN). Basta una constante `PEN` en el adaptador y comparar la moneda del evento.

**Llamadas externas fuera de los bloqueos:** si hace falta consultar la pasarela (últimos 4, Mercado Pago), hacerlo **antes** de abrir la transacción.

---

## 8. Vencimiento del checkout (CA4, RN-08)

| Capacidad | Stripe | Mercado Pago |
|---|---|---|
| Fijar vencimiento | `expires_at`, **mínimo 30 min**, máximo 24 h [1][2] | `expires`, `expiration_date_from`, `expiration_date_to`; sin mínimo indicado en la página [9] |
| Cerrar por API | `POST /v1/checkout/sessions/:id/expire`, solo si está `open`; da error si ya no lo está [2][10] | No confirmado |
| Evento al expirar | `checkout.session.expired` [2] | No confirmado |
| Pago en curso al vencer | No documentado | No documentado [9] |

**CA4 tiene tres capas:**

1. **Servidor:** el `POST` de iniciar pago rechaza si `!esVigente(ahora)`, usando `Reserva.esVigente` y no `getEstado()` (`architecture.md:239`). Respuesta 400 con mensaje, y la pantalla ofrece reservar de nuevo. Esto cubre CA4 tal como está redactado.
2. **Pantalla:** `ResumenReserva` ya pasa a `EXPIRADA` y oculta el botón (`:76,92`).
3. **Sesión abierta en la pasarela:** es lo difícil. Con Mercado Pago se fija `expiration_date_to`. Con Stripe la sesión vive al menos 30 min: hay que **cerrarla por API**, y eso debe dispararse desde nuestro lado.

**Cerrar la sesión con Stripe, opciones:**

- Desde el job de `ExpiracionReservas`: `reservas → pagos` está permitido, pero el job corre cada minuto, así que queda una brecha de hasta 1 min, y `expirar` también ocurre en `liberarVencidas`.
- Un proceso propio de `pagos` que cierre las sesiones cercanas a vencer, con margen. Cierra antes, pero quita segundos al usuario.

En ambos casos, la llamada va fuera de la transacción, y un fallo solo se registra: el webhook sigue protegiendo.

**Captura manual (solo Stripe):** `payment_intent_data[capture_method]=manual` autoriza sin capturar [11]. Al recibir `payment_intent.amount_capturable_updated`, el backend captura si la reserva sigue vigente o cancela la autorización si no [11]. Con eso, **un pago tardío no mueve dinero** y RN-08 pasa a ser cierta sin reembolsos. Costo: dos operaciones más en el adaptador y un paso entre confirmar y capturar. No pude confirmar el valor de `payment_status` en este flujo; hay que usar ese evento y probarlo.

**Sobre "expira junto con la reserva":** en cualquier pasarela, mantener abierto el checkout hasta el último segundo garantiza webhooks tras el vencimiento. Fijar el cierre un poco antes reduce el caso. Ver decisión de producto 3.

---

## 9. Frontend

- **`F/pages/reservas/ResumenReserva.jsx`:** el botón va en el bloque `PENDIENTE_PAGO` (:92-97). `docs/frontend.md:252`: "HU-13 pone aquí 'Pagar'". Solo relee al llegar a cero (`recargar`, :47), sin sondeo.
- **`F/api/reservas.js`:** `crearReserva`, `obtenerReserva`. Según `frontend.md:40-41`, lo de pagos va en `F/api/pagos.js` (nuevo).
- **`F/components/ui/CuentaRegresiva.jsx`:** sirve tal cual. `frontend.md:191`: solo avisa; decide el servidor.
- **Botón "Pagar":** único primario (`frontend.md:65,117`), `Boton` con `cargando` para no enviar dos veces (`:210`).
- **Ir a la pasarela:** `window.location.assign(url)` en la **misma pestaña**. El token vive en `sessionStorage` (`frontend.md:232`), que se conserva al volver en esa pestaña y no se comparte con otra nueva abierta después *(general)*.
- **Volver:** `success_url` y `cancel_url` se arman en el servidor con `espacioya.frontend.url` (`application.properties:25`): `/reservas/:id` con un indicador (`?pago=exito`, `?pago=cancelado`). No hace falta ruta nueva en `App.jsx`, que ya protege `/reservas/:id` (:124-131). `RutaProtegida` no guarda el retorno (`frontend.md:248`); la sesión dura 30 min de inactividad y la reserva 15, así que el caso es marginal.
- **El indicador de la URL no confirma nada.** Solo elige qué mensaje mostrar mientras se relee; quien decide es el webhook (RNF-CUM-02).
- **Tras volver, el webhook puede no haber llegado.** La pantalla seguiría en `PENDIENTE_PAGO`, y si el contador llega a cero mostraría "Se acabó el tiempo" a quien ya pagó. Hacen falta:
  - Un sondeo corto con `obtenerReserva` cuando `?pago=exito`, con un texto del tipo "Estamos confirmando tu pago…".
  - Evitar `SE_ACABO_EL_TIEMPO` durante ese sondeo.
  - Un límite y un texto final si no llega.
- **Con Stripe CLI** el redirect es inmediato [7]: en local este estado se verá siempre.
- **CA3:** en checkout alojado, el rechazo lo muestra primero la pasarela [5]; la plataforma puede no enterarse hasta el webhook. Si el usuario vuelve, ve "Pagar" y puede reintentar. Para mostrar un mensaje propio, la pantalla necesita saber que hubo un rechazo: exige ampliar el DTO o una consulta (decisión técnica 10).
- **Mensaje genérico:** Stripe da el motivo en su propia página, fuera de nuestro control. El nuestro no debe repetir el código de rechazo (`frontend.md:213-215`).
- **`EtiquetaEstado`:** `F/components/ui/EtiquetaEstado.jsx:5-25`. Faltan `PAGADO`, `RECHAZADO`, `REEMBOLSADO` y `REEMBOLSO_FALLIDO`. Existe `RECHAZADA` (identidad), no `RECHAZADO`. Colores en `frontend.md:142`: `PAGADO` éxito; `RECHAZADO` y `REEMBOLSO_FALLIDO` error; `REEMBOLSADO` neutro. **La guía no fija los textos**; propongo "Pagado", "Rechazado", "Reembolsado", "Reembolso fallido". Solo se agregan los que HU-13 muestre (comentario `:4`).
- **`textosReservas.js`:** ahí van los textos nuevos si la pantalla es de reservas, o un `F/pages/pagos/textosPagos.js`.
- **Dependencias:** con checkout alojado no hace falta ninguna librería de la pasarela en el frontend; agregarla se discute antes (`frontend.md:20`).
- **Reglas:** el frontend nunca envía el monto; `POST` sin cuerpo o con solo el id de la reserva.

---

## 10. Auditoría

- **`AuditoriaService.registrar`** (`B/comun/auditoria/AuditoriaService.java:20-23`): `@Transactional`, se une a la transacción del llamador, así que un rollback deshace también la auditoría.
- **`EventoAuditoria`:** `usuarioId`, `accion`, `entidad`, `entidadId`, `ip`, `detalle`.
- **Tabla:** `V1_2__auditoria.sql`. `usuario_id` e `ip` admiten nulos; `accion VARCHAR(60)`; `detalle TEXT`.
- **Acciones de reservas hoy:** `RESERVA_CREADA` (`ReservaServiceImpl.java:43`), con `espacioId`, `fecha`, `horas`, `monto`; y `RESERVA_EXPIRADA` (`ExpiracionReservas.java:29`).
- **Sin usuario de sesión (S9 de HU-12):** `usuario_id` e `ip` nulos, `origen=PROCESO` (`ExpiracionReservas.java:83-84`, `architecture.md:254`). Para el webhook: `origen=WEBHOOK` y la IP real con `IpCliente.de`. Esa IP es la de la pasarela, y solo es correcta con `PROXIES_CONFIANZA` configurado (`architecture.md:279-281`).

**Acciones sugeridas:** `CHECKOUT_INICIADO` (con usuario e IP), `PAGO_APROBADO`, `PAGO_RECHAZADO`, `RESERVA_CONFIRMADA`, `PAGO_TARDIO`, `PAGO_MONTO_DISTINTO`, `WEBHOOK_FIRMA_INVALIDA`. `entidad` = `pago` o `reserva`. En `detalle`: `reservaId`, id de transacción, monto, `origen`. **Nunca** datos de tarjeta ni la firma ni el cuerpo del evento.

- **`WEBHOOK_FIRMA_INVALIDA`:** puede inundar la tabla si alguien golpea la ruta pública. Alternativa: solo `log.warn`. Se decide en el spec.
- **Quién pagó:** ver decisión técnica 11.
- **Registro de cada transacción (R):** la propia tabla `pago` más la auditoría.

---

## 11. Configuración y secretos

- **`application.properties`:** prefijo `espacioya.<modulo>.…`, con `${VARIABLE:default}`. Las llaves llevan default vacío (`:33-34`, `:46`). Ahí van las nuevas, con un comentario que cite la historia.
- **Propiedades propuestas:** `espacioya.pagos.pasarela=${PAGOS_PASARELA:...}`, `espacioya.pagos.llave-secreta=${PAGOS_LLAVE_SECRETA:}`, `espacioya.pagos.secreto-webhook=${PAGOS_SECRETO_WEBHOOK:}`. Nombres neutros: el proveedor no debería filtrarse a la configuración si cambia.
- **`2_Backend/.env.example`:** una sección con nombres sin valores, como exige `conventions.md:139-140`. Avisar que el secreto de `stripe listen` no es el del endpoint registrado: confundirlos falla siempre la firma [6].
- **Pruebas:** `src/test/resources/config/application.properties`, con el comentario "SOLO PARA PRUEBAS" y valores que no parezcan reales.
- **`init.sh`:**
  - `:84-100` avisa con `[WARN]` por las tres llaves. Las de la pasarela solo aplican con el adaptador real: si se agregan, debe ser condicional.
  - `:282-283`, regla "Secretos trackeados en git": **solo mira nombres de archivo** (`.env`, `.pem`, `.key`, `.p12`, `.jks`, `application-local.`), no contenidos. Una llave `sk_test_…` pegada en un `.properties` no se detecta. `progress/history.md:109-110` ya tenía esa regla como pendiente. Es un cambio del arnés (`sdd: false`).
  - No hay ninguna regla que impida usar el SDK de la pasarela fuera del adaptador; solo lo dice `architecture.md`.
- **`README.md`:** hay que documentar cómo levantar la pasarela en local.
- **Backend `pom.xml`:** sin SDK de pasarela. Agregar uno es una dependencia nueva a justificar (decisión técnica 8).

---

## 12. Migraciones

Lista exacta (idéntica en el directorio de trabajo y en `develop`, verificada con `git ls-tree develop`):

```
V1_1__usuarios.sql              V2_1__documento_identidad.sql    V3_1__suspension_cuenta.sql
V1_2__auditoria.sql             V2_2__espacio.sql                V3_2__indices_busqueda.sql
V1_3__perfil_arrendador.sql     V2_3__revision_identidad.sql     V3_3__reservas.sql
V1_4__sesion.sql                V2_4__disponibilidad.sql
V1_5__token.sql
```

**No hay ninguna `V4_n`. Siguiente número libre del sprint 4: `V4_1`** (`feature_list.json`: HU-13 `sprint: 4`), por ejemplo `V4_1__pago.sql`.

Reglas de `docs/conventions.md:76-95`:

- Actualizar `develop` y revisar la carpeta antes de crearla.
- HU-14, HU-15 y HU-16 son del mismo sprint: si `V4_1` se ocupa antes del merge, renombrarla.
- Solo depende de `V3_3` y `V1_1`, de número menor. Correcto.
- HU-16 referenciará `pago`, así que su número debe ser mayor.

---

## 13. Requerimientos

**Matriz §7, fila HU-13** (`requerimientos.md:251`): E · R1 · S4 (9) · RF-E-01…04 · SEG-07, 11, 12, 17; CUM-01, 02; ARQ-08 · RN-03, RN-08 · depende de HU-12 · Eduardo Saldaña · Ariel Enriquez · Adapter, Facade.

| Id | Texto | Línea |
|---|---|---|
| RF-E-01 | Pagar una reserva pendiente vigente en el checkout de la pasarela; al confirmarse, pasarla a "confirmada" y enviar al usuario un correo con el comprobante | :108 |
| RF-E-02 | Al confirmarse, enviar al arrendador un correo con fecha, horario y espacio | :109 |
| RF-E-03 | Ante un pago rechazado, mensaje genérico y reintentar mientras siga vigente | :110 |
| RF-E-04 | El checkout expira junto con la reserva y se puede reservar de nuevo | :111 |
| RNF-SEG-07 | (T) Todo lo del cliente se valida en el servidor; los montos se calculan solo ahí | :152 |
| RNF-SEG-11 | (T) Webhook con firma verificada e idempotente; reembolsos idempotentes y solo contra el pago original | :156 |
| RNF-SEG-12 | (R) Auditoría de quién, qué, cuándo e IP, incluidas las transacciones | :157 |
| RNF-SEG-17 | (D) Rate limiting; "3 pagos fallidos por reserva / 30 min" | :162 |
| RNF-CUM-01 | PCI DSS: nunca recibe ni guarda datos de tarjeta; checkout de pasarela certificada en modo de prueba; comprobantes solo con los últimos 4 | :170 |
| RNF-CUM-02 | PCI DSS: el estado del pago cambia solo por webhook con firma verificada | :171 |
| RNF-ARQ-08 | Proveedores externos detrás de interfaces propias (Adapter), para cambiarlos sin tocar el dominio | :186 |
| RN-03 | Estados: pendiente de pago (retiene 15 min) → confirmada o expirada; confirmada → cancelada | :212 |
| RN-08 | El checkout expira junto con la reserva; no hay reembolso de pagos tardíos porque ese caso no ocurre | :217 |

**§8, componentes:** `:280` correo (HU-13 reutiliza; HU-25 formaliza); `:283` "Adaptador de la pasarela de pago", primera historia HU-13, lo reutilizan HU-16, HU-26 y HU-28; `:276` auditoría; `:277` rate limiting.

**§9, pendientes:** ninguno habla de la pasarela ni de HU-13. El más cercano, `:295` (§9-8), ya resolvió la expiración de reservas y deja abierto solo el recordatorio de HU-25. **Falta un pendiente por RN-08** (decisión de producto 2).

**Efecto en RNF-CUM-02:** el retorno del navegador no puede cambiar el estado. Stripe sugiere completar también desde la página de retorno consultando su API [7], pero **eso contradice RNF-CUM-02 y la historia** ("Estado cambia solo por webhook"): no se hace.

---

## 14. Lo que HU-13 debe dejar listo sin construirlo

Criterio: no agregar métodos ni columnas para el futuro (`modelo-datos.md:3-5`, YAGNI), pero no cerrarles el camino.

| Historia | Qué necesitará | Qué exige de HU-13 |
|---|---|---|
| **HU-14** Mis reservas | Monto y estado de la reserva. CA2: reservas confirmadas de mis espacios | Que `CONFIRMADA` salga de `Reserva.confirmar`. `ReservaDTO` ampliable sin romper. Nada de datos de pago al arrendador (RNF-SEG-16). Ya avisado: `estadoEn`, no `getEstado` |
| **HU-15** Cancelación | Pago de una reserva y su monto; liberar bloques; correos | Consultar el pago `PAGADO` por `reserva_id`; un solo `PAGADO` por reserva lo vuelve trivial. `PagoService` en `pagos`: `reservas → pagos` ya es válido. Los avisos tras el commit deben ser reutilizables |
| **HU-16** ★ Reembolso | `SolicitudReembolso.ejecutar()`; reintento idempotente; reutiliza el adaptador | **`id_transaccion_pasarela` debe servir para pedir el reembolso.** `PasarelaPago` es interfaz propia: HU-16 le agrega `reembolsar`; HU-13 no lo define. `Pago` con métodos de dominio para estados. Estados en el `CHECK` |
| **HU-19** Liquidación | Reservas usadas y cobradas, monto bruto | `pago.monto` fiable; puerto `ReservasLiquidables`, que no se crea ahora. La fila `PAGADO` no debe existir para pagos tardíos si no se distingue: entraría en la liquidación (decisión de producto 2) |
| **HU-26** Reclamos | Reembolso total o parcial | `reembolso.monto` lo resuelve HU-16; `PAGADO` → `REEMBOLSADO` con reembolso parcial queda para esa historia |
| **HU-28** Historial y comprobantes | Fecha, espacio, monto, estado, últimos 4; PDF | **`ultimos4` debe guardarse desde ahora**: no se recupera después. `pago` no tiene `usuario_id`: la propiedad se valida vía reserva, por un puerto futuro. La lista debe filtrar `RECHAZADO`: CA1 solo muestra pagado y reembolsado |
| **HU-22** Datos personales | `pagos` implementa `ParticipanteDatosPersonales` | Nada ahora. No guardar correos en `pago`, para que no haya nada que anonimizar |
| **HU-25** Correo | Mueve `NotificadorCorreo` a `comun/correo` | Métodos de pago separados y sin condicionar a `recordatorios_activos` |

---

## 15. Decisiones previas que condicionan HU-13

| Origen | Decisión | Efecto |
|---|---|---|
| HU-12 S2 (`requirements.md:30`) | `ReservasPorCobrar` lo crea HU-13 | Confirmado: no existe |
| **HU-12 S5** (`requirements.md:33`) | Una `PENDIENTE_PAGO` sigue y expira sola si se suspende alguien o se pausa el espacio; HU-13 comprueba `cuentaActiva` antes de cobrar | Decisión de producto 6 |
| HU-12 S7 (`progress/history.md:698`) | No se puede soltar una pendiente; 3 pendientes renovables | `PENDIENTE_PAGO → CANCELADA` no existe (`architecture.md:323`). Tras 3 rechazos los bloques quedan retenidos hasta vencer |
| HU-12 S9 | Expiración auditada sin usuario, con `origen=PROCESO` | Precedente del webhook (§ 10) |
| HU-12 DT-1, D4 | Orden `usuario → espacio → reserva` | § 1 |
| HU-12 DT-5 | El proceso no usa `SKIP LOCKED`; revalida con `estaVencida`. Prevé la competencia con el webhook | § 1 |
| HU-12 DT-7 | Sin `@Transactional`, `TransactionTemplate` y `catch` fuera | Patrón del webhook |
| HU-12 desviación de § 6 | `ahora` se toma después de los bloqueos | Aplica en `confirmar` |
| HU-08 DT-10 | Confirmar toma el bloqueo del espacio | § 1 |
| HU-09 D1 (`specs/HU-09/requirements.md:18`) | Espacios de arrendador no `ACTIVA`: se oculta al leer; `cuentaActiva` sin sesión ni rol | `CuentaService.java:92` lo da. `CatalogoEspaciosService.esVisible` :36 cubre además `PUBLICADO` |
| HU-09 S9 | La suspensión no toca reservas | Una reserva sigue viva con el arrendador suspendido |
| HU-09 D3 | La sesión de una cuenta no `ACTIVA` da 401 | Un usuario suspendido no llega al checkout, pero su webhook sí |
| HU-06 S3, HU-09 D4 | Correos tras el commit | § 4 |
| Observación de HU-04 (`progress/history.md:340-343`) | No notificar dentro de la transacción | § 4 |
| HU-12 pendiente 5 (`history.md:769-770`) | `PUBLICADO` implica identidad verificada hasta HU-20 | No comprobar identidad en el checkout |
| `progress/history.md:780` | Factory Method fuera de HU-13 | **Pendiente: confirmar con Ariel Enriquez (PO)** |
| `progress/doc_HU-13.md` § 1.1 | Pide registrar proveedor, idempotencia, quién confirma y cómo se cuentan los intentos | El spec-author debe llenar esas filas |

---

## Notas personales consultadas

Se usaron [[Mapa - Patrones de diseño]], [[Patrón Adapter]], [[Patrón Facade]], [[Patrón Factory Method]] y [[DIP - principio de inversión de dependencias]]; también se revisó [[Mapa - Arquitectura de software]]. **Las notas no cubren** webhooks, idempotencia, PCI DSS, concurrencia de pagos ni pasarelas específicas: esa parte es documentación externa o conocimiento general.

---

## Riesgos y decisiones abiertas para el spec

### Decisiones de producto (para el dueño)

#### 1. Proveedor de la pasarela

| Criterio | Stripe | Mercado Pago (Checkout Pro) | Culqi | Izipay |
|---|---|---|---|---|
| **Checkout alojado** | Sí: `ui_mode` por defecto `hosted_page`, con redirección [1] | Sí: redirige a Mercado Pago *(general; no leí una página que lo afirme para Perú)* | **No:** embebido con `Culqi.open()`; el backend crea el cargo con el token. V4 se retirará pronto [12] | **Embebido** (`KR.attachForm`) en el ejemplo oficial [13]. Redirección no confirmada |
| **Webhook con firma** | Sí: `Stripe-Signature`, HMAC-SHA256, timestamp [6] | Sí: `x-signature` con `ts` y `v1`; detalle en HMAC según su SDK [4][14] | **No documentada** en su página de webhooks [15]: no pude confirmar firma ni autenticación. Incumpliría RNF-CUM-02 hasta demostrar lo contrario | IPN con `kr-hash`, HMAC-SHA-256, en ejemplos de GitHub [13][16]. Portal oficial no leído |
| **Prueba sin trámites de empresa** | Sandbox tras crear cuenta, sin verificar negocio [17]. **Perú no está en la lista** [3]. **No pude confirmar** que se pueda crear la cuenta desde Perú | Cuentas de prueba, hasta 15 [18], y sitio para Perú. **No pude confirmar** los requisitos de la cuenta base | Exige cuenta de comercio con validación de 1 a 3 días hábiles [19]; RUC, RUS o DNI según terceros [20]. Sin negocio, dudoso | Credenciales del Back Office Vendedor [13]. Contrato o evaluación según terceros. Dudoso |
| **Documentación** | Muy completa, con CLI | Completa, en español, con variaciones por país | Suficiente, webhooks muy breves | Principalmente repositorios de ejemplo |
| **Demo local sin URL pública** | **Sí:** `stripe listen --forward-to localhost…` [6] | **No:** `notification_url` debe ser HTTPS y sin `localhost` [4]. Además: "Test payments, created with test credentials, will not send notifications" [4]; existe el botón "Simular" [4] | No confirmado; sin CLI conocido | No: IPN configurada en el Back Office [16] |
| **Expirar a los 15 min** | **No directamente:** mínimo 30 min; se cierra por API [1][2] | Sí, `expiration_date_to`, sin mínimo indicado [9] | No confirmado | No confirmado |
| **Soles (PEN)** | **No confirmado:** no pude leer la lista. El mínimo depende de la moneda de liquidación (0.50 USD) [8], así que **una hora a S/ 1.00 podría no alcanzarlo** (`modelo-datos.md:20`) | Sí, por sitio de Perú *(general)* | Sí *(general)* | Sí *(general)* |

**Observación sobre Mercado Pago:** "test payments" sin notificaciones afectaría a CA1 en la demo. La versión en español de la misma página no lo dice con claridad, y algunas guías indican compras de prueba con cuentas de prueba, cuyas credenciales podrían comportarse distinto. **No pude confirmarlo** (la página de compras de prueba dio 403). Hay que probarlo.

**Opciones:**

- **A. Stripe en modo de prueba.** Mejor encaje técnico: alojado, firma, demo local sin URL pública, buena documentación y captura manual para RN-08. Riesgos: alta de cuenta desde Perú, regla de 30 min y PEN.
- **B. Mercado Pago.** Mejor encaje local y vencimiento nativo. Riesgos: túnel público para el webhook y duda sobre las notificaciones de prueba.
- **C. Culqi o Izipay.** Descartables para esta historia con lo verificado: el primero por firma no documentada y ninguno es claramente alojado ni sin trámites.

**Recomendación: A, condicionada a una prueba de 30 minutos del dueño antes del spec.** Pasos:

1. Crear la cuenta en `dashboard.stripe.com/register` con datos reales.
2. Si deja entrar al sandbox: crear una sesión de Checkout en `pen` con `stripe` CLI y ver que llegue el webhook a `localhost`.
3. Si no deja: pasar a **B** y repetir la prueba de un pago de prueba que llegue por túnel a un endpoint.

**No se recomienda declarar otro país ni datos de empresa falsos** para entrar: abrir cuenta en otro país exige entidad y datos reales de ese país [21], y el informe documenta este trabajo. Un tercero recomienda crear una empresa en EE. UU.: fuera de lugar para este proyecto.

**En todos los casos se mantiene `PasarelaSimulada` para pruebas automáticas**, lo que limita el daño si el proveedor falla.

#### 2. ¿Qué se hace con un pago aprobado que llega con la reserva ya no vigente?

RN-08 dice que no ocurre, pero el spec debe decidirlo.

- **A. Registrar y no confirmar.** Fila con estado propio (p. ej. `PAGADO_TARDIO`), auditoría y aviso; el dinero queda sin resolver hasta una acción manual. Simple, pero deja un pago sin destino; y HU-19 y HU-28 deben ignorar ese estado.
- **B. Captura manual (solo Stripe).** Autorizar y capturar solo si sigue vigente; si no, cancelar la autorización [11]. RN-08 se vuelve cierta: no hay dinero movido ni reembolso.
- **C. Reembolso automático al llegar.** Cumple con el usuario, pero contradice `architecture.md:382` y depende de HU-16.
- **D. Confirmar si la reserva sigue `PENDIENTE_PAGO` y sus bloques siguen activos**, aunque haya pasado `expira_en`. Evita el caso más común, pero contradice `esVigente` (`architecture.md:262`) y necesita A o B para el resto.

**Recomendación:** con Stripe, **B**. Con Mercado Pago, **A** más un cierre anticipado del checkout (decisión 3). En ambos casos, **actualizar RN-08** en `requerimientos.md` y `architecture.md:382`, para que no diga que el caso no existe.

#### 3. ¿El checkout se cierra justo al vencer o un poco antes?

- **A. Al vencer.** El usuario aprovecha los 15 minutos, pero maximiza los tardíos.
- **B. Margen de 1 a 2 minutos.** No iniciar pago ni dejar abierta la sesión en el último tramo. Reduce mucho el caso; el usuario pierde ese tiempo.

**Recomendación: B con 1 minuto**, configurable y con texto claro. Si se elige captura manual, basta **A**, porque el tardío ya no cuesta nada.

#### 4. Al llegar a 3 rechazos, ¿qué ve el usuario y qué pasa con la reserva?

Por HU-12 S7 no se puede soltar: los bloques quedan retenidos hasta vencer. El mensaje genérico actual dice "Espera un momento e inténtalo de nuevo", y puede inducir a error.

- **A. Mantener la reserva hasta que expire**, con un 429 y el mensaje genérico. Cero cambios de regla.
- **B. Expirarla al tercer rechazo.** Libera antes, pero crea una transición nueva y permite liberar a voluntad.

**Recomendación: A.** Además, un mensaje en pantalla que indique reservar de nuevo cuando expire, sin revelar el motivo.

**Sobre el límite por persona:** el requisito solo pide "por reserva". Anotarlo como riesgo aceptado, como S7, y no agregar un límite que nadie pidió.

#### 5. Contenido de los correos

- **Comprobante (CA1):** propuesta: espacio, fecha, horario, monto, últimos 4 y referencia de la operación. Es solo el correo; el PDF es de HU-28. RNF-CUM-01 lo permite.
- **Arrendador (CA2):** fecha, horario y espacio, sin datos del usuario ni monto (RNF-SEG-16).

**Recomendación:** lo anterior. Con el notificador simulado no se envía nada real, y hay que decir eso en la demo.

#### 6. Espacio o arrendador que cambian mientras la reserva está pendiente (S5)

- **Antes del checkout:** S5 solo pide `cuentaActiva`, pero menciona también el espacio pausado u oculto. **Recomendación:** `CatalogoEspaciosService.esVisible`, que cubre ambas cosas, con el mismo 400 de checkout no disponible.
- **Después del checkout, con el pago ya hecho:** **recomendación: confirmar**, sin importar el estado del espacio o del arrendador, porque así lo dice S5 ("sigue y expira sola"), y las `CONFIRMADA` son tema de HU-15. El usuario suspendido se trata igual.

#### 7. ¿Se usa la pasarela simulada en la demo o en UAT?

- **A. Solo en pruebas y desarrollo**, y UAT con la pasarela real en modo de prueba.
- **B. También en UAT** si el proveedor falla.

**Recomendación: A.** La simulada no es una "pasarela certificada" (RNF-CUM-01), y su existencia en UAT sería un camino de confirmación sin firma real. Si se llega a B, debe decirse en el informe.

#### 8. Confirmar con el PO el retiro de Factory Method

Sigue pendiente con Ariel Enriquez (`progress/history.md:780`). No bloquea el spec, pero debe quedar cerrado antes del informe.

### Decisiones técnicas (para el spec-author)

1. **Qué id guarda `id_transaccion_pasarela`.**
   - Opciones: id de la intención (`pi_…`), id del intento (`ch_…`) o id del evento.
   - **Recomendación:** id del intento, y tabla para el id de sesión (siguiente punto). Si el proveedor es Mercado Pago, el id del pago, que ya es por intento.

2. **Dónde vive la sesión de checkout abierta** (para reutilizarla, cerrarla y evitar dos cobros). `pago` no tiene estado "en proceso".
   - Opciones: (a) tabla nueva `checkout_pago` (id, reserva_id, id_sesion, url, expira_en, estado) con índice único parcial para una sesión abierta por reserva; (b) columna en `reserva`, que llevaría datos de la pasarela a `reservas`; (c) estado `INICIADO` en `pago`, que mezcla con el historial y obliga a cambiar el modelo.
   - **Recomendación: (a)**, actualizando `modelo-datos.md`. Reutilizar la sesión abierta en un segundo "Pagar".

3. **Conteo de intentos fallidos.**
   - Opciones: `LimitadorIntentos` con clave por reserva, o contar filas `RECHAZADO`.
   - **Recomendación:** contar filas (§ 5), comprobar al iniciar el pago y cerrar la sesión al tercer rechazo.

4. **Forma del puerto y del bloqueo.**
   - **Recomendación:** `ReservasPorCobrar` con dos operaciones: `obtener(reservaId, usuarioId)` y `confirmar(reservaId, ...)` `MANDATORY`, que devuelve lo necesario para los correos. `DisponibilidadService.bloquearEspacio`, que devuelve `arrendadorId` y nombre. Corregir `architecture.md:263`.

5. **Transacción del webhook.**
   - Orden: consultas externas → `TransactionTemplate` → proyección de `espacioId` → `bloquearEspacio` → `findParaActualizar` → `ahora` → revalidar → `confirmar` → `INSERT pago` → auditoría → `afterCommit` de correos. `catch` por fuera con el nombre de la restricción.
   - Probar la trampa de § 1 con una IT y las carreras con `CountDownLatch`, como `ReservaConcurrenciaIT`.

6. **Quién cierra la sesión al vencer.**
   - Opciones: `ExpiracionReservas` llama a `pagos`, o un proceso propio de `pagos`.
   - **Recomendación:** proceso propio en `pagos`, con la misma forma que `ProgramacionExpiracion` (apagado en pruebas), para no meter HTTP en el bucle del job ni acoplarlo. Si se usa `expiration_date_to`, innecesario.

7. **Selección de implementación.**
   - `@ConditionalOnProperty`, sin valor por defecto que permita arrancar sin pasarela en UAT.
   - **Recomendación:** `simulada` por defecto solo en `src/test/resources/config/application.properties`, y en local definirla como las tres llaves. Sin propiedad, el backend no arranca, con un mensaje claro. Esto da `matchIfMissing=false`, y el fallo es "falta un bean".

8. **SDK o HTTP propio.**
   - Opciones: SDK oficial, o `RestClient` y HMAC propio con `javax.crypto`.
   - **Recomendación:** SDK oficial en el adaptador, porque reduce el riesgo en la firma. Comprobar compatibilidad con Java 25 y Spring Boot 4.1.2, que **no verifiqué**. Justificar la dependencia en `design.md`.

9. **Correos.**
   - Opciones: (a) dos métodos en `NotificadorCorreo` y tocar cinco dobles; (b) interfaz nueva en `pagos`.
   - **Recomendación: (a)**, por precedente (HU-04, HU-06, HU-09) y porque HU-25 ya planea consolidarla. Mover `despuesDelCommit` a `comun` si `pagos` lo necesita, en vez de copiarlo.

10. **Cómo sabe la pantalla del rechazo y del pago en proceso.**
    - Opciones: ampliar `ReservaDTO` con un campo calculado, o `GET` propio en `pagos`.
    - **Recomendación:** un `GET` en `pagos` con el resumen del último intento, para no acoplar `ReservaDTO` (HU-14 lo reutiliza). Respuesta sin ids de pasarela ni datos de tarjeta.

11. **`usuario_id` en la auditoría del webhook.**
    - Opciones: nulo con `origen=WEBHOOK` (S9), o el dueño de la reserva.
    - **Recomendación:** nulo, porque no actuó el usuario; el `usuarioId` va en `detalle` o se obtiene por la reserva.

12. **Rutas.**
    - Propuesta: `POST /api/reservas/{id}/pago` en `pagos.controller` (sesión, rol `USUARIO`, ajena = 404, id como texto, DT-9) y `POST /api/pagos/webhook` público.
    - `conventions.md:47-51` pide plurales: la segunda es una ruta de sistema.

13. **Códigos de respuesta del `POST` de iniciar pago.**
    - 200 o 201 con la URL de la pasarela; 400 sin campo si no está vigente (CA4); 404 si es ajena o no existe; 403 por rol; 429 por intentos; 502 o 503 genérico si la pasarela no responde. Hoy no hay manejador para 502: un fallo daría 500 genérico, que también es aceptable.

14. **Interfaz `PasarelaPago`.**
    - Neutra: `crearCheckout(...)`, `leerEvento(cuerpo, encabezados)` → evento propio (aprobado, rechazado, ignorado), `cerrarCheckout(id)`. Sin tipos del SDK en la firma ni en `domain` (`architecture.md:63-65`).
    - *(notas)* Adapter solo traduce: la regla "es tardío" o "es el tercer rechazo" va en el servicio, no en el adaptador. `CheckoutFacade` coordina; el webhook es otro servicio, no la Facade.

15. **Arnés (`sdd: false`, fuera de la historia).** Ampliar la regla de secretos de `init.sh:282` para buscar contenidos tipo `sk_test_`, `sk_live_` y `whsec_`. Anotarlo en `progress/history.md`.

### Riesgos

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| No se puede abrir la cuenta del proveedor elegido | Alta (Stripe) / media (MP) | Alto: bloquea la demo | Prueba del dueño antes del spec (decisión 1) |
| Pago tardío sin tratar | Media | Alto: pago sin reserva | Decisiones 2 y 3 |
| Sandbox de Stripe reintenta solo 3 veces en pocas horas [6] | Media | Bajo | Reenviar con `stripe events resend` |
| Secreto de `stripe listen` distinto del endpoint de UAT [6] | Media | Medio | `.env.example` y README |
| Tiempo de demo: esperar 15 min para mostrar CA4 | Alta | Bajo | `RelojControlable` en IT; en demo, como HU-12, reserva corta manual |
| Monto bajo rechazado por el mínimo [8] | Media | Medio | Datos de demo sobre S/ 5; manejar el error al crear la sesión |
| Una llave real termina en el repositorio | Baja | Alto | Decisión técnica 15; solo llaves de prueba |
| Doble pago en dos sesiones | Baja | Medio | Decisión técnica 2 e índice parcial |

---

## Fuentes (consultadas el 2026-10-30)

1. Stripe, Create a Checkout Session: https://docs.stripe.com/api/checkout/sessions/create
2. Stripe, Manage limited inventory: https://docs.stripe.com/payments/checkout/managing-limited-inventory
3. Stripe, Global availability: https://stripe.com/global (la lista leída no incluye a Perú)
4. Mercado Pago, Payment notifications (sitio de Perú): https://www.mercadopago.com.pe/developers/en/docs/checkout-pro/payment-notifications
5. Stripe, Migrate to Payment Intents; Card declines; Error handling: https://docs.stripe.com/payments/payment-methods/transitioning · https://docs.stripe.com/declines/card · https://docs.stripe.com/error-handling
6. Stripe, Webhooks: https://docs.stripe.com/webhooks
7. Stripe, Fulfill orders: https://docs.stripe.com/checkout/fulfillment
8. Stripe, Supported currencies: https://docs.stripe.com/currencies (la lista de monedas no pudo leerse)
9. Mercado Pago, Term of preference: https://www.mercadopago.com.ar/developers/en/docs/checkout-pro-preferences/additional-settings/term-of-preference
10. Stripe, Expire a Checkout Session: https://docs.stripe.com/api/checkout/sessions/expire
11. Stripe, Place a hold on a payment method: https://docs.stripe.com/payments/place-a-hold-on-a-payment-method
12. Culqi, Checkout V4: https://docs.culqi.com/es/documentacion/checkout/v4
13. Izipay, Embedded-PaymentForm-JavaScript: https://github.com/izipay-pe/Embedded-PaymentForm-JavaScript
14. Mercado Pago, Payment notifications (versión de Brasil, resultado de búsqueda sobre el SDK): https://mercadopago.com.br/developers/en/docs/checkout-pro/payment-notifications
15. Culqi, Webhooks: https://docs.culqi.com/es/documentacion/pagos-online/webhooks
16. Izipay, ejemplos: https://github.com/izipay-pe/Embedded-PaymentForm-PHP · https://github.com/izipay-pe/Server-PaymentForm-Python-Django · https://github.com/izipay-pe/Server-PaymentForm-.NET
17. Stripe, Set up your account: https://docs.stripe.com/get-started/account/activate
18. Mercado Pago, Cuentas de prueba: https://www.mercadopago.cl/developers/es/docs/your-integrations/test/accounts (versión de Chile, vista solo como resultado de búsqueda)
19. Culqi, Primeros pasos: https://docs.culqi.com/es/documentacion/pagos-online
20. BCP sobre Culqi (tercero): https://www.viabcp.com/pymes/cobros/soluciones-cobro/culqi
21. Stripe Support, Requirements to open an account in another country: https://support.stripe.com/questions/requirements-to-open-a-stripe-account-in-another-country

**Lo que no pude confirmar:** alta de una cuenta Stripe desde Perú, incluso solo para sandbox; PEN como moneda en Stripe; si un pago con cuentas de prueba de Mercado Pago dispara el webhook; requisitos de la cuenta base de Mercado Pago, Culqi e Izipay para solo probar; firma de webhooks de Culqi; existencia de redirección alojada en Izipay; que todo rechazo de Stripe tenga un cargo con id propio; `payment_status` con captura manual; compatibilidad de los SDK con Java 25 y Spring Boot 4.1.2.
