import { peticion } from './cliente.js'

// Panel admin (HU-06). Desde una IP no permitida o sin rol ADMIN la API responde 403 con su mensaje.
const ARRENDADORES = '/api/admin/arrendadores'
const CUENTAS = '/api/admin/cuentas'

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

// Suspensión y reactivación de cuentas (HU-09).
export function buscarCuentas(texto) {
  return peticion(`${CUENTAS}?q=${encodeURIComponent(texto)}`)
}

export function obtenerCuenta(id) {
  return peticion(`${CUENTAS}/${encodeURIComponent(id)}`)
}

export function suspenderCuenta(id, motivo) {
  return peticion(`${CUENTAS}/${encodeURIComponent(id)}/suspension`, { metodo: 'POST', cuerpo: { motivo } })
}

export function reactivarCuenta(id) {
  return peticion(`${CUENTAS}/${encodeURIComponent(id)}/reactivacion`, { metodo: 'POST' })
}

// Reclamos sobre una reserva (HU-26). Las partes usan /api/reclamos; el admin, /api/admin/reclamos.
const RECLAMOS = '/api/reclamos'
const RECLAMOS_ADMIN = '/api/admin/reclamos'

export function abrirReclamo({ reservaId, motivo, descripcion }) {
  return peticion(RECLAMOS, { metodo: 'POST', cuerpo: { reservaId, motivo, descripcion } })
}

export function listarReclamables() {
  return peticion(`${RECLAMOS}/reclamables`)
}

export function listarMisReclamos({ pagina }) {
  return peticion(`${RECLAMOS}?pagina=${encodeURIComponent(pagina)}`)
}

export function obtenerReclamo(id) {
  return peticion(`${RECLAMOS}/${encodeURIComponent(id)}`)
}

export function enviarMensajeReclamo(id, { texto }) {
  return peticion(`${RECLAMOS}/${encodeURIComponent(id)}/mensajes`, { metodo: 'POST', cuerpo: { texto } })
}

export function listarReclamosAdmin({ grupo, pagina }) {
  return peticion(`${RECLAMOS_ADMIN}?grupo=${encodeURIComponent(grupo)}&pagina=${encodeURIComponent(pagina)}`)
}

export function obtenerReclamoAdmin(id) {
  return peticion(`${RECLAMOS_ADMIN}/${encodeURIComponent(id)}`)
}

export function resolverReclamo(id, { aFavorDe, reembolso, montoReembolso, resolucion }) {
  return peticion(`${RECLAMOS_ADMIN}/${encodeURIComponent(id)}/resolucion`, {
    metodo: 'POST',
    cuerpo: { aFavorDe, reembolso, montoReembolso, resolucion },
  })
}
