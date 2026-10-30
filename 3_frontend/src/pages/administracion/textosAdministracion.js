// Solo presentación: los estados y las decisiones los fija el servidor (HU-06).
export const NOMBRES_DOCUMENTO = {
  IDENTIDAD: 'Documento de identidad',
  PROPIEDAD: 'Documento de propiedad o autorización',
}

// HU-09: el rol lo decide el servidor; aquí solo se le da un nombre para mostrarlo.
export const NOMBRES_ROL = {
  USUARIO: 'Usuario',
  ARRENDADOR: 'Arrendador',
  ADMIN: 'Administrador',
}

export function fechaHora(instante) {
  return instante ? new Date(instante).toLocaleString('es-PE') : ''
}
