import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { buscarEspacios } from '../../api/busqueda.js'
import { urlApi } from '../../api/cliente.js'
import { listarZonas } from '../../api/espacios.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import Imagen from '../../components/ui/Imagen.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import { TIPOS_ESPACIO, formatoPrecio } from '../espacios/textosEspacios.js'
import { SIN_MAS_RESULTADOS, SIN_RESULTADOS, SUGERENCIA_SIN_RESULTADOS, ventanaBusqueda } from './textosBusqueda.js'
import estilos from './BuscarEspacios.module.css'

const CAMPOS_FORMULARIO = ['tipo', 'zona', 'fecha']

function erroresPorCampo(error) {
  const porCampo = {}
  for (const { campo, mensaje } of error?.errores ?? []) {
    if (CAMPOS_FORMULARIO.includes(campo)) porCampo[campo] = mensaje
  }
  return porCampo
}

// Los filtros viven en la URL (?tipo=&zona=&fecha=&pagina=): "Atrás" y un enlace compartido repiten la búsqueda.
function BuscarEspacios() {
  const [parametros, setParametros] = useSearchParams()
  const { hoy, hasta } = ventanaBusqueda()
  const tipo = parametros.get('tipo')
  const zona = parametros.get('zona')
  const fecha = parametros.get('fecha')
  const pagina = parametros.get('pagina') ?? '1'
  const hayBusqueda = tipo !== null && zona !== null && fecha !== null
  const consulta = hayBusqueda ? parametros.toString() : null

  const [zonas, setZonas] = useState([])
  const [errorZonas, setErrorZonas] = useState(null)
  const [respuesta, setRespuesta] = useState(null)
  const [borrador, setBorrador] = useState(null)

  useEffect(() => {
    let activo = true
    listarZonas()
      .then((datos) => {
        if (activo) setZonas(datos)
      })
      .catch((error) => {
        if (activo) setErrorZonas(error.mensaje)
      })
    return () => {
      activo = false
    }
  }, [])

  useEffect(() => {
    if (!hayBusqueda) return undefined
    let activo = true
    buscarEspacios({ tipo, zona, fecha, pagina })
      .then((datos) => {
        if (activo) setRespuesta({ consulta, datos, error: null })
      })
      .catch((error) => {
        if (activo) setRespuesta({ consulta, datos: null, error })
      })
    return () => {
      activo = false
    }
  }, [hayBusqueda, consulta, tipo, zona, fecha, pagina])

  // El formulario parte de la URL y se reinicia cuando la URL cambia (p. ej. con "Atrás").
  const valores =
    borrador?.consulta === consulta ? borrador : { consulta, tipo: tipo ?? '', zona: zona ?? '', fecha: fecha ?? hoy }
  const vigente = respuesta?.consulta === consulta ? respuesta : null
  const buscando = hayBusqueda && !vigente
  const errores = erroresPorCampo(vigente?.error)

  function cambiar(campo) {
    return (valor) => setBorrador({ ...valores, [campo]: valor })
  }

  function alBuscar(evento) {
    evento.preventDefault()
    setParametros({ tipo: valores.tipo, zona: valores.zona, fecha: valores.fecha, pagina: '1' })
  }

  function irAPagina(numero) {
    setParametros({ tipo, zona, fecha, pagina: String(numero) })
  }

  const opcionesZona = zonas.map((z) => ({ valor: z.codigo, etiqueta: z.nombre }))
  const datos = vigente?.datos
  const numeroPagina = datos?.pagina ?? Number(pagina)

  return (
    <section className={estilos.pantalla}>
      <h2>Buscar espacios</h2>
      {errorZonas && <Alerta tipo="error">{errorZonas}</Alerta>}
      <form className={estilos.formulario} onSubmit={alBuscar} noValidate>
        <Campo
          etiqueta="Tipo de espacio"
          nombre="tipo"
          tipo="select"
          valor={valores.tipo}
          onChange={cambiar('tipo')}
          opciones={TIPOS_ESPACIO}
          error={errores.tipo}
        />
        <Campo
          etiqueta="Zona"
          nombre="zona"
          tipo="select"
          valor={valores.zona}
          onChange={cambiar('zona')}
          opciones={opcionesZona}
          error={errores.zona}
        />
        <Campo
          etiqueta="Fecha"
          nombre="fecha"
          tipo="date"
          valor={valores.fecha}
          onChange={cambiar('fecha')}
          min={hoy}
          max={hasta}
          error={errores.fecha}
        />
        <div className={estilos.enviar}>
          <Boton tipo="submit" cargando={buscando}>
            Buscar
          </Boton>
        </div>
      </form>

      {vigente?.error && <Alerta tipo="error">{vigente.error.mensaje}</Alerta>}

      {datos && datos.resultados.length === 0 && numeroPagina === 1 && (
        <div className={estilos.vacio}>
          <p className={estilos.secundario}>{SIN_RESULTADOS}</p>
          <p className={estilos.secundario}>{SUGERENCIA_SIN_RESULTADOS}</p>
        </div>
      )}
      {datos && datos.resultados.length === 0 && numeroPagina > 1 && (
        <p className={estilos.secundario}>{SIN_MAS_RESULTADOS}</p>
      )}

      {datos && datos.resultados.length > 0 && (
        <ul className={estilos.lista}>
          {datos.resultados.map((espacio) => (
            <li key={espacio.id}>
              <Tarjeta titulo={espacio.nombre}>
                <Imagen
                  src={espacio.fotoPrincipal ? urlApi(espacio.fotoPrincipal) : null}
                  alt={`Foto de ${espacio.nombre}`}
                />
                <p className={estilos.dato}>{espacio.zonaNombre}</p>
                <p className={estilos.precio}>{`${formatoPrecio(espacio.precioHora)} por hora`}</p>
              </Tarjeta>
            </li>
          ))}
        </ul>
      )}

      {datos && (datos.resultados.length > 0 || numeroPagina > 1) && (
        <nav className={estilos.paginacion} aria-label="Páginas de resultados">
          <Boton variante="secundario" deshabilitado={numeroPagina <= 1} onClick={() => irAPagina(numeroPagina - 1)}>
            Anterior
          </Boton>
          <span className={estilos.secundario}>{`Página ${numeroPagina}`}</span>
          <Boton variante="secundario" deshabilitado={!datos.hayMas} onClick={() => irAPagina(numeroPagina + 1)}>
            Siguiente
          </Boton>
        </nav>
      )}
    </section>
  )
}

export default BuscarEspacios
