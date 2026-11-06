// Solo presentación: el servidor valida el tipo y decide el precio.
export const TIPOS_ESPACIO = [
  { valor: 'CANCHA', etiqueta: 'Cancha' },
  { valor: 'ESTADIO', etiqueta: 'Estadio' },
  { valor: 'AUDITORIO', etiqueta: 'Auditorio' },
]

// HU-23. Lista fija de características, en el orden en que se muestran. El servidor valida la lista blanca.
export const CARACTERISTICAS = [
  { valor: 'TECHADO', etiqueta: 'Techado' },
  { valor: 'ILUMINACION', etiqueta: 'Iluminación' },
  { valor: 'VESTUARIOS', etiqueta: 'Vestuarios' },
  { valor: 'ESTACIONAMIENTO', etiqueta: 'Estacionamiento' },
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

// HU-21. Pausar, reanudar y dar de baja. Los mensajes de error llegan de la API tal cual.
export const AVISO_PAUSADO =
  'Este espacio está pausado: no aparece en las búsquedas ni recibe reservas nuevas. Tus reservas confirmadas se ' +
  'mantienen.'
export const AVISO_BAJA = 'Este espacio está dado de baja: ya no aparece en el catálogo y no se puede editar.'
export const TITULO_ESTADO_PUBLICACION = 'Estado de la publicación'
export const BOTON_PAUSAR = 'Pausar'
export const BOTON_REANUDAR = 'Reanudar'
export const BOTON_DAR_DE_BAJA = 'Dar de baja'
export const TITULO_PAUSAR = '¿Pausar este espacio?'
export const TEXTO_PAUSAR =
  'Dejará de aparecer en las búsquedas y no recibirá reservas nuevas. Las reservas que ya tienes se mantienen.'
export const CONFIRMAR_PAUSAR = 'Sí, pausar'
export const TITULO_DAR_DE_BAJA = '¿Dar de baja este espacio?'
export const TEXTO_DAR_DE_BAJA =
  'Es definitivo: dejará de aparecer en el catálogo y ya no podrás editarlo ni volver a publicarlo. Si tiene reservas ' +
  'futuras, primero tendrás que cancelarlas con reembolso del 100 %.'
export const CONFIRMAR_DAR_DE_BAJA = 'Sí, dar de baja'
export const MENSAJE_PAUSADO = 'Tu espacio está pausado.'
export const MENSAJE_REANUDADO = 'Tu espacio vuelve a estar publicado.'
export const MENSAJE_DADO_DE_BAJA = 'Tu espacio está dado de baja.'
export const TITULO_RESERVAS_AFECTADAS = 'Reservas afectadas'
export const SIN_RESERVAS_AFECTADAS =
  'Ya no quedan reservas confirmadas por cancelar. Vuelve a dar de baja el espacio.'
