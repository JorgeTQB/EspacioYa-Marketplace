import { Link } from 'react-router-dom'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import { formatoHora, formatoPrecio } from '../espacios/textosEspacios.js'
import { ENLACE_VER_ESPACIO, RESERVADO_POR, formatoFecha } from './textosReservas.js'
import estilos from './TarjetaReservaDeMiEspacio.module.css'

// "usuarioNombre" llega tal cual del servidor (su nombre o "Usuario de EspacioYa"); la API nunca envía correo ni
// teléfono. HU-15: "cancelar" es el botón de cancelación, solo para las CONFIRMADA de Próximas. HU-21 la reutiliza en
// "Reservas afectadas".
function TarjetaReservaDeMiEspacio({ reserva, cancelar = null }) {
  return (
    <Tarjeta
      titulo={reserva.espacioNombre}
      acciones={
        <div className={estilos.acciones}>
          <Link to={`/panel/espacios/${reserva.espacioId}`}>{ENLACE_VER_ESPACIO}</Link>
          {cancelar}
        </div>
      }
    >
      <div>
        <EtiquetaEstado estado={reserva.estado} />
      </div>
      <dl className={estilos.datos}>
        <dt>{RESERVADO_POR}</dt>
        <dd>{reserva.usuarioNombre}</dd>
        <dt>Fecha</dt>
        <dd>{formatoFecha(reserva.fecha)}</dd>
        <dt>Horario</dt>
        <dd>{`${formatoHora(reserva.horaInicio)}–${formatoHora(reserva.horaFin)}`}</dd>
        <dt>Monto</dt>
        <dd className={estilos.monto}>{formatoPrecio(reserva.montoTotal)}</dd>
      </dl>
    </Tarjeta>
  )
}

export default TarjetaReservaDeMiEspacio
