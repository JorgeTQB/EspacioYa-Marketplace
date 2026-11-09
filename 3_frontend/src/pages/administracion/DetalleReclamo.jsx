import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { enviarMensajeReclamo, obtenerReclamo } from '../../api/administracion.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import { formatoHora, formatoPrecio } from '../espacios/textosEspacios.js'
import { formatoFecha } from '../reservas/textosReservas.js'
import HiloReclamo from './HiloReclamo.jsx'
import {
  BOTON_ENVIAR_MENSAJE,
  CARGANDO,
  ETIQUETA_TU_MENSAJE,
  MOTIVOS_RECLAMO,
  RECLAMO_YA_RESUELTO,
  SIN_CUPO_MENSAJES,
  TITULO_RECLAMO,
  fechaHora,
  mensajesRestantes,
  resultadoReclamo,
} from './textosAdministracion.js'
import estilos from './DetalleReclamo.module.css'

const CAMPO_TEXTO = 'texto'

// HU-26 CA1, CA2, CA3: el reclamo visto por una de sus partes, con su hilo. Si se puede escribir, cuántos mensajes
// quedan y el monto del reembolso (solo al usuario) los dice la API.
function DetalleReclamo() {
  const { id } = useParams()
  const [reclamo, setReclamo] = useState(null)
  const [error, setError] = useState(null)
  const [texto, setTexto] = useState('')
  const [errorTexto, setErrorTexto] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [recarga, setRecarga] = useState(0)

  const releer = useCallback(() => setRecarga((n) => n + 1), [])

  useEffect(() => {
    let activo = true
    obtenerReclamo(id)
      .then((datos) => {
        if (activo) setReclamo(datos)
      })
      .catch((falla) => {
        if (activo) setError(falla.mensaje)
      })
    return () => {
      activo = false
    }
  }, [id, recarga])

  async function enviar() {
    setEnviando(true)
    setErrorTexto(null)
    setError(null)
    try {
      await enviarMensajeReclamo(id, { texto })
      setTexto('')
      releer()
    } catch (falla) {
      const deCampo = (falla.errores ?? []).find((e) => e.campo === CAMPO_TEXTO)
      if (deCampo) {
        setErrorTexto(deCampo.mensaje)
      } else {
        setError(falla.mensaje)
        releer()
      }
    } finally {
      setEnviando(false)
    }
  }

  if (!reclamo) {
    return (
      <section className={estilos.pantalla}>
        <h2>{TITULO_RECLAMO}</h2>
        {error ? <Alerta tipo="error">{error}</Alerta> : <p className={estilos.secundario}>{CARGANDO}</p>}
      </section>
    )
  }

  return (
    <section className={estilos.pantalla}>
      <h2>{TITULO_RECLAMO}</h2>
      {error && <Alerta tipo="error">{error}</Alerta>}
      <Tarjeta titulo={reclamo.espacioNombre}>
        <div>
          <EtiquetaEstado estado={reclamo.estado} />
        </div>
        <p className={estilos.dato}>
          {formatoFecha(reclamo.fecha)} · {`${formatoHora(reclamo.horaInicio)}–${formatoHora(reclamo.horaFin)}`}
        </p>
        <p className={estilos.dato}>Motivo: {MOTIVOS_RECLAMO[reclamo.motivo] ?? reclamo.motivo}</p>
        <p className={estilos.descripcion}>{reclamo.descripcion}</p>
        <p className={estilos.dato}>Abierto el {fechaHora(reclamo.abiertoEn)}</p>
        {reclamo.aFavorDe && (
          <>
            <p className={estilos.resultado}>{resultadoReclamo(reclamo.aFavorDe)}</p>
            <p className={estilos.descripcion}>{reclamo.resolucion}</p>
            {reclamo.montoReembolso != null && (
              <p>
                Reembolso solicitado: <strong className={estilos.monto}>{formatoPrecio(reclamo.montoReembolso)}</strong>
              </p>
            )}
          </>
        )}
      </Tarjeta>
      <HiloReclamo mensajes={reclamo.mensajes} papelPropio={reclamo.papel} />
      {reclamo.puedeEscribir ? (
        <div className={estilos.formulario}>
          <Campo
            etiqueta={ETIQUETA_TU_MENSAJE}
            nombre={CAMPO_TEXTO}
            tipo="textarea"
            valor={texto}
            onChange={setTexto}
            ayuda={mensajesRestantes(reclamo.mensajesRestantes)}
            error={errorTexto}
          />
          <div>
            <Boton variante="primario" cargando={enviando} onClick={enviar}>
              {BOTON_ENVIAR_MENSAJE}
            </Boton>
          </div>
        </div>
      ) : (
        <p className={estilos.secundario}>{reclamo.estado === 'ABIERTO' ? SIN_CUPO_MENSAJES : RECLAMO_YA_RESUELTO}</p>
      )}
    </section>
  )
}

export default DetalleReclamo
