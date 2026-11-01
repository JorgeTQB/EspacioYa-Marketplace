import { peticion } from './cliente.js'

// HU-13. Sin cuerpo: el monto lo calcula el servidor y la tarjeta se escribe en la página de la pasarela.
export function iniciarPago(reservaId) {
  return peticion(`/api/reservas/${encodeURIComponent(reservaId)}/pago`, { metodo: 'POST' })
}

export function obtenerEstadoPago(reservaId) {
  return peticion(`/api/reservas/${encodeURIComponent(reservaId)}/pago`)
}

// HU-13 D9. Solo con la pasarela simulada: con Mercado Pago, el backend responde 404 a estas dos rutas.
export function obtenerSimulacion(reservaId) {
  return peticion(`/api/reservas/${encodeURIComponent(reservaId)}/pago/simulacion`)
}

export function simularPago(reservaId, resultado) {
  return peticion(`/api/reservas/${encodeURIComponent(reservaId)}/pago/simulacion`, {
    metodo: 'POST',
    cuerpo: { resultado },
  })
}

// HU-16 CA2. Panel admin: la API exige IP permitida y rol ADMIN.
export function listarReembolsosPorAtender() {
  return peticion('/api/admin/reembolsos')
}

// Sin cuerpo: el monto y la transacción salen de la fila del reembolso en el servidor.
export function reintentarReembolso(id) {
  return peticion(`/api/admin/reembolsos/${encodeURIComponent(id)}/reintento`, { metodo: 'POST' })
}
