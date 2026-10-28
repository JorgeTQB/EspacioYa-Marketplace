import { useState } from 'react'
import { Link, Route, Routes, useNavigate } from 'react-router-dom'
import { NO_AUTENTICADO } from './api/cliente.js'
import { cerrarSesion } from './api/cuentas.js'
import { borrarToken } from './api/tokenSesion.js'
import Alerta from './components/ui/Alerta.jsx'
import Boton from './components/ui/Boton.jsx'
import IniciarSesion from './pages/cuentas/IniciarSesion.jsx'
import Panel from './pages/cuentas/Panel.jsx'
import Privacidad from './pages/cuentas/Privacidad.jsx'
import Registro from './pages/cuentas/Registro.jsx'
import RegistroArrendador from './pages/cuentas/RegistroArrendador.jsx'
import VerificarIdentidad from './pages/cuentas/VerificarIdentidad.jsx'
import RutaProtegida from './sesion/RutaProtegida.jsx'
import useSesion from './sesion/useSesion.js'
import estilos from './App.module.css'

const MENSAJE_SESION_CERRADA = 'Cerraste sesión.'

function App() {
  const { sesion, salir } = useSesion()
  const navegar = useNavigate()
  const [cerrando, setCerrando] = useState(false)
  const [errorCierre, setErrorCierre] = useState(null)

  function terminarSesion() {
    borrarToken()
    salir()
    navegar('/iniciar-sesion', { state: { mensaje: MENSAJE_SESION_CERRADA, tipo: 'exito' } })
  }

  async function alCerrarSesion() {
    setCerrando(true)
    setErrorCierre(null)
    try {
      await cerrarSesion()
      terminarSesion()
    } catch (error) {
      // Con 401 la sesión ya no valía en el backend: el resultado para el usuario es el mismo.
      if (error.estado === NO_AUTENTICADO) {
        terminarSesion()
      } else {
        setErrorCierre(error.mensaje)
      }
    } finally {
      setCerrando(false)
    }
  }

  return (
    <>
      <header className={estilos.encabezado}>
        <h1 className={estilos.marca}>
          <Link to="/">EspacioYa</Link>
        </h1>
        <nav className={estilos.navegacion}>
          {sesion ? (
            <>
              <Link to="/panel">Mi panel</Link>
              <Boton variante="secundario" cargando={cerrando} onClick={alCerrarSesion}>
                Cerrar sesión
              </Boton>
            </>
          ) : (
            <Link to="/iniciar-sesion">Iniciar sesión</Link>
          )}
        </nav>
      </header>
      {errorCierre && <Alerta tipo="error">{errorCierre}</Alerta>}
      <main>
        <Routes>
          <Route
            path="/"
            element={
              <>
                <p className={estilos.inicio}>
                  <Link to="/registro">Crea tu cuenta</Link>
                </p>
                <p>
                  <Link to="/registro-arrendador">¿Tienes un espacio? Regístrate como arrendador</Link>
                </p>
                <p>
                  <Link to="/iniciar-sesion">¿Ya tienes cuenta? Inicia sesión</Link>
                </p>
              </>
            }
          />
          <Route path="/registro" element={<Registro />} />
          <Route path="/registro-arrendador" element={<RegistroArrendador />} />
          <Route path="/privacidad" element={<Privacidad />} />
          <Route path="/iniciar-sesion" element={<IniciarSesion />} />
          <Route
            path="/panel"
            element={
              <RutaProtegida>
                <Panel />
              </RutaProtegida>
            }
          />
          <Route
            path="/panel/identidad"
            element={
              <RutaProtegida>
                <VerificarIdentidad />
              </RutaProtegida>
            }
          />
        </Routes>
      </main>
    </>
  )
}

export default App
