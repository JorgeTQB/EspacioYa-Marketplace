// Solo presentación: el servidor valida el tipo y decide el precio.
export const TIPOS_ESPACIO = [
  { valor: 'CANCHA', etiqueta: 'Cancha' },
  { valor: 'ESTADIO', etiqueta: 'Estadio' },
  { valor: 'AUDITORIO', etiqueta: 'Auditorio' },
]

export function nombreTipo(tipo) {
  return TIPOS_ESPACIO.find((opcion) => opcion.valor === tipo)?.etiqueta ?? tipo
}

export function formatoPrecio(precio) {
  return `S/ ${Number(precio).toFixed(2)}`
}

// HU-08. Solo presentación: el servidor valida las franjas y calcula el estado de cada bloque.
export const DIAS_SEMANA = [
  { valor: 1, etiqueta: 'Lunes' },
  { valor: 2, etiqueta: 'Martes' },
  { valor: 3, etiqueta: 'Miércoles' },
  { valor: 4, etiqueta: 'Jueves' },
  { valor: 5, etiqueta: 'Viernes' },
  { valor: 6, etiqueta: 'Sábado' },
  { valor: 7, etiqueta: 'Domingo' },
]

export function formatoHora(hora) {
  return `${String(hora).padStart(2, '0')}:00`
}

export const TEXTO_ESTADO_BLOQUE = {
  RESERVADO: 'Reservado',
  BLOQUEADO: 'Bloqueado',
  PASADO: 'Ya pasó',
}
