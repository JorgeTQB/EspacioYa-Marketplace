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

// HU-27. Reportar contenido: el catálogo de motivos lo valida el servidor; aquí solo sus nombres.
export const MOTIVOS_REPORTE = {
  FRAUDE: 'Posible fraude o estafa',
  INFORMACION_FALSA: 'Información falsa o engañosa',
  CONTENIDO_INAPROPIADO: 'Contenido ofensivo o inapropiado',
  SPAM: 'Spam o publicidad',
}
export const OPCIONES_MOTIVO_REPORTE = [
  { valor: '', etiqueta: 'Elige un motivo' },
  ...Object.entries(MOTIVOS_REPORTE).map(([valor, etiqueta]) => ({ valor, etiqueta })),
]
export const BOTON_REPORTAR_PUBLICACION = 'Reportar publicación'
export const BOTON_REPORTAR_RESENA = 'Reportar reseña'
export const BOTON_REPORTADO = 'Reportado'
export const TITULO_REPORTAR_PUBLICACION = 'Reportar esta publicación'
export const TITULO_REPORTAR_RESENA = 'Reportar esta reseña'
export const ETIQUETA_MOTIVO_REPORTE = 'Motivo'
export const AYUDA_REPORTE = 'El equipo de EspacioYa revisará tu reporte. No le diremos a nadie quién lo envió.'
export const BOTON_ENVIAR_REPORTE = 'Enviar reporte'
export const REPORTE_ENVIADO = 'Gracias. Enviamos tu reporte al equipo de EspacioYa.'
