// HU-10. Solo presentación: el servidor decide qué espacios están disponibles y revalida la fecha.
export const SIN_RESULTADOS = 'No encontramos espacios disponibles para esa fecha en esa zona.'
export const SUGERENCIA_SIN_RESULTADOS = 'Prueba con otra fecha o con otra zona.'
export const SIN_MAS_RESULTADOS = 'No hay más resultados.'
// HU-23 CA3: sin resultados con algún filtro, se sugiere quitar alguno.
export const SIN_RESULTADOS_CON_FILTROS = 'No encontramos espacios que cumplan todos los filtros.'
export const SUGERENCIA_QUITAR_FILTRO =
  'Prueba a quitar algún filtro: precio máximo, capacidad mínima o alguna característica.'
export const BOTON_QUITAR_FILTROS = 'Quitar filtros'

const DIAS_VENTANA = 55
const FORMATO_LIMA = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'America/Lima',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

// Hoy y hoy + 55 en Lima (AAAA-MM-DD), para el min y max del campo fecha. Es solo ayuda: el servidor revalida.
export function ventanaBusqueda(ahora = new Date()) {
  const hoy = FORMATO_LIMA.format(ahora)
  const hasta = new Date(`${hoy}T00:00:00Z`)
  hasta.setUTCDate(hasta.getUTCDate() + DIAS_VENTANA)
  return { hoy, hasta: hasta.toISOString().slice(0, 10) }
}
