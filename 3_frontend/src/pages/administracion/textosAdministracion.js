// Solo presentación: los estados y las decisiones los fija el servidor (HU-06).
export const NOMBRES_DOCUMENTO = {
  IDENTIDAD: 'Documento de identidad',
  PROPIEDAD: 'Documento de propiedad o autorización',
}

export function fechaHora(instante) {
  return instante ? new Date(instante).toLocaleString('es-PE') : ''
}
