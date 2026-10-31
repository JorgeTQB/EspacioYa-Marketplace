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
