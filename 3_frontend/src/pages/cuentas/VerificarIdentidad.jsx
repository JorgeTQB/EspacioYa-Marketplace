import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { urlApi } from '../../api/cliente.js'
import { enviarDocumentosIdentidad, obtenerIdentidad, obtenerSesionActual } from '../../api/cuentas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import useSesion from '../../sesion/useSesion.js'
import estilos from './VerificarIdentidad.module.css'

const TAMANO_MAXIMO = 5 * 1024 * 1024
const ACEPTADOS = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png'
const AYUDA_ARCHIVO = 'PDF, JPG o PNG de hasta 5 MB.'
const MENSAJE_TAMANO = 'El archivo debe pesar como máximo 5 MB.'
const FALTANTES = {
  documentoIdentidad: 'Sube tu documento de identidad (DNI o RUC).',
  documentoPropiedad: 'Sube el documento que acredita la propiedad o autorización del espacio.',
}
const NOMBRES_DOCUMENTO = {
  IDENTIDAD: 'Documento de identidad',
  PROPIEDAD: 'Documento de propiedad o autorización',
}

function erroresPorCampo(errores) {
  return Object.fromEntries(errores.map(({ campo, mensaje }) => [campo, mensaje]))
}

// Solo ayuda al usuario antes de enviar: el servidor revalida tipo y tamaño siempre.
function erroresDelCliente(archivos) {
  const errores = {}
  for (const [campo, archivo] of Object.entries(archivos)) {
    if (!archivo) errores[campo] = FALTANTES[campo]
    else if (archivo.size > TAMANO_MAXIMO) errores[campo] = MENSAJE_TAMANO
  }
  return errores
}

function VerificarIdentidad() {
  const { sesion, entrar } = useSesion()
  const esArrendador = sesion?.rol === 'ARRENDADOR'
  const [identidad, setIdentidad] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [archivos, setArchivos] = useState({ documentoIdentidad: null, documentoPropiedad: null })
  const [enviando, setEnviando] = useState(false)
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [errores, setErrores] = useState({})
  const [exito, setExito] = useState(false)

  useEffect(() => {
    if (!esArrendador) return undefined
    let activo = true
    obtenerIdentidad()
      .then((datos) => {
        if (activo) setIdentidad(datos)
      })
      .catch((error) => {
        if (activo) setErrorGeneral(error.mensaje)
      })
      .finally(() => {
        if (activo) setCargando(false)
      })
    return () => {
      activo = false
    }
  }, [esArrendador])

  if (!esArrendador) return <Navigate to="/panel" replace />

  function elegir(campo) {
    return (archivo) => setArchivos((actuales) => ({ ...actuales, [campo]: archivo }))
  }

  async function enviar(evento) {
    evento.preventDefault()
    setErrorGeneral(null)
    setExito(false)
    const delCliente = erroresDelCliente(archivos)
    setErrores(delCliente)
    if (Object.keys(delCliente).length > 0) return

    setEnviando(true)
    try {
      const respuesta = await enviarDocumentosIdentidad(archivos)
      setIdentidad(respuesta)
      setArchivos({ documentoIdentidad: null, documentoPropiedad: null })
      setExito(true)
      obtenerSesionActual()
        .then((datos) => entrar({ rol: datos.rol, aviso: datos.aviso }))
        .catch(() => {
          // El panel vuelve a pedir la sesión al entrar; aquí basta con la respuesta del envío.
        })
    } catch (error) {
      setErrorGeneral(error.mensaje)
      setErrores(erroresPorCampo(error.errores))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <section className={estilos.pantalla}>
      <div className={estilos.encabezado}>
        <h2>Verificación de identidad</h2>
        {identidad && <EtiquetaEstado estado={identidad.estadoIdentidad} />}
      </div>

      {exito && <Alerta tipo="exito">Recibimos tus documentos. Tu identidad está en revisión.</Alerta>}
      {errorGeneral && <Alerta tipo="error">{errorGeneral}</Alerta>}
      {cargando && <p className={estilos.secundario}>Cargando…</p>}

      {identidad?.motivoRechazo && (
        <Alerta tipo="error">Motivo del rechazo: {identidad.motivoRechazo}</Alerta>
      )}
      {identidad?.estadoIdentidad === 'EN_REVISION' && (
        <Alerta tipo="info">Estamos revisando tus documentos. Te avisaremos cuando terminemos.</Alerta>
      )}

      {identidad?.documentos?.length > 0 && (
        <div className={estilos.documentos}>
          <ul className={estilos.lista}>
            {identidad.documentos.map((documento) => (
              <li key={documento.id} className={estilos.documento}>
                <span>{NOMBRES_DOCUMENTO[documento.tipo] ?? documento.tipo}</span>
                <span className={estilos.secundario}>
                  {new Date(documento.subidoEn).toLocaleString('es-PE')}
                </span>
                <a href={urlApi(documento.url)} target="_blank" rel="noopener noreferrer">
                  Ver
                </a>
              </li>
            ))}
          </ul>
          <p className={estilos.ayuda}>El enlace vence en unos minutos; recarga la página para obtener uno nuevo.</p>
        </div>
      )}

      {identidad?.puedeEnviar && (
        <form className={estilos.formulario} onSubmit={enviar} noValidate>
          <Campo
            etiqueta="Documento de identidad (DNI o RUC)"
            nombre="documentoIdentidad"
            tipo="archivo"
            accept={ACEPTADOS}
            ayuda={AYUDA_ARCHIVO}
            onChange={elegir('documentoIdentidad')}
            error={errores.documentoIdentidad}
          />
          <Campo
            etiqueta="Documento de propiedad o autorización del espacio"
            nombre="documentoPropiedad"
            tipo="archivo"
            accept={ACEPTADOS}
            ayuda={AYUDA_ARCHIVO}
            onChange={elegir('documentoPropiedad')}
            error={errores.documentoPropiedad}
          />
          <Boton tipo="submit" cargando={enviando}>
            Enviar documentos
          </Boton>
        </form>
      )}
    </section>
  )
}

export default VerificarIdentidad
