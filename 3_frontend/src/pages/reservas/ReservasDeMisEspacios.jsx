import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  cancelarReservaDeMiEspacio,
  listarReservasDeMisEspacios,
  obtenerCancelacionDeMiEspacio,
} from '../../api/reservas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import { formatoHora, formatoPrecio } from '../espacios/textosEspacios.js'
import CancelarReserva from './CancelarReserva.jsx'
import SeccionReservas from './SeccionReservas.jsx'
import {
  ENLACE_VER_ESPACIO,
  RESERVADO_POR,
  RESERVA_CANCELADA_ARRENDADOR,
  SIN_CONFIRMADAS_PASADAS,
  SIN_CONFIRMADAS_PROXIMAS,
  TITULO_PASADAS,
  TITULO_PROXIMAS,
  TITULO_RESERVAS_DE_MIS_ESPACIOS,
  formatoFecha,
} from './textosReservas.js'
import estilos from './ReservasDeMisEspacios.module.css'

// "usuarioNombre" llega tal cual del servidor (su nombre o "Usuario de EspacioYa"); la API nunca envía correo ni
// teléfono. HU-15: "cancelar" es el botón de cancelación, solo para las CONFIRMADA de Próximas.
function tarjetaDeReserva(reserva, cancelar = null) {
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

// HU-14 CA2: las reservas confirmadas de los espacios del arrendador de la sesión, en próximas y pasadas. HU-15: tras
// cancelar, "recarga" cambia la key de las secciones y la cancelada deja de aparecer (solo se listan CONFIRMADA).
function ReservasDeMisEspacios() {
  const [recarga, setRecarga] = useState(0)
  const [exito, setExito] = useState(null)

  function alCancelar() {
    setExito(RESERVA_CANCELADA_ARRENDADOR)
    setRecarga((anterior) => anterior + 1)
  }

  function tarjetaProxima(reserva) {
    const cancelar =
      reserva.estado === 'CONFIRMADA' ? (
        <CancelarReserva
          reserva={reserva}
          obtener={obtenerCancelacionDeMiEspacio}
          confirmar={cancelarReservaDeMiEspacio}
          onCancelada={alCancelar}
        />
      ) : null
    return tarjetaDeReserva(reserva, cancelar)
  }

  return (
    <section className={estilos.pantalla}>
      <h2 className={estilos.titulo}>{TITULO_RESERVAS_DE_MIS_ESPACIOS}</h2>
      {exito && <Alerta tipo="exito">{exito}</Alerta>}
      <SeccionReservas
        key={`proximas-${recarga}`}
        titulo={TITULO_PROXIMAS}
        grupo="proximas"
        cargar={listarReservasDeMisEspacios}
        textoVacio={SIN_CONFIRMADAS_PROXIMAS}
        renderReserva={tarjetaProxima}
      />
      <SeccionReservas
        key={`pasadas-${recarga}`}
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
