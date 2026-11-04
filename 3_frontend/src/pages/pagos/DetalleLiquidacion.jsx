import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { marcarLiquidacionPagada, obtenerLiquidacion } from '../../api/pagos.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Modal from '../../components/ui/Modal.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { fechaHora } from '../administracion/textosAdministracion.js'
import { formatoPrecio } from '../espacios/textosEspacios.js'
import { formatoFecha } from '../reservas/textosReservas.js'
import LineasLiquidacion from './LineasLiquidacion.jsx'
import {
  AVISOS_ARRENDADOR,
  AVISO_LIQUIDACION_SIMULADA,
  AYUDA_NUMERO_OPERACION,
  BOTON_MARCAR_PAGADA,
  CARGANDO_LIQUIDACIONES,
  ENLACE_VOLVER_LIQUIDACIONES,
  ETIQUETA_BRUTO,
  ETIQUETA_COMISION,
  ETIQUETA_NETO,
  ETIQUETA_NUMERO_OPERACION,
  LIQUIDACION_MARCADA,
  NOTA_NUMERO_COMPLETO,
  SIN_CUENTA_DESTINO,
  TITULO_CUENTA_DESTINO,
  TITULO_MODAL_MARCAR,
  confirmacionMarcarPagada,
  cuentaRetenidaHasta,
  generadaEl,
  liquidacionDel,
  pagadaConDestino,
} from './textosPagos.js'
import estilos from './DetalleLiquidacion.module.css'

const CAMPO_OPERACION = 'numeroOperacion'

function CuentaDestino({ cuenta }) {
  if (!cuenta) {
    return <Alerta tipo="atencion">{SIN_CUENTA_DESTINO}</Alerta>
  }
  return (
    <Tarjeta titulo={TITULO_CUENTA_DESTINO}>
      <p className={estilos.dato}>{cuenta.banco}</p>
      <p className={estilos.dato}>{cuenta.titular}</p>
      {cuenta.numeroCompleto ? (
        <>
          <p className={estilos.numero}>{cuenta.numeroCompleto}</p>
          <p className={estilos.dato}>{NOTA_NUMERO_COMPLETO}</p>
        </>
      ) : (
        <>
          <p className={estilos.numero}>{cuenta.numeroEnmascarado}</p>
          <div>
            <EtiquetaEstado estado={cuenta.estado} />
          </div>
          {cuenta.estado === 'RETENIDA' && (
            <Alerta tipo="atencion">{cuentaRetenidaHasta(fechaHora(cuenta.retenidaHasta))}</Alerta>
          )}
        </>
      )}
    </Tarjeta>
  )
}

// HU-19 CA2, CA3, D4–D6: el admin revisa la liquidación, ve la cuenta de destino (completa solo si la API la entrega,
// es decir, si se puede pagar) y registra el número de operación de la transferencia. Si se puede pagar lo decide el
// servidor (`pagable`).
function DetalleLiquidacion() {
  const { id } = useParams()
  const { sesion } = useSesion()
  const esAdmin = sesion?.rol === 'ADMIN'
  const [detalle, setDetalle] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [recarga, setRecarga] = useState(0)
  const [marcando, setMarcando] = useState(false)
  const [numero, setNumero] = useState('')
  const [errorNumero, setErrorNumero] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState(null)

  useEffect(() => {
    if (!esAdmin) return undefined
    let activo = true
    obtenerLiquidacion(id)
      .then((datos) => {
        if (activo) {
          setDetalle(datos)
          setError(null)
        }
      })
      .catch((falla) => {
        if (activo) setError(falla.mensaje)
      })
      .finally(() => {
        if (activo) setCargando(false)
      })
    return () => {
      activo = false
    }
  }, [esAdmin, id, recarga])

  if (!esAdmin) return <Navigate to="/panel" replace />

  function abrirModal() {
    setNumero('')
    setErrorNumero(null)
    setMarcando(true)
  }

  function cerrarModal() {
    if (!enviando) setMarcando(false)
  }

  function marcar() {
    setEnviando(true)
    setErrorNumero(null)
    setResultado(null)
    marcarLiquidacionPagada(id, numero.trim())
      .then((respuesta) => {
        setDetalle(respuesta)
        setMarcando(false)
        setResultado({ tipo: 'exito', texto: LIQUIDACION_MARCADA })
      })
      .catch((falla) => {
        const deCampo = falla.errores?.find((errorCampo) => errorCampo.campo === CAMPO_OPERACION)
        if (deCampo) {
          setErrorNumero(deCampo.mensaje)
          return
        }
        setMarcando(false)
        setResultado({ tipo: 'error', texto: falla.mensaje })
        setRecarga((n) => n + 1)
      })
      .finally(() => {
        setEnviando(false)
      })
  }

  const volver = <Link to="/admin/liquidaciones">{ENLACE_VOLVER_LIQUIDACIONES}</Link>

  if (cargando) {
    return (
      <section className={estilos.pantalla}>
        <p className={estilos.dato}>{CARGANDO_LIQUIDACIONES}</p>
      </section>
    )
  }

  if (!detalle) {
    return (
      <section className={estilos.pantalla}>
        {error && <Alerta tipo="error">{error}</Alerta>}
        <p>{volver}</p>
      </section>
    )
  }

  return (
    <section className={estilos.pantalla}>
      <p>{volver}</p>
      <h2>{detalle.arrendadorNombre}</h2>
      <div>
        <EtiquetaEstado estado={detalle.estado} />
      </div>
      {resultado && <Alerta tipo={resultado.tipo}>{resultado.texto}</Alerta>}
      {error && <Alerta tipo="error">{error}</Alerta>}
      <p className={estilos.dato}>{liquidacionDel(formatoFecha(detalle.periodo))}</p>
      <p className={estilos.dato}>{generadaEl(fechaHora(detalle.generadaEn))}</p>
      <p className={estilos.dato}>
        {ETIQUETA_BRUTO}: <strong className={estilos.monto}>{formatoPrecio(detalle.montoBruto)}</strong>
      </p>
      <p className={estilos.dato}>
        {ETIQUETA_COMISION}: <strong className={estilos.monto}>{formatoPrecio(detalle.comision)}</strong>
      </p>
      <p className={estilos.dato}>
        {ETIQUETA_NETO}: <strong className={estilos.monto}>{formatoPrecio(detalle.montoNeto)}</strong>
      </p>
      {(detalle.avisos ?? []).map((aviso) => (
        <Alerta key={aviso} tipo="atencion">
          {AVISOS_ARRENDADOR[aviso] ?? aviso}
        </Alerta>
      ))}
      {detalle.simulada && <Alerta tipo="atencion">{AVISO_LIQUIDACION_SIMULADA}</Alerta>}

      {detalle.pago ? (
        <p className={estilos.dato}>
          {pagadaConDestino(fechaHora(detalle.pago.pagadaEn), detalle.pago.numeroOperacion, detalle.pago.banco,
            detalle.pago.cuentaEnmascarada)}
        </p>
      ) : (
        <CuentaDestino cuenta={detalle.cuenta} />
      )}

      <LineasLiquidacion lineas={detalle.reservas} />

      {detalle.pagable && (
        <div>
          <Boton variante="primario" onClick={abrirModal}>
            {BOTON_MARCAR_PAGADA}
          </Boton>
        </div>
      )}

      <Modal
        abierto={marcando}
        titulo={TITULO_MODAL_MARCAR}
        textoConfirmar={BOTON_MARCAR_PAGADA}
        variante="primario"
        cargando={enviando}
        onConfirmar={marcar}
        onCerrar={cerrarModal}
      >
        {detalle.cuenta && (
          <p>
            {confirmacionMarcarPagada(formatoPrecio(detalle.montoNeto), detalle.cuenta.banco,
              detalle.cuenta.numeroEnmascarado)}
          </p>
        )}
        <Campo
          etiqueta={ETIQUETA_NUMERO_OPERACION}
          nombre={CAMPO_OPERACION}
          valor={numero}
          onChange={setNumero}
          error={errorNumero}
          ayuda={AYUDA_NUMERO_OPERACION}
        />
      </Modal>
    </section>
  )
}

export default DetalleLiquidacion
