import { useCallback, useState } from 'react'
import {
  cancelarReservaDeMiEspacio,
  listarReservasDeMisEspacios,
  obtenerCancelacionDeMiEspacio,
} from '../../api/reservas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import CancelarReserva from '../reservas/CancelarReserva.jsx'
import SeccionReservas from '../reservas/SeccionReservas.jsx'
import TarjetaReservaDeMiEspacio from '../reservas/TarjetaReservaDeMiEspacio.jsx'
import { RESERVA_CANCELADA_ARRENDADOR } from '../reservas/textosReservas.js'
import { SIN_RESERVAS_AFECTADAS, TITULO_RESERVAS_AFECTADAS } from './textosEspacios.js'
import estilos from './ReservasAfectadas.module.css'

// HU-21 CA2: las reservas confirmadas próximas de este espacio, para cancelarlas una a una con HU-15 (la línea del
// reembolso del 100 % la pone CancelarReserva según la política ARRENDADOR que calcula la API). Tras cada
// cancelación, "recarga" cambia la key de la sección y la cancelada deja de aparecer.
function ReservasAfectadas({ espacioId }) {
  const [recarga, setRecarga] = useState(0)
  const [exito, setExito] = useState(null)
  // Estable: SeccionReservas la tiene en las dependencias de su efecto.
  const cargar = useCallback(
    ({ grupo, pagina }) => listarReservasDeMisEspacios({ grupo, pagina, espacioId }),
    [espacioId],
  )

  function alCancelar() {
    setExito(RESERVA_CANCELADA_ARRENDADOR)
    setRecarga((anterior) => anterior + 1)
  }

  function tarjeta(reserva) {
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
    <div className={estilos.afectadas}>
      {exito && <Alerta tipo="exito">{exito}</Alerta>}
      <SeccionReservas
        key={recarga}
        titulo={TITULO_RESERVAS_AFECTADAS}
        grupo="proximas"
        cargar={cargar}
        textoVacio={SIN_RESERVAS_AFECTADAS}
        renderReserva={tarjeta}
      />
    </div>
  )
}

export default ReservasAfectadas
