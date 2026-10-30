import estilos from './EtiquetaEstado.module.css'

// Texto y tipo de color por valor del enum de la API (docs/frontend.md, "Color de cada estado").
// Los estados de otras entidades los agrega la historia que los muestra.
const ESTADOS = {
  VERIFICADA: { texto: 'Identidad verificada', tipo: 'exito' },
  EN_REVISION: { texto: 'Identidad en revisión', tipo: 'atencion' },
  RECHAZADA: { texto: 'Identidad rechazada', tipo: 'error' },
  NO_INICIADA: { texto: 'Identidad sin verificar', tipo: 'neutro' },
  // Espacio (HU-07)
  PUBLICADO: { texto: 'Publicado', tipo: 'exito' },
  PAUSADO: { texto: 'Pausado', tipo: 'atencion' },
  OCULTO: { texto: 'Oculto', tipo: 'error' },
  BORRADOR: { texto: 'Borrador', tipo: 'neutro' },
  BAJA: { texto: 'De baja', tipo: 'neutro' },
  // Cuenta (HU-09)
  ACTIVA: { texto: 'Activa', tipo: 'exito' },
  SUSPENDIDA: { texto: 'Suspendida', tipo: 'error' },
  ELIMINADA: { texto: 'Eliminada', tipo: 'neutro' },
  // Reserva (HU-12)
  PENDIENTE_PAGO: { texto: 'Pendiente de pago', tipo: 'atencion' },
  CONFIRMADA: { texto: 'Confirmada', tipo: 'exito' },
  EXPIRADA: { texto: 'Expirada', tipo: 'neutro' },
  CANCELADA: { texto: 'Cancelada', tipo: 'neutro' },
}

function EtiquetaEstado({ estado }) {
  const { texto, tipo } = ESTADOS[estado] ?? { texto: estado, tipo: 'neutro' }
  return <span className={`${estilos.etiqueta} ${estilos[tipo]}`}>{texto}</span>
}

export default EtiquetaEstado
