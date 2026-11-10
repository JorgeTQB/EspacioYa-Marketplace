# Review — HU-28

**Veredicto:** APPROVED

`./init.sh` completo (sin `--rapido`): exit 0. Unitarias 233 verdes, `*IT` 1177 verdes (0 fallos, 0 errores), ESLint y build del frontend sin errores, sin colores fuera de `tokens.css`.

## Trazabilidad requirements <-> tests
Todos los tests llevan `@DisplayName("HU-28 R<n>: ...")` y comprueban resultados concretos (códigos HTTP, campos del DTO, contenido del PDF, filas sin cambios).
- R1: [x] `HistorialPagosControllerIT` (lista), `HistorialPagosServiceTest` (R1, R4)
- R2: [x] `HistorialPagosControllerIT` (lista), `HistorialPagosServiceTest` (R2, R5, R8)
- R3: [x] `HistorialPagosControllerIT` (R3: 4 estados ausentes), `HistorialPagosServiceTest` (R3 y R3/R17)
- R4: [x] `HistorialPagosControllerIT` (lista), `HistorialPagosServiceTest` (R1, R4)
- R5: [x] `HistorialPagosControllerIT` (R5: 11 pagos, orden y `hayMas`)
- R6: [x] `HistorialPagosControllerIT` (R6), `HistorialPagosServiceTest` (R6)
- R7: [x] `HistorialPagosControllerIT` (R7), `HistorialPagosServiceTest` (R7)
- R8: [x] `HistorialPagosControllerIT` (R8, con `usuarioId` en la query), `ReservasDelUsuarioImplIT` (R8)
- R9: [x] `HistorialPagosControllerIT` (R9: ARRENDADOR y ADMIN, historial y comprobante), `HistorialPagosServiceTest` (R9)
- R10: [x] `HistorialPagosControllerIT` (R10)
- R11: [x] `HistorialPagosControllerIT` (comprobante), `GeneradorComprobantePdfTest` (R11)
- R12: [x] `GeneradorComprobantePdfTest` (R12), `HistorialPagosControllerIT`
- R13: [x] `GeneradorComprobantePdfTest` (R13), `HistorialPagosControllerIT` (R13/R19)
- R14: [x] `GeneradorComprobantePdfTest` (R14)
- R15: [x] `GeneradorComprobantePdfTest` (R15)
- R16: [x] `GeneradorComprobantePdfTest` (R16)
- R17: [x] `HistorialPagosControllerIT` (R17: ajena, inexistente, mal escrita, no elegible; mismo 404), `HistorialPagosServiceTest`, `ReservasDelUsuarioImplIT`
- R18: [x] `HistorialPagosControllerIT` (R18), `HistorialPagosServiceTest` (R18)
- R19: [x] `HistorialPagosControllerIT` (R19), `GeneradorComprobantePdfTest` (R19), `HistorialPagosServiceTest` (R19)
- R20: [x] `HistorialPagosControllerIT` (R20, pasarela real: no listado y 404), `HistorialPagosServiceTest` (R20)
- R21: [x] `HistorialPagosControllerIT` (lista y comprobante); `TransaccionController` aplica `CacheControl.noStore()` en ambos
- R22: [x] `HistorialPagosControllerIT` (R22), `ReservasDelUsuarioImplIT` (R22), `HistorialPagosServiceTest` (R22)

## Trazabilidad con la historia
- CA1 (fecha, espacio, monto, estado): [x] R1-R7
- CA2 (descarga PDF): [x] R11, R12, R13
- (E/I) valida que la transacción sea del usuario de sesión: [x] R8, R9, R10, R17, R18; implementado en `HistorialPagosService` y `ReservasDelUsuarioImpl`, identidad solo desde `@AuthenticationPrincipal`
- (I) solo últimos 4 dígitos: [x] R14, R15, R16
- La tabla final de `requirements.md` cubre todos los CA y controles STRIDE.

## Tasks completas
- T1 a T19: [x] todas en `[x]` en `specs/HU-28/tasks.md`.
- Observación menor: T18 menciona la matriz §7 de `docs/requerimientos.md`, que no se tocó. Está justificado en `progress/impl_HU-28.md` (Notas): sus columnas libres son de pruebas, capturas y estado, que llena la pareja que prueba. Se acepta.

## Arquitectura y convenciones
- Capas respetadas: el controller solo importa el servicio; `pagos` accede a reservas por el puerto `pagos.service.puertos.ReservasDelUsuario`, implementado en `reservas.service.impl.ReservasDelUsuarioImpl`; el puerto está documentado en `docs/architecture.md`.
- DTO `record`, inyección por constructor, sin migración (la historia no la requiere), sin `@Autowired`, Lombok ni comodines (verificado por `init.sh`).
- PDF sin librerías externas y sin `/Info`; el comprobante no incluye datos del usuario, de la pasarela ni del arrendador.
- Frontend: `MisPagos.jsx` sin `fetch` directo, sin estilos en línea ni colores fuera de tokens; llamadas vía `src/api/pagos.js`; `design.md` tiene la sección "9. Pantallas".

## Checkpoints
- C1: [x]
- C2: [x] (la historia sigue `in_progress` hasta el cierre del dueño)
- C3: [x]
- C4: [x]
- C5: [x] (rama `feature/HU-28-historial-pagos-comprobantes`; `progress/history.md` modificado; cambios sin commit, lo hace el dueño)
- C6: [x]

## Cambios requeridos
Ninguno.
