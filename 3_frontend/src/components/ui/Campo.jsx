import { useId } from 'react'
import estilos from './Campo.module.css'

function Campo({ etiqueta, nombre, tipo = 'text', valor, onChange, error, ayuda }) {
  const id = useId()
  const idAyuda = `${id}-ayuda`
  const idError = `${id}-error`
  const descripcion = [error ? idError : null, ayuda && !error ? idAyuda : null].filter(Boolean).join(' ')
  const comunes = {
    id,
    name: nombre,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': descripcion || undefined,
  }

  const mensajes = (
    <>
      {ayuda && !error && <p id={idAyuda} className={estilos.ayuda}>{ayuda}</p>}
      {error && <p id={idError} className={estilos.error}>{error}</p>}
    </>
  )

  if (tipo === 'checkbox') {
    return (
      <div className={estilos.campo}>
        <div className={estilos.casilla}>
          <input
            {...comunes}
            type="checkbox"
            className={error ? `${estilos.marca} ${estilos.conError}` : estilos.marca}
            checked={Boolean(valor)}
            onChange={(evento) => onChange(evento.target.checked)}
          />
          <label htmlFor={id} className={estilos.etiquetaCasilla}>{etiqueta}</label>
        </div>
        {mensajes}
      </div>
    )
  }

  return (
    <div className={estilos.campo}>
      <label htmlFor={id} className={estilos.etiqueta}>{etiqueta}</label>
      <input
        {...comunes}
        type={tipo}
        className={error ? `${estilos.control} ${estilos.conError}` : estilos.control}
        value={valor}
        onChange={(evento) => onChange(evento.target.value)}
      />
      {mensajes}
    </div>
  )
}

export default Campo
