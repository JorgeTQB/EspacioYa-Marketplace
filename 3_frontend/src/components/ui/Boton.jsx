import estilos from './Boton.module.css'

function Boton({
  variante = 'primario',
  cargando = false,
  deshabilitado = false,
  onClick,
  tipo = 'button',
  children,
}) {
  return (
    <button
      type={tipo}
      className={`${estilos.boton} ${estilos[variante]}`}
      disabled={deshabilitado || cargando}
      aria-busy={cargando || undefined}
      onClick={onClick}
    >
      {cargando ? 'Enviando…' : children}
    </button>
  )
}

export default Boton
