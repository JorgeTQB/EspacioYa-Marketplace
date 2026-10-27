import { useEffect, useState } from 'react'
import { obtenerVersionPrivacidad } from '../../api/cuentas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import estilos from './Privacidad.module.css'

function Privacidad() {
  const [version, setVersion] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let activo = true
    obtenerVersionPrivacidad()
      .then((datos) => {
        if (activo) setVersion(datos.version)
      })
      .catch((falla) => {
        if (activo) setError(falla.mensaje)
      })
    return () => {
      activo = false
    }
  }, [])

  return (
    <article className={estilos.pantalla}>
      <h2>Política de privacidad y términos</h2>
      {error && <Alerta tipo="error">{error}</Alerta>}
      {version && <p className={estilos.version}>Versión vigente: {version}</p>}
      {/* El texto legal lo provee el equipo (supuesto S3 de HU-01); este es el texto provisional. */}
      <p>
        EspacioYa trata tus datos personales conforme a la Ley N.° 29733, Ley de Protección de Datos
        Personales, solo para gestionar tu cuenta, tus reservas y tus pagos en la plataforma.
      </p>
      <p>
        Al crear tu cuenta aceptas esta política y los términos de uso en la versión indicada.
      </p>
    </article>
  )
}

export default Privacidad
