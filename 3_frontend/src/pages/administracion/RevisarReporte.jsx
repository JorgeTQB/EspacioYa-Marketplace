import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { decidirReporte, obtenerReporteAdmin } from '../../api/administracion.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Modal from '../../components/ui/Modal.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { MOTIVOS_REPORTE } from '../busqueda/textosFicha.js'
import { formatoPrecio, nombreTipo } from '../espacios/textosEspacios.js'
import { formatoFecha } from '../reservas/textosReservas.js'
import {
  AYUDA_MOTIVO_DECISION,
  AYUDA_MOTIVO_ESPACIO,
  BOTON_REGISTRAR_DECISION,
  CARGANDO,
  DECISION_ESPACIO_OCULTO,
  DECISION_REGISTRADA,
  DECISIONES_REPORTE,
  ENLACE_FICHA_PUBLICA,
  ETIQUETA_DECISION,
  ETIQUETA_MOTIVO_DECISION,
  OPCION_ELIGE_DECISION,
  OPCION_MANTENER,
  OPCION_OCULTAR,
  OPCION_OCULTAR_POR_FRAUDE,
  TEXTO_FRAUDE,
  TEXTO_OCULTAR_ESPACIO,
  TITULO_CONTENIDO_REPORTADO,
  TITULO_DECIDIR,
  TITULO_MODAL_DECISION,
  TITULO_MODAL_FRAUDE,
  TITULO_MODAL_MANTENER,
  TITULO_MODAL_OCULTAR,
  TITULO_REPORTE,
  arrendadorDelEspacio,
  calificacionDeCinco,
  capacidadEnPersonas,
  decisionPorFraude,
  direccionDelEspacio,
  motivoDe,
  reportadoEl,
  reportesPendientes,
  resueltoEl,
  textoOcultar,
} from './textosAdministracion.js'
import estilos from './RevisarReporte.module.css'

const CAMPOS = ['decision', 'motivoDecision']
const POR_REVISAR = 'POR_REVISAR'

function mensajeDe(errores, campo) {
  return errores.find((error) => error.campo === campo)?.mensaje ?? null
}

function ContenidoEspacio({ espacio, espacioId }) {
  return (
    <Tarjeta titulo={TITULO_CONTENIDO_REPORTADO}>
      <p className={estilos.nombre}>{espacio.nombre}</p>
      <div>
        <EtiquetaEstado estado={espacio.estado} />
      </div>
      <p className={estilos.dato}>{`${nombreTipo(espacio.tipo)} · ${espacio.zona}`}</p>
      <p className={estilos.dato}>{direccionDelEspacio(espacio.direccion)}</p>
      <p className={estilos.dato}>{capacidadEnPersonas(espacio.capacidad)}</p>
      <p>
        <strong className={estilos.monto}>{formatoPrecio(espacio.precioHora)}</strong> por hora
      </p>
      <p className={estilos.descripcion}>{espacio.descripcion}</p>
      <p className={estilos.dato}>{arrendadorDelEspacio(espacio.arrendadorNombre)}</p>
      {espacio.estado === 'PUBLICADO' && <Link to={`/espacios/${espacioId}`}>{ENLACE_FICHA_PUBLICA}</Link>}
    </Tarjeta>
  )
}

function ContenidoResena({ resena }) {
  return (
    <Tarjeta titulo={TITULO_CONTENIDO_REPORTADO}>
      <p className={estilos.nombre}>{calificacionDeCinco(resena.calificacion)}</p>
      <div>
        <EtiquetaEstado estado={resena.estado} />
      </div>
      <p className={estilos.descripcion}>{resena.comentario}</p>
      <p className={estilos.dato}>{formatoFecha(resena.fecha)}</p>
      <p className={estilos.dato}>Espacio: {resena.espacioNombre}</p>
    </Tarjeta>
  )
}

// HU-27 CA2, CA3: el admin revisa el contenido reportado y decide mantenerlo u ocultarlo con un motivo; para un
// espacio, también ocultarlo por fraude (cancela sus reservas por empezar). Qué se oculta, qué se cancela y cuántas
// reservas lo decide el servidor: la pantalla solo repite lo que responde.
function RevisarReporte() {
  const { id } = useParams()
  const { sesion } = useSesion()
  const esAdmin = sesion?.rol === 'ADMIN'
  const [reporte, setReporte] = useState(null)
  const [error, setError] = useState(null)
  const [exito, setExito] = useState(null)
  const [decision, setDecision] = useState('')
  const [motivo, setMotivo] = useState('')
  const [errores, setErrores] = useState([])
  const [confirmando, setConfirmando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [recarga, setRecarga] = useState(0)

  useEffect(() => {
    if (!esAdmin) return undefined
    let activo = true
    obtenerReporteAdmin(id)
      .then((datos) => {
        if (activo) setReporte(datos)
      })
      .catch((falla) => {
        if (activo) setError(falla.mensaje)
      })
    return () => {
      activo = false
    }
  }, [esAdmin, id, recarga])

  if (!esAdmin) return <Navigate to="/panel" replace />

  if (!reporte) {
    return (
      <section className={estilos.pantalla}>
        <h2>{TITULO_REPORTE}</h2>
        {error ? <Alerta tipo="error">{error}</Alerta> : <p className={estilos.secundario}>{CARGANDO}</p>}
      </section>
    )
  }

  const esEspacio = reporte.tipoObjetivo === 'ESPACIO'
  const opciones = esEspacio
    ? [OPCION_ELIGE_DECISION, OPCION_MANTENER, OPCION_OCULTAR, OPCION_OCULTAR_POR_FRAUDE]
    : [OPCION_ELIGE_DECISION, OPCION_MANTENER, OPCION_OCULTAR]

  function modal() {
    if (decision === OPCION_OCULTAR_POR_FRAUDE.valor) {
      return { titulo: TITULO_MODAL_FRAUDE, variante: 'peligro-confirmar', textos: [TEXTO_FRAUDE] }
    }
    if (decision === OPCION_OCULTAR.valor) {
      const textos = [textoOcultar(reporte.reportesPendientes)]
      if (esEspacio) textos.push(TEXTO_OCULTAR_ESPACIO)
      return { titulo: TITULO_MODAL_OCULTAR, variante: 'peligro-confirmar', textos }
    }
    if (decision === OPCION_MANTENER.valor) {
      return { titulo: TITULO_MODAL_MANTENER, variante: 'primario', textos: [] }
    }
    return { titulo: TITULO_MODAL_DECISION, variante: 'primario', textos: [] }
  }

  async function confirmar() {
    setEnviando(true)
    setErrores([])
    setError(null)
    try {
      const resuelto = await decidirReporte(id, { decision: decision === '' ? null : decision, motivoDecision: motivo })
      setReporte(resuelto)
      if (resuelto.decision === OPCION_OCULTAR_POR_FRAUDE.valor) {
        setExito(decisionPorFraude(resuelto.reservasCanceladas))
      } else if (resuelto.decision === OPCION_OCULTAR.valor && resuelto.tipoObjetivo === 'ESPACIO') {
        setExito(DECISION_ESPACIO_OCULTO)
      } else {
        setExito(DECISION_REGISTRADA)
      }
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

  const { titulo, variante, textos } = modal()

  return (
    <section className={estilos.pantalla}>
      <h2>{TITULO_REPORTE}</h2>
      {exito && <Alerta tipo="exito">{exito}</Alerta>}
      {error && <Alerta tipo="error">{error}</Alerta>}
      <Tarjeta titulo={TITULO_REPORTE}>
        <div>
          <EtiquetaEstado estado={reporte.estado} />
        </div>
        <p className={estilos.dato}>{motivoDe(MOTIVOS_REPORTE[reporte.motivo] ?? reporte.motivo)}</p>
        <p className={estilos.dato}>{reportadoEl(reporte.creadoEn)}</p>
        {reporte.reportesPendientes > 1 && (
          <p className={estilos.dato}>{reportesPendientes(reporte.reportesPendientes)}</p>
        )}
        {reporte.decision && (
          <>
            <p className={estilos.resultado}>{DECISIONES_REPORTE[reporte.decision]}</p>
            <p className={estilos.descripcion}>{motivoDe(reporte.motivoDecision)}</p>
            <p className={estilos.dato}>{resueltoEl(reporte.resueltoEn)}</p>
          </>
        )}
      </Tarjeta>
      {reporte.espacio && <ContenidoEspacio espacio={reporte.espacio} espacioId={reporte.objetivoId} />}
      {reporte.resena && <ContenidoResena resena={reporte.resena} />}
      {reporte.estado === POR_REVISAR && (
        <div className={estilos.formulario}>
          <h3 className={estilos.subtitulo}>{TITULO_DECIDIR}</h3>
          <Campo
            etiqueta={ETIQUETA_DECISION}
            nombre="decision"
            tipo="select"
            valor={decision}
            onChange={setDecision}
            opciones={opciones}
            error={mensajeDe(errores, 'decision')}
          />
          <Campo
            etiqueta={ETIQUETA_MOTIVO_DECISION}
            nombre="motivoDecision"
            tipo="textarea"
            valor={motivo}
            onChange={setMotivo}
            ayuda={esEspacio ? `${AYUDA_MOTIVO_DECISION} ${AYUDA_MOTIVO_ESPACIO}` : AYUDA_MOTIVO_DECISION}
            error={mensajeDe(errores, 'motivoDecision')}
          />
          <div>
            <Boton variante="primario" onClick={() => setConfirmando(true)}>
              {BOTON_REGISTRAR_DECISION}
            </Boton>
          </div>
        </div>
      )}
      <Modal
        abierto={confirmando}
        titulo={titulo}
        textoConfirmar={BOTON_REGISTRAR_DECISION}
        variante={variante}
        cargando={enviando}
        onConfirmar={confirmar}
        onCerrar={() => {
          if (!enviando) setConfirmando(false)
        }}
      >
        {textos.map((texto) => (
          <p key={texto}>{texto}</p>
        ))}
      </Modal>
    </section>
  )
}

export default RevisarReporte
