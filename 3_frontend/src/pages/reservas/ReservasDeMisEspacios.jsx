import { useState } from 'react'
import {
  cancelarReservaDeMiEspacio,
  listarReservasDeMisEspacios,
  obtenerCancelacionDeMiEspacio,
} from '../../api/reservas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import CancelarReserva from './CancelarReserva.jsx'
import SeccionReservas from './SeccionReservas.jsx'
import TarjetaReservaDeMiEspacio from './TarjetaReservaDeMiEspacio.jsx'
import {
  RESERVA_CANCELADA_ARRENDADOR,
  SIN_CONFIRMADAS_PASADAS,
  SIN_CONFIRMADAS_PROXIMAS,
  TITULO_PASADAS,
  TITULO_PROXIMAS,
  TITULO_RESERVAS_DE_MIS_ESPACIOS,
} from './textosReservas.js'
import estilos from './ReservasDeMisEspacios.module.css'

function tarjetaDeReserva(reserva) {
  return <TarjetaReservaDeMiEspacio reserva={reserva} />
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
    return <TarjetaReservaDeMiEspacio reserva={reserva} cancelar={cancelar} />
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
