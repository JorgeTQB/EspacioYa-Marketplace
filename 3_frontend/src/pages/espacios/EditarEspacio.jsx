import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import {
  crearEspacio,
  editarEspacio,
  eliminarFotoEspacio,
  listarZonas,
  obtenerMiEspacio,
  publicarEspacio,
  subirFotoEspacio,
} from '../../api/espacios.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import useSesion from '../../sesion/useSesion.js'
import FotoDelEspacio from './FotoDelEspacio.jsx'
import { TIPOS_ESPACIO } from './textosEspacios.js'
import estilos from './EditarEspacio.module.css'

const MAXIMO_FOTOS = 5
const TAMANO_MAXIMO = 5 * 1024 * 1024
const ACEPTADAS = '.jpg,.jpeg,.png,image/jpeg,image/png'
const MENSAJE_CREADO =
  'Guardamos tu espacio como borrador. Agrega fotos y publícalo cuando tu identidad esté verificada.'
const MENSAJE_GUARDADO = 'Guardamos los cambios.'
const MENSAJE_PUBLICADO = 'Tu espacio está publicado.'
const AVISO_PUBLICADO =
  'Este espacio está publicado: los cambios que guardes se verán de inmediato en el catálogo.'
const MENSAJE_FALTA_FOTO = 'Elige una foto.'
const MENSAJE_TAMANO = 'El archivo debe pesar como máximo 5 MB.'
const MENSAJE_MAXIMO_FOTOS = 'Ya tienes 5 fotos. Quita una para subir otra.'
const FICHA_VACIA = {
  nombre: '',
  tipo: '',
  zona: '',
  direccion: '',
  capacidad: '',
  precioHora: '',
  descripcion: '',
}

function fichaDe(espacio) {
  return {
    nombre: espacio.nombre,
    tipo: espacio.tipo,
    zona: espacio.zona,
    direccion: espacio.direccion,
    capacidad: String(espacio.capacidad),
    precioHora: String(espacio.precioHora),
    descripcion: espacio.descripcion,
  }
}

// Solo convierte el texto de los campos numéricos: el servidor valida rangos y decimales.
function numeroONulo(texto) {
  return texto === '' ? null : Number(texto)
}

function cuerpoDe(ficha) {
  return { ...ficha, capacidad: numeroONulo(ficha.capacidad), precioHora: numeroONulo(ficha.precioHora) }
}

function erroresPorCampo(errores) {
  return Object.fromEntries(errores.map(({ campo, mensaje }) => [campo, mensaje]))
}

function EditarEspacio() {
  const { id } = useParams()
  const esNuevo = id === undefined
  const { sesion } = useSesion()
  const esArrendador = sesion?.rol === 'ARRENDADOR'
  const navegar = useNavigate()
  const ubicacion = useLocation()
  const [zonas, setZonas] = useState([])
  const [espacio, setEspacio] = useState(null)
  const [ficha, setFicha] = useState(FICHA_VACIA)
  const [noEncontrado, setNoEncontrado] = useState(null)
  const [alerta, setAlerta] = useState(() =>
    ubicacion.state?.exito ? { tipo: 'exito', texto: ubicacion.state.exito } : null,
  )
  const [errores, setErrores] = useState({})
  const [guardando, setGuardando] = useState(false)
  const [archivo, setArchivo] = useState(null)
  const [errorFoto, setErrorFoto] = useState(null)
  const [versionArchivo, setVersionArchivo] = useState(0)
  const [subiendo, setSubiendo] = useState(false)
  const [quitando, setQuitando] = useState(null)
  const [publicando, setPublicando] = useState(false)

  useEffect(() => {
    if (!esArrendador) return undefined
    let activo = true
    listarZonas()
      .then((datos) => {
        if (activo) setZonas(datos)
      })
      .catch((error) => {
        if (activo) setAlerta({ tipo: 'error', texto: error.mensaje })
      })
    return () => {
      activo = false
    }
  }, [esArrendador])

  useEffect(() => {
    if (!esArrendador || esNuevo) return undefined
    let activo = true
    obtenerMiEspacio(id)
      .then((datos) => {
        if (!activo) return
        setEspacio(datos)
        setFicha(fichaDe(datos))
      })
      .catch((error) => {
        if (activo) setNoEncontrado(error.mensaje)
      })
    return () => {
      activo = false
    }
  }, [esArrendador, esNuevo, id])

  if (!esArrendador) return <Navigate to="/panel" replace />

  const cargando = !esNuevo && !noEncontrado && String(espacio?.id) !== id
  const actual = cargando ? null : espacio
  const fotos = actual?.fotos ?? []
  const cupoLleno = fotos.length >= MAXIMO_FOTOS

  function cambiar(campo) {
    return (valor) => setFicha((previa) => ({ ...previa, [campo]: valor }))
  }

  async function guardar(evento) {
    evento.preventDefault()
    setAlerta(null)
    setErrores({})
    setGuardando(true)
    try {
      if (esNuevo) {
        const creado = await crearEspacio(cuerpoDe(ficha))
        setEspacio(creado)
        setFicha(fichaDe(creado))
        setAlerta({ tipo: 'exito', texto: MENSAJE_CREADO })
        navegar(`/panel/espacios/${creado.id}`, { replace: true, state: { exito: MENSAJE_CREADO } })
      } else {
        const editado = await editarEspacio(id, cuerpoDe(ficha))
        setEspacio(editado)
        setFicha(fichaDe(editado))
        setAlerta({ tipo: 'exito', texto: MENSAJE_GUARDADO })
      }
    } catch (error) {
      setAlerta({ tipo: 'error', texto: error.mensaje })
      setErrores(erroresPorCampo(error.errores))
    } finally {
      setGuardando(false)
    }
  }

  async function subirFoto() {
    setAlerta(null)
    setErrorFoto(null)
    // Solo ayuda antes de enviar: el servidor revalida tipo, tamaño y cupo siempre.
    if (!archivo) {
      setErrorFoto(MENSAJE_FALTA_FOTO)
      return
    }
    if (archivo.size > TAMANO_MAXIMO) {
      setErrorFoto(MENSAJE_TAMANO)
      return
    }
    setSubiendo(true)
    try {
      setEspacio(await subirFotoEspacio(espacio.id, archivo))
      setArchivo(null)
      setVersionArchivo((version) => version + 1)
    } catch (error) {
      if (error.errores.length > 0) setErrorFoto(error.errores[0].mensaje)
      else setAlerta({ tipo: 'error', texto: error.mensaje })
    } finally {
      setSubiendo(false)
    }
  }

  async function quitarFoto(fotoId) {
    setAlerta(null)
    setErrorFoto(null)
    setQuitando(fotoId)
    try {
      setEspacio(await eliminarFotoEspacio(espacio.id, fotoId))
    } catch (error) {
      setAlerta({ tipo: 'error', texto: error.mensaje })
    } finally {
      setQuitando(null)
    }
  }

  async function publicar() {
    setAlerta(null)
    setPublicando(true)
    try {
      setEspacio(await publicarEspacio(espacio.id))
      setAlerta({ tipo: 'exito', texto: MENSAJE_PUBLICADO })
    } catch (error) {
      setAlerta({ tipo: 'error', texto: error.mensaje, enlaceIdentidad: Boolean(sesion.aviso) })
    } finally {
      setPublicando(false)
    }
  }

  if (noEncontrado) {
    return (
      <section className={estilos.pantalla}>
        <Alerta tipo="error">
          {noEncontrado} <Link to="/panel/espacios">Volver a mis espacios</Link>
        </Alerta>
      </section>
    )
  }

  return (
    <section className={estilos.pantalla}>
      <div className={estilos.encabezado}>
        <h2>{esNuevo ? 'Nuevo espacio' : (actual?.nombre ?? '')}</h2>
        {actual && <EtiquetaEstado estado={actual.estado} />}
        {actual && <Link to={`/panel/espacios/${actual.id}/disponibilidad`}>Definir disponibilidad</Link>}
      </div>

      {alerta && (
        <Alerta tipo={alerta.tipo}>
          {alerta.texto}
          {alerta.enlaceIdentidad && (
            <>
              {' '}
              <Link to="/panel/identidad">Verificar mi identidad</Link>
            </>
          )}
        </Alerta>
      )}
      {actual?.estado === 'PUBLICADO' && <Alerta tipo="info">{AVISO_PUBLICADO}</Alerta>}
      {cargando && <p className={estilos.secundario}>Cargando…</p>}

      {!cargando && (
        <form className={estilos.bloque} onSubmit={guardar} noValidate>
          <Campo
            etiqueta="Nombre del espacio"
            nombre="nombre"
            valor={ficha.nombre}
            onChange={cambiar('nombre')}
            error={errores.nombre}
          />
          <Campo
            etiqueta="Tipo"
            nombre="tipo"
            tipo="select"
            opciones={TIPOS_ESPACIO}
            valor={ficha.tipo}
            onChange={cambiar('tipo')}
            error={errores.tipo}
          />
          <Campo
            etiqueta="Zona"
            nombre="zona"
            tipo="select"
            opciones={zonas.map((zona) => ({ valor: zona.codigo, etiqueta: zona.nombre }))}
            valor={ficha.zona}
            onChange={cambiar('zona')}
            error={errores.zona}
          />
          <Campo
            etiqueta="Dirección"
            nombre="direccion"
            valor={ficha.direccion}
            onChange={cambiar('direccion')}
            error={errores.direccion}
          />
          <Campo
            etiqueta="Capacidad (personas)"
            nombre="capacidad"
            tipo="number"
            valor={ficha.capacidad}
            onChange={cambiar('capacidad')}
            error={errores.capacidad}
          />
          <Campo
            etiqueta="Precio por hora (S/)"
            nombre="precioHora"
            tipo="number"
            valor={ficha.precioHora}
            onChange={cambiar('precioHora')}
            ayuda="Precio único por hora, en soles."
            error={errores.precioHora}
          />
          <Campo
            etiqueta="Descripción"
            nombre="descripcion"
            tipo="textarea"
            valor={ficha.descripcion}
            onChange={cambiar('descripcion')}
            ayuda="De 10 a 2000 caracteres. Cuenta qué incluye el espacio."
            error={errores.descripcion}
          />
          <Boton tipo="submit" cargando={guardando}>
            {esNuevo ? 'Guardar borrador' : 'Guardar cambios'}
          </Boton>
        </form>
      )}

      {actual && (
        <div className={estilos.bloque}>
          <h3>Fotos (hasta 5)</h3>
          {fotos.length > 0 && (
            <ul className={estilos.fotos}>
              {fotos.map((foto) => (
                <li key={foto.id} className={estilos.foto}>
                  <FotoDelEspacio url={foto.url} alt={`Foto ${foto.orden} del espacio`} />
                  {foto.orden === 1 && <span className={estilos.principal}>Principal</span>}
                  <Boton
                    variante="peligro"
                    cargando={quitando === foto.id}
                    deshabilitado={quitando !== null && quitando !== foto.id}
                    onClick={() => quitarFoto(foto.id)}
                  >
                    Quitar foto
                  </Boton>
                </li>
              ))}
            </ul>
          )}
          <Campo
            key={versionArchivo}
            etiqueta="Agregar foto"
            nombre="foto"
            tipo="archivo"
            accept={ACEPTADAS}
            ayuda="JPG o PNG de hasta 5 MB."
            onChange={setArchivo}
            error={errorFoto}
          />
          {cupoLleno && <p className={estilos.secundario}>{MENSAJE_MAXIMO_FOTOS}</p>}
          <Boton variante="secundario" cargando={subiendo} deshabilitado={cupoLleno} onClick={subirFoto}>
            Subir foto
          </Boton>
        </div>
      )}

      {actual?.estado === 'BORRADOR' && (
        <div className={estilos.bloque}>
          <h3>Publicación</h3>
          <p className={estilos.secundario}>Cuando lo publiques, tu espacio aparecerá en el catálogo.</p>
          <Boton variante="secundario" cargando={publicando} onClick={publicar}>
            Publicar
          </Boton>
        </div>
      )}
    </section>
  )
}

export default EditarEspacio
