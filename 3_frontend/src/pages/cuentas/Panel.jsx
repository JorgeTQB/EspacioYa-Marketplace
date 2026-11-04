import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { obtenerSesionActual } from '../../api/cuentas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { ENLACE_CUENTA_BANCARIA } from '../pagos/textosPagos.js'
import { ENLACE_SEGURIDAD } from './textosCuentas.js'
import estilos from './Panel.module.css'

const TEXTOS_POR_ROL = {
  USUARIO: { titulo: 'Mi cuenta', descripcion: 'Aquí verás tus reservas.' },
  ARRENDADOR: { titulo: 'Panel de arrendador', descripcion: 'Aquí verás tus espacios.' },
  ADMIN: { titulo: 'Panel de administración', descripcion: 'Revisa identidades, gestiona cuentas y atiende reembolsos.' },
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
      {/* HU-14: para USUARIO la descripción pasa a ser el enlace a "Mis reservas". */}
      {sesion.rol === 'USUARIO' ? (
        <p>
          <Link to="/mis-reservas">Mis reservas</Link>
        </p>
      ) : (
        <p className={estilos.descripcion}>{textos.descripcion}</p>
      )}
      {sesion.rol === 'ARRENDADOR' && (
        <>
          <p>
            <Link to="/panel/espacios">Mis espacios</Link>
          </p>
          <p>
            <Link to="/panel/reservas">Reservas de mis espacios</Link>
          </p>
          <p>
            <Link to="/panel/identidad">Verificar mi identidad</Link>
          </p>
          <p>
            <Link to="/panel/cuenta-bancaria">{ENLACE_CUENTA_BANCARIA}</Link>
          </p>
        </>
      )}
      {sesion.rol === 'ADMIN' && (
        <>
          <p>
            <Link to="/admin/identidades">Revisar identidades</Link>
          </p>
          <p>
            <Link to="/admin/cuentas">Buscar cuentas</Link>
          </p>
          <p>
            <Link to="/admin/reembolsos">Reembolsos por atender</Link>
          </p>
        </>
      )}
      <p>
        <Link to="/panel/seguridad">{ENLACE_SEGURIDAD}</Link>
      </p>
    </section>
  )
}

export default Panel
