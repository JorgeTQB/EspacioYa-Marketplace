import { peticion } from './cliente.js'

// HU-10: búsqueda pública. Sin token: el catálogo es igual con o sin sesión.
export function buscarEspacios({ tipo, zona, fecha, pagina }) {
  const parametros = new URLSearchParams({ tipo, zona, fecha, pagina: String(pagina) })
  return peticion(`/api/espacios?${parametros}`, { autenticada: false })
}

// HU-11: ficha pública y sus bloques libres del día. Sin token, como la búsqueda.
export function obtenerEspacio(id) {
  return peticion(`/api/espacios/${encodeURIComponent(id)}`, { autenticada: false })
}

export function consultarBloquesLibres(id, fecha) {
  const parametros = new URLSearchParams({ fecha })
  return peticion(`/api/espacios/${encodeURIComponent(id)}/disponibilidad?${parametros}`, { autenticada: false })
}
