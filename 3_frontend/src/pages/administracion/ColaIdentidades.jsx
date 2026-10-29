import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { listarIdentidadesEnRevision } from '../../api/administracion.js'
import Alerta from '../../components/ui/Alerta.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { fechaHora } from './textosAdministracion.js'
import estilos from './ColaIdentidades.module.css'

// CA1 de HU-06: los arrendadores en revisión en el orden que devuelve la API (del envío más antiguo al más reciente).
function ColaIdentidades() {
  const { sesion } = useSesion()
  const esAdmin = sesion?.rol === 'ADMIN'
  const exito = useLocation().state?.mensaje
  const [arrendadores, setArrendadores] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!esAdmin) return undefined
    let activo = true
    listarIdentidadesEnRevision()
      .then((datos) => {
        if (activo) setArrendadores(datos)
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
  }, [esAdmin])

  if (!esAdmin) return <Navigate to="/panel" replace />

  return (
    <section className={estilos.pantalla}>
      <h2>Identidades en revisión</h2>

      {exito && <Alerta tipo="exito">{exito}</Alerta>}
      {error && <Alerta tipo="error">{error}</Alerta>}
      {cargando && <p className={estilos.secundario}>Cargando…</p>}

      {!cargando && !error && arrendadores.length === 0 && (
        <p className={estilos.secundario}>No hay arrendadores esperando la revisión de su identidad.</p>
      )}

      {arrendadores.length > 0 && (
        <ul className={estilos.lista}>
          {arrendadores.map((arrendador) => (
            <li key={arrendador.arrendadorId}>
              <Tarjeta
                titulo={arrendador.nombre}
                acciones={<Link to={`/admin/identidades/${arrendador.arrendadorId}`}>Revisar</Link>}
              >
                <p className={estilos.dato}>{arrendador.razonSocial}</p>
                <p className={estilos.dato}>Tipo de documento: {arrendador.tipoDocumento}</p>
                <p className={estilos.dato}>Enviado el {fechaHora(arrendador.enviadoEn)}</p>
                <div>
                  <EtiquetaEstado estado="EN_REVISION" />
                </div>
              </Tarjeta>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default ColaIdentidades
