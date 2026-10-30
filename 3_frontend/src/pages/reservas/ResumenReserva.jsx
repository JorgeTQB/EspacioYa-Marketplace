import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { obtenerReserva } from '../../api/reservas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import CuentaRegresiva from '../../components/ui/CuentaRegresiva.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import { formatoHora, formatoPrecio } from '../espacios/textosEspacios.js'
import {
  CARGANDO,
  ENLACE_BUSCAR,
  ENLACE_VOLVER,
  RETENCION,
  SE_ACABO_EL_TIEMPO,
  formatoFecha,
} from './textosReservas.js'
import estilos from './ResumenReserva.module.css'

const PENDIENTE_PAGO = 'PENDIENTE_PAGO'
const EXPIRADA = 'EXPIRADA'
const NO_ENCONTRADO = 404

// HU-12: resumen de la reserva. El contador solo avisa: al llegar a cero se relee y el estado lo decide el servidor.
// El contador se reinicia con cada respuesta (lectura) y no con cada petición: el servidor redondea hacia abajo, así
// que al llegar a cero la reserva aún puede estar vigente con 0 segundos. Entonces cuenta ese segundo y vuelve a leer.
function ResumenReserva() {
  const { id } = useParams()
  const [respuesta, setRespuesta] = useState(null)
  const [recarga, setRecarga] = useState(0)

  useEffect(() => {
    let activo = true
    obtenerReserva(id)
      .then((datos) => {
        if (activo) setRespuesta((anterior) => ({ id, datos, error: null, lectura: (anterior?.lectura ?? 0) + 1 }))
      })
      .catch((error) => {
        if (activo) setRespuesta({ id, datos: null, error })
      })
    return () => {
      activo = false
    }
  }, [id, recarga])

  const actual = respuesta?.id === id ? respuesta : null

  function recargar() {
    setRecarga((numero) => numero + 1)
  }

  if (!actual) {
    return (
      <section className={estilos.pantalla}>
        <p className={estilos.secundario}>{CARGANDO}</p>
      </section>
    )
  }

  if (actual.error) {
    return (
      <section className={estilos.pantalla}>
        <Alerta tipo="error">{actual.error.mensaje}</Alerta>
        {actual.error.estado === NO_ENCONTRADO && (
          <p>
            <Link to="/buscar">{ENLACE_BUSCAR}</Link>
          </p>
        )}
      </section>
    )
  }

  const reserva = actual.datos

  return (
    <section className={estilos.pantalla}>
      {reserva.estado === EXPIRADA && <Alerta tipo="info">{SE_ACABO_EL_TIEMPO}</Alerta>}
      <Tarjeta
        titulo={reserva.espacioNombre}
        acciones={<Link to={`/espacios/${reserva.espacioId}`}>{ENLACE_VOLVER}</Link>}
      >
        <EtiquetaEstado estado={reserva.estado} />
        <dl className={estilos.datos}>
          <dt>Fecha</dt>
          <dd>{formatoFecha(reserva.fecha)}</dd>
          <dt>Horario</dt>
          <dd>{`${formatoHora(reserva.horaInicio)}–${formatoHora(reserva.horaFin)}`}</dd>
          <dt>Precio por hora</dt>
          <dd>{formatoPrecio(reserva.precioHora)}</dd>
          <dt>Monto total</dt>
          <dd className={estilos.monto}>{formatoPrecio(reserva.montoTotal)}</dd>
        </dl>
        {reserva.estado === PENDIENTE_PAGO && (
          <p className={estilos.retencion}>
            {RETENCION}{' '}
            <CuentaRegresiva key={actual.lectura} segundos={reserva.segundosRestantes} alTerminar={recargar} />
          </p>
        )}
      </Tarjeta>
    </section>
  )
}

export default ResumenReserva
