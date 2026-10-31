import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { iniciarPago, obtenerEstadoPago } from '../../api/pagos.js'
import { obtenerReserva } from '../../api/reservas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import CuentaRegresiva from '../../components/ui/CuentaRegresiva.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import { formatoHora, formatoPrecio } from '../espacios/textosEspacios.js'
import {
  BOTON_PAGAR,
  CARGANDO,
  CONFIRMANDO_PAGO,
  ENLACE_BUSCAR,
  ENLACE_VOLVER,
  INTENTOS_AGOTADOS,
  PAGO_EN_REVISION,
  PAGO_NO_APLICADO,
  PAGO_NO_DISPONIBLE,
  PAGO_RECHAZADO,
  RESERVA_CONFIRMADA,
  RETENCION,
  SE_ACABO_EL_TIEMPO,
  formatoFecha,
} from './textosReservas.js'
import estilos from './ResumenReserva.module.css'

const PENDIENTE_PAGO = 'PENDIENTE_PAGO'
const CONFIRMADA = 'CONFIRMADA'
const EXPIRADA = 'EXPIRADA'
const RECHAZADO = 'RECHAZADO'
const NO_APLICADOS = ['PAGADO_TARDIO', 'MONTO_DISTINTO']
const NO_ENCONTRADO = 404
const PAGO_EXITO = 'exito'
const PAGO_FALLO = 'fallo'
const MILISEGUNDOS_ENTRE_SONDEOS = 3000
const MAXIMO_SONDEOS = 10

function pagoNoAplicado(pago) {
  return NO_APLICADOS.includes(pago.ultimoResultado)
}

// Volvió de la pasarela con ?pago=exito y la notificación aún no llega. El parámetro solo elige el mensaje: nunca
// confirma nada. Una reserva que vence en esta espera no es "se acabó el tiempo": el usuario ya pagó.
function esperaConfirmacion(reserva, pago, retorno) {
  const sinConfirmar = reserva.estado === PENDIENTE_PAGO || reserva.estado === EXPIRADA
  return retorno === PAGO_EXITO && sinConfirmar && !pagoNoAplicado(pago)
}

function avisosDelPago(reserva, pago, retorno, sondeando) {
  const avisos = []
  if (pagoNoAplicado(pago)) {
    avisos.push({ tipo: 'atencion', texto: PAGO_NO_APLICADO })
  }
  if (reserva.estado === CONFIRMADA && retorno === PAGO_EXITO) {
    avisos.push({ tipo: 'exito', texto: RESERVA_CONFIRMADA })
  }
  if (esperaConfirmacion(reserva, pago, retorno)) {
    avisos.push(sondeando
      ? { tipo: 'info', texto: CONFIRMANDO_PAGO }
      : { tipo: 'atencion', texto: PAGO_EN_REVISION })
    return avisos
  }
  if (reserva.estado === EXPIRADA) {
    avisos.push({ tipo: 'info', texto: SE_ACABO_EL_TIEMPO })
  }
  if (reserva.estado !== PENDIENTE_PAGO) {
    return avisos
  }
  if (pago.intentosAgotados) {
    avisos.push({ tipo: 'atencion', texto: INTENTOS_AGOTADOS })
  } else if (!pago.pagoDisponible) {
    avisos.push({ tipo: 'info', texto: PAGO_NO_DISPONIBLE })
  } else if (pago.ultimoResultado === RECHAZADO || retorno === PAGO_FALLO) {
    avisos.push({ tipo: 'error', texto: PAGO_RECHAZADO })
  }
  return avisos
}

// HU-12 y HU-13: resumen de la reserva y su pago. El contador solo avisa: al llegar a cero se relee y el estado lo
// decide el servidor. El contador se reinicia con cada respuesta (lectura) y no con cada petición: el servidor redondea
// hacia abajo, así que al llegar a cero la reserva aún puede estar vigente con 0 segundos. Entonces cuenta ese segundo y
// vuelve a leer.
function ResumenReserva() {
  const { id } = useParams()
  const [parametros] = useSearchParams()
  const [respuesta, setRespuesta] = useState(null)
  const [recarga, setRecarga] = useState(0)
  const [sondeos, setSondeos] = useState(0)
  const [pagando, setPagando] = useState(false)
  const [errorPago, setErrorPago] = useState(null)

  const retorno = parametros.get('pago')

  useEffect(() => {
    let activo = true
    Promise.all([obtenerReserva(id), obtenerEstadoPago(id)])
      .then(([datos, pago]) => {
        if (activo) {
          setRespuesta((anterior) => ({ id, datos, pago, error: null, lectura: (anterior?.lectura ?? 0) + 1 }))
        }
      })
      .catch((error) => {
        if (!activo) return
        // Durante el sondeo, una lectura fallida no borra la reserva de quien ya pagó: conserva lo último y cuenta como
        // sondeo, porque el temporizador avanza igual (DEF-02).
        setRespuesta((anterior) => {
          const conservar = retorno === PAGO_EXITO && anterior?.id === id && anterior.datos
          return conservar ? anterior : { id, datos: null, pago: null, error }
        })
      })
    return () => {
      activo = false
    }
  }, [id, recarga, retorno])

  // Al volver con Atrás desde la pasarela, el navegador puede restaurar la página con "Pagar" aún ocupado (DEF-01).
  useEffect(() => {
    function alMostrarse(evento) {
      if (evento.persisted) {
        setPagando(false)
        setRecarga((numero) => numero + 1)
      }
    }
    window.addEventListener('pageshow', alMostrarse)
    return () => window.removeEventListener('pageshow', alMostrarse)
  }, [])

  const actual = respuesta?.id === id ? respuesta : null
  const esperando = Boolean(actual?.datos) && esperaConfirmacion(actual.datos, actual.pago, retorno)
  const sondeando = esperando && sondeos < MAXIMO_SONDEOS

  useEffect(() => {
    if (!sondeando) return undefined
    const espera = setTimeout(() => {
      setSondeos((numero) => numero + 1)
      setRecarga((numero) => numero + 1)
    }, MILISEGUNDOS_ENTRE_SONDEOS)
    return () => clearTimeout(espera)
  }, [sondeando, sondeos])

  function recargar() {
    setRecarga((numero) => numero + 1)
  }

  async function pagar() {
    setPagando(true)
    setErrorPago(null)
    try {
      const { urlPago } = await iniciarPago(id)
      // En la misma pestaña: el token vive en sessionStorage, que una pestaña nueva no comparte.
      window.location.assign(urlPago)
    } catch (error) {
      setErrorPago(error.mensaje)
      setPagando(false)
      recargar()
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
        {actual.error.estado === NO_ENCONTRADO && (
          <p>
            <Link to="/buscar">{ENLACE_BUSCAR}</Link>
          </p>
        )}
      </section>
    )
  }

  const reserva = actual.datos
  const pago = actual.pago
  const avisos = avisosDelPago(reserva, pago, retorno, sondeando)
  const puedePagar = reserva.estado === PENDIENTE_PAGO && pago.pagoDisponible && !esperando

  return (
    <section className={estilos.pantalla}>
      {errorPago && <Alerta tipo="error">{errorPago}</Alerta>}
      {avisos.map((aviso) => (
        <Alerta key={aviso.texto} tipo={aviso.tipo}>
          {aviso.texto}
        </Alerta>
      ))}
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
        {puedePagar && (
          <div className={estilos.pagar}>
            <Boton cargando={pagando} onClick={pagar}>
              {BOTON_PAGAR}
            </Boton>
          </div>
        )}
      </Tarjeta>
    </section>
  )
}

export default ResumenReserva
