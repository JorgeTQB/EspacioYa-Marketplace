# Implementación HU-28 — Historial de pagos y comprobantes

Rama: `feature/HU-28-historial-pagos-comprobantes` (sin commit). Todas las tasks T1-T19 hechas. Sin migración ni auditoría.

## Trazabilidad R<n> → test

Siglas: `S` = `HistorialPagosServiceTest`, `G` = `GeneradorComprobantePdfTest`, `IT` = `HistorialPagosControllerIT`,
`P` = `ReservasDelUsuarioImplIT`.

| R | Test |
|---|------|
| R1 | `IT.listaElHistorial`, `S.contenidoDeLaFila` |
| R2 | `IT.listaElHistorial`, `S.consultaPaginadaDeLasSuyas` |
| R3 | `IT.soloLosElegibles`, `S.estadosFueraDelHistorial`, `S.noElegible` |
| R4 | `IT.listaElHistorial`, `S.contenidoDeLaFila` |
| R5 | `IT.paginaDeDiez`, `S.consultaPaginadaDeLasSuyas` |
| R6 | `IT.paginaInvalida`, `S.paginaInvalida` |
| R7 | `IT.listaVacia`, `S.listaVacia` |
| R8 | `IT.soloLosDeLaSesion`, `S.deOtroUsuario`, `P.todasSoloLasSuyas` |
| R9 | `IT.otroRol`, `S.soloElUsuario` |
| R10 | `IT.sinSesion` |
| R11 | `IT.descargaElComprobante`, `S.comprobanteDeUnPagoPropio`, `G.estructuraValida` |
| R12 | `G.contenidoBasico`, `IT.descargaElComprobante` |
| R13 | `G.conReembolso`, `IT.comprobanteReembolsadoYSimulado` |
| R14 | `G.soloUltimosCuatro`, `IT.descargaElComprobante` |
| R15 | `G.tarjetaNoInformada` |
| R16 | `G.sinDatosAjenos`, `IT.descargaElComprobante` |
| R17 | `IT.mismoNoEncontrado`, `S.idMalEscrito`, `S.inexistente`, `S.deOtroUsuario`, `P.unaAjenaOInexistente` |
| R18 | `IT.sinDatosSensibles`, `S.sinDatosSensibles` |
| R19 | `IT.simuladosListados`, `IT.comprobanteReembolsadoYSimulado`, `S.simuladosConPasarelaSimulada`, `G.simulado` |
| R20 | `IT.simuladosConPasarelaReal`, `S.simuladosConPasarelaReal`, `S.comprobanteSimulado` |
| R21 | `IT.listaElHistorial`, `IT.descargaElComprobante` |
| R22 | `IT.noEscribe`, `S.soloLee`, `P.noEscribe` |

## Archivos

Backend nuevo (`2_Backend/src/main/java/pe/edu/ulima/espacioya/`): `pagos/controller/TransaccionController`,
`pagos/service/HistorialPagosService`, `pagos/service/ComprobanteDescargable`, `pagos/service/comprobante/{DatosComprobante,
GeneradorComprobantePdf}`, `pagos/service/puertos/{ReservasDelUsuario,ReservaDelUsuario}`, `pagos/dto/{TransaccionDTO,
PaginaTransaccionesDTO}`, `reservas/service/impl/ReservasDelUsuarioImpl`. Modificados: `pagos/domain/EstadoPago`,
`pagos/repository/PagoRepository`.

Tests nuevos: `HistorialPagosServiceTest`, `GeneradorComprobantePdfTest`, `HistorialPagosControllerIT`,
`ReservasDelUsuarioImplIT`.

Frontend: `api/pagos.js`, `components/ui/EtiquetaEstado.jsx`, `pages/pagos/textosPagos.js`, `pages/pagos/MisPagos.{jsx,module.css}`
(nuevos), `App.jsx`, `pages/cuentas/Panel.jsx`.

Docs: `docs/architecture.md` (puerto y párrafo de HU-28), `docs/frontend.md` (ruta y estados).

## Notas

- La matriz §7 de `docs/requerimientos.md` no se tocó: sus columnas libres son de pruebas, capturas y estado, que llena la
  pareja que prueba.
- `HistorialPagosControllerIT` no prueba un reembolso `FALLIDO` sumado a otros (la restricción `uq_reembolso_pago_origen`
  deja solo dos filas por pago); el filtro por estado `REEMBOLSADO` está en la unitaria `S.contenidoDeLaFila`.
- R20 con pasarela real se prueba en la IT construyendo `HistorialPagosService` con `"mercadopago"` sobre los beans reales.
- La sesión arrancó en `develop`; se creó la rama de la historia.
