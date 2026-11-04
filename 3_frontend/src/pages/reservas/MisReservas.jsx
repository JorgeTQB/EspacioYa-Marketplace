import { useState } from 'react'
import { Link } from 'react-router-dom'
import { cancelarReserva, listarMisReservas, obtenerCancelacion } from '../../api/reservas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import { formatoHora, formatoPrecio } from '../espacios/textosEspacios.js'
import CancelarReserva from './CancelarReserva.jsx'
import SeccionReservas from './SeccionReservas.jsx'
import {
  ENLACE_VER_ESPACIO,
  ENLACE_VER_RESERVA,
  RESERVA_CANCELADA_USUARIO,
  SIN_PASADAS,
  SIN_PROXIMAS,
  TITULO_MIS_RESERVAS,
  TITULO_PASADAS,
  TITULO_PROXIMAS,
  formatoFecha,
  reembolsoEnProceso,
} from './textosReservas.js'
import estilos from './MisReservas.module.css'

// "Ver reserva" va en todas, en cualquier estado (D10): en /reservas/:id están "Pagar" y el aviso de pago no aplicado.
// HU-15: "cancelar" es el botón de cancelación, solo para las CONFIRMADA de Próximas.
function tarjetaDeReserva(reserva, cancelar = null) {
  return (
    <Tarjeta
      titulo={reserva.espacioNombre}
      acciones={
        <div className={estilos.acciones}>
          <Link to={`/espacios/${reserva.espacioId}`}>{ENLACE_VER_ESPACIO}</Link>
          <Link to={`/reservas/${reserva.id}`}>{ENLACE_VER_RESERVA}</Link>
          {cancelar}
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

// HU-14 CA1: las reservas del usuario de la sesión, separadas por el servidor en próximas y pasadas. HU-15: tras
// cancelar, "recarga" cambia la key de las secciones y vuelven a pedir la página 1 (la cancelada pasa a Pasadas).
function MisReservas() {
  const [recarga, setRecarga] = useState(0)
  const [exito, setExito] = useState(null)

  function alCancelar(resultado) {
    const conReembolso = Number(resultado.montoReembolso) > 0
    setExito(
      conReembolso
        ? `${RESERVA_CANCELADA_USUARIO} ${reembolsoEnProceso(formatoPrecio(resultado.montoReembolso))}`
        : RESERVA_CANCELADA_USUARIO,
    )
    setRecarga((anterior) => anterior + 1)
  }

  function tarjetaProxima(reserva) {
    const cancelar =
      reserva.estado === 'CONFIRMADA' ? (
        <CancelarReserva
          reserva={reserva}
          obtener={obtenerCancelacion}
          confirmar={cancelarReserva}
          onCancelada={alCancelar}
        />
      ) : null
    return tarjetaDeReserva(reserva, cancelar)
  }

  return (
    <section className={estilos.pantalla}>
      <h2 className={estilos.titulo}>{TITULO_MIS_RESERVAS}</h2>
      {exito && <Alerta tipo="exito">{exito}</Alerta>}
      <SeccionReservas
        key={`proximas-${recarga}`}
        titulo={TITULO_PROXIMAS}
        grupo="proximas"
        cargar={listarMisReservas}
        textoVacio={SIN_PROXIMAS}
        renderReserva={tarjetaProxima}
      />
      <SeccionReservas
        key={`pasadas-${recarga}`}
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
