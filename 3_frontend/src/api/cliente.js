const URL_BASE = import.meta.env.VITE_API_URL ?? ''

const ERROR_RED = { mensaje: 'No pudimos conectar. Intenta de nuevo.', errores: [] }
const ERROR_GENERICO = 'Ocurrió un problema. Intenta de nuevo más tarde.'

async function leerJson(respuesta) {
  try {
    return await respuesta.json()
  } catch {
    return null
  }
}

export async function peticion(ruta, { metodo = 'GET', cuerpo } = {}) {
  const opciones = { method: metodo, headers: { Accept: 'application/json' } }
  if (cuerpo !== undefined) {
    opciones.headers['Content-Type'] = 'application/json'
    opciones.body = JSON.stringify(cuerpo)
  }

  let respuesta
  try {
    respuesta = await fetch(`${URL_BASE}${ruta}`, opciones)
  } catch {
    throw ERROR_RED
  }

  const datos = await leerJson(respuesta)
  if (!respuesta.ok) {
    throw {
      mensaje: datos?.mensaje ?? ERROR_GENERICO,
      errores: Array.isArray(datos?.errores) ? datos.errores : [],
    }
  }
  return datos
}
