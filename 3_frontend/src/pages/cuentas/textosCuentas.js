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
