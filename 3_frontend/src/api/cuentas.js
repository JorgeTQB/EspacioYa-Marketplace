import { peticion, peticionArchivo } from './cliente.js'

export function obtenerVersionPrivacidad() {
  return peticion('/api/politicas-privacidad/vigente')
}

export function registrarUsuario({ correo, contrasena, aceptaPrivacidad, versionPolitica }) {
  return peticion('/api/usuarios', {
    metodo: 'POST',
    cuerpo: { correo, contrasena, aceptaPrivacidad, versionPolitica },
  })
}

export function registrarArrendador({
  correo,
  contrasena,
  aceptaPrivacidad,
  versionPolitica,
  nombre,
  telefono,
  tipoDocumento,
  numeroDocumento,
  razonSocial,
  direccionFiscal,
}) {
  return peticion('/api/arrendadores', {
    metodo: 'POST',
    cuerpo: {
      correo,
      contrasena,
      aceptaPrivacidad,
      versionPolitica,
      nombre,
      telefono,
      tipoDocumento,
      numeroDocumento,
      razonSocial,
      direccionFiscal,
    },
  })
}

// El login no lleva el token anterior: un 401 aquí son credenciales inválidas, no una sesión perdida.
export function iniciarSesion({ correo, contrasena }) {
  return peticion('/api/sesiones', {
    metodo: 'POST',
    cuerpo: { correo, contrasena },
    autenticada: false,
  })
}

export function obtenerSesionActual() {
  return peticion('/api/sesiones/actual')
}

export function cerrarSesion() {
  return peticion('/api/sesiones/actual', { metodo: 'DELETE' })
}

export function enviarDocumentosIdentidad({ documentoIdentidad, documentoPropiedad }) {
  const formulario = new FormData()
  if (documentoIdentidad) formulario.append('documentoIdentidad', documentoIdentidad)
  if (documentoPropiedad) formulario.append('documentoPropiedad', documentoPropiedad)
  return peticion('/api/arrendadores/actual/documentos', { metodo: 'POST', cuerpo: formulario })
}

export function obtenerIdentidad() {
  return peticion('/api/arrendadores/actual/identidad')
}

// Recuperación de contraseña (HU-04): públicas, sin el token de sesión. El token del enlace va en el cuerpo.
export function solicitarRecuperacion({ correo }) {
  return peticion('/api/recuperaciones', {
    metodo: 'POST',
    cuerpo: { correo },
    autenticada: false,
  })
}

export function validarEnlaceRecuperacion(token) {
  return peticion('/api/recuperaciones/validaciones', {
    metodo: 'POST',
    cuerpo: { token },
    autenticada: false,
  })
}

export function restablecerContrasena({ token, contrasena }) {
  return peticion('/api/recuperaciones/confirmaciones', {
    metodo: 'POST',
    cuerpo: { token, contrasena },
    autenticada: false,
  })
}

// Segundo paso del inicio de sesión (HU-17). Sin token: el desafío va en el cuerpo y no es una sesión, así que un
// 401 aquí es una verificación expirada y no debe tomarse como sesión perdida.
export function verificarMfa({ desafio, codigo }) {
  return peticion('/api/sesiones/mfa', {
    metodo: 'POST',
    cuerpo: { desafio, codigo },
    autenticada: false,
  })
}

export function configurarMfaEnInicio({ desafio }) {
  return peticion('/api/sesiones/mfa/configuracion', {
    metodo: 'POST',
    cuerpo: { desafio },
    autenticada: false,
  })
}

// Verificación en dos pasos desde la cuenta (HU-17, /panel/seguridad).
export function obtenerMfa() {
  return peticion('/api/mfa')
}

export function iniciarConfiguracionMfa() {
  return peticion('/api/mfa/configuracion', { metodo: 'POST' })
}

export function activarMfa({ codigo }) {
  return peticion('/api/mfa/activacion', { metodo: 'POST', cuerpo: { codigo } })
}

export function desactivarMfa({ contrasena, codigo }) {
  return peticion('/api/mfa/desactivacion', { metodo: 'POST', cuerpo: { contrasena, codigo } })
}

// Perfil del titular de la sesión (HU-20). La cuenta sale siempre de la sesión: ninguna ruta lleva un id.
export function obtenerPerfil() {
  return peticion('/api/usuarios/actual/perfil')
}

export function actualizarDatosPerfil({ nombre, telefono }) {
  return peticion('/api/usuarios/actual/perfil', { metodo: 'PUT', cuerpo: { nombre, telefono } })
}

export function subirFotoPerfil(archivo) {
  const formulario = new FormData()
  if (archivo) formulario.append('foto', archivo)
  return peticion('/api/usuarios/actual/foto', { metodo: 'PUT', cuerpo: formulario })
}

// La foto es privada: se descarga con el token como Blob (un <img src> no envía Authorization).
export function obtenerFotoPerfil() {
  return peticionArchivo('/api/usuarios/actual/foto')
}

export function quitarFotoPerfil() {
  return peticion('/api/usuarios/actual/foto', { metodo: 'DELETE' })
}

export function solicitarCambioCorreo({ correo, contrasena }) {
  return peticion('/api/usuarios/actual/cambios-correo', { metodo: 'POST', cuerpo: { correo, contrasena } })
}

// Pública (HU-20 D21): el token del enlace va en el cuerpo y un 400 no debe borrar la sesión del navegador.
export function confirmarCambioCorreo(token) {
  return peticion('/api/cambios-correo/confirmaciones', {
    metodo: 'POST',
    cuerpo: { token },
    autenticada: false,
  })
}

export function actualizarFacturacion({ tipoDocumento, numeroDocumento, razonSocial, direccionFiscal, contrasena }) {
  return peticion('/api/arrendadores/actual/facturacion', {
    metodo: 'PUT',
    cuerpo: { tipoDocumento, numeroDocumento, razonSocial, direccionFiscal, contrasena },
  })
}

// Derechos sobre datos personales (HU-22). POST con la contraseña en el cuerpo: nunca en la URL. La exportación
// devuelve el JSON y la pantalla arma el archivo.
export function exportarMisDatos({ contrasena }) {
  return peticion('/api/usuarios/actual/exportaciones', { metodo: 'POST', cuerpo: { contrasena } })
}

export function eliminarMiCuenta({ contrasena }) {
  return peticion('/api/usuarios/actual/eliminacion', { metodo: 'POST', cuerpo: { contrasena } })
}
