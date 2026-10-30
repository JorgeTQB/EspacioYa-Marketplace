import { peticion } from './cliente.js'

// HU-10: búsqueda pública. Sin token: el catálogo es igual con o sin sesión.
export function buscarEspacios({ tipo, zona, fecha, pagina }) {
  const parametros = new URLSearchParams({ tipo, zona, fecha, pagina: String(pagina) })
  return peticion(`/api/espacios?${parametros}`, { autenticada: false })
}
