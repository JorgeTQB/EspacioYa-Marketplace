import { useEffect, useState } from 'react'
import { actualizarPreferenciasCorreo, obtenerPreferenciasCorreo } from '../../api/cuentas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import { erroresPorCampo } from './erroresCampo.js'
import {
  AYUDA_RECORDATORIOS,
  BOTON_GUARDAR_RECORDATORIOS,
  CARGANDO_RECORDATORIOS,
  ETIQUETA_RECORDATORIOS,
  RECORDATORIOS_GUARDADOS,
  TITULO_RECORDATORIOS,
} from './textosCuentas.js'
import estilos from './Recordatorios.module.css'

// HU-25 CA2: activar o desactivar el recordatorio 24 h antes de cada reserva. La casilla muestra lo que devuelve la
// API; quién recibe el correo lo decide el servidor con la cuenta de la sesión.
function Recordatorios() {
  const [activos, setActivos] = useState(null)
  const [errorCarga, setErrorCarga] = useState(null)
  const [errorCampo, setErrorCampo] = useState(null)
  const [error, setError] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    let vigente = true
    obtenerPreferenciasCorreo()
      .then((respuesta) => {
        if (vigente) setActivos(respuesta.recordatoriosActivos)
      })
      .catch((falla) => {
        if (vigente) setErrorCarga(falla.mensaje)
      })
    return () => {
      vigente = false
    }
  }, [])

  async function guardar(evento) {
    evento.preventDefault()
    setGuardando(true)
    setErrorCampo(null)
    setError(null)
    setAviso(null)
    try {
      const respuesta = await actualizarPreferenciasCorreo({ recordatoriosActivos: activos })
      setActivos(respuesta.recordatoriosActivos)
      setAviso(RECORDATORIOS_GUARDADOS)
    } catch (falla) {
      const porCampo = erroresPorCampo(falla.errores)
      if (porCampo.recordatoriosActivos) {
        setErrorCampo(porCampo.recordatoriosActivos)
      } else {
        setError(falla.mensaje)
      }
    } finally {
      setGuardando(false)
    }
  }

  function contenido() {
    if (errorCarga) return <Alerta tipo="error">{errorCarga}</Alerta>
    if (activos === null) return <p className={estilos.secundario}>{CARGANDO_RECORDATORIOS}</p>
    return (
      <form className={estilos.formulario} onSubmit={guardar} noValidate>
        {aviso && <Alerta tipo="exito">{aviso}</Alerta>}
        {error && <Alerta tipo="error">{error}</Alerta>}
        <Campo
          etiqueta={ETIQUETA_RECORDATORIOS}
          nombre="recordatoriosActivos"
          tipo="checkbox"
          valor={activos}
          onChange={setActivos}
          error={errorCampo}
          ayuda={AYUDA_RECORDATORIOS}
        />
        <Boton tipo="submit" variante="secundario" cargando={guardando}>
          {BOTON_GUARDAR_RECORDATORIOS}
        </Boton>
      </form>
    )
  }

  return <Tarjeta titulo={TITULO_RECORDATORIOS}>{contenido()}</Tarjeta>
}

export default Recordatorios
