import { peticion } from './cliente.js'

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
