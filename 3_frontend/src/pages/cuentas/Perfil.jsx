import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import {
  actualizarDatosPerfil,
  actualizarFacturacion,
  obtenerFotoPerfil,
  obtenerPerfil,
  quitarFotoPerfil,
  solicitarCambioCorreo,
  subirFotoPerfil,
} from '../../api/cuentas.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Imagen from '../../components/ui/Imagen.jsx'
import Modal from '../../components/ui/Modal.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import DatosPersonales from './DatosPersonales.jsx'
import { erroresPorCampo } from './erroresCampo.js'
import {
  ALT_FOTO,
  AVISO_FACTURACION_EN_REVISION,
  AVISO_FACTURACION_VERIFICADA,
  AYUDA_TELEFONO,
  BOTON_CAMBIAR_CORREO,
  BOTON_CONFIRMAR,
  BOTON_GUARDAR_DATOS,
  BOTON_GUARDAR_FACTURACION,
  BOTON_QUITAR_FOTO,
  BOTON_SUBIR_FOTO,
  CARGANDO_FOTO,
  CARGANDO_PERFIL,
  DATOS_GUARDADOS,
  ETIQUETA_CONTRASENA,
  ETIQUETA_CORREO_NUEVO,
  ETIQUETA_DIRECCION_FISCAL,
  ETIQUETA_FOTO,
  ETIQUETA_NOMBRE,
  ETIQUETA_NUMERO_DOCUMENTO,
  ETIQUETA_RAZON_SOCIAL,
  ETIQUETA_TELEFONO,
  ETIQUETA_TIPO_DOCUMENTO,
  EXPLICACION_CONTRASENA_PERFIL,
  FACTURACION_GUARDADA,
  FOTO_GUARDADA,
  FOTO_NO_DISPONIBLE,
  FOTO_QUITADA,
  OPCIONES_TIPO_DOCUMENTO,
  TITULO_CONFIRMAR_CONTRASENA,
  TITULO_CORREO,
  TITULO_DATOS_PERSONALES,
  TITULO_FACTURACION,
  TITULO_FOTO,
  TITULO_PERFIL,
  ayudaNumeroDocumento,
  correoActual,
  correoPendiente,
} from './textosCuentas.js'
import estilos from './Perfil.module.css'

const OPERACION_CORREO = 'correo'
const OPERACION_FACTURACION = 'facturacion'
const CAMPOS_FACTURACION = ['tipoDocumento', 'numeroDocumento', 'razonSocial', 'direccionFiscal']

function datosDe(perfil) {
  return { nombre: perfil.nombre ?? '', telefono: perfil.telefono ?? '' }
}

function facturacionDe(perfil) {
  const facturacion = perfil.facturacion
  if (!facturacion) return null
  return {
    tipoDocumento: facturacion.tipoDocumento,
    numeroDocumento: '',
    razonSocial: facturacion.razonSocial,
    direccionFiscal: facturacion.direccionFiscal,
  }
}

// HU-20 CA1-CA3: datos personales, foto, correo y (solo arrendador) facturación del titular de la sesión. Lo que se
// muestra tras guardar sale de la respuesta del servidor, no del estado local; las reglas (qué exige contraseña, cuándo
// vuelve la identidad a revisión) las aplica el servidor. La contraseña vive solo en el estado del modal.
function Perfil() {
  const { sesion, refrescar } = useSesion()
  const esAdmin = sesion?.rol === 'ADMIN'
  const [perfil, setPerfil] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [errorCarga, setErrorCarga] = useState(null)
  const [recarga, setRecarga] = useState(0)

  const [datos, setDatos] = useState({ nombre: '', telefono: '' })
  const [erroresDatos, setErroresDatos] = useState({})
  const [avisoDatos, setAvisoDatos] = useState(null)
  const [guardandoDatos, setGuardandoDatos] = useState(false)

  const [archivo, setArchivo] = useState(null)
  const [versionFoto, setVersionFoto] = useState(0)
  const [foto, setFoto] = useState({ version: -1, objeto: null, fallo: false })
  const [errorFoto, setErrorFoto] = useState(null)
  const [avisoFoto, setAvisoFoto] = useState(null)
  const [cambiandoFoto, setCambiandoFoto] = useState(false)

  const [correoNuevo, setCorreoNuevo] = useState('')
  const [errorCampoCorreo, setErrorCampoCorreo] = useState(null)
  const [avisoCorreo, setAvisoCorreo] = useState(null)

  const [facturacion, setFacturacion] = useState(null)
  const [erroresFacturacion, setErroresFacturacion] = useState({})
  const [avisoFacturacion, setAvisoFacturacion] = useState(null)

  const [operacion, setOperacion] = useState(null)
  const [contrasena, setContrasena] = useState('')
  const [errorContrasena, setErrorContrasena] = useState(null)
  const [errorModal, setErrorModal] = useState(null)
  const [confirmando, setConfirmando] = useState(false)

  function aplicarPerfil(respuesta) {
    setPerfil(respuesta)
    setDatos(datosDe(respuesta))
    setFacturacion(facturacionDe(respuesta))
    setVersionFoto((n) => n + 1)
  }

  useEffect(() => {
    if (esAdmin) return undefined
    let activo = true
    obtenerPerfil()
      .then((respuesta) => {
        if (activo) {
          aplicarPerfil(respuesta)
          setErrorCarga(null)
        }
      })
      .catch((falla) => {
        if (activo) setErrorCarga(falla.mensaje)
      })
      .finally(() => {
        if (activo) setCargando(false)
      })
    return () => {
      activo = false
    }
  }, [esAdmin, recarga])

  const tieneFoto = Boolean(perfil?.tieneFoto)

  // La foto es privada: se descarga con el token y se muestra como objeto local, que se libera al reemplazarla o al
  // salir de la pantalla.
  useEffect(() => {
    if (!tieneFoto) return undefined
    let activo = true
    let objeto = null
    obtenerFotoPerfil()
      .then((blob) => {
        objeto = URL.createObjectURL(blob)
        if (activo) {
          setFoto({ version: versionFoto, objeto, fallo: false })
        } else {
          URL.revokeObjectURL(objeto)
        }
      })
      .catch(() => {
        if (activo) setFoto({ version: versionFoto, objeto: null, fallo: true })
      })
    return () => {
      activo = false
      if (objeto) URL.revokeObjectURL(objeto)
    }
  }, [tieneFoto, versionFoto])

  if (esAdmin) return <Navigate to="/panel" replace />

  // ── Datos personales (CA1) ─────────────────────────────────────────

  async function guardarDatos(evento) {
    evento.preventDefault()
    setGuardandoDatos(true)
    setErroresDatos({})
    setAvisoDatos(null)
    try {
      aplicarPerfil(await actualizarDatosPerfil(datos))
      setAvisoDatos({ tipo: 'exito', texto: DATOS_GUARDADOS })
    } catch (falla) {
      setErroresDatos(erroresPorCampo(falla.errores))
      setAvisoDatos({ tipo: 'error', texto: falla.mensaje })
    } finally {
      setGuardandoDatos(false)
    }
  }

  // ── Foto (CA1) ─────────────────────────────────────────────────────

  async function cambiarFoto(accion, exito) {
    setCambiandoFoto(true)
    setErrorFoto(null)
    setAvisoFoto(null)
    try {
      aplicarPerfil(await accion())
      setAvisoFoto({ tipo: 'exito', texto: exito })
    } catch (falla) {
      const porCampo = erroresPorCampo(falla.errores)
      if (porCampo.foto) {
        setErrorFoto(porCampo.foto)
      } else {
        setAvisoFoto({ tipo: 'error', texto: falla.mensaje })
      }
    } finally {
      setCambiandoFoto(false)
    }
  }

  function subirFoto() {
    cambiarFoto(() => subirFotoPerfil(archivo), FOTO_GUARDADA)
  }

  function quitarFoto() {
    cambiarFoto(quitarFotoPerfil, FOTO_QUITADA)
  }

  // ── Modal de contraseña (CA2, CA3) ─────────────────────────────────

  function abrirModal(nueva) {
    setOperacion(nueva)
    setContrasena('')
    setErrorContrasena(null)
    setErrorModal(null)
  }

  function cerrarModal() {
    if (!confirmando) {
      setOperacion(null)
      setContrasena('')
    }
  }

  function pedirCambioCorreo(evento) {
    evento.preventDefault()
    setErrorCampoCorreo(null)
    setAvisoCorreo(null)
    abrirModal(OPERACION_CORREO)
  }

  function pedirFacturacion(evento) {
    evento.preventDefault()
    setErroresFacturacion({})
    setAvisoFacturacion(null)
    abrirModal(OPERACION_FACTURACION)
  }

  // Un error en un campo del formulario cierra el modal para que se vea bajo su Campo; uno de contraseña o sin campo
  // (429, identidad en revisión, documento no disponible) se queda en el modal.
  function mostrarError(falla, camposFormulario, alErrorDeCampo) {
    const porCampo = erroresPorCampo(falla.errores)
    if (porCampo.contrasena) {
      setErrorContrasena(porCampo.contrasena)
      return
    }
    if (camposFormulario.some((campo) => porCampo[campo])) {
      alErrorDeCampo(porCampo)
      setOperacion(null)
      return
    }
    setErrorModal(falla.mensaje)
  }

  async function confirmarConContrasena() {
    setConfirmando(true)
    setErrorContrasena(null)
    setErrorModal(null)
    try {
      if (operacion === OPERACION_CORREO) {
        const respuesta = await solicitarCambioCorreo({ correo: correoNuevo, contrasena })
        setOperacion(null)
        setCorreoNuevo('')
        setAvisoCorreo(respuesta.mensaje)
        setRecarga((n) => n + 1)
      } else {
        const respuesta = await actualizarFacturacion({ ...facturacion, contrasena })
        setOperacion(null)
        aplicarPerfil(respuesta)
        setAvisoFacturacion(FACTURACION_GUARDADA)
        refrescar()
      }
    } catch (falla) {
      if (operacion === OPERACION_CORREO) {
        mostrarError(falla, ['correo'], (porCampo) => setErrorCampoCorreo(porCampo.correo))
      } else {
        mostrarError(falla, CAMPOS_FACTURACION, setErroresFacturacion)
      }
    } finally {
      setContrasena('')
      setConfirmando(false)
    }
  }

  // ── Pantalla ───────────────────────────────────────────────────────

  if (cargando) {
    return (
      <section className={estilos.pantalla}>
        <h2>{TITULO_PERFIL}</h2>
        <p className={estilos.secundario}>{CARGANDO_PERFIL}</p>
      </section>
    )
  }

  if (!perfil) {
    return (
      <section className={estilos.pantalla}>
        <h2>{TITULO_PERFIL}</h2>
        {errorCarga && <Alerta tipo="error">{errorCarga}</Alerta>}
      </section>
    )
  }

  function vistaFoto() {
    if (!tieneFoto) return <Imagen src={null} alt={ALT_FOTO} />
    if (foto.version !== versionFoto) return <p className={estilos.secundario}>{CARGANDO_FOTO}</p>
    if (foto.fallo) return <p className={estilos.secundario}>{FOTO_NO_DISPONIBLE}</p>
    return <img className={estilos.foto} src={foto.objeto} alt={ALT_FOTO} />
  }

  const estadoIdentidad = perfil.facturacion?.estadoIdentidad
  const enRevision = estadoIdentidad === 'EN_REVISION'

  return (
    <section className={estilos.pantalla}>
      <h2>{TITULO_PERFIL}</h2>

      <Tarjeta titulo={TITULO_DATOS_PERSONALES}>
        {avisoDatos && <Alerta tipo={avisoDatos.tipo}>{avisoDatos.texto}</Alerta>}
        <form className={estilos.formulario} onSubmit={guardarDatos} noValidate>
          <Campo
            etiqueta={ETIQUETA_NOMBRE}
            nombre="nombre"
            valor={datos.nombre}
            onChange={(nombre) => setDatos((actual) => ({ ...actual, nombre }))}
            error={erroresDatos.nombre}
          />
          <Campo
            etiqueta={ETIQUETA_TELEFONO}
            nombre="telefono"
            tipo="tel"
            valor={datos.telefono}
            onChange={(telefono) => setDatos((actual) => ({ ...actual, telefono }))}
            error={erroresDatos.telefono}
            ayuda={AYUDA_TELEFONO}
          />
          <Boton tipo="submit" cargando={guardandoDatos}>
            {BOTON_GUARDAR_DATOS}
          </Boton>
        </form>
      </Tarjeta>

      <Tarjeta titulo={TITULO_FOTO}>
        {avisoFoto && <Alerta tipo={avisoFoto.tipo}>{avisoFoto.texto}</Alerta>}
        <div className={estilos.marcoFoto}>{vistaFoto()}</div>
        <Campo
          etiqueta={ETIQUETA_FOTO}
          nombre="foto"
          tipo="archivo"
          accept="image/jpeg,image/png"
          onChange={setArchivo}
          error={errorFoto}
        />
        <div className={estilos.acciones}>
          <Boton variante="secundario" cargando={cambiandoFoto} onClick={subirFoto}>
            {BOTON_SUBIR_FOTO}
          </Boton>
          {tieneFoto && (
            <Boton variante="secundario" deshabilitado={cambiandoFoto} onClick={quitarFoto}>
              {BOTON_QUITAR_FOTO}
            </Boton>
          )}
        </div>
      </Tarjeta>

      <Tarjeta titulo={TITULO_CORREO}>
        {avisoCorreo && <Alerta tipo="exito">{avisoCorreo}</Alerta>}
        <p>{correoActual(perfil.correo)}</p>
        {perfil.correoPendiente && <Alerta tipo="info">{correoPendiente(perfil.correoPendiente)}</Alerta>}
        <form className={estilos.formulario} onSubmit={pedirCambioCorreo} noValidate>
          <Campo
            etiqueta={ETIQUETA_CORREO_NUEVO}
            nombre="correo"
            tipo="email"
            valor={correoNuevo}
            onChange={setCorreoNuevo}
            error={errorCampoCorreo}
          />
          <Boton tipo="submit" variante="secundario">
            {BOTON_CAMBIAR_CORREO}
          </Boton>
        </form>
      </Tarjeta>

      {facturacion && (
        <Tarjeta titulo={TITULO_FACTURACION}>
          <EtiquetaEstado estado={estadoIdentidad} />
          {avisoFacturacion && <Alerta tipo="exito">{avisoFacturacion}</Alerta>}
          {estadoIdentidad === 'VERIFICADA' && <Alerta tipo="atencion">{AVISO_FACTURACION_VERIFICADA}</Alerta>}
          {enRevision && <Alerta tipo="info">{AVISO_FACTURACION_EN_REVISION}</Alerta>}
          <form className={estilos.formulario} onSubmit={pedirFacturacion} noValidate>
            {enRevision ? (
              <dl className={estilos.datos}>
                <dt>{ETIQUETA_TIPO_DOCUMENTO}</dt>
                <dd>{perfil.facturacion.tipoDocumento}</dd>
                <dt>{ETIQUETA_NUMERO_DOCUMENTO}</dt>
                <dd>{perfil.facturacion.numeroDocumentoEnmascarado}</dd>
                <dt>{ETIQUETA_RAZON_SOCIAL}</dt>
                <dd>{perfil.facturacion.razonSocial}</dd>
              </dl>
            ) : (
              <>
                <Campo
                  etiqueta={ETIQUETA_TIPO_DOCUMENTO}
                  nombre="tipoDocumento"
                  tipo="select"
                  opciones={OPCIONES_TIPO_DOCUMENTO}
                  valor={facturacion.tipoDocumento}
                  onChange={(tipoDocumento) => setFacturacion((actual) => ({ ...actual, tipoDocumento }))}
                  error={erroresFacturacion.tipoDocumento}
                />
                <Campo
                  etiqueta={ETIQUETA_NUMERO_DOCUMENTO}
                  nombre="numeroDocumento"
                  valor={facturacion.numeroDocumento}
                  onChange={(numeroDocumento) => setFacturacion((actual) => ({ ...actual, numeroDocumento }))}
                  error={erroresFacturacion.numeroDocumento}
                  ayuda={ayudaNumeroDocumento(perfil.facturacion.numeroDocumentoEnmascarado)}
                />
                <Campo
                  etiqueta={ETIQUETA_RAZON_SOCIAL}
                  nombre="razonSocial"
                  valor={facturacion.razonSocial}
                  onChange={(razonSocial) => setFacturacion((actual) => ({ ...actual, razonSocial }))}
                  error={erroresFacturacion.razonSocial}
                />
              </>
            )}
            <Campo
              etiqueta={ETIQUETA_DIRECCION_FISCAL}
              nombre="direccionFiscal"
              valor={facturacion.direccionFiscal}
              onChange={(direccionFiscal) => setFacturacion((actual) => ({ ...actual, direccionFiscal }))}
              error={erroresFacturacion.direccionFiscal}
            />
            <Boton tipo="submit" variante="secundario">
              {BOTON_GUARDAR_FACTURACION}
            </Boton>
          </form>
        </Tarjeta>
      )}

      <DatosPersonales />

      <Modal
        abierto={operacion !== null}
        titulo={TITULO_CONFIRMAR_CONTRASENA}
        textoConfirmar={BOTON_CONFIRMAR}
        cargando={confirmando}
        onConfirmar={confirmarConContrasena}
        onCerrar={cerrarModal}
      >
        <p>{EXPLICACION_CONTRASENA_PERFIL}</p>
        {errorModal && <Alerta tipo="error">{errorModal}</Alerta>}
        <Campo
          etiqueta={ETIQUETA_CONTRASENA}
          nombre="contrasena"
          tipo="password"
          valor={contrasena}
          onChange={setContrasena}
          error={errorContrasena}
        />
      </Modal>
    </section>
  )
}

export default Perfil
