import { useState } from 'react'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Modal from '../../components/ui/Modal.jsx'
import { formatoHora, formatoPrecio } from '../espacios/textosEspacios.js'
import {
  BOTON_CANCELAR_RESERVA,
  BOTON_CONFIRMAR_CANCELACION,
  BOTON_VOLVER,
  ETIQUETA_ESPACIO,
  ETIQUETA_FECHA,
  ETIQUETA_HORARIO,
  ETIQUETA_MONTO_PAGADO,
  LINEA_REEMBOLSO,
  TITULO_CANCELAR,
  formatoFecha,
} from './textosReservas.js'
import estilos from './CancelarReserva.module.css'

const CAMPO_MONTO_VISTO = 'montoReembolsoVisto'

// El servidor rechaza con 400 en montoReembolsoVisto si el monto cambió entre la vista previa y la confirmación (R32).
function errorDeMontoVisto(falla) {
  return falla.errores?.find((error) => error.campo === CAMPO_MONTO_VISTO)
}

function lineaReembolso(vista) {
  const linea = LINEA_REEMBOLSO[vista.politica]
  if (!linea) return null
  return (
    <p className={estilos.reembolso}>
      {linea.antes}
      {linea.despues && (
        <>
          <span className={estilos.monto}>{formatoPrecio(vista.montoReembolso)}</span>
          {linea.despues}
        </>
      )}
    </p>
  )
}

// HU-15: "Cancelar reserva" de una tarjeta CONFIRMADA de Próximas. Pide la vista previa (CA2), muestra lo que el
// servidor calculó y confirma enviando el monto visto tal cual (CA3). No decide nada: la política, el porcentaje y el
// monto son los de la API. obtener y confirmar son las funciones de api/reservas.js del usuario o del arrendador.
function CancelarReserva({ reserva, obtener, confirmar, onCancelada }) {
  const [vista, setVista] = useState(null)
  const [abierto, setAbierto] = useState(false)
  const [cargandoVista, setCargandoVista] = useState(false)
  const [cancelando, setCancelando] = useState(false)
  const [error, setError] = useState(null)
  const [aviso, setAviso] = useState(null)

  async function abrir() {
    setCargandoVista(true)
    setError(null)
    try {
      const datos = await obtener(reserva.id)
      setVista(datos)
      setAviso(null)
      setAbierto(true)
    } catch (falla) {
      setError(falla.mensaje)
    } finally {
      setCargandoVista(false)
    }
  }

  function cerrar() {
    setAbierto(false)
    setAviso(null)
  }

  async function confirmarCancelacion() {
    setCancelando(true)
    try {
      const resultado = await confirmar(reserva.id, vista.montoReembolso)
      setAbierto(false)
      onCancelada(resultado)
    } catch (falla) {
      const montoCambio = errorDeMontoVisto(falla)
      if (!montoCambio) {
        setAbierto(false)
        setError(falla.mensaje)
        return
      }
      setAviso(montoCambio.mensaje)
      try {
        setVista(await obtener(reserva.id))
      } catch (otraFalla) {
        setAbierto(false)
        setError(otraFalla.mensaje)
      }
    } finally {
      setCancelando(false)
    }
  }

  return (
    <div className={estilos.cancelar}>
      <Boton variante="peligro" cargando={cargandoVista} onClick={abrir}>
        {BOTON_CANCELAR_RESERVA}
      </Boton>
      {error && <Alerta tipo="error">{error}</Alerta>}
      {vista && (
        <Modal
          abierto={abierto}
          titulo={TITULO_CANCELAR}
          variante="peligro-confirmar"
          textoConfirmar={BOTON_CONFIRMAR_CANCELACION}
          textoCerrar={BOTON_VOLVER}
          cargando={cancelando}
          onConfirmar={confirmarCancelacion}
          onCerrar={cerrar}
        >
          {aviso && <Alerta tipo="atencion">{aviso}</Alerta>}
          <dl className={estilos.datos}>
            <dt>{ETIQUETA_ESPACIO}</dt>
            <dd>{vista.espacioNombre}</dd>
            <dt>{ETIQUETA_FECHA}</dt>
            <dd>{formatoFecha(vista.fecha)}</dd>
            <dt>{ETIQUETA_HORARIO}</dt>
            <dd>{`${formatoHora(vista.horaInicio)}–${formatoHora(vista.horaFin)}`}</dd>
            <dt>{ETIQUETA_MONTO_PAGADO}</dt>
            <dd className={estilos.monto}>{formatoPrecio(vista.montoPagado)}</dd>
          </dl>
          {lineaReembolso(vista)}
        </Modal>
      )}
    </div>
  )
}

export default CancelarReserva
