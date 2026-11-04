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

// HU-18. El arrendador sale de la sesión: ninguna ruta lleva su id. El número viaja solo en el PUT y la respuesta
// siempre lo devuelve enmascarado.
export function obtenerCuentaBancaria() {
  return peticion('/api/arrendadores/actual/cuenta-bancaria')
}

export function guardarCuentaBancaria({ banco, numero, declaraTitularidad, contrasena }) {
  return peticion('/api/arrendadores/actual/cuenta-bancaria', {
    metodo: 'PUT',
    cuerpo: { banco, numero, declaraTitularidad, contrasena },
  })
}

// HU-19. Panel admin: la API exige IP permitida y rol ADMIN. Sin cuerpo: montos, arrendadores y reservas los decide el
// servidor.
export function generarLiquidaciones() {
  return peticion('/api/admin/liquidaciones', { metodo: 'POST' })
}

export function listarLiquidaciones(grupo, pagina) {
  const parametros = new URLSearchParams({ grupo, pagina: String(pagina) })
  return peticion(`/api/admin/liquidaciones?${parametros}`)
}

export function obtenerLiquidacion(id) {
  return peticion(`/api/admin/liquidaciones/${encodeURIComponent(id)}`)
}

// Solo el número de operación: la fecha, el destino y quién pagó los pone el servidor.
export function marcarLiquidacionPagada(id, numeroOperacion) {
  return peticion(`/api/admin/liquidaciones/${encodeURIComponent(id)}/pago`, {
    metodo: 'POST',
    cuerpo: { numeroOperacion },
  })
}

// HU-19. El arrendador sale de la sesión: la ruta no lleva su id.
export function listarMisLiquidaciones(pagina) {
  const parametros = new URLSearchParams({ pagina: String(pagina) })
  return peticion(`/api/arrendadores/actual/liquidaciones?${parametros}`)
}
