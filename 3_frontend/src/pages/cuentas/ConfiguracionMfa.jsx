import { useState } from 'react'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import { erroresPorCampo } from './erroresCampo.js'
import {
  AYUDA_CODIGO_APP,
  ENLACE_ABRIR_APP,
  ETIQUETA_CODIGO,
  PASOS_CONFIGURACION,
  claveEnGrupos,
} from './textosCuentas.js'
import estilos from './ConfiguracionMfa.module.css'

// Clave y enlace otpauth:// que devuelve la API (HU-17 P7: sin QR). El enlace abre la app en el celular; en una
// computadora la clave se escribe a mano. alEnviar hace la llamada con el primer código.
function ConfiguracionMfa({ secreto, uri, textoBoton, alEnviar }) {
  const [codigo, setCodigo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const [errores, setErrores] = useState({})

  async function enviar(evento) {
    evento.preventDefault()
    setEnviando(true)
    setError(null)
    setErrores({})
    try {
      await alEnviar(codigo)
    } catch (falla) {
      setError(falla.mensaje)
      setErrores(erroresPorCampo(falla.errores))
      setCodigo('')
      setEnviando(false)
    }
  }

  return (
    <form className={estilos.formulario} onSubmit={enviar} noValidate>
      {error && <Alerta tipo="error">{error}</Alerta>}
      <ol className={estilos.pasos}>
        <li>{PASOS_CONFIGURACION[0]}</li>
        <li>
          {PASOS_CONFIGURACION[1]}
          <p className={estilos.clave}>{claveEnGrupos(secreto)}</p>
          <a href={uri}>{ENLACE_ABRIR_APP}</a>
        </li>
        <li>{PASOS_CONFIGURACION[2]}</li>
      </ol>
      <Campo
        etiqueta={ETIQUETA_CODIGO}
        nombre="codigo"
        valor={codigo}
        onChange={setCodigo}
        error={errores.codigo}
        ayuda={AYUDA_CODIGO_APP}
      />
      <Boton tipo="submit" cargando={enviando}>
        {textoBoton}
      </Boton>
    </form>
  )
}

export default ConfiguracionMfa
