import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { obtenerSimulacion, simularPago } from '../../api/pagos.js'
import { obtenerReserva } from '../../api/reservas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import { formatoHora, formatoPrecio } from '../espacios/textosEspacios.js'
import { CARGANDO, ENLACE_BUSCAR, formatoFecha } from '../reservas/textosReservas.js'
import {
  AVISO_SIMULACION,
  BOTON_APROBAR,
  BOTON_RECHAZAR,
  ENLACE_VOLVER_RESERVA,
  SIMULACION_NO_DISPONIBLE,
  TITULO_PAGO_SIMULADO,
} from './textosPagos.js'
import estilos from './PagoSimulado.module.css'

const APROBADO = 'APROBADO'
const RECHAZADO = 'RECHAZADO'

// HU-13 D9: hace de "página de la pasarela" mientras no se conecte Mercado Pago. No tiene ningún campo: el monto sale del
// servidor y aquí solo se elige un resultado. Solo el backend habilita esta pantalla: si la lectura falla, no muestra
// nada de la simulación. Con replace, Atrás no vuelve a los botones.
function PagoSimulado() {
  const { id } = useParams()
  const navegar = useNavigate()
  const [lectura, setLectura] = useState(null)
  const [recarga, setRecarga] = useState(0)
  const [enviando, setEnviando] = useState(null)
  const [errorEnvio, setErrorEnvio] = useState(null)

  useEffect(() => {
    let activo = true
    Promise.all([obtenerSimulacion(id), obtenerReserva(id)])
      .then(([simulacion, reserva]) => {
        if (activo) setLectura({ id, simulacion, reserva, error: null })
      })
      .catch((error) => {
        if (activo) setLectura({ id, simulacion: null, reserva: null, error })
      })
    return () => {
      activo = false
    }
  }, [id, recarga])

  const actual = lectura?.id === id ? lectura : null

  async function simular(resultado) {
    setEnviando(resultado)
    setErrorEnvio(null)
    try {
      await simularPago(id, resultado)
      const pago = resultado === APROBADO ? 'exito' : 'fallo'
      navegar(`/reservas/${id}?pago=${pago}`, { replace: true })
    } catch (error) {
      setErrorEnvio(error.mensaje)
      setEnviando(null)
      setRecarga((numero) => numero + 1)
    }
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
        <p>
          <Link to="/buscar">{ENLACE_BUSCAR}</Link>
        </p>
      </section>
    )
  }

  const { simulacion, reserva } = actual

  if (!simulacion.disponible) {
    return (
      <section className={estilos.pantalla}>
        <Alerta tipo="atencion">{AVISO_SIMULACION}</Alerta>
        {errorEnvio && <Alerta tipo="error">{errorEnvio}</Alerta>}
        <Alerta tipo="info">{SIMULACION_NO_DISPONIBLE}</Alerta>
        <p>
          <Link to={`/reservas/${id}`}>{ENLACE_VOLVER_RESERVA}</Link>
        </p>
      </section>
    )
  }

  return (
    <section className={estilos.pantalla}>
      <h2>{TITULO_PAGO_SIMULADO}</h2>
      <Alerta tipo="atencion">{AVISO_SIMULACION}</Alerta>
      {errorEnvio && <Alerta tipo="error">{errorEnvio}</Alerta>}
      <Tarjeta titulo={reserva.espacioNombre}>
        <dl className={estilos.datos}>
          <dt>Fecha</dt>
          <dd>{formatoFecha(reserva.fecha)}</dd>
          <dt>Horario</dt>
          <dd>{`${formatoHora(reserva.horaInicio)}–${formatoHora(reserva.horaFin)}`}</dd>
          <dt>Monto total</dt>
          <dd className={estilos.monto}>{formatoPrecio(reserva.montoTotal)}</dd>
        </dl>
        <div className={estilos.acciones}>
          <Boton
            cargando={enviando === APROBADO}
            deshabilitado={enviando !== null}
            onClick={() => simular(APROBADO)}
          >
            {BOTON_APROBAR}
          </Boton>
          <Boton
            variante="secundario"
            cargando={enviando === RECHAZADO}
            deshabilitado={enviando !== null}
            onClick={() => simular(RECHAZADO)}
          >
            {BOTON_RECHAZAR}
          </Boton>
        </div>
      </Tarjeta>
    </section>
  )
}

export default PagoSimulado
