import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { listarMisEspacios } from '../../api/espacios.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { formatoPrecio, nombreTipo } from './textosEspacios.js'
import estilos from './MisEspacios.module.css'

function MisEspacios() {
  const { sesion } = useSesion()
  const navegar = useNavigate()
  const esArrendador = sesion?.rol === 'ARRENDADOR'
  const [espacios, setEspacios] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!esArrendador) return undefined
    let activo = true
    listarMisEspacios()
      .then((datos) => {
        if (activo) setEspacios(datos)
      })
      .catch((falla) => {
        if (activo) setError(falla.mensaje)
      })
      .finally(() => {
        if (activo) setCargando(false)
      })
    return () => {
      activo = false
    }
  }, [esArrendador])

  if (!esArrendador) return <Navigate to="/panel" replace />

  return (
    <section className={estilos.pantalla}>
      <div className={estilos.encabezado}>
        <h2>Mis espacios</h2>
        <Boton onClick={() => navegar('/panel/espacios/nuevo')}>Crear un espacio</Boton>
      </div>

      {sesion.aviso && (
        <Alerta tipo="atencion">
          {sesion.aviso} <Link to="/panel/identidad">Verificar mi identidad</Link>
        </Alerta>
      )}
      {error && <Alerta tipo="error">{error}</Alerta>}
      {cargando && <p className={estilos.secundario}>Cargando…</p>}

      {!cargando && !error && espacios.length === 0 && (
        <p className={estilos.secundario}>Aún no tienes espacios. Crea el primero y guárdalo como borrador.</p>
      )}

      {espacios.length > 0 && (
        <ul className={estilos.lista}>
          {espacios.map((espacio) => (
            <li key={espacio.id}>
              <Tarjeta
                titulo={espacio.nombre}
                acciones={<Link to={`/panel/espacios/${espacio.id}`}>Editar</Link>}
              >
                <p className={estilos.dato}>
                  {nombreTipo(espacio.tipo)} · {espacio.zonaNombre}
                </p>
                <p className={estilos.precio}>{formatoPrecio(espacio.precioHora)} por hora</p>
                <div>
                  <EtiquetaEstado estado={espacio.estado} />
                </div>
              </Tarjeta>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default MisEspacios
