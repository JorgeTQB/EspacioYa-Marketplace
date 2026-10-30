// HU-12. Solo presentación: el monto, el estado y el tiempo restante los decide el servidor.
export const BOTON_RESERVAR = 'Reservar'
export const AYUDA_CONSECUTIVOS = 'Elige bloques seguidos.'
export const MENSAJE_INICIAR_SESION = 'Inicia sesión para reservar.'
export const RETENCION = 'Tus bloques están retenidos por:'
export const SE_ACABO_EL_TIEMPO = 'Se acabó el tiempo. Liberamos los bloques: puedes reservar de nuevo.'
export const ENLACE_VOLVER = 'Volver al espacio'
export const ENLACE_BUSCAR = 'Buscar espacios'
export const CARGANDO = 'Cargando…'

const FORMATO_FECHA = new Intl.DateTimeFormat('es-PE', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
})

// La fecha llega como AAAA-MM-DD, sin hora: se lee y se escribe en UTC para que ninguna zona horaria la mueva de día.
export function formatoFecha(fecha) {
  return FORMATO_FECHA.format(new Date(`${fecha}T00:00:00Z`))
}
