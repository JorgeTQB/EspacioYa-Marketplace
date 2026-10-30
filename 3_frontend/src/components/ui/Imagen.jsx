import estilos from './Imagen.module.css'

// HU-10: foto pública con proporción fija. Sin src (p. ej. un espacio sin foto principal) muestra "Sin foto".
function Imagen({ src, alt }) {
  if (!src) {
    return (
      <div className={estilos.vacia} role="img" aria-label={alt}>
        <span className={estilos.texto}>Sin foto</span>
      </div>
    )
  }
  return <img className={estilos.imagen} src={src} alt={alt} loading="lazy" />
}

export default Imagen
