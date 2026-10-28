import { useEffect, useState } from 'react'
import { descargarFotoEspacio } from '../../api/espacios.js'
import estilos from './FotoDelEspacio.module.css'

// La foto de un borrador solo la ve su dueño: se descarga con el token y se muestra como objeto local
// (un <img src> no envía el encabezado Authorization). El objeto se libera al cambiar la url o al desmontar.
function FotoDelEspacio({ url, alt }) {
  const [descarga, setDescarga] = useState({ url: null, objeto: null, fallo: false })

  useEffect(() => {
    let activo = true
    let objeto = null
    descargarFotoEspacio(url)
      .then((blob) => {
        objeto = URL.createObjectURL(blob)
        if (activo) {
          setDescarga({ url, objeto, fallo: false })
        } else {
          URL.revokeObjectURL(objeto)
        }
      })
      .catch(() => {
        if (activo) setDescarga({ url, objeto: null, fallo: true })
      })
    return () => {
      activo = false
      if (objeto) URL.revokeObjectURL(objeto)
    }
  }, [url])

  if (descarga.url !== url) {
    return (
      <div className={estilos.recuadro}>
        <span className={estilos.texto}>Cargando foto…</span>
      </div>
    )
  }
  if (descarga.fallo) {
    return (
      <div className={estilos.recuadro}>
        <span className={estilos.texto}>No pudimos mostrar esta foto.</span>
      </div>
    )
  }
  return <img className={estilos.foto} src={descarga.objeto} alt={alt} />
}

export default FotoDelEspacio
