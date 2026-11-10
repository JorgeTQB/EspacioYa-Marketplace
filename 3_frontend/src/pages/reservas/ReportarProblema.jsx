import { useState } from 'react'
import { abrirReclamo } from '../../api/administracion.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import Modal from '../../components/ui/Modal.jsx'
import {
  AYUDA_DESCRIPCION,
  BOTON_ENVIAR_RECLAMO,
  BOTON_REPORTAR,
  ETIQUETA_DESCRIPCION,
  ETIQUETA_MOTIVO,
  OPCIONES_MOTIVO,
  TITULO_REPORTAR,
} from '../administracion/textosAdministracion.js'
import estilos from './ReportarProblema.module.css'

const CAMPO_MOTIVO = 'motivo'
const CAMPO_DESCRIPCION = 'descripcion'

function mensajeDe(errores, campo) {
  return errores.find((error) => error.campo === campo)?.mensaje ?? null
}

// HU-26 CA1: "Reportar un problema" de una reserva que la API dijo que se puede reclamar. La pantalla no valida reglas:
// un motivo sin elegir viaja como null y responde el servidor. Los errores de motivo y descripción se quedan en el modal;
// cualquier otro rechazo (plazo vencido, ya reclamada, 404) lo cierra y se ve bajo el botón.
function ReportarProblema({ reserva, onReportado }) {
  const [abierto, setAbierto] = useState(false)
  const [motivo, setMotivo] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [erroresCampo, setErroresCampo] = useState([])
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  function abrir() {
    setError(null)
    setErroresCampo([])
    setAbierto(true)
  }

  async function enviar() {
    setEnviando(true)
    setErroresCampo([])
    try {
      const reclamo = await abrirReclamo({
        reservaId: reserva.id,
        motivo: motivo === '' ? null : motivo,
        descripcion,
      })
      setAbierto(false)
      onReportado(reclamo)
    } catch (falla) {
      const errores = falla.errores ?? []
      const deCampo = errores.filter((e) => e.campo === CAMPO_MOTIVO || e.campo === CAMPO_DESCRIPCION)
      if (deCampo.length > 0) {
        setErroresCampo(deCampo)
        return
      }
      setAbierto(false)
      setError(falla.mensaje)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className={estilos.reportar}>
      <Boton variante="secundario" onClick={abrir}>
        {BOTON_REPORTAR}
      </Boton>
      {error && <Alerta tipo="error">{error}</Alerta>}
      <Modal
        abierto={abierto}
        titulo={TITULO_REPORTAR}
        textoConfirmar={BOTON_ENVIAR_RECLAMO}
        variante="primario"
        cargando={enviando}
        onConfirmar={enviar}
        onCerrar={() => setAbierto(false)}
      >
        <div className={estilos.campos}>
          <Campo
            etiqueta={ETIQUETA_MOTIVO}
            nombre={CAMPO_MOTIVO}
            tipo="select"
            valor={motivo}
            onChange={setMotivo}
            opciones={OPCIONES_MOTIVO}
            error={mensajeDe(erroresCampo, CAMPO_MOTIVO)}
          />
          <Campo
            etiqueta={ETIQUETA_DESCRIPCION}
            nombre={CAMPO_DESCRIPCION}
            tipo="textarea"
            valor={descripcion}
            onChange={setDescripcion}
            ayuda={AYUDA_DESCRIPCION}
            error={mensajeDe(erroresCampo, CAMPO_DESCRIPCION)}
          />
        </div>
      </Modal>
    </div>
  )
}

export default ReportarProblema
