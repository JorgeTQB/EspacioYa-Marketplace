import { useContext } from 'react'
import ContextoSesion from './ContextoSesion.js'

function useSesion() {
  return useContext(ContextoSesion)
}

export default useSesion
