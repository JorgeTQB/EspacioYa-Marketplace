# Arquitectura — Qué significa "hacer un buen trabajo"

> Este documento define el estándar de calidad. Los agentes revisores
> evalúan código contra este archivo y contra el spec de la historia
> (`specs/HU-XX/`). Si no está aquí ni en el spec, no es un requisito.
>
> Modelo de datos: `docs/modelo-datos.md`. Requerimientos (RF, RNF, RN):
> `docs/requerimientos.md`.

## Stack

| Pieza          | Decisión |
|----------------|----------|
| Backend        | Spring Boot (Java) en `2_Backend/`: Spring MVC (API REST), Spring Data JPA (persistencia), Spring Security (autenticación, roles y MFA) |
| Frontend       | React + Vite en `3_frontend/`: SPA que consume la API REST, con tokens CSS y un kit de componentes propio (`docs/frontend.md`) |
| Comunicación   | API REST con JSON sobre HTTPS |
| Base de datos  | **PostgreSQL 17**. El esquema lo crean solo las migraciones Flyway de `2_Backend/src/main/resources/db/migration/`; `1_Database/compose.yaml` solo levanta el servidor local vacío |
| Pagos          | Pasarela certificada PCI DSS en modo de prueba: Mercado Pago Checkout Pro, en soles (checkout alojado + webhook con firma verificada). Se elige con `PAGOS_PASARELA`. **Mercado Pago no está conectado por ahora (HU-13 D8):** el adaptador está listo y UAT usa la pasarela simulada hasta conectarlo, sin pasarela certificada (D7 revisada; C1 de `specs/HU-13/design.md` § 14.13) |
| Archivos       | Fuera de la base de datos, en disco local o en un bucket S3 privado según `ALMACENAMIENTO_TIPO` (HU-29; ver [Almacenamiento](#almacenamiento-qué-deja-hu-29--adapter)). Documentos: almacenamiento privado cifrado, servido con URL firmada de corta duración. Fotos de espacios: sin cifrar y con URL estable `/api/fotos/<UUID>`, públicas solo si el espacio está publicado (decisión del dueño de HU-07) |
| Notificaciones | Solo correo, mediante un componente reutilizable |
| Despliegue UAT | AWS (Learner Lab): ALB con WAF que envía `/api/*` a 2 tareas Fargate del backend y el resto a 1 tarea nginx con el `dist/`; RDS PostgreSQL, S3 privado, correo simulado. Detalle en [`4_Infraestructure/arquitectura-aws.md`](../4_Infraestructure/arquitectura-aws.md) |

Estilo: **N-capas orientada al dominio**, cliente-servidor de 3 niveles,
**monolito modular** (no microservicios), MVC en el backend y DDD ligero
(cada épica es un módulo).

## Principios

1. **Capas claras.** Dentro de cada módulo:
   `controller → service → domain / repository`.
   - `controller` — recibe la solicitud, valida la entrada y convierte DTO.
   - `service` — coordina el caso de uso y las transacciones. Las
     implementaciones `*Impl` de una interfaz van en `service/impl/`
     (ver `docs/conventions.md`).
   - `domain` — entidades y reglas de negocio (estados, políticas, cálculos).
   - `repository` — acceso a datos con Spring Data JPA (DAO).
   Un controlador **nunca** llama directamente a un repositorio.

2. **Núcleo separado de la API.** La lógica de negocio y el acceso a datos no
   dependen de la web: la API REST es un cliente más del núcleo.

3. **Entity frente a DTO.** Las entidades nunca salen del backend. La API
   recibe y devuelve DTO con solo los campos necesarios. Nunca se exponen
   contraseñas, documentos, datos bancarios ni datos de tarjeta.

4. **Un paquete por módulo** dentro de `pe.edu.ulima.espacioya`, cada uno con
   `controller`, `dto`, `service`, `domain` y `repository`:

   | Paquete          | Historias |
   |------------------|-----------|
   | `cuentas`        | HU-01 a HU-05, HU-17, HU-20, HU-22 |
   | `espacios`       | HU-07, HU-08, HU-21 |
   | `busqueda`       | HU-10, HU-11, HU-23, HU-24 |
   | `reservas`       | HU-12, HU-14, HU-15, HU-25 |
   | `pagos`          | HU-13, HU-16, HU-18, HU-19, HU-28 |
   | `administracion` | HU-06, HU-09, HU-26, HU-27 |
   | `comun`          | Transversal: seguridad, auditoría, errores, correo, almacenamiento, cifrado, configuración |

5. **Dependencias en un solo sentido.**
   - Entre módulos solo se usa la interfaz del `service` del otro módulo
     (`reservas` usa `PagoService`, no `PagoRepository`).
   - Sin dependencias circulares. Cuando un módulo de abajo necesita algo del
     de arriba, define una interfaz en `service/puertos/` y el de arriba la
     implementa (ver [Puertos entre módulos](#puertos-entre-módulos)).
   - Todos los módulos pueden usar `comun`; `comun` no depende de ningún módulo.
   - El dominio no depende de Spring Web ni de la pasarela: las integraciones
     externas (pasarela, correo, almacenamiento) van detrás de interfaces
     propias (Adapter).

6. **Reglas de negocio y montos solo en el servidor.** El cliente nunca decide
   montos, roles ni estados. El frontend no contiene reglas de negocio.
   Excepción acotada, pedida por el dueño de HU-13: ver C3 en
   `specs/HU-13/design.md` § 14.13.

7. **Integridad también en la base de datos.** Las reglas críticas se
   garantizan con restricciones, no solo en código:
   - bloque único por (espacio, fecha, hora) solo para bloques activos (HU-12);
   - `pago.id_transaccion_pasarela` único y un solo pago `PAGADO` por reserva, `uq_pago_reserva_pagado` (HU-13);
   - un reembolso por pago y origen (HU-16);
   - una reserva liquidada una sola vez (HU-19);
   - una reseña y un reclamo por reserva (HU-24, HU-26);
   - `usuario.correo` único (HU-01);
   - `perfil_arrendador.hash_documento` único (HU-02);
   - llaves foráneas en todas las relaciones.

8. **POO, SOLID y patrones donde resuelven un problema real.** DAO/Repository
   en toda la persistencia. Los beans de Spring ya son singleton: no se
   programa un Singleton a mano. Los patrones esperados están en
   [Patrones por historia](#patrones-por-historia).

9. **Componentes transversales se construyen una vez** (en `comun`, en el
   primer sprint que los necesita) y se reutilizan: auditoría, rate limiting,
   manejo global de errores, sesión y tokens, correo, almacenamiento con URL
   firmada y cifrado de campos sensibles (`docs/requerimientos.md` §8).

## Flujo de datos

```
navegador (React)
   │  HTTPS / JSON
   ▼
<modulo>/controller   ── valida entrada, DTO ↔ caso de uso
   │
   ▼
<modulo>/service      ── transacción, orquestación
   │        │
   │        ├─→ otro módulo: solo vía su interfaz de service o un puerto
   │        └─→ comun: auditoría, correo, almacenamiento, cifrado
   ▼
<modulo>/domain       ── entidades y reglas
<modulo>/repository   ── Spring Data JPA
   │
   ▼
base de datos relacional (migraciones Flyway)

Integraciones externas (pasarela, correo, archivos) → siempre detrás de un Adapter.
```

## Puertos entre módulos

Cuando un módulo necesita algo de otro que ya depende de él (por ejemplo,
`pagos` confirma reservas pero `reservas` usa `pagos` para reembolsar), no se
crea un ciclo: el módulo de abajo define una interfaz en `service/puertos/` y
el de arriba la implementa (DIP). Dirección permitida:

```
comun ← cuentas ← espacios ← reservas → pagos → cuentas
           ↑         ↑          ↑
           └───── busqueda ─────┘        administracion → (todos)
```

`busqueda → cuentas` existe **solo** por el puerto `ParticipanteDatosPersonales` (HU-24): `busqueda` no usa `CuentaService`.

| Interfaz (la define)                  | La implementa              | Para |
|---------------------------------------|----------------------------|------|
| `comun.ValidadorSesion`               | cuentas                    | Que el filtro de seguridad revise si la sesión está revocada |
| `cuentas.ParticipanteDatosPersonales` | reservas, pagos, busqueda (HU-24) | HU-22: `exportar(TitularDatos) → List<SeccionDatos>` (se une a la transacción, solo lee; nombre de sección único en todo el JSON), `impedimentos(TitularDatos, ahora) → List<Impedimento>` (`MANDATORY`; toma sus bloqueos **antes** de leer, no escribe) y `anonimizar(TitularDatos)` (`MANDATORY`; borra o anonimiza lo suyo, nunca registros contables; archivos con `DespuesDelCommit`). Sin sesión ni rol. Se inyecta en `DatosPersonalesService` como `List<>` ordenada con `@Order` (`DatosPersonalesReservasImpl` 1, `DatosPersonalesPagosImpl` 2, `DatosPersonalesResenasImpl` 3, `DatosPersonalesReclamosImpl` 4 —HU-26—, `DatosPersonalesReportesImpl` 5 —HU-27—): agregar un módulo es agregar un bean (OCP). Ver [Datos personales](#datos-personales-qué-deja-hu-22-hu-24-hu-26-hu-27) |
| `espacios.ReservasDelEspacio`         | reservas                   | HU-08 y HU-21: `bloquesReservados(espacioId, desde, hasta)` → `BloqueReservado(fecha, horaInicio, pagado)` de las reservas activas (`PENDIENTE_PAGO` vigente o `CONFIRMADA`; `pagado` = `CONFIRMADA`). HU-10: método **default** `bloquesReservadosDelDia(espacioIds, fecha)` → `Map<Long, List<BloqueReservado>>` para la búsqueda en lote (por defecto llama a `bloquesReservados` por id; ver nota de HU-12 más abajo). Llega por `ObjectProvider`: sin implementación, ninguna fecha tiene reservas. **Contrato (HU-08 DT-10):** al crear o confirmar una reserva, `reservas` toma el mismo bloqueo del espacio (`EspacioRepository.findParaActualizar` o `SELECT … FOR UPDATE` sobre `espacio`) o relee `bloqueo_fecha` y el horario dentro de su transacción, para no reservar en un día que se acaba de bloquear |
| `pagos.ReservasPorCobrar`             | reservas                   | HU-13: `deUsuario(reservaId, usuarioId)` → `ReservaPorCobrar` (monto, `expiraEn`, vigencia y si el espacio se ve); `bloquear(reservaId)` → `ReservaBloqueada` (`MANDATORY`: toma espacio y reserva y evalúa la vigencia después; lleva los datos de los correos); `confirmar(reservaId, evaluadaEn)` (`MANDATORY`; `IllegalStateException` si no es vigente) |
| `pagos.ReservasLiquidables`           | reservas                   | HU-19: `describir(Collection<Long> reservaIds)` → `List<ReservaLiquidable>` (reserva, arrendador, espacio y su nombre, fecha, horas, `finEn` = fin del último bloque en Lima, `admitida` = `CONFIRMADA` o `CANCELADA` por el usuario con `monto_reembolso = 0`, `cancelada`). `readOnly`, una consulta por lote, sin sesión ni rol; colección vacía → `List.of()` sin consultar; una reserva sin bloques no aparece. Lo implementa `ReservasLiquidablesImpl` (no usa `ReservaDAO`: es una consulta del sistema con ids que salen de `pagos`). Se inyecta directo, como `ReservasPorCobrar` |
| `pagos.ReservasDelUsuario`            | reservas                   | HU-28: `todas(usuarioId)` → `List<ReservaDelUsuario>` y `una(usuarioId, reservaId)` → `Optional<ReservaDelUsuario>` (reserva, espacio y su nombre, fecha, `horaInicio`, `horaFin`). El dueño va en la firma: usa `ReservaDAO.delUsuario` y `CatalogoEspaciosService.nombres`, así que una reserva ajena o inexistente da vacío. `readOnly`, sin sesión ni rol. Lo implementa `ReservasDelUsuarioImpl`; `pagos` no lee la tabla `reserva` |
| `pagos.ReclamosAbiertos`              | administracion (HU-26)     | RN-12: `conReclamoAbierto(Collection<Long> reservaIds) → Set<Long>`, de esos ids los que tienen un reclamo `ABIERTO`; colección vacía → `Set.of()` sin consultar; se une a la transacción y no bloquea. `GeneradorLiquidaciones` lo consulta una vez por generación, después del bloqueo consultivo, como un filtro más de `liquidablesPorArrendador`. Lo implementa `ReclamosAbiertosImpl`. Ver [Reclamos](#reclamos-qué-deja-hu-26) |

Solo los servicios que usan otros módulos llevan interfaz pública
(`CuentaService`, `ReservaService`, `ReservasParaReclamo` — HU-26 —, `ReservasParaResena` — HU-24, ver
[Reseñas](#reseñas-qué-deja-hu-24-hu-26-hu-27) —, `PagoService` — creada por HU-15 con `reembolsarCancelacion`, ver
[Pagos](#pagos-qué-deja-hu-13-hu-14-hu-15-hu-16-hu-19-hu-28) —, y, para la moderación de HU-27,
`espacios.service.ModeracionEspacios`, `busqueda.service.ModeracionResenas` y `reservas.service.CancelacionPorModeracion`,
ver [Moderación](#moderación-qué-deja-hu-27)); los internos son clases
concretas (YAGNI). `CuentaService` la creó HU-07 con
`habilitacionArrendador` (correo e identidad verificados del arrendador de la
sesión), que `espacios` usa en lugar de los repositorios de `cuentas`. HU-06 le agregó, para `administracion`,
`arrendadoresEnRevision`, `expedienteIdentidad` (datos y documentos con URL firmada), `aprobarIdentidad` y
`rechazarIdentidad` (bloquean el perfil, cambian el estado y avisan por correo después del commit); cada uno exige el
rol `ADMIN` aunque `administracion` ya lo haya comprobado. No es un puerto: la dirección `administracion → cuentas` está
permitida. Si una historia necesita un puerto nuevo, lo justifica en su `design.md` y lo agrega a esta tabla.

HU-09 le agregó, para `administracion`, `buscarCuentas`, `detalleCuenta`, `suspenderCuenta` (bloquea la fila de
`usuario`, la deja `SUSPENDIDA`, revoca todas sus sesiones y avisa por correo con el motivo después del commit) y
`reactivarCuenta`; los cuatro exigen el rol `ADMIN`. Y, **sin exigir sesión ni rol** porque los usan rutas públicas:

- `cuentaActiva(usuarioId)` → `true` solo si la cuenta existe y está `ACTIVA`.
- `arrendadoresNoActivos()` → `Set<Long>` con los ids de los arrendadores cuyo estado no es `ACTIVA` (o vacío).

HU-12 le agregó `bloquearCuenta(usuarioId)` (D4, `@Transactional(propagation = MANDATORY)`): bloquea la fila de
`usuario` hasta el fin de la transacción del llamador y responde 404 si no existe. Sin sesión ni rol. HU-13 le agregó
`correoDe(usuarioId)`: correo de la cuenta o 404, sin sesión ni rol, porque lo usa el webhook. Nunca se devuelve a un
cliente. HU-14 le agregó `nombres(usuarioIds) → Map<Long, String>`: solo el nombre de las cuentas cuyo `nombre` no es nulo
ni está en blanco (las demás no aparecen), sin sesión ni rol y nunca correo ni teléfono (RNF-SEG-16). Una colección vacía
devuelve `Map.of()` sin consultar. `reservas` lo usa para mostrarle al arrendador quién reservó; **no** se usa `correoDe`
para eso. HU-18 le agregó, para `pagos` (la dirección `pagos → cuentas` ya estaba permitida; no es un puerto nuevo):
`confirmarContrasena(usuario, contrasena) → boolean` (bcrypt contra el hash de la cuenta de la **sesión**; no cuenta
intentos, lo hace el llamador con `LimitadorIntentos`; HU-20 y HU-22 pueden reutilizarlo) y `razonSocialDe(arrendadorId) →
String` (solo la razón social del perfil, 404 sin perfil; nunca el documento, RNF-SEG-16). `NotificadorCorreo` ganó
`notificarCambioCuentaBancaria(correo, AvisoCambioCuentaBancaria)` (HU-20 le agregó dos métodos más, ver
[Perfil](#perfil-qué-deja-hu-20-hu-22-hu-25-hu-06-hu-18)). HU-19 le agregó, para `pagos`, `arrendadoresSinIdentidadVerificada(Collection<Long>) → Set<Long>`: de esos ids, los que no tienen perfil o cuyo `estado_identidad` no es `VERIFICADA`; una consulta por lote (`PerfilArrendadorRepository.idsConIdentidadVerificada`), colección vacía → `Set.of()` sin consultar, sin sesión ni rol y nunca datos del perfil. HU-25 le agregó, para
`reservas`, `correoParaRecordatorio(usuarioId) → Optional<String>`: el correo solo si la cuenta existe, está `ACTIVA` y
tiene `recordatorios_activos = true`; si no, vacío (nunca 404). Sin sesión ni rol y nunca va a un cliente. Es el
**único** lector de la preferencia: `correoDe`, `AvisosPago`, `AvisosCancelacion` y `NotificadorCorreo` no la consultan
(RN-14).

### Cuenta bancaria: qué deja HU-18 (HU-19, HU-22)

Tabla `cuenta_bancaria` (`V5_2`): **una sola fila por arrendador** (`uq_cuenta_bancaria_arrendador`), con el número cifrado
(`v1:…`). `pagos.service.CuentaBancariaService` es una clase concreta (sin interfaz): HU-19 es del mismo módulo y la llama
directo. **HU-19** lee la cuenta con `CuentaBancariaRepository.findByArrendadorId` y decide con
`CuentaBancaria.estaRetenida(ahora)` (`retener_hasta` > ahora, RN-11: tras un **cambio**, no tras el primer registro); sin fila,
la liquidación queda `PENDIENTE_CUENTA`. HU-18 **no** expone el número completo en ninguna ruta; cómo lo ve el admin para
transferir a mano lo decide HU-19. **HU-22** **borra** la fila al eliminar la cuenta (no hay cascada desde
`usuario`; la liquidación `PAGADA` ya guardó banco y cuenta enmascarada). Rutas: `GET` y `PUT
/api/arrendadores/actual/cuenta-bancaria` (siempre `no-store`). El `PUT` exige rol `ARRENDADOR`, identidad verificada y la
contraseña (5 fallos en 15 min por cuenta → 429); contraseña incorrecta es 400 y no 401 porque `api/cliente.js` borra el
token ante un 401 con token.

### Perfil: qué deja HU-20 (HU-22, HU-25, HU-06, HU-18)

Todo vive en `cuentas`, con servicios concretos y **sin puertos nuevos** ni cambios en `CuentaService` (D20):
`PerfilService` (consulta, nombre y teléfono, foto), `CambioCorreoService` (solicitud y confirmación del correo nuevo),
`FacturacionService` (facturación del arrendador) y `ReautenticacionPerfil` (contraseña con `CuentaService.confirmarContrasena`
y `LimitadorIntentos`: **5 fallos en 15 min por cuenta** con la clave `perfil|<id>`, compartida por el correo y la
facturación; el fallo se audita `PERFIL_CONTRASENA_FALLIDA` fuera de transacción). Rutas (todas `no-store`, la cuenta sale
de la sesión): `GET`/`PUT /api/usuarios/actual/perfil` (nombre y teléfono, sin contraseña), `GET`/`PUT`/`DELETE
/api/usuarios/actual/foto` (JPG/PNG hasta 5 MiB, re-codificada y **cifrada** en `ArchivosPrivados`, carpeta `perfiles`; solo
la ve su dueño, `nosniff`), `POST /api/usuarios/actual/cambios-correo` (contraseña; 202 genérico; **3 por cuenta por hora**),
`POST /api/cambios-correo/confirmaciones` (**pública**, el token de 256 bits va en el cuerpo) y `PUT
/api/arrendadores/actual/facturacion` (solo `ARRENDADOR`, contraseña). `ADMIN` recibe 403 en todo el perfil (D7).
`NotificadorCorreo` ganó `notificarCambioCorreo(correoNuevo, enlace)` y `notificarAvisoCambioCorreo(correoActual,
correoNuevoEnmascarado)`; el aviso al correo actual sale al **solicitar** (D2). El correo de la cuenta no cambia hasta
confirmar (`usuario.correo_pendiente`, token `CAMBIO_CORREO` de 30 min); confirmar no revoca sesiones (D4). Un correo
nuevo que ya tiene cuenta recibe la misma respuesta sin token ni enlace (RNF-SEG-13).

**Volver a revisión (RN-02, D9–D12).** Una identidad `VERIFICADA` que cambia el documento (tipo o número) **o la razón
social** pasa a `EN_REVISION` en la misma transacción; la dirección sola no. `EN_REVISION` rechaza cambiar documento o
razón social; `NO_INICIADA`/`RECHAZADA` cambian sin cambiar de estado. Toda escritura bloquea `usuario → perfil_arrendador`
(la decisión del admin bloquea solo `perfil_arrendador`: sin ciclo). Efectos: **HU-06** ve al arrendador en la cola con sus
documentos vigentes y la fecha del envío original (D11); los espacios ya publicados **siguen visibles** y sus reservas
siguen (D12), pero `EspacioService` no publica espacios nuevos, HU-18 no registra ni cambia la cuenta bancaria y HU-19 avisa
al admin en las liquidaciones hasta que vuelva a `VERIFICADA`. `cuenta_bancaria.titular` **no** se actualiza (D14): HU-20 no
escribe en `pagos`; al volver a guardar la cuenta ya verificada se toma la razón social nueva. Un documento de otro
arrendador da un 400 genérico y cuenta como fallo en `perfil|<id>` (D13).

**Para las que siguen.** **HU-22** exporta `correo_pendiente` y si hay foto; al anonimizar borra `correo_pendiente`,
`foto_ruta`/`foto_tipo_mime` **y el archivo** de `ArchivosPrivados`, anula los tokens `CAMBIO_CORREO` y puede reutilizar
`ReautenticacionPerfil` (o su patrón) para su contraseña. **HU-25, hecho:** `NotificadorCorreoImpl` implementa los
dos métodos nuevos de `NotificadorCorreo` (y los otros once) en el adaptador real, ver
[Correo](#correo-qué-deja-hu-25--factory-method-adapter); el tipo de token `VERIFICACION` sigue libre para el correo de
registro.

**Regla para HU-10, HU-11 y HU-12 (HU-09 D1):** los espacios de un arrendador no `ACTIVA` conservan su estado pero
no se muestran ni se reservan. No se lee `usuario` desde `busqueda` ni `reservas`: se filtra con esos dos métodos. La
búsqueda paginada agrega `e.arrendadorId not in :ids` con `arrendadoresNoActivos()` y, **si el conjunto está vacío,
omite esa condición** (una colección vacía en `not in` no es portable en JPQL); la ficha y la reserva preguntan
`cuentaActiva(arrendadorId)` o heredan el filtro si usan `DisponibilidadService.horasLibres`. `espacios` ya lo aplica en
`FotoEspacioService.ver` (la foto de un `PUBLICADO` de un arrendador no activo responde 404 al público; una copia en
caché puede verse hasta 1 hora, `max-age=3600`) y en `horasLibres`.

`espacios.service.DisponibilidadService` es la interfaz pública de `espacios` (la creó HU-08):
`horasLibres(espacioId, fecha)` devuelve las horas de inicio de los bloques libres (horario − bloqueos − reservas −
horas ya empezadas) de un espacio `PUBLICADO` cuyo arrendador está `ACTIVA` (HU-09) en una fecha de las próximas 8
semanas, o una lista vacía. La reutilizan
`busqueda` (HU-10, HU-11) y `reservas` (HU-12); no hace falta otro puerto porque esas direcciones están permitidas.
HU-10 le agregó `conBloquesLibres(espacioIds, fecha) → Set<Long>`: los ids con `horasLibres` no vacía, consultando
horario, bloqueos y el puerto una vez por lote. HU-12 le agregó `retenerSiLibres(espacioId, fecha, horas) →
EspacioRetenido(nombre, precioHora)` (ver [Reservas](#reservas-qué-deja-hu-12-hu-13-hu-14-hu-15)). HU-13 le agregó
`bloquearEspacio(espacioId) → EspacioBloqueado(arrendadorId, nombre)` (`MANDATORY`): toma el bloqueo sin filtrar por estado
ni arrendador, porque un pago aprobado confirma su reserva aunque el espacio ya no se vea (D6).

`espacios.service.CatalogoEspaciosService` es la segunda interfaz pública de `espacios` (la creó HU-10), sin sesión ni
rol, para `busqueda` (HU-10, HU-11, HU-23): `esTipoValido`, `existeZona`, `publicados(tipo, zona)` → `EspacioPublicado`
de arrendadores `ACTIVA` ordenados por `precio_hora` e `id` (aplica la regla HU-09 D1), y `fotosPrincipales(ids)` →
URL `/api/fotos/<UUID>` de la foto con orden 1. HU-11 le agregó `publicado(espacioId)` → `Optional<FichaPublicada>`
(datos de la ficha y URL de todas sus fotos por orden ascendente, sin `arrendadorId`, estado ni fechas) y
`esVisible(espacioId)`; ambos aplican la misma regla (`PUBLICADO` y `cuentaActiva(arrendadorId)`) y devuelven vacío o
`false` para `null`, inexistente o no visible. `busqueda` nunca lee repositorios de `espacios`. HU-12 le agregó
`nombres(espacioIds)` → `Map<Long, String>`, **sin** filtro de visibilidad, para que `reservas` (HU-12, HU-14) muestre
una reserva cuyo espacio ya no se ve. HU-14 le agregó `nombresDelArrendador(arrendadorId)` → `Map<Long, String>`: id y
nombre de **todos** los espacios del arrendador, en cualquier estado (`BORRADOR`, `PUBLICADO`, `PAUSADO`, `BAJA`, `OCULTO`)
y sin filtro de visibilidad; vacío para `null` o sin espacios. Sin sesión ni rol: el llamador pasa el id de la sesión. Así
`reservas` sabe qué espacios son del arrendador sin leer `EspacioRepository`. HU-19 le agregó `arrendadoresDe(espacioIds)` → `Map<Long, Long>` (id del espacio → id de su arrendador), sin filtro de estado ni de visibilidad, como `nombres`; colección vacía → `Map.of()` sin consultar. Lo usa `ReservasLiquidablesImpl`. HU-23 le agregó `esCaracteristicaValida(codigo)` (`true` solo con `TECHADO`, `ILUMINACION`, `VESTUARIOS` o `ESTACIONAMIENTO` exactos) y amplió `EspacioPublicado` con `capacidad` y `caracteristicas` (`Set<String>` de códigos); `publicados` las carga en la misma consulta (`@EntityGraph(attributePaths = "caracteristicas")` en `EspacioRepository.buscarPublicados*`, sin N+1).

### Filtros: qué deja HU-23 (★ Decorator)

- **Parámetros** de la misma `GET /api/espacios` (mismo cupo `catalogo`, sin regla nueva): `precioMaximo`
  (`^[0-9]{1,6}(\.[0-9]{1,2})?$`, 1.00–100000.00, inclusivo), `capacidadMinima` (`^[0-9]{1,6}$`, 1–100000,
  inclusivo) y `caracteristica` **repetido** una vez por valor (lista fija, sin repetir; el controlador lo lee con
  `getParameterValues`, así `TECHADO,VESTUARIOS` no se parte y es 400). Presente y vacío = 400. Los nombres
  desconocidos se siguen ignorando. La respuesta no cambia (seis campos de HU-10).
- **Orden de errores** (un `ErrorCampoDTO` por campo, `ValidacionException`): `tipo`, `zona`, `fecha`, `pagina`,
  `precioMaximo`, `capacidadMinima`, `caracteristica`. Con algún error no se arma la cadena ni se calcula la
  disponibilidad.
- **Decorator por petición** en `busqueda/service`: `BusquedaEspacios` (Component), `BusquedaBase`
  (ConcreteComponent, sin cambios), `FiltroBusqueda` (Decorator abstracto: reenvía y conserva en orden lo que
  `admite`), `FiltroPrecioMaximo`, `FiltroCapacidadMinima`, `FiltroCaracteristicas` (ConcreteDecorators).
  `FiltrosBusqueda.envolver(base)` apila solo los presentes (precio adentro, características afuera); sin filtros
  devuelve la base misma. Los decoradores no son beans: `BusquedaService` sigue recibiendo un solo `BusquedaEspacios`.
- **Agregar un filtro**: una clase `extends FiltroBusqueda` con su `admite`, un componente en `FiltrosBusqueda` y su
  validación en `BusquedaService` (después de `caracteristica`). Ni `BusquedaBase` ni los otros filtros cambian. Si el
  dato no está en `EspacioPublicado`, se agrega allí (lo llena `CatalogoEspaciosServiceImpl.publicados`).
- **Características del espacio** (CA1): `enum espacios.domain.Caracteristica` + `@ElementCollection` en `Espacio`
  (tabla `espacio_caracteristica`); `FichaEspacioDTO.caracteristicas` con `@CaracteristicasDeLista` (error en el campo
  `caracteristicas`); ausente o `null` = ninguna (el `PUT` reemplaza la ficha). `EspacioDTO.caracteristicas` en el
  orden fijo; la edición audita `caracteristicas=[…]->[…]` en `EDICION_ESPACIO`.

**Rate limiting por patrón y cupo del catálogo (HU-10, §9.9).** `comun/limite/ReglaLimite` compara la ruta con
`PathPattern` de Spring (segmentos decodificados, como el enrutado de Spring MVC): una ruta literal coincide solo
consigo misma y un patrón (`/api/espacios/{id}`, `/api/fotos/*`) con las rutas que encajan; un patrón inválido falla
al crear el bean. `FiltroLimiteIntentos` descuenta un solo intento por grupo y solicitud. El cupo del catálogo
(grupo `catalogo`, 120 por IP por minuto, configurable con `espacioya.limite.catalogo.maximo` y `.ventana`) lo fija
`busqueda/controller/LimiteCatalogo`; HU-11 y HU-23 registran sus rutas con un `@Bean`
`limite.regla("GET", "/api/espacios/{id}")` (ver `ReglasLimiteBusqueda`) y comparten así el cupo. HU-11 registró
en `busqueda/controller/ReglasLimiteFicha` dos reglas del grupo: `GET /api/espacios/{id}` (ficha) y
`GET /api/espacios/{id}/disponibilidad` (un patrón `/{id}` no cubre la subruta); cuentan también las respuestas 404 y
400. HU-24 sumó allí `GET /api/espacios/{id}/resenas`, con el mismo cupo. `GET /api/zonas` y las fotos no cuentan.

**Zona horaria.** El bean `Clock` sigue en UTC (`creado_en`, auditoría). Todo lo que depende del calendario local
("hoy", la hora actual, la ventana de 8 semanas) se calcula en `America/Lima` con
`comun/configuracion/ZonaHoraria` (`hoy(reloj)`, `ahora(reloj)`), creada por HU-08.

### Disponibilidad: qué deja HU-08 y qué falta (HU-10, HU-11, HU-12, HU-21)

Lo que ya existe en `espacios` y se reutiliza tal cual:

| Pieza | Qué hace |
|-------|----------|
| `service.DisponibilidadService.horasLibres(espacioId, fecha)` | Única entrada pública. Horas de inicio (0–23) `LIBRE`, ascendentes; lista vacía si el espacio no existe, no está `PUBLICADO`, su arrendador no está `ACTIVA` (HU-09, vía `CuentaService.cuentaActiva`) o la fecha está fuera de la ventana. Transacción de solo lectura; no bloquea nada |
| `domain.VentanaReserva` | `VentanaReserva.desde(ZonaHoraria.hoy(reloj))` → `desde` = hoy, `hasta` = hoy + 55 (`DIAS = 56`); `contiene(fecha)`. Usarla para validar la fecha de una búsqueda, de la ficha o de una reserva, en vez de repetir la regla |
| `domain.CalculoDisponibilidad.bloquesDelDia(...)` | Cálculo puro (horario − bloqueo − horas reservadas − horas ya empezadas) → `Bloque(horaInicio, estado)` con `EstadoBloque` `LIBRE`, `BLOQUEADO`, `RESERVADO` o `PASADO` |
| Puerto `service.puertos.ReservasDelEspacio` | Ver la tabla de puertos. Las horas que devuelva quedan `RESERVADO` y salen de `horasLibres` |
| Rutas `/api/arrendadores/actual/espacios/{id}/horario`, `/bloqueos`, `/disponibilidad` | Solo para el **dueño** (rol `ARRENDADOR`). No sirven para la ficha ni la búsqueda públicas |

Lo que le toca a cada historia decidirlo en su `design.md` (agregando el método a `DisponibilidadService`, no
leyendo repositorios de `espacios` desde otro módulo):

- **HU-10 (búsqueda por fecha), hecho:** `DisponibilidadService.conBloquesLibres(Collection<Long> espacioIds,
  LocalDate fecha) → Set<Long>` reutiliza `CalculoDisponibilidad` y consulta horario, bloqueos y el puerto
  (`bloquesReservadosDelDia`) una sola vez por lote. La búsqueda pagina en memoria sobre la lista ya filtrada.
- **HU-11 (ficha pública), hecho:** ruta pública `GET /api/espacios/{id}/disponibilidad?fecha=` en `busqueda`
  (`FichaService`), con el cupo del catálogo. Solo devuelve los bloques **libres** con `horasLibres`, sin cambiar
  `DisponibilidadService`. Antes de calcular responde 404 genérico si el espacio no es visible
  (`CatalogoEspaciosService.esVisible`) y 400 en `fecha` si la fecha no es válida o está fuera de `VentanaReserva`
  (mismos textos que HU-08 y HU-10). Si una historia futura quiere mostrar los ocupados, agrega un método que devuelva
  los `Bloque` con su estado, sin `PASADO`/`BLOQUEADO`/`RESERVADO` distinguidos hacia afuera (todos "no disponible",
  RNF-SEG-16). Cómo alimentar `SelectorBloques`: ver `docs/frontend.md` § Bloques horarios.
- **HU-12 (reservar), hecho:** ver [Reservas](#reservas-qué-deja-hu-12-hu-13-hu-14-hu-15).
- **HU-12, pruebas:** `EspaciosControllerSoporte`, `BusquedaControllerIT` y `FichaControllerIT` registran su doble de
  `ReservasDelEspacio` con `@Primary`, así que siguen usando el doble aunque exista el bean real; cualquier doble nuevo
  del puerto debe llevarlo o el contexto no arranca (`NoUniqueBeanDefinitionException`). `DisponibilidadServiceImplIT`
  usa la implementación real con las tablas de reservas vacías; R21 de HU-08 (sin bean) se prueba en la unitaria.
- **HU-12, consulta en lote (HU-10 DT-6, D7), hecho:** `ReservasDelEspacioImpl` sobrescribe el método **default**
  `bloquesReservadosDelDia` con una sola consulta (`espacio_id IN (…)` y `fecha = …`) con el mismo criterio de reservas
  activas. Los ids sin reservas faltan en el mapa.
- **HU-21 (baja con reservas), hecho:** usa el mismo puerto `ReservasDelEspacio` con la ventana completa
  (`desde`, `hasta` de `VentanaReserva`) y filtra con `BloqueReservado.terminaDespuesDe(ahoraLocal)`. Ver
  [Estado del espacio](#estado-del-espacio-qué-deja-hu-21-hu-27).

### Estado del espacio: qué deja HU-21 (HU-27)

Pausa, reanudación y baja viven en `espacios.service.EstadoEspacioService` (clase concreta, sin interfaz), que reutiliza
`EspacioService.habilitacion` y `aDto`. Las reglas de estado están en la entidad: `Espacio.esPausable()` (`PUBLICADO`),
`esReanudable()` (`PAUSADO`), `admiteBaja()` (`PUBLICADO` o `PAUSADO`) y `pausar`, `reanudar`, `darDeBaja(Instant)`, que
lanzan `IllegalStateException` si el predicado no se cumple y fijan `actualizado_en`. `BAJA` es final. Sin migración: la
fecha queda en `actualizado_en` y en la auditoría.

| Ruta | Rol | Respuesta |
|------|-----|-----------|
| `POST /api/arrendadores/actual/espacios/{id}/pausa` · `/reanudacion` · `/baja` (`EstadoEspacioController`) | `ARRENDADOR` con correo verificado; espacio propio (ajeno = 404) | `EspacioDTO` |
| `POST /api/admin/espacios/{id}/pausa` · `/baja` (`EspacioAdminController`, dentro de `espacios`) | `ADMIN` (IP permitida + MFA + rol en el servicio, antes de leer) | `EstadoEspacioDTO(id, estado)` |

- **Orden.** Arrendador: rol y correo (403) → `findParaActualizar(id, arrendadorId)` (bloqueo de fila, 404) → estado (400)
  → *reanudar:* identidad `VERIFICADA` → al menos una foto (mensajes de HU-07) / *baja:* reservas por terminar → cambio →
  auditoría. Admin: rol → `findParaReservar(id)` (mismo bloqueo, sin filtro de dueño) → estado → reservas. El admin no
  reanuda ni cancela reservas; pausar y dar de baja no exigen identidad verificada.
- **Reserva por terminar.** Algún bloque de `ReservasDelEspacio.bloquesReservados(id, ventana)` (reservas `CONFIRMADA` o
  `PENDIENTE_PAGO` vigente) con `terminaDespuesDe(ZonaHoraria.ahora(reloj))`: incluye las futuras, las en curso y las
  pendientes de pago vigentes. Sin bean del puerto, no hay reservas. Si hay, 400 con un error en el campo `reservas`
  (`MENSAJE_RESERVAS_POR_TERMINAR`, o `…_ADMIN` para el admin); el arrendador las ve con el filtro `espacioId` de
  [Mis reservas](#mis-reservas-qué-deja-hu-14-hu-15-hu-21-hu-22-hu-24-hu-26) y las cancela una a una con HU-15 (100 %).
  Las en curso y las pendientes no se cancelan: hay que esperar a que terminen o venzan.
- **Carrera (R17).** Las reservas se leen después de tomar el bloqueo de la fila del espacio y en la misma transacción;
  crear una reserva (`retenerSiLibres`), el webhook y la cancelación toman el mismo bloqueo. O la reserva entra primero y la
  baja la ve, o la baja entra primero y la reserva relee `BAJA` y responde 404.
- **Auditoría.** `PAUSA_ESPACIO`, `REANUDACION_ESPACIO`, `BAJA_ESPACIO`, entidad `espacio`, detalle
  `estado=<anterior>-><nuevo>` y, si actúa el admin, `;actor=ADMIN;arrendadorId=<dueño>`. Los rechazos no se auditan.
- **Visibilidad.** No cambió nada en `busqueda`: toda la visibilidad pública exige `estado == PUBLICADO` (HU-09 D1), así que
  un `PAUSADO` o `BAJA` no aparece en la búsqueda, su ficha da 404 y no recibe reservas. Un `PAUSADO` sigue editable; un
  `BAJA` no (`esEditable`), sigue en "Mis espacios" y sus reservas pasadas se siguen liquidando (HU-19 no filtra estado).
- **HU-27** agregó `Espacio.esOcultable()` (`PUBLICADO` o `PAUSADO`) y `ocultar(Instant)`, y
  `EstadoEspacioService.ocultarComoAdmin` (`MANDATORY`: rol → `findParaReservar` → estado, 400
  `MENSAJE_NO_OCULTABLE` → `OCULTO` → auditoría `OCULTACION_ESPACIO` con `;actor=ADMIN;arrendadorId=…`), que solo llama
  `ModeracionEspacios.ocultar` desde la decisión del admin. `OCULTO` es **final**: no admite edición, pausa, reanudación
  ni baja (400) y no se restaura. Ocultar no mira reservas; por fraude las cancela `reservas` (ver
  [Moderación](#moderación-qué-deja-hu-27)).

### Reservas: qué deja HU-12 (HU-13, HU-14, HU-15)

| Pieza | Qué hace |
|-------|----------|
| `reservas.service.ReservaService` | Interfaz pública: `crear(usuario, datos, ip)` y `consultar(usuario, id)`. Solo el rol `USUARIO` reserva (D1); una reserva ajena es 404 |
| `Reserva.esVigente(ahora)`, `estaVencida(ahora)`, `estadoEn(ahora)` | Vigente si `PENDIENTE_PAGO` y `ahora < expira_en`. Quien lee usa siempre estos métodos y no `getEstado()`: una vencida sigue `PENDIENTE_PAGO` en la fila hasta que alguien la expira |
| `espacios.DisponibilidadService.retenerSiLibres` | `@Transactional(propagation = MANDATORY)`. Toma el bloqueo de `espacio` sin filtrar por dueño (`EspacioRepository.findParaReservar`), aplica HU-09 D1 con `cuentaActiva` (404 si no es visible), comprueba las horas bajo ese bloqueo (400 *horario no disponible*) y devuelve `EspacioRetenido` con nombre y precio leídos ya bloqueados. Debe ser la primera lectura del espacio en la transacción |
| `ReservasDelEspacioImpl` | Implementación real del puerto: `PENDIENTE_PAGO` vigente o `CONFIRMADA` |
| `ExpiracionReservas` | `expirarVencidas()` (una transacción por reserva) y `liberarVencidas(...)` (`MANDATORY`, dentro de la reserva que choca) |
| `ProgramacionExpiracion` | Proceso programado con `@Scheduled(fixedDelay…)`. Propiedades `espacioya.reservas.expiracion.activa` (`RESERVAS_EXPIRACION_ACTIVA`, por defecto `true`) e `.intervalo` (`RESERVAS_EXPIRACION_INTERVALO`, por defecto `PT1M`). Apagado en `src/test/resources/config/application.properties`: las pruebas llaman a `expirarVencidas()` |
| `service.recordatorio.RecordatoriosReserva` (HU-25) | `enviarPendientes()`: candidatas `ReservaRepository.findIdsPorRecordar(hoyLima, ReglaRecordatorio.ultimaFecha(ahora))` (`CONFIRMADA`, sin `recordatorio_enviado_en`, con un bloque activo en esas fechas) → una transacción por reserva: `findParaActualizar`, `admiteRecordatorio`, `ReglaRecordatorio.corresponde(instanteDeInicio, ahora)` (`inicio − 24 h ≤ ahora < inicio`), `marcarRecordatorio(ahora)`, `correoParaRecordatorio` de cada parte, auditoría `RECORDATORIO_RESERVA` (`origen=PROCESO;destinatarios=…`: `USUARIO,ARRENDADOR`, `USUARIO`, `ARRENDADOR` o `NINGUNO`) y un `DespuesDelCommit` por correo. Fuera de plazo no marca. "Como mucho una vez": un envío fallido no se reintenta. Solo toma el bloqueo de `reserva` |
| `service.recordatorio.ProgramacionRecordatorios` (HU-25) | `@Scheduled(fixedDelay…)` con **su propio** `@EnableScheduling` (el de `ProgramacionExpiracion` se apaga con su propiedad). Propiedades `espacioya.reservas.recordatorios.activa` (`RESERVAS_RECORDATORIOS_ACTIVA`, por defecto `true`) e `.intervalo` (`RESERVAS_RECORDATORIOS_INTERVALO`, por defecto `PT5M`). Apagado en las pruebas: llaman a `enviarPendientes()` |

**Orden fijo de bloqueos: `usuario → espacio → reserva`.** `crear` toma `CuentaService.bloquearCuenta` (serializa el
cupo de 3 pendientes de un usuario que reserva dos espacios a la vez) y luego `retenerSiLibres` (serializa a dos
usuarios, y a la reserva con el bloqueo de fechas y la edición del precio, HU-08 DT-10). El proceso programado solo
toma una fila de `reserva` por transacción y nunca `espacio`. Quien agregue un bloqueo en este camino respeta ese orden.
El índice `uq_reserva_bloque_activo` es la última defensa: `crear` traduce su violación a *horario no disponible*,
reconociéndola por el nombre de la restricción; cualquier otra violación se relanza.

**Expiración en tres capas (D2).** (1) Las lecturas comparan con *ahora*: una vencida no ocupa ni cuenta en el cupo, y
`GET /api/reservas/{id}` la responde `EXPIRADA` sin escribir. (2) El proceso programado la pasa a `EXPIRADA`, pone
`activo = false` y audita `RESERVA_EXPIRADA` con `usuario_id` e `ip` nulos y `origen=PROCESO`. (3) Al crear una reserva,
bajo el bloqueo del espacio, `liberarVencidas` expira las que retienen esas horas: el índice parcial no entiende el
tiempo, y sin esto una vencida daría un falso conflicto. Por lo mismo `desactivarDeReserva` es un `UPDATE` con
`flushAutomatically`, que llega a la base antes del `INSERT` de los bloques nuevos.

**HU-13, hecho:** `ReservasPorCobrarImpl` implementa el puerto. `retenerSiLibres` no sirve para confirmar, porque las
horas ya las retiene la propia reserva: `bloquear` lee `espacioId` con una proyección (`findEspacioIdById`, para no dejar
en el contexto una copia vieja de la reserva), toma `bloquearEspacio`, luego `findParaActualizar`, y evalúa `esVigente`
con un *ahora* tomado después de los dos bloqueos, porque el proceso y el webhook compiten por esa fila. El webhook no
toma el bloqueo de `usuario`: solo protege el cupo y confirmar lo reduce; un subconjunto del orden no crea ciclos. Antes
del checkout se comprueba `CatalogoEspaciosService.esVisible` (espacio publicado y arrendador activo, HU-12 S5, HU-13 D6).
`EspacioRetenido` no cambió: `bloquearEspacio` devuelve `EspacioBloqueado`.

### Pagos: qué deja HU-13 (HU-14, HU-15, HU-16, HU-19, HU-28)

| Pieza | Qué hace |
|-------|----------|
| `pagos.service.pasarela.PasarelaPago` | Adapter. `crearCheckout`, `cerrarCheckout`, `idNotificado` (verifica la firma; vacío si no es de un pago) y `consultarPago`, sin tipos del proveedor. Lo implementan `MercadoPagoAdapter` (`RestClient` + HMAC, sin el SDK, DT-1) y `PasarelaSimulada`, cada una con `@ConditionalOnProperty`: no hay fábrica. HU-16 le agregó `reembolsar(SolicitudReembolsoPasarela(idTransaccion, monto, claveIdempotencia, numeroIntento)) → ReembolsoRealizado(idReembolso)` (HU-31 agregó `numeroIntento` = `reembolso.intentos` ya confirmado, ≥ 1): solo devuelve si la pasarela aprobó; cualquier falla, rechazo o respuesta incompleta → `ServicioExternoException`. En Mercado Pago es `POST /v1/payments/{id}/refunds` con `X-Idempotency-Key`; Mercado Pago ignora el número de intento |
| `pagos.service.CheckoutFacade` | Facade: la única entrada de `PagoController`. Coordina `EvaluacionCobro` y `SesionesCheckout`, sin reglas y sin confirmar nada. El webhook **no** pasa por ella |
| `pagos.service.SimulacionPago`, `pagos.controller.PagoSimuladoController` | Solo existen con la simulada (`@ConditionalOnProperty`): con `mercadopago` la ruta responde 404. La pantalla hace de página de la pasarela: `SimulacionPago` comprueba rol, reserva, rechazos y checkout, audita `PAGO_SIMULADO` y entrega la notificación a `WebhookPagoService.recibir`. No toca `pago` ni `ReservasPorCobrar`. El resultado es lo único que elige el cliente; el monto sale de la reserva |
| `PasarelaSimulada.notificacionDe` | Fuera de `PasarelaPago`: el Target no cambia y `MercadoPagoAdapter` no gana un método vacío. Arma la notificación con un token interno que no sale del proceso y no prueba nada de la firma real |
| `pagos.service.WebhookPagoService` | Único camino que confirma una reserva (RNF-CUM-02), también con la simulada. Firma → consulta del pago, sin transacción → `TransactionTemplate`: `bloquear`, ¿ya existe?, clasificar, `INSERT pago`, `confirmar`, auditoría. Ninguna llamada externa retiene filas |
| `pagos.domain.ReglasPago` | Reglas puras: `motivoNoDisponible` (orden de R18), `estadoDeAprobado`, `intentosAgotados`, ventana de 30 min |
| `comun.transaccion.DespuesDelCommit` | `ejecutar(Runnable)`: corre la acción solo si la transacción se confirma y atrapa su fallo. Viene de `CuentaServiceImpl` y lo reutilizan los correos de cualquier módulo. Exige una transacción activa |
| `comun.error.ServicioExternoException` | 503 con `ErrorDTO.MENSAJE_SERVICIO_EXTERNO`, sin detalle. También servirá a correo y almacenamiento |

**Propiedades `espacioya.pagos.*`.** `pasarela` (`PAGOS_PASARELA`, `mercadopago` o `simulada`; vacía a propósito: sin un valor
válido no hay bean y el backend no arranca; `@ConditionalOnProperty` no distingue mayúsculas), `llave-secreta`
(`PAGOS_LLAVE_SECRETA`), `secreto-webhook` (`PAGOS_SECRETO_WEBHOOK`) y `url-notificacion` (`PAGOS_URL_NOTIFICACION`), sin las
que `mercadopago` no arranca; `url-api` (`PAGOS_URL_API`) y `margen-cierre` (`PAGOS_MARGEN_CIERRE`, `PT1M`). En UAT, `pasarela` vale
`simulada` y `secreto-webhook` queda **vacío**: `POST /api/pagos/webhook` rechaza todo pago y la pantalla es la única entrada
(S16). Las pruebas usan `simulada`; las IT del webhook, el adaptador real contra `StubMercadoPago`.

**`pagos.service.PagoService` (HU-15).** Interfaz pública de `pagos` para otros módulos; su primer método es
`reembolsarCancelacion(ReembolsoPorCancelacion(reservaId, monto, solicitadoPor, ip))`, `@Transactional(propagation =
MANDATORY)`. Contrato con HU-16 (`specs/HU-15/design.md` § 11.1): `reservas` lo llama una sola vez por reserva, **dentro**
de la transacción de la cancelación, con la reserva ya `CANCELADA`, sus bloques liberados y `monto > 0` (con 0 % no se
llama), y el monto nunca supera el del pago `PAGADO`. En HU-15 `PagoServiceImpl` solo busca ese pago
(`IllegalStateException` si no hay → 500 y rollback de la cancelación), valida `0 < monto ≤ pago.monto`
(`IllegalArgumentException`) y audita `REEMBOLSO_SOLICITADO` sobre el pago (`origen=CANCELACION;reservaId=…;monto=…`): **no**
llama a la pasarela, **no** cambia `pago.estado` y **no** crea `reembolso`. **Desde HU-16** (misma firma y propagación),
además de esas validaciones: si ya hay un reembolso `CANCELACION` del pago → `IllegalStateException`; crea la fila de
`reembolso` `PENDIENTE` con `saveAndFlush` (la violación de `uq_reembolso_pago_origen` revierte al llamador), audita
`REEMBOLSO_SOLICITADO` igual que antes y entrega el Command a `EjecutorReembolsos.ejecutarDespuesDelCommit`: la pasarela se
llama **después del commit**, en el mismo hilo, antes de que la cancelación responda. Un fallo de la pasarela nunca lanza
ahí: deja el reembolso `FALLIDO` y el pago `REEMBOLSO_FALLIDO`, y la cancelación responde igual 200. Ver
[Reembolsos](#reembolsos-qué-deja-hu-16-hu-26-hu-28). **HU-22** le agregó `pagosDeReservas(Collection<Long> reservaIds) →
List<PagoDeReserva>` (`readOnly`, sin sesión ni rol; colección vacía → `List.of()` sin consultar): cada pago de esas reservas
con monto, estado, fecha y sus `ReembolsoDePago` (monto, estado, fecha), en dos consultas por lote y **sin** `ultimos4` ni ids
de la pasarela. Lo usa `reservas` para exportar los pagos del usuario, porque `pagos` no sabe qué reservas son suyas.
**HU-26** le agregó, para `administracion` (todos `MANDATORY`, sin sesión ni rol): `bloquearGeneracionLiquidaciones()`
(`pg_advisory_xact_lock(190019)`, el mismo bloqueo de `GeneradorLiquidaciones`), `reservaLiquidada(reservaId)` (`true` si está
en `liquidacion_detalle`) y `reembolsarReclamo(ReembolsoPorReclamo(reservaId, monto, solicitadoPor, ip))`, gemelo de
`reembolsarCancelacion` con origen `RECLAMO` (las dos comparten el método privado `reembolsar`): pago `PAGADO`, `0 < monto ≤
pago.monto`, un reembolso `RECLAMO` por pago, `Reembolso.porReclamo`, auditoría `REEMBOLSO_SOLICITADO`
(`origen=RECLAMO;…`) y Command `AUTOMATICO` tras el commit. El pago queda `REEMBOLSADO` aunque el reembolso sea parcial.

**Lo que deben saber las historias que siguen.** Una reserva tiene como mucho un
pago `PAGADO`. HU-19 y HU-28 cuentan solo `PAGADO` (HU-28, también los reembolsados): `PAGADO_TARDIO` y `MONTO_DISTINTO`
no cobraron nada. Las IT que crean pagos los borran antes que la reserva: `pago` no tiene cascada. **Filas `sim-`:** con la
simulada, `id_transaccion_pasarela` empieza por `sim-` y ningún dinero se movió: HU-15, HU-16, HU-19 y HU-28 deben filtrarlas o
limpiarlas al conectar Mercado Pago (RS-5). HU-16: la simulada **aprueba** los reembolsos de sus filas `sim-` (id
`sim-reembolso-<clave>`, sin dinero movido) y rechaza cualquier otro id; con `mercadopago`, el reembolso de una fila `sim-`
falla (Mercado Pago no conoce ese pago) y queda `FALLIDO` en el panel del admin.

### Reembolsos: qué deja HU-16 (HU-26, HU-28)

Tabla `reembolso` (`V4_3`): la **fuente de verdad** del reembolso, con `PENDIENTE` → `REEMBOLSADO` / `FALLIDO` (y `FALLIDO` →
`PENDIENTE` al reintentar). `pago.estado` pasa a `REEMBOLSADO` o `REEMBOLSO_FALLIDO` **solo** en la misma transacción que el
reembolso (`Pago.marcarReembolsado` / `marcarReembolsoFallido`). Un reembolso por pago y origen (`uq_reembolso_pago_origen`).

| Pieza | Patrón ★ | Qué hace |
|-------|----------|----------|
| `pagos.service.reembolso.ComandoReembolso` | **Command** | `ResultadoReembolso ejecutar()` (`REEMBOLSADO`, `FALLIDO`, `NO_EJECUTADO`). Es lo único que conoce el Invoker |
| `pagos.service.reembolso.SolicitudReembolso` | Command (ConcreteCommand) | `final`, no es bean. Guarda `reembolsoId`, `Disparo` (`AUTOMATICO`/`ADMIN`), `solicitadoPor` e `ip`. `ejecutar()`: (1) reclama el intento con `RegistroReembolsos.iniciar`; vacío → `NO_EJECUTADO` sin llamar a nadie; (2) `PasarelaPago.reembolsar` **sin** transacción ni bloqueos; (3) `registrarExito` o, ante cualquier `RuntimeException`, `registrarFallo`. Idempotente y reintentable |
| `pagos.service.reembolso.SolicitudesReembolso` | (fábrica) | `crear(reembolsoId, disparo, solicitadoPor, ip)`: inyecta `PasarelaPago`, `RegistroReembolsos` y `Clock` para que los Clients no los conozcan |
| `pagos.service.reembolso.EjecutorReembolsos` | Command (Invoker) | `ejecutarDespuesDelCommit` (automático, vía `DespuesDelCommit`: con rollback no corre) y `ejecutarAhora` (reintento; `IllegalStateException` si hay una transacción activa: la llamada externa no retiene filas) |
| `PagoServiceImpl` (cancelación) y `pagos.service.ReembolsoAdminService` (reintento) | Command (Clients) | Crean el comando con su `Disparo` y se lo pasan al Invoker. Ninguno conoce `PasarelaPago` |
| `pagos.service.pasarela.PasarelaPago` | Command (Receiver) y Adapter (Target) | `reembolsar`, ver [Pagos](#pagos-qué-deja-hu-13-hu-14-hu-15-hu-16-hu-19-hu-28) |
| `pagos.service.reembolso.RegistroReembolsos` | (apoyo) | Las transacciones cortas del comando, cada una **`REQUIRES_NEW`** con su propio `TransactionTemplate` (en `afterCommit` la transacción original sigue ligada al hilo y una `REQUIRED` no escribiría). `iniciar`: `findParaActualizar` (bloqueo), `admiteIntento`, `iniciarIntento` (+1 intento, `ultimo_intento_en`) y commit. `registrarExito`/`registrarFallo`: bloqueo `reembolso` → `pago`, cambian los dos estados y auditan `REEMBOLSO_REALIZADO`/`REEMBOLSO_FALLIDO` (entidad `reembolso`, `pagoId=…;reservaId=…;intento=n;disparo=…;monto=…[;idReembolso=…]`, nunca texto del proveedor) con el `usuario_id` e IP del comando. Es el único que cambia `reembolso.estado` y, tras pagar, `pago.estado` |

**Reglas.** *En proceso*: `PENDIENTE` cuyo `coalesce(ultimo_intento_en, creado_en)` es posterior a *ahora* −
`Reembolso.MARGEN_SIN_RESPUESTA` (2 min); pasado ese margen está *sin respuesta* y se puede reintentar (un proceso que cayó a
mitad no deja el reembolso atascado). `Reembolso.motivoNoReintentable` → `YA_REEMBOLSADO` o `EN_PROCESO` (lo usa el admin);
`admiteIntento` además admite el **primer** intento (`PENDIENTE` sin intentos, el automático) aunque esté en proceso. La
**clave de idempotencia** es estable, `espacioya-reembolso-<id>`, igual en todos los intentos: un reintento tras una respuesta
perdida no reembolsa dos veces. Dos intentos a la vez se serializan en el bloqueo de `iniciar`: el segundo ve el reembolso
en proceso.

**Rutas del admin** (`pagos.controller.ReembolsoAdminController`, bajo `/api/admin/**`, así que `FiltroIpsAdmin` exige IP
permitida; el rol `ADMIN` lo exige el servicio antes de leer): `GET /api/admin/reembolsos` (los `FALLIDO` y `PENDIENTE` sin
respuesta, por `creado_en` e `id`, `ReembolsoAdminDTO` sin datos del usuario ni de la tarjeta) y `POST
/api/admin/reembolsos/{id}/reintento` (sin cuerpo; 200 con el estado resultante, 400 *ya reembolsado* / *en proceso*, 404).
Ambas con `Cache-Control: no-store`.

**Propiedad `espacioya.pagos.simulada.fallos-reembolso`** (`PAGOS_SIMULADA_FALLOS_REEMBOLSO`, 0 por defecto): con *N* > 0, la
simulada falla los intentos 1 a *N* de cada reembolso, por el número de intento guardado en `reembolso.intentos`: el
resultado es el mismo con varias réplicas y tras reiniciar (HU-31). Con `1` se demuestra CA2: el automático (intento 1)
falla y el reintento del admin (intento 2) funciona.

**Lo que deben saber las historias que siguen.** HU-26 crea su reembolso con `origen = RECLAMO` (agrega una fábrica
`Reembolso.porReclamo`) y lo ejecuta con `SolicitudesReembolso.crear(...)` + `EjecutorReembolsos`, sin tocar el comando;
como el pago puede estar ya `REEMBOLSADO`, decide qué estado le deja. HU-28 cuenta `PAGADO` y `REEMBOLSADO`; el monto devuelto
está en `reembolso.monto`. Las IT que borran `pago` borran antes `reembolso` (sin cascada).

### Liquidación: qué deja HU-19 (HU-22, HU-26, HU-28)

Tablas `liquidacion` y `liquidacion_detalle` (`V5_3`). El admin genera con un botón (`POST /api/admin/liquidaciones`, sin
cuerpo) y transfiere a mano; no hay proceso programado ni llamada a ningún banco.

| Pieza | Qué hace |
|-------|----------|
| `pagos.service.GeneradorLiquidaciones` | `MANDATORY`. Toma `LiquidacionRepository.bloquearGeneracion` (`pg_advisory_xact_lock(190019)`, se libera al terminar la transacción) y **después** el corte `Instant.now(reloj)`: dos generaciones a la vez se serializan y la segunda no ve lo que la primera liquidó. `uq_liquidacion_detalle_reserva` es la última defensa (`LiquidacionAdminService` la traduce a 400). Agrupa por arrendador y crea `GENERADA` (con cuenta, aunque esté retenida) o `PENDIENTE_CUENTA`; nunca modifica una liquidación existente |
| `PagoRepository.pagadosSinLiquidar(incluirSimulados)` | **Qué pago cuenta:** `PAGADO`, sin **ninguna** fila en `reembolso` (cualquier origen y estado), cuya reserva no está en `liquidacion_detalle`; los `sim-` solo si `espacioya.pagos.pasarela=simulada` (y entonces la línea y la liquidación salen `simulada`) |
| `pagos.domain.ReglasLiquidacion` | `esLiquidable(finEn, corte)`: `corte ≥ fin + 48 h` (borde inclusivo, `ESPERA = 48 h`); `comision(bruto)`: 10 % del bruto total, escala 2, `HALF_UP` (el `CHECK` de la base fija lo mismo); `esSimulado` con `ReglasPago.PREFIJO_SIMULADO` |
| `Liquidacion.motivoNoPagable(cuenta, ahora)` | En orden `YA_PAGADA`, `SIN_CUENTA`, `CUENTA_RETENIDA` (RN-11). Una `PENDIENTE_CUENTA` se paga directo si ahora hay cuenta no retenida; `PAGADA` es final |
| `LiquidacionAdminService` | Rutas `/api/admin/liquidaciones` (generar, `?grupo=por-pagar\|pagadas&pagina=n`, `/{id}`, `/{id}/pago`), rol `ADMIN` antes de leer, todas `no-store`. Marcar pagada bloquea `liquidacion → cuenta_bancaria` (HU-18 toma `usuario → cuenta_bancaria`: sin ciclo) y guarda banco y cuenta **enmascarada**. Audita `LIQUIDACION_GENERADA`, `LIQUIDACION_PAGADA` y `CUENTA_BANCARIA_CONSULTADA_ADMIN` |
| `CuentaBancaria.numeroParaTransferir()` | Único acceso al número completo. Solo lo usa el detalle admin de una liquidación **pagable**, que lo audita; nunca las listas, la respuesta del pago, la auditoría, los logs ni una ruta del arrendador |
| `MisLiquidacionesService` | `GET /api/arrendadores/actual/liquidaciones?pagina=n` (rol `ARRENDADOR`, sin datos del usuario, del pago ni del admin) y `tieneLiquidacionesPendientes(arrendadorId)`: `true` si tiene alguna `GENERADA` o `PENDIENTE_CUENTA` |

**Lo que deben saber las historias que siguen.** **HU-22:** conecta `MisLiquidacionesService.tieneLiquidacionesPendientes` a
`cuentas.ParticipanteDatosPersonales`; los pagos `PAGADO` aún no liquidados no cuentan (si deben bloquear, lo decide su
spec); `liquidacion` no tiene cascada. **HU-26, hecho** (ver [Reclamos](#reclamos-qué-deja-hu-26)): abre reclamos solo si `ahora < fin + 48 h`, con el mismo fin
(`ReservaConHorario.instanteDeFin`) y la misma `ReglasLiquidacion.ESPERA`, para que no haya hueco ni solape; agrega el puerto
`pagos.ReclamosAbiertos` como un filtro más en `GeneradorLiquidaciones` (paso de selección); como cada generación liquida todo
lo pendiente, una reserva que vuelve a ser liquidable entra sola en la siguiente; un reembolso por reclamo deja la reserva
fuera entera (si se quiere liquidar el remanente, cambia `pagadosSinLiquidar`). **HU-28, hecho:** `GET /api/transacciones?pagina=n`
y `GET /api/transacciones/{id}/comprobante` (`TransaccionController`, `HistorialPagosService`, solo rol `USUARIO`, `no-store`).
Lista los pagos `EstadoPago.EN_HISTORIAL` (`PAGADO` y `REEMBOLSADO`; `REEMBOLSO_FALLIDO` no) de las reservas que devuelve el
puerto `pagos.ReservasDelUsuario`, con `PagoRepository.deReservas` (`Slice`, de 10 en 10); `montoReembolsado` es la suma de
los reembolsos `REEMBOLSADO` del pago. Los `sim-` salen solo con la pasarela simulada. El comprobante es un PDF 1.4 escrito a
mano por `GeneradorComprobantePdf` (sin librería) con solo los últimos 4 dígitos; cualquier id ajeno, inexistente o no
elegible da el mismo 404. Sin migración ni auditoría. Las IT que crean liquidaciones que crean liquidaciones borran `liquidacion_detalle` y `liquidacion` antes que `pago`,
`reserva`, `perfil_arrendador` y `usuario` (`PagosSoporte.limpiar` ya lo hace).

### Mis reservas: qué deja HU-14 (HU-15, HU-21, HU-22, HU-24, HU-26)

Dos rutas de solo lectura, con la identidad tomada del token y `?grupo=proximas|pasadas&pagina=n` (de 10 en 10, con
`hayMas` y sin total, como HU-10): `GET /api/reservas` (rol `USUARIO`, `PaginaReservasDTO<ReservaDTO>`, el DTO de HU-12
sin cambios) y `GET /api/arrendadores/actual/reservas` (rol `ARRENDADOR`, `PaginaReservasDTO<ReservaDeMiEspacioDTO>`: solo
`CONFIRMADA`, con `usuarioNombre` y sin `usuarioId`, correo, teléfono ni pago). Otro rol recibe 403 antes de validar los
parámetros. Ninguna escribe ni audita. `ConsultaReservasService` coordina las dos.

**Filtro por espacio (HU-21).** `GET /api/arrendadores/actual/reservas` acepta `espacioId` opcional: solo dígitos y de 1 a
`Long.MAX_VALUE` (si no, 400 en `espacioId`, junto con los errores de `grupo` y `pagina`); si no es uno de los espacios del
arrendador de la sesión, 404 genérico. Con él, `ConsultaReservasService` pasa `Set.of(espacioId)` a
`ReservaDAO.confirmadasDeEspacios`; sin él, responde igual que HU-14. Es la lista de "reservas afectadas" de una baja.

| Pieza | Patrón ★ | Qué hace |
|-------|----------|----------|
| `reservas.repository.ReservaDAO` | **DAO** (DataAccessObject) | **Primer DAO explícito del repo.** Interfaz sin métodos de consulta general: `delUsuario(usuarioId)` recibe el dueño directamente; `confirmadasDeEspacios(espacioIds)` recibe ids de espacios, no el arrendador, y la propiedad la garantiza `ConsultaReservasService`, que solo le pasa los espacios ya filtrados por el arrendador de la sesión (`CatalogoEspaciosService.nombresDelArrendador`); colección vacía → `List.of()` sin consultar (STRIDE E/I). El servicio solo conoce esta interfaz |
| `reservas.repository.ReservaDAOJpa` | DAO (implementación) | `@Repository` de paquete. Usa Spring Data por dentro (`ReservaRepository.findByUsuarioId`, `findByEspacioIdInAndEstado` y `ReservaBloqueRepository.horarios`, que agrupa `min(fecha)`, `min` y `max(hora_inicio)` sobre **todas** las filas, activas o no) y une los dos resultados en memoria: dos consultas, sin N+1. Una reserva sin filas se omite. "Un repositorio por agregado" se mantiene: el DAO no lo reemplaza |
| `reservas.domain.ReservaConHorario` | DAO (TransferObject) | Reserva + `fecha`, `horaInicio`, `horaFin` (1 a 24). `fin()` con 24 es las 00:00 del día siguiente. `esProxima(ahora, ahoraLocal)`: estado leído con `estadoEn` `PENDIENTE_PAGO` o `CONFIRMADA` y fin posterior a la hora de Lima. HU-21 y HU-22 pueden reutilizarla para "reservas futuras" |
| `reservas.domain.HistorialReservas` | **Iterator** (Aggregate) | Recibe la lista del DAO, guarda una copia privada ordenada por inicio e id y **nunca la expone**: solo entrega recorridos `proximas()` (ascendente), `pasadas()` (descendente) y `recorrer(grupo)` como `java.util.Iterator` |
| `reservas.domain.IteradorReservas` | Iterator (ConcreteIterator) | De paquete. Cursor sobre la copia en un sentido y con un criterio (`esProxima` o su negación); `hasNext` no consume, `next` agotado lanza `NoSuchElementException` y `remove` no está soportado |
| `reservas.domain.Pagina` | Iterator (Client) | `Pagina.de(recorrido, n, 10)`: salta `(n − 1) × 10` con aritmética `long`, toma 10 y `hayMas = recorrido.hasNext()`, sin materializar el resto |

**Clasificación con un solo instante.** Cada solicitud toma `Instant.now(reloj)` una vez y su hora de Lima: el corte y el
`estado` de cada DTO usan el mismo, así una pendiente que vence durante la solicitud no aparece en próximas como
`EXPIRADA`. El DAO no filtra por tiempo (la regla del vencimiento vive solo en `estadoEn`); la única condición de estado
en SQL es `CONFIRMADA`, equivalente porque una `CONFIRMADA` se lee siempre `CONFIRMADA`. Una `EXPIRADA` o `CANCELADA` con
fecha futura va a pasadas. La conversión `Reserva → ReservaDTO` está en `ConversionReservas` (la usa también HU-12).
Clasificar y paginar en memoria es un riesgo aceptado (decenas de reservas por persona); si crece, se pasa el corte a SQL
sin cambiar la API.

**Lo que deben saber las historias que siguen.** HU-15: una `CANCELADA` cae en las pasadas del usuario y desaparece de la
lista del arrendador (solo `CONFIRMADA`); si quiere mostrársela, amplía `confirmadasDeEspacios` o agrega un método al DAO,
siempre con el dueño en la firma. HU-26 puede **ampliar** `ReservaDTO`, sin quitar ni cambiar campos, pero hay pruebas que fijan
sus 11 campos; HU-24 prefirió `GET /api/resenas/pendientes`. Un índice en
`reserva(espacio_id)` iría en una `V4_n` libre.

### Cancelación: qué deja HU-15 (HU-16, HU-21, HU-28)

Cuatro rutas, una por papel y método: `GET` (vista previa, CA2) y `POST` (confirmación, CA3) de
`/api/reservas/{id}/cancelacion` (rol `USUARIO`) y de `/api/arrendadores/actual/reservas/{id}/cancelacion` (rol
`ARRENDADOR`). Quién cancela (`QuienCancela`) lo fija la ruta, nunca el cuerpo; el `POST` solo lee `montoReembolsoVisto`
para **compararlo** con el monto recalculado (400 si cambió). Las cuatro delegan en `reservas.service.CancelacionReservaService`.
Solo se cancela una reserva cuyo estado leído es `CONFIRMADA` y que aún no empieza (`Reserva.motivoNoCancelable`).

| Pieza | Patrón ★ | Qué hace |
|-------|----------|----------|
| `reservas.domain.PoliticaReembolso` | **Strategy** | Una regla fija de RN-06: `codigo()`, `aplicaA(SolicitudCancelacion)`, `porcentaje()` y `monto(montoPagado)` por defecto (escala 2, `HALF_UP`). `ANTICIPACION_MINIMA` = 48 h |
| `ReembolsoUsuarioConAnticipacion`, `ReembolsoUsuarioSinAnticipacion`, `ReembolsoCancelaArrendador`, `ReembolsoPorModeracion` (HU-27) | Strategy (ConcreteStrategy) | Usuario con ≥ 48 h → 100 %; usuario con < 48 h → 0 %; arrendador → 100 %; `QuienCancela.ADMIN` (ocultar por fraude, código `MODERACION`) → 100 % con cualquier anticipación. Excluyentes y exhaustivas |
| `reservas.domain.CalculoReembolso` | Strategy (Context) | `politicaFija()` arma la lista de RN-06 (sin Spring ni configuración); `calcular` elige **la única** que aplica (`IllegalStateException` si no hay exactamente una). Agregar una regla es agregar una clase a la lista |
| `CancelacionReservaService` | Strategy (Client) | Arma `SolicitudCancelacion(quien, inicio, ahora, montoTotal)` con datos del servidor (inicio en `America/Lima`, `ReservaConHorario.instanteDeInicio`) y no tiene ningún `if` sobre la regla |

**Confirmación.** Orden `espacio → reserva` como HU-13: `findEspacioIdById` → `DisponibilidadService.bloquearEspacio` →
`findParaActualizar` → propiedad (`reserva.usuarioId` o `EspacioBloqueado.arrendadorId`) → *ahora* (después de los bloqueos)
→ motivo → cálculo → comparación → `Reserva.cancelar` → `desactivarDeReserva` → `PagoService.reembolsarCancelacion` si el
monto es mayor que 0 → auditoría `RESERVA_CANCELADA` → `AvisosCancelacion` (dos correos con `DespuesDelCommit`). Todo en una
transacción: un error revierte la cancelación y la solicitud de reembolso. Dos confirmaciones a la vez se serializan en el
espacio y la segunda ve `CANCELADA` (400). La vista previa es `readOnly` y no bloquea.

**`CANCELADA` con sus datos.** La fila guarda `cancelada_por`, `cancelada_en` y `monto_reembolso` (HU-16 y HU-28 los leen sin
parsear la auditoría); el porcentaje y la política quedan en el detalle de `RESERVA_CANCELADA`. Para el usuario la cancelada
cae en Pasadas; para el arrendador desaparece de su lista (HU-14 R15, sin cambios). La regla "id bien escrito" de las rutas
`/{id}` de reservas vive en `ConversionReservas.idReserva`. HU-21 la reutiliza sin cambios: el arrendador cancela una a una
las reservas que impiden la baja con la ruta `ARRENDADOR` (100 %).

**`QuienCancela.ADMIN` (HU-27).** Solo lo usa `CancelacionPorModeracionImpl` al ocultar un espacio por fraude;
`CancelacionReservaService` lo rechaza con 403 antes de leer (las rutas de HU-15 nunca cancelan como admin).
`ck_reserva_cancelada_por` admite `ADMIN` (`V8_2`); `Reserva.admiteLiquidacion` ya la excluye de toda liquidación.

### Datos personales: qué deja HU-22 (HU-24, HU-26, HU-27)

Derecho de acceso y supresión (Ley 29733) en `cuentas.service.DatosPersonalesService` (clase concreta) y
`DatosPersonalesController`. Dos rutas `POST` con `{ "contrasena" }` en el cuerpo (nunca en la URL), sin id, `no-store`:
`/api/usuarios/actual/exportaciones` (200 con el JSON y `Content-Disposition: attachment;
filename="espacioya-mis-datos-<AAAA-MM-DD>.json"`, fecha de Lima de `generadaEn`) y `/api/usuarios/actual/eliminacion` (200
`MensajeDTO`). `ADMIN` recibe 403 antes de todo. La contraseña la comprueba `ReautenticacionPerfil` (operaciones
`EXPORTACION` y `ELIMINACION`, el **mismo** cupo `perfil|<id>` de HU-20) fuera de transacción: con contraseña incorrecta
no se revelan los impedimentos.

- **Exportación.** Una transacción: `perfil` (rol, correo, correo pendiente, nombre, teléfono, si hay foto, si hay MFA,
  la preferencia `recordatoriosActivos` —HU-25 R37—, consentimiento y, para el arrendador, facturación con el documento **enmascarado**) + las secciones de cada participante
  en orden (`reservas` y `pagos` del usuario; `reservas` `CONFIRMADA` de sus espacios sin datos de quien reservó,
  `liquidaciones` y `cuentaBancaria` enmascarada o `null` del arrendador) + auditoría `DATOS_EXPORTADOS`
  (`secciones=<claves>`). Nunca hash, documento o cuenta completos, secreto MFA, `ultimos4` ni ids de la pasarela.
- **Eliminación.** `bcrypt` del secreto descartable **antes** de la transacción → `usuario` `FOR UPDATE` → impedimentos de
  todos los participantes (400 `ValidacionException`, un error por campo en el orden `reservas`, `liquidaciones`, `pagos`;
  nada cambia) → `Usuario.anonimizar` → perfil del arrendador (`findParaActualizar`, `PerfilArrendador.anonimizar`) y sus
  `documento_identidad` → tokens pendientes de **todos** los `TipoToken` anulados → `codigo_respaldo`, `mfa`,
  `desafio_mfa` borrados → `anonimizar` de cada participante (`pagos` borra `cuenta_bancaria`) → auditoría
  `CUENTA_ELIMINADA` (`rol=<ROL>`) → archivos (foto y documentos) con `DespuesDelCommit` → `SesionRepository.revocarTodas`
  (último: hace `flush` y `clear`). Cualquier fallo revierte todo. Marcadores y tablas: `docs/modelo-datos.md`.
- **Impedimentos (RN-17).** Usuario: alguna reserva con `ReservaConHorario.esProxima` (pendiente vigente o confirmada que
  no terminó). Arrendador: por cada espacio (`nombresDelArrendador`, id ascendente) `bloquearEspacio` y **después**
  `ReservasDelEspacio.bloquesReservados` con algún bloque que `terminaDespuesDe` ahora (criterio de HU-21); después
  `LiquidacionRepository.bloquearGeneracion` y **después** liquidaciones `GENERADA`/`PENDIENTE_CUENTA` y cobros por liquidar
  (`pagadosSinLiquidar` + `ReservasLiquidables.describir`, del arrendador y `admitida`; mismo criterio de simulados que
  `GeneradorLiquidaciones`). Reembolsos pendientes o fallidos y pagos `PAGADO_TARDIO`/`MONTO_DISTINTO` no bloquean (D3).
- **Orden de bloqueos.** `usuario → espacio` (como crear una reserva, `usuario → espacio`, y la baja de HU-21) y `usuario →
  advisory 190019 → perfil_arrendador → cuenta_bancaria`. Crear una reserva relee `cuentaActiva` bajo el bloqueo del
  espacio: o la eliminación la ve, o la reserva encuentra la cuenta eliminada (404). `pagadosSinLiquidar` es global: riesgo
  aceptado.
- **Qué se conserva.** Las filas de `usuario` y `perfil_arrendador` (anonimizadas), reservas, pagos, reembolsos,
  liquidaciones, auditoría (sin fecha de fin: el plazo legal sigue abierto, §9 punto 6), `revision_identidad`,
  `suspension_cuenta` y los espacios con su estado, ocultos por HU-09 D1. No hay correo de aviso (D8).
- **Para las que siguen.** **HU-24** implementó el puerto en `busqueda` (ver [Reseñas](#reseñas-qué-deja-hu-24-hu-26-hu-27));
  **HU-26** y **HU-27** igual con reclamos y reportes (`DatosPersonalesReportesImpl`, `@Order(5)`: exporta la sección
  `reportes` sin la decisión del admin, no impide ni anonimiza nada).

### Reseñas: qué deja HU-24 (HU-26, HU-27)

Tabla `resena` (`V7_2`) en `busqueda`, que estrena sus capas `domain` y `repository`. `ResenaService` es una clase concreta:
HU-27 creará la interfaz pública cuando `administracion` la necesite. Una reseña es por **reserva**: sin una reserva propia
reseñable no hay forma de crear una.

| Ruta | Sesión | Respuesta |
|------|--------|-----------|
| `POST /api/resenas` con `reservaId`, `calificacion` y `comentario` | `USUARIO` | 201 `ResenaCreadaDTO`; 400 por campo o por regla; 403; 404 si la reserva es ajena o no existe |
| `GET /api/resenas/pendientes` | `USUARIO` | `reservaIds` reseñables, ascendentes. Es lo que la pantalla usa para mostrar "Calificar": `ReservaDTO` **no** se amplió (P5) |
| `GET /api/espacios/{id}/resenas?pagina=n` | Pública, cupo `catalogo`, `permitAll` en `ConfiguracionSeguridad` | `PaginaResenasDTO`, de 10 en 10, por `creado_en` e `id` descendentes; cada reseña solo con `id`, `calificacion`, `comentario` y `fecha`. 404 antes de validar `pagina` |

- **Regla (`ReglasResena`).** Estado leído `CONFIRMADA` y `fin ≤ ahora < fin + 14 días`, con `fin` =
  `ReservaConHorario.instanteDeFin`, el mismo de HU-19 y HU-26. Un espacio que dejó de ser visible se puede reseñar (P9), y una
  reserva `CONFIRMADA` con reembolso por reclamo también (P10).
- **`reservas.service.ReservasParaResena`.** Interfaz pública angosta, **no es un puerto** (`busqueda → reservas` ya estaba
  permitida): `delUsuario(usuarioId, reservaId)` (404 si no es suya) y `confirmadasDe(usuarioId)` →
  `ReservaParaResena(id, espacioId, confirmada, finEn)`. Sin sesión ni rol. `ReservasParaResenaImpl` lee con
  `ReservaDAO.delUsuario` y filtra en memoria: el DAO exige el dueño.
- **Orden de `crear`.** `@Valid` → rol → `SanitizadorTexto.limpiar` → transacción: reserva propia → *ya reseñada* → regla →
  `saveAndFlush` → auditoría `RESENA_CREADA`. Sin bloqueos: una `CONFIRMADA` terminada ya no cambia, y `uq_resena_reserva` frena
  la carrera. Sin `@Transactional`, con `TransactionTemplate`, para traducir esa violación por su nombre (como HU-12).
- **Ficha.** `FichaPublicaDTO` agrega `calificacionPromedio` (1 decimal, `HALF_UP`, con enteros en `ResumenCalificaciones`; nulo sin
  reseñas) y `totalResenas`, solo de las `VISIBLE`. `ResultadoEspacioDTO` no cambia: la búsqueda no muestra ni ordena por promedio.
- **Paginación.** Spring Data rechaza un offset mayor que `Integer.MAX_VALUE`: `ResenaDAOJpa.visiblesDeEspacio` responde esa
  página (válida) vacía sin consultar. Quien pagine con `PageRequest` y números enormes debe hacer lo mismo.
- **`comun.texto.SanitizadorTexto.limpiar(String)`.** Normaliza saltos de línea, quita caracteres de control, etiquetas y todo `<` o
  `>` restante, y recorta. Puede devolver vacío. No escapa a entidades. Reutilizable por HU-26.
- **Datos personales.** `DatosPersonalesResenasImpl` exporta la sección `resenas` solo para el rol `USUARIO`, no genera
  impedimentos y al anonimizar reemplaza el `comentario` por `Resena.COMENTARIO_ELIMINADO`, conservando calificación, estado y autor.
- **HU-27** agregó `Resena.estaVisible()`/`ocultar()`, `ResenaRepository.findParaActualizar` y la interfaz pública
  `busqueda.service.ModeracionResenas` (`ModeracionResenasImpl`); el reporte guarda el `id` de la reseña en
  `reporte.resena_id`. Las consultas ya filtran `VISIBLE` y una reseña oculta sigue contando como *ya reseñada*.
  Toda IT que cree reseñas las borra antes que `reserva`, `espacio` y `usuario` (sin cascada), y antes borra `reporte`.
- **★ Decorator y DAO (reapertura de HU-24).** `ResenaService.crear` es el Client: comprueba el rol y llama al único bean
  `PublicacionResena`, que `ConfiguracionPublicacionResena.cadena` arma una vez como `PublicacionSanitizada(
  PublicacionTransaccional(PublicacionAuditada(PublicacionResenaBase)))`. El orden importa: la sanitización va fuera de la
  transacción (un comentario vacío no abre nada) y la auditoría dentro (si falla, no queda la reseña). Un comportamiento nuevo
  es otra subclase de `PublicacionResenaDecorador` y una línea en `cadena`. La tabla `resena` solo se lee y escribe por
  `busqueda.repository.ResenaDAO` (`ResenaDAOJpa`, `@Repository` de paquete, como `ReservaDAO`): `existeParaReserva`,
  `guardar` (`saveAndFlush`), `reservasResenadasPor`, `visiblesDeEspacio` (orden, filtro `VISIBLE` y tope de offset),
  `resumenVisibles`, `delUsuario`, `anonimizarComentarios`, `buscar`, `buscarVarias` y `buscarParaActualizar`.
  `ResenaRepository` solo lo usa `ResenaDAOJpa`: HU-26, HU-27 y las que sigan **no** deben inyectarlo (HU-27 ya pasa por el
  DAO; `AccesoResenasTest` lo comprueba).

### Reclamos: qué deja HU-26

Tablas `reclamo` y `mensaje_reclamo` (`V8_1`) en `administracion`. Un reclamo por reserva (`uq_reclamo_reserva`); guarda
sus dos partes (`usuario_id`, `arrendador_id`) para resolver la propiedad sin cruzar módulos. Estados `ABIERTO` →
`RESUELTO_USUARIO` / `RESUELTO_ARRENDADOR` (finales); la reserva sigue `CONFIRMADA`.

| Ruta | Sesión | Qué hace |
|------|--------|----------|
| `POST /api/reclamos` | `USUARIO` | Abre un reclamo (`reservaId`, `motivo` de catálogo, `descripcion` 1–1000 sanitizada) sobre una reserva propia `CONFIRMADA` con `inicio ≤ ahora < fin + 48 h` y no liquidada |
| `GET /api/reclamos/reclamables` | `USUARIO` | `reservaIds` reclamables, ascendentes (`ReservaDTO` no se amplió) |
| `GET /api/reclamos?pagina=n` · `GET /api/reclamos/{id}` | Parte (`ADMIN` 403) | Lista de a 10 y detalle con el hilo, `papel`, `mensajesRestantes`, `puedeEscribir`; el monto solo al usuario |
| `POST /api/reclamos/{id}/mensajes` | Parte | Hasta 10 mensajes por parte, solo mientras `ABIERTO`; el admin no escribe |
| `GET /api/admin/reclamos?grupo=abiertos\|resueltos&pagina=n` · `/{id}` · `POST /{id}/resolucion` | `ADMIN` | Cola, detalle (nombres y monto pagado) y resolución: `USUARIO` con `TOTAL` (monto del servidor) o `PARCIAL` (`0 < m < pagado`, 2 decimales), o `ARRENDADOR` |

Todas las `GET` son `no-store`; no hay `PUT`, `PATCH` ni `DELETE`. Un `id` mal escrito es el 404 genérico.

- **Interfaz `reservas.service.ReservasParaReclamo`** (no es un puerto): `delUsuario(usuarioId, reservaId)` y
  `confirmadasDe(usuarioId)` con `ReservaDAO.delUsuario` (exige el dueño) y `describir(ids)` por lote sin dueño →
  `ReservaParaReclamo` (espacio, arrendador, `confirmada` leído con `estadoEn`, fecha, horas, `inicioEn`, `finEn` en Lima).
- **Regla** `administracion.domain.ReglasReclamo`: `PLAZO = ReglasLiquidacion.ESPERA`; el borde superior coincide con el
  inferior de `esLiquidable`.
- **Retención (RN-12).** Abrir toma `PagoService.bloquearGeneracionLiquidaciones` y **después** lee el reloj y
  `reservaLiquidada`: o la generación ve el reclamo, o su corte ya pasó `fin + 48 h`. `GeneradorLiquidaciones` excluye lo
  que devuelve el puerto `ReclamosAbiertos`; un reclamo resuelto a favor del arrendador deja la reserva liquidable en la
  siguiente generación; uno a favor del usuario crea un reembolso `RECLAMO` y la reserva queda fuera para siempre
  (`pagadosSinLiquidar`), aunque el reembolso sea parcial (P7).
- **Orden de bloqueos.** Abrir: `advisory 190019` → `INSERT reclamo`. Mensaje: `reclamo` (`FOR UPDATE`). Resolver:
  `reclamo` → `INSERT reembolso`; tras el commit, `RegistroReembolsos` toma `reembolso → pago`. Nadie toma `reclamo` y
  después `advisory`.
- **Correos** (Factory Method de HU-25, en `administracion.service.correo`): al abrir, al arrendador sin la descripción ni
  datos del usuario; al resolver, a cada parte (el del arrendador sin montos). Client `AvisosReclamo`, un `DespuesDelCommit`
  por correo.
- **Auditoría:** `RECLAMO_ABIERTO` (`reservaId;motivo`), `RECLAMO_MENSAJE` (`mensajeId;papel`), `RECLAMO_RESUELTO`
  (`reservaId;aFavorDe[;reembolso;monto]`), nunca los textos.
- **Datos personales:** `DatosPersonalesReclamosImpl` (`@Order(4)`) exporta la sección `reclamos` (solo los textos del
  titular), impide eliminar la cuenta con un reclamo `ABIERTO` (campo `reclamos`) y reemplaza sus textos por "Texto
  eliminado.".
- **Pruebas:** toda IT que cree reclamos borra `mensaje_reclamo` y `reclamo` antes que `reserva` y `usuario`
  (`PagosSoporte`, `DatosPersonalesSoporte` y `ReclamosSoporte` ya lo hacen).

### Moderación: qué deja HU-27

Tabla `reporte` (`V8_2`) en `administracion`: `espacio_id` **o** `resena_id` (una sola, con FK y `ck_reporte_objetivo`),
motivo de catálogo (`FRAUDE`, `INFORMACION_FALSA`, `CONTENIDO_INAPROPIADO`, `SPAM`, sin texto libre) y la decisión en
`estado` (`POR_REVISAR` → `MANTENIDO` / `OCULTADO`, finales), `admin_id`, `motivo_decision`, `resuelto_en` y
`por_fraude`. Un reporte por reportante y contenido (`uq_reporte_espacio`, `uq_reporte_resena`).

| Ruta | Sesión | Qué hace |
|------|--------|----------|
| `POST /api/reportes` | `USUARIO` o `ARRENDADOR` (`ADMIN` 403) | Reporta un espacio visible o una reseña `VISIBLE` de un espacio visible (lo demás, 404 genérico); contenido propio o ya reportado, 400; 11.º reporte en 24 h, 429 |
| `GET /api/admin/reportes?grupo=pendientes\|resueltos&pagina=n` · `/{id}` | `ADMIN` | Cola de a 10 (por revisar del más antiguo; resueltos del más reciente) y detalle con el contenido en su estado actual, sin datos del reportante ni contacto del arrendador |
| `POST /api/admin/reportes/{id}/decision` | `ADMIN` | `MANTENER`, `OCULTAR` u `OCULTAR_POR_FRAUDE` (solo espacios) con `motivoDecision` 1–500 sanitizado; resuelve todos los reportes `POR_REVISAR` del mismo contenido |

Todas `no-store`; no hay `PUT`, `PATCH` ni `DELETE`. IPs permitidas y MFA por el prefijo `/api/admin/`.

- **Cupo (RNF-SEG-17).** 10 reportes por reportante en una ventana móvil de 24 h, contados en la base
  (`countByReportanteIdAndCreadoEnAfter`) **después** de `CuentaService.bloquearCuenta`; no usa `LimitadorIntentos`. Se
  comprueba antes de mirar el contenido.
- **Interfaces públicas** (no son puertos: `administracion → (todos)`). `espacios.service.ModeracionEspacios`
  (`reportable`, `describir`, `ocultar` → `EstadoEspacioService.ocultarComoAdmin`), `busqueda.service.ModeracionResenas`
  (`reportable`, `describir`, `ocultar`) y `reservas.service.CancelacionPorModeracion.cancelarPorFraude`. Los `ocultar` y
  `cancelarPorFraude` son `MANDATORY` y vuelven a exigir `ADMIN` antes de leer.
- **Ocultar por fraude.** Además de ocultar el espacio, en la misma transacción cancela sus reservas `CONFIRMADA` que aún
  no empiezan (`QuienCancela.ADMIN`, política `MODERACION` al 100 %), desactiva sus bloques, pide su reembolso con
  `PagoService.reembolsarCancelacion` (pasarela tras el commit), audita `RESERVA_CANCELADA` y avisa a cada usuario con el
  correo de HU-15 ("Cancelada por: EspacioYa", `AvisosCancelacion.programarAlUsuario`); al arrendador, un solo correo de
  moderación con la cantidad. Las iniciadas, terminadas y pendientes no cambian. Cualquier fallo revierte toda la decisión.
  `OCULTAR` sin fraude no toca reservas.
- **Orden de bloqueos.** Reportar: `usuario` → `INSERT reporte`. Decidir: `reporte` (hermanos, `FOR UPDATE` por id) →
  `espacio` o `resena` → (por fraude) `reserva` (confirmadas del espacio por id) → `pago`. Es el `espacio → reserva → pago`
  de HU-13/HU-15 precedido por `reporte`, que nadie más toma.
- **Correo** (Factory Method de HU-25): `CorreoEspacioOcultado` al arrendador con el motivo de la decisión (nunca el del
  reporte ni el reportante), solo al ocultar un espacio; Client `AvisosModeracion` con `DespuesDelCommit`.
- **Auditoría:** `REPORTE_CREADO` (`tipo;objetivoId;motivo`), `REPORTE_RESUELTO`
  (`tipo;objetivoId;decision;reportes;reservasCanceladas`, sin el motivo), `OCULTACION_ESPACIO` y `OCULTACION_RESENA`.
- **Datos personales:** `DatosPersonalesReportesImpl` (`@Order(5)`) exporta la sección `reportes`; sin impedimentos ni
  anonimización (no hay texto libre).
- **Pruebas:** toda IT que cree reportes borra `reporte` antes que `resena`, `espacio` y `usuario` (`ReclamosSoporte` y
  `DatosPersonalesSoporte` ya lo hacen).

### Correo: qué deja HU-25 (★ Factory Method, Adapter)

Paquete `comun/correo` (no depende de ningún módulo). Ninguna clase fuera de `EnvioCorreoSmtp` importa
`org.springframework.mail` ni `jakarta.mail`.

| Pieza | Rol | Qué hace |
|-------|-----|----------|
| `comun.correo.EnvioCorreo` | Adapter · Target | `enviar(MensajeCorreo)`; falla del proveedor → `ServicioExternoException`. `MensajeCorreo(para, asunto, cuerpo)`: texto plano, un único destinatario, sin copia ni copia oculta; rechaza vacíos |
| `comun.correo.EnvioCorreoSmtp` | Adapter · Adapter | Traduce a `SimpleMailMessage` (`from` = `espacioya.correo.remitente`) y lo entrega al Adaptee `JavaMailSender` (`spring-boot-starter-mail`). Cualquier `MailException` → `ServicioExternoException`, sin el texto del proveedor ni el destinatario. Sin remitente o sin `spring.mail.host` no se construye y el backend no arranca |
| `comun.correo.EnvioCorreoSimulado` | Adapter sin proveedor | Solo escribe "Envío de correo simulado: no se envió ningún correo." (sin destinatario, asunto ni cuerpo). Es el de UAT |
| `comun.correo.FabricaCorreo<E>` | Factory Method · Creator | `public final enviar(destinatario, evento)`: llama al método fábrica `protected abstract Correo crearCorreo(E)`, arma el `MensajeCorreo` y lo entrega a `EnvioCorreo`. Las fábricas **no son beans**: el Client las crea con el `EnvioCorreo` inyectado |
| `comun.correo.Correo` | Factory Method · Product | `asunto()` (fijo por evento) y `cuerpo()`. Los ConcreteProducts son `record` que reciben su evento |
| `comun.correo.FormatoCorreo`, `SinDatos` | — | Fecha `dd/MM/yyyy`, horario `HH:00 a HH:00` (fin 24 → `24:00`), monto `S/ 1234.50`, instante de Lima `dd/MM/yyyy HH:mm`; `SinDatos.VALOR` para los correos sin datos |
| `cuentas.service.correo.FabricaCorreo*` / `Correo*` | ConcreteCreator / ConcreteProduct | 13 pares, uno por método de `NotificadorCorreo` (bienvenida, recuperación, identidad ×2, suspensión, reactivación, comprobante, reserva pagada, cancelación ×2, cuenta bancaria, cambio de correo ×2) |
| `cuentas.service.impl.NotificadorCorreoImpl` | Client | Implementación real de `NotificadorCorreo` con `proveedor=smtp`; cada método llama a la fábrica de su evento. `notificarVerificacion` es un correo de bienvenida sin enlace (HU-25 D11). Con `simulado` queda `NotificadorCorreoSimulado` |
| `reservas.service.recordatorio.FabricaRecordatorio{Usuario,Arrendador}` | ConcreteCreator | Crean `CorreoRecordatorio{Usuario,Arrendador}` con `DatosRecordatorio(espacio, fecha, horaInicio, horaFin)` y nada más (sin montos, tarjeta, documentos ni contacto de la otra parte). El recordatorio **no** pasa por `NotificadorCorreo` |

`NotificadorCorreo` se queda en `cuentas.service` (HU-25 D10): moverlo a `comun` solo cambiaba imports.

**Agregar un correo (HU-26, HU-27).** Un `record` que implementa `Correo` (asunto fijo, cuerpo con solo los datos
necesarios) y una subclase `final` de `FabricaCorreo<Evento>` en el módulo dueño del evento; el Client la crea con el
`EnvioCorreo` inyectado y llama a `enviar` tras el commit (`DespuesDelCommit`). No se toca `FabricaCorreo`,
`EnvioCorreo` ni los demás correos.

**Selección del proveedor.** `espacioya.correo.proveedor` (`CORREO_PROVEEDOR`, por defecto `simulado`) con
`@ConditionalOnProperty`, como `PasarelaPago` (sin fábrica de adaptadores): `smtp` → `EnvioCorreoSmtp` +
`NotificadorCorreoImpl`; `simulado` → `EnvioCorreoSimulado` + `NotificadorCorreoSimulado`; otro valor → no hay bean y el
backend no arranca. Las pruebas fijan `simulado` y reemplazan `EnvioCorreo` o `NotificadorCorreo` con dobles `@Primary`.

**Activar SMTP real (HU-25 D12).** UAT y desarrollo usan `simulado`. Para enviar correos reales basta con definir estas
variables (servidor o `.env`, que no se versiona) y reiniciar; no se cambia código ni pruebas:

| Variable | Ejemplo | Obligatoria con `smtp` |
|---|---|---|
| `CORREO_PROVEEDOR` | `smtp` | Sí |
| `CORREO_REMITENTE` | `EspacioYa <no-responder@dominio-verificado>` (dirección autorizada por el proveedor) | Sí |
| `CORREO_SMTP_HOST` | host SMTP del proveedor (Gmail, Brevo, SES, Mailtrap…) | Sí |
| `CORREO_SMTP_PUERTO` | `587` (STARTTLS) | No, por defecto `587` |
| `CORREO_SMTP_USUARIO` | usuario o clave de API SMTP | Según el proveedor |
| `CORREO_SMTP_CONTRASENA` | contraseña de aplicación o clave SMTP | Según el proveedor |

`application.properties` exige STARTTLS (RNF-SEG-13) y fija timeouts de 5 s; el proveedor debe aceptar STARTTLS en ese
puerto y tener el remitente verificado. Para ensayar sin buzones reales sirve un servidor SMTP de pruebas (Mailtrap,
MailHog) con las mismas variables. Riesgo aceptado: el envío corre tras el commit en el hilo que confirma (webhook,
cancelación), así que una caída del proveedor alarga esas respuestas hasta 5 s por correo (no hay `@Async`).

### Almacenamiento: qué deja HU-29 (★ Adapter)

Paquete `comun/almacenamiento`. Ninguna clase fuera de `AlmacenamientoS3` y `ConfiguracionAlmacenamientoS3` importa
`software.amazon.awssdk` (las pruebas sí pueden).

| Pieza | Rol | Qué hace |
|-------|-----|----------|
| `AlmacenamientoArchivos` | Adapter · Target | `guardar`/`leer`/`eliminar`. Contrato común: guardar de nuevo reemplaza, `leer` vacío si la clave no existe, `eliminar` idempotente, falla del proveedor → `UncheckedIOException` con el mismo mensaje en ambos adaptadores (`FallaAlmacenamiento`), clave en blanco, absoluta o con `..` → `IllegalArgumentException`. Lo verifican `ContratoAlmacenamientoArchivos` y sus dos subclases |
| `AlmacenamientoLocal` | Adapter (Adaptee `java.nio.file.Files`) | Disco; por defecto (tipo ausente o `local`) |
| `AlmacenamientoS3` | Adapter (Adaptee `S3Client`) | Mismas claves que el disco, sin prefijo. `PutObject` con SSE-S3 (`AES256`) y sin ACL, `GetObject` (`NoSuchKey` → vacío), `DeleteObject`. La causa de la `UncheckedIOException` solo lleva el tipo de excepción y los códigos de S3: nunca la clave, el bucket ni el texto del proveedor. Al construirse comprueba el bucket con `HeadBucket`: inexistente, 403, otra región u otra falla → el backend no arranca |
| `ConfiguracionAlmacenamientoS3` | Crea el Adaptee | `S3Client` síncrono (cliente HTTP Apache del SDK), credenciales de `DefaultCredentialsProvider` (rol de la tarea; nunca claves en la configuración), región obligatoria, timeouts de 10 s por intento y 30 s por operación |
| `SeleccionAlmacenamiento` | `BeanFactoryPostProcessor` | Valida el tipo antes de crear beans: con otro valor (incluido vacío) el arranque falla nombrando `ALMACENAMIENTO_TIPO` y los valores `local` y `s3` |
| `ArchivosPrivados`, `ArchivosPublicos` | Clients | Sin cambios: validan la clave, cifran (privados) y delegan en el Target |

**Selección.** `espacioya.almacenamiento.tipo` con `@ConditionalOnProperty`, como `EnvioCorreo` y `PasarelaPago` (sin
fábrica de adaptadores). Con `s3` no hay fallback al disco: si falta el bucket o la región, o el bucket no existe o no es
accesible, el backend no arranca. Las IT fijan `local` (`src/test/resources/config/application.properties`).

| Variable | Ejemplo | Obligatoria |
|---|---|---|
| `ALMACENAMIENTO_TIPO` | `s3` | No, por defecto `local` |
| `ALMACENAMIENTO_S3_BUCKET` | nombre del bucket (ya creado, privado, SSE-S3; la app no lo crea) | Con `s3` |
| `ALMACENAMIENTO_S3_REGION` | `us-east-1` | Con `s3` |

Infraestructura que se supone en UAT (no es código): bucket con Block Public Access y cifrado SSE-S3 por defecto,
`taskRoleArn` = `LabRole` y salida de red de las tareas a S3 (endpoint de VPC tipo gateway). Ver
`specs/HU-29/design.md` § 11.

## Seguridad transversal (STRIDE)

Cada historia trae sus propios controles STRIDE en `docs/historias/HU-XX.md`.
Además, en todo el sistema:

- **(S) Suplantación:** la identidad se toma siempre de la sesión en el backend,
  nunca de un ID enviado por el cliente. Los tokens se invalidan al cerrar
  sesión o tras 30 minutos de inactividad. Al suspender una cuenta se revocan
  todas sus sesiones y, además, `SesionService.validar` rechaza (401, sin
  renovar) cualquier sesión de una cuenta cuyo estado no es `ACTIVA` (HU-09 D3).
- **(S) Panel admin: IPs permitidas + MFA:** toda ruta `/api/admin/**` pasa primero
  por `comun/seguridad/FiltroIpsAdmin`, que solo deja pasar las IPs de
  `ADMIN_IPS_PERMITIDAS` (IPs o rangos CIDR; vacía = nadie) y si no responde 403
  genérico; el admin además solo inicia sesión desde esas IPs (HU-06). Desde
  HU-17 el control de IPs **complementa** a MFA (P3): el admin debe configurar
  MFA en su primer inicio de sesión y usarlo en cada uno, y
  `SesionService.validar` rechaza (401, sin renovar) la sesión de un admin sin
  MFA activo (DT-3). La IP del
  cliente la resuelve Tomcat (`server.forward-headers-strategy=native`) y solo
  cree a `X-Forwarded-For` si la conexión viene de `PROXIES_CONFIANZA`; nunca se
  lee ese encabezado a mano. HU-09 y HU-27 ponen sus rutas bajo el mismo prefijo.
- **(S) Inicio de sesión con MFA (HU-17):** con MFA activo (o si es admin),
  `POST /api/sesiones` responde 202 con un **desafío** de un solo uso y 5 min
  (`desafio_mfa`, solo su SHA-256) que no es una sesión: solo se canjea por ella en
  `POST /api/sesiones/mfa` con un código TOTP (RFC 6238, `cuentas/domain/Totp`) o
  de respaldo. Secretos cifrados con `CifradorCampos`, códigos de respaldo con
  `HuellaCampos`; 5 fallos por desafío y 10 por cuenta en 15 min.
- **(T) Manipulación:** contraseñas con hash y sal (bcrypt o argon2). Todo lo
  que viene del cliente se valida en el servidor; los montos se calculan solo ahí.
- **(R) Repudio:** las acciones sensibles se registran en la auditoría con
  quién, qué, cuándo e IP.
- **(I) Divulgación:** TLS, mensajes de error genéricos, datos sensibles
  cifrados en reposo y documentos servidos con URL firmada de corta duración.
- **(D) Denegación de servicio:** rate limiting donde la historia lo indica.
  Los límites están en RNF-SEG-17 de `docs/requerimientos.md`.
  Desde HU-30, los límites de `comun.limite.LimitadorIntentos` se cuentan en PostgreSQL (`intento_limite`, una fila
  por clave con su huella HMAC y un `INSERT … ON CONFLICT` atómico) y valen para el conjunto de réplicas; la limpieza
  de vencidos (`ProgramacionLimpiezaIntentos`) corre en cada réplica con un bloqueo consultivo. En UAT, los límites
  por IP se complementan con una regla *rate-based* del AWS WAF en el ALB: es de la guía de despliegue, no del código.
- **(E) Elevación de privilegios:** roles asignados y validados en el backend en
  cada solicitud; la propiedad del recurso se valida antes de cada edición o
  consulta. Las cuentas de administrador solo se crean por script interno
  (`cuentas/service/CreadorAdmin`, activado con `ADMIN_CREAR=true`; HU-06). Los servicios del admin comprueban el rol
  con `comun.seguridad.RolesSesion.exigirAdmin` antes de leer (HU-27 DT-13: reemplazó las copias privadas).
- **PCI DSS:** la plataforma nunca recibe ni guarda datos de tarjeta. El estado
  del pago cambia solo por un webhook con firma verificada e idempotente. Mientras
  UAT use la pasarela simulada, este control no se demuestra ahí: ver C1 y C2 en
  `specs/HU-13/design.md` § 14.13.
- **Datos personales (Ley 29733):** consentimiento registrado con versión de la
  política (HU-01); derecho de acceso y supresión (HU-22).

## Estados del sistema

**Cuenta del arrendador.** Nunca se dice "verificado" a secas: siempre se aclara
si es el correo o la identidad.

| Estado                | Qué habilita |
|-----------------------|--------------|
| Correo pendiente      | Nada; espera la verificación del correo. Hoy no se llega a este estado: mientras el envío de correo sea simulado (HU-01, S5), la cuenta nace con el correo verificado |
| Correo verificado     | Iniciar sesión, crear borradores de espacios, subir documentos |
| Identidad en revisión | Nada adicional; espera la revisión del admin |
| Identidad verificada  | Publicar espacios visibles, recibir reservas pagadas, registrar cuenta bancaria |
| Identidad rechazada   | Volver a subir documentos |
| Suspendida            | Nada; no inicia sesión y sus espacios se ocultan |

En el modelo de datos se reparten en `usuario.correo_verificado`,
`usuario.estado` (`ACTIVA`, `SUSPENDIDA`, `ELIMINADA`) y
`perfil_arrendador.estado_identidad` (`NO_INICIADA`, `EN_REVISION`,
`VERIFICADA`, `RECHAZADA`). Además de las decisiones del admin (HU-06), `VERIFICADA` → `EN_REVISION` cuando el
arrendador cambia su documento o su razón social en el perfil (HU-20, RN-02); en revisión no puede cambiarlos.

- **Espacio:** `BORRADOR` → `PUBLICADO` (requiere identidad verificada) ⇄
  `PAUSADO` (pausa el arrendador o el admin; reanuda solo el arrendador, con identidad verificada y al menos una foto);
  `PUBLICADO` o `PAUSADO` → `BAJA` (el arrendador o el admin, sin reservas por terminar); `BAJA` es final: no se
  reactiva ni se edita (HU-21); `PUBLICADO` o `PAUSADO` → `OCULTO` por moderación del admin (HU-27), final: no se
  edita, pausa, reanuda, da de baja ni restaura. Si el
  arrendador está suspendido, sus espacios no se muestran pero conservan su estado.
- **Reserva:** `PENDIENTE_PAGO` (retiene los bloques 15 minutos) → `CONFIRMADA`
  (pago aprobado) o `EXPIRADA` (sin pago a tiempo); `CONFIRMADA` → `CANCELADA`.
  Mientras no expira no se puede soltar: `PENDIENTE_PAGO` → `CANCELADA` no existe (HU-12 S7).
- **Pago:** `RECHAZADO` si la pasarela no aprueba (se puede reintentar mientras
  la reserva siga vigente, hasta 3 rechazos cada 30 minutos); `PAGADO` →
  `REEMBOLSADO` o `REEMBOLSO_FALLIDO` (el admin puede reintentarlo).
  `PAGADO_TARDIO` (aprobado con la reserva no vigente) y `MONTO_DISTINTO`
  (aprobado con otro monto o moneda) no confirman la reserva y se devuelven a
  mano (HU-13 D2, S3).
- **Liquidación:** `GENERADA` → `PAGADA` (con número de operación);
  `PENDIENTE_CUENTA` si el arrendador no registró cuenta bancaria.

**Simplificaciones:**
- Política de cancelación fija: usuario con 48 h o más → 100 %; con menos → 0 %;
  si cancela el arrendador → 100 %.
- El enlace del checkout vence 1 minuto antes que la reserva (HU-13 D3).
- La liquidación la genera el admin con un botón y la transferencia es manual.
- Los bloques libres se calculan al consultar: horario − bloqueos − reservas
  (`CalculoDisponibilidad`, HU-08); ventana de 8 semanas = hoy + 55 días (`VentanaReserva`).

## Patrones por historia

Las historias marcadas ★ se documentan a fondo en el informe; su `design.md`
debe aplicar el patrón y mostrarlo en el diagrama de clases y de secuencia.

| Historia     | Patrones                        | Dónde vive y cómo se aplica |
|--------------|---------------------------------|-----------------------------|
| HU-13 ★      | Adapter, Facade                 | `pagos`: interfaz `PasarelaPago` + adaptador del proveedor; la implementación (sandbox o simulada) se elige por configuración con inyección de dependencias; `CheckoutFacade` para reserva vigente → monto en servidor → checkout |
| HU-14 ★      | Iterator, DAO                   | `reservas`: recorrer reservas próximas y pasadas sin exponer la colección (`HistorialReservas` + `IteradorReservas` + `Pagina`); consultas siempre filtradas por el usuario de la sesión (`ReservaDAO` + `ReservaDAOJpa`, primer DAO explícito). Ver [Mis reservas](#mis-reservas-qué-deja-hu-14-hu-15-hu-21-hu-22-hu-24-hu-26) |
| HU-15 ★      | Strategy                        | `reservas`: interfaz `PoliticaReembolso` con una estrategia por caso; el servicio la elige sin `if` por tipo (`CalculoReembolso` es el Context). Ver [Cancelación](#cancelación-qué-deja-hu-15-hu-16-hu-21-hu-28) |
| HU-16 ★      | Command, Adapter                | `pagos`: `SolicitudReembolso` con `ejecutar()`, idempotente y reintentable; reutiliza el adaptador de HU-13 |
| HU-23 ★      | Decorator                       | `busqueda`: cada filtro envuelve a la búsqueda base de HU-10 y se combinan libremente. Component `BusquedaEspacios`, ConcreteComponent `BusquedaBase`, Decorator `FiltroBusqueda`, ConcreteDecorators `FiltroPrecioMaximo`, `FiltroCapacidadMinima`, `FiltroCaracteristicas`; `FiltrosBusqueda.envolver` arma la cadena por petición y `BusquedaService` es el Client. Ver [Filtros](#filtros-qué-deja-hu-23--decorator) |
| HU-24 ★      | Decorator, DAO                  | `busqueda`: publicar una reseña es una cadena de decoradores armada una vez en `ConfiguracionPublicacionResena.cadena` (Component `PublicacionResena`, ConcreteComponent `PublicacionResenaBase`, Decorator `PublicacionResenaDecorador`, ConcreteDecorators `PublicacionSanitizada` → `PublicacionTransaccional` → `PublicacionAuditada`; Client `ResenaService.crear`). La tabla `resena` solo se toca por `ResenaDAO` + `ResenaDAOJpa` (BusinessObjects `ResenaService`, `PublicacionResenaBase`, `DatosPersonalesResenasImpl`, `ModeracionResenasImpl`; DataSource `ResenaRepository`; TransferObjects `Resena`, `TrozoResenas`, `ResumenCalificaciones`). Ver [Reseñas](#reseñas-qué-deja-hu-24-hu-26-hu-27) |
| HU-25 ★      | Factory Method, Adapter         | `comun/correo`: crear el correo según el evento (Creator `FabricaCorreo<E>` con el método fábrica `crearCorreo`, Product `Correo`; ConcreteCreators y ConcreteProducts en el módulo del evento: `FabricaRecordatorio{Usuario,Arrendador}` en `reservas` y 13 `FabricaCorreo*` en `cuentas`; Clients `RecordatoriosReserva` y `NotificadorCorreoImpl`) y adaptador del proveedor de correo (Target `EnvioCorreo`, Adapter `EnvioCorreoSmtp` sobre el Adaptee `JavaMailSender`, `EnvioCorreoSimulado`; se elige con `espacioya.correo.proveedor`). Ver [Correo](#correo-qué-deja-hu-25--factory-method-adapter) |
| HU-29 ★      | Adapter                         | `comun/almacenamiento`: Target `AlmacenamientoArchivos`, Adapters `AlmacenamientoLocal` (disco) y `AlmacenamientoS3` (Adaptee `S3Client` del AWS SDK v2); se elige con `espacioya.almacenamiento.tipo`; Clients `ArchivosPrivados` y `ArchivosPublicos`. Ver [Almacenamiento](#almacenamiento-qué-deja-hu-29--adapter) |
| Todas        | DAO / Repository                | Un repositorio por agregado |

## Qué NO hacer

- No llamar a un repositorio desde un controlador, ni al repositorio de otro módulo.
- No devolver entidades JPA en la API.
- No calcular montos, roles ni estados en el frontend, ni aceptarlos del cliente.
- No tomar el ID del usuario del cuerpo o la URL cuando debe salir de la sesión.
- No usar el SDK de la pasarela, del correo o del almacenamiento fuera de su adaptador.
- No mostrar detalles técnicos en los errores al cliente; el detalle va a los logs.
- No guardar archivos en la base de datos ni servir documentos con URL pública.
  Excepción decidida por el dueño de HU-07: las fotos de espacios no son
  sensibles y se sirven en `/api/fotos/<UUID>`, públicas si el espacio está
  `PUBLICADO` y, si no, solo a su dueño.
- No introducir microservicios, colas de mensajes ni nuevos módulos sin
  discutirlo antes (estado `blocked`).
- No implementar nada de lo que está **fuera del alcance**, aunque parezca útil:

  | Qué | Decisión |
  |-----|----------|
  | Galería de más de 5 fotos y video | Descartada |
  | Precios por franja horaria (se usa precio único por hora) | Descartada |
  | Mapa interactivo | Descartada |
  | Espacios favoritos | Descartada |
  | Reprogramar una reserva (se cancela y se reserva de nuevo) | Descartada |
  | Panel de ingresos y ocupación | Descartada |
  | Destacar publicación (pago por visibilidad) | Descartada |
  | Billetera digital, transferencias y cuotas | Descartada |
  | Notificaciones push | Descartada |
  | Política de cancelación configurable | Simplificada: fija de 48 h |
  | Liquidación automática (job y transferencia por API) | Simplificada: botón del admin y transferencia manual |
  | Reembolso automático de pagos que llegan tras expirar la reserva | Simplificada: el pago se registra como `PAGADO_TARDIO` y su devolución es manual (HU-13 D2) |
  | Respuesta del arrendador a reseñas y fotos en reclamos | Simplificada: fuera de HU-24 y HU-26 |
