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

// HU-24. El servidor toma el usuario de la sesión y el espacio de la reserva, y decide si se puede reseñar.
export function crearResena({ reservaId, calificacion, comentario }) {
  return peticion('/api/resenas', { metodo: 'POST', cuerpo: { reservaId, calificacion, comentario } })
}

export function listarResenasPendientes() {
  return peticion('/api/resenas/pendientes')
}

// Pública, como la ficha: de 10 en 10, de la más nueva a la más antigua, sin datos del autor.
export function listarResenasDeEspacio(id, pagina) {
  const parametros = new URLSearchParams({ pagina: String(pagina) })
  return peticion(`/api/espacios/${encodeURIComponent(id)}/resenas?${parametros}`, { autenticada: false })
}
