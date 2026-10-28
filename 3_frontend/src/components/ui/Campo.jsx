import { useId } from 'react'
import estilos from './Campo.module.css'

function Campo({ etiqueta, nombre, tipo = 'text', valor, onChange, error, ayuda, opciones = [] }) {
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

  const claseControl = error ? `${estilos.control} ${estilos.conError}` : estilos.control

  if (tipo === 'select') {
    return (
      <div className={estilos.campo}>
        <label htmlFor={id} className={estilos.etiqueta}>{etiqueta}</label>
        <select
          {...comunes}
          className={claseControl}
          value={valor}
          onChange={(evento) => onChange(evento.target.value)}
        >
          <option value="">Elige una opción</option>
          {opciones.map((opcion) => (
            <option key={opcion.valor} value={opcion.valor}>
              {opcion.etiqueta}
            </option>
          ))}
        </select>
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
        className={claseControl}
        value={valor}
        onChange={(evento) => onChange(evento.target.value)}
      />
      {mensajes}
    </div>
  )
}

export default Campo
