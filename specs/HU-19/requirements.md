# HU-19 — Requirements: Liquidación semanal al arrendador

> Fuente: `docs/historias/HU-19.md` (CA1–CA3, STRIDE T, E, R y la simplificación "botón del admin, transferencia
> manual"), `docs/requerimientos.md` (RF-E-10…12, RNF-SEG-07, 12, 18; relacionados RNF-SEG-01, 13, 14, 16, 19, RN-06,
> RN-10, RN-11, RN-12, RN-17), `docs/architecture.md` (módulo `pagos`, puerto reservado `pagos.ReservasLiquidables`,
> "Pagos: qué deja HU-13", "Reembolsos: qué deja HU-16", "Cuenta bancaria: qué deja HU-18", estados de la liquidación,
> "Qué NO hacer"), `docs/modelo-datos.md` (`liquidacion`, `liquidacion_detalle`), `specs/HU-13/`, `specs/HU-18/`,
> `progress/explore_HU-19.md` (P1–P18) y las **decisiones del dueño D1–D8** de `progress/HU-19.md` (2026-11-04).
> Notación EARS estricta (`docs/specs.md`). Los requirements hablan de la **API**; la presentación está en
> `design.md` § 15 Pantallas y las decisiones técnicas en `design.md` § 1.

## Decisiones del dueño (Eduardo Saldaña, 2026-11-04) — ya tomadas

| # | Decisión | Requirements |
|---|----------|--------------|
| D1 | Cada generación liquida **todo lo pendiente** (reservas usadas hace más de 48 h y no liquidadas). La semana o fecha de generación es solo informativa (`periodo`). | R7, R12, R17, R19 |
| D2 | Los pagos `sim-` se incluyen **solo** con `espacioya.pagos.pasarela=simulada` y se marcan como simulados en la API (y la UI) y en la auditoría. | R20, R21, R45 |
| D3 | La reserva cancelada por el usuario sin reembolso (< 48 h, RN-06 0 %) se liquida al arrendador cuando pasan 48 h desde el **fin** de la reserva. **⚠ Pendiente de confirmar con Ariel Enriquez (PO):** CA1 literal ("reservas usadas") la excluye. Si el PO no la acepta, se retira R11 y R10 pasa a excluir toda `CANCELADA`, antes de `in_progress` o en un cambio posterior. | R10, R11 |
| D4 | El admin ve el número de cuenta **completo** solo en el detalle de una liquidación por pagar (ruta `no-store`, cada consulta auditada); en listas, enmascarado. | R38, R39, R40, R41 |
| D5 | La retención de 72 h (RN-11) **bloquea** "marcar como pagada" con un mensaje que trae la fecha `retenerHasta`. | R28, R36 |
| D6 | Al arrendador suspendido o con identidad no verificada se le liquida igual; el admin ve un aviso. | R24, R37 |
| D7 | Una liquidación `PENDIENTE_CUENTA` se puede marcar pagada directamente si ahora hay cuenta (se revalida); las reservas nuevas van a una liquidación nueva. | R17, R26, R27 |
| D8 | Sin correos. HU-19 deja la consulta "¿tiene liquidaciones pendientes?" para HU-22. | R47, R48 |

## Supuestos técnicos (S1–S15) — el dueño los acepta al aprobar

> Las fuentes no los fijan y no son pendientes de `docs/requerimientos.md` §9. El spec adopta el valor indicado
> (con la recomendación del explorador cuando la había). Si el dueño no acepta alguno, cambian los `R<n>` citados
> **antes** de pasar a `in_progress`.

| # | Pregunta (explorador) | Valor del spec | Afecta |
|---|-----------------------|----------------|--------|
| S1 | P2 — Qué es "usada hace más de 48 h" y su borde | **Fin** de la reserva = último bloque + 1 h, en `America/Lima`, sobre **todas** sus filas de `reserva_bloque` (activas o no). Liquidable si `corte ≥ fin + 48 h` (borde inclusivo, al segundo). HU-26 debe abrir reclamos solo si `ahora < fin + 48 h`: las dos ventanas se tocan sin hueco ni solape. | R7, R8 |
| S2 | P4 — Base del bruto | `pago.monto` del pago `PAGADO` de la reserva (es igual a `reserva.monto_total` por `ReglasPago.estadoDeAprobado`): se lee dentro de `pagos`. | R13 |
| S3 | P6 — Reembolsos y estados de pago | Solo cuenta un pago `PAGADO` **sin ninguna fila** de `reembolso` (cualquier origen y estado). `PAGADO_TARDIO`, `MONTO_DISTINTO`, `RECHAZADO`, `REEMBOLSADO` y `REEMBOLSO_FALLIDO` no cuentan. Un reembolso parcial por reclamo (HU-26) deja la reserva fuera entera hasta que HU-26 decida otra cosa. | R9 |
| S4 | P7 — Comisión y redondeo | 10 % fijo (no configurable, como RN-06) sobre el **bruto total** de la liquidación, escala 2, `HALF_UP`; `neto = bruto − comisión`. No se guarda la tasa: un `CHECK` de la base la fija. | R13, R23 |
| S5 | P8 — Generar dos veces y concurrencia | Un segundo clic solo crea liquidaciones si hay reservas nuevas; si no, responde 200 con la lista vacía. Sin límite por periodo. Un arrendador sin reservas liquidables no recibe liquidación. Dos generaciones simultáneas se serializan con un bloqueo de PostgreSQL (`pg_advisory_xact_lock`) y `uq_liquidacion_detalle_reserva` es la última defensa. | R18, R22 |
| S6 | P9 — Forma del puerto | `pagos` parte de **sus** pagos `PAGADO` sin liquidar y le pregunta al puerto `pagos.ReservasLiquidables.describir(reservaIds)` por esas reservas; el puerto devuelve datos (arrendador, espacio, horario, fin, si su estado admite liquidación) y la regla de las 48 h vive en `pagos`. Se agrega `CatalogoEspaciosService.arrendadoresDe(espacioIds)` en `espacios`. | R7–R11 |
| S7 | P10 (complemento de D4) — Snapshot y cuenta retenida | Al marcar pagada se guardan en la liquidación el banco y el número **enmascarado** de la cuenta usada. Mientras la cuenta está retenida el detalle **no** entrega el número completo (no se puede pagar, D5), solo enmascarado. | R25, R38 |
| S8 | P14 — Número de operación | Obligatorio, de 4 a 30 caracteres entre letras, dígitos y guiones (el cliente quita espacios extremos). No es único. Inmutable: una `PAGADA` no se corrige ni se "despaga". La fecha de pago y quién pagó los pone el servidor. | R25, R29, R30 |
| S9 | P15 — Listas y paginación | Admin: grupos `por-pagar` (`GENERADA` y `PENDIENTE_CUENTA`, de la más antigua a la más reciente) y `pagadas` (de la más reciente a la más antigua), de 20 en 20 con `hayMas`; detalle con las reservas. Arrendador: sus liquidaciones de la más reciente a la más antigua, de 20 en 20 con `hayMas`, cada una con sus reservas. Sin totales de ingresos (fuera del alcance). | R33–R36, R42 |
| S10 | P17 — Reclamos (HU-26) | HU-19 **no** define `pagos.ReclamosAbiertos`; lo agrega HU-26 como un filtro más del paso de selección (design § 14). | — |
| S11 | P18 — Detalle de líneas | `liquidacion_detalle` guarda `reserva_id` (único), `pago_id` (único), `monto` y `simulado`. | R12, R23 |
| S12 | D6 — Cómo se calcula el aviso | Al leer (no se guarda): `CuentaService.arrendadoresNoActivos()` → `CUENTA_NO_ACTIVA` y método nuevo `CuentaService.arrendadoresSinIdentidadVerificada(ids)` → `IDENTIDAD_NO_VERIFICADA`. La auditoría de la generación guarda los avisos de ese momento. | R24, R37, R45 |
| S13 | D8 — Qué es "pendiente" para HU-22 | Una liquidación `GENERADA` o `PENDIENTE_CUENTA`. Las reservas pagadas aún no liquidadas **no** cuentan; si HU-22 las quiere, lo decide su spec. | R47 |
| S14 | Código de la generación | `POST` responde **200** (no 201) con lo creado, también si no creó nada: es una acción por lotes, como el reintento de HU-16 (DT-8). | R18, R19 |
| S15 | Nombre del arrendador para el admin | `CuentaService.nombres(ids)` (HU-14); si no tiene nombre, "Arrendador #<id>". Nunca correo, teléfono ni documento. | R19, R33, R36 |

## Definiciones

- **Admin:** sesión vigente con `rol = ADMIN`, desde una IP permitida (`FiltroIpsAdmin`) y con MFA (HU-17).
- **Arrendador:** sesión vigente con `rol = ARRENDADOR`.
- **Corte:** el instante (`Instant.now(reloj)`) que la generación toma **una sola vez**, después de obtener el bloqueo
  de generación (R22). Todas las reglas de tiempo de esa generación usan el mismo corte.
- **Periodo:** la fecha del corte en `America/Lima`. Es informativo (D1).
- **Fin de una reserva:** S1.
- **Pago simulado:** pago cuyo `id_transaccion_pasarela` empieza por `sim-` (HU-13 RS-5).
- **Reserva liquidable al corte:** reserva que cumple **todo** lo siguiente:
  1. tiene un pago `PAGADO` sin ninguna fila en `reembolso` (S3);
  2. ese pago no es simulado, o la pasarela configurada es `simulada` (D2);
  3. no aparece en `liquidacion_detalle` (CA1 "no liquidadas antes");
  4. su estado en `reserva` es `CONFIRMADA`, o es `CANCELADA` con `cancelada_por = USUARIO` y `monto_reembolso = 0`
     (D3 ⚠);
  5. `corte ≥ fin + 48 h` (S1).
- **Cuenta vigente:** la fila de `cuenta_bancaria` del arrendador (HU-18). **Retenida:** `CuentaBancaria.estaRetenida(ahora)`.
- **Liquidación por pagar:** estado `GENERADA` o `PENDIENTE_CUENTA`. **Pagable:** por pagar, con cuenta vigente y no
  retenida.
- **Número enmascarado:** `****` y los 4 últimos dígitos (`CuentaBancaria.enmascarado()`).
- **Avisos del arrendador:** `CUENTA_NO_ACTIVA` (su `usuario.estado` no es `ACTIVA`) e `IDENTIDAD_NO_VERIFICADA` (su
  `estado_identidad` no es `VERIFICADA`), calculados al leer (S12).
- **Mensajes:** texto exacto en `design.md` § 4.

## Acceso y roles (STRIDE E, S)

## R1
SI una solicitud a cualquier ruta de `/api/admin/liquidaciones` llega sin sesión vigente ENTONCES el sistema DEBE
responder 401 sin leer ni escribir `liquidacion`, `liquidacion_detalle` ni `auditoria`.

## R2
SI una sesión vigente cuyo rol no es `ADMIN` envía una solicitud a cualquier ruta de `/api/admin/liquidaciones`
ENTONCES el sistema DEBE responder 403 sin leer ni escribir `liquidacion`, `liquidacion_detalle`, `cuenta_bancaria` ni
`auditoria`.

## R3
SI una solicitud a cualquier ruta de `/api/admin/liquidaciones` llega desde una IP fuera de `ADMIN_IPS_PERMITIDAS`
ENTONCES el sistema DEBE responder 403 con el mensaje genérico sin crear ni modificar ninguna liquidación.

## R4
SI una solicitud a `/api/arrendadores/actual/liquidaciones` llega sin sesión vigente ENTONCES el sistema DEBE responder
401 sin leer `liquidacion`.

## R5
SI una sesión vigente cuyo rol no es `ARRENDADOR` envía `GET /api/arrendadores/actual/liquidaciones` ENTONCES el
sistema DEBE responder 403 antes de validar los parámetros y sin leer `liquidacion`.

## R6
El sistema DEBE tomar la identidad del arrendador siempre de la sesión: `GET /api/arrendadores/actual/liquidaciones` no
acepta un id de arrendador en la URL ni en los parámetros y solo devuelve liquidaciones cuyo `arrendador_id` es el de
la sesión.

## Generación (CA1, CA3, D1, D2, D3, D6, D7)

## R7
CUANDO el admin envía `POST /api/admin/liquidaciones`, el sistema DEBE crear una liquidación por cada arrendador que
tenga al menos una reserva liquidable al corte, con **todas** sus reservas liquidables como líneas (CA1, D1).

## R8
El sistema NO DEBE incluir en una liquidación una reserva cuyo fin más 48 horas sea posterior al corte; una reserva
cuyo fin más 48 horas es igual al corte sí se incluye (S1).

## R9
El sistema NO DEBE incluir en una liquidación una reserva que no tenga un pago `PAGADO`, ni una cuyo pago `PAGADO`
tenga alguna fila en `reembolso` (S3).

## R10
El sistema NO DEBE incluir en una liquidación una reserva `PENDIENTE_PAGO` o `EXPIRADA`, ni una `CANCELADA` por el
arrendador o con `monto_reembolso` mayor que 0 o nulo (D3 ⚠).

## R11
CUANDO una reserva `CANCELADA` por el usuario con `monto_reembolso = 0` cumple las demás condiciones de reserva
liquidable al corte, el sistema DEBE incluirla en la liquidación de su arrendador con su línea marcada como cancelada
(D3 ⚠ pendiente de confirmar con el PO).

## R12
El sistema NO DEBE incluir en una liquidación una reserva que ya figura en `liquidacion_detalle`, cualquiera que sea el
estado de esa liquidación anterior (CA1 "no liquidadas antes").

## R13
CUANDO el sistema crea una liquidación, el sistema DEBE calcular en el servidor el monto de cada línea igual al
`pago.monto` de su pago `PAGADO`, el monto bruto igual a la suma de las líneas, la comisión igual al 10 % del bruto
redondeado a 2 decimales con `HALF_UP` y el neto igual al bruto menos la comisión (CA1, STRIDE T, S2, S4).

## R14
El sistema NO DEBE leer del cliente montos, arrendadores, reservas, estados ni fechas al generar: `POST
/api/admin/liquidaciones` ignora cualquier cuerpo y el resultado es el mismo que sin cuerpo (STRIDE T, RNF-SEG-07).

## R15
CUANDO el sistema crea la liquidación de un arrendador que tiene cuenta vigente, el sistema DEBE dejarla en estado
`GENERADA`, aunque la cuenta esté retenida (CA1, D5).

## R16
CUANDO el sistema crea la liquidación de un arrendador que no tiene cuenta vigente, el sistema DEBE dejarla en estado
`PENDIENTE_CUENTA` (CA3).

## R17
CUANDO un arrendador con una liquidación `GENERADA` o `PENDIENTE_CUENTA` tiene reservas liquidables nuevas al corte, el
sistema DEBE crear para ellas una liquidación nueva sin modificar las líneas, los montos ni el estado de la existente
(D1, D7).

## R18
CUANDO el admin genera y no hay ninguna reserva liquidable al corte, el sistema DEBE responder 200 con la lista de
liquidaciones vacía sin crear ninguna fila en `liquidacion` ni en `liquidacion_detalle` (S5, S14).

## R19
CUANDO el sistema termina una generación, el sistema DEBE responder 200 con el corte y, por cada liquidación creada,
su id, el arrendador (id y nombre), el periodo (fecha del corte en Lima), el estado, el bruto, la comisión, el neto,
la cantidad de reservas, si es simulada y los avisos del arrendador (D1, S14, S15).

## R20
DONDE la pasarela configurada es `simulada`, el sistema DEBE incluir los pagos simulados y marcar como simuladas su
línea y la liquidación que los contiene (D2).

## R21
DONDE la pasarela configurada no es `simulada`, el sistema NO DEBE incluir en una liquidación ninguna reserva cuyo pago
`PAGADO` sea simulado (D2, HU-13 RS-5).

## R22
SI dos generaciones llegan a la vez ENTONCES el sistema DEBE serializarlas de modo que ambas respondan 200, cada
reserva quede en una sola liquidación y la segunda solo cree liquidaciones con reservas que la primera no incluyó
(STRIDE T, S5).

## R23
El sistema DEBE impedir en la base de datos que una reserva o un pago figuren en más de una línea de liquidación
(`uq_liquidacion_detalle_reserva`, `uq_liquidacion_detalle_pago`), que una liquidación tenga un estado fuera de
`PENDIENTE_CUENTA`, `GENERADA` o `PAGADA`, y que sus montos no cumplan `comision = round(monto_bruto × 0.10, 2)` y
`monto_neto = monto_bruto − comision` (STRIDE T, S4, S11).

## R24
CUANDO un arrendador con reservas liquidables tiene la cuenta suspendida o la identidad no verificada, el sistema DEBE
crear igualmente su liquidación con el estado que corresponda según su cuenta bancaria (D6).

## Marcar como pagada (CA2, CA3, D5, D7)

## R25
CUANDO el admin envía `POST /api/admin/liquidaciones/{id}/pago` con un `numeroOperacion` válido para una liquidación
`GENERADA` cuyo arrendador tiene cuenta vigente no retenida, el sistema DEBE pasarla a `PAGADA` guardando el número de
operación, la fecha de pago del servidor, el admin que la marcó y el banco y el número enmascarado de esa cuenta, y
responder 200 con el detalle (CA2, S7, S8).

## R26
CUANDO el admin marca como pagada una liquidación `PENDIENTE_CUENTA` cuyo arrendador ahora tiene cuenta vigente no
retenida, el sistema DEBE pasarla directamente a `PAGADA` como en R25 (D7).

## R27
SI el admin marca como pagada una liquidación cuyo arrendador no tiene cuenta vigente ENTONCES el sistema DEBE
rechazarlo con 400 y el mensaje *sin cuenta*, sin modificar la liquidación ni auditar el pago (CA3, D7).

## R28
SI el admin marca como pagada una liquidación cuyo arrendador tiene la cuenta retenida ENTONCES el sistema DEBE
rechazarlo con 400 y el mensaje *cuenta retenida* con la fecha `retener_hasta` en hora de Lima, sin modificar la
liquidación ni auditar el pago (D5, RN-11).

## R29
SI el admin marca como pagada una liquidación ya `PAGADA` ENTONCES el sistema DEBE rechazarlo con 400 y el mensaje *ya
pagada*, sin cambiar su número de operación, su fecha de pago ni quién la marcó (S8).

## R30
SI el cuerpo de `POST /api/admin/liquidaciones/{id}/pago` no trae `numeroOperacion` o no son de 4 a 30 caracteres entre
letras, dígitos y guiones ENTONCES el sistema DEBE rechazarlo con 400 y un error en el campo `numeroOperacion`, sin leer
ni modificar la liquidación (S8).

## R31
SI el `{id}` de `GET /api/admin/liquidaciones/{id}` o de `POST /api/admin/liquidaciones/{id}/pago` no es un id bien
escrito (1 a 19 dígitos, mayor que 0) o no existe ENTONCES el sistema DEBE responder 404 con el mensaje genérico, sin
escribir nada.

## R32
SI dos solicitudes marcan como pagada la misma liquidación a la vez ENTONCES el sistema DEBE aceptar solo una (200) y
rechazar la otra con 400 *ya pagada*, conservando el número de operación de la aceptada (STRIDE T).

## Consulta del admin (D4, D6)

## R33
CUANDO el admin envía `GET /api/admin/liquidaciones` sin `grupo` o con `grupo=por-pagar`, el sistema DEBE responder 200
con las liquidaciones `GENERADA` y `PENDIENTE_CUENTA` ordenadas por `generada_en` e `id` ascendentes, de 20 en 20 según
`pagina` (1 por defecto) y con `hayMas` (S9).

## R34
CUANDO el admin envía `GET /api/admin/liquidaciones?grupo=pagadas`, el sistema DEBE responder 200 con las liquidaciones
`PAGADA` ordenadas por `pagada_en` e `id` descendentes, de 20 en 20 según `pagina` y con `hayMas`, cada una con su
número de operación y fecha de pago (S9).

## R35
SI `grupo` no es `por-pagar` ni `pagadas`, o `pagina` no es un entero de 1 en adelante ENTONCES el sistema DEBE
responder 400 con un error por cada parámetro inválido, sin consultar `liquidacion`.

## R36
CUANDO el admin envía `GET /api/admin/liquidaciones/{id}` de una liquidación existente, el sistema DEBE responder 200
con sus datos (arrendador, periodo, estado, montos, simulada), sus líneas (reserva, nombre del espacio, fecha, hora de
inicio y de fin, monto, si es simulada y si es una cancelada sin reembolso), la cuenta vigente del arrendador (banco,
titular, número enmascarado, estado `DISPONIBLE`/`RETENIDA` y `retenidaHasta`) o nula, si es pagable y, si está
`PAGADA`, el número de operación, la fecha de pago y el banco y la cuenta enmascarada a los que se pagó (D5, S7).

## R37
CUANDO el admin lista o consulta liquidaciones, el sistema DEBE incluir en cada una los avisos `CUENTA_NO_ACTIVA` e
`IDENTIDAD_NO_VERIFICADA` que apliquen a su arrendador en el momento de la consulta (D6, S12).

## R38
MIENTRAS una liquidación es pagable, el sistema DEBE incluir en su detalle (`GET /api/admin/liquidaciones/{id}`) el
número completo de la cuenta vigente del arrendador (D4, S7).

## R39
CUANDO el sistema entrega el número completo de una cuenta en el detalle de una liquidación, el sistema DEBE auditar
`CUENTA_BANCARIA_CONSULTADA_ADMIN` sobre la entidad `cuenta_bancaria` con el admin, la IP y, en el detalle, la
liquidación, el arrendador y el número enmascarado (D4, STRIDE R).

## R40
El sistema NO DEBE devolver el número completo de una cuenta en las listas del admin, en la respuesta de la
generación, en el detalle de una liquidación `PAGADA` o no pagable, en la respuesta de marcar como pagada ni en ninguna
ruta del arrendador, y NO DEBE escribirlo en la auditoría ni en los logs (D4, RNF-SEG-14).

## R41
El sistema DEBE responder todas las rutas de `/api/admin/liquidaciones` y `/api/arrendadores/actual/liquidaciones` con
`Cache-Control: no-store` (D4).

## Mis liquidaciones (CA2, CA3)

## R42
CUANDO un arrendador envía `GET /api/arrendadores/actual/liquidaciones`, el sistema DEBE responder 200 con sus
liquidaciones ordenadas por `generada_en` e `id` descendentes, de 20 en 20 según `pagina` (1 por defecto) y con
`hayMas`, cada una con periodo, estado, bruto, comisión, neto, si es simulada, sus líneas y, si está `PAGADA`, el número
de operación, la fecha de pago y el banco y la cuenta enmascarada a los que se pagó (CA2, CA3, S9).

## R43
SI el arrendador envía `pagina` que no es un entero de 1 en adelante ENTONCES el sistema DEBE responder 400 con un error
en el campo `pagina`, sin consultar `liquidacion`.

## R44
El sistema NO DEBE incluir en la respuesta de `GET /api/arrendadores/actual/liquidaciones` datos del usuario que
reservó (id, nombre, correo, teléfono), ni del pago (id de transacción, últimos 4 dígitos), ni el id del admin que
generó o pagó (RNF-SEG-16).

## Auditoría (STRIDE R)

## R45
CUANDO el sistema crea una liquidación, el sistema DEBE auditar `LIQUIDACION_GENERADA` en la misma transacción sobre la
entidad `liquidacion` con el admin, la IP y, en el detalle, el arrendador, la cantidad de reservas, el bruto, la
comisión, el neto, el estado, si es simulada y los avisos del arrendador (STRIDE R, D2).

## R46
CUANDO el sistema marca una liquidación como pagada, el sistema DEBE auditar `LIQUIDACION_PAGADA` en la misma
transacción sobre la entidad `liquidacion` con el admin, la IP y, en el detalle, el estado anterior, el número de
operación, el banco y el número enmascarado de la cuenta, el neto y si es simulada (STRIDE R).

## HU-22 y correo (D8)

## R47
El sistema DEBE ofrecer a los demás componentes de `pagos` la consulta `tieneLiquidacionesPendientes(arrendadorId)`,
que devuelve `true` si y solo si el arrendador tiene alguna liquidación `GENERADA` o `PENDIENTE_CUENTA` (D8, S13).

## R48
El sistema NO DEBE enviar ningún correo al generar una liquidación ni al marcarla como pagada (D8).

## Trazabilidad con la historia

| Origen (`docs/historias/HU-19.md`, `progress/HU-19.md` y `docs/requerimientos.md`) | Cubierto por |
|-------------------------------------------------------------------------------------|--------------|
| CA1: el admin genera una liquidación por arrendador con las reservas usadas hace más de 48 h y no liquidadas: bruto, comisión (10 %) y neto | R7, R8, R9, R10, R12, R13, R15, R17, R18, R19, R22, R23 |
| CA2: al marcar la liquidación como pagada con el número de operación, el arrendador la ve "pagada" en "Mis liquidaciones" | R25, R26, R29, R30, R32, R42 |
| CA3: sin cuenta bancaria registrada, la liquidación queda "pendiente de cuenta" | R16, R26, R27, R42 |
| (T) montos calculados solo en servidor | R13, R14, R22, R23, R32 |
| (E) solo el admin genera y marca liquidaciones | R1, R2, R3, R5, R6, R31 |
| (R) registro de cada liquidación y cambio de estado | R39, R45, R46 |
| Simplificación: la genera el admin con un botón y transfiere a mano; sin job ni API bancaria | R7, R25 (sin proceso programado ni llamada a ningún banco: `design.md` DT-1) |
| D1 todo lo pendiente; periodo informativo | R7, R12, R17, R19 |
| D2 pagos `sim-` solo con la simulada, marcados | R20, R21, R45 |
| D3 cancelada sin reembolso se liquida (⚠ PO) | R10, R11 |
| D4 número completo solo en el detalle por pagar, auditado | R38, R39, R40, R41 |
| D5 retención de 72 h bloquea el pago | R15, R28, R36 |
| D6 suspendido o sin identidad verificada: se liquida y se avisa | R24, R37 |
| D7 `PENDIENTE_CUENTA` pagable si ahora hay cuenta; reservas nuevas en otra liquidación | R17, R26, R27 |
| D8 sin correos; consulta para HU-22 | R47, R48 |
| RF-E-10 / RF-E-11 / RF-E-12 (CA1 / CA2 / CA3) | R7, R13 / R25, R42 / R16 |
| RN-10 liquidación por botón, 48 h, 10 %, pagada o pendiente de cuenta | R7, R8, R13, R16, R25 |
| RN-11 retención de 72 h tras cambiar la cuenta | R28 |
| RNF-SEG-07 montos solo en el servidor | R13, R14 |
| RNF-SEG-12 auditoría | R39, R45, R46 |
| RNF-SEG-14 datos bancarios enmascarados | R38, R40 |
| RNF-SEG-16 minimización | R44 |
| RNF-SEG-18 rol validado en el backend | R2, R5 |
| RNF-SEG-01 / RNF-SEG-19 identidad de la sesión y propiedad | R4, R6 |
| Integridad en BD: una reserva liquidada una sola vez (principio 7) | R12, R23 |
