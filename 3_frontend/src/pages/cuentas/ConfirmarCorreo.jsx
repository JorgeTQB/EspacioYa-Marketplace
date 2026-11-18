import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { confirmarCambioCorreo } from '../../api/cuentas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import useSesion from '../../sesion/useSesion.js'
import {
  BOTON_CONFIRMAR_CORREO,
  ENLACE_CORREO_INVALIDO,
  ENLACE_INICIAR_SESION,
  ENLACE_IR_A_PERFIL,
  INSTRUCCION_CONFIRMAR_CORREO,
  TITULO_CONFIRMAR_CORREO,
} from './textosCuentas.js'
import estilos from './ConfirmarCorreo.module.css'

// El token llega en el fragmento (#token=…), que el navegador no envía a ningún servidor (como HU-04).
function tokenDelFragmento(fragmento) {
  return new URLSearchParams(fragmento.replace(/^#/, '')).get('token') ?? ''
}

// HU-20 CA2: confirma el correo nuevo. Es pública (D21) y exige pulsar el botón (D19): abrir el enlace no lo usa, así
// un previsualizador de correo no lo consume.
function ConfirmarCorreo() {
  const { sesion } = useSesion()
  const navegar = useNavigate()
  const { pathname, hash } = useLocation()
  const [token] = useState(() => tokenDelFragmento(hash))
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState(null)

  // Quita el token de la barra de direcciones y del historial en cuanto se leyó.
  useEffect(() => {
    if (hash) navegar(pathname, { replace: true })
  }, [hash, pathname, navegar])

  async function confirmar() {
    setEnviando(true)
    try {
      const respuesta = await confirmarCambioCorreo(token)
      setResultado({ tipo: 'exito', texto: respuesta.mensaje })
    } catch (falla) {
      setResultado({ tipo: 'error', texto: falla.mensaje })
    } finally {
      setEnviando(false)
    }
  }

  const enlaceSiguiente = sesion ? (
    <Link to="/panel/perfil">{ENLACE_IR_A_PERFIL}</Link>
  ) : (
    <Link to="/iniciar-sesion">{ENLACE_INICIAR_SESION}</Link>
  )

  function contenido() {
    if (!token) {
      return (
        <>
          <Alerta tipo="error">{ENLACE_CORREO_INVALIDO}</Alerta>
          <p className={estilos.otraOpcion}>
            <Link to="/panel/perfil">{ENLACE_IR_A_PERFIL}</Link>
          </p>
        </>
      )
    }
    if (resultado?.tipo === 'exito') {
      return (
        <>
          <Alerta tipo="exito">{resultado.texto}</Alerta>
          <p className={estilos.otraOpcion}>{enlaceSiguiente}</p>
        </>
      )
    }
    if (resultado?.tipo === 'error') {
      return (
        <>
          <Alerta tipo="error">{resultado.texto}</Alerta>
          <p className={estilos.otraOpcion}>
            <Link to="/panel/perfil">{ENLACE_IR_A_PERFIL}</Link>
          </p>
        </>
      )
    }
    return (
      <>
        <p>{INSTRUCCION_CONFIRMAR_CORREO}</p>
        <Boton cargando={enviando} onClick={confirmar}>
          {BOTON_CONFIRMAR_CORREO}
        </Boton>
      </>
    )
  }

  return (
    <section className={estilos.pantalla}>
      <h2>{TITULO_CONFIRMAR_CORREO}</h2>
      {contenido()}
    </section>
  )
}

export default ConfirmarCorreo
