import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { consultarBloquesLibres, obtenerEspacio } from '../../api/busqueda.js'
import { urlApi } from '../../api/cliente.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Campo from '../../components/ui/Campo.jsx'
import Galeria from '../../components/ui/Galeria.jsx'
import SelectorBloques from '../../components/ui/SelectorBloques.jsx'
import { formatoPrecio, nombreTipo } from '../espacios/textosEspacios.js'
import { ventanaBusqueda } from './textosBusqueda.js'
import {
  CARGANDO,
  ENLACE_BUSCAR,
  POLITICA_CANCELACION,
  SIN_BLOQUES,
  TITULO_BLOQUES,
  TITULO_POLITICA,
} from './textosFicha.js'
import estilos from './FichaEspacio.module.css'

const NO_ENCONTRADO = 404
const CAMPO_FECHA = 'fecha'

function errorDeFecha(error) {
  return error?.errores?.find((e) => e.campo === CAMPO_FECHA)?.mensaje ?? null
}

// HU-11: ficha pública de solo lectura. La fecha vive en la URL (?fecha=) para compartir el enlace y para que HU-12
// vuelva aquí tras el login; al cambiarla solo se piden los bloques libres de nuevo.
function FichaEspacio() {
  const { id } = useParams()
  const [parametros, setParametros] = useSearchParams()
  const { hoy, hasta } = ventanaBusqueda()
  const fecha = parametros.get('fecha') ?? hoy
  const consultaBloques = `${id}|${fecha}`

  const [respuestaFicha, setRespuestaFicha] = useState(null)
  const [respuestaBloques, setRespuestaBloques] = useState(null)

  useEffect(() => {
    let activo = true
    obtenerEspacio(id)
      .then((datos) => {
        if (activo) setRespuestaFicha({ id, datos, error: null })
      })
      .catch((error) => {
        if (activo) setRespuestaFicha({ id, datos: null, error })
      })
    return () => {
      activo = false
    }
  }, [id])

  // Una respuesta que llega después de otro cambio de fecha se descarta (activo = false).
  useEffect(() => {
    let activo = true
    consultarBloquesLibres(id, fecha)
      .then((datos) => {
        if (activo) setRespuestaBloques({ consulta: consultaBloques, datos, error: null })
      })
      .catch((error) => {
        if (activo) setRespuestaBloques({ consulta: consultaBloques, datos: null, error })
      })
    return () => {
      activo = false
    }
  }, [id, fecha, consultaBloques])

  const ficha = respuestaFicha?.id === id ? respuestaFicha : null
  const bloques = respuestaBloques?.consulta === consultaBloques ? respuestaBloques : null
  const errorFecha = errorDeFecha(bloques?.error)
  const noEncontrado = ficha?.error?.estado === NO_ENCONTRADO || bloques?.error?.estado === NO_ENCONTRADO

  function cambiarFecha(valor) {
    setParametros({ fecha: valor }, { replace: true })
  }

  if (noEncontrado) {
    const error = ficha?.error?.estado === NO_ENCONTRADO ? ficha.error : bloques.error
    return (
      <section className={estilos.pantalla}>
        <Alerta tipo="error">{error.mensaje}</Alerta>
        <p>
          <Link to="/buscar">{ENLACE_BUSCAR}</Link>
        </p>
      </section>
    )
  }

  if (!ficha) {
    return (
      <section className={estilos.pantalla}>
        <p className={estilos.secundario}>{CARGANDO}</p>
      </section>
    )
  }

  if (ficha.error) {
    return (
      <section className={estilos.pantalla}>
        <Alerta tipo="error">{ficha.error.mensaje}</Alerta>
      </section>
    )
  }

  const espacio = ficha.datos

  return (
    <section className={estilos.pantalla}>
      <h1 className={estilos.titulo}>{espacio.nombre}</h1>
      <p className={estilos.subtitulo}>{`${nombreTipo(espacio.tipo)} · ${espacio.zonaNombre}`}</p>

      <div className={estilos.contenido}>
        <Galeria fotos={espacio.fotos.map(urlApi)} alt={espacio.nombre} />

        <div className={estilos.datos}>
          <p className={estilos.precio}>{`${formatoPrecio(espacio.precioHora)} por hora`}</p>
          <p className={estilos.dato}>{`Dirección: ${espacio.direccion}`}</p>
          <p className={estilos.dato}>{`Capacidad: ${espacio.capacidad} personas`}</p>
          <p className={estilos.descripcion}>{espacio.descripcion}</p>

          <h2 className={estilos.seccion}>{TITULO_POLITICA}</h2>
          <ul className={estilos.politica}>
            {POLITICA_CANCELACION.map((regla) => (
              <li key={regla}>{regla}</li>
            ))}
          </ul>
        </div>
      </div>

      <h2 className={estilos.seccion}>{TITULO_BLOQUES}</h2>
      <div className={estilos.fecha}>
        <Campo
          etiqueta="Fecha"
          nombre="fecha"
          tipo="date"
          valor={fecha}
          onChange={cambiarFecha}
          min={hoy}
          max={hasta}
          error={errorFecha}
        />
      </div>
      {!bloques && <p className={estilos.secundario}>{CARGANDO}</p>}
      {bloques?.error && !errorFecha && <Alerta tipo="error">{bloques.error.mensaje}</Alerta>}
      {bloques?.datos && bloques.datos.bloquesLibres.length === 0 && (
        <p className={estilos.secundario}>{SIN_BLOQUES}</p>
      )}
      {bloques?.datos && (
        <SelectorBloques bloques={bloques.datos.bloquesLibres.map((bloque) => ({ ...bloque, libre: true }))} />
      )}
    </section>
  )
}

export default FichaEspacio
