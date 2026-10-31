import { peticion } from './cliente.js'

// HU-12. El servidor toma el usuario de la sesión y calcula el monto: el cliente solo envía lo que se eligió.
export function crearReserva({ espacioId, fecha, horas }) {
  return peticion('/api/reservas', { metodo: 'POST', cuerpo: { espacioId, fecha, horas } })
}

export function obtenerReserva(id) {
  return peticion(`/api/reservas/${encodeURIComponent(id)}`)
}

// HU-14. El servidor toma el dueño de la sesión, decide si cada reserva es próxima o pasada y pagina de 10 en 10.
export function listarMisReservas({ grupo, pagina }) {
  const parametros = new URLSearchParams({ grupo, pagina: String(pagina) })
  return peticion(`/api/reservas?${parametros}`)
}

export function listarReservasDeMisEspacios({ grupo, pagina }) {
  const parametros = new URLSearchParams({ grupo, pagina: String(pagina) })
  return peticion(`/api/arrendadores/actual/reservas?${parametros}`)
}

// HU-15. La política, el porcentaje y el monto los calcula el servidor. montoReembolsoVisto es el montoReembolso de la
// vista previa, sin tocarlo: el servidor solo lo compara con el que recalcula.
export function obtenerCancelacion(id) {
  return peticion(`/api/reservas/${encodeURIComponent(id)}/cancelacion`)
}

export function cancelarReserva(id, montoReembolsoVisto) {
  return peticion(`/api/reservas/${encodeURIComponent(id)}/cancelacion`, {
    metodo: 'POST',
    cuerpo: { montoReembolsoVisto },
  })
}

export function obtenerCancelacionDeMiEspacio(id) {
  return peticion(`/api/arrendadores/actual/reservas/${encodeURIComponent(id)}/cancelacion`)
}

export function cancelarReservaDeMiEspacio(id, montoReembolsoVisto) {
  return peticion(`/api/arrendadores/actual/reservas/${encodeURIComponent(id)}/cancelacion`, {
    metodo: 'POST',
    cuerpo: { montoReembolsoVisto },
  })
}
