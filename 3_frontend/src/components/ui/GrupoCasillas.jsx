import { useId } from 'react'
import Campo from './Campo.jsx'
import estilos from './GrupoCasillas.module.css'

// Varias casillas de una lista cerrada con una leyenda común. onChange recibe los valores marcados en el orden de
// opciones, así el arreglo enviado no depende del orden en que se marcaron.
function GrupoCasillas({ leyenda, nombre, opciones, valores = [], onChange, error, ayuda }) {
  const id = useId()
  const idAyuda = `${id}-ayuda`
  const idError = `${id}-error`
  const descripcion = [error ? idError : null, ayuda && !error ? idAyuda : null].filter(Boolean).join(' ')

  function alternar(valor) {
    return (marcada) => {
      const siguientes = opciones
        .map((opcion) => opcion.valor)
        .filter((codigo) => (codigo === valor ? marcada : valores.includes(codigo)))
      onChange(siguientes)
    }
  }

  return (
    <fieldset
      className={estilos.grupo}
      aria-invalid={error ? true : undefined}
      aria-describedby={descripcion || undefined}
    >
      <legend className={estilos.leyenda}>{leyenda}</legend>
      <div className={estilos.opciones}>
        {opciones.map((opcion) => (
          <Campo
            key={opcion.valor}
            etiqueta={opcion.etiqueta}
            nombre={nombre}
            tipo="checkbox"
            valor={valores.includes(opcion.valor)}
            onChange={alternar(opcion.valor)}
          />
        ))}
      </div>
      {ayuda && !error && <p id={idAyuda} className={estilos.ayuda}>{ayuda}</p>}
      {error && <p id={idError} className={estilos.error}>{error}</p>}
    </fieldset>
  )
}

export default GrupoCasillas
