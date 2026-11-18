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

// ── Reclamos (HU-26) ─────────────────────────────────────────────────
export const MOTIVOS_RECLAMO = {
  ESPACIO_CERRADO: 'Espacio cerrado',
  DISTINTO_A_LO_PUBLICADO: 'Distinto a lo publicado',
  OTRO: 'Otro',
}
export const OPCIONES_MOTIVO = [
  { valor: '', etiqueta: 'Elige un motivo' },
  ...Object.entries(MOTIVOS_RECLAMO).map(([valor, etiqueta]) => ({ valor, etiqueta })),
]
export const PAPELES_RECLAMO = { USUARIO: 'Usuario', ARRENDADOR: 'Arrendador' }

export const BOTON_REPORTAR = 'Reportar un problema'
export const TITULO_REPORTAR = 'Reportar un problema con tu reserva'
export const BOTON_ENVIAR_RECLAMO = 'Enviar reclamo'
export const ETIQUETA_MOTIVO = 'Motivo'
export const ETIQUETA_DESCRIPCION = 'Descripción'
export const AYUDA_DESCRIPCION = 'Cuéntanos qué pasó. Hasta 1000 caracteres. No puedes adjuntar fotos.'
export const RECLAMO_ABIERTO = 'Abrimos tu reclamo. El arrendador puede responder y el equipo de EspacioYa lo resolverá.'
export const ENLACE_VER_RECLAMO = 'Ver reclamo'

export const TITULO_MIS_RECLAMOS = 'Mis reclamos'
export const TITULO_RECLAMOS_ARRENDADOR = 'Reclamos de mis reservas'
export const SIN_RECLAMOS_USUARIO = 'Aún no tienes reclamos.'
export const SIN_RECLAMOS_ARRENDADOR = 'No hay reclamos sobre tus reservas.'
export const BOTON_VER_MAS = 'Ver más'
export const CARGANDO = 'Cargando…'

export const TITULO_RECLAMO = 'Reclamo'
export const SIN_MENSAJES = 'Aún no hay mensajes.'
export const ETIQUETA_TU_MENSAJE = 'Tu mensaje'
export const BOTON_ENVIAR_MENSAJE = 'Enviar mensaje'
export const RECLAMO_YA_RESUELTO = 'Este reclamo ya se resolvió.'
export const SIN_CUPO_MENSAJES = 'Llegaste al máximo de mensajes.'
export const PAPEL_PROPIO = 'Tú'
export const TITULO_HILO = 'Mensajes'

export function mensajesRestantes(n) {
  return `Te quedan ${n} mensajes.`
}

export function resultadoReclamo(aFavorDe) {
  return aFavorDe === 'USUARIO' ? 'Resuelto a favor del usuario' : 'Resuelto a favor del arrendador'
}

export function tituloReserva(id) {
  return `Reserva #${id}`
}

export const TITULO_RECLAMOS_ADMIN = 'Reclamos'
export const TITULO_ABIERTOS = 'Abiertos'
export const TITULO_RESUELTOS = 'Resueltos'
export const SIN_ABIERTOS = 'No hay reclamos abiertos.'
export const SIN_RESUELTOS = 'Aún no hay reclamos resueltos.'

export const TITULO_RESOLVER = 'Resolver reclamo'
export const ETIQUETA_A_FAVOR = 'A favor de'
export const OPCIONES_A_FAVOR = [
  { valor: '', etiqueta: 'Elige una opción' },
  { valor: 'USUARIO', etiqueta: 'Usuario' },
  { valor: 'ARRENDADOR', etiqueta: 'Arrendador' },
]
export const ETIQUETA_REEMBOLSO = 'Reembolso'
export const OPCIONES_REEMBOLSO = [
  { valor: '', etiqueta: 'Elige una opción' },
  { valor: 'TOTAL', etiqueta: 'Total' },
  { valor: 'PARCIAL', etiqueta: 'Parcial' },
]
export const ETIQUETA_MONTO_REEMBOLSO = 'Monto a reembolsar (S/)'
export const ETIQUETA_RESOLUCION = 'Motivo de la resolución'
export const BOTON_RESOLVER = 'Resolver'
export const TITULO_MODAL_RESOLVER = '¿Resolver este reclamo?'
export const CONFIRMA_TOTAL = 'Se reembolsará el total pagado al usuario'
export const CONFIRMA_ARRENDADOR = 'La reserva volverá a ser liquidable'
export const RECLAMO_RESUELTO = 'Reclamo resuelto. Avisamos por correo al usuario y al arrendador.'
export const REEMBOLSO_EN_PASARELA =
  'El reembolso se procesa en la pasarela; si falla, aparecerá en Reembolsos por atender.'
export const SIN_NOMBRE = 'Sin nombre'

export function confirmaParcial(monto) {
  return `Se reembolsará ${monto} al usuario`
}

export const ENLACE_MIS_RECLAMOS = 'Mis reclamos'
export const ENLACE_RECLAMOS_ARRENDADOR = 'Reclamos de mis reservas'
export const ENLACE_RECLAMOS_ADMIN = 'Reclamos'

// ── Reportes y moderación (HU-27) ────────────────────────────────────
// La decisión, sus efectos y las cantidades los fija el servidor; aquí solo textos.
export const ENLACE_REPORTES_ADMIN = 'Reportes'
export const TITULO_REPORTES_ADMIN = 'Reportes'
export const TITULO_POR_REVISAR = 'Por revisar'
export const SIN_POR_REVISAR = 'No hay reportes por revisar.'
export const SIN_REPORTES_RESUELTOS = 'Aún no hay reportes resueltos.'
export const ENLACE_REVISAR = 'Revisar'
export const TITULO_REPORTE = 'Reporte'
export const TITULO_CONTENIDO_REPORTADO = 'Contenido reportado'
export const ENLACE_FICHA_PUBLICA = 'Ver ficha pública'

export function tituloFilaReporte(tipoObjetivo, espacioNombre) {
  return tipoObjetivo === 'ESPACIO' ? `Espacio: ${espacioNombre}` : `Reseña en ${espacioNombre}`
}

export function reportesPendientes(n) {
  return `${n} reportes por revisar sobre este contenido`
}

export function calificacionDeCinco(n) {
  return `${n} de 5`
}

export const DECISIONES_REPORTE = {
  MANTENER: 'Decisión: se mantuvo el contenido',
  OCULTAR: 'Decisión: se ocultó el contenido',
  OCULTAR_POR_FRAUDE: 'Decisión: se ocultó por fraude y se cancelaron sus reservas por empezar',
}

export const TITULO_DECIDIR = 'Decidir'
export const ETIQUETA_DECISION = 'Decisión'
export const OPCION_MANTENER = { valor: 'MANTENER', etiqueta: 'Mantener el contenido' }
export const OPCION_OCULTAR = { valor: 'OCULTAR', etiqueta: 'Ocultar el contenido' }
export const OPCION_OCULTAR_POR_FRAUDE = {
  valor: 'OCULTAR_POR_FRAUDE',
  etiqueta: 'Ocultar por fraude y cancelar sus reservas',
}
export const OPCION_ELIGE_DECISION = { valor: '', etiqueta: 'Elige una opción' }
export const ETIQUETA_MOTIVO_DECISION = 'Motivo de la decisión'
export const AYUDA_MOTIVO_DECISION = 'Hasta 500 caracteres.'
export const AYUDA_MOTIVO_ESPACIO = 'Si lo ocultas, se lo enviaremos al arrendador por correo.'
export const BOTON_REGISTRAR_DECISION = 'Registrar decisión'

export const TITULO_MODAL_OCULTAR = '¿Ocultar este contenido?'
export const TITULO_MODAL_FRAUDE = '¿Ocultar este espacio por fraude?'
export const TITULO_MODAL_MANTENER = '¿Mantener este contenido?'
export const TITULO_MODAL_DECISION = '¿Registrar esta decisión?'
export const TEXTO_FRAUDE = 'Dejará de verse en el catálogo. Cancelaremos todas sus reservas confirmadas que aún no '
  + 'empiezan, devolveremos el 100 % a quienes reservaron y les avisaremos por correo. No se puede deshacer.'
export const TEXTO_OCULTAR_ESPACIO = 'Avisaremos al arrendador por correo con el motivo. Sus reservas confirmadas se '
  + 'mantienen.'

export function textoOcultar(pendientes) {
  return `Dejará de verse en el catálogo. Se resolverán los ${pendientes} reportes por revisar.`
}

export const DECISION_REGISTRADA = 'Decisión registrada.'
export const DECISION_ESPACIO_OCULTO = 'Decisión registrada. Avisamos al arrendador por correo.'

export function decisionPorFraude(n) {
  return `Decisión registrada. Cancelamos ${n} reservas con reembolso total y avisamos por correo.`
}

export function arrendadorDelEspacio(nombre) {
  return `Arrendador: ${nombre ?? SIN_NOMBRE}`
}

export function direccionDelEspacio(direccion) {
  return `Dirección: ${direccion ?? ''}`
}

export function capacidadEnPersonas(capacidad) {
  return `Capacidad: ${capacidad ?? ''} personas`
}

export function motivoDe(texto) {
  return `Motivo: ${texto ?? ''}`
}

export function reportadoEl(instante) {
  return `Reportado el ${fechaHora(instante)}`
}

export function resueltoEl(instante) {
  return `Resuelto el ${fechaHora(instante)}`
}
