import { useEffect, useState } from 'react'
import { Navigate, useParams } from 'react-router-dom'
import { obtenerReclamoAdmin, resolverReclamo } from '../../api/administracion.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Modal from '../../components/ui/Modal.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { formatoHora, formatoPrecio } from '../espacios/textosEspacios.js'
import { formatoFecha } from '../reservas/textosReservas.js'
import HiloReclamo from './HiloReclamo.jsx'
import {
  BOTON_RESOLVER,
  CARGANDO,
  CONFIRMA_ARRENDADOR,
  CONFIRMA_TOTAL,
  ETIQUETA_A_FAVOR,
  ETIQUETA_MONTO_REEMBOLSO,
  ETIQUETA_REEMBOLSO,
  ETIQUETA_RESOLUCION,
  MOTIVOS_RECLAMO,
  OPCIONES_A_FAVOR,
  OPCIONES_REEMBOLSO,
  RECLAMO_RESUELTO,
  REEMBOLSO_EN_PASARELA,
  SIN_NOMBRE,
  TITULO_MODAL_RESOLVER,
  TITULO_RECLAMO,
  TITULO_RESOLVER,
  confirmaParcial,
  fechaHora,
  resultadoReclamo,
  tituloReserva,
} from './textosAdministracion.js'
import estilos from './ResolverReclamo.module.css'

const CAMPOS = ['aFavorDe', 'reembolso', 'montoReembolso', 'resolucion']

function mensajeDe(errores, campo) {
  return errores.find((error) => error.campo === campo)?.mensaje ?? null
}

// HU-26 CA2, CA3: el admin lee el reclamo y lo resuelve. Los montos los calcula y valida el servidor: el modal solo
// repite el monto pagado (de la API) o el escrito.
function ResolverReclamo() {
  const { id } = useParams()
  const { sesion } = useSesion()
  const esAdmin = sesion?.rol === 'ADMIN'
  const [reclamo, setReclamo] = useState(null)
  const [error, setError] = useState(null)
  const [exito, setExito] = useState(null)
  const [aFavorDe, setAFavorDe] = useState('')
  const [reembolso, setReembolso] = useState('')
  const [monto, setMonto] = useState('')
  const [resolucion, setResolucion] = useState('')
  const [errores, setErrores] = useState([])
  const [confirmando, setConfirmando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [recarga, setRecarga] = useState(0)

  useEffect(() => {
    if (!esAdmin) return undefined
    let activo = true
    obtenerReclamoAdmin(id)
      .then((datos) => {
        if (activo) setReclamo(datos)
      })
      .catch((falla) => {
        if (activo) setError(falla.mensaje)
      })
    return () => {
      activo = false
    }
  }, [esAdmin, id, recarga])

  if (!esAdmin) return <Navigate to="/panel" replace />

  function textoConfirmacion() {
    if (aFavorDe === 'ARRENDADOR') return CONFIRMA_ARRENDADOR
    if (reembolso === 'PARCIAL') return confirmaParcial(`S/ ${monto}`)
    if (reembolso === 'TOTAL') return `${CONFIRMA_TOTAL} (${formatoPrecio(reclamo.montoPagado)})`
    return null
  }

  async function confirmar() {
    setEnviando(true)
    setErrores([])
    setError(null)
    const aFavorUsuario = aFavorDe === 'USUARIO'
    try {
      const resuelto = await resolverReclamo(id, {
        aFavorDe: aFavorDe === '' ? null : aFavorDe,
        reembolso: aFavorUsuario && reembolso !== '' ? reembolso : null,
        montoReembolso: aFavorUsuario && reembolso === 'PARCIAL' && monto !== '' ? Number(monto) : null,
        resolucion,
      })
      setReclamo(resuelto)
      setExito(aFavorUsuario ? `${RECLAMO_RESUELTO} ${REEMBOLSO_EN_PASARELA}` : RECLAMO_RESUELTO)
    } catch (falla) {
      const deCampo = (falla.errores ?? []).filter((e) => CAMPOS.includes(e.campo))
      if (deCampo.length > 0) {
        setErrores(deCampo)
      } else {
        setError(falla.mensaje)
        setRecarga((n) => n + 1)
      }
    } finally {
      setEnviando(false)
      setConfirmando(false)
    }
  }

  if (!reclamo) {
    return (
      <section className={estilos.pantalla}>
        <h2>{TITULO_RECLAMO}</h2>
        {error ? <Alerta tipo="error">{error}</Alerta> : <p className={estilos.secundario}>{CARGANDO}</p>}
      </section>
    )
  }

  return (
    <section className={estilos.pantalla}>
      <h2>{TITULO_RECLAMO}</h2>
      {exito && <Alerta tipo="exito">{exito}</Alerta>}
      {error && <Alerta tipo="error">{error}</Alerta>}
      <Tarjeta titulo={reclamo.espacioNombre}>
        <div>
          <EtiquetaEstado estado={reclamo.estado} />
        </div>
        <p className={estilos.dato}>
          {tituloReserva(reclamo.reservaId)} · {formatoFecha(reclamo.fecha)} ·{' '}
          {`${formatoHora(reclamo.horaInicio)}–${formatoHora(reclamo.horaFin)}`}
        </p>
        <p className={estilos.dato}>Usuario: {reclamo.usuarioNombre ?? SIN_NOMBRE}</p>
        <p className={estilos.dato}>Arrendador: {reclamo.arrendadorNombre ?? SIN_NOMBRE}</p>
        <p>
          Monto pagado: <strong className={estilos.monto}>{formatoPrecio(reclamo.montoPagado)}</strong>
        </p>
        <p className={estilos.dato}>Motivo: {MOTIVOS_RECLAMO[reclamo.motivo] ?? reclamo.motivo}</p>
        <p className={estilos.descripcion}>{reclamo.descripcion}</p>
        <p className={estilos.dato}>Abierto el {fechaHora(reclamo.abiertoEn)}</p>
        {reclamo.aFavorDe && (
          <>
            <p className={estilos.resultado}>{resultadoReclamo(reclamo.aFavorDe)}</p>
            <p className={estilos.descripcion}>{reclamo.resolucion}</p>
            {reclamo.montoReembolso != null && (
              <p>
                Reembolso: <strong className={estilos.monto}>{formatoPrecio(reclamo.montoReembolso)}</strong>
              </p>
            )}
          </>
        )}
      </Tarjeta>
      <HiloReclamo mensajes={reclamo.mensajes} />
      {reclamo.estado === 'ABIERTO' && (
        <div className={estilos.formulario}>
          <h3 className={estilos.subtitulo}>{TITULO_RESOLVER}</h3>
          <Campo
            etiqueta={ETIQUETA_A_FAVOR}
            nombre="aFavorDe"
            tipo="select"
            valor={aFavorDe}
            onChange={setAFavorDe}
            opciones={OPCIONES_A_FAVOR}
            error={mensajeDe(errores, 'aFavorDe')}
          />
          {aFavorDe === 'USUARIO' && (
            <Campo
              etiqueta={ETIQUETA_REEMBOLSO}
              nombre="reembolso"
              tipo="select"
              valor={reembolso}
              onChange={setReembolso}
              opciones={OPCIONES_REEMBOLSO}
              error={mensajeDe(errores, 'reembolso')}
            />
          )}
          {aFavorDe === 'USUARIO' && reembolso === 'PARCIAL' && (
            <Campo
              etiqueta={ETIQUETA_MONTO_REEMBOLSO}
              nombre="montoReembolso"
              tipo="number"
              step="0.01"
              valor={monto}
              onChange={setMonto}
              error={mensajeDe(errores, 'montoReembolso')}
            />
          )}
          <Campo
            etiqueta={ETIQUETA_RESOLUCION}
            nombre="resolucion"
            tipo="textarea"
            valor={resolucion}
            onChange={setResolucion}
            error={mensajeDe(errores, 'resolucion')}
          />
          <div>
            <Boton variante="primario" onClick={() => setConfirmando(true)}>
              {BOTON_RESOLVER}
            </Boton>
          </div>
        </div>
      )}
      <Modal
        abierto={confirmando}
        titulo={TITULO_MODAL_RESOLVER}
        textoConfirmar={BOTON_RESOLVER}
        variante="primario"
        cargando={enviando}
        onConfirmar={confirmar}
        onCerrar={() => {
          if (!enviando) setConfirmando(false)
        }}
      >
        {textoConfirmacion() && <p>{textoConfirmacion()}</p>}
      </Modal>
    </section>
  )
}

export default ResolverReclamo
