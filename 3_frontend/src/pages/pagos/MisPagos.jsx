import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { descargarComprobante, listarMisTransacciones } from '../../api/pagos.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { formatoPrecio } from '../espacios/textosEspacios.js'
import { formatoFecha } from '../reservas/textosReservas.js'
import SeccionLiquidaciones from './SeccionLiquidaciones.jsx'
import {
  AVISO_PAGO_SIMULADO,
  BOTON_DESCARGAR_COMPROBANTE,
  DESCRIPCION_MIS_PAGOS,
  SIN_PAGOS,
  TITULO_MIS_PAGOS,
  reembolsadoDe,
  tituloPago,
} from './textosPagos.js'
import estilos from './MisPagos.module.css'

// SeccionLiquidaciones lee la lista de `datos.liquidaciones`; aquí la API la llama `transacciones`, así que se adapta
// la respuesta en lugar de duplicar la sección "Ver más".
function cargarMisPagos({ pagina }) {
  return listarMisTransacciones(pagina).then((datos) => ({ ...datos, liquidaciones: datos.transacciones }))
}

// Baja el PDF con un enlace temporal, como DatosPersonales (HU-22).
function guardarPdf(blob, id) {
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = `comprobante-${id}.pdf`
  document.body.appendChild(enlace)
  enlace.click()
  enlace.remove()
  URL.revokeObjectURL(url)
}

function TarjetaPago({ transaccion }) {
  const [descargando, setDescargando] = useState(false)
  const [error, setError] = useState(null)

  async function descargar() {
    setDescargando(true)
    setError(null)
    try {
      guardarPdf(await descargarComprobante(transaccion.id), transaccion.id)
    } catch (falla) {
      setError(falla.mensaje)
    } finally {
      setDescargando(false)
    }
  }

  return (
    <Tarjeta titulo={tituloPago(transaccion.id)}>
      <div>
        <EtiquetaEstado estado={transaccion.estado} />
      </div>
      <p className={estilos.dato}>{formatoFecha(transaccion.fecha)}</p>
      <p className={estilos.dato}>{transaccion.espacio}</p>
      <p className={estilos.dato}>
        <strong className={estilos.monto}>{formatoPrecio(transaccion.monto)}</strong>
      </p>
      {transaccion.montoReembolsado != null && (
        <p className={estilos.dato}>{reembolsadoDe(formatoPrecio(transaccion.montoReembolsado))}</p>
      )}
      {transaccion.simulada && <p className={estilos.dato}>{AVISO_PAGO_SIMULADO}</p>}
      <div>
        <Boton variante="secundario" cargando={descargando} onClick={descargar}>
          {BOTON_DESCARGAR_COMPROBANTE}
        </Boton>
      </div>
      {error && <Alerta tipo="error">{error}</Alerta>}
    </Tarjeta>
  )
}

function renderPago(transaccion) {
  return <TarjetaPago transaccion={transaccion} />
}

// HU-28 CA1 y CA2: el usuario ve sus pagos y descarga el comprobante. Sin reglas aquí: qué pagos salen, el orden, los
// montos y el estado los decide el servidor; los últimos 4 dígitos de la tarjeta solo existen dentro del PDF.
function MisPagos() {
  const { sesion } = useSesion()

  if (sesion?.rol !== 'USUARIO') return <Navigate to="/panel" replace />

  return (
    <section className={estilos.pantalla}>
      <h2>{TITULO_MIS_PAGOS}</h2>
      <p className={estilos.dato}>{DESCRIPCION_MIS_PAGOS}</p>
      <SeccionLiquidaciones cargar={cargarMisPagos} textoVacio={SIN_PAGOS} renderLiquidacion={renderPago} />
    </section>
  )
}

export default MisPagos
