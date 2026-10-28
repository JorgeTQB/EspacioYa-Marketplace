import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { alPerderSesion } from '../api/cliente.js'
import { obtenerSesionActual } from '../api/cuentas.js'
import { leerToken } from '../api/tokenSesion.js'
import ContextoSesion from './ContextoSesion.js'

// El rol solo elige qué mostrar; el backend valida la sesión y el rol en cada solicitud.
function ProveedorSesion({ children }) {
  const navegar = useNavigate()
  const [sesion, setSesion] = useState(null)
  const [cargando, setCargando] = useState(() => Boolean(leerToken()))

  useEffect(() => {
    if (!leerToken()) return undefined
    let activo = true
    obtenerSesionActual()
      .then((datos) => {
        if (activo) setSesion({ rol: datos.rol, aviso: datos.aviso })
      })
      .catch(() => {
        if (activo) setSesion(null)
      })
      .finally(() => {
        if (activo) setCargando(false)
      })
    return () => {
      activo = false
    }
  }, [])

  useEffect(
    () =>
      alPerderSesion((mensaje) => {
        setSesion(null)
        navegar('/iniciar-sesion', { state: { mensaje, tipo: 'info' } })
      }),
    [navegar],
  )

  const entrar = useCallback(({ rol, aviso }) => setSesion({ rol, aviso }), [])
  const salir = useCallback(() => setSesion(null), [])
  const valor = useMemo(() => ({ sesion, cargando, entrar, salir }), [sesion, cargando, entrar, salir])

  return <ContextoSesion.Provider value={valor}>{children}</ContextoSesion.Provider>
}

export default ProveedorSesion
