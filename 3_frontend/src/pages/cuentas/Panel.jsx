import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { obtenerSesionActual } from '../../api/cuentas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import useSesion from '../../sesion/useSesion.js'
import estilos from './Panel.module.css'

const TEXTOS_POR_ROL = {
  USUARIO: { titulo: 'Mi cuenta', descripcion: 'Aquí verás tus reservas.' },
  ARRENDADOR: { titulo: 'Panel de arrendador', descripcion: 'Aquí verás tus espacios.' },
}

function Panel() {
  const { sesion, entrar } = useSesion()

  useEffect(() => {
    let activo = true
    obtenerSesionActual()
      .then((datos) => {
        if (activo) entrar({ rol: datos.rol, aviso: datos.aviso })
      })
      .catch(() => {
        // Un 401 ya lo maneja el proveedor de sesión; ante un error de red se sigue mostrando lo que había.
      })
    return () => {
      activo = false
    }
  }, [entrar])

  const textos = TEXTOS_POR_ROL[sesion.rol] ?? TEXTOS_POR_ROL.USUARIO

  return (
    <section className={estilos.pantalla}>
      <h2>{textos.titulo}</h2>
      {sesion.aviso && <Alerta tipo="atencion">{sesion.aviso}</Alerta>}
      <p className={estilos.descripcion}>{textos.descripcion}</p>
      {sesion.rol === 'ARRENDADOR' && (
        <>
          <p>
            <Link to="/panel/espacios">Mis espacios</Link>
          </p>
          <p>
            <Link to="/panel/identidad">Verificar mi identidad</Link>
          </p>
        </>
      )}
    </section>
  )
}

export default Panel
