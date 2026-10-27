import estilos from './Alerta.module.css'

function Alerta({ tipo = 'info', children }) {
  return (
    <div role={tipo === 'error' ? 'alert' : 'status'} className={`${estilos.alerta} ${estilos[tipo]}`}>
      {children}
    </div>
  )
}

export default Alerta
