import { useState } from 'react'
import { Link } from 'react-router-dom'
import { solicitarRecuperacion } from '../../api/cuentas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import estilos from './RecuperarContrasena.module.css'

function erroresPorCampo(errores) {
  return Object.fromEntries(errores.map(({ campo, mensaje }) => [campo, mensaje]))
}

function RecuperarContrasena() {
  const [correo, setCorreo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [errores, setErrores] = useState({})
  const [exito, setExito] = useState(null)

  async function enviar(evento) {
    evento.preventDefault()
    setEnviando(true)
    setErrorGeneral(null)
    setErrores({})
    try {
      const respuesta = await solicitarRecuperacion({ correo })
      setExito(respuesta.mensaje)
    } catch (error) {
      setErrorGeneral(error.mensaje)
      setErrores(erroresPorCampo(error.errores))
    } finally {
      setEnviando(false)
    }
  }

  const volver = (
    <p className={estilos.otraOpcion}>
      <Link to="/iniciar-sesion">Volver a iniciar sesión</Link>
    </p>
  )

  // El mensaje es el mismo exista o no la cuenta: se muestra tal cual lo envía la API (CA1).
  if (exito) {
    return (
      <section className={estilos.pantalla}>
        <h2>Recupera tu contraseña</h2>
        <Alerta tipo="exito">{exito}</Alerta>
        {volver}
      </section>
    )
  }

  return (
    <section className={estilos.pantalla}>
      <h2>Recupera tu contraseña</h2>
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
        <Boton tipo="submit" cargando={enviando}>
          Enviar enlace
        </Boton>
      </form>
      {volver}
    </section>
  )
}

export default RecuperarContrasena
