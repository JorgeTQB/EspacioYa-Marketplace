import { Link, Navigate } from 'react-router-dom'
import { listarMisLiquidaciones } from '../../api/pagos.js'
import Alerta from '../../components/ui/Alerta.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { fechaHora } from '../administracion/textosAdministracion.js'
import { formatoPrecio } from '../espacios/textosEspacios.js'
import { formatoFecha } from '../reservas/textosReservas.js'
import LineasLiquidacion from './LineasLiquidacion.jsx'
import SeccionLiquidaciones from './SeccionLiquidaciones.jsx'
import {
  AVISO_LIQUIDACION_SIMULADA,
  AVISO_REGISTRA_CUENTA,
  DESCRIPCION_MIS_LIQUIDACIONES,
  ENLACE_CUENTA_BANCARIA,
  ETIQUETA_BRUTO,
  ETIQUETA_COMISION,
  ETIQUETA_NETO,
  LIQUIDACION_EN_PROCESO,
  SIN_MIS_LIQUIDACIONES,
  TITULO_MIS_LIQUIDACIONES,
  liquidacionDel,
  pagadaAlArrendador,
} from './textosPagos.js'
import estilos from './MisLiquidaciones.module.css'

function cargarMisLiquidaciones({ pagina }) {
  return listarMisLiquidaciones(pagina)
}

function TarjetaMiLiquidacion({ liquidacion }) {
  return (
    <Tarjeta titulo={liquidacionDel(formatoFecha(liquidacion.periodo))}>
      <div>
        <EtiquetaEstado estado={liquidacion.estado} />
      </div>
      <p className={estilos.dato}>
        {ETIQUETA_BRUTO}: <strong className={estilos.monto}>{formatoPrecio(liquidacion.montoBruto)}</strong>
      </p>
      <p className={estilos.dato}>
        {ETIQUETA_COMISION}: <strong className={estilos.monto}>{formatoPrecio(liquidacion.comision)}</strong>
      </p>
      <p className={estilos.dato}>
        {ETIQUETA_NETO}: <strong className={estilos.monto}>{formatoPrecio(liquidacion.montoNeto)}</strong>
      </p>
      {liquidacion.estado === 'PAGADA' && liquidacion.pago && (
        <p className={estilos.dato}>
          {pagadaAlArrendador(fechaHora(liquidacion.pago.pagadaEn), liquidacion.pago.numeroOperacion,
            liquidacion.pago.banco, liquidacion.pago.cuentaEnmascarada)}
        </p>
      )}
      {liquidacion.estado === 'PENDIENTE_CUENTA' && (
        <Alerta tipo="atencion">
          {AVISO_REGISTRA_CUENTA} <Link to="/panel/cuenta-bancaria">{ENLACE_CUENTA_BANCARIA}</Link>
        </Alerta>
      )}
      {liquidacion.estado === 'GENERADA' && <p className={estilos.dato}>{LIQUIDACION_EN_PROCESO}</p>}
      {liquidacion.simulada && <p className={estilos.dato}>{AVISO_LIQUIDACION_SIMULADA}</p>}
      <LineasLiquidacion lineas={liquidacion.reservas} />
    </Tarjeta>
  )
}

function renderMiLiquidacion(liquidacion) {
  return <TarjetaMiLiquidacion liquidacion={liquidacion} />
}

// HU-19 CA2 y CA3: el arrendador ve sus liquidaciones, su estado y, si están pagadas, a dónde y con qué operación. Sin
// totales acumulados: el "Panel de ingresos" está fuera del alcance.
function MisLiquidaciones() {
  const { sesion } = useSesion()

  if (sesion?.rol !== 'ARRENDADOR') return <Navigate to="/panel" replace />

  return (
    <section className={estilos.pantalla}>
      <h2>{TITULO_MIS_LIQUIDACIONES}</h2>
      <p className={estilos.dato}>{DESCRIPCION_MIS_LIQUIDACIONES}</p>
      <SeccionLiquidaciones
        cargar={cargarMisLiquidaciones}
        textoVacio={SIN_MIS_LIQUIDACIONES}
        renderLiquidacion={renderMiLiquidacion}
      />
    </section>
  )
}

export default MisLiquidaciones
