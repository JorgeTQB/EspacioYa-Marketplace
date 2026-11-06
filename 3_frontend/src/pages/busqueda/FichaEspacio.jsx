import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { consultarBloquesLibres, obtenerEspacio } from '../../api/busqueda.js'
import { NO_AUTENTICADO, urlApi } from '../../api/cliente.js'
import { crearReserva } from '../../api/reservas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import Galeria from '../../components/ui/Galeria.jsx'
import SelectorBloques from '../../components/ui/SelectorBloques.jsx'
import useSesion from '../../sesion/useSesion.js'
import { formatoPrecio, nombreTipo } from '../espacios/textosEspacios.js'
import { AYUDA_CONSECUTIVOS, BOTON_RESERVAR, MENSAJE_INICIAR_SESION } from '../reservas/textosReservas.js'
import { ventanaBusqueda } from './textosBusqueda.js'
import {
  CARGANDO,
  ENLACE_BUSCAR,
  POLITICA_CANCELACION,
  SIN_BLOQUES,
  TITULO_BLOQUES,
  TITULO_POLITICA,
  resumenResenas,
} from './textosFicha.js'
import ResenasEspacio from './ResenasEspacio.jsx'
import estilos from './FichaEspacio.module.css'

const NO_ENCONTRADO = 404
const CAMPO_FECHA = 'fecha'
const ROL_QUE_RESERVA = 'USUARIO'

function errorDeFecha(error) {
  return error?.errores?.find((e) => e.campo === CAMPO_FECHA)?.mensaje ?? null
}

// Solo ayuda: el servidor revalida. SelectorBloques entrega las horas ordenadas.
function sonConsecutivas(horas) {
  return horas.length > 0 && horas.every((hora, i) => i === 0 || hora === horas[i - 1] + 1)
}

// HU-11 y HU-12: ficha pública desde la que se reserva. La fecha vive en la URL (?fecha=) para compartir el enlace y
// para volver aquí tras el login; al cambiarla solo se piden los bloques libres de nuevo. La selección y el error de
// una reserva se guardan junto a su consulta, así que cambiar de fecha los descarta sin un efecto.
function FichaEspacio() {
  const { id } = useParams()
  const navegar = useNavigate()
  const { sesion, cargando } = useSesion()
  const [parametros, setParametros] = useSearchParams()
  const { hoy, hasta } = ventanaBusqueda()
  const fecha = parametros.get('fecha') ?? hoy
  const consultaBloques = `${id}|${fecha}`

  const [respuestaFicha, setRespuestaFicha] = useState(null)
  const [respuestaBloques, setRespuestaBloques] = useState(null)
  const [recarga, setRecarga] = useState(0)
  const [seleccion, setSeleccion] = useState(null)
  const [fallo, setFallo] = useState(null)
  const [reservando, setReservando] = useState(false)

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
  }, [id, fecha, consultaBloques, recarga])

  const ficha = respuestaFicha?.id === id ? respuestaFicha : null
  const bloques = respuestaBloques?.consulta === consultaBloques ? respuestaBloques : null
  const errorFecha = errorDeFecha(bloques?.error)
  const noEncontrado = ficha?.error?.estado === NO_ENCONTRADO || bloques?.error?.estado === NO_ENCONTRADO
  const horas = seleccion?.consulta === consultaBloques ? seleccion.horas : []
  const mensajeFallo = fallo?.consulta === consultaBloques ? fallo.mensaje : null
  const consecutivas = sonConsecutivas(horas)
  // El rol solo elige qué mostrar; el servidor lo revalida. Un visitante también puede pulsar Reservar (CA4).
  const puedeReservar = !sesion || sesion.rol === ROL_QUE_RESERVA

  function cambiarFecha(valor) {
    setParametros({ fecha: valor }, { replace: true })
  }

  function cambiarSeleccion(nuevas) {
    setSeleccion({ consulta: consultaBloques, horas: nuevas })
  }

  async function reservar() {
    if (!sesion) {
      navegar('/iniciar-sesion', {
        state: { retorno: `/espacios/${id}?fecha=${fecha}`, mensaje: MENSAJE_INICIAR_SESION, tipo: 'info' },
      })
      return
    }
    setReservando(true)
    setFallo(null)
    try {
      const reserva = await crearReserva({ espacioId: ficha.datos.id, fecha, horas })
      navegar(`/reservas/${reserva.id}`)
    } catch (error) {
      setReservando(false)
      // Con 401 ProveedorSesion ya llevó al login, con retorno a esta ficha.
      if (error.estado === NO_AUTENTICADO) return
      setFallo({ consulta: consultaBloques, mensaje: error.mensaje })
      setSeleccion(null)
      setRecarga((actual) => actual + 1)
    }
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
          <p className={estilos.dato}>{resumenResenas(espacio.calificacionPromedio, espacio.totalResenas)}</p>

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
      {mensajeFallo && <Alerta tipo="error">{mensajeFallo}</Alerta>}
      {!bloques && <p className={estilos.secundario}>{CARGANDO}</p>}
      {bloques?.error && !errorFecha && <Alerta tipo="error">{bloques.error.mensaje}</Alerta>}
      {bloques?.datos && bloques.datos.bloquesLibres.length === 0 && (
        <p className={estilos.secundario}>{SIN_BLOQUES}</p>
      )}
      {bloques?.datos && (
        <SelectorBloques
          bloques={bloques.datos.bloquesLibres.map((bloque) => ({ ...bloque, libre: true }))}
          seleccionados={puedeReservar ? horas : undefined}
          onCambiar={puedeReservar ? cambiarSeleccion : undefined}
        />
      )}
      {puedeReservar && bloques?.datos?.bloquesLibres.length > 0 && (
        <div className={estilos.reservar}>
          {horas.length > 1 && !consecutivas && <p className={estilos.secundario}>{AYUDA_CONSECUTIVOS}</p>}
          <Boton cargando={reservando} deshabilitado={cargando || (sesion && !consecutivas)} onClick={reservar}>
            {BOTON_RESERVAR}
          </Boton>
        </div>
      )}

      <ResenasEspacio key={espacio.id} espacioId={espacio.id} />
    </section>
  )
}

export default FichaEspacio
