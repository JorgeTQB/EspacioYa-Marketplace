import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { obtenerVersionPrivacidad, registrarUsuario } from '../../api/cuentas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import estilos from './Registro.module.css'
import { REQUISITOS_CONTRASENA } from './textosCuentas.js'

function erroresPorCampo(errores) {
  return Object.fromEntries(errores.map(({ campo, mensaje }) => [campo, mensaje]))
}

function Registro() {
  const [version, setVersion] = useState(null)
  const [errorVersion, setErrorVersion] = useState(null)
  const [correo, setCorreo] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [acepta, setAcepta] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [errores, setErrores] = useState({})
  const [exito, setExito] = useState(null)

  useEffect(() => {
    let activo = true
    obtenerVersionPrivacidad()
      .then((datos) => {
        if (activo) setVersion(datos.version)
      })
      .catch((error) => {
        if (activo) setErrorVersion(error.mensaje)
      })
    return () => {
      activo = false
    }
  }, [])

  async function enviar(evento) {
    evento.preventDefault()
    setEnviando(true)
    setErrorGeneral(null)
    setErrores({})
    try {
      const respuesta = await registrarUsuario({
        correo,
        contrasena,
        aceptaPrivacidad: acepta,
        versionPolitica: version,
      })
      setExito(respuesta.mensaje)
    } catch (error) {
      setErrorGeneral(error.mensaje)
      setErrores(erroresPorCampo(error.errores))
    } finally {
      setEnviando(false)
    }
  }

  if (exito) {
    return (
      <section className={estilos.pantalla}>
        <h2>Crea tu cuenta</h2>
        <Alerta tipo="exito">{exito}</Alerta>
      </section>
    )
  }

  const etiquetaAceptacion = (
    <>
      Acepto los términos y la{' '}
      <Link to="/privacidad" target="_blank" rel="noreferrer">
        política de privacidad
      </Link>
    </>
  )

  return (
    <section className={estilos.pantalla}>
      <h2>Crea tu cuenta</h2>
      {errorVersion && <Alerta tipo="error">{errorVersion}</Alerta>}
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
          ayuda={REQUISITOS_CONTRASENA}
          error={errores.contrasena}
        />
        <Campo
          etiqueta={etiquetaAceptacion}
          nombre="aceptaPrivacidad"
          tipo="checkbox"
          valor={acepta}
          onChange={setAcepta}
          error={errores.aceptaPrivacidad ?? errores.versionPolitica}
        />
        <Boton tipo="submit" cargando={enviando} deshabilitado={!acepta || !version}>
          Crear cuenta
        </Boton>
      </form>
      <p className={estilos.otraCuenta}>
        <Link to="/registro-arrendador">¿Tienes un espacio? Regístrate como arrendador</Link>
      </p>
    </section>
  )
}

export default Registro
