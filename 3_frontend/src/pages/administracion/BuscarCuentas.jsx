import { useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { buscarCuentas } from '../../api/administracion.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { NOMBRES_ROL } from './textosAdministracion.js'
import estilos from './BuscarCuentas.module.css'

function errorDeBusqueda(errores) {
  return errores.find(({ campo }) => campo === 'q')?.mensaje ?? null
}

// CA1 de HU-09: el admin busca una cuenta por correo o nombre. La API valida el texto y decide qué coincide.
function BuscarCuentas() {
  const { sesion } = useSesion()
  const esAdmin = sesion?.rol === 'ADMIN'
  const [texto, setTexto] = useState('')
  const [resultado, setResultado] = useState(null)
  const [buscando, setBuscando] = useState(false)
  const [error, setError] = useState(null)
  const [errorCampo, setErrorCampo] = useState(null)

  if (!esAdmin) return <Navigate to="/panel" replace />

  async function enviar(evento) {
    evento.preventDefault()
    setBuscando(true)
    setError(null)
    setErrorCampo(null)
    try {
      setResultado(await buscarCuentas(texto))
    } catch (falla) {
      setResultado(null)
      const delCampo = errorDeBusqueda(falla.errores)
      if (delCampo) {
        setErrorCampo(delCampo)
      } else {
        setError(falla.mensaje)
      }
    } finally {
      setBuscando(false)
    }
  }

  return (
    <section className={estilos.pantalla}>
      <h2>Buscar cuentas</h2>

      {error && <Alerta tipo="error">{error}</Alerta>}

      <form className={estilos.formulario} onSubmit={enviar} noValidate>
        <Campo
          etiqueta="Correo o nombre"
          nombre="q"
          valor={texto}
          onChange={setTexto}
          error={errorCampo}
          ayuda="Al menos 3 caracteres."
        />
        <div>
          <Boton tipo="submit" cargando={buscando}>
            Buscar
          </Boton>
        </div>
      </form>

      {resultado?.hayMas && (
        <Alerta tipo="info">
          Hay más de 20 resultados. Escribe más del correo o del nombre para acotar.
        </Alerta>
      )}

      {resultado && resultado.cuentas.length === 0 && (
        <p className={estilos.secundario}>No encontramos cuentas con ese correo o nombre.</p>
      )}

      {resultado?.cuentas.length > 0 && (
        <ul className={estilos.lista}>
          {resultado.cuentas.map((cuenta) => (
            <li key={cuenta.id}>
              <Tarjeta
                titulo={cuenta.nombre ?? cuenta.correo}
                acciones={<Link to={`/admin/cuentas/${cuenta.id}`}>Ver cuenta</Link>}
              >
                <p className={estilos.dato}>{cuenta.correo}</p>
                <p className={estilos.dato}>{NOMBRES_ROL[cuenta.rol] ?? cuenta.rol}</p>
                <div>
                  <EtiquetaEstado estado={cuenta.estado} />
                </div>
              </Tarjeta>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default BuscarCuentas
