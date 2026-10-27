# Marketplace de Alquiler de Espacios — Especificación del proyecto

> **Para personas y asistentes de IA.** Lee este documento completo antes de diseñar, programar o redactar algo del proyecto. Si vas a trabajar una historia, lee también su archivo en `docs/historias/` (índice en la sección 9).
> Si una instrucción de un prompt contradice esta especificación, manda la especificación y hay que avisar al equipo.
> Generado el 2026-10-10 desde el tablero del equipo (tablero-is2) y actualizado el 2026-10-11 con [`arquitectura-propuesta.md`](arquitectura-propuesta.md) y [`docs/requerimientos.md`](docs/requerimientos.md). En el repositorio vive como `AGENTS.md` (este archivo) y las historias en `docs/historias/HU-XX.md`.
>
> **Documentos de referencia:**
> - [`arquitectura-propuesta.md`](arquitectura-propuesta.md): base de datos, restricciones, componentes, estilos, vista lógica y modelo de datos.
> - [`docs/requerimientos.md`](docs/requerimientos.md): catálogo de RF, RNF y reglas de negocio, más la matriz de trazabilidad.
>
> Este archivo resume lo esencial de ambos documentos; el detalle está en cada uno.

## 1. Qué es el producto

Marketplace web para alquilar espacios deportivos y para eventos (canchas, estadios, auditorios), con pago en línea.

- **Roles del sistema:** Usuario (arrendatario), Arrendador y Administrador.
- **Normativas:** PCI DSS (pagos con tarjeta, siempre vía pasarela certificada) y protección de datos personales (en Perú, Ley 29733).
- **Nombre del proyecto:** pendiente de confirmar. El `README.md` usa "EspacioYa".
- **Alcance:** 2 releases, 8 sprints y 28 historias de usuario, todas dentro del alcance.
  - **Release 1 (MVP transaccional):** al terminar, funciona el ciclo publicar → buscar → reservar → pagar → cancelar con reembolso.
  - **Release 2 (confianza y operación):** MFA, cobro del arrendador, perfil y datos personales, filtros, reseñas, reclamos y moderación.
- **Contexto académico:** Proyecto Final de Ingeniería de Software II (Universidad de Lima, 2026-2), con Scrum. Hay que demostrar POO, principios y patrones de diseño, arquitectura documentada y pruebas como parte del desarrollo.

## 2. Equipo

| Persona | Rol Scrum | Historias que diseña y programa | Su pareja (prueba sus historias) |
| --- | --- | --- | --- |
| Jorge Quispe | Developer (Dev1) | HU-01, HU-03, HU-09, HU-17, HU-25 | Kevin Chan |
| Adriana Callupe | Developer (Dev3) | HU-02, HU-11, HU-14, HU-20, HU-24 | Sebastian Garate |
| Eduardo Saldaña | Developer (Dev2) | HU-04, HU-12, HU-13, HU-19, HU-28 | Ariel Enriquez |
| Kevin Chan | Scrum Master | HU-05, HU-15, HU-22, HU-27 | Jorge Quispe |
| Sebastian Garate | Developer (Dev4) | HU-07, HU-08, HU-10, HU-21, HU-23 | Adriana Callupe |
| Ariel Enriquez | Product Owner | HU-06, HU-16, HU-18, HU-26 | Eduardo Saldaña |

- El **Product Owner** (Ariel) prioriza el backlog y acepta cada historia en la review.
- El **Scrum Master** (Kevin) facilita las ceremonias y redacta las actas.
- Todos programan.

## 3. Stack y arquitectura

| Pieza | Decisión |
| --- | --- |
| Backend | **Spring Boot (Java)**, programación orientada a objetos: Spring MVC (API REST), Spring Data JPA (persistencia) y Spring Security (autenticación, roles y MFA) |
| Frontend | **React** (SPA que consume la API REST; no contiene reglas de negocio) |
| Comunicación | API REST con JSON sobre HTTPS (TLS en toda la comunicación) |
| Base de datos | **Relacional. [POR CONFIRMAR: PostgreSQL]**, con migraciones versionadas (Flyway) y Spring Data JPA. El análisis está en la sección 1 de `arquitectura-propuesta.md`. Cuando se confirme, actualizar esta línea y justificarla en las secciones 6.4 y 6.10 del informe |
| Pagos | Pasarela certificada PCI DSS en **modo de prueba** (checkout de la pasarela + webhook con firma verificada) |
| Archivos | Almacenamiento privado fuera de la base de datos (documentos y fotos), servido con URL firmada de corta duración |
| Notificaciones | Solo por correo, mediante un componente reutilizable |
| Diagramas | Herramienta CASE del curso (se entregan los archivos editables). La IA puede proponerlos en PlantUML o Mermaid |

**Estilos y enfoque arquitectónico** (detalle en la sección 3 de `arquitectura-propuesta.md`):

| Tipo | Elección |
| --- | --- |
| Estilo | Arquitectura en capas (N-capas orientada al dominio): presentación → API → aplicación → dominio → persistencia, más una capa de infraestructura transversal |
| Despliegue | Cliente-servidor de 3 niveles: navegador (React) → servidor de aplicación (Spring Boot) → base de datos |
| Granularidad | **Monolito modular**, no microservicios |
| Patrón arquitectónico | MVC en el backend (Spring MVC; la vista es la respuesta JSON) |
| Enfoque | DDD ligero + OOA: cada épica es un módulo con su propio lenguaje; desarrollo basado en componentes con inyección de dependencias |

**Reglas de arquitectura (obligatorias):**

1. **Arquitectura en capas.** `Controller → Service → Domain/Repository`, con entidades y reglas en el dominio.
2. **El núcleo del backend va separado de la API REST.** La lógica de negocio y el acceso a datos no dependen de la web: la API es un cliente más del núcleo.
3. **Entity frente a DTO.** Las entidades nunca salen del backend: la API recibe y devuelve DTO, solo con los campos necesarios. Nunca se exponen contraseñas, documentos, datos bancarios ni datos de tarjeta.
4. **Un paquete por módulo**, alineado con las épicas, dentro de `pe.edu.ulima.<proyecto>`. Cada módulo tiene `controller`, `dto`, `service`, `domain` y `repository`:

   | Paquete | Historias |
   | --- | --- |
   | `cuentas` | HU-01 a HU-05, HU-17, HU-20, HU-22 |
   | `espacios` | HU-07, HU-08, HU-21 |
   | `busqueda` | HU-10, HU-11, HU-23, HU-24 |
   | `reservas` | HU-12, HU-14, HU-15, HU-25 |
   | `pagos` | HU-13, HU-16, HU-18, HU-19, HU-28 |
   | `administracion` | HU-06, HU-09, HU-26, HU-27 |
   | `comun` | Transversal: seguridad, auditoría, errores, correo, almacenamiento y configuración |

5. **Reglas de dependencia.**
   - Un controlador nunca llama directamente a un repositorio.
   - Entre módulos solo se usa la interfaz del `service` del otro módulo (por ejemplo, `reservas` usa `PagoService`, no `PagoRepository`), y no hay dependencias circulares.
   - El dominio no depende de Spring Web ni de la pasarela: las integraciones externas (pasarela, correo, almacenamiento) se usan detrás de interfaces propias (Adapter).
   - Todos los módulos pueden usar `comun`, pero `comun` no depende de ningún módulo.
6. **POO, principios y patrones.**
   - Aplicar SOLID, alta cohesión y bajo acoplamiento.
   - Patrones vistos en el curso: Factory Method, Abstract Factory, Singleton, Adapter, Facade, Decorator, Bridge, Strategy, Iterator, Command y DAO.
   - Usarlos solo donde resuelvan un problema real; la sección 10 indica cuáles se esperan en las historias documentadas.
   - DAO/Repository se usa en toda la persistencia. Los beans de Spring ya son *singleton*: no se programa un Singleton a mano.
   - Cada patrón se documenta en la sección 7.7 del informe, con dónde está y por qué.
7. **Reglas de negocio y montos solo en el servidor.** El cliente nunca decide montos, roles ni estados.
8. **Integridad en la base de datos.** Las reglas críticas también se garantizan con restricciones: bloque único por espacio, fecha y hora solo para bloques activos; `id_transaccion_pasarela` único; un reembolso por pago y origen; una reserva liquidada una sola vez; una reseña y un reclamo por reserva; correo único; llaves foráneas en todas las relaciones (sección 5.2 de `arquitectura-propuesta.md`).

**Convenciones de código (propuesta inicial; el equipo puede ajustarlas):**

- **Nombres:** las clases de dominio en español (`Reserva`, `Espacio`, `Liquidacion`) y los sufijos técnicos en inglés (`ReservaController`, `ReservaService`, `ReservaRepository`, `ReservaDTO`).
- **Endpoints REST:** en plural y en minúsculas (`/api/reservas`, `/api/espacios/{id}/disponibilidad`).
- **Errores:** un manejador global con mensajes genéricos al cliente; el detalle va solo en los logs.
- **Configuración y secretos:** en variables de entorno. Nunca se suben contraseñas, llaves de la pasarela ni tokens; se usa un `.env.example`.

## 4. Seguridad transversal (STRIDE)

Cada historia trae sus propios controles STRIDE (sección 9). Además, en todo el sistema:

- **(S) Suplantación:** la identidad se toma siempre de la sesión en el backend, nunca de un ID enviado por el cliente. Los tokens se invalidan al cerrar sesión o al expirar (30 minutos de inactividad).
- **(T) Manipulación:** las contraseñas se guardan con hash y sal (bcrypt o argon2). Todo lo que viene del cliente se valida en el servidor, y los montos se calculan solo ahí.
- **(R) Repudio:** las acciones sensibles se registran en una auditoría con quién, qué, cuándo e IP. Hay un componente de auditoría reutilizable.
- **(I) Divulgación:** se usa TLS y mensajes de error genéricos. Los datos sensibles van cifrados en reposo. Los documentos se sirven con URL firmada de corta duración.
- **(D) Denegación de servicio:** hay rate limiting donde la historia lo indica: registro, login, recuperación, búsqueda y ficha, reservas pendientes, pagos, códigos MFA, reclamos y reportes. Los límites concretos están en RNF-SEG-17 de `docs/requerimientos.md`.
- **(E) Elevación de privilegios:** los roles se asignan y validan en el backend en cada solicitud, y la propiedad del recurso se valida antes de cada edición. Las cuentas de administrador solo se crean por script interno.
- **PCI DSS:** la plataforma nunca recibe ni guarda datos de tarjeta. El estado del pago cambia solo por un webhook con firma verificada e idempotente.
- **Componentes transversales** (paquete `comun`): auditoría, rate limiting, manejo global de errores, sesión y tokens, correo, almacenamiento con URL firmada y cifrado de campos sensibles. Se construyen una vez, en el primer sprint que los necesita (la mayoría en el Sprint 1), y se reutilizan (sección 8 de `docs/requerimientos.md`).

## 5. Estados del sistema

**Cuenta del arrendador:** en el backlog nunca se dice "verificado" a secas: siempre se aclara si es el correo o la identidad.

| Estado | Qué habilita |
| --- | --- |
| Correo pendiente | Nada; espera la verificación del correo (HU-02 CA1). Aplica también al usuario recién registrado (HU-01) |
| Correo verificado | Iniciar sesión, crear borradores de espacios, subir documentos |
| Identidad en revisión | Nada adicional; espera la revisión del admin |
| Identidad verificada | Publicar espacios visibles, recibir reservas pagadas y registrar cuenta bancaria |
| Identidad rechazada | Volver a subir documentos |
| Suspendida | Nada; no puede iniciar sesión y sus espacios se ocultan |

En el modelo de datos, estos estados se reparten en tres campos: `usuario.correo_verificado`, `usuario.estado` (`ACTIVA`, `SUSPENDIDA`, `ELIMINADA`) y `perfil_arrendador.estado_identidad` (`NO_INICIADA`, `EN_REVISION`, `VERIFICADA`, `RECHAZADA`).

**Espacio:** `borrador` → `publicado` (requiere identidad verificada) ⇄ `pausado`; `publicado` → `baja`; `oculto` cuando lo oculta la moderación (HU-27). Si el arrendador está suspendido, sus espacios no se muestran, pero conservan su estado.

**Reserva:** `pendiente de pago` (retiene los bloques 15 minutos) → `confirmada` (pago aprobado) o `expirada` (sin pago a tiempo). Una reserva confirmada puede pasar a `cancelada`.

**Transacción (pago):** `rechazado` cuando la pasarela no aprueba el pago (se puede reintentar mientras la reserva siga vigente). Un pago aprobado queda `pagada` y puede pasar a `reembolsada` o a `reembolso fallido` (el admin puede reintentarlo).

**Liquidación:** el admin la genera semanalmente y queda `generada` → `pagada` (con número de operación). Queda en `pendiente de cuenta` si el arrendador no registró una cuenta bancaria.

**Simplificaciones del backlog:**
- La política de cancelación es fija: con 48 h o más se devuelve el 100 %; con menos, 0 %; si cancela el arrendador, se devuelve el 100 %.
- El checkout expira junto con la reserva.
- La liquidación la genera el admin con un botón y la transferencia es manual.
- Los bloques libres se calculan al consultar: horario − bloqueos − reservas.

## 6. Fuera del alcance

| Qué | Decisión | Motivo |
| --- | --- | --- |
| Galería ampliada (más de 5 fotos) y video | Descartada | Las 5 fotos de HU-07 bastan; el video suma almacenamiento y validación costosos. |
| Precios por franja horaria | Descartada | Complica el cálculo de montos, reembolsos y liquidaciones; se usa un precio único por hora. |
| Mapa interactivo | Descartada | Depende de un proveedor externo; la dirección en texto basta. |
| Espacios favoritos | Descartada | No aporta al ciclo principal de reserva y pago. |
| Reprogramar una reserva | Descartada | Exige un cambio atómico de bloques y cobrar o devolver diferencias; se cubre cancelando y reservando de nuevo. |
| Panel de ingresos y ocupación | Descartada | "Mis liquidaciones" (HU-19) ya muestra lo cobrado. |
| Destacar publicación (pago por visibilidad) | Descartada | Monetización secundaria; requiere un catálogo con volumen. |
| Pago con billetera digital, transferencias y cuotas | Descartada | Cada medio es otra integración; la tarjeta cubre el MVP. |
| Notificaciones push | Descartada | Requiere app o service worker; el correo cubre los avisos. |
| Política de cancelación configurable | Simplificada | Política fija de 48 h (HU-15). |
| Liquidación automática (job y transferencia por API bancaria) | Simplificada | El admin la genera con un botón y transfiere a mano (HU-19). |
| Reembolso de pagos que llegan tras expirar la reserva | Simplificada | El checkout expira junto con la reserva (HU-13), así que ese caso no ocurre. |
| Respuesta del arrendador a reseñas y fotos en reclamos | Simplificada | Se dejan fuera para acotar HU-24 y HU-26. |

No implementes nada de esta lista aunque parezca útil.

## 7. Plan de releases y sprints

El backlog define 8 sprints. Para terminar a tiempo (5 semanas con 6 personas), el equipo trabaja **dos sprints por semana**:

| Semana real | Release | Sprint | Objetivo | Historias |
| --- | --- | --- | --- | --- |
| 8 | Release 1 | 1 | Cuentas: registro, acceso y recuperación | HU-01, HU-02, HU-03, HU-04 |
| 8 | Release 1 | 2 | Oferta verificada: identidad, publicación y disponibilidad | HU-05, HU-06, HU-07, HU-08 |
| 9 | Release 1 | 3 | Control admin, búsqueda y reserva | HU-09, HU-10, HU-11, HU-12 |
| 9 | Release 1 | 4 | Pagar, consultar, cancelar y reembolsar | HU-13, HU-14, HU-15, HU-16 |
| 10 | Release 2 | 5 | Seguridad de cuentas y cobro del arrendador | HU-17, HU-18, HU-19 |
| 10 | Release 2 | 6 | Gestión de cuenta, publicaciones y datos personales | HU-20, HU-21, HU-22 |
| 11 | Release 2 | 7 | Descubrimiento y confianza | HU-23, HU-24, HU-25 |
| 11 | Release 2 | 8 | Soporte, moderación y comprobantes | HU-26, HU-27, HU-28 |

La semana 12 es de estabilización (pruebas, defectos e informe), y la entrega es en la semana 13.

## 8. Cómo se trabaja cada historia

1. **Diseñar (el dueño):** caso de uso, diagrama de secuencia del flujo principal y de un error, endpoints con sus DTO y clases. La pareja revisa el diseño antes de programar.
2. **Programar (el dueño):** siguiendo el diseño aprobado, en la rama `feature/HU-XX-descripcion-corta`, con todos los criterios y controles STRIDE. Termina con un pull request hacia `develop`.
3. **Probar y documentar (la pareja):**
   - revisa el pull request;
   - escribe pruebas unitarias (al menos una por criterio, incluido el de error) y ejecuta casos de caja negra;
   - guarda las capturas como `HU-XX-E01`, `HU-XX-E02`…;
   - llena la fila de la historia en la matriz de trazabilidad (sección 7 de [`docs/requerimientos.md`](docs/requerimientos.md)).
4. **Aceptar (el Product Owner):** en la review comprueba la historia contra sus criterios.

**Definition of Ready:**
- La historia está en formato *Como…, necesito…, para…*.
- Sus dependencias están indicadas y en un sprint anterior o el mismo.
- Tiene criterios Gherkin y controles STRIDE.
- Su tamaño es S o M.
- Está validada por el PO y el equipo.

**Definition of Done:**
- El código está revisado y tiene pruebas unitarias y de integración aprobadas.
- Está desplegado en UAT y validado por el PO.
- Los controles STRIDE están implementados.
- Cumple PCI DSS y la protección de datos cuando aplica.
- La documentación está actualizada si cambia un contrato de API o un estado.

**Reglas para usar IA:**
- La IA propone y redacta; la persona revisa y responde por el resultado. Nada entra a develop sin el pull request revisado por la pareja.
- Contexto fijo para todos: la especificación del proyecto (`AGENTS.md`) más el archivo de la historia que trabajas (`docs/historias/HU-XX.md`). Para diseñar, agrega también `arquitectura-propuesta.md` (paquetes, dependencias y modelo de datos) y los RF, RNF y RN de tu historia en `docs/requerimientos.md`. Dáselos siempre a tu IA: así el código sale igual aunque lo pidan 6 personas distintas.
- Primero diseño, después código: con la historia, sus criterios y su STRIDE se pide el diseño, se revisa, y recién entonces se pide el código siguiendo ese diseño.
- El informe se redacta con IA solo a partir de datos reales (actas, hoja de métricas, capturas, código). Nunca pedirle que invente métricas, dailies ni evidencias: la plantilla exige datos reales.
- Los diagramas que proponga la IA (por ejemplo en PlantUML) se pasan a la herramienta CASE del curso: el enunciado pide los archivos editables.
- Guardar los prompts importantes en una nota: se necesitan para la Declaración de uso de IA.

## 9. Historias de usuario

Fuente: Excel del backlog. Cada historia vive en su propio archivo en [`docs/historias/`](docs/historias/), con su dueño, la persona que la prueba, la semana real, los criterios de aceptación y los controles STRIDE. Los requerimientos derivados de cada historia (RF, RNF, RN) y la matriz de trazabilidad están en [`docs/requerimientos.md`](docs/requerimientos.md).

**Antes de diseñar o programar una historia**, lee este documento completo, el archivo de la historia y los archivos de las historias de las que depende.

★ = se documenta a fondo en el capítulo 8 del informe y sale en el video de su release (ver la sección 10).

### Épica A. Gestión de cuentas y autenticación

Permite a usuarios y arrendadores registrarse, autenticarse, verificar su identidad y gestionar su perfil y sus datos personales de forma segura.

| Historia | Release · Sprint (semana) | Tamaño | Depende de | Programa | Prueba y documenta |
| --- | --- | --- | --- | --- | --- |
| [HU-01 · Registro de usuario con consentimiento](docs/historias/HU-01.md) | R1 · S1 (8) | S | — | Jorge Quispe | Kevin Chan |
| [HU-02 · Registro de arrendador](docs/historias/HU-02.md) | R1 · S1 (8) | S | HU-01 | Adriana Callupe | Sebastian Garate |
| [HU-03 · Inicio y cierre de sesión](docs/historias/HU-03.md) | R1 · S1 (8) | S | HU-01, HU-02 | Jorge Quispe | Kevin Chan |
| [HU-04 · Recuperación de contraseña](docs/historias/HU-04.md) | R1 · S1 (8) | S | HU-03 | Eduardo Saldaña | Ariel Enriquez |
| [HU-05 · Envío de documentos de identidad del arrendador](docs/historias/HU-05.md) | R1 · S2 (8) | M | HU-02 | Kevin Chan | Jorge Quispe |
| [HU-17 · Autenticación multifactor (MFA)](docs/historias/HU-17.md) | R2 · S5 (10) | M | HU-03 | Jorge Quispe | Kevin Chan |
| [HU-20 · Edición de perfil](docs/historias/HU-20.md) | R2 · S6 (10) | S | HU-03 | Adriana Callupe | Sebastian Garate |
| [HU-22 · Derechos sobre datos personales](docs/historias/HU-22.md) | R2 · S6 (10) | M | HU-01, HU-19, HU-20 | Kevin Chan | Jorge Quispe |

### Épica B. Publicación y gestión de espacios

Permite al arrendador publicar sus espacios (canchas, estadios, auditorios), definir su disponibilidad y pausar o dar de baja sus publicaciones.

| Historia | Release · Sprint (semana) | Tamaño | Depende de | Programa | Prueba y documenta |
| --- | --- | --- | --- | --- | --- |
| [HU-07 · Publicar y editar un espacio](docs/historias/HU-07.md) | R1 · S2 (8) | M | HU-02 (visible tras HU-06) | Sebastian Garate | Adriana Callupe |
| [HU-08 · Disponibilidad del espacio](docs/historias/HU-08.md) | R1 · S2 (8) | M | HU-07 | Sebastian Garate | Adriana Callupe |
| [HU-21 · Pausar o dar de baja una publicación](docs/historias/HU-21.md) | R2 · S6 (10) | S | HU-07, HU-15 | Sebastian Garate | Adriana Callupe |

### Épica C. Búsqueda y descubrimiento de espacios

Permite al usuario encontrar espacios disponibles, evaluar su detalle y apoyarse en reseñas de otros usuarios.

| Historia | Release · Sprint (semana) | Tamaño | Depende de | Programa | Prueba y documenta |
| --- | --- | --- | --- | --- | --- |
| [HU-10 · Búsqueda por tipo, zona y fecha](docs/historias/HU-10.md) | R1 · S3 (9) | M | HU-07, HU-08 | Sebastian Garate | Adriana Callupe |
| [HU-11 · Detalle del espacio](docs/historias/HU-11.md) | R1 · S3 (9) | S | HU-07, HU-08 | Adriana Callupe | Sebastian Garate |
| [HU-23 · Filtros de búsqueda por precio, capacidad y características](docs/historias/HU-23.md) ★ | R2 · S7 (11) | S | HU-07, HU-10 | Sebastian Garate | Adriana Callupe |
| [HU-24 · Reseñas y calificaciones](docs/historias/HU-24.md) | R2 · S7 (11) | M | HU-14 | Adriana Callupe | Sebastian Garate |

### Épica D. Reservas y disponibilidad

Permite reservar bloques horarios sin dobles reservas, consultar y cancelar reservas y recibir recordatorios.

| Historia | Release · Sprint (semana) | Tamaño | Depende de | Programa | Prueba y documenta |
| --- | --- | --- | --- | --- | --- |
| [HU-12 · Reserva con bloqueo temporal](docs/historias/HU-12.md) | R1 · S3 (9) | M | HU-03, HU-08, HU-11 | Eduardo Saldaña | Ariel Enriquez |
| [HU-14 · Mis reservas](docs/historias/HU-14.md) ★ | R1 · S4 (9) | S | HU-12, HU-13 | Adriana Callupe | Sebastian Garate |
| [HU-15 · Cancelación de reservas](docs/historias/HU-15.md) ★ | R1 · S4 (9) | S | HU-13, HU-14 | Kevin Chan | Jorge Quispe |
| [HU-25 · Recordatorio de reserva por correo](docs/historias/HU-25.md) ★ | R2 · S7 (11) | S | HU-13 | Jorge Quispe | Kevin Chan |

### Épica E. Pagos en línea

Gestiona el cobro con tarjeta vía pasarela certificada PCI DSS, los reembolsos, la liquidación al arrendador y los comprobantes.

| Historia | Release · Sprint (semana) | Tamaño | Depende de | Programa | Prueba y documenta |
| --- | --- | --- | --- | --- | --- |
| [HU-13 · Pago con tarjeta](docs/historias/HU-13.md) ★ | R1 · S4 (9) | M | HU-12 | Eduardo Saldaña | Ariel Enriquez |
| [HU-16 · Reembolso automático](docs/historias/HU-16.md) ★ | R1 · S4 (9) | S | HU-13, HU-15 | Ariel Enriquez | Eduardo Saldaña |
| [HU-18 · Registro de cuenta bancaria del arrendador](docs/historias/HU-18.md) | R2 · S5 (10) | S | HU-06 | Ariel Enriquez | Eduardo Saldaña |
| [HU-19 · Liquidación semanal al arrendador](docs/historias/HU-19.md) | R2 · S5 (10) | M | HU-13, HU-18 | Eduardo Saldaña | Ariel Enriquez |
| [HU-28 · Historial de pagos y comprobantes](docs/historias/HU-28.md) | R2 · S8 (11) | S | HU-13, HU-16 | Eduardo Saldaña | Ariel Enriquez |

### Épica F. Panel de administración

Da al administrador control operativo: verificación de arrendadores, suspensión de cuentas, reclamos y moderación de contenido.

| Historia | Release · Sprint (semana) | Tamaño | Depende de | Programa | Prueba y documenta |
| --- | --- | --- | --- | --- | --- |
| [HU-06 · Revisión de identidad de arrendadores](docs/historias/HU-06.md) | R1 · S2 (8) | M | HU-05 | Ariel Enriquez | Eduardo Saldaña |
| [HU-09 · Suspensión y reactivación de cuentas](docs/historias/HU-09.md) | R1 · S3 (9) | S | HU-06 | Jorge Quispe | Kevin Chan |
| [HU-26 · Reclamos sobre una reserva](docs/historias/HU-26.md) | R2 · S8 (11) | M | HU-14, HU-16, HU-19 | Ariel Enriquez | Eduardo Saldaña |
| [HU-27 · Reportes y moderación de publicaciones y reseñas](docs/historias/HU-27.md) | R2 · S8 (11) | M | HU-09, HU-17, HU-24 | Kevin Chan | Jorge Quispe |

## 10. Historias documentadas a fondo y patrones de diseño

El profesor pide exponer **una historia por integrante**. Estas 6 se documentan completas en el capítulo 8 (escenario, diagrama de secuencia, código, tabla mensaje → método) y su patrón va en la sección 7.7.
Salen de los sprints que se graban en video: el **Sprint 4 del R1** y el **Sprint 7 del R2**.

| Historia | Dueño | Patrones | Cómo se aplican |
| --- | --- | --- | --- |
| HU-13 · Pago con tarjeta | Eduardo Saldaña | Adapter, Factory Method, Facade | Adapter: una interfaz propia PasarelaPago y un adaptador que traduce la API de la pasarela (checkout y verificación de la firma del webhook), para que el dominio no dependa del proveedor. Factory Method para crear la pasarela según la configuración (sandbox o real) y Facade (CheckoutFacade) para el flujo reserva vigente → monto en servidor → checkout. |
| HU-14 · Mis reservas | Adriana Callupe | Iterator, DAO | Iterator: recorrer las reservas del usuario separadas en próximas y pasadas sin exponer la colección ni la consulta. DAO/Repository: consultas filtradas siempre por el usuario de la sesión (control STRIDE E/I). |
| HU-15 · Cancelación de reservas | Kevin Chan | Strategy | Strategy: una interfaz PoliticaReembolso con una estrategia por caso (usuario con 48 h o más → 100 %, usuario con menos de 48 h → 0 %, arrendador → 100 %). El servicio elige la estrategia sin if por tipo, y si la política deja de ser fija solo se agrega una estrategia. |
| HU-16 · Reembolso automático | Ariel Enriquez | Command, Adapter | Command: cada reembolso es un objeto (SolicitudReembolso) con ejecutar(), que se registra, es idempotente y se puede reintentar con el botón del admin cuando falla. Reutiliza el Adapter de la pasarela de HU-13. |
| HU-23 · Filtros de búsqueda por precio, capacidad y características | Sebastian Garate | Decorator | Decorator: cada filtro (precio máximo, capacidad mínima, característica) envuelve a la búsqueda base de HU-10 y se pueden combinar libremente, sin una subclase por cada combinación de filtros. |
| HU-25 · Recordatorio de reserva por correo | Jorge Quispe | Factory Method, Adapter | Factory Method: crear el correo adecuado según el evento (recordatorio, pago confirmado, cancelación); solo el recordatorio respeta la preferencia de desactivarlo. Adapter para el proveedor de correo, que así se puede cambiar sin tocar el dominio. |

Al diseñar estas historias, pide a tu IA que aplique estos patrones y que lo muestre en el diagrama de clases y en el de secuencia.

**Dónde vive cada patrón** (según `arquitectura-propuesta.md`):

| Patrón | Ubicación |
| --- | --- |
| Adapter, Factory Method, Facade (HU-13) | Paquete `pagos`: la interfaz `PasarelaPago` y su adaptador; `CheckoutFacade` |
| Iterator, DAO (HU-14) | Paquete `reservas` |
| Strategy (HU-15) | Paquete `reservas`: la interfaz `PoliticaReembolso` |
| Command, Adapter (HU-16) | Paquete `pagos`: `SolicitudReembolso` (reutiliza el adaptador de HU-13) |
| Decorator (HU-23) | Paquete `busqueda`: filtros combinables |
| Factory Method, Adapter (HU-25) | Componente de correo en `comun` |
| DAO / Repository | Toda la persistencia: un repositorio por agregado |

**Patrones y estilos de arquitectura:** capas (N-capas), cliente-servidor de 3 niveles, monolito modular, MVC y DDD ligero (ver la sección 3).
