import estilos from './SelectorBloques.module.css'

function dosDigitos(hora) {
  return String(hora).padStart(2, '0')
}

function rango(bloque) {
  return `${dosDigitos(bloque.horaInicio)}:00–${dosDigitos(bloque.horaFin)}:00`
}

// Sin onCambiar es solo lectura: los bloques no son botones (HU-08, vista del arrendador).
function SelectorBloques({ bloques, seleccionados = [], onCambiar }) {
  if (!bloques || bloques.length === 0) return null

  function alternar(hora) {
    const nuevos = seleccionados.includes(hora)
      ? seleccionados.filter((h) => h !== hora)
      : [...seleccionados, hora].sort((a, b) => a - b)
    onCambiar(nuevos)
  }

  return (
    <ul className={estilos.bloques}>
      {bloques.map((bloque) => {
        const seleccionado = seleccionados.includes(bloque.horaInicio)
        if (!bloque.libre) {
          return (
            <li key={bloque.horaInicio} className={`${estilos.bloque} ${estilos.ocupado}`}>
              <span className={estilos.tachado}>{rango(bloque)}</span>
              {bloque.detalle && <span className={estilos.detalle}>{bloque.detalle}</span>}
            </li>
          )
        }
        if (!onCambiar) {
          return (
            <li key={bloque.horaInicio} className={`${estilos.bloque} ${estilos.libre}`}>
              {rango(bloque)}
            </li>
          )
        }
        return (
          <li key={bloque.horaInicio}>
            <button
              type="button"
              aria-pressed={seleccionado}
              className={`${estilos.bloque} ${seleccionado ? estilos.seleccionado : estilos.libre}`}
              onClick={() => alternar(bloque.horaInicio)}
            >
              {rango(bloque)}
            </button>
          </li>
        )
      })}
    </ul>
  )
}

export default SelectorBloques
