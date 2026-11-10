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

// HU-18. Solo textos: si la identidad está verificada, la cuenta y su estado los decide el servidor.
export const TITULO_CUENTA_BANCARIA = 'Cuenta bancaria'
export const DESCRIPCION_CUENTA_BANCARIA = 'Aquí recibirás los pagos de tus reservas.'
export const CARGANDO_CUENTA = 'Cargando…'
export const SIN_CUENTA_BANCARIA = 'Aún no tienes una cuenta bancaria registrada.'
export const TITULO_FORMULARIO_CUENTA = 'Datos de la cuenta'
export const ETIQUETA_BANCO = 'Banco'
export const ETIQUETA_NUMERO_CUENTA = 'Número de cuenta'
export const AYUDA_NUMERO_CUENTA = 'Solo dígitos, de 10 a 20.'
export const ETIQUETA_DECLARACION = 'La cuenta está a mi nombre o al de mi RUC'
export const ETIQUETA_CONTRASENA_CUENTA = 'Tu contraseña'
export const AYUDA_CONTRASENA_CUENTA = 'La pedimos para confirmar que eres tú.'
export const BOTON_GUARDAR_CUENTA = 'Guardar cuenta'
export const CUENTA_GUARDADA = 'Cuenta bancaria guardada.'
export const AVISO_CAMBIO_CUENTA =
  'Si cambias tu cuenta, te avisaremos por correo y tu próxima liquidación se retendrá 72 horas.'
export const AVISO_IDENTIDAD_CUENTA = 'Para registrar una cuenta bancaria, primero debes verificar tu identidad.'
export const ENLACE_VERIFICAR_IDENTIDAD = 'Verificar mi identidad'
export const ENLACE_CUENTA_BANCARIA = 'Cuenta bancaria'

export function retenidaHasta(fecha) {
  return `Retenida hasta el ${fecha}.`
}

// HU-19. Solo textos: montos, estados y avisos los decide el servidor.
export const CARGANDO_LIQUIDACIONES = 'Cargando…'
export const BOTON_VER_MAS_LIQUIDACIONES = 'Ver más'
export const ENLACE_LIQUIDACIONES = 'Liquidaciones'
export const ENLACE_MIS_LIQUIDACIONES = 'Mis liquidaciones'
export const TITULO_LIQUIDACIONES = 'Liquidaciones'
export const DESCRIPCION_LIQUIDACIONES =
  'Cada generación liquida las reservas usadas hace más de 48 horas que aún no se liquidaron. Los montos los calcula el sistema.'
export const BOTON_GENERAR_LIQUIDACION = 'Generar liquidación'
export const TITULO_MODAL_GENERAR = '¿Generar la liquidación?'
export const BOTON_CONFIRMAR_GENERAR = 'Generar'
export const SIN_RESERVAS_POR_LIQUIDAR = 'No hay reservas por liquidar.'
export const SECCION_POR_PAGAR = 'Por pagar'
export const SECCION_PAGADAS = 'Pagadas'
export const SIN_POR_PAGAR = 'No hay liquidaciones por pagar.'
export const SIN_PAGADAS = 'Aún no hay liquidaciones pagadas.'
export const ETIQUETA_BRUTO = 'Bruto'
export const ETIQUETA_COMISION = 'Comisión (10 %)'
export const ETIQUETA_NETO = 'Neto'
export const ENLACE_VER_DETALLE = 'Ver detalle'
export const ENLACE_VOLVER_LIQUIDACIONES = 'Volver a liquidaciones'
export const AVISOS_ARRENDADOR = {
  CUENTA_NO_ACTIVA: 'La cuenta del arrendador no está activa (suspendida o eliminada).',
  IDENTIDAD_NO_VERIFICADA: 'La identidad del arrendador no está verificada.',
}
export const AVISO_LIQUIDACION_SIMULADA = 'Incluye pagos simulados: no se cobró dinero real por estas reservas.'
export const LINEA_CANCELADA = 'Cancelada sin reembolso'
export const LINEA_SIMULADA = 'Simulado'
export const TITULO_CUENTA_DESTINO = 'Cuenta de destino'
export const NOTA_NUMERO_COMPLETO = 'Cópialo para transferir. Esta consulta queda registrada.'
export const SIN_CUENTA_DESTINO = 'El arrendador aún no registra una cuenta bancaria.'
export const BOTON_MARCAR_PAGADA = 'Marcar como pagada'
export const TITULO_MODAL_MARCAR = 'Marcar como pagada'
export const ETIQUETA_NUMERO_OPERACION = 'Número de operación'
export const AYUDA_NUMERO_OPERACION = 'De 4 a 30 letras, dígitos o guiones.'
export const LIQUIDACION_MARCADA = 'Liquidación marcada como pagada.'
export const TITULO_MIS_LIQUIDACIONES = 'Mis liquidaciones'
export const DESCRIPCION_MIS_LIQUIDACIONES =
  'Lo cobrado por tus reservas menos la comisión de la plataforma (10 %). La plataforma te transfiere el neto a tu cuenta bancaria.'
export const SIN_MIS_LIQUIDACIONES = 'Aún no tienes liquidaciones.'
export const AVISO_REGISTRA_CUENTA = 'Registra tu cuenta bancaria para recibir este pago.'
export const LIQUIDACION_EN_PROCESO = 'En proceso de pago.'

// HU-28. Solo textos: qué pagos aparecen, sus montos y estados los decide el servidor.
export const TITULO_MIS_PAGOS = 'Mis pagos'
export const DESCRIPCION_MIS_PAGOS = 'Tus pagos y reembolsos. Descarga el comprobante de cada uno en PDF.'
export const SIN_PAGOS = 'Aún no tienes pagos.'
export const BOTON_DESCARGAR_COMPROBANTE = 'Descargar comprobante'
export const ENLACE_MIS_PAGOS = 'Mis pagos'
export const AVISO_PAGO_SIMULADO = 'Pago simulado'

export function tituloPago(id) {
  return `Pago #${id}`
}

export function reembolsadoDe(monto) {
  return `Reembolsado: ${monto}`
}

export function liquidacionesGeneradas(cantidad) {
  return cantidad === 1 ? 'Se generó 1 liquidación.' : `Se generaron ${cantidad} liquidaciones.`
}

export function liquidacionDel(fecha) {
  return `Liquidación del ${fecha}`
}

export function cantidadReservas(cantidad) {
  return cantidad === 1 ? '1 reserva' : `${cantidad} reservas`
}

export function generadaEl(fechaHora) {
  return `Generada el ${fechaHora}`
}

export function pagadaConOperacion(fechaHora, operacion) {
  return `Pagada el ${fechaHora} · Operación ${operacion}`
}

export function pagadaConDestino(fechaHora, operacion, banco, cuenta) {
  return `Pagada el ${fechaHora} · Operación ${operacion} · ${banco} ${cuenta}`
}

export function pagadaAlArrendador(fechaHora, operacion, banco, cuenta) {
  return `Pagada el ${fechaHora} · Operación N.º ${operacion} · ${banco} ${cuenta}`
}

export function cuentaRetenidaHasta(fechaHora) {
  return `La cuenta cambió hace poco. Podrás marcar esta liquidación como pagada desde el ${fechaHora}.`
}

export function confirmacionMarcarPagada(neto, banco, cuenta) {
  return `Registra el número de operación de la transferencia de ${neto} a ${banco} ${cuenta}.`
}
