import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { listarMisReclamos } from '../../api/administracion.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { formatoFecha } from '../reservas/textosReservas.js'
import {
  BOTON_VER_MAS,
  CARGANDO,
  ENLACE_VER_RECLAMO,
  MOTIVOS_RECLAMO,
  SIN_RECLAMOS_ARRENDADOR,
  SIN_RECLAMOS_USUARIO,
  TITULO_MIS_RECLAMOS,
  TITULO_RECLAMOS_ARRENDADOR,
  fechaHora,
} from './textosAdministracion.js'
import estilos from './MisReclamos.module.css'

function sinRepetir(anteriores, nuevos) {
  const ids = new Set(anteriores.map((reclamo) => reclamo.id))
  return [...anteriores, ...nuevos.filter((reclamo) => !ids.has(reclamo.id))]
}

// HU-26 CA1: los reclamos de la parte de la sesión, de 10 en 10. El orden y qué reclamos ve los decide el servidor.
function MisReclamos() {
  const { sesion } = useSesion()
  const esAdmin = sesion?.rol === 'ADMIN'
  const esArrendador = sesion?.rol === 'ARRENDADOR'
  const [reclamos, setReclamos] = useState([])
  const [pagina, setPagina] = useState(0)
  const [hayMas, setHayMas] = useState(false)
  const [cargando, setCargando] = useState(true)
  const [cargandoMas, setCargandoMas] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (esAdmin) return undefined
    let activo = true
    listarMisReclamos({ pagina: 1 })
      .then((datos) => {
        if (!activo) return
        setReclamos(datos.reclamos)
        setPagina(datos.pagina)
        setHayMas(datos.hayMas)
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

  if (esAdmin) return <Navigate to="/panel" replace />

  async function verMas() {
    setCargandoMas(true)
    setError(null)
    try {
      const datos = await listarMisReclamos({ pagina: pagina + 1 })
      setReclamos((anteriores) => sinRepetir(anteriores, datos.reclamos))
      setPagina(datos.pagina)
      setHayMas(datos.hayMas)
    } catch (falla) {
      setError(falla.mensaje)
    } finally {
      setCargandoMas(false)
    }
  }

  return (
    <section className={estilos.pantalla}>
      <h2>{esArrendador ? TITULO_RECLAMOS_ARRENDADOR : TITULO_MIS_RECLAMOS}</h2>
      {error && <Alerta tipo="error">{error}</Alerta>}
      {cargando && <p className={estilos.secundario}>{CARGANDO}</p>}
      {!cargando && !error && reclamos.length === 0 && (
        <p className={estilos.secundario}>{esArrendador ? SIN_RECLAMOS_ARRENDADOR : SIN_RECLAMOS_USUARIO}</p>
      )}
      {reclamos.length > 0 && (
        <ul className={estilos.lista}>
          {reclamos.map((reclamo) => (
            <li key={reclamo.id}>
              <Tarjeta
                titulo={reclamo.espacioNombre}
                acciones={<Link to={`/reclamos/${reclamo.id}`}>{ENLACE_VER_RECLAMO}</Link>}
              >
                <div>
                  <EtiquetaEstado estado={reclamo.estado} />
                </div>
                <p className={estilos.dato}>Reserva del {formatoFecha(reclamo.fecha)}</p>
                <p className={estilos.dato}>Motivo: {MOTIVOS_RECLAMO[reclamo.motivo] ?? reclamo.motivo}</p>
                <p className={estilos.dato}>Abierto el {fechaHora(reclamo.abiertoEn)}</p>
              </Tarjeta>
            </li>
          ))}
        </ul>
      )}
      {hayMas && (
        <div>
          <Boton variante="secundario" cargando={cargandoMas} onClick={verMas}>
            {BOTON_VER_MAS}
          </Boton>
        </div>
      )}
    </section>
  )
}

export default MisReclamos
