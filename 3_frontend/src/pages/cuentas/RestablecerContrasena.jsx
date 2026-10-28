import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { restablecerContrasena, validarEnlaceRecuperacion } from '../../api/cuentas.js'
import { borrarToken } from '../../api/tokenSesion.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import useSesion from '../../sesion/useSesion.js'
import { CONTRASENAS_NO_COINCIDEN, REQUISITOS_CONTRASENA } from './textosCuentas.js'
import estilos from './RestablecerContrasena.module.css'

const SOLICITUD_RECHAZADA = 400

function erroresPorCampo(errores) {
  return Object.fromEntries(errores.map(({ campo, mensaje }) => [campo, mensaje]))
}

// El token llega en el fragmento (#token=…), que el navegador no envía a ningún servidor (HU-04, DT-2).
function tokenDelFragmento(fragmento) {
  return new URLSearchParams(fragmento.replace(/^#/, '')).get('token') ?? ''
}

function RestablecerContrasena() {
  const { salir } = useSesion()
  const navegar = useNavigate()
  const { pathname, hash } = useLocation()
  const [token] = useState(() => tokenDelFragmento(hash))
  const [estado, setEstado] = useState('validando')
  const [enlaceInvalido, setEnlaceInvalido] = useState(null)
  const [contrasena, setContrasena] = useState('')
  const [confirmacion, setConfirmacion] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [errores, setErrores] = useState({})

  // Quita el token de la barra de direcciones y del historial en cuanto se leyó.
  useEffect(() => {
    if (hash) navegar(pathname, { replace: true })
  }, [hash, pathname, navegar])

  // Sin token también se consulta: el backend lo rechaza con el mismo mensaje de enlace inválido.
  useEffect(() => {
    let activo = true
    validarEnlaceRecuperacion(token)
      .then(() => {
        if (activo) setEstado('formulario')
      })
      .catch((error) => {
        if (!activo) return
        setEnlaceInvalido(error.mensaje)
        setEstado('invalido')
      })
    return () => {
      activo = false
    }
  }, [token])

  async function enviar(evento) {
    evento.preventDefault()
    setErrorGeneral(null)
    setErrores({})
    if (contrasena !== confirmacion) {
      setErrores({ confirmacion: CONTRASENAS_NO_COINCIDEN })
      return
    }
    setEnviando(true)
    try {
      const respuesta = await restablecerContrasena({ token, contrasena })
      // Las sesiones se revocaron en el backend: la local tampoco vale (CA3, D4).
      borrarToken()
      salir()
      navegar('/iniciar-sesion', { replace: true, state: { mensaje: respuesta.mensaje, tipo: 'exito' } })
    } catch (error) {
      setEnviando(false)
      if (error.errores.length > 0) {
        setErrorGeneral(error.mensaje)
        setErrores(erroresPorCampo(error.errores))
      } else if (error.estado === SOLICITUD_RECHAZADA) {
        setEnlaceInvalido(error.mensaje)
        setEstado('invalido')
      } else {
        setErrorGeneral(error.mensaje)
      }
    }
  }

  if (estado === 'validando') {
    return (
      <section className={estilos.pantalla}>
        <h2>Restablece tu contraseña</h2>
      </section>
    )
  }

  if (estado === 'invalido') {
    return (
      <section className={estilos.pantalla}>
        <h2>Restablece tu contraseña</h2>
        <Alerta tipo="error">{enlaceInvalido}</Alerta>
        <p className={estilos.otraOpcion}>
          <Link to="/recuperar-contrasena">Solicitar un enlace nuevo</Link>
        </p>
      </section>
    )
  }

  return (
    <section className={estilos.pantalla}>
      <h2>Restablece tu contraseña</h2>
      {errorGeneral && <Alerta tipo="error">{errorGeneral}</Alerta>}
      <form className={estilos.formulario} onSubmit={enviar} noValidate>
        <Campo
          etiqueta="Nueva contraseña"
          nombre="contrasena"
          tipo="password"
          valor={contrasena}
          onChange={setContrasena}
          error={errores.contrasena}
          ayuda={REQUISITOS_CONTRASENA}
        />
        <Campo
          etiqueta="Repite la contraseña"
          nombre="confirmacion"
          tipo="password"
          valor={confirmacion}
          onChange={setConfirmacion}
          error={errores.confirmacion}
        />
        <Boton tipo="submit" cargando={enviando}>
          Cambiar contraseña
        </Boton>
      </form>
    </section>
  )
}

export default RestablecerContrasena
