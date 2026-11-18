# HU-24 — Design: Reseñas y calificaciones

> Requirements: `specs/HU-24/requirements.md` (R1–R58, decisiones P1–P22). Reutiliza HU-11 (ficha pública, cupo
> `catalogo`), HU-14 (`ReservaDAO`, `ReservaConHorario`) y HU-22 (`ParticipanteDatosPersonales`). Informe del código:
> `progress/explore_HU-24.md`.
>
> **Reapertura ★ (2026-11-13): Decorator y DAO.** HU-24 ya estaba implementada (`progress/impl_HU-24.md`,
> `progress/review_HU-24.md`). Esta revisión **refactoriza** su backend para aplicar los dos patrones del plan
> (§ 12) sin cambiar el contrato HTTP, la base de datos ni el frontend (R58). Lo nuevo de la reapertura está en
> DT-20 a DT-27, § 2 "Reapertura", § 3 (secuencias actualizadas), § 6, § 12, A9–A14, § 14 y § 16. Las tareas
> nuevas son T30–T40 de `tasks.md`.

## 1. Decisiones técnicas

| Id | Decisión | Por qué |
|----|----------|---------|
| DT-1 | La tabla, la entidad y el servicio viven en `busqueda`, que estrena sus capas `domain` y `repository`. | `architecture.md`, principio 4, y `feature_list.json`. |
| DT-2 | `busqueda` consulta las reservas con una **interfaz pública nueva y angosta** de `reservas`, `reservas.service.ReservasParaResena`. **No es un puerto**: la dirección `busqueda → reservas` ya está permitida, así que no hace falta inversión de dependencias. | `busqueda` no puede leer `ReservaRepository` (principio 5). `ReservaService.consultar` no da el fin ni lista por usuario, y agregarle métodos obliga a inyectar `ReservaDAO` en `ReservaServiceImpl` y tocar sus pruebas (alternativa A2). |
| DT-3 | `ReservasParaResenaImpl` lee siempre con `ReservaDAO.delUsuario(usuarioId)` y filtra en memoria, también para buscar una reserva por id. | El DAO exige el dueño en la firma: una reserva ajena simplemente no aparece (R9, STRIDE E/I). HU-14 ya aceptó clasificar en memoria decenas de reservas por persona. |
| DT-4 | La regla es **pura**: `busqueda.domain.ReglasResena.motivoNoResenable(confirmada, fin, ahora)`, con `PLAZO = 14 días`. | Bordes de R5, R7 y R8 probados con unitarias, sin reloj real. Mismo estilo que `ReglasLiquidacion`. |
| DT-5 | La referencia temporal es `ReservaConHorario.instanteDeFin` (P1). Intervalo semiabierto: `fin ≤ ahora < fin + 14 días`. Lima no tiene horario de verano, así que 14 × 24 h y 14 días de calendario coinciden. | Mismo fin que HU-19 y HU-26, y mismo criterio de borde: no hay dos definiciones de "ya pasó". |
| DT-6 | La creación **no** lleva `@Transactional`: usa `TransactionTemplate` y traduce la violación de `uq_resena_reserva`, reconocida por su nombre, a *ya reseñada*. **Reapertura:** ese código pasa sin cambios de `ResenaService.crear` al ConcreteDecorator `PublicacionTransaccional` (DT-21). | Patrón de `ReservaServiceImpl.crear` (HU-12 DT-7). Con `@Transactional` la violación llegaría al confirmar y no se podría traducir. Otra violación se relanza. `ResenaDAO.existeParaReserva` da el camino normal; el índice, la defensa ante la carrera (R14). |
| DT-7 | Sin bloqueos. | Una `CONFIRMADA` terminada ya no cambia de estado: `motivoNoCancelable` responde `YA_INICIADA` y el webhook solo confirma reservas vigentes. La doble reseña la frena el índice único. |
| DT-8 | Sanitización **al guardar**, en una utilidad nueva y pura, `comun.texto.SanitizadorTexto.limpiar(String)`. Pasos: `\r\n` y `\r` → `\n`; quitar `\p{Cc}` salvo `\n` y `\t`; quitar `<[^<>]*>`; quitar todo `<` y `>` restante; `strip()`. | P4. El último paso garantiza que no quede ninguna etiqueta, ni con `<scr<script>ipt>`. Quitar `\p{Cc}` además evita el 500 de PostgreSQL con `U+0000`. Las expresiones no retroceden: costo lineal. Va en `comun` porque el principio 9 pide construir lo transversal una vez y HU-26 puede reutilizarla. Ver alternativas A3 y A4. |
| DT-9 | `GET /api/resenas/pendientes` dice qué reservas se pueden reseñar; `ReservaDTO` no cambia (P5). La pantalla solo pregunta si el id está en la lista. | Comparar un id no es una regla de negocio: la decide el servidor. Sin puerto ni cambios en `reservas` más allá de DT-2 y sin tocar el contrato de HU-12 y HU-14. Ver alternativa A1. |
| DT-10 | El resumen va en `FichaPublicaDTO` (`calificacionPromedio`, `totalResenas`) y las reseñas en su ruta paginada, `GET /api/espacios/{id}/resenas` (P6). | HU-11 DT-16 prevé campos nuevos en la ficha. Paginar por separado evita que la ficha crezca con las reseñas. `Slice` hace `LIMIT n + 1`: da `hayMas` sin contar (como HU-10, sin total por página). |
| DT-11 | El promedio se calcula con `count` y `sum` enteros y `BigDecimal.divide(…, 1, HALF_UP)` en `ResumenCalificaciones` (R28). | `avg` devuelve `double`: 4.25 o 4.35 pueden redondear mal. Dos enteros son exactos. |
| DT-12 | `calificacion` es `SMALLINT` y **`short`** en la entidad, con `getCalificacion()` que devuelve `int`. | `spring.jpa.hibernate.ddl-auto=validate`: un `int` contra `SMALLINT` impide arrancar. Mismo arreglo que `ReservaBloque.horaInicio`. |
| DT-13 | `comentario` es `NOT NULL`; al anonimizar se reemplaza por `Resena.COMENTARIO_ELIMINADO` (P11), con un `UPDATE` masivo por `usuario_id`. | Mismo criterio que "Cuenta eliminada" en HU-22. La columna conserva su `CHECK` y la pantalla no necesita un caso nulo. |
| DT-14 | `DatosPersonalesResenasImpl` implementa el puerto con `@Order(3)`. El doble `ParticipanteQueFalla` pasa a `@Order(4)`. | HU-22 DT-2 lo dejó pendiente. Un doble con el mismo orden que un bean real dejaría indefinido quién anonimiza primero. |
| DT-15 | Los DTO de entrada usan lectores estrictos propios (`CrearResenaDTO.SoloEnteroLargo`, `SoloEnteroCorto`, `SoloTexto`). | Como `CrearReservaDTO`: un `"4"` o un `4.5` se reportan en su campo y no como cuerpo ilegible (R15–R17). `busqueda` no importa DTO de `reservas`. |
| DT-16 | Orden de `crear`: `@Valid` (400) → rol (403) → sanitizar (400) → transacción: reserva propia (404) → *ya reseñada* (400) → regla (400) → `saveAndFlush` → auditoría. **Reapertura:** el orden no cambia; ahora lo fija la cadena: `@Valid` en el controlador, rol en `ResenaService` (Client), sanitizar en `PublicacionSanitizada`, transacción en `PublicacionTransaccional`, auditoría en `PublicacionAuditada` (al volver de su envuelta) y el resto en `PublicacionResenaBase`. | La validación del cuerpo no depende de la reserva: no filtra nada. Mismo orden que `POST /api/reservas`. *Ya reseñada* va antes que el plazo porque es la causa real (R12). |
| DT-17 | `busqueda` usa `cuentas` **solo** por el puerto de HU-22, no por `CuentaService` (P8, P15). | No se muestra el autor, así que no hace falta `nombres(ids)`. |
| DT-18 | `ResenaService` es una clase concreta. HU-27 creará la interfaz pública cuando `administracion` la necesite. | "Solo los servicios que usan otros módulos llevan interfaz" (YAGNI). |
| DT-19 | Las reseñas públicas exponen `fecha` (`LocalDate` de Lima), no `creado_en`. | Minimización: un día basta, y la pantalla reutiliza `formatoFecha`. El orden sigue usando `creado_en`. |

### Decisiones de la reapertura ★

| Id | Decisión | Por qué |
|----|----------|---------|
| DT-20 | **Decorator sobre la publicación de una reseña.** Component `PublicacionResena.publicar(SolicitudResena) → Resena`; ConcreteComponent `PublicacionResenaBase`; Decorator abstracto `PublicacionResenaDecorador`; ConcreteDecorators `PublicacionSanitizada`, `PublicacionTransaccional` y `PublicacionAuditada`; Client `ResenaService.crear`. Todos en `busqueda.service`, como HU-23. | El informe define Decorator como "agrega comportamiento combinable a un objeto sin modificarlo (filtros de búsqueda, sanitización y auditoría)". Hoy `crear` mezcla cuatro responsabilidades en un método; separadas, cada una se prueba sola con un doble de su envuelta y una nueva (p. ej. un límite por usuario) es una clase más sin tocar la base. |
| DT-21 | La **transacción es un ConcreteDecorator** (`PublicacionTransaccional`), entre la sanitización y la auditoría. | La sanitización debe quedar **fuera** (R22: un comentario vacío no abre transacción ni lee la reserva) y la auditoría **dentro** (R23, R51). Ni el ConcreteComponent ni el Client pueden cumplir las dos cosas (A9, A10). Al ser un objeto armado a mano y no un proxy de Spring, la transacción solo puede ser `TransactionTemplate`, que es justo lo que DT-6 exige. |
| DT-22 | La cadena se arma **una vez**, en `@Configuration ConfiguracionPublicacionResena`, con un único `@Bean PublicacionResena`. Las piezas no son beans; su método estático `cadena(...)` es el único lugar que conoce el orden y lo usan el bean y `ResenaServiceTest`. | Ninguna pieza depende de la petición (HU-23 sí: sus filtros son datos de la consulta). Un solo bean del tipo evita `@Primary` y ambigüedad de inyección (mismo cuidado que HU-23 § 6.5). |
| DT-23 | El Component devuelve la entidad `Resena`, no `ResenaCreadaDTO`. `ResenaService` la mapea al DTO como hoy. | `PublicacionAuditada` necesita `espacioId` para el detalle de R23 y el DTO no lo tiene. La entidad no sale del módulo ni a la API. |
| DT-24 | `SolicitudResena(usuarioId, reservaId, calificacion, comentario, ip)` es un `record` inmutable con `conComentario(String)`. | Un decorador no modifica lo que recibe: entrega a su envuelta una copia (R45). La sesión ya se validó en el Client, así que basta el id. |
| DT-25 | **DAO explícito** `busqueda.repository.ResenaDAO` + `ResenaDAOJpa` (`@Repository`, de paquete), como `ReservaDAO` de HU-14. `ResenaRepository` queda igual (sin renombrar métodos) y solo lo usa `ResenaDAOJpa`. Los métodos hablan el idioma del negocio y esconden Spring Data: no reciben `Pageable` ni devuelven `Slice`, `TotalesResena` o `JpaRepository`. | Hoy `ResenaService`, `DatosPersonalesResenasImpl` y `ModeracionResenasImpl` arman `PageRequest`, ordenan, pasan `EstadoResena.VISIBLE` y convierten proyecciones cada uno por su lado. El DAO fija esas reglas en un solo lugar: nadie puede pedir reseñas públicas sin el filtro `VISIBLE` (R35) ni olvidar el orden (R30). `ResenaRepositoryIT` sigue valiendo sin cambios. |
| DT-26 | El tope de offset de R34 (`(n − 1) × 10 > Integer.MAX_VALUE` → página vacía) pasa de `ResenaService.deEspacio` a `ResenaDAOJpa.visiblesDeEspacio` (R55). | Es una limitación de `PageRequest`, o sea, de la tecnología de acceso: es justo lo que el DAO esconde. |
| DT-27 | `DatosPersonalesResenasImpl` (HU-22, creado por HU-24) y `ModeracionResenasImpl` (HU-27) **migran al DAO** (P21). Sus interfaces públicas, transacciones (`MANDATORY`, `readOnly`) y comportamiento no cambian. | El DAO es la única puerta a `resena` (R53). Dejar a HU-27 en el repositorio dejaría dos caminos de acceso a la misma tabla y el bloqueo pesimista fuera del DAO (A13). |

## 2. Archivos

`M` = modificar, `N` = nuevo. Raíz backend: `2_Backend/src/main/java/pe/edu/ulima/espacioya/` (`J/`), pruebas
`2_Backend/src/test/java/pe/edu/ulima/espacioya/` (`T/`), recursos `2_Backend/src/main/resources/` (`R/`), frontend
`3_frontend/src/` (`F/`).

### `busqueda`

| Archivo | | Qué |
|---------|---|-----|
| `R/db/migration/V7_2__resena.sql` | N | Tabla, restricciones e índices (§ 7). |
| `J/busqueda/domain/Resena.java` | N | Entidad (§ 6). |
| `J/busqueda/domain/EstadoResena.java` | N | `VISIBLE`, `OCULTA`. |
| `J/busqueda/domain/MotivoNoResenable.java` | N | `NO_USADA`, `PLAZO_VENCIDO`. |
| `J/busqueda/domain/ReglasResena.java` | N | Regla pura (DT-4). |
| `J/busqueda/domain/ResumenCalificaciones.java` | N | Promedio y total (DT-11). |
| `J/busqueda/repository/ResenaRepository.java` | N | Consultas (§ 6). |
| `J/busqueda/repository/TotalesResena.java` | N | Proyección `cantidad`, `suma`. |
| `J/busqueda/dto/CrearResenaDTO.java` | N | Entrada con lectores estrictos. |
| `J/busqueda/dto/ResenaCreadaDTO.java` | N | Respuesta del `POST`. |
| `J/busqueda/dto/ResenasPendientesDTO.java` | N | `List<Long> reservaIds`. |
| `J/busqueda/dto/ResenaPublicaDTO.java` | N | Cuatro campos públicos (R31). |
| `J/busqueda/dto/PaginaResenasDTO.java` | N | `pagina`, `resenas`, `hayMas`. |
| `J/busqueda/dto/ResenaExportadaDTO.java` | N | Sección `resenas` de HU-22. |
| `J/busqueda/dto/FichaPublicaDTO.java` | M | `+ calificacionPromedio, totalResenas`; `de(ficha, resumen)`. |
| `J/busqueda/service/ResenaService.java` | N | Caso de uso, clase concreta (§ 6). |
| `J/busqueda/service/FichaService.java` | M | Recibe `ResenaService` y arma el resumen solo si el espacio es visible. |
| `J/busqueda/service/BusquedaService.java` | M | `pagina(String)` pasa de `private` a paquete, para reutilizar la regla. |
| `J/busqueda/service/impl/DatosPersonalesResenasImpl.java` | N | Puerto de HU-22 (§ 10). |
| `J/busqueda/controller/ResenaController.java` | N | Las tres rutas (§ 4). |
| `J/busqueda/controller/ReglasLimiteFicha.java` | M | `RUTA_RESENAS` y su regla del grupo `catalogo`. |

### `reservas` y `comun`

| Archivo | | Qué |
|---------|---|-----|
| `J/reservas/service/ReservasParaResena.java` | N | Interfaz pública (DT-2). |
| `J/reservas/service/ReservaParaResena.java` | N | `record` que cruza a `busqueda`. |
| `J/reservas/service/impl/ReservasParaResenaImpl.java` | N | Implementación con `ReservaDAO` (DT-3). |
| `J/comun/texto/SanitizadorTexto.java` | N | Utilidad pura (DT-8). |
| `J/comun/seguridad/ConfiguracionSeguridad.java` | M | `GET /api/espacios/*/resenas` en `permitAll`. |

### Frontend

| Archivo | | Qué |
|---------|---|-----|
| `F/api/busqueda.js` | M | `crearResena`, `listarResenasPendientes`, `listarResenasDeEspacio`. |
| `F/pages/reservas/ResenarReserva.jsx` + `.module.css` | N | Botón y `Modal` (no es del kit). |
| `F/pages/reservas/MisReservas.jsx` | M | Lee pendientes y agrega la acción en "Pasadas". |
| `F/pages/reservas/textosReservas.js` | M | Textos del modal. |
| `F/pages/busqueda/ResenasEspacio.jsx` + `.module.css` | N | Sección "Reseñas" (no es del kit). |
| `F/pages/busqueda/FichaEspacio.jsx` | M | Resumen y sección. |
| `F/pages/busqueda/textosFicha.js` | M | Textos de la sección. |

### Reapertura ★ (Decorator y DAO)

Solo backend y documentación. **Sin** migraciones, cambios de DTO, de controlador, de `comun` ni de frontend (R58).

| Archivo | | Qué |
|---------|---|-----|
| `J/busqueda/service/PublicacionResena.java` | N | **Component** (interfaz). |
| `J/busqueda/service/SolicitudResena.java` | N | `record` de entrada de la cadena (DT-24). |
| `J/busqueda/service/PublicacionResenaBase.java` | N | **ConcreteComponent**: reserva, unicidad, plazo, guardar (R52). |
| `J/busqueda/service/PublicacionResenaDecorador.java` | N | **Decorator** abstracto. |
| `J/busqueda/service/PublicacionSanitizada.java` | N | **ConcreteDecorator**: `SanitizadorTexto.limpiar` (R45, R46). |
| `J/busqueda/service/PublicacionTransaccional.java` | N | **ConcreteDecorator**: `TransactionTemplate` + traducción de `uq_resena_reserva` (R47, R48). |
| `J/busqueda/service/PublicacionAuditada.java` | N | **ConcreteDecorator**: `RESENA_CREADA` (R49, R50). |
| `J/busqueda/service/ConfiguracionPublicacionResena.java` | N | Ensamblador: `@Bean` y `cadena(...)` (R44). |
| `J/busqueda/service/ResenaService.java` | M | **Client**: `crear` = rol + `publicacion.publicar` + DTO; usa `ResenaDAO`; ya no recibe `AuditoriaService` ni `TransactionTemplate`. |
| `J/busqueda/repository/ResenaDAO.java` | N | **DAO** (interfaz, § 12.2). |
| `J/busqueda/repository/ResenaDAOJpa.java` | N | Implementación JPA, `@Repository` de paquete. |
| `J/busqueda/repository/TrozoResenas.java` | N | `record` TransferObject: `List<Resena> resenas`, `boolean hayMas`. |
| `J/busqueda/repository/ResenaRepository.java` | — | Sin cambios; Javadoc: "solo lo usa `ResenaDAOJpa`". |
| `J/busqueda/service/impl/DatosPersonalesResenasImpl.java` | M | Recibe `ResenaDAO` (DT-27). |
| `J/busqueda/service/impl/ModeracionResenasImpl.java` | M | Recibe `ResenaDAO` (DT-27). |
| `docs/informe/HU-24.md` | N | Capítulo 8 del informe (§ 12.4). |

## 3. Caso de uso y secuencias

```mermaid
flowchart LR
  U([Usuario]) --> A[Reseñar una reserva pasada]
  U --> B[Ver sus reservas por reseñar]
  V([Visitante]) --> C[Ver promedio y reseñas en la ficha]
  U --> C
  A -. incluye .-> D[Validar reserva real, plazo y unicidad]
  A -. incluye .-> E[Sanitizar el comentario]
  A -. incluye .-> F[Auditar la creación]
```

**Escenario "Reseñar una reserva pasada" (R1, R23, R44–R52, R57), con Decorator y DAO.** Es el diagrama
del § 8.1.1 del informe. Los flujos alternos `alt` son R12 (ya reseñada), R8 (fuera de plazo) y R14 (carrera).

```mermaid
sequenceDiagram
  actor U as Usuario
  participant C as ResenaController
  participant S as ResenaService<br/>(Client)
  participant SZ as PublicacionSanitizada<br/>(ConcreteDecorator)
  participant TX as PublicacionTransaccional<br/>(ConcreteDecorator)
  participant AU as PublicacionAuditada<br/>(ConcreteDecorator)
  participant B as PublicacionResenaBase<br/>(ConcreteComponent)
  participant R as ReservasParaResena
  participant G as ReglasResena
  participant D as ResenaDAO<br/>(ResenaDAOJpa)
  participant RR as ResenaRepository
  participant A as AuditoriaService
  U->>C: POST /api/resenas {reservaId, calificacion, comentario}
  C->>C: @Valid, IpCliente.de
  C->>S: crear(usuario, datos, ip)
  S->>S: exigirRol(usuario)
  S->>SZ: publicar(SolicitudResena)
  SZ->>SZ: SanitizadorTexto.limpiar(comentario)
  SZ->>TX: publicar(solicitud.conComentario(limpio))
  TX->>TX: TransactionTemplate.execute
  TX->>AU: publicar(solicitud)
  AU->>B: publicar(solicitud)
  B->>R: delUsuario(usuarioId, reservaId)
  R-->>B: ReservaParaResena(id, espacioId, confirmada, finEn)
  B->>D: existeParaReserva(reservaId)
  D->>RR: existsByReservaId(reservaId)
  alt ya reseñada (R12)
    D-->>B: true
    B-->>U: ReglaNegocioException → 400 "Ya reseñaste esta reserva."
  else sin reseña previa
    D-->>B: false
    B->>G: motivoNoResenable(confirmada, finEn, ahora)
    alt fuera de plazo (R8)
      G-->>B: PLAZO_VENCIDO
      B-->>U: ReglaNegocioException → 400 "El plazo de 14 días…"
    else dentro del plazo
      G-->>B: vacío
      B->>D: guardar(Resena.nueva(...))
      D->>RR: saveAndFlush(resena)
      alt carrera: otra solicitud ya insertó (R14, R57)
        RR-->>D: DataIntegrityViolationException (uq_resena_reserva)
        D-->>TX: la excepción sube por AU sin auditar (R50)
        TX->>TX: rollback, reconoce la restricción por su nombre
        TX-->>U: ReglaNegocioException → 400 "Ya reseñaste esta reserva."
      else insertada
        RR-->>D: Resena con id
        D-->>B: Resena
        B-->>AU: Resena
        AU->>A: registrar(RESENA_CREADA)
        AU-->>TX: Resena
        TX->>TX: commit
        TX-->>SZ: Resena
        SZ-->>S: Resena
        S-->>C: ResenaCreadaDTO
        C-->>U: 201
      end
    end
  end
```

Los rechazos de la base (R12, R8, y también R6, R7 y el 404 de R9) atraviesan `PublicacionAuditada` sin registrar y
`PublicacionTransaccional` los revierte y los deja pasar: solo traduce la violación de `uq_resena_reserva`. El
`ManejadorGlobalErrores` los convierte en la respuesta HTTP.

**Flujo de error: comentario que queda vacío (R22, R46).** `PublicacionSanitizada` lanza antes de llamar a su
envuelta: no se abre transacción, no se lee la reserva y no hay auditoría.

```mermaid
sequenceDiagram
  actor U as Usuario
  participant S as ResenaService
  participant SZ as PublicacionSanitizada
  participant TX as PublicacionTransaccional
  S->>SZ: publicar(solicitud con "<script></script>")
  SZ->>SZ: SanitizadorTexto.limpiar → ""
  SZ-->>U: ReglaNegocioException("comentario") → 400
  Note over TX: nunca se invoca
```

## 4. Endpoints

| Método y ruta | Sesión | Entrada | Salida | Códigos |
|---------------|--------|---------|--------|---------|
| `POST /api/resenas` | `USUARIO` | `CrearResenaDTO` | `ResenaCreadaDTO` | 201; 400 (`reservaId`, `calificacion`, `comentario` o sin campo); 401; 403; 404 |
| `GET /api/resenas/pendientes` | `USUARIO` | — | `ResenasPendientesDTO` | 200; 401; 403 |
| `GET /api/espacios/{id}/resenas?pagina=n` | Pública, cupo `catalogo` | `id` y `pagina` como texto | `PaginaResenasDTO` | 200; 400 (`pagina`); 404; 429 |
| `GET /api/espacios/{id}` (HU-11) | Pública, cupo `catalogo` | — | `FichaPublicaDTO` con 2 campos más | Sin cambios |

Las dos rutas `/api/resenas` no se registran en `ConfiguracionSeguridad`: `anyRequest().authenticated()` ya las cubre.
Ninguna ruta lleva `usuarioId`: el usuario sale de `@AuthenticationPrincipal UsuarioAutenticado`.

## 5. DTO y mensajes

```java
public record CrearResenaDTO(
        @NotNull(message = M_RESERVA) @Positive(message = M_RESERVA)
        @JsonDeserialize(using = SoloEnteroLargo.class) Long reservaId,
        @NotNull(message = M_CALIFICACION) @Min(value = 1, message = M_CALIFICACION)
        @Max(value = 5, message = M_CALIFICACION)
        @JsonDeserialize(using = SoloEnteroCorto.class) Integer calificacion,
        @NotBlank(message = M_COMENTARIO) @Size(max = 500, message = M_COMENTARIO)
        @JsonDeserialize(using = SoloTexto.class) String comentario) { }

public record ResenaCreadaDTO(Long id, Long reservaId, int calificacion, String comentario, LocalDate fecha) { }
public record ResenasPendientesDTO(List<Long> reservaIds) { }
public record ResenaPublicaDTO(Long id, int calificacion, String comentario, LocalDate fecha) { }
public record PaginaResenasDTO(int pagina, List<ResenaPublicaDTO> resenas, boolean hayMas) { }
public record ResenaExportadaDTO(Long reservaId, String espacioNombre, int calificacion, String comentario,
                                 String estado, LocalDate fecha) { }
```

`FichaPublicaDTO` agrega al final `BigDecimal calificacionPromedio` y `long totalResenas`.

`SoloEnteroCorto` devuelve el valor solo si es un entero JSON que cabe en un `int`; un valor mayor se lee `null`.

| Mensaje | Constante | Texto |
|---------|-----------|-------|
| *reserva inválida* | `CrearResenaDTO.M_RESERVA` | Elige una reserva válida. |
| *calificación inválida* | `CrearResenaDTO.M_CALIFICACION` | Elige una calificación de 1 a 5. |
| *comentario inválido* | `CrearResenaDTO.M_COMENTARIO` | Escribe un comentario de hasta 500 caracteres. |
| *no reseñable* | `ResenaService.MENSAJE_NO_RESENABLE` | Solo puedes reseñar reservas confirmadas que ya pasaron. |
| *plazo vencido* | `ResenaService.MENSAJE_PLAZO_VENCIDO` | El plazo de 14 días para reseñar esta reserva ya terminó. |
| *ya reseñada* | `ResenaService.MENSAJE_YA_RESENADA` | Ya reseñaste esta reserva. |
| *comentario eliminado* | `Resena.COMENTARIO_ELIMINADO` | Comentario eliminado. |

## 6. Clases y firmas públicas

```java
// reservas.service — sin sesión ni rol: el llamador pasa el id de la sesión.
public record ReservaParaResena(Long id, Long espacioId, boolean confirmada, Instant finEn) { }

public interface ReservasParaResena {
    /** @throws RecursoNoEncontradoException si no es una reserva de ese usuario. */
    ReservaParaResena delUsuario(Long usuarioId, Long reservaId);

    /** Las de estado leído CONFIRMADA de ese usuario, cualquiera sea su fin. */
    List<ReservaParaResena> confirmadasDe(Long usuarioId);
}

// busqueda.domain
public final class ReglasResena {
    public static final Duration PLAZO = Duration.ofDays(14);

    /** Vacío si está dentro del plazo. No confirmada o sin terminar: NO_USADA; ahora ≥ fin + PLAZO: PLAZO_VENCIDO. */
    public static Optional<MotivoNoResenable> motivoNoResenable(boolean confirmada, Instant fin, Instant ahora);
}

public record ResumenCalificaciones(BigDecimal promedio, long total) {
    public static ResumenCalificaciones de(long cantidad, long suma);
}

@Entity
@Table(name = "resena")
public class Resena {
    public static final String COMENTARIO_ELIMINADO = "Comentario eliminado.";

    protected Resena() { }

    public static Resena nueva(Long reservaId, Long usuarioId, Long espacioId, int calificacion, String comentario,
                               Instant ahora);

    // Getters, sin setters. getCalificacion() devuelve int (DT-12).
}

// busqueda.repository — sin cambios en la reapertura; desde ella solo lo usa ResenaDAOJpa (R53).
public interface ResenaRepository extends JpaRepository<Resena, Long> {
    boolean existsByReservaId(Long reservaId);

    @Query("select r.reservaId from Resena r where r.usuarioId = :usuarioId")
    Set<Long> reservasResenadas(@Param("usuarioId") Long usuarioId);

    Slice<Resena> findByEspacioIdAndEstado(Long espacioId, EstadoResena estado, Pageable pagina);

    @Query("select count(r) as cantidad, coalesce(sum(r.calificacion), 0) as suma from Resena r "
            + "where r.espacioId = :espacioId and r.estado = :estado")
    TotalesResena totales(@Param("espacioId") Long espacioId, @Param("estado") EstadoResena estado);

    List<Resena> findByUsuarioIdOrderByCreadoEnAscIdAsc(Long usuarioId);

    @Modifying
    @Query("update Resena r set r.comentario = :comentario where r.usuarioId = :usuarioId")
    int reemplazarComentarios(@Param("usuarioId") Long usuarioId, @Param("comentario") String comentario);

    // HU-27 agregó findParaActualizar(id) con @Lock(PESSIMISTIC_WRITE).
}

// busqueda.repository — ★ DAO (reapertura, DT-25). No extiende Repository: Spring Data no lo toma.
public interface ResenaDAO {
    boolean existeParaReserva(Long reservaId);
    /** INSERT inmediato (saveAndFlush): la violación de uq_resena_reserva sale de aquí, dentro de la transacción (R57). */
    Resena guardar(Resena nueva);
    Set<Long> reservasResenadasPor(Long usuarioId);
    /** Página n (desde 1) de las VISIBLE del espacio, por creado_en e id descendentes (R54); vacía sin consultar si el offset no cabe en un int (R55). */
    TrozoResenas visiblesDeEspacio(Long espacioId, int pagina, int tamano);
    /** Solo VISIBLE (R56). */
    ResumenCalificaciones resumenVisibles(Long espacioId);
    /** HU-22: todas las del usuario, en cualquier estado, por creado_en e id ascendentes. */
    List<Resena> delUsuario(Long usuarioId);
    /** HU-22: reemplaza el comentario por Resena.COMENTARIO_ELIMINADO; devuelve cuántas cambió. */
    int anonimizarComentarios(Long usuarioId);
    /** HU-27. */
    Optional<Resena> buscar(Long id);
    List<Resena> buscarVarias(Collection<Long> ids);
    /** HU-27: con bloqueo PESSIMISTIC_WRITE; exige transacción activa. */
    Optional<Resena> buscarParaActualizar(Long id);
}

@Repository
class ResenaDAOJpa implements ResenaDAO {
    ResenaDAOJpa(ResenaRepository resenas);
}

public record TrozoResenas(List<Resena> resenas, boolean hayMas) {
    public static final TrozoResenas VACIO = new TrozoResenas(List.of(), false);
}

// busqueda.service — ★ Decorator (reapertura, DT-20 a DT-24)
public record SolicitudResena(Long usuarioId, Long reservaId, int calificacion, String comentario, String ip) {
    public SolicitudResena conComentario(String otro);
}

/** Component. */
public interface PublicacionResena {
    /** Crea la reseña y la devuelve con su id; lanza las excepciones de comun/error de R6–R22. */
    Resena publicar(SolicitudResena solicitud);
}

/** ConcreteComponent. Supone una transacción activa (la abre PublicacionTransaccional). */
public final class PublicacionResenaBase implements PublicacionResena {
    public PublicacionResenaBase(ResenaDAO resenas, ReservasParaResena reservas, Clock reloj);
}

/** Decorator. publicar reenvía a la envuelta; cada ConcreteDecorator lo redefine y llama a super.publicar. */
public abstract class PublicacionResenaDecorador implements PublicacionResena {
    protected PublicacionResenaDecorador(PublicacionResena envuelta);   // Objects.requireNonNull
    @Override public Resena publicar(SolicitudResena solicitud);        // return envuelta.publicar(solicitud)
    PublicacionResena envuelta();                                       // de paquete, para la prueba de R44
}

public final class PublicacionSanitizada extends PublicacionResenaDecorador {
    public PublicacionSanitizada(PublicacionResena envuelta);
}
public final class PublicacionTransaccional extends PublicacionResenaDecorador {
    public PublicacionTransaccional(PublicacionResena envuelta, TransactionTemplate transacciones);
}
public final class PublicacionAuditada extends PublicacionResenaDecorador {
    public PublicacionAuditada(PublicacionResena envuelta, AuditoriaService auditoria);
}

@Configuration
public class ConfiguracionPublicacionResena {
    @Bean
    PublicacionResena publicacionResena(ResenaDAO resenas, ReservasParaResena reservas, AuditoriaService auditoria,
                                        TransactionTemplate transacciones, Clock reloj);

    /** Único lugar que conoce el orden: Sanitizada( Transaccional( Auditada( Base ) ) ). */
    public static PublicacionResena cadena(ResenaDAO resenas, ReservasParaResena reservas, AuditoriaService auditoria,
                                           TransactionTemplate transacciones, Clock reloj);
}

// busqueda.service — Client
@Service
public class ResenaService {
    public static final String ACCION_RESENA_CREADA = "RESENA_CREADA";
    public static final int TAMANO_PAGINA = 10;

    // Reapertura: antes (ResenaRepository, ReservasParaResena, CatalogoEspaciosService, AuditoriaService,
    // TransactionTemplate, Clock).
    public ResenaService(PublicacionResena publicacion, ResenaDAO resenas, ReservasParaResena reservas,
                         CatalogoEspaciosService catalogo, Clock reloj);

    /** exigirRol → publicacion.publicar(new SolicitudResena(...)) → ResenaCreadaDTO. Sin @Transactional (DT-6). */
    public ResenaCreadaDTO crear(UsuarioAutenticado usuario, CrearResenaDTO datos, String ip);

    @Transactional(readOnly = true)
    public ResenasPendientesDTO pendientes(UsuarioAutenticado usuario);

    @Transactional(readOnly = true)
    public PaginaResenasDTO deEspacio(String espacioId, String pagina);

    @Transactional(readOnly = true)
    public ResumenCalificaciones resumen(Long espacioId);
}

// comun.texto
public final class SanitizadorTexto {
    public static String limpiar(String texto);
}
```

- `pendientes`: `confirmadasDe(usuarioId)`, quitar `ResenaDAO.reservasResenadasPor(usuarioId)`, conservar las que
  `motivoNoResenable` deja vacío con un solo `ahora`, ordenar por id.
- `deEspacio`: id con el criterio de `FichaService.idValido`, que pasa a paquete; `esVisible` (404, R33); después
  `pagina` (`BusquedaService.pagina`; 0 → 400, R34); `ResenaDAO.visiblesDeEspacio(id, n, TAMANO_PAGINA)`.
- `resumen`: `ResenaDAO.resumenVisibles(espacioId)`.
- `ResenaDAOJpa.visiblesDeEspacio`: `PageRequest.of(n - 1, tamano, Sort.by(DESC, "creadoEn", "id"))` y
  `findByEspacioIdAndEstado(id, VISIBLE, …)`; si `getOffset() > Integer.MAX_VALUE`, `TrozoResenas.VACIO` sin consultar
  (Spring Data rechaza ese offset, pero la página es válida: no tiene nada que mostrar). `resumenVisibles`:
  `ResumenCalificaciones.de(totales.getCantidad(), totales.getSuma())`. `guardar`: `saveAndFlush`.
- **Cadena (§ 12.1):** `PublicacionSanitizada.publicar` = `limpiar`; vacío → `ReglaNegocioException("comentario",
  M_COMENTARIO)`; si no, `super.publicar(solicitud.conComentario(limpio))`. `PublicacionTransaccional.publicar` =
  `transacciones.execute(estado -> super.publicar(solicitud))` con el `catch` de DT-6 (constante
  `RESTRICCION_UNA_POR_RESERVA` y `esReservaYaResenada` se mudan de `ResenaService`). `PublicacionAuditada.publicar` =
  `var resena = super.publicar(solicitud); auditoria.registrar(evento con el detalle de R23); return resena;`.
  `PublicacionResenaBase.publicar` = el actual `crearEnTransaccion` sin la auditoría. Los mensajes siguen siendo
  constantes de `ResenaService` (§ 5), que los decoradores leen.
- `ReservasParaResenaImpl` es `@Transactional(readOnly = true)`, con `estadoEn(ahora) == CONFIRMADA` y
  `instanteDeFin()`.

## 7. Migración

`V7_2__resena.sql`. HU-25 también es del sprint 7: antes de crearla, confirmar en `develop` que `V7_2` sigue libre.

```sql
-- HU-24: una reseña por reserva. estado lo usa HU-27; HU-24 solo crea VISIBLE.
CREATE TABLE resena (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reserva_id   BIGINT       NOT NULL,
    usuario_id   BIGINT       NOT NULL,
    espacio_id   BIGINT       NOT NULL,
    calificacion SMALLINT     NOT NULL,
    comentario   VARCHAR(500) NOT NULL,
    estado       VARCHAR(10)  NOT NULL,
    creado_en    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_resena_reserva UNIQUE (reserva_id),
    CONSTRAINT fk_resena_reserva FOREIGN KEY (reserva_id) REFERENCES reserva (id),
    CONSTRAINT fk_resena_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT fk_resena_espacio FOREIGN KEY (espacio_id) REFERENCES espacio (id),
    CONSTRAINT ck_resena_calificacion CHECK (calificacion BETWEEN 1 AND 5),
    CONSTRAINT ck_resena_estado CHECK (estado IN ('VISIBLE', 'OCULTA')),
    CONSTRAINT ck_resena_comentario CHECK (char_length(btrim(comentario)) >= 1)
);
-- La ficha pagina y promedia solo las visibles.
CREATE INDEX ix_resena_espacio_visible ON resena (espacio_id, creado_en DESC, id DESC) WHERE estado = 'VISIBLE';
-- Pendientes, exportación y anonimización de HU-22.
CREATE INDEX ix_resena_usuario ON resena (usuario_id);
```

## 8. Excepciones

Sin excepciones nuevas. Todas son de `comun/error/`:

| Excepción | Caso |
|-----------|------|
| `MethodArgumentNotValidException` | R15, R16, R17 |
| `ReglaNegocioException(campo, mensaje)` | R22, R34 |
| `ReglaNegocioException(mensaje)` | R6, R7, R8, R12, R14 |
| `AccesoDenegadoException` | R10, R26 |
| `RecursoNoEncontradoException` | R9, R33 |
| `DemasiadosIntentosException` (la lanza el filtro) | R38 |

## 9. Componentes de `comun` reutilizados

- **Auditoría:** `AuditoriaService.registrar(EventoAuditoria)`, dentro de la transacción. IP con `IpCliente.de(peticion)`.
  Reapertura: la llama `PublicacionAuditada`, que está dentro de `PublicacionTransaccional` (R51).
- **Sanitización:** `comun.texto.SanitizadorTexto` **no cambia** (firma, pasos ni pruebas). Reapertura: dentro de HU-24
  la llama `PublicacionSanitizada`; HU-26 (`ReclamoService`, `ReclamoAdminService`) y HU-27 (`ReporteAdminService`)
  la siguen llamando directo.
- **Rate limiting:** `LimiteCatalogo.regla("GET", RUTA_RESENAS)`, en `ReglasLimiteFicha`. El límite no se escribe a mano.
- **Hora:** `Clock` y `ZonaHoraria.LIMA`.
- **Errores:** `ManejadorGlobalErrores`. HU-24 no agrega manejadores.
- **Correo:** ninguno. La historia no pide avisos.
- **Nuevo:** `comun.texto.SanitizadorTexto` (DT-8), con su fila en `docs/requerimientos.md` § 8.

## 10. Datos personales (HU-22)

`DatosPersonalesResenasImpl`, `@Component`, `@Order(3)`, en `busqueda/service/impl/`:

| Método | Comportamiento |
|--------|----------------|
| `exportar` | `@Transactional(readOnly = true)`. Solo con `titular.esUsuario()`: `SeccionDatos("resenas", lista)`, también vacía, con `ResenaDAO.delUsuario` (reapertura); `espacioNombre` con `CatalogoEspaciosService.nombres(ids)`, sin filtro de visibilidad. Otro rol: `List.of()`. |
| `impedimentos` | `MANDATORY`. Siempre `List.of()`. |
| `anonimizar` | `MANDATORY`. `ResenaDAO.anonimizarComentarios(titular.usuarioId())` (reapertura; antes `reemplazarComentarios(…, Resena.COMENTARIO_ELIMINADO)`). No lleva auditoría propia: `CUENTA_ELIMINADA` la cubre. |

`usuario_id` se conserva: la fila de `usuario` queda anonimizada y la FK sigue válida. No hay archivos que borrar.

## 11. Pantallas

No agrega componentes al kit, estados a `EtiquetaEstado` ni colores. La calificación se elige con un `Campo tipo="select"`
y se muestra como texto ("4.3 de 5"): no hay estrellas. `VISIBLE` no se muestra, porque la ficha solo recibe reseñas
visibles. Solo tokens en los CSS Modules. Ninguna pantalla usa `dangerouslySetInnerHTML`: React escapa el comentario.

### `/mis-reservas` — `pages/reservas/MisReservas.jsx` (CA1, CA2, CA3)

- **Kit:** `Boton`, `Modal`, `Campo`, `Alerta`, `Tarjeta`. Nuevo fuera del kit: `pages/reservas/ResenarReserva.jsx`.
- **API:** `listarResenasPendientes()` → `GET /api/resenas/pendientes`; `crearResena({ reservaId, calificacion,
  comentario })` → `POST /api/resenas`, en `api/busqueda.js`.
- **Comportamiento:** al montarse y con cada `recarga`, la pantalla pide los pendientes y guarda un `Set` de ids. En
  "Pasadas", `tarjetaPasada(reserva)` pasa `ResenarReserva` como acción de `tarjetaDeReserva` solo si el id está en el
  `Set`. Si la consulta falla, la lista funciona igual, sin el botón; el servidor sigue validando al crear.
- **CA1:** `Boton secundario` "Calificar" abre `Modal` `primario` "Califica tu reserva" con `Campo select`
  "Calificación" (5 a 1, "5 - Excelente", "4 - Muy bueno", "3 - Regular", "2 - Malo", "1 - Muy malo") y `Campo textarea`
  "Comentario", con la ayuda "Hasta 500 caracteres.". "Publicar reseña" llama a `crearResena` con `cargando`. Con 201:
  se cierra el modal, `Alerta exito` "Tu reseña ya está publicada." y `recarga` sube, así que el botón desaparece.
- **CA2:** el botón no aparece en reservas no reseñables. Si la API igual rechaza (400 o 404), se cierra el modal y
  se muestra `Alerta error` con el mensaje tal cual bajo el botón.
- **CA3:** después de reseñar, la reserva sale de los pendientes. Si otro envío se adelantó, llega *ya reseñada* como
  `Alerta error`.
- **Errores por campo:** `calificacion` y `comentario` quedan dentro del modal, bajo su `Campo`. Un `select` vacío se
  envía como `null` y responde el servidor: la pantalla no valida reglas.

### `/espacios/:id` — `pages/busqueda/FichaEspacio.jsx` (CA1)

- **Kit:** `Tarjeta`, `Boton`, `Alerta`. Nuevo fuera del kit: `pages/busqueda/ResenasEspacio.jsx`, con
  `key={espacio.id}`.
- **API:** `obtenerEspacio` (con los dos campos nuevos) y `listarResenasDeEspacio(id, pagina)` → `GET
  /api/espacios/{id}/resenas`, con `autenticada: false`.
- **Resumen:** debajo de la descripción, en la columna de datos: "`4.3` de 5 · `12` reseñas" ("1 reseña" en singular),
  o "Aún sin reseñas." si `totalResenas` es 0. El promedio lo trae la API.
- **Sección "Reseñas":** al final de la pantalla, después de "Reservar", para no separar los bloques de su botón. Cada
  reseña es una `Tarjeta` con título "`n` de 5", el comentario (`white-space: pre-line`) y la fecha en
  `--color-texto-secundario` (`formatoFecha`). "Ver más" es un `Boton secundario` mientras `hayMas` y no repite ids,
  como `SeccionReservas`. Lista vacía: "Este espacio aún no tiene reseñas.". Error: `Alerta error` con el mensaje. El
  único primario sigue siendo "Reservar".

## 12. ★ Patrones: Decorator y DAO

`feature_list.json`: `patterns: ["Decorator", "DAO"]` (reapertura). HU-24 sigue reutilizando sin cambios el DAO de
HU-14 (`ReservaDAO`, a través de `ReservasParaResenaImpl`). Puerto que implementa: `cuentas.ParticipanteDatosPersonales`.
**No crea puertos nuevos** (DT-2): todas las clases nuevas viven en `busqueda` y no cruzan módulos.

### 12.1 Decorator (GoF, estructural)

**Problema.** Publicar una reseña exige cuatro cosas de naturaleza distinta: limpiar el texto (seguridad, STRIDE T),
abrir y cerrar la transacción traduciendo la carrera del índice (persistencia, DT-6), dejar rastro (no repudio, STRIDE
R) y la regla del negocio (reserva propia, una por reserva, plazo). Antes de la reapertura vivían juntas en
`ResenaService.crear` y `crearEnTransaccion`. Decorator agrega responsabilidades a un objeto **envolviéndolo con otros
de la misma interfaz**, sin modificarlo: la regla del negocio queda sola en el ConcreteComponent y cada comportamiento
transversal se suma o se quita cambiando una línea del ensamblador.

| Rol GoF | Clase | Responsabilidad |
|---------|-------|-----------------|
| **Component** | `busqueda.service.PublicacionResena` | `Resena publicar(SolicitudResena)`. |
| **ConcreteComponent** | `busqueda.service.PublicacionResenaBase` | Reserva propia (404), *ya reseñada*, `ReglasResena` y `ResenaDAO.guardar` (R52). Nada de texto, transacción ni auditoría. |
| **Decorator** | `busqueda.service.PublicacionResenaDecorador` (abstracta) | Guarda la `envuelta` (también `PublicacionResena`) y le reenvía `publicar`. |
| **ConcreteDecorator** | `PublicacionSanitizada` | **Antes** de reenviar: `SanitizadorTexto.limpiar`; vacío → 400 sin reenviar (R45, R46). |
| **ConcreteDecorator** | `PublicacionTransaccional` | **Alrededor**: `TransactionTemplate.execute` y traducción de `uq_resena_reserva` (R47, R48). |
| **ConcreteDecorator** | `PublicacionAuditada` | **Después** de que la envuelta vuelve: `RESENA_CREADA`; si lanza, nada (R49, R50). |
| (ensamblador) | `ConfiguracionPublicacionResena.cadena(...)` | Único lugar con el orden; lo publica como el único `@Bean PublicacionResena` (R44). |
| **Client** | `ResenaService.crear` | Comprueba el rol y solo conoce `PublicacionResena`. |
| (participante externo) | `SanitizadorTexto`, `AuditoriaService`, `TransactionTemplate` | Lo que cada ConcreteDecorator usa; ninguno cambia. |

```mermaid
classDiagram
    direction LR
    class PublicacionResena {
        <<interface>>
        +publicar(SolicitudResena) Resena
    }
    class PublicacionResenaBase {
        -ResenaDAO resenas
        -ReservasParaResena reservas
        -Clock reloj
        +publicar(SolicitudResena) Resena
    }
    class PublicacionResenaDecorador {
        <<abstract>>
        -PublicacionResena envuelta
        +publicar(SolicitudResena) Resena
    }
    class PublicacionSanitizada {
        +publicar(SolicitudResena) Resena
    }
    class PublicacionTransaccional {
        -TransactionTemplate transacciones
        +publicar(SolicitudResena) Resena
    }
    class PublicacionAuditada {
        -AuditoriaService auditoria
        +publicar(SolicitudResena) Resena
    }
    class ConfiguracionPublicacionResena {
        +cadena(...)$ PublicacionResena
    }
    class ResenaService {
        -PublicacionResena publicacion
        +crear(UsuarioAutenticado, CrearResenaDTO, String) ResenaCreadaDTO
    }
    class SolicitudResena {
        <<record>>
        +Long usuarioId
        +Long reservaId
        +int calificacion
        +String comentario
        +String ip
        +conComentario(String) SolicitudResena
    }

    PublicacionResena <|.. PublicacionResenaBase : ConcreteComponent
    PublicacionResena <|.. PublicacionResenaDecorador : Decorator
    PublicacionResenaDecorador o--> "1" PublicacionResena : envuelta
    PublicacionResenaDecorador <|-- PublicacionSanitizada
    PublicacionResenaDecorador <|-- PublicacionTransaccional
    PublicacionResenaDecorador <|-- PublicacionAuditada
    ConfiguracionPublicacionResena ..> PublicacionSanitizada : crea
    ConfiguracionPublicacionResena ..> PublicacionTransaccional : crea
    ConfiguracionPublicacionResena ..> PublicacionAuditada : crea
    ConfiguracionPublicacionResena ..> PublicacionResenaBase : crea
    ResenaService --> PublicacionResena : Client
    PublicacionResena ..> SolicitudResena
```

**Composición (de afuera hacia adentro) y por qué en ese orden:**

```java
// ConfiguracionPublicacionResena.cadena
return new PublicacionSanitizada(                       // 1. fuera de la transacción: R22 no abre ni lee nada
        new PublicacionTransaccional(                   // 2. abre la transacción y traduce la carrera (DT-6)
                new PublicacionAuditada(                // 3. dentro de la transacción: R23 y R51
                        new PublicacionResenaBase(resenas, reservas, reloj), auditoria),
                transacciones));
```

| Cambio de orden | Qué rompería |
|-----------------|--------------|
| Sanitizada dentro de Transaccional | Un comentario vacío abriría y revertiría una transacción por nada, y la sanitización dejaría de ir antes de todo acceso a datos (orden de DT-16). |
| Auditada fuera de Transaccional | La auditoría quedaría en otra transacción: una reseña podría guardarse sin rastro (R51) y una carrera auditaría una reseña que no existe. |
| Sin Transaccional | `saveAndFlush` sin transacción: la base y la auditoría no se revierten juntas y la violación no se traduce (R14). |

A diferencia de HU-23, aquí el orden **sí importa** (los decoradores no conmutan), por eso hay un solo ensamblador y
`ConfiguracionPublicacionResenaTest` fija la cadena completa (R44). Un comportamiento nuevo es una subclase de
`PublicacionResenaDecorador` más una línea en `cadena`; ni la base ni los otros decoradores cambian.

**Por qué no se rompe nada:** el controlador sigue llamando a `ResenaService.crear` con la misma firma; las
excepciones, los mensajes y el orden de validación son los mismos (DT-16); hay un solo bean de tipo `PublicacionResena`
y ninguna pieza es bean por separado.

### 12.2 DAO (Core J2EE Patterns, acceso a datos)

**Problema.** Tres clases de `busqueda` usaban `ResenaRepository` directamente y cada una repetía detalles de Spring
Data: `PageRequest` con su orden, el tope de offset, `EstadoResena.VISIBLE` como argumento, la proyección
`TotalesResena` y el texto de anonimización. DAO separa la lógica de negocio del acceso a datos con una interfaz en el
idioma del dominio; la tecnología (JPA, Spring Data, JPQL) queda en una sola implementación. Mismo estilo que
`ReservaDAO` de HU-14: interfaz pública, implementación `@Repository` de paquete, sin extender `Repository`.

| Rol (DAO) | Clase | Responsabilidad |
|-----------|-------|-----------------|
| **DataAccessObject** (interfaz) | `busqueda.repository.ResenaDAO` | Operaciones sobre `resena` en idioma del negocio; nada de `Pageable`, `Slice` ni proyecciones. |
| **DataAccessObject** (implementación) | `busqueda.repository.ResenaDAOJpa` | Traduce cada operación a `ResenaRepository`: orden, filtro `VISIBLE`, tope de offset, `saveAndFlush`, bloqueo. |
| **DataSource** | `ResenaRepository` (Spring Data JPA) sobre PostgreSQL | Consultas derivadas y JPQL; sin cambios. |
| **TransferObject** | `Resena`, `TrozoResenas`, `ResumenCalificaciones` | Lo que cruza la frontera del DAO. |
| **BusinessObject** | `ResenaService`, `PublicacionResenaBase`, `DatosPersonalesResenasImpl`, `ModeracionResenasImpl` | Solo conocen `ResenaDAO` (R53). |

```mermaid
classDiagram
    direction LR
    class ResenaDAO {
        <<interface>>
        +existeParaReserva(Long) boolean
        +guardar(Resena) Resena
        +reservasResenadasPor(Long) Set~Long~
        +visiblesDeEspacio(Long, int, int) TrozoResenas
        +resumenVisibles(Long) ResumenCalificaciones
        +delUsuario(Long) List~Resena~
        +anonimizarComentarios(Long) int
        +buscar(Long) Optional~Resena~
        +buscarVarias(Collection~Long~) List~Resena~
        +buscarParaActualizar(Long) Optional~Resena~
    }
    class ResenaDAOJpa {
        -ResenaRepository resenas
    }
    class ResenaRepository {
        <<interface>>
        +existsByReservaId(Long) boolean
        +saveAndFlush(Resena) Resena
        +reservasResenadas(Long) Set~Long~
        +findByEspacioIdAndEstado(Long, EstadoResena, Pageable) Slice~Resena~
        +totales(Long, EstadoResena) TotalesResena
        +findByUsuarioIdOrderByCreadoEnAscIdAsc(Long) List~Resena~
        +reemplazarComentarios(Long, String) int
        +findParaActualizar(Long) Optional~Resena~
    }
    class TrozoResenas {
        <<record>>
        +List~Resena~ resenas
        +boolean hayMas
    }
    class ResumenCalificaciones {
        <<record>>
        +BigDecimal promedio
        +long total
    }
    class Resena {
        <<entity>>
    }
    class ResenaService
    class PublicacionResenaBase
    class DatosPersonalesResenasImpl
    class ModeracionResenasImpl

    ResenaDAO <|.. ResenaDAOJpa
    ResenaDAOJpa --> ResenaRepository : DataSource
    ResenaDAOJpa ..> TrozoResenas : arma
    ResenaDAOJpa ..> ResumenCalificaciones : arma
    ResenaDAO ..> Resena
    ResenaService --> ResenaDAO : BusinessObject
    PublicacionResenaBase --> ResenaDAO : BusinessObject
    DatosPersonalesResenasImpl --> ResenaDAO : BusinessObject
    ModeracionResenasImpl --> ResenaDAO : BusinessObject
```

| Antes (repositorio) | Ahora (DAO) | Quién |
|---------------------|-------------|-------|
| `existsByReservaId` | `existeParaReserva` | `PublicacionResenaBase` |
| `saveAndFlush` | `guardar` | `PublicacionResenaBase` |
| `reservasResenadas` | `reservasResenadasPor` | `ResenaService.pendientes` |
| `PageRequest` + `findByEspacioIdAndEstado(…, VISIBLE, …)` + tope de offset | `visiblesDeEspacio(id, n, 10)` | `ResenaService.deEspacio` |
| `totales(…, VISIBLE)` + `ResumenCalificaciones.de` | `resumenVisibles` | `ResenaService.resumen` |
| `findByUsuarioIdOrderByCreadoEnAscIdAsc` | `delUsuario` | `DatosPersonalesResenasImpl.exportar` |
| `reemplazarComentarios(…, COMENTARIO_ELIMINADO)` | `anonimizarComentarios` | `DatosPersonalesResenasImpl.anonimizar` |
| `findById` / `findAllById(Set.copyOf(ids))` / `findParaActualizar` | `buscar` / `buscarVarias` / `buscarParaActualizar` | `ModeracionResenasImpl` |

`ResenaDAOJpa` no abre transacciones: las ponen sus llamadores (`readOnly` en consultas, `PublicacionTransaccional` al
crear, `MANDATORY` en HU-22 y HU-27), igual que `ReservaDAOJpa`.

### 12.3 Cómo se combinan

`PublicacionResenaBase` es a la vez **ConcreteComponent** del Decorator y **BusinessObject** del DAO: la regla del
negocio no conoce ni la transacción ni la tecnología de persistencia. La transacción la pone un decorador y el acceso a
datos lo esconde el DAO, así que la base se prueba con dos dobles (`ResenaDAO`, `ReservasParaResena`) y un reloj fijo.

### 12.4 Documentación del capítulo 8 (`docs/informe/HU-24.md`)

El implementer crea el archivo (T38 de `tasks.md`), todo en Mermaid editable y con código copiado del repo tal como
quede tras la refactorización (no del spec):

| Sección del informe | Contenido |
|---------------------|-----------|
| § 7.7 | Diagrama de clases de Decorator y de DAO (los de § 12.1 y § 12.2) y, para cada uno, tabla "rol → clase". |
| § 8.1 | Fila: ID (P22, por defecto `ESC-24`), HU-24, "Reseñar una reserva pasada", actor Usuario, Release 2 / Sprint 7, justificación (por qué este escenario muestra los dos patrones: la cadena de decoradores y el DAO en el mismo flujo, con un control de cada STRIDE de la historia). |
| § 8.1.1 | Diagrama de secuencia UML del escenario (el de § 3 de este diseño): flujo principal y alternos *ya reseñada*, *fuera de plazo* y carrera por `uq_resena_reserva`, con una explicación breve de cada uno. |
| § 8.1.2 | Tabla de identificación del escenario; fragmentos de código real por clase y método (`ResenaController.crear`, `ResenaService.crear`, `PublicacionSanitizada.publicar`, `PublicacionTransaccional.publicar`, `PublicacionAuditada.publicar`, `PublicacionResenaBase.publicar`, `ConfiguracionPublicacionResena.cadena`, `ResenaDAOJpa.existeParaReserva` y `guardar`); tabla de correspondencia "mensaje del diagrama → clase.método"; explicación; referencia a archivos (rutas desde la raíz del repo). |

## 13. Alternativas descartadas

| Id | Alternativa | Por qué no |
|----|-------------|------------|
| A1 | Agregar `resenable` a `ReservaDTO`, con un puerto `reservas.ResenasDeReservas` implementado por `busqueda`. | Rompe 7 aserciones de los 11 campos, el campo no aplica a `crear` ni a `consultar`, crea la primera carpeta de puertos de `reservas` y suma una consulta a tres rutas. La ruta propia evita todo eso con una llamada más (P5). |
| A2 | Agregar métodos a `ReservaService`. | Obliga a inyectar `ReservaDAO` en un constructor de 9 parámetros y a tocar `ReservaServiceImplTest`, y mezcla dos usos en una interfaz. Una interfaz angosta sigue el precedente de `ReservasLiquidables`. |
| A3 | Escapar con `HtmlUtils.htmlEscape` al guardar. | Doble escape: `&lt;` visible en la ficha y en la exportación de HU-22, y un `'` o `&` normal también se deformaría. |
| A4 | Usar jsoup, o rechazar con 400 todo comentario con `<` o `>`. | jsoup agrega una dependencia sin discutir para un texto que nunca se muestra como HTML. Rechazar castiga un "5 > 3" inocente y la historia pide sanitizar, no rechazar. |
| A5 | Poner las reseñas dentro de `FichaPublicaDTO`, sin ruta aparte. | La ficha crece sin límite o esconde las antiguas mientras `totalResenas` dice otra cosa. |
| A6 | Guardar el promedio en `espacio`. | `espacios` tendría que escribir por cuenta de `busqueda`, y HU-27 tendría que recalcularlo al ocultar. El modelo tampoco lo prevé. |
| A7 | Borrar la reseña al eliminar la cuenta. | Cambiaría el promedio de un espacio por un hecho ajeno a él. El comentario, que es lo personal, igual desaparece (P11). |
| A8 | `POST /api/reservas/{id}/resena`. | Una ruta `/api/reservas` en `busqueda` confunde el módulo dueño. `POST /api/resenas` sigue a `POST /api/reservas`, que lleva `espacioId` en el cuerpo. |
| A9 | (Reapertura) La transacción dentro del ConcreteComponent, como sugería la primera idea: la base abre el `TransactionTemplate`, y Sanitizada y Auditada la envuelven. | `PublicacionAuditada` quedaría **fuera** de la transacción: si la auditoría falla, la reseña ya está confirmada sin rastro (rompe R23 y R51), y en la carrera no habría forma de que la auditoría se revierta junto con el `INSERT`. |
| A10 | (Reapertura) La transacción en el Client (`ResenaService.crear` abre el `TransactionTemplate` y adentro llama a la cadena). | La sanitización tendría que correr dentro de la transacción (orden de DT-16 roto) o quedar fuera de la cadena como una llamada suelta, y entonces ya no sería un decorador sino el código de antes. |
| A11 | (Reapertura) Armar la cadena por petición en `ResenaService`, como `FiltrosBusqueda.envolver` de HU-23. | En HU-23 los filtros son datos de la consulta; aquí todas las piezas son siempre las mismas y sin estado. Armarla en cada petición crea cuatro objetos por nada y obliga a inyectar en `ResenaService` todo lo que necesitan los decoradores. |
| A12 | (Reapertura) Decorar con AOP de Spring (`@Aspect` para auditoría y sanitización) o con `@Transactional`. | Es otro mecanismo (proxy dinámico), no el Decorator GoF que pide el informe; el orden de los aspectos depende de `@Order` y no se ve en el código, y `@Transactional` impide traducir la violación (DT-6). |
| A13 | (Reapertura) DAO solo para `ResenaService`; HU-22 y HU-27 siguen con `ResenaRepository`. | Dos caminos de acceso a `resena`: la regla `VISIBLE`, el orden y el texto de anonimización seguirían duplicados, y R53 no se podría afirmar. Queda como opción si el dueño no acepta P21. |
| A14 | (Reapertura) Que `ResenaDAO` extienda `JpaRepository` (DAO = repositorio de Spring Data con otro nombre). | Expondría `findAll`, `deleteAll`, `Pageable`, `Slice` y toda la API de Spring Data a los BusinessObjects: no escondería nada. Mismo criterio que `ReservaDAO`, que no extiende `Repository`. |

## 14. Efectos en pruebas existentes

| Prueba | Cambio |
|--------|--------|
| `T/busqueda/controller/FichaControllerIT.java:267` | `containsOnlyKeys` suma `calificacionPromedio` y `totalResenas`. |
| `T/busqueda/service/FichaServiceTest.java:57, :72` | Constructor con `ResenaService` y `FichaPublicaDTO` con los dos campos. |
| `T/cuentas/controller/DatosPersonalesControllerIT.java:116, :138` | El usuario exporta `reservas`, `pagos`, `resenas`; detalle `secciones=reservas,pagos,resenas`. Las líneas `:158` y `:162` (arrendador) no cambian. |
| `T/cuentas/controller/DatosPersonalesSoporte.java:132` | `ParticipanteQueFalla` pasa a `@Order(4)` (DT-14). |
| `T/cuentas/controller/DatosPersonalesSoporte.java:237` | `limpiar` borra `resena` antes que `reserva`. |

`ResultadoEspacioDTO`, `ReservaDTO` y `ReservaServiceImpl` no cambian. La FK no tiene cascada: toda IT que cree reseñas
las borra al limpiar. Las demás suites no crean reseñas y siguen igual.

### Reapertura ★: pruebas que se adaptan y pruebas nuevas

Ningún `@DisplayName` ni aserción de comportamiento cambia: solo los dobles y el constructor.

| Prueba | Cambio |
|--------|--------|
| `T/busqueda/service/ResenaServiceTest.java` | `@Mock ResenaDAO` en lugar de `ResenaRepository`. `preparar` y `fechaEnLima` arman `new ResenaService(ConfiguracionPublicacionResena.cadena(resenas, reservas, auditoria, new TransactionTemplate(mock(...)), reloj), resenas, reservas, catalogo, reloj)`: los tests de R1–R24 siguen probando la cadena real de punta a punta. `existsByReservaId` → `existeParaReserva`, `saveAndFlush` → `guardar`, `reservasResenadas` → `reservasResenadasPor`, `totales` → `resumenVisibles` (devuelve `ResumenCalificaciones`; el helper `totales(...)` se borra), `findByEspacioIdAndEstado(…, Pageable)` → `visiblesDeEspacio(ESPACIO_ID, n, 10)` con `TrozoResenas`. Las aserciones sobre `Pageable` y orden de `primeraPagina` y `paginaPedida` pasan a `ResenaDAOJpaTest`; `paginaEnorme` y `ultimaPaginaConsultable` también (R55): en el servicio quedan como "pasa `n` y 10 al DAO". |
| `T/busqueda/service/impl/DatosPersonalesResenasImplTest.java` | `@Mock ResenaDAO`; `findByUsuarioIdOrderByCreadoEnAscIdAsc` → `delUsuario`, `reemplazarComentarios(id, COMENTARIO_ELIMINADO)` → `anonimizarComentarios(id)`. |
| `T/busqueda/service/impl/ModeracionResenasImplTest.java` (HU-27) | `@Mock ResenaDAO`; `findById` → `buscar`, `findAllById` → `buscarVarias`, `findParaActualizar` → `buscarParaActualizar`. |
| `T/busqueda/repository/ResenaRepositoryIT.java` | Sin cambios. |
| `ResenaControllerIT`, `FichaControllerIT`, `FichaServiceTest`, `LimiteFichaIT`, `DatosPersonalesControllerIT`, ITs de HU-27, `SanitizadorTextoTest`, pruebas de HU-26 | Sin cambios: son la evidencia de R58. |

| Prueba nueva | Qué fija |
|--------------|----------|
| `T/busqueda/service/ConfiguracionPublicacionResenaTest` | La cadena y su orden recorriendo `envuelta()` (R44). |
| `T/busqueda/service/PublicacionSanitizadaTest` | Con un doble de la envuelta: recibe el comentario limpio y el resto igual; vacío no la invoca (R45, R46). |
| `T/busqueda/service/PublicacionTransaccionalTest` | Con `PlatformTransactionManager` simulado: `commit` si vuelve, `rollback` si lanza; traduce solo `uq_resena_reserva` (R47, R48). |
| `T/busqueda/service/PublicacionAuditadaTest` | Registra después y devuelve la misma; si la envuelta lanza, no registra y relanza la misma (R49, R50). |
| `T/busqueda/service/PublicacionResenaBaseTest` | Orden 404 → *ya reseñada* → plazo con `InOrder`; guarda un comentario con `<b>` tal cual (no sanitiza) (R52). |
| `T/busqueda/service/PublicacionResenaIT` | Contexto completo con `@MockitoSpyBean AuditoriaService` que lanza: no queda fila en `resena` (R51). |
| `T/busqueda/AccesoResenasTest` | Por reflexión: ningún campo de tipo `ResenaRepository` en las clases de R53. |
| `T/busqueda/repository/ResenaDAOJpaTest` | Unitaria con `ResenaRepository` simulado: `PageRequest` (página, tamaño, orden), `VISIBLE`, offset enorme sin consultar, el límite que aún consulta (R54, R55). |
| `T/busqueda/repository/ResenaDAOJpaIT` | PostgreSQL real: orden y `hayMas` con 11 reseñas, una `OCULTA` fuera (R54); resumen solo `VISIBLE` (R56); `guardar` duplicado lanza desde `guardar` dentro de `TransactionTemplate` (R57); `delUsuario`, `anonimizarComentarios` solo del usuario. |

## 15. Riesgos aceptados y lo que deja HU-24

- **Carrera con la eliminación de la cuenta (riesgo aceptado).** Una reseña que se confirma justo después del `UPDATE` de
  `anonimizar` conserva su comentario. Exige que la misma persona reseñe durante su eliminación, que revoca sus sesiones
  en la misma transacción.
- **Lectura en memoria (riesgo aceptado).** `pendientes` y `delUsuario` cargan todas las reservas del usuario, como
  HU-14.
- **HU-27** agrega `Resena.ocultar`, la interfaz pública de `busqueda` para `administracion` y lee `id` de la ficha para
  `reporte.objetivo_id`. No necesita cambiar las consultas: ya filtran `VISIBLE`.
- **HU-26** puede reutilizar `SanitizadorTexto`.

## 16. Documentos que actualiza el implementer

| Documento | Cambio |
|-----------|--------|
| `docs/modelo-datos.md` | `resena` con `creado_en`, tipos y FK; fila de restricciones con los nombres de § 7; anonimización en la fila "Cuenta `ELIMINADA`". |
| `docs/architecture.md` | Flecha `busqueda → cuentas` (solo puerto); fila de `ParticipanteDatosPersonales` con `busqueda` y `@Order(3)`; párrafo de `ReservasParaResena` junto a las interfaces públicas; `FichaPublicaDTO` y `ConfiguracionSeguridad`; sección "Reseñas: qué deja HU-24 (HU-26, HU-27)". |
| `docs/requerimientos.md` | § 8: fila de sanitización de texto. § 9 punto 9: el cupo `catalogo` incluye las reseñas de la ficha. |
| `docs/frontend.md` | Rutas: reseñar en `/mis-reservas` y sección "Reseñas" en `/espacios/:id`. |

**Reapertura ★:**

| Documento | Cambio |
|-----------|--------|
| `docs/informe/HU-24.md` (nuevo) | Capítulo 8 del informe según § 12.4. |
| `docs/historias/HU-24.md` | Después de "Programa / Prueba y documenta", la línea `- **Se documenta a fondo (capítulo 8).** Patrones: Decorator, DAO.`, como en HU-23. |
| `docs/architecture.md` | Fila `HU-24 ★ \| Decorator, DAO \| …` en "Patrones por historia", entre HU-23 y HU-25, con los roles de § 12.1 y § 12.2 y el enlace a la sección de reseñas. En la sección "Reseñas: qué deja HU-24", un párrafo con la cadena y `ResenaDAO` (HU-26 y HU-27 no deben usar `ResenaRepository`; HU-27 ya lo hace por el DAO). |
| `docs/requerimientos.md` § 7 | Fila de HU-24: `[HU-24](historias/HU-24.md) ★` y columna "Patrón" `Decorator, DAO`. |
