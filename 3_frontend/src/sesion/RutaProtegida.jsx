import { Navigate } from 'react-router-dom'
import useSesion from './useSesion.js'

function RutaProtegida({ children }) {
  const { sesion, cargando } = useSesion()
  if (cargando) return null
  if (!sesion) return <Navigate to="/iniciar-sesion" replace />
  return children
}

export default RutaProtegida
