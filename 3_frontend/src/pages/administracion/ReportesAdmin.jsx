import { Link, Navigate } from 'react-router-dom'
import { listarReportesAdmin } from '../../api/administracion.js'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import SeccionLiquidaciones from '../pagos/SeccionLiquidaciones.jsx'
import { MOTIVOS_REPORTE } from '../busqueda/textosFicha.js'
import {
  ENLACE_REVISAR,
  SIN_POR_REVISAR,
  SIN_REPORTES_RESUELTOS,
  TITULO_POR_REVISAR,
  TITULO_REPORTES_ADMIN,
  TITULO_RESUELTOS,
  motivoDe,
  reportadoEl,
  resueltoEl,
  tituloFilaReporte,
} from './textosAdministracion.js'
import estilos from './ReportesAdmin.module.css'

// SeccionLiquidaciones lista lo que reciba en "liquidaciones": se adapta la página de reportes a esa forma.
function cargarReportes({ grupo, pagina }) {
  return listarReportesAdmin({ grupo, pagina }).then((datos) => ({ ...datos, liquidaciones: datos.reportes }))
}

function tarjeta(reporte) {
  return (
    <Tarjeta
      titulo={tituloFilaReporte(reporte.tipoObjetivo, reporte.espacioNombre)}
      acciones={<Link to={`/admin/reportes/${reporte.id}`}>{ENLACE_REVISAR}</Link>}
    >
      <div>
        <EtiquetaEstado estado={reporte.estado} />
      </div>
      <p className={estilos.dato}>{motivoDe(MOTIVOS_REPORTE[reporte.motivo] ?? reporte.motivo)}</p>
      <p className={estilos.dato}>{reportadoEl(reporte.creadoEn)}</p>
      {reporte.resueltoEn && <p className={estilos.dato}>{resueltoEl(reporte.resueltoEn)}</p>}
    </Tarjeta>
  )
}

// HU-27 CA2: la cola de moderación del admin. El orden (por revisar del más antiguo, resueltos del más reciente) y las
// páginas los decide el servidor. Sin botón primario en la lista.
function ReportesAdmin() {
  const { sesion } = useSesion()
  if (sesion?.rol !== 'ADMIN') return <Navigate to="/panel" replace />

  return (
    <section className={estilos.pantalla}>
      <h2>{TITULO_REPORTES_ADMIN}</h2>
      <SeccionLiquidaciones
        titulo={TITULO_POR_REVISAR}
        grupo="pendientes"
        cargar={cargarReportes}
        textoVacio={SIN_POR_REVISAR}
        renderLiquidacion={tarjeta}
      />
      <SeccionLiquidaciones
        titulo={TITULO_RESUELTOS}
        grupo="resueltos"
        cargar={cargarReportes}
        textoVacio={SIN_REPORTES_RESUELTOS}
        renderLiquidacion={tarjeta}
      />
    </section>
  )
}

export default ReportesAdmin
