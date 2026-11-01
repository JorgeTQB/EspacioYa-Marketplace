import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { listarReembolsosPorAtender, reintentarReembolso } from '../../api/pagos.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Modal from '../../components/ui/Modal.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { fechaHora } from '../administracion/textosAdministracion.js'
import { formatoPrecio } from '../espacios/textosEspacios.js'
import {
  BOTON_REINTENTAR,
  CARGANDO_REEMBOLSOS,
  CONFIRMACION_REINTENTO_ANTES,
  DESCRIPCION_REEMBOLSOS,
  ORIGENES_REEMBOLSO,
  REEMBOLSO_VOLVIO_A_FALLAR,
  SIN_REEMBOLSOS,
  TITULO_MODAL_REINTENTO,
  TITULO_REEMBOLSOS,
  confirmacionReintentoDespues,
  reembolsoRealizado,
  tituloReembolso,
} from './textosPagos.js'
import estilos from './ReembolsosPorAtender.module.css'

// CA2 de HU-16: el admin ve los reembolsos que la pasarela no confirmó y los reintenta con un botón. Qué reembolsos
// aparecen, su estado y su monto vienen de la API; la pantalla no calcula nada.
function ReembolsosPorAtender() {
  const { sesion } = useSesion()
  const esAdmin = sesion?.rol === 'ADMIN'
  const [reembolsos, setReembolsos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [recarga, setRecarga] = useState(0)
  const [elegido, setElegido] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState(null)

  useEffect(() => {
    if (!esAdmin) return undefined
    let activo = true
    listarReembolsosPorAtender()
      .then((datos) => {
        if (activo) {
          setReembolsos(datos)
          setError(null)
        }
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
  }, [esAdmin, recarga])

  if (!esAdmin) return <Navigate to="/panel" replace />

  function recargar() {
    setCargando(true)
    setRecarga((n) => n + 1)
  }

  function cerrarModal() {
    if (!enviando) setElegido(null)
  }

  function confirmarReintento() {
    const reembolso = elegido
    setEnviando(true)
    setResultado(null)
    reintentarReembolso(reembolso.id)
      .then((respuesta) => {
        if (respuesta.estado === 'REEMBOLSADO') {
          setResultado({ tipo: 'exito', texto: reembolsoRealizado(formatoPrecio(respuesta.monto)) })
        } else {
          setResultado({ tipo: 'error', texto: REEMBOLSO_VOLVIO_A_FALLAR })
        }
      })
      .catch((falla) => {
        setResultado({ tipo: 'error', texto: falla.mensaje })
      })
      .finally(() => {
        setEnviando(false)
        setElegido(null)
        recargar()
      })
  }

  return (
    <section className={estilos.pantalla}>
      <h2>{TITULO_REEMBOLSOS}</h2>
      <p className={estilos.secundario}>{DESCRIPCION_REEMBOLSOS}</p>

      {resultado && <Alerta tipo={resultado.tipo}>{resultado.texto}</Alerta>}
      {error && <Alerta tipo="error">{error}</Alerta>}
      {cargando && <p className={estilos.secundario}>{CARGANDO_REEMBOLSOS}</p>}

      {!cargando && !error && reembolsos.length === 0 && <p className={estilos.secundario}>{SIN_REEMBOLSOS}</p>}

      {reembolsos.length > 0 && (
        <ul className={estilos.lista}>
          {reembolsos.map((reembolso) => (
            <li key={reembolso.id}>
              <Tarjeta
                titulo={tituloReembolso(reembolso.reservaId)}
                acciones={
                  <Boton variante="secundario" deshabilitado={enviando} onClick={() => setElegido(reembolso)}>
                    {BOTON_REINTENTAR}
                  </Boton>
                }
              >
                <div>
                  <EtiquetaEstado estado={reembolso.estado} />
                </div>
                <p className={estilos.dato}>
                  Monto: <strong className={estilos.monto}>{formatoPrecio(reembolso.monto)}</strong>
                </p>
                <p className={estilos.dato}>Origen: {ORIGENES_REEMBOLSO[reembolso.origen] ?? reembolso.origen}</p>
                <p className={estilos.dato}>Intentos: {reembolso.intentos}</p>
                <p className={estilos.dato}>Solicitado el {fechaHora(reembolso.creadoEn)}</p>
                {reembolso.ultimoIntentoEn && (
                  <p className={estilos.dato}>Último intento: {fechaHora(reembolso.ultimoIntentoEn)}</p>
                )}
              </Tarjeta>
            </li>
          ))}
        </ul>
      )}

      <Modal
        abierto={elegido !== null}
        titulo={TITULO_MODAL_REINTENTO}
        textoConfirmar={BOTON_REINTENTAR}
        variante="primario"
        cargando={enviando}
        onConfirmar={confirmarReintento}
        onCerrar={cerrarModal}
      >
        {elegido && (
          <p>
            {CONFIRMACION_REINTENTO_ANTES}
            <strong>{formatoPrecio(elegido.monto)}</strong>
            {confirmacionReintentoDespues(elegido.reservaId)}
          </p>
        )}
      </Modal>
    </section>
  )
}

export default ReembolsosPorAtender
