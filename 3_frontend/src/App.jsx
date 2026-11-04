import { useState } from 'react'
import { Link, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { NO_AUTENTICADO } from './api/cliente.js'
import { cerrarSesion } from './api/cuentas.js'
import { borrarToken } from './api/tokenSesion.js'
import Alerta from './components/ui/Alerta.jsx'
import Boton from './components/ui/Boton.jsx'
import BuscarCuentas from './pages/administracion/BuscarCuentas.jsx'
import ColaIdentidades from './pages/administracion/ColaIdentidades.jsx'
import DetalleCuenta from './pages/administracion/DetalleCuenta.jsx'
import RevisarIdentidad from './pages/administracion/RevisarIdentidad.jsx'
import BuscarEspacios from './pages/busqueda/BuscarEspacios.jsx'
import FichaEspacio from './pages/busqueda/FichaEspacio.jsx'
import Inicio from './pages/inicio/Inicio.jsx'
import IniciarSesion from './pages/cuentas/IniciarSesion.jsx'
import Panel from './pages/cuentas/Panel.jsx'
import Privacidad from './pages/cuentas/Privacidad.jsx'
import RecuperarContrasena from './pages/cuentas/RecuperarContrasena.jsx'
import Registro from './pages/cuentas/Registro.jsx'
import RegistroArrendador from './pages/cuentas/RegistroArrendador.jsx'
import RestablecerContrasena from './pages/cuentas/RestablecerContrasena.jsx'
import SeguridadCuenta from './pages/cuentas/SeguridadCuenta.jsx'
import VerificarIdentidad from './pages/cuentas/VerificarIdentidad.jsx'
import DisponibilidadEspacio from './pages/espacios/DisponibilidadEspacio.jsx'
import EditarEspacio from './pages/espacios/EditarEspacio.jsx'
import MisEspacios from './pages/espacios/MisEspacios.jsx'
import CuentaBancaria from './pages/pagos/CuentaBancaria.jsx'
import PagoSimulado from './pages/pagos/PagoSimulado.jsx'
import ReembolsosPorAtender from './pages/pagos/ReembolsosPorAtender.jsx'
import MisReservas from './pages/reservas/MisReservas.jsx'
import ReservasDeMisEspacios from './pages/reservas/ReservasDeMisEspacios.jsx'
import ResumenReserva from './pages/reservas/ResumenReserva.jsx'
import RutaProtegida from './sesion/RutaProtegida.jsx'
import useSesion from './sesion/useSesion.js'
import estilos from './App.module.css'

const MENSAJE_SESION_CERRADA = 'Cerraste sesión.'

function App() {
  const { sesion, salir } = useSesion()
  const navegar = useNavigate()
  const enInicio = useLocation().pathname === '/'
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
        <div className={estilos.barra}>
          <h1 className={estilos.marca}>
            <Link to="/" aria-label="EspacioYa, ir al inicio">
              <svg className={estilos.pin} viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 2a7.5 7.5 0 0 0-7.5 7.5c0 5.4 6.2 11.6 6.9 12.2a.9.9 0 0 0 1.2 0c.7-.6 6.9-6.8 6.9-12.2A7.5 7.5 0 0 0 12 2Z" />
                <circle cx="12" cy="9.5" r="3" className={estilos.pinCentro} />
              </svg>
              <span className={estilos.marcaEspacio}>Espacio</span>
              <span className={estilos.marcaYa}>Ya</span>
            </Link>
          </h1>
          <nav className={estilos.secciones} aria-label="Secciones">
            <Link to="/buscar">Explorar espacios</Link>
            <Link to={{ pathname: '/', hash: '#como-funciona' }}>Cómo funciona</Link>
          </nav>
          <nav className={estilos.cuenta} aria-label="Cuenta">
            <Link to="/buscar" className={estilos.lupa} aria-label="Buscar espacios">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="11" cy="11" r="7" />
                <path d="m20 20-4-4" />
              </svg>
            </Link>
            {sesion ? (
              <>
                <Link to="/panel" className={estilos.enlace}>
                  Mi panel
                </Link>
                <Boton variante="secundario" cargando={cerrando} onClick={alCerrarSesion}>
                  Cerrar sesión
                </Boton>
              </>
            ) : (
              <>
                <Link to="/iniciar-sesion" className={estilos.enlace}>
                  Iniciar sesión
                </Link>
                <Link to="/registro" className={estilos.registrate}>
                  Regístrate
                </Link>
              </>
            )}
          </nav>
        </div>
      </header>
      {errorCierre && (
        <div className={estilos.contenido}>
          <Alerta tipo="error">{errorCierre}</Alerta>
        </div>
      )}
      <main className={enInicio ? undefined : estilos.contenido}>
        <Routes>
          <Route path="/" element={<Inicio />} />
          {/* HU-10: catálogo público, con o sin sesión. */}
          <Route path="/buscar" element={<BuscarEspacios />} />
          {/* HU-11: ficha pública (?fecha=AAAA-MM-DD), con o sin sesión; HU-12 vuelve aquí tras el login. */}
          <Route path="/espacios/:id" element={<FichaEspacio />} />
          {/* HU-12: resumen de la reserva; la API solo se la entrega a su dueño. */}
          <Route
            path="/reservas/:id"
            element={
              <RutaProtegida>
                <ResumenReserva />
              </RutaProtegida>
            }
          />
          {/* HU-13 D9: solo con la pasarela simulada; si el backend no la habilita, no muestra datos ni botones. */}
          <Route
            path="/reservas/:id/pago-simulado"
            element={
              <RutaProtegida>
                <PagoSimulado />
              </RutaProtegida>
            }
          />
          {/* HU-14 CA1: la API solo responde al rol USUARIO y con sus propias reservas. */}
          <Route
            path="/mis-reservas"
            element={
              <RutaProtegida>
                <MisReservas />
              </RutaProtegida>
            }
          />
          <Route path="/registro" element={<Registro />} />
          <Route path="/registro-arrendador" element={<RegistroArrendador />} />
          <Route path="/privacidad" element={<Privacidad />} />
          <Route path="/iniciar-sesion" element={<IniciarSesion />} />
          <Route path="/recuperar-contrasena" element={<RecuperarContrasena />} />
          <Route path="/restablecer-contrasena" element={<RestablecerContrasena />} />
          <Route
            path="/panel"
            element={
              <RutaProtegida>
                <Panel />
              </RutaProtegida>
            }
          />
          {/* HU-17: verificación en dos pasos de la cuenta, para los tres roles; la API decide qué se puede hacer. */}
          <Route
            path="/panel/seguridad"
            element={
              <RutaProtegida>
                <SeguridadCuenta />
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
          {/* HU-18: cuenta bancaria del arrendador; la API valida rol, identidad verificada y contraseña. */}
          <Route
            path="/panel/cuenta-bancaria"
            element={
              <RutaProtegida>
                <CuentaBancaria />
              </RutaProtegida>
            }
          />
          <Route
            path="/panel/espacios"
            element={
              <RutaProtegida>
                <MisEspacios />
              </RutaProtegida>
            }
          />
          {/* La clave distinta hace que pasar de "nuevo" a ":id" monte la pantalla de cero. */}
          <Route
            path="/panel/espacios/nuevo"
            element={
              <RutaProtegida>
                <EditarEspacio key="nuevo" />
              </RutaProtegida>
            }
          />
          <Route
            path="/panel/espacios/:id"
            element={
              <RutaProtegida>
                <EditarEspacio />
              </RutaProtegida>
            }
          />
          <Route
            path="/panel/espacios/:id/disponibilidad"
            element={
              <RutaProtegida>
                <DisponibilidadEspacio />
              </RutaProtegida>
            }
          />
          {/* HU-14 CA2: la API solo responde al rol ARRENDADOR y con las confirmadas de sus espacios. */}
          <Route
            path="/panel/reservas"
            element={
              <RutaProtegida>
                <ReservasDeMisEspacios />
              </RutaProtegida>
            }
          />
          {/* Panel admin (HU-06): la API exige IP permitida y rol ADMIN; la pantalla solo elige qué mostrar. */}
          <Route
            path="/admin/identidades"
            element={
              <RutaProtegida>
                <ColaIdentidades />
              </RutaProtegida>
            }
          />
          <Route
            path="/admin/identidades/:id"
            element={
              <RutaProtegida>
                <RevisarIdentidad />
              </RutaProtegida>
            }
          />
          {/* HU-09: mismo panel admin; la API valida IP, rol y qué cuentas se pueden suspender o reactivar. */}
          <Route
            path="/admin/cuentas"
            element={
              <RutaProtegida>
                <BuscarCuentas />
              </RutaProtegida>
            }
          />
          <Route
            path="/admin/cuentas/:id"
            element={
              <RutaProtegida>
                <DetalleCuenta />
              </RutaProtegida>
            }
          />
          {/* HU-16: mismo panel admin; la API valida IP y rol, y decide qué reembolsos se pueden reintentar. */}
          <Route
            path="/admin/reembolsos"
            element={
              <RutaProtegida>
                <ReembolsosPorAtender />
              </RutaProtegida>
            }
          />
        </Routes>
      </main>
    </>
  )
}

export default App
