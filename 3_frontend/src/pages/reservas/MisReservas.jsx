import { Link } from 'react-router-dom'
import { listarMisReservas } from '../../api/reservas.js'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import { formatoHora, formatoPrecio } from '../espacios/textosEspacios.js'
import SeccionReservas from './SeccionReservas.jsx'
import {
  ENLACE_VER_ESPACIO,
  ENLACE_VER_RESERVA,
  SIN_PASADAS,
  SIN_PROXIMAS,
  TITULO_MIS_RESERVAS,
  TITULO_PASADAS,
  TITULO_PROXIMAS,
  formatoFecha,
} from './textosReservas.js'
import estilos from './MisReservas.module.css'

// "Ver reserva" va en todas, en cualquier estado (D10): en /reservas/:id están "Pagar" y el aviso de pago no aplicado.
function tarjetaDeReserva(reserva) {
  return (
    <Tarjeta
      titulo={reserva.espacioNombre}
      acciones={
        <div className={estilos.acciones}>
          <Link to={`/espacios/${reserva.espacioId}`}>{ENLACE_VER_ESPACIO}</Link>
          <Link to={`/reservas/${reserva.id}`}>{ENLACE_VER_RESERVA}</Link>
        </div>
      }
    >
      <div>
        <EtiquetaEstado estado={reserva.estado} />
      </div>
      <dl className={estilos.datos}>
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

// HU-14 CA1: las reservas del usuario de la sesión, separadas por el servidor en próximas y pasadas.
function MisReservas() {
  return (
    <section className={estilos.pantalla}>
      <h2 className={estilos.titulo}>{TITULO_MIS_RESERVAS}</h2>
      <SeccionReservas
        titulo={TITULO_PROXIMAS}
        grupo="proximas"
        cargar={listarMisReservas}
        textoVacio={SIN_PROXIMAS}
        renderReserva={tarjetaDeReserva}
      />
      <SeccionReservas
        titulo={TITULO_PASADAS}
        grupo="pasadas"
        cargar={listarMisReservas}
        textoVacio={SIN_PASADAS}
        renderReserva={tarjetaDeReserva}
      />
    </section>
  )
}

export default MisReservas
