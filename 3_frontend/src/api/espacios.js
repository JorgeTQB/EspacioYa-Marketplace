import { peticion, peticionArchivo } from './cliente.js'

const MIS_ESPACIOS = '/api/arrendadores/actual/espacios'

export function listarZonas() {
  return peticion('/api/zonas')
}

export function listarMisEspacios() {
  return peticion(MIS_ESPACIOS)
}

export function obtenerMiEspacio(id) {
  return peticion(`${MIS_ESPACIOS}/${id}`)
}

export function crearEspacio(ficha) {
  return peticion(MIS_ESPACIOS, { metodo: 'POST', cuerpo: ficha })
}

export function editarEspacio(id, ficha) {
  return peticion(`${MIS_ESPACIOS}/${id}`, { metodo: 'PUT', cuerpo: ficha })
}

export function publicarEspacio(id) {
  return peticion(`${MIS_ESPACIOS}/${id}/publicacion`, { metodo: 'POST' })
}

export function subirFotoEspacio(id, archivo) {
  const formulario = new FormData()
  if (archivo) formulario.append('foto', archivo)
  return peticion(`${MIS_ESPACIOS}/${id}/fotos`, { metodo: 'POST', cuerpo: formulario })
}

export function eliminarFotoEspacio(id, fotoId) {
  return peticion(`${MIS_ESPACIOS}/${id}/fotos/${fotoId}`, { metodo: 'DELETE' })
}

// La url llega de la API (/api/fotos/<UUID>) y es estable.
export function descargarFotoEspacio(url) {
  return peticionArchivo(url)
}
