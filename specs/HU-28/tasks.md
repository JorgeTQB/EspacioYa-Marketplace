# HU-28 · Historial de pagos y comprobantes — Tasks

## Backend

- [x] T1 — Agregar `EstadoPago.EN_HISTORIAL` (`PAGADO`, `REEMBOLSADO`) y `PagoRepository.deReservas(...)` con `Slice`, filtro
  de estados y de `sim-`. Cubre: R2, R3, R5, R20. Test: `HistorialPagosControllerIT`.
- [x] T2 — Crear `pagos.service.puertos.ReservasDelUsuario` y `ReservaDelUsuario`. Cubre: R8. Test: `ReservasDelUsuarioImplIT`.
- [x] T3 — Crear `reservas.service.impl.ReservasDelUsuarioImpl` con `ReservaDAO.delUsuario` y
  `CatalogoEspaciosService.nombres` (`todas` y `una`, `readOnly`). Cubre: R8, R17, R22. Test: `ReservasDelUsuarioImplIT`.
- [x] T4 — Crear `DatosComprobante` y `GeneradorComprobantePdf` (PDF 1.4, Helvetica, `xref` calculada, sin librerías,
  sin `/Info`). Cubre: R12, R13, R14, R15, R16, R19. Test: `GeneradorComprobantePdfTest`.
- [x] T5 — Crear `TransaccionDTO`, `PaginaTransaccionesDTO` y `ComprobanteDescargable`. Cubre: R1, R4, R18. Test:
  `HistorialPagosServiceTest`.
- [x] T6 — Crear `HistorialPagosService.listar` (rol, `pagina`, puerto, `deReservas`, reembolsos por lote, DTO). Cubre: R1,
  R2, R3, R4, R5, R6, R7, R8, R9, R18, R19, R20, R22. Test: `HistorialPagosServiceTest`.
- [x] T7 — Crear `HistorialPagosService.comprobante` (rol, `id` bien escrito, elegibilidad, `sim-`, puerto, generador, 404
  único). Cubre: R3, R8, R9, R11, R17, R20, R22. Test: `HistorialPagosServiceTest`.
- [x] T8 — Crear `TransaccionController` (`GET /api/transacciones`, `GET /api/transacciones/{id}/comprobante`; sin
  `produces`; `Content-Disposition: attachment; filename="comprobante-<id>.pdf"`; `Cache-Control: no-store`). Cubre: R1,
  R9, R10, R11, R21. Test: `HistorialPagosControllerIT`.
- [x] T9 — Escribir `HistorialPagosServiceTest` (unitaria, Mockito, `@DisplayName("HU-28 Rn: ...")`): R2, R3, R4, R5, R6,
  R7, R8, R9, R17, R18, R19, R20, R22. Cubre: R2, R3, R4, R5, R6, R7, R8, R9, R17, R18, R19, R20, R22.
- [x] T10 — Escribir `GeneradorComprobantePdfTest` (unitaria): empieza con `%PDF-` y termina con `%%EOF`; contiene número,
  fecha, espacio, horario, monto y estado; con `REEMBOLSADO` contiene el reembolso; solo los últimos 4 dígitos; sin
  `ultimos4` o con `ultimos4` inválido dice "no informada" y no trae dígitos; leyenda de simulado; texto sin el id de la
  pasarela ni correo. Cubre: R12, R13, R14, R15, R16, R19.
- [x] T11 — Escribir `ReservasDelUsuarioImplIT` (Testcontainers, `@Import(TestcontainersConfiguration.class)`): `todas` solo
  trae las del usuario, con nombre y horario; `una` ajena o inexistente da vacío. Cubre: R8, R17, R22.
- [x] T12 — Escribir `HistorialPagosControllerIT` (Testcontainers; borra `reembolso` antes que `pago`; usa
  `PagosSoporte.limpiar`): 200 con fecha, espacio, monto, estado (R1, R2); `REEMBOLSADO` con `montoReembolsado` (R4); pagos
  `RECHAZADO`, `PAGADO_TARDIO`, `MONTO_DISTINTO`, `REEMBOLSO_FALLIDO` ausentes (R3); orden y `hayMas` con 11 pagos (R5);
  `pagina=0` y `pagina=abc` dan 400 (R6); lista vacía (R7); no aparece el pago de otro usuario ni con un `usuarioId`
  en la query (R8); `ARRENDADOR` y `ADMIN` 403 (R9); sin token 401 (R10); descarga 200 con `application/pdf`,
  `Content-Disposition` y `%PDF-` (R11); la respuesta JSON no contiene `ultimos4`, `idTransaccionPasarela` ni `usuarioId`
  (R18); ajena, inexistente, `abc` y `RECHAZADO` dan el mismo 404 (R17); `sim-` con pasarela `simulada` listado con
  `simulada = true` (R19) y, con pasarela real, ausente y 404 (R20); `Cache-Control: no-store` (R21); filas de `pago`,
  `reembolso` y `reserva` sin cambios tras listar y descargar (R22). Cubre: R1 a R11, R17 a R22.

## Frontend

- [x] T13 — Agregar `listarMisTransacciones(pagina)` y `descargarComprobante(id)` en `src/api/pagos.js`. Cubre: pantalla
  `/mis-pagos` (CA1, CA2).
- [x] T14 — Agregar `PAGADO` ("Pagado", éxito) y `REEMBOLSADO` ("Reembolsado", neutro) al mapa de
  `components/ui/EtiquetaEstado.jsx`. Cubre: pantalla `/mis-pagos` (CA1).
- [x] T15 — Agregar los textos de la pantalla a `pages/pagos/textosPagos.js`. Cubre: pantalla `/mis-pagos` (CA1, CA2).
- [x] T16 — Crear `pages/pagos/MisPagos.jsx` y `MisPagos.module.css` (tarjetas, "Ver más" con `SeccionLiquidaciones`,
  lista vacía, errores, "Descargar comprobante" con `Blob`). Cubre: pantalla `/mis-pagos` (CA1, CA2).
- [x] T17 — Agregar la ruta `/mis-pagos` con `RutaProtegida` en `App.jsx` y el enlace "Mis pagos" para `USUARIO` en
  `pages/cuentas/Panel.jsx`. Cubre: pantalla `/mis-pagos` (CA1).

## Documentación y cierre

- [x] T18 — Actualizar `docs/architecture.md` (puerto `pagos.ReservasDelUsuario` y qué deja HU-28), `docs/frontend.md`
  (ruta `/mis-pagos` y estados de `EtiquetaEstado`) y la matriz §7 de `docs/requerimientos.md`. Cubre: documentación.
- [x] T19 — Ejecutar `./init.sh` en verde (unitarias, `*IT`, lint y build), sin colores fuera de `tokens.css`, y completar
  la trazabilidad R<n> a test en `progress/impl_HU-28.md`. Cubre: todos los R.
