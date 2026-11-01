// HU-13 D9. Solo textos: si la simulación existe y qué pasa con el pago lo decide el servidor.
export const TITULO_PAGO_SIMULADO = 'Pago simulado'
export const AVISO_SIMULACION =
  'Simulación: esta pantalla no es una pasarela de pago. No se cobra nada ni se usa ninguna tarjeta.'
export const BOTON_APROBAR = 'Aprobar pago simulado'
export const BOTON_RECHAZAR = 'Rechazar pago simulado'
export const SIMULACION_NO_DISPONIBLE = 'Este pago simulado ya no está disponible.'
export const ENLACE_VOLVER_RESERVA = 'Volver a la reserva'

// HU-16. Solo textos: el estado y el monto los decide el servidor.
export const TITULO_REEMBOLSOS = 'Reembolsos por atender'
export const DESCRIPCION_REEMBOLSOS =
  'Reembolsos que la pasarela no confirmó. Al reintentar se pide de nuevo el mismo reembolso, contra el pago original.'
export const CARGANDO_REEMBOLSOS = 'Cargando…'
export const SIN_REEMBOLSOS = 'No hay reembolsos por atender.'
export const BOTON_REINTENTAR = 'Reintentar'
export const TITULO_MODAL_REINTENTO = '¿Reintentar el reembolso?'
export const ORIGENES_REEMBOLSO = {
  CANCELACION: 'Cancelación de la reserva',
  RECLAMO: 'Reclamo',
}

export function tituloReembolso(reservaId) {
  return `Reserva #${reservaId}`
}

// El monto va en negrita entre las dos partes.
export const CONFIRMACION_REINTENTO_ANTES = 'Se volverá a pedir a la pasarela el reembolso de '

export function confirmacionReintentoDespues(reservaId) {
  return ` de la reserva #${reservaId}, contra el pago original.`
}

export function reembolsoRealizado(monto) {
  return `Reembolso de ${monto} realizado.`
}

export const REEMBOLSO_VOLVIO_A_FALLAR = 'La pasarela volvió a fallar. Puedes reintentarlo más tarde.'
