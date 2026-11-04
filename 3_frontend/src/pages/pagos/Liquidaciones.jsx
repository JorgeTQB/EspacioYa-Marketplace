import { useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { generarLiquidaciones, listarLiquidaciones } from '../../api/pagos.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Modal from '../../components/ui/Modal.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { fechaHora } from '../administracion/textosAdministracion.js'
import { formatoPrecio } from '../espacios/textosEspacios.js'
import { formatoFecha } from '../reservas/textosReservas.js'
import SeccionLiquidaciones from './SeccionLiquidaciones.jsx'
import {
  AVISOS_ARRENDADOR,
  AVISO_LIQUIDACION_SIMULADA,
  BOTON_CONFIRMAR_GENERAR,
  BOTON_GENERAR_LIQUIDACION,
  DESCRIPCION_LIQUIDACIONES,
  ENLACE_VER_DETALLE,
  ETIQUETA_BRUTO,
  ETIQUETA_COMISION,
  ETIQUETA_NETO,
  SECCION_PAGADAS,
  SECCION_POR_PAGAR,
  SIN_PAGADAS,
  SIN_POR_PAGAR,
  SIN_RESERVAS_POR_LIQUIDAR,
  TITULO_LIQUIDACIONES,
  TITULO_MODAL_GENERAR,
  cantidadReservas,
  liquidacionDel,
  liquidacionesGeneradas,
  pagadaConOperacion,
} from './textosPagos.js'
import estilos from './Liquidaciones.module.css'

function cargarLiquidaciones({ grupo, pagina }) {
  return listarLiquidaciones(grupo, pagina)
}

function TarjetaLiquidacion({ liquidacion }) {
  return (
    <Tarjeta
      titulo={liquidacion.arrendadorNombre}
      acciones={<Link to={`/admin/liquidaciones/${liquidacion.id}`}>{ENLACE_VER_DETALLE}</Link>}
    >
      <div>
        <EtiquetaEstado estado={liquidacion.estado} />
      </div>
      <p className={estilos.dato}>{liquidacionDel(formatoFecha(liquidacion.periodo))}</p>
      <p className={estilos.dato}>
        {ETIQUETA_BRUTO}: <strong className={estilos.monto}>{formatoPrecio(liquidacion.montoBruto)}</strong>
      </p>
      <p className={estilos.dato}>
        {ETIQUETA_COMISION}: <strong className={estilos.monto}>{formatoPrecio(liquidacion.comision)}</strong>
      </p>
      <p className={estilos.dato}>
        {ETIQUETA_NETO}: <strong className={estilos.monto}>{formatoPrecio(liquidacion.montoNeto)}</strong>
      </p>
      <p className={estilos.dato}>{cantidadReservas(liquidacion.reservas)}</p>
      {liquidacion.simulada && <p className={estilos.dato}>{AVISO_LIQUIDACION_SIMULADA}</p>}
      {(liquidacion.avisos ?? []).map((aviso) => (
        <p key={aviso} className={estilos.dato}>
          {AVISOS_ARRENDADOR[aviso] ?? aviso}
        </p>
      ))}
      {liquidacion.estado === 'PAGADA' && (
        <p className={estilos.dato}>
          {pagadaConOperacion(fechaHora(liquidacion.pagadaEn), liquidacion.numeroOperacion)}
        </p>
      )}
    </Tarjeta>
  )
}

function renderLiquidacion(liquidacion) {
  return <TarjetaLiquidacion liquidacion={liquidacion} />
}

// HU-19 CA1 y CA3: el admin genera la liquidación con un botón y ve lo que hay por pagar y lo ya pagado. Qué se
// liquida, los montos y los estados los decide el servidor; la pantalla no calcula nada.
function Liquidaciones() {
  const { sesion } = useSesion()
  const esAdmin = sesion?.rol === 'ADMIN'
  const [confirmando, setConfirmando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState(null)
  const [recarga, setRecarga] = useState(0)

  if (!esAdmin) return <Navigate to="/panel" replace />

  function cerrarModal() {
    if (!enviando) setConfirmando(false)
  }

  function generar() {
    setEnviando(true)
    setResultado(null)
    generarLiquidaciones()
      .then((respuesta) => {
        const cantidad = respuesta.liquidaciones.length
        setResultado(
          cantidad > 0
            ? { tipo: 'exito', texto: liquidacionesGeneradas(cantidad) }
            : { tipo: 'info', texto: SIN_RESERVAS_POR_LIQUIDAR },
        )
      })
      .catch((falla) => {
        setResultado({ tipo: 'error', texto: falla.mensaje })
      })
      .finally(() => {
        setEnviando(false)
        setConfirmando(false)
        setRecarga((n) => n + 1)
      })
  }

  return (
    <section className={estilos.pantalla}>
      <h2>{TITULO_LIQUIDACIONES}</h2>
      <p className={estilos.secundario}>{DESCRIPCION_LIQUIDACIONES}</p>
      <div>
        <Boton variante="primario" cargando={enviando} onClick={() => setConfirmando(true)}>
          {BOTON_GENERAR_LIQUIDACION}
        </Boton>
      </div>
      {resultado && <Alerta tipo={resultado.tipo}>{resultado.texto}</Alerta>}

      <SeccionLiquidaciones
        key={`por-pagar-${recarga}`}
        titulo={SECCION_POR_PAGAR}
        grupo="por-pagar"
        cargar={cargarLiquidaciones}
        textoVacio={SIN_POR_PAGAR}
        renderLiquidacion={renderLiquidacion}
      />
      <SeccionLiquidaciones
        key={`pagadas-${recarga}`}
        titulo={SECCION_PAGADAS}
        grupo="pagadas"
        cargar={cargarLiquidaciones}
        textoVacio={SIN_PAGADAS}
        renderLiquidacion={renderLiquidacion}
      />

      <Modal
        abierto={confirmando}
        titulo={TITULO_MODAL_GENERAR}
        textoConfirmar={BOTON_CONFIRMAR_GENERAR}
        variante="primario"
        cargando={enviando}
        onConfirmar={generar}
        onCerrar={cerrarModal}
      >
        <p>{DESCRIPCION_LIQUIDACIONES}</p>
      </Modal>
    </section>
  )
}

export default Liquidaciones
