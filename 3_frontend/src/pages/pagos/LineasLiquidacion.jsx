import { formatoHora, formatoPrecio } from '../espacios/textosEspacios.js'
import { formatoFecha } from '../reservas/textosReservas.js'
import { LINEA_CANCELADA, LINEA_SIMULADA } from './textosPagos.js'
import estilos from './LineasLiquidacion.module.css'

// HU-19: las reservas de una liquidación (detalle del admin y "Mis liquidaciones"). Montos y marcas vienen de la API.
function LineasLiquidacion({ lineas }) {
  return (
    <ul className={estilos.lineas}>
      {lineas.map((linea) => (
        <li key={linea.reservaId} className={estilos.linea}>
          <span>{linea.espacioNombre ?? `Reserva #${linea.reservaId}`}</span>
          {linea.fecha && (
            <span className={estilos.secundario}>
              {formatoFecha(linea.fecha)} · {formatoHora(linea.horaInicio)}–{formatoHora(linea.horaFin)}
            </span>
          )}
          <strong className={estilos.monto}>{formatoPrecio(linea.monto)}</strong>
          {linea.cancelada && <span className={estilos.secundario}>{LINEA_CANCELADA}</span>}
          {linea.simulada && <span className={estilos.secundario}>{LINEA_SIMULADA}</span>}
        </li>
      ))}
    </ul>
  )
}

export default LineasLiquidacion
