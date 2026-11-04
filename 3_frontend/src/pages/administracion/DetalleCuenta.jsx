import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { obtenerCuenta, reactivarCuenta, suspenderCuenta } from '../../api/administracion.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Modal from '../../components/ui/Modal.jsx'
import useSesion from '../../sesion/useSesion.js'
import { NOMBRES_ROL, fechaHora } from './textosAdministracion.js'
import estilos from './DetalleCuenta.module.css'

const BUSQUEDA = '/admin/cuentas'
const MENSAJE_MOTIVO = 'Escribe el motivo de la suspensión (hasta 500 caracteres).'

function errorDeMotivo(errores) {
  return errores.find(({ campo }) => campo === 'motivo')?.mensaje ?? null
}

// CA1–CA3 de HU-09: el admin ve la cuenta y la suspende o reactiva. Qué acciones hay lo decide la API
// (puedeSuspender, puedeReactivar); la pantalla solo las muestra.
function DetalleCuenta() {
  const { id } = useParams()
  const { sesion } = useSesion()
  const esAdmin = sesion?.rol === 'ADMIN'
  const [detalle, setDetalle] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [errorCarga, setErrorCarga] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [recarga, setRecarga] = useState(0)
  const [dialogo, setDialogo] = useState(null)
  const [motivo, setMotivo] = useState('')
  const [errorMotivo, setErrorMotivo] = useState(null)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!esAdmin) return undefined
    let activo = true
    obtenerCuenta(id)
      .then((datos) => {
        if (activo) {
          setDetalle(datos)
          setErrorCarga(null)
        }
      })
      .catch((falla) => {
        if (activo) {
          setDetalle(null)
          setErrorCarga(falla.mensaje)
        }
      })
      .finally(() => {
        if (activo) setCargando(false)
      })
    return () => {
      activo = false
    }
  }, [esAdmin, id, recarga])

  if (!esAdmin) return <Navigate to="/panel" replace />

  const cuenta = detalle?.cuenta
  const nombre = cuenta ? (cuenta.nombre ?? cuenta.correo) : ''

  function abrir(tipo) {
    setMotivo('')
    setErrorMotivo(null)
    setAviso(null)
    setDialogo(tipo)
  }

  function cerrar() {
    if (!enviando) setDialogo(null)
  }

  async function actuar(enviar, mensajeExito) {
    setEnviando(true)
    try {
      await enviar()
      setDialogo(null)
      setAviso({ tipo: 'exito', texto: mensajeExito })
    } catch (falla) {
      const delCampo = errorDeMotivo(falla.errores)
      if (delCampo) {
        setErrorMotivo(delCampo)
        setEnviando(false)
        return
      }
      // No gestionable, ya suspendida, ya activa, 404, 403 o red: la acción no se hizo y el detalle cambió.
      setDialogo(null)
      setAviso({ tipo: 'error', texto: falla.mensaje })
    }
    setEnviando(false)
    setRecarga((valor) => valor + 1)
  }

  // Solo ayuda: el servidor recorta y valida el motivo siempre.
  function confirmarSuspension() {
    if (!motivo.trim()) {
      setErrorMotivo(MENSAJE_MOTIVO)
      return
    }
    actuar(
      () => suspenderCuenta(id, motivo),
      'Suspendiste la cuenta. Cerramos sus sesiones y le enviamos el motivo por correo.',
    )
  }

  function confirmarReactivacion() {
    actuar(() => reactivarCuenta(id), 'Reactivaste la cuenta. Le avisamos por correo.')
  }

  return (
    <section className={estilos.pantalla}>
      {aviso && <Alerta tipo={aviso.tipo}>{aviso.texto}</Alerta>}
      {errorCarga && <Alerta tipo="error">{errorCarga}</Alerta>}
      {cargando && <p className={estilos.secundario}>Cargando…</p>}

      {cuenta && (
        <>
          <div className={estilos.encabezado}>
            <h2>{nombre}</h2>
            <EtiquetaEstado estado={cuenta.estado} />
          </div>

          <dl className={estilos.datos}>
            <dt>Correo</dt>
            <dd>{cuenta.correo}</dd>
            {cuenta.telefono && (
              <>
                <dt>Teléfono</dt>
                <dd>{cuenta.telefono}</dd>
              </>
            )}
            <dt>Rol</dt>
            <dd>{NOMBRES_ROL[cuenta.rol] ?? cuenta.rol}</dd>
            <dt>Correo verificado</dt>
            <dd>{cuenta.correoVerificado ? 'Sí' : 'No'}</dd>
            {cuenta.estadoIdentidad && (
              <>
                <dt>Identidad</dt>
                <dd>
                  <EtiquetaEstado estado={cuenta.estadoIdentidad} />
                </dd>
              </>
            )}
          </dl>

          {detalle.suspension && (
            <div className={estilos.suspension}>
              <p>Suspendida el {fechaHora(detalle.suspension.fecha)}</p>
              <p>Motivo: {detalle.suspension.motivo}</p>
            </div>
          )}

          <div className={estilos.acciones}>
            {cuenta.puedeSuspender && (
              <Boton variante="peligro" onClick={() => abrir('suspender')}>
                Suspender cuenta
              </Boton>
            )}
            {cuenta.puedeReactivar && <Boton onClick={() => abrir('reactivar')}>Reactivar cuenta</Boton>}
          </div>
          {!cuenta.puedeSuspender && !cuenta.puedeReactivar && (
            <p className={estilos.secundario}>Esta cuenta no se puede suspender ni reactivar desde el panel.</p>
          )}

          <Modal
            abierto={dialogo === 'suspender'}
            titulo={`Suspender la cuenta de ${nombre}`}
            textoConfirmar="Suspender"
            variante="peligro-confirmar"
            cargando={enviando}
            onConfirmar={confirmarSuspension}
            onCerrar={cerrar}
          >
            <p>Se cerrarán sus sesiones, no podrá iniciar sesión y sus espacios dejarán de mostrarse.</p>
            <Campo
              etiqueta="Motivo de la suspensión"
              nombre="motivo"
              tipo="textarea"
              valor={motivo}
              onChange={setMotivo}
              error={errorMotivo}
              ayuda="Se lo enviaremos por correo. Hasta 500 caracteres."
            />
          </Modal>

          <Modal
            abierto={dialogo === 'reactivar'}
            titulo={`¿Reactivar la cuenta de ${nombre}?`}
            textoConfirmar="Reactivar"
            variante="primario"
            cargando={enviando}
            onConfirmar={confirmarReactivacion}
            onCerrar={cerrar}
          >
            <p>Podrá iniciar sesión y sus espacios volverán a mostrarse.</p>
          </Modal>
        </>
      )}

      {!cargando && (
        <p>
          <Link to={BUSQUEDA}>Volver a la búsqueda</Link>
        </p>
      )}
    </section>
  )
}

export default DetalleCuenta
