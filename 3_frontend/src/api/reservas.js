import { peticion } from './cliente.js'

// HU-12. El servidor toma el usuario de la sesión y calcula el monto: el cliente solo envía lo que se eligió.
export function crearReserva({ espacioId, fecha, horas }) {
  return peticion('/api/reservas', { metodo: 'POST', cuerpo: { espacioId, fecha, horas } })
}

export function obtenerReserva(id) {
  return peticion(`/api/reservas/${encodeURIComponent(id)}`)
}
