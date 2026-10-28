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
