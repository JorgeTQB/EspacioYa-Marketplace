import { Link, Navigate } from 'react-router-dom'
import { listarReclamosAdmin } from '../../api/administracion.js'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import SeccionLiquidaciones from '../pagos/SeccionLiquidaciones.jsx'
import { formatoFecha } from '../reservas/textosReservas.js'
import {
  ENLACE_VER_RECLAMO,
  MOTIVOS_RECLAMO,
  SIN_ABIERTOS,
  SIN_RESUELTOS,
  TITULO_ABIERTOS,
  TITULO_RECLAMOS_ADMIN,
  TITULO_RESUELTOS,
  fechaHora,
  tituloReserva,
} from './textosAdministracion.js'
import estilos from './ReclamosAdmin.module.css'

// SeccionLiquidaciones lista lo que reciba en "liquidaciones": se adapta la página de reclamos a esa forma.
function cargarReclamos({ grupo, pagina }) {
  return listarReclamosAdmin({ grupo, pagina }).then((datos) => ({ ...datos, liquidaciones: datos.reclamos }))
}

function tarjeta(reclamo) {
  return (
    <Tarjeta
      titulo={reclamo.espacioNombre}
      acciones={<Link to={`/admin/reclamos/${reclamo.id}`}>{ENLACE_VER_RECLAMO}</Link>}
    >
      <p className={estilos.dato}>{tituloReserva(reclamo.reservaId)}</p>
      <div>
        <EtiquetaEstado estado={reclamo.estado} />
      </div>
      <p className={estilos.dato}>Motivo: {MOTIVOS_RECLAMO[reclamo.motivo] ?? reclamo.motivo}</p>
      <p className={estilos.dato}>Reserva del {formatoFecha(reclamo.fecha)}</p>
      <p className={estilos.dato}>Abierto el {fechaHora(reclamo.abiertoEn)}</p>
      {reclamo.resueltoEn && <p className={estilos.dato}>Resuelto el {fechaHora(reclamo.resueltoEn)}</p>}
    </Tarjeta>
  )
}

// HU-26 CA2, CA3: la cola de reclamos del admin. El orden (abiertos del más antiguo, resueltos del más reciente) y
// las páginas los decide el servidor. Sin botón primario en la lista.
function ReclamosAdmin() {
  const { sesion } = useSesion()
  if (sesion?.rol !== 'ADMIN') return <Navigate to="/panel" replace />

  return (
    <section className={estilos.pantalla}>
      <h2>{TITULO_RECLAMOS_ADMIN}</h2>
      <SeccionLiquidaciones
        titulo={TITULO_ABIERTOS}
        grupo="abiertos"
        cargar={cargarReclamos}
        textoVacio={SIN_ABIERTOS}
        renderLiquidacion={tarjeta}
      />
      <SeccionLiquidaciones
        titulo={TITULO_RESUELTOS}
        grupo="resueltos"
        cargar={cargarReclamos}
        textoVacio={SIN_RESUELTOS}
        renderLiquidacion={tarjeta}
      />
    </section>
  )
}

export default ReclamosAdmin
