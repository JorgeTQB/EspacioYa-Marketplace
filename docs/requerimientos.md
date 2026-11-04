# Especificación de requerimientos — Marketplace de Alquiler de Espacios (EspacioYa)

> **Para personas y asistentes de IA.** Este documento cataloga los requerimientos funcionales (RF), no funcionales (RNF) y las reglas de negocio (RN) del proyecto, y los traza a cada historia de usuario.
> Se deriva **solo** de la especificación original del proyecto (hoy integrada en [`architecture.md`](architecture.md)) y de los archivos de [`docs/historias/`](historias/). Si algo de aquí contradice `architecture.md` o una historia, mandan ellos y hay que avisar al equipo.
> Versión 1.1 · 2026-10-27.

## 1. Propósito y fuentes

- **Propósito:** tener en un solo lugar qué debe hacer el sistema (RF), con qué calidad y restricciones (RNF) y bajo qué reglas (RN), con su origen. Sirve de base para el capítulo de requisitos del informe y para la matriz de trazabilidad que llena cada pareja de prueba (sección 7).
- **Fuentes:** la especificación original del proyecto y las 28 historias `HU-01` a `HU-28`.
- **Notación de origen:** `HU-XX CAn` = criterio de aceptación *n* de la historia; `HU-XX (X)` = control STRIDE de la historia con la letra X; `§n` = sección de la especificación original. Su contenido está hoy en `architecture.md`: §3 → Stack y Principios; §4 → Seguridad transversal; §5 → Estados del sistema; §6 → Qué NO hacer; §8 → `verification.md` (Definition of Done); §10 → Patrones por historia.
- **Prioridad:** **Alta** = Release 1 (MVP transaccional); **Media** = Release 2 (confianza y operación).

## 2. Actores

| Actor | Descripción |
| --- | --- |
| Visitante | Persona sin cuenta. Puede buscar y ver espacios y registrarse. |
| Usuario (arrendatario) | Cuenta que reserva y paga espacios, cancela, reseña y reclama. |
| Arrendador | Cuenta que publica espacios. Su capacidad depende de su estado (correo verificado, identidad en revisión, identidad verificada, identidad rechazada, suspendida; ver RN-01). |
| Administrador | Revisa identidades, suspende cuentas, gestiona reembolsos fallidos, liquidaciones, reclamos y moderación. Se crea solo por script interno. |
| Pasarela de pago (externo) | Pasarela certificada PCI DSS en modo de prueba: checkout, webhook firmado y reembolsos. |
| Proveedor de correo (externo) | Envío de correos transaccionales y recordatorios. |

## 3. Requerimientos funcionales

### Épica A. Gestión de cuentas y autenticación

| ID | El sistema debe… | Origen | Prioridad |
| --- | --- | --- | --- |
| RF-A-01 | Permitir que un visitante se registre como usuario con correo y contraseña que cumpla la política, y enviarle un correo de verificación. | HU-01 CA1 | Alta |
| RF-A-02 | Impedir el registro si no se marca la aceptación de términos y política de privacidad. | HU-01 CA2 | Alta |
| RF-A-03 | Ante correo ya existente o contraseña débil, mostrar un mensaje genérico o los requisitos mínimos sin revelar datos de otra cuenta. | HU-01 CA3 | Alta |
| RF-A-04 | Permitir el registro de arrendador con datos de contacto y facturación y aceptación de privacidad, creando la cuenta con rol arrendador y estado "correo pendiente". | HU-02 CA1 | Alta |
| RF-A-05 | Validar el formato de RUC/DNI y mostrar un error de validación específico. | HU-02 CA2 | Alta |
| RF-A-06 | Al verificar el correo, pasar al arrendador a "correo verificado" y avisarle que debe verificar su identidad para publicar. | HU-02 CA3 | Alta |
| RF-A-07 | Permitir iniciar sesión con credenciales correctas a cuentas con correo verificado y llevar al panel del rol. | HU-03 CA1 | Alta |
| RF-A-08 | Mostrar un mensaje genérico ante credenciales incorrectas. | HU-03 CA2 | Alta |
| RF-A-09 | Tras 5 fallos en 15 minutos, pedir captcha o bloquear temporalmente el acceso. | HU-03 CA3 | Alta |
| RF-A-10 | Invalidar la sesión al cerrar sesión o tras 30 minutos de inactividad. | HU-03 CA4, §4 (S) | Alta |
| RF-A-11 | Permitir solicitar la recuperación de contraseña mostrando siempre el mismo mensaje y, si la cuenta existe, enviar un enlace válido por 30 minutos. | HU-04 CA1 | Alta |
| RF-A-12 | Rechazar enlaces de recuperación expirados o ya usados y permitir pedir otro. | HU-04 CA2 | Alta |
| RF-A-13 | Cerrar todas las sesiones activas al cambiar la contraseña. | HU-04 CA3 | Alta |
| RF-A-14 | Permitir al arrendador con correo verificado subir DNI/RUC y un documento de propiedad o autorización (PDF/JPG/PNG, máx. 5 MB) y pasar su estado a "identidad en revisión". | HU-05 CA1 | Alta |
| RF-A-15 | Rechazar archivos de formato o tamaño no permitido indicando el motivo. | HU-05 CA2 | Alta |
| RF-A-16 | Mostrar al arrendador con identidad rechazada el motivo y permitirle volver a subir documentos. | HU-05 CA3 | Alta |
| RF-A-17 | Exigir al administrador configurar y usar MFA (TOTP) para acceder al panel. | HU-17 CA1 | Media |
| RF-A-18 | Permitir a usuarios y arrendadores activar MFA; desde entonces, pedir el código al iniciar sesión. | HU-17 CA2 | Media |
| RF-A-19 | Entregar 5 códigos de respaldo de un solo uso al terminar la configuración de MFA. | HU-17 CA3 | Media |
| RF-A-20 | Permitir editar nombre, teléfono, foto y datos de facturación del propio perfil, reflejando los cambios de inmediato. | HU-20 CA1 | Media |
| RF-A-21 | Al cambiar el correo, activarlo solo tras verificarlo y avisar al correo anterior. | HU-20 CA2 | Media |
| RF-A-22 | Si un arrendador con identidad verificada cambia su RUC/DNI, devolver su identidad a "en revisión". | HU-20 CA3 | Media |
| RF-A-23 | Permitir descargar, tras confirmar la contraseña, un archivo JSON con el perfil, reservas y pagos del usuario de la sesión. | HU-22 CA1 | Media |
| RF-A-24 | Permitir solicitar la eliminación de la cuenta (con contraseña) si no hay reservas futuras ni liquidaciones pendientes: desactivar la cuenta, anonimizar los datos personales y conservar los registros contables el plazo legal. | HU-22 CA2 | Media |
| RF-A-25 | Si hay reservas futuras o liquidaciones pendientes, indicar qué se debe resolver antes de eliminar la cuenta. | HU-22 CA3 | Media |
| RF-A-26 | Permitir activar o desactivar los recordatorios por correo desde el perfil. | HU-25 CA2 | Media |

### Épica B. Publicación y gestión de espacios

| ID | El sistema debe… | Origen | Prioridad |
| --- | --- | --- | --- |
| RF-B-01 | Permitir al arrendador con correo verificado crear la ficha de un espacio (tipo, dirección, zona, capacidad, precio por hora, hasta 5 fotos) y guardarla como borrador. | HU-07 CA1 | Alta |
| RF-B-02 | Permitir publicar el borrador solo si el arrendador tiene identidad verificada; el espacio queda visible en el catálogo. | HU-07 CA2 | Alta |
| RF-B-03 | Impedir publicar a un arrendador sin identidad verificada e indicarle completar la verificación. | HU-07 CA3 | Alta |
| RF-B-04 | Permitir editar un espacio publicado reflejando los cambios en el catálogo; las reservas ya pagadas conservan su precio. | HU-07 CA4 | Alta |
| RF-B-05 | Permitir al dueño definir un horario semanal por día en bloques de 1 hora, reservable en las próximas 8 semanas. | HU-08 CA1 | Alta |
| RF-B-06 | Permitir bloquear fechas puntuales; sus bloques dejan de estar disponibles. | HU-08 CA2 | Alta |
| RF-B-07 | Impedir bloquear una fecha con una reserva pagada, avisando del conflicto. | HU-08 CA3 | Alta |
| RF-B-08 | Permitir marcar las características del espacio desde una lista fija (techado, iluminación, vestuarios, estacionamiento). | HU-23 CA1 | Media |
| RF-B-09 | Permitir pausar un espacio: deja de aparecer en búsquedas y se mantienen las reservas existentes. | HU-21 CA1 | Media |
| RF-B-10 | Al dar de baja un espacio con reservas futuras, mostrar las reservas afectadas y exigir cancelarlas (reembolso 100 %) antes de completar la baja. | HU-21 CA2 | Media |

### Épica C. Búsqueda y descubrimiento

| ID | El sistema debe… | Origen | Prioridad |
| --- | --- | --- | --- |
| RF-C-01 | Buscar por tipo, zona y fecha, devolviendo solo espacios publicados de arrendadores no suspendidos con al menos un bloque libre esa fecha. | HU-10 CA1 | Alta |
| RF-C-02 | Mostrar en los resultados nombre, foto principal, precio por hora y zona, en páginas de 20. | HU-10 CA2 | Alta |
| RF-C-03 | Sin resultados, mostrar un mensaje y sugerir cambiar la fecha o la zona. | HU-10 CA3 | Alta |
| RF-C-04 | Mostrar la ficha del espacio con fotos, descripción, dirección en texto, capacidad, precio por hora y política de cancelación (48 h). | HU-11 CA1 | Alta |
| RF-C-05 | Mostrar los bloques libres del día elegido en la ficha y actualizarlos al cambiar la fecha. | HU-11 CA2 | Alta |
| RF-C-06 | Filtrar por precio máximo, capacidad mínima y características, cumpliendo todos los filtros junto con tipo, zona y fecha. | HU-23 CA2 | Media |
| RF-C-07 | Si la combinación de filtros no arroja resultados, sugerir quitar algún filtro. | HU-23 CA3 | Media |
| RF-C-08 | Permitir calificar de 1 a 5 y comentar una reserva confirmada ya pasada, dentro de los 14 días siguientes; la reseña aparece en la ficha y actualiza el promedio. | HU-24 CA1 | Media |
| RF-C-09 | Impedir reseñar un espacio sin una reserva pasada en él. | HU-24 CA2 | Media |
| RF-C-10 | Impedir reseñar dos veces la misma reserva. | HU-24 CA3 | Media |

### Épica D. Reservas y disponibilidad

| ID | El sistema debe… | Origen | Prioridad |
| --- | --- | --- | --- |
| RF-D-01 | Permitir a un usuario autenticado reservar uno o más bloques libres consecutivos, creando una reserva "pendiente de pago" con monto calculado en servidor y retención de 15 minutos. | HU-12 CA1 | Alta |
| RF-D-02 | Expirar la reserva sin pago confirmado a los 15 minutos y liberar los bloques. | HU-12 CA2 | Alta |
| RF-D-03 | Ante dos confirmaciones simultáneas del mismo bloque, otorgar la reserva a uno solo y mostrar "horario no disponible" al otro. | HU-12 CA3 | Alta |
| RF-D-04 | Redirigir al login a quien pulse "reservar" sin sesión y devolverlo a la ficha después. | HU-12 CA4 | Alta |
| RF-D-05 | Mostrar al usuario "Mis reservas" con espacio, fecha, horario, monto y estado, separadas en próximas y pasadas. | HU-14 CA1 | Alta |
| RF-D-06 | Mostrar al arrendador "Reservas de mis espacios" con las reservas confirmadas, el nombre del usuario y el horario. | HU-14 CA2 | Alta |
| RF-D-07 | Calcular el reembolso de una cancelación según la política fija (RN-06). | HU-15 CA1 | Alta |
| RF-D-08 | Mostrar el monto a reembolsar antes de confirmar la cancelación. | HU-15 CA2 | Alta |
| RF-D-09 | Al confirmar la cancelación, pasar la reserva a "cancelada", liberar los bloques, disparar el reembolso (RF-E-05) y avisar por correo al usuario y al arrendador. | HU-15 CA3 | Alta |
| RF-D-10 | Enviar un recordatorio por correo al usuario y al arrendador 24 h antes de cada reserva confirmada. | HU-25 CA1 | Media |
| RF-D-11 | No enviar el recordatorio a quien lo desactivó, pero sí enviar siempre los correos de pago y cancelación. | HU-25 CA2, CA3 | Media |

### Épica E. Pagos en línea

| ID | El sistema debe… | Origen | Prioridad |
| --- | --- | --- | --- |
| RF-E-01 | Permitir pagar una reserva pendiente vigente en el checkout de la pasarela; al confirmarse, pasar la reserva a "confirmada" y enviar al usuario un correo con el comprobante. | HU-13 CA1 | Alta |
| RF-E-02 | Al confirmarse la reserva, enviar al arrendador un correo con fecha, horario y espacio. | HU-13 CA2 | Alta |
| RF-E-03 | Ante un pago rechazado, mostrar un mensaje genérico y permitir reintentar mientras la reserva siga vigente. | HU-13 CA3 | Alta |
| RF-E-04 | Hacer que el checkout expire 1 minuto antes que la reserva (margen configurable, RN-08), no iniciar un pago en ese último minuto y permitir reservar de nuevo. | HU-13 CA4 | Alta |
| RF-E-05 | Solicitar el reembolso a la pasarela contra el pago original y pasar la transacción a "reembolsada" al confirmarse. | HU-16 CA1 | Alta |
| RF-E-06 | Si la pasarela falla, dejar la transacción en "reembolso fallido", mostrarla en el panel del admin y permitirle reintentar con un botón. | HU-16 CA2 | Alta |
| RF-E-07 | Permitir al arrendador con identidad verificada registrar una cuenta bancaria a su nombre o al de su RUC, disponible para liquidaciones. | HU-18 CA1 | Media |
| RF-E-08 | Al cambiar la cuenta bancaria (confirmando con contraseña), avisar por correo y retener la siguiente liquidación 72 h. | HU-18 CA2 | Media |
| RF-E-09 | Impedir registrar cuenta bancaria si la identidad no está verificada. | HU-18 CA3 | Media |
| RF-E-10 | Permitir al admin generar la liquidación semanal: una por arrendador, con las reservas usadas hace más de 48 h y no liquidadas, con monto bruto, comisión (10 %) y neto. | HU-19 CA1 | Media |
| RF-E-11 | Permitir al admin marcar la liquidación como "pagada" con el número de operación; el arrendador la ve en "Mis liquidaciones". | HU-19 CA2 | Media |
| RF-E-12 | Dejar en "pendiente de cuenta" la liquidación de un arrendador sin cuenta bancaria. | HU-19 CA3 | Media |
| RF-E-13 | Mostrar al usuario su historial de transacciones con fecha, espacio, monto y estado (pagado, reembolsado). | HU-28 CA1 | Media |
| RF-E-14 | Permitir descargar el comprobante de una transacción en PDF. | HU-28 CA2 | Media |

### Épica F. Panel de administración

| ID | El sistema debe… | Origen | Prioridad |
| --- | --- | --- | --- |
| RF-F-01 | Mostrar al admin la cola de arrendadores en "identidad en revisión" con sus datos y documentos, del más antiguo al más reciente. | HU-06 CA1 | Alta |
| RF-F-02 | Permitir aprobar a un arrendador: pasa a "identidad verificada" y recibe un correo de que ya puede publicar. | HU-06 CA2 | Alta |
| RF-F-03 | Permitir rechazar a un arrendador con motivo obligatorio: pasa a "identidad rechazada" y recibe un correo con el motivo. | HU-06 CA3 | Alta |
| RF-F-04 | Permitir buscar una cuenta por correo o nombre y ver sus datos, rol y estado. | HU-09 CA1 | Alta |
| RF-F-05 | Permitir suspender una cuenta con motivo: se invalidan sus sesiones, no puede iniciar sesión, se ocultan sus espacios y recibe un correo con el motivo. | HU-09 CA2 | Alta |
| RF-F-06 | Permitir reactivar una cuenta suspendida: recupera el acceso y sus espacios vuelven a ser visibles. | HU-09 CA3 | Alta |
| RF-F-07 | Permitir al usuario abrir un reclamo (motivo y descripción) sobre una reserva de hace menos de 48 h: queda en "reclamo abierto", el arrendador puede responder y la reserva sale de toda liquidación hasta resolverse. | HU-26 CA1 | Media |
| RF-F-08 | Permitir al admin resolver el reclamo a favor del usuario, disparando un reembolso total o parcial y avisando a ambos por correo. | HU-26 CA2 | Media |
| RF-F-09 | Permitir al admin resolver a favor del arrendador con motivo: la reserva vuelve a ser liquidable y ambos reciben correo. | HU-26 CA3 | Media |
| RF-F-10 | Permitir a un usuario autenticado reportar un espacio o reseña eligiendo un motivo; el reporte entra a la cola de moderación. | HU-27 CA1 | Media |
| RF-F-11 | Mostrar al admin el contenido y motivo del reporte y permitirle mantenerlo u ocultarlo con un motivo. | HU-27 CA2 | Media |
| RF-F-12 | Al ocultar una publicación, avisar al arrendador con el motivo y retirar el espacio del catálogo. | HU-27 CA3 | Media |

## 4. Requerimientos no funcionales

### 4.1 Seguridad (STRIDE)

| ID | Requerimiento | Origen |
| --- | --- | --- |
| RNF-SEG-01 | **(S)** La identidad se toma siempre de la sesión en el backend, nunca de un ID enviado por el cliente (reservas, consultas, perfil, recordatorios). | §4, HU-12 (S/E), HU-14 (E/I), HU-20 (E), HU-25 (E) |
| RNF-SEG-02 | **(S)** Los tokens se invalidan en el backend al cerrar sesión, expirar (30 min), cambiar la contraseña o suspender la cuenta. | §4, HU-03 (S), HU-04 CA3, HU-09 CA2 |
| RNF-SEG-03 | **(S)** Reautenticación con contraseña para cambiar correo, facturación o cuenta bancaria y para exportar o eliminar datos. | HU-18 (S/T), HU-20 (S), HU-22 (S) |
| RNF-SEG-04 | **(S)** MFA TOTP estándar (RFC 6238), obligatorio para admin; secretos cifrados y códigos de respaldo guardados con hash. Hasta HU-17, el panel admin solo es accesible desde IPs permitidas. | HU-17 (S/I), HU-06 (S), HU-09 (S), HU-27 (E/S) |
| RNF-SEG-05 | **(T)** Contraseñas con hash y sal (bcrypt o argon2). | §4, HU-01 (T) |
| RNF-SEG-06 | **(T)** Tokens de recuperación aleatorios, de un solo uso y guardados con hash. | HU-04 (S/T) |
| RNF-SEG-07 | **(T)** Todo lo que viene del cliente se valida en el servidor; los montos (reserva, reembolso, liquidación) se calculan solo ahí. | §3 regla 6, §4, HU-12, HU-13, HU-15, HU-19 (T) |
| RNF-SEG-08 | **(T)** Archivos validados por tipo real y tamaño; imágenes reprocesadas para eliminar metadatos. | HU-05 (T), HU-07 (T/I) |
| RNF-SEG-09 | **(T)** Restricción única por espacio y bloque en la base de datos contra la doble reserva. | HU-12 (T) |
| RNF-SEG-10 | **(T)** Filtros de búsqueda validados contra una lista blanca; texto de reseñas sanitizado contra inyección de scripts. | HU-23 (T), HU-24 (T) |
| RNF-SEG-11 | **(T)** Webhook de pago con firma verificada e idempotente; reembolsos idempotentes y solo contra el pago original. | §4, HU-13 (S/T), HU-16 (T/I) |
| RNF-SEG-12 | **(R)** Componente de auditoría reutilizable que registra quién, qué, cuándo e IP en las acciones sensibles: registro (con versión de la política aceptada), accesos, recuperación, cargas de documentos, decisiones de identidad, suspensiones, cambios de espacios y disponibilidad, reservas, transacciones, cancelaciones, reembolsos, cuenta bancaria, liquidaciones, cambios de perfil, MFA, exportación y eliminación, reseñas, reclamos y moderación. | §4, campo (R) de cada HU |
| RNF-SEG-13 | **(I)** TLS en toda la comunicación y mensajes de error genéricos (manejador global; el detalle va solo a los logs). Las respuestas no confirman si un correo existe. | §3, §4, HU-01 (I), HU-03 (I), HU-04 (I) |
| RNF-SEG-14 | **(I)** Datos sensibles cifrados en reposo: facturación, documentos, datos bancarios (mostrados enmascarados) y secretos MFA. | §4, HU-02 (I), HU-05 (I), HU-18 (I), HU-17 |
| RNF-SEG-15 | **(I)** Documentos en almacenamiento privado, servidos con URL firmada de corta duración. | §4, HU-05 (I), HU-06 (I) |
| RNF-SEG-16 | **(I)** Minimización de datos expuestos: la ficha y la búsqueda no muestran teléfono, correo ni documentos del arrendador; el arrendador no ve correo, teléfono ni datos de pago del usuario; los correos no incluyen tarjeta ni documentos. | HU-10 (I), HU-11 (I), HU-14 (I), HU-25 (I) |
| RNF-SEG-17 | **(D)** Rate limiting: registros por IP por hora; login 5 fallos / 15 min; recuperación 3 por correo / hora; búsqueda, ficha y filtros por IP (compartido); 3 pagos fallidos por reserva / 30 min; 3 reservas pendientes por usuario; intentos de código MFA por sesión; 1 reclamo por reserva; 10 reportes por usuario al día; contraseña de perfil (cambio de correo y facturación) 5 fallos / 15 min por cuenta; cambio de correo 3 por cuenta / hora. | §4, HU-01, 03, 04, 10, 11, 12, 13, 17, 20, 23, 26, 27 (D) |
| RNF-SEG-18 | **(E)** Roles asignados y validados en el backend en cada solicitud; el rol nunca se acepta del cliente; cuentas admin creadas solo por script interno. | §4, HU-02 (E), HU-06 (E), HU-09 (E), HU-19 (E) |
| RNF-SEG-19 | **(E)** Validación de propiedad del recurso antes de cada edición o consulta: espacios, calendario, documentos, reservas, cancelaciones, transacciones, comprobantes y reclamos. | §4, HU-05, 07, 08, 15, 21, 26, 28 (E) |

### 4.2 Cumplimiento normativo

| ID | Requerimiento | Origen |
| --- | --- | --- |
| RNF-CUM-01 | **PCI DSS:** la plataforma nunca recibe ni guarda datos de tarjeta; el pago se hace en el checkout de una pasarela certificada (modo de prueba); los comprobantes muestran solo los últimos 4 dígitos. | §1, §4, HU-13 (I/T), HU-28 (I) |
| RNF-CUM-02 | **PCI DSS:** el estado del pago cambia solo por webhook con firma verificada. | §4, HU-13 (S/T) |
| RNF-CUM-03 | **Datos personales:** consentimiento explícito al registrarse, guardando fecha, hora, IP y versión de la política aceptada. | HU-01 CA2, HU-01 (R), HU-02 CA1 |
| RNF-CUM-04 | **Datos personales:** derecho de acceso (exportación JSON) y de supresión (anonimización), conservando los registros contables el plazo legal. | HU-22 |

### 4.3 Arquitectura y mantenibilidad

| ID | Requerimiento | Origen |
| --- | --- | --- |
| RNF-ARQ-01 | Backend en Spring Boot (Java) y frontend en React (SPA). Base de datos **PostgreSQL 17** con migraciones Flyway. | §3, `architecture.md` (Stack) |
| RNF-ARQ-02 | Arquitectura en capas `Controller → Service → Repository` con entidades en el núcleo; el núcleo no depende de la web. | §3 reglas 1-2 |
| RNF-ARQ-03 | Las entidades nunca salen del backend: la API recibe y devuelve DTO con solo los campos necesarios. | §3 regla 3 |
| RNF-ARQ-04 | Un paquete por módulo (`cuentas`, `espacios`, `busqueda`, `reservas`, `pagos`, `administracion`), cada uno con `controller`, `dto`, `service`, `domain` y `repository`, más el paquete transversal `comun`. | §3 regla 4, `architecture.md` (Principio 4) |
| RNF-ARQ-05 | SOLID, alta cohesión y bajo acoplamiento; DAO/Repository en toda la persistencia; patrones por historia según la sección 7 (columna Patrón). | §3 regla 5, §10 |
| RNF-ARQ-06 | Convenciones: dominio en español con sufijos técnicos en inglés; endpoints REST en plural y minúsculas. | §3 |
| RNF-ARQ-07 | Configuración y secretos en variables de entorno, con `.env.example`; nunca se suben credenciales ni llaves. | §3 |
| RNF-ARQ-08 | Los proveedores externos (pasarela, correo) se usan detrás de interfaces propias (Adapter), para cambiarlos sin tocar el dominio. | §10 (HU-13, HU-16, HU-25) |

### 4.4 Usabilidad y capacidad

| ID | Requerimiento | Origen |
| --- | --- | --- |
| RNF-USA-01 | Resultados de búsqueda paginados de 20 en 20. | HU-10 CA2 |
| RNF-USA-02 | Hasta 5 fotos por espacio; documentos en PDF/JPG/PNG de hasta 5 MB. | HU-05 CA1, HU-07 |
| RNF-USA-03 | Mensajes de error claros para validaciones de formato (RUC/DNI, archivos, contraseña) y genéricos cuando se trata de seguridad. | HU-01 CA3, HU-02 CA2, HU-05 CA2 |
| RNF-USA-04 | Avisos al usuario por correo en los eventos clave (verificación, identidad, pago, cancelación, reembolso, suspensión, reclamo, moderación, recordatorio). | Varias HU |

### 4.5 Calidad y proceso

| ID | Requerimiento | Origen |
| --- | --- | --- |
| RNF-CAL-01 | Al menos una prueba unitaria por criterio de aceptación (incluido el de error) y pruebas de integración aprobadas. | §8 |
| RNF-CAL-02 | Casos de caja negra con capturas nombradas `HU-XX-E01`, `HU-XX-E02`… | §8 |
| RNF-CAL-03 | Despliegue en UAT y validación del PO antes de dar la historia por terminada. | §8 (DoD) |
| RNF-CAL-04 | Ningún cambio entra a `develop` sin un pull request revisado por la pareja; ramas `feature/HU-XX-descripcion-corta`. | §8 |

## 5. Reglas de negocio

| ID | Regla | Origen |
| --- | --- | --- |
| RN-01 | **Estados del arrendador:** correo verificado (inicia sesión, crea borradores, sube documentos) → identidad en revisión → identidad verificada (publica, recibe reservas pagadas, registra cuenta bancaria) o identidad rechazada (vuelve a subir documentos). Suspendida: no inicia sesión y sus espacios se ocultan. Nunca se dice "verificado" a secas. | §5, HU-02, HU-05, HU-06, HU-09 |
| RN-02 | Cambiar el RUC/DNI con identidad verificada devuelve la identidad a "en revisión". Cambiar la razón social también (HU-20 D9: es el nombre que el admin verificó y el titular que copia HU-18); cambiar solo la dirección fiscal no. | HU-20 CA3 |
| RN-03 | **Estados de la reserva:** pendiente de pago (retiene los bloques 15 min) → confirmada (pago aprobado) o expirada (sin pago a tiempo); confirmada → cancelada. | §5, HU-12, HU-13, HU-15 |
| RN-04 | **Disponibilidad:** horario semanal en bloques de 1 h, reservable en las próximas 8 semanas; bloques libres = horario − bloqueos − reservas, calculados al consultar. No se bloquea una fecha con reserva pagada. | §5, HU-08 |
| RN-05 | Máximo 3 reservas pendientes simultáneas por usuario; una reserva abarca uno o más bloques consecutivos. | HU-12 |
| RN-06 | **Política de cancelación fija:** usuario con 48 h o más → 100 %; usuario con menos de 48 h → 0 %; arrendador cancela → 100 %. No es configurable. | §5, HU-15 |
| RN-07 | **Estados de la transacción:** pagada → reembolsada, o reembolso fallido (el admin puede reintentarlo). | §5, HU-16 |
| RN-08 | El enlace del checkout vence 1 minuto antes que la reserva, y en ese último minuto no se inicia un pago. La notificación de la pasarela es asíncrona, así que un pago puede aprobarse y llegar con la reserva ya no vigente: se registra como `PAGADO_TARDIO`, se audita, no confirma la reserva y su devolución es manual (HU-13 D2, D3). | §5, §6, HU-13 CA4 |
| RN-09 | Editar el precio de un espacio no afecta a las reservas ya pagadas. | HU-07 CA4 |
| RN-10 | **Liquidación:** la genera el admin con un botón; incluye reservas usadas hace más de 48 h y no liquidadas; comisión de la plataforma 10 %; estados pagada (con número de operación) o pendiente de cuenta. La transferencia es manual. | §5, HU-19 |
| RN-11 | Tras cambiar la cuenta bancaria, la siguiente liquidación se retiene 72 h. | HU-18 CA2 |
| RN-12 | Una reserva con reclamo abierto no entra en ninguna liquidación hasta resolverse; un reclamo por reserva y solo si la reserva fue hace menos de 48 h. | HU-26 |
| RN-13 | Una reseña por reserva, solo de reservas confirmadas ya pasadas y dentro de los 14 días siguientes; calificación de 1 a 5. | HU-24 |
| RN-14 | Recordatorio 24 h antes de cada reserva confirmada; solo el recordatorio se puede desactivar, los correos de pago y cancelación no. | HU-25 |
| RN-15 | La búsqueda solo muestra espacios publicados, no pausados ni ocultos, de arrendadores no suspendidos. | HU-10 CA1, HU-21 CA1, HU-09 CA2, HU-27 CA3 |
| RN-16 | Para dar de baja un espacio con reservas futuras hay que cancelarlas antes (reembolso 100 %). | HU-21 CA2 |
| RN-17 | Para eliminar la cuenta no debe haber reservas futuras ni liquidaciones pendientes. | HU-22 CA2, CA3 |
| RN-18 | Precio único por hora (sin franjas). | §6 |

## 6. Fuera del alcance

No son requerimientos y no se implementan (detalle en `architecture.md`, sección "Qué NO hacer"): galería de más de 5 fotos y video; precios por franja horaria; mapa interactivo; favoritos; reprogramar reservas; panel de ingresos y ocupación; destacar publicaciones; billeteras digitales, transferencias y cuotas; notificaciones push; política de cancelación configurable; liquidación automática; reembolso de pagos tras expirar la reserva; respuesta del arrendador a reseñas y fotos en reclamos.

## 7. Matriz de trazabilidad

Las columnas **Pruebas unitarias**, **Capturas** y **Estado** las llena la pareja que prueba la historia, con datos reales.

| HU | Épica | R · Sprint (sem.) | RF | RNF principales | RN | Depende de | Programa | Prueba | Patrón | Pruebas unitarias | Capturas | Estado |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [HU-01](historias/HU-01.md) | A | R1 · S1 (8) | RF-A-01…03 | SEG-05, 12, 13, 17; CUM-03 | — | — | Jorge Quispe | Kevin Chan | — | | | |
| [HU-02](historias/HU-02.md) | A | R1 · S1 (8) | RF-A-04…06 | SEG-12, 14, 18; CUM-03 | RN-01 | HU-01 | Adriana Callupe | Sebastian Garate | — | | | |
| [HU-03](historias/HU-03.md) | A | R1 · S1 (8) | RF-A-07…10 | SEG-02, 12, 13, 17 | — | HU-01, HU-02 | Jorge Quispe | Kevin Chan | — | | | |
| [HU-04](historias/HU-04.md) | A | R1 · S1 (8) | RF-A-11…13 | SEG-02, 06, 12, 13, 17 | — | HU-03 | Eduardo Saldaña | Ariel Enriquez | — | | | |
| [HU-05](historias/HU-05.md) | A | R1 · S2 (8) | RF-A-14…16 | SEG-08, 12, 14, 15, 19 | RN-01 | HU-02 | Kevin Chan | Jorge Quispe | — | | | |
| [HU-06](historias/HU-06.md) | F | R1 · S2 (8) | RF-F-01…03 | SEG-04, 12, 15, 18 | RN-01 | HU-05 | Ariel Enriquez | Eduardo Saldaña | — | | | |
| [HU-07](historias/HU-07.md) | B | R1 · S2 (8) | RF-B-01…04 | SEG-08, 12, 19 | RN-09, RN-18 | HU-02 (visible tras HU-06) | Sebastian Garate | Adriana Callupe | — | | | |
| [HU-08](historias/HU-08.md) | B | R1 · S2 (8) | RF-B-05…07 | SEG-12, 19 | RN-04 | HU-07 | Sebastian Garate | Adriana Callupe | — | | | |
| [HU-09](historias/HU-09.md) | F | R1 · S3 (9) | RF-F-04…06 | SEG-02, 04, 12, 18 | RN-01, RN-15 | HU-06 | Jorge Quispe | Kevin Chan | — | | | |
| [HU-10](historias/HU-10.md) | C | R1 · S3 (9) | RF-C-01…03 | SEG-16, 17; USA-01 | RN-04, RN-15 | HU-07, HU-08 | Sebastian Garate | Adriana Callupe | — | | | |
| [HU-11](historias/HU-11.md) | C | R1 · S3 (9) | RF-C-04, 05 | SEG-16, 17 | RN-04, RN-06 | HU-07, HU-08 | Adriana Callupe | Sebastian Garate | — | | | |
| [HU-12](historias/HU-12.md) | D | R1 · S3 (9) | RF-D-01…04 | SEG-01, 07, 09, 12, 17 | RN-03, RN-05 | HU-03, HU-08, HU-11 | Eduardo Saldaña | Ariel Enriquez | — | | | |
| [HU-13](historias/HU-13.md) ★ | E | R1 · S4 (9) | RF-E-01…04 | SEG-07, 11, 12, 17; CUM-01, 02; ARQ-08 | RN-03, RN-08 | HU-12 | Eduardo Saldaña | Ariel Enriquez | Adapter, Facade | | | |
| [HU-14](historias/HU-14.md) ★ | D | R1 · S4 (9) | RF-D-05, 06 | SEG-01, 16 | RN-03 | HU-12, HU-13 | Adriana Callupe | Sebastian Garate | Iterator, DAO | | | |
| [HU-15](historias/HU-15.md) ★ | D | R1 · S4 (9) | RF-D-07…09 | SEG-07, 12, 19 | RN-03, RN-06 | HU-13, HU-14 | Kevin Chan | Jorge Quispe | Strategy | | | |
| [HU-16](historias/HU-16.md) ★ | E | R1 · S4 (9) | RF-E-05, 06 | SEG-11, 12; ARQ-08 | RN-07 | HU-13, HU-15 | Ariel Enriquez | Eduardo Saldaña | Command, Adapter | | | |
| [HU-17](historias/HU-17.md) | A | R2 · S5 (10) | RF-A-17…19 | SEG-04, 12, 14, 17 | — | HU-03 | Jorge Quispe | Kevin Chan | — | | | |
| [HU-18](historias/HU-18.md) | E | R2 · S5 (10) | RF-E-07…09 | SEG-03, 12, 14 | RN-01, RN-11 | HU-06 | Ariel Enriquez | Eduardo Saldaña | — | | | |
| [HU-19](historias/HU-19.md) | E | R2 · S5 (10) | RF-E-10…12 | SEG-07, 12, 18 | RN-10, RN-11 | HU-13, HU-18 | Eduardo Saldaña | Ariel Enriquez | — | | | |
| [HU-20](historias/HU-20.md) | A | R2 · S6 (10) | RF-A-20…22 | SEG-01, 03, 12 | RN-02 | HU-03 | Adriana Callupe | Sebastian Garate | — | | | |
| [HU-21](historias/HU-21.md) | B | R2 · S6 (10) | RF-B-09, 10 | SEG-12, 19 | RN-15, RN-16 | HU-07, HU-15 | Sebastian Garate | Adriana Callupe | — | | | |
| [HU-22](historias/HU-22.md) | A | R2 · S6 (10) | RF-A-23…25 | SEG-03, 12; CUM-04 | RN-17 | HU-01, HU-19, HU-20 | Kevin Chan | Jorge Quispe | — | | | |
| [HU-23](historias/HU-23.md) ★ | C | R2 · S7 (11) | RF-B-08, RF-C-06, 07 | SEG-10, 17 | RN-15 | HU-07, HU-10 | Sebastian Garate | Adriana Callupe | Decorator | | | |
| [HU-24](historias/HU-24.md) | C | R2 · S7 (11) | RF-C-08…10 | SEG-10, 12 | RN-13 | HU-14 | Adriana Callupe | Sebastian Garate | — | | | |
| [HU-25](historias/HU-25.md) ★ | D | R2 · S7 (11) | RF-D-10, 11, RF-A-26 | SEG-01, 16; ARQ-08 | RN-14 | HU-13 | Jorge Quispe | Kevin Chan | Factory Method, Adapter | | | |
| [HU-26](historias/HU-26.md) | F | R2 · S8 (11) | RF-F-07…09 | SEG-12, 17, 19 | RN-12 | HU-14, HU-16, HU-19 | Ariel Enriquez | Eduardo Saldaña | — | | | |
| [HU-27](historias/HU-27.md) | F | R2 · S8 (11) | RF-F-10…12 | SEG-04, 12, 17 | RN-15 | HU-09, HU-17, HU-24 | Kevin Chan | Jorge Quispe | — | | | |
| [HU-28](historias/HU-28.md) | E | R2 · S8 (11) | RF-E-13, 14 | SEG-19; CUM-01 | RN-07 | HU-13, HU-16 | Eduardo Saldaña | Ariel Enriquez | — | | | |

★ = se documenta a fondo en el capítulo 8 del informe y sale en el video de su release.

## 8. Componentes transversales

Piezas que implementan varios RNF a la vez. Conviene construirlas una vez, en el primer sprint que las necesita, y reutilizarlas.

| Componente | Cubre | Primera historia que lo necesita | Lo reutilizan |
| --- | --- | --- | --- |
| Auditoría (quién, qué, cuándo, IP) | RNF-SEG-12 | HU-01, HU-03 (Sprint 1) | Casi todas las historias |
| Rate limiting | RNF-SEG-17 | HU-01, HU-03, HU-04 (Sprint 1) | HU-10, 11, 13, 17, 23, 26, 27. El cupo de 3 reservas pendientes de HU-12 es una regla de negocio y no usa `LimitadorIntentos`: ese componente cuenta eventos en una ventana por IP, y el cupo es por usuario y se libera al expirar o pagar. |
| Manejador global de errores con mensajes genéricos | RNF-SEG-13 | Sprint 1 | Todo el backend |
| Gestión de sesión y tokens (invalidación, 30 min) | RNF-SEG-01, 02 | HU-03 (Sprint 1) | HU-04, HU-09, HU-17 |
| Envío de correo detrás de una interfaz propia | RNF-ARQ-08, USA-04 | HU-01 (Sprint 1, verificación) | HU-02, 04, 06, 09, 13, 15, 18, 20, 25, 26, 27. HU-25 formaliza Factory Method + Adapter. |
| Almacenamiento privado cifrado con URL firmada | RNF-SEG-08, 14, 15 | HU-05 (Sprint 2) | HU-06. HU-07 reutiliza solo la validación y re-codificación (sin EXIF/GPS) y el adaptador de disco: las fotos de espacios son públicas, sin cifrar y con URL estable (las de borradores, solo para su dueño). |
| Cálculo de disponibilidad (horario − bloqueos − reservas) | RN-04 | HU-08 (Sprint 2) | HU-10, 11, 12 |
| Adaptador de la pasarela de pago | RNF-SEG-11, CUM-01, 02 | HU-13 (Sprint 4) | HU-16, HU-26, HU-28 |
| Cifrado de campos sensibles en reposo | RNF-SEG-14 | HU-02 (Sprint 1, facturación) | HU-05, 17, 18 |

## 9. Pendientes y observaciones para el equipo

1. ~~**Base de datos por confirmar**~~ **Resuelto:** PostgreSQL 17 (índices únicos parciales para RNF-SEG-09 y transacciones ACID para RF-D-03).
2. ~~**Estado "correo pendiente"**~~ **Resuelto:** ya está en la tabla de estados de `architecture.md`.
3. **Respuesta del arrendador en reclamos** (HU-26 CA1): se menciona que "el arrendador puede responder", pero no hay un criterio que diga cómo se ve ni qué se valida. Aclararlo con el PO antes del Sprint 8.
4. ~~**Control compensatorio de IPs del panel admin**~~ **Resuelto (Ariel Enriquez, 2026-10-28, HU-06 D2):** filtro del backend sobre `/api/admin/**` con la lista `ADMIN_IPS_PERMITIDAS` (IPs o rangos CIDR; vacía = nadie entra) y el inicio de sesión del admin solo desde esas IPs. HU-09 y HU-27 reutilizan el mismo filtro. **HU-17 (Jorge Quispe, 2026-11-03, P3):** se mantiene y complementa a MFA (protege además de un token de sesión robado); `ADMIN_IPS_PERMITIDAS=0.0.0.0/0,::/0` lo abre sin cambiar código.
5. ~~**Política de contraseña**~~ **Resuelto (Jorge Quispe, 2026-10-27):** longitud de 8 a 64 caracteres, al menos una letra y un dígito, sin otras reglas de composición; se rechaza una contraseña igual al correo.
6. **Plazo legal de conservación contable** (HU-22 CA2): falta el plazo concreto.
7. ~~**Límite de registros por IP**~~ **Resuelto (Jorge Quispe, 2026-10-27):** 5 intentos de registro por IP por hora (ventana deslizante), contando todos los intentos, no solo los exitosos; al superarlo, 429 con mensaje genérico. ~~**Límite de intentos MFA**~~ **Resuelto (Jorge Quispe, 2026-11-03, HU-17 DT-8):** 5 códigos incorrectos por desafío (al 5.º se cierra y hay que volver a poner la contraseña) y 10 fallos por cuenta en 15 minutos (ventana deslizante, compartida por el inicio de sesión, la activación y la desactivación); al superarlo, 429 con mensaje genérico. Un código aceptado reinicia el contador de la cuenta.
8. **Envío de recordatorios** (RF-D-10): requiere un proceso programado. La simplificación de §5 solo descarta jobs para la disponibilidad y la liquidación, no para este caso; confirmarlo en el diseño de HU-25. ~~**Expiración de reservas**~~ (RF-D-02) **Resuelto (Eduardo Saldaña, 2026-10-30, HU-12 D2):** tres capas. Las lecturas ignoran las reservas vencidas, un proceso programado cada minuto las pasa a `EXPIRADA` y audita (`RESERVAS_EXPIRACION_ACTIVA`, `RESERVAS_EXPIRACION_INTERVALO`), y al crear una reserva se liberan las vencidas que retienen esos bloques.
9. ~~**Límite de búsqueda, ficha y filtros por IP**~~ **Resuelto (Sebastian Garate, 2026-10-29, HU-10):** 120 peticiones por IP por minuto (ventana deslizante), en un solo cupo compartido por la búsqueda (HU-10), la ficha y su disponibilidad (HU-11) y los filtros (HU-23); al superarlo, 429 con mensaje genérico. Cifra y ventana configurables en `application.properties` (`LIMITE_CATALOGO_MAXIMO`, `LIMITE_CATALOGO_VENTANA`). No cuentan el catálogo de zonas ni las imágenes. HU-10 extiende `ReglaLimite` para aceptar patrones de ruta (p. ej. `/api/espacios/{id}`), de modo que HU-11 y HU-23 solo registran sus reglas en el mismo grupo.
10. **Mercado Pago sin conectar (HU-13, D8, Eduardo Saldaña, 2026-10-30):** UAT usa la pasarela simulada hasta conectarlo. T23 **no se ha ejecutado**: falta comprobar la **firma real de una notificación (S1)**, los **tipos de pago excluidos (S5)** y el **cierre del checkout (S6)**. Hasta entonces, RNF-CUM-01 y RNF-CUM-02 no se demuestran en UAT (C1 y C2 de `specs/HU-13/design.md` § 14.13). Guía de la prueba: `progress/impl_HU-13.md`.
