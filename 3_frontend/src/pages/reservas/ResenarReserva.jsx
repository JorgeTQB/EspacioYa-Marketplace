import { useState } from 'react'
import { crearResena } from '../../api/busqueda.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import Modal from '../../components/ui/Modal.jsx'
import {
  AYUDA_COMENTARIO,
  BOTON_CALIFICAR,
  BOTON_PUBLICAR_RESENA,
  ETIQUETA_CALIFICACION,
  ETIQUETA_COMENTARIO,
  OPCIONES_CALIFICACION,
  TITULO_CALIFICAR,
} from './textosReservas.js'
import estilos from './ResenarReserva.module.css'

const CAMPO_CALIFICACION = 'calificacion'
const CAMPO_COMENTARIO = 'comentario'

function mensajeDe(errores, campo) {
  return errores.find((error) => error.campo === campo)?.mensaje ?? null
}

// HU-24 (CA1, CA2, CA3): "Calificar" de una reserva que la API dijo que se puede reseñar. La pantalla no valida reglas:
// una calificación sin elegir viaja como null y responde el servidor. Los errores de calificación y comentario se
// quedan en el modal; cualquier otro rechazo lo cierra y se ve bajo el botón.
function ResenarReserva({ reserva, onResenada }) {
  const [abierto, setAbierto] = useState(false)
  const [calificacion, setCalificacion] = useState('')
  const [comentario, setComentario] = useState('')
  const [erroresCampo, setErroresCampo] = useState([])
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  function abrir() {
    setError(null)
    setErroresCampo([])
    setAbierto(true)
  }

  async function publicar() {
    setEnviando(true)
    setErroresCampo([])
    try {
      await crearResena({
        reservaId: reserva.id,
        calificacion: calificacion === '' ? null : Number(calificacion),
        comentario,
      })
      setAbierto(false)
      onResenada()
    } catch (falla) {
      const errores = falla.errores ?? []
      const deCampo = errores.filter((e) => e.campo === CAMPO_CALIFICACION || e.campo === CAMPO_COMENTARIO)
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
    <div className={estilos.resenar}>
      <Boton variante="secundario" onClick={abrir}>
        {BOTON_CALIFICAR}
      </Boton>
      {error && <Alerta tipo="error">{error}</Alerta>}
      <Modal
        abierto={abierto}
        titulo={TITULO_CALIFICAR}
        textoConfirmar={BOTON_PUBLICAR_RESENA}
        cargando={enviando}
        onConfirmar={publicar}
        onCerrar={() => setAbierto(false)}
      >
        <div className={estilos.campos}>
          <Campo
            etiqueta={ETIQUETA_CALIFICACION}
            nombre={CAMPO_CALIFICACION}
            tipo="select"
            valor={calificacion}
            onChange={setCalificacion}
            opciones={OPCIONES_CALIFICACION}
            error={mensajeDe(erroresCampo, CAMPO_CALIFICACION)}
          />
          <Campo
            etiqueta={ETIQUETA_COMENTARIO}
            nombre={CAMPO_COMENTARIO}
            tipo="textarea"
            valor={comentario}
            onChange={setComentario}
            ayuda={AYUDA_COMENTARIO}
            error={mensajeDe(erroresCampo, CAMPO_COMENTARIO)}
          />
        </div>
      </Modal>
    </div>
  )
}

export default ResenarReserva
