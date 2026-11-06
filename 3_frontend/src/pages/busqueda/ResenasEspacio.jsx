import { useEffect, useState } from 'react'
import { listarResenasDeEspacio } from '../../api/busqueda.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import { BOTON_VER_MAS, formatoFecha } from '../reservas/textosReservas.js'
import { CARGANDO, SIN_RESENAS, TITULO_RESENAS, tituloResena } from './textosFicha.js'
import estilos from './ResenasEspacio.module.css'

const PRIMERA_PAGINA = 1

// Une la página nueva a lo ya cargado sin repetir ids: una reseña nueva corre a las demás entre dos lecturas.
function sinRepetir(anteriores, nuevas) {
  const ids = new Set(anteriores.map((resena) => resena.id))
  return [...anteriores, ...nuevas.filter((resena) => !ids.has(resena.id))]
}

// HU-24 (CA1): reseñas de la ficha pública. El orden, las páginas y cuáles se ven los decide el servidor. React escapa
// el comentario al mostrarlo.
function ResenasEspacio({ espacioId }) {
  const [resenas, setResenas] = useState([])
  const [pagina, setPagina] = useState(0)
  const [hayMas, setHayMas] = useState(false)
  const [cargandoInicio, setCargandoInicio] = useState(true)
  const [cargandoMas, setCargandoMas] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    let activo = true
    listarResenasDeEspacio(espacioId, PRIMERA_PAGINA)
      .then((datos) => {
        if (!activo) return
        setResenas(datos.resenas)
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
  }, [espacioId])

  async function verMas() {
    setCargandoMas(true)
    setError(null)
    try {
      const datos = await listarResenasDeEspacio(espacioId, pagina + 1)
      setResenas((anteriores) => sinRepetir(anteriores, datos.resenas))
      setPagina(datos.pagina)
      setHayMas(datos.hayMas)
    } catch (falla) {
      setError(falla.mensaje)
    } finally {
      setCargandoMas(false)
    }
  }

  return (
    <section className={estilos.resenas}>
      <h2 className={estilos.titulo}>{TITULO_RESENAS}</h2>
      {error && <Alerta tipo="error">{error}</Alerta>}
      {cargandoInicio && <p className={estilos.secundario}>{CARGANDO}</p>}
      {!cargandoInicio && !error && resenas.length === 0 && <p className={estilos.secundario}>{SIN_RESENAS}</p>}
      {resenas.length > 0 && (
        <ul className={estilos.lista}>
          {resenas.map((resena) => (
            <li key={resena.id}>
              <Tarjeta titulo={tituloResena(resena.calificacion)}>
                <p className={estilos.comentario}>{resena.comentario}</p>
                <p className={estilos.secundario}>{formatoFecha(resena.fecha)}</p>
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

export default ResenasEspacio
