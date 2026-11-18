import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { buscarEspacios } from '../../api/busqueda.js'
import { urlApi } from '../../api/cliente.js'
import { listarZonas } from '../../api/espacios.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import GrupoCasillas from '../../components/ui/GrupoCasillas.jsx'
import Imagen from '../../components/ui/Imagen.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import { CARACTERISTICAS, TIPOS_ESPACIO, formatoPrecio } from '../espacios/textosEspacios.js'
import {
  BOTON_QUITAR_FILTROS,
  SIN_MAS_RESULTADOS,
  SIN_RESULTADOS,
  SIN_RESULTADOS_CON_FILTROS,
  SUGERENCIA_QUITAR_FILTRO,
  SUGERENCIA_SIN_RESULTADOS,
  ventanaBusqueda,
} from './textosBusqueda.js'
import estilos from './BuscarEspacios.module.css'

const CAMPOS_FORMULARIO = ['tipo', 'zona', 'fecha', 'precioMaximo', 'capacidadMinima', 'caracteristica']

function erroresPorCampo(error) {
  const porCampo = {}
  for (const { campo, mensaje } of error?.errores ?? []) {
    if (CAMPOS_FORMULARIO.includes(campo)) porCampo[campo] = mensaje
  }
  return porCampo
}

// Los filtros viven en la URL (?tipo=&zona=&fecha=&pagina= y, de HU-23, precioMaximo, capacidadMinima y
// caracteristica repetido): "Atrás" y un enlace compartido repiten la búsqueda.
function BuscarEspacios() {
  const [parametros, setParametros] = useSearchParams()
  const { hoy, hasta } = ventanaBusqueda()
  const tipo = parametros.get('tipo')
  const zona = parametros.get('zona')
  const fecha = parametros.get('fecha')
  const pagina = parametros.get('pagina') ?? '1'
  const precioMaximo = parametros.get('precioMaximo')
  const capacidadMinima = parametros.get('capacidadMinima')
  const caracteristicas = parametros.getAll('caracteristica')
  const hayFiltros = precioMaximo !== null || capacidadMinima !== null || caracteristicas.length > 0
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
    // Se lee de la consulta (un texto) para que el efecto dependa de un valor estable y no de un arreglo nuevo.
    const actuales = new URLSearchParams(consulta)
    buscarEspacios({
      tipo: actuales.get('tipo'),
      zona: actuales.get('zona'),
      fecha: actuales.get('fecha'),
      pagina: actuales.get('pagina') ?? '1',
      precioMaximo: actuales.get('precioMaximo') ?? '',
      capacidadMinima: actuales.get('capacidadMinima') ?? '',
      caracteristicas: actuales.getAll('caracteristica'),
    })
      .then((datos) => {
        if (activo) setRespuesta({ consulta, datos, error: null })
      })
      .catch((error) => {
        if (activo) setRespuesta({ consulta, datos: null, error })
      })
    return () => {
      activo = false
    }
  }, [hayBusqueda, consulta])

  // El formulario parte de la URL y se reinicia cuando la URL cambia (p. ej. con "Atrás").
  const valores =
    borrador?.consulta === consulta
      ? borrador
      : {
          consulta,
          tipo: tipo ?? '',
          zona: zona ?? '',
          fecha: fecha ?? hoy,
          precioMaximo: precioMaximo ?? '',
          capacidadMinima: capacidadMinima ?? '',
          caracteristicas,
        }
  const vigente = respuesta?.consulta === consulta ? respuesta : null
  const buscando = hayBusqueda && !vigente
  const errores = erroresPorCampo(vigente?.error)

  function cambiar(campo) {
    return (valor) => setBorrador({ ...valores, [campo]: valor })
  }

  // Los filtros vacíos no van a la URL: un filtro presente pero vacío es inválido para el servidor (HU-23 P5).
  function alBuscar(evento) {
    evento.preventDefault()
    const nuevos = new URLSearchParams({ tipo: valores.tipo, zona: valores.zona, fecha: valores.fecha, pagina: '1' })
    if (valores.precioMaximo !== '') nuevos.append('precioMaximo', valores.precioMaximo)
    if (valores.capacidadMinima !== '') nuevos.append('capacidadMinima', valores.capacidadMinima)
    for (const caracteristica of valores.caracteristicas) nuevos.append('caracteristica', caracteristica)
    setParametros(nuevos)
  }

  // Copia la URL actual y solo cambia la página: los filtros se conservan al paginar.
  function irAPagina(numero) {
    const nuevos = new URLSearchParams(parametros)
    nuevos.set('pagina', String(numero))
    setParametros(nuevos)
  }

  function quitarFiltros() {
    setParametros({ tipo, zona, fecha, pagina: '1' })
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
        <Campo
          etiqueta="Precio máximo por hora (S/)"
          nombre="precioMaximo"
          tipo="number"
          valor={valores.precioMaximo}
          onChange={cambiar('precioMaximo')}
          min="1"
          step="0.01"
          error={errores.precioMaximo}
        />
        <Campo
          etiqueta="Capacidad mínima (personas)"
          nombre="capacidadMinima"
          tipo="number"
          valor={valores.capacidadMinima}
          onChange={cambiar('capacidadMinima')}
          min="1"
          error={errores.capacidadMinima}
        />
        <div className={estilos.filaCompleta}>
          <GrupoCasillas
            leyenda="Características"
            nombre="caracteristica"
            opciones={CARACTERISTICAS}
            valores={valores.caracteristicas}
            onChange={cambiar('caracteristicas')}
            error={errores.caracteristica}
          />
        </div>
        <div className={estilos.enviar}>
          <Boton tipo="submit" cargando={buscando}>
            Buscar
          </Boton>
        </div>
      </form>

      {vigente?.error && <Alerta tipo="error">{vigente.error.mensaje}</Alerta>}

      {datos && datos.resultados.length === 0 && numeroPagina === 1 && !hayFiltros && (
        <div className={estilos.vacio}>
          <p className={estilos.secundario}>{SIN_RESULTADOS}</p>
          <p className={estilos.secundario}>{SUGERENCIA_SIN_RESULTADOS}</p>
        </div>
      )}
      {datos && datos.resultados.length === 0 && numeroPagina === 1 && hayFiltros && (
        <div className={estilos.vacio}>
          <p className={estilos.secundario}>{SIN_RESULTADOS_CON_FILTROS}</p>
          <p className={estilos.secundario}>{SUGERENCIA_QUITAR_FILTRO}</p>
          <div className={estilos.accionVacio}>
            <Boton variante="secundario" onClick={quitarFiltros}>
              {BOTON_QUITAR_FILTROS}
            </Boton>
          </div>
        </div>
      )}
      {datos && datos.resultados.length === 0 && numeroPagina > 1 && (
        <p className={estilos.secundario}>{SIN_MAS_RESULTADOS}</p>
      )}

      {datos && datos.resultados.length > 0 && (
        <ul className={estilos.lista}>
          {datos.resultados.map((espacio) => (
            <li key={espacio.id}>
              <Tarjeta
                titulo={espacio.nombre}
                acciones={
                  <Link to={`/espacios/${espacio.id}?${new URLSearchParams({ fecha })}`}>Ver espacio</Link>
                }
              >
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
