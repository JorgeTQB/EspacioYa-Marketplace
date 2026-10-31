import { useEffect, useState } from 'react'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import { BOTON_VER_MAS, CARGANDO } from './textosReservas.js'
import estilos from './SeccionReservas.module.css'

const PRIMERA_PAGINA = 1

// Une la página nueva a lo ya cargado sin repetir ids (una reserva puede cambiar de página entre dos lecturas).
function sinRepetir(anteriores, nuevas) {
  const ids = new Set(anteriores.map((reserva) => reserva.id))
  return [...anteriores, ...nuevas.filter((reserva) => !ids.has(reserva.id))]
}

// HU-14: un grupo de reservas ("proximas" o "pasadas") con "Ver más". El corte, el orden y las páginas los decide el
// servidor; la sección solo pide la página siguiente mientras hayMas.
function SeccionReservas({ titulo, grupo, cargar, textoVacio, renderReserva }) {
  const [reservas, setReservas] = useState([])
  const [pagina, setPagina] = useState(0)
  const [hayMas, setHayMas] = useState(false)
  const [cargandoInicio, setCargandoInicio] = useState(true)
  const [cargandoMas, setCargandoMas] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    let activo = true
    cargar({ grupo, pagina: PRIMERA_PAGINA })
      .then((datos) => {
        if (!activo) return
        setReservas(datos.reservas)
        setPagina(datos.pagina)
        setHayMas(datos.hayMas)
      })
      .catch((falla) => {
        if (activo) setError(falla.mensaje)
      })
      .finally(() => {
        if (activo) setCargandoInicio(false)
      })
    return () => {
      activo = false
    }
  }, [cargar, grupo])

  async function verMas() {
    setCargandoMas(true)
    setError(null)
    try {
      const datos = await cargar({ grupo, pagina: pagina + 1 })
      setReservas((anteriores) => sinRepetir(anteriores, datos.reservas))
      setPagina(datos.pagina)
      setHayMas(datos.hayMas)
    } catch (falla) {
      setError(falla.mensaje)
    } finally {
      setCargandoMas(false)
    }
  }

  return (
    <section className={estilos.seccion}>
      <h3 className={estilos.titulo}>{titulo}</h3>
      {error && <Alerta tipo="error">{error}</Alerta>}
      {cargandoInicio && <p className={estilos.secundario}>{CARGANDO}</p>}
      {!cargandoInicio && !error && reservas.length === 0 && <p className={estilos.secundario}>{textoVacio}</p>}
      {reservas.length > 0 && (
        <ul className={estilos.lista}>
          {reservas.map((reserva) => (
            <li key={reserva.id}>{renderReserva(reserva)}</li>
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

export default SeccionReservas
