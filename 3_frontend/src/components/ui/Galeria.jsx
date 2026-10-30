import { useState } from 'react'
import Imagen from './Imagen.jsx'
import estilos from './Galeria.module.css'

// HU-11: foto elegida en grande y, si hay más de una, miniaturas para cambiarla. No llama a la API: recibe las URL
// ya completas, en el orden del servidor (la primera es la principal).
function Galeria({ fotos = [], alt }) {
  const [elegida, setElegida] = useState(0)

  if (fotos.length === 0) {
    return <Imagen src={null} alt={alt} />
  }

  const total = fotos.length
  const actual = Math.min(elegida, total - 1)

  return (
    <div className={estilos.galeria}>
      <Imagen src={fotos[actual]} alt={`${alt}, foto ${actual + 1} de ${total}`} />
      {total > 1 && (
        <ul className={estilos.miniaturas}>
          {fotos.map((foto, indice) => (
            <li key={foto}>
              <button
                type="button"
                aria-pressed={indice === actual}
                aria-label={`Ver foto ${indice + 1} de ${total}`}
                className={indice === actual ? `${estilos.miniatura} ${estilos.elegida}` : estilos.miniatura}
                onClick={() => setElegida(indice)}
              >
                <Imagen src={foto} alt="" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default Galeria
