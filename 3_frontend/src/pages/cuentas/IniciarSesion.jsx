import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { NO_AUTENTICADO } from '../../api/cliente.js'
import { configurarMfaEnInicio, iniciarSesion, verificarMfa } from '../../api/cuentas.js'
import { guardarToken } from '../../api/tokenSesion.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import { retornoValido } from '../../sesion/retorno.js'
import useSesion from '../../sesion/useSesion.js'
import CodigosRespaldo from './CodigosRespaldo.jsx'
import ConfiguracionMfa from './ConfiguracionMfa.jsx'
import PasoCodigoMfa from './PasoCodigoMfa.jsx'
import { erroresPorCampo } from './erroresCampo.js'
import {
  AVISO_MFA_ADMIN,
  BOTON_ACTIVAR_Y_ENTRAR,
  BOTON_CODIGOS_GUARDADOS,
  BOTON_VOLVER,
  PREPARANDO_CLAVE,
  TITULO_VERIFICACION,
} from './textosCuentas.js'
import estilos from './IniciarSesion.module.css'

// Pasos (HU-17): credenciales → codigo | configuracion → codigos. El desafío vive solo en este estado de React, nunca
// en sessionStorage: recargar la página vuelve a pedir la contraseña.
function IniciarSesion() {
  const { sesion, entrar } = useSesion()
  const navegar = useNavigate()
  const aviso = useLocation().state
  // Las dos salidas usan el mismo destino: al entrar, sesion cambia y el Navigate de abajo puede ganarle a navegar.
  const destino = retornoValido(aviso?.retorno) ?? '/panel'
  const [paso, setPaso] = useState('credenciales')
  const [desafio, setDesafio] = useState(null)
  const [configuracion, setConfiguracion] = useState(null)
  const [nuevaSesion, setNuevaSesion] = useState(null)
  const [correo, setCorreo] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [errores, setErrores] = useState({})

  if (sesion) return <Navigate to={destino} replace />

  function entrarCon(respuesta) {
    guardarToken(respuesta.token)
    entrar({ rol: respuesta.rol, aviso: respuesta.aviso })
    navegar(destino, { replace: true })
  }

  function volverACredenciales(mensaje) {
    setPaso('credenciales')
    setDesafio(null)
    setConfiguracion(null)
    setErrorGeneral(mensaje ?? null)
    setErrores({})
    setEnviando(false)
  }

  // 401 en el segundo paso = verificación expirada o agotada: se vuelve a pedir la contraseña con el mensaje.
  async function segundoPaso(llamada) {
    try {
      return await llamada()
    } catch (falla) {
      if (falla.estado === NO_AUTENTICADO) {
        volverACredenciales(falla.mensaje)
        return null
      }
      throw falla
    }
  }

  async function verificarCodigo(codigo) {
    const respuesta = await segundoPaso(() => verificarMfa({ desafio, codigo }))
    if (respuesta) entrarCon(respuesta)
  }

  // Al activar MFA el admin aún no "entra": antes ve sus códigos, o el Navigate de arriba se saltaría la lista.
  async function activarYEntrar(codigo) {
    const respuesta = await segundoPaso(() => verificarMfa({ desafio, codigo }))
    if (respuesta) {
      guardarToken(respuesta.token)
      setNuevaSesion(respuesta)
      setPaso('codigos')
    }
  }

  async function prepararConfiguracion(nuevoDesafio) {
    try {
      setConfiguracion(await configurarMfaEnInicio({ desafio: nuevoDesafio }))
    } catch (falla) {
      volverACredenciales(falla.mensaje)
    }
  }

  async function enviar(evento) {
    evento.preventDefault()
    setEnviando(true)
    setErrorGeneral(null)
    setErrores({})
    try {
      const respuesta = await iniciarSesion({ correo, contrasena })
      setContrasena('')
      if (respuesta.token) {
        entrarCon(respuesta)
        return
      }
      setDesafio(respuesta.desafio)
      setEnviando(false)
      if (respuesta.paso === 'CONFIGURACION') {
        setPaso('configuracion')
        await prepararConfiguracion(respuesta.desafio)
      } else {
        setPaso('codigo')
      }
    } catch (error) {
      setErrorGeneral(error.mensaje)
      setErrores(erroresPorCampo(error.errores))
      setContrasena('')
      setEnviando(false)
    }
  }

  if (paso === 'codigo') {
    return (
      <section className={estilos.pantalla}>
        <PasoCodigoMfa alEnviar={verificarCodigo} alVolver={() => volverACredenciales(null)} />
      </section>
    )
  }

  if (paso === 'configuracion') {
    return (
      <section className={estilos.pantalla}>
        <h2>{TITULO_VERIFICACION}</h2>
        <Alerta tipo="atencion">{AVISO_MFA_ADMIN}</Alerta>
        {configuracion ? (
          <ConfiguracionMfa
            secreto={configuracion.secreto}
            uri={configuracion.uri}
            textoBoton={BOTON_ACTIVAR_Y_ENTRAR}
            alEnviar={activarYEntrar}
          />
        ) : (
          <p className={estilos.secundario}>{PREPARANDO_CLAVE}</p>
        )}
        <p className={estilos.otraOpcion}>
          <Boton variante="secundario" onClick={() => volverACredenciales(null)}>
            {BOTON_VOLVER}
          </Boton>
        </p>
      </section>
    )
  }

  if (paso === 'codigos') {
    return (
      <section className={estilos.pantalla}>
        <h2>{TITULO_VERIFICACION}</h2>
        <CodigosRespaldo
          codigos={nuevaSesion.codigosRespaldo ?? []}
          textoBoton={BOTON_CODIGOS_GUARDADOS}
          alContinuar={() => {
            entrar({ rol: nuevaSesion.rol, aviso: nuevaSesion.aviso })
            navegar(destino, { replace: true })
          }}
        />
      </section>
    )
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
