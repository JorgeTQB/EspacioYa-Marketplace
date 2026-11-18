# HU-31 · Requirements — Fallo de reembolso simulado sin estado en memoria

> Fuente: [`docs/historias/HU-31.md`](../../docs/historias/HU-31.md) (CA1–CA4 y STRIDE). Depende de HU-16
> ([`specs/HU-16/`](../HU-16/): D6, D7, DT-5, DT-7, DT-11, R14, R15, R24, R43). Historia técnica (épica T): no tiene
> fila con RF en la matriz §7 de `docs/requerimientos.md`; no toca ningún pendiente de §9. Sin migraciones, sin
> endpoints nuevos y sin cambios de pantalla.
>
> Glosario:
> - *la simulada* = `pagos.service.pasarela.PasarelaSimulada` (activa con `espacioya.pagos.pasarela=simulada`).
> - *N* = el valor de `espacioya.pagos.simulada.fallos-reembolso` (`PAGOS_SIMULADA_FALLOS_REEMBOLSO`, 0 por defecto).
> - *el número de intento* = el componente nuevo `numeroIntento` de `SolicitudReembolsoPasarela`. Vale
>   `IntentoReembolso.numero`, que es `reembolso.intentos` tras sumar 1 en `RegistroReembolsos.iniciar` y confirmarse
>   en la base (HU-16 DT-5).
> - *pago simulado* = transacción original cuyo id empieza por `sim-` (`ReglasPago.PREFIJO_SIMULADO`).
> - *una instancia* = un objeto `PasarelaSimulada` propio; dos instancias simulan dos réplicas, y una instancia nueva
>   simula un reinicio.
> - *el adaptador real* = `MercadoPagoAdapter`.

## Propagación del número de intento (STRIDE T)

## R1
CUANDO `SolicitudReembolso.ejecutar()` reclama un intento, el sistema DEBE enviar a `PasarelaPago.reembolsar` una
`SolicitudReembolsoPasarela` cuyo `numeroIntento` es igual a `IntentoReembolso.numero` del intento reclamado.

## R2
CUANDO el admin envía `POST /api/admin/reembolsos/{id}/reintento` con un cuerpo que trae `numeroIntento` o `intentos`,
el sistema DEBE pedir a la pasarela el número de intento igual al valor de `reembolso.intentos` guardado en la base
tras iniciar ese intento, ignorando el cuerpo.

## R3
SI se construye una `SolicitudReembolsoPasarela` con `numeroIntento` menor que 1 ENTONCES el sistema DEBE lanzar
`IllegalArgumentException`.

## R4
El sistema DEBE enviar en todos los intentos de un mismo reembolso la misma transacción, el mismo monto y la misma
clave de idempotencia, y un número de intento que crece de uno en uno (1, 2, …) (HU-16 D6, R24).

## Decisión de la simulada (CA1, CA2, CA3)

## R5
DONDE *N* es mayor que 0, CUANDO la simulada recibe el reembolso de un pago simulado con número de intento menor o
igual que *N*, el sistema DEBE lanzar `ServicioExternoException`. (Reemplaza a HU-16 R15.)

## R6
CUANDO la simulada recibe el reembolso de un pago simulado con número de intento mayor que *N*, el sistema DEBE
aprobarlo y devolver `ReembolsoRealizado` con id `sim-reembolso-<clave de idempotencia>` (HU-16 R14).

## R7
MIENTRAS *N* vale 0, el sistema DEBE aprobar en el intento 1 el reembolso de un pago simulado.

## R8
La simulada DEBE devolver el mismo resultado (falla o el mismo id aprobado) para la misma solicitud y el mismo *N*,
sin importar cuántas llamadas haya recibido antes esa instancia u otra, ni con qué claves.

## R9
CUANDO dos instancias de la simulada con el mismo *N* atienden de forma alternada los intentos 1 a *N* + 1 de un mismo
reembolso, el sistema DEBE fallar exactamente los intentos 1 a *N* y aprobar el intento *N* + 1.

## R10
CUANDO una instancia nueva de la simulada recibe el intento *N* + 1 de un reembolso cuyos intentos 1 a *N* atendió y
falló otra instancia, el sistema DEBE aprobarlo.

## R11
CUANDO, con *N* = 1, el admin reintenta un reembolso `FALLIDO` con `intentos = 1` guardado en la base y del que la
simulada en ejecución nunca recibió una llamada (como tras un reinicio), el sistema DEBE dejar el reembolso
`REEMBOLSADO` con `intentos = 2`.

## R12
SI la simulada recibe el reembolso de una transacción que no empieza por `sim-` ENTONCES el sistema DEBE lanzar
`ServicioExternoException`, sea cual sea *N* y el número de intento (HU-16 R43).

## Registro de los fallos (STRIDE R)

## R13
CUANDO la simulada falla un intento por el número de intento, el sistema DEBE registrar en auditoría
`REEMBOLSO_FALLIDO` sobre el reembolso con el detalle de HU-16 DT-10, cuyo `intento=<n>` es ese número de intento.

## Adaptador real (CA4)

## R14
CUANDO el adaptador real recibe dos `SolicitudReembolsoPasarela` que solo difieren en `numeroIntento`, el sistema DEBE
enviar a la API del proveedor la misma ruta, el mismo cuerpo, la misma autorización y el mismo `X-Idempotency-Key`.

## Reemplazos y compatibilidad

- **HU-16 R15** ("las primeras *N* llamadas de cada clave de idempotencia") queda **reemplazado** por R5 y R8: la
  simulada ya no cuenta llamadas, decide por el número de intento persistido. HU-16 DT-11 (mapa
  `ConcurrentHashMap<String, AtomicInteger>` y "tras un reinicio el contador vuelve a 0") deja de valer. El spec de
  HU-16 no se edita (es histórico); `docs/architecture.md` sí se actualiza (design § 9).
- **HU-16 R14, R24, R43** se mantienen: R6, R4 y R12 los repiten con el número de intento.
- **HU-16 R9** (transacción y monto solo de la base) se extiende al número de intento con R2.
- **HU-26** (`ReclamoReembolsoFallidoIT`, *N* = 1): su intento automático es el 1 y sigue fallando. Sin cambios.
- El flujo del comando (HU-16 DT-2, DT-5), las rutas del admin, la pantalla `ReembolsosPorAtender` y el adaptador real
  no cambian de comportamiento (fuera del alcance de la historia).

## Trazabilidad con la historia

| Origen (docs/historias/HU-31.md)                                                        | Cubierto por          |
|-----------------------------------------------------------------------------------------|-----------------------|
| CA1: fallan los intentos 1..N y el N+1 se aprueba, sin importar qué réplica atienda     | R1, R4, R5, R6, R8, R9 |
| CA2: tras un reinicio el resultado depende del número de intento guardado en la base    | R1, R10, R11          |
| CA3: con `fallos-reembolso = 0` se aprueba al primer intento                            | R6, R7                |
| CA4: el adaptador real ignora el número y envía la misma clave de idempotencia          | R4, R14               |
| (T) el número de intento sale de la base, nunca de la petición                          | R1, R2, R3            |
| (R) cada intento fallido sigue quedando registrado igual que hoy                        | R13                   |
| Contexto: transacciones que no son `sim-` siguen fallando                               | R12                   |
