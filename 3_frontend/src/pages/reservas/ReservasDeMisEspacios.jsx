import { Link } from 'react-router-dom'
import { listarReservasDeMisEspacios } from '../../api/reservas.js'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import { formatoHora, formatoPrecio } from '../espacios/textosEspacios.js'
import SeccionReservas from './SeccionReservas.jsx'
import {
  ENLACE_VER_ESPACIO,
  RESERVADO_POR,
  SIN_CONFIRMADAS_PASADAS,
  SIN_CONFIRMADAS_PROXIMAS,
  TITULO_PASADAS,
  TITULO_PROXIMAS,
  TITULO_RESERVAS_DE_MIS_ESPACIOS,
  formatoFecha,
} from './textosReservas.js'
import estilos from './ReservasDeMisEspacios.module.css'

// "usuarioNombre" llega tal cual del servidor (su nombre o "Usuario de EspacioYa"); la API nunca envía correo ni
// teléfono. HU-15 agregará "Cancelar" en acciones.
function tarjetaDeReserva(reserva) {
  return (
    <Tarjeta
      titulo={reserva.espacioNombre}
      acciones={<Link to={`/panel/espacios/${reserva.espacioId}`}>{ENLACE_VER_ESPACIO}</Link>}
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

// HU-14 CA2: las reservas confirmadas de los espacios del arrendador de la sesión, en próximas y pasadas.
function ReservasDeMisEspacios() {
  return (
    <section className={estilos.pantalla}>
      <h2 className={estilos.titulo}>{TITULO_RESERVAS_DE_MIS_ESPACIOS}</h2>
      <SeccionReservas
        titulo={TITULO_PROXIMAS}
        grupo="proximas"
        cargar={listarReservasDeMisEspacios}
        textoVacio={SIN_CONFIRMADAS_PROXIMAS}
        renderReserva={tarjetaDeReserva}
      />
      <SeccionReservas
        titulo={TITULO_PASADAS}
        grupo="pasadas"
        cargar={listarReservasDeMisEspacios}
        textoVacio={SIN_CONFIRMADAS_PASADAS}
        renderReserva={tarjetaDeReserva}
      />
    </section>
  )
}

export default ReservasDeMisEspacios
