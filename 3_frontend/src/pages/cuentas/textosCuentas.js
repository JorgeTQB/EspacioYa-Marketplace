export const REQUISITOS_CONTRASENA =
  'La contraseña debe tener entre 8 y 64 caracteres, al menos una letra y un número, y no puede ser igual a tu correo.'

export const CONTRASENAS_NO_COINCIDEN = 'Las contraseñas no coinciden.'

// Verificación en dos pasos (HU-17).
export const TITULO_VERIFICACION = 'Verificación en dos pasos'
export const ETIQUETA_CODIGO = 'Código de verificación'
export const AYUDA_CODIGO = 'Escribe el código de 6 dígitos de tu app o uno de tus códigos de respaldo.'
export const AYUDA_CODIGO_APP = 'Escribe el código de 6 dígitos que te muestra tu app.'
export const BOTON_VERIFICAR = 'Verificar'
export const BOTON_VOLVER = 'Volver'
export const AVISO_MFA_ADMIN = 'Como administrador, debes activar la verificación en dos pasos para entrar al panel.'
export const PASOS_CONFIGURACION = [
  'Abre tu app de autenticación (por ejemplo, Google Authenticator o Microsoft Authenticator).',
  'Agrega una cuenta con esta clave:',
  'Escribe el código que te muestra.',
]
export const ENLACE_ABRIR_APP = 'Abrir en mi app'
export const BOTON_ACTIVAR_Y_ENTRAR = 'Activar y entrar'
export const BOTON_ACTIVAR = 'Activar'
export const AVISO_CODIGOS_RESPALDO =
  'Guarda estos códigos en un lugar seguro. Cada uno sirve una sola vez y no volverás a verlos.'
export const BOTON_CODIGOS_GUARDADOS = 'Ya los guardé, continuar'
export const BOTON_LISTO = 'Listo'
export const PREPARANDO_CLAVE = 'Preparando tu clave…'

export const TITULO_SEGURIDAD = 'Seguridad de la cuenta'
export const ENLACE_SEGURIDAD = 'Seguridad de la cuenta'
export const CARGANDO_SEGURIDAD = 'Cargando…'
export const MFA_INACTIVO =
  'La verificación en dos pasos no está activa. Actívala para que una contraseña robada no baste para entrar.'
export const BOTON_ACTIVAR_MFA = 'Activar verificación en dos pasos'
export const MFA_ACTIVADO =
  'Activaste la verificación en dos pasos. Desde ahora te pediremos un código al iniciar sesión.'
export const MFA_ACTIVO = 'La verificación en dos pasos está activa.'
export const MFA_OBLIGATORIO_ADMIN = 'Como administrador, la verificación en dos pasos es obligatoria.'
export const BOTON_DESACTIVAR = 'Desactivar'
export const TITULO_DESACTIVAR = '¿Desactivar la verificación en dos pasos?'
export const EXPLICACION_DESACTIVAR = 'Para confirmar, escribe tu contraseña y un código de tu app o de respaldo.'
export const ETIQUETA_CONTRASENA = 'Contraseña'
export const MFA_DESACTIVADO = 'Desactivaste la verificación en dos pasos.'

export function codigosRestantes(cantidad) {
  return cantidad === 1 ? 'Te queda 1 código de respaldo.' : `Te quedan ${cantidad} códigos de respaldo.`
}

// Solo presentación: la clave se muestra en grupos de 4 para copiarla sin errores.
export function claveEnGrupos(secreto) {
  return secreto.match(/.{1,4}/g)?.join(' ') ?? secreto
}

// Mi perfil (HU-20). Los mensajes de la API se muestran tal cual; estos son solo los de la pantalla.
export const ENLACE_PERFIL = 'Mi perfil'
export const TITULO_PERFIL = 'Mi perfil'
export const CARGANDO_PERFIL = 'Cargando…'
export const TITULO_DATOS_PERSONALES = 'Datos personales'
export const ETIQUETA_NOMBRE = 'Nombre completo'
export const ETIQUETA_TELEFONO = 'Teléfono'
export const AYUDA_TELEFONO = 'Celular de 9 dígitos que empiece con 9'
export const BOTON_GUARDAR_DATOS = 'Guardar cambios'
export const DATOS_GUARDADOS = 'Tus datos se guardaron.'
export const TITULO_FOTO = 'Foto'
export const ETIQUETA_FOTO = 'Foto de perfil (JPG o PNG, hasta 5 MB)'
export const ALT_FOTO = 'Tu foto de perfil'
export const CARGANDO_FOTO = 'Cargando foto…'
export const FOTO_NO_DISPONIBLE = 'No pudimos mostrar tu foto.'
export const BOTON_SUBIR_FOTO = 'Subir foto'
export const BOTON_QUITAR_FOTO = 'Quitar foto'
export const FOTO_GUARDADA = 'Tu foto se guardó.'

// Recordatorios (HU-25). CA3: el texto de ayuda deja claro que solo se apaga el recordatorio.
export const TITULO_RECORDATORIOS = 'Recordatorios'
export const ETIQUETA_RECORDATORIOS = 'Recibir un recordatorio por correo 24 h antes de cada reserva'
export const AYUDA_RECORDATORIOS = 'Los correos de pago y de cancelación se envían siempre.'
export const BOTON_GUARDAR_RECORDATORIOS = 'Guardar'
export const RECORDATORIOS_GUARDADOS = 'Guardamos tu preferencia de recordatorios.'
export const CARGANDO_RECORDATORIOS = 'Cargando…'
export const FOTO_QUITADA = 'Quitaste tu foto.'
export const TITULO_CORREO = 'Correo'
export const ETIQUETA_CORREO_NUEVO = 'Correo nuevo'
export const BOTON_CAMBIAR_CORREO = 'Cambiar correo'
export const TITULO_CONFIRMAR_CONTRASENA = 'Confirma con tu contraseña'
export const EXPLICACION_CONTRASENA_PERFIL = 'Por tu seguridad, escribe tu contraseña para guardar este cambio.'
export const BOTON_CONFIRMAR = 'Confirmar'
export const TITULO_FACTURACION = 'Datos de facturación'
export const ETIQUETA_TIPO_DOCUMENTO = 'Tipo de documento'
export const ETIQUETA_NUMERO_DOCUMENTO = 'Número de documento'
export const ETIQUETA_RAZON_SOCIAL = 'Razón social o nombre'
export const ETIQUETA_DIRECCION_FISCAL = 'Dirección fiscal'
export const BOTON_GUARDAR_FACTURACION = 'Guardar facturación'
export const FACTURACION_GUARDADA = 'Tus datos de facturación se guardaron.'
export const AVISO_FACTURACION_VERIFICADA =
  'Si cambias tu documento o tu razón social, tu identidad volverá a revisión y no podrás publicar espacios nuevos ' +
  'ni cambiar tu cuenta bancaria hasta que la aprobemos.'
export const AVISO_FACTURACION_EN_REVISION =
  'Tu identidad está en revisión. Mientras tanto solo puedes cambiar tu dirección fiscal.'
export const OPCIONES_TIPO_DOCUMENTO = [
  { valor: 'DNI', etiqueta: 'DNI' },
  { valor: 'RUC', etiqueta: 'RUC' },
]

export function correoActual(correo) {
  return `Tu correo: ${correo}`
}

export function correoPendiente(correo) {
  return `Pendiente de confirmar: ${correo}. Revisa ese correo y abre el enlace. Tu correo actual sigue activo.`
}

export function ayudaNumeroDocumento(enmascarado) {
  return `Actual: ${enmascarado}. Déjalo vacío para mantenerlo.`
}

// Confirmar correo nuevo (HU-20).
export const TITULO_CONFIRMAR_CORREO = 'Confirma tu nuevo correo'
export const INSTRUCCION_CONFIRMAR_CORREO = 'Pulsa el botón para confirmar tu nuevo correo.'
export const BOTON_CONFIRMAR_CORREO = 'Confirmar mi nuevo correo'
export const ENLACE_CORREO_INVALIDO =
  'El enlace para confirmar tu correo no es válido o ya expiró. Solicita el cambio de nuevo.'
export const ENLACE_IR_A_PERFIL = 'Ir a mi perfil'
export const ENLACE_INICIAR_SESION = 'Iniciar sesión'

// Mis datos personales (HU-22).
export const TITULO_MIS_DATOS = 'Mis datos personales'
export const EXPLICACION_MIS_DATOS = 'Puedes descargar una copia de tus datos o eliminar tu cuenta.'
export const BOTON_DESCARGAR_DATOS = 'Descargar mis datos'
export const BOTON_ELIMINAR_CUENTA = 'Eliminar mi cuenta'
export const EXPLICACION_CONTRASENA_DESCARGA = 'Por tu seguridad, escribe tu contraseña para descargar tus datos.'
export const BOTON_DESCARGAR = 'Descargar'
export const DATOS_DESCARGADOS = 'Descargamos tus datos en un archivo JSON.'
export const TITULO_ELIMINAR_CUENTA = '¿Eliminar tu cuenta?'
export const AVISO_ELIMINAR_CUENTA =
  'Se desactivará tu cuenta y se borrarán tus datos personales. Conservaremos tus reservas y pagos sin tus datos, ' +
  'como exige la ley. No podrás deshacerlo.'
export const TITULO_IMPEDIMENTOS = 'Antes de eliminar tu cuenta, resuelve esto:'
export const ENLACE_VER_RESERVAS = 'Ver mis reservas'
export const ENLACE_VER_RESERVAS_ESPACIOS = 'Ver las reservas de mis espacios'
export const ENLACE_VER_LIQUIDACIONES = 'Ver mis liquidaciones'
export const ENLACE_VER_RECLAMOS = 'Ver mis reclamos'

export function nombreArchivoDatos(fecha) {
  return `espacioya-mis-datos-${fecha}.json`
}
