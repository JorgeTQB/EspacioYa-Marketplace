import { useState } from 'react'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import { erroresPorCampo } from './erroresCampo.js'
import { AYUDA_CODIGO, BOTON_VERIFICAR, BOTON_VOLVER, ETIQUETA_CODIGO, TITULO_VERIFICACION } from './textosCuentas.js'
import estilos from './PasoCodigoMfa.module.css'

// Segundo paso del inicio de sesión (HU-17 CA1, CA2). alEnviar hace la llamada; si falla con un error que la pantalla
// padre no atiende (p. ej. 400 o 429), se muestra aquí en el mismo paso.
function PasoCodigoMfa({ alEnviar, alVolver }) {
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
    <>
      <h2>{TITULO_VERIFICACION}</h2>
      {error && <Alerta tipo="error">{error}</Alerta>}
      <form className={estilos.formulario} onSubmit={enviar} noValidate>
        <Campo
          etiqueta={ETIQUETA_CODIGO}
          nombre="codigo"
          valor={codigo}
          onChange={setCodigo}
          error={errores.codigo}
          ayuda={AYUDA_CODIGO}
        />
        <div className={estilos.acciones}>
          <Boton tipo="submit" cargando={enviando}>
            {BOTON_VERIFICAR}
          </Boton>
          <Boton variante="secundario" deshabilitado={enviando} onClick={alVolver}>
            {BOTON_VOLVER}
          </Boton>
        </div>
      </form>
    </>
  )
}

export default PasoCodigoMfa
