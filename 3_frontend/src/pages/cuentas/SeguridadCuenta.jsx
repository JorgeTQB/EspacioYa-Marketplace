import { useEffect, useState } from 'react'
import { activarMfa, desactivarMfa, iniciarConfiguracionMfa, obtenerMfa } from '../../api/cuentas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import Modal from '../../components/ui/Modal.jsx'
import CodigosRespaldo from './CodigosRespaldo.jsx'
import ConfiguracionMfa from './ConfiguracionMfa.jsx'
import { erroresPorCampo } from './erroresCampo.js'
import {
  BOTON_ACTIVAR,
  BOTON_ACTIVAR_MFA,
  BOTON_DESACTIVAR,
  BOTON_LISTO,
  CARGANDO_SEGURIDAD,
  ETIQUETA_CODIGO,
  ETIQUETA_CONTRASENA,
  EXPLICACION_DESACTIVAR,
  MFA_ACTIVADO,
  MFA_ACTIVO,
  MFA_DESACTIVADO,
  MFA_INACTIVO,
  MFA_OBLIGATORIO_ADMIN,
  TITULO_DESACTIVAR,
  TITULO_SEGURIDAD,
  TITULO_VERIFICACION,
  codigosRestantes,
} from './textosCuentas.js'
import estilos from './SeguridadCuenta.module.css'

// HU-17 CA2, CA3: activar y desactivar la verificación en dos pasos. Qué se puede hacer lo dice la API (activo,
// desactivable); la pantalla no decide por el rol.
function SeguridadCuenta() {
  const [estado, setEstado] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [recarga, setRecarga] = useState(0)
  const [error, setError] = useState(null)
  const [exito, setExito] = useState(null)
  const [preparando, setPreparando] = useState(false)
  const [configuracion, setConfiguracion] = useState(null)
  const [codigos, setCodigos] = useState(null)
  const [modalAbierto, setModalAbierto] = useState(false)
  const [contrasena, setContrasena] = useState('')
  const [codigo, setCodigo] = useState('')
  const [desactivando, setDesactivando] = useState(false)
  const [errorModal, setErrorModal] = useState(null)
  const [erroresModal, setErroresModal] = useState({})

  useEffect(() => {
    let activo = true
    obtenerMfa()
      .then((datos) => {
        if (activo) {
          setEstado(datos)
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
  }, [recarga])

  function recargar() {
    setCargando(true)
    setRecarga((n) => n + 1)
  }

  async function empezarConfiguracion() {
    setPreparando(true)
    setError(null)
    setExito(null)
    try {
      setConfiguracion(await iniciarConfiguracionMfa())
    } catch (falla) {
      setError(falla.mensaje)
    } finally {
      setPreparando(false)
    }
  }

  async function activar(codigoIngresado) {
    const respuesta = await activarMfa({ codigo: codigoIngresado })
    setConfiguracion(null)
    setCodigos(respuesta.codigos)
    setExito(MFA_ACTIVADO)
  }

  function terminarActivacion() {
    setCodigos(null)
    setExito(null)
    recargar()
  }

  function abrirModal() {
    setContrasena('')
    setCodigo('')
    setErrorModal(null)
    setErroresModal({})
    setExito(null)
    setModalAbierto(true)
  }

  function cerrarModal() {
    if (!desactivando) setModalAbierto(false)
  }

  async function confirmarDesactivacion() {
    setDesactivando(true)
    setErrorModal(null)
    setErroresModal({})
    try {
      await desactivarMfa({ contrasena, codigo })
      setModalAbierto(false)
      setExito(MFA_DESACTIVADO)
      recargar()
    } catch (falla) {
      setErrorModal(falla.mensaje)
      setErroresModal(erroresPorCampo(falla.errores))
      setContrasena('')
      setCodigo('')
    } finally {
      setDesactivando(false)
    }
  }

  function contenido() {
    if (codigos) {
      return <CodigosRespaldo codigos={codigos} textoBoton={BOTON_LISTO} alContinuar={terminarActivacion} />
    }
    if (configuracion) {
      return (
        <ConfiguracionMfa
          secreto={configuracion.secreto}
          uri={configuracion.uri}
          textoBoton={BOTON_ACTIVAR}
          alEnviar={activar}
        />
      )
    }
    if (cargando || !estado) {
      return cargando ? <p className={estilos.secundario}>{CARGANDO_SEGURIDAD}</p> : null
    }
    if (!estado.activo) {
      return (
        <>
          <p>{MFA_INACTIVO}</p>
          <Boton cargando={preparando} onClick={empezarConfiguracion}>
            {BOTON_ACTIVAR_MFA}
          </Boton>
        </>
      )
    }
    return (
      <>
        <Alerta tipo="exito">{MFA_ACTIVO}</Alerta>
        <p className={estilos.secundario}>{codigosRestantes(estado.codigosRespaldoRestantes)}</p>
        {estado.desactivable ? (
          <Boton variante="peligro" onClick={abrirModal}>
            {BOTON_DESACTIVAR}
          </Boton>
        ) : (
          <p className={estilos.secundario}>{MFA_OBLIGATORIO_ADMIN}</p>
        )}
      </>
    )
  }

  return (
    <section className={estilos.pantalla}>
      <h2>{TITULO_SEGURIDAD}</h2>
      <h3 className={estilos.subtitulo}>{TITULO_VERIFICACION}</h3>
      {exito && <Alerta tipo="exito">{exito}</Alerta>}
      {error && <Alerta tipo="error">{error}</Alerta>}
      {contenido()}

      <Modal
        abierto={modalAbierto}
        titulo={TITULO_DESACTIVAR}
        textoConfirmar={BOTON_DESACTIVAR}
        variante="peligro-confirmar"
        cargando={desactivando}
        onConfirmar={confirmarDesactivacion}
        onCerrar={cerrarModal}
      >
        <p>{EXPLICACION_DESACTIVAR}</p>
        {errorModal && <Alerta tipo="error">{errorModal}</Alerta>}
        <Campo
          etiqueta={ETIQUETA_CONTRASENA}
          nombre="contrasena"
          tipo="password"
          valor={contrasena}
          onChange={setContrasena}
          error={erroresModal.contrasena}
        />
        <Campo
          etiqueta={ETIQUETA_CODIGO}
          nombre="codigo"
          valor={codigo}
          onChange={setCodigo}
          error={erroresModal.codigo}
        />
      </Modal>
    </section>
  )
}

export default SeguridadCuenta
