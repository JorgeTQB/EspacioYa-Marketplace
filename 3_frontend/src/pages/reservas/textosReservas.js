// HU-12. Solo presentación: el monto, el estado y el tiempo restante los decide el servidor.
export const BOTON_RESERVAR = 'Reservar'
export const AYUDA_CONSECUTIVOS = 'Elige bloques seguidos.'
export const MENSAJE_INICIAR_SESION = 'Inicia sesión para reservar.'
export const RETENCION = 'Tus bloques están retenidos por:'
export const SE_ACABO_EL_TIEMPO = 'Se acabó el tiempo. Liberamos los bloques: puedes reservar de nuevo.'
export const ENLACE_VOLVER = 'Volver al espacio'
export const ENLACE_BUSCAR = 'Buscar espacios'
export const CARGANDO = 'Cargando…'

// HU-13. Solo textos: si se puede pagar y si el pago se confirmó lo decide el servidor.
export const BOTON_PAGAR = 'Pagar'
export const CONFIRMANDO_PAGO = 'Estamos confirmando tu pago…'
export const PAGO_EN_REVISION = 'Tu pago sigue en revisión. Si se aprueba, recibirás el comprobante por correo.'
export const RESERVA_CONFIRMADA = 'Tu reserva está confirmada. Te enviamos el comprobante por correo.'
export const PAGO_RECHAZADO = 'Tu pago no se aprobó. Puedes intentarlo de nuevo mientras tu reserva siga vigente.'
export const INTENTOS_AGOTADOS =
  'Alcanzaste el máximo de intentos de pago. Tus bloques siguen retenidos hasta que la reserva expire; después puedes reservar de nuevo.'
export const PAGO_NO_DISPONIBLE = 'Esta reserva ya no se puede pagar. Cuando expire, puedes reservar de nuevo.'
export const PAGO_NO_APLICADO = 'Recibimos un pago que no pudimos aplicar a esta reserva. Gestionaremos su devolución.'

// HU-14. Solo textos: qué reservas son próximas o pasadas y en qué orden lo decide el servidor.
export const TITULO_MIS_RESERVAS = 'Mis reservas'
export const TITULO_RESERVAS_DE_MIS_ESPACIOS = 'Reservas de mis espacios'
export const TITULO_PROXIMAS = 'Próximas'
export const TITULO_PASADAS = 'Pasadas'
export const SIN_PROXIMAS = 'Aún no tienes reservas próximas.'
export const SIN_PASADAS = 'Aún no tienes reservas pasadas.'
export const SIN_CONFIRMADAS_PROXIMAS = 'Aún no tienes reservas confirmadas próximas.'
export const SIN_CONFIRMADAS_PASADAS = 'Aún no tienes reservas confirmadas pasadas.'
export const BOTON_VER_MAS = 'Ver más'
export const ENLACE_VER_ESPACIO = 'Ver espacio'
export const ENLACE_VER_RESERVA = 'Ver reserva'
export const RESERVADO_POR = 'Reservado por'

// HU-15. Solo textos: el monto y la política los decide el servidor.
export const BOTON_CANCELAR_RESERVA = 'Cancelar reserva'
export const TITULO_CANCELAR = '¿Cancelar esta reserva?'
export const BOTON_CONFIRMAR_CANCELACION = 'Sí, cancelar reserva'
export const BOTON_VOLVER = 'Volver'
export const ETIQUETA_ESPACIO = 'Espacio'
export const ETIQUETA_FECHA = 'Fecha'
export const ETIQUETA_HORARIO = 'Horario'
export const ETIQUETA_MONTO_PAGADO = 'Monto pagado'
export const RESERVA_CANCELADA_USUARIO = 'Reserva cancelada. Te enviamos un correo con el detalle.'
export const RESERVA_CANCELADA_ARRENDADOR = 'Reserva cancelada. Le avisamos al usuario por correo.'

// La pantalla solo elige el texto según la política que devolvió la API; el monto (en negrita) es el de la API y va
// entre "antes" y "despues". Sin "despues", la línea no muestra monto.
export const LINEA_REEMBOLSO = {
  USUARIO_CON_ANTICIPACION: {
    antes: 'Se te devolverá ',
    despues: ' (100 %): cancelas con 48 horas o más de anticipación.',
  },
  USUARIO_SIN_ANTICIPACION: {
    antes: 'No corresponde reembolso (0 %): faltan menos de 48 horas para el inicio.',
  },
  ARRENDADOR: {
    antes: 'Al usuario se le devolverá ',
    despues: ' (100 %) y recibirá un correo.',
  },
}

export function reembolsoEnProceso(monto) {
  return `El reembolso de ${monto} está en proceso.`
}

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
