import { borrarToken, leerToken } from './tokenSesion.js'

const URL_BASE = import.meta.env.VITE_API_URL ?? ''

const ERROR_RED = { mensaje: 'No pudimos conectar. Intenta de nuevo.', errores: [] }
const ERROR_GENERICO = 'Ocurrió un problema. Intenta de nuevo más tarde.'
const SIN_CONTENIDO = 204
export const NO_AUTENTICADO = 401

const suscriptoresSesionPerdida = new Set()

// Para enlaces que el navegador abre por su cuenta (p. ej. URL firmadas de archivos).
export function urlApi(ruta) {
  return `${URL_BASE}${ruta}`
}

export function alPerderSesion(callback) {
  suscriptoresSesionPerdida.add(callback)
  return () => suscriptoresSesionPerdida.delete(callback)
}

async function leerJson(respuesta) {
  try {
    return await respuesta.json()
  } catch {
    return null
  }
}

export async function peticion(ruta, { metodo = 'GET', cuerpo, autenticada = true } = {}) {
  const opciones = { method: metodo, headers: { Accept: 'application/json' } }
  if (cuerpo instanceof FormData) {
    // Sin Content-Type: el navegador lo pone con el boundary del multipart.
    opciones.body = cuerpo
  } else if (cuerpo !== undefined) {
    opciones.headers['Content-Type'] = 'application/json'
    opciones.body = JSON.stringify(cuerpo)
  }
  const token = autenticada ? leerToken() : null
  if (token) {
    opciones.headers.Authorization = `Bearer ${token}`
  }

  let respuesta
  try {
    respuesta = await fetch(`${URL_BASE}${ruta}`, opciones)
  } catch {
    throw ERROR_RED
  }

  if (respuesta.status === SIN_CONTENIDO) {
    return null
  }
  const datos = await leerJson(respuesta)
  if (!respuesta.ok) {
    const error = {
      estado: respuesta.status,
      mensaje: datos?.mensaje ?? ERROR_GENERICO,
      errores: Array.isArray(datos?.errores) ? datos.errores : [],
    }
    if (respuesta.status === NO_AUTENTICADO && token) {
      borrarToken()
      suscriptoresSesionPerdida.forEach((callback) => callback(error.mensaje))
    }
    throw error
  }
  return datos
}
