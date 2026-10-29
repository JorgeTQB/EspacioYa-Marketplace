import { peticion } from './cliente.js'

// Panel admin (HU-06). Desde una IP no permitida o sin rol ADMIN la API responde 403 con su mensaje.
const ARRENDADORES = '/api/admin/arrendadores'

export function listarIdentidadesEnRevision() {
  return peticion(`${ARRENDADORES}/en-revision`)
}

export function obtenerExpedienteIdentidad(id) {
  return peticion(`${ARRENDADORES}/${encodeURIComponent(id)}/identidad`)
}

export function aprobarIdentidad(id) {
  return peticion(`${ARRENDADORES}/${encodeURIComponent(id)}/identidad/aprobacion`, { metodo: 'POST' })
}

export function rechazarIdentidad(id, motivo) {
  return peticion(`${ARRENDADORES}/${encodeURIComponent(id)}/identidad/rechazo`, {
    metodo: 'POST',
    cuerpo: { motivo },
  })
}
