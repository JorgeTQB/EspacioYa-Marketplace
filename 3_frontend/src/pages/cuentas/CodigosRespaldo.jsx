import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import { AVISO_CODIGOS_RESPALDO } from './textosCuentas.js'
import estilos from './CodigosRespaldo.module.css'

// HU-17 CA3: los 5 códigos de respaldo se muestran una sola vez; la API no los vuelve a entregar.
function CodigosRespaldo({ codigos, textoBoton, alContinuar }) {
  return (
    <div className={estilos.caja}>
      <Alerta tipo="atencion">{AVISO_CODIGOS_RESPALDO}</Alerta>
      <ol className={estilos.lista}>
        {codigos.map((codigo) => (
          <li key={codigo} className={estilos.codigo}>
            {codigo}
          </li>
        ))}
      </ol>
      <Boton onClick={alContinuar}>{textoBoton}</Boton>
    </div>
  )
}

export default CodigosRespaldo
