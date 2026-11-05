import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { eliminarMiCuenta, exportarMisDatos } from '../../api/cuentas.js'
import { borrarToken } from '../../api/tokenSesion.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import Modal from '../../components/ui/Modal.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { erroresPorCampo } from './erroresCampo.js'
import {
  AVISO_ELIMINAR_CUENTA,
  BOTON_DESCARGAR,
  BOTON_DESCARGAR_DATOS,
  BOTON_ELIMINAR_CUENTA,
  DATOS_DESCARGADOS,
  ENLACE_VER_LIQUIDACIONES,
  ENLACE_VER_RESERVAS,
  ENLACE_VER_RESERVAS_ESPACIOS,
  ETIQUETA_CONTRASENA,
  EXPLICACION_CONTRASENA_DESCARGA,
  EXPLICACION_MIS_DATOS,
  TITULO_CONFIRMAR_CONTRASENA,
  TITULO_ELIMINAR_CUENTA,
  TITULO_IMPEDIMENTOS,
  TITULO_MIS_DATOS,
  nombreArchivoDatos,
} from './textosCuentas.js'
import estilos from './DatosPersonales.module.css'

const DESCARGAR = 'descargar'
const ELIMINAR = 'eliminar'
const CAMPOS_IMPEDIMENTO = ['reservas', 'liquidaciones', 'pagos']

// La fecha de Lima del instante en que el servidor generó el archivo (en-CA da AAAA-MM-DD); no es una regla de negocio.
const FECHA_LIMA = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' })

function descargarJson(datos) {
  const blob = new Blob([JSON.stringify(datos, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombreArchivoDatos(FECHA_LIMA.format(new Date(datos.generadaEn)))
  document.body.appendChild(enlace)
  enlace.click()
  enlace.remove()
  URL.revokeObjectURL(url)
}

function enlaceDe(campo, rol) {
  if (campo === 'reservas') {
    return rol === 'ARRENDADOR'
      ? { ruta: '/panel/reservas', texto: ENLACE_VER_RESERVAS_ESPACIOS }
      : { ruta: '/mis-reservas', texto: ENLACE_VER_RESERVAS }
  }
  return { ruta: '/panel/liquidaciones', texto: ENLACE_VER_LIQUIDACIONES }
}

// HU-22 CA1-CA3: descargar una copia de los datos y eliminar la cuenta, ambas con contraseña. Qué se exporta, qué
// impide eliminar y qué se anonimiza lo decide el servidor; la pantalla solo muestra lo que responde. La contraseña vive
// solo en el estado del modal y el JSON solo en memoria hasta la descarga.
function DatosPersonales() {
  const { sesion, salir } = useSesion()
  const navegar = useNavigate()
  const [operacion, setOperacion] = useState(null)
  const [contrasena, setContrasena] = useState('')
  const [errorContrasena, setErrorContrasena] = useState(null)
  const [errorModal, setErrorModal] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [aviso, setAviso] = useState(null)
  const [impedimentos, setImpedimentos] = useState([])

  function abrir(nueva) {
    setOperacion(nueva)
    setContrasena('')
    setErrorContrasena(null)
    setErrorModal(null)
    setAviso(null)
    setImpedimentos([])
  }

  function cerrar() {
    if (!enviando) {
      setOperacion(null)
      setContrasena('')
    }
  }

  // Contraseña → bajo el Campo; impedimentos → cierran el modal y se listan en la tarjeta; 429 o sin campo → modal.
  function mostrarError(falla) {
    const porCampo = erroresPorCampo(falla.errores)
    if (porCampo.contrasena) {
      setErrorContrasena(porCampo.contrasena)
      return
    }
    const encontrados = falla.errores.filter(({ campo }) => CAMPOS_IMPEDIMENTO.includes(campo))
    if (encontrados.length > 0) {
      setImpedimentos(encontrados)
      setOperacion(null)
      return
    }
    setErrorModal(falla.mensaje)
  }

  async function confirmar() {
    setEnviando(true)
    setErrorContrasena(null)
    setErrorModal(null)
    try {
      if (operacion === DESCARGAR) {
        descargarJson(await exportarMisDatos({ contrasena }))
        setOperacion(null)
        setAviso(DATOS_DESCARGADOS)
      } else {
        const respuesta = await eliminarMiCuenta({ contrasena })
        // Antes de navegar: así el 401 siguiente no dispara el aviso de "sesión perdida".
        borrarToken()
        salir()
        navegar('/iniciar-sesion', { replace: true, state: { mensaje: respuesta.mensaje, tipo: 'exito' } })
      }
    } catch (falla) {
      mostrarError(falla)
    } finally {
      setContrasena('')
      setEnviando(false)
    }
  }

  const campoContrasena = (
    <Campo
      etiqueta={ETIQUETA_CONTRASENA}
      nombre="contrasena"
      tipo="password"
      valor={contrasena}
      onChange={setContrasena}
      error={errorContrasena}
    />
  )

  return (
    <>
      <Tarjeta
        titulo={TITULO_MIS_DATOS}
        acciones={
          <>
            <Boton variante="secundario" onClick={() => abrir(DESCARGAR)}>
              {BOTON_DESCARGAR_DATOS}
            </Boton>
            <Boton variante="peligro" onClick={() => abrir(ELIMINAR)}>
              {BOTON_ELIMINAR_CUENTA}
            </Boton>
          </>
        }
      >
        <p className={estilos.secundario}>{EXPLICACION_MIS_DATOS}</p>
        {aviso && <Alerta tipo="exito">{aviso}</Alerta>}
        {impedimentos.length > 0 && (
          <Alerta tipo="atencion">
            <p className={estilos.tituloImpedimentos}>{TITULO_IMPEDIMENTOS}</p>
            <ul className={estilos.impedimentos}>
              {impedimentos.map(({ campo, mensaje }) => {
                const enlace = enlaceDe(campo, sesion?.rol)
                return (
                  <li key={campo}>
                    {mensaje} <Link to={enlace.ruta}>{enlace.texto}</Link>
                  </li>
                )
              })}
            </ul>
          </Alerta>
        )}
      </Tarjeta>

      <Modal
        abierto={operacion === DESCARGAR}
        titulo={TITULO_CONFIRMAR_CONTRASENA}
        textoConfirmar={BOTON_DESCARGAR}
        cargando={enviando}
        onConfirmar={confirmar}
        onCerrar={cerrar}
      >
        <p>{EXPLICACION_CONTRASENA_DESCARGA}</p>
        {errorModal && <Alerta tipo="error">{errorModal}</Alerta>}
        {operacion === DESCARGAR && campoContrasena}
      </Modal>

      <Modal
        abierto={operacion === ELIMINAR}
        titulo={TITULO_ELIMINAR_CUENTA}
        textoConfirmar={BOTON_ELIMINAR_CUENTA}
        variante="peligro-confirmar"
        cargando={enviando}
        onConfirmar={confirmar}
        onCerrar={cerrar}
      >
        <Alerta tipo="atencion">{AVISO_ELIMINAR_CUENTA}</Alerta>
        {errorModal && <Alerta tipo="error">{errorModal}</Alerta>}
        {operacion === ELIMINAR && campoContrasena}
      </Modal>
    </>
  )
}

export default DatosPersonales
