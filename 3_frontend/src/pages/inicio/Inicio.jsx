import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { listarZonas } from '../../api/espacios.js'
import fotoAuditorios from '../../assets/inicio/auditorios.webp'
import fotoCanchas from '../../assets/inicio/canchas.webp'
import imagenDecorativa from '../../assets/inicio/decorativa.webp'
import fotoEstadios from '../../assets/inicio/estadios.webp'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import { TIPOS_ESPACIO } from '../espacios/textosEspacios.js'
import { ventanaBusqueda } from '../busqueda/textosBusqueda.js'
import {
  IconoAuditorio,
  IconoCalendario,
  IconoCancha,
  IconoEscudo,
  IconoEstadio,
  IconoFlecha,
  IconoLupa,
  IconoReserva,
  IconoSoporte,
  IconoTipo,
  IconoUbicacion,
  IconoVerificado,
} from './iconos.jsx'
import {
  DESCRIPCION_INICIO,
  LEMA_ACENTO,
  LEMA_INICIO,
  TIPOS_INICIO,
  TITULO_ACENTO,
  TITULO_INICIO,
  VENTAJAS,
} from './textosInicio.js'
import estilos from './Inicio.module.css'

const FOTO_TIPO = { CANCHA: fotoCanchas, ESTADIO: fotoEstadios, AUDITORIO: fotoAuditorios }
const ICONO_TIPO = { CANCHA: IconoCancha, ESTADIO: IconoEstadio, AUDITORIO: IconoAuditorio }
const ICONO_VENTAJA = {
  reservas: IconoReserva,
  pagos: IconoEscudo,
  verificados: IconoVerificado,
  soporte: IconoSoporte,
}

// /buscar lee ?tipo=&zona=&fecha=: con los tres busca; con menos, solo rellena su formulario.
function rutaBusqueda(filtros) {
  const parametros = new URLSearchParams()
  for (const [clave, valor] of Object.entries(filtros)) {
    if (valor) parametros.set(clave, valor)
  }
  const consulta = parametros.toString()
  return consulta ? `/buscar?${consulta}` : '/buscar'
}

function Inicio() {
  const navegar = useNavigate()
  const { hash, key } = useLocation()
  const { hoy, hasta } = ventanaBusqueda()
  const [zonas, setZonas] = useState([])
  const [filtros, setFiltros] = useState({ zona: '', fecha: hoy, tipo: '' })

  useEffect(() => {
    let activo = true
    // Si falla, la lista queda vacía: /buscar muestra el error con su propio mensaje.
    listarZonas()
      .then((datos) => {
        if (activo) setZonas(datos)
      })
      .catch(() => {})
    return () => {
      activo = false
    }
  }, [])

  // "Cómo funciona" del encabezado llega como /#como-funciona: React Router no desplaza solo.
  useEffect(() => {
    if (hash) document.getElementById(hash.slice(1))?.scrollIntoView()
  }, [hash, key])

  function cambiar(campo) {
    return (valor) => setFiltros((anteriores) => ({ ...anteriores, [campo]: valor }))
  }

  function alBuscar(evento) {
    evento.preventDefault()
    navegar(rutaBusqueda(filtros))
  }

  return (
    <div className={estilos.inicio}>
      <section className={estilos.hero} aria-labelledby="titulo-inicio">
        <div className={estilos.heroContenido}>
          <p className={estilos.lema}>
            {LEMA_INICIO} <span className={estilos.lemaAcento}>{LEMA_ACENTO}</span>
          </p>
          <h2 id="titulo-inicio" className={estilos.titulo}>
            {TITULO_INICIO} <span className={estilos.tituloAcento}>{TITULO_ACENTO}</span>
          </h2>
          {DESCRIPCION_INICIO.map((linea) => (
            <p key={linea} className={estilos.descripcion}>
              {linea}
            </p>
          ))}

          <form className={estilos.buscador} role="search" aria-label="Buscar espacios" onSubmit={alBuscar} noValidate>
            <div className={estilos.filtro}>
              <IconoUbicacion className={estilos.iconoFiltro} />
              <Campo
                etiqueta="¿Dónde?"
                nombre="zona"
                tipo="select"
                valor={filtros.zona}
                onChange={cambiar('zona')}
                opciones={zonas.map((zona) => ({ valor: zona.codigo, etiqueta: zona.nombre }))}
              />
            </div>
            <div className={estilos.filtro}>
              <IconoCalendario className={estilos.iconoFiltro} />
              <Campo
                etiqueta="¿Cuándo?"
                nombre="fecha"
                tipo="date"
                valor={filtros.fecha}
                onChange={cambiar('fecha')}
                min={hoy}
                max={hasta}
              />
            </div>
            <div className={estilos.filtro}>
              <IconoTipo className={estilos.iconoFiltro} />
              <Campo
                etiqueta="Tipo de espacio"
                nombre="tipo"
                tipo="select"
                valor={filtros.tipo}
                onChange={cambiar('tipo')}
                opciones={TIPOS_ESPACIO}
              />
            </div>
            <div className={estilos.enviar}>
              <Boton tipo="submit">
                <IconoLupa className={estilos.iconoBoton} />
                Buscar
              </Boton>
            </div>
          </form>
        </div>
      </section>

      <section className={estilos.tipos} aria-labelledby="titulo-tipos">
        <div className={estilos.ancho}>
          <div className={estilos.cabeceraTipos}>
            <div>
              <p className={estilos.antetitulo}>Explora por tipo de espacio</p>
              <h2 id="titulo-tipos" className={estilos.tituloSeccion}>
                Encuentra el lugar ideal para cada ocasión
              </h2>
            </div>
            <Link to="/buscar" className={estilos.verTodos}>
              Ver todos los espacios
              <IconoFlecha className={estilos.iconoFlecha} />
            </Link>
          </div>

          <ul className={estilos.tarjetas}>
            {TIPOS_INICIO.map(({ valor, titulo, subtitulo }) => {
              const IconoDelTipo = ICONO_TIPO[valor]
              return (
                <li key={valor}>
                  <Link to={rutaBusqueda({ tipo: valor })} className={estilos.tarjeta}>
                    <img className={estilos.foto} src={FOTO_TIPO[valor]} alt="" loading="lazy" />
                    <div className={estilos.pieTarjeta}>
                      <span className={estilos.iconoTipo}>
                        <IconoDelTipo />
                      </span>
                      <span className={estilos.textoTarjeta}>
                        <span className={estilos.tituloTarjeta}>{titulo}</span>
                        <span className={estilos.subtituloTarjeta}>{subtitulo}</span>
                      </span>
                      <IconoFlecha className={estilos.iconoFlecha} />
                    </div>
                  </Link>
                </li>
              )
            })}
          </ul>
        </div>
      </section>

      <section id="como-funciona" className={estilos.seccionBanda} aria-labelledby="titulo-banda">
        <div className={estilos.banda}>
          <div className={estilos.bandaTexto}>
            <p className={estilos.antetitulo}>Tu experiencia, nuestra prioridad</p>
            <h2 id="titulo-banda" className={estilos.tituloSeccion}>
              Alquila fácil, rápido y seguro
            </h2>
            <p className={estilos.parrafoBanda}>
              En EspacioYa te acompañamos en todo el proceso, desde la búsqueda hasta el pago, para que solo te
              enfoques en lo importante: disfrutar tu espacio.
            </p>
            <Link to="/registro-arrendador" className={estilos.publicar}>
              ¿Tienes un espacio? Publícalo
              <IconoFlecha className={estilos.iconoFlecha} />
            </Link>
          </div>

          <ul className={estilos.ventajas}>
            {VENTAJAS.map(({ clave, titulo, texto }) => {
              const IconoVentaja = ICONO_VENTAJA[clave]
              return (
                <li key={clave} className={estilos.ventaja}>
                  <span className={estilos.iconoVentaja}>
                    <IconoVentaja />
                  </span>
                  <span>
                    <span className={estilos.tituloVentaja}>{titulo}</span>
                    <span className={estilos.textoVentaja}>{texto}</span>
                  </span>
                </li>
              )
            })}
          </ul>

          <img className={estilos.decorativa} src={imagenDecorativa} alt="" loading="lazy" />
        </div>
      </section>
    </div>
  )
}

export default Inicio
