import { useEffect, useState } from 'react'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import { BOTON_VER_MAS_LIQUIDACIONES, CARGANDO_LIQUIDACIONES } from './textosPagos.js'
import estilos from './SeccionLiquidaciones.module.css'

const PRIMERA_PAGINA = 1

// Une la página nueva a lo ya cargado sin repetir ids (una liquidación puede cambiar de grupo entre dos lecturas).
function sinRepetir(anteriores, nuevas) {
  const ids = new Set(anteriores.map((liquidacion) => liquidacion.id))
  return [...anteriores, ...nuevas.filter((liquidacion) => !ids.has(liquidacion.id))]
}

// HU-19: una lista de liquidaciones con "Ver más" (como SeccionReservas de HU-14). El orden y las páginas los decide el
// servidor; la sección solo pide la página siguiente mientras hayMas. `titulo` es opcional.
function SeccionLiquidaciones({ titulo, grupo, cargar, textoVacio, renderLiquidacion }) {
  const [liquidaciones, setLiquidaciones] = useState([])
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
        setLiquidaciones(datos.liquidaciones)
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
      setLiquidaciones((anteriores) => sinRepetir(anteriores, datos.liquidaciones))
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
      {titulo && <h3 className={estilos.titulo}>{titulo}</h3>}
      {error && <Alerta tipo="error">{error}</Alerta>}
      {cargandoInicio && <p className={estilos.secundario}>{CARGANDO_LIQUIDACIONES}</p>}
      {!cargandoInicio && !error && liquidaciones.length === 0 && (
        <p className={estilos.secundario}>{textoVacio}</p>
      )}
      {liquidaciones.length > 0 && (
        <ul className={estilos.lista}>
          {liquidaciones.map((liquidacion) => (
            <li key={liquidacion.id}>{renderLiquidacion(liquidacion)}</li>
          ))}
        </ul>
      )}
      {hayMas && (
        <div>
          <Boton variante="secundario" cargando={cargandoMas} onClick={verMas}>
            {BOTON_VER_MAS_LIQUIDACIONES}
          </Boton>
        </div>
      )}
    </section>
  )
}

export default SeccionLiquidaciones
