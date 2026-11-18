import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listarReclamables } from '../../api/administracion.js'
import { listarResenasPendientes } from '../../api/busqueda.js'
import { cancelarReserva, listarMisReservas, obtenerCancelacion } from '../../api/reservas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import { ENLACE_VER_RECLAMO, RECLAMO_ABIERTO } from '../administracion/textosAdministracion.js'
import { formatoHora, formatoPrecio } from '../espacios/textosEspacios.js'
import CancelarReserva from './CancelarReserva.jsx'
import ReportarProblema from './ReportarProblema.jsx'
import ResenarReserva from './ResenarReserva.jsx'
import SeccionReservas from './SeccionReservas.jsx'
import {
  ENLACE_VER_ESPACIO,
  ENLACE_VER_RESERVA,
  RESENA_PUBLICADA,
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
// HU-15 y HU-24: "accion" es "Cancelar reserva" en las CONFIRMADA de Próximas y "Calificar" en las Pasadas reseñables.
function tarjetaDeReserva(reserva, accion = null) {
  return (
    <Tarjeta
      titulo={reserva.espacioNombre}
      acciones={
        <div className={estilos.acciones}>
          <Link to={`/espacios/${reserva.espacioId}`}>{ENLACE_VER_ESPACIO}</Link>
          <Link to={`/reservas/${reserva.id}`}>{ENLACE_VER_RESERVA}</Link>
          {accion}
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
// HU-24: qué reservas se pueden reseñar lo dice la API y se relee con "recarga"; si esa consulta falla, la lista
// funciona igual, sin el botón.
// HU-26: igual con las reclamables (Próximas y Pasadas); si la consulta falla, la lista funciona sin el botón.
function MisReservas() {
  const [recarga, setRecarga] = useState(0)
  const [exito, setExito] = useState(null)
  const [reclamoAbierto, setReclamoAbierto] = useState(null)
  const [porResenar, setPorResenar] = useState(() => new Set())
  const [reclamables, setReclamables] = useState(() => new Set())

  useEffect(() => {
    let activo = true
    listarReclamables()
      .then((datos) => {
        if (activo) setReclamables(new Set(datos.reservaIds))
      })
      .catch(() => {
        if (activo) setReclamables(new Set())
      })
    return () => {
      activo = false
    }
  }, [recarga])

  useEffect(() => {
    let activo = true
    listarResenasPendientes()
      .then((datos) => {
        if (activo) setPorResenar(new Set(datos.reservaIds))
      })
      .catch(() => {
        if (activo) setPorResenar(new Set())
      })
    return () => {
      activo = false
    }
  }, [recarga])

  function alResenar() {
    setReclamoAbierto(null)
    setExito(RESENA_PUBLICADA)
    setRecarga((anterior) => anterior + 1)
  }

  function alReportar(reclamo) {
    setExito(null)
    setReclamoAbierto(reclamo.id)
    setRecarga((anterior) => anterior + 1)
  }

  function reportar(reserva) {
    return reclamables.has(reserva.id) ? <ReportarProblema reserva={reserva} onReportado={alReportar} key="reportar" /> : null
  }

  function conAcciones(...acciones) {
    const presentes = acciones.filter(Boolean)
    if (presentes.length === 0) return null
    return <>{presentes}</>
  }

  function alCancelar(resultado) {
    setReclamoAbierto(null)
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
          key="cancelar"
        />
      ) : null
    return tarjetaDeReserva(reserva, conAcciones(cancelar, reportar(reserva)))
  }

  function tarjetaPasada(reserva) {
    const calificar = porResenar.has(reserva.id) ? (
      <ResenarReserva reserva={reserva} onResenada={alResenar} key="calificar" />
    ) : null
    return tarjetaDeReserva(reserva, conAcciones(calificar, reportar(reserva)))
  }

  return (
    <section className={estilos.pantalla}>
      <h2 className={estilos.titulo}>{TITULO_MIS_RESERVAS}</h2>
      {exito && <Alerta tipo="exito">{exito}</Alerta>}
      {reclamoAbierto && (
        <Alerta tipo="exito">
          {RECLAMO_ABIERTO} <Link to={`/reclamos/${reclamoAbierto}`}>{ENLACE_VER_RECLAMO}</Link>
        </Alerta>
      )}
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
        renderReserva={tarjetaPasada}
      />
    </section>
  )
}

export default MisReservas
