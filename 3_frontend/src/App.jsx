import { Link, Route, Routes } from 'react-router-dom'
import Privacidad from './pages/cuentas/Privacidad.jsx'
import Registro from './pages/cuentas/Registro.jsx'
import estilos from './App.module.css'

function App() {
  return (
    <>
      <header className={estilos.encabezado}>
        <h1 className={estilos.marca}>
          <Link to="/">EspacioYa</Link>
        </h1>
      </header>
      <main>
        <Routes>
          <Route
            path="/"
            element={
              <p className={estilos.inicio}>
                <Link to="/registro">Crea tu cuenta</Link>
              </p>
            }
          />
          <Route path="/registro" element={<Registro />} />
          <Route path="/privacidad" element={<Privacidad />} />
        </Routes>
      </main>
    </>
  )
}

export default App
