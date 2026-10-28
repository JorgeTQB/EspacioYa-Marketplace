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
| Pagos          | Pasarela certificada PCI DSS en modo de prueba (checkout de la pasarela + webhook con firma verificada) |
| Archivos       | Almacenamiento privado fuera de la base de datos, servido con URL firmada de corta duración |
| Notificaciones | Solo correo, mediante un componente reutilizable |

Estilo: **N-capas orientada al dominio**, cliente-servidor de 3 niveles,
**monolito modular** (no microservicios), MVC en el backend y DDD ligero
(cada épica es un módulo).

## Principios

1. **Capas claras.** Dentro de cada módulo:
   `controller → service → domain / repository`.
   - `controller` — recibe la solicitud, valida la entrada y convierte DTO.
   - `service` — coordina el caso de uso y las transacciones.
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

7. **Integridad también en la base de datos.** Las reglas críticas se
   garantizan con restricciones, no solo en código:
   - bloque único por (espacio, fecha, hora) solo para bloques activos (HU-12);
   - `pago.id_transaccion_pasarela` único (HU-13);
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
                     ↑          ↑
                 busqueda ──────┘        administracion → (todos)
```

| Interfaz (la define)                  | La implementa              | Para |
|---------------------------------------|----------------------------|------|
| `comun.ValidadorSesion`               | cuentas                    | Que el filtro de seguridad revise si la sesión está revocada |
| `cuentas.ParticipanteDatosPersonales` | reservas, pagos, busqueda  | HU-22: exportar, verificar impedimentos y anonimizar. Se inyecta como `List<>`: agregar un módulo no cambia nada (OCP) |
| `espacios.ReservasDelEspacio`         | reservas                   | HU-08 y HU-21 |
| `pagos.ReservasPorCobrar`             | reservas                   | HU-13: obtener la reserva vigente con su monto y confirmarla |
| `pagos.ReservasLiquidables`           | reservas                   | HU-19 |
| `pagos.ReclamosAbiertos`              | administracion             | RN-12 |

Solo los servicios que usan otros módulos llevan interfaz pública
(`CuentaService`, `ReservaService`, `PagoService`); los internos son clases
concretas (YAGNI). Si una historia necesita un puerto nuevo, lo justifica en
su `design.md` y lo agrega a esta tabla.

## Seguridad transversal (STRIDE)

Cada historia trae sus propios controles STRIDE en `docs/historias/HU-XX.md`.
Además, en todo el sistema:

- **(S) Suplantación:** la identidad se toma siempre de la sesión en el backend,
  nunca de un ID enviado por el cliente. Los tokens se invalidan al cerrar
  sesión o tras 30 minutos de inactividad.
- **(T) Manipulación:** contraseñas con hash y sal (bcrypt o argon2). Todo lo
  que viene del cliente se valida en el servidor; los montos se calculan solo ahí.
- **(R) Repudio:** las acciones sensibles se registran en la auditoría con
  quién, qué, cuándo e IP.
- **(I) Divulgación:** TLS, mensajes de error genéricos, datos sensibles
  cifrados en reposo y documentos servidos con URL firmada de corta duración.
- **(D) Denegación de servicio:** rate limiting donde la historia lo indica.
  Los límites están en RNF-SEG-17 de `docs/requerimientos.md`.
- **(E) Elevación de privilegios:** roles asignados y validados en el backend en
  cada solicitud; la propiedad del recurso se valida antes de cada edición o
  consulta. Las cuentas de administrador solo se crean por script interno.
- **PCI DSS:** la plataforma nunca recibe ni guarda datos de tarjeta. El estado
  del pago cambia solo por un webhook con firma verificada e idempotente.
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
`VERIFICADA`, `RECHAZADA`).

- **Espacio:** `BORRADOR` → `PUBLICADO` (requiere identidad verificada) ⇄
  `PAUSADO`; `PUBLICADO` → `BAJA`; `OCULTO` por moderación (HU-27). Si el
  arrendador está suspendido, sus espacios no se muestran pero conservan su estado.
- **Reserva:** `PENDIENTE_PAGO` (retiene los bloques 15 minutos) → `CONFIRMADA`
  (pago aprobado) o `EXPIRADA` (sin pago a tiempo); `CONFIRMADA` → `CANCELADA`.
- **Pago:** `RECHAZADO` si la pasarela no aprueba (se puede reintentar mientras
  la reserva siga vigente); `PAGADO` → `REEMBOLSADO` o `REEMBOLSO_FALLIDO`
  (el admin puede reintentarlo).
- **Liquidación:** `GENERADA` → `PAGADA` (con número de operación);
  `PENDIENTE_CUENTA` si el arrendador no registró cuenta bancaria.

**Simplificaciones:**
- Política de cancelación fija: usuario con 48 h o más → 100 %; con menos → 0 %;
  si cancela el arrendador → 100 %.
- El checkout expira junto con la reserva.
- La liquidación la genera el admin con un botón y la transferencia es manual.
- Los bloques libres se calculan al consultar: horario − bloqueos − reservas.

## Patrones por historia

Las historias marcadas ★ se documentan a fondo en el informe; su `design.md`
debe aplicar el patrón y mostrarlo en el diagrama de clases y de secuencia.

| Historia     | Patrones                        | Dónde vive y cómo se aplica |
|--------------|---------------------------------|-----------------------------|
| HU-13 ★      | Adapter, Factory Method, Facade | `pagos`: interfaz `PasarelaPago` + adaptador del proveedor; fábrica según configuración (sandbox o simulada); `CheckoutFacade` para reserva vigente → monto en servidor → checkout |
| HU-14 ★      | Iterator, DAO                   | `reservas`: recorrer reservas próximas y pasadas sin exponer la colección; consultas siempre filtradas por el usuario de la sesión |
| HU-15 ★      | Strategy                        | `reservas`: interfaz `PoliticaReembolso` con una estrategia por caso; el servicio la elige sin `if` por tipo |
| HU-16 ★      | Command, Adapter                | `pagos`: `SolicitudReembolso` con `ejecutar()`, idempotente y reintentable; reutiliza el adaptador de HU-13 |
| HU-23 ★      | Decorator                       | `busqueda`: cada filtro envuelve a la búsqueda base de HU-10 y se combinan libremente |
| HU-25 ★      | Factory Method, Adapter         | `comun/correo`: crear el correo según el evento; adaptador del proveedor de correo |
| Todas        | DAO / Repository                | Un repositorio por agregado |

## Qué NO hacer

- No llamar a un repositorio desde un controlador, ni al repositorio de otro módulo.
- No devolver entidades JPA en la API.
- No calcular montos, roles ni estados en el frontend, ni aceptarlos del cliente.
- No tomar el ID del usuario del cuerpo o la URL cuando debe salir de la sesión.
- No usar el SDK de la pasarela, del correo o del almacenamiento fuera de su adaptador.
- No mostrar detalles técnicos en los errores al cliente; el detalle va a los logs.
- No guardar archivos en la base de datos ni servirlos con URL pública.
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
  | Reembolso de pagos que llegan tras expirar la reserva | Simplificada: el checkout expira con la reserva |
  | Respuesta del arrendador a reseñas y fotos en reclamos | Simplificada: fuera de HU-24 y HU-26 |
