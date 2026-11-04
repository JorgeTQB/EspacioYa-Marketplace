import estilos from './Tarjeta.module.css'

function Tarjeta({ titulo, children, acciones }) {
  return (
    <article className={estilos.tarjeta}>
      {titulo && <h3 className={estilos.titulo}>{titulo}</h3>}
      <div className={estilos.contenido}>{children}</div>
      {acciones && <div className={estilos.acciones}>{acciones}</div>}
    </article>
  )
}

export default Tarjeta
