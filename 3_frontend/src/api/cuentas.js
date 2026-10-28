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
