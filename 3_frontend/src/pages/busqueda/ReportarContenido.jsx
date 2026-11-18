import { useState } from 'react'
import { reportarContenido } from '../../api/administracion.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import Modal from '../../components/ui/Modal.jsx'
import {
  AYUDA_REPORTE,
  BOTON_ENVIAR_REPORTE,
  BOTON_REPORTADO,
  BOTON_REPORTAR_PUBLICACION,
  BOTON_REPORTAR_RESENA,
  ETIQUETA_MOTIVO_REPORTE,
  OPCIONES_MOTIVO_REPORTE,
  REPORTE_ENVIADO,
  TITULO_REPORTAR_PUBLICACION,
  TITULO_REPORTAR_RESENA,
} from './textosFicha.js'
import estilos from './ReportarContenido.module.css'

const CAMPO_MOTIVO = 'motivo'
const TIPO_ESPACIO = 'ESPACIO'

// HU-27 CA1: reportar un espacio o una reseña de la ficha pública. La pantalla no valida reglas: un motivo sin elegir
// viaja como null y responde el servidor. El error de motivo se queda en el modal; cualquier otro rechazo (contenido
// propio, ya reportado, 404, 429) lo cierra y se ve bajo el botón.
function ReportarContenido({ tipoObjetivo, objetivoId }) {
  const esEspacio = tipoObjetivo === TIPO_ESPACIO
  const [abierto, setAbierto] = useState(false)
  const [motivo, setMotivo] = useState('')
  const [errorMotivo, setErrorMotivo] = useState(null)
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [reportado, setReportado] = useState(false)

  function abrir() {
    setError(null)
    setErrorMotivo(null)
    setAbierto(true)
  }

  async function enviar() {
    setEnviando(true)
    setErrorMotivo(null)
    try {
      await reportarContenido({ tipoObjetivo, objetivoId, motivo: motivo === '' ? null : motivo })
      setAbierto(false)
      setReportado(true)
    } catch (falla) {
      const deMotivo = (falla.errores ?? []).find((e) => e.campo === CAMPO_MOTIVO)
      if (deMotivo) {
        setErrorMotivo(deMotivo.mensaje)
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
      <Boton variante="secundario" deshabilitado={reportado} onClick={abrir}>
        {reportado ? BOTON_REPORTADO : esEspacio ? BOTON_REPORTAR_PUBLICACION : BOTON_REPORTAR_RESENA}
      </Boton>
      {reportado && <Alerta tipo="exito">{REPORTE_ENVIADO}</Alerta>}
      {error && <Alerta tipo="error">{error}</Alerta>}
      <Modal
        abierto={abierto}
        titulo={esEspacio ? TITULO_REPORTAR_PUBLICACION : TITULO_REPORTAR_RESENA}
        textoConfirmar={BOTON_ENVIAR_REPORTE}
        variante="primario"
        cargando={enviando}
        onConfirmar={enviar}
        onCerrar={() => setAbierto(false)}
      >
        <Campo
          etiqueta={ETIQUETA_MOTIVO_REPORTE}
          nombre={CAMPO_MOTIVO}
          tipo="select"
          valor={motivo}
          onChange={setMotivo}
          opciones={OPCIONES_MOTIVO_REPORTE}
          ayuda={AYUDA_REPORTE}
          error={errorMotivo}
        />
      </Modal>
    </div>
  )
}

export default ReportarContenido
