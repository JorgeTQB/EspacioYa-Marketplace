// HU-11. Solo presentación: la política es fija (RN-06) y el servidor decide qué bloques están libres.
export const TITULO_POLITICA = 'Política de cancelación'
export const POLITICA_CANCELACION = [
  'Si cancelas con 48 horas o más de anticipación, te devolvemos el 100 %.',
  'Si cancelas con menos de 48 horas, no hay reembolso.',
  'Si el arrendador cancela, te devolvemos el 100 %.',
]
export const TITULO_BLOQUES = 'Bloques libres'
export const SIN_BLOQUES = 'No hay bloques libres este día. Prueba con otra fecha.'
export const ENLACE_BUSCAR = 'Buscar espacios'
export const CARGANDO = 'Cargando…'

// HU-24. Solo textos: el promedio, el total y qué reseñas se ven los decide el servidor.
export const TITULO_RESENAS = 'Reseñas'
export const AUN_SIN_RESENAS = 'Aún sin reseñas.'
export const SIN_RESENAS = 'Este espacio aún no tiene reseñas.'

export function resumenResenas(promedio, total) {
  if (total === 0) return AUN_SIN_RESENAS
  const cantidad = total === 1 ? '1 reseña' : `${total} reseñas`
  return `${Number(promedio).toFixed(1)} de 5 · ${cantidad}`
}

export function tituloResena(calificacion) {
  return `${calificacion} de 5`
}
