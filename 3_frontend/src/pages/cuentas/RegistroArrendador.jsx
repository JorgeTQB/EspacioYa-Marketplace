import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { obtenerVersionPrivacidad, registrarArrendador } from '../../api/cuentas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import estilos from './RegistroArrendador.module.css'
import { REQUISITOS_CONTRASENA } from './textosCuentas.js'

const TIPOS_DOCUMENTO = [
  { valor: 'DNI', etiqueta: 'DNI' },
  { valor: 'RUC', etiqueta: 'RUC' },
]

const DATOS_INICIALES = {
  correo: '',
  contrasena: '',
  nombre: '',
  telefono: '',
  tipoDocumento: '',
  numeroDocumento: '',
  razonSocial: '',
  direccionFiscal: '',
}

function erroresPorCampo(errores) {
  return Object.fromEntries(errores.map(({ campo, mensaje }) => [campo, mensaje]))
}

function RegistroArrendador() {
  const [version, setVersion] = useState(null)
  const [errorVersion, setErrorVersion] = useState(null)
  const [datos, setDatos] = useState(DATOS_INICIALES)
  const [acepta, setAcepta] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [errores, setErrores] = useState({})
  const [confirmacion, setConfirmacion] = useState(null)

  useEffect(() => {
    let activo = true
    obtenerVersionPrivacidad()
      .then((respuesta) => {
        if (activo) setVersion(respuesta.version)
      })
      .catch((error) => {
        if (activo) setErrorVersion(error.mensaje)
      })
    return () => {
      activo = false
    }
  }, [])

  function cambiar(campo) {
    return (valor) => setDatos((anteriores) => ({ ...anteriores, [campo]: valor }))
  }

  async function enviar(evento) {
    evento.preventDefault()
    setEnviando(true)
    setErrorGeneral(null)
    setErrores({})
    try {
      const respuesta = await registrarArrendador({
        ...datos,
        aceptaPrivacidad: acepta,
        versionPolitica: version,
      })
      setConfirmacion(respuesta)
    } catch (error) {
      setErrorGeneral(error.mensaje)
      setErrores(erroresPorCampo(error.errores))
    } finally {
      setEnviando(false)
    }
  }

  if (confirmacion) {
    return (
      <section className={estilos.pantalla}>
        <h2>Regístrate como arrendador</h2>
        <Alerta tipo="exito">{confirmacion.mensaje}</Alerta>
        {confirmacion.aviso && <Alerta tipo="atencion">{confirmacion.aviso}</Alerta>}
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
      <h2>Regístrate como arrendador</h2>
      {errorVersion && <Alerta tipo="error">{errorVersion}</Alerta>}
      {errorGeneral && <Alerta tipo="error">{errorGeneral}</Alerta>}
      <form className={estilos.formulario} onSubmit={enviar} noValidate>
        <fieldset className={estilos.seccion}>
          <legend>Acceso</legend>
          <Campo
            etiqueta="Correo"
            nombre="correo"
            tipo="email"
            valor={datos.correo}
            onChange={cambiar('correo')}
            error={errores.correo}
          />
          <Campo
            etiqueta="Contraseña"
            nombre="contrasena"
            tipo="password"
            valor={datos.contrasena}
            onChange={cambiar('contrasena')}
            ayuda={REQUISITOS_CONTRASENA}
            error={errores.contrasena}
          />
        </fieldset>
        <fieldset className={estilos.seccion}>
          <legend>Contacto</legend>
          <Campo
            etiqueta="Nombre completo"
            nombre="nombre"
            valor={datos.nombre}
            onChange={cambiar('nombre')}
            error={errores.nombre}
          />
          <Campo
            etiqueta="Celular"
            nombre="telefono"
            tipo="tel"
            valor={datos.telefono}
            onChange={cambiar('telefono')}
            ayuda="9 dígitos, empieza con 9"
            error={errores.telefono}
          />
        </fieldset>
        <fieldset className={estilos.seccion}>
          <legend>Facturación</legend>
          <Campo
            etiqueta="Tipo de documento"
            nombre="tipoDocumento"
            tipo="select"
            opciones={TIPOS_DOCUMENTO}
            valor={datos.tipoDocumento}
            onChange={cambiar('tipoDocumento')}
            error={errores.tipoDocumento}
          />
          <Campo
            etiqueta="Número de documento"
            nombre="numeroDocumento"
            valor={datos.numeroDocumento}
            onChange={cambiar('numeroDocumento')}
            ayuda="DNI: 8 dígitos · RUC: 11 dígitos"
            error={errores.numeroDocumento}
          />
          <Campo
            etiqueta="Razón social o nombre"
            nombre="razonSocial"
            valor={datos.razonSocial}
            onChange={cambiar('razonSocial')}
            error={errores.razonSocial}
          />
          <Campo
            etiqueta="Dirección fiscal"
            nombre="direccionFiscal"
            valor={datos.direccionFiscal}
            onChange={cambiar('direccionFiscal')}
            error={errores.direccionFiscal}
          />
        </fieldset>
        <Campo
          etiqueta={etiquetaAceptacion}
          nombre="aceptaPrivacidad"
          tipo="checkbox"
          valor={acepta}
          onChange={setAcepta}
          error={errores.aceptaPrivacidad ?? errores.versionPolitica}
        />
        <Boton tipo="submit" cargando={enviando} deshabilitado={!acepta || !version}>
          Crear cuenta de arrendador
        </Boton>
      </form>
    </section>
  )
}

export default RegistroArrendador
