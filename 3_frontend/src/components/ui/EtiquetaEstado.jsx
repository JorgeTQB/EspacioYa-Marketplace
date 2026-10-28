import estilos from './EtiquetaEstado.module.css'

// Texto y tipo de color por valor del enum de la API (docs/frontend.md, "Color de cada estado").
// Los estados de otras entidades los agrega la historia que los muestra.
const ESTADOS = {
  VERIFICADA: { texto: 'Identidad verificada', tipo: 'exito' },
  EN_REVISION: { texto: 'Identidad en revisión', tipo: 'atencion' },
  RECHAZADA: { texto: 'Identidad rechazada', tipo: 'error' },
  NO_INICIADA: { texto: 'Identidad sin verificar', tipo: 'neutro' },
}

function EtiquetaEstado({ estado }) {
  const { texto, tipo } = ESTADOS[estado] ?? { texto: estado, tipo: 'neutro' }
  return <span className={`${estilos.etiqueta} ${estilos[tipo]}`}>{texto}</span>
}

export default EtiquetaEstado
