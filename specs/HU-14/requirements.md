# HU-14 — Requirements: Mis reservas

> **Revisado el 2026-10-31 tras integrar HU-13** (`done`, `ec51197`, merge `6afb5bc`), según
> `progress/explore_HU-14_HU-13.md`: IP de las IT, D4, D9, D10 (decisión de la dueña del 2026-10-31), D12 y textos
> sobre HU-13. R1–R38 no cambian. **D1–D12 confirmadas por la dueña** (Adriana Callupe) el 2026-10-31.

> Fuente: `docs/historias/HU-14.md` (CA1, CA2, STRIDE E/I e I), `docs/requerimientos.md` (RF-D-05, RF-D-06,
> RNF-SEG-01, RNF-SEG-16, RNF-SEG-19, RN-03, RNF-USA-01 como precedente), `docs/architecture.md` ("Reservas: qué deja
> HU-12", patrones ★ Iterator y DAO), `docs/modelo-datos.md` (`reserva`, `reserva_bloque`, `usuario.nombre`),
> `specs/HU-12/` (R37–R41, D2, DT-10, S11), `specs/HU-10/` (paginación con `hayMas`), `docs/historias/HU-13.md`,
> `progress/explore_HU-14.md` y `progress/explore_HU-14_HU-13.md` (huecos P1–P8, T1–T9, H1–H6). Notación EARS estricta (`docs/specs.md`). Los requirements
> hablan de la **API** y de las interfaces que usan otros módulos; la presentación está en `design.md` § 12 Pantallas y
> las decisiones técnicas (DT-n) en `design.md` § 1.

## Decisiones por defecto — confirmar en la aprobación

> ⚠ La dueña (**Adriana Callupe**) no contestó antes del spec, como en HU-11. El spec adopta estos valores; si alguno
> no se acepta al aprobar, cambian los requirements citados antes de pasar a `in_progress`.

| # | Hueco | Decisión | Afecta |
|---|-------|----------|--------|
| D1 | P1 | Al arrendador se le muestra el `nombre` de la cuenta del usuario si existe y no está en blanco; si no, el texto fijo **"Usuario de EspacioYa"**. Hoy las cuentas `USUARIO` no tienen nombre (HU-01 no lo pide): **HU-20** (editar perfil, Release 2) lo completa sin cambiar este contrato. Nunca se deriva del correo (RNF-SEG-16). | R18, R19, R36 |
| D2 | P2 | Una reserva es **próxima** si su fin (`fecha` + `horaFin`, hora de Lima) es posterior a *ahora* y su estado leído con `estadoEn(ahora)` es `PENDIENTE_PAGO` (vigente) o `CONFIRMADA`. Todas las demás son **pasadas**, también una `EXPIRADA` o `CANCELADA` con fecha futura. El corte lo decide el servidor. | R4, R5, R13, R14, R22, R23 |
| D3 | P3 | Próximas en orden **ascendente** y pasadas en orden **descendente** (por fecha y hora de inicio; a igualdad, por id). Se pagina como HU-10, con `pagina` y `hayMas` y **sin total**, de **10 en 10** en cada grupo. | R24–R32 |
| D4 | P4 | El usuario ve **todos** los estados con `EtiquetaEstado`, incluidas `EXPIRADA` y `CANCELADA`. Todas enlazan a `/reservas/:id` (D10); el pago está en esa pantalla (HU-13). | R4, R5, R10, R23; Pantallas |
| D5 | P5 | El arrendador ve, por reserva: espacio, fecha, horario, nombre del usuario (según D1), estado y monto. **Nunca** correo, teléfono ni datos de pago (RNF-SEG-16). Se separan en próximas y pasadas, sin filtro por espacio. | R13–R20 |
| D6 | P6 | De cada espacio se muestra solo el **nombre** y un enlace "Ver espacio" a la ficha de HU-11. Sin foto ni dirección. | R9; Pantallas |
| D7 | P7 | El usuario usa **`/mis-reservas`** y el arrendador **`/panel/reservas`**, con enlaces desde `Panel.jsx`. No se toca el `retorno` de `RutaProtegida` (código de HU-03). | Pantallas |
| D8 | P8 | Cada ruta exige su rol: `GET /api/reservas` solo `USUARIO` y `GET /api/arrendadores/actual/reservas` solo `ARRENDADOR`; otro rol recibe **403**. Al arrendador le basta el rol: no se le exige correo ni identidad verificados. | R2, R3 |
| D9 | H1 | `CONFIRMADA` ya se alcanza por la API con la pasarela simulada de HU-13 (`POST /api/reservas/{id}/pago` y `POST /api/reservas/{id}/pago/simulacion` con `APROBADO`). Las IT tienen **un caso de punta a punta de CA2** (reservar → pagar → aprobar simulado → aparece en las próximas del arrendador y como `CONFIRMADA` en las del usuario). Las reservas pasadas, las fechas distintas y los estados raros (`EXPIRADA` con filas inactivas, `CANCELADA`, vencida sin procesar) se siguen sembrando con `JdbcTemplate`. | R4, R13, R15; plan de pruebas |
| D10 | H3 | **Decisión de la dueña (2026-10-31):** el enlace "Ver reserva" a `/reservas/:id` aparece en **todas** las reservas del usuario, en cualquier estado, para que pueda llegar al aviso de pago no aplicado de HU-13 (p. ej. una `EXPIRADA` con un pago tardío). El botón "Pagar" ya está en esa pantalla (HU-13); HU-14 no agrega botones de pago. | D4; Pantallas |
| D11 | H4 | Quedan **fuera** de HU-14 el estado del pago, el comprobante y `ultimos4`: son de HU-28. HU-14 no lee la tabla `pago`. | R12, R20 |
| D12 | H6 | HU-13 está `done` (`ec51197`). La caja negra de CA2 se hace **completa** en UAT con la pasarela simulada (pagar → "Aprobar pago simulado" → la reserva aparece en "Reservas de mis espacios"). No queda ninguna verificación pendiente de integración. Lo que falte con Mercado Pago real es de HU-13, no de HU-14. | R13, R15 |

## Definiciones

- **Usuario con sesión:** cuenta con rol `USUARIO` y sesión vigente (HU-03, HU-12 D1).
- **Arrendador con sesión:** cuenta con rol `ARRENDADOR` y sesión vigente (D8).
- **Ahora:** el instante que da el reloj del sistema. **Ahora local:** ese instante en `America/Lima`
  (`ZonaHoraria.ahora(reloj)`).
- **Estado leído:** `Reserva.estadoEn(ahora)`. Una reserva vencida (`PENDIENTE_PAGO` con *ahora* ≥ `expira_en`) se lee
  `EXPIRADA` aunque su fila siga en `PENDIENTE_PAGO` (HU-12 D2).
- **Horario de una reserva:** `fecha` = la fecha de sus filas de `reserva_bloque`, **activas o no** (todas tienen la
  misma, HU-12 S6); `horaInicio` = la menor `hora_inicio`; `horaFin` = la mayor `hora_inicio` + 1 (de 1 a 24).
- **Fin de una reserva:** la fecha a las 00:00 más `horaFin` horas (con `horaFin = 24`, las 00:00 del día siguiente).
- **Reserva próxima:** estado leído `PENDIENTE_PAGO` o `CONFIRMADA` y fin posterior a *ahora local* (D2).
  **Reserva pasada:** la que no es próxima.
- **Espacios del arrendador:** los espacios cuyo `arrendador_id` es el id del arrendador con sesión, en cualquier estado
  (`BORRADOR`, `PUBLICADO`, `PAUSADO`, `BAJA`, `OCULTO`).
- **Grupo:** valor del parámetro `grupo`: exactamente `proximas` o `pasadas`.
- **Página válida:** parámetro `pagina` ausente (equivale a 1) o texto de solo dígitos con valor de 1 a 2 147 483 647
  (mismo criterio que HU-10).
- **Tamaño de página:** 10 (D3).
- **Orden del grupo:** `proximas`, ascendente por (fecha, `horaInicio`, id); `pasadas`, descendente por (fecha,
  `horaInicio`, id) (D3).
- **ReservaDTO:** el DTO de HU-12, sin cambios: `id`, `espacioId`, `espacioNombre`, `estado`, `fecha`, `horaInicio`,
  `horaFin`, `precioHora`, `montoTotal`, `expiraEn` y `segundosRestantes`.
- **ReservaDeMiEspacioDTO:** cuerpo con exactamente `id`, `espacioId`, `espacioNombre`, `usuarioNombre`, `estado`,
  `fecha`, `horaInicio`, `horaFin` y `montoTotal` (`design.md` § 5).
- **Página de reservas:** cuerpo con exactamente `pagina`, `reservas` y `hayMas` (`PaginaReservasDTO`).
- **Nombre mostrado del usuario:** el `nombre` de su cuenta si no es nulo ni está en blanco; si no, "Usuario de
  EspacioYa" (D1).
- **Mensajes** (texto exacto en `design.md` § 5): *grupo*, *página* (mismo texto que HU-10), *sesión inválida* (HU-03)
  y *acceso denegado*.

## Sesión y rol (STRIDE E/I, RNF-SEG-01, D8)

## R1
SI un cliente sin sesión vigente envía `GET /api/reservas` o `GET /api/arrendadores/actual/reservas` ENTONCES el
sistema DEBE responder 401 con el mensaje *sesión inválida*.

## R2
SI una cuenta con rol distinto de `USUARIO` envía `GET /api/reservas`, con cualquier valor de `grupo` y `pagina`,
ENTONCES el sistema DEBE responder 403 con el mensaje *acceso denegado* sin consultar reservas (D8).

## R3
SI una cuenta con rol distinto de `ARRENDADOR` envía `GET /api/arrendadores/actual/reservas`, con cualquier valor de
`grupo` y `pagina`, ENTONCES el sistema DEBE responder 403 con el mensaje *acceso denegado* sin consultar reservas
(D8).

## Mis reservas del usuario (CA1, RF-D-05)

## R4
CUANDO un usuario con sesión envía `GET /api/reservas` con `grupo=proximas` y una página válida, el sistema DEBE
responder 200 con una página de reservas cuyos elementos son ReservaDTO de sus reservas próximas (D2, D4).

## R5
CUANDO un usuario con sesión envía `GET /api/reservas` con `grupo=pasadas` y una página válida, el sistema DEBE
responder 200 con una página de reservas cuyos elementos son ReservaDTO de sus reservas pasadas (D2, D4).

## R6
El sistema DEBE incluir en la respuesta de `GET /api/reservas` solo reservas cuyo `usuario_id` es el id del usuario de
la sesión (STRIDE E/I, RNF-SEG-19).

## R7
CUANDO el sistema procesa `GET /api/reservas` o `GET /api/arrendadores/actual/reservas`, el sistema DEBE ignorar
cualquier parámetro distinto de `grupo` y `pagina`, como `usuarioId`, `arrendadorId` o `espacioId` (RNF-SEG-01).

## R8
El sistema DEBE responder en `fecha`, `horaInicio` y `horaFin` de cada elemento de las dos rutas el horario de la
reserva, calculado con todas sus filas de `reserva_bloque`, activas o no.

## R9
El sistema DEBE responder en `espacioNombre` de cada elemento de las dos rutas el nombre del espacio aunque el espacio
ya no esté `PUBLICADO` o su arrendador no esté `ACTIVA` (HU-12 S11, D6).

## R10
MIENTRAS una reserva es vencida, el sistema DEBE responderla en `GET /api/reservas` con `estado = "EXPIRADA"` sin
escribir en la base de datos (HU-12 D2).

## R11
El sistema DEBE responder en `segundosRestantes` de cada ReservaDTO de `GET /api/reservas` los segundos enteros que
faltan hasta `expira_en` si la reserva es vigente, y 0 en otro caso (como HU-12 R40).

## R12
El sistema DEBE responder cada elemento de `GET /api/reservas` con exactamente las claves del ReservaDTO, sin
`usuarioId`, sin datos del arrendador y sin datos de pago (HU-12 R41, D11).

## Reservas de mis espacios (CA2, RF-D-06)

## R13
CUANDO un arrendador con sesión envía `GET /api/arrendadores/actual/reservas` con `grupo=proximas` y una página
válida, el sistema DEBE responder 200 con una página de reservas cuyos elementos son ReservaDeMiEspacioDTO de las
reservas próximas de sus espacios (D2, D5).

## R14
CUANDO un arrendador con sesión envía `GET /api/arrendadores/actual/reservas` con `grupo=pasadas` y una página válida,
el sistema DEBE responder 200 con una página de reservas cuyos elementos son ReservaDeMiEspacioDTO de las reservas
pasadas de sus espacios (D2, D5).

## R15
El sistema NO DEBE incluir en `GET /api/arrendadores/actual/reservas` una reserva cuyo `estado` sea distinto de
`CONFIRMADA` (CA2, D9).

## R16
El sistema NO DEBE incluir en `GET /api/arrendadores/actual/reservas` una reserva de un espacio que no es de los
espacios del arrendador de la sesión (STRIDE E/I, RNF-SEG-19).

## R17
El sistema DEBE incluir en `GET /api/arrendadores/actual/reservas` las reservas `CONFIRMADA` de los espacios del
arrendador en cualquier estado del espacio, incluidos `PAUSADO`, `BAJA` y `OCULTO`.

## R18
DONDE la cuenta que hizo la reserva tiene un `nombre` no nulo ni en blanco, el sistema DEBE responder ese nombre en
`usuarioNombre` del ReservaDeMiEspacioDTO (D1).

## R19
SI la cuenta que hizo la reserva tiene el `nombre` nulo o en blanco ENTONCES el sistema DEBE responder
`usuarioNombre = "Usuario de EspacioYa"` (D1).

## R20
El sistema DEBE responder cada elemento de `GET /api/arrendadores/actual/reservas` con exactamente las claves del
ReservaDeMiEspacioDTO, sin `usuarioId`, correo, teléfono ni ningún dato de pago del usuario (STRIDE I, RNF-SEG-16, D5,
D11).

## R21
MIENTRAS el arrendador de la sesión no tiene espacios, el sistema DEBE responder a
`GET /api/arrendadores/actual/reservas` con una página válida 200 con `reservas` vacía y `hayMas = false`, sin consultar
la tabla `reserva`.

## Grupos, orden y páginas (las dos rutas, D2, D3)

## R22
El sistema DEBE devolver una reserva en el grupo `proximas` solo si es próxima, y en el grupo `pasadas` solo si es
pasada, según el estado leído y el fin comparados con *ahora* y *ahora local* de la solicitud (D2).

## R23
MIENTRAS una reserva del usuario tiene estado leído `EXPIRADA` o `CANCELADA`, el sistema DEBE devolverla en el grupo
`pasadas` aunque su fin sea posterior a *ahora local* (D2, D4).

## R24
CUANDO el grupo es `proximas`, el sistema DEBE ordenar los elementos de forma ascendente por fecha, `horaInicio` e id
(D3).

## R25
CUANDO el grupo es `pasadas`, el sistema DEBE ordenar los elementos de forma descendente por fecha, `horaInicio` e id
(D3).

## R26
CUANDO la página pedida es la `n`, el sistema DEBE responder en `reservas` los elementos del grupo de las posiciones
`(n − 1) × 10 + 1` a `n × 10` según el orden del grupo, y en `pagina` el valor `n` (D3).

## R27
El sistema DEBE responder `hayMas = true` solo si el grupo tiene elementos después de la última posición de la página
pedida, sin incluir el total de reservas en la respuesta (D3).

## R28
CUANDO la solicitud no trae `pagina`, el sistema DEBE responder la página 1.

## R29
SI `pagina` está presente y no es una página válida ENTONCES el sistema DEBE responder 400 con un error en el campo
`pagina` y el mensaje *página*.

## R30
SI `grupo` falta o no es exactamente `proximas` ni `pasadas` ENTONCES el sistema DEBE responder 400 con un error en el
campo `grupo` y el mensaje *grupo*.

## R31
SI `grupo` y `pagina` son inválidos a la vez ENTONCES el sistema DEBE responder un solo 400 con los dos errores de
campo.

## R32
CUANDO el grupo no tiene elementos en la página pedida, el sistema DEBE responder 200 con `reservas` vacía y
`hayMas = false`.

## Contratos internos (DAO, `cuentas`, `espacios`)

## R33
CUANDO el servicio invoca `ReservaDAO.delUsuario(usuarioId)`, el sistema DEBE devolver solo las reservas con ese
`usuario_id`, cada una con su horario (STRIDE E/I, ★ DAO).

## R34
CUANDO el servicio invoca `ReservaDAO.confirmadasDeEspacios(espacioIds)`, el sistema DEBE devolver solo las reservas
`CONFIRMADA` cuyo `espacio_id` está en esa colección, cada una con su horario, y una lista vacía sin consultar la base
de datos si la colección está vacía (STRIDE E/I, ★ DAO).

## R35
CUANDO `ReservaDAO` devuelve reservas con su horario, el sistema DEBE leerlas con una consulta a `reserva` y una a
`reserva_bloque`, sin importar cuántas reservas devuelva.

## R36
CUANDO otro módulo invoca `CuentaService.nombres(usuarioIds)`, el sistema DEBE devolver para cada id cuya cuenta tiene
`nombre` no nulo ni en blanco solo ese nombre, sin exigir sesión ni rol, y sin devolver correo, teléfono ni otro dato
de la cuenta (D1, RNF-SEG-16).

## R37
CUANDO otro módulo invoca `CatalogoEspaciosService.nombresDelArrendador(arrendadorId)`, el sistema DEBE devolver el id
y el nombre de cada espacio de ese arrendador en cualquier estado, y ninguno de otro arrendador.

## R38
CUANDO el sistema responde a `GET /api/reservas` o a `GET /api/arrendadores/actual/reservas`, el sistema NO DEBE
escribir en `reserva`, `reserva_bloque` ni `auditoria`.

## Reemplazos y compatibilidad

- **HU-12:** `GET /api/reservas/{id}`, `POST /api/reservas` y el ReservaDTO **no cambian**. `GET /api/reservas` (sin id),
  hoy libre, pasa a ser "Mis reservas". La conversión `Reserva → ReservaDTO` de `ReservaServiceImpl` se mueve a una
  clase compartida sin cambiar su resultado (`design.md` DT-11): los tests de HU-12 siguen verdes sin tocarlos.
- **HU-10:** el criterio de página válida, el mensaje *página* y la respuesta sin total se copian; `busqueda` no cambia.
- **`cuentas` y `espacios`:** solo se **agregan** `CuentaService.nombres` y `CatalogoEspaciosService.nombresDelArrendador`
  (`design.md` § 11). Ningún método existente cambia.
- **HU-13:** HU-14 no depende de su código de producción (verificado contra `ec51197`). Lo que asumía de ella está en
  `design.md` § 11.3, ahora **verificado**. Solo la IT de punta a punta de CA2 llama a la API de pagos (D9).

## Trazabilidad con la historia

| Origen (`docs/historias/HU-14.md` y fuentes)                                     | Cubierto por |
|----------------------------------------------------------------------------------|--------------|
| CA1: como usuario abro "Mis reservas"                                            | R1, R2, R4, R5 (pantalla en design § 12) |
| CA1: … veo espacio, fecha, horario, monto y estado de cada una                   | R4, R5, R8, R9, R10, R11, R12 |
| CA1: … separadas en próximas y pasadas                                           | R4, R5, R22, R23, R24, R25, R26–R32 |
| CA2: como arrendador abro "Reservas de mis espacios"                             | R1, R3, R13, R14, R21 (pantalla en design § 12) |
| CA2: … veo las reservas confirmadas de mis espacios                              | R13, R14, R15, R16, R17, R34 |
| CA2: … con el nombre del usuario y el horario                                    | R8, R18, R19, R36 |
| (E/I) Cada consulta filtra por el usuario de la sesión                           | R2, R3, R6, R7, R16, R33, R34, R37 |
| (I) El arrendador no ve correo, teléfono ni datos de pago del usuario            | R19, R20, R36 |
| RF-D-05 / RF-D-06                                                                | R4–R12, R22–R32 / R13–R21, R22–R32 |
| RNF-SEG-01 (identidad de la sesión)                                              | R6, R7, R16 |
| RNF-SEG-16 (minimización)                                                        | R12, R19, R20, R36 |
| RNF-SEG-19 (propiedad antes de consultar)                                        | R6, R16, R33, R34 |
| RN-03 (estados de la reserva, leídos con `estadoEn`)                             | R10, R15, R22, R23 |
| ★ DAO: consultas siempre filtradas por el usuario de la sesión                   | R33, R34, R35 |
| ★ Iterator: recorrer próximas y pasadas sin exponer la colección                 | R22–R27, R32 (design § 2) |
| Lectura sin efectos (consulta no auditada, RNF-SEG-12)                           | R10, R38 |
