import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { iniciarSesion } from '../../api/cuentas.js'
import { guardarToken } from '../../api/tokenSesion.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import useSesion from '../../sesion/useSesion.js'
import estilos from './IniciarSesion.module.css'

function erroresPorCampo(errores) {
  return Object.fromEntries(errores.map(({ campo, mensaje }) => [campo, mensaje]))
}

function IniciarSesion() {
  const { sesion, entrar } = useSesion()
  const navegar = useNavigate()
  const aviso = useLocation().state
  const [correo, setCorreo] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [errores, setErrores] = useState({})

  if (sesion) return <Navigate to="/panel" replace />

  async function enviar(evento) {
    evento.preventDefault()
    setEnviando(true)
    setErrorGeneral(null)
    setErrores({})
    try {
      const respuesta = await iniciarSesion({ correo, contrasena })
      guardarToken(respuesta.token)
      entrar({ rol: respuesta.rol, aviso: respuesta.aviso })
      navegar('/panel', { replace: true })
    } catch (error) {
      setErrorGeneral(error.mensaje)
      setErrores(erroresPorCampo(error.errores))
      setContrasena('')
      setEnviando(false)
    }
  }

  return (
    <section className={estilos.pantalla}>
      <h2>Inicia sesión</h2>
      {aviso?.mensaje && !errorGeneral && <Alerta tipo={aviso.tipo ?? 'info'}>{aviso.mensaje}</Alerta>}
      {errorGeneral && <Alerta tipo="error">{errorGeneral}</Alerta>}
      <form className={estilos.formulario} onSubmit={enviar} noValidate>
        <Campo
          etiqueta="Correo"
          nombre="correo"
          tipo="email"
          valor={correo}
          onChange={setCorreo}
          error={errores.correo}
        />
        <Campo
          etiqueta="Contraseña"
          nombre="contrasena"
          tipo="password"
          valor={contrasena}
          onChange={setContrasena}
          error={errores.contrasena}
        />
        <p className={estilos.olvido}>
          <Link to="/recuperar-contrasena">¿Olvidaste tu contraseña?</Link>
        </p>
        <Boton tipo="submit" cargando={enviando}>
          Iniciar sesión
        </Boton>
      </form>
      <p className={estilos.otraOpcion}>
        <Link to="/registro">¿No tienes cuenta? Regístrate</Link>
      </p>
    </section>
  )
}

export default IniciarSesion
