import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { aprobarIdentidad, obtenerExpedienteIdentidad, rechazarIdentidad } from '../../api/administracion.js'
import { urlApi } from '../../api/cliente.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Modal from '../../components/ui/Modal.jsx'
import useSesion from '../../sesion/useSesion.js'
import { NOMBRES_DOCUMENTO, fechaHora } from './textosAdministracion.js'
import estilos from './RevisarIdentidad.module.css'

const COLA = '/admin/identidades'
const MENSAJE_MOTIVO = 'Escribe el motivo del rechazo (hasta 500 caracteres).'

function errorDeMotivo(errores) {
  return errores.find(({ campo }) => campo === 'motivo')?.mensaje ?? null
}

// CA1–CA3 de HU-06: el admin coteja datos y documentos y decide. La API valida el rol, el estado y el motivo.
function RevisarIdentidad() {
  const { id } = useParams()
  const { sesion } = useSesion()
  const navegar = useNavigate()
  const esAdmin = sesion?.rol === 'ADMIN'
  const [expediente, setExpediente] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [dialogo, setDialogo] = useState(null)
  const [motivo, setMotivo] = useState('')
  const [errorMotivo, setErrorMotivo] = useState(null)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!esAdmin) return undefined
    let activo = true
    obtenerExpedienteIdentidad(id)
      .then((datos) => {
        if (activo) setExpediente(datos)
      })
      .catch((falla) => {
        if (activo) setError(falla.mensaje)
      })
      .finally(() => {
        if (activo) setCargando(false)
      })
    return () => {
      activo = false
    }
  }, [esAdmin, id])

  if (!esAdmin) return <Navigate to="/panel" replace />

  function abrir(tipo) {
    setMotivo('')
    setErrorMotivo(null)
    setDialogo(tipo)
  }

  function cerrar() {
    if (!enviando) setDialogo(null)
  }

  async function decidir(enviar, mensajeExito) {
    setEnviando(true)
    try {
      await enviar()
      navegar(COLA, { state: { mensaje: mensajeExito } })
    } catch (falla) {
      const delCampo = errorDeMotivo(falla.errores)
      if (delCampo) {
        setErrorMotivo(delCampo)
      } else {
        // Ya revisada, 404, 403 o red: la decisión no se tomó y el expediente ya no sirve para decidir.
        setDialogo(null)
        setExpediente(null)
        setError(falla.mensaje)
      }
      setEnviando(false)
    }
  }

  function confirmarAprobacion() {
    decidir(() => aprobarIdentidad(id), `Aprobaste la identidad de ${expediente.nombre}.`)
  }

  // Solo ayuda: el servidor recorta y valida el motivo siempre.
  function confirmarRechazo() {
    if (!motivo.trim()) {
      setErrorMotivo(MENSAJE_MOTIVO)
      return
    }
    decidir(
      () => rechazarIdentidad(id, motivo),
      `Rechazaste la identidad de ${expediente.nombre}. Le avisamos el motivo por correo.`,
    )
  }

  return (
    <section className={estilos.pantalla}>
      {error && (
        <>
          <Alerta tipo="error">{error}</Alerta>
          <p>
            <Link to={COLA}>Volver a la cola</Link>
          </p>
        </>
      )}
      {cargando && <p className={estilos.secundario}>Cargando…</p>}

      {expediente && (
        <>
          <div className={estilos.encabezado}>
            <h2>{expediente.nombre}</h2>
            <EtiquetaEstado estado={expediente.estadoIdentidad} />
          </div>

          <dl className={estilos.datos}>
            <dt>Correo</dt>
            <dd>{expediente.correo}</dd>
            <dt>Teléfono</dt>
            <dd>{expediente.telefono}</dd>
            <dt>Tipo de documento</dt>
            <dd>{expediente.tipoDocumento}</dd>
            <dt>Número de documento</dt>
            <dd>{expediente.numeroDocumento}</dd>
            <dt>Razón social</dt>
            <dd>{expediente.razonSocial}</dd>
            <dt>Dirección fiscal</dt>
            <dd>{expediente.direccionFiscal}</dd>
            <dt>Fecha de envío</dt>
            <dd>{fechaHora(expediente.enviadoEn)}</dd>
          </dl>

          {expediente.documentos.length > 0 && (
            <div className={estilos.documentos}>
              <ul className={estilos.lista}>
                {expediente.documentos.map((documento) => (
                  <li key={documento.id} className={estilos.documento}>
                    <span>{NOMBRES_DOCUMENTO[documento.tipo] ?? documento.tipo}</span>
                    <a href={urlApi(documento.url)} target="_blank" rel="noopener noreferrer">
                      Ver
                    </a>
                  </li>
                ))}
              </ul>
              <p className={estilos.ayuda}>
                El enlace vence en unos minutos; recarga la página para obtener uno nuevo.
              </p>
            </div>
          )}

          <div className={estilos.acciones}>
            <Boton onClick={() => abrir('aprobar')}>Aprobar identidad</Boton>
            <Boton variante="peligro" onClick={() => abrir('rechazar')}>
              Rechazar identidad
            </Boton>
          </div>

          <Modal
            abierto={dialogo === 'aprobar'}
            titulo={`¿Aprobar la identidad de ${expediente.nombre}?`}
            textoConfirmar="Aprobar"
            variante="primario"
            cargando={enviando}
            onConfirmar={confirmarAprobacion}
            onCerrar={cerrar}
          >
            <p>Podrá publicar sus espacios y le avisaremos por correo.</p>
          </Modal>

          <Modal
            abierto={dialogo === 'rechazar'}
            titulo={`Rechazar la identidad de ${expediente.nombre}`}
            textoConfirmar="Rechazar"
            variante="peligro-confirmar"
            cargando={enviando}
            onConfirmar={confirmarRechazo}
            onCerrar={cerrar}
          >
            <Campo
              etiqueta="Motivo del rechazo"
              nombre="motivo"
              tipo="textarea"
              valor={motivo}
              onChange={setMotivo}
              error={errorMotivo}
              ayuda="El arrendador lo verá en su panel y en el correo. Hasta 500 caracteres."
            />
          </Modal>
        </>
      )}
    </section>
  )
}

export default RevisarIdentidad
