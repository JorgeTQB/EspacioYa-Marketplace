// El token vive en sessionStorage (se borra al cerrar la pestaña), nunca en localStorage ni en cookies (HU-03, DT-2).
const CLAVE = 'espacioya.token'

export function leerToken() {
  try {
    return sessionStorage.getItem(CLAVE)
  } catch {
    return null
  }
}

export function guardarToken(token) {
  try {
    sessionStorage.setItem(CLAVE, token)
  } catch {
    // Sin sessionStorage (modo privado estricto) la sesión dura solo mientras la página siga abierta.
  }
}

export function borrarToken() {
  try {
    sessionStorage.removeItem(CLAVE)
  } catch {
    // Nada que borrar si sessionStorage no está disponible.
  }
}
