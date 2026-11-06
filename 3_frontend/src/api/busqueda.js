import { peticion } from './cliente.js'

// HU-10: búsqueda pública. Sin token: el catálogo es igual con o sin sesión.
// HU-23: los filtros vacíos no se envían (el servidor responde 400 a un filtro presente pero vacío) y cada
// característica va como un parámetro caracteristica aparte.
export function buscarEspacios({ tipo, zona, fecha, pagina, precioMaximo, capacidadMinima, caracteristicas = [] }) {
  const parametros = new URLSearchParams({ tipo, zona, fecha, pagina: String(pagina) })
  if (precioMaximo) parametros.append('precioMaximo', precioMaximo)
  if (capacidadMinima) parametros.append('capacidadMinima', capacidadMinima)
  for (const caracteristica of caracteristicas) parametros.append('caracteristica', caracteristica)
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
